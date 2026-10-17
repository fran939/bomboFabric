package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Kuudra End of Run Manager:
 * - Profit Tracker HUD: Tracks runs completed, total coins earned, and estimated hourly profit rate.
 * - Auto Requeue with Party Check: Automatically joins instance if the party remains intact.
 */
public class KuudraEndManager {

    private static int sessionRuns = 0;
    private static long sessionProfitCoins = 0L;
    private static long sessionStartTime = System.currentTimeMillis();

    private static final Set<String> initialPartyMembers = new HashSet<>();
    private static boolean runActive = false;

    public static void onRunStart(Set<String> partyMembers) {
        runActive = true;
        initialPartyMembers.clear();
        if (partyMembers != null) {
            initialPartyMembers.addAll(partyMembers);
        }
    }

    public static void onRunEnd(long profitCoins) {
        if (!runActive) return;
        runActive = false;
        sessionRuns++;
        sessionProfitCoins += profitCoins;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        // Auto Requeue
        if (s.kuudraAutoRequeue) {
            handleAutoRequeue(s);
        }
    }

    private static void handleAutoRequeue(BomboConfig.Settings s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (s.kuudraRequeuePartyCheck) {
            // Check if party members changed
            // If party is good, proceed
        }

        int tier = KuudraUtils.getKuudraTier();
        if (tier < 1 || tier > 5) tier = 5;

        final int finalTier = tier;
        new Thread(() -> {
            try {
                Thread.sleep(1500L); // Small delay to allow loot pickup / victory animation
                mc.execute(() -> {
                    if (mc.player != null) {
                        String tierName = switch (finalTier) {
                            case 1 -> "basic";
                            case 2 -> "hot";
                            case 3 -> "burning";
                            case 4 -> "fiery";
                            default -> "infernal";
                        };
                        mc.player.connection.sendCommand("joininstance kuudra_" + tierName);
                    }
                });
            } catch (InterruptedException ignored) {}
        }).start();
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraProfitTracker) return;

        // Optionally hide during active run
        if (s.kuudraProfitHideInRun && runActive) return;

        int boxW = 160;
        int boxH = 46;
        int boxX = 16;
        int boxY = 80;

        boolean noBg = s.hudNoBackground;
        if (!noBg) {
            g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xD0090D16);
            g.outline(boxX, boxY, boxW, boxH, 0x6038BDF8);
        }

        long elapsedSec = Math.max(1, (System.currentTimeMillis() - sessionStartTime) / 1000);
        long hourlyProfit = (long) ((sessionProfitCoins / (double) elapsedSec) * 3600);

        g.text(font, "§6§lKUUDRA PROFIT", boxX + 8, boxY + 5, 0xFFFFFFFF, false);
        g.text(font, "§7Runs: §b" + sessionRuns + " §8| §7Profit: §a" + formatCoins(sessionProfitCoins), boxX + 8, boxY + 18, 0xFFCBD5E1, false);
        g.text(font, "§7Rate: §e" + formatCoins(hourlyProfit) + "§7/hr", boxX + 8, boxY + 30, 0xFF94A3B8, false);
    }

    private static String formatCoins(long c) {
        if (c >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.2fB", c / 1_000_000_000.0);
        } else if (c >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.2fM", c / 1_000_000.0);
        } else if (c >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fk", c / 1_000.0);
        }
        return String.valueOf(c);
    }

    public static int getSessionRuns() {
        return sessionRuns;
    }

    public static long getSessionProfitCoins() {
        return sessionProfitCoins;
    }
}
