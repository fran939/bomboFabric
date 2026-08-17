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
   private static final AtomicLong lastCheckTime = new AtomicLong(0L);
   private static final long CHECK_INTERVAL_MS = 60000L;

   public static String getApiBase() {
      return "https://api.bombo.dpdns.org";
   }

   public static String getWebBase() {
      return "https://bombo.dpdns.org";
   }

   public static String getApiUrl(String path) {
      if (path == null) {
         path = "";
      }

      if (!path.startsWith("/")) {
         path = "/" + path;
      }

      String var10000 = getApiBase();
      return var10000 + path;
   }

   public static String getWebUrl(String path) {
      if (path == null) {
         path = "";
      }

      if (!path.startsWith("/")) {
         path = "/" + path;
      }

      String var10000 = getWebBase();
      return var10000 + path;
   }

   private static synchronized void checkHealthIfNeeded() {
      long now = System.currentTimeMillis();
      if (now - lastCheckTime.get() >= 60000L) {
         lastCheckTime.set(now);
         (new Thread(() -> {
            boolean ok = testUrl("https://api.bombo.dpdns.org/") || testUrl("https://bombo.dpdns.org/");
            useMainDomain.set(ok);
            System.out.println("[BomboAddons] Main domain health check: " + (ok ? "ONLINE (using api.bombo.dpdns.org)" : "OFFLINE (falling back to workers.dev)"));
         }, "BomboApi-HealthCheck")).start();
      }
   }

   public static boolean testUrl(String urlStr) {
      try {
         URL url = new URL(urlStr);
         HttpURLConnection conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("HEAD");
         conn.setConnectTimeout(2500);
         conn.setReadTimeout(2500);
         int code = conn.getResponseCode();
         return code >= 200 && code < 400;
      } catch (Exception var4) {
         return false;
      }
   }
}
