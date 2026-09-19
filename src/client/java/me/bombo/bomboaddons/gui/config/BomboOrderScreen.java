package me.bombo.bomboaddons.gui.config;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class BomboOrderScreen extends Screen {
    private final Screen parent;

    // Window Layout
    private int winX, winY, winW, winH;
    private int sidebarW = 180;
    private int headerH = 42;

    // Scroll state
    private double sidebarScroll = 0.0;
    private double maxSidebarScroll = 0.0;
    private double featureScroll = 0.0;
    private double maxFeatureScroll = 0.0;

    // State
    private String selectedCategory = "ALL";
    private String searchFilter = "";
    private int searchCursorPos = 0;
    private boolean searchFocused = false;

    // Drag and Drop state
    private FeatureOrganizerManager.FeatureMeta pendingDragFeature = null;
    private double featurePressX = 0;
    private double featurePressY = 0;
    private boolean isFeatureDragging = false;
    private FeatureOrganizerManager.FeatureMeta draggingFeature = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private String pendingDragCategory = null;
    private double categoryPressX = 0;
    private double categoryPressY = 0;
    private boolean isCategoryDragging = false;
    private String draggingCategory = null;
    private int dragCategoryOffsetY = 0;

    // Config Control interaction state in specific category view
    private ConfigItem activeKeybindItem = null;
    private ConfigItem hoveredSliderItem = null;

    // Category Context Menu (Right-Click on sidebar item or empty space)
    private boolean contextMenuOpen = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private String contextTargetCategory = null; // null if empty spot
    private boolean isCreatingCategory = false;
    private String categoryNameInput = "";
    private boolean isRenamingCategory = false;

    // Feature Edit Modal (Right-Click on a feature card)
    private boolean isEditingFeature = false;
    private FeatureOrganizerManager.FeatureMeta editingFeature = null;
    private String editFeatureNameInput = "";
    private String editFeatureDescInput = "";
    private boolean editFeatureEnabledByDefault = false;
    private int editFeatureFocusField = 0; // 0 = Name, 1 = Description
    private int editFeatureCursorPos = 0;

    // Category Creation/Renaming modal cursor
    private int categoryNameCursorPos = 0;

    public BomboOrderScreen(Screen parent) {
        super(Component.literal("Bombo Feature & Category Organizer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FeatureOrganizerManager.initDefaults();
        BomboConfig.Settings s = BomboConfig.get();
        float scale = s.guiWindowScale > 0.5f ? s.guiWindowScale : 1.0f;
        this.sidebarW = Math.max(140, Math.min(260, s.guiSidebarWidth > 0 ? s.guiSidebarWidth : 180));

        int targetW = (int) (900 * scale);
        int targetH = (int) (560 * scale);

        this.winW = Math.min(this.width - 24, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 1. Dark Backdrop
        g.fill(0, 0, this.width, this.height, 0xD0080A0E);

        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int sidebarBg = ConfigUITheme.getSidebarBg();
        int borderCol = ConfigUITheme.getBorderColor();
        int divCol = ConfigUITheme.getDividerColor();
        int accent = ConfigUITheme.getAccentColor();

        // 2. Main Window Box
        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);
        g.outline(winX, winY, winW, winH, borderCol);

        // 3. Header Bar
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.fill(winX, winY + headerH, winX + winW, winY + headerH + 1, divCol);

        g.text(this.font, "§d§lBOMBOADDONS §8| §eCategory & Feature Organizer (/b order)", winX + 16, winY + 14, 0xFFFFFFFF, false);

        // Search box in header
        int searchW = 180;
        int searchX = winX + winW - searchW - 40;
        int searchY = winY + 11;
        g.fill(searchX, searchY, searchX + searchW, searchY + 20, searchFocused ? 0xEE1E293B : 0xAA1E293B);
        g.outline(searchX, searchY, searchW, 20, searchFocused ? accent : 0x4464748B);
        String searchStr;
        if (searchFilter.isEmpty() && !searchFocused) {
            searchStr = "§8Search features...";
        } else if (searchFocused) {
            int cur = Math.max(0, Math.min(searchFilter.length(), searchCursorPos));
            searchStr = searchFilter.substring(0, cur) + (System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "|") + searchFilter.substring(cur);
        } else {
            searchStr = searchFilter;
        }
        g.text(this.font, searchStr, searchX + 6, searchY + 6, 0xFFFFFFFF, false);

        // Close Button
        int closeBtnX = winX + winW - 30;
        int closeBtnY = winY + 11;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 20 && mouseY >= closeBtnY && mouseY <= closeBtnY + 20;
        ConfigUITheme.drawPillButton(g, this.font, "✕", closeBtnX, closeBtnY, 20, 20, closeHover,
                closeHover ? 0xFFFF6666 : 0xFF94A3B8, closeHover ? 0x44EF4444 : 0x22EF4444, closeHover ? 0xFFEF4444 : 0x44EF4444);

        // 4. Sidebar Categories
        int sideX = winX;
        int sideY = winY + headerH + 1;
        int sideH = winH - headerH - 1;

        g.fill(sideX, sideY, sideX + sidebarW, sideY + sideH, sidebarBg);
        g.fill(sideX + sidebarW, sideY, sideX + sidebarW + 1, sideY + sideH, divCol);

        renderSidebar(g, sideX, sideY, sidebarW, sideH, mouseX, mouseY);

        // 5. Main Feature Grid / List (Features to drag into categories)
        int contentX = sideX + sidebarW + 12;
        int contentY = sideY + 8;
        int contentW = winW - sidebarW - 24;
        int contentH = sideH - 16;

        renderFeatureList(g, contentX, contentY, contentW, contentH, mouseX, mouseY);

        // 6. Right-Click Category Context Menu Modal
        if (contextMenuOpen) {
            renderContextMenu(g, mouseX, mouseY);
        }

        // 7. Modal for Adding / Renaming Category
        if (isCreatingCategory || isRenamingCategory) {
            renderCategoryModal(g, mouseX, mouseY);
        }

        // 7b. Modal for Editing Feature Name & Description
        if (isEditingFeature && editingFeature != null) {
            renderFeatureEditModal(g, mouseX, mouseY);
        }

        // 8. Render Dragged Feature Floating under cursor
        if (draggingFeature != null) {
            int cardW = 210;
            int cardH = 34;
            int dx = mouseX - dragOffsetX;
            int dy = mouseY - dragOffsetY;

            g.fill(dx, dy, dx + cardW, dy + cardH, 0xEE1E293B);
            g.outline(dx, dy, cardW, cardH, 0xFFFFAA00);
            g.text(this.font, "§e" + draggingFeature.name, dx + 8, dy + 6, 0xFFFFFFFF, true);
            g.text(this.font, "§7→ Drop to reorder or link", dx + 8, dy + 18, 0xFFAAAAAA, false);
        }

        // 8b. Render Dragged Category Floating under cursor
        if (draggingCategory != null) {
            int catW = sidebarW - 12;
            int catH = 20;
            int dx = mouseX - 10;
            int dy = mouseY - dragCategoryOffsetY;

            g.fill(dx, dy, dx + catW, dy + catH, 0xEE0F172A);
            g.outline(dx, dy, catW, catH, 0xFF38BDF8);
            g.text(this.font, "§b" + draggingCategory, dx + 10, dy + 6, 0xFFFFFFFF, true);
        }
    }

    private boolean isFeatureVisible(FeatureOrganizerManager.FeatureMeta fm) {
        if (fm == null) return false;
        BomboConfig.Settings s = BomboConfig.get();
        if ("Circle Radius".equalsIgnoreCase(fm.name)) {
            if (s != null && !"CIRCLE".equalsIgnoreCase(s.backpackPreviewRarityShape)) return false;
        }
        if ("Preview Keybind".equalsIgnoreCase(fm.name) || "Backpack Preview Keybind".equalsIgnoreCase(fm.name)) {
            if (s != null && !"ON_KEY".equalsIgnoreCase(s.backpackPreviewTrigger)) return false;
        }
        if (fm.parentDependency != null && !fm.parentDependency.trim().isEmpty()) {
            ConfigItem parentItem = ConfigRegistry.getMasterItemsMap().get(fm.parentDependency.trim());
            if (parentItem != null && parentItem.boolGetter != null) {
                if (!Boolean.TRUE.equals(parentItem.boolGetter.get())) return false;
            }
        }
        return true;
    }

    private void renderSidebar(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        int accent = ConfigUITheme.getAccentColor();
        List<String> categories = new ArrayList<>(FeatureOrganizerManager.customCategories);

        int listH = h - 34;
        g.enableScissor(x, y, x + w, y + listH);

        int curY = y + 6 - (int) this.sidebarScroll;
        int totalHeight = 0;

        // "ALL" Category option
        boolean allActive = "ALL".equalsIgnoreCase(selectedCategory);
        boolean allHover = mouseX >= x + 6 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 20 && mouseY >= y && mouseY <= y + listH;
        if (allActive) {
            g.fill(x + 6, curY, x + w - 12, curY + 20, 0x33000000 | (accent & 0x00FFFFFF));
            g.fill(x + 6, curY, x + 9, curY + 20, accent);
        } else if (allHover) {
            g.fill(x + 6, curY, x + w - 12, curY + 20, 0x1AFFFFFF);
        }
        g.text(this.font, "★ ALL Features", x + 16, curY + 6, allActive ? accent : (allHover ? 0xFFFFFFFF : 0xFF94A3B8), false);
        curY += 22;
        totalHeight += 22;

        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            boolean active = cat.equalsIgnoreCase(selectedCategory);
            boolean hover = mouseX >= x + 6 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 20 && mouseY >= y && mouseY <= y + listH;
            boolean isDragged = cat.equals(draggingCategory);

            // Check if feature or category is being dragged over this slot
            boolean dragHover = (draggingFeature != null || draggingCategory != null) && hover && !isDragged;

            if (isDragged) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x2238BDF8);
                g.outline(x + 6, curY, w - 18, 20, 0x4438BDF8);
            } else if (dragHover) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x5510B981);
                g.outline(x + 6, curY, w - 18, 20, 0xFF10B981);
            } else if (active) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x33000000 | (accent & 0x00FFFFFF));
                g.fill(x + 6, curY, x + 9, curY + 20, accent);
            } else if (hover) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x1AFFFFFF);
            }

            int count = 0;
            for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                if (fm.category.equalsIgnoreCase(cat) && isFeatureVisible(fm)) count++;
            }

            int textColor = isDragged ? 0xFF64748B : (active ? accent : (hover ? 0xFFFFFFFF : 0xFF94A3B8));
            g.text(this.font, cat, x + 16, curY + 6, textColor, false);
            g.text(this.font, "§8" + count, x + w - 24 - this.font.width(String.valueOf(count)), curY + 6, 0xFF64748B, false);

            curY += 22;
            totalHeight += 22;
        }

        // Empty Spot / Add New Category button at the end
        boolean addHover = mouseX >= x + 6 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 20 && mouseY >= y && mouseY <= y + listH;
        g.fill(x + 6, curY, x + w - 12, curY + 20, addHover ? 0x3322C55E : 0x1A22C55E);
        g.outline(x + 6, curY, w - 18, 20, addHover ? 0xFF4ADE80 : 0x4422C55E);
        g.text(this.font, "§a+ Add Category", x + 16, curY + 6, addHover ? 0xFF86EFAC : 0xFF4ADE80, false);
        curY += 24;
        totalHeight += 24;

        g.disableScissor();
        this.maxSidebarScroll = Math.max(0, totalHeight - listH + 16);
    }

    private void renderFeatureList(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<FeatureOrganizerManager.FeatureMeta> list = getFilteredFeatures();
        boolean isAllView = "ALL".equalsIgnoreCase(selectedCategory);

        int startY = y + 4 - (int) this.featureScroll;
        int totalH;

        g.enableScissor(x, y, x + w, y + h);

        if (isAllView) {
            // Compact multi-column tiles for ALL features overview
            int cardW = 210;
            int cardH = 34;
            int cols = Math.max(1, w / (cardW + 10));
            int rows = (list.size() + cols - 1) / cols;
            totalH = rows * (cardH + 8);

            for (int i = 0; i < list.size(); i++) {
                FeatureOrganizerManager.FeatureMeta fm = list.get(i);
                int col = i % cols;
                int row = i / cols;

                int cx = x + col * (cardW + 10);
                int cy = startY + row * (cardH + 8);

                if (cy + cardH >= y && cy <= y + h) {
                    boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
                    boolean isDragged = (draggingFeature == fm);

                    int cardBg = isDragged ? 0x44334155 : (hover ? 0xEE1E293B : 0xAA111827);
                    int borderCol = hover ? 0xFF38BDF8 : 0x33475569;

                    g.fill(cx, cy, cx + cardW, cy + cardH, cardBg);
                    g.outline(cx, cy, cardW, cardH, borderCol);

                    int badgeSize = 16;
                    int badgeX = cx + cardW - badgeSize - 6;
                    int badgeY = cy + 9;
                    boolean badgeHover = mouseX >= badgeX && mouseX <= badgeX + badgeSize && mouseY >= badgeY && mouseY <= badgeY + badgeSize;
                    int badgeBg = fm.isCheat ? (badgeHover ? 0xEE991B1B : 0xAA7F1D1D) : (badgeHover ? 0xEE15803D : 0xAA14532D);
                    int badgeBorder = fm.isCheat ? 0xFFEF4444 : 0xFF22C55E;
                    g.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, badgeBg);
                    g.outline(badgeX, badgeY, badgeSize, badgeSize, badgeBorder);
                    g.text(this.font, fm.isCheat ? "§c✕" : "§a✓", badgeX + 4, badgeY + 4, 0xFFFFFFFF, false);

                    int maxNameW = badgeX - (cx + 8) - 4;
                    String nameDisp = fm.name;
                    if (this.font.width(nameDisp) > maxNameW) {
                        nameDisp = this.font.plainSubstrByWidth(fm.name, maxNameW - this.font.width("..")) + "..";
                    }
                    g.text(this.font, nameDisp, cx + 8, cy + 6, 0xFFFFFFFF, false);

                    int maxCatW = badgeX - (cx + 8) - 4;
                    String catTag = "§8[" + fm.category + "]";
                    if (fm.parentDependency != null && !fm.parentDependency.isEmpty()) {
                        catTag += " §6↳ " + fm.parentDependency;
                    }
                    if (this.font.width(catTag) > maxCatW) {
                        catTag = this.font.plainSubstrByWidth(catTag, maxCatW - this.font.width("..")) + "..";
                    }
                    g.text(this.font, catTag, cx + 8, cy + 19, 0xFF94A3B8, false);
                }
            }
        } else {
            // Rich full-width Config Cards matching BomboConfigScreen layout
            int cardW = w - 12;
            int cardH = 40;
            totalH = list.size() * (cardH + 6);
            int accent = ConfigUITheme.getAccentColor();

            for (int i = 0; i < list.size(); i++) {
                FeatureOrganizerManager.FeatureMeta fm = list.get(i);
                int cx = x + 4;
                int cy = startY + i * (cardH + 6);

                if (cy + cardH >= y && cy <= y + h) {
                    boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
                    boolean isDragged = (draggingFeature == fm);
                    boolean handleHover = mouseX >= cx && mouseX <= cx + 22 && mouseY >= cy && mouseY <= cy + cardH;

                    int cardBg = isDragged ? 0x44334155 : (hover ? 0xEE1E293B : 0xAA0F172A);
                    int borderCol = isDragged ? 0xFFFFAA00 : (hover ? 0xFF38BDF8 : 0x33475569);

                    g.fill(cx, cy, cx + cardW, cy + cardH, cardBg);
                    g.outline(cx, cy, cardW, cardH, borderCol);

                    // Reorder Handle on left (highlighted on hover/drag)
                    g.text(this.font, "§8☰", cx + 8, cy + 14, handleHover ? 0xFF38BDF8 : (hover ? 0xFF94A3B8 : 0xFF64748B), false);

                    // Right Side Controls (matched ConfigItem if present)
                    ConfigItem item = ConfigRegistry.getMasterItemsMap().get(fm.name);
                    int ctrlRightX = cx + cardW - 32;
                    int ctrlY = cy + 11;

                    // Cheat/Legit Badge (small 16x16 box)
                    int badgeW = 16;
                    int badgeX = ctrlRightX - 2;
                    int badgeY = ctrlY + 1;
                    boolean badgeHover = mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + 16;
                    int badgeBg = fm.isCheat ? (badgeHover ? 0xEE991B1B : 0xAA7F1D1D) : (badgeHover ? 0xEE15803D : 0xAA14532D);
                    int badgeBorder = fm.isCheat ? 0xFFEF4444 : 0xFF22C55E;
                    g.fill(badgeX, badgeY, badgeX + badgeW, badgeY + 16, badgeBg);
                    g.outline(badgeX, badgeY, badgeW, 16, badgeBorder);
                    g.text(this.font, fm.isCheat ? "§c✕" : "§a✓", badgeX + 4, badgeY + 4, 0xFFFFFFFF, false);

                    // Shift controls left to make room for badge
                    int ctrlEnd = badgeX - 8;
                    int controlsLeft = ctrlEnd;
                    if (item != null) {
                        switch (item.type) {
                            case TOGGLE -> controlsLeft = ctrlEnd - 34;
                            case KEYBIND, CYCLE, BUTTON -> controlsLeft = ctrlEnd - 80;
                            case SLIDER_INT, SLIDER_FLOAT -> controlsLeft = ctrlEnd - 135;
                            case COLOR -> controlsLeft = ctrlEnd - 50;
                            default -> {}
                        }
                    }

                    // Feature Name & Parent Indicator
                    String title = ConfigUITheme.formatFont(fm.name);
                    if (fm.parentDependency != null && !fm.parentDependency.trim().isEmpty()) {
                        title += " §6↳ Requires: " + fm.parentDependency.trim();
                    }
                    int maxTitleW = Math.max(100, controlsLeft - (cx + 28) - 8);
                    if (this.font.width(title) > maxTitleW) {
                        title = this.font.plainSubstrByWidth(title, maxTitleW - this.font.width("..")) + "..";
                    }
                    g.text(this.font, title, cx + 24, cy + 7, 0xFFFFFFFF, false);

                    // Description / Subtitle
                    String desc = fm.description;
                    if (desc == null || desc.trim().isEmpty()) {
                        if (item != null && item.description != null) desc = item.description;
                    }
                    if (desc != null && !desc.isEmpty()) {
                        int maxDescW = Math.max(100, controlsLeft - (cx + 28) - 8);
                        String descDisp = desc;
                        if (this.font.width(descDisp) > maxDescW) {
                            descDisp = this.font.plainSubstrByWidth(desc, maxDescW - this.font.width("...")) + "...";
                        }
                        g.text(this.font, "§7" + ConfigUITheme.formatFont(descDisp), cx + 24, cy + 22, 0xFF94A3B8, false);
                    }

                    // Edit Pencil Button
                    int editBtnX = cx + cardW - 18;
                    boolean editHover = mouseX >= editBtnX && mouseX <= editBtnX + 16 && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                    ConfigUITheme.drawPillButton(g, this.font, "✎", editBtnX, ctrlY, 16, 18, editHover, editHover ? 0xFFFFFFFF : 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);

                    if (item != null) {
                        switch (item.type) {
                            case TOGGLE -> {
                                boolean active = item.boolGetter != null && item.boolGetter.get();
                                int toggleW = 34;
                                int toggleH = 18;
                                int toggleX = ctrlEnd - toggleW;
                                boolean toggleHover = mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= ctrlY && mouseY <= ctrlY + toggleH;
                                ConfigUITheme.drawToggleSwitch(g, toggleX, ctrlY, toggleW, toggleH, active, toggleHover);
                            }
                            case KEYBIND -> {
                                boolean listening = (activeKeybindItem == item);
                                String currentKey = item.stringGetter != null ? item.stringGetter.get() : "";
                                String displayKey = listening ? "§e[PRESS KEY]" : (currentKey.isEmpty() ? "§8None" : "§b" + ClickLogic.getKeyDisplayName(currentKey));
                                int keyW = 80;
                                int keyX = ctrlEnd - keyW;
                                boolean keyHover = mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                                int bg = listening ? 0x44FFAA00 : 0x22FFFFFF;
                                int border = listening ? 0xFFFFAA00 : 0x44FFFFFF;
                                ConfigUITheme.drawPillButton(g, this.font, displayKey, keyX, ctrlY, keyW, 18, keyHover, -1, bg, border);
                            }
                            case SLIDER_INT -> {
                                if (hover) this.hoveredSliderItem = item;
                                int val = item.intGetter != null ? item.intGetter.get() : 0;
                                int min = item.minInt;
                                int max = item.maxInt;
                                float pct = max > min ? Math.max(0.0f, Math.min(1.0f, (float) (val - min) / (max - min))) : 0.0f;
                                int trackW = 70;
                                int trackH = 6;
                                int trackX = ctrlEnd - 135;
                                int trackY = ctrlY + 6;
                                g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x33FFFFFF);
                                int filledW = (int) (pct * trackW);
                                if (filledW > 0) g.fill(trackX, trackY, trackX + filledW, trackY + trackH, accent);
                                int thumbX = trackX + filledW;
                                g.fill(thumbX - 4, trackY - 3, thumbX + 4, trackY + trackH + 3, 0xFFFFFFFF);
                                g.text(this.font, "§e" + val + item.intSuffix, ctrlEnd - 55, ctrlY + 5, -1, false);
                            }
                            case SLIDER_FLOAT -> {
                                if (hover) this.hoveredSliderItem = item;
                                float val = item.floatGetter != null ? item.floatGetter.get() : 0f;
                                float min = item.minFloat;
                                float max = item.maxFloat;
                                float pct = max > min ? Math.max(0.0f, Math.min(1.0f, (val - min) / (max - min))) : 0.0f;
                                int trackW = 70;
                                int trackH = 6;
                                int trackX = ctrlEnd - 135;
                                int trackY = ctrlY + 6;
                                g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x33FFFFFF);
                                int filledW = (int) (pct * trackW);
                                if (filledW > 0) g.fill(trackX, trackY, trackX + filledW, trackY + trackH, accent);
                                int thumbX = trackX + filledW;
                                g.fill(thumbX - 4, trackY - 3, thumbX + 4, trackY + trackH + 3, 0xFFFFFFFF);
                                g.text(this.font, String.format(Locale.ROOT, "§e%.1f%s", val, item.floatSuffix), ctrlEnd - 55, ctrlY + 5, -1, false);
                            }
                            case CYCLE -> {
                                String currentVal = item.stringGetter != null ? item.stringGetter.get() : "";
                                int btnW = 80;
                                int btnX = ctrlEnd - btnW;
                                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                                ConfigUITheme.drawPillButton(g, this.font, "§f" + currentVal, btnX, ctrlY, btnW, 18, btnHover, -1, 0x22FFFFFF, 0x44FFFFFF);
                            }
                            default -> {}
                        }
                    }
                }
            }
        }

        g.disableScissor();
        this.maxFeatureScroll = Math.max(0, totalH - h + 20);

        if (totalH > h) {
            int trackX = x + w - 4;
            int trackH = h;
            int thumbH = Math.max(16, (int) ((double) h * h / totalH));
            int thumbY = y + (int) ((trackH - thumbH) * (featureScroll / maxFeatureScroll));
            ConfigUITheme.drawScrollBar(g, trackX, y, 4, trackH, thumbY, thumbH);
        }
    }

    private List<FeatureOrganizerManager.FeatureMeta> getFilteredFeatures() {
        List<FeatureOrganizerManager.FeatureMeta> list = new ArrayList<>();
        String q = searchFilter.toLowerCase(Locale.ROOT).trim();
        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
            if (!isFeatureVisible(fm)) continue;
            if (!"ALL".equalsIgnoreCase(selectedCategory) && !fm.category.equalsIgnoreCase(selectedCategory)) {
                continue;
            }
            if (!q.isEmpty()) {
                String nameLower = fm.name.toLowerCase(Locale.ROOT);
                String catLower = fm.category.toLowerCase(Locale.ROOT);
                String descLower = fm.description != null ? fm.description.toLowerCase(Locale.ROOT) : "";

                boolean matches = nameLower.contains(q) || catLower.contains(q) || descLower.contains(q);

                // Specific keyword synonym expansions
                if (!matches && q.contains("lore")) {
                    matches = nameLower.contains("price") || nameLower.contains("bazaar") || nameLower.contains("museum")
                            || nameLower.contains("quality") || nameLower.contains("leather") || nameLower.contains("tooltip")
                            || nameLower.contains("value") || nameLower.contains("creation");
                }

                if (!matches) continue;
            }
            list.add(fm);
        }
        return list;
    }

    private void renderContextMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int mw = 150;
        int mh = contextTargetCategory != null ? 124 : 26;
        int mx = Math.min(this.width - mw - 10, contextMenuX);
        int my = Math.min(this.height - mh - 10, contextMenuY);

        g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, 0xFF38BDF8);
        g.fill(mx, my, mx + mw, my + mh, 0xF00F172A);

        int curY = my + 3;

        if (contextTargetCategory != null) {
            // Move Up
            boolean hoverUp = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverUp) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x3338BDF8);
            g.text(this.font, "▲ Move Up", mx + 8, curY + 5, hoverUp ? 0xFF38BDF8 : 0xFFFFFFFF, false);
            curY += 20;

            // Move Down
            boolean hoverDown = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverDown) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x3338BDF8);
            g.text(this.font, "▼ Move Down", mx + 8, curY + 5, hoverDown ? 0xFF38BDF8 : 0xFFFFFFFF, false);
            curY += 20;

            // Rename Category
            boolean hoverRename = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverRename) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x3338BDF8);
            g.text(this.font, "✏ Rename Category", mx + 8, curY + 5, hoverRename ? 0xFF38BDF8 : 0xFFFFFFFF, false);
            curY += 20;

            // Delete Category
            boolean hoverDelete = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverDelete) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x33EF4444);
            g.text(this.font, "🗑 §cDelete Category", mx + 8, curY + 5, hoverDelete ? 0xFFFF6666 : 0xFFEF4444, false);
            curY += 20;

            // Mark All as Legit
            boolean hoverLegit = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverLegit) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x3322C55E);
            g.text(this.font, "🛡 Mark All as Legit", mx + 8, curY + 5, hoverLegit ? 0xFF86EFAC : 0xFF22C55E, false);
            curY += 20;

            // Mark All as Cheat
            boolean hoverCheat = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverCheat) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x33DC2626);
            g.text(this.font, "⚡ Mark All as Cheat", mx + 8, curY + 5, hoverCheat ? 0xFFFCA5A5 : 0xFFDC2626, false);
        } else {
            // Add New Category
            boolean hoverNew = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 20;
            if (hoverNew) g.fill(mx + 2, curY, mx + mw - 2, curY + 20, 0x3322C55E);
            g.text(this.font, "➕ §aCreate New Category", mx + 8, curY + 6, hoverNew ? 0xFF86EFAC : 0xFF22C55E, false);
        }
    }

    private void renderCategoryModal(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int mw = 260;
        int mh = 100;
        int mx = (this.width - mw) / 2;
        int my = (this.height - mh) / 2;

        g.fill(mx, my, mx + mw, my + mh, 0xFF0F172A);
        g.outline(mx, my, mw, mh, 0xFF38BDF8);

        String title = isCreatingCategory ? "Create New Category" : "Rename Category (" + contextTargetCategory + ")";
        g.text(this.font, "§b§l" + title, mx + 16, my + 14, 0xFFFFFFFF, false);

        int inputX = mx + 16;
        int inputY = my + 36;
        int inputW = mw - 32;
        g.fill(inputX, inputY, inputX + inputW, inputY + 20, 0xFF1E293B);
        g.outline(inputX, inputY, inputW, 20, 0xFF38BDF8);

        categoryNameCursorPos = Math.max(0, Math.min(categoryNameInput.length(), categoryNameCursorPos));
        g.text(this.font, categoryNameInput, inputX + 6, inputY + 6, 0xFFFFFFFF, false);
        if (System.currentTimeMillis() / 500 % 2 == 0) {
            int cursorX = inputX + 6 + this.font.width(categoryNameInput.substring(0, categoryNameCursorPos));
            g.fill(cursorX, inputY + 4, cursorX + 1, inputY + 16, 0xFF38BDF8);
        }

        // [Confirm] and [Cancel]
        int btnW = 80;
        int btnH = 20;
        int cBtnX = mx + mw - 16 - btnW;
        int canBtnX = cBtnX - btnW - 8;
        int btnY = my + mh - 28;

        boolean canHover = mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean confHover = mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        ConfigUITheme.drawPillButton(g, this.font, "Cancel", canBtnX, btnY, btnW, btnH, canHover, 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);
        ConfigUITheme.drawPillButton(g, this.font, "Confirm", cBtnX, btnY, btnW, btnH, confHover, 0xFFFFFFFF, 0x440284C7, 0xFF0284C7);
    }

    private void renderFeatureEditModal(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int mw = 320;
        int mh = 210;
        int mx = (this.width - mw) / 2;
        int my = (this.height - mh) / 2;

        g.fill(mx, my, mx + mw, my + mh, 0xFF0F172A);
        g.outline(mx, my, mw, mh, 0xFF38BDF8);

        g.text(this.font, "§b§lEdit Feature Details", mx + 16, my + 12, 0xFFFFFFFF, false);

        // Name input label + box
        g.text(this.font, "§7Feature Name:", mx + 16, my + 28, 0xFF94A3B8, false);
        int nameInputX = mx + 16;
        int nameInputY = my + 40;
        int nameInputW = mw - 32;
        g.fill(nameInputX, nameInputY, nameInputX + nameInputW, nameInputY + 20, 0xFF1E293B);
        g.outline(nameInputX, nameInputY, nameInputW, 20, editFeatureFocusField == 0 ? 0xFF38BDF8 : 0x3364748B);

        String nameText = editFeatureNameInput;
        g.text(this.font, nameText, nameInputX + 6, nameInputY + 6, 0xFFFFFFFF, false);
        if (editFeatureFocusField == 0 && (System.currentTimeMillis() / 500 % 2 == 0)) {
            editFeatureCursorPos = Math.max(0, Math.min(nameText.length(), editFeatureCursorPos));
            int cursorX = nameInputX + 6 + this.font.width(nameText.substring(0, editFeatureCursorPos));
            g.fill(cursorX, nameInputY + 4, cursorX + 1, nameInputY + 16, 0xFF38BDF8);
        }

        // Description input label + box
        g.text(this.font, "§7Description:", mx + 16, my + 66, 0xFF94A3B8, false);
        int descInputX = mx + 16;
        int descInputY = my + 78;
        int descInputW = mw - 32;
        g.fill(descInputX, descInputY, descInputX + descInputW, descInputY + 34, 0xFF1E293B);
        g.outline(descInputX, descInputY, descInputW, 34, editFeatureFocusField == 1 ? 0xFF38BDF8 : 0x3364748B);

        String descText = editFeatureDescInput;
        editFeatureCursorPos = Math.max(0, Math.min(descText.length(), editFeatureCursorPos));

        if (descText.length() > 48) {
            String line1 = descText.substring(0, 48);
            String line2 = descText.substring(48);
            g.text(this.font, line1, descInputX + 6, descInputY + 5, 0xFFFFFFFF, false);
            g.text(this.font, line2, descInputX + 6, descInputY + 18, 0xFFFFFFFF, false);

            if (editFeatureFocusField == 1 && (System.currentTimeMillis() / 500 % 2 == 0)) {
                if (editFeatureCursorPos <= 48) {
                    int cursorX = descInputX + 6 + this.font.width(descText.substring(0, editFeatureCursorPos));
                    g.fill(cursorX, descInputY + 4, cursorX + 1, descInputY + 15, 0xFF38BDF8);
                } else {
                    int cursorX = descInputX + 6 + this.font.width(descText.substring(48, editFeatureCursorPos));
                    g.fill(cursorX, descInputY + 17, cursorX + 1, descInputY + 28, 0xFF38BDF8);
                }
            }
        } else {
            g.text(this.font, descText, descInputX + 6, descInputY + 6, 0xFFFFFFFF, false);
            if (editFeatureFocusField == 1 && (System.currentTimeMillis() / 500 % 2 == 0)) {
                int cursorX = descInputX + 6 + this.font.width(descText.substring(0, editFeatureCursorPos));
                g.fill(cursorX, descInputY + 4, cursorX + 1, descInputY + 16, 0xFF38BDF8);
            }
        }

        // Parent Dependency row
        int depY = my + 118;
        String curDep = editingFeature != null && editingFeature.parentDependency != null ? editingFeature.parentDependency : "";
        if (!curDep.isEmpty()) {
            g.text(this.font, "§7Requires: §6" + curDep, mx + 16, depY + 4, 0xFFFFFFFF, false);
            int clearW = 105;
            int clearX = mx + mw - 16 - clearW;
            boolean clearHover = mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= depY && mouseY <= depY + 16;
            ConfigUITheme.drawPillButton(g, this.font, "✕ Clear Req", clearX, depY, clearW, 16, clearHover, clearHover ? 0xFFFF6666 : 0xFFEF4444, 0x22EF4444, 0x44EF4444);
        } else {
            g.text(this.font, "§8No parent requirement", mx + 16, depY + 4, 0xFF64748B, false);
        }

        // Enabled by Default checkbox row
        int defY = my + 142;
        int togBoxX = mx + 16;
        int togBoxY = defY;
        boolean togHover = mouseX >= togBoxX && mouseX <= togBoxX + 180 && mouseY >= togBoxY && mouseY <= togBoxY + 16;
        g.fill(togBoxX, togBoxY, togBoxX + 14, togBoxY + 14, editFeatureEnabledByDefault ? 0xFF0284C7 : 0xFF1E293B);
        g.outline(togBoxX, togBoxY, 14, 14, editFeatureEnabledByDefault ? 0xFF38BDF8 : (togHover ? 0x8864748B : 0x4464748B));
        if (editFeatureEnabledByDefault) {
            g.text(this.font, "✓", togBoxX + 3, togBoxY + 3, 0xFFFFFFFF, false);
        }
        g.text(this.font, "Enabled by default", togBoxX + 20, togBoxY + 3, editFeatureEnabledByDefault ? 0xFF38BDF8 : 0xFFCBD5E1, false);

        // [Confirm] and [Cancel]
        int btnW = 80;
        int btnH = 20;
        int cBtnX = mx + mw - 16 - btnW;
        int canBtnX = cBtnX - btnW - 8;
        int btnY = my + mh - 26;

        boolean canHover = mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean confHover = mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        ConfigUITheme.drawPillButton(g, this.font, "Cancel", canBtnX, btnY, btnW, btnH, canHover, 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);
        ConfigUITheme.drawPillButton(g, this.font, "Save", cBtnX, btnY, btnW, btnH, confHover, 0xFFFFFFFF, 0x440284C7, 0xFF0284C7);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // 1. Feature Edit Modal
        if (isEditingFeature && editingFeature != null) {
            int mw = 320;
            int mh = 210;
            int mx = (this.width - mw) / 2;
            int my = (this.height - mh) / 2;

            int nameInputX = mx + 16;
            int nameInputY = my + 40;
            int nameInputW = mw - 32;
            if (mouseX >= nameInputX && mouseX <= nameInputX + nameInputW && mouseY >= nameInputY && mouseY <= nameInputY + 20) {
                editFeatureFocusField = 0;
                editFeatureCursorPos = editFeatureNameInput.length();
                return true;
            }

            int descInputX = mx + 16;
            int descInputY = my + 78;
            int descInputW = mw - 32;
            if (mouseX >= descInputX && mouseX <= descInputX + descInputW && mouseY >= descInputY && mouseY <= descInputY + 34) {
                editFeatureFocusField = 1;
                editFeatureCursorPos = editFeatureDescInput.length();
                return true;
            }

            // Clear requirement button
            int depY = my + 118;
            String curDep = editingFeature.parentDependency != null ? editingFeature.parentDependency : "";
            if (!curDep.isEmpty()) {
                int clearW = 105;
                int clearX = mx + mw - 16 - clearW;
                if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= depY && mouseY <= depY + 16) {
                    editingFeature.parentDependency = "";
                    FeatureOrganizerManager.save();
                    return true;
                }
            }

            // Enabled by default checkbox toggle
            int defY = my + 142;
            int togBoxX = mx + 16;
            int togBoxY = defY;
            if (mouseX >= togBoxX && mouseX <= togBoxX + 180 && mouseY >= togBoxY && mouseY <= togBoxY + 16) {
                editFeatureEnabledByDefault = !editFeatureEnabledByDefault;
                return true;
            }

            int btnW = 80;
            int btnH = 20;
            int cBtnX = mx + mw - 16 - btnW;
            int canBtnX = cBtnX - btnW - 8;
            int btnY = my + mh - 26;

            if (mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                commitFeatureEditModal();
                return true;
            }
            if (mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                isEditingFeature = false;
                editingFeature = null;
                return true;
            }
            return true;
        }

        // 1b. Category Modals
        if (isCreatingCategory || isRenamingCategory) {
            int mw = 260;
            int mh = 100;
            int mx = (this.width - mw) / 2;
            int my = (this.height - mh) / 2;
            int btnW = 80;
            int btnH = 20;
            int cBtnX = mx + mw - 16 - btnW;
            int canBtnX = cBtnX - btnW - 8;
            int btnY = my + mh - 28;

            if (mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                commitCategoryModal();
                return true;
            }
            if (mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                isCreatingCategory = false;
                isRenamingCategory = false;
                return true;
            }
            return true;
        }

        // 2. Context Menu
        if (contextMenuOpen) {
            int mw = 150;
            int mh = contextTargetCategory != null ? 124 : 26;
            int mx = Math.min(this.width - mw - 10, contextMenuX);
            int my = Math.min(this.height - mh - 10, contextMenuY);

            if (mouseX >= mx && mouseX <= mx + mw && mouseY >= my && mouseY <= my + mh) {
                int curY = my + 3;
                if (contextTargetCategory != null) {
                    if (mouseY >= curY && mouseY <= curY + 18) { // Move Up
                        int idx = FeatureOrganizerManager.customCategories.indexOf(contextTargetCategory);
                        if (idx > 0) {
                            Collections.swap(FeatureOrganizerManager.customCategories, idx, idx - 1);
                            FeatureOrganizerManager.save();
                        }
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Move Down
                        int idx = FeatureOrganizerManager.customCategories.indexOf(contextTargetCategory);
                        if (idx >= 0 && idx < FeatureOrganizerManager.customCategories.size() - 1) {
                            Collections.swap(FeatureOrganizerManager.customCategories, idx, idx + 1);
                            FeatureOrganizerManager.save();
                        }
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Rename
                        isRenamingCategory = true;
                        categoryNameInput = contextTargetCategory;
                        categoryNameCursorPos = categoryNameInput.length();
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Delete
                        String toDelete = contextTargetCategory;
                        FeatureOrganizerManager.customCategories.remove(toDelete);
                        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                            if (fm.category.equalsIgnoreCase(toDelete)) {
                                fm.category = "General";
                            }
                        }
                        if (selectedCategory.equalsIgnoreCase(toDelete)) selectedCategory = "ALL";
                        FeatureOrganizerManager.save();
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Mark All Legit
                        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                            if (fm.category.equalsIgnoreCase(contextTargetCategory)) {
                                fm.isCheat = false;
                            }
                        }
                        FeatureOrganizerManager.save();
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Mark All Cheat
                        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                            if (fm.category.equalsIgnoreCase(contextTargetCategory)) {
                                fm.isCheat = true;
                            }
                        }
                        FeatureOrganizerManager.save();
                        contextMenuOpen = false;
                        return true;
                    }
                } else {
                    if (mouseY >= curY && mouseY <= curY + 20) { // Create new
                        isCreatingCategory = true;
                        categoryNameInput = "";
                        categoryNameCursorPos = 0;
                        contextMenuOpen = false;
                        return true;
                    }
                }
            }
            contextMenuOpen = false;
            return true;
        }

        // Close Button
        int closeBtnX = winX + winW - 30;
        int closeBtnY = winY + 11;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 20 && mouseY >= closeBtnY && mouseY <= closeBtnY + 20) {
            FeatureOrganizerManager.save();
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        // Search Focus
        int searchW = 180;
        int searchX = winX + winW - searchW - 40;
        int searchY = winY + 11;
        boolean wasSearch = searchFocused;
        searchFocused = (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 20);
        if (searchFocused) {
            searchCursorPos = searchFilter.length();
        }
        if (searchFocused && button == 1) {
            searchFilter = "";
            searchCursorPos = 0;
        }

        // 3. Sidebar Categories Click
        int sideX = winX;
        int sideY = winY + headerH + 1;
        int sideH = winH - headerH - 1;
        int listH = sideH - 34;

        if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + sideH) {
            int curY = sideY + 6 - (int) this.sidebarScroll;
            List<String> categories = new ArrayList<>(FeatureOrganizerManager.customCategories);

            // ALL Category
            if (mouseY >= curY && mouseY <= curY + 20) {
                if (button == 0) {
                    selectedCategory = "ALL";
                    this.featureScroll = 0.0;
                    pendingDragCategory = null;
                    isCategoryDragging = false;
                    draggingCategory = null;
                }
                return true;
            }
            curY += 22;

            for (int i = 0; i < categories.size(); i++) {
                String cat = categories.get(i);
                if (mouseY >= curY && mouseY <= curY + 20) {
                    if (button == 0) {
                        selectedCategory = cat;
                        this.featureScroll = 0.0;
                        pendingDragCategory = cat;
                        categoryPressX = mouseX;
                        categoryPressY = mouseY;
                        isCategoryDragging = false;
                        dragCategoryOffsetY = (int) mouseY - curY;
                    } else if (button == 1) {
                        contextMenuOpen = true;
                        contextMenuX = (int) mouseX;
                        contextMenuY = (int) mouseY;
                        contextTargetCategory = cat;
                    }
                    return true;
                }
                curY += 22;
            }

            // + Add Category Button / Empty Spot
            if (mouseY >= curY && mouseY <= curY + 20) {
                isCreatingCategory = true;
                categoryNameInput = "";
                categoryNameCursorPos = 0;
                return true;
            }

            // Right click on empty sidebar area
            if (button == 1) {
                contextMenuOpen = true;
                contextMenuX = (int) mouseX;
                contextMenuY = (int) mouseY;
                contextTargetCategory = null;
                return true;
            }
        }

        // 4. Feature Card Click / Drag Start / Right-Click Edit / Rich Controls
        int contentX = sideX + sidebarW + 12;
        int contentY = sideY + 8;
        int contentW = winW - sidebarW - 24;
        int contentH = sideH - 16;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            List<FeatureOrganizerManager.FeatureMeta> list = getFilteredFeatures();
            boolean isAll = "ALL".equalsIgnoreCase(selectedCategory);
            int cardW = isAll ? 210 : (contentW - 8);
            int cardH = isAll ? 34 : 40;
            int cols = isAll ? Math.max(1, contentW / (cardW + 10)) : 1;
            int startY = contentY + 4 - (int) this.featureScroll;

            for (int i = 0; i < list.size(); i++) {
                FeatureOrganizerManager.FeatureMeta fm = list.get(i);
                int col = isAll ? (i % cols) : 0;
                int row = isAll ? (i / cols) : i;
                int cx = contentX + col * (cardW + (isAll ? 10 : 0));
                int cy = startY + row * (cardH + (isAll ? 8 : 6));

                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    // Right Click -> Open Feature Edit Modal
                    if (button == 1) {
                        isEditingFeature = true;
                        editingFeature = fm;
                        editFeatureNameInput = fm.name != null ? fm.name : "";
                        editFeatureEnabledByDefault = fm.enabledByDefault;
                        String desc = fm.description;
                        if (desc == null || desc.trim().isEmpty()) {
                            ConfigItem ci = ConfigRegistry.getMasterItemsMap().get(fm.name);
                            if (ci != null && ci.description != null && !ci.description.trim().isEmpty()) {
                                desc = ci.description;
                                fm.description = desc;
                            }
                        }
                        editFeatureDescInput = desc != null ? desc : "";
                        editFeatureFocusField = 0;
                        editFeatureCursorPos = editFeatureNameInput.length();
                        return true;
                    }

                    ConfigItem item = ConfigRegistry.getMasterItemsMap().get(fm.name);

                    if (!isAll) {
                        int ctrlRightX = cx + cardW - 32;
                        int ctrlY = cy + 11;

                        // Check Cheat/Legit Badge click in Category View
                        int badgeW = 16;
                        int badgeX = ctrlRightX - 2;
                        int badgeY = ctrlY + 1;
                        if (mouseX >= badgeX && mouseX <= badgeX + badgeW && mouseY >= badgeY && mouseY <= badgeY + 16) {
                            fm.isCheat = !fm.isCheat;
                            FeatureOrganizerManager.save();
                            return true;
                        }

                        // Check Edit Pencil button click
                        int editBtnX = cx + cardW - 18;
                        if (mouseX >= editBtnX && mouseX <= editBtnX + 16 && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                            isEditingFeature = true;
                            editingFeature = fm;
                            editFeatureNameInput = fm.name != null ? fm.name : "";
                            editFeatureEnabledByDefault = fm.enabledByDefault;
                            String desc = fm.description;
                            if (desc == null || desc.trim().isEmpty()) {
                                if (item != null && item.description != null && !item.description.trim().isEmpty()) {
                                    desc = item.description;
                                    fm.description = desc;
                                }
                            }
                            editFeatureDescInput = desc != null ? desc : "";
                            editFeatureFocusField = 0;
                            editFeatureCursorPos = editFeatureNameInput.length();
                            return true;
                        }

                        int ctrlEnd = badgeX - 8;

                        if (item != null) {
                            // Check Control type clicks
                            switch (item.type) {
                                case TOGGLE -> {
                                    int togW = 34;
                                    int togX = ctrlEnd - togW;
                                    if (mouseX >= togX && mouseX <= togX + togW && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                                        if (item.boolGetter != null && item.boolSetter != null) {
                                            boolean current = item.boolGetter.get();
                                            item.boolSetter.accept(!current);
                                            BomboConfig.save();
                                        }
                                        return true;
                                    }
                                }
                                case KEYBIND -> {
                                    int keyW = 80;
                                    int keyX = ctrlEnd - keyW;
                                    if (mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                                        if (button == 0) {
                                            if (this.activeKeybindItem == item) {
                                                this.activeKeybindItem = null;
                                            } else {
                                                this.activeKeybindItem = item;
                                            }
                                        } else if (button == 1) {
                                            if (item.stringSetter != null) {
                                                item.stringSetter.accept("");
                                                BomboConfig.save();
                                            }
                                            this.activeKeybindItem = null;
                                        }
                                        return true;
                                    }
                                }
                                case SLIDER_INT -> {
                                    int trackW = 70;
                                    int trackX = ctrlEnd - 135;
                                    int trackY = ctrlY + 6;
                                    if (mouseX >= trackX - 6 && mouseX <= trackX + trackW + 6 && mouseY >= trackY - 8 && mouseY <= trackY + 14) {
                                        float pct = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - trackX) / trackW));
                                        int val = Math.round(item.minInt + pct * (item.maxInt - item.minInt));
                                        if (item.intSetter != null) {
                                            item.intSetter.accept(val);
                                            BomboConfig.save();
                                        }
                                        return true;
                                    }
                                }
                                case SLIDER_FLOAT -> {
                                    int trackW = 70;
                                    int trackX = ctrlEnd - 135;
                                    int trackY = ctrlY + 6;
                                    if (mouseX >= trackX - 6 && mouseX <= trackX + trackW + 6 && mouseY >= trackY - 8 && mouseY <= trackY + 14) {
                                        float pct = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - trackX) / trackW));
                                        float val = item.minFloat + pct * (item.maxFloat - item.minFloat);
                                        if (item.floatSetter != null) {
                                            item.floatSetter.accept(val);
                                            BomboConfig.save();
                                        }
                                        return true;
                                    }
                                }
                                case CYCLE -> {
                                    int btnW = 80;
                                    int btnX = ctrlEnd - btnW;
                                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                                        if (item.cycleOptions != null && !item.cycleOptions.isEmpty() && item.stringGetter != null && item.stringSetter != null) {
                                            String cur = item.stringGetter.get();
                                            int curIdx = item.cycleOptions.indexOf(cur);
                                            int nextIdx = (curIdx + (button == 1 ? -1 : 1) + item.cycleOptions.size()) % item.cycleOptions.size();
                                            item.stringSetter.accept(item.cycleOptions.get(nextIdx));
                                            BomboConfig.save();
                                        }
                                        return true;
                                    }
                                }
                                default -> {}
                            }
                        }
                    } else if (isAll) {
                        // Check if clicked Cheat/Legit Badge
                        int badgeSize = 16;
                        int badgeX = cx + cardW - badgeSize - 6;
                        int badgeY = cy + 9;
                        if (mouseX >= badgeX && mouseX <= badgeX + badgeSize && mouseY >= badgeY && mouseY <= badgeY + badgeSize) {
                            fm.isCheat = !fm.isCheat;
                            FeatureOrganizerManager.save();
                            return true;
                        }
                    }

                    // Start dragging feature (Left click)
                    if (button == 0) {
                        pendingDragFeature = fm;
                        featurePressX = mouseX;
                        featurePressY = mouseY;
                        dragOffsetX = (int) mouseX - cx;
                        dragOffsetY = (int) mouseY - cy;
                        // If clicked directly on the left handle (cx to cx + 24), initiate drag immediately
                        if (mouseX >= cx && mouseX <= cx + 24) {
                            isFeatureDragging = true;
                            draggingFeature = fm;
                        } else {
                            isFeatureDragging = false;
                        }
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (pendingDragCategory != null && !isCategoryDragging) {
            if (Math.hypot(mouseX - categoryPressX, mouseY - categoryPressY) > 4.0) {
                isCategoryDragging = true;
                draggingCategory = pendingDragCategory;
            }
        }
        if (pendingDragFeature != null && !isFeatureDragging) {
            if (Math.hypot(mouseX - featurePressX, mouseY - featurePressY) > 3.0) {
                isFeatureDragging = true;
                draggingFeature = pendingDragFeature;
            }
        }
        if (isFeatureDragging && draggingFeature != null) {
            boolean isAll = "ALL".equalsIgnoreCase(selectedCategory);
            if (!isAll) {
                // In category view: live reordering as mouse moves over other cards!
                int sideX = winX;
                int sideY = winY + headerH + 1;
                int sideH = winH - headerH - 1;
                int contentX = sideX + sidebarW + 12;
                int contentY = sideY + 8;
                int contentW = winW - sidebarW - 24;
                int contentH = sideH - 16;

                if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
                    List<FeatureOrganizerManager.FeatureMeta> list = getFilteredFeatures();
                    int cardH = 40;
                    int startY = contentY + 4 - (int) this.featureScroll;

                    for (int i = 0; i < list.size(); i++) {
                        FeatureOrganizerManager.FeatureMeta target = list.get(i);
                        if (target == draggingFeature) continue;
                        int cy = startY + i * (cardH + 6);
                        if (mouseY >= cy && mouseY <= cy + cardH + 6) {
                            FeatureOrganizerManager.reorderFeatureInMemory(draggingFeature.name, target.name);
                            break;
                        }
                    }
                }
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (isCategoryDragging && draggingCategory != null) {
            int sideX = winX;
            int sideY = winY + headerH + 1;
            int sideH = winH - headerH - 1;
            if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + sideH) {
                int curY = sideY + 6 - (int) this.sidebarScroll;
                curY += 22; // skip ALL
                List<String> categories = new ArrayList<>(FeatureOrganizerManager.customCategories);
                for (int i = 0; i < categories.size(); i++) {
                    if (mouseY >= curY && mouseY <= curY + 20) {
                        FeatureOrganizerManager.reorderCategory(draggingCategory, i);
                        break;
                    }
                    curY += 22;
                }
            }
        }
        pendingDragCategory = null;
        isCategoryDragging = false;
        draggingCategory = null;

        pendingDragFeature = null;
        isFeatureDragging = false;
        if (draggingFeature != null) {
            // 1. Check if dropped on a sidebar category
            int sideX = winX;
            int sideY = winY + headerH + 1;
            int sideH = winH - headerH - 1;

            boolean dropped = false;
            if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + sideH) {
                int curY = sideY + 6 - (int) this.sidebarScroll;
                List<String> categories = new ArrayList<>(FeatureOrganizerManager.customCategories);
                curY += 22; // skip ALL

                for (String cat : categories) {
                    if (mouseY >= curY && mouseY <= curY + 20) {
                        draggingFeature.category = cat;
                        FeatureOrganizerManager.save();
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aMoved '§e" + draggingFeature.name + "§a' to category §b" + cat));
                        }
                        dropped = true;
                        break;
                    }
                    curY += 22;
                }
            }

            // 2. Check if dropped within features list / on another feature card
            if (!dropped) {
                int contentX = sideX + sidebarW + 12;
                int contentY = sideY + 8;
                int contentW = winW - sidebarW - 24;
                int contentH = sideH - 16;

                if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
                    long winHandle = Minecraft.getInstance().getWindow().handle();
                    boolean ctrlDown = GLFW.glfwGetKey(winHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(winHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

                    if (ctrlDown) {
                        List<FeatureOrganizerManager.FeatureMeta> list = getFilteredFeatures();
                        boolean isAll = "ALL".equalsIgnoreCase(selectedCategory);
                        int cardW = isAll ? 210 : (contentW - 8);
                        int cardH = isAll ? 34 : 44;
                        int cols = isAll ? Math.max(1, contentW / (cardW + 10)) : 1;
                        int startY = contentY + 4 - (int) this.featureScroll;

                        for (int i = 0; i < list.size(); i++) {
                            FeatureOrganizerManager.FeatureMeta target = list.get(i);
                            if (target == draggingFeature) continue;
                            int col = isAll ? (i % cols) : 0;
                            int row = isAll ? (i / cols) : i;
                            int cx = contentX + col * (cardW + (isAll ? 10 : 0));
                            int cy = startY + row * (cardH + (isAll ? 8 : 6));

                            if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                                draggingFeature.parentDependency = target.name;
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aLinked '§e" + draggingFeature.name + "§a' as dependent on '§b" + target.name + "§a'"));
                                }
                                break;
                            }
                        }
                    }
                    // Persist reordered state to disk
                    FeatureOrganizerManager.save();
                }
            }
            draggingFeature = null;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int sideX = winX;
        if (mouseX >= sideX && mouseX <= sideX + sidebarW) {
            this.sidebarScroll -= verticalAmount * 20.0;
            if (this.sidebarScroll < 0) this.sidebarScroll = 0;
            if (this.sidebarScroll > this.maxSidebarScroll) this.sidebarScroll = this.maxSidebarScroll;
            return true;
        } else {
            this.featureScroll -= verticalAmount * 24.0;
            if (this.featureScroll < 0) this.featureScroll = 0;
            if (this.featureScroll > this.maxFeatureScroll) this.featureScroll = this.maxFeatureScroll;
            return true;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (isEditingFeature) {
            if (c >= 32 && c != 127) {
                if (editFeatureFocusField == 0 && editFeatureNameInput.length() < 40) {
                    editFeatureCursorPos = Math.max(0, Math.min(editFeatureNameInput.length(), editFeatureCursorPos));
                    editFeatureNameInput = editFeatureNameInput.substring(0, editFeatureCursorPos) + c + editFeatureNameInput.substring(editFeatureCursorPos);
                    editFeatureCursorPos++;
                    return true;
                } else if (editFeatureFocusField == 1 && editFeatureDescInput.length() < 120) {
                    editFeatureCursorPos = Math.max(0, Math.min(editFeatureDescInput.length(), editFeatureCursorPos));
                    editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos) + c + editFeatureDescInput.substring(editFeatureCursorPos);
                    editFeatureCursorPos++;
                    return true;
                }
            }
        }
        if (isCreatingCategory || isRenamingCategory) {
            if (c >= 32 && c != 127 && categoryNameInput.length() < 24) {
                categoryNameCursorPos = Math.max(0, Math.min(categoryNameInput.length(), categoryNameCursorPos));
                categoryNameInput = categoryNameInput.substring(0, categoryNameCursorPos) + c + categoryNameInput.substring(categoryNameCursorPos);
                categoryNameCursorPos++;
                return true;
            }
        }
        if (searchFocused) {
            if (c >= 32 && c != 127) {
                searchCursorPos = Math.max(0, Math.min(searchFilter.length(), searchCursorPos));
                searchFilter = searchFilter.substring(0, searchCursorPos) + c + searchFilter.substring(searchCursorPos);
                searchCursorPos++;
                featureScroll = 0.0;
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int code = event.key();
        boolean isCtrl = event.hasControlDown() || (Minecraft.getInstance() != null && Minecraft.getInstance().hasControlDown());

        if (this.activeKeybindItem != null) {
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                if (this.activeKeybindItem.stringSetter != null) {
                    this.activeKeybindItem.stringSetter.accept("");
                    BomboConfig.save();
                }
            } else {
                String keyName = GLFW.glfwGetKeyName(code, 0);
                if (keyName == null || keyName.isEmpty()) {
                    keyName = "KEY_" + code;
                }
                if (this.activeKeybindItem.stringSetter != null) {
                    this.activeKeybindItem.stringSetter.accept(keyName.toUpperCase(Locale.ROOT));
                    BomboConfig.save();
                }
            }
            this.activeKeybindItem = null;
            return true;
        }

        if (isEditingFeature) {
            if (code == GLFW.GLFW_KEY_ENTER) {
                commitFeatureEditModal();
                return true;
            } else if (code == GLFW.GLFW_KEY_TAB) {
                editFeatureFocusField = (editFeatureFocusField == 0) ? 1 : 0;
                editFeatureCursorPos = (editFeatureFocusField == 0) ? editFeatureNameInput.length() : editFeatureDescInput.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_ESCAPE) {
                isEditingFeature = false;
                editingFeature = null;
                return true;
            } else if (code == GLFW.GLFW_KEY_UP) {
                if (editFeatureFocusField == 1) {
                    if (editFeatureDescInput.length() > 48) {
                        if (editFeatureCursorPos > 48) {
                            editFeatureCursorPos = Math.min(48, editFeatureCursorPos - 48);
                            return true;
                        } else if (editFeatureCursorPos > 0) {
                            editFeatureCursorPos = 0;
                            return true;
                        }
                    } else if (editFeatureCursorPos > 0) {
                        editFeatureCursorPos = 0;
                        return true;
                    }
                    editFeatureFocusField = 0;
                    editFeatureCursorPos = editFeatureNameInput.length();
                    return true;
                }
            } else if (code == GLFW.GLFW_KEY_DOWN) {
                if (editFeatureFocusField == 0) {
                    editFeatureFocusField = 1;
                    editFeatureCursorPos = 0;
                    return true;
                } else if (editFeatureFocusField == 1) {
                    if (editFeatureDescInput.length() > 48 && editFeatureCursorPos <= 48) {
                        editFeatureCursorPos = Math.min(editFeatureDescInput.length(), editFeatureCursorPos + 48);
                        return true;
                    } else {
                        editFeatureCursorPos = editFeatureDescInput.length();
                        return true;
                    }
                }
            } else if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    String target = (editFeatureFocusField == 0) ? editFeatureNameInput : editFeatureDescInput;
                    editFeatureCursorPos = getPrevWordIndex(target, editFeatureCursorPos);
                } else {
                    if (editFeatureCursorPos > 0) editFeatureCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                String target = (editFeatureFocusField == 0) ? editFeatureNameInput : editFeatureDescInput;
                if (isCtrl) {
                    editFeatureCursorPos = getNextWordIndex(target, editFeatureCursorPos);
                } else {
                    if (editFeatureCursorPos < target.length()) editFeatureCursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                editFeatureCursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                editFeatureCursorPos = (editFeatureFocusField == 0) ? editFeatureNameInput.length() : editFeatureDescInput.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (editFeatureFocusField == 0) {
                    if (isCtrl) {
                        int prev = getPrevWordIndex(editFeatureNameInput, editFeatureCursorPos);
                        editFeatureNameInput = editFeatureNameInput.substring(0, prev) + editFeatureNameInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos = prev;
                    } else if (editFeatureCursorPos > 0 && !editFeatureNameInput.isEmpty()) {
                        editFeatureNameInput = editFeatureNameInput.substring(0, editFeatureCursorPos - 1) + editFeatureNameInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos--;
                    }
                } else {
                    if (isCtrl) {
                        int prev = getPrevWordIndex(editFeatureDescInput, editFeatureCursorPos);
                        editFeatureDescInput = editFeatureDescInput.substring(0, prev) + editFeatureDescInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos = prev;
                    } else if (editFeatureCursorPos > 0 && !editFeatureDescInput.isEmpty()) {
                        editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos - 1) + editFeatureDescInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos--;
                    }
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (editFeatureFocusField == 0) {
                    if (isCtrl) {
                        int next = getNextWordIndex(editFeatureNameInput, editFeatureCursorPos);
                        editFeatureNameInput = editFeatureNameInput.substring(0, editFeatureCursorPos) + editFeatureNameInput.substring(next);
                    } else if (editFeatureCursorPos < editFeatureNameInput.length()) {
                        editFeatureNameInput = editFeatureNameInput.substring(0, editFeatureCursorPos) + editFeatureNameInput.substring(editFeatureCursorPos + 1);
                    }
                } else {
                    if (isCtrl) {
                        int next = getNextWordIndex(editFeatureDescInput, editFeatureCursorPos);
                        editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos) + editFeatureDescInput.substring(next);
                    } else if (editFeatureCursorPos < editFeatureDescInput.length()) {
                        editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos) + editFeatureDescInput.substring(editFeatureCursorPos + 1);
                    }
                }
                return true;
            }
        }

        if (isCreatingCategory || isRenamingCategory) {
            if (code == GLFW.GLFW_KEY_ENTER) {
                commitCategoryModal();
                return true;
            } else if (code == GLFW.GLFW_KEY_ESCAPE) {
                isCreatingCategory = false;
                isRenamingCategory = false;
                return true;
            } else if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    categoryNameCursorPos = getPrevWordIndex(categoryNameInput, categoryNameCursorPos);
                } else {
                    if (categoryNameCursorPos > 0) categoryNameCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                if (isCtrl) {
                    categoryNameCursorPos = getNextWordIndex(categoryNameInput, categoryNameCursorPos);
                } else {
                    if (categoryNameCursorPos < categoryNameInput.length()) categoryNameCursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                categoryNameCursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                categoryNameCursorPos = categoryNameInput.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (isCtrl) {
                    int prev = getPrevWordIndex(categoryNameInput, categoryNameCursorPos);
                    categoryNameInput = categoryNameInput.substring(0, prev) + categoryNameInput.substring(categoryNameCursorPos);
                    categoryNameCursorPos = prev;
                } else if (categoryNameCursorPos > 0 && !categoryNameInput.isEmpty()) {
                    categoryNameInput = categoryNameInput.substring(0, categoryNameCursorPos - 1) + categoryNameInput.substring(categoryNameCursorPos);
                    categoryNameCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (isCtrl) {
                    int next = getNextWordIndex(categoryNameInput, categoryNameCursorPos);
                    categoryNameInput = categoryNameInput.substring(0, categoryNameCursorPos) + categoryNameInput.substring(next);
                } else if (categoryNameCursorPos < categoryNameInput.length()) {
                    categoryNameInput = categoryNameInput.substring(0, categoryNameCursorPos) + categoryNameInput.substring(categoryNameCursorPos + 1);
                }
                return true;
            }
        }

        if (searchFocused) {
            if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    searchCursorPos = getPrevWordIndex(searchFilter, searchCursorPos);
                } else {
                    if (searchCursorPos > 0) searchCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                if (isCtrl) {
                    searchCursorPos = getNextWordIndex(searchFilter, searchCursorPos);
                } else {
                    if (searchCursorPos < searchFilter.length()) searchCursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                searchCursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                searchCursorPos = searchFilter.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (isCtrl) {
                    int prev = getPrevWordIndex(searchFilter, searchCursorPos);
                    searchFilter = searchFilter.substring(0, prev) + searchFilter.substring(searchCursorPos);
                    searchCursorPos = prev;
                } else if (searchCursorPos > 0 && !searchFilter.isEmpty()) {
                    searchFilter = searchFilter.substring(0, searchCursorPos - 1) + searchFilter.substring(searchCursorPos);
                    searchCursorPos--;
                }
                featureScroll = 0.0;
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (isCtrl) {
                    int next = getNextWordIndex(searchFilter, searchCursorPos);
                    searchFilter = searchFilter.substring(0, searchCursorPos) + searchFilter.substring(next);
                } else if (searchCursorPos < searchFilter.length()) {
                    searchFilter = searchFilter.substring(0, searchCursorPos) + searchFilter.substring(searchCursorPos + 1);
                }
                featureScroll = 0.0;
                return true;
            } else if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }

        if (code == GLFW.GLFW_KEY_ESCAPE) {
            FeatureOrganizerManager.save();
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    private void commitFeatureEditModal() {
        if (editingFeature != null) {
            String oldName = editingFeature.name;
            String newName = editFeatureNameInput.trim();
            String newDesc = editFeatureDescInput.trim();
            if (!newName.isEmpty()) {
                editingFeature.name = newName;
                editingFeature.description = newDesc;
                editingFeature.enabledByDefault = editFeatureEnabledByDefault;
                if (!oldName.equals(newName)) {
                    FeatureOrganizerManager.features.remove(oldName);
                    FeatureOrganizerManager.features.put(newName, editingFeature);
                }
                FeatureOrganizerManager.save();
            }
        }
        isEditingFeature = false;
        editingFeature = null;
    }

    private void commitCategoryModal() {
        String name = categoryNameInput.trim();
        if (!name.isEmpty()) {
            if (isCreatingCategory) {
                if (!FeatureOrganizerManager.customCategories.contains(name)) {
                    FeatureOrganizerManager.customCategories.add(name);
                    selectedCategory = name;
                    FeatureOrganizerManager.save();
                }
            } else if (isRenamingCategory && contextTargetCategory != null) {
                int idx = FeatureOrganizerManager.customCategories.indexOf(contextTargetCategory);
                if (idx != -1) {
                    FeatureOrganizerManager.customCategories.set(idx, name);
                    for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                        if (fm.category.equalsIgnoreCase(contextTargetCategory)) {
                            fm.category = name;
                        }
                    }
                    if (selectedCategory.equalsIgnoreCase(contextTargetCategory)) selectedCategory = name;
                    FeatureOrganizerManager.save();
                }
            }
        }
        isCreatingCategory = false;
        isRenamingCategory = false;
    }

    private static int getPrevWordIndex(String text, int cursor) {
        if (text == null || text.isEmpty() || cursor <= 0) return 0;
        int idx = Math.min(cursor - 1, text.length() - 1);
        while (idx > 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        while (idx > 0 && !Character.isWhitespace(text.charAt(idx - 1))) {
            idx--;
        }
        return Math.max(0, idx);
    }

    private static int getNextWordIndex(String text, int cursor) {
        if (text == null || text.isEmpty() || cursor >= text.length()) return text != null ? text.length() : 0;
        int idx = Math.max(0, cursor);
        while (idx < text.length() && !Character.isWhitespace(text.charAt(idx))) {
            idx++;
        }
        while (idx < text.length() && Character.isWhitespace(text.charAt(idx))) {
            idx++;
        }
        return Math.min(text.length(), idx);
    }
}
