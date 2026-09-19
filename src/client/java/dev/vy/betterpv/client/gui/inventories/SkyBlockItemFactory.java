package dev.vy.betterpv.client.gui.inventories;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.vy.betterpv.client.api.HypixelApiClient;
import dev.vy.betterpv.client.data.FormatUtil;
import dev.vy.betterpv.client.data.InventorySnapshot;
import dev.vy.betterpv.client.data.PetLoreResolver;
import dev.vy.betterpv.client.data.PetSnapshot;
import dev.vy.betterpv.client.gui.PvDraw;
import dev.vy.betterpv.client.gui.SkyBlockSymbols;
import dev.vy.betterpv.client.networth.InventoryDecoder;
import dev.vy.betterpv.client.networth.ItemWorth;
import dev.vy.betterpv.client.networth.NbtAttrs;
import dev.vy.betterpv.client.neu.NeuRepoCache;
import dev.vy.betterpv.client.neu.SkyBlockPackCache;
import dev.vy.betterpv.client.price.HypixelItemsCache;
import dev.vy.betterpv.client.util.LegacyChatFormatting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

public final class SkyBlockItemFactory {
	private static final Pattern SKULL_VALUE = Pattern.compile(
		"Value\\s*:\\s*\"([^\"]+)\"",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern SKULL_SIGNATURE = Pattern.compile(
		"Signature\\s*:\\s*\"([^\"]+)\"",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern SKULL_ID = Pattern.compile(
		"Id\\s*:\\s*\"([0-9a-fA-F\\-]{32,36})\"",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern LEATHER_COLOR = Pattern.compile(
		"color\\s*:\\s*(\\d+)",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern ITEM_MODEL = Pattern.compile(
		"ItemModel\\s*:\\s*\"([^\"]+)\"",
		Pattern.CASE_INSENSITIVE
	);
	/** Hypixel sack rune ids: {@code RUNE_ZAP_1} → NEU {@code ZAP_RUNE;1}. */
	private static final Pattern SACK_RUNE_ID = Pattern.compile(
		"^RUNE_(.+)_(\\d+)$",
		Pattern.CASE_INSENSITIVE
	);
	/** Rarity word in NEU lore tails ({@code §5§lEPIC DUNGEON CHESTPLATE}, {@code §6§lLEGENDARY DYE}). */
	private static final Pattern RARITY_WORD = Pattern.compile(
		"(?i)\\b(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY[_ ]SPECIAL|SPECIAL|ULTIMATE)\\b"
	);
	private static final String[] DYE_COLORS = {
		"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};

	private static final Map<String, ItemStack> BASE_CACHE = new ConcurrentHashMap<>();
	/** SkyBlock id → {@code hypixel_skyblock:item/...} for custom GUI icons. */
	private static final Map<String, String> ITEM_MODEL_BY_ID = new ConcurrentHashMap<>();

	private SkyBlockItemFactory() {
	}

	public static void clearCache() {
		BASE_CACHE.clear();
		ITEM_MODEL_BY_ID.clear();
		SkyBlockItemIconCache.clear();
	}

	public static Identifier customIcon(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return null;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		String model = ITEM_MODEL_BY_ID.get(key);
		if (model == null) {
			rememberItemModel(key, resolveItemModel(key));
			model = ITEM_MODEL_BY_ID.get(key);
		}
		return SkyBlockItemIconCache.getOrRequest(model);
	}

	public static Identifier customIconModel(String skyblockId, String itemModel) {
		if (itemModel == null || itemModel.isBlank()) {
			return customIcon(skyblockId);
		}
		if (skyblockId != null && !skyblockId.isBlank()) {
			rememberItemModel(skyblockId.toUpperCase(Locale.ROOT), itemModel);
		}
		return SkyBlockItemIconCache.getOrRequest(itemModel);
	}

	public static int customIconSize(String skyblockId) {
		if (skyblockId == null) {
			return 16;
		}
		String model = ITEM_MODEL_BY_ID.get(skyblockId.toUpperCase(Locale.ROOT));
		return SkyBlockItemIconCache.textureSize(model);
	}

	/** Hypixel {@code item_model} string for a SkyBlock id, if known. */
	public static String itemModel(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return null;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		String model = ITEM_MODEL_BY_ID.get(key);
		if (model == null) {
			rememberItemModel(key, resolveItemModel(key));
			model = ITEM_MODEL_BY_ID.get(key);
		}
		return model;
	}

	/** Blocking warm on a worker thread so the first inventory paint already has textures. */
	public static void warmBlocking(InventorySnapshot snapshot) {
		NeuRepoCache.ensureLoadedBlocking();
		SkyBlockPackCache.ensureReadyBlocking();
		prefetch(snapshot);
		if (snapshot == null) {
			return;
		}
		Set<String> ids = new HashSet<>();
		collectIds(snapshot.inventory(), ids);
		for (InventorySnapshot.Page page : snapshot.enderChest()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.backpacks()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.wardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.equipmentWardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Loadout loadout : snapshot.loadouts()) collectLoadoutIds(loadout, ids);
		for (InventorySnapshot.Page page : snapshot.sacks()) collectIds(page, ids);
		collectIds(snapshot.fishingBag(), ids);
		collectIds(snapshot.potionBag(), ids);
		collectIds(snapshot.quiver(), ids);
		for (InventorySnapshot.Page page : snapshot.accessoryBag()) collectIds(page, ids);
		collectIds(snapshot.timePocket(), ids);
		collectIds(snapshot.personalVault(), ids);
		collectIds(snapshot.carnivalMasks(), ids);
		collectIds(snapshot.candyBag(), ids);
		for (String id : ids) {
			for (String candidate : neuCandidates(id)) {
				if (NeuRepoCache.get(candidate) == null) {
					NeuRepoCache.getOrFetch(candidate);
				}
			}
			baseStack(id);
			customIcon(id);
		}
	}

	/** Prefetch + warm stacks without delaying the profile UI. */
	public static void warmAsync(InventorySnapshot snapshot) {
		prefetch(snapshot);
		HypixelApiClient.parseExecutor().execute(() -> {
			try {
				warmBlocking(snapshot);
			} catch (Exception ignored) {
			}
		});
	}

	/** Prefetch pet skull / held-item icons in the background. */
	public static void warmPetsAsync(PetSnapshot pets) {
		prefetchPets(pets);
		HypixelApiClient.parseExecutor().execute(() -> {
			try {
				warmPetsBlocking(pets);
			} catch (Exception ignored) {
			}
		});
	}

	public static void prefetchPets(PetSnapshot pets) {
		if (pets == null || pets.isEmpty()) {
			return;
		}
		Set<String> ids = new HashSet<>();
		for (PetSnapshot.Entry pet : pets.pets()) {
			if (pet.neuId() != null && !pet.neuId().isBlank()) {
				ids.add(pet.neuId());
			}
			if (pet.hasHeldItem()) {
				ids.add(pet.heldItem());
			}
		}
		prefetchIds(ids);
	}

	/** Prefetch NEU defs for arbitrary SkyBlock item ids (collections / minions). */
	public static void prefetchIds(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		SkyBlockItemIconCache.ensurePack();
		Set<String> neuIds = new HashSet<>();
		for (String id : ids) {
			if (id == null || id.isBlank()) {
				continue;
			}
			neuIds.addAll(neuCandidates(id));
		}
		NeuRepoCache.prefetch(neuIds);
	}

	private static void warmPetsBlocking(PetSnapshot pets) {
		if (pets == null || pets.isEmpty()) {
			return;
		}
		for (PetSnapshot.Entry pet : pets.pets()) {
			if (pet.neuId() != null && !pet.neuId().isBlank()) {
				baseStack(pet.neuId());
				customIcon(pet.neuId());
			}
			if (pet.hasHeldItem()) {
				baseStack(pet.heldItem());
				customIcon(pet.heldItem());
			}
		}
	}

	public static void prefetch(InventorySnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		Set<String> ids = new HashSet<>();
		collectIds(snapshot.inventory(), ids);
		for (InventorySnapshot.Page page : snapshot.enderChest()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.backpacks()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.wardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.equipmentWardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Loadout loadout : snapshot.loadouts()) collectLoadoutIds(loadout, ids);
		for (InventorySnapshot.Page page : snapshot.sacks()) collectIds(page, ids);
		collectIds(snapshot.fishingBag(), ids);
		collectIds(snapshot.potionBag(), ids);
		collectIds(snapshot.quiver(), ids);
		for (InventorySnapshot.Page page : snapshot.accessoryBag()) collectIds(page, ids);
		collectIds(snapshot.timePocket(), ids);
		collectIds(snapshot.personalVault(), ids);
		collectIds(snapshot.carnivalMasks(), ids);
		collectIds(snapshot.candyBag(), ids);
		// Inventory pane button skulls (backpack / sacks / bags).
		ids.add("JUMBO_BACKPACK");
		ids.add("POCKET_SACK_IN_A_SACK");
		ids.add("LARGE_POTION_BAG");
		ids.add("LARGE_TALISMAN_BAG");
		ids.addAll(NeuRepoCache.sackItemIds());
		SkyBlockItemIconCache.ensurePack();
		Set<String> neuIds = new HashSet<>();
		for (String id : ids) {
			neuIds.addAll(neuCandidates(id));
		}
		NeuRepoCache.prefetch(neuIds);
	}

	private static void collectIds(InventorySnapshot.Page page, Set<String> ids) {
		if (page == null || page.slots() == null) {
			return;
		}
		for (InventorySnapshot.Slot slot : page.slots()) {
			if (slot != null && !slot.isEmpty() && slot.id() != null) {
				ids.add(slot.id());
			}
		}
	}

	private static void collectLoadoutIds(InventorySnapshot.Loadout loadout, Set<String> ids) {
		if (loadout == null) {
			return;
		}
		for (InventorySnapshot.Slot slot : loadout.equipment()) {
			if (slot != null && !slot.isEmpty() && slot.id() != null) {
				ids.add(slot.id());
			}
		}
		for (InventorySnapshot.Slot slot : loadout.armor()) {
			if (slot != null && !slot.isEmpty() && slot.id() != null) {
				ids.add(slot.id());
			}
		}
		if (loadout.pet() != null && !loadout.pet().isEmpty() && loadout.pet().id() != null) {
			ids.add(loadout.pet().id());
		}
	}

	public static ItemStack iconStack(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return ItemStack.EMPTY;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		if ("RUBY_VEILSHROOM".equals(key)) {
			ItemStack veil = baseStack("VEILSHROOM");
			if (!veil.isEmpty() && !veil.is(Items.PAPER)) {
				return veil.copy();
			}
			return new ItemStack(Items.RED_MUSHROOM);
		}
		NeuRepoCache.prefetch(neuCandidates(skyblockId));
		return baseStack(skyblockId).copy();
	}

	/**
	 * Heart of the Mountain tab icon - SkyBlock mining skull (Deep Caverns / HotM menu style head).
	 * Token of the Mountain is not in the items API; this is the closest NEU mining portal skull.
	 */
	public static ItemStack hotmTabIcon() {
		ItemStack sky = iconStack("MINING_2_PORTAL");
		if (sky != null && !sky.isEmpty() && sky.is(Items.PLAYER_HEAD)) {
			return sky;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		applySkullTextureValue(head, HOTM_TAB_SKULL_VALUE, null);
		return head;
	}

	/** NEU {@code MINING_2_PORTAL} skull Value (HotM / mining menu stand-in). */
	private static final String HOTM_TAB_SKULL_VALUE =
		"ewogICJ0aW1lc3RhbXAiIDogMTYxODk5OTIyNDk2OSwKICAicHJvZmlsZUlkIiA6ICI1NjY3NWIyMjMyZjA0ZWUwODkxNzllOWM5MjA2Y2ZlOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVJbmRyYSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83NDIxM2RjNmRjNGIxNjQxZGVmZDMzM2Y0YTQ3MzJjYzcxNGRkNjc3NzE4ZmExMGYxNDBhNjkzOWMxMmFhMzJiIgogICAgfQogIH0KfQ==";

	/**
	 * Classic Flawless gemstone player-head textures (pre-item-model NEU).
	 * Modern gems are paper; these skull Values still render the familiar 3D cubes.
	 */
	private static final Map<String, String> FLAWLESS_GEM_SKULLS = Map.ofEntries(
		Map.entry("JADE", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTY1NzA0NywKICAicHJvZmlsZUlkIiA6ICIxYWZhZjc2NWI1ZGY0NjA3YmY3ZjY1ZGYzYWIwODhhOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMb3lfQmxvb2RBbmdlbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mODlmNzVlMGIwMDM3OGE1ODNkYmJhNzI4ZGNkYzZlOTM0NmYzMWRkNjAxZDQ0OGYzZDYwNjE1Yzc0NjVjYzNlIgogICAgfQogIH0KfQ=="),
		Map.entry("AMBER", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTE3NjQ5NiwKICAicHJvZmlsZUlkIiA6ICJiNWRkZTVmODJlYjM0OTkzYmMwN2Q0MGFiNWY2ODYyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJsdXhlbWFuIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlkY2U2MmY3MGFjMDQ2Yjg4MTExM2M2Y2Y4NjI5ODc3Mjc3NzRlMjY1ODg1NTAxYzlhMjQ1YjE4MGRiMDhjMGQiCiAgICB9CiAgfQp9"),
		Map.entry("AMETHYST", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTQ2NjY1MiwKICAicHJvZmlsZUlkIiA6ICI2MWVhMDkyM2FhNDQ0OTEwYmNlZjViZmQ2ZDNjMGQ1NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVEYXJ0aEZhdGhlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMzYyMzUyMWM4MTExYWQyOWU5ZGNmN2FjYzU2MDg1YTlhYjA3ZGE3MzJkMTUxODk3NmFlZTYxZDBiM2UzYmQ2IgogICAgfQogIH0KfQ=="),
		Map.entry("SAPPHIRE", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjIzMjc2OSwKICAicHJvZmlsZUlkIiA6ICI5MWZlMTk2ODdjOTA0NjU2YWExZmMwNTk4NmRkM2ZlNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJoaGphYnJpcyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85NTdjZmE5Yzc1YmE1ODQ2NDVlZTJhZjZkOTg2N2Q3NjdkZGVhNDY2N2NkZmM3MmRjMTA2MWRkMTk3NWNhN2QwIgogICAgfQogIH0KfQ=="),
		Map.entry("TOPAZ", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjQxMjI5MSwKICAicHJvZmlsZUlkIiA6ICI1N2IzZGZiNWY4YTY0OWUyOGI1NDRlNGZmYzYzMjU2ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJYaWthcm8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDEwOTY0ZjNjNDc5YWQ3ZDlhZmFmNjhhNDJjYWI3YzEwN2QyZDg4NGY1NzVjYWUyZjA3MGVjNmY5MzViM2JlIgogICAgfQogIH0KfQ=="),
		Map.entry("JASPER", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjAzNDI0NywKICAicHJvZmlsZUlkIiA6ICJiMGQ3MzJmZTAwZjc0MDdlOWU3Zjc0NjMwMWNkOThjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPUHBscyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZjk5M2QzYTQzZDQwNTk3YjQ3NDQ4NTk3NjE2MGQwY2Y1MmFjNjRkMTU3MzA3ZDNiMWM5NDFkYjIyNGQwYWM2IgogICAgfQogIH0KfQ=="),
		Map.entry("RUBY", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4MzU3MzU0NywKICAicHJvZmlsZUlkIiA6ICJmZDQ3Y2I4YjgzNjQ0YmY3YWIyYmUxODZkYjI1ZmMwZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDVUNGTDEyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzkyNmEyNDhmYmJjMDZjZjA2ZTJjOTIwZWNhMWNhYzhhMmM5NjE2NGQzMjYwNDk0YmVkMTQyZDU1MzAyNmNjNiIKICAgIH0KICB9Cn0="),
		Map.entry("AQUAMARINE", "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MDU2MjAxNiwKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNb2phbmdIYXNBU3R1ZGlvIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2QzNzY5OWJkZjhjYjQzZWZmMTQ3ZDljZTE5YjE0ODAyZjliYzg4YTIxZTYzYWQyOGI2Njk1MzBlOGEzYzBiNTciCiAgICB9CiAgfQp9"),
		Map.entry("CITRINE", "ewogICJ0aW1lc3RhbXAiIDogMTcwODQ3NzA2NTU2NywKICAicHJvZmlsZUlkIiA6ICJhYWZmMDUwYTExOTk0NzM1YjEyNDVlNDk0MGFlZjY4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXN0SW1tb3J0YWwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjA2YjRmNjNkM2QzYTM5Yzk4NTY1YWY0ODU4YTUxMzViZTc3NGFkNjcyZWIyMzZiYjY1YWRmYzhjYjM0MjVlOCIKICAgIH0KICB9Cn0="),
		Map.entry("ONYX", "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MTg1MDQxOSwKICAicHJvZmlsZUlkIiA6ICI0NmNhODkyZTY4ODA0YThmYjFkYzkwYjg0ZTY5ZjVmZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPbG8xNjA2IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFjYTliMjg0MWY3MWJhNmZjNGQ2ZDJlOTdlODBjMTgwMWYzNmNhMzk1OGU1YTlhODFhNGY4Nzg1ZjY0MzQzNjciCiAgICB9CiAgfQp9"),
		Map.entry("OPAL", "ewogICJ0aW1lc3RhbXAiIDogMTcyMDA0MjA1OTQ4NywKICAicHJvZmlsZUlkIiA6ICI1ZTdmY2RjYTU5YzI0NjkwODAwNjg4OTNkODU1ODM3NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJKYWVsbGFyaSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81ZDE1ZWQ3MGU3MjAwNDBhZDczMTFlNjkzNTlkZmRmNWUxMTRlYWRkMmE0YzFmOTcxYTk1MDEzNDFhNDUyNjRiIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="),
		Map.entry("PERIDOT", "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MjI2OTYzMSwKICAicHJvZmlsZUlkIiA6ICIxZDIyYmUxYmQ2YTM0NWQ0OTI0Nzc4YmM4YWFlMjYzMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNQVJTX0hYIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE4ODRhOGRjYjcxMjgzNDFjZTA5Y2IzNjE2YzExYzNlNDZmODBkMDgzYTZmMmRiYjRkYmUwYmI5MzIzMThiMDkiCiAgICB9CiAgfQp9")
	);

	public static ItemStack gemstoneHead(String gem) {
		if (gem == null || gem.isBlank()) {
			return ItemStack.EMPTY;
		}
		String key = gem.trim().toUpperCase(Locale.ROOT);
		if (key.endsWith("_CRYSTAL")) {
			key = key.substring(0, key.length() - "_CRYSTAL".length());
		}
		if (key.endsWith("_GEM")) {
			key = key.substring(0, key.length() - "_GEM".length());
		}
		String value = FLAWLESS_GEM_SKULLS.get(key);
		if (value == null) {
			return ItemStack.EMPTY;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		applySkullTextureValue(head, value, null);
		return head;
	}

	public static ItemStack texturedHead(String value) {
		if (value == null || value.isBlank()) {
			return ItemStack.EMPTY;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		applySkullTextureValue(head, value, null);
		return head;
	}

	public static ItemStack trophySkullStack(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		String value = dev.vy.betterpv.client.data.TrophySkulls.value(id);
		if (value == null || value.isBlank()) {
			return new ItemStack(Items.PLAYER_HEAD);
		}
		return texturedHead(value);
	}


	public static ItemStack toStack(InventorySnapshot.Slot slot) {
		if (slot == null || slot.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack base = baseStack(slot.id());
		ItemStack stack = base.copy();
		// Sack totals (and other huge counts) show as a single icon; quantity goes in the tooltip.
		int displayCount = slot.count() > 64 ? 1 : Math.max(1, Math.min(64, slot.count()));
		stack.setCount(displayCount);

		// Prefer live inventory NBT (custom dyes / skulls) over NEU defaults.
		if (slot.skullValue() != null && !slot.skullValue().isBlank()) {
			if (!stack.is(Items.PLAYER_HEAD)) {
				stack = new ItemStack(Items.PLAYER_HEAD, stack.getCount());
			}
			applySkullTextureValue(stack, slot.skullValue(), slot.skullSignature());
		}
		if (slot.dyeColor() != null && isLeather(stack.getItem())) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(slot.dyeColor() & 0xFFFFFF));
		}

		String name = slot.displayName();
		if (name == null || name.isBlank()) {
			JsonObject neu = neuItem(slot.id());
			if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
				name = neu.get("displayname").getAsString();
				// Pets ship as "[Lvl {LVL}] Name" - strip the level placeholder for UI labels.
				name = name.replaceAll("(?i)\\[Lvl\\s*\\{?LVL\\}?\\]\\s*", "").trim();
			}
		}
		if (name == null || name.isBlank()) {
			JsonObject def = HypixelItemsCache.get(canonicalId(slot.id()));
			if (def == null) {
				def = HypixelItemsCache.get(slot.id() == null ? "" : slot.id().toUpperCase(Locale.ROOT));
			}
			if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
				name = def.get("name").getAsString();
				if (name != null && !name.contains("§") && def.has("tier") && def.get("tier").isJsonPrimitive()) {
					name = tierColorPrefix(def.get("tier").getAsString()) + name;
				}
			}
		}
		if (name == null || name.isBlank()) {
			name = prettyId(canonicalId(slot.id()));
		}
		stack.set(DataComponents.CUSTOM_NAME, legacyText(name).copy().withStyle(style -> style.withItalic(false)));

		List<Component> loreLines = new ArrayList<>();
		if (slot.lore() != null && !slot.lore().isEmpty()) {
			for (String raw : slot.lore()) {
				String cleaned = cleanLoreLine(raw);
				loreLines.add(cleaned.isBlank() ? Component.empty() : EnchantTooltip.colorize(legacyText(cleaned)));
			}
		} else {
			// Auction PET_* tags often ship with empty lore — resolve NEU pet text.
			InventorySnapshot.Slot petResolved = resolveAuctionPetIfNeeded(slot);
			if (petResolved != null && petResolved.lore() != null && !petResolved.lore().isEmpty()) {
				if (petResolved.displayName() != null && !petResolved.displayName().isBlank()) {
					name = petResolved.displayName();
					stack.set(DataComponents.CUSTOM_NAME, legacyText(name).copy().withStyle(style -> style.withItalic(false)));
				}
				for (String raw : petResolved.lore()) {
					String cleaned = cleanLoreLine(raw);
					loreLines.add(cleaned.isBlank() ? Component.empty() : EnchantTooltip.colorize(legacyText(cleaned)));
				}
			} else {
				JsonObject neu = neuItem(slot.id());
				if (neu != null && neu.has("lore") && neu.get("lore").isJsonArray()) {
					for (var el : neu.getAsJsonArray("lore")) {
						if (el.isJsonPrimitive()) {
							String line = el.getAsString();
							loreLines.add(line == null || line.isBlank() ? Component.empty() : EnchantTooltip.colorize(legacyText(line)));
						}
					}
				}
			}
		}
		applyRecombobulatorMarkers(loreLines, slot);
		if (!loreLines.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(loreLines));
		}
		return stack;
	}

	/**
	 * Cofl / AH {@code PET_JELLYFISH} → resolved NEU pet slot with PetLoreResolver lore.
	 */
	public static InventorySnapshot.Slot auctionPetSlot(String tag, String displayName, String tier) {
		if (tag == null || tag.isBlank()) {
			return null;
		}
		String key = tag.toUpperCase(Locale.ROOT);
		if (!key.startsWith("PET_") || key.startsWith("PET_ITEM_")) {
			return null;
		}
		String type = key.substring(4);
		if (type.isBlank()) {
			return null;
		}
		String rarity = normalizeTier(tier);
		if (rarity.isBlank()) {
			rarity = normalizeTier(resolveTier(key));
		}
		if (rarity.isBlank()) {
			rarity = "LEGENDARY";
		}
		int tierIndex = petTierIndex(rarity);
		String neuId = type + ";" + tierIndex;
		int level = parsePetLevel(displayName);
		String pretty = type.replace('_', ' ');
		PetSnapshot.Entry pet = new PetSnapshot.Entry(
			type,
			pretty,
			rarity,
			tierIndex,
			neuId,
			level,
			Math.max(100, level),
			0,
			0,
			0,
			0,
			0f,
			false,
			"",
			0,
			"",
			"",
			0
		);
		String name = PetLoreResolver.displayNameFor(pet);
		if (name == null || name.isBlank()) {
			name = displayName == null || displayName.isBlank() ? pretty : displayName;
		}
		return new InventorySnapshot.Slot(neuId, 1, PetLoreResolver.loreFor(pet), name, null, null, null);
	}

	private static InventorySnapshot.Slot resolveAuctionPetIfNeeded(InventorySnapshot.Slot slot) {
		if (slot == null || slot.id() == null) {
			return null;
		}
		String key = slot.id().toUpperCase(Locale.ROOT);
		if (!key.startsWith("PET_") || key.startsWith("PET_ITEM_")) {
			return null;
		}
		return auctionPetSlot(key, slot.displayName(), "");
	}

	private static int petTierIndex(String tier) {
		if (tier == null) {
			return 3;
		}
		return switch (normalizeTier(tier)) {
			case "COMMON" -> 0;
			case "UNCOMMON" -> 1;
			case "RARE" -> 2;
			case "EPIC" -> 3;
			case "LEGENDARY" -> 4;
			case "MYTHIC" -> 5;
			default -> 4;
		};
	}

	private static int parsePetLevel(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return 1;
		}
		Matcher m = Pattern.compile("(?i)\\[Lvl\\s*(\\d+)\\]").matcher(displayName);
		if (m.find()) {
			try {
				return Math.max(1, Integer.parseInt(m.group(1)));
			} catch (NumberFormatException ignored) {
			}
		}
		return 1;
	}

	public static List<Component> tooltipLines(InventorySnapshot.Slot slot, ItemStack rendered) {
		return tooltipLines(slot, rendered, true);
	}

	public static List<Component> tooltipLines(
		InventorySnapshot.Slot slot,
		ItemStack rendered,
		boolean includeEstimatedValue
	) {
		List<Component> lines = new ArrayList<>();
		Component name = rendered.get(DataComponents.CUSTOM_NAME);
		if (name == null && slot != null && slot.displayName() != null) {
			name = legacyText(slot.displayName());
		}
		if (name == null && slot != null) {
			name = Component.literal(prettyId(canonicalId(slot.id())));
		}
		if (name != null) {
			if (slot != null && slot.count() > 1) {
				lines.add(
					name.copy().append(
						Component.literal(" x" + slot.count()).withStyle(ChatFormatting.DARK_GRAY)
					)
				);
			} else {
				lines.add(name);
			}
		}
		ItemLore lore = rendered.get(DataComponents.LORE);
		if (lore != null) {
			lines.addAll(lore.lines());
		}
		// Re-apply so tooltip rendering always gets live obfuscated markers.
		applyRecombobulatorMarkers(lines, slot);
		if (includeEstimatedValue) {
			appendEstimatedValueHint(lines, slot);
		}
		return lines;
	}

	/** Inserts estimated value + click hint under the rarity line when the item has a price. */
	private static void appendEstimatedValueHint(List<Component> lines, InventorySnapshot.Slot slot) {
		if (slot == null || slot.isEmpty() || lines == null) {
			return;
		}
		InventoryDecoder.Stack worth = new InventoryDecoder.Stack(
			slot.id(),
			slot.count(),
			slot.extraAttributes() == null ? new CompoundTag() : slot.extraAttributes(),
			slot.lore() == null ? List.of() : slot.lore(),
			slot.soulbound(),
			slot.displayName(),
			slot.dyeColor(),
			slot.skullValue(),
			slot.skullSignature()
		);
		double value = ItemWorth.value(worth);
		if (value <= 0) {
			return;
		}
		String coins = FormatUtil.shortCoins(Math.round(value));
		// Fixed colours so this line does not steal the rarity / name colour.
		MutableComponent estimated = Component.literal("Estimated value: ")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
			.append(Component.literal(coins).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)));
		MutableComponent hint = Component.literal("Click to view value breakdown")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true));

		int rarityIndex = findRarityLineIndex(lines);
		int insertAt = rarityIndex >= 0 ? rarityIndex + 1 : lines.size();
		lines.add(insertAt, estimated);
		lines.add(insertAt + 1, hint);
	}

	/** Rarity / item-name colour for recombobulated rarity lines. */
	private static int estimatedHeaderColor(List<Component> lines, InventorySnapshot.Slot slot) {
		int rarityIndex = findRarityLineIndex(lines);
		if (rarityIndex >= 0) {
			Integer styled = firstNonWhiteColor(lines.get(rarityIndex));
			if (styled != null) {
				return styled;
			}
			String plain = lines.get(rarityIndex).getString();
			Matcher matcher = RARITY_WORD.matcher(plain == null ? "" : plain);
			if (matcher.find()) {
				return tierArgb(matcher.group(1));
			}
		}
		if (slot != null && slot.id() != null) {
			return tierArgb(resolveTier(slot.id()));
		}
		return 0xFF55FF55;
	}

	private static Integer firstNonWhiteColor(Component component) {
		if (component == null) {
			return null;
		}
		Style style = component.getStyle();
		if (style.getColor() != null) {
			int rgb = style.getColor().getValue() & 0xFFFFFF;
			if (rgb != 0xFFFFFF && rgb != 0xE8E8F0) {
				return 0xFF000000 | rgb;
			}
		}
		for (Component sibling : component.getSiblings()) {
			Integer nested = firstNonWhiteColor(sibling);
			if (nested != null) {
				return nested;
			}
		}
		return null;
	}

	private static int findRarityLineIndex(List<Component> lines) {
		for (int i = lines.size() - 1; i >= 0; i--) {
			String plain = lines.get(i).getString();
			if (plain != null && RARITY_WORD.matcher(plain).find()) {
				return i;
			}
		}
		return -1;
	}

	private static ItemStack baseStack(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return new ItemStack(Items.PAPER);
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		for (String candidate : neuCandidates(skyblockId)) {
			ItemStack cached = BASE_CACHE.get(candidate);
			if (cached != null) {
				copyItemModel(candidate, key);
				return cached;
			}
			ItemStack built = buildFromNeu(candidate);
			if (built != null) {
				BASE_CACHE.put(candidate, built.copy());
				if (!key.equals(candidate)) {
					BASE_CACHE.put(key, built.copy());
				}
				copyItemModel(candidate, key);
				return built;
			}
		}
		// Don't cache Hypixel fallbacks - NEU may still be downloading.
		return buildFromHypixel(key);
	}

	private static void copyItemModel(String fromId, String toId) {
		if (fromId == null || toId == null || fromId.equalsIgnoreCase(toId)) {
			return;
		}
		String model = ITEM_MODEL_BY_ID.get(fromId.toUpperCase(Locale.ROOT));
		if (model != null) {
			ITEM_MODEL_BY_ID.put(toId.toUpperCase(Locale.ROOT), model);
		}
	}

	private static JsonObject neuItem(String skyblockId) {
		for (String candidate : neuCandidates(skyblockId)) {
			JsonObject item = NeuRepoCache.get(candidate);
			if (item != null) {
				return item;
			}
		}
		return null;
	}

	/**
	 * NEU internal names often differ from Hypixel ids
	 * (e.g. {@code RED_STAINED_GLASS_PANE} → {@code STAINED_GLASS_PANE-14}, pets {@code TYPE;4},
	 * sack runes {@code RUNE_ZAP_1} → {@code ZAP_RUNE;1}).
	 */
	private static List<String> neuCandidates(String skyblockId) {
		List<String> out = new ArrayList<>();
		if (skyblockId == null || skyblockId.isBlank()) {
			return out;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		addCandidate(out, key);
		if (key.contains(":")) {
			addCandidate(out, key.replace(':', '-'));
		}
		if ("RUBY_VEILSHROOM".equals(key)) {
			addCandidate(out, "VEILSHROOM");
		}
		Matcher rune = SACK_RUNE_ID.matcher(key);
		if (rune.matches()) {
			addCandidate(out, rune.group(1) + "_RUNE;" + rune.group(2));
		}
		// Cofl / AH pet tags: PET_JELLYFISH → NEU JELLYFISH;3 (not PET_ITEM_*).
		if (key.startsWith("PET_") && !key.startsWith("PET_ITEM_")) {
			String type = key.substring(4);
			if (!type.isBlank()) {
				for (int i : new int[] { 3, 4, 5, 2, 1, 0 }) {
					addCandidate(out, type + ";" + i);
				}
				addCandidate(out, type);
			}
		}
		// COLOUR_STAINED_GLASS_PANE / COLOUR_WOOL / etc. → legacy NEU damage form
		for (String color : COLOR_NAMES) {
			String prefix = color + "_";
			if (!key.startsWith(prefix)) {
				continue;
			}
			String rest = key.substring(prefix.length());
			Integer damage = COLOR_DAMAGE.get(color);
			if (damage == null) {
				break;
			}
			String neuBase = switch (rest) {
				case "STAINED_GLASS_PANE" -> "STAINED_GLASS_PANE";
				case "STAINED_GLASS" -> "STAINED_GLASS";
				case "WOOL" -> "WOOL";
				case "CARPET" -> "CARPET";
				case "TERRACOTTA", "STAINED_HARDENED_CLAY", "STAINED_CLAY" -> "STAINED_CLAY";
				case "CONCRETE" -> "CONCRETE";
				case "CONCRETE_POWDER" -> "CONCRETE_POWDER";
				default -> null;
			};
			if (neuBase != null) {
				addCandidate(out, neuBase + "-" + damage);
				if (damage == 0) {
					addCandidate(out, neuBase);
				}
			}
			break;
		}
		return out;
	}

	private static void addCandidate(List<String> out, String key) {
		if (key != null && !key.isBlank() && !out.contains(key)) {
			out.add(key);
		}
	}

	private static String canonicalId(String skyblockId) {
		if (skyblockId == null) {
			return "";
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		int semi = key.indexOf(';');
		return semi >= 0 ? key.substring(0, semi) : key.replaceAll("-\\d+$", "");
	}

	private static final String[] COLOR_NAMES = {
		"LIGHT_BLUE", "LIGHT_GRAY", "WHITE", "ORANGE", "MAGENTA", "YELLOW", "LIME", "PINK",
		"GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
	};

	private static final Map<String, Integer> COLOR_DAMAGE = Map.ofEntries(
		Map.entry("WHITE", 0),
		Map.entry("ORANGE", 1),
		Map.entry("MAGENTA", 2),
		Map.entry("LIGHT_BLUE", 3),
		Map.entry("YELLOW", 4),
		Map.entry("LIME", 5),
		Map.entry("PINK", 6),
		Map.entry("GRAY", 7),
		Map.entry("LIGHT_GRAY", 8),
		Map.entry("CYAN", 9),
		Map.entry("PURPLE", 10),
		Map.entry("BLUE", 11),
		Map.entry("BROWN", 12),
		Map.entry("GREEN", 13),
		Map.entry("RED", 14),
		Map.entry("BLACK", 15)
	);

	private static ItemStack buildFromNeu(String key) {
		JsonObject item = NeuRepoCache.get(key);
		if (item == null) {
			// Kick off async fetch; next frame/open may resolve.
			NeuRepoCache.prefetch(List.of(key));
			return null;
		}
		String itemId = item.has("itemid") && item.get("itemid").isJsonPrimitive()
			? item.get("itemid").getAsString()
			: "";
		int damage = item.has("damage") && item.get("damage").isJsonPrimitive()
			? item.get("damage").getAsInt()
			: 0;
		String nbt = item.has("nbttag") && item.get("nbttag").isJsonPrimitive()
			? item.get("nbttag").getAsString()
			: null;
		String model = extract(ITEM_MODEL, nbt);
		rememberItemModel(key, model);
		return buildFromNeuFields(itemId, damage, nbt);
	}

	static ItemStack buildFromNeuFields(String itemId, int damage, String nbt) {
		if (isPlayerSkull(itemId, damage)) {
			ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
			applySkullTexture(skull, nbt);
			// Never cache / return a bare Steve head — let Hypixel fallback or callers skip.
			return isTexturedPlayerHead(skull) ? skull : null;
		}
		if (isLegacySkull(itemId)) {
			return new ItemStack(legacySkullByDamage(damage));
		}

		String model = extract(ITEM_MODEL, nbt);
		String resolveId;
		if (isBanner(itemId) || isBanner(model)) {
			// NEU often sets ItemModel white_banner while damage carries the real colour (e.g. Totem).
			resolveId = "minecraft:banner";
		} else if (model != null && model.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
			resolveId = model;
		} else {
			resolveId = itemId;
		}
		Item item = resolveItemId(resolveId, damage);
		ItemStack stack = new ItemStack(item == null ? Items.PAPER : item);
		Integer leather = extractLeatherColor(nbt);
		if (leather != null && isLeather(stack.getItem())) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(leather));
		}
		return stack;
	}

	private static ItemStack buildFromHypixel(String key) {
		JsonObject def = HypixelItemsCache.get(canonicalId(key));
		if (def == null) {
			def = HypixelItemsCache.get(key);
		}
		if (def != null && def.has("item_model") && def.get("item_model").isJsonPrimitive()) {
			rememberItemModel(key, def.get("item_model").getAsString());
		}
		String material = def != null && def.has("material") && def.get("material").isJsonPrimitive()
			? def.get("material").getAsString()
			: null;
		int durability = 0;
		if (def != null && def.has("durability") && def.get("durability").isJsonPrimitive()) {
			durability = def.get("durability").getAsInt();
		} else if (def != null && def.has("damage") && def.get("damage").isJsonPrimitive()) {
			durability = def.get("damage").getAsInt();
		}
		String skinValue = extractHypixelSkinValue(def);
		String skinSignature = extractHypixelSkinSignature(def);
		if (isBrokenHypixelPlaceholderSkin(skinValue)) {
			skinValue = null;
			skinSignature = null;
		}
		boolean skullMaterial = material != null && (
			material.equalsIgnoreCase("SKULL_ITEM")
				|| material.equalsIgnoreCase("SKULL")
				|| material.equalsIgnoreCase("PLAYER_HEAD")
		);
		if (skinValue != null) {
			ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
			// Hypixel item skins often carry invalid signatures; unsigned Value still loads the texture URL.
			applySkullTextureValue(skull, skinValue, null);
			return isTexturedPlayerHead(skull) ? skull : new ItemStack(Items.PAPER);
		}
		if (skullMaterial) {
			// Bare SKULL_ITEM without skin renders as Steve - prefer paper so callers can fall back.
			return new ItemStack(Items.PAPER);
		}
		Item item = resolveMaterial(material, key, durability);
		return new ItemStack(item);
	}

	private static String extractHypixelSkinValue(JsonObject def) {
		if (def == null || !def.has("skin")) {
			return null;
		}
		var skin = def.get("skin");
		if (skin.isJsonPrimitive()) {
			String value = skin.getAsString();
			return value == null || value.isBlank() ? null : value;
		}
		if (skin.isJsonObject() && skin.getAsJsonObject().has("value")
			&& skin.getAsJsonObject().get("value").isJsonPrimitive()) {
			String value = skin.getAsJsonObject().get("value").getAsString();
			return value == null || value.isBlank() ? null : value;
		}
		return null;
	}

	private static String extractHypixelSkinSignature(JsonObject def) {
		if (def == null || !def.has("skin") || !def.get("skin").isJsonObject()) {
			return null;
		}
		var skin = def.getAsJsonObject("skin");
		if (skin.has("signature") && skin.get("signature").isJsonPrimitive()) {
			String signature = skin.get("signature").getAsString();
			return signature == null || signature.isBlank() ? null : signature;
		}
		return null;
	}

	/**
	 * Hypixel’s items API reuses one DiscordApp placeholder skin for potion/talisman bags (and similar).
	 * Those heads never look like the real menu icons.
	 */
	private static boolean isBrokenHypixelPlaceholderSkin(String skinValue) {
		if (skinValue == null || skinValue.isBlank()) {
			return false;
		}
		try {
			String json = new String(java.util.Base64.getDecoder().decode(padBase64(skinValue.replaceAll("\\s+", ""))));
			return json.contains("24bbfd9d84f42456cd02a4baa5cd054bced0ddb2d1c8321c83e5d667cd85575a")
				|| json.contains("DiscordApp");
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String resolveItemModel(String skyblockId) {
		JsonObject neu = neuItem(skyblockId);
		if (neu != null && neu.has("nbttag") && neu.get("nbttag").isJsonPrimitive()) {
			String model = extract(ITEM_MODEL, neu.get("nbttag").getAsString());
			if (model != null) {
				return model;
			}
		}
		JsonObject def = HypixelItemsCache.get(canonicalId(skyblockId));
		if (def == null) {
			def = HypixelItemsCache.get(skyblockId);
		}
		if (def != null && def.has("item_model") && def.get("item_model").isJsonPrimitive()) {
			return def.get("item_model").getAsString();
		}
		return null;
	}

	private static void rememberItemModel(String skyblockId, String model) {
		if (skyblockId == null || skyblockId.isBlank() || model == null || model.isBlank()) {
			return;
		}
		if (!model.toLowerCase(Locale.ROOT).startsWith("hypixel_skyblock:")) {
			return;
		}
		ITEM_MODEL_BY_ID.put(skyblockId.toUpperCase(Locale.ROOT), model);
		SkyBlockItemIconCache.getOrRequest(model);
	}

	private static String tierColorPrefix(String tier) {
		if (tier == null) {
			return "§f";
		}
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "COMMON" -> "§f";
			case "UNCOMMON" -> "§a";
			case "RARE" -> "§9";
			case "EPIC" -> "§5";
			case "LEGENDARY" -> "§6";
			case "MYTHIC" -> "§d";
			case "DIVINE" -> "§b";
			case "SPECIAL", "VERY_SPECIAL" -> "§c";
			case "ULTIMATE" -> "§4";
			default -> "§f";
		};
	}

	public static int tierArgb(String tier) {
		String key = normalizeTier(tier);
		if (key.isBlank()) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (key) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			case "ULTIMATE" -> 0xFFAA0000;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	public static int tierArgbFromFormattedName(String formattedName) {
		return tierArgb(tierFromFormattingPrefix(formattedName));
	}

	public static String neuTier(String skyblockId) {
		JsonObject neu = neuItem(skyblockId);
		if (neu == null) {
			return "";
		}
		if (neu.has("tier") && neu.get("tier").isJsonPrimitive()) {
			String tier = normalizeTier(neu.get("tier").getAsString());
			if (!tier.isBlank()) {
				return tier;
			}
		}
		return tierFromNeuItem(neu);
	}

	/**
	 * Resolve rarity from Hypixel items definitions, then NEU.
	 * Cofl pet tags ({@code PET_JELLYFISH}) omit rarity - do not guess from {@code TYPE;n} NEU files.
	 */
	public static String resolveTier(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return "";
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		boolean petAuctionTag = key.startsWith("PET_") && !key.startsWith("PET_ITEM_");
		JsonObject hypixel = HypixelItemsCache.get(key);
		if (hypixel == null) {
			hypixel = HypixelItemsCache.get(canonicalId(skyblockId));
		}
		if (hypixel != null && hypixel.has("tier") && hypixel.get("tier").isJsonPrimitive()) {
			String hypixelTier = normalizeTier(hypixel.get("tier").getAsString());
			if (!hypixelTier.isBlank()) {
				return hypixelTier;
			}
		}
		if (!petAuctionTag) {
			String neu = neuTier(skyblockId);
			if (!neu.isBlank()) {
				return neu;
			}
		}
		return "";
	}

	public static String normalizeTier(String tier) {
		if (tier == null || tier.isBlank()) {
			return "";
		}
		String t = stripFormatting(tier).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (t.startsWith("VERY_SPECIAL")) {
			return "VERY_SPECIAL";
		}
		return switch (t) {
			case "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "ULTIMATE" -> t;
			default -> {
				Matcher m = RARITY_WORD.matcher(t);
				yield m.find() ? m.group(1).toUpperCase(Locale.ROOT).replace(' ', '_') : "";
			}
		};
	}

	/** Newer NEU items often omit {@code tier}; rarity lives in the last lore line / name colour. */
	private static String tierFromNeuItem(JsonObject neu) {
		if (neu.has("lore") && neu.get("lore").isJsonArray()) {
			JsonArray lore = neu.getAsJsonArray("lore");
			for (int i = lore.size() - 1; i >= 0; i--) {
				JsonElement el = lore.get(i);
				if (!el.isJsonPrimitive()) {
					continue;
				}
				String plain = stripFormatting(el.getAsString());
				if (plain.isBlank()) {
					continue;
				}
				Matcher m = RARITY_WORD.matcher(plain);
				if (m.find()) {
					return normalizeTier(m.group(1));
				}
			}
		}
		if (neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			return tierFromFormattingPrefix(neu.get("displayname").getAsString());
		}
		return "";
	}

	private static String tierFromFormattingPrefix(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		Matcher code = Pattern.compile("§([0-9a-fk-or])", Pattern.CASE_INSENSITIVE).matcher(text);
		while (code.find()) {
			String tier = switch (Character.toLowerCase(code.group(1).charAt(0))) {
				case 'a' -> "UNCOMMON";
				case '9' -> "RARE";
				case '5' -> "EPIC";
				case '6' -> "LEGENDARY";
				case 'd' -> "MYTHIC";
				case 'b' -> "DIVINE";
				case 'c' -> "SPECIAL";
				case '4' -> "ULTIMATE";
				case 'f', '7' -> "COMMON";
				default -> "";
			};
			if (!tier.isBlank()) {
				return tier;
			}
		}
		return "";
	}

	public static String plainDisplayName(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return "";
		}
		JsonObject neu = neuItem(skyblockId);
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			String name = neu.get("displayname").getAsString();
			if (name != null && !name.isBlank()) {
				name = name.replaceAll("(?i)\\[Lvl\\s*\\{?LVL\\}?\\]\\s*", "").trim();
				return stripFormatting(name);
			}
		}
		JsonObject def = HypixelItemsCache.get(canonicalId(skyblockId));
		if (def == null) {
			def = HypixelItemsCache.get(skyblockId.toUpperCase(Locale.ROOT));
		}
		if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
			String name = def.get("name").getAsString();
			if (name != null && !name.isBlank()) {
				return stripFormatting(name);
			}
		}
		return prettyId(canonicalId(skyblockId));
	}

	private static String stripFormatting(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}
		return text.replaceAll("§.", "").replaceAll("&[0-9a-fk-or]", "");
	}

	private static boolean isBanner(String itemId) {
		if (itemId == null || itemId.isBlank()) {
			return false;
		}
		String id = itemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return id.equals("banner") || id.endsWith("_banner");
	}

	private static boolean isPlayerSkull(String itemId, int damage) {
		if (itemId == null) {
			return false;
		}
		String id = itemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		if (id.equals("player_head")) {
			return true;
		}
		return (id.equals("skull") || id.equals("skull_item")) && damage == 3;
	}

	private static boolean isLegacySkull(String itemId) {
		if (itemId == null) {
			return false;
		}
		String id = itemId.toLowerCase(Locale.ROOT).replace("minecraft:", "");
		return id.equals("skull") || id.equals("skull_item") || id.equals("player_head");
	}

	private static Item legacySkullByDamage(int damage) {
		return switch (damage) {
			case 0 -> Items.SKELETON_SKULL;
			case 1 -> Items.WITHER_SKELETON_SKULL;
			case 2 -> Items.ZOMBIE_HEAD;
			case 4 -> Items.CREEPER_HEAD;
			case 5 -> Items.DRAGON_HEAD;
			default -> Items.PLAYER_HEAD;
		};
	}

	private static boolean isLeather(Item item) {
		return item == Items.LEATHER_HELMET
			|| item == Items.LEATHER_CHESTPLATE
			|| item == Items.LEATHER_LEGGINGS
			|| item == Items.LEATHER_BOOTS
			|| item == Items.LEATHER_HORSE_ARMOR;
	}

	private static Integer extractLeatherColor(String nbt) {
		if (nbt == null || nbt.isBlank()) {
			return null;
		}
		Matcher matcher = LEATHER_COLOR.matcher(nbt);
		if (!matcher.find()) {
			return null;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static String extract(Pattern pattern, String nbt) {
		if (nbt == null || nbt.isBlank()) {
			return null;
		}
		Matcher matcher = pattern.matcher(nbt);
		return matcher.find() ? matcher.group(1) : null;
	}

	private static void applySkullTexture(ItemStack stack, String nbt) {
		if (stack == null || !stack.is(Items.PLAYER_HEAD) || nbt == null || !nbt.contains("Value")) {
			return;
		}
		Matcher valueMatcher = SKULL_VALUE.matcher(nbt);
		if (!valueMatcher.find()) {
			return;
		}
		String value = valueMatcher.group(1).replaceAll("\\s+", "");
		String signature = null;
		Matcher signatureMatcher = SKULL_SIGNATURE.matcher(nbt);
		if (signatureMatcher.find()) {
			signature = signatureMatcher.group(1).replaceAll("\\s+", "");
		}
		applySkullTextureValue(stack, value, signature);
	}

	private static void applySkullTextureValue(ItemStack stack, String value, String signature) {
		if (stack == null || value == null || value.isBlank()) {
			return;
		}
		String padded = padBase64(value.replaceAll("\\s+", ""));
		// Prefer unsigned textures first: NEU/Hypixel signatures are often rejected by the client
		// skin pipeline, which leaves a bare Steve head even when PROFILE is set incorrectly.
		if (tryApplySkullProfile(stack, padded, null)) {
			return;
		}
		if (signature != null && !signature.isBlank() && tryApplySkullProfile(stack, padded, signature)) {
			return;
		}
	}

	private static boolean tryApplySkullProfile(ItemStack stack, String paddedValue, String signature) {
		try {
			CompoundTag propTag = new CompoundTag();
			propTag.putString("name", "textures");
			propTag.putString("value", paddedValue);
			if (signature != null && !signature.isBlank()) {
				propTag.putString("signature", signature);
			}
			ListTag propsList = new ListTag();
			propsList.add(propTag);
			CompoundTag profileTag = new CompoundTag();
			profileTag.putIntArray("id", new int[] {0, 0, 0, 1});
			profileTag.put("properties", propsList);
			var parsed = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, profileTag).result();
			if (parsed.isPresent()) {
				stack.set(DataComponents.PROFILE, parsed.get());
				if (isTexturedPlayerHead(stack)) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		try {
			UUID uuid = UUID.nameUUIDFromBytes(("betterpv:" + paddedValue).getBytes());
			Property textures = signature == null || signature.isBlank()
				? new Property("textures", paddedValue)
				: new Property("textures", paddedValue, signature);
			PropertyMap properties = new PropertyMap(ImmutableListMultimap.of("textures", textures));
			GameProfile profile = new GameProfile(uuid, "betterpv", properties);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			return isTexturedPlayerHead(stack);
		} catch (Exception ignored) {
			return false;
		}
	}

	/** True when a player head has a non-blank textures property (not a Steve placeholder). */
	public static boolean isTexturedPlayerHead(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
			return false;
		}
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) {
			return false;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return false;
			}
			for (Property prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static String padBase64(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		int pad = (4 - (value.length() % 4)) % 4;
		return pad == 0 ? value : value + "=".repeat(pad);
	}

	private static UUID parseUuid(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			if (raw.length() == 32) {
				String dashed = raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
					+ raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20);
				return UUID.fromString(dashed);
			}
			return UUID.fromString(raw);
		} catch (Exception exception) {
			return null;
		}
	}

	private static Item resolveItemId(String itemId, int damage) {
		if (itemId == null || itemId.isBlank()) {
			return Items.PAPER;
		}
		String normalized = itemId.toLowerCase(Locale.ROOT).replace(' ', '_');
		if (!normalized.contains(":")) {
			normalized = "minecraft:" + normalized;
		}
		normalized = remapLegacy(normalized, damage);
		try {
			Identifier id = Identifier.parse(normalized);
			return BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PAPER);
		} catch (Exception exception) {
			return Items.PAPER;
		}
	}

	private static String remapLegacy(String id, int damage) {
		return switch (id) {
			case "minecraft:skull" -> "minecraft:player_head";
			case "minecraft:dye", "minecraft:ink_sack" -> dyeByDamage(damage);
			case "minecraft:potato_item" -> "minecraft:potato";
			case "minecraft:carrot_item" -> "minecraft:carrot";
			case "minecraft:melon" -> "minecraft:melon_slice";
			case "minecraft:reeds" -> "minecraft:sugar_cane";
			case "minecraft:nether_stalk" -> "minecraft:nether_wart";
			case "minecraft:double_plant" -> switch (damage) {
				case 0 -> "minecraft:sunflower";
				case 1 -> "minecraft:lilac";
				case 2 -> "minecraft:tall_grass";
				case 3 -> "minecraft:large_fern";
				case 4 -> "minecraft:rose_bush";
				case 5 -> "minecraft:peony";
				default -> "minecraft:sunflower";
			};
			case "minecraft:red_flower" -> damage == 1 ? "minecraft:blue_orchid" : "minecraft:poppy";
			case "minecraft:banner", "minecraft:white_banner" -> colorPrefixed("banner", damage);
			case "minecraft:stained_glass_pane" -> colorPrefixed("stained_glass_pane", damage);
			case "minecraft:stained_glass" -> colorPrefixed("stained_glass", damage);
			case "minecraft:wool" -> colorPrefixed("wool", damage);
			case "minecraft:carpet" -> colorPrefixed("carpet", damage);
			case "minecraft:stained_hardened_clay", "minecraft:stained_terracotta" -> colorPrefixed("terracotta", damage);
			default -> id;
		};
	}

	private static String colorPrefixed(String suffix, int damage) {
		int idx = Math.max(0, Math.min(DYE_COLORS.length - 1, damage));
		return "minecraft:" + DYE_COLORS[idx] + "_" + suffix;
	}

	private static String dyeByDamage(int damage) {
		return switch (damage) {
			case 1 -> "minecraft:red_dye";
			case 2 -> "minecraft:green_dye";
			case 3 -> "minecraft:cocoa_beans";
			case 4 -> "minecraft:lapis_lazuli";
			case 5 -> "minecraft:purple_dye";
			case 6 -> "minecraft:cyan_dye";
			case 7 -> "minecraft:light_gray_dye";
			case 8 -> "minecraft:gray_dye";
			case 9 -> "minecraft:pink_dye";
			case 10 -> "minecraft:lime_dye";
			case 11 -> "minecraft:yellow_dye";
			case 12 -> "minecraft:light_blue_dye";
			case 13 -> "minecraft:magenta_dye";
			case 14 -> "minecraft:orange_dye";
			case 15 -> "minecraft:bone_meal";
			default -> "minecraft:ink_sac";
		};
	}

	private static Item resolveMaterial(String material, String skyblockId, int durability) {
		if (material != null && !material.isBlank()) {
			String upper = material.toUpperCase(Locale.ROOT);
			// Legacy coloured blocks used material + durability (meta).
			String remapped = switch (upper) {
				case "STAINED_GLASS_PANE" -> colorPrefixed("stained_glass_pane", durability);
				case "STAINED_GLASS" -> colorPrefixed("stained_glass", durability);
				case "WOOL" -> colorPrefixed("wool", durability);
				case "CARPET" -> colorPrefixed("carpet", durability);
				case "STAINED_CLAY", "STAINED_HARDENED_CLAY", "HARDENED_CLAY" -> colorPrefixed("terracotta", durability);
				case "CONCRETE" -> colorPrefixed("concrete", durability);
				case "CONCRETE_POWDER" -> colorPrefixed("concrete_powder", durability);
				default -> null;
			};
			if (remapped != null) {
				Optional<Item> colored = BuiltInRegistries.ITEM.getOptional(Identifier.parse(remapped));
				if (colored.isPresent()) {
					return colored.get();
				}
			}
			Item mapped = legacyMaterial(material);
			if (mapped != null) {
				return mapped;
			}
			String path = material.toLowerCase(Locale.ROOT);
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("minecraft", path));
			if (item.isPresent()) {
				return item.get();
			}
		}
		// Modern Hypixel ids like RED_STAINED_GLASS_PANE.
		for (String color : COLOR_NAMES) {
			String prefix = color + "_";
			String upperId = skyblockId == null ? "" : skyblockId.toUpperCase(Locale.ROOT);
			if (upperId.startsWith(prefix)) {
				String rest = upperId.substring(prefix.length()).toLowerCase(Locale.ROOT);
				Optional<Item> direct = BuiltInRegistries.ITEM.getOptional(
					Identifier.fromNamespaceAndPath("minecraft", color.toLowerCase(Locale.ROOT) + "_" + rest)
				);
				if (direct.isPresent()) {
					return direct.get();
				}
			}
		}
		return guessFromId(skyblockId);
	}

	private static Item legacyMaterial(String material) {
		return switch (material.toUpperCase(Locale.ROOT)) {
			case "SKULL_ITEM", "SKULL" -> Items.PLAYER_HEAD;
			case "INK_SACK" -> Items.INK_SAC;
			case "RAW_FISH" -> Items.COD;
			case "COOKED_FISH" -> Items.COOKED_COD;
			case "WATCH" -> Items.CLOCK;
			case "EMPTY_MAP" -> Items.MAP;
			case "BOOK_AND_QUILL" -> Items.WRITABLE_BOOK;
			case "FIREBALL" -> Items.FIRE_CHARGE;
			case "SPECKLED_MELON" -> Items.GLISTERING_MELON_SLICE;
			case "SULPHUR" -> Items.GUNPOWDER;
			case "NETHER_STALK" -> Items.NETHER_WART;
			case "WATER_LILY" -> Items.LILY_PAD;
			case "CARROT_ITEM" -> Items.CARROT;
			case "POTATO_ITEM" -> Items.POTATO;
			case "GRILLED_PORK" -> Items.COOKED_PORKCHOP;
			case "PORK" -> Items.PORKCHOP;
			case "EXP_BOTTLE" -> Items.EXPERIENCE_BOTTLE;
			case "FIREWORK" -> Items.FIREWORK_ROCKET;
			case "RED_ROSE" -> Items.POPPY;
			case "YELLOW_FLOWER" -> Items.DANDELION;
			case "WEB" -> Items.COBWEB;
			case "LEATHER_HELMET" -> Items.LEATHER_HELMET;
			case "LEATHER_CHESTPLATE" -> Items.LEATHER_CHESTPLATE;
			case "LEATHER_LEGGINGS" -> Items.LEATHER_LEGGINGS;
			case "LEATHER_BOOTS" -> Items.LEATHER_BOOTS;
			default -> null;
		};
	}

	private static Item guessFromId(String id) {
		if (id == null) {
			return Items.PAPER;
		}
		String upper = id.toUpperCase(Locale.ROOT);
		if (upper.contains("SWORD")) return Items.DIAMOND_SWORD;
		if (upper.contains("BOW")) return Items.BOW;
		if (upper.contains("PICKAXE")) return Items.DIAMOND_PICKAXE;
		if (upper.contains("AXE")) return Items.DIAMOND_AXE;
		if (upper.contains("HOE")) return Items.DIAMOND_HOE;
		if (upper.contains("SHOVEL")) return Items.DIAMOND_SHOVEL;
		if (upper.contains("HELMET") || upper.contains("HOOD")) return Items.DIAMOND_HELMET;
		if (upper.contains("CHESTPLATE") || upper.contains("TUNIC")) return Items.DIAMOND_CHESTPLATE;
		if (upper.contains("LEGGINGS") || upper.contains("PANTS")) return Items.DIAMOND_LEGGINGS;
		if (upper.contains("BOOTS")) return Items.DIAMOND_BOOTS;
		if (upper.contains("ARROW")) return Items.ARROW;
		if (upper.contains("POTION")) return Items.POTION;
		if (upper.contains("FISH") || upper.contains("SEA")) return Items.COD;
		if (upper.contains("SACK")) return Items.BUNDLE;
		return Items.PAPER;
	}

	private static String prettyId(String id) {
		if (id == null || id.isBlank()) {
			return "Unknown";
		}
		String[] parts = id.toLowerCase(Locale.ROOT).split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) continue;
			if (!out.isEmpty()) out.append(' ');
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	/**
	 * Hypixel API lore often keeps the recomb "a" glyphs but drops {@code obfuscated:true}.
	 * Rebuild the rarity line from {@code rarity_upgrades} (or placeholder a's) with real obfuscation.
	 */
	private static void applyRecombobulatorMarkers(List<Component> loreLines, InventorySnapshot.Slot slot) {
		if (loreLines == null || loreLines.isEmpty() || slot == null) {
			return;
		}
		int rarityIndex = findRarityLineIndex(loreLines);
		if (rarityIndex < 0) {
			return;
		}
		boolean recombed = NbtAttrs.intValue(slot.extraAttributes(), "rarity_upgrades", 0) > 0
			|| looksLikeRecombPlaceholders(loreLines.get(rarityIndex).getString());
		if (!recombed) {
			return;
		}
		loreLines.set(rarityIndex, recombobulatedRarityLine(loreLines.get(rarityIndex)));
	}

	private static boolean looksLikeRecombPlaceholders(String plain) {
		if (plain == null) {
			return false;
		}
		String trimmed = plain.trim();
		return trimmed.length() >= 5
			&& (trimmed.charAt(0) == 'a' || trimmed.charAt(0) == 'A')
			&& Character.isWhitespace(trimmed.charAt(1))
			&& (trimmed.charAt(trimmed.length() - 1) == 'a' || trimmed.charAt(trimmed.length() - 1) == 'A')
			&& Character.isWhitespace(trimmed.charAt(trimmed.length() - 2))
			&& RARITY_WORD.matcher(trimmed).find();
	}

	private static Component recombobulatedRarityLine(Component line) {
		if (line == null) {
			return Component.empty();
		}
		String plain = line.getString();
		if (plain == null || plain.isBlank()) {
			return line;
		}
		String core = stripRecombPlaceholders(plain.trim());
		if (core.isBlank()) {
			return line;
		}
		int colorRgb = estimatedHeaderColor(List.of(line), null);
		Style body = Style.EMPTY
			.withItalic(false)
			.withBold(true)
			.withColor(colorRgb)
			.withObfuscated(false);
		Style marker = body.withObfuscated(true);
		return Component.empty()
			.append(Component.literal("a").withStyle(marker))
			.append(Component.literal(" " + core + " ").withStyle(body))
			.append(Component.literal("a").withStyle(marker));
	}

	/** Removes static leading/trailing recomb "a" placeholders left by API lore. */
	private static String stripRecombPlaceholders(String plain) {
		String core = plain;
		if (core.length() >= 2 && (core.charAt(0) == 'a' || core.charAt(0) == 'A')
			&& Character.isWhitespace(core.charAt(1))) {
			core = core.substring(2).stripLeading();
		}
		if (core.length() >= 2 && (core.charAt(core.length() - 1) == 'a' || core.charAt(core.length() - 1) == 'A')
			&& Character.isWhitespace(core.charAt(core.length() - 2))) {
			core = core.substring(0, core.length() - 1).stripTrailing();
		}
		return core;
	}

	public static Component legacyLine(String text) {
		return EnchantTooltip.colorize(legacyText(text));
	}

	private static Component legacyText(String text) {
		if (text == null || text.isEmpty()) {
			return Component.empty();
		}
		text = SkyBlockSymbols.replace(text);
		MutableComponent root = Component.empty();
		Style style = Style.EMPTY.withItalic(false);
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if ((c == '§' || c == '&') && i + 1 < text.length()) {
				if (!buf.isEmpty()) {
					root.append(Component.literal(buf.toString()).withStyle(style));
					buf.setLength(0);
				}
				char code = Character.toLowerCase(text.charAt(++i));
				if (code == 'k') {
					style = style.withObfuscated(true);
					continue;
				}
				ChatFormatting formatting = ChatFormatting.getByCode(code);
				if (formatting != null) {
					if (LegacyChatFormatting.isColor(formatting) || formatting == ChatFormatting.RESET) {
						style = Style.EMPTY.withItalic(false);
					}
					style = style.applyFormat(formatting);
				}
			} else {
				buf.append(c);
			}
		}
		if (!buf.isEmpty()) {
			root.append(Component.literal(buf.toString()).withStyle(style));
		}
		return root;
	}

	private static String cleanLoreLine(String raw) {
		return InventoryDecoder.cleanJsonText(raw);
	}
}
