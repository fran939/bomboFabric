package dev.vy.betterpv.client.gui.foraging.page;

import static dev.vy.betterpv.client.gui.foraging.ForagingUi.BAR_AFTER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.BAR_FORAGE;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.COLOR_MAXED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ENABLED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.FLIP_MS;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.FOREST_COLOR;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ICON;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.PAD;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.PANEL_HOVER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SEP_GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT_GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.STAT_ROW;

import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FishFamilyData;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.foraging.ForagingUi;
import dev.vy.betterpv.client.gui.foraging.ForagingUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Foraging overview subtab (left stats + flip panel). */
public final class ForagingOverviewPage {
	private final List<HoverZone> zones = new ArrayList<>();

	private boolean rightEssenceFace;
	private long rightFlipStartMs;
	private boolean rightFlipTarget;
	private int rightHitX;
	private int rightHitY;
	private int rightHitW;
	private int rightHitH;

	public void reset() {
		this.zones.clear();
		this.rightEssenceFace = false;
		this.rightFlipStartMs = 0L;
		this.rightHitW = 0;
	}

	public void onLeave() {
		this.rightEssenceFace = false;
		this.rightFlipStartMs = 0L;
		this.rightHitW = 0;
	}

	public boolean mouseClicked(double mx, double my) {
		if (ForagingUi.hit(mx, my, this.rightHitX, this.rightHitY, this.rightHitW, this.rightHitH)) {
			if (this.rightFlipStartMs != 0L) {
				return true;
			}
			this.rightFlipTarget = !this.rightEssenceFace;
			this.rightFlipStartMs = System.currentTimeMillis();
			return true;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int rightW = Math.max(200, w * 52 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		drawOverviewLeft(g, font, snapshot, x, y, leftW, h, mx, my);
		drawRightFlipPanel(g, font, snapshot, x + leftW + GAP, y, rightW, h, mx, my);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawRightFlipPanel(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.rightHitX = x;
		this.rightHitY = y;
		this.rightHitW = w;
		this.rightHitH = h;

		boolean hovered = mx >= x && mx < x + w && my >= y && my < y + h;
		float flipProgress = 0F;
		boolean animating = this.rightFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.rightFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.rightEssenceFace = this.rightFlipTarget;
				this.rightFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? ForagingUi.easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.rightFlipTarget : this.rightEssenceFace)
			: this.rightEssenceFace;
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

		if (showEssence) {
			drawForestEssenceFace(g, font, snapshot, x, y, w, h);
		} else {
			drawContestsFace(g, font, snapshot, x, y, w, h);
		}

		g.pose().popMatrix();
	}

	private void drawOverviewLeft(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		ly = ForagingUi.drawBar(g, font, "Foraging", String.valueOf(snapshot.foragingLevel()),
			snapshot.foragingFill(), snapshot.foragingMaxed(), BAR_FORAGE,
			snapshot.foragingHover(), lx, ly, lw, this.zones) + BAR_AFTER + 2;

		ly = ForagingUi.statLine(g, font, "Park race PB", ForagingUi.formatRaceMs(snapshot.raceBestMs()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);
		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);

		PvDraw.text(g, font, "Fish Family", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Set<String> discovered = new HashSet<>();
		for (String id : snapshot.fishFamily()) {
			if (id != null && !id.isBlank()) {
				discovered.add(id.toUpperCase(Locale.ROOT));
			}
		}
		List<String> catalog = new ArrayList<>(FishFamilyData.all());
		Set<String> known = new HashSet<>();
		for (String id : catalog) {
			known.add(id.toUpperCase(Locale.ROOT));
		}
		for (String id : discovered) {
			if (known.add(id)) {
				catalog.add(id);
			}
		}
		int cols = Math.max(1, Math.min(catalog.size(), Math.max(1, (lw + SLOT_GAP) / (SLOT + SLOT_GAP))));
		for (int i = 0; i < catalog.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (SLOT + SLOT_GAP);
			int by = ly + row * (SLOT + SLOT_GAP);
			String id = catalog.get(i);
			boolean found = discovered.contains(id.toUpperCase(Locale.ROOT));
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT;
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
			g.outline(bx, by, SLOT, SLOT, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
			if (found) {
				ForagingUi.drawFishFamilyIcon(g, id, bx + 1, by + 1);
			} else {
				g.item(new ItemStack(Items.BARRIER), bx + 1, by + 1);
			}
			this.zones.add(HoverZone.of(bx, by, SLOT, SLOT, List.of(
				PvTooltip.Line.of(FishFamilyData.displayName(id), found ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED),
				PvTooltip.Line.of(found ? "Shown to Coral" : "Undiscovered", PvDraw.COLOR_MUTED)
			)));
		}
		int rows = (catalog.size() + cols - 1) / cols;
		ly += rows * SLOT + Math.max(0, rows - 1) * SLOT_GAP + 6;

		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
		ly = ForagingUi.statLine(g, font, "Hina Chapter", String.valueOf(snapshot.hinaTier()),
			lx, ly, lw, PvDraw.COLOR_ACCENT);

		if (snapshot.hasGalateaBeacon()) {
			ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
			ly = ForagingUi.statLine(g, font, "Galatea Beacon",
				snapshot.galateaBeacon() + " / 10",
				lx, ly, lw, PvDraw.COLOR_GOLD);
		}

		ForagingSnapshot.HoneyInfo honey = snapshot.honey();
		if (honey != null && honey.present()) {
			ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
			PvDraw.text(g, font, "Honey", lx, ly, PvDraw.COLOR_MUTED);
			ly += ForagingUi.STAT_ROW;
			if (honey.smearedTrees() > 0) {
				int rowY = ly;
				ly = ForagingUi.statLine(g, font, "Smeared trees", String.valueOf(honey.smearedTrees()),
					lx, ly, lw, PvDraw.COLOR_ACCENT);
				if (!honey.treeNames().isEmpty()) {
					List<PvTooltip.Line> tip = new ArrayList<>();
					tip.add(PvTooltip.Line.title("Smeared trees", PvDraw.COLOR_ACCENT));
					tip.add(PvTooltip.Line.divider());
					int shown = 0;
					for (String name : honey.treeNames()) {
						if (shown >= 12) {
							tip.add(PvTooltip.Line.meta("+" + (honey.treeNames().size() - shown) + " more"));
							break;
						}
						tip.add(PvTooltip.Line.plain(name));
						shown++;
					}
					this.zones.add(HoverZone.of(lx, rowY, lw, ForagingUi.STAT_ROW, tip));
				}
			}
			if (honey.refills() > 0) {
				ly = ForagingUi.statLine(g, font, "Refills", String.valueOf(honey.refills()),
					lx, ly, lw, PvDraw.COLOR_GOLD);
			}
		}
	}

	private void drawMelodySection(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int lx, int ly, int lw, int bottom
	) {
		PvDraw.text(g, font, "Melodies Harp", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 2;
		ly = ForagingUi.statLine(g, font, "Talisman", snapshot.harpTalisman() ? "Claimed" : "Unclaimed",
			lx, ly, lw, snapshot.harpTalisman() ? ENABLED : PvDraw.COLOR_MUTED) + 2;

		List<ForagingSnapshot.HarpSong> songs = snapshot.harpSongs();
		if (songs.isEmpty()) {
			PvDraw.text(g, font, "No songs", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}

		int availH = Math.max(font.lineHeight, bottom - ly);
		int cellW = ICON + 4 + font.width("100%") + 2;
		int cols = Math.max(2, Math.min(songs.size(), Math.max(1, lw / Math.max(1, cellW + 2))));
		int rowsNeeded = (songs.size() + cols - 1) / cols;
		int cellH = Math.max(ICON + 2, Math.min(ICON + 4, availH / Math.max(1, rowsNeeded)));
		int gap = Math.max(1, Math.min(3, (availH - rowsNeeded * cellH) / Math.max(1, rowsNeeded)));

		ItemStack book = new ItemStack(Items.BOOK);
		String selected = snapshot.harpSelected();
		for (int i = 0; i < songs.size(); i++) {
			ForagingSnapshot.HarpSong song = songs.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (cellW + 2);
			int by = ly + row * (cellH + gap);
			if (by + ICON > bottom) {
				break;
			}
			double pct = song.best() <= 1.0 ? song.best() * 100.0 : song.best();
			boolean isSelected = selected != null && !selected.isBlank()
				&& selected.equalsIgnoreCase(song.id());
			g.item(book, bx, by);
			String pctText = Math.round(pct) + "%";
			PvDraw.text(g, font, pctText, bx + ICON + 2, by + Math.max(0, (ICON - font.lineHeight) / 2),
				isSelected ? ENABLED : PvDraw.COLOR_ACCENT);
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(song.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Best", PvDraw.COLOR_MUTED,
				FormatUtil.oneDecimal(pct) + "%", isSelected ? ENABLED : PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.row("Completions", PvDraw.COLOR_MUTED,
				String.valueOf(song.completions()), PvDraw.COLOR_MUTED));
			tip.add(PvTooltip.Line.row("Perfects", PvDraw.COLOR_MUTED,
				String.valueOf(song.perfects()), PvDraw.COLOR_GOLD));
			if (isSelected) {
				tip.add(PvTooltip.Line.meta("Selected"));
			}
			this.zones.add(HoverZone.of(bx, by, cellW, cellH, tip));
		}
	}

	private void drawContestsFace(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Tree gifts", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.TreeGift> gifts = snapshot.treeGifts();
		if (gifts.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			for (ForagingSnapshot.TreeGift gift : gifts) {
				String right = FormatUtil.commas(gift.count())
					+ (gift.milestoneTier() > 0 ? " · T" + gift.milestoneTier() : "");
				ry = ForagingUi.statLine(g, font, gift.name(), right, rx, ry, rw, PvDraw.COLOR_TEXT) + 1;
			}
			ry += 2;
		}

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Starlyn PBs", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.StarlynBest> bests = snapshot.starlynBests();
		if (bests.isEmpty()) {
			PvDraw.text(g, font, "None", rx, ry, PvDraw.COLOR_MUTED);
			ry += STAT_ROW + 2;
		} else {
			for (ForagingSnapshot.StarlynBest best : bests) {
				ry = ForagingUi.statLine(g, font, best.name(), FormatUtil.commas(best.amount()),
					rx, ry, rw, PvDraw.COLOR_GOLD) + 1;
			}
			ry += 2;
		}

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Whispers", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.WhisperPool> pools = snapshot.whisperPools();
		if (pools.isEmpty()) {
			ForagingUi.statLine(g, font, "Total", "-", rx, ry, rw, PvDraw.COLOR_MUTED);
		} else {
			for (ForagingSnapshot.WhisperPool pool : pools) {
				int poolColor = whisperPoolColor(pool.id());
				ry = ForagingUi.statLine(g, font, pool.label(), FormatUtil.commas(pool.earned()),
					rx, ry, rw, poolColor) + 1;
			}
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

	private void drawForestEssenceFace(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int cx = x + PAD;
		int cy = y + PAD;
		int innerW = w - PAD * 2;
		int bottom = y + h - PAD;
		DungeonSnapshot.EssenceShop shop = snapshot.forestShop();

		List<ForagingSnapshot.HarpSong> songs = snapshot.harpSongs();
		int melodyHeader = font.lineHeight + 2 + STAT_ROW + 2;
		int melodyCols = Math.max(2, Math.min(Math.max(1, songs.size()), Math.max(1, innerW / 40)));
		int melodyRows = songs.isEmpty() ? 1 : (songs.size() + melodyCols - 1) / melodyCols;
		int melodyBlock = melodyHeader + melodyRows * (ICON + 3) + SEP_GAP + 4;
		int shopsBottom = Math.max(cy + 48, bottom - melodyBlock);

		// Full 16px icons - no fractional scale (that softens item textures).
		int headerH = Math.max(16, font.lineHeight + 2);
		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(cy, headerH, 16, font.lineHeight);
		g.item(essenceIcon(shop.iconId()), cx, headerAlign.iconY());
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int headerLabelX = cx + 16 + 4;
		int nameMax = Math.max(8, innerW - (headerLabelX - cx) - balW - 4);
		PvDraw.textBold(g, font, ForagingUi.trim(font, shop.name() + " Essence", nameMax),
			headerLabelX, headerAlign.textY(), FOREST_COLOR);
		PvDraw.textBold(g, font, bal, cx + innerW - balW, headerAlign.textY(), FOREST_COLOR);

		int ly = cy + headerH + 6;
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int colGap = 10;
		int colW = Math.max(80, (innerW - colGap) / 2);
		int perkRows = Math.max(1, (perks.size() + 1) / 2);
		int availPerkH = Math.max(font.lineHeight + 2, shopsBottom - ly);
		int rowH = Math.max(16, Math.max(font.lineHeight + 2, Math.min(20, availPerkH / perkRows)));
		for (int i = 0; i < perks.size(); i++) {
			DungeonSnapshot.EssencePerk perk = perks.get(i);
			int col = i % 2;
			int row = i / 2;
			int px = cx + col * (colW + colGap);
			int py = ly + row * rowH;
			if (py + font.lineHeight > shopsBottom) {
				break;
			}
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(py, rowH, 16, font.lineHeight);
			g.item(forestPerkIcon(perk.id()), px, rowAlign.iconY());
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			int labelX = px + 16 + 3;
			String left = ForagingUi.trim(font, perk.name(), Math.max(8, colW - 16 - 3 - rightW - 4));
			int valueColor = perk.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, labelX, rowAlign.textY(), PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, px + colW, rowAlign.textY(), valueColor);
		}

		int sepY = ForagingUi.sectionSeparator(g, font, x, shopsBottom, w);
		drawMelodySection(g, font, snapshot, cx, sepY, innerW, bottom);
	}

	private static ItemStack essenceIcon(String skyblockId) {
		ItemStack stack = SkyBlockItemFactory.iconStack(skyblockId == null ? "" : skyblockId);
		return stack == null || stack.isEmpty() ? new ItemStack(Items.FLOWERING_AZALEA_LEAVES) : stack;
	}

	private static ItemStack forestPerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (perkId) {
			case "trapped" -> new ItemStack(Items.IRON_TRAPDOOR);
			case "axed" -> new ItemStack(Items.IRON_AXE);
			case "extreme_pressure" -> new ItemStack(Items.OBSIDIAN);
			case "lumberjack" -> new ItemStack(Items.OAK_LOG);
			case "tasty" -> new ItemStack(Items.APPLE);
			case "forest_training" -> new ItemStack(Items.BOOK);
			default -> new ItemStack(Items.PAPER);
		};
	}
}
