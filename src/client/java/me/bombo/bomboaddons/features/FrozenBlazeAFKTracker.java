package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
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
   private static int tickCounter = 0;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "fb_afk_hud"), FrozenBlazeAFKTracker::render);
   }

   public static boolean isHoldingRod(Minecraft mc) {
      if (mc.player == null) return false;
      ItemStack held = mc.player.getMainHandItem();
      if (held == null || held.isEmpty()) return false;
      if (held.getItem() instanceof net.minecraft.world.item.FishingRodItem) return true;
      String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().toLowerCase();
      if (path.contains("rod")) return true;
      String sbId = SkyblockUtils.getSkyblockId(held);
      return sbId != null && sbId.toUpperCase().contains("ROD");
   }

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

      // Check if player moved horizontally (jumping / Y axis doesn't count as moving)
      double dx = px - anchorX;
      double dz = pz - anchorZ;
      double distSq = dx * dx + dz * dz;

      if (distSq >= 0.25) {
         anchorX = px;
         anchorY = py;
         anchorZ = pz;
         lastMovedTime = now;
         pendingAlerts = 0;
         hasWarnedForCurrentAfk = false;
      } else {
         anchorY = py; // update Y so we track ground level without resetting AFK timer
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
         if (isWearingFrozenBlaze(mc) && (!s.fbWarnRequireRod || isHoldingRod(mc))) {
            pendingAlerts--;
            nextSubAlertTime = now + 400L;
            triggerWarning(mc, s);
         } else {
            pendingAlerts = 0;
            hasWarnedForCurrentAfk = false;
         }
      }
   }

   public static void drawTimerInfo(GuiGraphicsExtractor g, int x, int y, float scale, boolean preview) {
      Minecraft mc = Minecraft.getInstance();
      BomboConfig.Settings s = BomboConfig.get();
      String timerText;
      if (preview) {
         timerText = "§eAFK: §f00:30";
      } else {
         long now = System.currentTimeMillis();
         long elapsedMs = now - lastMovedTime;
         if (elapsedMs < 1000L) return;
         long totalSecs = elapsedMs / 1000L;
         long mins = totalSecs / 60;
         long secs = totalSecs % 60;
         String timeStr = String.format("%02d:%02d", mins, secs);
         boolean isWarning = totalSecs >= (s != null ? s.fbWarnSeconds : 60);
         timerText = (isWarning ? "§c§lAFK: " : "§eAFK: ") + "§f" + timeStr;
      }

      g.pose().pushMatrix();
      if (scale != 1.0F && scale > 0.0F) {
         g.pose().scale(scale, scale);
         g.text(mc.font, timerText, (int)((float)x / scale), (int)((float)y / scale), -1, true);
      } else {
         g.text(mc.font, timerText, x, y, -1, true);
      }
      g.pose().popMatrix();
   }

   public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.frozenBlazeWarning || !s.fbWarnTimerOnScreen) return;
      if (!isWearingFrozenBlaze(mc)) return;
      if (s.fbWarnRequireRod && !isHoldingRod(mc)) return;

      int x = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
      int y = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;
      float scale = s.fbWarnTimerScale > 0.0F ? s.fbWarnTimerScale : 1.0F;
      drawTimerInfo(g, x, y, scale, false);
   }

   private static String lastHeadDesc = "";
   private static String lastChestDesc = "";
   private static String lastLegsDesc = "";
   private static String lastFeetDesc = "";
   private static boolean cachedWearingFB = false;

   private static String getArmorPieceDesc(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return "None";
      String name = stack.getHoverName().getString();
      String sbId = SkyblockUtils.getSkyblockId(stack);
      return sbId.isEmpty() ? name : name + " (" + sbId + ")";
   }

   public static boolean isWearingFrozenBlaze(Minecraft mc) {
      if (mc.player == null) return false;
      ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
      ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
      ItemStack legs = mc.player.getItemBySlot(EquipmentSlot.LEGS);
      ItemStack feet = mc.player.getItemBySlot(EquipmentSlot.FEET);

      String curHeadDesc = getArmorPieceDesc(head);
      String curChestDesc = getArmorPieceDesc(chest);
      String curLegsDesc = getArmorPieceDesc(legs);
      String curFeetDesc = getArmorPieceDesc(feet);

      boolean headChanged = !curHeadDesc.equals(lastHeadDesc);
      boolean chestChanged = !curChestDesc.equals(lastChestDesc);
      boolean legsChanged = !curLegsDesc.equals(lastLegsDesc);
      boolean feetChanged = !curFeetDesc.equals(lastFeetDesc);

      if (headChanged || chestChanged || legsChanged || feetChanged) {
         if (BomboConfig.get().debugArmor) {
            if (headChanged) {
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo Debug§8] §eArmor Helmet swapped from: §f" + (lastHeadDesc.isEmpty() ? "None" : lastHeadDesc) + " §eto: §f" + curHeadDesc));
            }
            if (chestChanged) {
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo Debug§8] §eArmor Chestplate swapped from: §f" + (lastChestDesc.isEmpty() ? "None" : lastChestDesc) + " §eto: §f" + curChestDesc));
            }
            if (legsChanged) {
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo Debug§8] §eArmor Leggings swapped from: §f" + (lastLegsDesc.isEmpty() ? "None" : lastLegsDesc) + " §eto: §f" + curLegsDesc));
            }
            if (feetChanged) {
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo Debug§8] §eArmor Boots swapped from: §f" + (lastFeetDesc.isEmpty() ? "None" : lastFeetDesc) + " §eto: §f" + curFeetDesc));
            }
         }

         lastHeadDesc = curHeadDesc;
         lastChestDesc = curChestDesc;
         lastLegsDesc = curLegsDesc;
         lastFeetDesc = curFeetDesc;

         cachedWearingFB = isFrozenBlazePiece(head, EquipmentSlot.HEAD)
            && isFrozenBlazePiece(chest, EquipmentSlot.CHEST)
            && isFrozenBlazePiece(legs, EquipmentSlot.LEGS)
            && isFrozenBlazePiece(feet, EquipmentSlot.FEET);
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
