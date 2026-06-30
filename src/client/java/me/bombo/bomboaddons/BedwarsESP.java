package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BedwarsESP {
    public static final Map<UUID, Integer> PLAYER_COLORS = new ConcurrentHashMap<>();
    private static long lastUpdateMs = 0;

    public static boolean isInBedwars() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return false;

        // Check locraw first
        if (SkyblockUtils.isConnectedToHypixel() && "BEDWARS".equals(BomboaddonsClient.locrawGametype)) {
            return true;
        }

        // Fallback: check scoreboard title / lines
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            String title = ChatFormatting.stripFormatting(sidebar.getDisplayName().getString()).toLowerCase();
            if (title.contains("bed wars") || title.contains("bedwars")) {
                return true;
            }
        }
        return false;
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        // Update at most once every 500ms to save performance
        if (now - lastUpdateMs < 500) {
            return;
        }
        lastUpdateMs = now;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            PLAYER_COLORS.clear();
            return;
        }

        if (!BomboConfig.get().bedwarsEsp) {
            PLAYER_COLORS.clear();
            return;
        }

        if (!isInBedwars()) {
            PLAYER_COLORS.clear();
            return;
        }

        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info.getProfile() == null || info.getProfile().id() == null)
                continue;
            UUID uuid = info.getProfile().id();
            Component displayName = info.getTabListDisplayName();
            if (displayName != null) {
                Integer color = getTeamColorFromComponent(displayName, info.getProfile().name());
                if (color != null) {
                    PLAYER_COLORS.put(uuid, color);
                }
            }
        }
    }

    public static Integer getTeamColorFromComponent(Component component, String username) {
        if (component == null)
            return null;

        final java.util.concurrent.atomic.AtomicReference<Integer> parsedColor = new java.util.concurrent.atomic.AtomicReference<>(
                null);

        // Find color of the text containing player username
        component.visit((style, text) -> {
            if (text != null && text.toLowerCase().contains(username.toLowerCase())) {
                net.minecraft.network.chat.TextColor textColor = style.getColor();
                if (textColor != null) {
                    parsedColor.set(textColor.getValue());
                    return java.util.Optional.of(textColor.getValue());
                }
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);

        if (parsedColor.get() != null) {
            return parsedColor.get();
        }

        // Fallback: find any text color in the component
        component.visit((style, text) -> {
            if (text != null && !text.trim().isEmpty()) {
                net.minecraft.network.chat.TextColor textColor = style.getColor();
                if (textColor != null) {
                    parsedColor.set(textColor.getValue());
                    return java.util.Optional.of(textColor.getValue());
                }
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);

        return parsedColor.get();
    }

    public static boolean isOnOwnTeam(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return false;
        if (player == mc.player)
            return true;

        if (mc.player.getTeam() != null && player.getTeam() != null) {
            if (mc.player.getTeam().isAlliedTo(player.getTeam())) {
                return true;
            }
        }

        Integer ownColor = PLAYER_COLORS.get(mc.player.getUUID());
        Integer playerColor = PLAYER_COLORS.get(player.getUUID());
        if (ownColor != null && playerColor != null && ownColor.equals(playerColor)) {
            return true;
        }

        return false;
    }

    public static boolean shouldGlowEntity(Entity entity) {
        BomboConfig.Settings config = BomboConfig.get();
        if (!config.bedwarsEsp)
            return false;
        if (!(entity instanceof Player player))
            return false;

        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player)
            return false; // Don't glow own player self

        if (!isInBedwars())
            return false;

        boolean isOwnTeam = isOnOwnTeam(player);
        if (isOwnTeam && !config.bedwarsEspOwnTeam) {
            return false;
        }

        // If hideCheats is enabled, enforce line of sight (no seeing through walls)
        if (config.hideCheats) {
            if (mc.player != null && !mc.player.hasLineOfSight(player)) {
                return false;
            }
        }

        return true;
    }

    public static Integer getEntityColor(Entity entity) {
        BomboConfig.Settings config = BomboConfig.get();
        if (!config.bedwarsEsp)
            return null;
        if (!(entity instanceof Player player))
            return null;

        if (!isInBedwars())
            return null;

        // Try getting cached color from tab list parsing
        Integer color = PLAYER_COLORS.get(player.getUUID());
        if (color != null) {
            return color;
        }

        // Fallback: check player's team color
        if (player.getTeam() != null) {
            ChatFormatting format = player.getTeam().getColor();
            if (format != null) {
                net.minecraft.network.chat.TextColor textColor = net.minecraft.network.chat.TextColor
                        .fromLegacyFormat(format);
                if (textColor != null) {
                    return textColor.getValue();
                }
            }
        }

        return null;
    }
}
