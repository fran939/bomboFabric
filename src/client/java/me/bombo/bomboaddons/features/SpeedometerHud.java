package me.bombo.bomboaddons.features;

import java.util.ArrayDeque;
import java.util.Deque;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class SpeedometerHud {
   private static double lastX = 0;
   private static double lastY = 0;
   private static double lastZ = 0;
   private static long lastUpdateTime = 0L;
   private static double currentBps = 0.0;
   private static double displayBps = 0.0;
   private static double displayAvgBps = 0.0;
   private static boolean initializedPos = false;

   // 1-second rolling window buffer for displacement samples
   private static final Deque<SpeedSample> SAMPLES = new ArrayDeque<>();
   private static long lastDisplayRefreshTime = 0L;
   private static String cachedDisplayText = "§bSpeed: §f0.0 §7(0.0) bps";

   /** A single tick cannot move this far, so anything above it is a teleport rather than speed. */
   private static final double IMPOSSIBLE_JUMP_BLOCKS = 40.0;
   /**
    * The highest speed observed stays on screen for at least this long (the configured statistics
    * window when that is longer), so a real burst stays readable instead of dropping away instantly.
    */
   private static final long PEAK_HOLD_MS = 5000L;

   // Rolling min / avg / max statistics window (default 5 seconds).
   private static final Deque<SpeedSample> STAT_SAMPLES = new ArrayDeque<>();
   private static double statMin = 0.0;
   private static double statAvg = 0.0;
   private static double statMax = 0.0;
   /** Peak hold: the highest speed seen and the moment it may start following the window down. */
   private static double peakHoldBps = 0.0;
   private static long peakHoldUntil = 0L;
   private static String cachedStatsText = "§7min §f0.0 §8| §7avg §f0.0 §8| §7max §f0.0";

   private static class SpeedSample {
      final long timestamp;
      final double distance;
      final double instantBps;

      SpeedSample(long timestamp, double distance) {
         this(timestamp, distance, 0.0);
      }

      SpeedSample(long timestamp, double distance, double instantBps) {
         this.timestamp = timestamp;
         this.distance = distance;
         this.instantBps = instantBps;
      }
   }

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "speedometer_hud"), SpeedometerHud::render);
   }

   public static void onClientTick(Minecraft mc) {
      if (mc.player == null || mc.level == null) {
         initializedPos = false;
         currentBps = 0.0;
         displayBps = 0.0;
         displayAvgBps = 0.0;
         SAMPLES.clear();
         STAT_SAMPLES.clear();
         statMin = 0.0;
         statAvg = 0.0;
         statMax = 0.0;
         peakHoldBps = 0.0;
         peakHoldUntil = 0L;
         return;
      }

      double px = mc.player.getX();
      double py = mc.player.getY();
      double pz = mc.player.getZ();
      long now = System.currentTimeMillis();

      if (!initializedPos || lastUpdateTime == 0L) {
         lastX = px;
         lastY = py;
         lastZ = pz;
         lastUpdateTime = now;
         initializedPos = true;
         return;
      }

      long dt = now - lastUpdateTime;
      if (dt <= 0) return;

      double dx = px - lastX;
      double dy = py - lastY;
      double dz = pz - lastZ;
      double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

      // Add to rolling 1-second sample buffer
      SAMPLES.addLast(new SpeedSample(now, dist));

      // Evict samples older than 1000ms
      while (!SAMPLES.isEmpty() && now - SAMPLES.peekFirst().timestamp > 1000L) {
         SAMPLES.pollFirst();
      }

      // Calculate instantaneous blocks per second
      double instantBps = (dist / (double) dt) * 1000.0;

      // Handle AOTV / rapid teleports:
      if (dist >= 4.0) {
         currentBps = Math.max(currentBps, instantBps);
      } else {
         currentBps = currentBps * 0.65 + instantBps * 0.35;
      }

      updateStatistics(now, dist, instantBps);

      lastX = px;
      lastY = py;
      lastZ = pz;
      lastUpdateTime = now;
   }

   /** Milliseconds retained by the min/avg/max statistics window. */
   private static long statWindowMs() {
      BomboConfig.Settings s = BomboConfig.get();
      int seconds = s != null && s.speedometerStatsWindow > 0 ? s.speedometerStatsWindow : 5;
      return seconds * 1000L;
   }

   /**
    * Feeds the min/avg/max window. Teleport-sized jumps (AOTV, /warp, world changes) are ignored
    * so a single 300 bps spike cannot poison the maximum for the next five seconds.
    */
   private static void updateStatistics(long now, double dist, double instantBps) {
      // A teleport cannot be told apart from fast movement by distance alone: a Garden player
      // legitimately crosses 20+ blocks in a tick (400-500 bps), which the old 8-block cutoff threw
      // away, so the max never climbed above ~18 bps. Only physically impossible jumps are dropped
      // here, and every real sample is kept: retracting a spike as soon as ordinary movement resumed
      // is exactly what made the maximum flick to a genuine peak for one tick and snap straight back.
      if (dist < IMPOSSIBLE_JUMP_BLOCKS) {
         STAT_SAMPLES.addLast(new SpeedSample(now, dist, instantBps));
      }

      long windowMs = statWindowMs();
      while (!STAT_SAMPLES.isEmpty() && now - STAT_SAMPLES.peekFirst().timestamp > windowMs) {
         STAT_SAMPLES.pollFirst();
      }

      if (STAT_SAMPLES.isEmpty()) {
         statMin = 0.0;
         statAvg = 0.0;
         statMax = 0.0;
         peakHoldBps = 0.0;
         peakHoldUntil = 0L;
         return;
      }

      double min = Double.MAX_VALUE;
      double max = 0.0;
      double sum = 0.0;
      for (SpeedSample sample : STAT_SAMPLES) {
         double value = sample.instantBps;
         if (value < min) min = value;
         if (value > max) max = value;
         sum += value;
      }
      statMin = min == Double.MAX_VALUE ? 0.0 : min;
      statAvg = sum / (double) STAT_SAMPLES.size();

      // Peak hold: a faster sample raises the bar and restarts the hold, and the bar only follows the
      // ordinary window maximum back down once the hold (five seconds, or the configured window when
      // that is longer) has expired. That is what keeps the real maximum on screen instead of the
      // displayed peak collapsing the moment the next tick is slower.
      long holdMs = Math.max(PEAK_HOLD_MS, windowMs);
      if (max >= peakHoldBps) {
         peakHoldBps = max;
         peakHoldUntil = now + holdMs;
         statMax = max;
      } else if (now <= peakHoldUntil) {
         statMax = peakHoldBps;
      } else {
         peakHoldBps = max;
         statMax = max;
      }
   }

   /** Lowest speed seen in the statistics window, in blocks per second. */
   public static double getStatMinBps() {
      return statMin;
   }

   /** Mean speed over the statistics window, in blocks per second. */
   public static double getStatAvgBps() {
      return statAvg;
   }

   /** Highest speed seen in the statistics window, in blocks per second. */
   public static double getStatMaxBps() {
      return statMax;
   }

   public static double getOneSecondAverageBps() {
      long now = System.currentTimeMillis();
      // Purge old
      while (!SAMPLES.isEmpty() && now - SAMPLES.peekFirst().timestamp > 1000L) {
         SAMPLES.pollFirst();
      }
      if (SAMPLES.isEmpty()) return 0.0;

      double totalDist = 0.0;
      for (SpeedSample s : SAMPLES) {
         totalDist += s.distance;
      }

      long oldestTime = SAMPLES.peekFirst().timestamp;
      long timeSpan = now - oldestTime;
      if (timeSpan < 100L) {
         timeSpan = 100L;
      }

      // Blocks per second over window
      return (totalDist / (double) timeSpan) * 1000.0;
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("HUD: Speedometer")) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null || !s.speedometer) return;

         Minecraft mc = Minecraft.getInstance();
         if (mc.options.keyToggleGui.isDown()) return;
         if (mc.gui.screen() != null && !(mc.gui.screen() instanceof HudMoveScreen)) return;

         int x = s.speedometerX >= 0 ? s.speedometerX : 10;
         int y = s.speedometerY >= 0 ? s.speedometerY : 120;
         float scale = s.speedometerScale > 0 ? s.speedometerScale : 1.0F;

         drawSpeedometer(g, mc, x, y, scale, false);
      }
   }

   public static void drawSpeedometer(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, boolean preview) {
      BomboConfig.Settings s = BomboConfig.get();
      long now = System.currentTimeMillis();

      // Smooth visual values
      displayBps = displayBps * 0.8 + currentBps * 0.2;
      if (displayBps < 0.05) displayBps = 0.0;

      double avgBps = getOneSecondAverageBps();
      displayAvgBps = displayAvgBps * 0.85 + avgBps * 0.15;
      if (displayAvgBps < 0.05) displayAvgBps = 0.0;

      // Update text every 80ms for clean readability without rapid flickering
      boolean showStats = s == null || s.speedometerStats;

      if (preview) {
         cachedDisplayText = "§bSpeed: §f43.5 §7(38.2) bps";
         cachedStatsText = "§7min §f12.0 §8| §7avg §f43.5 §8| §7max §f61.2";
      } else if (now - lastDisplayRefreshTime >= 80L || cachedDisplayText == null) {
         lastDisplayRefreshTime = now;
         String unit = (s != null && s.speedometerUnit != null && !s.speedometerUnit.isEmpty()) ? s.speedometerUnit : "bps";

         if ("%".equalsIgnoreCase(unit) || "speed".equalsIgnoreCase(unit)) {
            int currentPct = (int) Math.round((displayBps / 4.317) * 100.0);
            int avgPct = (int) Math.round((displayAvgBps / 4.317) * 100.0);
            cachedDisplayText = "§bSpeed: §f" + currentPct + "% §7(" + avgPct + "%)";
            cachedStatsText = String.format("§7min §f%d%% §8| §7avg §f%d%% §8| §7max §f%d%%",
                    Math.round((statMin / 4.317) * 100.0), Math.round((statAvg / 4.317) * 100.0), Math.round((statMax / 4.317) * 100.0));
         } else if ("m/s".equalsIgnoreCase(unit)) {
            cachedDisplayText = String.format("§bSpeed: §f%.1f §7(%.1f) m/s", displayBps, displayAvgBps);
            cachedStatsText = String.format("§7min §f%.1f §8| §7avg §f%.1f §8| §7max §f%.1f m/s", statMin, statAvg, statMax);
         } else {
            cachedDisplayText = String.format("§bSpeed: §f%.1f §7(%.1f) bps", displayBps, displayAvgBps);
            cachedStatsText = String.format("§7min §f%.1f §8| §7avg §f%.1f §8| §7max §f%.1f bps", statMin, statAvg, statMax);
         }
      }

      g.pose().pushMatrix();
      if (scale != 1.0F && scale > 0.0F) {
         g.pose().scale(scale, scale);
         int ix = (int)((float)x / scale);
         int iy = (int)((float)y / scale);
         g.text(mc.font, cachedDisplayText, ix, iy, -1, true);
         if (showStats) g.text(mc.font, cachedStatsText, ix, iy + 11, -1, true);
      } else {
         g.text(mc.font, cachedDisplayText, x, y, -1, true);
         if (showStats) g.text(mc.font, cachedStatsText, x, y + 11, -1, true);
      }
      g.pose().popMatrix();
   }

   /** Text width of the widest line the speedometer draws, used for HUD sizing. */
   public static int getWidth(Minecraft mc, float scale) {
      if (mc == null || mc.font == null) return (int)(110.0F * scale);
      BomboConfig.Settings s = BomboConfig.get();
      int w = mc.font.width(cachedDisplayText);
      if (s == null || s.speedometerStats) {
         w = Math.max(w, mc.font.width(cachedStatsText));
      }
      return (int)((float)Math.max(60, w) * scale);
   }

   public static int getHeight(Minecraft mc, float scale) {
      BomboConfig.Settings s = BomboConfig.get();
      boolean stats = s == null || s.speedometerStats;
      return (int)((stats ? 23.0F : 12.0F) * scale);
   }

   public static void reset() {
      initializedPos = false;
      currentBps = 0.0;
      displayBps = 0.0;
      displayAvgBps = 0.0;
      lastUpdateTime = 0L;
      SAMPLES.clear();
      STAT_SAMPLES.clear();
      statMin = 0.0;
      statAvg = 0.0;
      statMax = 0.0;
      peakHoldBps = 0.0;
      peakHoldUntil = 0L;
   }
}
