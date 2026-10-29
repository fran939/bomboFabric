package me.bombo.bomboaddons;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class SlotHighlight {
   public static final List<String> COLORS = List.of("BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE", "GOLD", "BROWN", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE", "PINK");
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
      if (me.bombo.bomboaddons.features.StorageChestWaypoints.hasMatches() || !me.bombo.bomboaddons.BlockHighlight.targetChestPosList.isEmpty()) {
         return;
      }
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

   public static int getHighlightColor(net.minecraft.world.item.ItemStack stack, String displayName) {
      if (stack != null && !stack.isEmpty()) {
         String query = me.bombo.bomboaddons.features.StorageChestWaypoints.getActiveQuery();
         if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().replaceAll("§.", "").toLowerCase(java.util.Locale.ROOT);
            boolean isLore = false;
            if (q.startsWith("l:") || q.startsWith("lore:")) {
               isLore = true;
               q = q.substring(q.indexOf(':') + 1).trim();
            }
            String norm = q.replaceAll("[^a-z0-9]", "");
            String sbId = SkyblockUtils.getStrictSkyblockId(stack);
            if (sbId != null && !sbId.isEmpty()) {
               if (norm.equals("museum") || norm.equals("donatable")) {
                  if (me.bombo.bomboaddons.features.museum.MuseumManager.isDonatable(sbId, stack)) {
                     return -1442775296; // 0xAA00E5FF cyan
                  }
               } else if (norm.equals("donated")) {
                  if (me.bombo.bomboaddons.features.museum.MuseumManager.isDonated(sbId, stack)) {
                     return -1442775296;
                  }
               } else if (norm.equals("sack") || norm.equals("sacks")) {
                  if (me.bombo.bomboaddons.features.sacks.SacksManager.fitsInSack(sbId)) {
                     return -1442775296;
                  }
               } else if (isLore) {
                  if (norm.equals("museum") && me.bombo.bomboaddons.features.museum.MuseumManager.isMuseumItem(sbId)) {
                     return -1442775296;
                  }
               }
            }
         }
      }
      return getHighlightColor(displayName);
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

         String query = me.bombo.bomboaddons.features.StorageChestWaypoints.getActiveQuery();
         if (query != null && !query.trim().isEmpty()) {
            String q = query.trim().replaceAll("§.", "").toLowerCase();
            if (q.startsWith("l:") || q.startsWith("lore:")) {
               q = q.substring(q.indexOf(':') + 1).trim();
            }
            if (!q.isEmpty() && cleanName.contains(q)) {
               return -1442775296; // 0xAA00E5FF cyan
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
