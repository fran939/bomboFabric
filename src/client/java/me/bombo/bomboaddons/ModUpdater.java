package me.bombo.bomboaddons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ModUpdater {
   private static final String REPO = "fran939/bomboFabric";
   private static final String GITHUB_API_LIST = "https://api.github.com/repos/fran939/bomboFabric/releases";
   public static boolean updatedThisSession = false;
   public static boolean hasCheckedForUpdates = false;
   private static final Path PENDING_DELETE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_pending_delete.txt");

   public static void init() {
      if (Files.exists(PENDING_DELETE, new LinkOption[0])) {
         try {
            List<String> lines = Files.readAllLines(PENDING_DELETE, StandardCharsets.UTF_8);
            File currentJar = getCurrentJar();
            String currentPath = currentJar != null ? currentJar.getAbsolutePath() : "";

            for(String line : lines) {
               String oldJarPath = line.trim();
               if (!oldJarPath.isEmpty()) {
                  File oldJar = new File(oldJarPath);
                  if (oldJar.exists() && !oldJarPath.equals(currentPath)) {
                     if (oldJar.delete()) {
                        Bomboaddons.LOGGER.info("[BomboAddons] Deleted old version: " + oldJarPath);
                     } else {
                        Bomboaddons.LOGGER.warn("[BomboAddons] FAILED to delete old version: " + oldJarPath);
                     }
                  }
               }
            }

            Files.deleteIfExists(PENDING_DELETE);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

   }

   public static void checkAndUpdate(boolean silent) {
      checkAndUpdate(silent, BomboConfig.get().isBetaUpdateChannel());
   }

   public static void checkAndUpdate(boolean silent, boolean includeBetas) {
      if (!silent || !hasCheckedForUpdates) {
         hasCheckedForUpdates = true;
         (new Thread(() -> {
            try {
               String channelLabel = includeBetas ? "Betas & Full" : "Full Only";
               if (!silent) {
                  sendMessage("§7Checking for updates §8[§e" + channelLabel + "§8]§7...");
               }

               String latestVersion = null;
               String downloadUrl = null;
               String targetFilename = null;
               boolean isBeta = false;

               // 1. Try server API /mod/version
               try {
                  String updateApiUrl = "https://api.bombo.dpdns.org/mod/version";
                  Bomboaddons.logApiRequest(updateApiUrl);
                  HttpURLConnection conn = (HttpURLConnection) (new URL(updateApiUrl)).openConnection();
                  conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BomboAddons");
                  conn.setConnectTimeout(4000);
                  conn.setReadTimeout(4000);
                  if (conn.getResponseCode() == 200) {
                     BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                     JsonObject infoObj = JsonParser.parseReader(reader).getAsJsonObject();
                     JsonObject latestFull = infoObj.has("latestFull") && !infoObj.get("latestFull").isJsonNull() ? infoObj.getAsJsonObject("latestFull") : null;
                     JsonObject latestBeta = infoObj.has("latestBeta") && !infoObj.get("latestBeta").isJsonNull() ? infoObj.getAsJsonObject("latestBeta") : null;

                     if (!includeBetas) {
                        // Full versions only
                        if (latestFull != null && latestFull.has("version")) {
                           latestVersion = latestFull.get("version").getAsString();
                           if (latestFull.has("downloadUrl")) downloadUrl = latestFull.get("downloadUrl").getAsString();
                           if (latestFull.has("filename")) targetFilename = latestFull.get("filename").getAsString();
                           isBeta = false;
                        }
                     } else {
                        // Betas & Full releases: pick whichever is newer
                        String fullVer = latestFull != null && latestFull.has("version") ? latestFull.get("version").getAsString() : null;
                        String betaVer = latestBeta != null && latestBeta.has("version") ? latestBeta.get("version").getAsString() : null;

                        if (betaVer != null && fullVer != null) {
                           if (compareVersions(betaVer, fullVer) >= 0) {
                              latestVersion = betaVer;
                              if (latestBeta.has("downloadUrl")) downloadUrl = latestBeta.get("downloadUrl").getAsString();
                              if (latestBeta.has("filename")) targetFilename = latestBeta.get("filename").getAsString();
                              isBeta = true;
                           } else {
                              latestVersion = fullVer;
                              if (latestFull.has("downloadUrl")) downloadUrl = latestFull.get("downloadUrl").getAsString();
                              if (latestFull.has("filename")) targetFilename = latestFull.get("filename").getAsString();
                              isBeta = false;
                           }
                        } else if (betaVer != null) {
                           latestVersion = betaVer;
                           if (latestBeta.has("downloadUrl")) downloadUrl = latestBeta.get("downloadUrl").getAsString();
                           if (latestBeta.has("filename")) targetFilename = latestBeta.get("filename").getAsString();
                           isBeta = true;
                        } else if (fullVer != null) {
                           latestVersion = fullVer;
                           if (latestFull.has("downloadUrl")) downloadUrl = latestFull.get("downloadUrl").getAsString();
                           if (latestFull.has("filename")) targetFilename = latestFull.get("filename").getAsString();
                           isBeta = false;
                        }
                     }
                  }
               } catch (Throwable ignored) {}

               // 2. Specific endpoint fallbacks if /mod/version didn't provide it
               if (latestVersion == null || downloadUrl == null) {
                  try {
                     String endpoint = includeBetas ? "https://api.bombo.dpdns.org/mod/version/beta" : "https://api.bombo.dpdns.org/mod/version/full";
                     HttpURLConnection conn = (HttpURLConnection) (new URL(endpoint)).openConnection();
                     conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                     conn.setConnectTimeout(4000);
                     conn.setReadTimeout(4000);
                     if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("latest") && !obj.get("latest").isJsonNull()) {
                           JsonObject l = obj.getAsJsonObject("latest");
                           latestVersion = l.has("version") ? l.get("version").getAsString() : null;
                           if (l.has("downloadUrl")) downloadUrl = l.get("downloadUrl").getAsString();
                           if (l.has("filename")) targetFilename = l.get("filename").getAsString();
                           isBeta = l.has("isBeta") && l.get("isBeta").getAsBoolean();
                        }
                     }
                  } catch (Throwable ignored) {}
               }

               // 3. Fallback to GitHub Releases API if server is down
               if (latestVersion == null || downloadUrl == null) {
                  try {
                     String ghUrl = "https://api.github.com/repos/fran939/bomboFabric/releases";
                     HttpURLConnection ghConn = (HttpURLConnection) (new URL(ghUrl)).openConnection();
                     ghConn.setRequestProperty("User-Agent", "BomboAddons");
                     ghConn.setConnectTimeout(5000);
                     ghConn.setReadTimeout(5000);
                     if (ghConn.getResponseCode() == 200) {
                        BufferedReader ghReader = new BufferedReader(new InputStreamReader(ghConn.getInputStream(), StandardCharsets.UTF_8));
                        com.google.gson.JsonArray releases = JsonParser.parseReader(ghReader).getAsJsonArray();
                        for (com.google.gson.JsonElement el : releases) {
                           JsonObject r = el.getAsJsonObject();
                           String tag = (r.has("tag_name") ? r.get("tag_name").getAsString() : "").replaceFirst("^v", "");
                           boolean isPre = r.has("prerelease") && r.get("prerelease").getAsBoolean();
                           boolean isSubversion = tag.split("\\.").length >= 4;

                           if (!includeBetas && (isPre || isSubversion)) {
                              continue; // skip betas when Full Only is requested
                           }

                           if (r.has("assets")) {
                              for (com.google.gson.JsonElement aEl : r.getAsJsonArray("assets")) {
                                 JsonObject a = aEl.getAsJsonObject();
                                 String name = a.get("name").getAsString();
                                 if (name.endsWith(".jar") && !name.contains("sources") && !name.contains("dev")) {
                                    latestVersion = tag;
                                    downloadUrl = a.get("browser_download_url").getAsString();
                                    targetFilename = name;
                                    isBeta = isSubversion || isPre;
                                    break;
                                 }
                              }
                           }
                           if (latestVersion != null) break;
                        }
                     }
                  } catch (Throwable ignored) {}
               }

               String mcVersion = ((ModContainer) FabricLoader.getInstance().getModContainer("minecraft").get()).getMetadata().getVersion().getFriendlyString();
               String currentVersion = ((ModContainer) FabricLoader.getInstance().getModContainer("bomboaddons").get()).getMetadata().getVersion().getFriendlyString();
               if (currentVersion.equals("${version}")) {
                  if (!silent) {
                     sendMessage("§cRunning in dev environment with unset version. Update check skipped.");
                  }
                  return;
               }

               if (latestVersion == null || downloadUrl == null) {
                  if (!silent) {
                     sendMessage("§cNo releases or update jars found for channel: §e" + channelLabel);
                  }
                  return;
               }

               int comparison = compareVersions(latestVersion, currentVersion);
               if (comparison <= 0) {
                  if (!silent) {
                     if (comparison == 0) {
                        sendMessage("§aMod is up to date! (v" + currentVersion + " §8- §b" + channelLabel + "§a)");
                     } else {
                        sendMessage("§aMod is up to date! (Current v" + currentVersion + " is newer than latest " + channelLabel + " release v" + latestVersion + ")");
                     }
                  }
                  return;
               }

               String releaseTag = isBeta ? "§d[Beta]" : "§a[Full Release]";
               if (silent) {
                  sendMessage("§eUpdate found " + releaseTag + ": §b" + latestVersion + " §7(Current: " + currentVersion + ")");
                  sendMessage("§7Run §b/b update §7to download and install the new version.");
                  return;
               }

               sendMessage("§eUpdate found " + releaseTag + ": §b" + latestVersion + " §7(Current: " + currentVersion + ")");
               sendMessage("§7Downloading update §b" + latestVersion + "§7...");

               Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
               File currentJar = getCurrentJar();
               if (currentJar == null) {
                  sendMessage("§cFailed to identify current mod JAR location!");
                  return;
               }

               String finalFilename = targetFilename != null && !targetFilename.isEmpty()
                     ? targetFilename
                     : (latestVersion.startsWith("26.") || latestVersion.startsWith("1.")
                           ? "bomboaddons-" + latestVersion + ".jar"
                           : "bomboaddons-" + mcVersion + "-" + latestVersion + ".jar");

               File newJarFile = modsFolder.resolve(finalFilename).toFile();
               Bomboaddons.logApiRequest(downloadUrl);

               HttpURLConnection dlConn = (HttpURLConnection) (new URL(downloadUrl)).openConnection();
               dlConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BomboAddons");
               dlConn.setInstanceFollowRedirects(true);
               dlConn.setConnectTimeout(10000);
               dlConn.setReadTimeout(60000);

               try (InputStream in = dlConn.getInputStream()) {
                  Files.copy(in, newJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
               }

               updatedThisSession = true;
               StringBuilder pending = new StringBuilder();
               if (Files.exists(modsFolder, new LinkOption[0])) {
                  try (Stream<Path> stream = Files.list(modsFolder)) {
                     stream.filter((p) -> p.getFileName().toString().startsWith("bomboaddons") && p.getFileName().toString().endsWith(".jar"))
                           .filter((p) -> !p.equals(newJarFile.toPath()))
                           .forEach((p) -> pending.append(p.toAbsolutePath().toString()).append("\n"));
                  }
               }

               Files.writeString(PENDING_DELETE, pending.toString(), StandardCharsets.UTF_8);
               sendMessage("§aUpdate downloaded successfully: §b" + newJarFile.getName());
               sendMessage("§eThe old version will be automatically removed on next restart.");
            } catch (Exception var21) {
               if (!silent) {
                  sendMessage("§cError while updating: " + var21.getMessage());
               }
               var21.printStackTrace();
            }
         })).start();
      }
   }

   private static int compareVersions(String v1, String v2) {
      String[] parts1 = v1.split("\\.");
      String[] parts2 = v2.split("\\.");
      int length = Math.max(parts1.length, parts2.length);

      for(int i = 0; i < length; ++i) {
         int p1 = 0;
         int p2 = 0;

         try {
            if (i < parts1.length) {
               p1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
            }

            if (i < parts2.length) {
               p2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
            }
         } catch (NumberFormatException var9) {
         }

         if (p1 < p2) {
            return -1;
         }

         if (p1 > p2) {
            return 1;
         }
      }

      return 0;
   }

   public static void showChangelog() {
      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> mc.setScreenAndShow(new me.bombo.bomboaddons.gui.ChangelogScreen(mc.gui.screen())));
   }

   public static void installSpecificVersion(String targetVersion) {
      if (targetVersion == null || targetVersion.trim().isEmpty()) {
         sendMessage("§cUsage: /b version <targetVersion>");
         return;
      }
      String cleanTarget = targetVersion.replaceAll("^v", "").trim();
      (new Thread(() -> {
         try {
            sendMessage("§7Looking up version §e" + cleanTarget + "§7...");
            String downloadUrl = null;

            // 1. Try server API
            try {
               String apiUrl = "https://api.bombo.dpdns.org/mod/version/" + cleanTarget;
               HttpURLConnection conn = (HttpURLConnection)(new URL(apiUrl)).openConnection();
               conn.setRequestProperty("User-Agent", "Mozilla/5.0");
               conn.setConnectTimeout(4000);
               conn.setReadTimeout(4000);
               if (conn.getResponseCode() == 200) {
                  BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                  JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                  if (obj.has("downloadUrl")) {
                     downloadUrl = obj.get("downloadUrl").getAsString();
                  } else if (obj.has("download_url")) {
                     downloadUrl = obj.get("download_url").getAsString();
                  }
               }
            } catch (Throwable ignored) {}

            // 2. Try direct github release fallback
            if (downloadUrl == null) {
               try {
                  String ghUrl = "https://api.github.com/repos/fran939/bomboFabric/releases";
                  HttpURLConnection ghConn = (HttpURLConnection)(new URL(ghUrl)).openConnection();
                  ghConn.setRequestProperty("User-Agent", "BomboAddons");
                  ghConn.setConnectTimeout(5000);
                  ghConn.setReadTimeout(5000);
                  if (ghConn.getResponseCode() == 200) {
                     BufferedReader ghReader = new BufferedReader(new InputStreamReader(ghConn.getInputStream(), StandardCharsets.UTF_8));
                     com.google.gson.JsonArray releases = JsonParser.parseReader(ghReader).getAsJsonArray();
                     for (com.google.gson.JsonElement rEl : releases) {
                        JsonObject rObj = rEl.getAsJsonObject();
                        String tag = (rObj.has("tag_name") ? rObj.get("tag_name").getAsString() : "").replaceFirst("^v", "");
                        if (tag.equalsIgnoreCase(cleanTarget)) {
                           if (rObj.has("assets")) {
                              for (com.google.gson.JsonElement aEl : rObj.getAsJsonArray("assets")) {
                                 JsonObject a = aEl.getAsJsonObject();
                                 String name = a.get("name").getAsString();
                                 if (name.endsWith(".jar") && !name.contains("sources") && !name.contains("dev")) {
                                    downloadUrl = a.get("browser_download_url").getAsString();
                                    break;
                                 }
                              }
                           }
                           break;
                        }
                     }
                  }
               } catch (Throwable ignored) {}
            }

            if (downloadUrl == null) {
               sendMessage("§cCould not locate release jar for version: §e" + cleanTarget);
               return;
            }

            sendMessage("§aFound version §b" + cleanTarget + "§a! Downloading...");
            Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
            String mcVersion = ((ModContainer)FabricLoader.getInstance().getModContainer("minecraft").get()).getMetadata().getVersion().getFriendlyString();
            String jarName = cleanTarget.startsWith("26.") || cleanTarget.startsWith("1.")
                  ? "bomboaddons-" + cleanTarget + ".jar"
                  : "bomboaddons-" + mcVersion + "-" + cleanTarget + ".jar";
            File newJarFile = modsFolder.resolve(jarName).toFile();

            HttpURLConnection dlConn = (HttpURLConnection) (new URL(downloadUrl)).openConnection();
            dlConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BomboAddons");
            dlConn.setInstanceFollowRedirects(true);
            dlConn.setConnectTimeout(10000);
            dlConn.setReadTimeout(60000);

            try (InputStream in = dlConn.getInputStream()) {
               Files.copy(in, newJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            updatedThisSession = true;
            StringBuilder pending = new StringBuilder();
            if (Files.exists(modsFolder, new LinkOption[0])) {
               try (Stream<Path> stream = Files.list(modsFolder)) {
                  stream.filter((p) -> p.getFileName().toString().startsWith("bomboaddons-") && p.getFileName().toString().endsWith(".jar"))
                        .filter((p) -> !p.equals(newJarFile.toPath()))
                        .forEach((p) -> pending.append(p.toAbsolutePath().toString()).append("\n"));
               }
            }
            Files.writeString(PENDING_DELETE, pending.toString(), StandardCharsets.UTF_8);
            sendMessage("§aVersion installed: §b" + newJarFile.getName());
            sendMessage("§eRestart Minecraft to switch to version §b" + cleanTarget + "§e.");
         } catch (Exception e) {
            sendMessage("§cError downloading version: " + e.getMessage());
         }
      })).start();
   }

   public static void installPreviousVersion() {
      (new Thread(() -> {
         try {
            sendMessage("§7Finding previous version to downgrade...");
            String currentVersion = ((ModContainer)FabricLoader.getInstance().getModContainer("bomboaddons").get()).getMetadata().getVersion().getFriendlyString();

            // Fetch GitHub releases
            String ghUrl = "https://api.github.com/repos/fran939/bomboFabric/releases";
            HttpURLConnection ghConn = (HttpURLConnection)(new URL(ghUrl)).openConnection();
            ghConn.setRequestProperty("User-Agent", "BomboAddons");
            ghConn.setConnectTimeout(5000);
            ghConn.setReadTimeout(5000);
            if (ghConn.getResponseCode() == 200) {
               BufferedReader ghReader = new BufferedReader(new InputStreamReader(ghConn.getInputStream(), StandardCharsets.UTF_8));
               com.google.gson.JsonArray releases = JsonParser.parseReader(ghReader).getAsJsonArray();
               String targetVer = null;
               for (com.google.gson.JsonElement rEl : releases) {
                  JsonObject rObj = rEl.getAsJsonObject();
                  String tag = (rObj.has("tag_name") ? rObj.get("tag_name").getAsString() : "").replaceFirst("^v", "");
                  if (!tag.isEmpty() && compareVersions(tag, currentVersion) < 0) {
                     targetVer = tag;
                     break;
                  }
               }
               if (targetVer != null) {
                  installSpecificVersion(targetVer);
                  return;
               }
            }
            sendMessage("§cNo previous version found to downgrade to!");
         } catch (Exception e) {
            sendMessage("§cError finding previous version: " + e.getMessage());
         }
      })).start();
   }

   private static File getCurrentJar() {
      try {
         return new File(ModUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      } catch (Exception var1) {
         return null;
      }
   }

   private static void sendMessage(String msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> mc.player.sendSystemMessage(Component.literal("§6[Updater] " + msg)));
      }

   }
}
