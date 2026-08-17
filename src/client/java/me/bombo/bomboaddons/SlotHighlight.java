package me.bombo.bomboaddons;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SlotHighlight {
   public static final List<String> COLORS = List.of("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE", "PINK");
   private static Set<Integer> targetSlots = new HashSet();
   private static Set<String> targetNames = new HashSet();
   public static long highlightStartTime = 0L;
   private static int currentColor = -2147418368;
   private static final long HIGHLIGHT_DURATION_MS = 10000L;

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
      if (highlightStartTime != 0L && System.currentTimeMillis() - highlightStartTime > 10000L) {
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
      if (displayName == null) {
         return 0;
      } else {
         String cleanName = displayName.replaceAll("§.", "").toLowerCase();

         for(String name : targetNames) {
            if (cleanName.contains(name)) {
               return currentColor;
            }
         }

         if (BomboConfig.get().itemHighlightsEnabled) {
            for(Map.Entry<String, BomboConfig.HighlightInfo> entry : BomboConfig.get().itemHighlights.entrySet()) {
               if (((BomboConfig.HighlightInfo)entry.getValue()).enabled && cleanName.contains(((String)entry.getKey()).toLowerCase())) {
                  return getFormattingColor(((BomboConfig.HighlightInfo)entry.getValue()).color);
               }
            }
         }

         return 0;
      }
   }

   public static int getFormattingColor(String colorName) {
      if (colorName == null) {
         return -2147418368;
      } else {
         int base;
         switch (colorName.toUpperCase()) {
            case "BLACK" -> base = 0;
            case "DARK_BLUE" -> base = 170;
            case "DARK_GREEN" -> base = 43520;
            case "DARK_AQUA" -> base = 43690;
            case "DARK_RED" -> base = 11141120;
            case "DARK_PURPLE" -> base = 11141290;
            case "GOLD" -> base = 16755200;
            case "GRAY" -> base = 11184810;
            case "DARK_GRAY" -> base = 5592405;
            case "BLUE" -> base = 5592575;
            case "GREEN" -> base = 65280;
            case "AQUA" -> base = 5636095;
            case "RED" -> base = 16711680;
            case "LIGHT_PURPLE" -> base = 16733695;
            case "YELLOW" -> base = 16776960;
            case "WHITE" -> base = 16777215;
            case "PINK" -> base = 16733695;
            default -> base = 65280;
         }

         return Integer.MIN_VALUE | base & 16777215;
      }
   }

   private static int ensureAlpha(int color) {
      return (color & -16777216) == 0 ? Integer.MIN_VALUE | color & 16777215 : color;
   }

   public static void clearTargetSlot() {
      targetSlots.clear();
      targetNames.clear();
      highlightStartTime = 0L;
   }
}
