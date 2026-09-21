package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;

public class ClipboardCommandManager {
    private static String lastCopiedCommand = "";
    private static String lastSeenClipboard = "";
    private static long lastCheck = 0L;

    public static void onClipboardUpdated(String text) {
        if (text == null) return;
        String trimmed = text.trim();
        lastSeenClipboard = trimmed;
        if (trimmed.startsWith("/")) {
            lastCopiedCommand = trimmed;
        }
    }

    public static String getLastCopiedCommand() {
        pollClipboard();
        return lastCopiedCommand;
    }

    public static void pollClipboard() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 500L) return;
        lastCheck = now;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                String clip = mc.keyboardHandler.getClipboard();
                if (clip != null && !clip.equals(lastSeenClipboard)) {
                    onClipboardUpdated(clip);
                }
            }
        } catch (Throwable ignored) {}
    }
}
