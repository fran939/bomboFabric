package me.bombo.bomboaddons.features.diana;

import java.util.Locale;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class DianaLootshare {

    public static void onChatMessage(String cleanMessage) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.dianaLootshareEnabled) return;
        if (cleanMessage == null || cleanMessage.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check which mob type is mentioned
        String detectedMob = null;
        String lower = cleanMessage.toLowerCase(Locale.ROOT);

        if (lower.contains("inquisitor") || lower.contains("minos inquisitor")) {
            if (s.dianaLsInquisitor) detectedMob = "Minos Inquisitor";
        } else if (lower.contains("champion") || lower.contains("minos champion")) {
            if (s.dianaLsChampion) detectedMob = "Minos Champion";
        } else if (lower.contains("gaia") || lower.contains("gaia construct")) {
            if (s.dianaLsGaia) detectedMob = "Gaia Construct";
        } else if (lower.contains("minotaur")) {
            if (s.dianaLsMinotaur) detectedMob = "Minotaur";
        } else if (lower.contains("siamese") || lower.contains("siamese lynx")) {
            if (s.dianaLsSiamese) detectedMob = "Siamese Lynx";
        }

        if (detectedMob == null) return;

        // Trigger condition: either rare mob dug up, or lootshare threshold reached
        boolean isTrigger = lower.contains("dug out") || lower.contains("spawned") || lower.contains("dug up")
                || lower.contains("loot share") || lower.contains("lootshare") || lower.contains("damaged");

        if (!isTrigger) return;

        // 1. User Chat Message
        if (s.dianaLsUserMessage && s.dianaLsUserMessageText != null && !s.dianaLsUserMessageText.trim().isEmpty()) {
            String msg = s.dianaLsUserMessageText
                    .replace("{mob}", detectedMob)
                    .replace("&", "§");
            if (mc.gui != null && mc.gui.getChat() != null) {
                mc.gui.getChat().addClientSystemMessage(Component.literal(msg));
            }
        }

        // 2. Command execution (e.g. party chat "/pc Lootshare Ready on {mob}!")
        if (s.dianaLsCommand && s.dianaLsCommandText != null && !s.dianaLsCommandText.trim().isEmpty()) {
            String cmd = s.dianaLsCommandText.replace("{mob}", detectedMob);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (mc.getConnection() != null) {
                mc.getConnection().sendCommand(cmd);
            }
        }

        // 3. Title display
        if (s.dianaLsTitle && s.dianaLsTitleText != null && !s.dianaLsTitleText.trim().isEmpty()) {
            if (mc.gui != null) {
                String title = s.dianaLsTitleText
                        .replace("{mob}", detectedMob)
                        .replace("&", "§");
                mc.gui.setTitle(Component.literal(title));
                mc.gui.setTimes(10, 50, 10);
            }
        }

        // 4. Sound alert
        if (s.dianaLsSound) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
        }
    }
}
