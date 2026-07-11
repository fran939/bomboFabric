package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class LowestBinManager {
    private static final HttpClient client = HttpClient.newBuilder().build();
    private static final Map<String, Long> priceCache = new ConcurrentHashMap<>();
    private static final Map<String, Long> npcCache = new ConcurrentHashMap<>();
    private static final Map<String, Double> bazaarCache = new ConcurrentHashMap<>();
    private static final Map<String, Double> bazaarSellCache = new ConcurrentHashMap<>();
    private static final AtomicBoolean isFetchingBazaar = new AtomicBoolean(false);
    private static final AtomicBoolean isFetchingPrices = new AtomicBoolean(false);
    private static final AtomicBoolean isFetchingNpc = new AtomicBoolean(false);
    private static long lastFetchTime = 0;
    private static long lastBazaarFetch = 0;
    private static long lastNpcFetch = 0;
    private static final long CACHE_DURATION = 300000; // 5 minutes in ms
    private static long lastBazaarAttempt = 0;
    private static long lastPricesAttempt = 0;
    private static long lastNpcAttempt = 0;

    public static void ensureLoaded() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        if (!bazaarFresh && now - lastBazaarAttempt < 30000) bazaarFresh = true;

        boolean pricesFresh = !priceCache.isEmpty() && (now - lastFetchTime < CACHE_DURATION);
        if (!pricesFresh && now - lastPricesAttempt < 30000) pricesFresh = true;

        boolean npcFresh = !npcCache.isEmpty() && (now - lastNpcFetch < CACHE_DURATION);
        if (!npcFresh && now - lastNpcAttempt < 30000) npcFresh = true;

        if (!bazaarFresh || !pricesFresh || !npcFresh) {
            reload();
        }
    }

    public static void reload() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        boolean pricesFresh = !priceCache.isEmpty() && (now - lastFetchTime < CACHE_DURATION);
        boolean npcFresh = !npcCache.isEmpty() && (now - lastNpcFetch < CACHE_DURATION);

        if (!bazaarFresh && now - lastBazaarAttempt >= 30000) {
            lastBazaarAttempt = now;
            fetchFromBazaar();
        }
        if (!pricesFresh && now - lastPricesAttempt >= 30000) {
            lastPricesAttempt = now;
            fetchFromPrices();
        }
        if (!npcFresh && now - lastNpcAttempt >= 30000) {
            lastNpcAttempt = now;
            fetchFromNpc();
        }
        BitsManager.ensureLoaded();
    }

    public static String getStatus() {
        long now = System.currentTimeMillis();
        boolean bazaarFresh = now - lastBazaarFetch < CACHE_DURATION;
        boolean pricesFresh = now - lastFetchTime < CACHE_DURATION;
        StringBuilder sb = new StringBuilder("§6API Status:\n");
        sb.append("§7- Prices: ").append(pricesFresh ? "§aFresh" : "§cStale").append(" §8(").append(priceCache.size()).append(" ids)\n");
        sb.append("§7- Bazaar: ").append(bazaarFresh ? "§aFresh" : "§cStale").append(" §8(").append(bazaarCache.size()).append(" ids)\n");
        sb.append("§7- NPC: ").append(!npcCache.isEmpty() && (now - lastNpcFetch < CACHE_DURATION) ? "§aFresh" : "§cStale").append(" §8(").append(npcCache.size()).append(" ids)\n");
        sb.append("§7- Bits: §aLoaded §8(").append(BitsManager.bitCostCache.size()).append(" ids)");
        return sb.toString();
    }

    public static String findIdByName(String name) {
        return findIdByName(name, false);
    }

    public static String findIdByName(String name, boolean isStrict) {
        String search = name.toLowerCase().trim().replace(" ", "_");
        
        if (search.startsWith("e_")) search = "ENCHANTED_" + search.substring(2);
        else if (search.startsWith("enchanted_")) { /* handled */ }
        else if (search.startsWith("s_")) search = "SUPER_" + search.substring(2);
        
        String upperSearch = search.toUpperCase();
        
        if (bazaarCache.containsKey(upperSearch)) return upperSearch;
        if (priceCache.containsKey(upperSearch)) return upperSearch;
        
        if (isStrict) return null;
        
        for (String id : bazaarCache.keySet()) {
            if (id.toLowerCase().contains(search)) return id;
        }
        for (String id : priceCache.keySet()) {
            if (id.toLowerCase().contains(search)) return id;
        }
        
        return null;
    }

    public static long getCachedPrice(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return -1;
        skyblockId = mapEnchantedBookId(skyblockId);
        
        long price = getRawPrice(skyblockId);
        if (price > 0) return price;

        // Handle pet price translation variations
        if (skyblockId.contains(";")) {
            String[] parts = skyblockId.split(";");
            if (parts.length >= 2) {
                String petName = parts[0];
                String rarityPart = parts[1];
                boolean lvl100 = rarityPart.contains("+100");
                if (lvl100) rarityPart = rarityPart.replace("+100", "");
                try {
                    int rarityNum = Integer.parseInt(rarityPart);
                    String rarity = "";
                    if (rarityNum == 0) rarity = "COMMON";
                    else if (rarityNum == 1) rarity = "UNCOMMON";
                    else if (rarityNum == 2) rarity = "RARE";
                    else if (rarityNum == 3) rarity = "EPIC";
                    else if (rarityNum == 4) rarity = "LEGENDARY";
                    else if (rarityNum == 5) rarity = "MYTHIC";
                    
                    if (!rarity.isEmpty()) {
                        String suffix = lvl100 ? "-100" : "";
                        price = getRawPrice("PET-" + petName + "-" + rarity + suffix);
                        if (price > 0) return price;
                        price = getRawPrice("PET_" + petName + "_" + rarity + suffix);
                        if (price > 0) return price;
                        String lvlPrefix = lvl100 ? "LVL_100_" : "LVL_1_";
                        price = getRawPrice(lvlPrefix + rarity + "_" + petName);
                        if (price > 0) return price;
                    }
                } catch (Exception ignored) {}
            }
        }
        
        if (skyblockId.startsWith("PET_") || skyblockId.startsWith("PET-") || skyblockId.contains(";")) {
            boolean lvl100 = skyblockId.endsWith("-100");
            boolean lvl200 = skyblockId.endsWith("-200");
            String baseId = skyblockId;
            if (lvl100 || lvl200) baseId = baseId.substring(0, baseId.length() - 4);
            
            if (baseId.contains(";")) {
                String prefixRemoved = baseId.startsWith("PET-") ? baseId.substring(4) : baseId;
                String[] split = prefixRemoved.split(";");
                if (split.length >= 2) {
                    String petName = split[0];
                    String rarityNum = split[1];
                    String rarity = "COMMON";
                    if (rarityNum.equals("1")) rarity = "UNCOMMON";
                    else if (rarityNum.equals("2")) rarity = "RARE";
                    else if (rarityNum.equals("3")) rarity = "EPIC";
                    else if (rarityNum.equals("4")) rarity = "LEGENDARY";
                    else if (rarityNum.equals("5")) rarity = "MYTHIC";
                    
                    String lvlPrefix = lvl200 ? "LVL_200_" : (lvl100 ? "LVL_100_" : "LVL_1_");
                    price = getRawPrice(lvlPrefix + rarity + "_" + petName);
                    if (price > 0) return price;
                    
                    String odId = "PET-" + petName + "-" + rarity + (lvl200 ? "-200" : (lvl100 ? "-100" : ""));
                    price = getRawPrice(odId);
                    if (price > 0) return price;
                }
            } else {
                String[] parts = baseId.split("[_-]");
                if (parts.length >= 3) {
                    String rarity = parts[parts.length - 1];
                    String petName = baseId.substring(4, baseId.length() - rarity.length() - 1);
                    int rarityNum = 0;
                    if (rarity.equals("UNCOMMON")) rarityNum = 1;
                    else if (rarity.equals("RARE")) rarityNum = 2;
                    else if (rarity.equals("EPIC")) rarityNum = 3;
                    else if (rarity.equals("LEGENDARY")) rarityNum = 4;
                    else if (rarity.equals("MYTHIC")) rarityNum = 5;
                    
                    String suffix = lvl200 ? "+200" : (lvl100 ? "+100" : "");
                    price = getRawPrice(petName + ";" + rarityNum + suffix);
                    if (price > 0) return price;
                    
                    String lvlPrefix = lvl200 ? "LVL_200_" : (lvl100 ? "LVL_100_" : "LVL_1_");
                    price = getRawPrice(lvlPrefix + rarity + "_" + petName);
                    if (price > 0) return price;
                    
                    String odId = "PET-" + petName + "-" + rarity + (lvl200 ? "-200" : (lvl100 ? "-100" : ""));
                    price = getRawPrice(odId);
                    if (price > 0) return price;
                }
            }
        }
        
        if (BomboConfig.get().petPriceDebug && skyblockId.startsWith("PET")) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§d[Pet Price Debug] Tested ID: §f" + skyblockId));
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("§d[Pet Price Debug] Price found: §6" + price));
        }
        
        // Handle ENCHANTMENT_NAME_LEVEL variations
        if (skyblockId.startsWith("ENCHANTMENT_")) {
            String[] parts = skyblockId.split("_");
            if (parts.length >= 3) {
                String level = parts[parts.length - 1];
                String base = skyblockId.substring(0, skyblockId.lastIndexOf("_"));
                
                // Try Roman numerals if Arabic failed
                String[] roman = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
                try {
                    int lvl = Integer.parseInt(level);
                    if (lvl > 0 && lvl < roman.length) {
                        price = getRawPrice(base + "_" + roman[lvl]);
                        if (price > 0) return price;
                    }
                } catch (Exception ignored) {}
            }
        }
        
        // Try fuzzy match as a last resort
        String fuzzy = findIdByName(skyblockId, true);
        if (fuzzy != null && !fuzzy.equals(skyblockId)) {
            return getCachedPrice(fuzzy);
        }
        
        return -1L;
    }

    private static long getRawPrice(String id) {
        if (bazaarCache.containsKey(id)) {
            double price = bazaarCache.get(id);
            if (price <= 0 && bazaarSellCache.containsKey(id)) {
                price = bazaarSellCache.get(id);
            }
            if (price > 0) return Math.round(price);
        }
        if (priceCache.containsKey(id)) return priceCache.get(id);
        if (id.contains(";")) {
            String baseId = id.split(";")[0];
            if (bazaarCache.containsKey(baseId)) {
                double price = bazaarCache.get(baseId);
                if (price <= 0 && bazaarSellCache.containsKey(baseId)) {
                    price = bazaarSellCache.get(baseId);
                }
                if (price > 0) return Math.round(price);
            }
            if (priceCache.containsKey(baseId)) return priceCache.get(baseId);
        }
        return -1L;
    }

    public static boolean isBazaar(String skyblockId) {
        if (skyblockId == null) return false;
        skyblockId = mapEnchantedBookId(skyblockId);
        if (bazaarCache.containsKey(skyblockId)) return true;
        if (skyblockId.contains(";")) {
            return bazaarCache.containsKey(skyblockId.split(";")[0]);
        }
        return false;
    }

    public static CompletableFuture<Long> getLowestBin(String skyblockId) {
        if (skyblockId == null) return CompletableFuture.completedFuture(-1L);
        return CompletableFuture.completedFuture(getCachedPrice(skyblockId));
    }

    public static long getNpcPrice(String skyblockId) {
        if (skyblockId == null) return -1;
        skyblockId = mapEnchantedBookId(skyblockId);
        if (npcCache.containsKey(skyblockId)) return npcCache.get(skyblockId);
        if (skyblockId.contains(";")) {
            String baseId = skyblockId.split(";")[0];
            if (npcCache.containsKey(baseId)) return npcCache.get(baseId);
        }
        return -1L;
    }

    public static long getBuyPrice(String skyblockId) {
        long price = getCachedPrice(skyblockId);
        if (price > 0) return price;
        return 0L;
    }

    public static long getSellPrice(String skyblockId) {
        if (skyblockId == null || skyblockId.isEmpty()) return 0L;
        if (bazaarSellCache.containsKey(skyblockId)) {
            return Math.round(bazaarSellCache.get(skyblockId));
        }
        if (skyblockId.contains(";")) {
            String baseId = skyblockId.split(";")[0];
            if (bazaarSellCache.containsKey(baseId)) {
                return Math.round(bazaarSellCache.get(baseId));
            }
        }
        return getBuyPrice(skyblockId);
    }

    private static CompletableFuture<Boolean> fetchFromBazaar() {
        if (!isFetchingBazaar.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }
        String url = "https://api.hypixel.net/skyblock/bazaar";
        Bomboaddons.logApiRequest(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("success") && json.get("success").getAsBoolean()) {
                                JsonObject products = json.getAsJsonObject("products");
                                for (String key : products.keySet()) {
                                    JsonObject product = products.getAsJsonObject(key);
                                    if (product.has("quick_status")) {
                                        double buyPrice = product.getAsJsonObject("quick_status").get("buyPrice").getAsDouble();
                                        double sellPrice = product.getAsJsonObject("quick_status").get("sellPrice").getAsDouble();
                                        double price = buyPrice > 0 ? buyPrice : sellPrice;
                                        bazaarCache.put(key, price);
                                        bazaarSellCache.put(key, sellPrice);
                                    }
                                }
                                lastBazaarFetch = System.currentTimeMillis();
                                if (BomboConfig.get().debugMode) {
                                    Bomboaddons.sendMessage("§7[Debug] Loaded " + products.size() + " Bazaar prices");
                                }
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> false)
                .whenComplete((res, ex) -> isFetchingBazaar.set(false));
    }

    private static CompletableFuture<Boolean> fetchFromPrices() {
        if (!isFetchingPrices.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }
        return fetchFromUrl("https://api.eliteskyblock.com/resources/auctions/neu")
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://api.odtheking.com/lb/lowestbins");
            })
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://maro.skyblockextras.com/api/auctions/all");
            })
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://bomboapi.frandl938.workers.dev/prices2");
            })
            .thenCompose(success -> {
                if (success) return CompletableFuture.completedFuture(true);
                return fetchFromUrl("https://bomboapi.frandl938.workers.dev/prices");
            })
            .whenComplete((res, ex) -> isFetchingPrices.set(false));
    }

    private static CompletableFuture<Boolean> fetchFromNpc() {
        if (!isFetchingNpc.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false);
        }
        String url = "https://api.hypixel.net/resources/skyblock/items";
        Bomboaddons.logApiRequest(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (json.has("items") && json.get("items").isJsonArray()) {
                                JsonArray dataArray = json.getAsJsonArray("items");
                                int count = 0;
                                for (JsonElement element : dataArray) {
                                    if (!element.isJsonObject()) continue;
                                    JsonObject item = element.getAsJsonObject();
                                    if (item.has("id") && item.has("npc_sell_price")) {
                                        String id = item.get("id").getAsString();
                                        long value = Math.round(item.get("npc_sell_price").getAsDouble());
                                        npcCache.put(id, value);
                                        if (id.contains(";")) {
                                            npcCache.putIfAbsent(id.split(";")[0], value);
                                        }
                                        count++;
                                    }
                                }
                                lastNpcFetch = System.currentTimeMillis();
                                if (BomboConfig.get().debugMode) {
                                    Bomboaddons.sendMessage("§7[Debug] Loaded " + count + " NPC prices");
                                }
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> false)
                .whenComplete((res, ex) -> isFetchingNpc.set(false));
    }

    private static CompletableFuture<Boolean> fetchFromUrl(String url) {
        Bomboaddons.logApiRequest(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Bomboaddons)")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonElement root = JsonParser.parseString(response.body());
                            int count = 0;
                            
                            if (root.isJsonObject()) {
                                JsonObject json = root.getAsJsonObject();
                                
                                // Case 1: {"data": [...]} or {"data": {...}}
                                if (json.has("data")) {
                                    JsonElement data = json.get("data");
                                    if (data.isJsonArray()) {
                                        JsonArray dataArray = data.getAsJsonArray();
                                        for (JsonElement element : dataArray) {
                                            if (element.isJsonObject()) {
                                                JsonObject item = element.getAsJsonObject();
                                                if (item.has("id") && item.has("value")) {
                                                    String id = item.get("id").getAsString();
                                                    long value = Math.round(item.get("value").getAsDouble());
                                                    priceCache.put(id, value);
                                                    if (id.contains(";")) priceCache.putIfAbsent(id.split(";")[0], value);
                                                    count++;
                                                }
                                            }
                                        }
                                    } else if (data.isJsonObject()) {
                                        JsonObject dataObj = data.getAsJsonObject();
                                        for (String key : dataObj.keySet()) {
                                            JsonElement val = dataObj.get(key);
                                            if (val.isJsonPrimitive()) {
                                                long value = Math.round(val.getAsDouble());
                                                priceCache.put(key, value);
                                                if (key.contains(";")) priceCache.putIfAbsent(key.split(";")[0], value);
                                                count++;
                                            }
                                        }
                                    }
                                } 
                                // Case 2: Flat object {"ITEM_ID": price, ...}
                                else {
                                    for (String key : json.keySet()) {
                                        JsonElement val = json.get(key);
                                        if (val.isJsonPrimitive()) {
                                            try {
                                                long value = Math.round(val.getAsDouble());
                                                priceCache.put(key, value);
                                                if (key.contains(";")) priceCache.putIfAbsent(key.split(";")[0], value);
                                                count++;
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }
                            } else if (root.isJsonArray()) {
                                // Case 3: Raw array [...]
                                JsonArray array = root.getAsJsonArray();
                                for (JsonElement element : array) {
                                    if (element.isJsonObject()) {
                                        JsonObject item = element.getAsJsonObject();
                                        if (item.has("id") && item.has("value")) {
                                            String id = item.get("id").getAsString();
                                            long value = Math.round(item.get("value").getAsDouble());
                                            priceCache.put(id, value);
                                            if (id.contains(";")) priceCache.putIfAbsent(id.split(";")[0], value);
                                            count++;
                                        }
                                    }
                                }
                            }

                            if (count > 0) {
                                lastFetchTime = System.currentTimeMillis();
                                if (BomboConfig.get().debugMode) {
                                    Bomboaddons.sendMessage("§7[Debug] Loaded " + count + " prices from " + url);
                                }
                                return true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                }).exceptionally(ex -> {
                    ex.printStackTrace();
                    return false;
                });
    }

    public static String formatPrice(long price) {
        if (price >= 1000000000L) {
            return String.format("%.2fB", (double) price / 1.0E9D);
        } else if (price >= 1000000L) {
            return String.format("%.2fM", (double) price / 1000000.0D);
        } else if (price >= 1000L) {
            return String.format("%.1fK", (double) price / 1000.0D);
        } else {
            return String.valueOf(price);
        }
    }

    public static String formatPrice(double price) {
        if (price >= 1000000000.0) {
            return String.format("%.2fB", price / 1.0E9D);
        } else if (price >= 1000000.0) {
            return String.format("%.2fM", price / 1000000.0D);
        } else if (price >= 1000.0) {
            return String.format("%.1fK", price / 1000.0D);
        } else {
            if (price == (long) price) return String.valueOf((long) price);
            return String.format("%.3f", price);
        }
    }

    public static String mapEnchantedBookId(String skyblockId) {
        if (skyblockId == null) return null;
        
        String encName = null;
        String levelStr = null;
        
        if (skyblockId.contains(";")) {
            String[] parts = skyblockId.split(";");
            if (parts.length == 2) {
                encName = parts[0].toUpperCase();
                levelStr = parts[1];
            }
        } else if (skyblockId.startsWith("ENCHANTMENT_") || skyblockId.startsWith("ENCHANTED_BOOK_")) {
            int prefixLen = skyblockId.startsWith("ENCHANTMENT_") ? 12 : 15;
            int lastUnderscore = skyblockId.lastIndexOf("_");
            if (lastUnderscore > prefixLen) {
                encName = skyblockId.substring(prefixLen, lastUnderscore).toUpperCase();
                levelStr = skyblockId.substring(lastUnderscore + 1);
            }
        }
        
        if (encName != null && levelStr != null) {
                // Fix typos / translation mappings
                if (encName.equals("CHINERA")) encName = "CHIMERA";
                
                // ultimate enchantments list
                java.util.List<String> ultimates = java.util.List.of(
                    "CHIMERA", "ONE_FOR_ALL", "SOUL_EATER", "LEGION", "LAST_STAND",
                    "WISDOM", "BANK", "COMBO", "NO_PAIN_NO_GAIN", "INFERNO",
                    "FATAL_TEMPO", "REND", "FLASH", "JERRY", "HABANERO_TACTICUS", "THE_ONE"
                );
                if (ultimates.contains(encName)) {
                    encName = "ULTIMATE_" + encName;
                }
                
                // Try official Hypixel Bazaar format: ENCHANTMENT_ULTIMATE_CHIMERA_1
                String officialBz = "ENCHANTMENT_" + encName + "_" + levelStr;
                if (bazaarCache.containsKey(officialBz) || priceCache.containsKey(officialBz)) {
                    return officialBz;
                }
                
                // Try candidate with dashes (e.g. ENCHANTED_BOOK-ULTIMATE_CHIMERA-1)
                String candidate = "ENCHANTED_BOOK-" + encName + "-" + levelStr;
                if (bazaarCache.containsKey(candidate) || priceCache.containsKey(candidate)) {
                    return candidate;
                }
                
                // Try roman conversion
                String[] roman = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
                try {
                    int lvl = Integer.parseInt(levelStr);
                    if (lvl > 0 && lvl < roman.length) {
                        String r = roman[lvl];
                        String candBzRoman = "ENCHANTMENT_" + encName + "_" + r;
                        if (bazaarCache.containsKey(candBzRoman) || priceCache.containsKey(candBzRoman)) {
                            return candBzRoman;
                        }
                        String candRoman = "ENCHANTED_BOOK-" + encName + "-" + r;
                        if (bazaarCache.containsKey(candRoman) || priceCache.containsKey(candRoman)) {
                            return candRoman;
                        }
                    }
                } catch (Exception ignored) {}
                
                // Try arabic conversion
                int arabicLvl = me.bombo.bomboaddons.RomanNumber.romanToDecimal(levelStr);
                if (arabicLvl > 0) {
                    String candBzArabic = "ENCHANTMENT_" + encName + "_" + arabicLvl;
                    if (bazaarCache.containsKey(candBzArabic) || priceCache.containsKey(candBzArabic)) {
                        return candBzArabic;
                    }
                    String candArabic = "ENCHANTED_BOOK-" + encName + "-" + arabicLvl;
                    if (bazaarCache.containsKey(candArabic) || priceCache.containsKey(candArabic)) {
                        return candArabic;
                    }
                }
                
                return officialBz;
        }
        return skyblockId;
    }
}
