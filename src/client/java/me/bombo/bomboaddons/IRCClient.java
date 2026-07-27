package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class IRCClient {
    private static WebSocket webSocket;
    private static Socket tcpSocket;
    private static PrintWriter tcpWriter;
    private static Thread clientThread;
    private static boolean running = false;
    private static String currentNick = "";
    public static String lastError = "Not started yet";

    private static final java.util.Map<String, String> onlinePlayers = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.Map<String, String> getOnlinePlayers() {
        return onlinePlayers;
    }

    public static class ModUser {
        public final String username;
        public final String version;
        
        public ModUser(String username, String version) {
            this.username = username;
            this.version = version;
        }
    }

    public static ModUser parseNick(String nick) {
        if (nick != null && nick.startsWith("b_")) {
            String stripped = nick.substring(2);
            int vIdx = stripped.indexOf("_v");
            if (vIdx != -1) {
                String user = stripped.substring(0, vIdx);
                String verAndSuffix = stripped.substring(vIdx + 2);
                String[] verParts = verAndSuffix.split("_");
                StringBuilder verBuilder = new StringBuilder();
                for (int i = 0; i < Math.min(verParts.length, 3); i++) {
                    if (i > 0) verBuilder.append(".");
                    verBuilder.append(verParts[i]);
                }
                return new ModUser(user, verBuilder.toString());
            } else {
                return new ModUser(stripped, "Unknown (< 26.2.2)");
            }
        }
        return new ModUser(nick != null ? nick : "Player", "Unknown");
    }

    public static boolean isConnected() {
        return running && ((webSocket != null) || (tcpSocket != null && !tcpSocket.isClosed() && tcpSocket.isConnected()));
    }

    public static void start() {
        if (running) return;
        running = true;
        clientThread = new Thread(IRCClient::runLoop, "IRC-Client-Thread");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    public static void onEnabledToggled() {
        if (!BomboConfig.get().ircChatEnabled) {
            closeQuietly();
        }
    }

    private static void closeQuietly() {
        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Bye"); } catch (Throwable ignored) {}
            webSocket = null;
        }
        if (tcpSocket != null) {
            try { tcpSocket.close(); } catch (Throwable ignored) {}
            tcpSocket = null;
            tcpWriter = null;
        }
    }

    private static HttpClient createInsecureHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();
        } catch (Throwable t) {
            return HttpClient.newBuilder().build();
        }
    }

    private static void runLoop() {
        Random random = new Random();
        HttpClient client = createInsecureHttpClient();

        while (running) {
            if (!BomboConfig.get().ircChatEnabled) {
                closeQuietly();
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                continue;
            }

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

                currentNick = cleanUsername;

                // Attempt 1: Raw TCP Socket to chat.bombo.dpdns.org:6667
                try {
                    System.out.println("[BomboAddons-IRC] Trying TCP socket to chat.bombo.dpdns.org:6667...");
                    Socket sock = new Socket();
                    sock.connect(new java.net.InetSocketAddress("chat.bombo.dpdns.org", 6667), 4000);
                    tcpSocket = sock;
                    tcpWriter = new PrintWriter(sock.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));

                    sendRaw("NICK " + currentNick);
                    sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                    lastError = "None (Connected via TCP)";
                    System.out.println("[BomboAddons-IRC] Connected via TCP!");

                    String line;
                    while (running && BomboConfig.get().ircChatEnabled && tcpSocket != null && !tcpSocket.isClosed()) {
                        line = reader.readLine();
                        if (line == null) break;
                        handleLine(line);
                    }
                    closeQuietly();
                    continue;
                } catch (Throwable t1) {
                    lastError = "TCP 6667 failed: " + t1.getMessage();
                    System.err.println("[BomboAddons-IRC] TCP 6667 failed: " + t1.getMessage());
                }

                // Attempt 2: WebSocket wss://bombo.dpdns.org/bombochat
                try {
                    System.out.println("[BomboAddons-IRC] Trying WSS wss://bombo.dpdns.org/bombochat...");
                    CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                            .buildAsync(URI.create("wss://bombo.dpdns.org/bombochat"), new WebSocketListener());
                    webSocket = wsFuture.get(5, TimeUnit.SECONDS);
                    sendRaw("NICK " + currentNick);
                    lastError = "None (Connected via WSS)";
                    System.out.println("[BomboAddons-IRC] Connected via WSS!");

                    while (running && BomboConfig.get().ircChatEnabled && webSocket != null) {
                        Thread.sleep(2000);
                    }
                    closeQuietly();
                    continue;
                } catch (Throwable t2) {
                    lastError = "WSS failed: " + t2.getMessage();
                    System.err.println("[BomboAddons-IRC] WSS failed: " + t2.getMessage());
                }

                // Attempt 4: Direct IP TCP 51.170.56.117:6667
                try {
                    System.out.println("[BomboAddons-IRC] Trying direct IP TCP 51.170.56.117:6667...");
                    Socket sock = new Socket();
                    sock.connect(new java.net.InetSocketAddress("51.170.56.117", 6667), 4000);
                    tcpSocket = sock;
                    tcpWriter = new PrintWriter(sock.getOutputStream(), true);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream(), "UTF-8"));

                    sendRaw("NICK " + currentNick);
                    sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                    lastError = "None (Connected via Direct IP TCP)";
                    System.out.println("[BomboAddons-IRC] Connected via Direct IP TCP!");

                    String line;
                    while (running && BomboConfig.get().ircChatEnabled && tcpSocket != null && !tcpSocket.isClosed()) {
                        line = reader.readLine();
                        if (line == null) break;
                        handleLine(line);
                    }
                    closeQuietly();
                    continue;
                } catch (Throwable t4) {
                    lastError = "Direct IP 6667 failed: " + t4.getMessage();
                    System.err.println("[BomboAddons-IRC] Direct IP 6667 failed: " + t4.getMessage());
                }

                // Attempt 3: WebSocket ws://chat.bombo.dpdns.org:6668
                try {
                    System.out.println("[BomboAddons-IRC] Trying WS ws://chat.bombo.dpdns.org:6668...");
                    CompletableFuture<WebSocket> wsFuture = client.newWebSocketBuilder()
                            .buildAsync(URI.create("ws://chat.bombo.dpdns.org:6668"), new WebSocketListener());
                    webSocket = wsFuture.get(5, TimeUnit.SECONDS);
                    sendRaw("NICK " + currentNick);
                    lastError = "None (Connected via WS 6668)";
                    System.out.println("[BomboAddons-IRC] Connected via WS 6668!");

                    while (running && BomboConfig.get().ircChatEnabled && webSocket != null) {
                        Thread.sleep(2000);
                    }
                    closeQuietly();
                } catch (Throwable t3) {
                    lastError = "WS 6668 failed: " + t3.getMessage();
                    System.err.println("[BomboAddons-IRC] WS 6668 failed: " + t3.getMessage());
                }

            } catch (Throwable t) {
                closeQuietly();
            }

            try { Thread.sleep(3000); } catch (InterruptedException ie) { break; }
        }
    }

    private static void sendRaw(String msg) {
        if (webSocket != null) {
            try { webSocket.sendText(msg, true); } catch (Throwable ignored) {}
        }
        if (tcpWriter != null) {
            try { tcpWriter.println(msg); } catch (Throwable ignored) {}
        }
    }

    private static class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String fullMessage = textBuffer.toString();
                textBuffer.setLength(0);
                for (String line : fullMessage.split("\r?\n")) {
                    if (!line.trim().isEmpty()) {
                        handleLine(line.trim());
                    }
                }
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            webSocket = null;
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            webSocket = null;
        }
    }

    private static void handleLine(String line) {
        try {
            if (line.startsWith("PING ")) {
                sendRaw("PONG " + line.substring(5));
                return;
            }

            if (line.contains(" PRIVMSG ")) {
                int privmsgIdx = line.indexOf(" PRIVMSG ");
                int colonIdx = line.indexOf(" :", privmsgIdx);
                if (privmsgIdx != -1 && colonIdx != -1) {
                    String senderPart = line.substring(1, privmsgIdx);
                    String senderNick = senderPart.split("!")[0];

                    String payload = line.substring(colonIdx + 2);
                    String[] msgParts = payload.split("\u0002", 3);
                    String formattedMessage;

                    if (payload.contains("[DC]") || senderNick.equalsIgnoreCase("Discord")) {
                        String cleanPayload = payload.replace("&", "§");
                        if (cleanPayload.startsWith("§9[DC]\u0002")) {
                            String[] parts = cleanPayload.split("\u0002", 3);
                            if (parts.length == 3) {
                                formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] " + parts[1] + "§f: §r" + parts[2];
                            } else {
                                formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] §f" + cleanPayload;
                            }
                        } else {
                            String body = cleanPayload;
                            if (body.startsWith("§9[DC] ")) body = body.substring(7);
                            else if (body.startsWith("[DC] ")) body = body.substring(5);

                            int cIdx = body.indexOf(": ");
                            if (cIdx != -1) {
                                String dcUser = body.substring(0, cIdx).trim();
                                String dcMsg = body.substring(cIdx + 2);
                                formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] " + dcUser + "§f: §r" + dcMsg;
                            } else {
                                formattedMessage = "§r§8[§r§3Bombo§r§8] §9[DC] §f" + body;
                            }
                        }
                    } else if (msgParts.length == 3) {
                        String rankPrefix = msgParts[0];
                        String realUsername = msgParts[1];
                        String actualMsg = msgParts[2].replace('&', '§');
                        formattedMessage = "§r§8[§r§3Bombo§r§8] §r" + rankPrefix + realUsername + "§f: §r" + actualMsg;
                    } else {
                        String rankPrefix = RankCache.getRank(senderNick);
                        if (!rankPrefix.isEmpty() && !rankPrefix.endsWith(" ")) rankPrefix += " ";
                        String cleanPayload = payload.replace('&', '§');
                        formattedMessage = "§r§8[§r§3Bombo§r§8] §r" + rankPrefix + senderNick + "§f: §r" + cleanPayload;
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
        } catch (Throwable ignored) {}
    }

    public static void sendMessage(String msg) {
        if (msg == null || msg.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        new Thread(() -> {
            try {
                String username = mc.getUser().getName();
                String prefix = RankCache.getRank(username);
                String coloredMsg = msg.replace('&', '§');
                String payload = prefix + "\u0002" + username + "\u0002" + coloredMsg;
                sendRaw("PRIVMSG #bomboaddons_chat :" + payload);
            } catch (Throwable ignored) {}
        }).start();
    }
}
