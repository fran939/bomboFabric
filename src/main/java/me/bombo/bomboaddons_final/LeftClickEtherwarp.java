package me.bombo.bomboaddons_final;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import com.mojang.blaze3d.platform.InputConstants.Key;

@Environment(EnvType.CLIENT)
public class LeftClickEtherwarp {
   private static int state = 0;
   private static Key sneakKey = null;

   public static void onTick() {
      if (state != 0) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            reset();
         } else {
            if (state == 1) {
               if (mc.gameMode != null) {
                  mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                  mc.player.swing(InteractionHand.MAIN_HAND);
               }
               state = 2;
            } else if (state == 2) {
               sendSneakPacket(mc, false);
               getSneakMapping(mc).setDown(false);
               reset();
            }
         }
      }
   }

   private static void reset() {
      state = 0;
   }

   public static void onLeftClick() {
      if (BomboConfig.get().leftClickEtherwarp) {
         if (state == 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               ItemStack heldItem = mc.player.getMainHandItem();
               if (!heldItem.isEmpty()) {
                  String itemName = heldItem.getHoverName().getString();
                  if (itemName.contains("Aspect of the Void") || itemName.contains("Aspect of the End")) {
                     if (mc.options.keyShift.isDown()) {
                        if (mc.gameMode != null) {
                           mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                           mc.player.swing(InteractionHand.MAIN_HAND);
                        }
                     } else {
                        getSneakMapping(mc).setDown(true);
                        sendSneakPacket(mc, true);
                        state = 1;
                     }
                  }
               }
            }
         }
      }
   }

    private static net.minecraft.client.KeyMapping getSneakMapping(Minecraft mc) {
        return mc.options.keyShift;
    }

    private static void sendSneakPacket(Minecraft mc, boolean start) {
        if (mc.getConnection() == null || mc.player == null) return;
        
        Action action = null;
        try {
            action = Action.valueOf(start ? "PRESS_SHIFT_KEY" : "RELEASE_SHIFT_KEY");
        } catch (Exception e) {
            try {
                action = Action.valueOf(start ? "START_SNEAKING" : "STOP_SNEAKING");
            } catch (Exception e2) {}
        }
        
        if (action != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, action));
        }
    }
}
