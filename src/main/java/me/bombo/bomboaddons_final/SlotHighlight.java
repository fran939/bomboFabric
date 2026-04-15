package me.bombo.bomboaddons_final;

import me.bombo.bomboaddons_final.BomboConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class SlotHighlight {
    private static Set<Integer> targetSlots = new HashSet<>();
    private static Set<String> targetNames = new HashSet<>();
    private static long highlightStartTime = 0;
    private static int currentColor = 0x8000FF00;
    private static final long HIGHLIGHT_DURATION_MS = 10000; // 10 seconds

    public static void setTargetSlot(int slotIndex, int color) {
        clearTargetSlot();
        targetSlots.add(slotIndex);
        currentColor = ensureAlpha(color);
        highlightStartTime = System.currentTimeMillis();
    }

    public static void addTargetSlot(int slotIndex, int color) {
        targetSlots.add(slotIndex);
        currentColor = ensureAlpha(color);
        highlightStartTime = System.currentTimeMillis();
    }

    public static void addTargetName(String name, int color) {
        targetNames.add(name.toLowerCase());
        currentColor = ensureAlpha(color);
        highlightStartTime = System.currentTimeMillis();
    }

    private static void checkExpiration() {
        if (highlightStartTime != 0 && System.currentTimeMillis() - highlightStartTime > HIGHLIGHT_DURATION_MS) {
            clearTargetSlot();
        }
    }

    public static Set<Integer> getTargetSlots() {
        checkExpiration();
        return targetSlots;
    }

    public static boolean isTargetSlot(int slotIndex) {
        return getTargetSlots().contains(slotIndex);
    }

    public static int getCurrentColor() {
        return currentColor;
    }

    public static int getHighlightColor(String displayName) {
        checkExpiration();
        if (displayName == null) return 0;
        
        String cleanName = displayName.replaceAll("§.", "").toLowerCase();

        // 1. Session targets (e.g. from search navigation)
        for (String name : targetNames) {
            if (cleanName.contains(name)) {
                return currentColor;
            }
        }

        // 2. Persistent highlights from configuration
        for (Map.Entry<String, BomboConfig.HighlightInfo> entry : BomboConfig.get().highlights.entrySet()) {
            if (cleanName.contains(entry.getKey().toLowerCase())) {
                return getFormattingColor(entry.getValue().color);
            }
        }
        
        return 0;
    }

    public static int getFormattingColor(String colorName) {
        if (colorName == null) return 0x8000FF00;
        
        int base;
        switch (colorName.toUpperCase()) {
            case "BLACK": base = 0x000000; break;
            case "DARK_BLUE": base = 0x0000AA; break;
            case "DARK_GREEN": base = 0x00AA00; break;
            case "DARK_AQUA": base = 0x00AAAA; break;
            case "DARK_RED": base = 0xAA0000; break;
            case "DARK_PURPLE": base = 0xAA00AA; break;
            case "GOLD": base = 0xFFAA00; break;
            case "GRAY": base = 0xAAAAAA; break;
            case "DARK_GRAY": base = 0x555555; break;
            case "BLUE": base = 0x5555FF; break;
            case "GREEN": base = 0x00FF00; break;
            case "AQUA": base = 0x55FFFF; break;
            case "RED": base = 0xFF0000; break;
            case "LIGHT_PURPLE": base = 0xFF55FF; break;
            case "YELLOW": base = 0xFFFF00; break;
            case "WHITE": base = 0xFFFFFF; break;
            case "PINK": base = 0xFF55FF; break;
            default: base = 0x00FF00;
        }
        return 0x80000000 | (base & 0xFFFFFF);
    }

    private static int ensureAlpha(int color) {
        if ((color & 0xFF000000) == 0) {
            return 0x80000000 | (color & 0xFFFFFF);
        }
        return color;
    }

    public static void clearTargetSlot() {
        targetSlots.clear();
        targetNames.clear();
        highlightStartTime = 0;
    }
}
