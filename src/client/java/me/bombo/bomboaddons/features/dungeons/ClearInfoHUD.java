package me.bombo.bomboaddons.features.dungeons;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class ClearInfoHUD {
   public static void onChatMessage(String message) {
      BomboConfig.Settings settings = BomboConfig.get();
      if (settings.clearInfoHud) {
         if (message.contains("Team Score:")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
               return;
            }

            String clearText = "Clear: 0%";
            String secretsText = "Secrets Found: 0";
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective != null) {
               for(String line : SkyblockUtils.getSidebarLines(scoreboard, objective)) {
                  String clean = ChatFormatting.stripFormatting(line);
                  if (clean != null) {
                     if (!clean.contains("Dungeon Cleared:") && !clean.contains("Clear:")) {
                        if (clean.contains("Secrets Found:") || clean.contains("Secrets:")) {
                           secretsText = clean.trim();
                        }
                     } else {
                        clearText = clean.trim();
                     }
                  }
               }
            }

            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §a" + clearText + " §8| §b" + secretsText));
         }

      }
   }
}
