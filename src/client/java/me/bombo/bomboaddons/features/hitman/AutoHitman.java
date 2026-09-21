package me.bombo.bomboaddons.features.hitman;

import java.util.Random;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AutoHitman {
   private static final Random RANDOM = new Random();

   public enum State {
      IDLE,
      OPENING_CF,
      IN_CF,
      IN_HITMAN,
      CLAIMED,
      CLOSING
   }

   private static State state = State.IDLE;
   private static long nextTriggerTimeMs = 0L;
   private static int stateTicks = 0;
   private static boolean manualRun = false;

   public static void init() {
      scheduleNextRun();
   }

   public static void scheduleNextRun() {
      int baseMinutes = BomboConfig.get().autoHitmanIntervalMinutes;
      if (baseMinutes <= 0) baseMinutes = 5;

      // Range approximately between 60% and 160% of baseMinutes
      // e.g. for 5 minutes: between 3.0 and 8.0 minutes
      double factor = 0.6 + (RANDOM.nextDouble() * 1.0);
      long delayMs = (long) (baseMinutes * 60_000L * factor);
      nextTriggerTimeMs = System.currentTimeMillis() + delayMs;
   }

   public static void triggerManual() {
      manualRun = true;
      state = State.IDLE;
      nextTriggerTimeMs = System.currentTimeMillis();
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §e[AutoHitman] Triggering manual Hitman claim..."));
      }
   }

   public static void tick(Minecraft mc) {
      if (mc == null || mc.player == null) {
         return;
      }

      boolean enabled = BomboConfig.get().autoHitman || manualRun;
      if (!enabled) {
         if (state != State.IDLE) {
            state = State.IDLE;
         }
         return;
      }

      stateTicks++;

      switch (state) {
         case IDLE -> {
            long now = System.currentTimeMillis();
            if (now >= nextTriggerTimeMs && nextTriggerTimeMs > 0L) {
               // Only trigger if in SkyBlock
               if (!SkyblockUtils.isConnectedToHypixel()) {
                  nextTriggerTimeMs = now + 15_000L;
                  return;
               }

               var currentScreen = mc.gui.screen();
               if (currentScreen != null && !(currentScreen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
                  // Wait until player is not in an active inventory
                  nextTriggerTimeMs = now + 5_000L;
                  return;
               }

               mc.player.connection.sendCommand("chocolatefactory");
               state = State.OPENING_CF;
               stateTicks = 0;
            }
         }

         case OPENING_CF -> {
            if (stateTicks > 100) { // 5s timeout
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §c[AutoHitman] Timeout waiting for Chocolate Factory screen."));
               resetAfterRun();
               return;
            }

            if (mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
               String title = ChatFormatting.stripFormatting(containerScreen.getTitle().getString());
               if (title.contains("Chocolate Factory")) {
                  state = State.IN_CF;
                  stateTicks = 0;
               }
            }
         }

         case IN_CF -> {
            // Give 20 ticks (1.0s) cooldown after opening CF before clicking Hitman
            if (stateTicks < 20) return;

            if (stateTicks > 120) { // 6s timeout
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §c[AutoHitman] Rabbit Hitman button not found in Chocolate Factory."));
               closeScreen(mc);
               resetAfterRun();
               return;
            }

            if (mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
               var menu = containerScreen.getMenu();
               int hitmanSlot = -1;

               // Look for "Rabbit Hitman" item (the bow)
               for (Slot slot : menu.slots) {
                  if (slot.container == mc.player.getInventory()) continue;
                  ItemStack stack = slot.getItem();
                  if (stack.isEmpty()) continue;

                  String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                  if (name.contains("Rabbit Hitman")) {
                     hitmanSlot = slot.index;
                     break;
                  }
               }

               if (hitmanSlot != -1 && mc.gameMode != null) {
                  mc.gameMode.handleContainerInput(menu.containerId, hitmanSlot, 0, ContainerInput.PICKUP, mc.player);
                  state = State.IN_HITMAN;
                  stateTicks = 0;
               }
            } else {
               resetAfterRun();
            }
         }

         case IN_HITMAN -> {
            // Give 20 ticks (1.0s) cooldown after opening Hitman screen before clicking Claim All
            if (stateTicks < 20) return;

            if (stateTicks > 120) { // 6s timeout
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §c[AutoHitman] Claim All button not found in Rabbit Hitman."));
               closeScreen(mc);
               resetAfterRun();
               return;
            }

            if (mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
               String title = ChatFormatting.stripFormatting(containerScreen.getTitle().getString());
               if (title.contains("Rabbit Hitman")) {
                  var menu = containerScreen.getMenu();
                  int claimSlot = -1;

                  for (Slot slot : menu.slots) {
                     if (slot.container == mc.player.getInventory()) continue;
                     ItemStack stack = slot.getItem();
                     if (stack.isEmpty()) continue;

                     String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                     if (name.contains("Claim All")) {
                        claimSlot = slot.index;
                        break;
                     }
                  }

                  if (claimSlot != -1 && mc.gameMode != null) {
                     mc.gameMode.handleContainerInput(menu.containerId, claimSlot, 0, ContainerInput.PICKUP, mc.player);
                     state = State.CLAIMED;
                     stateTicks = 0;
                  }
               }
            } else {
               resetAfterRun();
            }
         }

         case CLAIMED -> {
            // Wait 20 ticks (1.0s) after clicking claim all before closing
            if (stateTicks >= 20) {
               closeScreen(mc);
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §a[AutoHitman] Successfully claimed Rabbit Hitman eggs!"));
               state = State.CLOSING;
               stateTicks = 0;
            }
         }

         case CLOSING -> {
            if (stateTicks >= 5) {
               resetAfterRun();
            }
         }
      }
   }

   private static void closeScreen(Minecraft mc) {
      if (mc.player != null) {
         mc.player.closeContainer();
      }
   }

   private static void resetAfterRun() {
      manualRun = false;
      state = State.IDLE;
      stateTicks = 0;
      scheduleNextRun();
   }

   public static long getNextTriggerTimeMs() {
      return nextTriggerTimeMs;
   }

   public static State getState() {
      return state;
   }
}
