package me.bombo.bomboaddons;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PerformanceProfiler {
    public static final Map<String, FeatureStats> STATS = new ConcurrentHashMap<>();
    private static long lastFlushTime = System.currentTimeMillis();
    private static final File LOG_FILE = new File("bombo_perf_debug.log");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static class FeatureStats {
        public final String name;
        public final AtomicLong totalNanos = new AtomicLong(0);
        public final AtomicLong callCount = new AtomicLong(0);
        public final AtomicLong maxNanos = new AtomicLong(0);
        public final AtomicLong minNanos = new AtomicLong(Long.MAX_VALUE);

        public FeatureStats(String name) {
            this.name = name;
        }

        public void record(long durationNanos) {
            totalNanos.addAndGet(durationNanos);
            callCount.incrementAndGet();
            maxNanos.accumulateAndGet(durationNanos, Math::max);
            minNanos.accumulateAndGet(durationNanos, Math::min);
        }

        public void reset() {
            totalNanos.set(0);
            callCount.set(0);
            maxNanos.set(0);
            minNanos.set(Long.MAX_VALUE);
        }
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final Scope NOOP = () -> {};

    public static boolean isEnabled() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.performanceDebug;
    }

    public static Scope scope(String name) {
        if (!isEnabled()) return NOOP;
        long start = System.nanoTime();
        return () -> {
            long duration = System.nanoTime() - start;
            record(name, duration);
        };
    }

    public static void record(String name, long durationNanos) {
        if (name == null || !isEnabled()) return;
        FeatureStats stats = STATS.computeIfAbsent(name, FeatureStats::new);
        stats.record(durationNanos);
    }

    public static void onTick() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        // Flush report every 5 seconds (5000ms)
        if (now - lastFlushTime >= 5000L) {
            flushReportToFile(now - lastFlushTime);
            lastFlushTime = now;
        }
    }

    public static synchronized void flushReportToFile(long intervalMs) {
        if (STATS.isEmpty()) return;
        double intervalSec = Math.max(0.1, intervalMs / 1000.0);

        List<Snapshot> snapshots = new ArrayList<>();
        for (FeatureStats stat : STATS.values()) {
            long count = stat.callCount.get();
            if (count > 0) {
                long total = stat.totalNanos.get();
                long max = stat.maxNanos.get();
                long min = stat.minNanos.get();
                snapshots.add(new Snapshot(stat.name, count, total, max, min, intervalSec));
                stat.reset();
            }
        }

        if (snapshots.isEmpty()) return;

        // Sort by total CPU time descending
        snapshots.sort((a, b) -> Double.compare(b.totalMsPerSec, a.totalMsPerSec));

        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println("================================================================================");
            pw.println("[" + DATE_FORMAT.format(new Date()) + "] PERFORMANCE DEBUG REPORT (Window: " + String.format("%.1f", intervalSec) + "s)");
            pw.println(String.format("%-36s | %-10s | %-10s | %-10s | %-10s", "Feature Name", "Calls/sec", "Avg (ms)", "Max (ms)", "Total (ms/s)"));
            pw.println("--------------------------------------------------------------------------------");
            for (Snapshot s : snapshots) {
                pw.println(String.format("%-36s | %10.1f | %10.4f | %10.4f | %10.2f",
                        s.name, s.callsPerSec, s.avgMs, s.maxMs, s.totalMsPerSec));
            }
            pw.println("================================================================================");
            pw.println();
        } catch (Throwable ignored) {
        }
    }

    public static void printReportToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (STATS.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7No performance stats recorded yet."));
            return;
        }

        List<Snapshot> snapshots = new ArrayList<>();
        double intervalSec = Math.max(0.1, (System.currentTimeMillis() - lastFlushTime) / 1000.0);
        for (FeatureStats stat : STATS.values()) {
            long count = stat.callCount.get();
            if (count > 0) {
                snapshots.add(new Snapshot(stat.name, count, stat.totalNanos.get(), stat.maxNanos.get(), stat.minNanos.get(), intervalSec));
            }
        }
        snapshots.sort((a, b) -> Double.compare(b.totalMsPerSec, a.totalMsPerSec));

        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §6§l=== Performance Report (Top 8) ==="));
        int limit = Math.min(8, snapshots.size());
        for (int i = 0; i < limit; i++) {
            Snapshot s = snapshots.get(i);
            String color = s.totalMsPerSec > 5.0 ? "§c" : (s.totalMsPerSec > 1.0 ? "§e" : "§a");
            mc.player.sendSystemMessage(Component.literal(
                    " §7" + (i + 1) + ". " + color + s.name + " §8-> §f" + String.format("%.3f", s.avgMs) + "ms avg §8| §e" + String.format("%.2f", s.totalMsPerSec) + "ms/s (" + String.format("%.0f", s.callsPerSec) + " calls/s)"
            ));
        }
        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Full detailed log stored in: §ebombo_perf_debug.log"));
    }

    public static class Snapshot {
        public final String name;
        public final long callCount;
        public final double avgMs;
        public final double maxMs;
        public final double minMs;
        public final double callsPerSec;
        public final double totalMsPerSec;

        public Snapshot(String name, long count, long totalNanos, long maxNanos, long minNanos, double intervalSec) {
            this.name = name;
            this.callCount = count;
            this.avgMs = count > 0 ? (totalNanos / (double) count) / 1_000_000.0 : 0.0;
            this.maxMs = maxNanos / 1_000_000.0;
            this.minMs = minNanos == Long.MAX_VALUE ? 0.0 : minNanos / 1_000_000.0;
            this.callsPerSec = count / intervalSec;
            this.totalMsPerSec = (totalNanos / 1_000_000.0) / intervalSec;
        }
    }
}
