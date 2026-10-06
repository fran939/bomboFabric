package me.bombo.bomboaddons.features.pip;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Picture-in-Picture (PiP) In-Game Media Overlay.
 * Supports web images, GIFs, and YouTube clean-canvas video previews without ads or UI clutter.
 * Fully repositionable via /b gui and customizable via /b pip or /b config.
 */
public class PipManager {

    private static final Identifier PIP_TEXTURE_ID = Identifier.fromNamespaceAndPath("bomboaddons", "pip_media_frame");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static final Pattern YT_REGEX = Pattern.compile(
            "(?:https?:\\/\\/)?(?:www\\.|m\\.)?(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|v\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})"
    );

    private static DynamicTexture activeTexture = null;
    private static boolean textureLoaded = false;
    private static boolean loading = false;
    private static String loadedUrl = "";
    private static String mediaTitle = "Media Stream";
    private static String mediaAuthor = "";
    private static boolean isYouTube = false;
    private static String errorMessage = null;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "pip_hud"), PipManager::renderHud);
    }

    public static void openPip(String url) {
        if (url == null || url.trim().isEmpty()) {
            return;
        }
        String cleanUrl = url.trim();
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.pipUrl = cleanUrl;
            s.pipEnabled = true;
            BomboConfig.save();
        }
        loadMedia(cleanUrl);
    }

    public static void closePip() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.pipEnabled = false;
            BomboConfig.save();
        }
    }

    public static void setOpacity(float opacity) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.pipOpacity = Math.max(0.05f, Math.min(1.0f, opacity));
            BomboConfig.save();
        }
    }

    public static void setScale(float scale) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.pipScale = Math.max(0.2f, Math.min(5.0f, scale));
            BomboConfig.save();
        }
    }

    public static void loadMedia(String url) {
        if (url == null || url.trim().isEmpty()) return;
        loadedUrl = url.trim();
        errorMessage = null;
        loading = true;
        textureLoaded = false;

        Matcher ytMatcher = YT_REGEX.matcher(loadedUrl);
        if (ytMatcher.find()) {
            isYouTube = true;
            String videoId = ytMatcher.group(1);
            mediaTitle = "YouTube (" + videoId + ")";
            mediaAuthor = "";
            fetchYouTubeMetadata(videoId);
            fetchAndApplyImage("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg",
                    "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
        } else {
            isYouTube = false;
            mediaTitle = getFilenameFromUrl(loadedUrl);
            mediaAuthor = "";
            fetchAndApplyImage(loadedUrl, null);
        }
    }

    private static String getFilenameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && path.contains("/")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isEmpty()) return name;
            }
        } catch (Exception ignored) {}
        return "Direct Image";
    }

    private static void fetchYouTubeMetadata(String videoId) {
        CompletableFuture.runAsync(() -> {
            try {
                String oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoId + "&format=json";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(oembedUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null) {
                    JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (json.has("title")) {
                        mediaTitle = json.get("title").getAsString();
                    }
                    if (json.has("author_name")) {
                        mediaAuthor = json.get("author_name").getAsString();
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    private static void fetchAndApplyImage(String primaryUrl, String fallbackUrl) {
        CompletableFuture.runAsync(() -> {
            try {
                byte[] imgBytes = downloadBytes(primaryUrl);
                if ((imgBytes == null || imgBytes.length == 0) && fallbackUrl != null) {
                    imgBytes = downloadBytes(fallbackUrl);
                }
                if (imgBytes == null || imgBytes.length == 0) {
                    loading = false;
                    errorMessage = "Failed to load media";
                    return;
                }

                try (InputStream in = new ByteArrayInputStream(imgBytes)) {
                    NativeImage nativeImg = NativeImage.read(in);
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> {
                        try {
                            if (activeTexture != null) {
                                activeTexture.close();
                            }
                            activeTexture = new DynamicTexture(() -> "pip_media_" + System.currentTimeMillis(), nativeImg);
                            mc.getTextureManager().register(PIP_TEXTURE_ID, activeTexture);
                            textureLoaded = true;
                            loading = false;
                            errorMessage = null;
                        } catch (Exception e) {
                            loading = false;
                            errorMessage = "Texture upload failed";
                        }
                    });
                }
            } catch (Exception e) {
                loading = false;
                errorMessage = "Network error: " + e.getMessage();
            }
        });
    }

    private static byte[] downloadBytes(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void renderHud(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.isHidden()) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.pipEnabled || s.pipUrl == null || s.pipUrl.trim().isEmpty()) return;

        // Auto reload if URL changed
        if (!s.pipUrl.trim().equals(loadedUrl) && !loading) {
            loadMedia(s.pipUrl.trim());
        }

        renderPipBox(g, mc.font, s.pipX, s.pipY, (int) (s.pipW * s.pipScale), (int) (s.pipH * s.pipScale), s.pipOpacity, s.pipShowBorder, s.pipCleanVideo, false);
    }

    public static void renderPipInMoveScreen(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        BomboConfig.Settings s = BomboConfig.get();
        float opacity = s != null ? s.pipOpacity : 0.9f;
        boolean border = s != null ? s.pipShowBorder : true;
        boolean clean = s != null ? s.pipCleanVideo : true;
        renderPipBox(g, mc.font, x, y, w, h, opacity, border, clean, true);
    }

    private static void renderPipBox(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, float opacity, boolean showBorder, boolean cleanVideo, boolean isMoveScreen) {
        float alpha = Math.max(0.05f, Math.min(1.0f, opacity));
        int alphaInt = (int) (alpha * 255.0f);
        int bgCol = (alphaInt << 24) | 0x000F172A;

        // Background card
        g.fill(x, y, x + w, y + h, bgCol);

        if (textureLoaded && activeTexture != null) {
            // Clean 16:9 video canvas - render direct texture
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PIP_TEXTURE_ID, x, y, 0.0f, 0.0f, w, h, w, h, (alphaInt << 24) | 0x00FFFFFF);
        } else if (loading) {
            String loadStr = "§e⏳ Loading PiP media...";
            g.text(font, loadStr, x + (w - font.width(loadStr)) / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF, true);
        } else if (errorMessage != null) {
            String errStr = "§c✖ " + errorMessage;
            g.text(font, errStr, x + (w - font.width(errStr)) / 2, y + (h - font.lineHeight) / 2, 0xFFFF6666, true);
        } else {
            String hintStr = "§7PiP Ready (/b pip <url>)";
            g.text(font, hintStr, x + (w - font.width(hintStr)) / 2, y + (h - font.lineHeight) / 2, 0xFF94A3B8, true);
        }

        // Header / Badge overlay when not in clean video mode or when hovered in Move Screen
        if (!cleanVideo || isMoveScreen) {
            int headerH = 18;
            g.fill(x, y, x + w, y + headerH, (Math.min(220, alphaInt) << 24) | 0x00020617);
            String titleDisp = mediaTitle;
            int maxTitleW = w - 40;
            if (font.width(titleDisp) > maxTitleW) {
                titleDisp = font.plainSubstrByWidth(titleDisp, maxTitleW - 6) + "..";
            }
            String prefix = isYouTube ? "§c▶ §f" : "§b🖼 §f";
            g.text(font, prefix + titleDisp, x + 6, y + 5, 0xFFFFFFFF, false);

            String opBadge = "§8" + (int)(alpha * 100) + "%";
            g.text(font, opBadge, x + w - font.width(opBadge) - 6, y + 5, 0xFF94A3B8, false);
        }

        // Clean border
        if (showBorder || isMoveScreen) {
            int borderCol = (alphaInt << 24) | (isMoveScreen ? 0x0038BDF8 : 0x00FFAA00);
            g.outline(x, y, w, h, borderCol);
        }
    }
}
