package me.bombo.bomboaddons.features.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * Queries LRCLIB (as used by modern open-source clients like vivi-music).
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
    private static final List<LyricsLine> currentLines = new ArrayList<>();

    private static final Map<String, List<LyricsLine>> CACHE = new HashMap<>();

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
            return;
        }

        String cacheKey = (artist + " - " + track).toLowerCase(Locale.ROOT).trim();
        String currentKey = (activeArtist + " - " + activeTrack).toLowerCase(Locale.ROOT).trim();

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
                statusMessage = "Lyrics loaded";
            }
            return;
        }

        fetchLyricsAsync(track, artist, cacheKey);
    }

    private static void fetchLyricsAsync(String track, String artist, String cacheKey) {
        loading = true;
        statusMessage = "Searching lyrics for " + track + "...";

        Thread fetchThread = new Thread(() -> {
            try {
                // Strip common noise like "(feat. ...)", "[Official Video]", "- Remastered"
                String cleanTrack = cleanTitle(track);
                String cleanArtist = cleanTitle(artist);

                String url = "https://lrclib.net/api/get?track_name="
                        + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                        + "&artist_name=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "BomboAddons/1.0 (https://github.com/fran939/bombofabric)")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200) {
                    parseAndApplyLyrics(resp.body(), cacheKey);
                    return;
                }

                // If not found, try search endpoint
                String searchUrl = "https://lrclib.net/api/search?q="
                        + URLEncoder.encode(cleanTrack + " " + cleanArtist, StandardCharsets.UTF_8);

                HttpRequest searchReq = HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .header("User-Agent", "BomboAddons/1.0 (https://github.com/fran939/bombofabric)")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();

                HttpResponse<String> searchResp = HTTP_CLIENT.send(searchReq, HttpResponse.BodyHandlers.ofString());
                if (searchResp.statusCode() == 200) {
                    JsonElement elem = JsonParser.parseString(searchResp.body());
                    if (elem.isJsonArray() && elem.getAsJsonArray().size() > 0) {
                        JsonObject best = elem.getAsJsonArray().get(0).getAsJsonObject();
                        parseAndApplyLyrics(best.toString(), cacheKey);
                        return;
                    }
                }

                synchronized (LyricsManager.class) {
                    currentLines.clear();
                    loading = false;
                    synced = false;
                    statusMessage = "No lyrics found for " + track;
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

    private static void parseAndApplyLyrics(String jsonStr, String cacheKey) {
        try {
            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            boolean isInstrumental = obj.has("instrumental") && obj.get("instrumental").getAsBoolean();
            if (isInstrumental) {
                synchronized (LyricsManager.class) {
                    currentLines.clear();
                    loading = false;
                    synced = false;
                    statusMessage = "Instrumental Track";
                }
                return;
            }

            String syncedLyrics = obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()
                    ? obj.get("syncedLyrics").getAsString() : "";
            String plainLyrics = obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()
                    ? obj.get("plainLyrics").getAsString() : "";

            List<LyricsLine> parsed = new ArrayList<>();

            if (!syncedLyrics.isEmpty()) {
                parsed = parseLrc(syncedLyrics);
                synced = true;
            } else if (!plainLyrics.isEmpty()) {
                // Plain unsynced lyrics
                String[] lines = plainLyrics.split("\r?\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty()) {
                        parsed.add(new LyricsLine(i * 3000L, (i + 1) * 3000L, line, splitWordsEvenly(line, i * 3000L, (i + 1) * 3000L)));
                    }
                }
                synced = false;
            }

            synchronized (LyricsManager.class) {
                currentLines.clear();
                currentLines.addAll(parsed);
                loading = false;
                statusMessage = parsed.isEmpty() ? "No lyrics available" : "Lyrics loaded";
                CACHE.put(cacheKey, parsed);
            }
        } catch (Throwable t) {
            synchronized (LyricsManager.class) {
                loading = false;
                statusMessage = "Error parsing lyrics: " + t.getMessage();
            }
        }
    }

    public static List<LyricsLine> parseLrc(String lrc) {
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

        // Compute end times and word-by-word timestamps
        List<LyricsLine> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LyricsLine cur = lines.get(i);
            long endMs = (i + 1 < lines.size()) ? lines.get(i + 1).startMs : cur.startMs + 5000L;
            if (endMs <= cur.startMs) endMs = cur.startMs + 2000L;

            // Check if line contains rich sync <mm:ss.xx> words
            List<WordTime> words = parseRichSyncWords(cur.text, cur.startMs, endMs);
            String cleanText = cur.text.replaceAll("<\\d{1,2}:\\d{2}\\.\\d{2,3}>", "").trim();

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
            list.add(new WordTime(word, wordStart, wordStart + 500L));
        }

        if (list.isEmpty()) {
            // Distribute words evenly across the line duration for smooth karaoke progression
            return splitWordsEvenly(text, lineStartMs, lineEndMs);
        }

        // Adjust word end times
        for (int i = 0; i < list.size(); i++) {
            WordTime wt = list.get(i);
            long nextStart = (i + 1 < list.size()) ? list.get(i + 1).startMs : lineEndMs;
            list.set(i, new WordTime(wt.word, wt.startMs, Math.max(wt.startMs + 200L, nextStart)));
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

    private static String cleanTitle(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*\\(feat\\..*?\\)", "")
                .replaceAll("(?i)\\s*\\[.*?\\]", "")
                .replaceAll("(?i)\\s*-\\s*(Remastered|Live|Official Video|Audio|Radio Edit).*", "")
                .trim();
    }
}
