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

/**
 * Renders an interactive Spotify HUD card on screen.
 * Displays official Spotify logo, song name, artist, progress bar, and clickable controls (|◀ ⏸ ▶|).
 * Remains visible during chat and inventory/container screens.
 */
public class SpotifyHud {

    public static final int HUD_WIDTH = 180;
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

        drawHud(g, s.spotifyHudX, s.spotifyHudY, s.spotifyHudScale, false);
    }

    public static int getHudWidth() {
        return HUD_WIDTH;
    }

    public static int getHudHeight() {
        return HUD_HEIGHT;
    }

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
        String artist = isDummy ? "Mora" : (open ? (SpotifyManager.getCurrentArtist().isEmpty() ? "Paused" : SpotifyManager.getCurrentArtist()) : "Not Running");

        // Background card: modern dark plum/slate
        g.fill(0, 0, HUD_WIDTH, HUD_HEIGHT, 0xEE1E1324);
        // Subtle container border
        g.fill(0, 0, HUD_WIDTH, 1, 0x2AFFFFFF);
        g.fill(0, HUD_HEIGHT - 1, HUD_WIDTH, HUD_HEIGHT, 0x2AFFFFFF);
        g.fill(0, 0, 1, HUD_HEIGHT, 0x2AFFFFFF);
        g.fill(HUD_WIDTH - 1, 0, HUD_WIDTH, HUD_HEIGHT, 0x2AFFFFFF);

        // Spotify Logo on the left
        int iconSize = 20;
        int iconX = 5;
        int iconY = 5;
        g.blit(SPOTIFY_ICON, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0F, 1.0F, 0.0F, 1.0F);

        // Track and Artist text
        int textX = 29;
        String trackDisplay = font.plainSubstrByWidth(track, 96);
        g.text(font, "§f§l" + trackDisplay, textX, 5, 0xFFFFFFFF, true);

        String artistDisplay = font.plainSubstrByWidth(artist, 96);
        g.text(font, "§7" + artistDisplay, textX, 16, 0xFFA098AA, false);

        // Controls on the right (Neon Cyan #00A4DC): |◀  ⏸/▶  ▶|
        int ctrlY = 9;
        g.text(font, "§b|◀", 130, ctrlY, 0xFF00A4DC, true);
        g.text(font, playing ? "§b⏸" : "§b▶", 147, ctrlY, 0xFF00A4DC, true);
        g.text(font, "§b▶|", 163, ctrlY, 0xFF00A4DC, true);

        // Progress bar at the bottom
        int barX = 5;
        int barY = 29;
        int barW = HUD_WIDTH - 10;
        g.fill(barX, barY, barX + barW, barY + 2, 0x33FFFFFF);

        float ratio = isDummy ? 0.65f : SpotifyManager.getProgressRatio();
        int fillW = Math.max(0, Math.min(barW, (int) (barW * ratio)));
        if (fillW > 0) {
            g.fill(barX, barY, barX + fillW, barY + 2, 0xFF00A4DC);
        }

        g.pose().popMatrix();
    }

    public static boolean onMouseClick(double mouseX, double mouseY, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.spotifyHudEnabled || button != 0) return false;

        float scale = s.spotifyHudScale <= 0.0f ? 1.0f : s.spotifyHudScale;
        double relX = (mouseX - s.spotifyHudX) / scale;
        double relY = (mouseY - s.spotifyHudY) / scale;

        if (relX < 0 || relX > HUD_WIDTH || relY < 0 || relY > HUD_HEIGHT) {
            return false;
        }

        // Spotify icon click -> Open Lyrics Screen
        if (relX >= 4 && relX <= 26 && relY >= 4 && relY <= 26) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.setScreenAndShow(new LyricsScreen()));
            }
            return true;
        }

        // Track title click -> open song search in Spotify
        if (relX >= 27 && relX <= 126 && relY >= 3 && relY <= 15) {
            openWebUrl(SpotifyManager.getTrackUrl());
            return true;
        }

        // Artist click -> open artist search in Spotify
        if (relX >= 27 && relX <= 126 && relY >= 16 && relY <= 28) {
            openWebUrl(SpotifyManager.getArtistUrl());
            return true;
        }

        // Media controls (Y: 5..28)
        if (relY >= 5 && relY <= 28) {
            if (relX >= 126 && relX <= 142) {
                SpotifyManager.prevTrack();
                return true;
            } else if (relX >= 143 && relX <= 159) {
                SpotifyManager.playPause();
                return true;
            } else if (relX >= 160 && relX <= 178) {
                SpotifyManager.nextTrack();
                return true;
            }
        }

        return false;
    }

    private static void openWebUrl(String url) {
        try {
            Util.getPlatform().openUri(new URI(url));
        } catch (Throwable ignored) {}
    }
}
