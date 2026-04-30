package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class CarnivalAuto {

    public static void onChatMessage(String rawMessage) {
        if (!BomboConfig.get().autoAcceptCarnival) return;

        // Pirate
        if (rawMessage.contains("[Aye sure do!]")) {
            sendCommand("selectnpcoption carnival_pirateman r_2_1");
        } 
        // Cowboy
        else if (rawMessage.contains("[Sure thing, partner!]")) {
            sendCommand("selectnpcoption carnival_cowboy r_2_1");
        } 
        // Fisherman
        else if (rawMessage.contains("[You guessed it!]")) {
            sendCommand("selectnpcoption carnival_fisherman r_2_1");
        }
    }

    private static void sendCommand(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(cmd);
        }
    }
}
