package me.bombo.bomboaddons.features.spotify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;

/**
 * Modern synchronized lyrics display screen with karaoke-style word-for-word progression.
 */
public class LyricsScreen extends Screen {

    private static final Identifier SPOTIFY_ICON = Identifier.fromNamespaceAndPath("bomboaddons", "textures/gui/spotify.png");

    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private boolean userScrolled = false;
    private long lastUserScrollTime = 0L;

    public LyricsScreen() {
        super(Component.literal("Spotify Lyrics"));
    }

    @Override
    protected void init() {
        super.init();
        userScrolled = false;
        scrollY = 0.0;
        targetScrollY = 0.0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        Font font = this.font;

        // Dark modern background gradient
        g.fill(0, 0, this.width, this.height, 0xF0120F1A);

        int topH = 68;
        int bottomH = 32;
        int lyricsAreaY = topH;
        int lyricsAreaH = this.height - topH - bottomH;

        // --- TOP HEADER ---
        g.fill(0, 0, this.width, topH, 0x881E182A);
        g.fill(0, topH - 1, this.width, topH, 0x33FFFFFF);

        // Spotify icon
        int iconSize = 28;
        int iconX = 20;
        int iconY = 16;
        g.blit(SPOTIFY_ICON, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0F, 1.0F, 0.0F, 1.0F);

        // Track and Artist
        String track = SpotifyManager.getCurrentTrack();
        String artist = SpotifyManager.getCurrentArtist();
        if (track.isEmpty()) track = "No Track Playing";
        if (artist.isEmpty()) artist = "Spotify Desktop";

        int textX = iconX + iconSize + 12;
        // Hover state for track
        boolean hoverTrack = mouseX >= textX && mouseX <= textX + font.width(track) && mouseY >= 14 && mouseY <= 26;
        g.text(font, (hoverTrack ? "§n§f" : "§f§l") + track, textX, 14, 0xFFFFFFFF, true);

        // Hover state for artist
        boolean hoverArtist = mouseX >= textX && mouseX <= textX + font.width(artist) && mouseY >= 28 && mouseY <= 40;
        g.text(font, (hoverArtist ? "§n§b" : "§7") + artist, textX, 29, 0xFFA098B0, true);

        // Top right controls: ⏮  ⏯  ⏭
        int ctrlY = 22;
        int nextX = this.width - 50;
        int playX = nextX - 28;
        int prevX = playX - 28;

        boolean playing = SpotifyManager.isPlaying();
        g.text(font, "§b|◀", prevX, ctrlY, 0xFF00A4DC, true);
        g.text(font, playing ? "§b⏸" : "§b▶", playX, ctrlY, 0xFF00A4DC, true);
        g.text(font, "§b▶|", nextX, ctrlY, 0xFF00A4DC, true);

        // Close button (X)
        int closeX = this.width - 24;
        boolean hoverClose = mouseX >= closeX - 4 && mouseX <= closeX + 12 && mouseY >= 8 && mouseY <= 24;
        g.text(font, hoverClose ? "§c§l✕" : "§8✕", closeX, 10, 0xFFFFFFFF, false);

        // Progress bar in header
        int barW = Math.min(400, this.width - 240);
        int barX = (this.width - barW) / 2;
        int barY = 54;
        g.fill(barX, barY, barX + barW, barY + 4, 0x44FFFFFF);
        float ratio = SpotifyManager.getProgressRatio();
        int fillW = (int) (barW * ratio);
        g.fill(barX, barY, barX + fillW, barY + 4, 0xFF00A4DC);

        // Timers
        String timeCurrent = SpotifyManager.getFormattedTime();
        String timeTotal = SpotifyManager.getFormattedDuration();
        g.text(font, "§8" + timeCurrent, barX - font.width(timeCurrent) - 6, barY - 2, 0xFF888899, true);
        g.text(font, "§8" + timeTotal, barX + barW + 6, barY - 2, 0xFF888899, true);

        // --- LYRICS CONTENT AREA ---
        List<LyricsManager.LyricsLine> lines = LyricsManager.getLines();
        long currentMs = SpotifyManager.getProgressSeconds() * 1000L;
        int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);

        // Auto-center scroll on active line if user isn't actively scrolling
        if (!userScrolled || (System.currentTimeMillis() - lastUserScrollTime > 3500L)) {
            userScrolled = false;
            if (activeIdx >= 0) {
                targetScrollY = activeIdx * 32.0;
            }
        }

        // Smooth scroll interpolation
        scrollY += (targetScrollY - scrollY) * 0.15;

        // Clip rendering to lyrics viewport
        int viewCenterY = lyricsAreaY + lyricsAreaH / 2;

        if (LyricsManager.isLoading()) {
            String loadingStr = "§bSearching for lyrics on LRCLIB...";
            g.text(font, loadingStr, (this.width - font.width(loadingStr)) / 2, viewCenterY - 6, 0xFF00A4DC, true);
        } else if (lines.isEmpty()) {
            String noLrc = "§7" + LyricsManager.getStatusMessage();
            g.text(font, noLrc, (this.width - font.width(noLrc)) / 2, viewCenterY - 6, 0xFF888899, true);
        } else {
            for (int i = 0; i < lines.size(); i++) {
                LyricsManager.LyricsLine line = lines.get(i);
                double lineY = viewCenterY + (i * 32.0) - scrollY;

                // Viewport cull lines outside area
                if (lineY < lyricsAreaY - 20 || lineY > lyricsAreaY + lyricsAreaH + 20) {
                    continue;
                }

                boolean isActive = (i == activeIdx);
                int lineIntY = (int) lineY;

                if (isActive) {
                    // Active line highlight glow pill
                    int textW = font.width(line.text());
                    int pillX = (this.width - textW) / 2 - 12;
                    int pillW = textW + 24;
                    g.fill(pillX, lineIntY - 4, pillX + pillW, lineIntY + 14, 0x2A00A4DC);

                    // Word-by-word karaoke highlighting
                    List<LyricsManager.WordTime> words = line.words();
                    if (words != null && !words.isEmpty()) {
                        int curX = (this.width - textW) / 2;
                        for (int w = 0; w < words.size(); w++) {
                            LyricsManager.WordTime wt = words.get(w);
                            boolean isWordActive = (currentMs >= wt.startMs());
                            String wordStr = wt.word() + " ";
                            int color = isWordActive ? 0xFF00E5FF : 0xFFFFFFFF;
                            g.text(font, (isWordActive ? "§b§l" : "§f") + wordStr, curX, lineIntY, color, true);
                            curX += font.width(wordStr);
                        }
                    } else {
                        // Standard active line without word timestamps
                        int startX = (this.width - textW) / 2;
                        g.text(font, "§b§l" + line.text(), startX, lineIntY, 0xFF00E5FF, true);
                    }
                } else if (i < activeIdx) {
                    // Past line (faded)
                    int textW = font.width(line.text());
                    int startX = (this.width - textW) / 2;
                    g.text(font, line.text(), startX, lineIntY, 0xFF706B7D, false);
                } else {
                    // Future line (semi-bright)
                    int textW = font.width(line.text());
                    int startX = (this.width - textW) / 2;
                    g.text(font, line.text(), startX, lineIntY, 0xFFB8B2C4, false);
                }
            }
        }

        // --- BOTTOM BAR ---
        g.fill(0, this.height - bottomH, this.width, this.height, 0x881E182A);
        g.fill(0, this.height - bottomH, this.width, this.height - bottomH + 1, 0x33FFFFFF);

        String info = LyricsManager.isSynced() ? "§a● Synced Lyrics (LRCLIB)" : "§e○ Plain Lyrics";
        g.text(font, info, 20, this.height - bottomH + 11, 0xFF888899, true);

        if (userScrolled) {
            String syncBtn = "§b§n[Sync to Current Line]";
            int sX = this.width - font.width(syncBtn) - 20;
            g.text(font, syncBtn, sX, this.height - bottomH + 11, 0xFF00A4DC, true);
        } else {
            String escTip = "§8Press Esc to close";
            int eX = this.width - font.width(escTip) - 20;
            g.text(font, escTip, eX, this.height - bottomH + 11, 0xFF666677, false);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        userScrolled = true;
        lastUserScrollTime = System.currentTimeMillis();
        targetScrollY -= verticalAmount * 40.0;
        int max = Math.max(0, LyricsManager.getLines().size() * 32);
        if (targetScrollY < 0) targetScrollY = 0;
        if (targetScrollY > max) targetScrollY = max;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            // Close button
            if (mouseX >= this.width - 30 && mouseX <= this.width - 10 && mouseY >= 6 && mouseY <= 26) {
                this.onClose();
                return true;
            }

            // Top Header: Track title click -> open song in Spotify
            int textX = 20 + 28 + 12;
            int trackW = this.font.width(SpotifyManager.getCurrentTrack());
            if (mouseX >= textX && mouseX <= textX + trackW && mouseY >= 12 && mouseY <= 26) {
                openWebUrl(SpotifyManager.getTrackUrl());
                return true;
            }

            // Artist click -> open artist in Spotify
            int artistW = this.font.width(SpotifyManager.getCurrentArtist());
            if (mouseX >= textX && mouseX <= textX + artistW && mouseY >= 27 && mouseY <= 42) {
                openWebUrl(SpotifyManager.getArtistUrl());
                return true;
            }

            // Top Controls: Prev, Play/Pause, Next
            int nextX = this.width - 50;
            int playX = nextX - 28;
            int prevX = playX - 28;
            if (mouseY >= 16 && mouseY <= 36) {
                if (mouseX >= prevX - 6 && mouseX <= prevX + 16) {
                    SpotifyManager.prevTrack();
                    return true;
                } else if (mouseX >= playX - 6 && mouseX <= playX + 16) {
                    SpotifyManager.playPause();
                    return true;
                } else if (mouseX >= nextX - 6 && mouseX <= nextX + 16) {
                    SpotifyManager.nextTrack();
                    return true;
                }
            }

            // Bottom bar sync button
            if (userScrolled && mouseY >= this.height - 32) {
                userScrolled = false;
                long currentMs = SpotifyManager.getProgressSeconds() * 1000L;
                int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);
                if (activeIdx >= 0) {
                    targetScrollY = activeIdx * 32.0;
                }
                return true;
            }
        }
        return super.mouseClicked(event, handled);
    }

    private static void openWebUrl(String url) {
        try {
            Util.getPlatform().openUri(new URI(url));
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
