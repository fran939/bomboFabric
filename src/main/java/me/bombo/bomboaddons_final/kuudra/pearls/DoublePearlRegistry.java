package me.bombo.bomboaddons_final.kuudra.pearls;

import net.minecraft.world.phys.Vec3;
import java.util.*;

public class DoublePearlRegistry {
    private static final EnumMap<PickupSpot, List<DoublePearl>> byPre = new EnumMap<>(PickupSpot.class);

    static {
        for (PickupSpot s : PickupSpot.values()) {
            byPre.put(s, new ArrayList<>());
        }
        // Load default routes
        for (DoublePearl dp : DoublePearlDefaults.DEFAULTS.values()) {
            byPre.get(dp.getPre()).add(dp);
        }
    }

    public static List<DoublePearl> getRoutesFrom(PickupSpot pre) {
        if (pre == null) return Collections.emptyList();
        PickupSpot missing = NoPre.getMissing();
        List<DoublePearl> base = byPre.getOrDefault(pre, Collections.emptyList());
        if (missing == PickupSpot.NONE || base.isEmpty()) return base;
        List<DoublePearl> out = new ArrayList<>(base.size());
        for (DoublePearl r : base) {
            if (r.getDrop() != missing) {
                out.add(r);
            }
        }
        return out;
    }
}
