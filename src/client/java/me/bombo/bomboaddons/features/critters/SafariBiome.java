package me.bombo.bomboaddons.features.critters;

public enum SafariBiome {
    FOREST("Forest", 0x55FF55, -40, 60, 0, 100),
    CAVERN("Cavern", 0xFFAA00, -150, -40, 0, 100),
    ICY("Icy", 0x55FFFF, -150, -50, -100, 0),
    HAUNTED("Haunted", 0xAA00AA, -50, 60, -100, 0);

    private final String displayName;
    private final int colour;
    public final double minX, maxX, minZ, maxZ;

    SafariBiome(String displayName, int colour, double minX, double maxX, double minZ, double maxZ) {
        this.displayName = displayName;
        this.colour = colour;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public boolean contains(double x, double z) {
        // Exclude start non-biome zone: -35 to -67 X, -15 to 17 Z
        if (x >= -67 && x <= -35 && z >= -15 && z <= 17) {
            return false;
        }
        // Exclude boat non-biome zone: -38 to -63 X, 18 to 28 Z
        if (x >= -63 && x <= -38 && z >= 18 && z <= 28) {
            return false;
        }
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public String displayName() {
        return displayName;
    }

    public String areaName() {
        return displayName + " Biome";
    }

    public int colour() {
        return colour;
    }

    public static SafariBiome fromCoordinates(double x, double z) {
        for (SafariBiome biome : values()) {
            if (biome.contains(x, z)) return biome;
        }
        return null;
    }

    public static SafariBiome fromDisplayName(String name) {
        if (name == null) return null;
        for (SafariBiome biome : values()) {
            if (biome.displayName.equalsIgnoreCase(name)) return biome;
        }
        return null;
    }

    public static SafariBiome fromAreaName(String area) {
        if (area == null) return null;
        for (SafariBiome biome : values()) {
            if (area.contains(biome.areaName()) || area.contains(biome.displayName)) return biome;
        }
        return null;
    }
}
