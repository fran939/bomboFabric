package me.bombo.bomboaddons;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class LeftClickEtherwarp {
   private static int state = 0;
   private static InputConstants.Key sneakKey = null;

   public static boolean isBusy() {
      return state != 0;
   }

   public static void onTick() {
      if (state != 0) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            reset();
         } else if (state == 1) {
            if (mc.gameMode != null) {
               mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
               mc.player.swing(InteractionHand.MAIN_HAND);
               GardenMacroDetector.recordWeaponUse();
            }

            state = 2;
         } else if (state == 2) {
            sendSneakPacket(mc, false);
            getSneakMapping(mc).setDown(false);
            reset();
         }
      }

   }

   private static void reset() {
      state = 0;
   }

   public static boolean isHoldingEtherwarp() {
      if (!BomboConfig.get().leftClickEtherwarp) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return false;
         } else {
            ItemStack heldItem = mc.player.getMainHandItem();
            if (heldItem.isEmpty()) {
               return false;
            } else {
               String itemName = heldItem.getHoverName().getString();
               return itemName.contains("Aspect of the Void") || itemName.contains("Aspect of the End");
            }
         }
      }
   }

   public static boolean onLeftClick() {
      if (isHoldingEtherwarp()) {
         Minecraft mc = Minecraft.getInstance();
         if (state == 0) {
            if (mc.options.keyShift.isDown()) {
               if (mc.gameMode != null) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  mc.player.swing(InteractionHand.MAIN_HAND);
                  GardenMacroDetector.recordWeaponUse();
               }
            } else {
               getSneakMapping(mc).setDown(true);
               sendSneakPacket(mc, true);
               state = 1;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static KeyMapping getSneakMapping(Minecraft mc) {
      return mc.options.keyShift;
   }

   private static void sendSneakPacket(Minecraft mc, boolean start) {
      if (mc.getConnection() != null && mc.player != null) {
         ServerboundPlayerCommandPacket.Action action = null;

         try {
            action = Action.valueOf(start ? "PRESS_SHIFT_KEY" : "RELEASE_SHIFT_KEY");
         } catch (Exception var6) {
            try {
               action = Action.valueOf(start ? "START_SNEAKING" : "STOP_SNEAKING");
            } catch (Exception var5) {
            }
         }

         if (action != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, action));
         }

      }
   }
}
