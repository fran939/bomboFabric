package me.bombo.bomboaddons;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class IRCClient {
   private static WebSocket webSocket;
   private static Socket tcpSocket;
   private static PrintWriter tcpWriter;
   private static Thread clientThread;
   private static boolean running = false;
   private static String currentNick = "";
   public static String activeEndpoint = "None";
   public static String lastError = "Not started yet";
   private static final Map<String, ModUser> onlinePlayers = new ConcurrentHashMap();

   public static Map<String, ModUser> getOnlinePlayers() {
      ensureSelfInOnlinePlayers();
      return onlinePlayers;
   }

   public static void requestNames() {
      sendRaw("NAMES #bomboaddons_chat");
   }

   public static void ensureSelfInOnlinePlayers() {
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getUser() != null) {
            String name = mc.getUser().getName();
            String clean = name.replaceAll("[^a-zA-Z0-9_]", "");
            if (!clean.isEmpty()) {
               String modVer = "26.1.2.20";
               String area = BomboaddonsClient.currentArea != null && !BomboaddonsClient.currentArea.isEmpty() ? BomboaddonsClient.currentArea : "None";
               onlinePlayers.put(clean, new ModUser(clean, modVer, area));
            }
         }
      } catch (Throwable var4) {
      }
   }

   public static ModUser parseNick(String nick) {
      if (nick != null && nick.startsWith("b_")) {
         String stripped = nick.substring(2);
         int vIdx = stripped.indexOf("_v");
         if (vIdx != -1) {
            String user = stripped.substring(0, vIdx);
            String rest = stripped.substring(vIdx + 2);
            int aIdx = rest.indexOf("__");
            String verStr;
            String areaStr = "Unknown";
            if (aIdx != -1) {
               verStr = rest.substring(0, aIdx).replace('_', '.');
               areaStr = rest.substring(aIdx + 2).replace('_', ' ');
            } else {
               verStr = rest.replace('_', '.');
            }
            return new ModUser(user, verStr, areaStr);
         } else {
            return new ModUser(stripped, "Unknown (< 26.2.2)", "Unknown");
         }
      } else {
         return new ModUser(nick != null ? nick : "Player", "Unknown", "Unknown");
      }
   }

   public static boolean isConnected() {
      return running && (webSocket != null || tcpSocket != null && !tcpSocket.isClosed() && tcpSocket.isConnected());
   }

   public static boolean isWebSocketConnected() {
      return running && webSocket != null;
   }

   public static boolean isTcpConnected() {
      return running && tcpSocket != null && !tcpSocket.isClosed() && tcpSocket.isConnected();
   }

   public static String getConnectionType() {
      if (webSocket != null) {
         return "WebSocket (WSS/WS)";
      } else if (tcpSocket != null && !tcpSocket.isClosed() && tcpSocket.isConnected()) {
         return "TCP Socket";
      } else {
         return "Disconnected";
      }
   }

   public static void start() {
      if (!running) {
         running = true;
         clientThread = new Thread(IRCClient::runLoop, "IRC-Client-Thread");
         clientThread.setDaemon(true);
         clientThread.start();
      }
   }

   public static void onEnabledToggled() {
      if (!BomboConfig.get().ircChatEnabled) {
         closeQuietly();
      }

   }

   private static void closeQuietly() {
      activeEndpoint = "None";
      if (webSocket != null) {
         try {
            webSocket.sendClose(1000, "Bye");
         } catch (Throwable var2) {
         }

         webSocket = null;
      }

      if (tcpSocket != null) {
         try {
            tcpSocket.close();
         } catch (Throwable var1) {
         }

         tcpSocket = null;
         tcpWriter = null;
      }

   }

   private static HttpClient createInsecureHttpClient() {
      try {
         TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
               return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
         }};
         SSLContext sslContext = SSLContext.getInstance("TLS");
         sslContext.init((KeyManager[])null, trustAllCerts, new SecureRandom());
         return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(5L)).build();
      } catch (Throwable var2) {
         return HttpClient.newBuilder().build();
      }
   }

   private static void runLoop() {
      Random random = new Random();
      HttpClient client = createInsecureHttpClient();

      while(running) {
         if (!BomboConfig.get().ircChatEnabled) {
            closeQuietly();

            try {
               Thread.sleep(1000L);
            } catch (InterruptedException var10) {
               break;
            }
         } else {
            try {
               onlinePlayers.clear();
               Minecraft mc = Minecraft.getInstance();
               String username = "Player";
               if (mc != null && mc.getUser() != null) {
                  username = mc.getUser().getName();
               }

               String cleanUsername = username.replaceAll("[^a-zA-Z0-9_]", "");
               if (cleanUsername.isEmpty()) {
                  cleanUsername = "bombo_" + random.nextInt(10000);
               }

               String modVersion = "26.1.2.20".replace('.', '_');
               String currentArea = BomboaddonsClient.currentArea != null && !BomboaddonsClient.currentArea.isEmpty() ? BomboaddonsClient.currentArea.replaceAll("[^a-zA-Z0-9]", "_") : "None";
               currentNick = "b_" + cleanUsername + "_v" + modVersion + "__" + currentArea;

               // Attempt 1: WebSocket (WSS - Default)
               try {
                  System.out.println("[BomboAddons-IRC] Trying WSS wss://bombo.dpdns.org/bombochat (Default)...");
                  CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder().buildAsync(URI.create("wss://bombo.dpdns.org/bombochat"), new WebSocketListener());
                  webSocket = (WebSocket)wsFuture.get(5L, TimeUnit.SECONDS);
                  sendRaw("NICK " + currentNick);
                  sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                  sendRaw("JOIN #bomboaddons_chat");
                  sendRaw("NAMES #bomboaddons_chat");
                  activeEndpoint = "wss://bombo.dpdns.org/bombochat (WebSocket)";
                  lastError = "None (Connected via WebSocket WSS)";
                  System.out.println("[BomboAddons-IRC] Connected via WebSocket WSS!");

                  while(running && BomboConfig.get().ircChatEnabled && webSocket != null) {
                     Thread.sleep(2000L);
                  }

                  closeQuietly();
                  continue;
               } catch (Throwable tWss) {
                  lastError = "WSS WebSocket failed: " + tWss.getMessage();
                  System.err.println("[BomboAddons-IRC] WSS WebSocket failed: " + tWss.getMessage());

                  // Attempt 2: WebSocket (WS port 6668)
                  try {
                     System.out.println("[BomboAddons-IRC] Trying WS ws://chat.bombo.dpdns.org:6668 (Fallback WS)...");
                     CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder().buildAsync(URI.create("ws://chat.bombo.dpdns.org:6668"), new WebSocketListener());
                     webSocket = (WebSocket)wsFuture.get(5L, TimeUnit.SECONDS);
                     sendRaw("NICK " + currentNick);
                     sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                     sendRaw("JOIN #bomboaddons_chat");
                     sendRaw("NAMES #bomboaddons_chat");
                     activeEndpoint = "ws://chat.bombo.dpdns.org:6668 (WebSocket)";
                     lastError = "None (Connected via WebSocket WS 6668)";
                     System.out.println("[BomboAddons-IRC] Connected via WebSocket WS 6668!");

                     while(running && BomboConfig.get().ircChatEnabled && webSocket != null) {
                        Thread.sleep(2000L);
                     }

                     closeQuietly();
                     continue;
                  } catch (Throwable tWs) {
                     lastError = "WS 6668 failed: " + tWs.getMessage();
                     System.err.println("[BomboAddons-IRC] WS 6668 failed: " + tWs.getMessage());

                     // Attempt 3: TCP Socket (chat.bombo.dpdns.org:6667)
                     try {
                        System.out.println("[BomboAddons-IRC] Trying TCP socket to chat.bombo.dpdns.org:6667 (Fallback TCP)...");
                        Socket sock = new Socket();
                        sock.connect(new InetSocketAddress("chat.bombo.dpdns.org", 6667), 4000);
                        tcpSocket = sock;
                        tcpWriter = new PrintWriter(sock.getOutputStream(), true);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                        sendRaw("NICK " + currentNick);
                        sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                        sendRaw("JOIN #bomboaddons_chat");
                        sendRaw("NAMES #bomboaddons_chat");
                        activeEndpoint = "chat.bombo.dpdns.org:6667 (TCP)";
                        lastError = "None (Connected via TCP)";
                        System.out.println("[BomboAddons-IRC] Connected via TCP!");

                        while(running && BomboConfig.get().ircChatEnabled && tcpSocket != null && !tcpSocket.isClosed()) {
                           String line = reader.readLine();
                           if (line == null) {
                              break;
                           }

                           handleLine(line);
                        }

                        closeQuietly();
                        continue;
                     } catch (Throwable tTcp1) {
                        lastError = "TCP 6667 failed: " + tTcp1.getMessage();
                        System.err.println("[BomboAddons-IRC] TCP 6667 failed: " + tTcp1.getMessage());

                        // Attempt 4: Direct IP TCP (51.170.56.117:6667)
                        try {
                           System.out.println("[BomboAddons-IRC] Trying direct IP TCP 51.170.56.117:6667 (Fallback Direct TCP)...");
                           Socket sock = new Socket();
                           sock.connect(new InetSocketAddress("51.170.56.117", 6667), 4000);
                           tcpSocket = sock;
                           tcpWriter = new PrintWriter(sock.getOutputStream(), true);
                           BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));
                           sendRaw("NICK " + currentNick);
                           sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                           sendRaw("JOIN #bomboaddons_chat");
                           sendRaw("NAMES #bomboaddons_chat");
                           activeEndpoint = "51.170.56.117:6667 (Direct TCP)";
                           lastError = "None (Connected via Direct IP TCP)";
                           System.out.println("[BomboAddons-IRC] Connected via Direct IP TCP!");

                           while(running && BomboConfig.get().ircChatEnabled && tcpSocket != null && !tcpSocket.isClosed()) {
                              String line = reader.readLine();
                              if (line == null) {
                                 break;
                              }

                              handleLine(line);
                           }

                           closeQuietly();
                           continue;
                        } catch (Throwable tTcp2) {
                           lastError = "Direct IP 6667 failed: " + tTcp2.getMessage();
                           System.err.println("[BomboAddons-IRC] Direct IP 6667 failed: " + tTcp2.getMessage());
                        }
                     }
                  }
               }
            } catch (Throwable var15) {
               closeQuietly();
            }

            try {
               Thread.sleep(3000L);
            } catch (InterruptedException var9) {
               break;
            }
         }
      }

   }

   private static void sendRaw(String msg) {
      if (BomboConfig.get().debugChat) {
         DebugUtils.debug("chat", "§e[IRC-Out] " + msg);
      }

      if (webSocket != null) {
         try {
            webSocket.sendText(msg, true);
         } catch (Throwable var3) {
         }
      }

      if (tcpWriter != null) {
         try {
            tcpWriter.println(msg);
         } catch (Throwable var2) {
         }
      }

   }

   private static void handleLine(String line) {
      try {
         if (BomboConfig.get().debugChat) {
            DebugUtils.debug("chat", "§b[IRC-In] " + line);
         }

         if (line.startsWith("PING ")) {
            sendRaw("PONG " + line.substring(5));
            return;
         }

         if (line.contains(" 001 ") || line.contains(" 376 ")) {
            sendRaw("JOIN #bomboaddons_chat");
            sendRaw("NAMES #bomboaddons_chat");
         }

         if (line.contains(" 353 ")) {
            int colonIdx = line.indexOf(" :", line.indexOf(" 353 "));
            if (colonIdx != -1) {
               String namesStr = line.substring(colonIdx + 2);

               for(String name : namesStr.split(" ")) {
                  String clean = name.trim().replaceAll("^[+@%~&]", "");
                  if (!clean.isEmpty()) {
                     ModUser mu = parseNick(clean);
                     onlinePlayers.put(mu.username, mu);
                  }
               }
            }
         }

         if (line.contains(" JOIN ")) {
            int exClam = line.indexOf("!");
            if (line.startsWith(":") && exClam != -1) {
               String nick = line.substring(1, exClam);
               if (!nick.isEmpty()) {
                  ModUser mu = parseNick(nick);
                  onlinePlayers.put(mu.username, mu);
               }
            }
         }

         if ((line.contains(" PART ") || line.contains(" QUIT ")) && line.startsWith(":")) {
            int exClam = line.indexOf("!");
            if (exClam != -1) {
               String nick = line.substring(1, exClam);
               ModUser mu = parseNick(nick);
               onlinePlayers.remove(mu.username);
               onlinePlayers.remove(nick);
            }
         }

         if (line.contains(" PRIVMSG ")) {
            int privmsgIdx = line.indexOf(" PRIVMSG ");
            int colonIdx = line.indexOf(" :", privmsgIdx);
            if (privmsgIdx != -1 && colonIdx != -1) {
               String senderPart = line.substring(1, privmsgIdx);
               String senderNick = senderPart.split("!")[0];
               ModUser senderMu = parseNick(senderNick);
               onlinePlayers.put(senderMu.username, senderMu);
               String payload = line.substring(colonIdx + 2);
               String[] msgParts = payload.split("\u0002", 3);
               String formattedMessage;
               if (!payload.contains("[DC]") && !senderNick.equalsIgnoreCase("Discord")) {
                  if (msgParts.length == 3) {
                     String rankPrefix = msgParts[0];
                     String realUsername = msgParts[1];
                     String actualMsg = msgParts[2].replace('&', '§');
                     formattedMessage = "§r§8[§r§3Bombo§r§8] §r" + rankPrefix + realUsername + "§f: §r" + actualMsg;
                  } else {
                     String rankPrefix = RankCache.getRank(senderNick);
                     if (!rankPrefix.isEmpty() && !rankPrefix.endsWith(" ")) {
                        rankPrefix = rankPrefix + " ";
                     }

                     String cleanPayload = ChromaTextHelper.processChroma(payload).replace('&', '§');
                     formattedMessage = "§r§8[§r§3Bombo§r§8] §r" + rankPrefix + senderNick + "§f: §r" + cleanPayload;
                  }
               } else {
                  String cleanPayload = ChromaTextHelper.processChroma(payload).replace("&", "§");
                  if (cleanPayload.startsWith("§9[DC]\u0002")) {
                     String[] parts = cleanPayload.split("\u0002", 3);
                     if (parts.length == 3) {
                        formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] " + parts[1] + "§f: §r" + parts[2];
                     } else {
                        formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] §f" + cleanPayload;
                     }
                  } else {
                     String body = cleanPayload;
                     if (cleanPayload.startsWith("§9[DC] ")) {
                        body = cleanPayload.substring(7);
                     } else if (cleanPayload.startsWith("[DC] ")) {
                        body = cleanPayload.substring(5);
                     }

                     int cIdx = body.indexOf(": ");
                     if (cIdx != -1) {
                        String dcUser = body.substring(0, cIdx).trim();
                        String dcMsg = body.substring(cIdx + 2);
                        formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] " + dcUser + "§f: §r" + dcMsg;
                     } else {
                        formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] §f" + body;
                     }
                  }
               }

               Minecraft mc = Minecraft.getInstance();
               if (mc != null && mc.player != null) {
                  mc.execute(() -> {
                     if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal(formattedMessage));
                     }

                  });
               }
            }
         }
      } catch (Throwable var14) {
      }

   }

   public static void sendMessage(String msg) {
      if (msg != null && !msg.trim().isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.player != null) {
            try {
               String finalMsg = SkyblockUtils.replaceCoordPlaceholders(msg);
               String username = mc.getUser().getName();
               String prefix = RankCache.getRank(username);
               String coloredMsg = ChromaTextHelper.processChroma(finalMsg).replace('&', '§');
               String payload = prefix + "\u0002" + username + "\u0002" + coloredMsg;
               (new Thread(() -> {
                  try {
                     if (BomboConfig.get().debugChat) {
                        DebugUtils.debug("chat", "§e[IRC-Sending] Transmitting: " + finalMsg);
                     }

                     sendRaw("PRIVMSG #bomboaddons_chat :" + payload);
                     if (BomboConfig.get().debugChat) {
                        DebugUtils.debug("chat", "§a[IRC-Sent] Transmitted to server socket OK!");
                     }
                  } catch (Throwable t) {
                     if (BomboConfig.get().debugChat) {
                        DebugUtils.debug("chat", "§c[IRC-Error] Failed to send message: " + t.getMessage());
                     }
                  }

               })).start();
            } catch (Throwable t) {
               if (BomboConfig.get().debugChat) {
                  DebugUtils.debug("chat", "§c[IRC-Error] Failed to prepare message: " + t.getMessage());
               }
            }

         }
      }
   }

   public static class ModUser {
      public final String username;
      public final String version;
      public final String area;

      public ModUser(String username, String version) {
         this(username, version, "Unknown");
      }

      public ModUser(String username, String version, String area) {
         this.username = username;
         this.version = version;
         this.area = area != null && !area.isEmpty() ? area : "Unknown";
      }
   }

   private static class WebSocketListener implements WebSocket.Listener {
      private final StringBuilder textBuffer = new StringBuilder();

      public void onOpen(WebSocket ws) {
         ws.request(1L);
      }

      public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
         this.textBuffer.append(data);
         if (last) {
            String fullMessage = this.textBuffer.toString();
            this.textBuffer.setLength(0);

            for(String line : fullMessage.split("\r?\n")) {
               if (!line.trim().isEmpty()) {
                  IRCClient.handleLine(line.trim());
               }
            }
         }

         ws.request(1L);
         return null;
      }

      public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
         IRCClient.webSocket = null;
         return null;
      }

      public void onError(WebSocket ws, Throwable error) {
         IRCClient.webSocket = null;
      }
   }
}
