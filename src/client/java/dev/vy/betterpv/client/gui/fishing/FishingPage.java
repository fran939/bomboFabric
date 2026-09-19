package dev.vy.betterpv.client.gui.fishing;

import dev.vy.betterpv.client.data.FishingSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.TrophyFishData;
import dev.vy.betterpv.client.data.TrophySkulls;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Fishing: single page, 1:2 split.
 * Left (~1/3) overview stats - click to flip to trophy frogs.
 * Right (~2/3) trophy fish.
 */
public final class FishingPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER = 4;
	private static final int STAT_ROW = 12;
	private static final int SEP_GAP = 10;
	private static final int BAR_FISH = 0xFF55AAFF;
	private static final int ITEM_SLOT_BG = 0xFF101018;
	private static final int SLOT = 16;
	private static final int SLOT_GAP = 3;
	private static final int TIER_BORDER = 1;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int FLIP_MS = 480;
	/** Soft violet for trophy names. */
	private static final int NAME_COLOR = 0xFFC9A6FF;
	private static final int TIER_DIAMOND = 0xFF55FFFF;
	private static final int TIER_GOLD = 0xFFFFE566;
	private static final int TIER_SILVER = 0xFFD0D0D8;
	private static final int TIER_BRONZE = 0xFFA05A2C;
	private static final int TIP_BG = 0xF0111116;
	private static final int TIP_BORDER = 0xFF2A2A33;
	private static final int TIP_TOTAL_LABEL = 0xFF9A9AA5;
	private static final int TIP_TOTAL_VALUE = 0xFFE8E8EE;
	private static final int TIP_COUNT = 0xFFC8C8D0;
	private static final int TIP_PAD_X = 12;
	private static final int TIP_PAD_Y = 10;
	private static final int TIP_ROW_GAP = 4;
	private static final int TIP_MIN_W = 160;

	private FishingSnapshot snapshot = FishingSnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();

	private int leftHitX;
	private int leftHitY;
	private int leftHitW;
	private int leftHitH;
	/** False = overview face, true = trophy frogs face. */
	private boolean leftFrogsFace;
	private long leftFlipStartMs;
	private boolean leftFlipTarget;

	public void apply(FishingSnapshot snapshot) {
		this.snapshot = snapshot == null ? FishingSnapshot.empty() : snapshot;
		this.zones.clear();
		TrophyFishData.ensureLoaded();
		TrophySkulls.ensureLoaded();
	}

	public FishingSnapshot snapshot() {
		return this.snapshot;
	}

	/** Flip left overview ↔ trophy frogs panel. */
	public boolean mouseClicked(double mx, double my) {
		if (mx < this.leftHitX || mx >= this.leftHitX + this.leftHitW
			|| my < this.leftHitY || my >= this.leftHitY + this.leftHitH) {
			return false;
		}
		if (this.leftFlipStartMs != 0L) {
			return true;
		}
		this.leftFlipTarget = !this.leftFrogsFace;
		this.leftFlipStartMs = System.currentTimeMillis();
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();

		// 1:2 - left overview/frogs, right trophy fish.
		int leftW = Math.max(90, w / 3);
		int rightW = Math.max(120, w - leftW - GAP);
		leftW = w - rightW - GAP;
		int leftX = x;
		int rightX = x + leftW + GAP;

		drawLeftFlipPanel(g, font, leftX, y, leftW, h, mouseX, mouseY);

		PvDraw.innerPanel(g, rightX, y, rightW, h);
		drawTrophyPanel(
			g, font, rightX, y, rightW, h,
			"Trophy Fish",
			FormatUtil.commas(this.snapshot.trophyFishTotal()) + " caught",
			this.snapshot.trophyFishLastCaught().isBlank()
				? null
				: "Last: " + prettyLast(this.snapshot.trophyFishLastCaught()),
			sortedRows(this.snapshot.trophyFish())
		);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawHover(g, font, mouseX, mouseY, screenW, screenH);
	}

	private void drawLeftFlipPanel(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY
	) {
		this.leftHitX = x;
		this.leftHitY = y;
		this.leftHitW = w;
		this.leftHitH = h;

		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		float flipProgress = 0F;
		boolean animating = this.leftFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.leftFrogsFace = this.leftFlipTarget;
				this.leftFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showFrogs = animating
			? (Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftFrogsFace)
			: this.leftFrogsFace;
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

		if (showFrogs) {
			drawTrophyPanel(
				g, font, x, y, w, h,
				"Trophy Frogs",
				FormatUtil.commas(this.snapshot.itemsFishedTrophyFrog()) + " caught",
				null,
				sortedRows(this.snapshot.trophyFrogs())
			);
		} else {
			drawOverviewFace(g, font, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawOverviewFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		ly = drawBar(g, font, "Fishing", String.valueOf(this.snapshot.fishingLevel()),
			this.snapshot.fishingFill(), this.snapshot.fishingMaxed(), BAR_FISH,
			this.snapshot.fishingHover(), lx, ly, lw) + BAR_AFTER + 2;

		ly = sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Items fished", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		ly = statLine(g, font, "Total", FormatUtil.commas(this.snapshot.itemsFishedTotal()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = statLine(g, font, "Normal", FormatUtil.commas(this.snapshot.itemsFishedNormal()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = statLine(g, font, "Treasure", FormatUtil.commas(this.snapshot.itemsFishedTreasure()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = statLine(g, font, "Large treasure", FormatUtil.commas(this.snapshot.itemsFishedLargeTreasure()),
			lx, ly, lw, PvDraw.COLOR_TEXT);
		ly = statLine(g, font, "Trophy fish", FormatUtil.commas(this.snapshot.itemsFishedTrophyFish()),
			lx, ly, lw, TIER_GOLD);
		ly = statLine(g, font, "Trophy frogs", FormatUtil.commas(this.snapshot.itemsFishedTrophyFrog()),
			lx, ly, lw, TIER_GOLD);
		ly = statLine(g, font, "Outstanding", FormatUtil.commas(this.snapshot.itemsFishedOutstanding()),
			lx, ly, lw, PvDraw.COLOR_MUTED);

		ly = sectionSeparator(g, font, x, ly, w);
		ly = statLine(g, font, "Sea creature kills", FormatUtil.commas(this.snapshot.seaCreatureKills()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = statLine(g, font, "Festival sharks", FormatUtil.commas(this.snapshot.festivalSharksKilled()),
			lx, ly, lw, TIER_GOLD);
	}

	private void drawTrophyPanel(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
		String title, String totalText, String subtitle, List<FishingSnapshot.TrophyRow> rows
	) {
		int cx = x + PAD;
		int cy = y + PAD;
		int innerW = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, title, cx, cy, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, totalText, cx + innerW, cy, PvDraw.COLOR_ACCENT);
		cy += font.lineHeight + 2;
		if (subtitle != null && !subtitle.isBlank()) {
			PvDraw.text(g, font, trim(font, subtitle, innerW), cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 2;
		}

		if (rows == null || rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No data",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		String summary = completionSummary(rows);
		PvDraw.text(g, font, trim(font, summary, innerW), cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		cy = panelSeparator(g, x, cy, w);

		int cols = Math.max(1, Math.min(rows.size(), Math.max(1, (innerW + SLOT_GAP) / (SLOT + SLOT_GAP))));
		int rowsN = (rows.size() + cols - 1) / cols;
		int gridX = cx;
		int gridY = cy;
		int gridH = rowsN * SLOT + Math.max(0, rowsN - 1) * SLOT_GAP;
		int fullRowW = cols * SLOT + Math.max(0, cols - 1) * SLOT_GAP;

		for (int i = 0; i < rows.size(); i++) {
			FishingSnapshot.TrophyRow row = rows.get(i);
			int col = i % cols;
			int r = i / cols;
			int rowStart = r * cols;
			int rowCount = Math.min(cols, rows.size() - rowStart);
			int rowOffset = 0;
			if (rowCount < cols) {
				int rowW = rowCount * SLOT + Math.max(0, rowCount - 1) * SLOT_GAP;
				rowOffset = (fullRowW - rowW) / 2;
			}
			int bx = gridX + rowOffset + col * (SLOT + SLOT_GAP);
			int by = gridY + r * (SLOT + SLOT_GAP);
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);

			TrophyFishData.Tier best = row.counts().bestDiscovered();
			if (best != null) {
				ItemStack skull = SkyBlockItemFactory.trophySkullStack(row.id() + "_" + best.suffix());
				if (skull.isEmpty()) {
					skull = new ItemStack(Items.TROPICAL_FISH);
				}
				g.item(skull, bx, by);
				drawTierBorder(g, bx, by, tierColor(best));
			} else {
				g.item(new ItemStack(Items.BARRIER), bx, by);
			}
			this.zones.add(HoverZone.trophy(bx, by, SLOT, SLOT, row));
		}

		cy = gridY + gridH + 2;
		cy = panelSeparator(g, x, cy, w);

		int barRow = font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		int avail = Math.max(0, bottom - cy);
		int gap = 3;
		int needed = 4 * barRow + 3 * gap;
		if (needed > avail && avail > 4 * barRow) {
			gap = Math.max(1, (avail - 4 * barRow) / 3);
		}
		TrophyFishData.Tier[] tiers = {
			TrophyFishData.Tier.BRONZE,
			TrophyFishData.Tier.SILVER,
			TrophyFishData.Tier.GOLD,
			TrophyFishData.Tier.DIAMOND
		};
		for (TrophyFishData.Tier tier : tiers) {
			if (cy + barRow > bottom) {
				break;
			}
			int have = 0;
			for (FishingSnapshot.TrophyRow row : rows) {
				if (row.counts().discovered(tier)) {
					have++;
				}
			}
			int max = rows.size();
			float fill = max <= 0 ? 0f : have / (float) max;
			String label = tierTitle(tier);
			String value = have + "/" + max;
			PvDraw.labeledBar(g, font, label, value, fill, cx, cy, innerW, tierColor(tier), false);
			this.zones.add(HoverZone.of(cx, cy, innerW, barRow, List.of(
				PvTooltip.Line.of(label + " " + value, tierColor(tier)),
				PvTooltip.Line.of(
					fill >= 1f ? "Complete" : FormatUtil.oneDecimal(fill * 100f) + "% of species",
					PvDraw.COLOR_MUTED
				)
			)));
			cy += barRow + gap;
		}
	}

	private static float easeInOutCubic(float t) {
		float x = Math.max(0F, Math.min(1F, t));
		return x < 0.5F
			? 4F * x * x * x
			: 1F - (float) Math.pow(-2F * x + 2F, 3) / 2F;
	}

	private static int panelSeparator(GuiGraphicsExtractor g, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return lineY + 1 + 4;
	}

	private static void drawTierBorder(GuiGraphicsExtractor g, int bx, int by, int color) {
		int t = TIER_BORDER;
		PvDraw.fill(g, bx, by, SLOT, t, color);
		PvDraw.fill(g, bx, by + SLOT - t, SLOT, t, color);
		PvDraw.fill(g, bx, by + t, t, SLOT - t * 2, color);
		PvDraw.fill(g, bx + SLOT - t, by + t, t, SLOT - t * 2, color);
	}

	private static String completionSummary(List<FishingSnapshot.TrophyRow> rows) {
		int n = rows.size();
		int diamond = 0;
		int discovered = 0;
		int tiers = 0;
		boolean exact = true;
		for (FishingSnapshot.TrophyRow row : rows) {
			if (row.counts().bestRank() >= 4) {
				diamond++;
			}
			if (row.anyDiscovered()) {
				discovered++;
			}
			tiers += row.counts().discoveredTier();
			exact = exact && row.countsExact();
		}
		int tierMax = n * 4;
		int pct = tierMax <= 0 ? 0 : Math.round(100f * tiers / tierMax);
		if (exact) {
			return diamond + "/" + n + " diamond · " + tiers + "/" + tierMax + " tiers (" + pct + "%)";
		}
		return discovered + "/" + n + " found · " + tiers + "/" + tierMax + " tiers (" + pct + "%)";
	}

	private static List<FishingSnapshot.TrophyRow> sortedRows(List<FishingSnapshot.TrophyRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return List.of();
		}
		List<FishingSnapshot.TrophyRow> out = new ArrayList<>(rows);
		out.sort(Comparator
			.comparingInt((FishingSnapshot.TrophyRow r) -> r.counts().bestRank()).reversed()
			.thenComparingInt((FishingSnapshot.TrophyRow r) -> r.counts().discoveredTier()).reversed()
			.thenComparingInt((FishingSnapshot.TrophyRow r) -> r.countsExact() ? r.totalCaught() : 0).reversed()
			.thenComparing(FishingSnapshot.TrophyRow::name, String.CASE_INSENSITIVE_ORDER));
		return out;
	}

	private static void drawTrophyStatTooltip(
		GuiGraphicsExtractor g, Font font, FishingSnapshot.TrophyRow row,
		int mouseX, int mouseY, int screenW, int screenH
	) {
		String title = row.name() == null ? "" : row.name();
		String totalLabel = "Total caught";
		String totalValue;
		if (row.countsExact()) {
			totalValue = FormatUtil.commas(row.totalCaught());
		} else if (row.anyDiscovered()) {
			totalValue = "Discovered";
		} else {
			totalValue = "Undiscovered";
		}

		TrophyFishData.Tier[] tiers = TrophyFishData.Tier.topToBottom();
		String[] tierLabels = new String[tiers.length];
		String[] tierValues = new String[tiers.length];
		for (int i = 0; i < tiers.length; i++) {
			TrophyFishData.Tier tier = tiers[i];
			tierLabels[i] = tierTitle(tier);
			int count = row.counts().of(tier);
			if (count <= 0) {
				tierValues[i] = "Undiscovered";
			} else if (row.countsExact()) {
				tierValues[i] = FormatUtil.commas(count);
			} else {
				tierValues[i] = "Caught";
			}
		}

		// Only show obtain/howto text when NEU item lore has it — do not invent.
		String howto = trophyHowtoFromNeu(row);

		int contentW = Math.max(font.width(PvDraw.styled(title, NAME_COLOR, true)), font.width(totalLabel) + 10 + font.width(PvDraw.styled(totalValue, TIP_TOTAL_VALUE, true)));
		for (int i = 0; i < tiers.length; i++) {
			contentW = Math.max(contentW, font.width(tierLabels[i]) + 10 + font.width(tierValues[i]));
		}
		if (howto != null && !howto.isBlank()) {
			contentW = Math.max(contentW, font.width(howto));
		}
		contentW = Math.max(TIP_MIN_W, contentW);
		int boxW = contentW + TIP_PAD_X * 2;

		int titleH = font.lineHeight;
		int dividerGap = 3;
		int totalH = font.lineHeight + 1;
		int tierH = font.lineHeight;
		int howtoH = (howto == null || howto.isBlank()) ? 0 : font.lineHeight + TIP_ROW_GAP;
		int boxH = TIP_PAD_Y * 2
			+ titleH
			+ dividerGap + 1 + dividerGap
			+ totalH
			+ TIP_ROW_GAP
			+ tiers.length * tierH
			+ Math.max(0, tiers.length - 1) * TIP_ROW_GAP
			+ howtoH;

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

		PvDraw.fill(g, x, y, boxW, boxH, TIP_BG);
		g.outline(x, y, boxW, boxH, TIP_BORDER);

		int tx = x + TIP_PAD_X;
		int ty = y + TIP_PAD_Y;
		int right = x + boxW - TIP_PAD_X;

		PvDraw.text(g, font, PvDraw.styled(title, NAME_COLOR, true), tx, ty);
		ty += titleH + dividerGap;
		PvDraw.fill(g, tx, ty, contentW, 1, TIP_BORDER);
		ty += 1 + dividerGap;

		PvDraw.text(g, font, totalLabel, tx, ty, TIP_TOTAL_LABEL);
		var totalComp = PvDraw.styled(totalValue, TIP_TOTAL_VALUE, true);
		PvDraw.text(g, font, totalComp, right - font.width(totalComp), ty);
		ty += totalH + TIP_ROW_GAP;

		for (int i = 0; i < tiers.length; i++) {
			PvDraw.text(g, font, tierLabels[i], tx, ty, tierColor(tiers[i]));
			PvDraw.textRight(g, font, tierValues[i], right, ty, TIP_COUNT);
			ty += tierH + TIP_ROW_GAP;
		}
		if (howto != null && !howto.isBlank()) {
			PvDraw.text(g, font, howto, tx, ty, PvDraw.COLOR_MUTED);
		}
	}

	/**
	 * First NEU lore line for the best/bronze trophy item when it looks like obtain text.
	 * Returns null when unavailable — do not invent howto copy.
	 */
	private static String trophyHowtoFromNeu(FishingSnapshot.TrophyRow row) {
		if (row == null || row.id() == null || row.id().isBlank()) {
			return null;
		}
		TrophyFishData.Tier best = row.counts() == null ? null : row.counts().bestDiscovered();
		TrophyFishData.Tier tier = best == null ? TrophyFishData.Tier.BRONZE : best;
		String iconId = row.id() + "_" + tier.suffix();
		JsonObject neu = NeuRepoCache.get(iconId);
		if (neu == null || !neu.has("lore") || !neu.get("lore").isJsonArray()) {
			return null;
		}
		JsonArray lore = neu.getAsJsonArray("lore");
		for (JsonElement el : lore) {
			if (el == null || !el.isJsonPrimitive()) {
				continue;
			}
			String raw = el.getAsString();
			if (raw == null || raw.isBlank()) {
				continue;
			}
			String plain = raw.replaceAll("§.", "").trim();
			if (plain.isBlank()) {
				continue;
			}
			String lower = plain.toLowerCase(Locale.ROOT);
			if (lower.startsWith("caught") || lower.contains("near ") || lower.contains("found ")
				|| lower.contains("obtain") || lower.startsWith("can be") || lower.contains("fished")) {
				return plain;
			}
			// First non-empty lore line is usually the obtain hint for trophy items.
			return plain;
		}
		return null;
	}

	private static String tierTitle(TrophyFishData.Tier tier) {
		return switch (tier) {
			case DIAMOND -> "Diamond";
			case GOLD -> "Gold";
			case SILVER -> "Silver";
			case BRONZE -> "Bronze";
		};
	}

	private static int tierColor(TrophyFishData.Tier tier) {
		return switch (tier) {
			case DIAMOND -> TIER_DIAMOND;
			case GOLD -> TIER_GOLD;
			case SILVER -> TIER_SILVER;
			case BRONZE -> TIER_BRONZE;
		};
	}

	private static String prettyLast(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		String[] parts = raw.split("/");
		String fish = parts[0].replace('_', ' ');
		StringBuilder sb = new StringBuilder();
		for (String p : fish.split(" ")) {
			if (p.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(p.charAt(0)));
			if (p.length() > 1) {
				sb.append(p.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		if (parts.length > 1) {
			sb.append(" · ").append(parts[1].substring(0, 1).toUpperCase(Locale.ROOT))
				.append(parts[1].substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	private int drawBar(
		GuiGraphicsExtractor g, Font font, String label, String value, float fill, boolean maxed,
		int color, String hover, int x, int y, int w
	) {
		String shown = fitValue(font, label, value == null ? "" : value, w);
		PvDraw.labeledBar(g, font, trim(font, label, Math.max(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed);
		int bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT;
		if (hover != null && !hover.isBlank()) {
			this.zones.add(HoverZone.of(x, y, w, bottom - y, List.of(PvTooltip.Line.of(hover, PvDraw.COLOR_TEXT))));
		}
		return bottom;
	}

	private static int sectionSeparator(GuiGraphicsExtractor g, Font font, int panelX, int y, int panelW) {
		int visualBottom = y - Math.max(0, STAT_ROW - font.lineHeight);
		int pad = SEP_GAP / 2;
		int lineY = visualBottom + pad;
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return lineY + 1 + pad;
	}

	private static int statLine(
		GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor
	) {
		String r = value == null ? "" : value;
		int leftMax = Math.max(8, w - font.width(r) - 6);
		PvDraw.text(g, font, trim(font, label, leftMax), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, r, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private static String fitValue(Font font, String label, String value, int w) {
		int max = Math.max(8, w - font.width(label) - 10);
		return trim(font, value, max);
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) + ellipsisW > maxW) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mx >= zone.x && mx < zone.x + zone.w && my >= zone.y && my < zone.y + zone.h) {
				if (zone.trophy != null) {
					drawTrophyStatTooltip(g, font, zone.trophy, mx, my, screenW, screenH);
				} else {
					PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH);
				}
				return;
			}
		}
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines, FishingSnapshot.TrophyRow trophy) {
		static HoverZone of(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
			return new HoverZone(x, y, w, h, lines, null);
		}

		static HoverZone trophy(int x, int y, int w, int h, FishingSnapshot.TrophyRow row) {
			return new HoverZone(x, y, w, h, List.of(), row);
		}
	}
}
