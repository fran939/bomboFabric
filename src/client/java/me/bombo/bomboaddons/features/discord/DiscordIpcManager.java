package me.bombo.bomboaddons.features.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects directly to the local Discord Desktop app via Windows Named Pipes (\\.\pipe\discord-ipc-0).
 * Retrieves active voice channel information, connected members, speaking indicators,
 * and screenshare (live stream) status in real time.
 */
public class DiscordIpcManager {

    private static final String CLIENT_ID = "383226320970055681"; // Discord Desktop IPC Client
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static Thread workerThread = null;

    private static volatile boolean connected = false;
    private static volatile boolean inVoice = false;
    private static volatile String currentChannelId = "";
    private static volatile String currentChannelName = "";
    private static volatile String currentGuildName = "";
    private static volatile String myDiscordUsername = "";
    private static volatile String myUserId = "";
    private static volatile String logReaderStatus = "None";
    private static volatile String activePipeName = "None";
    private static volatile String lastError = "None";
    private static volatile String lastHandshakeStatus = "Not attempted";
    private static volatile long lastPacketTime = 0L;
    private static volatile RandomAccessFile currentPipe = null;
    private static final Object PIPE_LOCK = new Object();
    private static Thread pollThread = null;

    // Prevent repeated popup authorizations
    private static volatile boolean hasAuthorizedThisSession = false;
    private static volatile boolean authCancelled = false;

    // Rolling log of the last 15 RPC packets for detailed diagnosis
    private static final Deque<String> packetHistory = new ConcurrentLinkedDeque<>();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Track active voice users: userId -> DiscordVoiceUser
    private static final Map<String, DiscordVoiceUser> voiceUsers = new ConcurrentHashMap<>();

    public record DiscordVoiceUser(
            String id,
            String username,
            String displayName,
            boolean isMuted,
            boolean isDeafened,
            boolean isSpeaking,
            boolean isScreenSharing
    ) {}

    public static boolean isConnected() {
        return connected;
    }

    public static boolean isInVoice() {
        return inVoice;
    }

    public static String getCurrentChannelName() {
        return currentChannelName;
    }

    public static String getCurrentGuildName() {
        return currentGuildName;
    }

    public static Collection<DiscordVoiceUser> getVoiceUsers() {
        return voiceUsers.values();
    }

    public static void init() {
        if (!BomboConfig.get().discordHudEnabled) {
            return;
        }
        if (RUNNING.compareAndSet(false, true)) {
            workerThread = new Thread(DiscordIpcManager::runLoop, "Bombo-DiscordIPC");
            workerThread.setDaemon(true);
            workerThread.start();
        }
    }

    public static void stop() {
        RUNNING.set(false);
        connected = false;
        RandomAccessFile pipe = currentPipe;
        currentPipe = null;
        if (pollThread != null) {
            try { pollThread.interrupt(); } catch (Throwable ignored) {}
            pollThread = null;
        }
        if (workerThread != null) {
            try { workerThread.interrupt(); } catch (Throwable ignored) {}
            workerThread = null;
        }
        if (pipe != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    pipe.close();
                } catch (Throwable ignored) {}
            });
        }
    }

    public static void requestAuth() {
        hasAuthorizedThisSession = false;
        forceSync();
    }

    private static void logPacket(boolean incoming, String json) {
        String time = LocalTime.now().format(TIME_FORMAT);
        String preview = json.replaceAll("\\s+", " ");
        if (preview.length() > 160) {
            preview = preview.substring(0, 160) + "...";
        }
        packetHistory.add("[" + time + "] " + (incoming ? "RX: " : "TX: ") + preview);
        while (packetHistory.size() > 15) {
            packetHistory.poll();
        }
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            RandomAccessFile pipe = null;
            try {
                pipe = findAndOpenPipe();
                if (pipe != null) {
                    synchronized (PIPE_LOCK) {
                        currentPipe = pipe;
                    }
                    connected = true;
                    startPollThread();

                    // Send Handshake
                    JsonObject handshake = new JsonObject();
                    handshake.addProperty("v", 1);
                    handshake.addProperty("client_id", CLIENT_ID);
                    writePacket(pipe, 0, handshake.toString());

                    // Read frames
                    while (RUNNING.get()) {
                        Packet packet = readPacket(pipe);
                        if (packet == null) break;
                        handlePacket(pipe, packet);
                    }
                }
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            } finally {
                synchronized (PIPE_LOCK) {
                    currentPipe = null;
                }
                connected = false;
                inVoice = false;
                voiceUsers.clear();
                if (pipe != null) {
                    try { pipe.close(); } catch (Exception ignored) {}
                }
            }

            try {
                Thread.sleep(3000L); // retry connection every 3 seconds
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static RandomAccessFile findAndOpenPipe() {
        for (int i = 0; i < 10; i++) {
            String path = "\\\\.\\pipe\\discord-ipc-" + i;
            File file = new File(path);
            try {
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                activePipeName = path;
                return raf;
            } catch (Exception ignored) {}
        }
        activePipeName = "None (No pipe available 0-9)";
        return null;
    }

    private record Packet(int opcode, String json) {}

    public static void forceSync() {
        scanDiscordLogForVoice();
        CompletableFuture.runAsync(() -> {
            try {
                if (!connected || currentPipe == null) {
                    init();
                    return;
                }
                synchronized (PIPE_LOCK) {
                    RandomAccessFile pipe = currentPipe;
                    if (pipe != null && connected) {
                        if (!hasAuthorizedThisSession && !authCancelled) {
                            sendAuthorize(pipe);
                        }
                        JsonObject getVoice = new JsonObject();
                        getVoice.addProperty("cmd", "GET_SELECTED_VOICE_CHANNEL");
                        getVoice.addProperty("nonce", UUID.randomUUID().toString());
                        writePacket(pipe, 1, getVoice.toString());
                    }
                }
            } catch (Throwable t) {
                lastError = "forceSync: " + t.getMessage();
            }
        });
    }

    public static void requestAuthorization() {
        authCancelled = false;
        hasAuthorizedThisSession = false;
        CompletableFuture.runAsync(() -> {
            synchronized (PIPE_LOCK) {
                RandomAccessFile pipe = currentPipe;
                if (pipe != null && connected) {
                    try {
                        sendAuthorize(pipe);
                    } catch (Throwable t) {
                        lastError = "Auth request: " + t.getMessage();
                    }
                }
            }
        });
    }

    private static void sendAuthorize(RandomAccessFile pipe) throws Exception {
        JsonObject authArgs = new JsonObject();
        authArgs.addProperty("client_id", CLIENT_ID);
        JsonArray scopes = new JsonArray();
        scopes.add("rpc");
        scopes.add("rpc.voice.read");
        authArgs.add("scopes", scopes);

        JsonObject authReq = new JsonObject();
        authReq.addProperty("cmd", "AUTHORIZE");
        authReq.add("args", authArgs);
        authReq.addProperty("nonce", UUID.randomUUID().toString());
        writePacket(pipe, 1, authReq.toString());
        hasAuthorizedThisSession = true;
    }

    private static void startPollThread() {
        if (pollThread == null || !pollThread.isAlive()) {
            pollThread = new Thread(() -> {
                while (RUNNING.get() && connected) {
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException e) {
                        break;
                    }
                    pollVoiceStatus();
                }
            }, "Bombo-DiscordVoicePoll");
            pollThread.setDaemon(true);
            pollThread.start();
        }
    }

    private static void pollVoiceStatus() {
        synchronized (PIPE_LOCK) {
            RandomAccessFile pipe = currentPipe;
            if (pipe != null && connected) {
                try {
                    JsonObject getVoice = new JsonObject();
                    getVoice.addProperty("cmd", "GET_SELECTED_VOICE_CHANNEL");
                    getVoice.addProperty("nonce", UUID.randomUUID().toString());
                    writePacket(pipe, 1, getVoice.toString());
                } catch (Throwable t) {
                    lastError = "Poll: " + t.getMessage();
                }
            }
        }
        // Fallback / complementary detection from Discord desktop logs
        scanDiscordLogForVoice();
    }

    public static class DiscordBotUserInfo {
        public final String id;
        public final String username;
        public final String displayName;
        public final String avatar;

        public DiscordBotUserInfo(String id, String username, String displayName, String avatar) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.avatar = avatar;
        }
    }

    private static final Map<String, DiscordBotUserInfo> BOT_USER_CACHE = new ConcurrentHashMap<>();
    private static long lastBotFetchTime = 0L;
    private static final Pattern INBOUND_USER_PATTERN = Pattern.compile("Inbound audio delay stats for user:\\s*(\\d{15,20})");

    private static void fetchMissingBotUsersAsync(Set<String> missingIds) {
        long now = System.currentTimeMillis();
        if (now - lastBotFetchTime < 3000L || missingIds.isEmpty()) return;
        lastBotFetchTime = now;
        CompletableFuture.runAsync(() -> {
            try {
                String idsParam = String.join(",", missingIds);
                String url = "https://api.bombo.dpdns.org/api/bot/users?ids=" + idsParam;
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                        if (entry.getValue().isJsonObject()) {
                            JsonObject uObj = entry.getValue().getAsJsonObject();
                            String uId = entry.getKey();
                            String uName = uObj.has("username") ? uObj.get("username").getAsString() : "User";
                            String dName = uObj.has("displayName") ? uObj.get("displayName").getAsString() : uName;
                            String av = uObj.has("avatar") && !uObj.get("avatar").isJsonNull() ? uObj.get("avatar").getAsString() : null;
                            BOT_USER_CACHE.put(uId, new DiscordBotUserInfo(uId, uName, dName, av));
                        }
                    }
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void scanDiscordLogForVoice() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null) return;

            // 1. Scan renderer_js.log for connection / channel states
            File logFile = new File(appData, "discord/logs/renderer_js.log");
            boolean foundConnected = false;
            boolean foundDisconnect = false;
            boolean foundHeartbeat = false;
            String foundChannel = null;
            int memberCount = 1;

            if (logFile.exists() && logFile.canRead() && logFile.length() > 0) {
                long len = logFile.length();
                int toRead = (int) Math.min(65536L, len);
                byte[] buffer = new byte[toRead];
                try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                    raf.seek(len - toRead);
                    raf.readFully(buffer);
                }

                String content = new String(buffer, StandardCharsets.UTF_8);
                String[] lines = content.split("\r?\n");

                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i];
                    if (!foundDisconnect && line.contains("[RTCConnection(") && line.contains("default)]") && line.contains("[VOICE_DISCONNECT]")) {
                        foundDisconnect = true;
                        break;
                    }
                    if (!foundConnected && line.contains("[RTCConnection(") && line.contains("default)]") && line.contains("RTC_CONNECTED")) {
                        foundConnected = true;
                    }
                    if (!foundHeartbeat && line.contains("[RTCControlSocket(default)]") && line.contains("Heartbeat")) {
                        foundHeartbeat = true;
                    }
                    if (foundChannel == null && line.contains("Updating channel:")) {
                        int idx = line.indexOf("Updating channel:");
                        if (idx != -1) {
                            String rest = line.substring(idx + 17).trim();
                            int paren = rest.indexOf('(');
                            if (paren != -1) {
                                String cStr = rest.substring(paren + 1);
                                int closeP = cStr.indexOf(')');
                                if (closeP != -1) {
                                    try {
                                        memberCount = Math.max(1, Integer.parseInt(cStr.substring(0, closeP).trim()));
                                    } catch (Throwable ignored) {}
                                }
                                rest = rest.substring(0, paren).trim();
                            }
                            if (!rest.isEmpty()) foundChannel = rest;
                        }
                    }
                }
            }

            // 2. Scan WebRTC logs (discord-webrtc_0, discord-webrtc_1) for actual incoming user IDs in call
            Set<String> webrtcUserIds = new LinkedHashSet<>();
            File webrtc0 = new File(appData, "discord/logs/discord-webrtc_0");
            File webrtc1 = new File(appData, "discord/logs/discord-webrtc_1");
            File activeWebrtc = (webrtc0.exists() && webrtc0.length() > 0) ? webrtc0 : (webrtc1.exists() ? webrtc1 : null);

            if (activeWebrtc != null && activeWebrtc.canRead() && activeWebrtc.length() > 0) {
                long wlen = activeWebrtc.length();
                int wToRead = (int) Math.min(65536L, wlen);
                byte[] wbuf = new byte[wToRead];
                try (RandomAccessFile wraf = new RandomAccessFile(activeWebrtc, "r")) {
                    wraf.seek(wlen - wToRead);
                    wraf.readFully(wbuf);
                }
                String wContent = new String(wbuf, StandardCharsets.UTF_8);
                Matcher m = INBOUND_USER_PATTERN.matcher(wContent);
                while (m.find()) {
                    String uid = m.group(1);
                    if (!uid.equals(myUserId)) {
                        webrtcUserIds.add(uid);
                    }
                }
            }

            if (!webrtcUserIds.isEmpty()) {
                memberCount = Math.max(memberCount, webrtcUserIds.size() + 1);
                // Trigger async bot resolution for any un-cached IDs
                Set<String> missing = new HashSet<>();
                for (String uid : webrtcUserIds) {
                    if (!BOT_USER_CACHE.containsKey(uid)) {
                        missing.add(uid);
                    }
                }
                if (!missing.isEmpty()) {
                    fetchMissingBotUsersAsync(missing);
                }
            }

            if ((foundConnected || foundHeartbeat || !webrtcUserIds.isEmpty()) && !foundDisconnect) {
                inVoice = true;
                logReaderStatus = "Active (" + memberCount + " in call via " + (!webrtcUserIds.isEmpty() ? "WebRTC" : "Log") + ")";
                if (currentChannelName.isEmpty() || currentChannelName.startsWith("Voice Call") || currentChannelName.startsWith("Voice (")) {
                    currentChannelName = foundChannel != null ? ("Voice (" + foundChannel.substring(Math.max(0, foundChannel.length() - 4)) + ")") : "Voice Call";
                }
                if (foundChannel != null && currentChannelId.isEmpty()) {
                    currentChannelId = foundChannel;
                }

                if (!webrtcUserIds.isEmpty()) {
                    // Populate voiceUsers using precise WebRTC IDs and resolved bot names
                    String selfId = !myUserId.isEmpty() ? myUserId : "self";
                    String selfName = !myDiscordUsername.isEmpty() ? myDiscordUsername : "You";
                    boolean selfSpeaking = voiceUsers.containsKey(selfId) && voiceUsers.get(selfId).isSpeaking();
                    voiceUsers.put(selfId, new DiscordVoiceUser(selfId, selfName, selfName, false, false, selfSpeaking, false));

                    for (String uid : webrtcUserIds) {
                        DiscordBotUserInfo info = BOT_USER_CACHE.get(uid);
                        String dName = info != null ? info.displayName : ("User (" + uid.substring(Math.max(0, uid.length() - 4)) + ")");
                        String uName = info != null ? info.username : dName;
                        boolean isSpeaking = voiceUsers.containsKey(uid) && voiceUsers.get(uid).isSpeaking();
                        boolean isMuted = voiceUsers.containsKey(uid) && voiceUsers.get(uid).isMuted();
                        boolean isDeaf = voiceUsers.containsKey(uid) && voiceUsers.get(uid).isDeafened();
                        boolean isLive = voiceUsers.containsKey(uid) && voiceUsers.get(uid).isScreenSharing();
                        voiceUsers.put(uid, new DiscordVoiceUser(uid, uName, dName, isMuted, isDeaf, isSpeaking, isLive));
                    }

                    // Remove placeholder member_X entries
                    voiceUsers.keySet().removeIf(k -> k.startsWith("member_"));
                } else if (voiceUsers.isEmpty() || voiceUsers.size() < memberCount) {
                    voiceUsers.clear();
                    String name = !myDiscordUsername.isEmpty() ? myDiscordUsername : "You";
                    String id = !myUserId.isEmpty() ? myUserId : "self";
                    voiceUsers.put(id, new DiscordVoiceUser(id, name, name, false, false, false, false));
                    for (int m_idx = 2; m_idx <= memberCount; m_idx++) {
                        String mId = "member_" + m_idx;
                        voiceUsers.put(mId, new DiscordVoiceUser(mId, "Member " + m_idx, "Member " + m_idx, false, false, false, false));
                    }
                }
            } else if (foundDisconnect) {
                logReaderStatus = "Disconnected (VOICE_DISCONNECT detected)";
                if (lastError.contains("4006") || !connected) {
                    inVoice = false;
                    voiceUsers.clear();
                }
            } else {
                logReaderStatus = "No active call found";
            }
        } catch (Throwable t) {
            logReaderStatus = "Error: " + t.getMessage();
        }
    }

    private static void writePacket(RandomAccessFile pipe, int opcode, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(bytes.length);
        buf.put(bytes);
        pipe.write(buf.array());
        logPacket(false, json);
    }

    private static Packet readPacket(RandomAccessFile pipe) throws Exception {
        byte[] header = new byte[8];
        pipe.readFully(header);
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = buf.getInt();
        int len = buf.getInt();
        if (len < 0 || len > 2_000_000) return null;

        byte[] body = new byte[len];
        pipe.readFully(body);
        String json = new String(body, StandardCharsets.UTF_8);
        logPacket(true, json);
        return new Packet(opcode, json);
    }

    private static void handlePacket(RandomAccessFile pipe, Packet packet) {
        try {
            JsonObject obj = JsonParser.parseString(packet.json()).getAsJsonObject();
            String cmd = obj.has("cmd") && !obj.get("cmd").isJsonNull() ? obj.get("cmd").getAsString() : "";
            String evt = obj.has("evt") && !obj.get("evt").isJsonNull() ? obj.get("evt").getAsString() : "";

            lastPacketTime = System.currentTimeMillis();

            if ("ERROR".equals(evt) || (obj.has("data") && obj.getAsJsonObject("data").has("code") && obj.getAsJsonObject("data").has("message"))) {
                JsonObject data = obj.getAsJsonObject("data");
                int errCode = data.has("code") ? data.get("code").getAsInt() : 0;
                if (errCode == 5000) {
                    authCancelled = true;
                    hasAuthorizedThisSession = true;
                }
                lastError = "Discord Error (" + errCode + "): " + data.get("message").getAsString();
                return;
            }

            if ("DISPATCH".equals(cmd)) {
                if ("READY".equals(evt)) {
                    lastHandshakeStatus = "READY received";
                    if (obj.has("data") && obj.get("data").isJsonObject()) {
                        JsonObject data = obj.getAsJsonObject("data");
                        if (data.has("user") && data.get("user").isJsonObject()) {
                            JsonObject user = data.getAsJsonObject("user");
                            myDiscordUsername = user.has("username") ? user.get("username").getAsString() : "User";
                            myUserId = user.has("id") ? user.get("id").getAsString() : "";
                        }
                    }

                    // Only send AUTHORIZE if enabled in config to avoid the repeated popup modal!
                    if (BomboConfig.get().discordVoiceAutoAuth && !hasAuthorizedThisSession && !authCancelled) {
                        JsonObject authArgs = new JsonObject();
                        authArgs.addProperty("client_id", CLIENT_ID);
                        JsonArray scopes = new JsonArray();
                        scopes.add("rpc");
                        scopes.add("rpc.voice.read");
                        authArgs.add("scopes", scopes);

                        JsonObject authReq = new JsonObject();
                        authReq.addProperty("cmd", "AUTHORIZE");
                        authReq.add("args", authArgs);
                        authReq.addProperty("nonce", UUID.randomUUID().toString());
                        writePacket(pipe, 1, authReq.toString());
                        hasAuthorizedThisSession = true;
                    }

                    // Subscribe to voice channel select & speaking events
                    subscribe(pipe, "VOICE_CHANNEL_SELECT");
                    subscribe(pipe, "VOICE_STATE_CREATE");
                    subscribe(pipe, "VOICE_STATE_UPDATE");
                    subscribe(pipe, "VOICE_STATE_DELETE");
                    subscribe(pipe, "SPEAKING_START");
                    subscribe(pipe, "SPEAKING_STOP");

                    // Query current channel
                    JsonObject getVoice = new JsonObject();
                    getVoice.addProperty("cmd", "GET_SELECTED_VOICE_CHANNEL");
                    getVoice.addProperty("nonce", UUID.randomUUID().toString());
                    writePacket(pipe, 1, getVoice.toString());
                } else if ("VOICE_CHANNEL_SELECT".equals(evt)) {
                    updateVoiceChannel(obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : null);
                } else if ("VOICE_STATE_CREATE".equals(evt) || "VOICE_STATE_UPDATE".equals(evt)) {
                    updateVoiceUser(obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : null);
                } else if ("VOICE_STATE_DELETE".equals(evt)) {
                    removeVoiceUser(obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : null);
                } else if ("SPEAKING_START".equals(evt)) {
                    setSpeaking(obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : null, true);
                } else if ("SPEAKING_STOP".equals(evt)) {
                    setSpeaking(obj.has("data") && obj.get("data").isJsonObject() ? obj.getAsJsonObject("data") : null, false);
                }
            } else if ("AUTHORIZE".equals(cmd)) {
                lastHandshakeStatus = "Authorized (Approval confirmed)";
                pollVoiceStatus();
            } else if ("GET_SELECTED_VOICE_CHANNEL".equals(cmd)) {
                if (obj.has("data") && obj.get("data").isJsonObject()) {
                    updateVoiceChannel(obj.getAsJsonObject("data"));
                } else if (obj.has("data") && obj.get("data").isJsonNull()) {
                    updateVoiceChannel(null);
                }
            }
        } catch (Exception t) {
            lastError = "Handle: " + t.getMessage();
        }
    }

    private static void subscribe(RandomAccessFile pipe, String event) throws Exception {
        JsonObject sub = new JsonObject();
        sub.addProperty("cmd", "SUBSCRIBE");
        sub.addProperty("evt", event);
        sub.addProperty("nonce", UUID.randomUUID().toString());
        writePacket(pipe, 1, sub.toString());
    }

    private static void updateVoiceChannel(JsonObject data) {
        if (data == null || !data.has("id") || data.get("id").isJsonNull()) {
            inVoice = false;
            currentChannelId = "";
            currentChannelName = "";
            currentGuildName = "";
            voiceUsers.clear();
            return;
        }

        inVoice = true;
        currentChannelId = data.get("id").getAsString();
        currentChannelName = data.has("name") && !data.get("name").isJsonNull() ? data.get("name").getAsString() : "Voice Channel";
        voiceUsers.clear();

        if (data.has("voice_states") && data.get("voice_states").isJsonArray()) {
            for (JsonElement el : data.getAsJsonArray("voice_states")) {
                if (el.isJsonObject()) {
                    updateVoiceUser(el.getAsJsonObject());
                }
            }
        }
    }

    private static void updateVoiceUser(JsonObject state) {
        if (state == null || !state.has("user") || !state.get("user").isJsonObject()) return;
        JsonObject user = state.getAsJsonObject("user");
        String id = user.has("id") ? user.get("id").getAsString() : "";
        if (id.isEmpty()) return;

        String username = user.has("username") ? user.get("username").getAsString() : "User";
        String displayName = user.has("global_name") && !user.get("global_name").isJsonNull()
                ? user.get("global_name").getAsString() : username;

        boolean mute = (state.has("mute") && state.get("mute").getAsBoolean())
                || (state.has("self_mute") && state.get("self_mute").getAsBoolean());
        boolean deaf = (state.has("deaf") && state.get("deaf").getAsBoolean())
                || (state.has("self_deaf") && state.get("self_deaf").getAsBoolean());
        boolean screenSharing = (state.has("self_stream") && state.get("self_stream").getAsBoolean())
                || (state.has("self_video") && state.get("self_video").getAsBoolean());

        boolean speaking = voiceUsers.containsKey(id) && voiceUsers.get(id).isSpeaking();

        voiceUsers.put(id, new DiscordVoiceUser(id, username, displayName, mute, deaf, speaking, screenSharing));
    }

    private static void removeVoiceUser(JsonObject state) {
        if (state == null || !state.has("user") || !state.get("user").isJsonObject()) return;
        String id = state.getAsJsonObject("user").has("id") ? state.getAsJsonObject("user").get("id").getAsString() : "";
        if (!id.isEmpty()) {
            voiceUsers.remove(id);
        }
    }

    private static void setSpeaking(JsonObject data, boolean speaking) {
        if (data == null || !data.has("user_id")) return;
        String id = data.get("user_id").getAsString();
        DiscordVoiceUser existing = voiceUsers.get(id);
        if (existing != null) {
            voiceUsers.put(id, new DiscordVoiceUser(
                    existing.id(),
                    existing.username(),
                    existing.displayName(),
                    existing.isMuted(),
                    existing.isDeafened(),
                    speaking,
                    existing.isScreenSharing()
            ));
        } else {
            String name = (id.equals(myUserId) || id.isEmpty()) ? (!myDiscordUsername.isEmpty() ? myDiscordUsername : "You") : ("User (" + id.substring(Math.max(0, id.length() - 4)) + ")");
            voiceUsers.put(id, new DiscordVoiceUser(id, name, name, false, false, speaking, false));
        }
    }

    public static void dumpDebugInfo(Consumer<Component> feedback) {
        scanDiscordLogForVoice();
        feedback.accept(Component.literal("§9========== §b[Discord IPC Detailed Diagnostics] §9=========="));
        feedback.accept(Component.literal("§7Enabled in Config: " + (BomboConfig.get().discordHudEnabled ? "§aYes" : "§cNo")));
        feedback.accept(Component.literal("§7Worker Thread: " + (workerThread != null && workerThread.isAlive() ? "§aAlive" : "§cStopped")));
        feedback.accept(Component.literal("§7Connected: " + (connected ? "§aYes (User: " + myDiscordUsername + ")" : "§cNo")));
        feedback.accept(Component.literal("§7Active Pipe: §f" + activePipeName));
        feedback.accept(Component.literal("§7Last Handshake: §e" + lastHandshakeStatus));
        feedback.accept(Component.literal("§7In Voice: " + (inVoice ? "§aYes (#" + currentChannelName + ", ID: " + currentChannelId + ")" : "§cNo")));
        feedback.accept(Component.literal("§7Local Log Reader: §e" + logReaderStatus));
        feedback.accept(Component.literal("§7Active Members: §b" + voiceUsers.size() + " in call"));
        feedback.accept(Component.literal("§7Last Error: " + (lastError.equals("None") ? "§aNone" : "§c" + lastError)));

        if (lastPacketTime > 0) {
            long agoSec = (System.currentTimeMillis() - lastPacketTime) / 1000L;
            feedback.accept(Component.literal("§7Last Packet: §a" + agoSec + "s ago"));
        }

        // Voice users breakdown
        if (!voiceUsers.isEmpty()) {
            feedback.accept(Component.literal("§6-- Call Members --"));
            for (DiscordVoiceUser u : voiceUsers.values()) {
                feedback.accept(Component.literal("  §7• §f" + u.displayName() + " (§e" + u.username() + "§7) - "
                        + (u.isSpeaking() ? "§aSpeaking " : "§7Silent ")
                        + (u.isScreenSharing() ? "§c[LIVE] " : "")
                        + (u.isMuted() ? "§8[Muted] " : "")
                        + (u.isDeafened() ? "§8[Deafened]" : "")));
            }
        }

        // Recent RPC Packets Log
        feedback.accept(Component.literal("§6-- Recent RPC Packets (Last " + packetHistory.size() + ") --"));
        if (packetHistory.isEmpty()) {
            feedback.accept(Component.literal("  §8(No packets recorded yet)"));
        } else {
            for (String p : packetHistory) {
                feedback.accept(Component.literal("  §8" + p));
            }
        }

        // Process search
        List<String> foundProcesses = new ArrayList<>();
        try {
            ProcessHandle.allProcesses().forEach(p -> {
                String cmd = p.info().command().orElse("");
                String lower = cmd.toLowerCase(Locale.ROOT);
                if (lower.contains("discord") || lower.contains("discordcanary") || lower.contains("discordptb")) {
                    String name = cmd.substring(Math.max(cmd.lastIndexOf('/'), cmd.lastIndexOf('\\')) + 1);
                    foundProcesses.add(name + " (PID " + p.pid() + ")");
                }
            });
        } catch (Throwable t) {
            foundProcesses.add("Error listing processes: " + t.getMessage());
        }
        feedback.accept(Component.literal("§7Discord Processes: " + (foundProcesses.isEmpty() ? "§cNone running!" : "§a" + String.join(", ", foundProcesses))));

        // Pipe probing 0..9
        StringBuilder pipeStatus = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            File f = new File("\\\\.\\pipe\\discord-ipc-" + i);
            try (RandomAccessFile test = new RandomAccessFile(f, "rw")) {
                pipeStatus.append(" §a#").append(i).append("[Open]");
            } catch (Throwable t) {
                if (f.exists()) {
                    pipeStatus.append(" §e#").append(i).append("[Locked]");
                } else {
                    pipeStatus.append(" §8#").append(i).append("[None]");
                }
            }
        }
        feedback.accept(Component.literal("§7Pipes (0-9):" + pipeStatus.toString()));
        feedback.accept(Component.literal("§9========================================================"));
    }

    public static void handleSsCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!connected) {
            mc.player.sendSystemMessage(Component.literal("§9[Discord] §cDiscord Desktop app is not connected. Make sure Discord is open on your PC!"));
            dumpDebugInfo(mc.player::sendSystemMessage);
            return;
        }

        if (!inVoice || voiceUsers.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§9[Discord] §7Connected as §b" + myDiscordUsername + "§7, but you are not in an active Discord voice channel."));
            mc.player.sendSystemMessage(Component.literal("§7Tip: Join a Discord voice channel, then use §e/b discord sync §7or §e/b discord debug§7."));
            return;
        }

        mc.player.sendSystemMessage(Component.literal("§9[Discord] §bVoice Channel: §f#" + currentChannelName + " §7(" + voiceUsers.size() + " in call)"));
        boolean foundStream = false;
        for (DiscordVoiceUser u : voiceUsers.values()) {
            StringBuilder sb = new StringBuilder();
            sb.append(u.isSpeaking() ? "  §a● " : "  §7○ ");
            sb.append("§f").append(u.displayName());
            if (u.isScreenSharing()) {
                sb.append(" §c§l[LIVE / Screen Share]§r");
                foundStream = true;
            }
            if (u.isMuted()) sb.append(" §8[Muted]");
            if (u.isDeafened()) sb.append(" §8[Deafened]");
            mc.player.sendSystemMessage(Component.literal(sb.toString()));
        }

        if (!foundStream) {
            mc.player.sendSystemMessage(Component.literal("§9[Discord] §7No users are currently sharing their screen."));
        }

        // Toggle HUD visibility
        BomboConfig.Settings s = BomboConfig.get();
        s.discordHudEnabled = !s.discordHudEnabled;
        BomboConfig.save();
        if (s.discordHudEnabled) {
            init();
        } else {
            stop();
        }
        mc.player.sendSystemMessage(Component.literal("§9[Discord] §7Discord Voice HUD: " + (s.discordHudEnabled ? "§aEnabled" : "§cDisabled")));
    }
}
