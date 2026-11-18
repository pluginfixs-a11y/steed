package steed.mixin;

import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import steed.HornData;

/**
 * Точильный камень — единственный способ отвязать рог: привязанный рог превращается в обычный.
 * Ваниль для рога без зачарований отдаёт пустой результат, поэтому подставляем его сами.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {

	@Inject(method = "computeResult", at = @At("RETURN"), cancellable = true)
	private void steed$stripBinding(ItemStack first, ItemStack second, CallbackInfoReturnable<ItemStack> cir) {
		if (!cir.getReturnValue().isEmpty()) {
			return;
		}
		ItemStack source = boundHorn(first, second);
		if (source == null) {
			return;
		}
		ItemStack plain = source.copyWithCount(1);
		HornData.unbind(plain);
		cir.setReturnValue(plain);
	}

	private static ItemStack boundHorn(ItemStack first, ItemStack second) {
		if (first.isEmpty() == second.isEmpty()) {
			return null;
		}
		ItemStack only = first.isEmpty() ? second : first;
		if (only.getCount() != 1 || !HornData.isHorn(only)) {
			return null;
		}
		return HornData.read(only) == null ? null : only;
	}
}
