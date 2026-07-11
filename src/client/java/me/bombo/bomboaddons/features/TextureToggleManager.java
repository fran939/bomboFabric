package me.bombo.bomboaddons.features;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class TextureToggleManager {
    public static final TextureToggleManager INSTANCE = new TextureToggleManager();

    private final Map<String, Map<String, String>> itemIds;
    private final Map<String, GameProfile> cachedItems = new HashMap<>();
    public final Set<String> whitelistedItems = new HashSet<>();
    public boolean blacklistMode = false;

    private TextureToggleManager() {
        Map<String, Map<String, String>> loadedItemIds = null;
        try {
            var is = getClass().getResourceAsStream("/assets/bomboaddons/ItemDataSet.json");
            if (is != null) {
                loadedItemIds = new Gson().fromJson(new InputStreamReader(is), new TypeToken<Map<String, Map<String, String>>>() {}.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        itemIds = loadedItemIds != null ? loadedItemIds : new HashMap<>();
    }

    public void init() {
        loadConfig();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        ScreenEvents.BEFORE_INIT.register((client, screen, i, i1) -> {
            ScreenKeyboardEvents.allowKeyRelease(screen).register((screen1, event) -> {
                if (!BomboConfig.get().noResourcePack) return true;
                
                int boundKey = ClickLogic.getKeyCode(BomboConfig.get().textureToggleKey);
                if (boundKey != -1 && event.key() == boundKey) {
                    if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> containerScreen) {
                        net.minecraft.world.inventory.Slot hoveredSlot = ((me.bombo.bomboaddons.mixin.AbstractContainerScreenAccessor) containerScreen).getHoveredSlot();
                        if (hoveredSlot != null && hoveredSlot.hasItem()) {
                            ItemStack stack = hoveredSlot.getItem();
                            String sbId = skyblockId(stack);
                            if (sbId != null) {
                                toggleItem(sbId);
                            }
                        }
                    }
                }
                return true;
            });
        });
    }

    public void toggleItem(String sbId) {
        boolean added = false;
        if (whitelistedItems.contains(sbId)) {
            whitelistedItems.remove(sbId);
        } else {
            added = whitelistedItems.add(sbId);
        }
        
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(
                    Component.literal("[BomboAddons] ")
                            .withStyle(Style.EMPTY.withColor(ChatFormatting.RED))
                            .append(Component.literal(blacklistMode ? "Blacklist " : "Whitelist ").withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
                            .append(Component.literal(added ? "added " : "removed ").withStyle(Style.EMPTY.withColor(added ? ChatFormatting.GREEN : ChatFormatting.RED)))
                            .append(Component.literal(sbId).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA)))
            );
        }
    }

    public String skyblockId(ItemStack itemStack) {
        var customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        var tag = customData.copyTag();
        if (tag == null) return null;

        String sbId = null;
        if (tag.contains("id")) {
            sbId = tag.getString("id").orElse(null);
        }
        boolean isQuiver = tag.contains("quiver_arrow");
        if (isQuiver && sbId == null) {
            return "NRP$QUIVER";
        }
        return sbId;
    }

    public String modelId(ItemStack itemStack) {
        String id = skyblockId(itemStack);
        if (id == null || itemIds == null) return null;
        var map = itemIds.get(id);
        if (map == null) return null;
        return map.get("model");
    }

    public GameProfile gameProfile(String sbId) {
        if (blacklistMode && !whitelistedItems.contains(sbId)) return null;
        if (whitelistedItems.contains(sbId) && !blacklistMode) return null;
        return cachedItems.get(sbId);
    }

    public GameProfile gameProfile(ItemStack itemStack) {
        String sbId = skyblockId(itemStack);
        if (sbId == null) return null;
        return gameProfile(sbId);
    }

    public Identifier fromModelId(ItemStack itemStack, Identifier modelId) {
        if (modelId == null) return null;
        if (!modelId.getNamespace().startsWith("hypixel_skyblock")) return modelId;

        String sbId = skyblockId(itemStack);
        if (sbId == null) return modelId;

        if (blacklistMode && !whitelistedItems.contains(sbId)) return modelId;
        if (whitelistedItems.contains(sbId) && !blacklistMode) return modelId;

        if (itemIds != null && itemIds.containsKey(sbId)) {
            String id = modelId(itemStack);
            if (id == null) return modelId;

            if (id.equals("minecraft:player_head")) {
                var cache = itemIds.get(sbId);
                if (cache != null && cache.containsKey("value")) {
                    String value = cache.get("value");
                    if (!cachedItems.containsKey(sbId)) {
                        PropertyMap map = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
                        cachedItems.put(sbId, new GameProfile(UUID.randomUUID(), "bombo$fakeItem", map));
                    }
                }
            }
            return Identifier.parse(id);
        }

        return BuiltInRegistries.ITEM.getKey(itemStack.getItem());
    }

    private Path getConfigPath() {
        return Paths.get("config", "bomboaddons", "texturetoggle.json");
    }

    private void loadConfig() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                JsonObject obj = new Gson().fromJson(json, JsonObject.class);
                if (obj.has("blacklistMode")) {
                    blacklistMode = obj.get("blacklistMode").getAsBoolean();
                }
                if (obj.has("whitelist")) {
                    whitelistedItems.clear();
                    JsonArray arr = obj.getAsJsonArray("whitelist");
                    for (var e : arr) {
                        whitelistedItems.add(e.getAsString());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveConfig() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("blacklistMode", blacklistMode);
            JsonArray arr = new JsonArray();
            for (String s : whitelistedItems) {
                arr.add(s);
            }
            obj.add("whitelist", arr);
            Files.writeString(path, new Gson().toJson(obj));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
