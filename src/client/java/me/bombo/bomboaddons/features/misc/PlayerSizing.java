package me.bombo.bomboaddons.features.misc;

import me.bombo.bomboaddons.BomboConfig;

/**
 * Handles custom player model sizing and nametag scaling.
 */
public class PlayerSizing {

    public static boolean shouldScalePlayer() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.scalePlayers;
    }

    public static float getPlayerScale() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || s.playerSize < 0.05F) return 1.0F;
        return s.playerSize;
    }

    public static boolean shouldScaleNameTag() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.scalePlayers && s.scaleNameTag;
    }

    public static boolean shouldIgnoreNPC() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.playerIgnoreNPC;
    }
}
