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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Manages fetching, parsing, and real-time synchronization of music lyrics.
 * Collects multi-provider candidates inspired by Vivi Music:
 * PAXSENIX (Apple Music Syllables), BETTERLYRICS, LRCLIB, KUGOU, UNISON, YOULYPLUS.
 * Supports word-by-word karaoke highlights, Spotify-style full line highlights,
 * unformatted/concatenated LRC splitting, custom lyrics editing, and candidate switching.
 */
public class LyricsManager {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Pattern LINE_PATTERN = Pattern.compile("^\\[(\\d{1,2}):(\\d{2})(?:\\.|:)(\\d{2,3})\\](.*)$");
    private static final Pattern WORD_PATTERN = Pattern.compile("[<\\(](\\d{1,2}):(\\d{2})(?:\\.|:)(\\d{2,3})[>\\)]\\s*([^<\\(\\r\\n]+)");
    private static final Pattern BETTER_WORD_PATTERN = Pattern.compile("<([^:>|]+):(\\d+(?:\\.\\d+)?):(\\d+(?:\\.\\d+)?)>");

    public record WordTime(String word, long startMs, long endMs) {}

    public record LyricsLine(long startMs, long endMs, String text, List<WordTime> words, String backgroundText, String agent) {
        public LyricsLine(long startMs, long endMs, String text, List<WordTime> words, String backgroundText) {
            this(startMs, endMs, text, words, backgroundText, (backgroundText != null && !backgroundText.isEmpty()) || (text != null && text.startsWith("(") && text.endsWith(")")) ? "v2" : "v1");
        }

        public LyricsLine(long startMs, long endMs, String text, List<WordTime> words) {
            this(startMs, endMs, text, words, null, "v1");
        }

        public boolean isWordActive(int wordIdx, long currentMs) {
            if (words == null || wordIdx < 0 || wordIdx >= words.size()) return false;
            WordTime wt = words.get(wordIdx);
            return currentMs >= wt.startMs;
        }
    }

    public record LyricCandidate(
            String id,
            String provider,
            String syncType,
            String preview,
            String rawData,
            List<LyricsLine> lines
    ) {}

    private static volatile String activeTrack = "";
    private static volatile String activeArtist = "";
    private static volatile boolean loading = false;
    private static volatile boolean synced = false;
    private static volatile String statusMessage = "No track playing";
    private static volatile String lastRawLyrics = "No lyrics loaded yet.";
    private static final List<LyricsLine> currentLines = new CopyOnWriteArrayList<>();
    public static final List<LyricCandidate> availableCandidates = new CopyOnWriteArrayList<>();
    public static volatile int selectedCandidateIndex = -1;

    private static final Map<String, List<LyricCandidate>> CACHE = new HashMap<>();
    private static final Map<String, String> USER_PREFERRED_CANDIDATES = new ConcurrentHashMap<>();
    private static volatile int dominantColor = 0xFF1ED760;

    private static final Path OFFSETS_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/lyrics_offsets.json");
    private static final Map<String, Integer> SONG_OFFSETS = new ConcurrentHashMap<>();
    private static volatile boolean offsetsLoaded = false;

    private static void loadOffsetsIfNeeded() {
        if (offsetsLoaded) return;
        offsetsLoaded = true;
        try {
            if (Files.exists(OFFSETS_FILE)) {
                String json = Files.readString(OFFSETS_FILE, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        SONG_OFFSETS.put(entry.getKey(), entry.getValue().getAsInt());
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void saveOffsetsAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(OFFSETS_FILE.getParent());
                JsonObject obj = new JsonObject();
                for (Map.Entry<String, Integer> entry : SONG_OFFSETS.entrySet()) {
                    obj.addProperty(entry.getKey(), entry.getValue());
                }
                Files.writeString(OFFSETS_FILE, obj.toString(), StandardCharsets.UTF_8);
            } catch (Throwable ignored) {}
        });
    }

    public static LyricCandidate getSelectedCandidate() {
        int idx = selectedCandidateIndex;
        if (idx >= 0 && idx < availableCandidates.size()) {
            return availableCandidates.get(idx);
        }
        return null;
    }

    public static void saveSongOffset(int offsetMs) {
        loadOffsetsIfNeeded();
        String songKey = (activeArtist + " - " + activeTrack).toLowerCase(Locale.ROOT).trim();
        if (songKey.isEmpty()) return;
        LyricCandidate curCand = getSelectedCandidate();
        String provKey = curCand != null ? curCand.provider().toLowerCase(Locale.ROOT).trim() : "default";
        SONG_OFFSETS.put(songKey + "::" + provKey, offsetMs);
        SONG_OFFSETS.put(songKey, offsetMs);
        saveOffsetsAsync();
    }

    public static void restoreSongOffset(LyricCandidate cand) {
        loadOffsetsIfNeeded();
        String songKey = (activeArtist + " - " + activeTrack).toLowerCase(Locale.ROOT).trim();
        if (songKey.isEmpty()) return;
        String provKey = cand != null ? cand.provider().toLowerCase(Locale.ROOT).trim() : "default";
        Integer offset = SONG_OFFSETS.get(songKey + "::" + provKey);
        if (offset == null) {
            offset = SONG_OFFSETS.get(songKey);
        }
        if (offset != null) {
            BomboConfig.get().lyricsOffsetMs = offset;
        } else {
            BomboConfig.get().lyricsOffsetMs = 0;
        }
    }

    public static int getDominantColor() {
        return dominantColor;
    }

    public static final String[] PROVIDERS = new String[]{"ALL (Vivi Music)", "PAXSENIX", "LRCLIB", "BETTERLYRICS", "KUGOU", "UNISON", "YOULYPLUS"};

    private static final java.util.concurrent.atomic.AtomicLong currentTrackEpoch = new java.util.concurrent.atomic.AtomicLong(0);
    public static volatile boolean lyricsDebugActive = false;
    private static java.io.PrintWriter debugWriter = null;
    private static int debugFrameCount = 0;

    public static synchronized boolean toggleDebug() {
        lyricsDebugActive = !lyricsDebugActive;
        if (lyricsDebugActive) {
            debugFrameCount = 0;
            try {
                java.io.File logFile = new java.io.File(net.minecraft.client.Minecraft.getInstance().gameDirectory, "bombo_lyrics_debug.log");
                debugWriter = new java.io.PrintWriter(new java.io.FileWriter(logFile, false));
                debugWriter.println("=== BomboAddons Lyrics Debug Log Started: " + java.time.LocalDateTime.now() + " ===");
                debugWriter.println("Track: " + SpotifyManager.getCurrentTrack() + " | Artist: " + SpotifyManager.getCurrentArtist());
                debugWriter.println("Provider: " + getProvider() + " | Total Lines: " + currentLines.size());
                debugWriter.println("--------------------------------------------------------------------------------");
                debugWriter.flush();
            } catch (Throwable t) {
                lyricsDebugActive = false;
            }
        } else {
            if (debugWriter != null) {
                debugWriter.println("=== BomboAddons Lyrics Debug Log Stopped (Total frames logged: " + debugFrameCount + ") ===");
                debugWriter.flush();
                debugWriter.close();
                debugWriter = null;
            }
        }
        return lyricsDebugActive;
    }

    public static synchronized void logFrame(long currentMs, int rawIdx, int activeIdx, String lineText, long lineStart, long lineEnd, int activeWords, int totalWords) {
        if (!lyricsDebugActive || debugWriter == null) return;
        debugFrameCount++;
        debugWriter.printf("[%05d | %s] ms=%d | rawIdx=%d | activeIdx=%d | line=[%dms..%dms] \"%s\" | words=%d/%d%n",
                debugFrameCount, java.time.LocalTime.now().toString(), currentMs, rawIdx, activeIdx, lineStart, lineEnd, lineText, activeWords, totalWords);
        if (debugFrameCount % 10 == 0) {
            debugWriter.flush();
        }
    }

    private static volatile Identifier albumArtTexture = null;
    private static volatile String lastArtworkUrl = "";

    public static Identifier getAlbumArtTexture() {
        return albumArtTexture;
    }

    public static List<LyricsLine> getLines() {
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

    public static boolean isWordSynced() {
        return currentLines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
    }

    public static String getRawLyrics() {
        if (!currentLines.isEmpty() && isWordSynced()) {
            return formatCleanLrc(currentLines);
        }
        return lastRawLyrics != null ? lastRawLyrics : "No raw lyrics available.";
    }

    public static String getProvider() {
        BomboConfig.Settings s = BomboConfig.get();
        return (s != null && s.lyricsProvider != null && !s.lyricsProvider.isEmpty()) ? s.lyricsProvider : "ALL (Vivi Music)";
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

    public static int getCurrentLineIndex(long currentMs) {
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
            availableCandidates.clear();
            selectedCandidateIndex = -1;
            statusMessage = "No track playing";
            lastRawLyrics = "No track playing.";
            albumArtTexture = null;
            lastArtworkUrl = "";
            return;
        }

        String cacheKey = (artist + " - " + track).toLowerCase(Locale.ROOT).trim();
        String currentKey = (activeArtist + " - " + activeTrack).toLowerCase(Locale.ROOT).trim();

        if (cacheKey.equals(currentKey)) {
            return;
        }

        activeTrack = track;
        activeArtist = artist;
        long epoch = currentTrackEpoch.incrementAndGet();
        albumArtTexture = null;
        lastArtworkUrl = "";

        // Re-verify artwork after 7 seconds to guarantee it updated even if metadata transitioned late
        java.util.concurrent.CompletableFuture.delayedExecutor(7, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            if (epoch == currentTrackEpoch.get() && albumArtTexture == null && !activeTrack.isEmpty()) {
                fetchArtwork(cleanTitle(activeTrack), cleanTitle(activeArtist), epoch);
            }
        });

        if (CACHE.containsKey(cacheKey)) {
            List<LyricCandidate> cached = CACHE.get(cacheKey);
            availableCandidates.clear();
            availableCandidates.addAll(cached);
            if (!availableCandidates.isEmpty()) {
                String preferredId = USER_PREFERRED_CANDIDATES.get(cacheKey);
                LyricCandidate toApply = availableCandidates.get(0);
                if (preferredId != null) {
                    for (LyricCandidate c : availableCandidates) {
                        if (c.id().equals(preferredId)) {
                            toApply = c;
                            break;
                        }
                    }
                }
                applyCandidate(toApply);
            }
            fetchArtwork(cleanTitle(track), cleanTitle(artist), epoch);
            return;
        }

        fetchLyricsAsync(track, artist, cacheKey, epoch);
    }

    public static void applyCandidate(LyricCandidate cand) {
        if (cand == null || cand.lines().isEmpty()) return;
        currentLines.clear();
        currentLines.addAll(cand.lines());
        lastRawLyrics = cand.rawData();
        synced = !"Plain".equalsIgnoreCase(cand.syncType());
        statusMessage = cand.syncType() + " (" + cand.provider() + ")";
        loading = false;

        String curKey = (activeArtist + " - " + activeTrack).toLowerCase(Locale.ROOT).trim();
        if (!curKey.isEmpty()) {
            USER_PREFERRED_CANDIDATES.put(curKey, cand.id());
            List<LyricCandidate> cached = CACHE.get(curKey);
            if (cached != null) {
                cached.removeIf(c -> c.id().equals(cand.id()));
                cached.add(0, cand);
            }
        }

        for (int i = 0; i < availableCandidates.size(); i++) {
            if (availableCandidates.get(i).id().equals(cand.id())) {
                selectedCandidateIndex = i;
                break;
            }
        }

        restoreSongOffset(cand);
    }

    public static void applyCustomLyrics(String customRaw) {
        if (customRaw == null || customRaw.trim().isEmpty()) return;
        List<LyricsLine> parsed = parseLrc(customRaw);
        boolean isSyn = true;
        if (parsed.isEmpty()) {
            String[] lines = customRaw.split("\r?\n");
            long t = 0L;
            for (String l : lines) {
                String cl = l.trim();
                if (!cl.isEmpty()) {
                    parsed.add(new LyricsLine(t, t + 3000L, cl, null, null));
                    t += 3000L;
                }
            }
            isSyn = false;
        }

        String preview = parsed.size() > 0 ? parsed.get(0).text() : "Custom Lyrics";
        boolean hasWords = parsed.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
        String syncType = hasWords ? "Word-Synced" : (isSyn ? "Line-Synced" : "Plain");
        LyricCandidate customCand = new LyricCandidate(
                "custom-" + System.currentTimeMillis(),
                "Custom",
                syncType,
                preview,
                hasWords ? formatCleanLrc(parsed) : customRaw,
                parsed
        );

        availableCandidates.add(0, customCand);
        applyCandidate(customCand);
    }

    private static void fetchLyricsAsync(String track, String artist, String cacheKey, long epoch) {
        loading = true;
        statusMessage = "Searching lyrics across providers...";
        availableCandidates.clear();
        selectedCandidateIndex = -1;

        new Thread(() -> {
            if (epoch != currentTrackEpoch.get()) return;
            String cleanTrack = cleanTitle(track);
            String cleanArtist = cleanTitle(artist);

            // Fetch Artwork via iTunes Search with epoch guard
            fetchArtwork(cleanTrack, cleanArtist, epoch);

            List<LyricCandidate> candidates = new ArrayList<>();

            // 1. Paxsenix (Apple Music Syllable Sync)
            try {
                fetchPaxsenixCandidates(cleanTrack, cleanArtist, candidates);
            } catch (Throwable ignored) {}

            // 2. LRCLIB Search (Returns list of candidates)
            try {
                fetchLrcLibCandidates(cleanTrack, cleanArtist, candidates);
            } catch (Throwable ignored) {}

            // 3. Kugou Lyrics Search
            try {
                fetchKugouCandidates(cleanTrack, cleanArtist, candidates);
            } catch (Throwable ignored) {}

            // 4. Unison, YouLyPlus & Musixmatch
            try {
                fetchUnisonCandidates(cleanTrack, cleanArtist, candidates);
            } catch (Throwable ignored) {}
            try {
                fetchYouLyPlusCandidates(cleanTrack, cleanArtist, candidates);
            } catch (Throwable ignored) {}
            try {
                fetchMusixmatchCandidates(cleanTrack, cleanArtist, candidates);
            } catch (Throwable ignored) {}

            if (epoch != currentTrackEpoch.get()) return;

            // Deduplicate by signature (provider + syncType + normalized preview) to eliminate identical duplicates
            List<LyricCandidate> unique = new ArrayList<>();
            Set<String> seenSigs = new HashSet<>();
            for (LyricCandidate c : candidates) {
                String norm = c.preview().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                String sig = c.provider().toLowerCase(Locale.ROOT) + ":" + c.syncType() + ":" + norm;
                if (!seenSigs.contains(sig)) {
                    seenSigs.add(sig);
                    unique.add(c);
                }
            }

            // Sort: Primary priority is sync quality: Word-Synced (3) > Line-Synced (2) > Plain/Unsynced (1) > None (0)
            String pref = BomboConfig.get().lyricsPreferredProvider != null ? BomboConfig.get().lyricsPreferredProvider.trim().toLowerCase(Locale.ROOT) : "auto";
            unique.sort((a, b) -> {
                int scoreA = a.syncType().contains("Word") ? 3 : (a.syncType().contains("Line") ? 2 : 1);
                int scoreB = b.syncType().contains("Word") ? 3 : (b.syncType().contains("Line") ? 2 : 1);
                if (scoreA != scoreB) {
                    return Integer.compare(scoreB, scoreA);
                }

                // Both have same quality: boost preferred provider if configured
                boolean aPref = !pref.equals("auto") && a.provider().toLowerCase(Locale.ROOT).contains(pref);
                boolean bPref = !pref.equals("auto") && b.provider().toLowerCase(Locale.ROOT).contains(pref);
                if (aPref != bPref) return aPref ? -1 : 1;

                return 0;
            });

            if (epoch != currentTrackEpoch.get()) return;

            availableCandidates.clear();
            availableCandidates.addAll(unique);

            if (!availableCandidates.isEmpty()) {
                CACHE.put(cacheKey, new ArrayList<>(availableCandidates));
                applyCandidate(availableCandidates.get(0));
            } else {
                currentLines.clear();
                loading = false;
                synced = false;
                statusMessage = "No lyrics found for " + cleanTrack;
                lastRawLyrics = "No lyrics found across providers.";
            }
        }, "Bombo-LyricsFetcher").start();
    }

    public static void refetchArtwork() {
        lastArtworkUrl = "";
        albumArtTexture = null;
        String track = SpotifyManager.getCurrentTrack();
        String artist = SpotifyManager.getCurrentArtist();
        if (track != null && !track.isEmpty()) {
            activeTrack = track;
            activeArtist = artist != null ? artist : "";
            long epoch = currentTrackEpoch.incrementAndGet();
            new Thread(() -> fetchArtwork(cleanTitle(track), cleanTitle(activeArtist), epoch), "Bombo-CoverRefetcher").start();
        }
    }

    private static void fetchArtwork(String cleanTrack, String cleanArtist, long epoch) {
        if (epoch != currentTrackEpoch.get()) return;
        boolean found = false;
        try {
            String query = (cleanTrack + " " + cleanArtist).trim();
            String itunesUrl = "https://itunes.apple.com/search?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&entity=song&limit=1";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(itunesUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (epoch != currentTrackEpoch.get()) return;
            if (resp.statusCode() == 200) {
                JsonObject itunesJson = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (itunesJson.has("results") && itunesJson.getAsJsonArray("results").size() > 0) {
                    JsonObject songObj = itunesJson.getAsJsonArray("results").get(0).getAsJsonObject();
                    if (songObj.has("artworkUrl100")) {
                        downloadAndRegisterAlbumArt(songObj.get("artworkUrl100").getAsString().replace("100x100bb", "256x256bb"), epoch);
                        found = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Fallback 1: Deezer API
        if (!found && epoch == currentTrackEpoch.get()) {
            try {
                String query = (cleanTrack + " " + cleanArtist).trim();
                String deezerUrl = "https://api.deezer.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&limit=1";
                HttpRequest dReq = HttpRequest.newBuilder()
                        .uri(URI.create(deezerUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();
                HttpResponse<String> dResp = HTTP_CLIENT.send(dReq, HttpResponse.BodyHandlers.ofString());
                if (epoch != currentTrackEpoch.get()) return;
                if (dResp.statusCode() == 200) {
                    JsonObject dJson = JsonParser.parseString(dResp.body()).getAsJsonObject();
                    if (dJson.has("data") && dJson.getAsJsonArray("data").size() > 0) {
                        JsonObject trackObj = dJson.getAsJsonArray("data").get(0).getAsJsonObject();
                        if (trackObj.has("album") && trackObj.get("album").isJsonObject()) {
                            JsonObject alb = trackObj.getAsJsonObject("album");
                            String coverUrl = alb.has("cover_big") && !alb.get("cover_big").isJsonNull()
                                    ? alb.get("cover_big").getAsString()
                                    : (alb.has("cover_medium") && !alb.get("cover_medium").isJsonNull() ? alb.get("cover_medium").getAsString() : null);
                            if (coverUrl != null && !coverUrl.isEmpty()) {
                                downloadAndRegisterAlbumArt(coverUrl, epoch);
                                found = true;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Fallback 2: iTunes with track title only
        if (!found && epoch == currentTrackEpoch.get() && !cleanTrack.isEmpty()) {
            try {
                String itunesUrl = "https://itunes.apple.com/search?term=" + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                        + "&entity=song&limit=1";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(itunesUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();
                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (epoch != currentTrackEpoch.get()) return;
                if (resp.statusCode() == 200) {
                    JsonObject itunesJson = JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (itunesJson.has("results") && itunesJson.getAsJsonArray("results").size() > 0) {
                        JsonObject songObj = itunesJson.getAsJsonArray("results").get(0).getAsJsonObject();
                        if (songObj.has("artworkUrl100")) {
                            downloadAndRegisterAlbumArt(songObj.get("artworkUrl100").getAsString().replace("100x100bb", "256x256bb"), epoch);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger albumArtCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    private static void downloadAndRegisterAlbumArt(String artworkUrl, long epoch) {
        if (epoch != currentTrackEpoch.get()) return;
        if (artworkUrl == null || artworkUrl.equals(lastArtworkUrl)) return;
        lastArtworkUrl = artworkUrl;

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(artworkUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BomboAddons")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (epoch != currentTrackEpoch.get()) return;
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                byte[] bytes = resp.body();
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        if (epoch != currentTrackEpoch.get()) return;
                        NativeImage img = null;
                        try (InputStream in = new ByteArrayInputStream(bytes)) {
                            img = NativeImage.read(in);
                        } catch (Throwable t) {
                            try (InputStream in2 = new ByteArrayInputStream(bytes)) {
                                java.awt.image.BufferedImage bimg = javax.imageio.ImageIO.read(in2);
                                if (bimg != null) {
                                    java.io.ByteArrayOutputStream pngOut = new java.io.ByteArrayOutputStream();
                                    javax.imageio.ImageIO.write(bimg, "png", pngOut);
                                    try (InputStream in3 = new ByteArrayInputStream(pngOut.toByteArray())) {
                                        img = NativeImage.read(in3);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }

                        if (img != null && epoch == currentTrackEpoch.get()) {
                            dominantColor = extractDominantColor(img);
                            int artNum = albumArtCounter.incrementAndGet();
                            DynamicTexture dynTex = new DynamicTexture(() -> "spotify_album_art_" + artNum, img);
                            dynTex.upload();
                            Identifier id = Identifier.fromNamespaceAndPath("bomboaddons", "spotify_album_art_" + artNum);
                            mc.getTextureManager().register(id, dynTex);
                            albumArtTexture = id;
                        }
                    });
                }
            }
        } catch (Throwable t) {
            System.err.println("[BomboAddons] Failed to download/register album art: " + t.getMessage());
        }
    }

    private static int extractDominantColor(NativeImage img) {
        if (img == null) return 0xFF1ED760;
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            long sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            int bestVibrant = 0xFF1ED760;
            int maxSat = 0;

            for (int y = 4; y < h - 4; y += Math.max(1, h / 16)) {
                for (int x = 4; x < w - 4; x += Math.max(1, w / 16)) {
                    int argb = img.getPixel(x, y);
                    int r = (argb) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = (argb >> 16) & 0xFF;
                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    int delta = max - min;
                    if (delta > 25 && max > 50 && min < 210) {
                        sumR += r;
                        sumG += g;
                        sumB += b;
                        count++;
                        if (delta > maxSat) {
                            maxSat = delta;
                            bestVibrant = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                    }
                }
            }
            if (maxSat > 30) {
                return bestVibrant;
            } else if (count > 0) {
                int avgR = (int) (sumR / count);
                int avgG = (int) (sumG / count);
                int avgB = (int) (sumB / count);
                return 0xFF000000 | (avgR << 16) | (avgG << 8) | avgB;
            }
        } catch (Throwable ignored) {}
        return 0xFF1ED760;
    }

    private static void fetchPaxsenixCandidates(String cleanTrack, String cleanArtist, List<LyricCandidate> out) {
        try {
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
            if (itunesResp.statusCode() != 200) return;

            JsonObject itunesJson = JsonParser.parseString(itunesResp.body()).getAsJsonObject();
            if (!itunesJson.has("results") || itunesJson.getAsJsonArray("results").size() == 0) return;

            JsonObject songObj = itunesJson.getAsJsonArray("results").get(0).getAsJsonObject();
            long trackId = songObj.get("trackId").getAsLong();

            String lyricsUrl = "https://lyrics.paxsenix.org/apple-music/lyrics?id=" + trackId;
            HttpRequest lyricsReq = HttpRequest.newBuilder()
                    .uri(URI.create(lyricsUrl))
                    .header("User-Agent", "ViviMusic/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> lyricsResp = HTTP_CLIENT.send(lyricsReq, HttpResponse.BodyHandlers.ofString());
            if (lyricsResp.statusCode() != 200) return;

            String body = lyricsResp.body();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();

            if (root.has("content") && root.get("content").isJsonArray()) {
                JsonArray contentArr = root.getAsJsonArray("content");
                List<LyricsLine> lines = new ArrayList<>();

                for (JsonElement el : contentArr) {
                    if (!el.isJsonObject()) continue;
                    JsonObject item = el.getAsJsonObject();

                    long startMs = item.has("timestamp") ? item.get("timestamp").getAsLong() : 0L;
                    long endMs = item.has("endtime") ? item.get("endtime").getAsLong() : startMs + 4000L;

                    List<WordTime> words = new ArrayList<>();
                    StringBuilder fullText = new StringBuilder();

                    if (item.has("text") && item.get("text").isJsonArray()) {
                        JsonArray textArr = item.getAsJsonArray("text");
                        StringBuilder curWord = new StringBuilder();
                        Long wStart = null;
                        Long wEnd = null;

                        for (JsonElement tel : textArr) {
                            if (!tel.isJsonObject()) continue;
                            JsonObject tok = tel.getAsJsonObject();
                            String syllable = tok.has("text") ? tok.get("text").getAsString() : "";
                            long sylStart = tok.has("timestamp") ? tok.get("timestamp").getAsLong() : startMs;
                            long sylEnd = tok.has("endtime") ? tok.get("endtime").getAsLong() : endMs;
                            boolean part = tok.has("part") && tok.get("part").getAsBoolean();

                            if (wStart == null) wStart = sylStart;
                            wEnd = sylEnd;
                            curWord.append(syllable);

                            if (!part) {
                                String wStr = curWord.toString().trim();
                                if (!wStr.isEmpty()) {
                                    words.add(new WordTime(wStr, wStart, wEnd));
                                    if (fullText.length() > 0) fullText.append(" ");
                                    fullText.append(wStr);
                                }
                                curWord.setLength(0);
                                wStart = null;
                            }
                        }
                        if (curWord.length() > 0) {
                            String wStr = curWord.toString().trim();
                            if (!wStr.isEmpty()) {
                                words.add(new WordTime(wStr, wStart != null ? wStart : startMs, wEnd != null ? wEnd : endMs));
                                if (fullText.length() > 0) fullText.append(" ");
                                fullText.append(wStr);
                            }
                        }
                    }

                    String bgText = null;
                    if (item.has("backgroundText") && item.get("backgroundText").isJsonArray()) {
                        StringBuilder bgSb = new StringBuilder();
                        for (JsonElement bgEl : item.getAsJsonArray("backgroundText")) {
                            if (bgEl.isJsonObject() && bgEl.getAsJsonObject().has("text")) {
                                if (bgSb.length() > 0) bgSb.append(" ");
                                bgSb.append(bgEl.getAsJsonObject().get("text").getAsString().trim());
                            }
                        }
                        if (bgSb.length() > 0) bgText = "(" + bgSb.toString() + ")";
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
                    boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                    String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                    out.add(new LyricCandidate(
                            "paxsenix-" + trackId,
                            "Paxsenix",
                            hasWords ? "Word-Synced" : "Line-Synced",
                            preview,
                            formatCleanLrc(lines),
                            lines
                    ));
                }
            }

            if (root.has("lrc") && !root.get("lrc").isJsonNull()) {
                String lrc = root.get("lrc").getAsString();
                List<LyricsLine> lines = parseLrc(lrc);
                if (!lines.isEmpty()) {
                    boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                    String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                    out.add(new LyricCandidate(
                            "paxsenix-lrc-" + trackId,
                            "Paxsenix",
                            hasWords ? "Word-Synced" : "Line-Synced",
                            preview,
                            formatCleanLrc(lines),
                            lines
                    ));
                }
            }
        } catch (Throwable ignored) {}
    }

    public static String formatCleanLrc(List<LyricsLine> lines) {
        if (lines == null || lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (LyricsLine l : lines) {
            long min = (l.startMs() / 1000) / 60;
            long sec = (l.startMs() / 1000) % 60;
            long ms = l.startMs() % 1000;
            sb.append(String.format("[%02d:%02d.%03d]", min, sec, ms));
            if (l.words() != null && !l.words().isEmpty()) {
                for (WordTime wt : l.words()) {
                    long wMin = (wt.startMs() / 1000) / 60;
                    long wSec = (wt.startMs() / 1000) % 60;
                    long wMs = wt.startMs() % 1000;
                    sb.append(String.format("<%02d:%02d.%03d>%s ", wMin, wSec, wMs, wt.word()));
                }
                if (l.backgroundText() != null && !l.backgroundText().isEmpty()) {
                    sb.append(l.backgroundText());
                }
            } else {
                sb.append(l.text());
                if (l.backgroundText() != null && !l.backgroundText().isEmpty()) {
                    sb.append(" ").append(l.backgroundText());
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static void fetchLrcLibCandidates(String cleanTrack, String cleanArtist, List<LyricCandidate> out) {
        // 1. Exact match attempt via /api/get
        try {
            String exactUrl = "https://lrclib.net/api/get?track_name="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&artist_name=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);
            HttpRequest exactReq = HttpRequest.newBuilder()
                    .uri(URI.create(exactUrl))
                    .header("User-Agent", "BomboAddons/1.0 (https://github.com/fran939/bombofabric)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> exactResp = HTTP_CLIENT.send(exactReq, HttpResponse.BodyHandlers.ofString());
            if (exactResp.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(exactResp.body()).getAsJsonObject();
                long id = obj.has("id") ? obj.get("id").getAsLong() : System.currentTimeMillis();
                String synced = obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull() ? obj.get("syncedLyrics").getAsString() : "";
                if (!synced.isEmpty()) {
                    List<LyricsLine> lines = parseLrc(synced);
                    if (!lines.isEmpty()) {
                        boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                        String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                        out.add(new LyricCandidate("lrclib-exact-" + id, "LrcLib", hasWords ? "Word-Synced" : "Line-Synced", preview, synced, lines));
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Search query attempt
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
                if (elem.isJsonArray()) {
                    int syncedCount = 0;
                    int plainCount = 0;
                    Set<String> seenPreviews = new HashSet<>();
                    for (JsonElement itemEl : elem.getAsJsonArray()) {
                        if (!itemEl.isJsonObject()) continue;
                        JsonObject obj = itemEl.getAsJsonObject();
                        long id = obj.has("id") ? obj.get("id").getAsLong() : System.currentTimeMillis();

                        String synced = obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()
                                ? obj.get("syncedLyrics").getAsString() : "";
                        String plain = obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()
                                ? obj.get("plainLyrics").getAsString() : "";

                        if (!synced.isEmpty() && syncedCount < 3) {
                            List<LyricsLine> lines = parseLrc(synced);
                            if (!lines.isEmpty()) {
                                String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                                String norm = preview.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                                if (!seenPreviews.contains(norm)) {
                                    seenPreviews.add(norm);
                                    boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                                    out.add(new LyricCandidate(
                                            "lrclib-" + id,
                                            "LrcLib",
                                            hasWords ? "Word-Synced" : "Line-Synced",
                                            preview,
                                            synced,
                                            lines
                                    ));
                                    syncedCount++;
                                }
                            }
                        } else if (!plain.isEmpty() && plainCount < 1) {
                            List<LyricsLine> lines = new ArrayList<>();
                            long t = 0L;
                            for (String l : plain.split("\r?\n")) {
                                String cl = l.trim();
                                if (!cl.isEmpty()) {
                                    lines.add(new LyricsLine(t, t + 3000L, cl, null, null));
                                    t += 3000L;
                                }
                            }
                            if (!lines.isEmpty()) {
                                String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                                String norm = preview.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                                if (!seenPreviews.contains(norm)) {
                                    seenPreviews.add(norm);
                                    out.add(new LyricCandidate(
                                            "lrclib-plain-" + id,
                                            "LrcLib",
                                            "Plain",
                                            preview,
                                            plain,
                                            lines
                                    ));
                                    plainCount++;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void fetchKugouCandidates(String cleanTrack, String cleanArtist, List<LyricCandidate> out) {
        try {
            String query = (cleanTrack + " " + cleanArtist).trim();
            String searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("candidates") && root.get("candidates").isJsonArray()) {
                    JsonArray arr = root.getAsJsonArray("candidates");
                    for (int i = 0; i < Math.min(3, arr.size()); i++) {
                        JsonObject cand = arr.get(i).getAsJsonObject();
                        String id = cand.get("id").getAsString();
                        String accesskey = cand.get("accesskey").getAsString();

                        String dlUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=" + id + "&accesskey=" + accesskey + "&fmt=lrc&charset=utf8";
                        HttpRequest dlReq = HttpRequest.newBuilder().uri(URI.create(dlUrl)).timeout(Duration.ofSeconds(3)).GET().build();
                        HttpResponse<String> dlResp = HTTP_CLIENT.send(dlReq, HttpResponse.BodyHandlers.ofString());
                        if (dlResp.statusCode() == 200) {
                            JsonObject dlObj = JsonParser.parseString(dlResp.body()).getAsJsonObject();
                            if (dlObj.has("content")) {
                                byte[] decoded = Base64.getDecoder().decode(dlObj.get("content").getAsString());
                                String lrcText = new String(decoded, StandardCharsets.UTF_8);
                                List<LyricsLine> lines = parseLrc(lrcText);
                                if (!lines.isEmpty()) {
                                    boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                                    String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                                    out.add(new LyricCandidate(
                                            "kugou-" + id,
                                            "Kugou",
                                            hasWords ? "Word-Synced" : "Line-Synced",
                                            preview,
                                            lrcText,
                                            lines
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void fetchUnisonCandidates(String cleanTrack, String cleanArtist, List<LyricCandidate> out) {
        try {
            String url = "https://unison.boidu.dev/lyrics?song="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&artist=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "ViviMusic/1.0").timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                List<LyricsLine> lines = parseLrc(resp.body());
                if (!lines.isEmpty()) {
                    boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                    String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                    out.add(new LyricCandidate("unison-" + System.currentTimeMillis(), "Unison", hasWords ? "Word-Synced" : "Line-Synced", preview, resp.body(), lines));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void fetchYouLyPlusCandidates(String cleanTrack, String cleanArtist, List<LyricCandidate> out) {
        try {
            String url = "https://lyricsplus.binimum.org/v2/lyrics/get?title="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&artist=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "ViviMusic/1.0").timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body().trim();
                if (body.startsWith("{")) {
                    JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                    JsonArray lyricsArr = null;
                    if (root.has("data") && root.get("data").isJsonObject() && root.getAsJsonObject("data").has("lyrics")) {
                        lyricsArr = root.getAsJsonObject("data").getAsJsonArray("lyrics");
                    } else if (root.has("lyrics") && root.get("lyrics").isJsonArray()) {
                        lyricsArr = root.getAsJsonArray("lyrics");
                    }

                    if (lyricsArr != null && lyricsArr.size() > 0) {
                        List<LyricsLine> lines = new ArrayList<>();
                        for (JsonElement el : lyricsArr) {
                            if (!el.isJsonObject()) continue;
                            JsonObject lineObj = el.getAsJsonObject();
                            long time = lineObj.has("time") ? lineObj.get("time").getAsLong() : 0L;
                            long dur = lineObj.has("duration") ? lineObj.get("duration").getAsLong() : 3000L;
                            long endTime = time + dur;
                            String text = lineObj.has("text") ? lineObj.get("text").getAsString().trim() : "";
                            List<WordTime> words = new ArrayList<>();
                            if (lineObj.has("syllabus") && lineObj.get("syllabus").isJsonArray()) {
                                StringBuilder curWord = new StringBuilder();
                                long curWordStart = -1L;
                                long curWordEnd = -1L;

                                for (JsonElement sel : lineObj.getAsJsonArray("syllabus")) {
                                    if (!sel.isJsonObject()) continue;
                                    JsonObject so = sel.getAsJsonObject();
                                    String stext = so.has("text") ? so.get("text").getAsString() : "";
                                    long stime = so.has("time") ? so.get("time").getAsLong() : time;
                                    long sdur = so.has("duration") ? so.get("duration").getAsLong() : 200L;

                                    if (stext.equals(" ") || stext.equals("\t")) {
                                        if (curWord.length() > 0) {
                                            words.add(new WordTime(curWord.toString(), curWordStart, curWordEnd));
                                            curWord.setLength(0);
                                            curWordStart = -1L;
                                            curWordEnd = -1L;
                                        }
                                    } else {
                                        if (curWordStart < 0) curWordStart = stime;
                                        curWordEnd = stime + sdur;
                                        curWord.append(stext.trim());
                                        if (stext.endsWith(" ")) {
                                            words.add(new WordTime(curWord.toString(), curWordStart, curWordEnd));
                                            curWord.setLength(0);
                                            curWordStart = -1L;
                                            curWordEnd = -1L;
                                        }
                                    }
                                }
                                if (curWord.length() > 0) {
                                    words.add(new WordTime(curWord.toString(), curWordStart, curWordEnd));
                                }
                            }
                            if (!text.isEmpty()) {
                                lines.add(new LyricsLine(time, endTime, text, words.isEmpty() ? null : words, null));
                            }
                        }
                        if (!lines.isEmpty()) {
                            lines.sort(Comparator.comparingLong(LyricsLine::startMs));
                            boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                            String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                            out.add(new LyricCandidate("youly-" + System.currentTimeMillis(), "YouLyPlus", hasWords ? "Word-Synced" : "Line-Synced", preview, formatCleanLrc(lines), lines));
                            return;
                        }
                    }
                }

                List<LyricsLine> lines = parseLrc(body);
                if (!lines.isEmpty()) {
                    boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                    String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                    out.add(new LyricCandidate("youly-" + System.currentTimeMillis(), "YouLyPlus", hasWords ? "Word-Synced" : "Line-Synced", preview, body, lines));
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String musixmatchToken = null;

    private static String mxSign(String url) {
        try {
            String norm = url.replace("%20", "+").replace(" ", "+");
            java.time.LocalDate now = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
            String dateStr = String.format("%04d%02d%02d", now.getYear(), now.getMonthValue(), now.getDayOfMonth());
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            char[] k1 = {'b','3','d','c','8','7','8','8'};
            char[] k2 = {'2','9','9','f','5','8','0','6'};
            char[] k3 = {'a','7','0','a','6','a','2','0'};
            char[] k4 = {'a','0','c','b','0','f','f','c'};
            byte[] keyBytes = (new String(k1) + new String(k2) + new String(k3) + new String(k4)).getBytes(StandardCharsets.UTF_8);
            mac.init(new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal((norm + dateStr).getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(hmacBytes);
            return norm + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8) + "&signature_protocol=sha256";
        } catch (Throwable t) {
            return url;
        }
    }

    private static synchronized String getMusixmatchToken() {
        if (musixmatchToken != null) return musixmatchToken;
        try {
            String tokenUrl = mxSign("https://apic.musixmatch.com/ws/1.1/token.get?app_id=mobile-app-v1.0&guid=" + UUID.randomUUID() + "&format=json");
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(tokenUrl)).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
                if (root.has("message") && root.getAsJsonObject("message").has("body")) {
                    JsonObject body = root.getAsJsonObject("message").getAsJsonObject("body");
                    if (body.has("user_token")) {
                        musixmatchToken = body.get("user_token").getAsString();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return musixmatchToken;
    }

    private static void fetchMusixmatchCandidates(String cleanTrack, String cleanArtist, List<LyricCandidate> out) {
        try {
            String token = getMusixmatchToken();
            if (token == null || token.isEmpty()) return;

            String searchUrl = mxSign("https://apic.musixmatch.com/ws/1.1/track.search?app_id=mobile-app-v1.0&format=json&q_track="
                    + URLEncoder.encode(cleanTrack, StandardCharsets.UTF_8)
                    + "&q_artist=" + URLEncoder.encode(cleanArtist, StandardCharsets.UTF_8)
                    + "&f_has_lyrics=true&page_size=3&usertoken=" + token);

            HttpRequest sReq = HttpRequest.newBuilder().uri(URI.create(searchUrl)).timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> sResp = HTTP_CLIENT.send(sReq, HttpResponse.BodyHandlers.ofString());
            if (sResp.statusCode() != 200) return;

            JsonObject sRoot = JsonParser.parseString(sResp.body()).getAsJsonObject();
            if (!sRoot.has("message") || !sRoot.getAsJsonObject("message").has("body")) return;
            JsonObject sBody = sRoot.getAsJsonObject("message").getAsJsonObject("body");
            if (!sBody.has("track_list") || !sBody.get("track_list").isJsonArray()) return;

            JsonArray trackList = sBody.getAsJsonArray("track_list");
            for (JsonElement tel : trackList) {
                if (!tel.isJsonObject() || !tel.getAsJsonObject().has("track")) continue;
                JsonObject trackObj = tel.getAsJsonObject().getAsJsonObject("track");
                long trackId = trackObj.get("track_id").getAsLong();
                boolean hasSubtitles = trackObj.has("has_subtitles") && trackObj.get("has_subtitles").getAsInt() == 1;

                if (hasSubtitles) {
                    String subUrl = mxSign("https://apic.musixmatch.com/ws/1.1/track.subtitle.get?app_id=mobile-app-v1.0&format=json&track_id=" + trackId + "&usertoken=" + token);
                    HttpRequest subReq = HttpRequest.newBuilder().uri(URI.create(subUrl)).timeout(Duration.ofSeconds(3)).GET().build();
                    HttpResponse<String> subResp = HTTP_CLIENT.send(subReq, HttpResponse.BodyHandlers.ofString());
                    if (subResp.statusCode() == 200) {
                        JsonObject subRoot = JsonParser.parseString(subResp.body()).getAsJsonObject();
                        JsonObject subBody = subRoot.getAsJsonObject("message").getAsJsonObject("body");
                        if (subBody.has("subtitle") && subBody.getAsJsonObject("subtitle").has("subtitle_body")) {
                            String lrc = subBody.getAsJsonObject("subtitle").get("subtitle_body").getAsString();
                            List<LyricsLine> lines = parseLrc(lrc);
                            if (!lines.isEmpty()) {
                                boolean hasWords = lines.stream().anyMatch(l -> l.words() != null && !l.words().isEmpty());
                                String preview = lines.get(0).text() + (lines.size() > 1 ? " | " + lines.get(1).text() : "");
                                out.add(new LyricCandidate("musixmatch-" + trackId, "Musixmatch", hasWords ? "Word-Synced" : "Line-Synced", preview, lrc, lines));
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static List<LyricsLine> parseLrc(String lrc) {
        List<LyricsLine> lines = new ArrayList<>();
        if (lrc == null || lrc.trim().isEmpty()) return lines;

        // CRUCIAL: Split by lookahead for timestamps OR newlines to handle unformatted concatenated LRCs!
        String[] rawLines = lrc.split("(?=\\[\\d{1,2}:\\d{2})|\\r?\\n");

        for (String raw : rawLines) {
            raw = raw.trim();
            if (raw.isEmpty() || raw.startsWith("[offset:") || raw.startsWith("[ti:") || raw.startsWith("[ar:") || raw.startsWith("[al:")) {
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
                // Strip metadata tags like {agent:v1}
                content = content.replaceAll("\\{[^}]+\\}", "").trim();
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
            String cleanText = cur.text
                    .replaceAll("<\\d{1,2}:\\d{2}\\.\\d{2,3}>", "")
                    .replaceAll("<[^>]+>", "")
                    .trim();

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
        List<WordTime> list = new ArrayList<>();

        // Format 1: <00:11.586>Word
        Matcher wm = WORD_PATTERN.matcher(text);
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

        // Format 2: BetterLyrics / Musixmatch: <word:start:end|word:start:end>
        if (list.isEmpty()) {
            Matcher bm = BETTER_WORD_PATTERN.matcher(text);
            while (bm.find()) {
                String word = bm.group(1).trim();
                try {
                    long wStart = (long) (Double.parseDouble(bm.group(2)) * 1000.0);
                    long wEnd = (long) (Double.parseDouble(bm.group(3)) * 1000.0);
                    if (!word.isEmpty()) {
                        list.add(new WordTime(word, wStart, Math.max(wStart + 100L, wEnd)));
                    }
                } catch (Throwable ignored) {}
            }
        }

        // CRITICAL FIX: If there are NO real word-for-word tags, return NULL!
        // Do NOT call splitWordsEvenly! This allows Spotify-style full line highlighting!
        if (list.isEmpty()) {
            return null;
        }

        // Calculate endMs for each word based on next word's startMs
        for (int i = 0; i < list.size(); i++) {
            WordTime wt = list.get(i);
            long nextStart = (i + 1 < list.size()) ? list.get(i + 1).startMs : lineEndMs;
            list.set(i, new WordTime(wt.word, wt.startMs, Math.max(wt.startMs + 50L, nextStart)));
        }

        return list;
    }

    private static String cleanTitle(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)\\s*\\(feat\\..*?\\)", "")
                .replaceAll("(?i)\\s*\\[official.*?\\]", "")
                .replaceAll("(?i)\\s*- remastered.*", "")
                .trim();
    }
}
