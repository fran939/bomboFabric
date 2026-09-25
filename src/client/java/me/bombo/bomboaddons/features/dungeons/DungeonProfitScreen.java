package me.bombo.bomboaddons.features.dungeons;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class DungeonProfitScreen extends Screen {

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT);

    private final Screen parent;
    private double scrollAmount = 0.0D;
    private double maxScroll = 0.0D;
    private String selectedFloorFilter = "All";

    public DungeonProfitScreen() {
        this(null);
    }

    public DungeonProfitScreen(Screen parent) {
        super(Component.literal("Dungeon Profit Tracker"));
        this.parent = parent;
    }

    private static class FloorAggregate {
        int runs = 0;
        long profit = 0L;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

        Font font = this.font;
        int winW = Math.min(680, this.width - 30);
        int winH = Math.min(420, this.height - 30);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;
        int headerH = 58;

        // Window background
        g.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, 0xEE11141F);
        g.outline(winX, winY, winW, winH, ConfigUITheme.getAccentColor());
        g.fill(winX, winY, winX + winW, winY + headerH, 0xEE1E293B);

        // Header Title
        g.text(font, ConfigUITheme.formatFont("§d§lDUNGEON PROFIT TRACKER"), winX + 14, winY + 10, 0xFFFFFFFF, false);

        List<DungeonProfitLog.RunRecord> allRuns = DungeonProfitLog.getRuns();
        long totalProfit = 0L;
        int kismetsUsed = 0;
        Map<String, FloorAggregate> floorMap = new LinkedHashMap<>();

        for (DungeonProfitLog.RunRecord run : allRuns) {
            totalProfit += run.netProfit;
            if (run.kismetUsed) kismetsUsed++;
            String fl = run.floor != null && !run.floor.isBlank() ? run.floor : "Unknown";
            FloorAggregate agg = floorMap.computeIfAbsent(fl, k -> new FloorAggregate());
            agg.runs++;
            agg.profit += run.netProfit;
        }

        long avgProfit = allRuns.isEmpty() ? 0L : (totalProfit / allRuns.size());
        String profitColor = totalProfit >= 0 ? "§a+" : "§c";
        int pending = DungeonProfitLog.getUnsyncedCount();

        // Subtitle stats
        g.text(font, "§7Total: " + profitColor + LowestBinManager.formatPrice(totalProfit)
                + " §8| §7Runs: §e" + allRuns.size()
                + " §8| §7Avg: " + (avgProfit >= 0 ? "§a+" : "§c") + LowestBinManager.formatPrice(avgProfit)
                + " §8| §7Kismets: §d" + kismetsUsed
                + " §8| §7Sync: §a" + DungeonProfitLog.getSyncedCount() + "§7/§e" + pending + " pend",
                winX + 14, winY + 28, 0xFF94A3B8, false);

        // Header Action buttons
        int syncBtnW = 76;
        int syncBtnX = winX + winW - 146;
        int syncBtnY = winY + 12;
        boolean syncHovered = mouseX >= syncBtnX && mouseX <= syncBtnX + syncBtnW && mouseY >= syncBtnY && mouseY <= syncBtnY + 20;
        ConfigUITheme.drawPillButton(g, font, "§aSync Web", syncBtnX, syncBtnY, syncBtnW, 20, syncHovered, -1,
                0x2210B981, 0x4410B981);

        int closeBtnW = 56;
        int closeBtnX = winX + winW - 64;
        int closeBtnY = winY + 12;
        boolean closeHovered = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnW && mouseY >= closeBtnY && mouseY <= closeBtnY + 20;
        ConfigUITheme.drawPillButton(g, font, "§7Close", closeBtnX, closeBtnY, closeBtnW, 20, closeHovered, -1,
                0x22FFFFFF, 0x44FFFFFF);

        // Content Area split: Left side Floor breakdown, Right side Runs list
        int leftW = 180;
        int leftX = winX + 10;
        int leftY = winY + headerH + 8;
        int contentH = winH - headerH - 18;

        g.fill(leftX, leftY, leftX + leftW, leftY + contentH, 0x140F172A);
        g.outline(leftX, leftY, leftW, contentH, 0x33475569);
        g.text(font, "§6Floor Breakdown", leftX + 8, leftY + 6, 0xFFF59E0B, false);

        List<Map.Entry<String, FloorAggregate>> sortedFloors = new ArrayList<>(floorMap.entrySet());
        sortedFloors.sort((a, b) -> Integer.compare(DungeonProfitLog.floorSortKey(a.getKey()), DungeonProfitLog.floorSortKey(b.getKey())));

        int flY = leftY + 22;
        // "All Floors" option
        boolean allHovered = mouseX >= leftX + 4 && mouseX <= leftX + leftW - 4 && mouseY >= flY && mouseY <= flY + 14;
        boolean allSelected = "All".equalsIgnoreCase(selectedFloorFilter);
        if (allSelected) {
            g.fill(leftX + 4, flY, leftX + leftW - 4, flY + 14, 0x3338BDF8);
        } else if (allHovered) {
            g.fill(leftX + 4, flY, leftX + leftW - 4, flY + 14, 0x1AFFFFFF);
        }
        g.text(font, (allSelected ? "§b§l" : "§7") + "All Floors (" + allRuns.size() + ")", leftX + 8, flY + 3, 0xFFFFFFFF, false);
        flY += 16;

        for (Map.Entry<String, FloorAggregate> entry : sortedFloors) {
            if (flY + 14 > leftY + contentH - 4) break;
            boolean hovered = mouseX >= leftX + 4 && mouseX <= leftX + leftW - 4 && mouseY >= flY && mouseY <= flY + 14;
            boolean selected = entry.getKey().equalsIgnoreCase(selectedFloorFilter);
            if (selected) {
                g.fill(leftX + 4, flY, leftX + leftW - 4, flY + 14, 0x3338BDF8);
            } else if (hovered) {
                g.fill(leftX + 4, flY, leftX + leftW - 4, flY + 14, 0x1AFFFFFF);
            }
            String prof = (entry.getValue().profit >= 0 ? "§a+" : "§c") + LowestBinManager.formatPrice(entry.getValue().profit);
            g.text(font, (selected ? "§b" : "§f") + entry.getKey() + " §8(" + entry.getValue().runs + ")", leftX + 8, flY + 3, 0xFFFFFFFF, false);
            int pW = font.width(prof);
            g.text(font, prof, leftX + leftW - 8 - pW, flY + 3, 0xFFFFFFFF, false);
            flY += 16;
        }

        // Right side: Runs list
        int rightX = leftX + leftW + 10;
        int rightW = winW - leftW - 30;
        int rightY = leftY;

        g.fill(rightX, rightY, rightX + rightW, rightY + contentH, 0x140F172A);
        g.outline(rightX, rightY, rightW, contentH, 0x33475569);

        List<DungeonProfitLog.RunRecord> displayedRuns = new ArrayList<>();
        for (int i = allRuns.size() - 1; i >= 0; i--) {
            DungeonProfitLog.RunRecord run = allRuns.get(i);
            if ("All".equalsIgnoreCase(selectedFloorFilter) || run.floor.equalsIgnoreCase(selectedFloorFilter)) {
                displayedRuns.add(run);
            }
        }

        if (displayedRuns.isEmpty()) {
            g.text(font, "§8No runs logged yet for this filter.", rightX + 12, rightY + 12, 0xFF64748B, false);
            return;
        }

        int rowH = 22;
        int totalH = displayedRuns.size() * rowH;
        maxScroll = Math.max(0, totalH - contentH);
        if (scrollAmount > maxScroll) scrollAmount = maxScroll;

        int firstRow = (int) (scrollAmount / rowH);
        int visibleCount = contentH / rowH + 2;

        DungeonProfitLog.RunRecord hoveredRun = null;
        g.enableScissor(rightX, rightY, rightX + rightW, rightY + contentH);
        for (int i = 0; i < visibleCount && firstRow + i < displayedRuns.size(); i++) {
            int index = firstRow + i;
            DungeonProfitLog.RunRecord run = displayedRuns.get(index);
            int rowY = rightY + i * rowH - (int) (scrollAmount % rowH);

            boolean rowHovered = mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= rowY && mouseY <= rowY + rowH - 1;
            if (rowHovered) hoveredRun = run;
            g.fill(rightX, rowY, rightX + rightW, rowY + rowH - 1, rowHovered ? 0x22334155 : (index % 2 == 0 ? 0x0A0F172A : 0x1A1E293B));

            String timeStr = TIME_FMT.format(new Date(run.timestamp));
            String floorStr = "§e" + run.floor;
            String chestStr = "§7" + run.chest;
            String kismetStr = run.kismetUsed ? " §d[K]" : "";
            String profitStr = (run.netProfit >= 0 ? "§a+" : "§c") + LowestBinManager.formatPrice(run.netProfit);
            String costStr = "§8cost: §6" + LowestBinManager.formatPrice(run.cost);

            g.text(font, "§8" + timeStr, rightX + 6, rowY + 7, 0xFF64748B, false);
            g.text(font, floorStr + " " + chestStr + kismetStr, rightX + 80, rowY + 7, 0xFFE2E8F0, false);
            g.text(font, costStr, rightX + rightW - 170, rowY + 7, 0xFF94A3B8, false);

            int pW = font.width(profitStr);
            g.text(font, profitStr, rightX + rightW - 10 - pW, rowY + 7, 0xFFFFFFFF, false);
        }
        g.disableScissor();

        // Scrollbar for right pane
        if (maxScroll > 0) {
            int scrollbarX = rightX + rightW - 5;
            g.fill(scrollbarX, rightY, scrollbarX + 4, rightY + contentH, 0x22FFFFFF);
            int thumbH = Math.max(16, (int) ((contentH / (float) totalH) * contentH));
            int thumbY = rightY + (int) ((scrollAmount / maxScroll) * (contentH - thumbH));
            g.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, 0x88D946EF);
        }

        if (hoveredRun != null) {
            List<dev.vy.betterpv.client.gui.PvTooltip.Line> lines = new ArrayList<>();
            lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.title(hoveredRun.floor + " - " + hoveredRun.chest + (hoveredRun.kismetUsed ? " [Kismet]" : ""), 0xFFFFAA00));
            lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.divider());
            lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.row("Cost:", 0xFF94A3B8, LowestBinManager.formatPrice(hoveredRun.cost), 0xFFFFAA00));
            lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.row("Contents Value:", 0xFF94A3B8, LowestBinManager.formatPrice(hoveredRun.contentsValue), 0xFFE2E8F0));
            int hProfitColor = hoveredRun.netProfit >= 0 ? 0xFF10B981 : 0xFFEF4444;
            String sign = hoveredRun.netProfit >= 0 ? "+" : "";
            lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.row("Net Profit:", 0xFF94A3B8, sign + LowestBinManager.formatPrice(hoveredRun.netProfit), hProfitColor));
            if (hoveredRun.durationMs > 0) {
                lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.meta("Duration: " + (hoveredRun.durationMs / 1000) + "s | Source: " + hoveredRun.source));
            }
            lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.divider());
            if (hoveredRun.items == null || hoveredRun.items.isEmpty()) {
                lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.meta("No items recorded"));
            } else {
                for (DungeonProfitLog.ItemLine item : hoveredRun.items) {
                    String countStr = item.quantity > 1 ? " x" + item.quantity : "";
                    String itemVal = LowestBinManager.formatPrice(item.totalValue);
                    lines.add(dev.vy.betterpv.client.gui.PvTooltip.Line.row(item.name + countStr, 0xFFE2E8F0, itemVal, 0xFF86EFAC));
                }
            }
            dev.vy.betterpv.client.gui.PvTooltip.drawStyled(g, font, lines, mouseX, mouseY, this.width, this.height);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (maxScroll > 0) {
            scrollAmount -= amountY * 26.0D;
            scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() != 0) return super.mouseClicked(event, handled);

        int winW = Math.min(680, this.width - 30);
        int winH = Math.min(420, this.height - 30);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int syncBtnW = 76;
        int syncBtnX = winX + winW - 146;
        int syncBtnY = winY + 12;
        if (event.x() >= syncBtnX && event.x() <= syncBtnX + syncBtnW && event.y() >= syncBtnY && event.y() <= syncBtnY + 20) {
            DungeonProfitLog.syncPending(null);
            return true;
        }

        int closeBtnW = 56;
        int closeBtnX = winX + winW - 64;
        int closeBtnY = winY + 12;
        if (event.x() >= closeBtnX && event.x() <= closeBtnX + closeBtnW && event.y() >= closeBtnY && event.y() <= closeBtnY + 20) {
            this.onClose();
            return true;
        }

        // Left side Floor filter clicks
        int leftW = 180;
        int leftX = winX + 10;
        int leftY = winY + 58 + 8;
        int flY = leftY + 22;

        if (event.x() >= leftX + 4 && event.x() <= leftX + leftW - 4 && event.y() >= flY && event.y() <= flY + 14) {
            selectedFloorFilter = "All";
            scrollAmount = 0;
            return true;
        }
        flY += 16;

        Map<String, FloorAggregate> floorMap = new LinkedHashMap<>();
        for (DungeonProfitLog.RunRecord r : DungeonProfitLog.getRuns()) {
            String fl = r.floor != null && !r.floor.isBlank() ? r.floor : "Unknown";
            floorMap.computeIfAbsent(fl, k -> new FloorAggregate());
        }
        List<String> sortedKeys = new ArrayList<>(floorMap.keySet());
        sortedKeys.sort((a, b) -> Integer.compare(DungeonProfitLog.floorSortKey(a), DungeonProfitLog.floorSortKey(b)));

        for (String k : sortedKeys) {
            if (event.x() >= leftX + 4 && event.x() <= leftX + leftW - 4 && event.y() >= flY && event.y() <= flY + 14) {
                selectedFloorFilter = k;
                scrollAmount = 0;
                return true;
            }
            flY += 16;
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            scrollAmount = 0;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            scrollAmount = maxScroll;
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
