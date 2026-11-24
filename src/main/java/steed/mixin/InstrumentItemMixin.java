package steed.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.InstrumentComponent;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import steed.Recall;
import steed.SteedConfig;

/**
 * ПКМ привязанным рогом — вызов лошади. Ванильный {@code use} не отменяем: рог играет свой
 * звук и анимацию, а мы после него запускаем вызов и ставим кулдаун на минуту.
 * <p>
 * Исключение — Shift+ПКМ по привязанному рогу: это отвязка, и она перехватывается на HEAD,
 * чтобы ваниль не проиграла звук вызова и не поставила свой кулдаун.
 */
@Mixin(InstrumentItem.class)
public abstract class InstrumentItemMixin {

	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void steed$unbind(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)
				|| !SteedConfig.get().goat_horn_recall || !player.isShiftKeyDown()) {
			return;
		}
		ItemStack horn = player.getItemInHand(hand);
		if (!Recall.unbindIfSneaking(serverPlayer, horn)) {
			return;
		}
		InstrumentComponent component = horn.get(DataComponents.INSTRUMENT);
		if (component != null) {
			SoundEvent sound = component.instrument().value().soundEvent().value();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), sound,
					SoundSource.PLAYERS, 1.0F, 1.0F);
		}
		cir.setReturnValue(InteractionResult.SUCCESS);
	}

	@Inject(method = "use", at = @At("RETURN"))
	private void steed$recall(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (!SteedConfig.get().goat_horn_recall) {
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		Recall.use(serverPlayer, stack);
	}
}
