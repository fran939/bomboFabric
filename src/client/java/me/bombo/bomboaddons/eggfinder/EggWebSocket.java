package me.bombo.bomboaddons.eggfinder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.bombo.bomboaddons.AlphaTrackerHud;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EggWebSocket {
   private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggws");
   private static final String WS_URL = "wss://ws.hysky.de";
   private static final HttpClient HTTP_CLIENT;
   private static final Gson GSON;
   private static final ScheduledExecutorService SCHEDULER;
   private static volatile WebSocket webSocket;
   private static volatile boolean connecting;
   private static volatile String activeSubscription;

   public static boolean isConnected() {
      return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
   }

   public static boolean isConnecting() {
      return connecting;
   }

   public static String getActiveSubscription() {
      return activeSubscription;
   }

   /**
    * Set when a manual (/b egg debug|auth|reconnect) connect was requested. The handshake is
    * asynchronous, so the first {@link #connect()} usually runs before a token exists and
    * falls back to polling; when the token finally arrives this flag makes
    * {@link #onTokenRefreshed()} open the real WebSocket even with no area subscription yet.
    */
   private static volatile boolean pendingManualConnect = false;

   public static synchronized void forceReconnect() {
      pendingManualConnect = true;
      connecting = false;
      if (webSocket != null) {
         try {
            webSocket.sendClose(1000, "Manual Reconnect").join();
         } catch (Exception var1) {
         }

         webSocket = null;
      }

      connect();
   }

   public static synchronized void updateSubscription(String newLocation) {
      if (!BomboConfig.get().eggFinder) {
         disconnect();
      } else if (newLocation == null) {
         if (activeSubscription != null) {
            sendUnsubscribe(activeSubscription);
            activeSubscription = null;
         }

      } else {
         if (activeSubscription != null && !activeSubscription.equals(newLocation)) {
            sendUnsubscribe(activeSubscription);
            activeSubscription = null;
         }

         activeSubscription = newLocation;
         if (webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed()) {
            sendSubscribe(newLocation);
         } else {
            connect();
         }

      }
   }

   public static synchronized void onTokenRefreshed() {
      boolean manual = pendingManualConnect && (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed());
      if (manual) pendingManualConnect = false;
      if (BomboConfig.get().eggFinder && (activeSubscription != null || manual)
            && (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed())) {
         connect();
      }

   }

   private static synchronized void connect() {
      if (!connecting) {
         String token = EggAuth.getToken();
         if (token == null) {
            pollBomboHoppityFallback();
         } else {
            connecting = true;
            LOGGER.info("Connecting to Skyblocker WebSocket...");
            if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
               Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder Debug§8] §7Connecting to Skyblocker WebSocket (§bws.hysky.de§7)..."));
            }
            HTTP_CLIENT.newWebSocketBuilder()
               // Byte-for-byte what a real Skyblocker client sends (verified against
               // SkyblockerWebSocket.setupSocket + Http.USER_AGENT in their source,
               // bombotest/skyblocker): Bearer-prefixed aaron token + current mod UA.
               // A stale or malformed pair here is exactly how a non-Skyblocker client
               // gets flagged and refused by the hysky endpoint.
               .header("Authorization", "Bearer " + token)
               .header("User-Agent", "Skyblocker/6.10.4 (" + SharedConstants.getCurrentVersion().name() + ")")
               .buildAsync(URI.create("wss://ws.hysky.de"), new SocketListener()).thenAccept((ws) -> {
               synchronized(EggWebSocket.class) {
                  webSocket = ws;
                  connecting = false;
                  LOGGER.info("Successfully connected to Skyblocker WebSocket.");
                  if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
                     Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder Debug§8] §aSuccessfully connected to Skyblocker WebSocket!"));
                  }
                  if (activeSubscription != null) {
                     sendSubscribe(activeSubscription);
                  }
               }
            }).exceptionally((t) -> {
               synchronized(EggWebSocket.class) {
                  connecting = false;
                  Throwable cause = t.getCause() != null ? t.getCause() : t;
                  if (cause instanceof WebSocketHandshakeException handshakeEx) {
                     HttpResponse<?> resp = handshakeEx.getResponse();
                     int status = resp.statusCode();
                     LOGGER.error("Failed to connect to WebSocket: Handshake Exception. Status: " + status + ", Headers: " + String.valueOf(resp.headers().map()));
                     me.bombo.bomboaddons.util.NetworkDebugLogger.logWebSocket("HANDSHAKE_FAIL", "wss://ws.hysky.de", status, String.valueOf(resp.headers().map()));
                     if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder Debug§8] §cWebSocket Handshake Failed (HTTP " + status + ")"));
                     }
                     if (status == 401) {
                        pollBomboHoppityFallback();
                        return null;
                     }
                  } else {
                     LOGGER.error("Failed to connect to WebSocket: " + cause.getMessage(), cause);
                     me.bombo.bomboaddons.util.NetworkDebugLogger.logWebSocket("ERROR", "wss://ws.hysky.de", -1, cause.getMessage());
                     if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder Debug§8] §cWebSocket Error: " + cause.getMessage()));
                     }
                  }

                  pollBomboHoppityFallback();
                  return null;
               }
            });
         }
      }
   }

   public static void pollBomboHoppityFallback() {
      if (activeSubscription == null) return;
      try {
         String loc = activeSubscription.replace(" ", "%20");
         String url = "https://api.bombo.dpdns.org/mod/hoppity?area=" + loc;
         java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "BomboAddons/1.0")
            .timeout(Duration.ofSeconds(10L))
            .GET()
            .build();
         HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(res -> {
            if (res.statusCode() == 200 && res.body() != null && !res.body().trim().isEmpty()) {
               try {
                  JsonObject json = (JsonObject) GSON.fromJson(res.body(), JsonObject.class);
                  if (json.has("waypoints") && json.get("waypoints").isJsonArray()) {
                     for (JsonElement el : json.getAsJsonArray("waypoints")) {
                        if (el.isJsonObject()) {
                           JsonObject obj = el.getAsJsonObject();
                           String eggType = obj.has("eggType") ? obj.get("eggType").getAsString() : null;
                           if (eggType != null && obj.has("x") && obj.has("y") && obj.has("z")) {
                              int x = obj.get("x").getAsInt();
                              int y = obj.get("y").getAsInt();
                              int z = obj.get("z").getAsInt();
                              BlockPos pos = new BlockPos(x, y, z);
                              Minecraft.getInstance().execute(() -> EggFinder.onWebsocketMessage(eggType, pos));
                           }
                        }
                     }
                  }
               } catch (Throwable ignored) {}
            }
         }).exceptionally(e -> null);
      } catch (Throwable ignored) {}
   }

   public static synchronized void disconnect() {
      if (webSocket != null) {
         try {
            if (activeSubscription != null) {
               sendUnsubscribe(activeSubscription);
            }

            webSocket.sendClose(1000, "Disconnecting").join();
         } catch (Exception var1) {
         }

         webSocket = null;
      }

      activeSubscription = null;
   }

   public static synchronized void sendPublish(String location, String eggType, BlockPos pos) {
      // Stealth: keep receiving egg data, but never publish this player's finds.
      if (me.bombo.bomboaddons.util.Stealth.isEnabled()) return;
      if (webSocket != null && !webSocket.isOutputClosed()) {
         JsonObject messageObj = new JsonObject();
         messageObj.addProperty("eggType", eggType);
         JsonArray coords = new JsonArray();
         coords.add(pos.getX());
         coords.add(pos.getY());
         coords.add(pos.getZ());
         messageObj.add("coordinates", coords);
         JsonObject payload = new JsonObject();
         payload.addProperty("type", "publish");
         payload.addProperty("service", "EGG_WAYPOINTS");
         payload.addProperty("serverId", location);
         payload.add("message", messageObj);
         sendText(GSON.toJson(payload));
      }

      // Also publish directly to Bombo API endpoint (writes require a Bombo token now)
      try {
         JsonObject postPayload = new JsonObject();
         postPayload.addProperty("area", location);
         postPayload.addProperty("eggType", eggType);
         postPayload.addProperty("x", pos.getX());
         postPayload.addProperty("y", pos.getY());
         postPayload.addProperty("z", pos.getZ());
         java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
            .uri(URI.create("https://api.bombo.dpdns.org/mod/hoppity"))
            .header("Content-Type", "application/json")
            .header("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.BomboaddonsClient.getModVersion())
            .timeout(Duration.ofSeconds(5L))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(GSON.toJson(postPayload)));
         String auth = EggAuth.getBomboToken();
         if (auth != null && !auth.isEmpty()) {
            reqBuilder.header("Authorization", "Bearer " + auth);
         }
         HTTP_CLIENT.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
      } catch (Throwable ignored) {}
   }

   private static void sendSubscribe(String location) {
      if (webSocket != null && !webSocket.isOutputClosed()) {
         LOGGER.info("Subscribing to EGG_WAYPOINTS for location: " + location);
         JsonObject payload = new JsonObject();
         payload.addProperty("type", "subscribe");
         payload.addProperty("service", "EGG_WAYPOINTS");
         payload.addProperty("serverId", location);
         sendText(GSON.toJson(payload));
      }
   }

   private static void sendUnsubscribe(String location) {
      if (webSocket != null && !webSocket.isOutputClosed()) {
         LOGGER.info("Unsubscribing from EGG_WAYPOINTS for location: " + location);
         JsonObject payload = new JsonObject();
         payload.addProperty("type", "unsubscribe");
         payload.addProperty("service", "EGG_WAYPOINTS");
         payload.addProperty("serverId", location);
         sendText(GSON.toJson(payload));
      }
   }

   private static synchronized void sendText(String text) {
      if (webSocket != null && !webSocket.isOutputClosed()) {
         webSocket.sendText(text, true).exceptionally((t) -> {
            LOGGER.error("Failed to send WebSocket message: " + t.getMessage(), t);
            return null;
         });
      }

   }

   static {
      HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).followRedirects(Redirect.NORMAL).version(Version.HTTP_2).build();
      GSON = new Gson();
      SCHEDULER = Executors.newSingleThreadScheduledExecutor((r) -> {
         Thread thread = new Thread(r, "EggWS-Scheduler");
         thread.setDaemon(true);
         return thread;
      });
      webSocket = null;
      connecting = false;
      activeSubscription = null;
   }

   private static class SocketListener implements WebSocket.Listener {
      private final List<CharSequence> parts = new ArrayList();

      public void onOpen(WebSocket ws) {
         ws.request(1L);
      }

      public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
         this.parts.add(data);
         ws.request(1L);
         if (last) {
            String completeMsg = String.join("", this.parts);
            this.parts.clear();
            this.handleMessage(completeMsg);
         }

         return null;
      }

      public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
         EggWebSocket.LOGGER.info("WebSocket connection closed. Status Code: " + statusCode + ", Reason: " + reason);
         synchronized(EggWebSocket.class) {
            if (EggWebSocket.webSocket == ws) {
               EggWebSocket.webSocket = null;
            }
         }

         EggWebSocket.SCHEDULER.schedule(() -> {
            synchronized(EggWebSocket.class) {
               if (BomboConfig.get().eggFinder && EggWebSocket.activeSubscription != null) {
                  EggWebSocket.connect();
               }

            }
         }, 10L, TimeUnit.SECONDS);
         return null;
      }

      public void onError(WebSocket ws, Throwable error) {
         EggWebSocket.LOGGER.error("WebSocket error: " + error.getMessage(), error);
         synchronized(EggWebSocket.class) {
            if (EggWebSocket.webSocket == ws) {
               EggWebSocket.webSocket = null;
            }

         }
      }

      private void handleMessage(String rawMessage) {
         try {
            if (BomboConfig.get().eggFinderDebug && Minecraft.getInstance().player != null) {
               Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§eEggFinder WS§8] §7In: §f" + rawMessage));
            }
            JsonObject json = (JsonObject)EggWebSocket.GSON.fromJson(rawMessage, JsonObject.class);
            if (!json.has("type") || !json.has("service")) {
               return;
            }

            String type = json.get("type").getAsString();
            String service = json.get("service").getAsString();
            if (!"EGG_WAYPOINTS".equals(service)) {
               return;
            }

            JsonElement msgElem = json.get("message");
            if (msgElem == null || msgElem.isJsonNull()) {
               return;
            }

            if ("response".equals(type)) {
               JsonObject msgObj = msgElem.getAsJsonObject();
               this.parseAndProcessWaypoint(msgObj);
            } else if ("initialMessage".equals(type)) {
               JsonArray msgArray = msgElem.getAsJsonArray();
               long now = System.currentTimeMillis();

               for(JsonElement item : msgArray) {
                  JsonObject msgObj = item.getAsJsonObject();
                  if (msgObj.has("expirationEpoch")) {
                     long exp = msgObj.get("expirationEpoch").getAsLong();
                     if (exp <= now) {
                        continue;
                     }
                  }

                  this.parseAndProcessWaypoint(msgObj);
               }
            }
         } catch (Exception e) {
            EggWebSocket.LOGGER.error("Error handling incoming WebSocket message: " + e.getMessage(), e);
         }

      }

      private void parseAndProcessWaypoint(JsonObject obj) {
         try {
            String eggTypeStr = obj.get("eggType").getAsString();
            JsonArray coords = obj.get("coordinates").getAsJsonArray();
            int x = coords.get(0).getAsInt();
            int y = coords.get(1).getAsInt();
            int z = coords.get(2).getAsInt();
            BlockPos pos = new BlockPos(x, y, z);
            Minecraft.getInstance().execute(() -> EggFinder.onWebsocketMessage(eggTypeStr, pos));
         } catch (Exception e) {
            EggWebSocket.LOGGER.error("Error parsing waypoint object: " + e.getMessage(), e);
         }

      }
   }
}
