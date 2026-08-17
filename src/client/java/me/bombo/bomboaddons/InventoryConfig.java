package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public class InventoryConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final File OLD_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_inventories.json").toFile();
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons_inventories.json").toFile();
   private static List<InventorySnapshot> snapshots = new ArrayList();

   public static void load() {
      if (OLD_CONFIG_FILE.exists()) {
         try {
            if (!CONFIG_FILE.getParentFile().exists()) {
               CONFIG_FILE.getParentFile().mkdirs();
            }

            Files.move(OLD_CONFIG_FILE.toPath(), CONFIG_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      if (!CONFIG_FILE.exists()) {
         save();
      } else {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               Type type = (new TypeToken<List<InventorySnapshot>>() {
               }).getType();
               snapshots = (List)GSON.fromJson(reader, type);
               if (snapshots == null) {
                  snapshots = new ArrayList();
               }
            } catch (Throwable var5) {
               try {
                  reader.close();
               } catch (Throwable var3) {
                  var5.addSuppressed(var3);
               }

               throw var5;
            }

            reader.close();
         } catch (IOException e) {
            Bomboaddons.LOGGER.error("Failed to load inventory snapshots", e);
            snapshots = new ArrayList();
         }

      }
   }

   public static void save() {
      try {
         FileWriter writer = new FileWriter(CONFIG_FILE);

         try {
            GSON.toJson(snapshots, writer);
         } catch (Throwable var4) {
            try {
               writer.close();
            } catch (Throwable var3) {
               var4.addSuppressed(var3);
            }

            throw var4;
         }

         writer.close();
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

   static {
      load();
   }
}
