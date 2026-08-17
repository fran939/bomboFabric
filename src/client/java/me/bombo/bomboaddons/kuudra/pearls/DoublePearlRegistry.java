package me.bombo.bomboaddons.kuudra.pearls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public class DoublePearlRegistry {
   private static final EnumMap<PickupSpot, List<DoublePearl>> byPre = new EnumMap(PickupSpot.class);

   public static List<DoublePearl> getRoutesFrom(PickupSpot pre) {
      if (pre == null) {
         return Collections.emptyList();
      } else {
         PickupSpot missing = NoPre.getMissing();
         List<DoublePearl> base = (List)byPre.getOrDefault(pre, Collections.emptyList());
         if (missing != PickupSpot.NONE && !base.isEmpty()) {
            List<DoublePearl> out = new ArrayList(base.size());

            for(DoublePearl r : base) {
               if (r.getDrop() != missing) {
                  out.add(r);
               }
            }

            return out;
         } else {
            return base;
         }
      }
   }

   static {
      for(PickupSpot s : PickupSpot.values()) {
         byPre.put(s, new ArrayList());
      }

      for(DoublePearl dp : DoublePearlDefaults.DEFAULTS.values()) {
         ((List)byPre.get(dp.getPre())).add(dp);
      }

   }
}
