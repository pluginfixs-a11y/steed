package steed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки в <code>config/steed.json</code>. Читаются один раз при старте.
 * Каждая фича выключается отдельно.
 */
public class SteedConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("steed.json");

	private static SteedConfig instance = new SteedConfig();
	private static volatile boolean loaded;

	/** Броню лошади можно зачаровать на столе и на наковальне. */
	public boolean enchant_horse_armor = true;

	/** Козий рог привязывается к лошади и вызывает её. */
	public boolean goat_horn_recall = true;

	/** Множитель скорости лошади (1.3 = +30%). */
	public double horse_speed_multiplier = 1.3;

	/** Множитель силы прыжка лошади (1.3 = +30%). */
	public double horse_jump_multiplier = 1.3;

	/** Радиус поиска безопасного места для вызова (блоков по каждой оси). */
	public int recall_radius = 5;

	/** Задержка телепорта в тиках (40 = 2 секунды). */
	public int recall_delay_ticks = 40;

	/** Кулдаун рога после успешного вызова в тиках (1200 = 1 минута). */
	public int recall_cooldown_ticks = 1200;

	public static SteedConfig get() {
		if (!loaded) {
			load();
		}
		return instance;
	}

	public static SteedConfig load() {
		if (loaded) {
			return instance;
		}
		SteedConfig config = new SteedConfig();
		if (Files.exists(PATH)) {
			try {
				SteedConfig fromDisk = GSON.fromJson(Files.readString(PATH, StandardCharsets.UTF_8), SteedConfig.class);
				if (fromDisk != null) {
					config = fromDisk;
				}
			} catch (Exception e) {
				Steed.LOGGER.error("Не смог прочитать {}, беру значения по умолчанию", PATH, e);
			}
		}
		config.save();
		instance = config;
		loaded = true;
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			Steed.LOGGER.error("Не смог записать {}", PATH, e);
		}
	}
}
