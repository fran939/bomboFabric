package me.bombo.bomboaddons.gui.config;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.SlotHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BomboConfigScreen extends Screen {
    public static Screen create() {
        return new BomboConfigScreen(null);
    }

    private final Screen parent;
    public static String activeCategory = "General";
    public static String searchQuery = "";
    private static EditBox searchBox;

    public static void setSearchQuery(String query) {
        searchQuery = query != null ? query : "";
        scrollAmount = 0.0;
        sidebarScrollAmount = 0.0;
    }

    // Viewport & Layout
    private int winX, winY, winW, winH;
    private int sidebarW = 180;
    private int headerH = 42;

    // Scroll state for Content and Sidebar (persists across open/close)
    private static double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private static double sidebarScrollAmount = 0.0;
    private double maxSidebarScroll = 0.0;

    // State for Color Picker, Keybinds, and Text Inputs
    private static ConfigItem activeColorItem = null;
    private static ConfigItem activeKeybindItem = null;
    public static ConfigItem activeTextItem = null;
    public static int activeTextCursor = -1;
    public static int activeTextScroll = 0;
    private static HudMoveScreen.HudTarget activeHudStyleTarget = null;
    private static int activeHudStyleTab = 0; // 0=Background, 1=Border, 2=Title, 3=Text

    // Direct numeric entry for sliders: clicking the value label turns it into a text field.
    private static ConfigItem editingSliderValueItem = null;
    private static String sliderValueBuffer = "";
    private static int sliderValueCursor = 0;

    // Deployable Dropdown List
    private static ConfigItem activeDropdownItem = null;
    private static int dropdownX = 0;
    private static int dropdownY = 0;
    private static int dropdownW = 110;

    // Draggable Sliders & Precision Arrow Key Navigation
    private ConfigItem draggingSliderItem = null;
    private ConfigItem selectedSliderItem = null;
    private ConfigItem hoveredSliderItem = null;
    private int dragTrackX = 0;
    private int dragTrackW = 100;

    // Hovered Keybind Conflict Tooltip
    private String hoveredConflictDescription = null;
    private int hoveredConflictX = 0;
    private int hoveredConflictY = 0;

    private static boolean isSameItem(ConfigItem a, ConfigItem b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.type != b.type) return false;
        String nameA = a.name != null ? a.name.trim() : "";
        String nameB = b.name != null ? b.name.trim() : "";
        return nameA.equalsIgnoreCase(nameB);
    }

    public BomboConfigScreen(Screen parent) {
        super(Component.literal("BomboAddons Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BomboConfig.Settings s = BomboConfig.get();
        float scale = s.guiWindowScale > 0.5f ? s.guiWindowScale : 1.0f;
        this.sidebarW = s.guiSidebarWidth >= 120 && s.guiSidebarWidth <= 260 ? s.guiSidebarWidth : 180;

        int targetW = (int) (880 * scale);
        int targetH = (int) (540 * scale);

        this.winW = Math.min(this.width - 24, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        List<String> available = ConfigRegistry.getAvailableCategories();
        if (!available.contains(activeCategory)) {
            activeCategory = available.contains("General") ? "General" : available.get(0);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        BomboConfig.Settings s = BomboConfig.get();
        this.hoveredSliderItem = null;
        this.hoveredConflictDescription = null;

        // Instant dynamic recalculation of window bounds and sidebar width on scale change
        float scale = s.guiWindowScale > 0 ? s.guiWindowScale : 1.0f;
        int targetW = (int) (880 * scale);
        int targetH = (int) (540 * scale);
        this.winW = Math.min(this.width - 24, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
        this.sidebarW = Math.max(120, Math.min(260, s.guiSidebarWidth > 0 ? s.guiSidebarWidth : 180));

        // Continuous slider dragging
        if (this.draggingSliderItem != null && this.dragTrackW > 0) {
            float pct = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - this.dragTrackX) / this.dragTrackW));
            if (this.draggingSliderItem.type == ConfigItem.Type.SLIDER_INT) {
                int min = this.draggingSliderItem.minInt;
                int max = this.draggingSliderItem.maxInt;
                int step = this.draggingSliderItem.stepInt > 0 ? this.draggingSliderItem.stepInt : 1;
                int rawVal = Math.round(min + pct * (max - min));
                int steppedVal = min + Math.round((float) (rawVal - min) / step) * step;
                steppedVal = Math.max(min, Math.min(max, steppedVal));
                if (this.draggingSliderItem.intSetter != null) {
                    this.draggingSliderItem.intSetter.accept(steppedVal);
                    BomboConfig.save();
                }
            } else if (this.draggingSliderItem.type == ConfigItem.Type.SLIDER_FLOAT) {
                float min = this.draggingSliderItem.minFloat;
                float max = this.draggingSliderItem.maxFloat;
                float step = this.draggingSliderItem.stepFloat > 0f ? this.draggingSliderItem.stepFloat : 0.01f;
                float rawVal = min + pct * (max - min);
                float steppedVal = min + Math.round((rawVal - min) / step) * step;
                steppedVal = Math.max(min, Math.min(max, steppedVal));
                if (this.draggingSliderItem.floatSetter != null) {
                    this.draggingSliderItem.floatSetter.accept(steppedVal);
                    BomboConfig.save();
                }
            }
        }

        // 1. Dynamic backdrop alpha (0% = clear, 100% = solid black)
        int bgAlpha = Math.max(0, Math.min(100, s.guiBackgroundOpacity)) * 255 / 100;
        int backdropColor = (bgAlpha << 24) | 0x080A0E;
        g.fill(0, 0, this.width, this.height, backdropColor);

        // 2. Main Window Container with dynamic theme surfaces
        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int sidebarBg = ConfigUITheme.getSidebarBg();
        int borderCol = ConfigUITheme.getBorderColor();
        int divCol = ConfigUITheme.getDividerColor();

        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);

        // Window Outline
        g.fill(winX, winY, winX + winW, winY + 1, borderCol);
        g.fill(winX, winY + winH - 1, winX + winW, winY + winH, borderCol);
        g.fill(winX, winY, winX + 1, winY + winH, borderCol);
        g.fill(winX + winW - 1, winY, winX + winW, winY + winH, borderCol);

        // 3. Header Bar
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.fill(winX, winY + headerH, winX + winW, winY + headerH + 1, divCol);

        // Header Title in Light Purple (0xFFD8B4FE)
        g.text(this.font, "§d§lBOMBOADDONS", winX + 16, winY + 14, 0xFFD8B4FE, false);
        g.text(this.font, "§8| §f" + (searchQuery.isEmpty() ? activeCategory : "Search Results"), winX + 115, winY + 14, ConfigUITheme.getTextMuted(), false);

        // Header Search Box
        int searchW = Math.min(220, Math.max(140, this.winW / 4));
        int searchX = winX + winW - searchW - 135;
        int searchY = winY + 11;
        ConfigCustomWidgets.renderCleanInputField(g, this.font, "Search settings...", searchQuery, "globalSearch", searchX, searchY, searchW, mouseX, mouseY);

        // Header Quick Actions: [❖ Move HUDs] and [✕ Close]
        int moveBtnX = winX + winW - 125;
        int moveBtnY = winY + 11;
        boolean moveHover = mouseX >= moveBtnX && mouseX <= moveBtnX + 85 && mouseY >= moveBtnY && mouseY <= moveBtnY + 20;
        ConfigUITheme.drawPillButton(g, this.font, "§6❖ Move HUDs", moveBtnX, moveBtnY, 85, 20, moveHover,
                moveHover ? 0xFFFFFFFF : 0xFFFFAA00, moveHover ? 0x44FFAA00 : 0x22FFAA00, moveHover ? 0xFFFFAA00 : 0x66FFAA00);

        int closeBtnX = winX + winW - 34;
        int closeBtnY = winY + 11;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 24 && mouseY >= closeBtnY && mouseY <= closeBtnY + 20;
        ConfigUITheme.drawPillButton(g, this.font, "✕", closeBtnX, closeBtnY, 24, 20, closeHover,
                closeHover ? 0xFFFF6666 : 0xFF94A3B8, closeHover ? 0x44EF4444 : 0x22EF4444, closeHover ? 0xFFEF4444 : 0x44EF4444);

        // 4. Sidebar Background & Border
        int sideX = winX;
        int sideY = winY + headerH + 1;
        int sideH = winH - headerH - 1;

        g.fill(sideX, sideY, sideX + sidebarW, sideY + sideH, sidebarBg);
        g.fill(sideX + sidebarW, sideY, sideX + sidebarW + 1, sideY + sideH, divCol);

        // Render Scrollable Sidebar Categories (Alphabetical, cheat-aware, no bottom button)
        renderSidebarCategories(g, sideX, sideY, sidebarW, sideH, mouseX, mouseY);

        // 5. Main Content Area
        int contentX = sideX + sidebarW + 12;
        int contentY = sideY + 8;
        int contentW = winW - sidebarW - 24;
        int contentH = sideH - 16;

        renderContentArea(g, contentX, contentY, contentW, contentH, mouseX, mouseY);

        // 6. Floating Modals & Dropdowns (rendered on top of everything)
        if (activeDropdownItem != null) {
            renderDropdownMenu(g, mouseX, mouseY);
        } else if (activeColorItem != null) {
            renderColorPickerModal(g, mouseX, mouseY);
        } else if (activeHudStyleTarget != null) {
            renderHudStyleModal(g, mouseX, mouseY);
        }
        ConfigCustomWidgets.renderColorDropdownPopup(g, this.font, mouseX, mouseY);
        if ("Dev".equalsIgnoreCase(activeCategory)) {
            FeatureOrganizerManager.renderDropdownOverlay(g, this.font, mouseX, mouseY);
        }

        // 7. Floating Conflict Tooltip
        if (this.hoveredConflictDescription != null) {
            String title = "§c⚠ Key Conflict";
            String detail = "§7Overlaps with: §e" + this.hoveredConflictDescription;
            int tw = Math.max(this.font.width(title), this.font.width(detail)) + 16;
            int th = 28;
            int tx = Math.min(this.width - tw - 8, this.hoveredConflictX + 12);
            int ty = Math.max(8, this.hoveredConflictY - 14);

            g.fill(tx, ty, tx + tw, ty + th, 0xEE1A0A0A);
            g.outline(tx, ty, tw, th, 0xFFFF4444);
            g.text(this.font, title, tx + 8, ty + 5, 0xFFFF5555, true);
            g.text(this.font, detail, tx + 8, ty + 16, 0xFFE2E8F0, false);
        }
    }

    private List<String> getVisibleCategories() {
        List<String> categories = ConfigRegistry.getAvailableCategories();
        if (!searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase().trim();
            List<String> filtered = new ArrayList<>();
            for (String cat : categories) {
                if (cat.toLowerCase().contains(q)) {
                    filtered.add(cat);
                } else {
                    for (ConfigItem it : ConfigRegistry.getItemsForCategory(cat)) {
                        if ((it.name != null && it.name.toLowerCase().contains(q)) || (it.description != null && it.description.toLowerCase().contains(q))) {
                            filtered.add(cat);
                            break;
                        }
                    }
                }
            }
            return filtered;
        }
        return categories;
    }

    private void renderSidebarCategories(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        int accent = ConfigUITheme.getAccentColor();
        List<String> categories = getVisibleCategories();

        int listH = h - 8;
        g.enableScissor(x, y, x + w, y + listH);

        int curY = y + 8 - (int) this.sidebarScrollAmount;
        int totalSidebarHeight = 0;

        for (String cat : categories) {
            boolean active = cat.equalsIgnoreCase(activeCategory);
            boolean hover = mouseX >= x + 6 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 20 && mouseY >= y && mouseY <= y + listH;

            // Extra divider before GUI Settings / Debug
            if (cat.equals("GUI Settings")) {
                g.fill(x + 12, curY - 3, x + w - 16, curY - 2, 0x33FFFFFF);
            }

            if (active) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x33000000 | (accent & 0x00FFFFFF));
                g.fill(x + 6, curY, x + 9, curY + 20, accent);
            } else if (hover) {
                g.fill(x + 6, curY, x + w - 12, curY + 20, 0x1AFFFFFF);
            }

            int textColor = active ? accent : (hover ? ConfigUITheme.getTextTitle() : ConfigUITheme.getTextMuted());
            String displayLabel = cat.equals("GUI Settings") ? "🎨 " + cat : (cat.equals("Debug") ? "⚙ " + cat : ConfigRegistry.displayCategoryName(cat));
            g.text(this.font, ConfigUITheme.formatFont(displayLabel), x + 16, curY + 6, textColor, false);

            curY += 22;
            totalSidebarHeight += 22;
        }

        g.disableScissor();

        this.maxSidebarScroll = Math.max(0, totalSidebarHeight - listH + 16);
    }

    private void renderContentArea(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<ConfigItem> items = searchQuery.isEmpty() ? ConfigRegistry.getItemsForCategory(activeCategory) : searchMatchingItems();

        int itemY = y - (int) this.scrollAmount;
        int totalHeight = 0;

        g.enableScissor(x, y, x + w, y + h);

        for (ConfigItem item : items) {
            if (isItemParentDisabled(item)) continue;

            int cardH = item.getEffectiveCardHeight();

            if (itemY + cardH >= y && itemY <= y + h) {
                renderConfigItemCard(g, item, x, itemY, w - 10, cardH, mouseX, mouseY);
            }

            itemY += cardH + 4;
            totalHeight += cardH + 4;
        }

        this.maxScroll = Math.max(0, totalHeight - h + 20);

        g.disableScissor();

        // Render scrollbar if needed
        if (totalHeight > h) {
            int trackX = x + w - 5;
            int trackY = y;
            int trackH = h;
            int thumbH = Math.max(16, (int) ((double) h * h / totalHeight));
            int thumbY = trackY + (int) ((trackH - thumbH) * (scrollAmount / maxScroll));
            ConfigUITheme.drawScrollBar(g, trackX, trackY, 4, trackH, thumbY, thumbH);
        }
    }

    private boolean isItemParentDisabled(ConfigItem item) {
        if (item == null || item.name == null) return false;
        FeatureOrganizerManager.FeatureMeta fm = FeatureOrganizerManager.features.get(item.name);
        if (fm != null && fm.parentDependency != null && !fm.parentDependency.trim().isEmpty()) {
            String parentName = fm.parentDependency.trim();
            ConfigItem parentItem = ConfigRegistry.getMasterItemsMap().get(parentName);
            if (parentItem != null && parentItem.boolGetter != null) {
                return !Boolean.TRUE.equals(parentItem.boolGetter.get());
            }
        }
        return false;
    }

    private void renderConfigItemCard(GuiGraphicsExtractor g, ConfigItem item, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (item.type == ConfigItem.Type.HEADER) {
            g.text(this.font, ConfigUITheme.formatFont("§6§l" + item.name.toUpperCase()), x + 4, y + 6, ConfigUITheme.ACCENT_GOLD, true);
            g.fill(x + 4, y + 18, x + w - 8, y + 19, 0x22FFAA00);
            return;
        }

        if (item.type == ConfigItem.Type.CUSTOM_CARD && item.customRenderer != null) {
            item.customRenderer.render(g, this.font, x, y, w, h, mouseX, mouseY);
            return;
        }

        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h && activeColorItem == null && activeDropdownItem == null;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        // Title & Description
        g.text(this.font, ConfigUITheme.formatFont(item.name), x + 10, y + 7, ConfigUITheme.getTextTitle(), false);
        if (item.description != null && !item.description.isEmpty()) {
            g.text(this.font, ConfigUITheme.formatFont(item.description), x + 10, y + 21, ConfigUITheme.getTextMuted(), false);
        }

        // Right side controls
        int ctrlRightX = x + w - 10;
        int ctrlY = y + 10;
        int accent = ConfigUITheme.getAccentColor();

        switch (item.type) {
            case TOGGLE -> {
                boolean active = item.boolGetter != null && item.boolGetter.get();
                int toggleW = 34;
                int toggleH = 18;
                int toggleX = ctrlRightX - toggleW;

                // Move icon button if HUD target exists
                if (item.hudTarget != null) {
                    int moveBtnX = toggleX - 30;
                    boolean moveHover = mouseX >= moveBtnX && mouseX <= moveBtnX + 24 && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                    ConfigUITheme.drawPillButton(g, this.font, "§e❖", moveBtnX, ctrlY, 24, 18, moveHover,
                            moveHover ? 0xFFFFFFFF : 0xFFFFAA00, moveHover ? 0x44FFAA00 : 0x22FFAA00, 0x66FFAA00);
                }

                boolean toggleHover = mouseX >= toggleX && mouseX <= toggleX + toggleW && mouseY >= ctrlY && mouseY <= ctrlY + toggleH;
                ConfigUITheme.drawToggleSwitch(g, toggleX, ctrlY, toggleW, toggleH, active, toggleHover);
            }

            case SLIDER_INT -> {
                if (hovered) this.hoveredSliderItem = item;
                int val = item.intGetter != null ? item.intGetter.get() : 0;
                int min = item.minInt;
                int max = item.maxInt;
                float pct = max > min ? Math.max(0.0f, Math.min(1.0f, (float) (val - min) / (max - min))) : 0.0f;

                // Draggable Slider Bar (Clean bar without +/- buttons)
                int trackW = 100;
                int trackH = 6;
                int trackX = ctrlRightX - 165;
                int trackY = ctrlY + 6;

                g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x33FFFFFF);
                g.outline(trackX, trackY, trackW, trackH, 0x22FFFFFF);

                int filledW = (int) (pct * trackW);
                if (filledW > 0) {
                    g.fill(trackX, trackY, trackX + filledW, trackY + trackH, accent);
                }

                // Thumb Handle
                int thumbX = trackX + filledW;
                g.fill(thumbX - 4, trackY - 3, thumbX + 4, trackY + trackH + 3, 0xFFFFFFFF);
                g.outline(thumbX - 4, trackY - 3, 8, trackH + 6, accent);

                // Value label - click it to type an exact number instead of dragging.
                renderSliderValue(g, item, val + item.intSuffix, ctrlRightX, ctrlY, mouseX, mouseY);
            }

            case SLIDER_COINS -> {
                if (hovered) this.hoveredSliderItem = item;
                long val = item.longGetter != null ? item.longGetter.get() : 0L;
                long min = item.minLong;
                long max = item.maxLong;
                float pct = max > min ? Math.max(0.0F, Math.min(1.0F, (float) (val - min) / (float) (max - min))) : 0.0F;

                int trackW = 100;
                int trackH = 6;
                int trackX = ctrlRightX - 165;
                int trackY = ctrlY + 6;

                g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x33FFFFFF);
                g.outline(trackX, trackY, trackW, trackH, 0x22FFFFFF);

                int filledW = (int) (pct * trackW);
                if (filledW > 0) {
                    g.fill(trackX, trackY, trackX + filledW, trackY + trackH, accent);
                }

                int thumbX = trackX + filledW;
                g.fill(thumbX - 4, trackY - 3, thumbX + 4, trackY + trackH + 3, 0xFFFFFFFF);
                g.outline(thumbX - 4, trackY - 3, 8, trackH + 6, accent);

                renderSliderValue(g, item, ConfigItem.formatCoins(val) + " coins", ctrlRightX, ctrlY, mouseX, mouseY);
            }

            case SLIDER_FLOAT -> {
                if (hovered) this.hoveredSliderItem = item;
                float val = item.floatGetter != null ? item.floatGetter.get() : 0f;
                float min = item.minFloat;
                float max = item.maxFloat;
                float pct = max > min ? Math.max(0.0f, Math.min(1.0f, (val - min) / (max - min))) : 0.0f;

                // Draggable Slider Bar
                int trackW = 100;
                int trackH = 6;
                int trackX = ctrlRightX - 165;
                int trackY = ctrlY + 6;

                g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x33FFFFFF);
                g.outline(trackX, trackY, trackW, trackH, 0x22FFFFFF);

                int filledW = (int) (pct * trackW);
                if (filledW > 0) {
                    g.fill(trackX, trackY, trackX + filledW, trackY + trackH, accent);
                }

                // Thumb Handle
                int thumbX = trackX + filledW;
                g.fill(thumbX - 4, trackY - 3, thumbX + 4, trackY + trackH + 3, 0xFFFFFFFF);
                g.outline(thumbX - 4, trackY - 3, 8, trackH + 6, accent);

                // Value label - click it to type an exact number instead of dragging.
                renderSliderValue(g, item, String.format(java.util.Locale.US, "%.2f", val) + item.floatSuffix, ctrlRightX, ctrlY, mouseX, mouseY);
            }

            case CYCLE -> {
                String curVal = item.stringGetter != null ? item.stringGetter.get() : "";
                int cycleW = 110;
                int cycleX = ctrlRightX - cycleW;
                boolean cycleHover = mouseX >= cycleX && mouseX <= cycleX + cycleW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                ConfigUITheme.drawPillButton(g, this.font, "§e" + curVal + " ▾", cycleX, ctrlY, cycleW, 18, cycleHover, -1, 0x22FFFFFF, 0x44FFFFFF);
            }

            case COLOR -> {
                String rawCol = item.stringGetter != null ? item.stringGetter.get() : "WHITE";
                String colorName = getColorDisplayName(rawCol);
                int swatchW = 95;
                int swatchX = ctrlRightX - swatchW;
                boolean swatchHover = mouseX >= swatchX && mouseX <= swatchX + swatchW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                ConfigUITheme.drawPillButton(g, this.font, "§f" + colorName + " ▾", swatchX, ctrlY, swatchW, 18, swatchHover, -1, 0x33FFFFFF, 0x55FFFFFF);
            }

            case KEYBIND -> {
                boolean listening = isSameItem(activeKeybindItem, item);
                String currentKey = item.stringGetter != null ? item.stringGetter.get() : "";
                boolean hasConflict = !listening && !currentKey.isEmpty() && isKeyConflict(item, currentKey);
                String prefix = hasConflict ? "§c" : "§b";
                String displayKey = listening ? "§e[PRESS KEY]" : (currentKey.isEmpty() ? "§8None" : prefix + ClickLogic.getKeyDisplayName(currentKey));

                int modeW = item.boolGetter != null ? 54 : 0;
                int keyW = item.boolGetter != null ? 76 : 85;
                int keyX = item.boolGetter != null ? (ctrlRightX - modeW - 6 - keyW) : (ctrlRightX - keyW);
                boolean keyHover = mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= ctrlY && mouseY <= ctrlY + 18;

                if (keyHover && hasConflict) {
                    this.hoveredConflictDescription = getKeyConflictDescription(item, currentKey);
                    this.hoveredConflictX = mouseX;
                    this.hoveredConflictY = mouseY;
                }
                int bg = listening ? 0x44FFAA00 : (hasConflict ? 0x44FF2222 : 0x22FFFFFF);
                int border = listening ? 0xFFFFAA00 : (hasConflict ? 0xFFFF4444 : 0x44FFFFFF);
                ConfigUITheme.drawPillButton(g, this.font, displayKey, keyX, ctrlY, keyW, 18, keyHover, -1, bg, border);

                if (item.boolGetter != null) {
                    int modeX = ctrlRightX - modeW;
                    boolean modeVal = item.boolGetter.get();
                    String modeStr = modeVal ? "§6Toggle" : "§aHold";
                    boolean modeHover = mouseX >= modeX && mouseX <= modeX + modeW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                    ConfigUITheme.drawPillButton(g, this.font, modeStr, modeX, ctrlY, modeW, 18, modeHover, -1, modeVal ? 0x33FFAA00 : 0x3300FF88, modeVal ? 0x88FFAA00 : 0x8800FF88);
                }
            }

            case BUTTON -> {
                String txt = item.buttonText != null ? item.buttonText : "Action";
                int btnW = 110;
                int btnX = ctrlRightX - btnW;
                boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                ConfigUITheme.drawPillButton(g, this.font, "§f" + txt, btnX, ctrlY, btnW, 18, btnHover, -1, btnHover ? 0x4400E5FF : 0x2200E5FF, 0x6600E5FF);
            }

            case SUBMENU -> {
                int btnW = 90;
                int btnX = ctrlRightX - btnW;
                boolean subHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= ctrlY && mouseY <= ctrlY + 18;
                ConfigUITheme.drawPillButton(g, this.font, "§bEdit →", btnX, ctrlY, btnW, 18, subHover, -1, 0x2200E5FF, 0x4400E5FF);
            }

            case TEXT -> {
                boolean focused = isSameItem(activeTextItem, item);
                String curVal = item.stringGetter != null ? item.stringGetter.get() : "";
                if (curVal == null) curVal = "";
                int textW = 160;
                int textX = ctrlRightX - textW;
                boolean textHover = mouseX >= textX && mouseX <= textX + textW && mouseY >= ctrlY && mouseY <= ctrlY + 18;

                g.fill(textX, ctrlY, textX + textW, ctrlY + 18, focused ? 0x4400E5FF : (textHover ? 0x331E293B : 0x221E293B));
                g.outline(textX, ctrlY, textW, 18, focused ? ConfigUITheme.getAccentColor() : (textHover ? 0x66FFFFFF : 0x33FFFFFF));

                int availW = textW - 14;
                if (focused) {
                    if (activeTextCursor < 0 || activeTextCursor > curVal.length()) {
                        activeTextCursor = curVal.length();
                    }
                    int cursorPx = this.font.width(curVal.substring(0, activeTextCursor));
                    if (cursorPx - activeTextScroll > availW) {
                        activeTextScroll = cursorPx - availW;
                    }
                    if (cursorPx - activeTextScroll < 0) {
                        activeTextScroll = cursorPx;
                    }
                } else {
                    activeTextScroll = 0;
                }

                g.enableScissor(textX + 2, ctrlY, textX + textW - 2, ctrlY + 18);
                if (curVal.isEmpty() && !focused) {
                    g.text(this.font, "§8Click to type...", textX + 6, ctrlY + 5, 0xFF64748B, false);
                } else {
                    int drawX = textX + 6 - (focused ? activeTextScroll : 0);
                    g.text(this.font, curVal, drawX, ctrlY + 5, 0xFFFFFFFF, false);
                    if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                        int cursorPx = this.font.width(curVal.substring(0, activeTextCursor));
                        g.fill(drawX + cursorPx, ctrlY + 4, drawX + cursorPx + 1, ctrlY + 14, 0xFF00E5FF);
                    }
                }
                g.disableScissor();
            }

            default -> {}
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Slider direct numeric input
    // ---------------------------------------------------------------------------------------------

    /** Hit rect of a slider's value label (where "§e500K coins" is drawn). */
    private boolean sliderLabelHit(int ctrlRightX, int ctrlY, int mouseX, int mouseY) {
        int boxX = ctrlRightX - 62;
        return mouseX >= boxX && mouseX <= boxX + 60 && mouseY >= ctrlY && mouseY <= ctrlY + 18;
    }

    private void beginSliderValueEdit(ConfigItem item, String initial) {
        editingSliderValueItem = item;
        sliderValueBuffer = initial != null ? initial : "";
        sliderValueCursor = sliderValueBuffer.length();
        // Keep the other editors from fighting over the same keystrokes.
        activeTextItem = null;
        activeColorItem = null;
    }

    private void cancelSliderValueEdit() {
        editingSliderValueItem = null;
        sliderValueBuffer = "";
        sliderValueCursor = 0;
    }

    /** Applies the typed value, clamped to the slider's range and snapped to its step. */
    private void commitSliderValueEdit() {
        ConfigItem item = editingSliderValueItem;
        if (item == null) return;
        String typed = sliderValueBuffer.trim();
        switch (item.type) {
            case SLIDER_INT -> {
                String normalized = typed.replaceAll("(?i)coins?", "").trim();
                long parsedInt = ConfigItem.parseCoins(normalized);
                if (parsedInt != Long.MIN_VALUE && item.intSetter != null) {
                    int step = item.stepInt > 0 ? item.stepInt : 1;
                    long min = item.minInt;
                    long max = item.maxInt;
                    long snapped = min + Math.round((double) (parsedInt - min) / step) * step;
                    item.intSetter.accept((int) Math.max(min, Math.min(max, snapped)));
                    BomboConfig.save();
                }
            }
            case SLIDER_FLOAT -> {
                try {
                    float parsed = Float.parseFloat(typed.replace(",", ""));
                    if (item.floatSetter != null) {
                        float step = item.stepFloat > 0F ? item.stepFloat : 0.01F;
                        float snapped = item.minFloat + Math.round((parsed - item.minFloat) / step) * step;
                        item.floatSetter.accept(Math.max(item.minFloat, Math.min(item.maxFloat, snapped)));
                        BomboConfig.save();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            case SLIDER_COINS -> {
                long parsed = ConfigItem.parseCoins(typed);
                if (parsed != Long.MIN_VALUE && item.longSetter != null) {
                    long step = item.stepLong > 0L ? item.stepLong : 1L;
                    long min = item.minLong;
                    long max = item.maxLong;
                    long snapped = min + Math.round((double) (parsed - min) / step) * step;
                    item.longSetter.accept(Math.max(min, Math.min(max, snapped)));
                    BomboConfig.save();
                }
            }
            default -> {
            }
        }
        cancelSliderValueEdit();
    }

    /** True when the keystroke was consumed by the slider's numeric editor. */
    private boolean handleSliderValueChar(char c) {
        if (editingSliderValueItem == null) return false;
        boolean isDigit = c >= '0' && c <= '9';
        boolean isSuffix = c == 'k' || c == 'K' || c == 'm' || c == 'M' || c == 'b' || c == 'B';
        boolean isDecimal = c == '.' && !sliderValueBuffer.contains(".");
        boolean isSeparator = c == ',' || c == '_';
        if ((isDigit || isSuffix || isDecimal || isSeparator) && sliderValueBuffer.length() < 24) {
            sliderValueCursor = Math.max(0, Math.min(sliderValueCursor, sliderValueBuffer.length()));
            sliderValueBuffer = sliderValueBuffer.substring(0, sliderValueCursor) + c + sliderValueBuffer.substring(sliderValueCursor);
            sliderValueCursor++;
        }
        return true;
    }

    private boolean handleSliderValueKey(KeyEvent event) {
        if (editingSliderValueItem == null) return false;
        int keyCode = event.key();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> commitSliderValueEdit();
            case GLFW.GLFW_KEY_ESCAPE -> cancelSliderValueEdit();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (sliderValueCursor > 0 && !sliderValueBuffer.isEmpty()) {
                    sliderValueBuffer = sliderValueBuffer.substring(0, sliderValueCursor - 1) + sliderValueBuffer.substring(sliderValueCursor);
                    sliderValueCursor--;
                }
            }
            case GLFW.GLFW_KEY_LEFT -> sliderValueCursor = Math.max(0, sliderValueCursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> sliderValueCursor = Math.min(sliderValueBuffer.length(), sliderValueCursor + 1);
            default -> {
            }
        }
        return true;
    }

    /**
     * Slider value label: a direct-input box while editing, otherwise a clickable label that
     * advertises itself on hover (the request was "click the number to type it").
     */
    private void renderSliderValue(GuiGraphicsExtractor g, ConfigItem item, String display, int ctrlRightX, int ctrlY, int mouseX, int mouseY) {
        int boxX = ctrlRightX - 62;
        int boxW = 60;
        boolean editing = editingSliderValueItem == item;
        boolean hoveredLabel = !editing && mouseX >= boxX && mouseX <= boxX + boxW && mouseY >= ctrlY && mouseY <= ctrlY + 18;

        if (editing) {
            g.fill(boxX, ctrlY, boxX + boxW, ctrlY + 18, 0x4400E5FF);
            g.outline(boxX, ctrlY, boxW, 18, ConfigUITheme.getAccentColor());
            g.enableScissor(boxX + 2, ctrlY, boxX + boxW - 2, ctrlY + 18);
            g.text(this.font, "§f" + sliderValueBuffer, boxX + 4, ctrlY + 5, -1, false);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int clamped = Math.max(0, Math.min(sliderValueCursor, sliderValueBuffer.length()));
                int caret = this.font.width(sliderValueBuffer.substring(0, clamped));
                g.fill(boxX + 4 + caret, ctrlY + 4, boxX + 5 + caret, ctrlY + 14, 0xFF00E5FF);
            }
            g.disableScissor();
            return;
        }

        if (hoveredLabel) {
            g.fill(boxX, ctrlY, boxX + boxW, ctrlY + 18, 0x22FFFFFF);
        }
        g.text(this.font, "§e" + display, boxX + 2, ctrlY + 5, -1, false);
    }

    private void renderDropdownMenu(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (activeDropdownItem == null) return;
        List<String> options = activeDropdownItem.cycleOptions != null ? activeDropdownItem.cycleOptions : SlotHighlight.COLORS;
        int rowH = 20;
        int totalH = options.size() * rowH + 8;
        int dx = dropdownX;
        int dy = dropdownY;
        int dw = dropdownW;

        // Backdrop drop shadow & card
        g.fill(dx - 1, dy - 1, dx + dw + 1, dy + totalH + 1, 0xEE11141F);
        g.outline(dx - 1, dy - 1, dw + 2, totalH + 2, ConfigUITheme.getAccentColor());

        String currentVal = activeDropdownItem.stringGetter != null ? activeDropdownItem.stringGetter.get() : "";

        for (int i = 0; i < options.size(); i++) {
            String opt = options.get(i);
            int optY = dy + 4 + i * rowH;
            boolean optHover = mouseX >= dx && mouseX <= dx + dw && mouseY >= optY && mouseY <= optY + rowH;

            if (optHover) {
                g.fill(dx + 2, optY, dx + dw - 2, optY + rowH, 0x33FFFFFF);
            }

            boolean isSelected = opt.equalsIgnoreCase(currentVal);
            int textColor = isSelected ? ConfigUITheme.getAccentColor() : (optHover ? 0xFFFFFFFF : 0xFFCBD5E1);

            // Color circle pill indicator
            int colorHex = getColorHexForName(opt);
            g.fill(dx + 8, optY + 5, dx + 16, optY + 13, colorHex);
            g.outline(dx + 8, optY + 5, 8, 8, 0x44FFFFFF);

            g.text(this.font, (isSelected ? "§l" : "") + opt, dx + 22, optY + 6, textColor, false);

            if (isSelected) {
                g.text(this.font, "✓", dx + dw - 14, optY + 6, ConfigUITheme.getAccentColor(), false);
            }
        }
    }

    private static final String[][] SKYBLOCK_DYE_PRESETS = new String[][]{
        {"#00E5FF", "Cyan"}, {"#FFAA00", "Gold"}, {"#10B981", "Emerald"}, {"#A855F7", "Purple"},
        {"#EF4444", "Red"}, {"#3B82F6", "Blue"}, {"#EC4899", "Pink"}, {"#FFFFFF", "White"},
        {"#7FFFD4", "Aquamarine"}, {"#E0115F", "Ruby"}, {"#50C878", "Emerald"}, {"#9966CC", "Amethyst"},
        {"#FF7F50", "Coral"}, {"#D4AF37", "Metallic"}, {"#FFD700", "Pure Gold"}, {"#000000", "Pure Black"}
    };

    public static String getColorDisplayName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "White";
        String clean = raw.trim();
        for (String[] preset : SKYBLOCK_DYE_PRESETS) {
            if (preset[0].equalsIgnoreCase(clean) || preset[1].equalsIgnoreCase(clean)) {
                return preset[1];
            }
        }
        String hex = clean.startsWith("#") ? clean.substring(1) : clean;
        try {
            int rgb = Integer.parseInt(hex, 16) & 0xFFFFFF;
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            double bestDist = Double.MAX_VALUE;
            String bestName = "#" + hex.toUpperCase();
            for (String[] preset : SKYBLOCK_DYE_PRESETS) {
                try {
                    int pRgb = Integer.parseInt(preset[0].substring(1), 16) & 0xFFFFFF;
                    int pr = (pRgb >> 16) & 0xFF;
                    int pg = (pRgb >> 8) & 0xFF;
                    int pb = pRgb & 0xFF;
                    double dist = Math.sqrt((r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb));
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestName = preset[1];
                    }
                } catch (Throwable ignored) {}
            }
            if (bestDist <= 45.0) {
                return bestName;
            }
            return "#" + hex.toUpperCase();
        } catch (Throwable ignored) {
            return clean;
        }
    }

    private int getColorHexForName(String name) {
        if (name == null) return 0xFFFFFFFF;
        return switch (name.toLowerCase()) {
            case "cyan" -> 0xFF00E5FF;
            case "gold", "yellow" -> 0xFFFFD700;
            case "emerald", "green" -> 0xFF10B981;
            case "purple" -> 0xFFA855F7;
            case "red" -> 0xFFEF4444;
            case "blue" -> 0xFF3B82F6;
            case "pink" -> 0xFFEC4899;
            case "orange" -> 0xFFF97316;
            case "black" -> 0xFF1E293B;
            default -> 0xFFFFFFFF;
        };
    }

    private static String colorPickerHexInput = "";
    private static boolean colorPickerHexFocused = false;
    private static float colorPickerHue = 0.0f;
    private static float colorPickerSat = 1.0f;
    private static float colorPickerVal = 1.0f;
    private static boolean isDraggingWheel = false;
    private static boolean isDraggingVal = false;

    private void openColorPicker(ConfigItem item) {
        activeColorItem = item;
        activeDropdownItem = null;
        String cur = item.stringGetter != null ? item.stringGetter.get() : "#FFFFFF";
        if (cur == null || cur.isEmpty()) cur = "#FFFFFF";
        colorPickerHexInput = cur.startsWith("#") ? cur.substring(1) : cur;
        colorPickerHexFocused = false;
        try {
            int rgb = parseColorRgb(cur);
            float[] hsv = new float[3];
            java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
            colorPickerHue = hsv[0];
            colorPickerSat = hsv[1];
            colorPickerVal = hsv[2];
        } catch (Throwable t) {
            colorPickerHue = 0.0f;
            colorPickerSat = 1.0f;
            colorPickerVal = 1.0f;
        }
    }

    private int parseColorRgb(String text) {
        if (text == null || text.isEmpty()) return 0xFFFFFF;
        if (text.startsWith("#")) text = text.substring(1);
        try {
            return Integer.parseInt(text, 16) & 0xFFFFFF;
        } catch (Exception e) {
            return getColorHexForName(text) & 0xFFFFFF;
        }
    }

    private void renderColorPickerModal(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
        int modalW = 280;
        int modalH = 260;
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        ConfigUITheme.drawCard(g, modalX, modalY, modalW, modalH, false);
        g.text(this.font, "§6§lSelect Color §8| §e" + (activeColorItem != null ? activeColorItem.name : ""), modalX + 12, modalY + 10, -1, false);

        int closeX = modalX + modalW - 24;
        int closeY = modalY + 8;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16;
        ConfigUITheme.drawPillButton(g, this.font, "✕", closeX, closeY, 16, 16, closeHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

        // 1. Interactive Color Wheel Disk / Box (Hue & Saturation)
        int wheelX = modalX + 16;
        int wheelY = modalY + 30;
        int wheelW = 120;
        int wheelH = 100;

        // Continuous dragging update
        if (isDraggingWheel) {
            float relX = Math.max(0f, Math.min(1f, (float)(mouseX - wheelX) / wheelW));
            float relY = Math.max(0f, Math.min(1f, (float)(mouseY - wheelY) / wheelH));
            colorPickerHue = relX;
            colorPickerSat = 1.0f - relY;
            updateColorPickerHexFromHsv();
        } else if (isDraggingVal) {
            int sliderY = modalY + 138;
            float relX = Math.max(0f, Math.min(1f, (float)(mouseX - wheelX) / wheelW));
            colorPickerVal = relX;
            updateColorPickerHexFromHsv();
        }

        // Draw Hue / Saturation gradient block
        int step = 4;
        for (int px = 0; px < wheelW; px += step) {
            float h = (float) px / wheelW;
            for (int py = 0; py < wheelH; py += step) {
                float sat = 1.0f - ((float) py / wheelH);
                int rgb = java.awt.Color.HSBtoRGB(h, sat, colorPickerVal);
                g.fill(wheelX + px, wheelY + py, wheelX + px + step, wheelY + py + step, 0xFF000000 | (rgb & 0xFFFFFF));
            }
        }
        g.outline(wheelX - 1, wheelY - 1, wheelW + 2, wheelH + 2, 0x55FFFFFF);

        // Wheel cursor indicator
        int curX = wheelX + (int)(colorPickerHue * wheelW);
        int curY = wheelY + (int)((1.0f - colorPickerSat) * wheelH);
        g.fill(curX - 3, curY - 3, curX + 3, curY + 3, 0xFFFFFFFF);
        g.outline(curX - 4, curY - 4, 8, 8, 0xFF000000);

        // 2. Brightness / Value Slider
        int valSliderY = modalY + 138;
        int valSliderH = 12;
        for (int px = 0; px < wheelW; px += 2) {
            float v = (float) px / wheelW;
            int rgb = java.awt.Color.HSBtoRGB(colorPickerHue, colorPickerSat, v);
            g.fill(wheelX + px, valSliderY, wheelX + px + 2, valSliderY + valSliderH, 0xFF000000 | (rgb & 0xFFFFFF));
        }
        g.outline(wheelX - 1, valSliderY - 1, wheelW + 2, valSliderH + 2, 0x55FFFFFF);
        int valThumbX = wheelX + (int)(colorPickerVal * wheelW);
        g.fill(valThumbX - 2, valSliderY - 2, valThumbX + 2, valSliderY + valSliderH + 2, 0xFFFFFFFF);
        g.outline(valThumbX - 3, valSliderY - 3, 6, valSliderH + 6, 0xFF000000);

        // 3. Current Color Preview Box & Hex Text Input
        int rightColX = modalX + 148;
        int previewH = 26;
        int currentRgb = java.awt.Color.HSBtoRGB(colorPickerHue, colorPickerSat, colorPickerVal) & 0xFFFFFF;
        g.fill(rightColX, wheelY, modalX + modalW - 16, wheelY + previewH, 0xFF000000 | currentRgb);
        g.outline(rightColX, wheelY, modalW - 164, previewH, 0x88FFFFFF);

        // Hex Input Field
        int hexY = wheelY + previewH + 8;
        int hexW = modalW - 164;
        g.fill(rightColX, hexY, rightColX + hexW, hexY + 18, colorPickerHexFocused ? 0x4400E5FF : 0x331E293B);
        g.outline(rightColX, hexY, hexW, 18, colorPickerHexFocused ? ConfigUITheme.getAccentColor() : 0x44FFFFFF);
        String hexDisplay = "#" + colorPickerHexInput + (colorPickerHexFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "§b|" : "");
        g.text(this.font, hexDisplay, rightColX + 6, hexY + 5, 0xFFFFFFFF, false);

        // Apply Hex Button
        int applyBtnY = hexY + 24;
        boolean applyHover = mouseX >= rightColX && mouseX <= rightColX + hexW && mouseY >= applyBtnY && mouseY <= applyBtnY + 18;
        ConfigUITheme.drawPillButton(g, this.font, "§a✔ Apply", rightColX, applyBtnY, hexW, 18, applyHover, -1, 0x3310B981, 0x6610B981);

        // 4. Skyblock Dyes & Swatches Grid
        int swatchStartY = modalY + 162;
        g.text(this.font, "§7Preset Dyes & Colors:", modalX + 16, swatchStartY, 0xFF94A3B8, false);

        int swatchGridY = swatchStartY + 12;
        int sCols = 8;
        int sSize = 14;
        int sGap = 4;
        for (int i = 0; i < SKYBLOCK_DYE_PRESETS.length; i++) {
            String[] dye = SKYBLOCK_DYE_PRESETS[i];
            int col = i % sCols;
            int row = i / sCols;
            int sx = modalX + 16 + col * (sSize + sGap);
            int sy = swatchGridY + row * (sSize + sGap);

            int dyeRgb = parseColorRgb(dye[0]);
            boolean sHover = mouseX >= sx && mouseX <= sx + sSize && mouseY >= sy && mouseY <= sy + sSize;
            g.fill(sx, sy, sx + sSize, sy + sSize, 0xFF000000 | dyeRgb);
            g.outline(sx, sy, sSize, sSize, sHover ? 0xFFFFFFFF : 0x44FFFFFF);
        }

        // Done / Close Button
        int doneY = modalY + modalH - 26;
        int doneW = 80;
        int doneX = modalX + (modalW - doneW) / 2;
        boolean doneHover = mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= doneY && mouseY <= doneY + 18;
        ConfigUITheme.drawPillButton(g, this.font, "§fDone", doneX, doneY, doneW, 18, doneHover, -1, 0x22FFFFFF, 0x44FFFFFF);
    }

    private void renderHudStyleModal(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x99000000);
        int modalW = 320;
        int modalH = 260;
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        ConfigUITheme.drawCard(g, modalX, modalY, modalW, modalH, false);
        String hudName = activeHudStyleTarget != null ? activeHudStyleTarget.name() : "HUD";
        g.text(this.font, "§6§lCustomize HUD §8| §e" + hudName, modalX + 12, modalY + 10, -1, false);

        int closeX = modalX + modalW - 24;
        int closeY = modalY + 8;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16;
        ConfigUITheme.drawPillButton(g, this.font, "✕", closeX, closeY, 16, 16, closeHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

        BomboConfig.HudStyle style = BomboConfig.get().getHudStyle(hudName);

        // Tabs: Background, Border, Title, Text
        String[] tabs = new String[]{"Background", "Border", "Title", "Text"};
        int tabW = (modalW - 24) / 4;
        for (int i = 0; i < tabs.length; i++) {
            int tx = modalX + 12 + i * tabW;
            int ty = modalY + 30;
            boolean active = activeHudStyleTab == i;
            boolean tHover = mouseX >= tx && mouseX <= tx + tabW - 2 && mouseY >= ty && mouseY <= ty + 18;
            int bg = active ? 0x4400E5FF : (tHover ? 0x22FFFFFF : 0x11FFFFFF);
            int border = active ? ConfigUITheme.getAccentColor() : 0x33FFFFFF;
            g.fill(tx, ty, tx + tabW - 2, ty + 18, bg);
            g.outline(tx, ty, tabW - 2, 18, border);
            int tw = this.font.width(tabs[i]);
            g.text(this.font, (active ? "§b§l" : "§7") + tabs[i], tx + (tabW - 2 - tw) / 2, ty + 5, active ? -1 : 0xFFCBD5E1, false);
        }

        // Current Color Display
        String currentColorHex = switch (activeHudStyleTab) {
            case 0 -> style.backgroundColor;
            case 1 -> style.borderColor;
            case 2 -> style.titleColor;
            default -> style.textColor;
        };
        int parsedRgb = BomboConfig.HudStyle.parseHexColor(currentColorHex, 0xFFFFFFFF);

        int previewY = modalY + 56;
        g.text(this.font, "§7Current Value: §e" + currentColorHex, modalX + 16, previewY + 4, 0xFFE2E8F0, false);
        g.fill(modalX + modalW - 60, previewY, modalX + modalW - 16, previewY + 16, parsedRgb);
        g.outline(modalX + modalW - 60, previewY, 44, 16, 0xFFFFFFFF);

        // Preset Color Swatches
        int swatchStartY = modalY + 80;
        g.text(this.font, "§7Quick Palette Presets:", modalX + 16, swatchStartY, 0xFF94A3B8, false);

        int swatchGridY = swatchStartY + 14;
        int sCols = 8;
        int sSize = 16;
        int sGap = 6;
        for (int i = 0; i < SKYBLOCK_DYE_PRESETS.length; i++) {
            String[] dye = SKYBLOCK_DYE_PRESETS[i];
            int col = i % sCols;
            int row = i / sCols;
            int sx = modalX + 16 + col * (sSize + sGap);
            int sy = swatchGridY + row * (sSize + sGap);

            int dyeRgb = parseColorRgb(dye[0]);
            boolean sHover = mouseX >= sx && mouseX <= sx + sSize && mouseY >= sy && mouseY <= sy + sSize;
            g.fill(sx, sy, sx + sSize, sy + sSize, 0xFF000000 | dyeRgb);
            g.outline(sx, sy, sSize, sSize, sHover ? 0xFFFFFFFF : 0x44FFFFFF);
        }

        // Reset to Default & Done Buttons
        int btnY = modalY + modalH - 26;
        int rBtnW = 100;
        int rBtnX = modalX + 16;
        boolean rHover = mouseX >= rBtnX && mouseX <= rBtnX + rBtnW && mouseY >= btnY && mouseY <= btnY + 18;
        ConfigUITheme.drawPillButton(g, this.font, "§cReset Defaults", rBtnX, btnY, rBtnW, 18, rHover, -1, 0x22EF4444, 0x44EF4444);

        int doneW = 80;
        int doneX = modalX + modalW - 16 - doneW;
        boolean doneHover = mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= btnY && mouseY <= btnY + 18;
        ConfigUITheme.drawPillButton(g, this.font, "§aDone", doneX, btnY, doneW, 18, doneHover, -1, 0x2210B981, 0x4410B981);
    }

    private void updateColorPickerHexFromHsv() {
        int rgb = java.awt.Color.HSBtoRGB(colorPickerHue, colorPickerSat, colorPickerVal) & 0xFFFFFF;
        colorPickerHexInput = String.format(java.util.Locale.US, "%06X", rgb);
        if (activeColorItem != null && activeColorItem.stringSetter != null) {
            activeColorItem.stringSetter.accept("#" + colorPickerHexInput);
            BomboConfig.save();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        // 0. Keybind Card / GUI Keybind Card / Config Item Keybind Mouse Button Capture (for buttons like Middle Click mouse3, mouse4, mouse5, right click, etc.)
        if (activeKeybindItem != null && event.button() > 0) {
            int mouseCode = 1000 + event.button();
            String prefix = ConfigCustomWidgets.getCurrentlyHeldComboPrefix(mouseCode);
            if (event.button() >= 2 && prefix.isEmpty()) {
                if (activeKeybindItem.stringSetter != null) {
                    activeKeybindItem.stringSetter.accept("mouse" + (event.button() + 1));
                }
                return true;
            }
            String comboStr = ConfigCustomWidgets.buildFullComboString(mouseCode);
            if (activeKeybindItem.stringSetter != null) {
                activeKeybindItem.stringSetter.accept(comboStr);
                BomboConfig.save();
            }
            activeKeybindItem = null;
            return true;
        }
        if (ConfigCustomWidgets.clickKeyIsListening && event.button() > 0) {
            int mouseCode = 1000 + event.button();
            String prefix = ConfigCustomWidgets.getCurrentlyHeldComboPrefix(mouseCode);
            if (event.button() >= 2 && prefix.isEmpty()) {
                ConfigCustomWidgets.clickKeyInput = "mouse" + (event.button() + 1);
                ConfigCustomWidgets.clickKeyIsListening = false;
                return true;
            }
            ConfigCustomWidgets.clickKeyInput = ConfigCustomWidgets.buildFullComboString(mouseCode);
            ConfigCustomWidgets.clickKeyIsListening = false;
            return true;
        }
        if (ConfigCustomWidgets.autoSeqKeyIsListening && event.button() > 0) {
            int mouseCode = 1000 + event.button();
            String prefix = ConfigCustomWidgets.getCurrentlyHeldComboPrefix(mouseCode);
            if (event.button() >= 2 && prefix.isEmpty()) {
                ConfigCustomWidgets.autoSeqKeyInput = "mouse" + (event.button() + 1);
                ConfigCustomWidgets.autoSeqKeyIsListening = false;
                return true;
            }
            ConfigCustomWidgets.autoSeqKeyInput = ConfigCustomWidgets.buildFullComboString(mouseCode);
            ConfigCustomWidgets.autoSeqKeyIsListening = false;
            return true;
        }
        if (ConfigCustomWidgets.kbIsListening && event.button() > 0) {
            int mouseCode = 1000 + event.button();
            String prefix = ConfigCustomWidgets.getCurrentlyHeldComboPrefix(mouseCode);
            if (event.button() >= 2 && prefix.isEmpty()) {
                ConfigCustomWidgets.kbKeyInput = "mouse" + (event.button() + 1);
                return true;
            }
            ConfigCustomWidgets.kbKeyInput = ConfigCustomWidgets.buildFullComboString(mouseCode);
            ConfigCustomWidgets.kbIsListening = false;
            return true;
        }
        if (ConfigCustomWidgets.guiKbIsListening && event.button() > 0) {
            int mouseCode = 1000 + event.button();
            String prefix = ConfigCustomWidgets.getCurrentlyHeldComboPrefix(mouseCode);
            if (event.button() >= 2 && prefix.isEmpty()) {
                ConfigCustomWidgets.guiKbKeyInput = "mouse" + (event.button() + 1);
                return true;
            }
            ConfigCustomWidgets.guiKbKeyInput = ConfigCustomWidgets.buildFullComboString(mouseCode);
            ConfigCustomWidgets.guiKbIsListening = false;
            return true;
        }

        // Deployable Color Popup Click
        if (ConfigCustomWidgets.handleColorDropdownClick(mouseX, mouseY)) {
            return true;
        }

        // 1. Modal Overlay Clicks
        if (activeDropdownItem != null) {
            List<String> options = activeDropdownItem.cycleOptions != null ? activeDropdownItem.cycleOptions : SlotHighlight.COLORS;
            int rowH = 20;
            int totalH = options.size() * rowH + 8;
            int dx = dropdownX;
            int dy = dropdownY;
            int dw = dropdownW;

            if (mouseX >= dx && mouseX <= dx + dw && mouseY >= dy && mouseY <= dy + totalH) {
                int clickedIdx = (int) (mouseY - dy - 4) / rowH;
                if (clickedIdx >= 0 && clickedIdx < options.size()) {
                    String selected = options.get(clickedIdx);
                    if (activeDropdownItem.stringSetter != null) {
                        activeDropdownItem.stringSetter.accept(selected);
                        BomboConfig.save();
                        this.init();
                    }
                }
            }
            activeDropdownItem = null;
            return true;
        }

        // 1. Color Picker Modal handling
        if (activeColorItem != null) {
            int modalW = 280;
            int modalH = 260;
            int modalX = (this.width - modalW) / 2;
            int modalY = (this.height - modalH) / 2;

            int closeX = modalX + modalW - 24;
            int closeY = modalY + 8;
            if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16) {
                activeColorItem = null;
                return true;
            }

            // Wheel click
            int wheelX = modalX + 16;
            int wheelY = modalY + 30;
            int wheelW = 120;
            int wheelH = 100;
            if (mouseX >= wheelX && mouseX <= wheelX + wheelW && mouseY >= wheelY && mouseY <= wheelY + wheelH) {
                isDraggingWheel = true;
                float relX = Math.max(0f, Math.min(1f, (float)(mouseX - wheelX) / wheelW));
                float relY = Math.max(0f, Math.min(1f, (float)(mouseY - wheelY) / wheelH));
                colorPickerHue = relX;
                colorPickerSat = 1.0f - relY;
                updateColorPickerHexFromHsv();
                return true;
            }

            // Brightness / Value Slider click
            int valSliderY = modalY + 138;
            int valSliderH = 12;
            if (mouseX >= wheelX && mouseX <= wheelX + wheelW && mouseY >= valSliderY - 2 && mouseY <= valSliderY + valSliderH + 2) {
                isDraggingVal = true;
                float relX = Math.max(0f, Math.min(1f, (float)(mouseX - wheelX) / wheelW));
                colorPickerVal = relX;
                updateColorPickerHexFromHsv();
                return true;
            }

            // Hex Input Click
            int rightColX = modalX + 148;
            int previewH = 26;
            int hexY = wheelY + previewH + 8;
            int hexW = modalW - 164;
            if (mouseX >= rightColX && mouseX <= rightColX + hexW && mouseY >= hexY && mouseY <= hexY + 18) {
                colorPickerHexFocused = true;
                return true;
            } else {
                colorPickerHexFocused = false;
            }

            // Apply Hex Button Click
            int applyBtnY = hexY + 24;
            if (mouseX >= rightColX && mouseX <= rightColX + hexW && mouseY >= applyBtnY && mouseY <= applyBtnY + 18) {
                try {
                    int rgb = parseColorRgb(colorPickerHexInput);
                    float[] hsv = new float[3];
                    java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
                    colorPickerHue = hsv[0];
                    colorPickerSat = hsv[1];
                    colorPickerVal = hsv[2];
                    if (activeColorItem.stringSetter != null) {
                        activeColorItem.stringSetter.accept("#" + String.format(java.util.Locale.US, "%06X", rgb));
                        BomboConfig.save();
                    }
                } catch (Throwable ignored) {}
                return true;
            }

            // Presets Click
            int swatchStartY = modalY + 162;
            int swatchGridY = swatchStartY + 12;
            int sCols = 8;
            int sSize = 14;
            int sGap = 4;
            for (int i = 0; i < SKYBLOCK_DYE_PRESETS.length; i++) {
                String[] dye = SKYBLOCK_DYE_PRESETS[i];
                int col = i % sCols;
                int row = i / sCols;
                int sx = modalX + 16 + col * (sSize + sGap);
                int sy = swatchGridY + row * (sSize + sGap);
                if (mouseX >= sx && mouseX <= sx + sSize && mouseY >= sy && mouseY <= sy + sSize) {
                    String hex = dye[0];
                    colorPickerHexInput = hex.startsWith("#") ? hex.substring(1) : hex;
                    int rgb = parseColorRgb(hex);
                    float[] hsv = new float[3];
                    java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
                    colorPickerHue = hsv[0];
                    colorPickerSat = hsv[1];
                    colorPickerVal = hsv[2];
                    if (activeColorItem.stringSetter != null) {
                        activeColorItem.stringSetter.accept(hex);
                        BomboConfig.save();
                    }
                    return true;
                }
            }

            // Done Button Click
            int doneY = modalY + modalH - 26;
            int doneW = 80;
            int doneX = modalX + (modalW - doneW) / 2;
            if (mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= doneY && mouseY <= doneY + 18) {
                activeColorItem = null;
                return true;
            }

            return true;
        }

        // 1b. HUD Style Customizer Modal Handling
        if (activeHudStyleTarget != null) {
            int modalW = 320;
            int modalH = 260;
            int modalX = (this.width - modalW) / 2;
            int modalY = (this.height - modalH) / 2;

            int closeX = modalX + modalW - 24;
            int closeY = modalY + 8;
            if (mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 16) {
                activeHudStyleTarget = null;
                return true;
            }

            // Tabs click
            int tabW = (modalW - 24) / 4;
            for (int i = 0; i < 4; i++) {
                int tx = modalX + 12 + i * tabW;
                int ty = modalY + 30;
                if (mouseX >= tx && mouseX <= tx + tabW - 2 && mouseY >= ty && mouseY <= ty + 18) {
                    activeHudStyleTab = i;
                    return true;
                }
            }

            String hudName = activeHudStyleTarget.name();
            BomboConfig.HudStyle style = BomboConfig.get().getHudStyle(hudName);

            // Palette swatch click
            int swatchStartY = modalY + 80;
            int swatchGridY = swatchStartY + 14;
            int sCols = 8;
            int sSize = 16;
            int sGap = 6;
            for (int i = 0; i < SKYBLOCK_DYE_PRESETS.length; i++) {
                String[] dye = SKYBLOCK_DYE_PRESETS[i];
                int col = i % sCols;
                int row = i / sCols;
                int sx = modalX + 16 + col * (sSize + sGap);
                int sy = swatchGridY + row * (sSize + sGap);
                if (mouseX >= sx && mouseX <= sx + sSize && mouseY >= sy && mouseY <= sy + sSize) {
                    String hex = dye[0];
                    switch (activeHudStyleTab) {
                        case 0 -> style.backgroundColor = "#90" + hex.substring(1);
                        case 1 -> style.borderColor = hex;
                        case 2 -> style.titleColor = hex;
                        default -> style.textColor = hex;
                    }
                    BomboConfig.save();
                    return true;
                }
            }

            // Reset Defaults Button
            int btnY = modalY + modalH - 26;
            int rBtnW = 100;
            int rBtnX = modalX + 16;
            if (mouseX >= rBtnX && mouseX <= rBtnX + rBtnW && mouseY >= btnY && mouseY <= btnY + 18) {
                style.backgroundColor = "#90000000";
                style.borderColor = "#FFAA00";
                style.titleColor = "#FFAA00";
                style.textColor = "#FFFFFF";
                BomboConfig.save();
                return true;
            }

            // Done Button
            int doneW = 80;
            int doneX = modalX + modalW - 16 - doneW;
            if (mouseX >= doneX && mouseX <= doneX + doneW && mouseY >= btnY && mouseY <= btnY + 18) {
                activeHudStyleTarget = null;
                return true;
            }

            return true;
        }

        // 2. Header Search & Quick Actions
        int searchW = Math.min(220, Math.max(140, this.winW / 4));
        int searchX = winX + winW - searchW - 135;
        int searchY = winY + 11;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 20) {
            if (event.button() == 1) {
                setSearchQuery("");
                if (searchBox != null) searchBox.setValue("");
            }
            ConfigCustomWidgets.activeFocusedField = "globalSearch";
            return true;
        }

        int moveBtnX = winX + winW - 125;
        int moveBtnY = winY + 11;
        if (mouseX >= moveBtnX && mouseX <= moveBtnX + 85 && mouseY >= moveBtnY && mouseY <= moveBtnY + 20) {
            Minecraft.getInstance().setScreenAndShow(new HudMoveScreen());
            return true;
        }

        int closeBtnX = winX + winW - 34;
        int closeBtnY = winY + 11;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 24 && mouseY >= closeBtnY && mouseY <= closeBtnY + 20) {
            BomboConfig.save();
            this.minecraft.setScreenAndShow(this.parent);
            return true;
        }

        // 3. Sidebar Categories Click (From sorted available categories list)
        int sideX = winX;
        int sideY = winY + headerH + 1;
        int sideH = winH - headerH - 1;
        int listH = sideH - 8;

        if (mouseX >= sideX && mouseX <= sideX + sidebarW && mouseY >= sideY && mouseY <= sideY + listH) {
            int curY = sideY + 8 - (int) this.sidebarScrollAmount;
            List<String> categories = getVisibleCategories();

            for (String cat : categories) {
                if (mouseY >= curY && mouseY <= curY + 20) {
                    activeCategory = cat;
                    this.scrollAmount = 0.0;
                    FeatureOrganizerManager.dropdownOpen = false;
                    return true;
                }
                curY += 22;
            }
        }

        // 4. Content Items Click
        int contentX = sideX + sidebarW + 12;
        int contentY = sideY + 8;
        int contentW = winW - sidebarW - 24;
        int contentH = sideH - 16;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            List<ConfigItem> items = searchQuery.isEmpty() ? ConfigRegistry.getItemsForCategory(activeCategory) : searchMatchingItems();
            int itemY = contentY - (int) this.scrollAmount;

            for (ConfigItem item : items) {
                if (isItemParentDisabled(item)) continue;

                int cardH = item.getEffectiveCardHeight();

                if (mouseY >= itemY && mouseY <= itemY + cardH) {
                    handleItemClick(item, contentX, itemY, contentW - 10, cardH, (int) mouseX, (int) mouseY, event.button());
                    return true;
                }
                itemY += cardH + 4;
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingSliderItem = null;
        isDraggingWheel = false;
        isDraggingVal = false;
        ConfigCustomWidgets.isCrosshairMouseDown = false;
        if (ConfigCustomWidgets.isDraggingWaypoint) {
            ConfigCustomWidgets.isDraggingWaypoint = false;
            ConfigCustomWidgets.draggedWaypointIndex = -1;
            ConfigCustomWidgets.draggingRouteName = null;
            me.bombo.bomboaddons.OrderedWaypoints.saveToConfig();
        }
        if (ConfigCustomWidgets.isDraggingLore) {
            ConfigCustomWidgets.isDraggingLore = false;
            ConfigCustomWidgets.draggedLoreIndex = -1;
            BomboConfig.save();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (activeColorItem != null) {
            if (isDraggingWheel || isDraggingVal) return true;
        }
        if ("Custom Crosshair".equals(activeCategory)) {
            ConfigCustomWidgets.handleCrosshairClick(0, 0, this.width, this.height, (int) event.x(), (int) event.y(), event.button());
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    private void handleItemClick(ConfigItem item, int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        int ctrlRightX = x + w - 10;
        int ctrlY = y + 10;

        if (item.type == ConfigItem.Type.CUSTOM_CARD && item.customClickHandler != null) {
            item.customClickHandler.onClick(x, y, w, h, mouseX, mouseY, button);
            return;
        }

        switch (item.type) {
            case TOGGLE -> {
                if (button == 1 && item.hudTarget != null) {
                    activeHudStyleTarget = item.hudTarget;
                    activeHudStyleTab = 0;
                    activeColorItem = null;
                    activeDropdownItem = null;
                    return;
                }
                // Check if [❖] Move icon button was clicked
                if (item.hudTarget != null) {
                    int moveBtnX = ctrlRightX - 34 - 30;
                    if (mouseX >= moveBtnX && mouseX <= moveBtnX + 24 && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                        Minecraft.getInstance().setScreenAndShow(new HudMoveScreen(item.hudTarget));
                        return;
                    }
                }
                if (item.boolSetter != null && item.boolGetter != null) {
                    boolean nextVal = !item.boolGetter.get();
                    item.boolSetter.accept(nextVal);
                    BomboConfig.save();
                }
            }

            case SLIDER_INT -> {
                if (button == 1) { // Right click -> reset to default
                    if (item.intSetter != null) {
                        item.intSetter.accept(item.defaultInt);
                        BomboConfig.save();
                    }
                    return;
                }
                // Numeric label -> direct text entry.
                if (sliderLabelHit(ctrlRightX, ctrlY, mouseX, mouseY)) {
                    beginSliderValueEdit(item, String.valueOf(item.intGetter != null ? item.intGetter.get() : 0));
                    return;
                }
                int trackW = 100;
                int trackX = ctrlRightX - 165;
                this.selectedSliderItem = item;

                // Click on track bar or card to adjust
                if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= ctrlY - 2 && mouseY <= ctrlY + 20) {
                    float pct = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - trackX) / trackW));
                    int min = item.minInt;
                    int max = item.maxInt;
                    int step = item.stepInt > 0 ? item.stepInt : 1;
                    int rawVal = Math.round(min + pct * (max - min));
                    int steppedVal = min + Math.round((float) (rawVal - min) / step) * step;
                    steppedVal = Math.max(min, Math.min(max, steppedVal));
                    if (item.intSetter != null) {
                        item.intSetter.accept(steppedVal);
                        BomboConfig.save();
                    }
                    this.draggingSliderItem = item;
                    this.dragTrackX = trackX;
                    this.dragTrackW = trackW;
                }
            }

            case SLIDER_COINS -> {
                if (button == 1) { // Right click -> reset to default
                    if (item.longSetter != null) {
                        item.longSetter.accept(item.defaultLong);
                        BomboConfig.save();
                    }
                    return;
                }
                if (sliderLabelHit(ctrlRightX, ctrlY, mouseX, mouseY)) {
                    beginSliderValueEdit(item, String.valueOf(item.longGetter != null ? item.longGetter.get() : 0L));
                    return;
                }
                int trackW = 100;
                int trackX = ctrlRightX - 165;
                this.selectedSliderItem = item;

                if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= ctrlY - 2 && mouseY <= ctrlY + 20) {
                    float pct = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - trackX) / trackW));
                    long min = item.minLong;
                    long max = item.maxLong;
                    long step = item.stepLong > 0L ? item.stepLong : 1L;
                    long rawVal = Math.round(min + (double) pct * (max - min));
                    long steppedVal = min + Math.round((double) (rawVal - min) / step) * step;
                    steppedVal = Math.max(min, Math.min(max, steppedVal));
                    if (item.longSetter != null) {
                        item.longSetter.accept(steppedVal);
                        BomboConfig.save();
                    }
                    this.draggingSliderItem = item;
                    this.dragTrackX = trackX;
                    this.dragTrackW = trackW;
                }
            }

            case SLIDER_FLOAT -> {
                if (button == 1) { // Right click -> reset to default
                    if (item.floatSetter != null) {
                        item.floatSetter.accept(item.defaultFloat);
                        BomboConfig.save();
                    }
                    return;
                }
                // Numeric label -> direct text entry.
                if (sliderLabelHit(ctrlRightX, ctrlY, mouseX, mouseY)) {
                    beginSliderValueEdit(item, String.format(java.util.Locale.US, "%.2f", item.floatGetter != null ? item.floatGetter.get() : 0.0F));
                    return;
                }
                int trackW = 100;
                int trackX = ctrlRightX - 165;
                this.selectedSliderItem = item;

                // Click on track bar or card to adjust
                if (mouseX >= trackX && mouseX <= trackX + trackW && mouseY >= ctrlY - 2 && mouseY <= ctrlY + 20) {
                    float pct = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - trackX) / trackW));
                    float min = item.minFloat;
                    float max = item.maxFloat;
                    float step = item.stepFloat > 0f ? item.stepFloat : 0.01f;
                    float rawVal = min + pct * (max - min);
                    float steppedVal = min + Math.round((rawVal - min) / step) * step;
                    steppedVal = Math.max(min, Math.min(max, steppedVal));
                    if (item.floatSetter != null) {
                        item.floatSetter.accept(steppedVal);
                        BomboConfig.save();
                    }
                    this.draggingSliderItem = item;
                    this.dragTrackX = trackX;
                    this.dragTrackW = trackW;
                }
            }

            case CYCLE -> {
                activeDropdownItem = isSameItem(activeDropdownItem, item) ? null : item;
                dropdownX = ctrlRightX - 110;
                dropdownY = ctrlY + 22;
                dropdownW = 110;
            }

            case COLOR -> {
                openColorPicker(item);
            }

            case KEYBIND -> {
                int modeW = item.boolGetter != null ? 54 : 0;
                int keyW = item.boolGetter != null ? 76 : 85;
                int modeX = ctrlRightX - modeW;
                int keyX = item.boolGetter != null ? (modeX - 6 - keyW) : (ctrlRightX - keyW);

                if (item.boolGetter != null && mouseX >= modeX && mouseX <= modeX + modeW && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                    if (item.boolSetter != null) {
                        item.boolSetter.accept(!item.boolGetter.get());
                        BomboConfig.save();
                    }
                    return;
                }

                if (mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= ctrlY && mouseY <= ctrlY + 18) {
                    long handle = Minecraft.getInstance().getWindow().handle();
                    boolean shiftHeld = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (shiftHeld) {
                        String currentKey = item.stringGetter != null ? item.stringGetter.get() : "";
                        if (isKeyConflict(item, currentKey)) {
                            int targetCode = me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(currentKey);
                            ConfigItem foundItem = null;
                            for (String cat : ConfigRegistry.getAvailableCategories()) {
                                for (ConfigItem ci2 : ConfigRegistry.getItemsForCategory(cat)) {
                                    if (ci2 != item && ci2.type == ConfigItem.Type.KEYBIND && ci2.stringGetter != null) {
                                        if (me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(ci2.stringGetter.get()) == targetCode) {
                                            foundItem = ci2;
                                            break;
                                        }
                                    }
                                }
                                if (foundItem != null) break;
                            }
                            if (foundItem != null) {
                                activeCategory = foundItem.category;
                                searchQuery = "";
                                activeKeybindItem = foundItem;
                                List<ConfigItem> catItems = ConfigRegistry.getItemsForCategory(foundItem.category);
                                int accumulatedY = 0;
                                for (ConfigItem ci2 : catItems) {
                                    if (ci2 == foundItem) break;
                                    accumulatedY += ci2.getEffectiveCardHeight() + 4;
                                }
                                scrollAmount = Math.max(0, accumulatedY - 20);
                                return;
                            } else {
                                String desc = getKeyConflictDescription(item, currentKey);
                                Minecraft mc = Minecraft.getInstance();
                                if (desc != null && desc.startsWith("Vanilla:")) {
                                    String searchKeyName = desc.substring(8).trim();
                                    me.bombo.bomboaddons.BomboConfig.controlsInitialSearchQuery = searchKeyName;
                                    mc.setScreenAndShow(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this, mc.options));
                                    return;
                                }
                                if (mc.player != null) {
                                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bBomboAddons§8] §7Keybind conflicts with: §e" + (desc != null ? desc : "Vanilla Keybind")));
                                }
                            }
                        }
                        return;
                    }
                    ConfigCustomWidgets.activeFocusedField = null;
                    if (searchBox != null) searchBox.setFocused(false);
                    activeTextItem = null;
                    activeKeybindItem = item;
                }
            }

            case BUTTON -> {
                if (item.action != null) item.action.run();
            }

            case SUBMENU -> {
                if (item.submenuOpener != null) {
                    item.submenuOpener.run();
                    Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.BomboConfigGUI(this));
                }
            }

            case TEXT -> {
                activeTextItem = item;
                ConfigCustomWidgets.activeFocusedField = null;
                if (searchBox != null) searchBox.setFocused(false);
                activeKeybindItem = null;
                activeColorItem = null;
                activeDropdownItem = null;
            }

            default -> {}
        }
    }

    private List<ConfigItem> searchMatchingItems() {
        List<ConfigItem> list = new ArrayList<>();
        String q = searchQuery.toLowerCase().trim();
        for (String cat : ConfigRegistry.getAvailableCategories()) {
            List<ConfigItem> catMatches = new ArrayList<>();
            for (ConfigItem item : ConfigRegistry.getItemsForCategory(cat)) {
                if (item.type == ConfigItem.Type.HEADER) continue;
                if ((item.name != null && item.name.toLowerCase().contains(q))
                        || (item.description != null && item.description.toLowerCase().contains(q))) {
                    catMatches.add(item);
                }
            }
            if (!catMatches.isEmpty()) {
                list.add(ConfigItem.header("-------------- " + cat + " --------------", cat));
                list.addAll(catMatches);
            }
        }
        return list;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if ("Dev".equalsIgnoreCase(activeCategory) && FeatureOrganizerManager.dropdownOpen) {
            FeatureOrganizerManager.handleScrolled(verticalAmount);
            return true;
        }
        if (activeColorItem != null || activeDropdownItem != null) return true;

        if ("Dev".equalsIgnoreCase(activeCategory) && FeatureOrganizerManager.isMouseOverCard(mouseX, mouseY)) {
            FeatureOrganizerManager.handleScrolled(verticalAmount);
            return true;
        }

        int sideX = winX;
        if (mouseX >= sideX && mouseX <= sideX + sidebarW) {
            this.sidebarScrollAmount -= verticalAmount * 20.0;
            if (this.sidebarScrollAmount < 0) this.sidebarScrollAmount = 0;
            if (this.sidebarScrollAmount > this.maxSidebarScroll) this.sidebarScrollAmount = this.maxSidebarScroll;
            return true;
        } else {
            this.scrollAmount -= verticalAmount * 24.0;
            if (this.scrollAmount < 0) this.scrollAmount = 0;
            if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
            return true;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (handleSliderValueChar((char) event.codepoint())) {
            return true;
        }
        if (activeColorItem != null && colorPickerHexFocused) {
            char c = (char) event.codepoint();
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                if (colorPickerHexInput.length() < 6) {
                    colorPickerHexInput += Character.toUpperCase(c);
                    try {
                        int rgb = parseColorRgb(colorPickerHexInput);
                        float[] hsv = new float[3];
                        java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
                        colorPickerHue = hsv[0];
                        colorPickerSat = hsv[1];
                        colorPickerVal = hsv[2];
                        if (activeColorItem.stringSetter != null) {
                            activeColorItem.stringSetter.accept("#" + colorPickerHexInput);
                            BomboConfig.save();
                        }
                    } catch (Throwable ignored) {}
                    return true;
                }
            }
        }
        if (activeTextItem != null && activeTextItem.stringSetter != null) {
            char c = (char) event.codepoint();
            if (c >= 32 && c != 127) {
                String cur = activeTextItem.stringGetter != null ? activeTextItem.stringGetter.get() : "";
                if (cur == null) cur = "";
                if (activeTextCursor < 0 || activeTextCursor > cur.length()) {
                    activeTextCursor = cur.length();
                }
                if (cur.length() < 512) {
                    String next = cur.substring(0, activeTextCursor) + c + cur.substring(activeTextCursor);
                    activeTextCursor++;
                    activeTextItem.stringSetter.accept(next);
                    BomboConfig.save();
                }
                return true;
            }
        }
        if (FeatureOrganizerManager.handleChar((char) event.codepoint())) {
            return true;
        }
        if (ConfigCustomWidgets.handleCharTyped((char) event.codepoint())) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (handleSliderValueKey(event)) {
            return true;
        }
        if (activeKeybindItem != null) {
            int keyCode = event.key();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (activeKeybindItem.stringSetter != null) {
                    activeKeybindItem.stringSetter.accept("");
                    BomboConfig.save();
                }
                activeKeybindItem = null;
                ConfigCustomWidgets.capturePrefixOpen = false;
                return true;
            }

            // Shared capture state machine: a modifier opens a combo (Ctrl+A, Alt+A) instead of
            // committing itself the instant it is pressed.
            String captured = ConfigCustomWidgets.captureKeyStep(keyCode, "");
            if (ConfigCustomWidgets.captureStillListening) {
                return true; // combo open, waiting for the main key
            }
            if (captured != null) {
                if (activeKeybindItem.stringSetter != null && !captured.isEmpty()) {
                    activeKeybindItem.stringSetter.accept(captured);
                    BomboConfig.save();
                }
                activeKeybindItem = null;
                return true;
            }
            return true;
        }

        if (FeatureOrganizerManager.handleKey(event.key(), event.scancode(), event.modifiers())) {
            return true;
        }
        if (activeColorItem != null && colorPickerHexFocused) {
            int code = event.key();
            long handle = Minecraft.getInstance().getWindow().handle();
            boolean isCtrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

            if (isCtrl && code == GLFW.GLFW_KEY_C) {
                // Copy hex
                try {
                    Minecraft.getInstance().keyboardHandler.setClipboard("#" + colorPickerHexInput);
                } catch (Throwable ignored) {}
                return true;
            } else if (isCtrl && code == GLFW.GLFW_KEY_X) {
                // Cut hex
                try {
                    Minecraft.getInstance().keyboardHandler.setClipboard("#" + colorPickerHexInput);
                    colorPickerHexInput = "";
                } catch (Throwable ignored) {}
                return true;
            } else if (isCtrl && code == GLFW.GLFW_KEY_V) {
                // Paste hex
                try {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.trim().isEmpty()) {
                        String clean = clip.trim().replace("#", "").toUpperCase();
                        if (clean.length() > 6) clean = clean.substring(0, 6);
                        colorPickerHexInput = clean;
                        int rgb = parseColorRgb(colorPickerHexInput);
                        float[] hsv = new float[3];
                        java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
                        colorPickerHue = hsv[0];
                        colorPickerSat = hsv[1];
                        colorPickerVal = hsv[2];
                        if (activeColorItem.stringSetter != null) {
                            activeColorItem.stringSetter.accept("#" + colorPickerHexInput);
                            BomboConfig.save();
                        }
                    }
                } catch (Throwable ignored) {}
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE || code == GLFW.GLFW_KEY_DELETE) {
                if (!colorPickerHexInput.isEmpty()) {
                    colorPickerHexInput = colorPickerHexInput.substring(0, colorPickerHexInput.length() - 1);
                    if (!colorPickerHexInput.isEmpty()) {
                        try {
                            int rgb = parseColorRgb(colorPickerHexInput);
                            float[] hsv = new float[3];
                            java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsv);
                            colorPickerHue = hsv[0];
                            colorPickerSat = hsv[1];
                            colorPickerVal = hsv[2];
                            if (activeColorItem.stringSetter != null) {
                                activeColorItem.stringSetter.accept("#" + colorPickerHexInput);
                                BomboConfig.save();
                            }
                        } catch (Throwable ignored) {}
                    }
                    return true;
                }
            } else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_ESCAPE) {
                colorPickerHexFocused = false;
                return true;
            }
        }
        if (activeTextItem != null && activeTextItem.stringSetter != null) {
            int code = event.key();
            long handle = Minecraft.getInstance().getWindow().handle();
            boolean isCtrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            String cur = activeTextItem.stringGetter != null ? activeTextItem.stringGetter.get() : "";
            if (cur == null) cur = "";
            if (activeTextCursor < 0 || activeTextCursor > cur.length()) {
                activeTextCursor = cur.length();
            }

            if (code == GLFW.GLFW_KEY_LEFT) {
                if (isCtrl) {
                    int pos = activeTextCursor - 1;
                    while (pos > 0 && cur.charAt(pos) == ' ') pos--;
                    while (pos > 0 && cur.charAt(pos - 1) != ' ') pos--;
                    activeTextCursor = Math.max(0, pos);
                } else {
                    activeTextCursor = Math.max(0, activeTextCursor - 1);
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                if (isCtrl) {
                    int pos = activeTextCursor;
                    while (pos < cur.length() && cur.charAt(pos) != ' ') pos++;
                    while (pos < cur.length() && cur.charAt(pos) == ' ') pos++;
                    activeTextCursor = Math.min(cur.length(), pos);
                } else {
                    activeTextCursor = Math.min(cur.length(), activeTextCursor + 1);
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                activeTextCursor = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                activeTextCursor = cur.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (isCtrl) {
                    activeTextItem.stringSetter.accept("");
                    activeTextCursor = 0;
                    BomboConfig.save();
                } else if (activeTextCursor > 0 && !cur.isEmpty()) {
                    String next = cur.substring(0, activeTextCursor - 1) + cur.substring(activeTextCursor);
                    activeTextCursor--;
                    activeTextItem.stringSetter.accept(next);
                    BomboConfig.save();
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (activeTextCursor < cur.length()) {
                    String next = cur.substring(0, activeTextCursor) + cur.substring(activeTextCursor + 1);
                    activeTextItem.stringSetter.accept(next);
                    BomboConfig.save();
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_ESCAPE) {
                activeTextItem = null;
                return true;
            } else if (isCtrl && code == GLFW.GLFW_KEY_V) {
                try {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        String cleanClip = clip.trim();
                        String next = cur.substring(0, activeTextCursor) + cleanClip + cur.substring(activeTextCursor);
                        activeTextCursor += cleanClip.length();
                        activeTextItem.stringSetter.accept(next);
                        BomboConfig.save();
                    }
                } catch (Exception ignored) {}
                return true;
            } else if (isCtrl && code == GLFW.GLFW_KEY_C) {
                try {
                    Minecraft.getInstance().keyboardHandler.setClipboard(cur);
                } catch (Exception ignored) {}
                return true;
            }
            return true;
        }
        long handle = Minecraft.getInstance().getWindow().handle();
        boolean isCtrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        // Ctrl + Z Undo for highlights and widgets (prioritize lightweight stack for zero lag)
        if (isCtrl && event.key() == GLFW.GLFW_KEY_Z) {
            if (!ConfigCustomWidgets.undoStack.isEmpty()) {
                ConfigCustomWidgets.undoStack.pop().run();
                BomboConfig.save();
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3Bombo§8]§r §aUndid last action!"));
                }
                return true;
            }
            if (me.bombo.bomboaddons.BomboConfigGUI.undoHighlight()) {
                BomboConfig.save();
                return true;
            }
        }

        if (ConfigCustomWidgets.handleKeyPressed(event.key())) {
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (activeDropdownItem != null) {
                activeDropdownItem = null;
                return true;
            }
            BomboConfig.save();
            this.minecraft.setScreenAndShow(this.parent);
            return true;
        }

        // Arrow keys / Keypad precision stepping for sliders
        ConfigItem targetSlider = this.hoveredSliderItem != null ? this.hoveredSliderItem : this.selectedSliderItem;
        if (targetSlider != null) {
            int k = event.key();
            boolean isLeft = (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_DOWN || k == GLFW.GLFW_KEY_KP_4 || k == GLFW.GLFW_KEY_KP_2);
            boolean isRight = (k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_UP || k == GLFW.GLFW_KEY_KP_6 || k == GLFW.GLFW_KEY_KP_8);

            if (isLeft || isRight) {
                int dir = isLeft ? -1 : +1;
                if (targetSlider.type == ConfigItem.Type.SLIDER_INT && targetSlider.intGetter != null && targetSlider.intSetter != null) {
                    int cur = targetSlider.intGetter.get();
                    int step = targetSlider.stepInt > 0 ? targetSlider.stepInt : 1;
                    int next = Math.max(targetSlider.minInt, Math.min(targetSlider.maxInt, cur + dir * step));
                    targetSlider.intSetter.accept(next);
                    BomboConfig.save();
                    return true;
                } else if (targetSlider.type == ConfigItem.Type.SLIDER_FLOAT && targetSlider.floatGetter != null && targetSlider.floatSetter != null) {
                    float cur = targetSlider.floatGetter.get();
                    float step = targetSlider.stepFloat > 0f ? targetSlider.stepFloat : 0.05f;
                    float next = Math.max(targetSlider.minFloat, Math.min(targetSlider.maxFloat, cur + dir * step));
                    targetSlider.floatSetter.accept(next);
                    BomboConfig.save();
                    return true;
                }
            }
        }

        return super.keyPressed(event);
    }

    public enum KeyScope {
        GLOBAL,
        CONTAINER_GENERIC,
        WARDROBE,
        PETS,
        PROFILES
    }

    public static KeyScope getItemScope(ConfigItem item) {
        if (item == null) return KeyScope.GLOBAL;
        String name = item.name != null ? item.name : "";
        String cat = item.category != null ? item.category : "";

        if ("Wardrobe".equalsIgnoreCase(cat) || name.toLowerCase().contains("wardrobe") || name.toLowerCase().contains("loadout")) {
            return KeyScope.WARDROBE;
        }
        if ("Pets".equalsIgnoreCase(cat) || name.toLowerCase().contains("pet")) {
            return KeyScope.PETS;
        }
        if ("Profiles".equalsIgnoreCase(cat) || name.toLowerCase().contains("profile")) {
            return KeyScope.PROFILES;
        }
        if (GUI_EXCLUSIVE_HOTKEY_NAMES.contains(name) || "Inventory".equalsIgnoreCase(cat) || name.startsWith("Slot ")) {
            return KeyScope.CONTAINER_GENERIC;
        }
        return KeyScope.GLOBAL;
    }

    private static final Set<String> GUI_EXCLUSIVE_HOTKEY_NAMES = Set.of(
        "Trade Hotkey", "Trade Max Pet Hotkey", "Recipe Viewer", "Usage Viewer", "Texture Toggle",
        "Show Item Info", "Count Items", "Copy Item NBT", "Get From Sacks (Max)",
        "Get From Sacks (Stack)", "Bestiary Highlight", "Blocked Slots Bypass",
        "Next Menu Page", "Previous Menu Page", "Go Back", "Smart Back",
        "Save Inventory Layout", "Save Inventory", "Save Layout", "Anvil Auto Combine"
    );

    public static boolean isGuiExclusiveItem(ConfigItem item) {
        return getItemScope(item) != KeyScope.GLOBAL;
    }

    public static boolean isKeyConflict(ConfigItem item, String keyStr) {
        if (keyStr == null || keyStr.trim().isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        int targetCode = me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(keyStr);
        if (targetCode <= 0) return false;

        KeyScope scope = getItemScope(item);

        int occurrences = 0;
        // Vanilla keybindings only conflict with GLOBAL scope keys
        if (scope == KeyScope.GLOBAL && mc.options != null && mc.options.keyMappings != null) {
            for (net.minecraft.client.KeyMapping km : mc.options.keyMappings) {
                if (km != null) {
                    String name = km.getName();
                    if (name.equals("key.saveToolbarActivator") || name.equals("key.loadToolbarActivator") || name.equals("key.spectatorOutlines")) {
                        continue;
                    }
                    try {
                        for (java.lang.reflect.Field f : km.getClass().getDeclaredFields()) {
                            if (com.mojang.blaze3d.platform.InputConstants.Key.class.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                com.mojang.blaze3d.platform.InputConstants.Key k = (com.mojang.blaze3d.platform.InputConstants.Key) f.get(km);
                                if (k != null && k.getValue() == targetCode) {
                                    occurrences++;
                                    if (occurrences >= 1) return true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // Check against other mod settings with the same scope
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            List<String> keysToCheck = new ArrayList<>();
            if (scope == KeyScope.GLOBAL) {
                keysToCheck.add(s.priceHistoryKey);
                keysToCheck.add(s.chatPeekKey);
                keysToCheck.add(s.clipboardRunKey);
                keysToCheck.add(s.clipboardRunLastCommandKey);
                keysToCheck.add(s.vanillaToggleCrouchKey);
                keysToCheck.add(s.vanillaToggleAttackKey);
                keysToCheck.add(s.vanillaToggleUseKey);
                keysToCheck.add(s.freelookKey);
                keysToCheck.add(s.freecamKey);
                keysToCheck.add(s.backpackPreviewKey);
            } else if (scope == KeyScope.CONTAINER_GENERIC) {
                keysToCheck.add(s.tradeKey);
                keysToCheck.add(s.tradeMaxKey);
                keysToCheck.add(s.countItemKey);
                keysToCheck.add(s.recipeKey);
                keysToCheck.add(s.usageKey);
                keysToCheck.add(s.showItemKey);
                keysToCheck.add(s.copyNbtKey);
                keysToCheck.add(s.gfsMaxKey);
                keysToCheck.add(s.gfsStackKey);
                keysToCheck.add(s.nextPageKey);
                keysToCheck.add(s.prevPageKey);
                keysToCheck.add(s.goBackKey);
                keysToCheck.add(s.textureToggleKey);
                keysToCheck.add(s.bestiaryHighlightKey);
                keysToCheck.add(s.blockedSlotsBypassKey);
                keysToCheck.add(s.saveInventoryKey);
                keysToCheck.add(s.anvilAutoCombineKey);
            } else if (scope == KeyScope.WARDROBE) {
                if (s.wardrobeKeys != null) {
                    for (int i = 0; i < Math.min(12, s.wardrobeKeys.size()); i++) {
                        keysToCheck.add(s.wardrobeKeys.get(i));
                    }
                }
            } else if (scope == KeyScope.PETS) {
                if (s.petKeys != null) {
                    for (int i = 0; i < Math.min(9, s.petKeys.size()); i++) {
                        keysToCheck.add(s.petKeys.get(i));
                    }
                }
                keysToCheck.add(s.savePetKey);
            }

            for (String k : keysToCheck) {
                if (k != null && !k.isEmpty() && me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(k) == targetCode) {
                    occurrences++;
                    if (occurrences >= 2) return true;
                }
            }
        }
        return false;
    }

    public static String getKeyConflictDescription(ConfigItem item, String keyStr) {
        if (keyStr == null || keyStr.trim().isEmpty()) return null;
        Minecraft mc = Minecraft.getInstance();
        int targetCode = me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(keyStr);
        if (targetCode <= 0) return null;

        KeyScope scope = getItemScope(item);

        // Check vanilla keybindings
        if (scope == KeyScope.GLOBAL && mc.options != null && mc.options.keyMappings != null) {
            for (net.minecraft.client.KeyMapping km : mc.options.keyMappings) {
                if (km != null) {
                    String name = km.getName();
                    if (name.equals("key.saveToolbarActivator") || name.equals("key.loadToolbarActivator") || name.equals("key.spectatorOutlines")) {
                        continue;
                    }
                    try {
                        for (java.lang.reflect.Field f : km.getClass().getDeclaredFields()) {
                            if (com.mojang.blaze3d.platform.InputConstants.Key.class.isAssignableFrom(f.getType())) {
                                f.setAccessible(true);
                                com.mojang.blaze3d.platform.InputConstants.Key k = (com.mojang.blaze3d.platform.InputConstants.Key) f.get(km);
                                if (k != null && k.getValue() == targetCode) {
                                    if (name.startsWith("key.")) name = name.substring(4);
                                    return "Vanilla: " + name.replace('.', ' ');
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        // Check against other mod settings in same scope
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            Map<String, String> keyNames = new LinkedHashMap<>();
            if (scope == KeyScope.GLOBAL) {
                keyNames.put("Price History Graph", s.priceHistoryKey);
                keyNames.put("Chat Peek", s.chatPeekKey);
                keyNames.put("Run Copied", s.clipboardRunKey);
                keyNames.put("Run Last Copied Command", s.clipboardRunLastCommandKey);
                keyNames.put("Vanilla Sneak Toggle", s.vanillaToggleCrouchKey);
                keyNames.put("Vanilla Attack Toggle", s.vanillaToggleAttackKey);
                keyNames.put("Vanilla Use Toggle", s.vanillaToggleUseKey);
                keyNames.put("Freelook", s.freelookKey);
                keyNames.put("Freecam", s.freecamKey);
                keyNames.put("Backpack Preview", s.backpackPreviewKey);
            } else if (scope == KeyScope.CONTAINER_GENERIC) {
                keyNames.put("Trade", s.tradeKey);
                keyNames.put("Trade Max Pet", s.tradeMaxKey);
                keyNames.put("Count Items", s.countItemKey);
                keyNames.put("Recipe Viewer", s.recipeKey);
                keyNames.put("Usage Viewer", s.usageKey);
                keyNames.put("Show Item Info", s.showItemKey);
                keyNames.put("Copy NBT", s.copyNbtKey);
                keyNames.put("GFS Max", s.gfsMaxKey);
                keyNames.put("GFS Stack", s.gfsStackKey);
                keyNames.put("Next Page", s.nextPageKey);
                keyNames.put("Prev Page", s.prevPageKey);
                keyNames.put("Go Back", s.goBackKey);
                keyNames.put("Texture Toggle", s.textureToggleKey);
                keyNames.put("Bestiary Highlight", s.bestiaryHighlightKey);
                keyNames.put("Save Inventory", s.saveInventoryKey);
                keyNames.put("Blocked Slots Bypass", s.blockedSlotsBypassKey);
                keyNames.put("Anvil Auto Combine", s.anvilAutoCombineKey);
            } else if (scope == KeyScope.WARDROBE) {
                if (s.wardrobeKeys != null) {
                    for (int i = 0; i < Math.min(12, s.wardrobeKeys.size()); i++) {
                        keyNames.put("Wardrobe Slot " + (i + 1), s.wardrobeKeys.get(i));
                    }
                }
            } else if (scope == KeyScope.PETS) {
                if (s.petKeys != null) {
                    for (int i = 0; i < Math.min(9, s.petKeys.size()); i++) {
                        keyNames.put("Pet Slot " + (i + 1), s.petKeys.get(i));
                    }
                }
                keyNames.put("Save Pet Preset", s.savePetKey);
            }

            for (Map.Entry<String, String> entry : keyNames.entrySet()) {
                if (item != null && entry.getKey().equalsIgnoreCase(item.name)) continue;
                String k = entry.getValue();
                if (k != null && !k.isEmpty() && me.bombo.bomboaddons.CustomBindsProcessor.getGlfwCodeForName(k) == targetCode) {
                    return "Setting: " + entry.getKey();
                }
            }
        }
        return null;
    }

    public static boolean isKeyConflict(String keyStr) {
        return isKeyConflict(null, keyStr);
    }
}
