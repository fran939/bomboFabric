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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
    private static volatile String activePipeName = "None";
    private static volatile String lastError = "None";
    private static volatile String lastHandshakeStatus = "Not attempted";
    private static volatile long lastPacketTime = 0L;
    private static volatile RandomAccessFile currentPipe = null;
    private static final Object PIPE_LOCK = new Object();
    private static Thread pollThread = null;

    // Prevent repeated popup authorizations
    private static volatile boolean hasAuthorizedThisSession = false;

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
        synchronized (PIPE_LOCK) {
            if (currentPipe != null) {
                try {
                    currentPipe.close();
                } catch (Exception ignored) {}
                currentPipe = null;
            }
        }
        if (pollThread != null) {
            pollThread.interrupt();
            pollThread = null;
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
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
        pollVoiceStatus();
    }

    public static void requestAuthorization() {
        hasAuthorizedThisSession = false;
        synchronized (PIPE_LOCK) {
            RandomAccessFile pipe = currentPipe;
            if (pipe != null && connected) {
                try {
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
                } catch (Throwable t) {
                    lastError = "Auth request: " + t.getMessage();
                }
            }
        }
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
                lastError = "Discord Error (" + data.get("code").getAsInt() + "): " + data.get("message").getAsString();
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
                        }
                    }

                    // Only send AUTHORIZE once to avoid the repeated popup modal!
                    if (!hasAuthorizedThisSession) {
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
        }
    }

    public static void dumpDebugInfo(Consumer<Component> feedback) {
        feedback.accept(Component.literal("§9========== §b[Discord IPC Detailed Diagnostics] §9=========="));
        feedback.accept(Component.literal("§7Enabled in Config: " + (BomboConfig.get().discordHudEnabled ? "§aYes" : "§cNo")));
        feedback.accept(Component.literal("§7Worker Thread: " + (workerThread != null && workerThread.isAlive() ? "§aAlive" : "§cStopped")));
        feedback.accept(Component.literal("§7Connected: " + (connected ? "§aYes (User: " + myDiscordUsername + ")" : "§cNo")));
        feedback.accept(Component.literal("§7Active Pipe: §f" + activePipeName));
        feedback.accept(Component.literal("§7Last Handshake: §e" + lastHandshakeStatus));
        feedback.accept(Component.literal("§7In Voice: " + (inVoice ? "§aYes (#" + currentChannelName + ", ID: " + currentChannelId + ")" : "§cNo")));
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
