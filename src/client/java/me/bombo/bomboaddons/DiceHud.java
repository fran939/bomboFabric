package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;

public class DiceHud {
    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "dice_hud"), DiceHud::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.diceTracker) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.gui.hud.isHidden()) return;
        if (client.gui.screen() != null) return;

        if (DiceTracker.shouldShowHud()) {
            drawDiceInfo(g, s.diceHudX, s.diceHudY, false);
        }
    }

    public static void drawDiceInfo(GuiGraphicsExtractor g, int x, int y, boolean isHovered) {
        DiceTracker.Stats stats = DiceTracker.getStats();
        long profit = stats.totalEarned - stats.totalSpent;
        String profitStr = (profit >= 0 ? "§a+" : "§c") + formatCoins(profit) + " coins";
        
        int normalRolls = 0;
        for (int count : stats.normalRolls.values()) normalRolls += count;
        int highRolls = 0;
        for (int count : stats.highClassRolls.values()) highRolls += count;
        
        long totalRollCosts = (normalRolls * 666666L) + (highRolls * 6666666L);
        long totalDiceCosts = stats.totalSpent - totalRollCosts;

        List<String> lines = new ArrayList<>();
        lines.add("§6§lDice Tracker");
        lines.add("§fRolls: §d" + normalRolls + " §6" + highRolls);
        lines.add("§fDices: §d" + stats.normalDicesUsed + " §6" + stats.highClassDicesUsed + " §7(" + formatCoins(totalDiceCosts) + ")");
        lines.add("§fProfit: " + profitStr);

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = BomboConfig.get().diceHudScale;
        g.pose().scale(scale, scale);

        int curY = 0;
        int maxWidth = 0;
        for (String line : lines) {
            g.text(Minecraft.getInstance().font, line, 0, curY, 0xFFFFFFFF, true);
            maxWidth = Math.max(maxWidth, Minecraft.getInstance().font.width(line));
            curY += 10;
        }

        g.pose().popMatrix();

        if (isHovered) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add("§6§lRoll Breakdown:");
            for (int i = 1; i <= 7; i++) {
                String k = String.valueOf(i);
                int n = stats.normalRolls.getOrDefault(k, 0);
                int h = stats.highClassRolls.getOrDefault(k, 0);
                tooltip.add("§f" + i + ": §d" + n + " §6" + h);
            }
            
            tooltip.add("");
            tooltip.add("§7Financials:");
            tooltip.add("§fRoll Costs: §c-" + formatCoins(totalRollCosts));
            tooltip.add("§fDice Losses: §c-" + formatCoins(totalDiceCosts));
            tooltip.add("§fTotal Earned: §a+" + formatCoins(stats.totalEarned));
            
            drawCustomTooltip(g, tooltip, x + maxWidth + 5, y);
        }
    }

    private static void drawCustomTooltip(GuiGraphicsExtractor g, List<String> lore, int x, int y) {
        int tX = x + 12;
        int tY = y;
        int width = 125;
        int height = lore.size() * 10 + 4;
        
        g.fill(tX - 4, tY - 4, tX + width, tY + height, 0xFF181818);
        g.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, 0xFF555555);
        g.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, 0xFF555555);
        g.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, 0xFF555555);
        g.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, 0xFF555555);
        
        for (int i = 0; i < lore.size(); i++) {
            g.text(Minecraft.getInstance().font, lore.get(i), tX, tY + i * 10, 0xFFFFFFFF, true);
        }
    }

    private static String formatCoins(long coins) {
        long abs = Math.abs(coins);
        if (abs >= 1000000000) return String.format("%.2fB", (double)coins / 1e9);
        if (abs >= 1000000) return String.format("%.1fM", (double)coins / 1e6);
        if (abs >= 1000) return String.format("%.1fK", (double)coins / 1e3);
        return String.valueOf(coins);
    }
}
