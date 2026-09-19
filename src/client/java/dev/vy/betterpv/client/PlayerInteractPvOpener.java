package dev.vy.betterpv.client;

/**
 * Legacy shift+right-click hook — replaced by {@link HypixelProfileSpyButton}
 * on Hypixel's native Profile chest. Kept as a no-op registrar for call sites.
 */
public final class PlayerInteractPvOpener {
	private PlayerInteractPvOpener() {
	}

	public static void register() {
		// Intentionally empty: opening PV from shift+entity click was the wrong UX.
		// Use HypixelProfileSpyButton on the Hypixel "'s Profile" chest instead.
	}
}
