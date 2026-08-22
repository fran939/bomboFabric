package me.bombo.bomboaddons.features;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class BestiaryManager {

   private static final java.util.regex.Pattern BESTIARY_LEVELUP_PATTERN = java.util.regex.Pattern.compile("(?i)BESTIARY\\s+(.+?)\\s+([0-9IVXLCDM]+)\\s*(?:➜|➡|->|»|\\u27A1|\\u2794|\\u00BB)\\s*([0-9IVXLCDM]+)");

   public static int parseRomanOrInt(String s) {
      if (s == null) return 0;
      s = s.trim().toUpperCase(Locale.ROOT);
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException e) {
         int total = 0;
         int prev = 0;
         for (int i = s.length() - 1; i >= 0; i--) {
            int val = 0;
            switch (s.charAt(i)) {
               case 'I': val = 1; break;
               case 'V': val = 5; break;
               case 'X': val = 10; break;
               case 'L': val = 50; break;
               case 'C': val = 100; break;
               case 'D': val = 500; break;
               case 'M': val = 1000; break;
               default: val = 0; break;
            }
            if (val < prev) total -= val;
            else { total += val; prev = val; }
         }
         return total;
      }
   }

   public static void onChatMessage(String rawMessage) {
      if (rawMessage == null || !rawMessage.toUpperCase(Locale.ROOT).contains("BESTIARY")) return;
      String clean = rawMessage.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      java.util.regex.Matcher m = BESTIARY_LEVELUP_PATTERN.matcher(clean);
      if (m.find()) {
         String mobName = m.group(1).trim();
         int newTier = parseRomanOrInt(m.group(2).trim());
         int targetTier = parseRomanOrInt(m.group(3).trim());
         int finalTier = Math.max(newTier, targetTier);
         checkAndDisableMaxedMob(mobName, finalTier);
      }
   }

   public static void checkAndDisableMaxedMob(String mobName, int currentTier) {
      BestiaryDataFetcher.BestiaryMobRule rule = BestiaryDataFetcher.getRule(mobName, null);
      int maxTier = (rule != null && rule.maxTier > 0) ? rule.maxTier : 20;
      if (currentTier >= maxTier) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.highlights != null) {
            String cleanMob = mobName.toLowerCase(Locale.ROOT).trim();
            List<String> toRemove = new ArrayList<>();
            for (String k : s.highlights.keySet()) {
               String cleanKey = k.toLowerCase(Locale.ROOT).trim().replaceAll("(?i)\\s+(?:X{0,3}(?:IX|IV|V?I{1,3})|L|C|D|M|[0-9]+)$", "").trim();
               if (cleanKey.equals(cleanMob) || k.equalsIgnoreCase(mobName)) {
                  toRemove.add(k);
               }
            }
            if (!toRemove.isEmpty()) {
               for (String k : toRemove) {
                  s.highlights.remove(k);
               }
               BomboConfig.save();
               Minecraft mc = Minecraft.getInstance();
               if (mc.player != null) {
                  Component msg = Component.literal("§8[§3Bombo§8] §aMaxed bestiary reached for §e" + mobName + "§a, disabling highlight. ")
                     .append(Component.literal("§b§n[Click to re-enable]")
                        .withStyle(style -> style
                           .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/b behighlight add " + mobName))
                           .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§eClick to re-enable highlight for " + mobName)))
                        )
                     );
                  mc.player.sendSystemMessage(msg);
               }
            }
         }
      }
   }

   public static boolean isBestiaryGui(AbstractContainerScreen<?> screen) {
      if (screen == null) return false;
      String rawTitle = screen.getTitle() != null ? screen.getTitle().getString() : "";
      String clean = rawTitle.replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
      if (clean.contains("bestiary")) {
         return true;
      }
      if (screen.getMenu() != null) {
         for (Slot slot : screen.getMenu().slots) {
            if (slot != null && slot.hasItem()) {
               ItemStack stack = slot.getItem();
               String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
               if (name.contains("search bestiary") || name.contains("bestiary milestone") || name.equals("bestiary")) {
                  return true;
               }
               ItemLore lore = stack.get(DataComponents.LORE);
               if (lore != null) {
                  for (Component line : lore.lines()) {
                     String str = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
                     if (str.contains("search through all of the mobs in the bestiary") ||
                         str.contains("reach new milestones in your bestiary") ||
                         str.contains("click to open bestiary milestones") ||
                         str.contains("unlock it in your bestiary") ||
                         str.contains("families found:")) {
                        return true;
                     }
                  }
               }
            }
         }
      }
      return false;
   }

   public static boolean isBestiaryGui(String rawTitle) {
      if (rawTitle == null) return false;
      String clean = rawTitle.replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
      return clean.contains("bestiary");
   }

   public static String getCategoryFromGui(AbstractContainerScreen<?> screen) {
      if (screen == null) return "Your Island";
      
      // 1. Try to read from top icon slot (slot 4)
      try {
         if (screen.getMenu() != null && screen.getMenu().slots.size() > 4) {
            Slot topSlot = screen.getMenu().slots.get(4);
            if (topSlot != null && topSlot.hasItem()) {
               ItemStack topItem = topSlot.getItem();
               String topName = topItem.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               if (!topName.isEmpty() && !topName.equalsIgnoreCase("Bestiary") && !topName.contains("Go Back") && !topName.contains("Close")) {
                  return topName;
               }
            }
         }
      } catch (Throwable ignored) {}

      // 2. Try to parse from GUI title
      String rawTitle = screen.getTitle().getString();
      String cleanTitle = rawTitle.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      String[] parts = cleanTitle.split("➜|->|➔|»");
      if (parts.length > 1) {
         return parts[1].trim();
      }
      if (cleanTitle.toLowerCase(Locale.ROOT).startsWith("bestiary")) {
         return cleanTitle.substring(cleanTitle.indexOf(' ') + 1).trim();
      }
      return cleanTitle;
   }

   public static String mapCategoryToIsland(String categoryName) {
      if (categoryName == null) return "";
      String lower = categoryName.toLowerCase(Locale.ROOT).trim();
      if (lower.contains("your island") || lower.contains("private island")) {
         return "Private Island";
      }
      if (lower.contains("hub") && !lower.contains("dungeon")) {
         return "Hub";
      }
      if (lower.contains("farming") || lower.contains("barn") || lower.contains("mushroom")) {
         return "The Farming Islands";
      }
      if (lower.contains("park")) {
         return "The Park";
      }
      if (lower.contains("spider")) {
         return "Spider's Den";
      }
      if (lower.contains("end")) {
         return "The End";
      }
      if (lower.contains("crimson") || lower.contains("nether")) {
         return "Crimson Isle";
      }
      if (lower.contains("deep caverns") || lower.contains("caverns")) {
         return "Deep Caverns";
      }
      if (lower.contains("dwarven")) {
         return "Dwarven Mines";
      }
      if (lower.contains("hollows")) {
         return "Crystal Hollows";
      }
      if (lower.contains("glacite") || lower.contains("mineshaft")) {
         return "Glacite Mineshafts";
      }
      if (lower.contains("rift")) {
         return "The Rift";
      }
      if (lower.contains("garden")) {
         return "Garden";
      }
      if (lower.contains("dungeon") || lower.contains("catacomb")) {
         return "Dungeons";
      }
      if (lower.contains("torrhus") || lower.contains("canyon")) {
         return "Torrhus Canyon";
      }
      if (lower.contains("galatea")) {
         return "Galatea";
      }
      if (lower.contains("fishing") || lower.contains("mythological") || lower.contains("jerry") || lower.contains("spooky") || lower.contains("event")) {
         return ""; // Everywhere / Global
      }
      return categoryName.trim(); // Default to category name directly as island if unknown
   }

   public static String getCategoryForIsland(String requiredIsland) {
      if (requiredIsland == null || requiredIsland.trim().isEmpty() || "*".equals(requiredIsland) || "All".equalsIgnoreCase(requiredIsland) || "Everywhere".equalsIgnoreCase(requiredIsland)) {
         return "Global / Non-Island Bestiary";
      }
      String lower = requiredIsland.toLowerCase(Locale.ROOT).trim();
      if (lower.contains("private island") || lower.contains("your island")) return "Your Island Bestiary";
      if (lower.contains("hub") && !lower.contains("dungeon")) return "Hub Bestiary";
      if (lower.contains("farming") || lower.contains("barn") || lower.contains("mushroom")) return "The Farming Islands Bestiary";
      if (lower.contains("park")) return "The Park Bestiary";
      if (lower.contains("spider")) return "Spider's Den Bestiary";
      if (lower.contains("end")) return "The End Bestiary";
      if (lower.contains("crimson") || lower.contains("nether")) return "Crimson Isle Bestiary";
      if (lower.contains("deep caverns") || lower.contains("caverns")) return "Deep Caverns Bestiary";
      if (lower.contains("dwarven")) return "Dwarven Mines Bestiary";
      if (lower.contains("hollows")) return "Crystal Hollows Bestiary";
      if (lower.contains("glacite") || lower.contains("mineshaft")) return "Glacite Mineshafts Bestiary";
      if (lower.contains("rift")) return "The Rift Bestiary";
      if (lower.contains("garden")) return "Garden Bestiary";
      if (lower.contains("dungeon") || lower.contains("catacomb")) return "Dungeons Bestiary";
      if (lower.contains("torrhus") || lower.contains("canyon")) return "Torrhus Canyon Bestiary";
      if (lower.contains("galatea")) return "Galatea Bestiary";
      if (lower.contains("fishing")) return "Fishing Bestiary";
      if (lower.contains("mythological")) return "Mythological Creatures Bestiary";
      if (lower.contains("jerry")) return "Jerry Bestiary";
      if (lower.contains("spooky")) return "Spooky Festival Bestiary";
      if (requiredIsland.endsWith("Bestiary") || requiredIsland.endsWith("bestiary")) {
         return requiredIsland;
      }
      return requiredIsland + " Bestiary";
   }

   public static List<String> getGeneralHighlights(Map<String, BomboConfig.HighlightInfo> highlights) {
      List<String> list = new ArrayList<>();
      if (highlights != null) {
         for (Map.Entry<String, BomboConfig.HighlightInfo> entry : highlights.entrySet()) {
            BomboConfig.HighlightInfo info = entry.getValue();
            if (info != null && !info.isBestiary) {
               list.add(entry.getKey());
            }
         }
         Collections.sort(list);
      }
      return list;
   }

   public static Map<String, List<String>> getGroupedBestiary(Map<String, BomboConfig.HighlightInfo> highlights) {
      Map<String, List<String>> grouped = new LinkedHashMap<>();
      String[] standardCategories = new String[]{
         "Hub Bestiary",
         "Garden Bestiary",
         "Your Island Bestiary",
         "The Farming Islands Bestiary",
         "The Park Bestiary",
         "Spider's Den Bestiary",
         "The End Bestiary",
         "Crimson Isle Bestiary",
         "Deep Caverns Bestiary",
         "Dwarven Mines Bestiary",
         "Crystal Hollows Bestiary",
         "Glacite Mineshafts Bestiary",
         "The Rift Bestiary",
         "Dungeons Bestiary",
         "Fishing Bestiary",
         "Mythological Creatures Bestiary",
         "Jerry Bestiary",
         "Spooky Festival Bestiary",
         "Global / Non-Island Bestiary"
      };
      for (String cat : standardCategories) {
         grouped.put(cat, new ArrayList<>());
      }

      if (highlights != null) {
         ArrayList<String> sortedMobs = new ArrayList<>(highlights.keySet());
         Collections.sort(sortedMobs);
         for (String mobName : sortedMobs) {
            BomboConfig.HighlightInfo info = highlights.get(mobName);
            if (info != null && info.isBestiary) {
               String cat = getCategoryForIsland(info.requiredIsland);
               grouped.computeIfAbsent(cat, k -> new ArrayList<>()).add(mobName);
            }
         }
      }

      grouped.entrySet().removeIf(e -> e.getValue().isEmpty());
      return grouped;
   }

      public static String cleanMobName(String raw) {
      if (raw == null) return "";
      String clean = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      if (clean.endsWith(" (Unlocked)")) {
         clean = clean.substring(0, clean.length() - 11).trim();
      }
      return clean.replaceAll("(?i)\\s+(?:M{0,4}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})|[0-9]+)$", "").trim();
   }

   public static String cleanMobName(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return "";
      String raw = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      if (raw.endsWith(" (Unlocked)")) {
         raw = raw.substring(0, raw.length() - 11).trim();
      }
      // Strip trailing Roman numerals and tiers (e.g. "Crypt Ghoul VIII" -> "Crypt Ghoul", "Wolf XV" -> "Wolf")
      raw = raw.replaceAll("(?i)\\s+(?:M{0,4}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3})|[0-9]+)$", "").trim();
      return raw;
   }

   public static boolean isNavigationalOrInvalid(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return true;
      if (stack.is(Items.GRAY_STAINED_GLASS_PANE) || stack.is(Items.BLACK_STAINED_GLASS_PANE) || stack.is(Items.WHITE_STAINED_GLASS_PANE) || stack.is(Items.BARRIER) || stack.is(Items.ARROW) || stack.is(Items.OAK_SIGN) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.BOOK)) {
         return true;
      }
      String name = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(Locale.ROOT);
      if (name.contains("go back") || name.contains("close") || name.contains("search") || name.contains("page ") || name.equals("back") ||
          name.contains("milestone") || name.contains("toggle families") || name.contains("families completed display")) {
         return true;
      }
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore != null) {
         for (Component line : lore.lines()) {
            String lineStr = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
            if (lineStr.contains("families found:") || lineStr.contains("view all of the") || lineStr.contains("click to toggle!") || lineStr.contains("milestone rewards")) {
               return true; // Category header or toggle button
            }
         }
      }
      return false;
   }

   public static boolean isMobCompleted(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) return false;
      
      for (Component line : lore.lines()) {
         String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
         if (clean.contains("you haven't unlocked this family yet!")) {
            return false;
         }
         if (clean.contains("(max!)") || clean.contains("(max)") || clean.contains("100% (max!)") || clean.contains("bestiary maxed") || clean.contains("families completed: 100%")) {
            return true;
         }
         if (clean.contains("progress to tier")) {
            return false;
         }
      }

      for (Component line : lore.lines()) {
         String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
         if (clean.contains("overall progress: 100%")) {
            return true;
         }
      }
      return false;
   }

   public static String getColorFormatting(String color) {
      if (color == null) return "§6";
      switch (color.toUpperCase(Locale.ROOT)) {
         case "RED": return "§c";
         case "DARK_RED": return "§4";
         case "GOLD": return "§6";
         case "YELLOW": return "§e";
         case "GREEN": return "§a";
         case "DARK_GREEN": return "§2";
         case "AQUA": return "§b";
         case "DARK_AQUA": return "§3";
         case "BLUE": return "§9";
         case "DARK_BLUE": return "§1";
         case "LIGHT_PURPLE": case "PINK": return "§d";
         case "DARK_PURPLE": case "PURPLE": return "§5";
         case "WHITE": return "§f";
         case "GRAY": return "§7";
         case "DARK_GRAY": return "§8";
         case "BLACK": return "§0";
         default: return "§f";
      }
   }

   public static String getNextColor(String current) {
      String[] colors = new String[]{"GOLD", "YELLOW", "GREEN", "AQUA", "BLUE", "LIGHT_PURPLE", "RED", "WHITE", "GRAY", "DARK_GREEN", "DARK_AQUA", "DARK_BLUE", "DARK_PURPLE", "DARK_RED"};
      if (current == null) return colors[0];
      for (int i = 0; i < colors.length; i++) {
         if (colors[i].equalsIgnoreCase(current)) {
            return colors[(i + 1) % colors.length];
         }
      }
      return colors[0];
   }

   public static String getCategoryColor(String category) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.bestiaryCategoryColors != null) {
         String c = s.bestiaryCategoryColors.get(category);
         if (c != null && !c.isEmpty()) return c;
      }
      return "GOLD";
   }

   public static boolean getCategoryTracer(String category) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.bestiaryCategoryTracers != null) {
         Boolean t = s.bestiaryCategoryTracers.get(category);
         if (t != null) return t;
      }
      return false;
   }

   public static void setCategoryColor(String category, String color) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return;
      if (s.bestiaryCategoryColors == null) s.bestiaryCategoryColors = new java.util.HashMap<>();
      s.bestiaryCategoryColors.put(category, color);
      String islandReq = mapCategoryToIsland(category);
      if (s.highlights != null) {
         for (Map.Entry<String, BomboConfig.HighlightInfo> entry : s.highlights.entrySet()) {
            BomboConfig.HighlightInfo hi = entry.getValue();
            if (hi != null) {
               String mobIsland = hi.requiredIsland != null ? hi.requiredIsland : "";
               String mobCat = getCategoryForIsland(mobIsland);
               if (category.equalsIgnoreCase(mobCat) || islandReq.equalsIgnoreCase(mobIsland)) {
                  hi.color = color;
               }
            }
         }
      }
      BomboConfig.save();
   }

   public static void setCategoryTracer(String category, boolean tracer) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return;
      if (s.bestiaryCategoryTracers == null) s.bestiaryCategoryTracers = new java.util.HashMap<>();
      s.bestiaryCategoryTracers.put(category, tracer);
      String islandReq = mapCategoryToIsland(category);
      if (s.highlights != null) {
         for (Map.Entry<String, BomboConfig.HighlightInfo> entry : s.highlights.entrySet()) {
            BomboConfig.HighlightInfo hi = entry.getValue();
            if (hi != null) {
               String mobIsland = hi.requiredIsland != null ? hi.requiredIsland : "";
               String mobCat = getCategoryForIsland(mobIsland);
               if (category.equalsIgnoreCase(mobCat) || islandReq.equalsIgnoreCase(mobIsland)) {
                  hi.tracer = tracer;
               }
            }
         }
      }
      BomboConfig.save();
   }

   public static boolean handleBestiaryKey(ItemStack hoveredItem, Slot hoveredSlot, AbstractContainerScreen<?> screen) {
      if (screen == null || !isBestiaryGui(screen)) return false;
      if (hoveredSlot != null && hoveredSlot.index == 4) {
         return highlightAllNonCompleted(screen);
      }
      if (hoveredItem != null && !hoveredItem.isEmpty()) {
         ItemLore lore = hoveredItem.get(DataComponents.LORE);
         if (lore != null) {
            for (Component line : lore.lines()) {
               if (line.getString().contains("Families Found:")) {
                  return highlightAllNonCompleted(screen);
               }
            }
         }
         return highlightSingleMob(hoveredItem, screen);
      }
      return false;
   }

   public static boolean highlightSingleMob(ItemStack mobItem, AbstractContainerScreen<?> screen) {
      if (mobItem == null || mobItem.isEmpty() || isNavigationalOrInvalid(mobItem)) return false;
      String mobName = cleanMobName(mobItem);
      if (mobName.isEmpty()) return false;
      String cat = getCategoryFromGui(screen);
      String islandReq = mapCategoryToIsland(cat);
      String color = getCategoryColor(cat);
      boolean tracer = getCategoryTracer(cat);
      
      BomboConfig.Settings s = BomboConfig.get();
      if (s.highlights == null) s.highlights = new java.util.HashMap<>();
      BomboConfig.HighlightInfo info = new BomboConfig.HighlightInfo(color, false, true, tracer, islandReq, true);
      BestiaryDataFetcher.BestiaryMobRule rule = BestiaryDataFetcher.getRule(mobName, islandReq);
      if (rule != null) {
         if (rule.entityType != null) info.entityType = rule.entityType;
         if (rule.heads != null && !rule.heads.isEmpty()) info.headHashes = new ArrayList<>(rule.heads);
         if (rule.armor != null) info.armorType = rule.armor;
         if (rule.playerName != null) info.playerName = rule.playerName;
         if (rule.riding != null) info.ridingType = rule.riding;
         if (rule.heldItem != null) info.heldItem = rule.heldItem;
         if (rule.subarea != null) info.requiredSubarea = rule.subarea;
      }
      s.highlights.put(mobName.toLowerCase(Locale.ROOT), info);
      s.highlightsEnabled = true;
      BomboConfig.save();

      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         String islandDisplay = islandReq.isEmpty() ? "Everywhere" : islandReq;
         mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aAdded Bestiary highlight for §e" + mobName + " §7(Island: §b" + islandDisplay + "§7, Color: " + getColorFormatting(color) + color + "§7)"));
      }
      return true;
   }

   public static boolean highlightAllNonCompleted(AbstractContainerScreen<?> screen) {
      if (screen == null || screen.getMenu() == null) return false;
      String cat = getCategoryFromGui(screen);
      String islandReq = mapCategoryToIsland(cat);
      String color = getCategoryColor(cat);
      boolean tracer = getCategoryTracer(cat);

      BomboConfig.Settings s = BomboConfig.get();
      if (s.highlights == null) s.highlights = new java.util.HashMap<>();

      int addedCount = 0;
      int maxSlots = Math.min(screen.getMenu().slots.size(), 54);
      for (int i = 9; i < maxSlots; i++) {
         Slot slot = screen.getMenu().slots.get(i);
         if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (!isNavigationalOrInvalid(stack) && !isMobCompleted(stack)) {
               String mobName = cleanMobName(stack);
               if (!mobName.isEmpty()) {
                  BomboConfig.HighlightInfo info = new BomboConfig.HighlightInfo(color, false, true, tracer, islandReq, true);
                  BestiaryDataFetcher.BestiaryMobRule rule = BestiaryDataFetcher.getRule(mobName, islandReq);
                  if (rule != null) {
                     if (rule.entityType != null) info.entityType = rule.entityType;
                     if (rule.heads != null && !rule.heads.isEmpty()) info.headHashes = new ArrayList<>(rule.heads);
                     if (rule.armor != null) info.armorType = rule.armor;
                     if (rule.playerName != null) info.playerName = rule.playerName;
                     if (rule.riding != null) info.ridingType = rule.riding;
                     if (rule.heldItem != null) info.heldItem = rule.heldItem;
                     if (rule.subarea != null) info.requiredSubarea = rule.subarea;
                  }
                  s.highlights.put(mobName.toLowerCase(Locale.ROOT), info);
                  addedCount++;
               }
            }
         }
      }

      if (addedCount > 0) {
         s.highlightsEnabled = true;
         BomboConfig.save();
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            String islandDisplay = islandReq.isEmpty() ? "Everywhere" : islandReq;
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aHighlighted §e" + addedCount + " §anon-completed Bestiary mobs for §b" + cat + " §7(Island: §e" + islandDisplay + "§7, Color: " + getColorFormatting(color) + color + "§7)!"));
         }
         return true;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §eAll mobs in " + cat + " are already completed or no mobs found!"));
         }
         return true;
      }
   }
}
