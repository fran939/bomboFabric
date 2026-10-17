package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import me.bombo.bomboaddons.kuudra.pearls.Supply;
import me.bombo.bomboaddons.kuudra.pearls.SupplySpot;
import me.bombo.bomboaddons.kuudra.pearls.SupplyStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Kuudra Supplies Category Helper:
 * - Supply Waypoints: 3D waypoints for active supply locations.
 * - Supply Hitboxes: Renders custom hitboxes around supply crates and giant zombies.
 * - Best Aim Point: Calculates and highlights the optimal click point on supply zombies.
 * - Pickup Range Highlight: Visually signals when the player is within crate pickup reach (<= 3 blocks).
 * - Drag Hitbox: Renders circle indicating the maximum tether distance for dragging crates (5 blocks).
 */
public class KuudraSuppliesHelper {

    public static class SupplyTarget {
        public final String name;
        public final Vec3 pos;
        public final boolean isPlaced;
        public final double distance;
        public final boolean inPickupRange;

        public SupplyTarget(String name, Vec3 pos, boolean isPlaced, double distance, boolean inPickupRange) {
            this.name = name;
            this.pos = pos;
            this.isPlaced = isPlaced;
            this.distance = distance;
            this.inPickupRange = inPickupRange;
        }
    }

    public static List<SupplyTarget> getActiveSupplies() {
        List<SupplyTarget> targets = new ArrayList<>();
        if (!SkyblockUtils.isInKuudra()) return targets;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return targets;

        Vec3 playerPos = mc.player.position();

        Supply[] supplies = KuudraUtils.getSupplies();
        if (supplies != null) {
            for (Supply s : supplies) {
                SupplySpot spot = s.getSpot();
                Vec3 pos = spot != null ? spot.getLocation() : null;
                if (pos == null) continue;

                boolean isPlaced = s.getStatus() == SupplyStatus.COMPLETED;
                double dist = playerPos.distanceTo(pos);
                boolean inRange = dist <= 3.2;

                targets.add(new SupplyTarget(spot != null ? spot.name() : "Supply", pos, isPlaced, dist, inRange));
            }
        }

        return targets;
    }

    public static boolean isWithinPickupRange(Vec3 cratePos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cratePos == null) return false;
        return mc.player.position().distanceTo(cratePos) <= 3.2;
    }

    public static boolean isWithinDragLimit(Vec3 cratePos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || cratePos == null) return false;
        return mc.player.position().distanceTo(cratePos) <= 5.0;
    }
}
