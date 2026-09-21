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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class EquipmentHud {
   private static final int SLOT_SIZE = 18;
   private static final int PADDING = 2;

   // Equipment slots: Necklace (0), Cloak (1), Belt (2), Gloves/Bracelet (3)
   public static final ItemStack[] capturedEquipment = new ItemStack[]{
      ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
   };

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "equipment_hud"), EquipmentHud::render);
      loadSavedEquipment();
   }

   private static void loadSavedEquipment() {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         Minecraft mc = Minecraft.getInstance();
         if (s != null && s.savedEquipmentNbt != null && !s.savedEquipmentNbt.isEmpty() && mc.level != null) {
            net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, mc.level.registryAccess());
            for (int i = 0; i < Math.min(4, s.savedEquipmentNbt.size()); i++) {
               String snbt = s.savedEquipmentNbt.get(i);
               if (snbt != null && !snbt.trim().isEmpty() && !snbt.equals("{}")) {
                  try {
                     net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(snbt);
                     ItemStack stack = (ItemStack) ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
                     if (stack != null && !stack.isEmpty()) {
                        capturedEquipment[i] = stack;
                     }
                  } catch (Throwable ignored) {}
               }
            }
         }
      } catch (Throwable ignored) {}
   }

   private static void saveEquipmentToConfig() {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) return;
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) return;
         if (s.savedEquipmentNbt == null) s.savedEquipmentNbt = new java.util.ArrayList<>();
         s.savedEquipmentNbt.clear();
         net.minecraft.resources.RegistryOps<net.minecraft.nbt.Tag> ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, mc.level.registryAccess());
         for (int i = 0; i < 4; i++) {
            ItemStack stack = capturedEquipment[i];
            if (stack != null && !stack.isEmpty()) {
               try {
                  net.minecraft.nbt.Tag tag = (net.minecraft.nbt.Tag) ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                  s.savedEquipmentNbt.add(tag.toString());
               } catch (Throwable t) {
                  s.savedEquipmentNbt.add("");
               }
            } else {
               s.savedEquipmentNbt.add("");
            }
         }
         BomboConfig.save();
      } catch (Throwable ignored) {}
   }

   private static boolean isVertical() {
      return BomboConfig.get().equipmentHudVertical;
   }

   public static int getHudWidth() {
      return isVertical() ? SLOT_SIZE + PADDING * 2 : SLOT_SIZE * 4 + PADDING * 2;
   }

   public static int getHudHeight() {
      return isVertical() ? SLOT_SIZE * 4 + PADDING * 2 : SLOT_SIZE + PADDING * 2;
   }

   public static void updateEquipmentSlot(int index, ItemStack stack) {
      if (index >= 0 && index < 4) {
         if (stack == null || stack.isEmpty()) {
            capturedEquipment[index] = ItemStack.EMPTY;
         } else {
            String name = stack.getHoverName().getString().trim();
            if (name.equalsIgnoreCase("Empty Equipment Slot") || name.equalsIgnoreCase("Empty Slot") || name.isEmpty()) {
               capturedEquipment[index] = ItemStack.EMPTY;
            } else {
               capturedEquipment[index] = stack.copy();
            }
         }
         saveEquipmentToConfig();
      }
   }

   /** Returns true if the HUD should be rendered given the current screen. */
   public static boolean shouldShow(Minecraft mc) {
      if (mc.gui.screen() == null) return true;
      if (mc.gui.screen() instanceof ChatScreen) return true;
      if (mc.gui.screen() instanceof PauseScreen) return true;
      BomboConfig.Settings s = BomboConfig.get();
      if (mc.gui.screen() instanceof AbstractContainerScreen) {
         return s != null && s.equipmentHudShowInInventory;
      }
      return true;
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.options.keyToggleGui.isDown()) return;
      if (mc.gui.screen() instanceof HudMoveScreen) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.equipmentHud) return;

      if (shouldShow(mc)) {
         drawHud(g, s.equipmentHudX, s.equipmentHudY, s.equipmentHudScale, false);
      }
   }

   private static boolean hasLoadedFromConfig = false;

   public static void drawHud(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean isDummy) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null && !isDummy) return;

      if (!hasLoadedFromConfig && mc.level != null) {
         hasLoadedFromConfig = true;
         loadSavedEquipment();
      }

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

      ItemStack[] dummyStacks = new ItemStack[]{
         new ItemStack(Items.GOLDEN_CARROT),
         new ItemStack(Items.ELYTRA),
         new ItemStack(Items.LEATHER),
         new ItemStack(Items.CHAINMAIL_BOOTS)
      };

      for (int i = 0; i < 4; i++) {
         int slotX = vertical ? PADDING : PADDING + i * SLOT_SIZE;
         int slotY = vertical ? PADDING + i * SLOT_SIZE : PADDING;

         g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, s.getHudSlotBgColorArgb());
         g.outline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, s.getHudSlotBorderColorArgb());

         ItemStack stack = isDummy ? dummyStacks[i] : capturedEquipment[i];
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
      if (!s.equipmentHud || !s.equipmentHudShowInInventory || !s.equipmentHudShowTooltip) return;
      float scale = s.equipmentHudScale > 0.0F ? s.equipmentHudScale : 1.0F;
      renderHoverTooltip(g, s.equipmentHudX, s.equipmentHudY, scale);
   }

   private static void renderHoverTooltip(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && !s.equipmentHudShowTooltip) return;
      boolean vertical = isVertical();

      double mouseX = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
      double mouseY = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();

      for (int i = 0; i < 4; i++) {
         int slotScreenX = (int) (baseX + (vertical ? PADDING : PADDING + i * SLOT_SIZE) * scale);
         int slotScreenY = (int) (baseY + (vertical ? PADDING + i * SLOT_SIZE : PADDING) * scale);
         int slotScreenW = (int) (SLOT_SIZE * scale);
         int slotScreenH = (int) (SLOT_SIZE * scale);

         if (mouseX >= slotScreenX && mouseX <= slotScreenX + slotScreenW && mouseY >= slotScreenY && mouseY <= slotScreenY + slotScreenH) {
            ItemStack stack = capturedEquipment[i];
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
      if (!s.equipmentHud) return false;
      if (button != 0) return false;

      Minecraft mc = Minecraft.getInstance();
      if (mc.gui.screen() instanceof HudMoveScreen) return false;
      if (!shouldShow(mc) || mc.gui.screen() == null) return false;

      float scale = s.equipmentHudScale > 0.0F ? s.equipmentHudScale : 1.0F;
      int screenW = (int) (getHudWidth() * scale);
      int screenH = (int) (getHudHeight() * scale);

      if (mouseX >= s.equipmentHudX && mouseX <= s.equipmentHudX + screenW && mouseY >= s.equipmentHudY && mouseY <= s.equipmentHudY + screenH) {
         String action = s.equipmentHudClickAction != null ? s.equipmentHudClickAction.trim().toLowerCase() : "equipment";
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
