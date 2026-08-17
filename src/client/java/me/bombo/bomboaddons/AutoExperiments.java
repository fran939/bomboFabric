package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoExperiments {
   private static String lastDetectedTitle = "";
   private static final HashMap<Integer, Integer> ultrasequencerOrder = new HashMap();
   private static final List<Integer> chronomatronOrder = new ArrayList(28);
   private static long lastClickTime = 0L;
   private static int lastGlowingSlot = -1;
   private static boolean noteInProgress = false;
   private static boolean hasAdded = false;
   private static boolean wasInInputPhase = false;
   private static boolean wasInShowingPhase = false;
   private static int lastAdded = -1;
   private static String debugStr = "None";
   private static int clicks = 0;
   private static final Random random = new Random();
   private static long tickCount = 0L;
   private static long lastDebugTime = 0L;
   private static String lastInventoryHash = "";

   public static String getDebugStr() {
      return debugStr;
   }

   public static String getDetectedTitle() {
      return lastDetectedTitle;
   }

   public static void reset() {
      ultrasequencerOrder.clear();
      chronomatronOrder.clear();
      hasAdded = false;
      wasInShowingPhase = false;
      wasInInputPhase = false;
      lastAdded = -1;
      lastGlowingSlot = -1;
      noteInProgress = false;
      clicks = 0;
   }

   public static void onTick() {
      ++tickCount;
      Minecraft mc = Minecraft.getInstance();
      BomboConfig.Settings config = BomboConfig.get();
      if (mc.player != null && !config.hideCheats && config.autoExperiments) {
         String title = mc.screen != null ? mc.screen.getTitle().getString().trim() : "";
         if (mc.screen == null) {
            if (!lastDetectedTitle.isEmpty()) {
               lastDetectedTitle = "";
               reset();
            }

            long var10000 = tickCount % 20L;
            debugStr = "Waiting for GUI... " + (var10000 < 10L ? "|" : "-");
         } else {
            String cleanTitle = title.replaceAll("(?i)§[0-9a-fk-or]", "");
            if (!cleanTitle.equals(lastDetectedTitle)) {
               if (lastDetectedTitle.contains("Chronomatron") != cleanTitle.contains("Chronomatron") || lastDetectedTitle.contains("Ultrasequencer") != cleanTitle.contains("Ultrasequencer") || cleanTitle.contains("Stakes") || lastDetectedTitle.contains("Stakes") || cleanTitle.contains("Experiment Over")) {
                  reset();
               }

               lastDetectedTitle = cleanTitle;
            }

            Screen screen = mc.screen;
            if (screen instanceof AbstractContainerScreen && !cleanTitle.contains("Stakes") && !cleanTitle.contains("Table")) {
               AbstractContainerScreen<?> acs = (AbstractContainerScreen)screen;
               int timerSlot = findTimerSlot(acs.getMenu().slots);
               if (System.currentTimeMillis() - lastDebugTime > 1000L) {
                  String timerInfo = timerSlot != -1 ? ((Slot)acs.getMenu().slots.get(timerSlot)).getItem().getItem().toString() : "NONE";
                  debugStr = String.format("Timer: %s | Mode: %s", timerInfo, cleanTitle.contains("Chron") ? "Chrono" : "Ultra");
                  lastDebugTime = System.currentTimeMillis();
               }

               if (cleanTitle.contains("Chronomatron")) {
                  solveChronomatron(mc, acs);
               } else if (cleanTitle.contains("Ultrasequencer")) {
                  solveUltraSequencer(mc, acs);
               }
            } else if (cleanTitle.contains("Stakes") || cleanTitle.contains("Table")) {
               debugStr = "In Menu (Ignored)";
            }
         }
      }

   }

   private static String getInventoryHash(List<Slot> slots) {
      StringBuilder sb = new StringBuilder();

      for(int i = 0; i < 54 && i < slots.size(); ++i) {
         ItemStack stack = ((Slot)slots.get(i)).getItem();
         sb.append(stack.getItem().toString()).append(stack.getCount()).append(hasGlint(stack));
      }

      return sb.toString();
   }

   private static boolean hasGlint(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.hasFoil() || stack.getComponents().has(DataComponents.ENCHANTMENTS) || stack.getComponents().has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
      }
   }

   private static void solveChronomatron(Minecraft mc, AbstractContainerScreen<?> screen) {
      BomboConfig.Settings config = BomboConfig.get();
      List<Slot> slots = screen.getMenu().slots;
      int timerSlot = findTimerSlot(slots);
      if (timerSlot != -1) {
         ItemStack timerStack = ((Slot)slots.get(timerSlot)).getItem();
         boolean isInputPhase = isClock(timerStack);
         boolean isShowingPhase = isGlowstone(timerStack) || isGlowItem(timerStack);
         boolean terracottaFound = false;
         int currentFlashedSlot = -1;

         for(int i = 0; i < slots.size() && i < 54; ++i) {
            Slot slot = (Slot)slots.get(i);
            if (slot.container != mc.player.getInventory() && i != timerSlot) {
               ItemStack stack = slot.getItem();
               String currentItem = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
               boolean isFlashMaterial = currentItem.contains("terracotta") || currentItem.contains("wool") || currentItem.contains("concrete") || currentItem.contains("glowstone");
               if (isFlashMaterial && isNoteItem(stack)) {
                  terracottaFound = true;
                  currentFlashedSlot = i;
                  break;
               }
            }
         }

         if (terracottaFound) {
            if (!noteInProgress) {
               noteInProgress = true;
               lastGlowingSlot = currentFlashedSlot;
               if (config.debugMode) {
                  mc.player.sendSystemMessage(Component.literal("§7[Bombo] Chrono: Flash detected at slot " + currentFlashedSlot));
               }
            }
         } else {
            noteInProgress = false;
         }

         if (isInputPhase && !wasInInputPhase) {
            if (lastGlowingSlot != -1) {
               chronomatronOrder.add(lastGlowingSlot);
               if (config.debugMode) {
                  LocalPlayer var10000 = mc.player;
                  int var10001 = chronomatronOrder.size();
                  var10000.sendSystemMessage(Component.literal("§a[Bombo] Chrono: Note #" + var10001 + " captured: " + lastGlowingSlot));
               }

               lastGlowingSlot = -1;
            } else if (config.debugMode) {
               mc.player.sendSystemMessage(Component.literal("§c[Bombo] Chrono: No note captured this round!"));
            }

            clicks = 0;
            hasAdded = true;
            noteInProgress = false;
         }

         wasInInputPhase = isInputPhase;
         debugStr = String.format("Chrono | Phase: %s | Clicks: %d/%d | Glow: %d", isShowingPhase ? "SHOWING" : (isInputPhase ? "INPUT" : "WAIT"), clicks, chronomatronOrder.size(), lastGlowingSlot);
         if (isInputPhase && hasAdded && chronomatronOrder.size() > clicks) {
            long delay = (long)(config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               int slotIndex = (Integer)chronomatronOrder.get(clicks);
               clickSlot(mc, screen, slotIndex);
               if (config.debugMode) {
                  int var18 = clicks + 1;
                  mc.player.sendSystemMessage(Component.literal("§b[Bombo] Chrono Click #" + var18 + " (Slot " + slotIndex + ")"));
               }

               lastClickTime = System.currentTimeMillis();
               ++clicks;
               if (clicks >= chronomatronOrder.size()) {
                  hasAdded = false;
                  if (config.debugMode) {
                     mc.player.sendSystemMessage(Component.literal("§7[Bombo] Chrono: Round finished, waiting for next note..."));
                  }

                  int targetRound = config.experimentGetMaxXp ? 20 : 6 + config.experimentSerumCount;
                  if (config.experimentAutoClose && chronomatronOrder.size() >= targetRound && chronomatronOrder.size() > 2) {
                     mc.player.sendSystemMessage(Component.literal("§c[Bombo] Target round reached (" + targetRound + ")! Closing."));
                     mc.player.closeContainer();
                  }
               }
            }
         }
      }

   }

   private static void solveUltraSequencer(Minecraft mc, AbstractContainerScreen<?> screen) {
      BomboConfig.Settings config = BomboConfig.get();
      if (config.experimentGetMaxXp) {
         boolean var10000 = true;
      } else {
         int var16 = 9 - config.experimentSerumCount;
      }

      List<Slot> slots = screen.getMenu().slots;
      int timerSlot = findTimerSlot(slots);
      if (timerSlot != -1) {
         ItemStack timerStack = ((Slot)slots.get(timerSlot)).getItem();
         boolean isInputPhase = isClock(timerStack);
         boolean isShowingPhase = isGlowstone(timerStack) || isGlowItem(timerStack);
         debugStr = String.format("Ultra | Added: %s | Clicks: %d/%d | Timer: %s", hasAdded, clicks, ultrasequencerOrder.size(), timerStack.getItem().toString());
         if (isShowingPhase) {
            if (!wasInShowingPhase) {
               ultrasequencerOrder.clear();
               if (config.debugMode) {
                  mc.player.sendSystemMessage(Component.literal("§7[Bombo] Ultra: Showing phase started, clearing map."));
               }
            }

            for(int i = 0; i < slots.size() && i < 54; ++i) {
               Slot slot = (Slot)slots.get(i);
               if (slot.container != mc.player.getInventory() && i != timerSlot) {
                  ItemStack stack = slot.getItem();
                  if (isNoteItem(stack)) {
                     int order = stack.getCount();
                     if (order > 0 && !ultrasequencerOrder.containsKey(order - 1)) {
                        ultrasequencerOrder.put(order - 1, i);
                        if (config.debugMode) {
                           mc.player.sendSystemMessage(Component.literal("§a[Bombo] Ultra: Captured dye #" + order + " at slot " + i));
                        }
                     }
                  }
               }
            }

            if (!ultrasequencerOrder.isEmpty()) {
               hasAdded = true;
               clicks = 0;
            }
         }

         wasInShowingPhase = isShowingPhase;
         if (isInputPhase && hasAdded && !ultrasequencerOrder.isEmpty() && ultrasequencerOrder.size() > clicks) {
            long delay = (long)(config.experimentClickDelay + (random.nextInt(41) - 20));
            if (delay < 0L) {
               delay = 0L;
            }

            if (System.currentTimeMillis() - lastClickTime > delay) {
               if (ultrasequencerOrder.containsKey(clicks)) {
                  int slotIndex = (Integer)ultrasequencerOrder.get(clicks);
                  clickSlot(mc, screen, slotIndex);
                  if (config.debugMode) {
                     int var10001 = clicks + 1;
                     mc.player.sendSystemMessage(Component.literal("§b[Bombo] Ultra Click #" + var10001 + " (Slot " + slotIndex + ")"));
                  }

                  lastClickTime = System.currentTimeMillis();
                  ++clicks;
                  if (clicks >= ultrasequencerOrder.size()) {
                     int targetRound = config.experimentGetMaxXp ? 20 : 2 + config.experimentSerumCount;
                     if (config.experimentAutoClose && ultrasequencerOrder.size() >= targetRound && ultrasequencerOrder.size() > 1) {
                        mc.player.sendSystemMessage(Component.literal("§c[Bombo] Target round reached (" + targetRound + ")! Closing."));
                        mc.player.closeContainer();
                     }
                  }
               } else {
                  ++clicks;
               }
            }
         }

      }
   }

   private static int findTimerSlot(List<Slot> slots) {
      Minecraft mc = Minecraft.getInstance();
      if (slots.size() > 49) {
         Slot s49 = (Slot)slots.get(49);
         if (s49.container != mc.player.getInventory()) {
            ItemStack item = s49.getItem();
            if (isClock(item) || isGlowstone(item) || isGlowItem(item)) {
               return 49;
            }
         }
      }

      for(int i = 0; i < slots.size() && i < 54; ++i) {
         Slot slot = (Slot)slots.get(i);
         if (slot.container != mc.player.getInventory()) {
            ItemStack stack = slot.getItem();
            if (isClock(stack) || isGlowstone(stack) || isGlowItem(stack)) {
               return i;
            }
         }
      }

      return -1;
   }

   private static void clickSlot(Minecraft mc, AbstractContainerScreen<?> screen, int slotIndex) {
      if (mc.gameMode != null && mc.player != null) {
         BomboConfig.Settings s = BomboConfig.get();
         int button = 0;
         ContainerInput type = ContainerInput.PICKUP;
         switch (s.experimentClickType) {
            case 1:
               button = 2;
               type = ContainerInput.CLONE;
               break;
            case 2:
               button = 0;
               type = ContainerInput.QUICK_MOVE;
         }

         mc.gameMode.handleContainerInput(screen.getMenu().containerId, slotIndex, button, type, mc.player);
      }

   }

   private static boolean isGlowstone(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.is(Items.GLOWSTONE_DUST) || stack.is(Items.GLOWSTONE);
      }
   }

   private static boolean isGlowItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.is(Items.SEA_LANTERN) || stack.is(Items.GLOW_INK_SAC) || stack.is(Items.GLOW_ITEM_FRAME) || stack.is(Items.BEACON) || stack.is(Items.MAGMA_BLOCK) || stack.is(Items.COMMAND_BLOCK) || stack.is(Items.REPEATING_COMMAND_BLOCK);
      }
   }

   private static boolean isClock(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         return stack.is(Items.CLOCK) || stack.is(Items.PLAYER_HEAD) || stack.is(Items.MAP) || stack.is(Items.FILLED_MAP);
      }
   }

   private static boolean isNoteItem(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
         if (path.contains("glass_pane")) {
            return false;
         } else {
            return path.contains("dye") || path.contains("stained_glass") || path.contains("skull") || path.contains("head") || path.contains("star") || path.contains("dust") || path.contains("terracotta") || path.contains("wool") || path.contains("concrete") || path.equals("bone_meal") || path.equals("ink_sac") || path.equals("lapis_lazuli") || path.equals("cocoa_beans") || path.equals("player_head");
         }
      }
   }

   private static boolean isDye(ItemStack stack) {
      return isNoteItem(stack);
   }
}
