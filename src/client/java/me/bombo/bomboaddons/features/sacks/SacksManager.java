package me.bombo.bomboaddons.features.sacks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.inferno.SacksHelper;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authoritative manager for Sacks and item-to-sack compatibility.
 */
public class SacksManager {
    public static class SackRef {
        public final String sackId;
        public final String sackName;
        public final String category;

        public SackRef(String sackId, String sackName, String category) {
            this.sackId = sackId;
            this.sackName = sackName;
            this.category = category;
        }
    }

    private static final Map<String, List<SackRef>> ITEM_TO_SACKS = new ConcurrentHashMap<>();
    private static final Set<String> KNOWN_SACK_IDS = ConcurrentHashMap.newKeySet();
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

    private static void parseJson(Object input) {
        try {
            JsonObject root;
            if (input instanceof java.io.Reader) {
                root = JsonParser.parseReader((java.io.Reader) input).getAsJsonObject();
            } else {
                root = JsonParser.parseString((String) input).getAsJsonObject();
            }

            if (root.has("sacks") && root.get("sacks").isJsonArray()) {
                for (JsonElement el : root.getAsJsonArray("sacks")) {
                    if (el.isJsonObject()) {
                        String sid = el.getAsJsonObject().has("id") ? el.getAsJsonObject().get("id").getAsString() : "";
                        if (!sid.isEmpty()) {
                            KNOWN_SACK_IDS.add(sid.toUpperCase(Locale.ROOT));
                        }
                    }
                }
            }

            if (root.has("item_to_sacks") && root.get("item_to_sacks").isJsonObject()) {
                JsonObject map = root.getAsJsonObject("item_to_sacks");
                for (Map.Entry<String, JsonElement> e : map.entrySet()) {
                    String itemId = e.getKey().toUpperCase(Locale.ROOT);
                    if (!e.getValue().isJsonArray()) continue;

                    List<SackRef> refs = new ArrayList<>();
                    for (JsonElement rel : e.getValue().getAsJsonArray()) {
                        if (!rel.isJsonObject()) continue;
                        JsonObject robj = rel.getAsJsonObject();
                        String sid = robj.has("sack_id") ? robj.get("sack_id").getAsString() : "";
                        String sname = robj.has("sack_name") ? robj.get("sack_name").getAsString() : "";
                        String scat = robj.has("category") ? robj.get("category").getAsString() : "General";
                        refs.add(new SackRef(sid, sname, scat));
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
        List<SackRef> refs = ITEM_TO_SACKS.get(skyblockId.toUpperCase(Locale.ROOT));
        return refs != null ? refs : Collections.emptyList();
    }

    /**
     * Returns the primary/cleanest sack name for this item.
     * Prefers non-beginner sack name (e.g. "Agronomy Sack" over "Beginner Agronomy Sack").
     */
    public static String getPrimarySackName(String skyblockId) {
        List<SackRef> refs = getSacksForItem(skyblockId);
        if (refs.isEmpty()) return "Sack";

        // Try to pick non-beginner sack first
        for (SackRef r : refs) {
            if (!r.sackName.toLowerCase(Locale.ROOT).contains("beginner")) {
                return r.sackName;
            }
        }
        return refs.get(0).sackName;
    }

    /**
     * Checks if the player currently owns any sack capable of holding this item.
     * Looks in player inventory and Sack of Sacks.
     */
    public static boolean playerHasSack(String skyblockId) {
        List<SackRef> refs = getSacksForItem(skyblockId);
        if (refs.isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return true; // Default to true if player world not ready

        Set<String> candidateIds = new HashSet<>();
        for (SackRef r : refs) {
            if (r.sackId != null && !r.sackId.isEmpty()) {
                candidateIds.add(r.sackId.toUpperCase(Locale.ROOT));
                // Also normalized without sizes
                String norm = r.sackId.replaceAll("^(LARGE_|MEDIUM_|SMALL_|BEGINNER_)", "");
                candidateIds.add(norm);
                candidateIds.add("LARGE_" + norm);
                candidateIds.add("MEDIUM_" + norm);
                candidateIds.add("SMALL_" + norm);
                candidateIds.add("BEGINNER_" + norm);
            }
        }

        // 1. Scan player inventory for sack items
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack item = mc.player.getInventory().getItem(i);
            if (item == null || item.isEmpty()) continue;
            String sbId = SkyblockUtils.getStrictSkyblockId(item);
            if (sbId != null) {
                String upper = sbId.toUpperCase(Locale.ROOT);
                if (candidateIds.contains(upper)) {
                    return true;
                }
                String norm = upper.replaceAll("^(LARGE_|MEDIUM_|SMALL_|BEGINNER_)", "");
                if (candidateIds.contains(norm)) {
                    return true;
                }
            }
        }

        return false;
    }
}
