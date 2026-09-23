package me.bombo.bomboaddons.gui.config;

import me.bombo.bomboaddons.*;
import me.bombo.bomboaddons.features.BestiaryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;

public class ConfigCustomWidgets {
    // Active focused text input field ID
    public static String activeFocusedField = null;

    // Crosshair Mouse Drag State
    public static boolean isCrosshairMouseDown = false;
    public static int crosshairMouseButton = 0;

    // Universal Deployable Color Dropdown Picker
    public static Consumer<String> activeColorDropdownSetter = null;
    public static int colorDropdownX = -1;
    public static int colorDropdownY = -1;
    public static int colorDropdownW = 110;

    // Lore Additions Drag State
    public static int draggedLoreIndex = -1;
    public static double dragMouseOffsetY = 0;
    public static boolean isDraggingLore = false;

    // Crosshair Presets helper
    public static final String[] CROSSHAIR_PRESET_NAMES = new String[] {
            "Dot", "Plus", "Lg Plus", "Sm Plus", "Circle",
            "Op Circle", "Square", "F Square", "Target", "F Target",
            "Arrow", "Cross", "Sm Cross", "T Shape", "Caret", "Hash", "Clear"
    };

    public static boolean[] getPresetGrid(String name) {
        int idx = switch (name) {
            case "Plus" -> 0;
            case "Sm Plus" -> 1;
            case "Arrow" -> 2;
            case "Dot" -> 3;
            case "Circle" -> 4;
            case "Op Circle" -> 5;
            case "Lg Plus" -> 6;
            case "Caret" -> 7;
            case "Square" -> 8;
            case "F Square" -> 9;
            case "Target" -> 10;
            case "F Target" -> 11;
            case "Hash" -> 12;
            case "Cross" -> 13;
            case "Sm Cross" -> 14;
            case "T Shape" -> 15;
            default -> -1;
        };
        if (idx >= 0 && idx < CrosshairPresets.PRESETS.length) {
            return CrosshairPresets.PRESETS[idx];
        }
        return new boolean[225];
    }

    // ---------------------------------------------------------------------------------------------
    // 1. CUSTOM CROSSHAIR DESIGNER CARD
    // ---------------------------------------------------------------------------------------------
    public static void renderCrosshairDesigner(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        BomboConfig.CrosshairSettings crosshair = s.customCrosshair;
        if (crosshair.grid == null || crosshair.grid.length != 225) {
            crosshair.grid = new boolean[225];
        }

        ConfigUITheme.drawCard(g, x, y, w, h, false);

        // Header Title
        g.text(font, "§e§lInteractive Crosshair Canvas", x + 12, y + 10, -1, false);
        g.text(font, "§7Click or drag: §aLeft-Click = Draw §8| §cRight-Click = Erase", x + 12, y + 22, 0xFF94A3B8, false);

        // Presets Button Grid (3 rows of buttons)
        int pStartX = x + 12;
        int pCurY = y + 36;
        int btnW = (w - 40) / 6;
        int btnH = 16;

        for (int i = 0; i < CROSSHAIR_PRESET_NAMES.length; i++) {
            String name = CROSSHAIR_PRESET_NAMES[i];
            int col = i % 6;
            int row = i / 6;
            int bx = pStartX + col * (btnW + 4);
            int by = pCurY + row * (btnH + 3);

            boolean hover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH;
            ConfigUITheme.drawPillButton(g, font, name, bx, by, btnW, btnH, hover, hover ? 0xFFFFFFFF : 0xFFCBD5E1, hover ? 0x4400E5FF : 0x22FFFFFF, hover ? 0x6600E5FF : 0x33FFFFFF);
        }

        // 15x15 Drawing Canvas Grid
        int gridStartY = pCurY + 3 * (btnH + 3) + 10;
        int gridSize = 10;
        int gridStartX = x + 24;

        g.text(font, "§615x15 Pixel Grid Editor:", gridStartX, gridStartY - 10, -1, false);

        // Continuous drag drawing while holding mouse button
        if (isCrosshairMouseDown && mouseX >= gridStartX && mouseX < gridStartX + 15 * gridSize && mouseY >= gridStartY && mouseY < gridStartY + 15 * gridSize) {
            int col = (mouseX - gridStartX) / gridSize;
            int row = (mouseY - gridStartY) / gridSize;
            int idx = row * 15 + col;
            if (idx >= 0 && idx < 225) {
                crosshair.grid[idx] = (crosshairMouseButton == 0);
            }
        }

        // Canvas backdrop card
        g.fill(gridStartX - 3, gridStartY - 3, gridStartX + 15 * gridSize + 3, gridStartY + 15 * gridSize + 3, 0xDD0D1117);
        g.outline(gridStartX - 3, gridStartY - 3, 15 * gridSize + 6, 15 * gridSize + 6, ConfigUITheme.getAccentColor());

        int crosshairColor = CrosshairRenderer.getColorValue(crosshair.color, crosshair.chroma);

        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                int idx = r * 15 + c;
                int px = gridStartX + c * gridSize;
                int py = gridStartY + r * gridSize;
                boolean isPixelOn = crosshair.grid[idx];
                boolean isCenter = (r == 7 && c == 7);
                boolean hover = mouseX >= px && mouseX < px + gridSize && mouseY >= py && mouseY < py + gridSize;

                // Background / grid line
                int cellBg = isPixelOn ? crosshairColor : (isCenter ? 0x44FFFFFF : (hover ? 0x22FFFFFF : 0x111E293B));
                g.fill(px, py, px + gridSize - 1, py + gridSize - 1, cellBg);

                // Outline around pixels if outline is enabled
                if (isPixelOn && crosshair.outline) {
                    g.outline(px, py, gridSize - 1, gridSize - 1, 0x88000000);
                } else if (hover) {
                    g.outline(px, py, gridSize - 1, gridSize - 1, 0x88FFFFFF);
                } else if (isCenter && !isPixelOn) {
                    g.outline(px, py, gridSize - 1, gridSize - 1, 0x4400E5FF);
                }
            }
        }

        // Live In-Game Preview Window
        int prevX = gridStartX + 15 * gridSize + 35;
        int prevY = gridStartY + 10;
        int prevW = 120;
        int prevH = 120;

        g.text(font, "§bLive Crosshair Preview:", prevX, prevY - 12, -1, false);
        g.fill(prevX, prevY, prevX + prevW, prevY + prevH, 0xDD11141F);
        g.outline(prevX, prevY, prevW, prevH, 0x44FFFFFF);

        // Center crosshair marker in preview
        int pcX = prevX + prevW / 2;
        int pcY = prevY + prevH / 2;
        g.fill(pcX - 15, pcY, pcX + 16, pcY + 1, 0x22FFFFFF);
        g.fill(pcX, pcY - 15, pcX + 1, pcY + 16, 0x22FFFFFF);

        // Render scaled custom crosshair inside preview
        float pScale = 2.5f;
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                int idx = r * 15 + c;
                if (crosshair.grid[idx]) {
                    float px = (c - 7) * pScale;
                    float py = (r - 7) * pScale;
                    int x1 = (int) (pcX + px);
                    int y1 = (int) (pcY + py);
                    int x2 = (int) (pcX + px + pScale);
                    int y2 = (int) (pcY + py + pScale);
                    if (crosshair.outline) {
                        int ox = Math.max(1, (int) (pScale * 0.25f));
                        g.fill(x1 - ox, y1 - ox, x2 + ox, y2 + ox, 0xFF000000);
                    }
                    g.fill(x1, y1, x2, y2, crosshairColor);
                }
            }
        }

        // Clear button
        int clearX = prevX + 10;
        int clearY = prevY + prevH + 12;
        boolean clearHover = mouseX >= clearX && mouseX <= clearX + prevW - 20 && mouseY >= clearY && mouseY <= clearY + 18;
        ConfigUITheme.drawPillButton(g, font, "§cClear Grid", clearX, clearY, prevW - 20, 18, clearHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);
    }

    public static boolean handleCrosshairClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        BomboConfig.CrosshairSettings crosshair = s.customCrosshair;
        if (crosshair.grid == null || crosshair.grid.length != 225) {
            crosshair.grid = new boolean[225];
        }

        isCrosshairMouseDown = true;
        crosshairMouseButton = button;

        // Presets click check
        int pStartX = x + 12;
        int pCurY = y + 36;
        int btnW = (w - 40) / 6;
        int btnH = 16;

        for (int i = 0; i < CROSSHAIR_PRESET_NAMES.length; i++) {
            String name = CROSSHAIR_PRESET_NAMES[i];
            int col = i % 6;
            int row = i / 6;
            int bx = pStartX + col * (btnW + 4);
            int by = pCurY + row * (btnH + 3);

            if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH) {
                if ("Clear".equalsIgnoreCase(name)) {
                    crosshair.grid = new boolean[225];
                } else {
                    boolean[] preset = getPresetGrid(name);
                    System.arraycopy(preset, 0, crosshair.grid, 0, 225);
                }
                BomboConfig.save();
                return true;
            }
        }

        // Grid canvas click check
        int gridStartY = pCurY + 3 * (btnH + 3) + 10;
        int gridSize = 10;
        int gridStartX = x + 24;

        if (mouseX >= gridStartX && mouseX < gridStartX + 15 * gridSize && mouseY >= gridStartY && mouseY < gridStartY + 15 * gridSize) {
            int col = (mouseX - gridStartX) / gridSize;
            int row = (mouseY - gridStartY) / gridSize;
            int idx = row * 15 + col;
            if (idx >= 0 && idx < 225) {
                crosshair.grid[idx] = (button == 0); // Left click = draw, right click = erase
                BomboConfig.save();
                return true;
            }
        }

        // Clear button click check
        int prevX = gridStartX + 15 * gridSize + 35;
        int prevY = gridStartY + 10;
        int prevW = 120;
        int prevH = 120;
        int clearX = prevX + 10;
        int clearY = prevY + prevH + 12;
        if (mouseX >= clearX && mouseX <= clearX + prevW - 20 && mouseY >= clearY && mouseY <= clearY + 18) {
            crosshair.grid = new boolean[225];
            BomboConfig.save();
            return true;
        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 2. HIGHLIGHTS ADVANCED EDITOR & SEPARATED BESTIARY / CUSTOM ENTRIES
    // ---------------------------------------------------------------------------------------------
    public static String highMobInput = "";
    public static String advItemDisplayInput = "";
    public static String advEntityTypeInput = "";
    public static String advHeadHashInput = "";
    public static String advMobSizeInput = "";
    public static String advArmorPieceInput = "";
    public static String advPlayerNameInput = "";
    public static String highIslandInput = "";
    public static String advVisibilityInput = "ALL";
    public static String highColorInput = "GOLD";
    public static boolean highTracerInput = false;
    public static boolean advShowTitleInput = false;
    public static boolean advPlaySoundInput = false;
    public static String editingHighMob = null;
    public static String highlightSearchQuery = "";

    public static int getHighlightCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        int baseH = s.highlightAdvancedMode ? 185 : 95;
        int rows = 0;

        String filter = highlightSearchQuery.toLowerCase().trim();
        List<String> generalMobs = BestiaryManager.getGeneralHighlights(s.highlights);
        boolean isGenCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("General");
        rows += 1; // General header
        if (!isGenCollapsed) {
            for (String m : generalMobs) {
                if (filter.isEmpty() || m.toLowerCase().contains(filter)) rows++;
            }
        }

        Map<String, List<String>> bestiaryGroups = BestiaryManager.getGroupedBestiary(s.highlights);
        boolean isBestiaryCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("Bestiary");
        rows += 1; // Bestiary master header
        if (!isBestiaryCollapsed) {
            for (Map.Entry<String, List<String>> e : bestiaryGroups.entrySet()) {
                rows += 1; // Subcategory header
                boolean isCatCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains(e.getKey());
                if (!isCatCollapsed) {
                    for (String m : e.getValue()) {
                        if (filter.isEmpty() || m.toLowerCase().contains(filter) || e.getKey().toLowerCase().contains(filter)) rows++;
                    }
                }
            }
        }

        return baseH + 45 + rows * 24 + 60; // Extra padding so nothing overflows bottom bar
    }

    public static void renderHighlightEditor(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int colW = (w - 36) / 2;
        int col1X = x + 12;
        int col2X = x + 12 + colW + 12;
        int curY = y + 10;

        // Mode Switch Header & Quick Undo Note
        String modeText = s.highlightAdvancedMode ? "§bMode: §e[ Advanced Mode ] §7(Click to switch)" : "§bMode: §a[ Basic Mode ] §7(Click to switch)";
        boolean modeHover = mouseX >= col1X && mouseX <= col1X + 220 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, modeText, col1X, curY, 220, 18, modeHover, -1, 0x2200E5FF, 0x4400E5FF);

        g.text(font, "§8Tip: Press §fCtrl+Z§8 to undo changes | Right-click name to toggle", col2X, curY + 5, 0xFF64748B, false);

        curY += 24;

        if (s.highlightAdvancedMode) {
            // Column 1 Clean Inline Placeholders
            renderCleanInputField(g, font, "Nametag / Regex", highMobInput, "highMob", col1X, curY, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Item Display ID / Name", advItemDisplayInput, "advItemDisplay", col1X, curY + 22, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Entity Type (e.g. zombie)", advEntityTypeInput, "advEntityType", col1X, curY + 44, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Head Texture Hash", advHeadHashInput, "advHeadHash", col1X, curY + 66, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Mob Size (e.g. 24, 10+)", advMobSizeInput, "advMobSize", col1X, curY + 88, colW, mouseX, mouseY);

            // Col 1 Toggles
            renderInlineToggle(g, font, "Tracer Line", highTracerInput, col1X, curY + 112, colW / 2 - 4, mouseX, mouseY);
            renderInlineToggle(g, font, "Show Title on Spawn", advShowTitleInput, col1X + colW / 2 + 4, curY + 112, colW / 2 - 4, mouseX, mouseY);

            // Column 2 Clean Inline Placeholders
            renderCleanInputField(g, font, "Armor Piece (e.g. diamond)", advArmorPieceInput, "advArmorPiece", col2X, curY, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Player Name / Skin", advPlayerNameInput, "advPlayerName", col2X, curY + 22, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Island / Subarea", highIslandInput, "highIsland", col2X, curY + 44, colW, mouseX, mouseY);

            // Visibility & Color
            renderCycleSelector(g, font, "Visibility", advVisibilityInput, col2X, curY + 66, colW / 2 - 4, mouseX, mouseY);
            renderColorSelector(g, font, "Color", highColorInput, col2X + colW / 2 + 4, curY + 66, colW / 2 - 4, mouseX, mouseY);

            // Col 2 Toggles
            renderInlineToggle(g, font, "Play Sound on Spawn", advPlaySoundInput, col2X, curY + 88, colW, mouseX, mouseY);

            // Action Buttons
            int btnY = curY + 112;
            String addText = editingHighMob != null ? "§e✔ Save Advanced Highlight" : "§a+ Add Advanced Highlight";
            boolean addHover = mouseX >= col2X && mouseX <= col2X + 160 && mouseY >= btnY && mouseY <= btnY + 18;
            ConfigUITheme.drawPillButton(g, font, addText, col2X, btnY, 160, 18, addHover, -1, addHover ? 0x4410B981 : 0x2210B981, 0x6610B981);

            if (editingHighMob != null) {
                boolean cancelHover = mouseX >= col2X + 165 && mouseX <= col2X + 230 && mouseY >= btnY && mouseY <= btnY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cCancel", col2X + 165, btnY, 65, 18, cancelHover, -1, 0x22EF4444, 0x44EF4444);
            }

            curY += 136;
        } else {
            // Basic Mode Clean Placeholders
            renderCleanInputField(g, font, "Mob Name / Tag", highMobInput, "highMob", col1X, curY, colW, mouseX, mouseY);
            renderCleanInputField(g, font, "Island Filter (e.g. garden)", highIslandInput, "highIsland", col1X, curY + 22, colW, mouseX, mouseY);

            renderColorSelector(g, font, "Color", highColorInput, col2X, curY, colW / 2 - 4, mouseX, mouseY);
            renderInlineToggle(g, font, "Tracer", highTracerInput, col2X + colW / 2 + 4, curY, colW / 2 - 4, mouseX, mouseY);

            int btnY = curY + 22;
            String addText = editingHighMob != null ? "§e✔ Save Highlight" : "§a+ Add Highlight";
            boolean addHover = mouseX >= col2X && mouseX <= col2X + 150 && mouseY >= btnY && mouseY <= btnY + 18;
            ConfigUITheme.drawPillButton(g, font, addText, col2X, btnY, 150, 18, addHover, -1, 0x2210B981, 0x6610B981);

            if (editingHighMob != null) {
                boolean cancelHover = mouseX >= col2X + 155 && mouseX <= col2X + 220 && mouseY >= btnY && mouseY <= btnY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cCancel", col2X + 155, btnY, 65, 18, cancelHover, -1, 0x22EF4444, 0x44EF4444);
            }

            curY += 46;
        }

        // Divider
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        // Search Bar for Highlight Entries
        g.text(font, "§6Highlight Entries:", x + 12, curY + 4, -1, false);
        renderCleanInputField(g, font, "Search entries...", highlightSearchQuery, "highSearch", x + w - 180, curY, 168, mouseX, mouseY);
        curY += 24;

        String filter = highlightSearchQuery.toLowerCase().trim();

        // -----------------------------------------------------------------------------------------
        // SECTION 1: CUSTOM / MANUALLY ADDED HIGHLIGHTS
        // -----------------------------------------------------------------------------------------
        List<String> rawGeneralMobs = BestiaryManager.getGeneralHighlights(s.highlights);
        List<String> generalMobs = new ArrayList<>();
        for (String m : rawGeneralMobs) {
            if (filter.isEmpty() || m.toLowerCase().contains(filter)) {
                generalMobs.add(m);
            }
        }

        boolean isGenCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("General");
        int genHeaderY = curY;
        g.fill(x + 12, genHeaderY, x + w - 12, genHeaderY + 20, 0x441E293B);

        // Collapse Arrow
        boolean genArrowHover = mouseX >= x + 16 && mouseX <= x + 38 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18;
        ConfigUITheme.drawPillButton(g, font, isGenCollapsed ? "▶" : "▼", x + 16, genHeaderY + 2, 22, 16, genArrowHover, 0xFF00E5FF, 0x2200E5FF, 0x4400E5FF);

        g.text(font, "§6§lCustom Highlights §7(" + generalMobs.size() + ")", x + 44, genHeaderY + 6, -1, false);

        // General Header Quick Actions
        int rx = x + w - 18;

        // Delete All Custom
        int delAllX = rx - 26;
        boolean delAllHover = mouseX >= delAllX && mouseX <= delAllX + 26 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18;
        ConfigUITheme.drawPillButton(g, font, "§cDEL", delAllX, genHeaderY + 2, 26, 16, delAllHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

        // Toggle All Custom
        int allToggleX = delAllX - 38;
        boolean anyGenDisabled = generalMobs.stream().anyMatch(m -> s.highlights.containsKey(m) && !s.highlights.get(m).enabled);
        boolean allToggleHover = mouseX >= allToggleX && mouseX <= allToggleX + 36 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18;
        ConfigUITheme.drawPillButton(g, font, "§aALL", allToggleX, genHeaderY + 2, 36, 16, allToggleHover, 0xFF10B981, 0x2210B981, 0x4410B981);

        // Category Tracer Toggle
        int catTrX = allToggleX - 42;
        boolean anyGenTracer = generalMobs.stream().anyMatch(m -> s.highlights.containsKey(m) && s.highlights.get(m).tracer);
        boolean catTrHover = mouseX >= catTrX && mouseX <= catTrX + 40 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18;
        ConfigUITheme.drawPillButton(g, font, anyGenTracer ? "§bTR" : "§7NO TR", catTrX, genHeaderY + 2, 40, 16, catTrHover, anyGenTracer ? 0xFF00E5FF : 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);

        // Category Color Picker Button
        int catColX = catTrX - 62;
        String defaultGenColor = BestiaryManager.getCategoryColor("General");
        boolean catColHover = mouseX >= catColX && mouseX <= catColX + 58 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18;
        ConfigUITheme.drawPillButton(g, font, "§f" + defaultGenColor + " ▾", catColX, genHeaderY + 2, 58, 16, catColHover, -1, 0x22FFFFFF, 0x44FFFFFF);

        curY += 22;

        if (!isGenCollapsed) {
            for (String mobName : generalMobs) {
                BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                if (info == null) continue;
                renderHighlightRow(g, font, mobName, info, x, curY, w, mouseX, mouseY);
                curY += 22;
            }
        }

        curY += 6;

        // -----------------------------------------------------------------------------------------
        // SECTION 2: BESTIARY CATEGORIES (HIERARCHICAL & EXPANDABLE)
        // -----------------------------------------------------------------------------------------
        Map<String, List<String>> rawGroupedBestiary = BestiaryManager.getGroupedBestiary(s.highlights);
        boolean isBestiaryMasterCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("Bestiary");

        int bestiaryHeaderY = curY;
        g.fill(x + 12, bestiaryHeaderY, x + w - 12, bestiaryHeaderY + 20, 0x441E293B);

        boolean bestiaryArrowHover = mouseX >= x + 16 && mouseX <= x + 38 && mouseY >= bestiaryHeaderY + 2 && mouseY <= bestiaryHeaderY + 18;
        ConfigUITheme.drawPillButton(g, font, isBestiaryMasterCollapsed ? "▶" : "▼", x + 16, bestiaryHeaderY + 2, 22, 16, bestiaryArrowHover, 0xFF00E5FF, 0x2200E5FF, 0x4400E5FF);

        g.text(font, "§b§lBestiary Categories", x + 44, bestiaryHeaderY + 6, -1, false);

        curY += 22;

        if (!isBestiaryMasterCollapsed) {
            for (Map.Entry<String, List<String>> entry : rawGroupedBestiary.entrySet()) {
                String cat = entry.getKey();
                List<String> rawMobsInCat = entry.getValue();
                List<String> mobsInCat = new ArrayList<>();
                for (String m : rawMobsInCat) {
                    if (filter.isEmpty() || m.toLowerCase().contains(filter) || cat.toLowerCase().contains(filter)) {
                        mobsInCat.add(m);
                    }
                }

                if (mobsInCat.isEmpty() && !filter.isEmpty()) continue;

                boolean isCatCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains(cat);
                int subHeaderY = curY;
                g.fill(x + 20, subHeaderY, x + w - 12, subHeaderY + 20, 0x330F172A);

                // Subcategory Arrow
                boolean subArrowHover = mouseX >= x + 24 && mouseX <= x + 44 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18;
                ConfigUITheme.drawPillButton(g, font, isCatCollapsed ? "▶" : "▼", x + 24, subHeaderY + 2, 20, 16, subArrowHover, 0xFF38BDF8, 0x2200E5FF, 0x4400E5FF);

                g.text(font, "§e" + cat + " §7(" + mobsInCat.size() + ")", x + 48, subHeaderY + 6, -1, false);

                // Subcategory actions: Color, Tracer, All, Del
                int subRx = x + w - 18;

                // Delete all in subcategory
                int sDelX = subRx - 26;
                boolean sDelHover = mouseX >= sDelX && mouseX <= sDelX + 26 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", sDelX, subHeaderY + 2, 26, 16, sDelHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                // Toggle all in subcategory
                int sAllX = sDelX - 38;
                boolean anySubDisabled = mobsInCat.stream().anyMatch(m -> s.highlights.containsKey(m) && !s.highlights.get(m).enabled);
                boolean sAllHover = mouseX >= sAllX && mouseX <= sAllX + 36 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18;
                ConfigUITheme.drawPillButton(g, font, "§aALL", sAllX, subHeaderY + 2, 36, 16, sAllHover, 0xFF10B981, 0x2210B981, 0x4410B981);

                // Subcategory Tracer
                int sTrX = sAllX - 42;
                boolean catTracer = BestiaryManager.getCategoryTracer(cat);
                boolean sTrHover = mouseX >= sTrX && mouseX <= sTrX + 40 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18;
                ConfigUITheme.drawPillButton(g, font, catTracer ? "§bTR" : "§7NO TR", sTrX, subHeaderY + 2, 40, 16, sTrHover, catTracer ? 0xFF00E5FF : 0xFF94A3B8, 0x22FFFFFF, 0x44FFFFFF);

                // Subcategory Color
                int sColX = sTrX - 62;
                String catColor = BestiaryManager.getCategoryColor(cat);
                boolean sColHover = mouseX >= sColX && mouseX <= sColX + 58 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18;
                ConfigUITheme.drawPillButton(g, font, "§f" + catColor + " ▾", sColX, subHeaderY + 2, 58, 16, sColHover, -1, 0x22FFFFFF, 0x44FFFFFF);

                curY += 22;

                if (!isCatCollapsed) {
                    for (String mobName : mobsInCat) {
                        BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                        if (info == null) continue;
                        renderHighlightRow(g, font, mobName, info, x + 8, curY, w - 8, mouseX, mouseY);
                        curY += 22;
                    }
                }
            }
        }
    }

    public static int getColorVal(String colorName) {
        return 0xFF000000 | (SlotHighlight.getFormattingColor(colorName) & 0x00FFFFFF);
    }

    private static void renderHighlightRow(GuiGraphicsExtractor g, Font font, String mobName, BomboConfig.HighlightInfo info, int x, int rowY, int w, int mouseX, int mouseY) {
        g.fill(x + 12, rowY, x + w - 12, rowY + 20, 0x221E293B);

        int nameColor = info.enabled ? getColorVal(info.color) : 0xFFFF5555;
        String displayName = mobName + (info.isAdvanced ? " §8[Adv]" : "") + ((info.requiredIsland != null && !info.requiredIsland.isEmpty()) ? " §7(" + info.requiredIsland + ")" : "");

        int textX = x + 18;
        int textY = rowY + 6;
        g.text(font, displayName, textX, textY, nameColor, false);

        // If disabled, draw a sharp strikethrough red line in the middle of the mob text
        if (!info.enabled) {
            int nameW = font.width(mobName);
            int lineY = textY + 4;
            g.fill(textX, lineY, textX + nameW, lineY + 1, 0xFFFF5555);
        }

        // Right side control buttons
        int rightX = x + w - 18;

        // [DEL] Button
        int delX = rightX - 26;
        boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
        ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

        // [EDIT] Button
        int editX = delX - 34;
        boolean editHover = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
        ConfigUITheme.drawPillButton(g, font, "§eEDIT", editX, rowY + 2, 32, 16, editHover, -1, 0x22FFFFFF, 0x44FFFFFF);

        // [ON / OFF] Button
        int onX = editX - 38;
        boolean onHover = mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
        String onLabel = info.enabled ? "§aON" : "§cOFF";
        ConfigUITheme.drawPillButton(g, font, onLabel, onX, rowY + 2, 36, 16, onHover, info.enabled ? 0xFF10B981 : 0xFF94A3B8, info.enabled ? 0x3310B981 : 0x1AFFFFFF, info.enabled ? 0x6610B981 : 0x33FFFFFF);

        // [TR] Button
        int trX = onX - 38;
        boolean trHover = mouseX >= trX && mouseX <= trX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
        String trLabel = info.tracer ? "§bTR" : "§7OFF";
        ConfigUITheme.drawPillButton(g, font, trLabel, trX, rowY + 2, 36, 16, trHover, info.tracer ? 0xFF00E5FF : 0xFF94A3B8, info.tracer ? 0x3300E5FF : 0x1AFFFFFF, info.tracer ? 0x6600E5FF : 0x33FFFFFF);

        // Deployable Color Dropdown Button
        int colBx = trX - 60;
        boolean colHover = mouseX >= colBx && mouseX <= colBx + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
        ConfigUITheme.drawPillButton(g, font, "§f" + info.color + " ▾", colBx, rowY + 2, 56, 16, colHover, -1, 0x22FFFFFF, 0x44FFFFFF);
    }

    public static boolean handleHighlightEditorClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int colW = (w - 36) / 2;
        int col1X = x + 12;
        int col2X = x + 12 + colW + 12;
        int curY = y + 10;

        // Mode switch click
        if (mouseX >= col1X && mouseX <= col1X + 220 && mouseY >= curY && mouseY <= curY + 18) {
            s.highlightAdvancedMode = !s.highlightAdvancedMode;
            BomboConfig.save();
            return true;
        }

        curY += 24;

        if (s.highlightAdvancedMode) {
            if (checkFieldClick(col1X, curY, colW, 18, "highMob", mouseX, mouseY)) return true;
            if (checkFieldClick(col1X, curY + 22, colW, 18, "advItemDisplay", mouseX, mouseY)) return true;
            if (checkFieldClick(col1X, curY + 44, colW, 18, "advEntityType", mouseX, mouseY)) return true;
            if (checkFieldClick(col1X, curY + 66, colW, 18, "advHeadHash", mouseX, mouseY)) return true;
            if (checkFieldClick(col1X, curY + 88, colW, 18, "advMobSize", mouseX, mouseY)) return true;

            if (mouseX >= col1X && mouseX <= col1X + colW / 2 - 4 && mouseY >= curY + 112 && mouseY <= curY + 130) {
                highTracerInput = !highTracerInput;
                return true;
            }
            if (mouseX >= col1X + colW / 2 + 4 && mouseX <= col1X + colW && mouseY >= curY + 112 && mouseY <= curY + 130) {
                advShowTitleInput = !advShowTitleInput;
                return true;
            }

            if (checkFieldClick(col2X, curY, colW, 18, "advArmorPiece", mouseX, mouseY)) return true;
            if (checkFieldClick(col2X, curY + 22, colW, 18, "advPlayerName", mouseX, mouseY)) return true;
            if (checkFieldClick(col2X, curY + 44, colW, 18, "highIsland", mouseX, mouseY)) return true;

            if (mouseX >= col2X && mouseX <= col2X + colW / 2 - 4 && mouseY >= curY + 66 && mouseY <= curY + 84) {
                List<String> visOpts = List.of("ALL", "ONLY_VISIBLE", "ONLY_INVISIBLE");
                int idx = visOpts.indexOf(advVisibilityInput);
                advVisibilityInput = visOpts.get((idx + 1) % visOpts.size());
                return true;
            }

            // Deployable Color Dropdown on form
            if (mouseX >= col2X + colW / 2 + 4 && mouseX <= col2X + colW && mouseY >= curY + 66 && mouseY <= curY + 84) {
                openColorDropdown(col2X + colW / 2 + 4, curY + 86, colW / 2 - 4, color -> highColorInput = color);
                return true;
            }

            if (mouseX >= col2X && mouseX <= col2X + colW && mouseY >= curY + 88 && mouseY <= curY + 106) {
                advPlaySoundInput = !advPlaySoundInput;
                return true;
            }

            int btnY = curY + 112;
            if (mouseX >= col2X && mouseX <= col2X + 160 && mouseY >= btnY && mouseY <= btnY + 18) {
                BomboConfigGUI.pushHighlightHistory();
                saveAdvancedHighlight();
                return true;
            }

            if (editingHighMob != null && mouseX >= col2X + 165 && mouseX <= col2X + 230 && mouseY >= btnY && mouseY <= btnY + 18) {
                clearHighlightInputs();
                return true;
            }

            curY += 136;
        } else {
            if (checkFieldClick(col1X, curY, colW, 18, "highMob", mouseX, mouseY)) return true;
            if (checkFieldClick(col1X, curY + 22, colW, 18, "highIsland", mouseX, mouseY)) return true;

            if (mouseX >= col2X && mouseX <= col2X + colW / 2 - 4 && mouseY >= curY && mouseY <= curY + 18) {
                openColorDropdown(col2X, curY + 20, colW / 2 - 4, color -> highColorInput = color);
                return true;
            }

            if (mouseX >= col2X + colW / 2 + 4 && mouseX <= col2X + colW && mouseY >= curY && mouseY <= curY + 18) {
                highTracerInput = !highTracerInput;
                return true;
            }

            int btnY = curY + 22;
            if (mouseX >= col2X && mouseX <= col2X + 150 && mouseY >= btnY && mouseY <= btnY + 18) {
                BomboConfigGUI.pushHighlightHistory();
                saveBasicHighlight();
                return true;
            }

            if (editingHighMob != null && mouseX >= col2X + 155 && mouseX <= col2X + 220 && mouseY >= btnY && mouseY <= btnY + 18) {
                clearHighlightInputs();
                return true;
            }

            curY += 46;
        }

        // Search Filter Click
        curY += 7;
        if (checkFieldClick(x + w - 180, curY, 168, 18, "highSearch", mouseX, mouseY)) return true;
        curY += 24;

        String filter = highlightSearchQuery.toLowerCase().trim();

        // -----------------------------------------------------------------------------------------
        // SECTION 1: CUSTOM HIGHLIGHTS INTERACTION
        // -----------------------------------------------------------------------------------------
        List<String> rawGeneralMobs = BestiaryManager.getGeneralHighlights(s.highlights);
        List<String> generalMobs = new ArrayList<>();
        for (String m : rawGeneralMobs) {
            if (filter.isEmpty() || m.toLowerCase().contains(filter)) generalMobs.add(m);
        }

        boolean isGenCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("General");
        int genHeaderY = curY;

        // Collapse Arrow Click
        if (mouseX >= x + 16 && mouseX <= x + 38 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18) {
            if (s.collapsedBestiaryCategories == null) s.collapsedBestiaryCategories = new HashSet<>();
            if (isGenCollapsed) s.collapsedBestiaryCategories.remove("General");
            else s.collapsedBestiaryCategories.add("General");
            BomboConfig.save();
            return true;
        }

        int rx = x + w - 18;
        int delAllX = rx - 26;
        int allToggleX = delAllX - 38;
        int catTrX = allToggleX - 42;
        int catColX = catTrX - 62;

        // Delete All Custom
        if (mouseX >= delAllX && mouseX <= delAllX + 26 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18) {
            BomboConfigGUI.pushHighlightHistory();
            for (String m : generalMobs) s.highlights.remove(m);
            BomboConfig.save();
            return true;
        }

        // Toggle All Custom
        if (mouseX >= allToggleX && mouseX <= allToggleX + 36 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18) {
            BomboConfigGUI.pushHighlightHistory();
            boolean anyDisabled = generalMobs.stream().anyMatch(m -> s.highlights.containsKey(m) && !s.highlights.get(m).enabled);
            for (String m : generalMobs) {
                if (s.highlights.containsKey(m)) s.highlights.get(m).enabled = anyDisabled;
            }
            BomboConfig.save();
            return true;
        }

        // Category Tracer Toggle
        if (mouseX >= catTrX && mouseX <= catTrX + 40 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18) {
            BomboConfigGUI.pushHighlightHistory();
            boolean anyTracer = generalMobs.stream().anyMatch(m -> s.highlights.containsKey(m) && s.highlights.get(m).tracer);
            for (String m : generalMobs) {
                if (s.highlights.containsKey(m)) s.highlights.get(m).tracer = !anyTracer;
            }
            BomboConfig.save();
            return true;
        }

        // Category Color Picker Dropdown
        if (mouseX >= catColX && mouseX <= catColX + 58 && mouseY >= genHeaderY + 2 && mouseY <= genHeaderY + 18) {
            openColorDropdown(catColX, genHeaderY + 20, 58, color -> {
                BomboConfigGUI.pushHighlightHistory();
                for (String m : generalMobs) {
                    if (s.highlights.containsKey(m)) s.highlights.get(m).color = color;
                }
                BestiaryManager.setCategoryColor("General", color);
                BomboConfig.save();
            });
            return true;
        }

        curY += 22;

        if (!isGenCollapsed) {
            for (String mobName : generalMobs) {
                BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                if (info == null) continue;
                if (handleHighlightRowClick(mobName, info, x, curY, w, mouseX, mouseY, button)) return true;
                curY += 22;
            }
        }

        curY += 6;

        // -----------------------------------------------------------------------------------------
        // SECTION 2: BESTIARY CATEGORIES INTERACTION
        // -----------------------------------------------------------------------------------------
        Map<String, List<String>> rawGroupedBestiary = BestiaryManager.getGroupedBestiary(s.highlights);
        boolean isBestiaryMasterCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("Bestiary");
        int bestiaryHeaderY = curY;

        if (mouseX >= x + 16 && mouseX <= x + 38 && mouseY >= bestiaryHeaderY + 2 && mouseY <= bestiaryHeaderY + 18) {
            if (s.collapsedBestiaryCategories == null) s.collapsedBestiaryCategories = new HashSet<>();
            if (isBestiaryMasterCollapsed) s.collapsedBestiaryCategories.remove("Bestiary");
            else s.collapsedBestiaryCategories.add("Bestiary");
            BomboConfig.save();
            return true;
        }

        curY += 22;

        if (!isBestiaryMasterCollapsed) {
            for (Map.Entry<String, List<String>> entry : rawGroupedBestiary.entrySet()) {
                String cat = entry.getKey();
                List<String> rawMobsInCat = entry.getValue();
                List<String> mobsInCat = new ArrayList<>();
                for (String m : rawMobsInCat) {
                    if (filter.isEmpty() || m.toLowerCase().contains(filter) || cat.toLowerCase().contains(filter)) {
                        mobsInCat.add(m);
                    }
                }

                if (mobsInCat.isEmpty() && !filter.isEmpty()) continue;

                boolean isCatCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains(cat);
                int subHeaderY = curY;

                // Subcategory arrow
                if (mouseX >= x + 24 && mouseX <= x + 44 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18) {
                    if (s.collapsedBestiaryCategories == null) s.collapsedBestiaryCategories = new HashSet<>();
                    if (isCatCollapsed) s.collapsedBestiaryCategories.remove(cat);
                    else s.collapsedBestiaryCategories.add(cat);
                    BomboConfig.save();
                    return true;
                }

                int subRx = x + w - 18;
                int sDelX = subRx - 26;
                int sAllX = sDelX - 38;
                int sTrX = sAllX - 42;
                int sColX = sTrX - 62;

                // Subcategory delete all
                if (mouseX >= sDelX && mouseX <= sDelX + 26 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18) {
                    BomboConfigGUI.pushHighlightHistory();
                    for (String m : mobsInCat) s.highlights.remove(m);
                    BomboConfig.save();
                    return true;
                }

                // Subcategory toggle all
                if (mouseX >= sAllX && mouseX <= sAllX + 36 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18) {
                    BomboConfigGUI.pushHighlightHistory();
                    boolean anyDisabled = mobsInCat.stream().anyMatch(m -> s.highlights.containsKey(m) && !s.highlights.get(m).enabled);
                    for (String m : mobsInCat) {
                        if (s.highlights.containsKey(m)) s.highlights.get(m).enabled = anyDisabled;
                    }
                    BomboConfig.save();
                    return true;
                }

                // Subcategory tracer toggle
                if (mouseX >= sTrX && mouseX <= sTrX + 40 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18) {
                    BomboConfigGUI.pushHighlightHistory();
                    boolean catTracer = BestiaryManager.getCategoryTracer(cat);
                    BestiaryManager.setCategoryTracer(cat, !catTracer);
                    for (String m : mobsInCat) {
                        if (s.highlights.containsKey(m)) s.highlights.get(m).tracer = !catTracer;
                    }
                    BomboConfig.save();
                    return true;
                }

                // Subcategory color dropdown
                if (mouseX >= sColX && mouseX <= sColX + 58 && mouseY >= subHeaderY + 2 && mouseY <= subHeaderY + 18) {
                    openColorDropdown(sColX, subHeaderY + 20, 58, color -> {
                        BomboConfigGUI.pushHighlightHistory();
                        BestiaryManager.setCategoryColor(cat, color);
                        for (String m : mobsInCat) {
                            if (s.highlights.containsKey(m)) s.highlights.get(m).color = color;
                        }
                        BomboConfig.save();
                    });
                    return true;
                }

                curY += 22;

                if (!isCatCollapsed) {
                    for (String mobName : mobsInCat) {
                        BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                        if (info == null) continue;
                        if (handleHighlightRowClick(mobName, info, x + 8, curY, w - 8, mouseX, mouseY, button)) return true;
                        curY += 22;
                    }
                }
            }
        }

        activeFocusedField = null;
        return false;
    }

    private static boolean handleHighlightRowClick(String mobName, BomboConfig.HighlightInfo info, int x, int rowY, int w, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int rightX = x + w - 18;

        int delX = rightX - 26;
        int editX = delX - 34;
        int onX = editX - 38;
        int trX = onX - 38;
        int colBx = trX - 60;

        // Right-Click on entry row or mob name: Toggle Enabled / Disabled
        if (button == 1 && mouseX >= x + 12 && mouseX < colBx && mouseY >= rowY && mouseY <= rowY + 20) {
            BomboConfigGUI.pushHighlightHistory();
            info.enabled = !info.enabled;
            BomboConfig.save();
            return true;
        }

        // [DEL] Click
        if (mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
            BomboConfigGUI.pushHighlightHistory();
            s.highlights.remove(mobName);
            BomboConfig.save();
            return true;
        }

        // [EDIT] Click
        if (mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
            editingHighMob = mobName;
            highMobInput = mobName;
            highColorInput = info.color;
            highTracerInput = info.tracer;
            highIslandInput = info.requiredIsland != null ? info.requiredIsland : "";
            if (info.isAdvanced || (info.itemDisplayId != null && !info.itemDisplayId.isEmpty())) {
                s.highlightAdvancedMode = true;
                advItemDisplayInput = info.itemDisplayId != null ? info.itemDisplayId : "";
                advEntityTypeInput = info.entityType != null ? info.entityType : "";
                advHeadHashInput = (info.headHashes != null && !info.headHashes.isEmpty()) ? info.headHashes.get(0) : "";
                advMobSizeInput = info.mobSize != null ? info.mobSize : "";
                advArmorPieceInput = info.armorPiece != null ? info.armorPiece : "";
                advPlayerNameInput = info.playerName != null ? info.playerName : "";
                advVisibilityInput = info.showInvisible ? "ALL" : "ONLY_VISIBLE";
                advShowTitleInput = info.showTitleOnSpawn;
                advPlaySoundInput = info.playSoundOnSpawn;
            }
            return true;
        }

        // [ON / OFF] Click
        if (mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
            BomboConfigGUI.pushHighlightHistory();
            info.enabled = !info.enabled;
            BomboConfig.save();
            return true;
        }

        // [TR] Click
        if (mouseX >= trX && mouseX <= trX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
            BomboConfigGUI.pushHighlightHistory();
            info.tracer = !info.tracer;
            BomboConfig.save();
            return true;
        }

        // Deployable Color Dropdown Click
        if (mouseX >= colBx && mouseX <= colBx + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
            openColorDropdown(colBx, rowY + 20, 56, color -> {
                BomboConfigGUI.pushHighlightHistory();
                info.color = color;
                BomboConfig.save();
            });
            return true;
        }

        return false;
    }

    // Helper to open deployable color list modal
    public static void openColorDropdown(int bx, int by, int bw, Consumer<String> setter) {
        colorDropdownX = bx;
        colorDropdownY = by;
        colorDropdownW = Math.max(90, bw);
        activeColorDropdownSetter = setter;
    }

    public static void renderColorDropdownPopup(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (activeColorDropdownSetter == null || colorDropdownX < 0) return;

        List<String> colors = SlotHighlight.COLORS;
        int rowH = 18;
        int totalH = colors.size() * rowH + 6;

        g.fill(colorDropdownX - 2, colorDropdownY - 2, colorDropdownX + colorDropdownW + 2, colorDropdownY + totalH + 2, 0xF00D1117);
        g.outline(colorDropdownX - 2, colorDropdownY - 2, colorDropdownW + 4, totalH + 4, ConfigUITheme.getAccentColor());

        for (int i = 0; i < colors.size(); i++) {
            String c = colors.get(i);
            int ry = colorDropdownY + 3 + i * rowH;
            boolean hover = mouseX >= colorDropdownX && mouseX <= colorDropdownX + colorDropdownW && mouseY >= ry && mouseY < ry + rowH;
            if (hover) {
                g.fill(colorDropdownX, ry, colorDropdownX + colorDropdownW, ry + rowH, 0x4400E5FF);
            }
            int colorVal = getColorVal(c);
            g.fill(colorDropdownX + 4, ry + 4, colorDropdownX + 14, ry + 14, colorVal);
            g.outline(colorDropdownX + 4, ry + 4, 10, 10, 0xFFFFFFFF);
            g.text(font, c, colorDropdownX + 18, ry + 5, hover ? 0xFFFFFFFF : 0xFFCBD5E1, false);
        }
    }

    public static boolean handleColorDropdownClick(double mouseX, double mouseY) {
        if (activeColorDropdownSetter == null || colorDropdownX < 0) return false;

        List<String> colors = SlotHighlight.COLORS;
        int rowH = 18;
        int totalH = colors.size() * rowH + 6;

        if (mouseX >= colorDropdownX && mouseX <= colorDropdownX + colorDropdownW && mouseY >= colorDropdownY && mouseY <= colorDropdownY + totalH) {
            int idx = (int) (mouseY - colorDropdownY - 3) / rowH;
            if (idx >= 0 && idx < colors.size()) {
                activeColorDropdownSetter.accept(colors.get(idx));
                activeColorDropdownSetter = null;
                return true;
            }
        }
        activeColorDropdownSetter = null;
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // 3. LORE ADDITIONS DRAG & DROP CARD
    // ---------------------------------------------------------------------------------------------
    public static class LoreItemDef {
        public final String key;
        public final String label;
        public final java.util.function.Supplier<Boolean> getter;
        public final java.util.function.Consumer<Boolean> setter;

        public LoreItemDef(String key, String label, java.util.function.Supplier<Boolean> getter, java.util.function.Consumer<Boolean> setter) {
            this.key = key;
            this.label = label;
            this.getter = getter;
            this.setter = setter;
        }
    }

    public static List<LoreItemDef> getLoreItemDefs() {
        BomboConfig.Settings s = BomboConfig.get();
        return List.of(
                new LoreItemDef("copyCanceled", "Copy Canceled Order Amount (Ctrl+Click)", () -> s.copyCanceledOrderAmount, v -> s.copyCanceledOrderAmount = v),
                new LoreItemDef("supercraft", "Supercraft Max Calculator (Ctrl+Click)", () -> s.supercraftMaxCalculator, v -> s.supercraftMaxCalculator = v),
                new LoreItemDef("startsIn", "Starts In Absolute Time", () -> s.startsInAbsoluteTime, v -> s.startsInAbsoluteTime = v),
                new LoreItemDef("dungeonQuality", "Dungeon Item Quality", () -> s.showDungeonQuality, v -> s.showDungeonQuality = v),
                new LoreItemDef("itemCreationDate", "Item Creation Date", () -> s.showItemCreationDate, v -> s.showItemCreationDate = v),
                new LoreItemDef("leatherColor", "Leather Armor Hex Color", () -> s.showLeatherColor, v -> s.showLeatherColor = v),
                new LoreItemDef("museumDonated", "Museum Donated Status", () -> s.showMuseumDonated, v -> s.showMuseumDonated = v),
                new LoreItemDef("skyblockId", "Skyblock Item ID", () -> s.showSkyblockId, v -> s.showSkyblockId = v),
                new LoreItemDef("lowestBin", "Lowest BIN / BZ Price", () -> s.lowestBin, v -> s.lowestBin = v),
                new LoreItemDef("craftCost", "Raw Craft Cost", () -> s.craftCostTooltip, v -> s.craftCostTooltip = v),
                new LoreItemDef("npcPrice", "NPC Sell Price", () -> s.npcPrice, v -> s.npcPrice = v),
                new LoreItemDef("petLowestBin", "Pet Lowest BIN", () -> s.showPetLowestBin, v -> s.showPetLowestBin = v),
                new LoreItemDef("estimatedValue", "Estimated Value (Craft + Enchants)", () -> s.showEstimatedValue, v -> s.showEstimatedValue = v)
        );
    }

    public static int getLoreAdditionsCardHeight() {
        return 13 * 26 + 45;
    }

    public static void renderLoreAdditionsCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        g.text(font, "§e§lTooltip Additions & Calculations", x + 12, y + 10, -1, false);
        g.text(font, "§7Click or drag §f[☰]§7 to reorder lines seamlessly in tooltips", x + 12, y + 22, 0xFF94A3B8, false);

        int curY = y + 36;
        List<LoreItemDef> items = new ArrayList<>(getLoreItemDefs());
        items.sort(Comparator.comparingInt(def -> s.getLoreOrder(def.key, 1)));

        // Handle live drag reordering as mouse moves over other rows
        if (isDraggingLore && draggedLoreIndex >= 0 && draggedLoreIndex < items.size()) {
            int hoveredTargetIdx = Math.max(0, Math.min(items.size() - 1, (mouseY - (y + 36)) / 26));
            if (hoveredTargetIdx != draggedLoreIndex) {
                LoreItemDef draggedItem = items.remove(draggedLoreIndex);
                items.add(hoveredTargetIdx, draggedItem);
                draggedLoreIndex = hoveredTargetIdx;
                // Normalize all 13 items to sequential 1..13
                for (int i = 0; i < items.size(); i++) {
                    s.setLoreOrder(items.get(i).key, i + 1);
                }
                BomboConfig.save();
            }
        }

        for (int i = 0; i < items.size(); i++) {
            LoreItemDef def = items.get(i);
            boolean active = def.getter != null && def.getter.get();
            int order = i + 1; // Sequential normalized order
            String pos = s.lorePosition != null ? s.lorePosition.getOrDefault(def.key, "BOT") : "BOT";

            int rowY = curY + i * 26;
            boolean isDragged = (isDraggingLore && draggedLoreIndex == i);

            // Row Card Background
            int rowBg = isDragged ? 0x6600E5FF : (active ? 0x331E293B : 0x1A0F172A);
            g.fill(x + 12, rowY, x + w - 12, rowY + 22, rowBg);
            if (isDragged) {
                g.outline(x + 12, rowY, w - 24, 22, ConfigUITheme.getAccentColor());
            }

            // Drag Grip Handle [ ☰ ]
            boolean gripHover = mouseX >= x + 16 && mouseX <= x + 34 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            g.text(font, "§7☰", x + 20, rowY + 7, gripHover || isDragged ? 0xFF00E5FF : 0xFF64748B, false);

            // Toggle Checkbox Switch
            boolean toggleHover = mouseX >= x + 38 && mouseX <= x + 56 && mouseY >= rowY + 3 && mouseY <= rowY + 19;
            int toggleCol = active ? 0xFF10B981 : 0xFF64748B;
            g.fill(x + 38, rowY + 3, x + 56, rowY + 19, active ? 0x4410B981 : 0x22FFFFFF);
            g.outline(x + 38, rowY + 3, 18, 16, toggleCol);
            if (active) {
                g.fill(x + 42, rowY + 7, x + 52, rowY + 15, 0xFF10B981);
            }

            // Title Label
            g.text(font, (active ? "§f" : "§8") + def.label, x + 64, rowY + 7, active ? 0xFFFFFFFF : 0xFF94A3B8, false);

            // Right side button: [▲ TOP] / [▼ BOT]
            int rx = x + w - 18;
            int posW = 54;
            int posX = rx - posW;
            boolean isTop = "TOP".equalsIgnoreCase(pos);
            String posLabel = isTop ? "▲ TOP" : "▼ BOT";
            boolean posHover = mouseX >= posX && mouseX <= posX + posW && mouseY >= rowY + 3 && mouseY <= rowY + 19;
            ConfigUITheme.drawPillButton(g, font, posLabel, posX, rowY + 3, posW, 16, posHover, isTop ? 0xFF00E5FF : 0xFF94A3B8, isTop ? 0x3300E5FF : 0x22FFFFFF, isTop ? 0x6600E5FF : 0x44FFFFFF);
        }
    }

    public static boolean handleLoreAdditionsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int curY = y + 36;
        List<LoreItemDef> items = new ArrayList<>(getLoreItemDefs());
        items.sort(Comparator.comparingInt(def -> s.getLoreOrder(def.key, 1)));

        for (int i = 0; i < items.size(); i++) {
            LoreItemDef def = items.get(i);
            int rowY = curY + i * 26;

            // Drag Grip Click
            if (mouseX >= x + 16 && mouseX <= x + 34 && mouseY >= rowY && mouseY <= rowY + 22) {
                isDraggingLore = true;
                draggedLoreIndex = i;
                return true;
            }

            // Toggle Switch Click
            if (mouseX >= x + 38 && mouseX <= x + 56 && mouseY >= rowY + 3 && mouseY <= rowY + 19) {
                if (def.setter != null && def.getter != null) {
                    def.setter.accept(!def.getter.get());
                    BomboConfig.save();
                    return true;
                }
            }

            int rx = x + w - 18;
            int posW = 54;
            int posX = rx - posW;

            // [▲ TOP] / [▼ BOT] Position toggle click
            if (mouseX >= posX && mouseX <= posX + posW && mouseY >= rowY + 3 && mouseY <= rowY + 19) {
                String curPos = s.lorePosition != null ? s.lorePosition.getOrDefault(def.key, "BOT") : "BOT";
                s.setLorePos(def.key, "BOT".equals(curPos) ? "TOP" : "BOT");
                BomboConfig.save();
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 4. CLICKER TARGETS MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String clickGuiInput = "";
    public static String clickKeyInput = "";
    public static String clickItemInput = "";
    public static String clickTypeInput = "Left Click";
    public static int editingClickIndex = -1;
    public static boolean clickKeyIsListening = false;

    public static int getClickerCardHeight() {
        int count = ClickLogic.getTargets() != null ? ClickLogic.getTargets().size() : 0;
        return 85 + count * 24 + 40;
    }

    public static void renderClickerCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int curY = y + 10;
        int colW = (w - 36) / 3;

        renderCleanInputField(g, font, "GUI Matcher (e.g. Chest)", clickGuiInput, "clickGui", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Item Name / Matcher", clickItemInput, "clickItem", x + 12 + colW + 6, curY, colW, mouseX, mouseY);

        int keyX = x + 12 + colW * 2 + 12;
        int keyW = colW - 12;
        String keyDisplay = clickKeyIsListening ? "§e[PRESS KEY]" : (clickKeyInput.isEmpty() ? "§8Keybind (Click)" : "§b" + ClickLogic.getKeyDisplayName(clickKeyInput));
        boolean keyHover = mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, keyDisplay, keyX, curY, keyW, 18, keyHover, -1, clickKeyIsListening ? 0x44FFAA00 : 0x221E293B, clickKeyIsListening ? 0xFFFFAA00 : 0x55FFFFFF);

        curY += 24;

        String addText = editingClickIndex != -1 ? "§e✔ Save Click Target" : "§a+ Add Click Target";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addText, x + 12, curY, 168, 18, addHover, -1, 0x2210B981, 0x6610B981);

        if (editingClickIndex != -1) {
            boolean cancelHover = mouseX >= x + 185 && mouseX <= x + 250 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 185, curY, 65, 18, cancelHover, -1, 0x22EF4444, 0x44EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
        if (targets == null || targets.isEmpty()) {
            g.text(font, "§8No automated click targets added yet. Add one above!", x + 16, curY + 4, 0xFF94A3B8, false);
        } else {
            for (int i = 0; i < targets.size(); i++) {
                ClickLogic.ClickTarget t = targets.get(i);
                int rowY = curY + i * 24;
                g.fill(x + 12, rowY, x + w - 12, rowY + 20, 0x221E293B);

                g.text(font, "§e[" + t.gui + "] §f" + t.item + " §8-> §b" + ClickLogic.getKeyDisplayName(t.keyName), x + 18, rowY + 6, -1, false);

                int rx = x + w - 18;
                int delX = rx - 26;
                boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                int editX = delX - 34;
                boolean editHover = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§eEDIT", editX, rowY + 2, 32, 16, editHover, -1, 0x22FFFFFF, 0x44FFFFFF);
            }
        }
    }

    public static boolean handleClickerCardClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        int curY = y + 10;
        int colW = (w - 36) / 3;

        if (checkFieldClick(x + 12, curY, colW, 18, "clickGui", mouseX, mouseY)) {
            clickKeyIsListening = false;
            return true;
        }
        if (checkFieldClick(x + 12 + colW + 6, curY, colW, 18, "clickItem", mouseX, mouseY)) {
            clickKeyIsListening = false;
            return true;
        }

        int keyX = x + 12 + colW * 2 + 12;
        int keyW = colW - 12;
        if (mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18) {
            clickKeyIsListening = !clickKeyIsListening;
            activeFocusedField = null;
            return true;
        }

        curY += 24;
        if (mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18) {
            clickKeyIsListening = false;
            if (!clickItemInput.trim().isEmpty() && !clickKeyInput.trim().isEmpty()) {
                if (editingClickIndex != -1) {
                    ClickLogic.removeTarget(editingClickIndex);
                    editingClickIndex = -1;
                }
                ClickLogic.setTarget(clickItemInput.trim(), clickGuiInput.trim(), clickKeyInput.trim(), "left", false);
                clickGuiInput = "";
                clickItemInput = "";
                clickKeyInput = "";
                activeFocusedField = null;
                return true;
            }
        }

        if (editingClickIndex != -1 && mouseX >= x + 185 && mouseX <= x + 250 && mouseY >= curY && mouseY <= curY + 18) {
            clickKeyIsListening = false;
            editingClickIndex = -1;
            clickGuiInput = "";
            clickItemInput = "";
            clickKeyInput = "";
            return true;
        }

        curY += 32;
        List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
        if (targets != null) {
            for (int i = 0; i < targets.size(); i++) {
                ClickLogic.ClickTarget t = targets.get(i);
                int rowY = curY + i * 24;
                int rx = x + w - 18;
                int delX = rx - 26;
                int editX = delX - 34;

                if (mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    clickKeyIsListening = false;
                    ClickLogic.removeTarget(i);
                    return true;
                }

                if (mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    editingClickIndex = i;
                    clickGuiInput = t.gui;
                    clickItemInput = t.item;
                    clickKeyInput = t.keyName;
                    clickKeyIsListening = false;
                    return true;
                }
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 5. CHAT TRIGGERS MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String triggerTextInput = "";
    public static String triggerCommandInput = "";
    public static String triggerTitleInput = "";
    public static String triggerSoundInput = "";
    public static int triggerSoundTimesInput = 1;
    public static int editingTriggerIdx = -1;

    public static int getChatTriggersCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.ChatTrigger> list = s.profileChatTriggers != null ? s.profileChatTriggers.get(s.activeProfile) : null;
        int count = list != null ? list.size() : 0;
        return 110 + count * 24 + 40;
    }

    public static void renderChatTriggersCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int curY = y + 10;
        int colW = (w - 36) / 2;

        renderCleanInputField(g, font, "Trigger Message / Regex", triggerTextInput, "trigText", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Command to Execute (e.g. /warp hub)", triggerCommandInput, "trigCmd", x + 12 + colW + 12, curY, colW, mouseX, mouseY);

        curY += 22;
        renderCleanInputField(g, font, "Screen Title Alert", triggerTitleInput, "trigTitle", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Play Sound Chime", triggerSoundInput, "trigSound", x + 12 + colW + 12, curY, colW, mouseX, mouseY);

        curY += 24;
        String addText = editingTriggerIdx != -1 ? "§e✔ Save Chat Trigger" : "§a+ Add Chat Trigger";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addText, x + 12, curY, 168, 18, addHover, -1, 0x2210B981, 0x6610B981);

        if (editingTriggerIdx != -1) {
            boolean cancelHover = mouseX >= x + 185 && mouseX <= x + 250 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 185, curY, 65, 18, cancelHover, -1, 0x22EF4444, 0x44EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        List<BomboConfig.ChatTrigger> list = s.profileChatTriggers != null ? s.profileChatTriggers.get(s.activeProfile) : null;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No triggers in active profile [" + s.activeProfile + "]. Add one above!", x + 16, curY + 4, 0xFF94A3B8, false);
        } else {
            for (int i = 0; i < list.size(); i++) {
                BomboConfig.ChatTrigger t = list.get(i);
                int rowY = curY + i * 24;
                g.fill(x + 12, rowY, x + w - 12, rowY + 20, 0x221E293B);

                g.text(font, "§e\"" + t.triggerText + "\" §8-> §b" + (t.commandToRun != null && !t.commandToRun.isEmpty() ? t.commandToRun : t.titleToShow), x + 18, rowY + 6, -1, false);

                int rx = x + w - 18;
                int delX = rx - 26;
                boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                int editX = delX - 34;
                boolean editHover = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§eEDIT", editX, rowY + 2, 32, 16, editHover, -1, 0x22FFFFFF, 0x44FFFFFF);

                int onX = editX - 38;
                boolean onHover = mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, t.enabled ? "§aON" : "§cOFF", onX, rowY + 2, 36, 16, onHover, t.enabled ? 0xFF10B981 : 0xFF94A3B8, t.enabled ? 0x3310B981 : 0x1AFFFFFF, 0x44FFFFFF);
            }
        }
    }

    public static boolean handleChatTriggersCardClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int curY = y + 10;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW, 18, "trigText", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 12, curY, colW, 18, "trigCmd", mouseX, mouseY)) return true;

        curY += 22;
        if (checkFieldClick(x + 12, curY, colW, 18, "trigTitle", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 12, curY, colW, 18, "trigSound", mouseX, mouseY)) return true;

        curY += 24;
        if (mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18) {
            if (!triggerTextInput.trim().isEmpty()) {
                if (s.profileChatTriggers == null) s.profileChatTriggers = new HashMap<>();
                List<BomboConfig.ChatTrigger> list = s.profileChatTriggers.computeIfAbsent(s.activeProfile, k -> new ArrayList<>());
                BomboConfig.ChatTrigger ct = new BomboConfig.ChatTrigger(triggerTextInput.trim(), triggerCommandInput.trim(), triggerTitleInput.trim(), triggerSoundInput.trim(), triggerSoundTimesInput);
                if (editingTriggerIdx != -1 && editingTriggerIdx < list.size()) {
                    list.set(editingTriggerIdx, ct);
                    editingTriggerIdx = -1;
                } else {
                    list.add(ct);
                }
                BomboConfig.save();
                triggerTextInput = "";
                triggerCommandInput = "";
                triggerTitleInput = "";
                triggerSoundInput = "";
                activeFocusedField = null;
                return true;
            }
        }

        if (editingTriggerIdx != -1 && mouseX >= x + 185 && mouseX <= x + 250 && mouseY >= curY && mouseY <= curY + 18) {
            editingTriggerIdx = -1;
            triggerTextInput = "";
            triggerCommandInput = "";
            triggerTitleInput = "";
            triggerSoundInput = "";
            return true;
        }

        curY += 32;
        List<BomboConfig.ChatTrigger> list = s.profileChatTriggers != null ? s.profileChatTriggers.get(s.activeProfile) : null;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                BomboConfig.ChatTrigger t = list.get(i);
                int rowY = curY + i * 24;
                int rx = x + w - 18;
                int delX = rx - 26;
                int editX = delX - 34;
                int onX = editX - 38;

                int finalI = i;
                if (mouseX >= delX - 4 && mouseX <= delX + 30 && mouseY >= rowY && mouseY <= rowY + 20) {
                    BomboConfig.ChatTrigger removed = list.remove(finalI);
                    undoStack.push(() -> {
                        list.add(Math.min(finalI, list.size()), removed);
                        BomboConfig.save();
                    });
                    if (editingTriggerIdx == finalI) editingTriggerIdx = -1;
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    editingTriggerIdx = i;
                    triggerTextInput = t.triggerText;
                    triggerCommandInput = t.commandToRun;
                    triggerTitleInput = t.titleToShow;
                    triggerSoundInput = t.soundToPlay != null ? t.soundToPlay : "";
                    return true;
                }

                if (mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    t.enabled = !t.enabled;
                    BomboConfig.save();
                    return true;
                }
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 6. BLOCK HIGHLIGHTS LIST & ADD CARD
    // ---------------------------------------------------------------------------------------------
    public static String bhBlockInput = "";
    public static String bhColorInput = "GOLD";
    public static boolean bhThroughWalls = true;

    public static int getBlockHighlightCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        int count = s.blockHighlights != null ? s.blockHighlights.size() : 0;
        return 85 + count * 24 + 40;
    }

    public static void renderBlockHighlightsCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int curY = y + 10;
        int colW = (w - 36) / 3;

        renderCleanInputField(g, font, "Block ID e.g. diamond_block", bhBlockInput, "bhBlock", x + 12, curY, colW + 20, mouseX, mouseY);
        renderColorSelector(g, font, "Color", bhColorInput, x + 12 + colW + 30, curY, colW - 20, mouseX, mouseY);
        renderInlineToggle(g, font, "Through Walls", bhThroughWalls, x + 12 + colW * 2 + 20, curY, colW - 20, mouseX, mouseY);

        curY += 24;
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§a+ Add Block Highlight", x + 12, curY, 168, 18, addHover, -1, 0x2210B981, 0x6610B981);

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        if (s.blockHighlights == null || s.blockHighlights.isEmpty()) {
            g.text(font, "§8No blocks highlighted yet. Add one above!", x + 16, curY + 4, 0xFF94A3B8, false);
        } else {
            List<String> blocks = new ArrayList<>(s.blockHighlights.keySet());
            Collections.sort(blocks);
            for (String blk : blocks) {
                BomboConfig.BlockHighlightInfo info = s.blockHighlights.get(blk);
                if (info == null) continue;
                int rowY = curY;
                g.fill(x + 12, rowY, x + w - 12, rowY + 20, 0x221E293B);

                int blkColor = info.enabled ? getColorVal(info.color) : 0xFFFF5555;
                g.text(font, blk + (info.throughWalls ? " §7(Walls)" : ""), x + 18, rowY + 6, blkColor, false);
                if (!info.enabled) {
                    int bW = font.width(blk);
                    g.fill(x + 18, rowY + 10, x + 18 + bW, rowY + 11, 0xFFFF5555);
                }

                int rightX = x + w - 18;
                int delX = rightX - 26;
                boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                int onX = delX - 38;
                boolean onHover = mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                String onLabel = info.enabled ? "§aON" : "§cOFF";
                ConfigUITheme.drawPillButton(g, font, onLabel, onX, rowY + 2, 36, 16, onHover, info.enabled ? 0xFF10B981 : 0xFF94A3B8, info.enabled ? 0x3310B981 : 0x1AFFFFFF, 0x44FFFFFF);

                int colBx = onX - 60;
                boolean colHover = mouseX >= colBx && mouseX <= colBx + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§f" + info.color + " ▾", colBx, rowY + 2, 56, 16, colHover, -1, 0x22FFFFFF, 0x44FFFFFF);

                curY += 24;
            }
        }
    }

    public static boolean handleBlockHighlightsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int curY = y + 10;
        int colW = (w - 36) / 3;

        if (checkFieldClick(x + 12, curY, colW + 20, 18, "bhBlock", mouseX, mouseY)) return true;

        if (mouseX >= x + 12 + colW + 30 && mouseX <= x + 12 + colW * 2 + 10 && mouseY >= curY && mouseY <= curY + 18) {
            openColorDropdown(x + 12 + colW + 30, curY + 20, colW - 20, color -> bhColorInput = color);
            return true;
        }

        if (mouseX >= x + 12 + colW * 2 + 20 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 18) {
            bhThroughWalls = !bhThroughWalls;
            return true;
        }

        curY += 24;
        if (mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18) {
            String bName = bhBlockInput.trim().toLowerCase();
            if (!bName.isEmpty()) {
                if (s.blockHighlights == null) s.blockHighlights = new HashMap<>();
                s.blockHighlights.put(bName, new BomboConfig.BlockHighlightInfo(bhColorInput.toUpperCase(), bhThroughWalls, true));
                bhBlockInput = "";
                activeFocusedField = null;
                BomboConfig.save();
                return true;
            }
        }

        curY += 32;
        if (s.blockHighlights != null) {
            List<String> blocks = new ArrayList<>(s.blockHighlights.keySet());
            Collections.sort(blocks);
            for (String blk : blocks) {
                BomboConfig.BlockHighlightInfo info = s.blockHighlights.get(blk);
                if (info == null) continue;
                int rowY = curY;
                int rightX = x + w - 18;
                int delX = rightX - 26;
                int onX = delX - 38;
                int colBx = onX - 60;

                // Right click row to toggle
                if (button == 1 && mouseX >= x + 12 && mouseX < colBx && mouseY >= rowY && mouseY <= rowY + 20) {
                    info.enabled = !info.enabled;
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    s.blockHighlights.remove(blk);
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    info.enabled = !info.enabled;
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= colBx && mouseX <= colBx + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    openColorDropdown(colBx, rowY + 20, 56, color -> {
                        info.color = color;
                        BomboConfig.save();
                    });
                    return true;
                }

                curY += 24;
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 7. PROFILES MANAGEMENT & COMMAND BINDS CARD
    // ---------------------------------------------------------------------------------------------
    public static String newProfileInput = "";

    public static void renderProfilesManager(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);
        BomboConfig.Settings s = BomboConfig.get();
        String activeProf = s != null && s.activeProfile != null ? s.activeProfile : "default";

        g.text(font, "§6§lProfile Management §8| §bActive: §e" + activeProf, x + 12, y + 10, ConfigUITheme.ACCENT_GOLD, false);
        g.text(font, "§7Use §fUp/Down Arrow Keys §7or buttons below to cycle and switch active profiles.", x + 12, y + 22, ConfigUITheme.getTextMuted(), false);

        int curY = y + 38;
        int colW = (w - 36) / 2;

        renderCleanInputField(g, font, "New Profile Name", newProfileInput, "newProfile", x + 12, curY, colW, mouseX, mouseY);

        boolean addHover = mouseX >= x + 12 + colW + 6 && mouseX <= x + 12 + colW + 6 + 110 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§a+ Create Profile", x + 12 + colW + 6, curY, 110, 18, addHover, -1, 0x2210B981, 0x6610B981);

        boolean dupHover = mouseX >= x + 12 + colW + 122 && mouseX <= x + 12 + colW + 122 + 110 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§e📋 Clone Active", x + 12 + colW + 122, curY, 110, 18, dupHover, -1, 0x22FFAA00, 0x44FFAA00);

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 8;

        List<String> allProfiles = ConfigRegistry.getAllProfileNames();
        int btnW = (w - 36) / 4;
        int btnH = 20;

        for (int i = 0; i < allProfiles.size(); i++) {
            String pName = allProfiles.get(i);
            int col = i % 4;
            int row = i / 4;
            int bx = x + 12 + col * (btnW + 4);
            int by = curY + row * (btnH + 4);

            if (by + btnH > y + h - 10) break;

            boolean isActive = pName.equalsIgnoreCase(activeProf);
            boolean bHover = mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH;

            int borderCol = isActive ? 0xFF00E5FF : (bHover ? 0xFFFFFFFF : 0x44FFFFFF);
            int bgCol = isActive ? 0x4400E5FF : (bHover ? 0x22FFFFFF : 0x111E293B);
            String title = (isActive ? "§b● §f" : "§7") + pName;

            ConfigUITheme.drawPillButton(g, font, title, bx, by, btnW, btnH, bHover, isActive ? 0xFF00E5FF : -1, bgCol, borderCol);

            if (!"default".equalsIgnoreCase(pName) && !"General".equalsIgnoreCase(pName)) {
                int delX = bx + btnW - 14;
                int delY = by + 2;
                boolean delHover = mouseX >= delX && mouseX <= delX + 12 && mouseY >= delY && mouseY <= delY + 12;
                if (bHover) {
                    g.text(font, "§c✕", delX, delY + 2, delHover ? 0xFFFF5555 : 0xFF888888, false);
                }
            }
        }
    }

    public static boolean handleProfilesManagerClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return false;

        int curY = y + 38;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW, 18, "newProfile", mouseX, mouseY)) return true;

        // Create Profile
        if (mouseX >= x + 12 + colW + 6 && mouseX <= x + 12 + colW + 6 + 110 && mouseY >= curY && mouseY <= curY + 18) {
            String name = newProfileInput.trim();
            if (!name.isEmpty()) {
                if (s.profileBinds == null) s.profileBinds = new HashMap<>();
                s.profileBinds.putIfAbsent(name, new ArrayList<>());
                s.activeProfile = name;
                newProfileInput = "";
                activeFocusedField = null;
                BomboConfig.save();
                return true;
            }
        }

        // Clone Active Profile
        if (mouseX >= x + 12 + colW + 122 && mouseX <= x + 12 + colW + 122 + 110 && mouseY >= curY && mouseY <= curY + 18) {
            String current = s.activeProfile != null ? s.activeProfile : "default";
            String cloneName = current + "_copy";
            if (s.profileBinds == null) s.profileBinds = new HashMap<>();
            List<BomboConfig.CommandBind> originalBinds = s.profileBinds.getOrDefault(current, new ArrayList<>());
            List<BomboConfig.CommandBind> clonedBinds = new ArrayList<>();
            for (BomboConfig.CommandBind kb : originalBinds) {
                BomboConfig.CommandBind cb = new BomboConfig.CommandBind();
                cb.command = kb.command;
                cb.keyCodes = kb.keyCodes != null ? new ArrayList<>(kb.keyCodes) : new ArrayList<>();
                cb.keyName = kb.keyName;
                cb.requiredIsland = kb.requiredIsland;
                cb.requiredArmor = kb.requiredArmor;
                cb.requiredProfile = kb.requiredProfile;
                cb.enabled = kb.enabled;
                clonedBinds.add(cb);
            }
            s.profileBinds.put(cloneName, clonedBinds);
            s.activeProfile = cloneName;
            BomboConfig.save();
            return true;
        }

        curY += 34;
        List<String> allProfiles = ConfigRegistry.getAllProfileNames();
        int btnW = (w - 36) / 4;
        int btnH = 20;

        for (int i = 0; i < allProfiles.size(); i++) {
            String pName = allProfiles.get(i);
            int col = i % 4;
            int row = i / 4;
            int bx = x + 12 + col * (btnW + 4);
            int by = curY + row * (btnH + 4);

            if (by + btnH > y + h - 10) break;

            if (!"default".equalsIgnoreCase(pName) && !"General".equalsIgnoreCase(pName)) {
                int delX = bx + btnW - 14;
                int delY = by + 2;
                if (mouseX >= delX && mouseX <= delX + 14 && mouseY >= delY && mouseY <= delY + 14) {
                    if (s.profileBinds != null) s.profileBinds.remove(pName);
                    if (s.keybindBinds != null) s.keybindBinds.remove(pName);
                    if (s.coordBinds != null) s.coordBinds.remove(pName);
                    if (s.customWaypoints != null) s.customWaypoints.remove(pName);
                    if (s.profileChatTriggers != null) s.profileChatTriggers.remove(pName);
                    if (pName.equalsIgnoreCase(s.activeProfile)) s.activeProfile = "default";
                    BomboConfig.save();
                    return true;
                }
            }

            if (mouseX >= bx && mouseX <= bx + btnW && mouseY >= by && mouseY <= by + btnH) {
                s.activeProfile = pName;
                BomboConfig.save();
                return true;
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // COMMON WIDGET HELPERS
    // ---------------------------------------------------------------------------------------------
    public static boolean isFieldSelected = false;
    public static int cursorPosition = -1;
    public static int selectionAnchor = -1;
    public static final Deque<Runnable> undoStack = new ArrayDeque<>();

    public static void renderCleanInputField(GuiGraphicsExtractor g, Font font, String placeholder, String value, String fieldId, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean focused = fieldId.equals(activeFocusedField);
        int inputH = 18;

        g.fill(bx, by, bx + bw, by + inputH, focused ? 0xEE1E293B : 0xEE0F172A);
        g.outline(bx, by, bw, inputH, focused ? ConfigUITheme.getAccentColor() : 0x33FFFFFF);

        String curVal = value != null ? value : "";
        if (!focused) {
            String displayVal = !curVal.isEmpty() ? curVal : "§8" + placeholder;
            g.text(font, displayVal, bx + 6, by + 5, !curVal.isEmpty() ? 0xFFFFFFFF : 0xFF64748B, false);
            return;
        }

        // Focused Field Rendering with Selection Range and Cursor
        if (cursorPosition < 0 || cursorPosition > curVal.length()) {
            cursorPosition = curVal.length();
        }

        if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
            int selStart = Math.min(selectionAnchor, cursorPosition);
            int selEnd = Math.max(selectionAnchor, cursorPosition);
            selStart = Math.max(0, Math.min(curVal.length(), selStart));
            selEnd = Math.max(0, Math.min(curVal.length(), selEnd));

            int x1 = bx + 6 + font.width(curVal.substring(0, selStart));
            int x2 = bx + 6 + font.width(curVal.substring(0, selEnd));
            g.fill(x1, by + 3, x2, by + inputH - 3, 0x6600AAFF);
        }

        g.text(font, curVal, bx + 6, by + 5, 0xFFFFFFFF, false);

        // Blinking Cursor
        if (System.currentTimeMillis() % 1000 > 500) {
            int curX = bx + 6 + font.width(curVal.substring(0, Math.min(curVal.length(), Math.max(0, cursorPosition))));
            g.fill(curX, by + 3, curX + 1, by + inputH - 3, 0xFFFFFFFF);
        }
    }

    private static void renderInlineToggle(GuiGraphicsExtractor g, Font font, String label, boolean val, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 18;
        String text = (val ? "§a✔ " : "§c✖ ") + label;
        ConfigUITheme.drawPillButton(g, font, text, bx, by, bw, 18, hover, val ? 0xFF10B981 : 0xFF94A3B8, val ? 0x3310B981 : 0x1AFFFFFF, val ? 0x6610B981 : 0x33FFFFFF);
    }

    private static void renderCycleSelector(GuiGraphicsExtractor g, Font font, String label, String currentVal, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 18;
        ConfigUITheme.drawPillButton(g, font, "§7" + label + ": §e" + currentVal + " ▾", bx, by, bw, 18, hover, -1, 0x22FFFFFF, 0x44FFFFFF);
    }

    private static void renderColorSelector(GuiGraphicsExtractor g, Font font, String label, String currentColor, int bx, int by, int bw, int mouseX, int mouseY) {
        boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + 18;
        ConfigUITheme.drawPillButton(g, font, "§7" + label + ": §f" + currentColor + " ▾", bx, by, bw, 18, hover, -1, 0x22FFFFFF, 0x44FFFFFF);
    }

    public static boolean checkFieldClick(int bx, int by, int bw, int bh, String fieldId, int mouseX, int mouseY) {
        if (mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + bh) {
            if (!fieldId.equals(activeFocusedField)) {
                isFieldSelected = false;
                selectionAnchor = -1;
            }
            activeFocusedField = fieldId;
            String curVal = getFieldValue(fieldId);
            cursorPosition = curVal != null ? curVal.length() : 0;
            return true;
        }
        return false;
    }

    private static void saveAdvancedHighlight() {
        BomboConfig.Settings s = BomboConfig.get();
        String key = !highMobInput.trim().isEmpty() ? highMobInput.trim()
                : (!advItemDisplayInput.trim().isEmpty() ? "display:" + advItemDisplayInput.trim()
                : (!advEntityTypeInput.trim().isEmpty() ? advEntityTypeInput.trim()
                : advHeadHashInput.trim()));

        if (!key.isEmpty()) {
            if (editingHighMob != null) {
                s.highlights.remove(editingHighMob);
            }
            boolean showInvis = "ALL".equals(advVisibilityInput) || "ONLY_INVISIBLE".equals(advVisibilityInput);
            BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(
                    highColorInput.toUpperCase(Locale.ROOT), showInvis, true, highTracerInput,
                    highIslandInput.trim(), false);
            hi.isAdvanced = true;
            hi.targetType = !advItemDisplayInput.trim().isEmpty() ? "ITEM_DISPLAY" : "ADVANCED";
            hi.itemDisplayId = advItemDisplayInput.trim();
            hi.entityType = advEntityTypeInput.trim();
            if (!advHeadHashInput.trim().isEmpty()) {
                hi.headHashes = new ArrayList<>(List.of(advHeadHashInput.trim().toLowerCase(Locale.ROOT)));
            }
            hi.mobSize = advMobSizeInput.trim();
            hi.armorPiece = advArmorPieceInput.trim();
            hi.playerName = advPlayerNameInput.trim();
            hi.showTitleOnSpawn = advShowTitleInput;
            hi.playSoundOnSpawn = advPlaySoundInput;
            s.highlights.put(key.toLowerCase(Locale.ROOT), hi);
            BomboConfig.save();
            clearHighlightInputs();
        }
    }

    private static void saveBasicHighlight() {
        BomboConfig.Settings s = BomboConfig.get();
        String cleanMob = highMobInput.trim();
        if (!cleanMob.isEmpty()) {
            if (editingHighMob != null) {
                s.highlights.remove(editingHighMob);
            }
            BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(
                    highColorInput.toUpperCase(Locale.ROOT), true, true, highTracerInput,
                    highIslandInput.trim(), false);
            s.highlights.put(cleanMob.toLowerCase(Locale.ROOT), hi);
            BomboConfig.save();
            clearHighlightInputs();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 8. ALIASES MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String aliasCommandInput = "";
    public static String aliasActualInput = "";
    public static String editingAliasKey = null;

    public static int getAliasesCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        int count = s.commandAliases != null ? s.commandAliases.size() : 0;
        return 85 + count * 24 + 40;
    }

    public static void renderAliasesCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int curY = y + 10;
        int colW = (w - 36) / 2;

        renderCleanInputField(g, font, "Alias (e.g. v)", aliasCommandInput, "aliasCmd", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Target Command (e.g. visit)", aliasActualInput, "aliasAct", x + 12 + colW + 12, curY, colW, mouseX, mouseY);

        curY += 24;
        String addText = editingAliasKey != null ? "§e✔ Save Alias" : "§a+ Add Command Alias";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addText, x + 12, curY, 168, 18, addHover, -1, 0x2210B981, 0x6610B981);

        if (editingAliasKey != null) {
            boolean cancelHover = mouseX >= x + 185 && mouseX <= x + 250 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 185, curY, 65, 18, cancelHover, -1, 0x22EF4444, 0x44EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        if (s.commandAliases == null || s.commandAliases.isEmpty()) {
            g.text(font, "§8No command aliases added yet. (e.g. /v -> /visit)", x + 16, curY + 4, 0xFF94A3B8, false);
        } else {
            List<String> sortedAliases = new ArrayList<>(s.commandAliases.keySet());
            Collections.sort(sortedAliases);
            for (String aliasKey : sortedAliases) {
                String target = s.commandAliases.get(aliasKey);
                int rowY = curY;
                g.fill(x + 12, rowY, x + w - 12, rowY + 20, 0x221E293B);

                g.text(font, "§6/" + aliasKey + " §8-> §b/" + target, x + 18, rowY + 6, -1, false);

                int rx = x + w - 18;
                int delX = rx - 26;
                boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                int editX = delX - 34;
                boolean editHover = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§eEDIT", editX, rowY + 2, 32, 16, editHover, -1, 0x22FFFFFF, 0x44FFFFFF);

                curY += 24;
            }
        }
    }

    public static boolean handleAliasesCardClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int curY = y + 10;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW, 18, "aliasCmd", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 12, curY, colW, 18, "aliasAct", mouseX, mouseY)) return true;

        curY += 24;
        if (mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18) {
            String cleanAlias = aliasCommandInput.trim().startsWith("/") ? aliasCommandInput.trim().substring(1) : aliasCommandInput.trim();
            String cleanTarget = aliasActualInput.trim();
            if (!cleanAlias.isEmpty() && !cleanTarget.isEmpty()) {
                if (s.commandAliases == null) s.commandAliases = new HashMap<>();
                if (editingAliasKey != null) {
                    s.commandAliases.remove(editingAliasKey);
                    editingAliasKey = null;
                }
                s.commandAliases.put(cleanAlias, cleanTarget);
                BomboConfig.save();
                BomboaddonsClient.registerAllAliases();
                aliasCommandInput = "";
                aliasActualInput = "";
                activeFocusedField = null;
                return true;
            }
        }

        if (editingAliasKey != null && mouseX >= x + 185 && mouseX <= x + 250 && mouseY >= curY && mouseY <= curY + 18) {
            editingAliasKey = null;
            aliasCommandInput = "";
            aliasActualInput = "";
            return true;
        }

        curY += 32;
        if (s.commandAliases != null) {
            List<String> sortedAliases = new ArrayList<>(s.commandAliases.keySet());
            Collections.sort(sortedAliases);
            for (String aliasKey : sortedAliases) {
                int rowY = curY;
                int rx = x + w - 18;
                int delX = rx - 26;
                int editX = delX - 34;

                if (mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    s.commandAliases.remove(aliasKey);
                    BomboConfig.save();
                    BomboaddonsClient.registerAllAliases();
                    return true;
                }

                if (mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    editingAliasKey = aliasKey;
                    aliasCommandInput = aliasKey;
                    aliasActualInput = s.commandAliases.get(aliasKey);
                    return true;
                }

                curY += 24;
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 9. PARTICLE HIGHLIGHTS / ESP MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String particleTypeInput = "";
    public static String particleColorInput = "GOLD";

    public static int getParticleHighlightsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        int count = s.particleHighlights != null ? s.particleHighlights.size() : 0;
        return 85 + count * 24 + 40;
    }

    public static void renderParticleHighlightsCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int curY = y + 10;
        int colW = (w - 36) / 2;

        renderCleanInputField(g, font, "Particle Type / ID (e.g. flame, heart, portal)", particleTypeInput, "partType", x + 12, curY, colW + 20, mouseX, mouseY);
        renderColorSelector(g, font, "Color", particleColorInput, x + 12 + colW + 32, curY, colW - 20, mouseX, mouseY);

        curY += 24;
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§a+ Add Particle ESP", x + 12, curY, 168, 18, addHover, -1, 0x2210B981, 0x6610B981);

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        if (s.particleHighlights == null || s.particleHighlights.isEmpty()) {
            g.text(font, "§8No particle ESP filters configured yet. Add one above!", x + 16, curY + 4, 0xFF94A3B8, false);
        } else {
            List<String> pTypes = new ArrayList<>(s.particleHighlights.keySet());
            Collections.sort(pTypes);
            for (String pt : pTypes) {
                BomboConfig.HighlightInfo info = s.particleHighlights.get(pt);
                if (info == null) continue;
                int rowY = curY;
                g.fill(x + 12, rowY, x + w - 12, rowY + 20, 0x221E293B);

                int pColor = info.enabled ? getColorVal(info.color) : 0xFFFF5555;
                g.text(font, pt, x + 18, rowY + 6, pColor, false);
                if (!info.enabled) {
                    int ptW = font.width(pt);
                    g.fill(x + 18, rowY + 10, x + 18 + ptW, rowY + 11, 0xFFFF5555);
                }

                int rightX = x + w - 18;
                int delX = rightX - 26;
                boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                int onX = delX - 38;
                boolean onHover = mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, info.enabled ? "§aON" : "§cOFF", onX, rowY + 2, 36, 16, onHover, info.enabled ? 0xFF10B981 : 0xFF94A3B8, info.enabled ? 0x3310B981 : 0x1AFFFFFF, 0x44FFFFFF);

                int colBx = onX - 60;
                boolean colHover = mouseX >= colBx && mouseX <= colBx + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§f" + info.color + " ▾", colBx, rowY + 2, 56, 16, colHover, -1, 0x22FFFFFF, 0x44FFFFFF);

                curY += 24;
            }
        }
    }

    public static boolean handleParticleHighlightsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int curY = y + 10;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW + 20, 18, "partType", mouseX, mouseY)) return true;

        if (mouseX >= x + 12 + colW + 32 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 18) {
            openColorDropdown(x + 12 + colW + 32, curY + 20, colW - 20, color -> particleColorInput = color);
            return true;
        }

        curY += 24;
        if (mouseX >= x + 12 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18) {
            String pName = particleTypeInput.trim().toLowerCase();
            if (!pName.isEmpty()) {
                if (s.particleHighlights == null) s.particleHighlights = new HashMap<>();
                s.particleHighlights.put(pName, new BomboConfig.HighlightInfo(particleColorInput.toUpperCase(), true, true, false, "", false));
                particleTypeInput = "";
                activeFocusedField = null;
                BomboConfig.save();
                return true;
            }
        }

        curY += 32;
        if (s.particleHighlights != null) {
            List<String> pTypes = new ArrayList<>(s.particleHighlights.keySet());
            Collections.sort(pTypes);
            for (String pt : pTypes) {
                BomboConfig.HighlightInfo info = s.particleHighlights.get(pt);
                if (info == null) continue;
                int rowY = curY;
                int rightX = x + w - 18;
                int delX = rightX - 26;
                int onX = delX - 38;
                int colBx = onX - 60;

                // Right click to toggle
                if (button == 1 && mouseX >= x + 12 && mouseX < colBx && mouseY >= rowY && mouseY <= rowY + 20) {
                    info.enabled = !info.enabled;
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    s.particleHighlights.remove(pt);
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= onX && mouseX <= onX + 36 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    info.enabled = !info.enabled;
                    BomboConfig.save();
                    return true;
                }

                if (mouseX >= colBx && mouseX <= colBx + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    openColorDropdown(colBx, rowY + 20, 56, color -> {
                        info.color = color;
                        BomboConfig.save();
                    });
                    return true;
                }

                curY += 24;
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // 10. CUSTOM SOUNDS & SOUND REPLACEMENTS CARD
    // ---------------------------------------------------------------------------------------------
    public static String soundOrigInput = "";
    public static String soundReplInput = "";

    public static int getCustomSoundsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        int count = s.customSoundReplacements != null ? s.customSoundReplacements.size() : 0;
        return 95 + count * 24 + 40;
    }

    public static void renderCustomSoundsCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        ConfigUITheme.drawCard(g, x, y, w, h, false);

        int curY = y + 10;
        int colW = (w - 36) / 2;

        renderCleanInputField(g, font, "Original Sound (e.g. entity.ender_dragon.growl)", soundOrigInput, "sndOrig", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Replacement (e.g. sound.mp3, entity.cat.purr)", soundReplInput, "sndRepl", x + 12 + colW + 12, curY, colW, mouseX, mouseY);

        curY += 24;
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 160 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§a+ Save / Replace", x + 12, curY, 150, 18, addHover, -1, 0x2210B981, 0x6610B981);

        boolean testHover = mouseX >= x + 168 && mouseX <= x + 230 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "► Test", x + 168, curY, 62, 18, testHover, -1, 0x2200E5FF, 0x4400E5FF);

        boolean folderHover = mouseX >= x + w - 145 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "📁 Open Sound Folder", x + w - 145, curY, 133, 18, folderHover, -1, 0x22FFAA00, 0x44FFAA00);

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, 0x33FFFFFF);
        curY += 6;

        if (s.customSoundReplacements == null || s.customSoundReplacements.isEmpty()) {
            g.text(font, "§8No sound replacements added yet. Drop .mp3, .wav, .ogg into folder or add above!", x + 16, curY + 4, 0xFF94A3B8, false);
        } else {
            List<String> keys = new ArrayList<>(s.customSoundReplacements.keySet());
            Collections.sort(keys);
            for (String orig : keys) {
                String repl = s.customSoundReplacements.get(orig);
                boolean isEnabled = s.disabledCustomSoundReplacements == null || !s.disabledCustomSoundReplacements.contains(orig);
                int rowY = curY;
                g.fill(x + 12, rowY, x + w - 12, rowY + 20, isEnabled ? 0x221E293B : 0x150F172A);

                // Status Indicator / Label
                String statusPrefix = isEnabled ? "§e" : "§8[OFF] §7";
                g.text(font, statusPrefix + orig + " §8-> §a" + repl, x + 18, rowY + 6, isEnabled ? -1 : 0xFF888888, false);

                int rx = x + w - 18;

                // [DEL] button
                int delX = rx - 26;
                boolean delHover = mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§cDEL", delX, rowY + 2, 26, 16, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                // [► Test] button
                int testRowX = delX - 44;
                boolean tHover = mouseX >= testRowX && mouseX <= testRowX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "► Test", testRowX, rowY + 2, 40, 16, tHover, 0xFF00E5FF, 0x2200E5FF, 0x4400E5FF);

                // [Edit] button
                int editX = testRowX - 38;
                boolean editHover = mouseX >= editX && mouseX <= editX + 34 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                ConfigUITheme.drawPillButton(g, font, "§bEdit", editX, rowY + 2, 34, 16, editHover, 0xFF00E5FF, 0x2238BDF8, 0x4438BDF8);

                // [ON/OFF] Toggle button
                int togX = editX - 44;
                boolean togHover = mouseX >= togX && mouseX <= togX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 18;
                String togText = isEnabled ? "§aON" : "§cOFF";
                ConfigUITheme.drawPillButton(g, font, togText, togX, rowY + 2, 40, 16, togHover, isEnabled ? 0xFF10B981 : 0xFFEF4444, isEnabled ? 0x2210B981 : 0x22EF4444, isEnabled ? 0x5510B981 : 0x55EF4444);

                curY += 24;
            }
        }
    }

    public static boolean handleCustomSoundsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        int curY = y + 10;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW, 18, "sndOrig", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 12, curY, colW, 18, "sndRepl", mouseX, mouseY)) return true;

        curY += 24;
        // + Save / Replace
        if (mouseX >= x + 12 && mouseX <= x + 160 && mouseY >= curY && mouseY <= curY + 18) {
            String o = soundOrigInput.trim().toLowerCase();
            String r = soundReplInput.trim();
            if (!o.isEmpty() && !r.isEmpty()) {
                if (s.customSoundReplacements == null) s.customSoundReplacements = new HashMap<>();
                s.customSoundReplacements.put(o, r);
                BomboConfig.save();
                soundOrigInput = "";
                soundReplInput = "";
                activeFocusedField = null;
                return true;
            }
        }

        // ► Test button in form
        if (mouseX >= x + 168 && mouseX <= x + 230 && mouseY >= curY && mouseY <= curY + 18) {
            String r = !soundReplInput.trim().isEmpty() ? soundReplInput.trim() : soundOrigInput.trim();
            if (!r.isEmpty()) {
                me.bombo.bomboaddons.features.sounds.CustomSoundManager.playCustomOrVanillaSound(r, 1.0f, 1.0f);
                return true;
            }
        }

        // 📁 Open Sound Folder
        if (mouseX >= x + w - 145 && mouseX <= x + w - 12 && mouseY >= curY && mouseY <= curY + 18) {
            try {
                java.io.File dir = me.bombo.bomboaddons.features.sounds.CustomSoundManager.getSoundsDirectory();
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", dir.getAbsolutePath()).start();
                } else {
                    new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start();
                }
            } catch (Exception ignored) {}
            return true;
        }

        curY += 32;
        if (s.customSoundReplacements != null) {
            List<String> keys = new ArrayList<>(s.customSoundReplacements.keySet());
            Collections.sort(keys);
            for (String orig : keys) {
                String repl = s.customSoundReplacements.get(orig);
                int rowY = curY;
                int rx = x + w - 18;
                int delX = rx - 26;
                int testRowX = delX - 44;
                int editX = testRowX - 38;
                int togX = editX - 44;

                // [DEL]
                if (mouseX >= delX && mouseX <= delX + 26 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    s.customSoundReplacements.remove(orig);
                    if (s.disabledCustomSoundReplacements != null) {
                        s.disabledCustomSoundReplacements.remove(orig);
                    }
                    BomboConfig.save();
                    return true;
                }

                // [► Test]
                if (mouseX >= testRowX && mouseX <= testRowX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    me.bombo.bomboaddons.features.sounds.CustomSoundManager.playCustomOrVanillaSound(repl, 1.0f, 1.0f);
                    return true;
                }

                // [Edit]
                if (mouseX >= editX && mouseX <= editX + 34 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    soundOrigInput = orig;
                    soundReplInput = repl;
                    activeFocusedField = "sndRepl";
                    cursorPosition = soundReplInput.length();
                    return true;
                }

                // [ON/OFF Toggle]
                if (mouseX >= togX && mouseX <= togX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    if (s.disabledCustomSoundReplacements == null) {
                        s.disabledCustomSoundReplacements = new java.util.HashSet<>();
                    }
                    if (s.disabledCustomSoundReplacements.contains(orig)) {
                        s.disabledCustomSoundReplacements.remove(orig);
                    } else {
                        s.disabledCustomSoundReplacements.add(orig);
                    }
                    BomboConfig.save();
                    return true;
                }

                curY += 24;
            }
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // CUSTOM WAYPOINTS MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String wpNameInput = "";
    public static String wpCoordsInput = "";
    public static String wpIslandInput = "";
    public static String wpColorInput = "Aqua";
    public static boolean wpThroughWalls = true;
    public static boolean wpBeacon = true;
    public static int editingWpIndex = -1;

    public static int getWaypointsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.CustomWaypoint> list = s.customWaypoints != null ? s.customWaypoints.get(s.activeProfile) : null;
        int count = list != null ? list.size() : 0;
        return 125 + Math.max(1, count) * 26;
    }

    public static void renderWaypointsManager(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        int curY = y + 8;
        g.text(font, "§6§lCUSTOM WORLD WAYPOINTS (" + BomboConfig.get().activeProfile + ")", x + 12, curY, ConfigUITheme.ACCENT_GOLD, false);
        curY += 18;

        int colW = (w - 36) / 2;

        // Form Row 1: Name and Coords + [📍 Current]
        renderCleanInputField(g, font, "Waypoint Name", wpNameInput, "wpName", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Coords: X Y Z (e.g. 10 70 -50)", wpCoordsInput, "wpCoords", x + 12 + colW + 12, curY, colW - 70, mouseX, mouseY);

        // 📍 Current button
        int curBtnX = x + 12 + colW + 12 + colW - 65;
        boolean curHover = mouseX >= curBtnX && mouseX <= curBtnX + 65 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§e📍 Current", curBtnX, curY, 65, 18, curHover, -1, 0x22FFAA00, 0x55FFAA00);

        curY += 24;

        // Form Row 2: Island, Color Dropdown, Through Walls, Beacon
        int subColW = (w - 36) / 4;
        renderCleanInputField(g, font, "Island Filter (blank=all)", wpIslandInput, "wpIsland", x + 12, curY, subColW, mouseX, mouseY);
        renderColorSelector(g, font, "Color", wpColorInput, x + 12 + subColW + 6, curY, subColW - 10, mouseX, mouseY);
        renderInlineToggle(g, font, "Through Walls", wpThroughWalls, x + 12 + (subColW + 6) * 2, curY, subColW - 10, mouseX, mouseY);
        renderInlineToggle(g, font, "Beacon Beam", wpBeacon, x + 12 + (subColW + 6) * 3, curY, subColW - 10, mouseX, mouseY);

        curY += 24;

        // Form Row 3: Action Buttons
        String addBtnText = editingWpIndex >= 0 ? "§a✔ Save Changes" : "§b+ Add Waypoint";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 130 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 118, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        if (editingWpIndex >= 0) {
            boolean canHover = mouseX >= x + 136 && mouseX <= x + 200 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 136, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        // Waypoints List
        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.CustomWaypoint> list = s.customWaypoints != null ? s.customWaypoints.get(s.activeProfile) : null;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No waypoints created for profile '" + s.activeProfile + "'. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CustomWaypoint wp = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;

            int delX = rx - 24;
            int editX = delX - 30;
            int onX = editX - 36;
            int bcnX = onX - 44;
            int colX = bcnX - 58;

            int wpColHex = BomboRenderUtils.colorNameToHex(wp.color != null ? wp.color : "Aqua");
            g.text(font, (wp.enabled ? "§f" : "§c§m") + (wp.name != null && !wp.name.isEmpty() ? wp.name : "Waypoint"), x + 16, rowY + 4, wp.enabled ? wpColHex : 0xFFEF4444, false);

            String coordStr = String.format(java.util.Locale.US, "§7(%d, %d, %d)%s", (int)Math.floor(wp.x), (int)Math.floor(wp.y), (int)Math.floor(wp.z),
                    (wp.requiredIsland != null && !wp.requiredIsland.isEmpty() ? " §8[" + wp.requiredIsland + "]" : ""));
            g.text(font, coordStr, x + 130, rowY + 4, ConfigUITheme.getTextMuted(), false);

            // [Color] ▾
            boolean colHover = mouseX >= colX && mouseX <= colX + 54 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§7" + (wp.color != null ? wp.color : "Aqua") + " ▾", colX, rowY, 54, 18, colHover, wpColHex, 0x22FFFFFF, 0x44FFFFFF);

            // [BCN]
            boolean bcnHover = mouseX >= bcnX && mouseX <= bcnX + 40 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, wp.showBeacon ? "§bBCN" : "§8BCN", bcnX, rowY, 40, 18, bcnHover,
                    wp.showBeacon ? 0xFF00E5FF : 0xFF64748B, wp.showBeacon ? 0x3300E5FF : 0x1AFFFFFF, 0x4400E5FF);

            // [ON/OFF]
            boolean onHover = mouseX >= onX && mouseX <= onX + 32 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, wp.enabled ? "§aON" : "§cOFF", onX, rowY, 32, 18, onHover,
                    wp.enabled ? 0xFF10B981 : 0xFFEF4444, wp.enabled ? 0x3310B981 : 0x22EF4444, 0x6610B981);

            // [EDIT]
            boolean editHover = mouseX >= editX && mouseX <= editX + 26 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§e✎", editX, rowY, 26, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            // [DEL]
            boolean delHover = mouseX >= delX && mouseX <= delX + 22 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY, 22, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleWaypointsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.customWaypoints == null) s.customWaypoints = new HashMap<>();
        List<BomboConfig.CustomWaypoint> list = s.customWaypoints.computeIfAbsent(s.activeProfile, k -> new ArrayList<>());

        int curY = y + 26;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW, 18, "wpName", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 12, curY, colW - 70, 18, "wpCoords", mouseX, mouseY)) return true;

        // 📍 Current button
        int curBtnX = x + 12 + colW + 12 + colW - 65;
        if (mouseX >= curBtnX && mouseX <= curBtnX + 65 && mouseY >= curY && mouseY <= curY + 18) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int px = (int) Math.floor(mc.player.getX());
                int py = (int) Math.floor(mc.player.getY());
                int pz = (int) Math.floor(mc.player.getZ());
                wpCoordsInput = px + " " + py + " " + pz;
            }
            return true;
        }

        curY += 24;
        int subColW = (w - 36) / 4;
        if (checkFieldClick(x + 12, curY, subColW, 18, "wpIsland", mouseX, mouseY)) return true;

        // Color selector
        if (mouseX >= x + 12 + subColW + 6 && mouseX <= x + 12 + (subColW + 6) + subColW - 10 && mouseY >= curY && mouseY <= curY + 18) {
            openColorDropdown(x + 12 + subColW + 6, curY + 20, subColW - 10, col -> wpColorInput = col);
            return true;
        }

        // Through walls toggle
        if (mouseX >= x + 12 + (subColW + 6) * 2 && mouseX <= x + 12 + (subColW + 6) * 2 + subColW - 10 && mouseY >= curY && mouseY <= curY + 18) {
            wpThroughWalls = !wpThroughWalls;
            return true;
        }

        // Beacon toggle
        if (mouseX >= x + 12 + (subColW + 6) * 3 && mouseX <= x + 12 + (subColW + 6) * 3 + subColW - 10 && mouseY >= curY && mouseY <= curY + 18) {
            wpBeacon = !wpBeacon;
            return true;
        }

        curY += 24;

        // Save / Add button
        if (mouseX >= x + 12 && mouseX <= x + 130 && mouseY >= curY && mouseY <= curY + 18) {
            if (!wpCoordsInput.trim().isEmpty()) {
                String[] parts = wpCoordsInput.trim().replace(",", " ").replaceAll("\\s+", " ").split(" ");
                double px = 0, py = 64, pz = 0;
                try {
                    if (parts.length >= 3) {
                        px = Double.parseDouble(parts[0]);
                        py = Double.parseDouble(parts[1]);
                        pz = Double.parseDouble(parts[2]);
                    }
                } catch (Exception ignored) {}

                String wpName = !wpNameInput.trim().isEmpty() ? wpNameInput.trim() : "Waypoint";
                if (editingWpIndex >= 0 && editingWpIndex < list.size()) {
                    BomboConfig.CustomWaypoint wp = list.get(editingWpIndex);
                    wp.name = wpName;
                    wp.x = px;
                    wp.y = py;
                    wp.z = pz;
                    wp.requiredIsland = wpIslandInput.trim();
                    wp.color = wpColorInput;
                    wp.showThroughWalls = wpThroughWalls;
                    wp.showBeacon = wpBeacon;
                } else {
                    BomboConfig.CustomWaypoint wp = new BomboConfig.CustomWaypoint(wpName, px, py, pz, wpIslandInput.trim(), wpThroughWalls, wpBeacon, wpColorInput, "General");
                    list.add(wp);
                }
                editingWpIndex = -1;
                wpNameInput = "";
                wpCoordsInput = "";
                wpIslandInput = "";
                BomboConfig.save();
                return true;
            }
        }

        if (editingWpIndex >= 0 && mouseX >= x + 136 && mouseX <= x + 200 && mouseY >= curY && mouseY <= curY + 18) {
            editingWpIndex = -1;
            wpNameInput = "";
            wpCoordsInput = "";
            wpIslandInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CustomWaypoint wp = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 30;
            int onX = editX - 36;
            int bcnX = onX - 44;
            int colX = bcnX - 58;

            int finalI = i;
            if (mouseX >= colX && mouseX <= colX + 54 && mouseY >= rowY && mouseY <= rowY + 18) {
                openColorDropdown(colX, rowY + 20, 54, color -> {
                    wp.color = color;
                    BomboConfig.save();
                });
                return true;
            }

            if (mouseX >= bcnX && mouseX <= bcnX + 40 && mouseY >= rowY && mouseY <= rowY + 18) {
                wp.showBeacon = !wp.showBeacon;
                BomboConfig.save();
                return true;
            }

            if (mouseX >= onX && mouseX <= onX + 32 && mouseY >= rowY && mouseY <= rowY + 18) {
                wp.enabled = !wp.enabled;
                BomboConfig.save();
                return true;
            }

            if (mouseX >= editX && mouseX <= editX + 26 && mouseY >= rowY && mouseY <= rowY + 18) {
                editingWpIndex = finalI;
                wpNameInput = wp.name != null ? wp.name : "";
                wpCoordsInput = ((int)Math.floor(wp.x)) + " " + ((int)Math.floor(wp.y)) + " " + ((int)Math.floor(wp.z));
                wpIslandInput = wp.requiredIsland != null ? wp.requiredIsland : "";
                wpColorInput = wp.color != null ? wp.color : "Aqua";
                wpThroughWalls = wp.showThroughWalls;
                wpBeacon = wp.showBeacon;
                return true;
            }

            if (mouseX >= delX && mouseX <= delX + 22 && mouseY >= rowY && mouseY <= rowY + 18) {
                BomboConfig.CustomWaypoint removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingWpIndex == finalI) editingWpIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // ORDERED WAYPOINTS MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    // 13. ORDERED WAYPOINTS & MULTI-ROUTE CATEGORIES MANAGER
    // ---------------------------------------------------------------------------------------------
    public static boolean isExportDropdownOpen = false;
    public static int exportDropdownX = -1;
    public static int exportDropdownY = -1;
    public static boolean isDraggingWaypoint = false;
    public static int draggedWaypointIndex = -1;
    public static String draggingRouteName = null;
    public static String orderedWaypointsSearchQuery = "";

    public static int getOrderedWaypointsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || s.orderedRoutes == null || s.orderedRoutes.isEmpty()) {
            return 110;
        }
        int h = 95;
        String filter = orderedWaypointsSearchQuery.toLowerCase().trim();
        for (Map.Entry<String, BomboConfig.OrderedRoute> entry : s.orderedRoutes.entrySet()) {
            BomboConfig.OrderedRoute r = entry.getValue();
            h += 24; // Category header
            if (!r.collapsed && r.waypoints != null) {
                for (BomboConfig.OrderedWaypointData wp : r.waypoints) {
                    if (filter.isEmpty() || (wp.name != null && wp.name.toLowerCase().contains(filter)) || entry.getKey().toLowerCase().contains(filter)) {
                        h += 24;
                    }
                }
            }
        }
        return h + 30;
    }

    public static void renderOrderedWaypointsManager(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        int curY = y + 8;
        g.text(font, "§6§lORDERED WAYPOINTS & ROUTE CATEGORIES", x + 12, curY, ConfigUITheme.ACCENT_GOLD, false);

        BomboConfig.Settings s = BomboConfig.get();
        int btnY = curY - 2;
        int btnW = 75;

        // [+ New Route Category]
        boolean newRouteHover = mouseX >= x + w - 370 && mouseX <= x + w - 370 + 95 && mouseY >= btnY && mouseY <= btnY + 18;
        ConfigUITheme.drawPillButton(g, font, "§a+ New Category", x + w - 370, btnY, 95, 18, newRouteHover, -1, 0x3310B981, 0x6610B981);

        // Import Button
        boolean impHover = mouseX >= x + w - 265 && mouseX <= x + w - 265 + btnW && mouseY >= btnY && mouseY <= btnY + 18;
        ConfigUITheme.drawPillButton(g, font, "§a📥 Import", x + w - 265, btnY, btnW, 18, impHover, -1, 0x3310B981, 0x6610B981);

        // Export Button
        boolean expHover = mouseX >= x + w - 180 && mouseX <= x + w - 180 + btnW && mouseY >= btnY && mouseY <= btnY + 18;
        ConfigUITheme.drawPillButton(g, font, "§e📤 Export ▾", x + w - 180, btnY, btnW, 18, expHover, -1, 0x33FFAA00, 0x66FFAA00);

        // Clear All Button
        boolean clrHover = mouseX >= x + w - 95 && mouseX <= x + w - 12 && mouseY >= btnY && mouseY <= btnY + 18;
        ConfigUITheme.drawPillButton(g, font, "§c🗑 Clear", x + w - 95, btnY, 83, 18, clrHover, -1, 0x33EF4444, 0x66EF4444);

        curY += 24;
        // Search bar
        renderCleanInputField(g, font, "Search categories and waypoints...", orderedWaypointsSearchQuery, "ordSearch", x + 12, curY, w - 24, mouseX, mouseY);

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        if (s == null || s.orderedRoutes == null || s.orderedRoutes.isEmpty()) {
            g.text(font, "§8No ordered route categories exist. Click '§a+ New Category§8' or '§a📥 Import§8' above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        String filter = orderedWaypointsSearchQuery.toLowerCase().trim();
        List<String> routeNames = new ArrayList<>(s.orderedRoutes.keySet());

        for (String routeName : routeNames) {
            BomboConfig.OrderedRoute route = s.orderedRoutes.get(routeName);
            if (route == null) continue;

            int catHeaderY = curY;
            g.fill(x + 12, catHeaderY, x + w - 12, catHeaderY + 20, 0x441E293B);

            // Collapse Arrow Button
            boolean arrowHover = mouseX >= x + 16 && mouseX <= x + 38 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18;
            ConfigUITheme.drawPillButton(g, font, route.collapsed ? "▶" : "▼", x + 16, catHeaderY + 2, 22, 16, arrowHover, 0xFF00E5FF, 0x2200E5FF, 0x4400E5FF);

            int wpCount = route.waypoints != null ? route.waypoints.size() : 0;
            String catColorStr = route.color != null ? route.color : "Aqua";
            int catHex = 0xFF000000 | (BomboRenderUtils.colorNameToHex(catColorStr) & 0x00FFFFFF);

            g.text(font, "§6§l" + routeName + " §7(" + wpCount + " waypoints)", x + 44, catHeaderY + 6, route.enabled ? 0xFFFFFFFF : 0xFF94A3B8, false);

            int rx = x + w - 18;

            // Delete Category [DEL]
            int delCatX = rx - 28;
            boolean delCatHover = mouseX >= delCatX && mouseX <= delCatX + 28 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cDEL", delCatX, catHeaderY + 2, 28, 16, delCatHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

            // Add Waypoint to Category [+ Add]
            int addWpX = delCatX - 44;
            boolean addWpHover = mouseX >= addWpX && mouseX <= addWpX + 40 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18;
            ConfigUITheme.drawPillButton(g, font, "§a+ Add", addWpX, catHeaderY + 2, 40, 16, addWpHover, 0xFF10B981, 0x2210B981, 0x4410B981);

            // Toggle All Waypoints in Category [ALL]
            int toggleAllX = addWpX - 40;
            boolean toggleAllHover = mouseX >= toggleAllX && mouseX <= toggleAllX + 36 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18;
            ConfigUITheme.drawPillButton(g, font, route.enabled ? "§aALL" : "§cOFF", toggleAllX, catHeaderY + 2, 36, 16, toggleAllHover, route.enabled ? 0xFF10B981 : 0xFF94A3B8, route.enabled ? 0x2210B981 : 0x1AFFFFFF, 0x44FFFFFF);

            // Category Color Picker Button [Color ▾]
            int catColX = toggleAllX - 64;
            boolean catColHover = mouseX >= catColX && mouseX <= catColX + 60 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18;
            ConfigUITheme.drawPillButton(g, font, "§f" + catColorStr + " ▾", catColX, catHeaderY + 2, 60, 16, catColHover, catHex, 0x22FFFFFF, 0x44FFFFFF);

            curY += 22;

            if (!route.collapsed && route.waypoints != null) {
                // Live drag reordering within this category
                if (isDraggingWaypoint && routeName.equals(draggingRouteName) && draggedWaypointIndex >= 0 && draggedWaypointIndex < route.waypoints.size()) {
                    int hoveredTargetIdx = Math.max(0, Math.min(route.waypoints.size() - 1, (mouseY - curY) / 24));
                    if (hoveredTargetIdx != draggedWaypointIndex) {
                        OrderedWaypoints.reorderWaypointInRoute(routeName, draggedWaypointIndex, hoveredTargetIdx);
                        draggedWaypointIndex = hoveredTargetIdx;
                    }
                }

                for (int i = 0; i < route.waypoints.size(); i++) {
                    BomboConfig.OrderedWaypointData wp = route.waypoints.get(i);
                    if (!filter.isEmpty() && (wp.name == null || !wp.name.toLowerCase().contains(filter)) && !routeName.toLowerCase().contains(filter)) {
                        continue;
                    }

                    int rowY = curY;
                    boolean isDragged = (isDraggingWaypoint && routeName.equals(draggingRouteName) && draggedWaypointIndex == i);

                    int rowBg = isDragged ? 0x6600E5FF : 0x1A0F172A;
                    g.fill(x + 12, rowY, x + w - 12, rowY + 22, rowBg);
                    if (isDragged) {
                        g.outline(x + 12, rowY, w - 24, 22, ConfigUITheme.getAccentColor());
                    }

                    // Drag Grip Handle [ ☰ ]
                    int gripX = x + 16;
                    boolean gripHover = mouseX >= gripX - 2 && mouseX <= gripX + 16 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
                    g.text(font, "§7☰", gripX, rowY + 7, gripHover || isDragged ? 0xFF00E5FF : 0xFF64748B, false);

                    // Toggle Button [ON] / [OFF]
                    int onBtnX = gripX + 20;
                    boolean onHover = mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
                    ConfigUITheme.drawPillButton(g, font, wp.enabled ? "§aON" : "§cOFF", onBtnX, rowY + 2, 32, 18, onHover, wp.enabled ? 0xFF10B981 : 0xFF94A3B8, wp.enabled ? 0x2210B981 : 0x1AFFFFFF, 0x44FFFFFF);

                    // Waypoint Name & Coords
                    int textX = onBtnX + 38;
                    int wpColHex = 0xFF000000 | (BomboRenderUtils.colorNameToHex(wp.color != null ? wp.color : "Aqua") & 0x00FFFFFF);
                    String prefix = "§7#" + (i + 1) + " ";
                    g.text(font, prefix + (wp.name != null ? wp.name : "Waypoint"), textX, rowY + 7, wp.enabled ? 0xFFFFFFFF : 0xFF64748B, false);

                    String coordStr = String.format(java.util.Locale.US, "§8(%d, %d, %d)", (int)Math.floor(wp.x), (int)Math.floor(wp.y), (int)Math.floor(wp.z));
                    g.text(font, coordStr, textX + 140, rowY + 7, ConfigUITheme.getTextMuted(), false);

                    // Row Actions (Right aligned)
                    int delX = rx - 24;
                    int colX = delX - 60;

                    // [Color ▾]
                    boolean colHover = mouseX >= colX && mouseX <= colX + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
                    ConfigUITheme.drawPillButton(g, font, (wp.color != null ? wp.color : "Aqua") + " ▾", colX, rowY + 2, 56, 18, colHover, wpColHex, 0x22FFFFFF, 0x44FFFFFF);

                    // [DEL ✕]
                    boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
                    ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY + 2, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

                    curY += 24;
                }
            }
            curY += 4;
        }

        // Render Export Popup Menu if open
        if (isExportDropdownOpen) {
            int popX = exportDropdownX > 0 ? exportDropdownX : x + w - 180;
            int popY = exportDropdownY > 0 ? exportDropdownY : y + 26;
            int popW = 160;
            int popH = 64;

            g.fill(popX, popY, popX + popW, popY + popH, 0xFA0F172A);
            g.outline(popX, popY, popW, popH, ConfigUITheme.getAccentColor());

            String[] formats = new String[]{"SkyHanni (JSON)", "Skyblocker (V1)", "Waypointer (WPL)"};
            for (int fi = 0; fi < formats.length; fi++) {
                int fy = popY + 4 + fi * 19;
                boolean fHover = mouseX >= popX + 4 && mouseX <= popX + popW - 4 && mouseY >= fy && mouseY <= fy + 17;
                if (fHover) g.fill(popX + 4, fy, popX + popW - 4, fy + 17, 0x3300E5FF);
                g.text(font, "§f" + formats[fi], popX + 8, fy + 4, fHover ? 0xFF00E5FF : 0xFFCBD5E1, false);
            }
        }
    }

    public static boolean handleOrderedWaypointsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        int curY = y + 8;
        int btnY = curY - 2;
        int btnW = 75;

        // Export Dropdown Click
        if (isExportDropdownOpen) {
            int popX = exportDropdownX > 0 ? exportDropdownX : x + w - 180;
            int popY = exportDropdownY > 0 ? exportDropdownY : y + 26;
            int popW = 160;
            if (mouseX >= popX && mouseX <= popX + popW && mouseY >= popY && mouseY <= popY + 64) {
                int clickedIdx = (mouseY - popY - 4) / 19;
                if (clickedIdx == 0) {
                    OrderedWaypoints.exportSkyHanniJson();
                    if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3Bombo§8]§r §aExported SkyHanni JSON to clipboard!"));
                } else if (clickedIdx == 1) {
                    OrderedWaypoints.exportSkyblockerV1();
                    if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3Bombo§8]§r §aExported Skyblocker V1 format to clipboard!"));
                } else if (clickedIdx == 2) {
                    OrderedWaypoints.exportWaypointerWpl();
                    if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3Bombo§8]§r §aExported Waypointer WPL format to clipboard!"));
                }
                isExportDropdownOpen = false;
                return true;
            } else {
                isExportDropdownOpen = false;
            }
        }

        // [+ New Category]
        if (mouseX >= x + w - 370 && mouseX <= x + w - 370 + 95 && mouseY >= btnY && mouseY <= btnY + 18) {
            BomboConfig.Settings s = BomboConfig.get();
            int count = (s != null && s.orderedRoutes != null) ? s.orderedRoutes.size() + 1 : 1;
            String newName = "Category " + count;
            OrderedWaypoints.createRoute(newName);
            return true;
        }

        // Import Clipboard
        if (mouseX >= x + w - 265 && mouseX <= x + w - 265 + btnW && mouseY >= btnY && mouseY <= btnY + 18) {
            try {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    int count = OrderedWaypoints.importWaypointsFromClipboard(clip);
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3Bombo§8]§r §aImported §e" + count + "§a ordered route waypoints!"));
                    }
                }
            } catch (Exception ignored) {}
            return true;
        }

        // Export Dropdown Trigger
        if (mouseX >= x + w - 180 && mouseX <= x + w - 180 + btnW && mouseY >= btnY && mouseY <= btnY + 18) {
            exportDropdownX = x + w - 180;
            exportDropdownY = btnY + 20;
            isExportDropdownOpen = !isExportDropdownOpen;
            return true;
        }

        // Clear All
        if (mouseX >= x + w - 95 && mouseX <= x + w - 12 && mouseY >= btnY && mouseY <= btnY + 18) {
            OrderedWaypoints.clear();
            return true;
        }

        curY += 24;
        if (checkFieldClick(x + 12, curY, w - 24, 18, "ordSearch", mouseX, mouseY)) return true;

        curY += 32;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || s.orderedRoutes == null) return false;

        String filter = orderedWaypointsSearchQuery.toLowerCase().trim();
        List<String> routeNames = new ArrayList<>(s.orderedRoutes.keySet());

        for (String routeName : routeNames) {
            BomboConfig.OrderedRoute route = s.orderedRoutes.get(routeName);
            if (route == null) continue;

            int catHeaderY = curY;
            int rx = x + w - 18;
            int delCatX = rx - 28;
            int addWpX = delCatX - 44;
            int toggleAllX = addWpX - 40;
            int catColX = toggleAllX - 64;

            // Collapse Arrow Button
            if (mouseX >= x + 16 && mouseX <= x + 38 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18) {
                OrderedWaypoints.toggleRouteCollapsed(routeName);
                return true;
            }

            // Delete Category [DEL]
            if (mouseX >= delCatX && mouseX <= delCatX + 28 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18) {
                OrderedWaypoints.deleteRoute(routeName);
                return true;
            }

            // Add Waypoint [+ Add]
            if (mouseX >= addWpX && mouseX <= addWpX + 40 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    net.minecraft.world.phys.Vec3 pos = mc.player.position();
                    OrderedWaypoints.addWaypointToRoute(routeName, pos, "Waypoint " + ((route.waypoints != null ? route.waypoints.size() : 0) + 1), route.color != null ? route.color : "Aqua");
                }
                return true;
            }

            // Toggle All [ALL]
            if (mouseX >= toggleAllX && mouseX <= toggleAllX + 36 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18) {
                OrderedWaypoints.toggleRouteEnabled(routeName);
                return true;
            }

            // Category Color [Color ▾]
            if (mouseX >= catColX && mouseX <= catColX + 60 && mouseY >= catHeaderY + 2 && mouseY <= catHeaderY + 18) {
                openColorDropdown(catColX, catHeaderY + 20, 60, color -> {
                    OrderedWaypoints.setRouteColor(routeName, color);
                });
                return true;
            }

            curY += 22;

            if (!route.collapsed && route.waypoints != null) {
                for (int i = 0; i < route.waypoints.size(); i++) {
                    BomboConfig.OrderedWaypointData wp = route.waypoints.get(i);
                    if (!filter.isEmpty() && (wp.name == null || !wp.name.toLowerCase().contains(filter)) && !routeName.toLowerCase().contains(filter)) {
                        continue;
                    }

                    int rowY = curY;
                    int gripX = x + 16;
                    int onBtnX = gripX + 20;
                    int delX = rx - 24;
                    int colX = delX - 60;
                    int finalI = i;

                    // Drag Grip Handle
                    if (mouseX >= gripX - 4 && mouseX <= gripX + 18 && mouseY >= rowY && mouseY <= rowY + 22) {
                        isDraggingWaypoint = true;
                        draggedWaypointIndex = i;
                        draggingRouteName = routeName;
                        return true;
                    }

                    // Toggle [ON] / [OFF]
                    if (mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                        OrderedWaypoints.toggleWaypointInRoute(routeName, finalI);
                        return true;
                    }

                    // [Color ▾]
                    if (mouseX >= colX && mouseX <= colX + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                        openColorDropdown(colX, rowY + 20, 56, color -> {
                            OrderedWaypoints.setWaypointColorInRoute(routeName, finalI, color);
                        });
                        return true;
                    }

                    // [DEL ✕]
                    if (mouseX >= delX - 2 && mouseX <= delX + 26 && mouseY >= rowY && mouseY <= rowY + 22) {
                        OrderedWaypoints.removeWaypointFromRoute(routeName, finalI);
                        return true;
                    }

                    curY += 24;
                }
            }
            curY += 4;
        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // COORD BINDS MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String cbCmdInput = "";
    public static String cbCoordsInput = "";
    public static String cbIslandInput = "";
    public static String cbRadiusInput = "";
    public static boolean cbShowWp = true;
    public static String cbMinDelayInput = "";
    public static String cbMaxDelayInput = "";
    public static String cbCooldownInput = "";
    public static int editingCbIndex = -1;

    public static int getCoordBindsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.CoordBind> list = s.coordBinds != null ? s.coordBinds.get(s.activeProfile) : null;
        int count = list != null ? list.size() : 0;
        return 125 + Math.max(1, count) * 26;
    }

    public static void renderCoordBindsManager(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        int curY = y + 8;
        g.text(font, "§6§lCOORDINATE COMMAND BINDS (" + BomboConfig.get().activeProfile + ")", x + 12, curY, ConfigUITheme.ACCENT_GOLD, false);
        curY += 18;

        int colW = (w - 36) / 2;
        renderCleanInputField(g, font, "Command (e.g. /warp hub)", cbCmdInput, "cbCmd", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Coords: X Y Z", cbCoordsInput, "cbCoords", x + 12 + colW + 12, curY, colW - 70, mouseX, mouseY);

        int curBtnX = x + 12 + colW + 12 + colW - 65;
        boolean curHover = mouseX >= curBtnX && mouseX <= curBtnX + 65 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§e📍 Current", curBtnX, curY, 65, 18, curHover, -1, 0x22FFAA00, 0x55FFAA00);

        curY += 24;

        int subColW = (w - 36) / 6;
        renderCleanInputField(g, font, "Island Filter", cbIslandInput, "cbIsland", x + 12, curY, subColW, mouseX, mouseY);
        renderCleanInputField(g, font, "Radius (3.0)", cbRadiusInput, "cbRadius", x + 12 + subColW + 6, curY, subColW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Min Delay (s)", cbMinDelayInput, "cbMinDelay", x + 12 + (subColW + 6) * 2, curY, subColW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Max Delay (s)", cbMaxDelayInput, "cbMaxDelay", x + 12 + (subColW + 6) * 3, curY, subColW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Cooldown s (3.0)", cbCooldownInput, "cbCooldown", x + 12 + (subColW + 6) * 4, curY, subColW - 6, mouseX, mouseY);
        renderInlineToggle(g, font, "Waypoint", cbShowWp, x + 12 + (subColW + 6) * 5, curY, subColW - 6, mouseX, mouseY);

        curY += 24;

        String addBtnText = editingCbIndex >= 0 ? "§a✔ Save Coord Bind" : "§b+ Add Coord Bind";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 130 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 126, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        if (editingCbIndex >= 0) {
            boolean canHover = mouseX >= x + 144 && mouseX <= x + 208 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 144, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.CoordBind> list = s.coordBinds != null ? s.coordBinds.get(s.activeProfile) : null;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No coordinate binds configured for profile '" + s.activeProfile + "'. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CoordBind cb = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 26;
            int editX = delX - 32;
            int onX = editX - 38;

            g.text(font, (cb.enabled ? "§e" : "§c§m") + cb.command, x + 16, rowY + 4, cb.enabled ? 0xFFFFAA00 : 0xFFEF4444, false);
            String delayStr = (cb.maxDelay > 0 || cb.minDelay > 0) ? String.format(java.util.Locale.US, " d=%.1f-%.1fs", cb.minDelay, cb.maxDelay) : "";
            String details = String.format(java.util.Locale.US, "§7at (%d, %d, %d) r=%.1f cd=%.1fs%s%s", (int)Math.floor(cb.x), (int)Math.floor(cb.y), (int)Math.floor(cb.z), cb.radius, cb.cooldownSeconds, delayStr, (cb.requiredIsland != null && !cb.requiredIsland.isEmpty() ? " §8[" + cb.requiredIsland + "]" : ""));
            g.text(font, details, x + 160, rowY + 4, ConfigUITheme.getTextMuted(), false);

            boolean onHover = mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, cb.enabled ? "§aON" : "§cOFF", onX, rowY, 34, 18, onHover,
                    cb.enabled ? 0xFF10B981 : 0xFFEF4444, cb.enabled ? 0x3310B981 : 0x22EF4444, 0x6610B981);

            boolean editHover = mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§e✎", editX, rowY, 28, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleCoordBindsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.coordBinds == null) s.coordBinds = new HashMap<>();
        List<BomboConfig.CoordBind> list = s.coordBinds.computeIfAbsent(s.activeProfile, k -> new ArrayList<>());

        int curY = y + 26;
        int colW = (w - 36) / 2;

        if (checkFieldClick(x + 12, curY, colW, 18, "cbCmd", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 12, curY, colW - 70, 18, "cbCoords", mouseX, mouseY)) return true;

        int curBtnX = x + 12 + colW + 12 + colW - 65;
        if (mouseX >= curBtnX && mouseX <= curBtnX + 65 && mouseY >= curY && mouseY <= curY + 18) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int px = (int) Math.floor(mc.player.getX());
                int py = (int) Math.floor(mc.player.getY());
                int pz = (int) Math.floor(mc.player.getZ());
                cbCoordsInput = px + " " + py + " " + pz;
            }
            return true;
        }

        curY += 24;
        int subColW = (w - 36) / 6;
        if (checkFieldClick(x + 12, curY, subColW, 18, "cbIsland", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + subColW + 6, curY, subColW - 6, 18, "cbRadius", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (subColW + 6) * 2, curY, subColW - 6, 18, "cbMinDelay", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (subColW + 6) * 3, curY, subColW - 6, 18, "cbMaxDelay", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (subColW + 6) * 4, curY, subColW - 6, 18, "cbCooldown", mouseX, mouseY)) return true;

        if (mouseX >= x + 12 + (subColW + 6) * 5 && mouseX <= x + 12 + (subColW + 6) * 5 + subColW - 6 && mouseY >= curY && mouseY <= curY + 18) {
            cbShowWp = !cbShowWp;
            return true;
        }

        curY += 24;

        if (mouseX >= x + 12 && mouseX <= x + 130 && mouseY >= curY && mouseY <= curY + 18) {
            if (!cbCmdInput.trim().isEmpty() && !cbCoordsInput.trim().isEmpty()) {
                String[] parts = cbCoordsInput.trim().replace(",", " ").replaceAll("\\s+", " ").split(" ");
                double px = 0, py = 64, pz = 0;
                try {
                    if (parts.length >= 3) {
                        px = Double.parseDouble(parts[0]);
                        py = Double.parseDouble(parts[1]);
                        pz = Double.parseDouble(parts[2]);
                    }
                } catch (Exception ignored) {}

                double rad = 3.0;
                try { if (!cbRadiusInput.trim().isEmpty()) rad = Double.parseDouble(cbRadiusInput.trim()); } catch (Exception ignored) {}

                double minD = 0.0, maxD = 0.0;
                try { if (!cbMinDelayInput.trim().isEmpty()) minD = Double.parseDouble(cbMinDelayInput.trim()); } catch (Exception ignored) {}
                try { if (!cbMaxDelayInput.trim().isEmpty()) maxD = Double.parseDouble(cbMaxDelayInput.trim()); } catch (Exception ignored) {}

                double cd = 3.0;
                try { if (!cbCooldownInput.trim().isEmpty()) cd = Double.parseDouble(cbCooldownInput.trim()); } catch (Exception ignored) {}

                if (editingCbIndex >= 0 && editingCbIndex < list.size()) {
                    BomboConfig.CoordBind cb = list.get(editingCbIndex);
                    cb.command = cbCmdInput.trim();
                    cb.x = px;
                    cb.y = py;
                    cb.z = pz;
                    cb.radius = rad;
                    cb.requiredIsland = cbIslandInput.trim();
                    cb.showWaypoint = cbShowWp;
                    cb.minDelay = minD;
                    cb.maxDelay = maxD;
                    cb.cooldownSeconds = cd;
                } else {
                    BomboConfig.CoordBind cb = new BomboConfig.CoordBind();
                    cb.command = cbCmdInput.trim();
                    cb.x = px;
                    cb.y = py;
                    cb.z = pz;
                    cb.radius = rad;
                    cb.requiredIsland = cbIslandInput.trim();
                    cb.showWaypoint = cbShowWp;
                    cb.minDelay = minD;
                    cb.maxDelay = maxD;
                    cb.cooldownSeconds = cd;
                    cb.enabled = true;
                    list.add(cb);
                }
                editingCbIndex = -1;
                cbCmdInput = "";
                cbCoordsInput = "";
                cbIslandInput = "";
                cbRadiusInput = "";
                cbMinDelayInput = "";
                cbMaxDelayInput = "";
                cbCooldownInput = "";
                BomboConfig.save();
                return true;
            }
        }

        if (editingCbIndex >= 0 && mouseX >= x + 144 && mouseX <= x + 208 && mouseY >= curY && mouseY <= curY + 18) {
            editingCbIndex = -1;
            cbCmdInput = "";
            cbCoordsInput = "";
            cbIslandInput = "";
            cbRadiusInput = "";
            cbMinDelayInput = "";
            cbMaxDelayInput = "";
            cbCooldownInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CoordBind cb = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 26;
            int editX = delX - 32;
            int onX = editX - 38;

            int finalI = i;
            if (mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18) {
                cb.enabled = !cb.enabled;
                BomboConfig.save();
                return true;
            }

            if (mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18) {
                editingCbIndex = finalI;
                cbCmdInput = cb.command != null ? cb.command : "";
                cbCoordsInput = ((int)Math.floor(cb.x)) + " " + ((int)Math.floor(cb.y)) + " " + ((int)Math.floor(cb.z));
                cbIslandInput = cb.requiredIsland != null ? cb.requiredIsland : "";
                cbRadiusInput = cb.radius != 3.0 ? String.valueOf(cb.radius) : "";
                cbMinDelayInput = cb.minDelay > 0 ? String.valueOf(cb.minDelay) : "";
                cbMaxDelayInput = cb.maxDelay > 0 ? String.valueOf(cb.maxDelay) : "";
                cbCooldownInput = cb.cooldownSeconds != 3.0 ? String.valueOf(cb.cooldownSeconds) : "";
                cbShowWp = cb.showWaypoint;
                return true;
            }

            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18) {
                BomboConfig.CoordBind removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingCbIndex == finalI) editingCbIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    public static void clearHighlightInputs() {
        highMobInput = "";
        advItemDisplayInput = "";
        advEntityTypeInput = "";
        advHeadHashInput = "";
        advMobSizeInput = "";
        advArmorPieceInput = "";
        advPlayerNameInput = "";
        highIslandInput = "";
        advVisibilityInput = "ALL";
        advShowTitleInput = false;
        advPlaySoundInput = false;
        editingHighMob = null;
        activeFocusedField = null;
    }

    // ---------------------------------------------------------------------------------------------
    // KEYBOARD TYPING HANDLERS & SHORTCUTS (CTRL+A, CTRL+BACKSPACE, CTRL+V, CTRL+C)
    // ---------------------------------------------------------------------------------------------
    public static String getActiveFieldValue() {
        return getFieldValue(activeFocusedField);
    }

    public static String getFieldValue(String fieldId) {
        if (fieldId == null) return "";
        return switch (fieldId) {
            case "highMob" -> highMobInput;
            case "advItemDisplay" -> advItemDisplayInput;
            case "advEntityType" -> advEntityTypeInput;
            case "advHeadHash" -> advHeadHashInput;
            case "advMobSize" -> advMobSizeInput;
            case "advArmorPiece" -> advArmorPieceInput;
            case "advPlayerName" -> advPlayerNameInput;
            case "highIsland" -> highIslandInput;
            case "highSearch" -> highlightSearchQuery;
            case "newProfile" -> newProfileInput;
            case "bhBlock" -> bhBlockInput;
            case "clickGui" -> clickGuiInput;
            case "clickItem" -> clickItemInput;
            case "clickKey" -> clickKeyInput;
            case "trigText" -> triggerTextInput;
            case "trigCmd" -> triggerCommandInput;
            case "trigTitle" -> triggerTitleInput;
            case "trigSound" -> triggerSoundInput;
            case "aliasCmd" -> aliasCommandInput;
            case "aliasAct" -> aliasActualInput;
            case "partType" -> particleTypeInput;
            case "sndOrig" -> soundOrigInput;
            case "sndRepl" -> soundReplInput;
            case "globalSearch" -> BomboConfigScreen.searchQuery;
            case "wpName" -> wpNameInput;
            case "wpCoords" -> wpCoordsInput;
            case "wpIsland" -> wpIslandInput;
            case "ordIsland" -> OrderedWaypoints.routeRequiredIsland;
            case "ordArmor" -> OrderedWaypoints.routeRequiredArmor;
            case "cbCmd" -> cbCmdInput;
            case "cbCoords" -> cbCoordsInput;
            case "cbIsland" -> cbIslandInput;
            case "cbRadius" -> cbRadiusInput;
            case "cbMinDelay" -> cbMinDelayInput;
            case "cbMaxDelay" -> cbMaxDelayInput;
            case "cbCooldown" -> cbCooldownInput;
            case "entMatcher" -> entMatcherInput;
            case "entIsland" -> entIslandInput;
            case "entSubarea" -> entSubareaInput;
            case "blkFrom" -> blkFromInput;
            case "blkTo" -> blkToInput;
            case "blkIsland" -> blkIslandInput;
            case "blkSubarea" -> blkSubareaInput;
            case "kbCmd" -> kbCmdInput;
            case "kbKey" -> kbKeyInput;
            case "kbIsland" -> kbIslandInput;
            case "guiKbCmd" -> guiKbCmdInput;
            case "guiKbKey" -> guiKbKeyInput;
            case "guiKbIsland" -> guiKbIslandInput;
            case "autoRuleProfile" -> autoRuleProfileInput;
            case "autoRuleIsland" -> autoRuleIslandInput;
            case "autoRuleSubarea" -> autoRuleSubareaInput;
            case "autoRuleClass" -> autoRuleClassInput;
            case "autoRuleArmor" -> autoRuleArmorInput;
            case "autoRuleChat" -> autoRuleChatInput;
            case "autoSeqName" -> autoSeqNameInput;
            case "autoSeqDelay" -> autoSeqLoopDelay;
            case "autoSeqJitter" -> autoSeqJitterInput;
            case "actSlot" -> actionSlotInput;
            case "actItem" -> actionItemInput;
            case "actCmd" -> actionCmdInput;
            case "actEntity" -> actionEntityInput;
            case "actRadius" -> actionRadiusInput;
            case "actRepeat" -> actionRepeatInput;
            case "actDelay" -> actionDelayInput;
            default -> "";
        };
    }

    public static void setActiveFieldValue(String val) {
        if (activeFocusedField == null) return;
        String nonNull = val != null ? val : "";
        switch (activeFocusedField) {
            case "highMob" -> highMobInput = nonNull;
            case "advItemDisplay" -> advItemDisplayInput = nonNull;
            case "advEntityType" -> advEntityTypeInput = nonNull;
            case "advHeadHash" -> advHeadHashInput = nonNull;
            case "advMobSize" -> advMobSizeInput = nonNull;
            case "advArmorPiece" -> advArmorPieceInput = nonNull;
            case "advPlayerName" -> advPlayerNameInput = nonNull;
            case "highIsland" -> highIslandInput = nonNull;
            case "highSearch" -> highlightSearchQuery = nonNull;
            case "newProfile" -> newProfileInput = nonNull;
            case "bhBlock" -> bhBlockInput = nonNull;
            case "clickGui" -> clickGuiInput = nonNull;
            case "clickItem" -> clickItemInput = nonNull;
            case "clickKey" -> clickKeyInput = nonNull;
            case "trigText" -> triggerTextInput = nonNull;
            case "trigCmd" -> triggerCommandInput = nonNull;
            case "trigTitle" -> triggerTitleInput = nonNull;
            case "trigSound" -> triggerSoundInput = nonNull;
            case "aliasCmd" -> aliasCommandInput = nonNull;
            case "aliasAct" -> aliasActualInput = nonNull;
            case "partType" -> particleTypeInput = nonNull;
            case "sndOrig" -> soundOrigInput = nonNull;
            case "sndRepl" -> soundReplInput = nonNull;
            case "globalSearch" -> BomboConfigScreen.setSearchQuery(nonNull);
            case "wpName" -> wpNameInput = nonNull;
            case "wpCoords" -> wpCoordsInput = nonNull;
            case "wpIsland" -> wpIslandInput = nonNull;
            case "ordIsland" -> OrderedWaypoints.routeRequiredIsland = nonNull;
            case "ordArmor" -> OrderedWaypoints.routeRequiredArmor = nonNull;
            case "cbCmd" -> cbCmdInput = nonNull;
            case "cbCoords" -> cbCoordsInput = nonNull;
            case "cbIsland" -> cbIslandInput = nonNull;
            case "cbRadius" -> cbRadiusInput = nonNull;
            case "cbMinDelay" -> cbMinDelayInput = nonNull;
            case "cbMaxDelay" -> cbMaxDelayInput = nonNull;
            case "cbCooldown" -> cbCooldownInput = nonNull;
            case "entMatcher" -> entMatcherInput = nonNull;
            case "entIsland" -> entIslandInput = nonNull;
            case "entSubarea" -> entSubareaInput = nonNull;
            case "blkFrom" -> blkFromInput = nonNull;
            case "blkTo" -> blkToInput = nonNull;
            case "blkIsland" -> blkIslandInput = nonNull;
            case "blkSubarea" -> blkSubareaInput = nonNull;
            case "kbCmd" -> kbCmdInput = nonNull;
            case "kbKey" -> kbKeyInput = nonNull;
            case "kbIsland" -> kbIslandInput = nonNull;
            case "guiKbCmd" -> guiKbCmdInput = nonNull;
            case "guiKbKey" -> guiKbKeyInput = nonNull;
            case "guiKbIsland" -> guiKbIslandInput = nonNull;
            case "autoRuleProfile" -> autoRuleProfileInput = nonNull;
            case "autoRuleIsland" -> autoRuleIslandInput = nonNull;
            case "autoRuleSubarea" -> autoRuleSubareaInput = nonNull;
            case "autoRuleClass" -> autoRuleClassInput = nonNull;
            case "autoRuleArmor" -> autoRuleArmorInput = nonNull;
            case "autoRuleChat" -> autoRuleChatInput = nonNull;
            case "autoSeqName" -> autoSeqNameInput = nonNull;
            case "autoSeqDelay" -> autoSeqLoopDelay = nonNull;
            case "autoSeqJitter" -> autoSeqJitterInput = nonNull;
            case "actSlot" -> actionSlotInput = nonNull;
            case "actItem" -> actionItemInput = nonNull;
            case "actCmd" -> actionCmdInput = nonNull;
            case "actEntity" -> actionEntityInput = nonNull;
            case "actRadius" -> actionRadiusInput = nonNull;
            case "actRepeat" -> actionRepeatInput = nonNull;
            case "actDelay" -> actionDelayInput = nonNull;
        }
    }

    public static boolean handleCharTyped(char codePoint) {
        if (activeFocusedField == null || Character.isISOControl(codePoint)) return false;
        String cur = getActiveFieldValue();
        if (cursorPosition < 0 || cursorPosition > cur.length()) cursorPosition = cur.length();

        if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
            int selStart = Math.max(0, Math.min(selectionAnchor, cursorPosition));
            int selEnd = Math.min(cur.length(), Math.max(selectionAnchor, cursorPosition));
            String prefix = cur.substring(0, selStart);
            String suffix = cur.substring(selEnd);
            setActiveFieldValue(prefix + codePoint + suffix);
            cursorPosition = prefix.length() + 1;
            selectionAnchor = -1;
            isFieldSelected = false;
        } else {
            String prefix = cur.substring(0, cursorPosition);
            String suffix = cur.substring(cursorPosition);
            setActiveFieldValue(prefix + codePoint + suffix);
            cursorPosition = prefix.length() + 1;
            selectionAnchor = -1;
            isFieldSelected = false;
        }
        return true;
    }

    public static boolean handleKeyPressed(int keyCode) {
        // --- Keybind capture -------------------------------------------------------
        // One shared state machine for all four capture widgets. A modifier (Ctrl/Alt/Shift/
        // Win/F-key/keypad) OPENS a combo instead of committing: the button shows the held
        // prefix ([CTRL + ...]) so Ctrl+A or Alt+A is expressible. Pressing a normal key
        // commits the full combo; releasing the modifier alone commits it as a single key.
        if (clickKeyIsListening) {
            String captured = captureKeyStep(keyCode, clickKeyInput);
            if (captured != null) {
                clickKeyInput = captured;
                if (!captureStillListening) clickKeyIsListening = false;
                return true;
            }
        }
        if (autoSeqKeyIsListening) {
            String captured = captureKeyStep(keyCode, autoSeqKeyInput);
            if (captured != null) {
                autoSeqKeyInput = captured;
                if (!captureStillListening) autoSeqKeyIsListening = false;
                return true;
            }
        }
        if (kbIsListening) {
            String captured = captureKeyStep(keyCode, kbKeyInput);
            if (captured != null) {
                kbKeyInput = captured;
                if (!captureStillListening) kbIsListening = false;
                return true;
            }
        }
        if (guiKbIsListening) {
            String captured = captureKeyStep(keyCode, guiKbKeyInput);
            if (captured != null) {
                guiKbKeyInput = captured;
                if (!captureStillListening) guiKbIsListening = false;
                return true;
            }
        }

        long handle = Minecraft.getInstance().getWindow().handle();
        boolean isCtrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean isShift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        // Global Ctrl + Z: Undo Last Deleted / Modified Entry
        if (isCtrl && keyCode == GLFW.GLFW_KEY_Z) {
            if (!undoStack.isEmpty()) {
                undoStack.pop().run();
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§3Bombo§8]§r §aUndid last action!"));
                }
                return true;
            }
        }

        if (activeFocusedField == null) return false;

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            activeFocusedField = null;
            isFieldSelected = false;
            selectionAnchor = -1;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            activeFocusedField = null;
            isFieldSelected = false;
            selectionAnchor = -1;
            return true;
        }

        String cur = getActiveFieldValue();
        if (cursorPosition < 0 || cursorPosition > cur.length()) cursorPosition = cur.length();

        // Ctrl + A: Select All
        if (isCtrl && keyCode == GLFW.GLFW_KEY_A) {
            selectionAnchor = 0;
            cursorPosition = cur.length();
            isFieldSelected = !cur.isEmpty();
            return true;
        }

        // Ctrl + Shift + Left Arrow: Extend Selection Left by Word
        if (isCtrl && isShift && keyCode == GLFW.GLFW_KEY_LEFT) {
            if (selectionAnchor == -1) selectionAnchor = cursorPosition;
            cursorPosition = findPrevWordBoundary(cur, cursorPosition);
            isFieldSelected = (selectionAnchor != cursorPosition);
            return true;
        }

        // Ctrl + Shift + Right Arrow: Extend Selection Right by Word
        if (isCtrl && isShift && keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (selectionAnchor == -1) selectionAnchor = cursorPosition;
            cursorPosition = findNextWordBoundary(cur, cursorPosition);
            isFieldSelected = (selectionAnchor != cursorPosition);
            return true;
        }

        // Ctrl + Left Arrow: Jump Word Left
        if (isCtrl && !isShift && keyCode == GLFW.GLFW_KEY_LEFT) {
            cursorPosition = findPrevWordBoundary(cur, cursorPosition);
            selectionAnchor = -1;
            isFieldSelected = false;
            return true;
        }

        // Ctrl + Right Arrow: Jump Word Right
        if (isCtrl && !isShift && keyCode == GLFW.GLFW_KEY_RIGHT) {
            cursorPosition = findNextWordBoundary(cur, cursorPosition);
            selectionAnchor = -1;
            isFieldSelected = false;
            return true;
        }

        // Shift + Left Arrow: Extend Selection Left
        if (!isCtrl && isShift && keyCode == GLFW.GLFW_KEY_LEFT) {
            if (selectionAnchor == -1) selectionAnchor = cursorPosition;
            cursorPosition = Math.max(0, cursorPosition - 1);
            isFieldSelected = (selectionAnchor != cursorPosition);
            return true;
        }

        // Shift + Right Arrow: Extend Selection Right
        if (!isCtrl && isShift && keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (selectionAnchor == -1) selectionAnchor = cursorPosition;
            cursorPosition = Math.min(cur.length(), cursorPosition + 1);
            isFieldSelected = (selectionAnchor != cursorPosition);
            return true;
        }

        // Shift + Home
        if (isShift && keyCode == GLFW.GLFW_KEY_HOME) {
            if (selectionAnchor == -1) selectionAnchor = cursorPosition;
            cursorPosition = 0;
            isFieldSelected = (selectionAnchor != cursorPosition);
            return true;
        }

        // Shift + End
        if (isShift && keyCode == GLFW.GLFW_KEY_END) {
            if (selectionAnchor == -1) selectionAnchor = cursorPosition;
            cursorPosition = cur.length();
            isFieldSelected = (selectionAnchor != cursorPosition);
            return true;
        }

        // Left Arrow without Shift or Ctrl
        if (!isCtrl && !isShift && keyCode == GLFW.GLFW_KEY_LEFT) {
            if (isFieldSelected && selectionAnchor >= 0) {
                cursorPosition = Math.min(cursorPosition, selectionAnchor);
            } else {
                cursorPosition = Math.max(0, cursorPosition - 1);
            }
            selectionAnchor = -1;
            isFieldSelected = false;
            return true;
        }

        // Right Arrow without Shift or Ctrl
        if (!isCtrl && !isShift && keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (isFieldSelected && selectionAnchor >= 0) {
                cursorPosition = Math.max(cursorPosition, selectionAnchor);
            } else {
                cursorPosition = Math.min(cur.length(), cursorPosition + 1);
            }
            selectionAnchor = -1;
            isFieldSelected = false;
            return true;
        }

        // Home
        if (!isShift && keyCode == GLFW.GLFW_KEY_HOME) {
            cursorPosition = 0;
            selectionAnchor = -1;
            isFieldSelected = false;
            return true;
        }

        // End
        if (!isShift && keyCode == GLFW.GLFW_KEY_END) {
            cursorPosition = cur.length();
            selectionAnchor = -1;
            isFieldSelected = false;
            return true;
        }

        // Ctrl + Backspace: Delete Word Backward
        if (isCtrl && keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
                int selStart = Math.max(0, Math.min(selectionAnchor, cursorPosition));
                int selEnd = Math.min(cur.length(), Math.max(selectionAnchor, cursorPosition));
                setActiveFieldValue(cur.substring(0, selStart) + cur.substring(selEnd));
                cursorPosition = selStart;
                selectionAnchor = -1;
                isFieldSelected = false;
            } else if (!cur.isEmpty() && cursorPosition > 0) {
                String left = cur.substring(0, cursorPosition);
                int lastSpace = Math.max(left.lastIndexOf(' '), Math.max(left.lastIndexOf('/'), left.lastIndexOf('_')));
                int newPos = lastSpace >= 0 ? lastSpace : 0;
                setActiveFieldValue(cur.substring(0, newPos) + cur.substring(cursorPosition));
                cursorPosition = newPos;
            }
            return true;
        }

        // Ctrl + V: Paste from Clipboard
        if (isCtrl && keyCode == GLFW.GLFW_KEY_V) {
            try {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    String clean = clip.replace("\r", "").replace("\n", " ");
                    if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
                        int selStart = Math.max(0, Math.min(selectionAnchor, cursorPosition));
                        int selEnd = Math.min(cur.length(), Math.max(selectionAnchor, cursorPosition));
                        setActiveFieldValue(cur.substring(0, selStart) + clean + cur.substring(selEnd));
                        cursorPosition = selStart + clean.length();
                        selectionAnchor = -1;
                        isFieldSelected = false;
                    } else {
                        String prefix = cur.substring(0, cursorPosition);
                        String suffix = cur.substring(cursorPosition);
                        setActiveFieldValue(prefix + clean + suffix);
                        cursorPosition = prefix.length() + clean.length();
                    }
                }
            } catch (Exception ignored) {}
            return true;
        }

        // Ctrl + C: Copy to Clipboard
        if (isCtrl && keyCode == GLFW.GLFW_KEY_C) {
            try {
                if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
                    int selStart = Math.max(0, Math.min(selectionAnchor, cursorPosition));
                    int selEnd = Math.min(cur.length(), Math.max(selectionAnchor, cursorPosition));
                    Minecraft.getInstance().keyboardHandler.setClipboard(cur.substring(selStart, selEnd));
                } else {
                    Minecraft.getInstance().keyboardHandler.setClipboard(cur);
                }
            } catch (Exception ignored) {}
            return true;
        }

        // Regular Backspace
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
                int selStart = Math.max(0, Math.min(selectionAnchor, cursorPosition));
                int selEnd = Math.min(cur.length(), Math.max(selectionAnchor, cursorPosition));
                setActiveFieldValue(cur.substring(0, selStart) + cur.substring(selEnd));
                cursorPosition = selStart;
                selectionAnchor = -1;
                isFieldSelected = false;
            } else if (!cur.isEmpty() && cursorPosition > 0) {
                String prefix = cur.substring(0, cursorPosition - 1);
                String suffix = cur.substring(cursorPosition);
                setActiveFieldValue(prefix + suffix);
                cursorPosition = Math.max(0, cursorPosition - 1);
            }
            return true;
        }

        // Tab Completion for Particles, Sounds & Blocks, with Tab Field Cycling Fallback
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            String activeVal = getActiveFieldValue().trim().toLowerCase();
            boolean handledCompletion = false;
            if (!isShift && activeFocusedField.equals("partType")) {
                for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.keySet()) {
                    String path = id.getPath();
                    if (path.toLowerCase().startsWith(activeVal) && !path.equalsIgnoreCase(activeVal)) {
                        setActiveFieldValue(path);
                        cursorPosition = path.length();
                        handledCompletion = true;
                        break;
                    }
                }
            } else if (!isShift && (activeFocusedField.equals("sndOrig") || activeFocusedField.equals("sndRepl") || activeFocusedField.equals("trigSound"))) {
                for (java.io.File f : me.bombo.bomboaddons.features.sounds.CustomSoundManager.getCustomSoundFiles()) {
                    String fn = f.getName();
                    if (fn.toLowerCase().startsWith(activeVal) && !fn.equalsIgnoreCase(activeVal)) {
                        setActiveFieldValue(fn);
                        cursorPosition = fn.length();
                        handledCompletion = true;
                        break;
                    }
                }
                if (!handledCompletion) {
                    for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) {
                        String full = id.toString();
                        String path = id.getPath();
                        if (full.toLowerCase().startsWith(activeVal) && !full.equalsIgnoreCase(activeVal)) {
                            setActiveFieldValue(full);
                            cursorPosition = full.length();
                            handledCompletion = true;
                            break;
                        } else if (path.toLowerCase().startsWith(activeVal) && !path.equalsIgnoreCase(activeVal)) {
                            setActiveFieldValue(path);
                            cursorPosition = path.length();
                            handledCompletion = true;
                            break;
                        }
                    }
                }
            } else if (!isShift && (activeFocusedField.equals("blkFrom") || activeFocusedField.equals("blkTo"))) {
                for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet()) {
                    String full = id.toString();
                    String path = id.getPath();
                    if (full.toLowerCase().startsWith(activeVal) && !full.equalsIgnoreCase(activeVal)) {
                        setActiveFieldValue(path);
                        cursorPosition = path.length();
                        handledCompletion = true;
                        break;
                    } else if (path.toLowerCase().startsWith(activeVal) && !path.equalsIgnoreCase(activeVal)) {
                        setActiveFieldValue(path);
                        cursorPosition = path.length();
                        handledCompletion = true;
                        break;
                    }
                }
            }

            if (!handledCompletion) {
                cycleTabField(isShift);
            }
            return true;
        }

        // Regular Delete
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (isFieldSelected && selectionAnchor >= 0 && selectionAnchor != cursorPosition) {
                int selStart = Math.max(0, Math.min(selectionAnchor, cursorPosition));
                int selEnd = Math.min(cur.length(), Math.max(selectionAnchor, cursorPosition));
                setActiveFieldValue(cur.substring(0, selStart) + cur.substring(selEnd));
                cursorPosition = selStart;
                selectionAnchor = -1;
                isFieldSelected = false;
            } else if (!cur.isEmpty() && cursorPosition < cur.length()) {
                String prefix = cur.substring(0, cursorPosition);
                String suffix = cur.substring(cursorPosition + 1);
                setActiveFieldValue(prefix + suffix);
            }
            return true;
        }

        if (activeFocusedField != null) {
            return true;
        }

        return false;
    }

    // --- Shared keybind-capture state machine ----------------------------------
    /**
     * Result of {@link #captureKeyStep}: null means "this key is not for the capture widget"
     * (let normal GUI handling run); otherwise it is the new binding string to store, and
     * {@link #captureStillListening} says whether the capture stays open (a modifier was
     * pressed and we await the combo's main key).
     */
    public static boolean captureStillListening = false;
    /** True while a modifier has opened a combo but no main key has been pressed yet. */
    public static boolean capturePrefixOpen = false;

    /** Is this key a modifier/starter that should open a combo rather than commit alone? */
    private static boolean isStarterKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER
                || (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25)
                || (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_EQUAL);
    }

    /**
     * One key press against an open capture. Public because the config screen's generic
     * keybind-item capture uses the same state machine.
     *
     * <ul>
     *   <li>Esc cancels (returns {@code ""} with capture closed).</li>
     *   <li>A modifier with no combo open starts the prefix and keeps listening, so the
     *       button label can show {@code [CTRL + ...]} while Ctrl is held.</li>
     *   <li>A normal key commits {@code prefix + key} and closes the capture.</li>
     *   <li>A modifier pressed while a prefix is already open extends the prefix
     *       (Ctrl then Alt = {@code lctrl+lalt+...}).</li>
     * </ul>
     */
    public static String captureKeyStep(int keyCode, String currentInput) {
        captureStillListening = false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            capturePrefixOpen = false;
            return "";
        }
        String prefix = getCurrentlyHeldComboPrefix(keyCode);
        if (prefix.isEmpty() && isStarterKey(keyCode)) {
            // Open (or extend) a combo and keep listening. Show the live prefix in the button.
            capturePrefixOpen = true;
            captureStillListening = true;
            return me.bombo.bomboaddons.CustomBindsProcessor.getKeyNameForGlfwCode(keyCode);
        }
        capturePrefixOpen = false;
        return buildFullComboString(keyCode);
    }

    /** Label for a capture button, including the live combo prefix while one is open. */
    public static String captureButtonLabel(boolean listening, String currentValue, String idleText) {
        if (listening && capturePrefixOpen) {
            String held = getCurrentlyHeldComboPrefix(-1);
            if (held.endsWith("+")) held = held.substring(0, held.length() - 1);
            if (!held.isEmpty()) {
                String[] parts = held.split("\\+");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sb.append(" + ");
                    sb.append(me.bombo.bomboaddons.ClickLogic.getKeyDisplayName(parts[i]));
                }
                return "§e[" + sb + " + ...]";
            }
        }
        if (listening) return "§e[Press Key...]";
        if (currentValue == null || currentValue.isEmpty()) return idleText;
        return "§b" + me.bombo.bomboaddons.ClickLogic.getKeyDisplayName(currentValue);
    }

    public static String getCurrentlyHeldComboPrefix(int currentCode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return "";
        long handle = mc.getWindow().handle();
        java.util.LinkedHashSet<String> parts = new java.util.LinkedHashSet<>();

        // 1. Modifiers (Ctrl, Shift, Alt)
        boolean lctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS;
        boolean rctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        if (lctrl && currentCode != GLFW.GLFW_KEY_LEFT_CONTROL) parts.add("lctrl");
        else if (rctrl && currentCode != GLFW.GLFW_KEY_RIGHT_CONTROL) parts.add("rctrl");

        boolean lshift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
        boolean rshift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (lshift && currentCode != GLFW.GLFW_KEY_LEFT_SHIFT) parts.add("lshift");
        else if (rshift && currentCode != GLFW.GLFW_KEY_RIGHT_SHIFT) parts.add("rshift");

        boolean lalt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
        boolean ralt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        if (lalt && currentCode != GLFW.GLFW_KEY_LEFT_ALT) parts.add("lalt");
        else if (ralt && currentCode != GLFW.GLFW_KEY_RIGHT_ALT) parts.add("ralt");

        // 2. Mouse buttons held down (mouse3 - mouse8: buttons 2 - 7)
        for (int btn = 2; btn <= 7; btn++) {
            int mouseCode = 1000 + btn;
            if (mouseCode != currentCode && (GLFW.glfwGetMouseButton(handle, btn) == GLFW.GLFW_PRESS || me.bombo.bomboaddons.CustomBindsProcessor.pressedKeys.contains(mouseCode))) {
                parts.add("mouse" + (btn + 1));
            }
        }

        // 3. F-keys held down (F1 - F25: GLFW 290 - 314)
        for (int f = 290; f <= 314; f++) {
            if (f != currentCode && GLFW.glfwGetKey(handle, f) == GLFW.GLFW_PRESS) {
                parts.add("f" + (f - 289));
            }
        }

        // 4. Keypad keys held down (Keypad 0-9: GLFW 320 - 329, and operators 330-336)
        for (int kp = 320; kp <= 336; kp++) {
            if (kp != currentCode && GLFW.glfwGetKey(handle, kp) == GLFW.GLFW_PRESS) {
                parts.add(me.bombo.bomboaddons.CustomBindsProcessor.getKeyNameForGlfwCode(kp));
            }
        }

        // 5. Standard letters (A-Z: GLFW 65 - 90)
        for (int key = 65; key <= 90; key++) {
            if (key != currentCode && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS) {
                parts.add(me.bombo.bomboaddons.CustomBindsProcessor.getKeyNameForGlfwCode(key));
            }
        }

        // 6. Standard top row numbers (0-9: GLFW 48 - 57)
        for (int key = 48; key <= 57; key++) {
            if (key != currentCode && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS) {
                parts.add(me.bombo.bomboaddons.CustomBindsProcessor.getKeyNameForGlfwCode(key));
            }
        }

        // 7. Navigation & special keys
        int[] otherKeys = {
            GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_CAPS_LOCK,
            GLFW.GLFW_KEY_GRAVE_ACCENT, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL,
            GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET, GLFW.GLFW_KEY_BACKSLASH,
            GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_COMMA,
            GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_SLASH
        };
        for (int key : otherKeys) {
            if (key != currentCode && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS) {
                parts.add(me.bombo.bomboaddons.CustomBindsProcessor.getKeyNameForGlfwCode(key));
            }
        }

        if (parts.isEmpty()) return "";
        return String.join("+", parts) + "+";
    }

    public static String buildFullComboString(int keyCode) {
        String prefix = getCurrentlyHeldComboPrefix(keyCode);
        String currentKey = me.bombo.bomboaddons.CustomBindsProcessor.getKeyNameForGlfwCode(keyCode);
        return prefix + currentKey;
    }
    public static String kbCmdInput = "";
    public static String kbKeyInput = "";
    public static String kbIslandInput = "";
    public static boolean kbIsListening = false;
    public static int editingKbIndex = -1;

    public static int getKeybindsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return 140;
        List<BomboConfig.CommandBind> list = s.keybindBinds != null ? s.keybindBinds.get(s.activeProfile) : null;
        int count = list != null ? list.size() : 0;
        return 125 + Math.max(1, count) * 26;
    }

    public static void renderKeybindsManager(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        int curY = y + 8;
        g.text(font, "§6§lPROFILE COMMAND KEYBINDS (" + BomboConfig.get().activeProfile + ")", x + 12, curY, ConfigUITheme.ACCENT_GOLD, false);
        curY += 18;

        int colW = (w - 36) / 4;
        renderCleanInputField(g, font, "Command (e.g. /warp hub)", kbCmdInput, "kbCmd", x + 12, curY, colW * 2, mouseX, mouseY);

        int keyX = x + 12 + colW * 2 + 6;
        int keyW = colW - 6;
        String keyDisplay = kbIsListening ? "§e[PRESS KEY]" : (kbKeyInput.isEmpty() ? "§8Keybind (Click)" : "§b" + kbKeyInput);
        boolean keyHover = mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, keyDisplay, keyX, curY, keyW, 18, keyHover, -1, kbIsListening ? 0x44FFAA00 : 0x221E293B, 0x55FFFFFF);

        renderCleanInputField(g, font, "Island (blank=all)", kbIslandInput, "kbIsland", keyX + keyW + 6, curY, colW, mouseX, mouseY);

        curY += 24;

        String addBtnText = editingKbIndex >= 0 ? "§a✔ Save Keybind" : "§b+ Add Keybind";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 130 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 126, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        if (editingKbIndex >= 0) {
            boolean canHover = mouseX >= x + 144 && mouseX <= x + 208 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 144, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.CommandBind> list = s.keybindBinds != null ? s.keybindBinds.get(s.activeProfile) : null;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No command keybinds configured for profile '" + s.activeProfile + "'. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CommandBind cb = list.get(i);
            int rowY = curY;

            int rowBg = (i % 2 == 0) ? 0x1A0F172A : 0x221E293B;
            g.fill(x + 12, rowY, x + w - 12, rowY + 22, rowBg);

            // Toggle Button [ON] / [OFF]
            int onBtnX = x + 16;
            boolean onHover = mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, cb.enabled ? "§aON" : "§cOFF", onBtnX, rowY + 2, 32, 18, onHover, cb.enabled ? 0xFF10B981 : 0xFF94A3B8, cb.enabled ? 0x2210B981 : 0x1AFFFFFF, 0x44FFFFFF);

            // Key Pill
            int pillX = onBtnX + 38;
            String kName = (cb.keyName != null && !cb.keyName.isEmpty()) ? cb.keyName : "NONE";
            int pillW = Math.max(45, font.width(kName) + 12);
            ConfigUITheme.drawPillButton(g, font, "§b" + kName, pillX, rowY + 2, pillW, 18, false, -1, 0x3300E5FF, 0x6600E5FF);

            // Command text
            int textX = pillX + pillW + 8;
            g.text(font, "§e" + (cb.command != null ? cb.command : ""), textX, rowY + 7, 0xFFFFFFFF, false);

            if (cb.requiredIsland != null && !cb.requiredIsland.isEmpty()) {
                g.text(font, "§7[" + cb.requiredIsland + "]", textX + font.width("§e" + cb.command) + 8, rowY + 7, ConfigUITheme.getTextMuted(), false);
            }

            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 44;

            // [Edit]
            boolean editHover = mouseX >= editX && mouseX <= editX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, "§eEdit", editX, rowY + 2, 40, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            // [DEL ✕]
            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY + 2, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleKeybindsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        int curY = y + 26;
        int colW = (w - 36) / 4;

        if (checkFieldClick(x + 12, curY, colW * 2, 18, "kbCmd", mouseX, mouseY)) return true;

        int keyX = x + 12 + colW * 2 + 6;
        int keyW = colW - 6;
        if (mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18) {
            kbIsListening = true;
            activeFocusedField = null;
            return true;
        }

        if (checkFieldClick(keyX + keyW + 6, curY, colW, 18, "kbIsland", mouseX, mouseY)) return true;

        curY += 24;

        BomboConfig.Settings s = BomboConfig.get();
        if (s.keybindBinds == null) s.keybindBinds = new HashMap<>();
        List<BomboConfig.CommandBind> list = s.keybindBinds.computeIfAbsent(s.activeProfile, k -> new ArrayList<>());

        // [+ Add Keybind] / [Save]
        if (mouseX >= x + 12 && mouseX <= x + 138 && mouseY >= curY && mouseY <= curY + 18) {
            if (!kbCmdInput.trim().isEmpty()) {
                if (editingKbIndex >= 0 && editingKbIndex < list.size()) {
                    BomboConfig.CommandBind cb = list.get(editingKbIndex);
                    cb.command = kbCmdInput.trim();
                    cb.keyName = kbKeyInput.trim();
                    cb.requiredIsland = kbIslandInput.trim();
                } else {
                    BomboConfig.CommandBind cb = new BomboConfig.CommandBind();
                    cb.command = kbCmdInput.trim();
                    cb.keyName = kbKeyInput.trim();
                    cb.requiredIsland = kbIslandInput.trim();
                    cb.enabled = true;
                    list.add(cb);
                }
                editingKbIndex = -1;
                kbCmdInput = "";
                kbKeyInput = "";
                kbIslandInput = "";
                BomboConfig.save();
                return true;
            }
        }

        if (editingKbIndex >= 0 && mouseX >= x + 144 && mouseX <= x + 208 && mouseY >= curY && mouseY <= curY + 18) {
            editingKbIndex = -1;
            kbCmdInput = "";
            kbKeyInput = "";
            kbIslandInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CommandBind cb = list.get(i);
            int rowY = curY;
            int onBtnX = x + 16;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 44;

            int finalI = i;
            // Toggle
            if (mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                cb.enabled = !cb.enabled;
                BomboConfig.save();
                return true;
            }

            // Edit
            if (mouseX >= editX && mouseX <= editX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                editingKbIndex = finalI;
                kbCmdInput = cb.command != null ? cb.command : "";
                kbKeyInput = cb.keyName != null ? cb.keyName : "";
                kbIslandInput = cb.requiredIsland != null ? cb.requiredIsland : "";
                return true;
            }

            // Delete
            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                BomboConfig.CommandBind removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingKbIndex == finalI) editingKbIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // GUI CONTAINER KEYBINDS (PROFILE BINDS) MANAGER CARD
    // ---------------------------------------------------------------------------------------------
    public static String guiKbCmdInput = "";
    public static String guiKbKeyInput = "";
    public static String guiKbIslandInput = "";
    public static boolean guiKbIsListening = false;
    public static int editingGuiKbIndex = -1;

    public static int getGuiBindsCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return 140;
        List<BomboConfig.CommandBind> list = s.profileBinds != null ? s.profileBinds.get(s.activeProfile) : null;
        int count = list != null ? list.size() : 0;
        return 125 + Math.max(1, count) * 26;
    }

    public static void renderGuiBindsManager(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        int curY = y + 8;
        g.text(font, "§b§lGUI CONTAINER KEYBINDS (" + BomboConfig.get().activeProfile + ") §7- Executed while inside a container/GUI", x + 12, curY, ConfigUITheme.getAccentColor(), false);
        curY += 18;

        int colW = (w - 36) / 4;
        renderCleanInputField(g, font, "Command (e.g. /warp hub)", guiKbCmdInput, "guiKbCmd", x + 12, curY, colW * 2, mouseX, mouseY);

        int keyX = x + 12 + colW * 2 + 6;
        int keyW = colW - 6;
        String keyDisplay = guiKbIsListening ? "§e[PRESS KEY]" : (guiKbKeyInput.isEmpty() ? "§8Keybind (Click)" : "§b" + guiKbKeyInput);
        boolean keyHover = mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, keyDisplay, keyX, curY, keyW, 18, keyHover, -1, guiKbIsListening ? 0x44FFAA00 : 0x221E293B, 0x55FFFFFF);

        renderCleanInputField(g, font, "Island (blank=all)", guiKbIslandInput, "guiKbIsland", keyX + keyW + 6, curY, colW, mouseX, mouseY);

        curY += 24;

        String addBtnText = editingGuiKbIndex >= 0 ? "§a✔ Save GUI Bind" : "§b+ Add GUI Bind";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 130 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 126, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        if (editingGuiKbIndex >= 0) {
            boolean canHover = mouseX >= x + 144 && mouseX <= x + 208 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 144, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.CommandBind> list = s.profileBinds != null ? s.profileBinds.get(s.activeProfile) : null;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No GUI container keybinds configured for profile '" + s.activeProfile + "'. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CommandBind cb = list.get(i);
            int rowY = curY;

            int rowBg = (i % 2 == 0) ? 0x1A0F172A : 0x221E293B;
            g.fill(x + 12, rowY, x + w - 12, rowY + 22, rowBg);

            // Toggle Button [ON] / [OFF]
            int onBtnX = x + 16;
            boolean onHover = mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, cb.enabled ? "§aON" : "§cOFF", onBtnX, rowY + 2, 32, 18, onHover, cb.enabled ? 0xFF10B981 : 0xFF94A3B8, cb.enabled ? 0x2210B981 : 0x1AFFFFFF, 0x44FFFFFF);

            // Key Pill
            int pillX = onBtnX + 38;
            String kName = (cb.keyName != null && !cb.keyName.isEmpty()) ? cb.keyName : "NONE";
            int pillW = Math.max(45, font.width(kName) + 12);
            ConfigUITheme.drawPillButton(g, font, "§b" + kName, pillX, rowY + 2, pillW, 18, false, -1, 0x3300E5FF, 0x6600E5FF);

            // Command text
            int textX = pillX + pillW + 8;
            g.text(font, "§e" + (cb.command != null ? cb.command : ""), textX, rowY + 7, 0xFFFFFFFF, false);

            if (cb.requiredIsland != null && !cb.requiredIsland.isEmpty()) {
                g.text(font, "§7[" + cb.requiredIsland + "]", textX + font.width("§e" + cb.command) + 8, rowY + 7, ConfigUITheme.getTextMuted(), false);
            }

            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 44;

            // [Edit]
            boolean editHover = mouseX >= editX && mouseX <= editX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, "§eEdit", editX, rowY + 2, 40, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            // [DEL ✕]
            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY + 2, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleGuiBindsClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        int curY = y + 26;
        int colW = (w - 36) / 4;

        if (checkFieldClick(x + 12, curY, colW * 2, 18, "guiKbCmd", mouseX, mouseY)) return true;

        int keyX = x + 12 + colW * 2 + 6;
        int keyW = colW - 6;
        if (mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18) {
            guiKbIsListening = true;
            activeFocusedField = null;
            return true;
        }

        if (checkFieldClick(keyX + keyW + 6, curY, colW, 18, "guiKbIsland", mouseX, mouseY)) return true;

        curY += 24;

        BomboConfig.Settings s = BomboConfig.get();
        if (s.profileBinds == null) s.profileBinds = new HashMap<>();
        List<BomboConfig.CommandBind> list = s.profileBinds.computeIfAbsent(s.activeProfile, k -> new ArrayList<>());

        // [+ Add Keybind] / [Save]
        if (mouseX >= x + 12 && mouseX <= x + 138 && mouseY >= curY && mouseY <= curY + 18) {
            if (!guiKbCmdInput.trim().isEmpty()) {
                if (editingGuiKbIndex >= 0 && editingGuiKbIndex < list.size()) {
                    BomboConfig.CommandBind cb = list.get(editingGuiKbIndex);
                    cb.command = guiKbCmdInput.trim();
                    cb.keyName = guiKbKeyInput.trim();
                    cb.requiredIsland = guiKbIslandInput.trim();
                } else {
                    BomboConfig.CommandBind cb = new BomboConfig.CommandBind();
                    cb.command = guiKbCmdInput.trim();
                    cb.keyName = guiKbKeyInput.trim();
                    cb.requiredIsland = guiKbIslandInput.trim();
                    cb.enabled = true;
                    list.add(cb);
                }
                editingGuiKbIndex = -1;
                guiKbCmdInput = "";
                guiKbKeyInput = "";
                guiKbIslandInput = "";
                BomboConfig.save();
                return true;
            }
        }

        if (editingGuiKbIndex >= 0 && mouseX >= x + 144 && mouseX <= x + 208 && mouseY >= curY && mouseY <= curY + 18) {
            editingGuiKbIndex = -1;
            guiKbCmdInput = "";
            guiKbKeyInput = "";
            guiKbIslandInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.CommandBind cb = list.get(i);
            int rowY = curY;
            int onBtnX = x + 16;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 44;

            int finalI = i;
            // Toggle
            if (mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                cb.enabled = !cb.enabled;
                BomboConfig.save();
                return true;
            }

            // Edit
            if (mouseX >= editX && mouseX <= editX + 40 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                editingGuiKbIndex = finalI;
                guiKbCmdInput = cb.command != null ? cb.command : "";
                guiKbKeyInput = cb.keyName != null ? cb.keyName : "";
                guiKbIslandInput = cb.requiredIsland != null ? cb.requiredIsland : "";
                return true;
            }

            // Delete
            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                BomboConfig.CommandBind removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingGuiKbIndex == finalI) editingGuiKbIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // WORD NAVIGATION & TAB CYCLING HELPERS
    // ---------------------------------------------------------------------------------------------
    private static int findPrevWordBoundary(String text, int pos) {
        if (pos <= 0 || text.isEmpty()) return 0;
        int i = Math.min(pos - 1, text.length() - 1);
        while (i > 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        boolean inWord = Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_';
        while (i > 0) {
            char c = text.charAt(i - 1);
            if (Character.isWhitespace(c)) break;
            boolean prevInWord = Character.isLetterOrDigit(c) || c == '_';
            if (inWord != prevInWord) break;
            i--;
        }
        return i;
    }

    private static int findNextWordBoundary(String text, int pos) {
        int len = text.length();
        if (pos >= len || text.isEmpty()) return len;
        int i = pos;
        while (i < len && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= len) return len;
        boolean inWord = Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_';
        while (i < len) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) break;
            boolean nextInWord = Character.isLetterOrDigit(c) || c == '_';
            if (inWord != nextInWord) break;
            i++;
        }
        return i;
    }

    private static final List<List<String>> TAB_CYCLES = List.of(
        List.of("cbCmd", "cbCoords", "cbIsland", "cbRadius", "cbMinDelay", "cbMaxDelay", "cbCooldown"),
        List.of("entMatcher", "entIsland", "entSubarea"),
        List.of("blkFrom", "blkTo", "blkIsland", "blkSubarea"),
        List.of("kbCmd", "kbKey", "kbIsland"),
        List.of("sndOrig", "sndRepl", "sndVolume", "sndPitch", "sndIsland", "sndSubarea"),
        List.of("ctText", "ctIsland", "ctCmd", "ctSubarea", "ctSound", "ctMinDelay", "ctMaxDelay", "ctCooldown"),
        List.of("partType", "partIsland", "partSubarea"),
        List.of("owName", "owCoords", "owCat", "owIsland", "owColor"),
        List.of("highMob", "highIsland", "advItemDisplay", "advEntityType", "advHeadHash", "advMobSize", "advArmorPiece", "advPlayerName")
    );

    public static void cycleTabField(boolean reverse) {
        if (activeFocusedField == null) return;
        for (List<String> cycle : TAB_CYCLES) {
            int idx = cycle.indexOf(activeFocusedField);
            if (idx != -1) {
                int nextIdx = reverse ? (idx - 1 + cycle.size()) % cycle.size() : (idx + 1) % cycle.size();
                activeFocusedField = cycle.get(nextIdx);
                isFieldSelected = false;
                selectionAnchor = -1;
                String val = getActiveFieldValue();
                cursorPosition = val.length();
                return;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ENTITY HIDER CARD
    // ---------------------------------------------------------------------------------------------
    public static String entMatcherInput = "";
    public static String entIslandInput = "";
    public static String entSubareaInput = "";
    public static int editingEntIndex = -1;

    public static int getEntityHiderCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return 130;
        int count = s.hiddenEntities != null ? s.hiddenEntities.size() : 0;
        return 90 + Math.max(1, count) * 24 + 10;
    }

    public static void renderEntityHiderCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);
        g.text(font, "§6§lEntity Hider Rules §7(Texture Hash / Name / Type ID)", x + 12, y + 8, ConfigUITheme.ACCENT_GOLD, false);

        int curY = y + 26;
        int colW = (w - 36) / 3;

        renderCleanInputField(g, font, "Texture Hash / Mob Name / Entity ID", entMatcherInput, "entMatcher", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Island Filter (e.g. Garden)", entIslandInput, "entIsland", x + 12 + colW + 6, curY, colW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Subarea (e.g. Plot - 16, p1..p5)", entSubareaInput, "entSubarea", x + 12 + (colW + 6) * 2, curY, colW - 6, mouseX, mouseY);

        curY += 24;

        String addBtnText = editingEntIndex >= 0 ? "§a✔ Save Rule" : "§b+ Add Rule";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 110 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 98, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        boolean presetHover = mouseX >= x + 116 && mouseX <= x + 230 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§ePreset Minion Hash", x + 116, curY, 114, 18, presetHover, -1, 0x22FFAA00, 0x44FFAA00);

        if (editingEntIndex >= 0) {
            boolean canHover = mouseX >= x + 236 && mouseX <= x + 300 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 236, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.EntityHideRule> list = s.hiddenEntities;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No entity hider rules configured. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.EntityHideRule rule = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 32;
            int onX = editX - 38;

            String label = (rule.enabled ? "§e" : "§c§m") + rule.matcher;
            if (rule.island != null && !rule.island.isEmpty()) label += " §7[" + rule.island + "]";
            if (rule.subarea != null && !rule.subarea.isEmpty()) label += " §d(" + rule.subarea + ")";
            g.text(font, label, x + 16, rowY + 4, rule.enabled ? 0xFFFFAA00 : 0xFFEF4444, false);

            boolean onHover = mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, rule.enabled ? "§aON" : "§cOFF", onX, rowY, 34, 18, onHover,
                    rule.enabled ? 0xFF10B981 : 0xFFEF4444, rule.enabled ? 0x3310B981 : 0x22EF4444, 0x6610B981);

            boolean editHover = mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§e✎", editX, rowY, 28, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleEntityHiderClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.hiddenEntities == null) s.hiddenEntities = new ArrayList<>();
        List<BomboConfig.EntityHideRule> list = s.hiddenEntities;

        int curY = y + 26;
        int colW = (w - 36) / 3;

        if (checkFieldClick(x + 12, curY, colW, 18, "entMatcher", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 6, curY, colW - 6, 18, "entIsland", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (colW + 6) * 2, curY, colW - 6, 18, "entSubarea", mouseX, mouseY)) return true;

        curY += 24;

        if (mouseX >= x + 12 && mouseX <= x + 110 && mouseY >= curY && mouseY <= curY + 18) {
            if (!entMatcherInput.trim().isEmpty()) {
                if (editingEntIndex >= 0 && editingEntIndex < list.size()) {
                    BomboConfig.EntityHideRule rule = list.get(editingEntIndex);
                    rule.matcher = entMatcherInput.trim();
                    rule.island = entIslandInput.trim();
                    rule.subarea = entSubareaInput.trim();
                } else {
                    list.add(new BomboConfig.EntityHideRule(entMatcherInput.trim(), entIslandInput.trim(), entSubareaInput.trim()));
                }
                editingEntIndex = -1;
                entMatcherInput = "";
                entIslandInput = "";
                entSubareaInput = "";
                BomboConfig.save();
                return true;
            }
        }

        if (mouseX >= x + 116 && mouseX <= x + 230 && mouseY >= curY && mouseY <= curY + 18) {
            entMatcherInput = "c526a56b80f56a6870f891d1d46fa7f8c71494cad24e94326da84b3829417b81";
            entIslandInput = "Garden";
            return true;
        }

        if (editingEntIndex >= 0 && mouseX >= x + 236 && mouseX <= x + 300 && mouseY >= curY && mouseY <= curY + 18) {
            editingEntIndex = -1;
            entMatcherInput = "";
            entIslandInput = "";
            entSubareaInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.EntityHideRule rule = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 32;
            int onX = editX - 38;

            int finalI = i;
            if (mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18) {
                rule.enabled = !rule.enabled;
                BomboConfig.save();
                return true;
            }

            if (mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18) {
                editingEntIndex = finalI;
                entMatcherInput = rule.matcher != null ? rule.matcher : "";
                entIslandInput = rule.island != null ? rule.island : "";
                entSubareaInput = rule.subarea != null ? rule.subarea : "";
                return true;
            }

            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18) {
                BomboConfig.EntityHideRule removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingEntIndex == finalI) editingEntIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // BLOCK HIDER & REPLACEMENTS CARD
    // ---------------------------------------------------------------------------------------------
    public static String blkFromInput = "";
    public static String blkToInput = "";
    public static String blkIslandInput = "";
    public static String blkSubareaInput = "";
    public static boolean blkKeepProps = true;
    public static int editingBlkIndex = -1;

    public static int getBlockHiderCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return 130;
        int count = s.blockReplacements != null ? s.blockReplacements.size() : 0;
        return 90 + Math.max(1, count) * 24 + 10;
    }

    public static void renderBlockHiderCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);
        g.text(font, "§6§lBlock Replacements & Hider §7(Tab to complete block IDs)", x + 12, y + 8, ConfigUITheme.ACCENT_GOLD, false);

        int curY = y + 26;
        int colW = (w - 36) / 5;

        renderCleanInputField(g, font, "From Block (e.g. sugar_cane)", blkFromInput, "blkFrom", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "To Block (air / glass_pane)", blkToInput, "blkTo", x + 12 + colW + 6, curY, colW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Island Filter", blkIslandInput, "blkIsland", x + 12 + (colW + 6) * 2, curY, colW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Subarea Filter (p1..p5)", blkSubareaInput, "blkSubarea", x + 12 + (colW + 6) * 3, curY, colW - 6, mouseX, mouseY);
        renderInlineToggle(g, font, "KeepProps", blkKeepProps, x + 12 + (colW + 6) * 4, curY, colW - 6, mouseX, mouseY);

        curY += 24;

        String addBtnText = editingBlkIndex >= 0 ? "§a✔ Save Rule" : "§b+ Add Rule";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 110 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 98, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        boolean presetHover = mouseX >= x + 116 && mouseX <= x + 230 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, "§ePreset Cane->Pane", x + 116, curY, 114, 18, presetHover, -1, 0x22FFAA00, 0x44FFAA00);

        if (editingBlkIndex >= 0) {
            boolean canHover = mouseX >= x + 236 && mouseX <= x + 300 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 236, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.BlockReplaceRule> list = s.blockReplacements;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No block replacement rules configured. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.BlockReplaceRule rule = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 32;
            int onX = editX - 38;

            String label = (rule.enabled ? "§e" : "§c§m") + rule.fromBlock + " §7➡ §a" + (rule.toBlock.isEmpty() ? "AIR" : rule.toBlock);
            if (rule.preserveProperties) label += " §8(props)";
            if (rule.island != null && !rule.island.isEmpty()) label += " §7[" + rule.island + "]";
            if (rule.subarea != null && !rule.subarea.isEmpty()) label += " §d(" + rule.subarea + ")";
            g.text(font, label, x + 16, rowY + 4, rule.enabled ? 0xFFFFAA00 : 0xFFEF4444, false);

            boolean onHover = mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, rule.enabled ? "§aON" : "§cOFF", onX, rowY, 34, 18, onHover,
                    rule.enabled ? 0xFF10B981 : 0xFFEF4444, rule.enabled ? 0x3310B981 : 0x22EF4444, 0x6610B981);

            boolean editHover = mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§e✎", editX, rowY, 28, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleBlockHiderClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.blockReplacements == null) s.blockReplacements = new ArrayList<>();
        List<BomboConfig.BlockReplaceRule> list = s.blockReplacements;

        int curY = y + 26;
        int colW = (w - 36) / 5;

        if (checkFieldClick(x + 12, curY, colW, 18, "blkFrom", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 6, curY, colW - 6, 18, "blkTo", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (colW + 6) * 2, curY, colW - 6, 18, "blkIsland", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (colW + 6) * 3, curY, colW - 6, 18, "blkSubarea", mouseX, mouseY)) return true;

        if (mouseX >= x + 12 + (colW + 6) * 4 && mouseX <= x + 12 + (colW + 6) * 4 + colW - 6 && mouseY >= curY && mouseY <= curY + 18) {
            blkKeepProps = !blkKeepProps;
            return true;
        }

        curY += 24;

        if (mouseX >= x + 12 && mouseX <= x + 110 && mouseY >= curY && mouseY <= curY + 18) {
            if (!blkFromInput.trim().isEmpty()) {
                if (editingBlkIndex >= 0 && editingBlkIndex < list.size()) {
                    BomboConfig.BlockReplaceRule rule = list.get(editingBlkIndex);
                    rule.fromBlock = blkFromInput.trim();
                    rule.toBlock = blkToInput.trim();
                    rule.island = blkIslandInput.trim();
                    rule.subarea = blkSubareaInput.trim();
                    rule.preserveProperties = blkKeepProps;
                } else {
                    list.add(new BomboConfig.BlockReplaceRule(blkFromInput.trim(), blkToInput.trim(), blkIslandInput.trim(), blkSubareaInput.trim(), blkKeepProps));
                }
                editingBlkIndex = -1;
                blkFromInput = "";
                blkToInput = "";
                blkIslandInput = "";
                blkSubareaInput = "";
                BomboConfig.save();
                return true;
            }
        }

        if (mouseX >= x + 116 && mouseX <= x + 230 && mouseY >= curY && mouseY <= curY + 18) {
            blkFromInput = "sugar_cane";
            blkToInput = "glass_pane";
            blkIslandInput = "Garden";
            return true;
        }

        if (editingBlkIndex >= 0 && mouseX >= x + 236 && mouseX <= x + 300 && mouseY >= curY && mouseY <= curY + 18) {
            editingBlkIndex = -1;
            blkFromInput = "";
            blkToInput = "";
            blkIslandInput = "";
            blkSubareaInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.BlockReplaceRule rule = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 32;
            int onX = editX - 38;

            int finalI = i;
            if (mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18) {
                rule.enabled = !rule.enabled;
                BomboConfig.save();
                return true;
            }

            if (mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18) {
                editingBlkIndex = finalI;
                blkFromInput = rule.fromBlock != null ? rule.fromBlock : "";
                blkToInput = rule.toBlock != null ? rule.toBlock : "";
                blkIslandInput = rule.island != null ? rule.island : "";
                blkSubareaInput = rule.subarea != null ? rule.subarea : "";
                blkKeepProps = rule.preserveProperties;
                return true;
            }

            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18) {
                BomboConfig.BlockReplaceRule removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingBlkIndex == finalI) editingBlkIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // AUTO PROFILE RULES CARD
    // ---------------------------------------------------------------------------------------------
    public static String autoRuleProfileInput = "";
    public static String autoRuleIslandInput = "";
    public static String autoRuleSubareaInput = "";
    public static String autoRuleClassInput = "";
    public static String autoRuleArmorInput = "";
    public static String autoRuleChatInput = "";
    public static int editingAutoRuleIndex = -1;

    public static int getAutoProfileRulesCardHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return 140;
        int count = s.autoProfileRules != null ? s.autoProfileRules.size() : 0;
        return 120 + Math.max(1, count) * 24 + 10;
    }

    public static void renderAutoProfileRulesCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);
        g.text(font, "§6§lAuto Profile Swap Rules & Triggers", x + 12, y + 8, ConfigUITheme.ACCENT_GOLD, false);

        int curY = y + 26;
        int colW = (w - 36) / 3;

        // Row 1
        renderCleanInputField(g, font, "Target Profile (e.g. mining)", autoRuleProfileInput, "autoRuleProfile", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Island Filter (e.g. Garden)", autoRuleIslandInput, "autoRuleIsland", x + 12 + colW + 6, curY, colW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Subarea Filter (e.g. p1)", autoRuleSubareaInput, "autoRuleSubarea", x + 12 + (colW + 6) * 2, curY, colW - 6, mouseX, mouseY);

        curY += 24;

        // Row 2
        renderCleanInputField(g, font, "Class Filter (e.g. Mage)", autoRuleClassInput, "autoRuleClass", x + 12, curY, colW, mouseX, mouseY);
        renderCleanInputField(g, font, "Armor Filter (e.g. necron, !storm)", autoRuleArmorInput, "autoRuleArmor", x + 12 + colW + 6, curY, colW - 6, mouseX, mouseY);
        renderCleanInputField(g, font, "Chat Trigger Message", autoRuleChatInput, "autoRuleChat", x + 12 + (colW + 6) * 2, curY, colW - 6, mouseX, mouseY);

        curY += 24;

        String addBtnText = editingAutoRuleIndex >= 0 ? "§a✔ Save Rule" : "§b+ Add Rule";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 110 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 98, 18, addHover, -1, 0x3300E5FF, 0x6600E5FF);

        if (editingAutoRuleIndex >= 0) {
            boolean canHover = mouseX >= x + 116 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 116, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        BomboConfig.Settings s = BomboConfig.get();
        List<BomboConfig.ProfileAutoSwapRule> list = s != null ? s.autoProfileRules : null;
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No auto profile swap rules configured. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.ProfileAutoSwapRule rule = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 32;
            int onX = editX - 38;

            StringBuilder sb = new StringBuilder();
            sb.append(rule.enabled ? "§e➡ §b" : "§c§m➡ §7").append(rule.targetProfile);
            if (rule.island != null && !rule.island.isEmpty()) sb.append(" §7[").append(rule.island).append("]");
            if (rule.subarea != null && !rule.subarea.isEmpty()) sb.append(" §d(").append(rule.subarea).append(")");
            if (rule.dungeonClass != null && !rule.dungeonClass.isEmpty()) sb.append(" §6<").append(rule.dungeonClass).append(">");
            if (rule.armorRequirement != null && !rule.armorRequirement.isEmpty()) sb.append(" §a{").append(rule.armorRequirement).append("}");
            if (rule.chatMessage != null && !rule.chatMessage.isEmpty()) sb.append(" §f\"").append(rule.chatMessage).append("\"");

            g.text(font, sb.toString(), x + 16, rowY + 4, rule.enabled ? 0xFFFFAA00 : 0xFFEF4444, false);

            boolean onHover = mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, rule.enabled ? "§aON" : "§cOFF", onX, rowY, 34, 18, onHover,
                    rule.enabled ? 0xFF10B981 : 0xFFEF4444, rule.enabled ? 0x3310B981 : 0x22EF4444, 0x6610B981);

            boolean editHover = mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§e✎", editX, rowY, 28, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY, 24, 18, delHover, -1, 0x33EF4444, 0x66EF4444);

            curY += 24;
        }
    }

    public static boolean handleAutoProfileRulesClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return false;
        if (s.autoProfileRules == null) s.autoProfileRules = new ArrayList<>();
        List<BomboConfig.ProfileAutoSwapRule> list = s.autoProfileRules;

        int curY = y + 26;
        int colW = (w - 36) / 3;

        if (checkFieldClick(x + 12, curY, colW, 18, "autoRuleProfile", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 6, curY, colW - 6, 18, "autoRuleIsland", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (colW + 6) * 2, curY, colW - 6, 18, "autoRuleSubarea", mouseX, mouseY)) return true;

        curY += 24;

        if (checkFieldClick(x + 12, curY, colW, 18, "autoRuleClass", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + colW + 6, curY, colW - 6, 18, "autoRuleArmor", mouseX, mouseY)) return true;
        if (checkFieldClick(x + 12 + (colW + 6) * 2, curY, colW - 6, 18, "autoRuleChat", mouseX, mouseY)) return true;

        curY += 24;

        if (mouseX >= x + 12 && mouseX <= x + 110 && mouseY >= curY && mouseY <= curY + 18) {
            if (!autoRuleProfileInput.trim().isEmpty()) {
                if (editingAutoRuleIndex >= 0 && editingAutoRuleIndex < list.size()) {
                    BomboConfig.ProfileAutoSwapRule rule = list.get(editingAutoRuleIndex);
                    rule.targetProfile = autoRuleProfileInput.trim();
                    rule.island = autoRuleIslandInput.trim();
                    rule.subarea = autoRuleSubareaInput.trim();
                    rule.dungeonClass = autoRuleClassInput.trim();
                    rule.armorRequirement = autoRuleArmorInput.trim();
                    rule.chatMessage = autoRuleChatInput.trim();
                } else {
                    list.add(new BomboConfig.ProfileAutoSwapRule(autoRuleProfileInput.trim(), autoRuleIslandInput.trim(), autoRuleSubareaInput.trim(), autoRuleClassInput.trim(), autoRuleArmorInput.trim(), autoRuleChatInput.trim()));
                }
                editingAutoRuleIndex = -1;
                autoRuleProfileInput = "";
                autoRuleIslandInput = "";
                autoRuleSubareaInput = "";
                autoRuleClassInput = "";
                autoRuleArmorInput = "";
                autoRuleChatInput = "";
                activeFocusedField = null;
                BomboConfig.save();
                return true;
            }
        }

        if (editingAutoRuleIndex >= 0 && mouseX >= x + 116 && mouseX <= x + 180 && mouseY >= curY && mouseY <= curY + 18) {
            editingAutoRuleIndex = -1;
            autoRuleProfileInput = "";
            autoRuleIslandInput = "";
            autoRuleSubareaInput = "";
            autoRuleClassInput = "";
            autoRuleArmorInput = "";
            autoRuleChatInput = "";
            return true;
        }

        curY += 32;

        for (int i = 0; i < list.size(); i++) {
            BomboConfig.ProfileAutoSwapRule rule = list.get(i);
            int rowY = curY;
            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 32;
            int onX = editX - 38;

            int finalI = i;
            if (mouseX >= onX && mouseX <= onX + 34 && mouseY >= rowY && mouseY <= rowY + 18) {
                rule.enabled = !rule.enabled;
                BomboConfig.save();
                return true;
            }

            if (mouseX >= editX && mouseX <= editX + 28 && mouseY >= rowY && mouseY <= rowY + 18) {
                editingAutoRuleIndex = finalI;
                autoRuleProfileInput = rule.targetProfile != null ? rule.targetProfile : "";
                autoRuleIslandInput = rule.island != null ? rule.island : "";
                autoRuleSubareaInput = rule.subarea != null ? rule.subarea : "";
                autoRuleClassInput = rule.dungeonClass != null ? rule.dungeonClass : "";
                autoRuleArmorInput = rule.armorRequirement != null ? rule.armorRequirement : "";
                autoRuleChatInput = rule.chatMessage != null ? rule.chatMessage : "";
                return true;
            }

            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY && mouseY <= rowY + 18) {
                BomboConfig.ProfileAutoSwapRule removed = list.remove(finalI);
                undoStack.push(() -> {
                    list.add(Math.min(finalI, list.size()), removed);
                    BomboConfig.save();
                });
                if (editingAutoRuleIndex == finalI) editingAutoRuleIndex = -1;
                BomboConfig.save();
                return true;
            }

            curY += 24;
        }

        activeFocusedField = null;
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // AUTO SEQUENCES MANAGER (Auto Category)
    // ---------------------------------------------------------------------------------------------
    public static String autoSeqNameInput = "";
    public static String autoSeqKeyInput = "";
    public static boolean autoSeqKeyIsListening = false;
    public static boolean autoSeqLoop = false;
    public static String autoSeqLoopDelay = "400";
    /** Randomisation percentage applied to every wait in the sequence. */
    public static String autoSeqJitterInput = "30";
    public static int editingAutoSeqIndex = -1;
    public static int expandedSeqActionIndex = -1;

    // Action builder inputs
    public static me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType newActionType = me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType.CLICK_SLOT;
    public static String actionSlotInput = "";
    public static String actionItemInput = "";
    public static String actionClickType = "LEFT"; // LEFT, RIGHT, SHIFT_LEFT, DROP
    public static String actionCmdInput = "";
    public static boolean actionRightClick = true;
    public static String actionEntityInput = "";
    public static String actionRadiusInput = "5";
    public static String actionRepeatInput = "1";
    public static String actionDelayInput = "200";

    public static int getAutoSequencesCardHeight() {
        List<me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence> list = me.bombo.bomboaddons.features.auto.AutoSequenceManager.getSequences();
        int h = 95;
        if (list == null || list.isEmpty()) {
            return h + 40;
        }
        for (int i = 0; i < list.size(); i++) {
            me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence seq = list.get(i);
            h += 24; // Sequence header row
            if (seq.actions != null && !seq.actions.isEmpty()) {
                h += seq.actions.size() * 20 + 4;
            }
            if (expandedSeqActionIndex == i) {
                h += 56; // Action builder sub-card
            }
            h += 4;
        }
        return h + 30;
    }

    public static void renderAutoSequencesCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        ConfigUITheme.drawCard(g, x, y, w, h, hovered);

        int curY = y + 8;
        g.text(font, "§6§lCUSTOM AUTOMATION SEQUENCES", x + 12, curY, ConfigUITheme.ACCENT_GOLD, false);
        curY += 18;

        // Sequence Form Fields
        int nameW = (w - 36) / 2;
        renderCleanInputField(g, font, "Sequence Name (e.g. Plushie Macro)", autoSeqNameInput, "autoSeqName", x + 12, curY, nameW, mouseX, mouseY);

        int keyX = x + 12 + nameW + 6;
        int keyW = 105;
        String keyDisp = autoSeqKeyIsListening ? "§e[PRESS KEY]" : (autoSeqKeyInput.isEmpty() ? "§8Keybind (Click)" : "§b" + me.bombo.bomboaddons.ClickLogic.getKeyDisplayName(autoSeqKeyInput));
        boolean keyHover = mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, keyDisp, keyX, curY, keyW, 18, keyHover, -1, autoSeqKeyIsListening ? 0x44FFAA00 : 0x221E293B, autoSeqKeyIsListening ? 0xFFFFAA00 : 0x55FFFFFF);

        int loopX = keyX + keyW + 6;
        int loopW = 75;
        boolean loopHover = mouseX >= loopX && mouseX <= loopX + loopW && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, autoSeqLoop ? "§aLoop: ON" : "§7Loop: OFF", loopX, curY, loopW, 18, loopHover, -1, autoSeqLoop ? 0x3310B981 : 0x221E293B, autoSeqLoop ? 0x6610B981 : 0x44FFFFFF);

        int delayX = loopX + loopW + 6;
        int delayW = Math.max(50, (x + w - 12) - delayX);
        int halfDelay = Math.max(45, delayW / 2 - 3);
        renderCleanInputField(g, font, "Loop ms", autoSeqLoopDelay, "autoSeqDelay", delayX, curY, halfDelay, mouseX, mouseY);
        renderCleanInputField(g, font, "Jitter %", autoSeqJitterInput, "autoSeqJitter",
                delayX + halfDelay + 6, curY, Math.max(40, delayW - halfDelay - 6), mouseX, mouseY);

        curY += 24;

        String addBtnText = editingAutoSeqIndex >= 0 ? "§e✔ Save Sequence" : "§a+ Add Sequence";
        boolean addHover = mouseX >= x + 12 && mouseX <= x + 140 && mouseY >= curY && mouseY <= curY + 18;
        ConfigUITheme.drawPillButton(g, font, addBtnText, x + 12, curY, 130, 18, addHover, -1, 0x3310B981, 0x6610B981);

        if (editingAutoSeqIndex >= 0) {
            boolean canHover = mouseX >= x + 148 && mouseX <= x + 212 && mouseY >= curY && mouseY <= curY + 18;
            ConfigUITheme.drawPillButton(g, font, "§cCancel", x + 148, curY, 64, 18, canHover, -1, 0x33EF4444, 0x66EF4444);
        }

        curY += 26;
        g.fill(x + 12, curY, x + w - 12, curY + 1, ConfigUITheme.getDividerColor());
        curY += 6;

        List<me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence> list = me.bombo.bomboaddons.features.auto.AutoSequenceManager.getSequences();
        if (list == null || list.isEmpty()) {
            g.text(font, "§8No automation sequences created yet. Add one above!", x + 16, curY + 4, ConfigUITheme.getTextMuted(), false);
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence seq = list.get(i);
            int rowY = curY;
            boolean isRunning = me.bombo.bomboaddons.features.auto.AutoSequenceManager.isRunning(seq);

            int rowBg = isRunning ? 0x3310B981 : ((i % 2 == 0) ? 0x1A0F172A : 0x221E293B);
            g.fill(x + 12, rowY, x + w - 12, rowY + 22, rowBg);

            // [ON] / [OFF]
            int onBtnX = x + 16;
            boolean onHover = mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, seq.enabled ? "§aON" : "§cOFF", onBtnX, rowY + 2, 32, 18, onHover, seq.enabled ? 0xFF10B981 : 0xFF94A3B8, seq.enabled ? 0x2210B981 : 0x1AFFFFFF, 0x44FFFFFF);

            // [▶ RUN] / [⏹ STOP]
            int runBtnX = onBtnX + 36;
            boolean runHover = mouseX >= runBtnX && mouseX <= runBtnX + 46 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            String runText = isRunning ? "§c⏹ STOP" : "§a▶ RUN";
            ConfigUITheme.drawPillButton(g, font, runText, runBtnX, rowY + 2, 46, 18, runHover, -1, isRunning ? 0x33EF4444 : 0x2210B981, isRunning ? 0x66EF4444 : 0x5510B981);

            // Key Pill
            int pillX = runBtnX + 50;
            String kName = (seq.triggerKey != null && !seq.triggerKey.isEmpty()) ? me.bombo.bomboaddons.ClickLogic.getKeyDisplayName(seq.triggerKey) : "NO KEY";
            int pillW = Math.max(45, font.width(kName) + 12);
            ConfigUITheme.drawPillButton(g, font, "§b" + kName, pillX, rowY + 2, pillW, 18, false, -1, 0x3300E5FF, 0x6600E5FF);

            // Sequence Name & Loop tag
            int textX = pillX + pillW + 8;
            g.text(font, "§f§l" + (seq.name != null ? seq.name : "Unnamed"), textX, rowY + 7, 0xFFFFFFFF, false);

            if (seq.loop) {
                int loopTagX = textX + font.width("§f§l" + seq.name) + 6;
                g.text(font, "§e[LOOP " + me.bombo.bomboaddons.features.auto.AutoSequenceManager
                        .describeJitter(Math.max(50, seq.loopDelayMs), seq.jitterPercent) + "]",
                        loopTagX, rowY + 7, 0xFFFFAA00, false);
            }

            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 36;
            int actX = editX - 60;

            // [+ Action]
            boolean actHover = mouseX >= actX && mouseX <= actX + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            boolean isExpanded = expandedSeqActionIndex == i;
            ConfigUITheme.drawPillButton(g, font, isExpanded ? "§e▲ Close" : "§b+ Action", actX, rowY + 2, 56, 18, actHover, -1, isExpanded ? 0x33FFAA00 : 0x2200E5FF, 0x55FFFFFF);

            // [Edit]
            boolean editHover = mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, "§eEdit", editX, rowY + 2, 32, 18, editHover, -1, 0x22FFAA00, 0x44FFAA00);

            // [DEL]
            boolean delHover = mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20;
            ConfigUITheme.drawPillButton(g, font, "§c✕", delX, rowY + 2, 24, 18, delHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

            curY += 24;

            // Render existing actions list
            if (seq.actions != null && !seq.actions.isEmpty()) {
                for (int a = 0; a < seq.actions.size(); a++) {
                    me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction action = seq.actions.get(a);
                    int aRowY = curY;
                    g.fill(x + 28, aRowY, x + w - 12, aRowY + 18, 0x150F172A);

                    g.text(font, "§8#" + (a + 1) + " §f" + action.getSummary(), x + 34, aRowY + 5, 0xFFCBD5E1, false);

                    int aRx = x + w - 18;
                    int aDelX = aRx - 20;
                    int aDownX = aDelX - 22;
                    int aUpX = aDownX - 22;

                    // [▲]
                    if (a > 0) {
                        boolean upHover = mouseX >= aUpX && mouseX <= aUpX + 18 && mouseY >= aRowY + 1 && mouseY <= aRowY + 17;
                        ConfigUITheme.drawPillButton(g, font, "▲", aUpX, aRowY + 1, 18, 16, upHover, -1, 0x1AFFFFFF, 0x33FFFFFF);
                    }
                    // [▼]
                    if (a < seq.actions.size() - 1) {
                        boolean downHover = mouseX >= aDownX && mouseX <= aDownX + 18 && mouseY >= aRowY + 1 && mouseY <= aRowY + 17;
                        ConfigUITheme.drawPillButton(g, font, "▼", aDownX, aRowY + 1, 18, 16, downHover, -1, 0x1AFFFFFF, 0x33FFFFFF);
                    }
                    // [✕]
                    boolean aDelHover = mouseX >= aDelX && mouseX <= aDelX + 18 && mouseY >= aRowY + 1 && mouseY <= aRowY + 17;
                    ConfigUITheme.drawPillButton(g, font, "§c✕", aDelX, aRowY + 1, 18, 16, aDelHover, 0xFFFF5555, 0x22EF4444, 0x44EF4444);

                    curY += 20;
                }
            }

            // Render action builder panel if expanded
            if (isExpanded) {
                int boxY = curY;
                int boxH = 52;
                g.fill(x + 24, boxY, x + w - 12, boxY + boxH, 0x331E293B);

                // Row 1: Action Type pills
                int tX = x + 30;
                me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType[] types = me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType.values();
                for (me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType t : types) {
                    boolean isSel = newActionType == t;
                    int tW = font.width(t.displayName) + 12;
                    boolean tHover = mouseX >= tX && mouseX <= tX + tW && mouseY >= boxY + 4 && mouseY <= boxY + 20;
                    ConfigUITheme.drawPillButton(g, font, (isSel ? "§a" : "§7") + t.displayName, tX, boxY + 4, tW, 16, tHover, -1, isSel ? 0x4410B981 : 0x1AFFFFFF, isSel ? 0x8810B981 : 0x33FFFFFF);
                    tX += tW + 4;
                }

                // Row 2: Parameters based on type + Add Button
                int pY = boxY + 26;
                int pX = x + 30;

                switch (newActionType) {
                    case CLICK_SLOT -> {
                        renderCleanInputField(g, font, "Slot # (-1=name)", actionSlotInput, "actSlot", pX, pY, 85, mouseX, mouseY);
                        pX += 90;
                        renderCleanInputField(g, font, "Item Name", actionItemInput, "actItem", pX, pY, 105, mouseX, mouseY);
                        pX += 110;
                        boolean ctHover = mouseX >= pX && mouseX <= pX + 70 && mouseY >= pY && mouseY <= pY + 18;
                        ConfigUITheme.drawPillButton(g, font, "§e" + actionClickType, pX, pY, 70, 18, ctHover, -1, 0x22FFFFFF, 0x44FFFFFF);
                        pX += 75;
                    }
                    case CLOSE_GUI -> {
                        g.text(font, "§7Closes the open container", pX, pY + 5, 0xFF94A3B8, false);
                        pX += 120;
                    }
                    case RUN_COMMAND -> {
                        renderCleanInputField(g, font, "Command / Chat", actionCmdInput, "actCmd", pX, pY, 200, mouseX, mouseY);
                        pX += 205;
                    }
                    case CLICK_WORLD -> {
                        boolean cwHover = mouseX >= pX && mouseX <= pX + 105 && mouseY >= pY && mouseY <= pY + 18;
                        ConfigUITheme.drawPillButton(g, font, actionRightClick ? "§bRight-Click" : "§bLeft-Click", pX, pY, 105, 18, cwHover, -1, 0x2200E5FF, 0x4400E5FF);
                        pX += 110;
                    }
                    case INTERACT_ENTITY -> {
                        renderCleanInputField(g, font, "NPC Name", actionEntityInput, "actEntity", pX, pY, 115, mouseX, mouseY);
                        pX += 120;
                        renderCleanInputField(g, font, "Radius", actionRadiusInput, "actRadius", pX, pY, 50, mouseX, mouseY);
                        pX += 55;
                        boolean npcHover = mouseX >= pX && mouseX <= pX + 70 && mouseY >= pY && mouseY <= pY + 18;
                        ConfigUITheme.drawPillButton(g, font, actionRightClick ? "§bRight-Clk" : "§bLeft-Clk", pX, pY, 70, 18, npcHover, -1, 0x2200E5FF, 0x4400E5FF);
                        pX += 75;
                    }
                    case WAIT -> {
                        g.text(font, "§7Stand still", pX, pY + 5, 0xFF94A3B8, false);
                        pX += 75;
                    }
                }

                // Repeat count
                renderCleanInputField(g, font, "Repeat", actionRepeatInput, "actRepeat", pX, pY, 45, mouseX, mouseY);
                pX += 50;

                // Delay ms input
                renderCleanInputField(g, font, "Delay ms", actionDelayInput, "actDelay", pX, pY, 60, mouseX, mouseY);
                pX += 65;

                // [+ Add Step]
                boolean addStepHover = mouseX >= pX && mouseX <= pX + 65 && mouseY >= pY && mouseY <= pY + 18;
                ConfigUITheme.drawPillButton(g, font, "§a+ Add Step", pX, pY, 65, 18, addStepHover, -1, 0x3310B981, 0x6610B981);

                curY += boxH + 4;
            }

            curY += 4;
        }
    }

    public static boolean handleAutoSequencesCardClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        Font font = Minecraft.getInstance().font;
        int curY = y + 8 + 18;

        int nameW = (w - 36) / 2;
        if (checkFieldClick(x + 12, curY, nameW, 18, "autoSeqName", mouseX, mouseY)) {
            autoSeqKeyIsListening = false;
            return true;
        }

        int keyX = x + 12 + nameW + 6;
        int keyW = 105;
        if (mouseX >= keyX && mouseX <= keyX + keyW && mouseY >= curY && mouseY <= curY + 18) {
            autoSeqKeyIsListening = !autoSeqKeyIsListening;
            activeFocusedField = null;
            return true;
        }

        int loopX = keyX + keyW + 6;
        int loopW = 75;
        if (mouseX >= loopX && mouseX <= loopX + loopW && mouseY >= curY && mouseY <= curY + 18) {
            autoSeqLoop = !autoSeqLoop;
            autoSeqKeyIsListening = false;
            return true;
        }

        int delayX = loopX + loopW + 6;
        int delayW = Math.max(50, (x + w - 12) - delayX);
        int halfDelay = Math.max(45, delayW / 2 - 3);
        if (checkFieldClick(delayX, curY, halfDelay, 18, "autoSeqDelay", mouseX, mouseY)) {
            autoSeqKeyIsListening = false;
            return true;
        }
        int jitterX = delayX + halfDelay + 6;
        int jitterW = Math.max(40, delayW - halfDelay - 6);
        if (checkFieldClick(jitterX, curY, jitterW, 18, "autoSeqJitter", mouseX, mouseY)) {
            autoSeqKeyIsListening = false;
            return true;
        }

        curY += 24;

        // [+ Add Sequence] / [✔ Save Sequence]
        if (mouseX >= x + 12 && mouseX <= x + 140 && mouseY >= curY && mouseY <= curY + 18) {
            autoSeqKeyIsListening = false;
            String name = autoSeqNameInput.trim();
            if (!name.isEmpty()) {
                int loopDelay = 400;
                try {
                    loopDelay = Integer.parseInt(autoSeqLoopDelay.trim());
                } catch (Throwable ignored) {}
                int jitter = 30;
                try {
                    jitter = Integer.parseInt(autoSeqJitterInput.trim());
                } catch (Throwable ignored) {}
                jitter = Math.max(0, Math.min(90, jitter));

                if (editingAutoSeqIndex >= 0) {
                    List<me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence> list = me.bombo.bomboaddons.features.auto.AutoSequenceManager.getSequences();
                    if (editingAutoSeqIndex < list.size()) {
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence existing = list.get(editingAutoSeqIndex);
                        existing.name = name;
                        existing.triggerKey = autoSeqKeyInput.trim();
                        existing.loop = autoSeqLoop;
                        existing.loopDelayMs = Math.max(50, loopDelay);
                        existing.jitterPercent = jitter;
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.save();
                    }
                    editingAutoSeqIndex = -1;
                } else {
                    me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence seq = new me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence(name, autoSeqKeyInput.trim());
                    seq.loop = autoSeqLoop;
                    seq.loopDelayMs = Math.max(50, loopDelay);
                    seq.jitterPercent = jitter;
                    me.bombo.bomboaddons.features.auto.AutoSequenceManager.addSequence(seq);
                }

                autoSeqNameInput = "";
                autoSeqKeyInput = "";
                autoSeqLoop = false;
                autoSeqLoopDelay = "400";
                autoSeqJitterInput = "30";
                activeFocusedField = null;
                return true;
            }
        }

        // [Cancel]
        if (editingAutoSeqIndex >= 0 && mouseX >= x + 148 && mouseX <= x + 212 && mouseY >= curY && mouseY <= curY + 18) {
            editingAutoSeqIndex = -1;
            autoSeqNameInput = "";
            autoSeqKeyInput = "";
            autoSeqLoop = false;
            autoSeqLoopDelay = "400";
            autoSeqJitterInput = "30";
            autoSeqKeyIsListening = false;
            return true;
        }

        curY += 32;

        List<me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence> list = me.bombo.bomboaddons.features.auto.AutoSequenceManager.getSequences();
        if (list == null || list.isEmpty()) {
            activeFocusedField = null;
            return false;
        }

        for (int i = 0; i < list.size(); i++) {
            me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence seq = list.get(i);
            int rowY = curY;

            int onBtnX = x + 16;
            if (mouseX >= onBtnX && mouseX <= onBtnX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                seq.enabled = !seq.enabled;
                me.bombo.bomboaddons.features.auto.AutoSequenceManager.save();
                return true;
            }

            int runBtnX = onBtnX + 36;
            if (mouseX >= runBtnX && mouseX <= runBtnX + 46 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                // Execution lives in the flavor runtime; the legit build cannot run a sequence.
                me.bombo.bomboaddons.flavor.Flavor.get().toggleSequenceByIndex(i, "Config GUI", "RUN button");
                return true;
            }

            int rx = x + w - 18;
            int delX = rx - 24;
            int editX = delX - 36;
            int actX = editX - 60;

            if (mouseX >= actX && mouseX <= actX + 56 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                expandedSeqActionIndex = (expandedSeqActionIndex == i ? -1 : i);
                return true;
            }

            if (mouseX >= editX && mouseX <= editX + 32 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                editingAutoSeqIndex = i;
                autoSeqNameInput = seq.name != null ? seq.name : "";
                autoSeqKeyInput = seq.triggerKey != null ? seq.triggerKey : "";
                autoSeqLoop = seq.loop;
                autoSeqLoopDelay = String.valueOf(seq.loopDelayMs > 0 ? seq.loopDelayMs : 400);
                autoSeqJitterInput = String.valueOf(seq.jitterPercent >= 0 ? seq.jitterPercent : 30);
                autoSeqKeyIsListening = false;
                return true;
            }

            if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= rowY + 2 && mouseY <= rowY + 20) {
                me.bombo.bomboaddons.features.auto.AutoSequenceManager.removeSequence(i);
                if (expandedSeqActionIndex == i) expandedSeqActionIndex = -1;
                if (editingAutoSeqIndex == i) editingAutoSeqIndex = -1;
                return true;
            }

            curY += 24;

            // Existing actions list clicks
            if (seq.actions != null && !seq.actions.isEmpty()) {
                for (int a = 0; a < seq.actions.size(); a++) {
                    int aRowY = curY;
                    int aRx = x + w - 18;
                    int aDelX = aRx - 20;
                    int aDownX = aDelX - 22;
                    int aUpX = aDownX - 22;

                    // [▲]
                    if (a > 0 && mouseX >= aUpX && mouseX <= aUpX + 18 && mouseY >= aRowY + 1 && mouseY <= aRowY + 17) {
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction moved = seq.actions.remove(a);
                        seq.actions.add(a - 1, moved);
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.save();
                        return true;
                    }
                    // [▼]
                    if (a < seq.actions.size() - 1 && mouseX >= aDownX && mouseX <= aDownX + 18 && mouseY >= aRowY + 1 && mouseY <= aRowY + 17) {
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction moved = seq.actions.remove(a);
                        seq.actions.add(a + 1, moved);
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.save();
                        return true;
                    }
                    // [✕]
                    if (mouseX >= aDelX && mouseX <= aDelX + 18 && mouseY >= aRowY + 1 && mouseY <= aRowY + 17) {
                        seq.actions.remove(a);
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.save();
                        return true;
                    }

                    curY += 20;
                }
            }

            // Action builder clicks
            if (expandedSeqActionIndex == i) {
                int boxY = curY;
                int tX = x + 30;
                me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType[] types = me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType.values();
                for (me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType t : types) {
                    int tW = font.width(t.displayName) + 12;
                    if (mouseX >= tX && mouseX <= tX + tW && mouseY >= boxY + 4 && mouseY <= boxY + 20) {
                        newActionType = t;
                        return true;
                    }
                    tX += tW + 4;
                }

                int pY = boxY + 26;
                int pX = x + 30;

                switch (newActionType) {
                    case CLICK_SLOT -> {
                        if (checkFieldClick(pX, pY, 85, 18, "actSlot", mouseX, mouseY)) return true;
                        pX += 90;
                        if (checkFieldClick(pX, pY, 105, 18, "actItem", mouseX, mouseY)) return true;
                        pX += 110;
                        if (mouseX >= pX && mouseX <= pX + 70 && mouseY >= pY && mouseY <= pY + 18) {
                            actionClickType = switch (actionClickType) {
                                case "LEFT" -> "RIGHT";
                                case "RIGHT" -> "SHIFT_LEFT";
                                case "SHIFT_LEFT" -> "DROP";
                                default -> "LEFT";
                            };
                            return true;
                        }
                        pX += 75;
                    }
                    case CLOSE_GUI -> {
                        pX += 120;
                    }
                    case RUN_COMMAND -> {
                        if (checkFieldClick(pX, pY, 200, 18, "actCmd", mouseX, mouseY)) return true;
                        pX += 205;
                    }
                    case CLICK_WORLD -> {
                        if (mouseX >= pX && mouseX <= pX + 105 && mouseY >= pY && mouseY <= pY + 18) {
                            actionRightClick = !actionRightClick;
                            return true;
                        }
                        pX += 110;
                    }
                    case INTERACT_ENTITY -> {
                        if (checkFieldClick(pX, pY, 115, 18, "actEntity", mouseX, mouseY)) return true;
                        pX += 120;
                        if (checkFieldClick(pX, pY, 50, 18, "actRadius", mouseX, mouseY)) return true;
                        pX += 55;
                        if (mouseX >= pX && mouseX <= pX + 70 && mouseY >= pY && mouseY <= pY + 18) {
                            actionRightClick = !actionRightClick;
                            return true;
                        }
                        pX += 75;
                    }
                    case WAIT -> {
                        pX += 75;
                    }
                }

                if (checkFieldClick(pX, pY, 45, 18, "actRepeat", mouseX, mouseY)) return true;
                pX += 50;

                if (checkFieldClick(pX, pY, 60, 18, "actDelay", mouseX, mouseY)) return true;
                pX += 65;

                // [+ Add Step]
                if (mouseX >= pX && mouseX <= pX + 65 && mouseY >= pY && mouseY <= pY + 18) {
                    int delay = 200;
                    try {
                        delay = Integer.parseInt(actionDelayInput.trim());
                    } catch (Throwable ignored) {}
                    int repeat = 1;
                    try {
                        repeat = Integer.parseInt(actionRepeatInput.trim());
                    } catch (Throwable ignored) {}
                    repeat = Math.max(1, Math.min(1000, repeat));

                    me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction action = null;
                    switch (newActionType) {
                        case CLICK_SLOT -> {
                            int slotIdx = -1;
                            try {
                                slotIdx = Integer.parseInt(actionSlotInput.trim());
                            } catch (Throwable ignored) {}
                            action = me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction.clickSlot(slotIdx, actionItemInput.trim(), actionClickType, delay);
                        }
                        case CLOSE_GUI -> action = me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction.closeGui(delay);
                        case RUN_COMMAND -> action = me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction.runCommand(actionCmdInput.trim(), delay);
                        case CLICK_WORLD -> action = me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction.clickWorld(actionRightClick, delay);
                        case INTERACT_ENTITY -> {
                            double radius = 5.0D;
                            try {
                                radius = Double.parseDouble(actionRadiusInput.trim());
                            } catch (Throwable ignored) {}
                            action = me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction.interactEntity(
                                    actionEntityInput.trim(), Math.max(1.0D, Math.min(32.0D, radius)), actionRightClick, delay);
                        }
                        case WAIT -> action = me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction.waitDelay(delay);
                    }

                    if (action != null) {
                        action.repeatCount = repeat;
                        seq.actions.add(action);
                        me.bombo.bomboaddons.features.auto.AutoSequenceManager.save();
                        actionSlotInput = "";
                        actionItemInput = "";
                        actionCmdInput = "";
                        actionEntityInput = "";
                        actionRadiusInput = "5";
                        actionRepeatInput = "1";
                        actionDelayInput = "200";
                        activeFocusedField = null;
                        return true;
                    }
                }

                curY += 56;
            }

            curY += 4;
        }

        activeFocusedField = null;
        return false;
    }
}
