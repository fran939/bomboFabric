package dev.vy.betterpv.client.gui.garden.page;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.GardenData;
import dev.vy.betterpv.client.data.GardenSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.garden.GardenUi;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static dev.vy.betterpv.client.gui.garden.GardenUi.*;

/** Garden crops + composter subpage. */
public final class CropsPage {
	private int scroll;
	private int maxScroll;
	private int scrollTop;
	private int scrollH;

	public void render(GardenSnapshot snap, GardenUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my) {
		int leftW = Math.max(140, w * 40 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = leftW - PAD * 2;

		List<GardenSnapshot.CropRow> crops = snap.crops();
		PvDraw.text(g, font, "Crop milestones", lx, ly, PvDraw.COLOR_MUTED);
		if (!crops.isEmpty()) {
			long sum = 0L;
			for (GardenSnapshot.CropRow crop : crops) {
				sum += crop.milestone();
			}
			String avg = "Average: " + FormatUtil.oneDecimal(sum / (double) crops.size());
			if (font.width("Crop milestones") + 10 + font.width(avg) <= lw) {
				PvDraw.textRight(g, font, avg, lx + lw, ly, PvDraw.COLOR_MUTED);
			}
		}
		ly += font.lineHeight + 4;

		// Readable 2-col Overview-style rows; last odd item centered; row gap fills panel height.
		int cols = 2;
		int colGap = GAP;
		int colW = (lw - colGap) / cols;
		int miniBarH = 3;
		int labelGap = 2;
		int blockH = font.lineHeight + labelGap + miniBarH;
		int minRowH = Math.max(ICON, blockH) + 2;
		int gridRows = crops.isEmpty() ? 0 : (crops.size() + cols - 1) / cols;
		int gridTop = ly;
		int gridH = y + h - PAD - gridTop;
		int rowH = gridRows <= 0 ? minRowH : Math.max(minRowH, gridH / gridRows);
		this.scrollTop = gridTop;
		this.scrollH = gridH;
		this.maxScroll = 0;
		this.scroll = 0;

		g.enableScissor(lx, gridTop, lx + lw, gridTop + gridH);
		int lastRow = Math.max(0, gridRows - 1);
		int lastRowCount = crops.isEmpty() ? 0 : ((crops.size() - 1) % cols) + 1;
		for (int i = 0; i < crops.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int rowItems = row == lastRow ? lastRowCount : cols;
			int rowW = rowItems * colW + Math.max(0, rowItems - 1) * colGap;
			int rowX = lx + Math.max(0, (lw - rowW) / 2);
			int bx = rowX + col * (colW + colGap);
			int by = gridTop + row * rowH;
			if (by + rowH < gridTop || by > gridTop + gridH) {
				continue;
			}
			GardenSnapshot.CropRow crop = crops.get(i);
			int textX = bx + ICON + 3;
			int barW = Math.max(24, colW - ICON - 3);
			int iconY = by + Math.max(0, (blockH - ICON) / 2);
			GardenUi.drawIcon(g, crop.iconId(), bx, iconY, ICON, GardenData.cropPackModel(crop.id()));
			String name = crop.name();
			String lvl = String.valueOf(crop.milestone());
			int nameMax = Math.max(8, barW - font.width(lvl) - 4);
			PvDraw.text(g, font, GardenUi.trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, lvl, textX + barW, by, crop.milestoneMaxed() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			PvDraw.progressBar(g, textX, by + font.lineHeight + labelGap, barW, miniBarH,
				crop.milestoneFill(), BAR_CROP, crop.milestoneMaxed());
			ui.zones.add(new GardenUi.HoverZone(bx, by, colW, blockH + 1, ui.cropMilestoneTip(snap, crop)));
		}
		g.disableScissor();

		GardenSnapshot.Composter c = snap.composter();
		int rx = x + leftW + GAP + PAD;
		int ry = y + PAD;
		int rw = rightW - PAD * 2;
		PvDraw.text(g, font, "Composter", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		int cColGap = 8;
		int cColW = (rw - cColGap) / 2;
		int leftCol = rx;
		int rightCol = rx + cColW + cColGap;
		int row1 = ry;
		int row2 = ry + STAT_ROW + 1;
		GardenUi.statLine(g, font, "Organic Matter", FormatUtil.shortXp(c.organicMatter()), leftCol, row1, cColW, PvDraw.COLOR_TEXT);
		GardenUi.statLine(g, font, "Fuel", FormatUtil.shortXp(c.fuelUnits()), leftCol, row2, cColW, PvDraw.COLOR_TEXT);
		GardenUi.statLine(g, font, "Compost", FormatUtil.shortXp(c.compostUnits()), rightCol, row1, cColW, BAR_COMPOST);
		GardenUi.statLine(g, font, "Items", FormatUtil.shortXp(c.compostItems()), rightCol, row2, cColW, PvDraw.COLOR_MUTED);
		ry = row2 + STAT_ROW + 4;

		PvDraw.fill(g, rx, ry, rw, 1, PvDraw.COLOR_BORDER);
		ry += 5;

		PvDraw.text(g, font, "Upgrades", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		List<GardenSnapshot.ComposterUpgrade> upgrades = c.upgrades();
		int upBarH = 3;
		int upLabelGap = 2;
		int upBlockH = font.lineHeight + upLabelGap + upBarH;
		int upRowH = Math.max(ICON, upBlockH) + 3;
		int availH = Math.max(upRowH, y + h - PAD - ry);
		if (!upgrades.isEmpty()) {
			upRowH = Math.max(Math.max(ICON, upBlockH) + 1, availH / upgrades.size());
		}
		for (int i = 0; i < upgrades.size(); i++) {
			int by = ry + i * upRowH;
			GardenSnapshot.ComposterUpgrade u = upgrades.get(i);
			int textX = rx + ICON + 4;
			int barW = Math.max(20, rw - ICON - 4);
			int iconY = by + Math.max(0, (upBlockH - ICON) / 2);
			GardenUi.drawIcon(g, u.iconId(), rx, iconY, ICON, GardenData.composterUpgradePackModel(u.id()));
			String name = shortComposterName(u);
			String lvl = u.level() + " / " + u.maxLevel();
			int nameMax = Math.max(8, barW - font.width(lvl) - 4);
			PvDraw.text(g, font, GardenUi.trim(font, name, nameMax), textX, by, PvDraw.COLOR_TEXT);
			PvDraw.textRight(g, font, lvl, textX + barW, by, u.maxed() ? COMPLETED_C : PvDraw.COLOR_MUTED);
			PvDraw.progressBar(g, textX, by + font.lineHeight + upLabelGap, barW, upBarH, u.fill(), BAR_COMPOST, u.maxed());
			ui.zones.add(new GardenUi.HoverZone(rx, by, rw, upBlockH + 1, List.of(
				PvTooltip.Line.of(u.name(), PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Purchased: " + u.level(), COMPLETED_C),
				PvTooltip.Line.of("Missing: " + u.missing(), REJECTED_C),
				PvTooltip.Line.of(u.maxed() ? "MAX" : FormatUtil.oneDecimal(u.fill() * 100) + "%", PvDraw.COLOR_MUTED)
			)));
		}
	}


	public void resetScroll() {
		this.scroll = 0;
	}

	public void clearScrollExtents() {
		this.maxScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.maxScroll <= 0) {
			return false;
		}
		if (mouseY < this.scrollTop || mouseY >= this.scrollTop + this.scrollH) {
			return false;
		}
		int next = Math.max(0, Math.min(this.maxScroll, this.scroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.scroll) {
			return false;
		}
		this.scroll = next;
		return true;
	}

	private static String shortComposterName(GardenSnapshot.ComposterUpgrade u) {
		if (u == null || u.id() == null) {
			return "?";
		}
		return switch (u.id().toLowerCase(Locale.ROOT)) {
			case "organic_matter_cap" -> "Organic Cap";
			case "cost_reduction" -> "Cost Reduction";
			case "multi_drop" -> "Multi Drop";
			case "fuel_cap" -> "Fuel Cap";
			case "speed" -> "Speed";
			default -> u.name();
		};
	}
}
