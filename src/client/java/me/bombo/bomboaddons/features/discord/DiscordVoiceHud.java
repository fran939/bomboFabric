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

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.discordHudEnabled) return;
        if (s.discordHudOnlyInCall && (!DiscordIpcManager.isConnected() || !DiscordIpcManager.isInVoice())) return;

        drawHud(g, s.discordHudX, s.discordHudY, s.discordHudScale, false);
    }

    public static int getHudWidth() {
        return 140;
    }

    public static int getHudHeight() {
        Collection<DiscordIpcManager.DiscordVoiceUser> users = DiscordIpcManager.getVoiceUsers();
        int count = Math.max(1, users.size());
        return 16 + count * 11;
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

        String channel = isDummy ? "General (Voice)" : (DiscordIpcManager.isInVoice() ? DiscordIpcManager.getCurrentChannelName() : "Not in Call");
        List<String> lines = new ArrayList<>();
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
                StringBuilder sb = new StringBuilder();
                sb.append(u.isSpeaking() ? " §a● " : " §8○ ");
                sb.append(u.isSpeaking() ? "§a" : "§f").append(u.displayName());
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
        int totalH = lines.size() * 11 + 4;

        // Render subtle translucent dark background
        g.fill(0, 0, maxW, totalH, 0x88000000);

        int y = 3;
        for (String line : lines) {
            g.text(font, line, 4, y, 0xFFFFFFFF, true);
            y += 11;
        }

        g.pose().popMatrix();
    }
}
