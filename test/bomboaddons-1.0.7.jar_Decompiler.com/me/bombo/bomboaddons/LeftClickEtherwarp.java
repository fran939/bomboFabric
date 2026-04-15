package me.bombo.bomboaddons;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_2848;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_2848.class_2849;
import net.minecraft.class_3675.class_306;

@Environment(EnvType.CLIENT)
public class LeftClickEtherwarp {
   private static int state = 0;
   private static class_306 sneakKey = null;

   public static void onTick() {
      if (state != 0) {
         class_310 mc = class_310.method_1551();
         if (mc.field_1724 == null) {
            reset();
         } else {
            if (state == 1) {
               if (mc.field_1761 != null) {
                  mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
                  mc.field_1724.method_6104(class_1268.field_5808);
               }

               state = 2;
            } else if (state == 2) {
               if (mc.method_1562() != null) {
                  sendSneakPacket(mc, false);
               }

               if (sneakKey != null) {
                  class_304.method_1416(sneakKey, false);
               }

               if (mc.field_1690.field_1832 != null) {
                  mc.field_1690.field_1832.method_23481(false);
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
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 != null) {
               class_1799 heldItem = mc.field_1724.method_6047();
               if (!heldItem.method_7960()) {
                  String itemName = heldItem.method_7964().getString();
                  if (itemName.contains("Aspect of the Void") || itemName.contains("Aspect of the End")) {
                     boolean isSneaking = mc.field_1690.field_1832.method_1434();
                     if (isSneaking) {
                        if (mc.field_1761 != null) {
                           mc.field_1761.method_2919(mc.field_1724, class_1268.field_5808);
                           mc.field_1724.method_6104(class_1268.field_5808);
                        }
                     } else {
                        class_306 key = getSneakKey(mc);
                        if (key != null) {
                           class_304.method_1416(key, true);
                           mc.field_1690.field_1832.method_23481(true);
                           if (mc.method_1562() != null) {
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

   private static class_306 getSneakKey(class_310 mc) {
      try {
         Method[] var1 = class_304.class.getMethods();
         int var2 = var1.length;

         for(int var3 = 0; var3 < var2; ++var3) {
            Method m = var1[var3];
            if (m.getReturnType() == class_306.class && m.getParameterCount() == 0) {
               class_306 k = (class_306)m.invoke(mc.field_1690.field_1832);
               if (k != null) {
                  return k;
               }
            }
         }

         Field f = class_304.class.getDeclaredField("key");
         f.setAccessible(true);
         return (class_306)f.get(mc.field_1690.field_1832);
      } catch (Exception var6) {
         var6.printStackTrace();
         return null;
      }
   }

   private static void sendSneakPacket(class_310 mc, boolean start) {
      try {
         Class<?> actionClass = class_2849.class;
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
            mc.method_1562().method_52787(new class_2848(mc.field_1724, (class_2849)action));
         }
      } catch (Exception var11) {
         var11.printStackTrace();
      }

   }
}
