package me.bombo.bomboaddons_final;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PlaytimeTracker {
    private static final File SAVE_FILE = new File(Minecraft.getInstance().gameDirectory, "config/bombo_playtime.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static Map<String, AreaData> areaDataMap = new HashMap<>();
    private static long sessionStartTime = System.currentTimeMillis();
    private static Vec3 lastPos = Vec3.ZERO;
    private static float lastYaw = 0;
    private static float lastPitch = 0;
    private static long lastMoveTime = System.currentTimeMillis();
    private static boolean isAfk = false;
    
    private static long lastTickTime = System.currentTimeMillis();
    private static long lastCloudSyncTime = System.currentTimeMillis();

    public static void load() {
        if (!SAVE_FILE.exists()) return;
        try (FileReader reader = new FileReader(SAVE_FILE)) {
            Type type = new TypeToken<Map<String, AreaData>>(){}.getType();
            Map<String, AreaData> loaded = GSON.fromJson(reader, type);
            if (loaded != null) areaDataMap = loaded;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!SAVE_FILE.getParentFile().exists()) SAVE_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(SAVE_FILE)) {
                GSON.toJson(areaDataMap, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        long now = System.currentTimeMillis();
        long delta = now - lastTickTime;
        lastTickTime = now;
        
        // AFK Detection
        Vec3 currentPos = mc.player.position();
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();
        
        if (!currentPos.equals(lastPos) || currentYaw != lastYaw || currentPitch != lastPitch) {
            lastMoveTime = now;
            isAfk = false;
        } else if (now - lastMoveTime > 60000) { // 1 minute threshold for AFK
            isAfk = true;
        }
        
        lastPos = currentPos;
        lastYaw = currentYaw;
        lastPitch = currentPitch;
        
        String area = normalizeAreaName(BomboaddonsClient.currentArea);
        String today = LocalDate.now().toString();
        
        AreaData data = areaDataMap.computeIfAbsent(area, k -> new AreaData());
        data.totalTime += delta;
        data.sessionTime += delta;
        
        // Update daily stats
        data.dailyTime.put(today, data.dailyTime.getOrDefault(today, 0L) + delta);
        
        if (isAfk) {
            data.afkTime += delta;
            data.sessionAfkTime += delta;
            data.dailyAfk.put(today, data.dailyAfk.getOrDefault(today, 0L) + delta);
        }
        
        // Auto-save every 1 minute
        if (now % 60000 < delta) save();
        
        // Sync to cloud every 5 minutes
        if (now - lastCloudSyncTime > 300000) {
            lastCloudSyncTime = now;
            sendPlaytimeDataToCloud();
        }
    }

    public static String normalizeAreaName(String name) {
        if (name == null || name.isEmpty()) return "Unknown";
        String normalized = name.replaceAll("§.", "");
        if (normalized.startsWith("The ")) {
            normalized = normalized.substring(4);
        }
        return normalized;
    }

    public static Map<String, AreaData> getAreaDataMap() {
        return areaDataMap;
    }

    public static long getSessionTime() {
        return System.currentTimeMillis() - sessionStartTime;
    }

    public static boolean isAfk() {
        return isAfk;
    }

    public static String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return String.format("%ds", seconds);
    }

    public static class AreaData {
        public long totalTime = 0;
        public long afkTime = 0;
        public Map<String, Long> dailyTime = new HashMap<>();
        public Map<String, Long> dailyAfk = new HashMap<>();
        public transient long sessionTime = 0;
        public transient long sessionAfkTime = 0;
    }

    private static void sendPlaytimeDataToCloud() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        String username = mc.player.getScoreboardName();
        String uuid = mc.player.getUUID().toString();
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        payload.put("uuid", uuid);
        payload.put("areaDataMap", areaDataMap);
        
        String json = GSON.toJson(payload);
        
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URI("https://bomboapi.frandl938.workers.dev/playtime").toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = json.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
                int responseCode = conn.getResponseCode();
                DebugUtils.debug("playtime", "Cloud sync response: " + responseCode);
            } catch (Exception e) {
                DebugUtils.debug("playtime", "Cloud sync failed: " + e.getMessage());
            }
        }).start();
    }
}
