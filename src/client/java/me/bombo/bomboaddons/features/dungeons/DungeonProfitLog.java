package me.bombo.bomboaddons.features.dungeons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.bombo.bomboaddons.AutoCroesus;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.util.ApiHistory;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Itemized dungeon loot ledger.
 *
 * <p>Every chest that is actually opened - by hand or by Auto Croesus - is recorded here with its
 * full contents, the floor it came from (parsed from the container title: "Master Mode The
 * Catacombs Floor VII" becomes {@code M7}), how long the run took and the resulting net profit.
 *
 * <p>Records are kept locally in {@code config/bomboaddons/dungeon_profit_runs.json} and mirrored to
 * the backend through {@code POST /api/v1/profits}, so {@code /b profit} and the web profile page
 * agree. Sync is best-effort: an offline client keeps the runs and retries later.
 */
public final class DungeonProfitLog {

    /** How many runs to keep on disk before the oldest are dropped. */
    private static final int MAX_RUNS = 500;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bomboaddons/dungeon_profit_runs.json").toFile();

    private static final List<RunRecord> runs = new ArrayList<>();
    private static int syncedCount = 0;
    private static boolean loaded = false;

    /** Wall clock start of the current run, used for the duration field. */
    private static volatile long currentRunStartedAt = 0L;
    private static volatile String currentRunFloor = null;

    private DungeonProfitLog() {
    }

    /** One item line inside a run. */
    public static final class ItemLine {
        public String name = "";
        public String itemId = "";
        public long quantity = 1L;
        public long unitPrice = 0L;
        public long totalValue = 0L;

        public ItemLine() {
        }

        public ItemLine(String name, String itemId, long quantity, long unitPrice, long totalValue) {
            this.name = name;
            this.itemId = itemId;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalValue = totalValue;
        }
    }

    /** A single chest opening. */
    public static final class RunRecord {
        public long timestamp = System.currentTimeMillis();
        public String floor = "Unknown";
        public String chest = "";
        public boolean kuudra = false;
        public String kuudraTier = null;
        public long cost = 0L;
        public long contentsValue = 0L;
        public long netProfit = 0L;
        public boolean kismetUsed = false;
        public long durationMs = 0L;
        public String source = "MANUAL";
        public boolean synced = false;
        public List<ItemLine> items = new ArrayList<>();

        public String type() {
            return kuudra ? "kuudra" : "dungeons";
        }
    }

    // -------------------------------------------------------------------------------------------
    // Run timing
    // -------------------------------------------------------------------------------------------

    /** Called when a new run is detected (boss phase entered) so duration can be measured. */
    public static void noteRunStart(String floor) {
        currentRunStartedAt = System.currentTimeMillis();
        currentRunFloor = floor;
    }

    public static void clearRunStart() {
        currentRunStartedAt = 0L;
        currentRunFloor = null;
    }

    public static long getElapsedRunMs() {
        long started = currentRunStartedAt;
        return started > 0L ? Math.max(0L, System.currentTimeMillis() - started) : 0L;
    }

    // -------------------------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------------------------

    public static synchronized void record(String floor, String chestName, long cost, long contentsValue,
                                           long netProfit, boolean kismetUsed, List<ItemLine> items,
                                           String source) {
        record(floor, chestName, cost, contentsValue, netProfit, kismetUsed, items, source, false, null);
    }

    public static synchronized void record(String floor, String chestName, long cost, long contentsValue,
                                           long netProfit, boolean kismetUsed, List<ItemLine> items,
                                           String source, boolean kuudra, String kuudraTier) {
        ensureLoaded();
        RunRecord rec = new RunRecord();
        rec.floor = floor == null || floor.isBlank() ? "Unknown" : floor;
        rec.chest = chestName != null ? chestName : "";
        rec.cost = cost;
        rec.contentsValue = contentsValue;
        rec.netProfit = netProfit;
        rec.kismetUsed = kismetUsed;
        rec.durationMs = getElapsedRunMs();
        rec.source = source != null ? source : "MANUAL";
        rec.kuudra = kuudra;
        rec.kuudraTier = kuudraTier;
        if (items != null) {
            rec.items.addAll(items);
        }

        runs.add(rec);
        while (runs.size() > MAX_RUNS) {
            RunRecord removed = runs.remove(0);
            if (removed.synced && syncedCount > 0) {
                syncedCount--;
            }
        }
        save();
        clearRunStart();
        syncPending(null);
    }

    public static synchronized List<RunRecord> getRuns() {
        ensureLoaded();
        return new ArrayList<>(runs);
    }

    public static synchronized int getSyncedCount() {
        ensureLoaded();
        return syncedCount;
    }

    public static synchronized int getUnsyncedCount() {
        ensureLoaded();
        int count = 0;
        for (RunRecord run : runs) {
            if (!run.synced) count++;
        }
        return count;
    }

    /** Local aggregates, so {@code /b profit} works with no network at all. */
    public static long[] localTotals() {
        ensureLoaded();
        long profit = 0L;
        int count = 0;
        int kuudra = 0;
        for (RunRecord run : runs) {
            profit += run.netProfit;
            count++;
            if (run.kuudra) kuudra++;
        }
        return new long[]{profit, count, kuudra};
    }

    // -------------------------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------------------------

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (FILE.exists()) {
                Type type = new TypeToken<List<RunRecord>>() {
                }.getType();
                try (Reader reader = Files.newBufferedReader(FILE.toPath(), StandardCharsets.UTF_8)) {
                    List<RunRecord> loadedRuns = GSON.fromJson(reader, type);
                    if (loadedRuns != null) {
                        runs.clear();
                        runs.addAll(loadedRuns);
                        syncedCount = 0;
                        for (RunRecord run : runs) {
                            if (run.synced) syncedCount++;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (Writer writer = Files.newBufferedWriter(FILE.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(runs, writer);
            }
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------------------------------------
    // Server sync
    // -------------------------------------------------------------------------------------------

    /** POSTs every unsynced run to /api/v1/profits. Never blocks the game thread. */
    public static void syncPending(net.minecraft.client.multiplayer.ClientPacketListener ignored) {
        ensureLoaded();
        List<RunRecord> pending = new ArrayList<>();
        synchronized (DungeonProfitLog.class) {
            for (RunRecord run : runs) {
                if (!run.synced) pending.add(run);
            }
        }
        if (pending.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                String url = BomboApiUrl.getApiUrl("/api/v1/profits");
                JsonObject payload = new JsonObject();
                Minecraft mc = Minecraft.getInstance();
                payload.addProperty("ign", mc.getUser() != null ? mc.getUser().getName() : "unknown");
                payload.addProperty("modVersion", me.bombo.bomboaddons.BomboaddonsClient.MOD_VERSION);
                JsonArray array = new JsonArray();
                for (RunRecord run : pending) {
                    array.add(toJson(run));
                }
                payload.add("runs", array);

                long started = System.currentTimeMillis();
                HttpURLConnection conn = (HttpURLConnection) new URL(new URI(url).toString()).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.BomboaddonsClient.MOD_VERSION);
                String token = me.bombo.bomboaddons.eggfinder.EggAuth.getBomboToken();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                } else {
                    // The upload endpoint is token-gated, so a missing token is the one failure that
                    // looks like "nothing happened". Record it so /b apihistory explains why.
                    ApiHistory.note("POST", url, 401, "no Bombo token - run /b egg auth to authenticate");
                }
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                try (var os = conn.getOutputStream()) {
                    os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
                int code = conn.getResponseCode();
                ApiHistory.http("POST", url, code, System.currentTimeMillis() - started);
                if (code >= 200 && code < 300) {
                    synchronized (DungeonProfitLog.class) {
                        for (RunRecord run : pending) {
                            run.synced = true;
                            syncedCount++;
                        }
                        save();
                    }
                }
            } catch (Throwable t) {
                ApiHistory.http("POST", BomboApiUrl.getApiUrl("/api/v1/profits"), 0, 0L);
            }
        });
    }

    private static JsonObject toJson(RunRecord run) {
        JsonObject obj = new JsonObject();
        obj.addProperty("timestamp", run.timestamp);
        obj.addProperty("floor", run.floor);
        obj.addProperty("chest", run.chest);
        obj.addProperty("type", run.type());
        if (run.kuudraTier != null) obj.addProperty("tier", run.kuudraTier);
        obj.addProperty("cost", run.cost);
        obj.addProperty("contentsValue", run.contentsValue);
        obj.addProperty("profit", run.netProfit);
        obj.addProperty("kismetUsed", run.kismetUsed);
        obj.addProperty("durationMs", run.durationMs);
        obj.addProperty("source", run.source);
        JsonArray items = new JsonArray();
        for (ItemLine line : run.items) {
            JsonObject item = new JsonObject();
            item.addProperty("name", line.name);
            item.addProperty("id", line.itemId);
            item.addProperty("quantity", line.quantity);
            item.addProperty("unitPrice", line.unitPrice);
            item.addProperty("totalValue", line.totalValue);
            items.add(item);
        }
        obj.add("items", items);
        return obj;
    }

    // -------------------------------------------------------------------------------------------
    // Remote lookups for /b profit <user>
    // -------------------------------------------------------------------------------------------

    /**
     * Fetches another player's profit summary from {@code /profit/:user[/:type]} and echoes it to
     * the given consumer (invoked off-thread - callers must hop back to the client thread).
     */
    public static void fetchUserProfit(String user, String type, java.util.function.Consumer<String> whenReady) {
        CompletableFuture.runAsync(() -> {
            String path = "/profit/" + user + (type != null && !type.isBlank() ? "/" + type : "");
            String url = BomboApiUrl.getApiUrl(path);
            String result;
            long started = System.currentTimeMillis();
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(new URI(url).toString()).openConnection();
                conn.setRequestProperty("User-Agent", "BomboAddons/" + me.bombo.bomboaddons.BomboaddonsClient.MOD_VERSION);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                int code = conn.getResponseCode();
                ApiHistory.http("GET", url, code, System.currentTimeMillis() - started);
                if (code == 200) {
                    try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        result = formatRemote(user, json);
                    }
                } else if (code == 404) {
                    result = "§cNo profit data recorded for §e" + user + "§c yet.";
                } else {
                    result = "§cProfit lookup failed (HTTP " + code + ").";
                }
            } catch (Throwable t) {
                ApiHistory.http("GET", url, 0, System.currentTimeMillis() - started);
                result = "§cCould not reach the profit service: " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            }
            if (whenReady != null) whenReady.accept(result);
        });
    }

    private static String formatRemote(String user, JsonObject json) {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append("§8--- §b").append(user).append(" §8---\n");
        long total = json.has("totalProfit") ? json.get("totalProfit").getAsLong() : 0L;
        long runs = json.has("totalRuns") ? json.get("totalRuns").getAsLong() : 0L;
        sb.append("§7Total Profit: ").append(total >= 0 ? "§a+" : "§c").append(LowestBinManager.formatPrice(total)).append("\n");
        sb.append("§7Runs: §e").append(runs).append("\n");
        if (json.has("bestFloor") && json.get("bestFloor").isJsonPrimitive()) {
            sb.append("§7Best Floor: §b").append(json.get("bestFloor").getAsString()).append("\n");
        }
        if (json.has("floors") && json.get("floors").isJsonObject()) {
            JsonObject floors = json.getAsJsonObject("floors");
            List<String> keys = new ArrayList<>(floors.keySet());
            keys.sort((a, b) -> Integer.compare(AutoCroesus.floorSortKey(a), AutoCroesus.floorSortKey(b)));
            for (String key : keys) {
                JsonObject floor = floors.getAsJsonObject(key);
                long profit = floor.has("profit") ? floor.get("profit").getAsLong() : 0L;
                long floorRuns = floor.has("runs") ? floor.get("runs").getAsLong() : 0L;
                sb.append("§8- §b").append(key).append("§7: ").append(profit >= 0 ? "§a+" : "§c")
                        .append(LowestBinManager.formatPrice(profit)).append(" §8(").append(floorRuns).append(" runs)\n");
            }
        }
        return sb.toString().trim();
    }

    /** Sends a chat message from whatever thread we are on. */
    public static void sendToChat(String message) {
        if (message == null || message.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal(message));
            }
        });
    }
}
