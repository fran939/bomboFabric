package me.bombo.bomboaddons.gui;

import java.text.SimpleDateFormat;
import java.util.*;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.features.inferno.InfernoMinionTracker;
import me.bombo.bomboaddons.features.inferno.SacksHelper;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class InfernoTrackerScreen extends Screen {
    private final Screen parent;
    private int currentTab = 0; // 0: Drops, 1: Fuel, 2: Minions, 3: Activity Log
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private boolean isDraggingScrollbar = false;
    private boolean isSyncing = false;
    private String syncStatus = null;

    // Editing passkey modal
    private boolean editingPasskey = false;
    private EditBox passkeyEditBox = null;

    // Activity detail modal & grouped activity state
    public static class ActivityGroup {
        public String player = "Unknown";
        public String type = "CLAIM"; // "CLAIM" or "FUEL"
        public long startTime = 0L;
        public long endTime = 0L;
        public int minionCount = 0;
        public int itemCount = 0;
        public long totalRevenue = 0L;
        public long totalCost = 0L;
        public Map<String, Integer> drops = new LinkedHashMap<>();
        public List<InfernoMinionTracker.ActionEntry> subActions = new ArrayList<>();
    }

    private ActivityGroup selectedActivity = null;
    private double detailModalScroll = 0.0;
    private double detailModalMaxScroll = 0.0;
    private static final SimpleDateFormat DETAIL_TIME_FMT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT);
    public static final int TAB_DROPS = 0;
    public static final int TAB_FUEL = 1;
    public static final int TAB_MINIONS = 2;
    public static final int TAB_ACTIVITY = 3;
    public static final int TAB_CRAFT = 4;

    private static final String[] TAB_NAMES = {"📦 Drops & Revenue", "⛽ Fuel & Costs", "🤖 Tracked Minions", "📜 Activity Log", "🔨 Fuel Craft"};
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT);

    public InfernoTrackerScreen(Screen parent) {
        super(Component.literal("Inferno Minion Tracker"));
        this.parent = parent;
    }

    public void setActiveTab(int tab) {
        this.currentTab = Math.max(0, Math.min(tab, TAB_NAMES.length - 1));
    }

    @Override
    protected void init() {
        super.init();
        InfernoMinionTracker.ensureLoaded();
        InfernoMinionTracker.syncWithServer(null);
        me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.refreshSacks();

        if (this.editingPasskey) {
            int winW = Math.min(this.width - 40, 680);
            int winX = (this.width - winW) / 2;
            int boxY = (this.height - 30) / 2;
            int boxW = 220;
            int boxX = winX + (winW - boxW) / 2;
            this.passkeyEditBox = new EditBox(this.font, boxX, boxY, boxW, 20, Component.literal("Coop Passkey"));
            this.passkeyEditBox.setMaxLength(64);
            this.passkeyEditBox.setValue(InfernoMinionTracker.getPasskey());
            this.addWidget(this.passkeyEditBox);
            this.setFocused(this.passkeyEditBox);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Darkened glass background
        g.fill(0, 0, this.width, this.height, 0xD00A0D14);

        int winW = Math.min(this.width - 40, 720);
        int winH = Math.min(this.height - 40, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int bg = 0xF0111827; // Deep obsidian dark slate
        int border = 0xFF374151; // Slate border
        int cardBg = 0x801F2937; // Elevated translucent card
        Font font = this.font;

        // Window outline & panel
        g.fill(winX, winY, winX + winW, winY + winH, bg);
        g.outline(winX, winY, winW, winH, border);

        // Header
        String curPass = InfernoMinionTracker.getPasskey();
        String currentPid = me.bombo.bomboaddons.SkyblockUtils.currentProfileId != null ? me.bombo.bomboaddons.SkyblockUtils.currentProfileId.trim() : "";
        boolean isLinkedToPid = !currentPid.isEmpty() && curPass.equalsIgnoreCase(currentPid);

        g.text(font, "§6§l🔥 INFERNO MINION TRACKER", winX + 16, winY + 14, 0xFFFFFFFF, true);
        String subLabel = "§7Coop: " + (isLinkedToPid ? "§a[Profile Linked] §e" : "§e") + curPass + " §8[Click to Edit]";
        g.text(font, subLabel, winX + 16, winY + 27, 0xFFA0AEC0, false);

        int linkBtnX = winX + 16 + font.width(subLabel) + 6;
        int linkBtnY = winY + 24;
        int linkBtnW = 95;
        int linkBtnH = 14;
        if (!currentPid.isEmpty() && !isLinkedToPid) {
            boolean linkHovered = mouseX >= linkBtnX && mouseX <= linkBtnX + linkBtnW && mouseY >= linkBtnY && mouseY <= linkBtnY + linkBtnH;
            g.fill(linkBtnX, linkBtnY, linkBtnX + linkBtnW, linkBtnY + linkBtnH, linkHovered ? 0xFF0284C7 : 0xFF0369A1);
            g.outline(linkBtnX, linkBtnY, linkBtnW, linkBtnH, 0xFF38BDF8);
            g.centeredText(font, "§f⚡ Link Profile ID", linkBtnX + linkBtnW / 2, linkBtnY + 3, 0xFFFFFFFF);
        }

        // Top Action Buttons
        int btnY = winY + 12;
        int btnH = 20;

        // [✕ Close]
        int closeW = 24;
        int closeX = winX + winW - closeW - 14;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(closeX, btnY, closeX + closeW, btnY + btnH, closeHovered ? 0xFFEF4444 : 0xFF2D3748);
        g.outline(closeX, btnY, closeW, btnH, 0xFF4A5568);
        g.centeredText(font, "✕", closeX + closeW / 2, btnY + 5, 0xFFFFFFFF);

        // [🔄 Sync]
        int syncW = 60;
        int syncX = closeX - syncW - 6;
        boolean syncHovered = mouseX >= syncX && mouseX <= syncX + syncW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(syncX, btnY, syncX + syncW, btnY + btnH, syncHovered ? 0xFF2563EB : 0xFF1E293B);
        g.outline(syncX, btnY, syncW, btnH, 0xFF3B82F6);
        g.centeredText(font, isSyncing ? "§e..." : "§b🔄 Sync", syncX + syncW / 2, btnY + 5, 0xFFFFFFFF);

        // [🗑 Reset]
        int resetW = 64;
        int resetX = syncX - resetW - 6;
        boolean resetHovered = mouseX >= resetX && mouseX <= resetX + resetW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(resetX, btnY, resetX + resetW, btnY + btnH, resetHovered ? 0xFFB91C1C : 0xFF1E293B);
        g.outline(resetX, btnY, resetW, btnH, 0xFFEF4444);
        g.centeredText(font, "§c🗑 Reset", resetX + resetW / 2, btnY + 5, 0xFFFFFFFF);

        // [📋 Copy]
        int copyW = 62;
        int copyX = resetX - copyW - 6;
        boolean copyHovered = mouseX >= copyX && mouseX <= copyX + copyW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(copyX, btnY, copyX + copyW, btnY + btnH, copyHovered ? 0xFF374151 : 0xFF1E293B);
        g.outline(copyX, btnY, copyW, btnH, 0xFF4B5563);
        g.centeredText(font, "§7📋 Copy", copyX + copyW / 2, btnY + 5, 0xFFFFFFFF);

        // 4 Summary Metric Cards
        InfernoMinionTracker.TrackerState s = InfernoMinionTracker.getState();
        int cardsY = winY + 44;
        int cardH = 46;
        int gap = 8;
        int cardW = (winW - 32 - gap * 3) / 4;

        // Card 1: Net Profit
        int c1X = winX + 16;
        int profitColor = s.netProfit >= 0 ? 0xFF10B981 : 0xFFEF4444;
        drawMetricCard(g, font, c1X, cardsY, cardW, cardH, "NET PROFIT", (s.netProfit >= 0 ? "+" : "") + InfernoMinionTracker.formatCoins(s.netProfit) + " coins", profitColor, cardBg, border);

        // Card 2: Total Revenue
        int c2X = c1X + cardW + gap;
        drawMetricCard(g, font, c2X, cardsY, cardW, cardH, "GROSS REVENUE", "+" + InfernoMinionTracker.formatCoins(s.totalRevenue) + " coins", 0xFFFBBF24, cardBg, border);

        // Card 3: Fuel Cost
        int c3X = c2X + cardW + gap;
        drawMetricCard(g, font, c3X, cardsY, cardW, cardH, "FUEL EXPENSES", (s.totalCost > 0 ? "-" : "") + InfernoMinionTracker.formatCoins(s.totalCost) + " coins", 0xFFF87171, cardBg, border);

        // Card 4: Fuels Used
        int c4X = c3X + cardW + gap;
        long fuelsCount = s.fuels.getOrDefault(InfernoMinionTracker.HYPERGOLIC_FUEL_ID, 0L);
        drawMetricCard(g, font, c4X, cardsY, cardW, cardH, "FUELS CONSUMED", fuelsCount + " Hypergolic (" + (fuelsCount * 24) + "h)", 0xFFFB923C, cardBg, border);

        // Tabs Header
        int tabsY = cardsY + cardH + 10;
        int tabW = (winW - 32) / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tX = winX + 16 + i * tabW;
            boolean active = (i == this.currentTab);
            boolean hov = mouseX >= tX && mouseX <= tX + tabW - 4 && mouseY >= tabsY && mouseY <= tabsY + 22;

            int tabBg = active ? 0xFF2563EB : (hov ? 0x60374151 : 0x301F2937);
            g.fill(tX, tabsY, tX + tabW - 4, tabsY + 22, tabBg);
            g.outline(tX, tabsY, tabW - 4, 22, active ? 0xFF60A5FA : 0xFF4B5563);
            g.centeredText(font, (active ? "§f§l" : "§7") + TAB_NAMES[i], tX + (tabW - 4) / 2, tabsY + 6, 0xFFFFFFFF);
        }

        // Content Area with Scissor
        int contentX = winX + 16;
        int contentY = tabsY + 28;
        int contentW = winW - 32;
        int contentH = winH - (contentY - winY) - 14;

        g.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0x40111827);
        g.outline(contentX, contentY, contentW, contentH, border);

        int totalH = 0;
        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        switch (this.currentTab) {
            case 0 -> totalH = renderDropsTab(g, font, contentX + 8, contentY + 8 - (int) this.scrollAmount, contentW - 16, s);
            case 1 -> totalH = renderFuelTab(g, font, contentX + 8, contentY + 8 - (int) this.scrollAmount, contentW - 16, s);
            case 2 -> totalH = renderMinionsTab(g, font, contentX + 8, contentY + 8 - (int) this.scrollAmount, contentW - 16, s);
            case 3 -> totalH = renderActivityTab(g, font, contentX + 8, contentY + 8 - (int) this.scrollAmount, contentW - 16, s, mouseX, mouseY);
            case 4 -> totalH = renderFuelCraftTab(g, font, contentX + 8, contentY + 8 - (int) this.scrollAmount, contentW - 16, mouseX, mouseY);
        }

        g.disableScissor();

        // Calculate scroll bounds
        this.maxScroll = Math.max(0, totalH - contentH + 16);

        // Scrollbar
        if (this.maxScroll > 0) {
            int sbW = 4;
            int sbX = contentX + contentW - sbW - 3;
            int thumbH = Math.max(20, (int) ((float) contentH / totalH * contentH));
            int thumbY = contentY + (int) ((this.scrollAmount / this.maxScroll) * (contentH - thumbH));
            g.fill(sbX, contentY, sbX + sbW, contentY + contentH, 0x33000000);
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF3B82F6);
        }

        // Passkey Edit Modal
        if (this.editingPasskey) {
            renderPasskeyModal(g, font, winX, winY, winW, winH, mouseX, mouseY);
        }

        // Activity Detail Modal
        if (this.selectedActivity != null) {
            renderActivityDetailModal(g, font, winX, winY, winW, winH, mouseX, mouseY);
        }
    }

    private void drawMetricCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, String value, int valueColor, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
        g.text(font, "§8" + title, x + 8, y + 6, 0xFF9CA3AF, false);
        g.text(font, value, x + 8, y + 22, valueColor, true);
    }

    private int renderDropsTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, InfernoMinionTracker.TrackerState s) {
        int curY = y;

        // Table Header
        g.fill(x, curY, x + w, curY + 20, 0xFF1F2937);
        g.outline(x, curY, w, 20, 0xFF374151);
        g.text(font, "§7ITEM DROP", x + 10, curY + 5, 0xFFE5E7EB, false);
        g.text(font, "§7QUANTITY", x + w - 300, curY + 5, 0xFFE5E7EB, false);
        g.text(font, "§7UNIT PRICE", x + w - 190, curY + 5, 0xFFE5E7EB, false);
        g.text(font, "§7TOTAL VALUE", x + w - 85, curY + 5, 0xFFE5E7EB, false);
        curY += 24;

        if (s.drops.isEmpty()) {
            g.centeredText(font, "§7No drops recorded yet. Collect from an Inferno Minion on your island!", x + w / 2, curY + 40, 0xFF9CA3AF);
            return 120;
        }

        // Sort by total value descending
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(s.drops.entrySet());
        sorted.sort((a, b) -> {
            long valA = a.getValue() * InfernoMinionTracker.getDropUnitPrice(a.getKey());
            long valB = b.getValue() * InfernoMinionTracker.getDropUnitPrice(b.getKey());
            return Long.compare(valB, valA);
        });

        int rowH = 26;
        for (Map.Entry<String, Long> entry : sorted) {
            String id = entry.getKey();
            long count = entry.getValue();
            long unitPrice = InfernoMinionTracker.getDropUnitPrice(id);
            long totalVal = count * unitPrice;

            boolean rare = id.contains("APEX") || id.contains("VERTEX") || id.contains("PEPPER") || id.contains("FISH");
            int rowBg = rare ? 0x25F59E0B : 0x151F2937;
            g.fill(x, curY, x + w, curY + rowH, rowBg);
            g.outline(x, curY, w, rowH, rare ? 0x60F59E0B : 0x30374151);

            String displayName = (rare ? "§6§l★ " : "§f") + InfernoMinionTracker.formatDropName(id);
            g.text(font, displayName, x + 10, curY + 8, 0xFFFFFFFF, false);
            g.text(font, "§e" + String.format(Locale.ROOT, "%,d", count), x + w - 300, curY + 8, 0xFFFCD34D, false);
            g.text(font, "§7" + InfernoMinionTracker.formatCoins(unitPrice), x + w - 190, curY + 8, 0xFF9CA3AF, false);
            g.text(font, "§6+" + InfernoMinionTracker.formatCoins(totalVal), x + w - 85, curY + 8, 0xFFFBBF24, true);

            curY += rowH + 4;
        }

        return curY - y;
    }

    private int renderFuelTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, InfernoMinionTracker.TrackerState s) {
        int curY = y;
        long fuelsCount = s.fuels.getOrDefault(InfernoMinionTracker.HYPERGOLIC_FUEL_ID, 0L);
        long craftCost = LowestBinManager.getCraftCostCached(InfernoMinionTracker.HYPERGOLIC_FUEL_ID);
        if (craftCost <= 0) craftCost = 4_320_000L;
        long totalFuelCost = fuelsCount * craftCost;

        // Big Info Banner
        int bannerH = 74;
        g.fill(x, curY, x + w, curY + bannerH, 0x301E3A8A);
        g.outline(x, curY, w, bannerH, 0xFF3B82F6);
        g.text(font, "§b§lHYPERGOLIC CRUDE GABAGOOL FUEL", x + 12, curY + 10, 0xFFFFFFFF, true);
        g.text(font, "§7* Minion Speed: §a20x Multiplier §7| Specialty Production: §eCrude Gabagool (4/5 replacement)", x + 12, curY + 26, 0xFFD1D5DB, false);
        g.text(font, "§7* Rare Drops Enabled: §6Chili Pepper, Inferno Vertex, Inferno Apex, Reaper Pepper", x + 12, curY + 40, 0xFFD1D5DB, false);
        g.text(font, "§7* Single Fuel Duration: §e24 Hours §7| Pricing Mode: §6Raw Craft Cost", x + 12, curY + 54, 0xFFD1D5DB, false);
        curY += bannerH + 12;

        // Stat Row
        int statH = 36;
        g.fill(x, curY, x + w, curY + statH, 0x201F2937);
        g.outline(x, curY, w, statH, 0xFF374151);
        g.text(font, "§fTotal Fuel Placed: §6" + fuelsCount + "x", x + 12, curY + 13, 0xFFFFFFFF, false);
        g.text(font, "§fCraft Cost per Fuel: §c-" + InfernoMinionTracker.formatCoins(craftCost), x + w / 3, curY + 13, 0xFFFFFFFF, false);
        g.text(font, "§fTotal Expense: §c-" + InfernoMinionTracker.formatCoins(totalFuelCost), x + (w * 2) / 3, curY + 13, 0xFFFFFFFF, true);
        curY += statH + 12;

        // History of tracked fuels
        g.text(font, "§7Unique Fuel Instances Tracked (" + s.seenFuelUuids.size() + " registered UUIDs):", x, curY, 0xFF9CA3AF, false);
        int resetBtnW = 76;
        int resetBtnH = 16;
        int resetBtnX = x + w - resetBtnW;
        int resetBtnY = curY - 3;
        g.fill(resetBtnX, resetBtnY, resetBtnX + resetBtnW, resetBtnY + resetBtnH, 0xFF7F1D1D);
        g.outline(resetBtnX, resetBtnY, resetBtnW, resetBtnH, 0xFFEF4444);
        g.centeredText(font, "§cClear Fuels", resetBtnX + resetBtnW / 2, resetBtnY + 4, 0xFFFFFFFF);
        curY += 18;

        if (s.seenFuelUuids.isEmpty()) {
            g.centeredText(font, "§7No fuels placed yet. Insert Hypergolic Gabagool into slot 19 of an Inferno Minion!", x + w / 2, curY + 30, 0xFF9CA3AF);
            return curY - y + 60;
        }

        int itemH = 22;
        int idx = 1;
        for (String uuid : s.seenFuelUuids) {
            g.fill(x, curY, x + w, curY + itemH, 0x151F2937);
            g.outline(x, curY, w, itemH, 0x30374151);
            g.text(font, "§8#" + (idx++) + " §6Hypergolic Gabagool §7UUID: §e" + uuid, x + 8, curY + 6, 0xFFE5E7EB, false);
            g.text(font, "§c-" + InfernoMinionTracker.formatCoins(craftCost) + " coins", x + w - 120, curY + 6, 0xFFF87171, true);
            curY += itemH + 3;
        }

        return curY - y;
    }

    private int renderMinionsTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, InfernoMinionTracker.TrackerState s) {
        int curY = y;
        InfernoMinionTracker.ensureAllMinionsInitialized();

        // Top Summary Header
        int sumH = 34;
        g.fill(x, curY, x + w, curY + sumH, 0xFF1E293B);
        g.outline(x, curY, w, sumH, 0xFF334155);

        long activeCount = s.minions.values().stream().filter(m -> m.totalDrops > 0 || m.totalFuels > 0).count();
        long totalHarvestDrops = s.minions.values().stream().mapToLong(m -> m.totalDrops).sum();
        long totalHarvestFuels = s.minions.values().stream().mapToLong(m -> m.totalFuels).sum();

        g.text(font, "§6§l🤖 32 Inferno Minions Island Grid §7(Row 1: #1-16 @ Z=49.5 | Row 2: #17-32 @ Z=45.5)", x + 10, curY + 7, 0xFFFFFFFF, true);
        g.text(font, "§a" + activeCount + "/32 Active §8| §e" + SacksHelper.formatNumber(totalHarvestDrops) + " §7Drops Claimed §8| §6" + totalHarvestFuels + " §7Fuels Used", x + 10, curY + 20, 0xFFCBD5E1, false);

        curY += sumH + 10;

        // Strictly assemble minions sorted from #1 to #32 with zero legacy duplicates
        List<InfernoMinionTracker.MinionInfo> minionList = new ArrayList<>();
        for (int i = 1; i <= 32; i++) {
            InfernoMinionTracker.MinionInfo m = s.minions.get("minion_" + i);
            if (m == null) {
                m = new InfernoMinionTracker.MinionInfo();
                m.index = i;
                m.uuid = "minion_" + i;
                m.tier = "Inferno Minion #" + i;
                m.posX = InfernoMinionTracker.getMinionTargetX(i);
                m.posY = 103.0;
                m.posZ = InfernoMinionTracker.getMinionTargetZ(i);
                s.minions.put("minion_" + i, m);
            }
            m.index = i;
            m.tier = "Inferno Minion #" + i;
            minionList.add(m);
        }

        int rowH = 44;
        for (InfernoMinionTracker.MinionInfo m : minionList) {
            int row = m.index <= 16 ? 1 : 2;
            int col = m.index <= 16 ? m.index : (m.index - 16);
            double targetX = InfernoMinionTracker.getMinionTargetX(m.index);
            double targetZ = InfernoMinionTracker.getMinionTargetZ(m.index);

            boolean hasActivity = m.totalDrops > 0 || m.totalFuels > 0;
            int rowBg = hasActivity ? 0x251E293B : 0x15111827;
            g.fill(x, curY, x + w, curY + rowH, rowBg);
            g.outline(x, curY, w, rowH, hasActivity ? 0xFF475569 : 0x30374151);

            // Minion title & row/col
            String minionTitle = "§6§l#" + m.index + " " + m.tier + " §7(Row " + row + ", Spot " + col + ")";
            g.text(font, minionTitle, x + 10, curY + 7, 0xFFFFFFFF, false);

            // Exact Coords
            String posStr = String.format(Locale.ROOT, "§7Location: §f%.1f, 103.0, %.1f", targetX, targetZ);
            g.text(font, posStr, x + 10, curY + 24, 0xFF94A3B8, false);

            // Status badge
            String statusBadge;
            int badgeColor;
            if (m.totalFuels > 0) {
                statusBadge = "§a⚡ FUELED (" + m.totalFuels + ")";
                badgeColor = 0xFF10B981;
            } else if (m.totalDrops > 0) {
                statusBadge = "§e📦 HARVESTED";
                badgeColor = 0xFFFCD34D;
            } else {
                statusBadge = "§8💤 READY";
                badgeColor = 0xFF64748B;
            }
            g.text(font, statusBadge, x + w - 320, curY + 8, badgeColor, true);

            // Drops & fuels
            String stats = "§aDrops: §e" + SacksHelper.formatNumber(m.totalDrops) + " §8| §6Fuels: §e" + m.totalFuels;
            g.text(font, stats, x + w - 210, curY + 8, 0xFFE2E8F0, false);

            // Last claim time & player
            String lastTime = m.lastActionTime > 0
                    ? "§7Last: §b" + TIME_FMT.format(new Date(m.lastActionTime)) + (m.lastActionPlayer != null ? " §8(" + m.lastActionPlayer + ")" : "")
                    : "§8Never harvested";
            g.text(font, lastTime, x + w - 210, curY + 24, 0xFF94A3B8, false);

            curY += rowH + 4;
        }

        return curY - y;
    }

    public List<ActivityGroup> groupActions(List<InfernoMinionTracker.ActionEntry> rawActions) {
        List<ActivityGroup> groups = new ArrayList<>();
        if (rawActions == null || rawActions.isEmpty()) return groups;

        ActivityGroup currentGroup = null;
        for (InfernoMinionTracker.ActionEntry a : rawActions) {
            if (a == null) continue;
            String type = a.type != null ? a.type.toUpperCase(Locale.ROOT) : "CLAIM";
            String player = (a.player != null && !a.player.isEmpty()) ? a.player : "Player";
            long time = a.timestamp;

            boolean canMerge = false;
            if (currentGroup != null && currentGroup.type.equals(type) && currentGroup.player.equalsIgnoreCase(player)) {
                // Group contiguous actions by the same player within a 3-minute window (180,000 ms)
                long diff = Math.abs(currentGroup.endTime - time);
                if (diff <= 180000L) {
                    canMerge = true;
                }
            }

            if (!canMerge) {
                currentGroup = new ActivityGroup();
                currentGroup.type = type;
                currentGroup.player = player;
                currentGroup.startTime = time;
                currentGroup.endTime = time;
                groups.add(currentGroup);
            }

            if (time < currentGroup.startTime) currentGroup.startTime = time;
            if (time > currentGroup.endTime) currentGroup.endTime = time;
            currentGroup.minionCount++;
            currentGroup.totalRevenue += a.revenue;
            currentGroup.totalCost += a.cost;
            currentGroup.subActions.add(a);

            if ("CLAIM".equals(type) && a.drops != null) {
                for (Map.Entry<String, Integer> e : a.drops.entrySet()) {
                    currentGroup.drops.put(e.getKey(), currentGroup.drops.getOrDefault(e.getKey(), 0) + e.getValue());
                    currentGroup.itemCount += e.getValue();
                }
            } else if ("FUEL".equals(type)) {
                currentGroup.itemCount++;
            }
        }
        return groups;
    }

    private int renderActivityTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, InfernoMinionTracker.TrackerState s, int mouseX, int mouseY) {
        int curY = y;
        List<ActivityGroup> groups = groupActions(s.actions);
        if (groups.isEmpty()) {
            g.centeredText(font, "§7No coop activity recorded yet.", x + w / 2, curY + 40, 0xFF9CA3AF);
            return 100;
        }

        g.text(font, "§7Recent Activity Sessions (" + groups.size() + " sessions, " + s.actions.size() + " minion actions) §8- Click to inspect:", x, curY, 0xFFE5E7EB, false);
        curY += 16;

        int rowH = 38;
        for (ActivityGroup grp : groups) {
            boolean isClaim = "CLAIM".equalsIgnoreCase(grp.type);
            boolean isHovered = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + rowH;
            int rowBg = isClaim ? (isHovered ? 0x2A10B981 : 0x1510B981) : (isHovered ? 0x2AEF4444 : 0x15EF4444);
            int border = isClaim ? (isHovered ? 0x9010B981 : 0x4010B981) : (isHovered ? 0x90EF4444 : 0x40EF4444);

            g.fill(x, curY, x + w, curY + rowH, rowBg);
            g.outline(x, curY, w, rowH, border);

            // Badge & session title
            String badge = isClaim ? "§a[CLAIM]" : "§c[FUEL]";
            String timeStr = grp.startTime > 0 ? TIME_FMT.format(new Date(grp.startTime)) : "";
            String minionStr = grp.minionCount == 1 ? "1 minion" : (grp.minionCount + " minions");
            String itemStr = grp.itemCount == 1 ? "1 item" : (grp.itemCount + " items");

            if (isClaim) {
                // e.g. bomboclas claimed 24 minions at 10/27 20:36 collecting 1,482 items
                g.text(font, badge + " §f" + grp.player + " §7claimed §e" + minionStr + " §7at §f" + timeStr + " §7collecting §e" + itemStr, x + 10, curY + 6, 0xFFFFFFFF, false);

                StringBuilder desc = new StringBuilder("§7Collected: ");
                if (!grp.drops.isEmpty()) {
                    int i = 0;
                    for (Map.Entry<String, Integer> e : grp.drops.entrySet()) {
                        if (i++ > 0) desc.append(", ");
                        desc.append(e.getValue()).append("x ").append(InfernoMinionTracker.formatDropName(e.getKey()));
                        if (i >= 4 && grp.drops.size() > 4) {
                            desc.append(", +").append(grp.drops.size() - 4).append(" more");
                            break;
                        }
                    }
                }
                desc.append(isHovered ? " §b[Click to view full breakdown]" : " §8[Click for details]");
                g.text(font, desc.toString(), x + 10, curY + 21, 0xFFCBD5E1, false);
                g.text(font, "§6+" + InfernoMinionTracker.formatCoins(grp.totalRevenue), x + w - 90, curY + 13, 0xFFFBBF24, true);
            } else {
                g.text(font, badge + " §f" + grp.player + " §7placed §e" + minionStr + " fuels §7at §f" + timeStr, x + 10, curY + 6, 0xFFFFFFFF, false);
                g.text(font, "§7Hypergolic Crude Gabagool (Slot 19)" + (isHovered ? " §b[Click to view full breakdown]" : " §8[Click for details]"), x + 10, curY + 21, 0xFFCBD5E1, false);
                g.text(font, "§c-" + InfernoMinionTracker.formatCoins(grp.totalCost), x + w - 90, curY + 13, 0xFFF87171, true);
            }

            curY += rowH + 4;
        }

        return curY - y;
    }

    private int renderFuelCraftTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        int curY = y;
        me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.CraftPlan plan =
                me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.computePlan();

        // 1. Target Controls Header Card
        int ctrlH = 34;
        g.fill(x, curY, x + w, curY + ctrlH, 0xFF1F2937);
        g.outline(x, curY, w, ctrlH, 0xFF374151);

        g.text(font, "§6§lTarget Hypergolic Fuels: §e" + plan.targetFuels, x + 10, curY + 13, 0xFFFFFFFF, true);

        // Stepper buttons [-] and [+]
        int btnY = curY + 7;
        int btnH = 20;

        int minusX = x + 185;
        int minusW = 22;
        boolean minusHov = mouseX >= minusX && mouseX <= minusX + minusW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(minusX, btnY, minusX + minusW, btnY + btnH, minusHov ? 0xFF374151 : 0xFF111827);
        g.outline(minusX, btnY, minusW, btnH, 0xFF4B5563);
        g.centeredText(font, "§f-", minusX + minusW / 2, btnY + 5, 0xFFFFFFFF);

        int plusX = minusX + minusW + 4;
        int plusW = 22;
        boolean plusHov = mouseX >= plusX && mouseX <= plusX + plusW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(plusX, btnY, plusX + plusW, btnY + btnH, plusHov ? 0xFF374151 : 0xFF111827);
        g.outline(plusX, btnY, plusW, btnH, 0xFF4B5563);
        g.centeredText(font, "§f+", plusX + plusW / 2, btnY + 5, 0xFFFFFFFF);

        // Presets: [16] [32] [64] [128]
        int[] presets = {16, 32, 64, 128};
        int pX = plusX + plusW + 10;
        int pW = 32;
        for (int p : presets) {
            boolean active = plan.targetFuels == p;
            boolean pHov = mouseX >= pX && mouseX <= pX + pW && mouseY >= btnY && mouseY <= btnY + btnH;
            g.fill(pX, btnY, pX + pW, btnY + btnH, active ? 0xFF2563EB : (pHov ? 0xFF374151 : 0xFF111827));
            g.outline(pX, btnY, pW, btnH, active ? 0xFF60A5FA : 0xFF4B5563);
            g.centeredText(font, (active ? "§f§l" : "§7") + p, pX + pW / 2, btnY + 5, 0xFFFFFFFF);
            pX += pW + 4;
        }

        // Right side: Total estimated cost & ready count
        String costStr = "§7Est. Buy Cost: §6" + InfernoMinionTracker.formatCoins(plan.totalEstimatedBuyCost) + " coins";
        g.text(font, costStr, x + w - font.width(costStr) - 10, curY + 13, 0xFFFCD34D, false);

        curY += ctrlH + 10;

        // 2. Raw Materials Section Header
        g.fill(x, curY, x + w, curY + 20, 0xFF1E293B);
        g.outline(x, curY, w, 20, 0xFF334155);
        g.text(font, "§b§l📦 RAW MATERIALS TO BUY (Click to copy & open /bz)", x + 10, curY + 5, 0xFF38BDF8, false);
        g.text(font, "§7HAVE (INV+SACKS)", x + w - 310, curY + 5, 0xFF94A3B8, false);
        g.text(font, "§7TO BUY", x + w - 190, curY + 5, 0xFF94A3B8, false);
        g.text(font, "§7SUBTOTAL", x + w - 85, curY + 5, 0xFF94A3B8, false);
        curY += 24;

        int rowH = 34;
        for (me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.CraftItem item : plan.rawMaterials) {
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + rowH;
            int rowBg = item.toBuy == 0 ? 0x1510B981 : (hov ? 0x30374151 : 0x151F2937);
            g.fill(x, curY, x + w, curY + rowH, rowBg);
            g.outline(x, curY, w, rowH, item.toBuy == 0 ? 0x4010B981 : (hov ? 0xFF60A5FA : 0x30374151));

            String iconAndName = item.icon + " " + (item.toBuy == 0 ? "§a" : "§f") + item.name + " §7(Need " + me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.formatNum(item.needed) + ")";
            g.text(font, iconAndName, x + 10, curY + 6, 0xFFFFFFFF, false);

            String haveDetail = "§8Inv: §7" + item.haveInv + " §8| Sacks: §7" + item.havePersonalSacks + (item.haveCoopSacks > 0 ? " §8| Coop: §b" + item.haveCoopSacks : "");
            g.text(font, haveDetail, x + 10, curY + 19, 0xFF9CA3AF, false);

            g.text(font, (item.getTotalHave() >= item.needed ? "§a" : "§e") + me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.formatNum(item.getTotalHave()), x + w - 310, curY + 12, 0xFFFFFFFF, false);

            if (item.toBuy == 0) {
                g.text(font, "§a✓ READY", x + w - 190, curY + 12, 0xFF10B981, true);
            } else {
                g.text(font, "§c" + me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.formatNum(item.toBuy), x + w - 190, curY + 12, 0xFFEF4444, true);
            }

            g.text(font, "§6" + (item.totalCost > 0 ? InfernoMinionTracker.formatCoins(item.totalCost) : "0"), x + w - 85, curY + 12, 0xFFFBBF24, false);

            curY += rowH + 4;
        }

        curY += 8;

        // 3. Crafting Stages / Intermediate Crafts Section Header
        g.fill(x, curY, x + w, curY + 20, 0xFF1E293B);
        g.outline(x, curY, w, 20, 0xFF334155);
        g.text(font, "§e§l🔨 INTERMEDIATE CRAFTING STAGES", x + 10, curY + 5, 0xFFFCD34D, false);
        g.text(font, "§7POSSESSED", x + w - 310, curY + 5, 0xFF94A3B8, false);
        g.text(font, "§7TO CRAFT", x + w - 190, curY + 5, 0xFF94A3B8, false);
        g.text(font, "§7STATUS", x + w - 85, curY + 5, 0xFF94A3B8, false);
        curY += 24;

        for (me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.CraftItem item : plan.intermediates) {
            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + rowH;
            int rowBg = item.toCraft == 0 ? 0x1510B981 : (hov ? 0x30374151 : 0x151F2937);
            g.fill(x, curY, x + w, curY + rowH, rowBg);
            g.outline(x, curY, w, rowH, item.toCraft == 0 ? 0x4010B981 : (hov ? 0xFFFBBF24 : 0x30374151));

            String iconAndName = item.icon + " §f" + item.name + " §7(Total Needed: " + me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.formatNum(item.needed) + ")";
            g.text(font, iconAndName, x + 10, curY + 6, 0xFFFFFFFF, false);

            String haveDetail = "§8Inv: §7" + item.haveInv + " §8| Sacks: §7" + item.havePersonalSacks + (item.haveCoopSacks > 0 ? " §8| Coop: §b" + item.haveCoopSacks : "");
            g.text(font, haveDetail, x + 10, curY + 19, 0xFF9CA3AF, false);

            g.text(font, (item.getTotalHave() >= item.needed ? "§a" : "§e") + me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.formatNum(item.getTotalHave()), x + w - 310, curY + 12, 0xFFFFFFFF, false);

            if (item.toCraft == 0) {
                g.text(font, "§a0 (Sufficient)", x + w - 190, curY + 12, 0xFF10B981, false);
                g.text(font, "§a✓ READY", x + w - 85, curY + 12, 0xFF10B981, true);
            } else {
                g.text(font, "§e" + me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.formatNum(item.toCraft), x + w - 190, curY + 12, 0xFFFCD34D, true);
                g.text(font, "§6CRAFT", x + w - 85, curY + 12, 0xFFFB923C, false);
            }

            curY += rowH + 4;
        }

        curY += 8;

        // 4. Co-op Members Sacks Breakdown Card
        java.util.List<String> coopSacks = me.bombo.bomboaddons.features.inferno.SacksHelper.getCoopMembersWithSacks();
        if (!coopSacks.isEmpty()) {
            int coopH = 22 + coopSacks.size() * 16;
            g.fill(x, curY, x + w, curY + coopH, 0x153B82F6);
            g.outline(x, curY, w, coopH, 0x403B82F6);
            g.text(font, "§b👥 Co-op Sacks Overview (Shared automatically across co-op)", x + 10, curY + 6, 0xFF60A5FA, false);
            int cY = curY + 22;
            for (String member : coopSacks) {
                String summary = me.bombo.bomboaddons.features.inferno.SacksHelper.getUserFuelSacksSummary(member);
                g.text(font, "§7• §e" + member + "§7: " + summary, x + 16, cY, 0xFFE2E8F0, false);
                cY += 16;
            }
            curY += coopH + 6;
        }

        return curY - y;
    }

    private void renderActivityDetailModal(GuiGraphicsExtractor g, Font font, int winX, int winY, int winW, int winH, int mouseX, int mouseY) {
        if (this.selectedActivity == null) return;
        ActivityGroup grp = this.selectedActivity;

        // Dark dim backdrop
        g.fill(winX, winY, winX + winW, winY + winH, 0xDD000000);

        int modalW = Math.min(520, winW - 30);
        int modalH = Math.min(360, winH - 30);
        int modalX = winX + (winW - modalW) / 2;
        int modalY = winY + (winH - modalH) / 2;

        // Panel background & glowing border
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF0111827);
        g.outline(modalX, modalY, modalW, modalH, 0xFF3B82F6);

        // Header
        g.text(font, "§6§l🔥 SESSION DETAILS: §f" + grp.player, modalX + 16, modalY + 14, 0xFFFFFFFF, true);
        String timeStr = grp.startTime > 0 ? DETAIL_TIME_FMT.format(new Date(grp.startTime)) : "Recent";
        g.text(font, "§7Time: §e" + timeStr + " §8| §7Type: " + ("CLAIM".equalsIgnoreCase(grp.type) ? "§aMinion Claim" : "§cFuel Insertion"), modalX + 16, modalY + 27, 0xFFA0AEC0, false);

        // Close [✕] button
        int closeBtnW = 20;
        int closeBtnH = 20;
        int closeBtnX = modalX + modalW - closeBtnW - 12;
        int closeBtnY = modalY + 12;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnW && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnH;
        g.fill(closeBtnX, closeBtnY, closeBtnX + closeBtnW, closeBtnY + closeBtnH, closeHover ? 0xFFEF4444 : 0xFF1F2937);
        g.outline(closeBtnX, closeBtnY, closeBtnW, closeBtnH, closeHover ? 0xFFF87171 : 0xFF374151);
        g.centeredText(font, "✕", closeBtnX + closeBtnW / 2, closeBtnY + 5, 0xFFFFFFFF);

        // Mini metric cards bar (3 cards)
        int cardY = modalY + 44;
        int cardH = 34;
        int cardW = (modalW - 32 - 16) / 3;

        // Card 1: Minions
        int c1X = modalX + 16;
        g.fill(c1X, cardY, c1X + cardW, cardY + cardH, 0x601F2937);
        g.outline(c1X, cardY, cardW, cardH, 0xFF374151);
        g.text(font, "§7MINIONS", c1X + 8, cardY + 5, 0xFF9CA3AF, false);
        g.text(font, "§e" + grp.minionCount + " Minions", c1X + 8, cardY + 18, 0xFFFBBF24, true);

        // Card 2: Total Items
        int c2X = c1X + cardW + 8;
        g.fill(c2X, cardY, c2X + cardW, cardY + cardH, 0x601F2937);
        g.outline(c2X, cardY, cardW, cardH, 0xFF374151);
        g.text(font, "§7TOTAL ITEMS", c2X + 8, cardY + 5, 0xFF9CA3AF, false);
        g.text(font, "§b" + grp.itemCount + " Items", c2X + 8, cardY + 18, 0xFF38BDF8, true);

        // Card 3: Revenue / Cost
        int c3X = c2X + cardW + 8;
        boolean isClaim = "CLAIM".equalsIgnoreCase(grp.type);
        g.fill(c3X, cardY, c3X + cardW, cardY + cardH, 0x601F2937);
        g.outline(c3X, cardY, cardW, cardH, isClaim ? 0xFF10B981 : 0xFFEF4444);
        g.text(font, isClaim ? "§7GROSS VALUE" : "§7FUEL EXPENSE", c3X + 8, cardY + 5, 0xFF9CA3AF, false);
        g.text(font, (isClaim ? "§a+" : "§c-") + InfernoMinionTracker.formatCoins(isClaim ? grp.totalRevenue : grp.totalCost), c3X + 8, cardY + 18, isClaim ? 0xFF10B981 : 0xFFF87171, true);

        // Inner scrollable content
        int innerX = modalX + 16;
        int innerY = cardY + cardH + 12;
        int innerW = modalW - 32;
        int innerH = modalH - (innerY - modalY) - 14;

        g.fill(innerX, innerY, innerX + innerW, innerY + innerH, 0x400A0D14);
        g.outline(innerX, innerY, innerW, innerH, 0xFF1F2937);

        g.enableScissor(innerX + 1, innerY + 1, innerX + innerW - 1, innerY + innerH - 1);
        int curContentY = innerY + 8 - (int) this.detailModalScroll;

        // Section 1: Drops Breakdown
        if (isClaim && !grp.drops.isEmpty()) {
            g.text(font, "§6§lCOLLECTED DROPS BREAKDOWN:", innerX + 8, curContentY, 0xFFF59E0B, true);
            curContentY += 16;

            for (Map.Entry<String, Integer> e : grp.drops.entrySet()) {
                String sbId = e.getKey();
                int count = e.getValue();
                long unitPrice = InfernoMinionTracker.getDropUnitPrice(sbId);
                long totalVal = unitPrice * count;

                String dropName = InfernoMinionTracker.formatDropName(sbId);
                g.text(font, "• §f" + count + "x §e" + dropName, innerX + 14, curContentY, 0xFFFFFFFF, false);
                g.text(font, "§7@ §e" + InfernoMinionTracker.formatCoins(unitPrice) + " §7= §6+" + InfernoMinionTracker.formatCoins(totalVal), innerX + innerW - 150, curContentY, 0xFFFBBF24, false);
                curContentY += 14;
            }
            curContentY += 8;
        }

        // Section 2: Individual Minion Claims / Actions
        g.text(font, "§3§lMINION LOGS (" + grp.subActions.size() + " actions):", innerX + 8, curContentY, 0xFF06B6D4, true);
        curContentY += 16;

        int actionIdx = 1;
        for (InfernoMinionTracker.ActionEntry sub : grp.subActions) {
            String subTime = sub.timestamp > 0 ? TIME_FMT.format(new Date(sub.timestamp)) : "";
            String pos = (sub.pos != null && !sub.pos.isEmpty()) ? sub.pos : "Unknown pos";
            g.text(font, "§8#" + (actionIdx++) + " §7Minion at §e(" + pos + ") §8[" + subTime + "]", innerX + 14, curContentY, 0xFFCBD5E1, false);

            if (isClaim) {
                StringBuilder dropSb = new StringBuilder();
                if (sub.drops != null) {
                    int di = 0;
                    for (Map.Entry<String, Integer> de : sub.drops.entrySet()) {
                        if (di++ > 0) dropSb.append(", ");
                        dropSb.append(de.getValue()).append("x ").append(InfernoMinionTracker.formatDropName(de.getKey()));
                    }
                }
                g.text(font, "   §7Drops: " + dropSb.toString(), innerX + 14, curContentY + 12, 0xFF94A3B8, false);
                g.text(font, "§6+" + InfernoMinionTracker.formatCoins(sub.revenue), innerX + innerW - 80, curContentY + 5, 0xFFFBBF24, true);
                curContentY += 26;
            } else {
                g.text(font, "   §7Fuel UUID: §8" + (sub.fuelUuid != null ? sub.fuelUuid : "N/A"), innerX + 14, curContentY + 12, 0xFF94A3B8, false);
                g.text(font, "§c-" + InfernoMinionTracker.formatCoins(sub.cost), innerX + innerW - 80, curContentY + 5, 0xFFF87171, true);
                curContentY += 26;
            }
        }

        g.disableScissor();

        int totalDetailH = curContentY - (innerY + 8 - (int) this.detailModalScroll);
        this.detailModalMaxScroll = Math.max(0, totalDetailH - innerH + 16);

        // Detail scrollbar
        if (this.detailModalMaxScroll > 0) {
            int sbW = 3;
            int sbX = innerX + innerW - sbW - 2;
            int thumbH = Math.max(16, (int) ((float) innerH / totalDetailH * innerH));
            int thumbY = innerY + (int) ((this.detailModalScroll / this.detailModalMaxScroll) * (innerH - thumbH));
            g.fill(sbX, innerY, sbX + sbW, innerY + innerH, 0x33000000);
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF3B82F6);
        }
    }

    private void renderPasskeyModal(GuiGraphicsExtractor g, Font font, int winX, int winY, int winW, int winH, int mouseX, int mouseY) {
        g.fill(winX, winY, winX + winW, winY + winH, 0xCC000000);

        int modalW = 280;
        int modalH = 130;
        int modalX = winX + (winW - modalW) / 2;
        int modalY = winY + (winH - modalH) / 2;

        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF1F2937);
        g.outline(modalX, modalY, modalW, modalH, 0xFF3B82F6);

        g.centeredText(font, "§f§lCoop Passkey", modalX + modalW / 2, modalY + 14, 0xFFFFFFFF);
        g.centeredText(font, "§7Enter passkey to sync with coop members:", modalX + modalW / 2, modalY + 28, 0xFF9CA3AF);

        if (this.passkeyEditBox != null) {
            this.passkeyEditBox.extractRenderState(g, mouseX, mouseY, 0);
        }

        // [Save] button
        int saveW = 80;
        int saveH = 20;
        int saveX = modalX + modalW / 2 - saveW - 8;
        int saveY = modalY + modalH - 32;
        boolean saveHover = mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= saveY && mouseY <= saveY + saveH;
        g.fill(saveX, saveY, saveX + saveW, saveY + saveH, saveHover ? 0xFF16A34A : 0xFF15803D);
        g.outline(saveX, saveY, saveW, saveH, 0xFF22C55E);
        g.centeredText(font, "§aSave", saveX + saveW / 2, saveY + 5, 0xFFFFFFFF);

        // [Cancel] button
        int cancelW = 80;
        int cancelH = 20;
        int cancelX = modalX + modalW / 2 + 8;
        int cancelY = modalY + modalH - 32;
        boolean cancelHover = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= cancelY && mouseY <= cancelY + cancelH;
        g.fill(cancelX, cancelY, cancelX + cancelW, cancelY + cancelH, cancelHover ? 0xFFEF4444 : 0xFFB91C1C);
        g.outline(cancelX, cancelY, cancelW, cancelH, 0xFFF87171);
        g.centeredText(font, "§cCancel", cancelX + cancelW / 2, cancelY + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedActivity != null) {
            if (this.detailModalMaxScroll > 0) {
                this.detailModalScroll -= verticalAmount * 24.0;
                if (this.detailModalScroll < 0) this.detailModalScroll = 0;
                if (this.detailModalScroll > this.detailModalMaxScroll) this.detailModalScroll = this.detailModalMaxScroll;
            }
            return true;
        }
        if (this.maxScroll > 0) {
            this.scrollAmount -= verticalAmount * 28.0;
            if (this.scrollAmount < 0) this.scrollAmount = 0;
            if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        int winW = Math.min(this.width - 40, 720);
        int winH = Math.min(this.height - 40, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        if (this.selectedActivity != null) {
            int modalW = Math.min(520, winW - 30);
            int modalH = Math.min(360, winH - 30);
            int modalX = winX + (winW - modalW) / 2;
            int modalY = winY + (winH - modalH) / 2;

            int closeBtnW = 20;
            int closeBtnH = 20;
            int closeBtnX = modalX + modalW - closeBtnW - 12;
            int closeBtnY = modalY + 12;

            if (event.x() >= closeBtnX && event.x() <= closeBtnX + closeBtnW && event.y() >= closeBtnY && event.y() <= closeBtnY + closeBtnH) {
                this.selectedActivity = null;
                return true;
            }

            if (event.x() < modalX || event.x() > modalX + modalW || event.y() < modalY || event.y() > modalY + modalH) {
                this.selectedActivity = null;
                return true;
            }

            return true;
        }

        if (this.editingPasskey) {
            int modalW = 280;
            int modalH = 130;
            int modalX = winX + (winW - modalW) / 2;
            int modalY = winY + (winH - modalH) / 2;

            int saveW = 80;
            int saveH = 20;
            int saveX = modalX + modalW / 2 - saveW - 8;
            int saveY = modalY + modalH - 32;
            if (event.x() >= saveX && event.x() <= saveX + saveW && event.y() >= saveY && event.y() <= saveY + saveH) {
                if (this.passkeyEditBox != null) {
                    InfernoMinionTracker.setPasskey(this.passkeyEditBox.getValue());
                }
                this.editingPasskey = false;
                this.removeWidget(this.passkeyEditBox);
                this.passkeyEditBox = null;
                return true;
            }

            int cancelW = 80;
            int cancelH = 20;
            int cancelX = modalX + modalW / 2 + 8;
            int cancelY = modalY + modalH - 32;
            if (event.x() >= cancelX && event.x() <= cancelX + cancelW && event.y() >= cancelY && event.y() <= cancelY + cancelH) {
                this.editingPasskey = false;
                this.removeWidget(this.passkeyEditBox);
                this.passkeyEditBox = null;
                return true;
            }

            if (this.passkeyEditBox != null) {
                this.passkeyEditBox.mouseClicked(event, handled);
            }
            return true;
        }

        // Profile ID link click
        String curPass = InfernoMinionTracker.getPasskey();
        String currentPid = me.bombo.bomboaddons.SkyblockUtils.currentProfileId != null ? me.bombo.bomboaddons.SkyblockUtils.currentProfileId.trim() : "";
        boolean isLinkedToPid = !currentPid.isEmpty() && curPass.equalsIgnoreCase(currentPid);
        String subLabel = "§7Coop: " + (isLinkedToPid ? "§a[Profile Linked] §e" : "§e") + curPass + " §8[Click to Edit]";
        int linkBtnX = winX + 16 + this.font.width(subLabel) + 6;
        int linkBtnY = winY + 24;
        int linkBtnW = 95;
        int linkBtnH = 14;
        if (!currentPid.isEmpty() && !isLinkedToPid) {
            if (event.x() >= linkBtnX && event.x() <= linkBtnX + linkBtnW && event.y() >= linkBtnY && event.y() <= linkBtnY + linkBtnH) {
                InfernoMinionTracker.setPasskey(currentPid);
                return true;
            }
        }

        // Passkey click to edit
        if (event.x() >= winX + 16 && event.x() <= winX + 280 && event.y() >= winY + 24 && event.y() <= winY + 38) {
            this.editingPasskey = true;
            this.init();
            return true;
        }

        int btnY = winY + 12;
        int btnH = 20;

        // Close
        int closeW = 24;
        int closeX = winX + winW - closeW - 14;
        if (event.x() >= closeX && event.x() <= closeX + closeW && event.y() >= btnY && event.y() <= btnY + btnH) {
            this.onClose();
            return true;
        }

        // Sync
        int syncW = 60;
        int syncX = closeX - syncW - 6;
        if (event.x() >= syncX && event.x() <= syncX + syncW && event.y() >= btnY && event.y() <= btnY + btnH) {
            this.isSyncing = true;
            InfernoMinionTracker.syncWithServer(() -> {
                this.isSyncing = false;
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    InfernoMinionTracker.TrackerState cur = InfernoMinionTracker.getState();
                    long dropsTotal = cur.drops.values().stream().mapToLong(Long::longValue).sum();
                    long fuelsCount = cur.fuels.getOrDefault(InfernoMinionTracker.HYPERGOLIC_FUEL_ID, 0L);
                    net.minecraft.client.Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§8[§6🔥 Inferno§8] §aCoop synchronized (§e" + InfernoMinionTracker.getPasskey() + "§a): §f" + dropsTotal + " drops§7, §e" + fuelsCount + " fuels§7, §aNet: " + (cur.netProfit >= 0 ? "§a+" : "§c") + InfernoMinionTracker.formatCoins(cur.netProfit)
                    ));
                }
            });
            return true;
        }

        // Reset
        int resetW = 64;
        int resetX = syncX - resetW - 6;
        if (event.x() >= resetX && event.x() <= resetX + resetW && event.y() >= btnY && event.y() <= btnY + btnH) {
            InfernoMinionTracker.resetData(() -> {
                this.scrollAmount = 0;
            });
            return true;
        }

        // Copy Summary
        int copyW = 62;
        int copyX = resetX - copyW - 6;
        if (event.x() >= copyX && event.x() <= copyX + copyW && event.y() >= btnY && event.y() <= btnY + btnH) {
            copySummaryToClipboard();
            return true;
        }

        // Tabs click
        int cardsY = winY + 44;
        int cardH = 46;
        int tabsY = cardsY + cardH + 10;
        int tabW = (winW - 32) / TAB_NAMES.length;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int tX = winX + 16 + i * tabW;
            if (event.x() >= tX && event.x() <= tX + tabW - 4 && event.y() >= tabsY && event.y() <= tabsY + 22) {
                this.currentTab = i;
                this.scrollAmount = 0;
                return true;
            }
        }

        // Fuel Tab: Clear Fuels button click
        if (this.currentTab == 1) {
            int contentX = winX + 16;
            int contentY = tabsY + 30;
            int contentW = winW - 32;
            int contentH = winH - (contentY - winY) - 16;
            int fx = contentX + 8;
            int fy = contentY + 8 - (int) this.scrollAmount;
            int fw = contentW - 16;
            int fBtnY = fy + 74 + 12 + 36 + 12 - 3;
            int fBtnX = fx + fw - 76;
            if (event.x() >= fBtnX && event.x() <= fBtnX + 76 && event.y() >= fBtnY && event.y() <= fBtnY + 16
                    && event.y() >= contentY && event.y() <= contentY + contentH) {
                InfernoMinionTracker.resetFuels(null);
                return true;
            }
        }

        // Activity Tab: Row click to open detail modal
        if (this.currentTab == 3) {
            int contentX = winX + 16;
            int contentY = tabsY + 30;
            int contentW = winW - 32;
            int contentH = winH - (contentY - winY) - 16;
            if (event.x() >= contentX && event.x() <= contentX + contentW && event.y() >= contentY && event.y() <= contentY + contentH) {
                int listX = contentX + 8;
                int curY = contentY + 8 - (int) this.scrollAmount + 16;
                int listW = contentW - 16;
                int rowH = 38;
                List<ActivityGroup> groups = groupActions(InfernoMinionTracker.getState().actions);
                for (ActivityGroup grp : groups) {
                    if (event.y() >= curY && event.y() <= curY + rowH && event.x() >= listX && event.x() <= listX + listW) {
                        this.selectedActivity = grp;
                        this.detailModalScroll = 0;
                        return true;
                    }
                    curY += rowH + 4;
                }
            }
        }

        // Fuel Craft Tab: Stepper, Presets & Item Card clicks
        if (this.currentTab == 4) {
            int contentX = winX + 16;
            int contentY = tabsY + 30;
            int contentW = winW - 32;
            int contentH = winH - (contentY - winY) - 16;
            int fx = contentX + 8;
            int fy = contentY + 8 - (int) this.scrollAmount;
            int fw = contentW - 16;

            // Stepper & Presets
            int stepY = fy + 7;
            int stepH = 20;
            int minusX = fx + 185;
            int minusW = 22;
            int plusX = minusX + minusW + 4;
            int plusW = 22;

            if (event.y() >= stepY && event.y() <= stepY + stepH && event.y() >= contentY && event.y() <= contentY + contentH) {
                if (event.x() >= minusX && event.x() <= minusX + minusW) {
                    int step = event.hasShiftDown() ? 8 : 1;
                    me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.setTargetFuels(
                            Math.max(1, me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.targetFuels - step));
                    return true;
                }
                if (event.x() >= plusX && event.x() <= plusX + plusW) {
                    int step = event.hasShiftDown() ? 8 : 1;
                    me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.setTargetFuels(
                            Math.min(1000, me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.targetFuels + step));
                    return true;
                }
                int[] presets = {16, 32, 64, 128};
                int pX = plusX + plusW + 10;
                int pW = 32;
                for (int p : presets) {
                    if (event.x() >= pX && event.x() <= pX + pW) {
                        me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.setTargetFuels(p);
                        return true;
                    }
                    pX += pW + 4;
                }
            }

            // Material cards clicks
            int curY = fy + 34 + 10 + 24;
            int rowH = 34;
            me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.CraftPlan plan =
                    me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.computePlan();

            for (me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.CraftItem item : plan.rawMaterials) {
                if (event.x() >= fx && event.x() <= fx + fw && event.y() >= curY && event.y() <= curY + rowH
                        && event.y() >= contentY && event.y() <= contentY + contentH) {
                    me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.executeBazaarAction(item);
                    return true;
                }
                curY += rowH + 4;
            }

            curY += 8 + 24;
            for (me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.CraftItem item : plan.intermediates) {
                if (event.x() >= fx && event.x() <= fx + fw && event.y() >= curY && event.y() <= curY + rowH
                        && event.y() >= contentY && event.y() <= contentY + contentH) {
                    me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper.executeBazaarAction(item);
                    return true;
                }
                curY += rowH + 4;
            }
        }

        return super.mouseClicked(event, handled);
    }

    private void copySummaryToClipboard() {
        InfernoMinionTracker.TrackerState s = InfernoMinionTracker.getState();
        StringBuilder sb = new StringBuilder();
        sb.append("🔥 Inferno Minion Tracker Summary (Coop: ").append(InfernoMinionTracker.getPasskey()).append(")\n");
        sb.append("Net Profit: ").append(s.netProfit >= 0 ? "+" : "").append(InfernoMinionTracker.formatCoins(s.netProfit)).append(" coins\n");
        sb.append("Gross Revenue: +").append(InfernoMinionTracker.formatCoins(s.totalRevenue)).append(" coins\n");
        sb.append("Fuel Expenses: -").append(InfernoMinionTracker.formatCoins(s.totalCost)).append(" coins\n");
        sb.append("Fuels Used: ").append(s.fuels.getOrDefault(InfernoMinionTracker.HYPERGOLIC_FUEL_ID, 0L)).append("x\n");
        sb.append("Top Drops:\n");
        for (Map.Entry<String, Long> e : s.drops.entrySet()) {
            sb.append(" - ").append(e.getValue()).append("x ").append(InfernoMinionTracker.formatDropName(e.getKey())).append("\n");
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§6🔥 Inferno§8] §aSummary copied to clipboard!"));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.selectedActivity != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                this.selectedActivity = null;
                return true;
            }
        }
        if (this.editingPasskey) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                this.editingPasskey = false;
                this.removeWidget(this.passkeyEditBox);
                this.passkeyEditBox = null;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                if (this.passkeyEditBox != null) {
                    InfernoMinionTracker.setPasskey(this.passkeyEditBox.getValue());
                }
                this.editingPasskey = false;
                this.removeWidget(this.passkeyEditBox);
                this.passkeyEditBox = null;
                return true;
            }
            if (this.passkeyEditBox != null && this.passkeyEditBox.keyPressed(event)) {
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
