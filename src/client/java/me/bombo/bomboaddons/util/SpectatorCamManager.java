package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public class SpectatorCamManager {
    private static Entity lastCameraEntity = null;
    private static float yaw = 0.0f;
    private static float pitch = 0.0f;

    public static boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        Entity camEnt = mc.getCameraEntity();
        return camEnt != null && camEnt != mc.player;
    }

    public static void update() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            lastCameraEntity = null;
            return;
        }
        Entity camEnt = mc.getCameraEntity();
        if (camEnt != null && camEnt != mc.player) {
            if (camEnt != lastCameraEntity) {
                lastCameraEntity = camEnt;
                yaw = camEnt.getYRot();
                pitch = camEnt.getXRot();
            }
        } else {
            lastCameraEntity = null;
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

        yaw += dyaw;
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + dpitch));
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static void reset() {
        lastCameraEntity = null;
    }
}
