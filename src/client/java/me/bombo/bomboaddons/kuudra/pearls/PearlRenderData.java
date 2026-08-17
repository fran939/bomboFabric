package me.bombo.bomboaddons.kuudra.pearls;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.world.phys.Vec3;

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
      this.time = Math.max(0L, this.getTimeUntilThrow(progressStart, tracking) / 25L * 25L);
      this.cachedDisplay = this.time > 0L && !hasSupply ? "§c" + this.time + "ms" : "§a§lTHROW";
   }

   private long getTimeUntilThrow(long progressStart, boolean tracking) {
      if (!tracking) {
         return this.flightTimeMs;
      } else {
         int base = Pearls.getPearlDelay(BomboConfig.get().kuudraTalisman, KuudraUtils.getKuudraTier());
         int delay = base + Pearls.cachedInitialDelay;
         if (this.isDouble) {
            delay += Pearls.cachedDoubleDelay;
         }

         return (long)delay - this.flightTimeMs - (System.currentTimeMillis() - progressStart);
      }
   }

   public PearlRenderData(PearlSolution sol, Vec3 target, boolean isDouble, boolean isSky, boolean hasSupply, long progressStart, boolean tracking) {
      this.solution = sol.solution;
      this.yaw = sol.yaw;
      this.pitch = sol.pitch;
      this.flightTimeMs = sol.flightTimeMs;
      this.target = target;
      this.isDouble = isDouble;
      this.isSky = isSky;
      this.updateDisplay(hasSupply, progressStart, tracking);
   }
}
