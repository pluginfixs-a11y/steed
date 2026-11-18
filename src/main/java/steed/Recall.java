package steed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundCooldownPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Вызов лошади по рогу: поиск безопасного места рядом с игроком, двухсекундный «телепорт
 * эндермена» с частицами, потом сама лошадь.
 * <p>
 * Привязка с рога не снимается никогда — единственный способ отвязать — точильный камень.
 * Лошадь ищется и в незагруженных чанках: сначала грузится 3x3 чанка вокруг последних известных
 * координат, если её там нет — 5x5 (лошадь могла отойти). Поиск повторяется до 200 тиков,
 * потому что на загруженном сервере чанки читаются с диска секундами. Координаты в роге
 * обновляются после каждого успешного вызова — иначе следующая попытка искала бы старое место.
 * Текст в чат и над хотбаром не пишем — только звуки и лог сервера.
 */
public final class Recall {

	public enum Result {
		OK, LOADING, NOT_BOUND, HORSE_GONE, RIDER, NO_SPOT
	}

	/** Сколько тиков ждём, пока сервер догрузит чанки и увидит лошадь. */
	private static final int RETRY_TICKS = 200;
	/** Радиус (в чанках) первой попытки загрузки: 1 = 3x3. */
	private static final int CHUNK_RADIUS = 1;
	/** Радиус второй попытки: 2 = 5x5, на случай если лошадь отошла от последней точки. */
	private static final int CHUNK_RADIUS_WIDE = 2;
	/** Насколько глубоко вниз ищем опору под игроком. */
	private static final int SEARCH_DOWN = 24;
	/** Насколько вверх ищем (на случай, если игрок под землёй). */
	private static final int SEARCH_UP = 8;

	/** Принудительно загруженная квадратная область чанков вокруг лошади. */
	record Forced(ServerLevel level, int cx, int cz, int radius) {
		static Forced around(ServerLevel level, HornData.Bound bound, int radius) {
			int cx = bound.x() >> 4;
			int cz = bound.z() >> 4;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					level.setChunkForced(cx + dx, cz + dz, true);
					level.getChunk(cx + dx, cz + dz);
				}
			}
			return new Forced(level, cx, cz, radius);
		}

		boolean loaded() {
			return level.getChunkSource().getChunkNow(cx, cz) != null;
		}

		void release() {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					level.setChunkForced(cx + dx, cz + dz, false);
				}
			}
		}
	}

	private static final class Pending {
		final ServerLevel target;
		final AbstractHorse horse;
		final ItemStack horn;
		final Vec3 spot;
		final Forced forced;
		int ticks;

		Pending(ServerLevel target, AbstractHorse horse, ItemStack horn, Vec3 spot, int ticks, Forced forced) {
			this.target = target;
			this.horse = horse;
			this.horn = horn;
			this.spot = spot;
			this.ticks = ticks;
			this.forced = forced;
		}
	}

	private static final class Retry {
		final ServerPlayer player;
		final ItemStack horn;
		final HornData.Bound bound;
		final ServerLevel level;
		final Forced forced;
		int ticks;

		Retry(ServerPlayer player, ItemStack horn, HornData.Bound bound, ServerLevel level, Forced forced, int ticks) {
			this.player = player;
			this.horn = horn;
			this.bound = bound;
			this.level = level;
			this.forced = forced;
			this.ticks = ticks;
		}
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Retry> RETRIES = new ArrayList<>();

	private Recall() {
	}

	/** Сброс между мирами: статические списки не должны переживать остановку сервера. */
	public static void clear() {
		PENDING.clear();
		RETRIES.clear();
	}

	/** ПКМ рогом: вызов, кулдаун и звуковой отклик. */
	public static void use(ServerPlayer player, ItemStack horn) {
		Result result = start(player, horn);
		switch (result) {
			case OK -> {
				HornData.ensureGlint(horn);
				applyCooldown(player, horn);
			}
			case LOADING -> removeVanillaCooldown(player, horn);
			case NOT_BOUND -> {
			}
			default -> {
				removeVanillaCooldown(player, horn);
				feedback(player, result);
				Steed.LOGGER.info("Steed: вызов не удался — {} (игрок {})", result, player.getPlainTextName());
			}
		}
	}

	public static Result start(ServerPlayer player, ItemStack horn) {
		HornData.Bound bound = HornData.read(horn);
		if (bound == null) {
			Steed.LOGGER.info("Steed: у рога нет привязки (игрок {})", player.getPlainTextName());
			return Result.NOT_BOUND;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel horseLevel = server.getLevel(bound.dimension());
		if (horseLevel == null) {
			return Result.HORSE_GONE;
		}
		AbstractHorse horse = find(horseLevel, bound);
		if (horse != null) {
			Steed.LOGGER.info("Steed: лошадь {} в прогруженном чанке — телепорт без прогрузки", bound.horse());
			return startWith(player, horn, horse, null);
		}
		// лошадь в незагруженном чанке: грузим 3x3 вокруг последних координат, потом 5x5
		Steed.LOGGER.info("Steed: лошадь {} не в мире, гружу чанки вокруг {} {}",
				bound.horse(), bound.dimension().identifier(), new BlockPos(bound.x(), bound.y(), bound.z()));
		Forced forced = Forced.around(horseLevel, bound, CHUNK_RADIUS);
		horse = find(horseLevel, bound);
		if (horse == null) {
			Steed.LOGGER.info("Steed: в 3x3 нет, расширяю до 5x5");
			forced = Forced.around(horseLevel, bound, CHUNK_RADIUS_WIDE);
			horse = find(horseLevel, bound);
		}
		if (horse != null) {
			return startWith(player, horn, horse, forced);
		}
		RETRIES.add(new Retry(player, horn, bound, horseLevel, forced, RETRY_TICKS));
		return Result.LOADING;
	}

	private static AbstractHorse find(ServerLevel level, HornData.Bound bound) {
		Entity entity = level.getEntity(bound.horse());
		if (!(entity instanceof AbstractHorse horse) || horse.isRemoved() || !horse.isAlive()) {
			return null;
		}
		return horse;
	}

	private static Result startWith(ServerPlayer player, ItemStack horn, AbstractHorse horse, Forced forced) {
		if (horse.getFirstPassenger() != null) {
			release(forced);
			return Result.RIDER;
		}
		ServerLevel targetLevel = (ServerLevel) player.level();
		Vec3 spot = findSpot(targetLevel, player, horse);
		if (spot == null) {
			release(forced);
			return Result.NO_SPOT;
		}
		PENDING.add(new Pending(targetLevel, horse, horn, spot,
				Math.max(1, SteedConfig.get().recall_delay_ticks), forced));
		return Result.OK;
	}

	private static void release(Forced forced) {
		if (forced != null) {
			forced.release();
		}
	}

	public static void tick(MinecraftServer server) {
		tickRetries(server);
		tickPending(server);
	}

	private static void tickRetries(MinecraftServer server) {
		if (RETRIES.isEmpty()) {
			return;
		}
		List<Retry> next = new ArrayList<>(RETRIES.size());
		for (Retry retry : RETRIES) {
			if (retry.player.level().getServer() != server) {
				continue;
			}
			AbstractHorse horse = find(retry.level, retry.bound);
			if (horse != null) {
				Steed.LOGGER.info("Steed: лошадь {} нашлась после загрузки чанков", retry.bound.horse());
				Result result = startWith(retry.player, retry.horn, horse, retry.forced);
				if (result == Result.OK) {
					HornData.ensureGlint(retry.horn);
					applyCooldown(retry.player, retry.horn);
				} else {
					removeVanillaCooldown(retry.player, retry.horn);
					feedback(retry.player, result);
					Steed.LOGGER.info("Steed: вызов не удался — {} (игрок {})", result, retry.player.getPlainTextName());
				}
				continue;
			}
			if (--retry.ticks > 0) {
				next.add(retry);
			} else {
				Steed.LOGGER.warn("Steed: лошадь {} не нашлась за {} тиков ({} {} {} {}), чанк загружен={}",
						retry.bound.horse(), RETRY_TICKS, retry.bound.dimension().identifier(),
						retry.bound.x(), retry.bound.y(), retry.bound.z(), retry.forced.loaded());
				release(retry.forced);
				removeVanillaCooldown(retry.player, retry.horn);
				feedback(retry.player, Result.HORSE_GONE);
			}
		}
		RETRIES.clear();
		RETRIES.addAll(next);
	}

	private static void tickPending(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		List<Pending> next = new ArrayList<>(PENDING.size());
		for (Pending pending : PENDING) {
			if (pending.target.getServer() != server) {
				continue;
			}
			pending.target.sendParticles(ParticleTypes.PORTAL,
					pending.spot.x, pending.spot.y + 0.9, pending.spot.z, 8, 0.4, 0.7, 0.4, 0.05);
			if (pending.ticks > 1) {
				pending.ticks--;
				next.add(pending);
				continue;
			}
			AbstractHorse horse = pending.horse;
			if (!horse.isRemoved() && horse.isAlive()) {
				horse.teleportTo(pending.target, pending.spot.x, pending.spot.y, pending.spot.z,
						Set.<Relative>of(), horse.getYRot(), horse.getXRot(), false);
				horse.setDeltaMovement(Vec3.ZERO);
				horse.fallDistance = 0.0F;
				// координаты в роге — уже новые: следующая попытка найдёт чанк с первого раза
				HornData.storePosition(pending.horn, horse);
			}
			release(pending.forced);
		}
		PENDING.clear();
		PENDING.addAll(next);
	}

	/**
	 * Ближайшее место рядом с игроком, где лошадь влезает целиком, не задевает игрока и под ней
	 * твёрдая опора. Ищем и вниз на {@link #SEARCH_DOWN} блоков — иначе вызов в полёте не работал бы.
	 */
	private static Vec3 findSpot(ServerLevel level, ServerPlayer player, AbstractHorse horse) {
		BlockPos origin = player.blockPosition();
		int radius = Math.max(1, SteedConfig.get().recall_radius);
		EntityDimensions dimensions = horse.getDimensions(horse.getPose());
		AABB playerBox = player.getBoundingBox().inflate(0.1);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
							|| pos.getY() <= level.getMinY() + 1 || pos.getY() >= level.getMaxY() - 2) {
						continue;
					}
					if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
						continue;
					}
					if (!Block.canSupportRigidBlock(level, pos.below())) {
						continue;
					}
					AABB box = dimensions.makeBoundingBox(Vec3.atBottomCenterOf(pos));
					if (box.intersects(playerBox)) {
						continue;
					}
					if (!level.noCollision(horse, box)) {
						continue;
					}
					double distance = pos.distSqr(origin);
					if (distance < bestDistance) {
						bestDistance = distance;
						best = pos.immutable();
					}
				}
			}
		}
		return best == null ? null : Vec3.atBottomCenterOf(best);
	}

	private static void applyCooldown(ServerPlayer player, ItemStack horn) {
		int cooldown = Math.max(20, SteedConfig.get().recall_cooldown_ticks);
		player.getCooldowns().addCooldown(horn, cooldown);
		if (player.connection != null) {
			player.connection.send(new ClientboundCooldownPacket(
					player.getCooldowns().getCooldownGroup(horn), cooldown));
		}
	}

	/** Неудача не должна блокировать рог: снимаем ванильный кулдаун (140 тиков). */
	private static void removeVanillaCooldown(ServerPlayer player, ItemStack horn) {
		player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(horn));
	}

	/** Отклик без текста: короткий низкий «нельзя», его слышно и в полёте. */
	public static void feedback(ServerPlayer player, Result result) {
		if (result == Result.OK || result == Result.NOT_BOUND || result == Result.LOADING) {
			return;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
	}
}
