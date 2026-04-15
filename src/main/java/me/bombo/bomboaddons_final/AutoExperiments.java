package me.bombo.bomboaddons_final;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

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
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         if (BomboConfig.get().autoExperiments) {
            if (mc.screen == null) {
               reset();
            } else {
               String title = mc.screen.getTitle().getString();
               Screen var3 = mc.screen;
               if (var3 instanceof AbstractContainerScreen) {
                  AbstractContainerScreen screen = (AbstractContainerScreen) var3;
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

   private static void solveChronomatron(Minecraft mc, AbstractContainerScreen screen) {
      BomboConfig.Settings config = BomboConfig.get();
      int maxRound = config.experimentGetMaxXp ? 15 : 11 - config.experimentSerumCount;
      List<Slot> slots = screen.getMenu().slots;
      if (slots.size() > 49) {
         ItemStack timerStack = ((Slot) slots.get(49)).getItem();
         Item timerItem = timerStack.getItem();
         ItemStack lastAddedStack = slots.size() > lastAdded ? ((Slot) slots.get(lastAdded)).getItem()
               : ItemStack.EMPTY;
         if (timerItem == Items.GLOWSTONE && !lastAddedStack.hasFoil()) {
            if (config.experimentAutoClose && chronomatronOrder.size() > maxRound && mc.player != null) {
               mc.player.closeContainer();
            }

            hasAdded = false;
         }

         if (!hasAdded && timerItem == Items.CLOCK) {
            for (int i = 10; i <= 43 && slots.size() > i; ++i) {
               ItemStack stack = ((Slot) slots.get(i)).getItem();
               if (stack.hasFoil()) {
                  chronomatronOrder.add(i);
                  lastAdded = i;
                  hasAdded = true;
                  clicks = 0;
                  if (config.debugMode) {
                     mc.player.displayClientMessage(Component.literal("§a[Bombo] Chrono: Memorized NEW slot " + i),
                           false);
                  }
                  break;
               }
            }
         }

         if (hasAdded && timerItem == Items.CLOCK && chronomatronOrder.size() > clicks) {
            long delay = (long) (config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               int slotIndex = (Integer) chronomatronOrder.get(clicks);
               if (config.debugMode) {
                  int var10001 = clicks + 1;
                  mc.player.displayClientMessage(
                        Component.literal("§b[Bombo] Chrono: Clicking #" + var10001 + " -> Slot " + slotIndex), false);
               }

               clickSlot(mc, screen, slotIndex);
               lastClickTime = System.currentTimeMillis();
               ++clicks;
            }
         }

      }
   }

   private static void solveUltraSequencer(Minecraft mc, AbstractContainerScreen screen) {
      BomboConfig.Settings config = BomboConfig.get();
      int maxRound = config.experimentGetMaxXp ? 20 : 9 - config.experimentSerumCount;
      List<Slot> slots = screen.getMenu().slots;
      if (slots.size() > 49) {
         ItemStack timerStack = ((Slot) slots.get(49)).getItem();
         Item timerItem = timerStack.getItem();
         if (timerItem == Items.CLOCK) {
            hasAdded = false;
         }

         int order;
         if (!hasAdded && timerItem == Items.GLOWSTONE) {
            if (slots.size() <= 44) {
               return;
            }

            if (((Slot) slots.get(44)).getItem().isEmpty()) {
               return;
            }

            ultrasequencerOrder.clear();

            for (int i = 9; i <= 44 && slots.size() > i; ++i) {
               ItemStack stack = ((Slot) slots.get(i)).getItem();
               if (isDye(stack)) {
                  order = stack.getCount();
                  ultrasequencerOrder.put(order - 1, i);
               }
            }

            hasAdded = true;
            clicks = 0;
            if (config.debugMode) {
               mc.player.displayClientMessage(
                     Component.literal("§a[Bombo] Ultra: Memorized " + ultrasequencerOrder.size()), false);
            }

            if (ultrasequencerOrder.size() > maxRound && config.experimentAutoClose && mc.player != null) {
               mc.player.closeContainer();
            }
         }

         if (timerItem == Items.CLOCK && ultrasequencerOrder.containsKey(clicks)) {
            long delay = (long) (config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               order = (Integer) ultrasequencerOrder.get(clicks);
               if (config.debugMode) {
                  int var10001 = clicks + 1;
                  mc.player.displayClientMessage(
                        Component.literal("§b[Bombo] Ultra: Clicking #" + var10001 + " -> Slot " + order), false);
               }

               clickSlot(mc, screen, order);
               lastClickTime = System.currentTimeMillis();
               ++clicks;
            }
         }

      }
   }

   private static boolean isDye(ItemStack stack) {
      String name = stack.getItem().toString();
      if (name.endsWith("_dye")) {
         return true;
      } else {
         return name.contains("bone_meal") || name.contains("ink_sac") || name.contains("lapis_lazuli")
               || name.contains("cocoa_beans");
      }
   }

   private static void clickSlot(Minecraft mc, AbstractContainerScreen screen, int slotIndex) {
      if (mc.gameMode != null && mc.player != null) {
         mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slotIndex, 2, ClickType.CLONE, mc.player);
      }

   }
}
