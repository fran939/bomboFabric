package me.bombo.bomboaddons.cheat.dungeons;

import me.bombo.bomboaddons.features.dungeons.*;

import me.bombo.bomboaddons.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.util.ApiHistory;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Master Mode F7 Bedrock chest expected value, used to decide whether a Kismet reroll is
 * value-positive.
 *
 * <p><b>Single source of truth:</b> the numbers the Discord {@code !kismet} command prints come from
 * {@code bomboapi/src/commands/kismet.js}. Rather than re-deriving them (and drifting), this class
 * first asks the very same service - {@code GET /mod/kismet/<player>} - which exposes the player's
 * shard levels, the live prices and the resulting {@code rerollSpotThreshold}. The local port of
 * that formula is only a fallback for offline / rate-limited situations, and it is deliberately
 * kept numerically identical to the JS (same breakpoints, same interpolation).
 */
public final class KismetEv {

    /** How long a successful server answer stays authoritative. */
    private static final long CACHE_TTL_MS = 10L * 60L * 1000L;

    public static final String BASE_URL = "https://api.bombo.dpdns.org";
    /** The chest cost side of the EV: 8M with Paul's Marauder perk, 10M otherwise. */
    private static final long CHEST_COST = 10_000_000L;

    private static volatile Snapshot cached = null;
    private static volatile long lastFetch = 0L;
    private static volatile boolean fetching = false;
    private static volatile String lastError = null;

    private KismetEv() {
    }

    /** Resolved EV inputs for the current player. */
    public static final class Snapshot {
        public String ign = "Unknown";
        public int effectiveBoxLevel = 0;
        public int catacombBoxLevel = 0;
        public int echoOfBoxesLevel = 0;
        public int pityLevel = 0;
        public boolean paulActive = false;
        public boolean marauderPerk = false;

        public long handlePrice;
        public double avgScrollPrice;
        public long claymorePrice;
        public long masterStar5Price;
        public long kismetPrice;
        public long chestCost = CHEST_COST;

        public double handleChance;
        public double scrollChance;
        public double claymoreChance;
        public double masterStarChance;

        /** Chest value below which rerolling with a Kismet Feather is EV-positive. */
        public long rerollSpotThreshold;
        public long coinsPerRunNoReroll;
        public long coinsPerHourNoReroll;
        public long coinsPerRunWithReroll;
        public long coinsPerHourWithReroll;

        /** "bomboapi" when the values came from the server, "local" for the offline port. */
        public String source = "local";
    }

    /** Cached snapshot, or null when nothing has been resolved yet. */
    public static Snapshot peek() {
        return cached;
    }

    public static String getLastError() {
        return lastError;
    }

    /**
     * Refreshes from the server in the background when the cache has expired. Safe to call every
     * tick - it no-ops while a fetch is in flight.
     */
    public static void refreshIfStale(String ign) {
        if (ign == null || ign.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - lastFetch < CACHE_TTL_MS) return;
        if (fetching) return;
        lastFetch = now;
        fetching = true;
        final String target = ign;
        CompletableFuture.runAsync(() -> {
            try {
                Snapshot fetched = fetchFromServer(target);
                if (fetched != null) {
                    cached = fetched;
                    lastError = null;
                } else {
                    lastError = "no EV data returned";
                }
            } catch (Throwable t) {
                lastError = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            } finally {
                fetching = false;
            }
        });
    }

    /** Blocking fetch of {@code /mod/kismet/<ign>}; returns null on any failure. */
    public static Snapshot fetchFromServer(String ign) {
        String url = BASE_URL + "/mod/kismet/" + ign;
        long started = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(new URI(url).toString()).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "BomboAddons");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            int code = conn.getResponseCode();
            ApiHistory.http("GET", url, code, System.currentTimeMillis() - started);
            if (code != 200) {
                // Fall back to the command alias the Discord bot itself uses.
                return fetchFromCommandAlias(ign);
            }
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return parse(ign, json);
            }
        } catch (Throwable t) {
            ApiHistory.http("GET", url, 0, System.currentTimeMillis() - started);
            return null;
        }
    }

    private static Snapshot fetchFromCommandAlias(String ign) {
        String url = BASE_URL + "/command/kismet/" + ign;
        long started = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(new URI(url).toString()).openConnection();
            conn.setRequestProperty("User-Agent", "BomboAddons");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            int code = conn.getResponseCode();
            ApiHistory.http("GET", url, code, System.currentTimeMillis() - started);
            if (code != 200) return null;
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject ev = json.has("ev_data") && json.get("ev_data").isJsonObject() ? json.getAsJsonObject("ev_data") : json;
                return parse(ign, ev, json);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static Snapshot parse(String ign, JsonObject ev) {
        return parse(ign, ev, ev);
    }

    private static Snapshot parse(String ign, JsonObject ev, JsonObject meta) {
        Snapshot snap = new Snapshot();
        snap.ign = ign;
        snap.source = "bomboapi";
        snap.effectiveBoxLevel = optInt(meta, "effective_box_level", 0);
        snap.paulActive = meta.has("paul_active") && meta.get("paul_active").isJsonPrimitive() && meta.get("paul_active").getAsBoolean();
        snap.marauderPerk = meta.has("marauder_perk") && meta.get("marauder_perk").isJsonPrimitive() && meta.get("marauder_perk").getAsBoolean();

        snap.handlePrice = optLong(ev, "handlePrice", 413_000_000L);
        snap.avgScrollPrice = optDouble(ev, "avgScrollPrice", 172_000_000.0D);
        snap.claymorePrice = optLong(ev, "claymorePrice", 168_000_000L);
        snap.masterStar5Price = optLong(ev, "masterStar5Price", 109_000_000L);
        snap.kismetPrice = optLong(ev, "kismetPrice", 1_400_000L);
        snap.chestCost = optLong(ev, "chestCost", CHEST_COST);
        snap.handleChance = optDouble(ev, "handleChance", 0.002002D);
        snap.rerollSpotThreshold = optLong(ev, "rerollSpotThreshold", 926_337L);
        snap.coinsPerRunNoReroll = optLong(ev, "coinsPerRunNoReroll", 3_602_129L);
        snap.coinsPerHourNoReroll = optLong(ev, "coinsPerHourNoReroll", 30_875_389L);
        snap.coinsPerRunWithReroll = optLong(ev, "coinsPerRunWithReroll", 4_435_832L);
        snap.coinsPerHourWithReroll = optLong(ev, "coinsPerHourWithReroll", 38_021_418L);
        return snap;
    }

    /**
     * Offline port of {@code calculateM7BedrockEV} from {@code bomboapi/src/commands/kismet.js}.
     * Used when the API is unreachable, so the reroll decision still follows the same curve.
     */
    public static Snapshot calculateLocal(int effectiveBoxLevel, boolean marauderPerk) {
        int box = Math.max(0, Math.min(13, effectiveBoxLevel));
        Snapshot snap = new Snapshot();
        snap.source = "local";
        snap.effectiveBoxLevel = box;
        snap.marauderPerk = marauderPerk;
        snap.chestCost = marauderPerk ? 8_000_000L : CHEST_COST;

        snap.handlePrice = priceOr("NECRON_HANDLE", 413_000_000L);
        double witherShield = priceOr("WITHER_SHIELD_SCROLL", 172_679_996L);
        double implosion = priceOr("IMPLOSION_SCROLL", 173_881_871L);
        double shadowWarp = priceOr("SHADOW_WARP_SCROLL", 172_174_604L);
        snap.avgScrollPrice = (witherShield + implosion + shadowWarp) / 3.0D;
        snap.claymorePrice = priceOr("DARK_CLAYMORE", 168_000_000L);
        snap.masterStar5Price = priceOr("FIFTH_MASTER_STAR", 108_999_995L);
        snap.kismetPrice = priceOr("KISMET_FEATHER", 1_469_891L);

        // Drop-rate scaling: Box 0 -> 7 -> 13, interpolated exactly like the JS breakpoints.
        snap.handleChance = scale(box, 0.001864D, 0.002002D, 0.002003D);
        snap.scrollChance = scale(box, 0.00245D, 0.00267D, 0.002781D);
        snap.claymoreChance = scale(box, 0.00105D, 0.00113D, 0.001181D);
        snap.masterStarChance = scale(box, 0.0035D, 0.0039D, 0.004185D);

        // Dynamic reroll spot: 752,403 (Box 0) -> 926,337 (Box 7) -> 1,244,993 (Box 13).
        double threshold;
        double runNo;
        double hourNo;
        double runWith;
        double hourWith;
        if (box >= 13) {
            threshold = 1_244_993.0D;
            runNo = 4_043_919.0D;
            hourNo = 34_662_166.0D;
            runWith = 5_264_012.0D;
            hourWith = 45_120_106.0D;
        } else if (box >= 7) {
            double ratio = (box - 7) / 6.0D;
            threshold = 926_337.0D + ratio * (1_244_993.0D - 926_337.0D);
            runNo = 3_602_129.0D + ratio * (4_043_919.0D - 3_602_129.0D);
            hourNo = 30_875_389.0D + ratio * (34_662_166.0D - 30_875_389.0D);
            runWith = 4_435_832.0D + ratio * (5_264_012.0D - 4_435_832.0D);
            hourWith = 38_021_418.0D + ratio * (45_120_106.0D - 38_021_418.0D);
        } else {
            double ratio = box / 7.0D;
            threshold = 752_403.0D + ratio * (926_337.0D - 752_403.0D);
            runNo = 3_370_174.0D + ratio * (3_602_129.0D - 3_370_174.0D);
            hourNo = 28_887_204.0D + ratio * (30_875_389.0D - 28_887_204.0D);
            runWith = 4_107_529.0D + ratio * (4_435_832.0D - 4_107_529.0D);
            hourWith = 35_207_390.0D + ratio * (38_021_418.0D - 35_207_390.0D);
        }

        if (marauderPerk) {
            runNo += 2_000_000.0D;
            hourNo += 2_000_000.0D * 8.57D;
            runWith += 2_000_000.0D;
            hourWith += 2_000_000.0D * 8.57D;
        }

        snap.rerollSpotThreshold = Math.round(threshold);
        snap.coinsPerRunNoReroll = Math.round(runNo);
        snap.coinsPerHourNoReroll = Math.round(hourNo);
        snap.coinsPerRunWithReroll = Math.round(runWith);
        snap.coinsPerHourWithReroll = Math.round(hourWith);
        return snap;
    }

    /** Linear interpolation across the Box 0 / Box 7 / Box 13 breakpoints. */
    private static double scale(int box, double at0, double at7, double at13) {
        if (box >= 13) return at13;
        if (box >= 7) return at7 + ((box - 7) / 6.0D) * (at13 - at7);
        return at0 + (box / 7.0D) * (at7 - at0);
    }

    private static long priceOr(String id, long fallback) {
        try {
            long price = LowestBinManager.getCachedPrice(id);
            return price > 0L ? price : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * The chest value below which a Kismet reroll pays for itself. Prefers the live server EV and
     * falls back to the local port at the currently known box level.
     */
    public static long getRerollSpotThreshold(String ign, int fallbackBoxLevel) {
        Snapshot snap = cached;
        if (snap != null) {
            return snap.rerollSpotThreshold;
        }
        return calculateLocal(fallbackBoxLevel, false).rerollSpotThreshold;
    }

    /** Human readable one-liner for {@code /b ac kismet} and the reroll chat line. */
    public static String describe() {
        Snapshot snap = cached;
        if (snap == null) {
            return "§7Kismet EV: §cnot loaded yet §7(run §e/b ac ev§7 to fetch)";
        }
        return "§7Kismet EV §8(" + snap.source + "§8) §7- box level §b" + snap.effectiveBoxLevel
                + " §7| threshold §e" + LowestBinManager.formatPrice(snap.rerollSpotThreshold)
                + " §7| feather §e" + LowestBinManager.formatPrice(snap.kismetPrice)
                + " §7| handle §e" + String.format(java.util.Locale.US, "%.4f%%", snap.handleChance * 100.0D)
                + (snap.marauderPerk ? " §7| §bPaul Marauder" : "");
    }

    private static int optInt(JsonObject json, String key, int fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static long optLong(JsonObject json, String key, long fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsLong() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static double optDouble(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
