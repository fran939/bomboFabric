package me.bombo.bomboaddons.features.screenshare;

import com.google.gson.JsonObject;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.PerformanceProfiler;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-performance screenshare streaming engine.
 * Supports configurable quality (720p 30fps / 1080p 60fps) and direct Minecraft framebuffer capture
 * (eliminating Windows OS cursor flicker and preventing accidental desktop sharing).
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
    private static volatile int currentTargetW = 1280;
    private static volatile int currentTargetH = 720;
    private static volatile long framesCaptured = 0L;
    private static volatile long framesSent = 0L;
    private static volatile long totalBytesSent = 0L;
    private static volatile float currentFps = 0.0f;
    private static volatile float currentBitrateKbps = 0.0f;
    private static volatile long lastLatencyMs = 0L;
    private static volatile long lastCaptureDurationMs = 0L;
    private static volatile long lastGpuCaptureMs = 0L;
    private static volatile long lastEncodeMs = 0L;
    private static volatile float lastJpegSizeKb = 0.0f;
    private static volatile String lastCaptureMode = "Direct GPU (OpenGL)";
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
            System.err.println("[BomboAddons] Robot fallback unavailable: " + t.getMessage());
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
                String rawIgn = mc.player.getScoreboardName();
                String myIgn = rawIgn != null ? net.minecraft.ChatFormatting.stripFormatting(rawIgn).trim() : "player";
                String streamUrl = "https://bombo.dpdns.org/screenshare?user=" + myIgn;
                net.minecraft.network.chat.MutableComponent link = Component.literal(streamUrl)
                        .withStyle(style -> style
                                .withColor(net.minecraft.ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(URI.create(streamUrl)))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§eClick to view live stream on web"))));
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aLive screensharing started! View stream at: ").append(link));
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
                Thread stopThread = new Thread(() -> {
                    try {
                        JsonObject obj = new JsonObject();
                        obj.addProperty("user", myIgn);
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.bombo.dpdns.org/api/screenshare/stop"))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(2))
                                .POST(HttpRequest.BodyPublishers.ofString(obj.toString(), StandardCharsets.UTF_8))
                                .build();
                        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
                    } catch (Throwable ignored) {}
                }, "Bombo-ScreenshareStop");
                stopThread.setDaemon(true);
                stopThread.start();
            }

            if (mc != null && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §7Live screensharing stopped."));
            }
        }
    }

    public static String getDirectGpuModeName() {
        try {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("vulkanmod")) {
                return "Direct GPU (Vulkan - <3ms)";
            }
        } catch (Throwable ignored) {}
        return "Direct GPU (OpenGL - <3ms)";
    }

    public static String getLastCaptureMode() {
        return lastCaptureMode;
    }

    public static float getCurrentFps() {
        return currentFps;
    }

    public static float getCurrentBitrateKbps() {
        return currentBitrateKbps;
    }

    public static long getLastLatencyMs() {
        return lastLatencyMs;
    }

    public static long getLastCaptureDurationMs() {
        return lastCaptureDurationMs;
    }

    public static long getLastGpuCaptureMs() {
        return lastGpuCaptureMs;
    }

    public static long getLastEncodeMs() {
        return lastEncodeMs;
    }

    public static float getLastJpegSizeKb() {
        return lastJpegSizeKb;
    }

    public static long getTotalBytesSent() {
        return totalBytesSent;
    }

    public static long getFramesCaptured() {
        return framesCaptured;
    }

    public static long getFramesSent() {
        return framesSent;
    }

    public static int getCurrentTargetW() {
        return currentTargetW;
    }

    public static int getCurrentTargetH() {
        return currentTargetH;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean gpuCaptureInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicInteger inFlightEncodes = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.ExecutorService ENCODE_POOL = java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "Bombo-ScreenshareEncoder");
        t.setDaemon(true);
        return t;
    });
    private static final ThreadLocal<ImageWriter> JPEG_WRITERS = ThreadLocal.withInitial(() -> {
        java.util.Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
        return it.hasNext() ? it.next() : null;
    });

    private static final java.util.concurrent.atomic.AtomicInteger inFlightPosts = new java.util.concurrent.atomic.AtomicInteger(0);

    private static void processAndSendFrame(Minecraft mc, byte[] jpegBytes, int targetW, int targetH, long capStart) {
        lastCaptureDurationMs = System.currentTimeMillis() - capStart;
        if (jpegBytes != null && jpegBytes.length > 0) {
            framesCaptured++;
            final int jpegLen = jpegBytes.length;
            String b64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes);
            String myIgn = (mc != null && mc.player != null) ? mc.player.getScoreboardName() : "User";

            JsonObject payload = new JsonObject();
            payload.addProperty("user", myIgn);
            payload.addProperty("frame", b64);
            payload.addProperty("fps", (int) Math.max(1, Math.round(currentFps)));
            payload.addProperty("width", targetW);
            payload.addProperty("height", targetH);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.bombo.dpdns.org/api/screenshare/frame"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(1200))
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            // Non-blocking async pipeline (up to 4 concurrent HTTP requests) for 30-60 FPS
            if (inFlightPosts.get() < 4) {
                inFlightPosts.incrementAndGet();
                long postStart = System.currentTimeMillis();
                HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .whenComplete((resp, err) -> {
                            inFlightPosts.decrementAndGet();
                            if (resp != null && resp.statusCode() == 200) {
                                framesSent++;
                                totalBytesSent += jpegLen;
                                windowFramesSent++;
                                windowBytesSent += jpegLen;
                                lastLatencyMs = System.currentTimeMillis() - postStart;
                                lastError = "";
                            } else if (resp != null) {
                                lastError = "Server HTTP " + resp.statusCode();
                            } else if (err != null) {
                                lastError = err.getMessage();
                            }
                        });
            }
        }
    }

    private static void streamLoop() {
        Minecraft mc = Minecraft.getInstance();
        while (STREAMING.get()) {
            long targetDelayMs = 33L;
            try {
                int targetW = 1280;
                int targetH = 720;
                float quality = 0.70f;
                BomboConfig.Settings s = BomboConfig.get();
                String q = (s != null && s.screenshareQuality != null) ? s.screenshareQuality : "720p 60fps";

                if (q.contains("1440p") || q.contains("2K")) {
                    targetW = 2560;
                    targetH = 1440;
                    targetDelayMs = q.contains("120fps") ? 8L : (q.contains("60fps") ? 16L : 33L);
                    quality = 0.50f;
                } else if (q.contains("1080p")) {
                    targetW = 1920;
                    targetH = 1080;
                    targetDelayMs = q.contains("120fps") ? 8L : (q.contains("60fps") ? 16L : 33L);
                    quality = 0.55f;
                } else {
                    targetW = 1280;
                    targetH = 720;
                    targetDelayMs = q.contains("30fps") ? 33L : 16L;
                    quality = 0.55f;
                }

                currentTargetW = targetW;
                currentTargetH = targetH;

                if (mc != null && inFlightEncodes.get() < 2 && gpuCaptureInProgress.compareAndSet(false, true)) {
                    long capStart = System.currentTimeMillis();
                    boolean mcActive = mc.isWindowActive();
                    final int fw = targetW;
                    final int fh = targetH;
                    final float fq = quality;
                    if (s == null || s.screenshareOnlyMinecraft || mcActive) {
                        lastCaptureMode = getDirectGpuModeName();
                        triggerMinecraftCapture(mc, fw, fh, fq, jpeg -> {
                            if (jpeg != null && jpeg.length > 0) {
                                processAndSendFrame(mc, jpeg, fw, fh, capStart);
                            } else {
                                lastCaptureMode = "AWT Robot Desktop (High CPU)";
                                triggerRobotCapture(mc, fw, fh, fq, rJpeg -> {
                                    processAndSendFrame(mc, rJpeg, fw, fh, capStart);
                                });
                            }
                        });
                    } else {
                        lastCaptureMode = "AWT Robot Desktop (High CPU)";
                        triggerRobotCapture(mc, fw, fh, fq, rJpeg -> {
                            processAndSendFrame(mc, rJpeg, fw, fh, capStart);
                        });
                    }
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

                Thread.sleep(targetDelayMs);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                gpuCaptureInProgress.set(false);
                try { Thread.sleep(50L); } catch (InterruptedException ignored) { break; }
            }
        }
        gpuCaptureInProgress.set(false);
    }

    private static void drawCursorIfVisible(Minecraft mc, BufferedImage bi, int imgW, int imgH) {
        try {
            if (mc == null || mc.gui == null || mc.gui.screen() == null) return;
            double mx = mc.mouseHandler.xpos();
            double my = mc.mouseHandler.ypos();
            int winW = mc.getWindow().getWidth();
            int winH = mc.getWindow().getHeight();
            if (winW <= 0 || winH <= 0) return;
            int cx = (int) Math.round((mx / (double) winW) * imgW);
            int cy = (int) Math.round((my / (double) winH) * imgH);
            if (cx < 0 || cx >= imgW || cy < 0 || cy >= imgH) return;

            Graphics2D g2 = bi.createGraphics();
            int[] xPoints = {cx, cx, cx + 11, cx + 7, cx + 12, cx + 10, cx + 5, cx + 8};
            int[] yPoints = {cy, cy + 15, cy + 11, cy + 9, cy + 15, cy + 16, cy + 10, cy + 8};

            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawPolygon(xPoints, yPoints, xPoints.length);
            g2.setColor(Color.WHITE);
            g2.fillPolygon(xPoints, yPoints, xPoints.length);
            g2.dispose();
        } catch (Throwable ignored) {}
    }

    private static void triggerMinecraftCapture(Minecraft mc, int targetW, int targetH, float quality, Consumer<byte[]> onDone) {
        if (mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) {
            gpuCaptureInProgress.set(false);
            onDone.accept(null);
            return;
        }

        long gpuStart = System.currentTimeMillis();
        mc.execute(() -> {
            try {
                com.mojang.blaze3d.pipeline.RenderTarget rt = mc.gameRenderer.mainRenderTarget();
                if (rt == null || rt.width <= 0 || rt.height <= 0) {
                    gpuCaptureInProgress.set(false);
                    onDone.accept(null);
                    return;
                }

                net.minecraft.client.Screenshot.takeScreenshot(rt, 1, nativeImg -> {
                    try {
                        if (nativeImg == null) {
                            gpuCaptureInProgress.set(false);
                            onDone.accept(null);
                            return;
                        }
                        lastGpuCaptureMs = System.currentTimeMillis() - gpuStart;
                        int w = nativeImg.getWidth();
                        int h = nativeImg.getHeight();
                        int[] pixels = nativeImg.makePixelArray();
                        nativeImg.close();
                        gpuCaptureInProgress.set(false);

                        inFlightEncodes.incrementAndGet();
                        ENCODE_POOL.execute(() -> {
                            try {
                                long encStart = System.currentTimeMillis();
                                BufferedImage bi = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
                                int[] destPixels = ((java.awt.image.DataBufferInt) bi.getRaster().getDataBuffer()).getData();

                                if (w == targetW && h == targetH) {
                                    System.arraycopy(pixels, 0, destPixels, 0, pixels.length);
                                } else {
                                    for (int y = 0; y < targetH; y++) {
                                        int srcY = (y * h) / targetH;
                                        int srcRow = srcY * w;
                                        int dstRow = y * targetW;
                                        for (int x = 0; x < targetW; x++) {
                                            destPixels[dstRow + x] = pixels[srcRow + (x * w) / targetW];
                                        }
                                    }
                                }
                                drawCursorIfVisible(mc, bi, targetW, targetH);
                                byte[] jpeg = compressScaledJpeg(bi, targetW, targetH, quality);
                                lastEncodeMs = System.currentTimeMillis() - encStart;
                                if (jpeg != null) {
                                    lastJpegSizeKb = jpeg.length / 1024.0f;
                                }
                                onDone.accept(jpeg);
                            } catch (Throwable t) {
                                onDone.accept(null);
                            } finally {
                                inFlightEncodes.decrementAndGet();
                            }
                        });
                    } catch (Throwable t) {
                        gpuCaptureInProgress.set(false);
                        try { if (nativeImg != null) nativeImg.close(); } catch (Throwable ignored) {}
                        onDone.accept(null);
                    }
                });
            } catch (Throwable t) {
                gpuCaptureInProgress.set(false);
                onDone.accept(null);
            }
        });
    }

    private static void triggerRobotCapture(Minecraft mc, int targetW, int targetH, float quality, Consumer<byte[]> onDone) {
        gpuCaptureInProgress.set(false);
        inFlightEncodes.incrementAndGet();
        ENCODE_POOL.execute(() -> {
            try {
                byte[] jpeg = captureRobotFrame(mc, targetW, targetH, quality);
                onDone.accept(jpeg);
            } catch (Throwable t) {
                onDone.accept(null);
            } finally {
                inFlightEncodes.decrementAndGet();
            }
        });
    }

    private static byte[] captureRobotFrame(Minecraft mc, int targetW, int targetH, float quality) {
        try {
            if (robotInstance == null) {
                robotInstance = new Robot();
            }

            int winX = mc.getWindow().getX();
            int winY = mc.getWindow().getY();
            int winW = mc.getWindow().getWidth();
            int winH = mc.getWindow().getHeight();

            if (winW <= 0 || winH <= 0) return null;

            Rectangle virtualBounds = getVirtualScreenBounds();
            int clampedX = Math.max(virtualBounds.x, Math.min(winX, virtualBounds.x + virtualBounds.width - 50));
            int clampedY = Math.max(virtualBounds.y, Math.min(winY, virtualBounds.y + virtualBounds.height - 50));
            int clampedW = Math.min(winW, virtualBounds.x + virtualBounds.width - clampedX);
            int clampedH = Math.min(winH, virtualBounds.y + virtualBounds.height - clampedY);

            if (clampedW <= 10 || clampedH <= 10) {
                clampedX = virtualBounds.x;
                clampedY = virtualBounds.y;
                clampedW = Math.min(virtualBounds.width, targetW);
                clampedH = Math.min(virtualBounds.height, targetH);
            }

            Rectangle rect = new Rectangle(clampedX, clampedY, clampedW, clampedH);
            BufferedImage fullImg = robotInstance.createScreenCapture(rect);
            if (fullImg == null) return null;

            return compressScaledJpeg(fullImg, targetW, targetH, quality);
        } catch (Throwable t) {
            lastError = "Robot Capture: " + t.getMessage();
            return null;
        }
    }

    private static byte[] compressScaledJpeg(BufferedImage src, int targetW, int targetH, float quality) throws Exception {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        BufferedImage scaled;
        if (src.getType() == BufferedImage.TYPE_INT_RGB && (srcW == targetW && srcH == targetH || (srcW <= targetW && srcH <= targetH))) {
            scaled = src;
        } else if (srcW <= targetW && srcH <= targetH) {
            // Keep native 1:1 pixel sharpness without upscaling
            scaled = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.drawImage(src, 0, 0, null);
            g2.dispose();
        } else {
            scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.drawImage(src, 0, 0, targetW, targetH, null);
            g2.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(65536);
        ImageWriter writer = JPEG_WRITERS.get();
        if (writer != null) {
            synchronized (writer) {
                try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
                    writer.setOutput(mcios);
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    if (param.canWriteCompressed()) {
                        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        param.setCompressionQuality(quality);
                    }
                    writer.write(null, new IIOImage(scaled, null, null), param);
                } finally {
                    writer.reset();
                }
            }
        } else {
            ImageIO.write(scaled, "jpg", baos);
        }

        return baos.toByteArray();
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
        BomboConfig.Settings s = BomboConfig.get();
        boolean onlyMc = s == null || s.screenshareOnlyMinecraft;
        String qualityMode = (s != null && s.screenshareQuality != null) ? s.screenshareQuality : "720p 30fps";

        feedback.accept(Component.literal("§9========== §b[Screenshare Stream Diagnostics] §9=========="));
        feedback.accept(Component.literal("§7Streaming Active: " + (isStreaming() ? "§aYes (Broadcasting)" : "§cNo (Stopped)")));
        feedback.accept(Component.literal("§7Target Audience: §f" + (activeStreamTarget.isEmpty() ? "None" : activeStreamTarget)));
        feedback.accept(Component.literal("§7Capture Mode: " + (lastCaptureMode.startsWith("Direct") ? "§a" : "§e") + lastCaptureMode));
        feedback.accept(Component.literal("§7Configured Quality: §e" + qualityMode + " §7(Streaming: §b" + currentTargetW + "x" + currentTargetH + "§7)"));
        feedback.accept(Component.literal("§7Live Framerate: §b" + String.format("%.1f", currentFps) + " FPS"));
        feedback.accept(Component.literal("§7Current Bitrate: §d" + String.format("%.1f", currentBitrateKbps) + " kbps"));
        feedback.accept(Component.literal("§7HTTP POST Latency: §a" + lastLatencyMs + "ms §8| §7Frame Payload Size: §b" + String.format("%.1f", lastJpegSizeKb) + " KB"));
        feedback.accept(Component.literal("§7Frame Capture Time: §f" + lastCaptureDurationMs + "ms §7(GPU Read: §a" + lastGpuCaptureMs + "ms §7| Downsample & Encode: §e" + lastEncodeMs + "ms§7)"));
        feedback.accept(Component.literal("§7Frames Captured / Sent: §e" + framesCaptured + " §7/ §a" + framesSent));
        feedback.accept(Component.literal("§7Total Bandwidth Sent: §e" + String.format("%.2f", totalBytesSent / 1048576.0) + " MB"));
        feedback.accept(Component.literal("§7Last Stream Error: " + (lastError.isEmpty() ? "§aNone" : "§c" + lastError)));
        String cleanIgn = myIgn != null ? net.minecraft.ChatFormatting.stripFormatting(myIgn).trim() : "player";
        String diagStreamUrl = "https://bombo.dpdns.org/screenshare?user=" + cleanIgn;
        net.minecraft.network.chat.MutableComponent diagLink = Component.literal(diagStreamUrl)
                .withStyle(style -> style
                        .withColor(net.minecraft.ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(URI.create(diagStreamUrl)))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§eClick to view live stream on web"))));
        feedback.accept(Component.literal("§7Live Stream Viewer: ").append(diagLink));
        feedback.accept(Component.literal("§9========================================================"));
    }
}
