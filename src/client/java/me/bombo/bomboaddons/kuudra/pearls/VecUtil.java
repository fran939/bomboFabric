package me.bombo.bomboaddons.kuudra.pearls;

import net.minecraft.world.phys.Vec3;

public class VecUtil {
    public static Vec3 directionFromYawPitch(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians((double) yaw);
        float pitchRad = (float) Math.toRadians((double) pitch);
        return new Vec3(-Math.sin((double) yawRad) * Math.cos((double) pitchRad), -Math.sin((double) pitchRad), Math.cos((double) yawRad) * Math.cos((double) pitchRad));
    }

    public static Vec3 pointFromYawPitch(Vec3 pos, float yaw, float pitch, double distance) {
        return pos.add(directionFromYawPitch(yaw, pitch).scale(distance));
    }

    public static double distanceToSqr(Vec3 vec, double x, double y, double z) {
        double dx = vec.x - x;
        double dy = vec.y - y;
        double dz = vec.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
