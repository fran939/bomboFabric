package me.bombo.bomboaddons.kuudra.pearls;

import net.minecraft.world.phys.Vec3;

public enum SupplySpot {
   SUPPLY1(new Vec3((double)-98.0F, (double)79.0F, -112.94)),
   SUPPLY2(new Vec3((double)-106.0F, (double)79.0F, -112.94)),
   SUPPLY3(new Vec3((double)-110.0F, (double)79.0F, (double)-106.0F)),
   SUPPLY4(new Vec3((double)-106.0F, (double)79.0F, -99.06)),
   SUPPLY5(new Vec3((double)-98.0F, (double)79.0F, -99.06)),
   SUPPLY6(new Vec3((double)-94.0F, (double)79.0F, (double)-106.0F));

   private final Vec3 location;
   private final int[] intLocation;

   private SupplySpot(Vec3 location) {
      this.location = location;
      this.intLocation = new int[]{(int)location.x, (int)location.y, (int)location.z};
   }

   public Vec3 getLocation() {
      return this.location;
   }

   public int getX() {
      return this.intLocation[0];
   }

   public int getY() {
      return this.intLocation[1];
   }

   public int getZ() {
      return this.intLocation[2];
   }

   // $FF: synthetic method
   private static SupplySpot[] $values() {
      return new SupplySpot[]{SUPPLY1, SUPPLY2, SUPPLY3, SUPPLY4, SUPPLY5, SUPPLY6};
   }
}
