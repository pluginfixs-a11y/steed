package steed.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Единственное место, где ваниль забыла {@code enchantable}: {@code horseArmor()} собирает броню
 * из материала, но не добавляет зачаровываемость. Берём её из того же {@link ArmorMaterial},
 * что и у человеческой брони, поэтому числа совпадают с ванильными (leather 15, iron 9, …).
 */
@Mixin(Item.Properties.class)
public abstract class ItemPropertiesMixin {

	@Inject(method = "horseArmor", at = @At("RETURN"), cancellable = true)
	private void steed$enchantable(ArmorMaterial material, CallbackInfoReturnable<Item.Properties> cir) {
		if (!steed.SteedConfig.get().enchant_horse_armor) {
			return;
		}
		cir.setReturnValue(((Item.Properties) (Object) this).enchantable(material.enchantmentValue()));
	}
}
