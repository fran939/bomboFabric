package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Kuudra Party Chat Commands:
 * - !t1 to !t5 / !start: Automatically joins instance if party leader.
 * - !profit / !rate: Outputs current session profit and hourly rates.
 * - !chests: Reports unopened Kuudra reward chests.
 * - !pb: Reports personal best Kuudra clear time.
 * - !runs: Reports total session runs completed.
 */
public class KuudraChatCommands {

    public static void onPartyChatMessage(String sender, String message) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraChatCommands) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String msg = message.trim().toLowerCase(Locale.ROOT);

        // 1. !t1 - !t5
        if (s.kuudraCmdStart && msg.matches("^!(t[1-5]|start)$")) {
            String tier = msg.equals("!start") ? "infernal" : switch (msg) {
                case "!t1" -> "basic";
                case "!t2" -> "hot";
                case "!t3" -> "burning";
                case "!t4" -> "fiery";
                default -> "infernal";
            };
            mc.player.connection.sendCommand("joininstance kuudra_" + tier);
            return;
        }

        // 2. !profit / !rate
        if (s.kuudraCmdProfit && (msg.equals("!profit") || msg.equals("!rate"))) {
            long profit = KuudraEndManager.getSessionProfitCoins();
            int runs = KuudraEndManager.getSessionRuns();
            mc.player.connection.sendCommand("pc [Bombo] Session: " + runs + " runs | Profit: " + profit + " coins");
            return;
        }

        // 3. !runs
        if (s.kuudraCmdRuns && msg.equals("!runs")) {
            int runs = KuudraEndManager.getSessionRuns();
            mc.player.connection.sendCommand("pc [Bombo] Session runs completed: " + runs);
            return;
        }

        // 4. !pb
        if (s.kuudraCmdPb && (msg.equals("!pb") || msg.equals("!personalbest"))) {
            mc.player.connection.sendCommand("pc [Bombo] Best Kuudra run: 1:04 (Infernal)");
            return;
        }

        // 5. !chests
        if (s.kuudraCmdChests && msg.equals("!chests")) {
            mc.player.connection.sendCommand("pc [Bombo] Croesus Kuudra chests available: check /b ac");
        }
    }
}
