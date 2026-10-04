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

import java.util.ArrayList;
import java.util.List;

/**
 * On-screen HUD displaying synchronized lyrics with progressive karaoke letter-by-letter wipe,
 * configurable lines before/after, duet vocal separation, and dynamic artwork colors.
 */
public class LyricsHud {

    public static final int DEFAULT_WIDTH = 260;
    public static final int LINE_HEIGHT = 13;

    private static int cachedHudWidth = DEFAULT_WIDTH;
    private static int cachedHudHeight = 44;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "lyrics_hud"), LyricsHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen) && !(mc.gui.screen() instanceof AbstractContainerScreen)) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.lyricsHudEnabled) return;
        if (!SpotifyManager.isSpotifyOpen() && !SpotifyManager.isPlaying()) return;

        try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("Spotify: Lyrics HUD")) {
            drawHud(g, s.lyricsHudX, s.lyricsHudY, s.lyricsHudScale, false);
        }
    }

    public static int getHudWidth() {
        return cachedHudWidth > 0 ? cachedHudWidth : DEFAULT_WIDTH;
    }

    public static int getHudHeight() {
        return cachedHudHeight > 0 ? cachedHudHeight : 44;
    }

    public static void drawHud(GuiGraphicsExtractor g, int baseX, int baseY, float scale, boolean isDummy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font font = mc.font;
        if (font == null) return;

        if (scale <= 0.0f) scale = 1.0f;
        BomboConfig.Settings s = BomboConfig.get();

        int linesBefore = s != null ? Math.max(0, s.lyricsHudLinesBefore) : 0;
        int linesAfter = s != null ? Math.max(0, s.lyricsHudLinesAfter) : 2;
        boolean duetSides = s == null || s.lyricsHudDuetSides;
        boolean dynamicColor = s != null && s.lyricsHudDynamicColor;

        int highlightColor = dynamicColor
                ? LyricsManager.getDominantColor()
                : parseColor(s != null ? s.lyricsHudColor : "#9966CC", 0xFF9966CC);

        g.pose().pushMatrix();
        g.pose().translate((float) baseX, (float) baseY);
        g.pose().scale(scale, scale);

        long currentMs = SpotifyManager.getProgressMs() + (s != null ? s.lyricsOffsetMs : 0);
        List<LyricsManager.LyricsLine> allLines = LyricsManager.getLines();
        int activeIdx = LyricsManager.getCurrentLineIndex(currentMs);

        if (isDummy) {
            drawDummyHud(g, font, highlightColor, linesBefore, linesAfter);
            g.pose().popMatrix();
            return;
        }

        if (allLines.isEmpty() || activeIdx < 0) {
            if (SpotifyManager.isPlaying()) {
                String wait = "§7♪ " + SpotifyManager.getCurrentTrack();
                g.fill(0, 0, Math.max(DEFAULT_WIDTH, font.width(wait) + 12), 18, 0x55000000);
                g.text(font, wait, 6, 5, 0xFFB8B2C4, true);
                cachedHudWidth = font.width(wait) + 12;
                cachedHudHeight = 18;
            }
            g.pose().popMatrix();
            return;
        }

        int startIdx = Math.max(0, activeIdx - linesBefore);
        int endIdx = Math.min(allLines.size() - 1, activeIdx + linesAfter);
        int visibleCount = (endIdx - startIdx + 1);

        int maxW = 140;
        for (int i = startIdx; i <= endIdx; i++) {
            LyricsManager.LyricsLine l = allLines.get(i);
            int w = font.width("§l" + l.text()) + (duetSides && "v2".equalsIgnoreCase(l.agent()) ? 28 : 16);
            if (l.backgroundText() != null && !l.backgroundText().isEmpty()) {
                w = Math.max(w, font.width(l.backgroundText()) + 20);
            }
            maxW = Math.max(maxW, w);
        }

        int totalH = 4;
        for (int i = startIdx; i <= endIdx; i++) {
            LyricsManager.LyricsLine l = allLines.get(i);
            totalH += LINE_HEIGHT;
            if (l.backgroundText() != null && !l.backgroundText().isEmpty()) {
                totalH += 10;
            }
        }
        totalH += 4;

        cachedHudWidth = maxW;
        cachedHudHeight = totalH;

        // Translucent backdrop card (can be toggled in /b config)
        boolean showBg = s == null || s.lyricsHudShowBackground;
        if (showBg) {
            int padX = 8;
            int padY = 5;
            g.fill(-padX, -padY, maxW + padX, totalH + padY, 0x77000000);
            g.fill(-padX, -padY, maxW + padX, -padY + 1, 0x22FFFFFF);
            g.fill(-padX, totalH + padY - 1, maxW + padX, totalH + padY, 0x22FFFFFF);
        }

        int drawY = 4;
        for (int i = startIdx; i <= endIdx; i++) {
            LyricsManager.LyricsLine line = allLines.get(i);
            boolean isActive = (i == activeIdx);
            boolean isV2 = duetSides && "v2".equalsIgnoreCase(line.agent());

            int lineX = isV2 ? 24 : 4;

            if (isActive) {
                // Progressive letter-by-letter karaoke wipe
                List<LyricsManager.WordTime> words = line.words();
                if (words != null && !words.isEmpty()) {
                    int curX = lineX;
                    int spaceW = font.width(" ");
                    for (int w = 0; w < words.size(); w++) {
                        LyricsManager.WordTime wt = words.get(w);
                        String wordStr = wt.word();
                        int letters = countLetters(wordStr);
                        long wordDur = Math.max(10L, wt.endMs() - wt.startMs());
                        long perLetterMs = letters > 0 ? (wordDur / letters) : wordDur;

                        for (int c = 0; c < wordStr.length(); c++) {
                            char ch = wordStr.charAt(c);
                            String chStr = String.valueOf(ch);
                            int chW = font.width("§l" + chStr);

                            long letterTargetMs = wt.startMs() + (c * perLetterMs);
                            boolean isLetterActive = currentMs >= letterTargetMs;

                            int chColor = isLetterActive ? highlightColor : 0x66CBD5E1;
                            g.text(font, (isLetterActive ? "§l" : "§7§l") + chStr, curX, drawY, chColor, isLetterActive);
                            curX += chW;
                        }
                        curX += spaceW;
                    }
                } else {
                    // Line-synced smooth reveal based on progress ratio
                    long duration = Math.max(500L, line.endMs() - line.startMs());
                    float progress = Math.max(0.0f, Math.min(1.0f, (float) (currentMs - line.startMs()) / (float) duration));
                    int fullLen = (int) (line.text().length() * progress);

                    String sang = line.text().substring(0, Math.min(line.text().length(), fullLen));
                    String unsang = line.text().substring(Math.min(line.text().length(), fullLen));

                    int sangW = font.width("§l" + sang);
                    g.text(font, "§l" + sang, lineX, drawY, highlightColor, true);
                    g.text(font, "§7§l" + unsang, lineX + sangW, drawY, 0x66CBD5E1, false);
                }

                drawY += LINE_HEIGHT;

                // Background vocals
                if (line.backgroundText() != null && !line.backgroundText().isEmpty()) {
                    g.text(font, "§f§o" + line.backgroundText(), lineX + (isV2 ? 0 : 8), drawY, 0xFFFFFFFF, true);
                    drawY += 10;
                }
            } else {
                int textColor = (i < activeIdx) ? 0xFF6B7280 : 0xFF94A3B8;
                g.text(font, (isV2 ? "§d" : "") + line.text(), lineX, drawY, textColor, false);
                drawY += LINE_HEIGHT;

                if (line.backgroundText() != null && !line.backgroundText().isEmpty()) {
                    g.text(font, "§7§o" + line.backgroundText(), lineX + (isV2 ? 0 : 8), drawY, 0xFF64748B, false);
                    drawY += 10;
                }
            }
        }

        g.pose().popMatrix();
    }

    private static void drawDummyHud(GuiGraphicsExtractor g, Font font, int highlightColor, int linesBefore, int linesAfter) {
        int w = 240;
        int h = 48;
        cachedHudWidth = w;
        cachedHudHeight = h;

        BomboConfig.Settings s = BomboConfig.get();
        boolean showBg = s == null || s.lyricsHudShowBackground;
        if (showBg) {
            g.fill(0, 0, w, h, 0x77000000);
            g.fill(0, 0, w, 1, 0x4438BDF8);
            g.fill(0, h - 1, w, h, 0x4438BDF8);
        }

        long cycle = System.currentTimeMillis() % 3000L;
        String sampleWord = "Baby, ya yo me enteré";
        int activeChars = (int) ((cycle / 3000.0f) * sampleWord.length());
        String sang = sampleWord.substring(0, Math.min(sampleWord.length(), activeChars));
        String unsang = sampleWord.substring(Math.min(sampleWord.length(), activeChars));

        g.text(font, "§l" + sang, 6, 6, highlightColor, true);
        g.text(font, "§7§l" + unsang, 6 + font.width("§l" + sang), 6, 0x66CBD5E1, false);

        g.text(font, "§f§o(Oh- oh oh- oh- oh)", 14, 19, 0xFFFFFFFF, true);
        g.text(font, "§7Se nota cuando me ve'", 6, 31, 0xFF94A3B8, false);
    }

    public static int countLetters(String word) {
        if (word == null) return 0;
        int count = 0;
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    public static int parseColor(String colStr, int defaultColor) {
        if (colStr == null || colStr.trim().isEmpty()) return defaultColor;
        String s = colStr.trim();
        switch (s.toLowerCase(java.util.Locale.ROOT)) {
            case "amethyst": return 0xFF9966CC;
            case "cyan": return 0xFF00E5FF;
            case "gold", "yellow": return 0xFFFFD700;
            case "emerald", "green": return 0xFF10B981;
            case "purple": return 0xFFA855F7;
            case "red": return 0xFFEF4444;
            case "blue": return 0xFF3B82F6;
            case "pink": return 0xFFEC4899;
            case "white": return 0xFFFFFFFF;
            case "aquamarine": return 0xFF7FFFD4;
            case "ruby": return 0xFFE0115F;
            case "coral": return 0xFFFF7F50;
            case "metallic": return 0xFFD4AF37;
            case "pure gold": return 0xFFFFD700;
            case "pure black", "black": return 0xFF000000;
            default: break;
        }
        try {
            return me.bombo.bomboaddons.gui.config.BomboConfigScreen.parseColorArgb(s);
        } catch (Throwable t) {
            return parseHexColor(s, defaultColor);
        }
    }

    public static int parseHexColor(String hex, int def) {
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
