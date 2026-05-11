package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class LowestBinManager {
    private static final HttpClient client = HttpClient.newBuilder().build();
    private static final Map<String, Long> priceCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> npcCache = new ConcurrentHashMap<>();
    private static final Map<String, Double> bazaarCache = new ConcurrentHashMap<>();
    private static long lastFetchTime = 0;
    private static long lastBazaarFetch = 0;
    private static long lastNpcFetch = 0;
    private static final long CACHE_DURATION = 300000; // 5 minutes in ms

    public static void ensureLoaded() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        boolean pricesFresh = !priceCache.isEmpty() && (now - lastFetchTime < CACHE_DURATION);
        boolean npcFresh = !npcCache.isEmpty() && (now - lastNpcFetch < CACHE_DURATION);
        if (!bazaarFresh || !pricesFresh || !npcFresh) {
            reload();
        }
    }

    public static void reload() {
        CompletableFuture.allOf(
            fetchFromBazaar(), 
            fetchFromPrices(), 
            fetchFromNpc(),
            BitsManager.ensureLoaded()
        );
    }

    public static String getStatus() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        boolean pricesFresh = now - lastFetchTime < CACHE_DURATION;
        StringBuilder sb = new StringBuilder("§6API Status:\n");
        sb.append("§7- Prices: ").append(pricesFresh ? "§aFresh" : "§cStale").append(" §8(").append(priceCache.size()).append(" ids)\n");
        sb.append("§7- Bazaar: ").append(bazaarFresh ? "§aFresh" : "§cStale").append(" §8(").append(bazaarCache.size()).append(" ids)\n");
        sb.append("§7- NPC: ").append(!npcCache.isEmpty() && (now - lastNpcFetch < CACHE_DURATION) ? "§aFresh" : "§cStale").append(" §8(").append(npcCache.size()).append(" ids)\n");
        sb.append("§7- Bits: §aLoaded §8(").append(BitsManager.bitCostCache.size()).append(" ids)");
        return sb.toString();
    }

    public static String findIdByName(String name) {
        return findIdByName(name, false);
    }

    public static String findIdByName(String name, boolean isStrict) {
        String search = name.toLowerCase().trim().replace(" ", "_");
        
        if (search.startsWith("e_")) search = "ENCHANTED_" + search.substring(2);
        else if (search.startsWith("enchanted_")) { /* handled */ }
        else if (search.startsWith("s_")) search = "SUPER_" + search.substring(2);
        
        String upperSearch = search.toUpperCase();
        
        if (bazaarCache.containsKey(upperSearch)) return upperSearch;
        if (priceCache.containsKey(upperSearch)) return upperSearch;
        
        if (isStrict) return null;
        
        for (String id : bazaarCache.keySet()) {
            if (id.toLowerCase().contains(search)) return id;
        }
        for (String id : priceCache.keySet()) {
            if (id.toLowerCase().contains(search)) return id;
        }
        
        return null;
    }

    public static long getCachedPrice(String skyblockId) {
        if (skyblockId == null) return -1;
        if (bazaarCache.containsKey(skyblockId)) return Math.round(bazaarCache.get(skyblockId));
        if (priceCache.containsKey(skyblockId)) return priceCache.get(skyblockId);
        
        // Fallback for IDs with delimiters (e.g. RED_SCARF;0 -> RED_SCARF)
        if (skyblockId.contains(";")) {
            String baseId = skyblockId.split(";")[0];
            if (bazaarCache.containsKey(baseId)) return Math.round(bazaarCache.get(baseId));
            if (priceCache.containsKey(baseId)) return priceCache.get(baseId);
        }
        
        return -1L;
    }

    public static boolean isBazaar(String skyblockId) {
        if (skyblockId == null) return false;
        if (bazaarCache.containsKey(skyblockId)) return true;
        if (skyblockId.contains(";")) {
            return bazaarCache.containsKey(skyblockId.split(";")[0]);
        }
        return false;
    }

    public static CompletableFuture<Long> getLowestBin(String skyblockId) {
        if (skyblockId == null) return CompletableFuture.completedFuture(-1L);
        return CompletableFuture.completedFuture(getCachedPrice(skyblockId));
    }

    public static long getNpcPrice(String skyblockId) {
        if (skyblockId == null) return -1;
        if (npcCache.containsKey(skyblockId)) return npcCache.get(skyblockId);
        if (skyblockId.contains(";")) {
            String baseId = skyblockId.split(";")[0];
            if (npcCache.containsKey(baseId)) return npcCache.get(baseId);
        }
        return -1L;
    }

    private static CompletableFuture<Boolean> fetchFromBazaar() {
        String url = "https://bomboapi.frandl938.workers.dev/hyp/skyblock/bazaar";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("success") && json.get("success").getAsBoolean()) {
                                JsonObject products = json.getAsJsonObject("products");
                                for (String key : products.keySet()) {
                                    JsonObject product = products.getAsJsonObject(key);
                                    if (product.has("quick_status")) {
                                        double buyPrice = product.getAsJsonObject("quick_status").get("buyPrice").getAsDouble();
                                        bazaarCache.put(key, buyPrice);
                                    }
                                }
                                lastBazaarFetch = System.currentTimeMillis();
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> false);
    }

    private static CompletableFuture<Boolean> fetchFromPrices() {
        return fetchFromUrl("https://api.eliteskyblock.com/resources/auctions/neu")
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://api.odtheking.com/lb/lowestbins");
            })
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://maro.skyblockextras.com/api/auctions/all");
            })
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://bomboapi.frandl938.workers.dev/prices2");
            })
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://bomboapi.frandl938.workers.dev/prices");
            });
    }

    private static CompletableFuture<Boolean> fetchFromNpc() {
        String url = "https://bomboapi.frandl938.workers.dev/npc";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("data") && json.get("data").isJsonArray()) {
                                JsonArray dataArray = json.getAsJsonArray("data");
                                int count = 0;
                                for (JsonElement element : dataArray) {
                                    if (!element.isJsonObject()) continue;
                                    JsonObject item = element.getAsJsonObject();
                                    if (item.has("id") && item.has("npc_sell_price")) {
                                        String id = item.get("id").getAsString();
                                        long value = Math.round(item.get("npc_sell_price").getAsDouble());
                                        npcCache.put(id, value);
                                        if (id.contains(";")) {
                                            npcCache.putIfAbsent(id.split(";")[0], value);
                                        }
                                        count++;
                                    }
                                }
                                lastNpcFetch = System.currentTimeMillis();
                                if (BomboConfig.get().debugMode) {
                                    Bomboaddons.sendMessage("§7[Debug] Loaded " + count + " NPC prices");
                                }
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> false);
    }

    private static CompletableFuture<Boolean> fetchFromUrl(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonElement root = JsonParser.parseString(response.body());
                            int count = 0;
                            
                            if (root.isJsonObject()) {
                                JsonObject json = root.getAsJsonObject();
                                
                                // Case 1: {"data": [...]} or {"data": {...}}
                                if (json.has("data")) {
                                    JsonElement data = json.get("data");
                                    if (data.isJsonArray()) {
                                        JsonArray dataArray = data.getAsJsonArray();
                                        for (JsonElement element : dataArray) {
                                            if (element.isJsonObject()) {
                                                JsonObject item = element.getAsJsonObject();
                                                if (item.has("id") && item.has("value")) {
                                                    String id = item.get("id").getAsString();
                                                    long value = Math.round(item.get("value").getAsDouble());
                                                    priceCache.put(id, value);
                                                    if (id.contains(";")) priceCache.putIfAbsent(id.split(";")[0], value);
                                                    count++;
                                                }
                                            }
                                        }
                                    } else if (data.isJsonObject()) {
                                        JsonObject dataObj = data.getAsJsonObject();
                                        for (String key : dataObj.keySet()) {
                                            JsonElement val = dataObj.get(key);
                                            if (val.isJsonPrimitive()) {
                                                long value = Math.round(val.getAsDouble());
                                                priceCache.put(key, value);
                                                if (key.contains(";")) priceCache.putIfAbsent(key.split(";")[0], value);
                                                count++;
                                            }
                                        }
                                    }
                                } 
                                // Case 2: Flat object {"ITEM_ID": price, ...}
                                else {
                                    for (String key : json.keySet()) {
                                        JsonElement val = json.get(key);
                                        if (val.isJsonPrimitive()) {
                                            try {
                                                long value = Math.round(val.getAsDouble());
                                                priceCache.put(key, value);
                                                if (key.contains(";")) priceCache.putIfAbsent(key.split(";")[0], value);
                                                count++;
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }
                            } else if (root.isJsonArray()) {
                                // Case 3: Raw array [...]
                                JsonArray array = root.getAsJsonArray();
                                for (JsonElement element : array) {
                                    if (element.isJsonObject()) {
                                        JsonObject item = element.getAsJsonObject();
                                        if (item.has("id") && item.has("value")) {
                                            String id = item.get("id").getAsString();
                                            long value = Math.round(item.get("value").getAsDouble());
                                            priceCache.put(id, value);
                                            if (id.contains(";")) priceCache.putIfAbsent(id.split(";")[0], value);
                                            count++;
                                        }
                                    }
                                }
                            }

                            if (count > 0) {
                                lastFetchTime = System.currentTimeMillis();
                                if (BomboConfig.get().debugMode) {
                                    Bomboaddons.sendMessage("§7[Debug] Loaded " + count + " prices from " + url);
                                }
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    return false;
                });
    }

    public static String formatPrice(long price) {
        if (price >= 1000000000L) {
            return String.format("%.2fB", (double) price / 1.0E9D);
        } else if (price >= 1000000L) {
            return String.format("%.2fM", (double) price / 1000000.0D);
        } else if (price >= 1000L) {
            return String.format("%.1fK", (double) price / 1000.0D);
        } else {
            return String.valueOf(price);
        }
    }
}
