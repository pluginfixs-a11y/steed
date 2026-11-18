package steed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Лошади: зачаровываемая броня, призыв козьим рогом, +30% к скорости и прыжку.
 * <p>
 * Мод чисто серверный: клиенту ничего ставить не нужно.
 */
public class Steed implements ModInitializer {
	public static final String MOD_ID = "steed";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		SteedConfig config = SteedConfig.load();
		LOGGER.info("Steed: броня={}, рог={}, скорость x{}, прыжок x{}",
				config.enchant_horse_armor, config.goat_horn_recall,
				config.horse_speed_multiplier, config.horse_jump_multiplier);

		ServerTickEvents.END_SERVER_TICK.register(Recall::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Recall.clear());

		if (System.getProperty("steed.selftest") != null) {
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::onTick);
			LOGGER.info("Steed: включена самопроверка (-Dsteed.selftest)");
		}
	}
}
