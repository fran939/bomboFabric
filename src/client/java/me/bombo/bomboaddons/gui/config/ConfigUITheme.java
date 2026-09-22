package me.bombo.bomboaddons.gui.config;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ConfigUITheme {
    // Dynamic Accent Colors
    public static int getAccentColor() {
        String name = BomboConfig.get().guiAccentColor;
        if (name == null) return 0xFF00E5FF;
        return getColorForName(name, 0xFF00E5FF);
    }

    public static int getSecondaryColor() {
        String name = BomboConfig.get().guiSecondaryColor;
        if (name == null) return 0xFFFFFFFF;
        return getColorForName(name, 0xFFFFFFFF);
    }

    public static int getToggleColor() {
        String name = BomboConfig.get().guiToggleColor;
        if (name == null) return 0xFF10B981;
        return getColorForName(name, 0xFF10B981);
    }

    public static int getColorForName(String name, int defaultHex) {
        if (name == null) return defaultHex;
        return switch (name.toLowerCase()) {
            case "cyan" -> 0xFF00E5FF;
            case "gold", "yellow" -> 0xFFFFAA00;
            case "emerald", "green" -> 0xFF10B981;
            case "purple", "violet" -> 0xFFC084FC;
            case "red" -> 0xFFEF4444;
            case "blue" -> 0xFF3B82F6;
            case "pink" -> 0xFFEC4899;
            case "orange" -> 0xFFF97316;
            case "white" -> 0xFFFFFFFF;
            case "black" -> 0xFF1E293B;
            default -> defaultHex;
        };
    }

    // Dynamic Theme Surfaces based on Theme Mode
    public static int getMainWindowBg() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFFFFFFFF;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0xFFF8FAFC;
        if ("Transparent".equalsIgnoreCase(mode)) return 0x00000000; // Full transparent background
        return 0xF20F1218; // Dark Mode
    }

    public static int getSidebarBg() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFFFFFFFF;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0xFFEDF2F7;
        if ("Transparent".equalsIgnoreCase(mode)) return 0x00000000;
        return 0xFA141720;
    }

    public static int getHeaderBg() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFFFFFFFF;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0xFFE2E8F0;
        if ("Transparent".equalsIgnoreCase(mode)) return 0x22000000;
        return 0xFA171B26;
    }

    public static int getCardBg(boolean hovered) {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) {
            return hovered ? 0xFFFFFFEE : 0xFFFFFFFF;
        }
        if ("Light Mode".equalsIgnoreCase(mode)) {
            return hovered ? 0xFFE2E8F0 : 0xFFFFFFFF;
        }
        if ("Transparent".equalsIgnoreCase(mode)) {
            return hovered ? 0x44252D3F : 0x221C2230;
        }
        return hovered ? 0xDD252D3F : 0x991C2230;
    }

    public static int getBorderColor() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFFFFD700;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0xFFCBD5E1;
        int sec = getSecondaryColor();
        if (sec != 0xFFFFFFFF) {
            return 0x55000000 | (sec & 0x00FFFFFF);
        }
        if ("Transparent".equalsIgnoreCase(mode)) return 0x44FFFFFF;
        return 0x22FFFFFF;
    }

    public static int getDividerColor() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFFFFD700;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0x22000000;
        int sec = getSecondaryColor();
        if (sec != 0xFFFFFFFF) {
            return 0x33000000 | (sec & 0x00FFFFFF);
        }
        return 0x1AFFFFFF;
    }

    public static int getTextTitle() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFF000000;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0xFF0F172A;
        return 0xFFFFFFFF;
    }

    public static int getTextMuted() {
        String mode = BomboConfig.get().guiThemeMode;
        if ("Zamasu".equalsIgnoreCase(mode)) return 0xFF555500;
        if ("Light Mode".equalsIgnoreCase(mode)) return 0xFF475569;
        int sec = getSecondaryColor();
        if (sec != 0xFFFFFFFF) {
            return 0xDD000000 | (sec & 0x00FFFFFF);
        }
        return 0xFF94A3B8;
    }

    public static String getFontPrefix() {
        String font = BomboConfig.get().guiFont;
        if (font == null) return "";
        return switch (font.toLowerCase()) {
            case "alt", "runes", "illusion" -> "§k";
            case "bold" -> "§l";
            case "italic", "smooth" -> "§o";
            case "underline" -> "§n";
            default -> "";
        };
    }

    public static String formatFont(String text) {
        if (text == null || text.isEmpty()) return "";
        String prefix = getFontPrefix();
        if (prefix.isEmpty()) return text;
        if (text.startsWith("§")) {
            return text + prefix;
        }
        return prefix + text.replace("§r", "§r" + prefix);
    }

    public static final int ACCENT_GOLD = 0xFFFFAA00;
    public static final int ACCENT_CYAN = 0xFF00E5FF;
    public static final int TEXT_MUTED = 0xFF94A3B8;
    public static final int TEXT_TITLE = 0xFFFFFFFF;
    public static final int BG_MAIN_WINDOW = 0xF20F1218;
    public static final int BG_SIDEBAR = 0xFA141720;
    public static final int BG_HEADER = 0xFA171B26;
    public static final int BORDER_WINDOW = 0x445A6D8C;
    public static final int DIVIDER_COLOR = 0x1AFFFFFF;

    /**
     * Draws a card container with subtle background and 1px border.
     */
    public static void drawCard(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
        int bg = getCardBg(hovered);
        int accent = getAccentColor();
        boolean glow = BomboConfig.get().guiCardGlow;
        int border = (hovered && glow) ? (0x55000000 | (accent & 0x00FFFFFF)) : getBorderColor();

        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
    }

    /**
     * Draws an interactive toggle switch.
     */
    public static void drawToggleSwitch(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered) {
        String mode = BomboConfig.get().guiThemeMode;
        boolean isLight = "Light Mode".equalsIgnoreCase(mode) || "Zamasu".equalsIgnoreCase(mode);
        int toggleColor = getToggleColor();
        int bg;
        int border;
        if (active) {
            bg = hovered ? (0xEE000000 | (toggleColor & 0x00FFFFFF)) : toggleColor;
            border = isLight ? 0xFF0F172A : 0xFFFFFFFF;
        } else {
            bg = isLight ? (hovered ? 0xFFCBD5E1 : 0xFFE2E8F0) : (hovered ? 0xFF475569 : 0xFF334155);
            border = isLight ? 0xFF64748B : 0x44FFFFFF;
        }

        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);

        // Knob
        int knobSize = h - 4;
        int knobX = active ? (x + w - knobSize - 2) : (x + 2);
        int knobY = y + 2;
        int knobColor = (isLight && !active) ? 0xFF475569 : 0xFFFFFFFF;
        g.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobColor);
        if (isLight && !active) {
            g.outline(knobX, knobY, knobSize, knobSize, 0xFF334155);
        }
    }

    /**
     * Draws a pill button with hover effects.
     */
    public static void drawPillButton(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int h, boolean hovered,
                                      int textColor, int bgColor, int borderColor) {
        g.fill(x, y, x + w, y + h, bgColor);
        g.outline(x, y, w, h, borderColor);

        int textW = font.width(text);
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        g.text(font, text, textX, textY, textColor != -1 ? textColor : (hovered ? 0xFFFFFFFF : 0xFFE2E8F0), false);
    }

    /**
     * Draws a scroll bar track and thumb.
     */
    public static void drawScrollBar(GuiGraphicsExtractor g, int x, int y, int w, int h, int thumbY, int thumbH) {
        g.fill(x, y, x + w, y + h, 0x1AFFFFFF);
        g.fill(x, thumbY, x + w, thumbY + thumbH, 0x66FFFFFF);
    }
}
