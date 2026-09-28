package me.bombo.bomboaddons.features.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
 * Also retrieves album artwork for display in Spotify HUD.
 */
public class LyricsManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Pattern LINE_PATTERN = Pattern.compile("^\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.*)$");
    private static final Pattern WORD_PATTERN = Pattern.compile("<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)");

    public record WordTime(String word, long startMs, long endMs) {}

    public record LyricsLine(long startMs, long endMs, String text, List<WordTime> words, String backgroundText) {
        public LyricsLine(long startMs, long endMs, String text, List<WordTime> words) {
            this(startMs, endMs, text, words, null);
        }

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

    private static volatile Identifier albumArtTexture = null;
    private static volatile String lastArtworkUrl = "";

    public static Identifier getAlbumArtTexture() {
        return albumArtTexture;
    }

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
            albumArtTexture = null;
            lastArtworkUrl = "";
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

                // Always search iTunes for album artwork in background if needed
                fetchArtworkFromItunes(cleanTrack, cleanArtist);

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

    private static void fetchArtworkFromItunes(String cleanTrack, String cleanArtist) {
        try {
            String query = (cleanTrack + " " + cleanArtist).trim();
            String url = "https://itunes.apple.com/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&entity=song&limit=1";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (json.has("results") && json.getAsJsonArray("results").size() > 0) {
                    JsonObject first = json.getAsJsonArray("results").get(0).getAsJsonObject();
                    if (first.has("artworkUrl100")) {
                        String artUrl = first.get("artworkUrl100").getAsString();
                        // Request higher resolution 256x256
                        artUrl = artUrl.replace("100x100bb", "256x256bb");
                        downloadAndRegisterAlbumArt(artUrl);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void downloadAndRegisterAlbumArt(String artworkUrl) {
        if (artworkUrl == null || artworkUrl.equals(lastArtworkUrl)) return;
        lastArtworkUrl = artworkUrl;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(artworkUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                byte[] bytes = resp.body();
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        try (InputStream in = new ByteArrayInputStream(bytes)) {
                            NativeImage img = NativeImage.read(in);
                            if (img != null) {
                                DynamicTexture dynTex = new DynamicTexture(() -> "spotify_album_art", img);
                                Identifier id = Identifier.fromNamespaceAndPath("bomboaddons", "spotify_album_art");
                                mc.getTextureManager().register(id, dynTex);
                                albumArtTexture = id;
                            }
                        } catch (Throwable t) {
                            System.err.println("[BomboAddons] Failed to register album artwork: " + t.getMessage());
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean fetchPaxsenix(String cleanTrack, String cleanArtist, String cacheKey) {
        try {
            // First find Apple Music trackId using iTunes search
            String query = (cleanTrack + " " + cleanArtist).trim();
            String itunesUrl = "https://itunes.apple.com/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&entity=song&limit=1";
            HttpRequest itunesReq = HttpRequest.newBuilder()
                    .uri(URI.create(itunesUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> itunesResp = HTTP_CLIENT.send(itunesReq, HttpResponse.BodyHandlers.ofString());
            if (itunesResp.statusCode() != 200) return false;

            JsonObject itunesJson = JsonParser.parseString(itunesResp.body()).getAsJsonObject();
            if (!itunesJson.has("results") || itunesJson.getAsJsonArray("results").size() == 0) {
                return false;
            }

            JsonObject songObj = itunesJson.getAsJsonArray("results").get(0).getAsJsonObject();
            long trackId = songObj.get("trackId").getAsLong();
            if (songObj.has("artworkUrl100")) {
                downloadAndRegisterAlbumArt(songObj.get("artworkUrl100").getAsString().replace("100x100bb", "256x256bb"));
            }

            // Query Paxsenix Apple Music API
            String lyricsUrl = "https://lyrics.paxsenix.org/apple-music/lyrics?id=" + trackId;
            HttpRequest lyricsReq = HttpRequest.newBuilder()
                    .uri(URI.create(lyricsUrl))
                    .header("User-Agent", "ViviMusic/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> lyricsResp = HTTP_CLIENT.send(lyricsReq, HttpResponse.BodyHandlers.ofString());
            if (lyricsResp.statusCode() != 200) return false;

            lastRawLyrics = lyricsResp.body();
            JsonObject root = JsonParser.parseString(lyricsResp.body()).getAsJsonObject();

            if (root.has("content") && root.get("content").isJsonArray()) {
                JsonArray contentArr = root.getAsJsonArray("content");
                List<LyricsLine> lines = new ArrayList<>();

                for (JsonElement el : contentArr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject item = el.getAsJsonObject();

                    long startMs = item.has("timestamp") ? item.get("timestamp").getAsLong() : 0L;
                    long endMs = item.has("endtime") ? item.get("endtime").getAsLong() : startMs + 4000L;
                    boolean isBg = item.has("background") && item.get("background").getAsBoolean();

                    // Parse syllable words
                    List<WordTime> words = new ArrayList<>();
                    StringBuilder fullText = new StringBuilder();

                    if (item.has("text") && item.get("text").isJsonArray()) {
                        JsonArray textArr = item.getAsJsonArray("text");
                        StringBuilder currentWord = new StringBuilder();
                        Long currentWordStart = null;
                        Long currentWordEnd = null;

                        for (JsonElement tel : textArr) {
                            if (!tel.isJsonObject()) continue;
                            JsonObject tok = tel.getAsJsonObject();
                            String syllable = tok.has("text") ? tok.get("text").getAsString() : "";
                            long sylStart = tok.has("timestamp") ? tok.get("timestamp").getAsLong() : startMs;
                            long sylEnd = tok.has("endtime") ? tok.get("endtime").getAsLong() : endMs;
                            boolean part = tok.has("part") && tok.get("part").getAsBoolean();

                            if (currentWordStart == null) currentWordStart = sylStart;
                            currentWordEnd = sylEnd;
                            currentWord.append(syllable);

                            if (!part) {
                                String wStr = currentWord.toString().trim();
                                if (!wStr.isEmpty()) {
                                    words.add(new WordTime(wStr, currentWordStart, currentWordEnd));
                                    if (fullText.length() > 0) fullText.append(" ");
                                    fullText.append(wStr);
                                }
                                currentWord.setLength(0);
                                currentWordStart = null;
                            }
                        }

                        // Flush trailing word if any
                        if (currentWord.length() > 0) {
                            String wStr = currentWord.toString().trim();
                            if (!wStr.isEmpty()) {
                                words.add(new WordTime(wStr, currentWordStart != null ? currentWordStart : startMs, currentWordEnd != null ? currentWordEnd : endMs));
                                if (fullText.length() > 0) fullText.append(" ");
                                fullText.append(wStr);
                            }
                        }
                    }

                    // Background text parsing
                    String bgText = null;
                    if (item.has("backgroundText") && item.get("backgroundText").isJsonArray()) {
                        JsonArray bgArr = item.getAsJsonArray("backgroundText");
                        StringBuilder bgSb = new StringBuilder();
                        for (JsonElement bgEl : bgArr) {
                            if (bgEl.isJsonObject() && bgEl.getAsJsonObject().has("text")) {
                                if (bgSb.length() > 0) bgSb.append(" ");
                                bgSb.append(bgEl.getAsJsonObject().get("text").getAsString().trim());
                            }
                        }
                        if (bgSb.length() > 0) {
                            bgText = "(" + bgSb.toString() + ")";
                        }
                    }

                    String finalLineText = fullText.toString();
                    if (finalLineText.isEmpty() && item.has("plain")) {
                        finalLineText = item.get("plain").getAsString();
                    }

                    if (!finalLineText.isEmpty()) {
                        lines.add(new LyricsLine(startMs, endMs, finalLineText, words.isEmpty() ? null : words, bgText));
                    }
                }

                if (!lines.isEmpty()) {
                    lines.sort(Comparator.comparingLong(LyricsLine::startMs));
                    synchronized (LyricsManager.class) {
                        currentLines.clear();
                        currentLines.addAll(lines);
                        loading = false;
                        synced = true;
                        CACHE.put(cacheKey, new ArrayList<>(lines));
                        statusMessage = "Word-Synced Lyrics (PAXSENIX)";
                    }
                    return true;
                }
            }

            // Fallback to LRC format in Paxsenix
            if (root.has("lrc") && !root.get("lrc").isJsonNull()) {
                parseAndApplyLyrics(root.toString(), cacheKey, "PAXSENIX");
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

    private static boolean fetchLrcLib(String cleanTrack, String cleanArtist, String cacheKey) {
        try {
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
                    JsonArray arr = elem.getAsJsonArray();
                    JsonObject bestCandidate = null;
                    int bestScore = -1;

                    // Prioritize candidates: Word-for-Word (<mm:ss.xx>) > Line-Synced (syncedLyrics) > Plain
                    for (JsonElement itemEl : arr) {
                        if (!itemEl.isJsonObject()) continue;
                        JsonObject candidate = itemEl.getAsJsonObject();
                        String syncedLrc = candidate.has("syncedLyrics") && !candidate.get("syncedLyrics").isJsonNull()
                                ? candidate.get("syncedLyrics").getAsString() : "";
                        String plain = candidate.has("plainLyrics") && !candidate.get("plainLyrics").isJsonNull()
                                ? candidate.get("plainLyrics").getAsString() : "";

                        int score = 0;
                        if (!syncedLrc.isEmpty()) {
                            if (WORD_PATTERN.matcher(syncedLrc).find()) {
                                score = 3; // Word-level synchronized
                            } else {
                                score = 2; // Line-level synchronized
                            }
                        } else if (!plain.isEmpty()) {
                            score = 1; // Plain text
                        }

                        if (score > bestScore) {
                            bestScore = score;
                            bestCandidate = candidate;
                            if (score == 3) break; // Maximum priority found!
                        }
                    }

                    if (bestCandidate != null) {
                        parseAndApplyLyrics(bestCandidate.toString(), cacheKey, "LRCLIB");
                        return true;
                    }
                }
            }

            // Fallback direct get
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
                        parsed.add(new LyricsLine(estimatedTime, estimatedTime + 3000L, clean, null, null));
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
                lines.add(new LyricsLine(startMs, startMs + 4000L, content, null, null));
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

            if (words != null && !words.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int w = 0; w < words.size(); w++) {
                    if (w > 0) sb.append(" ");
                    sb.append(words.get(w).word());
                }
                cleanText = sb.toString();
            }

            result.add(new LyricsLine(cur.startMs, endMs, cleanText, words, null));
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
