package me.bombo.bomboaddons.features.diana;

import java.util.Locale;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class DianaLootshare {

    private static String pendingDugMob = null;
    private static long pendingMobTime = 0L;
    private static final java.util.Set<Integer> hitEntityIds = new java.util.HashSet<>();

    public static void onPlayerAttackEntity(net.minecraft.world.entity.Entity target) {
        if (target == null) return;
        hitEntityIds.add(target.getId());

        String name = target.getName().getString().toLowerCase(Locale.ROOT);
        String customName = target.getCustomName() != null ? target.getCustomName().getString().toLowerCase(Locale.ROOT) : "";
        String combined = name + " " + customName;

        if (pendingDugMob != null && (System.currentTimeMillis() - pendingMobTime < 120000L)) {
            String check = pendingDugMob.toLowerCase(Locale.ROOT);
            if (combined.contains(check) || check.contains(combined) || combined.contains("minotaur") || combined.contains("gaia") || combined.contains("inquisitor") || combined.contains("champion") || combined.contains("lynx")) {
                triggerLootshareAlert(pendingDugMob);
                pendingDugMob = null;
            }
        } else {
            String detectedMob = matchMobType(combined);
            if (detectedMob != null) {
                triggerLootshareAlert(detectedMob);
            }
        }
    }

    private static String matchMobType(String text) {
        BomboConfig.Settings s = BomboConfig.get();
        if (text.contains("inquisitor") || text.contains("minos inquisitor")) {
            if (s.dianaLsInquisitor) return "Minos Inquisitor";
        } else if (text.contains("champion") || text.contains("minos champion")) {
            if (s.dianaLsChampion) return "Minos Champion";
        } else if (text.contains("gaia") || text.contains("gaia construct")) {
            if (s.dianaLsGaia) return "Gaia Construct";
        } else if (text.contains("minotaur")) {
            if (s.dianaLsMinotaur) return "Minotaur";
        } else if (text.contains("siamese") || text.contains("siamese lynx")) {
            if (s.dianaLsSiamese) return "Siamese Lynx";
        }
        return null;
    }

    public static void onChatMessage(String cleanMessage) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.dianaLootshareEnabled) return;
        if (cleanMessage == null || cleanMessage.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String lower = cleanMessage.toLowerCase(Locale.ROOT);
        String detectedMob = matchMobType(lower);
        if (detectedMob == null) return;

        boolean isSpawn = lower.contains("dug out") || lower.contains("spawned") || lower.contains("dug up") || lower.contains("yikes") || lower.contains("woah") || lower.contains("oh!");
        boolean isDamaged = lower.contains("damaged") || lower.contains("loot share") || lower.contains("lootshare");

        if (isDamaged) {
            triggerLootshareAlert(detectedMob);
            pendingDugMob = null;
        } else if (isSpawn) {
            pendingDugMob = detectedMob;
            pendingMobTime = System.currentTimeMillis();
        }
    }

    public static void triggerLootshareAlert(String detectedMob) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.dianaLootshareEnabled || detectedMob == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. User Chat Message
        if (s.dianaLsUserMessage && s.dianaLsUserMessageText != null && !s.dianaLsUserMessageText.trim().isEmpty()) {
            String msg = s.dianaLsUserMessageText
                    .replace("{mob}", detectedMob)
                    .replace("&", "§");
            if (mc.gui != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
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
                mc.gui.hud.setTitle(Component.literal(title));
            }
        }

        // 4. Sound alert
        if (s.dianaLsSound) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.5F);
        }
    }
}
