package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kuudra Splits and Pace Tracker:
 * - Monitors phase transitions (Supplies, Build, Fuel/Stun, Kill).
 * - Tracks supply placed times, fresh times, and server lag.
 * - Displays a live Liquid Glass Splits and Pace HUD.
 * - Posts a detailed split summary in chat upon Kuudra defeat.
 */
public class KuudraSplitsManager {

    public enum Phase {
        NONE("Waiting"),
        SUPPLIES("Supplies"),
        BUILD("Build"),
        STUN("Fuel / Stun"),
        KILL("Kill"),
        ENDED("Victory");

        public final String displayName;
        Phase(String displayName) {
            this.displayName = displayName;
        }
    }

    private static Phase currentPhase = Phase.NONE;
    private static long runStartTime = -1L;
    private static long suppliesEndTime = -1L;
    private static long buildEndTime = -1L;
    private static long stunEndTime = -1L;
    private static long killEndTime = -1L;

    public static class SupplyRecord {
        public final int number;
        public final String playerDisplay;
        public final long timeMs;

        public SupplyRecord(int number, String playerDisplay, long timeMs) {
            this.number = number;
            this.playerDisplay = playerDisplay;
            this.timeMs = timeMs;
        }
    }

    private static int suppliesPlaced = 0;
    private static final List<Long> supplyTimes = new ArrayList<>();
    private static final List<SupplyRecord> supplyRecords = new ArrayList<>();
    private static final List<Long> freshTimes = new ArrayList<>();

    private static long lastServerTickTime = 0L;
    private static long accumulatedServerLagMs = 0L;

    public static void onRunStart() {
        currentPhase = Phase.SUPPLIES;
        runStartTime = System.currentTimeMillis();
        suppliesEndTime = -1L;
        buildEndTime = -1L;
        stunEndTime = -1L;
        killEndTime = -1L;
        suppliesPlaced = 0;
        supplyTimes.clear();
        supplyRecords.clear();
        freshTimes.clear();
        accumulatedServerLagMs = 0L;
        lastServerTickTime = System.currentTimeMillis();
    }

    public static void onSupplyPlaced(int count) {
        onSupplyPlaced(count, "");
    }

    public static void onSupplyPlaced(int count, String playerDisplay) {
        suppliesPlaced = count;
        long elapsed = System.currentTimeMillis() - runStartTime;
        supplyTimes.add(elapsed);
        supplyRecords.add(new SupplyRecord(count, playerDisplay, elapsed));

        if (count >= 6 && currentPhase == Phase.SUPPLIES) {
            suppliesEndTime = System.currentTimeMillis();
            currentPhase = Phase.BUILD;
        }
    }

    public static void onFresh() {
        if (runStartTime > 0) {
            freshTimes.add(System.currentTimeMillis() - runStartTime);
        }
    }

    public static void onBuildComplete() {
        if (currentPhase == Phase.BUILD) {
            buildEndTime = System.currentTimeMillis();
            currentPhase = Phase.STUN;
        }
    }

    public static void onKuudraStunned() {
        if (currentPhase == Phase.STUN) {
            stunEndTime = System.currentTimeMillis();
            currentPhase = Phase.KILL;
        }
    }

    public static void onKuudraDefeated() {
        if (currentPhase != Phase.ENDED && currentPhase != Phase.NONE) {
            killEndTime = System.currentTimeMillis();
            currentPhase = Phase.ENDED;
            postDetailedOverview();
        }
    }

    public static void reset() {
        currentPhase = Phase.NONE;
        runStartTime = -1L;
        suppliesEndTime = -1L;
        buildEndTime = -1L;
        stunEndTime = -1L;
        killEndTime = -1L;
        suppliesPlaced = 0;
        supplyTimes.clear();
        freshTimes.clear();
        accumulatedServerLagMs = 0L;
    }

    public static void onServerTick() {
        if (runStartTime > 0 && currentPhase != Phase.ENDED && currentPhase != Phase.NONE) {
            long now = System.currentTimeMillis();
            if (lastServerTickTime > 0) {
                long diff = now - lastServerTickTime;
                if (diff > 60L) { // Server tick took > 50ms + 10ms tolerance
                    accumulatedServerLagMs += (diff - 50L);
                }
            }
            lastServerTickTime = now;
        }
    }

    private static void postDetailedOverview() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraShowDetailedOverview) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long totalRun = killEndTime - runStartTime;
        long p1 = suppliesEndTime > 0 ? (suppliesEndTime - runStartTime) : 0;
        long p2 = (buildEndTime > 0 && suppliesEndTime > 0) ? (buildEndTime - suppliesEndTime) : 0;
        long p3 = (stunEndTime > 0 && buildEndTime > 0) ? (stunEndTime - buildEndTime) : 0;
        long p4 = (killEndTime > 0 && stunEndTime > 0) ? (killEndTime - stunEndTime) : 0;

        mc.player.sendSystemMessage(Component.literal("§8§m-----------------------------------------------------"));
        mc.player.sendSystemMessage(Component.literal("§6§l⚡ KUUDRA RUN COMPLETE §8| §bTotal Time: §a" + formatTime(totalRun)));
        mc.player.sendSystemMessage(Component.literal("§7• Phase 1 (Supplies): §e" + formatTime(p1) + " §8(6/6 supplies)"));
        mc.player.sendSystemMessage(Component.literal("§7• Phase 2 (Build): §e" + formatTime(p2) + " §8(Ballista built)"));
        mc.player.sendSystemMessage(Component.literal("§7• Phase 3 (Fuel/Stun): §e" + formatTime(p3)));
        mc.player.sendSystemMessage(Component.literal("§7• Phase 4 (Kill): §e" + formatTime(p4)));

        if (s.kuudraShowLag && accumulatedServerLagMs > 0) {
            mc.player.sendSystemMessage(Component.literal("§7• Server Lag: §c" + String.format(Locale.ROOT, "%.2fs", accumulatedServerLagMs / 1000.0)));
        }
        if (s.kuudraFreshTimes && !freshTimes.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§7• Fresh Times (" + freshTimes.size() + "): §d" + formatFreshList()));
        }
        mc.player.sendSystemMessage(Component.literal("§8§m-----------------------------------------------------"));
    }

    private static String formatFreshList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < freshTimes.size(); i++) {
            if (i > 0) sb.append("§7, §d");
            sb.append(formatTime(freshTimes.get(i)));
        }
        return sb.toString();
    }

    public static String formatTime(long ms) {
        if (ms <= 0) return "0.0s";
        long sec = ms / 1000;
        long rem = (ms % 1000) / 100;
        if (sec >= 60) {
            long min = sec / 60;
            sec = sec % 60;
            return String.format(Locale.ROOT, "%d:%02d.%ds", min, sec, rem);
        }
        return String.format(Locale.ROOT, "%d.%ds", sec, rem);
    }

    public static Phase getCurrentPhase() {
        return currentPhase;
    }

    public static long getRunStartTime() {
        return runStartTime;
    }

    public static int getSuppliesPlaced() {
        return suppliesPlaced;
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;
        if (!SkyblockUtils.isInKuudra()) return;

        boolean showSplits = s.kuudraSplits && (currentPhase != Phase.NONE);
        boolean showSupplyTimes = s.kuudraSupplyTimes && (!supplyTimes.isEmpty());
        boolean showFreshTimes = s.kuudraFreshTimes && (!freshTimes.isEmpty());

        if (!showSplits && !showSupplyTimes && !showFreshTimes) return;

        int hudX = 16;
        int hudY = 60;
        int lineHeight = 10;
        long now = System.currentTimeMillis();

        List<String> lines = new ArrayList<>();

        if (showSplits) {
            long p1 = suppliesEndTime > 0 ? (suppliesEndTime - runStartTime) : (runStartTime > 0 ? (now - runStartTime) : 0);
            long p2 = buildEndTime > 0 ? (buildEndTime - suppliesEndTime) : (suppliesEndTime > 0 ? (now - suppliesEndTime) : 0);
            long p3 = stunEndTime > 0 ? (stunEndTime - buildEndTime) : (buildEndTime > 0 ? (now - buildEndTime) : 0);
            long p4 = killEndTime > 0 ? (killEndTime - stunEndTime) : (stunEndTime > 0 ? (now - stunEndTime) : 0);

            lines.add("§bSupplies: §f" + formatTime(p1));
            if (currentPhase.ordinal() >= Phase.BUILD.ordinal()) {
                lines.add("§6Build: §f" + formatTime(p2));
            }
            if (currentPhase.ordinal() >= Phase.STUN.ordinal()) {
                lines.add("§eFuel / Stun: §f" + formatTime(p3));
            }
            if (currentPhase.ordinal() >= Phase.KILL.ordinal()) {
                lines.add("§cKill: §f" + formatTime(p4));
            }
            if (s.kuudraPace && runStartTime > 0) {
                long totalElapsed = (killEndTime > 0 ? killEndTime : now) - runStartTime;
                lines.add("§dPace: §f" + formatTime(totalElapsed));
            }
            if (s.kuudraShowLag && accumulatedServerLagMs > 0) {
                lines.add("§cLag: §f" + String.format(Locale.ROOT, "%.2fs", accumulatedServerLagMs / 1000.0));
            }
        }

        if (showSupplyTimes) {
            if (!lines.isEmpty()) lines.add("");
            lines.add("§b§lSupply Times [§a" + Math.min(6, supplyRecords.size()) + "§8/§a6§b§l]");
            for (SupplyRecord rec : supplyRecords) {
                String p = (rec.playerDisplay != null && !rec.playerDisplay.isEmpty()) ? "§b" + rec.playerDisplay + " " : "";
                lines.add(p + "§8(" + rec.number + "/6): §f" + formatTime(rec.timeMs));
            }
        }

        if (showFreshTimes) {
            if (!lines.isEmpty()) lines.add("");
            lines.add("§b§lFresh Times [§2§l" + freshTimes.size() + "§b§l]");
            for (int i = 0; i < freshTimes.size(); i++) {
                lines.add("§8#" + (i + 1) + " §d" + formatTime(freshTimes.get(i)));
            }
        }

        if (lines.isEmpty()) return;

        int maxW = 0;
        for (String l : lines) {
            maxW = Math.max(maxW, font.width(l));
        }

        int totalH = lines.size() * lineHeight + 4;
        if (!s.hudNoBackground) {
            g.fill(hudX - 4, hudY - 2, hudX + maxW + 4, hudY + totalH, 0x90000000);
        }

        int curY = hudY;
        for (String l : lines) {
            if (!l.isEmpty()) {
                g.text(font, l, hudX, curY, 0xFFFFFFFF, true);
            }
            curY += lineHeight;
        }
    }
}
