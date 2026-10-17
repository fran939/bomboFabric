package me.bombo.bomboaddons.features.kuudra;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KuudraUserInfoCommand {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public static void showKuudraInfo(String playerInput) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String target = (playerInput != null && !playerInput.trim().isEmpty())
                ? playerInput.trim()
                : mc.player.getName().getString();

        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §7Fetching Kuudra overview for §e" + target + "§7..."));

        new Thread(() -> {
            try {
                String url = BomboApiUrl.getApiUrl("/api/pv/" + target);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "bomboaddons")
                        .GET()
                        .build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200 || resp.body() == null || resp.body().isEmpty()) {
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cCould not fetch profile data for §e" + target + "§c (Status: " + resp.statusCode() + ")."));
                        }
                    });
                    return;
                }

                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                mc.execute(() -> {
                    if (mc.player != null) {
                        Component overview = buildKuudraOverview(root, target);
                        mc.player.sendSystemMessage(overview);
                    }
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cError fetching Kuudra info: §7" + e.getMessage()));
                    }
                });
            }
        }).start();
    }

    private static Component buildKuudraOverview(JsonObject root, String fallbackName) {
        String username = root.has("username") ? root.get("username").getAsString() : fallbackName;
        double sbLevel = root.has("level") ? root.get("level").getAsDouble() : 0.0;
        int mp = root.has("magical_power") ? root.get("magical_power").getAsInt() : 0;
        String power = root.has("power") ? root.get("power").getAsString() : "None";

        JsonObject crimson = root.has("crimson") ? root.getAsJsonObject("crimson") : null;
        int basicRuns = crimson != null && crimson.has("basic_runs") ? crimson.get("basic_runs").getAsInt() : 0;
        int hotRuns = crimson != null && crimson.has("hot_runs") ? crimson.get("hot_runs").getAsInt() : 0;
        int burningRuns = crimson != null && crimson.has("burning_runs") ? crimson.get("burning_runs").getAsInt() : 0;
        int fieryRuns = crimson != null && crimson.has("fiery_runs") ? crimson.get("fiery_runs").getAsInt() : 0;
        int infernalRuns = crimson != null && crimson.has("infernal_runs") ? crimson.get("infernal_runs").getAsInt() : 0;
        int totalRuns = basicRuns + hotRuns + burningRuns + fieryRuns + infernalRuns;

        double cataLevel = 0.0;
        if (root.has("dungeons") && root.get("dungeons").isJsonObject()) {
            JsonObject dung = root.getAsJsonObject("dungeons");
            if (dung.has("cataLevel")) cataLevel = dung.get("cataLevel").getAsDouble();
        }

        // Equipment & weapons scan
        boolean hasHyp = false;
        boolean hasTerm = false;
        boolean hasRag = false;
        int termDuplex = 0;
        int ragChim = 0;

        if (root.has("weapons") && root.get("weapons").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("weapons")) {
                if (!el.isJsonObject()) continue;
                String name = el.getAsJsonObject().has("name") ? el.getAsJsonObject().get("name").getAsString().toLowerCase(Locale.ROOT) : "";
                if (name.contains("hyperion") || name.contains("scylla") || name.contains("astraea") || name.contains("valkyrie")) {
                    hasHyp = true;
                }
                if (name.contains("terminator")) {
                    hasTerm = true;
                }
                if (name.contains("ragnarock")) {
                    hasRag = true;
                }
            }
        }

        MutableComponent main = Component.empty();

        // Header with /pv click
        String startLine = String.format(Locale.ROOT, "§2§m-----§f[- §7[§e%.0f§7] §2%s §f-]§2§m-----\n", sbLevel, username);
        MutableComponent headerComp = Component.literal(startLine)
                .withStyle(s -> s
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to view full profile: §e/pv " + username)))
                        .withClickEvent(new ClickEvent.RunCommand("/pv " + username))
                );
        main.append(headerComp);

        // Runs Breakdown
        List<String> runsList = new ArrayList<>();
        runsList.add("§aBasic: §f" + basicRuns);
        runsList.add("§aHot: §f" + hotRuns);
        runsList.add("§aBurning: §f" + burningRuns);
        runsList.add("§aFiery: §f" + fieryRuns);
        runsList.add("§aInfernal: §f" + infernalRuns);
        runsList.add("\n§a§lTotal: §f" + totalRuns);
        String runsLore = "§a§lRuns Breakdown:\n\n" + String.join("\n", runsList);

        MutableComponent runsComp = Component.literal(String.format(Locale.ROOT, "§aRuns: §c%d §7(§f%d§7)\n", infernalRuns, totalRuns))
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(runsLore))));
        main.append(runsComp);

        // Magical Power
        String mpLore = "§a§lMP Breakdown:\n\n§7* §aPower: §f§l" + power + "\n§7* §aTotal MP: §b" + mp;
        MutableComponent mpComp = Component.literal("§aMagical Power: §6" + mp + " §7(§e" + power + "§7)\n")
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(mpLore))));
        main.append(mpComp);

        // Catacombs
        main.append(Component.literal(String.format(Locale.ROOT, "§aCata: §c%.2f\n", cataLevel)));

        // Weapons
        main.append(Component.literal("§aHyperion: " + (hasHyp ? "§a✔" : "§4X") + "\n"));
        main.append(Component.literal("§aTerminator: " + (hasTerm ? "§a✔" : "§4X") + "\n"));
        main.append(Component.literal("§aRagnarock Axe: " + (hasRag ? "§a✔" : "§4X") + "\n"));

        // Footer
        main.append(Component.literal("§2§m-----------------------------"));

        return main;
    }
}
