package me.bombo.bomboaddons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.Bomboaddons;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

public class ProfileFetcher {
    private static final HttpClient CLIENT = HttpClient.newBuilder().build();

    private static final double[] SKILL_XP = {0, 50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425, 9925, 14925, 22425, 32425, 47425, 67425, 97425, 147425, 222425, 322425, 522425, 822425, 1222425, 1722425, 2322425, 3022425, 3822425, 4722425, 5722425, 6822425, 8022425, 9322425, 10722425, 12222425, 13822425, 15522425, 17322425, 19222425, 21222425, 23322425, 25522425, 27822425, 30222425, 32722425, 35322425, 38072425, 40972425, 44072425, 47472425, 51172425, 55172425, 59472425, 64072425, 68972425, 74172425, 79672425, 85472425, 91572425, 97972425, 104672425, 111672425};
    private static final double[] SLAYER_XP = {0, 5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000};
    private static final double[] CATA_XP = {0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 98040, 137040, 191040, 265040, 365040, 500040, 680040, 910040, 1200040, 1550040, 1970040, 2470040, 3070040, 3800040, 4700040, 5800040, 7150040, 8800040, 10800040, 13200040, 16100040, 19600040, 23900040, 29200040, 35700040, 43600040, 53200040, 64800040, 78800040, 95600040, 115600040, 139600040};

    private static int xpToLevel(double xp, double[] table) {
        for (int i = table.length - 1; i >= 0; i--) {
            if (xp >= table[i]) return i;
        }
        return 0;
    }

    private static class CachedProfile {
        ProfileData data;
        long timestamp;
        CachedProfile(ProfileData data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
    private static final Map<String, CachedProfile> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> UUID_CACHE = new ConcurrentHashMap<>();
    private static String pvApiKey = null;
    
    public static CompletableFuture<ProfileData> fetchProfile(String username) {
        String lowerName = username.toLowerCase();
        if (CACHE.containsKey(lowerName)) {
            CachedProfile cached = CACHE.get(lowerName);
            if (System.currentTimeMillis() - cached.timestamp < 60000) {
                return CompletableFuture.completedFuture(cached.data);
            }
        }
        
        if (UUID_CACHE.containsKey(lowerName)) {
            String uuid = UUID_CACHE.get(lowerName);
            return fetchFromPvApi(uuid, username).thenCompose(pvData -> {
                return fetchFromBomboAPI(uuid, username).thenCompose(bomboData -> {
                    if (bomboData == null) return fetchFromSnailifyAPI(uuid, username);
                    return CompletableFuture.completedFuture(bomboData);
                }).thenApply(hypixelData -> {
                    ProfileData merged = hypixelData;
                    if (merged == null) merged = pvData;
                    else if (pvData != null) {
                        merged.networth = pvData.networth;
                        if (pvData.purse > 0) merged.purse = pvData.purse;
                        if (pvData.bank > 0) merged.bank = pvData.bank;
                        merged.foragingWhispers = pvData.foragingWhispers;
                        merged.foragingSpentWhispers = pvData.foragingSpentWhispers;
                        merged.foragingFig = pvData.foragingFig;
                        merged.foragingMangrove = pvData.foragingMangrove;
                        merged.riftVisits = pvData.riftVisits;
                        merged.riftMotes = pvData.riftMotes;
                        merged.riftGrubber = pvData.riftGrubber;
                    }
                    if (merged != null) CACHE.put(username.toLowerCase(), new CachedProfile(merged));
                    return merged;
                });
            });
        }

        String mojangUrl = "https://api.mojang.com/users/profiles/minecraft/" + username;
        Bomboaddons.logApiRequest(mojangUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mojangUrl))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenCompose(res -> {
            if (res.statusCode() != 200) {
                return CompletableFuture.completedFuture(null);
            }
            try {
                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                if (!json.has("id")) return CompletableFuture.completedFuture(null);
                String uuid = json.get("id").getAsString();
                UUID_CACHE.put(lowerName, uuid);
                
                return fetchFromPvApi(uuid, username).thenCompose(pvData -> {
                    return fetchFromBomboAPI(uuid, username).thenCompose(bomboData -> {
                        if (bomboData == null) return fetchFromSnailifyAPI(uuid, username);
                        return CompletableFuture.completedFuture(bomboData);
                    }).thenApply(hypixelData -> {
                        ProfileData merged = hypixelData;
                        if (merged == null) merged = pvData;
                        else if (pvData != null) {
                            merged.networth = pvData.networth;
                            if (pvData.purse > 0) merged.purse = pvData.purse;
                            if (pvData.bank > 0) merged.bank = pvData.bank;
                            merged.foragingWhispers = pvData.foragingWhispers;
                            merged.foragingSpentWhispers = pvData.foragingSpentWhispers;
                            merged.foragingFig = pvData.foragingFig;
                            merged.foragingMangrove = pvData.foragingMangrove;
                            merged.riftVisits = pvData.riftVisits;
                            merged.riftMotes = pvData.riftMotes;
                            merged.riftLifetimeMotes = pvData.riftLifetimeMotes;
                            merged.riftGrubber = pvData.riftGrubber;
                            merged.riftEnigmaSouls = pvData.riftEnigmaSouls;
                            merged.riftDeadCats = pvData.riftDeadCats;
                            merged.riftUnlockedEyes = pvData.riftUnlockedEyes;
                            merged.riftSecondsSitting = pvData.riftSecondsSitting;
                            merged.riftTrophies = pvData.riftTrophies;
                        }
                        if (merged != null) CACHE.put(username.toLowerCase(), new CachedProfile(merged));
                        return merged;
                    }).thenCompose(merged -> {
                        if (merged == null) return CompletableFuture.completedFuture(null);
                        
                        // Fetch Museum Data independently since it doesn't need auth
                        if (!merged.museumWeapons.isEmpty() || !merged.museumArmor.isEmpty() || !merged.museumRarities.isEmpty() || !merged.museumSpecial.isEmpty()) {
                            return CompletableFuture.completedFuture(merged);
                        }
                        
                        HttpRequest mReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://skyblock-pv.thatgravyboat.tech/api/v1/museum/" + merged.profileId))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                            .timeout(Duration.ofSeconds(15))
                            .GET().build();
                        
                        return CLIENT.sendAsync(mReq, HttpResponse.BodyHandlers.ofString()).thenApply(mRes -> {
                            if (mRes.statusCode() == 200) {
                                try {
                                    JsonObject mJson = JsonParser.parseString(mRes.body()).getAsJsonObject();
                                    
                                    merged.museumWeapons.clear();
                                    merged.museumArmor.clear();
                                    merged.museumRarities.clear();
                                    merged.museumSpecial.clear();
                                    
                                    if (mJson.has("members")) {
                                        JsonObject members = mJson.getAsJsonObject("members");
                                        String u = uuid != null ? uuid.replace("-", "") : "";
                                        if (members.has(u)) {
                                            JsonObject memberMuseum = members.getAsJsonObject(u);
                                            parseMuseumData(memberMuseum, merged);
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                            dumpProfileDebug(merged);
                            return merged;
                        });
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
                return CompletableFuture.completedFuture(null);
            }
        }).exceptionally(e -> {
            e.printStackTrace();
            return null;
        });
    }

    private static void dumpProfileDebug(ProfileData data) {
        if (data == null) return;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("=== PROFILE DEBUG LOG ===\n");
            sb.append("Timestamp: ").append(new java.util.Date()).append("\n");
            sb.append("Username: ").append(data.username).append("\n");
            sb.append("Profile Name: ").append(data.profileName).append("\n");
            sb.append("Profile ID: ").append(data.profileId).append("\n");
            sb.append("Purse: ").append(data.purse).append("\n");
            sb.append("Bank: ").append(data.bank).append("\n");
            sb.append("Networth: ").append(data.networth).append("\n");
            sb.append("SkyBlock Level: ").append(data.skyblockLevel).append("\n");
            sb.append("--- SKILLS ---\n");
            sb.append("Farming: ").append(data.farming).append("\n");
            sb.append("Mining: ").append(data.mining).append(" (HotM Exp: ").append(data.hotmExp).append(")\n");
            sb.append("Combat: ").append(data.combat).append("\n");
            sb.append("Foraging: ").append(data.foraging).append(" (Whispers: ").append(data.foragingWhispers).append(", Spent: ").append(data.foragingSpentWhispers).append(", Fig: ").append(data.foragingFig).append(", Mangrove: ").append(data.foragingMangrove).append(")\n");
            sb.append("Fishing: ").append(data.fishing).append("\n");
            sb.append("Enchanting: ").append(data.enchanting).append("\n");
            sb.append("Alchemy: ").append(data.alchemy).append("\n");
            sb.append("Taming: ").append(data.taming).append("\n");
            sb.append("--- RIFT ---\n");
            sb.append("Visits: ").append(data.riftVisits).append(", Motes: ").append(data.riftMotes).append(", Lifetime: ").append(data.riftLifetimeMotes).append("\n");
            sb.append("Grubber: ").append(data.riftGrubber).append(", Enigma: ").append(data.riftEnigmaSouls).append(", DeadCats: ").append(data.riftDeadCats).append(", Eyes: ").append(data.riftUnlockedEyes).append("\n");
            sb.append("Trophies count: ").append(data.riftTrophies.size()).append("\n");
            sb.append("--- MUSEUM ---\n");
            sb.append("Weapons: ").append(data.museumWeapons.size()).append(", Armor: ").append(data.museumArmor.size()).append(", Rarities: ").append(data.museumRarities.size()).append(", Special: ").append(data.museumSpecial.size()).append("\n");
            sb.append("--- CHOCOLATE FACTORY ---\n");
            sb.append("Chocolate: ").append(data.cfChocolate).append(", Total: ").append(data.cfTotalChocolate).append(", Prestige: ").append(data.cfPrestigeLevel).append("\n");
            sb.append("=== END DEBUG LOG ===\n");

            java.nio.file.Files.writeString(
                java.nio.file.Paths.get("bomboaddons_profile_debug.txt"),
                sb.toString(),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception ignored) {}
    }
    public static void parseMuseumData(JsonObject memberMuseum, ProfileData merged) {
        if (memberMuseum.has("items")) {
            JsonObject itemsObj = memberMuseum.getAsJsonObject("items");
            for (java.util.Map.Entry<String, JsonElement> entry : itemsObj.entrySet()) {
                JsonObject itemData = entry.getValue().getAsJsonObject();
                if (itemData.has("items")) {
                    JsonObject innerItems = itemData.getAsJsonObject("items");
                    if (innerItems.has("data")) {
                        java.util.List<net.minecraft.world.item.ItemStack> parsed = me.bombo.bomboaddons.LF.decodeToItems(innerItems.get("data").getAsString());
                        for (net.minecraft.world.item.ItemStack stack : parsed) {
                            if (stack == null || stack.isEmpty()) continue;
                            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
                            if (itemName.contains("helmet") || itemName.contains("chestplate") || itemName.contains("leggings") || itemName.contains("boots") || itemName.contains("armor")) {
                                merged.museumArmor.add(stack);
                            } else if (itemName.contains("sword") || itemName.contains("bow") || itemName.contains("axe") || itemName.contains("weapon")) {
                                merged.museumWeapons.add(stack);
                            } else {
                                merged.museumRarities.add(stack);
                            }
                        }
                    }
                }
            }
        }
        if (memberMuseum.has("special")) {
            com.google.gson.JsonArray specialArr = memberMuseum.getAsJsonArray("special");
            for (JsonElement el : specialArr) {
                if (el.isJsonObject() && el.getAsJsonObject().has("items")) {
                    JsonObject innerItems = el.getAsJsonObject().getAsJsonObject("items");
                    if (innerItems.has("data")) {
                        java.util.List<net.minecraft.world.item.ItemStack> parsed = me.bombo.bomboaddons.LF.decodeToItems(innerItems.get("data").getAsString());
                        for (net.minecraft.world.item.ItemStack stack : parsed) {
                            if (stack != null && !stack.isEmpty()) merged.museumSpecial.add(stack);
                        }
                    }
                }
            }
        }
    }
    
    private static CompletableFuture<String> authenticatePV() {
        if (pvApiKey != null) return CompletableFuture.completedFuture(pvApiKey);
        return CompletableFuture.supplyAsync(() -> {
            try {
                User user = Minecraft.getInstance().getUser();
                String server = java.util.UUID.randomUUID().toString();
                
                com.mojang.authlib.minecraft.MinecraftSessionService sessionService = null;
                for (java.lang.reflect.Method m : Minecraft.class.getMethods()) {
                    if (m.getReturnType().getName().contains("SessionService")) {
                        sessionService = (com.mojang.authlib.minecraft.MinecraftSessionService) m.invoke(Minecraft.getInstance());
                        break;
                    }
                }
                if (sessionService == null) {
                    for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                        if (f.getType().getName().contains("SessionService")) {
                            f.setAccessible(true);
                            sessionService = (com.mojang.authlib.minecraft.MinecraftSessionService) f.get(Minecraft.getInstance());
                            break;
                        }
                    }
                }
                
                if (sessionService != null) {
                    sessionService.joinServer(user.getProfileId(), user.getAccessToken(), server);
                } else {
                    System.out.println("Could not find MinecraftSessionService!");
                }
                
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://skyblock-pv.thatgravyboat.tech/api/v1/authenticate"))
                    .header("User-Agent", "SkyBlockPV/1.0.0/1.21.1")
                    .header("x-minecraft-username", user.getName())
                    .header("x-minecraft-server", server)
                    .GET().build();
                HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    pvApiKey = res.body().replace("\"", "").trim();
                    return pvApiKey;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    private static CompletableFuture<ProfileData> fetchFromPvApi(String uuid, String username) {
        return authenticatePV().thenCompose(key -> {
            if (key == null) return CompletableFuture.completedFuture(null);
            String url = "https://skyblock-pv.thatgravyboat.tech/api/v1/profiles/" + uuid;
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Authorization", key)
                .timeout(Duration.ofSeconds(30)).GET().build();
            return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
                if (res.statusCode() != 200) return null;
                try {
                    // Dump the json for debugging
                    java.nio.file.Files.writeString(
                        java.nio.file.Paths.get("pv_response.json"),
                        res.body(),
                        java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
                    );
                    JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (!root.has("selected")) return null;
                    String selectedId = root.get("selected").getAsString();
                    JsonObject profile = root.getAsJsonObject("profiles").getAsJsonObject(selectedId);
                    
                    ProfileData data = new ProfileData();
                    data.username = username;
                    data.profileId = selectedId;
                    data.profileName = profile.has("cuteName") ? profile.get("cuteName").getAsString() : "Unknown";
                    data.skyblockLevel = profile.has("skyblockLevel") ? profile.get("skyblockLevel").getAsDouble() : 0;
                    
                    if (profile.has("netWorth")) {
                        JsonObject nw = profile.getAsJsonObject("netWorth");
                        if (nw.has("total")) data.networth = nw.get("total").getAsDouble();
                    }
                    
                    if (profile.has("currency")) {
                        JsonObject cur = profile.getAsJsonObject("currency");
                        if (cur.has("purse")) data.purse = cur.get("purse").getAsDouble();
                    }
                    if (profile.has("bank")) {
                        JsonObject bank = profile.getAsJsonObject("bank");
                        if (bank.has("soloBank")) data.bank = bank.get("soloBank").getAsDouble();
                        if (bank.has("profileBank") && data.bank == 0) data.bank = bank.get("profileBank").getAsDouble();
                    }
                    
                    try {
                        if (profile.has("foragingCore") && !profile.get("foragingCore").isJsonNull()) {
                            JsonObject fc = profile.getAsJsonObject("foragingCore");
                            if (fc.has("forests_whispers")) data.foragingWhispers = fc.get("forests_whispers").getAsInt();
                            if (fc.has("forests_whispers_spent")) data.foragingSpentWhispers = fc.get("forests_whispers_spent").getAsInt();
                        }
                        if (profile.has("foraging") && !profile.get("foraging").isJsonNull()) {
                            JsonObject f = profile.getAsJsonObject("foraging");
                            if (f.has("tree_gifts") && !f.get("tree_gifts").isJsonNull()) {
                                JsonObject tg = f.getAsJsonObject("tree_gifts");
                                if (tg.has("FIG")) data.foragingFig = tg.get("FIG").getAsInt();
                                if (tg.has("MANGROVE")) data.foragingMangrove = tg.get("MANGROVE").getAsInt();
                            }
                        }
                        if (profile.has("rift") && !profile.get("rift").isJsonNull()) {
                            JsonObject rift = profile.getAsJsonObject("rift");
                            if (rift.has("member") && !rift.get("member").isJsonNull()) {
                                JsonObject member = rift.getAsJsonObject("member");
                                if (member.has("castle") && !member.get("castle").isJsonNull()) {
                                    JsonObject castle = member.getAsJsonObject("castle");
                                    if (castle.has("grubber_stacks")) data.riftGrubber = castle.get("grubber_stacks").getAsInt();
                                }
                            }
                            if (rift.has("playerStats") && !rift.get("playerStats").isJsonNull()) {
                                JsonObject stats = rift.getAsJsonObject("playerStats");
                                if (stats.has("visits")) data.riftVisits = stats.get("visits").getAsInt();
                                if (stats.has("lifetime_motes_earned")) data.riftLifetimeMotes = stats.get("lifetime_motes_earned").getAsInt();
                            }
                            if (rift.has("currency") && !rift.get("currency").isJsonNull()) {
                                JsonObject currency = rift.getAsJsonObject("currency");
                                if (currency.has("motes_purse")) data.riftMotes = currency.get("motes_purse").getAsInt();
                            }
                            if (rift.has("dead_cat") && !rift.get("dead_cat").isJsonNull()) {
                                JsonObject dc = rift.getAsJsonObject("dead_cat");
                                if (dc.has("found_cats") && !dc.get("found_cats").isJsonNull()) {
                                    data.riftDeadCats = dc.getAsJsonArray("found_cats").size();
                                }
                            }
                            if (rift.has("enigma") && !rift.get("enigma").isJsonNull()) {
                                JsonObject enigma = rift.getAsJsonObject("enigma");
                                if (enigma.has("found_souls") && !enigma.get("found_souls").isJsonNull()) {
                                    data.riftEnigmaSouls = enigma.getAsJsonArray("found_souls").size();
                                }
                            }
                            if (rift.has("eyes_unlocked") && !rift.get("eyes_unlocked").isJsonNull()) {
                                data.riftUnlockedEyes = rift.getAsJsonArray("eyes_unlocked").size();
                            }
                            if (rift.has("village_plaza") && !rift.get("village_plaza").isJsonNull()) {
                                JsonObject vp = rift.getAsJsonObject("village_plaza");
                                if (vp.has("got_supreme_timecharm") && !vp.get("got_supreme_timecharm").isJsonNull() && vp.get("got_supreme_timecharm").getAsBoolean()) {
                                    // time sitting parsing
                                }
                                if (vp.has("timecharms") && !vp.get("timecharms").isJsonNull()) {
                                    JsonObject tc = vp.getAsJsonObject("timecharms");
                                    if (tc.has("supreme") && !tc.get("supreme").isJsonNull()) {
                                        JsonObject sup = tc.getAsJsonObject("supreme");
                                        if (sup.has("seconds_sitting")) data.riftSecondsSitting = sup.get("seconds_sitting").getAsInt();
                                    }
                                }
                            }
                            if (rift.has("timecharms") && !rift.get("timecharms").isJsonNull()) {
                                JsonObject timecharms = rift.getAsJsonObject("timecharms");
                                if (timecharms.has("unlocked") && !timecharms.get("unlocked").isJsonNull()) {
                                    for (JsonElement el : timecharms.getAsJsonArray("unlocked")) {
                                        JsonObject tc = el.getAsJsonObject();
                                        Trophy t = new Trophy();
                                        t.type = tc.has("type") ? tc.get("type").getAsString() : "unknown";
                                        t.visits = tc.has("visits") ? tc.get("visits").getAsInt() : 0;
                                        t.timestamp = tc.has("timestamp") ? tc.get("timestamp").getAsLong() : 0;
                                        data.riftTrophies.add(t);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                    return data;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }).thenCompose(data -> {
                if (data == null || data.profileId == null) return CompletableFuture.completedFuture(null);
                HttpRequest mReq = HttpRequest.newBuilder().uri(URI.create("https://skyblock-pv.thatgravyboat.tech/api/v1/museum/" + data.profileId))
                        .header("Authorization", key)
                        .timeout(Duration.ofSeconds(10)).GET().build();
                return CLIENT.sendAsync(mReq, HttpResponse.BodyHandlers.ofString()).thenApply(mRes -> {
                    if (mRes.statusCode() == 200) {
                        try {
                            JsonObject mRoot = JsonParser.parseString(mRes.body()).getAsJsonObject();
                            if (mRoot.has("members")) {
                                JsonObject members = mRoot.getAsJsonObject("members");
                                String u = uuid.replace("-", "");
                                if (members.has(u)) {
                                    JsonObject member = members.getAsJsonObject(u);
                                    if (member.has("items")) {
                                        JsonObject itemsObj = member.getAsJsonObject("items");
                                        for (java.util.Map.Entry<String, JsonElement> entry : itemsObj.entrySet()) {
                                            JsonObject itemData = entry.getValue().getAsJsonObject();
                                            if (itemData.has("items")) {
                                                JsonObject innerItems = itemData.getAsJsonObject("items");
                                                if (innerItems.has("data")) {
                                                    java.util.List<net.minecraft.world.item.ItemStack> parsed = me.bombo.bomboaddons.LF.decodeToItems(innerItems.get("data").getAsString());
                                                    for (net.minecraft.world.item.ItemStack stack : parsed) {
                                                        if (stack == null || stack.isEmpty()) continue;
                                                        String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
                                                        if (itemName.contains("helmet") || itemName.contains("chestplate") || itemName.contains("leggings") || itemName.contains("boots") || itemName.contains("armor")) {
                                                            data.museumArmor.add(stack);
                                                        } else if (itemName.contains("sword") || itemName.contains("bow") || itemName.contains("axe") || itemName.contains("weapon")) {
                                                            data.museumWeapons.add(stack);
                                                        } else {
                                                            data.museumRarities.add(stack);
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (member.has("special")) {
                                        com.google.gson.JsonArray specialArr = member.getAsJsonArray("special");
                                        for (JsonElement el : specialArr) {
                                            if (el.isJsonObject() && el.getAsJsonObject().has("items")) {
                                                JsonObject innerItems = el.getAsJsonObject().getAsJsonObject("items");
                                                if (innerItems.has("data")) {
                                                    java.util.List<net.minecraft.world.item.ItemStack> parsed = me.bombo.bomboaddons.LF.decodeToItems(innerItems.get("data").getAsString());
                                                    for (net.minecraft.world.item.ItemStack stack : parsed) {
                                                        if (stack != null && !stack.isEmpty()) data.museumSpecial.add(stack);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {}
                    }
                    return data;
                });
            });
        });
    }

    private static CompletableFuture<ProfileData> fetchFromBomboAPI(String uuid, String username) {
        String url = me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl("/" + uuid);
        Bomboaddons.logApiRequest(url);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() != 200) return null;
            try {
                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                if (!json.has("raw_profile")) return null;
                JsonObject rawProfile = json.getAsJsonObject("raw_profile");
                String profileName = json.has("profile") ? json.get("profile").getAsString() : "Unknown";
                ProfileData data = parseProfile(rawProfile, uuid, username, profileName);
                if (data != null && rawProfile.has("museum")) {
                    JsonObject museumData = rawProfile.getAsJsonObject("museum");
                    String u = uuid != null ? uuid.replace("-", "") : "";
                    if (museumData.has(u)) {
                        parseMuseumData(museumData.getAsJsonObject(u), data);
                    }
                }
                return data;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).exceptionally(e -> null);
    }

    private static CompletableFuture<ProfileData> fetchFromSnailifyAPI(String uuid, String username) {
        String url = "https://profile.snailify.workers.dev/?uuid=" + uuid;
        Bomboaddons.logApiRequest(url);
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build();
        return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() != 200) return null;
            try {
                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                if (!json.has("profiles")) return null;
                com.google.gson.JsonArray profiles = json.getAsJsonArray("profiles");
                if (profiles.size() == 0) return null;
                
                JsonObject currentProfile = null;
                for (JsonElement el : profiles) {
                    JsonObject p = el.getAsJsonObject();
                    if (p.has("selected") && p.get("selected").getAsBoolean()) {
                        currentProfile = p;
                        break;
                    }
                }
                if (currentProfile == null) currentProfile = profiles.get(0).getAsJsonObject();
                
                String profileName = currentProfile.has("cute_name") ? currentProfile.get("cute_name").getAsString() : "Unknown";
                return parseProfile(currentProfile, uuid, username, profileName);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).exceptionally(e -> null);
    }

    public static void populateFromMemberData(JsonObject memberData, ProfileData data) {
        if (memberData.has("leveling")) {
            JsonObject leveling = memberData.getAsJsonObject("leveling");
            if (leveling.has("experience")) data.skyblockLevel = leveling.get("experience").getAsDouble() / 100.0;
        }
        
        if (memberData.has("player_data")) {
            JsonObject pData = memberData.getAsJsonObject("player_data");
            if (pData.has("experience")) {
                JsonObject exp = pData.getAsJsonObject("experience");
                data.farming = xpToLevel(getRawXp(exp, "SKILL_FARMING"), SKILL_XP);
                data.mining = xpToLevel(getRawXp(exp, "SKILL_MINING"), SKILL_XP);
                data.combat = xpToLevel(getRawXp(exp, "SKILL_COMBAT"), SKILL_XP);
                data.foraging = xpToLevel(getRawXp(exp, "SKILL_FORAGING"), SKILL_XP);
                data.fishing = xpToLevel(getRawXp(exp, "SKILL_FISHING"), SKILL_XP);
                data.enchanting = xpToLevel(getRawXp(exp, "SKILL_ENCHANTING"), SKILL_XP);
                data.alchemy = xpToLevel(getRawXp(exp, "SKILL_ALCHEMY"), SKILL_XP);
                data.taming = xpToLevel(getRawXp(exp, "SKILL_TAMING"), SKILL_XP);
            }
            
            if (pData.has("rift")) {
                JsonObject pRift = pData.getAsJsonObject("rift");
                if (pRift.has("visits")) data.riftVisits = pRift.get("visits").getAsInt();
                if (pRift.has("lifetime_motes_earned")) data.riftLifetimeMotes = pRift.get("lifetime_motes_earned").getAsInt();
            }
        }
        
        if (memberData.has("currencies")) {
            JsonObject currencies = memberData.getAsJsonObject("currencies");
            if (currencies.has("motes_purse")) data.riftMotes = currencies.get("motes_purse").getAsInt();
        }
        
        if (memberData.has("slayer") && memberData.getAsJsonObject("slayer").has("slayer_bosses")) {
            JsonObject bosses = memberData.getAsJsonObject("slayer").getAsJsonObject("slayer_bosses");
            data.zombieSlayerInfo = parseSlayerInfo(bosses, "zombie");
            data.zombieSlayer = data.zombieSlayerInfo.level;
            
            data.spiderSlayerInfo = parseSlayerInfo(bosses, "spider");
            data.spiderSlayer = data.spiderSlayerInfo.level;

            data.wolfSlayerInfo = parseSlayerInfo(bosses, "wolf");
            data.wolfSlayer = data.wolfSlayerInfo.level;

            data.endermanSlayerInfo = parseSlayerInfo(bosses, "enderman");
            data.endermanSlayer = data.endermanSlayerInfo.level;

            data.blazeSlayerInfo = parseSlayerInfo(bosses, "blaze");
            data.blazeSlayer = data.blazeSlayerInfo.level;

            data.vampireSlayerInfo = parseSlayerInfo(bosses, "vampire");
            data.vampireSlayer = data.vampireSlayerInfo.level;
        }

        if (memberData.has("events")) {
            JsonObject events = memberData.getAsJsonObject("events");
            if (events.has("easter")) {
                JsonObject easter = events.getAsJsonObject("easter");
                if (easter.has("chocolate")) data.cfChocolate = easter.get("chocolate").getAsLong();
                if (easter.has("total_chocolate")) data.cfTotalChocolate = easter.get("total_chocolate").getAsLong();
                if (easter.has("chocolate_since_prestige")) data.cfChocolateSincePrestige = easter.get("chocolate_since_prestige").getAsLong();
                if (easter.has("chocolate_level")) data.cfPrestigeLevel = easter.get("chocolate_level").getAsInt();
                if (easter.has("chocolate_multiplier_upgrades")) data.cfMultiplierUpgrades = easter.get("chocolate_multiplier_upgrades").getAsInt();
            }
        }

        if (memberData.has("rift") && !memberData.get("rift").isJsonNull()) {
            JsonObject rift = memberData.getAsJsonObject("rift");
            
            if (rift.has("village_plaza") && !rift.get("village_plaza").isJsonNull()) {
                JsonObject vp = rift.getAsJsonObject("village_plaza");
                if (vp.has("lonely") && !vp.get("lonely").isJsonNull()) {
                    JsonObject lonely = vp.getAsJsonObject("lonely");
                    if (lonely.has("seconds_sitting")) {
                        data.riftSecondsSitting = lonely.get("seconds_sitting").getAsInt();
                    }
                }
            }
            if (rift.has("dead_cats") && !rift.get("dead_cats").isJsonNull()) {
                JsonObject deadCats = rift.getAsJsonObject("dead_cats");
                if (deadCats.has("found_cats")) {
                    data.riftDeadCats = deadCats.getAsJsonArray("found_cats").size();
                }
            }
            if (rift.has("enigma") && !rift.get("enigma").isJsonNull()) {
                JsonObject enigma = rift.getAsJsonObject("enigma");
                if (enigma.has("found_souls")) {
                    data.riftEnigmaSouls = enigma.getAsJsonArray("found_souls").size();
                }
            }
            if (rift.has("wither_cage") && !rift.get("wither_cage").isJsonNull()) {
                JsonObject witherCage = rift.getAsJsonObject("wither_cage");
                if (witherCage.has("killed_eyes")) {
                    data.riftUnlockedEyes = witherCage.getAsJsonArray("killed_eyes").size();
                }
            }
            if (rift.has("castle") && !rift.get("castle").isJsonNull()) {
                JsonObject castle = rift.getAsJsonObject("castle");
                if (castle.has("grubber_stacks")) {
                    data.riftGrubber = castle.get("grubber_stacks").getAsInt();
                }
            }
            if (rift.has("gallery") && !rift.get("gallery").isJsonNull()) {
                JsonObject gallery = rift.getAsJsonObject("gallery");
                if (gallery.has("secured_trophies")) {
                    for (JsonElement el : gallery.getAsJsonArray("secured_trophies")) {
                        JsonObject obj = el.getAsJsonObject();
                        Trophy t = new Trophy();
                        if (obj.has("type")) t.type = obj.get("type").getAsString();
                        if (obj.has("timestamp")) t.timestamp = obj.get("timestamp").getAsLong();
                        if (obj.has("visits")) t.visits = obj.get("visits").getAsInt();
                        data.riftTrophies.add(t);
                    }
                }
            }
        }

        // Fallbacks for Rift Visits & Lifetime Motes
        if (data.riftLifetimeMotes == 0 && data.riftMotes > 0) {
            data.riftLifetimeMotes = data.riftMotes;
        }
        if (data.riftVisits == 0 && !data.riftTrophies.isEmpty()) {
            int maxV = 0;
            for (Trophy t : data.riftTrophies) {
                if (t.visits > maxV) maxV = t.visits;
            }
            data.riftVisits = maxV;
        }
        
        if (memberData.has("dungeons")) {
            JsonObject dungeons = memberData.getAsJsonObject("dungeons");
            if (dungeons.has("secrets")) data.totalSecrets = dungeons.get("secrets").getAsLong();
            if (dungeons.has("selected_dungeon_class")) data.selectedDungeonClass = dungeons.get("selected_dungeon_class").getAsString();
            
            if (dungeons.has("player_classes")) {
                JsonObject pClasses = dungeons.getAsJsonObject("player_classes");
                for (java.util.Map.Entry<String, JsonElement> entry : pClasses.entrySet()) {
                    if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has("experience")) {
                        double exp = entry.getValue().getAsJsonObject().get("experience").getAsDouble();
                        data.classXpMap.put(entry.getKey(), exp);
                        data.classLevelMap.put(entry.getKey(), xpToLevel(exp, CATA_XP));
                    }
                }
            }
            
            if (dungeons.has("dungeon_types")) {
                JsonObject dTypes = dungeons.getAsJsonObject("dungeon_types");
                if (dTypes.has("catacombs") && dTypes.get("catacombs").isJsonObject()) {
                    JsonObject cata = dTypes.getAsJsonObject("catacombs");
                    if (cata.has("experience")) {
                        data.catacombsXp = cata.get("experience").getAsDouble();
                        data.catacombs = xpToLevel(data.catacombsXp, CATA_XP);
                    }
                    if (cata.has("tier_completions") && cata.get("tier_completions").isJsonObject()) {
                        JsonObject comps = cata.getAsJsonObject("tier_completions");
                        for (java.util.Map.Entry<String, JsonElement> e : comps.entrySet()) {
                            try {
                                int floor = Integer.parseInt(e.getKey());
                                data.normalFloorCompletions.put(floor, e.getValue().getAsInt());
                            } catch (Exception ignored) {}
                        }
                    }
                }
                if (dTypes.has("master_catacombs") && dTypes.get("master_catacombs").isJsonObject()) {
                    JsonObject mcata = dTypes.getAsJsonObject("master_catacombs");
                    if (mcata.has("tier_completions") && mcata.get("tier_completions").isJsonObject()) {
                        JsonObject comps = mcata.getAsJsonObject("tier_completions");
                        for (java.util.Map.Entry<String, JsonElement> e : comps.entrySet()) {
                            try {
                                int floor = Integer.parseInt(e.getKey());
                                data.masterFloorCompletions.put(floor, e.getValue().getAsInt());
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
        }
        
        if (memberData.has("mining_core")) {
            JsonObject mc = memberData.getAsJsonObject("mining_core");
            if (mc.has("experience")) data.hotmExp = mc.get("experience").getAsDouble();
            if (mc.has("nodes")) {
                JsonObject nodesObj = mc.getAsJsonObject("nodes");
                if (nodesObj.has("mining_core") && nodesObj.get("mining_core").isJsonObject()) {
                    nodesObj = nodesObj.getAsJsonObject("mining_core");
                }
                for (java.util.Map.Entry<String, JsonElement> entry : nodesObj.entrySet()) {
                    try {
                        if (entry.getValue().isJsonPrimitive()) {
                            data.hotmNodes.put(entry.getKey(), entry.getValue().getAsInt());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        if (memberData.has("collection")) {
            data.totalCollections = memberData.getAsJsonObject("collection").size();
        }
        
        // Parse NBT Inventories
        JsonObject inventory = memberData.has("inventory") ? memberData.getAsJsonObject("inventory") : new JsonObject();
        JsonObject petsData = memberData.has("pets_data") ? memberData.getAsJsonObject("pets_data") : new JsonObject();
        
        if (inventory.has("inv_contents")) {
            JsonObject inv = inventory.getAsJsonObject("inv_contents");
            if (inv.has("data")) data.inventory = me.bombo.bomboaddons.LF.decodeToItems(inv.get("data").getAsString());
        }
        if (inventory.has("ender_chest_contents")) {
            JsonObject ec = inventory.getAsJsonObject("ender_chest_contents");
            if (ec.has("data")) data.enderChest = me.bombo.bomboaddons.LF.decodeToItems(ec.get("data").getAsString());
        }
        if (inventory.has("wardrobe_contents")) {
            JsonObject wd = inventory.getAsJsonObject("wardrobe_contents");
            if (wd.has("data")) data.wardrobe = me.bombo.bomboaddons.LF.decodeToItems(wd.get("data").getAsString());
        } else {
            JsonObject sharedInv = memberData.has("shared_inventory") ? memberData.getAsJsonObject("shared_inventory") : new JsonObject();
            if (sharedInv.has("wardrobe_contents")) {
                JsonObject wd = sharedInv.getAsJsonObject("wardrobe_contents");
                if (wd.has("data")) data.wardrobe = me.bombo.bomboaddons.LF.decodeToItems(wd.get("data").getAsString());
            } else if (memberData.has("wardrobe_contents")) {
                JsonObject wd = memberData.getAsJsonObject("wardrobe_contents");
                if (wd.has("data")) data.wardrobe = me.bombo.bomboaddons.LF.decodeToItems(wd.get("data").getAsString());
            }
        }
        if (inventory.has("personal_vault_contents")) {
            JsonObject pv = inventory.getAsJsonObject("personal_vault_contents");
            if (pv.has("data")) data.personalVault = me.bombo.bomboaddons.LF.decodeToItems(pv.get("data").getAsString());
        }
        if (inventory.has("inv_armor")) {
            JsonObject inv = inventory.getAsJsonObject("inv_armor");
            if (inv.has("data")) data.armor = me.bombo.bomboaddons.LF.decodeToItems(inv.get("data").getAsString());
        }
        if (inventory.has("equipment_contents")) {
            JsonObject eq = inventory.getAsJsonObject("equipment_contents");
            if (eq.has("data")) data.equipment = me.bombo.bomboaddons.LF.decodeToItems(eq.get("data").getAsString());
        }
        if (inventory.has("bag_contents")) {
            JsonObject bag = inventory.getAsJsonObject("bag_contents");
            if (bag.has("talisman_bag")) {
                JsonObject acc = bag.getAsJsonObject("talisman_bag");
                if (acc.has("data")) data.accessories = me.bombo.bomboaddons.LF.decodeToItems(acc.get("data").getAsString());
            }
            if (bag.has("potion_bag")) {
                JsonObject obj = bag.getAsJsonObject("potion_bag");
                if (obj.has("data")) data.potionBag = me.bombo.bomboaddons.LF.decodeToItems(obj.get("data").getAsString());
            }
            if (bag.has("fishing_bag")) {
                JsonObject obj = bag.getAsJsonObject("fishing_bag");
                if (obj.has("data")) data.fishingBag = me.bombo.bomboaddons.LF.decodeToItems(obj.get("data").getAsString());
            }
            if (bag.has("quiver")) {
                JsonObject obj = bag.getAsJsonObject("quiver");
                if (obj.has("data")) data.quiver = me.bombo.bomboaddons.LF.decodeToItems(obj.get("data").getAsString());
            }
        }
        if (inventory.has("candy_inventory_contents")) {
            JsonObject candy = inventory.getAsJsonObject("candy_inventory_contents");
            if (candy.has("data")) data.candyBag = me.bombo.bomboaddons.LF.decodeToItems(candy.get("data").getAsString());
        }
        if (inventory.has("backpack_contents")) {
            JsonObject bp = inventory.getAsJsonObject("backpack_contents");
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : bp.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject page = entry.getValue().getAsJsonObject();
                    if (page.has("data")) {
                        try {
                            int idx = Integer.parseInt(entry.getKey());
                            data.backpacks.put(idx, me.bombo.bomboaddons.LF.decodeToItems(page.get("data").getAsString()));
                        } catch (Exception e) {}
                    }
                }
            }
        }
        if (petsData.has("pets") && petsData.get("pets").isJsonArray()) {
            for (com.google.gson.JsonElement el : petsData.getAsJsonArray("pets")) {
                if (el.isJsonObject()) {
                    JsonObject petObj = el.getAsJsonObject();
                    Pet pet = new Pet();
                    pet.type = petObj.has("type") ? petObj.get("type").getAsString() : "Unknown";
                    pet.tier = petObj.has("tier") ? petObj.get("tier").getAsString() : "COMMON";
                    pet.active = petObj.has("active") && petObj.get("active").getAsBoolean();
                    pet.exp = petObj.has("exp") ? petObj.get("exp").getAsDouble() : 0.0;
                    data.pets.add(pet);
                }
            }
        }
    }

    private static ProfileData parseProfile(JsonObject profileObject, String uuid, String username, String profileName) {
        if (!profileObject.has("members")) return null;
        JsonObject members = profileObject.getAsJsonObject("members");
        
        JsonObject memberData = null;
        for (Map.Entry<String, JsonElement> entry : members.entrySet()) {
            if (entry.getKey().replace("-", "").equalsIgnoreCase(uuid.replace("-", ""))) {
                memberData = entry.getValue().getAsJsonObject();
                break;
            }
        }
        
        if (memberData == null) return null;
        
        ProfileData data = new ProfileData();
        data.username = username;
        data.profileName = profileName;
        data.profileId = profileObject.has("profile_id") ? profileObject.get("profile_id").getAsString() : null;
        
        if (profileObject.has("currencies")) {
            JsonObject currencies = profileObject.getAsJsonObject("currencies");
            if (currencies.has("coin_purse")) data.purse = currencies.get("coin_purse").getAsDouble();
        }
        if (profileObject.has("banking")) {
            JsonObject banking = profileObject.getAsJsonObject("banking");
            if (banking.has("balance")) data.bank = banking.get("balance").getAsDouble();
        }
        
        populateFromMemberData(memberData, data);
        
        data.networth = 0;
        return data;
    }

    private static double getRawXp(JsonObject exp, String skillName) {
        if (exp.has(skillName)) {
            return exp.get(skillName).getAsDouble();
        }
        return 0;
    }

    public static class SlayerInfo {
        public double xp = 0;
        public int level = 0;
        public java.util.Map<Integer, Integer> kills = new java.util.HashMap<>();
    }

    private static SlayerInfo parseSlayerInfo(JsonObject bosses, String bossName) {
        SlayerInfo info = new SlayerInfo();
        if (bosses.has(bossName)) {
            JsonObject boss = bosses.getAsJsonObject(bossName);
            if (boss.has("xp")) {
                info.xp = boss.get("xp").getAsDouble();
                info.level = xpToLevel(info.xp, SLAYER_XP);
            }
            for (java.util.Map.Entry<String, JsonElement> entry : boss.entrySet()) {
                if (entry.getKey().startsWith("boss_kills_tier_")) {
                    try {
                        int tier = Integer.parseInt(entry.getKey().replace("boss_kills_tier_", "")) + 1;
                        info.kills.put(tier, entry.getValue().getAsInt());
                    } catch (Exception ignored) {}
                }
            }
        }
        return info;
    }

    private static double getSlayerXp(JsonObject bosses, String bossName) {
        if (bosses.has(bossName)) {
            JsonObject boss = bosses.getAsJsonObject(bossName);
            if (boss.has("xp")) {
                return boss.get("xp").getAsDouble();
            }
        }
        return 0;
    }

    public static class ProfileData {
        public String username;
        public String profileName;
        public String profileId;
        public double purse;
        public double bank;
        public double skyblockLevel;
        public double taming = 0;
        public java.util.Map<String, Integer> hotmNodes = new java.util.HashMap<>();
        
        public int foragingWhispers = 0;
        public int foragingSpentWhispers = 0;
        public int foragingFig = 0;
        public int foragingMangrove = 0;
        
        public int riftVisits = 0;
        public int riftMotes = 0;
        public int riftLifetimeMotes = 0;
        public int riftGrubber = 0;
        public int riftEnigmaSouls = 0;
        public int riftDeadCats = 0;
        public int riftUnlockedEyes = 0;
        public int riftSecondsSitting = 0;
        public java.util.List<Trophy> riftTrophies = new java.util.ArrayList<>();
        
        public long cfChocolate = 0;
        public long cfTotalChocolate = 0;
        public long cfChocolateSincePrestige = 0;
        public int cfPrestigeLevel = 0;
        public int cfMultiplierUpgrades = 0;
        
        public double networth;
        
        public SlayerInfo zombieSlayerInfo = new SlayerInfo();
        public SlayerInfo spiderSlayerInfo = new SlayerInfo();
        public SlayerInfo wolfSlayerInfo = new SlayerInfo();
        public SlayerInfo endermanSlayerInfo = new SlayerInfo();
        public SlayerInfo blazeSlayerInfo = new SlayerInfo();
        public SlayerInfo vampireSlayerInfo = new SlayerInfo();
        
        public int farming, mining, combat, foraging, fishing, enchanting, alchemy;
        public int zombieSlayer, spiderSlayer, wolfSlayer, endermanSlayer, blazeSlayer, vampireSlayer;
        public int catacombs;
        public double catacombsXp;
        public long totalSecrets;
        public String selectedDungeonClass = "None";
        public java.util.Map<String, Double> classXpMap = new java.util.HashMap<>();
        public java.util.Map<String, Integer> classLevelMap = new java.util.HashMap<>();
        public java.util.Map<Integer, Integer> normalFloorCompletions = new java.util.TreeMap<>();
        public java.util.Map<Integer, Integer> masterFloorCompletions = new java.util.TreeMap<>();
        
        public double hotmExp;
        public int totalCollections;

        public java.util.List<net.minecraft.world.item.ItemStack> inventory = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> enderChest = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> wardrobe = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> personalVault = new java.util.ArrayList<>();
        
        public java.util.List<net.minecraft.world.item.ItemStack> armor = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> equipment = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> accessories = new java.util.ArrayList<>();
        public java.util.Map<Integer, java.util.List<net.minecraft.world.item.ItemStack>> backpacks = new java.util.TreeMap<>();
        public java.util.List<Pet> pets = new java.util.ArrayList<>();
        
        public java.util.List<net.minecraft.world.item.ItemStack> potionBag = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> fishingBag = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> quiver = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> candyBag = new java.util.ArrayList<>();
        
        public java.util.List<net.minecraft.world.item.ItemStack> museumWeapons = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> museumArmor = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> museumRarities = new java.util.ArrayList<>();
        public java.util.List<net.minecraft.world.item.ItemStack> museumSpecial = new java.util.ArrayList<>();
    }

    public static class Pet {
        public String type;
        public String tier;
        public boolean active;
        public double exp;
    }

    public static class Trophy {
        public String type;
        public int visits;
        public long timestamp;
    }
}

