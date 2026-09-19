package dev.vy.betterpv.client.gui.auctions;

import com.google.gson.JsonArray;
import dev.vy.betterpv.client.api.CoflnetApiClient;
import dev.vy.betterpv.client.data.AuctionSnapshot;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.inventories.SkyBlockIconRenderer;
import dev.vy.betterpv.client.gui.inventories.SkyBlockItemFactory;
import dev.vy.betterpv.client.gui.nav.PvSubTab;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Auction Stats / Sold / Bought for the profiled player. */
public final class AuctionPage {
	private static final int PAD = 6;
	private static final int GAP = 6;
	private static final int ROW_H = 20;
	private static final int ITEM = 16;
	private static final int COL_GAP = 6;
	private static final int LOAD_MORE_H = 16;
	private static final int STAT_ROW = 12;
	private static final float CREDIT_SCALE = 0.75F;
	private static final int CREDIT_GAP = 5;

	private AuctionSnapshot snapshot = AuctionSnapshot.empty();
	private int scroll;
	private int maxScroll;
	private int statsScroll;
	private int statsMaxScroll;
	private int listTop;
	private int listH;
	private int listX;
	private int listW;
	private int statsTop;
	private int statsH;
	private boolean scrollStats;
	private int loadMoreX;
	private int loadMoreY;
	private int loadMoreW;
	private boolean loadMoreVisible;
	private final AtomicBoolean loadingMore = new AtomicBoolean(false);
	private final java.util.Set<String> enrichedAuctionIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private InventorySnapshot.Slot hoveredSlot;
	private ItemStack hoveredStack = ItemStack.EMPTY;
	private AuctionSnapshot.Listing hoveredListing;

	public void apply(AuctionSnapshot snapshot) {
		this.snapshot = snapshot == null ? AuctionSnapshot.empty() : snapshot;
		this.scroll = 0;
		this.statsScroll = 0;
		this.loadingMore.set(false);
		this.enrichedAuctionIds.clear();
		prefetchAndEnrich(this.snapshot);
	}

	public AuctionSnapshot snapshot() {
		return this.snapshot;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.hoveredSlot = null;
		this.hoveredStack = ItemStack.EMPTY;
		this.hoveredListing = null;
		this.loadMoreVisible = false;

		AuctionSnapshot.Bucket bucket = bucketFor(sub);
		if (bucket == AuctionSnapshot.Bucket.ACTIVE) {
			renderStatsTab(g, font, x, y, w, h, mouseX, mouseY, screenW, screenH);
			return;
		}
		renderHistoryTab(g, font, bucket, x, y, w, h, mouseX, mouseY, screenW, screenH);
	}

	private void renderStatsTab(
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
		int rightW = Math.max(120, Math.min(168, w / 3));
		int leftW = w - rightW - GAP;
		int leftX = x;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, leftX, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);
		this.scrollStats = false;

		List<AuctionSnapshot.Listing> listings = this.snapshot.active();
		int cx = leftX + PAD;
		int cy = y + PAD;
		int innerW = leftW - PAD * 2;

		PvDraw.text(g, font, Component.translatable("betterpv.auctions.active").getString(), cx, cy, PvDraw.COLOR_MUTED);
		String summary = listings.size() + " · " + FormatUtil.shortCoins(this.snapshot.totalCoins(AuctionSnapshot.Bucket.ACTIVE));
		PvDraw.textRight(g, font, summary, cx + innerW, cy, PvDraw.COLOR_TEXT);
		cy += font.lineHeight + 4;

		this.listX = cx;
		this.listW = innerW;
		this.listTop = cy;
		this.listH = Math.max(20, h - PAD - (cy - y));
		int contentH = listings.size() * ROW_H;
		this.maxScroll = Math.max(0, contentH - this.listH);
		this.scroll = Math.min(this.scroll, this.maxScroll);

		g.enableScissor(cx, this.listTop, cx + innerW, this.listTop + this.listH);
		int rowY = this.listTop - this.scroll;
		for (AuctionSnapshot.Listing listing : listings) {
			if (rowY + ROW_H >= this.listTop && rowY <= this.listTop + this.listH) {
				drawCell(g, font, listing, AuctionSnapshot.Bucket.ACTIVE, cx, rowY, innerW, mouseX, mouseY);
			}
			rowY += ROW_H;
		}
		g.disableScissor();

		if (listings.isEmpty()) {
			String empty = Component.translatable("betterpv.auctions.empty").getString();
			PvDraw.textCentered(
				g, font, empty,
				leftX + leftW / 2,
				this.listTop + this.listH / 2 - font.lineHeight / 2,
				PvDraw.COLOR_MUTED
			);
		}

		drawStatsPanel(g, font, rightX, y, rightW, h, mouseX, mouseY);
		drawTooltip(g, font, mouseX, mouseY, screenW, screenH);
	}

	private void drawStatsPanel(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		AuctionSnapshot.Stats stats = this.snapshot.stats();
		int cx = x + PAD;
		int headerY = y + PAD;
		int innerW = w - PAD * 2;
		int scrollGutter = 6;
		int contentW = Math.max(20, innerW - scrollGutter);

		PvDraw.text(g, font, Component.translatable("betterpv.auctions.stats").getString(), cx, headerY, PvDraw.COLOR_MUTED);
		this.statsTop = headerY + font.lineHeight + 4;
		this.statsH = Math.max(20, h - PAD - (this.statsTop - y));
		this.scrollStats = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

		int contentH = measureStatsContent(font, stats);
		this.statsMaxScroll = Math.max(0, contentH - this.statsH);
		this.statsScroll = Math.min(this.statsScroll, this.statsMaxScroll);

		g.enableScissor(cx, this.statsTop, cx + contentW, this.statsTop + this.statsH);
		int cy = this.statsTop - this.statsScroll;

		cy = statRow(g, font, "betterpv.auctions.gold_earned", FormatUtil.shortCoins(stats.goldEarned()), cx, cy, contentW, 0xFFFFD36A);
		cy = statRow(g, font, "betterpv.auctions.gold_spent", FormatUtil.shortCoins(stats.goldSpent()), cx, cy, contentW, 0xFFFFD36A);
		if (stats.fees() > 0L) {
			cy = statRow(g, font, "betterpv.auctions.fees", FormatUtil.shortCoins(stats.fees()), cx, cy, contentW, PvDraw.COLOR_MUTED);
		}
		cy += 4;
		cy = statRow(g, font, "betterpv.auctions.created", FormatUtil.commas(stats.created()), cx, cy, contentW, PvDraw.COLOR_TEXT);
		cy = statRow(g, font, "betterpv.auctions.won", FormatUtil.commas(stats.won()), cx, cy, contentW, PvDraw.COLOR_TEXT);
		cy = statRow(g, font, "betterpv.auctions.bids", FormatUtil.commas(stats.bids()), cx, cy, contentW, PvDraw.COLOR_TEXT);
		cy = statRow(g, font, "betterpv.auctions.highest_bid", FormatUtil.shortCoins(stats.highestBid()), cx, cy, contentW, 0xFFFFD36A);

		cy += 6;
		cy = rarityBlock(g, font, "betterpv.auctions.sold_by_rarity", stats.totalSold(), cx, cy, contentW);
		cy += 4;
		rarityBlock(g, font, "betterpv.auctions.bought_by_rarity", stats.totalBought(), cx, cy, contentW);
		g.disableScissor();
		if (this.statsMaxScroll > 0) {
			drawStatsScrollCue(g, cx + innerW, this.statsTop, this.statsH);
		}
	}

	/** Thin accent scrollbar when stats content overflows the panel. */
	private void drawStatsScrollCue(GuiGraphicsExtractor g, int trackRightX, int top, int h) {
		int trackW = 2;
		int trackX = trackRightX - trackW;
		if (h <= 4) {
			return;
		}
		PvDraw.fill(g, trackX, top, trackW, h, 0x662A2A35);
		int thumbH = Math.max(8, (int) (h * (h / (double) (h + this.statsMaxScroll))));
		int travel = Math.max(0, h - thumbH);
		int thumbY = top + (this.statsMaxScroll <= 0
			? 0
			: (int) Math.round(travel * (this.statsScroll / (double) this.statsMaxScroll)));
		PvDraw.fill(g, trackX, thumbY, trackW, thumbH, PvDraw.COLOR_ACCENT);
	}

	private static int measureStatsContent(Font font, AuctionSnapshot.Stats stats) {
		int h = 0;
		h += STAT_ROW * 2; // earned + spent
		if (stats.fees() > 0L) {
			h += STAT_ROW;
		}
		h += 4;
		h += STAT_ROW * 4; // created, won, bids, highest
		h += 6;
		h += rarityBlockHeight(font, stats.totalSold());
		h += 4;
		h += rarityBlockHeight(font, stats.totalBought());
		return h;
	}

	private static int rarityBlockHeight(Font font, Map<String, Long> counts) {
		if (counts == null || counts.isEmpty()) {
			return 0;
		}
		return font.lineHeight + 2 + counts.size() * STAT_ROW;
	}

	private static int rarityBlock(
		GuiGraphicsExtractor g,
		Font font,
		String titleKey,
		Map<String, Long> counts,
		int x,
		int y,
		int w
	) {
		if (counts == null || counts.isEmpty()) {
			return y;
		}
		int cy = y;
		PvDraw.text(g, font, Component.translatable(titleKey).getString(), x, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		for (Map.Entry<String, Long> entry : sortRarityDescending(counts)) {
			String label = InventoryDecoder.prettyWords(entry.getKey());
			int color = SkyBlockItemFactory.tierArgb(entry.getKey());
			PvDraw.text(g, font, label, x, cy, color);
			PvDraw.textRight(g, font, FormatUtil.commas(entry.getValue()), x + w, cy, PvDraw.COLOR_TEXT);
			cy += STAT_ROW;
		}
		return cy;
	}

	private static List<Map.Entry<String, Long>> sortRarityDescending(Map<String, Long> counts) {
		List<Map.Entry<String, Long>> entries = new java.util.ArrayList<>(counts.entrySet());
		entries.sort((a, b) -> Integer.compare(rarityRank(b.getKey()), rarityRank(a.getKey())));
		return entries;
	}

	private static int rarityRank(String rarity) {
		if (rarity == null || rarity.isBlank()) {
			return -1;
		}
		return switch (rarity.toUpperCase(java.util.Locale.ROOT)) {
			case "ULTIMATE" -> 9;
			case "VERY_SPECIAL" -> 8;
			case "SPECIAL" -> 7;
			case "DIVINE" -> 6;
			case "MYTHIC" -> 5;
			case "LEGENDARY" -> 4;
			case "EPIC" -> 3;
			case "RARE" -> 2;
			case "UNCOMMON" -> 1;
			case "COMMON" -> 0;
			default -> -1;
		};
	}

	private static int statRow(
		GuiGraphicsExtractor g,
		Font font,
		String labelKey,
		String value,
		int x,
		int y,
		int w,
		int valueColor
	) {
		PvDraw.text(g, font, Component.translatable(labelKey).getString(), x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value, x + w, y, valueColor);
		return y + STAT_ROW;
	}

	private void renderHistoryTab(
		GuiGraphicsExtractor g,
		Font font,
		AuctionSnapshot.Bucket bucket,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		List<AuctionSnapshot.Listing> listings = this.snapshot.forBucket(bucket);
		int cols = 2;

		PvDraw.innerPanel(g, x, y, w, h);
		int cx = x + PAD;
		int cy = y + PAD;
		int innerW = w - PAD * 2;

		String title = titleFor(bucket);
		PvDraw.text(g, font, title, cx, cy, PvDraw.COLOR_MUTED);
		String summary = listings.size() + " · " + FormatUtil.shortCoins(this.snapshot.totalCoins(bucket));
		PvDraw.textRight(g, font, summary, cx + innerW, cy, PvDraw.COLOR_TEXT);
		cy += font.lineHeight + 4;

		boolean canMore = (bucket == AuctionSnapshot.Bucket.SOLD && this.snapshot.soldHasMore())
			|| (bucket == AuctionSnapshot.Bucket.BOUGHT && this.snapshot.boughtHasMore());
		int creditH = Math.max(1, Math.round(font.lineHeight * CREDIT_SCALE));
		int footerH = creditH + CREDIT_GAP + (canMore ? LOAD_MORE_H + CREDIT_GAP : 0);
		this.listX = cx;
		this.listW = innerW;
		this.listTop = cy;
		this.listH = Math.max(20, h - PAD - (cy - y) - footerH);

		int rows = listings.isEmpty() ? 0 : (listings.size() + cols - 1) / cols;
		int contentH = rows * ROW_H;
		this.maxScroll = Math.max(0, contentH - this.listH);
		this.scroll = Math.min(this.scroll, this.maxScroll);
		this.statsMaxScroll = 0;
		this.scrollStats = false;

		int cellW = (innerW - COL_GAP) / 2;

		g.enableScissor(cx, this.listTop, cx + innerW, this.listTop + this.listH);
		int rowY = this.listTop - this.scroll;
		for (int i = 0; i < listings.size(); i++) {
			int col = i % cols;
			int row = i / cols;
			int cellX = cx + col * (cellW + COL_GAP);
			int cellY = rowY + row * ROW_H;
			if (cellY + ROW_H >= this.listTop && cellY <= this.listTop + this.listH) {
				drawCell(g, font, listings.get(i), bucket, cellX, cellY, cellW, mouseX, mouseY);
			}
		}
		g.disableScissor();

		int footerY = y + h - PAD - creditH;
		if (canMore) {
			this.loadMoreX = cx;
			this.loadMoreY = footerY - CREDIT_GAP - LOAD_MORE_H;
			this.loadMoreW = innerW;
			this.loadMoreVisible = true;
			boolean hover = mouseX >= this.loadMoreX && mouseX < this.loadMoreX + this.loadMoreW
				&& mouseY >= this.loadMoreY && mouseY < this.loadMoreY + LOAD_MORE_H;
			String label = this.loadingMore.get()
				? "Loading…"
				: Component.translatable("betterpv.auctions.load_more").getString();
			PvDraw.fill(g, this.loadMoreX, this.loadMoreY, this.loadMoreW, LOAD_MORE_H, hover ? 0xFF2A3A55 : 0xFF16161E);
			g.outline(
				this.loadMoreX, this.loadMoreY, this.loadMoreW, LOAD_MORE_H,
				hover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER
			);
			PvDraw.textCentered(
				g, font, label,
				this.loadMoreX + this.loadMoreW / 2,
				this.loadMoreY + (LOAD_MORE_H - font.lineHeight) / 2,
				PvDraw.COLOR_TEXT
			);
		}

		String credit = Component.translatable("betterpv.auctions.credit").getString();
		PvDraw.textScaled(g, font, credit, cx, footerY, PvDraw.COLOR_BORDER, CREDIT_SCALE);

		if (listings.isEmpty()) {
			String empty = Component.translatable("betterpv.auctions.empty").getString();
			PvDraw.textCentered(
				g, font, empty,
				x + w / 2,
				this.listTop + this.listH / 2 - font.lineHeight / 2,
				PvDraw.COLOR_MUTED
			);
		}

		maybeAutofill(bucket, cols, listings.size());
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawTooltip(g, font, mouseX, mouseY, screenW, screenH);
	}

	/** Keep paging Cofl until the list fills the panel (or no more pages). */
	private void maybeAutofill(AuctionSnapshot.Bucket bucket, int cols, int count) {
		if (bucket != AuctionSnapshot.Bucket.SOLD && bucket != AuctionSnapshot.Bucket.BOUGHT) {
			return;
		}
		boolean canMore = (bucket == AuctionSnapshot.Bucket.SOLD && this.snapshot.soldHasMore())
			|| (bucket == AuctionSnapshot.Bucket.BOUGHT && this.snapshot.boughtHasMore());
		if (!canMore || this.listH <= 0) {
			return;
		}
		int rows = count <= 0 ? 0 : (count + cols - 1) / cols;
		int contentH = rows * ROW_H;
		if (contentH >= this.listH) {
			return;
		}
		requestMore(bucket);
	}

	private void drawTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.hoveredSlot == null || this.hoveredStack.isEmpty()) {
			return;
		}
		List<Component> tip = new java.util.ArrayList<>(
			SkyBlockItemFactory.tooltipLines(this.hoveredSlot, this.hoveredStack)
		);
		if (this.hoveredListing != null) {
			applyListingNameColor(tip, this.hoveredListing);
		}
		if (this.hoveredListing != null && this.hoveredListing.detailLines() != null
			&& !this.hoveredListing.detailLines().isEmpty()) {
			if (!tip.isEmpty()) {
				tip.add(Component.empty());
			}
			for (String line : this.hoveredListing.detailLines()) {
				if (line == null || line.isBlank()) {
					tip.add(Component.empty());
				} else {
					tip.add(SkyBlockItemFactory.legacyLine(line));
				}
			}
		}
		if (!tip.isEmpty()) {
			dev.vy.betterpv.client.gui.PvTooltip.drawComponents(g, font, tip, mouseX, mouseY, screenW, screenH);
		}
	}

	private void drawCell(
		GuiGraphicsExtractor g,
		Font font,
		AuctionSnapshot.Listing listing,
		AuctionSnapshot.Bucket bucket,
		int x,
		int y,
		int w,
		int mouseX,
		int mouseY
	) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H
			&& mouseY >= this.listTop && mouseY < this.listTop + this.listH;
		if (hover) {
			PvDraw.fill(g, x, y, w, ROW_H - 1, 0xFF1A1A28);
		}

		InventorySnapshot.Slot slot = listing.slot();
		ItemStack stack = ItemStack.EMPTY;
		if (slot != null && !slot.isEmpty()) {
			stack = SkyBlockItemFactory.toStack(slot);
			int ix = x + 1;
			int iy = y + (ROW_H - ITEM) / 2;
			SkyBlockIconRenderer.draw(g, stack, slot.id(), ix, iy, ITEM);
			if (hover) {
				this.hoveredSlot = slot;
				this.hoveredStack = stack;
				this.hoveredListing = listing;
			}
		}

		String price = FormatUtil.shortCoins(listing.price());
		int priceW = font.width(price);
		int textX = x + ITEM + 4;
		int textY = y + (ROW_H - font.lineHeight) / 2;
		int textMax = Math.max(12, w - ITEM - 8 - priceW - 4);

		String name = listing.itemName() == null || listing.itemName().isBlank() ? "?" : listing.itemName();
		String time = timeLabel(listing, bucket);
		String rest = time.isEmpty() ? "" : " - " + time;
		int restW = font.width(rest);
		String shownName = trim(font, name, Math.max(8, textMax - restW));
		int nameColor = nameColor(listing);

		PvDraw.text(g, font, shownName, textX, textY, nameColor);
		if (!rest.isEmpty()) {
			PvDraw.text(g, font, rest, textX + font.width(shownName), textY, PvDraw.COLOR_MUTED);
		}
		PvDraw.textRight(g, font, price, x + w - 2, textY, 0xFFFFD36A);
	}

	private static void applyListingNameColor(List<Component> tip, AuctionSnapshot.Listing listing) {
		if (tip.isEmpty() || listing == null) {
			return;
		}
		String raw = listing.itemName();
		if (raw == null || raw.isBlank()) {
			return;
		}
		if (raw.indexOf('§') >= 0) {
			tip.set(0, SkyBlockItemFactory.legacyLine(raw));
			return;
		}
		int color = nameColor(listing);
		String plain = net.minecraft.ChatFormatting.stripFormatting(raw);
		if (plain == null || plain.isBlank()) {
			return;
		}
		tip.set(0, PvDraw.styled(plain, color, false));
	}

	private static int nameColor(AuctionSnapshot.Listing listing) {
		String tier = listing.tier();
		if (tier == null || tier.isBlank()) {
			String id = listing.tag();
			if ((id == null || id.isBlank()) && listing.slot() != null) {
				id = listing.slot().id();
			}
			if (id != null && !id.isBlank()) {
				tier = SkyBlockItemFactory.resolveTier(id);
			}
		}
		int color = SkyBlockItemFactory.tierArgb(tier);
		if (color == PvDraw.COLOR_TEXT && listing.itemName() != null && !listing.itemName().isBlank()) {
			int fromName = SkyBlockItemFactory.tierArgbFromFormattedName(listing.itemName());
			if (fromName != PvDraw.COLOR_TEXT) {
				color = fromName;
			}
		}
		return color;
	}

	private static String timeLabel(AuctionSnapshot.Listing listing, AuctionSnapshot.Bucket bucket) {
		long now = System.currentTimeMillis();
		if (bucket == AuctionSnapshot.Bucket.ACTIVE && listing.endMs() > now) {
			return FormatUtil.prettySpan(listing.endMs() - now);
		}
		if (listing.endMs() > 0L) {
			long ago = Math.max(0L, now - listing.endMs());
			return ago < 60_000L ? "just now" : FormatUtil.prettySpan(ago) + " ago";
		}
		return "";
	}

	private static String trim(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(out.toString() + c) > budget) {
				break;
			}
			out.append(c);
		}
		return out + ellipsis;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.scrollStats && this.statsMaxScroll > 0) {
			int before = this.statsScroll;
			this.statsScroll = Math.max(
				0,
				Math.min(this.statsMaxScroll, this.statsScroll - (int) Math.signum(scrollY) * STAT_ROW * 2)
			);
			return this.statsScroll != before;
		}
		if (this.maxScroll <= 0) {
			return false;
		}
		int before = this.scroll;
		this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) Math.signum(scrollY) * ROW_H));
		return this.scroll != before;
	}

	public boolean mouseClicked(double mx, double my, PvSubTab sub) {
		if (!this.loadMoreVisible) {
			return false;
		}
		if (mx < this.loadMoreX || mx >= this.loadMoreX + this.loadMoreW
			|| my < this.loadMoreY || my >= this.loadMoreY + LOAD_MORE_H) {
			return false;
		}
		requestMore(bucketFor(sub));
		return true;
	}

	private void requestMore(AuctionSnapshot.Bucket bucket) {
		if (this.snapshot.playerUuid() == null || !this.loadingMore.compareAndSet(false, true)) {
			return;
		}
		if (bucket == AuctionSnapshot.Bucket.SOLD) {
			int next = this.snapshot.soldPage() + 1;
			CoflnetApiClient.playerAuctions(this.snapshot.playerUuid(), next).whenComplete((arr, err) ->
				Minecraft.getInstance().execute(() -> onMoreSold(arr.orElse(null), next)));
			return;
		}
		if (bucket == AuctionSnapshot.Bucket.BOUGHT) {
			int next = this.snapshot.boughtPage() + 1;
			CoflnetApiClient.playerBids(this.snapshot.playerUuid(), next).whenComplete((arr, err) ->
				Minecraft.getInstance().execute(() -> onMoreBought(arr.orElse(null), next)));
			return;
		}
		this.loadingMore.set(false);
	}

	private void onMoreSold(JsonArray arr, int page) {
		this.loadingMore.set(false);
		if (arr == null) {
			return;
		}
		List<AuctionSnapshot.Listing> extra = AuctionSnapshot.parseCoflSold(arr);
		this.snapshot = this.snapshot.withMoreSold(extra, page, arr.size() >= 10);
		prefetchAndEnrich(this.snapshot);
	}

	private void onMoreBought(JsonArray arr, int page) {
		this.loadingMore.set(false);
		if (arr == null) {
			return;
		}
		List<AuctionSnapshot.Listing> extra = AuctionSnapshot.parseCoflBought(arr);
		this.snapshot = this.snapshot.withMoreBought(extra, page, arr.size() >= 10);
		prefetchAndEnrich(this.snapshot);
	}

	/**
	 * Warm icons (incl. {@code PET_*} → NEU pet skulls) and enrich Cofl history rows.
	 * Player auction/bid summaries omit tier + upgrades; {@code /auction/{id}} has them.
	 */
	private void prefetchAndEnrich(AuctionSnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		java.util.Set<String> ids = new java.util.HashSet<>();
		java.util.List<AuctionSnapshot.Listing> needDetail = new java.util.ArrayList<>();
		for (AuctionSnapshot.Bucket bucket : AuctionSnapshot.Bucket.values()) {
			for (AuctionSnapshot.Listing listing : snapshot.forBucket(bucket)) {
				String id = listing.tag();
				if (id == null || id.isBlank()) {
					id = listing.slot() == null ? null : listing.slot().id();
				}
				if (id != null && !id.isBlank()) {
					ids.add(id);
				}
				if (listing.auctionId() == null || listing.auctionId().isBlank()) {
					continue;
				}
				String aid = listing.auctionId().replace("-", "").toLowerCase(java.util.Locale.ROOT);
				if (this.enrichedAuctionIds.contains(aid)) {
					continue;
				}
				boolean missingTier = listing.tier() == null || listing.tier().isBlank();
				boolean mangledStars = listing.itemName() != null && listing.itemName().indexOf('?') >= 0;
				if (missingTier || mangledStars || listing.detailLines() == null || listing.detailLines().isEmpty()) {
					needDetail.add(listing);
				}
			}
		}
		for (String id : ids) {
			SkyBlockItemFactory.iconStack(id);
		}
		if (needDetail.isEmpty()) {
			return;
		}
		dev.vy.betterpv.client.api.HypixelApiClient.parseExecutor().execute(() -> {
			try {
				java.util.Map<String, String> resolved = new java.util.LinkedHashMap<>();
				java.util.List<AuctionSnapshot.Listing> needCofl = new java.util.ArrayList<>();
				for (AuctionSnapshot.Listing listing : needDetail) {
					String tag = listing.tag();
					if ((tag == null || tag.isBlank()) && listing.slot() != null) {
						tag = listing.slot().id();
					}
					String tier = SkyBlockItemFactory.resolveTier(tag);
					boolean missingTier = listing.tier() == null || listing.tier().isBlank();
					if (missingTier && !tier.isBlank()) {
						resolved.put(listing.auctionId(), tier);
					}
					needCofl.add(listing);
				}
				if (!resolved.isEmpty()) {
					applyTiersOnClient(snapshot, resolved, false);
				}
				int budget = Math.min(36, needCofl.size());
				for (int i = 0; i < budget; i++) {
					AuctionSnapshot.Listing listing = needCofl.get(i);
					String auctionId = listing.auctionId();
					String aid = auctionId.replace("-", "").toLowerCase(java.util.Locale.ROOT);
					this.enrichedAuctionIds.add(aid);
					CoflnetApiClient.auction(auctionId).thenAccept(opt -> {
						if (opt.isEmpty()) {
							return;
						}
						AuctionSnapshot.Listing enriched = AuctionSnapshot.enrichFromCoflDetail(listing, opt.get());
						applyEnrichmentOnClient(snapshot, auctionId, enriched);
					});
				}
			} catch (Exception ignored) {
			}
		});
	}

	private void applyTiersOnClient(AuctionSnapshot expectedBase, java.util.Map<String, String> tiers, boolean overwrite) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (this.snapshot != expectedBase && this.snapshot.playerUuid() != null
				&& expectedBase.playerUuid() != null
				&& !this.snapshot.playerUuid().equals(expectedBase.playerUuid())) {
				return;
			}
			this.snapshot = this.snapshot.withTiers(tiers, overwrite);
		});
	}

	private void applyEnrichmentOnClient(
		AuctionSnapshot expectedBase,
		String auctionId,
		AuctionSnapshot.Listing enriched
	) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> {
			if (enriched == null || auctionId == null || auctionId.isBlank()) {
				return;
			}
			String aid = auctionId.replace("-", "").toLowerCase(java.util.Locale.ROOT);
			this.enrichedAuctionIds.add(aid);
			if (this.snapshot.playerUuid() != null
				&& expectedBase.playerUuid() != null
				&& !this.snapshot.playerUuid().equals(expectedBase.playerUuid())) {
				return;
			}
			this.snapshot = this.snapshot.withEnrichments(java.util.Map.of(auctionId, enriched));
		});
	}

	private void applyTiersOnClient(AuctionSnapshot expectedBase, java.util.Map<String, String> tiers) {
		applyTiersOnClient(expectedBase, tiers, false);
	}

	private static AuctionSnapshot.Bucket bucketFor(PvSubTab sub) {
		if (sub == PvSubTab.AUCTION_SOLD) {
			return AuctionSnapshot.Bucket.SOLD;
		}
		if (sub == PvSubTab.AUCTION_BOUGHT) {
			return AuctionSnapshot.Bucket.BOUGHT;
		}
		return AuctionSnapshot.Bucket.ACTIVE;
	}

	private static String titleFor(AuctionSnapshot.Bucket bucket) {
		return switch (bucket) {
			case ACTIVE -> Component.translatable("betterpv.auctions.active").getString();
			case SOLD -> Component.translatable("betterpv.sub.auction_sold").getString();
			case BOUGHT -> Component.translatable("betterpv.sub.auction_bought").getString();
		};
	}
}
