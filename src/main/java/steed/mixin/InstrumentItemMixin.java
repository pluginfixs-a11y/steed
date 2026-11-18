package steed.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemStack;
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
 */
@Mixin(InstrumentItem.class)
public abstract class InstrumentItemMixin {

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
