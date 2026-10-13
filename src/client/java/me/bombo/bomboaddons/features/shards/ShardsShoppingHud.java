package me.bombo.bomboaddons.features.shards;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class ShardsShoppingHud {
    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "shards_shopping_hud"), ShardsShoppingHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.shardsShoppingHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof HudMoveScreen)) return;
        if (!ShardsShoppingManager.hasPlan()) return;

        ShardsShoppingManager.renderHud(g, s.shardsShoppingHudX, s.shardsShoppingHudY, s.shardsShoppingHudScale, false);
    }
}
