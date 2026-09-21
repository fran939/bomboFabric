package me.bombo.bomboaddons.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import net.fabricmc.loader.api.FabricLoader;

public class NetworkDebugLogger {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final File LOG_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/debug_network.log").toFile();

    public static synchronized void log(String type, String url, int statusCode, String details) {
        try {
            if (!LOG_FILE.getParentFile().exists()) {
                LOG_FILE.getParentFile().mkdirs();
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                String time = DATE_FORMAT.format(new Date());
                pw.println("[" + time + "] [" + type + "] Status: " + statusCode + " | URL: " + url);
                if (details != null && !details.trim().isEmpty()) {
                    pw.println("  Details: " + details.trim());
                }
                pw.flush();
            }
        } catch (Throwable ignored) {}
    }

    public static void logHttp(String method, String url, int status, String responsePreview) {
        log("HTTP-" + method, url, status, responsePreview);
    }

    public static void logWebSocket(String event, String url, int status, String frame) {
        log("WS-" + event, url, status, frame);
    }
}
