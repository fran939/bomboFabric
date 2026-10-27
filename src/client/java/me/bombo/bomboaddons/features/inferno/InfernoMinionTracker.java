package me.bombo.bomboaddons.features.inferno;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class InfernoMinionTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final File DATA_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/inferno_tracker.json").toFile();

    // Valid Inferno Minion drop IDs
    public static final Set<String> TRACKED_DROPS = Set.of(
        "CRUDE_GABAGOOL",
        "VERY_CRUDE_GABAGOOL",
        "FUEL_GABAGOOL",
        "HEAVY_GABAGOOL",
        "CHILI_PEPPER",
        "ENCHANTED_CHILI_PEPPER",
        "INFERNO_VERTEX",
        "INFERNO_APEX",
        "REAPER_PEPPER",
        "GABAGOOL_THE_FISH"
    );

    // Drops that NEVER go to sacks and always enter player inventory directly
    public static final Set<String> NON_SACK_DROPS = Set.of(
        "VERY_CRUDE_GABAGOOL",
        "FUEL_GABAGOOL",
        "HEAVY_GABAGOOL",
        "INFERNO_VERTEX",
        "INFERNO_APEX",
        "REAPER_PEPPER",
        "GABAGOOL_THE_FISH"
    );

    public static final String HYPERGOLIC_FUEL_ID = "INFERNO_HYPERGOLIC_CRUDE_GABAGOOL";
    public static final Pattern SACKS_ADD_PATTERN = Pattern.compile("(?i)^\\+([\\d,]+)x?\\s+(.+?)(?:\\s*\\([^\\)]*\\))?$");

    // Data structures
    public static class MinionInfo {
        public int index = 1;
        public String uuid;
        public int entityId;
        public double posX, posY, posZ;
        public int x, y, z;
        public String tier = "Inferno Minion";
        public long totalDrops;
        public long totalFuels;
        public long lastActionTime;
        public String lastActionPlayer = "Player";
        public Map<String, Long> dropCounts = new ConcurrentHashMap<>();
    }

    public static class ActionEntry {
        public String type; // "CLAIM" or "FUEL"
        public String player;
        public long timestamp;
        public String minionUuid;
        public int minionEntityId;
        public String pos;
        public long cost;
        public long revenue;
        public Map<String, Integer> drops;
        public String fuelUuid;
    }

    public static class TrackerState {
        public String passkey = "default";
        public long totalRevenue = 0L;
        public long totalCost = 0L;
        public long netProfit = 0L;
        public Map<String, Long> drops = new ConcurrentHashMap<>();
        public Map<String, Long> fuels = new ConcurrentHashMap<>();
        public Set<String> seenFuelUuids = Collections.synchronizedSet(new HashSet<>());
        public List<ActionEntry> actions = Collections.synchronizedList(new ArrayList<>());
        public Map<String, MinionInfo> minions = new ConcurrentHashMap<>();
    }

    private static TrackerState state = new TrackerState();
    private static boolean loaded = false;

    // Runtime context
    private static Entity lastInteractedEntity = null;
    private static long lastInteractTime = 0L;
    private static String activeMinionUuid = null;
    private static int activeMinionEntityId = 0;
    private static BlockPos activeMinionPos = null;
    private static String activeMinionName = "Inferno Minion";
    public static int activeMinionIndex = 1;

    // 32 Inferno Minion Island Mapping (Row 1: #1-16 @ Z=49.5; Row 2: #17-32 @ Z=45.5)
    public static double getMinionTargetX(int index) {
        if (index >= 1 && index <= 16) {
            return 51.5 + (index - 1) * 2.0;
        } else if (index >= 17 && index <= 32) {
            return 81.5 - (index - 17) * 2.0;
        }
        return 51.5;
    }

    public static double getMinionTargetZ(int index) {
        if (index >= 1 && index <= 16) {
            return 49.5;
        } else if (index >= 17 && index <= 32) {
            return 45.5;
        }
        return 49.5;
    }

    public static int getMinionIndexForCoords(double x, double y, double z) {
        int bestIdx = -1;
        double bestDistSq = 20.0; // within ~4.4 blocks
        for (int i = 1; i <= 32; i++) {
            double tx = getMinionTargetX(i);
            double ty = 103.0;
            double tz = getMinionTargetZ(i);
            double dx = x - tx;
            double dy = y - ty;
            double dz = z - tz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    public static void ensureAllMinionsInitialized() {
        for (int i = 1; i <= 32; i++) {
            String key = "minion_" + i;
            final int idx = i;
            MinionInfo info = state.minions.computeIfAbsent(key, k -> {
                MinionInfo m = new MinionInfo();
                m.index = idx;
                m.uuid = key;
                m.tier = "Inferno Minion #" + idx;
                m.posX = getMinionTargetX(idx);
                m.posY = 103.0;
                m.posZ = getMinionTargetZ(idx);
                m.x = (int) Math.floor(m.posX);
                m.y = 103;
                m.z = (int) Math.floor(m.posZ);
                return m;
            });
            info.index = idx;
            info.tier = "Inferno Minion #" + idx;
            info.posX = getMinionTargetX(idx);
            info.posY = 103.0;
            info.posZ = getMinionTargetZ(idx);
            info.x = (int) Math.floor(info.posX);
            info.y = 103;
            info.z = (int) Math.floor(info.posZ);
        }
    }

    // Fuel guard: records existing fuel sitting in slot 19 so it is NEVER counted as placed
    private static final Set<String> existingRunningFuelUuids = Collections.synchronizedSet(new HashSet<>());
    private static boolean isInteractingWithFuelSlot = false;
    private static String fuelUuidBeforeClick = null;

    // Tracks if minion GUI was open recently
    private static long lastMinionGuiTime = 0L;
    private static long lastBulkClaimTime = 0L;
    private static long lastMinionHarvestClickTime = 0L;

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        loadFromDisk();
        ensureAllMinionsInitialized();
        syncWithServer(null);
    }

    public static TrackerState getState() {
        ensureLoaded();
        return state;
    }

    public static String getPasskey() {
        ensureLoaded();
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.infernoCoopPasskey != null && !s.infernoCoopPasskey.trim().isEmpty() && !s.infernoCoopPasskey.trim().equalsIgnoreCase("default")) {
            return s.infernoCoopPasskey.trim();
        }
        if (state.passkey != null && !state.passkey.trim().isEmpty() && !state.passkey.trim().equalsIgnoreCase("default")) {
            return state.passkey.trim();
        }
        if (SkyblockUtils.currentProfileId != null && !SkyblockUtils.currentProfileId.trim().isEmpty() && !SkyblockUtils.currentProfileId.trim().equalsIgnoreCase("default")) {
            return SkyblockUtils.currentProfileId.trim();
        }
        if (s != null && s.lastProfileId != null && !s.lastProfileId.trim().isEmpty() && !s.lastProfileId.trim().equalsIgnoreCase("default")) {
            return s.lastProfileId.trim();
        }
        return "82de315f-8acd-425d-b6ee-76e7fe96e0cd";
    }

    public static void onProfileChanged(String newProfileId) {
        if (newProfileId == null || newProfileId.trim().isEmpty()) return;
        newProfileId = newProfileId.trim().toLowerCase(Locale.ROOT);
        BomboConfig.Settings s = BomboConfig.get();
        String currentKey = (s != null && s.infernoCoopPasskey != null) ? s.infernoCoopPasskey.trim() : state.passkey;
        boolean isDefaultOrEmpty = currentKey == null || currentKey.isEmpty() || currentKey.equalsIgnoreCase("default");
        boolean isProfileFormat = currentKey != null && currentKey.matches("^[0-9a-fA-F-]{16,}$");

        if (isDefaultOrEmpty || isProfileFormat) {
            setPasskey(newProfileId);
        }
    }

    public static void setPasskey(String key) {
        if (key == null || key.trim().isEmpty()) key = "default";
        key = key.trim().toLowerCase(Locale.ROOT);
        if (!key.equals(state.passkey)) {
            state.passkey = key;
            state.drops.clear();
            state.fuels.clear();
            state.totalRevenue = 0;
            state.totalCost = 0;
            state.netProfit = 0;
            state.actions.clear();
            state.minions.clear();
        }
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.infernoCoopPasskey = key;
            BomboConfig.save();
        }
        saveToDisk();
        syncWithServer(null);
    }

    public static void loadFromDisk() {
        try {
            if (!DATA_FILE.exists()) {
                DATA_FILE.getParentFile().mkdirs();
                return;
            }
            try (FileReader reader = new FileReader(DATA_FILE, StandardCharsets.UTF_8)) {
                TrackerState loadedState = GSON.fromJson(reader, TrackerState.class);
                if (loadedState != null) {
                    state = loadedState;
                    if (state.drops == null) state.drops = new ConcurrentHashMap<>();
                    if (state.fuels == null) state.fuels = new ConcurrentHashMap<>();
                    if (state.seenFuelUuids == null) state.seenFuelUuids = Collections.synchronizedSet(new HashSet<>());
                    if (state.actions == null) state.actions = Collections.synchronizedList(new ArrayList<>());
                    if (state.minions == null) state.minions = new ConcurrentHashMap<>();
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static synchronized void saveToDisk() {
        try {
            if (!DATA_FILE.getParentFile().exists()) {
                DATA_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(DATA_FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void syncWithServer(Runnable onComplete) {
        String key = getPasskey();
        CompletableFuture.runAsync(() -> {
            try {
                String url = BomboApiUrl.getApiUrl("/api/inferno/data?key=" + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8));
                HttpRequest req = BomboApiUrl.attachApiKey(HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(6))).build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (json.has("success") && json.get("success").getAsBoolean() && json.has("data")) {
                        JsonObject data = json.getAsJsonObject("data");
                        Minecraft.getInstance().execute(() -> {
                            mergeServerData(data);
                            saveToDisk();
                            if (onComplete != null) onComplete.run();
                        });
                        return;
                    }
                }
            } catch (Throwable t) {
                // Silently keep local state if offline
            }
            if (onComplete != null) {
                Minecraft.getInstance().execute(onComplete);
            }
        });
    }

    private static void mergeServerData(JsonObject data) {
        if (data.has("totalRevenue")) state.totalRevenue = data.get("totalRevenue").getAsLong();
        if (data.has("totalCost")) state.totalCost = data.get("totalCost").getAsLong();
        if (data.has("netProfit")) state.netProfit = data.get("netProfit").getAsLong();

        if (data.has("drops") && data.get("drops").isJsonObject()) {
            state.drops.clear();
            JsonObject dObj = data.getAsJsonObject("drops");
            for (Map.Entry<String, JsonElement> entry : dObj.entrySet()) {
                state.drops.put(entry.getKey(), entry.getValue().getAsLong());
            }
        }

        if (data.has("fuels") && data.get("fuels").isJsonObject()) {
            state.fuels.clear();
            JsonObject fObj = data.getAsJsonObject("fuels");
            for (Map.Entry<String, JsonElement> entry : fObj.entrySet()) {
                state.fuels.put(entry.getKey(), entry.getValue().getAsLong());
            }
        }

        if (data.has("seenFuelUuids") && data.get("seenFuelUuids").isJsonArray()) {
            JsonArray arr = data.getAsJsonArray("seenFuelUuids");
            for (JsonElement el : arr) {
                state.seenFuelUuids.add(el.getAsString());
            }
        }

        if (data.has("actions") && data.get("actions").isJsonArray()) {
            state.actions.clear();
            JsonArray arr = data.getAsJsonArray("actions");
            for (JsonElement el : arr) {
                try {
                    if (el.isJsonObject()) {
                        JsonObject aObj = el.getAsJsonObject();
                        ActionEntry a = GSON.fromJson(aObj, ActionEntry.class);
                        if (a != null) {
                            if ((a.player == null || a.player.isEmpty()) && aObj.has("user")) {
                                a.player = aObj.get("user").getAsString();
                            }
                            if (a.cost == 0 && aObj.has("fuelCost")) {
                                a.cost = aObj.get("fuelCost").getAsLong();
                            }
                            state.actions.add(a);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (data.has("minions") && data.get("minions").isJsonObject()) {
            JsonObject mObj = data.getAsJsonObject("minions");
            for (Map.Entry<String, JsonElement> entry : mObj.entrySet()) {
                try {
                    MinionInfo m = GSON.fromJson(entry.getValue(), MinionInfo.class);
                    if (m != null) state.minions.put(entry.getKey(), m);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void pushActionToServer(ActionEntry action) {
        CompletableFuture.runAsync(() -> {
            try {
                String key = getPasskey();
                JsonObject payload = new JsonObject();
                payload.addProperty("key", key);
                payload.addProperty("passkey", key);
                payload.addProperty("type", action.type);
                payload.addProperty("player", action.player);
                payload.addProperty("user", action.player);
                payload.addProperty("minionUuid", action.minionUuid);
                payload.addProperty("minionEntityId", action.minionEntityId);
                payload.addProperty("minionPos", action.pos);
                if (action.fuelUuid != null) payload.addProperty("fuelUuid", action.fuelUuid);
                if (action.cost > 0) {
                    payload.addProperty("fuelCost", action.cost);
                    payload.addProperty("cost", action.cost);
                }
                if (action.revenue > 0) payload.addProperty("revenue", action.revenue);
                if (action.drops != null && !action.drops.isEmpty()) {
                    JsonObject dObj = new JsonObject();
                    for (Map.Entry<String, Integer> e : action.drops.entrySet()) {
                        dObj.addProperty(e.getKey(), e.getValue());
                    }
                    payload.add("drops", dObj);
                }

                String url = BomboApiUrl.getApiUrl("/api/inferno/action");
                HttpRequest req = BomboApiUrl.attachApiKey(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(6)))
                    .build();

                HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static void resetData(Runnable onComplete) {
        state.totalRevenue = 0L;
        state.totalCost = 0L;
        state.netProfit = 0L;
        state.drops.clear();
        state.fuels.clear();
        state.seenFuelUuids.clear();
        state.actions.clear();
        state.minions.clear();
        saveToDisk();

        CompletableFuture.runAsync(() -> {
            try {
                String key = getPasskey();
                String url = BomboApiUrl.getApiUrl("/api/inferno/reset?key=" + java.net.URLEncoder.encode(key, StandardCharsets.UTF_8));
                HttpRequest req = BomboApiUrl.attachApiKey(HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(6))).build();
                HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {}
            if (onComplete != null) {
                Minecraft.getInstance().execute(onComplete);
            }
        });
    }

    /**
     * Resets specifically fuel expenses and fuel counts without wiping collected drops.
     */
    public static void resetFuels(Runnable onComplete) {
        state.totalCost = 0L;
        state.netProfit = state.totalRevenue;
        state.fuels.clear();
        state.seenFuelUuids.clear();
        state.actions.removeIf(a -> "FUEL".equalsIgnoreCase(a.type));
        for (MinionInfo m : state.minions.values()) {
            m.totalFuels = 0;
        }
        saveToDisk();
        if (onComplete != null) {
            Minecraft.getInstance().execute(onComplete);
        }
    }

    // Entity interaction hook
    public static void onEntityRightClicked(Entity entity) {
        if (!SkyblockUtils.isInPrivateIsland()) return;
        if (entity == null) return;
        if (entity instanceof ArmorStand || entity.getName().getString().contains("Minion")) {
            lastInteractedEntity = entity;
            lastInteractTime = System.currentTimeMillis();
        }
    }

    // Container open / tick hook
    public static void onContainerTick(AbstractContainerScreen<?> screen) {
        if (!SkyblockUtils.isInPrivateIsland()) return;
        String title = screen.getTitle().getString();
        if (!isInfernoMinionGui(title)) return;

        lastMinionGuiTime = System.currentTimeMillis();

        if (screen.getMenu() instanceof ChestMenu chestMenu) {
            resolveActiveMinion(title);

            // Register existing fuel currently sitting in slot 19 so it is NEVER counted as placed fuel
            ItemStack fuelStack = chestMenu.getSlot(19).getItem();
            String existingUuid = extractFuelUuid(fuelStack);
            if (existingUuid != null) {
                existingRunningFuelUuids.add(existingUuid);
            }
        }
    }

    public static boolean isInfernoMinionGui(String title) {
        if (title == null) return false;
        String clean = ChatFormatting.stripFormatting(title).trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("recipe") || lower.contains("crafting") || lower.contains("bazaar") || lower.contains("order")) return false;
        return clean.matches("(?i)^Inferno Minion(\\s+[IVXLCDM]+)?$");
    }

    public static boolean isInfernoMinionGuiOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> containerScreen) {
            return isInfernoMinionGui(containerScreen.getTitle().getString());
        }
        return false;
    }

    private static void resolveActiveMinion(String title) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double bestX = 0, bestY = 0, bestZ = 0;
        boolean foundCandidate = false;

        // 1. Check last interacted entity (if within 8s)
        if (lastInteractedEntity != null && System.currentTimeMillis() - lastInteractTime < 8000L) {
            bestX = lastInteractedEntity.getX();
            bestY = lastInteractedEntity.getY();
            bestZ = lastInteractedEntity.getZ();
            foundCandidate = true;
            activeMinionEntityId = lastInteractedEntity.getId();
        }

        // 2. Check player hitResult (crosshair raycast block or entity)
        if (!foundCandidate && mc.hitResult != null) {
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr && ehr.getEntity() != null) {
                bestX = ehr.getEntity().getX();
                bestY = ehr.getEntity().getY();
                bestZ = ehr.getEntity().getZ();
                foundCandidate = true;
                activeMinionEntityId = ehr.getEntity().getId();
            } else if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                BlockPos bp = bhr.getBlockPos();
                bestX = bp.getX() + 0.5;
                bestY = bp.getY() + 1.0;
                bestZ = bp.getZ() + 0.5;
                foundCandidate = true;
            }
        }

        // 3. Fallback: look for nearest ArmorStand within 6 blocks
        if (!foundCandidate && mc.level != null) {
            ArmorStand nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof ArmorStand stand) {
                    double distSq = stand.distanceToSqr(mc.player);
                    if (distSq < 36.0 && distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = stand;
                    }
                }
            }
            if (nearest != null) {
                bestX = nearest.getX();
                bestY = nearest.getY();
                bestZ = nearest.getZ();
                foundCandidate = true;
                activeMinionEntityId = nearest.getId();
            }
        }

        // 4. Fallback: player's position
        if (!foundCandidate) {
            bestX = mc.player.getX();
            bestY = mc.player.getY();
            bestZ = mc.player.getZ();
        }

        // Match against 32-minion grid
        int idx = getMinionIndexForCoords(bestX, bestY, bestZ);
        if (idx == -1) {
            idx = getMinionIndexForCoords(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        if (idx != -1) {
            activeMinionIndex = idx;
            activeMinionName = "Inferno Minion #" + idx;
            activeMinionUuid = "minion_" + idx;
            double tx = getMinionTargetX(idx);
            double tz = getMinionTargetZ(idx);
            activeMinionPos = new BlockPos((int) Math.floor(tx), 103, (int) Math.floor(tz));
        } else {
            activeMinionIndex = 1;
            activeMinionName = title != null ? title : "Inferno Minion";
            activeMinionUuid = "minion_1";
            activeMinionPos = mc.player.blockPosition();
        }
    }

    public static void onContainerClosed() {
        isInteractingWithFuelSlot = false;
        fuelUuidBeforeClick = null;
    }

    // Slot click hook
    public static void onSlotClick(Slot slot, int slotId, int mouseButton, ContainerInput type, AbstractContainerScreen<?> screen, boolean isReturn) {
        if (!SkyblockUtils.isInPrivateIsland()) return;
        String title = screen.getTitle().getString();
        if (!isInfernoMinionGui(title)) return;
        if (!(screen.getMenu() instanceof ChestMenu chestMenu)) return;

        lastMinionGuiTime = System.currentTimeMillis();
        resolveActiveMinion(title);

        if (!isReturn) {
            // Check if player clicked Pickup Minion (slot 53) or Collect All (slot 49)
            String buttonName = "";
            if (slot != null && slot.hasItem()) {
                buttonName = ChatFormatting.stripFormatting(slot.getItem().getHoverName().getString()).toLowerCase(Locale.ROOT);
            }
            boolean isPickupButton = (slotId == 53 || buttonName.contains("pickup minion") || buttonName.contains("pick up minion"))
                && (buttonName.contains("pickup") || buttonName.contains("pick up"));
            boolean isCollectAllButton = (slotId == 49 || buttonName.contains("collect all"))
                && buttonName.contains("collect all");

            if (isPickupButton || isCollectAllButton) {
                lastMinionHarvestClickTime = System.currentTimeMillis();
                lastBulkClaimTime = System.currentTimeMillis();
                Map<String, Integer> claimed = new HashMap<>();
                long revenue = 0L;
                int containerSize = Math.min(chestMenu.slots.size(), 54);
                for (int i = 0; i < containerSize; i++) {
                    if (i == 19) continue; // Skip fuel slot
                    Slot s = chestMenu.getSlot(i);
                    if (s != null && s.hasItem()) {
                        ItemStack item = s.getItem();
                        String id = SkyblockUtils.getSkyblockId(item);
                        if (NON_SACK_DROPS.contains(id)) {
                            int count = item.getCount();
                            if (count > 0) {
                                claimed.put(id, claimed.getOrDefault(id, 0) + count);
                                revenue += getDropUnitPrice(id) * count;
                            }
                        }
                    }
                }
                if (!claimed.isEmpty()) {
                    recordClaim(claimed, revenue);
                }
            } else if (slotId >= 0 && slotId < 54 && slotId != 19) {
                // Individual slot click on a minion storage slot (must be in minion chest rows, not slot 49/53 or player inv)
                boolean isStorageSlot = (slotId >= 21 && slotId <= 25) || (slotId >= 30 && slotId <= 34) || (slotId >= 39 && slotId <= 43) || (slotId >= 18 && slotId < 45);
                long now = System.currentTimeMillis();
                if (isStorageSlot && now - lastBulkClaimTime > 800L && slot != null && slot.hasItem()) {
                    ItemStack clickedItem = slot.getItem();
                    String id = SkyblockUtils.getSkyblockId(clickedItem);
                    if (NON_SACK_DROPS.contains(id)) {
                        lastMinionHarvestClickTime = now;
                        int count = clickedItem.getCount();
                        if (type == ContainerInput.PICKUP && mouseButton == 1) {
                            count = (count + 1) / 2; // Right click picks up half
                        }
                        if (count > 0) {
                            Map<String, Integer> single = Map.of(id, count);
                            long revenue = getDropUnitPrice(id) * count;
                            recordClaim(single, revenue);
                        }
                    }
                }
            }

            // Check if player is actually interacting with the fuel slot
            isInteractingWithFuelSlot = (slotId == 19) || (type == ContainerInput.QUICK_MOVE && slot != null && isFuelItem(slot.getItem()));
            if (isInteractingWithFuelSlot) {
                fuelUuidBeforeClick = extractFuelUuid(chestMenu.getSlot(19).getItem());
            } else {
                fuelUuidBeforeClick = null;
            }
        } else {
            // RETURN: verify fuel updates
            Minecraft mc = Minecraft.getInstance();
            boolean checkFuel = isInteractingWithFuelSlot;
            String beforeUuid = fuelUuidBeforeClick;
            isInteractingWithFuelSlot = false;
            fuelUuidBeforeClick = null;

            if (checkFuel) {
                mc.execute(() -> {
                    try {
                        ItemStack fuelSlotItem = chestMenu.getSlot(19).getItem();
                        String afterUuid = extractFuelUuid(fuelSlotItem);
                        if (afterUuid != null && !afterUuid.equals(beforeUuid)) {
                            // Slot 19 changed to a new fuel UUID during this click
                            if (!existingRunningFuelUuids.contains(afterUuid) && !state.seenFuelUuids.contains(afterUuid)) {
                                checkAndRecordFuelPlacement(fuelSlotItem, afterUuid);
                            }
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                });
            }
        }
    }

    private static void checkAndRecordFuelPlacement(ItemStack fuelStack, String fuelUuid) {
        if (fuelStack == null || fuelStack.isEmpty() || fuelUuid == null || fuelUuid.isEmpty()) return;

        // Check if fuel is Hypergolic gabagool fuel
        String sbId = SkyblockUtils.getSkyblockId(fuelStack);
        boolean isHypergolic = HYPERGOLIC_FUEL_ID.equalsIgnoreCase(sbId) || isFuelItem(fuelStack);
        if (!isHypergolic) return;

        // Double check deduplication
        if (state.seenFuelUuids.contains(fuelUuid)) return;
        if (existingRunningFuelUuids.contains(fuelUuid)) return;

        // Calculate Craft Cost
        long craftCost = LowestBinManager.getCraftCostCached(HYPERGOLIC_FUEL_ID);
        if (craftCost <= 0) {
            craftCost = LowestBinManager.getCachedPrice(HYPERGOLIC_FUEL_ID);
        }
        if (craftCost <= 0) {
            craftCost = 4_350_000L;
        }

        // Record fuel
        state.seenFuelUuids.add(fuelUuid);
        existingRunningFuelUuids.add(fuelUuid);
        state.totalCost += craftCost;
        state.netProfit = state.totalRevenue - state.totalCost;
        state.fuels.put(HYPERGOLIC_FUEL_ID, state.fuels.getOrDefault(HYPERGOLIC_FUEL_ID, 0L) + 1L);

        // Update minion record
        updateMinionRecord(0, 1, null);

        // Log action
        Minecraft mc = Minecraft.getInstance();
        String playerName = mc.player != null ? mc.player.getName().getString() : "Player";
        ActionEntry action = new ActionEntry();
        action.type = "FUEL";
        action.player = playerName;
        action.timestamp = System.currentTimeMillis();
        action.minionUuid = "minion_" + activeMinionIndex;
        action.minionEntityId = activeMinionEntityId;
        double tx = getMinionTargetX(activeMinionIndex);
        double tz = getMinionTargetZ(activeMinionIndex);
        action.pos = String.format(Locale.ROOT, "%.1f, 103.0, %.1f", tx, tz);
        action.cost = craftCost;
        action.revenue = 0L;
        action.fuelUuid = fuelUuid;
        state.actions.add(0, action);

        saveToDisk();
        pushActionToServer(action);

        if (BomboConfig.get().infernoChatAlerts && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Inferno§8] §aAdded 1x Hypergolic Fuel! §7(Craft Cost: §c-" + formatCoins(craftCost) + "§7) §8[Net: " + (state.netProfit >= 0 ? "§a+" : "§c") + formatCoins(state.netProfit) + "§8]"));
        }
    }

    /**
     * Chat hook for [Sacks] +X items messages
     */
    public static void onChatMessage(Component message) {
        if (message == null) return;
        if (!SkyblockUtils.isInPrivateIsland()) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && !s.infernoTrackerEnabled) return;

        String unformatted = ChatFormatting.stripFormatting(message.getString());
        if (unformatted == null || !unformatted.contains("[Sacks]")) return;

        // Player must have actively clicked a harvest action in an Inferno minion within the last 2 seconds
        long now = System.currentTimeMillis();
        boolean recentHarvest = (now - lastMinionHarvestClickTime <= 2000L);
        if (!recentHarvest) return;
        if (SkyblockUtils.isInBazaar()) return;

        List<String> hoverLines = extractAllHoverText(message);
        if (hoverLines.isEmpty()) return;

        Map<String, Integer> claimedFromSacks = new HashMap<>();
        long claimRevenue = 0L;

        for (String line : hoverLines) {
            String clean = ChatFormatting.stripFormatting(line).trim();
            Matcher matcher = SACKS_ADD_PATTERN.matcher(clean);
            if (matcher.find()) {
                int count = (int) parseLongSafe(matcher.group(1));
                String rawName = matcher.group(2).trim();
                String sbId = resolveInfernoDropId(rawName);
                if (sbId != null && count > 0) {
                    claimedFromSacks.put(sbId, claimedFromSacks.getOrDefault(sbId, 0) + count);
                    long unitPrice = getDropUnitPrice(sbId);
                    claimRevenue += unitPrice * count;
                }
            }
        }

        if (!claimedFromSacks.isEmpty()) {
            recordClaim(claimedFromSacks, claimRevenue);
        }
    }

    public static String resolveInfernoDropId(String rawName) {
        if (rawName == null) return null;
        String lower = rawName.toLowerCase(Locale.ROOT).trim();
        if (lower.contains("crude gabagool")) {
            if (lower.contains("very crude")) return "VERY_CRUDE_GABAGOOL";
            return "CRUDE_GABAGOOL";
        }
        if (lower.contains("fuel gabagool")) return "FUEL_GABAGOOL";
        if (lower.contains("heavy gabagool")) return "HEAVY_GABAGOOL";
        if (lower.contains("chili pepper")) {
            if (lower.contains("enchanted")) return "ENCHANTED_CHILI_PEPPER";
            return "CHILI_PEPPER";
        }
        if (lower.contains("inferno vertex") || lower.contains("vertex")) return "INFERNO_VERTEX";
        if (lower.contains("inferno apex") || lower.contains("apex")) return "INFERNO_APEX";
        if (lower.contains("reaper pepper")) return "REAPER_PEPPER";
        if (lower.contains("gabagool the fish") || lower.contains("gabagool_the_fish") || (lower.contains("gabagool") && lower.contains("fish"))) return "GABAGOOL_THE_FISH";
        return null;
    }

    private static List<String> extractAllHoverText(Component component) {
        List<String> lines = new ArrayList<>();
        if (component == null) return lines;
        Set<String> visitedTexts = new HashSet<>();
        inspectComponentHover(component, lines, visitedTexts);
        return lines;
    }

    private static void inspectComponentHover(Component comp, List<String> lines, Set<String> visitedTexts) {
        if (comp == null) return;
        Style style = comp.getStyle();
        if (style != null && style.getHoverEvent() != null) {
            HoverEvent hover = style.getHoverEvent();
            if (hover instanceof HoverEvent.ShowText textHover) {
                Component hoverComp = textHover.value();
                if (hoverComp != null) {
                    String fullText = hoverComp.getString();
                    if (fullText != null && !fullText.trim().isEmpty() && visitedTexts.add(fullText.trim())) {
                        for (String l : fullText.split("\\r?\\n")) {
                            if (!l.trim().isEmpty()) {
                                lines.add(l.trim());
                            }
                        }
                    }
                }
            }
        }
        for (Component child : comp.getSiblings()) {
            inspectComponentHover(child, lines, visitedTexts);
        }
    }

    private static void recordClaim(Map<String, Integer> claimedDrops, long claimRevenue) {
        state.totalRevenue += claimRevenue;
        state.netProfit = state.totalRevenue - state.totalCost;

        int totalCount = 0;
        for (Map.Entry<String, Integer> e : claimedDrops.entrySet()) {
            state.drops.put(e.getKey(), state.drops.getOrDefault(e.getKey(), 0L) + e.getValue());
            totalCount += e.getValue();
        }

        updateMinionRecord(totalCount, 0, claimedDrops);

        Minecraft mc = Minecraft.getInstance();
        String playerName = mc.player != null ? mc.player.getName().getString() : "Player";
        ActionEntry action = new ActionEntry();
        action.type = "CLAIM";
        action.player = playerName;
        action.timestamp = System.currentTimeMillis();
        action.minionUuid = "minion_" + activeMinionIndex;
        action.minionEntityId = activeMinionEntityId;
        double tx = getMinionTargetX(activeMinionIndex);
        double tz = getMinionTargetZ(activeMinionIndex);
        action.pos = String.format(Locale.ROOT, "%.1f, 103.0, %.1f", tx, tz);
        action.cost = 0L;
        action.revenue = claimRevenue;
        action.drops = claimedDrops;
        state.actions.add(0, action);

        saveToDisk();
        pushActionToServer(action);

        if (BomboConfig.get().infernoChatAlerts && mc.player != null) {
            StringBuilder sb = new StringBuilder("§8[§6🔥 Inferno§8] §aClaimed §e" + totalCount + "§a drops from §6Minion #" + activeMinionIndex + " §7(§6+" + formatCoins(claimRevenue) + "§7): ");
            int i = 0;
            for (Map.Entry<String, Integer> e : claimedDrops.entrySet()) {
                if (i++ > 0) sb.append("§7, ");
                sb.append("§f").append(e.getValue()).append("x §e").append(formatDropName(e.getKey()));
            }
            sb.append(" §8[Net: ").append(state.netProfit >= 0 ? "§a+" : "§c").append(formatCoins(state.netProfit)).append("§8]");
            mc.player.sendSystemMessage(Component.literal(sb.toString()));
        }
    }

    private static void updateMinionRecord(int addedDrops, int addedFuels, Map<String, Integer> claimedDrops) {
        ensureAllMinionsInitialized();
        int idx = (activeMinionIndex >= 1 && activeMinionIndex <= 32) ? activeMinionIndex : 1;
        String key = "minion_" + idx;
        MinionInfo info = state.minions.computeIfAbsent(key, k -> {
            MinionInfo m = new MinionInfo();
            m.index = idx;
            m.uuid = key;
            m.tier = "Inferno Minion #" + idx;
            m.posX = getMinionTargetX(idx);
            m.posY = 103.0;
            m.posZ = getMinionTargetZ(idx);
            m.x = (int) Math.floor(m.posX);
            m.y = 103;
            m.z = (int) Math.floor(m.posZ);
            return m;
        });

        info.index = idx;
        info.entityId = activeMinionEntityId;
        info.tier = "Inferno Minion #" + idx;
        info.posX = getMinionTargetX(idx);
        info.posY = 103.0;
        info.posZ = getMinionTargetZ(idx);
        info.x = (int) Math.floor(info.posX);
        info.y = 103;
        info.z = (int) Math.floor(info.posZ);
        info.totalDrops += addedDrops;
        info.totalFuels += addedFuels;
        info.lastActionTime = System.currentTimeMillis();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            info.lastActionPlayer = mc.player.getName().getString();
        }

        if (claimedDrops != null) {
            if (info.dropCounts == null) info.dropCounts = new ConcurrentHashMap<>();
            for (Map.Entry<String, Integer> e : claimedDrops.entrySet()) {
                info.dropCounts.put(e.getKey(), info.dropCounts.getOrDefault(e.getKey(), 0L) + e.getValue());
            }
        }
    }

    public static boolean isFarmDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = SkyblockUtils.getSkyblockId(stack);
        if (id.isEmpty()) return false;

        // Explicitly exclude minion fuel
        if (HYPERGOLIC_FUEL_ID.equalsIgnoreCase(id) || isFuelItem(stack)) {
            return false;
        }

        // Exclude minion upgrades and buttons
        if (id.contains("FLYCATCHER") || id.contains("COMPACTOR") || id.contains("HOPPER") || id.contains("SPREADING") || id.contains("SOIL") || id.contains("SMELTER")) {
            return false;
        }

        if (TRACKED_DROPS.contains(id)) return true;
        if (id.contains("GABAGOOL") && !id.contains("FUEL_BLOCK")) return true;
        if (id.contains("PEPPER")) return true;
        if (id.contains("VERTEX") || id.contains("APEX")) return true;

        return false;
    }

    public static boolean isFuelItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            CustomData cd = (CustomData) stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
                CompoundTag tag = cd.copyTag();
                if (tag.contains("fuel_item") || tag.contains("inferno_fuel")) return true;
                if (tag.contains("ExtraAttributes")) {
                    CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                    if (ea != null && (ea.contains("fuel_item") || ea.contains("inferno_fuel"))) return true;
                }
            }
        } catch (Throwable ignored) {}
        String id = SkyblockUtils.getSkyblockId(stack);
        return HYPERGOLIC_FUEL_ID.equalsIgnoreCase(id);
    }

    public static String extractFuelUuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            CustomData cd = (CustomData) stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
                CompoundTag tag = cd.copyTag();
                if (tag.contains("uuid")) {
                    return tag.getString("uuid").orElse(null);
                }
                if (tag.contains("ExtraAttributes")) {
                    CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                    if (ea != null && ea.contains("uuid")) {
                        return ea.getString("uuid").orElse(null);
                    }
                }
                if (tag.contains("timestamp")) {
                    return "ts_" + tag.getLong("timestamp").orElse(0L);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static long getDropUnitPrice(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return 0L;
        long p = LowestBinManager.getCachedPrice(skyblockId);
        if ("INFERNO_VERTEX".equals(skyblockId)) {
            if (p <= 0L || p > 1_500_000L) return 222199L;
            return p;
        }
        if ("INFERNO_APEX".equals(skyblockId)) {
            if (p <= 0L || p > 40_000_000L) return 8157176L;
            return p;
        }
        if ("REAPER_PEPPER".equals(skyblockId)) {
            if (p <= 0L || p > 30_000_000L) return 13619981L;
            return p;
        }
        if ("GABAGOOL_THE_FISH".equals(skyblockId)) {
            if (p <= 0L || p > 25_000_000L) return 7000000L;
            return p;
        }
        if (p <= 0L) {
            switch (skyblockId) {
                case "CRUDE_GABAGOOL": return 402L;
                case "VERY_CRUDE_GABAGOOL": return 1608L;
                case "FUEL_GABAGOOL": return 14500L;
                case "HEAVY_GABAGOOL": return 350000L;
                case "CHILI_PEPPER": return 31500L;
                case "ENCHANTED_CHILI_PEPPER": return 5040000L;
                case "INFERNO_VERTEX": return 222199L;
                case "INFERNO_APEX": return 8157176L;
                case "REAPER_PEPPER": return 13619981L;
                case "GABAGOOL_THE_FISH": return 7000000L;
                default: return 1000L;
            }
        }
        return p;
    }

    public static String formatCoins(long c) {
        boolean neg = c < 0;
        long abs = Math.abs(c);
        String s;
        if (abs >= 1_000_000_000L) {
            s = String.format(Locale.ROOT, "%.2fB", abs / 1_000_000_000.0);
        } else if (abs >= 1_000_000L) {
            s = String.format(Locale.ROOT, "%.2fM", abs / 1_000_000.0);
        } else if (abs >= 1_000L) {
            s = String.format(Locale.ROOT, "%.1fK", abs / 1_000.0);
        } else {
            s = String.valueOf(abs);
        }
        return (neg ? "-" : "") + s;
    }

    public static String formatDropName(String id) {
        if (id == null) return "Unknown";
        switch (id) {
            case "CRUDE_GABAGOOL": return "Crude Gabagool";
            case "VERY_CRUDE_GABAGOOL": return "Very Crude Gabagool";
            case "FUEL_GABAGOOL": return "Fuel Gabagool";
            case "HEAVY_GABAGOOL": return "Heavy Gabagool";
            case "HYPERGOLIC_GABAGOOL": return "Hypergolic Gabagool";
            case "CHILI_PEPPER": return "Chili Pepper";
            case "ENCHANTED_CHILI_PEPPER": return "Enchanted Chili Pepper";
            case "INFERNO_VERTEX": return "Inferno Vertex";
            case "INFERNO_APEX": return "Inferno Apex";
            case "REAPER_PEPPER": return "Reaper Pepper";
            case "GABAGOOL_THE_FISH": return "Gabagool the Fish";
            case "INFERNO_HYPERGOLIC_CRUDE_GABAGOOL": return "Hypergolic Crude Gabagool";
            default:
                String[] words = id.toLowerCase(Locale.ROOT).replace("_", " ").split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    if (w.isEmpty()) continue;
                    if (sb.length() > 0) sb.append(" ");
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
                }
                return sb.toString();
        }
    }

    private static long parseLongSafe(String s) {
        if (s == null) return 0L;
        try {
            return Long.parseLong(s.replace(",", "").trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
