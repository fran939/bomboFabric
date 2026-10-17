package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Kuudra Build Helper:
 * - Announce Fresh: Automatically says "Fresh!" in party chat when the player gets fresh.
 * - Teammate Fresh Timer: Displays duration timers overhead when teammates get fresh.
 * - Build Timer HUD: Countdown until building phase unlocks.
 * - Build Progress: Tracks and renders ballista build percentage and progress.
 */
public class KuudraBuildHelper {

    private static int buildProgressPercent = 0;
    private static long buildStartTime = -1L;
    private static final Map<String, Long> teammateFreshExpiries = new HashMap<>();

    public static void onFreshTriggered() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && s.kuudraAnnounceFresh && SkyblockUtils.isInKuudra()) {
            mc.player.connection.sendCommand("pc Fresh!");
        }
    }

    public static void onTeammateFresh(String player, long durationMs) {
        teammateFreshExpiries.put(player, System.currentTimeMillis() + durationMs);
    }

    public static void updateProgress(int percent) {
        buildProgressPercent = Math.max(0, Math.min(100, percent));
        if (buildProgressPercent >= 100) {
            KuudraNotifications.notifyBuildFinished();
            KuudraSplitsManager.onBuildComplete();
        }
    }

    public static void onBuildPhaseStart() {
        buildStartTime = System.currentTimeMillis();
        buildProgressPercent = 0;
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraBuildTimer) return;
        if (!SkyblockUtils.isInKuudra()) return;

        if (KuudraSplitsManager.getCurrentPhase() != KuudraSplitsManager.Phase.BUILD) return;

        int boxW = 140;
        int boxH = 26;
        int boxX = 16;
        int boxY = screenH - 80;

        boolean noBg = s.hudNoBackground;
        if (!noBg) {
            g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xD0090D16);
            g.outline(boxX, boxY, boxW, boxH, 0x6038BDF8);
        }

        String label = "§6§lBUILD: §e" + buildProgressPercent + "%";
        g.text(font, label, boxX + 8, boxY + 5, 0xFFFFFFFF, false);

        // Mini progress bar
        int barX = boxX + 8;
        int barY = boxY + 16;
        int barW = boxW - 16;
        int barH = 4;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1E293B);
        int fillW = (int) ((buildProgressPercent / 100.0) * barW);
        g.fill(barX, barY, barX + fillW, barY + barH, 0xFFF59E0B);
    }

    public static int getBuildProgressPercent() {
        return buildProgressPercent;
    }

    public static void reset() {
        buildProgressPercent = 0;
        buildStartTime = -1L;
        teammateFreshExpiries.clear();
    }
}
