package me.bombo.bomboaddons.features.magicfind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.features.profile.ProfileFetcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class MagicFindScreen extends Screen {

    private final ProfileFetcher.ProfileData profileData;
    private MagicFindOptimizer.AnalysisResult analysis;
    private MagicFindOptimizer.Mode currentMode = MagicFindOptimizer.Mode.GENERAL;
    private MagicFindOptimizer.Category selectedCategory = MagicFindOptimizer.Category.ALL;
    private boolean showMissingOnly = false;
    private boolean sortByEfficiency = false;
    private int scrollOffset = 0;
    private int maxScroll = 0;

    public MagicFindScreen(ProfileFetcher.ProfileData profileData) {
        this(profileData, MagicFindOptimizer.Mode.GENERAL);
    }

    public MagicFindScreen(ProfileFetcher.ProfileData profileData, MagicFindOptimizer.Mode mode) {
        super(Component.literal("§6§lMagic Find Optimizer"));
        this.profileData = profileData;
        this.currentMode = mode != null ? mode : MagicFindOptimizer.Mode.GENERAL;
        refreshAnalysis();
    }

    public void refreshAnalysis() {
        this.analysis = MagicFindOptimizer.evaluateProfile(profileData, currentMode);
        if (this.analysis == null) {
            this.analysis = new MagicFindOptimizer.AnalysisResult();
            this.analysis.username = "Player";
            this.analysis.mode = currentMode;
            this.analysis.sources = MagicFindOptimizer.getSourcesForMode(currentMode);
        }
    }

    @Override
    protected void init() {
        super.init();
        
        // Mode toggle button (General vs Diana)
        int modeBtnW = 160;
        Button modeBtn = Button.builder(Component.literal(currentMode == MagicFindOptimizer.Mode.DIANA ? "§d§l[Mode: DIANA EVENT]" : "§b§l[Mode: GENERAL MF]"), btn -> {
            currentMode = (currentMode == MagicFindOptimizer.Mode.GENERAL) ? MagicFindOptimizer.Mode.DIANA : MagicFindOptimizer.Mode.GENERAL;
            scrollOffset = 0;
            refreshAnalysis();
            this.clearWidgets();
            this.init();
        }).bounds((this.width - modeBtnW) / 2, 6, modeBtnW, 18).build();
        this.addRenderableWidget(modeBtn);

        // Compact Category buttons in top bar
        MagicFindOptimizer.Category[] cats = MagicFindOptimizer.Category.values();
        int btnW = Math.min(78, (this.width - 20) / cats.length - 4);
        int spacing = btnW + 4;
        int startX = (this.width - (cats.length * spacing - 4)) / 2;
        int btnY = 28;

        for (int i = 0; i < cats.length; i++) {
            MagicFindOptimizer.Category cat = cats[i];
            int x = startX + i * spacing;
            Button b = Button.builder(Component.literal((cat == selectedCategory ? "§a" : "§7") + cat.displayName), btn -> {
                selectedCategory = cat;
                scrollOffset = 0;
                this.clearWidgets();
                this.init();
            }).bounds(x, btnY, btnW, 18).build();
            this.addRenderableWidget(b);
        }

        // Filter and Sort toggle buttons below category bar
        this.addRenderableWidget(Button.builder(Component.literal(showMissingOnly ? "§c[Missing Only: ON]" : "§7[Missing Only: OFF]"), btn -> {
            showMissingOnly = !showMissingOnly;
            scrollOffset = 0;
            this.clearWidgets();
            this.init();
        }).bounds(this.width / 2 - 130, 50, 125, 18).build());

        this.addRenderableWidget(Button.builder(Component.literal(sortByEfficiency ? "§e[Sort: Cheapest/MF]" : "§7[Sort: Default]"), btn -> {
            sortByEfficiency = !sortByEfficiency;
            scrollOffset = 0;
            this.clearWidgets();
            this.init();
        }).bounds(this.width / 2 + 5, 50, 125, 18).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background for the whole screen
        graphics.fill(0, 0, this.width, this.height, 0xC0101015);

        // Header Background & divider
        graphics.fill(0, 0, this.width, 74, 0xE0151520);
        graphics.fill(0, 74, this.width, 76, 0xFF404050);

        Font font = this.font != null ? this.font : Minecraft.getInstance().font;
        String user = (analysis != null && analysis.username != null) ? analysis.username : "Player";

        double curMf = analysis != null ? analysis.currentMf : 0.0;
        double maxMf = analysis != null ? analysis.maxPossibleMf : 0.0;
        long totalCost = analysis != null ? analysis.totalCostToMax : 0L;

        String currentMfText = "§7Current MF: §e+" + String.format("%.1f", curMf) + " §8| §7Max Possible: §a+" + String.format("%.1f", maxMf);
        String costText = "§7Cost to Max Missing: §6" + LowestBinManager.formatPrice(totalCost) + " coins";
        graphics.centeredText(font, currentMfText + " §8| " + costText, this.width / 2, 78, -1);

        // Item List Container
        List<MagicFindOptimizer.MfSource> filtered = new ArrayList<>();
        if (analysis != null && analysis.sources != null) {
            for (MagicFindOptimizer.MfSource src : analysis.sources) {
                if (selectedCategory != MagicFindOptimizer.Category.ALL && src.category != selectedCategory) continue;
                if (showMissingOnly && src.isOwned) continue;
                filtered.add(src);
            }
        }

        if (sortByEfficiency) {
            filtered.sort((a, b) -> Double.compare(a.getCostPerMf(), b.getCostPerMf()));
        }

        int itemHeight = 36;
        int listTop = 92;
        int listBottom = this.height - 24;
        int visibleCount = Math.max(1, (listBottom - listTop) / itemHeight);
        maxScroll = Math.max(0, filtered.size() - visibleCount);

        int renderY = listTop;
        int boxW = Math.min(560, this.width - 40);
        int boxX = (this.width - boxW) / 2;

        int startIndex = Math.min(scrollOffset, filtered.size());
        for (int i = startIndex; i < filtered.size() && renderY + itemHeight <= listBottom; i++) {
            MagicFindOptimizer.MfSource src = filtered.get(i);
            boolean isHovered = mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= renderY && mouseY <= renderY + itemHeight - 2;

            int bgColor = isHovered ? 0xE8252838 : 0xD0181A24;
            graphics.fill(boxX, renderY, boxX + boxW, renderY + itemHeight - 2, bgColor);
            graphics.outline(boxX, renderY, boxW, itemHeight - 2, isHovered ? 0xFF606080 : 0xFF353545);

            // Left status indicator bar (Green for Owned, Red for Missing)
            graphics.fill(boxX, renderY, boxX + 4, renderY + itemHeight - 2, src.isOwned ? 0xFF22C55E : 0xFFEF4444);

            // Name and MF Value
            String mfBadge = "§6+" + String.format("%.1f", src.magicFind) + " MF";
            String titleText = (src.isOwned ? "§a✔ §f" : "§c✖ §f") + src.name + " §e(" + mfBadge + "§e)";
            graphics.text(font, titleText, boxX + 12, renderY + 5, -1, true);

            // Description / Category
            graphics.text(font, "§8[" + src.category.displayName + "] §7" + src.description, boxX + 12, renderY + 18, -1, true);

            // Cost & Ownership Status on right
            String statusText;
            if (src.isManualOverride) {
                statusText = "§e[MANUAL OWNED]";
            } else if (src.isOwned) {
                statusText = "§a[OWNED]";
            } else {
                long cost = src.getEstimatedCost();
                statusText = cost <= 0L ? "§b[Free/Milestone]" : "§6" + LowestBinManager.formatPrice(cost) + " coins";
            }
            int statusW = font.width(statusText);
            graphics.text(font, statusText, boxX + boxW - statusW - 10, renderY + 12, -1, true);

            renderY += itemHeight;
        }

        // Footer instructions & count
        String footer = "§7Showing " + filtered.size() + " sources | [Scroll] Navigate | §e[Right-Click] Manual Override | §b/b stat mf diana";
        graphics.centeredText(font, footer, this.width / 2, this.height - 14, 0xFFAAAAAA);

        // Render widgets (buttons) on top
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDown) {
        if (!isDown) return super.mouseClicked(event, isDown);
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int itemHeight = 36;
        int listTop = 92;
        int listBottom = this.height - 24;
        int boxW = Math.min(560, this.width - 40);
        int boxX = (this.width - boxW) / 2;

        List<MagicFindOptimizer.MfSource> filtered = new ArrayList<>();
        if (analysis != null && analysis.sources != null) {
            for (MagicFindOptimizer.MfSource src : analysis.sources) {
                if (selectedCategory != MagicFindOptimizer.Category.ALL && src.category != selectedCategory) continue;
                if (showMissingOnly && src.isOwned) continue;
                filtered.add(src);
            }
        }

        if (sortByEfficiency) {
            filtered.sort((a, b) -> Double.compare(a.getCostPerMf(), b.getCostPerMf()));
        }

        int renderY = listTop;
        int startIndex = Math.min(scrollOffset, filtered.size());
        for (int i = startIndex; i < filtered.size() && renderY + itemHeight <= listBottom; i++) {
            MagicFindOptimizer.MfSource src = filtered.get(i);
            if (mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= renderY && mouseY <= renderY + itemHeight - 2) {
                if (button == 1) { // Right-Click to toggle override
                    MagicFindOptimizer.toggleManualOverride(src.id);
                    refreshAnalysis();
                    return true;
                }
            }
            renderY += itemHeight;
        }

        return super.mouseClicked(event, isDown);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0.0) {
            scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
