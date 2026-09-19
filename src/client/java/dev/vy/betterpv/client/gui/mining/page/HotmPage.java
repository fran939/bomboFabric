package dev.vy.betterpv.client.gui.mining.page;

import dev.vy.betterpv.client.data.MiningHotmData;
import dev.vy.betterpv.client.data.MiningSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.mining.MiningUi;
import dev.vy.betterpv.client.gui.mining.MiningUi.HoverZone;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** HOTM subtab: powders | perk tree | forge / unlocked flip. Owns HOTM-specific scroll fields. */
public final class HotmPage {
	private static final int SIDE_MIN = 118;
	private static final int FLIP_MS = 480;
	private static final int PANEL_HOVER = 0x0AFFFFFF;

	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;
	private int hotmUnlockedScrollLeft;
	private int hotmUnlockedScrollTop;
	private int hotmUnlockedScrollW;
	private int hotmUnlockedScrollH;
	private int hotmUnlockedMaxScroll;

	/** false = forge face, true = unlocked perks face. */
	private boolean unlockedFace;
	private boolean flipTarget;
	private long flipStartMs;
	private int forgeHitX;
	private int forgeHitY;
	private int forgeHitW;
	private int forgeHitH;

	public void reset() {
		this.scroll = 0;
		this.maxScroll = 0;
		this.hotmUnlockedScrollLeft = 0;
		this.hotmUnlockedScrollTop = 0;
		this.hotmUnlockedScrollW = 0;
		this.hotmUnlockedScrollH = 0;
		this.hotmUnlockedMaxScroll = 0;
		this.unlockedFace = false;
		this.flipStartMs = 0L;
		this.forgeHitW = 0;
		this.forgeHitH = 0;
	}

	public void onEnter() {
		this.scroll = 0;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.forgeHitW <= 0 || this.forgeHitH <= 0) {
			return false;
		}
		if (mx < this.forgeHitX || mx >= this.forgeHitX + this.forgeHitW
			|| my < this.forgeHitY || my >= this.forgeHitY + this.forgeHitH) {
			return false;
		}
		if (this.flipStartMs != 0L) {
			return true;
		}
		this.flipTarget = !this.unlockedFace;
		this.flipStartMs = System.currentTimeMillis();
		return true;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.hotmUnlockedMaxScroll <= 0) {
			return false;
		}
		if (mouseX < this.hotmUnlockedScrollLeft
			|| mouseX >= this.hotmUnlockedScrollLeft + this.hotmUnlockedScrollW
			|| mouseY < this.hotmUnlockedScrollTop
			|| mouseY >= this.hotmUnlockedScrollTop + this.hotmUnlockedScrollH) {
			return false;
		}
		int next = Math.max(0, Math.min(
			this.hotmUnlockedMaxScroll,
			this.scroll + (scrollY > 0 ? -14 : 14)
		));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int sideW = Math.max(SIDE_MIN, Math.min(140, w / 5));
		int centerW = w - sideW * 2 - MiningUi.GAP * 2;
		if (centerW < 160) {
			sideW = Math.max(100, (w - MiningUi.GAP * 2 - 160) / 2);
			centerW = w - sideW * 2 - MiningUi.GAP * 2;
		}

		int leftX = x;
		int centerX = x + sideW + MiningUi.GAP;
		int rightX = centerX + centerW + MiningUi.GAP;

		PvDraw.innerPanel(g, leftX, y, sideW, h);
		PvDraw.innerPanel(g, centerX, y, centerW, h);

		renderHotmLeft(g, font, snapshot, zones, leftX, y, sideW, h, mx, my);
		renderHotmTree(g, font, snapshot, zones, centerX, y, centerW, h, mx, my);
		renderForgeFlipPanel(g, font, snapshot, zones, rightX, y, sideW, h, mx, my);
	}

	private void renderHotmLeft(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;

		ry = MiningUi.drawBar(g, font, "HOTM", String.valueOf(snapshot.hotmLevel()),
			snapshot.hotmFill(), snapshot.hotmMaxed(), MiningUi.BAR_HOTM,
			snapshot.hotmHover(), rx, ry, rw, mx, my, zones) + MiningUi.BAR_AFTER;
		ry = MiningUi.drawBar(g, font, "Mining", String.valueOf(snapshot.miningLevel()),
			snapshot.miningFill(), snapshot.miningMaxed(), MiningUi.BAR_MINING,
			snapshot.miningHover(), rx, ry, rw, mx, my, zones) + MiningUi.BAR_AFTER + 2;

		ry = MiningUi.drawPowdersBlock(g, font, snapshot, rx, ry, rw, zones) + 2;
		ry = MiningUi.statLine(g, font, "Tokens spent", String.valueOf(snapshot.tokensSpent()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 2;

		MiningHotmData.PerkDef cotm = MiningHotmData.perk("core_of_the_mountain");
		int cotmLevel = snapshot.nodeLevel("core_of_the_mountain");
		int cotmMax = cotm == null ? 10 : cotm.maxLevel();
		PvDraw.text(g, font, "Core of the Mountain", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		int cotmColor = cotmLevel >= cotmMax ? MiningUi.PLACED : PvDraw.COLOR_ACCENT;
		String cotmValue = cotmLevel + " / " + cotmMax;
		PvDraw.text(g, font, cotmValue, rx, ry, cotmColor);
		zones.add(HoverZone.of(rx, ry - font.lineHeight - 2, rw, font.lineHeight + MiningUi.STAT_ROW,
			List.of(
				PvTooltip.Line.of("Core of the Mountain", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Level " + cotmLevel + " / " + cotmMax, PvDraw.COLOR_ACCENT)
			)));
		ry += font.lineHeight + 4;

		PvDraw.text(g, font, "Ability", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		MiningUi.wrapText(g, font, snapshot.selectedAbilityName(), rx, ry, rw, PvDraw.COLOR_ACCENT);
		ry += font.lineHeight + 4;

		int unlockedCount = unlockedNodes(snapshot).size();
		PvDraw.text(g, font, "Unlocked", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		PvDraw.text(g, font, String.valueOf(unlockedCount), rx, ry, PvDraw.COLOR_ACCENT);
		zones.add(HoverZone.of(rx, ry - font.lineHeight - 2, rw, font.lineHeight + MiningUi.STAT_ROW,
			List.of(
				PvTooltip.Line.of("Unlocked perks: " + unlockedCount, PvDraw.COLOR_TEXT)
			)));
	}

	private List<UnlockedNode> unlockedNodes(MiningSnapshot snapshot) {
		List<UnlockedNode> out = new ArrayList<>();
		for (MiningHotmData.PerkDef perk : MiningHotmData.perks()) {
			if ("core_of_the_mountain".equals(perk.id())) {
				continue;
			}
			int level = snapshot.nodeLevel(perk.id());
			if (level <= 0) {
				continue;
			}
			out.add(new UnlockedNode(
				perk.id(),
				perk.name(),
				level,
				perk.maxLevel(),
				perk.ability(),
				level >= perk.maxLevel()
			));
		}
		out.sort(Comparator
			.comparing(UnlockedNode::ability).reversed()
			.thenComparing(UnlockedNode::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private void renderForgeFlipPanel(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		this.forgeHitX = x;
		this.forgeHitY = y;
		this.forgeHitW = w;
		this.forgeHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.flipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.flipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.unlockedFace = this.flipTarget;
				this.flipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showUnlocked = animating
			? (Math.cos(angle) < 0.0 ? this.flipTarget : this.unlockedFace)
			: this.unlockedFace;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		if (showUnlocked) {
			drawUnlockedFace(g, font, snapshot, zones, x, y, w, h);
		} else {
			this.hotmUnlockedScrollLeft = 0;
			this.hotmUnlockedScrollTop = 0;
			this.hotmUnlockedScrollW = 0;
			this.hotmUnlockedScrollH = 0;
			this.hotmUnlockedMaxScroll = 0;
			this.maxScroll = 0;
			drawForgeFace(g, font, snapshot, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawForgeFace(GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, int x, int y, int w, int h) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;
		PvDraw.text(g, font, "Forge", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		drawForgeList(g, font, snapshot, rx, ry, rw, y + h - MiningUi.PAD);
	}

	private void drawUnlockedFace(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h
	) {
		int rx = x + MiningUi.PAD;
		int ry = y + MiningUi.PAD;
		int rw = w - MiningUi.PAD * 2;
		int bottom = y + h - MiningUi.PAD;
		List<UnlockedNode> unlocked = unlockedNodes(snapshot);
		PvDraw.text(g, font, "Unlocked (" + unlocked.size() + ")", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;

		if (unlocked.isEmpty()) {
			this.hotmUnlockedScrollLeft = 0;
			this.hotmUnlockedScrollTop = 0;
			this.hotmUnlockedScrollW = 0;
			this.hotmUnlockedScrollH = 0;
			this.hotmUnlockedMaxScroll = 0;
			this.maxScroll = 0;
			this.scroll = 0;
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			return;
		}

		int availH = Math.max(font.lineHeight, bottom - ry);
		int rowH = MiningUi.STAT_ROW + 1;
		int contentH = unlocked.size() * rowH;
		this.hotmUnlockedScrollLeft = rx;
		this.hotmUnlockedScrollTop = ry;
		this.hotmUnlockedScrollW = rw;
		this.hotmUnlockedScrollH = availH;
		this.hotmUnlockedMaxScroll = Math.max(0, contentH - availH);
		this.maxScroll = this.hotmUnlockedMaxScroll;
		this.scrollTop = ry;
		this.scrollH = availH;
		this.scroll = Math.max(0, Math.min(this.scroll, this.hotmUnlockedMaxScroll));

		g.enableScissor(rx, ry, rx + rw, ry + availH);
		int yy = ry - this.scroll;
		for (UnlockedNode node : unlocked) {
			if (yy + rowH >= ry && yy <= ry + availH) {
				int valueColor = node.ability() ? PvDraw.COLOR_GOLD
					: (node.maxed() ? MiningUi.PLACED : PvDraw.COLOR_TEXT);
				String right = node.level() + "/" + node.maxLevel();
				int rightW = Math.max(1, font.width(right));
				String left = MiningUi.trim(font, node.name(), Math.max(8, rw - rightW - 6));
				PvDraw.text(g, font, left, rx, yy, valueColor);
				PvDraw.textRight(g, font, right, rx + rw, yy, PvDraw.COLOR_MUTED);
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(node.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of("Level " + node.level() + " / " + node.maxLevel(), PvDraw.COLOR_ACCENT));
				if (node.ability()) {
					tip.add(PvTooltip.Line.of("Pickaxe Ability", PvDraw.COLOR_GOLD));
				}
				zones.add(HoverZone.of(rx, Math.max(ry, yy), rw, Math.min(rowH, ry + availH - Math.max(ry, yy)), tip));
			}
			yy += rowH;
		}
		g.disableScissor();
	}

	private int drawForgeList(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, int x, int y, int w, int bottom
	) {
		List<MiningSnapshot.ForgeProcess> forge = snapshot.forge();
		if (forge.isEmpty()) {
			PvDraw.text(g, font, "No items forging", x, y, PvDraw.COLOR_MUTED);
			return y + MiningUi.STAT_ROW;
		}
		int ly = y;
		for (MiningSnapshot.ForgeProcess p : forge) {
			if (ly + MiningUi.STAT_ROW > bottom) {
				break;
			}
			String left = "Slot " + p.slot();
			String right = MiningUi.trim(font, p.name(), Math.max(24, w - font.width(left) - 8));
			ly = MiningUi.statLine(g, font, left, right, x, ly, w, PvDraw.COLOR_TEXT) + 1;
			String ago = forgeAgo(p.startTimeMs());
			if (!ago.isBlank() && ly + MiningUi.STAT_ROW <= bottom) {
				ly = MiningUi.statLine(g, font, "", ago, x, ly, w, PvDraw.COLOR_MUTED) + 1;
			}
		}
		return ly;
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void renderHotmTree(
		GuiGraphicsExtractor g, Font font, MiningSnapshot snapshot, List<HoverZone> zones,
		int x, int y, int w, int h, int mx, int my
	) {
		List<MiningHotmData.PerkDef> perks = MiningHotmData.perks();
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "HOTM layout unavailable",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = MiningHotmData.maxX() + 1;
		int rows = MiningHotmData.maxY() + 1;
		int innerW = w - MiningUi.PAD * 2;
		int innerH = h - MiningUi.PAD * 2;
		int baseIcon = 16;
		int basePad = 2;
		int baseCell = baseIcon + basePad * 2;
		int baseGap = 2;
		int baseGridW = cols * baseCell + Math.max(0, cols - 1) * baseGap;
		int baseGridH = rows * baseCell + Math.max(0, rows - 1) * baseGap;
		// Integer zoom only - fractional scale softens item textures.
		int zoom = 1;
		if (baseGridW > 0 && baseGridH > 0) {
			zoom = Math.max(1, Math.min(innerW / baseGridW, innerH / baseGridH));
			zoom = Math.min(3, zoom);
		}
		int cell = baseCell * zoom;
		int cellGap = baseGap * zoom;
		int iconDraw = baseIcon * zoom;
		int gridW = cols * cell + Math.max(0, cols - 1) * cellGap;
		int gridH = rows * cell + Math.max(0, rows - 1) * cellGap;
		int gridX = x + MiningUi.PAD + Math.max(0, (innerW - gridW) / 2);
		int gridY = y + MiningUi.PAD + Math.max(0, (innerH - gridH) / 2);

		this.scrollTop = y + MiningUi.PAD;
		this.scrollH = innerH;

		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		for (MiningHotmData.PerkDef perk : perks) {
			int cx = gridX + perk.x() * (cell + cellGap);
			int cy = gridY + perk.y() * (cell + cellGap);
			int level = snapshot.nodeLevel(perk.id());

			PvDraw.fill(g, cx, cy, cell, cell, MiningUi.ITEM_SLOT_BG);
			g.outline(cx, cy, cell, cell, MiningUi.ITEM_SLOT_BORDER);

			ItemStack stack = perkIcon(perk, level);
			int ix = cx + Math.max(0, (cell - iconDraw) / 2);
			int iy = cy + Math.max(0, (cell - iconDraw) / 2);
			if (zoom == 1) {
				g.item(stack, ix, iy);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(ix, iy);
				g.pose().scale(zoom, zoom);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			}

			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(perk.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.of("Level " + level + " / " + perk.maxLevel(), PvDraw.COLOR_ACCENT));
			if (perk.ability()) {
				tip.add(PvTooltip.Line.of("Pickaxe Ability", PvDraw.COLOR_GOLD));
			}
			if (perk.powder() != null && !perk.powder().isBlank() && !perk.powder().startsWith("(")) {
				tip.add(PvTooltip.Line.of(prettyPowder(perk.powder()) + " powder", powderColor(perk.powder())));
			}
			zones.add(HoverZone.of(cx, cy, cell, cell, tip));
		}
		g.disableScissor();
	}

	/**
	 * Abilities: locked coal block, unlocked diamond block.
	 * CotM: diamond block only when maxed, otherwise coal block.
	 * Other perks: locked coal, unlocked emerald, maxed diamond.
	 */
	private static ItemStack perkIcon(MiningHotmData.PerkDef perk, int level) {
		if ("core_of_the_mountain".equals(perk.id())) {
			return new ItemStack(level >= perk.maxLevel() ? Items.DIAMOND_BLOCK : Items.COAL_BLOCK);
		}
		if (perk.ability()) {
			return new ItemStack(level > 0 ? Items.DIAMOND_BLOCK : Items.COAL_BLOCK);
		}
		if (level <= 0) {
			return new ItemStack(Items.COAL);
		}
		if (level >= perk.maxLevel()) {
			return new ItemStack(Items.DIAMOND);
		}
		return new ItemStack(Items.EMERALD);
	}

	private static String prettyPowder(String powder) {
		if (powder == null || powder.isBlank()) {
			return "";
		}
		return MiningUi.title(powder);
	}

	private static int powderColor(String powder) {
		if (powder == null) {
			return PvDraw.COLOR_MUTED;
		}
		return switch (powder.toUpperCase(Locale.ROOT)) {
			case "MITHRIL" -> MiningUi.BAR_MITHRIL;
			case "GEMSTONE" -> MiningUi.BAR_GEM;
			case "GLACITE" -> MiningUi.BAR_GLACITE;
			default -> PvDraw.COLOR_MUTED;
		};
	}

	private static String forgeAgo(long startMs) {
		if (startMs <= 0L) {
			return "";
		}
		long ago = Math.max(0L, System.currentTimeMillis() - startMs);
		long mins = ago / 60_000L;
		if (mins < 60L) {
			return mins + "m ago";
		}
		long hours = mins / 60L;
		if (hours < 48L) {
			return hours + "h ago";
		}
		return (hours / 24L) + "d ago";
	}

	private record UnlockedNode(
		String id, String name, int level, int maxLevel, boolean ability, boolean maxed
	) {
	}
}
