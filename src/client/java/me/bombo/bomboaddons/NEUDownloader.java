package me.bombo.bomboaddons;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NEUDownloader {
    private static final String API_URL = "https://api.github.com/repos/NotEnoughUpdates/NotEnoughUpdates-REPO/commits/master";
    private static final String ZIP_URL = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
    public static final Path REPO_DIR = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/neu_repo");
    public static final Path ITEMS_DIR = REPO_DIR.resolve("items");
    private static final Path VERSION_FILE = REPO_DIR.resolve("version.txt");
    private static boolean isDownloading = false;

    public static void checkAndDownloadAsync() {
        if (isDownloading) return;
        isDownloading = true;

        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(REPO_DIR);

                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

                // 1. Check latest commit hash
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("User-Agent", "BomboAddons")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String latestSha = json.get("sha").getAsString();
                    
                    String currentSha = "";
                    if (Files.exists(VERSION_FILE)) {
                        currentSha = Files.readString(VERSION_FILE).trim();
                    }

                    if (!latestSha.equals(currentSha) || !Files.exists(ITEMS_DIR)) {
                        System.out.println("[BomboAddons] Downloading latest NEU repository (commit: " + latestSha + ")...");
                        
                        HttpRequest zipReq = HttpRequest.newBuilder()
                                .uri(URI.create(ZIP_URL))
                                .header("User-Agent", "BomboAddons")
                                .timeout(Duration.ofMinutes(5))
                                .GET()
                                .build();

                        HttpResponse<InputStream> zipRes = client.send(zipReq, HttpResponse.BodyHandlers.ofInputStream());
                        if (zipRes.statusCode() == 200) {
                            try (ZipInputStream zis = new ZipInputStream(zipRes.body())) {
                                ZipEntry entry;
                                while ((entry = zis.getNextEntry()) != null) {
                                    // ZIP contains "NotEnoughUpdates-REPO-master/..."
                                    String name = entry.getName();
                                    int firstSlash = name.indexOf('/');
                                    if (firstSlash != -1) {
                                        String relative = name.substring(firstSlash + 1);
                                        // We care about items and constants
                                        if (relative.startsWith("items/") || relative.startsWith("constants/")) {
                                            Path target = REPO_DIR.resolve(relative);
                                            if (entry.isDirectory()) {
                                                Files.createDirectories(target);
                                            } else {
                                                Files.createDirectories(target.getParent());
                                                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                                            }
                                        }
                                    }
                                    zis.closeEntry();
                                }
                            }
                            Files.writeString(VERSION_FILE, latestSha);
                            System.out.println("[BomboAddons] Successfully downloaded and extracted NEU repository.");
                        } else {
                            System.err.println("[BomboAddons] Failed to download NEU zip: HTTP " + zipRes.statusCode());
                        }
                    } else {
                        System.out.println("[BomboAddons] NEU repository is up to date.");
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
