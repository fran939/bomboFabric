package me.bombo.bomboaddons.features.museum;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authoritative Museum catalog and player donation tracking manager.
 * Resolves donation eligibility, transitive upgrade chains, armor set matching,
 * in-game donation chat detection, and SkyBlock XP rewards.
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
    private static final Path STORAGE_FILE = Path.of("config", "bomboaddons", "museum_donated.json");
    private static final Pattern DONATION_CHAT_PATTERN = Pattern.compile("You donated your (.+?) to the (.+?) section of the Museum!(?: \\+(\\d+) SkyBlock XP)?", Pattern.CASE_INSENSITIVE);
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
        loadDonatedFromDisk();
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
        } catch (Exception ignored) {}
    }

    /**
     * Loads tracked donated items from local storage config/bomboaddons/museum_donated.json.
     */
    public static synchronized void loadDonatedFromDisk() {
        try {
            if (Files.exists(STORAGE_FILE)) {
                String str = Files.readString(STORAGE_FILE, StandardCharsets.UTF_8);
                JsonElement elem = JsonParser.parseString(str);
                if (elem.isJsonArray()) {
                    for (JsonElement el : elem.getAsJsonArray()) {
                        String id = el.getAsString().toUpperCase(Locale.ROOT);
                        DONATED_IDS.add(id);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Saves tracked donated items to local storage config/bomboaddons/museum_donated.json.
     */
    public static synchronized void saveDonatedToDisk() {
        try {
            if (STORAGE_FILE.getParent() != null && !Files.exists(STORAGE_FILE.getParent())) {
                Files.createDirectories(STORAGE_FILE.getParent());
            }
            JsonArray arr = new JsonArray();
            for (String id : DONATED_IDS) {
                arr.add(id);
            }
            Files.writeString(STORAGE_FILE, arr.toString(), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
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

    /**
     * Syncs player's museum donations directly from the backend API.
     */
    public static void syncPlayerDonationsAsync(String username, String profileId) {
        if (username == null || username.trim().isEmpty() || username.equalsIgnoreCase("Player")) return;
        CompletableFuture.runAsync(() -> {
            try {
                String url = BomboApiUrl.api("/mod/museum/" + encode(username.trim()));
                if (profileId != null && !profileId.isEmpty()) {
                    url += "?profile=" + encode(profileId);
                }
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isEmpty()) {
                    JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (root.has("donatedIds") && root.get("donatedIds").isJsonArray()) {
                        int added = 0;
                        for (JsonElement el : root.getAsJsonArray("donatedIds")) {
                            String id = el.getAsString().toUpperCase(Locale.ROOT);
                            if (DONATED_IDS.add(id)) {
                                added++;
                            }
                        }
                        if (root.has("specialItems") && root.get("specialItems").isJsonArray()) {
                            for (JsonElement el : root.getAsJsonArray("specialItems")) {
                                if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                                    String id = el.getAsJsonObject().get("id").getAsString().toUpperCase(Locale.ROOT);
                                    if (DONATED_IDS.add(id)) {
                                        added++;
                                    }
                                }
                            }
                        }
                        saveDonatedToDisk();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private static String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * Intercepts chat messages to detect in-game museum donations.
     * Pattern: "You donated your Rookie Pickaxe to the Mining section of the Museum! +2 SkyBlock XP"
     */
    public static void onChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) return;
        String clean = ChatFormatting.stripFormatting(rawMessage).trim();
        if (!clean.contains("donated your") || !clean.contains("Museum!")) return;

        Matcher m = DONATION_CHAT_PATTERN.matcher(clean);
        if (m.find()) {
            String itemName = m.group(1).trim();
            String section = m.group(2).trim();

            String matchedId = null;
            // 1. Look up exact item name in CATALOG
            for (MuseumEntry entry : CATALOG.values()) {
                if (entry.name.equalsIgnoreCase(itemName)) {
                    matchedId = entry.id;
                    break;
                }
            }

            // 2. Fallback: normalize name into ID
            if (matchedId == null) {
                String normalized = itemName.toUpperCase(Locale.ROOT)
                        .replaceAll("[^A-Z0-9]+", "_")
                        .replaceAll("^_+|_+$", "");
                if (CATALOG.containsKey(normalized)) {
                    matchedId = normalized;
                } else {
                    matchedId = normalized;
                }
            }

            if (matchedId != null) {
                recordDonated(matchedId);
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§8[§d🏛️ Museum§8] §aTracked donation: §e" + itemName + " §7(" + section + " Section)!"));
                }
            }
        }
    }

    private static void parseJson(Object input) {
        try {
            JsonObject root;
            if (input instanceof Reader) {
                root = JsonParser.parseReader((Reader) input).getAsJsonObject();
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

    public static boolean isDisallowedEquipment(String u) {
        if (u.endsWith("_BELT") || u.endsWith("_NECKLACE") || u.endsWith("_GLOVES") || u.endsWith("_CLOAK") || u.endsWith("_BRACELET")) {
            if (u.contains("DOJO") || u.equals("WHITE_BELT") || u.equals("YELLOW_BELT") || u.equals("GREEN_BELT")
                    || u.equals("BLUE_BELT") || u.equals("BROWN_BELT") || u.equals("BLACK_BELT") || u.equals("RIFT_NECKLACE_OUTSIDE")) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isMuseumItem(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        String u = skyblockId.toUpperCase(Locale.ROOT);
        if (isDisallowedEquipment(u)) return false;
        return CATALOG.containsKey(u);
    }

    public static MuseumEntry getEntry(String skyblockId) {
        if (skyblockId == null) return null;
        return CATALOG.get(skyblockId.toUpperCase(Locale.ROOT));
    }

    /**
     * Checks if this item has already been donated to the museum.
     * Evaluates direct donation, ExtraAttributes tag, armor set donation, and transitive upgrades.
     */
    public static boolean isDonated(String skyblockId) {
        return isDonated(skyblockId, null);
    }

    public static boolean isDonated(String skyblockId, ItemStack stack) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        String id = skyblockId.toUpperCase(Locale.ROOT);

        // 1. Direct recorded donation
        if (DONATED_IDS.contains(id)) return true;

        // 2. Armor set matching (Hypixel API records base set ID like YOUNG_DRAGON for Young Dragon armor)
        MuseumEntry entry = CATALOG.get(id);
        if (entry != null && entry.armorSet != null && !entry.armorSet.isEmpty()) {
            String setUpper = entry.armorSet.toUpperCase(Locale.ROOT);
            if (DONATED_IDS.contains(setUpper)) {
                return true;
            }
        }
        if (id.endsWith("_HELMET") || id.endsWith("_CHESTPLATE") || id.endsWith("_LEGGINGS") || id.endsWith("_BOOTS")) {
            String baseSet = id.replaceAll("_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)$", "");
            if (DONATED_IDS.contains(baseSet)) {
                return true;
            }
        }

        // 3. Transitive upgrade check: if player donated a higher-tier item, this item is satisfied!
        if (entry != null && entry.upgradesTo != null) {
            for (String upId : entry.upgradesTo) {
                if (DONATED_IDS.contains(upId)) {
                    return true;
                }
            }
        }

        // 4. Check stack ExtraAttributes for donated_museum
        if (stack != null && !stack.isEmpty()) {
            if (checkDonatedNbt(stack)) {
                DONATED_IDS.add(id);
                saveDonatedToDisk();
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
     * Honors museumHideSpecialItems toggle to hide special category items.
     */
    public static boolean isDonatable(String skyblockId) {
        return isDonatable(skyblockId, null);
    }

    public static boolean isDonatable(String skyblockId, ItemStack stack) {
        if (skyblockId == null || skyblockId.isEmpty()) return false;
        if (!isMuseumItem(skyblockId)) return false;

        // Toggle: hide special items from donatable
        if (BomboConfig.get().museumHideSpecialItems) {
            MuseumEntry entry = getEntry(skyblockId);
            if (entry != null && "Special".equalsIgnoreCase(entry.category)) {
                return false;
            }
        }

        return !isDonated(skyblockId, stack);
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
            if (DONATED_IDS.add(skyblockId.toUpperCase(Locale.ROOT))) {
                saveDonatedToDisk();
            }
        }
    }

    public static void clearDonated() {
        DONATED_IDS.clear();
        saveDonatedToDisk();
    }

    public static Set<String> getDonatedIds() {
        return Collections.unmodifiableSet(DONATED_IDS);
    }

    public static class ArmorSetStatus {
        public final String setName;
        public final int totalPieces;
        public final int ownedPieces;
        public final List<String> missingPieces;
        public final boolean isFullSet;
        public final boolean isDonated;

        public ArmorSetStatus(String setName, int totalPieces, int ownedPieces, List<String> missingPieces, boolean isFullSet, boolean isDonated) {
            this.setName = setName;
            this.totalPieces = totalPieces;
            this.ownedPieces = ownedPieces;
            this.missingPieces = missingPieces != null ? missingPieces : Collections.emptyList();
            this.isFullSet = isFullSet;
            this.isDonated = isDonated;
        }
    }

    public static boolean isArmorPiece(String id) {
        if (id == null) return false;
        String u = id.toUpperCase(Locale.ROOT);
        if (u.endsWith("_BELT") || u.endsWith("_NECKLACE") || u.endsWith("_GLOVES") || u.endsWith("_CLOAK") || u.endsWith("_BRACELET")) {
            return false;
        }
        return u.endsWith("_HELMET") || u.endsWith("_CHESTPLATE") || u.endsWith("_LEGGINGS") || u.endsWith("_BOOTS")
                || u.contains("HELMET") || u.contains("CHESTPLATE") || u.contains("LEGGINGS") || u.contains("BOOTS")
                || u.contains("HAT") || u.contains("CAP") || u.contains("MASK") || u.contains("CROWN")
                || u.contains("FEDORA") || u.contains("HOOD") || u.contains("JACKET") || u.contains("TUNIC")
                || u.contains("ROBE") || u.contains("VEST") || u.contains("PANTS") || u.contains("OXFORDS");
    }

    private static int getArmorSlotOrder(String id) {
        String u = id.toUpperCase(Locale.ROOT);
        if (u.contains("HELMET") || u.contains("HAT") || u.contains("CAP") || u.contains("MASK") || u.contains("CROWN") || u.contains("FEDORA") || u.contains("HOOD")) return 0;
        if (u.contains("CHESTPLATE") || u.contains("JACKET") || u.contains("TUNIC") || u.contains("ROBE") || u.contains("VEST")) return 1;
        if (u.contains("LEGGINGS") || u.contains("PANTS")) return 2;
        if (u.contains("BOOTS") || u.contains("OXFORDS") || u.contains("SHOES")) return 3;
        return 4;
    }

    private static String getPieceShortName(MuseumEntry piece) {
        String id = piece.id.toUpperCase(Locale.ROOT);
        if (id.contains("HELMET") || id.contains("HAT") || id.contains("CAP") || id.contains("MASK") || id.contains("CROWN") || id.contains("FEDORA") || id.contains("HOOD")) return "Helmet";
        if (id.contains("CHESTPLATE") || id.contains("JACKET") || id.contains("TUNIC") || id.contains("ROBE") || id.contains("VEST")) return "Chestplate";
        if (id.contains("LEGGINGS") || id.contains("PANTS")) return "Leggings";
        if (id.contains("BOOTS") || id.contains("OXFORDS") || id.contains("SHOES")) return "Boots";
        if (piece.name != null && !piece.name.isEmpty()) {
            String[] parts = piece.name.split("\\s+");
            return parts[parts.length - 1];
        }
        return piece.id;
    }

    private static String formatSetName(String armorSet) {
        if (armorSet == null || armorSet.isEmpty()) return "";
        String[] words = armorSet.replace("_", " ").toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    /**
     * Resolves armor set status for a museum armor item, checking whether all pieces of the set
     * are owned across island chest storage and active inventory, and returning missing pieces.
     */
    public static ArmorSetStatus getArmorSetStatus(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return null;
        String id = skyblockId.toUpperCase(Locale.ROOT);
        MuseumEntry entry = CATALOG.get(id);
        if (entry == null || entry.armorSet == null || entry.armorSet.isEmpty()) return null;
        if (!isArmorPiece(id)) return null;

        List<MuseumEntry> setPieces = new ArrayList<>();
        for (MuseumEntry e : CATALOG.values()) {
            if (entry.armorSet.equalsIgnoreCase(e.armorSet)) {
                if (isArmorPiece(e.id)) {
                    setPieces.add(e);
                }
            }
        }
        if (setPieces.size() <= 1) return null;

        setPieces.sort(Comparator.comparingInt(e -> getArmorSlotOrder(e.id)));

        Set<String> trackedIds = me.bombo.bomboaddons.features.StorageTracker.getTrackedSkyblockIds();
        boolean setDonated = isDonated(entry.armorSet) || isDonated(id);

        List<String> missing = new ArrayList<>();
        int owned = 0;

        for (MuseumEntry piece : setPieces) {
            boolean has = trackedIds.contains(piece.id) || isDonated(piece.id);
            if (!has && piece.countsAs != null) {
                for (String c : piece.countsAs) {
                    if (trackedIds.contains(c) || isDonated(c)) {
                        has = true;
                        break;
                    }
                }
            }
            if (!has && piece.upgradesTo != null) {
                for (String up : piece.upgradesTo) {
                    if (trackedIds.contains(up) || isDonated(up)) {
                        has = true;
                        break;
                    }
                }
            }
            if (has) {
                owned++;
            } else {
                missing.add(getPieceShortName(piece));
            }
        }

        boolean isFull = (owned >= setPieces.size());
        String formattedName = formatSetName(entry.armorSet);
        return new ArmorSetStatus(formattedName, setPieces.size(), owned, missing, isFull, setDonated);
    }
}
