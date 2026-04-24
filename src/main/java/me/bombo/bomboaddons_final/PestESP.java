package me.bombo.bomboaddons_final;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashSet;
import java.util.Set;

public class PestESP {
    private static final Set<Entity> pests = new HashSet<>();

    public static void render(WorldRenderContext context) {
        BomboConfig.Settings settings = BomboConfig.get();
        if (!settings.pestEsp) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!SkyblockUtils.isInGarden()) return;

        // Visual rendering disabled for stability in 1.21.11
        /*
        pests.clear();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand) {
                if (TargetPests.getPestName(stand) != null) {
                    pests.add(stand);
                }
            }
        }
        */
    }
    
    public static void clear() {
        pests.clear();
    }
}
