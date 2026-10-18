package me.bombo.bomboaddons.features.misc;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.kuudra.KuudraSplitsManager;
import me.bombo.bomboaddons.features.kuudra.KuudraStunKillHelper;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import me.bombo.bomboaddons.kuudra.pearls.Pearls;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class SimCommandHelper {

    public static void handleSimCommand(String messageText) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (messageText == null || messageText.trim().isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Usage: §b/b sim <message>§7 or §b/b sim title:<title>"));
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Example: §f/b sim [NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!"));
            return;
        }

        String raw = messageText.trim();
        if (raw.toLowerCase(Locale.ROOT).startsWith("title:")) {
            String titleText = raw.substring(6).trim().replace('&', '§');
            Pearls.onTitleReceived(titleText);
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.setTitle(Component.literal(titleText));
            }
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Simulated Title: §r" + titleText));
            return;
        }

        String formatted = raw.replace('&', '§');
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            if (mc.gui.hud.getChat() instanceof me.bombo.bomboaddons.util.IChatComponent chatAccessor) {
                chatAccessor.bombo$addMessage(Component.literal(formatted));
            } else {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(formatted));
            }
        }
    }

    public static void handleAreaCommand(String arg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (arg == null || arg.trim().isEmpty()) {
            String loc = SkyblockUtils.getLocation();
            String sub = SkyblockUtils.getSubArea();
            String sim = SkyblockUtils.getSimulatedArea();
            boolean inKuudra = KuudraUtils.inKuudra() || "Kuudra".equalsIgnoreCase(loc);

            if (inKuudra) {
                int tier = KuudraUtils.getKuudraTier();
                String phaseStr = (sub != null && !sub.isEmpty()) ? sub : "p1";
                String msg = "§8[§3Bombo§8] §7Current Area: §aKuudra §7(Tier: §eT" + tier + "§7, SubArea: §b" + phaseStr + "§7)";
                if (sim != null) msg += " §e[SIMULATED]";
                mc.player.sendSystemMessage(Component.literal(msg));
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Tip: Use §b/b area t5 p1§7..§bp5§7 to simulate phases."));
            } else {
                String displayArea = (loc != null ? loc : "Unknown");
                if (sub != null && !sub.isEmpty() && !sub.equalsIgnoreCase("None")) {
                    displayArea += " (" + sub + ")";
                }
                if (sim != null) {
                    displayArea += " §e[SIMULATED]";
                }
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Current Area: §a" + displayArea));
            }
            return;
        }

        String trimmed = arg.trim();
        if (trimmed.equalsIgnoreCase("clear") || trimmed.equalsIgnoreCase("reset") || trimmed.equalsIgnoreCase("off") || trimmed.equalsIgnoreCase("none")) {
            SkyblockUtils.setSimulatedArea(null);
            SkyblockUtils.setSimulatedSubArea(null);
            KuudraSplitsManager.reset();
            KuudraStunKillHelper.onStunFinished();
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Area simulation §cdisabled§7. Current real area: §a" + SkyblockUtils.getLocation()));
            return;
        }

        // Check for Kuudra tier & phase simulation (e.g. "t5 p1", "t5", "p3", "kuudra t5 p2")
        String[] parts = trimmed.split("\\s+");
        Integer kuudraTier = null;
        String kuudraPhase = null;
        boolean explicitKuudra = false;

        for (String p : parts) {
            String lower = p.toLowerCase(Locale.ROOT);
            if (lower.matches("^t[1-5]$")) {
                kuudraTier = Character.getNumericValue(lower.charAt(1));
            } else if (lower.matches("^p[1-5]$") || lower.equals("supplies") || lower.equals("build") || lower.equals("stun") || lower.equals("kill") || lower.equals("dps") || lower.equals("ended")) {
                kuudraPhase = normalizePhase(lower);
            } else if (lower.equals("kuudra")) {
                explicitKuudra = true;
            }
        }

        if (kuudraTier != null || kuudraPhase != null || explicitKuudra) {
            int finalTier = (kuudraTier != null) ? kuudraTier : 5;
            String finalPhase = (kuudraPhase != null) ? kuudraPhase : "p1";

            SkyblockUtils.setSimulatedArea("Kuudra");
            SkyblockUtils.setSimulatedSubArea(finalPhase);
            BomboaddonsClient.currentArea = "Kuudra";
            BomboaddonsClient.currentSubArea = finalPhase;

            BomboConfig.Settings s = BomboConfig.get();
            if (s != null) {
                s.kuudraTiers = finalTier;
            }

            // Trigger Kuudra phase state machines
            switch (finalPhase) {
                case "p1" -> {
                    KuudraSplitsManager.onRunStart();
                    KuudraStunKillHelper.onRunStart();
                }
                case "p2" -> {
                    KuudraSplitsManager.onSupplyPlaced(6, mc.player.getName().getString());
                    KuudraStunKillHelper.onRunStart();
                }
                case "p3" -> {
                    KuudraSplitsManager.onBuildComplete();
                    KuudraStunKillHelper.onBallistaReady();
                }
                case "p4" -> {
                    KuudraSplitsManager.onKuudraStunned();
                    KuudraStunKillHelper.onStunFinished();
                }
                case "p5" -> {
                    KuudraSplitsManager.onKuudraDefeated();
                    KuudraStunKillHelper.onStunFinished();
                }
            }

            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Area forced to: §aKuudra §7(Tier: §eT" + finalTier + "§7, SubArea: §b" + finalPhase + "§7) §8(use '§b/b area reset§8' to restore)"));
            return;
        }

        // General island simulation (e.g. "The Garden", "Hub", "Crimson Isle")
        SkyblockUtils.setSimulatedArea(trimmed);
        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Area forced to: §a" + trimmed + " §8(use '§b/b area reset§8' to restore auto-detection)"));
    }

    private static String normalizePhase(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "p1", "supplies" -> "p1";
            case "p2", "build" -> "p2";
            case "p3", "stun" -> "p3";
            case "p4", "kill", "dps" -> "p4";
            case "p5", "ended" -> "p5";
            default -> raw;
        };
    }
}
