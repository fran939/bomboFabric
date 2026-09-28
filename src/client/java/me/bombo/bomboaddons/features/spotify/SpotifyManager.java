package me.bombo.bomboaddons.features.spotify;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.ptr.IntByReference;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interfaces directly with Spotify Desktop on Windows without requiring Spotify Developer tokens.
 * - Queries Windows System Media Transport Controls (GSMTC) via a background PowerShell process
 *   for 100% exact playback position, total duration, track name, artist, and playing/paused status.
 *   Pausing preserves exact position without drifting or resetting to 0:00.
 * - Falls back to Win32 EnumWindows window-title inspection if GSMTC is unavailable.
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

    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static Thread pollThread = null;
    private static Process gsmtcProcess = null;

    private static volatile boolean isSpotifyOpen = false;
    private static volatile boolean isPlaying = false;
    private static volatile String currentTrack = "";
    private static volatile String currentArtist = "";
    private static volatile int progressSeconds = 0;
    private static volatile int durationSeconds = 0;
    private static volatile long lastStateUpdate = 0L;

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

    public static int getProgressSeconds() {
        if (!isPlaying || lastStateUpdate <= 0) {
            return progressSeconds;
        }
        int elapsed = progressSeconds + (int) ((System.currentTimeMillis() - lastStateUpdate) / 1000L);
        if (durationSeconds > 0) {
            elapsed = Math.min(elapsed, durationSeconds);
        }
        return Math.max(0, elapsed);
    }

    public static long getProgressMs() {
        if (!isPlaying || lastStateUpdate <= 0) {
            return progressSeconds * 1000L;
        }
        long elapsed = (progressSeconds * 1000L) + (System.currentTimeMillis() - lastStateUpdate);
        if (durationSeconds > 0) {
            elapsed = Math.min(elapsed, durationSeconds * 1000L);
        }
        return Math.max(0L, elapsed);
    }

    public static int getDurationSeconds() {
        return durationSeconds;
    }

    public static float getProgressRatio() {
        if (durationSeconds <= 0) return 0.0f;
        return Math.min(1.0f, Math.max(0.0f, (float) getProgressSeconds() / (float) durationSeconds));
    }

    public static String getFormattedTime() {
        int sec = getProgressSeconds();
        int minutes = sec / 60;
        int seconds = sec % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static String getFormattedDuration() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static void openTrackInSpotify() {
        String query = (currentTrack + " " + currentArtist).trim();
        openDesktopOrWeb(query);
    }

    public static void openArtistInSpotify() {
        String query = currentArtist.trim();
        openDesktopOrWeb(query);
    }

    private static void openDesktopOrWeb(String query) {
        if (query.isEmpty()) return;
        try {
            // Open via Spotify Desktop App protocol: spotify:search:<query>
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", "", "spotify:search:" + encoded);
            pb.start();
        } catch (Throwable t) {
            try {
                net.minecraft.util.Util.getPlatform().openUri(new URI("https://open.spotify.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8)));
            } catch (Throwable ignored) {}
        }
    }

    public static String getTrackUrl() {
        try {
            String query = (currentTrack + " " + currentArtist).trim();
            if (query.isEmpty()) query = "Spotify";
            return "https://open.spotify.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "https://open.spotify.com";
        }
    }

    public static String getArtistUrl() {
        try {
            String query = currentArtist.trim();
            if (query.isEmpty()) query = "Spotify";
            return "https://open.spotify.com/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8);
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
                                    int pos = parseSafeInt(parts[3]);
                                    int dur = parseSafeInt(parts[4]);
                                    String stat = parts[5].trim();

                                    currentTrack = title;
                                    currentArtist = artist;
                                    progressSeconds = pos;
                                    durationSeconds = dur;
                                    isPlaying = stat.equalsIgnoreCase("Playing");
                                    isSpotifyOpen = true;
                                    lastStateUpdate = System.currentTimeMillis();

                                    // Notify LyricsManager of track update
                                    LyricsManager.updateTrack(title, artist, pos);
                                }
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

    private static int parseSafeInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return 0;
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
                    + "$mgr = Await $asyncOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\r\n\r\n"
                    + "while ($true) {\r\n"
                    + "    try {\r\n"
                    + "        $session = $mgr.GetCurrentSession()\r\n"
                    + "        if ($session) {\r\n"
                    + "            $tl = $session.GetTimelineProperties()\r\n"
                    + "            $propsOp = $session.TryGetMediaPropertiesAsync()\r\n"
                    + "            $props = Await $propsOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\r\n"
                    + "            $pos = [math]::Floor($tl.Position.TotalSeconds)\r\n"
                    + "            $dur = [math]::Floor($tl.EndTime.TotalSeconds)\r\n"
                    + "            $stat = $session.GetPlaybackInfo().PlaybackStatus\r\n"
                    + "            [Console]::WriteLine(\"STATE|\" + $props.Title + \"|\" + $props.Artist + \"|\" + $pos + \"|\" + $dur + \"|\" + $stat)\r\n"
                    + "        } else {\r\n"
                    + "            [Console]::WriteLine(\"NONE\")\r\n"
                    + "        }\r\n"
                    + "    } catch {\r\n"
                    + "        [Console]::WriteLine(\"NONE\")\r\n"
                    + "    }\r\n"
                    + "    Start-Sleep -Milliseconds 500\r\n"
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
        } catch (Throwable ignored) {}

        isSpotifyOpen = foundSpotify[0];
        if (!isSpotifyOpen) {
            isPlaying = false;
            return;
        }

        String full = rawTitle[0];
        if (full.isEmpty() || full.equalsIgnoreCase("Spotify") || full.equalsIgnoreCase("Spotify Free") || full.equalsIgnoreCase("Spotify Premium")) {
            isPlaying = false;
        } else {
            isPlaying = true;
            int dashIndex = full.indexOf(" - ");
            if (dashIndex != -1) {
                currentArtist = full.substring(0, dashIndex).trim();
                currentTrack = full.substring(dashIndex + 3).trim();
            } else {
                currentArtist = "Spotify";
                currentTrack = full;
            }
            LyricsManager.updateTrack(currentTrack, currentArtist, progressSeconds);
        }
    }

    public static void playPause() {
        sendMediaKey(VK_MEDIA_PLAY_PAUSE);
        isPlaying = !isPlaying;
    }

    public static void nextTrack() {
        sendMediaKey(VK_MEDIA_NEXT_TRACK);
    }

    public static void prevTrack() {
        sendMediaKey(VK_MEDIA_PREV_TRACK);
    }

    private static void sendMediaKey(byte vkCode) {
        try {
            User32Extra.INSTANCE.keybd_event(vkCode, (byte) 0, 0, 0);
            User32Extra.INSTANCE.keybd_event(vkCode, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Throwable ignored) {}
    }
}
