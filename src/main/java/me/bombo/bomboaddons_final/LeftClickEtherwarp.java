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
               if (mc.getConnection() != null) {
                  sendSneakPacket(mc, false);
               }

               if (sneakKey != null) {
                  KeyMapping.set(sneakKey, false);
               }

               if (mc.options.keyShift != null) {
                  mc.options.keyShift.setDown(false);
               }

               reset();
            }

         }
      }
   }

   private static void reset() {
      state = 0;
      sneakKey = null;
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
                     boolean isSneaking = mc.options.keyShift.isDown();
                     if (isSneaking) {
                        if (mc.gameMode != null) {
                           mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                           mc.player.swing(InteractionHand.MAIN_HAND);
                        }
                     } else {
                        Key key = getSneakKey(mc);
                        if (key != null) {
                           KeyMapping.set(key, true);
                           mc.options.keyShift.setDown(true);
                           if (mc.getConnection() != null) {
                              sendSneakPacket(mc, true);
                           }

                           sneakKey = key;
                           state = 1;
                        }
                     }
                  }

               }
            }
         }
      }
   }

   private static Key getSneakKey(Minecraft mc) {
      try {
         Method[] var1 = KeyMapping.class.getMethods();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            Method m = var1[var3];
            if (m.getReturnType() == Key.class && m.getParameterCount() == 0) {
               Key k = (Key)m.invoke(mc.options.keyShift);
               if (k != null) {
                  return k;
               }
            }
         }

         Field f = KeyMapping.class.getDeclaredField("key");
         f.setAccessible(true);
         return (Key)f.get(mc.options.keyShift);
      } catch (Exception var6) {
         var6.printStackTrace();
         return null;
      }
   }

   private static void sendSneakPacket(Minecraft mc, boolean start) {
      try {
         Class<?> actionClass = Action.class;
         Object action = null;
         String target1 = start ? "PRESS_SHIFT_KEY" : "RELEASE_SHIFT_KEY";
         String target2 = start ? "START_SNEAKING" : "STOP_SNEAKING";
         Object[] var6 = actionClass.getEnumConstants();
         int var7 = var6.length;

         for(int var8 = 0; var8 < var7; ++var8) {
            Object o = var6[var8];
            String name = o.toString();
            if (name.equals(target1) || name.equals(target2)) {
               action = o;
               break;
            }
         }

         if (action != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, (Action)action));
         }
      } catch (Exception var11) {
         var11.printStackTrace();
      }

   }
}
