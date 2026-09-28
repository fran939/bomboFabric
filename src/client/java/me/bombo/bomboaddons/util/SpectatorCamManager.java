package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Manages camera orientation and look angles when spectating another player or entity.
 * Keeps the camera naturally synchronized to the spectated entity's head/body rotation
 * with smooth mouse freelook offsets.
 */
public class SpectatorCamManager {
    private static Entity lastCameraEntity = null;
    private static float offsetYaw = 0.0f;
    private static float offsetPitch = 0.0f;

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        Entity camEnt = mc.getCameraEntity();
        return camEnt != null && camEnt != mc.player;
    }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            reset();
            return;
        }
        Entity camEnt = mc.getCameraEntity();
        if (camEnt != null && camEnt != mc.player) {
            if (camEnt != lastCameraEntity) {
                lastCameraEntity = camEnt;
                resetOffsets();
            }
        } else {
            reset();
        }
    }

    public static void onMouseTurn(double accumulatedDX, double accumulatedDY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        double sens = mc.options.sensitivity().get();
        double d = sens * 0.6 + 0.2;
        double factor = d * d * d * 8.0 * 0.15 * 0.5;
        float dyaw = (float)(accumulatedDX * factor);
        float dpitch = (float)(accumulatedDY * factor);

        offsetYaw += dyaw;
        offsetPitch = Math.max(-90.0F, Math.min(90.0F, offsetPitch + dpitch));
    }

    public static float getYaw(float baseYaw) {
        if (isActive()) {
            return baseYaw + offsetYaw;
        }
        return baseYaw;
    }

    public static float getPitch(float basePitch) {
        if (isActive()) {
            return Math.max(-90.0F, Math.min(90.0F, basePitch + offsetPitch));
        }
        return basePitch;
    }

    public static void reset() {
        lastCameraEntity = null;
        resetOffsets();
    }

    public static void resetOffsets() {
        offsetYaw = 0.0f;
        offsetPitch = 0.0f;
    }
}
