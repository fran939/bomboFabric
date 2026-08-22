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
import me.bombo.bomboaddons.mixin.PlayerTabOverlayAccessor;
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

            return result;
         }
      } else {
         return text;
      }
   }

   public static String getSkyblockId(net.minecraft.world.item.ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return "";
      try {
         net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)itemStack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
         if (customData != null) {
            net.minecraft.nbt.CompoundTag tag = customData.copyTag();
            String id = tag.getString("id").orElse("");
            if (!id.isEmpty()) {
               return id;
            }

            net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
               return ea.getString("id").orElse("");
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
            if (!id.isEmpty()) {
               return id;
            }

            net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea != null) {
               return ea.getString("id").orElse("");
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
      refreshLocationCacheIfNeeded();
      return cachedLocation;
   }

   private static String internalGetLocation() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return "Menu";
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
         } else if (mc.getConnection() != null) {
            return "Limbo";
         }

         if (loc.equals("Unknown") && mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList();

            for(Component c : tabLines) {
               plainTabLines.add(c.getString());
            }

            loc = parseAreaFromLines(plainTabLines);
            if (loc.equals("Unknown")) {
               PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor)mc.gui.getTabList();
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
         if (mc.screen != null) {
            String name = mc.screen.getClass().getSimpleName();
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

            PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor)mc.gui.getTabList();
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
      for(String line : lines) {
         String clean = line.replaceAll("(?i)§.", "").trim();
         if (clean.contains("The Catacombs (")) {
            int start = clean.indexOf("(") + 1;
            int end = clean.indexOf(")");
            if (start > 0 && end > start) {
               return clean.substring(start, end);
            }
         }

         if (clean.startsWith("⏏") || clean.startsWith("⏣") || clean.startsWith("ф") || clean.startsWith("📍") || clean.startsWith("\uD83D\uDCCD") || clean.startsWith("\uE067") || clean.startsWith("\uE000") || (clean.length() > 2 && clean.charAt(0) > 127 && !Character.isLetterOrDigit(clean.charAt(0)))) {
            String sub = clean.replaceFirst("^[⏏⏣ф📍\\uD83D\\uDCCD\\uE067\\uE000-\\uF8FF\\s]+", "").trim();
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
                                          } else if (!lower.contains("island") && !lower.contains("plot")) {
                                             if (lower.contains("garden")) {
                                                return "The Garden";
                                             } else {
                                                return !lower.contains("village") && !lower.contains("ruins") && !lower.contains("high level") && !lower.contains("forest") && !lower.contains("mountain") && !lower.contains("wilderness") && !lower.contains("graveyard") && !lower.contains("coal") && !lower.contains("bazaar") && !lower.contains("community center") && !lower.contains("farm") && !lower.contains("hut") && !lower.contains("canvas") && !lower.contains("carnival") && !lower.contains("colosseum") && !lower.contains("election") && !lower.contains("blacksmith") && !lower.contains("auction") && !lower.contains("bank") && !lower.contains("abiphone") && !lower.contains("library") && !lower.contains("thaumaturgist") && !lower.contains("sewer") && !lower.contains("museum") && !lower.contains("taylor") && !lower.contains("seymour") && !lower.contains("shen") && !lower.contains("elise") && !lower.contains("wizard") && !lower.contains("flower house") ? "Unknown" : "The Hub";
                                             }
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
      return loc.equalsIgnoreCase("The Garden") || loc.toLowerCase().contains("garden");
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
            return ea != null ? ea.getString("id").orElse("") : tag.getString("id").orElse("");
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
            return ea != null ? ea.getString("id").orElse("") : tag.getString("id").orElse("");
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
}
