package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ModUpdater {
    private static final String REPO = "fran939/bomboFabric";
    private static final String GITHUB_API = "https://api.github.com/repos/" + REPO + "/releases/latest";
    public static boolean updatedThisSession = false;
    private static final Path PENDING_DELETE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_pending_delete.txt");

    public static void init() {
        if (Files.exists(PENDING_DELETE)) {
            try {
                List<String> lines = Files.readAllLines(PENDING_DELETE, StandardCharsets.UTF_8);
                File currentJar = getCurrentJar();
                String currentPath = currentJar != null ? currentJar.getAbsolutePath() : "";

                for (String line : lines) {
                    String oldJarPath = line.trim();
                    if (oldJarPath.isEmpty()) continue;
                    File oldJar = new File(oldJarPath);
                    if (oldJar.exists() && !oldJarPath.equals(currentPath)) {
                        if (oldJar.delete()) {
                            Bomboaddons.LOGGER.info("[BomboAddons] Deleted old version: " + oldJarPath);
                        } else {
                            Bomboaddons.LOGGER.warn("[BomboAddons] FAILED to delete old version: " + oldJarPath);
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
        if (silent && updatedThisSession) return;
        
        new Thread(() -> {
            try {
                if (!silent) sendMessage("§7Checking for updates...");

                HttpURLConnection conn = (HttpURLConnection) new URL(GITHUB_API).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (conn.getResponseCode() != 200) {
                    if (!silent) sendMessage("§cFailed to check for updates (HTTP " + conn.getResponseCode() + ")");
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String latestVersion = json.get("tag_name").getAsString().replace("v", "");
                String currentVersion = FabricLoader.getInstance().getModContainer("bomboaddons")
                        .get().getMetadata().getVersion().getFriendlyString();

                if (currentVersion.equals("${version}")) {
                    if (!silent) sendMessage("§cRunning in dev environment with unset version. Update skipped.");
                    return;
                }

                int comparison = compareVersions(latestVersion, currentVersion);

                if (comparison < 0 || (comparison == 0 && silent)) {
                    if (!silent) sendMessage("§aMod is up to date! (v" + currentVersion + ")");
                    return;
                }

                if (silent) {
                    sendMessage("§eUpdate found: §b" + latestVersion + " §7(Current: " + currentVersion + ")");
                    sendMessage("§7Run §b/b update §7to download the new version.");
                    return;
                }

                sendMessage("§eUpdate found: §b" + latestVersion + " §7(Current: " + currentVersion + ")");
                
                String downloadUrl = null;
                JsonArray assets = json.getAsJsonArray("assets");
                for (JsonElement asset : assets) {
                    JsonObject assetObj = asset.getAsJsonObject();
                    if (assetObj.get("name").getAsString().endsWith(".jar")) {
                        downloadUrl = assetObj.get("browser_download_url").getAsString();
                        break;
                    }
                }

                if (downloadUrl == null) {
                    sendMessage("§cNo .jar found in the latest release!");
                    return;
                }

                sendMessage("§7Downloading update...");
                
                Path modsFolder = FabricLoader.getInstance().getGameDir().resolve("mods");
                File currentJar = getCurrentJar();
                if (currentJar == null) {
                    sendMessage("§cFailed to identify current mod JAR location!");
                    return;
                }
                
                File newJarFile = modsFolder.resolve("bomboaddons-" + latestVersion + ".jar").toFile();

                try (InputStream in = new URL(downloadUrl).openStream()) {
                    Files.copy(in, newJarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                
                updatedThisSession = true;

                // Record ALL existing bomboaddons JARs to be deleted (except the new one)
                StringBuilder pending = new StringBuilder();
                if (Files.exists(modsFolder)) {
                    try (var stream = Files.list(modsFolder)) {
                        stream.filter(p -> p.getFileName().toString().startsWith("bomboaddons-") && p.getFileName().toString().endsWith(".jar"))
                              .filter(p -> !p.equals(newJarFile.toPath()))
                              .forEach(p -> pending.append(p.toAbsolutePath().toString()).append("\n"));
                    }
                }
                
                Files.writeString(PENDING_DELETE, pending.toString(), StandardCharsets.UTF_8);
                
                sendMessage("§aUpdate downloaded: §b" + newJarFile.getName());
                sendMessage("§eThe old versions will be removed on next restart.");
                
            } catch (Exception e) {
                if (!silent) sendMessage("§cError while updating: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = 0, p2 = 0;
            try {
                if (i < parts1.length) p1 = Integer.parseInt(parts1[i].replaceAll("[^0-9]", ""));
                if (i < parts2.length) p2 = Integer.parseInt(parts2[i].replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {}
            if (p1 < p2) return -1;
            if (p1 > p2) return 1;
        }
        return 0;
    }

    private static File getCurrentJar() {
        try {
            return new File(ModUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static void sendMessage(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.execute(() -> mc.player.displayClientMessage(Component.literal("§6[Updater] " + msg), false));
        }
    }
}
