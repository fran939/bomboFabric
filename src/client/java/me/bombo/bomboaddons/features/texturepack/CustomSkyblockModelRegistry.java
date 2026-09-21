package me.bombo.bomboaddons.features.texturepack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class CustomSkyblockModelRegistry {
    public static class OverrideRule {
        public Identifier targetModel;
        public Pattern displayNamePattern;
        public Pattern lorePattern;
        public Map<String, Pattern> extraAttributesPatterns = new HashMap<>();

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return false;

            // 1. Display Name Pattern
            if (displayNamePattern != null) {
                String plainName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                if (plainName == null || !displayNamePattern.matcher(plainName).find()) {
                    return false;
                }
            }

            // 2. Extra Attributes Patterns
            if (!extraAttributesPatterns.isEmpty()) {
                CompoundTag extra = SkyblockUtils.getExtraAttributes(stack);
                if (extra == null) return false;

                for (Map.Entry<String, Pattern> entry : extraAttributesPatterns.entrySet()) {
                    String key = entry.getKey();
                    Pattern pat = entry.getValue();

                    if (!extra.contains(key)) return false;
                    String val = extra.getString(key).orElse("");
                    if (!pat.matcher(val).find()) {
                        return false;
                    }
                }
            }

            // 3. Lore Pattern
            if (lorePattern != null) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore == null) return false;
                boolean matched = false;
                for (net.minecraft.network.chat.Component line : lore.lines()) {
                    String plainLine = ChatFormatting.stripFormatting(line.getString());
                    if (plainLine != null && lorePattern.matcher(plainLine).find()) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }

            return true;
        }
    }

    // BaseModelId -> List of OverrideRules (volatile atomic references to ConcurrentHashMap)
    private static volatile Map<Identifier, List<OverrideRule>> OVERRIDES = new ConcurrentHashMap<>();
    private static volatile Map<String, List<OverrideRule>> SIMPLE_NAME_OVERRIDES = new ConcurrentHashMap<>();
    public static final Map<String, Identifier> SIMPLE_MODELS = new ConcurrentHashMap<>();

    public static void clear() {
        OVERRIDES = new ConcurrentHashMap<>();
        SIMPLE_NAME_OVERRIDES = new ConcurrentHashMap<>();
        SIMPLE_MODELS.clear();
    }

    public static void registerSimpleModel(String simpleName, Identifier modelId) {
        if (simpleName != null && modelId != null) {
            SIMPLE_MODELS.put(simpleName.toLowerCase(Locale.ROOT), modelId);
        }
    }

    public static void loadOverrides(ResourceManager resourceManager) {
        Map<Identifier, List<OverrideRule>> tempOverrides = new ConcurrentHashMap<>();
        Map<String, List<OverrideRule>> tempSimpleOverrides = new ConcurrentHashMap<>();

        try {
            Map<Identifier, Resource> resources = resourceManager.listResources(
                    "models/item",
                    id -> !id.getNamespace().equals("minecraft") && !id.getNamespace().equals("realms")
                            && id.getPath().endsWith(".json")
            );

            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier fileId = entry.getKey();
                String path = fileId.getPath();
                if (!path.startsWith("models/item/") || !path.endsWith(".json")) continue;

                String subPath = path.substring("models/item/".length(), path.length() - ".json".length());
                Identifier baseModelId = Identifier.fromNamespaceAndPath(fileId.getNamespace(), subPath);

                try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                    if (!root.has("overrides") || !root.get("overrides").isJsonArray()) continue;

                    JsonArray overridesArr = root.getAsJsonArray("overrides");
                    List<OverrideRule> ruleList = new CopyOnWriteArrayList<>();

                    for (JsonElement elem : overridesArr) {
                        if (!elem.isJsonObject()) continue;
                        JsonObject oObj = elem.getAsJsonObject();
                        if (!oObj.has("model") || !oObj.has("predicate")) continue;

                        String targetModelStr = oObj.get("model").getAsString();
                        Identifier targetModelId;
                        if (targetModelStr.contains(":")) {
                            String[] parts = targetModelStr.split(":", 2);
                            String targetPath = parts[1];
                            if (targetPath.startsWith("item/")) targetPath = targetPath.substring("item/".length());
                            targetModelId = Identifier.fromNamespaceAndPath(parts[0], targetPath);
                        } else {
                            String targetPath = targetModelStr;
                            if (targetPath.startsWith("item/")) targetPath = targetPath.substring("item/".length());
                            targetModelId = Identifier.fromNamespaceAndPath(fileId.getNamespace(), targetPath);
                        }

                        JsonObject predObj = oObj.getAsJsonObject("predicate");
                        OverrideRule rule = new OverrideRule();
                        rule.targetModel = targetModelId;

                        // Parse display name predicate
                        if (predObj.has("firmament:display_name")) {
                            JsonElement dnElem = predObj.get("firmament:display_name");
                            rule.displayNamePattern = parseStringOrRegex(dnElem);
                        }

                        // Parse extra attributes predicate
                        if (predObj.has("firmament:extra_attributes")) {
                            JsonElement eaElem = predObj.get("firmament:extra_attributes");
                            if (eaElem.isJsonObject()) {
                                JsonObject eaObj = eaElem.getAsJsonObject();
                                for (String key : eaObj.keySet()) {
                                    rule.extraAttributesPatterns.put(key, parseStringOrRegex(eaObj.get(key)));
                                }
                            }
                        }

                        // Parse lore predicate
                        if (predObj.has("firmament:lore")) {
                            JsonElement loreElem = predObj.get("firmament:lore");
                            rule.lorePattern = parseStringOrRegex(loreElem);
                        }

                        ruleList.add(rule);
                    }

                    if (!ruleList.isEmpty()) {
                        tempOverrides.put(baseModelId, ruleList);
                        if (!baseModelId.getPath().startsWith("item/")) {
                            tempOverrides.put(Identifier.fromNamespaceAndPath(baseModelId.getNamespace(), "item/" + baseModelId.getPath()), ruleList);
                        }

                        // Pre-index by simple name for thread-safe O(1) lookups during rendering
                        String bPath = baseModelId.getPath().toLowerCase(Locale.ROOT);
                        if (bPath.startsWith("item/")) bPath = bPath.substring(5);
                        int lastSlash = bPath.lastIndexOf('/');
                        String baseName = (lastSlash != -1) ? bPath.substring(lastSlash + 1) : bPath;
                        tempSimpleOverrides.computeIfAbsent(baseName, k -> new CopyOnWriteArrayList<>()).addAll(ruleList);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("[BomboAddons] Failed to load custom model overrides: " + e.getMessage());
        }

        // Atomic swap of references: render thread never encounters half-loaded or cleared collections
        OVERRIDES = tempOverrides;
        SIMPLE_NAME_OVERRIDES = tempSimpleOverrides;
    }

    private static Pattern parseStringOrRegex(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return Pattern.compile(".*");
        if (elem.isJsonPrimitive()) {
            return Pattern.compile("(?i)" + Pattern.quote(elem.getAsString()));
        }
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("regex")) {
                return Pattern.compile("(?i)" + obj.get("regex").getAsString());
            }
            if (obj.has("string")) {
                return parseStringOrRegex(obj.get("string"));
            }
            if (obj.has("equals")) {
                return Pattern.compile("(?i)^" + Pattern.quote(obj.get("equals").getAsString()) + "$");
            }
        }
        return Pattern.compile(".*");
    }

    public static Identifier resolveOverride(Identifier baseModelId, ItemStack stack) {
        try {
            if (baseModelId == null || stack == null || stack.isEmpty()) return null;
            Map<Identifier, List<OverrideRule>> map = OVERRIDES;
            List<OverrideRule> rules = map.get(baseModelId);
            if (rules == null || rules.isEmpty()) {
                if (baseModelId.getPath().startsWith("item/")) {
                    rules = map.get(Identifier.fromNamespaceAndPath(baseModelId.getNamespace(), baseModelId.getPath().substring("item/".length())));
                } else {
                    rules = map.get(Identifier.fromNamespaceAndPath(baseModelId.getNamespace(), "item/" + baseModelId.getPath()));
                }
            }
            if (rules == null || rules.isEmpty()) return null;

            for (OverrideRule rule : rules) {
                if (rule != null && rule.matches(stack)) {
                    return rule.targetModel;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Identifier resolveOverrideBySimpleName(String simpleName, ItemStack stack) {
        try {
            if (simpleName == null || stack == null || stack.isEmpty()) return null;
            String sLower = simpleName.toLowerCase(Locale.ROOT);

            // Fast O(1) thread-safe lookup from pre-indexed map
            List<OverrideRule> indexed = SIMPLE_NAME_OVERRIDES.get(sLower);
            if (indexed != null) {
                for (OverrideRule rule : indexed) {
                    if (rule != null && rule.matches(stack)) {
                        return rule.targetModel;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static List<OverrideRule> getOverrides(Identifier baseModelId) {
        try {
            return OVERRIDES.get(baseModelId);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
