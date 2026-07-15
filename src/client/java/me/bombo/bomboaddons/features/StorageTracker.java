package me.bombo.bomboaddons.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import me.bombo.bomboaddons.Bomboaddons;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class StorageTracker {
    private static final File STORAGE_FILE = new File(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile(), "bomboaddons_storage.json");
    
    // Container Name -> Slot Index -> NBT String
    public static final Map<String, Map<Integer, String>> storageData = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, String>> uncompressedCache = new java.util.concurrent.ConcurrentHashMap<>();
    public static long lastUpdateTime = 0;
    private static long lastSave = 0;

    public static net.minecraft.core.BlockPos lastClickedBlockPos = null;

    public static void init() {
        if (!STORAGE_FILE.exists()) return;
        try (FileReader reader = new FileReader(STORAGE_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("containers")) {
                JsonObject containers = root.getAsJsonObject("containers");
                for (String containerName : containers.keySet()) {
                    JsonObject container = containers.getAsJsonObject(containerName);
                    Map<Integer, String> slots = new java.util.concurrent.ConcurrentHashMap<>();
                    if (container.has("items")) {
                        JsonArray items = container.getAsJsonArray("items");
                        for (JsonElement elem : items) {
                            JsonObject itemObj = elem.getAsJsonObject();
                            int slot = itemObj.get("slot").getAsInt();
                            String nbt = itemObj.get("nbt").getAsString();
                            slots.put(slot, nbt);
                        }
                    }
                    storageData.put(containerName, slots);
                }
            }
            
            // Migrate legacy chest names
            java.util.List<String> toRemove = new java.util.ArrayList<>();
            Map<String, Map<Integer, String>> toAdd = new HashMap<>();
            for (String key : storageData.keySet()) {
                if (key.contains(" @ ") && !key.startsWith("Island Chest @ ")) {
                    String newKey = "Island Chest @ " + key.substring(key.indexOf(" @ ") + 3);
                    toRemove.add(key);
                    toAdd.put(newKey, storageData.get(key));
                }
            }
            for (String key : toRemove) storageData.remove(key);
            storageData.putAll(toAdd);
            
            // Deduplicate double chests from migrated data
            java.util.List<String> duplicates = new java.util.ArrayList<>();
            for (String key : storageData.keySet()) {
                if (duplicates.contains(key)) continue;
                if (key.startsWith("Island Chest @ ")) {
                    try {
                        String[] parts = key.substring(15).split(",");
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            net.minecraft.core.BlockPos adj = pos.relative(d);
                            String adjKey = "Island Chest @ " + adj.getX() + ", " + adj.getY() + ", " + adj.getZ();
                            if (storageData.containsKey(adjKey) && !duplicates.contains(adjKey)) {
                                // keep the one with lower coordinate
                                if (adj.compareTo(pos) < 0) {
                                    duplicates.add(key);
                                } else {
                                    duplicates.add(adjKey);
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
            for (String key : duplicates) storageData.remove(key);
            
            // Clean up useless menu glass panes from loaded data
            boolean cleaned = false;
            for (Map<Integer, String> slotsMap : storageData.values()) {
                java.util.Iterator<Map.Entry<Integer, String>> it = slotsMap.entrySet().iterator();
                while (it.hasNext()) {
                    String nbt = it.next().getValue();
                    if (nbt.contains("\"minecraft:black_stained_glass_pane\"") && nbt.contains("hide_tooltip:1b") && nbt.contains("text:\"\"")) {
                        it.remove();
                        cleaned = true;
                    }
                }
            }

            if (!toRemove.isEmpty() || !duplicates.isEmpty() || cleaned) {
                save(); // save migrated and cleaned data
            }

            // Async background migration to Base64 compressed NBT
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                boolean migrated = false;
                for (Map<Integer, String> slotsMap : storageData.values()) {
                    for (Map.Entry<Integer, String> entry : slotsMap.entrySet()) {
                        String nbt = entry.getValue();
                        if (nbt != null && !nbt.startsWith("B64:") && !nbt.isEmpty()) {
                            try {
                                net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.TagParser.parseCompoundFully(nbt);
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                net.minecraft.nbt.NbtIo.writeCompressed(tag, baos);
                                entry.setValue("B64:" + java.util.Base64.getEncoder().encodeToString(baos.toByteArray()));
                                migrated = true;
                            } catch (Exception e) {}
                        }
                    }
                }
                if (migrated) {
                    save();
                }
            });

            lastUpdateTime = System.currentTimeMillis();
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to load storage cache", e);
        }
    }

    private static long lastSaveAttempt = 0;
    public static synchronized void save() {
        if (System.currentTimeMillis() - lastSaveAttempt < 2000) return;
        lastSaveAttempt = System.currentTimeMillis();
        lastUpdateTime = System.currentTimeMillis();
        try {
            JsonObject root = new JsonObject();
            JsonObject containers = new JsonObject();
            
            for (Map.Entry<String, Map<Integer, String>> entry : storageData.entrySet()) {
                JsonObject containerObj = new JsonObject();
                JsonArray items = new JsonArray();
                for (Map.Entry<Integer, String> slotEntry : entry.getValue().entrySet()) {
                    JsonObject itemObj = new JsonObject();
                    itemObj.addProperty("slot", slotEntry.getKey());
                    itemObj.addProperty("nbt", slotEntry.getValue());
                    items.add(itemObj);
                }
                containerObj.add("items", items);
                containers.add(entry.getKey(), containerObj);
            }
            root.add("containers", containers);
            
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    STORAGE_FILE.getParentFile().mkdirs();
                    try (FileWriter writer = new FileWriter(STORAGE_FILE)) {
                        new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
                    }
                } catch (Exception e) {
                    Bomboaddons.LOGGER.error("[BomboAddons] Failed to save storage cache", e);
                }
            });
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to build storage cache JSON", e);
        }
    }

    public static void onGuiTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString().replaceAll("§.", "").trim();
            
            if (isTrackableContainer(title)) {
                boolean changed = false;
                if (me.bombo.bomboaddons.SkyblockUtils.getLocation().equals("Private Island") && lastClickedBlockPos != null) {
                    if (title.startsWith("Chest") || title.startsWith("Large Chest") || title.startsWith("Small Chest")) {
                        title = "Island Chest @ " + lastClickedBlockPos.getX() + ", " + lastClickedBlockPos.getY() + ", " + lastClickedBlockPos.getZ();
                        // Clear out adjacent chest positions to deduplicate old double chests
                        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                            net.minecraft.core.BlockPos adj = lastClickedBlockPos.relative(d);
                            String oldKey = "Island Chest @ " + adj.getX() + ", " + adj.getY() + ", " + adj.getZ();
                            if (storageData.containsKey(oldKey)) {
                                storageData.remove(oldKey);
                                changed = true;
                            }
                        }
                    }
                }
                if (title.startsWith("Museum ➜")) {
                    title = "Museum";
                }

                Map<Integer, String> slots = storageData.computeIfAbsent(title, k -> new java.util.concurrent.ConcurrentHashMap<>());
                
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
                
                Map<Integer, String> uncompressedSlots = uncompressedCache.computeIfAbsent(title, k -> new java.util.concurrent.ConcurrentHashMap<>());
                
                for (Slot slot : screen.getMenu().slots) {
                    if (slot.container == mc.player.getInventory()) continue;
                    
                    ItemStack stack = slot.getItem();
                    String uncompressedNbtStr = "";
                    if (!stack.isEmpty()) {
                        String name = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                        if (name.isEmpty() || name.contains("Empty ") || name.contains("Locked") || name.equals("Back") || name.equals("Close") || name.contains(" Page") || name.equals("Go Back")) {
                            uncompressedNbtStr = "";
                        } else {
                            try {
                                Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                                uncompressedNbtStr = tag.toString();
                            } catch (Exception e) {
                                uncompressedNbtStr = "";
                            }
                        }
                    }
                    
                    String existingUncompressed = uncompressedSlots.get(slot.index);
                    if (existingUncompressed == null && uncompressedNbtStr.isEmpty()) continue;
                    if (existingUncompressed != null && existingUncompressed.equals(uncompressedNbtStr)) continue;
                    
                    if (uncompressedNbtStr.isEmpty()) {
                        uncompressedSlots.remove(slot.index);
                        slots.remove(slot.index);
                    } else {
                        uncompressedSlots.put(slot.index, uncompressedNbtStr);
                        try {
                            Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                            if (tag instanceof net.minecraft.nbt.CompoundTag ct) {
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                net.minecraft.nbt.NbtIo.writeCompressed(ct, baos);
                                String nbtStr = "B64:" + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                                slots.put(slot.index, nbtStr);
                            } else {
                                slots.put(slot.index, tag.toString());
                            }
                        } catch (Exception e) {
                            slots.put(slot.index, uncompressedNbtStr);
                        }
                    }
                    if (!changed) {
                        Bomboaddons.LOGGER.info("[BomboAddons] Storage changed in GUI at slot " + slot.index + " in " + title);
                    }
                    changed = true;
                }
                
                if (changed) {
                    save();
                }
            }
        }
    }

    private static boolean isTrackableContainer(String title) {
        if (title.startsWith("Ender Chest (")) return true;
        if (title.contains("Backpack (Slot #")) return true;
        if (title.contains("Pets") || title.equals("Pets")) return true;
        if (title.contains("Accessory Bag")) return true;
        if (title.endsWith(" Sack") && !title.equals("Sacks")) return true;
        if (title.equals("Fishing Bag")) return true;
        if (title.equals("Potion Bag")) return true;
        if (title.equals("Quiver")) return true;
        if (title.equals("Time Pocket")) return true;
        if (title.contains("Armor Sets")) return true;
        if (title.contains("Equipment Sets")) return true;
        if (title.equals("Personal Vault")) return true;
        if (title.startsWith("Museum ➜")) return true;
        if (title.equals("Chest") || title.equals("Large Chest") || title.equals("Small Chest")) return true;
        return false;
    }

    public static String[] getDisplayLocAndCommand(String containerName) {
        String displayLoc = containerName;
        String cmd = "";
        
        if (displayLoc.startsWith("Ender Chest (")) {
            String num = displayLoc.replaceAll("[^0-9]", "");
            if (num.length() > 0) {
                displayLoc = "Ender Chest " + num.charAt(0);
                cmd = "/enderchest " + num.charAt(0);
            }
        } else if (displayLoc.contains("Backpack (Slot #")) {
            String num = displayLoc.replaceAll("[^0-9]", "");
            displayLoc = "Backpack " + num;
            cmd = "/backpack " + num;
        } else if (displayLoc.contains("Pets") || displayLoc.equals("Pets")) {
            cmd = "/pets";
        } else if (displayLoc.contains("Accessory Bag")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("Accessory Bag \\((\\d+)/\\d+\\)").matcher(displayLoc);
            if (m.find()) {
                displayLoc = "Accessory Bag " + m.group(1);
            }
            cmd = "/accessorybag";
        } else if (displayLoc.endsWith(" Sack")) {
            cmd = "/sax";
        } else if (displayLoc.equals("Fishing Bag")) {
            cmd = "/fishingbag";
        } else if (displayLoc.equals("Potion Bag")) {
            cmd = "/potionbag";
        } else if (displayLoc.equals("Quiver")) {
            cmd = "/quiver";
        } else if (displayLoc.equals("Time Pocket")) {
            cmd = "/timepocket";
        } else if (displayLoc.contains("Armor Sets")) {
            cmd = "/armor";
        } else if (displayLoc.contains("Equipment Sets")) {
            cmd = "/equipment";
        } else if (displayLoc.equals("Personal Vault")) {
            cmd = "/bank";
        } else if (displayLoc.equals("Museum")) {
            cmd = "/warp museum";
        } else if (displayLoc.contains(" @ ")) {
            displayLoc = "Island Chest @ " + displayLoc.substring(displayLoc.indexOf(" @ ") + 3);
        }
        
        return new String[]{displayLoc, cmd};
    }

    private static long lastPlayerInvUpdate = 0;
    public static void updatePlayerInventory(Minecraft mc) {
        if (mc.player == null) return;
        if (System.currentTimeMillis() - lastPlayerInvUpdate < 10000) return;
        lastPlayerInvUpdate = System.currentTimeMillis();
        
        boolean changed = false;
        Map<Integer, String> slots = storageData.computeIfAbsent("Inventory", k -> new java.util.concurrent.ConcurrentHashMap<>());
        Map<Integer, String> uncompressedSlots = uncompressedCache.computeIfAbsent("Inventory", k -> new java.util.concurrent.ConcurrentHashMap<>());
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
        
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            String uncompressedNbtStr = "";
            if (!stack.isEmpty()) {
                String name = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                if (!name.contains("Empty ") && !name.contains("Locked") && !name.equals("Back") && !name.equals("Close") && !name.contains(" Page") && !name.equals("Go Back")) {
                    try {
                        Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        uncompressedNbtStr = tag.toString();
                    } catch (Exception e) {
                        uncompressedNbtStr = "";
                    }
                }
            }
            
            String existingUncompressed = uncompressedSlots.get(i);
            if (existingUncompressed == null && uncompressedNbtStr.isEmpty()) continue;
            if (existingUncompressed != null && existingUncompressed.equals(uncompressedNbtStr)) continue;
            
            if (uncompressedNbtStr.isEmpty()) {
                uncompressedSlots.remove(i);
                slots.remove(i);
            } else {
                uncompressedSlots.put(i, uncompressedNbtStr);
                try {
                    Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                    if (tag instanceof net.minecraft.nbt.CompoundTag ct) {
                        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                        net.minecraft.nbt.NbtIo.writeCompressed(ct, baos);
                        String nbtStr = "B64:" + java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
                        slots.put(i, nbtStr);
                    } else {
                        slots.put(i, tag.toString());
                    }
                } catch (Exception e) {
                    slots.put(i, uncompressedNbtStr);
                }
            }
            changed = true;
        }
        
        if (changed) {
            save();
        }
    }
}
