package me.bombo.bomboaddons.features.critters;

import java.util.Locale;

public record Critter(String name, SafariBiome biome, Rarity rarity, int spawnQuota) {

    public Critter(String name, SafariBiome biome, Rarity rarity) {
        this(name, biome, rarity, 0);
    }

    public boolean hasQuota() {
        return spawnQuota > 0;
    }

    public String bazaarId() {
        return "SHARD_" + name.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public enum Rarity {
        COMMON(0xFFFFFF),
        UNCOMMON(0x55FF55),
        RARE(0x5555FF),
        EPIC(0xAA00AA),
        LEGENDARY(0xFFAA00);

        private final int colour;

        Rarity(int colour) {
            this.colour = colour;
        }

        public int colour() {
            return colour;
        }
    }
}
