package me.bombo.bomboaddons.features.discord;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Renders the Discord Voice Call HUD on screen:
 * Displays the current channel, connected members, speaking indicators (green/gray dots),
 * mute/deafen states, and [LIVE] screenshare indicators.
 */
public class DiscordVoiceHud {

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "discord_hud"), DiscordVoiceHud::render);
    }

    private static long lastBackgroundScan = 0L;
    private static long lastHudUpdate = 0L;
    private static List<String> cachedLines = new ArrayList<>();
    private static int cachedMaxW = 100;
    private static int cachedTotalH = 26;

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;

        long now = System.currentTimeMillis();
        if (now - lastBackgroundScan > 2000L) {
            lastBackgroundScan = now;
            DiscordIpcManager.scanDiscordLogForVoice();
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.discordHudEnabled) return;
        if (s.discordHudOnlyInCall && (!DiscordIpcManager.isConnected() || !DiscordIpcManager.isInVoice())) return;

        try (me.bombo.bomboaddons.PerformanceProfiler.Scope p = me.bombo.bomboaddons.PerformanceProfiler.scope("HUD: Discord Voice")) {
            drawHud(g, s.discordHudX, s.discordHudY, s.discordHudScale, false);
        }
    }

    public static int getHudWidth() {
        return 140;
    }

    public static int getHudHeight() {
        Collection<DiscordIpcManager.DiscordVoiceUser> users = DiscordIpcManager.getVoiceUsers();
        int count = Math.max(1, users.size());
        return 16 + count * 11;
    }

    private static final List<String> cachedUserRowIds = new ArrayList<>();

    public static boolean handleMouseClick(double mouseX, double mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.discordHudEnabled || !DiscordIpcManager.isInVoice()) return false;
        float scale = s.discordHudScale <= 0 ? 1.0f : s.discordHudScale;
        double relX = (mouseX - s.discordHudX) / scale;
        double relY = (mouseY - s.discordHudY) / scale;
        if (relX < 0 || relX > cachedMaxW || relY < 0 || relY > cachedTotalH) return false;

        int lineIdx = (int) ((relY - 3) / 11);
        int userRowIdx = lineIdx - 1;
        if (userRowIdx >= 0 && userRowIdx < cachedUserRowIds.size()) {
            String uid = cachedUserRowIds.get(userRowIdx);
            if (uid != null && !uid.isEmpty() && !uid.equals("self") && !uid.equals(DiscordIpcManager.getMyUserId())) {
                DiscordIpcManager.toggleUserMute(uid);
                return true;
            }
        }
        return false;
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

        long now = System.currentTimeMillis();
        if (now - lastHudUpdate >= 16L || isDummy || cachedLines.isEmpty()) {
            lastHudUpdate = now;
            String channel = isDummy ? "General (Voice)" : (DiscordIpcManager.isInVoice() ? DiscordIpcManager.getCurrentChannelName() : "Not in Call");
            List<String> lines = new ArrayList<>();
            cachedUserRowIds.clear();
            lines.add("§9§lDiscord §8| §b#" + channel);

            if (isDummy) {
                lines.add(" §a● §fPlayer1 §c§l[LIVE]");
                lines.add(" §7○ §fPlayer2 §8[M]");
            } else if (!DiscordIpcManager.isConnected()) {
                lines.add(" §8(Discord Disconnected)");
            } else if (!DiscordIpcManager.isInVoice() || DiscordIpcManager.getVoiceUsers().isEmpty()) {
                lines.add(" §8(Not in voice call)");
            } else {
                for (DiscordIpcManager.DiscordVoiceUser u : DiscordIpcManager.getVoiceUsers()) {
                    cachedUserRowIds.add(u.id());
                    StringBuilder sb = new StringBuilder();
                    sb.append(u.isSpeaking() ? " §a● " : " §8○ ");
                    if (u.isLocallyMuted()) {
                        sb.append("§7§m").append(u.displayName()).append("§r §c[MUTED]");
                    } else {
                        sb.append(u.isSpeaking() ? "§a" : "§f").append(u.displayName());
                    }
                    if (u.isScreenSharing()) {
                        sb.append(" §c§l[LIVE]§r");
                    }
                    if (u.isMuted()) sb.append(" §c[M]");
                    if (u.isDeafened()) sb.append(" §c[D]");
                    lines.add(sb.toString());
                }
            }

            int maxW = 100;
            for (String line : lines) {
                maxW = Math.max(maxW, font.width(line) + 8);
            }
            cachedLines = lines;
            cachedMaxW = maxW;
            cachedTotalH = lines.size() * 11 + 4;
        }

        // Render subtle translucent dark background
        g.fill(0, 0, cachedMaxW, cachedTotalH, 0x88000000);

        int y = 3;
        for (String line : cachedLines) {
            g.text(font, line, 4, y, 0xFFFFFFFF, true);
            y += 11;
        }

        g.pose().popMatrix();
    }
}
