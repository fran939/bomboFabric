package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Manages the camera orientation and look angles when spectating another player or entity.
 * Keeps the camera naturally synchronized to the spectated entity's head/body rotation
 * while allowing smooth mouse freelook offsets.
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
                offsetYaw = 0.0f; // reset offsets on new spectated target
                offsetPitch = 0.0f;
            }
        } else {
            reset();
        }
    }

    public static void onMouseTurn(double accumulatedDX, double accumulatedDY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double factor = f * f * f * 8.0 * 0.15;
        float dyaw = (float)(accumulatedDX * factor);
        float dpitch = (float)(accumulatedDY * factor);

        offsetYaw += dyaw;
        offsetPitch = Math.max(-90.0F, Math.min(90.0F, offsetPitch + dpitch));
    }

    public static float getYaw() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            Entity camEnt = mc.getCameraEntity();
            if (camEnt != null && camEnt != mc.player) {
                // Dynamically follow the spectated entity's look direction + mouse offset
                return camEnt.getYRot() + offsetYaw;
            }
        }
        return offsetYaw;
    }

    public static float getPitch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            Entity camEnt = mc.getCameraEntity();
            if (camEnt != null && camEnt != mc.player) {
                // Follow the entity's pitch and clamp within valid viewing angles
                return Math.max(-90.0F, Math.min(90.0F, camEnt.getXRot() + offsetPitch));
            }
        }
        return offsetPitch;
    }

    public static void reset() {
        lastCameraEntity = null;
        offsetYaw = 0.0f;
        offsetPitch = 0.0f;
    }
}
