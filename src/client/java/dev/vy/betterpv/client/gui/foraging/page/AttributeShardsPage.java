package dev.vy.betterpv.client.gui.foraging.page;

import static dev.vy.betterpv.client.gui.foraging.ForagingUi.COLOR_MAXED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ICON;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_LOCKED_BG;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_LOCKED_BORDER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.PAD;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT_GAP;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.foraging.ForagingUi;
import dev.vy.betterpv.client.gui.foraging.ForagingUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.price.ItemPricer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Attribute Shards subtab with dual-scroll grid + cost list. */
public final class AttributeShardsPage {
	private final List<HoverZone> zones = new ArrayList<>();

	private int attrGridScroll;
	private int attrGridMaxScroll;
	private int attrGridX;
	private int attrGridY;
	private int attrGridW;
	private int attrGridH;
	private int attrListScroll;
	private int attrListMaxScroll;
	private int attrListX;
	private int attrListY;
	private int attrListW;
	private int attrListH;

	public void reset() {
		this.zones.clear();
		this.attrGridScroll = 0;
		this.attrListScroll = 0;
	}

	public void resetScroll() {
		this.attrGridScroll = 0;
		this.attrListScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		int delta = scrollY > 0 ? -14 : 14;
		boolean overGrid = ForagingUi.hit(mouseX, mouseY, this.attrGridX, this.attrGridY, this.attrGridW, this.attrGridH);
		boolean overList = ForagingUi.hit(mouseX, mouseY, this.attrListX, this.attrListY, this.attrListW, this.attrListH);
		if (overGrid && this.attrGridMaxScroll > 0) {
			int next = Math.max(0, Math.min(this.attrGridMaxScroll, this.attrGridScroll + delta));
			if (next != this.attrGridScroll) {
				this.attrGridScroll = next;
			}
			return true;
		}
		if (overList && this.attrListMaxScroll > 0) {
			int next = Math.max(0, Math.min(this.attrListMaxScroll, this.attrListScroll + delta));
			if (next != this.attrListScroll) {
				this.attrListScroll = next;
			}
			return true;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int rightW = Math.max(210, w * 42 / 100);
		int leftW = w - rightW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		List<ForagingSnapshot.AttributeShardRow> rows = snapshot.attributeShards();
		drawAttributeGrid(g, font, x, y, leftW, h, mx, my, rows);
		drawAttributeCostList(g, font, x + leftW + GAP, y, rightW, h, mx, my, rows);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawAttributeGrid(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my,
		List<ForagingSnapshot.AttributeShardRow> rows
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Attribute Shards", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;
		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No attribute data",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.attrGridMaxScroll = 0;
			this.attrGridScroll = 0;
			this.attrGridW = 0;
			this.attrGridH = 0;
			return;
		}

		int cols = Math.max(6, Math.min(12, (lw + SLOT_GAP) / (SLOT + SLOT_GAP)));
		int gridRows = (rows.size() + cols - 1) / cols;
		int contentH = gridRows * (SLOT + SLOT_GAP);
		this.attrGridX = x;
		this.attrGridY = ly;
		this.attrGridW = w;
		this.attrGridH = Math.max(0, bottom - ly);
		this.attrGridMaxScroll = Math.max(0, contentH - this.attrGridH);
		this.attrGridScroll = Math.min(this.attrGridScroll, this.attrGridMaxScroll);

		g.enableScissor(lx, this.attrGridY, lx + lw, this.attrGridY + this.attrGridH);
		for (int i = 0; i < rows.size(); i++) {
			ForagingSnapshot.AttributeShardRow row = rows.get(i);
			int col = i % cols;
			int r = i / cols;
			int bx = lx + col * (SLOT + SLOT_GAP);
			int by = ly + r * (SLOT + SLOT_GAP) - this.attrGridScroll;
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& ForagingUi.hit(mx, my, lx, this.attrGridY, lw, this.attrGridH);
			boolean locked = !row.unlocked();
			PvDraw.fill(g, bx, by, SLOT, SLOT, locked ? ITEM_SLOT_LOCKED_BG : ITEM_SLOT_BG);
			int border = locked
				? ITEM_SLOT_LOCKED_BORDER
				: (hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
			g.outline(bx, by, SLOT, SLOT, border);
			ItemStack stack = attributeIcon(row);
			SkyBlockIconRenderer.draw(g, stack, row.iconId(), bx + 1, by + 1, 16);
			ForagingUi.addClippedHover(this.zones, bx, by, SLOT, SLOT, lx, this.attrGridY, lw, this.attrGridH,
				attributeTooltip(row));
		}
		g.disableScissor();
	}

	private void drawAttributeCostList(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my,
		List<ForagingSnapshot.AttributeShardRow> rows
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Not maxed", rx, ry, PvDraw.COLOR_MUTED);

		List<ForagingSnapshot.AttributeShardRow> notMaxed = new ArrayList<>();
		for (ForagingSnapshot.AttributeShardRow row : rows) {
			if (row.level() < row.maxLevel()) {
				notMaxed.add(row);
			}
		}
		double totalCost = 0;
		for (ForagingSnapshot.AttributeShardRow row : notMaxed) {
			totalCost += shardMaxCost(row);
		}
		String totalText = totalCost > 0 ? FormatUtil.shortCoins(totalCost) : "-";
		PvDraw.textRight(g, font, totalText, rx + rw, ry, PvDraw.COLOR_GOLD);
		ry += font.lineHeight + 2;
		PvDraw.text(g, font, "Cheapest to max", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 2;
		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);

		notMaxed.sort(Comparator
			.comparingDouble(AttributeShardsPage::shardMaxCost)
			.thenComparingInt(r -> Math.max(0, r.shardsForMax() - r.shardsOwned())));

		if (notMaxed.isEmpty()) {
			PvDraw.text(g, font, "All maxed", rx, ry, COLOR_MAXED);
			this.attrListMaxScroll = 0;
			this.attrListScroll = 0;
			this.attrListW = 0;
			this.attrListH = 0;
			return;
		}

		int rowH = Math.max(ICON + 2, font.lineHeight * 2 + 4);
		this.attrListX = x;
		this.attrListY = ry;
		this.attrListW = w;
		this.attrListH = Math.max(0, bottom - ry);
		int contentH = notMaxed.size() * rowH;
		this.attrListMaxScroll = Math.max(0, contentH - this.attrListH);
		this.attrListScroll = Math.min(this.attrListScroll, this.attrListMaxScroll);

		g.enableScissor(rx, this.attrListY, rx + rw, this.attrListY + this.attrListH);
		int yy = this.attrListY - this.attrListScroll;
		for (ForagingSnapshot.AttributeShardRow row : notMaxed) {
			int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
			ItemStack stack = attributeIcon(row);
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(yy, rowH, ICON, font.lineHeight);
			SkyBlockIconRenderer.draw(g, stack, row.iconId(), rx, rowAlign.iconY(), ICON);
			String qty = "x" + FormatUtil.commas(need);
			int qtyW = font.width(qty);
			int nameColor = rarityColor(row.rarity());
			PvDraw.text(g, font, ForagingUi.trim(font, row.name(), Math.max(8, rw - ICON - 8 - qtyW)),
				rx + ICON + 4, rowAlign.textY(), nameColor);
			PvDraw.textRight(g, font, qty, rx + rw, rowAlign.textY(), PvDraw.COLOR_ACCENT);
			double cost = shardMaxCost(row);
			String costText = cost > 0 ? FormatUtil.shortCoins(cost) : "-";
			PvDraw.text(g, font, costText, rx + ICON + 4, rowAlign.textY() + font.lineHeight + 1, PvDraw.COLOR_GOLD);
			ForagingUi.addClippedHover(this.zones, rx, yy, rw, rowH, rx, this.attrListY, rw, this.attrListH,
				attributeTooltip(row));
			yy += rowH;
		}
		g.disableScissor();
	}

	private static ItemStack attributeIcon(ForagingSnapshot.AttributeShardRow row) {
		ItemStack stack = SkyBlockItemFactory.iconStack(row.iconId());
		if (stack == null || stack.isEmpty() || stack.is(Items.BARRIER)) {
			stack = new ItemStack(Items.PRISMARINE_SHARD);
		}
		return stack;
	}

	private static List<PvTooltip.Line> attributeTooltip(ForagingSnapshot.AttributeShardRow row) {
		int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
		double cost = shardMaxCost(row);
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.of(row.name(), rarityColor(row.rarity())));
		tip.add(PvTooltip.Line.of(ForagingUi.pretty(row.rarity()), rarityColor(row.rarity())));
		tip.add(PvTooltip.Line.of("Level " + row.level() + " / " + row.maxLevel(), PvDraw.COLOR_ACCENT));
		tip.add(PvTooltip.Line.of(
			row.unlocked()
				? "Shards to max: " + FormatUtil.commas(need)
				: "Locked · " + FormatUtil.commas(row.shardsForMax()) + " shards to max",
			PvDraw.COLOR_MUTED
		));
		if (row.unlocked()) {
			tip.add(PvTooltip.Line.of("Owned: " + FormatUtil.commas(row.shardsOwned())
				+ " / " + FormatUtil.commas(row.shardsForMax()), PvDraw.COLOR_GOLD));
		}
		if (cost > 0) {
			tip.add(PvTooltip.Line.of("Cost to max: " + FormatUtil.shortCoins(cost), PvDraw.COLOR_GOLD));
		}
		return tip;
	}

	private static double shardMaxCost(ForagingSnapshot.AttributeShardRow row) {
		int need = Math.max(0, row.shardsForMax() - row.shardsOwned());
		if (need <= 0) {
			return 0;
		}
		double unit = ItemPricer.price(row.priceId());
		if (unit <= 0) {
			unit = ItemPricer.price(row.iconId());
		}
		if (unit <= 0) {
			String icon = row.iconId();
			int semi = icon == null ? -1 : icon.indexOf(';');
			if (semi > 0) {
				unit = ItemPricer.price(icon.substring(0, semi));
			}
		}
		return unit > 0 ? unit * need : 0;
	}

	private static int rarityColor(String rarity) {
		if (rarity == null) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (rarity.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			default -> PvDraw.COLOR_TEXT;
		};
	}
}
