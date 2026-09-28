package me.bombo.bomboaddons.features.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects directly to the local Discord Desktop app via Windows Named Pipes (\\.\pipe\discord-ipc-0).
 * Retrieves active voice channel information, connected members, speaking indicators,
 * and screenshare (live stream) status in real time.
 */
public class DiscordIpcManager {

    private static final String CLIENT_ID = "134500000000000000"; // BomboAddons Discord Client
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static Thread workerThread = null;

    private static volatile boolean connected = false;
    private static volatile boolean inVoice = false;
    private static volatile String currentChannelName = "";
    private static volatile String currentGuildName = "";
    private static volatile String myDiscordUsername = "";

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
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    private static void runLoop() {
        while (RUNNING.get()) {
            RandomAccessFile pipe = null;
            try {
                pipe = findAndOpenPipe();
                if (pipe != null) {
                    connected = true;
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
            } catch (Exception ignored) {
            } finally {
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
        for (int i = 0; i < 4; i++) {
            File file = new File("\\\\.\\pipe\\discord-ipc-" + i);
            try {
                return new RandomAccessFile(file, "rw");
            } catch (Exception ignored) {}
        }
        return null;
    }

    private record Packet(int opcode, String json) {}

    private static void writePacket(RandomAccessFile pipe, int opcode, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(bytes.length);
        buf.put(bytes);
        pipe.write(buf.array());
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
        return new Packet(opcode, new String(body, StandardCharsets.UTF_8));
    }

    private static void handlePacket(RandomAccessFile pipe, Packet packet) {
        try {
            JsonObject obj = JsonParser.parseString(packet.json()).getAsJsonObject();
            String cmd = obj.has("cmd") && !obj.get("cmd").isJsonNull() ? obj.get("cmd").getAsString() : "";
            String evt = obj.has("evt") && !obj.get("evt").isJsonNull() ? obj.get("evt").getAsString() : "";

            if ("DISPATCH".equals(cmd)) {
                if ("READY".equals(evt)) {
                    if (obj.has("data") && obj.get("data").isJsonObject()) {
                        JsonObject data = obj.getAsJsonObject("data");
                        if (data.has("user") && data.get("user").isJsonObject()) {
                            JsonObject user = data.getAsJsonObject("user");
                            myDiscordUsername = user.has("username") ? user.get("username").getAsString() : "User";
                        }
                    }
                    // Authorize & Subscribe to voice channels
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
            } else if ("GET_SELECTED_VOICE_CHANNEL".equals(cmd)) {
                if (obj.has("data") && obj.get("data").isJsonObject()) {
                    updateVoiceChannel(obj.getAsJsonObject("data"));
                }
            }
        } catch (Exception ignored) {
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
            currentChannelName = "";
            currentGuildName = "";
            voiceUsers.clear();
            return;
        }

        inVoice = true;
        currentChannelName = data.has("name") ? data.get("name").getAsString() : "Voice Channel";
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

    /**
     * Handles /ss or /b ss command: outputs Discord Voice status, screenshares, and toggles HUD.
     */
    public static void handleSsCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!connected) {
            mc.player.sendSystemMessage(Component.literal("§9[Discord] §cDiscord Desktop app is not connected. Make sure Discord is open on your PC!"));
            return;
        }

        if (!inVoice || voiceUsers.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§9[Discord] §7Connected as §b" + myDiscordUsername + "§7, but you are not in an active Discord voice channel."));
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
