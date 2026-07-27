package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiscordBridge {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DiscordBridge-Thread");
        t.setDaemon(true);
        return t;
    });

    public static boolean isEnabled() {
        return BomboConfig.get().discordBridgeEnabled && 
               BomboConfig.get().discordWebhookUrl != null && 
               !BomboConfig.get().discordWebhookUrl.trim().isEmpty();
    }

    public static void onChatMessage(String rawMessage) {
        if (!isEnabled()) return;
        if (rawMessage == null || rawMessage.trim().isEmpty()) return;

        // Strip minecraft formatting codes
        String clean = ChatFormatting.stripFormatting(rawMessage).trim();
        if (clean.isEmpty()) return;

        // Avoid infinite feedback loops from internal debug messages or bridge system notices
        if (clean.contains("DailyRewardDebug") || clean.contains("[BomboAddons]") || clean.contains("[DiscordBridge]")) {
            return;
        }

        // Detect /bc (Bombo Chat) messages: e.g. "[Bombo] [MVP+] Player: Message" or "[Bombo] Player: Message"
        boolean isBcChat = clean.startsWith("[Bombo] ");
        boolean isGuild = clean.startsWith("Guild >") || clean.contains("Officer >");
        boolean isParty = clean.startsWith("Party >");
        boolean isDm = clean.startsWith("From ") || clean.startsWith("To ");

        if (isBcChat) {
            if (!BomboConfig.get().discordBridgeBcChat) return;
            String body = clean.substring("[Bombo] ".length());
            String sender = "Player";
            String msgContent = body;
            int colonIdx = body.indexOf(": ");
            if (colonIdx != -1) {
                sender = extractCleanUsername(body.substring(0, colonIdx));
                msgContent = body.substring(colonIdx + 2);
            }
            sendWebhookAsync(sender, msgContent);
            return;
        }

        // Filter standard minecraft chat according to config settings
        if (BomboConfig.get().discordBridgeGuild || BomboConfig.get().discordBridgeParty || BomboConfig.get().discordBridgeDm) {
            boolean match = (BomboConfig.get().discordBridgeGuild && isGuild) ||
                          (BomboConfig.get().discordBridgeParty && isParty) ||
                          (BomboConfig.get().discordBridgeDm && isDm);
            if (!match && !BomboConfig.get().discordBridgeAllChat) return;
        } else if (!BomboConfig.get().discordBridgeAllChat) {
            // Default to forwarding Guild, Party, and DMs if no specific toggles are on
            if (!isGuild && !isParty && !isDm) return;
        }

        String sender = "Player";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getUser() != null) {
                sender = extractCleanUsername(mc.getUser().getName());
            }
        } catch (Exception ignored) {}

        sendWebhookAsync(sender, clean);
    }

    private static String extractCleanUsername(String rawSender) {
        if (rawSender == null) return "Player";
        String s = rawSender.trim();
        if (s.contains("(Bombo Chat)")) {
            s = s.replace("(Bombo Chat)", "").trim();
        }
        if (s.contains("] ")) {
            s = s.substring(s.lastIndexOf("] ") + 2).trim();
        }
        if (s.contains(" ")) {
            s = s.split(" ")[0].trim();
        }
        return s.isEmpty() ? "Player" : s;
    }

    public static void sendNotification(String title, String description) {
        if (!isEnabled()) return;
        String content = "**[" + title + "]** " + description;
        String sender = "BomboAddons Notifications";
        sendWebhookAsync(sender, content);
    }

    public static void sendWebhookAsync(String username, String content) {
        String webhookUrl = BomboConfig.get().discordWebhookUrl;
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) return;

        EXECUTOR.submit(() -> {
            try {
                URL url = new URL(webhookUrl.trim());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("User-Agent", "BomboAddons-MinecraftMod");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String safeUsername = escapeJson(username);
                String safeContent = escapeJson(content);
                String avatarUrl = "https://mc-heads.net/avatar/" + safeUsername + "/100.png";

                String jsonPayload = "{\"username\":\"" + safeUsername + "\",\"avatar_url\":\"" + avatarUrl + "\",\"content\":\"" + safeContent + "\"}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    System.err.println("[DiscordBridge] HTTP Error " + code + " when posting to Discord webhook.");
                }
            } catch (Exception e) {
                System.err.println("[DiscordBridge] Error sending webhook: " + e.getMessage());
            }
        });
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (ch <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
