package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockItemManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.mojang.authlib.GameProfile;
import net.minecraft.world.item.component.ResolvableProfile;

@Environment(EnvType.CLIENT)
public class TotemAnimationManager {

    private static final Pattern MAGIC_FIND_PATTERN = Pattern.compile("^(?!.*:)(?:RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|PET DROP|RNGESUS INCITING DROP|UNBELIEVABLE) DROP!\\s+\\(?(?<item>.+?)\\)?(?:\\s+\\(\\+\\d+%?.*Magic Find\\))?.*$");
    private static final Pattern GENERIC_RARE_PATTERN = Pattern.compile("^(?!.*:).*?(?:RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|PET DROP)!?\\s+\\(?(?<item>[A-Za-z0-9 '’\\-_]+)\\)?.*$");

    // Inventory tracking for "All Drops" totem animation (tracked globally to avoid false triggers when moving items)
    private static final Map<String, Integer> previousCounts = new HashMap<>();
    private static final Map<String, ItemStack> previousSamples = new HashMap<>();
    private static long lastTotemPlayedTime = 0L;
    private static boolean isFirstSnapshot = true;

    public static void onChatMessage(String clean) {
        if (clean == null || clean.isEmpty()) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.totemRareDrops) return;

        // Skip non-valuable crop drops or common messages
        if (clean.contains("RARE CROP!") || clean.contains("Ethereal Vine") || clean.contains("Tightly-Tied Hay Bale") || clean.contains("Crop Fever")) {
            return;
        }

        Matcher m = MAGIC_FIND_PATTERN.matcher(clean);
        if (m.matches()) {
            String itemName = m.group("item");
            if (itemName != null) {
                triggerTotemForName(itemName.trim());
                return;
            }
        }

        Matcher m2 = GENERIC_RARE_PATTERN.matcher(clean);
        if (m2.matches()) {
            try {
                String itemName = m2.group("item");
                if (itemName != null && !itemName.isEmpty()) {
                    triggerTotemForName(itemName.trim());
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            previousCounts.clear();
            previousSamples.clear();
            isFirstSnapshot = true;
            return;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.totemAllDrops || !s.totemRareDrops) {
            if (isFirstSnapshot) {
                updateSnapshot(mc);
                isFirstSnapshot = false;
            }
            return;
        }

        if (isFirstSnapshot) {
            updateSnapshot(mc);
            isFirstSnapshot = false;
            return;
        }

        Map<String, Integer> currentCounts = new HashMap<>();
        Map<String, ItemStack> currentSamples = new HashMap<>();
        int size = Math.min(36, mc.player.getInventory().getContainerSize());

        for (int i = 0; i < size; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String key = getItemKey(stack);
                currentCounts.put(key, currentCounts.getOrDefault(key, 0) + stack.getCount());
                currentSamples.putIfAbsent(key, stack.copy());
            }
        }

        ItemStack newlyReceived = null;
        for (Map.Entry<String, Integer> entry : currentCounts.entrySet()) {
            String key = entry.getKey();
            int currentQty = entry.getValue();
            int prevQty = previousCounts.getOrDefault(key, 0);

            if (currentQty > prevQty) {
                newlyReceived = currentSamples.get(key);
                break;
            }
        }

        previousCounts.clear();
        previousCounts.putAll(currentCounts);
        previousSamples.clear();
        previousSamples.putAll(currentSamples);

        if (newlyReceived != null && !newlyReceived.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastTotemPlayedTime > 300L) { // Prevent extreme spam
                playTotemAnimation(newlyReceived);
                lastTotemPlayedTime = now;
            }
        }
    }

    private static String getItemKey(ItemStack stack) {
        String sbId = me.bombo.bomboaddons.SkyblockUtils.getSkyblockId(stack);
        if (sbId != null && !sbId.isEmpty()) return "SB:" + sbId;
        String name = stack.getHoverName().getString();
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return item + ":" + name;
    }

    private static void updateSnapshot(Minecraft mc) {
        previousCounts.clear();
        previousSamples.clear();
        int size = Math.min(36, mc.player.getInventory().getContainerSize());
        for (int i = 0; i < size; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty()) {
                String key = getItemKey(stack);
                previousCounts.put(key, previousCounts.getOrDefault(key, 0) + stack.getCount());
                previousSamples.putIfAbsent(key, stack.copy());
            }
        }
    }

    public static void triggerTotemForName(String cleanItemName) {
        long now = System.currentTimeMillis();
        if (now - lastTotemPlayedTime < 300L) return;

        ItemStack stack = resolveItemStack(cleanItemName);
        playTotemAnimation(stack);
        lastTotemPlayedTime = now;
    }

    public static void playTotemAnimation(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ItemStack toDisplay = (stack != null && !stack.isEmpty()) ? stack : new ItemStack(Items.TOTEM_OF_UNDYING);
        mc.execute(() -> {
            if (mc.gameRenderer != null) {
                mc.gameRenderer.displayItemActivation(toDisplay);
            }
            if (mc.player != null) {
                mc.player.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.0F);
            }
        });
    }

    public static ItemStack resolveItemStack(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return new ItemStack(Items.TOTEM_OF_UNDYING);
        }
        String clean = rawName.replaceAll("(?i)§.", "").trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        // 0. Skull texture hash check (e.g. "skull:822d8e751c8f2fd4c8942c44bdb2f5ca4d8ae8e575ed3eb34c18a86e93b")
        if (lower.startsWith("skull:")) {
            String hash = clean.substring("skull:".length()).trim();
            if (!hash.isEmpty()) {
                String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
                String b64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
                ResolvableProfile rp = SkyblockItemManager.createProfile(b64, null);
                ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
                if (rp != null) {
                    stack.set(DataComponents.PROFILE, rp);
                }
                return stack;
            }
        }

        // 0b. Player head with SNBT / profile tag (e.g. "player_head[profile={...}]")
        if (lower.startsWith("player_head[") || lower.startsWith("minecraft:player_head[")) {
            Matcher m = Pattern.compile("value:\\\\?\"([^\\\\\"]+)\\\\?\"").matcher(clean);
            if (m.find()) {
                String b64 = m.group(1);
                ResolvableProfile rp = SkyblockItemManager.createProfile(b64, null);
                ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
                if (rp != null) {
                    stack.set(DataComponents.PROFILE, rp);
                }
                return stack;
            }
            Matcher nameMatcher = Pattern.compile("name:([a-zA-Z0-9_]+)").matcher(clean);
            if (nameMatcher.find()) {
                String pName = nameMatcher.group(1);
                ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
                stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + pName).getBytes(StandardCharsets.UTF_8)), pName)));
                return stack;
            }
        }

        // 1. Explicit vanilla item check (e.g. "minecraft:compass", "minecraft:crafting_table")
        if (lower.startsWith("minecraft:")) {
            String path = lower.substring("minecraft:".length()).trim();
            Identifier id = Identifier.tryParse("minecraft:" + path);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }

        // 2. Direct SkyBlock ID check (e.g. "HYPERION", "ASPECT_OF_THE_END")
        String upperId = clean.toUpperCase(Locale.ROOT).replace(" ", "_");
        if (SkyblockItemManager.getInfo(upperId) != null) {
            ItemStack sbItem = SkyblockItemManager.createSkyblockItem(upperId);
            if (sbItem != null && !sbItem.isEmpty()) {
                return sbItem;
            }
        }

        // 3. Vanilla registry match by clean name (e.g. "compass", "crafting_table", "chest")
        String cleanVanillaPath = lower.replace(" ", "_").replace("'", "").replace("-", "_");
        Identifier vanillaId = Identifier.tryParse("minecraft:" + cleanVanillaPath);
        if (vanillaId != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(vanillaId).orElse(null);
            if (item != null && item != Items.AIR) {
                return new ItemStack(item);
            }
        }

        // 4. Check player inventory for matching name or ID
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                ItemStack is = mc.player.getInventory().getItem(i);
                if (!is.isEmpty()) {
                    String hover = is.getHoverName().getString().replaceAll("(?i)§.", "").trim();
                    String isSbId = me.bombo.bomboaddons.SkyblockUtils.getSkyblockId(is);
                    if (hover.equalsIgnoreCase(clean) || hover.toLowerCase(Locale.ROOT).contains(lower)
                            || (isSbId != null && isSbId.equalsIgnoreCase(upperId))) {
                        return is.copy();
                    }
                }
            }
        }

        // 5. Check SkyblockItemManager cache for display name match
        try {
            Map<String, SkyblockItemManager.SkyblockItemInfo> cache = SkyblockItemManager.getItemCache();
            if (cache != null && !cache.isEmpty()) {
                for (SkyblockItemManager.SkyblockItemInfo info : cache.values()) {
                    if (info.name != null) {
                        String infoClean = info.name.replaceAll("(?i)§.", "").trim();
                        if (infoClean.equalsIgnoreCase(clean) || (info.id != null && info.id.equalsIgnoreCase(upperId))) {
                            ItemStack created = SkyblockItemManager.createSkyblockItem(info.id);
                            if (created != null && !created.isEmpty()) return created;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Fallback default
        return new ItemStack(Items.TOTEM_OF_UNDYING);
    }
}
