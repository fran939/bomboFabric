package me.bombo.bomboaddons.kuudra.pearls;

import net.minecraft.world.phys.Vec3;

public enum PickupSpot {
   SHOP(new Vec3((double)-81.0F, (double)76.0F, (double)-143.0F), "Shop", 1),
   X(new Vec3((double)-142.5F, (double)77.0F, (double)-148.0F), "X", 2),
   X_CANNON(new Vec3((double)-143.0F, (double)76.0F, (double)-125.0F), "X Cannon", 3),
   EQUALS(new Vec3((double)-65.5F, (double)76.0F, (double)-87.5F), "Equals", 4),
   SLASH(new Vec3((double)-113.5F, (double)77.0F, (double)-68.5F), "Slash", 5),
   TRIANGLE(new Vec3((double)-67.5F, (double)77.0F, (double)-122.5F), "Triangle", 6),
   SQUARE(new Vec3((double)-143.0F, (double)76.0F, (double)-80.0F), "Square", -1),
   NONE(new Vec3((double)0.0F, (double)0.0F, (double)0.0F), "None", -1);

   private final Vec3 location;
   private final String displayText;
   private final int supplyId;
   private static final PickupSpot[] SPOTS = values();

   private PickupSpot(Vec3 location, String displayText, int supplyId) {
      this.location = location;
      this.displayText = displayText;
      this.supplyId = supplyId;
   }

   public Vec3 getLocation() {
      return this.location;
   }

   public String getDisplayText() {
      return this.displayText;
   }

   public int getSupplyId() {
      return this.supplyId;
   }

   public static PickupSpot getClosestSpot(Vec3 eyePos) {
      PickupSpot closest = NONE;
      double bestDistSq = Double.MAX_VALUE;

      for(PickupSpot spot : SPOTS) {
         if (spot != NONE) {
            double distSq = eyePos.distanceToSqr(spot.location);
            if (distSq < bestDistSq) {
               bestDistSq = distSq;
               closest = spot;
            }
         }
      }

      return closest;
   }

   // $FF: synthetic method
   private static PickupSpot[] $values() {
      return new PickupSpot[]{SHOP, X, X_CANNON, EQUALS, SLASH, TRIANGLE, SQUARE, NONE};
   }
}
