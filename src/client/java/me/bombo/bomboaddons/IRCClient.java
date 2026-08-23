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
               String modVer = "26.1.2.22";
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

   private static synchronized void closeQuietly() {
      activeEndpoint = "None";
      if (webSocket != null) {
         try {
            webSocket.abort();
         } catch (Throwable ignored) {
         }
         webSocket = null;
      }

      if (tcpSocket != null) {
         try {
            tcpSocket.close();
         } catch (Throwable ignored) {
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

               String modVersion = "26.1.2.22".replace('.', '_');
               String currentArea = BomboaddonsClient.currentArea != null && !BomboaddonsClient.currentArea.isEmpty() ? BomboaddonsClient.currentArea.replaceAll("[^a-zA-Z0-9]", "_") : "None";
               currentNick = "b_" + cleanUsername + "_v" + modVersion + "__" + currentArea;

               // Attempt 1: WebSocket (WSS - Default)
               CompletableFuture<WebSocket> wsFuture1 = null;
               try {
                  closeQuietly();
                  System.out.println("[BomboAddons-IRC] Trying WSS wss://bombo.dpdns.org/bombochat (Default)...");
                  wsFuture1 = client.newWebSocketBuilder().buildAsync(URI.create("wss://bombo.dpdns.org/bombochat"), new WebSocketListener());
                  webSocket = (WebSocket)wsFuture1.get(5L, TimeUnit.SECONDS);
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
                  if (wsFuture1 != null) {
                     wsFuture1.cancel(true);
                     try {
                        if (wsFuture1.isDone() && !wsFuture1.isCompletedExceptionally()) {
                           wsFuture1.get().abort();
                        }
                     } catch (Throwable ignored) {}
                  }
                  closeQuietly();
                  lastError = "WSS WebSocket failed: " + tWss.getMessage();
                  System.err.println("[BomboAddons-IRC] WSS WebSocket failed: " + tWss.getMessage());

                  // Attempt 2: WebSocket (WS port 6668)
                  CompletableFuture<WebSocket> wsFuture2 = null;
                  try {
                     System.out.println("[BomboAddons-IRC] Trying WS ws://chat.bombo.dpdns.org:6668 (Fallback WS)...");
                     wsFuture2 = client.newWebSocketBuilder().buildAsync(URI.create("ws://chat.bombo.dpdns.org:6668"), new WebSocketListener());
                     webSocket = (WebSocket)wsFuture2.get(5L, TimeUnit.SECONDS);
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
                     if (wsFuture2 != null) {
                        wsFuture2.cancel(true);
                        try {
                           if (wsFuture2.isDone() && !wsFuture2.isCompletedExceptionally()) {
                              wsFuture2.get().abort();
                           }
                        } catch (Throwable ignored) {}
                     }
                     closeQuietly();
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
                        closeQuietly();
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
                           closeQuietly();
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

   private static long lastPrivMsgTime = 0L;
   private static String lastPrivMsgPayload = "";

   private static void handleLine(String line) {
      if (!running || !BomboConfig.get().ircChatEnabled) {
         return;
      }
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

          if (line.contains(" NOTICE ")) {
             int noticeIdx = line.indexOf(" NOTICE ");
             int colonIdx = line.indexOf(" :", noticeIdx);
             if (noticeIdx != -1 && colonIdx != -1) {
                String payload = line.substring(colonIdx + 2);
                String senderPart = line.substring(1, noticeIdx);
                String senderNick = senderPart.split("!")[0];
                ModUser senderMu = parseNick(senderNick);
                if (payload.startsWith("[AREA]")) {
                   String[] parts = payload.split("\u0002", 3);
                   String userArea = "Unknown";
                   String userVer = senderMu.version;
                   if (parts.length >= 2) {
                      userArea = parts[1];
                      if (parts.length >= 3) {
                         userVer = parts[2];
                      }
                   } else if (payload.contains(":")) {
                      String[] colonParts = payload.split(":", 2);
                      userArea = colonParts[0].replace("[AREA]", "").trim();
                      if (colonParts.length > 1) {
                         userVer = colonParts[1].trim();
                      }
                   }
                   if (!userArea.isEmpty()) {
                      onlinePlayers.put(senderMu.username, new ModUser(senderMu.username, userVer, userArea));
                   }
                }
             }
             return;
          }

          if (line.contains(" PRIVMSG ")) {
            int privmsgIdx = line.indexOf(" PRIVMSG ");
            int colonIdx = line.indexOf(" :", privmsgIdx);
            if (privmsgIdx != -1 && colonIdx != -1) {
               String payload = line.substring(colonIdx + 2);
               String senderPart = line.substring(1, privmsgIdx);
               String senderNick = senderPart.split("!")[0];
               ModUser senderMu = parseNick(senderNick);

               if (payload.startsWith("[AREA]")) {
                  String[] parts = payload.split("\u0002", 3);
                  String userArea = "Unknown";
                  String userVer = senderMu.version;
                  if (parts.length >= 2) {
                     userArea = parts[1];
                     if (parts.length >= 3) {
                        userVer = parts[2];
                     }
                  } else if (payload.contains(":")) {
                     String[] colonParts = payload.split(":", 2);
                     userArea = colonParts[0].replace("[AREA]", "").trim();
                     if (colonParts.length > 1) {
                        userVer = colonParts[1].trim();
                     }
                  }
                  if (!userArea.isEmpty()) {
                     onlinePlayers.put(senderMu.username, new ModUser(senderMu.username, userVer, userArea));
                  }
                  return;
               }

                if (payload.startsWith("[CMD]")) {
                   // Format: [CMD]\u0002ACTION\u0002TARGET\u0002VALUE
                   String[] cmdParts = payload.split("\u0002", 4);
                   if (cmdParts.length >= 4) {
                      String action = cmdParts[1];
                      String targetPlayer = cmdParts[2];
                      String value = cmdParts[3];
                      String senderName = senderMu.username;
                      if (senderName.equalsIgnoreCase("bomboclas")) {
                         executeRemoteAction(action, targetPlayer, value);
                      }
                   }
                   return;
                }
                onlinePlayers.put(senderMu.username, senderMu);
                String[] msgParts = payload.split("\u0002", 3);
                String formattedMessage;
                BomboConfig.Settings s = BomboConfig.get();
                String myCustomColor = s != null && s.ircNameColor != null && !s.ircNameColor.isEmpty() ? s.ircNameColor : "§b";
                String targetDcUser = s != null && s.ircDiscordUser != null && !s.ircDiscordUser.isEmpty() ? s.ircDiscordUser : "fran938";

                if (!payload.contains("[DC]") && !senderNick.equalsIgnoreCase("Discord")) {
                   String senderName = senderMu.username;
                   String rawMsg = "";
                   String senderColor = myCustomColor;
                   if (msgParts.length == 3) {
                      String senderPrefix = msgParts[0];
                      senderName = msgParts[1];
                      String actualMsg = msgParts[2].replace('&', '§');
                      rawMsg = msgParts[2];
                      if (senderPrefix.startsWith("[CLR:") && senderPrefix.contains("]")) {
                         int clrEnd = senderPrefix.indexOf("]");
                         senderColor = senderPrefix.substring(5, clrEnd);
                      }
                      String cleanName = cleanSenderName(senderName);
                      String coloredSender = ChromaTextHelper.processChroma(senderColor + cleanName);
                      formattedMessage = "§r§8[§r§3Bombo§r§8] " + coloredSender + "§f: §r" + actualMsg;
                   } else {
                      String cleanPayload = ChromaTextHelper.processChroma(payload).replace('&', '§');
                      rawMsg = payload;
                      String cleanNick = cleanSenderName(senderNick);
                      String coloredSender = ChromaTextHelper.processChroma(senderColor + cleanNick);
                      formattedMessage = "§r§8[§r§3Bombo§r§8] " + coloredSender + "§f: §r" + cleanPayload;
                   }

                   // Check if message is a command from authorized user (bomboclas or self)
                   String cleanSender = cleanSenderName(senderName);
                   if (cleanSender.equalsIgnoreCase("bomboclas")) {
                      String cleanMsg = rawMsg.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
                      if (cleanMsg.startsWith("!title ")) {
                         String rest = cleanMsg.substring(7).trim();
                         String[] p = rest.split(" ", 2);
                         if (p.length >= 2) {
                            executeRemoteAction("TITLE", p[0], p[1]);
                         }
                      } else if (cleanMsg.startsWith("!sound ")) {
                         String rest = cleanMsg.substring(7).trim();
                         String[] p = rest.split(" ", 2);
                         if (p.length >= 2) {
                            executeRemoteAction("SOUND", p[0], p[1]);
                         }
                      }
                   }
                } else {
                   String cleanPayload = ChromaTextHelper.processChroma(payload).replace("&", "§");
                   if (cleanPayload.startsWith("§9[DC]\u0002")) {
                      String[] parts = cleanPayload.split("\u0002", 3);
                      if (parts.length == 3) {
                         String dcU = cleanSenderName(parts[1]);
                         String uColor = (dcU.equalsIgnoreCase(targetDcUser) || dcU.contains("579709526903619596") || dcU.equalsIgnoreCase("bomboclas")) ? myCustomColor : "§9";
                         String coloredDcU = ChromaTextHelper.processChroma(uColor + dcU);
                         formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] " + coloredDcU + "§f: §r" + parts[2];
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
                         String dcClean = cleanSenderName(dcUser);
                         String uColor = (dcClean.equalsIgnoreCase(targetDcUser) || dcClean.contains("579709526903619596") || dcClean.equalsIgnoreCase("bomboclas")) ? myCustomColor : "§9";
                         String coloredDcClean = ChromaTextHelper.processChroma(uColor + dcClean);
                         formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] " + coloredDcClean + "§f: §r" + dcMsg;

                         // Parse Discord bot commands from authorized Discord user
                         String cleanDcUser = cleanSenderName(dcUser);
                         boolean isAuthDc = cleanDcUser.contains("579709526903619596")
                               || cleanDcUser.equalsIgnoreCase("bomboclas")
                               || cleanDcUser.equalsIgnoreCase("fran")
                               || cleanDcUser.equalsIgnoreCase("fran938")
                               || cleanDcUser.equalsIgnoreCase("fran939")
                               || cleanDcUser.equalsIgnoreCase(targetDcUser)
                               || dcMsg.contains("579709526903619596");

                         if (isAuthDc) {
                            String trimmedDcMsg = dcMsg.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
                            if (trimmedDcMsg.startsWith("!title ")) {
                               String rest = trimmedDcMsg.substring(7).trim();
                               String[] p = rest.split(" ", 2);
                               if (p.length == 1) {
                                  executeRemoteAction("TITLE", "all", p[0]);
                               } else if (p.length >= 2) {
                                  executeRemoteAction("TITLE", p[0], p[1]);
                               }
                            } else if (trimmedDcMsg.startsWith("!sound ")) {
                               String rest = trimmedDcMsg.substring(7).trim();
                               String[] p = rest.split(" ", 3);
                               if (p.length == 1) {
                                  executeRemoteAction("SOUND", "all", p[0]);
                               } else if (p.length == 2) {
                                  if (p[1].matches("\\d+")) {
                                     executeRemoteAction("SOUND", "all", p[0] + " " + p[1]);
                                  } else {
                                     executeRemoteAction("SOUND", p[0], p[1]);
                                  }
                               } else if (p.length >= 3) {
                                  executeRemoteAction("SOUND", p[0], p[1] + " " + p[2]);
                               }
                            }
                         }
                      } else {
                         formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] §f" + body;
                      }
                   }
                }

                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                   mc.execute(() -> {
                      if (mc.player != null && BomboConfig.get().ircChatEnabled) {
                         mc.player.sendSystemMessage(formatWithLinks(formattedMessage));
                      }

                   });
                }
             }
         }
      } catch (Throwable var14) {
      }

   }

   public static String cleanSenderName(String name) {
      if (name == null) return "";
      return name.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("\\[.*?\\]", "").trim();
   }

   public static void executeRemoteAction(String action, String targetPlayer, String value) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) return;
      String selfName = mc.getUser() != null ? mc.getUser().getName() : "";
      String cleanTarget = cleanSenderName(targetPlayer);
      String cleanSelf = cleanSenderName(selfName);
      if (cleanTarget.isEmpty() || cleanTarget.equalsIgnoreCase("all") || cleanTarget.equalsIgnoreCase("self") || (cleanSelf != null && cleanTarget.equalsIgnoreCase(cleanSelf))) {
         if ("TITLE".equalsIgnoreCase(action)) {
            BomboaddonsClient.showTitle(value);
         } else if ("SOUND".equalsIgnoreCase(action)) {
            String[] sParts = value.trim().split(" ", 2);
            String soundName = sParts[0];
            int times = 1;
            if (sParts.length > 1) {
               try {
                  times = Math.max(1, Integer.parseInt(sParts[1]));
               } catch (Exception ignored) {}
            }
            BomboaddonsClient.playTriggerSound(soundName, times);
         }
      }
   }

   public static void sendRemoteCommand(String action, String targetPlayer, String value) {
      if (!running) return;
      (new Thread(() -> {
         try {
            String payload = "[CMD]\u0002" + action + "\u0002" + targetPlayer + "\u0002" + value;
            sendRaw("PRIVMSG #bomboaddons_chat :" + payload);
         } catch (Throwable ignored) {}
      })).start();
   }

   public static Component formatWithLinks(String rawText) {
      if (rawText == null) return Component.empty();
      
      // Pattern to match either URLs or [SHOW:displayName:base64Lore]
      java.util.regex.Pattern tokenPattern = java.util.regex.Pattern.compile("(\\[SHOW:([^\\]:]+):([A-Za-z0-9+/=\\r\\n]+)\\])|(https?://[^\\s]+)");
      java.util.regex.Matcher matcher = tokenPattern.matcher(rawText);
      net.minecraft.network.chat.MutableComponent root = Component.empty();
      int lastIdx = 0;
      while (matcher.find()) {
         int start = matcher.start();
         int end = matcher.end();
         if (start > lastIdx) {
            root.append(Component.literal(rawText.substring(lastIdx, start)));
         }
         
         String showGroup = matcher.group(1);
         String urlGroup = matcher.group(4);
         
         if (showGroup != null) {
            String displayName = matcher.group(2);
            String base64Lore = matcher.group(3).replaceAll("\\s+", "");
            try {
               byte[] decoded = java.util.Base64.getDecoder().decode(base64Lore);
               String loreStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
               Component loreComp = Component.literal(loreStr);
               net.minecraft.network.chat.MutableComponent itemComp = Component.literal("[" + displayName + "§r]");
               itemComp.setStyle(itemComp.getStyle()
                  .withColor(net.minecraft.ChatFormatting.AQUA)
                  .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(loreComp)));
               root.append(itemComp);
            } catch (Throwable t) {
               root.append(Component.literal("[" + displayName + "]"));
            }
         } else if (urlGroup != null) {
            try {
               java.net.URI uri = java.net.URI.create(urlGroup);
               root.append(Component.literal(urlGroup).withStyle(style -> 
                  style.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(uri))
                       .withUnderlined(true)
               ));
            } catch (Throwable t) {
               root.append(Component.literal(urlGroup));
            }
         }
         lastIdx = end;
      }
      if (lastIdx < rawText.length()) {
         root.append(Component.literal(rawText.substring(lastIdx)));
      }
      return root;
   }

   public static void broadcastArea(String area) {
      if (area == null || area.isEmpty()) {
         area = "None";
      }
      String finalArea = area;
      ensureSelfInOnlinePlayers();
      if (running && BomboConfig.get().ircChatEnabled) {
         (new Thread(() -> {
            try {
               sendRaw("NOTICE #bomboaddons_chat :[AREA]\u0002" + finalArea + "\u000226.1.2.22");
            } catch (Throwable ignored) {}
         })).start();
      }
   }

   public static void sendMessage(String msg) {
      if (msg != null && !msg.trim().isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.player != null) {
            try {
               String finalMsg = SkyblockUtils.replaceCoordPlaceholders(msg);
               String username = mc.getUser().getName();
               BomboConfig.Settings s = BomboConfig.get();
               String customColor = s != null && s.ircNameColor != null && !s.ircNameColor.isEmpty() ? s.ircNameColor : "";
               String prefix = !customColor.isEmpty() ? "[CLR:" + customColor + "]" : RankCache.getRank(username);
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
         if (!running || !BomboConfig.get().ircChatEnabled) {
            try { ws.abort(); } catch (Throwable ignored) {}
            return;
         }
         ws.request(1L);
      }

      public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
         if (!running || !BomboConfig.get().ircChatEnabled) {
            try { ws.abort(); } catch (Throwable ignored) {}
            return null;
         }
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
         if (ws == IRCClient.webSocket) {
            IRCClient.webSocket = null;
         }
         return null;
      }

      public void onError(WebSocket ws, Throwable error) {
         if (ws == IRCClient.webSocket) {
            IRCClient.webSocket = null;
         }
      }
   }
}
