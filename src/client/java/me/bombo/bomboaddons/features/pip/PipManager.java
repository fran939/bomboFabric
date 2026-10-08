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
import net.minecraft.network.chat.Component;
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

    // Server-transcoded video playback (YouTube etc.) via bomboapi /api/media/*.
    private static boolean isVideo = false;
    private static String videoSessionId = "";
    private static boolean videoPaused = false;
    private static long lastVideoPoll = 0L;
    private static boolean videoFrameInFlight = false;
    private static long videoPositionSeconds = 0L;
    private static long lastVideoStatusPoll = 0L;
    private static final String MEDIA_API = "https://api.bombo.dpdns.org/api/media";
    /** /open attempts before giving up and showing the thumbnail. */
    private static final int OPEN_ATTEMPTS = 3;
    /** Automatic session restarts allowed per URL, so a dead link cannot loop. */
    private static final int MAX_VIDEO_RETRIES = 2;
    /** Frames decoded by the current session (0 = nothing received yet). */
    private static int videoFrameCount = 0;
    /** When the current session was (re)started, for the no-frame watchdog. */
    private static long videoSessionStartedAt = 0L;
    /** Automatic restarts already spent on the current URL. */
    private static int videoSessionRetries = 0;
    /** Last session problem already reported, so chat is not spammed. */
    private static String reportedVideoError = null;
    /** Video id the current session was opened for, needed to reload after a failure. */
    private static String currentVideoId = "";

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
        closeVideoSession();
    }

    // ---------------------------------------------------------------------------------------------
    // Server-transcoded video sessions
    // ---------------------------------------------------------------------------------------------

    /** True when the current PiP source is a server-transcoded video (YouTube et al). */
    public static boolean isVideo() {
        return isVideo;
    }

    public static boolean isVideoPaused() {
        return videoPaused;
    }

    /** Opens a transcode session for a new URL, resetting the restart budget. */
    private static void startVideoSession(String url, String videoId) {
        currentVideoId = videoId == null ? "" : videoId;
        videoSessionRetries = 0;
        openVideoSession(url, currentVideoId);
    }

    /** Re-opens the current URL after a failure, spending one restart. */
    private static void retryVideoSession() {
        String url = loadedUrl;
        if (url == null || url.isEmpty() || currentVideoId.isEmpty()) return;
        videoSessionRetries++;
        openVideoSession(url, currentVideoId);
    }

    /**
     * Asks bomboapi to open a transcode session and keeps the id. The call goes out as a plain GET
     * (the server mirrors every parameter in the query string) because that is the request shape
     * already proven to survive in-game; if it cannot start, the failure is reported in chat instead
     * of silently swapping in the thumbnail.
     */
    private static void openVideoSession(String url, String videoId) {
        closeVideoSession();
        isVideo = true;
        loading = true;
        textureLoaded = false;
        mediaWidth = 0;
        mediaHeight = 0;
        errorMessage = null;
        videoFrameCount = 0;
        videoSessionStartedAt = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            String failure = null;
            for (int attempt = 1; attempt <= OPEN_ATTEMPTS; attempt++) {
                MediaResponse resp = mediaGet("/open", "url=" + urlEncode(url));
                if (resp.status() >= 200 && resp.status() < 300) {
                    try {
                        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                        if (json.has("id")) {
                            videoSessionId = json.get("id").getAsString();
                            return;
                        }
                        failure = "no session id (" + brief(resp.body()) + ")";
                    } catch (Throwable t) {
                        failure = "bad reply (" + brief(resp.body()) + ")";
                    }
                } else {
                    failure = "HTTP " + resp.status() + " " + brief(resp.body());
                }
                if (attempt < OPEN_ATTEMPTS) sleep(1200L);
            }

            // The server refused the session: say why, then degrade to the thumbnail.
            notifyChat("§8[§bBombo§8] §cPiP video could not start: " + failure + "§7 - showing the thumbnail.");
            isVideo = false;
            videoSessionId = "";
            fetchAndApplyImage(
                    "https://i.ytimg.com/vi/" + videoId + "/maxresdefault.jpg",
                    "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
        });
    }

    /** Response of a media API call (HTTP status plus the raw body, or -1 on a transport error). */
    private record MediaResponse(int status, String body) {
    }

    private static MediaResponse mediaGet(String path, String query) {
        try {
            String url = MEDIA_API + path + (query == null || query.isEmpty() ? "" : "?" + query);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "BomboAddons/1.0")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return new MediaResponse(resp.statusCode(), resp.body() == null ? "" : resp.body());
        } catch (Throwable t) {
            String msg = t.getMessage();
            return new MediaResponse(-1, t.getClass().getSimpleName() + (msg == null ? "" : ": " + msg));
        }
    }

    /** Fire-and-forget media command (pause / seek / close); failures are not worth interrupting play. */
    private static void mediaCommand(String path, String query) {
        CompletableFuture.runAsync(() -> mediaGet(path, query));
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String brief(String body) {
        if (body == null) return "empty";
        String clean = body.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120) + "...";
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Sends a chat line from any thread. */
    private static void notifyChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(message));
                }
            } catch (Throwable ignored) {
            }
        });
    }

    /** Reports a session problem once and spends a restart before giving up for good. */
    private static void handleVideoFailure(String reason) {
        if (reason == null || reason.isEmpty()) return;
        String key = videoSessionId + "|" + reason;
        if (key.equals(reportedVideoError)) return;
        reportedVideoError = key;

        if (reason.equals("Stream ended")) {
            notifyChat("§8[§bBombo§8] §7PiP: the video reached the end.");
            return;
        }
        if (videoSessionRetries < MAX_VIDEO_RETRIES) {
            notifyChat("§8[§bBombo§8] §ePiP: " + reason + "§7 - restarting the stream...");
            retryVideoSession();
            return;
        }
        notifyChat("§8[§bBombo§8] §cPiP: " + reason);
    }

    /** Pauses or resumes server-side playback (freezes the frame while paused). */
    public static void setVideoPaused(boolean paused) {
        if (videoSessionId.isEmpty()) return;
        videoPaused = paused;
        mediaCommand("/pause", "id=" + urlEncode(videoSessionId) + "&paused=" + paused);
    }

    public static void toggleVideoPaused() {
        setVideoPaused(!videoPaused);
    }

    /** Seeks the server-side video to an absolute position. */
    public static void seekVideo(long seconds) {
        if (videoSessionId.isEmpty()) return;
        long target = Math.max(0L, seconds);
        // The previous frame stays on screen until the seeked one arrives, so the box never flashes
        // empty while the server re-opens ffmpeg at the new position.
        videoPositionSeconds = target;
        mediaCommand("/seek", "id=" + urlEncode(videoSessionId) + "&seconds=" + target);
    }

    public static void seekVideoRelative(long deltaSeconds) {
        if (videoSessionId.isEmpty()) return;
        pollVideoStatus(seconds -> seekVideo(Math.max(0L, seconds + deltaSeconds)));
    }

    /** Refreshes the cached playback position at most once per second. */
    private static void pollVideoPosition() {
        if (!isVideo || videoSessionId.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastVideoStatusPoll < 1000L) return;
        lastVideoStatusPoll = now;
        pollVideoStatus(seconds -> videoPositionSeconds = seconds);
    }

    /** Human readable playback position, e.g. {@code 1:23}. */
    private static String formatPosition(long seconds) {
        long s = Math.max(0L, seconds);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private static void pollVideoStatus(java.util.function.LongConsumer onPosition) {
        final String id = videoSessionId;
        if (id.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                MediaResponse resp = mediaGet("/status", "id=" + urlEncode(id));
                if (resp.status() != 200) return;
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.has("positionSeconds")) {
                    onPosition.accept((long) json.get("positionSeconds").getAsDouble());
                }
                if (json.has("frames")) {
                    videoFrameCount = json.get("frames").getAsInt();
                }
                if (json.has("error") && !json.get("error").isJsonNull()) {
                    String err = json.get("error").getAsString();
                    if (err != null && !err.isEmpty()) handleVideoFailure(err);
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public static void closeVideoSession() {
        if (!videoSessionId.isEmpty()) {
            mediaCommand("/close", "id=" + urlEncode(videoSessionId));
        }
        videoSessionId = "";
        isVideo = false;
        videoPaused = false;
        videoFrameInFlight = false;
        videoPositionSeconds = 0L;
        videoFrameCount = 0;
    }

    private static void pollVideoFrame() {
        if (videoFrameInFlight) return;
        final String id = videoSessionId;
        if (id.isEmpty()) return;
        videoFrameInFlight = true;
        CompletableFuture.runAsync(() -> {
            try {
                DownloadResult result = download(MEDIA_API + "/frame?id=" + id + "&t=" + System.currentTimeMillis());
                if (result != null && isUsableImage(result)) {
                    videoFrameCount++;
                    applyImageData(result.data);
                }
            } catch (Throwable ignored) {
            } finally {
                videoFrameInFlight = false;
            }
        });
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
        closeVideoSession();

        String screenshareUser = extractScreenshareUser(loadedUrl);
        if (screenshareUser != null) {
            isYouTube = false;
            isVideo = false;
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
            startVideoSession(loadedUrl, videoId);
        } else {
            isYouTube = false;
            isVideo = false;
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

        // Server-transcoded video: poll frames while playing.
        if (isVideo && !videoSessionId.isEmpty() && !videoPaused && !videoFrameInFlight) {
            long now = System.currentTimeMillis();
            if (now - lastVideoPoll >= 80L) {
                lastVideoPoll = now;
                pollVideoFrame();
            }
        }
        // A session that never produced a frame (dead link, yt-dlp hiccup, resolver timeout) is
        // restarted instead of sitting on "Loading media..." forever.
        if (isVideo && !videoSessionId.isEmpty() && videoFrameCount == 0
                && videoSessionRetries < MAX_VIDEO_RETRIES
                && System.currentTimeMillis() - videoSessionStartedAt > 15000L) {
            retryVideoSession();
        }
        pollVideoPosition();

        renderPipBox(g, mc.font, s.pipX, s.pipY, (int) (s.pipW * s.pipScale), (int) (s.pipH * s.pipScale),
                s.pipOpacity, s.pipShowBorder, false);
    }

    public static void renderPipInMoveScreen(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        BomboConfig.Settings s = BomboConfig.get();
        float opacity = s != null ? s.pipOpacity : 0.9f;
        boolean border = s != null ? s.pipShowBorder : true;
        renderPipBox(g, mc.font, x, y, w, h, opacity, border, true);
        if (isVideo) {
            pollVideoPosition();
            drawVideoControls(g, mc.font, x, y, w, h);
        }
    }

    /** Media control bar drawn over the video in the HUD editor. */
    private static void drawVideoControls(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
        int barH = 18;
        int barY = y + h - barH - 4;
        if (barY < y) return;
        g.fill(x + 2, barY - 2, x + w - 2, barY + barH + 2, 0xCC0B0F19);

        int bw = Math.min(52, Math.max(34, (w - 120) / 3));
        int bx = x + 6;
        drawPill(g, font, "§f⏪ 5s", bx, barY, bw, barH);
        bx += bw + 4;
        drawPill(g, font, (videoPaused ? "§a▶ Play" : "§e⏸ Pause"), bx, barY, bw, barH);
        bx += bw + 4;
        drawPill(g, font, "§f5s ⏩", bx, barY, bw, barH);

        int closeW = 22;
        int closeX = x + w - closeW - 6;
        drawPill(g, font, "§c✕", closeX, barY, closeW, barH);

        // Playback clock sits between the transport buttons and the close pill.
        String pos = "§7⏱ §f" + formatPosition(videoPositionSeconds) + (videoPaused ? " §8(§epaused§8)" : "");
        int posW = font.width(pos);
        int posX = closeX - posW - 8;
        if (posX > bx + bw + 4) {
            g.text(font, pos, posX, barY + (barH - 8) / 2, 0xFFFFFFFF, false);
        }
    }

    private static void drawPill(GuiGraphicsExtractor g, Font font, String label, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xEE1E293B);
        g.outline(x, y, w, h, 0x6638BDF8);
        g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, -1);
    }

    /** Returns true when a click landed on one of the video control buttons. */
    public static boolean handleMoveScreenControlsClick(double mx, double my, int x, int y, int w, int h, int button) {
        if (!isVideo) return false;
        int barH = 18;
        int barY = y + h - barH - 4;
        if (barY < y) return false;
        int bw = Math.min(52, Math.max(34, (w - 120) / 3));
        int bx = x + 6;
        if (mx >= bx && mx <= bx + bw && my >= barY && my <= barY + barH) {
            seekVideoRelative(-5);
            return true;
        }
        bx += bw + 4;
        if (mx >= bx && mx <= bx + bw && my >= barY && my <= barY + barH) {
            toggleVideoPaused();
            return true;
        }
        bx += bw + 4;
        if (mx >= bx && mx <= bx + bw && my >= barY && my <= barY + barH) {
            seekVideoRelative(5);
            return true;
        }
        int closeW = 22;
        int cx = x + w - closeW - 6;
        if (mx >= cx && mx <= cx + closeW && my >= barY && my <= barY + barH) {
            closePip();
            return true;
        }
        return false;
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
