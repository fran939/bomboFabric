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
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Environment(EnvType.CLIENT)
public class SkyblockItemManager {
    public static class SkyblockItemInfo {
        public final String id;
        public final String name;
        public final String material;
        public final String tier;
        public final String skinValue;
        public final String skinSignature;
        public final String itemModel;
        public final int color;
        public final List<Component> lore;
        public final JsonArray recipes;
        public final boolean vanilla;

        public SkyblockItemInfo(String id, String name, String material, String tier, String skinValue, String skinSignature, int color, String itemModel, List<Component> lore, JsonArray recipes, boolean vanilla) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.tier = tier;
            this.skinValue = skinValue;
            this.skinSignature = skinSignature;
            this.color = color;
            this.itemModel = itemModel;
            this.lore = lore;
            this.recipes = recipes;
            this.vanilla = vanilla;
        }
    }

    private static final Map<String, SkyblockItemInfo> itemCache = new ConcurrentHashMap<>();
    private static final Map<String, List<SkyblockItemInfo>> usageCache = new ConcurrentHashMap<>();
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
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(mapped))
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
        
        net.minecraft.world.item.component.ResolvableProfile cached = profileCache.get(value);
        if (cached != null) return cached;
        
        try {
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
                Path itemsDir = NEUDownloader.ITEMS_DIR;
                if (!Files.exists(itemsDir)) {
                    System.err.println("[BomboAddons] NEU repo not found. Waiting for downloader.");
                    return;
                }

                Map<String, SkyblockItemInfo> tempMap = new ConcurrentHashMap<>();
                Map<String, List<SkyblockItemInfo>> tempUsages = new ConcurrentHashMap<>();

                com.google.gson.JsonObject petNums = null;
                try {
                    Path pnPath = NEUDownloader.REPO_DIR.resolve("constants").resolve("petnums.json");
                    if (Files.exists(pnPath)) {
                        try (FileReader reader = new FileReader(pnPath.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                            petNums = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        }
                    }
                } catch(Exception e) {}
                final com.google.gson.JsonObject finalPetNums = petNums;

                try (java.util.stream.Stream<Path> paths = Files.list(itemsDir)) {
                    paths.parallel().filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (FileReader reader = new FileReader(p.toFile(), java.nio.charset.StandardCharsets.UTF_8)) {
                            JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                            String id = obj.has("internalname") ? obj.get("internalname").getAsString() : "";
                            if (id.isEmpty() && obj.has("itemid") && !obj.get("itemid").getAsString().startsWith("minecraft:")) {
                                id = obj.get("itemid").getAsString();
                            }
                            if (id.isEmpty()) {
                                String fn = p.getFileName().toString();
                                id = fn.substring(0, fn.length() - 5);
                            }
                            
                            String displayname = obj.has("displayname") ? obj.get("displayname").getAsString() : id;
                            if (displayname.contains("{LVL}") && id.contains(";")) {
                                displayname = displayname.replace("{LVL}", "100");
                            }
                            if (displayname.contains("Enchanted Book") && id.contains(";")) {
                                String[] parts = id.split(";");
                                if (parts.length == 2) {
                                    String base = parts[0].replace("_", " ");
                                    base = java.util.Arrays.stream(base.split(" "))
                                        .map(w -> w.length() > 0 ? w.substring(0, 1).toUpperCase() + w.substring(1).toLowerCase() : "")
                                        .collect(java.util.stream.Collectors.joining(" "));
                                    String[] roman = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
                                    String lvlStr = "";
                                    try {
                                        int lvl = Integer.parseInt(parts[1]);
                                        if (lvl > 0 && lvl < roman.length) lvlStr = roman[lvl];
                                        else lvlStr = parts[1];
                                    } catch(Exception e) { lvlStr = parts[1]; }
                                    displayname = "§9" + base + " " + lvlStr;
                                }
                            }
                            
                            String material = "";
                            if (obj.has("material") && !obj.get("material").isJsonNull()) {
                                material = obj.get("material").getAsString();
                            }
                            if (material.isEmpty() && obj.has("itemid") && !obj.get("itemid").isJsonNull()) {
                                String it = obj.get("itemid").getAsString();
                                if (it.startsWith("minecraft:")) it = it.substring(10);
                                material = it;
                            }
                            if (material.isEmpty()) material = "chest";
                            if (material.startsWith("minecraft:")) {
                                material = material.substring(10);
                            }
                            if (material.equals("skull") || material.equals("SKULL_ITEM")) {
                                material = "player_head";
                            }

                            List<Component> lore = new ArrayList<>();
                            String extractedTier = "COMMON";
                            if (obj.has("lore") && obj.get("lore").isJsonArray()) {
                                JsonArray loreArr = obj.getAsJsonArray("lore");
                                for (JsonElement el : loreArr) {
                                    String line = el.getAsString();
                                    lore.add(Component.literal(line));
                                }
                                if (loreArr.size() > 0) {
                                    String lastLine = loreArr.get(loreArr.size() - 1).getAsString().replaceAll("§[0-9a-fk-or]", "");
                                    String[] words = lastLine.split(" ");
                                    if (words.length > 0) {
                                        extractedTier = words[0].toUpperCase();
                                        if (extractedTier.equals("VERY")) extractedTier = "VERY_SPECIAL";
                                    }
                                }
                            }
                            String tier = obj.has("tier") ? obj.get("tier").getAsString() : extractedTier;
                            
                            // Replace stats for pets using petnums.json
                            if (finalPetNums != null && id.contains(";")) {
                                String baseId = id.substring(0, id.indexOf(";"));
                                if (finalPetNums.has(baseId)) {
                                    try {
                                        com.google.gson.JsonObject pObj = finalPetNums.getAsJsonObject(baseId).getAsJsonObject(tier);
                                        if (pObj != null && pObj.has("100")) {
                                            com.google.gson.JsonObject p100 = pObj.getAsJsonObject("100");
                                            com.google.gson.JsonObject statNums = p100.has("statNums") ? p100.getAsJsonObject("statNums") : new com.google.gson.JsonObject();
                                            com.google.gson.JsonArray otherNums = p100.has("otherNums") ? p100.getAsJsonArray("otherNums") : new com.google.gson.JsonArray();
                                            
                                            for (int i = 0; i < lore.size(); i++) {
                                                String line = lore.get(i).getString();
                                                // Replace {STAT}
                                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([A-Z_]+)\\}").matcher(line);
                                                while (m.find()) {
                                                    String stat = m.group(1);
                                                    if (statNums.has(stat)) {
                                                        String val = statNums.get(stat).getAsString();
                                                        if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
                                                        line = line.replace("{" + stat + "}", val);
                                                    }
                                                }
                                                // Replace {0}, {1} etc
                                                java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\\{([0-9]+)\\}").matcher(line);
                                                while (m2.find()) {
                                                    int idx = Integer.parseInt(m2.group(1));
                                                    if (idx >= 0 && idx < otherNums.size()) {
                                                        String val = otherNums.get(idx).getAsString();
                                                        if (val.endsWith(".0")) val = val.substring(0, val.length() - 2);
                                                        line = line.replace("{" + idx + "}", val);
                                                    }
                                                }
                                                lore.set(i, net.minecraft.network.chat.Component.literal(line));
                                            }
                                        }
                                    } catch (Exception e) {}
                                }
                            }

                            String skinValue = null;
                            String skinSignature = null;
                            int color = -1;
                            if (obj.has("nbttag")) {
                                String nbtStr = "";
                                if (obj.get("nbttag").isJsonObject()) {
                                    nbtStr = obj.getAsJsonObject("nbttag").toString();
                                } else if (obj.get("nbttag").isJsonPrimitive()) {
                                    nbtStr = obj.get("nbttag").getAsString();
                                }
                                
                                if (!nbtStr.isEmpty()) {
                                    Matcher m = Pattern.compile("(?i)Value\\\\?\"?\\s*:\\s*\\\\?\"([a-zA-Z0-9+/=]+)\\\\?\"?").matcher(nbtStr);
                                    if (m.find()) {
                                        skinValue = m.group(1);
                                    }
                                    
                                    Matcher colorMatcher = Pattern.compile("color:(\\d+)").matcher(nbtStr);
                                    if (colorMatcher.find()) {
                                        try {
                                            color = Integer.parseInt(colorMatcher.group(1));
                                        } catch (Exception e) {}
                                    }
                                }
                            }

                            if (color == -1 && obj.has("color")) {
                                color = parseColor(obj.get("color").getAsString());
                            }

                            if (skinValue == null && obj.has("texture")) {
                                String tex = obj.get("texture").getAsString();
                                if (tex.length() <= 100 && !tex.contains("{")) {
                                    String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + tex + "\"}}}";
                                    skinValue = java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                } else {
                                    skinValue = tex;
                                }
                            }
                            
                            JsonArray recipes = new JsonArray();
                            if (obj.has("recipe") && obj.get("recipe").isJsonObject()) {
                                recipes.add(obj.getAsJsonObject("recipe"));
                            }
                            if (obj.has("recipes") && obj.get("recipes").isJsonArray()) {
                                recipes.addAll(obj.getAsJsonArray("recipes"));
                            }
                            if (recipes.isEmpty()) recipes = null;

                            boolean isVanilla = obj.has("vanilla") && obj.get("vanilla").getAsBoolean();

                            SkyblockItemInfo info = new SkyblockItemInfo(id, displayname, material, tier, skinValue, skinSignature, color, null, lore, recipes, isVanilla);
                            tempMap.put(id, info);

                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }

                // Build usages and inject mob drops
                for (SkyblockItemInfo info : new ArrayList<>(tempMap.values())) {
                    if (info.recipes != null) {
                        Set<String> inputs = new HashSet<>();
                        for (JsonElement rel : info.recipes) {
                            JsonObject r = rel.getAsJsonObject();
                            
                            if (r.has("type") && "drops".equals(r.get("type").getAsString()) && r.has("drops") && r.get("drops").isJsonArray()) {
                                for (JsonElement dropEl : r.getAsJsonArray("drops")) {
                                    JsonObject dropObj = dropEl.getAsJsonObject();
                                    if (dropObj.has("id")) {
                                        String dropId = dropObj.get("id").getAsString().split(":")[0];
                                        SkyblockItemInfo target = tempMap.get(dropId);
                                        if (target != null) {
                                            JsonObject mobDrop = new JsonObject();
                                            mobDrop.addProperty("type", "mob_drop");
                                            mobDrop.addProperty("mob_id", info.id);
                                            mobDrop.addProperty("mob_name", info.name);
                                            mobDrop.add("all_drops", r.getAsJsonArray("drops"));
                                            
                                            if (target.recipes == null) {
                                                target = new SkyblockItemInfo(target.id, target.name, target.material, target.tier, target.skinValue, target.skinSignature, target.color, target.itemModel, target.lore, new JsonArray(), target.vanilla);
                                                tempMap.put(target.id, target);
                                            }
                                            target.recipes.add(mobDrop);
                                        }
                                    }
                                }
                            }

                            for (String key : new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"}) {
                                if (r.has(key)) {
                                    String val = r.get(key).getAsString();
                                    if (!val.isEmpty()) inputs.add(val.split(":")[0]);
                                }
                            }
                            if (r.has("inputs") && r.get("inputs").isJsonArray()) {
                                for (JsonElement inEl : r.getAsJsonArray("inputs")) {
                                    String val = inEl.getAsString();
                                    if (!val.isEmpty()) inputs.add(val.split(":")[0]);
                                }
                            }
                        }
                        for (String in : inputs) {
                            tempUsages.computeIfAbsent(in, k -> new ArrayList<>()).add(info);
                        }
                    }
                }

                itemCache.clear();
                itemCache.putAll(tempMap);
                usageCache.clear();
                usageCache.putAll(tempUsages);
                loaded = true;
                System.out.println("[BomboAddons] Loaded " + itemCache.size() + " items and " + usageCache.size() + " usages from NEU DB.");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isFetching.set(false);
            }
        });
    }

    public static int parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) return -1;
        try {
            if (colorStr.contains(",")) {
                String[] rgb = colorStr.split(",");
                if (rgb.length == 3) {
                    int r = Integer.parseInt(rgb[0].trim());
                    int g = Integer.parseInt(rgb[1].trim());
                    int b = Integer.parseInt(rgb[2].trim());
                    return (r << 16) | (g << 8) | b;
                }
            } else {
                return Integer.parseInt(colorStr.trim());
            }
        } catch (Exception e) {}
        return -1;
    }

    public static Map<String, SkyblockItemInfo> getAllItems() {
        ensureLoaded();
        return itemCache;
    }

    public static List<SkyblockItemInfo> getUsages(String id) {
        ensureLoaded();
        return usageCache.getOrDefault(id, Collections.emptyList());
    }

    public static String getTierColor(String tier) {
        if (tier == null) return "§f";
        switch (tier.toUpperCase()) {
            case "UNCOMMON": return "§a";
            case "RARE": return "§9";
            case "EPIC": return "§5";
            case "LEGENDARY": return "§6";
            case "MYTHIC": return "§d";
            case "DIVINE": return "§b";
            case "SPECIAL": 
            case "VERY_SPECIAL": return "§c";
            default: return "§f";
        }
    }

    public static int getTierValue(String tier) {
        if (tier == null) return 0;
        switch (tier.toUpperCase()) {
            case "COMMON": return 1;
            case "UNCOMMON": return 2;
            case "RARE": return 3;
            case "EPIC": return 4;
            case "LEGENDARY": return 5;
            case "MYTHIC": return 6;
            case "DIVINE": return 7;
            case "SPECIAL": return 8;
            case "VERY_SPECIAL": return 9;
            default: return 0;
        }
    }

    public static ItemStack createSkyblockItem(String id) {
        if (id == null || id.isEmpty()) return ItemStack.EMPTY;
        
        SkyblockItemInfo info = getInfo(id);
        Item baseItem = Items.STONE;
        String skinValue = null;
        int color = -1;

        if (info != null) {
            skinValue = info.skinValue;
            color = info.color;
            if (info.itemModel != null) {
                String modelId = info.itemModel;
                if (!modelId.contains(":")) {
                    modelId = "minecraft:" + modelId;
                }
                Item override = BuiltInRegistries.ITEM.getOptional(Identifier.parse(modelId))
                        .orElse(null);
                if (override != null && override != Items.AIR) {
                    baseItem = override;
                }
            } else if (info.material != null) {
                SkyblockItemInfo fallbackInfo = itemCache.get(info.material);
                if (fallbackInfo != null) {
                    Item override = getOverrideItem(fallbackInfo.material);
                    if (override != null && override != Items.AIR) {
                        baseItem = override;
                    }
                    if (skinValue == null && fallbackInfo.skinValue != null) {
                        skinValue = fallbackInfo.skinValue;
                    }
                    if (color == -1 && fallbackInfo.color != -1) {
                        color = fallbackInfo.color;
                    }
                } else {
                    Item override = getOverrideItem(info.material);
                    if (override != null && override != Items.AIR) {
                        baseItem = override;
                    }
                }
            }
        }
        
        ItemStack stack = new ItemStack(baseItem);
        
        CompoundTag tag = new CompoundTag();
        CompoundTag ea = new CompoundTag();
        ea.putString("id", id);
        tag.put("ExtraAttributes", ea);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        
        if (info != null && info.name != null && !info.name.isEmpty()) {
            String name = info.name;
            if (!name.startsWith("§")) {
                name = getTierColor(info.tier) + name;
            }
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }

        if (info != null && color != -1) {
            stack.set(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(color));
        }
        
        if (skinValue != null && !skinValue.isEmpty()) {
            net.minecraft.world.item.component.ResolvableProfile rp = createProfile(skinValue, null);
            if (rp != null) {
                stack.set(DataComponents.PROFILE, rp);
            }
        }

        if (info != null && info.lore != null && !info.lore.isEmpty()) {
            stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(info.lore));
        }

        if (skinValue != null) {
            net.minecraft.world.item.component.ResolvableProfile rp = createProfile(skinValue, info != null ? info.skinSignature : null);
            if (rp != null) {
                stack.set(DataComponents.PROFILE, rp);
            }
        }
        
        return stack;
    }

    public static void attachLoreAsync(String id, ItemStack stack) {
        // Pre-loaded.
    }
}
