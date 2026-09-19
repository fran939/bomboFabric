package dev.vy.betterpv.client.cosmetics;

public final class GradientColors {
	private GradientColors() {
	}

	public static int interpolateRgb(int startColor, int endColor, float progress) {
		float clamped = clamp(progress, 0.0F, 1.0F);
		int startRed = (startColor >> 16) & 0xFF;
		int startGreen = (startColor >> 8) & 0xFF;
		int startBlue = startColor & 0xFF;
		int endRed = (endColor >> 16) & 0xFF;
		int endGreen = (endColor >> 8) & 0xFF;
		int endBlue = endColor & 0xFF;
		int red = clampInt((int) (startRed + ((endRed - startRed) * clamped)), 0, 255);
		int green = clampInt((int) (startGreen + ((endGreen - startGreen) * clamped)), 0, 255);
		int blue = clampInt((int) (startBlue + ((endBlue - startBlue) * clamped)), 0, 255);
		return (red << 16) | (green << 8) | blue;
	}

	public static int interpolateArgb(int startColor, int endColor, float progress) {
		return interpolateRgb(startColor, endColor, progress) | 0xFF000000;
	}

	public static float linearProgress(int index, int count) {
		if (count <= 1) return 0.0F;
		return (float) index / (float) (count - 1);
	}

	public static int linearArgb(int startRgb, int endRgb, int index, int count) {
		return interpolateArgb(startRgb, endRgb, linearProgress(index, count));
	}

	public static float clamp(float value, float min, float max) {
		return Math.max(min, Math.min(max, value));
	}

	public static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
