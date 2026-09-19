package dev.vy.betterpv.client.networth;

import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.PvTooltip;
import dev.vy.betterpv.client.price.ItemPricer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NetworthBreakdown {
	/** SkyBlock gem shop: gems per booster cookie. */
	private static final double GEMS_PER_COOKIE = 325.0;
	/** Hypixel store pack: gems for $4.99. */
	private static final double GEMS_PER_PACK = 675.0;
	private static final double USD_PER_PACK = 4.99;
	private static final int MAX_ITEM_ROWS = 12;

	public record ItemLine(String id, String label, long count, double value) {
	}

	public record Line(String name, double value, List<ItemLine> items) {
		public Line(String name, double value) {
			this(name, value, List.of());
		}

		public Line {
			items = items == null ? List.of() : List.copyOf(items);
		}
	}

	private final double total;
	private final List<Line> categories;
	private final String note;

	public NetworthBreakdown(double total, List<Line> categories, String note) {
		this.total = total;
		this.categories = List.copyOf(categories);
		this.note = note == null ? "" : note;
	}

	public static NetworthBreakdown empty(String note) {
		return new NetworthBreakdown(0, List.of(), note);
	}

	public double total() {
		return this.total;
	}

	public List<Line> categories() {
		return this.categories;
	}

	public String note() {
		return this.note;
	}

	public List<String> tooltipLines() {
		return tooltipStyledLines().stream()
			.map(line -> line.spans().stream().map(PvTooltip.Span::text).reduce("", String::concat))
			.toList();
	}

	public List<PvTooltip.Line> tooltipStyledLines() {
		return tooltipStyledLines(null);
	}

	public List<PvTooltip.Line> tooltipStyledLines(NetworthMode mode) {
		return tooltipStyledLines(mode, false);
	}

	public List<PvTooltip.Line> tooltipStyledLines(NetworthMode mode, boolean showSackItems) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Networth: ", PvDraw.COLOR_WHITE),
			PvTooltip.Span.bold(FormatUtil.commas(Math.round(this.total)), PvDraw.COLOR_GOLD)
		)));
		if (mode != null) {
			lines.add(PvTooltip.Line.of(mode.display(), PvDraw.COLOR_MUTED));
		}
		lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
		for (Line line : this.categories) {
			if (line.value() <= 0) {
				continue;
			}
			String prefix = title(line.name()) + ": ";
			String value = FormatUtil.commas(Math.round(line.value()));
			lines.add(new PvTooltip.Line(List.of(
				PvTooltip.Span.of(prefix, PvDraw.COLOR_WHITE),
				PvTooltip.Span.of(value, PvDraw.COLOR_GOLD)
			)));
			List<ItemLine> items = line.items();
			if (items.isEmpty()) {
				continue;
			}
			boolean sacks = "sacks".equalsIgnoreCase(line.name());
			if (sacks && !showSackItems) {
				lines.add(PvTooltip.Line.of("  Hold L-shift for sack items", PvDraw.COLOR_MUTED));
				continue;
			}
			int shown = Math.min(MAX_ITEM_ROWS, items.size());
			for (int i = 0; i < shown; i++) {
				ItemLine item = items.get(i);
				String countBit = item.count() > 1L ? " ×" + FormatUtil.commas(item.count()) : "";
				lines.add(new PvTooltip.Line(List.of(
					PvTooltip.Span.of("  " + item.label() + countBit + ": ", PvDraw.COLOR_MUTED),
					PvTooltip.Span.of(FormatUtil.shortCoins(Math.round(item.value())), PvDraw.COLOR_GOLD)
				)));
			}
			if (items.size() > shown) {
				lines.add(PvTooltip.Line.of(
					"  +" + (items.size() - shown) + " more items",
					PvDraw.COLOR_MUTED
				));
			}
		}
		if (!this.note.isBlank()) {
			lines.add(PvTooltip.Line.of(this.note, PvDraw.COLOR_MUTED));
		}
		lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
		lines.add(profileValueLine());
		lines.add(PvTooltip.Line.of("(Price estimated with cookies)", PvDraw.COLOR_MUTED));
		lines.add(PvTooltip.Line.of("I do not condone IRL Trading", PvDraw.COLOR_MUTED));
		if (mode != null) {
			lines.add(PvTooltip.Line.of("", PvDraw.COLOR_MUTED));
			boolean cosmetic = mode.includeCosmetics();
			boolean unsoulbound = mode.soulboundFilter() == NetworthMode.SoulboundFilter.ONLY_UNSOULBOUND;
			lines.add(PvTooltip.Line.of(
				"Left click: " + (cosmetic ? "non-cosmetic" : "cosmetic"),
				PvDraw.COLOR_MUTED
			));
			lines.add(PvTooltip.Line.of(
				"Right click: " + (unsoulbound ? "all items" : "unsoulbound only"),
				PvDraw.COLOR_MUTED
			));
		}
		return lines;
	}

	/**
	 * NEU-style IRL estimate: networth → booster cookies (bazaar) → gems (325/cookie) → USD
	 * via the 675-gem / $4.99 pack.
	 */
	private PvTooltip.Line profileValueLine() {
		Double usd = profileValueUsd(this.total);
		if (usd == null) {
			return new PvTooltip.Line(List.of(
				PvTooltip.Span.of("Profile Value: ", PvDraw.COLOR_MUTED),
				PvTooltip.Span.of("-", 0xFFFF5555)
			));
		}
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.of("Profile Value: ", PvDraw.COLOR_WHITE),
			PvTooltip.Span.bold("$" + FormatUtil.commas(Math.round(usd)), 0xFF55FF55)
		));
	}

	public static Double profileValueUsd(double networthCoins) {
		if (networthCoins <= 0) {
			return 0.0;
		}
		double cookiePrice = ItemPricer.price("BOOSTER_COOKIE");
		if (cookiePrice <= 0) {
			return null;
		}
		double cookies = networthCoins / cookiePrice;
		return (cookies * GEMS_PER_COOKIE / GEMS_PER_PACK) * USD_PER_PACK;
	}

	private static String title(String id) {
		if ("storage".equalsIgnoreCase(id)) {
			return "Backpacks";
		}
		if ("sacks_bag".equalsIgnoreCase(id)) {
			return "Sacks Bag";
		}
		String[] parts = id.split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0)));
			if (part.length() > 1) {
				out.append(part.substring(1).toLowerCase(Locale.ROOT));
			}
		}
		return out.toString();
	}
}
