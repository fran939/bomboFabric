package dev.vy.betterpv.client.gui.inventories;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.SkyBlockStats;
import dev.vy.betterpv.client.gui.nav.InventoryPane;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.networth.ItemWorth;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Left grid renderer + page controls for Inventories. Right pane buttons stay in ProfileViewerScreen. */
public final class InventoryPage {
	private static final int SLOT = 18;
	private static final int SLOT_GAP = 2;
	private static final int PAGE_BTN = 18;
	private static final int ARMOR_GAP = 8;
	/** Same spacing as between inventory rows so the hotbar sits flush under the 3×9 grid. */
	private static final int HOTBAR_GAP = SLOT_GAP;
	private static final int GRID_PAD = 4;
	/** Small gap under the title for non-accessory-bag previews. */
	private static final int PREVIEW_TOP_PAD = 6;
	private static final int COL_GAP = 4;
	private static final int META_GAP = 6;
	private static final int LOADOUT_GAP = 14;
	private static final int LOADOUTS_PER_PAGE = 2;
	private static final int SEARCH_HIGHLIGHT = 0xFF55FF55;
	private static final int PANEL_HOVER = 0x0AFFFFFF;
	private static final int FLIP_MS = 480;
	private static final int POWERS_ROW = 12;

	private InventorySnapshot snapshot = InventorySnapshot.empty();
	private InventoryPane pane = InventoryPane.INVENTORY;
	/** When on Sacks: {@code null} = sack menu; otherwise index into {@link InventorySnapshot#sacks()}. */
	private Integer openSackIndex;
	private final Map<InventoryPane, Integer> pageIndex = new EnumMap<>(InventoryPane.class);
	private String searchQuery = "";
	private final Set<InventoryPane> searchPanes = EnumSet.noneOf(InventoryPane.class);
	private final Map<InventoryPane, Set<Integer>> searchPages = new EnumMap<>(InventoryPane.class);
	private final List<SlotHit> hits = new ArrayList<>();
	private final List<RunnableHit> pageHits = new ArrayList<>();
	/** Render/tooltip stacks keyed by slot identity; rebuilt when the snapshot changes. */
	private final Map<InventorySnapshot.Slot, ItemStack> stackCache = new IdentityHashMap<>();
	private InventorySnapshot.Slot hoveredSlot;
	private ItemStack hoveredStack = ItemStack.EMPTY;
	private int gemScroll;
	private int gemMaxScroll;
	private int gemScrollTop;
	private int gemScrollH;
	private List<Component> gemHoverTip;
	private int gemHoverX;
	private int gemHoverY;

	/** Accessory bag panel flip: bag face ↔ unlocked powers (whole content area). */
	private boolean accessoryPowersFace;
	private boolean accessoryFlipTarget;
	private long accessoryFlipStartMs;
	private int accessoryFlipX;
	private int accessoryFlipY;
	private int accessoryFlipW;
	private int accessoryFlipH;
	private int accessoryPowersScroll;
	private int accessoryPowersMaxScroll;
	private int accessoryTuningsX;
	private int accessoryTuningsY;
	private int accessoryTuningsW;
	private int accessoryTuningsH;
	private boolean accessoryTuningsHover;
	private final ItemValueOverlay valueOverlay = new ItemValueOverlay();

	public void apply(InventorySnapshot snapshot) {
		this.snapshot = snapshot == null ? InventorySnapshot.empty() : snapshot;
		this.pageIndex.clear();
		this.openSackIndex = null;
		this.gemScroll = 0;
		this.stackCache.clear();
		this.valueOverlay.close();
		resetAccessoryFlip();
		if (!this.pane.visibleOn(this.snapshot)) {
			this.pane = InventoryPane.INVENTORY;
		}
		rebuildSearchIndex();
		SkyBlockItemFactory.prefetch(this.snapshot);
	}

	public void setPane(InventoryPane pane) {
		if (pane != null) {
			if (this.pane != pane) {
				resetAccessoryFlip();
				this.valueOverlay.close();
			}
			this.pane = pane;
			this.openSackIndex = null;
		}
	}

	private void resetAccessoryFlip() {
		this.accessoryPowersFace = false;
		this.accessoryFlipTarget = false;
		this.accessoryFlipStartMs = 0L;
		this.accessoryPowersScroll = 0;
		this.accessoryPowersMaxScroll = 0;
		this.accessoryFlipW = 0;
		this.accessoryFlipH = 0;
		this.accessoryTuningsW = 0;
		this.accessoryTuningsH = 0;
		this.accessoryTuningsHover = false;
	}

	public InventoryPane pane() {
		return this.pane;
	}

	public List<InventoryPane> visiblePanes() {
		List<InventoryPane> out = new ArrayList<>();
		for (InventoryPane pane : InventoryPane.values()) {
			if (pane.visibleOn(this.snapshot)) {
				out.add(pane);
			}
		}
		return out;
	}

	public void setSearchQuery(String query) {
		String next = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (next.equals(this.searchQuery)) {
			return;
		}
		this.searchQuery = next;
		rebuildSearchIndex();
	}

	/** True when this pane contains at least one search match. */
	public boolean searchHighlightsPane(InventoryPane pane) {
		return pane != null && this.searchPanes.contains(pane);
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
		this.hits.clear();
		this.pageHits.clear();
		this.hoveredSlot = null;
		this.hoveredStack = ItemStack.EMPTY;
		this.gemHoverTip = null;
		this.accessoryTuningsHover = false;

		if (this.pane == InventoryPane.ACCESSORY_BAG) {
			renderAccessoryBagPanel(g, font, x, y, w, h, mouseX, mouseY);
			return;
		}

		PvDraw.innerPanel(g, x, y, w, h);

		if (this.pane == InventoryPane.SACKS) {
			renderSacks(g, font, x, y, w, h, mouseX, mouseY);
			return;
		}

		boolean loadouts = this.pane == InventoryPane.LOADOUTS;
		List<InventorySnapshot.Page> pages = loadouts ? List.of() : pagesFor(this.pane);
		List<InventorySnapshot.Loadout> loadoutList = this.snapshot.loadouts();
		int pageCount = loadouts
			? Math.max(1, (Math.max(1, loadoutList.size()) + LOADOUTS_PER_PAGE - 1) / LOADOUTS_PER_PAGE)
			: Math.max(1, pages.size());
		int page = clampPage(this.pane, pageCount);
		boolean multi = pageCount > 1;

		String title = titleFor(loadouts, loadoutList, pages, page);
		int headerY = y + 6;
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT);

		int metaTop = headerY + font.lineHeight + 4;
		int previewTop = metaTop + PREVIEW_TOP_PAD;
		int gridBottom = y + h - (multi ? PAGE_BTN + 10 : 6);
		int previewH = Math.max(SLOT, gridBottom - previewTop);
		int previewX = x + 8;
		int previewW = w - 16;

		if (loadouts) {
			drawLoadoutsPage(g, font, previewX, previewTop, previewW, previewH, loadoutList, page, mouseX, mouseY);
		} else if (this.pane == InventoryPane.INVENTORY) {
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage("Inventory", 9)
				: pages.get(Math.min(page, pages.size() - 1));
			drawPlayerInventory(g, font, previewX, previewTop, previewW, previewH, current.slots(), mouseX, mouseY);
		} else {
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage(this.pane.label().getString(), 9)
				: pages.get(Math.min(page, pages.size() - 1));
			boolean markers = this.pane == InventoryPane.WARDROBE || this.pane == InventoryPane.EQUIPMENT_WARDROBE;
			drawGrid(
				g,
				font,
				previewX,
				previewTop,
				previewW,
				previewH,
				current.columns(),
				current.slots(),
				markers,
				markers ? current.equippedColumn() : -1,
				mouseX,
				mouseY
			);
		}

		if (multi) {
			Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
			boolean highlightPrev = matches.stream().anyMatch(p -> p < page);
			boolean highlightNext = matches.stream().anyMatch(p -> p > page);
			drawPager(g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount, mouseX, mouseY, highlightPrev, highlightNext, null);
		}
	}

	/** Sack menu (NEU sack skulls) or a single opened sack’s contents. */
	private void renderSacks(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		List<InventorySnapshot.Page> sackPages = this.snapshot.sacks();
		boolean inSack = this.openSackIndex != null
			&& this.openSackIndex >= 0
			&& this.openSackIndex < sackPages.size();

		int headerY = y + 6;
		String title = inSack ? sackPages.get(this.openSackIndex).title() : "Sacks";
		PvDraw.text(g, font, title, x + 8, headerY, PvDraw.COLOR_TEXT);

		int previewTop = headerY + font.lineHeight + 4 + PREVIEW_TOP_PAD;
		boolean footer = inSack || sackMenuPageCount(sackPages) > 1;
		int gridBottom = y + h - (footer ? PAGE_BTN + 10 : 6);
		int previewH = Math.max(SLOT, gridBottom - previewTop);
		int previewX = x + 8;
		int previewW = w - 16;

		if (inSack) {
			InventorySnapshot.Page current = sackPages.get(this.openSackIndex);
			if ("Gemstone".equalsIgnoreCase(current.title())) {
				drawGemstoneMatrix(g, font, previewX, previewTop, previewW, previewH, current.slots(), mouseX, mouseY);
			} else {
				drawGrid(
					g, font, previewX, previewTop, previewW, previewH,
					current.columns(), current.slots(), false, -1, mouseX, mouseY
				);
			}
			int backX = x + 8;
			int backY = y + h - PAGE_BTN - 6;
			drawPageButton(g, font, backX, backY, "<", mouseX, mouseY, true, false, () -> this.openSackIndex = null);
			PvDraw.textCentered(
				g, font, "Sacks",
				x + w / 2,
				backY + (PAGE_BTN - font.lineHeight) / 2,
				PvDraw.COLOR_MUTED
			);
			return;
		}

		int menuPages = sackMenuPageCount(sackPages);
		int page = clampPage(this.pane, menuPages);
		drawSackMenu(g, font, previewX, previewTop, previewW, previewH, sackPages, page, mouseX, mouseY);
		if (menuPages > 1) {
			int perPage = sackMenuSlotsPerPage();
			Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
			boolean highlightPrev = matches.stream().anyMatch(sackIdx -> sackIdx / perPage < page);
			boolean highlightNext = matches.stream().anyMatch(sackIdx -> sackIdx / perPage > page);
			drawPager(g, font, x, y + h - PAGE_BTN - 6, w, page, menuPages, mouseX, mouseY, highlightPrev, highlightNext, null);
		}
	}

	private int sackMenuPageCount(List<InventorySnapshot.Page> sackPages) {
		int total = Math.max(0, sackPages.size());
		int perPage = sackMenuSlotsPerPage();
		return Math.max(1, (total + perPage - 1) / perPage);
	}

	private int sackMenuSlotsPerPage() {
		// Match drawGrid capacity for a typical preview (~6 rows × 9 cols).
		return 9 * 6;
	}

	private void drawSackMenu(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Page> sackPages,
		int page,
		int mouseX,
		int mouseY
	) {
		if (sackPages.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int cols = 9;
		int perPage = sackMenuSlotsPerPage();
		int start = page * perPage;
		int end = Math.min(sackPages.size(), start + perPage);
		List<InventorySnapshot.Slot> menuSlots = new ArrayList<>(end - start);
		for (int i = start; i < end; i++) {
			menuSlots.add(sackMenuSlot(sackPages.get(i)));
		}
		drawGrid(g, font, x, y, w, h, cols, menuSlots, false, -1, mouseX, mouseY);

		// Click targets: open the corresponding sack (drawGrid only adds tooltip hits).
		int rows = Math.max(1, (menuSlots.size() + cols - 1) / cols);
		int statusReserve = 0;
		int maxRows = Math.max(1, (h - GRID_PAD - statusReserve + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int shown = Math.min(menuSlots.size(), cols * rows);
		for (int i = 0; i < shown; i++) {
			int sackIndex = start + i;
			int col = i % cols;
			int row = i / cols;
			int sx = startX + col * (SLOT + SLOT_GAP);
			int sy = startY + row * (SLOT + SLOT_GAP);
			boolean searchHit = this.searchPages.getOrDefault(this.pane, Set.of()).contains(sackIndex);
			if (searchHit) {
				g.outline(sx, sy, SLOT, SLOT, SEARCH_HIGHLIGHT);
			}
			this.pageHits.add(new RunnableHit(sx, sy, SLOT, SLOT, () -> this.openSackIndex = sackIndex));
		}
	}

	private InventorySnapshot.Slot sackMenuSlot(InventorySnapshot.Page page) {
		String title = page == null || page.title() == null || page.title().isBlank() ? "Sack" : page.title();
		String itemId = NeuRepoCache.sackItemId(title);
		if (itemId == null || itemId.isBlank()) {
			itemId = "POCKET_SACK_IN_A_SACK";
		}
		return new InventorySnapshot.Slot(itemId, 1, List.of(), title, null, null, null);
	}

	/** Drawn after the right button panel so tips are never covered by it. */
	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.valueOverlay.isOpen()) {
			return;
		}
		if (this.gemHoverTip != null && !this.gemHoverTip.isEmpty()) {
			PvTooltip.drawComponents(g, font, this.gemHoverTip, mouseX, mouseY, screenW, screenH);
			return;
		}
		if (this.accessoryTuningsHover) {
			List<Component> tip = accessoryTuningsTooltip();
			if (tip != null && !tip.isEmpty()) {
				PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH);
				return;
			}
		}
		if (this.hoveredSlot != null && !this.hoveredStack.isEmpty()) {
			List<Component> tip = SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack);
			PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH);
		}
	}

	public void renderOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		this.valueOverlay.render(g, font, screenW, screenH, mouseX, mouseY);
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.valueOverlay.isOpen()) {
			return this.valueOverlay.mouseClicked(mx, my);
		}
		for (RunnableHit hit : this.pageHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				hit.action.run();
				return true;
			}
		}
		for (SlotHit hit : this.hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				openItemValue(hit.slot(), hit.stack());
				return true;
			}
		}
		if (this.pane == InventoryPane.ACCESSORY_BAG
			&& canFlipAccessoryPowers()
			&& this.accessoryFlipW > 0
			&& mx >= this.accessoryFlipX && mx < this.accessoryFlipX + this.accessoryFlipW
			&& my >= this.accessoryFlipY && my < this.accessoryFlipY + this.accessoryFlipH
			&& !overAccessoryTunings(mx, my)) {
			if (this.accessoryFlipStartMs != 0L) {
				return true;
			}
			this.accessoryFlipTarget = !this.accessoryPowersFace;
			this.accessoryFlipStartMs = System.currentTimeMillis();
			return true;
		}
		return false;
	}

	private void openItemValue(InventorySnapshot.Slot slot, ItemStack stack) {
		if (slot == null || slot.isEmpty()) {
			return;
		}
		InventoryDecoder.Stack worth = new InventoryDecoder.Stack(
			slot.id(),
			slot.count(),
			slot.extraAttributes() == null ? new net.minecraft.nbt.CompoundTag() : slot.extraAttributes(),
			slot.lore() == null ? List.of() : slot.lore(),
			slot.soulbound(),
			slot.displayName(),
			slot.dyeColor(),
			slot.skullValue(),
			slot.skullSignature()
		);
		ItemWorth.Breakdown breakdown = ItemWorth.breakdown(worth);
		String title = slot.displayName() != null && !slot.displayName().isBlank()
			? slot.displayName().replaceAll("§.", "").trim()
			: SkyBlockItemFactory.plainDisplayName(slot.id());
		this.valueOverlay.open(title, stack, breakdown);
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.valueOverlay.isOpen()) {
			return this.valueOverlay.mouseScrolled(scrollY);
		}
		if (this.pane == InventoryPane.ACCESSORY_BAG && showingAccessoryPowersFace()) {
			if (this.accessoryPowersMaxScroll <= 0) {
				return false;
			}
			if (mouseX < this.accessoryFlipX || mouseX >= this.accessoryFlipX + this.accessoryFlipW
				|| mouseY < this.accessoryFlipY || mouseY >= this.accessoryFlipY + this.accessoryFlipH) {
				return false;
			}
			int before = this.accessoryPowersScroll;
			this.accessoryPowersScroll = Math.max(0, Math.min(this.accessoryPowersMaxScroll,
				this.accessoryPowersScroll + (scrollY > 0 ? -POWERS_ROW : POWERS_ROW)));
			return this.accessoryPowersScroll != before;
		}
		if (this.pane == InventoryPane.SACKS && this.openSackIndex != null) {
			if (this.gemMaxScroll > 0 && isOpenGemstoneSack()) {
				int before = this.gemScroll;
				if (scrollY > 0) {
					this.gemScroll = Math.max(0, this.gemScroll - (SLOT + SLOT_GAP));
				} else if (scrollY < 0) {
					this.gemScroll = Math.min(this.gemMaxScroll, this.gemScroll + (SLOT + SLOT_GAP));
				}
				return this.gemScroll != before;
			}
			return false;
		}
		int size;
		if (this.pane == InventoryPane.LOADOUTS) {
			size = Math.max(1, (Math.max(1, this.snapshot.loadouts().size()) + LOADOUTS_PER_PAGE - 1) / LOADOUTS_PER_PAGE);
		} else if (this.pane == InventoryPane.SACKS) {
			size = sackMenuPageCount(this.snapshot.sacks());
		} else {
			size = Math.max(1, pagesFor(this.pane).size());
		}
		if (size <= 1) {
			return false;
		}
		int page = clampPage(this.pane, size);
		if (scrollY > 0 && page > 0) {
			this.pageIndex.put(this.pane, page - 1);
			return true;
		}
		if (scrollY < 0 && page < size - 1) {
			this.pageIndex.put(this.pane, page + 1);
			return true;
		}
		return false;
	}

	private String titleFor(
		boolean loadouts,
		List<InventorySnapshot.Loadout> loadoutList,
		List<InventorySnapshot.Page> pages,
		int page
	) {
		if (loadouts) {
			return "Loadouts";
		}
		if (pages.isEmpty()) {
			return this.pane.label().getString();
		}
		return pages.get(Math.min(page, pages.size() - 1)).title();
	}

	private void drawAccessoryMeta(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		InventorySnapshot.AccessoryInfo info,
		int mouseX,
		int mouseY
	) {
		this.accessoryTuningsW = 0;
		this.accessoryTuningsH = 0;
		this.accessoryTuningsHover = false;
		if (info == null) {
			return;
		}

		MutableComponent line1 = Component.empty();
		line1.append(PvDraw.styled("MP ", PvDraw.COLOR_MUTED, false));
		line1.append(PvDraw.styled(FormatUtil.commas(info.magicalPower()), 0xFF55FFFF, true));
		line1.append(PvDraw.styled("  ·  Power ", PvDraw.COLOR_MUTED, false));
		line1.append(SkyBlockStats.powerName(info.selectedPower()));
		if (info.bagUpgrades() > 0) {
			line1.append(PvDraw.styled(" · Upgrades ", PvDraw.COLOR_MUTED, false));
			line1.append(PvDraw.styled(String.valueOf(info.bagUpgrades()), PvDraw.COLOR_GOLD, true));
		}
		PvDraw.text(g, font, trimComponent(font, line1, w), x, y);

		int tuningsY = y + font.lineHeight + 2;
		int color = PvDraw.COLOR_MUTED;
		Component tunings = Component.literal("Tunings").setStyle(
			Style.EMPTY
				.withColor(net.minecraft.network.chat.TextColor.fromRgb(color & 0xFFFFFF))
				.withUnderlined(true)
		);
		int tuningsW = font.width(tunings);
		boolean hover = mouseX >= x && mouseX < x + tuningsW
			&& mouseY >= tuningsY && mouseY < tuningsY + font.lineHeight;
		if (hover) {
			tunings = Component.literal("Tunings").setStyle(
				Style.EMPTY
					.withColor(net.minecraft.network.chat.TextColor.fromRgb(PvDraw.COLOR_ACCENT & 0xFFFFFF))
					.withUnderlined(true)
			);
			tuningsW = font.width(tunings);
		}
		this.accessoryTuningsX = x;
		this.accessoryTuningsY = tuningsY;
		this.accessoryTuningsW = tuningsW;
		this.accessoryTuningsH = font.lineHeight;
		this.accessoryTuningsHover = hover;
		PvDraw.text(g, font, tunings, x, tuningsY);
	}

	private List<Component> accessoryTuningsTooltip() {
		InventorySnapshot.AccessoryInfo info = this.snapshot.accessoryInfo();
		List<Component> lines = new ArrayList<>();
		lines.add(PvDraw.styled("Tunings", PvDraw.COLOR_TEXT, true));
		if (info == null || info.tunings().isEmpty()) {
			lines.add(PvDraw.styled("No saved tunings", PvDraw.COLOR_MUTED, false));
			return lines;
		}
		for (InventorySnapshot.TuningTemplate template : info.tunings()) {
			MutableComponent line = Component.empty();
			line.append(PvDraw.styled("#" + template.slot() + " ", PvDraw.COLOR_MUTED, false));
			line.append(SkyBlockStats.tuningStats(template.stats()));
			lines.add(line);
		}
		return lines;
	}

	private boolean overAccessoryTunings(double mx, double my) {
		return this.accessoryTuningsW > 0
			&& mx >= this.accessoryTuningsX && mx < this.accessoryTuningsX + this.accessoryTuningsW
			&& my >= this.accessoryTuningsY && my < this.accessoryTuningsY + this.accessoryTuningsH;
	}

	/** Whole left inventory panel flips: border, content, and pager. */
	private void renderAccessoryBagPanel(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		List<InventorySnapshot.Page> pages = pagesFor(InventoryPane.ACCESSORY_BAG);
		int pageCount = Math.max(1, pages.size());
		int page = clampPage(this.pane, pageCount);
		boolean multi = pageCount > 1 && !showingAccessoryPowersFace();

		this.accessoryFlipX = x;
		this.accessoryFlipY = y;
		this.accessoryFlipW = w;
		this.accessoryFlipH = h;

		boolean canFlip = canFlipAccessoryPowers();
		boolean overTunings = overAccessoryTunings(mouseX, mouseY);
		boolean hovered = canFlip
			&& !overTunings
			&& mouseX >= x && mouseX < x + w
			&& mouseY >= y && mouseY < y + h;
		float flipProgress = 0F;
		boolean animating = this.accessoryFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.accessoryFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.accessoryPowersFace = this.accessoryFlipTarget;
				this.accessoryFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
				if (!this.accessoryPowersFace) {
					this.accessoryPowersScroll = 0;
				}
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showPowers = animating
			? (Math.cos(angle) < 0.0 ? this.accessoryFlipTarget : this.accessoryPowersFace)
			: this.accessoryPowersFace;
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
			PvDraw.fill(g, x, y, w, h, PANEL_HOVER);
		}

		int contentX = x + 8;
		int contentY = y + 6;
		int contentW = w - 16;
		int contentBottom = y + h - (multi ? PAGE_BTN + 10 : 6);
		int contentH = Math.max(SLOT, contentBottom - contentY);

		if (showPowers) {
			this.accessoryTuningsW = 0;
			this.accessoryTuningsH = 0;
			this.accessoryTuningsHover = false;
			drawAccessoryPowersFace(g, font, contentX, contentY, contentW, contentH);
		} else {
			String title = titleFor(false, List.of(), pages, page);
			PvDraw.text(g, font, title, contentX, contentY, PvDraw.COLOR_TEXT);
			int metaTop = contentY + font.lineHeight + 4;
			drawAccessoryMeta(g, font, contentX, metaTop, contentW, this.snapshot.accessoryInfo(), mouseX, mouseY);
			int previewTop = metaTop + font.lineHeight + 2 + font.lineHeight + 6;
			int previewH = Math.max(SLOT, contentY + contentH - previewTop);
			InventorySnapshot.Page current = pages.isEmpty()
				? InventorySnapshot.emptyPage("Accessory Bag", 9)
				: pages.get(Math.min(page, pages.size() - 1));
			drawGrid(
				g, font, contentX, previewTop, contentW, previewH,
				current.columns(), current.slots(), false, -1, mouseX, mouseY
			);
			if (multi) {
				Set<Integer> matches = this.searchPages.getOrDefault(this.pane, Set.of());
				boolean highlightPrev = matches.stream().anyMatch(p -> p < page);
				boolean highlightNext = matches.stream().anyMatch(p -> p > page);
				drawPager(
					g, font, x, y + h - PAGE_BTN - 6, w, page, pageCount,
					mouseX, mouseY, highlightPrev, highlightNext, null
				);
			}
		}

		g.pose().popMatrix();
	}

	private void drawAccessoryPowersFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		InventorySnapshot.AccessoryInfo info = this.snapshot.accessoryInfo();
		List<String> powers = info == null ? List.of() : info.unlockedPowers();
		String selected = info == null ? "" : info.selectedPower();

		PvDraw.text(g, font, "Unlocked powers", x, y, PvDraw.COLOR_MUTED);
		String count = FormatUtil.commas(powers.size());
		PvDraw.textRight(g, font, count, x + w, y, PvDraw.COLOR_ACCENT);

		int listTop = y + font.lineHeight + 4;
		int listBottom = y + h;
		int listH = Math.max(0, listBottom - listTop);
		int colGap = 10;
		int colW = Math.max(40, (w - colGap) / 2);
		int rows = (powers.size() + 1) / 2;
		int contentH = rows * POWERS_ROW;
		this.accessoryPowersMaxScroll = Math.max(0, contentH - listH);
		this.accessoryPowersScroll = Math.min(this.accessoryPowersScroll, this.accessoryPowersMaxScroll);

		g.enableScissor(x, listTop, x + w, listTop + listH);
		for (int i = 0; i < powers.size(); i++) {
			int col = i % 2;
			int row = i / 2;
			int px = x + col * (colW + colGap);
			int py = listTop + row * POWERS_ROW - this.accessoryPowersScroll;
			if (py + font.lineHeight < listTop || py > listTop + listH) {
				continue;
			}
			String power = powers.get(i);
			boolean active = selected != null && !selected.isBlank()
				&& selected.equalsIgnoreCase(power == null ? "" : power);
			Component name = SkyBlockStats.powerName(power);
			PvDraw.text(g, font, trimComponent(font, name, colW - (active ? font.width(" ●") + 2 : 0)), px, py);
			if (active) {
				PvDraw.textRight(g, font, "●", px + colW, py, 0xFF55FF55);
			}
		}
		g.disableScissor();

		if (powers.isEmpty()) {
			PvDraw.textCentered(g, font, "None unlocked", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
		}
	}

	private boolean canFlipAccessoryPowers() {
		InventorySnapshot.AccessoryInfo info = this.snapshot.accessoryInfo();
		return info != null && !info.unlockedPowers().isEmpty();
	}

	private boolean showingAccessoryPowersFace() {
		if (this.accessoryFlipStartMs != 0L) {
			float progress = Math.min(1F, (System.currentTimeMillis() - this.accessoryFlipStartMs) / (float) FLIP_MS);
			float angle = easeInOutCubic(progress) * (float) Math.PI;
			return Math.cos(angle) < 0.0 ? this.accessoryFlipTarget : this.accessoryPowersFace;
		}
		return this.accessoryPowersFace;
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F ? 4F * t * t * t : 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void drawLoadoutsPage(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Loadout> all,
		int page,
		int mouseX,
		int mouseY
	) {
		if (all.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int start = page * LOADOUTS_PER_PAGE;
		int end = Math.min(all.size(), start + LOADOUTS_PER_PAGE);
		int count = Math.max(1, end - start);
		int cardW = (w - LOADOUT_GAP * (count - 1)) / count;
		for (int i = 0; i < count; i++) {
			InventorySnapshot.Loadout loadout = all.get(start + i);
			int cx = x + i * (cardW + LOADOUT_GAP);
			drawLoadoutCard(g, font, cx, y, cardW, h, loadout, mouseX, mouseY);
		}
	}

	/** Equipment column left of armor (both top→bottom), metadata under/ beside. */
	private void drawLoadoutCard(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		InventorySnapshot.Loadout loadout,
		int mouseX,
		int mouseY
	) {
		int gearW = SLOT + COL_GAP + SLOT;
		int startY = y + GRID_PAD;
		PvDraw.text(g, font, SkyBlockStats.loadoutName(loadout.name()), x + GRID_PAD, startY);
		int gearTop = startY + font.lineHeight + 4;
		int eqX = x + GRID_PAD;
		int armorX = eqX + SLOT + COL_GAP;
		for (int i = 0; i < 4; i++) {
			int sy = gearTop + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, eqX, sy, i < loadout.equipment().size() ? loadout.equipment().get(i) : null, mouseX, mouseY);
			drawSlot(g, font, armorX, sy, i < loadout.armor().size() ? loadout.armor().get(i) : null, mouseX, mouseY);
		}

		int metaX = eqX + gearW + META_GAP;
		int metaW = Math.max(40, w - (metaX - x) - GRID_PAD);
		int my = gearTop;
		PvDraw.text(g, font, "Power", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 1;
		PvDraw.text(g, font, trimComponent(font, SkyBlockStats.powerName(loadout.powerStone()), metaW), metaX, my);
		my += font.lineHeight + 4;

		PvDraw.text(g, font, "Tuning", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 1;
		Component tune = SkyBlockStats.tuningStats(loadout.tuning());
		if (font.width(tune) <= metaW) {
			PvDraw.text(g, font, tune, metaX, my);
		} else {
			g.enableScissor(metaX, my, metaX + metaW, my + font.lineHeight + 1);
			PvDraw.text(g, font, tune, metaX, my);
			g.disableScissor();
		}
		my += font.lineHeight + 4;

		PvDraw.text(g, font, "Pet", metaX, my, PvDraw.COLOR_MUTED);
		my += font.lineHeight + 2;
		drawSlot(g, font, metaX, my, loadout.pet(), mouseX, mouseY);
		if (loadout.pet() == null || loadout.pet().isEmpty()) {
			g.item(new ItemStack(Items.BONE), metaX + 1, my + 1);
		}
		String petText = loadout.petLabel().isBlank() ? "-" : loadout.petLabel();
		int petColor = PvDraw.COLOR_TEXT;
		if (loadout.pet() != null && !loadout.pet().isEmpty()) {
			String tier = SkyBlockItemFactory.resolveTier(loadout.pet().id());
			if (!tier.isBlank()) {
				petColor = SkyBlockItemFactory.tierArgb(tier);
			}
		}
		PvDraw.text(
			g,
			font,
			trimToWidth(font, petText, Math.max(20, metaW - SLOT - 4)),
			metaX + SLOT + 4,
			my + (SLOT - font.lineHeight) / 2,
			petColor
		);
	}

	private void drawPlayerInventory(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Slot> slots,
		int mouseX,
		int mouseY
	) {
		InventorySnapshot.Slot[] equipment = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] armor = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] hotbar = new InventorySnapshot.Slot[9];
		InventorySnapshot.Slot[] main = new InventorySnapshot.Slot[27];
		// Layout: [equipment×4][armor×4][inv×36], with legacy fallback without equipment.
		boolean hasEquipment = slots.size() >= 44;
		int armorOff = hasEquipment ? 4 : 0;
		int invOff = armorOff + 4;
		for (int i = 0; i < 4; i++) {
			equipment[i] = hasEquipment && i < slots.size() ? slots.get(i) : null;
			armor[i] = armorOff + i < slots.size() ? slots.get(armorOff + i) : null;
		}
		for (int i = 0; i < 36; i++) {
			InventorySnapshot.Slot slot = invOff + i < slots.size() ? slots.get(invOff + i) : null;
			if (i < 9) {
				hotbar[i] = slot;
			} else {
				main[i - 9] = slot;
			}
		}

		int armorH = 4 * SLOT + 3 * SLOT_GAP;
		int mainH = 3 * SLOT + 2 * SLOT_GAP;
		// Hotbar sits under the main 3×9 grid (not under the taller armor column).
		int totalH = Math.max(armorH, mainH + HOTBAR_GAP + SLOT);
		int mainW = 9 * SLOT + 8 * SLOT_GAP;
		int gearW = SLOT + COL_GAP + SLOT;
		int totalW = gearW + ARMOR_GAP + mainW;
		int startX = x + Math.max(0, (w - totalW) / 2);
		int startY = y + Math.max(0, (h - totalH) / 2);

		int eqX = startX;
		int armorX = eqX + SLOT + COL_GAP;
		int bodyX = startX + gearW + ARMOR_GAP;
		int bodyY = startY;
		int hotbarY = bodyY + mainH + HOTBAR_GAP;

		for (int i = 0; i < 4; i++) {
			int sy = startY + i * (SLOT + SLOT_GAP);
			drawSlot(g, font, eqX, sy, equipment[i], mouseX, mouseY);
			drawSlot(g, font, armorX, sy, armor[i], mouseX, mouseY);
		}
		for (int i = 0; i < 27; i++) {
			int col = i % 9;
			int row = i / 9;
			drawSlot(g, font, bodyX + col * (SLOT + SLOT_GAP), bodyY + row * (SLOT + SLOT_GAP), main[i], mouseX, mouseY);
		}
		for (int i = 0; i < 9; i++) {
			drawSlot(g, font, bodyX + i * (SLOT + SLOT_GAP), hotbarY, hotbar[i], mouseX, mouseY);
		}

		boolean empty = true;
		for (InventorySnapshot.Slot s : slots) {
			if (s != null && !s.isEmpty()) {
				empty = false;
				break;
			}
		}
		if (empty) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + Math.min(h / 2, totalH / 2) - font.lineHeight / 2, PvDraw.COLOR_MUTED);
		}
	}

	private void drawPager(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int page,
		int total,
		int mouseX,
		int mouseY,
		boolean highlightPrev,
		boolean highlightNext,
		String centerLabel
	) {
		int prevX = x + 8;
		int nextX = x + w - 8 - PAGE_BTN;
		drawPageButton(g, font, prevX, y, "<", mouseX, mouseY, page > 0, highlightPrev, () -> stepPage(total, -1));
		drawPageButton(g, font, nextX, y, ">", mouseX, mouseY, page < total - 1, highlightNext, () -> stepPage(total, 1));
		String label = centerLabel != null && !centerLabel.isBlank()
			? centerLabel
			: (page + 1) + " / " + total;
		int labelMax = Math.max(40, nextX - prevX - PAGE_BTN - 16);
		if (font.width(label) > labelMax) {
			label = trimToWidth(font, label, labelMax);
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED);
	}

	private void drawPageButton(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		String label,
		int mouseX,
		int mouseY,
		boolean enabled,
		boolean searchHighlight,
		Runnable action
	) {
		boolean hovered = enabled && mouseX >= x && mouseX < x + PAGE_BTN && mouseY >= y && mouseY < y + PAGE_BTN;
		int bg = !enabled ? 0xFF121218 : searchHighlight ? 0xFF3A2A10 : hovered ? 0xFF2A3A55 : 0xFF16161E;
		int border = searchHighlight ? SEARCH_HIGHLIGHT : hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.fill(g, x, y, PAGE_BTN, PAGE_BTN, bg);
		g.outline(x, y, PAGE_BTN, PAGE_BTN, border);
		PvDraw.textCentered(g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2, enabled ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (enabled) {
			this.pageHits.add(new RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action));
		}
	}

	private boolean isOpenGemstoneSack() {
		if (this.openSackIndex == null) {
			return false;
		}
		List<InventorySnapshot.Page> sacks = this.snapshot.sacks();
		if (this.openSackIndex < 0 || this.openSackIndex >= sacks.size()) {
			return false;
		}
		return "Gemstone".equalsIgnoreCase(sacks.get(this.openSackIndex).title());
	}

	/**
	 * One slot per gem family; hover tip lists Rough→Perfect counts (no scroll matrix).
	 */
	private void drawGemstoneMatrix(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		List<InventorySnapshot.Slot> slots,
		int mouseX,
		int mouseY
	) {
		this.gemMaxScroll = 0;
		this.gemScroll = 0;
		String[] tiers = {"ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT"};
		String[] tierLabels = {"Rough", "Flawed", "Fine", "Flawless", "Perfect"};
		java.util.LinkedHashMap<String, int[]> counts = new java.util.LinkedHashMap<>();
		java.util.LinkedHashMap<String, InventorySnapshot.Slot> bestIcon = new java.util.LinkedHashMap<>();
		if (slots != null) {
			for (InventorySnapshot.Slot slot : slots) {
				if (slot == null || slot.id() == null) {
					continue;
				}
				String upper = slot.id().toUpperCase(Locale.ROOT);
				String gem = null;
				int tierIdx = -1;
				for (int t = 0; t < tiers.length; t++) {
					String prefix = tiers[t] + "_";
					if (upper.startsWith(prefix) && upper.endsWith("_GEM")) {
						gem = upper.substring(prefix.length(), upper.length() - "_GEM".length());
						tierIdx = t;
						break;
					}
				}
				if (gem == null || tierIdx < 0) {
					continue;
				}
				int[] row = counts.computeIfAbsent(gem, ignored -> new int[tiers.length]);
				row[tierIdx] = Math.max(0, slot.count());
				InventorySnapshot.Slot prev = bestIcon.get(gem);
				if (prev == null || tierIdx > bestTierIndex(prev.id(), tiers)) {
					bestIcon.put(gem, slot);
				}
			}
		}
		if (counts.isEmpty()) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = Math.min(6, Math.max(3, w / (SLOT + SLOT_GAP)));
		int rows = (counts.size() + cols - 1) / cols;
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int i = 0;
		for (var entry : counts.entrySet()) {
			int col = i % cols;
			int row = i / cols;
			int sx = startX + col * (SLOT + SLOT_GAP);
			int sy = startY + row * (SLOT + SLOT_GAP);
			InventorySnapshot.Slot icon = bestIcon.get(entry.getKey());
			if (icon == null) {
				icon = new InventorySnapshot.Slot(
					"FINE_" + entry.getKey() + "_GEM", 1, List.of(), null, null, null, null
				);
			}
			drawSlot(g, font, sx, sy, icon, mouseX, mouseY);
			if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
				List<Component> tip = new ArrayList<>();
				tip.add(Component.literal(InventoryDecoder.prettyWords(entry.getKey()))
					.withStyle(Style.EMPTY.withColor(gemColour(entry.getKey()))));
				int[] c = entry.getValue();
				for (int t = 0; t < tiers.length; t++) {
					tip.add(Component.literal(tierLabels[t] + ": ")
						.withStyle(Style.EMPTY.withColor(gemTierColour(t)))
						.append(Component.literal(FormatUtil.commas(c[t]))
							.withStyle(Style.EMPTY.withColor(0xFFE8E8F0))));
				}
				// Prefer showing this tip over the generic item tip.
				this.hoveredSlot = null;
				this.hoveredStack = ItemStack.EMPTY;
				this.gemHoverTip = tip;
				this.gemHoverX = mouseX;
				this.gemHoverY = mouseY;
			}
			i++;
		}
	}

	private static int bestTierIndex(String id, String[] tiers) {
		if (id == null) {
			return -1;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		for (int t = tiers.length - 1; t >= 0; t--) {
			if (upper.startsWith(tiers[t] + "_")) {
				return t;
			}
		}
		return -1;
	}

	/** SkyBlock gem name colours (Rough→Perfect tip header). */
	private static int gemColour(String gem) {
		if (gem == null) {
			return 0xFFE8E8F0;
		}
		return switch (gem.toUpperCase(Locale.ROOT)) {
			case "RUBY" -> 0xFFFF5555;
			case "AMBER" -> 0xFFFFAA00;
			case "TOPAZ" -> 0xFFFFFF55;
			case "JADE" -> 0xFF55FF55;
			case "SAPPHIRE" -> 0xFF5555FF;
			case "AMETHYST" -> 0xFFAA00AA;
			case "JASPER" -> 0xFFFF55FF;
			case "OPAL" -> 0xFFFCFCFC;
			case "AQUAMARINE" -> 0xFF55FFFF;
			case "CITRINE" -> 0xFFE5A84B;
			case "ONYX" -> 0xFF555555;
			case "PERIDOT" -> 0xFF00AA00;
			default -> 0xFFE8E8F0;
		};
	}

	/** Rough / Flawed / Fine / Flawless / Perfect label colours. */
	private static int gemTierColour(int tierIdx) {
		return switch (tierIdx) {
			case 0 -> 0xFFAAAAAA; // Rough
			case 1 -> 0xFF55FF55; // Flawed
			case 2 -> 0xFF5555FF; // Fine
			case 3 -> 0xFFAA00AA; // Flawless
			case 4 -> 0xFFFFAA00; // Perfect
			default -> 0xFFE8E8F0;
		};
	}

	private void drawGrid(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int columns,
		List<InventorySnapshot.Slot> slots,
		boolean statusRow,
		int equippedColumn,
		int mouseX,
		int mouseY
	) {
		int cols = Math.max(1, columns);
		int rows = Math.max(1, (slots.size() + cols - 1) / cols);
		int statusReserve = statusRow ? SLOT + SLOT_GAP : 0;
		int maxRows = Math.max(1, (h - GRID_PAD - statusReserve + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP + statusReserve;
		int startX = x + Math.max(GRID_PAD, (w - gridW) / 2);
		int startY = y + Math.max(GRID_PAD, (h - gridH) / 2);
		int maxSlots = cols * rows;

		int shown = Math.min(slots.size(), maxSlots);
		for (int i = 0; i < shown; i++) {
			InventorySnapshot.Slot slotData = slots.get(i);
			int col = i % cols;
			int row = i / cols;
			drawSlot(g, font, startX + col * (SLOT + SLOT_GAP), startY + row * (SLOT + SLOT_GAP), slotData, mouseX, mouseY);
		}

		if (statusRow) {
			int dyeY = startY + rows * (SLOT + SLOT_GAP);
			for (int col = 0; col < cols; col++) {
				int dyeX = startX + col * (SLOT + SLOT_GAP);
				drawSlot(g, font, dyeX, dyeY, null, mouseX, mouseY);
				ItemStack dye = new ItemStack(
					col == equippedColumn ? Items.DYE.lime() : Items.DYE.lightGray()
				);
				g.item(dye, dyeX + 1, dyeY + 1);
			}
		}

		if (slots.isEmpty() || slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED);
		}
	}

	private void drawSlot(
		GuiGraphicsExtractor g,
		Font font,
		int sx,
		int sy,
		InventorySnapshot.Slot slot,
		int mouseX,
		int mouseY
	) {
		boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
		boolean match = slotMatches(slot);
		PvDraw.fill(g, sx, sy, SLOT, SLOT, match ? 0xFF2A2410 : hovered ? 0xFF2A2A38 : 0xFF101018);
		g.outline(sx, sy, SLOT, SLOT, match ? SEARCH_HIGHLIGHT : hovered ? PvDraw.COLOR_ACCENT : 0xFF2A2A35);

		if (slot != null && !slot.isEmpty()) {
			ItemStack stack = cachedStack(slot);
			SkyBlockIconRenderer.draw(g, stack, slot.id(), sx + 1, sy + 1, 16);
			boolean hideCount = this.pane == InventoryPane.SACKS
				&& this.openSackIndex != null
				&& this.openSackIndex >= 0;
			if (!hideCount && slot.count() > 1) {
				String count = slot.count() <= 64
					? String.valueOf(slot.count())
					: FormatUtil.shortXp(slot.count());
				float scale = font.width(count) > SLOT - 4 ? 0.5f : 1f;
				int textH = Math.max(1, Math.round(font.lineHeight * scale));
				int textW = Math.max(1, Math.round(font.width(count) * scale));
				int tx = sx + SLOT - 1 - textW;
				int ty = sy + SLOT - 1 - textH;
				if (scale >= 0.99f) {
					PvDraw.text(g, font, count, tx, ty, PvDraw.COLOR_TEXT);
				} else {
					PvDraw.textScaled(g, font, count, tx, ty, PvDraw.COLOR_TEXT, scale);
				}
			}
			this.hits.add(new SlotHit(sx, sy, SLOT, SLOT, slot, stack));
			if (hovered) {
				this.hoveredSlot = slot;
				this.hoveredStack = stack;
			}
		}
	}

	private ItemStack cachedStack(InventorySnapshot.Slot slot) {
		ItemStack cached = this.stackCache.get(slot);
		if (cached != null) {
			return cached;
		}
		ItemStack built = SkyBlockItemFactory.toStack(slot);
		this.stackCache.put(slot, built);
		return built;
	}

	private void rebuildSearchIndex() {
		this.searchPanes.clear();
		this.searchPages.clear();
		if (this.searchQuery.isEmpty()) {
			return;
		}
		for (InventoryPane pane : InventoryPane.values()) {
			if (!pane.visibleOn(this.snapshot)) {
				continue;
			}
			if (pane == InventoryPane.LOADOUTS) {
				List<InventorySnapshot.Loadout> loadouts = this.snapshot.loadouts();
				for (int i = 0; i < loadouts.size(); i++) {
					if (loadoutMatches(loadouts.get(i))) {
						int page = i / LOADOUTS_PER_PAGE;
						this.searchPanes.add(pane);
						this.searchPages.computeIfAbsent(pane, ignored -> new HashSet<>()).add(page);
					}
				}
				continue;
			}
			List<InventorySnapshot.Page> pages = pagesFor(pane);
			for (int i = 0; i < pages.size(); i++) {
				if (pageMatches(pages.get(i))) {
					this.searchPanes.add(pane);
					this.searchPages.computeIfAbsent(pane, ignored -> new HashSet<>()).add(i);
				}
			}
		}
	}

	private boolean pageMatches(InventorySnapshot.Page page) {
		if (page == null || page.slots() == null) {
			return false;
		}
		for (InventorySnapshot.Slot slot : page.slots()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		return false;
	}

	private boolean loadoutMatches(InventorySnapshot.Loadout loadout) {
		if (loadout == null) {
			return false;
		}
		for (InventorySnapshot.Slot slot : loadout.equipment()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		for (InventorySnapshot.Slot slot : loadout.armor()) {
			if (slotMatches(slot)) {
				return true;
			}
		}
		return slotMatches(loadout.pet());
	}

	private boolean slotMatches(InventorySnapshot.Slot slot) {
		if (this.searchQuery.isEmpty() || slot == null || slot.isEmpty()) {
			return false;
		}
		String id = slot.id() == null ? "" : slot.id().toLowerCase(Locale.ROOT);
		String name = plainText(slot.displayName());
		if (id.contains(this.searchQuery) || name.contains(this.searchQuery)) {
			return true;
		}
		ItemStack preview = cachedStack(slot);
		var customName = preview.get(DataComponents.CUSTOM_NAME);
		if (customName != null && plainText(customName.getString()).contains(this.searchQuery)) {
			return true;
		}
		if (slot.lore() != null) {
			for (String line : slot.lore()) {
				if (line != null && plainText(line).contains(this.searchQuery)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String plainText(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.replaceAll("§.", "").toLowerCase(Locale.ROOT);
	}

	private static String trimToWidth(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private static Component trimComponent(Font font, Component value, int maxW) {
		if (value == null) {
			return Component.empty();
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String plain = trimToWidth(font, value.getString(), maxW);
		return PvDraw.styled(plain, PvDraw.COLOR_TEXT, false);
	}

	private List<InventorySnapshot.Page> pagesFor(InventoryPane pane) {
		return switch (pane) {
			case INVENTORY -> List.of(this.snapshot.inventory());
			case ENDER_CHEST -> this.snapshot.enderChest();
			case BACKPACKS -> this.snapshot.backpacks();
			case WARDROBE -> this.snapshot.wardrobe();
			case EQUIPMENT_WARDROBE -> this.snapshot.equipmentWardrobe();
			case LOADOUTS -> List.of();
			case SACKS -> this.snapshot.sacks();
			case FISHING_BAG -> List.of(this.snapshot.fishingBag());
			case POTION_BAG -> List.of(this.snapshot.potionBag());
			case QUIVER -> List.of(this.snapshot.quiver());
			case ACCESSORY_BAG -> this.snapshot.accessoryBag();
			case TIME_POCKET -> List.of(this.snapshot.timePocket());
			case PERSONAL_VAULT -> List.of(this.snapshot.personalVault());
			case CARNIVAL_MASKS -> List.of(this.snapshot.carnivalMasks());
			case CANDY_BAG -> List.of(this.snapshot.candyBag());
		};
	}

	/** Ctrl+click steps 5 pages; normal click steps 1. */
	private void stepPage(int total, int direction) {
		int size = Math.max(1, total);
		int page = clampPage(this.pane, size);
		int step = controlDown() ? 5 : 1;
		int next = Math.max(0, Math.min(size - 1, page + direction * step));
		this.pageIndex.put(this.pane, next);
	}

	private static boolean controlDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LCONTROL)
			|| InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RCONTROL);
	}

	private int clampPage(InventoryPane pane, int size) {
		if (size <= 0) {
			return 0;
		}
		int page = this.pageIndex.getOrDefault(pane, 0);
		if (page < 0) page = 0;
		if (page >= size) page = size - 1;
		this.pageIndex.put(pane, page);
		return page;
	}

	private record SlotHit(int x, int y, int w, int h, InventorySnapshot.Slot slot, ItemStack stack) {
	}

	private record RunnableHit(int x, int y, int w, int h, Runnable action) {
	}
}
