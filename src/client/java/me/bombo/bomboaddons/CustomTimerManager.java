package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        public final String logoItemId;
        public final boolean showOnlyWhenReady;
        public final boolean keepReadyState;
        public boolean ready = false;
        public long readyExpiration = -1;

        public CustomTimer(String name, long durationMs, boolean isPartyTimer, String logoItemId, boolean showOnlyWhenReady, boolean keepReadyState) {
            this.name = name;
            this.endTime = System.currentTimeMillis() + durationMs;
            this.isPartyTimer = isPartyTimer;
            this.logoItemId = logoItemId;
            this.showOnlyWhenReady = showOnlyWhenReady;
            this.keepReadyState = keepReadyState;
        }
    }

    public static final List<CustomTimer> activeTimers = new CopyOnWriteArrayList<>();

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "custom_timer_manager"), CustomTimerManager::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.customTimerHudEnabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;

        // Hide during open screens unless it is the HUD movement screen
        if (client.screen != null && !(client.screen instanceof HudMoveScreen)) return;

        drawTimers(g, s.customTimerHudX, s.customTimerHudY, false);
    }

    public static void drawTimers(GuiGraphicsExtractor g, int x, int y, boolean isHovered) {
        BomboConfig.Settings s = BomboConfig.get();
        if (activeTimers.isEmpty() && !isHovered) return;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = s.customTimerHudScale;
        g.pose().scale(scale, scale);

        int curY = 0;
        long now = System.currentTimeMillis();

        if (activeTimers.isEmpty()) {
            g.text(Minecraft.getInstance().font, "§6§lTimers §7(Example)", 0, curY, 0xFFFFFFFF, true);
            curY += 14;
            g.text(Minecraft.getInstance().font, "§fTimer: §a02:45", 0, curY, 0xFFFFFFFF, true);
            curY += 14;
            g.text(Minecraft.getInstance().font, "§fbomboclas: §e04:59", 0, curY, 0xFFFFFFFF, true);
        } else {
            for (CustomTimer timer : activeTimers) {
                if (timer.showOnlyWhenReady && !timer.ready) continue;
                int startX = 0;
                if (timer.logoItemId != null && !timer.logoItemId.isEmpty()) {
                    net.minecraft.world.item.ItemStack item = me.bombo.bomboaddons.SkyblockItemManager.createSkyblockItem(timer.logoItemId);
                    if (item != null && !item.isEmpty()) {
                        g.pose().pushMatrix();
                        g.pose().translate(0f, (float)(curY - 3));
                        g.pose().scale(0.8f, 0.8f);
                        g.item(item, 0, 0);
                        g.pose().popMatrix();
                        startX += 14;
                    }
                }
                
                if (timer.ready) {
                    g.text(Minecraft.getInstance().font, "§f" + timer.name + ": §a§lREADY", startX, curY, 0xFFFFFFFF, true);
                } else {
                    long remaining = timer.endTime - now;
                    if (remaining < 0) remaining = 0;
                    String timeColor = remaining < 10000 ? "§c" : (remaining < 30000 ? "§e" : "§a");
                    g.text(Minecraft.getInstance().font, "§f" + timer.name + ": " + timeColor + formatTimeRemaining(remaining), startX, curY, 0xFFFFFFFF, true);
                }
                curY += 14;
            }
        }

        g.pose().popMatrix();
    }

    public static int getWidth() {
        return 120; // Fixed width for simplicity and room for icons
    }

    public static int getHeight() {
        int count = activeTimers.isEmpty() ? 3 : (1 + activeTimers.size());
        return count * 14;
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
        startTimer(name, durationMs, false, null, false, false);
    }

    public static void startTimer(String name, long durationMs, boolean isPartyTimer, String logoItemId, boolean showOnlyWhenReady) {
        startTimer(name, durationMs, isPartyTimer, logoItemId, showOnlyWhenReady, false);
    }

    public static void startTimer(String name, long durationMs, boolean isPartyTimer, String logoItemId, boolean showOnlyWhenReady, boolean keepReadyState) {
        if (name == null || name.isEmpty()) {
            name = "Timer";
        }
        // Remove existing timer of the same name to override/reset it
        for (CustomTimer timer : activeTimers) {
            if (timer.name.equalsIgnoreCase(name)) {
                activeTimers.remove(timer);
            }
        }
        activeTimers.add(new CustomTimer(name, durationMs, isPartyTimer, logoItemId, showOnlyWhenReady, keepReadyState));
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        for (CustomTimer timer : activeTimers) {
            if (timer.ready) {
                if (!timer.keepReadyState && timer.readyExpiration != -1 && now > timer.readyExpiration) {
                    activeTimers.remove(timer);
                }
            } else if (now >= timer.endTime) {
                timer.ready = true;
                timer.readyExpiration = timer.keepReadyState ? -1 : (now + 10000); // Keep ready for 10 seconds
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
