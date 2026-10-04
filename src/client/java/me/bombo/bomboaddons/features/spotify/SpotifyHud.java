package me.bombo.bomboaddons.features.spotify;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.Objects;
import me.bombo.bomboaddons.features.spotify.LyricsManager.LyricsLine;

/**
 * Renders an interactive Spotify HUD card on screen.
 * Displays official Spotify logo, song name, artist, progress bar, and clickable controls (|◀ ⏸ ▶|).
 * Remains visible during chat and inventory/container screens.
 */
public class SpotifyHud {

    public static final int HUD_WIDTH = 220;
    public static final int HUD_HEIGHT = 34;

    private static final Identifier SPOTIFY_ICON = Identifier.fromNamespaceAndPath("bomboaddons", "textures/gui/spotify.png");

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "spotify_hud"), SpotifyHud::render);
        SpotifyManager.init();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen) && !(mc.gui.screen() instanceof AbstractContainerScreen)) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.spotifyHudEnabled) return;
        if (!SpotifyManager.isSpotifyOpen() && !s.spotifyHudAlwaysShow) return;

        try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("Spotify: HUD Render")) {
            drawHud(g, s.spotifyHudX, s.spotifyHudY, s.spotifyHudScale, false);
        }
    }

    public static int getHudWidth() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.spotifyHudLyricsMode) {
            return 250;
        }
        return HUD_WIDTH;
    }

    public static int getHudHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.spotifyHudLyricsMode) {
            int extra = Math.max(0, s.spotifyHudLyricsNextLines - 1);
            return HUD_HEIGHT + extra * 11;
        }
        return HUD_HEIGHT;
    }

    private static String lastBgRaw = null;
    private static int cachedBg = 0xEE1E1324;
    private static String lastBorderRaw = null;
    private static int cachedBorder = 0x3300A4DC;
    private static String lastTitleRaw = null;
    private static int cachedTitle = 0xFFFFFFFF;
    private static String lastArtistRaw = null;
    private static int cachedArtist = 0xFFA098AA;
    private static String lastAccentRaw = null;
    private static int cachedAccent = 0xFF00A4DC;
    private static String lastProgRaw = null;
    private static int cachedProg = 0x33333344;

    private static String lastRawTrack = null;
    private static String cachedTrackDisplay = "";
    private static String lastRawArtist = null;
    private static String cachedArtistDisplay = "";

    public static void drawHud(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean isDummy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font font = mc.font;
        if (font == null) return;

        if (scale <= 0.0f) scale = 1.0f;

        g.pose().pushMatrix();
        g.pose().translate((float) baseX, (float) baseY);
        g.pose().scale(scale, scale);

        boolean playing = isDummy || SpotifyManager.isPlaying();
        boolean open = isDummy || SpotifyManager.isSpotifyOpen();

        String track = isDummy ? "CALENTÓN" : (open ? (SpotifyManager.getCurrentTrack().isEmpty() ? "No Track" : SpotifyManager.getCurrentTrack()) : "Spotify Closed");
        String artist = isDummy ? "Mora (feat. Feid)" : (open ? (SpotifyManager.getFullArtistDisplay().isEmpty() ? "Paused" : SpotifyManager.getFullArtistDisplay()) : "Not Running");

        BomboConfig.Settings s = BomboConfig.get();
        if (s != null) {
            if (!Objects.equals(s.spotifyBgColor, lastBgRaw)) {
                lastBgRaw = s.spotifyBgColor;
                cachedBg = parseHexColor(lastBgRaw, 0xEE1E1324);
            }
            if (!Objects.equals(s.spotifyBorderColor, lastBorderRaw)) {
                lastBorderRaw = s.spotifyBorderColor;
                cachedBorder = parseHexColor(lastBorderRaw, 0x3300A4DC);
            }
            if (!Objects.equals(s.spotifyTitleColor, lastTitleRaw)) {
                lastTitleRaw = s.spotifyTitleColor;
                cachedTitle = parseHexColor(lastTitleRaw, 0xFFFFFFFF);
            }
            if (!Objects.equals(s.spotifyArtistColor, lastArtistRaw)) {
                lastArtistRaw = s.spotifyArtistColor;
                cachedArtist = parseHexColor(lastArtistRaw, 0xFFA098AA);
            }
            if (!Objects.equals(s.spotifyAccentColor, lastAccentRaw)) {
                lastAccentRaw = s.spotifyAccentColor;
                cachedAccent = parseHexColor(lastAccentRaw, 0xFF00A4DC);
            }
            if (!Objects.equals(s.spotifyProgressBgColor, lastProgRaw)) {
                lastProgRaw = s.spotifyProgressBgColor;
                cachedProg = parseHexColor(lastProgRaw, 0x33333344);
            }
        }

        int hudW = getHudWidth();
        int hudH = getHudHeight();
        boolean lyricsMode = s != null && s.spotifyHudLyricsMode;

        int bgColor = cachedBg;
        int borderColor = cachedBorder;
        int titleColor = cachedTitle;
        int artistColor = cachedArtist;
        int accentColor = cachedAccent;
        int progressBg = cachedProg;

        // Background card
        g.fill(0, 0, hudW, hudH, bgColor);
        // Container border
        g.fill(0, 0, hudW, 1, borderColor);
        g.fill(0, hudH - 1, hudW, hudH, borderColor);
        g.fill(0, 0, 1, hudH, borderColor);
        g.fill(hudW - 1, 0, hudW, hudH, borderColor);

        // Spotify Logo or Album Artwork on the left
        int iconSize = 20;
        int iconX = 5;
        int iconY = Math.max(5, (hudH - 6 - iconSize) / 2);
        Identifier albumArt = (s != null && (lyricsMode || s.spotifyHudShowAlbumArt)) ? LyricsManager.getAlbumArtTexture() : null;
        if (albumArt != null) {
            g.blit(albumArt, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0F, 1.0F, 0.0F, 1.0F);
        } else {
            int bgGreen = 0xFF1DB954;
            int waveColor = 0xFF121212;
            g.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, bgGreen);
            g.fill(iconX + 3, iconY + 5, iconX + 17, iconY + 7, waveColor);
            g.fill(iconX + 4, iconY + 9, iconX + 16, iconY + 11, waveColor);
            g.fill(iconX + 5, iconY + 13, iconX + 15, iconY + 15, waveColor);
        }

        int textX = 29;
        int ctrlW = 46;
        int ctrlX = hudW - ctrlW - 4;
        int textMaxW = ctrlX - textX - 4;

        if (lyricsMode) {
            // Render synchronized lyrics: current active line + next lines
            long progressMs = isDummy ? 45000L : (long) (SpotifyManager.getProgressRatio() * SpotifyManager.getDurationSeconds() * 1000L);
            java.util.List<LyricsLine> lines = isDummy ? java.util.List.of(
                    new LyricsLine(40000L, 48000L, "Sé que te gusta el calentón", java.util.Collections.emptyList()),
                    new LyricsLine(48000L, 55000L, "Tú me miras y yo te miro lento", java.util.Collections.emptyList())
            ) : LyricsManager.getLines();

            int activeIdx = LyricsManager.getCurrentLineIndex(progressMs);
            String currentLineText = (activeIdx >= 0 && activeIdx < lines.size()) ? lines.get(activeIdx).text() : "";
            if (currentLineText.isEmpty()) {
                currentLineText = isDummy ? "Sé que te gusta el calentón" : (LyricsManager.isLoading() ? "Loading lyrics..." : track);
            }

            String dispCurrent = font.plainSubstrByWidth(currentLineText, textMaxW);
            g.text(font, "§l" + dispCurrent, textX, 5, accentColor, true);

            int nextCount = s.spotifyHudLyricsNextLines;
            int lineY = 16;
            for (int i = 1; i <= nextCount; i++) {
                int nextIdx = (activeIdx >= 0) ? activeIdx + i : i - 1;
                String nextLineText = (nextIdx >= 0 && nextIdx < lines.size()) ? lines.get(nextIdx).text() : "";
                if (nextLineText.isEmpty() && i == 1 && isDummy) {
                    nextLineText = "Tú me miras y yo te miro lento";
                }
                if (!nextLineText.isEmpty()) {
                    String dispNext = font.plainSubstrByWidth(nextLineText, textMaxW);
                    g.text(font, dispNext, textX, lineY, artistColor, false);
                }
                lineY += 11;
            }
        } else {
            // Standard Track & Artist text
            if (!track.equals(lastRawTrack)) {
                lastRawTrack = track;
                String disp = track;
                while (font.width("§l" + disp) > textMaxW && disp.length() > 3) {
                    disp = disp.substring(0, disp.length() - 2) + "…";
                }
                cachedTrackDisplay = disp;
            }
            g.text(font, "§l" + cachedTrackDisplay, textX, 5, titleColor, true);

            if (!artist.equals(lastRawArtist)) {
                lastRawArtist = artist;
                cachedArtistDisplay = font.plainSubstrByWidth(artist, textMaxW);
            }
            g.text(font, cachedArtistDisplay, textX, 16, artistColor, false);
        }

        // Controls on the right: |◀  ⏸/▶  ▶|
        int ctrlY = Math.max(5, (hudH - 6 - 9) / 2);
        g.text(font, "|◀", ctrlX, ctrlY, accentColor, true);
        g.text(font, playing ? "⏸" : "▶", ctrlX + 16, ctrlY, accentColor, true);
        g.text(font, "▶|", ctrlX + 32, ctrlY, accentColor, true);

        // Progress bar at the bottom
        int barX = 5;
        int barY = hudH - 5;
        int barW = hudW - 10;
        g.fill(barX, barY, barX + barW, barY + 2, progressBg);

        float ratio = isDummy ? 0.65f : SpotifyManager.getProgressRatio();
        int fillW = Math.max(0, Math.min(barW, (int) (barW * ratio)));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + 2, accentColor);
        }

        g.pose().popMatrix();
    }

    public static boolean onMouseClick(double mouseX, double mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.spotifyHudEnabled || button != 0) return false;

        float scale = s.spotifyHudScale <= 0.0f ? 1.0f : s.spotifyHudScale;
        double relX = (mouseX - s.spotifyHudX) / scale;
        double relY = (mouseY - s.spotifyHudY) / scale;

        int hudW = getHudWidth();
        int hudH = getHudHeight();

        if (relX < 0 || relX > hudW || relY < 0 || relY > hudH) {
            return false;
        }

        // Spotify icon or Album art click -> Open Lyrics Screen
        if (relX >= 4 && relX <= 26 && relY >= 4 && relY <= 26) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.setScreenAndShow(new LyricsScreen()));
            }
            return true;
        }

        int ctrlW = 46;
        int ctrlX = hudW - ctrlW - 4;

        if (s.spotifyHudLyricsMode) {
            // In lyrics mode, clicking lyrics text opens Lyrics Screen
            if (relX >= 27 && relX <= ctrlX - 4 && relY >= 3 && relY <= hudH - 6) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> mc.setScreenAndShow(new LyricsScreen()));
                }
                return true;
            }
        } else {
            // Track title click -> open song in Spotify Desktop App
            if (relX >= 27 && relX <= ctrlX - 4 && relY >= 3 && relY <= 15) {
                SpotifyManager.openTrackInSpotify();
                return true;
            }

            // Artist click -> open artist in Spotify Desktop App
            if (relX >= 27 && relX <= ctrlX - 4 && relY >= 16 && relY <= 28) {
                SpotifyManager.openArtistInSpotify();
                return true;
            }
        }

        // Media controls
        if (relY >= 3 && relY <= hudH - 6) {
            if (relX >= ctrlX - 4 && relX <= ctrlX + 14) {
                SpotifyManager.prevTrack();
                return true;
            } else if (relX >= ctrlX + 15 && relX <= ctrlX + 29) {
                SpotifyManager.playPause();
                return true;
            } else if (relX >= ctrlX + 30 && relX <= hudW - 2) {
                SpotifyManager.nextTrack();
                return true;
            }
        }

        return false;
    }

    private static int parseHexColor(String hex, int def) {
        if (hex == null || hex.isEmpty()) return def;
        try {
            String clean = hex.trim().replace("#", "");
            if (clean.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(clean, 16));
            } else if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (Throwable ignored) {}
        return def;
    }
}
