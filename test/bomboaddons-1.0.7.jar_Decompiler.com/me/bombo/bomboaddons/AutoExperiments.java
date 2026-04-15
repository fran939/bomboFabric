package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_437;
import net.minecraft.class_465;

@Environment(EnvType.CLIENT)
public class AutoExperiments {
   private static HashMap<Integer, Integer> ultrasequencerOrder = new HashMap();
   private static ArrayList<Integer> chronomatronOrder = new ArrayList(28);
   private static long lastClickTime = 0L;
   private static boolean hasAdded = false;
   private static int lastAdded = 0;
   private static int clicks = 0;
   private static final Random random = new Random();

   public static void reset() {
      ultrasequencerOrder.clear();
      chronomatronOrder.clear();
      hasAdded = false;
      lastAdded = 0;
      clicks = 0;
   }

   public static void onTick() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null) {
         if (BomboConfig.get().autoExperiments) {
            if (mc.field_1755 == null) {
               reset();
            } else {
               String title = mc.field_1755.method_25440().getString();
               class_437 var3 = mc.field_1755;
               if (var3 instanceof class_465) {
                  class_465 screen = (class_465)var3;
                  if (title.contains("Chronomatron")) {
                     solveChronomatron(mc, screen);
                  } else if (title.contains("Ultrasequencer")) {
                     solveUltraSequencer(mc, screen);
                  } else {
                     reset();
                  }
               }

            }
         }
      }
   }

   private static void solveChronomatron(class_310 mc, class_465 screen) {
      BomboConfig.Settings config = BomboConfig.get();
      int maxRound = config.experimentGetMaxXp ? 15 : 11 - config.experimentSerumCount;
      List<class_1735> slots = screen.method_17577().field_7761;
      if (slots.size() > 49) {
         class_1799 timerStack = ((class_1735)slots.get(49)).method_7677();
         class_1792 timerItem = timerStack.method_7909();
         class_1799 lastAddedStack = slots.size() > lastAdded ? ((class_1735)slots.get(lastAdded)).method_7677() : class_1799.field_8037;
         if (timerItem == class_1802.field_8801 && !lastAddedStack.method_7958()) {
            if (config.experimentAutoClose && chronomatronOrder.size() > maxRound && mc.field_1724 != null) {
               mc.field_1724.method_7346();
            }

            hasAdded = false;
         }

         if (!hasAdded && timerItem == class_1802.field_8557) {
            for(int i = 10; i <= 43 && slots.size() > i; ++i) {
               class_1799 stack = ((class_1735)slots.get(i)).method_7677();
               if (stack.method_7958()) {
                  chronomatronOrder.add(i);
                  lastAdded = i;
                  hasAdded = true;
                  clicks = 0;
                  if (config.debugMode) {
                     mc.field_1724.method_7353(class_2561.method_43470("§a[Bombo] Chrono: Memorized NEW slot " + i), false);
                  }
                  break;
               }
            }
         }

         if (hasAdded && timerItem == class_1802.field_8557 && chronomatronOrder.size() > clicks) {
            long delay = (long)(config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               int slotIndex = (Integer)chronomatronOrder.get(clicks);
               if (config.debugMode) {
                  int var10001 = clicks + 1;
                  mc.field_1724.method_7353(class_2561.method_43470("§b[Bombo] Chrono: Clicking #" + var10001 + " -> Slot " + slotIndex), false);
               }

               clickSlot(mc, screen, slotIndex);
               lastClickTime = System.currentTimeMillis();
               ++clicks;
            }
         }

      }
   }

   private static void solveUltraSequencer(class_310 mc, class_465 screen) {
      BomboConfig.Settings config = BomboConfig.get();
      int maxRound = config.experimentGetMaxXp ? 20 : 9 - config.experimentSerumCount;
      List<class_1735> slots = screen.method_17577().field_7761;
      if (slots.size() > 49) {
         class_1799 timerStack = ((class_1735)slots.get(49)).method_7677();
         class_1792 timerItem = timerStack.method_7909();
         if (timerItem == class_1802.field_8557) {
            hasAdded = false;
         }

         int order;
         if (!hasAdded && timerItem == class_1802.field_8801) {
            if (slots.size() <= 44) {
               return;
            }

            if (((class_1735)slots.get(44)).method_7677().method_7960()) {
               return;
            }

            ultrasequencerOrder.clear();

            for(int i = 9; i <= 44 && slots.size() > i; ++i) {
               class_1799 stack = ((class_1735)slots.get(i)).method_7677();
               if (isDye(stack)) {
                  order = stack.method_7947();
                  ultrasequencerOrder.put(order - 1, i);
               }
            }

            hasAdded = true;
            clicks = 0;
            if (config.debugMode) {
               mc.field_1724.method_7353(class_2561.method_43470("§a[Bombo] Ultra: Memorized " + ultrasequencerOrder.size()), false);
            }

            if (ultrasequencerOrder.size() > maxRound && config.experimentAutoClose && mc.field_1724 != null) {
               mc.field_1724.method_7346();
            }
         }

         if (timerItem == class_1802.field_8557 && ultrasequencerOrder.containsKey(clicks)) {
            long delay = (long)(config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               order = (Integer)ultrasequencerOrder.get(clicks);
               if (config.debugMode) {
                  int var10001 = clicks + 1;
                  mc.field_1724.method_7353(class_2561.method_43470("§b[Bombo] Ultra: Clicking #" + var10001 + " -> Slot " + order), false);
               }

               clickSlot(mc, screen, order);
               lastClickTime = System.currentTimeMillis();
               ++clicks;
            }
         }

      }
   }

   private static boolean isDye(class_1799 stack) {
      String name = stack.method_7909().toString();
      if (name.endsWith("_dye")) {
         return true;
      } else {
         return name.contains("bone_meal") || name.contains("ink_sac") || name.contains("lapis_lazuli") || name.contains("cocoa_beans");
      }
   }

   private static void clickSlot(class_310 mc, class_465 screen, int slotIndex) {
      if (mc.field_1761 != null && mc.field_1724 != null) {
         mc.field_1761.method_2906(screen.method_17577().field_7763, slotIndex, 2, class_1713.field_7796, mc.field_1724);
      }

   }
}
