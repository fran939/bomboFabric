package me.bombo.bomboaddons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.fabricmc.loader.api.FabricLoader;

public class NEUDownloader {
   private static final String API_URL = "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master";
   private static final String ZIP_URL = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
   public static final Path REPO_DIR = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/neu_repo");
   public static final Path ITEMS_DIR;
   private static final Path VERSION_FILE;
   private static boolean isDownloading;

   public static void checkAndDownloadAsync() {
      if (!isDownloading) {
         isDownloading = true;
         CompletableFuture.runAsync(() -> {
            try {
               Files.createDirectories(REPO_DIR);
               HttpClient client = HttpClient.newBuilder().followRedirects(Redirect.NORMAL).build();
               HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master")).header("User-Agent", "BomboAddons").timeout(Duration.ofSeconds(10L)).GET().build();
               HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
               if (response.statusCode() == 200) {
                  JsonObject json = JsonParser.parseString((String)response.body()).getAsJsonObject();
                  String latestSha = json.get("sha").getAsString();
                  String currentSha = "";
                  if (Files.exists(VERSION_FILE, new LinkOption[0])) {
                     currentSha = Files.readString(VERSION_FILE).trim();
                  }

                  if (latestSha.equals(currentSha) && Files.exists(ITEMS_DIR, new LinkOption[0])) {
                     System.out.println("[BomboAddons] NEU repository is up to date.");
                  } else {
                     System.out.println("[BomboAddons] Downloading latest NEU repository (commit: " + latestSha + ")...");
                     HttpRequest zipReq = HttpRequest.newBuilder().uri(URI.create("https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip")).header("User-Agent", "BomboAddons").timeout(Duration.ofMinutes(5L)).GET().build();
                     HttpResponse<InputStream> zipRes = client.send(zipReq, BodyHandlers.ofInputStream());
                     if (zipRes.statusCode() == 200) {
                        ZipInputStream zis = new ZipInputStream((InputStream)zipRes.body());

                        ZipEntry entry;
                        try {
                           for(; (entry = zis.getNextEntry()) != null; zis.closeEntry()) {
                              String name = entry.getName();
                              int firstSlash = name.indexOf(47);
                              if (firstSlash != -1) {
                                 String relative = name.substring(firstSlash + 1);
                                 if (relative.startsWith("items/") || relative.startsWith("constants/")) {
                                    Path target = REPO_DIR.resolve(relative);
                                    if (entry.isDirectory()) {
                                       Files.createDirectories(target);
                                    } else {
                                       Files.createDirectories(target.getParent());
                                       Files.copy(zis, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING});
                                    }
                                 }
                              }
                           }
                        } catch (Throwable var20) {
                           try {
                              zis.close();
                           } catch (Throwable x2) {
                              var20.addSuppressed(x2);
                           }

                           throw var20;
                        }

                        zis.close();
                        Files.writeString(VERSION_FILE, latestSha);
                        System.out.println("[BomboAddons] Successfully downloaded and extracted NEU repository.");
                     } else {
                        System.err.println("[BomboAddons] Failed to download NEU zip: HTTP " + zipRes.statusCode());
                     }
                  }
               } else {
                  System.err.println("[BomboAddons] Failed to check NEU commit API: HTTP " + response.statusCode());
               }
            } catch (Exception e) {
               System.err.println("[BomboAddons] Error during NEU sync:");
               e.printStackTrace();
            } finally {
               isDownloading = false;
            }

         });
      }
   }

   static {
      ITEMS_DIR = REPO_DIR.resolve("items");
      VERSION_FILE = REPO_DIR.resolve("version.txt");
      isDownloading = false;
   }
}
