package me.bombo.bomboaddons.features.spotify;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Rich, synchronized karaoke lyrics screen.
 * Displays word-by-word karaoke or Spotify-style full line highlights,
 * Vivi Music multi-provider candidate selector modal, interactive delay slider with click-to-type number input,
 * raw data viewer & custom lyrics editor, and Desktop Spotify links.
 */
public class LyricsScreen extends Screen {

    private static final Identifier SPOTIFY_ICON = Identifier.fromNamespaceAndPath("bomboaddons", "textures/gui/spotify.png");

    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private boolean userScrolled = false;
    private long lastUserScrollTime = 0L;

    // Modals
    private boolean showRawModal = false;
    private boolean showCandidatesModal = false;
    private boolean isEditingCustom = false;
    private String customLyricsBuffer = "";

    private double rawScrollY = 0.0;
    private double candidatesScrollY = 0.0;
    private static final int LINE_HEIGHT = 38;

    private int lastActiveLineIdx = -1;
    private int maxActiveWordIdx = -1;
    private long lastSeenCurrentMs = 0L;

    // Offset editing & slider dragging state
    private boolean isDraggingSlider = false;
    private boolean isEditingOffsetBox = false;
    private String offsetInputBuffer = "";

    public LyricsScreen() {
        super(Component.literal("Synced Lyrics"));
    }

    @Override
    protected void init() {
        super.init();
        userScrolled = false;
        showRawModal = false;
        showCandidatesModal = false;
        isEditingCustom = false;
        isDraggingSlider = false;
        isEditingOffsetBox = false;
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
        int bottomH = 38;
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

        // Timers
        String timeCurrent = SpotifyManager.getFormattedTime();
        String timeTotal = SpotifyManager.getFormattedDuration();
        g.text(font, "§8" + timeCurrent, barX - font.width(timeCurrent) - 6, barY - 2, 0xFF888899, true);
        g.text(font, "§8" + timeTotal, barX + barW + 6, barY - 2, 0xFF888899, true);

        // --- LYRICS CONTENT AREA ---
        List<LyricsManager.LyricsLine> lines = LyricsManager.getLines();
        long currentMs = SpotifyManager.getProgressMs() + BomboConfig.get().lyricsOffsetMs;
        int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);

        // Monotonic progression
        if (activeIdx != lastActiveLineIdx || Math.abs(currentMs - lastSeenCurrentMs) > 2500L) {
            lastActiveLineIdx = activeIdx;
            maxActiveWordIdx = -1;
        }
        lastSeenCurrentMs = currentMs;

        // Auto-scroll
        if (!userScrolled || (System.currentTimeMillis() - lastUserScrollTime > 3500L)) {
            userScrolled = false;
            if (activeIdx >= 0) {
                targetScrollY = activeIdx * LINE_HEIGHT;
            }
        }

        // Smooth scroll
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
                    // Line width
                    int totalLineW = 0;
                    int spaceW = font.width(" ");
                    if (words != null && !words.isEmpty()) {
                        for (int w = 0; w < words.size(); w++) {
                            if (w > 0) totalLineW += spaceW;
                            totalLineW += font.width("§b§l" + words.get(w).word());
                        }
                    } else {
                        // Spotify style full line width
                        totalLineW = font.width("§f§l" + line.text());
                    }

                    // Background highlight pill
                    int pillX = (this.width - totalLineW) / 2 - 14;
                    int pillW = totalLineW + 28;
                    int pillH = line.backgroundText() != null ? 30 : 22;
                    g.fill(pillX, lineIntY - 5, pillX + pillW, lineIntY + pillH, 0x3300A4DC);

                    if (words != null && !words.isEmpty()) {
                        // Word-by-word karaoke rendering
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
                        // SPOTIFY FULL LINE HIGHLIGHT (Image 4): Highlight entire line in bright bold white!
                        int startX = (this.width - totalLineW) / 2;
                        g.text(font, "§f§l" + line.text(), startX, lineIntY, 0xFFFFFFFF, true);
                    }

                    // Background vocals underneath
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
        int barBottomY = this.height - bottomH;
        g.fill(0, barBottomY, this.width, this.height, 0xEE160F1E);
        g.fill(0, barBottomY, this.width, barBottomY + 1, 0x3300A4DC);

        // 1. Status & Candidate Selector Button (Vivi Music modal trigger)
        int candCount = LyricsManager.availableCandidates.size();
        String candBtn = "§d§n[Lyrics (" + (candCount > 0 ? candCount : "1") + ")]";
        int candX = 16;
        g.text(font, candBtn, candX, barBottomY + 12, 0xFFD946EF, true);

        // 2. Raw / Edit Lyrics button
        String rawBtn = showRawModal ? "§a§n[Hide Raw]" : "§e§n[Raw / Edit]";
        int rawX = candX + font.width(candBtn) + 12;
        g.text(font, rawBtn, rawX, barBottomY + 12, showRawModal ? 0xFF55FF55 : 0xFFE0BB40, true);

        // 3. Interactive Delay Slider + Number Input Box + Reset Button
        int offsetGroupX = rawX + font.width(rawBtn) + 16;
        g.text(font, "§7Delay:", offsetGroupX, barBottomY + 12, 0xFF9CA3AF, false);

        int sliderX = offsetGroupX + font.width("Delay:") + 8;
        int sliderW = 100;
        int sliderY = barBottomY + 15;

        // Handle dragging
        if (isDraggingSlider) {
            float ratioDrag = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - sliderX) / sliderW));
            int newOffset = Math.round((ratioDrag * 10000.0f) - 5000.0f);
            // Snap to 50ms intervals
            newOffset = (newOffset / 50) * 50;
            BomboConfig.get().lyricsOffsetMs = newOffset;
            BomboConfig.save();
        }

        int curOffset = BomboConfig.get().lyricsOffsetMs;
        float thumbRatio = Math.max(0.0f, Math.min(1.0f, (curOffset + 5000.0f) / 10000.0f));
        int thumbX = sliderX + (int) (thumbRatio * sliderW);

        // Track
        g.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 4, 0x44333344);
        g.fill(sliderX, sliderY, thumbX, sliderY + 4, 0xFF00A4DC);
        // Thumb
        g.fill(thumbX - 3, sliderY - 3, thumbX + 3, sliderY + 7, 0xFF00E5FF);

        // Click-to-type number box
        int boxX = sliderX + sliderW + 10;
        int boxW = 58;
        int boxY = barBottomY + 8;
        int boxH = 16;

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, isEditingOffsetBox ? 0xFF2A1F33 : 0x441E1324);
        g.outline(boxX, boxY, boxW, boxH, isEditingOffsetBox ? 0xFF00E5FF : 0x4400A4DC);

        String boxDisplay = isEditingOffsetBox
                ? offsetInputBuffer + ((System.currentTimeMillis() / 400 % 2 == 0) ? "|" : "")
                : (curOffset >= 0 ? "+" : "") + curOffset + "ms";
        int dispX = boxX + (boxW - font.width(boxDisplay)) / 2;
        g.text(font, (isEditingOffsetBox ? "§e" : "§b") + boxDisplay, dispX, boxY + 4, 0xFF00E5FF, false);

        // Reset button [↺]
        int resetX = boxX + boxW + 6;
        g.text(font, "§c[↺]", resetX, barBottomY + 12, 0xFFFF6666, true);

        // Right side: Sync button or ESC tip
        if (userScrolled) {
            String syncBtn = "§b§n[Sync to Current Line]";
            int sX = this.width - font.width(syncBtn) - 20;
            g.text(font, syncBtn, sX, barBottomY + 12, 0xFF00A4DC, true);
        } else {
            String escTip = "§8Esc to close";
            int eX = this.width - font.width(escTip) - 20;
            g.text(font, escTip, eX, barBottomY + 12, 0xFF666677, false);
        }

        // --- MODALS OVERLAYS ---
        if (showCandidatesModal) {
            renderCandidatesModal(g, font, mouseX, mouseY);
        } else if (showRawModal) {
            renderRawModal(g, font, mouseX, mouseY);
        }
    }

    private void renderCandidatesModal(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xCC000000);

        int modalW = Math.min(640, this.width - 40);
        int modalH = Math.min(380, this.height - 60);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        // Card Frame
        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA1A1322);
        g.outline(modalX, modalY, modalW, modalH, 0xFF00A4DC);

        // Header
        g.text(font, "§f§lSelect Lyrics Version §7(" + LyricsManager.availableCandidates.size() + " found)", modalX + 16, modalY + 14, 0xFFFFFFFF, true);

        String closeBtn = "§c[✕]";
        int closeX = modalX + modalW - 28;
        g.text(font, closeBtn, closeX, modalY + 14, 0xFFFF5555, true);

        // Candidates List Area
        int listX = modalX + 14;
        int listY = modalY + 36;
        int listW = modalW - 28;
        int listH = modalH - 48;

        g.enableScissor(listX, listY, listW, listH);

        List<LyricsManager.LyricCandidate> cands = LyricsManager.availableCandidates;
        int itemH = 50;
        int totalH = cands.size() * itemH;
        int maxScroll = Math.max(0, totalH - listH);
        candidatesScrollY = Math.max(0, Math.min(candidatesScrollY, maxScroll));

        int drawY = listY + 4 - (int) candidatesScrollY;

        for (int i = 0; i < cands.size(); i++) {
            LyricsManager.LyricCandidate cand = cands.get(i);
            boolean isSelected = (i == LyricsManager.selectedCandidateIndex);
            boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= drawY && mouseY <= drawY + itemH - 4;

            int bgCol = isSelected ? 0x4400A4DC : (hover ? 0x22FFFFFF : 0x22120B17);
            g.fill(listX, drawY, listX + listW, drawY + itemH - 6, bgCol);
            g.outline(listX, drawY, listW, itemH - 6, isSelected ? 0xFF00E5FF : 0x2200A4DC);

            // Preview snippet lines
            String[] pLines = cand.preview().split("\\|");
            String line1 = pLines.length > 0 ? pLines[0].trim() : cand.preview();
            String line2 = pLines.length > 1 ? pLines[1].trim() : "...";

            g.text(font, "§f" + line1, listX + 12, drawY + 8, 0xFFFFFFFF, false);
            g.text(font, "§8" + line2, listX + 12, drawY + 22, 0xFFA098B0, false);

            // Provider & Sync badges on right
            int badgeRight = listX + listW - 12;

            if (isSelected) {
                String activeTag = "§a✔ Active";
                g.text(font, activeTag, badgeRight - font.width(activeTag), drawY + 14, 0xFF55FF55, true);
                badgeRight -= font.width(activeTag) + 10;
            }

            // Sync type pill
            String syncTag = "[" + cand.syncType() + "]";
            int syncCol = cand.syncType().contains("Word") ? 0xFF00E5FF : (cand.syncType().contains("Line") ? 0xFF10B981 : 0xFF9CA3AF);
            g.text(font, syncTag, badgeRight - font.width(syncTag), drawY + 14, syncCol, false);
            badgeRight -= font.width(syncTag) + 8;

            // Provider pill
            String provTag = "[" + cand.provider() + "]";
            g.text(font, provTag, badgeRight - font.width(provTag), drawY + 14, 0xFFD946EF, false);

            drawY += itemH;
        }

        g.disableScissor();
    }

    private void renderRawModal(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0xCC000000);

        int modalW = Math.min(680, this.width - 40);
        int modalH = Math.min(420, this.height - 60);
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFA1E1324);
        g.outline(modalX, modalY, modalW, modalH, 0xFF00A4DC);

        // Header Title
        String title = isEditingCustom ? "§e§lCustom Lyrics Editor" : "§b§lRaw Lyrics API Data (" + LyricsManager.getProvider() + ")";
        g.text(font, title, modalX + 16, modalY + 14, 0xFF00E5FF, true);

        // Buttons in header: [Edit / View Mode] [Paste / Copy] [Apply] [✕]
        int btnRight = modalX + modalW - 30;

        String closeBtn = "§c[✕]";
        g.text(font, closeBtn, btnRight, modalY + 14, 0xFFFF5555, true);
        btnRight -= font.width(closeBtn) + 12;

        if (isEditingCustom) {
            String applyBtn = "§a§l[Apply Lyrics]";
            g.text(font, applyBtn, btnRight - font.width(applyBtn), modalY + 14, 0xFF55FF55, true);
            btnRight -= font.width(applyBtn) + 12;

            String pasteBtn = "§b[Paste Clipboard]";
            g.text(font, pasteBtn, btnRight - font.width(pasteBtn), modalY + 14, 0xFF00E5FF, true);
            btnRight -= font.width(pasteBtn) + 12;

            String cancelBtn = "§7[Cancel]";
            g.text(font, cancelBtn, btnRight - font.width(cancelBtn), modalY + 14, 0xFF9CA3AF, false);
        } else {
            String copyBtn = "§a[Copy Clipboard]";
            g.text(font, copyBtn, btnRight - font.width(copyBtn), modalY + 14, 0xFF55FF55, true);
            btnRight -= font.width(copyBtn) + 12;

            String editBtn = "§e[Edit Custom]";
            g.text(font, editBtn, btnRight - font.width(editBtn), modalY + 14, 0xFFFFD700, true);
        }

        // Content Area
        int areaX = modalX + 16;
        int areaY = modalY + 36;
        int areaW = modalW - 32;
        int areaH = modalH - 48;

        g.fill(areaX, areaY, areaX + areaW, areaY + areaH, 0x88120B17);
        g.outline(areaX, areaY, areaW, areaH, 0x3300A4DC);

        g.enableScissor(areaX, areaY, areaW, areaH);

        String textToDraw = isEditingCustom ? customLyricsBuffer : LyricsManager.getRawLyrics();
        String[] rawLines = textToDraw.split("(?=\\[\\d{1,2}:\\d{2})|\\r?\\n");
        int lineHeight = 13;
        int maxScroll = Math.max(0, rawLines.length * lineHeight - areaH);
        rawScrollY = Math.max(0, Math.min(rawScrollY, maxScroll));

        int drawY = areaY + 6 - (int) rawScrollY;
        for (String line : rawLines) {
            if (drawY >= areaY - 14 && drawY <= areaY + areaH + 14) {
                g.text(font, (isEditingCustom ? "§f" : "§7") + line, areaX + 8, drawY, 0xFFB8B2C4, false);
            }
            drawY += lineHeight;
        }

        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showCandidatesModal) {
            candidatesScrollY -= verticalAmount * 36.0;
            return true;
        }
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

            // 1. Candidates Modal Click Handling
            if (showCandidatesModal) {
                int modalW = Math.min(640, this.width - 40);
                int modalH = Math.min(380, this.height - 60);
                int modalX = (this.width - modalW) / 2;
                int modalY = (this.height - modalH) / 2;

                // Close button
                if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= modalX + modalW - 36 && mouseX <= modalX + modalW - 10) {
                    showCandidatesModal = false;
                    return true;
                }

                // Check card clicks
                int listX = modalX + 14;
                int listY = modalY + 36;
                int listW = modalW - 28;
                int listH = modalH - 48;

                if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                    int clickedIdx = (int) ((mouseY - listY + candidatesScrollY) / 50);
                    if (clickedIdx >= 0 && clickedIdx < LyricsManager.availableCandidates.size()) {
                        LyricsManager.applyCandidate(LyricsManager.availableCandidates.get(clickedIdx));
                        showCandidatesModal = false;
                        return true;
                    }
                }

                // Click outside modal
                if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
                    showCandidatesModal = false;
                    return true;
                }
                return true;
            }

            // 2. Raw Modal Click Handling
            if (showRawModal) {
                int modalW = Math.min(680, this.width - 40);
                int modalH = Math.min(420, this.height - 60);
                int modalX = (this.width - modalW) / 2;
                int modalY = (this.height - modalH) / 2;

                // Close button
                if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= modalX + modalW - 36 && mouseX <= modalX + modalW - 10) {
                    showRawModal = false;
                    isEditingCustom = false;
                    return true;
                }

                if (isEditingCustom) {
                    // Apply lyrics button
                    String applyBtn = "[Apply Lyrics]";
                    int applyX = modalX + modalW - 46 - this.font.width(applyBtn);
                    if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= applyX && mouseX <= applyX + this.font.width(applyBtn)) {
                        LyricsManager.applyCustomLyrics(customLyricsBuffer);
                        showRawModal = false;
                        isEditingCustom = false;
                        return true;
                    }

                    // Paste clipboard button
                    String pasteBtn = "[Paste Clipboard]";
                    int pasteX = applyX - 12 - this.font.width(pasteBtn);
                    if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= pasteX && mouseX <= pasteX + this.font.width(pasteBtn)) {
                        String cb = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (cb != null && !cb.isEmpty()) {
                            customLyricsBuffer = cb;
                        }
                        return true;
                    }

                    // Cancel button
                    String cancelBtn = "[Cancel]";
                    int cancelX = pasteX - 12 - this.font.width(cancelBtn);
                    if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= cancelX && mouseX <= cancelX + this.font.width(cancelBtn)) {
                        isEditingCustom = false;
                        return true;
                    }
                } else {
                    // Copy to clipboard
                    String copyBtn = "[Copy Clipboard]";
                    int copyX = modalX + modalW - 46 - this.font.width(copyBtn);
                    if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= copyX && mouseX <= copyX + this.font.width(copyBtn)) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(LyricsManager.getRawLyrics());
                        return true;
                    }

                    // Edit custom button
                    String editBtn = "[Edit Custom]";
                    int editX = copyX - 12 - this.font.width(editBtn);
                    if (mouseY >= modalY + 10 && mouseY <= modalY + 26 && mouseX >= editX && mouseX <= editX + this.font.width(editBtn)) {
                        isEditingCustom = true;
                        customLyricsBuffer = LyricsManager.getRawLyrics();
                        return true;
                    }
                }

                // Click outside modal
                if (mouseX < modalX || mouseX > modalX + modalW || mouseY < modalY || mouseY > modalY + modalH) {
                    showRawModal = false;
                    isEditingCustom = false;
                    return true;
                }
                return true;
            }

            // Close (X)
            if (mouseY >= 8 && mouseY <= 26 && mouseX >= this.width - 30 && mouseX <= this.width - 8) {
                this.onClose();
                return true;
            }

            // Desktop Links
            int iconSize = 28;
            int textX = 20 + iconSize + 12;
            String track = SpotifyManager.getCurrentTrack();
            if (mouseY >= 14 && mouseY <= 26 && mouseX >= textX && mouseX <= textX + this.font.width(track)) {
                SpotifyManager.openTrackInSpotify();
                return true;
            }

            String artist = SpotifyManager.getCurrentArtist();
            if (mouseY >= 28 && mouseY <= 40 && mouseX >= textX && mouseX <= textX + this.font.width(artist)) {
                SpotifyManager.openArtistInSpotify();
                return true;
            }

            // Media controls
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

            // Bottom bar: Candidate Selector Button
            int barBottomY = this.height - 38;
            int candW = this.font.width("[Lyrics (" + (LyricsManager.availableCandidates.size() > 0 ? LyricsManager.availableCandidates.size() : "1") + ")]");
            if (mouseY >= barBottomY + 6 && mouseY <= barBottomY + 28 && mouseX >= 16 && mouseX <= 16 + candW + 8) {
                showCandidatesModal = true;
                candidatesScrollY = 0;
                return true;
            }

            // Bottom bar: Raw / Edit lyrics button
            int rawX = 16 + candW + 12;
            int rawW = this.font.width(showRawModal ? "[Hide Raw]" : "[Raw / Edit]");
            if (mouseY >= barBottomY + 6 && mouseY <= barBottomY + 28 && mouseX >= rawX && mouseX <= rawX + rawW + 8) {
                showRawModal = !showRawModal;
                rawScrollY = 0;
                isEditingCustom = false;
                return true;
            }

            // Delay slider & Box click
            int offsetGroupX = rawX + rawW + 16;
            int sliderX = offsetGroupX + this.font.width("Delay:") + 8;
            int sliderW = 100;
            int boxX = sliderX + sliderW + 10;
            int boxW = 58;
            int resetX = boxX + boxW + 6;

            // Slider click / drag start
            if (mouseY >= barBottomY + 10 && mouseY <= barBottomY + 24 && mouseX >= sliderX - 4 && mouseX <= sliderX + sliderW + 4) {
                isDraggingSlider = true;
                isEditingOffsetBox = false;
                float ratioDrag = Math.max(0.0f, Math.min(1.0f, (float) (mouseX - sliderX) / sliderW));
                int newOffset = Math.round((ratioDrag * 10000.0f) - 5000.0f);
                newOffset = (newOffset / 50) * 50;
                BomboConfig.get().lyricsOffsetMs = newOffset;
                BomboConfig.save();
                return true;
            }

            // Number input box click
            if (mouseY >= barBottomY + 6 && mouseY <= barBottomY + 26 && mouseX >= boxX && mouseX <= boxX + boxW) {
                isEditingOffsetBox = true;
                offsetInputBuffer = String.valueOf(BomboConfig.get().lyricsOffsetMs);
                return true;
            } else if (isEditingOffsetBox) {
                commitOffsetInput();
            }

            // Reset button click
            if (mouseY >= barBottomY + 6 && mouseY <= barBottomY + 26 && mouseX >= resetX && mouseX <= resetX + 26) {
                BomboConfig.get().lyricsOffsetMs = 0;
                BomboConfig.save();
                isEditingOffsetBox = false;
                return true;
            }

            // Bottom bar sync button
            if (userScrolled && mouseY >= barBottomY + 6 && mouseX >= this.width - 160) {
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
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            isDraggingSlider = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (isEditingOffsetBox) {
            char c = (char) event.codepoint();
            if (Character.isDigit(c) || (c == '-' && offsetInputBuffer.isEmpty())) {
                if (offsetInputBuffer.length() < 7) {
                    offsetInputBuffer += c;
                }
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isEditingOffsetBox) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!offsetInputBuffer.isEmpty()) {
                    offsetInputBuffer = offsetInputBuffer.substring(0, offsetInputBuffer.length() - 1);
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitOffsetInput();
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                isEditingOffsetBox = false;
                return true;
            }
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (showCandidatesModal) {
                showCandidatesModal = false;
                return true;
            }
            if (showRawModal) {
                showRawModal = false;
                isEditingCustom = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void commitOffsetInput() {
        if (isEditingOffsetBox) {
            try {
                if (!offsetInputBuffer.isEmpty() && !offsetInputBuffer.equals("-")) {
                    int val = Integer.parseInt(offsetInputBuffer);
                    val = Math.max(-100000, Math.min(100000, val));
                    BomboConfig.get().lyricsOffsetMs = val;
                    BomboConfig.save();
                }
            } catch (Throwable ignored) {}
            isEditingOffsetBox = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
