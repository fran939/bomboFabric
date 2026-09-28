package me.bombo.bomboaddons.features.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages fetching, parsing, and real-time word-for-word synchronization of music lyrics.
 * Supports multiple providers inspired by vivi-music: LRCLIB, PAXSENIX, UNISON, YOULYPLUS.
 */
public class LyricsManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Pattern LINE_PATTERN = Pattern.compile("^\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.*)$");
    private static final Pattern WORD_PATTERN = Pattern.compile("<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)");

    public record WordTime(String word, long startMs, long endMs) {}

    public record LyricsLine(long startMs, long endMs, String text, List<WordTime> words) {
        public boolean isWordActive(int wordIdx, long currentMs) {
            if (words == null || wordIdx < 0 || wordIdx >= words.size()) return false;
            WordTime wt = words.get(wordIdx);
            return currentMs >= wt.startMs;
        }
    }

    private static volatile String activeTrack = "";
    private static volatile String activeArtist = "";
    private static volatile boolean loading = false;
    private static volatile boolean synced = false;
    private static volatile String statusMessage = "No track playing";
    private static volatile String lastRawLyrics = "No lyrics loaded yet.";
    private static final List<LyricsLine> currentLines = new ArrayList<>();

    private static final Map<String, List<LyricsLine>> CACHE = new HashMap<>();

    public static final String[] PROVIDERS = new String[]{"LRCLIB", "PAXSENIX", "UNISON", "YOULYPLUS"};

    public static synchronized List<LyricsLine> getLines() {
        return new ArrayList<>(currentLines);
    }

    public static boolean isLoading() {
        return loading;
    }

    public static boolean isSynced() {
        return synced;
    }

    public static boolean hasLyrics() {
        return !currentLines.isEmpty();
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static String getRawLyrics() {
        return lastRawLyrics != null ? lastRawLyrics : "No raw lyrics available.";
    }

    public static String getProvider() {
        BomboConfig.Settings s = BomboConfig.get();
        return (s != null && s.lyricsProvider != null && !s.lyricsProvider.isEmpty()) ? s.lyricsProvider : "LRCLIB";
    }

    public static void setProvider(String prov) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            s.lyricsProvider = prov;
            BomboConfig.save();
        }
        refetchCurrent();
    }

    public static void cycleProvider() {
        String current = getProvider();
        int idx = 0;
        for (int i = 0; i < PROVIDERS.length; i++) {
            if (PROVIDERS[i].equalsIgnoreCase(current)) {
                idx = (i + 1) % PROVIDERS.length;
                break;
            }
        }
        setProvider(PROVIDERS[idx]);
    }

    public static void refetchCurrent() {
        if (!activeTrack.isEmpty()) {
            String track = activeTrack;
            String artist = activeArtist;
            activeTrack = "";
            activeArtist = "";
            CACHE.clear();
            updateTrack(track, artist, SpotifyManager.getProgressSeconds());
        }
    }

    public static synchronized int getCurrentLineIndex(long currentMs) {
        if (currentLines.isEmpty()) return -1;
        int activeIdx = -1;
        for (int i = 0; i < currentLines.size(); i++) {
            LyricsLine line = currentLines.get(i);
            if (currentMs >= line.startMs) {
                activeIdx = i;
            } else {
                break;
            }
        }
        return activeIdx;
    }

    public static void updateTrack(String track, String artist, int posSec) {
        if (track == null || track.isEmpty()) {
            activeTrack = "";
            activeArtist = "";
            currentLines.clear();
            statusMessage = "No track playing";
            lastRawLyrics = "No track playing.";
            return;
        }

        String cacheKey = (getProvider() + " - " + artist + " - " + track).toLowerCase(Locale.ROOT).trim();
        String currentKey = (getProvider() + " - " + activeArtist + " - " + activeTrack).toLowerCase(Locale.ROOT).trim();

        if (cacheKey.equals(currentKey) && !currentLines.isEmpty()) {
            return; // Already loaded
        }

        activeTrack = track;
        activeArtist = artist;

        if (CACHE.containsKey(cacheKey)) {
            synchronized (LyricsManager.class) {
                currentLines.clear();
                currentLines.addAll(CACHE.get(cacheKey));
                synced = true;
                statusMessage = "Lyrics loaded (" + getProvider() + ")";
            }
            return;
        }

        fetchLyricsAsync(track, artist, cacheKey);
    }

    private static void fetchLyricsAsync(String track, String artist, String cacheKey) {
        loading = true;
        statusMessage = "Searching lyrics on " + getProvider() + "...";

        Thread fetchThread = new Thread(() -> {
            try {
                String cleanTrack = cleanTitle(track);
                String cleanArtist = cleanTitle(artist);
                String provider = getProvider();

                boolean success = false;
                if ("PAXSENIX".equalsIgnoreCase(provider)) {
                    success = fetchPaxsenix(cleanTrack, cleanArtist, cacheKey);
                } else if ("UNISON".equalsIgnoreCase(provider)) {
                    success = fetchUnison(cleanTrack, cleanArtist, cacheKey);
                } else if ("YOULYPLUS".equalsIgnoreCase(provider)) {
                    success = fetchYouLyPlus(cleanTrack, cleanArtist, cacheKey);
                }

                if (!success) {
                    // Default / fallback to LRCLIB
                    fetchLrcLib(cleanTrack, cleanArtist, cacheKey);
                }
            } catch (Throwable t) {
                synchronized (LyricsManager.class) {
                    loading = false;
                    statusMessage = "Could not fetch lyrics: " + t.getMessage();
                }
            }
        }, "Bombo-LyricsFetcher");

        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    private static boolean fetchLrcLib(String cleanTrack, String cleanArtist, String cacheKey) {
        try {
            String url = "https://lrclib.net/api/get?track_name="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&artist_name=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "BomboAddons/1.0 (https://github.com/fran939/bombofabric)")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                parseAndApplyLyrics(resp.body(), cacheKey, "LRCLIB");
                return true;
            }

            // Search fallback
            String searchUrl = "https://lrclib.net/api/search?q="
                    + URLEncoder.encode(cleanTrack + " " + cleanArtist, StandardCharsets.UTF_8);

            HttpRequest searchReq = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "BomboAddons/1.0 (https://github.com/fran939/bombofabric)")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> searchResp = HTTP_CLIENT.send(searchReq, HttpResponse.BodyHandlers.ofString());
            if (searchResp.statusCode() == 200) {
                JsonElement elem = JsonParser.parseString(searchResp.body());
                if (elem.isJsonArray() && elem.getAsJsonArray().size() > 0) {
                    JsonObject best = elem.getAsJsonArray().get(0).getAsJsonObject();
                    parseAndApplyLyrics(best.toString(), cacheKey, "LRCLIB");
                    return true;
                }
            }

            synchronized (LyricsManager.class) {
                currentLines.clear();
                loading = false;
                synced = false;
                statusMessage = "No lyrics found for " + cleanTrack;
                lastRawLyrics = "No lyrics found on LRCLIB.";
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean fetchPaxsenix(String cleanTrack, String cleanArtist, String cacheKey) {
        try {
            String url = "https://lyrics.paxsenix.org/apple-music/search?q="
                    + URLEncoder.encode(cleanTrack + " " + cleanArtist, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ViviMusic/1.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                parseAndApplyLyrics(resp.body(), cacheKey, "PAXSENIX");
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean fetchUnison(String cleanTrack, String cleanArtist, String cacheKey) {
        try {
            String url = "https://unison.boidu.dev/lyrics?song="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&artist=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ViviMusic/1.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                parseAndApplyLyrics(resp.body(), cacheKey, "UNISON");
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean fetchYouLyPlus(String cleanTrack, String cleanArtist, String cacheKey) {
        try {
            String url = "https://lyricsplus.binimum.org/v2/lyrics/get?title="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&artist=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ViviMusic/1.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                parseAndApplyLyrics(resp.body(), cacheKey, "YOULYPLUS");
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static void parseAndApplyLyrics(String jsonStr, String cacheKey, String providerName) {
        try {
            lastRawLyrics = jsonStr;
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            boolean isInstrumental = obj.has("instrumental") && obj.get("instrumental").getAsBoolean();
            if (isInstrumental) {
                synchronized (LyricsManager.class) {
                    currentLines.clear();
                    loading = false;
                    synced = false;
                    statusMessage = "Instrumental Track (" + providerName + ")";
                    lastRawLyrics = "Instrumental Track";
                }
                return;
            }

            String syncedLyrics = obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()
                    ? obj.get("syncedLyrics").getAsString() : "";
            String plainLyrics = obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()
                    ? obj.get("plainLyrics").getAsString() : "";

            if (syncedLyrics.isEmpty() && obj.has("lrc") && !obj.get("lrc").isJsonNull()) {
                syncedLyrics = obj.get("lrc").getAsString();
            }

            List<LyricsLine> parsed = new ArrayList<>();

            if (!syncedLyrics.isEmpty()) {
                lastRawLyrics = syncedLyrics;
                parsed = parseLrc(syncedLyrics);
                synced = true;
            } else if (!plainLyrics.isEmpty()) {
                lastRawLyrics = plainLyrics;
                String[] lines = plainLyrics.split("\r?\n");
                long estimatedTime = 0L;
                for (String l : lines) {
                    String clean = l.trim();
                    if (!clean.isEmpty()) {
                        parsed.add(new LyricsLine(estimatedTime, estimatedTime + 3000L, clean, null));
                        estimatedTime += 3000L;
                    }
                }
                synced = false;
            }

            synchronized (LyricsManager.class) {
                currentLines.clear();
                currentLines.addAll(parsed);
                loading = false;
                if (!parsed.isEmpty()) {
                    CACHE.put(cacheKey, new ArrayList<>(parsed));
                    statusMessage = (synced ? "Synced Lyrics" : "Plain Lyrics") + " (" + providerName + ")";
                } else {
                    statusMessage = "No lyrics found (" + providerName + ")";
                }
            }
        } catch (Throwable t) {
            synchronized (LyricsManager.class) {
                loading = false;
                statusMessage = "Parse error: " + t.getMessage();
            }
        }
    }

    private static String cleanTitle(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*\\(feat\\..*?\\)", "")
                .replaceAll("(?i)\\s*\\[official.*?\\]", "")
                .replaceAll("(?i)\\s*- remastered.*", "")
                .replaceAll("(?i)\\s*- remix.*", "")
                .trim();
    }

    private static List<LyricsLine> parseLrc(String lrc) {
        List<LyricsLine> lines = new ArrayList<>();
        String[] rawLines = lrc.split("\r?\n");

        for (String raw : rawLines) {
            raw = raw.trim();
            if (raw.isEmpty() || raw.startsWith("[offset:") || raw.startsWith("[ti:") || raw.startsWith("[ar:")) {
                continue;
            }

            Matcher m = LINE_PATTERN.matcher(raw);
            if (m.matches()) {
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long sub = Long.parseLong(m.group(3));
                long ms = sub;
                if (m.group(3).length() == 2) ms *= 10;
                long startMs = (min * 60 + sec) * 1000 + ms;

                String content = m.group(4).trim();
                lines.add(new LyricsLine(startMs, startMs + 4000L, content, null));
            }
        }

        lines.sort(Comparator.comparingLong(LyricsLine::startMs));

        List<LyricsLine> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LyricsLine cur = lines.get(i);
            long endMs = (i + 1 < lines.size()) ? lines.get(i + 1).startMs : cur.startMs + 5000L;
            if (endMs <= cur.startMs) endMs = cur.startMs + 2000L;

            List<WordTime> words = parseRichSyncWords(cur.text, cur.startMs, endMs);
            String cleanText = cur.text.replaceAll("<\\d{1,2}:\\d{2}\\.\\d{2,3}>", "").trim();
            
            // Reconstruct cleanText with consistent spaces if words are parsed
            if (words != null && !words.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int w = 0; w < words.size(); w++) {
                    if (w > 0) sb.append(" ");
                    sb.append(words.get(w).word());
                }
                cleanText = sb.toString();
            }

            result.add(new LyricsLine(cur.startMs, endMs, cleanText, words));
        }

        return result;
    }

    private static List<WordTime> parseRichSyncWords(String text, long lineStartMs, long lineEndMs) {
        Matcher wm = WORD_PATTERN.matcher(text);
        List<WordTime> list = new ArrayList<>();
        while (wm.find()) {
            long min = Long.parseLong(wm.group(1));
            long sec = Long.parseLong(wm.group(2));
            long sub = Long.parseLong(wm.group(3));
            long ms = sub;
            if (wm.group(3).length() == 2) ms *= 10;
            long wordStart = (min * 60 + sec) * 1000 + ms;
            String word = wm.group(4).trim();
            if (!word.isEmpty()) {
                list.add(new WordTime(word, wordStart, wordStart + 500L));
            }
        }

        if (list.isEmpty()) {
            return splitWordsEvenly(text, lineStartMs, lineEndMs);
        }

        for (int i = 0; i < list.size(); i++) {
            WordTime wt = list.get(i);
            long nextStart = (i + 1 < list.size()) ? list.get(i + 1).startMs : lineEndMs;
            list.set(i, new WordTime(wt.word, wt.startMs, Math.max(wt.startMs + 100L, nextStart)));
        }

        return list;
    }

    private static List<WordTime> splitWordsEvenly(String text, long startMs, long endMs) {
        List<WordTime> words = new ArrayList<>();
        String[] tokens = text.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return words;

        long duration = Math.max(100L, endMs - startMs);
        long wordDuration = duration / tokens.length;

        for (int i = 0; i < tokens.length; i++) {
            long wStart = startMs + (i * wordDuration);
            long wEnd = (i == tokens.length - 1) ? endMs : (wStart + wordDuration);
            words.add(new WordTime(tokens[i], wStart, wEnd));
        }
        return words;
    }
}
