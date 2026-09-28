package me.bombo.bomboaddons.features.spotify;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Interfaces with Spotify Desktop on Windows without requiring Spotify Developer tokens.
 * - Extracts current song title and artist from the Spotify window title.
 * - Controls playback (Play/Pause, Next, Previous) via Windows virtual media keys.
 * - Tracks playback duration in seconds.
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

    private static volatile boolean isSpotifyOpen = false;
    private static volatile boolean isPlaying = false;
    private static volatile String currentTrack = "";
    private static volatile String currentArtist = "";
    private static volatile int progressSeconds = 0;

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
        return progressSeconds;
    }

    public static String getFormattedTime() {
        int minutes = progressSeconds / 60;
        int seconds = progressSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public static void init() {
        if (running.compareAndSet(false, true)) {
            pollThread = new Thread(SpotifyManager::pollLoop, "BomboAddons-SpotifyPoller");
            pollThread.setDaemon(true);
            pollThread.start();
        }
    }

    private static void pollLoop() {
        while (running.get()) {
            try {
                updateSpotifyState();
                if (isPlaying) {
                    progressSeconds++;
                }
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable ignored) {
            }
        }
    }

    private static void updateSpotifyState() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }

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
                                    return false; // Found active song title window!
                                } else if (rawTitle[0].isEmpty()) {
                                    rawTitle[0] = title;
                                }
                            }
                        }
                    }
                }
                return true;
            }, null);
        } catch (Throwable ignored) {
        }

        isSpotifyOpen = foundSpotify[0];

        if (!isSpotifyOpen) {
            isPlaying = false;
            currentTrack = "";
            currentArtist = "";
            progressSeconds = 0;
            return;
        }

        String full = rawTitle[0];
        if (full.isEmpty() || full.equalsIgnoreCase("Spotify") || full.equalsIgnoreCase("Spotify Free") || full.equalsIgnoreCase("Spotify Premium")) {
            isPlaying = false;
        } else {
            isPlaying = true;
            String newArtist;
            String newTrack;
            int dashIndex = full.indexOf(" - ");
            if (dashIndex != -1) {
                newArtist = full.substring(0, dashIndex).trim();
                newTrack = full.substring(dashIndex + 3).trim();
            } else {
                newArtist = "Spotify";
                newTrack = full;
            }

            if (!newTrack.equals(currentTrack) || !newArtist.equals(currentArtist)) {
                currentArtist = newArtist;
                currentTrack = newTrack;
                progressSeconds = 0;
            }
        }
    }

    public static void playPause() {
        sendMediaKey(VK_MEDIA_PLAY_PAUSE);
        isPlaying = !isPlaying;
    }

    public static void nextTrack() {
        sendMediaKey(VK_MEDIA_NEXT_TRACK);
        progressSeconds = 0;
    }

    public static void prevTrack() {
        sendMediaKey(VK_MEDIA_PREV_TRACK);
        progressSeconds = 0;
    }

    private static void sendMediaKey(byte vkCode) {
        try {
            User32Extra.INSTANCE.keybd_event(vkCode, (byte) 0, 0, 0);
            User32Extra.INSTANCE.keybd_event(vkCode, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Throwable ignored) {
        }
    }
}
