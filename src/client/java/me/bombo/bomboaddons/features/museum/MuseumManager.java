package me.bombo.bomboaddons.features.museum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

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
 * Authoritative Museum catalog and player donation tracking manager.
 * Resolves donation eligibility, transitive upgrade chains, and SkyBlock XP rewards.
 */
public class MuseumManager {
    public static class MuseumEntry {
        public final String id;
        public final String name;
        public final String category;
        public final int donationXp;
        public final String directUpgrade;
        public final List<String> upgradesTo;
        public final List<String> countsAs;
        public final String armorSet;

        public MuseumEntry(String id, String name, String category, int donationXp, String directUpgrade,
                           List<String> upgradesTo, List<String> countsAs, String armorSet) {
            this.id = id;
            this.name = name != null ? name : id;
            this.category = category != null ? category : "Special";
            this.donationXp = donationXp;
            this.directUpgrade = directUpgrade;
            this.upgradesTo = upgradesTo != null ? upgradesTo : Collections.emptyList();
            this.countsAs = countsAs != null ? countsAs : Collections.emptyList();
            this.armorSet = armorSet;
        }
    }

    private static final Map<String, MuseumEntry> CATALOG = new ConcurrentHashMap<>();
    private static final Set<String> DONATED_IDS = ConcurrentHashMap.newKeySet();
    private static boolean initialized = false;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    static {
        init();
    }

    public static synchronized void init() {
        if (initialized && !CATALOG.isEmpty()) return;
        loadBundledData();
        refreshFromApiAsync();
        initialized = true;
    }

    /**
     * Loads the bundled museum_items.json from mod resources.
     */
    private static void loadBundledData() {
        try (InputStream in = MuseumManager.class.getResourceAsStream("/data/museum_items.json")) {
            if (in != null) {
                parseJson(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            // Log or ignore
        }
    }

    /**
     * Refreshes the latest museum catalog from the Bombo API in background.
     */
    public static void refreshFromApiAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(BomboApiUrl.api("/mod/museum")))
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

            if (root.has("items") && root.get("items").isJsonObject()) {
                JsonObject itemsObj = root.getAsJsonObject("items");
                for (Map.Entry<String, JsonElement> e : itemsObj.entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject obj = e.getValue().getAsJsonObject();
                    String id = e.getKey().toUpperCase(Locale.ROOT);
                    String name = obj.has("name") ? obj.get("name").getAsString() : id;
                    String cat = obj.has("category") ? obj.get("category").getAsString() : "Special";
                    int xp = obj.has("donation_xp") ? obj.get("donation_xp").getAsInt() : 0;
                    String directUp = obj.has("direct_upgrade") && !obj.get("direct_upgrade").isJsonNull()
                            ? obj.get("direct_upgrade").getAsString() : null;

                    List<String> upTo = new ArrayList<>();
                    if (obj.has("upgrades_to") && obj.get("upgrades_to").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("upgrades_to")) {
                            upTo.add(el.getAsString().toUpperCase(Locale.ROOT));
                        }
                    }

                    List<String> counts = new ArrayList<>();
                    if (obj.has("counts_as") && obj.get("counts_as").isJsonArray()) {
                        for (JsonElement el : obj.getAsJsonArray("counts_as")) {
                            counts.add(el.getAsString().toUpperCase(Locale.ROOT));
                        }
                    }

                    String armorSet = obj.has("armor_set") && !obj.get("armor_set").isJsonNull()
                            ? obj.get("armor_set").getAsString() : null;

                    CATALOG.put(id, new MuseumEntry(id, name, cat, xp, directUp, upTo, counts, armorSet));
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean isMuseumItem(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        return CATALOG.containsKey(skyblockId.toUpperCase(Locale.ROOT));
    }

    public static MuseumEntry getEntry(String skyblockId) {
        if (skyblockId == null) return null;
        return CATALOG.get(skyblockId.toUpperCase(Locale.ROOT));
    }

    /**
     * Checks if this item has already been donated to the museum.
     * Evaluates direct donation, ExtraAttributes tag, and transitive upgrades.
     */
    public static boolean isDonated(String skyblockId) {
        return isDonated(skyblockId, null);
    }

    public static boolean isDonated(String skyblockId, ItemStack stack) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        String id = skyblockId.toUpperCase(Locale.ROOT);

        // 1. Direct recorded donation
        if (DONATED_IDS.contains(id)) return true;

        // 2. Transitive upgrade check: if player donated a higher-tier item, this item is satisfied!
        MuseumEntry entry = CATALOG.get(id);
        if (entry != null && entry.upgradesTo != null) {
            for (String upId : entry.upgradesTo) {
                if (DONATED_IDS.contains(upId)) {
                    return true;
                }
            }
        }

        // 3. Check stack ExtraAttributes for donated_museum
        if (stack != null && !stack.isEmpty()) {
            if (checkDonatedNbt(stack)) {
                DONATED_IDS.add(id);
                return true;
            }
        }

        return false;
    }

    private static boolean checkDonatedNbt(ItemStack stack) {
        try {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
                CompoundTag tag = customData.copyTag();
                if (tag.contains("ExtraAttributes")) {
                    CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                    if (ea != null) {
                        return ea.getBoolean("donated_museum").orElse(false)
                                || (ea.getByte("donated_museum").orElse((byte) 0) == 1)
                                || (ea.getInt("donated_museum").orElse(0) == 1);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Checks if an item is eligible to be donated to the museum.
     */
    public static boolean isDonatable(String skyblockId) {
        return isDonatable(skyblockId, null);
    }

    public static boolean isDonatable(String skyblockId, ItemStack stack) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        return isMuseumItem(skyblockId) && !isDonated(skyblockId, stack);
    }

    /**
     * If donating this item counts as donating another (e.g. Frosty the Snow Cannon),
     * returns the friendly display name(s).
     */
    public static String getCountsAsDescription(String skyblockId) {
        if (skyblockId == null) return null;
        MuseumEntry entry = CATALOG.get(skyblockId.toUpperCase(Locale.ROOT));
        if (entry == null || entry.countsAs == null || entry.countsAs.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entry.countsAs.size(); i++) {
            String cid = entry.countsAs.get(i);
            MuseumEntry centry = CATALOG.get(cid);
            String cname = centry != null ? centry.name : cid;
            if (i > 0) sb.append(", ");
            sb.append(cname);
        }
        return sb.toString();
    }

    public static int getRewardXp(String skyblockId) {
        if (skyblockId == null) return 0;
        MuseumEntry entry = CATALOG.get(skyblockId.toUpperCase(Locale.ROOT));
        return entry != null ? entry.donationXp : 0;
    }

    public static void recordDonated(String skyblockId) {
        if (skyblockId != null && !skyblockId.isEmpty()) {
            DONATED_IDS.add(skyblockId.toUpperCase(Locale.ROOT));
        }
    }

    public static void clearDonated() {
        DONATED_IDS.clear();
    }

    public static Set<String> getDonatedIds() {
        return Collections.unmodifiableSet(DONATED_IDS);
    }
}
