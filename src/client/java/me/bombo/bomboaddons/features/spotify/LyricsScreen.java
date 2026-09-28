package me.bombo.bomboaddons.features.spotify;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.List;

/**
 * Rich, synchronized karaoke lyrics screen.
 * Displays word-by-word highlighted singing lines, smooth auto-scroll,
 * multi-provider selector, delay adder, raw/edit data viewer, and Desktop Spotify links.
 */
public class LyricsScreen extends Screen {

    private static final Identifier SPOTIFY_ICON = Identifier.fromNamespaceAndPath("bomboaddons", "textures/gui/spotify.png");

    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private boolean userScrolled = false;
    private long lastUserScrollTime = 0L;

    private boolean showRawModal = false;
    private double rawScrollY = 0.0;
    private static final int LINE_HEIGHT = 38;

    private int lastActiveLineIdx = -1;
    private int maxActiveWordIdx = -1;
    private long lastSeenCurrentMs = 0L;

    public LyricsScreen() {
        super(Component.literal("Synced Lyrics"));
    }

    @Override
    protected void init() {
        super.init();
        userScrolled = false;
        showRawModal = false;
        lastActiveLineIdx = -1;
        maxActiveWordIdx = -1;
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

        // Spotify icon or Album Art
        int iconSize = 28;
        int iconX = 20;
        int iconY = 16;
        Identifier albumArt = LyricsManager.getAlbumArtTexture();
        Identifier iconToDraw = (albumArt != null) ? albumArt : SPOTIFY_ICON;
        g.blit(iconToDraw, iconX, iconY, iconX + iconSize, iconY + iconSize, 0.0F, 1.0F, 0.0F, 1.0F);

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
        long currentMs = SpotifyManager.getProgressMs() + BomboConfig.get().lyricsOffsetMs;
        int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);

        // Monotonic active word highlight progression (prevents flickering/jumping backwards)
        if (activeIdx != lastActiveLineIdx || Math.abs(currentMs - lastSeenCurrentMs) > 2500L) {
            lastActiveLineIdx = activeIdx;
            maxActiveWordIdx = -1;
        }
        lastSeenCurrentMs = currentMs;

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
                    // Compute fixed line width using uniform bold format to eliminate wobbling
                    int totalLineW = 0;
                    int spaceW = font.width(" ");
                    if (words != null && !words.isEmpty()) {
                        for (int w = 0; w < words.size(); w++) {
                            if (w > 0) totalLineW += spaceW;
                            totalLineW += font.width("§b§l" + words.get(w).word());
                        }
                    } else {
                        totalLineW = font.width("§b§l" + line.text());
                    }

                    // Background pill highlight
                    int pillX = (this.width - totalLineW) / 2 - 14;
                    int pillW = totalLineW + 28;
                    int pillH = line.backgroundText() != null ? 30 : 22;
                    g.fill(pillX, lineIntY - 5, pillX + pillW, lineIntY + pillH, 0x3300A4DC);

                    // Word-by-word karaoke rendering with stable slot positions
                    if (words != null && !words.isEmpty()) {
                        int curX = (this.width - totalLineW) / 2;
                        for (int w = 0; w < words.size(); w++) {
                            LyricsManager.WordTime wt = words.get(w);
                            if (currentMs >= wt.startMs()) {
                                maxActiveWordIdx = Math.max(maxActiveWordIdx, w);
                            }
                            boolean isWordActive = (w <= maxActiveWordIdx);
                            int wordSlotW = font.width("§b§l" + wt.word());

                            if (isWordActive) {
                                g.text(font, "§b§l" + wt.word(), curX, lineIntY, 0xFF00E5FF, true);
                            } else {
                                g.text(font, "§f§l" + wt.word(), curX, lineIntY, 0xFFFFFFFF, true);
                            }
                            curX += wordSlotW + spaceW;
                        }
                    } else {
                        int startX = (this.width - totalLineW) / 2;
                        g.text(font, "§b§l" + line.text(), startX, lineIntY, 0xFF00E5FF, true);
                    }

                    // Secondary / Background vocals underneath
                    if (line.backgroundText() != null && !line.backgroundText().isEmpty()) {
                        String bg = line.backgroundText();
                        int bgW = font.width("§7§o" + bg);
                        g.text(font, "§7§o" + bg, (this.width - bgW) / 2, lineIntY + 14, 0xFFA098B0, false);
                    }
                } else {
                    int textW = font.width(line.text());
                    int startX = (this.width - textW) / 2;
                    int color = (i < activeIdx) ? 0xFF706B7D : 0xFFB8B2C4;
                    g.text(font, line.text(), startX, lineIntY, color, false);

                    if (line.backgroundText() != null && !line.backgroundText().isEmpty()) {
                        String bg = line.backgroundText();
                        int bgW = font.width("§8§o" + bg);
                        g.text(font, "§8§o" + bg, (this.width - bgW) / 2, lineIntY + 12, 0xFF6B7280, false);
                    }
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
        int rawX = provX + font.width(provBtn) + 14;
        g.text(font, rawBtn, rawX, this.height - bottomH + 11, showRawModal ? 0xFF55FF55 : 0xFFE0BB40, true);

        // Delay adder controls: [-100ms] [Offset: +Xms] [+100ms]
        int offsetMs = BomboConfig.get().lyricsOffsetMs;
        String minusBtn = "§c[-100ms]";
        String offsetLabel = String.format("§e%s%dms", offsetMs >= 0 ? "+" : "", offsetMs);
        String plusBtn = "§a[+100ms]";

        int offsetGroupX = rawX + font.width(rawBtn) + 14;
        g.text(font, minusBtn, offsetGroupX, this.height - bottomH + 11, 0xFFFF6666, true);
        int labelX = offsetGroupX + font.width(minusBtn) + 4;
        g.text(font, offsetLabel, labelX, this.height - bottomH + 11, 0xFFFFD700, false);
        int plusX = labelX + font.width(offsetLabel) + 4;
        g.text(font, plusBtn, plusX, this.height - bottomH + 11, 0xFF55FF55, true);

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
        int closeBtnX = modalX + modalW - 30;
        g.text(font, closeBtn, closeBtnX, modalY + 14, 0xFFFF5555, true);

        // Content Area
        int areaX = modalX + 16;
        int areaY = modalY + 36;
        int areaW = modalW - 32;
        int areaH = modalH - 50;

        g.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0x88120B17);
        g.outline(areaX, areaY, areaW, areaH, 0x3300A4DC);

        g.enableScissor(areaX, areaY, areaW, areaH);

        String raw = LyricsManager.getRawLyrics();
        String[] rawLines = raw.split("\r?\n");
        int lineHeight = 12;
        int maxScroll = Math.max(0, rawLines.length * lineHeight - areaH);
        rawScrollY = Math.max(0, Math.min(rawScrollY, maxScroll));

        int drawY = areaY + 6 - (int) rawScrollY;
        for (String line : rawLines) {
            if (drawY >= areaY - 12 && drawY <= areaY + areaH + 12) {
                g.text(font, "§7" + line, areaX + 8, drawY, 0xFFB8B2C4, false);
            }
            drawY += lineHeight;
        }

        g.disableScissor();

        // Modal Scrollbar
        if (maxScroll > 0) {
            int sbX = areaX + areaW - 4;
            int thumbH = Math.max(16, (int) ((areaH / (float) (rawLines.length * lineHeight)) * areaH));
            int thumbY = areaY + (int) ((rawScrollY / maxScroll) * (areaH - thumbH));
            g.fill(sbX, areaY, sbX + 3, areaY + areaH, 0x22FFFFFF);
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0x8800A4DC);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showRawModal) {
            rawScrollY -= verticalAmount * 24.0;
            return true;
        }

        userScrolled = true;
        lastUserScrollTime = System.currentTimeMillis();
        targetScrollY -= verticalAmount * (LINE_HEIGHT * 1.5);
        int maxScroll = Math.max(0, LyricsManager.getLines().size() * LINE_HEIGHT);
        targetScrollY = Math.max(0, Math.min(targetScrollY, maxScroll));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0 || event.button() == 1) {
            double mouseX = event.x();
            double mouseY = event.y();

            if (showRawModal) {
                int modalW = Math.min(680, this.width - 60);
                int modalH = Math.min(400, this.height - 80);
                int modalX = (this.width - modalW) / 2;
                int modalY = (this.height - modalH) / 2;

                // Close button
                if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= modalX + modalW - 40 && mouseX <= modalX + modalW - 10) {
                    showRawModal = false;
                    return true;
                }

                // Copy to clipboard button
                String copyBtn = "[Copy to Clipboard]";
                int copyX = modalX + modalW - this.font.width(copyBtn) - 60;
                if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= copyX && mouseX <= copyX + this.font.width(copyBtn)) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(LyricsManager.getRawLyrics());
                    return true;
                }

                // Click outside modal closes it
                if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
                    showRawModal = false;
                    return true;
                }
                return true;
            }

            // Close button (X)
            if (mouseY >= 8 && mouseY <= 26 && mouseX >= this.width - 30 && mouseX <= this.width - 8) {
                this.onClose();
                return true;
            }

            // Click song title -> open in Spotify Desktop
            int iconSize = 28;
            int textX = 20 + iconSize + 12;
            String track = SpotifyManager.getCurrentTrack();
            if (mouseY >= 14 && mouseY <= 26 && mouseX >= textX && mouseX <= textX + this.font.width(track)) {
                SpotifyManager.openTrackInSpotify();
                return true;
            }

            // Click artist -> open in Spotify Desktop
            String artist = SpotifyManager.getCurrentArtist();
            if (mouseY >= 28 && mouseY <= 40 && mouseX >= textX && mouseX <= textX + this.font.width(artist)) {
                SpotifyManager.openArtistInSpotify();
                return true;
            }

            // Media controls in header
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
            int rawX = provX + provW + 14;
            int rawW = this.font.width("[Raw / Edit Lyrics]");
            if (mouseY >= this.height - 32 && mouseX >= rawX && mouseX <= rawX + rawW + 10) {
                showRawModal = !showRawModal;
                rawScrollY = 0;
                return true;
            }

            // Delay offset buttons: [-100ms] [Offset: +Xms] [+100ms]
            int offsetMs = BomboConfig.get().lyricsOffsetMs;
            String minusBtn = "[-100ms]";
            String offsetLabel = String.format("%s%dms", offsetMs >= 0 ? "+" : "", offsetMs);
            String plusBtn = "[+100ms]";

            int offsetGroupX = rawX + rawW + 14;
            int minusW = this.font.width(minusBtn);
            int labelW = this.font.width(offsetLabel);
            int plusW = this.font.width(plusBtn);

            int labelX = offsetGroupX + minusW + 4;
            int plusX = labelX + labelW + 4;

            if (mouseY >= this.height - 32 && mouseY <= this.height - 6) {
                if (mouseX >= offsetGroupX && mouseX <= offsetGroupX + minusW) {
                    BomboConfig.get().lyricsOffsetMs -= 100;
                    BomboConfig.save();
                    return true;
                } else if (mouseX >= labelX && mouseX <= labelX + labelW) {
                    BomboConfig.get().lyricsOffsetMs = 0;
                    BomboConfig.save();
                    return true;
                } else if (mouseX >= plusX && mouseX <= plusX + plusW) {
                    BomboConfig.get().lyricsOffsetMs += 100;
                    BomboConfig.save();
                    return true;
                }
            }

            // Bottom bar sync button
            if (userScrolled && mouseY >= this.height - 32 && mouseX >= this.width - 160) {
                userScrolled = false;
                long currentMs = SpotifyManager.getProgressMs() + BomboConfig.get().lyricsOffsetMs;
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (showRawModal) {
                showRawModal = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
