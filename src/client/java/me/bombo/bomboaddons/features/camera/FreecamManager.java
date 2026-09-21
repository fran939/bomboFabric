package me.bombo.bomboaddons.features.camera;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class FreecamManager {
   private static boolean freecamActive = false;
   private static double camX = 0.0;
   private static double camY = 0.0;
   private static double camZ = 0.0;
   private static float camYaw = 0.0f;
   private static float camPitch = 0.0f;
   private static float speed = 1.0f;

   public static boolean isFreecamActive() {
      if (freecamActive) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null || mc.level == null) {
            toggleFreecam(false);
            return false;
         }
      }
      return freecamActive;
   }

   public static double getCamX() { return camX; }
   public static double getCamY() { return camY; }
   public static double getCamZ() { return camZ; }
   public static float getCamYaw() { return camYaw; }
   public static float getCamPitch() { return camPitch; }

   public static void toggleFreecam(boolean state) {
      Minecraft mc = Minecraft.getInstance();
      if (state) {
         if (mc.player != null) {
            Vec3 eyePos = mc.player.getEyePosition();
            camX = eyePos.x;
            camY = eyePos.y;
            camZ = eyePos.z;
            camYaw = mc.player.getYRot();
            camPitch = mc.player.getXRot();
            freecamActive = true;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            Bomboaddons.sendMessage("§8[§3Bombo§8] §aFreecam Enabled §7(WASD to fly, Space/Shift for up/down)");
         }
      } else {
         if (freecamActive) {
            freecamActive = false;
            if (mc.player != null) {
               mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            }
            Bomboaddons.sendMessage("§8[§3Bombo§8] §cFreecam Disabled");
         }
      }
   }

   public static void toggleFreecam() {
      toggleFreecam(!freecamActive);
   }

   public static void onMouseTurn(double deltaX, double deltaY) {
      if (!isFreecamActive()) return;
      Minecraft mc = Minecraft.getInstance();
      double sens = mc.options.sensitivity().get();
      double f = sens * 0.6 + 0.2;
      double factor = f * f * f * 8.0 * 0.15;
      double dy = deltaY;
      if (mc.options.invertMouseY().get()) {
         dy = -dy;
      }
      camYaw += (float)(deltaX * factor);
      camPitch += (float)(dy * factor);
      camPitch = Math.max(-90.0f, Math.min(90.0f, camPitch));
   }

   public static void onClientTick() {
      if (!isFreecamActive()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      // Calculate camera movement direction vector based on input keys
      double forward = 0;
      double strafe = 0;
      double vertical = 0;

      if (mc.options.keyUp.isDown()) forward += 1;
      if (mc.options.keyDown.isDown()) forward -= 1;
      if (mc.options.keyLeft.isDown()) strafe -= 1;
      if (mc.options.keyRight.isDown()) strafe += 1;
      if (mc.options.keyJump.isDown()) vertical += 1;
      if (mc.options.keyShift.isDown()) vertical -= 1;

      if (forward != 0 || strafe != 0 || vertical != 0) {
         float yawRad = (float) Math.toRadians(camYaw);
         float pitchRad = (float) Math.toRadians(camPitch);

         double moveSpeed = speed * (mc.options.keySprint.isDown() ? 2.5 : 0.8);

         double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
         double forwardY = -Math.sin(pitchRad);
         double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);

         double strafeX = -Math.cos(yawRad);
         double strafeZ = -Math.sin(yawRad);

         camX += (forward * forwardX + strafe * strafeX) * moveSpeed;
         camY += (forward * forwardY + vertical) * moveSpeed;
         camZ += (forward * forwardZ + strafe * strafeZ) * moveSpeed;
      }
   }
}
