package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Unified HUD renderer for Kuudra notifications, boss health, build progress, and profit tracker.
 */
public class KuudraHudManager {

    public static void init() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("bomboaddons", "kuudra_hud"),
                KuudraHudManager::render
        );
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (!SkyblockUtils.isInKuudra()) return;

        Font font = mc.font;
        int winW = mc.getWindow().getGuiScaledWidth();
        int winH = mc.getWindow().getGuiScaledHeight();

        // 1. In-game Alerts & Notifications
        KuudraNotifications.renderHUD(g, font, winW, winH);

        // 2. Kuudra Boss Health & Direction
        KuudraBossTracker.renderHUD(g, font, winW, winH);

        // 3. Build Progress & Timer HUD
        KuudraBuildHelper.renderHUD(g, font, winW, winH);

        // 4. Kuudra Profit Tracker HUD
        KuudraEndManager.renderHUD(g, font, winW, winH);

        // 5. Kuudra Splits, Supply Times, Fresh Times & Pace HUD
        KuudraSplitsManager.renderHUD(g, font, winW, winH);
    }
}
