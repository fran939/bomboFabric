package me.bombo.bomboaddons.features.swapper;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

public class InventorySlotColorScreen extends Screen {
    private final Screen parent;

    private int winX, winY, winW, winH;
    private final int headerH = 40;

    // Selected slot to customize (0..8), -1 for none
    private int selectedSlot = 0;

    // Custom Hex input & cursor
    private String customHexInput = "#EF4444";
    private int hexCursorPos = 7;
    private boolean hexFocused = false;
    private boolean hexSelectedAll = false;
    private final Deque<String> hexUndoStack = new ArrayDeque<>();
    private final Deque<String> hexRedoStack = new ArrayDeque<>();

    private void pushHexUndo() {
        hexUndoStack.push(customHexInput);
        if (hexUndoStack.size() > 50) hexUndoStack.removeLast();
        hexRedoStack.clear();
    }

    // Editing line color directly
    private boolean editingLineColor = false;

    // Full interactive Color Picker modal state
    private boolean colorPickerOpen = false;
    private boolean colorPickerForLine = false;
    private float pickerHue = 0.0f; // 0..360
    private float pickerSat = 1.0f; // 0..1
    private float pickerVal = 1.0f; // 0..1
    private int pickerColor = 0xFFEF4444;
    private int pickerOriginalColor = 0xFFEF4444;
    private boolean draggingSV = false;
    private boolean draggingHue = false;

    private static final int[] PALETTE = new int[]{
            0xFFEF4444, // Red
            0xFFF97316, // Orange
            0xFFFACC15, // Yellow
            0xFF22C55E, // Green
            0xFF10B981, // Emerald
            0xFF06B6D4, // Cyan
            0xFF38BDF8, // Light Blue
            0xFF3B82F6, // Blue
            0xFF8B5CF6, // Purple
            0xFFD946EF, // Magenta
            0xFFEC4899, // Pink
            0xFFF8FAFC  // White
    };

    private static final String[] PALETTE_NAMES = new String[]{
            "Red", "Orange", "Yellow", "Green", "Emerald", "Cyan", "Sky", "Blue", "Purple", "Magenta", "Pink", "White"
    };

    public InventorySlotColorScreen(Screen parent) {
        super(Component.literal("Hotbar Slot Colors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        InventorySlotSwapManager.init();
        int targetW = 760;
        int targetH = 490;
        this.winW = Math.min(this.width - 20, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        int curCol = InventorySlotSwapManager.getSlotColor(selectedSlot);
        this.customHexInput = String.format("#%06X", (curCol & 0xFFFFFF));
        this.hexCursorPos = this.customHexInput.length();
    }

    private static int parseHex(String hex, int fallback) {
        if (hex == null) return fallback;
        String clean = hex.trim().replace("#", "");
        try {
            if (clean.length() == 6) {
                int rgb = Integer.parseInt(clean, 16);
                return 0xFF000000 | rgb;
            } else if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public static int hsvToRgb(float h, float s, float v) {
        int r = 0, g = 0, b = 0;
        if (s <= 0.0f) {
            r = g = b = Math.round(v * 255);
        } else {
            float hue = (h % 360f + 360f) % 360f;
            float sector = hue / 60.0f;
            int i = (int) Math.floor(sector);
            float f = sector - i;
            float p = v * (1.0f - s);
            float q = v * (1.0f - s * f);
            float t = v * (1.0f - s * (1.0f - f));
            switch (i) {
                case 0: r = Math.round(v * 255); g = Math.round(t * 255); b = Math.round(p * 255); break;
                case 1: r = Math.round(q * 255); g = Math.round(v * 255); b = Math.round(p * 255); break;
                case 2: r = Math.round(p * 255); g = Math.round(v * 255); b = Math.round(t * 255); break;
                case 3: r = Math.round(p * 255); g = Math.round(q * 255); b = Math.round(v * 255); break;
                case 4: r = Math.round(t * 255); g = Math.round(p * 255); b = Math.round(v * 255); break;
                default: r = Math.round(v * 255); g = Math.round(p * 255); b = Math.round(q * 255); break;
            }
        }
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static float[] rgbToHsv(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float rf = r / 255.0f;
        float gf = g / 255.0f;
        float bf = b / 255.0f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;
        float h = 0f;
        if (delta > 0.00001f) {
            if (max == rf) {
                h = 60.0f * (((gf - bf) / delta) % 6.0f);
            } else if (max == gf) {
                h = 60.0f * (((bf - rf) / delta) + 2.0f);
            } else {
                h = 60.0f * (((rf - gf) / delta) + 4.0f);
            }
        }
        if (h < 0) h += 360.0f;
        float s = max <= 0 ? 0 : delta / max;
        float v = max;
        return new float[]{h, s, v};
    }

    private void openColorPicker(boolean forLine) {
        this.colorPickerForLine = forLine;
        int current = forLine ? BomboConfig.get().swapUnifiedLineColorValue : InventorySlotSwapManager.getSlotColor(selectedSlot);
        this.pickerOriginalColor = current;
        this.pickerColor = current;
        float[] hsv = rgbToHsv(current);
        this.pickerHue = hsv[0];
        this.pickerSat = hsv[1];
        this.pickerVal = hsv[2];
        this.colorPickerOpen = true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Dark backdrop
        g.fill(0, 0, this.width, this.height, 0xD0080A0E);

        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int borderCol = ConfigUITheme.getBorderColor();
        int divCol = ConfigUITheme.getDividerColor();
        Font font = this.font;

        // Window outline & bg
        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);
        g.outline(winX, winY, winW, winH, borderCol);

        // Header
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.fill(winX, winY + headerH, winX + winW, winY + headerH + 1, divCol);

        g.text(font, "§d§lBOMBOADDONS §8| §bHotbar Slot Colors (/b swap color)", winX + 16, winY + 14, 0xFFFFFFFF, false);

        // Reset to Defaults Button
        int rstBtnX = winX + winW - 200;
        int rstBtnY = winY + 9;
        boolean rstHover = mouseX >= rstBtnX && mouseX <= rstBtnX + 120 && mouseY >= rstBtnY && mouseY <= rstBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, "↺ Defaults", rstBtnX, rstBtnY, 120, 22, rstHover,
                0xFFCBD5E1, rstHover ? 0x44EF4444 : 0x22EF4444, 0xFFEF4444);

        // Close button
        int closeBtnX = winX + winW - 60;
        int closeBtnY = winY + 9;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 50 && mouseY >= closeBtnY && mouseY <= closeBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, "Done", closeBtnX, closeBtnY, 50, 22, closeHover,
                0xFFFFFFFF, closeHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        // Grid of 9 hotbar slots
        int startY = winY + headerH + 14;
        int slotColW = (winW - 48) / 9;

        g.text(font, "§7Select a hotbar slot below, then pick a color or enter a custom hex:", winX + 24, startY, 0xFF94A3B8, false);
        startY += 16;

        for (int i = 0; i < 9; i++) {
            int sx = winX + 24 + i * slotColW;
            int sy = startY;
            int sw = slotColW - 6;
            int sh = 74;

            boolean isSel = (!editingLineColor && selectedSlot == i);
            boolean hover = mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh;

            int currentColor = InventorySlotSwapManager.getSlotColor(i);

            // Card background
            g.fill(sx, sy, sx + sw, sy + sh, isSel ? 0xEE1E293B : (hover ? 0xAA1E293B : 0x660F172A));
            g.outline(sx, sy, sw, sh, isSel ? 0xFF38BDF8 : (hover ? 0x8864748B : 0x33475569));

            // Slot label
            g.centeredText(font, "Slot " + (i + 1), sx + sw / 2, sy + 6, isSel ? 0xFF38BDF8 : 0xFFCBD5E1);

            // Color swatch
            int swatchW = sw - 16;
            int swatchH = 22;
            int swatchX = sx + 8;
            int swatchY = sy + 22;

            g.fill(swatchX, swatchY, swatchX + swatchW, swatchY + swatchH, currentColor);
            g.outline(swatchX, swatchY, swatchW, swatchH, 0xFFFFFFFF);

            // Hex label
            String hex = String.format("#%06X", (currentColor & 0xFFFFFF));
            g.centeredText(font, "§8" + hex, sx + sw / 2, sy + 52, 0xFF94A3B8);
        }

        // Section: Custom Color Box & Unified Line Color Row
        int optY = startY + 84;
        g.fill(winX + 24, optY, winX + winW - 24, optY + 1, divCol);
        optY += 10;

        BomboConfig.Settings cfg = BomboConfig.get();

        // Left side: Custom Color Input Box
        g.text(font, (editingLineColor ? "§eUnified Line Color Hex:" : ("§bSlot " + (selectedSlot + 1) + " Custom Color Hex:")), winX + 24, optY + 4, 0xFFFFFFFF, false);

        int hexBoxX = winX + 220;
        int hexBoxW = 96;
        int hexBoxH = 20;
        g.fill(hexBoxX, optY, hexBoxX + hexBoxW, optY + hexBoxH, 0xEE1E293B);
        g.outline(hexBoxX, optY, hexBoxW, hexBoxH, hexFocused ? 0xFF38BDF8 : 0x4464748B);

        // Render text with precise cursor
        int tx = hexBoxX + 6;
        int ty = optY + 6;
        if (hexFocused && hexSelectedAll && !customHexInput.isEmpty()) {
            int selW = font.width(customHexInput);
            g.fill(tx - 1, optY + 4, tx + selW + 1, optY + hexBoxH - 4, 0x880284C7);
        }
        g.text(font, customHexInput, tx, ty, 0xFFFFFFFF, false);
        if (hexFocused && !hexSelectedAll && (System.currentTimeMillis() / 400 % 2 == 0)) {
            int safeCursor = Math.max(0, Math.min(customHexInput.length(), hexCursorPos));
            int cursorX = tx + font.width(customHexInput.substring(0, safeCursor));
            g.fill(cursorX, optY + 4, cursorX + 1, optY + hexBoxH - 4, 0xFF38BDF8);
        }

        // Clickable Color preview box (between hex and apply) -> Opens Color Picker!
        int prevBoxX = hexBoxX + hexBoxW + 8;
        int parsedCol = parseHex(customHexInput, editingLineColor ? cfg.swapUnifiedLineColorValue : InventorySlotSwapManager.getSlotColor(selectedSlot));
        boolean prevHover = mouseX >= prevBoxX && mouseX <= prevBoxX + 22 && mouseY >= optY && mouseY <= optY + 20;
        g.fill(prevBoxX, optY, prevBoxX + 22, optY + 20, parsedCol);
        g.outline(prevBoxX, optY, 22, 20, prevHover ? 0xFF38BDF8 : 0xFFFFFFFF);

        // Apply button
        int applyBtnX = prevBoxX + 28;
        boolean applyHover = mouseX >= applyBtnX && mouseX <= applyBtnX + 54 && mouseY >= optY && mouseY <= optY + 20;
        ConfigUITheme.drawPillButton(g, font, "Apply", applyBtnX, optY, 54, 20, applyHover,
                0xFFFFFFFF, applyHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        // Color Picker button icon next to Apply
        int pickerBtnX = applyBtnX + 58;
        boolean pickerHover = mouseX >= pickerBtnX && mouseX <= pickerBtnX + 44 && mouseY >= optY && mouseY <= optY + 20;
        ConfigUITheme.drawPillButton(g, font, "🎨 Pick", pickerBtnX, optY, 44, 20, pickerHover,
                0xFFF59E0B, pickerHover ? 0x44D97706 : 0x22D97706, 0xFFD97706);

        // Right side: Unified Line Color Option
        int lineOptX = winX + 480;
        boolean lineHover = mouseX >= lineOptX && mouseX <= lineOptX + 160 && mouseY >= optY && mouseY <= optY + 20;
        g.fill(lineOptX, optY + 3, lineOptX + 14, optY + 17, cfg.swapUnifiedLineColor ? 0xFF0284C7 : 0xFF1E293B);
        g.outline(lineOptX, optY + 3, 14, 14, cfg.swapUnifiedLineColor ? 0xFF38BDF8 : (lineHover ? 0x8864748B : 0x4464748B));
        if (cfg.swapUnifiedLineColor) g.text(font, "✓", lineOptX + 3, optY + 6, 0xFFFFFFFF, false);
        g.text(font, "Always same color for line", lineOptX + 20, optY + 6, cfg.swapUnifiedLineColor ? 0xFF38BDF8 : 0xFFCBD5E1, false);

        // Line Color swatch button
        int lineSwatchX = lineOptX + 175;
        boolean lineSwatchHover = mouseX >= lineSwatchX && mouseX <= lineSwatchX + 70 && mouseY >= optY && mouseY <= optY + 20;
        g.fill(lineSwatchX, optY + 1, lineSwatchX + 18, optY + 19, cfg.swapUnifiedLineColorValue);
        g.outline(lineSwatchX, optY + 1, 18, 18, editingLineColor ? 0xFF38BDF8 : (lineSwatchHover ? 0xFFFFFFFF : 0x8864748B));
        g.text(font, editingLineColor ? "§aEditing" : "§7Color", lineSwatchX + 24, optY + 6, editingLineColor ? 0xFF22C55E : 0xFF94A3B8, false);

        // Sub-row: Line Width & Show Lines controls
        int lineCtrlY = optY + 26;
        // Show Connecting Lines Checkbox
        int chkX = winX + 24;
        boolean chkHover = mouseX >= chkX && mouseX <= chkX + 170 && mouseY >= lineCtrlY && mouseY <= lineCtrlY + 18;
        g.fill(chkX, lineCtrlY + 2, chkX + 14, lineCtrlY + 16, cfg.swapShowLines ? 0xFF0284C7 : 0xFF1E293B);
        g.outline(chkX, lineCtrlY + 2, 14, 14, cfg.swapShowLines ? 0xFF38BDF8 : (chkHover ? 0x8864748B : 0x4464748B));
        if (cfg.swapShowLines) g.text(font, "✓", chkX + 3, lineCtrlY + 5, 0xFFFFFFFF, false);
        g.text(font, "Show Connecting Lines", chkX + 20, lineCtrlY + 5, cfg.swapShowLines ? 0xFFFFFFFF : 0xFF94A3B8, false);

        // Line Width Stepper
        int lwX = chkX + 200;
        g.text(font, "Line Width:", lwX, lineCtrlY + 5, 0xFFCBD5E1, false);

        int minusBtnX = lwX + 70;
        boolean minusHover = mouseX >= minusBtnX && mouseX <= minusBtnX + 20 && mouseY >= lineCtrlY && mouseY <= lineCtrlY + 18;
        ConfigUITheme.drawPillButton(g, font, "-", minusBtnX, lineCtrlY, 20, 18, minusHover,
                0xFFCBD5E1, minusHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        int valBoxX = minusBtnX + 24;
        g.fill(valBoxX, lineCtrlY, valBoxX + 44, lineCtrlY + 18, 0xAA1E293B);
        g.outline(valBoxX, lineCtrlY, 44, 18, 0x4464748B);
        g.centeredText(font, String.format("%.1f px", cfg.swapLineWidth), valBoxX + 22, lineCtrlY + 5, 0xFF38BDF8);

        int plusBtnX = valBoxX + 48;
        boolean plusHover = mouseX >= plusBtnX && mouseX <= plusBtnX + 20 && mouseY >= lineCtrlY && mouseY <= lineCtrlY + 18;
        ConfigUITheme.drawPillButton(g, font, "+", plusBtnX, lineCtrlY, 20, 18, plusHover,
                0xFFCBD5E1, plusHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        // Palette picker section
        int palY = lineCtrlY + 28;
        g.fill(winX + 24, palY, winX + winW - 24, palY + 1, divCol);
        palY += 10;

        String palTitle = editingLineColor ? "§eUnified Line Color Palette Presets:" : ("§bSlot " + (selectedSlot + 1) + " Palette Presets:");
        g.text(font, palTitle, winX + 24, palY, 0xFFFFFFFF, false);
        palY += 16;

        int palCols = 6;
        int pBtnW = (winW - 60) / palCols;
        int pBtnH = 28;

        int activeTargetColor = editingLineColor ? cfg.swapUnifiedLineColorValue : InventorySlotSwapManager.getSlotColor(selectedSlot);

        for (int pi = 0; pi < PALETTE.length; pi++) {
            int row = pi / palCols;
            int col = pi % palCols;
            int px = winX + 24 + col * pBtnW;
            int py = palY + row * (pBtnH + 6);
            int pw = pBtnW - 10;

            int colVal = PALETTE[pi];
            boolean isCur = (activeTargetColor == colVal);
            boolean pHover = mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + pBtnH;

            g.fill(px, py, px + pw, py + pBtnH, 0xEE1E293B);
            g.outline(px, py, pw, pBtnH, isCur ? 0xFFFFFFFF : (pHover ? 0xFF38BDF8 : 0x4464748B));

            // Circle swatch inside button
            g.fill(px + 8, py + 5, px + 24, py + 21, colVal);
            g.outline(px + 8, py + 5, 16, 16, 0xFFFFFFFF);

            g.text(font, PALETTE_NAMES[pi] + (isCur ? " §a✓" : ""), px + 30, py + 9, isCur ? 0xFFFFFFFF : 0xFFCBD5E1, false);
        }

        // Tip text at bottom
        int tipY = winY + winH - 22;
        g.text(font, "§8Tip: Click preview box or 🎨 Pick to open full color picker. Hotkeys: Enter to apply, Esc to exit.", winX + 24, tipY, 0xFF94A3B8, false);

        // Render Color Picker modal if open
        if (colorPickerOpen) {
            renderColorPickerModal(g, font, mouseX, mouseY);
        }
    }

    private void renderColorPickerModal(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        // Modal backdrop overlay
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int modalW = 280;
        int modalH = 260;
        int mx = (this.width - modalW) / 2;
        int my = (this.height - modalH) / 2;

        // Modal box
        g.fill(mx, my, mx + modalW, my + modalH, 0xFF111827);
        g.outline(mx, my, modalW, modalH, 0xFF38BDF8);

        // Title
        String title = colorPickerForLine ? "🎨 Pick Unified Line Color" : ("🎨 Pick Slot " + (selectedSlot + 1) + " Color");
        g.text(font, title, mx + 12, my + 10, 0xFF38BDF8, false);

        // SV Matrix (Saturation x Value)
        int svX = mx + 14;
        int svY = my + 30;
        int svW = 180;
        int svH = 120;
        g.outline(svX - 1, svY - 1, svW + 2, svH + 2, 0xFF475569);

        // Render SV grid approximation (18x12 blocks)
        int stepsX = 18;
        int stepsY = 12;
        int blockW = svW / stepsX;
        int blockH = svH / stepsY;
        for (int ix = 0; ix < stepsX; ix++) {
            float s = (float) ix / (stepsX - 1);
            for (int iy = 0; iy < stepsY; iy++) {
                float v = 1.0f - ((float) iy / (stepsY - 1));
                int rgb = hsvToRgb(pickerHue, s, v);
                int bx = svX + ix * blockW;
                int by = svY + iy * blockH;
                g.fill(bx, by, bx + blockW, by + blockH, rgb);
            }
        }

        // SV selection indicator
        int selX = svX + Math.round(pickerSat * svW);
        int selY = svY + Math.round((1.0f - pickerVal) * svH);
        g.outline(selX - 4, selY - 4, 8, 8, 0xFFFFFFFF);
        g.outline(selX - 3, selY - 3, 6, 6, 0xFF000000);

        // Hue Slider (Vertical)
        int hueX = mx + 208;
        int hueY = svY;
        int hueW = 16;
        int hueH = svH;
        g.outline(hueX - 1, hueY - 1, hueW + 2, hueH + 2, 0xFF475569);

        int hueSteps = 24;
        int hBlockH = hueH / hueSteps;
        for (int ih = 0; ih < hueSteps; ih++) {
            float h = (float) ih / hueSteps * 360f;
            int rgb = hsvToRgb(h, 1.0f, 1.0f);
            int by = hueY + ih * hBlockH;
            g.fill(hueX, by, hueX + hueW, by + hBlockH, rgb);
        }

        // Hue slider handle
        int hueHandleY = hueY + Math.round((pickerHue / 360f) * hueH);
        g.fill(hueX - 2, hueHandleY - 2, hueX + hueW + 2, hueHandleY + 2, 0xFFFFFFFF);
        g.outline(hueX - 2, hueHandleY - 2, hueW + 4, 4, 0xFF000000);

        // Live preview & info area
        int prevY = svY + svH + 12;
        int curRgb = pickerColor;
        int r = (curRgb >> 16) & 0xFF;
        int gr = (curRgb >> 8) & 0xFF;
        int b = curRgb & 0xFF;

        // Swatch
        g.fill(svX, prevY, svX + 36, prevY + 28, curRgb);
        g.outline(svX, prevY, 36, 28, 0xFFFFFFFF);

        // Hex & RGB Text
        String hexStr = String.format("#%06X", (curRgb & 0xFFFFFF));
        g.text(font, "§f" + hexStr, svX + 44, prevY + 2, 0xFFFFFFFF, false);
        g.text(font, String.format("§7R:%d G:%d B:%d", r, gr, b), svX + 44, prevY + 16, 0xFFCBD5E1, false);

        // Action Buttons: Select / Cancel
        int btnY = my + modalH - 34;

        int selectBtnX = mx + 30;
        int selectBtnW = 100;
        boolean selHover = mouseX >= selectBtnX && mouseX <= selectBtnX + selectBtnW && mouseY >= btnY && mouseY <= btnY + 22;
        ConfigUITheme.drawPillButton(g, font, "✓ Select", selectBtnX, btnY, selectBtnW, 22, selHover,
                0xFFFFFFFF, selHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        int cancelBtnX = mx + 150;
        int cancelBtnW = 100;
        boolean canHover = mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= btnY && mouseY <= btnY + 22;
        ConfigUITheme.drawPillButton(g, font, "✕ Cancel", cancelBtnX, btnY, cancelBtnW, 22, canHover,
                0xFFCBD5E1, canHover ? 0x44EF4444 : 0x22EF4444, 0xFFEF4444);
    }

    private void updateColorPickerSV(int mouseX, int mouseY) {
        int modalW = 280;
        int modalH = 260;
        int mx = (this.width - modalW) / 2;
        int my = (this.height - modalH) / 2;
        int svX = mx + 14;
        int svY = my + 30;
        int svW = 180;
        int svH = 120;

        float s = (float) (mouseX - svX) / (float) svW;
        float v = 1.0f - ((float) (mouseY - svY) / (float) svH);
        this.pickerSat = Math.max(0.0f, Math.min(1.0f, s));
        this.pickerVal = Math.max(0.0f, Math.min(1.0f, v));
        this.pickerColor = hsvToRgb(pickerHue, pickerSat, pickerVal);
    }

    private void updateColorPickerHue(int mouseY) {
        int modalW = 280;
        int modalH = 260;
        int my = (this.height - modalH) / 2;
        int hueY = my + 30;
        int hueH = 120;

        float frac = (float) (mouseY - hueY) / (float) hueH;
        this.pickerHue = Math.max(0.0f, Math.min(359.9f, frac * 360.0f));
        this.pickerColor = hsvToRgb(pickerHue, pickerSat, pickerVal);
    }

    private void applyCustomColor() {
        int parsed = parseHex(customHexInput, 0);
        if (parsed != 0) {
            if (editingLineColor) {
                BomboConfig.get().swapUnifiedLineColorValue = parsed;
                BomboConfig.save();
            } else {
                InventorySlotSwapManager.setSlotColor(selectedSlot, parsed);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (colorPickerOpen) {
            int modalW = 280;
            int modalH = 260;
            int mx = (this.width - modalW) / 2;
            int my = (this.height - modalH) / 2;

            int svX = mx + 14;
            int svY = my + 30;
            int svW = 180;
            int svH = 120;

            int hueX = mx + 208;
            int hueY = svY;
            int hueW = 16;
            int hueH = svH;

            // Click SV box
            if (mouseX >= svX && mouseX <= svX + svW && mouseY >= svY && mouseY <= svY + svH) {
                draggingSV = true;
                updateColorPickerSV((int) mouseX, (int) mouseY);
                return true;
            }

            // Click Hue bar
            if (mouseX >= hueX && mouseX <= hueX + hueW && mouseY >= hueY && mouseY <= hueY + hueH) {
                draggingHue = true;
                updateColorPickerHue((int) mouseY);
                return true;
            }

            // Select button
            int btnY = my + modalH - 34;
            int selectBtnX = mx + 30;
            int selectBtnW = 100;
            if (mouseX >= selectBtnX && mouseX <= selectBtnX + selectBtnW && mouseY >= btnY && mouseY <= btnY + 22) {
                colorPickerOpen = false;
                customHexInput = String.format("#%06X", (pickerColor & 0xFFFFFF));
                hexCursorPos = customHexInput.length();
                if (colorPickerForLine) {
                    BomboConfig.get().swapUnifiedLineColorValue = pickerColor;
                    BomboConfig.save();
                } else {
                    InventorySlotSwapManager.setSlotColor(selectedSlot, pickerColor);
                }
                return true;
            }

            // Cancel button
            int cancelBtnX = mx + 150;
            int cancelBtnW = 100;
            if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW && mouseY >= btnY && mouseY <= btnY + 22) {
                colorPickerOpen = false;
                return true;
            }

            // Outside modal click closes
            if (mouseX < mx || mouseX > mx + modalW || mouseY < my || mouseY > my + modalH) {
                colorPickerOpen = false;
                return true;
            }

            return true;
        }

        // Reset to Defaults Button
        int rstBtnX = winX + winW - 200;
        int rstBtnY = winY + 9;
        if (mouseX >= rstBtnX && mouseX <= rstBtnX + 120 && mouseY >= rstBtnY && mouseY <= rstBtnY + 22) {
            if (InventorySlotSwapManager.customHotbarColors != null) {
                for (int i = 0; i < 9; i++) {
                    InventorySlotSwapManager.customHotbarColors[i] = 0;
                }
                InventorySlotSwapManager.saveColors();
            }
            BomboConfig.get().swapUnifiedLineColor = false;
            BomboConfig.get().swapUnifiedLineColorValue = 0xFF22C55E;
            BomboConfig.get().swapShowLines = true;
            BomboConfig.get().swapLineWidth = 2.0f;
            BomboConfig.save();
            customHexInput = String.format("#%06X", (InventorySlotSwapManager.getSlotColor(selectedSlot) & 0xFFFFFF));
            hexCursorPos = customHexInput.length();
            return true;
        }

        // Done / Close Button
        int closeBtnX = winX + winW - 60;
        int closeBtnY = winY + 9;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 50 && mouseY >= closeBtnY && mouseY <= closeBtnY + 22) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        // Grid of 9 hotbar slots
        int startY = winY + headerH + 30;
        int slotColW = (winW - 48) / 9;

        for (int i = 0; i < 9; i++) {
            int sx = winX + 24 + i * slotColW;
            int sy = startY;
            int sw = slotColW - 6;
            int sh = 74;

            if (mouseX >= sx && mouseX <= sx + sw && mouseY >= sy && mouseY <= sy + sh) {
                selectedSlot = i;
                editingLineColor = false;
                customHexInput = String.format("#%06X", (InventorySlotSwapManager.getSlotColor(i) & 0xFFFFFF));
                hexCursorPos = customHexInput.length();
                hexFocused = false;
                if (mouseY >= sy + 22 && mouseY <= sy + 44) {
                    InventorySlotSwapManager.cycleSlotColor(i);
                    customHexInput = String.format("#%06X", (InventorySlotSwapManager.getSlotColor(i) & 0xFFFFFF));
                    hexCursorPos = customHexInput.length();
                }
                return true;
            }
        }

        int optY = startY + 84 + 10;

        // Custom hex text box click
        int hexBoxX = winX + 220;
        int hexBoxW = 96;
        int hexBoxH = 20;
        if (mouseX >= hexBoxX && mouseX <= hexBoxX + hexBoxW && mouseY >= optY && mouseY <= optY + hexBoxH) {
            hexFocused = true;
            // Calculate cursor pos based on click X
            int relX = (int) mouseX - (hexBoxX + 6);
            int bestPos = 0;
            int minDiff = Integer.MAX_VALUE;
            for (int ci = 0; ci <= customHexInput.length(); ci++) {
                int w = font.width(customHexInput.substring(0, ci));
                int diff = Math.abs(w - relX);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestPos = ci;
                }
            }
            hexCursorPos = bestPos;
            return true;
        } else {
            hexFocused = false;
        }

        // Preview box click -> Opens Color Picker!
        int prevBoxX = hexBoxX + hexBoxW + 8;
        if (mouseX >= prevBoxX && mouseX <= prevBoxX + 22 && mouseY >= optY && mouseY <= optY + 20) {
            openColorPicker(editingLineColor);
            return true;
        }

        // Apply button click
        int applyBtnX = prevBoxX + 28;
        if (mouseX >= applyBtnX && mouseX <= applyBtnX + 54 && mouseY >= optY && mouseY <= optY + 20) {
            applyCustomColor();
            return true;
        }

        // Color Picker button icon click
        int pickerBtnX = applyBtnX + 58;
        if (mouseX >= pickerBtnX && mouseX <= pickerBtnX + 44 && mouseY >= optY && mouseY <= optY + 20) {
            openColorPicker(editingLineColor);
            return true;
        }

        // Unified Line Color Checkbox
        int lineOptX = winX + 480;
        if (mouseX >= lineOptX && mouseX <= lineOptX + 160 && mouseY >= optY && mouseY <= optY + 20) {
            BomboConfig.get().swapUnifiedLineColor = !BomboConfig.get().swapUnifiedLineColor;
            BomboConfig.save();
            return true;
        }

        // Unified Line Color Swatch click (switch to editing line color, or shift-click for picker)
        int lineSwatchX = lineOptX + 175;
        if (mouseX >= lineSwatchX && mouseX <= lineSwatchX + 70 && mouseY >= optY && mouseY <= optY + 20) {
            if (mouseX <= lineSwatchX + 18) {
                // Clicked the color square itself -> open color picker for line!
                openColorPicker(true);
            } else {
                editingLineColor = !editingLineColor;
                if (editingLineColor) {
                    customHexInput = String.format("#%06X", (BomboConfig.get().swapUnifiedLineColorValue & 0xFFFFFF));
                } else {
                    customHexInput = String.format("#%06X", (InventorySlotSwapManager.getSlotColor(selectedSlot) & 0xFFFFFF));
                }
                hexCursorPos = customHexInput.length();
            }
            return true;
        }

        // Line Controls row (Show lines checkbox & Line width stepper)
        int lineCtrlY = optY + 26;
        int chkX = winX + 24;
        if (mouseX >= chkX && mouseX <= chkX + 170 && mouseY >= lineCtrlY && mouseY <= lineCtrlY + 18) {
            BomboConfig.get().swapShowLines = !BomboConfig.get().swapShowLines;
            BomboConfig.save();
            return true;
        }

        int lwX = chkX + 200;
        int minusBtnX = lwX + 70;
        if (mouseX >= minusBtnX && mouseX <= minusBtnX + 20 && mouseY >= lineCtrlY && mouseY <= lineCtrlY + 18) {
            BomboConfig.get().swapLineWidth = Math.max(1.0f, BomboConfig.get().swapLineWidth - 0.5f);
            BomboConfig.save();
            return true;
        }

        int plusBtnX = minusBtnX + 24 + 48;
        if (mouseX >= plusBtnX && mouseX <= plusBtnX + 20 && mouseY >= lineCtrlY && mouseY <= lineCtrlY + 18) {
            BomboConfig.get().swapLineWidth = Math.min(6.0f, BomboConfig.get().swapLineWidth + 0.5f);
            BomboConfig.save();
            return true;
        }

        // Palette buttons
        int palY = lineCtrlY + 28 + 10 + 16;
        int palCols = 6;
        int pBtnW = (winW - 60) / palCols;
        int pBtnH = 28;

        for (int pi = 0; pi < PALETTE.length; pi++) {
            int row = pi / palCols;
            int col = pi % palCols;
            int px = winX + 24 + col * pBtnW;
            int py = palY + row * (pBtnH + 6);
            int pw = pBtnW - 10;

            if (mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + pBtnH) {
                int colVal = PALETTE[pi];
                if (editingLineColor) {
                    BomboConfig.get().swapUnifiedLineColorValue = colVal;
                    BomboConfig.save();
                    customHexInput = String.format("#%06X", (colVal & 0xFFFFFF));
                    hexCursorPos = customHexInput.length();
                } else {
                    InventorySlotSwapManager.setSlotColor(selectedSlot, colVal);
                    customHexInput = String.format("#%06X", (colVal & 0xFFFFFF));
                    hexCursorPos = customHexInput.length();
                }
                return true;
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingSV = false;
        draggingHue = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (colorPickerOpen) {
            if (draggingSV) {
                updateColorPickerSV((int) event.x(), (int) event.y());
                return true;
            }
            if (draggingHue) {
                updateColorPickerHue((int) event.y());
                return true;
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (colorPickerOpen) return true;
        char c = (char) event.codepoint();
        if (hexFocused) {
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '#') {
                pushHexUndo();
                if (hexSelectedAll) {
                    customHexInput = (c == '#') ? "#" : ("#" + c);
                    hexCursorPos = customHexInput.length();
                    hexSelectedAll = false;
                    return true;
                }
                if (customHexInput.length() < 9) {
                    hexCursorPos = Math.max(0, Math.min(customHexInput.length(), hexCursorPos));
                    customHexInput = customHexInput.substring(0, hexCursorPos) + c + customHexInput.substring(hexCursorPos);
                    hexCursorPos++;
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (colorPickerOpen) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                colorPickerOpen = false;
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                colorPickerOpen = false;
                pushHexUndo();
                customHexInput = String.format("#%06X", (pickerColor & 0xFFFFFF));
                hexCursorPos = customHexInput.length();
                hexSelectedAll = false;
                if (colorPickerForLine) {
                    BomboConfig.get().swapUnifiedLineColorValue = pickerColor;
                    BomboConfig.save();
                } else {
                    InventorySlotSwapManager.setSlotColor(selectedSlot, pickerColor);
                }
                return true;
            }
            return true;
        }

        if (hexFocused) {
            boolean ctrlHeld = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean shiftHeld = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            hexCursorPos = Math.max(0, Math.min(customHexInput.length(), hexCursorPos));

            // Select All
            if (ctrlHeld && event.key() == GLFW.GLFW_KEY_A) {
                hexSelectedAll = true;
                return true;
            }

            // Copy
            if (ctrlHeld && event.key() == GLFW.GLFW_KEY_C) {
                if (!customHexInput.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(customHexInput);
                }
                return true;
            }

            // Undo / Redo
            if (ctrlHeld && event.key() == GLFW.GLFW_KEY_Z) {
                if (shiftHeld) {
                    if (!hexRedoStack.isEmpty()) {
                        hexUndoStack.push(customHexInput);
                        customHexInput = hexRedoStack.pop();
                        hexCursorPos = customHexInput.length();
                        hexSelectedAll = false;
                    }
                } else {
                    if (!hexUndoStack.isEmpty()) {
                        hexRedoStack.push(customHexInput);
                        customHexInput = hexUndoStack.pop();
                        hexCursorPos = customHexInput.length();
                        hexSelectedAll = false;
                    }
                }
                return true;
            }

            // Redo (Ctrl + Y)
            if (ctrlHeld && event.key() == GLFW.GLFW_KEY_Y) {
                if (!hexRedoStack.isEmpty()) {
                    hexUndoStack.push(customHexInput);
                    customHexInput = hexRedoStack.pop();
                    hexCursorPos = customHexInput.length();
                    hexSelectedAll = false;
                }
                return true;
            }

            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (hexSelectedAll) {
                    pushHexUndo();
                    customHexInput = "#";
                    hexCursorPos = 1;
                    hexSelectedAll = false;
                    return true;
                }
                pushHexUndo();
                if (ctrlHeld) {
                    if (hexCursorPos > 1) {
                        customHexInput = "#" + customHexInput.substring(hexCursorPos);
                        hexCursorPos = 1;
                    } else if (hexCursorPos > 0) {
                        customHexInput = customHexInput.substring(hexCursorPos);
                        hexCursorPos = 0;
                    }
                } else if (hexCursorPos > 0) {
                    customHexInput = customHexInput.substring(0, hexCursorPos - 1) + customHexInput.substring(hexCursorPos);
                    hexCursorPos--;
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_DELETE) {
                if (hexSelectedAll) {
                    pushHexUndo();
                    customHexInput = "#";
                    hexCursorPos = 1;
                    hexSelectedAll = false;
                    return true;
                }
                pushHexUndo();
                if (ctrlHeld) {
                    if (hexCursorPos < customHexInput.length()) {
                        customHexInput = customHexInput.substring(0, hexCursorPos);
                    }
                } else if (hexCursorPos < customHexInput.length()) {
                    customHexInput = customHexInput.substring(0, hexCursorPos) + customHexInput.substring(hexCursorPos + 1);
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_LEFT) {
                hexSelectedAll = false;
                if (ctrlHeld) {
                    hexCursorPos = 0;
                } else if (hexCursorPos > 0) {
                    hexCursorPos--;
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_RIGHT) {
                hexSelectedAll = false;
                if (ctrlHeld) {
                    hexCursorPos = customHexInput.length();
                } else if (hexCursorPos < customHexInput.length()) {
                    hexCursorPos++;
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_HOME) {
                hexSelectedAll = false;
                hexCursorPos = 0;
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_END) {
                hexSelectedAll = false;
                hexCursorPos = customHexInput.length();
                return true;
            } else if (ctrlHeld && event.key() == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (char c : clip.toCharArray()) {
                        if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F') || c == '#') {
                            sb.append(c);
                        }
                    }
                    String pasted = sb.toString();
                    if (!pasted.isEmpty()) {
                        pushHexUndo();
                        if (hexSelectedAll) {
                            String res = pasted.startsWith("#") ? pasted : ("#" + pasted);
                            if (res.length() > 9) res = res.substring(0, 9);
                            customHexInput = res;
                            hexCursorPos = customHexInput.length();
                            hexSelectedAll = false;
                            return true;
                        }
                        int rem = 9 - customHexInput.length();
                        if (rem > 0) {
                            if (pasted.length() > rem) pasted = pasted.substring(0, rem);
                            customHexInput = customHexInput.substring(0, hexCursorPos) + pasted + customHexInput.substring(hexCursorPos);
                            hexCursorPos += pasted.length();
                        }
                    }
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                applyCustomColor();
                hexFocused = false;
                hexSelectedAll = false;
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                hexFocused = false;
                hexSelectedAll = false;
                return true;
            }
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }
}
