package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class DiceHud {
   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "dice_hud"), DiceHud::render);
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.diceTracker) {
         Minecraft client = Minecraft.getInstance();
         if (!client.options.hideGui) {
            if (client.screen == null) {
               if (DiceTracker.shouldShowHud()) {
                  drawDiceInfo(g, s.diceHudX, s.diceHudY, false);
               }

            }
         }
      }
   }

   public static void drawDiceInfo(GuiGraphicsExtractor g, int x, int y, boolean isHovered) {
      BomboConfig.Settings s = BomboConfig.get();
      boolean isCurrent = "Current".equalsIgnoreCase(s.diceDisplayMode);
      DiceTracker.Stats stats = isCurrent ? DiceTracker.getSessionStats() : DiceTracker.getStats();
      long profit = stats.totalEarned - stats.totalSpent;
      String profitStr = (profit >= 0L ? "§a+" : "§c") + formatCoins(profit) + " coins";
      int normalRolls = 0;

      for(int count : stats.normalRolls.values()) {
         normalRolls += count;
      }

      int highRolls = 0;

      for(int count : stats.highClassRolls.values()) {
         highRolls += count;
      }

      long totalRollCosts = (long)normalRolls * 666666L + (long)highRolls * 6666666L;
      long totalDiceCosts = stats.totalSpent - totalRollCosts;
      List<String> lines = new ArrayList();
      lines.add(isCurrent ? "§6§lDice Tracker (Current)" : "§6§lDice Tracker (Lifetime)");
      lines.add("§fRolls: §d" + normalRolls + " §6" + highRolls);
      long var10001 = stats.normalDicesUsed;
      lines.add("§fDices: §d" + var10001 + " §6" + stats.highClassDicesUsed + " §7(" + formatCoins(totalDiceCosts) + ")");
      lines.add("§fProfit: " + profitStr);
      lines.add("§fDisplay Mode: §b[" + (isCurrent ? "Current" : "Lifetime") + "]");
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = BomboConfig.get().diceHudScale;
      g.pose().scale(scale, scale);
      int curY = 0;
      int maxWidth = 0;

      for(String line : lines) {
         g.text(Minecraft.getInstance().font, line, 0, curY, -1, true);
         maxWidth = Math.max(maxWidth, Minecraft.getInstance().font.width(line));
         curY += 10;
      }

      g.pose().popMatrix();
      if (isHovered) {
         List<String> tooltip = new ArrayList();
         tooltip.add("§6§lRoll Breakdown:");

         for(int i = 1; i <= 7; ++i) {
            String k = String.valueOf(i);
            int n = (Integer)stats.normalRolls.getOrDefault(k, 0);
            int h = (Integer)stats.highClassRolls.getOrDefault(k, 0);
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

      for(String line : lore) {
         width = Math.max(width, Minecraft.getInstance().font.width(line));
      }

      width += 8;
      int height = lore.size() * 10 + 4;
      g.fill(tX - 4, y - 4, tX + width, y + height, -15198184);
      g.fill(tX - 5, y - 5, tX - 4, y + height + 1, -11184811);
      g.fill(tX + width, y - 5, tX + width + 1, y + height + 1, -11184811);
      g.fill(tX - 5, y - 5, tX + width + 1, y - 4, -11184811);
      g.fill(tX - 5, y + height, tX + width + 1, y + height + 1, -11184811);

      for(int i = 0; i < lore.size(); ++i) {
         g.text(Minecraft.getInstance().font, (String)lore.get(i), tX, tY + i * 10, -1, true);
      }

   }

   public static void showStatsInChat() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         DiceTracker.Stats total = DiceTracker.getStats();
         DiceTracker.Stats session = DiceTracker.getSessionStats();
         int sessionNormal = 0;

         for(int count : session.normalRolls.values()) {
            sessionNormal += count;
         }

         int sessionHigh = 0;

         for(int count : session.highClassRolls.values()) {
            sessionHigh += count;
         }

         long sessionProfit = session.totalEarned - session.totalSpent;
         long sessionRollCosts = (long)sessionNormal * 666666L + (long)sessionHigh * 6666666L;
         long sessionDiceCosts = session.totalSpent - sessionRollCosts;
         int totalNormal = 0;

         for(int count : total.normalRolls.values()) {
            totalNormal += count;
         }

         int totalHigh = 0;

         for(int count : total.highClassRolls.values()) {
            totalHigh += count;
         }

         long totalProfit = total.totalEarned - total.totalSpent;
         long totalRollCosts = (long)totalNormal * 666666L + (long)totalHigh * 6666666L;
         long totalDiceCosts = total.totalSpent - totalRollCosts;
         mc.player.sendSystemMessage(Component.literal("§8---------------------------------------------------------"));
         mc.player.sendSystemMessage(Component.literal("§6§lDice Tracker Statistics:"));
         mc.player.sendSystemMessage(Component.literal("§e--- Current Session ---"));
         mc.player.sendSystemMessage(Component.literal("  §fRolls: §d" + sessionNormal + " §6" + sessionHigh));
         long var10001 = session.normalDicesUsed;
         mc.player.sendSystemMessage(Component.literal("  §fDices Used: §d" + var10001 + " §6" + session.highClassDicesUsed + " §7(" + formatCoins(sessionDiceCosts) + ")"));
         mc.player.sendSystemMessage(Component.literal("  §fProfit: " + (sessionProfit >= 0L ? "§a+" : "§c") + formatCoins(sessionProfit) + " coins"));
         mc.player.sendSystemMessage(Component.literal("§e--- All-Time (Total) ---"));
         mc.player.sendSystemMessage(Component.literal("  §fRolls: §d" + totalNormal + " §6" + totalHigh));
         var10001 = total.normalDicesUsed;
         mc.player.sendSystemMessage(Component.literal("  §fDices Used: §d" + var10001 + " §6" + total.highClassDicesUsed + " §7(" + formatCoins(totalDiceCosts) + ")"));
         mc.player.sendSystemMessage(Component.literal("  §fProfit: " + (totalProfit >= 0L ? "§a+" : "§c") + formatCoins(totalProfit) + " coins"));
         mc.player.sendSystemMessage(Component.literal("§8---------------------------------------------------------"));
      }
   }

   public static String formatCoins(long coins) {
      long abs = Math.abs(coins);
      if (abs >= 1000000000L) {
         return String.format("%.2fB", (double)coins / (double)1.0E9F);
      } else if (abs >= 1000000L) {
         return String.format("%.1fM", (double)coins / (double)1000000.0F);
      } else {
         return abs >= 1000L ? String.format("%.1fK", (double)coins / (double)1000.0F) : String.valueOf(coins);
      }
   }
}
