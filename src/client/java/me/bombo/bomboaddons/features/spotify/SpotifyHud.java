package me.bombo.bomboaddons.features.spotify;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

/**
 * Renders an interactive Spotify HUD card on screen.
 * Displays song name, artist, elapsed time, and clickable controls (⏮ ⏯ ⏭).
 */
public class SpotifyHud {

    public static final int HUD_WIDTH = 150;
    public static final int HUD_HEIGHT = 38;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "spotify_hud"), SpotifyHud::render);
        SpotifyManager.init();
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;

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

        String track = isDummy ? "Starboy" : (open ? (SpotifyManager.getCurrentTrack().isEmpty() ? "No Track" : SpotifyManager.getCurrentTrack()) : "Spotify Closed");
        String artist = isDummy ? "The Weeknd" : (open ? (SpotifyManager.getCurrentArtist().isEmpty() ? "Paused" : SpotifyManager.getCurrentArtist()) : "Not Running");
        String timeStr = isDummy ? "01:42" : SpotifyManager.getFormattedTime();

        // Background card
        g.fill(0, 0, HUD_WIDTH, HUD_HEIGHT, 0xAA121214);
        g.fill(0, 0, 2, HUD_HEIGHT, 0xFF1DB954); // Spotify green left accent bar

        // Header: Green Spotify icon/title & Artist
        String header = "§a§l\u266B §f" + truncate(track, 18);
        g.text(font, header, 6, 4, 0xFFFFFFFF, true);

        String sub = "§7" + truncate(artist, 22);
        g.text(font, sub, 6, 15, 0xFFAAAAAA, true);

        // Controls bar: [⏮] [⏯ / ⏸] [⏭]  Timer
        String playIcon = playing ? "§a\u23F8" : "§e\u25B6";
        g.text(font, "§f\u23EE", 8, 26, 0xFFFFFFFF, true);   // Prev button at x=8..18
        g.text(font, playIcon, 26, 26, 0xFFFFFFFF, true);      // Play/Pause button at x=26..36
        g.text(font, "§f\u23ED", 44, 26, 0xFFFFFFFF, true);   // Next button at x=44..54

        // Playback elapsed timer
        String timerDisplay = "§8[" + (playing ? "§a" : "§7") + timeStr + "§8]";
        g.text(font, timerDisplay, 62, 26, 0xFFFFFFFF, true);

        g.pose().popMatrix();
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 2)) + "..";
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

        // Controls are located on Y: 22..36
        if (relY >= 22 && relY <= 38) {
            if (relX >= 4 && relX <= 20) {
                SpotifyManager.prevTrack();
                return true;
            } else if (relX >= 22 && relX <= 38) {
                SpotifyManager.playPause();
                return true;
            } else if (relX >= 40 && relX <= 56) {
                SpotifyManager.nextTrack();
                return true;
            }
        }
        return false;
    }
}
