package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;

public class ItemListOverlay {
   public static String query = "";
   private static final List<SkyblockItemManager.SkyblockItemInfo> filteredItems = new ArrayList();
   private static final Map<String, ItemStack> itemStackCache = new ConcurrentHashMap();
   public static int currentPage = 0;
   public static int itemsPerPage = 60;
   public static int cols = 8;
   public static int rows = 8;
   public static int sidebarX = 0;
   public static int sidebarY = 0;
   public static int sidebarW = 0;
   public static int sidebarH = 0;
   public static EditBox searchBox = null;
   private static boolean initialized = false;
   public static boolean isDragging = false;
   public static boolean isResizing = false;
   public static double dragOffsetX = (double)0.0F;
   public static double dragOffsetY = (double)0.0F;
   private static final int RESIZE_HANDLE_SIZE = 8;
   public static ItemStack hoveredStack = null;
   public static String hoveredId = null;
   public static String calcPreview = null;
   public static String pendingQuery = null;
   public static long lastQueryTime = 0L;
   private static String expandedId = null;
   private static int expandedX = 0;
   private static int expandedY = 0;
   private static long expandedTime = 0L;
   public static boolean isHiddenState = false;
   public static long lastSearchBoxClick = 0L;
   public static boolean inventorySearchMode = false;
   private static final Map<String, List<SkyblockItemManager.SkyblockItemInfo>> variantMap = new ConcurrentHashMap();

   public static void init() {
      if (!initialized) {
         filterItems();
         initialized = true;
      }

   }

   public static void setQuery(String q) {
      query = q;
      currentPage = 0;
      pendingQuery = q;
      lastQueryTime = q != null && q.isEmpty() ? 0L : System.currentTimeMillis();
      if (q != null && !q.isEmpty() && q.matches(".*[\\+\\-\\*/xX].*") && q.matches(".*[0-9].*")) {
         String mathQ = q.replaceAll("(?<=\\d)\\s*[xX]\\s*(?=\\d)", "*");
         SkyblockCalculator.EvaluationResult res = SkyblockCalculator.evaluate(mathQ);
         if (res.error == null) {
            String formattedNumber = res.value == (double)((long)res.value) ? String.format("%,d", (long)res.value) : String.format("%,.2f", res.value);
            String shortPrice = LowestBinManager.formatPrice(res.value).toLowerCase();
            calcPreview = q + " = " + formattedNumber + " (" + shortPrice + ")";
         } else {
            calcPreview = null;
         }
      } else {
         calcPreview = null;
      }

   }

   private static int customNameCompare(String nameA, String nameB) {
      List<String> kuudra = List.of("", "Hot ", "Burning ", "Fiery ", "Infernal ");

      for(String suffix : List.of("Aurora Helmet", "Aurora Chestplate", "Aurora Leggings", "Aurora Boots", "Crimson Helmet", "Crimson Chestplate", "Crimson Leggings", "Crimson Boots", "Terror Helmet", "Terror Chestplate", "Terror Leggings", "Terror Boots", "Fervor Helmet", "Fervor Chestplate", "Fervor Leggings", "Fervor Boots", "Hollow Helmet", "Hollow Chestplate", "Hollow Leggings", "Hollow Boots")) {
         boolean aIsKuudra = false;
         boolean bIsKuudra = false;
         int aRank = -1;
         int bRank = -1;

         for(int i = 0; i < kuudra.size(); ++i) {
            String var10001 = (String)kuudra.get(i);
            if (nameA.equalsIgnoreCase(var10001 + suffix)) {
               aIsKuudra = true;
               aRank = i;
            }

            var10001 = (String)kuudra.get(i);
            if (nameB.equalsIgnoreCase(var10001 + suffix)) {
               bIsKuudra = true;
               bRank = i;
            }
         }

         if (aIsKuudra && bIsKuudra) {
            return Integer.compare(aRank, bRank);
         }
      }

      int lastSpaceA = nameA.lastIndexOf(32);
      int lastSpaceB = nameB.lastIndexOf(32);
      if (lastSpaceA != -1 && lastSpaceB != -1) {
         String prefixA = nameA.substring(0, lastSpaceA);
         String prefixB = nameB.substring(0, lastSpaceB);
         if (prefixA.equalsIgnoreCase(prefixB)) {
            String suffixA = nameA.substring(lastSpaceA + 1);
            String suffixB = nameB.substring(lastSpaceB + 1);
            int romanA = RomanNumber.romanToDecimal(suffixA);
            int romanB = RomanNumber.romanToDecimal(suffixB);
            if (romanA > 0 && romanB > 0) {
               return Integer.compare(romanA, romanB);
            }
         }
      }

      return nameA.compareToIgnoreCase(nameB);
   }

   private static void filterItems() {
      filteredItems.clear();
      variantMap.clear();
      Map<String, SkyblockItemManager.SkyblockItemInfo> allItems = SkyblockItemManager.getAllItems();
      if (allItems != null) {
         String lowerQuery = query.toLowerCase().trim();
         Map<String, List<SkyblockItemManager.SkyblockItemInfo>> groups = new HashMap();
         boolean hideSkins = BomboConfig.get().itemListHideSkins;
         boolean hideNPCs = BomboConfig.get().itemListHideNPCs;
         boolean hideMobs = BomboConfig.get().itemListHideMobs;
         boolean hideVanilla = BomboConfig.get().itemListHideVanilla;

         for(SkyblockItemManager.SkyblockItemInfo info : allItems.values()) {
            boolean matchesQuery = lowerQuery.isEmpty() || (info.name != null && info.name.toLowerCase().contains(lowerQuery)) || (info.id != null && info.id.toLowerCase().contains(lowerQuery));
            if (!matchesQuery && info.lore != null && !lowerQuery.isEmpty()) {
               for (Component lineComp : info.lore) {
                  if (lineComp != null) {
                     String lineStr = lineComp.getString().toLowerCase();
                     if (lineStr.contains(lowerQuery)) {
                        matchesQuery = true;
                        break;
                     }
                  }
               }
            }

            if ((!hideVanilla || !info.vanilla) && (info.id == null || (!hideSkins || !info.id.contains("_SKIN") && !info.id.contains("DYE") && !info.id.endsWith("_SHIMMER") && !info.id.endsWith("_PERSONALITY") && (info.name == null || !info.name.toLowerCase().contains(" skin") && !info.name.toLowerCase().contains(" dye"))) && (!hideNPCs || !info.id.contains("_NPC")) && (!hideMobs || !info.id.endsWith("_MONSTER") && !info.id.endsWith("_BOSS") && !info.id.endsWith("_MINIBOSS") && (info.name == null || !info.name.toLowerCase().contains("sea creature")))) && matchesQuery) {
               String baseId = info.id;
               if (baseId != null) {
                  if (baseId.contains(";")) {
                     baseId = baseId.substring(0, baseId.indexOf(";"));
                  }

                  if (baseId.matches("(HOT|BURNING|FIERY|INFERNAL)_(AURORA|CRIMSON|TERROR|FERVOR|HOLLOW)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")) {
                     baseId = baseId.replaceFirst("^(HOT|BURNING|FIERY|INFERNAL)_", "");
                  } else if (baseId.matches("PERFECT_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)_[0-9]+")) {
                     baseId = baseId.substring(0, baseId.lastIndexOf(95));
                  } else if (baseId.matches(".*_GENERATOR_[0-9]+")) {
                     baseId = baseId.substring(0, baseId.lastIndexOf(95));
                  } else if (baseId.matches("(SOUL_)?CAMPFIRE_TALISMAN_[0-9]+")) {
                     baseId = baseId.substring(0, baseId.lastIndexOf(95));
                  } else if (baseId.matches("ROMEO_AND_JULIET_[0-9]+")) {
                     baseId = baseId.substring(0, baseId.lastIndexOf(95));
                  } else if (baseId.matches("POTION_AFFINITY_TALISMAN_[0-9]+")) {
                     baseId = baseId.substring(0, baseId.lastIndexOf(95));
                  }
               }

               if (baseId != null) {
                  ((List)groups.computeIfAbsent(baseId, (k) -> new ArrayList())).add(info);
               }
            }
         }

         for(List<SkyblockItemManager.SkyblockItemInfo> group : groups.values()) {
            if (group.size() == 1) {
               filteredItems.add((SkyblockItemManager.SkyblockItemInfo)group.get(0));
            } else {
               group.sort((a, b) -> {
                  int tvCmp = Integer.compare(SkyblockItemManager.getTierValue(a.tier), SkyblockItemManager.getTierValue(b.tier));
                  return tvCmp != 0 ? tvCmp : customNameCompare(a.name != null ? a.name : "", b.name != null ? b.name : "");
               });
               SkyblockItemManager.SkyblockItemInfo rep = (SkyblockItemManager.SkyblockItemInfo)group.get(group.size() - 1);
               filteredItems.add(rep);
               variantMap.put(rep.id, group);
            }
         }

         int sortType = BomboConfig.get().itemListSortType;
         boolean reverse = BomboConfig.get().itemListSortReverse;
         filteredItems.sort((a, b) -> {
            int result = 0;
            if (sortType == 0) {
               int tvA = SkyblockItemManager.getTierValue(a.tier);
               int tvB = SkyblockItemManager.getTierValue(b.tier);
               if (tvA != tvB) {
                  result = Integer.compare(tvB, tvA);
               } else {
                  String nameA = a.name != null ? a.name : "";
                  String nameB = b.name != null ? b.name : "";
                  result = customNameCompare(nameA, nameB);
               }
            } else if (sortType == 1) {
               String nameA = a.name != null ? a.name : "";
               String nameB = b.name != null ? b.name : "";
               result = customNameCompare(nameA, nameB);
            } else {
               String nameA = a.name != null ? a.name : "";
               String nameB = b.name != null ? b.name : "";
               result = customNameCompare(nameA, nameB);
            }

            return reverse ? -result : result;
         });
      }
   }

   private static boolean isCosmetic(SkyblockItemManager.SkyblockItemInfo info) {
      if (info.id == null) {
         return false;
      } else {
         return info.id.contains("_SKIN") || info.id.contains("DYE") || info.id.endsWith("_SHIMMER") || info.id.endsWith("_PERSONALITY");
      }
   }

   public static void updateLayout(int leftPos, int imageWidth, int topPos, int width, int height) {
      init();
      if (BomboConfig.get().itemListX == -1) {
         sidebarW = 180;
         sidebarH = Math.min(250, height - 20);
         sidebarX = Math.max(10, width - sidebarW - 10);
         sidebarY = 10;
      } else {
         sidebarX = BomboConfig.get().itemListX;
         sidebarY = BomboConfig.get().itemListY;
         sidebarW = BomboConfig.get().itemListW;
         sidebarH = BomboConfig.get().itemListH;
         if (sidebarW > width) {
            sidebarW = width;
         }

         if (sidebarH > height) {
            sidebarH = height;
         }

         if (sidebarX + sidebarW > width) {
            sidebarX = Math.max(0, width - sidebarW);
         }

         if (sidebarY + sidebarH > height) {
            sidebarY = Math.max(0, height - sidebarH);
         }

         if (sidebarX < 0) {
            sidebarX = 0;
         }

         if (sidebarY < 0) {
            sidebarY = 0;
         }
      }

      updateGridSize();
   }

   public static void updateGridSize() {
      if (sidebarW >= 120) {
         cols = Math.max(1, (sidebarW - 10) / 18);
         rows = Math.max(1, (sidebarH - 55) / 18);
         itemsPerPage = cols * rows;
      }

   }

   public static void saveLayout() {
      BomboConfig.get().itemListX = sidebarX;
      BomboConfig.get().itemListY = sidebarY;
      BomboConfig.get().itemListW = sidebarW;
      BomboConfig.get().itemListH = sidebarH;
      BomboConfig.save();
   }

   public static int getTierColorInt(String tier) {
      if (tier == null) {
         return -1437248171;
      } else {
         switch (tier.toUpperCase()) {
            case "UNCOMMON":
               return -1437204651;
            case "RARE":
               return -1437248001;
            case "EPIC":
               return -1431699286;
            case "LEGENDARY":
               return -1426085376;
            case "MYTHIC":
               return -1426106881;
            case "DIVINE":
               return -1437204481;
            case "SPECIAL":
            case "VERY_SPECIAL":
               return -1426107051;
            default:
               return -1437248171;
         }
      }
   }

   public static void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
      if (pendingQuery != null && System.currentTimeMillis() - lastQueryTime > 300L) {
         filterItems();
         pendingQuery = null;
      }

      BomboConfig.Settings s = BomboConfig.get();
      int globalScreenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
      sidebarX = s.itemListX == -1 ? Math.max(10, globalScreenWidth - sidebarW - 10) : s.itemListX;
      sidebarY = s.itemListY == -1 ? 10 : s.itemListY;
      if (searchBox != null && !s.itemListSeparateSearch) {
         searchBox.setPosition(sidebarX + 5, sidebarY + sidebarH - 52);
      }
      if (s.itemListW != sidebarW || s.itemListH != sidebarH) {
         sidebarW = s.itemListW;
         sidebarH = s.itemListH;
         updateGridSize();
      }

      hoveredStack = null;
      hoveredId = null;
      Component hoveredComponent = null;
      if (!BomboConfig.get().itemListEnabled) {
         if (searchBox != null) {
            searchBox.setVisible(false);
         }

      } else {
         if (searchBox != null) {
            searchBox.setVisible(true);
         }

         SkyblockItemManager.ensureLoaded();
         boolean onLeft = sidebarX < Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
         int togglesWidth = 128;
         int searchBoxW = sidebarW - 10 - togglesWidth - 4;
         if (searchBoxW < 30) {
            searchBoxW = 30;
         }

         boolean autoHide = BomboConfig.get().autoHideItemList && !(Minecraft.getInstance().screen instanceof HudMoveScreen);
         boolean searchFocused = searchBox != null && searchBox.isFocused();
         int tabX = onLeft ? sidebarX : sidebarX + sidebarW - 15;
         int tabY = sidebarY + sidebarH / 2 - 15;
         if (autoHide && !searchFocused) {
            if (isHiddenState) {
               if (mouseX >= tabX && mouseX <= tabX + 15 && mouseY >= tabY && mouseY <= tabY + 30) {
                  isHiddenState = false;
               }
            } else if (mouseX < sidebarX || mouseX > sidebarX + sidebarW || mouseY < sidebarY || mouseY > sidebarY + sidebarH) {
               isHiddenState = true;
            }
         } else {
            isHiddenState = false;
         }

         if (isHiddenState) {
            if (searchBox != null) {
               if (!BomboConfig.get().itemListSearchAlwaysVisible && !BomboConfig.get().itemListSeparateSearch) {
                  searchBox.setVisible(false);
               } else {
                  searchBox.setVisible(true);
               }
            }

            graphics.fill(tabX, tabY, tabX + 15, tabY + 30, -1442840576);
            graphics.text(font, onLeft ? ">" : "<", tabX + 4, tabY + 11, -1, true);
         } else {
            if (searchBox != null && !BomboConfig.get().itemListSeparateSearch) {
               if (onLeft) {
                  searchBox.setX(sidebarX + 5 + togglesWidth + 4);
               } else {
                  searchBox.setX(sidebarX + 5);
               }

               searchBox.setY(sidebarY + sidebarH - 52);
               searchBox.setWidth(searchBoxW);
            }

            if (Minecraft.getInstance().screen instanceof HudMoveScreen) {
               graphics.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH - 42, -2013265920);
               graphics.centeredText(font, "Item List Area", sidebarX + sidebarW / 2, sidebarY + (sidebarH - 42) / 2, -1);
               if (!BomboConfig.get().itemListLocked) {
                  graphics.fill(sidebarX, sidebarY + sidebarH - 52, sidebarX + 5, sidebarY + sidebarH - 32, -1442775296);
               }

            } else {
               if (!BomboConfig.get().itemListRemoveBackground) {
                  graphics.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH - 42, -1442840576);
               }

               int togglesX = onLeft ? sidebarX + 5 : sidebarX + 5 + searchBoxW + 4;
               int togglesY = sidebarY + sidebarH - 52;
               graphics.fill(togglesX, togglesY, togglesX + 18, togglesY + 20, 1342177280);
               ItemStack hopperStack = new ItemStack(Items.HOPPER);
               int currentSort = BomboConfig.get().itemListSortType;
               String sortName = currentSort == 0 ? "Rarity" : "Name";
               String order = BomboConfig.get().itemListSortReverse ? "(Descending)" : "(Ascending)";
               graphics.item(hopperStack, togglesX + 1, togglesY + 2);
               if (mouseX >= togglesX && mouseX < togglesX + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
                  graphics.fill(togglesX, togglesY, togglesX + 18, togglesY + 20, 1358954495);
                  hoveredComponent = Component.literal("§eSort by " + sortName + " " + order);
               }

               int scathaTx = togglesX + 22;
               int scathaColor = BomboConfig.get().itemListHideSkins ? -2130771968 : 1342177280;
               graphics.fill(scathaTx, togglesY, scathaTx + 18, togglesY + 20, scathaColor);
               ItemStack scathaStack = (ItemStack)itemStackCache.computeIfAbsent("PET_SKIN_SCATHA_ALBINO", SkyblockItemManager::createSkyblockItem);
               if (scathaStack != null && !scathaStack.isEmpty()) {
                  graphics.item(scathaStack, scathaTx + 1, togglesY + 2);
               }

               if (mouseX >= scathaTx && mouseX < scathaTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
                  graphics.fill(scathaTx, togglesY, scathaTx + 18, togglesY + 20, 1358954495);
                  hoveredComponent = Component.literal("§eToggle Skins/Dyes");
               }

               int npcTx = togglesX + 44;
               int npcColor = BomboConfig.get().itemListHideNPCs ? -2130771968 : 1342177280;
               graphics.fill(npcTx, togglesY, npcTx + 18, togglesY + 20, npcColor);
               ItemStack npcStack = new ItemStack(Items.VILLAGER_SPAWN_EGG);
               graphics.item(npcStack, npcTx + 1, togglesY + 2);
               if (mouseX >= npcTx && mouseX < npcTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
                  graphics.fill(npcTx, togglesY, npcTx + 18, togglesY + 20, 1358954495);
                  hoveredComponent = Component.literal("§eToggle NPCs");
               }

               int mobTx = togglesX + 66;
               int mobColor = BomboConfig.get().itemListHideMobs ? -2130771968 : 1342177280;
               graphics.fill(mobTx, togglesY, mobTx + 18, togglesY + 20, mobColor);
               ItemStack mobStack = new ItemStack(Items.ZOMBIE_SPAWN_EGG);
               graphics.item(mobStack, mobTx + 1, togglesY + 2);
               if (mouseX >= mobTx && mouseX < mobTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
                  graphics.fill(mobTx, togglesY, mobTx + 18, togglesY + 20, 1358954495);
                  hoveredComponent = Component.literal("§eToggle Mobs");
               }

               int vanillaTx = togglesX + 88;
               int vanillaColor = BomboConfig.get().itemListHideVanilla ? -2130771968 : 1342177280;
               graphics.fill(vanillaTx, togglesY, vanillaTx + 18, togglesY + 20, vanillaColor);
               ItemStack vanillaStack = new ItemStack(Items.GRASS_BLOCK);
               graphics.item(vanillaStack, vanillaTx + 1, togglesY + 2);
               if (mouseX >= vanillaTx && mouseX < vanillaTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
                  graphics.fill(vanillaTx, togglesY, vanillaTx + 18, togglesY + 20, 1358954495);
                  hoveredComponent = Component.literal("§eToggle Vanilla Items");
               }

               int autoHideTx = togglesX + 110;
               int autoHideColor = BomboConfig.get().autoHideItemList ? -2141847723 : 1342177280;
               graphics.fill(autoHideTx, togglesY, autoHideTx + 18, togglesY + 20, autoHideColor);
               ItemStack autoHideStack = new ItemStack(Items.ENDER_EYE);
               graphics.item(autoHideStack, autoHideTx + 1, togglesY + 2);
               if (mouseX >= autoHideTx && mouseX < autoHideTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
                  graphics.fill(autoHideTx, togglesY, autoHideTx + 18, togglesY + 20, 1358954495);
                  hoveredComponent = Component.literal("§eToggle Auto-Hide");
               }

               if (!BomboConfig.get().itemListLocked) {
                  graphics.fill(sidebarX, sidebarY + sidebarH - 52, sidebarX + 5, sidebarY + sidebarH - 32, -5592406);
               }

               int startX = sidebarX + 5;
               int startY = sidebarY + 5;
               hoveredStack = null;
               hoveredId = null;
               boolean mouseInPopout = false;
               if (expandedId != null && System.currentTimeMillis() - expandedTime < 200L) {
                  List<SkyblockItemManager.SkyblockItemInfo> variants = (List)variantMap.get(expandedId);
                  if (variants != null) {
                     int pCols = Math.min(variants.size(), 10);
                     int pRows = (variants.size() + pCols - 1) / pCols;
                     int popoutW = pCols * 18;
                     int popoutH = pRows * 18;
                     int popoutX = expandedX + 8 - popoutW / 2;
                     int popoutY = expandedY + 18;
                     int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                     if (popoutX < 0) {
                        popoutX = 0;
                     }

                     if (popoutX + popoutW > screenWidth) {
                        popoutX = screenWidth - popoutW;
                     }

                     if (mouseX >= popoutX && mouseX < popoutX + popoutW && mouseY >= popoutY && mouseY < popoutY + popoutH) {
                        mouseInPopout = true;
                     }
                  }
               }

               for(int i = 0; i < itemsPerPage; ++i) {
                  int idx = currentPage * itemsPerPage + i;
                  if (idx >= filteredItems.size()) {
                     break;
                  }

                  int col = i % cols;
                  int row = i / cols;
                  int slotX = startX + col * 18;
                  int slotY = startY + row * 18;
                  SkyblockItemManager.SkyblockItemInfo info = (SkyblockItemManager.SkyblockItemInfo)filteredItems.get(idx);
                  ItemStack stack = (ItemStack)itemStackCache.computeIfAbsent(info.id, SkyblockItemManager::createSkyblockItem);
                  if (stack != null && !stack.isEmpty()) {
                     if (BomboConfig.get().itemListColoredBackground) {
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, getTierColorInt(info.tier));
                     }

                     graphics.item(stack, slotX, slotY);
                     if (!mouseInPopout && mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706433);
                        hoveredStack = stack;
                        hoveredId = info.id;
                     }
                  }
               }

               boolean anyHovered = false;
               if (hoveredId != null && variantMap.containsKey(hoveredId)) {
                  expandedId = hoveredId;

                  for(int i = 0; i < itemsPerPage; ++i) {
                     int idx = currentPage * itemsPerPage + i;
                     if (idx >= filteredItems.size()) {
                        break;
                     }

                     if (((SkyblockItemManager.SkyblockItemInfo)filteredItems.get(idx)).id.equals(hoveredId)) {
                        expandedX = startX + i % cols * 18;
                        expandedY = startY + i / cols * 18;
                        break;
                     }
                  }

                  expandedTime = System.currentTimeMillis();
                  anyHovered = true;
               }

               if (expandedId != null && System.currentTimeMillis() - expandedTime < 200L) {
                  List<SkyblockItemManager.SkyblockItemInfo> variants = (List)variantMap.get(expandedId);
                  if (variants != null) {
                     int pCols = Math.min(variants.size(), 10);
                     int pRows = (variants.size() + pCols - 1) / pCols;
                     int popoutW = pCols * 18;
                     int popoutH = pRows * 18;
                     int popoutX = expandedX + 8 - popoutW / 2;
                     int popoutY = expandedY + 18;
                     int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                     if (popoutX < 0) {
                        popoutX = 0;
                     }

                     if (popoutX + popoutW > screenWidth) {
                        popoutX = screenWidth - popoutW;
                     }

                     graphics.fill(popoutX, popoutY, popoutX + popoutW, popoutY + popoutH, -587202560);

                     for(int v = 0; v < variants.size(); ++v) {
                        SkyblockItemManager.SkyblockItemInfo vInfo = (SkyblockItemManager.SkyblockItemInfo)variants.get(v);
                        ItemStack vStack = (ItemStack)itemStackCache.computeIfAbsent(vInfo.id, SkyblockItemManager::createSkyblockItem);
                        int vx = popoutX + v % pCols * 18;
                        int vy = popoutY + v / pCols * 18;
                        graphics.item(vStack, vx, vy);
                        if (mouseX >= vx && mouseX < vx + 16 && mouseY >= vy && mouseY < vy + 16) {
                           graphics.fill(vx, vy, vx + 16, vy + 16, -2130706433);
                           hoveredStack = vStack;
                           hoveredId = vInfo.id;
                           anyHovered = true;
                           expandedTime = System.currentTimeMillis();
                        }
                     }
                  }
               }

               if (!anyHovered && System.currentTimeMillis() - expandedTime > 200L) {
                  expandedId = null;
               }

               int maxPages = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / itemsPerPage);
               if (currentPage >= maxPages) {
                  currentPage = maxPages - 1;
               }

               if (currentPage < 0) {
                  currentPage = 0;
               }

               int var10000 = currentPage + 1;
               String pageStr = var10000 + " / " + maxPages;
               int pageRowY = sidebarY + sidebarH - 18;
               graphics.text(font, "§e[<-]", sidebarX + 5, pageRowY + 4, -1, false);
               graphics.text(font, pageStr, sidebarX + 35, pageRowY + 4, -1, false);
               graphics.text(font, "§e[->]", sidebarX + 80, pageRowY + 4, -1, false);
               if (calcPreview != null && searchBox != null && searchBox.visible) {
                  int cx = searchBox.getX();
                  int cy = searchBox.getY() + 16 + 2;
                  int cw = font.width(calcPreview);
                  graphics.fill(cx, cy, cx + cw + 4, cy + 12, -1442840576);
                  graphics.text(font, calcPreview, cx + 2, cy + 2, -22016, true);
               }

               if (hoveredComponent != null) {
                  graphics.setTooltipForNextFrame(font, hoveredComponent, mouseX, mouseY);
               } else if (hoveredStack != null) {
                  try {
                     if (hoveredId != null && BomboConfig.get().lowestBin) {
                        long price = (Long)LowestBinManager.getLowestBin(hoveredId).getNow(-1L);
                        if (price > 0L) {
                           List<Component> tooltip = hoveredStack.getTooltipLines(TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, Default.NORMAL);
                           boolean isBz = LowestBinManager.isBazaar(hoveredId);
                           String label = isBz ? "§6BZ: " : "§6Lowest BIN: ";
                           String priceText = label + "§e" + LowestBinManager.formatPrice(price);
                           boolean isPet = hoveredId.startsWith("PET-") || hoveredId.contains(";");
                           if (isPet) {
                              int maxLvl = !hoveredId.contains("GOLDEN_DRAGON") && !hoveredId.contains("JADE_DRAGON") && !hoveredId.contains("ROSE_DRAGON") ? 100 : 200;
                              long lvlMaxPrice = (Long)LowestBinManager.getLowestBin(hoveredId + "-" + maxLvl).getNow(-1L);
                              if (lvlMaxPrice > 0L) {
                                 priceText = priceText + " §7(" + LowestBinManager.formatPrice(lvlMaxPrice) + ")";
                              }
                           }

                           boolean hasPrice = false;

                           for(Component c : tooltip) {
                              if (c.getString().contains("Lowest BIN:") || c.getString().contains("BZ:")) {
                                 hasPrice = true;
                                 break;
                              }
                           }

                           if (!hasPrice) {
                              List<Component> mutableTooltip = new ArrayList(tooltip);
                              mutableTooltip.add(Component.literal(priceText));
                              graphics.setTooltipForNextFrame(font, mutableTooltip, Optional.empty(), mouseX, mouseY);
                              return;
                           }
                        }
                     }

                     graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
                  } catch (Throwable var53) {
                     graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
                  }
               }

            }
         }
      }
   }

   public static boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!BomboConfig.get().itemListEnabled) {
         return false;
      } else if (sidebarW < 120) {
         return false;
      } else {
         if (searchBox != null) {
            float scale = BomboConfig.get().itemListSearchScale;
            double unscaledX = mouseX;
            double unscaledY = mouseY;
            if (scale != 1.0F) {
               int tx = searchBox.getX();
               int ty = searchBox.getY();
               unscaledX = (double)tx + (mouseX - (double)tx) / (double)scale;
               unscaledY = (double)ty + (mouseY - (double)ty) / (double)scale;
            }

            if (searchBox.isMouseOver(unscaledX, unscaledY)) {
               if (button == 1) {
                  searchBox.setValue("");
                  searchBox.setFocused(false);
                  if (Minecraft.getInstance().screen != null) {
                     Minecraft.getInstance().screen.setFocused((GuiEventListener)null);
                  }

                  return true;
               }

               long now = System.currentTimeMillis();
               if (now - lastSearchBoxClick < 300L) {
                  inventorySearchMode = !inventorySearchMode;
               }

               lastSearchBoxClick = now;
               return false;
            }
         }

         if (isHiddenState) {
            return false;
         } else if (Minecraft.getInstance().screen instanceof HudMoveScreen) {
            return false;
         } else {
            boolean onLeft = sidebarX < Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
            int togglesWidth = 128;
            int searchBoxW = sidebarW - 10 - togglesWidth - 4;
            int togglesX = onLeft ? sidebarX + 5 : sidebarX + 5 + searchBoxW + 4;
            int togglesY = sidebarY + sidebarH - 52;
            int scathaTx = togglesX + 22;
            int npcTx = togglesX + 44;
            int mobTx = togglesX + 66;
            int vanillaTx = togglesX + 88;
            int autoHideTx = togglesX + 110;
            if (mouseY >= (double)togglesY && mouseY < (double)(togglesY + 20)) {
               if (mouseX >= (double)togglesX && mouseX < (double)(togglesX + 18)) {
                  if (button == 1) {
                     BomboConfig.get().itemListSortReverse = !BomboConfig.get().itemListSortReverse;
                  } else {
                     int current = BomboConfig.get().itemListSortType;
                     BomboConfig.get().itemListSortType = (current + 1) % 2;
                  }

                  BomboConfig.save();
                  filterItems();
                  return true;
               }

               if (mouseX >= (double)scathaTx && mouseX < (double)(scathaTx + 18)) {
                  BomboConfig.get().itemListHideSkins = !BomboConfig.get().itemListHideSkins;
                  BomboConfig.save();
                  filterItems();
                  return true;
               }

               if (mouseX >= (double)npcTx && mouseX < (double)(npcTx + 18)) {
                  BomboConfig.get().itemListHideNPCs = !BomboConfig.get().itemListHideNPCs;
                  BomboConfig.save();
                  filterItems();
                  return true;
               }

               if (mouseX >= (double)mobTx && mouseX < (double)(mobTx + 18)) {
                  BomboConfig.get().itemListHideMobs = !BomboConfig.get().itemListHideMobs;
                  BomboConfig.save();
                  filterItems();
                  return true;
               }

               if (mouseX >= (double)vanillaTx && mouseX < (double)(vanillaTx + 18)) {
                  BomboConfig.get().itemListHideVanilla = !BomboConfig.get().itemListHideVanilla;
                  BomboConfig.save();
                  filterItems();
                  return true;
               }

               if (mouseX >= (double)autoHideTx && mouseX < (double)(autoHideTx + 18)) {
                  BomboConfig.get().autoHideItemList = !BomboConfig.get().autoHideItemList;
                  BomboConfig.save();
                  return true;
               }
            }

            if (expandedId != null) {
               List<SkyblockItemManager.SkyblockItemInfo> variants = (List)variantMap.get(expandedId);
               if (variants != null) {
                  int popoutX = expandedX + 18;
                  int popoutY = expandedY;

                  for(int v = 0; v < variants.size(); ++v) {
                     int vx = popoutX + v * 18;
                     if (mouseX >= (double)vx && mouseX < (double)(vx + 16) && mouseY >= (double)popoutY && mouseY < (double)(popoutY + 16)) {
                        SkyblockItemManager.SkyblockItemInfo info = (SkyblockItemManager.SkyblockItemInfo)variants.get(v);
                        if (button == 0) {
                           Minecraft.getInstance().setScreenAndShow(new RecipeViewerScreen(info.id, Minecraft.getInstance().screen));
                        } else if (button == 1) {
                           RecipeViewerScreen rvs = new RecipeViewerScreen(info.id, Minecraft.getInstance().screen);
                           rvs.setUsageMode(true);
                           Minecraft.getInstance().setScreenAndShow(rvs);
                        }

                        return true;
                     }
                  }
               }
            }

            if (mouseX >= (double)sidebarX && mouseX <= (double)(sidebarX + sidebarW) && mouseY >= (double)sidebarY && mouseY <= (double)(sidebarY + sidebarH - 56)) {
               int startX = sidebarX + 5;
               int startY = sidebarY + 5;

               for(int i = 0; i < itemsPerPage; ++i) {
                  int idx = currentPage * itemsPerPage + i;
                  if (idx >= filteredItems.size()) {
                     break;
                  }

                  int col = i % cols;
                  int row = i / cols;
                  int slotX = startX + col * 18;
                  int slotY = startY + row * 18;
                  if (mouseX >= (double)slotX && mouseX < (double)(slotX + 16) && mouseY >= (double)slotY && mouseY < (double)(slotY + 16)) {
                     SkyblockItemManager.SkyblockItemInfo info = (SkyblockItemManager.SkyblockItemInfo)filteredItems.get(idx);
                     if (button == 0) {
                        Minecraft.getInstance().setScreenAndShow(new RecipeViewerScreen(info.id, Minecraft.getInstance().screen));
                     } else if (button == 1) {
                        RecipeViewerScreen rvs = new RecipeViewerScreen(info.id, Minecraft.getInstance().screen);
                        rvs.setUsageMode(true);
                        Minecraft.getInstance().setScreenAndShow(rvs);
                     }

                     return true;
                  }
               }

               if (!BomboConfig.get().itemListLocked) {
                  isDragging = true;
                  dragOffsetX = mouseX - (double)sidebarX;
                  dragOffsetY = mouseY - (double)sidebarY;
                  return true;
               } else {
                  return false;
               }
            } else {
               int pageRowY = sidebarY + sidebarH - 18;
               if (mouseY >= (double)pageRowY && mouseY < (double)(pageRowY + 18)) {
                  int maxPages = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / itemsPerPage);
                  if (mouseX >= (double)(sidebarX + 5) && mouseX < (double)(sidebarX + 30)) {
                     if (currentPage > 0) {
                        --currentPage;
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                     }

                     return true;
                  }

                  if (mouseX >= (double)(sidebarX + 80) && mouseX < (double)(sidebarX + 105)) {
                     if (currentPage < maxPages - 1) {
                        ++currentPage;
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                     }

                     return true;
                  }
               }

               return false;
            }
         }
      }
   }

   public static boolean mouseScrolled(double mouseX, double mouseY, double amountY) {
      if (sidebarW < 120) {
         return false;
      } else {
         if (mouseX >= (double)sidebarX && mouseX < (double)(sidebarX + sidebarW) && mouseY >= (double)sidebarY && mouseY < (double)(sidebarY + sidebarH)) {
            int maxPages = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / itemsPerPage);
            int scroll = (int)Math.signum(amountY);
            if (scroll > 0 && currentPage > 0) {
               --currentPage;
               return true;
            }

            if (scroll < 0 && currentPage < maxPages - 1) {
               ++currentPage;
               return true;
            }
         }

         return false;
      }
   }
}
