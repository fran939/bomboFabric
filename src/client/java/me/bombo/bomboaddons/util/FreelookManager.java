package me.bombo.bomboaddons.util;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

public class FreelookManager {
   private static boolean active = false;
   private static float freelookYaw = 0.0F;
   private static float freelookPitch = 0.0F;
   private static CameraType originalCameraType;

   public static boolean isFreelookActive() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         return active;
      } else {
         if (active) {
            toggleFreelook(false);
         }

         return false;
      }
   }

   public static float getFreelookYaw() {
      return freelookYaw;
   }

   public static float getFreelookPitch() {
      return freelookPitch;
   }

   public static void toggleFreelook(boolean state) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         active = false;
      } else if (state != active) {
         active = state;
         if (active) {
            originalCameraType = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            freelookYaw = mc.player.getYRot();
            freelookPitch = mc.player.getXRot();
         } else {
            mc.options.setCameraType(originalCameraType);
         }

      }
   }

   public static void onMouseTurn(double dx, double dy) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.options != null) {
         double sensitivity = (Double)mc.options.sensitivity().get();
         double modifier = sensitivity * 0.6 + 0.2;
         double scale = modifier * modifier * modifier * (double)8.0F;
         double deltaYaw = dx * scale * 0.15;
         double deltaPitch = dy * scale * 0.15;
         if ((Boolean)mc.options.invertMouseY().get()) {
            deltaPitch = -deltaPitch;
         }

         freelookYaw += (float)deltaYaw;
         freelookPitch += (float)deltaPitch;
         freelookPitch = Math.max(-90.0F, Math.min(90.0F, freelookPitch));
      }
   }

   static {
      originalCameraType = CameraType.FIRST_PERSON;
   }
}
