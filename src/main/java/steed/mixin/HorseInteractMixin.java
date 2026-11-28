package steed.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import steed.HornData;
import steed.SteedConfig;

/**
 * ПКМ козьим рогом по лошади — привязка. Перехватываем на {@code Entity.interact}: у каждой
 * лошади свой {@code mobInteract} (Horse переопределяет), а {@code interact} один на всех.
 * <p>
 * Никакого текста: рог просто звучит, как будто в него дунули у самой лошади.
 */
@Mixin(Entity.class)
public abstract class HorseInteractMixin {

	@Inject(method = "interact", at = @At("HEAD"), cancellable = true)
	private void steed$bindHorn(Player player, InteractionHand hand, Vec3 hit, CallbackInfoReturnable<InteractionResult> cir) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof AbstractHorse horse)) {
			return;
		}
		// присед + ПКМ по лошади в ванили не делает ничего — не отбираем жест у игрока
		if (player.isShiftKeyDown()) {
			return;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!SteedConfig.get().goat_horn_recall || !HornData.isHorn(held)) {
			return;
		}
		HornData.Bound existing = HornData.read(held);
		if (existing != null && existing.horse().equals(horse.getUUID())) {
			// рог уже привязан к этой лошади — не мешаем ванили посадить верхом
			return;
		}
		if (horse.level().isClientSide()) {
			cir.setReturnValue(InteractionResult.SUCCESS);
			return;
		}
		// рог привязываем только к своей приручённой лошади; ванильное взаимодействие не отменяем
		if (!horse.isTamed()) {
			return;
		}
		EntityReference<LivingEntity> ref = horse.getOwnerReference();
		if (ref != null && !ref.matches(player)) {
			return;
		}
		HornData.bind(held, horse, player);
		blowHorn(horse, held);
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
			// ванильный клиент успевает «сесть» на лошадь локально — снимаем фантомного всадника
			serverPlayer.connection.send(new ClientboundSetPassengersPacket(horse));
		}
		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	private static void blowHorn(AbstractHorse horse, ItemStack horn) {
		InstrumentComponent component = horn.get(DataComponents.INSTRUMENT);
		if (component == null) {
			return;
		}
		SoundEvent sound = component.instrument().value().soundEvent().value();
		horse.level().playSound(null, horse.getX(), horse.getY(), horse.getZ(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}
}
