package dev.vy.betterpv.client.gui.foraging.page;

import static dev.vy.betterpv.client.gui.foraging.ForagingUi.DISABLED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ENABLED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.PAD;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.STAT_ROW;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ForagingHotfData;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.foraging.ForagingUi;
import dev.vy.betterpv.client.gui.foraging.ForagingUi.HoverZone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Heart of the Forest subtab. */
public final class HotfPage {
	private final List<HoverZone> zones = new ArrayList<>();

	public void reset() {
		this.zones.clear();
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		// Wider sides so "Whispers" / "Heart of the Forest" aren't truncated.
		int leftW = Math.max(128, Math.min(156, w / 5));
		int rightW = Math.max(136, Math.min(168, w * 22 / 100));
		int centerW = w - leftW - rightW - GAP * 2;
		if (centerW < 180) {
			int shrink = 180 - centerW;
			int takeLeft = Math.min(shrink / 2, Math.max(0, leftW - 118));
			int takeRight = Math.min(shrink - takeLeft, Math.max(0, rightW - 124));
			leftW -= takeLeft;
			rightW -= takeRight;
			centerW = w - leftW - rightW - GAP * 2;
		}
		int leftX = x;
		int centerX = x + leftW + GAP;
		int rightX = centerX + centerW + GAP;

		PvDraw.innerPanel(g, leftX, y, leftW, h);
		PvDraw.innerPanel(g, centerX, y, centerW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		renderHotfLeft(g, font, snapshot, leftX, y, leftW, h);
		renderHotfCenter(g, font, snapshot, centerX, y, centerW, h, mx, my);
		renderHotfRight(g, font, snapshot, rightX, y, rightW, h);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void renderHotfLeft(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Whispers", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.WhisperPool> pools = snapshot.whisperPools();
		if (pools.isEmpty()) {
			ry = ForagingUi.statLine(g, font, "Total", "-", rx, ry, rw, PvDraw.COLOR_MUTED) + 3;
		} else {
			for (ForagingSnapshot.WhisperPool pool : pools) {
				if (ry + STAT_ROW * 2 > bottom) {
					break;
				}
				int poolColor = whisperPoolColor(pool.id());
				PvDraw.text(g, font, pool.label(), rx, ry, poolColor);
				ry += font.lineHeight + 1;
				int totalY = ry;
				ry = ForagingUi.statLine(g, font, "Total", FormatUtil.commas(pool.earned()),
					rx, ry, rw, poolColor) + 2;
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.title(pool.label() + " whispers", poolColor));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_MUTED,
					FormatUtil.commas(pool.earned()), poolColor));
				tip.add(PvTooltip.Line.row("Balance", PvDraw.COLOR_MUTED,
					FormatUtil.commas(pool.balance()), PvDraw.COLOR_ACCENT));
				tip.add(PvTooltip.Line.row("Spent", PvDraw.COLOR_MUTED,
					FormatUtil.commas(pool.spent()), PvDraw.COLOR_TEXT));
				this.zones.add(HoverZone.of(rx, totalY - font.lineHeight - 1, rw, STAT_ROW + font.lineHeight + 1, tip));
			}
		}

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Dailies", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = ForagingUi.statLine(g, font, "Trees cut", FormatUtil.commas(snapshot.currentDailyTreesCut()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 1;
		ry = ForagingUi.statLine(g, font, "Daily Tree Gifts", FormatUtil.commas(snapshot.currentDailyGifts()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 2;

		List<String> dailyLogs = snapshot.currentDailyLogs();
		if (!dailyLogs.isEmpty() && ry + font.lineHeight < bottom) {
			PvDraw.text(g, font, "Logs cut", rx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 2;
			for (String log : dailyLogs) {
				if (ry + STAT_ROW > bottom) {
					break;
				}
				PvDraw.text(g, font, ForagingUi.trim(font, ForagingUi.pretty(log), rw), rx, ry, PvDraw.COLOR_TEXT);
				ry += STAT_ROW;
			}
			ry += 2;
		}

		if (ry + STAT_ROW * 2 <= bottom) {
			ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
			PvDraw.text(g, font, "Daily effect", rx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 2;
			String effect = snapshot.dailyEffect().isBlank() ? "-" : ForagingUi.pretty(snapshot.dailyEffect());
			ForagingUi.wrapText(g, font, effect, rx, ry, rw, PvDraw.COLOR_GOLD);
			// current_daily_effect_last_changed is unmapped (value scale != daily_*_day). Hide raw until verified.
		}
	}

	private void renderHotfCenter(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		List<ForagingHotfData.PerkDef> perks = ForagingHotfData.perks();
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "HOTF layout unavailable",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = ForagingHotfData.maxX() + 1;
		int rows = ForagingHotfData.maxY() + 1;
		int innerW = w - PAD * 2;
		int innerH = h - PAD * 2;
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
		int gridX = x + PAD + Math.max(0, (innerW - gridW) / 2);
		int gridY = y + PAD + Math.max(0, (innerH - gridH) / 2);

		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		for (ForagingHotfData.PerkDef perk : perks) {
			int cx = gridX + perk.x() * (cell + cellGap);
			int cy = gridY + perk.y() * (cell + cellGap);
			int level = snapshot.hotfNodeLevel(perk.id());
			PvDraw.fill(g, cx, cy, cell, cell, ITEM_SLOT_BG);
			g.outline(cx, cy, cell, cell, ITEM_SLOT_BORDER);
			ItemStack stack = hotfPerkIcon(perk, level);
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
			boolean enabled = snapshot.hotfNodeEnabled(perk.id());
			if (level > 0) {
				tip.add(PvTooltip.Line.of(enabled ? "Enabled" : "Disabled",
					enabled ? ENABLED : DISABLED));
			}
			if (perk.ability()) {
				tip.add(PvTooltip.Line.of("Ability", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(cx, cy, cell, cell, tip));
		}
		g.disableScissor();
	}

	/**
	 * Locked: pale oak button · unlocked: stripped oak · maxed: oak log.
	 * Locked ability: dead bush · unlocked/maxed ability: stripped/oak log.
	 */
	private static ItemStack hotfPerkIcon(ForagingHotfData.PerkDef perk, int level) {
		if (perk.ability()) {
			if (level <= 0) {
				return new ItemStack(Items.DEAD_BUSH);
			}
			return new ItemStack(level >= perk.maxLevel() ? Items.OAK_LOG : Items.STRIPPED_OAK_LOG);
		}
		if (level <= 0) {
			return new ItemStack(Items.PALE_OAK_BUTTON);
		}
		if (level >= perk.maxLevel()) {
			return new ItemStack(Items.OAK_LOG);
		}
		return new ItemStack(Items.STRIPPED_OAK_LOG);
	}

	private void renderHotfRight(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Heart of the Forest", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 6;
		ry = ForagingUi.statLine(g, font, "HOTF XP", FormatUtil.shortXp(snapshot.hotfXp()),
			rx, ry, rw, PvDraw.COLOR_ACCENT) + 4;
		ry = ForagingUi.statLine(g, font, "Tokens spent", String.valueOf(snapshot.forestTokensSpent()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 6;
		// skill_tree.last_reset.foraging unit/reliability unverified; hide until confirmed.

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Ability", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;
		String ability = snapshot.selectedAbility().isBlank()
			? "-"
			: ForagingHotfData.displayName(snapshot.selectedAbility());
		ForagingUi.wrapText(g, font, ability, rx, ry, rw, PvDraw.COLOR_GOLD);
		ry += font.lineHeight + 8;

		if (snapshot.refundAbilityFree()) {
			ForagingUi.statLine(g, font, "Refund", "Free", rx, ry, rw, ENABLED);
		}
	}

	private static int whisperPoolColor(String id) {
		if (id == null) {
			return PvDraw.COLOR_ACCENT;
		}
		return switch (id.toLowerCase(java.util.Locale.ROOT)) {
			case "forest" -> 0xFF55C878;
			case "desert" -> 0xFFE8A838;
			default -> PvDraw.COLOR_ACCENT;
		};
	}
}
