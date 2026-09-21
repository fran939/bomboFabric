package me.bombo.bomboaddons.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ItemTradeManager {
   public static class TradeEntry {
      public final String name;
      public final String tag;
      public final boolean isBazaar;

      public TradeEntry(String name, String tag, boolean isBazaar) {
         this.name = name;
         this.tag = tag;
         this.isBazaar = isBazaar;
      }
   }

   // Indexed by tag (Skyblock ID), uppercase clean name, and raw tag
   private static final Map<String, TradeEntry> byTag = new ConcurrentHashMap<>();
   private static final Map<String, TradeEntry> byCleanName = new ConcurrentHashMap<>();
   private static boolean initialized = false;
   private static long lastFetchTime = 0L;

   public static void init() {
      if (!initialized || System.currentTimeMillis() - lastFetchTime > 3600000L) {
         initialized = true;
         lastFetchTime = System.currentTimeMillis();
         CompletableFuture.runAsync(() -> {
            try {
               String apiUrl = BomboApiUrl.getWebUrl("/mod/trade");
               URL url = new URL(apiUrl);
               HttpURLConnection conn = (HttpURLConnection) url.openConnection();
               conn.setRequestMethod("GET");
               conn.setConnectTimeout(5000);
               conn.setReadTimeout(10000);
               conn.connect();

               if (conn.getResponseCode() == 200) {
                  try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8)) {
                     JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
                     for (JsonElement el : array) {
                        if (el != null && el.isJsonObject()) {
                           JsonObject obj = el.getAsJsonObject();
                           String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : null;
                           String tag = obj.has("tag") && !obj.get("tag").isJsonNull() ? obj.get("tag").getAsString() : null;
                           
                           boolean isBazaar = false;
                           if (obj.has("flags")) {
                              JsonElement flagsEl = obj.get("flags");
                              if (flagsEl.isJsonPrimitive()) {
                                 String fStr = flagsEl.getAsString();
                                 if ("BAZAAR".equalsIgnoreCase(fStr)) {
                                    isBazaar = true;
                                 } else {
                                    try {
                                       int fInt = Integer.parseInt(fStr);
                                       // Bit 0 indicates Bazaar (e.g. 1, 17); Auction items have flags like 20, 36, 52 or "AUCTION"
                                       if ((fInt & 1) != 0) {
                                          isBazaar = true;
                                       }
                                    } catch (Exception ignored) {}
                                 }
                              }
                           }

                           if (tag != null && !tag.isEmpty()) {
                              TradeEntry entry = new TradeEntry(name, tag, isBazaar);
                              byTag.put(tag.toUpperCase(java.util.Locale.ROOT), entry);
                              if (name != null && !name.equalsIgnoreCase("null") && !name.trim().isEmpty()) {
                                 String clean = name.replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(java.util.Locale.ROOT);
                                 byCleanName.put(clean, entry);
                              }
                           }
                        }
                     }
                  }
               }
            } catch (Throwable t) {
               // Silently ignore or fallback
            }
         });
      }
   }

   public static TradeEntry getByTag(String skyblockId) {
      if (skyblockId == null) return null;
      init();
      return byTag.get(skyblockId.trim().toUpperCase(java.util.Locale.ROOT));
   }

   public static TradeEntry getByCleanName(String cleanName) {
      if (cleanName == null) return null;
      init();
      return byCleanName.get(cleanName.trim().toLowerCase(java.util.Locale.ROOT));
   }

   public static TradeEntry lookup(String skyblockId, String cleanName) {
      TradeEntry entry = getByTag(skyblockId);
      if (entry != null) return entry;
      return getByCleanName(cleanName);
   }
}
