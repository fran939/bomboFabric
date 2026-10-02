package me.bombo.bomboaddons.features.spotify;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.W32APIOptions;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Windows Native Win32 Spotify Controller via JNA PostMessage.
 * Allows toggling playback (Play/Pause, Next, Previous) without stealing focus,
 * without pausing other media (e.g. YouTube/browser), and without opening cmd/terminal windows.
 */
public class SpotifyWin32Handler {

    public interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer FindWindow(String lpClassName, String lpWindowName);
        Pointer FindWindowEx(Pointer hwndParent, Pointer hwndChildAfter, String lpszClass, String lpszWindow);
        boolean PostMessage(Pointer hWnd, int msg, Pointer wParam, long lParam);
        int GetClassName(Pointer hWnd, char[] lpClassName, int nMaxCount);
        int GetWindowText(Pointer hWnd, char[] lpString, int nMaxCount);
        boolean IsWindowVisible(Pointer hWnd);

        interface WNDENUMPROC extends com.sun.jna.Callback {
            boolean callback(Pointer hWnd, Pointer data);
        }

        boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer data);
    }

    public static final int WM_APPCOMMAND = 0x0319;
    public static final int APPCOMMAND_MEDIA_NEXTTRACK = 11;
    public static final int APPCOMMAND_MEDIA_PREVIOUSTRACK = 12;
    public static final int APPCOMMAND_MEDIA_PLAY_PAUSE = 14;

    private static final String SPOTIFY_CLASS_NAME = "Chrome_WidgetWin_0";

    public static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase(Locale.ROOT).contains("win");
    }

    public static void playPause() {
        sendCommand(APPCOMMAND_MEDIA_PLAY_PAUSE);
    }

    public static void nextTrack() {
        sendCommand(APPCOMMAND_MEDIA_NEXTTRACK);
    }

    public static void prevTrack() {
        sendCommand(APPCOMMAND_MEDIA_PREVIOUSTRACK);
    }

    /**
     * Sends the media command to Spotify's top-level window asynchronously.
     * Does not block Minecraft's client tick loop or UI rendering thread.
     */
    public static void sendCommand(int appCommand) {
        if (!isWindows()) return;

        CompletableFuture.runAsync(() -> {
            try {
                long lParam = (long) appCommand << 16;
                boolean[] sent = {false};

                // 1. Try FindWindow directly
                try {
                    Pointer hwnd = User32.INSTANCE.FindWindow(SPOTIFY_CLASS_NAME, null);
                    if (hwnd != null) {
                        User32.INSTANCE.PostMessage(hwnd, WM_APPCOMMAND, null, lParam);
                        sent[0] = true;
                    }
                } catch (Throwable ignored) {}

                // 2. Enumerate windows in case multiple Chrome_WidgetWin_0 exist (e.g. Spotify main window)
                try {
                    User32.INSTANCE.EnumWindows((hWnd, data) -> {
                        char[] clsBuf = new char[64];
                        User32.INSTANCE.GetClassName(hWnd, clsBuf, 64);
                        String cls = Native.toString(clsBuf);
                        if (SPOTIFY_CLASS_NAME.equals(cls)) {
                            // Verify if it has Spotify title or is visible
                            char[] titleBuf = new char[256];
                            User32.INSTANCE.GetWindowText(hWnd, titleBuf, 256);
                            String title = Native.toString(titleBuf);
                            if (title != null && (title.contains("Spotify") || title.contains(" - ") || User32.INSTANCE.IsWindowVisible(hWnd))) {
                                User32.INSTANCE.PostMessage(hWnd, WM_APPCOMMAND, null, lParam);
                                sent[0] = true;
                            }
                        }
                        return true;
                    }, null);
                } catch (Throwable ignored) {}

                // 3. If no window was found via JNA, fallback to legacy virtual key event as safe fallback
                if (!sent[0]) {
                    SpotifyManager.sendMediaKeyFallback(appCommand);
                }
            } catch (Throwable t) {
                SpotifyManager.sendMediaKeyFallback(appCommand);
            }
        });
    }
}
