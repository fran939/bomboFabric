package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class FrozenBlazeAFKTracker {
   private static double anchorX = 0;
   private static double anchorY = 0;
   private static double anchorZ = 0;
   private static long lastMovedTime = System.currentTimeMillis();
   private static boolean wasWearing = false;
   private static int pendingAlerts = 0;
   private static long nextSubAlertTime = 0;
   private static boolean hasWarnedForCurrentAfk = false;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "fb_afk_hud"), FrozenBlazeAFKTracker::render);
   }

   private static int tickCounter = 0;

   public static void onTick(Minecraft mc) {
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null) return;

      if (++tickCounter % 4 != 0 && pendingAlerts == 0) return;

      boolean wearing = isWearingFrozenBlaze(mc);
      if (!s.frozenBlazeWarning) return;

      if (!wearing) {
         wasWearing = false;
         pendingAlerts = 0;
         hasWarnedForCurrentAfk = false;
         return;
      }

      long now = System.currentTimeMillis();
      double px = mc.player.getX();
      double py = mc.player.getY();
      double pz = mc.player.getZ();

      if (!wasWearing) {
         wasWearing = true;
         anchorX = px;
         anchorY = py;
         anchorZ = pz;
         lastMovedTime = now;
         pendingAlerts = 0;
         hasWarnedForCurrentAfk = false;
         return;
      }

      // Check if player moved
      double dx = px - anchorX;
      double dy = py - anchorY;
      double dz = pz - anchorZ;
      double distSq = dx * dx + dy * dy + dz * dz;

      if (distSq >= 0.25) {
         anchorX = px;
         anchorY = py;
         anchorZ = pz;
         lastMovedTime = now;
         pendingAlerts = 0;
         hasWarnedForCurrentAfk = false;
      } else {
         long warnMs = (long) Math.max(1, s.fbWarnSeconds) * 1000L;
         if (now - lastMovedTime >= warnMs) {
            // Warn only 3 times per AFK stationary session, not repeatedly until movement
            if (!hasWarnedForCurrentAfk && pendingAlerts == 0) {
               hasWarnedForCurrentAfk = true;
               pendingAlerts = 3;
               nextSubAlertTime = now;
            }
         }
      }

      // Fire the 3 alert bursts spaced 400ms apart
      if (pendingAlerts > 0 && now >= nextSubAlertTime) {
         if (isWearingFrozenBlaze(mc)) {
            pendingAlerts--;
            nextSubAlertTime = now + 400L;
            triggerWarning(mc, s);
         } else {
            pendingAlerts = 0;
            hasWarnedForCurrentAfk = false;
         }
      }
   }

   public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.frozenBlazeWarning || !s.fbWarnTimerOnScreen) return;
      if (!isWearingFrozenBlaze(mc)) return;

      long now = System.currentTimeMillis();
      long elapsedMs = now - lastMovedTime;
      if (elapsedMs < 1000L) return;

      long totalSecs = elapsedMs / 1000L;
      long mins = totalSecs / 60;
      long secs = totalSecs % 60;
      String timeStr = String.format("%02d:%02d", mins, secs);

      boolean isWarning = totalSecs >= s.fbWarnSeconds;
      String timerText = (isWarning ? "§c§lAFK: " : "§eAFK: ") + "§f" + timeStr;

      int x = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
      int y = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;

      g.pose().pushMatrix();
      if (s.fbWarnTimerScale != 1.0F && s.fbWarnTimerScale > 0.0F) {
         g.pose().scale(s.fbWarnTimerScale, s.fbWarnTimerScale);
      }
      g.text(mc.font, timerText, x, y, -1, true);
      g.pose().popMatrix();
   }

   private static ItemStack lastHead = ItemStack.EMPTY;
   private static ItemStack lastChest = ItemStack.EMPTY;
   private static ItemStack lastLegs = ItemStack.EMPTY;
   private static ItemStack lastFeet = ItemStack.EMPTY;
   private static boolean cachedWearingFB = false;

   public static boolean isWearingFrozenBlaze(Minecraft mc) {
      if (mc.player == null) return false;
      ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
      ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
      ItemStack legs = mc.player.getItemBySlot(EquipmentSlot.LEGS);
      ItemStack feet = mc.player.getItemBySlot(EquipmentSlot.FEET);

      boolean changed = !ItemStack.matches(head, lastHead)
         || !ItemStack.matches(chest, lastChest)
         || !ItemStack.matches(legs, lastLegs)
         || !ItemStack.matches(feet, lastFeet);

      if (changed) {
         lastHead = head.copy();
         lastChest = chest.copy();
         lastLegs = legs.copy();
         lastFeet = feet.copy();

         cachedWearingFB = isFrozenBlazePiece(head, EquipmentSlot.HEAD)
            && isFrozenBlazePiece(chest, EquipmentSlot.CHEST)
            && isFrozenBlazePiece(legs, EquipmentSlot.LEGS)
            && isFrozenBlazePiece(feet, EquipmentSlot.FEET);

         if (BomboConfig.get().debugMode || BomboConfig.get().debugArmor) {
            String hName = head.isEmpty() ? "None" : head.getHoverName().getString();
            String cName = chest.isEmpty() ? "None" : chest.getHoverName().getString();
            String lName = legs.isEmpty() ? "None" : legs.getHoverName().getString();
            String fName = feet.isEmpty() ? "None" : feet.getHoverName().getString();
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo Debug§8] §eArmor swapped to: §f" + hName + "§7, §f" + cName + "§7, §f" + lName + "§7, §f" + fName + " §7(FB: " + (cachedWearingFB ? "§a4/4" : "§cNo") + "§7)"));
         }
      }
      return cachedWearingFB;
   }

   private static boolean isFrozenBlazePiece(ItemStack stack, EquipmentSlot slot) {
      if (stack == null || stack.isEmpty()) return false;
      
      // 1. Check CustomData / ExtraAttributes
      CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         String tagStr = customData.copyTag().toString().toUpperCase();
         if (tagStr.contains("FROZEN_BLAZE")) {
            return true;
         }
      }

      // 2. Check Display Name
      String name = stack.getHoverName().getString().toLowerCase();
      if (name.contains("frozen blaze")) {
         return true;
      }

      return false;
   }

   private static void triggerWarning(Minecraft mc, BomboConfig.Settings s) {
      if (s.fbWarnSound) {
         mc.execute(() -> {
            try {
               mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.5F));
               mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            } catch (Throwable ignored) {}
         });
      }
      if (s.fbWarnTitle && mc.gui != null) {
         mc.gui.setTitle(Component.literal("§c§lMOVE! §e(Frozen Blaze AFK)"));
         mc.gui.setSubtitle(Component.literal("§7Stationary for §e" + s.fbWarnSeconds + "s§7!"));
         mc.gui.setTimes(5, 25, 5);
      }
      if (s.fbWarnChat) {
         Component msg = Component.literal("§8[§3Bombo§8] §c§l[!] Frozen Blaze AFK Warning: §eMove at least 1 full block!");
         if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(msg);
         } else if (mc.player != null) {
            mc.player.sendSystemMessage(msg);
         }
      }
   }

   public static void reset() {
      wasWearing = false;
      lastMovedTime = System.currentTimeMillis();
      pendingAlerts = 0;
      hasWarnedForCurrentAfk = false;
   }
}
