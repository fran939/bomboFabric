package me.bombo.bomboaddons.features.storageoverlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.StorageTracker;
import java.util.Arrays;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class BackpackPreview {
    private static final Pattern ECHEST_PATTERN = Pattern.compile(".*?(?:Ender Chest|Cofre de Ender).*?(?:\\((\\d{1,2})/(\\d{1,2})\\)|Page\\s*(\\d{1,2})|#?\\s*(\\d{1,2}))", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECHEST_PAGE_PATTERN = Pattern.compile(".*?(?:Ender Chest|Cofre de Ender).*?Page\\s*(\\d{1,2})", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK_PATTERN = Pattern.compile(".*?(?:Backpack|Mochila).*?(?:\\((?:Slot|Ranura)\\s*#?\\(?(\\d{1,2})\\)?\\)|#?\\s*(\\d{1,2}))", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK_SIZE_PATTERN = Pattern.compile("(?:has|tiene)\\s+(\\d+)\\s+(?:slots|ranuras)", Pattern.CASE_INSENSITIVE);

    public static final int STORAGE_SIZE = 27;
    private static final @Nullable Storage[] storages = new Storage[STORAGE_SIZE];

    private static String loadedProfile = "";
    private static Path saveDir = null;

    public static @Nullable Storage[] getStorages() {
        seedFromStorageTrackerIfEmpty();
        return storages;
    }

    public static @Nullable Storage getStorage(int index) {
        if (index < 0 || index >= STORAGE_SIZE) return null;
        if (storages[index] == null) {
            seedStorageFromStorageTracker(index);
        }
        return storages[index];
    }

    public static int getStorageIndexFromTitle(String rawTitle) {
        if (rawTitle == null) return -1;
        String title = ChatFormatting.stripFormatting(rawTitle).trim();
        String lower = title.toLowerCase(Locale.ROOT);

        Matcher echest = ECHEST_PATTERN.matcher(title);
        if (echest.find()) {
            for (int g = 1; g <= echest.groupCount(); g++) {
                String val = echest.group(g);
                if (val != null && !val.isEmpty()) {
                    try {
                        return Integer.parseInt(val) - 1;
                    } catch (Exception ignored) {}
                }
            }
        }

        Matcher echestPage = ECHEST_PAGE_PATTERN.matcher(title);
        if (echestPage.find()) {
            try {
                return Integer.parseInt(echestPage.group(1)) - 1;
            } catch (Exception ignored) {}
        }

        Matcher backpack = BACKPACK_PATTERN.matcher(title);
        if (backpack.find()) {
            for (int g = 1; g <= backpack.groupCount(); g++) {
                String val = backpack.group(g);
                if (val != null && !val.isEmpty()) {
                    try {
                        return Integer.parseInt(val) + 8;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (lower.equals("ender chest") || lower.startsWith("ender chest") || lower.equals("cofre de ender") || lower.startsWith("cofre de ender")) {
            return 0;
        }

        return -1;
    }

    private static final java.time.format.DateTimeFormatter DEBUG_TIME = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void logStorageDebug(String msg) {
        try {
            Path logPath = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("storage_debug.log");
            if (logPath.getParent() != null) Files.createDirectories(logPath.getParent());
            String line = "[" + java.time.LocalDateTime.now().format(DEBUG_TIME) + "] " + msg + System.lineSeparator();
            Files.writeString(logPath, line, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {}
    }

    public static Path getSaveDir() {
        if (saveDir == null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                String uuid = (mc.getUser() != null && mc.getUser().getProfileId() != null)
                        ? mc.getUser().getProfileId().toString().replace("-", "") : "default_user";
                String profileId = (SkyblockUtils.currentProfileId != null && !SkyblockUtils.currentProfileId.isEmpty())
                        ? SkyblockUtils.currentProfileId : "default";
                saveDir = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("backpack-preview").resolve(uuid).resolve(profileId);
                Files.createDirectories(saveDir);
            } catch (Exception ignored) {}
        }
        return saveDir;
    }

    public static void dumpDebugToFile(Consumer<Component> feedback) {
        try {
            Path logPath = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("storage_debug.log");
            if (logPath.getParent() != null) Files.createDirectories(logPath.getParent());

            StringBuilder sb = new StringBuilder();
            sb.append("================================================================================\n");
            sb.append("BOMBOADDONS STORAGE DEBUG DUMP - ").append(java.time.LocalDateTime.now().format(DEBUG_TIME)).append("\n");
            sb.append("================================================================================\n");
            sb.append("Skyblock Active: ").append(SkyblockUtils.isOnSkyblock()).append("\n");
            sb.append("Current Profile ID: ").append(SkyblockUtils.currentProfileId).append("\n");
            sb.append("Loaded Profile: ").append(loadedProfile).append("\n");
            sb.append("Save Directory: ").append(getSaveDir() != null ? getSaveDir().toString() : "null").append("\n");
            sb.append("Storage Tracker Seeded: ").append(hasAnyStorage()).append("\n\n");

            sb.append("--- DISCOVERED STORAGES (Total 27) ---\n");
            for (int i = 0; i < STORAGE_SIZE; i++) {
                Storage s = storages[i];
                if (s == null) {
                    sb.append(String.format("[%02d] %-18s : NULL / UNINITIALIZED\n", i, getStorageName(i)));
                } else {
                    int nonNullCount = 0;
                    List<String> sampleItems = new ArrayList<>();
                    for (int slot = 0; slot < s.size(); slot++) {
                        ItemStack item = s.getStack(slot);
                        if (!item.isEmpty()) {
                            nonNullCount++;
                            if (sampleItems.size() < 5) {
                                sampleItems.add(String.format("slot %d: %s (x%d)", slot, item.getHoverName().getString(), item.getCount()));
                            }
                        }
                    }
                    sb.append(String.format("[%02d] %-18s : Size=%d, Active Items=%d | Sample: %s\n",
                            i, s.name(), s.size(), nonNullCount,
                            sampleItems.isEmpty() ? "Empty" : String.join(", ", sampleItems)));
                }
            }
            sb.append("================================================================================\n\n");

            Files.writeString(logPath, sb.toString(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);

            if (feedback != null) {
                feedback.accept(Component.literal("§8[§3Bombo§8] §aStorage debug dumped to §e.minecraft/config/bomboaddons/storage_debug.log"));
            }
        } catch (Throwable t) {
            if (feedback != null) {
                feedback.accept(Component.literal("§8[§3Bombo§8] §cFailed to dump storage debug: " + t.getMessage()));
            }
        }
    }

    public static void updateStorageDirectly(int index, Container container) {
        if (index < 0 || index >= STORAGE_SIZE || container == null) return;
        int size = container.getContainerSize();
        ItemStack[] copy = new ItemStack[size];
        List<String> itemNames = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack st = container.getItem(i);
            copy[i] = st.copy();
            if (!st.isEmpty() && itemNames.size() < 8) {
                itemNames.add("slot " + i + ": " + st.getHoverName().getString() + " x" + st.getCount());
            }
        }
        storages[index] = new Storage(new SimpleContainer(copy), getStorageName(index), true);
        saveStorage(index);
        logStorageDebug("updateStorageDirectly index=" + index + " (" + getStorageName(index) + "), items: " + String.join(", ", itemNames));
    }

    public static String getStorageName(int index) {
        String defaultName = (index <= 8) ? "Ender Chest " + (index + 1) : "Backpack " + (index - 8);
        try {
            Map<String, String> customs = BomboConfig.get().storageCustomNames;
            if (customs != null && customs.containsKey(String.valueOf(index))) {
                String custom = customs.get(String.valueOf(index));
                if (custom != null && !custom.trim().isEmpty()) {
                    return custom.trim();
                }
            }
        } catch (Throwable ignored) {}
        return defaultName;
    }

    public static void setCustomStorageName(int index, String newName) {
        if (index < 0 || index >= STORAGE_SIZE) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s.storageCustomNames == null) {
            s.storageCustomNames = new java.util.HashMap<>();
        }
        if (newName == null || newName.trim().isEmpty()) {
            s.storageCustomNames.remove(String.valueOf(index));
        } else {
            s.storageCustomNames.put(String.valueOf(index), newName.trim());
        }
        BomboConfig.save();
    }

    public static int extractSlotNumber(String text) {
        if (text == null) return -1;
        Matcher m = Pattern.compile("(?:slot\\s*#?\\s*|backpack\\s+(?:slot\\s*#?\\s*)?)(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        Matcher m2 = Pattern.compile("#(\\d+)").matcher(text);
        if (m2.find()) {
            try {
                return Integer.parseInt(m2.group(1));
            } catch (Exception ignored) {}
        }
        return -1;
    }

    public static int extractPageNumber(String text) {
        if (text == null) return -1;
        Matcher m = Pattern.compile("(?:\\(|page\\s*)(\\d+)(?:/|\\s*\\)|$)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return -1;
    }

    public static void onProfileChanged(String newProfileId) {
        loadedProfile = "";
        Arrays.fill(storages, null);
        tick();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isOnSkyblock() || mc.getUser() == null || mc.getUser().getProfileId() == null) {
            return;
        }

        String uuid = mc.getUser().getProfileId().toString().replace("-", "");
        String profileId = SkyblockUtils.currentProfileId;
        if (profileId == null || profileId.isEmpty()) {
            profileId = "default";
        }
        String combined = uuid + "/" + profileId;

        if (!combined.equals(loadedProfile)) {
            loadedProfile = combined;
            Arrays.fill(storages, null);
            saveDir = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("backpack-preview").resolve(uuid).resolve(profileId);
            try {
                Files.createDirectories(saveDir);
            } catch (Exception ignored) {}
            loadStorages();
        }

        saveDirtyStorages();
    }

    public static void updateFromContainer(String rawTitle, Container container) {
        if (rawTitle == null || container == null) return;
        String clean = ChatFormatting.stripFormatting(rawTitle).trim().toLowerCase(Locale.ENGLISH);

        logStorageDebug("updateFromContainer called with rawTitle=\"" + rawTitle + "\", containerSize=" + container.getContainerSize());

        if (clean.equals("storage") || clean.startsWith("storage ") || clean.contains("storage") || clean.contains("almacenamiento")) {
            logStorageDebug("Detected main /storage menu opened.");
            initializeStorage(container);
            return;
        }

        int index = getStorageIndexFromTitle(rawTitle);
        if (index >= 0 && index < STORAGE_SIZE) {
            int size = container.getContainerSize();
            ItemStack[] copy = new ItemStack[size];
            List<String> itemNames = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                ItemStack st = container.getItem(i);
                copy[i] = st.copy();
                if (!st.isEmpty() && itemNames.size() < 8) {
                    itemNames.add("slot " + i + ": " + st.getHoverName().getString() + " x" + st.getCount());
                }
            }
            storages[index] = new Storage(new SimpleContainer(copy), getStorageName(index), true);
            saveStorage(index);
            logStorageDebug("Updated storage index=" + index + " (" + getStorageName(index) + "), items: " + String.join(", ", itemNames));
        } else {
            logStorageDebug("Could not match title to any storage index: \"" + rawTitle + "\"");
        }
    }

    public static void initializeStorage(Container container) {
        if (container == null) return;

        // Ender chests: slots 9..17
        for (int i = 9; i < 18; ++i) {
            if (i >= container.getContainerSize()) break;
            ItemStack slotItem = container.getItem(i);
            int index = i - 9;
            if (slotItem.is(Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.RED))) continue;

            // Detect exact capacity / rows from lore if present, or previously saved size from disk
            int detectedSize = 54;
            ItemLore lore = slotItem.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String str = line.getString();
                    Matcher mRows = Pattern.compile("(\\d+)\\s*(?:rows|filas)", Pattern.CASE_INSENSITIVE).matcher(str);
                    if (mRows.find()) {
                        try {
                            detectedSize = (Integer.parseInt(mRows.group(1)) * 9) + 9;
                            break;
                        } catch (Exception ignored) {}
                    }
                    Matcher mSlots = Pattern.compile("(\\d+)\\s*(?:slots|items|ranuras)", Pattern.CASE_INSENSITIVE).matcher(str);
                    if (mSlots.find()) {
                        try {
                            detectedSize = Integer.parseInt(mSlots.group(1)) + 9;
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (detectedSize == 54 && saveDir != null) {
                File f = saveDir.resolve(index + ".json").toFile();
                if (f.exists() && f.isFile()) {
                    try (FileReader r = new FileReader(f)) {
                        JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                        if (o.has("size")) {
                            detectedSize = o.get("size").getAsInt();
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (storages[index] == null) {
                final int finalSize = detectedSize;
                storages[index] = new Storage(
                        new SimpleContainer(Stream.generate(() -> ItemStack.EMPTY).limit(finalSize).toArray(ItemStack[]::new)),
                        getStorageName(index), true
                );
            }
        }

        // Backpacks: slots 27..44
        for (int i = 27; i < 45; ++i) {
            if (i >= container.getContainerSize()) break;
            ItemStack slotItem = container.getItem(i);
            int index = i - 18; // 9..26
            if (slotItem.is(Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.BROWN))) {
                storages[index] = null;
                continue;
            }
            if (storages[index] != null) continue;

            int capacity = 54;
            ItemLore lore = slotItem.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    Matcher m = BACKPACK_SIZE_PATTERN.matcher(line.getString());
                    if (m.find()) {
                        try {
                            capacity = Integer.parseInt(m.group(1));
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (capacity == 54 && saveDir != null) {
                File f = saveDir.resolve(index + ".json").toFile();
                if (f.exists() && f.isFile()) {
                    try (FileReader r = new FileReader(f)) {
                        JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                        if (o.has("size")) {
                            capacity = o.get("size").getAsInt() - 9;
                        }
                    } catch (Exception ignored) {}
                }
            }

            storages[index] = new Storage(
                    new SimpleContainer(Stream.generate(() -> ItemStack.EMPTY).limit(Math.max(9, capacity + 9)).toArray(ItemStack[]::new)),
                    getStorageName(index), true
            );
        }
    }

    private static void saveDirtyStorages() {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (storages[i] != null && storages[i].dirty) {
                saveStorage(i);
            }
        }
    }

    private static void saveStorage(int index) {
        Path dir = getSaveDir();
        if (dir == null || storages[index] == null) return;
        Storage storage = storages[index];

        CompletableFuture.runAsync(() -> {
            try {
                Minecraft mc = Minecraft.getInstance();
                RegistryOps<Tag> ops = mc.level != null ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess()) : null;
                if (ops == null) return;

                JsonObject root = new JsonObject();
                root.addProperty("name", storage.name());
                root.addProperty("size", storage.size());

                JsonArray items = new JsonArray();
                for (int slot = 0; slot < storage.size(); slot++) {
                    ItemStack stack = storage.getStack(slot);
                    if (!stack.isEmpty()) {
                        try {
                            Tag tag = (Tag) ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                            JsonObject itemObj = new JsonObject();
                            itemObj.addProperty("slot", slot);
                            itemObj.addProperty("nbt", tag.toString());
                            items.add(itemObj);
                        } catch (Exception ignored) {}
                    }
                }
                root.add("items", items);

                File file = dir.resolve(index + ".json").toFile();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(root.toString());
                }
                storage.dirty = false;
            } catch (Exception e) {
                Bomboaddons.LOGGER.warn("[BomboAddons] Failed to save backpack preview index {}: {}", index, e.getMessage());
            }
        });
    }

    private static void loadStorages() {
        Path dir = getSaveDir();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            storages[i] = null;
            if (dir == null) continue;
            File file = dir.resolve(i + ".json").toFile();
            if (file.exists() && file.isFile()) {
                int index = i;
                CompletableFuture.runAsync(() -> {
                    try (FileReader reader = new FileReader(file)) {
                        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                        String name = root.has("name") ? root.get("name").getAsString() : getStorageName(index);
                        int size = root.has("size") ? root.get("size").getAsInt() : 54;

                        Minecraft mc = Minecraft.getInstance();
                        RegistryOps<Tag> ops = mc.level != null ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess()) : null;

                        ItemStack[] itemArray = Stream.generate(() -> ItemStack.EMPTY).limit(size).toArray(ItemStack[]::new);
                        if (root.has("items") && ops != null) {
                            for (JsonElement el : root.getAsJsonArray("items")) {
                                JsonObject obj = el.getAsJsonObject();
                                int slot = obj.get("slot").getAsInt();
                                String nbt = obj.get("nbt").getAsString();
                                if (slot >= 0 && slot < size && !nbt.isEmpty()) {
                                    try {
                                        ItemStack stack = me.bombo.bomboaddons.gui.StorageOverlayManager.parseItemStack(nbt, ops);
                                        itemArray[slot] = stack;
                                    } catch (Exception ignored) {}
                                }
                            }
                        }

                        Storage st = new Storage(new SimpleContainer(itemArray), name, false);
                        mc.execute(() -> storages[index] = st);
                    } catch (Exception e) {
                        Bomboaddons.LOGGER.warn("[BomboAddons] Failed to load backpack preview file {}: {}", file.getName(), e.getMessage());
                    }
                });
            }
        }
    }

    public static boolean hasAnyStorage() {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (storages[i] != null) return true;
        }
        return false;
    }

    private static void seedFromStorageTrackerIfEmpty() {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (storages[i] == null) {
                seedStorageFromStorageTracker(i);
            }
        }
        // Fallback for Ender Chest 1 only (guaranteed for all SkyBlock players)
        if (storages[0] == null) {
            storages[0] = new Storage(
                new SimpleContainer(Stream.generate(() -> ItemStack.EMPTY).limit(54).toArray(ItemStack[]::new)),
                getStorageName(0), false
            );
        }
    }

    private static void seedStorageFromStorageTracker(int index) {
        if (storages[index] != null) return;
        Map<Integer, String> slots = null;
        String resolvedName = getStorageName(index);

        for (Map.Entry<String, Map<Integer, String>> entry : StorageTracker.storageData.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String lower = key.toLowerCase(Locale.ROOT);
            if (index <= 8) {
                if (lower.contains("ender chest") || lower.contains("cofre de ender")) {
                    int p = extractPageNumber(key);
                    if (p == index + 1) {
                        slots = entry.getValue();
                        resolvedName = key;
                        break;
                    }
                }
            } else {
                if (lower.contains("backpack") || lower.contains("mochila")) {
                    int s = extractSlotNumber(key);
                    if (s == (index - 8)) {
                        slots = entry.getValue();
                        resolvedName = key;
                        break;
                    }
                }
            }
        }

        if (slots == null || slots.isEmpty()) return;

        int finalSize;
        if (index <= 8) {
            // Ender Chests in Hypixel SkyBlock always have 54 container slots (5 rows = 45 items + 9 nav)
            finalSize = 54;
        } else {
            int maxSlot = 17;
            for (Integer slotNum : slots.keySet()) {
                if (slotNum > maxSlot) maxSlot = slotNum;
            }
            finalSize = Math.min(54, Math.max(18, ((maxSlot / 9) + 1) * 9));
        }

        Minecraft mc = Minecraft.getInstance();
        RegistryOps<Tag> ops = mc.level != null ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess()) : null;

        ItemStack[] itemArray = Stream.generate(() -> ItemStack.EMPTY).limit(finalSize).toArray(ItemStack[]::new);
        if (ops != null) {
            for (Map.Entry<Integer, String> entry : slots.entrySet()) {
                int slot = entry.getKey();
                String nbt = entry.getValue();
                if (slot >= 0 && slot < itemArray.length && nbt != null && !nbt.isEmpty()) {
                    try {
                        ItemStack stack = me.bombo.bomboaddons.gui.StorageOverlayManager.parseItemStack(nbt, ops);
                        itemArray[slot] = stack;
                    } catch (Exception ignored) {}
                }
            }
        }

        storages[index] = new Storage(new SimpleContainer(itemArray), getStorageName(index), false);
    }

    public static class Storage {
        private final Container inventory;
        private final String name;
        private boolean dirty;

        public Storage(Container inventory, String name, boolean dirty) {
            this.inventory = inventory;
            this.name = name;
            this.dirty = dirty;
        }

        public String name() {
            return name;
        }

        public int size() {
            return inventory.getContainerSize();
        }

        public ItemStack getStack(int index) {
            if (index < 0 || index >= inventory.getContainerSize()) return ItemStack.EMPTY;
            return inventory.getItem(index);
        }

        public List<ItemStack> getItemList() {
            List<ItemStack> items = new ArrayList<>(size());
            for (int i = 0; i < size(); ++i) {
                items.add(getStack(i));
            }
            return items;
        }
    }
}
