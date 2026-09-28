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
 * Rich, synchronized karaoke lyrics screen.
 * Displays word-by-word highlighted singing lines, smooth auto-scroll,
 * multi-provider selector, raw/edit data viewer, and Desktop Spotify links.
 */
public class LyricsScreen extends Screen {

    private static final Identifier SPOTIFY_ICON = Identifier.fromNamespaceAndPath("bomboaddons", "textures/gui/spotify.png");

    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private boolean userScrolled = false;
    private long lastUserScrollTime = 0L;

    private boolean showRawModal = false;
    private double rawScrollY = 0.0;
    private static final int LINE_HEIGHT = 36;

    public LyricsScreen() {
        super(Component.literal("Synced Lyrics"));
    }

    @Override
    protected void init() {
        super.init();
        userScrolled = false;
        showRawModal = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("Lyrics: Screen Render")) {
            renderLyrics(g, mouseX, mouseY, delta);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderLyrics(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Dark plum background matching Spotify overlay theme
        g.fill(0, 0, this.width, this.height, 0xFA140E1A);

        Font font = this.font;
        int topH = 68;
        int bottomH = 36;
        int lyricsAreaY = topH;
        int lyricsAreaH = this.height - topH - bottomH;

        // --- TOP HEADER ---
        g.fill(0, 0, this.width, topH, 0x881E1324);
        g.fill(0, topH - 1, this.width, topH, 0x3300A4DC);

        // Official Spotify icon
        int iconSize = 28;
        int iconX = 20;
        int iconY = 16;
        g.blit(SPOTIFY_ICON, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0F, 1.0F, 0.0F, 1.0F);

        // Track and Artist (Desktop links)
        String track = SpotifyManager.getCurrentTrack();
        String artist = SpotifyManager.getCurrentArtist();
        if (track.isEmpty()) track = "No Track Playing";
        if (artist.isEmpty()) artist = "Spotify Desktop";

        int textX = iconX + iconSize + 12;
        boolean hoverTrack = mouseX >= textX && mouseX <= textX + font.width(track) && mouseY >= 14 && mouseY <= 26;
        g.text(font, (hoverTrack ? "§n§b" : "§f§l") + track, textX, 14, 0xFFFFFFFF, true);

        boolean hoverArtist = mouseX >= textX && mouseX <= textX + font.width(artist) && mouseY >= 28 && mouseY <= 40;
        g.text(font, (hoverArtist ? "§n§b" : "§7") + artist, textX, 29, 0xFFA098B0, true);

        // Top right controls: ⏮  ⏸/▶  ⏭
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
        g.fill(barX, barY, barX + barW, barY + 4, 0x44333344);
        float ratio = SpotifyManager.getProgressRatio();
        int fillW = (int) (barW * ratio);
        g.fill(barX, barY, barX + fillW, barY + 4, 0xFF00A4DC);

        // Smooth Timers
        String timeCurrent = SpotifyManager.getFormattedTime();
        String timeTotal = SpotifyManager.getFormattedDuration();
        g.text(font, "§8" + timeCurrent, barX - font.width(timeCurrent) - 6, barY - 2, 0xFF888899, true);
        g.text(font, "§8" + timeTotal, barX + barW + 6, barY - 2, 0xFF888899, true);

        // --- LYRICS CONTENT AREA ---
        List<LyricsManager.LyricsLine> lines = LyricsManager.getLines();
        long currentMs = SpotifyManager.getProgressMs();
        int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);

        // Auto-center scroll on active line
        if (!userScrolled || (System.currentTimeMillis() - lastUserScrollTime > 3500L)) {
            userScrolled = false;
            if (activeIdx >= 0) {
                targetScrollY = activeIdx * LINE_HEIGHT;
            }
        }

        // Smooth scroll interpolation
        scrollY += (targetScrollY - scrollY) * 0.15;
        int viewCenterY = lyricsAreaY + lyricsAreaH / 2;

        if (LyricsManager.isLoading()) {
            String loadingStr = "§b" + LyricsManager.getStatusMessage();
            g.text(font, loadingStr, (this.width - font.width(loadingStr)) / 2, viewCenterY - 6, 0xFF00A4DC, true);
        } else if (lines.isEmpty()) {
            String noLrc = "§7" + LyricsManager.getStatusMessage();
            g.text(font, noLrc, (this.width - font.width(noLrc)) / 2, viewCenterY - 6, 0xFF888899, true);
        } else {
            for (int i = 0; i < lines.size(); i++) {
                LyricsManager.LyricsLine line = lines.get(i);
                double lineY = viewCenterY + (i * (double) LINE_HEIGHT) - scrollY;

                // Viewport culling
                if (lineY < lyricsAreaY - 24 || lineY > lyricsAreaY + lyricsAreaH + 24) {
                    continue;
                }

                boolean isActive = (i == activeIdx);
                int lineIntY = (int) lineY;

                List<LyricsManager.WordTime> words = line.words();
                if (isActive) {
                    // Compute accurate line width with word spacing
                    int totalLineW = 0;
                    if (words != null && !words.isEmpty()) {
                        for (int w = 0; w < words.size(); w++) {
                            if (w > 0) totalLineW += font.width(" ");
                            boolean isWActive = (currentMs >= words.get(w).startMs());
                            totalLineW += font.width((isWActive ? "§b§l" : "§f") + words.get(w).word());
                        }
                    } else {
                        totalLineW = font.width("§b§l" + line.text());
                    }

                    // Background pill highlight
                    int pillX = (this.width - totalLineW) / 2 - 14;
                    int pillW = totalLineW + 28;
                    g.fill(pillX, lineIntY - 5, pillX + pillW, lineIntY + 16, 0x3300A4DC);

                    // Word-by-word karaoke rendering
                    if (words != null && !words.isEmpty()) {
                        int curX = (this.width - totalLineW) / 2;
                        int spaceW = font.width(" ");
                        for (int w = 0; w < words.size(); w++) {
                            LyricsManager.WordTime wt = words.get(w);
                            boolean isWordActive = (currentMs >= wt.startMs());
                            String formatted = (isWordActive ? "§b§l" : "§f") + wt.word();
                            int color = isWordActive ? 0xFF00E5FF : 0xFFFFFFFF;
                            g.text(font, formatted, curX, lineIntY, color, true);
                            curX += font.width(formatted) + spaceW;
                        }
                    } else {
                        int startX = (this.width - totalLineW) / 2;
                        g.text(font, "§b§l" + line.text(), startX, lineIntY, 0xFF00E5FF, true);
                    }
                } else {
                    int textW = font.width(line.text());
                    int startX = (this.width - textW) / 2;
                    int color = (i < activeIdx) ? 0xFF706B7D : 0xFFB8B2C4;
                    g.text(font, line.text(), startX, lineIntY, color, false);
                }
            }
        }

        // --- BOTTOM BAR ---
        g.fill(0, this.height - bottomH, this.width, this.height, 0x881E1324);
        g.fill(0, this.height - bottomH, this.width, this.height - bottomH + 1, 0x3300A4DC);

        // Status & Provider button on left
        String statusText = LyricsManager.isSynced() ? "§a● Synced" : "§e○ Plain";
        g.text(font, statusText, 20, this.height - bottomH + 11, 0xFF888899, true);

        String provBtn = "§b§n[Provider: " + LyricsManager.getProvider() + "]";
        int provX = 85;
        g.text(font, provBtn, provX, this.height - bottomH + 11, 0xFF00A4DC, true);

        // Raw / Edit lyrics button
        String rawBtn = showRawModal ? "§a§n[Hide Raw Data]" : "§e§n[Raw / Edit Lyrics]";
        int rawX = provX + font.width(provBtn) + 16;
        g.text(font, rawBtn, rawX, this.height - bottomH + 11, showRawModal ? 0xFF55FF55 : 0xFFE0BB40, true);

        if (userScrolled) {
            String syncBtn = "§b§n[Sync to Current Line]";
            int sX = this.width - font.width(syncBtn) - 20;
            g.text(font, syncBtn, sX, this.height - bottomH + 11, 0xFF00A4DC, true);
        } else {
            String escTip = "§8Press Esc to close";
            int eX = this.width - font.width(escTip) - 20;
            g.text(font, escTip, eX, this.height - bottomH + 11, 0xFF666677, false);
        }

        // --- RAW LYRICS MODAL OVERLAY ---
        if (showRawModal) {
            renderRawModal(g, font, mouseX, mouseY);
        }
    }

    private void renderRawModal(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xAA000000);

        int modalW = Math.min(680, this.width - 60);
        int modalH = Math.min(400, this.height - 80);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Modal window frame
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA1E1324);
        g.fill(modalX, modalY, modalX + modalW, modalY + 1, 0xFF00A4DC);
        g.fill(modalX, modalY + modalH - 1, modalX + modalW, modalY + modalH, 0xFF00A4DC);
        g.fill(modalX, modalY, modalX + 1, modalY + modalH, 0xFF00A4DC);
        g.fill(modalX + modalW - 1, modalY, modalX + modalW, modalY + modalH, 0xFF00A4DC);

        // Header
        g.text(font, "§b§lRaw Lyrics API Data (" + LyricsManager.getProvider() + ")", modalX + 16, modalY + 14, 0xFF00E5FF, true);

        // Action Buttons: Copy & Close
        String copyBtn = "§a[Copy to Clipboard]";
        int copyX = modalX + modalW - font.width(copyBtn) - 60;
        g.text(font, copyBtn, copyX, modalY + 14, 0xFF55FF55, true);

        String closeBtn = "§c[✕]";
        int closeX = modalX + modalW - 30;
        g.text(font, closeBtn, closeX, modalY + 14, 0xFFFF5555, true);

        // Text display box
        int boxX = modalX + 14;
        int boxY = modalY + 36;
        int boxW = modalW - 28;
        int boxH = modalH - 48;
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xEE120A16);

        String raw = LyricsManager.getRawLyrics();
        String[] rawLines = raw.split("\r?\n");

        int lineY = boxY + 6 - (int) rawScrollY;
        for (String rLine : rawLines) {
            if (lineY >= boxY + 2 && lineY <= boxY + boxH - 12) {
                String sub = font.plainSubstrByWidth(rLine, boxW - 16);
                g.text(font, "§7" + sub, boxX + 8, lineY, 0xFFAAAAAA, false);
            }
            lineY += 12;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showRawModal) {
            rawScrollY -= verticalAmount * 24.0;
            if (rawScrollY < 0) rawScrollY = 0;
            return true;
        }

        userScrolled = true;
        lastUserScrollTime = System.currentTimeMillis();
        targetScrollY -= verticalAmount * 45.0;
        int max = Math.max(0, LyricsManager.getLines().size() * LINE_HEIGHT);
        if (targetScrollY < 0) targetScrollY = 0;
        if (targetScrollY > max) targetScrollY = max;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            if (showRawModal) {
                int modalW = Math.min(680, this.width - 60);
                int modalH = Math.min(400, this.height - 80);
                int modalX = (this.width - modalW) / 2;
                int modalY = (this.height - modalH) / 2;

                // Close modal
                if (mouseX >= modalX + modalW - 40 && mouseX <= modalX + modalW - 10 && mouseY >= modalY + 8 && mouseY <= modalY + 28) {
                    showRawModal = false;
                    return true;
                }

                // Copy to clipboard
                int copyX = modalX + modalW - this.font.width("§a[Copy to Clipboard]") - 60;
                if (mouseX >= copyX && mouseX <= copyX + 120 && mouseY >= modalY + 8 && mouseY <= modalY + 28) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.keyboardHandler != null) {
                        mc.keyboardHandler.setClipboard(LyricsManager.getRawLyrics());
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aCopied raw lyrics to clipboard!"));
                        }
                    }
                    return true;
                }
                return true;
            }

            // Close button
            if (mouseX >= this.width - 30 && mouseX <= this.width - 10 && mouseY >= 6 && mouseY <= 26) {
                this.onClose();
                return true;
            }

            // Top Header: Track title click -> open song in Spotify Desktop
            int textX = 20 + 28 + 12;
            int trackW = this.font.width(SpotifyManager.getCurrentTrack());
            if (mouseX >= textX && mouseX <= textX + trackW && mouseY >= 12 && mouseY <= 26) {
                SpotifyManager.openTrackInSpotify();
                return true;
            }

            // Artist click -> open artist in Spotify Desktop
            int artistW = this.font.width(SpotifyManager.getCurrentArtist());
            if (mouseX >= textX && mouseX <= textX + artistW && mouseY >= 27 && mouseY <= 42) {
                SpotifyManager.openArtistInSpotify();
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

            // Bottom bar: Provider switcher
            int provX = 85;
            int provW = this.font.width("[Provider: " + LyricsManager.getProvider() + "]");
            if (mouseY >= this.height - 32 && mouseX >= provX && mouseX <= provX + provW + 10) {
                LyricsManager.cycleProvider();
                return true;
            }

            // Bottom bar: Raw / Edit lyrics button
            int rawX = provX + provW + 16;
            int rawW = this.font.width("[Raw / Edit Lyrics]");
            if (mouseY >= this.height - 32 && mouseX >= rawX && mouseX <= rawX + rawW + 10) {
                showRawModal = !showRawModal;
                rawScrollY = 0;
                return true;
            }

            // Bottom bar sync button
            if (userScrolled && mouseY >= this.height - 32 && mouseX >= this.width - 160) {
                userScrolled = false;
                long currentMs = SpotifyManager.getProgressMs();
                int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);
                if (activeIdx >= 0) {
                    targetScrollY = activeIdx * LINE_HEIGHT;
                }
                return true;
            }
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
