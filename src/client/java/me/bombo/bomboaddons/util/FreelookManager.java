package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;

public class FreelookManager {
    private static boolean active = false;
    private static float freelookYaw = 0.0f;
    private static float freelookPitch = 0.0f;
    private static CameraType originalCameraType = CameraType.FIRST_PERSON;

    public static boolean isFreelookActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (active) {
                toggleFreelook(false);
            }
            return false;
        }
        return active;
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
            return;
        }
        if (state == active) return;

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

    public static void onMouseTurn(double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        
        double sensitivity = mc.options.sensitivity().get();
        double modifier = sensitivity * 0.6 + 0.2;
        double scale = modifier * modifier * modifier * 8.0;

        double deltaYaw = dx * scale * 0.15;
        double deltaPitch = dy * scale * 0.15;

        if (mc.options.invertMouseY().get()) {
            deltaPitch = -deltaPitch;
        }

        freelookYaw += (float) deltaYaw;
        freelookPitch += (float) deltaPitch;

        // Clip pitch to prevent camera flipping upside down
        freelookPitch = Math.max(-90.0f, Math.min(90.0f, freelookPitch));
    }
}
