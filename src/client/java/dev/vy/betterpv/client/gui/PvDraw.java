package dev.vy.betterpv.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

public final class PvDraw {
	public static final int COLOR_PANEL = 0xD0101018;
	public static final int COLOR_PANEL_OUTER = 0xE00A0A10;
	public static final int COLOR_PANEL_INNER = 0xC0181824;
	public static final int COLOR_BORDER = 0xFF3A3A4A;
	public static final int COLOR_TEXT = 0xFFE8E8F0;
	public static final int COLOR_MUTED = 0xFF9A9AAC;
	public static final int COLOR_GOLD = 0xFFFFAA00;
	public static final int COLOR_WHITE = 0xFFFFFFFF;
	public static final int COLOR_ACCENT = 0xFF5B8CFF;
	public static final int COLOR_DIVIDER = 0xFF2A2A33;
	/** Visible empty track (must read against inner panel). */
	public static final int COLOR_BAR_BG = 0xFF363646;
	public static final int COLOR_BAR_FILL = 0xFF3D8B5A;
	public static final int COLOR_BAR_FILL_SLAYER = 0xFF8B4A3D;
	/** Matches catgirllivid cosmetics name gradient. */
	public static final int COLOR_MAXED_BAR_LEFT = 0xFF9278C5;
	public static final int COLOR_MAXED_BAR_RIGHT = 0xFFF3E6FD;
	public static final int BAR_HEIGHT = 6;

	/** Cosmetics-inspired loop + vivid HSV rainbow for the Konami easter egg. */
	private static final float EASTER_EGG_SPEED = 0.12F;
	/** Horizontal resolution of the prebaked maxed-bar gradient (smooth, one blit). */
	private static final int MAXED_BAR_TEX_W = 256;
	private static final Identifier MAXED_BAR_TEX = Identifier.fromNamespaceAndPath("betterpv", "gui/maxed_xp_bar");
	private static boolean maxedBarRegistered;

	private PvDraw() {
	}

	public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int argb) {
		g.fill(x, y, x + w, y + h, argb);
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		fill(g, x, y, w, h, COLOR_PANEL);
		g.outline(x, y, w, h, COLOR_BORDER);
	}

	public static void innerPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		fill(g, x, y, w, h, COLOR_PANEL_INNER);
		g.outline(x, y, w, h, COLOR_BORDER);
	}

	public static void layeredPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int pad) {
		fill(g, x, y, w, h, COLOR_PANEL_INNER);
		g.outline(x, y, w, h, COLOR_BORDER);
	}

	public static void text(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color) {
		g.text(font, value, x, y, color, true);
	}

	/** Draw text scaled about its top-left corner (e.g. footer credits). */
	public static void textScaled(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, float scale) {
		if (scale == 1.0F) {
			text(g, font, value, x, y, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, value, 0, 0, color, true);
		g.pose().popMatrix();
	}

	public static void text(GuiGraphicsExtractor g, Font font, Component value, int x, int y) {
		if (value == null) {
			return;
		}
		// White is the fallback for unstyled runs; per-sibling Style colours still apply.
		g.text(font, value, x, y, COLOR_WHITE, true);
	}

	public static Component styled(String value, int rgb, boolean bold) {
		return styled(value, rgb, bold, false);
	}

	public static Component styled(String value, int rgb, boolean bold, boolean italic) {
		Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF));
		if (bold) {
			style = style.withBold(true);
		}
		if (italic) {
			style = style.withItalic(true);
		}
		return Component.literal(value).setStyle(style);
	}

	public static void textBold(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color) {
		text(g, font, styled(value, color, true), x, y);
	}

	public static void textBoldScaled(GuiGraphicsExtractor g, Font font, String value, int x, int y, int color, float scale) {
		if (scale == 1.0F) {
			textBold(g, font, value, x, y, color);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		text(g, font, styled(value, color, true), 0, 0);
		g.pose().popMatrix();
	}

	/**
	 * Y positions so a square item icon and a text line share the same vertical center.
	 * Works when {@code rowH} is shorter than the icon (dense lists used to pin icons to the top
	 * while centering text, which made icons look low).
	 */
	public record IconTextAlign(int iconY, int textY) {
		public static IconTextAlign of(int rowY, int rowH, int iconSize, int lineHeight) {
			int pairH = Math.max(Math.max(1, iconSize), Math.max(1, lineHeight));
			int pairTop = rowY + Math.max(0, (Math.max(1, rowH) - pairH) / 2);
			int iconY = pairTop + Math.max(0, (pairH - iconSize) / 2);
			int textY = pairTop + Math.max(0, (pairH - lineHeight) / 2);
			return new IconTextAlign(iconY, textY);
		}
	}

	public static int width(Font font, Component value) {
		return font.width(value);
	}

	public static int widthBold(Font font, String value) {
		return font.width(styled(value, 0xFFFFFF, true));
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, String value, int cx, int y, int color) {
		int x = cx - font.width(value) / 2;
		text(g, font, value, x, y, color);
	}

	public static void textCentered(GuiGraphicsExtractor g, Font font, Component value, int cx, int y) {
		int x = cx - font.width(value) / 2;
		text(g, font, value, x, y);
	}

	public static void textRight(GuiGraphicsExtractor g, Font font, String value, int rightX, int y, int color) {
		text(g, font, value, rightX - font.width(value), y, color);
	}

	public static int sectionHeader(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w) {
		text(g, font, label, x, y, COLOR_MUTED);
		int lineX = x + font.width(label) + 4;
		int lineY = y + font.lineHeight / 2;
		int lineW = x + w - lineX;
		if (lineW > 0) {
			fill(g, lineX, lineY, lineW, 1, COLOR_BORDER);
		}
		return font.lineHeight + 3;
	}

	public static void progressBar(GuiGraphicsExtractor g, int x, int y, int w, int h, float progress, int fillColor) {
		progressBar(g, x, y, w, h, progress, fillColor, false);
	}

	public static void progressBar(
		GuiGraphicsExtractor g,
		int x,
		int y,
		int w,
		int h,
		float progress,
		int fillColor,
		boolean maxedShiny
	) {
		if (w <= 0 || h <= 0) {
			return;
		}
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		drawXpBarSolidRounded(g, x, y, w, h, COLOR_BAR_BG, true);
		int filled = Math.max(0, Math.round(w * clamped));
		if (filled <= 0) {
			return;
		}
		// Always round both ends of the fill, including incomplete bars.
		if (MoulberryMode.isActive()) {
			drawXpBarAnimatedGradient(g, x, y, filled, h, true);
		} else if (maxedShiny) {
			drawXpBarMaxed(g, x, y, filled, h, true);
		} else {
			drawXpBarSolidRounded(g, x, y, filled, h, fillColor, true);
		}
	}

	private static void drawXpBarSolidRounded(
		GuiGraphicsExtractor g, int x, int y, int w, int h, int fillColor, boolean roundRight
	) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (h < 3 || w == 1) {
			fill(g, x, y + (h < 3 ? 0 : 1), w, h < 3 ? h : Math.max(1, h - 2), fillColor);
			return;
		}
		int leftInset = 1;
		int rightInset = roundRight ? 1 : 0;
		int bodyW = w - leftInset - rightInset;
		if (bodyW > 0) {
			fill(g, x + leftInset, y, bodyW, h, fillColor);
		}
		fill(g, x, y + 1, 1, Math.max(1, h - 2), fillColor);
		if (roundRight) {
			fill(g, x + w - 1, y + 1, 1, Math.max(1, h - 2), fillColor);
		}
	}

	/** Smooth lilac→cream maxed fill via one prebaked texture blit (not N fill rects). */
	private static void drawXpBarMaxed(
		GuiGraphicsExtractor g, int x, int y, int w, int h, boolean roundRight
	) {
		ensureMaxedBarTexture();
		if (!maxedBarRegistered || w <= 0 || h <= 0) {
			fill(g, x, y, w, h, COLOR_MAXED_BAR_LEFT);
			return;
		}
		int leftInset = 1;
		int rightInset = roundRight ? 1 : 0;
		int bodyW = w - leftInset - rightInset;
		if (bodyW > 0) {
			g.blit(
				RenderPipelines.GUI_TEXTURED,
				MAXED_BAR_TEX,
				x + leftInset, y,
				0, 0,
				bodyW, h,
				MAXED_BAR_TEX_W, BAR_HEIGHT,
				MAXED_BAR_TEX_W, BAR_HEIGHT
			);
		}
		fill(g, x, y + 1, 1, Math.max(1, h - 2), COLOR_MAXED_BAR_LEFT);
		if (roundRight) {
			// Tip colour follows the gradient at this fill width (not always cream).
			float t = MAXED_BAR_TEX_W <= 1
				? 1F
				: (float) Math.min(Math.max(w - 1, 0), MAXED_BAR_TEX_W - 1) / (float) (MAXED_BAR_TEX_W - 1);
			fill(g, x + w - 1, y + 1, 1, Math.max(1, h - 2), lerpArgb(COLOR_MAXED_BAR_LEFT, COLOR_MAXED_BAR_RIGHT, t));
		}
	}

	private static void ensureMaxedBarTexture() {
		if (maxedBarRegistered) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null) {
			return;
		}
		NativeImage image = new NativeImage(MAXED_BAR_TEX_W, BAR_HEIGHT, false);
		for (int px = 0; px < MAXED_BAR_TEX_W; px++) {
			float t = MAXED_BAR_TEX_W <= 1 ? 0F : (float) px / (float) (MAXED_BAR_TEX_W - 1);
			int argb = lerpArgb(COLOR_MAXED_BAR_LEFT, COLOR_MAXED_BAR_RIGHT, t);
			for (int py = 0; py < BAR_HEIGHT; py++) {
				image.setPixel(px, py, argb);
			}
		}
		client.getTextureManager().register(MAXED_BAR_TEX, new DynamicTexture(() -> "BetterPV maxed XP bar", image));
		maxedBarRegistered = true;
	}

	/** Scrolling HSV rainbow - tips sample the same t-map as the body so ends stay in sync. */
	private static void drawXpBarAnimatedGradient(
		GuiGraphicsExtractor g, int x, int y, int w, int h, boolean roundRight
	) {
		double time = System.currentTimeMillis() / 50.0D;
		float offset = (float) positiveModulo(time * EASTER_EGG_SPEED, 1.0D);
		final float spanHue = 0.85F;
		drawPillGradient(
			g, x, y, w, h,
			t -> rainbowArgb(positiveModulo(t * spanHue + offset, 1.0F)),
			true,
			roundRight,
			12
		);
	}

	@FunctionalInterface
	private interface BarColorAt {
		int at(float t);
	}

	private static void drawPillGradient(
		GuiGraphicsExtractor g,
		int x,
		int y,
		int w,
		int h,
		BarColorAt colorAt,
		boolean roundLeft,
		boolean roundRight,
		int maxSegments
	) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (h < 3) {
			fill(g, x, y, w, h, colorAt.at(0.5F));
			return;
		}

		java.util.function.IntUnaryOperator atPx = px -> {
			float t = w <= 1 ? 0F : (float) px / (float) (w - 1);
			return colorAt.at(t);
		};

		if (w == 1) {
			fill(g, x, y + 1, 1, h - 2, atPx.applyAsInt(0));
			return;
		}

		int fromCol = roundLeft ? 1 : 0;
		int toCol = roundRight ? w - 1 : w;
		if (roundLeft) {
			fill(g, x, y + 1, 1, h - 2, atPx.applyAsInt(0));
		}
		if (roundRight) {
			fill(g, x + w - 1, y + 1, 1, h - 2, atPx.applyAsInt(w - 1));
		}
		fillGradientSpan(g, x, y, w, h, fromCol, toCol, colorAt, maxSegments);
	}

	private static void fillGradientSpan(
		GuiGraphicsExtractor g,
		int barX,
		int y,
		int barW,
		int h,
		int fromCol,
		int toCol,
		BarColorAt colorAt,
		int maxSegments
	) {
		int span = toCol - fromCol;
		if (span <= 0) {
			return;
		}
		int segments = Math.min(span, Math.max(1, maxSegments));
		for (int s = 0; s < segments; s++) {
			int x0 = barX + fromCol + s * span / segments;
			int x1 = barX + fromCol + (s + 1) * span / segments;
			int midPx = Math.max(0, Math.min(barW - 1, (x0 + x1 - 1) / 2 - barX));
			float t = barW <= 1 ? 0F : (float) midPx / (float) (barW - 1);
			fill(g, x0, y, Math.max(1, x1 - x0), h, colorAt.at(t));
		}
	}

	/** Hue wheel colour; blends toward cosmetics purple so it still nods to the name gradient. */
	public static int chromaRgb(float hue) {
		return rainbowArgb(hue);
	}

	public static Component chromaText(String text, boolean bold) {
		if (text == null || text.isEmpty()) {
			return Component.empty();
		}
		float t = (System.currentTimeMillis() % 2800L) / 2800F;
		MutableComponent out = Component.empty();
		for (int i = 0; i < text.length(); i++) {
			int rgb = rainbowArgb(positiveModulo(t + i * 0.07F, 1.0F)) & 0xFFFFFF;
			Style style = Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(false);
			if (bold) {
				style = style.withBold(true);
			}
			out.append(Component.literal(String.valueOf(text.charAt(i))).setStyle(style));
		}
		return out;
	}

	/** Hue wheel colour; blends toward cosmetics purple so it still nods to the name gradient. */
	private static int rainbowArgb(float hue) {
		float h = positiveModulo(hue, 1.0F);
		int rgb = java.awt.Color.HSBtoRGB(h, 0.85F, 1.0F) & 0xFFFFFF;
		int cosmetics = COLOR_MAXED_BAR_LEFT & 0xFFFFFF;
		return lerpArgb(rgb | 0xFF000000, cosmetics | 0xFF000000, 0.18F);
	}

	private static float positiveModulo(float value, float modulus) {
		float remainder = value % modulus;
		return remainder < 0.0F ? remainder + modulus : remainder;
	}

	private static double positiveModulo(double value, double modulus) {
		double remainder = value % modulus;
		return remainder < 0.0D ? remainder + modulus : remainder;
	}

	private static int lerpArgb(int from, int to, float t) {
		t = Math.max(0F, Math.min(1F, t));
		int a1 = (from >>> 24) & 0xFF;
		int r1 = (from >>> 16) & 0xFF;
		int g1 = (from >>> 8) & 0xFF;
		int b1 = from & 0xFF;
		int a2 = (to >>> 24) & 0xFF;
		int r2 = (to >>> 16) & 0xFF;
		int g2 = (to >>> 8) & 0xFF;
		int b2 = to & 0xFF;
		int a = Math.round(a1 + (a2 - a1) * t);
		int r = Math.round(r1 + (r2 - r1) * t);
		int g = Math.round(g1 + (g2 - g1) * t);
		int b = Math.round(b1 + (b2 - b1) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor
	) {
		labeledBar(g, font, label, value, progress, x, y, w, fillColor, false);
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny
	) {
		labeledBar(g, font, label, value, progress, x, y, w, fillColor, maxedShiny, COLOR_TEXT, false);
	}

	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny,
		int labelColor,
		boolean labelBold
	) {
		drawLabelValue(g, font, label, value, x, y, w, labelColor, labelBold);
		int barY = y + font.lineHeight + 2;
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny);
	}

	/** Prefers a prebuilt label line (one prepareText) for skill / slayer grids. */
	public static void labeledBar(
		GuiGraphicsExtractor g,
		Font font,
		Component labelLine,
		float progress,
		int x,
		int y,
		int w,
		int fillColor,
		boolean maxedShiny
	) {
		g.text(font, labelLine, x, y, COLOR_WHITE, false);
		int barY = y + font.lineHeight + 2;
		progressBar(g, x, barY, w, BAR_HEIGHT, progress, fillColor, maxedShiny);
	}

	/** Label left, value flush-right within {@code w} (true alignment, not space padding). */
	public static void drawLabelValue(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		int x,
		int y,
		int w,
		int labelColor,
		boolean labelBold
	) {
		String left = label == null ? "" : label;
		String right = value == null ? "" : value;
		Component leftComp = styled(left, labelColor, labelBold);
		g.text(font, leftComp, x, y, COLOR_WHITE, false);
		if (!right.isEmpty()) {
			int vw = font.width(right);
			g.text(font, styled(right, COLOR_MUTED, false), x + Math.max(0, w - vw), y, COLOR_WHITE, false);
		}
	}

	/** Right-align {@code value} after {@code label} within {@code w} using space padding. */
	public static Component paddedLabelValue(Font font, String label, String value, int w) {
		return paddedLabelValue(font, label, value, w, COLOR_TEXT, false);
	}

	public static Component paddedLabelValue(
		Font font, String label, String value, int w, int labelColor, boolean labelBold
	) {
		String left = label == null ? "" : label;
		String right = value == null ? "" : value;
		Component leftComp = styled(left, labelColor, labelBold);
		int gap = w - font.width(leftComp) - font.width(right);
		int spaceW = Math.max(1, font.width(" "));
		int spaces = Math.max(1, gap / spaceW);
		MutableComponent line = Component.empty();
		line.append(leftComp);
		line.append(Component.literal(" ".repeat(spaces)));
		line.append(styled(right, COLOR_MUTED, false));
		return line;
	}
}
