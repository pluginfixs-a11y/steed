package steed;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;

/**
 * +30% к скорости и силе прыжка (множители в конфиге). Модификатор навешивается на каждую
 * лошадь при создании и при загрузке из NBT — значит и на уже существующих в мире,
 * и повторно не дублируется.
 * <p>
 * Модификатор временный: он не пишется в NBT, а навешивается заново при каждой загрузке.
 */
public final class HorseBoost {
	private static final Identifier SPEED = Identifier.fromNamespaceAndPath("steed", "speed_bonus");
	private static final Identifier JUMP = Identifier.fromNamespaceAndPath("steed", "jump_bonus");

	private HorseBoost() {
	}

	public static void applyTo(AbstractHorse horse) {
		apply(horse, Attributes.MOVEMENT_SPEED, SPEED, SteedConfig.get().horse_speed_multiplier);
		apply(horse, Attributes.JUMP_STRENGTH, JUMP, SteedConfig.get().horse_jump_multiplier);
	}

	private static void apply(AbstractHorse horse, Holder<Attribute> attribute, Identifier id, double multiplier) {
		double bonus = multiplier - 1.0;
		if (bonus <= 0.0) {
			return;
		}
		AttributeInstance instance = horse.getAttribute(attribute);
		if (instance == null || instance.getModifier(id) != null) {
			return;
		}
		instance.addTransientModifier(new AttributeModifier(id, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
	}
}
