package me.bombo.bomboaddons.util;

import me.bombo.bomboaddons.BomboConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Outbound request history for {@code /b apihistory}.
 *
 * <p>Every HTTP call and WebSocket frame the mod makes is funnelled through {@link #record} so the
 * user can answer "did my request actually go out, and what came back" without a debugger. The
 * history is an in-memory ring buffer (bounded by {@code apiHistoryMaxEntries}) mirrored into
 * {@code config/bomboaddons/api_history.log}, which is truncated when it grows past
 * {@link #MAX_LOG_BYTES} so it can never eat the disk on a long-running session.
 *
 * <p>This is deliberately dependency-free and exception-swallowing: request logging must never be
 * able to break the request it is describing.
 */
public final class ApiHistory {

    public enum Kind {
        HTTP("HTTP"),
        WS("WS");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** One outbound request or WebSocket frame. */
    public static final class Entry {
        public final long timestamp;
        public final Kind kind;
        public final String method;
        public final String url;
        public final int status;
        public final long durationMs;
        public final String note;
        public final String headers;
        public final String service;

        Entry(Kind kind, String method, String url, int status, long durationMs, String note, String headers) {
            this.timestamp = System.currentTimeMillis();
            this.kind = kind;
            this.method = method != null ? method : "GET";
            this.url = maskSensitive(url != null ? url : "");
            this.status = status;
            this.durationMs = durationMs;
            this.note = note;
            this.headers = maskHeaders(headers);
            this.service = classify(this.url);
        }
    }

    public static String maskSensitive(String str) {
        if (str == null) return null;
        return str.replaceAll("(?i)(key|token|auth)=([a-zA-Z0-9_-]{4})[a-zA-Z0-9_-]+([a-zA-Z0-9_-]{4})", "$1=$2****$3");
    }

    public static String maskHeaders(String headers) {
        if (headers == null || headers.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (String line : headers.split("[\\r\\n]+")) {
            if (line.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            int colon = line.indexOf(':');
            if (colon > 0) {
                String name = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                if (name.equalsIgnoreCase("API-Key") || name.equalsIgnoreCase("Authorization") || name.toLowerCase(Locale.ROOT).contains("key") || name.toLowerCase(Locale.ROOT).contains("token")) {
                    if (val.startsWith("Bearer ") && val.length() > 15) {
                        String token = val.substring(7);
                        val = "Bearer " + token.substring(0, 4) + "****" + token.substring(token.length() - 4);
                    } else if (val.length() > 8) {
                        val = val.substring(0, 4) + "****" + val.substring(val.length() - 4);
                    } else {
                        val = "****";
                    }
                }
                sb.append(name).append(": ").append(val);
            } else {
                sb.append(line.trim());
            }
        }
        return sb.toString();
    }

    /** Services the tracker labels explicitly; anything else shows as "Other". */
    public static String classify(String url) {
        if (url == null) return "Other";
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("hypixel.net")) return "Hypixel";
        if (lower.contains("athen")) return "Athen";
        if (lower.contains("eliteskyblock") || lower.contains("elitebot")) return "EliteSkyblock";
        if (lower.contains("bombo.dpdns.org") || lower.contains("bomboclas")) return "Bombo API";
        if (lower.contains("coflnet")) return "Coflnet";
        return "Other";
    }

    private static final long MAX_LOG_BYTES = 2L * 1024L * 1024L;
    private static final File LOG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("bomboaddons/api_history.log").toFile();
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS");

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static long totalRecorded = 0L;
    private static long droppedFromMemory = 0L;

    private ApiHistory() {
    }

    public static void record(Kind kind, String method, String url, int status, long durationMs, String note) {
        record(kind, method, url, status, durationMs, note, null);
    }

    public static synchronized void record(Kind kind, String method, String url, int status, long durationMs, String note, String headers) {
        if (kind == null) kind = Kind.HTTP;
        BomboConfig.Settings cfg = BomboConfig.get();
        if (cfg != null && !cfg.apiHistoryEnabled) {
            return;
        }
        int max = cfg != null && cfg.apiHistoryMaxEntries > 0 ? cfg.apiHistoryMaxEntries : 500;

        Entry entry = new Entry(kind, method, url, status, durationMs, note, headers);
        ENTRIES.addLast(entry);
        totalRecorded++;
        while (ENTRIES.size() > max) {
            ENTRIES.removeFirst();
            droppedFromMemory++;
        }

        appendToFile(entry);
    }

    /** Convenience for HTTP calls whose response we have seen. */
    public static void http(String method, String url, int status, long durationMs) {
        record(Kind.HTTP, method, url, status, durationMs, null, null);
    }

    public static void http(String method, String url, int status, long durationMs, String headers) {
        record(Kind.HTTP, method, url, status, durationMs, null, headers);
    }

    /**
     * An outbound request we know was issued but whose response is not observed by the caller
     * (raw {@code HttpURLConnection} sites). Status {@code -1} renders as "..." rather than an
     * error, so "the request went out" and "the request failed" stay distinguishable.
     */
    public static void issued(String method, String url) {
        // Collapse bursts: price refreshes hit the same URL several times per second and would
        // otherwise bury every other request in the viewer.
        synchronized (ApiHistory.class) {
            Entry last = ENTRIES.peekLast();
            if (last != null && last.kind == Kind.HTTP && last.status == -1
                    && last.url != null && last.url.equals(url)
                    && System.currentTimeMillis() - last.timestamp < 3000L) {
                return;
            }
        }
        record(Kind.HTTP, method, url, -1, 0L, "issued");
    }

    /**
     * Records something worth knowing that did not produce a normal response - a request we chose
     * not to send, or a failure the caller already understands. The reason shows up in the
     * {@code /b apihistory} list and in {@code api_history.log}.
     */
    public static void note(String method, String url, int status, String reason) {
        record(Kind.HTTP, method, url, status, 0L, reason);
    }

    /** Convenience for WebSocket lifecycle events and frames. */
    public static void ws(String event, String url, int status, String frame) {
        record(Kind.WS, event, url, status, 0L, frame);
    }

    public static synchronized List<Entry> getEntries() {
        return new ArrayList<>(ENTRIES);
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        droppedFromMemory = 0L;
        totalRecorded = 0L;
    }

    public static synchronized long getTotalRecorded() {
        return totalRecorded;
    }

    public static synchronized long getDroppedFromMemory() {
        return droppedFromMemory;
    }

    /** Renders the status column; {@code -1} is an issued-but-unconfirmed request. */
    public static String statusText(int status) {
        if (status == -1) return "...";
        if (status == 0) return "ERR";
        return String.valueOf(status);
    }

    /** Aggregated counters for the screen header. */
    public static String summarize(List<Entry> entries) {
        int ok = 0;
        int failed = 0;
        long slowest = 0L;
        String slowestUrl = null;
        for (Entry e : entries) {
            if (e.status >= 200 && e.status < 400) {
                ok++;
            } else if (e.status > 0) {
                failed++;
            }
            if (e.durationMs > slowest) {
                slowest = e.durationMs;
                slowestUrl = e.url;
            }
        }
        return "§a" + ok + " ok §8| §c" + failed + " failed §8| §7slowest: §e" + slowest + "ms"
                + (slowestUrl != null ? " §8(" + shorten(slowestUrl) + "§8)" : "");
    }

    public static String shorten(String url) {
        if (url == null) return "";
        String clean = url.replaceFirst("^https?://", "");
        return clean.length() > 60 ? clean.substring(0, 57) + "..." : clean;
    }

    public static String formatTime(long timestamp) {
        return TIME_FMT.format(new Date(timestamp));
    }

    private static void appendToFile(Entry entry) {
        try {
            File parent = LOG_FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            if (LOG_FILE.exists() && LOG_FILE.length() > MAX_LOG_BYTES) {
                // Rotate rather than grow forever.
                File old = new File(LOG_FILE.getParentFile(), "api_history.log.1");
                if (old.exists()) {
                    old.delete();
                }
                LOG_FILE.renameTo(old);
            }
            try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                pw.println("[" + TIME_FMT.format(new Date(entry.timestamp)) + "] [" + entry.kind.label + " " + entry.method
                        + "] " + entry.status + " " + entry.durationMs + "ms " + entry.url
                        + (entry.note != null ? " | " + entry.note : ""));
                pw.flush();
            }
        } catch (Throwable ignored) {
        }
    }
}
