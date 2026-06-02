package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ParticleTracker {

    public static class ParticleEntry {
        public final String type;
        public final double x, y, z;
        public final long timestamp;

        public ParticleEntry(String type, double x, double y, double z) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /** Rolling window of recent particle events (thread-safe for packet thread writes). */
    private static final Collection<ParticleEntry> ENTRIES = new ConcurrentLinkedQueue<>();

    /** How long to remember particles (ms). */
    private static final long WINDOW_MS = 5_000;

    /** Default nearby radius in blocks. */
    public static double espRadius = 32.0;

    /** Whether the particle ESP is enabled. */
    public static boolean espEnabled = false;

    // -----------------------------------------------------------------------
    // Called from ParticleMixin (packet thread) — lightweight, lock-free
    // -----------------------------------------------------------------------

    public static void onParticle(String typeName, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        double dx = x - player.getX();
        double dy = y - player.getY();
        double dz = z - player.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        double maxR = Math.max(espRadius, 32.0); // always collect within at least 32 blocks
        if (distSq > maxR * maxR) return;

        ENTRIES.add(new ParticleEntry(cleanTypeName(typeName), x, y, z));
    }

    // -----------------------------------------------------------------------
    // Called from tick (client thread) — evict stale entries
    // -----------------------------------------------------------------------

    public static void onTick() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        ENTRIES.removeIf(e -> e.timestamp < cutoff);
    }

    // -----------------------------------------------------------------------
    // Query API
    // -----------------------------------------------------------------------

    /**
     * Returns a map of particleType → count, sorted descending, for particles
     * within {@code radius} blocks of the player in the last 5 seconds.
     */
    public static Map<String, Integer> getSummary(double radius) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return Collections.emptyMap();

        Map<String, Integer> counts = new LinkedHashMap<>();
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        double rSq = radius * radius;

        for (ParticleEntry e : ENTRIES) {
            if (e.timestamp < cutoff) continue;
            double dx = e.x - player.getX();
            double dy = e.y - player.getY();
            double dz = e.z - player.getZ();
            if (dx * dx + dy * dy + dz * dz > rSq) continue;
            counts.merge(e.type, 1, Integer::sum);
        }

        // Sort by count descending
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : sorted) result.put(entry.getKey(), entry.getValue());
        return result;
    }

    /**
     * Returns all recent entries within espRadius for the ESP renderer.
     * Can optionally filter by type substring if filterType is non-null.
     */
    public static List<ParticleEntry> getEspPoints(String filterType) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return Collections.emptyList();

        List<ParticleEntry> result = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        double rSq = espRadius * espRadius;

        for (ParticleEntry e : ENTRIES) {
            if (e.timestamp < cutoff) continue;
            double dx = e.x - player.getX();
            double dy = e.y - player.getY();
            double dz = e.z - player.getZ();
            if (dx * dx + dy * dy + dz * dz > rSq) continue;
            if (filterType != null && !e.type.toLowerCase().contains(filterType.toLowerCase())) continue;
            result.add(e);
        }
        return result;
    }

    /** Clears all tracked particles. */
    public static void clear() {
        ENTRIES.clear();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a fully-qualified particle type string such as
     * "minecraft:flame" or "net.minecraft.core.particles.SimpleParticleType@abc"
     * into a readable short name like "FLAME".
     */
    private static String cleanTypeName(String raw) {
        if (raw == null) return "UNKNOWN";
        // Strip "minecraft:" namespace
        int colonIdx = raw.lastIndexOf(':');
        if (colonIdx >= 0) raw = raw.substring(colonIdx + 1);
        // Strip class path if toString gave us a class name
        int dotIdx = raw.lastIndexOf('.');
        if (dotIdx >= 0) raw = raw.substring(dotIdx + 1);
        // Strip "@hash" suffix from toString
        int atIdx = raw.indexOf('@');
        if (atIdx >= 0) raw = raw.substring(0, atIdx);
        // Strip dollar signs (e.g. SimpleParticleType$1 -> SimpleParticleType)
        int dollarIdx = raw.indexOf('$');
        if (dollarIdx >= 0) raw = raw.substring(0, dollarIdx);
        return raw.toUpperCase(Locale.ROOT);
    }

    /** Returns a chat color int for a given particle type name. */
    public static int colorForType(String type) {
        if (type == null) return 0xFFFFFF;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "flame":           return 0xFF6600;
            case "smoke":
            case "large_smoke":     return 0x888888;
            case "witch":           return 0x8800FF;
            case "heart":           return 0xFF3399;
            case "crit":
            case "enchanted_hit":   return 0xFFDD00;
            case "firework":        return 0xFF44CC;
            case "explosion":
            case "explosion_emitter": return 0xFF4400;
            case "end_rod":         return 0xEEEEEE;
            case "dragon_breath":   return 0x8800AA;
            case "totem_of_undying": return 0x00FF88;
            case "splash":
            case "rain":
            case "falling_water":   return 0x4488FF;
            default:                return 0x00FFFF; // Aqua for unknown
        }
    }
}
