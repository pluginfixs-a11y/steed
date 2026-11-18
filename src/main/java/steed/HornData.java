package steed;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Привязка рога к лошади хранится в ванильном компоненте {@code minecraft:custom_data}
 * (свой {@code DataComponentType} серверный мод не может синхронизировать с ванильным клиентом,
 * а custom_data клиент просто носит с собой). Привязка снимается только вместе с самим предметом.
 */
public final class HornData {
	private static final String KEY = "steed";

	public record Bound(UUID horse, ResourceKey<Level> dimension, int x, int y, int z) {
	}

	private HornData() {
	}

	/** Козий рог = предмет с компонентом инструмента. */
	public static boolean isHorn(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.INSTRUMENT);
	}

	public static void bind(ItemStack stack, AbstractHorse horse) {
		CompoundTag tag = new CompoundTag();
		tag.putString("horse", horse.getUUID().toString());
		tag.putString("dim", horse.level().dimension().identifier().toString());
		tag.putInt("x", horse.blockPosition().getX());
		tag.putInt("y", horse.blockPosition().getY());
		tag.putInt("z", horse.blockPosition().getZ());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.put(KEY, tag));
		ensureGlint(stack);
	}

	public static Bound read(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag().getCompoundOrEmpty(KEY);
		if (!tag.contains("horse")) {
			return null;
		}
		try {
			UUID uuid = UUID.fromString(tag.getStringOr("horse", ""));
			Identifier dim = Identifier.parse(tag.getStringOr("dim", "minecraft:overworld"));
			return new Bound(uuid, ResourceKey.create(Registries.DIMENSION, dim),
					tag.getIntOr("x", 0), tag.getIntOr("y", 0), tag.getIntOr("z", 0));
		} catch (Exception e) {
			return null;
		}
	}

	/** Блеск «зачарованного» рога: возвращаем, если какая-то механика его сняла. */
	public static void ensureGlint(ItemStack stack) {
		if (!Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
		}
	}

	/** Координаты в роге держим свежими: следующая попытка найдёт чанк без выгрузки. */
	public static void storePosition(ItemStack stack, AbstractHorse horse) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return;
		}
		CompoundTag tag = data.copyTag();
		CompoundTag own = tag.getCompoundOrEmpty(KEY);
		if (!own.contains("horse")) {
			return;
		}
		own.putString("dim", horse.level().dimension().identifier().toString());
		own.putInt("x", horse.blockPosition().getX());
		own.putInt("y", horse.blockPosition().getY());
		own.putInt("z", horse.blockPosition().getZ());
		tag.put(KEY, own);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** Отвязка (точильный камень): рог становится обычным — ни привязки, ни блеска. */
	public static void unbind(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			CompoundTag tag = data.copyTag();
			tag.remove(KEY);
			if (tag.isEmpty()) {
				stack.remove(DataComponents.CUSTOM_DATA);
			} else {
				stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
		}
		stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
	}
}
