package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public class GardenMacroDetector {
   public static long lastTeleportWeaponUseTime = 0L;
   public static long lastWarpTpCommandTime = 0L;
   public static boolean expectingTeleport = false;
   private static Vec3 recordedPos = null;
   private static float recordedYaw = 0.0F;
   private static float recordedPitch = 0.0F;
   private static boolean checkingPacket = false;

   public static void onMovePlayerPacketHead() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         recordedPos = mc.player.position();
         recordedYaw = mc.player.getYRot();
         recordedPitch = mc.player.getXRot();
         checkingPacket = true;
      }

   }

   public static void onMovePlayerPacketTail() {
      if (checkingPacket) {
         checkingPacket = false;
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            BomboConfig.Settings s = BomboConfig.get();
            if (s.gardenMacroCheckDetector && s.gardenMovement && SkyblockUtils.isInGarden()) {
               long now = System.currentTimeMillis();
               boolean commandTp = expectingTeleport && now - lastWarpTpCommandTime < 10000L;
               boolean weaponTp = now - lastTeleportWeaponUseTime < 1500L;
               if (!commandTp && !weaponTp) {
                  double dist = mc.player.position().distanceTo(recordedPos);
                  float yawDiff = Math.abs(mc.player.getYRot() - recordedYaw);
                  float pitchDiff = Math.abs(mc.player.getXRot() - recordedPitch);
                  if (dist > (double)8.0F || yawDiff > 0.1F || pitchDiff > 0.1F) {
                     triggerMacroCheck(dist, yawDiff, pitchDiff);
                  }

               } else {
                  if (commandTp) {
                     expectingTeleport = false;
                  }

               }
            }
         }
      }
   }

   public static void recordWeaponUse() {
      lastTeleportWeaponUseTime = System.currentTimeMillis();
   }

   public static void recordCommandSend(String command) {
      if (command.startsWith("/")) {
         command = command.substring(1);
      }

      String lower = command.trim().toLowerCase();
      if (lower.startsWith("warp") || lower.startsWith("tp") || lower.startsWith("tptoplot") || lower.startsWith("plottp") || lower.startsWith("hub") || lower.startsWith("is") || lower.startsWith("island")) {
         lastWarpTpCommandTime = System.currentTimeMillis();
         expectingTeleport = true;
      }

   }

   private static void triggerMacroCheck(double dist, float yawDiff, float pitchDiff) {
      BomboConfig.Settings s = BomboConfig.get();
      Minecraft mc = Minecraft.getInstance();
      if (s.gardenMacroCheckStop) {
         s.gardenMovement = false;
         BomboConfig.save();
         if (mc.player != null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
         }

         GardenMovement.reset();
      }

      if (mc.player != null) {
         for(int i = 0; i < 5; ++i) {
            mc.player.sendSystemMessage(Component.literal("§c§l[BomboAddons] WARNING: SUDDEN TELEPORT/ROTATION DETECTED!"));
         }

         LocalPlayer var10000 = mc.player;
         String var10001 = String.format("%.2f", dist);
         var10000.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Details: Distance: " + var10001 + "m, YawDiff: " + String.format("%.2f", yawDiff) + "°, PitchDiff: " + String.format("%.2f", pitchDiff) + "°"));
      }

      mc.execute(() -> {
         mc.gui.setTimes(10, 100, 20);
         mc.gui.setTitle(Component.literal("§c§lMACRO CHECKED!"));
         mc.gui.setSubtitle(Component.literal("§eGarden Movement Alert"));
      });
      SoundEvent finalSound;
      switch (s.gardenMacroCheckSound != null ? s.gardenMacroCheckSound.toLowerCase() : "anvil") {
         case "pling" -> finalSound = (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value();
         case "wither" -> finalSound = SoundEvents.WITHER_DEATH;
         case "explode" -> finalSound = (SoundEvent)SoundEvents.GENERIC_EXPLODE.value();
         default -> finalSound = SoundEvents.ANVIL_LAND;
      }

      int soundCount = s.gardenMacroCheckSoundCount;
      int soundDelay = s.gardenMacroCheckSoundDelay;
      (new Thread(() -> {
         for(int i = 0; i < soundCount; ++i) {
            Minecraft.getInstance().execute(() -> {
               try {
                  Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(finalSound, 1.0F));
               } catch (Throwable var2) {
               }

            });

            try {
               Thread.sleep((long)soundDelay);
            } catch (InterruptedException var5) {
               break;
            }
         }

      })).start();
   }
}
