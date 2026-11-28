package steed;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Самопроверка без клиента: сцена в 3000 блоках от спавна, два тика (свежая сущность
 * попадает в поиск по UUID только со следующего тика). Включается {@code -Dsteed.selftest=1}.
 */
public final class SelfTest {
	private static final BlockPos ORIGIN = new BlockPos(3000, 200, 3000);
	private static int phase;
	private static int waitTicks;

	private static Horse bindHorse;
	private static FakePlayer bindPlayer;
	private static Horse recallHorse;
	private static FakePlayer recallPlayer;
	private static ItemStack recallHorn;
	private static BlockPos recallBase;

	private SelfTest() {
	}

	public static void onTick(MinecraftServer server) {
		if (phase == 2) {
			return;
		}
		ServerLevel level = server.overworld();
		if (phase == 0) {
			phase = 1;
			try {
				forceChunks(level);
				testEnchant(server, level);
				testAnvil(server, level);
				testGrindstone(level);
				testShiftUnbind(level);
				testHorseBoost(level);
				testWideSearch(level);
				prepareEntities(level);
			} catch (Throwable t) {
				Steed.LOGGER.error("SELFTEST упал (тик 1)", t);
			}
			return;
		}
		// свежая лошадь появляется в поиске по UUID не сразу — ждём её, а не падаем в HORSE_GONE
		if (recallHorse == null || (level.getEntity(recallHorse.getUUID()) == null && waitTicks++ < 40)) {
			return;
		}
		phase = 2;
		try {
			testHornBind();
			testRecall(level);
		} catch (Throwable t) {
			Steed.LOGGER.error("SELFTEST упал (тик 2)", t);
		}
	}

	private static void forceChunks(ServerLevel level) {
		for (int dx = 0; dx <= 96; dx += 16) {
			for (int dz = 0; dz <= 16; dz += 16) {
				level.setChunkForced((ORIGIN.getX() + dx) >> 4, (ORIGIN.getZ() + dz) >> 4, true);
				level.getChunk((ORIGIN.getX() + dx) >> 4, (ORIGIN.getZ() + dz) >> 4);
			}
		}
	}

	private static Horse spawn(ServerLevel level, BlockPos pos) {
		Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
		if (horse == null) {
			throw new IllegalStateException("не смог создать лошадь");
		}
		horse.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		level.addFreshEntity(horse);
		return horse;
	}

	private static ItemStack goatHorn(ServerLevel level) {
		Holder<Instrument> instrument = level.getServer().registryAccess()
				.lookupOrThrow(Registries.INSTRUMENT).getOrThrow(Instruments.PONDER_GOAT_HORN);
		return InstrumentItem.create(Items.GOAT_HORN, instrument);
	}

	private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			level.setBlock(pos, state, 3);
		}
	}

	private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
		fill(level, from, to, Blocks.AIR.defaultBlockState());
	}

	/** Броня: зачаровываемость, цена стола, применимость защиты и реальная защита на лошади. */
	private static void testEnchant(MinecraftServer server, ServerLevel level) {
		ItemStack[] armors = {
				new ItemStack(Items.LEATHER_HORSE_ARMOR),
				new ItemStack(Items.COPPER_HORSE_ARMOR),
				new ItemStack(Items.IRON_HORSE_ARMOR),
				new ItemStack(Items.GOLDEN_HORSE_ARMOR),
				new ItemStack(Items.DIAMOND_HORSE_ARMOR),
				new ItemStack(Items.NETHERITE_HORSE_ARMOR)
		};
		StringBuilder ench = new StringBuilder();
		boolean all = true;
		for (ItemStack armor : armors) {
			boolean ok = armor.isEnchantable() && EnchantmentHelper.getEnchantmentCost(level.getRandom(), 30, 15, armor) > 0;
			all &= ok;
			ench.append(armor.getItem()).append('=').append(ok).append(' ');
		}
		Steed.LOGGER.info("SELFTEST enchant: все 6 тиров={} [{}]", all, ench.toString().trim());

		Holder<Enchantment> protection = protection(server);
		boolean canEnchant = protection.value().canEnchant(new ItemStack(Items.IRON_HORSE_ARMOR));
		boolean bodySlot = protection.value().matchingSlot(EquipmentSlot.BODY);
		int options = EnchantmentHelper.getAvailableEnchantmentResults(30, new ItemStack(Items.IRON_HORSE_ARMOR),
				server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
						.map(h -> (Holder<Enchantment>) h)).size();
		Steed.LOGGER.info("SELFTEST protection: canEnchant={} слот BODY={} вариантов на столе={}",
				canEnchant, bodySlot, options);

		ItemStack armored = new ItemStack(Items.IRON_HORSE_ARMOR);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		mutable.set(protection, 4);
		armored.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());

		BlockPos base = ORIGIN;
		clear(level, base.offset(-3, 0, -3), base.offset(3, 3, 3));
		fill(level, base.offset(-3, -1, -3), base.offset(3, -1, 3), Blocks.STONE.defaultBlockState());
		Horse bare = spawn(level, base);
		Horse dressed = spawn(level, base.offset(4, 0, 0));
		dressed.setItemSlot(EquipmentSlot.BODY, armored);
		float protBare = EnchantmentHelper.getDamageProtection(level, bare, level.damageSources().generic());
		float protDressed = EnchantmentHelper.getDamageProtection(level, dressed, level.damageSources().generic());
		Steed.LOGGER.info("SELFTEST damage: без брони={} с Protection IV={} (ожидание 0.0 / 4.0)", protBare, protDressed);
		bare.discard();
		dressed.discard();
	}

	/** Наковальня: книга с Protection IV + броня лошади → броня с чарами. */
	private static void testAnvil(MinecraftServer server, ServerLevel level) {
		FakePlayer player = FakePlayer.get(level);
		AnvilMenu menu = new AnvilMenu(0, player.getInventory(), ContainerLevelAccess.create(level, ORIGIN));
		Holder<Enchantment> protection = protection(server);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(protection, 4);
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
		menu.getSlot(0).set(new ItemStack(Items.IRON_HORSE_ARMOR));
		menu.getSlot(1).set(book);
		menu.createResult();
		ItemStack result = menu.getSlot(2).getItem();
		int found = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(protection);
		Steed.LOGGER.info("SELFTEST anvil: результат={} Protection={} цена={} (ожидание iron_horse_armor / 4)",
				result.isEmpty() ? "пусто" : result.getItem(), found, menu.getCost());
	}

	/** Точильный камень: привязанный рог → обычный, без привязки и без блеска. */
	private static void testGrindstone(ServerLevel level) {
		Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
		if (horse == null) {
			throw new IllegalStateException("не смог создать лошадь");
		}
		horse.snapTo(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5);
		FakePlayer player = FakePlayer.get(level);
		ItemStack bound = goatHorn(level);
		HornData.bind(bound, horse, player);

		GrindstoneMenu menu = new GrindstoneMenu(0, player.getInventory(), ContainerLevelAccess.create(level, ORIGIN));
		menu.getSlot(0).set(bound.copy());
		menu.slotsChanged(player.getInventory());
		ItemStack result = menu.getSlot(2).getItem();
		boolean stillBound = !result.isEmpty() && HornData.read(result) != null;
		boolean glint = !result.isEmpty() && Boolean.TRUE.equals(result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		Steed.LOGGER.info("SELFTEST grindstone: результат={} привязка={} блеск={} (ожидание goat_horn / false / false)",
				result.isEmpty() ? "пусто" : result.getItem(), stillBound, glint);
		horse.discard();
	}

	/** Shift+ПКМ по привязанному рогу снимает привязку и блеск без точильного камня. */
	private static void testShiftUnbind(ServerLevel level) {
		Horse horse = EntityTypes.HORSE.create(level, EntitySpawnReason.COMMAND);
		if (horse == null) {
			throw new IllegalStateException("не смог создать лошадь");
		}
		horse.snapTo(ORIGIN.getX() + 0.5, ORIGIN.getY(), ORIGIN.getZ() + 0.5);
		FakePlayer player = FakePlayer.get(level);
		ItemStack horn = goatHorn(level);
		HornData.bind(horn, horse, player);
		player.setShiftKeyDown(true);
		try {
			Recall.use(player, horn);
		} finally {
			player.setShiftKeyDown(false);
		}
		boolean unbound = HornData.read(horn) == null
				&& !Boolean.TRUE.equals(horn.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		Steed.LOGGER.info("SELFTEST shift-unbind: привязка={} блеск={} (ожидание false/false)",
				HornData.read(horn) != null,
				Boolean.TRUE.equals(horn.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)));
		if (!unbound) {
			Steed.LOGGER.error("SELFTEST shift-unbind: провал");
		}
		horse.discard();
	}

	/** +30% к скорости и прыжку: модификатор есть и даёт ровно множитель из конфига. */
	private static void testHorseBoost(ServerLevel level) {
		Horse horse = spawn(level, ORIGIN);
		AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
		double speedRatio = speed == null || speed.getBaseValue() == 0.0 ? 0.0 : speed.getValue() / speed.getBaseValue();
		double jumpRatio = jump == null || jump.getBaseValue() == 0.0 ? 0.0 : jump.getValue() / jump.getBaseValue();
		Steed.LOGGER.info("SELFTEST horse-boost: скорость {}x (база {}), прыжок {}x (база {}) (ожидание {}x / {}x)",
				String.format("%.2f", speedRatio), speed == null ? 0 : speed.getBaseValue(),
				String.format("%.2f", jumpRatio), jump == null ? 0 : jump.getBaseValue(),
				SteedConfig.get().horse_speed_multiplier, SteedConfig.get().horse_jump_multiplier);
		horse.discard();
	}

	/** Лошадь и игрок для второй фазы: свежая сущность ищется по UUID только со следующего тика. */
	private static void prepareEntities(ServerLevel level) {
		BlockPos bindBase = ORIGIN.offset(50, 0, 0);
		clear(level, bindBase.offset(-3, -1, -3), bindBase.offset(3, 4, 3));
		fill(level, bindBase.offset(-3, -1, -3), bindBase.offset(3, -1, 3), Blocks.STONE.defaultBlockState());
		bindHorse = spawn(level, bindBase);
		bindPlayer = FakePlayer.get(level);
		bindPlayer.snapTo(bindBase.getX() + 2.5, bindBase.getY(), bindBase.getZ() + 0.5);

		recallBase = ORIGIN.offset(70, 0, 0);
		clear(level, recallBase.offset(-8, -1, -8), recallBase.offset(8, 6, 8));
		fill(level, recallBase.offset(-8, -1, -8), recallBase.offset(8, -1, 8), Blocks.STONE.defaultBlockState());
		recallHorse = spawn(level, recallBase.offset(6, 0, 6));
		recallPlayer = FakePlayer.get(level);
		recallPlayer.snapTo(recallBase.getX() + 0.5, recallBase.getY(), recallBase.getZ() + 0.5);
		recallHorn = goatHorn(level);
		HornData.bind(recallHorn, recallHorse, recallPlayer);
	}

	/** ПКМ рогом по лошади: привязка в custom_data + блеск. Идёт через настоящий {@code Entity.interact}. */
	private static void testHornBind() {
		ServerLevel level = (ServerLevel) bindPlayer.level();
		bindHorse.setTamed(true);
		bindHorse.setOwner(bindPlayer);
		bindPlayer.setItemInHand(InteractionHand.MAIN_HAND, goatHorn(level));
		bindHorse.interact(bindPlayer, InteractionHand.MAIN_HAND, bindHorse.position());
		ItemStack horn = bindPlayer.getMainHandItem();
		HornData.Bound bound = HornData.read(horn);
		boolean glint = Boolean.TRUE.equals(horn.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
		boolean sameHorse = bound != null && bound.horse().equals(bindHorse.getUUID());
		Steed.LOGGER.info("SELFTEST horn-bind: привязка={} та же лошадь={} блеск={}", bound != null, sameHorse, glint);

		// дикую лошадь привязать рогом нельзя
		Horse wild = spawn(level, ORIGIN.offset(54, 0, 0));
		bindPlayer.setItemInHand(InteractionHand.MAIN_HAND, goatHorn(level));
		wild.interact(bindPlayer, InteractionHand.MAIN_HAND, wild.position());
		boolean wildBound = HornData.read(bindPlayer.getMainHandItem()) != null;
		Steed.LOGGER.info("SELFTEST horn-bind-wild: привязка={} (ожидание false)", wildBound);
		if (wildBound) {
			Steed.LOGGER.error("SELFTEST horn-bind-wild: провал");
		}
		wild.discard();
	}

	/** Вызов: всадник — отказ, свободное место — телепорт через задержку, замурованный игрок — отказ. */
	private static void testRecall(ServerLevel level) {
		Recall.Result rider = riderCheck(level);
		Steed.LOGGER.info("SELFTEST recall-rider: {} (ожидание RIDER)", rider);

		BlockPos boxed = ORIGIN.offset(30, 0, 0);
		// полная замуровка: иначе поиск найдёт опору на крыше коробки (он теперь смотрит и вверх)
		clear(level, boxed.offset(-7, -26, -7), boxed.offset(7, 10, 7));
		fill(level, boxed.offset(-6, -25, -6), boxed.offset(6, 9, 6), Blocks.STONE.defaultBlockState());
		clear(level, boxed, boxed.above());
		recallPlayer.snapTo(boxed.getX() + 0.5, boxed.getY(), boxed.getZ() + 0.5);
		Recall.Result noSpot = Recall.start(recallPlayer, recallHorn);
		Steed.LOGGER.info("SELFTEST recall-no-spot: {} (ожидание NO_SPOT)", noSpot);

		recallPlayer.snapTo(recallBase.getX() + 0.5, recallBase.getY(), recallBase.getZ() + 0.5);
		Recall.Result ok = Recall.start(recallPlayer, recallHorn);
		Steed.LOGGER.info("SELFTEST recall-start: {} (ожидание OK)", ok);
		for (int i = 0; i < SteedConfig.get().recall_delay_ticks + 2; i++) {
			Recall.tick(level.getServer());
		}
		double distance = recallHorse.position().distanceTo(recallPlayer.position());
		Steed.LOGGER.info("SELFTEST recall-done: расстояние до игрока={} (ожидание < 8)", distance);
		HornData.Bound after = HornData.read(recallHorn);
		boolean fresh = after != null
				&& Math.abs(after.x() - recallHorse.blockPosition().getX()) <= 1
				&& Math.abs(after.z() - recallHorse.blockPosition().getZ()) <= 1;
		Steed.LOGGER.info("SELFTEST recall-coords: в роге={} лошадь={} свежие={} (ожидание true)",
				after == null ? "-" : after.x() + "," + after.z(),
				recallHorse.blockPosition().getX() + "," + recallHorse.blockPosition().getZ(), fresh);
	}

	/** Устаревшие координаты: 3x3 не накрывает чанк лошади, 5x5 накрывает. */
	private static void testWideSearch(ServerLevel level) {
		BlockPos far = ORIGIN.offset(200, 0, 200);
		HornData.Bound stale = new HornData.Bound(UUID.randomUUID(), level.dimension(),
				far.getX() - 32, far.getY(), far.getZ() - 32, null);
		int hx = far.getX() >> 4;
		int hz = far.getZ() >> 4;
		boolean before = level.getChunkSource().getChunkNow(hx, hz) != null;
		Recall.Forced narrow = Recall.Forced.around(level, stale, 1);
		boolean narrowCovers = level.getChunkSource().getChunkNow(hx, hz) != null;
		Recall.Forced wide = Recall.Forced.around(level, stale, 2);
		boolean wideCovers = level.getChunkSource().getChunkNow(hx, hz) != null;
		Steed.LOGGER.info("SELFTEST wide-search: до={} 3x3 накрывает={} 5x5 накрывает={} (ожидание false/false/true)",
				before, narrowCovers, wideCovers);
		narrow.release();
		wide.release();
	}

	private static Recall.Result riderCheck(ServerLevel level) {
		Entity rider = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		boolean sat = false;
		if (rider != null) {
			rider.snapTo(recallHorse.getX(), recallHorse.getY(), recallHorse.getZ());
			level.addFreshEntity(rider);
			sat = rider.startRiding(recallHorse, true, true);
		}
		Recall.Result result = Recall.start(recallPlayer, recallHorn);
		Steed.LOGGER.info("SELFTEST recall-rider: посадил зомби={}, пассажиров={}", sat, recallHorse.getPassengers().size());
		if (rider != null) {
			rider.stopRiding();
			rider.discard();
		}
		return result;
	}

	private static Holder<Enchantment> protection(MinecraftServer server) {
		return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
	}
}
