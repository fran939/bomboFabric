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
      if (!silent || !hasCheckedForUpdates) {
         hasCheckedForUpdates = true;
         (new Thread(() -> {
            try {
               if (!silent) {
                  sendMessage("§7Checking for updates...");
               }

               String latestVersion = null;
               String downloadUrl = null;

               String updateApiUrl = "https://api.bombo.dpdns.org/downloads/latest/info";
               try {
                  Bomboaddons.logApiRequest(updateApiUrl);
                  HttpURLConnection conn = (HttpURLConnection)(new URL(updateApiUrl)).openConnection();
                  conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                  conn.setConnectTimeout(3000);
                  conn.setReadTimeout(3000);
                  if (conn.getResponseCode() == 200) {
                     BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                     JsonObject infoObj = JsonParser.parseReader(reader).getAsJsonObject();
                     if (infoObj.has("latestVersion")) latestVersion = infoObj.get("latestVersion").getAsString();
                     if (infoObj.has("downloadUrl")) downloadUrl = infoObj.get("downloadUrl").getAsString();
                  }
               } catch (Throwable ignored) {}

               if (latestVersion == null || downloadUrl == null) {
                  try {
                     String ghUrl = "https://api.github.com/repos/fran939/bomboFabric/releases/latest";
                     HttpURLConnection ghConn = (HttpURLConnection)(new URL(ghUrl)).openConnection();
                     ghConn.setRequestProperty("User-Agent", "BomboAddons");
                     ghConn.setConnectTimeout(5000);
                     ghConn.setReadTimeout(5000);
                     if (ghConn.getResponseCode() == 200) {
                        BufferedReader ghReader = new BufferedReader(new InputStreamReader(ghConn.getInputStream()));
                        JsonObject ghObj = JsonParser.parseReader(ghReader).getAsJsonObject();
                        if (ghObj.has("tag_name")) {
                           String tag = ghObj.get("tag_name").getAsString();
                           latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                        }
                        if (ghObj.has("assets")) {
                           com.google.gson.JsonArray assets = ghObj.getAsJsonArray("assets");
                           for (com.google.gson.JsonElement el : assets) {
                              JsonObject a = el.getAsJsonObject();
                              String name = a.get("name").getAsString();
                              if (name.endsWith(".jar") && !name.contains("sources") && !name.contains("dev")) {
                                 downloadUrl = a.get("browser_download_url").getAsString();
                                 break;
                              }
                           }
                        }
                     }
                  } catch (Throwable ignored) {}
               }
               String mcVersion = ((ModContainer)FabricLoader.getInstance().getModContainer("minecraft").get()).getMetadata().getVersion().getFriendlyString();
               String currentVersion = ((ModContainer)FabricLoader.getInstance().getModContainer("bomboaddons").get()).getMetadata().getVersion().getFriendlyString();
               if (currentVersion.equals("${version}")) {
                  if (!silent) {
                     sendMessage("§cRunning in dev environment with unset version. Update skipped.");
                  }

                  return;
               }

               if (latestVersion == null || downloadUrl == null) {
                  if (!silent) {
                     sendMessage("§cNo releases or update jars found!");
                  }

                  return;
               }

               int comparison = compareVersions(latestVersion, currentVersion);
               if (comparison < 0 || comparison == 0 && silent) {
                  if (!silent) {
                     sendMessage("§aMod is up to date! (v" + currentVersion + ")");
                  }

                  return;
               }

               if (silent) {
                  sendMessage("§eUpdate found: §b" + latestVersion + " §7(Current: " + currentVersion + ")");
                  sendMessage("§7Run §b/b update §7to download the new version.");
                  return;
               }

               sendMessage("§eUpdate found: §b" + latestVersion + " §7(Current: " + currentVersion + ")");
               sendMessage("§7Downloading update...");
               Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
               File currentJar = getCurrentJar();
               if (currentJar == null) {
                  sendMessage("§cFailed to identify current mod JAR location!");
                  return;
               }

               File newJarFile = modsFolder.resolve("bomboaddons-" + mcVersion + "-" + latestVersion + ".jar").toFile();
               Bomboaddons.logApiRequest(downloadUrl);
               InputStream in = (new URL(downloadUrl)).openStream();

               try {
                  Files.copy(in, newJarFile.toPath(), new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
               } catch (Throwable var20) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable x2) {
                        var20.addSuppressed(x2);
                     }
                  }

                  throw var20;
               }

               if (in != null) {
                  in.close();
               }

               updatedThisSession = true;
               StringBuilder pending = new StringBuilder();
               if (Files.exists(modsFolder, new LinkOption[0])) {
                  Stream<Path> stream = Files.list(modsFolder);

                  try {
                     stream.filter((p) -> p.getFileName().toString().startsWith("bomboaddons-") && p.getFileName().toString().endsWith(".jar")).filter((p) -> !p.equals(newJarFile.toPath())).forEach((p) -> pending.append(p.toAbsolutePath().toString()).append("\n"));
                  } catch (Throwable var19) {
                     if (stream != null) {
                        try {
                           stream.close();
                        } catch (Throwable x2) {
                           var19.addSuppressed(x2);
                        }
                     }

                     throw var19;
                  }

                  if (stream != null) {
                     stream.close();
                  }
               }

               Files.writeString(PENDING_DELETE, pending.toString(), StandardCharsets.UTF_8);
               sendMessage("§aUpdate downloaded: §b" + newJarFile.getName());
               sendMessage("§eThe old versions will be removed on next restart.");
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
