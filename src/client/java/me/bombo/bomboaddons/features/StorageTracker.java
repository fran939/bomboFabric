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
    public static final Map<String, Map<Integer, String>> storageData = new HashMap<>();
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
                    Map<Integer, String> slots = new HashMap<>();
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
            lastUpdateTime = System.currentTimeMillis();
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to load storage cache", e);
        }
    }

    public static void save() {
        lastUpdateTime = System.currentTimeMillis();
        try {
            STORAGE_FILE.getParentFile().mkdirs();
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
            
            try (FileWriter writer = new FileWriter(STORAGE_FILE)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Failed to save storage cache", e);
        }
    }

    public static void onGuiTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString().replaceAll("§.", "").trim();
            
            if (isTrackableContainer(title)) {
                if (me.bombo.bomboaddons.SkyblockUtils.getLocation().equals("Private Island") && lastClickedBlockPos != null) {
                    if (title.equals("Chest") || title.equals("Large Chest") || title.equals("Small Chest")) {
                        title = title + String.format(" @ %d, %d, %d", lastClickedBlockPos.getX(), lastClickedBlockPos.getY(), lastClickedBlockPos.getZ());
                    }
                }
                if (title.startsWith("Museum ➜")) {
                    title = "Museum";
                }

                boolean changed = false;
                Map<Integer, String> slots = storageData.computeIfAbsent(title, k -> new HashMap<>());
                
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
                
                for (Slot slot : screen.getMenu().slots) {
                    if (slot.container == mc.player.getInventory()) continue;
                    
                    ItemStack stack = slot.getItem();
                    String nbtStr = "";
                    if (!stack.isEmpty()) {
                        String name = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                        if (name.contains("Empty ") || name.contains("Locked") || name.equals("Back") || name.equals("Close") || name.contains(" Page") || name.equals("Go Back")) {
                            nbtStr = "";
                        } else {
                            try {
                                Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                                nbtStr = tag.toString();
                            } catch (Exception e) {
                                nbtStr = "";
                            }
                        }
                    }
                    
                    String existing = slots.get(slot.index);
                    if (existing == null && nbtStr.isEmpty()) continue;
                    if (existing != null && existing.equals(nbtStr)) continue;
                    
                    if (nbtStr.isEmpty()) {
                        slots.remove(slot.index);
                    } else {
                        slots.put(slot.index, nbtStr);
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

    public static void updatePlayerInventory(Minecraft mc) {
        if (mc.player == null) return;
        boolean changed = false;
        Map<Integer, String> slots = storageData.computeIfAbsent("Inventory", k -> new HashMap<>());
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
        
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            String nbtStr = "";
            if (!stack.isEmpty()) {
                String name = net.minecraft.ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
                if (!name.contains("Empty ") && !name.contains("Locked") && !name.equals("Back") && !name.equals("Close") && !name.contains(" Page") && !name.equals("Go Back")) {
                    try {
                        Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        nbtStr = tag.toString();
                    } catch (Exception e) {
                        nbtStr = "";
                    }
                }
            }
            
            String existing = slots.get(i);
            if (existing == null && nbtStr.isEmpty()) continue;
            if (existing != null && existing.equals(nbtStr)) continue;
            
            if (nbtStr.isEmpty()) {
                slots.remove(i);
            } else {
                slots.put(i, nbtStr);
            }
            changed = true;
        }
        
        if (changed) {
            save();
        }
    }
}
