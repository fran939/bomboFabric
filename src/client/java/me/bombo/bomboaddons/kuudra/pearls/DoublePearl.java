package me.bombo.bomboaddons.kuudra.pearls;

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

   public String getId() {
      return this.id;
   }

   public Vec3 getLocation() {
      return this.location;
   }

   public PickupSpot getPre() {
      return this.pre;
   }

   public PickupSpot getDrop() {
      return this.drop;
   }

   public boolean isDefault() {
      return this.isDefault;
   }

   public String toString() {
      return this.id;
   }
}
