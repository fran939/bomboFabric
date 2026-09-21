package me.bombo.bomboaddons.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
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
public class MayorManager {

    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public static final long SKYBLOCK_EPOCH_MS = 1560275700000L; // June 11, 2019, 17:55 UTC (Year 1 start)
    public static final long YEAR_DURATION_MS = 446400000L; // 372 SkyBlock days * 20 min = 5.1667 days (446,400,000 ms)

    public static class MayorPerk {
        public final String name;
        public final String description;

        public MayorPerk(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public static class MayorTerm {
        public final int year;
        public final String name;
        public final String key;
        public final List<MayorPerk> perks = new ArrayList<>();
        public final long startTimestamp;
        public final long endTimestamp;
        public final int color;

        public MayorTerm(int year, String name, String key, long startTimestamp, long endTimestamp, int color) {
            this.year = year;
            this.name = name != null ? name : "Unknown";
            this.key = key != null ? key : this.name.toLowerCase();
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
            this.color = color;
        }
    }

    private static final Map<Integer, MayorTerm> mayorCache = new ConcurrentHashMap<>();
    private static final Set<Integer> activeFetches = Collections.synchronizedSet(new HashSet<>());

    public static int getSkyblockYearForTimestamp(long timestamp) {
        if (timestamp < SKYBLOCK_EPOCH_MS) return 1;
        return 1 + (int) ((timestamp - SKYBLOCK_EPOCH_MS) / YEAR_DURATION_MS);
    }

    public static long getStartTimestampForYear(int year) {
        if (year <= 1) return SKYBLOCK_EPOCH_MS;
        return SKYBLOCK_EPOCH_MS + (long) (year - 1) * YEAR_DURATION_MS;
    }

    public static long getEndTimestampForYear(int year) {
        return getStartTimestampForYear(year) + YEAR_DURATION_MS;
    }

    public static int getMayorColor(String mayorName) {
        if (mayorName == null) return 0x226366F1;
        String m = mayorName.toLowerCase();
        if (m.contains("paul")) return 0x33EF4444;       // Red
        if (m.contains("marina")) return 0x3306B6D4;     // Cyan
        if (m.contains("diana")) return 0x3310B981;      // Emerald
        if (m.contains("aatrox")) return 0x33DC2626;     // Crimson
        if (m.contains("cole")) return 0x33F59E0B;       // Amber
        if (m.contains("diaz")) return 0x33EAB308;       // Gold
        if (m.contains("derpy")) return 0x33EC4899;      // Pink
        if (m.contains("jerry")) return 0x33F472B6;      // Light pink
        if (m.contains("scorpius")) return 0x338B5CF6;   // Violet
        if (m.contains("foxy")) return 0x33F97316;       // Orange
        if (m.contains("finnegan")) return 0x3322C55E;   // Green
        return 0x336366F1;                               // Indigo
    }

    public static int getMayorSolidColor(String mayorName) {
        if (mayorName == null) return 0xFF6366F1;
        String m = mayorName.toLowerCase();
        if (m.contains("paul")) return 0xFFEF4444;
        if (m.contains("marina")) return 0xFF06B6D4;
        if (m.contains("diana")) return 0xFF10B981;
        if (m.contains("aatrox")) return 0xFFDC2626;
        if (m.contains("cole")) return 0xFFF59E0B;
        if (m.contains("diaz")) return 0xFFEAB308;
        if (m.contains("derpy")) return 0xFFEC4899;
        if (m.contains("jerry")) return 0xFFF472B6;
        if (m.contains("scorpius")) return 0xFF8B5CF6;
        if (m.contains("foxy")) return 0xFFF97316;
        if (m.contains("finnegan")) return 0xFF22C55E;
        return 0xFF6366F1;
    }

    public static MayorTerm getCachedMayor(int year) {
        return mayorCache.get(year);
    }

    public static MayorTerm getMayorForTimestamp(long timestamp) {
        int year = getSkyblockYearForTimestamp(timestamp);
        MayorTerm term = mayorCache.get(year);
        if (term == null) {
            fetchMayorForYear(year);
            // Return placeholder until fetched
            long start = getStartTimestampForYear(year);
            long end = getEndTimestampForYear(year);
            return new MayorTerm(year, "Year " + year, "unknown", start, end, 0x226366F1);
        }
        return term;
    }

    public static List<MayorTerm> getMayorsForTimeRange(long startT, long endT) {
        List<MayorTerm> list = new ArrayList<>();
        int startYear = getSkyblockYearForTimestamp(startT);
        int endYear = getSkyblockYearForTimestamp(endT);
        if (endYear - startYear > 35) {
            return list;
        }

        for (int y = startYear; y <= endYear; y++) {
            MayorTerm m = mayorCache.get(y);
            if (m == null) {
                fetchMayorForYear(y);
                long sy = getStartTimestampForYear(y);
                long ey = getEndTimestampForYear(y);
                m = new MayorTerm(y, "Year " + y, "unknown", sy, ey, 0x226366F1);
            }
            list.add(m);
        }
        return list;
    }

    public static CompletableFuture<MayorTerm> fetchMayorForYear(int year) {
        if (year <= 0) return CompletableFuture.completedFuture(null);
        MayorTerm cached = mayorCache.get(year);
        if (cached != null && !"unknown".equals(cached.key)) {
            return CompletableFuture.completedFuture(cached);
        }

        if (!activeFetches.add(year)) {
            return CompletableFuture.completedFuture(cached);
        }

        String url = "https://sky.coflnet.com/api/mayor/" + year;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            return client.sendAsync(req, BodyHandlers.ofString()).thenApply(resp -> {
                activeFetches.remove(year);
                long start = getStartTimestampForYear(year);
                long end = getEndTimestampForYear(year);
                if (resp.statusCode() == 200) {
                    try {
                        JsonElement elem = JsonParser.parseString(resp.body());
                        if (elem.isJsonObject()) {
                            JsonObject obj = elem.getAsJsonObject();
                            String name = "Unknown";
                            String key = "unknown";
                            JsonObject mayorObj = null;

                            if (obj.has("winner") && obj.get("winner").isJsonObject()) {
                                mayorObj = obj.getAsJsonObject("winner");
                            } else if (obj.has("mayor") && obj.get("mayor").isJsonObject()) {
                                mayorObj = obj.getAsJsonObject("mayor");
                            }

                            if (mayorObj != null) {
                                if (mayorObj.has("name")) name = mayorObj.get("name").getAsString();
                                if (mayorObj.has("key")) key = mayorObj.get("key").getAsString();
                            } else if (obj.has("name")) {
                                name = obj.get("name").getAsString();
                                if (obj.has("key")) key = obj.get("key").getAsString();
                            }

                            if ("unknown".equalsIgnoreCase(key) && year == 515) {
                                name = "Finnegan";
                                key = "finnegan";
                            }

                            int color = getMayorColor(name);
                            MayorTerm term = new MayorTerm(year, name, key, start, end, color);

                            // Parse perks
                            JsonArray perksArr = null;
                            if (mayorObj != null && mayorObj.has("perks")) {
                                perksArr = mayorObj.getAsJsonArray("perks");
                            } else if (obj.has("perks")) {
                                perksArr = obj.getAsJsonArray("perks");
                            }
                            if (perksArr != null) {
                                for (JsonElement pe : perksArr) {
                                    if (pe.isJsonObject()) {
                                        JsonObject po = pe.getAsJsonObject();
                                        String pName = po.has("name") ? po.get("name").getAsString() : "";
                                        String pDesc = po.has("description") ? po.get("description").getAsString() : "";
                                        term.perks.add(new MayorPerk(pName, pDesc));
                                    }
                                }
                            }
                            if (term.perks.isEmpty() && "finnegan".equalsIgnoreCase(key)) {
                                term.perks.add(new MayorPerk("Farming Contests", "Jacob's Farming Contests happen more often."));
                                term.perks.add(new MayorPerk("GOAT", "Increases the chance of rare pests in Garden."));
                            }

                            mayorCache.put(year, term);
                            return term;
                        }
                    } catch (Throwable ignored) {}
                }
                String fallbackName = (year == 515) ? "Finnegan" : ("Year " + year);
                String fallbackKey = (year == 515) ? "finnegan" : "unknown";
                int fallbackColor = (year == 515) ? getMayorColor("Finnegan") : 0x226366F1;
                MayorTerm fallback = new MayorTerm(year, fallbackName, fallbackKey, start, end, fallbackColor);
                if (year == 515) {
                    fallback.perks.add(new MayorPerk("Farming Contests", "Jacob's Farming Contests happen more often."));
                }
                mayorCache.put(year, fallback);
                return fallback;
            }).exceptionally(ex -> {
                activeFetches.remove(year);
                long start = getStartTimestampForYear(year);
                long end = getEndTimestampForYear(year);
                String fallbackName = (year == 515) ? "Finnegan" : ("Year " + year);
                String fallbackKey = (year == 515) ? "finnegan" : "unknown";
                int fallbackColor = (year == 515) ? getMayorColor("Finnegan") : 0x226366F1;
                MayorTerm fallback = new MayorTerm(year, fallbackName, fallbackKey, start, end, fallbackColor);
                if (year == 515) {
                    fallback.perks.add(new MayorPerk("Farming Contests", "Jacob's Farming Contests happen more often."));
                }
                mayorCache.put(year, fallback);
                return fallback;
            });
        } catch (Throwable t) {
            activeFetches.remove(year);
            return CompletableFuture.completedFuture(null);
        }
    }
}
