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
        if (client.options.hideGui) return;
        if (client.screen != null) return;

        if (DiceTracker.shouldShowHud()) {
            drawDiceInfo(g, s.diceHudX, s.diceHudY, false);
        }
    }

    public static void drawDiceInfo(GuiGraphicsExtractor g, int x, int y, boolean isHovered) {
        BomboConfig.Settings s = BomboConfig.get();
        boolean isCurrent = "Current".equalsIgnoreCase(s.diceDisplayMode);
        DiceTracker.Stats stats = isCurrent ? DiceTracker.getSessionStats() : DiceTracker.getStats();

        long profit = stats.totalEarned - stats.totalSpent;
        String profitStr = (profit >= 0 ? "§a+" : "§c") + formatCoins(profit) + " coins";
        
        int normalRolls = 0;
        for (int count : stats.normalRolls.values()) normalRolls += count;
        int highRolls = 0;
        for (int count : stats.highClassRolls.values()) highRolls += count;
        
        long totalRollCosts = (normalRolls * 666666L) + (highRolls * 6666666L);
        long totalDiceCosts = stats.totalSpent - totalRollCosts;

        List<String> lines = new ArrayList<>();
        lines.add(isCurrent ? "§6§lDice Tracker (Current)" : "§6§lDice Tracker (Lifetime)");
        lines.add("§fRolls: §d" + normalRolls + " §6" + highRolls);
        lines.add("§fDices: §d" + stats.normalDicesUsed + " §6" + stats.highClassDicesUsed + " §7(" + formatCoins(totalDiceCosts) + ")");
        lines.add("§fProfit: " + profitStr);
        lines.add("§fDisplay Mode: §b[" + (isCurrent ? "Current" : "Lifetime") + "]");

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
            tooltip.add("");
            tooltip.add("§bClick to switch Display Mode!");
            
            drawCustomTooltip(g, tooltip, x + maxWidth + 5, y);
        }
    }

    private static void drawCustomTooltip(GuiGraphicsExtractor g, List<String> lore, int x, int y) {
        int tX = x + 12;
        int tY = y;
        int width = 0;
        for (String line : lore) {
            width = Math.max(width, Minecraft.getInstance().font.width(line));
        }
        width += 8;
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

    public static void showStatsInChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        DiceTracker.Stats total = DiceTracker.getStats();
        DiceTracker.Stats session = DiceTracker.getSessionStats();

        int sessionNormal = 0;
        for (int count : session.normalRolls.values()) sessionNormal += count;
        int sessionHigh = 0;
        for (int count : session.highClassRolls.values()) sessionHigh += count;
        long sessionProfit = session.totalEarned - session.totalSpent;
        long sessionRollCosts = (sessionNormal * 666666L) + (sessionHigh * 6666666L);
        long sessionDiceCosts = session.totalSpent - sessionRollCosts;

        int totalNormal = 0;
        for (int count : total.normalRolls.values()) totalNormal += count;
        int totalHigh = 0;
        for (int count : total.highClassRolls.values()) totalHigh += count;
        long totalProfit = total.totalEarned - total.totalSpent;
        long totalRollCosts = (totalNormal * 666666L) + (totalHigh * 6666666L);
        long totalDiceCosts = total.totalSpent - totalRollCosts;

        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8---------------------------------------------------------"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6§lDice Tracker Statistics:"));
        
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e--- Current Session ---"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §fRolls: §d" + sessionNormal + " §6" + sessionHigh));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §fDices Used: §d" + session.normalDicesUsed + " §6" + session.highClassDicesUsed + " §7(" + formatCoins(sessionDiceCosts) + ")"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §fProfit: " + (sessionProfit >= 0 ? "§a+" : "§c") + formatCoins(sessionProfit) + " coins"));

        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e--- All-Time (Total) ---"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §fRolls: §d" + totalNormal + " §6" + totalHigh));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §fDices Used: §d" + total.normalDicesUsed + " §6" + total.highClassDicesUsed + " §7(" + formatCoins(totalDiceCosts) + ")"));
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("  §fProfit: " + (totalProfit >= 0 ? "§a+" : "§c") + formatCoins(totalProfit) + " coins"));

        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8---------------------------------------------------------"));
    }

    public static String formatCoins(long coins) {
        long abs = Math.abs(coins);
        if (abs >= 1000000000) return String.format("%.2fB", (double)coins / 1e9);
        if (abs >= 1000000) return String.format("%.1fM", (double)coins / 1e6);
        if (abs >= 1000) return String.format("%.1fK", (double)coins / 1e3);
        return String.valueOf(coins);
    }
}
