package me.bombo.bomboaddons.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class PriceHistoryManager {

    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static class PricePoint {
        public final long timestamp;
        public final double price;

        public PricePoint(long timestamp, double price) {
            this.timestamp = timestamp;
            this.price = price;
        }
    }

    public static class ItemHistoryData {
        public final String itemTag;
        public final List<PricePoint> points = new ArrayList<>();
        public double avg7d = -1;
        public double avg30d = -1;
        public double minPrice = 0;
        public double maxPrice = 0;
        public long lastFetched = 0;

        public ItemHistoryData(String itemTag) {
            this.itemTag = itemTag;
        }

        public synchronized void recalculate() {
            if (points.isEmpty()) return;
            long now = System.currentTimeMillis();
            long sevenDaysAgo = now - (7L * 24L * 60L * 60L * 1000L);
            long thirtyDaysAgo = now - (30L * 24L * 60L * 60L * 1000L);

            double sum7 = 0;
            int count7 = 0;
            double sum30 = 0;
            int count30 = 0;
            double min = Double.MAX_VALUE;
            double max = 0;

            for (PricePoint p : points) {
                if (p.price > 0) {
                    if (p.price < min) min = p.price;
                    if (p.price > max) max = p.price;
                }
                if (p.timestamp >= sevenDaysAgo && p.price > 0) {
                    sum7 += p.price;
                    count7++;
                }
                if (p.timestamp >= thirtyDaysAgo && p.price > 0) {
                    sum30 += p.price;
                    count30++;
                }
            }

            this.avg7d = count7 > 0 ? (sum7 / count7) : -1;
            this.avg30d = count30 > 0 ? (sum30 / count30) : -1;
            this.minPrice = min == Double.MAX_VALUE ? 0 : min;
            this.maxPrice = max;
        }
    }

    public static boolean isNonMarketItem(String tag) {
        if (tag == null || tag.trim().isEmpty()) return true;
        String u = tag.toUpperCase().trim();
        return u.equals("SKYBLOCK_MENU") || u.startsWith("BARRIER") || u.startsWith("BEDROCK")
                || u.equals("AIR") || u.contains("GLASS_PANE") || u.equals("UNKNOWN")
                || u.startsWith("QUIVER_ARROW");
    }

    private static final Map<String, ItemHistoryData> historyCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> negativeCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 600_000L; // 10 minutes
    private static final long NEGATIVE_TTL_MS = 300_000L; // 5 minutes
    private static volatile long rateLimitCooldownUntilMs = 0L;

    private static class FetchTask {
        final String tag;
        final String cleanRange;
        final String pathRange;
        final String cacheKey;
        final CompletableFuture<ItemHistoryData> future;

        FetchTask(String tag, String cleanRange, String pathRange, String cacheKey, CompletableFuture<ItemHistoryData> future) {
            this.tag = tag;
            this.cleanRange = cleanRange;
            this.pathRange = pathRange;
            this.cacheKey = cacheKey;
            this.future = future;
        }
    }

    private static final java.util.concurrent.BlockingQueue<FetchTask> taskQueue = new java.util.concurrent.LinkedBlockingQueue<>(200);
    private static final Set<String> queuedKeys = Collections.synchronizedSet(new HashSet<>());

    static {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    FetchTask task = taskQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                    if (task != null) {
                        processTask(task);
                        // Delay at least 1500ms between requests to never exceed 40 req/min (< 60 req/min limit)
                        Thread.sleep(1500L);
                    }
                } catch (InterruptedException ignored) {
                    break;
                } catch (Throwable t) {
                    try { Thread.sleep(1000L); } catch (Throwable ignored) {}
                }
            }
        }, "Bombo-PriceHistory-Queue");
        worker.setDaemon(true);
        worker.start();
    }

    public static ItemHistoryData getCachedHistory(String itemTag) {
        return getCachedHistory(itemTag, "30d");
    }

    public static ItemHistoryData getCachedHistory(String itemTag, String range) {
        if (itemTag == null || itemTag.isEmpty() || isNonMarketItem(itemTag)) return null;
        String r = range != null ? range.toLowerCase() : "30d";
        String key = itemTag.toUpperCase() + "_" + r;
        long now = System.currentTimeMillis();
        Long negTime = negativeCache.get(key);
        if (negTime != null && (now - negTime < NEGATIVE_TTL_MS)) {
            return new ItemHistoryData(itemTag.toUpperCase());
        }
        ItemHistoryData data = historyCache.get(key);
        if (data != null && (now - data.lastFetched < CACHE_TTL_MS)) {
            return data;
        }
        return null;
    }

    public static CompletableFuture<ItemHistoryData> fetchHistory(String itemTag, String range) {
        if (itemTag == null || itemTag.isEmpty() || isNonMarketItem(itemTag)) {
            return CompletableFuture.completedFuture(null);
        }
        String tag = itemTag.toUpperCase();
        String tempCleanRange = "30d";
        String tempPathRange = "month";
        if ("1d".equalsIgnoreCase(range) || "day".equalsIgnoreCase(range)) {
            tempCleanRange = "1d";
            tempPathRange = "day";
        } else if ("7d".equalsIgnoreCase(range) || "week".equalsIgnoreCase(range)) {
            tempCleanRange = "7d";
            tempPathRange = "week";
        } else if ("30d".equalsIgnoreCase(range) || "month".equalsIgnoreCase(range)) {
            tempCleanRange = "30d";
            tempPathRange = "month";
        } else if ("all".equalsIgnoreCase(range) || "full".equalsIgnoreCase(range)) {
            tempCleanRange = "all";
            tempPathRange = "full";
        }
        final String cleanRange = tempCleanRange;
        final String pathRange = tempPathRange;
        String cacheKey = tag + "_" + cleanRange;

        long now = System.currentTimeMillis();
        ItemHistoryData existing = historyCache.get(cacheKey);
        if (existing != null && (now - existing.lastFetched < CACHE_TTL_MS)) {
            return CompletableFuture.completedFuture(existing);
        }
        Long neg = negativeCache.get(cacheKey);
        if (neg != null && (now - neg < NEGATIVE_TTL_MS)) {
            return CompletableFuture.completedFuture(new ItemHistoryData(tag));
        }

        if (queuedKeys.add(cacheKey)) {
            CompletableFuture<ItemHistoryData> future = new CompletableFuture<>();
            if (!taskQueue.offer(new FetchTask(tag, cleanRange, pathRange, cacheKey, future))) {
                queuedKeys.remove(cacheKey);
                return CompletableFuture.completedFuture(existing);
            }
            return future;
        }
        return CompletableFuture.completedFuture(existing);
    }

    private static void processTask(FetchTask task) {
        queuedKeys.remove(task.cacheKey);
        long now = System.currentTimeMillis();
        ItemHistoryData existing = historyCache.get(task.cacheKey);
        if (existing != null && (now - existing.lastFetched < CACHE_TTL_MS)) {
            task.future.complete(existing);
            return;
        }
        Long neg = negativeCache.get(task.cacheKey);
        if (neg != null && (now - neg < NEGATIVE_TTL_MS)) {
            task.future.complete(new ItemHistoryData(task.tag));
            return;
        }

        String path = "/api/item/price/" + task.tag + "/history/" + task.pathRange;
        String primaryUrl = BomboApiUrl.getApiUrl(path);
        String backupUrl = "https://bomboapi.frandl938.workers.dev" + path;
        String coflRange = task.pathRange.equals("full") ? "all" : task.pathRange;
        String coflUrl = "https://sky.coflnet.com/api/item/price/" + task.tag + "/history/" + coflRange;

        CompletableFuture<JsonObject> primaryFuture;
        if (now < rateLimitCooldownUntilMs) {
            primaryFuture = CompletableFuture.completedFuture(null);
        } else {
            primaryFuture = fetchUrl(primaryUrl);
        }

        primaryFuture.thenCompose(json -> {
            if (json != null && hasValidPoints(json)) return CompletableFuture.completedFuture(json);
            return fetchUrl(backupUrl);
        }).thenCompose(json -> {
            if (json != null && hasValidPoints(json)) return CompletableFuture.completedFuture(json);
            return fetchUrl(coflUrl);
        }).thenAccept(json -> {
            if (json != null) {
                ItemHistoryData data = parseHistoryJson(task.tag, json);
                if (data != null && !data.points.isEmpty()) {
                    data.lastFetched = System.currentTimeMillis();
                    historyCache.put(task.cacheKey, data);
                    task.future.complete(data);
                    return;
                }
            }
            negativeCache.put(task.cacheKey, System.currentTimeMillis());
            ItemHistoryData emptyData = new ItemHistoryData(task.tag);
            task.future.complete(emptyData);
        }).exceptionally(ex -> {
            negativeCache.put(task.cacheKey, System.currentTimeMillis());
            task.future.complete(new ItemHistoryData(task.tag));
            return null;
        });
    }

    private static boolean hasValidPoints(JsonObject json) {
        if (json == null) return false;
        if (json.has("history") && json.get("history").isJsonArray() && json.getAsJsonArray("history").size() > 0) return true;
        if (json.has("data") && json.get("data").isJsonArray() && json.getAsJsonArray("data").size() > 0) return true;
        if (json.has("prices") && json.get("prices").isJsonArray() && json.getAsJsonArray("prices").size() > 0) return true;
        return false;
    }

    private static CompletableFuture<JsonObject> fetchUrl(String urlStr) {
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                    .timeout(Duration.ofSeconds(6))
                    .GET();
            if (urlStr.contains("bombo.dpdns.org")) {
                BomboApiUrl.attachApiKey(reqBuilder);
            }

            return client.sendAsync(reqBuilder.build(), BodyHandlers.ofString()).thenApply(resp -> {
                if (resp.statusCode() == 200) {
                    try {
                        JsonElement elem = JsonParser.parseString(resp.body());
                        if (elem.isJsonObject()) {
                            return elem.getAsJsonObject();
                        } else if (elem.isJsonArray()) {
                            JsonObject wrap = new JsonObject();
                            wrap.add("history", elem.getAsJsonArray());
                            return wrap;
                        }
                    } catch (Throwable ignored) {}
                } else if (resp.statusCode() == 429) {
                    rateLimitCooldownUntilMs = System.currentTimeMillis() + 60_000L;
                    if (BomboConfig.get().apiDebug) {
                        System.out.println("[BomboAddons] HTTP 429 Rate Limit on " + urlStr + ". Backing off for 60s.");
                    }
                }
                return null;
            }).exceptionally(e -> null);
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static ItemHistoryData parseHistoryJson(String tag, JsonObject json) {
        ItemHistoryData data = new ItemHistoryData(tag);
        JsonArray arr = null;
        if (json.has("history") && json.get("history").isJsonArray()) {
            arr = json.getAsJsonArray("history");
        } else if (json.has("data") && json.get("data").isJsonArray()) {
            arr = json.getAsJsonArray("data");
        } else if (json.has("prices") && json.get("prices").isJsonArray()) {
            arr = json.getAsJsonArray("prices");
        }

        if (arr != null) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    long time = 0;
                    double price = 0;
                    if (o.has("time")) {
                        JsonElement te = o.get("time");
                        if (te.isJsonPrimitive() && te.getAsJsonPrimitive().isNumber()) {
                            time = te.getAsLong();
                        } else if (te.isJsonPrimitive() && te.getAsJsonPrimitive().isString()) {
                            try {
                                time = java.time.LocalDateTime.parse(te.getAsString()).toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
                            } catch (Exception ignored) {
                                try {
                                    time = java.time.Instant.parse(te.getAsString()).toEpochMilli();
                                } catch (Exception ignored2) {}
                            }
                        }
                    } else if (o.has("timestamp")) {
                        JsonElement te = o.get("timestamp");
                        if (te.isJsonPrimitive() && te.getAsJsonPrimitive().isNumber()) {
                            time = te.getAsLong();
                        } else if (te.isJsonPrimitive() && te.getAsJsonPrimitive().isString()) {
                            try {
                                time = java.time.Instant.parse(te.getAsString()).toEpochMilli();
                            } catch (Exception ignored) {}
                        }
                    } else if (o.has("t")) {
                        time = o.get("t").getAsLong();
                    }

                    if (time > 0 && time < 10000000000L) {
                        time *= 1000L;
                    }

                    if (o.has("price")) price = o.get("price").getAsDouble();
                    else if (o.has("lowestBin")) price = o.get("lowestBin").getAsDouble();
                    else if (o.has("avg")) price = o.get("avg").getAsDouble();
                    else if (o.has("min")) price = o.get("min").getAsDouble();
                    else if (o.has("max")) price = o.get("max").getAsDouble();
                    else if (o.has("p")) price = o.get("p").getAsDouble();
                    else if (o.has("val")) price = o.get("val").getAsDouble();

                    if (time > 0 && price > 0) {
                        data.points.add(new PricePoint(time, price));
                    }
                }
            }
        }

        data.points.sort(Comparator.comparingLong(p -> p.timestamp));
        data.recalculate();
        return data;
    }
}
