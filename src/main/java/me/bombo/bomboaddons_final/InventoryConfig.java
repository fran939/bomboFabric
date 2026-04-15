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
import java.util.ArrayList;
import java.util.List;

public class InventoryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bomboaddons_inventories.json").toFile();

    private static List<InventorySnapshot> snapshots = new ArrayList<>();

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<List<InventorySnapshot>>() {
            }.getType();
            snapshots = GSON.fromJson(reader, type);
            if (snapshots == null) {
                snapshots = new ArrayList<>();
            }
        } catch (IOException e) {
            Bomboaddons.LOGGER.error("Failed to load inventory snapshots", e);
            snapshots = new ArrayList<>();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(snapshots, writer);
        } catch (IOException e) {
            Bomboaddons.LOGGER.error("Failed to save inventory snapshots", e);
        }
    }

    public static List<InventorySnapshot> getSnapshots() {
        return snapshots;
    }

    public static void addSnapshot(InventorySnapshot snapshot) {
        snapshots.add(snapshot);
        save();
    }
}
