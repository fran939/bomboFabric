package me.bombo.bomboaddons;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

public class DiscordBridge {
   private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor((r) -> {
      Thread t = new Thread(r, "DiscordBridge-Thread");
      t.setDaemon(true);
      return t;
   });

   public static boolean isEnabled() {
      return BomboConfig.get().discordBridgeEnabled && BomboConfig.get().discordWebhookUrl != null && !BomboConfig.get().discordWebhookUrl.trim().isEmpty();
   }

   public static void onChatMessage(String rawMessage) {
      if (isEnabled()) {
         if (rawMessage != null && !rawMessage.trim().isEmpty()) {
            String clean = ChatFormatting.stripFormatting(rawMessage).trim();
            if (!clean.isEmpty()) {
               if (!clean.contains("DailyRewardDebug") && !clean.contains("[BomboAddons]") && !clean.contains("[DiscordBridge]")) {
                  boolean isBcChat = clean.startsWith("[Bombo] ");
                  boolean isGuild = clean.startsWith("Guild >") || clean.contains("Officer >");
                  boolean isParty = clean.startsWith("Party >");
                  boolean isDm = clean.startsWith("From ") || clean.startsWith("To ");
                  if (isBcChat) {
                     if (BomboConfig.get().discordBridgeBcChat) {
                        String body = clean.substring("[Bombo] ".length());
                        String sender = "Player";
                        String msgContent = body;
                        int colonIdx = body.indexOf(": ");
                        if (colonIdx != -1) {
                           sender = extractCleanUsername(body.substring(0, colonIdx));
                           msgContent = body.substring(colonIdx + 2);
                        }

                        sendWebhookAsync(sender, msgContent);
                     }
                  } else {
                     if (!BomboConfig.get().discordBridgeGuild && !BomboConfig.get().discordBridgeParty && !BomboConfig.get().discordBridgeDm) {
                        if (!BomboConfig.get().discordBridgeAllChat && !isGuild && !isParty && !isDm) {
                           return;
                        }
                     } else {
                        boolean match = BomboConfig.get().discordBridgeGuild && isGuild || BomboConfig.get().discordBridgeParty && isParty || BomboConfig.get().discordBridgeDm && isDm;
                        if (!match && !BomboConfig.get().discordBridgeAllChat) {
                           return;
                        }
                     }

                     String sender = "Player";

                     try {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.getUser() != null) {
                           sender = extractCleanUsername(mc.getUser().getName());
                        }
                     } catch (Exception var10) {
                     }

                     sendWebhookAsync(sender, clean);
                  }
               }
            }
         }
      }
   }

   private static String extractCleanUsername(String rawSender) {
      if (rawSender == null) {
         return "Player";
      } else {
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
   }

   public static void sendNotification(String title, String description) {
      if (isEnabled()) {
         String content = "**[" + title + "]** " + description;
         String sender = "BomboAddons Notifications";
         sendWebhookAsync(sender, content);
      }
   }

   public static void sendWebhookAsync(String username, String content) {
      String webhookUrl = BomboConfig.get().discordWebhookUrl;
      if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
         String formattedContent = formatForDiscord(content);
         EXECUTOR.submit(() -> {
            try {
               URL url = new URL(webhookUrl.trim());
               HttpURLConnection conn = (HttpURLConnection)url.openConnection();
               conn.setRequestMethod("POST");
               conn.setRequestProperty("Content-Type", "application/json; utf-8");
               conn.setRequestProperty("User-Agent", "BomboAddons-MinecraftMod");
               conn.setDoOutput(true);
               conn.setConnectTimeout(5000);
               conn.setReadTimeout(5000);
               String safeUsername = escapeJson(username);
               String safeContent = escapeJson(formattedContent);
               String avatarUrl = "https://mc-heads.net/avatar/" + safeUsername + "/100.png";
               String jsonPayload = "{\"username\":\"" + safeUsername + "\",\"avatar_url\":\"" + avatarUrl + "\",\"content\":\"" + safeContent + "\"}";
               OutputStream os = conn.getOutputStream();

               try {
                  byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                  os.write(input, 0, input.length);
               } catch (Throwable var13) {
                  if (os != null) {
                     try {
                        os.close();
                     } catch (Throwable x2) {
                        var13.addSuppressed(x2);
                     }
                  }

                  throw var13;
               }

               if (os != null) {
                  os.close();
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
   }

   private static String formatForDiscord(String input) {
      if (input == null || !input.contains("[SHOW:")) {
         return input;
      }
      try {
         java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\[SHOW:([^\\]:]+):([A-Za-z0-9+/=\\r\\n]+)\\]");
         java.util.regex.Matcher m = p.matcher(input);
         StringBuilder sb = new StringBuilder();
         while (m.find()) {
            String name = m.group(1).replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            String b64 = m.group(2).replaceAll("\\s+", "");
            try {
               byte[] decoded = java.util.Base64.getDecoder().decode(b64);
               String lore = new String(decoded, StandardCharsets.UTF_8).replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("\n```yaml\n" + lore + "\n```\n"));
            } catch (Throwable t) {
               m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("[" + name + "]"));
            }
         }
         m.appendTail(sb);
         return sb.toString();
      } catch (Throwable t) {
         return input;
      }
   }

   private static String escapeJson(String input) {
      if (input == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < input.length(); ++i) {
            char ch = input.charAt(i);
            switch (ch) {
               case '\b':
                  sb.append("\\b");
                  break;
               case '\t':
                  sb.append("\\t");
                  break;
               case '\n':
                  sb.append("\\n");
                  break;
               case '\f':
                  sb.append("\\f");
                  break;
               case '\r':
                  sb.append("\\r");
                  break;
               case '"':
                  sb.append("\\\"");
                  break;
               case '\\':
                  sb.append("\\\\");
                  break;
               default:
                  if (ch <= 31) {
                     sb.append(String.format("\\u%04x", ch));
                  } else {
                     sb.append(ch);
                  }
            }
         }

         return sb.toString();
      }
   }
}
