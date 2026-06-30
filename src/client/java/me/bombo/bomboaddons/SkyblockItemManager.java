package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class SkyblockItemManager {
    public static class SkyblockItemInfo {
        public final String id;
        public final String material;
        public final String skinValue;
        public final String skinSignature;

        public SkyblockItemInfo(String id, String material, String skinValue, String skinSignature) {
            this.id = id;
            this.material = material;
            this.skinValue = skinValue;
            this.skinSignature = skinSignature;
        }
    }

    private static final HttpClient client = HttpClient.newBuilder().build();
    private static final Map<String, SkyblockItemInfo> itemCache = new ConcurrentHashMap<>();
    private static final AtomicBoolean isFetching = new AtomicBoolean(false);
    private static boolean loaded = false;

    public static void ensureLoaded() {
        if (!loaded && !isFetching.get()) {
            loadAsync();
        }
    }

    public static SkyblockItemInfo getInfo(String id) {
        ensureLoaded();
        return itemCache.get(id);
    }

    public static Item getOverrideItem(String material) {
        if (material == null || material.isEmpty()) return null;
        String mapped = LF.guessItem(material);
        if (mapped == null) {
            mapped = "minecraft:" + material.toLowerCase();
        }
        return BuiltInRegistries.ITEM.get(Identifier.parse(mapped))
                .map(net.minecraft.core.Holder::value)
                .orElse(null);
    }

    public static net.minecraft.core.Holder<Item> getOverrideItemHolder(String material) {
        if (material == null || material.isEmpty()) return null;
        String mapped = LF.guessItem(material);
        if (mapped == null) {
            mapped = "minecraft:" + material.toLowerCase();
        }
        return BuiltInRegistries.ITEM.get(Identifier.parse(mapped))
                .orElse(null);
    }

    public static com.mojang.authlib.properties.PropertyMap createMutablePropertyMap() {
        try {
            com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> dummy = 
                com.google.common.collect.LinkedHashMultimap.create();
            com.mojang.authlib.properties.PropertyMap pm = new com.mojang.authlib.properties.PropertyMap(dummy);
            try {
                java.lang.reflect.Field propField = com.mojang.authlib.properties.PropertyMap.class.getDeclaredField("properties");
                propField.setAccessible(true);
                propField.set(pm, com.google.common.collect.LinkedHashMultimap.create());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return pm;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static final Map<String, net.minecraft.world.item.component.ResolvableProfile> profileCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static net.minecraft.world.item.component.ResolvableProfile createProfile(String value, String signature) {
        if (value == null || value.isEmpty()) return null;
        
        // Cache by skin value to avoid recreating and re-downloading constantly
        net.minecraft.world.item.component.ResolvableProfile cached = profileCache.get(value);
        if (cached != null) return cached;
        
        try {
            // Generate a consistent UUID from the skin value
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            com.mojang.authlib.properties.PropertyMap props = createMutablePropertyMap();
            if (props != null) {
                props.put("textures", new com.mojang.authlib.properties.Property("textures", value));
            }
            
            com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(uuid, "", props);
            net.minecraft.world.item.component.ResolvableProfile rp = net.minecraft.world.item.component.ResolvableProfile.createResolved(profile);
            if (rp != null) {
                profileCache.put(value, rp);
                return rp;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void loadAsync() {
        if (!isFetching.compareAndSet(false, true)) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                // 1. Try local file first
                File localFile = new File("E:\\Users\\frand\\Videos\\hypixelitems.json");
                if (localFile.exists()) {
                    System.out.println("[BomboAddons] Loading Skyblock items from local file: " + localFile.getAbsolutePath());
                    try (FileReader reader = new FileReader(localFile)) {
                        JsonElement element = JsonParser.parseReader(reader);
                        JsonArray array = null;
                        if (element.isJsonArray()) {
                            array = element.getAsJsonArray();
                        } else if (element.isJsonObject() && element.getAsJsonObject().has("items")) {
                            array = element.getAsJsonObject().getAsJsonArray("items");
                        }
                        if (array != null) {
                            parseAndLoadItems(array);
                            loaded = true;
                            System.out.println("[BomboAddons] Loaded " + itemCache.size() + " items from local file.");
                            return;
                        }
                    } catch (Exception ex) {
                        System.err.println("[BomboAddons] Failed to load local items file, falling back to API: " + ex.getMessage());
                    }
                }

                // 2. Fallback to web API
                System.out.println("[BomboAddons] Fetching Skyblock items list from API for client-side model override...");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://bomboapi.frandl938.workers.dev/hyp/resources/skyblock/items"))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonElement element = JsonParser.parseString(response.body());
                    JsonArray array = null;
                    if (element.isJsonArray()) {
                        array = element.getAsJsonArray();
                    } else if (element.isJsonObject() && element.getAsJsonObject().has("items")) {
                        array = element.getAsJsonObject().getAsJsonArray("items");
                    }
                    if (array != null) {
                        parseAndLoadItems(array);
                        loaded = true;
                        System.out.println("[BomboAddons] Loaded " + itemCache.size() + " Skyblock items from API.");
                    } else {
                        System.err.println("[BomboAddons] API response format was invalid (missing items array).");
                    }
                } else {
                    System.err.println("[BomboAddons] Failed to load Skyblock items from API: HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                System.err.println("[BomboAddons] Error loading Skyblock items list:");
                e.printStackTrace();
            } finally {
                isFetching.set(false);
            }
        });
    }

    private static void parseAndLoadItems(JsonArray array) {
        Map<String, SkyblockItemInfo> tempMap = new ConcurrentHashMap<>();
        for (JsonElement el : array) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                String id = obj.has("id") ? obj.get("id").getAsString() : "";
                if (id.isEmpty()) continue;
                String material = obj.has("material") ? obj.get("material").getAsString() : "";
                String skinValue = null;
                String skinSignature = null;
                if (obj.has("skin") && obj.get("skin").isJsonObject()) {
                    JsonObject skinObj = obj.getAsJsonObject("skin");
                    skinValue = skinObj.has("value") ? skinObj.get("value").getAsString() : null;
                    skinSignature = skinObj.has("signature") ? skinObj.get("signature").getAsString() : null;
                }
                tempMap.put(id, new SkyblockItemInfo(id, material, skinValue, skinSignature));
            }
        }
        itemCache.clear();
        itemCache.putAll(tempMap);
    }
}
