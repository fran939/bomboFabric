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

public class BitsManager {
    private static final HttpClient client = HttpClient.newBuilder().build();
    public static final java.util.Map<String, Integer> bitCostCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static long lastFetchTime = 0;

    public static CompletableFuture<Boolean> ensureLoaded() {
        long now = System.currentTimeMillis();
        if (!bitCostCache.isEmpty() && (now - lastFetchTime < 300000)) {
            return CompletableFuture.completedFuture(true);
        }

        String url = "https://bomboapi.frandl938.workers.dev/bi";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").timeout(Duration.ofSeconds(10)).GET().build();

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
                }).exceptionally(ex -> false);
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

    public static CompletableFuture<List<String>> fetchTopBits(int amount) {
        String url = "https://bomboapi.frandl938.workers.dev/bi";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
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
                            results.add("§cFailed to parse Bits API response.");
                        }
                    } else {
                        results.add("§cBits API returned error code: " + response.statusCode());
                    }
                    return results;
                }).exceptionally(ex -> {
                    List<String> results = new ArrayList<>();
                    results.add("§cFailed to connect to Bits API.");
                    return results;
                });
    }
}
