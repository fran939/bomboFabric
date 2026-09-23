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

               // Flavor gate: an update may only install the artifact belonging to the build
               // that is running. This is what stops the cheat build from replacing itself with
               // the legit jar (and vice versa) just because it happens to be the newest release.
               //
               // Note the order: this is evaluated *after* the version comparison below. The old
               // code blanked the version first, so a build whose flavor had no published artifact
               // reported "no releases found" even when it was simply up to date on a channel the
               // catalog clearly had entries for.
               boolean flavorMatch = matchesFlavor(targetFilename, downloadUrl);

               String mcVersion = ((ModContainer) FabricLoader.getInstance().getModContainer("minecraft").get()).getMetadata().getVersion().getFriendlyString();
               String currentVersion = Constants.myVersion();
               if (currentVersion.equals("${version}")) {
                  if (!silent) {
                     sendMessage("§cRunning in dev environment with unset version. Update check skipped.");
                  }
                  return;
               }

               if (latestVersion == null) {
                  if (!silent) {
                     sendMessage("§cNo releases found for channel: §e" + channelLabel);
                     sendMessage("§7Checked §bapi.bombo.dpdns.org/mod/version§7 and the GitHub releases.");
                  }
                  return;
               }

               // Never cross Minecraft versions: a 26.1.x install must not "update" itself to a
               // 26.2.x jar (different loader build - it would not even load). Versions are
               // mc.mod.sub, so the first two segments identify the Minecraft line.
               if (!sameMinecraftLine(currentVersion, latestVersion)) {
                  if (!silent) {
                     sendMessage("§eUpdate §b" + latestVersion + "§e is for a different Minecraft version.");
                     sendMessage("§7This install is §b" + currentVersion + "§7 (MC " + mcVersion
                           + "). Get the matching build for MC §b" + mcVersion + "§7 from §b/b update versions§7.");
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

               // There really is a newer version; now we need our own artifact for it.
               if (!flavorMatch || downloadUrl == null) {
                  if (targetFilename != null && !targetFilename.isEmpty()) {
                     System.err.println("[Bombo] Ignoring " + targetFilename + " - flavor "
                           + Constants.FLAVOR + " expects " + Constants.artifactFilePrefix() + "*.jar");
                  }
                  if (!silent) {
                     sendMessage("§eUpdate §b" + latestVersion + "§e exists, but no §b"
                           + Constants.artifactPrefix() + "-*.jar§e artifact is published for it.");
                     if (targetFilename != null && !targetFilename.isEmpty()) {
                        sendMessage("§7Only published artifact: §f" + targetFilename);
                     }
                     sendMessage("§7This build is §b" + Constants.identityLine()
                           + "§7. Use §b/b update switch§7 to move to the other build, "
                           + "or publish a §b" + Constants.artifactPrefix() + "§7 release.");
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
                           ? Constants.artifactFilePrefix() + latestVersion + ".jar"
                           : Constants.artifactFilePrefix() + mcVersion + "-" + latestVersion + ".jar");

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
                     // Scoped to this flavor's prefix so the pending delete list can never
                     // remove the artifact of the other flavor.
                     stream.filter((p) -> p.getFileName().toString().startsWith(Constants.artifactFilePrefix())
                                 && p.getFileName().toString().endsWith(".jar"))
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

   /**
    * True when a catalog entry belongs to the flavor that is running.
    * Filenames are the source of truth because the release catalog lists every artifact.
    */
   private static boolean matchesFlavor(String filename, String downloadUrl) {
      String prefix = Constants.artifactFilePrefix();
      if (filename != null && !filename.isEmpty()) {
         return filename.startsWith(prefix);
      }
      if (downloadUrl != null && !downloadUrl.isEmpty()) {
         String tail = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
         return tail.startsWith(prefix);
      }
      return false;
   }

   /** Prefix of the jar this build would install if the user ran {@code /b update switch}. */
   private static String otherFlavorPrefix() {
      return Constants.CHEAT_FLAVOR ? "bomboaddons-" : "bomboclient-";
   }

   /**
    * Downloads the other flavor's artifact and queues this one for removal on restart.
    *
    * <p>Deliberately explicit and two-step: nothing is replaced until Minecraft restarts.
    */
   public static void installOtherFlavor() {
      final String targetPrefix = otherFlavorPrefix();
      final String targetName = Constants.CHEAT_FLAVOR ? "BomboAddons (legit)" : "BomboClient (cheat)";

      (new Thread(() -> {
         try {
            sendMessage("§7Looking for the §b" + targetName + " §7artifact (" + targetPrefix + "*.jar)...");

            String downloadUrl = null;
            String filename = null;

            HttpURLConnection ghConn = (HttpURLConnection) (new URL(GITHUB_API_LIST)).openConnection();
            ghConn.setRequestProperty("User-Agent", "BomboAddons");
            ghConn.setConnectTimeout(5000);
            ghConn.setReadTimeout(5000);
            if (ghConn.getResponseCode() == 200) {
               BufferedReader ghReader = new BufferedReader(new InputStreamReader(ghConn.getInputStream(), StandardCharsets.UTF_8));
               com.google.gson.JsonArray releases = JsonParser.parseReader(ghReader).getAsJsonArray();
               for (com.google.gson.JsonElement rEl : releases) {
                  JsonObject rObj = rEl.getAsJsonObject();
                  if (!rObj.has("assets")) continue;
                  for (com.google.gson.JsonElement aEl : rObj.getAsJsonArray("assets")) {
                     JsonObject a = aEl.getAsJsonObject();
                     String name = a.has("name") ? a.get("name").getAsString() : "";
                     if (name.startsWith(targetPrefix) && name.endsWith(".jar")
                           && !name.contains("sources") && !name.contains("dev")) {
                        filename = name;
                        downloadUrl = a.get("browser_download_url").getAsString();
                        break;
                     }
                  }
                  if (downloadUrl != null) break;
               }
            }

            if (downloadUrl == null || filename == null) {
               sendMessage("§cNo §b" + targetName + " §cartifact found in the release catalog yet.");
               return;
            }

            Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
            File newJarFile = modsFolder.resolve(filename).toFile();

            sendMessage("§7Downloading §b" + filename + "§7...");
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
            pending.append(Files.exists(PENDING_DELETE) ? Files.readString(PENDING_DELETE, StandardCharsets.UTF_8) : "");
            File currentJar = getCurrentJar();
            if (currentJar != null) {
               pending.append(currentJar.getAbsolutePath()).append("\n");
            }
            Files.writeString(PENDING_DELETE, pending.toString(), StandardCharsets.UTF_8);

            sendMessage("§aDownloaded §b" + filename + "§a.");
            sendMessage("§eRestart Minecraft to run the §b" + targetName + " §eflavor. The current jar is removed on restart.");
         } catch (Exception e) {
            sendMessage("§cError switching flavor: " + e.getMessage());
         }
      })).start();
   }

   /**
     * True when both versions belong to the same Minecraft line (same first two segments:
     * {@code 26.2.28.35} and {@code 26.2.29} share {@code 26.2}; {@code 26.1.4.2} does not).
     */
   private static boolean sameMinecraftLine(String a, String b) {
      if (a == null || b == null) return true; // unknown - do not block on our own parse gaps
      String[] pa = a.trim().split("\\.");
      String[] pb = b.trim().split("\\.");
      if (pa.length < 2 || pb.length < 2) return true;
      return pa[0].equals(pb[0]) && pa[1].equals(pb[1]);
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
                  ? Constants.artifactFilePrefix() + cleanTarget + ".jar"
                  : Constants.artifactFilePrefix() + mcVersion + "-" + cleanTarget + ".jar";
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
                  stream.filter((p) -> p.getFileName().toString().startsWith(Constants.artifactFilePrefix())
                              && p.getFileName().toString().endsWith(".jar"))
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
            String currentVersion = Constants.myVersion();

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
