package me.bombo.bomboaddons_final.kuudra.pearls;

import net.minecraft.world.phys.Vec3;

public final class DoublePearl {
    private final String id;
    private final Vec3 location;
    private final PickupSpot pre;
    private final PickupSpot drop;
    private final boolean isDefault;

    public DoublePearl(String id, Vec3 location, PickupSpot pre, PickupSpot drop, boolean isDefault) {
        this.id = id;
        this.location = location;
        this.pre = pre;
        this.drop = drop;
        this.isDefault = isDefault;
    }

    public String getId() { return id; }
    public Vec3 getLocation() { return location; }
    public PickupSpot getPre() { return pre; }
    public PickupSpot getDrop() { return drop; }
    public boolean isDefault() { return isDefault; }

    @Override public String toString() { return id; }
}
