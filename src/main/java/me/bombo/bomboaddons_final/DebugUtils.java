package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DebugUtils {
    public static void debug(String type, String message) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.debugMaster) return;
        
        boolean shouldLog = false;
        switch(type.toLowerCase()) {
            case "chat" -> shouldLog = s.debugChat;
            case "gui" -> shouldLog = s.debugGuis;
            case "entity" -> shouldLog = s.debugEntities;
            case "command" -> shouldLog = s.debugCommands;
        }
        
        if (shouldLog) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Prevent infinite loop for chat debug
                if (type.equalsIgnoreCase("chat") && message.contains("[chat Debug]")) return;
                
                mc.player.displayClientMessage(Component.literal("§7[" + type + " Debug] " + message), false);
            }
        }
    }
}
