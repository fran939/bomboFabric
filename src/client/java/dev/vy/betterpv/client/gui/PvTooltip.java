package dev.vy.betterpv.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared profile-viewer tooltip template.
 *
 * <p>Use {@link Line#title}, {@link Line#divider}, {@link Line#row}, {@link Line#meta},
 * and {@link Line#action} so Networth / Weight / Username History (and future tips)
 * share title rules, right-aligned values, muted secondary text, and section dividers.
 */
public final class PvTooltip {
	private static final int PAD = 8;
	private static final int ROW_GAP = 10;
	private static final int META_GAP = 4;
	private static final int DIVIDER_H = 7;

	public enum Kind {
		TEXT,
		DIVIDER,
		ROW,
		META,
		ACTION,
		BLANK
	}

	public record Span(String text, int color, boolean bold, boolean italic, boolean obfuscated) {
		public Span {
			text = text == null ? "" : text;
		}

		public Span(String text, int color, boolean bold, boolean italic) {
			this(text, color, bold, italic, false);
		}

		public Span(String text, int color, boolean bold) {
			this(text, color, bold, false, false);
		}

		public static Span of(String text, int color) {
			return new Span(text, color, false, false, false);
		}

		public static Span bold(String text, int color) {
			return new Span(text, color, true, false, false);
		}

		public static Span italic(String text, int color) {
			return new Span(text, color, false, true, false);
		}

		public static Span obfuscated(String text, int color, boolean bold) {
			return new Span(text, color, bold, false, true);
		}

		public Component toComponent() {
			Style style = Style.EMPTY
				.withItalic(this.italic)
				.withBold(this.bold ? Boolean.TRUE : null)
				.withObfuscated(this.obfuscated ? Boolean.TRUE : null)
				.withColor(TextColor.fromRgb(this.color & 0xFFFFFF));
			return Component.literal(this.text).setStyle(style);
		}
	}

	public record Line(Kind kind, List<Span> left, List<Span> value, List<Span> meta) {
		public Line {
			kind = kind == null ? Kind.TEXT : kind;
			left = left == null ? List.of() : List.copyOf(left);
			value = value == null ? List.of() : List.copyOf(value);
			meta = meta == null ? List.of() : List.copyOf(meta);
		}

		public Line(List<Span> spans) {
			this(Kind.TEXT, spans, List.of(), List.of());
		}

		public static Line of(String text, int color) {
			return text(List.of(Span.of(text, color)));
		}

		public static Line bold(String text, int color) {
			return text(List.of(Span.bold(text, color)));
		}

		public static Line plain(String text) {
			return of(text, PvDraw.COLOR_TEXT);
		}

		public static Line text(List<Span> spans) {
			return new Line(Kind.TEXT, spans, List.of(), List.of());
		}

		public static Line title(String text, int color) {
			return bold(text, color);
		}

		public static Line divider() {
			return new Line(Kind.DIVIDER, List.of(), List.of(), List.of());
		}

		public static Line blank() {
			return new Line(Kind.BLANK, List.of(), List.of(), List.of());
		}

		public static Line meta(String text) {
			return new Line(Kind.META, List.of(Span.of(text, PvDraw.COLOR_MUTED)), List.of(), List.of());
		}

		public static Line action(String text) {
			// Match inventory "Click to view value breakdown" (dark gray italic).
			return new Line(Kind.ACTION, List.of(Span.italic(text, 0xFF555555)), List.of(), List.of());
		}

		public static Line row(String label, int labelColor, String value, int valueColor) {
			return row(
				List.of(Span.of(label, labelColor)),
				List.of(Span.of(value, valueColor)),
				List.of()
			);
		}

		public static Line row(List<Span> label, List<Span> value) {
			return row(label, value, List.of());
		}

		public static Line row(List<Span> label, List<Span> value, List<Span> meta) {
			return new Line(Kind.ROW, label, value, meta);
		}

		public List<Span> spans() {
			if (this.kind == Kind.ROW) {
				List<Span> out = new ArrayList<>(this.left.size() + this.value.size() + this.meta.size());
				out.addAll(this.left);
				out.addAll(this.value);
				out.addAll(this.meta);
				return List.copyOf(out);
			}
			return this.left;
		}

		public Component toComponent() {
			MutableComponent out = Component.empty();
			for (Span span : spans()) {
				out.append(span.toComponent());
			}
			return out;
		}

		public boolean isBlank() {
			return this.kind == Kind.BLANK
				|| (this.kind != Kind.DIVIDER && spans().stream().allMatch(s -> s.text().isEmpty()));
		}

		public boolean isDivider() {
			return this.kind == Kind.DIVIDER;
		}
	}

	private PvTooltip() {
	}

	public static void draw(GuiGraphicsExtractor g, Font font, List<String> lines, int mouseX, int mouseY, int screenW, int screenH) {
		draw(g, font, lines, mouseX, mouseY, screenW, screenH, PvDraw.COLOR_TEXT);
	}

	public static void drawComponents(
		GuiGraphicsExtractor g,
		Font font,
		List<Component> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		List<Line> styled = new ArrayList<>(lines.size());
		for (Component line : lines) {
			styled.add(lineFromComponent(line));
		}
		drawStyled(g, font, styled, mouseX, mouseY, screenW, screenH);
	}

	/** Expand a lore/name Component into coloured spans (never flatten with getString). */
	private static Line lineFromComponent(Component line) {
		if (line == null) {
			return Line.blank();
		}
		List<Span> spans = spansFromComponent(line);
		if (spans.isEmpty() || spans.stream().allMatch(s -> s.text().isEmpty())) {
			return Line.blank();
		}
		return Line.text(spans);
	}

	private static List<Span> spansFromComponent(Component line) {
		List<Span> spans = new ArrayList<>();
		line.visit((style, text) -> {
			if (text == null || text.isEmpty()) {
				return Optional.empty();
			}
			int color = PvDraw.COLOR_TEXT;
			TextColor styleColor = style == null ? null : style.getColor();
			if (styleColor != null) {
				color = 0xFF000000 | styleColor.getValue();
			}
			boolean bold = style != null && Boolean.TRUE.equals(style.isBold());
			boolean italic = style != null && Boolean.TRUE.equals(style.isItalic());
			boolean obfuscated = style != null && Boolean.TRUE.equals(style.isObfuscated());
			spans.add(new Span(text, color, bold, italic, obfuscated));
			return Optional.empty();
		}, Style.EMPTY);
		return spans;
	}

	public static void draw(
		GuiGraphicsExtractor g,
		Font font,
		List<String> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		int textColor
	) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		drawStyled(g, font, lines.stream().map(line -> Line.of(line, textColor)).toList(), mouseX, mouseY, screenW, screenH);
	}

	public static void drawStyled(
		GuiGraphicsExtractor g,
		Font font,
		List<Line> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		drawStyled(g, font, lines, mouseX, mouseY, screenW, screenH, 0, Integer.MAX_VALUE, null);
	}

	/**
	 * Draw a styled tip; when {@code maxBodyH} is finite, content after the sticky header
	 * (title + optional divider) scrolls by {@code scrollY}.
	 */
	public static int drawStyled(
		GuiGraphicsExtractor g,
		Font font,
		List<Line> lines,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		int scrollY,
		int maxBodyH,
		int[] maxScrollOut
	) {
		if (lines == null || lines.isEmpty()) {
			if (maxScrollOut != null && maxScrollOut.length > 0) {
				maxScrollOut[0] = 0;
			}
			return 0;
		}

		int lineH = font.lineHeight + 3;
		int blankH = Math.max(4, font.lineHeight / 2);
		Metrics metrics = measure(font, lines, lineH, blankH);
		int boxW = metrics.contentW + PAD * 2;

		int stickyCount = stickyHeaderCount(lines);
		int stickyH = 0;
		int bodyH = 0;
		for (int i = 0; i < lines.size(); i++) {
			int h = metrics.heights.get(i);
			if (i < stickyCount) {
				stickyH += h;
			} else {
				bodyH += h;
			}
		}
		boolean scrolling = maxBodyH < Integer.MAX_VALUE / 4 && bodyH > maxBodyH;
		int visibleBodyH = scrolling ? Math.min(maxBodyH, bodyH) : bodyH;
		int maxScroll = Math.max(0, bodyH - visibleBodyH);
		int scroll = Math.max(0, Math.min(maxScroll, scrollY));
		if (maxScrollOut != null && maxScrollOut.length > 0) {
			maxScrollOut[0] = maxScroll;
		}

		int footerH = scrolling ? font.lineHeight + 2 : 0;
		int boxH = PAD * 2 + stickyH + visibleBodyH + footerH;

		int x = mouseX + 12;
		int y = mouseY - 12;
		if (x + boxW > screenW - 4) {
			x = mouseX - boxW - 8;
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4;
		}
		if (x < 4) {
			x = 4;
		}
		if (y < 4) {
			y = 4;
		}

		PvDraw.fill(g, x, y, boxW, boxH, 0xF0101018);
		g.outline(x, y, boxW, boxH, PvDraw.COLOR_BORDER);

		int ty = y + PAD;
		for (int i = 0; i < stickyCount; i++) {
			drawLine(g, font, lines.get(i), metrics, x, ty, boxW, lineH);
			ty += metrics.heights.get(i);
		}

		if (scrolling) {
			int bodyTop = ty;
			g.enableScissor(x + 1, bodyTop, x + boxW - 1, bodyTop + visibleBodyH);
			int drawY = bodyTop - scroll;
			for (int i = stickyCount; i < lines.size(); i++) {
				drawLine(g, font, lines.get(i), metrics, x, drawY, boxW, lineH);
				drawY += metrics.heights.get(i);
			}
			g.disableScissor();
			PvDraw.text(g, font, "Scroll for more",
				x + PAD, y + boxH - PAD - font.lineHeight, PvDraw.COLOR_MUTED);
		} else {
			for (int i = stickyCount; i < lines.size(); i++) {
				drawLine(g, font, lines.get(i), metrics, x, ty, boxW, lineH);
				ty += metrics.heights.get(i);
			}
		}
		return maxScroll;
	}

	public static void drawCenteredAbove(
		GuiGraphicsExtractor g,
		Font font,
		Component line,
		int cx,
		int topY,
		int screenW,
		int screenH
	) {
		if (line == null) {
			return;
		}
		int pad = 6;
		int lineH = font.lineHeight + 2;
		int boxW = Math.max(8, font.width(line)) + pad * 2;
		int boxH = lineH + pad * 2;
		int x = cx - boxW / 2;
		int y = topY - boxH - 3;
		drawLegacyBox(g, font, List.of(line), x, y, boxW, boxH, pad, lineH, screenW, screenH);
	}

	public static void drawCenteredLeft(
		GuiGraphicsExtractor g,
		Font font,
		Component line,
		int leftX,
		int cy,
		int screenW,
		int screenH
	) {
		if (line == null) {
			return;
		}
		int pad = 6;
		int lineH = font.lineHeight + 2;
		int boxW = Math.max(8, font.width(line)) + pad * 2;
		int boxH = lineH + pad * 2;
		int x = leftX - boxW - 3;
		int y = cy - boxH / 2;
		drawLegacyBox(g, font, List.of(line), x, y, boxW, boxH, pad, lineH, screenW, screenH);
	}

	private static int stickyHeaderCount(List<Line> lines) {
		if (lines.isEmpty()) {
			return 0;
		}
		int n = 1;
		if (lines.size() > 1 && lines.get(1).isDivider()) {
			n = 2;
		}
		return n;
	}

	private static Metrics measure(Font font, List<Line> lines, int lineH, int blankH) {
		int maxLabel = 0;
		int maxValue = 0;
		int maxMeta = 0;
		int maxText = 8;
		List<Integer> heights = new ArrayList<>(lines.size());
		for (Line line : lines) {
			switch (line.kind()) {
				case DIVIDER -> heights.add(DIVIDER_H);
				case BLANK -> heights.add(blankH);
				case ROW -> {
					heights.add(lineH);
					maxLabel = Math.max(maxLabel, widthOf(font, line.left()));
					maxValue = Math.max(maxValue, widthOf(font, line.value()));
					maxMeta = Math.max(maxMeta, widthOf(font, line.meta()));
				}
				case TEXT, META, ACTION -> {
					heights.add(line.isBlank() ? blankH : lineH);
					maxText = Math.max(maxText, widthOf(font, line.left()));
				}
			}
		}
		int rowW = maxLabel + ROW_GAP + maxValue + (maxMeta > 0 ? META_GAP + maxMeta : 0);
		int contentW = Math.max(maxText, rowW);
		return new Metrics(contentW, maxLabel, maxValue, maxMeta, heights);
	}

	private static void drawLine(
		GuiGraphicsExtractor g,
		Font font,
		Line line,
		Metrics metrics,
		int boxX,
		int ty,
		int boxW,
		int lineH
	) {
		int innerLeft = boxX + PAD;
		int innerRight = boxX + boxW - PAD;
		switch (line.kind()) {
			case DIVIDER -> {
				int mid = ty + DIVIDER_H / 2;
				PvDraw.fill(g, innerLeft, mid, boxW - PAD * 2, 1, PvDraw.COLOR_DIVIDER);
			}
			case BLANK -> {
			}
			case ROW -> {
				drawSpans(g, font, line.left(), innerLeft, ty);
				int valueW = widthOf(font, line.value());
				int valueColRight = innerRight
					- (metrics.maxMeta > 0 ? META_GAP + metrics.maxMeta : 0);
				int valueX = valueColRight - valueW;
				drawSpans(g, font, line.value(), valueX, ty);
				if (!line.meta().isEmpty()) {
					drawSpans(g, font, line.meta(), valueColRight + META_GAP, ty);
				}
			}
			case ACTION -> {
				// Slight indent so instructions don't read as data rows.
				drawSpans(g, font, line.left(), innerLeft + 4, ty);
			}
			case TEXT, META -> drawSpans(g, font, line.left(), innerLeft, ty);
		}
	}

	private static void drawSpans(GuiGraphicsExtractor g, Font font, List<Span> spans, int x, int y) {
		int cx = x;
		for (Span span : spans) {
			if (span == null || span.text().isEmpty()) {
				continue;
			}
			// Obfuscated / italic need Component rendering so the font applies §k scramble.
			if (span.obfuscated() || span.italic()) {
				Component c = span.toComponent();
				PvDraw.text(g, font, c, cx, y);
				cx += font.width(c);
			} else if (span.bold()) {
				PvDraw.textBold(g, font, span.text(), cx, y, span.color());
				cx += PvDraw.widthBold(font, span.text());
			} else {
				PvDraw.text(g, font, span.text(), cx, y, span.color());
				cx += font.width(span.text());
			}
		}
	}

	private static int widthOf(Font font, List<Span> spans) {
		int w = 0;
		for (Span span : spans) {
			w += font.width(span.toComponent());
		}
		return w;
	}

	private static void drawLegacyBox(
		GuiGraphicsExtractor g,
		Font font,
		List<Component> rendered,
		int x,
		int y,
		int boxW,
		int boxH,
		int pad,
		int lineH,
		int screenW,
		int screenH
	) {
		if (x + boxW > screenW - 4) {
			x = screenW - boxW - 4;
		}
		if (y + boxH > screenH - 4) {
			y = screenH - boxH - 4;
		}
		if (x < 4) {
			x = 4;
		}
		if (y < 4) {
			y = 4;
		}
		PvDraw.fill(g, x, y, boxW, boxH, 0xF0101018);
		g.outline(x, y, boxW, boxH, PvDraw.COLOR_BORDER);
		int ty = y + pad;
		for (Component line : rendered) {
			if (!line.getString().isEmpty()) {
				PvDraw.text(g, font, line, x + pad, ty);
			}
			ty += lineH;
		}
	}

	private record Metrics(int contentW, int maxLabel, int maxValue, int maxMeta, List<Integer> heights) {
	}
}
