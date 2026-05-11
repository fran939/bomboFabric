package me.bombo.bomboaddons_final;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class HotbarConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File OLD_CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bomboaddons_hotbars.json").toFile();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bombo/bomboaddons_hotbars.json").toFile();

    // Map of Snapshot ID -> Array of 9 SlotData objects
    private static Map<String, SlotData[]> snapshots = new HashMap<>();

    public static void load() {
        if (OLD_CONFIG_FILE.exists()) {
            try {
                if (!CONFIG_FILE.getParentFile().exists()) CONFIG_FILE.getParentFile().mkdirs();
                java.nio.file.Files.move(OLD_CONFIG_FILE.toPath(), CONFIG_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, SlotData[]>>() {
            }.getType();
            snapshots = GSON.fromJson(reader, type);
            if (snapshots == null) {
                snapshots = new HashMap<>();
            }
        } catch (IOException e) {
            Bomboaddons.LOGGER.error("Failed to load hotbar config", e);
            snapshots = new HashMap<>();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(snapshots, writer);
        } catch (IOException e) {
            Bomboaddons.LOGGER.error("Failed to save hotbar config", e);
        }
    }

    public static Map<String, SlotData[]> getSnapshots() {
        return snapshots;
    }

    public static void saveSnapshot(String id, SlotData[] data) {
        snapshots.put(id, data);
        save();
    }

    public static void deleteSnapshot(String id) {
        if (snapshots.remove(id) != null) {
            save();
        }
    }

    public static class SlotData {
        public String skyblockUuid; // The unique UUID of the specific item instance
        public String skyblockId; // e.g., "HYPERION", "ASPECT_OF_THE_DRAGONS"
        public String vanillaId; // e.g., "minecraft:diamond_sword"
        public String customName; // Fallback name if no SB data is present

        public SlotData() {
        }

        public SlotData(String skyblockUuid, String skyblockId, String vanillaId, String customName) {
            this.skyblockUuid = skyblockUuid;
            this.skyblockId = skyblockId;
            this.vanillaId = vanillaId;
            this.customName = customName;
        }
    }

    static {
        load();
    }
}
