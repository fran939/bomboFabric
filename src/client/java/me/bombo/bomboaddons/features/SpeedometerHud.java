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

   private static class SpeedSample {
      final long timestamp;
      final double distance;

      SpeedSample(long timestamp, double distance) {
         this.timestamp = timestamp;
         this.distance = distance;
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

      lastX = px;
      lastY = py;
      lastZ = pz;
      lastUpdateTime = now;
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
      if (preview) {
         cachedDisplayText = "§bSpeed: §f43.5 §7(38.2) bps";
      } else if (now - lastDisplayRefreshTime >= 80L || cachedDisplayText == null) {
         lastDisplayRefreshTime = now;
         String unit = (s != null && s.speedometerUnit != null && !s.speedometerUnit.isEmpty()) ? s.speedometerUnit : "bps";

         if ("%".equalsIgnoreCase(unit) || "speed".equalsIgnoreCase(unit)) {
            int currentPct = (int) Math.round((displayBps / 4.317) * 100.0);
            int avgPct = (int) Math.round((displayAvgBps / 4.317) * 100.0);
            cachedDisplayText = "§bSpeed: §f" + currentPct + "% §7(" + avgPct + "%)";
         } else if ("m/s".equalsIgnoreCase(unit)) {
            cachedDisplayText = String.format("§bSpeed: §f%.1f §7(%.1f) m/s", displayBps, displayAvgBps);
         } else {
            cachedDisplayText = String.format("§bSpeed: §f%.1f §7(%.1f) bps", displayBps, displayAvgBps);
         }
      }

      g.pose().pushMatrix();
      if (scale != 1.0F && scale > 0.0F) {
         g.pose().scale(scale, scale);
         g.text(mc.font, cachedDisplayText, (int)((float)x / scale), (int)((float)y / scale), -1, true);
      } else {
         g.text(mc.font, cachedDisplayText, x, y, -1, true);
      }
      g.pose().popMatrix();
   }

   public static int getWidth(Minecraft mc, float scale) {
      return (int)(110.0F * scale);
   }

   public static int getHeight(Minecraft mc, float scale) {
      return (int)(12.0F * scale);
   }

   public static void reset() {
      initializedPos = false;
      currentBps = 0.0;
      displayBps = 0.0;
      displayAvgBps = 0.0;
      lastUpdateTime = 0L;
      SAMPLES.clear();
   }
}
