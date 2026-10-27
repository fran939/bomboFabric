package me.bombo.bomboaddons.gui;

import java.text.SimpleDateFormat;
import java.util.*;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.features.inferno.InfernoMinionTracker;
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

    private static final String[] TAB_NAMES = {"📦 Drops & Revenue", "⛽ Fuel & Costs", "🤖 Tracked Minions", "📜 Activity Log"};
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("MM/dd HH:mm", Locale.ROOT);

    public InfernoTrackerScreen(Screen parent) {
        super(Component.literal("Inferno Minion Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        InfernoMinionTracker.ensureLoaded();

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
            case 3 -> totalH = renderActivityTab(g, font, contentX + 8, contentY + 8 - (int) this.scrollAmount, contentW - 16, s);
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
        if (s.minions.isEmpty()) {
            g.centeredText(font, "§7No Inferno Minions tracked yet.", x + w / 2, curY + 40, 0xFF9CA3AF);
            g.centeredText(font, "§8Right-click or collect from any Inferno Minion on your Private Island to register it!", x + w / 2, curY + 56, 0xFF6B7280);
            return 120;
        }

        g.text(font, "§7Total Active Minions Tracked: §a" + s.minions.size(), x, curY, 0xFFE5E7EB, false);
        curY += 16;

        int rowH = 40;
        for (InfernoMinionTracker.MinionInfo m : s.minions.values()) {
            g.fill(x, curY, x + w, curY + rowH, 0x201F2937);
            g.outline(x, curY, w, rowH, 0xFF374151);

            String minionTitle = "§6§l" + m.tier + " §7(ID: §e" + m.entityId + "§7)";
            g.text(font, minionTitle, x + 10, curY + 7, 0xFFFFFFFF, false);

            String posStr = "§7Location: §f" + m.x + ", " + m.y + ", " + m.z;
            g.text(font, posStr, x + 10, curY + 22, 0xFF9CA3AF, false);

            String stats = "§aDrops: §e" + m.totalDrops + " §8| §6Fuels: §e" + m.totalFuels;
            g.text(font, stats, x + w - 210, curY + 7, 0xFFD1D5DB, false);

            String lastTime = m.lastActionTime > 0 ? "§7Last: §b" + TIME_FMT.format(new Date(m.lastActionTime)) : "§8Never";
            g.text(font, lastTime, x + w - 210, curY + 22, 0xFF9CA3AF, false);

            curY += rowH + 6;
        }

        return curY - y;
    }

    private int renderActivityTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, InfernoMinionTracker.TrackerState s) {
        int curY = y;
        if (s.actions.isEmpty()) {
            g.centeredText(font, "§7No coop activity recorded yet.", x + w / 2, curY + 40, 0xFF9CA3AF);
            return 100;
        }

        g.text(font, "§7Recent Coop Activity Log (" + s.actions.size() + " actions):", x, curY, 0xFFE5E7EB, false);
        curY += 16;

        int rowH = 34;
        for (InfernoMinionTracker.ActionEntry a : s.actions) {
            boolean isClaim = "CLAIM".equalsIgnoreCase(a.type);
            int rowBg = isClaim ? 0x1510B981 : 0x15EF4444;
            int border = isClaim ? 0x4010B981 : 0x40EF4444;

            g.fill(x, curY, x + w, curY + rowH, rowBg);
            g.outline(x, curY, w, rowH, border);

            // Badge
            String badge = isClaim ? "§a[CLAIM]" : "§c[FUEL]";
            String timeStr = a.timestamp > 0 ? TIME_FMT.format(new Date(a.timestamp)) : "";
            g.text(font, badge + " §f" + a.player + " §7(" + timeStr + ")", x + 10, curY + 6, 0xFFFFFFFF, false);

            if (isClaim) {
                StringBuilder desc = new StringBuilder("§7Collected: ");
                if (a.drops != null) {
                    int i = 0;
                    for (Map.Entry<String, Integer> e : a.drops.entrySet()) {
                        if (i++ > 0) desc.append(", ");
                        desc.append(e.getValue()).append("x ").append(InfernoMinionTracker.formatDropName(e.getKey()));
                    }
                }
                g.text(font, desc.toString(), x + 10, curY + 19, 0xFFCBD5E1, false);
                g.text(font, "§6+" + InfernoMinionTracker.formatCoins(a.revenue), x + w - 90, curY + 11, 0xFFFBBF24, true);
            } else {
                g.text(font, "§7Placed 1x Hypergolic Crude Gabagool (Slot 19)", x + 10, curY + 19, 0xFFCBD5E1, false);
                g.text(font, "§c-" + InfernoMinionTracker.formatCoins(a.cost), x + w - 90, curY + 11, 0xFFF87171, true);
            }

            curY += rowH + 4;
        }

        return curY - y;
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
