package dev.vy.betterpv.client.gui;

import dev.vy.betterpv.BetterPV;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

/**
 * Stuck-loading egg finale: close PV → wait → {@code /limbo} → wait → fake Hypixel ban disconnect.
 */
public final class LoadingEggFinale {
	private enum Step {
		IDLE,
		CLOSE_PV,
		WAIT_AFTER_CLOSE,
		SEND_LIMBO,
		WAIT_AFTER_LIMBO,
		SHOW_BAN,
		DONE
	}

	private static Step step = Step.IDLE;
	private static long nextAtMs;

	private LoadingEggFinale() {
	}

	public static boolean isActive() {
		return step != Step.IDLE && step != Step.DONE;
	}

	/** Queue the sequence (safe to call from render). Actual close happens on the next tick. */
	public static void start() {
		if (step != Step.IDLE && step != Step.DONE) {
			return;
		}
		BetterPV.LOGGER.warn("Loading egg finale - close PV, /limbo, then fake ban");
		step = Step.CLOSE_PV;
		nextAtMs = 0L;
	}

	/**
	 * Cancels a queued (or in-flight) finale.
	 *
	 * <p>Used when the load genuinely failed: replying with {@code /limbo} and a fake Hypixel ban
	 * would be a lie, and it also kicks the player out of the world over a network hiccup.
	 */
	public static void abort() {
		if (step != Step.IDLE && step != Step.DONE) {
			BetterPV.LOGGER.warn("Loading egg finale aborted");
		}
		step = Step.DONE;
		nextAtMs = 0L;
	}

	public static void tick(Minecraft client) {
		if (step == Step.IDLE || step == Step.DONE) {
			return;
		}
		if (client == null) {
			return;
		}
		long now = System.currentTimeMillis();
		switch (step) {
			case CLOSE_PV -> {
				Screen screen = client.gui.screen();
				if (screen instanceof ProfileViewerScreen) {
					client.setScreenAndShow(null);
				}
				step = Step.WAIT_AFTER_CLOSE;
				nextAtMs = now + jitter(900, 1100);
			}
			case WAIT_AFTER_CLOSE -> {
				if (now < nextAtMs) {
					return;
				}
				step = Step.SEND_LIMBO;
			}
			case SEND_LIMBO -> {
				LocalPlayer player = client.player;
				ClientPacketListener connection = client.getConnection();
				if (player != null && connection != null) {
					try {
						connection.sendCommand("limbo");
					} catch (Exception e) {
						BetterPV.LOGGER.warn("Failed to send /limbo during egg finale", e);
					}
				}
				step = Step.WAIT_AFTER_LIMBO;
				nextAtMs = now + jitter(500, 1000);
			}
			case WAIT_AFTER_LIMBO -> {
				if (now < nextAtMs) {
					return;
				}
				step = Step.SHOW_BAN;
			}
			case SHOW_BAN -> {
				FakeBanScreen.show(client);
				step = Step.DONE;
			}
			default -> {
			}
		}
	}

	private static long jitter(int minMs, int maxMs) {
		return ThreadLocalRandom.current().nextInt(minMs, maxMs + 1);
	}
}
