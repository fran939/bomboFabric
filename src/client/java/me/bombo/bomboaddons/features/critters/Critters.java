package me.bombo.bomboaddons.features.critters;

import java.util.*;

public final class Critters {

    private static final List<Critter> ALL = List.of(
        // --- Forest (9) ---
        new Critter("Foxtrot", SafariBiome.FOREST, Critter.Rarity.COMMON),
        new Critter("Bluebird", SafariBiome.FOREST, Critter.Rarity.UNCOMMON),
        new Critter("Honeybug", SafariBiome.FOREST, Critter.Rarity.UNCOMMON),
        new Critter("Treefrog", SafariBiome.FOREST, Critter.Rarity.UNCOMMON),
        new Critter("Woodchucker", SafariBiome.FOREST, Critter.Rarity.UNCOMMON),
        new Critter("Fluffling", SafariBiome.FOREST, Critter.Rarity.RARE),
        new Critter("Hideonfloor", SafariBiome.FOREST, Critter.Rarity.RARE),
        new Critter("Parakeet", SafariBiome.FOREST, Critter.Rarity.RARE),
        new Critter("Macaw", SafariBiome.FOREST, Critter.Rarity.LEGENDARY),

        // --- Cavern (9) ---
        new Critter("Cavernfish", SafariBiome.CAVERN, Critter.Rarity.COMMON),
        new Critter("Flitter", SafariBiome.CAVERN, Critter.Rarity.COMMON),
        new Critter("Shyworm", SafariBiome.CAVERN, Critter.Rarity.COMMON),
        new Critter("Driftling", SafariBiome.CAVERN, Critter.Rarity.UNCOMMON),
        new Critter("Chuckwalla", SafariBiome.CAVERN, Critter.Rarity.RARE),
        new Critter("Rockmite", SafariBiome.CAVERN, Critter.Rarity.RARE),
        new Critter("Scrappy", SafariBiome.CAVERN, Critter.Rarity.RARE),
        new Critter("Snoozle", SafariBiome.CAVERN, Critter.Rarity.RARE),
        new Critter("Gemzie", SafariBiome.CAVERN, Critter.Rarity.EPIC, 3),

        // --- Icy (9) ---
        new Critter("Strongarm", SafariBiome.ICY, Critter.Rarity.COMMON),
        new Critter("Tepid", SafariBiome.ICY, Critter.Rarity.COMMON),
        new Critter("Polaris", SafariBiome.ICY, Critter.Rarity.UNCOMMON),
        new Critter("Shuddersquid", SafariBiome.ICY, Critter.Rarity.UNCOMMON),
        new Critter("Billygoat", SafariBiome.ICY, Critter.Rarity.RARE),
        new Critter("Mantis Shrimp", SafariBiome.ICY, Critter.Rarity.RARE),
        new Critter("Nozzlenose", SafariBiome.ICY, Critter.Rarity.RARE),
        new Critter("Troodon", SafariBiome.ICY, Critter.Rarity.RARE, 3),
        new Critter("Wumpa", SafariBiome.ICY, Critter.Rarity.LEGENDARY, 1),

        // --- Haunted (10) ---
        new Critter("Areita", SafariBiome.HAUNTED, Critter.Rarity.UNCOMMON),
        new Critter("Bloodbat", SafariBiome.HAUNTED, Critter.Rarity.UNCOMMON),
        new Critter("Duplico", SafariBiome.HAUNTED, Critter.Rarity.UNCOMMON),
        new Critter("Gazer", SafariBiome.HAUNTED, Critter.Rarity.UNCOMMON, 4),
        new Critter("Litterbug", SafariBiome.HAUNTED, Critter.Rarity.UNCOMMON),
        new Critter("Solsnatcher", SafariBiome.HAUNTED, Critter.Rarity.UNCOMMON),
        new Critter("Gimmiegold", SafariBiome.HAUNTED, Critter.Rarity.RARE),
        new Critter("Hideonwall", SafariBiome.HAUNTED, Critter.Rarity.RARE),
        new Critter("Hideyho", SafariBiome.HAUNTED, Critter.Rarity.RARE, 1),
        new Critter("Doomspiral", SafariBiome.HAUNTED, Critter.Rarity.LEGENDARY, 1)
    );

    private static final Map<String, Critter> BY_NAME = new LinkedHashMap<>();
    private static final Map<SafariBiome, List<Critter>> BY_BIOME = new EnumMap<>(SafariBiome.class);
    private static final List<Critter> BY_NAME_LENGTH_DESC;

    static {
        for (Critter critter : ALL) {
            BY_NAME.put(critter.name(), critter);
            BY_BIOME.computeIfAbsent(critter.biome(), b -> new ArrayList<>()).add(critter);
        }
        for (SafariBiome biome : SafariBiome.values()) {
            BY_BIOME.putIfAbsent(biome, List.of());
        }

        List<Critter> byLength = new ArrayList<>(ALL);
        byLength.sort(Comparator.comparingInt((Critter c) -> c.name().length()).reversed());
        BY_NAME_LENGTH_DESC = List.copyOf(byLength);
    }

    public static final Critter MACAW = BY_NAME.get("Macaw");

    private Critters() {}

    public static List<Critter> all() {
        return ALL;
    }

    public static int total() {
        return ALL.size();
    }

    public static List<Critter> inBiome(SafariBiome biome) {
        return BY_BIOME.getOrDefault(biome, List.of());
    }

    public static int totalIn(SafariBiome biome) {
        return BY_BIOME.getOrDefault(biome, List.of()).size();
    }

    public static Critter byName(String name) {
        return BY_NAME.get(name);
    }

    public static Critter findIn(String line) {
        for (Critter critter : BY_NAME_LENGTH_DESC) {
            if (line.contains(critter.name())) return critter;
        }
        return null;
    }
}
