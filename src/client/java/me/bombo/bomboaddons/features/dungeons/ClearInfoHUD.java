package me.bombo.bomboaddons.features.dungeons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;

public class ClearInfoHUD {
    public static void onChatMessage(String message) {
        BomboConfig.Settings settings = BomboConfig.get();
        if (!settings.clearInfoHud) return;
        
        if (message.contains("Team Score:")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            
            String clearText = "Clear: 0%";
            String secretsText = "Secrets Found: 0";
            
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
            if (objective != null) {
                List<String> sbLines = SkyblockUtils.getSidebarLines(scoreboard, objective);
                for (String line : sbLines) {
                    String clean = net.minecraft.ChatFormatting.stripFormatting(line);
                    if (clean == null) continue;
                    
                    if (clean.contains("Dungeon Cleared:") || clean.contains("Clear:")) {
                        clearText = clean.trim();
                    } else if (clean.contains("Secrets Found:") || clean.contains("Secrets:")) {
                        secretsText = clean.trim();
                    }
                }
            }
            
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §a" + clearText + " §8| §b" + secretsText));
        }
    }
}
