package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class ExperimentationTableHud {
   private static final Map<String, DetectedRngItem> storedRewards = new ConcurrentHashMap();
   private static int scrollIndex = 0;
   private static final Path RNG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons_rng.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static long lastRenderFrame = -1L;
   private static boolean lastInMenu = false;
   private static long lastScanTime = 0L;

   private static void loadRngFromFile() {
      try {
         if (Files.exists(RNG_FILE, new LinkOption[0])) {
            Reader reader = Files.newBufferedReader(RNG_FILE);

            try {
               RngDataState state = (RngDataState)GSON.fromJson(reader, RngDataState.class);
               if (state != null) {
                  if (state.rewards != null) {
                     storedRewards.putAll(state.rewards);
                  }

                  lastScanTime = state.lastScanTime;
               }
            } catch (Throwable var4) {
               if (reader != null) {
                  try {
                     reader.close();
                  } catch (Throwable var3) {
                     var4.addSuppressed(var3);
                  }
               }

               throw var4;
            }

            if (reader != null) {
               reader.close();
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   private static void saveRngToFile() {
      try {
         if (!Files.exists(RNG_FILE.getParent(), new LinkOption[0])) {
            Files.createDirectories(RNG_FILE.getParent());
         }

         Writer writer = Files.newBufferedWriter(RNG_FILE);

         try {
            RngDataState state = new RngDataState();
            state.lastScanTime = lastScanTime;
            state.rewards = storedRewards;
            GSON.toJson(state, writer);
         } catch (Throwable var4) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }
            }

            throw var4;
         }

         if (writer != null) {
            writer.close();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public static void onHudRender(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      long currentFrame = System.currentTimeMillis();
      if (currentFrame != lastRenderFrame || mc.screen instanceof HudMoveScreen) {
         lastRenderFrame = currentFrame;
         BomboConfig.Settings s = BomboConfig.get();
         if (s.rngProfitHud) {
            LowestBinManager.ensureLoaded();
            boolean inMenu = false;
            Screen var7 = mc.screen;
            if (var7 instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> screen = (AbstractContainerScreen)var7;
               String title = screen.getTitle().getString();
               if (title.toLowerCase().contains("experimentation table rng")) {
                  inMenu = true;
                  if (!lastInMenu) {
                     if (s.debugMaster) {
                        Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Experimentation Table RNG GUI detected: §f" + title);
                     }

                     long now = System.currentTimeMillis();
                     if (lastScanTime > 0L && now - lastScanTime > 43200000L) {
                        storedRewards.clear();
                        saveRngToFile();
                        scrollIndex = 0;
                        if (s.debugMaster) {
                           Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Cleared stored RNG rewards (New daily experiment/12h timeout).");
                        }
                     }
                  }

                  if (currentFrame - lastScanTime > 250L) {
                     scanMenu(screen);
                     lastScanTime = currentFrame;
                     saveRngToFile();
                  }
               }
            }

            lastInMenu = inMenu;
            if (inMenu || mc.screen instanceof HudMoveScreen) {
               if (mc.screen instanceof HudMoveScreen && storedRewards.isEmpty()) {
                  storedRewards.put("ENCHANTMENT_LOOTING_5", new DetectedRngItem("ENCHANTMENT_LOOTING_5", "Looting V", 500000));
                  storedRewards.put("ENCHANTMENT_GROWTH_6", new DetectedRngItem("ENCHANTMENT_GROWTH_6", "Growth VI", 150000));
                  storedRewards.put("ENCHANTMENT_GIANT_KILLER_7", new DetectedRngItem("ENCHANTMENT_GIANT_KILLER_7", "Giant Killer VII", 500000));
                  storedRewards.put("ENCHANTMENT_CRITICAL_7", new DetectedRngItem("ENCHANTMENT_CRITICAL_7", "Critical VII", 500000));
                  storedRewards.put("ENCHANTMENT_SHARPNESS_7", new DetectedRngItem("ENCHANTMENT_SHARPNESS_7", "Sharpness VII", 500000));
                  storedRewards.put("ENCHANTMENT_POWER_7", new DetectedRngItem("ENCHANTMENT_POWER_7", "Power VII", 500000));
                  storedRewards.put("ENCHANTMENT_PROTECTION_7", new DetectedRngItem("ENCHANTMENT_PROTECTION_7", "Protection VII", 500000));
                  storedRewards.put("ENCHANTMENT_CUBISM_6", new DetectedRngItem("ENCHANTMENT_CUBISM_6", "Cubism VI", 150000));
                  storedRewards.put("ENCHANTMENT_PROSECUTE_6", new DetectedRngItem("ENCHANTMENT_PROSECUTE_6", "Prosecute VI", 150000));
               }

               if (!storedRewards.isEmpty()) {
                  List<DetectedRngItem> toDraw = new ArrayList();

                  for(DetectedRngItem item : storedRewards.values()) {
                     long buyPrice = LowestBinManager.getBuyPrice(item.id);
                     long sellPrice = LowestBinManager.getSellPrice(item.id);
                     if (buyPrice > 0L || sellPrice > 0L) {
                        toDraw.add(item);
                     }
                  }

                  if (!toDraw.isEmpty()) {
                     toDraw.sort((a, b) -> {
                        long pA = LowestBinManager.getSellPrice(a.id);
                        long pB = LowestBinManager.getSellPrice(b.id);
                        double cpmA = a.metterCost > 0 ? (double)pA / (double)a.metterCost : (double)0.0F;
                        double cpmB = b.metterCost > 0 ? (double)pB / (double)b.metterCost : (double)0.0F;
                        return Double.compare(cpmB, cpmA);
                     });
                     drawRngInfo(g, s.rngProfitHudX, s.rngProfitHudY, toDraw);
                  }
               }
            }
         }
      }
   }

   private static void scanMenu(AbstractContainerScreen<?> screen) {
      AbstractContainerMenu menu = screen.getMenu();
      boolean changed = false;

      for(int i = 0; i < 54 && i < menu.slots.size(); ++i) {
         Slot slot = menu.getSlot(i);
         if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            int row = i / 9;
            int col = i % 9;
            if (row >= 1 && row <= 4 && col >= 1 && col <= 7) {
               String name = stack.getHoverName().getString();
               String cleanName = name.replaceAll("(?i)§.", "").trim();
               if (!cleanName.isEmpty() && !cleanName.equalsIgnoreCase("Go Back") && !cleanName.equalsIgnoreCase("Close") && !cleanName.contains("Page")) {
                  String rarity = "COMMON";
                  ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
                  if (lore != null) {
                     for(Component line : lore.lines()) {
                        String lineStr = line.getString().toUpperCase();
                        if (lineStr.contains("RARE")) {
                           rarity = "RARE";
                        } else if (lineStr.contains("EPIC")) {
                           rarity = "EPIC";
                        } else if (lineStr.contains("LEGENDARY")) {
                           rarity = "LEGENDARY";
                        } else if (lineStr.contains("MYTHIC")) {
                           rarity = "MYTHIC";
                        } else if (lineStr.contains("UNCOMMON")) {
                           rarity = "UNCOMMON";
                        }
                     }
                  }

                  String id = getSkyblockIdFromName(cleanName, rarity);
                  if (!id.isEmpty() && !id.contains("GLASS_PANE") && !id.equals("BARRIER")) {
                     int metterCost = 1;
                     if (lore != null) {
                        for(Component line : lore.lines()) {
                           String lineStr = line.getString().replaceAll("(?i)§.", "").trim();
                           if (lineStr.contains("Experimental XP:") && lineStr.contains("/")) {
                              String maxStr = lineStr.substring(lineStr.indexOf(47) + 1).replaceAll("[^0-9]", "");
                              if (!maxStr.isEmpty()) {
                                 try {
                                    metterCost = Integer.parseInt(maxStr);
                                 } catch (NumberFormatException var19) {
                                 }
                              }
                              break;
                           }
                        }
                     }

                     if (metterCost <= 1) {
                        metterCost = stack.getCount();
                        if (metterCost <= 0) {
                           metterCost = 1;
                        }
                     }

                     DetectedRngItem existing = (DetectedRngItem)storedRewards.get(id);
                     if (existing == null || existing.metterCost != metterCost || !existing.name.equals(cleanName)) {
                        storedRewards.put(id, new DetectedRngItem(id, cleanName, metterCost));
                        changed = true;
                     }
                  }
               }
            }
         }
      }

      if (changed) {
         saveRngToFile();
      }

   }

   public static void scroll(int delta) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.rngProfitHud && !storedRewards.isEmpty()) {
         int total = storedRewards.size();
         if (total <= 8) {
            scrollIndex = 0;
         } else {
            if (delta > 0) {
               scrollIndex = Math.max(0, scrollIndex - 1);
            } else if (delta < 0) {
               int maxScroll = total - 1 - 7;
               scrollIndex = Math.min(maxScroll, scrollIndex + 1);
            }

         }
      }
   }

   public static int getHudHeight() {
      int hasPriceCount = 0;

      for(DetectedRngItem item : storedRewards.values()) {
         long buyPrice = LowestBinManager.getBuyPrice(item.id);
         long sellPrice = LowestBinManager.getSellPrice(item.id);
         if (buyPrice > 0L || sellPrice > 0L) {
            ++hasPriceCount;
         }
      }

      if (hasPriceCount == 0) {
         return 30;
      } else {
         int shownCount = Math.min(Math.max(0, hasPriceCount - 1), 7);
         return 40 + (shownCount > 0 ? 4 : 0) + shownCount * 10;
      }
   }

   public static void drawRngInfo(GuiGraphicsExtractor g, int x, int y, List<DetectedRngItem> items) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      int width = 185;
      int shownCount = Math.min(items.size() - 1, 7);
      int totalEnchantsShown = 1 + shownCount;
      int height = 40 + (shownCount > 0 ? 4 : 0) + shownCount * 10;
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = BomboConfig.get().rngProfitHudScale;
      g.pose().scale(scale, scale);
      int opacity = BomboConfig.get().rngProfitHudOpacity;
      int alpha = (int)((double)opacity * 2.55);
      int bgColor = alpha << 24 | 0;
      if (opacity > 0) {
         g.fill(-5, -5, width + 5, height + 5, bgColor);
         g.outline(-5, -5, width + 10, height + 10, -1);
      }

      g.text(font, "§6§lRNG Experiments Profit", 0, 0, -1, true);
      g.fill(0, 11, width, 12, -1426063361);
      int curY = 18;
      if (items.isEmpty()) {
         g.text(font, "§7No items detected", 0, curY, -1, true);
         g.pose().popMatrix();
      } else {
         g.text(font, "§eMost profit:", 0, curY, -1, true);
         curY += 10;
         DetectedRngItem bestItem = (DetectedRngItem)items.get(0);
         long buyPriceBest = LowestBinManager.getBuyPrice(bestItem.id);
         long sellPriceBest = LowestBinManager.getSellPrice(bestItem.id);
         String bestDisplayName = getPrettyName(bestItem.id, bestItem.name);
         String var10000 = bestDisplayName.length() > 16 ? bestDisplayName.substring(0, 14) + ".." : bestDisplayName;
         String bestNameText = "§a§l" + var10000;
         String bestValueText;
         if (buyPriceBest <= 0L && sellPriceBest <= 0L) {
            bestValueText = "§8N/A";
         } else {
            String buyFormatted = formatPriceShort(buyPriceBest);
            String sellFormatted = formatPriceShort(sellPriceBest);
            bestValueText = "§a" + buyFormatted + "/" + sellFormatted;
         }

         g.text(font, bestNameText, 5, curY, -1, true);
         int bestValueWidth = font.width(bestValueText.replaceAll("(?i)§.", ""));
         g.text(font, bestValueText, width - bestValueWidth - 5, curY, -1, true);
         curY += 12;
         if (shownCount > 0) {
            g.fill(5, curY, width - 5, curY + 1, 1157627903);
            curY += 4;
         }

         int maxScroll = Math.max(0, items.size() - 1 - 7);
         if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
         }

         for(int i = 0; i < shownCount; ++i) {
            int itemIndex = 1 + scrollIndex + i;
            if (itemIndex >= items.size()) {
               break;
            }

            DetectedRngItem item = (DetectedRngItem)items.get(itemIndex);
            long buyPrice = LowestBinManager.getBuyPrice(item.id);
            long sellPrice = LowestBinManager.getSellPrice(item.id);
            String displayName = getPrettyName(item.id, item.name);
            var10000 = displayName.length() > 16 ? displayName.substring(0, 14) + ".." : displayName;
            String nameText = "§f" + var10000;
            String valueText;
            if (buyPrice <= 0L && sellPrice <= 0L) {
               valueText = "§8N/A";
            } else {
               String buyFormatted = formatPriceShort(buyPrice);
               String sellFormatted = formatPriceShort(sellPrice);
               valueText = "§7" + buyFormatted + "/" + sellFormatted;
            }

            g.text(font, nameText, 5, curY, -1, true);
            int valueWidth = font.width(valueText.replaceAll("(?i)§.", ""));
            g.text(font, valueText, width - valueWidth - 5, curY, -1, true);
            curY += 10;
         }

         if (items.size() - 1 > 7) {
            int scrollbarX = width - 2;
            int scrollbarY = 44;
            int scrollbarHeight = height - 48;
            g.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarHeight, 872415231);
            double scrollPct = (double)scrollIndex / (double)maxScroll;
            int thumbHeight = 15;
            int thumbY = scrollbarY + (int)(scrollPct * (double)(scrollbarHeight - thumbHeight));
            g.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, -855638017);
         }

         g.pose().popMatrix();
      }
   }

   public static String getSkyblockIdFromName(String cleanName) {
      return getSkyblockIdFromName(cleanName, "COMMON");
   }

   public static String getSkyblockIdFromName(String cleanName, String rarity) {
      if (cleanName != null && !cleanName.isEmpty()) {
         String upper = cleanName.toUpperCase().replace(" ", "_");
         if (upper.contains("] ")) {
            String petName = upper.substring(upper.indexOf("] ") + 2).trim();
            return "PET_" + petName + "_" + rarity;
         } else if (cleanName.equalsIgnoreCase("End Stone Idol")) {
            return "ENDSTONE_IDOL";
         } else if (cleanName.equalsIgnoreCase("Chain of the End Times")) {
            return "CHAIN_END_TIMES";
         } else if (cleanName.equalsIgnoreCase("Grand Experience Bottle")) {
            return "GRAND_EXP_BOTTLE";
         } else if (cleanName.equalsIgnoreCase("Titanic Experience Bottle")) {
            return "TITANIC_EXP_BOTTLE";
         } else if (cleanName.equalsIgnoreCase("Colossal Experience Bottle")) {
            return "COLOSSAL_EXP_BOTTLE";
         } else {
            String[] parts = cleanName.split("\\s+");
            if (parts.length >= 2) {
               String lastWord = parts[parts.length - 1].toUpperCase();
               if (lastWord.matches("^[IVXLCDM]+$")) {
                  int level = RomanNumber.romanToDecimal(lastWord);
                  if (level > 0) {
                     StringBuilder baseName = new StringBuilder();

                     for(int i = 0; i < parts.length - 1; ++i) {
                        baseName.append(parts[i].toUpperCase()).append("_");
                     }

                     String base = baseName.toString().replaceAll("_$", "");
                     return "ENCHANTMENT_" + base + "_" + level;
                  }
               }
            }

            String found = LowestBinManager.findIdByName(cleanName, true);
            return found != null ? found : upper;
         }
      } else {
         return "";
      }
   }

   public static String getPrettyName(String id, String fallbackName) {
      if (id.startsWith("ENCHANTMENT_")) {
         String enchantName = id.replace("ENCHANTMENT_", "");
         String[] parts = enchantName.split("_");
         if (parts.length >= 2) {
            try {
               String var10000 = parts[0].substring(0, 1).toUpperCase();
               String name = var10000 + parts[0].substring(1).toLowerCase();
               if (parts.length > 2) {
                  StringBuilder sb = new StringBuilder();

                  for(int i = 0; i < parts.length - 1; ++i) {
                     sb.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1).toLowerCase()).append(" ");
                  }

                  name = sb.toString().trim();
               }

               String level = parts[parts.length - 1];
               return name + " " + level;
            } catch (Exception var7) {
            }
         }
      }

      return fallbackName;
   }

   public static String formatPriceShort(long price) {
      if (price <= 0L) {
         return "0";
      } else {
         double val;
         String unit;
         if (price >= 1000000000L) {
            val = (double)price / (double)1.0E9F;
            unit = "b";
         } else if (price >= 1000000L) {
            val = (double)price / (double)1000000.0F;
            unit = "m";
         } else {
            if (price < 1000L) {
               return String.valueOf(price);
            }

            val = (double)price / (double)1000.0F;
            unit = "k";
         }

         return val == (double)((long)val) ? String.format("%d%s", (long)val, unit) : String.format("%.1f%s", val, unit);
      }
   }

   static {
      loadRngFromFile();
   }

   public static class RngDataState {
      public long lastScanTime = 0L;
      public Map<String, DetectedRngItem> rewards = new ConcurrentHashMap();
   }

   public static class DetectedRngItem {
      public String id;
      public String name;
      public int metterCost;

      public DetectedRngItem(String id, String name, int metterCost) {
         this.id = id;
         this.name = name;
         this.metterCost = metterCost;
      }
   }
}
