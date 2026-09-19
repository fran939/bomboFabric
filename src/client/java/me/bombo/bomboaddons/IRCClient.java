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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

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
   public static final Map<String, String> playerPartyData = new ConcurrentHashMap<>();

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
               String modVer = BomboaddonsClient.MOD_VERSION;
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

               String modVersion = BomboaddonsClient.MOD_VERSION.replace('.', '_');
               String currentArea = BomboaddonsClient.currentArea != null && !BomboaddonsClient.currentArea.isEmpty() ? BomboaddonsClient.currentArea.replaceAll("[^a-zA-Z0-9]", "_") : "None";
               currentNick = "b_" + cleanUsername + "_v" + modVersion + "__" + currentArea;
               String apiKey = me.bombo.bomboaddons.features.auth.BomboApiKeyManager.getApiKey();
               if (BomboConfig.get().apiKeyDebug || BomboConfig.get().apiDebug) {
                  System.out.println("[BomboAddons-IRC] Connecting with API Key: " + (apiKey.isEmpty() ? "(NONE)" : apiKey));
               }

               // Attempt 1: WebSocket (WSS - Default)
               CompletableFuture<WebSocket> wsFuture1 = null;
               try {
                  closeQuietly();
                  System.out.println("[BomboAddons-IRC] Trying WSS wss://bombo.dpdns.org/bombochat (Default)...");
                  var wsBuilder1 = client.newWebSocketBuilder();
                  if (!apiKey.isEmpty()) {
                     wsBuilder1.header("Api-Key", apiKey);
                     wsBuilder1.header("X-Api-Key", apiKey);
                  }
                  wsFuture1 = wsBuilder1.buildAsync(URI.create("wss://bombo.dpdns.org/bombochat"), new WebSocketListener());
                  webSocket = (WebSocket)wsFuture1.get(5L, TimeUnit.SECONDS);
                  if (!apiKey.isEmpty()) sendRaw("PASS " + apiKey);
                  sendRaw("NICK " + currentNick);
                  sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                  sendRaw("JOIN #bomboaddons_chat");
                  sendRaw("NAMES #bomboaddons_chat");
                  activeEndpoint = "wss://bombo.dpdns.org/bombochat (WebSocket)";
                  lastError = "None (Connected via WebSocket WSS)";
                  System.out.println("[BomboAddons-IRC] Connected via WebSocket WSS!");
                  onConnected();

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
                     var wsBuilder2 = client.newWebSocketBuilder();
                     if (!apiKey.isEmpty()) {
                        wsBuilder2.header("Api-Key", apiKey);
                        wsBuilder2.header("X-Api-Key", apiKey);
                     }
                     wsFuture2 = wsBuilder2.buildAsync(URI.create("ws://chat.bombo.dpdns.org:6668"), new WebSocketListener());
                     webSocket = (WebSocket)wsFuture2.get(5L, TimeUnit.SECONDS);
                     if (!apiKey.isEmpty()) sendRaw("PASS " + apiKey);
                     sendRaw("NICK " + currentNick);
                     sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                     sendRaw("JOIN #bomboaddons_chat");
                     sendRaw("NAMES #bomboaddons_chat");
                     activeEndpoint = "ws://chat.bombo.dpdns.org:6668 (WebSocket)";
                     lastError = "None (Connected via WebSocket WS 6668)";
                     System.out.println("[BomboAddons-IRC] Connected via WebSocket WS 6668!");
                     onConnected();

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
                        if (!apiKey.isEmpty()) sendRaw("PASS " + apiKey);
                        sendRaw("NICK " + currentNick);
                        sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                        sendRaw("JOIN #bomboaddons_chat");
                        sendRaw("NAMES #bomboaddons_chat");
                        activeEndpoint = "chat.bombo.dpdns.org:6667 (TCP)";
                        lastError = "None (Connected via TCP)";
                        System.out.println("[BomboAddons-IRC] Connected via TCP!");
                        onConnected();

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
                           if (!apiKey.isEmpty()) sendRaw("PASS " + apiKey);
                           sendRaw("NICK " + currentNick);
                           sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                           sendRaw("JOIN #bomboaddons_chat");
                           sendRaw("NAMES #bomboaddons_chat");
                           activeEndpoint = "51.170.56.117:6667 (Direct TCP)";
                           lastError = "None (Connected via Direct IP TCP)";
                           System.out.println("[BomboAddons-IRC] Connected via Direct IP TCP!");
                           onConnected();

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

   public static void sendRaw(String msg) {
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
                if (payload.contains("[AREA]")) {
                   String userArea = "Unknown";
                   String userVer = senderMu.version;
                   String targetUser = senderMu.username;
                   if (payload.startsWith("[CLR:") && payload.contains("]")) {
                      int clrEnd = payload.indexOf("]");
                      String meta = payload.substring(5, clrEnd);
                      for (String token : meta.split("\\|")) {
                         if (token.startsWith("LOC:")) userArea = token.substring(4).trim();
                         else if (token.startsWith("IGN:")) targetUser = token.substring(4).trim();
                         else if (token.startsWith("NAME:")) onlinePlayers.put(token.substring(5).trim(), new ModUser(token.substring(5).trim(), userVer, userArea));
                         else if (token.startsWith("PARTY:")) {
                            String p = token.substring(6).trim();
                            if (!p.isEmpty()) playerPartyData.put(targetUser.toLowerCase(), formatPartySummaryFromSerialized(p));
                         }
                      }
                   }
                   int areaIdx = payload.indexOf("[AREA]");
                   String afterArea = payload.substring(areaIdx);
                   String[] parts = afterArea.split("\u0002");
                   if (parts.length >= 2) {
                      userArea = parts[1];
                      if (parts.length >= 3) {
                         userVer = parts[2];
                      }
                   }
                   if (!userArea.isEmpty()) {
                      onlinePlayers.put(senderMu.username, new ModUser(senderMu.username, userVer, userArea));
                      onlinePlayers.put(targetUser, new ModUser(targetUser, userVer, userArea));
                   }
                } else if (payload.startsWith("[EGG]")) {
                   // Format: [EGG]\u0002location\u0002eggType\u0002x\u0002y\u0002z
                   String[] parts = payload.split("\u0002");
                   if (parts.length >= 6) {
                      String loc = parts[1];
                      String eggTypeStr = parts[2];
                      try {
                         int x = Integer.parseInt(parts[3]);
                         int y = Integer.parseInt(parts[4]);
                         int z = Integer.parseInt(parts[5]);
                         net.minecraft.core.BlockPos eggPos = new net.minecraft.core.BlockPos(x, y, z);
                         Minecraft mc = Minecraft.getInstance();
                         if (mc != null) {
                            mc.execute(() -> me.bombo.bomboaddons.eggfinder.EggFinder.onWebsocketMessage(eggTypeStr, eggPos));
                         }
                      } catch (Throwable ignored) {}
                   }
                 } else if (payload.startsWith("[META_CUSTOM_ITEM]")) {
                     // Format: [META_CUSTOM_ITEM]\u0002itemKey\u0002itemUuid\u0002material\u0002name\u0002b64Lore[\u0002armorColor]
                     String[] parts = payload.split("\u0002", 7);
                     if (parts.length >= 6) {
                        String k = parts[1];
                        String uuid = parts[2];
                        String mat = parts[3];
                        String nm = parts[4];
                        String b64Lore = parts[5];
                        String armorCol = parts.length >= 7 ? parts[6] : "";
                        String lr = "";
                        try {
                           if (!b64Lore.isEmpty()) {
                              lr = new String(java.util.Base64.getDecoder().decode(b64Lore), java.nio.charset.StandardCharsets.UTF_8);
                           }
                        } catch (Throwable ignored) {}
                        BomboConfig.CustomItemOverride cov = new BomboConfig.CustomItemOverride(mat, nm, lr, null, armorCol);
                        if (!uuid.isEmpty()) {
                           BomboConfig.get().customItemOverrides.put(uuid, cov);
                        }
                        if (!k.isEmpty()) {
                           BomboConfig.get().customItemOverrides.put(k, cov);
                        }
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
                       // Check if message is a command from authorized user (bomboclas or self)
                       String senderName = senderMu.username;
                       if (senderName.equalsIgnoreCase("bomboclas")) {
                          executeRemoteAction(action, targetPlayer, value);
                       }
                    }
                    return;
                 }

                 if (payload.startsWith("[CUSTOM_ITEM]")) {
                    // Format: [CUSTOM_ITEM]\u0002itemKey\u0002material\u0002name\u0002base64Lore
                    String[] itemParts = payload.split("\u0002", 5);
                    if (itemParts.length >= 4) {
                       String key = itemParts[1].trim();
                       String mat = itemParts[2].trim();
                       String name = itemParts[3].trim();
                       String lore = "";
                       if (itemParts.length >= 5 && !itemParts[4].isEmpty()) {
                          try {
                             byte[] decoded = java.util.Base64.getDecoder().decode(itemParts[4].trim());
                             lore = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                          } catch (Throwable ignored) {}
                       }
                       if (!key.isEmpty()) {
                          BomboConfig.get().customItemOverrides.put(key, new BomboConfig.CustomItemOverride(mat, name, lore));
                       }
                    }
                    return;
                 }

                 onlinePlayers.put(senderMu.username, senderMu);
                 String[] msgParts = payload.split("\u0002", 3);
                 Component fullDisplayComponent;
                 BomboConfig.Settings s = BomboConfig.get();
                 String myCustomColor = s != null && s.ircNameColor != null && !s.ircNameColor.isEmpty() ? s.ircNameColor : "§b";
                 String targetDcUser = s != null && s.ircDiscordUser != null && !s.ircDiscordUser.isEmpty() ? s.ircDiscordUser : "fran938";

                 if (!payload.contains("[DC]") && !senderNick.equalsIgnoreCase("Discord")) {
                    String senderName = senderMu.username;
                    String realIgn = senderMu.username;
                    String rawMsg = "";
                    String senderColor = myCustomColor;
                    String senderArea = senderMu.area;

                    if (msgParts.length == 3) {
                       String senderPrefix = msgParts[0];
                       senderName = msgParts[1];
                       realIgn = senderName;
                       rawMsg = msgParts[2];
                       if (senderPrefix.startsWith("[CLR:") && senderPrefix.contains("]")) {
                          int clrEnd = senderPrefix.indexOf("]");
                          String metaContent = senderPrefix.substring(5, clrEnd);
                          String[] metaTokens = metaContent.split("\\|");
                          for (String token : metaTokens) {
                             if (token.startsWith("UUID:")) {
                                // sender UUID
                             } else if (token.startsWith("LOC:")) {
                                senderArea = token.substring(4).trim();
                             } else if (token.startsWith("IGN:")) {
                                String ign = token.substring(4).trim();
                                if (!ign.isEmpty()) realIgn = ign;
                             } else if (token.startsWith("NAME:")) {
                                String cName = token.substring(5).trim();
                                if (!cName.isEmpty()) senderName = cName;
                             } else if (token.startsWith("PARTY:")) {
                                String pData = token.substring(6).trim();
                                if (!pData.isEmpty()) {
                                   playerPartyData.put(realIgn.toLowerCase(), formatPartySummaryFromSerialized(pData));
                                }
                             } else if (token.startsWith("§") || token.startsWith("#")) {
                                senderColor = token;
                             }
                          }
                       } else if (!senderPrefix.isEmpty()) {
                          senderColor = senderPrefix;
                       }
                    } else if (msgParts.length == 2) {
                       senderName = msgParts[0];
                       realIgn = senderName;
                       rawMsg = msgParts[1];
                    } else {
                       rawMsg = payload;
                       if (payload.contains(": ")) {
                          int cIdx = payload.indexOf(": ");
                          senderName = payload.substring(0, cIdx).trim();
                          realIgn = senderName;
                          rawMsg = payload.substring(cIdx + 2);
                       } else {
                          senderName = senderNick;
                          realIgn = senderName;
                       }
                    }

                    // Sanitize rawMsg in case of bracket leakage
                    if (rawMsg.startsWith("[CLR:") && rawMsg.contains("]")) {
                       rawMsg = rawMsg.substring(rawMsg.indexOf("]") + 1).trim();
                    }

                    String cleanName = cleanSenderName(senderName);
                    String cleanIgn = cleanSenderName(realIgn);
                    if (cleanIgn.isEmpty()) cleanIgn = cleanName;
                    if (!cleanName.equalsIgnoreCase(cleanIgn)) {
                       linkDiscordUser(cleanName, cleanIgn);
                    }

                    if (senderArea != null && !senderArea.isEmpty() && !senderArea.equalsIgnoreCase("Unknown")) {
                       onlinePlayers.put(cleanName, new ModUser(cleanName, senderMu.version, senderArea));
                       onlinePlayers.put(cleanIgn, new ModUser(cleanIgn, senderMu.version, senderArea));
                       onlinePlayers.put(senderMu.username, new ModUser(senderMu.username, senderMu.version, senderArea));
                    }

                    // Trigger background networth update for the real Minecraft account
                    me.bombo.bomboaddons.features.profile.ProfileFetcher.silentFetchProfile(cleanIgn);

                    net.minecraft.network.chat.MutableComponent playerComp = buildHoverablePlayerComponent(cleanName, senderColor, cleanIgn);
                    Component msgBodyComp = formatWithLinks(rawMsg);
                    fullDisplayComponent = Component.literal("§r§8[§r§3Bombo§r§8] ").append(playerComp).append(Component.literal("§f: §r")).append(msgBodyComp);

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
                    String dcSender = "Discord";
                    String dcMsg = cleanPayload;
                    if (cleanPayload.startsWith("§9[DC]\u0002")) {
                       String[] parts = cleanPayload.split("\u0002", 3);
                       if (parts.length == 3) {
                          dcSender = cleanSenderName(parts[1]);
                          dcMsg = parts[2];
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
                          dcSender = cleanSenderName(body.substring(0, cIdx).trim());
                          dcMsg = body.substring(cIdx + 2);
                       }
                    }

                    String uColor = (dcSender.equalsIgnoreCase(targetDcUser) || dcSender.contains("579709526903619596") || dcSender.equalsIgnoreCase("bomboclas")) ? myCustomColor : "§9";
                    String linkedIgn = getLinkedIgn(dcSender);
                    net.minecraft.network.chat.MutableComponent dcPlayerComp = buildHoverablePlayerComponent(dcSender, uColor, linkedIgn);
                    Component msgBodyComp = formatWithLinks(dcMsg);
                    fullDisplayComponent = Component.literal("§r§8[§r§3Bombo§r§8] §9[DC] ").append(dcPlayerComp).append(Component.literal("§f: §r")).append(msgBodyComp);

                    // Check for link command in Discord message: !link <ign>
                    String cleanDcMsg = dcMsg.trim().replaceAll("(?i)§[0-9a-fk-or]", "");
                    if (cleanDcMsg.startsWith("!link ")) {
                       String linkTarget = cleanDcMsg.substring(6).trim();
                       if (!linkTarget.isEmpty()) {
                          discordLinks.put(dcSender.toLowerCase(), linkTarget);
                          saveDiscordLinks();
                       }
                    }

                    // Parse Discord bot commands from authorized Discord user
                    boolean isAuthDc = dcSender.contains("579709526903619596")
                          || dcSender.equalsIgnoreCase("bomboclas")
                          || dcSender.equalsIgnoreCase("fran")
                          || dcSender.equalsIgnoreCase("fran938")
                          || dcSender.equalsIgnoreCase("fran939")
                          || dcSender.equalsIgnoreCase(targetDcUser)
                          || dcMsg.contains("579709526903619596");

                    if (isAuthDc) {
                       String trimmedDcMsg = cleanDcMsg;
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
                          String[] p = trimmedDcMsg.split(" ", 3);
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
                 }

                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.player != null) {
                   mc.execute(() -> {
                      if (mc.player != null && BomboConfig.get().ircChatEnabled) {
                         mc.player.sendSystemMessage(fullDisplayComponent);
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
      
      String emojiText = me.bombo.bomboaddons.util.EmojiShortcodeHelper.replaceEmojis(rawText);
      String processedText = ChromaTextHelper.processChroma(emojiText);
      // Pattern to match either URLs or [SHOW:displayName:base64Lore]
      java.util.regex.Pattern tokenPattern = java.util.regex.Pattern.compile("(\\[SHOW:([^\\]:]+):([A-Za-z0-9+/=\\r\\n]+)\\])|(https?://[^\\s]+)");
      java.util.regex.Matcher matcher = tokenPattern.matcher(processedText);
      net.minecraft.network.chat.MutableComponent root = Component.empty();
      int lastIdx = 0;
      while (matcher.find()) {
         int start = matcher.start();
         int end = matcher.end();
         if (start > lastIdx) {
            root.append(ChromaTextHelper.parseFormattedText(processedText.substring(lastIdx, start)));
         }
         
         String showGroup = matcher.group(1);
         String urlGroup = matcher.group(4);
         
         if (showGroup != null) {
            String displayName = matcher.group(2);
            String base64Lore = matcher.group(3).replaceAll("\\s+", "");
            try {
               byte[] decoded = java.util.Base64.getDecoder().decode(base64Lore);
               String loreStr = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
               net.minecraft.network.chat.HoverEvent hoverEvent = SBECommands.createHoverEvent(loreStr);
               net.minecraft.network.chat.MutableComponent itemComp = ChromaTextHelper.parseFormattedText("[" + displayName + "§r]");
               Style itemStyle = itemComp.getStyle();
               if (hoverEvent != null) {
                  itemStyle = itemStyle.withHoverEvent(hoverEvent);
               } else {
                  itemStyle = itemStyle.withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(ChromaTextHelper.parseFormattedText(loreStr)));
               }
               itemComp.setStyle(itemStyle);
               root.append(itemComp);
            } catch (Throwable t) {
               root.append(ChromaTextHelper.parseFormattedText("[" + displayName + "§r]"));
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
      if (lastIdx < processedText.length()) {
         root.append(ChromaTextHelper.parseFormattedText(processedText.substring(lastIdx)));
      }
      return root;
   }

   private static final Map<String, String> discordLinks = new ConcurrentHashMap();
   private static final File DISCORD_LINKS_FILE = new File(Minecraft.getInstance().gameDirectory, "config/bomboaddons/discord_links.json");

   static {
      loadDiscordLinks();
   }

   private static void loadDiscordLinks() {
      // Default link for fran/fran938 -> bomboclas
      discordLinks.put("fran", "bomboclas");
      discordLinks.put("fran938", "bomboclas");
      discordLinks.put("fran939", "bomboclas");
      try {
         if (DISCORD_LINKS_FILE.exists()) {
            String content = Files.readString(DISCORD_LINKS_FILE.toPath());
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
               if (entry.getValue().isJsonPrimitive()) {
                  discordLinks.put(entry.getKey().toLowerCase(), entry.getValue().getAsString());
               }
            }
         }
      } catch (Throwable ignored) {}
   }

   private static void saveDiscordLinks() {
      try {
         if (!DISCORD_LINKS_FILE.getParentFile().exists()) {
            DISCORD_LINKS_FILE.getParentFile().mkdirs();
         }
         JsonObject root = new JsonObject();
         for (Map.Entry<String, String> entry : discordLinks.entrySet()) {
            root.addProperty(entry.getKey(), entry.getValue());
         }
         Files.writeString(DISCORD_LINKS_FILE.toPath(), root.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Throwable ignored) {}
   }

   public static Map<String, String> getDiscordLinks() {
      return discordLinks;
   }

   public static void linkDiscordUser(String customName, String mcName) {
      if (customName == null || mcName == null) return;
      discordLinks.put(customName.toLowerCase().trim(), mcName.trim());
      saveDiscordLinks();
   }

   public static String getLinkedIgn(String dcSender) {
      if (dcSender == null) return null;
      String clean = cleanSenderName(dcSender).toLowerCase();
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.ircDiscordUser != null && !s.ircDiscordUser.isEmpty() && clean.equalsIgnoreCase(s.ircDiscordUser.trim())) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.getUser() != null) {
            return mc.getUser().getName();
         }
      }
      return discordLinks.getOrDefault(clean, clean);
   }

   public static net.minecraft.network.chat.MutableComponent buildHoverablePlayerComponent(String username, String colorCode) {
      return buildHoverablePlayerComponent(username, colorCode, null);
   }

   public static String buildPlayerTooltipText(String cleanName, String lookupIgn) {
      String areaStr = "Unknown";
      ensureSelfInOnlinePlayers();
      Minecraft mc = Minecraft.getInstance();
      String selfName = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "";
      if (lookupIgn.equalsIgnoreCase(selfName) || cleanName.equalsIgnoreCase(selfName)) {
         String curLoc = SkyblockUtils.getLocation();
         String curSub = SkyblockUtils.getSubArea();
         if (curLoc != null && !curLoc.equals("None") && !curLoc.equals("Unknown")) {
            if (curSub != null && !curSub.equals("None") && !curSub.equals("Unknown") && !curSub.equalsIgnoreCase(curLoc)) {
               areaStr = curLoc + " - " + curSub;
            } else {
               areaStr = curLoc;
            }
         }
      } else {
         ModUser mu = onlinePlayers.get(lookupIgn);
         if (mu == null) {
            mu = onlinePlayers.get(cleanName);
         }
         if (mu != null && mu.area != null && !mu.area.isEmpty() && !mu.area.equalsIgnoreCase("None")) {
            areaStr = mu.area;
         }
      }

      String formattedArea = "§7Unknown";
      if (!areaStr.equalsIgnoreCase("Unknown")) {
         if (areaStr.contains(" - ")) {
            String[] aParts = areaStr.split(" - ", 2);
            formattedArea = "§a" + aParts[0].trim() + " §7(§b" + aParts[1].trim() + "§7)";
         } else {
            formattedArea = "§a" + areaStr.trim();
         }
      }

      String lower = lookupIgn.toLowerCase();
      String tabLvl = me.bombo.bomboaddons.features.profile.ProfileFetcher.getTabSkyBlockLevel(lookupIgn);
      String sbLvlStr = tabLvl;
      String nwStr = null;

      me.bombo.bomboaddons.features.profile.ProfileFetcher.ProfileData pData = me.bombo.bomboaddons.features.profile.ProfileFetcher.getCachedProfile(lower);
      if (pData != null) {
         if (sbLvlStr == null) {
            if (pData.skyblockLevel > 0) {
               sbLvlStr = "§3[§b" + (int)Math.floor(pData.skyblockLevel) + "§3]";
            }
         }
         if (pData.networth > 0) {
            nwStr = "§6" + formatNetworth(pData.networth);
         }
      } else {
         me.bombo.bomboaddons.features.profile.ProfileFetcher.LightweightStats lw = me.bombo.bomboaddons.features.profile.ProfileFetcher.getLightweightStats(lower);
         if (lw != null) {
            if (sbLvlStr == null && lw.skyblockLevel > 0) {
               sbLvlStr = "§3[§b" + (int)Math.floor(lw.skyblockLevel) + "§3]";
            }
            if (lw.networth > 0) {
               nwStr = "§6" + formatNetworth(lw.networth);
            } else if (lw.networth == -1) {
               nwStr = "§7Hidden / N/A";
            }
         }
         if (lower.contains("bot") || lower.equalsIgnoreCase("discord") || lower.equalsIgnoreCase("server")) {
            sbLvlStr = "§7[§9BOT§7]";
            nwStr = "§7N/A";
         } else if (lw == null || System.currentTimeMillis() - lw.timestamp > 300000L) {
            me.bombo.bomboaddons.features.profile.ProfileFetcher.silentFetchProfile(lower);
         }
      }

      if (sbLvlStr == null) sbLvlStr = "§7Loading...";
      if (nwStr == null) nwStr = "§7Loading...";

      StringBuilder tooltip = new StringBuilder();
      tooltip.append("§6§l=== Player Info ===\n");
      tooltip.append("§7Player: §f").append(lookupIgn).append("\n");
      tooltip.append("§7Area: ").append(formattedArea).append("\n");
      tooltip.append("§7SB Level: ").append(sbLvlStr).append("\n");
      tooltip.append("§7Networth: ").append(nwStr).append("\n");

      String partyStr = null;
      if (lookupIgn.equalsIgnoreCase(selfName) || cleanName.equalsIgnoreCase(selfName)) {
         if (me.bombo.bomboaddons.features.party.PartyManager.isInParty()) {
            partyStr = me.bombo.bomboaddons.features.party.PartyManager.getPartySummary();
         }
      } else {
         partyStr = playerPartyData.get(lookupIgn.toLowerCase());
         if (partyStr == null) partyStr = playerPartyData.get(cleanName.toLowerCase());
      }
      if (partyStr != null && !partyStr.isEmpty()) {
         tooltip.append("§7Party: §d").append(partyStr).append("\n");
      }

      tooltip.append("§8--------------------\n");
      tooltip.append("§eClick to suggest /pv ").append(lookupIgn);
      return tooltip.toString();
   }

   public static net.minecraft.network.chat.MutableComponent buildHoverablePlayerComponent(String username, String colorCode, String linkedIgn) {
      String cleanName = cleanSenderName(username);
      String lookupIgn = (linkedIgn != null && !linkedIgn.isEmpty()) ? cleanSenderName(linkedIgn) : getLinkedIgn(cleanName);
      if (lookupIgn.equalsIgnoreCase(cleanName)) {
         String alt = getLinkedIgn(cleanName);
         if (alt != null && !alt.isEmpty()) lookupIgn = alt;
      }
      String coloredName = ChromaTextHelper.processChroma(colorCode.replace('&', '§') + cleanName);
      net.minecraft.network.chat.MutableComponent comp = ChromaTextHelper.parseFormattedText(coloredName);

      String tooltip = buildPlayerTooltipText(cleanName, lookupIgn);
      net.minecraft.network.chat.HoverEvent hoverEvent = SBECommands.createHoverEvent(tooltip);
      if (hoverEvent == null) {
         hoverEvent = new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(tooltip));
      }
      applyHoverAndClickRecursive(comp, hoverEvent, "/pv " + lookupIgn);
      return comp;
   }

   public static void updateChatMessagesForPlayer(String username, double sbLevel, double networth) {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null || mc.gui == null || mc.gui.hud.getChat() == null) return;
      mc.execute(() -> {
         try {
            net.minecraft.client.gui.components.ChatComponent chat = mc.gui.hud.getChat();
            if (chat instanceof me.bombo.bomboaddons.mixin.ChatComponentAccessor acc) {
               java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage> messages = acc.getAllMessages();
               if (messages == null || messages.isEmpty()) return;

               String cleanTarget = cleanSenderName(username).toLowerCase();
               boolean modified = false;

               for (int i = 0; i < messages.size(); i++) {
                  net.minecraft.client.multiplayer.chat.GuiMessage msg = messages.get(i);
                  if (msg != null && msg.content() != null) {
                     String msgStr = msg.content().getString();
                     if (msgStr.toLowerCase().contains(cleanTarget)) {
                        net.minecraft.network.chat.Component updated = updateHoverEventInComponent(msg.content(), cleanTarget);
                        if (updated != msg.content()) {
                           messages.set(i, new net.minecraft.client.multiplayer.chat.GuiMessage(msg.addedTime(), updated, msg.signature(), msg.source(), msg.tag()));
                           modified = true;
                        }
                     }
                  }
               }

               if (modified) {
                  acc.invokeRefreshTrimmedMessages();
               }
            }
         } catch (Throwable ignored) {}
      });
   }

   private static net.minecraft.network.chat.Component updateHoverEventInComponent(net.minecraft.network.chat.Component comp, String targetName) {
      if (comp == null) return comp;
      net.minecraft.network.chat.MutableComponent result = comp.plainCopy();
      net.minecraft.network.chat.Style style = comp.getStyle();
      if (style.getHoverEvent() != null) {
         String hoverStr = style.getHoverEvent().toString();
         if (hoverStr.toLowerCase().contains(targetName) && hoverStr.contains("Loading...")) {
            String tooltip = buildPlayerTooltipText(targetName, targetName);
            net.minecraft.network.chat.HoverEvent newHover = SBECommands.createHoverEvent(tooltip);
            if (newHover == null) {
               newHover = new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(tooltip));
            }
            style = style.withHoverEvent(newHover);
         }
      }
      result.setStyle(style);
      for (net.minecraft.network.chat.Component sibling : comp.getSiblings()) {
         result.append(updateHoverEventInComponent(sibling, targetName));
      }
      return result;
   }

   public static void applyHoverAndClickRecursive(net.minecraft.network.chat.MutableComponent comp, net.minecraft.network.chat.HoverEvent hover, String suggestCmd) {
      if (comp == null) return;
      comp.setStyle(comp.getStyle().withHoverEvent(hover).withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(suggestCmd)));
      for (net.minecraft.network.chat.Component sibling : comp.getSiblings()) {
         if (sibling instanceof net.minecraft.network.chat.MutableComponent mut) {
            applyHoverAndClickRecursive(mut, hover, suggestCmd);
         }
      }
   }

   public static String formatNetworth(double coins) {
      if (coins >= 1.0E12D) {
         return String.format(java.util.Locale.US, "%.2fT", coins / 1.0E12D);
      } else if (coins >= 1.0E9D) {
         return String.format(java.util.Locale.US, "%.2fB", coins / 1.0E9D);
      } else if (coins >= 1.0E6D) {
         return String.format(java.util.Locale.US, "%.2fM", coins / 1.0E6D);
      } else if (coins >= 1000.0D) {
         return String.format(java.util.Locale.US, "%.1fk", coins / 1000.0D);
      } else {
         return String.format(java.util.Locale.US, "%.0f", coins);
      }
   }

   public static String formatPartySummaryFromSerialized(String pData) {
      if (pData == null || pData.isEmpty() || pData.equalsIgnoreCase("none")) return "None";
      String[] members = pData.split(";");
      if (members.length == 0) return "None";
      String leader = members[0].trim();
      if (members.length == 1) {
         return "Leader: " + leader;
      }
      return "Leader: " + leader + " (" + members.length + " members)";
   }

   public static String buildSelfMetadataPrefix(String area, String subArea) {
      Minecraft mc = Minecraft.getInstance();
      String username = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "";
      String uuid = (mc != null && mc.getUser() != null && mc.getUser().getProfileId() != null) ? mc.getUser().getProfileId().toString() : "";
      BomboConfig.Settings s = BomboConfig.get();
      String customColor = s != null && s.ircNameColor != null && !s.ircNameColor.isEmpty() ? s.ircNameColor : "§b";
      String keyOwner = s != null && s.keyOwnerName != null && !s.keyOwnerName.isEmpty() ? s.keyOwnerName : "";
      String customBridgeName = !keyOwner.isEmpty() ? keyOwner : (s != null && s.ircDiscordUser != null && !s.ircDiscordUser.isEmpty() && !s.ircDiscordUser.equals("fran938") ? s.ircDiscordUser : username);
      String apiKey = me.bombo.bomboaddons.features.auth.BomboApiKeyManager.getApiKey();

      String areaStr = "Unknown";
      if (area != null && !area.equals("None") && !area.equals("Unknown")) {
         if (subArea != null && !subArea.equals("None") && !subArea.equals("Unknown") && !subArea.equalsIgnoreCase(area)) {
            areaStr = area + " - " + subArea;
         } else {
            areaStr = area;
         }
      }

      int selfLvl = 0;
      long selfNw = 0;
      try {
         var pData = me.bombo.bomboaddons.features.profile.ProfileFetcher.getCachedProfile(username.toLowerCase());
         if (pData != null) {
            selfLvl = (int) pData.skyblockLevel;
            selfNw = (long) pData.networth;
         }
      } catch (Throwable ignored) {}

      String partySerialized = me.bombo.bomboaddons.features.party.PartyManager.getPartySerialized();

      StringBuilder sb = new StringBuilder("[CLR:").append(customColor);
      if (!apiKey.isEmpty()) sb.append("|KEY:").append(apiKey);
      if (!uuid.isEmpty()) sb.append("|UUID:").append(uuid);
      if (!username.isEmpty()) sb.append("|IGN:").append(username);
      if (!customBridgeName.isEmpty()) sb.append("|NAME:").append(customBridgeName);
      sb.append("|LOC:").append(areaStr);
      if (selfLvl > 0) sb.append("|SBLVL:").append(selfLvl);
      if (selfNw > 0) sb.append("|NW:").append(selfNw);
      if (!partySerialized.isEmpty()) sb.append("|PARTY:").append(partySerialized);
      sb.append("]");
      return sb.toString();
   }

   public static void postOnlineStatus(String area, String subArea) {
      final String safeArea = (area == null || area.isEmpty()) ? "None" : area;
      final String safeSubArea = (subArea == null) ? "" : subArea;
      java.util.concurrent.CompletableFuture.runAsync(() -> {
         try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            String username = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "Player";
            String uuid = (mc != null && mc.getUser() != null && mc.getUser().getProfileId() != null) ? mc.getUser().getProfileId().toString() : "";
            me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
            String keyOwner = s != null && s.keyOwnerName != null && !s.keyOwnerName.isEmpty() ? s.keyOwnerName : "";
            String customBridgeName = !keyOwner.isEmpty() ? keyOwner : (s != null && s.ircDiscordUser != null && !s.ircDiscordUser.isEmpty() && !s.ircDiscordUser.equals("fran938") ? s.ircDiscordUser : username);

            int selfLvl = 0;
            long selfNw = 0;
            try {
               var pData = me.bombo.bomboaddons.features.profile.ProfileFetcher.getCachedProfile(username.toLowerCase());
               if (pData != null) {
                  selfLvl = (int) pData.skyblockLevel;
                  selfNw = (long) pData.networth;
               }
            } catch (Throwable ignored) {}

            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("ign", username);
            obj.addProperty("name", customBridgeName);
            if (!uuid.isEmpty()) obj.addProperty("uuid", uuid);
            obj.addProperty("area", safeArea);
            obj.addProperty("subarea", safeSubArea);
            obj.addProperty("sbLevel", selfLvl);
            obj.addProperty("networth", selfNw);
            obj.addProperty("version", BomboaddonsClient.MOD_VERSION);
            obj.addProperty("mod_version", BomboaddonsClient.MOD_VERSION);
            obj.addProperty("modVersion", BomboaddonsClient.MOD_VERSION);

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                  .uri(java.net.URI.create("https://bombo.dpdns.org/api/v1/bridge/online"))
                  .header("Content-Type", "application/json")
                  .timeout(java.time.Duration.ofSeconds(4))
                  .POST(java.net.http.HttpRequest.BodyPublishers.ofString(obj.toString()))
                  .build();
            createInsecureHttpClient().sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding());
         } catch (Throwable ignored) {}
      });
   }

   public static void broadcastArea(String area, String subArea) {
      final String safeArea = (area == null || area.isEmpty()) ? "None" : area;
      final String safeSubArea = subArea;
      postOnlineStatus(safeArea, safeSubArea);
      String displayLoc = safeArea;
      if (safeSubArea != null && !safeSubArea.isEmpty() && !safeSubArea.equalsIgnoreCase("None") && !safeSubArea.equalsIgnoreCase("Unknown") && !safeSubArea.equalsIgnoreCase(safeArea)) {
         displayLoc = safeArea + " - " + safeSubArea;
      }
      final String finalArea = displayLoc;
      ensureSelfInOnlinePlayers();
      if (running && BomboConfig.get().ircChatEnabled) {
         (new Thread(() -> {
            try {
               String metaPrefix = buildSelfMetadataPrefix(safeArea, safeSubArea);
               sendRaw("NOTICE #bomboaddons_chat :" + metaPrefix + "\u0002[AREA]\u0002" + finalArea + "\u0002" + BomboaddonsClient.MOD_VERSION);
               sendRaw("PRIVMSG #bomboaddons_chat :[AREA]\u0002" + finalArea + "\u0002" + BomboaddonsClient.MOD_VERSION);
            } catch (Throwable ignored) {}
         })).start();
      }
   }

   private static void onConnected() {
      try {
         String loc = SkyblockUtils.getLocation();
         String sub = SkyblockUtils.getSubArea();
         broadcastArea(loc, sub);
      } catch (Throwable ignored) {}
   }

   public static void broadcastArea(String area) {
      broadcastArea(area, null);
   }

   public static void broadcastEgg(String location, String eggType, net.minecraft.core.BlockPos pos) {
      if (location == null || eggType == null || pos == null) return;
      if (running) {
         (new Thread(() -> {
            try {
               sendRaw("NOTICE #bomboaddons_chat :[EGG]\u0002" + location + "\u0002" + eggType + "\u0002" + pos.getX() + "\u0002" + pos.getY() + "\u0002" + pos.getZ());
            } catch (Throwable ignored) {}
         })).start();
      }
   }

   public static void sendMessage(String msg) {
      if (msg != null && !msg.trim().isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc != null && mc.player != null) {
            try {
               String emojiProcessed = me.bombo.bomboaddons.util.EmojiShortcodeHelper.replaceEmojis(msg);
               String finalMsg = SkyblockUtils.replaceCoordPlaceholders(emojiProcessed);
               String username = mc.getUser().getName();

               String curLoc = SkyblockUtils.getLocation();
               String curSub = SkyblockUtils.getSubArea();
               postOnlineStatus(curLoc, curSub);

               String prefix = buildSelfMetadataPrefix(curLoc, curSub);
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
