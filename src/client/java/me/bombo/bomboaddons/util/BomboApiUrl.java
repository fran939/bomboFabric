package me.bombo.bomboaddons.util;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class BomboApiUrl {
    public static final String MAIN_API_BASE = "https://api.bombo.dpdns.org";
    public static final String MAIN_WEB_BASE = "https://bombo.dpdns.org";
    public static final String BACKUP_API_BASE = "https://bomboapi.frandl938.workers.dev";

    private static final AtomicBoolean useMainDomain = new AtomicBoolean(true);
    private static final AtomicLong lastCheckTime = new AtomicLong(0);
    private static final long CHECK_INTERVAL_MS = 60000; // Re-check health every 60 seconds

    public static String getApiBase() {
        checkHealthIfNeeded();
        return useMainDomain.get() ? MAIN_API_BASE : BACKUP_API_BASE;
    }

    public static String getWebBase() {
        checkHealthIfNeeded();
        return useMainDomain.get() ? MAIN_WEB_BASE : BACKUP_API_BASE;
    }

    public static String getApiUrl(String path) {
        if (path == null) path = "";
        if (!path.startsWith("/")) path = "/" + path;
        return getApiBase() + path;
    }

    public static String getWebUrl(String path) {
        if (path == null) path = "";
        if (!path.startsWith("/")) path = "/" + path;
        return getWebBase() + path;
    }

    private static synchronized void checkHealthIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime.get() < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime.set(now);

        new Thread(() -> {
            boolean ok = testUrl(MAIN_API_BASE + "/") || testUrl(MAIN_WEB_BASE + "/");
            useMainDomain.set(ok);
            System.out.println("[BomboAddons] Main domain health check: " + (ok ? "ONLINE (using api.bombo.dpdns.org)" : "OFFLINE (falling back to workers.dev)"));
        }, "BomboApi-HealthCheck").start();
    }

    public static boolean testUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(2500);
            conn.setReadTimeout(2500);
            int code = conn.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) {
            return false;
        }
    }
}
