package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.MayorManager;
import me.bombo.bomboaddons.features.PriceHistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.*;

public class PriceHistoryScreen extends Screen {

    private final Screen parent;
    private final ItemStack targetStack;
    private final String skyblockId;
    private final String displayName;

    private String selectedRange = "30d"; // 1d, 7d, 30d, all
    private PriceHistoryManager.ItemHistoryData historyData;
    private boolean loading = true;

    // Zoom & Pan state
    private double zoomFactor = 1.0; // 1.0 = full range, > 1.0 = zoomed in
    private double panCenterPct = 0.5; // 0.0 to 1.0 (center of visible window)
    private boolean isDraggingGraph = false;
    private double lastDragMouseX = 0;

    // Cache for 150+ FPS graph rasterization
    private int[] cachedTopY = null;
    private int cachedW = -1;
    private int cachedH = -1;
    private int cachedPointsCount = 0;
    private long cachedViewMinT = -1;
    private long cachedViewMaxT = -1;
    private double cachedMinP = -1;
    private double cachedMaxP = -1;
    private List<PriceHistoryManager.PricePoint> cachedRenderPoints = null;
    private List<MayorManager.MayorTerm> cachedMayors = Collections.emptyList();

    private static final SimpleDateFormat FULL_DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.ROOT);
    private static final SimpleDateFormat DAY_DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy", Locale.ROOT);
    private static final SimpleDateFormat HOUR_DATE_FORMAT = new SimpleDateFormat("HH:mm", Locale.ROOT);
    private static final SimpleDateFormat REAL_YEAR_FORMAT = new SimpleDateFormat("yyyy", Locale.ROOT);

    public PriceHistoryScreen(Screen parent, ItemStack stack) {
        super(Component.literal("Price History"));
        this.parent = parent;
        this.targetStack = (stack != null && !stack.isEmpty()) ? stack.copy() : ItemStack.EMPTY;
        String id = SkyblockUtils.getSkyblockId(this.targetStack);
        if (id == null || id.isEmpty()) {
            id = (stack != null && !stack.isEmpty()) ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase() : "UNKNOWN";
        }
        this.skyblockId = id;
        this.displayName = (stack != null && !stack.isEmpty()) ? stack.getHoverName().getString() : id;
    }

    @Override
    protected void init() {
        super.init();
        loadData();
    }

    private void loadData() {
        this.loading = true;
        this.zoomFactor = 1.0;
        this.panCenterPct = 0.5;
        PriceHistoryManager.fetchHistory(this.skyblockId, this.selectedRange).thenAccept(data -> {
            this.historyData = data;
            this.loading = false;
        });
    }

    private static int parseHexColor(String hex, int defaultCol) {
        if (hex == null || hex.trim().isEmpty()) return defaultCol;
        try {
            String clean = hex.trim().replace("#", "");
            if (clean.length() == 6) {
                return 0xFF000000 | Integer.parseInt(clean, 16);
            } else if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (Exception ignored) {}
        return defaultCol;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Dark translucent overlay
        g.fill(0, 0, this.width, this.height, 0xAA000000);

        int winW = Math.min(620, this.width - 30);
        int winH = Math.min(410, this.height - 30);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        BomboConfig.Settings s = BomboConfig.get();
        int customBg = (s != null) ? parseHexColor(s.priceHistoryBgColor, 0x120824) : 0x120824;
        int customBorder = (s != null) ? parseHexColor(s.priceHistoryBorderColor, 0x7C3AED) : 0x7C3AED;
        int customLine = (s != null) ? parseHexColor(s.priceHistoryLineColor, 0xA855F7) : 0xA855F7;
        boolean showMayors = (s == null) || s.priceHistoryShowMayors;

        int cardBg = 0xF2000000 | (customBg & 0x00FFFFFF);
        int headerBg = 0xFF000000 | (((customBg >> 1) & 0x7F7F7F) + 0x100518);

        // Card frame
        g.fill(winX, winY, winX + winW, winY + winH, cardBg);
        g.outline(winX, winY, winW, winH, customBorder);

        // Header bar
        g.fill(winX, winY, winX + winW, winY + 38, headerBg);
        g.outline(winX, winY, winW, 38, (customBorder & 0x00FFFFFF) | 0x88000000);

        // Item Icon & Name
        if (!this.targetStack.isEmpty()) {
            g.item(this.targetStack, winX + 10, winY + 11);
        }
        g.text(this.font, "§e" + this.displayName, winX + 34, winY + 10, 0xFFFFFFFF, true);
        g.text(this.font, "§8ID: §7" + this.skyblockId, winX + 34, winY + 22, 0xFFC4B5FD, false);

        // Current Price summary on top right
        long binPrice = LowestBinManager.getCachedPrice(this.skyblockId);
        long bzBuy = LowestBinManager.getBuyPrice(this.skyblockId);
        long bzSell = LowestBinManager.getSellPrice(this.skyblockId);
        long npc = LowestBinManager.getNpcPrice(this.skyblockId);

        String summaryStr = "";
        if (LowestBinManager.isBazaar(this.skyblockId)) {
            summaryStr = "§6BZ: §a" + LowestBinManager.formatPrice(bzBuy) + " §7/ §c" + LowestBinManager.formatPrice(bzSell);
        } else if (binPrice > 0) {
            summaryStr = "§6BIN: §e" + LowestBinManager.formatPrice(binPrice);
        }
        if (npc > 0) {
            summaryStr += (summaryStr.isEmpty() ? "" : " §8| ") + "§6NPC: §f" + LowestBinManager.formatPrice(npc);
        }
        if (!summaryStr.isEmpty()) {
            g.text(this.font, summaryStr, winX + winW - 14 - this.font.width(summaryStr), winY + 14, 0xFFFFFFFF, false);
        }

        // Stats summary row below header
        int statY = winY + 48;
        if (this.historyData != null && !this.historyData.points.isEmpty()) {
            String avg7 = this.historyData.avg7d > 0 ? LowestBinManager.formatPrice(Math.round(this.historyData.avg7d)) : "N/A";
            String avg30 = this.historyData.avg30d > 0 ? LowestBinManager.formatPrice(Math.round(this.historyData.avg30d)) : "N/A";
            String minStr = LowestBinManager.formatPrice(Math.round(this.historyData.minPrice));
            String maxStr = LowestBinManager.formatPrice(Math.round(this.historyData.maxPrice));

            String stats = "§77d Avg: §d" + avg7 + "  §8▪  §730d Avg: §d" + avg30 + "  §8▪  §7Min: §a" + minStr + "  §8▪  §7Max: §e" + maxStr;
            g.text(this.font, stats, winX + 14, statY + 4, 0xFFE2E8F0, false);
        }

        // Range selector buttons (1D, 7D, 30D, ALL) on the right of stat row
        int btnW = 38;
        int btnH = 16;
        int btnY = statY;
        String[] ranges = new String[]{"1d", "7d", "30d", "all"};
        String[] rangeLabels = new String[]{"1D", "7D", "30D", "ALL"};
        int btnStartX = winX + winW - 14 - (btnW + 4) * ranges.length;

        for (int i = 0; i < ranges.length; i++) {
            int bx = btnStartX + i * (btnW + 4);
            boolean active = ranges[i].equalsIgnoreCase(this.selectedRange);
            boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

            int bg = active ? customBorder : (hover ? 0x44A78BFA : 0x221E1B4B);
            int border = active ? 0xFFFFFFFF : (hover ? 0x88A78BFA : 0x444C1D95);
            int textCol = active ? 0xFFFFFFFF : (hover ? 0xFFEDE9FE : 0xFFA78BFA);

            g.fill(bx, btnY, bx + btnW, btnY + btnH, bg);
            g.outline(bx, btnY, btnW, btnH, border);
            g.centeredText(this.font, rangeLabels[i], bx + btnW / 2, btnY + 4, textCol);
        }

        // Zoom indicator / reset badge if zoomed
        if (this.zoomFactor > 1.05) {
            String zoomStr = String.format(Locale.ROOT, "§bZoom: %.1fx §7(Right-click to reset)", this.zoomFactor);
            g.text(this.font, zoomStr, winX + 14, winY + winH - 18, 0xFF94A3B8, false);
        } else if ("all".equalsIgnoreCase(this.selectedRange)) {
            g.text(this.font, "§8💡 Tip: Scroll on chart to zoom, drag to pan", winX + 14, winY + winH - 18, 0xFF64748B, false);
        }

        // Graph Area
        int graphX = winX + 46;
        int graphY = winY + 74;
        int graphW = winW - 60;
        int graphH = winH - 100;

        renderGraph(g, graphX, graphY, graphW, graphH, mouseX, mouseY, customLine, showMayors);
    }

    private void renderGraph(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, int lineColor, boolean showMayors) {
        // Graph background
        g.fill(x, y, x + w, y + h, 0xFF0B0514);
        g.outline(x, y, w, h, 0xFF2A0D45);

        if (this.loading) {
            g.centeredText(this.font, "§7Loading price history...", x + w / 2, y + h / 2 - 4, 0xFFA78BFA);
            return;
        }

        if (this.historyData == null || this.historyData.points.isEmpty()) {
            g.centeredText(this.font, "§cNo price history recorded for this item.", x + w / 2, y + h / 2 - 4, 0xFFEF4444);
            return;
        }

        List<PriceHistoryManager.PricePoint> allPoints = this.historyData.points;
        if (allPoints.size() < 2) {
            g.centeredText(this.font, "§7Not enough data points in this range.", x + w / 2, y + h / 2 - 4, 0xFFA78BFA);
            return;
        }

        long fullMinT = allPoints.get(0).timestamp;
        long fullMaxT = allPoints.get(allPoints.size() - 1).timestamp;
        if (fullMaxT <= fullMinT) fullMaxT = fullMinT + 1;

        // Calculate visible time window [viewMinT, viewMaxT] based on zoom and pan
        long totalSpan = fullMaxT - fullMinT;
        long viewSpan = Math.max(3600000L, (long) (totalSpan / this.zoomFactor));
        long centerT = fullMinT + (long) (this.panCenterPct * totalSpan);
        long viewMinT = Math.max(fullMinT, centerT - viewSpan / 2);
        long viewMaxT = Math.min(fullMaxT, viewMinT + viewSpan);
        if (viewMaxT - viewMinT < viewSpan && viewMinT > fullMinT) {
            viewMinT = Math.max(fullMinT, viewMaxT - viewSpan);
        }

        // Check if graph geometry needs recomputation
        boolean needRecompute = (cachedTopY == null || cachedW != w || cachedH != h
                || cachedViewMinT != viewMinT || cachedViewMaxT != viewMaxT
                || cachedPointsCount != allPoints.size() || cachedRenderPoints == null);

        List<PriceHistoryManager.PricePoint> renderPoints;
        double minP;
        double maxP;
        int[] topYArr;

        if (needRecompute) {
            // Filter points within visible window (with 1 point boundary on each side for smooth lines)
            List<PriceHistoryManager.PricePoint> visiblePoints = new ArrayList<>();
            PriceHistoryManager.PricePoint lastBefore = null;
            PriceHistoryManager.PricePoint firstAfter = null;

            for (PriceHistoryManager.PricePoint p : allPoints) {
                if (p.timestamp < viewMinT) {
                    lastBefore = p;
                } else if (p.timestamp > viewMaxT) {
                    if (firstAfter == null) firstAfter = p;
                } else {
                    visiblePoints.add(p);
                }
            }

            renderPoints = new ArrayList<>();
            if (lastBefore != null) renderPoints.add(lastBefore);
            renderPoints.addAll(visiblePoints);
            if (firstAfter != null) renderPoints.add(firstAfter);

            if (renderPoints.size() < 2) {
                renderPoints = allPoints;
                viewMinT = fullMinT;
                viewMaxT = fullMaxT;
            }

            minP = Double.MAX_VALUE;
            maxP = 0;
            for (PriceHistoryManager.PricePoint p : renderPoints) {
                if (p.price < minP) minP = p.price;
                if (p.price > maxP) maxP = p.price;
            }
            if (maxP <= minP) maxP = minP + 1.0;

            topYArr = new int[w];
            Arrays.fill(topYArr, -1);

            for (int i = 0; i < renderPoints.size() - 1; i++) {
                PriceHistoryManager.PricePoint pt0 = renderPoints.get(i);
                PriceHistoryManager.PricePoint pt1 = renderPoints.get(i + 1);
                int x0 = x + (int) (((double) (pt0.timestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
                int y0 = y + h - (int) (((pt0.price - minP) / (maxP - minP)) * (h - 14)) - 7;
                int x1 = x + (int) (((double) (pt1.timestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
                int y1 = y + h - (int) (((pt1.price - minP) / (maxP - minP)) * (h - 14)) - 7;

                int drawStart = Math.max(x, Math.min(x + w - 1, Math.min(x0, x1)));
                int drawEnd = Math.max(x, Math.min(x + w - 1, Math.max(x0, x1)));

                if (x1 == x0) {
                    int col = drawStart - x;
                    int minY = Math.max(y, Math.min(y + h, Math.min(y0, y1)));
                    if (topYArr[col] == -1 || minY < topYArr[col]) {
                        topYArr[col] = minY;
                    }
                } else {
                    for (int colX = drawStart; colX <= drawEnd; colX++) {
                        float t = (float) (colX - x0) / (x1 - x0);
                        int topY = (int) (y0 + t * (y1 - y0));
                        topY = Math.max(y, Math.min(y + h, topY));
                        int col = colX - x;
                        if (topYArr[col] == -1 || topY < topYArr[col]) {
                            topYArr[col] = topY;
                        }
                    }
                }
            }

            cachedTopY = topYArr;
            cachedW = w;
            cachedH = h;
            cachedViewMinT = viewMinT;
            cachedViewMaxT = viewMaxT;
            cachedPointsCount = allPoints.size();
            cachedMinP = minP;
            cachedMaxP = maxP;
            cachedRenderPoints = renderPoints;
            if (showMayors && viewMaxT > fullMinT) {
                cachedMayors = MayorManager.getMayorsForTimeRange(viewMinT, viewMaxT);
            } else {
                cachedMayors = Collections.emptyList();
            }
        } else {
            renderPoints = cachedRenderPoints;
            minP = cachedMinP;
            maxP = cachedMaxP;
            topYArr = cachedTopY;
        }

        // 1. Render Mayor Timeline Zones (Inside scissor box)
        g.enableScissor(x, y, x + w, y + h);
        MayorManager.MayorTerm hoveredMayor = null;
        if (showMayors && !cachedMayors.isEmpty()) {
            for (MayorManager.MayorTerm mayor : cachedMayors) {
                int mx0 = x + (int) (((double) (mayor.startTimestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
                int mx1 = x + (int) (((double) (mayor.endTimestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
                int clampX0 = Math.max(x, Math.min(x + w, mx0));
                int clampX1 = Math.max(x, Math.min(x + w, mx1));

                if (clampX1 > clampX0) {
                    g.fill(clampX0, y, clampX1, y + h, mayor.color);
                    // Separator line
                    g.fill(clampX1 - 1, y, clampX1, y + h, 0x33FFFFFF);

                    // Mayor name tag if wide enough
                    if (clampX1 - clampX0 > 48) {
                        int tagCol = MayorManager.getMayorSolidColor(mayor.name);
                        String tagText = mayor.name;
                        if (this.font.width(tagText) > (clampX1 - clampX0 - 4)) {
                            tagText = REAL_YEAR_FORMAT.format(new Date(mayor.startTimestamp));
                        }
                        g.centeredText(this.font, "§l" + tagText, (clampX0 + clampX1) / 2, y + 4, tagCol);
                    }

                    if (mouseX >= clampX0 && mouseX <= clampX1 && mouseY >= y && mouseY <= y + h) {
                        hoveredMayor = mayor;
                    }
                }
            }
        }

        // 2. Grid lines (Inside scissor box)
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            int ly = y + h - (int) ((double) i / gridLines * (h - 10)) - 5;
            g.fill(x, ly, x + w, ly + 1, 0x22581C87);
        }

        // Area fill below curve
        int bottomY = y + h - 1;
        int fillColor = 0x18000000 | (lineColor & 0x00FFFFFF);
        for (int col = 0; col < w; col++) {
            int curY = topYArr[col];
            if (curY != -1 && bottomY > curY) {
                g.fill(x + col, curY, x + col + 1, bottomY, fillColor);
            }
        }

        // Smooth continuous curve line connecting consecutive data points
        for (int i = 0; i < renderPoints.size() - 1; i++) {
            PriceHistoryManager.PricePoint pt0 = renderPoints.get(i);
            PriceHistoryManager.PricePoint pt1 = renderPoints.get(i + 1);
            int px0 = x + (int) (((double) (pt0.timestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
            int py0 = y + h - (int) (((pt0.price - minP) / (maxP - minP)) * (h - 14)) - 7;
            int px1 = x + (int) (((double) (pt1.timestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
            int py1 = y + h - (int) (((pt1.price - minP) / (maxP - minP)) * (h - 14)) - 7;
            drawThickLine(g, px0, py0, px1, py1, lineColor);
        }

        // Nearest point hover tracking (O(log N) binary search instead of O(N))
        PriceHistoryManager.PricePoint closestPoint = null;
        int closestDistSq = Integer.MAX_VALUE;
        int closestPx = -1;
        int closestPy = -1;

        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h && !renderPoints.isEmpty()) {
            double rel = Math.max(0.0, Math.min(1.0, (double) (mouseX - x - 5) / (w - 10)));
            long targetT = viewMinT + (long) (rel * (viewMaxT - viewMinT));
            int idx = Collections.binarySearch(renderPoints, new PriceHistoryManager.PricePoint(targetT, 0), Comparator.comparingLong(p -> p.timestamp));
            if (idx < 0) idx = -idx - 1;
            int startIdx = Math.max(0, idx - 4);
            int endIdx = Math.min(renderPoints.size() - 1, idx + 4);
            for (int i = startIdx; i <= endIdx; i++) {
                PriceHistoryManager.PricePoint pt = renderPoints.get(i);
                int px = x + (int) (((double) (pt.timestamp - viewMinT) / (viewMaxT - viewMinT)) * (w - 10)) + 5;
                int py = y + h - (int) (((pt.price - minP) / (maxP - minP)) * (h - 14)) - 7;
                int dx = mouseX - px;
                int dy = mouseY - py;
                int distSq = dx * dx + dy * dy;
                if (distSq < closestDistSq && Math.abs(dx) <= 24) {
                    closestDistSq = distSq;
                    closestPoint = pt;
                    closestPx = px;
                    closestPy = py;
                }
            }
        }

        g.disableScissor();

        // 2b. Price labels rendered outside scissor so text never gets clipped
        for (int i = 0; i <= gridLines; i++) {
            int ly = y + h - (int) ((double) i / gridLines * (h - 10)) - 5;
            double priceVal = minP + ((double) i / gridLines) * (maxP - minP);
            String pStr = LowestBinManager.formatPrice(Math.round(priceVal));
            g.text(this.font, "§8" + pStr, x - 6 - this.font.width(pStr), ly - 4, 0xFF8B5CF6, false);
        }

        // 2c. Bottom Timeline Labels & SkyBlock Years
        int timelineY = y + h + 3;
        int numTicks = Math.max(3, Math.min(6, w / 90));
        for (int tIdx = 0; tIdx < numTicks; tIdx++) {
            double pct = (double) tIdx / (numTicks - 1);
            long tickT = (long) (viewMinT + pct * (viewMaxT - viewMinT));
            int tickX = x + (int) (pct * (w - 10)) + 5;

            String label;
            if ("all".equalsIgnoreCase(this.selectedRange)) {
                label = REAL_YEAR_FORMAT.format(new Date(tickT));
            } else if ("1d".equalsIgnoreCase(this.selectedRange)) {
                label = HOUR_DATE_FORMAT.format(new Date(tickT));
            } else {
                label = DAY_DATE_FORMAT.format(new Date(tickT));
            }

            int lblW = this.font.width(label);
            int drawLblX = Math.max(x, Math.min(x + w - lblW, tickX - lblW / 2));
            g.fill(tickX, y + h, tickX + 1, y + h + 2, 0x55A78BFA);
            g.text(this.font, "§8" + label, drawLblX, timelineY, 0xFF94A3B8, false);
        }

        // 5. Highlight hovered point & render tooltip
        if (closestPoint != null) {
            g.fill(closestPx, y, closestPx + 1, y + h, 0x44A855F7);
            g.fill(closestPx - 4, closestPy - 4, closestPx + 5, closestPy + 5, lineColor);
            g.fill(closestPx - 2, closestPy - 2, closestPx + 3, closestPy + 3, 0xFFFFFFFF);

            SimpleDateFormat fmt = "1d".equalsIgnoreCase(this.selectedRange) ? FULL_DATE_FORMAT : ("all".equalsIgnoreCase(this.selectedRange) ? FULL_DATE_FORMAT : DAY_DATE_FORMAT);
            String dateStr = fmt.format(new Date(closestPoint.timestamp));
            String priceStr = "§e" + LowestBinManager.formatPrice(Math.round(closestPoint.price)) + " coins";

            MayorManager.MayorTerm ptMayor = MayorManager.getMayorForTimestamp(closestPoint.timestamp);
            String mayorStr = (showMayors && ptMayor != null && !"unknown".equals(ptMayor.key)) ? ("§6Mayor: §e" + ptMayor.name + " §7(" + REAL_YEAR_FORMAT.format(new Date(closestPoint.timestamp)) + ")") : null;

            int tw = Math.max(this.font.width(dateStr), this.font.width(priceStr));
            if (mayorStr != null) tw = Math.max(tw, this.font.width(mayorStr));
            tw += 16;
            int th = mayorStr != null ? 38 : 28;
            int tx = Math.min(x + w - tw - 4, Math.max(x + 4, closestPx - tw / 2));
            int ty = (closestPy - th - 8 < y) ? closestPy + 10 : closestPy - th - 8;

            g.fill(tx, ty, tx + tw, ty + th, 0xF215092A);
            g.outline(tx, ty, tw, th, lineColor);
            g.text(this.font, "§7" + dateStr, tx + 6, ty + 4, 0xFFC4B5FD, false);
            g.text(this.font, priceStr, tx + 6, ty + 15, 0xFFFFFFFF, true);
            if (mayorStr != null) {
                g.text(this.font, mayorStr, tx + 6, ty + 26, 0xFFFCD34D, false);
            }
        } else if (hoveredMayor != null && !"unknown".equals(hoveredMayor.key)) {
            // Hovered Mayor Badge Card
            String title = "§6Mayor " + hoveredMayor.name + " §7(" + REAL_YEAR_FORMAT.format(new Date(hoveredMayor.startTimestamp)) + ")";
            int tw = this.font.width(title);
            for (MayorManager.MayorPerk p : hoveredMayor.perks) {
                tw = Math.max(tw, this.font.width("§e• " + p.name + ": §7" + p.description));
            }
            tw = Math.min(w - 20, Math.max(120, tw + 16));
            int th = 18 + hoveredMayor.perks.size() * 12 + 4;
            int tx = Math.min(x + w - tw - 4, Math.max(x + 4, mouseX - tw / 2));
            int ty = y + 16;

            g.fill(tx, ty, tx + tw, ty + th, 0xEE1A0C2E);
            g.outline(tx, ty, tw, th, MayorManager.getMayorSolidColor(hoveredMayor.name));
            g.text(this.font, title, tx + 6, ty + 4, 0xFFFFFFFF, true);

            int py = ty + 16;
            for (MayorManager.MayorPerk p : hoveredMayor.perks) {
                String perkLine = "§e• " + p.name;
                if (!p.description.isEmpty()) perkLine += " §7- " + p.description;
                if (this.font.width(perkLine) > tw - 12) {
                    perkLine = perkLine.substring(0, Math.min(perkLine.length(), 40)) + "...";
                }
                g.text(this.font, perkLine, tx + 6, py, 0xFFE2E8F0, false);
                py += 12;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            int winW = Math.min(620, this.width - 30);
            int winH = Math.min(410, this.height - 30);
            int winX = (this.width - winW) / 2;
            int winY = (this.height - winH) / 2;
            int graphX = winX + 46;
            int graphW = winW - 60;
            int graphY = winY + 74;
            int graphH = winH - 100;

            if (mouseX >= graphX && mouseX <= graphX + graphW && mouseY >= graphY && mouseY <= graphY + graphH) {
                double mousePct = Math.max(0.0, Math.min(1.0, (mouseX - graphX) / graphW));
                if (verticalAmount > 0) {
                    // Zoom IN
                    this.zoomFactor = Math.min(30.0, this.zoomFactor * 1.3);
                } else {
                    // Zoom OUT
                    this.zoomFactor = Math.max(1.0, this.zoomFactor / 1.3);
                }
                if (this.zoomFactor <= 1.05) {
                    this.zoomFactor = 1.0;
                    this.panCenterPct = 0.5;
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.isDraggingGraph && this.zoomFactor > 1.0) {
            int winW = Math.min(620, this.width - 30);
            int graphW = winW - 60;
            double pctDelta = -deltaX / (graphW * this.zoomFactor);
            this.panCenterPct = Math.max(0.0, Math.min(1.0, this.panCenterPct + pctDelta));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDraggingGraph = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        int winW = Math.min(620, this.width - 30);
        int winH = Math.min(410, this.height - 30);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int statY = winY + 48;
        int btnW = 38;
        int btnH = 16;
        int btnY = statY;
        String[] ranges = new String[]{"1d", "7d", "30d", "all"};
        int btnStartX = winX + winW - 14 - (btnW + 4) * ranges.length;

        for (int i = 0; i < ranges.length; i++) {
            int bx = btnStartX + i * (btnW + 4);
            if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                this.selectedRange = ranges[i];
                loadData();
                return true;
            }
        }

        // Graph area interaction
        int graphX = winX + 46;
        int graphY = winY + 74;
        int graphW = winW - 60;
        int graphH = winH - 100;

        if (mouseX >= graphX && mouseX <= graphX + graphW && mouseY >= graphY && mouseY <= graphY + graphH) {
            if (event.button() == 1) { // Right-click -> Reset zoom
                this.zoomFactor = 1.0;
                this.panCenterPct = 0.5;
                return true;
            } else if (event.button() == 0) {
                this.isDraggingGraph = true;
                this.lastDragMouseX = mouseX;
                return true;
            }
        }

        // Clicking outside closes the window
        if (mouseX < winX || mouseX > winX + winW || mouseY < winY || mouseY > winY + winH) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawThickLine(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            g.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
