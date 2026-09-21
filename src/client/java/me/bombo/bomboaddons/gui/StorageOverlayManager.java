package me.bombo.bomboaddons.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockItemManager;
import me.bombo.bomboaddons.features.StorageTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
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

public class StorageOverlayManager {
   public static boolean isOverlayOpen = false;
   public static EditBox searchBox = null;
   public static String searchQuery = "";
   public static String selectedStorageKey = null; // e.g. "Ender Chest (Page 1)" or "Backpack (Slot #1)"
   public static int scrollOffset = 0;
   
   // Fast cached parsed storage containers
   private static final Map<String, ContainerDisplayData> containerCache = new ConcurrentHashMap<>();
   private static long lastStorageUpdateTime = -1L;
   private static final Map<String, ItemStack> nbtStackCache = new ConcurrentHashMap<>();

   public static class ContainerDisplayData {
      public final String title;
      public final String type; // "Ender Chest", "Backpack", "Chest", etc.
      public final int number;
      public final Map<Integer, ItemStack> items = new HashMap<>();
      public final int maxSlot;

      public ContainerDisplayData(String title, String type, int number, int maxSlot) {
         this.title = title;
         this.type = type;
         this.number = number;
         this.maxSlot = maxSlot;
      }
   }

   public static boolean isStorageMenu(String title) {
      if (title == null) return false;
      String clean = ChatFormatting.stripFormatting(title).toLowerCase();
      return clean.contains("storage") || clean.contains("ender chest") || clean.contains("backpack") || clean.contains("vault");
   }

   public static void onContainerInit(AbstractContainerScreen<?> screen, int screenW, int screenH, int leftPos, int topPos, int imageW, int imageH) {
      if (!BomboConfig.get().storageOverlay) {
         isOverlayOpen = false;
         searchBox = null;
         return;
      }

      String title = screen.getTitle().getString();
      if (isStorageMenu(title)) {
         isOverlayOpen = true;
         refreshDataIfNeeded();

         int overlayW = 340;
         int overlayH = 240;
         int ox = (screenW - overlayW) / 2;
         int oy = (screenH - overlayH) / 2;

         searchBox = new EditBox(Minecraft.getInstance().font, ox + 10, oy + 8, 140, 16, Component.literal("Search items..."));
         searchBox.setValue(searchQuery);
         searchBox.setResponder(val -> {
            searchQuery = val;
            scrollOffset = 0;
         });
         searchBox.setBordered(true);
      } else {
         isOverlayOpen = false;
         searchBox = null;
      }
   }

   public static void refreshDataIfNeeded() {
      if (StorageTracker.lastUpdateTime != lastStorageUpdateTime || containerCache.isEmpty()) {
         lastStorageUpdateTime = StorageTracker.lastUpdateTime;
         containerCache.clear();

         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) return;
         RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());

         for (Map.Entry<String, Map<Integer, String>> entry : StorageTracker.storageData.entrySet()) {
            String title = entry.getKey();
            String lower = title.toLowerCase();
            String type = "Other";
            int num = 1;

            if (lower.contains("ender chest")) {
               type = "Ender Chest";
               num = extractNumber(title, 1);
            } else if (lower.contains("backpack")) {
               type = "Backpack";
               num = extractNumber(title, 1);
            } else if (lower.contains("vault")) {
               type = "Personal Vault";
               num = 1;
            } else if (lower.contains("chest")) {
               type = "Chest";
               num = extractNumber(title, 1);
            }

            int maxSlot = -1;
            ContainerDisplayData cData = new ContainerDisplayData(title, type, num, 54);

            for (Map.Entry<Integer, String> slotEntry : entry.getValue().entrySet()) {
               int slot = slotEntry.getKey();
               String nbtStr = slotEntry.getValue();
               if (nbtStr != null && !nbtStr.isEmpty()) {
                  ItemStack stack = parseItemStack(nbtStr, ops);
                  if (stack != null && !stack.isEmpty()) {
                     cData.items.put(slot, stack);
                     if (slot > maxSlot) maxSlot = slot;
                  }
               }
            }

            if (!cData.items.isEmpty() || type.equals("Ender Chest") || type.equals("Backpack")) {
               containerCache.put(title, cData);
            }
         }

         if (selectedStorageKey == null || !containerCache.containsKey(selectedStorageKey)) {
            // Default to Ender Chest 1 or Backpack 1
            if (containerCache.containsKey("Ender Chest (Page 1)")) {
               selectedStorageKey = "Ender Chest (Page 1)";
            } else if (!containerCache.isEmpty()) {
               selectedStorageKey = containerCache.keySet().iterator().next();
            }
         }
      }
   }

   private static int extractNumber(String text, int def) {
      Matcher m = Pattern.compile("(\\d+)").matcher(text);
      if (m.find()) {
         try {
            return Integer.parseInt(m.group(1));
         } catch (Exception ignored) {}
      }
      return def;
   }

   private static ItemStack parseItemStack(String nbtStr, RegistryOps<Tag> ops) {
      ItemStack cached = nbtStackCache.get(nbtStr);
      if (cached != null) return cached;
      try {
         CompoundTag tag;
         if (nbtStr.startsWith("B64:")) {
            byte[] decoded = Base64.getDecoder().decode(nbtStr.substring(4));
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            tag = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap());
         } else {
            tag = TagParser.parseCompoundFully(nbtStr);
         }
         ItemStack stack = ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
         if (!stack.isEmpty() && stack.getItem() == Items.PLAYER_HEAD && !stack.has(DataComponents.PROFILE)) {
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
                                 ResolvableProfile rp = SkyblockItemManager.createProfile(skinVal, null);
                                 if (rp != null) stack.set(DataComponents.PROFILE, rp);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
         if (!stack.isEmpty()) {
            nbtStackCache.put(nbtStr, stack);
         }
         return stack;
      } catch (Exception e) {
         return ItemStack.EMPTY;
      }
   }

   public static void renderOverlay(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
      if (!isOverlayOpen || !BomboConfig.get().storageOverlay) return;

      int overlayW = 340;
      int overlayH = 240;
      int ox = (screenW - overlayW) / 2;
      int oy = (screenH - overlayH) / 2;

      // Dark sleek backdrop & outline (Skyblocker style)
      g.fill(ox, oy, ox + overlayW, oy + overlayH, 0xF811141B);
      g.outline(ox, oy, overlayW, overlayH, 0xFF3C4452);
      g.outline(ox + 1, oy + 1, overlayW - 2, overlayH - 2, 0xFF232832);

      // Search box
      if (searchBox != null) {
         searchBox.setX(ox + 10);
         searchBox.setY(oy + 8);
         searchBox.extractRenderState(g, mouseX, mouseY, 0);
      }

      // Title & page count
      String header = "§6§lStorage §8• §7" + containerCache.size() + " Containers";
      g.text(font, header, ox + 160, oy + 12, -1, true);

      // Close hint button on right
      int closeX = ox + overlayW - 22;
      int closeY = oy + 8;
      boolean closeHover = mouseX >= closeX && mouseX < closeX + 14 && mouseY >= closeY && mouseY < closeY + 14;
      g.fill(closeX, closeY, closeX + 14, closeY + 14, closeHover ? 0xFF992222 : 0xFF441818);
      g.text(font, "§c§l✕", closeX + 3, closeY + 3, -1, false);

      // Category / Tab buttons (EC 1-9, BP 1-18)
      int tabX = ox + 10;
      int tabY = oy + 28;
      int tabH = 15;
      int tabW = 34;

      // 1. Ender Chest row (1..9)
      g.text(font, "§d§lEnder Chests §8(Click to open)", tabX, tabY, -1, false);
      tabY += 10;
      for (int i = 1; i <= 9; i++) {
         int tx = tabX + (i - 1) * 36;
         int ty = tabY;
         String key = "Ender Chest (Page " + i + ")";
         boolean isSelected = key.equals(selectedStorageKey);
         boolean isHovered = mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < ty + tabH;
         int btnBg = isSelected ? 0xFF653F9E : (isHovered ? 0xFF452D6C : 0xFF221736);
         g.fill(tx, ty, tx + tabW, ty + tabH, btnBg);
         g.outline(tx, ty, tabW, tabH, isSelected ? 0xFFE5A6FF : 0xFF51387A);
         g.text(font, (isSelected ? "§e§l" : "§7") + "EC " + i, tx + 5, ty + 4, -1, false);
      }

      tabY += tabH + 6;

      // 2. Backpacks row (1..18, 2 rows of 9)
      g.text(font, "§6§lBackpacks §8(Click to open)", tabX, tabY, -1, false);
      tabY += 10;
      for (int i = 1; i <= 18; i++) {
         int row = (i - 1) / 9;
         int col = (i - 1) % 9;
         int tx = tabX + col * 36;
         int ty = tabY + row * (tabH + 2);
         String key = "Backpack (Slot #" + i + ")";
         boolean isSelected = key.equals(selectedStorageKey);
         boolean isHovered = mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < ty + tabH;
         int btnBg = isSelected ? 0xFF9E6524 : (isHovered ? 0xFF6B451B : 0xFF362410);
         g.fill(tx, ty, tx + tabW, ty + tabH, btnBg);
         g.outline(tx, ty, tabW, tabH, isSelected ? 0xFFFFC266 : 0xFF7A4F1F);
         g.text(font, (isSelected ? "§e§l" : "§7") + "BP " + i, tx + 5, ty + 4, -1, false);
      }

      // Container Slot Grid Area (9x6 grid = 54 slots)
      int gridX = ox + 10;
      int gridY = oy + 120;
      int slotSize = 18;

      g.fill(gridX - 2, gridY - 2, gridX + 9 * slotSize + 2, gridY + 6 * slotSize + 2, 0xFF14171E);
      g.outline(gridX - 2, gridY - 2, 9 * slotSize + 4, 6 * slotSize + 4, 0xFF3B4452);

      ContainerDisplayData activeData = selectedStorageKey != null ? containerCache.get(selectedStorageKey) : null;
      ItemStack hoveredOverlayStack = null;

      String lowerQ = searchQuery.toLowerCase().trim();

      for (int r = 0; r < 6; r++) {
         for (int c = 0; c < 9; c++) {
            int slotIdx = r * 9 + c;
            int sx = gridX + c * slotSize;
            int sy = gridY + r * slotSize;

            g.fill(sx, sy, sx + 16, sy + 16, 0xFF1C2028);
            g.outline(sx, sy, 16, 16, 0xFF2B323F);

            if (activeData != null && activeData.items.containsKey(slotIdx)) {
               ItemStack stack = activeData.items.get(slotIdx);
               if (stack != null && !stack.isEmpty()) {
                  // Search Highlight
                  boolean match = true;
                  if (!lowerQ.isEmpty()) {
                     String name = stack.getHoverName().getString().toLowerCase();
                     match = name.contains(lowerQ);
                     if (!match) {
                        ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
                        if (lore != null) {
                           for (Component line : lore.lines()) {
                              if (line.getString().toLowerCase().contains(lowerQ)) {
                                 match = true;
                                 break;
                              }
                           }
                        }
                     }
                  }

                  if (!match) {
                     // Darken non-matching
                     g.fill(sx, sy, sx + 16, sy + 16, 0xAA000000);
                  }

                  g.item(stack, sx, sy);
                  g.itemDecorations(font, stack, sx, sy);

                  if (match && !lowerQ.isEmpty()) {
                     g.outline(sx - 1, sy - 1, 18, 18, 0xFF55FF55);
                  }

                  if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                     g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
                     hoveredOverlayStack = stack;
                  }
               }
            }
         }
      }

      // Render Item Tooltip on top
      if (hoveredOverlayStack != null) {
         List<Component> tooltipLines = Screen.getTooltipFromItem(Minecraft.getInstance(), hoveredOverlayStack);
         g.setTooltipForNextFrame(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
      }
   }

   private static void openContainerCommand(String type, int num) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.connection != null) {
         if ("EC".equalsIgnoreCase(type)) {
            mc.player.connection.sendCommand("enderchest " + num);
         } else if ("BP".equalsIgnoreCase(type)) {
            mc.player.connection.sendCommand("backpack " + num);
         }
      }
   }

   public static boolean mouseClicked(MouseButtonEvent event, int screenW, int screenH) {
      if (!isOverlayOpen || !BomboConfig.get().storageOverlay) return false;

      int overlayW = 340;
      int overlayH = 240;
      int ox = (screenW - overlayW) / 2;
      int oy = (screenH - overlayH) / 2;

      double mouseX = event.x();
      double mouseY = event.y();

      if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY) && searchBox.mouseClicked(event, false)) {
         searchBox.setFocused(true);
         return true;
      } else if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
         searchBox.setFocused(true);
         return true;
      } else if (searchBox != null) {
         searchBox.setFocused(false);
      }

      // Close button
      int closeX = ox + overlayW - 22;
      int closeY = oy + 8;
      if (mouseX >= closeX && mouseX < closeX + 14 && mouseY >= closeY && mouseY < closeY + 14) {
         isOverlayOpen = false;
         return true;
      }

      // Check EC tabs
      int tabX = ox + 10;
      int tabY = oy + 38;
      int tabH = 15;
      int tabW = 34;
      for (int i = 1; i <= 9; i++) {
         int tx = tabX + (i - 1) * 36;
         int ty = tabY;
         if (mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < ty + tabH) {
            selectedStorageKey = "Ender Chest (Page " + i + ")";
            openContainerCommand("EC", i);
            return true;
         }
      }

      // Check BP tabs
      tabY = oy + 69;
      for (int i = 1; i <= 18; i++) {
         int row = (i - 1) / 9;
         int col = (i - 1) % 9;
         int tx = tabX + col * 36;
         int ty = tabY + row * (tabH + 2);
         if (mouseX >= tx && mouseX < tx + tabW && mouseY >= ty && mouseY < ty + tabH) {
            selectedStorageKey = "Backpack (Slot #" + i + ")";
            openContainerCommand("BP", i);
            return true;
         }
      }

      // If clicked inside overlay panel, consume click so it doesn't click chest behind
      if (mouseX >= ox && mouseX <= ox + overlayW && mouseY >= oy && mouseY <= oy + overlayH) {
         return true;
      }

      return false;
   }

   public static boolean keyPressed(KeyEvent event) {
      if (!isOverlayOpen || !BomboConfig.get().storageOverlay) return false;
      if (searchBox != null && searchBox.isFocused()) {
         if (event.key() == 256) { // ESC
            searchBox.setFocused(false);
            return true;
         }
         return searchBox.keyPressed(event);
      }
      return false;
   }
}
