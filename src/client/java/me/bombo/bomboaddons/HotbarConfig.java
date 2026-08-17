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
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class HotbarConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final File OLD_CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_hotbars.json").toFile();
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons_hotbars.json").toFile();
   private static Map<String, SlotData[]> snapshots = new HashMap();

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
               Type type = (new TypeToken<Map<String, SlotData[]>>() {
               }).getType();
               snapshots = (Map)GSON.fromJson(reader, type);
               if (snapshots == null) {
                  snapshots = new HashMap();
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
            Bomboaddons.LOGGER.error("Failed to load hotbar config", e);
            snapshots = new HashMap();
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

   static {
      load();
   }

   public static class SlotData {
      public String skyblockUuid;
      public String skyblockId;
      public String vanillaId;
      public String customName;

      public SlotData() {
      }

      public SlotData(String skyblockUuid, String skyblockId, String vanillaId, String customName) {
         this.skyblockUuid = skyblockUuid;
         this.skyblockId = skyblockId;
         this.vanillaId = vanillaId;
         this.customName = customName;
      }
   }
}
