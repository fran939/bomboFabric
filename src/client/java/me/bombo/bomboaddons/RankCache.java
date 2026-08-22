package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class RankCache {
   private static final Path CACHE_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/ranks.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final Map<String, String> cache = new ConcurrentHashMap();
   private static final Set<String> pendingFetches = ConcurrentHashMap.newKeySet();
   private static final Set<String> fetchedThisSession = ConcurrentHashMap.newKeySet();

   public static void load() {
      try {
         if (Files.exists(CACHE_PATH, new LinkOption[0])) {
            Reader reader = Files.newBufferedReader(CACHE_PATH);

            try {
               Type type = (new TypeToken<Map<String, String>>() {
               }).getType();
               Map<String, String> loaded = (Map)GSON.fromJson(reader, type);
               if (loaded != null) {
                  loaded.forEach((k, v) -> {
                     if (v != null) {
                        String clean = v.replaceAll("[§&].", "");
                        if (!clean.matches(".*\\d+.*")) {
                           cache.put(k, v);
                        }
                     }

                  });
               }
            } catch (Throwable var4) {
               if (reader != null) {
                  try {
                     reader.close();
                  } catch (Throwable var3) {
                     var4.addSuppressed(var3);
                  }
               }

               throw var4;
            }

            if (reader != null) {
               reader.close();
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.getUser() != null) {
         String name = mc.getUser().getName();
         if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Player")) {
            fetchAsync(name);
         }
      }

   }

   public static void save() {
      try {
         if (!Files.exists(CACHE_PATH.getParent(), new LinkOption[0])) {
            Files.createDirectories(CACHE_PATH.getParent());
         }

         Writer writer = Files.newBufferedWriter(CACHE_PATH);

         try {
            GSON.toJson(cache, writer);
         } catch (Throwable var4) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }
            }

            throw var4;
         }

         if (writer != null) {
            writer.close();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public static String getRank(String username) {
      if (username != null && !username.isEmpty()) {
         String lowerName = username.toLowerCase();
         if (!fetchedThisSession.contains(lowerName)) {
            fetchAsync(username);
         }

         String rank = (String)cache.get(lowerName);
         if (rank == null) {
            return "";
         } else {
            String clean = rank.replaceAll("[§&].", "");
            return clean.matches(".*\\d+.*") ? "" : rank;
         }
      } else {
         return "";
      }
   }

   public static void setRank(String username, String rank) {
      if (username != null && !username.isEmpty() && rank != null) {
         String lowerName = username.toLowerCase();
         cache.put(lowerName, rank);
         fetchedThisSession.add(lowerName);
         save();
      }
   }

   public static void fetchAsync(String username) {
      if (username != null && !username.isEmpty()) {
         String lowerName = username.toLowerCase();
         if (pendingFetches.add(lowerName)) {
            fetchedThisSession.add(lowerName);
            (new Thread(() -> {
               try {
                  InputStreamReader reader;
                  label123: {
                     URL url = (new URI(BomboApiUrl.getApiUrl("/nw/" + username))).toURL();
                     HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                     conn.setRequestMethod("GET");
                     conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                     conn.setConnectTimeout(5000);
                     conn.setReadTimeout(5000);
                     int status = conn.getResponseCode();
                     if (status == 200) {
                        reader = new InputStreamReader(conn.getInputStream(), "UTF-8");

                        try {
                           JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                           if (obj.has("data")) {
                              JsonObject data = obj.getAsJsonObject("data");
                              if (data.has("rank")) {
                                 String rank = data.get("rank").getAsString();
                                 if (rank != null) {
                                    cache.put(lowerName, rank);
                                    save();
                                    break label123;
                                 }
                              }
                           }
                        } catch (Throwable var15) {
                           try {
                              reader.close();
                           } catch (Throwable x2) {
                              var15.addSuppressed(x2);
                           }

                           throw var15;
                        }

                        reader.close();
                     }

                     if (!cache.containsKey(lowerName)) {
                        cache.put(lowerName, "");
                        save();
                     }

                     return;
                  }

                  reader.close();
               } catch (Exception var16) {
                  fetchedThisSession.remove(lowerName);
                  return;
               } finally {
                  pendingFetches.remove(lowerName);
               }

            }, "Rank-Fetch-" + username)).start();
         }
      }
   }
}
