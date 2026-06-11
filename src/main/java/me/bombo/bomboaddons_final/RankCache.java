package me.bombo.bomboaddons_final;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RankCache {
    private static final Path CACHE_PATH = FabricLoader.getInstance().getConfigDir().resolve("bombo/ranks.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final Set<String> pendingFetches = ConcurrentHashMap.newKeySet();
    private static final Set<String> fetchedThisSession = ConcurrentHashMap.newKeySet();

    public static void load() {
        try {
            if (Files.exists(CACHE_PATH)) {
                try (Reader reader = Files.newBufferedReader(CACHE_PATH)) {
                    java.lang.reflect.Type type = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> loaded = GSON.fromJson(reader, type);
                    if (loaded != null) {
                        loaded.forEach((k, v) -> {
                            if (v != null) {
                                String clean = v.replaceAll("[§&].", "");
                                if (!clean.matches(".*\\d+.*")) {
                                    cache.put(k, v);
                                }
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Auto-fetch local player's rank on start
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null) {
            String name = mc.getUser().getName();
            if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Player")) {
                fetchAsync(name);
            }
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CACHE_PATH.getParent())) {
                Files.createDirectories(CACHE_PATH.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CACHE_PATH)) {
                GSON.toJson(cache, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getRank(String username) {
        if (username == null || username.isEmpty()) return "";
        String lowerName = username.toLowerCase();
        
        if (!fetchedThisSession.contains(lowerName)) {
            fetchAsync(username);
        }
        
        String rank = cache.get(lowerName);
        if (rank == null) {
            return "";
        }
        String clean = rank.replaceAll("[§&].", "");
        if (clean.matches(".*\\d+.*")) {
            return "";
        }
        return rank;
    }

    public static void setRank(String username, String rank) {
        if (username == null || username.isEmpty() || rank == null) return;
        String lowerName = username.toLowerCase();
        cache.put(lowerName, rank);
        fetchedThisSession.add(lowerName);
        save();
    }

    public static void fetchAsync(String username) {
        if (username == null || username.isEmpty()) return;
        String lowerName = username.toLowerCase();
        if (!pendingFetches.add(lowerName)) return;
        
        fetchedThisSession.add(lowerName);

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URI("https://sbecommands-api.icarusphantom.dev/v1/sbecommands/nw/" + username).toURL();
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int status = conn.getResponseCode();
                if (status == 200) {
                    try (java.io.InputStreamReader reader = new java.io.InputStreamReader(conn.getInputStream(), "UTF-8")) {
                        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("data")) {
                            com.google.gson.JsonObject data = obj.getAsJsonObject("data");
                            if (data.has("rank")) {
                                String rank = data.get("rank").getAsString();
                                if (rank != null) {
                                    cache.put(lowerName, rank);
                                    save();
                                    return;
                                }
                            }
                        }
                    }
                }
                if (!cache.containsKey(lowerName)) {
                    cache.put(lowerName, "");
                    save();
                }
            } catch (Exception e) {
                fetchedThisSession.remove(lowerName);
            } finally {
                pendingFetches.remove(lowerName);
            }
        }, "Rank-Fetch-" + username).start();
    }
}
