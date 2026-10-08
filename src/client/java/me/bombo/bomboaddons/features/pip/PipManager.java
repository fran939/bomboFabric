package me.bombo.bomboaddons.features.pip;

import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Picture-in-Picture (PiP) In-Game Media Overlay.
 *
 * <p>Supports direct image links and YouTube thumbnails. Images are always drawn at their true
 * aspect ratio and never upscaled past their native resolution. The media filename / opacity bar
 * is only shown inside the HUD editor ({@code /b gui}).
 */
public class PipManager {

    private static final Identifier PIP_TEXTURE_ID = Identifier.fromNamespaceAndPath("bomboaddons", "pip_media_frame");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    private static DynamicTexture activeTexture = null;
    private static boolean textureLoaded = false;
    private static boolean loading = false;
    private static String loadedUrl = "";
    private static String mediaTitle = "Media Stream";
    private static String mediaAuthor = "";
    private static boolean isYouTube = false;
    private static String errorMessage = null;
    private static int mediaWidth = 0;
    private static int mediaHeight = 0;

    // Bombo live screenshare (/b stream) support: the public frame endpoint is polled so a
    // screenshare link can be shown in PiP.
    private static boolean isStream = false;
    private static String streamUser = "";
    private static long lastStreamPoll = 0L;
    private static boolean streamInFlight = false;

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
        mediaWidth = 0;
        mediaHeight = 0;

        String screenshareUser = extractScreenshareUser(loadedUrl);
        if (screenshareUser != null) {
            isYouTube = false;
            isStream = true;
            streamUser = screenshareUser;
            mediaTitle = "Live Stream (" + screenshareUser + ")";
            mediaAuthor = "";
            lastStreamPoll = 0L;
            streamInFlight = false;
            fetchStreamFrame(screenshareUser);
            return;
        }
        isStream = false;

        String videoId = extractYouTubeId(loadedUrl);
        if (videoId != null) {
            isYouTube = true;
            mediaTitle = "YouTube (" + videoId + ")";
            mediaAuthor = "";
            fetchYouTubeMetadata(videoId);
            // maxresdefault is 404 for many videos; hqdefault is a reliable fallback.
            fetchAndApplyImage(
                    "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
        } else {
            isYouTube = false;
            mediaTitle = getFilenameFromUrl(loadedUrl);
            mediaAuthor = "";
            fetchAndApplyImage(loadedUrl, null);
        }
    }

    /** Extracts an 11-char YouTube video id from every common URL shape, or null. */
    public static String extractYouTubeId(String url) {
        if (url == null) return null;
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) return null;

        Matcher m = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})").matcher(u);
        if (m.find()) return m.group(1);
        m = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})").matcher(u);
        if (m.find()) return m.group(1);
        m = Pattern.compile("(?:/live/|/embed/|/shorts/|/v/)([A-Za-z0-9_-]{11})").matcher(u);
        if (m.find()) return m.group(1);
        return null;
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

    /** Result of a media download, including the content type so we can reject web pages. */
    private static class DownloadResult {
        final String contentType;
        final byte[] data;

        DownloadResult(String contentType, byte[] data) {
            this.contentType = contentType;
            this.data = data;
        }
    }

    private static void fetchAndApplyImage(String primaryUrl, String fallbackUrl) {
        CompletableFuture.runAsync(() -> {
            try {
                DownloadResult result = download(primaryUrl);
                if (!isUsableImage(result) && fallbackUrl != null) {
                    result = download(fallbackUrl);
                }
                if (!isUsableImage(result)) {
                    loading = false;
                    if (result == null || result.data == null || result.data.length == 0) {
                        errorMessage = "Failed to load media";
                    } else if (result.contentType != null && result.contentType.toLowerCase(Locale.ROOT).contains("text/html")) {
                        errorMessage = "That link is a web page, not an image";
                    } else {
                        errorMessage = "Not a supported image";
                    }
                    return;
                }

                NativeImage nativeImg = decodeImage(result.data);
                if (nativeImg == null) {
                    loading = false;
                    errorMessage = "Not a supported image";
                    return;
                }

                Minecraft mc = Minecraft.getInstance();
                int w = nativeImg.getWidth();
                int h = nativeImg.getHeight();
                mc.execute(() -> {
                    try {
                        if (activeTexture != null) {
                            activeTexture.close();
                        }
                        activeTexture = new DynamicTexture(() -> "pip_media_" + System.currentTimeMillis(), nativeImg);
                        mc.getTextureManager().register(PIP_TEXTURE_ID, activeTexture);
                        mediaWidth = w;
                        mediaHeight = h;
                        textureLoaded = true;
                        loading = false;
                        errorMessage = null;
                    } catch (Exception e) {
                        loading = false;
                        errorMessage = "Texture upload failed";
                    }
                });
            } catch (Exception e) {
                loading = false;
                errorMessage = "Network error: " + e.getMessage();
            }
        });
    }

    private static boolean isUsableImage(DownloadResult result) {
        if (result == null || result.data == null || result.data.length < 8) return false;
        String ct = result.contentType != null ? result.contentType.toLowerCase(Locale.ROOT) : "";
        if (ct.contains("text/html") || ct.contains("application/json") || ct.contains("text/plain")) return false;

        byte[] d = result.data;
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return true;
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) return true;
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F') return true;
        if (d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d.length > 11
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return true;
        if (d[0] == 'B' && d[1] == 'M') return true;
        // A leading '<' means the server returned an HTML page (redirects, consent walls, 404s).
        if (d[0] == '<') return false;
        return true;
    }

    private static NativeImage decodeImage(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return NativeImage.read(in);
        } catch (Throwable ignored) {
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage bimg = ImageIO.read(in);
            if (bimg != null) {
                ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                ImageIO.write(bimg, "png", pngOut);
                try (InputStream in2 = new ByteArrayInputStream(pngOut.toByteArray())) {
                    return NativeImage.read(in2);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** Extracts the player name from any Bombo screenshare URL, or null when it is not one. */
    public static String extractScreenshareUser(String url) {
        if (url == null) return null;
        String u = url.trim();
        String lower = u.toLowerCase(Locale.ROOT);
        if (!lower.contains("screenshare")) return null;

        Matcher m = Pattern.compile("[?&]user=([^&\\s/#]+)").matcher(u);
        if (m.find()) {
            String user = decodeUrlComponent(m.group(1));
            if (!user.isEmpty()) return user;
        }
        m = Pattern.compile("screenshare/(?:stream/|frame/|ws/)?([^?&#/\\s]+)").matcher(u);
        if (m.find()) {
            String user = decodeUrlComponent(m.group(1));
            if (!user.isEmpty() && !user.equalsIgnoreCase("list") && !user.equalsIgnoreCase("ws")
                    && !user.equalsIgnoreCase("frame") && !user.equalsIgnoreCase("stream")) {
                return user;
            }
        }
        return null;
    }

    private static String decodeUrlComponent(String value) {
        if (value == null) return "";
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return value;
        }
    }

    /** Uploads raw image bytes as the PiP texture on the render thread. */
    private static void applyImageData(byte[] data) {
        NativeImage nativeImg = decodeImage(data);
        if (nativeImg == null) {
            loading = false;
            errorMessage = "Not a supported image";
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = nativeImg.getWidth();
        int h = nativeImg.getHeight();
        mc.execute(() -> {
            try {
                if (activeTexture != null) {
                    activeTexture.close();
                }
                activeTexture = new DynamicTexture(() -> "pip_media_" + System.currentTimeMillis(), nativeImg);
                mc.getTextureManager().register(PIP_TEXTURE_ID, activeTexture);
                mediaWidth = w;
                mediaHeight = h;
                textureLoaded = true;
                loading = false;
                errorMessage = null;
            } catch (Exception e) {
                loading = false;
                errorMessage = "Texture upload failed";
            }
        });
    }

    /** Polls the public screenshare frame endpoint once and updates the PiP texture. */
    public static void fetchStreamFrame(String user) {
        if (user == null || user.isEmpty() || streamInFlight) return;
        streamInFlight = true;
        CompletableFuture.runAsync(() -> {
            try {
                String encoded = java.net.URLEncoder.encode(user, java.nio.charset.StandardCharsets.UTF_8);
                String frameUrl = "https://api.bombo.dpdns.org/api/screenshare/frame?user=" + encoded
                        + "&t=" + System.currentTimeMillis();
                DownloadResult result = download(frameUrl);
                if (result != null && isUsableImage(result)) {
                    applyImageData(result.data);
                } else if (!textureLoaded) {
                    loading = false;
                    errorMessage = result == null ? "Stream offline" : "Stream unavailable";
                }
            } catch (Throwable t) {
                if (!textureLoaded) {
                    loading = false;
                    errorMessage = "Stream error";
                }
            } finally {
                streamInFlight = false;
            }
        });
    }

    private static DownloadResult download(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String ct = resp.headers().firstValue("Content-Type").orElse("");
                return new DownloadResult(ct, resp.body());
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

        // Auto reload if the configured URL changed
        if (!s.pipUrl.trim().equals(loadedUrl) && !loading) {
            loadMedia(s.pipUrl.trim());
        }

        // Live screenshare: keep polling fresh frames while the overlay is visible.
        if (isStream && !streamInFlight) {
            long now = System.currentTimeMillis();
            if (now - lastStreamPoll >= 120L) {
                lastStreamPoll = now;
                fetchStreamFrame(streamUser);
            }
        }

        renderPipBox(g, mc.font, s.pipX, s.pipY, (int) (s.pipW * s.pipScale), (int) (s.pipH * s.pipScale),
                s.pipOpacity, s.pipShowBorder, false);
    }

    public static void renderPipInMoveScreen(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        BomboConfig.Settings s = BomboConfig.get();
        float opacity = s != null ? s.pipOpacity : 0.9f;
        boolean border = s != null ? s.pipShowBorder : true;
        renderPipBox(g, mc.font, x, y, w, h, opacity, border, true);
    }

    private static void renderPipBox(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
            float opacity, boolean showBorder, boolean isMoveScreen) {
        float alpha = Math.max(0.05f, Math.min(1.0f, opacity));
        int alphaInt = (int) (alpha * 255.0f);
        int bgCol = (alphaInt << 24) | 0x000F172A;

        // Background card
        g.fill(x, y, x + w, y + h, bgCol);

        if (textureLoaded && activeTexture != null && mediaWidth > 0 && mediaHeight > 0) {
            // Aspect-preserving fit inside the frame, never upscaled past native resolution.
            double fit = Math.min(w / (double) mediaWidth, h / (double) mediaHeight);
            double factor = Math.min(1.0, fit);
            int drawW = Math.max(1, (int) Math.round(mediaWidth * factor));
            int drawH = Math.max(1, (int) Math.round(mediaHeight * factor));
            int dx = x + (w - drawW) / 2;
            int dy = y + (h - drawH) / 2;
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, PIP_TEXTURE_ID,
                    dx, dy, 0.0f, 0.0f, drawW, drawH, drawW, drawH, (alphaInt << 24) | 0x00FFFFFF);
        } else if (loading) {
            String loadStr = "§e⏳ Loading media...";
            g.text(font, loadStr, x + (w - font.width(loadStr)) / 2, y + (h - font.lineHeight) / 2, 0xFFFFFFFF, true);
        } else if (errorMessage != null) {
            String errStr = "§c✖ " + errorMessage;
            g.text(font, errStr, x + (w - font.width(errStr)) / 2, y + (h - font.lineHeight) / 2, 0xFFFF6666, true);
        } else {
            String hintStr = "§7PiP Ready (/b pip <url>)";
            g.text(font, hintStr, x + (w - font.width(hintStr)) / 2, y + (h - font.lineHeight) / 2, 0xFF94A3B8, true);
        }

        // The filename / opacity bar is only visible inside the HUD editor so it never covers the
        // media during normal play.
        if (isMoveScreen) {
            int headerH = 18;
            g.fill(x, y, x + w, y + headerH, (Math.min(220, alphaInt) << 24) | 0x00020617);
            String titleDisp = mediaTitle;
            int maxTitleW = w - 40;
            if (font.width(titleDisp) > maxTitleW) {
                titleDisp = font.plainSubstrByWidth(titleDisp, maxTitleW - 6) + "..";
            }
            String prefix = isYouTube ? "§c▶ §f" : "§b🖼 §f";
            g.text(font, prefix + titleDisp, x + 6, y + 5, 0xFFFFFFFF, false);

            String opBadge = "§8" + (int) (alpha * 100) + "%";
            g.text(font, opBadge, x + w - font.width(opBadge) - 6, y + 5, 0xFF94A3B8, false);
        }

        // Subtle border
        if (showBorder || isMoveScreen) {
            int borderCol = (alphaInt << 24) | (isMoveScreen ? 0x0038BDF8 : 0x00FFAA00);
            g.outline(x, y, w, h, borderCol);
        }
    }
}
