package me.bombo.bomboaddons.features.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class BomboApiKeyManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static String getApiKey() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.apiKey != null) {
            return s.apiKey.trim();
        }
        return "";
    }

    public static boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    public static boolean checkApiKeyAndWarn(String actionName) {
        if (!hasApiKey()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MutableComponent msg = Component.literal("§8[§3Bombo§8] §cNo API key found. Generate one with ")
                        .append(Component.literal("§e/b apikey")
                                .setStyle(Style.EMPTY
                                        .withColor(ChatFormatting.YELLOW)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.RunCommand("/b apikey"))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to run §e/b apikey")))))
                        .append(Component.literal("§c."));
                mc.player.sendSystemMessage(msg);
            }
            return false;
        }
        return true;
    }

    public static CompletableFuture<String> fetchOrGenerateKey() {
        Minecraft mc = Minecraft.getInstance();
        String mcName = (mc != null && mc.getUser() != null) ? mc.getUser().getName() : "Player";
        String mcUuid = (mc != null && mc.getUser() != null && mc.getUser().getProfileId() != null)
                ? mc.getUser().getProfileId().toString()
                : "";

        JsonObject reqBody = new JsonObject();
        reqBody.addProperty("mc_name", mcName);
        reqBody.addProperty("mc_uuid", mcUuid);

        String url = BomboApiUrl.MAIN_API_BASE + "/api/keys/player";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "BomboAddons/" + mcUuid)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(reqBody.toString()));

        return HTTP_CLIENT.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.body() != null) {
                        try {
                            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                            if (resp.statusCode() == 200 && json.has("success") && json.get("success").getAsBoolean() && json.has("key")) {
                                String key = json.get("key").getAsString();
                                BomboConfig.Settings s = BomboConfig.get();
                                if (s != null) {
                                    s.apiKey = key;
                                    BomboConfig.save();
                                }
                                return key;
                            }
                            if (json.has("message")) {
                                throw new RuntimeException(json.get("message").getAsString());
                            } else if (json.has("error")) {
                                String err = json.get("error").getAsString();
                                if (json.has("motive")) {
                                    err += ": " + json.get("motive").getAsString();
                                }
                                throw new RuntimeException(err);
                            }
                        } catch (RuntimeException re) {
                            throw re;
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse API key response: " + e.getMessage(), e);
                        }
                    }
                    throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
                });
    }

    public static void setApiKey(String key) {
        if (key == null) key = "";
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.apiKey = key.trim();
            BomboConfig.save();
        }
    }

    public static void setCustomBridgeName(String name) {
        if (name == null) name = "";
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.ircDiscordUser = name.trim();
            BomboConfig.save();
        }
    }

    public static void displayKeyInChat(String key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        MutableComponent keyComp = Component.literal(key)
                .setStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.CopyToClipboard(key))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to copy API Key to clipboard"))));

        MutableComponent syncComp = Component.literal(" §8[§eSync from Discord§8]")
                .setStyle(Style.EMPTY
                        .withColor(ChatFormatting.YELLOW)
                        .withUnderlined(false)
                        .withClickEvent(new ClickEvent.RunCommand("/b key sync"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to sync/refresh API key from Discord for your MC account"))));

        MutableComponent fullMsg = Component.literal("§a[BomboAPI] §fYour API Key: ")
                .append(keyComp)
                .append(syncComp);

        mc.player.sendSystemMessage(fullMsg);
    }
}
