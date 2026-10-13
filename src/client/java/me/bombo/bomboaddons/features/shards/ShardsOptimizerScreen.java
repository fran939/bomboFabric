package me.bombo.bomboaddons.features.shards;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.shards.ShardsShoppingManager.DirectBuyItem;
import me.bombo.bomboaddons.features.shards.ShardsShoppingManager.FusionItem;
import me.bombo.bomboaddons.features.shards.ShardsShoppingManager.PlanData;
import me.bombo.bomboaddons.features.shards.ShardsShoppingManager.PlannedUpgrade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ShardsOptimizerScreen extends Screen {
    private String selectedClass = "archer";
    private EditBox budgetBox;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private Button pinHudButton;

    private static class ClickableButtonArea {
        int minX, minY, maxX, maxY;
        Runnable action;
        String tooltip;

        ClickableButtonArea(int minX, int minY, int maxX, int maxY, Runnable action, String tooltip) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.action = action;
            this.tooltip = tooltip;
        }
    }

    private final List<ClickableButtonArea> rowButtons = new ArrayList<>();

    public ShardsOptimizerScreen() {
        super(Component.literal("Attribute Shards Optimizer"));
    }

    public ShardsOptimizerScreen(String initialClass, String initialBudget) {
        super(Component.literal("Attribute Shards Optimizer"));
        if (initialClass != null && !initialClass.isEmpty()) {
            this.selectedClass = initialClass.toLowerCase();
        }
    }

    @Override
    protected void init() {
        super.init();
        rowButtons.clear();

        int modalW = Math.min(520, this.width - 20);
        int modalH = Math.min(360, this.height - 20);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Budget input field
        String currentBudgetText = budgetBox != null ? budgetBox.getValue() : "100m";
        this.budgetBox = new EditBox(this.font, modalX + 70, modalY + 54, 90, 18, Component.literal("Budget"));
        this.budgetBox.setValue(currentBudgetText);
        this.addRenderableWidget(this.budgetBox);

        // Calculate Button
        this.addRenderableWidget(Button.builder(Component.literal("§6§l⚡ Calculate"), b -> {
            String bVal = budgetBox.getValue().trim();
            String player = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
            ShardsShoppingManager.fetchPlanAsync(player, selectedClass, bVal, err -> {
                // Done
            });
        }).bounds(modalX + modalW - 130, modalY + 53, 120, 20).build());

        // Pin to HUD toggle button
        boolean hudActive = BomboConfig.get().shardsShoppingHud;
        this.pinHudButton = Button.builder(Component.literal(hudActive ? "§a§l📌 HUD: ON" : "§7📌 HUD: OFF"), b -> {
            BomboConfig.Settings s = BomboConfig.get();
            s.shardsShoppingHud = !s.shardsShoppingHud;
            BomboConfig.save();
            b.setMessage(Component.literal(s.shardsShoppingHud ? "§a§l📌 HUD: ON" : "§7📌 HUD: OFF"));
        }).bounds(modalX + modalW - 250, modalY + 53, 115, 20).build();
        this.addRenderableWidget(this.pinHudButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int modalW = Math.min(520, this.width - 20);
        int modalH = Math.min(360, this.height - 20);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Class selection pills
        String[] classes = {"archer", "berserk", "mage", "healer", "tank", "all"};
        int pillX = modalX + 12;
        int pillY = modalY + 28;
        for (String c : classes) {
            int pW = this.font.width(capitalize(c)) + 12;
            if (mouseX >= pillX && mouseX <= pillX + pW && mouseY >= pillY && mouseY <= pillY + 16) {
                selectedClass = c;
                return true;
            }
            pillX += pW + 6;
        }

        // Quick budget pills: 10M, 50M, 100M, 250M, 500M
        String[] quickBudgets = {"10m", "50m", "100m", "250m", "500m"};
        int qX = modalX + 168;
        int qY = modalY + 55;
        for (String qb : quickBudgets) {
            int qW = this.font.width(qb.toUpperCase()) + 8;
            if (mouseX >= qX && mouseX <= qX + qW && mouseY >= qY && mouseY <= qY + 15) {
                budgetBox.setValue(qb);
                return true;
            }
            qX += qW + 4;
        }

        // Open Web Optimizer button
        int webBtnX = modalX + modalW - 120;
        int webBtnY = modalY + 8;
        if (mouseX >= webBtnX && mouseX <= webBtnX + 110 && mouseY >= webBtnY && mouseY <= webBtnY + 14) {
            String p = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
            String url = "https://bombo.dpdns.org/dungeons/shards?player=" + p + "&class=" + selectedClass;
            Util.getPlatform().openUri(URI.create(url));
            return true;
        }

        // Check dynamic row action buttons (e.g. BUY buttons)
        synchronized (rowButtons) {
            for (ClickableButtonArea btn : rowButtons) {
                if (mouseX >= btn.minX && mouseX <= btn.maxX && mouseY >= btn.minY && mouseY <= btn.maxY) {
                    btn.action.run();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        int modalW = Math.min(520, this.width - 20);
        int modalH = Math.min(360, this.height - 20);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Modal backdrop and border
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF00D1117);
        g.outline(modalX, modalY, modalW, modalH, 0xFF30363D);

        // Header accent bar
        g.fill(modalX, modalY, modalX + modalW, modalY + 3, 0xFFF59E0B);

        // Title and Subtitle
        g.text(this.font, "§6§lAttribute Shards Optimizer", modalX + 12, modalY + 10, -1, true);
        g.text(this.font, "§7[🌐 Open Web]", modalX + modalW - 95, modalY + 10, 0x00D4FF, false);

        // Class Selection Pills row
        String[] classes = {"archer", "berserk", "mage", "healer", "tank", "all"};
        int pillX = modalX + 12;
        int pillY = modalY + 28;
        for (String c : classes) {
            boolean active = c.equalsIgnoreCase(selectedClass);
            String title = capitalize(c);
            int pW = this.font.width(title) + 12;
            int bgCol = active ? 0xFFF59E0B : 0xFF21262D;
            int textCol = active ? 0x000000 : 0xC9D1D9;
            g.fill(pillX, pillY, pillX + pW, pillY + 16, bgCol);
            g.text(this.font, (active ? "§l" : "") + title, pillX + 6, pillY + 4, textCol, false);
            pillX += pW + 6;
        }

        // Budget row label
        g.text(this.font, "§7Budget:", modalX + 12, modalY + 59, -1, true);

        // Quick Budget pills
        String[] quickBudgets = {"10m", "50m", "100m", "250m", "500m"};
        int qX = modalX + 168;
        int qY = modalY + 55;
        for (String qb : quickBudgets) {
            int qW = this.font.width(qb.toUpperCase()) + 8;
            g.fill(qX, qY, qX + qW, qY + 15, 0xFF161B22);
            g.outline(qX, qY, qW, 15, 0xFF30363D);
            g.text(this.font, qb.toUpperCase(), qX + 4, qY + 4, 0x58A6FF, false);
            qX += qW + 4;
        }

        // Separator Line
        g.fill(modalX + 10, modalY + 79, modalX + modalW - 10, modalY + 80, 0xFF30363D);

        // Content Area with Scrolling
        int contentX = modalX + 10;
        int contentY = modalY + 84;
        int contentW = modalW - 20;
        int contentH = modalH - 94;

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        synchronized (rowButtons) {
            rowButtons.clear();
        }

        if (ShardsShoppingManager.isLoading()) {
            g.text(this.font, "§e⚡ Calculating optimal shard fusion & bazaar plan...", contentX + 10, contentY + 30, -1, true);
            g.disableScissor();
            return;
        }

        if (ShardsShoppingManager.getLastError() != null) {
            g.text(this.font, "§cError: " + ShardsShoppingManager.getLastError(), contentX + 10, contentY + 30, 0xFF5555, true);
            g.disableScissor();
            return;
        }

        PlanData plan = ShardsShoppingManager.getActivePlan();
        if (plan == null) {
            g.text(this.font, "§7Enter your budget and click §eCalculate§7 to view the optimal upgrade list.", contentX + 10, contentY + 30, 0x8B949E, false);
            g.disableScissor();
            return;
        }

        int currY = contentY + 6 - scrollOffset;

        // Plan Summary Banner
        g.fill(contentX, currY, contentX + contentW, currY + 28, 0xFF161B22);
        g.outline(contentX, currY, contentW, 28, 0xFF30363D);

        String spentStr = ShardsShoppingManager.formatCoins(plan.spent);
        String budgetTotalStr = ShardsShoppingManager.formatCoins(plan.budget);
        String remainStr = ShardsShoppingManager.formatCoins(plan.remaining);

        g.text(this.font, "§6Spent: §f" + spentStr + " §8/ §e" + budgetTotalStr + " §7(Remaining: §a" + remainStr + "§7)", contentX + 8, currY + 6, -1, true);
        g.text(this.font, "§a" + plan.upgrades.size() + " Upgrades Planned §7• §b" + plan.directBuys.size() + " Direct Buys §7• §d" + plan.fusions.size() + " Fusions", contentX + 8, currY + 17, 0x8B949E, false);
        currY += 34;

        // 1. Direct Buys Shopping List
        if (!plan.directBuys.isEmpty()) {
            g.text(this.font, "§a§lDIRECT BUYS §7(Click Buy to open Bazaar with Auto-Fill):", contentX + 2, currY, 0x2EA043, true);
            currY += 14;

            for (DirectBuyItem item : plan.directBuys) {
                g.fill(contentX, currY, contentX + contentW, currY + 20, 0xFF161B22);
                g.outline(contentX, currY, contentW, 20, 0xFF21262D);

                String fusionTag = item.forFusion != null ? " §8(" + item.forFusion + ")" : "";
                g.text(this.font, "§f• §b" + item.name + " §ax" + item.quantity + fusionTag, contentX + 6, currY + 6, -1, true);
                g.text(this.font, "§7~" + ShardsShoppingManager.formatCoins(item.estimatedCost), contentX + contentW - 140, currY + 6, 0x8B949E, false);

                // Buy Button [ 🛒 BUY ]
                int btnX = contentX + contentW - 65;
                int btnY = currY + 2;
                int btnW = 60;
                int btnH = 16;
                boolean isHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, isHover ? 0xFF2EA043 : 0xFF238636);
                g.text(this.font, "🛒 BUY", btnX + 14, btnY + 4, -1, true);

                final String sName = item.name;
                final int sQty = item.quantity;
                synchronized (rowButtons) {
                    rowButtons.add(new ClickableButtonArea(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                        ShardsShoppingManager.buyItem(sName, sQty);
                    }, "Opens /bz and copies " + sQty));
                }

                currY += 24;
            }
            currY += 8;
        }

        // 2. Fusions Needed
        if (!plan.fusions.isEmpty()) {
            g.text(this.font, "§d§lFUSION CRAFTS §7(Galatea Shards Fusion Helper):", contentX + 2, currY, 0xBC8CFF, true);
            currY += 14;

            for (FusionItem f : plan.fusions) {
                g.fill(contentX, currY, contentX + contentW, currY + 28, 0xFF161B22);
                g.outline(contentX, currY, contentW, 28, 0xFF30363D);

                String saveTag = f.savingsPercent > 0 ? String.format(Locale.US, " §a(Save %.1f%%)", f.savingsPercent) : "";
                g.text(this.font, "§6" + f.name + " §fx" + f.quantity + " §7(" + f.craftsCount + " crafts)" + saveTag, contentX + 6, currY + 5, -1, true);
                g.text(this.font, "§7Recipe: §f" + f.input1Qty + " " + f.input1Name + " §8+ §f" + f.input2Qty + " " + f.input2Name, contentX + 6, currY + 16, 0x8B949E, false);

                currY += 32;
            }
            currY += 8;
        }

        // 3. Upgrades Breakdown
        if (!plan.upgrades.isEmpty()) {
            g.text(this.font, "§e§lTARGET STAT UPGRADES §7(Strict Priority Descending):", contentX + 2, currY, 0xF59E0B, true);
            currY += 14;

            for (int i = 0; i < plan.upgrades.size(); i++) {
                PlannedUpgrade u = plan.upgrades.get(i);
                g.fill(contentX, currY, contentX + contentW, currY + 22, 0xFF161B22);
                g.outline(contentX, currY, contentW, 22, 0xFF21262D);

                String starStr = "★".repeat(Math.max(1, Math.min(5, u.priority)));
                g.text(this.font, "#" + (i + 1) + " §e" + starStr + " §f" + u.name + " §7(" + u.attrName + ")", contentX + 6, currY + 7, -1, true);
                g.text(this.font, "§bTier " + u.fromTier + " ➔ " + u.toTier + " §7(+" + u.shardsNeeded + ")", contentX + contentW - 200, currY + 7, 0x58A6FF, false);
                g.text(this.font, "§6" + ShardsShoppingManager.formatCoins(u.cost), contentX + contentW - 75, currY + 7, 0xF59E0B, true);

                currY += 26;
            }
        }

        int totalContentHeight = (currY + scrollOffset) - (contentY + 6);
        maxScroll = Math.max(0, totalContentHeight - contentH);

        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int scrollbarX = contentX + contentW + 2;
            int scrollbarH = Math.max(20, (int) ((float) contentH * contentH / (totalContentHeight)));
            int scrollbarY = contentY + (int) ((float) scrollOffset * (contentH - scrollbarH) / maxScroll);
            g.fill(scrollbarX, contentY, scrollbarX + 3, contentY + contentH, 0xFF161B22);
            g.fill(scrollbarX, scrollbarY, scrollbarX + 3, scrollbarY + scrollbarH, 0xFF58A6FF);
        }
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
