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
    private boolean isDraggingCategoryScrollbar = false;
    private boolean isDraggingFeatureScrollbar = false;

    // State
    private String selectedCategory = "ALL";
    private final Set<String> selectedCategories = new LinkedHashSet<>(Collections.singletonList("ALL"));
    private int lastClickedCategoryIndex = -1;
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
    private String editFeatureParentInput = "";
    private boolean editFeatureEnabledByDefault = false;
    private String editFeatureTagsInput = "";
    private int editFeatureFocusField = 0; // 0 = Name, 1 = Description, 2 = Parent Requirement, 3 = Tags
    private int editFeatureCursorPos = 0;

    // Category Creation/Renaming modal cursor
    private int categoryNameCursorPos = 0;

    // Multi-feature selection
    private final Set<FeatureOrganizerManager.FeatureMeta> selectedFeatures = new LinkedHashSet<>();
    private int lastClickedFeatureIndex = -1;

    // Tag modal state
    private boolean isEditingCategoryTags = false;
    private String categoryTagsInput = "";
    private int categoryTagsCursorPos = 0;
    private String targetCategoryForTags = "";

    // Subcategory state
    private boolean isCreatingSubcategory = false;
    private boolean isRenamingSubcategory = false;
    private String subcategoryNameInput = "";
    private int subcategoryNameCursorPos = 0;
    private String contextTargetSubcategory = null;
    private boolean isFeatureListContextMenu = false;
    private String editFeatureSubcatInput = "";

    // Row representation for category subcategory layout
    private static class FeatureRow {
        final boolean isSubcatHeader;
        final String subcatName;
        final FeatureOrganizerManager.FeatureMeta feature;
        final boolean indented;

        FeatureRow(String subcatName) {
            this.isSubcatHeader = true;
            this.subcatName = subcatName;
            this.feature = null;
            this.indented = false;
        }

        FeatureRow(FeatureOrganizerManager.FeatureMeta fm, boolean indented, String subcatName) {
            this.isSubcatHeader = false;
            this.subcatName = subcatName;
            this.feature = fm;
            this.indented = indented;
        }
    }

    // Undo stack
    private static class UndoSnapshot {
        List<String> categories = new ArrayList<>();
        Map<String, List<String>> categoryTags = new LinkedHashMap<>();
        Map<String, List<String>> subcategories = new LinkedHashMap<>();
        Map<String, FeatureOrganizerManager.FeatureMeta> features = new LinkedHashMap<>();
    }
    private final Deque<UndoSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<UndoSnapshot> redoStack = new ArrayDeque<>();

    private UndoSnapshot createCurrentSnapshot() {
        UndoSnapshot snap = new UndoSnapshot();
        snap.categories.addAll(FeatureOrganizerManager.customCategories);
        for (var entry : FeatureOrganizerManager.categoryTagsMap.entrySet()) {
            snap.categoryTags.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (var entry : FeatureOrganizerManager.subcategoriesMap.entrySet()) {
            snap.subcategories.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (var entry : FeatureOrganizerManager.features.entrySet()) {
            FeatureOrganizerManager.FeatureMeta old = entry.getValue();
            FeatureOrganizerManager.FeatureMeta clone = new FeatureOrganizerManager.FeatureMeta(old.name, old.category, old.subCategory, old.isCheat, old.description);
            clone.parentDependency = old.parentDependency;
            clone.enabledByDefault = old.enabledByDefault;
            if (old.tags != null) clone.tags = new ArrayList<>(old.tags);
            snap.features.put(entry.getKey(), clone);
        }
        return snap;
    }

    private void pushUndoSnapshot() {
        undoStack.push(createCurrentSnapshot());
        if (undoStack.size() > 50) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void applySnapshot(UndoSnapshot snap) {
        FeatureOrganizerManager.customCategories.clear();
        FeatureOrganizerManager.customCategories.addAll(snap.categories);
        FeatureOrganizerManager.categoryTagsMap.clear();
        FeatureOrganizerManager.categoryTagsMap.putAll(snap.categoryTags);
        FeatureOrganizerManager.subcategoriesMap.clear();
        for (var entry : snap.subcategories.entrySet()) {
            FeatureOrganizerManager.subcategoriesMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        FeatureOrganizerManager.features.clear();
        FeatureOrganizerManager.features.putAll(snap.features);
        FeatureOrganizerManager.save();
        selectedFeatures.clear();
    }

    private boolean popUndoSnapshot() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(createCurrentSnapshot());
        if (redoStack.size() > 50) redoStack.removeLast();
        UndoSnapshot snap = undoStack.pop();
        applySnapshot(snap);
        return true;
    }

    private boolean popRedoSnapshot() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(createCurrentSnapshot());
        if (undoStack.size() > 50) undoStack.removeLast();
        UndoSnapshot snap = redoStack.pop();
        applySnapshot(snap);
        return true;
    }

    public BomboOrderScreen(Screen parent) {
        super(Component.literal("Bombo Feature & Category Organizer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FeatureOrganizerManager.initDefaults();
        // Ensure "Uncategorized" category exists if any feature belongs to it
        if (!FeatureOrganizerManager.customCategories.contains("Uncategorized")) {
            for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                if (fm != null && "Uncategorized".equalsIgnoreCase(fm.category)) {
                    FeatureOrganizerManager.customCategories.add("Uncategorized");
                    break;
                }
            }
        }
        if (selectedCategories.isEmpty()) {
            selectedCategories.add(selectedCategory);
        }

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

        // 7c. Modal for Editing Category Tags
        if (isEditingCategoryTags) {
            renderCategoryTagsModal(g, mouseX, mouseY);
        }

        // 7d. Modal for Creating / Renaming Subcategories
        if (isCreatingSubcategory || isRenamingSubcategory) {
            renderSubcategoryModal(g, mouseX, mouseY);
        }

        // 8. Render Dragged Feature Floating under cursor (Full card width with gold border)
        if (draggingFeature != null) {
            int cardW = contentW - 8;
            int cardH = 40;
            int dx = mouseX - dragOffsetX;
            int dy = mouseY - dragOffsetY;

            // Semi-transparent drop shadow + dark card background + gold outline
            g.fill(dx + 3, dy + 3, dx + cardW + 3, dy + cardH + 3, 0x55000000);
            g.fill(dx, dy, dx + cardW, dy + cardH, 0xF01E293B);
            g.outline(dx, dy, cardW, cardH, 0xFFFFAA00);

            g.text(this.font, "§e☰", dx + 8, dy + 14, 0xFFFFAA00, false);
            int moveCount = (selectedFeatures.contains(draggingFeature) && selectedFeatures.size() > 1) ? selectedFeatures.size() : 1;
            String title = (moveCount > 1) ? ("§bMoving " + moveCount + " selected features") : ConfigUITheme.formatFont(draggingFeature.name);
            if (moveCount == 1 && draggingFeature.parentDependency != null && !draggingFeature.parentDependency.trim().isEmpty()) {
                title += " §6↳ Requires: " + draggingFeature.parentDependency.trim();
            }
            g.text(this.font, "§e" + title, dx + 24, dy + 7, 0xFFFFFFFF, true);

            // Check what we're currently hovering to give active feedback
            String hoverHint = "§7→ Drop top/bottom to reorder, center to link";
            if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + sideH) {
                int curY = sideY + 6 - (int) this.sidebarScroll;
                curY += 22; // skip ALL
                List<String> categories = getActiveCategories();
                for (String cat : categories) {
                    if (mouseY >= curY && mouseY <= curY + 20) {
                        if (isCtrlHeld()) {
                            hoverHint = "§d⊕ Link to category: " + cat + " §7(Ctrl+Drop)";
                        } else {
                            hoverHint = "§b→ Move to category: " + cat;
                        }
                        break;
                    }
                    curY += 22;
                }
            } else {
                List<FeatureOrganizerManager.FeatureMeta> currentList = getFilteredFeatures();
                boolean isAll = "ALL".equalsIgnoreCase(selectedCategory);
                int listStartY = contentY + 4 - (int) this.featureScroll;
                if (isAll) {
                    int targetCardW = 210;
                    int targetCardH = 34;
                    int cols = Math.max(1, contentW / (targetCardW + 10));
                    for (int i = 0; i < currentList.size(); i++) {
                        FeatureOrganizerManager.FeatureMeta target = currentList.get(i);
                        if (target == draggingFeature) continue;
                        int col = (i % cols);
                        int row = (i / cols);
                        int tcx = contentX + col * (targetCardW + 10);
                        int tcy = listStartY + row * (targetCardH + 8);
                        if (mouseX >= tcx && mouseX <= tcx + targetCardW && mouseY >= tcy && mouseY <= tcy + targetCardH) {
                            double relY = (mouseY - tcy) / (double) targetCardH;
                            if (relY < 0.25) {
                                hoverHint = "§b↑ Insert above " + target.name;
                            } else if (relY > 0.75) {
                                hoverHint = "§b↓ Insert below " + target.name;
                            } else {
                                hoverHint = "§6↳ Require parent: " + target.name;
                            }
                            break;
                        }
                    }
                } else {
                    List<FeatureRow> rows = getCategoryRows(currentList);
                    int curY = listStartY;
                    for (int i = 0; i < rows.size(); i++) {
                        FeatureRow r = rows.get(i);
                        int targetCardH = r.isSubcatHeader ? 26 : 40;
                        int tcy = curY;
                        curY += targetCardH + 6;

                        if (r.isSubcatHeader) {
                            int tcx = contentX + 4;
                            int targetCardW = contentW - 12;
                            if (mouseX >= tcx && mouseX <= tcx + targetCardW && mouseY >= tcy && mouseY <= tcy + targetCardH) {
                                hoverHint = "§6📁 Move into subcategory: " + r.subcatName;
                                break;
                            }
                        } else {
                            FeatureOrganizerManager.FeatureMeta target = r.feature;
                            if (target == draggingFeature) continue;
                            int indent = r.indented ? 20 : 0;
                            int tcx = contentX + 4 + indent;
                            int targetCardW = contentW - 12 - indent;
                            if (mouseX >= tcx && mouseX <= tcx + targetCardW && mouseY >= tcy && mouseY <= tcy + targetCardH) {
                                double relY = (mouseY - tcy) / (double) targetCardH;
                                if (relY < 0.25) {
                                    hoverHint = "§b↑ Insert above " + target.name;
                                } else if (relY > 0.75) {
                                    hoverHint = "§b↓ Insert below " + target.name;
                                } else {
                                    hoverHint = "§6↳ Require parent: " + target.name;
                                }
                                break;
                            }
                        }
                    }
                }
            }
            g.text(this.font, hoverHint, dx + 24, dy + 22, 0xFFFCD34D, false);
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

    private List<String> getActiveCategories() {
        List<String> list = new ArrayList<>(FeatureOrganizerManager.customCategories);
        if (!BomboConfig.isDev()) {
            list.removeIf(c -> "Dev".equalsIgnoreCase(c) || "Developer".equalsIgnoreCase(c));
        }
        return list;
    }

    private void renderSidebar(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        int accent = ConfigUITheme.getAccentColor();
        List<String> categories = getActiveCategories();

        int listH = h - 34;
        g.enableScissor(x, y, x + w, y + listH);

        int curY = y + 6 - (int) this.sidebarScroll;
        int totalHeight = 0;

        // "ALL" Category option
        boolean allActive = selectedCategories.contains("ALL") || ("ALL".equalsIgnoreCase(selectedCategory) && selectedCategories.size() <= 1);
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
            boolean active = selectedCategories.contains(cat) || cat.equalsIgnoreCase(selectedCategory);
            boolean isPrimary = cat.equalsIgnoreCase(selectedCategory);
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
                if (selectedCategories.size() > 1) {
                    g.outline(x + 6, curY, w - 18, 20, 0x5500E5FF);
                }
            } else if (hover) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x1AFFFFFF);
            }

            int count = 0;
            for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                if (fm.belongsToCategory(cat) && isFeatureVisible(fm)) count++;
            }

            String idxBadge = (i < 9) ? "§8[" + (i + 1) + "] " : "";
            int textColor = isDragged ? 0xFF64748B : (active ? accent : (hover ? 0xFFFFFFFF : 0xFF94A3B8));
            g.text(this.font, idxBadge + cat, x + 16, curY + 6, textColor, false);
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
        if (totalHeight > listH) {
            int trackX = x + w - 4;
            int thumbH = Math.max(16, (int) ((double) listH * listH / totalHeight));
            int thumbY = y + (int) ((listH - thumbH) * (this.sidebarScroll / this.maxSidebarScroll));
            ConfigUITheme.drawScrollBar(g, trackX, y, 4, listH, thumbY, thumbH);
        }
    }

    private void renderFeatureList(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<FeatureOrganizerManager.FeatureMeta> list = getFilteredFeatures();
        boolean isAllView = selectedCategories.contains("ALL") || selectedCategories.size() > 1 || "ALL".equalsIgnoreCase(selectedCategory);

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
                    boolean isDragTarget = (draggingFeature != null && !isDragged && hover);
                    double relY = isDragTarget ? ((mouseY - cy) / (double) cardH) : -1.0;
                    boolean isLinkTarget = isDragTarget && (relY >= 0.25 && relY <= 0.75);

                    boolean isSelected = selectedFeatures.contains(fm);
                    int cardBg = isDragged ? 0x44334155 : (isLinkTarget ? 0x44FFAA00 : (isSelected ? 0x4400E5FF : (hover ? 0xEE1E293B : 0xAA111827)));
                    int borderCol = isDragged ? 0xFFFFAA00 : (isLinkTarget ? 0xFFFFAA00 : (isSelected ? 0xFF00E5FF : (hover ? 0xFF38BDF8 : 0x33475569)));

                    g.fill(cx, cy, cx + cardW, cy + cardH, cardBg);
                    g.outline(cx, cy, cardW, cardH, borderCol);

                    if (isDragTarget) {
                        if (relY < 0.25) {
                            g.fill(cx, cy - 2, cx + cardW, cy + 1, 0xFF38BDF8);
                        } else if (relY > 0.75) {
                            g.fill(cx, cy + cardH - 1, cx + cardW, cy + cardH + 2, 0xFF38BDF8);
                        }
                    }

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
            // Tree hierarchy view with subcategory headers and indented features
            List<FeatureRow> rows = getCategoryRows(list);
            totalH = 0;
            for (FeatureRow r : rows) {
                totalH += (r.isSubcatHeader ? 26 : 40) + 6;
            }
            int accent = ConfigUITheme.getAccentColor();
            int curY = startY;

            for (int i = 0; i < rows.size(); i++) {
                FeatureRow row = rows.get(i);
                int cardH = row.isSubcatHeader ? 26 : 40;
                int cy = curY;
                curY += cardH + 6;

                if (cy + cardH < y || cy > y + h) {
                    continue;
                }

                if (row.isSubcatHeader) {
                    // Render subcategory header / separator
                    int cx = x + 4;
                    int cardW = w - 12;
                    boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
                    boolean isDragOver = draggingFeature != null && hover;

                    int bgCol = isDragOver ? 0x66FFAA00 : (hover ? 0x33FFAA00 : 0x221E293B);
                    int borderCol = isDragOver ? 0xFFFFAA00 : (hover ? 0xFFFFAA00 : 0x55FFAA00);

                    g.fill(cx, cy, cx + cardW, cy + cardH, bgCol);
                    g.outline(cx, cy, cardW, cardH, borderCol);

                    // Subcategory title & folder icon
                    g.text(this.font, "§6§l▾ §e" + row.subcatName.toUpperCase(Locale.ROOT), cx + 10, cy + 9, 0xFFFFAA00, false);

                    // Horizontal separator line next to title
                    int titleW = this.font.width("▾ " + row.subcatName.toUpperCase(Locale.ROOT)) + 20;
                    int lineStartX = cx + 10 + titleW;
                    int lineEndX = cx + cardW - 84;
                    if (lineEndX > lineStartX) {
                        g.fill(lineStartX, cy + 13, lineEndX, cy + 14, 0x44FFAA00);
                    }

                    // Quick action buttons: ▲ Move Up, ▼ Move Down, ✎ Rename, ✕ Delete
                    int btnY = cy + 4;
                    int btnH = 18;

                    int delX = cx + cardW - 20;
                    boolean delHover = mouseX >= delX && mouseX <= delX + 16 && mouseY >= btnY && mouseY <= btnY + btnH;
                    ConfigUITheme.drawPillButton(g, this.font, "✕", delX, btnY, 16, btnH, delHover, delHover ? 0xFFFF6666 : 0xFFEF4444, 0x22EF4444, 0x44EF4444);

                    int renX = delX - 20;
                    boolean renHover = mouseX >= renX && mouseX <= renX + 16 && mouseY >= btnY && mouseY <= btnY + btnH;
                    ConfigUITheme.drawPillButton(g, this.font, "✎", renX, btnY, 16, btnH, renHover, renHover ? 0xFFFFFFFF : 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);

                    int downX = renX - 20;
                    boolean downHover = mouseX >= downX && mouseX <= downX + 16 && mouseY >= btnY && mouseY <= btnY + btnH;
                    ConfigUITheme.drawPillButton(g, this.font, "▼", downX, btnY, 16, btnH, downHover, downHover ? 0xFF38BDF8 : 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);

                    int upX = downX - 20;
                    boolean upHover = mouseX >= upX && mouseX <= upX + 16 && mouseY >= btnY && mouseY <= btnY + btnH;
                    ConfigUITheme.drawPillButton(g, this.font, "▲", upX, btnY, 16, btnH, upHover, upHover ? 0xFF38BDF8 : 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);

                } else {
                    // Feature card (indented if under a subcategory)
                    FeatureOrganizerManager.FeatureMeta fm = row.feature;
                    int indent = row.indented ? 20 : 0;
                    int cx = x + 4 + indent;
                    int cardW = w - 12 - indent;

                    // Draw subtle guide tree lines if indented
                    if (row.indented) {
                        int guideX = x + 12;
                        g.fill(guideX, cy - 6, guideX + 1, cy + 20, 0x55FFAA00);
                        g.fill(guideX, cy + 19, guideX + 8, cy + 20, 0x55FFAA00);
                    }

                    boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
                    boolean isDragged = (draggingFeature == fm);
                    boolean isDragTarget = (draggingFeature != null && !isDragged && hover);
                    double relY = isDragTarget ? ((mouseY - cy) / (double) cardH) : -1.0;
                    boolean isLinkTarget = isDragTarget && (relY >= 0.25 && relY <= 0.75);
                    boolean handleHover = mouseX >= cx && mouseX <= cx + 22 && mouseY >= cy && mouseY <= cy + cardH;

                    boolean isSelected = selectedFeatures.contains(fm);
                    int cardBg = isDragged ? 0x44334155 : (isLinkTarget ? 0x44FFAA00 : (isSelected ? 0x4400E5FF : (hover ? 0xEE1E293B : 0xAA0F172A)));
                    int borderCol = isDragged ? 0xFFFFAA00 : (isLinkTarget ? 0xFFFFAA00 : (isSelected ? 0xFF00E5FF : (hover ? 0xFF38BDF8 : 0x33475569)));

                    g.fill(cx, cy, cx + cardW, cy + cardH, cardBg);
                    g.outline(cx, cy, cardW, cardH, borderCol);

                    if (isDragTarget) {
                        if (relY < 0.25) {
                            g.fill(cx, cy - 2, cx + cardW, cy + 1, 0xFF38BDF8);
                        } else if (relY > 0.75) {
                            g.fill(cx, cy + cardH - 1, cx + cardW, cy + cardH + 2, 0xFF38BDF8);
                        }
                    }

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
                    int maxTitleW = Math.max(30, controlsLeft - (cx + 28) - 8);
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
                        int maxDescW = Math.max(30, controlsLeft - (cx + 28) - 8);
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

                    // If indented (in a subcategory), show quick Remove from Subcategory button (⮌)
                    if (row.indented) {
                        int unassignBtnX = editBtnX - 18;
                        boolean unassignHover = mouseX >= unassignBtnX && mouseX <= unassignBtnX + 16 && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                        ConfigUITheme.drawPillButton(g, this.font, "⮌", unassignBtnX, ctrlY, 16, 18, unassignHover, unassignHover ? 0xFFFF6666 : 0xFFEF4444, 0x22EF4444, 0x44EF4444);
                    }

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

    private List<FeatureRow> getCategoryRows(List<FeatureOrganizerManager.FeatureMeta> rawFeatures) {
        List<FeatureRow> rows = new ArrayList<>();
        boolean isAll = selectedCategories.isEmpty() || selectedCategories.contains("ALL");
        if (isAll || selectedCategories.size() > 1) {
            for (FeatureOrganizerManager.FeatureMeta fm : rawFeatures) {
                rows.add(new FeatureRow(fm, false, null));
            }
            return rows;
        }

        String cat = selectedCategory;
        List<String> subcats = FeatureOrganizerManager.subcategoriesMap.computeIfAbsent(cat, k -> new ArrayList<>());

        for (FeatureOrganizerManager.FeatureMeta fm : rawFeatures) {
            if (fm.subCategory != null && !fm.subCategory.trim().isEmpty()) {
                String sub = fm.subCategory.trim();
                if (!subcats.contains(sub)) {
                    subcats.add(sub);
                }
            }
        }

        // Group 0: Features with no subcategory
        for (FeatureOrganizerManager.FeatureMeta fm : rawFeatures) {
            if (fm.subCategory == null || fm.subCategory.trim().isEmpty() || !subcats.contains(fm.subCategory.trim())) {
                rows.add(new FeatureRow(fm, false, null));
            }
        }

        // Group 1..N: Each subcategory and its features
        for (String sub : subcats) {
            rows.add(new FeatureRow(sub));
            for (FeatureOrganizerManager.FeatureMeta fm : rawFeatures) {
                if (sub.equalsIgnoreCase(fm.subCategory)) {
                    rows.add(new FeatureRow(fm, true, sub));
                }
            }
        }

        return rows;
    }

    private void deleteSubcategory(String cat, String subcat) {
        if (cat == null || subcat == null) return;
        List<String> subcats = FeatureOrganizerManager.subcategoriesMap.get(cat);
        if (subcats != null) {
            subcats.remove(subcat);
        }
        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
            if (cat.equalsIgnoreCase(fm.category) && subcat.equalsIgnoreCase(fm.subCategory)) {
                fm.subCategory = "";
            }
        }
        FeatureOrganizerManager.save();
    }

    private void moveSubcategory(String cat, String subcat, int direction) {
        if (cat == null || subcat == null) return;
        List<String> subcats = FeatureOrganizerManager.subcategoriesMap.get(cat);
        if (subcats != null) {
            int idx = subcats.indexOf(subcat);
            int targetIdx = idx + direction;
            if (idx >= 0 && targetIdx >= 0 && targetIdx < subcats.size()) {
                Collections.swap(subcats, idx, targetIdx);
                FeatureOrganizerManager.save();
            }
        }
    }

    private List<FeatureOrganizerManager.FeatureMeta> getFilteredFeatures() {
        List<FeatureOrganizerManager.FeatureMeta> list = new ArrayList<>();
        String q = searchFilter.toLowerCase(Locale.ROOT).trim();
        boolean isAll = selectedCategories.isEmpty() || selectedCategories.contains("ALL");
        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
            if (!isFeatureVisible(fm)) continue;
            if (!isAll) {
                boolean matchesCat = false;
                for (String sel : selectedCategories) {
                    if (fm.belongsToCategory(sel)) {
                        matchesCat = true;
                        break;
                    }
                }
                if (!matchesCat) {
                    continue;
                }
            }
            if (!q.isEmpty()) {
                String nameLower = fm.name.toLowerCase(Locale.ROOT);
                String catLower = fm.category != null ? fm.category.toLowerCase(Locale.ROOT) : "";
                String descLower = fm.description != null ? fm.description.toLowerCase(Locale.ROOT) : "";
                String subcatLower = fm.subCategory != null ? fm.subCategory.toLowerCase(Locale.ROOT) : "";

                boolean matches = nameLower.contains(q) || catLower.contains(q) || descLower.contains(q) || subcatLower.contains(q);

                // Check feature tags
                if (!matches && fm.tags != null) {
                    for (String tag : fm.tags) {
                        if (tag.toLowerCase(Locale.ROOT).contains(q)) {
                            matches = true;
                            break;
                        }
                    }
                }

                // Check category tags (e.g. tag 'garden' matches category 'Farming')
                if (!matches && fm.category != null) {
                    List<String> ctags = FeatureOrganizerManager.categoryTagsMap.get(fm.category);
                    if (ctags != null) {
                        for (String tag : ctags) {
                            if (tag.toLowerCase(Locale.ROOT).contains(q)) {
                                matches = true;
                                break;
                            }
                        }
                    }
                }

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
        int mh;
        if (contextTargetCategory != null) {
            mh = 144;
        } else if (contextTargetSubcategory != null) {
            mh = 84;
        } else {
            mh = 26;
        }
        int mx = Math.min(this.width - mw - 10, contextMenuX);
        int my = Math.min(this.height - mh - 10, contextMenuY);

        int borderCol = (contextTargetSubcategory != null || isFeatureListContextMenu) ? 0xFFFFAA00 : 0xFF38BDF8;
        g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, borderCol);
        g.fill(mx, my, mx + mw, my + mh, 0xF00F172A);

        int curY = my + 3;

        if (contextTargetCategory != null) {
            // Edit Category Tags
            boolean hoverTags = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverTags) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x3338BDF8);
            g.text(this.font, "🏷 Edit Tags", mx + 8, curY + 5, hoverTags ? 0xFF38BDF8 : 0xFFFFFFFF, false);
            curY += 20;
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
        } else if (contextTargetSubcategory != null) {
            // Rename Subcategory
            boolean hoverRename = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverRename) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x33FFAA00);
            g.text(this.font, "✏ Rename Subcategory", mx + 8, curY + 5, hoverRename ? 0xFFFFAA00 : 0xFFFFFFFF, false);
            curY += 20;

            // Delete Subcategory
            boolean hoverDelete = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverDelete) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x33EF4444);
            g.text(this.font, "🗑 §cDelete Subcategory", mx + 8, curY + 5, hoverDelete ? 0xFFFF6666 : 0xFFEF4444, false);
            curY += 20;

            // Move Up
            boolean hoverUp = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverUp) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x33FFAA00);
            g.text(this.font, "▲ Move Up", mx + 8, curY + 5, hoverUp ? 0xFFFFAA00 : 0xFFFFFFFF, false);
            curY += 20;

            // Move Down
            boolean hoverDown = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 18;
            if (hoverDown) g.fill(mx + 2, curY, mx + mw - 2, curY + 18, 0x33FFAA00);
            g.text(this.font, "▼ Move Down", mx + 8, curY + 5, hoverDown ? 0xFFFFAA00 : 0xFFFFFFFF, false);
        } else if (isFeatureListContextMenu) {
            // Add Subcategory / Separator
            boolean hoverNew = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 20;
            if (hoverNew) g.fill(mx + 2, curY, mx + mw - 2, curY + 20, 0x33FFAA00);
            g.text(this.font, "➕ §eAdd Subcategory", mx + 8, curY + 6, hoverNew ? 0xFFFDE68A : 0xFFFFAA00, false);
        } else {
            // Add New Category
            boolean hoverNew = mouseX >= mx && mouseX <= mx + mw && mouseY >= curY && mouseY <= curY + 20;
            if (hoverNew) g.fill(mx + 2, curY, mx + mw - 2, curY + 20, 0x3322C55E);
            g.text(this.font, "➕ §aCreate New Category", mx + 8, curY + 6, hoverNew ? 0xFF86EFAC : 0xFF22C55E, false);
        }
    }

    private void renderCategoryTagsModal(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int mw = 300;
        int mh = 120;
        int mx = (this.width - mw) / 2;
        int my = (this.height - mh) / 2;

        g.fill(mx, my, mx + mw, my + mh, 0xFF0F172A);
        g.outline(mx, my, mw, mh, 0xFF38BDF8);

        g.text(this.font, "§b§lTags for '" + targetCategoryForTags + "'", mx + 16, my + 12, 0xFFFFFFFF, false);
        g.text(this.font, "§7Search tags (e.g. garden, crops, money):", mx + 16, my + 28, 0xFF94A3B8, false);

        int inputX = mx + 16;
        int inputY = my + 42;
        int inputW = mw - 32;
        g.fill(inputX, inputY, inputX + inputW, inputY + 20, 0xFF1E293B);
        g.outline(inputX, inputY, inputW, 20, 0xFF38BDF8);

        categoryTagsCursorPos = Math.max(0, Math.min(categoryTagsInput.length(), categoryTagsCursorPos));
        String disp = categoryTagsInput.isEmpty() ? "§8(Enter comma-separated tags...)" : categoryTagsInput;
        g.text(this.font, disp, inputX + 6, inputY + 6, categoryTagsInput.isEmpty() ? 0xFF64748B : 0xFFFFFFFF, false);
        if (System.currentTimeMillis() / 500 % 2 == 0) {
            int cursorX = inputX + 6 + this.font.width(categoryTagsInput.substring(0, categoryTagsCursorPos));
            g.fill(cursorX, inputY + 4, cursorX + 1, inputY + 16, 0xFF38BDF8);
        }

        int btnW = 80;
        int btnH = 20;
        int cBtnX = mx + mw - 16 - btnW;
        int canBtnX = cBtnX - btnW - 8;
        int btnY = my + mh - 28;

        boolean canHover = mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean confHover = mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        ConfigUITheme.drawPillButton(g, this.font, "Cancel", canBtnX, btnY, btnW, btnH, canHover, 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);
        ConfigUITheme.drawPillButton(g, this.font, "Save", cBtnX, btnY, btnW, btnH, confHover, 0xFFFFFFFF, 0x440284C7, 0xFF0284C7);
    }

    private void commitCategoryTagsModal() {
        if (targetCategoryForTags != null && !targetCategoryForTags.isEmpty()) {
            pushUndoSnapshot();
            List<String> parsed = new ArrayList<>();
            for (String part : categoryTagsInput.split(",")) {
                String clean = part.trim().toLowerCase(Locale.ROOT);
                if (!clean.isEmpty() && !parsed.contains(clean)) {
                    parsed.add(clean);
                }
            }
            FeatureOrganizerManager.categoryTagsMap.put(targetCategoryForTags, parsed);
            FeatureOrganizerManager.save();
        }
        isEditingCategoryTags = false;
        targetCategoryForTags = "";
    }

    private void renderSubcategoryModal(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int mw = 280;
        int mh = 100;
        int mx = (this.width - mw) / 2;
        int my = (this.height - mh) / 2;

        g.fill(mx, my, mx + mw, my + mh, 0xFF0F172A);
        g.outline(mx, my, mw, mh, 0xFFFFAA00);

        String title = isCreatingSubcategory ? "Add Subcategory / Separator" : "Rename Subcategory";
        g.text(this.font, "§6§l" + title, mx + 16, my + 14, 0xFFFFFFFF, false);

        int inputX = mx + 16;
        int inputY = my + 36;
        int inputW = mw - 32;
        g.fill(inputX, inputY, inputX + inputW, inputY + 20, 0xFF1E293B);
        g.outline(inputX, inputY, inputW, 20, 0xFFFFAA00);

        subcategoryNameCursorPos = Math.max(0, Math.min(subcategoryNameInput.length(), subcategoryNameCursorPos));
        String disp = subcategoryNameInput.isEmpty() ? "§8(Subcategory name, e.g. Garden)" : subcategoryNameInput;
        g.text(this.font, disp, inputX + 6, inputY + 6, subcategoryNameInput.isEmpty() ? 0xFF64748B : 0xFFFFFFFF, false);
        if (System.currentTimeMillis() / 500 % 2 == 0) {
            int cursorX = inputX + 6 + this.font.width(subcategoryNameInput.substring(0, subcategoryNameCursorPos));
            g.fill(cursorX, inputY + 4, cursorX + 1, inputY + 16, 0xFFFFAA00);
        }

        int btnW = 80;
        int btnH = 20;
        int cBtnX = mx + mw - 16 - btnW;
        int canBtnX = cBtnX - btnW - 8;
        int btnY = my + mh - 28;

        boolean canHover = mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean confHover = mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        ConfigUITheme.drawPillButton(g, this.font, "Cancel", canBtnX, btnY, btnW, btnH, canHover, 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);
        ConfigUITheme.drawPillButton(g, this.font, "Confirm", cBtnX, btnY, btnW, btnH, confHover, 0xFFFFFFFF, 0x44D97706, 0xFFD97706);
    }

    private void commitSubcategoryModal() {
        String name = subcategoryNameInput.trim();
        if (!name.isEmpty() && selectedCategory != null && !selectedCategory.equalsIgnoreCase("ALL")) {
            pushUndoSnapshot();
            List<String> subcats = FeatureOrganizerManager.subcategoriesMap.computeIfAbsent(selectedCategory, k -> new ArrayList<>());
            if (isCreatingSubcategory) {
                if (!subcats.contains(name)) {
                    subcats.add(name);
                }
            } else if (isRenamingSubcategory && contextTargetSubcategory != null) {
                int idx = subcats.indexOf(contextTargetSubcategory);
                if (idx != -1) {
                    subcats.set(idx, name);
                }
                for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                    if (selectedCategory.equalsIgnoreCase(fm.category) && contextTargetSubcategory.equalsIgnoreCase(fm.subCategory)) {
                        fm.subCategory = name;
                    }
                }
            }
            FeatureOrganizerManager.save();
        }
        isCreatingSubcategory = false;
        isRenamingSubcategory = false;
        contextTargetSubcategory = null;
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

        int mw = 380;
        int mh = 330;
        int mx = (this.width - mw) / 2;
        int my = (this.height - mh) / 2;

        g.fill(mx, my, mx + mw, my + mh, 0xFF0F172A);
        g.outline(mx, my, mw, mh, 0xFF38BDF8);

        g.text(this.font, "§b§lEdit Feature Details", mx + 16, my + 12, 0xFFFFFFFF, false);

        // Field 0: Name input label + box
        g.text(this.font, "§7Feature Name:", mx + 16, my + 27, 0xFF94A3B8, false);
        int nameInputX = mx + 16;
        int nameInputY = my + 38;
        int nameInputW = mw - 32;
        g.fill(nameInputX, nameInputY, nameInputX + nameInputW, nameInputY + 18, 0xFF1E293B);
        g.outline(nameInputX, nameInputY, nameInputW, 18, editFeatureFocusField == 0 ? 0xFF38BDF8 : 0x3364748B);

        String nameText = editFeatureNameInput;
        g.enableScissor(nameInputX + 2, nameInputY + 1, nameInputX + nameInputW - 2, nameInputY + 17);
        g.text(this.font, nameText, nameInputX + 6, nameInputY + 5, 0xFFFFFFFF, false);
        if (editFeatureFocusField == 0 && (System.currentTimeMillis() / 500 % 2 == 0)) {
            editFeatureCursorPos = Math.max(0, Math.min(nameText.length(), editFeatureCursorPos));
            int cursorX = nameInputX + 6 + this.font.width(nameText.substring(0, editFeatureCursorPos));
            g.fill(cursorX, nameInputY + 3, cursorX + 1, nameInputY + 15, 0xFF38BDF8);
        }
        g.disableScissor();

        // Field 1: Description input label + box (multi-line word-wrapped and strictly scissored)
        g.text(this.font, "§7Description:", mx + 16, my + 60, 0xFF94A3B8, false);
        int descInputX = mx + 16;
        int descInputY = my + 71;
        int descInputW = mw - 32;
        int descInputH = 42;
        g.fill(descInputX, descInputY, descInputX + descInputW, descInputY + descInputH, 0xFF1E293B);
        g.outline(descInputX, descInputY, descInputW, descInputH, editFeatureFocusField == 1 ? 0xFF38BDF8 : 0x3364748B);

        String descText = editFeatureDescInput;
        editFeatureCursorPos = Math.max(0, Math.min(descText.length(), editFeatureCursorPos));

        List<net.minecraft.util.FormattedCharSequence> wrappedLines = this.font.split(Component.literal(descText), descInputW - 12);
        g.enableScissor(descInputX + 2, descInputY + 2, descInputX + descInputW - 2, descInputY + descInputH - 2);

        if (wrappedLines.isEmpty()) {
            if (editFeatureFocusField == 1 && (System.currentTimeMillis() / 500 % 2 == 0)) {
                g.fill(descInputX + 6, descInputY + 4, descInputX + 7, descInputY + 14, 0xFF38BDF8);
            }
        } else {
            int lineY = descInputY + 4;
            int running = 0;
            int cursorLine = 0;
            int cursorCol = 0;
            boolean cursorFound = false;

            for (int l = 0; l < wrappedLines.size(); l++) {
                net.minecraft.util.FormattedCharSequence seq = wrappedLines.get(l);
                StringBuilder sb = new StringBuilder();
                seq.accept((idx, style, cp) -> {
                    sb.appendCodePoint(cp);
                    return true;
                });
                String lineStr = sb.toString();
                g.text(this.font, lineStr, descInputX + 6, lineY, 0xFFFFFFFF, false);

                if (!cursorFound) {
                    if (editFeatureCursorPos <= running + lineStr.length()) {
                        cursorLine = l;
                        cursorCol = Math.max(0, editFeatureCursorPos - running);
                        cursorFound = true;
                    } else {
                        running += lineStr.length();
                        if (running < descText.length() && descText.charAt(running) == ' ') {
                            running++;
                        }
                    }
                }
                lineY += 10;
            }

            if (!cursorFound) {
                cursorLine = Math.max(0, wrappedLines.size() - 1);
                net.minecraft.util.FormattedCharSequence lastSeq = wrappedLines.get(cursorLine);
                StringBuilder lastSb = new StringBuilder();
                lastSeq.accept((idx, style, cp) -> {
                    lastSb.appendCodePoint(cp);
                    return true;
                });
                cursorCol = lastSb.length();
            }

            if (editFeatureFocusField == 1 && (System.currentTimeMillis() / 500 % 2 == 0)) {
                net.minecraft.util.FormattedCharSequence curSeq = wrappedLines.get(Math.min(cursorLine, wrappedLines.size() - 1));
                StringBuilder curSb = new StringBuilder();
                curSeq.accept((idx, style, cp) -> {
                    curSb.appendCodePoint(cp);
                    return true;
                });
                String sub = curSb.substring(0, Math.min(cursorCol, curSb.length()));
                int cursorX = descInputX + 6 + this.font.width(sub);
                int cY = descInputY + 4 + cursorLine * 10;
                g.fill(cursorX, cY, cursorX + 1, cY + 9, 0xFF38BDF8);
            }
        }
        g.disableScissor();

        // Field 2: Requires Parent Feature (Dependency)
        int depY = my + 119;
        g.text(this.font, "§7Requires Parent Feature (Dependency):", mx + 16, depY, 0xFF94A3B8, false);
        int depInputX = mx + 16;
        int depInputY = depY + 11;
        int clearW = editFeatureParentInput.isEmpty() ? 0 : 64;
        int depInputW = mw - 32 - (clearW > 0 ? (clearW + 6) : 0);

        g.fill(depInputX, depInputY, depInputX + depInputW, depInputY + 18, 0xFF1E293B);
        g.outline(depInputX, depInputY, depInputW, 18, editFeatureFocusField == 2 ? 0xFFFFAA00 : 0x3364748B);

        String depText = editFeatureParentInput;
        g.enableScissor(depInputX + 2, depInputY + 1, depInputX + depInputW - 2, depInputY + 17);
        if (depText.isEmpty()) {
            g.text(this.font, "§8(None - runs independently)", depInputX + 6, depInputY + 5, 0xFF64748B, false);
        } else {
            g.text(this.font, "§6" + depText, depInputX + 6, depInputY + 5, 0xFFFFFFFF, false);
        }
        if (editFeatureFocusField == 2 && (System.currentTimeMillis() / 500 % 2 == 0)) {
            editFeatureCursorPos = Math.max(0, Math.min(depText.length(), editFeatureCursorPos));
            int cursorX = depInputX + 6 + this.font.width(depText.substring(0, editFeatureCursorPos));
            g.fill(cursorX, depInputY + 3, cursorX + 1, depInputY + 15, 0xFFFFAA00);
        }
        g.disableScissor();

        if (clearW > 0) {
            int clearX = depInputX + depInputW + 6;
            boolean clearHover = mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= depInputY && mouseY <= depInputY + 18;
            ConfigUITheme.drawPillButton(g, this.font, "✕ Clear", clearX, depInputY, clearW, 18, clearHover, clearHover ? 0xFFFF6666 : 0xFFEF4444, 0x22EF4444, 0x44EF4444);
        }

        // Field 3: Subcategory / Separator
        int subcatY = my + 152;
        g.text(this.font, "§7Subcategory / Separator (optional):", mx + 16, subcatY, 0xFF94A3B8, false);
        int subcatInputX = mx + 16;
        int subcatInputY = subcatY + 11;
        int subClearW = editFeatureSubcatInput.isEmpty() ? 0 : 64;
        int subcatInputW = mw - 32 - (subClearW > 0 ? (subClearW + 6) : 0);
        g.fill(subcatInputX, subcatInputY, subcatInputX + subcatInputW, subcatInputY + 18, 0xFF1E293B);
        g.outline(subcatInputX, subcatInputY, subcatInputW, 18, editFeatureFocusField == 3 ? 0xFFFFAA00 : 0x3364748B);

        String subcatText = editFeatureSubcatInput;
        String subcatDisp = subcatText.isEmpty() ? "§8(No subcategory - top level)" : subcatText;
        g.enableScissor(subcatInputX + 2, subcatInputY + 1, subcatInputX + subcatInputW - 2, subcatInputY + 17);
        g.text(this.font, subcatDisp, subcatInputX + 6, subcatInputY + 5, subcatText.isEmpty() ? 0xFF64748B : 0xFFFFAA00, false);
        if (editFeatureFocusField == 3 && (System.currentTimeMillis() / 500 % 2 == 0)) {
            editFeatureCursorPos = Math.max(0, Math.min(subcatText.length(), editFeatureCursorPos));
            int cursorX = subcatInputX + 6 + this.font.width(subcatText.substring(0, editFeatureCursorPos));
            g.fill(cursorX, subcatInputY + 3, cursorX + 1, subcatInputY + 15, 0xFFFFAA00);
        }
        g.disableScissor();

        if (subClearW > 0) {
            int clearX = subcatInputX + subcatInputW + 6;
            boolean clearHover = mouseX >= clearX && mouseX <= clearX + subClearW && mouseY >= subcatInputY && mouseY <= subcatInputY + 18;
            ConfigUITheme.drawPillButton(g, this.font, "✕ Clear", clearX, subcatInputY, subClearW, 18, clearHover, clearHover ? 0xFFFF6666 : 0xFFEF4444, 0x22EF4444, 0x44EF4444);
        }

        // Field 4: Tags input label + box
        int tagsY = my + 185;
        g.text(this.font, "§7Tags (comma-separated, e.g. garden, crops):", mx + 16, tagsY, 0xFF94A3B8, false);
        int tagsInputX = mx + 16;
        int tagsInputY = tagsY + 11;
        int tagsInputW = mw - 32;
        g.fill(tagsInputX, tagsInputY, tagsInputX + tagsInputW, tagsInputY + 18, 0xFF1E293B);
        g.outline(tagsInputX, tagsInputY, tagsInputW, 18, editFeatureFocusField == 4 ? 0xFF38BDF8 : 0x3364748B);

        String tagsText = editFeatureTagsInput;
        String tagsDisp = tagsText.isEmpty() ? "§8(No custom tags)" : tagsText;
        g.enableScissor(tagsInputX + 2, tagsInputY + 1, tagsInputX + tagsInputW - 2, tagsInputY + 17);
        g.text(this.font, tagsDisp, tagsInputX + 6, tagsInputY + 5, tagsText.isEmpty() ? 0xFF64748B : 0xFFFFFFFF, false);
        if (editFeatureFocusField == 4 && (System.currentTimeMillis() / 500 % 2 == 0)) {
            editFeatureCursorPos = Math.max(0, Math.min(tagsText.length(), editFeatureCursorPos));
            int cursorX = tagsInputX + 6 + this.font.width(tagsText.substring(0, editFeatureCursorPos));
            g.fill(cursorX, tagsInputY + 3, cursorX + 1, tagsInputY + 15, 0xFF38BDF8);
        }
        g.disableScissor();

        // Enabled by Default checkbox row
        int defY = my + 220;
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
            int mw = 380;
            int mh = 330;
            int mx = (this.width - mw) / 2;
            int my = (this.height - mh) / 2;

            int nameInputX = mx + 16;
            int nameInputY = my + 38;
            int nameInputW = mw - 32;
            if (mouseX >= nameInputX && mouseX <= nameInputX + nameInputW && mouseY >= nameInputY && mouseY <= nameInputY + 18) {
                editFeatureFocusField = 0;
                editFeatureCursorPos = editFeatureNameInput.length();
                return true;
            }

            int descInputX = mx + 16;
            int descInputY = my + 71;
            int descInputW = mw - 32;
            int descInputH = 42;
            if (mouseX >= descInputX && mouseX <= descInputX + descInputW && mouseY >= descInputY && mouseY <= descInputY + descInputH) {
                editFeatureFocusField = 1;
                editFeatureCursorPos = editFeatureDescInput.length();
                return true;
            }

            // Parent Dependency field and Clear button
            int depY = my + 119;
            int depInputY = depY + 11;
            int clearW = editFeatureParentInput.isEmpty() ? 0 : 64;
            int depInputW = mw - 32 - (clearW > 0 ? (clearW + 6) : 0);
            int depInputX = mx + 16;

            if (mouseX >= depInputX && mouseX <= depInputX + depInputW && mouseY >= depInputY && mouseY <= depInputY + 18) {
                editFeatureFocusField = 2;
                editFeatureCursorPos = editFeatureParentInput.length();
                return true;
            }

            if (clearW > 0) {
                int clearX = depInputX + depInputW + 6;
                if (mouseX >= clearX && mouseX <= clearX + clearW && mouseY >= depInputY && mouseY <= depInputY + 18) {
                    editFeatureParentInput = "";
                    editFeatureCursorPos = 0;
                    return true;
                }
            }

            // Field 3: Subcategory input box
            int subcatY = my + 152;
            int subcatInputX = mx + 16;
            int subcatInputY = subcatY + 11;
            int subClearW = editFeatureSubcatInput.isEmpty() ? 0 : 64;
            int subcatInputW = mw - 32 - (subClearW > 0 ? (subClearW + 6) : 0);
            if (mouseX >= subcatInputX && mouseX <= subcatInputX + subcatInputW && mouseY >= subcatInputY && mouseY <= subcatInputY + 18) {
                editFeatureFocusField = 3;
                editFeatureCursorPos = editFeatureSubcatInput.length();
                return true;
            }

            if (subClearW > 0) {
                int clearX = subcatInputX + subcatInputW + 6;
                if (mouseX >= clearX && mouseX <= clearX + subClearW && mouseY >= subcatInputY && mouseY <= subcatInputY + 18) {
                    editFeatureSubcatInput = "";
                    editFeatureCursorPos = 0;
                    return true;
                }
            }

            // Field 4: Tags input box
            int tagsY = my + 185;
            int tagsInputX = mx + 16;
            int tagsInputY = tagsY + 11;
            int tagsInputW = mw - 32;
            if (mouseX >= tagsInputX && mouseX <= tagsInputX + tagsInputW && mouseY >= tagsInputY && mouseY <= tagsInputY + 18) {
                editFeatureFocusField = 4;
                editFeatureCursorPos = editFeatureTagsInput.length();
                return true;
            }

            // Enabled by default checkbox toggle
            int defY = my + 220;
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

        // 1c. Category Tags Modal Click
        if (isEditingCategoryTags) {
            int mw = 300;
            int mh = 120;
            int mx = (this.width - mw) / 2;
            int my = (this.height - mh) / 2;

            int inputX = mx + 16;
            int inputY = my + 42;
            int inputW = mw - 32;
            if (mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= inputY && mouseY <= inputY + 20) {
                categoryTagsCursorPos = categoryTagsInput.length();
                return true;
            }

            int btnW = 80;
            int btnH = 20;
            int cBtnX = mx + mw - 16 - btnW;
            int canBtnX = cBtnX - btnW - 8;
            int btnY = my + mh - 28;

            if (mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                commitCategoryTagsModal();
                return true;
            }
            if (mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                isEditingCategoryTags = false;
                return true;
            }
            return true;
        }

        // 1d. Subcategory Modals
        if (isCreatingSubcategory || isRenamingSubcategory) {
            int mw = 280;
            int mh = 100;
            int mx = (this.width - mw) / 2;
            int my = (this.height - mh) / 2;
            int btnW = 80;
            int btnH = 20;
            int cBtnX = mx + mw - 16 - btnW;
            int canBtnX = cBtnX - btnW - 8;
            int btnY = my + mh - 28;

            if (mouseX >= cBtnX && mouseX <= cBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                commitSubcategoryModal();
                return true;
            }
            if (mouseX >= canBtnX && mouseX <= canBtnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                isCreatingSubcategory = false;
                isRenamingSubcategory = false;
                contextTargetSubcategory = null;
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
            int mh = contextTargetCategory != null ? 144 : (contextTargetSubcategory != null ? 84 : 26);
            int mx = Math.min(this.width - mw - 10, contextMenuX);
            int my = Math.min(this.height - mh - 10, contextMenuY);

            if (mouseX >= mx && mouseX <= mx + mw && mouseY >= my && mouseY <= my + mh) {
                int curY = my + 3;
                if (contextTargetCategory != null) {
                    // Edit Tags
                    if (mouseY >= curY && mouseY <= curY + 18) {
                        isEditingCategoryTags = true;
                        targetCategoryForTags = contextTargetCategory;
                        List<String> tags = FeatureOrganizerManager.categoryTagsMap.get(contextTargetCategory);
                        categoryTagsInput = (tags != null) ? String.join(", ", tags) : "";
                        categoryTagsCursorPos = categoryTagsInput.length();
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Move Up
                        pushUndoSnapshot();
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
                        pushUndoSnapshot();
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
                        pushUndoSnapshot();
                        String toDelete = contextTargetCategory;
                        FeatureOrganizerManager.customCategories.remove(toDelete);
                        FeatureOrganizerManager.categoryTagsMap.remove(toDelete);
                        if (!FeatureOrganizerManager.customCategories.contains("Uncategorized")) {
                            FeatureOrganizerManager.customCategories.add("Uncategorized");
                        }
                        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                            if (fm.category.equalsIgnoreCase(toDelete)) {
                                fm.category = "Uncategorized";
                            }
                        }
                        if (selectedCategory.equalsIgnoreCase(toDelete)) selectedCategory = "Uncategorized";
                        FeatureOrganizerManager.save();
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Mark All Legit
                        pushUndoSnapshot();
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
                        pushUndoSnapshot();
                        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                            if (fm.category.equalsIgnoreCase(contextTargetCategory)) {
                                fm.isCheat = true;
                            }
                        }
                        FeatureOrganizerManager.save();
                        contextMenuOpen = false;
                        return true;
                    }
                } else if (contextTargetSubcategory != null) {
                    if (mouseY >= curY && mouseY <= curY + 18) { // Rename Subcategory
                        isRenamingSubcategory = true;
                        subcategoryNameInput = contextTargetSubcategory;
                        subcategoryNameCursorPos = subcategoryNameInput.length();
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Delete Subcategory
                        pushUndoSnapshot();
                        deleteSubcategory(selectedCategory, contextTargetSubcategory);
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Move Up
                        pushUndoSnapshot();
                        moveSubcategory(selectedCategory, contextTargetSubcategory, -1);
                        contextMenuOpen = false;
                        return true;
                    }
                    curY += 20;
                    if (mouseY >= curY && mouseY <= curY + 18) { // Move Down
                        pushUndoSnapshot();
                        moveSubcategory(selectedCategory, contextTargetSubcategory, 1);
                        contextMenuOpen = false;
                        return true;
                    }
                } else if (isFeatureListContextMenu) {
                    if (mouseY >= curY && mouseY <= curY + 20) { // Add Subcategory
                        isCreatingSubcategory = true;
                        subcategoryNameInput = "";
                        subcategoryNameCursorPos = 0;
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

        // 3. Scrollbars Click
        int sideX = winX;
        int sideY = winY + headerH + 1;
        int sideH = winH - headerH - 1;
        int listH = sideH - 34;

        if (this.maxSidebarScroll > 0) {
            int trackX = sideX + sidebarW - 4;
            if (mouseX >= trackX - 6 && mouseX <= trackX + 10 && mouseY >= sideY && mouseY <= sideY + listH) {
                double thumbH = Math.max(16, (double) listH * listH / (double) (this.maxSidebarScroll + listH));
                double fraction = (mouseY - sideY - thumbH / 2.0) / Math.max(1.0, (double) (listH - thumbH));
                this.sidebarScroll = Math.max(0, Math.min(this.maxSidebarScroll, fraction * this.maxSidebarScroll));
                this.isDraggingCategoryScrollbar = true;
                return true;
            }
        }

        int chkContentX = sideX + sidebarW + 12;
        int chkContentY = sideY + 8;
        int chkContentW = winW - sidebarW - 24;
        int chkContentH = sideH - 16;
        if (this.maxFeatureScroll > 0) {
            int trackX = chkContentX + chkContentW - 4;
            if (mouseX >= trackX - 6 && mouseX <= trackX + 10 && mouseY >= chkContentY && mouseY <= chkContentY + chkContentH) {
                double thumbH = Math.max(16, (double) chkContentH * chkContentH / (double) (this.maxFeatureScroll + chkContentH));
                double fraction = (mouseY - chkContentY - thumbH / 2.0) / Math.max(1.0, (double) (chkContentH - thumbH));
                this.featureScroll = Math.max(0, Math.min(this.maxFeatureScroll, fraction * this.maxFeatureScroll));
                this.isDraggingFeatureScrollbar = true;
                return true;
            }
        }

        // 4. Sidebar Categories Click
        if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + sideH) {
            int curY = sideY + 6 - (int) this.sidebarScroll;
            List<String> categories = getActiveCategories();

            // ALL Category
            if (mouseY >= curY && mouseY <= curY + 20) {
                if (button == 0) {
                    selectedCategory = "ALL";
                    selectedCategories.clear();
                    selectedCategories.add("ALL");
                    lastClickedCategoryIndex = -1;
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
                        boolean ctrl = isCtrlHeld();
                        boolean shift = isShiftHeld();

                        if (shift && lastClickedCategoryIndex >= 0 && lastClickedCategoryIndex < categories.size()) {
                            // Shift-click range selection (e.g. click 1, then Shift+click 5 -> selects 1..5)
                            int start = Math.min(lastClickedCategoryIndex, i);
                            int end = Math.max(lastClickedCategoryIndex, i);
                            selectedCategories.remove("ALL");
                            for (int idx = start; idx <= end; idx++) {
                                selectedCategories.add(categories.get(idx));
                            }
                            selectedCategory = cat;
                        } else if (ctrl) {
                            // Ctrl-click multi-selection toggle
                            selectedCategories.remove("ALL");
                            if (selectedCategories.contains(cat)) {
                                selectedCategories.remove(cat);
                                if (selectedCategories.isEmpty()) {
                                    selectedCategories.add("ALL");
                                    selectedCategory = "ALL";
                                    lastClickedCategoryIndex = -1;
                                } else {
                                    selectedCategory = selectedCategories.iterator().next();
                                }
                            } else {
                                selectedCategories.add(cat);
                                selectedCategory = cat;
                                lastClickedCategoryIndex = i;
                            }
                        } else {
                            // Standard single click
                            selectedCategories.clear();
                            selectedCategories.add(cat);
                            selectedCategory = cat;
                            lastClickedCategoryIndex = i;
                        }

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
            int startY = contentY + 4 - (int) this.featureScroll;

            if (!isAll) {
                List<FeatureRow> rows = getCategoryRows(list);
                int curRowY = startY;

                for (int i = 0; i < rows.size(); i++) {
                    FeatureRow r = rows.get(i);
                    int cardH = r.isSubcatHeader ? 26 : 40;
                    int cy = curRowY;
                    curRowY += cardH + 6;

                    if (r.isSubcatHeader) {
                        int cx = contentX + 4;
                        int cardW = contentW - 12;
                        if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                            if (button == 1) { // Right click on subcategory header
                                contextMenuOpen = true;
                                contextMenuX = (int) mouseX;
                                contextMenuY = (int) mouseY;
                                contextTargetCategory = null;
                                contextTargetSubcategory = r.subcatName;
                                isFeatureListContextMenu = false;
                                return true;
                            }
                            // Quick action buttons: ▲ Move Up, ▼ Move Down, ✎ Rename, ✕ Delete
                            int btnY = cy + 4;
                            int btnH = 18;
                            int delX = cx + cardW - 20;
                            if (mouseX >= delX && mouseX <= delX + 16 && mouseY >= btnY && mouseY <= btnY + btnH) {
                                pushUndoSnapshot();
                                deleteSubcategory(selectedCategory, r.subcatName);
                                return true;
                            }
                            int renX = delX - 20;
                            if (mouseX >= renX && mouseX <= renX + 16 && mouseY >= btnY && mouseY <= btnY + btnH) {
                                isRenamingSubcategory = true;
                                contextTargetSubcategory = r.subcatName;
                                subcategoryNameInput = r.subcatName;
                                subcategoryNameCursorPos = subcategoryNameInput.length();
                                return true;
                            }
                            int downX = renX - 20;
                            if (mouseX >= downX && mouseX <= downX + 16 && mouseY >= btnY && mouseY <= btnY + btnH) {
                                pushUndoSnapshot();
                                moveSubcategory(selectedCategory, r.subcatName, 1);
                                return true;
                            }
                            int upX = downX - 20;
                            if (mouseX >= upX && mouseX <= upX + 16 && mouseY >= btnY && mouseY <= btnY + btnH) {
                                pushUndoSnapshot();
                                moveSubcategory(selectedCategory, r.subcatName, -1);
                                return true;
                            }
                            return true;
                        }
                    } else {
                        FeatureOrganizerManager.FeatureMeta fm = r.feature;
                        int indent = r.indented ? 20 : 0;
                        int cx = contentX + 4 + indent;
                        int cardW = contentW - 12 - indent;

                        if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                            // Right Click -> Open Feature Edit Modal
                            if (button == 1) {
                                isEditingFeature = true;
                                editingFeature = fm;
                                editFeatureNameInput = fm.name != null ? fm.name : "";
                                editFeatureEnabledByDefault = fm.enabledByDefault;
                                editFeatureParentInput = fm.parentDependency != null ? fm.parentDependency : "";
                                editFeatureSubcatInput = fm.subCategory != null ? fm.subCategory : "";
                                editFeatureTagsInput = (fm.tags != null) ? String.join(", ", fm.tags) : "";
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
                                editFeatureParentInput = fm.parentDependency != null ? fm.parentDependency : "";
                                editFeatureSubcatInput = fm.subCategory != null ? fm.subCategory : "";
                                editFeatureTagsInput = (fm.tags != null) ? String.join(", ", fm.tags) : "";
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

                            // Check Remove from Subcategory button click
                            if (r.indented || (fm.subCategory != null && !fm.subCategory.trim().isEmpty())) {
                                int unassignBtnX = editBtnX - 18;
                                if (mouseX >= unassignBtnX && mouseX <= unassignBtnX + 16 && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                                    pushUndoSnapshot();
                                    fm.subCategory = "";
                                    FeatureOrganizerManager.save();
                                    return true;
                                }
                            }

                            int ctrlEnd = badgeX - 8;
                            if (item != null) {
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

                            // Start dragging feature / Multi-selection (Left click)
                            if (button == 0) {
                                boolean shift = isShiftHeld();
                                boolean ctrl = isCtrlHeld();

                                if (shift && lastClickedFeatureIndex >= 0 && lastClickedFeatureIndex < list.size()) {
                                    int start = Math.min(lastClickedFeatureIndex, i);
                                    int end = Math.max(lastClickedFeatureIndex, i);
                                    for (int idx = start; idx <= end; idx++) {
                                        selectedFeatures.add(list.get(idx));
                                    }
                                } else if (ctrl) {
                                    if (selectedFeatures.contains(fm)) {
                                        selectedFeatures.remove(fm);
                                    } else {
                                        selectedFeatures.add(fm);
                                    }
                                    lastClickedFeatureIndex = i;
                                } else {
                                    if (!selectedFeatures.contains(fm)) {
                                        selectedFeatures.clear();
                                        selectedFeatures.add(fm);
                                    }
                                    lastClickedFeatureIndex = i;
                                }

                                pendingDragFeature = fm;
                                featurePressX = mouseX;
                                featurePressY = mouseY;
                                dragOffsetX = (int) mouseX - cx;
                                dragOffsetY = (int) mouseY - cy;
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

                // Right click on empty spot in feature list -> Add Subcategory Context Menu
                if (button == 1) {
                    contextMenuOpen = true;
                    contextMenuX = (int) mouseX;
                    contextMenuY = (int) mouseY;
                    contextTargetCategory = null;
                    contextTargetSubcategory = null;
                    isFeatureListContextMenu = true;
                    return true;
                }
            } else {
                // ALL View
                int cardW = 210;
                int cardH = 34;
                int cols = Math.max(1, contentW / (cardW + 10));
                for (int i = 0; i < list.size(); i++) {
                    FeatureOrganizerManager.FeatureMeta fm = list.get(i);
                    int col = (i % cols);
                    int row = (i / cols);
                    int cx = contentX + col * (cardW + 10);
                    int cy = startY + row * (cardH + 8);

                    if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                    // Right Click -> Open Feature Edit Modal
                    if (button == 1) {
                        isEditingFeature = true;
                        editingFeature = fm;
                        editFeatureNameInput = fm.name != null ? fm.name : "";
                        editFeatureEnabledByDefault = fm.enabledByDefault;
                        editFeatureParentInput = fm.parentDependency != null ? fm.parentDependency : "";
                        editFeatureSubcatInput = fm.subCategory != null ? fm.subCategory : "";
                        editFeatureTagsInput = (fm.tags != null) ? String.join(", ", fm.tags) : "";
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
                            editFeatureParentInput = fm.parentDependency != null ? fm.parentDependency : "";
                            editFeatureSubcatInput = fm.subCategory != null ? fm.subCategory : "";
                            editFeatureTagsInput = (fm.tags != null) ? String.join(", ", fm.tags) : "";
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

                    // Start dragging feature / Multi-selection (Left click)
                    if (button == 0) {
                        boolean shift = isShiftHeld();
                        boolean ctrl = isCtrlHeld();

                        if (shift && lastClickedFeatureIndex >= 0 && lastClickedFeatureIndex < list.size()) {
                            // Shift-click range select features
                            int start = Math.min(lastClickedFeatureIndex, i);
                            int end = Math.max(lastClickedFeatureIndex, i);
                            for (int idx = start; idx <= end; idx++) {
                                selectedFeatures.add(list.get(idx));
                            }
                        } else if (ctrl) {
                            // Ctrl-click toggle selection
                            if (selectedFeatures.contains(fm)) {
                                selectedFeatures.remove(fm);
                            } else {
                                selectedFeatures.add(fm);
                            }
                            lastClickedFeatureIndex = i;
                        } else {
                            // Single click: if already in multi-selection, keep it so user can drag all together
                            if (!selectedFeatures.contains(fm)) {
                                selectedFeatures.clear();
                                selectedFeatures.add(fm);
                            }
                            lastClickedFeatureIndex = i;
                        }

                        pendingDragFeature = fm;
                        featurePressX = mouseX;
                        featurePressY = mouseY;
                        dragOffsetX = (int) mouseX - cx;
                        dragOffsetY = (int) mouseY - cy;
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
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();

        int sideX = winX;
        int sideY = winY + headerH + 1;
        int sideH = winH - headerH - 1;
        int listH = sideH - 34;
        int contentY = sideY + 8;
        int contentH = sideH - 16;

        if (this.isDraggingCategoryScrollbar && this.maxSidebarScroll > 0) {
            double thumbH = Math.max(16, (double) listH * listH / (double) (this.maxSidebarScroll + listH));
            double fraction = (mouseY - sideY - thumbH / 2.0) / Math.max(1.0, (double) (listH - thumbH));
            this.sidebarScroll = Math.max(0, Math.min(this.maxSidebarScroll, fraction * this.maxSidebarScroll));
            return true;
        }

        if (this.isDraggingFeatureScrollbar && this.maxFeatureScroll > 0) {
            double thumbH = Math.max(16, (double) contentH * contentH / (double) (this.maxFeatureScroll + contentH));
            double fraction = (mouseY - contentY - thumbH / 2.0) / Math.max(1.0, (double) (contentH - thumbH));
            this.featureScroll = Math.max(0, Math.min(this.maxFeatureScroll, fraction * this.maxFeatureScroll));
            return true;
        }
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
        // Do not reorder in-memory while dragging to prevent list shifting/flickering
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDraggingCategoryScrollbar = false;
        this.isDraggingFeatureScrollbar = false;
        double mouseX = event.x();
        double mouseY = event.y();

        if (isCategoryDragging && draggingCategory != null) {
            int sideX = winX;
            int sideY = winY + headerH + 1;
            int sideH = winH - headerH - 1;
            if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + sideH) {
                int curY = sideY + 6 - (int) this.sidebarScroll;
                curY += 22; // skip ALL
                List<String> categories = getActiveCategories();
                for (int i = 0; i < categories.size(); i++) {
                    if (mouseY >= curY && mouseY <= curY + 20) {
                        pushUndoSnapshot();
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
                List<String> categories = getActiveCategories();
                curY += 22; // skip ALL

                for (String cat : categories) {
                    if (mouseY >= curY && mouseY <= curY + 20) {
                        pushUndoSnapshot();
                        Set<FeatureOrganizerManager.FeatureMeta> toMove = new LinkedHashSet<>();
                        if (selectedFeatures.contains(draggingFeature)) {
                            toMove.addAll(selectedFeatures);
                        } else {
                            toMove.add(draggingFeature);
                        }
                        boolean ctrlHeld = isCtrlHeld();
                        for (FeatureOrganizerManager.FeatureMeta meta : toMove) {
                            if (ctrlHeld) {
                                // Ctrl+Drag: link the feature into the target category (duplicate)
                                if (meta.linkedCategories == null) meta.linkedCategories = new java.util.ArrayList<>();
                                if (!meta.belongsToCategory(cat)) {
                                    meta.linkedCategories.add(cat);
                                }
                            } else {
                                // Normal drag: move the feature to the target category
                                // Remove from linkedCategories if it was linked there
                                if (meta.linkedCategories != null) {
                                    meta.linkedCategories.removeIf(lc -> lc != null && lc.equalsIgnoreCase(cat));
                                }
                                meta.category = cat;
                                meta.subCategory = "";
                            }
                        }
                        FeatureOrganizerManager.save();
                        if (Minecraft.getInstance().player != null) {
                            String verb = ctrlHeld ? "Linked" : "Moved";
                            if (toMove.size() > 1) {
                                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §a" + verb + " §e" + toMove.size() + "§a features to category §b" + cat));
                            } else {
                                Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §a" + verb + " '§e" + draggingFeature.name + "§a' to category §b" + cat));
                            }
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
                    List<FeatureOrganizerManager.FeatureMeta> list = getFilteredFeatures();
                    boolean isAll = "ALL".equalsIgnoreCase(selectedCategory);
                    int startY = contentY + 4 - (int) this.featureScroll;

                    if (!isAll) {
                        List<FeatureRow> rows = getCategoryRows(list);
                        int curRowY = startY;

                        for (int i = 0; i < rows.size(); i++) {
                            FeatureRow r = rows.get(i);
                            int cardH = r.isSubcatHeader ? 26 : 40;
                            int cy = curRowY;
                            curRowY += cardH + 6;

                            if (r.isSubcatHeader) {
                                int cx = contentX + 4;
                                int cardW = contentW - 12;
                                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                                    pushUndoSnapshot();
                                    Set<FeatureOrganizerManager.FeatureMeta> toMove = new LinkedHashSet<>();
                                    if (selectedFeatures.contains(draggingFeature)) {
                                        toMove.addAll(selectedFeatures);
                                    } else {
                                        toMove.add(draggingFeature);
                                    }
                                    for (FeatureOrganizerManager.FeatureMeta fm : toMove) {
                                        fm.subCategory = r.subcatName;
                                    }
                                    FeatureOrganizerManager.save();
                                    if (Minecraft.getInstance().player != null) {
                                        if (toMove.size() > 1) {
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aMoved §e" + toMove.size() + "§a features into subcategory §6" + r.subcatName));
                                        } else {
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aMoved '§e" + draggingFeature.name + "§a' into subcategory §6" + r.subcatName));
                                        }
                                    }
                                    break;
                                }
                            } else {
                                FeatureOrganizerManager.FeatureMeta target = r.feature;
                                if (target == draggingFeature) continue;
                                int indent = r.indented ? 20 : 0;
                                int cx = contentX + 4 + indent;
                                int cardW = contentW - 12 - indent;

                                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                                    pushUndoSnapshot();
                                    draggingFeature.subCategory = r.indented ? r.subcatName : "";
                                    double relY = (mouseY - cy) / (double) cardH;
                                    if (relY < 0.25) {
                                        FeatureOrganizerManager.reorderFeatureRelative(draggingFeature.name, target.name, false);
                                        if (Minecraft.getInstance().player != null) {
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aMoved '§e" + draggingFeature.name + "§a' above '§b" + target.name + "§a'"));
                                        }
                                    } else if (relY > 0.75) {
                                        FeatureOrganizerManager.reorderFeatureRelative(draggingFeature.name, target.name, true);
                                        if (Minecraft.getInstance().player != null) {
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aMoved '§e" + draggingFeature.name + "§a' below '§b" + target.name + "§a'"));
                                        }
                                    } else {
                                        draggingFeature.parentDependency = target.name;
                                        FeatureOrganizerManager.save();
                                        if (Minecraft.getInstance().player != null) {
                                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aLinked '§e" + draggingFeature.name + "§a' as dependent on '§b" + target.name + "§a'"));
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        int cardW = 210;
                        int cardH = 34;
                        int cols = Math.max(1, contentW / (cardW + 10));
                        for (int i = 0; i < list.size(); i++) {
                            FeatureOrganizerManager.FeatureMeta target = list.get(i);
                            if (target == draggingFeature) continue;
                            int col = (i % cols);
                            int row = (i / cols);
                            int cx = contentX + col * (cardW + 10);
                            int cy = startY + row * (cardH + 8);

                            if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH) {
                                pushUndoSnapshot();
                                double relY = (mouseY - cy) / (double) cardH;
                                if (relY < 0.25) {
                                    FeatureOrganizerManager.reorderFeatureRelative(draggingFeature.name, target.name, false);
                                } else if (relY > 0.75) {
                                    FeatureOrganizerManager.reorderFeatureRelative(draggingFeature.name, target.name, true);
                                } else {
                                    draggingFeature.parentDependency = target.name;
                                }
                                break;
                            }
                        }
                    }
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
                } else if (editFeatureFocusField == 2 && editFeatureParentInput.length() < 40) {
                    editFeatureCursorPos = Math.max(0, Math.min(editFeatureParentInput.length(), editFeatureCursorPos));
                    editFeatureParentInput = editFeatureParentInput.substring(0, editFeatureCursorPos) + c + editFeatureParentInput.substring(editFeatureCursorPos);
                    editFeatureCursorPos++;
                    return true;
                } else if (editFeatureFocusField == 3 && editFeatureSubcatInput.length() < 30) {
                    editFeatureCursorPos = Math.max(0, Math.min(editFeatureSubcatInput.length(), editFeatureCursorPos));
                    editFeatureSubcatInput = editFeatureSubcatInput.substring(0, editFeatureCursorPos) + c + editFeatureSubcatInput.substring(editFeatureCursorPos);
                    editFeatureCursorPos++;
                    return true;
                } else if (editFeatureFocusField == 4 && editFeatureTagsInput.length() < 60) {
                    editFeatureCursorPos = Math.max(0, Math.min(editFeatureTagsInput.length(), editFeatureCursorPos));
                    editFeatureTagsInput = editFeatureTagsInput.substring(0, editFeatureCursorPos) + c + editFeatureTagsInput.substring(editFeatureCursorPos);
                    editFeatureCursorPos++;
                    return true;
                }
            }
        }
        if (isEditingCategoryTags) {
            if (c >= 32 && c != 127 && categoryTagsInput.length() < 60) {
                categoryTagsCursorPos = Math.max(0, Math.min(categoryTagsInput.length(), categoryTagsCursorPos));
                categoryTagsInput = categoryTagsInput.substring(0, categoryTagsCursorPos) + c + categoryTagsInput.substring(categoryTagsCursorPos);
                categoryTagsCursorPos++;
                return true;
            }
        }
        if (isCreatingSubcategory || isRenamingSubcategory) {
            if (c >= 32 && c != 127 && subcategoryNameInput.length() < 24) {
                subcategoryNameCursorPos = Math.max(0, Math.min(subcategoryNameInput.length(), subcategoryNameCursorPos));
                subcategoryNameInput = subcategoryNameInput.substring(0, subcategoryNameCursorPos) + c + subcategoryNameInput.substring(subcategoryNameCursorPos);
                subcategoryNameCursorPos++;
                return true;
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

        if (isEditingCategoryTags) {
            if (code == GLFW.GLFW_KEY_ENTER) {
                commitCategoryTagsModal();
                return true;
            } else if (code == GLFW.GLFW_KEY_ESCAPE) {
                isEditingCategoryTags = false;
                return true;
            } else if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    categoryTagsCursorPos = getPrevWordIndex(categoryTagsInput, categoryTagsCursorPos);
                } else if (categoryTagsCursorPos > 0) {
                    categoryTagsCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                if (isCtrl) {
                    categoryTagsCursorPos = getNextWordIndex(categoryTagsInput, categoryTagsCursorPos);
                } else if (categoryTagsCursorPos < categoryTagsInput.length()) {
                    categoryTagsCursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                categoryTagsCursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                categoryTagsCursorPos = categoryTagsInput.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (isCtrl) {
                    int prev = getPrevWordIndex(categoryTagsInput, categoryTagsCursorPos);
                    categoryTagsInput = categoryTagsInput.substring(0, prev) + categoryTagsInput.substring(categoryTagsCursorPos);
                    categoryTagsCursorPos = prev;
                } else if (categoryTagsCursorPos > 0 && !categoryTagsInput.isEmpty()) {
                    categoryTagsInput = categoryTagsInput.substring(0, categoryTagsCursorPos - 1) + categoryTagsInput.substring(categoryTagsCursorPos);
                    categoryTagsCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (isCtrl) {
                    int next = getNextWordIndex(categoryTagsInput, categoryTagsCursorPos);
                    categoryTagsInput = categoryTagsInput.substring(0, categoryTagsCursorPos) + categoryTagsInput.substring(next);
                } else if (categoryTagsCursorPos < categoryTagsInput.length()) {
                    categoryTagsInput = categoryTagsInput.substring(0, categoryTagsCursorPos) + categoryTagsInput.substring(categoryTagsCursorPos + 1);
                }
                return true;
            }
            return true;
        }

        if (isEditingFeature) {
            if (code == GLFW.GLFW_KEY_ENTER) {
                commitFeatureEditModal();
                return true;
            } else if (code == GLFW.GLFW_KEY_TAB) {
                editFeatureFocusField = (editFeatureFocusField + 1) % 5;
                if (editFeatureFocusField == 0) editFeatureCursorPos = editFeatureNameInput.length();
                else if (editFeatureFocusField == 1) editFeatureCursorPos = editFeatureDescInput.length();
                else if (editFeatureFocusField == 2) editFeatureCursorPos = editFeatureParentInput.length();
                else editFeatureCursorPos = editFeatureTagsInput.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_ESCAPE) {
                isEditingFeature = false;
                editingFeature = null;
                return true;
            } else if (code == GLFW.GLFW_KEY_UP) {
                if (editFeatureFocusField == 2) {
                    editFeatureFocusField = 1;
                    editFeatureCursorPos = editFeatureDescInput.length();
                    return true;
                } else if (editFeatureFocusField == 1) {
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
                        editFeatureFocusField = 2;
                        editFeatureCursorPos = 0;
                        return true;
                    }
                }
            } else if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    String target = (editFeatureFocusField == 0) ? editFeatureNameInput : (editFeatureFocusField == 1 ? editFeatureDescInput : editFeatureParentInput);
                    editFeatureCursorPos = getPrevWordIndex(target, editFeatureCursorPos);
                } else {
                    if (editFeatureCursorPos > 0) editFeatureCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                String target = (editFeatureFocusField == 0) ? editFeatureNameInput : (editFeatureFocusField == 1 ? editFeatureDescInput : editFeatureParentInput);
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
                editFeatureCursorPos = (editFeatureFocusField == 0) ? editFeatureNameInput.length() : (editFeatureFocusField == 1 ? editFeatureDescInput.length() : editFeatureParentInput.length());
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
                } else if (editFeatureFocusField == 1) {
                    if (isCtrl) {
                        int prev = getPrevWordIndex(editFeatureDescInput, editFeatureCursorPos);
                        editFeatureDescInput = editFeatureDescInput.substring(0, prev) + editFeatureDescInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos = prev;
                    } else if (editFeatureCursorPos > 0 && !editFeatureDescInput.isEmpty()) {
                        editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos - 1) + editFeatureDescInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos--;
                    }
                } else if (editFeatureFocusField == 2) {
                    if (isCtrl) {
                        int prev = getPrevWordIndex(editFeatureParentInput, editFeatureCursorPos);
                        editFeatureParentInput = editFeatureParentInput.substring(0, prev) + editFeatureParentInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos = prev;
                    } else if (editFeatureCursorPos > 0 && !editFeatureParentInput.isEmpty()) {
                        editFeatureParentInput = editFeatureParentInput.substring(0, editFeatureCursorPos - 1) + editFeatureParentInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos--;
                    }
                } else if (editFeatureFocusField == 3) {
                    if (isCtrl) {
                        int prev = getPrevWordIndex(editFeatureTagsInput, editFeatureCursorPos);
                        editFeatureTagsInput = editFeatureTagsInput.substring(0, prev) + editFeatureTagsInput.substring(editFeatureCursorPos);
                        editFeatureCursorPos = prev;
                    } else if (editFeatureCursorPos > 0 && !editFeatureTagsInput.isEmpty()) {
                        editFeatureTagsInput = editFeatureTagsInput.substring(0, editFeatureCursorPos - 1) + editFeatureTagsInput.substring(editFeatureCursorPos);
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
                } else if (editFeatureFocusField == 1) {
                    if (isCtrl) {
                        int next = getNextWordIndex(editFeatureDescInput, editFeatureCursorPos);
                        editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos) + editFeatureDescInput.substring(next);
                    } else if (editFeatureCursorPos < editFeatureDescInput.length()) {
                        editFeatureDescInput = editFeatureDescInput.substring(0, editFeatureCursorPos) + editFeatureDescInput.substring(editFeatureCursorPos + 1);
                    }
                } else if (editFeatureFocusField == 2) {
                    if (isCtrl) {
                        int next = getNextWordIndex(editFeatureParentInput, editFeatureCursorPos);
                        editFeatureParentInput = editFeatureParentInput.substring(0, editFeatureCursorPos) + editFeatureParentInput.substring(next);
                    } else if (editFeatureCursorPos < editFeatureParentInput.length()) {
                        editFeatureParentInput = editFeatureParentInput.substring(0, editFeatureCursorPos) + editFeatureParentInput.substring(editFeatureCursorPos + 1);
                    }
                } else if (editFeatureFocusField == 3) {
                    if (isCtrl) {
                        int next = getNextWordIndex(editFeatureTagsInput, editFeatureCursorPos);
                        editFeatureTagsInput = editFeatureTagsInput.substring(0, editFeatureCursorPos) + editFeatureTagsInput.substring(next);
                    } else if (editFeatureCursorPos < editFeatureTagsInput.length()) {
                        editFeatureTagsInput = editFeatureTagsInput.substring(0, editFeatureCursorPos) + editFeatureTagsInput.substring(editFeatureCursorPos + 1);
                    }
                }
                return true;
            }
        }

        if (isCreatingSubcategory || isRenamingSubcategory) {
            if (code == GLFW.GLFW_KEY_ENTER) {
                commitSubcategoryModal();
                return true;
            } else if (code == GLFW.GLFW_KEY_ESCAPE) {
                isCreatingSubcategory = false;
                isRenamingSubcategory = false;
                contextTargetSubcategory = null;
                return true;
            } else if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    subcategoryNameCursorPos = getPrevWordIndex(subcategoryNameInput, subcategoryNameCursorPos);
                } else if (subcategoryNameCursorPos > 0) {
                    subcategoryNameCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                if (isCtrl) {
                    subcategoryNameCursorPos = getNextWordIndex(subcategoryNameInput, subcategoryNameCursorPos);
                } else if (subcategoryNameCursorPos < subcategoryNameInput.length()) {
                    subcategoryNameCursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                subcategoryNameCursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                subcategoryNameCursorPos = subcategoryNameInput.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (isCtrl) {
                    int prev = getPrevWordIndex(subcategoryNameInput, subcategoryNameCursorPos);
                    subcategoryNameInput = subcategoryNameInput.substring(0, prev) + subcategoryNameInput.substring(subcategoryNameCursorPos);
                    subcategoryNameCursorPos = prev;
                } else if (subcategoryNameCursorPos > 0 && !subcategoryNameInput.isEmpty()) {
                    subcategoryNameInput = subcategoryNameInput.substring(0, subcategoryNameCursorPos - 1) + subcategoryNameInput.substring(subcategoryNameCursorPos);
                    subcategoryNameCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (isCtrl) {
                    int next = getNextWordIndex(subcategoryNameInput, subcategoryNameCursorPos);
                    subcategoryNameInput = subcategoryNameInput.substring(0, subcategoryNameCursorPos) + subcategoryNameInput.substring(next);
                } else if (subcategoryNameCursorPos < subcategoryNameInput.length()) {
                    subcategoryNameInput = subcategoryNameInput.substring(0, subcategoryNameCursorPos) + subcategoryNameInput.substring(subcategoryNameCursorPos + 1);
                }
                return true;
            }
            return true;
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

        if (isCtrl) {
            List<String> categories = getActiveCategories();
            // Ctrl+T: New Tab / Add Category
            if (code == GLFW.GLFW_KEY_T) {
                isCreatingCategory = true;
                categoryNameInput = "";
                categoryNameCursorPos = 0;
                return true;
            }
            // Ctrl+Z: Undo / Redo
            if (code == GLFW.GLFW_KEY_Z) {
                if (isShiftHeld()) {
                    if (popRedoSnapshot()) {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aReapplied last change (Redo)."));
                        }
                        if (!FeatureOrganizerManager.customCategories.contains(selectedCategory) && !"ALL".equalsIgnoreCase(selectedCategory)) {
                            selectedCategory = FeatureOrganizerManager.customCategories.isEmpty() ? "ALL" : FeatureOrganizerManager.customCategories.get(0);
                        }
                        selectedCategories.clear();
                        selectedCategories.add(selectedCategory);
                        return true;
                    }
                } else {
                    if (popUndoSnapshot()) {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aReverted last change (Undo)."));
                        }
                        if (!FeatureOrganizerManager.customCategories.contains(selectedCategory) && !"ALL".equalsIgnoreCase(selectedCategory)) {
                            selectedCategory = FeatureOrganizerManager.customCategories.isEmpty() ? "ALL" : FeatureOrganizerManager.customCategories.get(0);
                        }
                        selectedCategories.clear();
                        selectedCategories.add(selectedCategory);
                        return true;
                    }
                }
            }
            // Ctrl+Y: Redo
            if (code == GLFW.GLFW_KEY_Y) {
                if (popRedoSnapshot()) {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aReapplied last change (Redo)."));
                    }
                    if (!FeatureOrganizerManager.customCategories.contains(selectedCategory) && !"ALL".equalsIgnoreCase(selectedCategory)) {
                        selectedCategory = FeatureOrganizerManager.customCategories.isEmpty() ? "ALL" : FeatureOrganizerManager.customCategories.get(0);
                    }
                    selectedCategories.clear();
                    selectedCategories.add(selectedCategory);
                    return true;
                }
            }
            // Ctrl+W: Close / Delete all selected category tabs (or current category)
            if (code == GLFW.GLFW_KEY_W) {
                Set<String> toDelete = new LinkedHashSet<>(selectedCategories);
                toDelete.remove("ALL");
                toDelete.remove("Uncategorized");
                if (toDelete.isEmpty() && !"ALL".equalsIgnoreCase(selectedCategory) && !"Uncategorized".equalsIgnoreCase(selectedCategory)) {
                    toDelete.add(selectedCategory);
                }
                if (!toDelete.isEmpty()) {
                    pushUndoSnapshot();
                    for (String del : toDelete) {
                        for (FeatureOrganizerManager.FeatureMeta fm : FeatureOrganizerManager.features.values()) {
                            if (fm.category != null && fm.category.equalsIgnoreCase(del)) {
                                fm.category = "Uncategorized";
                            }
                        }
                        FeatureOrganizerManager.customCategories.remove(del);
                        FeatureOrganizerManager.categoryTagsMap.remove(del);
                    }
                    if (!FeatureOrganizerManager.customCategories.contains("Uncategorized")) {
                        FeatureOrganizerManager.customCategories.add("Uncategorized");
                    }
                    selectedCategory = "Uncategorized";
                    selectedCategories.clear();
                    selectedCategories.add("Uncategorized");
                    FeatureOrganizerManager.save();
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §cDeleted " + toDelete.size() + " categories (features moved to Uncategorized). Press §eCtrl+Z §cto undo."));
                    }
                    return true;
                }
            }
            // Ctrl+1 through Ctrl+8: Switch directly to tab 1 to 8
            if (code >= GLFW.GLFW_KEY_1 && code <= GLFW.GLFW_KEY_8) {
                int targetIdx = code - GLFW.GLFW_KEY_1;
                if (targetIdx < categories.size()) {
                    selectedCategory = categories.get(targetIdx);
                    selectedCategories.clear();
                    selectedCategories.add(selectedCategory);
                    lastClickedCategoryIndex = targetIdx;
                    this.featureScroll = 0.0;
                    return true;
                }
            }
            // Ctrl+9: Switch to last tab
            if (code == GLFW.GLFW_KEY_9) {
                if (!categories.isEmpty()) {
                    selectedCategory = categories.get(categories.size() - 1);
                    selectedCategories.clear();
                    selectedCategories.add(selectedCategory);
                    lastClickedCategoryIndex = categories.size() - 1;
                    this.featureScroll = 0.0;
                    return true;
                }
            }
            // Ctrl+Tab / Ctrl+Shift+Tab: Cycle tabs
            if (code == GLFW.GLFW_KEY_TAB) {
                if (!categories.isEmpty()) {
                    int curIdx = categories.indexOf(selectedCategory);
                    boolean shift = event.hasShiftDown() || (Minecraft.getInstance() != null && Minecraft.getInstance().hasShiftDown());
                    int nextIdx;
                    if (shift) {
                        nextIdx = curIdx <= 0 ? categories.size() - 1 : curIdx - 1;
                    } else {
                        nextIdx = (curIdx + 1) % categories.size();
                    }
                    selectedCategory = categories.get(nextIdx);
                    selectedCategories.clear();
                    selectedCategories.add(selectedCategory);
                    lastClickedCategoryIndex = nextIdx;
                    this.featureScroll = 0.0;
                    return true;
                }
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
            pushUndoSnapshot();
            String oldName = editingFeature.name;
            String newName = editFeatureNameInput.trim();
            String newDesc = editFeatureDescInput.trim();
            if (!newName.isEmpty()) {
                editingFeature.name = newName;
                editingFeature.description = newDesc;
                editingFeature.enabledByDefault = editFeatureEnabledByDefault;
                editingFeature.parentDependency = editFeatureParentInput.trim();
                editingFeature.subCategory = editFeatureSubcatInput.trim();
                if (!editingFeature.subCategory.isEmpty() && editingFeature.category != null) {
                    List<String> subcats = FeatureOrganizerManager.subcategoriesMap.computeIfAbsent(editingFeature.category, k -> new ArrayList<>());
                    if (!subcats.contains(editingFeature.subCategory)) {
                        subcats.add(editingFeature.subCategory);
                    }
                }

                List<String> parsedTags = new ArrayList<>();
                for (String t : editFeatureTagsInput.split(",")) {
                    String clean = t.trim().toLowerCase(Locale.ROOT);
                    if (!clean.isEmpty() && !parsedTags.contains(clean)) {
                        parsedTags.add(clean);
                    }
                }
                editingFeature.tags = parsedTags;

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
                    selectedCategories.clear();
                    selectedCategories.add(name);
                    lastClickedCategoryIndex = FeatureOrganizerManager.customCategories.size() - 1;
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
                    if (selectedCategory.equalsIgnoreCase(contextTargetCategory)) {
                        selectedCategory = name;
                        selectedCategories.remove(contextTargetCategory);
                        selectedCategories.add(name);
                    }
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

    private static boolean isCtrlHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long win = mc.getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean isShiftHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        long win = mc.getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}
