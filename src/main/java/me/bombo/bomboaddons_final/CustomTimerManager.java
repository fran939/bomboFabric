package me.bombo.bomboaddons_final;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CustomTimerManager {

    public static class CustomTimer {
        public final String name;
        public final long endTime;
        public final boolean isPartyTimer;

        public CustomTimer(String name, long durationMs, boolean isPartyTimer) {
            this.name = name;
            this.endTime = System.currentTimeMillis() + durationMs;
            this.isPartyTimer = isPartyTimer;
        }
    }

    public static final List<CustomTimer> activeTimers = new CopyOnWriteArrayList<>();

    public static void init() {
        HudRenderCallback.EVENT.register(CustomTimerManager::render);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.customTimerHudEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        // Hide during open screens unless it is the HUD movement screen
        if (client.screen != null && !(client.screen instanceof HudMoveScreen)) return;

        drawTimers(g, s.customTimerHudX, s.customTimerHudY, false);
    }

    public static void drawTimers(GuiGraphics g, int x, int y, boolean isHovered) {
        BomboConfig.Settings s = BomboConfig.get();
        List<String> lines = new ArrayList<>();

        if (activeTimers.isEmpty()) {
            if (isHovered) {
                lines.add("§6§lTimers §7(Example)");
                lines.add("§fTimer: §a02:45");
                lines.add("§fbomboclas: §e04:59");
            }
        } else {
            lines.add("§6§lActive Timers");
            long now = System.currentTimeMillis();
            for (CustomTimer timer : activeTimers) {
                long remaining = timer.endTime - now;
                if (remaining < 0) remaining = 0;
                String timeColor = remaining < 10000 ? "§c" : (remaining < 30000 ? "§e" : "§a");
                lines.add("§f" + timer.name + ": " + timeColor + formatTimeRemaining(remaining));
            }
        }

        if (lines.isEmpty()) return;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = s.customTimerHudScale;
        g.pose().scale(scale, scale);

        int curY = 0;
        for (String line : lines) {
            g.drawString(Minecraft.getInstance().font, line, 0, curY, 0xFFFFFFFF, true);
            curY += 10;
        }

        g.pose().popMatrix();
    }

    public static int getWidth() {
        int maxWidth = 60;
        List<String> lines = new ArrayList<>();
        if (activeTimers.isEmpty()) {
            lines.add("§6§lTimers §7(Example)");
            lines.add("§fTimer: §a02:45");
            lines.add("§fbomboclas: §e04:59");
        } else {
            lines.add("§6§lActive Timers");
            long now = System.currentTimeMillis();
            for (CustomTimer timer : activeTimers) {
                long remaining = timer.endTime - now;
                lines.add("§f" + timer.name + ": §a" + formatTimeRemaining(remaining));
            }
        }
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, Minecraft.getInstance().font.width(line.replaceAll("§.", "")));
        }
        return maxWidth;
    }

    public static int getHeight() {
        int count = activeTimers.isEmpty() ? 3 : (1 + activeTimers.size());
        return count * 10;
    }

    public static String formatTimeRemaining(long remainingMs) {
        if (remainingMs <= 0) return "00:00";
        long totalSecs = remainingMs / 1000;
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, mins, secs);
        } else {
            return String.format("%02d:%02d", mins, secs);
        }
    }

    public static long parseTimeMs(String input) {
        if (input == null || input.isEmpty()) return -1;
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)(h|m|s)", Pattern.CASE_INSENSITIVE).matcher(input);
        long totalMs = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            try {
                double value = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                switch (unit) {
                    case "h": totalMs += (long) (value * 3600 * 1000); break;
                    case "m": totalMs += (long) (value * 60 * 1000); break;
                    case "s": totalMs += (long) (value * 1000); break;
                }
            } catch (NumberFormatException ignored) {}
        }
        if (!found) {
            // Also try parsing as pure number of minutes (e.g. "3" -> 3 minutes)
            try {
                double value = Double.parseDouble(input);
                return (long) (value * 60 * 1000);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return totalMs;
    }

    public static void startTimer(String name, long durationMs) {
        startTimer(name, durationMs, false);
    }

    public static void startTimer(String name, long durationMs, boolean isPartyTimer) {
        if (name == null || name.isEmpty()) {
            name = "Timer";
        }
        // Remove existing timer of the same name to override/reset it
        for (CustomTimer timer : activeTimers) {
            if (timer.name.equalsIgnoreCase(name)) {
                activeTimers.remove(timer);
            }
        }
        activeTimers.add(new CustomTimer(name, durationMs, isPartyTimer));
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (CustomTimer timer : activeTimers) {
            if (now >= timer.endTime) {
                activeTimers.remove(timer);
                Minecraft.getInstance().execute(() -> {
                    // Play Note Block Pling Sound
                    Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F)
                    );
                    // Send Chat Alert
                    String nameLabel = timer.name.equalsIgnoreCase("Timer") ? "" : " '" + timer.name + "'";
                    Bomboaddons.sendMessage("&8[&bBomboAddons&8] &cTimer" + nameLabel + " has expired!");

                    if (timer.isPartyTimer) {
                        BomboaddonsClient.executeTracked("pc Timer for " + timer.name + " has expired!");
                    }
                });
            }
        }
    }
}
