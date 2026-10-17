package me.bombo.bomboaddons.features.misc;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.world.entity.Entity;

/**
 * Visual Entity Replacement framework:
 * Allows replacing entity appearances client-side.
 */
public class EntityReplace {

    public static boolean isEnabled() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.entityReplaceEnabled;
    }

    public static boolean shouldIgnoreNPC() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.entityReplaceIgnoreNPC;
    }

    public static boolean shouldReplace(Entity entity) {
        if (!isEnabled()) return false;
        if (entity == null) return false;
        return false;
    }
}
