package me.bombo.bomboaddons.features.misc;

import me.bombo.bomboaddons.BomboaddonsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandDelayManager {
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "BomboCommandDelayScheduler");
        t.setDaemon(true);
        return t;
    });

    public static void scheduleCommand(long delayMs, String command) {
        if (command == null || command.trim().isEmpty()) return;
        final String cmd = command.trim();
        Minecraft mc = Minecraft.getInstance();

        if (delayMs <= 0) {
            mc.execute(() -> BomboaddonsClient.executeTracked(cmd));
            return;
        }

        SCHEDULER.schedule(() -> {
            try {
                mc.execute(() -> BomboaddonsClient.executeTracked(cmd));
            } catch (Throwable ignored) {}
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public static void handleCommand(String rawArgs) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String args = (rawArgs != null) ? rawArgs.trim() : "";
        if (args.isEmpty() || args.equalsIgnoreCase("help")) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eDelayed Command Execution:"));
            mc.player.sendSystemMessage(Component.literal("§7Usage: §b/b cd <time_ms> <command> §7(e.g. §b/b cd 100 /wd§7 or §b/b cd 250 /warp camp§7)"));
            return;
        }

        int firstSpace = args.indexOf(' ');
        if (firstSpace == -1) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cUsage: /b cd <time_ms> <command> (e.g. /b cd 100 /wd)"));
            return;
        }

        String delayPart = args.substring(0, firstSpace).trim();
        String commandPart = args.substring(firstSpace + 1).trim();

        long delayMs;
        try {
            if (delayPart.toLowerCase().endsWith("ms")) {
                delayPart = delayPart.substring(0, delayPart.length() - 2).trim();
            } else if (delayPart.toLowerCase().endsWith("s")) {
                double sec = Double.parseDouble(delayPart.substring(0, delayPart.length() - 1).trim());
                delayMs = (long) (sec * 1000.0);
                scheduleCommand(delayMs, commandPart);
                return;
            }
            delayMs = Long.parseLong(delayPart);
        } catch (NumberFormatException e) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cInvalid delay milliseconds: §e" + delayPart));
            return;
        }

        if (commandPart.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cNo command specified to run! Usage: /b cd " + delayMs + " <command>"));
            return;
        }

        scheduleCommand(delayMs, commandPart);
    }
}
