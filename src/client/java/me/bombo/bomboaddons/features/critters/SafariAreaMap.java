package me.bombo.bomboaddons.features.critters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class SafariAreaMap {

    private static final String RESOURCE = "/assets/bomboaddons/safari_areas.txt";
    private static final double MAX_NODE_DISTANCE = 40.0;

    private static int[] xs;
    private static int[] ys;
    private static int[] zs;
    private static byte[] areas;
    private static boolean loaded;

    private SafariAreaMap() {}

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;

        try (InputStream in = SafariAreaMap.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                System.err.println("[BomboAddons] Missing " + RESOURCE + "; position-based Safari biome detection disabled");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                var lines = reader.lines().filter(l -> !l.isBlank()).toList();
                xs = new int[lines.size()];
                ys = new int[lines.size()];
                zs = new int[lines.size()];
                areas = new byte[lines.size()];
                for (int i = 0; i < lines.size(); i++) {
                    String[] parts = lines.get(i).split(" ");
                    xs[i] = Integer.parseInt(parts[0]);
                    ys[i] = Integer.parseInt(parts[1]);
                    zs[i] = Integer.parseInt(parts[2]);
                    areas[i] = Byte.parseByte(parts[3]);
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[BomboAddons] Could not read " + RESOURCE + ": " + e.getMessage());
            xs = null;
        }
    }

    public static SafariBiome biomeAt(double x, double y, double z) {
        load();
        if (xs == null || xs.length == 0) return null;

        int best = -1;
        double bestDistanceSq = Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            double dx = x - xs[i];
            double dy = y - ys[i];
            double dz = z - zs[i];
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = i;
            }
        }

        if (best < 0 || bestDistanceSq > MAX_NODE_DISTANCE * MAX_NODE_DISTANCE) return null;
        return fromIndex(areas[best]);
    }

    private static SafariBiome fromIndex(byte index) {
        return switch (index) {
            case 1 -> SafariBiome.FOREST;
            case 2 -> SafariBiome.CAVERN;
            case 3 -> SafariBiome.ICY;
            case 4 -> SafariBiome.HAUNTED;
            default -> null;
        };
    }
}
