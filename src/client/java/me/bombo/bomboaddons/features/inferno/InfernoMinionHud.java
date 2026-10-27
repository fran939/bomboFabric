package me.bombo.bomboaddons.features.inferno;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.PerformanceProfiler;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class InfernoMinionHud {

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "inferno_minion_hud"), InfernoMinionHud::render);
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.infernoHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyToggleGui.isDown()) return;

        boolean isMoving = mc.gui.screen() instanceof HudMoveScreen;
        if (!isMoving) {
            if (s.infernoHudOnlyIsland && !SkyblockUtils.isInPrivateIsland()) return;
            if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen) && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
                return;
            }
        }

        try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HUD: Inferno Minion")) {
            Font font = mc.font;
            float scale = s.infernoHudScale > 0 ? s.infernoHudScale : 1.0f;
            int x = s.infernoHudX;
            int y = s.infernoHudY;

            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().scale(scale, scale);

            drawHud(g, font, 0, 0);

            g.pose().popMatrix();
        }
    }

    public static void drawHud(GuiGraphicsExtractor g, Font font, int x, int y) {
        InfernoMinionTracker.TrackerState state = InfernoMinionTracker.getState();
        int w = getHudWidth();
        int h = getHudHeight();

        // Background & border
        g.fill(x, y, x + w, y + h, 0xB00B0F19);
        g.outline(x, y, w, h, 0xFF374151);

        // Header
        g.text(font, "§6§l🔥 Inferno Minions", x + 6, y + 5, 0xFFFFFFFF, true);

        // Net profit line
        long net = state.netProfit;
        String netStr = (net >= 0 ? "§a+" : "§c") + InfernoMinionTracker.formatCoins(net) + " coins";
        g.text(font, "§7Net: " + netStr, x + 6, y + 17, 0xFFFFFFFF, false);

        // Revenue & Fuel line
        long rev = state.totalRevenue;
        long cost = state.totalCost;
        long fuels = state.fuels.getOrDefault(InfernoMinionTracker.HYPERGOLIC_FUEL_ID, 0L);
        g.text(font, "§eRev: §6+" + InfernoMinionTracker.formatCoins(rev) + " §7| §cCost: -" + InfernoMinionTracker.formatCoins(cost), x + 6, y + 28, 0xFFFFFFFF, false);

        // Fuels used & Coop status
        String passkey = InfernoMinionTracker.getPasskey();
        String passDisplay = passkey.length() > 12 ? passkey.substring(0, 10) + ".." : passkey;
        g.text(font, "§7Fuels: §b" + fuels + "x §8| §7Coop: §e" + passDisplay, x + 6, y + 39, 0xFFFFFFFF, false);
    }

    public static int getHudWidth() {
        return 175;
    }

    public static int getHudHeight() {
        return 52;
    }
}
