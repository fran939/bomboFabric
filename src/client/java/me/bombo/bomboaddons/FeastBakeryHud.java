package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FeastBakeryHud {
   private static final List<DetectedItem> DETECTED_ITEMS = new ArrayList();
   private static long lastRenderFrame = -1L;
   private static boolean lastInMenu = false;
   private static long lastScanTime = 0L;

   public static void onHudRender(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      long currentFrame = System.currentTimeMillis();
      if (currentFrame != lastRenderFrame || mc.screen instanceof HudMoveScreen) {
         lastRenderFrame = currentFrame;
         BomboConfig.Settings s = BomboConfig.get();
         if (s.feastBakeryHud) {
            LowestBinManager.ensureLoaded();
            boolean inMenu = false;
            Screen var7 = mc.screen;
            if (var7 instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> screen = (AbstractContainerScreen)var7;
               String title = screen.getTitle().getString();
               if (title.toLowerCase().contains("bakery")) {
                  inMenu = true;
                  if (!lastInMenu && s.debugMaster) {
                     Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Bakery GUI detected: §f" + title);
                  }

                  if (currentFrame - lastScanTime > 250L) {
                     scanMenu(screen);
                     lastScanTime = currentFrame;
                  }
               }
            }

            lastInMenu = inMenu;
            if (!inMenu && !(mc.screen instanceof HudMoveScreen)) {
               DETECTED_ITEMS.clear();
            } else {
               if (mc.screen instanceof HudMoveScreen && DETECTED_ITEMS.isEmpty()) {
                  DETECTED_ITEMS.add(new DetectedItem("FRESHLY_BAKED_TALISMAN", "Baked Talisman", 25));
                  DETECTED_ITEMS.add(new DetectedItem("POPCORN_RING", "Popcorn Ring", 125));
                  DETECTED_ITEMS.add(new DetectedItem("ENCHANTMENT_FEAST_1", "Enchanted Book (Feast I)", 500));
               }

               if (!DETECTED_ITEMS.isEmpty()) {
                  List<DetectedItem> toDraw = new ArrayList(DETECTED_ITEMS);
                  toDraw.sort((a, b) -> {
                     long pA = LowestBinManager.getCachedPrice(a.id);
                     long pB = LowestBinManager.getCachedPrice(b.id);
                     double cpkA = a.kernelCost > 0 ? (double)pA / (double)a.kernelCost : (double)0.0F;
                     double cpkB = b.kernelCost > 0 ? (double)pB / (double)b.kernelCost : (double)0.0F;
                     return Double.compare(cpkB, cpkA);
                  });
                  drawBakeryInfo(g, s.feastBakeryHudX, s.feastBakeryHudY, toDraw);
               }
            }
         }
      }
   }

   private static void scanMenu(AbstractContainerScreen<?> screen) {
      List<DetectedItem> newList = new ArrayList();
      AbstractContainerMenu menu = screen.getMenu();

      for(int i = 0; i < 54 && i < menu.slots.size(); ++i) {
         Slot slot = menu.getSlot(i);
         if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            int kernels = getKernelCost(stack);
            if (kernels > 0) {
               String id = SkyblockUtils.getInternalId(stack);
               String name = stack.getHoverName().getString();
               if (name.equals("Enchanted Book") || id.equals("ENCHANTED_BOOK")) {
                  for(Component line : SkyblockUtils.getLore(stack)) {
                     String text = line.getString().replaceAll("(?i)§.", "").trim();
                     if (text.startsWith("Feast ")) {
                        id = "ENCHANTMENT_FEAST_" + text.replace("Feast ", "").trim();
                        name = text;
                        break;
                     }
                  }
               }

               if (BomboConfig.get().debugMaster) {
                  Bomboaddons.sendMessage("§7[Debug] Bakery Item: §f" + name + " §7(ID: §b" + id + "§7, Cost: §e" + kernels + "§7)");
               }

               newList.add(new DetectedItem(id, name, kernels));
            }
         }
      }

      DETECTED_ITEMS.clear();
      DETECTED_ITEMS.addAll(newList);
   }

   private static int getKernelCost(ItemStack stack) {
      try {
         List<Component> lore = SkyblockUtils.getLore(stack);

         for(int i = 0; i < lore.size(); ++i) {
            String line = ((Component)lore.get(i)).getString().replaceAll("(?i)§.", "").trim();
            if (line.toLowerCase().startsWith("cost") && i + 1 < lore.size()) {
               String nextLine = ((Component)lore.get(i + 1)).getString().replaceAll("(?i)§.", "").trim();
               if (nextLine.toLowerCase().contains("kernels")) {
                  try {
                     String costStr = nextLine.toLowerCase().replace("kernels", "").trim().replace(",", "");
                     return Integer.parseInt(costStr);
                  } catch (NumberFormatException var7) {
                     String digits = nextLine.replaceAll("[^0-9]", "");
                     if (!digits.isEmpty()) {
                        return Integer.parseInt(digits);
                     }
                  }
               }
            }
         }
      } catch (Exception var8) {
      }

      return -1;
   }

   public static int getHudWidth() {
      return 185;
   }

   public static int getHudHeight(int itemCount) {
      return (itemCount + 2) * 10 + 10;
   }

   public static void drawBakeryInfo(GuiGraphicsExtractor g, int x, int y, List<DetectedItem> items) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      int playerKernels = -1;
      int width = getHudWidth();
      int height = getHudHeight(items.size());
      g.pose().pushMatrix();
      g.pose().translate((float)x, (float)y);
      float scale = BomboConfig.get().feastBakeryHudScale;
      g.pose().scale(scale, scale);
      int opacity = BomboConfig.get().rngProfitHudOpacity;
      int alpha = (int)((double)opacity * 2.55);
      int bgColor = alpha << 24 | 0;
      if (opacity > 0) {
         g.fill(-5, -5, width + 5, height + 5, bgColor);
         g.outline(-5, -5, width + 10, height + 10, -1);
      }

      g.text(font, "§6§lFeast Bakery §r§7- §bScott", 0, 0, -1, true);
      g.fill(0, 11, width, 12, -1426063361);
      int curY = 18;

      for(DetectedItem item : items) {
         long itemPrice = LowestBinManager.getCachedPrice(item.id);
         if (itemPrice <= 0L) {
            if (item.id.startsWith("ENCHANTMENT_FEAST_")) {
               itemPrice = 4500000L;
            } else if (item.id.equals("BREAD_BOWL")) {
               itemPrice = 1500000L;
            }
         }

         double coinsPerKernel = item.kernelCost > 0 ? (double)itemPrice / (double)item.kernelCost : (double)0.0F;
         String priceColor = "§a";
         if (itemPrice <= 0L) {
            priceColor = "§7";
         } else if (coinsPerKernel < (double)5000.0F) {
            priceColor = "§c";
         } else if (coinsPerKernel < (double)10000.0F) {
            priceColor = "§e";
         }

         String displayName = item.name;
         if (item.id.startsWith("ENCHANTMENT_")) {
            String enchantName = item.id.replace("ENCHANTMENT_", "");
            String[] parts = enchantName.split("_");
            if (parts.length >= 2) {
               String var10000 = parts[0].substring(0, 1).toUpperCase();
               String prettyName = var10000 + parts[0].substring(1).toLowerCase();
               displayName = prettyName + " " + parts[1];
            }
         }

         String var29 = displayName.length() > 20 ? displayName.substring(0, 18) + ".." : displayName;
         String nameText = "§f" + var29;
         String valueText = itemPrice <= 0L ? "§8N/A" : priceColor + LowestBinManager.formatPrice((long)coinsPerKernel) + "§7/k";
         g.text(font, nameText, 0, curY, -1, true);
         int valueWidth = font.width(valueText.replaceAll("(?i)§.", ""));
         g.text(font, valueText, width - valueWidth, curY, -1, true);
         curY += 10;
      }

      g.fill(0, curY + 2, width, curY + 3, 1728053247);
      String bottomText = "§7Scott's Bakery";
      g.text(font, bottomText, width / 2 - font.width(bottomText.replaceAll("(?i)§.", "")) / 2, curY + 5, -1, true);
      g.pose().popMatrix();
   }

   public static class DetectedItem {
      public String id;
      public String name;
      public int kernelCost;

      public DetectedItem(String id, String name, int kernelCost) {
         this.id = id;
         this.name = name;
         this.kernelCost = kernelCost;
      }
   }
}
