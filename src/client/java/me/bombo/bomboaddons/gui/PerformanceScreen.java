package me.bombo.bomboaddons.gui;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.PerformanceProfiler;
import me.bombo.bomboaddons.features.screenshare.ScreenshareManager;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class PerformanceScreen extends Screen {
    private final Screen parent;
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private long lastFetchTime = 0L;
    private List<PerformanceProfiler.Snapshot> cachedSnapshots = new ArrayList<>();
    private double totalMeasuredMsPerSec = 0.0;
    private PerformanceProfiler.Snapshot hoveredSnapshot = null;

    private String drillDownFilter = null;
    private long dumpFeedbackTime = 0L;
    private String dumpFeedbackMsg = "";

    public PerformanceScreen(Screen parent) {
        super(Component.literal("BomboAddons Performance Profiler"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        PerformanceProfiler.activeScreenOpen = true;
        refreshSnapshots();
    }

    @Override
    public void removed() {
        super.removed();
        PerformanceProfiler.activeScreenOpen = false;
    }

    private void refreshSnapshots() {
        long now = System.currentTimeMillis();
        double intervalSec = lastFetchTime > 0 ? Math.max(0.1, (now - lastFetchTime) / 1000.0) : 1.5;
        lastFetchTime = now;
        cachedSnapshots.clear();
        totalMeasuredMsPerSec = 0.0;

        for (PerformanceProfiler.FeatureStats stat : PerformanceProfiler.STATS.values()) {
            long count = stat.callCount.getAndSet(0);
            long total = stat.totalNanos.getAndSet(0);
            long max = stat.maxNanos.getAndSet(0);
            long min = stat.minNanos.getAndSet(Long.MAX_VALUE);
            if (count > 0) {
                if (drillDownFilter != null && !drillDownFilter.isEmpty()) {
                    if (!stat.name.toLowerCase(java.util.Locale.ROOT).contains(drillDownFilter.toLowerCase(java.util.Locale.ROOT))) {
                        continue;
                    }
                }
                PerformanceProfiler.Snapshot snap = new PerformanceProfiler.Snapshot(
                        stat.name, count, total, max, min, intervalSec
                );
                cachedSnapshots.add(snap);
                totalMeasuredMsPerSec += snap.totalMsPerSec;
            }
        }

        // Sort descending by CPU consumption (totalMsPerSec)
        cachedSnapshots.sort((a, b) -> Double.compare(b.totalMsPerSec, a.totalMsPerSec));
    }

    private int getHeaderH() { return 56; }
    private int getWinW() { return Math.min(860, this.width - 40); }
    private int getWinH() {
        int streamCardH = ScreenshareManager.isStreaming() ? 38 : 0;
        int estimatedH = getHeaderH() + streamCardH + Math.max(1, cachedSnapshots.size()) * 36 + 24;
        return Math.min(Math.max(estimatedH, 200), Math.min(560, this.height - 24));
    }
    private int getWinX() { return (this.width - getWinW()) / 2; }
    private int getWinY() { return (this.height - getWinH()) / 2; }

    private double cachedProcessCpu = -1.0;
    private long cachedUsedMem = 0L;
    private long cachedTotalMem = 0L;
    private int cachedActiveThreads = 0;
    private long lastSysInfoFetch = 0L;

    private void updateSysInfo() {
        long now = System.currentTimeMillis();
        if (now - lastSysInfoFetch < 1000L && lastSysInfoFetch > 0L) return;
        lastSysInfoFetch = now;

        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory() / (1024 * 1024);
        long freeMem = rt.freeMemory() / (1024 * 1024);
        cachedTotalMem = totalMem;
        cachedUsedMem = totalMem - freeMem;
        cachedActiveThreads = Thread.activeCount();

        try {
            java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                cachedProcessCpu = sunBean.getProcessCpuLoad() * 100.0;
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Refresh snapshots every 1.5 seconds if actively viewing
        if (System.currentTimeMillis() - lastFetchTime > 1500L) {
            refreshSnapshots();
        }
        updateSysInfo();

        // Dark frosted backdrop
        g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

        int winW = getWinW();
        int headerH = getHeaderH();
        int winH = getWinH();
        int winX = getWinX();
        int winY = getWinY();

        // Window base
        g.fill(winX, winY, winX + winW, winY + winH, ConfigUITheme.getMainWindowBg());
        g.outline(winX, winY, winW, winH, ConfigUITheme.getBorderColor());

        // Header bar
        g.fill(winX, winY, winX + winW, winY + headerH, ConfigUITheme.getSidebarBg());
        g.fill(winX, winY + headerH - 1, winX + winW, winY + headerH, ConfigUITheme.getBorderColor());

        Font font = Minecraft.getInstance().font;
        String title = drillDownFilter != null
                ? "§b§lBomboAddons §fProfiler §8> §e" + drillDownFilter
                : "§b§lBomboAddons §fPerformance Visualizer";
        g.text(font, title, winX + 16, winY + 10, 0xFFFFFFFF, true);

        String statusSummary = String.format("§7Features: §f%d §8| §7Total Load: §e%.2f ms/s" + (drillDownFilter != null ? " §8(Drill-down Active)" : " §8(Click item to drill down)"),
                cachedSnapshots.size(), totalMeasuredMsPerSec);
        g.text(font, statusSummary, winX + 16, winY + 25, 0xFFAAAAAA, false);

        String cpuPart = (cachedProcessCpu >= 0.0) ? String.format(" | §eCPU: §a%.1f%%", cachedProcessCpu) : "";
        String memSummary = String.format("§7JVM: §a%dMB §8/ §7%dMB%s §8| §7Threads: §b%d",
                cachedUsedMem, cachedTotalMem, cpuPart, cachedActiveThreads);
        if (ScreenshareManager.isStreaming()) {
            memSummary += String.format(" | §3Stream: §a%.0f FPS §7(%s)", ScreenshareManager.getCurrentFps(), ScreenshareManager.getLastCaptureMode());
        } else {
            memSummary += " | §3Screenshare: §7" + ScreenshareManager.getLastCaptureMode();
        }
        g.text(font, memSummary, winX + 16, winY + 38, 0xFF888888, false);

        // Right side header buttons (Back / Dump Log / Reset Stats / Close)
        int btnW = 80;
        int btnH = 22;

        if (drillDownFilter != null) {
            int backBtnX = winX + winW - btnW * 4 - 40;
            int backBtnY = winY + 16;
            boolean hoverBack = mouseX >= backBtnX && mouseX <= backBtnX + btnW && mouseY >= backBtnY && mouseY <= backBtnY + btnH;
            g.fill(backBtnX, backBtnY, backBtnX + btnW, backBtnY + btnH, hoverBack ? 0x4400E5FF : 0x22FFFFFF);
            g.outline(backBtnX, backBtnY, btnW, btnH, hoverBack ? 0xFF00E5FF : 0x44FFFFFF);
            g.text(font, "§b← All", backBtnX + 18, backBtnY + 7, 0xFFFFFFFF, false);
        }

        int dumpBtnX = winX + winW - btnW * 3 - 30;
        int dumpBtnY = winY + 16;
        boolean hoverDump = mouseX >= dumpBtnX && mouseX <= dumpBtnX + btnW && mouseY >= dumpBtnY && mouseY <= dumpBtnY + btnH;
        g.fill(dumpBtnX, dumpBtnY, dumpBtnX + btnW, dumpBtnY + btnH, hoverDump ? 0x4400FF66 : 0x22FFFFFF);
        g.outline(dumpBtnX, dumpBtnY, btnW, btnH, hoverDump ? 0xFF00FF66 : 0x44FFFFFF);
        g.text(font, "§aDump Log", dumpBtnX + 14, dumpBtnY + 7, 0xFFFFFFFF, false);

        int resetBtnX = winX + winW - btnW * 2 - 20;
        int resetBtnY = winY + 16;
        boolean hoverReset = mouseX >= resetBtnX && mouseX <= resetBtnX + btnW && mouseY >= resetBtnY && mouseY <= resetBtnY + btnH;
        g.fill(resetBtnX, resetBtnY, resetBtnX + btnW, resetBtnY + btnH, hoverReset ? 0x44FF9900 : 0x22FFFFFF);
        g.outline(resetBtnX, resetBtnY, btnW, btnH, hoverReset ? 0xFFFFAA00 : 0x44FFFFFF);
        g.text(font, "§6Reset Stats", resetBtnX + 10, resetBtnY + 7, 0xFFFFFFFF, false);

        int closeBtnX = winX + winW - btnW - 10;
        int closeBtnY = winY + 16;
        boolean hoverClose = mouseX >= closeBtnX && mouseX <= closeBtnX + btnW && mouseY >= closeBtnY && mouseY <= closeBtnY + btnH;
        g.fill(closeBtnX, closeBtnY, closeBtnX + btnW, closeBtnY + btnH, hoverClose ? 0x44FF3333 : 0x22FFFFFF);
        g.outline(closeBtnX, closeBtnY, btnW, btnH, hoverClose ? 0xFFFF4444 : 0x44FFFFFF);
        g.text(font, "§cClose (ESC)", closeBtnX + 10, closeBtnY + 7, 0xFFFFFFFF, false);

        if (dumpFeedbackTime > 0 && System.currentTimeMillis() - dumpFeedbackTime < 4000L) {
            g.text(font, "§a" + dumpFeedbackMsg, winX + 16, winY + headerH - 12, 0xFF00FF66, true);
        }

        // Content Area with Scrolling
        int contentX = winX + 16;
        int contentY = winY + headerH + 12;
        int contentW = winW - 32;
        int contentH = winH - headerH - 24;

        hoveredSnapshot = null;

        if (cachedSnapshots.isEmpty()) {
            String emptyMsg = BomboConfig.get().performanceDebug
                    ? "§7No profiling data accumulated yet. Run commands or interact with menus to see CPU loads."
                    : "§ePerformance profiler is currently disabled. Enable \"Performance Profiler\" in config or click below:";
            g.text(font, emptyMsg, contentX + 10, contentY + 20, 0xFFAAAAAA, false);

            if (!BomboConfig.get().performanceDebug) {
                int enableBtnX = contentX + 10;
                int enableBtnY = contentY + 45;
                int enableBtnW = 160;
                int enableBtnH = 24;
                boolean hoverEnable = mouseX >= enableBtnX && mouseX <= enableBtnX + enableBtnW && mouseY >= enableBtnY && mouseY <= enableBtnY + enableBtnH;
                g.fill(enableBtnX, enableBtnY, enableBtnX + enableBtnW, enableBtnY + enableBtnH, hoverEnable ? 0xFF10B981 : 0xFF059669);
                g.text(font, "§f✔ Enable Profiler Now", enableBtnX + 16, enableBtnY + 8, 0xFFFFFFFF, true);
            }
            return;
        }

        double maxSingleFeatureMs = cachedSnapshots.get(0).totalMsPerSec;
        if (maxSingleFeatureMs <= 0.0001) maxSingleFeatureMs = 1.0;

        int cardHeight = 32;
        int cardGap = 4;
        boolean isStreaming = ScreenshareManager.isStreaming();
        int streamCardH = isStreaming ? 34 : 0;
        int totalContentHeight = (cachedSnapshots.size() * (cardHeight + cardGap)) + (isStreaming ? (streamCardH + cardGap) : 0);
        maxScroll = Math.max(0, totalContentHeight - contentH);
        scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));

        g.enableScissor(contentX, contentY, contentW, contentH);

        int curY = contentY - (int) scrollAmount;

        // Render live streaming telemetry card first if streaming
        if (isStreaming) {
            int cardY = curY;
            curY += streamCardH + cardGap;
            if (cardY + streamCardH >= contentY && cardY <= contentY + contentH) {
                boolean isHovered = mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= cardY && mouseY <= cardY + streamCardH;
                g.fill(contentX, cardY, contentX + contentW, cardY + streamCardH, isHovered ? 0x3300E5FF : 0x1A003344);
                g.outline(contentX, cardY, contentW, streamCardH, isHovered ? 0xFF00E5FF : 0xFF0088AA);

                // Live pulsing indicator
                boolean pulse = (System.currentTimeMillis() / 600) % 2 == 0;
                g.fill(contentX + 10, cardY + 7, contentX + 18, cardY + 15, pulse ? 0xFFFF3333 : 0xFF990000);
                g.text(font, "§c§lLIVE §bScreenshare Broadcasting", contentX + 24, cardY + 6, 0xFFFFFFFF, true);

                String resStr = ScreenshareManager.getCurrentTargetW() + "x" + ScreenshareManager.getCurrentTargetH();
                String rightMetrics = String.format("§a%.1f FPS §8| §d%.1f kbps §8| §b%s",
                        ScreenshareManager.getCurrentFps(), ScreenshareManager.getCurrentBitrateKbps(), resStr);
                int rmW = font.width(rightMetrics);
                g.text(font, rightMetrics, contentX + contentW - rmW - 12, cardY + 6, 0xFFFFFFFF, false);

                String details = String.format("§7Mode: §e%s §8| §7HTTP Latency: §a%dms §8| §7Payload: §b%.1f KB §8| §7Sent: §6%.2f MB §8| §7Frames: §f%d/%d",
                        ScreenshareManager.getLastCaptureMode(),
                        ScreenshareManager.getLastLatencyMs(),
                        ScreenshareManager.getLastJpegSizeKb(),
                        ScreenshareManager.getTotalBytesSent() / (1024.0 * 1024.0),
                        ScreenshareManager.getFramesSent(),
                        ScreenshareManager.getFramesCaptured());
                g.text(font, details, contentX + 10, cardY + 19, 0xFF9CA3AF, false);
            }
        }

        for (int i = 0; i < cachedSnapshots.size(); i++) {
            PerformanceProfiler.Snapshot snap = cachedSnapshots.get(i);
            int cardY = curY;
            curY += cardHeight + cardGap;

            // Only render visible cards
            if (cardY + cardHeight < contentY || cardY > contentY + contentH) {
                continue;
            }

            boolean isHovered = mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= cardY && mouseY <= cardY + cardHeight;
            if (isHovered) {
                hoveredSnapshot = snap;
            }

            // Card background
            int cardBg = isHovered ? 0x2E253347 : 0x1A1F2937;
            g.fill(contentX, cardY, contentX + contentW, cardY + cardHeight, cardBg);

            // Proportional CPU Load visual bar at bottom of card
            double loadRatio = Math.min(1.0, snap.totalMsPerSec / maxSingleFeatureMs);
            int barW = (int) (contentW * loadRatio);
            int barColor;
            if (snap.totalMsPerSec > 30.0 || snap.cpuPercent > 3.0) {
                barColor = 0xFFEF4444; // High CPU / Red (>3% CPU)
            } else if (snap.totalMsPerSec > 10.0 || snap.cpuPercent > 1.0) {
                barColor = 0xFFF59E0B; // Moderate CPU / Orange (>1% CPU)
            } else if (snap.totalMsPerSec > 2.0 || snap.cpuPercent > 0.2) {
                barColor = 0xFF10B981; // Low CPU / Green (>0.2% CPU)
            } else {
                barColor = 0xFF3B82F6; // Very Low / Blue
            }

            g.fill(contentX, cardY + cardHeight - 2, contentX + barW, cardY + cardHeight, barColor);
            g.outline(contentX, cardY, contentW, cardHeight, isHovered ? barColor : 0x334B5563);

            // Rank badge
            String rank = "#" + (i + 1);
            int rankColor = i == 0 ? 0xFFFFD700 : (i == 1 ? 0xFFC0C0C0 : (i == 2 ? 0xFFCD7F32 : 0xFF9CA3AF));
            g.text(font, rank, contentX + 10, cardY + 5, rankColor, true);

            // Feature Name
            g.text(font, snap.name, contentX + 44, cardY + 5, 0xFFFFFFFF, false);

            // Metrics: Total CPU consumption
            String totalMsStr = String.format("%.2f ms/s (%.1f%% CPU)", snap.totalMsPerSec, snap.cpuPercent);
            int msStrW = font.width(totalMsStr);
            g.text(font, totalMsStr, contentX + contentW - msStrW - 14, cardY + 5, barColor, true);

            // Sub-metrics (Calls/sec or FPS, Avg latency, Max latency, Est Memory)
            boolean isRenderFeature = snap.name.toLowerCase(java.util.Locale.ROOT).contains("hud") || snap.name.toLowerCase(java.util.Locale.ROOT).contains("render");
            String callLabel = isRenderFeature ? "Render" : "Calls";
            String rateUnit = isRenderFeature ? " fps" : "/s";
            String details = String.format("§7%s: §f%.0f%s §8| §7Avg: §f%.3f ms §8| §7Max: §f%.2f ms §8| §7Est Mem: §b~%d KB",
                    callLabel, snap.callsPerSec, rateUnit, snap.avgMs, snap.maxMs, snap.estimatedMemKb);
            g.text(font, details, contentX + 44, cardY + 17, 0xFF9CA3AF, false);
        }

        g.disableScissor();

        // Scrollbar track & thumb
        if (maxScroll > 0) {
            int scrollbarX = contentX + contentW - 6;
            int scrollbarH = contentH;
            g.fill(scrollbarX, contentY, scrollbarX + 4, contentY + scrollbarH, 0x22FFFFFF);
            int thumbH = Math.max(20, (int) ((contentH / (float) totalContentHeight) * contentH));
            int thumbY = contentY + (int) ((scrollAmount / maxScroll) * (contentH - thumbH));
            g.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, 0x8800E5FF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (maxScroll > 0) {
            scrollAmount -= amountY * 24.0;
            scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0) {
            int winW = getWinW();
            int winH = getWinH();
            int winX = getWinX();
            int winY = getWinY();
            int headerH = getHeaderH();

            int btnW = 80;
            int btnH = 22;

            if (drillDownFilter != null) {
                int backBtnX = winX + winW - btnW * 4 - 40;
                int backBtnY = winY + 16;
                if (event.x() >= backBtnX && event.x() <= backBtnX + btnW && event.y() >= backBtnY && event.y() <= backBtnY + btnH) {
                    drillDownFilter = null;
                    scrollAmount = 0;
                    refreshSnapshots();
                    return true;
                }
            }

            int dumpBtnX = winX + winW - btnW * 3 - 30;
            int dumpBtnY = winY + 16;
            if (event.x() >= dumpBtnX && event.x() <= dumpBtnX + btnW && event.y() >= dumpBtnY && event.y() <= dumpBtnY + btnH) {
                PerformanceProfiler.dumpNowToFile((comp) -> {
                    dumpFeedbackMsg = comp.getString();
                    dumpFeedbackTime = System.currentTimeMillis();
                });
                return true;
            }

            int resetBtnX = winX + winW - btnW * 2 - 20;
            int resetBtnY = winY + 16;
            if (event.x() >= resetBtnX && event.x() <= resetBtnX + btnW && event.y() >= resetBtnY && event.y() <= resetBtnY + btnH) {
                for (PerformanceProfiler.FeatureStats s : PerformanceProfiler.STATS.values()) {
                    s.reset();
                }
                refreshSnapshots();
                return true;
            }

            int closeBtnX = winX + winW - btnW - 10;
            int closeBtnY = winY + 16;
            if (event.x() >= closeBtnX && event.x() <= closeBtnX + btnW && event.y() >= closeBtnY && event.y() <= closeBtnY + btnH) {
                this.onClose();
                return true;
            }

            // Click card to drill down
            int contentX = winX + 16;
            int contentY = winY + headerH + 12;
            int contentW = winW - 32;
            int contentH = winH - headerH - 24;
            if (event.x() >= contentX && event.x() <= contentX + contentW && event.y() >= contentY && event.y() <= contentY + contentH) {
                if (hoveredSnapshot != null) {
                    String name = hoveredSnapshot.name;
                    String target = name;
                    if (name.contains(":")) {
                        target = name.substring(name.indexOf(':') + 1).trim();
                    }
                    if (drillDownFilter == null) {
                        drillDownFilter = target;
                    } else {
                        drillDownFilter = null;
                    }
                    scrollAmount = 0;
                    refreshSnapshots();
                    return true;
                }
            }

            // Enable profiler button when empty
            if (!BomboConfig.get().performanceDebug && cachedSnapshots.isEmpty()) {
                int enableBtnX = contentX + 10;
                int enableBtnY = contentY + 45;
                int enableBtnW = 160;
                int enableBtnH = 24;
                if (event.x() >= enableBtnX && event.x() <= enableBtnX + enableBtnW && event.y() >= enableBtnY && event.y() <= enableBtnY + enableBtnH) {
                    BomboConfig.get().performanceDebug = true;
                    BomboConfig.save();
                    refreshSnapshots();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
