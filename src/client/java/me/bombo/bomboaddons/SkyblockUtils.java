package me.bombo.bomboaddons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.mixin.PlayerTabOverlayAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public class SkyblockUtils {
   public static String currentProfileId = "";
   public static String lastExecutedCommand = "";
   public static String simulatedArea = null;

   public static void setSimulatedArea(String area) {
      if (area == null || area.trim().isEmpty() || area.equalsIgnoreCase("clear") || area.equalsIgnoreCase("reset") || area.equalsIgnoreCase("none")) {
         simulatedArea = null;
      } else {
         simulatedArea = area.trim();
         BomboaddonsClient.currentArea = simulatedArea;
      }
      cachedLocation = null;
      lastCacheTime = 0L;
   }

   public static String getSimulatedArea() {
      return simulatedArea;
   }

   public static String replaceCoordPlaceholders(String text) {
      if (text != null && text.contains("$")) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return text;
         } else {
            int x = (int)Math.floor(mc.player.getX());
            int y = (int)Math.floor(mc.player.getY());
            int z = (int)Math.floor(mc.player.getZ());
            String coords = x + " " + y + " " + z;
            String result = text;
            if (text.contains("$coords")) {
               result = text.replace("$coords", coords);
            }

            if (result.contains("$coord")) {
               result = result.replace("$coord", "x: " + x + ", y: " + y + ", z: " + z);
            }

            if (result.contains("$paste")) {
               String clip = "";
               try {
                  clip = mc.keyboardHandler.getClipboard();
               } catch (Exception ignored) {}
               if (clip == null) clip = "";
               result = result.replace("$paste", clip);
            }

            if (result.contains("$x")) {
               result = result.replace("$x", String.valueOf(x));
            }

            if (result.contains("$y")) {
               result = result.replace("$y", String.valueOf(y));
            }

            if (result.contains("$z")) {
               result = result.replace("$z", String.valueOf(z));
            }

            if (result.toLowerCase().contains("!wiki hand")) {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               String sbId = "";
               if (held != null && !held.isEmpty()) {
                  sbId = getSkyblockId(held);
                  if (sbId == null || sbId.isEmpty()) {
                     sbId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().toUpperCase();
                  }
               }
               result = result.replaceAll("(?i)!wiki\\s+hand\\b", "!wiki " + (sbId != null ? sbId : ""));
            }

            if (result.contains("$show") || result.contains("[item]") || result.contains("$lore")) {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               if (held != null && !held.isEmpty()) {
                  String serialized = serializeItemForChat(held);
                  result = result.replace("$show", serialized).replace("[item]", serialized).replace("$lore", serialized);
               }
            }

            if (result.contains("$handid")) {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               String sbId = "";
               if (held != null && !held.isEmpty()) {
                  sbId = getSkyblockId(held);
                  if (sbId == null || sbId.isEmpty()) {
                     sbId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().toUpperCase();
                  }
               }
               result = result.replace("$handid", sbId != null ? sbId : "");
            }

            if (result.contains("$hand")) {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               String handName = "";
               if (held != null && !held.isEmpty()) {
                  handName = net.minecraft.ChatFormatting.stripFormatting(held.getHoverName().getString());
               }
               result = result.replace("$hand", handName != null ? handName : "");
            }

            if (result.contains("$look")) {
               String lookTarget = "";
               net.minecraft.world.phys.HitResult hit = mc.hitResult;
               if (hit instanceof net.minecraft.world.phys.BlockHitResult bHit) {
                  net.minecraft.core.BlockPos pos = bHit.getBlockPos();
                  if (mc.level != null && pos != null) {
                     lookTarget = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).getPath();
                  }
               } else if (hit instanceof net.minecraft.world.phys.EntityHitResult eHit) {
                  net.minecraft.world.entity.Entity entity = eHit.getEntity();
                  if (entity != null) {
                     lookTarget = entity.getName().getString();
                  }
               }
               result = result.replace("$look", lookTarget != null ? lookTarget : "");
            }

            if (result.contains("$random")) {
               java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$random(?:\\((\\d+)-(\\d+)\\))?");
               java.util.regex.Matcher matcher = pattern.matcher(result);
               StringBuilder sb = new StringBuilder();
               java.util.Random rnd = new java.util.Random();
               while (matcher.find()) {
                  int min = 0;
                  int max = 100;
                  if (matcher.group(1) != null && matcher.group(2) != null) {
                     try {
                        min = Integer.parseInt(matcher.group(1));
                        max = Integer.parseInt(matcher.group(2));
                     } catch (Exception ignored) {}
                  }
                  if (max < min) {
                     int t = min; min = max; max = t;
                  }
                  int val = min + rnd.nextInt(max - min + 1);
                  matcher.appendReplacement(sb, String.valueOf(val));
               }
               matcher.appendTail(sb);
               result = sb.toString();
            }

            return result;
         }
      } else {
         if (text != null && text.toLowerCase().contains("!wiki hand")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               String sbId = "";
               if (held != null && !held.isEmpty()) {
                  sbId = getSkyblockId(held);
                  if (sbId == null || sbId.isEmpty()) {
                     sbId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().toUpperCase();
                  }
               }
               return text.replaceAll("(?i)!wiki\\s+hand\\b", "!wiki " + (sbId != null ? sbId : ""));
            }
         }
         if (text != null && (text.contains("$show") || text.contains("[item]") || text.contains("$lore"))) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
               if (held != null && !held.isEmpty()) {
                  String serialized = serializeItemForChat(held);
                  return text.replace("$show", serialized).replace("[item]", serialized).replace("$lore", serialized);
               }
            }
         }
         return text;
      }
   }

   public static String getItemRarityColor(net.minecraft.world.item.ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return "§f";
      try {
         // 1. Check ExtraAttributes
         net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
         if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
               String tier = ea.getString("tier").orElse("");
               if (!tier.isEmpty()) {
                  return getRarityCode(tier);
               }
               // Check petInfo
               String petInfo = ea.getString("petInfo").orElse("");
               if (!petInfo.isEmpty() && petInfo.contains("\"tier\":\"")) {
                  int tIdx = petInfo.indexOf("\"tier\":\"");
                  int end = petInfo.indexOf("\"", tIdx + 8);
                  if (end != -1) {
                     return getRarityCode(petInfo.substring(tIdx + 8, end));
                  }
               }
            }
         }

         // 2. Scan lore for rarity footer (e.g. COMMON, RARE, LEGENDARY DUNGEON SWORD)
         net.minecraft.world.item.component.ItemLore itemLore = (net.minecraft.world.item.component.ItemLore)itemStack.get(net.minecraft.core.component.DataComponents.LORE);
         if (itemLore != null && !itemLore.lines().isEmpty()) {
            List<Component> lines = itemLore.lines();
            for (int i = lines.size() - 1; i >= 0; --i) {
               String str = lines.get(i).getString();
               String clean = str.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               if (clean.contains("COMMON") || clean.contains("UNCOMMON") || clean.contains("RARE") || clean.contains("EPIC") || clean.contains("LEGENDARY") || clean.contains("MYTHIC") || clean.contains("DIVINE") || clean.contains("SPECIAL") || clean.contains("VERY SPECIAL") || clean.contains("SUPREME")) {
                  if (clean.contains("VERY SPECIAL") || clean.contains("SPECIAL")) return "§c";
                  if (clean.contains("SUPREME") || clean.contains("DIVINE")) return "§b";
                  if (clean.contains("MYTHIC")) return "§d";
                  if (clean.contains("LEGENDARY")) return "§6";
                  if (clean.contains("EPIC")) return "§5";
                  if (clean.contains("RARE")) return "§9";
                  if (clean.contains("UNCOMMON")) return "§a";
                  if (clean.contains("COMMON")) return "§f";
               }
            }
         }
      } catch (Throwable ignored) {}
      return "§f";
   }

   public static String getRarityCode(String tier) {
      if (tier == null) return "§f";
      switch (tier.toUpperCase()) {
         case "UNCOMMON": return "§a";
         case "RARE": return "§9";
         case "EPIC": return "§5";
         case "LEGENDARY": return "§6";
         case "MYTHIC": return "§d";
         case "DIVINE":
         case "SUPREME": return "§b";
         case "SPECIAL":
         case "VERY_SPECIAL":
         case "VERY SPECIAL": return "§c";
         case "COMMON":
         default: return "§f";
      }
   }

   public static String serializeItemForChat(net.minecraft.world.item.ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return "";
      try {
         String formattedDisplayName = getFormattedComponentText(itemStack.getHoverName());
         String displayName = formattedDisplayName;
         if (displayName.isEmpty() || !displayName.contains("§")) {
            String color = getItemRarityColor(itemStack);
            displayName = color + itemStack.getHoverName().getString();
         }

         List<String> loreLines = new ArrayList<>();
         loreLines.add(displayName);
         net.minecraft.world.item.component.ItemLore itemLore = (net.minecraft.world.item.component.ItemLore)itemStack.get(net.minecraft.core.component.DataComponents.LORE);
         if (itemLore != null) {
            for (Component line : itemLore.lines()) {
               String formattedLine = getFormattedComponentText(line);
               if (formattedLine.isEmpty()) {
                  formattedLine = line.getString();
               }
               loreLines.add(formattedLine);
            }
         }
         String combined = String.join("\n", loreLines);
         String encoded = java.util.Base64.getEncoder().encodeToString(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
         return "[SHOW:" + displayName + ":" + encoded + "]";
      } catch (Throwable t) {
         return itemStack.getHoverName().getString();
      }
   }

   public static net.minecraft.nbt.CompoundTag getExtraAttributes(net.minecraft.world.item.ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return null;
      try {
         net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
         if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            if (tag.contains("ExtraAttributes")) {
               return tag.getCompound("ExtraAttributes").orElse(null);
            }
            return tag;
         }
      } catch (Throwable ignored) {}
      return null;
   }

   public static String getSkyblockId(net.minecraft.world.item.ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return "";
      try {
         net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
         if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            String id = tag.getString("id").orElse("");
            net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (id.isEmpty() && ea != null) {
               id = ea.getString("id").orElse("");
            }
            if (id.equals("FACTION_RABBIT") || tag.contains("faction_rabbit_id") || (ea != null && ea.contains("faction_rabbit_id"))) {
               String sub = tag.getString("faction_rabbit_id").orElse("");
               if (sub.isEmpty() && ea != null) sub = ea.getString("faction_rabbit_id").orElse("");
               if (!sub.isEmpty()) {
                  return "FACTION_RABBIT_" + sub.toUpperCase(java.util.Locale.ROOT);
               }
            }
            if (!id.isEmpty()) {
               return id;
            }
         }
      } catch (Throwable ignored) {}
      try {
         return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()).getPath().toUpperCase();
      } catch (Throwable ignored) {}
      return "";
   }

   public static String getStrictSkyblockId(net.minecraft.world.item.ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return "";
      try {
         net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
         if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            String id = tag.getString("id").orElse("");
            net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (id.isEmpty() && ea != null) {
               id = ea.getString("id").orElse("");
            }
            if (id.equals("FACTION_RABBIT") || tag.contains("faction_rabbit_id") || (ea != null && ea.contains("faction_rabbit_id"))) {
               String sub = tag.getString("faction_rabbit_id").orElse("");
               if (sub.isEmpty() && ea != null) sub = ea.getString("faction_rabbit_id").orElse("");
               if (!sub.isEmpty()) {
                  return "FACTION_RABBIT_" + sub.toUpperCase(java.util.Locale.ROOT);
               }
            }
            if (!id.isEmpty()) {
               return id;
            }
         }
      } catch (Throwable ignored) {}
      return "";
   }

   public static boolean isConnectedToHypixel() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.getCurrentServer() == null) {
         return false;
      } else {
         String ip = mc.getCurrentServer().ip.toLowerCase();
         return ip.contains("hypixel.net") || ip.contains("hypixel.io");
      }
   }

   private static long lastCacheTime = 0L;
   private static String cachedLocation = "Unknown";
   private static String cachedSubArea = "None";
   private static List<String> cachedSidebarLines = Collections.emptyList();

   private static void refreshLocationCacheIfNeeded() {
      long now = System.currentTimeMillis();
      if (now - lastCacheTime < 250L) {
         return;
      }
      lastCacheTime = now;

      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         cachedLocation = "Menu";
         cachedSubArea = "None";
         cachedSidebarLines = Collections.emptyList();
         return;
      }

      Scoreboard scoreboard = mc.level.getScoreboard();
      Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      if (sidebar != null) {
         List<String> rawLines = getSidebarLines(scoreboard, sidebar);
         List<String> clean = new ArrayList<>();
         for (String l : rawLines) {
            clean.add(l.replaceAll("(?i)§.", "").trim().toLowerCase());
         }
         cachedSidebarLines = clean;
      } else {
         cachedSidebarLines = Collections.emptyList();
      }

      cachedSubArea = internalGetSubArea();
      cachedLocation = internalGetLocation();
   }

   public static boolean matchesIslandRequirement(String reqIsland) {
      if (reqIsland == null || reqIsland.trim().isEmpty()) {
         return true;
      }

      refreshLocationCacheIfNeeded();

      String areaLower = (cachedLocation != null ? cachedLocation : "").toLowerCase();
      String subLower = (cachedSubArea != null ? cachedSubArea : "").toLowerCase();
      List<String> sbLines = cachedSidebarLines;

      String[] parts = reqIsland.split(",");
      List<String> positive = new ArrayList();
      List<String> negative = new ArrayList();

      for(String p : parts) {
         String t = p.trim().toLowerCase();
         if (!t.isEmpty()) {
            if (t.startsWith("!")) {
               String neg = t.substring(1).trim();
               if (!neg.isEmpty()) {
                  negative.add(neg);
               }
            } else {
               positive.add(t);
            }
         }
      }

      for(String neg : negative) {
         if (areaLower.contains(neg) || subLower.contains(neg)) {
            return false;
         }
         for(String sb : sbLines) {
            if (sb.contains(neg)) {
               return false;
            }
         }
      }

      if (positive.isEmpty()) {
         return true;
      }

      for(String pos : positive) {
         if (areaLower.contains(pos) || subLower.contains(pos)) {
            return true;
         }
         for(String sb : sbLines) {
            if (sb.contains(pos)) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean matchesArmorRequirement(String reqArmor) {
      if (reqArmor == null || reqArmor.trim().isEmpty()) {
         return true;
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      }

      String[] parts = reqArmor.split(",");
      List<String> positive = new ArrayList();
      List<String> negative = new ArrayList();

      for(String p : parts) {
         String t = p.trim().toLowerCase();
         if (!t.isEmpty()) {
            if (t.startsWith("!")) {
               String neg = t.substring(1).trim();
               if (!neg.isEmpty()) {
                  negative.add(neg);
               }
            } else {
               positive.add(t);
            }
         }
      }

      List<String> equippedArmorNames = new ArrayList();
      for(int i = 36; i <= 39; ++i) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (stack != null && !stack.isEmpty()) {
            equippedArmorNames.add(stack.getHoverName().getString().toLowerCase());
         }
      }

      for(String neg : negative) {
         for(String name : equippedArmorNames) {
            if (name.contains(neg)) {
               return false;
            }
         }
      }

      if (positive.isEmpty()) {
         return true;
      }

      for(String pos : positive) {
         for(String name : equippedArmorNames) {
            if (name.contains(pos)) {
               return true;
            }
         }
      }

      return false;
   }

   public static String mapLocrawToArea(String mode, String map) {
      if (map != null && !map.trim().isEmpty()) {
         return map.trim();
      }

      if (mode == null) {
         mode = "";
      }

      String lowerMode = mode.toLowerCase();
      if (!lowerMode.contains("dungeon_hub")) {
         if (!lowerMode.contains("kuudra")) {
            if (!lowerMode.contains("garden")) {
               if (!lowerMode.contains("hub")) {
                  if (!lowerMode.contains("island")) {
                     if (!lowerMode.contains("dungeon")) {
                        if (!lowerMode.contains("mines")) {
                           if (!lowerMode.contains("crystal_hollows")) {
                              if (!lowerMode.contains("crimson_isle")) {
                                 if (!lowerMode.contains("spider")) {
                                    if (!lowerMode.contains("end")) {
                                       if (!lowerMode.contains("park")) {
                                          if (!lowerMode.contains("caverns")) {
                                             if (!lowerMode.contains("gold")) {
                                                if (lowerMode.contains("torrhus") || lowerMode.contains("canyon") || lowerMode.contains("springs")) {
                                                   return "Torrhus Canyon";
                                                }
                                                if (!lowerMode.contains("farming") && !lowerMode.contains("barn") && !lowerMode.contains("desert") && !lowerMode.contains("mushroom")) {
                                                   if (!lowerMode.contains("rift")) {
                                                      if (!lowerMode.contains("jerry")) {
                                                         if (!lowerMode.contains("auction")) {
                                                            return "Unknown";
                                                         } else {
                                                            return "Dark Auction";
                                                         }
                                                      } else {
                                                         return "Jerry's Workshop";
                                                      }
                                                   } else {
                                                      return "The Rift";
                                                   }
                                                } else {
                                                   return "The Farming Islands";
                                                }
                                             } else {
                                                return "Gold Mine";
                                             }
                                          } else {
                                             return "Deep Caverns";
                                          }
                                       } else {
                                          return "The Park";
                                       }
                                    } else {
                                       return "The End";
                                    }
                                 } else {
                                    return "Spider's Den";
                                 }
                              } else {
                                 return "Crimson Isle";
                              }
                           } else {
                              return "Crystal Hollows";
                           }
                        } else {
                           return "Dwarven Mines";
                        }
                     } else {
                        return "Dungeons";
                     }
                  } else {
                     return "Private Island";
                  }
               } else {
                  return "The Hub";
               }
            } else {
               return "The Garden";
            }
         } else {
            return "Kuudra's Hollow";
         }
      } else {
         return "Dungeon Hub";
      }
   }

   public static String getLocation() {
      if (simulatedArea != null) {
         return simulatedArea;
      }
      refreshLocationCacheIfNeeded();
      return cachedLocation;
   }

   // -------------------------------------------------------------------------------------------
   // Dungeon floor + boss phase (v26.2.28.39)
   //
   // getLocation() intentionally buckets everything in the Catacombs into "Dungeons", which is
   // useless for a feature that needs to know it is in F4 vs M7. These helpers read the sidebar /
   // tab line "The Catacombs (F4)" or "Master Mode The Catacombs (M7)" instead.
   // -------------------------------------------------------------------------------------------

   /** Matches {@code The Catacombs (F4)} and {@code Master Mode The Catacombs (M7)} / {@code Floor VII}. */
   private static final Pattern DUNGEON_FLOOR_TAG = Pattern.compile(
           "(?i)catacombs\\s*(?:\\(\\s*([fm])?\\s*([0-9ivxlcdm]+)\\s*\\)|floor\\s*([0-9ivxlcdm]+))");

   /** The floor at the time boss mode was entered, so a new run starts back in clear phase. */
   private static volatile String dungeonBossFloor = null;
   private static volatile boolean dungeonBossActive = false;

   /**
    * "F4" / "M7" while the player is inside a Catacombs floor, otherwise {@code null}.
    * Returns the master-mode tag when the map is master mode.
    */
   public static String getDungeonFloorTag() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return null;
      }
      List<String> candidates = new ArrayList<>();
      try {
         Scoreboard scoreboard = mc.level.getScoreboard();
         Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
         if (sidebar != null) {
            candidates.addAll(getSidebarLines(scoreboard, sidebar));
         }
      } catch (Throwable ignored) {
      }
      try {
         for (Component line : getTabListLines()) {
            candidates.add(line.getString());
         }
      } catch (Throwable ignored) {
      }
      for (String raw : candidates) {
         String tag = parseDungeonFloorTag(raw);
         if (tag != null) {
            return tag;
         }
      }
      return null;
   }

   /** Parses a single sidebar/tab line into a floor tag ("F4", "M7") or {@code null}. */
   public static String parseDungeonFloorTag(String raw) {
      if (raw == null) return null;
      String clean = ChatFormatting.stripFormatting(raw);
      if (clean == null) return null;
      clean = clean.trim();
      if (clean.isEmpty()) return null;
      String lower = clean.toLowerCase(Locale.ROOT);
      if (!lower.contains("catacombs")) return null;

      Matcher matcher = DUNGEON_FLOOR_TAG.matcher(clean);
      if (!matcher.find()) return null;
      String letter = matcher.group(1);
      String number = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
      int floor = romanToFloor(number);
      if (floor <= 0) return null;
      boolean master = letter != null ? letter.equalsIgnoreCase("m") : lower.contains("master");
      return (master ? "M" : "F") + floor;
   }

   private static int romanToFloor(String raw) {
      if (raw == null || raw.isEmpty()) return -1;
      if (raw.matches("[0-9]+")) {
         try {
            return Integer.parseInt(raw);
         } catch (NumberFormatException e) {
            return -1;
         }
      }
      String upper = raw.toUpperCase(Locale.ROOT);
      java.util.Map<Character, Integer> values = java.util.Map.of(
              'I', 1, 'V', 5, 'X', 10, 'L', 50, 'C', 100, 'D', 500, 'M', 1000);
      int total = 0;
      for (int i = 0; i < upper.length(); i++) {
         Integer value = values.get(upper.charAt(i));
         if (value == null) return -1;
         if (i + 1 < upper.length()) {
            Integer next = values.get(upper.charAt(i + 1));
            if (next != null && next > value) {
               total -= value;
               continue;
            }
         }
         total += value;
      }
      return total > 0 && total <= 7 ? total : -1;
   }

   /** {@code "clear"} or {@code "boss"} inside a Catacombs floor, otherwise {@code null}. */
   public static String getDungeonPhase() {
      String floor = getDungeonFloorTag();
      if (floor == null) return null;
      if (dungeonBossFloor != null && !dungeonBossFloor.equals(floor)) {
         // Different floor than the run boss mode was entered on: treat it as a fresh run.
         dungeonBossActive = false;
         dungeonBossFloor = null;
      }
      return dungeonBossActive ? "boss" : "clear";
   }

   /**
    * Called by {@code DungeonBossManager} when a real floor boss announces itself. The Watcher
    * (and the blood room it lives in) deliberately does not count as a boss.
    */
   public static void setDungeonBossActive(boolean active) {
      dungeonBossActive = active;
      dungeonBossFloor = active ? getDungeonFloorTag() : null;
   }

   public static boolean isDungeonBossActive() {
      return dungeonBossActive;
   }

   /** True while the player is inside a Catacombs floor (clear or boss room). */
   public static boolean isInCatacombs() {
      String floor = getDungeonFloorTag();
      if (floor != null) return true;
      String area = getLocation();
      return area != null && (area.equalsIgnoreCase("Dungeons") || area.toLowerCase(Locale.ROOT).contains("catacombs"));
   }

   public static boolean isInLimbo() {
      return "limbo".equalsIgnoreCase(BomboaddonsClient.locrawServer) 
          || "LIMBO".equalsIgnoreCase(BomboaddonsClient.locrawGametype) 
          || "Limbo".equalsIgnoreCase(BomboaddonsClient.currentArea);
   }

   private static String internalGetLocation() {
      if (simulatedArea != null) {
         return simulatedArea;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return "Menu";
      } else if (isInLimbo()) {
         return "Limbo";
      } else {
         if (isConnectedToHypixel() && "SKYBLOCK".equals(BomboaddonsClient.locrawGametype)) {
            String area = mapLocrawToArea(BomboaddonsClient.locrawMode, BomboaddonsClient.locrawMap);
            if (!area.equals("Unknown")) {
               return area;
            }
         }

         String loc = "Unknown";
         Scoreboard scoreboard = mc.level.getScoreboard();
         Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
         if (sidebar != null) {
            List<String> sbLines = getSidebarLines(scoreboard, sidebar);
            loc = parseAreaFromLines(sbLines);
            if (loc.equals("Unknown")) {
               for(String line : sbLines) {
                  if (line.toLowerCase().contains("hypixel.net")) {
                     loc = "Lobby";
                     break;
                  }
               }
            }
         }

         if (loc.equals("Unknown") && mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList();

            for(Component c : tabLines) {
               plainTabLines.add(c.getString());
            }

            loc = parseAreaFromLines(plainTabLines);
            if (loc.equals("Unknown")) {
               PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor)mc.gui.hud.getTabList();
               Component header = tabAccessor.getHeader();
               Component footer = tabAccessor.getFooter();
               if (header != null) {
                  loc = parseAreaFromLines(List.of(header.getString()));
               }

               if (loc.equals("Unknown") && footer != null) {
                  loc = parseAreaFromLines(List.of(footer.getString()));
               }
            }
         }

         if (loc.equals("Unknown")) {
            String sub = internalGetSubArea();
            if (!sub.equals("None")) {
               String mapped = mapSubAreaToMainArea(sub);
               if (!mapped.equals("Unknown")) {
                  loc = mapped;
               }
            }
         }

         return loc;
      }
   }

   public static String getSubArea() {
      refreshLocationCacheIfNeeded();
      return cachedSubArea;
   }

   private static String internalGetSubArea() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         if (mc.gui.screen() != null) {
            String name = mc.gui.screen().getClass().getSimpleName();
            if (name.equals("JoinMultiplayerScreen") || name.equals("MultiplayerScreen")) {
               return "Multiplayer Menu";
            }

            if (name.equals("TitleScreen")) {
               return "Main Menu";
            }
         }

         return "None";
      } else {
         Scoreboard scoreboard = mc.level.getScoreboard();
         Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);

         // Check for Dungeon Floor 7 / Master 7 boss phases (p1..p5)
         if (mc.player != null) {
            double py = mc.player.getY();
            boolean isF7 = false;
            if (sidebar != null) {
               for (String l : cachedSidebarLines) {
                  if (l.contains("the catacombs") && (l.contains("(f7)") || l.contains("(m7)"))) {
                     isF7 = true;
                     break;
                  }
               }
            }
            if (isF7 || (cachedLocation != null && cachedLocation.equalsIgnoreCase("Dungeons") && py <= 240)) {
               if (py > 210) return "p1";
               else if (py > 155) return "p2";
               else if (py > 100) return "p3";
               else if (py > 45) return "p4";
               else if (py > 0) return "p5";
            }
         }

         // Check for Critter Safari biome
         if (me.bombo.bomboaddons.features.critters.SafariLocation.inSafari()) {
            me.bombo.bomboaddons.features.critters.SafariBiome sb = me.bombo.bomboaddons.features.critters.SafariLocation.biome();
            if (sb != null) {
               return sb.areaName();
            }
         }

         if (sidebar != null) {
            List<String> sbLines = getSidebarLines(scoreboard, sidebar);
            String sub = parseSubAreaFromLines(sbLines);
            if (!sub.equals("None")) {
               return sub;
            }
         }

         if (mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList();

            for(Component c : tabLines) {
               plainTabLines.add(c.getString());
            }

            String sub = parseSubAreaFromLines(plainTabLines);
            if (!sub.equals("None")) {
               return sub;
            }

            PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor)mc.gui.hud.getTabList();
            Component header = tabAccessor.getHeader();
            Component footer = tabAccessor.getFooter();
            if (header != null) {
               String s = parseSubAreaFromLines(List.of(header.getString()));
               if (!s.equals("None")) {
                  return s;
               }
            }

            if (footer != null) {
               String s = parseSubAreaFromLines(List.of(footer.getString()));
               if (!s.equals("None")) {
                  return s;
               }
            }
         }

         return "None";
      }
   }

   private static String parseSubAreaFromLines(List<String> lines) {
      // First check specifically for Plot - XX lines on Garden
      for(String line : lines) {
         String clean = line.replaceAll("(?i)§.", "").trim();
         if (clean.matches("(?i).*\\bPlot\\s*-\\s*\\d+.*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(Plot\\s*-\\s*\\d+)").matcher(clean);
            if (m.find()) {
               return m.group(1);
            }
         }
      }

      for(String line : lines) {
         String clean = line.replaceAll("(?i)§.", "").trim();
         if (clean.contains("The Catacombs (")) {
            int start = clean.indexOf("(") + 1;
            int end = clean.indexOf(")");
            if (start > 0 && end > start) {
               return clean.substring(start, end);
            }
         }

         // Scoreboard zone line e.g. "⏣ Village", "Village", "⏣ Farm", "⏣ Wilderness"
         if (clean.startsWith("⏏") || clean.startsWith("⏣") || clean.startsWith("ф") || clean.startsWith("📍") || clean.startsWith("\uD83D\uDCCD") || clean.startsWith("\uE067") || clean.startsWith("\uE000") || (clean.length() > 2 && clean.charAt(0) > 127 && !Character.isLetterOrDigit(clean.charAt(0)))) {
            String sub = clean.replaceFirst("^[⏏⏣ф📍\\uD83D\\uDCCD\\uE067\\uE000-\\uF8FF\\s]+", "").trim();
            // Ignore pest count lines like "x8" or "8x" or "Pests: 8"
            if (sub.matches("^[xX]?\\s*\\d+\\s*[xX]?$") || sub.toLowerCase().startsWith("pest")) {
               continue;
            }
            if (sub.startsWith("Area:") || sub.startsWith("Zone:")) {
               sub = sub.substring(sub.indexOf(":") + 1).trim();
            }
            if (sub.toLowerCase().contains("kuudra") && sub.contains("(T")) {
               int idx = sub.indexOf("(T");
               if (idx + 2 < sub.length()) {
                  char c = sub.charAt(idx + 2);
                  if (c >= '1' && c <= '5') {
                     return "T" + c;
                  }
               }
            }

            if (!sub.isEmpty()) {
               return sub;
            }
         }

         if (clean.startsWith("Area:") || clean.startsWith("Zone:")) {
            String sub = clean.substring(clean.indexOf(":") + 1).trim();
            if (!sub.isEmpty()) {
               return sub;
            }
         }
      }

      return "None";
   }

   private static String parseAreaFromLines(List<String> lines) {
      for(String line : lines) {
         String clean = line.replaceAll("(?i)§.", "").trim();
         if (!clean.contains("Area:") && !clean.contains("Zone:")) {
            String lower = clean.toLowerCase();
            if (lower.contains("dungeon hub")) {
               return "Dungeon Hub";
            }

            if (!lower.contains("kuudra's hollow") && !lower.contains("kuudra")) {
               if (!lower.contains("the garden") && !lower.contains("garden")) {
                  if (!lower.contains("the hub") && !lower.contains("hub")) {
                     if (!lower.contains("private island") && !lower.contains("island")) {
                        if (!lower.contains("catacombs") && !lower.contains("dungeon")) {
                           if (lower.contains("dwarven mines")) {
                              return "Dwarven Mines";
                           }

                           if (lower.contains("crystal hollows")) {
                              return "Crystal Hollows";
                           }

                           if (lower.contains("crimson isle")) {
                              return "Crimson Isle";
                           }

                           if (lower.contains("spider's den")) {
                              return "Spider's Den";
                           }

                           if (lower.contains("the end")) {
                              return "The End";
                           }

                           if (lower.contains("the park")) {
                              return "The Park";
                           }

                           if (lower.contains("deep caverns")) {
                              return "Deep Caverns";
                           }

                           if (lower.contains("gold mine")) {
                              return "Gold Mine";
                           }

                           if (!lower.contains("farming") && !lower.contains("the barn") && !lower.contains("barn") && !lower.contains("mushroom desert") && !lower.contains("desert")) {
                              if (!lower.contains("the rift") && !lower.contains("rift")) {
                                 if (!lower.contains("jerry's workshop") && !lower.contains("jerry")) {
                                    if (!lower.contains("dark auction") && !lower.contains("auction")) {
                                       if (lower.contains("limbo")) {
                                          return "Limbo";
                                       }

                                       if (lower.contains("lobby")) {
                                          return "Lobby";
                                       }
                                       continue;
                                    }

                                    return "Dark Auction";
                                 }

                                 return "Jerry's Workshop";
                              }

                              return "The Rift";
                           }

                           return "Farming Islands";
                        }

                        return "Dungeons";
                     }

                     return "Private Island";
                  }

                  return "The Hub";
               }

               return "The Garden";
            }

            return "Kuudra's Hollow";
         }

         return clean.substring(clean.indexOf(":") + 1).trim();
      }

      return "Unknown";
   }

   public static String mapSubAreaToMainArea(String sub) {
      if (sub != null && !sub.isEmpty() && !sub.equalsIgnoreCase("None")) {
         String lower = sub.toLowerCase();
         if (lower.contains("torrhus") || lower.contains("springs") || lower.contains("heights") || lower.contains("canyon") || lower.contains("galatea")) {
            return "Torrhus Canyon";
         }
         if (!lower.contains("catacombs") && !lower.contains("dungeon") && !lower.matches("^(f[1-7]|m[1-7])$")) {
            if (!lower.matches("^(t[1-5])$") && !lower.contains("kuudra")) {
               if (!lower.contains("barn") && !lower.contains("desert") && !lower.contains("mushroom") && !lower.contains("oasis") && !lower.contains("windmill") && !lower.contains("trevor") && !lower.contains("shepherd") && !lower.contains("jake")) {
                  if (!lower.contains("dwarven") && !lower.contains("forge") && !lower.contains("goblin") && !lower.contains("royal") && !lower.contains("palace") && !lower.contains("cliffside") && !lower.contains("copcoils") && !lower.contains("rampart") && !lower.contains("aristocrat") && !lower.contains("hanging court")) {
                     if (!lower.contains("hollows") && !lower.contains("nucleus") && !lower.contains("precursor") && !lower.contains("khazad") && !lower.contains("grotto") && !lower.contains("mithril deposits")) {
                        if (!lower.contains("cavern") && !lower.contains("deep") && !lower.contains("gunpowder") && !lower.contains("lapis") && !lower.contains("pigman") && !lower.contains("slimehill") && !lower.contains("obsidian") && !lower.contains("diamond reserve")) {
                           if (lower.contains("gold")) {
                              return "Gold Mine";
                           } else if (!lower.contains("park") && !lower.contains("spruce") && !lower.contains("birch") && !lower.contains("savanna") && !lower.contains("howling") && !lower.contains("melancholy") && !lower.contains("dark thicket")) {
                              if (!lower.contains("spider") && !lower.contains("den") && !lower.contains("arachne") && !lower.contains("archaeologist")) {
                                 if (!lower.contains("end") && !lower.contains("nest") && !lower.contains("sepulture") && !lower.contains("zealot")) {
                                    if (!lower.contains("crimson") && !lower.contains("isle") && !lower.contains("scarleton") && !lower.contains("dragontail") && !lower.contains("ashfang") && !lower.contains("lest") && !lower.contains("marsh") && !lower.contains("bastion") && !lower.contains("smoldering") && !lower.contains("aurea")) {
                                       if (!lower.contains("rift") && !lower.contains("wyld") && !lower.contains("lagoon") && !lower.contains("colosseum reborn") && !lower.contains("dreadfarm") && !lower.contains("westbridge") && !lower.contains("otherside") && !lower.contains("mirror") && !lower.contains("gallery") && !lower.contains("village plaza")) {
                                          if (lower.contains("jerry")) {
                                             return "Jerry's Workshop";
                                          } else if (lower.contains("garden") || lower.contains("plot")) {
                                             return "The Garden";
                                          } else if (!lower.contains("island")) {
                                             return !lower.contains("village") && !lower.contains("ruins") && !lower.contains("high level") && !lower.contains("forest") && !lower.contains("mountain") && !lower.contains("wilderness") && !lower.contains("graveyard") && !lower.contains("coal") && !lower.contains("bazaar") && !lower.contains("community center") && !lower.contains("farm") && !lower.contains("hut") && !lower.contains("canvas") && !lower.contains("carnival") && !lower.contains("colosseum") && !lower.contains("election") && !lower.contains("blacksmith") && !lower.contains("auction") && !lower.contains("bank") && !lower.contains("abiphone") && !lower.contains("library") && !lower.contains("thaumaturgist") && !lower.contains("sewer") && !lower.contains("museum") && !lower.contains("taylor") && !lower.contains("seymour") && !lower.contains("shen") && !lower.contains("elise") && !lower.contains("wizard") && !lower.contains("flower house") ? "Unknown" : "The Hub";
                                          } else {
                                             return "Private Island";
                                          }
                                       } else {
                                          return "The Rift";
                                       }
                                    } else {
                                       return "Crimson Isle";
                                    }
                                 } else {
                                    return "The End";
                                 }
                              } else {
                                 return "Spider's Den";
                              }
                           } else {
                              return "The Park";
                           }
                        } else {
                           return "Deep Caverns";
                        }
                     } else {
                        return "Crystal Hollows";
                     }
                  } else {
                     return "Dwarven Mines";
                  }
               } else {
                  return "Farming Islands";
               }
            } else {
               return "Kuudra's Hollow";
            }
         } else {
            return "Dungeons";
         }
      } else {
         return "Unknown";
      }
   }

   public static boolean isInGarden() {
      String loc = getLocation();
      if (loc.equalsIgnoreCase("The Garden") || loc.toLowerCase().contains("garden") || loc.toLowerCase().contains("plot")) {
         return true;
      }
      String current = BomboaddonsClient.currentArea;
      if (current != null && (current.toLowerCase().contains("garden") || current.toLowerCase().contains("plot"))) {
         return true;
      }
      return false;
   }

   public static boolean isInDungeon() {
      String loc = getLocation();
      if (loc.equalsIgnoreCase("Dungeons") || loc.toLowerCase().contains("catacombs")) {
         return true;
      }
      String current = BomboaddonsClient.currentArea;
      if (current != null && (current.equalsIgnoreCase("Dungeons") || current.toLowerCase().contains("catacombs"))) {
         return true;
      }
      String sub = getSubArea();
      if (sub != null && sub.matches("^(?i)(f[1-7]|m[1-7]|p[1-5])$")) {
         return true;
      }
      return false;
   }

   public static String getCurrentLocation() {
      return getLocation();
   }

   public static String getSubarea() {
      return getSubArea();
   }

   public static String getDungeonClass() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return "";
      String myName = mc.getUser().getName();
      for (Component c : getTabListLines()) {
         String str = c.getString();
         if (str.contains(myName)) {
            for (String cls : List.of("Healer", "Mage", "Berserk", "Archer", "Tank")) {
               if (str.toLowerCase().contains(cls.toLowerCase())) {
                  return cls;
               }
            }
         }
      }
      return "";
   }

   public static List<String> getSidebarLines(Scoreboard scoreboard, Objective objective) {
      List<String> lines = new ArrayList();
      scoreboard.listPlayerScores(objective).forEach((score) -> {
         String ownerName = score.owner();
         PlayerTeam team = scoreboard.getPlayersTeam(ownerName);
         if (team != null) {
            Component fullName = Component.empty().append(team.getPlayerPrefix()).append(Component.literal(ownerName)).append(team.getPlayerSuffix());
            lines.add(fullName.getString());
         } else {
            lines.add(ownerName);
         }

      });
      Collections.reverse(lines);
      return lines;
   }

   public static List<Component> getTabListLines() {
      Minecraft mc = Minecraft.getInstance();
      List<Component> lines = new ArrayList();
      if (mc.getConnection() == null) {
         return lines;
      } else {
         Comparator<PlayerInfo> ENTRY_ORDERING = Comparator.comparingInt((PlayerInfo p) -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0).thenComparing((PlayerInfo p) -> p.getProfile().name().toLowerCase());
         List<PlayerInfo> players = new ArrayList(mc.getConnection().getOnlinePlayers());
         players.sort(ENTRY_ORDERING);

         for(PlayerInfo info : players) {
            Component displayName = info.getTabListDisplayName();
            if (displayName != null) {
               lines.add(displayName);
            } else if (info.getProfile() != null && info.getProfile().name() != null) {
               lines.add(Component.literal(info.getProfile().name()));
            }
         }

         return lines;
      }
   }

   public static String getFormattedComponentText(Component comp) {
      if (comp == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();
         appendFormattedText(comp, sb);
         return sb.toString();
      }
   }

   private static void appendFormattedText(Component comp, StringBuilder sb) {
      if (comp != null) {
         Style style = comp.getStyle();
         if (style != null && style.getColor() != null) {
            int rgb = style.getColor().getValue();
            String code = getClosestColorCode(rgb);
            sb.append("§").append(code);
         }

         if (style != null && style.isBold()) {
            sb.append("§l");
         }

         if (style != null && style.isItalic()) {
            sb.append("§o");
         }

         if (style != null && style.isUnderlined()) {
            sb.append("§n");
         }

         if (style != null && style.isStrikethrough()) {
            sb.append("§m");
         }

         if (style != null && style.isObfuscated()) {
            sb.append("§k");
         }

         ComponentContents var7 = comp.getContents();
         if (!(var7 instanceof PlainTextContents)) {
            sb.append(comp.getString());
         } else {
            PlainTextContents plain = (PlainTextContents)var7;
            sb.append(plain.text());

            for(Component child : comp.getSiblings()) {
               appendFormattedText(child, sb);
            }

         }
      }
   }

   private static String getClosestColorCode(int rgb) {
      int r = rgb >> 16 & 255;
      int g = rgb >> 8 & 255;
      int b = rgb & 255;
      int[] colors = new int[]{0, 170, 43520, 43690, 11141120, 11141290, 16755200, 11184810, 5592405, 5592575, 5635925, 5636095, 16733525, 16733695, 16777045, 16777215};
      char[] codes = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
      double minDist = Double.MAX_VALUE;
      char bestCode = 'f';

      for(int i = 0; i < colors.length; ++i) {
         int cr = colors[i] >> 16 & 255;
         int cg = colors[i] >> 8 & 255;
         int cb = colors[i] & 255;
         double dist = Math.pow((double)(r - cr), (double)2.0F) + Math.pow((double)(g - cg), (double)2.0F) + Math.pow((double)(b - cb), (double)2.0F);
         if (dist < minDist) {
            minDist = dist;
            bestCode = codes[i];
         }
      }

      return String.valueOf(bestCode);
   }

   public static String getInternalId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         if (stack.getItem().toString().contains("enchanted_book")) {
            ItemEnchantments enchants = (ItemEnchantments)stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (enchants == null) {
               enchants = (ItemEnchantments)stack.get(DataComponents.ENCHANTMENTS);
            }

            if (enchants != null && !enchants.isEmpty()) {
               for(Holder<Enchantment> holder : enchants.keySet()) {
                  int level = enchants.getLevel(holder);
                  String path = (String)holder.unwrapKey().map((key) -> key.identifier().getPath()).orElse("");
                  if (!path.isEmpty()) {
                     String var10000 = path.toUpperCase();
                     return "ENCHANTMENT_" + var10000 + "_" + level;
                  }
               }
            }
         }

         CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag searchTag = tag;
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
               searchTag = ea;
            }

            String id = searchTag.getString("id").orElse("");
            if (id.equals("ENCHANTED_BOOK")) {
               CompoundTag enchants = searchTag.getCompound("enchantments").orElse(null);
               if (enchants == null) {
                  enchants = searchTag.getCompound("enchantment").orElse(null);
               }

               if (enchants != null && !enchants.keySet().isEmpty()) {
                  String name = (String)enchants.keySet().iterator().next();
                  int level = (Integer)enchants.getInt(name).orElse(1);
                  String var19 = name.toUpperCase();
                  return "ENCHANTMENT_" + var19 + "_" + level;
               }
            }

            if (id.equals("PET")) {
               String petInfoStr = searchTag.getString("petInfo").orElse("");
               if (!petInfoStr.isEmpty()) {
                  try {
                     JsonObject petObj = JsonParser.parseString(petInfoStr).getAsJsonObject();
                     if (petObj.has("type") && petObj.has("tier")) {
                        String type = petObj.get("type").getAsString();
                        String tier = petObj.get("tier").getAsString();
                        return "PET-" + type + "-" + tier;
                     }
                  } catch (Exception var10) {
                  }
               }
            }

            if (id.equals("FACTION_RABBIT") || searchTag.contains("faction_rabbit_id") || tag.contains("faction_rabbit_id")) {
               String sub = searchTag.getString("faction_rabbit_id").orElse("");
               if (sub.isEmpty()) sub = tag.getString("faction_rabbit_id").orElse("");
               if (!sub.isEmpty()) {
                  return "FACTION_RABBIT_" + sub.toUpperCase(java.util.Locale.ROOT);
               }
            }

            return !id.isEmpty() ? id : tag.getString("id").orElse("");
         } else {
            return "";
         }
      } else {
         return "";
      }
   }

   public static String getInternalIdRaw(ItemStack stack) {
      if (stack == null) {
         return "";
      } else {
         CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
         if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            String id = ea != null ? ea.getString("id").orElse("") : tag.getString("id").orElse("");
            if (id.equals("FACTION_RABBIT") || (ea != null && ea.contains("faction_rabbit_id")) || tag.contains("faction_rabbit_id")) {
               String sub = ea != null ? ea.getString("faction_rabbit_id").orElse("") : "";
               if (sub.isEmpty()) sub = tag.getString("faction_rabbit_id").orElse("");
               if (!sub.isEmpty()) {
                  return "FACTION_RABBIT_" + sub.toUpperCase(java.util.Locale.ROOT);
               }
            }
            return id;
         } else {
            return "";
         }
      }
   }

   public static String getInternalIdRaw(DataComponentMap map) {
      if (map == null) {
         return "";
      } else {
         CustomData customData = (CustomData)map.get(DataComponents.CUSTOM_DATA);
         if (customData != null) {
            CompoundTag tag = customData.copyTag();
            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            String id = ea != null ? ea.getString("id").orElse("") : tag.getString("id").orElse("");
            if (id.equals("FACTION_RABBIT") || (ea != null && ea.contains("faction_rabbit_id")) || tag.contains("faction_rabbit_id")) {
               String sub = ea != null ? ea.getString("faction_rabbit_id").orElse("") : "";
               if (sub.isEmpty()) sub = tag.getString("faction_rabbit_id").orElse("");
               if (!sub.isEmpty()) {
                  return "FACTION_RABBIT_" + sub.toUpperCase(java.util.Locale.ROOT);
               }
            }
            return id;
         } else {
            return "";
         }
      }
   }

   public static List<Component> getLore(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
         return lore != null ? lore.lines() : Collections.emptyList();
      } else {
         return Collections.emptyList();
      }
   }

   public static boolean isOnSkyblock() {
      return isConnectedToHypixel() && 
         ("SKYBLOCK".equalsIgnoreCase(BomboaddonsClient.locrawGametype) || matchesIslandRequirement("skyblock"));
   }

   public static boolean isInSkyblock() {
      return isOnSkyblock();
   }

   public static long getSkyblockDay() {
      // Skyblock Year 1 Day 1 started June 11, 2019 at 17:55:00 UTC (1560275700000 ms)
      // 1 Skyblock day = 20 minutes = 1,200,000 ms
      long epochMs = 1560275700000L;
      long now = System.currentTimeMillis();
      if (now < epochMs) return 1L;
      return (now - epochMs) / 1200000L + 1L;
   }
}
