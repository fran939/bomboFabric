package me.bombo.bomboaddons.gui;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import me.bombo.bomboaddons.BlockHighlight;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockItemManager;
import me.bombo.bomboaddons.SlotHighlight;
import me.bombo.bomboaddons.features.StorageTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class GlobalStorageScreen extends Screen {
   private EditBox searchBox;
   private String currentCategory = "All";
   private List<AggregatedItem> filteredItems = new ArrayList();
   private List<AggregatedItem> allItems = new ArrayList();
   private static List<AggregatedItem> cachedAllItems = new ArrayList();
   private static long cachedStorageTime = -1L;
   private int scrollOffset = 0;
   private int itemsPerRow;
   private final int itemSize;
   private final int spacing;
   private int maxVisibleRows;
   private boolean isResizing;
   private double resizeStartX;
   private double resizeStartY;
   private int resizeStartCols;
   private int resizeStartRows;
   private List<TabInfo> tabs;
   private static final Map<String, ParsedItemInfo> nbtParseCache = new HashMap();
   private AggregatedItem hoveredItem;

   public GlobalStorageScreen() {
      super(Component.literal("Global Storage"));
      this.itemsPerRow = BomboConfig.get().storageGuiCols;
      this.itemSize = 18;
      this.spacing = 2;
      this.maxVisibleRows = BomboConfig.get().storageGuiRows;
      this.isResizing = false;
      this.resizeStartX = (double)0.0F;
      this.resizeStartY = (double)0.0F;
      this.resizeStartCols = 0;
      this.resizeStartRows = 0;
      this.tabs = new ArrayList();
      this.hoveredItem = null;
   }

   protected void init() {
      int panelW = 8 + this.itemsPerRow * 18 + 2 + 2 + 12 + 4;
      int panelH = 22 + this.maxVisibleRows * 18 + 2 + 6;
      int px = (this.width - panelW) / 2;
      int py = (this.height - panelH) / 2;
      this.searchBox = new EditBox(this.font, px + 9, py + 6, panelW - 18, 10, Component.literal("Search..."));
      this.searchBox.setMaxLength(64);
      this.searchBox.setBordered(false);
      this.searchBox.setResponder(this::updateSearch);
      this.addRenderableWidget(this.searchBox);
      this.tabs.clear();
      this.tabs.add(new TabInfo("All", new ItemStack(Items.COMPASS)));
      this.tabs.add(new TabInfo("Inventory", new ItemStack(Items.PLAYER_HEAD)));
      this.tabs.add(new TabInfo("Chest", new ItemStack(Items.CHEST)));
      this.tabs.add(new TabInfo("Ender Chest", new ItemStack(Items.ENDER_CHEST)));
      ItemStack bp = SkyblockItemManager.createSkyblockItem("GREATER_BACKPACK");
      this.tabs.add(new TabInfo("Backpack", bp != null ? bp : new ItemStack(Items.LEATHER)));
      this.tabs.add(new TabInfo("Vault", new ItemStack(Items.GOLD_BLOCK)));
      this.tabs.add(new TabInfo("Sack", new ItemStack(Items.GUNPOWDER)));
      this.tabs.add(new TabInfo("Armor", new ItemStack(Items.DIAMOND_CHESTPLATE)));
      this.aggregateItems();
      this.updateSearch(this.searchBox.getValue());
   }

   private void aggregateItems() {
      if (StorageTracker.lastUpdateTime == cachedStorageTime && !cachedAllItems.isEmpty()) {
         this.allItems = new ArrayList(cachedAllItems);
      } else {
         Minecraft mc = Minecraft.getInstance();
         RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
         Map<String, AggregatedItem> map = new HashMap();

         for(Map.Entry<String, Map<Integer, String>> containerEntry : StorageTracker.storageData.entrySet()) {
            String containerName = (String)containerEntry.getKey();

            for(String nbtStr : ((Map<Integer, String>)(Map<?, ?>)containerEntry.getValue()).values()) {
               if (nbtStr != null && !nbtStr.isEmpty()) {
                  try {
                     ParsedItemInfo info = (ParsedItemInfo)nbtParseCache.get(nbtStr);
                     if (info == null) {
                        CompoundTag tag;
                        if (nbtStr.startsWith("B64:")) {
                           byte[] decoded = Base64.getDecoder().decode(nbtStr.substring(4));
                           ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
                           tag = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap());
                        } else {
                           tag = TagParser.parseCompoundFully(nbtStr);
                        }

                        ItemStack stack = (ItemStack)ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
                        if (!stack.isEmpty() && stack.getItem() == Items.PLAYER_HEAD && !stack.has(DataComponents.PROFILE)) {
                           try {
                              CustomData cd = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
                              if (cd != null) {
                                 CompoundTag ct = cd.copyTag();
                                 if (ct.contains("SkullOwner")) {
                                    CompoundTag so = ct.getCompoundOrEmpty("SkullOwner");
                                    if (so.contains("Properties")) {
                                       CompoundTag props = so.getCompoundOrEmpty("Properties");
                                       if (props.contains("textures")) {
                                          ListTag textures = props.getListOrEmpty("textures");
                                          if (!textures.isEmpty()) {
                                             CompoundTag t0 = (CompoundTag)textures.get(0);
                                             if (t0.contains("Value")) {
                                                String skinVal = t0.getString("Value").orElse("");
                                                if (!skinVal.isEmpty()) {
                                                   ResolvableProfile rp = SkyblockItemManager.createProfile(skinVal, (String)null);
                                                   if (rp != null) {
                                                      stack.set(DataComponents.PROFILE, rp);
                                                   }
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              }
                           } catch (Exception var20) {
                           }
                        }

                        if (!stack.isEmpty()) {
                           info = new ParsedItemInfo();
                           info.stack = stack;
                           info.cleanName = ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                           info.id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                           info.name = stack.getHoverName().getString();
                           ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
                           if (lore != null) {
                              for(Component line : lore.lines()) {
                                 String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                                 if (clean.startsWith("Stored: ")) {
                                    String val = clean.substring(8).split("/")[0].replace(",", "").trim();

                                    try {
                                       info.sackStoredCount = Integer.parseInt(val);
                                    } catch (NumberFormatException var21) {
                                    }
                                    break;
                                 }
                              }
                           }

                           nbtParseCache.put(nbtStr, info);
                        }
                     }

                     if (info != null && info.stack.has(DataComponents.CUSTOM_DATA) && !info.cleanName.contains("Empty ") && !info.cleanName.contains("Locked") && !info.cleanName.equals("Back") && !info.cleanName.equals("Close") && !info.cleanName.contains(" Page") && !info.cleanName.equals("Go Back")) {
                        String groupKey = info.id + ":" + info.name;
                        int count = info.stack.getCount();
                        if ((containerName.contains("Sack") || containerName.equals("Inventory")) && info.sackStoredCount != -1) {
                           count = info.sackStoredCount;
                        }

                        ItemStack finalStack = info.stack;
                        AggregatedItem agg = (AggregatedItem)map.computeIfAbsent(groupKey, (k) -> new AggregatedItem(finalStack, nbtStr));
                        agg.addLocation(containerName, count);
                     }
                  } catch (Exception var22) {
                  }
               }
            }
         }

         this.allItems = new ArrayList(map.values());
         this.allItems.sort((a, b) -> Integer.compare(b.totalCount, a.totalCount));
         cachedAllItems = new ArrayList(this.allItems);
         cachedStorageTime = StorageTracker.lastUpdateTime;
      }
   }

   private void updateSearch(String query) {
      String lowerQuery = query.toLowerCase();
      this.filteredItems = (List)this.allItems.stream().filter((agg) -> {
         boolean categoryMatch = this.currentCategory.equals("All") || agg.locations.keySet().stream().anyMatch((loc) -> loc.toLowerCase().contains(this.currentCategory.toLowerCase()));
         boolean textMatch = lowerQuery.isEmpty() || agg.stack.getHoverName().getString().toLowerCase().contains(lowerQuery);
         if (!textMatch && !lowerQuery.isEmpty()) {
            ItemLore lore = (ItemLore)agg.stack.get(DataComponents.LORE);
            if (lore != null) {
               for(Component line : lore.lines()) {
                  if (line.getString().toLowerCase().contains(lowerQuery)) {
                     textMatch = true;
                     break;
                  }
               }
            }
         }

         return categoryMatch && textMatch;
      }).collect(Collectors.toList());
      this.scrollOffset = 0;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (scrollY > (double)0.0F && this.scrollOffset > 0) {
         --this.scrollOffset;
      } else if (scrollY < (double)0.0F) {
         int maxScroll = Math.max(0, (int)Math.ceil((double)this.filteredItems.size() / (double)this.itemsPerRow) - this.maxVisibleRows);
         if (this.scrollOffset < maxScroll) {
            ++this.scrollOffset;
         }
      }

      return true;
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      int panelW = 8 + this.itemsPerRow * 18 + 2 + 2 + 12 + 4;
      int panelH = 22 + this.maxVisibleRows * 18 + 2 + 6;
      int px = (this.width - panelW) / 2;
      int py = (this.height - panelH) / 2;
      if (event.x() >= (double)(px + panelW - 12) && event.x() <= (double)(px + panelW) && event.y() >= (double)(py + panelH - 12) && event.y() <= (double)(py + panelH)) {
         this.isResizing = true;
         this.resizeStartX = event.x();
         this.resizeStartY = event.y();
         this.resizeStartCols = this.itemsPerRow;
         this.resizeStartRows = this.maxVisibleRows;
         return true;
      } else {
         int tabWidth = 28;

         for(int i = 0; i < this.tabs.size(); ++i) {
            int tx = px + 28 * i;
            int ty = py - 28;
            boolean selected = this.currentCategory.equals(((TabInfo)this.tabs.get(i)).name);
            if (selected) {
               ty = py - 32;
            }

            int th = selected ? 32 : 28;
            if (event.x() >= (double)tx && event.x() < (double)(tx + tabWidth) && event.y() >= (double)ty && event.y() < (double)(ty + th)) {
               this.currentCategory = ((TabInfo)this.tabs.get(i)).name;
               this.updateSearch(this.searchBox.getValue());
               return true;
            }
         }

         if (this.hoveredItem != null && !this.hoveredItem.locations.isEmpty()) {
            boolean addedPos = false;
            BlockHighlight.targetChestPosList.clear();

            for(String targetLoc : this.hoveredItem.locations.keySet()) {
               String[] locCmd = StorageTracker.getDisplayLocAndCommand(targetLoc);
               String cmd = locCmd[1];
               if (!cmd.isEmpty()) {
                  if (cmd.startsWith("/")) {
                     cmd = cmd.substring(1);
                  }

                  this.minecraft.player.connection.sendCommand(cmd);
                  SlotHighlight.addTargetName(this.hoveredItem.stack.getHoverName().getString(), -1442775296);
                  addedPos = true;
                  break;
               }

               if (targetLoc.contains(" @ ")) {
                  try {
                     String coords = targetLoc.substring(targetLoc.indexOf(" @ ") + 3);
                     String[] parts = coords.split(",");
                     if (parts.length == 3) {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockHighlight.targetChestPosList.add(pos);

                        try {
                           BlockState state = this.minecraft.level.getBlockState(pos);
                           if (state.getBlock() instanceof ChestBlock) {
                              ChestType type = (ChestType)state.getValue(ChestBlock.TYPE);
                              if (type != ChestType.SINGLE) {
                                 Direction connectedDir = ChestBlock.getConnectedDirection(state);
                                 BlockHighlight.targetChestPosList.add(pos.relative(connectedDir));
                              }
                           }
                        } catch (Exception var22) {
                        }

                        addedPos = true;
                     }
                  } catch (Exception var23) {
                  }
               }
            }

            if (addedPos) {
               BlockHighlight.targetChestTime = System.currentTimeMillis();
               SlotHighlight.addTargetName(this.hoveredItem.stack.getHoverName().getString(), -1442775296);
            }

            this.minecraft.setScreen((Screen)null);
            return true;
         } else {
            return super.mouseClicked(event, handled);
         }
      }
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      if (this.isResizing) {
         int dCols = (int)Math.round((event.x() - this.resizeStartX) / (double)9.0F);
         int dRows = (int)Math.round((event.y() - this.resizeStartY) / (double)9.0F);
         this.itemsPerRow = Math.max(5, Math.min(24, this.resizeStartCols + dCols));
         this.maxVisibleRows = Math.max(3, Math.min(16, this.resizeStartRows + dRows));
         int panelW = 8 + this.itemsPerRow * 18 + 2 + 2 + 12 + 4;
         int panelH = 22 + this.maxVisibleRows * 18 + 2 + 6;
         int px = (this.width - panelW) / 2;
         int py = (this.height - panelH) / 2;
         this.searchBox.setX(px + 9);
         this.searchBox.setY(py + 6);
         this.searchBox.setWidth(panelW - 18);
         int maxScroll = Math.max(0, (int)Math.ceil((double)this.filteredItems.size() / (double)this.itemsPerRow) - this.maxVisibleRows);
         if (this.scrollOffset > maxScroll) {
            this.scrollOffset = maxScroll;
         }

         return true;
      } else {
         return super.mouseDragged(event, dragX, dragY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.isResizing) {
         this.isResizing = false;
         BomboConfig.get().storageGuiCols = this.itemsPerRow;
         BomboConfig.get().storageGuiRows = this.maxVisibleRows;
         BomboConfig.save();
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      int panelW = 8 + this.itemsPerRow * 18 + 2 + 2 + 12 + 4;
      int panelH = 22 + this.maxVisibleRows * 18 + 2 + 6;
      int px = (this.width - panelW) / 2;
      int py = (this.height - panelH) / 2;
      graphics.fill(0, 0, this.width, this.height, 1610612736);
      int tabWidth = 28;

      for(int i = 0; i < this.tabs.size(); ++i) {
         TabInfo tab = (TabInfo)this.tabs.get(i);
         boolean selected = this.currentCategory.equals(tab.name);
         int tx = px + 28 * i;
         int ty = py - 28;
         int th = selected ? 32 : 28;
         if (selected) {
            ty = py - 32;
         }

         graphics.fill(tx, ty, tx + tabWidth, ty + th, -11184811);
         graphics.fill(tx + 1, ty + 1, tx + tabWidth - 1, ty + th, selected ? -3618616 : -6710887);
         if (selected) {
            graphics.fill(tx + 1, ty + 1, tx + tabWidth - 1, ty + 2, -1118482);
            graphics.fill(tx + 1, ty + 1, tx + 2, ty + th, -1118482);
         }

         graphics.item(tab.icon, tx + 6, ty + 7);
      }

      graphics.fill(px, py, px + panelW, py + panelH, -7631989);
      graphics.fill(px + 7, py + 4, px + panelW - 4, py + 18, -13158601);
      graphics.fill(px + 8, py + 5, px + panelW - 5, py + 17, -16777216);
      int gx = px + 8;
      int gy = py + 22;
      int gw = this.itemsPerRow * 18 + 2;
      int gh = this.maxVisibleRows * 18 + 2;
      graphics.fill(gx - 1, gy - 1, gx + gw + 1, gy + gh + 1, -13158601);
      graphics.fill(gx, gy, gx + gw, gy + gh, -7631989);
      int scrollX = gx + gw + 2;
      int scrollH = this.maxVisibleRows * 18;
      graphics.fill(scrollX, gy, scrollX + 12, gy + scrollH, -13158601);
      graphics.fill(scrollX + 1, gy + 1, scrollX + 11, gy + scrollH - 1, -7631989);
      int startX = gx + 1;
      int startY = gy + 1;
      int row = 0;
      int col = 0;
      int startIndex = this.scrollOffset * this.itemsPerRow;
      this.hoveredItem = null;
      int hx = 0;
      int hy = 0;

      for(int i = startIndex; i < this.filteredItems.size() && row < this.maxVisibleRows; ++i) {
         AggregatedItem agg = (AggregatedItem)this.filteredItems.get(i);
         int x = startX + col * 18;
         int y = startY + row * 18;
         graphics.item(agg.renderStack, x + 1, y + 1);
         graphics.itemDecorations(this.font, agg.renderStack, x + 1, y + 1);
         if (agg.totalCount > 1) {
            String amt = this.formatAmount(agg.totalCount);
            graphics.text(this.font, amt, x + 17 - this.font.width(amt), y + 9, 16777215, true);
         }

         if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18) {
            graphics.fill(x + 1, y + 1, x + 18 - 1, y + 18 - 1, -2130706433);
            this.hoveredItem = agg;
            hx = x;
            hy = y;
         }

         ++col;
         if (col >= this.itemsPerRow) {
            col = 0;
            ++row;
         }
      }

      int totalRows = Math.max(1, (int)Math.ceil((double)this.filteredItems.size() / (double)this.itemsPerRow));
      int maxScroll = Math.max(0, totalRows - this.maxVisibleRows);
      int knobH = Math.max(12, (int)((double)this.maxVisibleRows / (double)totalRows * (double)scrollH));
      if (knobH > scrollH - 2) {
         knobH = scrollH - 2;
      }

      int knobY = gy + 1;
      if (maxScroll > 0) {
         knobY += (int)((double)this.scrollOffset / (double)maxScroll * (double)(scrollH - 2 - knobH));
      }

      graphics.fill(scrollX + 1, knobY, scrollX + 11, knobY + knobH, -3750202);
      if (this.hoveredItem != null) {
         List<Component> tooltip = new ArrayList();
         tooltip.add(this.hoveredItem.stack.getHoverName());
         ItemLore lore = (ItemLore)this.hoveredItem.stack.get(DataComponents.LORE);
         if (lore != null) {
            tooltip.addAll(lore.lines());
         }

         tooltip.add(Component.literal("§8----------------"));
         tooltip.add(Component.literal("§7Total Amount: §e" + this.hoveredItem.totalCount));
         tooltip.add(Component.literal("§8----------------"));

         for(Map.Entry<String, Integer> loc : this.hoveredItem.locations.entrySet()) {
            String[] locCmd = StorageTracker.getDisplayLocAndCommand((String)loc.getKey());
            String displayLoc = locCmd[0];
            tooltip.add(Component.literal("§7" + displayLoc + ": §a" + String.valueOf(loc.getValue())));
         }

         graphics.setTooltipForNextFrame(this.font, tooltip, Optional.empty(), hx, hy);
      }

      int rhx = px + panelW - 8;
      int rhy = py + panelH - 8;
      graphics.fill(rhx + 4, rhy + 4, rhx + 6, rhy + 6, -1);
      graphics.fill(rhx + 2, rhy + 6, rhx + 4, rhy + 8, -1);
      graphics.fill(rhx + 6, rhy + 2, rhx + 8, rhy + 4, -1);
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   private String formatAmount(int amount) {
      if (amount >= 1000000) {
         return String.format("%.1fM", (double)amount / (double)1000000.0F);
      } else {
         return amount >= 1000 ? String.format("%.1fk", (double)amount / (double)1000.0F) : String.valueOf(amount);
      }
   }

   private static class TabInfo {
      String name;
      ItemStack icon;

      TabInfo(String name, ItemStack icon) {
         this.name = name;
         this.icon = icon;
      }
   }

   private static class AggregatedItem {
      ItemStack stack;
      ItemStack renderStack;
      int totalCount = 0;
      Map<String, Integer> locations = new HashMap();
      String rawNbt;

      AggregatedItem(ItemStack stack, String nbt) {
         this.stack = stack.copy();
         this.renderStack = stack.copy();
         this.renderStack.setCount(1);
         this.rawNbt = nbt;
      }

      void addLocation(String container, int count) {
         this.totalCount += count;
         this.locations.put(container, (Integer)this.locations.getOrDefault(container, 0) + count);
      }
   }

   private static class ParsedItemInfo {
      ItemStack stack;
      String cleanName;
      String id;
      String name;
      int sackStoredCount = -1;
   }
}
