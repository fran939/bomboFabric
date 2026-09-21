package me.bombo.bomboaddons.features.magicfind;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockItemManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.profile.ProfileFetcher;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class MagicFindOptimizer {

    public enum Mode {
        GENERAL("General"),
        DIANA("Diana (Mythological)");

        public final String displayName;
        Mode(String displayName) {
            this.displayName = displayName;
        }
    }

    public enum Category {
        ALL("All"),
        BASE("Base"),
        EQUIPMENT("Equipment & Armor"),
        PETS("Pets & Items"),
        ENCHANTMENTS("Enchantments"),
        COLLECTIONS("Bestiary & Collections"),
        CONSUMABLES("Potions & Cakes"),
        COMMUNITY("Community & Misc");

        public final String displayName;
        Category(String displayName) {
            this.displayName = displayName;
        }
    }

    public static class MfSource {
        public final String id;
        public final String name;
        public final Category category;
        public final double magicFind;
        public final String skyblockItemId;
        public final long defaultCost;
        public final String description;
        public boolean isOwned;
        public boolean isManualOverride;

        public MfSource(String id, String name, Category category, double magicFind, String skyblockItemId, long defaultCost, String description) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.magicFind = magicFind;
            this.skyblockItemId = skyblockItemId;
            this.defaultCost = defaultCost;
            this.description = description;
            this.isOwned = false;
            this.isManualOverride = false;
        }

        public long getEstimatedCost() {
            if (skyblockItemId != null && !skyblockItemId.isEmpty()) {
                long p = LowestBinManager.getCachedPrice(skyblockItemId);
                if (p > 0L) return p;
            }
            return defaultCost;
        }

        public double getCostPerMf() {
            if (magicFind <= 0.0) return Long.MAX_VALUE;
            return (double) getEstimatedCost() / magicFind;
        }
    }

    public static class AnalysisResult {
        public String username;
        public Mode mode = Mode.GENERAL;
        public double currentMf;
        public double maxPossibleMf;
        public long totalCostToMax;
        public List<MfSource> sources = new ArrayList<>();
    }

    public static AnalysisResult evaluateProfile(ProfileFetcher.ProfileData profile) {
        return evaluateProfile(profile, Mode.GENERAL);
    }

    public static AnalysisResult evaluateProfile(ProfileFetcher.ProfileData profile, Mode mode) {
        String name = (profile != null && profile.username != null) ? profile.username : (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : "Player");
        return analyze(name, profile, mode);
    }

    private static final Map<String, Boolean> manualOverrides = new ConcurrentHashMap<>();
    private static final File OVERRIDES_FILE = new File(Minecraft.getInstance().gameDirectory, "config/bomboaddons/mf_overrides.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        loadOverrides();
    }

    public static void toggleManualOverride(String sourceId) {
        boolean curr = manualOverrides.getOrDefault(sourceId, false);
        if (curr) {
            manualOverrides.remove(sourceId);
        } else {
            manualOverrides.put(sourceId, true);
        }
        saveOverrides();
    }

    public static boolean isManuallyOwned(String sourceId) {
        return manualOverrides.getOrDefault(sourceId, false);
    }

    private static void loadOverrides() {
        try {
            if (OVERRIDES_FILE.exists()) {
                try (FileReader reader = new FileReader(OVERRIDES_FILE)) {
                    Type type = new TypeToken<Map<String, Boolean>>() {}.getType();
                    Map<String, Boolean> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) manualOverrides.putAll(loaded);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void saveOverrides() {
        try {
            OVERRIDES_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(OVERRIDES_FILE)) {
                GSON.toJson(manualOverrides, writer);
            }
        } catch (Exception ignored) {}
    }

    public static List<MfSource> getSourcesForMode(Mode mode) {
        List<MfSource> list = new ArrayList<>();

        // Base & Permanent
        list.add(new MfSource("base_player", "Base Player Magic Find", Category.BASE, 10.0, "", 0L, "Base SkyBlock Magic Find"));
        list.add(new MfSource("community_shop", "Community Shop Profile Upgrade", Category.COMMUNITY, 5.0, "", 0L, "5 Profile Upgrades for +5 Magic Find"));
        list.add(new MfSource("bestiary_milestone", "Bestiary Milestone 100+", Category.COLLECTIONS, 5.0, "", 0L, "+1 MF per 20 Bestiary Milestones (up to +5)"));
        list.add(new MfSource("pet_score", "Pet Score 500+", Category.PETS, 13.0, "", 0L, "+1 to +13 MF based on unique pets collected"));
        list.add(new MfSource("century_cake", "Century Cake (Magic Find)", Category.CONSUMABLES, 5.0, "CENTURY_CAKE", 15_000_000L, "+5 MF Cake Buff"));

        if (mode == Mode.DIANA) {
            list.add(new MfSource("diana_favor", "Diana's Favor (Carnival Perk)", Category.COMMUNITY, 3.0, "", 0L, "+3 MF on Mythological Mobs during event"));
            list.add(new MfSource("diana_bookshelf", "Diana's Bookshelf Upgrade", Category.COMMUNITY, 1.0, "DIANAS_BOOKSHELF", 5_000_000L, "+1 MF Rift/Hub permanent profile unlock"));

            // Weapons / Bestiary Scaling for Diana
            list.add(new MfSource("daedalus_axe", "⚚ Daedalus Blade (Fragged)", Category.EQUIPMENT, 15.0, "DAEDALUS_AXE", 110_000_000L, "Best-in-slot Diana weapon + pet stat mirroring"));
            list.add(new MfSource("mythological_bestiary", "Mythological Bestiary Scaling", Category.COLLECTIONS, 69.0, "", 0L, "+0.3 MF per Mythological Mob Tier completed with Daedalus Blade (up to +69 MF)"));

            // Armor for Diana (Crown of Avarice / Greed / 3/4 Mythos Armor)
            list.add(new MfSource("crown_of_avarice", "Crown of Avarice (Max Coins)", Category.EQUIPMENT, 25.0, "CROWN_OF_AVARICE", 1_200_000_000L, "+2.5 to +25 MF against Mythological Mobs"));
            list.add(new MfSource("mythos_armor_3_4", "3/4 Mythos Armor (Chest, Leggings, Boots)", Category.EQUIPMENT, 15.0, "MYTHOS_CHESTPLATE", 45_000_000L, "+15 MF against Mythological Mobs"));
            list.add(new MfSource("sorrow_armor_set", "Full Sorrow Armor Set (Alternative)", Category.EQUIPMENT, 40.0, "SORROW_CHESTPLATE", 96_000_000L, "+40 Base MF (4x Sorrow pieces)"));

            // Pet & Relic for Diana
            list.add(new MfSource("gdrag_pet", "Golden Dragon (Lvl 200, 1B Bank)", Category.PETS, 45.0, "PET-GOLDEN_DRAGON-LEGENDARY-200", 1_400_000_000L, "+10 Base MF + 20 MF Shining Scales + 15 MF Relic"));
            list.add(new MfSource("hephaestus_relic", "Hephaestus Relic Pet Item", Category.PETS, 15.0, "HEPHAESTUS_RELIC", 180_000_000L, "+50% Pet Stats boost on Golden Dragon (increases GDrag MF from +30 to +45)"));
            list.add(new MfSource("minos_relic", "Minos Relic Pet Item (Alternative)", Category.PETS, 10.0, "MINOS_RELIC", 45_000_000L, "+33.3% Pet Stats boost on Golden Dragon"));

            // Enchantments
            list.add(new MfSource("chimera_5", "Chimera V Enchantment", Category.ENCHANTMENTS, 45.0, "ENCHANTMENT_CHIMERA_5", 500_000_000L, "Copies 100% of Golden Dragon stats (+45 MF)"));
            list.add(new MfSource("divine_gift_3", "Divine Gift III Enchantment", Category.ENCHANTMENTS, 6.0, "ENCHANTMENT_DIVINE_GIFT_3", 14_000_000L, "+6 MF Weapon Enchantment"));

            // Consumables
            list.add(new MfSource("witches_stews", "Witches Stews (Alcina)", Category.CONSUMABLES, 2.0, "WITCHES_STEW", 4_000_000L, "+2 MF against specific Mythological Mob Types"));
        } else {
            // General Armor
            list.add(new MfSource("sorrow_helmet", "Sorrow Helmet", Category.EQUIPMENT, 10.0, "SORROW_HELMET", 22_000_000L, "+10 Base MF on Helmet"));
            list.add(new MfSource("sorrow_chestplate", "Sorrow Chestplate", Category.EQUIPMENT, 10.0, "SORROW_CHESTPLATE", 28_000_000L, "+10 Base MF on Chestplate"));
            list.add(new MfSource("sorrow_leggings", "Sorrow Leggings", Category.EQUIPMENT, 10.0, "SORROW_LEGGINGS", 24_000_000L, "+10 Base MF on Leggings"));
            list.add(new MfSource("sorrow_boots", "Sorrow Boots", Category.EQUIPMENT, 10.0, "SORROW_BOOTS", 22_000_000L, "+10 Base MF on Boots"));
            list.add(new MfSource("clover_helmet", "Clover Helmet", Category.EQUIPMENT, 5.0, "CLOVER_HELMET", 8_000_000L, "Alternative Lucky Helmet"));

            // General Weapons
            list.add(new MfSource("daedalus_axe", "Daedalus Axe / Blade", Category.EQUIPMENT, 15.0, "DAEDALUS_AXE", 65_000_000L, "Weapon with base MF & pet stats copy"));
            list.add(new MfSource("divine_gift_3", "Divine Gift III Enchantment", Category.ENCHANTMENTS, 6.0, "ENCHANTMENT_DIVINE_GIFT_3", 14_000_000L, "+6 MF weapon enchantment"));
            list.add(new MfSource("chimera_5", "Chimera V Enchantment", Category.ENCHANTMENTS, 50.0, "ENCHANTMENT_CHIMERA_5", 500_000_000L, "Copies 100% of Pet Magic Find onto weapon"));

            // General Pets & Relics
            list.add(new MfSource("black_cat_pet", "Black Cat Pet (Level 100)", Category.PETS, 15.0, "PET-BLACK_CAT-LEGENDARY-100", 35_000_000L, "+15 MF at Lvl 100"));
            list.add(new MfSource("gdrag_pet", "Golden Dragon (Lvl 200, 1B Bank)", Category.PETS, 50.0, "PET-GOLDEN_DRAGON-LEGENDARY-200", 1_400_000_000L, "Massive +50 MF scaling with bank"));
            list.add(new MfSource("hephaestus_relic", "Hephaestus Relic Pet Item", Category.PETS, 25.0, "HEPHAESTUS_RELIC", 180_000_000L, "+50% Pet Stats boost on Golden Dragon / Black Cat"));
            list.add(new MfSource("minos_relic", "Minos Relic Pet Item", Category.PETS, 16.6, "MINOS_RELIC", 45_000_000L, "+33.3% Pet Stats boost on Golden Dragon / Black Cat"));
            list.add(new MfSource("clover_pet_item", "Lucky Clover Pet Item", Category.PETS, 7.0, "MAGIC_8_BALL", 12_000_000L, "+7 Magic Find pet item"));
        }

        // Equipment (Both)
        list.add(new MfSource("rift_necklace", "Rift Necklace (Max Timecharms)", Category.EQUIPMENT, 8.0, "RIFT_NECKLACE", 10_000_000L, "+8 MF with all 8 Rift Timecharms unlocked"));
        list.add(new MfSource("clover_ring", "Lucky Clover Ring", Category.EQUIPMENT, 2.0, "LUCKY_CLOVER_RING", 5_000_000L, "Lucky Clover Reforged Equipment"));
        list.add(new MfSource("davids_cloak", "David's Cloak", Category.EQUIPMENT, 2.0, "DAVIDS_CLOAK", 18_000_000L, "+2 MF Cloak Equipment"));
        list.add(new MfSource("enrichments_mf", "Magic Find Enrichments (x65)", Category.EQUIPMENT, 32.5, "MAGIC_FIND_ENRICHMENT", 195_000_000L, "+0.5 MF per Legendary/Mythic Accessory (up to +32.5 MF)"));

        // Consumables & Potions (Both)
        list.add(new MfSource("god_potion", "God Potion (Magic Find IV)", Category.CONSUMABLES, 75.0, "GOD_POTION", 1_200_000L, "+75 Magic Find potion buff"));
        list.add(new MfSource("magic_find_beacon", "Beacon V (Magic Find Tier)", Category.CONSUMABLES, 10.0, "BEACON", 35_000_000L, "+10 MF Beacon Profile buff"));
        list.add(new MfSource("booster_cookie", "Booster Cookie Buff", Category.CONSUMABLES, 15.0, "BOOSTER_COOKIE", 11_500_000L, "+15 Magic Find cookie active"));

        return list;
    }

    public static List<MfSource> getDefaultSources() {
        return getSourcesForMode(Mode.GENERAL);
    }

    public static AnalysisResult analyze(String username, ProfileFetcher.ProfileData profile, Mode mode) {
        AnalysisResult res = new AnalysisResult();
        res.username = username;
        res.mode = mode != null ? mode : Mode.GENERAL;
        List<MfSource> list = getSourcesForMode(res.mode);

        Set<String> ownedIds = new HashSet<>();
        if (profile != null) {
            checkItemStacks(profile.inventory, ownedIds);
            checkItemStacks(profile.armor, ownedIds);
            checkItemStacks(profile.wardrobe, ownedIds);
            checkItemStacks(profile.enderChest, ownedIds);
            checkItemStacks(profile.personalVault, ownedIds);
            checkItemStacks(profile.accessories, ownedIds);
            checkItemStacks(profile.equipment, ownedIds);
            if (profile.backpacks != null) {
                for (List<ItemStack> bp : profile.backpacks.values()) {
                    checkItemStacks(bp, ownedIds);
                }
            }
        }

        // Check Pet items on Golden Dragon or Black Cat
        boolean hasHephaestusOnPet = false;
        boolean hasMinosOnPet = false;
        if (profile != null && profile.pets != null) {
            for (ProfileFetcher.Pet pet : profile.pets) {
                if (pet != null && pet.heldItem != null) {
                    String h = pet.heldItem.toUpperCase(Locale.ROOT);
                    if (h.contains("HEPHAESTUS")) {
                        hasHephaestusOnPet = true;
                    }
                    if (h.contains("MINOS_RELIC") || h.contains("MINOS")) {
                        hasMinosOnPet = true;
                    }
                }
            }
        }

        double totalCurrent = 0.0;
        double totalMax = 0.0;
        long neededCost = 0L;

        for (MfSource src : list) {
            boolean manual = isManuallyOwned(src.id);
            boolean autoOwned = false;

            if (src.skyblockItemId != null && !src.skyblockItemId.isEmpty()) {
                if (ownedIds.contains(src.skyblockItemId.toUpperCase(Locale.ROOT))) {
                    autoOwned = true;
                }
            }

            if ("gdrag_pet".equals(src.id)) {
                if (hasPet(profile, "GOLDEN_DRAGON", 200)) autoOwned = true;
            } else if ("black_cat_pet".equals(src.id)) {
                if (hasPet(profile, "BLACK_CAT", 100)) autoOwned = true;
            } else if ("hephaestus_relic".equals(src.id)) {
                if (hasHephaestusOnPet || ownedIds.contains("HEPHAESTUS_RELIC")) autoOwned = true;
            } else if ("minos_relic".equals(src.id)) {
                if (hasMinosOnPet || hasHephaestusOnPet || ownedIds.contains("MINOS_RELIC") || ownedIds.contains("HEPHAESTUS_RELIC")) autoOwned = true;
            } else if ("base_player".equals(src.id)) {
                autoOwned = true;
            } else if ("mythological_bestiary".equals(src.id)) {
                if (hasMythologicalBestiary(profile)) autoOwned = true;
            }

            // If player already has Hephaestus Relic, they don't need Minos Relic
            if ("minos_relic".equals(src.id) && (hasHephaestusOnPet || ownedIds.contains("HEPHAESTUS_RELIC"))) {
                autoOwned = true;
            }

            src.isManualOverride = manual;
            src.isOwned = manual || autoOwned;

            totalMax += src.magicFind;
            if (src.isOwned) {
                totalCurrent += src.magicFind;
            } else {
                neededCost += src.getEstimatedCost();
            }
            res.sources.add(src);
        }

        res.currentMf = totalCurrent;
        res.maxPossibleMf = totalMax;
        res.totalCostToMax = neededCost;
        return res;
    }

    private static void checkItemStacks(List<ItemStack> list, Set<String> out) {
        if (list == null) return;
        for (ItemStack stack : list) {
            if (stack == null || stack.isEmpty()) continue;
            String sbId = SkyblockUtils.getSkyblockId(stack);
            if (sbId != null && !sbId.isEmpty()) {
                out.add(sbId.toUpperCase(Locale.ROOT));
            }
            // Check displayName for special items
            String name = stack.getHoverName().getString();
            if (name.contains("Rift Necklace")) out.add("RIFT_NECKLACE");
            if (name.contains("Hephaestus Relic")) out.add("HEPHAESTUS_RELIC");
            if (name.contains("Minos Relic")) out.add("MINOS_RELIC");
            if (name.contains("Daedalus Axe")) out.add("DAEDALUS_AXE");
        }
    }

    private static boolean hasPet(ProfileFetcher.ProfileData data, String petType, int minLevel) {
        if (data == null || data.pets == null) return false;
        for (ProfileFetcher.Pet pet : data.pets) {
            if (pet != null && petType.equalsIgnoreCase(pet.type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMythologicalBestiary(ProfileFetcher.ProfileData data) {
        if (data == null || data.bestiaryKills == null) return false;
        int count = 0;
        for (String k : data.bestiaryKills.keySet()) {
            if (k.contains("minos") || k.contains("siamese") || k.contains("gaia") || k.contains("minotaur")) {
                count++;
            }
        }
        return count >= 4;
    }
}
