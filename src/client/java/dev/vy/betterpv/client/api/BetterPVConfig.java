package dev.vy.betterpv.client.api;

import dev.vy.betterpv.BetterPV;

public final class BetterPVConfig {
	private BetterPVConfig() {
	}

	public static void load() {
		BetterPV.LOGGER.info("Hypixel: api.vyriv.dev via Minecraft session JWT");
	}
}
