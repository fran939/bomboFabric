package me.bombo.bomboaddons_final;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BazaarUtils {
   private static final Set<String> bazaarProducts = new HashSet();
   private static boolean initialized = false;

   public static void init() {
      if (!initialized) {
         initialized = true;
         CompletableFuture.runAsync(() -> {
            try {
               URL url = new URL("https://api.hypixel.net/skyblock/bazaar");
               HttpURLConnection conn = (HttpURLConnection)url.openConnection();
               conn.setRequestMethod("GET");
               conn.connect();
               if (conn.getResponseCode() == 200) {
                  InputStreamReader reader = new InputStreamReader(conn.getInputStream());

                  try {
                     JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                     if (json.has("products")) {
                        JsonObject products = json.getAsJsonObject("products");
                        synchronized(bazaarProducts) {
                           bazaarProducts.clear();
                           bazaarProducts.addAll(products.keySet());
                        }

                        System.out.println("[BomboAddons] Loaded " + bazaarProducts.size() + " bazaar products.");
                     }
                  } catch (Throwable var9) {
                     try {
                        reader.close();
                     } catch (Throwable var7) {
                        var9.addSuppressed(var7);
                     }

                     throw var9;
                  }

                  reader.close();
               }
            } catch (Exception var10) {
               var10.printStackTrace();
            }

         });
      }
   }

   public static int getProductCount() {
      synchronized(bazaarProducts) {
         return bazaarProducts.size();
      }
   }

   public static boolean isBazaarItem(String skyblockId) {
      synchronized(bazaarProducts) {
         return bazaarProducts.contains(skyblockId);
      }
   }
}
