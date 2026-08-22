package me.bombo.bomboaddons.features;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class SupercraftHelper {
   private static final Pattern MISSING_ITEMS_PATTERN = Pattern.compile("([0-9,]+)x\\s+missing\\s+items", Pattern.CASE_INSENSITIVE);
   private static final Pattern CRAFTING_AMOUNT_PATTERN = Pattern.compile("Crafting\\s+(\\d+)\\s+items", Pattern.CASE_INSENSITIVE);
   private static final Pattern STARTS_IN_PATTERN = Pattern.compile("Starts\\s+in:\\s*(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s)?", Pattern.CASE_INSENSITIVE);

   public static class LoreAddition {
      public final String key;
      public final Component line;
      public final String position; // "TOP" or "BOTTOM"
      public final int order;

      public LoreAddition(String key, Component line, String position, int order) {
         this.key = key;
         this.line = line;
         this.position = position != null ? position : "BOTTOM";
         this.order = order;
      }
   }

   public static String getCanceledOrderAmount(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return null;
      String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
      if (!name.contains("Cancel Order")) return null;

      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) return null;

      for (Component line : lore.lines()) {
         String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
         Matcher m = MISSING_ITEMS_PATTERN.matcher(clean);
         if (m.find()) {
            return m.group(1).replace(",", "").trim();
         }
      }
      return null;
   }

   public static int getCraftAmountPerCraft(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return 1;
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) return 1;

      for (Component line : lore.lines()) {
         String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
         Matcher m = CRAFTING_AMOUNT_PATTERN.matcher(clean);
         if (m.find()) {
            try {
               return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
         }
      }
      return 1;
   }

   public static int calculateMaxCrafts(AbstractContainerScreen<?> screen, ItemStack supercraftStack) {
      if (screen == null || supercraftStack == null) return 0;
      int craftAmount = getCraftAmountPerCraft(supercraftStack);
      if (craftAmount <= 0) craftAmount = 1;

      int maxStackSize = 64;
      for (Slot slot : screen.getMenu().slots) {
         if (!(slot.container instanceof Inventory) && slot.hasItem()) {
            ItemStack s = slot.getItem();
            String sName = s.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
            if (!sName.contains("Supercraft") && !sName.contains("Quick Craft") && !sName.contains("Close") && !sName.contains("Back") && !sName.contains("Recipe")) {
               if (s.is(Items.ENCHANTED_BOOK) || s.getItem().toString().contains("enchanted_book") || sName.toLowerCase().contains("enchanted book")) {
                  maxStackSize = 1;
               } else {
                  maxStackSize = s.getMaxStackSize();
               }
               break;
            }
         }
      }

      int emptySlots = 0;
      for (Slot slot : screen.getMenu().slots) {
         if (slot.container instanceof Inventory) {
            if (!slot.hasItem()) {
               emptySlots++;
            }
         }
      }

      int totalItemsCanHold = emptySlots * maxStackSize;
      return totalItemsCanHold / craftAmount;
   }

   public static Component formatLeatherColorComponent(int rgb) {
      int cleanRgb = rgb & 0xFFFFFF;
      String hexStr = String.format("#%06X", cleanRgb);
      MutableComponent comp = Component.literal("§7Color: ");
      MutableComponent hexPart = Component.literal(hexStr);
      hexPart.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(cleanRgb)));
      comp.append(hexPart);
      return comp;
   }

   public static void appendTooltip(ItemStack stack, List<Component> lines) {
      if (!BomboConfig.get().loreAdditionsEnabled || stack == null || stack.isEmpty() || lines == null) return;
      try {
         BomboConfig.Settings s = BomboConfig.get();
         String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
         List<LoreAddition> additions = new ArrayList<>();

         // 1. Cancel Order Tooltip
         if (s.copyCanceledOrderAmount && name.contains("Cancel Order")) {
            String amount = getCanceledOrderAmount(stack);
            if (amount != null) {
               additions.add(new LoreAddition("copyCanceled", Component.literal("§eHold Ctrl + Click to copy " + amount), s.getLorePos("copyCanceled", "BOTTOM"), s.getLoreOrder("copyCanceled", 1)));
            }
         }

         // 2. Supercraft Max Calculator Tooltip
         if (s.supercraftMaxCalculator && name.contains("Supercraft")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
               int maxCrafts = calculateMaxCrafts(screen, stack);
               String pos = s.getLorePos("supercraft", "BOTTOM");
               int order = s.getLoreOrder("supercraft", 2);
               additions.add(new LoreAddition("supercraft", Component.literal("§bMax craftable: §a" + maxCrafts), pos, order));
               additions.add(new LoreAddition("supercraft", Component.literal("§eHold Ctrl + Click to copy " + maxCrafts), pos, order));
            }
         }

         // 3. Starts In Absolute Time Tooltip
         if (s.startsInAbsoluteTime) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
               for (Component line : lore.lines()) {
                  String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
                  Matcher m = STARTS_IN_PATTERN.matcher(clean);
                  if (m.find()) {
                     long days = m.group(1) != null ? Long.parseLong(m.group(1)) : 0L;
                     long hours = m.group(2) != null ? Long.parseLong(m.group(2)) : 0L;
                     long mins = m.group(3) != null ? Long.parseLong(m.group(3)) : 0L;
                     long secs = m.group(4) != null ? Long.parseLong(m.group(4)) : 0L;
                     long totalMillis = (days * 86400L + hours * 3600L + mins * 60L + secs) * 1000L;
                     if (totalMillis > 0L) {
                        long targetEpoch = System.currentTimeMillis() + totalMillis;
                        Locale loc = Locale.getDefault();
                        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", loc);
                        String dayName = dayFmt.format(new Date(targetEpoch));
                        java.text.DateFormat dateFmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT, loc);
                        String dateStr = dateFmt.format(new Date(targetEpoch));
                        java.text.DateFormat timeFmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, loc);
                        String timeStr = timeFmt.format(new Date(targetEpoch));
                        String formatted = dayName + " " + dateStr + " " + timeStr;
                        additions.add(new LoreAddition("startsIn", Component.literal("§7Starts at: §e" + formatted), s.getLorePos("startsIn", "BOTTOM"), s.getLoreOrder("startsIn", 3)));
                        break;
                     }
                  }
               }
            }
         }

         net.minecraft.world.item.component.CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
         net.minecraft.nbt.CompoundTag tag = customData != null ? customData.copyTag() : null;
         net.minecraft.nbt.CompoundTag ea = tag != null ? tag.getCompound("ExtraAttributes").orElse(tag) : null;

         // 4. Dungeon Item Quality
         if (s.showDungeonQuality && ea != null) {
            int quality = 0;
            if (ea.getInt("baseStatBoostPercentage").isPresent()) {
               quality = ea.getInt("baseStatBoostPercentage").get();
            } else if (tag != null && tag.getInt("baseStatBoostPercentage").isPresent()) {
               quality = tag.getInt("baseStatBoostPercentage").get();
            }

            if (quality > 0) {
               String skillReq = ea.getString("dungeon_skill_req").orElse(tag != null ? tag.getString("dungeon_skill_req").orElse("") : "");
               String floor = null;
               int reqNum = -1;
               if (!skillReq.isEmpty()) {
                  String numStr = skillReq.replaceAll("[^0-9]", "");
                  if (!numStr.isEmpty()) {
                     try {
                        reqNum = Integer.parseInt(numStr);
                     } catch (Exception ignored) {}
                  }
               }
               if (reqNum >= 36) floor = "M7";
               else if (reqNum >= 34) floor = "M6";
               else if (reqNum >= 32) floor = "M5";
               else if (reqNum >= 30) floor = "M4";
               else if (reqNum >= 28) floor = "M3";
               else if (reqNum >= 26) floor = "M2";
               else if (reqNum >= 24) floor = "M1/F7";
               else if (reqNum >= 19) floor = "F6";
               else if (reqNum >= 14) floor = "F5";
               else if (reqNum >= 9) floor = "F4";
               else if (reqNum >= 5) floor = "F3";
               else if (reqNum >= 3) floor = "F2";
               else if (reqNum >= 1) floor = "F1";

               String qText = floor != null ? "§7Quality §6" + quality + "% §8(§c" + floor + "§8)" : "§7Quality §6" + quality + "%";
               additions.add(new LoreAddition("dungeonQuality", Component.literal(qText), s.getLorePos("dungeonQuality", "BOTTOM"), s.getLoreOrder("dungeonQuality", 4)));
            }
         }

         // 5. Item Creation Date (Timestamp)
         if (s.showItemCreationDate && ea != null) {
            long ts = ea.getLong("timestamp").orElse(tag != null ? tag.getLong("timestamp").orElse(0L) : 0L);
            if (ts > 0L) {
               SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
               additions.add(new LoreAddition("itemCreationDate", Component.literal("§7Created: §e" + sdf.format(new Date(ts))), s.getLorePos("itemCreationDate", "BOTTOM"), s.getLoreOrder("itemCreationDate", 5)));
            }
         }

         // 6. Leather Armor Hex Color
         if (s.showLeatherColor) {
            boolean alreadyHasColor = false;
            for (Component line : lines) {
               if (line != null && line.getString().toLowerCase().contains("color: #")) {
                  alreadyHasColor = true;
                  break;
               }
            }
            if (!alreadyHasColor) {
               net.minecraft.world.item.component.DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
               if (dyed != null) {
                  int rgb = dyed.rgb();
                  additions.add(new LoreAddition("leatherColor", formatLeatherColorComponent(rgb), s.getLorePos("leatherColor", "BOTTOM"), s.getLoreOrder("leatherColor", 6)));
               } else if (tag != null) {
                  if (tag.getCompound("display").isPresent() && tag.getCompound("display").get().getInt("color").isPresent()) {
                     int rgb = tag.getCompound("display").get().getInt("color").get();
                     additions.add(new LoreAddition("leatherColor", formatLeatherColorComponent(rgb), s.getLorePos("leatherColor", "BOTTOM"), s.getLoreOrder("leatherColor", 6)));
                  } else if (tag.getInt("color").isPresent()) {
                     int rgb = tag.getInt("color").get();
                     additions.add(new LoreAddition("leatherColor", formatLeatherColorComponent(rgb), s.getLorePos("leatherColor", "BOTTOM"), s.getLoreOrder("leatherColor", 6)));
                  }
               }
            }
         }

         // 7. Museum Donated Status
         if (s.showMuseumDonated && ea != null) {
            boolean donated = ea.getBoolean("donated_museum").orElse(false)
               || (ea.getByte("donated_museum").orElse((byte)0) == 1)
               || (ea.getInt("donated_museum").orElse(0) == 1)
               || (tag != null && tag.getBoolean("donated_museum").orElse(false))
               || (tag != null && tag.getByte("donated_museum").orElse((byte)0) == 1);
            if (donated) {
               additions.add(new LoreAddition("museumDonated", Component.literal("§dDonated to Museum"), s.getLorePos("museumDonated", "BOTTOM"), s.getLoreOrder("museumDonated", 7)));
            }
         }

         // 8. Skyblock Item ID (Only show for real Skyblock items with Hypixel ExtraAttributes)
         if (s.showSkyblockId) {
            String sbId = SkyblockUtils.getStrictSkyblockId(stack);
            if (sbId != null && !sbId.isEmpty()) {
               additions.add(new LoreAddition("skyblockId", Component.literal("§7Skyblock ID: §8" + sbId), s.getLorePos("skyblockId", "BOTTOM"), s.getLoreOrder("skyblockId", 8)));
            }
         }

         applyAdditionsToLines(lines, additions);
      } catch (Throwable ignored) {}
   }

   public static void applyAdditionsToLines(List<Component> lines, List<LoreAddition> additions) {
      if (lines == null || additions == null || additions.isEmpty()) return;
      List<LoreAddition> top = new ArrayList<>();
      List<LoreAddition> bottom = new ArrayList<>();

      for (LoreAddition a : additions) {
         if ("TOP".equalsIgnoreCase(a.position)) {
            top.add(a);
         } else {
            bottom.add(a);
         }
      }

      top.sort(Comparator.comparingInt(a -> a.order));
      bottom.sort(Comparator.comparingInt(a -> a.order));

      int insertIndex = Math.min(1, lines.size());
      for (LoreAddition a : top) {
         lines.add(insertIndex++, a.line);
      }

      for (LoreAddition a : bottom) {
         lines.add(a.line);
      }
   }

   public static boolean handleCtrlClick(AbstractContainerScreen<?> screen, Slot slot) {
      if (screen == null || slot == null || !slot.hasItem()) return false;
      ItemStack stack = slot.getItem();
      String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");

      if (BomboConfig.get().copyCanceledOrderAmount && name.contains("Cancel Order")) {
         String amount = getCanceledOrderAmount(stack);
         if (amount != null) {
            Minecraft.getInstance().keyboardHandler.setClipboard(amount);
            Bomboaddons.sendMessage("§a[BomboAddons] Copied " + amount + " to clipboard!");
            return false;
         }
      }

      if (BomboConfig.get().supercraftMaxCalculator && name.contains("Supercraft")) {
         int maxCrafts = calculateMaxCrafts(screen, stack);
         Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(maxCrafts));
         Bomboaddons.sendMessage("§a[BomboAddons] Copied " + maxCrafts + " to clipboard!");
         return true;
      }

      return false;
   }
}
