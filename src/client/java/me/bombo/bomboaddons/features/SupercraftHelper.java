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
      String screenTitle = screen.getTitle().getString().replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase();

      // Check if the recipe screen title indicates a non-stackable item
      if (isNonStackableKeyword(screenTitle)) {
         maxStackSize = 1;
      }

      // Check supercraft item lore to see what item it is crafting (e.g. recipe ingredients / target)
      ItemLore supercraftLore = supercraftStack.get(DataComponents.LORE);
      if (supercraftLore != null) {
         for (Component l : supercraftLore.lines()) {
            String lClean = l.getString().replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase();
            if (isNonStackableKeyword(lClean)) {
               maxStackSize = 1;
               break;
            }
         }
      }

      for (Slot slot : screen.getMenu().slots) {
         if (!(slot.container instanceof Inventory) && slot.hasItem()) {
            ItemStack s = slot.getItem();
            String sName = s.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "");
            if (!sName.contains("Supercraft") && !sName.contains("Quick Craft") && !sName.contains("Close") && !sName.contains("Back") && !sName.contains("Recipe")) {
               String sNameLower = sName.toLowerCase();
               if (s.is(Items.ENCHANTED_BOOK) || s.getItem().toString().contains("enchanted_book") || isNonStackableKeyword(sNameLower) || s.getMaxStackSize() == 1) {
                  maxStackSize = 1;
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

   private static boolean isNonStackableKeyword(String text) {
      if (text == null) return false;
      String t = text.toLowerCase();
      return t.contains("backpack") || t.contains("enchanted book") || t.contains("book")
            || t.contains("helmet") || t.contains("chestplate") || t.contains("leggings") || t.contains("boots")
            || t.contains("sword") || t.contains("bow") || t.contains("wand") || t.contains("rod")
            || t.contains("pickaxe") || t.contains("axe") || t.contains("shovel") || t.contains("spade")
            || t.contains("hoe") || t.contains("drill") || t.contains("shears") || t.contains("scythe")
            || t.contains("dagger") || t.contains("shortbow") || t.contains("talisman") || t.contains("ring")
            || t.contains("artifact") || t.contains("relic") || t.contains("necklace") || t.contains("cloak")
            || t.contains("belt") || t.contains("gloves") || t.contains("bracelet") || t.contains("pouch")
            || t.contains("sack") || t.contains("vacuum") || t.contains("blade") || t.contains("staff");
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

   public static void appendTooltipInPlace(ItemStack stack, List<Component> lines) {
      if (!BomboConfig.get().loreAdditionsEnabled || stack == null || stack.isEmpty() || lines == null) {
         return;
      }
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
            if (mc.gui.screen() instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
               int maxCrafts = calculateMaxCrafts(screen, stack);
               String pos = s.getLorePos("supercraft", "BOTTOM");
               int order = s.getLoreOrder("supercraft", 2);
               if (maxCrafts > 0) {
                  additions.add(new LoreAddition("supercraft", Component.literal("§aHold Ctrl + Click to copy " + maxCrafts + " (Max Crafts)"), pos, order));
               } else {
                  additions.add(new LoreAddition("supercraft", Component.literal("§cHold Ctrl + Click: Not enough materials for 1 craft"), pos, order));
               }
            }
         }

         // Hypixel ExtraAttributes tags
         net.minecraft.nbt.CompoundTag ea = SkyblockUtils.getExtraAttributes(stack);
         net.minecraft.world.item.component.CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
         net.minecraft.nbt.CompoundTag tag = customData != null ? customData.copyTag() : null;
         if (ea == null && tag != null && tag.contains("ExtraAttributes")) {
            ea = tag.getCompound("ExtraAttributes").orElse(tag);
         }

         // Starts In Absolute Time
         if (s.startsInAbsoluteTime) {
            ItemLore loreComp = stack.get(DataComponents.LORE);
            if (loreComp != null) {
               for (Component l : loreComp.lines()) {
                  String clean = l.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                  String lower = clean.toLowerCase(Locale.ROOT);
                  if (lower.startsWith("starts in:") || lower.startsWith("starts in ") || lower.startsWith("ends in:") || lower.startsWith("ends in ")) {
                     boolean isStarts = lower.startsWith("starts");
                     long durationMs = parseDurationMs(clean);
                     if (durationMs > 0) {
                        long targetEpoch = System.currentTimeMillis() + durationMs;
                        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                        SimpleDateFormat dayFmt = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault());
                        boolean isDifferentDay = !isSameDay(System.currentTimeMillis(), targetEpoch);
                        String timeStr = isDifferentDay ? dayFmt.format(new Date(targetEpoch)) : sdf.format(new Date(targetEpoch));
                        String label = isStarts ? ("§7Starts at: §e" + timeStr) : ("§7Ends at: §e" + timeStr);
                        additions.add(new LoreAddition("startsIn", Component.literal(label), s.getLorePos("startsIn", "BOTTOM"), s.getLoreOrder("startsIn", 3)));
                        break;
                     }
                  }
               }
            }
         }

         // 3. Chocolate Factory Rabbit Rarity
         if (s.showRabbitRarity && ea != null) {
            String rabbitId = ea.getString("rabbit").orElse(tag != null ? tag.getString("rabbit").orElse("") : "");
            if (!rabbitId.isEmpty()) {
               String rName = rabbitId.replace("_", " ").toLowerCase(Locale.ROOT);
               String rRarity = "COMMON";
               String colorCode = "§f";

               if (rName.contains("mythic") || rName.contains("divine")) {
                  rRarity = "MYTHIC";
                  colorCode = "§d§l";
               } else if (rName.contains("legendary")) {
                  rRarity = "LEGENDARY";
                  colorCode = "§6§l";
               } else if (rName.contains("epic")) {
                  rRarity = "EPIC";
                  colorCode = "§5§l";
               } else if (rName.contains("rare")) {
                  rRarity = "RARE";
                  colorCode = "§9§l";
               } else if (rName.contains("uncommon")) {
                  rRarity = "UNCOMMON";
                  colorCode = "§a§l";
               }

               additions.add(new LoreAddition("rabbitRarity", Component.literal("§7Rabbit: " + colorCode + rRarity), s.getLorePos("rabbitRarity", "BOTTOM"), s.getLoreOrder("rabbitRarity", 3)));
            }
         }

         // 4. Dungeon Item Quality / Drop Floor
         if (s.showDungeonQuality) {
            int quality = -1;
            if (ea != null) {
               net.minecraft.nbt.Tag qTag = ea.get("baseStatBoostPercentage");
               if (qTag instanceof net.minecraft.nbt.NumericTag num) {
                  quality = (int) Math.round(num.doubleValue());
               }
            }

            if (quality >= 0) {
               String floor = null;
               int tier = -1;
               if (ea != null) {
                  net.minecraft.nbt.Tag tTag = ea.get("item_tier");
                  if (tTag instanceof net.minecraft.nbt.NumericTag num) {
                     tier = num.intValue();
                  }
               }

               ItemLore loreComp = stack.get(DataComponents.LORE);
               boolean isMasterMode = false;
               if (loreComp != null) {
                  for (Component l : loreComp.lines()) {
                     String sClean = l.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                     if (sClean.contains("Master Mode")) {
                        isMasterMode = true;
                     }
                     if (sClean.contains("Catacombs Floor") || sClean.contains("Floor ")) {
                        String up = sClean.toUpperCase(Locale.ROOT);
                        if (up.contains("FLOOR VII") || up.contains("FLOOR 7")) floor = "7";
                        else if (up.contains("FLOOR VI") || up.contains("FLOOR 6")) floor = "6";
                        else if (up.contains("FLOOR V") || up.contains("FLOOR 5")) floor = "5";
                        else if (up.contains("FLOOR IV") || up.contains("FLOOR 4")) floor = "4";
                        else if (up.contains("FLOOR III") || up.contains("FLOOR 3")) floor = "3";
                        else if (up.contains("FLOOR II") || up.contains("FLOOR 2")) floor = "2";
                        else if (up.contains("FLOOR I") || up.contains("FLOOR 1")) floor = "1";
                     }
                  }
               }

               if (floor == null && tier >= 1 && tier <= 7) {
                  floor = String.valueOf(tier);
               }

               String floorPrefix = isMasterMode ? "M" : "F";
               String floorStr = floor != null ? (floorPrefix + floor) : null;
               String color = quality >= 50 ? "§6§l" : (quality >= 40 ? "§e" : "§7");
               String qText = floorStr != null
                     ? ("§7Quality: " + color + quality + "% §8(§c" + floorStr + "§8)")
                     : ("§7Quality: " + color + quality + "%");
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

         applyAdditionsInPlace(lines, additions);
      } catch (Throwable ignored) {}
   }

   public static void applyAdditionsInPlace(List<Component> lines, List<LoreAddition> additions) {
      if (lines == null || additions == null || additions.isEmpty()) return;
      try {
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
      } catch (Throwable ignored) {}
   }

   public static List<Component> appendTooltip(ItemStack stack, List<Component> lines) {
      List<Component> mutable = mutableCopy(lines);
      appendTooltipInPlace(stack, mutable);
      return mutable;
   }

   public static List<Component> applyAdditionsToLines(List<Component> lines, List<LoreAddition> additions) {
      List<Component> mutableLines = mutableCopy(lines);
      applyAdditionsInPlace(mutableLines, additions);
      return mutableLines;
   }

   private static List<Component> mutableCopy(List<Component> lines) {
      return lines == null ? new ArrayList<>() : new ArrayList<>(lines);
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

   private static long parseDurationMs(String text) {
      long totalMs = 0;
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*([dhms])", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
      while (m.find()) {
         try {
            long val = Long.parseLong(m.group(1));
            String unit = m.group(2).toLowerCase(Locale.ROOT);
            switch (unit) {
               case "d" -> totalMs += val * 86_400_000L;
               case "h" -> totalMs += val * 3_600_000L;
               case "m" -> totalMs += val * 60_000L;
               case "s" -> totalMs += val * 1_000L;
            }
         } catch (Exception ignored) {}
      }
      return totalMs;
   }

   private static boolean isSameDay(long t1, long t2) {
      java.util.Calendar c1 = java.util.Calendar.getInstance();
      c1.setTimeInMillis(t1);
      java.util.Calendar c2 = java.util.Calendar.getInstance();
      c2.setTimeInMillis(t2);
      return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR)
            && c1.get(java.util.Calendar.DAY_OF_YEAR) == c2.get(java.util.Calendar.DAY_OF_YEAR);
   }
}
