package me.bombo.bomboaddons.features.inferno;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles fetching, categorizing, and inspecting player sacks from the Hypixel SkyBlock API.
 */
public class SacksHelper {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    // Cache of sacks by username / UUID: Map<PlayerName, Map<ItemId, Count>>
    private static final Map<String, Map<String, Integer>> SACKS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> SACKS_CACHE_TIME = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 60_000L; // 1 minute

    // Co-op shared sacks: Map<ProfileId, Map<PlayerName, Map<ItemId, Count>>>
    private static final Map<String, Map<String, Map<String, Integer>>> COOP_SACKS_CACHE = new ConcurrentHashMap<>();

    public enum SackCategory {
        AGRONOMY("🌾 Agronomy Sack", 0x10B981),
        MINING("⛏️ Mining Sack", 0x60A5FA),
        COMBAT("⚔️ Combat Sack", 0xEF4444),
        FORAGING("🪓 Foraging Sack", 0xF59E0B),
        FISHING("🎣 Fishing Sack", 0x06B6D4),
        SLAYER("💀 Slayer Sack", 0x8B5CF6),
        NETHER("🔥 Nether Sack", 0xF97316),
        GEMSTONE("💎 Gemstone Sack", 0xEC4899),
        DUNGEON("🏰 Dungeon Sack", 0x6366F1),
        ENCHANTING("✨ Enchanting Sack", 0x3B82F6),
        OTHER("📦 Other Sack", 0x9CA3AF);

        public final String displayName;
        public final int color;

        SackCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }
    }

    /**
     * Resolves which sack an item belongs to based on SkyBlock wiki definitions.
     */
    public static SackCategory getSackCategory(String itemId) {
        if (itemId == null) return SackCategory.OTHER;
        String id = itemId.toUpperCase(Locale.ROOT);

        // Gemstones
        if (id.contains("_GEM") || id.startsWith("ROUGH_") || id.startsWith("FLAWED_") || id.startsWith("FINE_") || id.startsWith("FLAWLESS_") || id.startsWith("PERFECT_")) {
            return SackCategory.GEMSTONE;
        }

        // Slayer
        if (id.contains("GABAGOOL") || id.contains("CHILI_PEPPER") || id.contains("REVENANT") || id.contains("FOUL_FLESH")
                || id.contains("TARANTULA") || id.contains("SPIDER_CATALYST") || id.contains("WOLF_TOOTH")
                || id.contains("GOLDEN_TOOTH") || id.contains("NULL_SPHERE") || id.contains("NULL_OVOID")
                || id.contains("DERELICT_ASHE") || id.contains("INFERNO_FUEL_BLOCK") || id.contains("INFERNO_VERTEX")
                || id.contains("INFERNO_APEX") || id.contains("REAPER_PEPPER")) {
            return SackCategory.SLAYER;
        }

        // Dungeon
        if (id.contains("ESSENCE") || id.contains("FRAGMENT") || id.contains("BONZO") || id.contains("LIVID") || id.contains("SCARF")) {
            return SackCategory.DUNGEON;
        }

        // Nether / Crimson
        if (id.contains("SULPHUR") || id.contains("MAGMA_FISH") || id.contains("SPELL_POWDER") || id.contains("CORRUPTED_FRAGMENT")) {
            return SackCategory.NETHER;
        }

        // Agronomy
        if (id.contains("WHEAT") || id.contains("CARROT") || id.contains("POTATO") || id.contains("PUMPKIN") || id.contains("MELON")
                || id.contains("MUSHROOM") || id.contains("CACTUS") || id.contains("SUGAR_CANE") || id.contains("SUGAR")
                || id.contains("NETHER_STALK") || id.contains("NETHER_WART") || id.contains("COCOA") || id.contains("SEEDS")
                || id.contains("HAY_BALE") || id.contains("HAY_BLOCK") || id.contains("COMPOST") || id.contains("ROSE")
                || id.contains("FLOWER") || id.contains("SUNFLOWER") || id.contains("TIGHTLY_TIED_HAY_BALE")) {
            return SackCategory.AGRONOMY;
        }

        // Mining
        if (id.contains("COBBLESTONE") || id.contains("COAL") || id.contains("IRON") || id.contains("GOLD")
                || id.contains("DIAMOND") || id.contains("EMERALD") || id.contains("REDSTONE") || id.contains("QUARTZ")
                || id.contains("OBSIDIAN") || id.contains("GLOWSTONE") || id.contains("FLINT") || id.contains("GRAVEL")
                || id.contains("ICE") || id.contains("PACKED_ICE") || id.contains("SAND") || id.contains("END_STONE")
                || id.contains("ENDSTONE") || id.contains("MITHRIL") || id.contains("TITANIUM") || id.contains("HARD_STONE")
                || id.contains("GLACITE") || id.contains("CONCENTRATED_STONE")) {
            return SackCategory.MINING;
        }

        // Combat
        if (id.contains("ROTTEN_FLESH") || id.contains("BONE") || id.contains("STRING") || id.contains("SPIDER_EYE")
                || id.contains("GUNPOWDER") || id.contains("ENDER_PEARL") || id.contains("SLIME_BALL") || id.contains("SLIME_BLOCK")
                || id.contains("MAGMA_CREAM") || id.contains("BLAZE_ROD") || id.contains("BLAZE_POWDER") || id.contains("GHAST_TEAR")
                || id.contains("EYE_OF_ENDER")) {
            return SackCategory.COMBAT;
        }

        // Foraging
        if (id.contains("LOG") || id.contains("WOOD") || id.contains("APPLE") || id.contains("SAPLING") || id.contains("LEAVES")) {
            return SackCategory.FORAGING;
        }

        // Fishing
        if (id.contains("FISH") || id.contains("SALMON") || id.contains("CLOWNFISH") || id.contains("PUFFERFISH")
                || id.contains("PRISMARINE") || id.contains("SPONGE") || id.contains("INK_SACK") || id.contains("LILY")) {
            return SackCategory.FISHING;
        }

        // Enchanting
        if (id.contains("EXP_BOTTLE") || id.contains("EXPERIENCE")) {
            return SackCategory.ENCHANTING;
        }

        return SackCategory.OTHER;
    }

    /**
     * Gets personal cached sack count for an item.
     */
    public static int getPersonalSackCount(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        String name = mc.player.getName().getString();
        Map<String, Integer> sacks = SACKS_CACHE.get(name.toLowerCase(Locale.ROOT));
        if (sacks == null) return 0;
        return sacks.getOrDefault(itemId, 0);
    }

    /**
     * Gets all co-op members' combined sack count for an item (excluding self if desired).
     */
    public static int getCoopSackCount(String itemId, boolean includeSelf) {
        Minecraft mc = Minecraft.getInstance();
        String self = mc.player != null ? mc.player.getName().getString().toLowerCase(Locale.ROOT) : "";
        int total = 0;
        for (Map.Entry<String, Map<String, Integer>> entry : SACKS_CACHE.entrySet()) {
            if (!includeSelf && entry.getKey().equalsIgnoreCase(self)) continue;
            total += entry.getValue().getOrDefault(itemId, 0);
        }
        return total;
    }

    /**
     * Gets total available count across physical inventory and sacks.
     */
    public static int getTotalAvailable(String itemId, boolean includeCoop) {
        int inv = getPhysicalInventoryCount(itemId);
        int personalSacks = getPersonalSackCount(itemId);
        int coopSacks = includeCoop ? getCoopSackCount(itemId, false) : 0;
        return inv + personalSacks + coopSacks;
    }

    /**
     * Counts items currently in the player's physical 36-slot inventory.
     */
    public static int getPhysicalInventoryCount(String itemId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String sbId = me.bombo.bomboaddons.SkyblockUtils.getSkyblockId(stack);
                if (itemId.equalsIgnoreCase(sbId)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /**
     * Refreshes sacks data asynchronously for the local player and all known co-op members.
     */
    public static void refreshAllCoopSacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            fetchUserSacks(mc.player.getName().getString());
        }
        for (String member : me.bombo.bomboaddons.TabCompletionManager.coop) {
            if (member != null && !member.isEmpty()) {
                fetchUserSacks(member);
            }
        }
        try {
            var state = me.bombo.bomboaddons.features.inferno.InfernoMinionTracker.getState();
            if (state != null && state.actions != null) {
                for (var a : state.actions) {
                    if (a.player != null && !a.player.isEmpty()) {
                        fetchUserSacks(a.player);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Gets list of co-op member usernames currently present in the sack cache.
     */
    public static List<String> getCoopMembersWithSacks() {
        List<String> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        String self = mc.player != null ? mc.player.getName().getString().toLowerCase(Locale.ROOT) : "";
        for (String u : SACKS_CACHE.keySet()) {
            if (!u.equalsIgnoreCase(self) && !u.contains("-")) { // skip raw uuids and self
                list.add(u);
            }
        }
        return list;
    }

    /**
     * Returns a compact summary string of a co-op member's fuel materials in their sacks.
     */
    public static String getUserFuelSacksSummary(String username) {
        if (username == null) return "None";
        Map<String, Integer> sacks = SACKS_CACHE.get(username.toLowerCase(Locale.ROOT));
        if (sacks == null || sacks.isEmpty()) return "§8No sacks cached";
        List<String> parts = new ArrayList<>();
        checkAdd(sacks, "CRUDE_GABAGOOL", "Crude", parts);
        checkAdd(sacks, "VERY_CRUDE_GABAGOOL", "Very Crude", parts);
        checkAdd(sacks, "FUEL_GABAGOOL", "Fuel Gab", parts);
        checkAdd(sacks, "HEAVY_GABAGOOL", "Heavy Gab", parts);
        checkAdd(sacks, "HYPERGOLIC_GABAGOOL", "Hypergolic", parts);
        checkAdd(sacks, "SULPHURIC_COAL", "Sulphuric Coal", parts);
        checkAdd(sacks, "ENCHANTED_COAL", "Ench Coal", parts);
        checkAdd(sacks, "ENCHANTED_SULPHUR", "Ench Sulphur", parts);
        checkAdd(sacks, "CRUDE_GABAGOOL_DISTILLATE", "Distillate", parts);
        checkAdd(sacks, "INFERNO_FUEL_BLOCK", "Fuel Block", parts);
        if (parts.isEmpty()) return "§8No fuel materials";
        return String.join("§8, §7", parts);
    }

    private static void checkAdd(Map<String, Integer> m, String id, String label, List<String> out) {
        int cnt = m.getOrDefault(id, 0);
        if (cnt > 0) {
            out.add("§f" + String.format(Locale.ROOT, "%,d", cnt) + " §e" + label);
        }
    }

    /**
     * Fetches sacks asynchronously for a given user, storing into SACKS_CACHE.
     */
    public static CompletableFuture<Map<String, Integer>> fetchUserSacks(String username) {
        if (username == null || username.trim().isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) username = mc.player.getName().getString();
            else return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String userLower = username.toLowerCase(Locale.ROOT).trim();
        Long cachedTime = SACKS_CACHE_TIME.get(userLower);
        if (cachedTime != null && System.currentTimeMillis() - cachedTime < CACHE_TTL_MS && SACKS_CACHE.containsKey(userLower)) {
            return CompletableFuture.completedFuture(SACKS_CACHE.get(userLower));
        }

        String url = BomboApiUrl.getApiUrl("/data/" + userLower);
        HttpRequest req = BomboApiUrl.attachApiKey(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (BomboAddons)")
                .timeout(Duration.ofSeconds(8))
                .GET())
                .build();

        String finalUser = userLower;
        return HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(res -> {
                    if (res.statusCode() != 200 || res.body() == null) return Collections.<String, Integer>emptyMap();
                    try {
                        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                        if (!root.has("profiles") || !root.get("profiles").isJsonArray()) {
                            return Collections.<String, Integer>emptyMap();
                        }

                        JsonArray profiles = root.getAsJsonArray("profiles");
                        JsonObject selectedProfile = null;
                        for (JsonElement el : profiles) {
                            if (el.isJsonObject()) {
                                JsonObject p = el.getAsJsonObject();
                                if (p.has("selected") && p.get("selected").getAsBoolean()) {
                                    selectedProfile = p;
                                    break;
                                }
                            }
                        }
                        if (selectedProfile == null && profiles.size() > 0 && profiles.get(0).isJsonObject()) {
                            selectedProfile = profiles.get(0).getAsJsonObject();
                        }
                        if (selectedProfile == null || !selectedProfile.has("members")) {
                            return Collections.<String, Integer>emptyMap();
                        }

                        JsonObject members = selectedProfile.getAsJsonObject("members");
                        Map<String, Integer> targetSacks = new ConcurrentHashMap<>();

                        // Parse sacks for all co-op members
                        for (Map.Entry<String, JsonElement> memEntry : members.entrySet()) {
                            String memberUuid = memEntry.getKey();
                            if (!memEntry.getValue().isJsonObject()) continue;
                            JsonObject memberObj = memEntry.getValue().getAsJsonObject();

                            JsonObject sacksObj = null;
                            if (memberObj.has("inventory") && memberObj.get("inventory").isJsonObject()) {
                                JsonObject inv = memberObj.getAsJsonObject("inventory");
                                if (inv.has("sacks_counts") && inv.get("sacks_counts").isJsonObject()) {
                                    sacksObj = inv.getAsJsonObject("sacks_counts");
                                }
                            }
                            if (sacksObj == null && memberObj.has("sacks_counts") && memberObj.get("sacks_counts").isJsonObject()) {
                                sacksObj = memberObj.getAsJsonObject("sacks_counts");
                            }

                            if (sacksObj != null) {
                                Map<String, Integer> memberSacks = new ConcurrentHashMap<>();
                                for (Map.Entry<String, JsonElement> itemEntry : sacksObj.entrySet()) {
                                    try {
                                        int count = itemEntry.getValue().getAsInt();
                                        if (count > 0) {
                                            memberSacks.put(itemEntry.getKey(), count);
                                        }
                                    } catch (Exception ignored) {}
                                }

                                // If this member matches the requested username, set as target
                                // Note: resolve target if uuid matches
                                SACKS_CACHE.put(memberUuid, memberSacks);
                                if (targetSacks.isEmpty() || memberUuid.contains(finalUser)) {
                                    targetSacks.putAll(memberSacks);
                                }
                            }
                        }

                        SACKS_CACHE.put(finalUser, targetSacks);
                        SACKS_CACHE_TIME.put(finalUser, System.currentTimeMillis());
                        return targetSacks;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        return Collections.<String, Integer>emptyMap();
                    }
                }).exceptionally(ex -> Collections.emptyMap());
    }

    /**
     * Executes the `/b debug sacks [user]` chat output.
     */
    public static void displaySacksDebug(String username) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String target = (username != null && !username.trim().isEmpty())
                ? username.trim()
                : mc.player.getName().getString();

        mc.player.sendSystemMessage(Component.literal("§8[§6🔥 BomboAddons§8] §7Querying sacks API for §b" + target + "§7..."));

        fetchUserSacks(target).thenAccept(sacks -> mc.execute(() -> {
            if (sacks == null || sacks.isEmpty()) {
                mc.player.sendSystemMessage(Component.literal("§8[§6🔥 BomboAddons§8] §cNo sacks data found or player has sacks API disabled for §e" + target + "§c."));
                return;
            }

            // Group by category
            Map<SackCategory, Map<String, Integer>> grouped = new TreeMap<>(Comparator.comparing(Enum::ordinal));
            int grandTotal = 0;

            for (Map.Entry<String, Integer> entry : sacks.entrySet()) {
                SackCategory cat = getSackCategory(entry.getKey());
                grouped.computeIfAbsent(cat, k -> new TreeMap<>()).put(entry.getKey(), entry.getValue());
                grandTotal += entry.getValue();
            }

            MutableComponent headerMsg = Component.literal("§8[§6🔥 Sacks Debug§8] §aFound §e" + formatNumber(grandTotal) + "§a total items in §b" + target + "§a's sacks: ");
            headerMsg.append(Component.literal("§b§l[🎒 Open Sacks GUI]")
                    .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/b sacks " + target))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§bClick to open interactive Sacks GUI!")))));
            mc.player.sendSystemMessage(headerMsg);

            for (Map.Entry<SackCategory, Map<String, Integer>> g : grouped.entrySet()) {
                SackCategory cat = g.getKey();
                Map<String, Integer> items = g.getValue();
                int catTotal = items.values().stream().mapToInt(Integer::intValue).sum();

                MutableComponent catHeader = Component.literal("\n§e" + cat.displayName + " §7(§f" + formatNumber(catTotal) + " items§7, §b" + items.size() + " types§7):");
                mc.player.sendSystemMessage(catHeader);

                // Show top 12 items, rest truncated with hover
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(items.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                StringBuilder line = new StringBuilder("  ");
                int shown = 0;
                for (Map.Entry<String, Integer> item : sorted) {
                    if (shown >= 8) {
                        int remaining = sorted.size() - shown;
                        line.append("§7... §8[§e+").append(remaining).append(" more§8]");
                        break;
                    }
                    if (shown > 0) line.append("§7, ");
                    line.append("§a").append(formatItemName(item.getKey())).append("§7: §e").append(formatNumber(item.getValue()));
                    shown++;
                }

                MutableComponent lineComp = Component.literal(line.toString());
                // Build full hover text
                StringBuilder hover = new StringBuilder("§6Full " + cat.displayName + " Contents:\n");
                for (Map.Entry<String, Integer> it : sorted) {
                    hover.append("§7• §f").append(formatItemName(it.getKey())).append(": §e").append(formatNumber(it.getValue())).append("\n");
                }
                lineComp.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover.toString().trim()))));
                mc.player.sendSystemMessage(lineComp);
            }

            mc.player.sendSystemMessage(Component.literal("§8§m-----------------------------------------------------"));
        }));
    }

    public static String formatItemName(String sbId) {
        if (sbId == null) return "Unknown";
        String[] words = sbId.toLowerCase(Locale.ROOT).replace("_", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static String formatNumber(long val) {
        return String.format(Locale.ROOT, "%,d", val);
    }
}
