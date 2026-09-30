package me.bombo.bomboaddons.features.screenshare;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Captures live in-game video frames at 25-30 FPS and streams them to the Bombo screenshare server
 * (https://bombo.dpdns.org/screenshare) with ultra-low latency (<50ms via native MJPEG).
 * Includes robust screen-bounds clamping and detailed metrics dumper (/ss debug).
 */
public class ScreenshareManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final AtomicBoolean STREAMING = new AtomicBoolean(false);
    private static Thread streamThread = null;
    private static volatile String activeStreamTarget = "";
    private static Robot robotInstance = null;

    // Stream metrics & telemetry
    public static final int TARGET_WIDTH = 640;
    public static final int TARGET_HEIGHT = 360;
    private static volatile long framesCaptured = 0L;
    private static volatile long framesSent = 0L;
    private static volatile long totalBytesSent = 0L;
    private static volatile float currentFps = 0.0f;
    private static volatile float currentBitrateKbps = 0.0f;
    private static volatile long lastLatencyMs = 0L;
    private static volatile long lastCaptureDurationMs = 0L;
    private static volatile String lastError = "";

    // Window FPS calculation
    private static long lastMetricCalcTime = 0L;
    private static long windowFramesSent = 0L;
    private static long windowBytesSent = 0L;

    static {
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                robotInstance = new Robot();
            }
        } catch (Throwable t) {
            System.err.println("[BomboAddons] Could not initialize Robot for screensharing: " + t.getMessage());
        }
    }

    public static boolean isStreaming() {
        return STREAMING.get();
    }

    public static String getActiveTarget() {
        return activeStreamTarget;
    }

    public static void startStreaming(String targetUser) {
        activeStreamTarget = (targetUser != null && !targetUser.isEmpty()) ? targetUser : "All";
        if (STREAMING.compareAndSet(false, true)) {
            framesCaptured = 0L;
            framesSent = 0L;
            totalBytesSent = 0L;
            lastMetricCalcTime = System.currentTimeMillis();
            windowFramesSent = 0L;
            windowBytesSent = 0L;
            lastError = "";

            streamThread = new Thread(ScreenshareManager::streamLoop, "Bombo-ScreenshareStreamer");
            streamThread.setDaemon(true);
            streamThread.start();

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                String myIgn = mc.player.getScoreboardName();
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aLive screensharing started! View stream at: §b§nhttps://bombo.dpdns.org/screenshare?user=" + myIgn));
            }
        }
    }

    public static void stopStreaming() {
        if (STREAMING.compareAndSet(true, false)) {
            if (streamThread != null) {
                streamThread.interrupt();
                streamThread = null;
            }
            activeStreamTarget = "";
            currentFps = 0.0f;
            currentBitrateKbps = 0.0f;

            // Notify server to cleanup stream
            Minecraft mc = Minecraft.getInstance();
            String myIgn = (mc != null && mc.player != null) ? mc.player.getScoreboardName() : "";
            if (!myIgn.isEmpty()) {
                new Thread(() -> {
                    try {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("user", myIgn);
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.bombo.dpdns.org/api/screenshare/stop"))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(3))
                                .POST(HttpRequest.BodyPublishers.ofString(obj.toString(), StandardCharsets.UTF_8))
                                .build();
                        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
                    } catch (Throwable ignored) {}
                }, "Bombo-ScreenshareStop").start();
            }

            if (mc != null && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Live screensharing stopped."));
            }
        }
    }

    private static void streamLoop() {
        Minecraft mc = Minecraft.getInstance();
        while (STREAMING.get()) {
            long frameStart = System.currentTimeMillis();
            try {
                if (mc != null && mc.getWindow() != null) {
                    long capStart = System.currentTimeMillis();
                    byte[] jpegBytes = captureFrame(mc);
                    lastCaptureDurationMs = System.currentTimeMillis() - capStart;

                    if (jpegBytes != null && jpegBytes.length > 0) {
                        framesCaptured++;
                        String b64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes);
                        String myIgn = mc.player != null ? mc.player.getScoreboardName() : "User";

                        JsonObject payload = new JsonObject();
                        payload.addProperty("user", myIgn);
                        payload.addProperty("frame", b64);
                        payload.addProperty("fps", (int) Math.max(15, currentFps));
                        payload.addProperty("width", TARGET_WIDTH);
                        payload.addProperty("height", TARGET_HEIGHT);

                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.bombo.dpdns.org/api/screenshare/frame"))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofMillis(800))
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                                .build();

                        long postStart = System.currentTimeMillis();
                        HttpResponse<Void> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
                        lastLatencyMs = System.currentTimeMillis() - postStart;

                        if (resp.statusCode() == 200) {
                            framesSent++;
                            totalBytesSent += jpegBytes.length;
                            windowFramesSent++;
                            windowBytesSent += jpegBytes.length;
                            lastError = "";
                        } else {
                            lastError = "Server returned HTTP " + resp.statusCode();
                        }
                    }
                }
            } catch (Throwable t) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            }

            // Calculate rolling FPS & Bitrate every second
            long now = System.currentTimeMillis();
            long dt = now - lastMetricCalcTime;
            if (dt >= 1000L) {
                currentFps = (windowFramesSent * 1000.0f) / dt;
                currentBitrateKbps = ((windowBytesSent * 8.0f) / 1024.0f) / (dt / 1000.0f);
                windowFramesSent = 0L;
                windowBytesSent = 0L;
                lastMetricCalcTime = now;
            }

            long elapsed = System.currentTimeMillis() - frameStart;
            // Aim for ~25-30 FPS (target ~35-40ms total frame cycle)
            long sleepTime = Math.max(8L, 35L - elapsed);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static byte[] captureFrame(Minecraft mc) {
        try {
            if (robotInstance == null) {
                robotInstance = new Robot();
            }

            int winX = mc.getWindow().getX();
            int winY = mc.getWindow().getY();
            int winW = mc.getWindow().getWidth();
            int winH = mc.getWindow().getHeight();

            if (winW <= 0 || winH <= 0) return null;

            // Clamping against virtual screen bounds to prevent IllegalArgumentException
            Rectangle virtualBounds = getVirtualScreenBounds();
            int clampedX = Math.max(virtualBounds.x, Math.min(winX, virtualBounds.x + virtualBounds.width - 50));
            int clampedY = Math.max(virtualBounds.y, Math.min(winY, virtualBounds.y + virtualBounds.height - 50));
            int clampedW = Math.min(winW, virtualBounds.x + virtualBounds.width - clampedX);
            int clampedH = Math.min(winH, virtualBounds.y + virtualBounds.height - clampedY);

            if (clampedW <= 10 || clampedH <= 10) {
                clampedX = virtualBounds.x;
                clampedY = virtualBounds.y;
                clampedW = Math.min(virtualBounds.width, 1280);
                clampedH = Math.min(virtualBounds.height, 720);
            }

            Rectangle rect = new Rectangle(clampedX, clampedY, clampedW, clampedH);
            BufferedImage fullImg = robotInstance.createScreenCapture(rect);
            if (fullImg == null) return null;

            // Downscale to 640x360 for high FPS and low network load
            BufferedImage scaled = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.drawImage(fullImg, 0, 0, TARGET_WIDTH, TARGET_HEIGHT, null);
            g2.dispose();

            // Compress with JPEG quality 0.70 for crisp clarity and fast encoding
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
                writer.setOutput(mcios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.70f);
                }
                writer.write(null, new IIOImage(scaled, null, null), param);
            } finally {
                writer.dispose();
            }

            return baos.toByteArray();
        } catch (Throwable t) {
            lastError = "Capture: " + t.getMessage();
            return null;
        }
    }

    private static Rectangle getVirtualScreenBounds() {
        Rectangle bounds = new Rectangle();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        for (GraphicsDevice gd : devices) {
            bounds = bounds.union(gd.getDefaultConfiguration().getBounds());
        }
        if (bounds.width <= 0 || bounds.height <= 0) {
            Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
            bounds = new Rectangle(0, 0, d.width, d.height);
        }
        return bounds;
    }

    public static void dumpDebugInfo(Consumer<Component> feedback) {
        Minecraft mc = Minecraft.getInstance();
        String myIgn = (mc != null && mc.player != null) ? mc.player.getScoreboardName() : "User";

        feedback.accept(Component.literal("§9========== §b[Screenshare Stream Diagnostics] §9=========="));
        feedback.accept(Component.literal("§7Streaming Active: " + (isStreaming() ? "§aYes (Broadcasting)" : "§cNo (Stopped)")));
        feedback.accept(Component.literal("§7Target Audience: §f" + (activeStreamTarget.isEmpty() ? "None" : activeStreamTarget)));
        feedback.accept(Component.literal("§7Stream Quality: §e" + TARGET_WIDTH + "x" + TARGET_HEIGHT + " §7(JPEG 70% Quality)"));
        feedback.accept(Component.literal("§7Live Framerate: §b" + String.format("%.1f", currentFps) + " FPS §7(Target: §b25-30 FPS§7)"));
        feedback.accept(Component.literal("§7Current Bitrate: §d" + String.format("%.1f", currentBitrateKbps) + " kbps"));
        feedback.accept(Component.literal("§7HTTP POST Latency: §a" + lastLatencyMs + "ms"));
        feedback.accept(Component.literal("§7Screen Capture Time: §f" + lastCaptureDurationMs + "ms"));
        feedback.accept(Component.literal("§7Frames Captured / Sent: §e" + framesCaptured + " §7/ §a" + framesSent));
        feedback.accept(Component.literal("§7Total Bandwidth Sent: §e" + String.format("%.2f", totalBytesSent / 1048576.0) + " MB"));
        feedback.accept(Component.literal("§7Last Stream Error: " + (lastError.isEmpty() ? "§aNone" : "§c" + lastError)));
        feedback.accept(Component.literal("§7Live Stream Viewer: §b§nhttps://bombo.dpdns.org/screenshare?user=" + myIgn));
        feedback.accept(Component.literal("§9========================================================"));
    }
}
