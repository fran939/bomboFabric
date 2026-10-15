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
    private Button showMaxedButton;
    private boolean showMaxed = false;

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

    private static class HoverTooltipTarget {
        int minX, minY, maxX, maxY;
        String name, rarity, attrName, family, effect, explanation;
        int currentTier, targetTier, currentStacks, maxStacks, shardsNeeded;
        long unitCost, totalCost;

        HoverTooltipTarget(int minX, int minY, int maxX, int maxY,
                           String name, String rarity, String attrName, String family,
                           String effect, String explanation,
                           int currentTier, int targetTier, int currentStacks, int maxStacks, int shardsNeeded,
                           long unitCost, long totalCost) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.name = name;
            this.rarity = rarity;
            this.attrName = attrName;
            this.family = family;
            this.effect = effect;
            this.explanation = explanation;
            this.currentTier = currentTier;
            this.targetTier = targetTier;
            this.currentStacks = currentStacks;
            this.maxStacks = maxStacks;
            this.shardsNeeded = shardsNeeded;
            this.unitCost = unitCost;
            this.totalCost = totalCost;
        }
    }

    private final List<ClickableButtonArea> rowButtons = new ArrayList<>();
    private final List<HoverTooltipTarget> hoverTargets = new ArrayList<>();

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
        hoverTargets.clear();

        int modalW = Math.min(520, this.width - 20);
        int modalH = Math.min(360, this.height - 20);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Row 1: [Hide Maxed] / [Show Maxed] button on right of class pills
        this.showMaxedButton = Button.builder(Component.literal(showMaxed ? "§a[Hide Maxed]" : "§7[Show Maxed]"), b -> {
            showMaxed = !showMaxed;
            b.setMessage(Component.literal(showMaxed ? "§a[Hide Maxed]" : "§7[Show Maxed]"));
        }).bounds(modalX + modalW - 105, modalY + 26, 95, 18).build();
        this.addRenderableWidget(this.showMaxedButton);

        // Row 2: Budget input field (compacted to prevent any overlap)
        String currentBudgetText = budgetBox != null ? budgetBox.getValue() : "100m";
        this.budgetBox = new EditBox(this.font, modalX + 56, modalY + 52, 66, 18, Component.literal("Budget"));
        this.budgetBox.setValue(currentBudgetText);
        this.addRenderableWidget(this.budgetBox);

        // Row 2: Pin to HUD toggle button
        boolean hudActive = BomboConfig.get().shardsShoppingHud;
        this.pinHudButton = Button.builder(Component.literal(hudActive ? "§a§l📌 HUD: ON" : "§7📌 HUD: OFF"), b -> {
            BomboConfig.Settings s = BomboConfig.get();
            s.shardsShoppingHud = !s.shardsShoppingHud;
            BomboConfig.save();
            b.setMessage(Component.literal(s.shardsShoppingHud ? "§a§l📌 HUD: ON" : "§7📌 HUD: OFF"));
        }).bounds(modalX + modalW - 198, modalY + 52, 92, 19).build();
        this.addRenderableWidget(this.pinHudButton);

        // Row 2: Calculate Button
        this.addRenderableWidget(Button.builder(Component.literal("§6§l⚡ Calculate"), b -> {
            String bVal = budgetBox.getValue().trim();
            String player = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
            ShardsShoppingManager.fetchPlanAsync(player, selectedClass, bVal, err -> {
                // Done
            });
        }).bounds(modalX + modalW - 102, modalY + 52, 92, 19).build());

        // Initial fetch if no active plan
        if (!ShardsShoppingManager.hasPlan() && !ShardsShoppingManager.isLoading()) {
            String p = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
            ShardsShoppingManager.fetchPlanAsync(p, selectedClass, currentBudgetText, null);
        }
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
                String bVal = budgetBox != null ? budgetBox.getValue().trim() : "100m";
                String player = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
                ShardsShoppingManager.fetchPlanAsync(player, selectedClass, bVal, null);
                return true;
            }
            pillX += pW + 6;
        }

        // Quick budget pills: 10M, 50M, 100M, 250M, 500M
        String[] quickBudgets = {"10m", "50m", "100m", "250m", "500m"};
        int qX = modalX + 128;
        int qY = modalY + 53;
        for (String qb : quickBudgets) {
            int qW = this.font.width(qb.toUpperCase()) + 8;
            if (mouseX >= qX && mouseX <= qX + qW && mouseY >= qY && mouseY <= qY + 16) {
                if (budgetBox != null) budgetBox.setValue(qb);
                String player = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
                ShardsShoppingManager.fetchPlanAsync(player, selectedClass, qb, null);
                return true;
            }
            qX += qW + 4;
        }

        // Open Web Optimizer button
        int webBtnX = modalX + modalW - 120;
        int webBtnY = modalY + 8;
        if (mouseX >= webBtnX && mouseX <= webBtnX + 110 && mouseY >= webBtnY && mouseY <= webBtnY + 14) {
            String p = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me";
            String url = "https://bomboclas.me/dungeons/shards?player=" + p + "&class=" + selectedClass;
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
        int modalW = Math.min(520, this.width - 20);
        int modalH = Math.min(360, this.height - 20);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Modal backdrop and border (liquid glass obsidian aesthetic)
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA090D16);
        g.outline(modalX, modalY, modalW, modalH, 0x6038BDF8);

        // Header accent bar (cyan glowing accent)
        g.fill(modalX, modalY, modalX + modalW, modalY + 3, 0xFF38BDF8);

        // Title and Subtitle
        g.text(this.font, "§6§lAttribute Shards Optimizer", modalX + 12, modalY + 10, -1, true);
        g.text(this.font, "§7[🌐 Open Web]", modalX + modalW - 95, modalY + 10, 0xFF38BDF8, false);

        // Class Selection Pills row (liquid glass style)
        String[] classes = {"archer", "berserk", "mage", "healer", "tank", "all"};
        int pillX = modalX + 12;
        int pillY = modalY + 28;
        for (String c : classes) {
            boolean active = c.equalsIgnoreCase(selectedClass);
            String title = capitalize(c);
            int pW = this.font.width(title) + 12;
            int bgCol = active ? 0xDD38BDF8 : 0x250F172A;
            int borderCol = active ? 0xFFE0F2FE : 0x4038BDF8;
            int textCol = active ? 0xFF090D16 : 0xFFCBD5E1;
            g.fill(pillX, pillY, pillX + pW, pillY + 16, bgCol);
            g.outline(pillX, pillY, pW, 16, borderCol);
            g.text(this.font, (active ? "§l" : "") + title, pillX + 6, pillY + 4, textCol, false);
            pillX += pW + 6;
        }

        // Budget row label
        g.text(this.font, "§7Budget:", modalX + 12, modalY + 56, -1, true);

        // Quick Budget pills
        String[] quickBudgets = {"10m", "50m", "100m", "250m", "500m"};
        int qX = modalX + 128;
        int qY = modalY + 53;
        for (String qb : quickBudgets) {
            int qW = this.font.width(qb.toUpperCase()) + 8;
            g.fill(qX, qY, qX + qW, qY + 16, 0x250F172A);
            g.outline(qX, qY, qW, 16, 0x4038BDF8);
            g.text(this.font, qb.toUpperCase(), qX + 4, qY + 4, 0xFF38BDF8, false);
            qX += qW + 4;
        }

        // Separator Line
        g.fill(modalX + 10, modalY + 77, modalX + modalW - 10, modalY + 78, 0x3038BDF8);

        // Render widgets (EditBox, Buttons) on top of the background & headers
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Content Area with Scrolling
        int contentX = modalX + 10;
        int contentY = modalY + 82;
        int contentW = modalW - 20;
        int contentH = modalH - 92;

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        synchronized (rowButtons) {
            rowButtons.clear();
        }
        synchronized (hoverTargets) {
            hoverTargets.clear();
        }

        if (ShardsShoppingManager.isLoading()) {
            g.text(this.font, "§e⚡ Calculating optimal shard fusion & bazaar plan...", contentX + 10, contentY + 30, -1, true);
            g.disableScissor();
            return;
        }

        if (ShardsShoppingManager.getLastError() != null) {
            g.text(this.font, "§cError: " + ShardsShoppingManager.getLastError(), contentX + 10, contentY + 30, 0xFFFF5555, true);
            g.disableScissor();
            return;
        }

        PlanData plan = ShardsShoppingManager.getActivePlan();
        if (plan == null) {
            g.text(this.font, "§7Enter your budget and click §eCalculate§7 to view the optimal upgrade list.", contentX + 10, contentY + 30, 0xFF8B949E, false);
            g.disableScissor();
            return;
        }

        int currY = contentY + 6 - scrollOffset;

        // Plan Summary Banner (Liquid Glass style)
        g.fill(contentX, currY, contentX + contentW, currY + 40, 0x250F172A);
        g.outline(contentX, currY, contentW, 40, 0x5038BDF8);

        String spentStr = ShardsShoppingManager.formatCoins(plan.spent);
        String budgetTotalStr = ShardsShoppingManager.formatCoins(plan.budget);
        String remainStr = ShardsShoppingManager.formatCoins(plan.remaining);

        g.text(this.font, "§6Spent: §f" + spentStr + " §8/ §e" + budgetTotalStr + " §7(Remaining: §a" + remainStr + "§7)", contentX + 8, currY + 5, -1, true);
        g.text(this.font, "§eFound Shards: §a" + plan.totalUnlocked + "§8/§f" + plan.totalCatalogShards +
                " §7• §eMaxed Shards: §a" + plan.totalMaxed + "§8/§f" + plan.totalCatalogShards +
                " §7• §eTotal Levels: §a" + plan.totalLevels + "§8/§f" + (plan.totalCatalogShards * 10), contentX + 8, currY + 16, 0xFFCBD5E1, false);
        g.text(this.font, "§a" + plan.upgrades.size() + " Upgrades Planned §7• §b" + plan.directBuys.size() + " Direct Buys §7• §d" + plan.fusions.size() + " Fusions", contentX + 8, currY + 27, 0xFF94A3B8, false);
        currY += 46;

        // 1. Direct Buys Shopping List
        if (!plan.directBuys.isEmpty()) {
            g.text(this.font, "§a§lDIRECT BUYS §7(Click Buy to open Bazaar with Auto-Fill):", contentX + 2, currY, 0xFF34D399, true);
            currY += 14;

            for (DirectBuyItem item : plan.directBuys) {
                g.fill(contentX, currY, contentX + contentW, currY + 20, 0x1A0F172A);
                g.outline(contentX, currY, contentW, 20, 0x3038BDF8);

                String fusionTag = item.forFusion != null ? " §8(" + item.forFusion + ")" : "";
                String rColor = ShardsShoppingManager.getRarityColorCode(item.rarity);
                g.text(this.font, "§f• " + rColor + item.name + " §ax" + item.quantity + fusionTag, contentX + 6, currY + 6, -1, true);
                g.text(this.font, "§7~" + ShardsShoppingManager.formatCoins(item.estimatedCost), contentX + contentW - 140, currY + 6, 0xFF94A3B8, false);

                // Buy Button [ 🛒 BUY ]
                int btnX = contentX + contentW - 65;
                int btnY = currY + 2;
                int btnW = 60;
                int btnH = 16;
                boolean isHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, isHover ? 0xCC059669 : 0x99047857);
                g.outline(btnX, btnY, btnW, btnH, 0xFF10B981);
                g.text(this.font, "🛒 BUY", btnX + 14, btnY + 4, -1, true);

                final String sName = item.name;
                final int sQty = item.quantity;
                synchronized (rowButtons) {
                    rowButtons.add(new ClickableButtonArea(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                        ShardsShoppingManager.buyItem(sName, sQty);
                    }, "Opens /bz and copies " + sQty));
                }

                synchronized (hoverTargets) {
                    hoverTargets.add(new HoverTooltipTarget(contentX, currY, btnX - 4, currY + 20,
                            item.name, item.rarity, item.attrName, item.family, item.effect, item.explanation,
                            item.currentTier, item.currentTier, item.currentStacks, item.maxStacks, item.quantity,
                            item.unitCost, item.estimatedCost));
                }

                currY += 24;
            }
            currY += 8;
        }

        // 2. Fusions Needed
        if (!plan.fusions.isEmpty()) {
            g.text(this.font, "§d§lFUSION CRAFTS §7(Galatea Shards Fusion Helper):", contentX + 2, currY, 0xFFC084FC, true);
            currY += 14;

            for (FusionItem f : plan.fusions) {
                g.fill(contentX, currY, contentX + contentW, currY + 28, 0x1A0F172A);
                g.outline(contentX, currY, contentW, 28, 0x3038BDF8);

                String saveTag = f.savingsPercent > 0 ? String.format(Locale.US, " §a(Save %.1f%%)", f.savingsPercent) : "";
                String rColor = ShardsShoppingManager.getRarityColorCode(f.rarity);
                g.text(this.font, rColor + f.name + " §fx" + f.quantity + " §7(" + f.craftsCount + " crafts)" + saveTag, contentX + 6, currY + 5, -1, true);
                g.text(this.font, "§7Recipe: §f" + f.input1Qty + " " + f.input1Name + " §8+ §f" + f.input2Qty + " " + f.input2Name, contentX + 6, currY + 16, 0xFF94A3B8, false);

                synchronized (hoverTargets) {
                    hoverTargets.add(new HoverTooltipTarget(contentX, currY, contentX + contentW, currY + 28,
                            f.name, f.rarity, "", f.family, f.effect, f.explanation,
                            0, 0, f.currentStacks, f.maxStacks, f.quantity,
                            0, f.estimatedCost));
                }

                currY += 32;
            }
            currY += 8;
        }

        // 3. Upgrades Breakdown
        if (!plan.upgrades.isEmpty()) {
            g.text(this.font, "§e§lTARGET STAT UPGRADES §7(Strict Priority Descending):", contentX + 2, currY, 0xFFFBBF24, true);
            currY += 14;

            for (int i = 0; i < plan.upgrades.size(); i++) {
                PlannedUpgrade u = plan.upgrades.get(i);
                g.fill(contentX, currY, contentX + contentW, currY + 22, 0x1A0F172A);
                g.outline(contentX, currY, contentW, 22, 0x3038BDF8);

                String starStr = "★".repeat(Math.max(1, Math.min(5, u.priority)));
                String rColor = ShardsShoppingManager.getRarityColorCode(u.rarity);
                g.text(this.font, "#" + (i + 1) + " §e" + starStr + " " + rColor + u.name + " §7(" + u.attrName + ")", contentX + 6, currY + 7, -1, true);
                g.text(this.font, "§bTier " + u.fromTier + " ➔ " + u.toTier + " §7(+" + u.shardsNeeded + ")", contentX + contentW - 200, currY + 7, 0xFF38BDF8, false);
                g.text(this.font, "§6" + ShardsShoppingManager.formatCoins(u.cost), contentX + contentW - 75, currY + 7, 0xFFFBBF24, true);

                synchronized (hoverTargets) {
                    hoverTargets.add(new HoverTooltipTarget(contentX, currY, contentX + contentW, currY + 22,
                            u.name, u.rarity, u.attrName, u.family, u.effect, u.explanation,
                            u.fromTier, u.toTier, u.currentStacks, u.maxStacks, u.shardsNeeded,
                            u.unitCost, u.cost));
                }

                currY += 26;
            }
            currY += 8;
        }

        // 4. All Shards Catalog / Maxed Section (Matches Image 3)
        if (showMaxed && plan.allShards != null && !plan.allShards.isEmpty()) {
            g.text(this.font, "§b§lALL ATTRIBUTE SHARDS §7(Showing All Unlocked & Maxed):", contentX + 2, currY, 0xFF38BDF8, true);
            currY += 14;

            for (ShardsShoppingManager.FullShardEntry shard : plan.allShards) {
                g.fill(contentX, currY, contentX + contentW, currY + 18, 0x1A0F172A);
                g.outline(contentX, currY, contentW, 18, 0x3038BDF8);

                String status = shard.isMaxed ? "§a10 Maxed" : (shard.currentTier > 0 ? "§eTier " + shard.currentTier + "/10" : "§7Not Unlocked");
                String rColor = ShardsShoppingManager.getRarityColorCode(shard.rarity);
                g.text(this.font, "§7- " + rColor + shard.name + " Shard " + status, contentX + 6, currY + 5, -1, true);

                if (!shard.isMaxed && shard.costToMax > 0) {
                    g.text(this.font, "§7~" + ShardsShoppingManager.formatCoins(shard.costToMax) + " to max", contentX + contentW - 140, currY + 5, 0xFF8B949E, false);
                } else if (shard.isMaxed) {
                    g.text(this.font, "§a✔ MAX", contentX + contentW - 60, currY + 5, 0xFF2EA043, true);
                }

                synchronized (hoverTargets) {
                    hoverTargets.add(new HoverTooltipTarget(contentX, currY, contentX + contentW, currY + 18,
                            shard.name, shard.rarity, shard.attrName, shard.family, shard.effect, shard.explanation,
                            shard.currentTier, shard.isMaxed ? 10 : shard.currentTier + 1, shard.currentStacks, shard.maxStacks, 0,
                            shard.unitCost, shard.costToMax));
                }

                currY += 22;
            }
        }

        int totalContentHeight = (currY + scrollOffset) - (contentY + 6);
        maxScroll = Math.max(0, totalContentHeight - contentH);

        g.disableScissor();

        // Check if mouse is hovering over any row to render rich tooltip (Image 5 style)
        synchronized (hoverTargets) {
            for (HoverTooltipTarget target : hoverTargets) {
                if (mouseX >= target.minX && mouseX <= target.maxX && mouseY >= target.minY && mouseY <= target.maxY
                        && mouseY >= contentY && mouseY <= contentY + contentH) {
                    ShardsShoppingManager.renderShardTooltip(g, this.font, mouseX, mouseY,
                            target.name, target.rarity, target.attrName, target.family,
                            target.effect, target.explanation,
                            target.currentTier, target.targetTier, target.currentStacks, target.maxStacks, target.shardsNeeded,
                            target.unitCost, target.totalCost);
                    break;
                }
            }
        }

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
