package me.bombo.bomboaddons.features.hud;

import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InventoryHud {
   private static final int COLS = 9;
   private static final int ROWS = 3;
   private static final int SLOT_SIZE = 18;
   private static final int PADDING = 2;
   private static final int TOTAL_WIDTH = COLS * SLOT_SIZE + PADDING * 2;
   private static final int TOTAL_HEIGHT = ROWS * SLOT_SIZE + PADDING * 2;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "inventory_hud"), InventoryHud::render);
   }

   private static boolean isVertical() {
      BomboConfig.Settings s = BomboConfig.get();
      return s != null && s.inventoryHudVertical;
   }

   public static int getHudWidth() {
      return isVertical() ? (ROWS * SLOT_SIZE + PADDING * 2) : (COLS * SLOT_SIZE + PADDING * 2);
   }

   public static int getHudHeight() {
      return isVertical() ? (COLS * SLOT_SIZE + PADDING * 2) : (ROWS * SLOT_SIZE + PADDING * 2);
   }

   /** Returns true if the HUD should be rendered given the current screen. */
   public static boolean shouldShow(Minecraft mc) {
      if (mc.gui.screen() == null) return true;
      if (mc.gui.screen() instanceof ChatScreen) return true;
      if (mc.gui.screen() instanceof PauseScreen) return true;
      BomboConfig.Settings s = BomboConfig.get();
      if (mc.gui.screen() instanceof AbstractContainerScreen) {
         return s != null && s.inventoryHudShowInInventory;
      }
      return true;
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      if (mc.gui.screen() instanceof HudMoveScreen) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.inventoryHud) return;

      if (shouldShow(mc)) {
         drawHud(g, s.inventoryHudX, s.inventoryHudY, s.inventoryHudScale, false);
      }
   }

   public static void drawHud(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean isDummy) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null && !isDummy) return;

      if (scale <= 0.0F) scale = 1.0F;

      int screenW = mc.getWindow().getGuiScaledWidth();
      int screenH = mc.getWindow().getGuiScaledHeight();
      int currentW = getHudWidth();
      int currentH = getHudHeight();
      int effectiveW = (int) ((float) currentW * scale);
      int effectiveH = (int) ((float) currentH * scale);
      if (screenW > effectiveW) {
         baseX = Math.max(0, Math.min(baseX, screenW - effectiveW));
      }
      if (screenH > effectiveH) {
         baseY = Math.max(0, Math.min(baseY, screenH - effectiveH));
      }

      BomboConfig.Settings s = BomboConfig.get();
      boolean vertical = isVertical();

      g.pose().pushMatrix();
      g.pose().translate((float) baseX, (float) baseY);
      g.pose().scale(scale, scale);

      // Background plate
      g.fill(0, 0, currentW, currentH, s.getHudBgColorArgb());
      g.outline(0, 0, currentW, currentH, s.getHudBorderColorArgb());

      Inventory inv = mc.player != null ? mc.player.getInventory() : null;

      for (int i = 0; i < 27; i++) {
         int slotIndex = 9 + i; // Main inventory slots 9-35
         int col = vertical ? (i / 9) : (i % 9);
         int row = vertical ? (i % 9) : (i / 9);
         int slotX = PADDING + col * SLOT_SIZE;
         int slotY = PADDING + row * SLOT_SIZE;

         g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, s.getHudSlotBgColorArgb());
         g.outline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, s.getHudSlotBorderColorArgb());

         ItemStack stack = ItemStack.EMPTY;
         if (isDummy) {
            if (slotIndex % 4 == 0) stack = new ItemStack(Items.COAL);
            else if (slotIndex % 5 == 0) stack = new ItemStack(Items.IRON_INGOT);
            else if (slotIndex % 7 == 0) stack = new ItemStack(Items.GOLD_INGOT);
         } else if (inv != null && slotIndex < 36) {
            stack = inv.getItem(slotIndex);
         }

         if (stack != null && !stack.isEmpty()) {
            if (s != null && s.inventoryItemRarityBg) {
               String code = me.bombo.bomboaddons.SkyblockUtils.getItemRarityColor(stack);
               int rCol = switch (code) {
                  case "§a" -> 0x55FF55;
                  case "§9" -> 0x5555FF;
                  case "§5" -> 0xAA00AA;
                  case "§6" -> 0xFFAA00;
                  case "§d" -> 0xFF55FF;
                  case "§b" -> 0x55FFFF;
                  case "§c" -> 0xFF5555;
                  default -> 0;
               };
               if (rCol != 0) {
                  g.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0x44000000 | rCol);
               }
            }
            g.item(stack, slotX + 1, slotY + 1);
            g.itemDecorations(mc.font, stack, slotX + 1, slotY + 1);
         }
      }

      g.pose().popMatrix();

      // Tooltip rendering when screen is open
      if (!isDummy && shouldShow(mc) && mc.gui.screen() != null && !(mc.gui.screen() instanceof HudMoveScreen)) {
         renderHoverTooltip(g, baseX, baseY, scale);
      }
   }

   public static void renderHoverTooltipDirect(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.inventoryHud || !s.inventoryHudShowInInventory || !s.inventoryHudShowTooltip) return;
      float scale = s.inventoryHudScale > 0.0F ? s.inventoryHudScale : 1.0F;
      renderHoverTooltip(g, s.inventoryHudX, s.inventoryHudY, scale);
   }

   private static void renderHoverTooltip(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && !s.inventoryHudShowTooltip) return;

      double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
      double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

      Inventory inv = mc.player.getInventory();
      boolean vertical = isVertical();

      for (int i = 0; i < 27; i++) {
         int slotIndex = 9 + i;
         int col = vertical ? (i / 9) : (i % 9);
         int row = vertical ? (i % 9) : (i / 9);
         int slotScreenX = (int) (baseX + (PADDING + col * SLOT_SIZE) * scale);
         int slotScreenY = (int) (baseY + (PADDING + row * SLOT_SIZE) * scale);
         int slotScreenW = (int) (SLOT_SIZE * scale);
         int slotScreenH = (int) (SLOT_SIZE * scale);

         if (mouseX >= slotScreenX && mouseX <= slotScreenX + slotScreenW && mouseY >= slotScreenY && mouseY <= slotScreenY + slotScreenH) {
            ItemStack stack = inv.getItem(slotIndex);
            if (stack != null && !stack.isEmpty()) {
               List<net.minecraft.network.chat.Component> lines = stack.getTooltipLines(
                  net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                  mc.player,
                  net.minecraft.world.item.TooltipFlag.NORMAL
               );
               if (lines != null && !lines.isEmpty()) {
                  drawCustomTooltip(g, mc, lines, (int) mouseX, (int) mouseY);
               }
            }
            return;
         }
      }
   }

   public static void drawCustomTooltip(GuiGraphicsExtractor g, Minecraft mc, List<net.minecraft.network.chat.Component> lines, int x, int y) {
      if (lines == null || lines.isEmpty() || mc.font == null) return;
      int maxW = 0;
      for (net.minecraft.network.chat.Component line : lines) {
         int w = mc.font.width(line);
         if (w > maxW) maxW = w;
      }
      int tooltipW = maxW + 8;
      int tooltipH = lines.size() * 10 + 6;

      int screenW = mc.getWindow().getGuiScaledWidth();
      int screenH = mc.getWindow().getGuiScaledHeight();

      int tx = x + 12;
      int ty = y - 12;

      if (tx + tooltipW > screenW) {
         tx = x - tooltipW - 4;
      }
      if (ty + tooltipH > screenH) {
         ty = screenH - tooltipH - 4;
      }
      if (ty < 4) ty = 4;

      g.pose().pushMatrix();
      g.pose().translate(0.0F, 0.0F);

      // Hypixel dark rounded style
      g.fill(tx, ty, tx + tooltipW, ty + tooltipH, 0xF0100010);
      g.outline(tx, ty, tooltipW, tooltipH, 0x505000FF);

      int lineY = ty + 4;
      for (net.minecraft.network.chat.Component line : lines) {
         g.text(mc.font, line, tx + 4, lineY, 0xFFFFFFFF, true);
         lineY += 10;
      }

      g.pose().popMatrix();
   }
}
