package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MessageScheduler {
    public static final MessageScheduler INSTANCE = new MessageScheduler();
    private static final int MIN_DELAY = 200;
    private long lastMessage = 0;

    public void sendMessageAfterCooldown(String message, boolean hide) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        long now = System.currentTimeMillis();
        if (lastMessage + MIN_DELAY < now) {
            sendMessage(message, hide);
            lastMessage = now;
        } else {
            long delay = (lastMessage + MIN_DELAY) - now;
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
                client.execute(() -> sendMessage(message, hide));
            });
            lastMessage = now + delay;
        }
    }

    private void sendMessage(String message, boolean hide) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) return;
        message = message.trim();
        if (message.startsWith("/")) {
            client.player.connection.sendCommand(message.substring(1));
        } else {
            client.player.connection.sendChat(message);
        }
    }
}
