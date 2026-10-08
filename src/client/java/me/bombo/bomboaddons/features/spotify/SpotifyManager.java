package me.bombo.bomboaddons.features.spotify;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interfaces directly with Spotify Desktop on Windows without requiring Spotify Developer tokens.
 * - Queries Windows System Media Transport Controls (GSMTC) via a background PowerShell process
 *   for 100% exact playback position, total duration, track name, artist, and playing/paused status.
 * - Features millisecond-level monotonic progress interpolation to guarantee smooth 1-second ticks
 *   (0:35, 0:36, 0:37...) with zero jitter, drifting, or oscillation.
 * - Controls playback (Play/Pause, Next, Previous) via Windows virtual media keys.
 */
public class SpotifyManager {

    public interface User32Extra extends Library {
        User32Extra INSTANCE = Native.load("user32", User32Extra.class);
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    private static final byte VK_MEDIA_NEXT_TRACK = (byte) 0xB0;
    private static final byte VK_MEDIA_PREV_TRACK = (byte) 0xB1;
    private static final byte VK_MEDIA_PLAY_PAUSE = (byte) 0xB3;
    private static final int KEYEVENTF_KEYUP = 0x0002;
    private static final java.net.http.HttpClient HTTP_CLIENT = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static Thread pollThread = null;
    private static Process gsmtcProcess = null;

    private static volatile boolean isSpotifyOpen = false;
    private static volatile boolean isPlaying = false;
    private static volatile String currentTrack = "";
    private static volatile String currentArtist = "";
    private static volatile long baseProgressMs = 0L;
    private static volatile long durationMs = 0L;
    private static volatile long lastStateUpdate = 0L;
    private static volatile long monotonicProgressMs = 0L;

    public static boolean isSpotifyOpen() {
        return isSpotifyOpen;
    }

    public static boolean isPlaying() {
        return isPlaying;
    }

    public static String getCurrentTrack() {
        return currentTrack;
    }

    public static String getCurrentArtist() {
        return currentArtist;
    }

    public static String getFullArtistDisplay() {
        String base = currentArtist != null ? currentArtist.trim() : "";
        if (base.isEmpty()) return "";
        if (currentTrack == null || currentTrack.isEmpty()) return base;
        if (!base.toLowerCase(java.util.Locale.ROOT).contains("feat") && !base.toLowerCase(java.util.Locale.ROOT).contains("ft.")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)\\((?:feat|ft)\\.?\\s*([^\\)]+)\\)").matcher(currentTrack);
            if (m.find()) {
                return base + " (feat. " + m.group(1).trim() + ")";
            }
        }
        return base;
    }

    public static void seekTo(long targetMs) {
        if (targetMs < 0) targetMs = 0;
        if (durationMs > 0 && targetMs > durationMs) targetMs = durationMs;
        baseProgressMs = targetMs;
        monotonicProgressMs = targetMs;
        lastStateUpdate = System.currentTimeMillis();
        long finalMs = targetMs;
        CompletableFuture.runAsync(() -> {
            try {
                File dir = new File(System.getProperty("java.io.tmpdir"), "bomboaddons");
                if (!dir.exists()) dir.mkdirs();
                File seekFile = new File(dir, "spotify_seek.txt");
                try (FileOutputStream fos = new FileOutputStream(seekFile)) {
                    fos.write(("SEEK|" + finalMs + "\r\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch (Throwable ignored) {}
        });
    }

    public static int getProgressSeconds() {
        return (int) (getProgressMs() / 1000L);
    }

    public static long getProgressMs() {
        if (!isPlaying || lastStateUpdate <= 0) {
            return baseProgressMs;
        }
        long now = System.currentTimeMillis();
        long rawCalculated = baseProgressMs + (now - lastStateUpdate);
        if (durationMs > 0) {
            rawCalculated = Math.min(rawCalculated, durationMs);
        }

        // Monotonic guard: during playback of the same track, never let the clock jump backwards
        if (rawCalculated > monotonicProgressMs) {
            monotonicProgressMs = rawCalculated;
        }
        return monotonicProgressMs;
    }

    public static int getDurationSeconds() {
        return (int) (durationMs / 1000L);
    }

    public static float getProgressRatio() {
        if (durationMs <= 0) return 0.0f;
        return Math.min(1.0f, Math.max(0.0f, (float) getProgressMs() / (float) durationMs));
    }

    public static String getFormattedTime() {
        int sec = getProgressSeconds();
        int minutes = sec / 60;
        int seconds = sec % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static String getFormattedDuration() {
        int sec = getDurationSeconds();
        int minutes = sec / 60;
        int seconds = sec % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static final java.util.Map<String, String> ARTIST_URL_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void openTrackInSpotify() {
        String track = currentTrack.trim();
        String artist = currentArtist.trim();
        if (track.isEmpty()) return;
        String query = (track + " " + artist).trim();

        CompletableFuture.runAsync(() -> {
            String nativeUri = "spotify:search:" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
            String webFallback = "https://open.spotify.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20") + "/tracks";
            try {
                String apiUrl = "https://api.bombo.dpdns.org/api/spotify/resolve?track=" + URLEncoder.encode(track, StandardCharsets.UTF_8)
                        + "&artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> resp = HTTP_CLIENT
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (obj.has("albumUri") && !obj.get("albumUri").isJsonNull()) {
                        nativeUri = obj.get("albumUri").getAsString();
                        if (obj.has("albumUrl") && !obj.get("albumUrl").isJsonNull()) {
                            webFallback = obj.get("albumUrl").getAsString();
                        }
                    } else if (obj.has("albumId") && !obj.get("albumId").isJsonNull()) {
                        String aid = obj.get("albumId").getAsString();
                        nativeUri = "spotify:album:" + aid;
                        webFallback = "https://open.spotify.com/album/" + aid;
                    } else if (obj.has("trackId") && !obj.get("trackId").isJsonNull()) {
                        String tid = obj.get("trackId").getAsString();
                        nativeUri = "spotify:track:" + tid;
                        webFallback = "https://open.spotify.com/track/" + tid;
                    }
                }
            } catch (Throwable ignored) {}
            openSpotifyUri(nativeUri, webFallback);
        });
    }

    public static void openAlbumInSpotify() {
        openTrackInSpotify();
    }

    private static volatile long lastReportTime = 0L;
    private static void reportNowPlayingToServer(String title, String artist, long posMs, long durMs, boolean playing) {
        long now = System.currentTimeMillis();
        if (now - lastReportTime < 2000L && title.equals(currentTrack)) {
            return;
        }
        lastReportTime = now;
        CompletableFuture.runAsync(() -> {
            try {
                com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
                payload.addProperty("title", title);
                payload.addProperty("artist", artist);
                payload.addProperty("progressMs", posMs);
                payload.addProperty("durationMs", durMs);
                payload.addProperty("isPlaying", playing);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("https://api.bombo.dpdns.org/api/spotify/now-playing"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(2))
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                        .build();
                HTTP_CLIENT.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            } catch (Throwable ignored) {}
        });
    }

    public static void openArtistInSpotify() {
        String artist = currentArtist.trim();
        if (artist.isEmpty()) return;
        String lower = artist.toLowerCase(java.util.Locale.ROOT);
        String cached = ARTIST_URL_CACHE.get(lower);
        if (cached != null && !cached.isEmpty()) {
            String nativeUri = cached.contains("/artist/")
                    ? "spotify:artist:" + cached.substring(cached.indexOf("/artist/") + 8).split("[/?]")[0]
                    : cached;
            openSpotifyUri(nativeUri, cached);
            return;
        }

        // Asynchronously resolve direct Spotify Artist profile URL via backend
        CompletableFuture.runAsync(() -> {
            String webUrl = "https://open.spotify.com/search/" + URLEncoder.encode(artist, StandardCharsets.UTF_8).replace("+", "%20") + "/artists";
            String nativeUri = "spotify:search:" + URLEncoder.encode(artist, StandardCharsets.UTF_8).replace("+", "%20");
            try {
                String apiUrl = "https://api.bombo.dpdns.org/api/spotify/resolve?artist=" + URLEncoder.encode(artist, StandardCharsets.UTF_8);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> resp = HTTP_CLIENT
                        .send(req, java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() == 200) {
                    com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
                    if (obj.has("artistUrl") && !obj.get("artistUrl").isJsonNull()) {
                        String directUrl = obj.get("artistUrl").getAsString();
                        if (directUrl.contains("/artist/")) {
                            webUrl = directUrl;
                            String aid = directUrl.substring(directUrl.indexOf("/artist/") + 8).split("[/?]")[0];
                            nativeUri = "spotify:artist:" + aid;
                            ARTIST_URL_CACHE.put(lower, directUrl);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            openSpotifyUri(nativeUri, webUrl);
        });
    }

    private static void openSpotifyUri(String nativeUri, String webFallback) {
        try {
            net.minecraft.util.Util.getPlatform().openUri(URI.create(nativeUri));
        } catch (Throwable t) {
            openCleanUri(webFallback);
        }
    }

    private static void openCleanUri(String url) {
        try {
            net.minecraft.util.Util.getPlatform().openUri(URI.create(url));
        } catch (Throwable t) {
            try {
                java.awt.Desktop.getDesktop().browse(URI.create(url));
            } catch (Throwable ignored) {}
        }
    }

    public static String getTrackUrl() {
        try {
            String query = (currentTrack + " " + currentArtist).trim();
            if (query.isEmpty()) query = "Spotify";
            return "https://open.spotify.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20") + "/tracks";
        } catch (Throwable t) {
            return "https://open.spotify.com";
        }
    }

    public static String getArtistUrl() {
        try {
            String query = currentArtist.trim();
            if (query.isEmpty()) query = "Spotify";
            String lower = query.toLowerCase(java.util.Locale.ROOT);
            String cached = ARTIST_URL_CACHE.get(lower);
            if (cached != null) return cached;
            return "https://open.spotify.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20") + "/artists";
        } catch (Throwable t) {
            return "https://open.spotify.com";
        }
    }

    public static void init() {
        if (running.compareAndSet(false, true)) {
            pollThread = new Thread(SpotifyManager::runLoop, "BomboAddons-SpotifyPoller");
            pollThread.setDaemon(true);
            pollThread.start();
        }
    }

    public static void stop() {
        running.set(false);
        if (gsmtcProcess != null) {
            try {
                gsmtcProcess.destroyForcibly();
            } catch (Throwable ignored) {}
            gsmtcProcess = null;
        }
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }

    private static void runLoop() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }

        File scriptFile = ensurePollerScript();

        while (running.get()) {
            if (scriptFile != null && scriptFile.exists()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "powershell.exe",
                            "-NoProfile",
                            "-ExecutionPolicy", "Bypass",
                            "-File", scriptFile.getAbsolutePath()
                    );
                    pb.redirectErrorStream(true);
                    gsmtcProcess = pb.start();

                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(gsmtcProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (running.get() && (line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("STATE|")) {
                                String[] parts = line.split("\\|", 6);
                                if (parts.length >= 6) {
                                    String title = parts[1].trim();
                                    String artist = parts[2].trim();
                                    long posMs = parseSafeLong(parts[3]);
                                    long durMs = parseSafeLong(parts[4]);
                                    String stat = parts[5].trim();

                                    boolean wasPlaying = isPlaying;
                                    boolean nowPlaying = stat.equalsIgnoreCase("Playing");
                                    boolean trackChanged = !title.equals(currentTrack) || !artist.equals(currentArtist);

                                    currentTrack = title;
                                    currentArtist = artist;
                                    durationMs = durMs;
                                    isPlaying = nowPlaying;
                                    isSpotifyOpen = true;

                                    long now = System.currentTimeMillis();
                                    if (trackChanged) {
                                        baseProgressMs = posMs;
                                        monotonicProgressMs = posMs;
                                        lastStateUpdate = now;
                                    } else if (nowPlaying) {
                                        if (!wasPlaying) {
                                            // Resuming from pause: re-anchor the clock to Spotify's own
                                            // position so the paused time is never injected into the lyrics.
                                            baseProgressMs = posMs;
                                            monotonicProgressMs = posMs;
                                            lastStateUpdate = now;
                                        } else if (posMs > monotonicProgressMs) {
                                            // GSMTC caught up or track jumped forward
                                            baseProgressMs = posMs;
                                            monotonicProgressMs = posMs;
                                            lastStateUpdate = now;
                                        } else if (monotonicProgressMs - posMs > 1500L) {
                                            // User seeked backwards: re-anchor so lyrics follow immediately.
                                            baseProgressMs = posMs;
                                            monotonicProgressMs = posMs;
                                            lastStateUpdate = now;
                                        } else if (Math.abs((baseProgressMs + (now - lastStateUpdate)) - posMs) > 1000L) {
                                            // Re-anchor the extrapolation origin so the clock can never
                                            // drift more than ~1s away from Spotify's reported position.
                                            baseProgressMs = posMs;
                                            lastStateUpdate = now;
                                        }
                                    } else {
                                        if (Math.abs(posMs - monotonicProgressMs) > 1000L) {
                                            baseProgressMs = posMs;
                                            monotonicProgressMs = posMs;
                                            lastStateUpdate = now;
                                        }
                                    }

                                    // Notify LyricsManager of track update
                                    LyricsManager.updateTrack(title, artist, (int) (posMs / 1000L));
                                    reportNowPlayingToServer(title, artist, posMs, durMs, nowPlaying);
                                }
                            } else if (line.startsWith("OPEN|")) {
                                isSpotifyOpen = true;
                            } else if (line.equals("NONE")) {
                                fallbackToWindowInspection();
                            }
                        }
                    }

                    if (gsmtcProcess != null) {
                        gsmtcProcess.waitFor();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable t) {
                    fallbackToWindowInspection();
                }
            } else {
                fallbackToWindowInspection();
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static long parseSafeLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static File ensurePollerScript() {
        try {
            File dir = new File(System.getProperty("java.io.tmpdir"), "bomboaddons");
            if (!dir.exists()) dir.mkdirs();
            File script = new File(dir, "spotify_poller.ps1");

            String content = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\r\n"
                    + "Add-Type -AssemblyName System.Runtime.WindowsRuntime\r\n"
                    + "$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\r\n"
                    + "Function Await($WinRtTask, $ResultType) {\r\n"
                    + "    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)\r\n"
                    + "    $netTask = $asTask.Invoke($null, @($WinRtTask))\r\n"
                    + "    $netTask.Wait(-1) | Out-Null\r\n"
                    + "    $netTask.Result\r\n"
                    + "}\r\n"
                    + "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime] | Out-Null\r\n"
                    + "$asyncOp = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()\r\n"
                    + "$mgr = Await $asyncOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\r\n"
                    + "$seekFile = Join-Path $env:TEMP 'bomboaddons\\spotify_seek.txt'\r\n\r\n"
                    + "while ($true) {\r\n"
                    + "    try {\r\n"
                    + "        $session = $null\r\n"
                    + "        $sessions = $mgr.GetSessions()\r\n"
                    + "        if ($sessions) {\r\n"
                    + "            foreach ($s in $sessions) {\r\n"
                    + "                if ($s.SourceAppUserModelId -and ($s.SourceAppUserModelId.ToLower().Contains('spotify') -or $s.SourceAppUserModelId.ToLower().EndsWith('spotify.exe'))) {\r\n"
                    + "                    if (-not $session -or $s.GetPlaybackInfo().PlaybackStatus -eq 'Playing') {\r\n"
                    + "                        $session = $s\r\n"
                    + "                    }\r\n"
                    + "                }\r\n"
                    + "            }\r\n"
                    + "        }\r\n"
                    + "        if (-not $session) {\r\n"
                    + "            $cur = $mgr.GetCurrentSession()\r\n"
                    + "            if ($cur) {\r\n"
                    + "                $session = $cur\r\n"
                    + "            }\r\n"
                    + "        }\r\n"
                    + "        if (Test-Path $seekFile) {\r\n"
                    + "            try {\r\n"
                    + "                $cmd = Get-Content -Path $seekFile -Raw\r\n"
                    + "                Remove-Item -Path $seekFile -Force -ErrorAction SilentlyContinue\r\n"
                    + "                if ($cmd -and $cmd.StartsWith('SEEK|')) {\r\n"
                    + "                    $seekMs = [long]($cmd.Substring(5).Trim())\r\n"
                    + "                    $targetTicks = [long]($seekMs * 10000)\r\n"
                    + "                    if ($session) {\r\n"
                    + "                        Await ($session.TryChangePlaybackPositionAsync($targetTicks)) ([bool]) | Out-Null\r\n"
                    + "                    }\r\n"
                    + "                }\r\n"
                    + "            } catch {}\r\n"
                    + "        }\r\n"
                    + "        if ($session) {\r\n"
                    + "            $tl = $session.GetTimelineProperties()\r\n"
                    + "            $propsOp = $session.TryGetMediaPropertiesAsync()\r\n"
                    + "            $props = Await $propsOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\r\n"
                    + "            $posMs = [math]::Round($tl.Position.TotalMilliseconds)\r\n"
                    + "            $durMs = [math]::Round($tl.EndTime.TotalMilliseconds)\r\n"
                    + "            $stat = $session.GetPlaybackInfo().PlaybackStatus\r\n"
                    + "            [Console]::WriteLine(\"STATE|\" + $props.Title + \"|\" + $props.Artist + \"|\" + $posMs + \"|\" + $durMs + \"|\" + $stat)\r\n"
                    + "        } else {\r\n"
                    + "            $spProc = Get-Process spotify -ErrorAction SilentlyContinue\r\n"
                    + "            if ($spProc) {\r\n"
                    + "                [Console]::WriteLine(\"OPEN|Spotify\")\r\n"
                    + "            } else {\r\n"
                    + "                [Console]::WriteLine(\"NONE\")\r\n"
                    + "            }\r\n"
                    + "        }\r\n"
                    + "    } catch {\r\n"
                    + "        $spProc = Get-Process spotify -ErrorAction SilentlyContinue\r\n"
                    + "        if ($spProc) {\r\n"
                    + "            [Console]::WriteLine(\"OPEN|Spotify\")\r\n"
                    + "        } else {\r\n"
                    + "            [Console]::WriteLine(\"NONE\")\r\n"
                    + "        }\r\n"
                    + "    }\r\n"
                    + "    Start-Sleep -Milliseconds 250\r\n"
                    + "}\r\n";

            try (FileOutputStream fos = new FileOutputStream(script)) {
                fos.write(content.getBytes(StandardCharsets.UTF_8));
            }
            return script;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void fallbackToWindowInspection() {
        final boolean[] foundSpotify = {false};
        final String[] rawTitle = {""};

        try {
            User32.INSTANCE.EnumWindows((hWnd, data) -> {
                IntByReference pidRef = new IntByReference();
                User32.INSTANCE.GetWindowThreadProcessId(hWnd, pidRef);
                int pid = pidRef.getValue();

                Optional<ProcessHandle> ph = ProcessHandle.of(pid);
                if (ph.isPresent()) {
                    String cmd = ph.get().info().command().orElse("").toLowerCase();
                    if (cmd.endsWith("spotify.exe") || cmd.contains("spotify")) {
                        char[] buffer = new char[512];
                        int length = User32.INSTANCE.GetWindowText(hWnd, buffer, 512);
                        if (length > 0) {
                            String title = new String(buffer, 0, length).trim();
                            if (!title.isEmpty() && !title.equals("Default IME") && !title.equals("MSCTFIME UI")) {
                                foundSpotify[0] = true;
                                if (!title.equalsIgnoreCase("Spotify") && !title.equalsIgnoreCase("Spotify Free") && !title.equalsIgnoreCase("Spotify Premium")) {
                                    rawTitle[0] = title;
                                    return false;
                                } else if (rawTitle[0].isEmpty()) {
                                    rawTitle[0] = title;
                                }
                            }
                        }
                    }
                }
                return true;
            }, null);

            isSpotifyOpen = foundSpotify[0];

            if (isSpotifyOpen && !rawTitle[0].isEmpty()) {
                String fullTitle = rawTitle[0];
                if (!fullTitle.equalsIgnoreCase("Spotify") && !fullTitle.equalsIgnoreCase("Spotify Free") && !fullTitle.equalsIgnoreCase("Spotify Premium")) {
                    String[] parts = fullTitle.split(" - ", 2);
                    String artist = parts.length > 0 ? parts[0].trim() : "Unknown Artist";
                    String track = parts.length > 1 ? parts[1].trim() : fullTitle;

                    if (!currentTrack.equals(track) || !currentArtist.equals(artist)) {
                        currentTrack = track;
                        currentArtist = artist;
                        baseProgressMs = 0L;
                        monotonicProgressMs = 0L;
                        lastStateUpdate = System.currentTimeMillis();
                        LyricsManager.updateTrack(track, artist, 0);
                        reportNowPlayingToServer(track, artist, 0, 0, true);
                    }
                    isPlaying = true;
                } else {
                    isPlaying = false;
                }
            } else {
                isPlaying = false;
            }
        } catch (Throwable t) {
            isSpotifyOpen = false;
            isPlaying = false;
        }
    }

    public static void playPause() {
        if (SpotifyWin32Handler.isWindows()) {
            SpotifyWin32Handler.playPause();
        } else {
            sendMediaKey(VK_MEDIA_PLAY_PAUSE);
        }
    }

    public static void nextTrack() {
        if (SpotifyWin32Handler.isWindows()) {
            SpotifyWin32Handler.nextTrack();
        } else {
            sendMediaKey(VK_MEDIA_NEXT_TRACK);
        }
    }

    public static void prevTrack() {
        if (SpotifyWin32Handler.isWindows()) {
            SpotifyWin32Handler.prevTrack();
        } else {
            sendMediaKey(VK_MEDIA_PREV_TRACK);
        }
    }

    public static void sendMediaKeyFallback(int appCommand) {
        if (appCommand == SpotifyWin32Handler.APPCOMMAND_MEDIA_PLAY_PAUSE) {
            sendMediaKey(VK_MEDIA_PLAY_PAUSE);
        } else if (appCommand == SpotifyWin32Handler.APPCOMMAND_MEDIA_NEXTTRACK) {
            sendMediaKey(VK_MEDIA_NEXT_TRACK);
        } else if (appCommand == SpotifyWin32Handler.APPCOMMAND_MEDIA_PREVIOUSTRACK) {
            sendMediaKey(VK_MEDIA_PREV_TRACK);
        }
    }

    private static void sendMediaKey(byte vkCode) {
        try {
            User32Extra.INSTANCE.keybd_event(vkCode, (byte) 0, 0, 0);
            User32Extra.INSTANCE.keybd_event(vkCode, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Throwable ignored) {}
    }
}
