package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class PlaytimeTracker {
   private static final File OLD_SAVE_FILE;
   private static final File SAVE_FILE;
   private static final Gson GSON;
   private static Map<String, AreaData> areaDataMap;
   private static String currentTrackedUser;
   private static long sessionStartTime;
   private static Vec3 lastPos;
   private static float lastYaw;
   private static float lastPitch;
   private static long lastMoveTime;
   private static boolean isAfk;
   private static long lastTickTime;
   public static long lastCloudSyncTime;
   private static String lastSyncedArea;

   private static File getSaveFile() {
      Minecraft mc = Minecraft.getInstance();
      String username = "default";
      if (mc.getUser() != null && mc.getUser().getName() != null) {
         username = mc.getUser().getName().toLowerCase(Locale.ROOT);
      }

      return new File(mc.gameDirectory, "config/bombo/bombo_playtime_" + username + ".json");
   }

   public static void load() {
      Minecraft mc = Minecraft.getInstance();
      String username = "default";
      if (mc.getUser() != null && mc.getUser().getName() != null) {
         username = mc.getUser().getName().toLowerCase(Locale.ROOT);
      }

      currentTrackedUser = username;
      File userFile = getSaveFile();
      if (OLD_SAVE_FILE.exists()) {
         try {
            if (!SAVE_FILE.getParentFile().exists()) {
               SAVE_FILE.getParentFile().mkdirs();
            }

            Files.move(OLD_SAVE_FILE.toPath(), SAVE_FILE.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      if (!userFile.exists() && SAVE_FILE.exists()) {
         try {
            Files.copy(SAVE_FILE.toPath(), userFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      if (!userFile.exists()) {
         areaDataMap = new HashMap();
      } else {
         try {
            FileReader reader = new FileReader(userFile);

            try {
               Type type = (new TypeToken<Map<String, AreaData>>() {
               }).getType();
               Map<String, AreaData> loaded = (Map) GSON.fromJson(reader, type);
               if (loaded != null) {
                  areaDataMap = new HashMap();
                  loaded.forEach((k, v) -> {
                     String norm = normalizeAreaName(k);
                     if (areaDataMap.containsKey(norm)) {
                        mergeData((AreaData) areaDataMap.get(norm), v);
                     } else {
                        areaDataMap.put(norm, v);
                     }

                  });
                  migrateMenuData();
                  migrateFarmingIslandsData();
                  migrateKuudraData();
                  migrateUnknownData();
                  save();
               }
            } catch (Throwable var9) {
               try {
                  reader.close();
               } catch (Throwable var6) {
                  var9.addSuppressed(var6);
               }

               throw var9;
            }

            reader.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      // Fetch cloud backup from server to restore / merge if local file is missing or
      // out of date
      final String finalTargetUser = username;
      if (!finalTargetUser.equals("default")) {
         CompletableFuture.runAsync(() -> {
            try {
               String apiUrl = BomboApiUrl.getApiUrl("/playtime/" + finalTargetUser);
               URL url = (new URI(apiUrl)).toURL();
               HttpURLConnection conn = (HttpURLConnection) url.openConnection();
               conn.setRequestMethod("GET");
               conn.setConnectTimeout(5000);
               conn.setReadTimeout(5000);
               int code = conn.getResponseCode();
               if (code == 200) {
                  try (java.io.InputStream is = conn.getInputStream();
                        java.io.InputStreamReader isr = new java.io.InputStreamReader(is,
                              java.nio.charset.StandardCharsets.UTF_8)) {
                     com.google.gson.JsonObject cloudObj = GSON.fromJson(isr, com.google.gson.JsonObject.class);
                     if (cloudObj != null && cloudObj.has("areaDataMap")) {
                        com.google.gson.JsonObject areas = cloudObj.getAsJsonObject("areaDataMap");
                        boolean mergedAny = false;
                        for (String key : areas.keySet()) {
                           AreaData cloudData = GSON.fromJson(areas.get(key), AreaData.class);
                           if (cloudData != null) {
                              String norm = normalizeAreaName(key);
                              if (areaDataMap.containsKey(norm)) {
                                 AreaData local = areaDataMap.get(norm);
                                 // Only take cloud data if cloud total time is greater than local time
                                 if (cloudData.totalTime > local.totalTime) {
                                    mergeData(local, cloudData);
                                    mergedAny = true;
                                 }
                              } else {
                                 areaDataMap.put(norm, cloudData);
                                 mergedAny = true;
                              }
                           }
                        }
                        if (mergedAny) {
                           migrateMenuData();
                           migrateFarmingIslandsData();
                           migrateKuudraData();
                           migrateUnknownData();
                           save();
                           DebugUtils.debug("playtime", "Successfully restored playtime data from cloud backup!");
                        }
                     }
                  }
               }
            } catch (Exception e) {
               DebugUtils.debug("playtime", "Failed to fetch cloud backup: " + e.getMessage());
            }
         });
      }
   }

   private static void mergeData(AreaData target, AreaData source) {
      target.totalTime += source.totalTime;
      target.afkTime += source.afkTime;
      source.dailyTime
            .forEach((date, time) -> target.dailyTime.put(date, (Long) target.dailyTime.getOrDefault(date, 0L) + time));
      source.dailyAfk
            .forEach((date, time) -> target.dailyAfk.put(date, (Long) target.dailyAfk.getOrDefault(date, 0L) + time));
      source.subAreas.forEach((subName, subData) -> {
         String normSub = normalizeAreaName(subName);
         if (target.subAreas.containsKey(normSub)) {
            mergeData((AreaData) target.subAreas.get(normSub), subData);
         } else {
            target.subAreas.put(normSub, subData);
         }

      });
   }

   private static void migrateMenuData() {
      if (areaDataMap.containsKey("Main Menu") || areaDataMap.containsKey("Multiplayer Menu")) {
         AreaData menuData = (AreaData) areaDataMap.computeIfAbsent("Menu", (k) -> new AreaData());
         String[] legacyNames = new String[] { "Main Menu", "Multiplayer Menu" };

         for (String oldName : legacyNames) {
            if (areaDataMap.containsKey(oldName)) {
               AreaData oldData = (AreaData) areaDataMap.remove(oldName);
               mergeData(menuData, oldData);
               menuData.subAreas.put(oldName, oldData);
            }
         }

      }
   }

   private static void migrateFarmingIslandsData() {
      if (areaDataMap.containsKey("The Barn") || areaDataMap.containsKey("Barn")
            || areaDataMap.containsKey("Mushroom Desert") || areaDataMap.containsKey("MushroomDesert")) {
         AreaData farmingData = (AreaData) areaDataMap.computeIfAbsent("Farming Islands", (k) -> new AreaData());
         String[] legacyNames = new String[] { "The Barn", "Barn", "Mushroom Desert", "MushroomDesert" };

         for (String oldName : legacyNames) {
            if (areaDataMap.containsKey(oldName)) {
               AreaData oldData = (AreaData) areaDataMap.remove(oldName);
               mergeData(farmingData, oldData);
               String normName = oldName;
               if (oldName.equalsIgnoreCase("Barn")) {
                  normName = "The Barn";
               }

               if (oldName.equalsIgnoreCase("MushroomDesert")) {
                  normName = "Mushroom Desert";
               }

               farmingData.subAreas.put(normName, oldData);
            }
         }

      }
   }

   private static void migrateKuudraData() {
      if (areaDataMap.containsKey("Kuudra") || areaDataMap.containsKey("T1") || areaDataMap.containsKey("T2")
            || areaDataMap.containsKey("T3") || areaDataMap.containsKey("T4") || areaDataMap.containsKey("T5")) {
         AreaData kuudraData = (AreaData) areaDataMap.computeIfAbsent("Kuudra's Hollow", (k) -> new AreaData());
         String[] legacyNames = new String[] { "Kuudra", "T1", "T2", "T3", "T4", "T5" };

         for (String oldName : legacyNames) {
            if (areaDataMap.containsKey(oldName)) {
               AreaData oldData = (AreaData) areaDataMap.remove(oldName);
               mergeData(kuudraData, oldData);
               String normName = oldName;
               if (oldName.equalsIgnoreCase("Kuudra")) {
                  normName = "Kuudra's Hollow";
               }

               kuudraData.subAreas.put(normName, oldData);
            }
         }

      }
   }

   private static void migrateUnknownData() {
      if (areaDataMap.containsKey("Unknown")) {
         AreaData unknownData = (AreaData) areaDataMap.get("Unknown");
         if (unknownData != null && unknownData.subAreas != null && !unknownData.subAreas.isEmpty()) {
            List<String> migratedSubs = new ArrayList();
            unknownData.subAreas.forEach((subNamex, subDatax) -> {
               String mappedArea = SkyblockUtils.mapSubAreaToMainArea(subNamex);
               if (!mappedArea.equals("Unknown")) {
                  AreaData targetArea = (AreaData) areaDataMap.computeIfAbsent(normalizeAreaName(mappedArea),
                        (k) -> new AreaData());
                  targetArea.totalTime += subDatax.totalTime;
                  targetArea.afkTime += subDatax.afkTime;
                  subDatax.dailyTime.forEach((date, time) -> targetArea.dailyTime.put(date,
                        (Long) targetArea.dailyTime.getOrDefault(date, 0L) + time));
                  subDatax.dailyAfk.forEach((date, time) -> targetArea.dailyAfk.put(date,
                        (Long) targetArea.dailyAfk.getOrDefault(date, 0L) + time));
                  String normSub = normalizeAreaName(subNamex);
                  if (targetArea.subAreas.containsKey(normSub)) {
                     mergeData((AreaData) targetArea.subAreas.get(normSub), subDatax);
                  } else {
                     targetArea.subAreas.put(normSub, subDatax);
                  }

                  migratedSubs.add(subNamex);
               }

            });

            for (String subName : migratedSubs) {
               AreaData subData = (AreaData) unknownData.subAreas.remove(subName);
               if (subData != null) {
                  unknownData.totalTime = Math.max(0L, unknownData.totalTime - subData.totalTime);
                  unknownData.afkTime = Math.max(0L, unknownData.afkTime - subData.afkTime);
                  subData.dailyTime.forEach((date, time) -> {
                     long oldVal = (Long) unknownData.dailyTime.getOrDefault(date, 0L);
                     unknownData.dailyTime.put(date, Math.max(0L, oldVal - time));
                  });
                  subData.dailyAfk.forEach((date, time) -> {
                     long oldVal = (Long) unknownData.dailyAfk.getOrDefault(date, 0L);
                     unknownData.dailyAfk.put(date, Math.max(0L, oldVal - time));
                  });
               }
            }

            if (unknownData.subAreas.isEmpty() && unknownData.totalTime < 1000L) {
               areaDataMap.remove("Unknown");
            }

         }
      }
   }

   public static void save() {
      File userFile = getSaveFile();

      try {
         String json = GSON.toJson(areaDataMap);
         CompletableFuture.runAsync(() -> {
            try {
               if (!userFile.getParentFile().exists()) {
                  userFile.getParentFile().mkdirs();
               }

               FileWriter writer = new FileWriter(userFile);

               try {
                  writer.write(json);
               } catch (Throwable var6) {
                  try {
                     writer.close();
                  } catch (Throwable x2) {
                     var6.addSuppressed(x2);
                  }

                  throw var6;
               }

               writer.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         });
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   private static String cachedToday = null;
   private static long lastDateCheck = 0L;

   public static void tick() {
      Minecraft mc = Minecraft.getInstance();
      long now = System.currentTimeMillis();
      long delta = now - lastTickTime;
      if (delta < 1000L) {
         return;
      }
      lastTickTime = now;

      String username = "default";
      if (mc.getUser() != null && mc.getUser().getName() != null) {
         username = mc.getUser().getName().toLowerCase(Locale.ROOT);
      }

      if (currentTrackedUser == null || !currentTrackedUser.equals(username)) {
         save();
         load();
      }

      boolean inWorld = mc.player != null && mc.level != null;
      if (inWorld) {
         Vec3 currentPos = mc.player.position();
         float currentYaw = mc.player.getYRot();
         float currentPitch = mc.player.getXRot();
         if (lastPos != null && currentPos.equals(lastPos) && currentYaw == lastYaw && currentPitch == lastPitch) {
            if (now - lastMoveTime > 60000L) {
               isAfk = true;
            }
         } else {
            lastMoveTime = now;
            isAfk = false;
         }

         lastPos = currentPos;
         lastYaw = currentYaw;
         lastPitch = currentPitch;
      } else {
         isAfk = true;
      }

      if (cachedToday == null || now - lastDateCheck > 60000L) {
         cachedToday = LocalDate.now().toString();
         lastDateCheck = now;
      }
      String today = cachedToday;

      String area = normalizeAreaName(BomboaddonsClient.currentArea);
      String subArea = normalizeAreaName(BomboaddonsClient.currentSubArea);
      AreaData data = (AreaData) areaDataMap.computeIfAbsent(area, (k) -> new AreaData());
      data.totalTime += delta;
      data.sessionTime += delta;
      data.dailyTime.put(today, (Long) data.dailyTime.getOrDefault(today, 0L) + delta);
      if (isAfk) {
         data.afkTime += delta;
         data.sessionAfkTime += delta;
         data.dailyAfk.put(today, (Long) data.dailyAfk.getOrDefault(today, 0L) + delta);
      }

      if (!subArea.equals("None") && !subArea.equalsIgnoreCase(area)) {
         String mappedMain = normalizeAreaName(SkyblockUtils.mapSubAreaToMainArea(subArea));
         if (mappedMain.equals("Unknown") || mappedMain.equalsIgnoreCase(area)) {
            AreaData subData = (AreaData) data.subAreas.computeIfAbsent(subArea, (k) -> new AreaData());
            subData.totalTime += delta;
            subData.sessionTime += delta;
            subData.dailyTime.put(today, (Long) subData.dailyTime.getOrDefault(today, 0L) + delta);
            if (isAfk) {
               subData.afkTime += delta;
               subData.sessionAfkTime += delta;
               subData.dailyAfk.put(today, (Long) subData.dailyAfk.getOrDefault(today, 0L) + delta);
            }
         }
      }

      if (now % 60000L < delta) {
         save();
      }

      if (now - lastCloudSyncTime > 300000L) {
         lastCloudSyncTime = now;
         sendPlaytimeDataToCloud();
      }
   }

   public static String normalizeAreaName(String name) {
      if (name != null && !name.isEmpty()) {
         String normalized = name.replaceAll("(?i)§.", "").trim();
         if (normalized.startsWith("The ")) {
            normalized = normalized.substring(4);
         }

         if (normalized.equalsIgnoreCase("Lobby")) {
            return "Lobby";
         } else {
            return normalized.equalsIgnoreCase("Hub") ? "Hub" : normalized;
         }
      } else {
         return "Unknown";
      }
   }

   public static Map<String, AreaData> getAreaDataMap() {
      return areaDataMap;
   }

   public static long getSessionTime() {
      return System.currentTimeMillis() - sessionStartTime;
   }

   public static boolean isAfk() {
      return isAfk;
   }

   public static String formatTime(long millis) {
      long hours = TimeUnit.MILLISECONDS.toHours(millis);
      long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60L;
      long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60L;
      if (hours > 0L) {
         return String.format("%dh %dm", hours, minutes);
      } else {
         return minutes > 0L ? String.format("%dm %ds", minutes, seconds) : String.format("%ds", seconds);
      }
   }

   public static void sendPlaytimeDataToCloud() {
      sendPlaytimeDataToCloud(true);
   }

   public static void sendPlaytimeDataToCloud(boolean async) {
      save();
      Minecraft mc = Minecraft.getInstance();
      String username = mc.getUser().getName();
      String uuid = mc.getUser().getProfileId().toString();
      if (username != null && uuid != null) {
         Map<String, Object> payload = new HashMap();
         payload.put("username", username);
         payload.put("uuid", uuid);
         payload.put("areaDataMap", areaDataMap);
         payload.put("sessionTime", getSessionTime());
         payload.put("currentArea", BomboaddonsClient.currentArea);
         payload.put("isAfk", isAfk());
         String json = GSON.toJson(payload);
         Runnable r = () -> {
            try {
               String apiUrl = BomboApiUrl.getApiUrl("/playtime");
               URL url = (new URI(apiUrl)).toURL();
               Bomboaddons.logApiRequest(apiUrl);
               HttpURLConnection conn = (HttpURLConnection) url.openConnection();
               conn.setRequestMethod("POST");
               conn.setRequestProperty("Content-Type", "application/json");
               conn.setDoOutput(true);
               OutputStream os = conn.getOutputStream();

               try {
                  byte[] input = json.getBytes("utf-8");
                  os.write(input, 0, input.length);
               } catch (Throwable var9) {
                  if (os != null) {
                     try {
                        os.close();
                     } catch (Throwable x2) {
                        var9.addSuppressed(x2);
                     }
                  }

                  throw var9;
               }

               if (os != null) {
                  os.close();
               }

               int responseCode = conn.getResponseCode();
               DebugUtils.debug("playtime", "Cloud sync response: " + responseCode);
               if (responseCode != 200) {
                  mc.execute(() -> {
                     if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal(
                              "§8[§bBomboAddons§8] §cFailed to sync playtime data (HTTP " + responseCode + ")"));
                     }

                  });
               }
            } catch (Exception e) {
               DebugUtils.debug("playtime", "Cloud sync failed: " + e.getMessage());
               mc.execute(() -> {
                  if (mc.player != null) {
                     mc.player.sendSystemMessage(
                           Component.literal("§8[§bBomboAddons§8] §cError syncing playtime data: " + e.getMessage()));
                  }

               });
            }

         };
         if (async) {
            (new Thread(r)).start();
         } else {
            r.run();
         }

      }
   }

   static {
      OLD_SAVE_FILE = new File(Minecraft.getInstance().gameDirectory, "config/bombo_playtime.json");
      SAVE_FILE = new File(Minecraft.getInstance().gameDirectory, "config/bombo/bombo_playtime.json");
      GSON = (new GsonBuilder()).setPrettyPrinting().create();
      areaDataMap = new HashMap();
      currentTrackedUser = null;
      sessionStartTime = System.currentTimeMillis();
      lastPos = Vec3.ZERO;
      lastYaw = 0.0F;
      lastPitch = 0.0F;
      lastMoveTime = System.currentTimeMillis();
      isAfk = false;
      lastTickTime = System.currentTimeMillis();
      lastCloudSyncTime = System.currentTimeMillis();
      lastSyncedArea = "None";
   }

   public static class AreaData {
      public long totalTime = 0L;
      public long afkTime = 0L;
      public Map<String, Long> dailyTime = new HashMap();
      public Map<String, Long> dailyAfk = new HashMap();
      public Map<String, AreaData> subAreas = new HashMap();
      public transient long sessionTime = 0L;
      public transient long sessionAfkTime = 0L;
   }
}
