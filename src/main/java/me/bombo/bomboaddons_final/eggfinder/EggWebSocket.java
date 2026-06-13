package me.bombo.bomboaddons_final.eggfinder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.Bomboaddons;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class EggWebSocket {
    private static final Logger LOGGER = LoggerFactory.getLogger("bomboaddons-eggws");
    private static final String WS_URL = "wss://ws.hysky.de";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_2)
            .build();
    private static final Gson GSON = new Gson();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "EggWS-Scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile WebSocket webSocket = null;
    private static volatile boolean connecting = false;
    private static volatile String activeSubscription = null;

    public static boolean isConnected() {
        return webSocket != null && !webSocket.isInputClosed() && !webSocket.isOutputClosed();
    }

    public static boolean isConnecting() {
        return connecting;
    }

    public static String getActiveSubscription() {
        return activeSubscription;
    }

    public static synchronized void forceReconnect() {
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Manual Reconnect").join();
            } catch (Exception ignored) {}
            webSocket = null;
        }
        connect();
    }

    public static synchronized void updateSubscription(String newLocation) {
        if (!BomboConfig.get().eggFinder) {
            disconnect();
            return;
        }

        if (newLocation == null) {
            if (activeSubscription != null) {
                sendUnsubscribe(activeSubscription);
                activeSubscription = null;
            }
            return;
        }

        if (activeSubscription != null && !activeSubscription.equals(newLocation)) {
            sendUnsubscribe(activeSubscription);
            activeSubscription = null;
        }

        activeSubscription = newLocation;
        if (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed()) {
            connect();
        } else {
            sendSubscribe(newLocation);
        }
    }

    public static synchronized void onTokenRefreshed() {
        if (BomboConfig.get().eggFinder && activeSubscription != null) {
            if (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed()) {
                connect();
            }
        }
    }

    private static synchronized void connect() {
        if (connecting) return;

        String token = EggAuth.getToken();
        if (token == null) {
            LOGGER.info("API token is not yet ready. Postponing connection.");
            return;
        }

        connecting = true;
        LOGGER.info("Connecting to Skyblocker WebSocket...");

        HTTP_CLIENT.newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "Skyblocker/1.7.1 (" + net.minecraft.SharedConstants.getCurrentVersion().name() + ")")
                .buildAsync(URI.create(WS_URL), new SocketListener())
                .thenAccept(ws -> {
                    synchronized (EggWebSocket.class) {
                        webSocket = ws;
                        connecting = false;
                        LOGGER.info("Successfully connected to Skyblocker WebSocket.");
                        if (activeSubscription != null) {
                            sendSubscribe(activeSubscription);
                        }
                    }
                })
                .exceptionally(t -> {
                    synchronized (EggWebSocket.class) {
                        connecting = false;
                        Throwable cause = t.getCause() != null ? t.getCause() : t;
                        if (cause instanceof java.net.http.WebSocketHandshakeException) {
                            java.net.http.WebSocketHandshakeException handshakeEx = (java.net.http.WebSocketHandshakeException) cause;
                            java.net.http.HttpResponse<?> resp = handshakeEx.getResponse();
                            LOGGER.error("Failed to connect to WebSocket: Handshake Exception. Status: " 
                                    + resp.statusCode() + ", Headers: " + resp.headers().map());
                        } else {
                            LOGGER.error("Failed to connect to WebSocket: " + cause.getMessage(), cause);
                        }
                        // Retry after 10 seconds
                        SCHEDULER.schedule(EggWebSocket::connect, 10, TimeUnit.SECONDS);
                    }
                    return null;
                });
    }

    public static synchronized void disconnect() {
        if (webSocket != null) {
            try {
                if (activeSubscription != null) {
                    sendUnsubscribe(activeSubscription);
                }
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Disconnecting").join();
            } catch (Exception ignored) {}
            webSocket = null;
        }
        activeSubscription = null;
    }

    public static synchronized void sendPublish(String location, String eggType, BlockPos pos) {
        if (webSocket == null || webSocket.isOutputClosed()) return;

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

    private static void sendSubscribe(String location) {
        if (webSocket == null || webSocket.isOutputClosed()) return;
        LOGGER.info("Subscribing to EGG_WAYPOINTS for location: " + location);

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "subscribe");
        payload.addProperty("service", "EGG_WAYPOINTS");
        payload.addProperty("serverId", location);

        sendText(GSON.toJson(payload));
    }

    private static void sendUnsubscribe(String location) {
        if (webSocket == null || webSocket.isOutputClosed()) return;
        LOGGER.info("Unsubscribing from EGG_WAYPOINTS for location: " + location);

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "unsubscribe");
        payload.addProperty("service", "EGG_WAYPOINTS");
        payload.addProperty("serverId", location);

        sendText(GSON.toJson(payload));
    }

    private static synchronized void sendText(String text) {
        if (webSocket != null && !webSocket.isOutputClosed()) {
            webSocket.sendText(text, true).exceptionally(t -> {
                LOGGER.error("Failed to send WebSocket message: " + t.getMessage(), t);
                return null;
            });
        }
    }

    private static class SocketListener implements WebSocket.Listener {
        private final List<CharSequence> parts = new ArrayList<>();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            parts.add(data);
            ws.request(1);

            if (last) {
                String completeMsg = String.join("", parts);
                parts.clear();
                handleMessage(completeMsg);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            LOGGER.info("WebSocket connection closed. Status Code: " + statusCode + ", Reason: " + reason);
            synchronized (EggWebSocket.class) {
                if (webSocket == ws) {
                    webSocket = null;
                }
            }
            // Auto-reconnect if we still need the subscription
            SCHEDULER.schedule(() -> {
                synchronized (EggWebSocket.class) {
                    if (BomboConfig.get().eggFinder && activeSubscription != null) {
                        connect();
                    }
                }
            }, 10, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            LOGGER.error("WebSocket error: " + error.getMessage(), error);
            synchronized (EggWebSocket.class) {
                if (webSocket == ws) {
                    webSocket = null;
                }
            }
        }

        private void handleMessage(String rawMessage) {
            try {
                JsonObject json = GSON.fromJson(rawMessage, JsonObject.class);
                if (!json.has("type") || !json.has("service")) return;

                String type = json.get("type").getAsString();
                String service = json.get("service").getAsString();

                if (!"EGG_WAYPOINTS".equals(service)) return;

                JsonElement msgElem = json.get("message");
                if (msgElem == null || msgElem.isJsonNull()) return;

                if ("response".equals(type)) {
                    JsonObject msgObj = msgElem.getAsJsonObject();
                    parseAndProcessWaypoint(msgObj);
                } else if ("initialMessage".equals(type)) {
                    JsonArray msgArray = msgElem.getAsJsonArray();
                    long now = System.currentTimeMillis();
                    for (JsonElement item : msgArray) {
                        JsonObject msgObj = item.getAsJsonObject();
                        if (msgObj.has("expirationEpoch")) {
                            long exp = msgObj.get("expirationEpoch").getAsLong();
                            if (exp <= now) continue; // Skip expired
                        }
                        parseAndProcessWaypoint(msgObj);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error handling incoming WebSocket message: " + e.getMessage(), e);
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

                // Pass to EggFinder
                Minecraft.getInstance().execute(() -> {
                    EggFinder.onWebsocketMessage(eggTypeStr, pos);
                });
            } catch (Exception e) {
                LOGGER.error("Error parsing waypoint object: " + e.getMessage(), e);
            }
        }
    }
}
