package dev.vy.betterpv.client.gui.nav;

import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class IconButtonBar {
	public static final int TAB = 26;
	public static final int GAP = 1;
	public static final int SEAM = 3;

	public static final int BTN = TAB;

	private final List<Hit> hits = new ArrayList<>();

	public void clearHits() {
		this.hits.clear();
	}

	public boolean click(double mouseX, double mouseY) {
		for (Hit hit : this.hits) {
			if (hit.contains(mouseX, mouseY)) {
				hit.onClick.run();
				return true;
			}
		}
		return false;
	}

	public void drawInnerButton(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		ItemStack icon,
		Component label,
		boolean selected,
		boolean hovered,
		Runnable onClick
	) {
		int bg = selected ? 0xFF2A3A55 : hovered ? 0xFF222230 : 0xFF16161E;
		int border = selected ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.fill(g, x, y, TAB, TAB, bg);
		g.outline(x, y, TAB, TAB, border);
		g.item(icon, x + 5, y + 5);
		this.hits.add(new Hit(x, y, TAB, TAB, onClick));
		if (hovered) {
			tooltipAbove(g, font, label, x + TAB / 2, y);
		}
	}

	public void drawTopFrameTabs(
		GuiGraphicsExtractor g,
		Font font,
		int panelX,
		int panelY,
		int mouseX,
		int mouseY,
		List<Entry> entries,
		Object selectedKey
	) {
		// Tab body sits fully above the panel; selected tab opens a 1px seam into the border.
		int tabY = panelY - TAB;
		int cx = panelX + 4;
		for (Entry entry : entries) {
			boolean selected = entry.key.equals(selectedKey);
			boolean hovered = mouseX >= cx && mouseX < cx + TAB && mouseY >= tabY && mouseY < panelY + 1;
			drawTopTab(g, font, cx, tabY, panelY, entry.icon, entry.label, selected, hovered, entry.onClick);
			cx += TAB + GAP;
		}
	}

	public void drawLeftFrameTabs(
		GuiGraphicsExtractor g,
		Font font,
		int panelX,
		int panelY,
		int panelH,
		int mouseX,
		int mouseY,
		List<Entry> entries,
		Object selectedKey
	) {
		int tabX = panelX - TAB;
		int cy = panelY;
		int tabSize = TAB;
		int gap = GAP;
		if (panelH > 0 && !entries.isEmpty()) {
			int n = entries.size();
			int need = n * TAB + (n - 1) * GAP;
			if (need > panelH) {
				gap = Math.max(0, (panelH - n * TAB) / Math.max(1, n - 1));
				need = n * TAB + (n - 1) * gap;
			}
			if (need > panelH) {
				// Even with zero gap, shrink tabs so the stack ends at panel bottom.
				tabSize = Math.max(16, panelH / n);
				gap = Math.max(0, (panelH - n * tabSize) / Math.max(1, n - 1));
			}
		}
		for (Entry entry : entries) {
			boolean selected = entry.key.equals(selectedKey);
			boolean hovered = mouseX >= tabX && mouseX < panelX + 1 && mouseY >= cy && mouseY < cy + tabSize;
			drawLeftTab(
				g, font, tabX, cy, panelX, tabSize,
				entry.icon, entry.texture, entry.textureSize, entry.label, selected, hovered, entry.onClick
			);
			cy += tabSize + gap;
		}
	}

	public void drawLeftFrameTabs(
		GuiGraphicsExtractor g,
		Font font,
		int panelX,
		int panelY,
		int mouseX,
		int mouseY,
		List<Entry> entries,
		Object selectedKey
	) {
		drawLeftFrameTabs(g, font, panelX, panelY, 0, mouseX, mouseY, entries, selectedKey);
	}

	private void drawTopTab(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int panelY,
		ItemStack icon,
		Component label,
		boolean selected,
		boolean hovered,
		Runnable onClick
	) {
		int h = panelY - y + (selected ? 1 : 0);
		if (h < TAB) {
			h = TAB;
		}
		int bg = selected ? PvDraw.COLOR_PANEL : hovered ? 0xF0202030 : 0xF012121C;
		int border = selected || hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;

		PvDraw.fill(g, x, y, TAB, h, bg);
		g.fill(x, y, x + TAB, y + 1, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x + TAB - 1, y, x + TAB, y + h, border);
		if (!selected) {
			g.fill(x, y + h - 1, x + TAB, y + h, border);
		} else {
			PvDraw.fill(g, x + 1, panelY, TAB - 2, 1, PvDraw.COLOR_PANEL);
		}

		g.item(icon, x + (TAB - 16) / 2, y + (TAB - 16) / 2);
		this.hits.add(new Hit(x, y, TAB, Math.max(h, TAB), onClick));
		// Tooltips only - avoid building tip Components until hovered.
		if (hovered) {
			tooltipAbove(g, font, label, x + TAB / 2, y);
		}
	}

	private void drawLeftTab(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int panelX,
		int tabH,
		ItemStack icon,
		Identifier texture,
		int textureSize,
		Component label,
		boolean selected,
		boolean hovered,
		Runnable onClick
	) {
		int h = Math.max(16, tabH);
		int w = panelX - x + (selected ? 1 : 0);
		if (w < TAB) {
			w = TAB;
		}
		int bg = selected ? PvDraw.COLOR_PANEL : hovered ? 0xF0202030 : 0xF012121C;
		int border = selected || hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;

		PvDraw.fill(g, x, y, w, h, bg);
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x, y + h - 1, x + w, y + h, border);
		if (!selected) {
			g.fill(x + w - 1, y, x + w, y + h, border);
		} else {
			// Open a 1px seam into the panel border so the selected tab connects.
			PvDraw.fill(g, panelX, y + 1, 1, Math.max(1, h - 2), PvDraw.COLOR_PANEL);
		}

		int iconDraw = Math.min(16, Math.max(10, h - 4));
		int ix = x + (TAB - iconDraw) / 2;
		int iy = y + (h - iconDraw) / 2;
		if (texture != null) {
			int tex = Math.max(1, textureSize);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, ix, iy, 0, 0, iconDraw, iconDraw, tex, tex, tex, tex);
		} else if (iconDraw >= 16) {
			g.item(icon, ix, iy);
		} else {
			// Item renders are fixed 16×16 — center and accept slight clip into pad when tabs shrink.
			g.item(icon, x + (TAB - 16) / 2, y + (h - 16) / 2);
		}
		this.hits.add(new Hit(x, y, Math.max(w, TAB), h, onClick));
		if (hovered) {
			tooltipLeft(g, font, label, x, y + h / 2);
		}
	}

	public void addHit(int x, int y, int w, int h, Runnable onClick) {
		this.hits.add(new Hit(x, y, w, h, onClick));
	}

	public void maybeTooltip(GuiGraphicsExtractor g, Font font, Component label, boolean hovered, int x, int y) {
		if (!hovered) {
			return;
		}
		int[] screen = screenSize();
		PvTooltip.drawComponents(g, font, List.of(label), x, y, screen[0], screen[1]);
	}

	private static void tooltipAbove(GuiGraphicsExtractor g, Font font, Component label, int cx, int topY) {
		int[] screen = screenSize();
		PvTooltip.drawCenteredAbove(g, font, label, cx, topY, screen[0], screen[1]);
	}

	private static void tooltipLeft(GuiGraphicsExtractor g, Font font, Component label, int leftX, int cy) {
		int[] screen = screenSize();
		PvTooltip.drawCenteredLeft(g, font, label, leftX, cy, screen[0], screen[1]);
	}

	private static int[] screenSize() {
		var window = Minecraft.getInstance().getWindow();
		return new int[] { window.getGuiScaledWidth(), window.getGuiScaledHeight() };
	}

	public record Entry(Object key, ItemStack icon, Component label, Runnable onClick, Identifier texture, int textureSize) {
		public Entry(Object key, ItemStack icon, Component label, Runnable onClick) {
			this(key, icon, label, onClick, null, 16);
		}

		public Entry(Object key, ItemStack icon, Component label, Runnable onClick, Identifier texture) {
			this(key, icon, label, onClick, texture, 16);
		}
	}

	private record Hit(int x, int y, int w, int h, Runnable onClick) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
		}
	}
}
