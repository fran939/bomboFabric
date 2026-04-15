package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class BomboConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().registerTypeAdapter(BomboConfig.HighlightInfo.class, new BomboConfig.HighlightInfoAdapter()).create();
   private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons.json").toFile();
   private static BomboConfig.Settings settings = new BomboConfig.Settings();

   public static void load() {
      if (!CONFIG_FILE.exists()) {
         save();
      } else {
         try {
            FileReader reader = new FileReader(CONFIG_FILE);

            try {
               settings = (BomboConfig.Settings)GSON.fromJson(reader, BomboConfig.Settings.class);
               if (settings == null) {
                  settings = new BomboConfig.Settings();
               }
            } catch (Throwable var4) {
               try {
                  reader.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }

               throw var4;
            }

            reader.close();
         } catch (IOException var5) {
            var5.printStackTrace();
         }

      }
   }

   public static void save() {
      try {
         FileWriter writer = new FileWriter(CONFIG_FILE);

         try {
            GSON.toJson(settings, writer);
         } catch (Throwable var4) {
            try {
               writer.close();
            } catch (Throwable var3) {
               var4.addSuppressed(var3);
            }

            throw var4;
         }

         writer.close();
      } catch (IOException var5) {
         var5.printStackTrace();
      }

   }

   public static BomboConfig.Settings get() {
      return settings;
   }

   static {
      load();
   }

   public static class Settings {
      public boolean signCalculator = true;
      public boolean chestClicker = true;
      public boolean autoClicker = true;
      public boolean sbeCommands = true;
      public boolean leftClickEtherwarp = false;
      public int signCalcX = -1;
      public int signCalcY = -1;
      public boolean autoExperiments = false;
      public int experimentClickDelay = 200;
      public boolean experimentAutoClose = true;
      public int experimentSerumCount = 0;
      public boolean experimentGetMaxXp = false;
      public boolean sphinxMacro = false;
      public String chatPeekKey = "y";
      public String tradeKey = "n";
      public String recipeKey = "r";
      public String usageKey = "u";
      public Map<String, BomboConfig.HighlightInfo> highlights = new HashMap();
      public boolean debugMode = false;
   }

   public static class HighlightInfo {
      public String color;
      public boolean showInvisible;

      public HighlightInfo() {
      }

      public HighlightInfo(String color, boolean showInvisible) {
         this.color = color;
         this.showInvisible = showInvisible;
      }
   }

   public static class HighlightInfoAdapter extends TypeAdapter<BomboConfig.HighlightInfo> {
      public void write(JsonWriter out, BomboConfig.HighlightInfo value) throws IOException {
         if (value == null) {
            out.nullValue();
         } else {
            out.beginObject();
            out.name("color").value(value.color);
            out.name("showInvisible").value(value.showInvisible);
            out.endObject();
         }
      }

      public BomboConfig.HighlightInfo read(JsonReader in) throws IOException {
         if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
         } else if (in.peek() == JsonToken.STRING) {
            return new BomboConfig.HighlightInfo(in.nextString(), false);
         } else {
            BomboConfig.HighlightInfo info = new BomboConfig.HighlightInfo();
            in.beginObject();

            while(in.hasNext()) {
               String name = in.nextName();
               if (name.equals("color")) {
                  info.color = in.nextString();
               } else if (name.equals("showInvisible")) {
                  info.showInvisible = in.nextBoolean();
               } else {
                  in.skipValue();
               }
            }

            in.endObject();
            return info;
         }
      }
   }
}
