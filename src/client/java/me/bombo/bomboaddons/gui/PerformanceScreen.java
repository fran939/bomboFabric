package me.bombo.bomboaddons.gui;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.PerformanceProfiler;
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

    public PerformanceScreen(Screen parent) {
        super(Component.literal("BomboAddons Performance Profiler"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        refreshSnapshots();
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Refresh snapshots every 1.5 seconds if actively viewing
        if (System.currentTimeMillis() - lastFetchTime > 1500L) {
            refreshSnapshots();
        }

        // Dark frosted backdrop
        g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

        int winW = Math.min(860, this.width - 40);
        int winH = Math.min(540, this.height - 40);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Window base
        g.fill(winX, winY, winX + winW, winY + winH, ConfigUITheme.getMainWindowBg());
        g.outline(winX, winY, winW, winH, ConfigUITheme.getBorderColor());

        // Header bar
        int headerH = 46;
        g.fill(winX, winY, winX + winW, winY + headerH, ConfigUITheme.getSidebarBg());
        g.fill(winX, winY + headerH - 1, winX + winW, winY + headerH, ConfigUITheme.getBorderColor());

        Font font = Minecraft.getInstance().font;
        String title = drillDownFilter != null
                ? "§b§lBomboAddons §fProfiler §8> §e" + drillDownFilter
                : "§b§lBomboAddons §fPerformance Visualizer";
        g.text(font, title, winX + 16, winY + 12, 0xFFFFFFFF, true);

        String statusSummary = String.format("§7Features: §f%d §8| §7Total Load: §e%.2f ms/s" + (drillDownFilter != null ? " §8(Drill-down Active)" : " §8(Click item to drill down)"),
                cachedSnapshots.size(), totalMeasuredMsPerSec);
        g.text(font, statusSummary, winX + 16, winY + 28, 0xFFAAAAAA, false);

        // Right side header buttons (Back / Reset Stats / Close)
        int btnW = 80;
        int btnH = 22;

        if (drillDownFilter != null) {
            int backBtnX = winX + winW - btnW * 3 - 30;
            int backBtnY = winY + 12;
            boolean hoverBack = mouseX >= backBtnX && mouseX <= backBtnX + btnW && mouseY >= backBtnY && mouseY <= backBtnY + btnH;
            g.fill(backBtnX, backBtnY, backBtnX + btnW, backBtnY + btnH, hoverBack ? 0x4400E5FF : 0x22FFFFFF);
            g.outline(backBtnX, backBtnY, btnW, btnH, hoverBack ? 0xFF00E5FF : 0x44FFFFFF);
            g.text(font, "§b← All", backBtnX + 18, backBtnY + 7, 0xFFFFFFFF, false);
        }

        int resetBtnX = winX + winW - btnW * 2 - 20;
        int resetBtnY = winY + 12;
        boolean hoverReset = mouseX >= resetBtnX && mouseX <= resetBtnX + btnW && mouseY >= resetBtnY && mouseY <= resetBtnY + btnH;
        g.fill(resetBtnX, resetBtnY, resetBtnX + btnW, resetBtnY + btnH, hoverReset ? 0x44FF9900 : 0x22FFFFFF);
        g.outline(resetBtnX, resetBtnY, btnW, btnH, hoverReset ? 0xFFFFAA00 : 0x44FFFFFF);
        g.text(font, "§6Reset Stats", resetBtnX + 10, resetBtnY + 7, 0xFFFFFFFF, false);

        int closeBtnX = winX + winW - btnW - 10;
        int closeBtnY = winY + 12;
        boolean hoverClose = mouseX >= closeBtnX && mouseX <= closeBtnX + btnW && mouseY >= closeBtnY && mouseY <= closeBtnY + btnH;
        g.fill(closeBtnX, closeBtnY, closeBtnX + btnW, closeBtnY + btnH, hoverClose ? 0x44FF3333 : 0x22FFFFFF);
        g.outline(closeBtnX, closeBtnY, btnW, btnH, hoverClose ? 0xFFFF4444 : 0x44FFFFFF);
        g.text(font, "§cClose (ESC)", closeBtnX + 10, closeBtnY + 7, 0xFFFFFFFF, false);

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

        int totalContentHeight = 0;
        int cardHeight = 44;
        int cardGap = 8;
        totalContentHeight = cachedSnapshots.size() * (cardHeight + cardGap);
        maxScroll = Math.max(0, totalContentHeight - contentH);
        scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));

        g.enableScissor(contentX, contentY, contentW, contentH);

        int curY = contentY - (int) scrollAmount;

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
            if (snap.totalMsPerSec > 5.0) {
                barColor = 0xFFEF4444; // High CPU / Red
            } else if (snap.totalMsPerSec > 1.5) {
                barColor = 0xFFF59E0B; // Moderate CPU / Orange
            } else if (snap.totalMsPerSec > 0.3) {
                barColor = 0xFF10B981; // Low CPU / Green
            } else {
                barColor = 0xFF3B82F6; // Very Low / Blue
            }

            g.fill(contentX, cardY + cardHeight - 3, contentX + barW, cardY + cardHeight, barColor);
            g.outline(contentX, cardY, contentW, cardHeight, isHovered ? barColor : 0x334B5563);

            // Rank badge
            String rank = "#" + (i + 1);
            int rankColor = i == 0 ? 0xFFFFD700 : (i == 1 ? 0xFFC0C0C0 : (i == 2 ? 0xFFCD7F32 : 0xFF9CA3AF));
            g.text(font, rank, contentX + 10, cardY + 10, rankColor, true);

            // Feature Name
            g.text(font, snap.name, contentX + 44, cardY + 10, 0xFFFFFFFF, false);

            // Metrics: Total CPU consumption
            String totalMsStr = String.format("%.2f ms/s", snap.totalMsPerSec);
            g.text(font, totalMsStr, contentX + contentW - 120, cardY + 10, barColor, true);

            // Sub-metrics (Calls/sec, Avg latency, Max latency)
            String details = String.format("§7Calls: §f%.0f/s §8| §7Avg: §f%.3f ms §8| §7Max: §f%.2f ms §8| §7Min: §f%.3f ms",
                    snap.callsPerSec, snap.avgMs, snap.maxMs, snap.minMs);
            g.text(font, details, contentX + 44, cardY + 24, 0xFF9CA3AF, false);
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
            int winW = Math.min(860, this.width - 40);
            int winH = Math.min(540, this.height - 40);
            int winX = (this.width - winW) / 2;
            int winY = (this.height - winH) / 2;
            int headerH = 46;

            int btnW = 80;
            int btnH = 22;

            if (drillDownFilter != null) {
                int backBtnX = winX + winW - btnW * 3 - 30;
                int backBtnY = winY + 12;
                if (event.x() >= backBtnX && event.x() <= backBtnX + btnW && event.y() >= backBtnY && event.y() <= backBtnY + btnH) {
                    drillDownFilter = null;
                    scrollAmount = 0;
                    refreshSnapshots();
                    return true;
                }
            }

            int resetBtnX = winX + winW - btnW * 2 - 20;
            int resetBtnY = winY + 12;
            if (event.x() >= resetBtnX && event.x() <= resetBtnX + btnW && event.y() >= resetBtnY && event.y() <= resetBtnY + btnH) {
                for (PerformanceProfiler.FeatureStats s : PerformanceProfiler.STATS.values()) {
                    s.reset();
                }
                refreshSnapshots();
                return true;
            }

            int closeBtnX = winX + winW - btnW - 10;
            int closeBtnY = winY + 12;
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
