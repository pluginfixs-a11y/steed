package steed.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import steed.HorseBoost;

/**
 * Точка, где лошадь получает +30% к скорости и прыжку: конструктор (новый спавн, в том числе
 * прирученные и рождённые) и чтение NBT (лошадь, загруженная из чанка). Событие
 * {@code ServerEntityEvents.ENTITY_LOAD} для свежего спавна в 26.2 не срабатывает, поэтому
 * цепляемся к самим методам.
 */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {

	@Inject(method = "<init>", at = @At("RETURN"))
	private void steed$boostOnCreate(EntityType<?> type, Level level, CallbackInfo ci) {
		HorseBoost.applyTo((AbstractHorse) (Object) this);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
	private void steed$boostOnLoad(ValueInput input, CallbackInfo ci2) {
		HorseBoost.applyTo((AbstractHorse) (Object) this);
	}
}
