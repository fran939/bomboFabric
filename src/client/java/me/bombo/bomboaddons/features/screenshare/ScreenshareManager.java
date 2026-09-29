package me.bombo.bomboaddons.features.screenshare;

import com.google.gson.JsonObject;
import me.bombo.bomboaddons.BomboConfig;
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

/**
 * Captures live in-game video frames and streams them to the Bombo screenshare server
 * (https://bombo.dpdns.org/screenshare) so other players can view the screen live in web browser or HUD.
 */
public class ScreenshareManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final AtomicBoolean STREAMING = new AtomicBoolean(false);
    private static Thread streamThread = null;
    private static volatile String activeStreamTarget = "";
    private static Robot robotInstance = null;

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
            streamThread = new Thread(ScreenshareManager::streamLoop, "Bombo-ScreenshareStreamer");
            streamThread.setDaemon(true);
            streamThread.start();

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                String myIgn = mc.player.getScoreboardName();
                mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aLive screensharing started! View at: §b§nhttps://bombo.dpdns.org/screenshare?user=" + myIgn));
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
                    byte[] jpegBytes = captureFrame(mc);
                    if (jpegBytes != null && jpegBytes.length > 0) {
                        String b64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes);
                        String myIgn = mc.player != null ? mc.player.getScoreboardName() : "User";

                        JsonObject payload = new JsonObject();
                        payload.addProperty("user", myIgn);
                        payload.addProperty("frame", b64);
                        payload.addProperty("fps", 10);
                        payload.addProperty("width", 640);
                        payload.addProperty("height", 360);

                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("https://api.bombo.dpdns.org/api/screenshare/frame"))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofMillis(1200))
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                                .build();

                        HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
                    }
                }
            } catch (Throwable ignored) {
            }

            long elapsed = System.currentTimeMillis() - frameStart;
            long sleepTime = Math.max(80L, 120L - elapsed); // Aim for ~8-10 FPS
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

            Rectangle rect = new Rectangle(Math.max(0, winX), Math.max(0, winY), winW, winH);
            BufferedImage fullImg = robotInstance.createScreenCapture(rect);
            if (fullImg == null) return null;

            // Downscale to 640x360 for high FPS and low bandwidth
            int targetW = 640;
            int targetH = 360;
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
            g2.drawImage(fullImg, 0, 0, targetW, targetH, null);
            g2.dispose();

            // Compress with JPEG quality 0.65 for fast transmission
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            try (MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos)) {
                writer.setOutput(mcios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.65f);
                }
                writer.write(null, new IIOImage(scaled, null, null), param);
            } finally {
                writer.dispose();
            }

            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}
