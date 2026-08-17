package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public class StopwatchManager {
   private static boolean active = false;
   private static boolean paused = false;
   private static long startTimeMs = 0L;
   private static long accumulatedTimeMs = 0L;

   public static void init() {
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "stopwatch_hud"), StopwatchManager::render);
   }

   public static void start() {
      active = true;
      paused = false;
      startTimeMs = System.currentTimeMillis();
      accumulatedTimeMs = 0L;
   }

   public static void togglePause() {
      if (active) {
         if (paused) {
            paused = false;
            startTimeMs = System.currentTimeMillis();
         } else {
            paused = true;
            accumulatedTimeMs += System.currentTimeMillis() - startTimeMs;
         }

      }
   }

   public static void stop() {
      active = false;
      paused = false;
      startTimeMs = 0L;
      accumulatedTimeMs = 0L;
   }

   public static boolean isActive() {
      return active;
   }

   public static boolean isPaused() {
      return paused;
   }

   public static long getElapsedTimeMs() {
      if (!active) {
         return 0L;
      } else {
         return paused ? accumulatedTimeMs : accumulatedTimeMs + (System.currentTimeMillis() - startTimeMs);
      }
   }

   private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
      if (active) {
         Minecraft mc = Minecraft.getInstance();
         if (!mc.options.hideGui) {
            drawStopwatch(g, 10, 200);
         }
      }
   }

   public static void drawStopwatch(GuiGraphicsExtractor g, int x, int y) {
      long elapsed = getElapsedTimeMs();
      long totalSecs = elapsed / 1000L;
      long mins = totalSecs / 60L;
      long secs = totalSecs % 60L;
      long millis = elapsed % 1000L / 10L;
      String formatted = String.format("%02d:%02d.%02d", mins, secs, millis);
      String text = "§b§lStopwatch: §f" + formatted + (paused ? " §c(PAUSED)" : "");
      g.text(Minecraft.getInstance().font, text, x, y, -1, true);
   }
}
