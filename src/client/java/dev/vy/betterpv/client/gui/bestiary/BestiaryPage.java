package dev.vy.betterpv.client.gui.bestiary;

import dev.vy.betterpv.client.data.BestiaryData;
import dev.vy.betterpv.client.data.BestiarySnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Bestiary left panel: family grid for the selected island/category.
 * Right island buttons live in {@code ProfileViewerScreen} (inventory split layout).
 */
public final class BestiaryPage {
	private static final int PAD = 6;
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 2;
	private static final int SCROLL_STEP = (SLOT + SLOT_GAP) * 3;
	private static final int DIST_BAR_H = 24;
	private static final int DIST_BAR_GAP = 6;
	private static final int DIST_SEP = 1;
	private static final int DIST_BG = 0xFF101018;
	private static final int DIST_BORDER = 0xFF2A2A35;
	private static final int DIST_LOW = 0xFF555555;
	private static final int DIST_MID = 0xFFFFAA00;
	private static final int DIST_HIGH = 0xFF55FF55;
	private static final int DIST_MAXED = 0xFF9278C5;

	private BestiarySnapshot snapshot = BestiarySnapshot.empty();
	private String categoryId = "";
	private String searchQuery = "";
	private int scroll;
	private int maxScroll;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;

	private final Map<String, ItemStack> iconCache = new HashMap<>();
	private String hoverKey = "";
	private List<Component> hoverTip = List.of();

	public void apply(BestiarySnapshot snapshot) {
		this.snapshot = snapshot == null ? BestiarySnapshot.empty() : snapshot;
		this.iconCache.clear();
		this.scroll = 0;
		if (this.categoryId.isBlank()) {
			List<BestiaryData.Category> cats = BestiaryData.categories();
			for (BestiaryData.Category cat : cats) {
				if (cat != null && !cat.families().isEmpty()) {
					this.categoryId = cat.id();
					break;
				}
			}
			if (this.categoryId.isBlank() && !cats.isEmpty()) {
				this.categoryId = cats.get(0).id();
			}
		}
	}

	public int totalUnlockedTiers() {
		return this.snapshot.totalUnlockedTiers();
	}

	public int totalMaxTiers() {
		return this.snapshot.totalMaxTiers();
	}

	public int claimedMilestone() {
		return this.snapshot.claimedMilestone();
	}

	public int milestone() {
		return this.snapshot.milestone();
	}

	public String lastKilledMob() {
		return this.snapshot.lastKilledMob();
	}

	public void setCategory(String categoryId) {
		String next = categoryId == null ? "" : categoryId;
		if (!next.equals(this.categoryId)) {
			this.categoryId = next;
			this.scroll = 0;
			this.hoverKey = "";
			this.hoverTip = List.of();
		}
	}

	public String categoryId() {
		return this.categoryId;
	}

	public void setSearchQuery(String query) {
		this.searchQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
	}

	public boolean searchHighlightsCategory(String categoryId) {
		if (this.searchQuery.isBlank() || categoryId == null) {
			return false;
		}
		BestiarySnapshot.CategoryProgress progress = this.snapshot.category(categoryId);
		if (progress == null) {
			return false;
		}
		for (BestiarySnapshot.FamilyProgress fam : progress.families()) {
			if (matchesSearch(fam)) {
				return true;
			}
		}
		BestiaryData.Category cat = progress.category();
		return cat != null && cat.name().toLowerCase(Locale.ROOT).contains(this.searchQuery);
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.hoverKey = "";
		this.hoverTip = List.of();
		BestiaryData.ensureLoaded();
		BestiarySnapshot.CategoryProgress progress = this.snapshot.category(this.categoryId);
		if (progress == null && !BestiaryData.categories().isEmpty()) {
			progress = this.snapshot.category(BestiaryData.categories().get(0).id());
			if (progress != null) {
				this.categoryId = progress.category().id();
			}
		}

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = Math.max(8, w - PAD * 2);

		String title = progress == null ? "Bestiary" : progress.category().name();
		PvDraw.text(g, font, title, lx, ly, PvDraw.COLOR_TEXT);
		ly += font.lineHeight + 2;

		if (progress != null) {
			String counts = progress.familiesUnlocked() + "/" + progress.families().size()
				+ " · Tiers " + FormatUtil.commas(progress.unlockedTiers())
				+ "/" + FormatUtil.commas(progress.maxTiers());
			PvDraw.text(g, font, counts, lx, ly, PvDraw.COLOR_MUTED);
		} else {
			PvDraw.text(g, font, "No bestiary data", lx, ly, PvDraw.COLOR_MUTED);
		}
		ly += font.lineHeight + 4;

		String milestone = "Milestone " + FormatUtil.commas(this.snapshot.milestone())
			+ "  ·  +" + FormatUtil.commas(this.snapshot.claimedMilestone()) + " HP";
		PvDraw.text(g, font, milestone, lx, ly, PvDraw.COLOR_GOLD);
		ly += font.lineHeight + 2;
		if (!this.snapshot.lastKilledMob().isBlank()) {
			PvDraw.text(g, font, "Last: " + prettyMobId(this.snapshot.lastKilledMob()), lx, ly, PvDraw.COLOR_MUTED);
			ly += font.lineHeight + 4;
		} else {
			ly += 4;
		}

		List<BestiarySnapshot.FamilyProgress> families = progress == null ? List.of() : progress.families();
		boolean showDistBar = !families.isEmpty();
		int distReserve = showDistBar ? DIST_BAR_H + DIST_BAR_GAP : 0;
		this.gridX = lx;
		this.gridY = ly;
		this.gridW = lw;
		this.gridH = Math.max(0, y + h - PAD - ly - distReserve);

		if (families.isEmpty()) {
			this.maxScroll = 0;
			PvDraw.textCentered(g, font, "No families",
				x + w / 2, this.gridY + Math.max(0, this.gridH / 2 - font.lineHeight / 2), PvDraw.COLOR_MUTED);
			return;
		}

		int cols = Math.max(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP));
		int rows = (families.size() + cols - 1) / cols;
		int contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP;
		this.maxScroll = Math.max(0, contentH - this.gridH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		int barY = this.gridY + this.gridH + DIST_BAR_GAP;
		int[] barWidths = barSegmentWidths(families.size(), Math.max(0, lw - 2));
		BestiarySnapshot.FamilyProgress linked = findLinkedFamily(
			families, cols, lx, ly, barY, lw, barWidths, mouseX, mouseY
		);
		String linkedId = linked == null || linked.family() == null ? "" : linked.family().id();

		int rowPitch = SLOT + SLOT_GAP;
		int firstRow = Math.max(0, this.scroll / rowPitch);
		int lastRow = Math.min(rows - 1, (this.scroll + this.gridH) / rowPitch + 1);
		int first = firstRow * cols;
		int last = Math.min(families.size() - 1, (lastRow + 1) * cols - 1);

		g.enableScissor(lx, this.gridY, lx + lw, this.gridY + this.gridH);
		for (int i = first; i <= last; i++) {
			BestiarySnapshot.FamilyProgress fam = families.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * rowPitch;
			int by = ly + row * rowPitch - this.scroll;
			if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
				continue;
			}
			boolean linkedHover = !linkedId.isBlank()
				&& fam.family() != null
				&& linkedId.equals(fam.family().id());
			boolean searchHit = matchesSearch(fam);
			PvDraw.fill(g, bx, by, SLOT, SLOT, 0xFF101018);
			int border = searchHit ? 0xFF55FF55 : linkedHover ? PvDraw.COLOR_ACCENT : 0xFF2A2A35;
			g.outline(bx, by, SLOT, SLOT, border);
			ItemStack icon = familyIcon(fam);
			int pad = Math.max(0, (SLOT - 16) / 2);
			if (!icon.isEmpty()) {
				g.item(icon, bx + pad, by + pad);
			}
		}
		g.disableScissor();

		drawTierDistributionBar(g, families, lx, barY, lw, barWidths, linkedId);
		if (linked != null) {
			this.hoverKey = linked.family().id();
			this.hoverTip = tooltipFor(linked);
		}
	}

	/**
	 * Family under the cursor on either the icon grid or the matching distribution segment.
	 */
	private BestiarySnapshot.FamilyProgress findLinkedFamily(
		List<BestiarySnapshot.FamilyProgress> families,
		int cols,
		int gridLeft,
		int gridTop,
		int barY,
		int barW,
		int[] barWidths,
		int mouseX,
		int mouseY
	) {
		int rowPitch = SLOT + SLOT_GAP;
		boolean overGrid = mouseX >= this.gridX && mouseX < this.gridX + this.gridW
			&& mouseY >= this.gridY && mouseY < this.gridY + this.gridH;
		if (overGrid) {
			for (int i = 0; i < families.size(); i++) {
				int col = i % cols;
				int row = i / cols;
				int bx = gridLeft + col * rowPitch;
				int by = gridTop + row * rowPitch - this.scroll;
				if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
					continue;
				}
				if (mouseX >= bx && mouseX < bx + SLOT && mouseY >= by && mouseY < by + SLOT) {
					return families.get(i);
				}
			}
		}

		boolean overBar = mouseY >= barY && mouseY < barY + DIST_BAR_H
			&& mouseX >= gridLeft && mouseX < gridLeft + barW;
		if (!overBar || barWidths == null) {
			return null;
		}
		int innerX = gridLeft + 1;
		int innerW = Math.max(0, barW - 2);
		int cursor = innerX;
		for (int i = 0; i < families.size() && i < barWidths.length; i++) {
			int segW = Math.max(1, barWidths[i]);
			if (cursor >= innerX + innerW) {
				break;
			}
			segW = Math.min(segW, innerX + innerW - cursor);
			if (mouseX >= cursor && mouseX < cursor + segW) {
				return families.get(i);
			}
			cursor += segW;
			if (i < families.size() - 1 && cursor < innerX + innerW) {
				cursor += DIST_SEP;
			}
		}
		return null;
	}

	/**
	 * Equal-width segments across the category.
	 * Colour = that family's own completion (tier / tiersMax).
	 */
	private void drawTierDistributionBar(
		GuiGraphicsExtractor g,
		List<BestiarySnapshot.FamilyProgress> families,
		int x,
		int y,
		int w,
		int[] widths,
		String linkedId
	) {
		if (families.isEmpty() || w <= 0) {
			return;
		}
		PvDraw.fill(g, x, y, w, DIST_BAR_H, DIST_BG);
		g.outline(x, y, w, DIST_BAR_H, DIST_BORDER);

		int innerX = x + 1;
		int innerY = y + 1;
		int innerW = Math.max(0, w - 2);
		int innerH = Math.max(0, DIST_BAR_H - 2);
		if (innerW <= 0 || innerH <= 0 || widths == null) {
			return;
		}

		int cursor = innerX;
		for (int i = 0; i < families.size() && i < widths.length; i++) {
			int segW = Math.max(1, widths[i]);
			if (cursor >= innerX + innerW) {
				break;
			}
			segW = Math.min(segW, innerX + innerW - cursor);
			BestiarySnapshot.FamilyProgress fam = families.get(i);
			PvDraw.fill(g, cursor, innerY, segW, innerH, completionColor(fam));
			boolean linkedHover = linkedId != null && !linkedId.isBlank()
				&& fam.family() != null
				&& linkedId.equals(fam.family().id());
			if (linkedHover) {
				g.outline(cursor, innerY, segW, innerH, PvDraw.COLOR_ACCENT);
			}
			cursor += segW;
			if (i < families.size() - 1 && cursor < innerX + innerW) {
				PvDraw.fill(g, cursor, innerY, DIST_SEP, innerH, DIST_BORDER);
				cursor += DIST_SEP;
			}
		}
	}

	private static int[] barSegmentWidths(int n, int innerW) {
		if (n <= 0 || innerW <= 0) {
			return new int[0];
		}
		int sepTotal = Math.max(0, (n - 1) * DIST_SEP);
		int fillW = Math.max(n, innerW - sepTotal);
		int[] weights = new int[n];
		for (int i = 0; i < n; i++) {
			weights[i] = 1;
		}
		return distributeWidths(weights, n, fillW);
	}

	private static int[] distributeWidths(int[] weights, int weightSum, int totalPx) {
		int n = weights.length;
		int[] out = new int[n];
		if (n == 0 || totalPx <= 0 || weightSum <= 0) {
			return out;
		}
		int assigned = 0;
		double[] frac = new double[n];
		for (int i = 0; i < n; i++) {
			double exact = totalPx * (weights[i] / (double) weightSum);
			out[i] = Math.max(1, (int) Math.floor(exact));
			frac[i] = exact - Math.floor(exact);
			assigned += out[i];
		}
		// If every segment got the 1px floor, we may overshoot - scale down largest.
		while (assigned > totalPx) {
			int best = 0;
			for (int i = 1; i < n; i++) {
				if (out[i] > out[best]) {
					best = i;
				}
			}
			if (out[best] <= 1) {
				break;
			}
			out[best]--;
			assigned--;
		}
		int remain = totalPx - assigned;
		while (remain > 0) {
			int best = 0;
			for (int i = 1; i < n; i++) {
				if (frac[i] > frac[best] || (frac[i] == frac[best] && out[i] < out[best])) {
					best = i;
				}
			}
			out[best]++;
			frac[best] = -1;
			remain--;
		}
		return out;
	}

	private static int completionColor(BestiarySnapshot.FamilyProgress fam) {
		if (fam == null) {
			return DIST_LOW;
		}
		if (fam.maxed() || (fam.tiersMax() > 0 && fam.tier() >= fam.tiersMax())) {
			return DIST_MAXED;
		}
		float pct = fam.tiersMax() <= 0 ? 0F : fam.tier() / (float) fam.tiersMax();
		if (pct < 0.25F) {
			return DIST_LOW;
		}
		if (pct < 0.75F) {
			return DIST_MID;
		}
		return DIST_HIGH;
	}

	private List<Component> tooltipFor(BestiarySnapshot.FamilyProgress fam) {
		List<Component> tip = new ArrayList<>();
		tip.add(SkyBlockItemFactory.legacyLine("§a" + fam.family().name()));
		tip.add(SkyBlockItemFactory.legacyLine(
			"§7Tier: §e" + fam.tier() + "§7/§e" + fam.tiersMax() + (fam.maxed() ? " §a(MAX)" : "")));
		if (fam.maxed() || fam.nextNeed() <= 0L) {
			tip.add(SkyBlockItemFactory.legacyLine("§7Kills: §aMAX"));
		} else {
			tip.add(SkyBlockItemFactory.legacyLine(
				"§7Kills: §f" + FormatUtil.commas(fam.intoTier())
					+ "§7/§f" + FormatUtil.commas(fam.nextNeed())));
		}
		tip.add(SkyBlockItemFactory.legacyLine("§7Total kills: §f" + FormatUtil.commas(fam.kills())));
		if (fam.deaths() > 0) {
			tip.add(SkyBlockItemFactory.legacyLine("§7Deaths to family: §c" + FormatUtil.commas(fam.deaths())));
		}
		if (!fam.family().mobIds().isEmpty()) {
			tip.add(Component.empty());
			tip.add(SkyBlockItemFactory.legacyLine("§7Mobs:"));
			int shown = 0;
			for (String mob : fam.family().mobIds()) {
				if (shown >= 8) {
					tip.add(SkyBlockItemFactory.legacyLine("§8…" + (fam.family().mobIds().size() - shown) + " more"));
					break;
				}
				tip.add(SkyBlockItemFactory.legacyLine("§8• §7" + prettyMobId(mob)));
				shown++;
			}
		}
		return tip;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.gridW <= 0 || this.gridH <= 0) {
			return false;
		}
		if (mouseX < this.gridX || mouseX >= this.gridX + this.gridW
			|| mouseY < this.gridY || mouseY >= this.gridY + this.gridH) {
			return false;
		}
		if (this.maxScroll <= 0) {
			return true;
		}
		int delta = scrollY > 0 ? -SCROLL_STEP : SCROLL_STEP;
		this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll + delta));
		return true;
	}

	private boolean matchesSearch(BestiarySnapshot.FamilyProgress fam) {
		if (this.searchQuery.isBlank() || fam == null || fam.family() == null) {
			return false;
		}
		String name = fam.family().name().toLowerCase(Locale.ROOT);
		if (name.contains(this.searchQuery)) {
			return true;
		}
		String prettyName = prettyMobId(fam.family().name()).toLowerCase(Locale.ROOT);
		if (prettyName.contains(this.searchQuery)) {
			return true;
		}
		for (String mob : fam.family().mobIds()) {
			if (mob == null) {
				continue;
			}
			if (mob.toLowerCase(Locale.ROOT).contains(this.searchQuery)) {
				return true;
			}
			if (prettyMobId(mob).toLowerCase(Locale.ROOT).contains(this.searchQuery)) {
				return true;
			}
		}
		return false;
	}

	public ItemStack categoryIcon(String categoryId) {
		String key = "cat:" + (categoryId == null ? "" : categoryId);
		ItemStack cached = this.iconCache.get(key);
		if (cached != null) {
			return cached;
		}
		BestiaryData.Category cat = BestiaryData.category(categoryId);
		ItemStack icon = ItemStack.EMPTY;
		if (cat != null) {
			if (cat.textureValue() != null && !cat.textureValue().isBlank()) {
				icon = SkyBlockItemFactory.texturedHead(cat.textureValue());
			}
			if (icon.isEmpty() && cat.itemIcon() != null && !cat.itemIcon().isBlank()) {
				String itemId = cat.itemIcon().toLowerCase(Locale.ROOT).replace(' ', '_');
				icon = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(itemId))
					.map(ItemStack::new)
					.orElse(ItemStack.EMPTY);
			}
		}
		if (icon.isEmpty()) {
			icon = new ItemStack(Items.MAP);
		}
		this.iconCache.put(key, icon);
		return icon;
	}

	private ItemStack familyIcon(BestiarySnapshot.FamilyProgress fam) {
		String id = fam.family().id();
		ItemStack cached = this.iconCache.get(id);
		if (cached != null) {
			return cached;
		}
		ItemStack icon = ItemStack.EMPTY;
		String texture = fam.family().textureValue();
		if (texture != null && !texture.isBlank()) {
			icon = SkyBlockItemFactory.texturedHead(texture);
		}
		if (icon.isEmpty() && fam.family().itemIcon() != null && !fam.family().itemIcon().isBlank()) {
			String itemId = fam.family().itemIcon().toLowerCase(Locale.ROOT).replace(' ', '_');
			icon = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(itemId))
				.map(ItemStack::new)
				.orElse(ItemStack.EMPTY);
		}
		if (icon.isEmpty()) {
			icon = new ItemStack(Items.SPAWNER);
		}
		this.iconCache.put(id, icon);
		return icon;
	}

	/** Draw after island buttons / frame tabs so tips paint on top. */
	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (!this.hoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.hoverTip, mouseX, mouseY, screenW, screenH);
		}
	}

	/** {@code bogged_10} / {@code magma_cube_rider_90} → readable title case, level suffix dropped. */
	static String prettyMobId(String id) {
		if (id == null || id.isBlank()) {
			return "";
		}
		String cleaned = id.trim().replaceAll("§[0-9a-fk-or]", "");
		cleaned = cleaned.replaceAll("(?i)_\\d+$", "");
		return BestiaryData.prettyId(cleaned);
	}
}
