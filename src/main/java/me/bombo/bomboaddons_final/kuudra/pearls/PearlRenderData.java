package me.bombo.bomboaddons_final.kuudra.pearls;

import net.minecraft.world.phys.Vec3;
import me.bombo.bomboaddons_final.BomboConfig;

public class PearlRenderData {
    private final long flightTimeMs;
    public final Vec3 solution;
    public final float yaw;
    public final float pitch;
    public final Vec3 target;
    public final boolean isDouble;
    public final boolean isSky;
    public String cachedDisplay;
    public long time;

    public void updateDisplay(boolean hasSupply, long progressStart, boolean tracking) {
        time = Math.max(0, (getTimeUntilThrow(progressStart, tracking) / 25) * 25);
        cachedDisplay = (time <= 0 || hasSupply) ? "§a§lTHROW" : "§c" + time + "ms";
    }

    private long getTimeUntilThrow(long progressStart, boolean tracking) {
        if (!tracking) return flightTimeMs;

        int base = Pearls.getPearlDelay(BomboConfig.get().kuudraTalisman, KuudraUtils.getKuudraTier());
        int delay = base + Pearls.cachedInitialDelay;
        if (isDouble) delay += Pearls.cachedDoubleDelay;

        return delay - flightTimeMs - (System.currentTimeMillis() - progressStart);
    }

    public PearlRenderData(PearlSolution sol, Vec3 target, boolean isDouble, boolean isSky, boolean hasSupply, long progressStart, boolean tracking) {
        this.solution = sol.solution;
        this.yaw = sol.yaw;
        this.pitch = sol.pitch;
        this.flightTimeMs = sol.flightTimeMs;
        this.target = target;
        this.isDouble = isDouble;
        this.isSky = isSky;
        updateDisplay(hasSupply, progressStart, tracking);
    }
}
