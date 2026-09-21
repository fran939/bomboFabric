package me.bombo.bomboaddons.features.dungeons.map;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DungeonRoom {
    public enum Type {
        ENTRANCE(0xFF16A34A, "Entrance"),
        NORMAL(0xFF93502A, "Normal"),
        PUZZLE(0xFF8B5CF6, "Puzzle"),
        TRAP(0xFFEA580C, "Trap"),
        MINIBOSS(0xFFEAB308, "Yellow"),
        FAIRY(0xFFEC4899, "Fairy"),
        BLOOD(0xFFDC2626, "Blood"),
        BOSS(0xFFB71C1C, "Boss"),
        UNOPENED(0xFF1E140C, "Unopened"),
        UNKNOWN(0xFF140D08, "Unknown");

        public final int color;
        public final String displayName;

        Type(int color, String displayName) {
            this.color = color;
            this.displayName = displayName;
        }
    }

    public enum Checkmark {
        NONE,
        GRAY,
        WHITE,
        GREEN,
        FAILED
    }

    public String name = "Unknown";
    public Type type = Type.UNOPENED;
    public Checkmark checkmark = Checkmark.NONE;
    public int maxSecrets = 0;
    public int currentSecrets = 0;
    public int crypts = 0;
    public String shape = "1x1";
    public String puzzleName = null;
    public String clearedBy = null;
    public long clearTime = 0L;
    public boolean isDiscovered = false;
    public boolean isExplored = false;

    // Grid coordinates
    public int primaryGridX = 0;
    public int primaryGridZ = 0;

    // Per-component edge connections (grid index -> flags)
    public boolean connectedNorth = false;
    public boolean connectedSouth = false;
    public boolean connectedWest = false;
    public boolean connectedEast = false;

    // Set of grid indices (0..35) that make up this room
    public final Set<Integer> components = new HashSet<>();
    public int minGridX = 0, minGridZ = 0, maxGridX = 0, maxGridZ = 0;
    public double worldMinX = 0, worldMinZ = 0, worldMaxX = 0, worldMaxZ = 0;

    public DungeonRoom(int gridX, int gridZ) {
        this.primaryGridX = gridX;
        this.primaryGridZ = gridZ;
        addComponent(gridX, gridZ);
    }

    public void addComponent(int gridX, int gridZ) {
        int idx = gridZ * 6 + gridX;
        components.add(idx);
        updateBounds();
    }

    public void removeComponent(int gridX, int gridZ) {
        components.remove(gridZ * 6 + gridX);
        updateBounds();
    }

    public void clearComponents() {
        components.clear();
        updateBounds();
    }

    public void updateBounds() {
        if (components.isEmpty()) {
            this.minGridX = primaryGridX;
            this.minGridZ = primaryGridZ;
            this.maxGridX = primaryGridX;
            this.maxGridZ = primaryGridZ;
            return;
        }
        int minX = 6, minZ = 6, maxX = -1, maxZ = -1;
        for (int idx : components) {
            int gx = idx % 6;
            int gz = idx / 6;
            if (gx < minX) minX = gx;
            if (gx > maxX) maxX = gx;
            if (gz < minZ) minZ = gz;
            if (gz > maxZ) maxZ = gz;
        }
        this.minGridX = minX;
        this.minGridZ = minZ;
        this.maxGridX = maxX;
        this.maxGridZ = maxZ;

        // Catacombs coordinate mapping:
        // Room (0,0) center is at (-185, -185) with 32 block spacing
        this.worldMinX = -201.0 + (minGridX * 32.0);
        this.worldMinZ = -201.0 + (minGridZ * 32.0);
        this.worldMaxX = -169.0 + (maxGridX * 32.0);
        this.worldMaxZ = -169.0 + (maxGridZ * 32.0);
    }

    public boolean isInside(double px, double pz) {
        return px >= worldMinX - 2.0 && px <= worldMaxX + 2.0 && pz >= worldMinZ - 2.0 && pz <= worldMaxZ + 2.0;
    }

    public boolean hasComponent(int gridX, int gridZ) {
        return components.contains(gridZ * 6 + gridX);
    }

    public double getCenterGridX() {
        if (components.isEmpty()) return primaryGridX;
        double sum = 0;
        for (int idx : components) sum += (idx % 6);
        return sum / components.size();
    }

    public double getCenterGridZ() {
        if (components.isEmpty()) return primaryGridZ;
        double sum = 0;
        for (int idx : components) sum += (idx / 6);
        return sum / components.size();
    }
}
