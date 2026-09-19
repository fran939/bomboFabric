package dev.vy.betterpv.client.gui.foraging.page;

import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ENABLED;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.PAD;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.SLOT_GAP;
import static dev.vy.betterpv.client.gui.foraging.ForagingUi.STAT_ROW;

import dev.vy.betterpv.client.data.AttributeShardsData;
import dev.vy.betterpv.client.data.DungeonSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.ForagingSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.gui.foraging.ForagingUi;
import dev.vy.betterpv.client.gui.foraging.ForagingUi.HoverZone;
import dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class SafariPage {
	private static final String[] TICKET_ORDER = { "basic", "economy", "premium", "first_class" };
	private static final String[] BIOME_ORDER = { "forest", "cavern", "haunted", "icy", "desert" };

	private final List<HoverZone> zones = new ArrayList<>();
	private int gridScroll;
	private int gridMaxScroll;
	private int gridX;
	private int gridY;
	private int gridW;
	private int gridH;

	public void reset() {
		this.zones.clear();
		this.gridScroll = 0;
	}

	public void resetScroll() {
		this.gridScroll = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.gridMaxScroll <= 0) {
			return false;
		}
		if (!ForagingUi.hit(mouseX, mouseY, this.gridX, this.gridY, this.gridW, this.gridH)) {
			return false;
		}
		int next = Math.max(0, Math.min(this.gridMaxScroll, this.gridScroll + (scrollY > 0 ? -14 : 14)));
		if (next == this.gridScroll) {
			return false;
		}
		this.gridScroll = next;
		return true;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		int leftW = Math.max(160, w * 38 / 100);
		int rightW = w - leftW - GAP;
		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h);

		ForagingSnapshot.SafariInfo safari = snapshot.safari();
		drawSummary(g, font, snapshot, safari, x, y, leftW, h);
		drawCritterGrid(g, font, safari, x + leftW + GAP, y, rightW, h, mx, my);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void drawSummary(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		ForagingSnapshot.SafariInfo safari, int x, int y, int w, int h
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Safari", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 4;

		if (!safari.present()) {
			PvDraw.text(g, font, "No safari data", lx, ly, PvDraw.COLOR_MUTED);
			return;
		}

		ly = ForagingUi.statLine(g, font, "Critters", FormatUtil.commas(safari.discoveredCritters().size()),
			lx, ly, lw, PvDraw.COLOR_ACCENT) + 1;
		ly = ForagingUi.statLine(g, font, "Captures", FormatUtil.commas(safari.totalCaptures()),
			lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
		ly = ForagingUi.statLine(g, font, "Tickets", FormatUtil.commas(safari.totalTickets()),
			lx, ly, lw, PvDraw.COLOR_GOLD) + 2;

		ly = drawSafariEssenceShop(g, font, snapshot.safariShop(), lx, ly, lw, bottom);
		if (ly + font.lineHeight + STAT_ROW > bottom) {
			return;
		}

		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Tickets", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Map<String, Long> tickets = orderedLongMap(safari.tickets(), TICKET_ORDER);
		if (tickets.isEmpty()) {
			ly = ForagingUi.statLine(g, font, "None", "-", lx, ly, lw, PvDraw.COLOR_MUTED) + 2;
		} else {
			for (var e : tickets.entrySet()) {
				if (ly + STAT_ROW > bottom) {
					return;
				}
				ly = ForagingUi.statLine(g, font, ForagingUi.pretty(e.getKey()), FormatUtil.commas(e.getValue()),
					lx, ly, lw, ticketColor(e.getKey())) + 1;
			}
			ly += 2;
		}

		if (ly + font.lineHeight + STAT_ROW > bottom) {
			return;
		}
		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Biome captures", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Map<String, Long> captures = orderedLongMap(safari.biomeCaptures(), BIOME_ORDER);
		if (captures.isEmpty()) {
			ly = ForagingUi.statLine(g, font, "None", "-", lx, ly, lw, PvDraw.COLOR_MUTED) + 2;
		} else {
			for (var e : captures.entrySet()) {
				if (ly + STAT_ROW > bottom) {
					return;
				}
				ly = ForagingUi.statLine(g, font, ForagingUi.pretty(e.getKey()), FormatUtil.commas(e.getValue()),
					lx, ly, lw, PvDraw.COLOR_TEXT) + 1;
			}
			ly += 2;
		}

		if (ly + font.lineHeight + STAT_ROW > bottom) {
			return;
		}
		ly = ForagingUi.sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Milestones", lx, ly, PvDraw.COLOR_MUTED);
		ly += font.lineHeight + 3;
		Map<String, Integer> miles = orderedIntMap(safari.milestoneTiers(), BIOME_ORDER);
		if (miles.isEmpty()) {
			ForagingUi.statLine(g, font, "None", "-", lx, ly, lw, PvDraw.COLOR_MUTED);
			return;
		}
		for (var e : miles.entrySet()) {
			if (ly + STAT_ROW > bottom) {
				return;
			}
			ly = ForagingUi.statLine(g, font, ForagingUi.pretty(e.getKey()), "Tier " + e.getValue(),
				lx, ly, lw, ENABLED) + 1;
		}
	}

	private void drawCritterGrid(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot.SafariInfo safari,
		int x, int y, int w, int h, int mx, int my
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;

		PvDraw.text(g, font, "Discovered critters", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 4;

		List<String> critters = new ArrayList<>(safari.discoveredCritters());
		critters.sort(Comparator.comparing(s -> ForagingUi.pretty(s).toLowerCase(Locale.ROOT)));

		this.gridX = rx;
		this.gridY = ry;
		this.gridW = rw;
		this.gridH = Math.max(0, y + h - PAD - ry);

		if (critters.isEmpty()) {
			this.gridMaxScroll = 0;
			PvDraw.textCentered(g, font, safari.present() ? "None discovered" : "No safari data",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = Math.max(1, (rw + SLOT_GAP) / (SLOT + SLOT_GAP));
		int rows = (critters.size() + cols - 1) / cols;
		int contentH = rows * (SLOT + SLOT_GAP) - SLOT_GAP;
		this.gridMaxScroll = Math.max(0, contentH - this.gridH);
		this.gridScroll = Math.min(this.gridScroll, this.gridMaxScroll);

		ItemStack fallback = new ItemStack(Items.DYE.lime());
		g.enableScissor(this.gridX, this.gridY, this.gridX + this.gridW, this.gridY + this.gridH);
		for (int i = 0; i < critters.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int bx = rx + col * (SLOT + SLOT_GAP);
			int by = ry + row * (SLOT + SLOT_GAP) - this.gridScroll;
			if (by + SLOT < this.gridY || by > this.gridY + this.gridH) {
				continue;
			}
			boolean hovered = mx >= bx && mx < bx + SLOT && my >= by && my < by + SLOT
				&& my >= this.gridY && my < this.gridY + this.gridH;
			PvDraw.fill(g, bx, by, SLOT, SLOT, ITEM_SLOT_BG);
			g.outline(bx, by, SLOT, SLOT, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER);
			String id = critters.get(i);
			drawCritterIcon(g, id, bx + 1, by + 1, fallback);
			this.zones.add(HoverZone.of(bx, Math.max(by, this.gridY), SLOT,
				Math.min(by + SLOT, this.gridY + this.gridH) - Math.max(by, this.gridY),
				List.of(
					PvTooltip.Line.title(ForagingUi.pretty(id), PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED, "Discovered", ENABLED)
				)));
		}
		g.disableScissor();
	}

	private static final int SAFARI_COLOR = 0xFF7CFF9A;
	private static final int COLOR_MAXED = 0xFF7CFF9A;

	private static int drawSafariEssenceShop(
		GuiGraphicsExtractor g, Font font, DungeonSnapshot.EssenceShop shop,
		int x, int y, int w, int bottom
	) {
		if (shop == null || y + font.lineHeight + 4 > bottom) {
			return y;
		}
		int headerH = Math.max(16, font.lineHeight + 2);
		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(y, headerH, 16, font.lineHeight);
		g.item(safariEssenceIcon(shop.iconId()), x, headerAlign.iconY());
		String bal = FormatUtil.commas(shop.balance());
		int balW = PvDraw.widthBold(font, bal);
		int headerLabelX = x + 16 + 4;
		int nameMax = Math.max(8, w - (headerLabelX - x) - balW - 4);
		PvDraw.textBold(g, font, ForagingUi.trim(font, shop.name() + " Essence", nameMax),
			headerLabelX, headerAlign.textY(), SAFARI_COLOR);
		PvDraw.textBold(g, font, bal, x + w - balW, headerAlign.textY(), SAFARI_COLOR);

		int ly = y + headerH + 4;
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		if (perks.isEmpty()) {
			return ly + 2;
		}
		int colGap = 8;
		int colW = Math.max(70, (w - colGap) / 2);
		int perkRows = Math.max(1, (perks.size() + 1) / 2);
		int avail = Math.max(font.lineHeight + 2, bottom - ly - 4);
		int rowH = Math.max(16, Math.max(font.lineHeight + 1, Math.min(18, avail / perkRows)));
		for (int i = 0; i < perks.size(); i++) {
			DungeonSnapshot.EssencePerk perk = perks.get(i);
			int col = i % 2;
			int row = i / 2;
			int px = x + col * (colW + colGap);
			int py = ly + row * rowH;
			if (py + font.lineHeight > bottom) {
				break;
			}
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(py, rowH, 16, font.lineHeight);
			g.item(safariPerkIcon(perk.id()), px, rowAlign.iconY());
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			int labelX = px + 16 + 2;
			String left = ForagingUi.trim(font, perk.name(), Math.max(8, colW - 16 - 2 - rightW - 2));
			int valueColor = perk.maxed() ? COLOR_MAXED : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, labelX, rowAlign.textY(), PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, px + colW, rowAlign.textY(), valueColor);
		}
		return ly + perkRows * rowH + 4;
	}

	private static ItemStack safariEssenceIcon(String id) {
		ItemStack stack = SkyBlockItemFactory.iconStack(id == null ? "ESSENCE_SAFARI" : id);
		if (usableIcon(stack)) {
			return stack;
		}
		stack = SkyBlockItemFactory.iconStack("ESSENCE_FOREST");
		return usableIcon(stack) ? stack : new ItemStack(Items.DYE.lime());
	}

	private static ItemStack safariPerkIcon(String perkId) {
		if (perkId == null) {
			return new ItemStack(Items.PAPER);
		}
		return switch (perkId) {
			case "critter_catcher" -> new ItemStack(Items.EGG);
			case "critter_master" -> new ItemStack(Items.NETHER_STAR);
			case "floortunate" -> new ItemStack(Items.STRING);
			case "fresh_footprints" -> new ItemStack(Items.LEATHER_BOOTS);
			case "head_start" -> new ItemStack(Items.MAP);
			case "hunting_hotspot" -> new ItemStack(Items.COMPASS);
			case "thawing" -> new ItemStack(Items.MAGMA_CREAM);
			case "deep_diver" -> new ItemStack(Items.TURTLE_HELMET);
			case "quickdraw" -> new ItemStack(Items.BOW);
			case "amateur_hour" -> new ItemStack(Items.BOOK);
			case "sparkling_specialist" -> new ItemStack(Items.AMETHYST_SHARD);
			default -> new ItemStack(Items.PAPER);
		};
	}

	private static void drawCritterIcon(GuiGraphicsExtractor g, String critterId, int x, int y, ItemStack fallback) {
		ItemStack stack = critterSkull(critterId);
		if (stack != null && !stack.isEmpty()) {
			g.item(stack, x, y);
			return;
		}
		if (critterId != null && !critterId.isBlank() && SkyBlockIconRenderer.hasKnownIcon(critterId)) {
			SkyBlockIconRenderer.draw(g, fallback, critterId, x, y, 16);
			return;
		}
		g.item(fallback, x, y);
	}

	private static ItemStack critterSkull(String critterId) {
		if (critterId == null || critterId.isBlank()) {
			return ItemStack.EMPTY;
		}
		AttributeShardsData.ensureLoaded();
		AttributeShardsData.Def def = AttributeShardsData.byCritterId(critterId);
		if (def != null) {
			ItemStack fromDef = SkyBlockItemFactory.iconStack(def.iconId());
			if (usableIcon(fromDef)) {
				return fromDef;
			}
			if (def.bazaarName() != null) {
				ItemStack bazaar = SkyBlockItemFactory.iconStack(def.bazaarName());
				if (usableIcon(bazaar)) {
					return bazaar;
				}
			}
		}
		String upper = critterId.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		for (String id : List.of("SHARD_" + upper, upper, "ATTRIBUTE_SHARD_" + upper + ";1")) {
			ItemStack stack = SkyBlockItemFactory.iconStack(id);
			if (usableIcon(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean usableIcon(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.is(Items.PAPER) || stack.is(Items.BARRIER)
			|| stack.is(Items.DYE.lime())) {
			return false;
		}
		// Bare PLAYER_HEAD renders as Steve — only accept textured skulls.
		if (stack.is(Items.PLAYER_HEAD)) {
			return SkyBlockItemFactory.isTexturedPlayerHead(stack);
		}
		return true;
	}

	private static int ticketColor(String id) {
		if (id == null) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "premium", "first_class" -> PvDraw.COLOR_GOLD;
			case "economy" -> PvDraw.COLOR_ACCENT;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	private static Map<String, Long> orderedLongMap(Map<String, Long> source, String[] order) {
		LinkedHashMap<String, Long> out = new LinkedHashMap<>();
		if (source == null || source.isEmpty()) {
			return out;
		}
		for (String key : order) {
			Long v = source.get(key);
			if (v != null && v > 0L) {
				out.put(key, v);
			}
		}
		List<String> rest = new ArrayList<>();
		for (String key : source.keySet()) {
			if (!out.containsKey(key) && source.get(key) != null && source.get(key) > 0L) {
				rest.add(key);
			}
		}
		rest.sort(String.CASE_INSENSITIVE_ORDER);
		for (String key : rest) {
			out.put(key, source.get(key));
		}
		return out;
	}

	private static Map<String, Integer> orderedIntMap(Map<String, Integer> source, String[] order) {
		LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
		if (source == null || source.isEmpty()) {
			return out;
		}
		for (String key : order) {
			Integer v = source.get(key);
			if (v != null && v > 0) {
				out.put(key, v);
			}
		}
		List<String> rest = new ArrayList<>();
		for (String key : source.keySet()) {
			if (!out.containsKey(key) && source.get(key) != null && source.get(key) > 0) {
				rest.add(key);
			}
		}
		rest.sort(String.CASE_INSENSITIVE_ORDER);
		for (String key : rest) {
			out.put(key, source.get(key));
		}
		return out;
	}
}
