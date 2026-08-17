package me.bombo.bomboaddons.kuudra.pearls;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

public final class DoublePearlDefaults {
   public static final Map<String, DoublePearl> DEFAULTS;

   private static void put(Map<String, DoublePearl> m, String id, Vec3 loc, PickupSpot pre, PickupSpot drop) {
      m.put(id, new DoublePearl(id, loc, pre, drop, true));
   }

   static {
      Map<String, DoublePearl> m = new LinkedHashMap();
      put(m, "SLASH->X_CANNON", new Vec3((double)-130.0F, (double)78.0F, (double)-114.0F), PickupSpot.SLASH, PickupSpot.X_CANNON);
      put(m, "SLASH->SQUARE", new Vec3((double)-140.0F, (double)76.0F, (double)-87.0F), PickupSpot.SLASH, PickupSpot.SQUARE);
      put(m, "X->X_CANNON", new Vec3((double)-135.0F, (double)75.0F, (double)-123.5F), PickupSpot.X, PickupSpot.X_CANNON);
      put(m, "X->SQUARE", new Vec3((double)-140.0F, (double)76.0F, (double)-87.0F), PickupSpot.X, PickupSpot.SQUARE);
      put(m, "TRIANGLE->SHOP", new Vec3((double)-76.0F, (double)77.5F, (double)-137.0F), PickupSpot.TRIANGLE, PickupSpot.SHOP);
      put(m, "EQUALS->SHOP", new Vec3((double)-76.0F, (double)77.5F, (double)-137.0F), PickupSpot.EQUALS, PickupSpot.SHOP);
      DEFAULTS = Collections.unmodifiableMap(m);
   }
}
