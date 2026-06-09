package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class BitsManager {
    private static final HttpClient client = HttpClient.newBuilder().build();
    public static final java.util.Map<String, Integer> bitCostCache = new java.util.concurrent.ConcurrentHashMap<>();
    static {
        bitCostCache.put("GOD_POTION_2", 1500);
        bitCostCache.put("KISMET_FEATHER", 1350);
        bitCostCache.put("KAT_FLOWER", 500);
        bitCostCache.put("KAT_BOUQUET", 2500);
        bitCostCache.put("MATRIARCH_PARFUM", 1200);
        bitCostCache.put("HOLOGRAM", 2000);
        bitCostCache.put("DITTO_BLOB", 600);
        bitCostCache.put("BUILDERS_WAND", 12000);
        bitCostCache.put("BLOCK_ZAPPER", 5000);
        bitCostCache.put("BITS_TALISMAN", 15000);
        bitCostCache.put("SHARD_BITBUG", 5000);
        bitCostCache.put("POCKET_SACK_IN_A_SACK", 8000);
        bitCostCache.put("PORTALIZER", 4800);
        bitCostCache.put("TRIO_CONTACTS_ADDON", 6450);
        bitCostCache.put("AUTOPET_RULES_2", 21000);
        bitCostCache.put("ENCHANTMENT_EXPERTISE_1", 4000);
        bitCostCache.put("ENCHANTMENT_COMPACT_1", 4000);
        bitCostCache.put("ENCHANTMENT_CULTIVATING_1", 4000);
        bitCostCache.put("ENCHANTMENT_ABSORB_1", 4000);
        bitCostCache.put("ENCHANTMENT_CHAMPION_1", 4000);
        bitCostCache.put("ENCHANTMENT_HECATOMB_1", 6000);
        bitCostCache.put("ENCHANTMENT_TOXOPHILITE_1", 4000);
        bitCostCache.put("TALISMAN_ENRICHMENT_SWAPPER", 200);
        bitCostCache.put("HEAT_CORE", 3000);
        bitCostCache.put("HYPER_CATALYST_UPGRADE", 300);
        bitCostCache.put("ULTIMATE_CARROT_CANDY_UPGRADE", 8000);
        bitCostCache.put("COLOSSAL_EXP_BOTTLE_UPGRADE", 1200);
        bitCostCache.put("JUMBO_BACKPACK_UPGRADE", 4000);
        bitCostCache.put("MINION_STORAGE_EXPANDER", 1500);
    }
    private static final AtomicBoolean isFetching = new AtomicBoolean(false);
    private static long lastFetchTime = 0;
    private static long lastAttemptTime = 0;

    public static CompletableFuture<Boolean> ensureLoaded() {
        long now = System.currentTimeMillis();
        if (!bitCostCache.isEmpty() && (now - lastFetchTime < 300000)) {
            return CompletableFuture.completedFuture(true);
        }
        if (now - lastAttemptTime < 30000) {
            return CompletableFuture.completedFuture(false);
        }

        if (!isFetching.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }
        lastAttemptTime = now;

        String url = "https://bomboapi.frandl938.workers.dev/bi";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                            for (JsonElement element : jsonArray) {
                                JsonObject obj = element.getAsJsonObject();
                                if (obj.has("name") && obj.has("bits")) {
                                    bitCostCache.put(obj.get("name").getAsString(), obj.get("bits").getAsInt());
                                }
                            }
                            lastFetchTime = System.currentTimeMillis();
                            return true;
                        } catch (Exception e) {}
                    }
                    return false;
                }).exceptionally(ex -> false)
                .whenComplete((res, ex) -> isFetching.set(false));
    }

    public static class BitItem {
        public String originalName;
        public String formattedName;
        public double profitPerBit;

        public BitItem(String originalName, double profitPerBit) {
            this.originalName = originalName;
            this.profitPerBit = profitPerBit;
            this.formattedName = formatName(originalName);
        }

        private String formatName(String name) {
            int bracketIndex = name.indexOf("(");
            if (bracketIndex != -1) {
                name = name.substring(0, bracketIndex).trim();
            }
            
            String[] parts = name.toLowerCase().split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (part.length() > 0) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1));
                    }
                    sb.append(" ");
                }
            }
            return sb.toString().trim();
        }
    }

    private static void getLocalTopBits(int amount, List<String> results, String reason) {
        try {
            List<BitItem> items = new ArrayList<>();
            for (java.util.Map.Entry<String, Integer> entry : bitCostCache.entrySet()) {
                String name = entry.getKey();
                int bits = entry.getValue();
                if (bits <= 0) continue;
                
                double price = LowestBinManager.getSellPrice(name);
                if (price <= 0) {
                    String foundId = LowestBinManager.findIdByName(name, true);
                    if (foundId != null) {
                        price = LowestBinManager.getSellPrice(foundId);
                    }
                }
                
                if (price > 0) {
                    items.add(new BitItem(name, price / bits));
                }
            }
            
            if (!items.isEmpty()) {
                items.sort(Comparator.comparingDouble((BitItem b) -> b.profitPerBit).reversed());
                results.add("§6Profit Per Bit §7(Local Fallback)");
                for (int i = 0; i < Math.min(amount, items.size()); i++) {
                    BitItem item = items.get(i);
                    results.add(String.format("§e%s: §a%d", item.formattedName, Math.round(item.profitPerBit)));
                }
            } else {
                results.add("§c" + reason);
            }
        } catch (Exception ex) {
            results.add("§c" + reason);
        }
    }

    public static CompletableFuture<List<String>> fetchTopBits(int amount) {
        String url = "https://bomboapi.frandl938.workers.dev/bi";
        Bomboaddons.logApiRequest(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Upgrade-Insecure-Requests", "1")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    List<String> results = new ArrayList<>();
                    if (BomboConfig.get().apiDebug) {
                        results.add("§b[Debug] API: " + url);
                    }
                    if (response.statusCode() == 200) {
                        try {
                            JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
                            List<BitItem> items = new ArrayList<>();
                            
                            for (JsonElement element : jsonArray) {
                                JsonObject obj = element.getAsJsonObject();
                                if (obj.has("name") && obj.has("profit_per_bit")) {
                                    String name = obj.get("name").getAsString();
                                    double profit = obj.get("profit_per_bit").getAsDouble();
                                    items.add(new BitItem(name, profit));
                                }
                            }
                            
                            items.sort(Comparator.comparingDouble((BitItem b) -> b.profitPerBit).reversed());
                            
                            results.add("§6Profit Per Bit");
                            for (int i = 0; i < Math.min(amount, items.size()); i++) {
                                BitItem item = items.get(i);
                                results.add(String.format("§e%s: §a%d", item.formattedName, Math.round(item.profitPerBit)));
                            }
                            
                        } catch (Exception e) {
                            getLocalTopBits(amount, results, "Failed to parse Bits API response.");
                        }
                    } else {
                        getLocalTopBits(amount, results, "Bits API returned error code: " + response.statusCode());
                    }
                    return results;
                }).exceptionally(ex -> {
                    List<String> results = new ArrayList<>();
                    getLocalTopBits(amount, results, "Failed to connect to Bits API.");
                    return results;
                });
    }
}
