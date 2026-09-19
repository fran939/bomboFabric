package dev.vy.betterpv.client.gui.nav;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum PvTab {
	HOME(Items.PAPER, "betterpv.tab.home"),
	DUNGEONS(Items.SKELETON_SKULL, "betterpv.tab.dungeons"),
	INVENTORIES(Items.CHEST, "betterpv.tab.inventories"),
	PETS(Items.BONE, "betterpv.tab.pets"),
	AUCTIONS(Items.GOLD_BLOCK, "betterpv.tab.auctions"),
	COLLECTIONS(Items.ITEM_FRAME, "betterpv.tab.collections"),
	GARDEN(Items.IRON_HOE, "betterpv.tab.garden"),
	MINING(Items.IRON_PICKAXE, "betterpv.tab.mining"),
	FORAGING(Items.IRON_AXE, "betterpv.tab.foraging"),
	FISHING(Items.FISHING_ROD, "betterpv.tab.fishing"),
	CRIMSON(Items.NETHERRACK, "betterpv.tab.crimson"),
	RIFT(Items.ENDER_EYE, "betterpv.tab.rift"),
	MUSEUM(Items.EMERALD, "betterpv.tab.museum"),
	BESTIARY(Items.IRON_SWORD, "betterpv.tab.bestiary"),
	EVENTS(Items.FIREWORK_ROCKET, "betterpv.tab.events");

	private final Item iconItem;
	private final String langKey;

	PvTab(Item item, String langKey) {
		this.iconItem = item;
		this.langKey = langKey;
	}

	public ItemStack icon() {
		return new ItemStack(this.iconItem);
	}

	public Component label() {
		return Component.translatable(this.langKey);
	}

	/**
	 * Resolves {@code /pv <page>} aliases. Returns {@code null} when the token is not a page
	 * (caller should treat it as a player name).
	 */
	public static PvTab fromCommandArg(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "home" -> HOME;
			case "dungeons", "dungeon" -> DUNGEONS;
			case "storage", "inventories", "inventory" -> INVENTORIES;
			case "pets", "pet" -> PETS;
			case "auctions", "auction" -> AUCTIONS;
			case "collections", "collection" -> COLLECTIONS;
			case "garden" -> GARDEN;
			case "mining" -> MINING;
			case "foraging" -> FORAGING;
			case "fishing" -> FISHING;
			case "crimson" -> CRIMSON;
			case "rift" -> RIFT;
			case "museum" -> MUSEUM;
			case "bestiary" -> BESTIARY;
			case "events", "event" -> EVENTS;
			default -> null;
		};
	}

	/** Canonical {@code /pv <page>} tokens shown in command suggestions. */
	public static String[] commandAliases() {
		return new String[] {
			"home",
			"dungeons",
			"storage",
			"pets",
			"auctions",
			"collections",
			"garden",
			"mining",
			"foraging",
			"fishing",
			"crimson",
			"rift",
			"museum",
			"bestiary",
			"events"
		};
	}

	public PvSubTab[] subTabs() {
		return switch (this) {
			case HOME -> new PvSubTab[] { PvSubTab.HOME_OVERVIEW, PvSubTab.HOME_MISC };
			case DUNGEONS -> PvSubTab.NONE;
			case AUCTIONS -> new PvSubTab[] { PvSubTab.AUCTION_STATS, PvSubTab.AUCTION_SOLD, PvSubTab.AUCTION_BOUGHT };
			case COLLECTIONS -> new PvSubTab[] { PvSubTab.COLLECTIONS_LIST, PvSubTab.COLLECTIONS_MINIONS };
			case GARDEN -> new PvSubTab[] {
				PvSubTab.GARDEN_OVERVIEW,
				PvSubTab.GARDEN_VISITORS,
				PvSubTab.GARDEN_CROPS,
				PvSubTab.GARDEN_GREENHOUSE,
				PvSubTab.GARDEN_JACOB
			};
			case MINING -> new PvSubTab[] {
				PvSubTab.MINING_OVERVIEW,
				PvSubTab.MINING_HOTM,
				PvSubTab.MINING_GLACITE
			};
			case FORAGING -> new PvSubTab[] {
				PvSubTab.FORAGING_OVERVIEW,
				PvSubTab.FORAGING_HOTF,
				PvSubTab.FORAGING_HUNTING,
				PvSubTab.FORAGING_SAFARI,
				PvSubTab.FORAGING_ATTRIBUTE_SHARDS
			};
			case FISHING -> PvSubTab.NONE;
			case CRIMSON -> new PvSubTab[] {
				PvSubTab.CRIMSON_OVERVIEW,
				PvSubTab.CRIMSON_KUUDRA,
				PvSubTab.CRIMSON_ABIPHONE
			};
			case RIFT -> new PvSubTab[] { PvSubTab.RIFT_OVERVIEW, PvSubTab.RIFT_INVENTORY };
			case EVENTS -> new PvSubTab[] {
				PvSubTab.EVENTS_BINGO,
				PvSubTab.EVENTS_CHOCOLATE
			};
			default -> PvSubTab.NONE;
		};
	}

	public boolean hasSubTabs() {
		return subTabs().length > 0;
	}

	public boolean isInventorySplit() {
		return this == INVENTORIES;
	}

	public boolean isBestiarySplit() {
		return this == BESTIARY;
	}

	public boolean hasMuseumSort() {
		return this == MUSEUM;
	}

	public Object[] leftTabs() {
		if (this == MUSEUM) {
			return MuseumSort.values();
		}
		return subTabs();
	}
}
