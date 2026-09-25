package me.bombo.bomboaddons.features.storageoverlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.StorageTracker;
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
    private static final Pattern ECHEST_PATTERN = Pattern.compile("(?:Ender Chest|Cofre de Ender).*\\((\\d+)/\\d+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ECHEST_PAGE_PATTERN = Pattern.compile("(?:Ender Chest|Cofre de Ender).*Page (\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK_PATTERN = Pattern.compile("(?:Backpack|Mochila).*\\((?:Slot|Ranura) #(\\d+)\\)", Pattern.CASE_INSENSITIVE);
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

        Matcher echest = ECHEST_PATTERN.matcher(title);
        if (echest.find()) {
            try {
                return Integer.parseInt(echest.group(1)) - 1;
            } catch (Exception ignored) {}
        }

        Matcher echestPage = ECHEST_PAGE_PATTERN.matcher(title);
        if (echestPage.find()) {
            try {
                return Integer.parseInt(echestPage.group(1)) - 1;
            } catch (Exception ignored) {}
        }

        Matcher backpack = BACKPACK_PATTERN.matcher(title);
        if (backpack.find()) {
            try {
                return Integer.parseInt(backpack.group(1)) + 8;
            } catch (Exception ignored) {}
        }

        return -1;
    }

    public static String getStorageName(int index) {
        if (index <= 8) {
            return "Ender Chest " + (index + 1);
        } else {
            return "Backpack " + (index - 8);
        }
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

        if (clean.equals("storage") || clean.startsWith("storage ") || clean.equals("almacenamiento")) {
            initializeStorage(container);
            return;
        }

        int index = getStorageIndexFromTitle(rawTitle);
        if (index >= 0 && index < STORAGE_SIZE) {
            storages[index] = new Storage(container, getStorageName(index), true);
            saveStorage(index);
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
            if (storages[index] == null) {
                storages[index] = new Storage(
                        new SimpleContainer(Stream.generate(() -> ItemStack.EMPTY).limit(54).toArray(ItemStack[]::new)),
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

            ItemLore lore = slotItem.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    Matcher m = BACKPACK_SIZE_PATTERN.matcher(line.getString());
                    if (m.find()) {
                        try {
                            int capacity = Integer.parseInt(m.group(1));
                            storages[index] = new Storage(
                                    new SimpleContainer(Stream.generate(() -> ItemStack.EMPTY).limit(capacity + 9).toArray(ItemStack[]::new)),
                                    getStorageName(index), true
                            );
                            break;
                        } catch (Exception ignored) {}
                    }
                }
            }
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
        if (saveDir == null || storages[index] == null) return;
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

                File file = saveDir.resolve(index + ".json").toFile();
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
        for (int i = 0; i < STORAGE_SIZE; i++) {
            storages[i] = null;
            if (saveDir == null) continue;
            File file = saveDir.resolve(i + ".json").toFile();
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
            } else {
                seedStorageFromStorageTracker(i);
            }
        }
    }

    private static void seedFromStorageTrackerIfEmpty() {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (storages[i] == null) {
                seedStorageFromStorageTracker(i);
            }
        }
    }

    private static void seedStorageFromStorageTracker(int index) {
        if (storages[index] != null) return;
        String trackerKey;
        if (index <= 8) {
            trackerKey = "Ender Chest (Page " + (index + 1) + ")";
            if (!StorageTracker.storageData.containsKey(trackerKey)) {
                trackerKey = "Ender Chest (" + (index + 1) + "/9)";
            }
        } else {
            trackerKey = "Backpack (Slot #" + (index - 8) + ")";
        }

        Map<Integer, String> slots = StorageTracker.storageData.get(trackerKey);
        if (slots == null || slots.isEmpty()) return;

        int maxSlot = 53;
        for (Integer slotNum : slots.keySet()) {
            if (slotNum > maxSlot) maxSlot = slotNum;
        }

        Minecraft mc = Minecraft.getInstance();
        RegistryOps<Tag> ops = mc.level != null ? RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess()) : null;

        ItemStack[] itemArray = Stream.generate(() -> ItemStack.EMPTY).limit(maxSlot + 1).toArray(ItemStack[]::new);
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
