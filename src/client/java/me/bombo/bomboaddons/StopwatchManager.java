package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class StopwatchManager {
    private static boolean active = false;
    private static boolean paused = false;
    private static long startTimeMs = 0;
    private static long accumulatedTimeMs = 0;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "stopwatch_hud"), StopwatchManager::render);
    }

    public static void start() {
        active = true;
        paused = false;
        startTimeMs = System.currentTimeMillis();
        accumulatedTimeMs = 0;
    }

    public static void togglePause() {
        if (!active) return;
        if (paused) {
            // Resume
            paused = false;
            startTimeMs = System.currentTimeMillis();
        } else {
            // Pause
            paused = true;
            accumulatedTimeMs += (System.currentTimeMillis() - startTimeMs);
        }
    }

    public static void stop() {
        active = false;
        paused = false;
        startTimeMs = 0;
        accumulatedTimeMs = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static long getElapsedTimeMs() {
        if (!active) return 0;
        if (paused) return accumulatedTimeMs;
        return accumulatedTimeMs + (System.currentTimeMillis() - startTimeMs);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        drawStopwatch(g, 10, 200);
    }

    public static void drawStopwatch(GuiGraphicsExtractor g, int x, int y) {
        long elapsed = getElapsedTimeMs();
        long totalSecs = elapsed / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        long millis = (elapsed % 1000) / 10;

        String formatted = String.format("%02d:%02d.%02d", mins, secs, millis);
        String text = "§b§lStopwatch: §f" + formatted + (paused ? " §c(PAUSED)" : "");

        g.text(Minecraft.getInstance().font, text, x, y, 0xFFFFFFFF, true);
    }
}
