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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorHud {
   private static final int SLOT_SIZE = 18;
   private static final int PADDING = 2;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "armor_hud"), ArmorHud::render);
   }

   private static boolean isVertical() {
      return BomboConfig.get().armorHudVertical;
   }

   public static int getHudWidth() {
      return isVertical() ? SLOT_SIZE + PADDING * 2 : SLOT_SIZE * 4 + PADDING * 2;
   }

   public static int getHudHeight() {
      return isVertical() ? SLOT_SIZE * 4 + PADDING * 2 : SLOT_SIZE + PADDING * 2;
   }

   /** Returns true if the HUD should be rendered given the current screen. */
   public static boolean shouldShow(Minecraft mc) {
      if (mc.gui.screen() == null) return true;
      if (mc.gui.screen() instanceof ChatScreen) return true;
      if (mc.gui.screen() instanceof PauseScreen) return true;
      BomboConfig.Settings s = BomboConfig.get();
      if (mc.gui.screen() instanceof AbstractContainerScreen) {
         return s != null && s.armorHudShowInInventory;
      }
      return true;
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      if (mc.gui.screen() instanceof HudMoveScreen) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.armorHud) return;

      if (shouldShow(mc)) {
         drawHud(g, s.armorHudX, s.armorHudY, s.armorHudScale, false);
      }
   }

   public static void drawHud(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean isDummy) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null && !isDummy) return;

      if (scale <= 0.0F) scale = 1.0F;
      boolean vertical = isVertical();
      int w = getHudWidth();
      int h = getHudHeight();

      int screenW = mc.getWindow().getGuiScaledWidth();
      int screenH = mc.getWindow().getGuiScaledHeight();
      int effectiveW = (int) ((float) w * scale);
      int effectiveH = (int) ((float) h * scale);
      if (screenW > effectiveW) {
         baseX = Math.max(0, Math.min(baseX, screenW - effectiveW));
      }
      if (screenH > effectiveH) {
         baseY = Math.max(0, Math.min(baseY, screenH - effectiveH));
      }

      BomboConfig.Settings s = BomboConfig.get();

      g.pose().pushMatrix();
      g.pose().translate((float) baseX, (float) baseY);
      g.pose().scale(scale, scale);

      // Background plate
      g.fill(0, 0, w, h, s.getHudBgColorArgb());
      g.outline(0, 0, w, h, s.getHudBorderColorArgb());

      EquipmentSlot[] slots = new EquipmentSlot[]{
         EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
      };

      ItemStack[] dummyStacks = new ItemStack[]{
         new ItemStack(Items.DIAMOND_HELMET),
         new ItemStack(Items.DIAMOND_CHESTPLATE),
         new ItemStack(Items.DIAMOND_LEGGINGS),
         new ItemStack(Items.DIAMOND_BOOTS)
      };

      for (int i = 0; i < 4; i++) {
         int slotX = vertical ? PADDING : PADDING + i * SLOT_SIZE;
         int slotY = vertical ? PADDING + i * SLOT_SIZE : PADDING;

         g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, s.getHudSlotBgColorArgb());
         g.outline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, s.getHudSlotBorderColorArgb());

         ItemStack stack = isDummy ? dummyStacks[i] : mc.player.getItemBySlot(slots[i]);
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

      // Tooltip rendering when a screen is open
      if (!isDummy && shouldShow(mc) && mc.gui.screen() != null && !(mc.gui.screen() instanceof HudMoveScreen)) {
         renderHoverTooltip(g, baseX, baseY, scale);
      }
   }

   public static void renderHoverTooltipDirect(GuiGraphicsExtractor g) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.armorHud || !s.armorHudShowInInventory || !s.armorHudShowTooltip) return;
      float scale = s.armorHudScale > 0.0F ? s.armorHudScale : 1.0F;
      renderHoverTooltip(g, s.armorHudX, s.armorHudY, scale);
   }

   private static void renderHoverTooltip(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && !s.armorHudShowTooltip) return;
      boolean vertical = isVertical();

      double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
      double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

      EquipmentSlot[] slots = new EquipmentSlot[]{
         EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
      };

      for (int i = 0; i < 4; i++) {
         int slotScreenX = (int) (baseX + (vertical ? PADDING : PADDING + i * SLOT_SIZE) * scale);
         int slotScreenY = (int) (baseY + (vertical ? PADDING + i * SLOT_SIZE : PADDING) * scale);
         int slotScreenW = (int) (SLOT_SIZE * scale);
         int slotScreenH = (int) (SLOT_SIZE * scale);

         if (mouseX >= slotScreenX && mouseX <= slotScreenX + slotScreenW && mouseY >= slotScreenY && mouseY <= slotScreenY + slotScreenH) {
            ItemStack stack = mc.player.getItemBySlot(slots[i]);
            if (stack != null && !stack.isEmpty()) {
               List<net.minecraft.network.chat.Component> lines = stack.getTooltipLines(
                  net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                  mc.player,
                  net.minecraft.world.item.TooltipFlag.NORMAL
               );
               if (lines != null && !lines.isEmpty()) {
                  InventoryHud.drawCustomTooltip(g, mc, lines, (int) mouseX, (int) mouseY);
               }
            }
            break;
         }
      }
   }

   public static boolean onMouseClick(double mouseX, double mouseY, int button) {
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.armorHud) return false;
      if (button != 0) return false;

      Minecraft mc = Minecraft.getInstance();
      if (mc.gui.screen() instanceof HudMoveScreen) return false;
      if (!shouldShow(mc) || mc.gui.screen() == null) return false;

      float scale = s.armorHudScale > 0.0F ? s.armorHudScale : 1.0F;
      int screenW = (int) (getHudWidth() * scale);
      int screenH = (int) (getHudHeight() * scale);

      if (mouseX >= s.armorHudX && mouseX <= s.armorHudX + screenW && mouseY >= s.armorHudY && mouseY <= s.armorHudY + screenH) {
         String action = s.armorHudClickAction != null ? s.armorHudClickAction.trim().toLowerCase() : "wardrobe";
         if (action.isEmpty() || action.equals("none") || action.equals("nothing")) {
            return false;
         }
         if (action.equals("inventory") || action.equals("/inventory") || action.equals("inv")) {
            if (mc.player != null) {
               mc.setScreenAndShow(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
         } else {
            String cmd = action.startsWith("/") ? action.substring(1) : action;
            if (mc.player != null && mc.player.connection != null) {
               mc.player.connection.sendCommand(cmd);
            }
         }
         mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
         return true;
      }

      return false;
   }
}
