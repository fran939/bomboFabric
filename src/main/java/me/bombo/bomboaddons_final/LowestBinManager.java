package me.bombo.bomboaddons_final;

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
    private static final Map<String, Double> bazaarCache = new ConcurrentHashMap<>();
    private static long lastFetchTime = 0;
    private static long lastBazaarFetch = 0;
    private static final long CACHE_DURATION = 300000; // 5 minutes in ms

    public static void ensureLoaded() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        boolean pricesFresh = !priceCache.isEmpty() && (now - lastFetchTime < CACHE_DURATION);
        if (!bazaarFresh || !pricesFresh) {
            reload();
        }
    }

    public static void reload() {
        CompletableFuture.allOf(
            fetchFromBazaar(), 
            fetchFromPrices(), 
            BitsManager.ensureLoaded()
        ).thenRun(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("§b[BomboAddons] §aPrices Loaded! §8(" + priceCache.size() + " items)"), false);
            }
        });
    }

    public static String getStatus() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        boolean pricesFresh = now - lastFetchTime < CACHE_DURATION;
        StringBuilder sb = new StringBuilder("§6API Status:\n");
        sb.append("§7- Prices: ").append(pricesFresh ? "§aFresh" : "§cStale").append(" §8(").append(priceCache.size()).append(" ids)\n");
        sb.append("§7- Bazaar: ").append(bazaarFresh ? "§aFresh" : "§cStale").append(" §8(").append(bazaarCache.size()).append(" ids)\n");
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
        return priceCache.getOrDefault(skyblockId, -1L);
    }

    public static CompletableFuture<Long> getLowestBin(String skyblockId) {
        if (skyblockId == null) return CompletableFuture.completedFuture(-1L);
        return CompletableFuture.completedFuture(getCachedPrice(skyblockId));
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
        String url = "https://bomboapi.frandl938.workers.dev/prices";
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
                            for (String key : json.keySet()) {
                                String cleanKey = key;
                                if (key.contains(";")) {
                                    cleanKey = key.split(";")[0];
                                }
                                priceCache.put(cleanKey, json.get(key).getAsLong());
                            }
                            lastFetchTime = System.currentTimeMillis();
                            return true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> false);
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
