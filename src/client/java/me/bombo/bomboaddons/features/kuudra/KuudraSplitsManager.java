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

    private static int suppliesPlaced = 0;
    private static final List<Long> supplyTimes = new ArrayList<>();
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
        freshTimes.clear();
        accumulatedServerLagMs = 0L;
        lastServerTickTime = System.currentTimeMillis();
    }

    public static void onSupplyPlaced(int count) {
        suppliesPlaced = count;
        long elapsed = System.currentTimeMillis() - runStartTime;
        supplyTimes.add(elapsed);

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

    public static long getAccumulatedServerLagMs() {
        return accumulatedServerLagMs;
    }
}
