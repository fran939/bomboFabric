package dev.vy.betterpv.client.gui;

/**
 * Hypixel SkyBlock level number colors (wiki Century Party / level brackets).
 */
public final class SkyBlockLevelColors {
	private SkyBlockLevelColors() {
	}

	/** ARGB color for the given SkyBlock level number. */
	public static int colorFor(int level) {
		if (level >= 480) {
			return 0xFFAA0000; // dark red
		}
		if (level >= 440) {
			return 0xFFFF5555; // red
		}
		if (level >= 400) {
			return 0xFFFFAA00; // gold
		}
		if (level >= 360) {
			return 0xFFAA00AA; // purple / dark purple
		}
		if (level >= 320) {
			return 0xFFFF55FF; // light purple / pink
		}
		if (level >= 280) {
			return 0xFF5555FF; // blue
		}
		if (level >= 240) {
			return 0xFF00AAAA; // dark aqua / cyan
		}
		if (level >= 200) {
			return 0xFF55FFFF; // aqua
		}
		if (level >= 160) {
			return 0xFF00AA00; // dark green
		}
		if (level >= 120) {
			return 0xFF55FF55; // green
		}
		if (level >= 80) {
			return 0xFFFFFF55; // yellow
		}
		if (level >= 40) {
			return 0xFFFFFFFF; // white
		}
		return 0xFFAAAAAA; // gray
	}
}
