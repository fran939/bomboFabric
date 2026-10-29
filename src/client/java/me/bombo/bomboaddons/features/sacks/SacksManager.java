package me.bombo.bomboaddons.features.sacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative Sacks catalog and player sack tracking manager.
 * Maps all SkyBlock sacks, compatible items, and determines if player owns the sack.
 * Persists owned sacks from Sack of Sacks container and API synchronization.
 */
public class SacksManager {
    public static class SackRef {
        public final String sackId;
        public final String sackName;

        public SackRef(String sackId, String sackName) {
            this.sackId = sackId;
            this.sackName = sackName;
        }
    }

    private static final Map<String, List<SackRef>> ITEM_TO_SACKS = new ConcurrentHashMap<>();
    private static final Set<String> OWNED_SACK_IDS = ConcurrentHashMap.newKeySet();
    private static final Path STORAGE_FILE = Path.of("config", "bomboaddons", "owned_sacks.json");
    private static boolean initialized = false;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    static {
        init();
    }

    public static synchronized void init() {
        if (initialized && !ITEM_TO_SACKS.isEmpty()) return;
        loadBundledData();
        loadOwnedSacksFromDisk();
        refreshFromApiAsync();
        initialized = true;
    }

    private static void loadBundledData() {
        try (InputStream in = SacksManager.class.getResourceAsStream("/data/sacks_items.json")) {
            if (in != null) {
                parseJson(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void loadOwnedSacksFromDisk() {
        try {
            if (Files.exists(STORAGE_FILE)) {
                String str = Files.readString(STORAGE_FILE, StandardCharsets.UTF_8);
                JsonElement elem = JsonParser.parseString(str);
                if (elem.isJsonArray()) {
                    for (JsonElement el : elem.getAsJsonArray()) {
                        String id = el.getAsString().toUpperCase(Locale.ROOT);
                        OWNED_SACK_IDS.add(id);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void saveOwnedSacksToDisk() {
        try {
            if (STORAGE_FILE.getParent() != null && !Files.exists(STORAGE_FILE.getParent())) {
                Files.createDirectories(STORAGE_FILE.getParent());
            }
            JsonArray arr = new JsonArray();
            for (String id : OWNED_SACK_IDS) {
                arr.add(id);
            }
            Files.writeString(STORAGE_FILE, arr.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    public static void refreshFromApiAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(BomboApiUrl.api("/mod/sacks")))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(8))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty()) {
                    parseJson(resp.body());
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * Syncs player's sacks directly from the backend API.
     */
    public static void syncPlayerSacksAsync(String username, String profileId) {
        if (username == null || username.trim().isEmpty() || username.equalsIgnoreCase("Player")) return;
        CompletableFuture.runAsync(() -> {
            try {
                String url = BomboApiUrl.api("/mod/sacks/" + encode(username.trim()));
                if (profileId != null && !profileId.isEmpty()) {
                    url += "?profile=" + encode(profileId);
                }
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty()) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("ownedSackIds") && root.get("ownedSackIds").isJsonArray()) {
                        int added = 0;
                        for (JsonElement el : root.getAsJsonArray("ownedSackIds")) {
                            String id = el.getAsString().toUpperCase(Locale.ROOT);
                            if (OWNED_SACK_IDS.add(id)) {
                                added++;
                            }
                        }
                        if (added > 0) {
                            saveOwnedSacksToDisk();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static void parseJson(Object input) {
        try {
            JsonObject root;
            if (input instanceof Reader) {
                root = JsonParser.parseReader((Reader) input).getAsJsonObject();
            } else {
                root = JsonParser.parseString((String) input).getAsJsonObject();
            }

            if (root.has("item_to_sacks") && root.get("item_to_sacks").isJsonObject()) {
                JsonObject mapObj = root.getAsJsonObject("item_to_sacks");
                for (Map.Entry<String, JsonElement> e : mapObj.entrySet()) {
                    String itemId = e.getKey().toUpperCase(Locale.ROOT);
                    List<SackRef> refs = new ArrayList<>();
                    if (e.getValue().isJsonArray()) {
                        for (JsonElement el : e.getValue().getAsJsonArray()) {
                            String sackName = el.getAsString();
                            String sackId = sackName.toUpperCase(Locale.ROOT)
                                    .replaceAll("[^A-Z0-9]+", "_")
                                    .replaceAll("^_+|_+$", "");
                            refs.add(new SackRef(sackId, sackName));
                        }
                    }
                    ITEM_TO_SACKS.put(itemId, refs);
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean fitsInSack(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        return ITEM_TO_SACKS.containsKey(skyblockId.toUpperCase(Locale.ROOT));
    }

    public static List<SackRef> getSacksForItem(String skyblockId) {
        if (skyblockId == null) return Collections.emptyList();
        List<SackRef> list = ITEM_TO_SACKS.get(skyblockId.toUpperCase(Locale.ROOT));
        return list != null ? list : Collections.emptyList();
    }

    public static String getPrimarySackName(String skyblockId) {
        List<SackRef> refs = getSacksForItem(skyblockId);
        if (refs.isEmpty()) return "Sack";

        for (SackRef r : refs) {
            if (!r.sackName.toLowerCase(Locale.ROOT).contains("beginner")) {
                return r.sackName;
            }
        }
        return refs.get(0).sackName;
    }

    /**
     * Records a sack owned by the player (e.g. from Sack of Sacks container GUI).
     */
    public static void recordOwnedSack(String sackId) {
        if (sackId != null && !sackId.isEmpty()) {
            if (OWNED_SACK_IDS.add(sackId.toUpperCase(Locale.ROOT))) {
                saveOwnedSacksToDisk();
            }
        }
    }

    public static void recordOwnedSacks(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return;
        boolean changed = false;
        for (String id : ids) {
            if (id != null && !id.isEmpty()) {
                if (OWNED_SACK_IDS.add(id.toUpperCase(Locale.ROOT))) {
                    changed = true;
                }
            }
        }
        if (changed) {
            saveOwnedSacksToDisk();
        }
    }

    public static Set<String> getOwnedSackIds() {
        return Collections.unmodifiableSet(OWNED_SACK_IDS);
    }

    /**
     * Checks if the player currently owns any sack capable of holding this item.
     * Searches both persistent Sack of Sacks registry and player's inventory.
     */
    public static boolean playerHasSack(String skyblockId) {
        List<SackRef> refs = getSacksForItem(skyblockId);
        if (refs.isEmpty()) return false;

        Set<String> candidateIds = new HashSet<>();
        for (SackRef r : refs) {
            if (r.sackId != null && !r.sackId.isEmpty()) {
                String raw = r.sackId.toUpperCase(Locale.ROOT);
                candidateIds.add(raw);
                String norm = raw.replaceAll("^(LARGE_|MEDIUM_|SMALL_|BEGINNER_)", "");
                candidateIds.add(norm);
                candidateIds.add("LARGE_" + norm);
                candidateIds.add("MEDIUM_" + norm);
                candidateIds.add("SMALL_" + norm);
                candidateIds.add("BEGINNER_" + norm);
            }
        }

        // 1. Check persistent Sack of Sacks collection
        for (String owned : OWNED_SACK_IDS) {
            String upper = owned.toUpperCase(Locale.ROOT);
            if (candidateIds.contains(upper)) {
                return true;
            }
            String norm = upper.replaceAll("^(LARGE_|MEDIUM_|SMALL_|BEGINNER_)", "");
            if (candidateIds.contains(norm)) {
                return true;
            }
        }

        // 2. Scan player active inventory for carried sacks
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getInventory() != null) {
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack item = mc.player.getInventory().getItem(i);
                if (item == null || item.isEmpty()) continue;
                String sbId = SkyblockUtils.getStrictSkyblockId(item);
                if (sbId != null) {
                    String upper = sbId.toUpperCase(Locale.ROOT);
                    if (candidateIds.contains(upper)) {
                        OWNED_SACK_IDS.add(upper);
                        saveOwnedSacksToDisk();
                        return true;
                    }
                    String norm = upper.replaceAll("^(LARGE_|MEDIUM_|SMALL_|BEGINNER_)", "");
                    if (candidateIds.contains(norm)) {
                        OWNED_SACK_IDS.add(upper);
                        saveOwnedSacksToDisk();
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
