package dev.vy.betterpv.client.gui;

/**
 * Konami-code easter egg (↑↑↓↓←→←→BA↵) - a nod to Moulberry / NEU PV shenanigans.
 * Enables animated rainbow skill bars, Dinnerbone flip, and a spinning player preview.
 */
public final class MoulberryMode {
	/** GLFW key codes (same style as {@link dev.vy.betterpv.client.gui.dungeons.DungeonPage}). */
	private static final int KEY_UP = 265;
	private static final int KEY_DOWN = 264;
	private static final int KEY_LEFT = 263;
	private static final int KEY_RIGHT = 262;
	private static final int KEY_B = 66;
	private static final int KEY_A = 65;
	private static final int KEY_ENTER = 257;
	private static final int KEY_KP_ENTER = 335;

	private static final int[] SEQUENCE = {
		KEY_UP, KEY_UP, KEY_DOWN, KEY_DOWN,
		KEY_LEFT, KEY_RIGHT, KEY_LEFT, KEY_RIGHT,
		KEY_B, KEY_A, KEY_ENTER
	};

	private static boolean active;
	private static int progress;
	private static int lastKey = Integer.MIN_VALUE;
	private static long lastKeyAtMs;

	private MoulberryMode() {
	}

	public static boolean isActive() {
		return active;
	}

	public static boolean keyPressed(int key) {
		if (key == KEY_KP_ENTER) {
			key = KEY_ENTER;
		}
		long now = System.currentTimeMillis();
		// Ignore OS key-repeat spam (especially arrows held down).
		if (key == lastKey && now - lastKeyAtMs < 40L) {
			return false;
		}
		lastKey = key;
		lastKeyAtMs = now;

		if (progress < SEQUENCE.length && key == SEQUENCE[progress]) {
			progress++;
			if (progress >= SEQUENCE.length) {
				progress = 0;
				active = !active;
			}
			return true;
		}
		if (key == SEQUENCE[0]) {
			progress = 1;
			return true;
		}
		progress = 0;
		return false;
	}

	/** Letter keys also arrive as typed characters - accept B/A that way if keyPressed missed them. */
	public static boolean charTyped(char ch) {
		char lower = Character.toLowerCase(ch);
		// keyPressed already consumed this letter on the same press.
		if (lower == 'b' && lastKey == KEY_B) {
			return false;
		}
		if (lower == 'a' && lastKey == KEY_A) {
			return false;
		}
		if (lower == 'b') {
			return keyPressed(KEY_B);
		}
		if (lower == 'a') {
			return keyPressed(KEY_A);
		}
		return false;
	}
}
