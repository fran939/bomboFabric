package me.bombo.bomboaddons.features.discord;

import com.mojang.blaze3d.platform.NativeImage;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the Discord Voice Call HUD on screen:
 * Displays the current channel, connected members, speaking indicators (green/gray dots),
 * mute/deafen states, member avatars, and [LIVE] screenshare indicators.
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

    private static final Map<String, Identifier> AVATAR_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> FETCHING_AVATARS = ConcurrentHashMap.newKeySet();

    private static Identifier getOrFetchAvatar(String userId, String avatarHash) {
        if (userId == null || avatarHash == null || avatarHash.isEmpty()) return null;
        String key = userId + "_" + avatarHash;
        Identifier cached = AVATAR_TEXTURE_CACHE.get(key);
        if (cached != null) return cached;

        if (FETCHING_AVATARS.add(key)) {
            CompletableFuture.runAsync(() -> {
                try {
                    String url = "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png?size=32";
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(3))
                            .GET()
                            .build();
                    java.net.http.HttpResponse<byte[]> resp = java.net.http.HttpClient.newHttpClient()
                            .send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                        try (ByteArrayInputStream in = new ByteArrayInputStream(resp.body())) {
                            NativeImage nImg = NativeImage.read(in);
                            if (nImg != null) {
                                Minecraft.getInstance().execute(() -> {
                                    try {
                                        DynamicTexture dynTex = new DynamicTexture(() -> "discord_avatar_" + key, nImg);
                                        Identifier id = Identifier.fromNamespaceAndPath("bomboaddons", "discord_avatar_" + key.toLowerCase(java.util.Locale.ROOT));
                                        Minecraft.getInstance().getTextureManager().register(id, dynTex);
                                        AVATAR_TEXTURE_CACHE.put(key, id);
                                    } catch (Throwable ignored) {}
                                });
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    FETCHING_AVATARS.remove(key);
                }
            });
        }
        return null;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.gui.screen() instanceof HudMoveScreen) return;

        long now = System.currentTimeMillis();
        if (now - lastBackgroundScan > 1000L) {
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
        return cachedMaxW > 0 ? cachedMaxW : 140;
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
            if (uid != null && !uid.isEmpty()) {
                if (uid.equals("self") || uid.equals(DiscordIpcManager.getMyUserId())) {
                    Minecraft mc = Minecraft.getInstance();
                    DiscordIpcManager.toggleSelfMute(mc != null && mc.player != null ? mc.player::sendSystemMessage : null);
                    return true;
                } else {
                    DiscordIpcManager.toggleUserMute(uid);
                    return true;
                }
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
        BomboConfig.Settings s = BomboConfig.get();
        boolean hideBrand = s != null && s.discordHudHideBrand;
        boolean showAvatars = s != null && s.discordHudShowAvatars;
        boolean showServerNick = s == null || s.discordHudShowServerNickname;

        g.pose().pushMatrix();
        g.pose().translate((float) baseX, (float) baseY);
        g.pose().scale(scale, scale);

        long now = System.currentTimeMillis();
        if (now - lastHudUpdate >= 16L || isDummy || cachedLines.isEmpty()) {
            lastHudUpdate = now;
            String channel = isDummy ? "General (Voice)" : (DiscordIpcManager.isInVoice() ? DiscordIpcManager.getCurrentChannelName() : "Not in Call");
            List<String> lines = new ArrayList<>();
            cachedUserRowIds.clear();

            String brandPrefix = hideBrand ? "" : "§9§lDiscord §8| ";
            lines.add(brandPrefix + "§b#" + channel);

            if (isDummy) {
                lines.add(" §a● §fPlayer1 §c§l[LIVE]");
                lines.add(" §7○ §fPlayer2 §8[M]");
                cachedUserRowIds.add("dummy1");
                cachedUserRowIds.add("dummy2");
            } else if (!DiscordIpcManager.isConnected()) {
                lines.add(" §8(Discord Disconnected)");
            } else if (!DiscordIpcManager.isInVoice() || DiscordIpcManager.getVoiceUsers().isEmpty()) {
                lines.add(" §8(Not in voice call)");
            } else {
                java.util.Set<String> seenKeys = new java.util.HashSet<>();
                String myUid = DiscordIpcManager.getMyUserId();
                List<DiscordIpcManager.DiscordVoiceUser> userList = new ArrayList<>(DiscordIpcManager.getVoiceUsers());
                userList.sort((a, b) -> {
                    boolean aSelf = a.id().equals(myUid) || "self".equals(a.id());
                    boolean bSelf = b.id().equals(myUid) || "self".equals(b.id());
                    if (aSelf && !bSelf) return -1;
                    if (!aSelf && bSelf) return 1;
                    String na = (a.username() != null && !a.username().isEmpty()) ? a.username() : a.displayName();
                    String nb = (b.username() != null && !b.username().isEmpty()) ? b.username() : b.displayName();
                    return String.CASE_INSENSITIVE_ORDER.compare(na != null ? na : "", nb != null ? nb : "");
                });

                for (DiscordIpcManager.DiscordVoiceUser u : userList) {
                    String normDisplay = u.displayName() != null ? u.displayName().toLowerCase(java.util.Locale.ROOT).trim() : "";
                    String normUser = u.username() != null ? u.username().toLowerCase(java.util.Locale.ROOT).trim() : "";
                    if ("self".equals(u.id()) && (!myUid.isEmpty() || seenKeys.contains(normDisplay) || seenKeys.contains(normUser))) {
                        continue;
                    }
                    if (seenKeys.contains(u.id()) || (!normDisplay.isEmpty() && seenKeys.contains(normDisplay)) || (!normUser.isEmpty() && seenKeys.contains(normUser))) {
                        continue;
                    }
                    seenKeys.add(u.id());
                    if (!normDisplay.isEmpty()) seenKeys.add(normDisplay);
                    if (!normUser.isEmpty()) seenKeys.add(normUser);

                    cachedUserRowIds.add(u.id());
                    // Always show the real Discord username (handle) across all members
                    String nameToRender = (u.username() != null && !u.username().isEmpty() && !u.username().startsWith("User ("))
                            ? u.username()
                            : (u.displayName() != null && !u.displayName().isEmpty() ? u.displayName() : "User");

                    StringBuilder sb = new StringBuilder();
                    sb.append(u.isSpeaking() ? " §a● " : " §8○ ");
                    if (u.isLocallyMuted()) {
                        sb.append("§7§m").append(nameToRender).append("§r §6[MUTE]");
                    } else {
                        sb.append(u.isSpeaking() ? "§a" : "§f").append(nameToRender);
                    }
                    if (u.isScreenSharing()) {
                        sb.append(" §c§l[LIVE]§r");
                    }
                    if (u.isSelfDeafened()) {
                        sb.append(" §4[§c✕ DEAF§4]");
                    } else if (u.isSelfMuted()) {
                        sb.append(" §6[§e✕ MIC§6]");
                    } else if (u.isDeafened()) {
                        sb.append(" §4[§c✕ DEAF§4]");
                    } else if (u.isMuted()) {
                        sb.append(" §4[§c✕ MIC§4]");
                    }
                    lines.add(sb.toString());
                }
            }

            int extraW = showAvatars ? 12 : 0;
            int maxW = 100;
            for (String line : lines) {
                maxW = Math.max(maxW, font.width(line) + 8 + extraW);
            }
            cachedLines = lines;
            cachedMaxW = maxW;
            cachedTotalH = lines.size() * 11 + 4;
        }

        // Render subtle translucent dark background
        g.fill(0, 0, cachedMaxW, cachedTotalH, 0x88000000);

        int y = 3;
        for (int i = 0; i < cachedLines.size(); i++) {
            String line = cachedLines.get(i);
            int textX = 4;
            if (i > 0 && showAvatars && (i - 1) < cachedUserRowIds.size()) {
                String uid = cachedUserRowIds.get(i - 1);
                DiscordIpcManager.DiscordBotUserInfo botInfo = DiscordIpcManager.getBotUserInfo(uid);
                int avX = 4;
                int avY = y + 1;
                Identifier avTex = (botInfo != null && botInfo.avatar != null) ? getOrFetchAvatar(uid, botInfo.avatar) : null;
                if (avTex != null) {
                    g.blit(avTex, avX, avY, avX + 8, avY + 8, 0.0F, 1.0F, 0.0F, 1.0F);
                } else {
                    g.fill(avX, avY, avX + 8, avY + 8, 0x555865F2);
                }
                textX += 10;
            }
            g.text(font, line, textX, y, 0xFFFFFFFF, true);
            y += 11;
        }

        g.pose().popMatrix();
    }
}
