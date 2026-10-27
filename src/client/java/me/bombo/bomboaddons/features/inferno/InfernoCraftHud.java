package me.bombo.bomboaddons.features.inferno;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.PerformanceProfiler;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;

/**
 * On-screen HUD displaying live Inferno Fuel craft progress,
 * remaining materials to buy on Bazaar, and next recipe step.
 */
public class InfernoCraftHud {

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "inferno_craft_hud"), InfernoCraftHud::render);
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.infernoCraftHudEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyToggleGui.isDown()) return;

        boolean isMoving = mc.gui.screen() instanceof HudMoveScreen;
        if (!isMoving) {
            if (s.infernoCraftHudOnlyIsland && !SkyblockUtils.isInPrivateIsland()) return;
            if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen) && !(mc.gui.screen() instanceof AbstractContainerScreen)) {
                return;
            }
        }

        try (PerformanceProfiler.Scope p = PerformanceProfiler.scope("HUD: Inferno Fuel Craft")) {
            Font font = mc.font;
            float scale = s.infernoCraftHudScale > 0 ? s.infernoCraftHudScale : 1.0f;
            int x = s.infernoCraftHudX;
            int y = s.infernoCraftHudY;

            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().scale(scale, scale);

            drawHud(g, font, 0, 0);

            g.pose().popMatrix();
        }
    }

    public static void drawHud(GuiGraphicsExtractor g, Font font, int x, int y) {
        InfernoFuelCraftHelper.CraftPlan plan = InfernoFuelCraftHelper.computePlan();
        int w = getHudWidth();
        int h = getHudHeight();

        // Glassmorphic background and border
        g.fill(x, y, x + w, y + h, 0xB80B0F19);
        g.outline(x, y, w, h, 0xFF374151);

        // Header
        g.text(font, "§6§l✦ Fuel Craft Helper §8(" + plan.targetFuels + " Fuels)", x + 6, y + 5, 0xFFFFFFFF, true);

        // Raw materials status line
        if (plan.totalMissingRawTypes == 0) {
            g.text(font, "§a✔ All raw materials collected!", x + 6, y + 17, 0xFF10B981, false);
        } else {
            g.text(font, "§7To Buy: §e" + plan.totalMissingRawTypes + " items §7(§6" + InfernoMinionTracker.formatCoins(plan.totalEstimatedBuyCost) + "§7)", x + 6, y + 17, 0xFFFBBF24, false);
        }

        // Top missing raw material / next step
        String nextStep = getNextStepText(plan);
        g.text(font, nextStep, x + 6, y + 28, 0xFFFFFFFF, false);

        // Co-op & ready materials progress
        int totalRaw = plan.rawMaterials.size();
        g.text(font, "§7Progress: §b" + plan.totalReadyRawTypes + "/" + totalRaw + " ready §8| §e/b inferno craft", x + 6, y + 39, 0xFF9CA3AF, false);
    }

    private static String getNextStepText(InfernoFuelCraftHelper.CraftPlan plan) {
        // First check missing raw materials
        for (InfernoFuelCraftHelper.CraftItem raw : plan.rawMaterials) {
            if (raw.toBuy > 0) {
                return "§cBuy: §f" + InfernoFuelCraftHelper.formatNum(raw.toBuy) + "x " + raw.name;
            }
        }

        // If raw materials are present, check intermediate crafts
        for (InfernoFuelCraftHelper.CraftItem inter : plan.intermediates) {
            if (inter.toCraft > 0) {
                return "§eCraft: §a" + InfernoFuelCraftHelper.formatNum(inter.toCraft) + "x " + inter.name;
            }
        }

        return "§a✔ Ready to place fuels in minions!";
    }

    public static int getHudWidth() {
        return 195;
    }

    public static int getHudHeight() {
        return 52;
    }
}
