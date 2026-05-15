package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import me.bombo.bomboaddons_final.mixin.PlayerTabOverlayAccessor;

public class SkyblockUtils {

    public static String getLocation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "Menu";

        // 1. Try Scoreboard Sidebar
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            List<String> sbLines = getSidebarLines(scoreboard, sidebar);
            String loc = parseAreaFromLines(sbLines);
            if (!loc.equals("Unknown")) return loc;
            
            // Check for Lobby marker in scoreboard
            for (String line : sbLines) {
                if (line.toLowerCase().contains("hypixel.net")) return "Lobby";
            }
        } else {
            // No scoreboard sidebar - usually Limbo
            if (mc.getConnection() != null) {
                return "Limbo";
            }
        }

        // 2. Try Tab List Header/Footer
        if (mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList<>();
            for (Component c : tabLines) plainTabLines.add(c.getString());
            
            String loc = parseAreaFromLines(plainTabLines);
            if (!loc.equals("Unknown")) return loc;
            
            PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor) mc.gui.getTabList();
            Component header = tabAccessor.getHeader();
            Component footer = tabAccessor.getFooter();
            if (header != null) {
                String l = parseAreaFromLines(List.of(header.getString()));
                if (!l.equals("Unknown")) return l;
            }
            if (footer != null) {
                String l = parseAreaFromLines(List.of(footer.getString()));
                if (!l.equals("Unknown")) return l;
            }
        }

        return "Unknown";
    }
    
    public static String getSubArea() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (mc.screen != null) {
                String name = mc.screen.getClass().getSimpleName();
                if (name.equals("JoinMultiplayerScreen") || name.equals("MultiplayerScreen")) return "Multiplayer Menu";
                if (name.equals("TitleScreen")) return "Main Menu";
            }
            return "None";
        }

        // 1. Try Scoreboard Sidebar
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null) {
            List<String> sbLines = getSidebarLines(scoreboard, sidebar);
            String sub = parseSubAreaFromLines(sbLines);
            if (!sub.equals("None")) return sub;
        }

        // 2. Try Tab List Header/Footer
        if (mc.getConnection() != null) {
            List<Component> tabLines = getTabListLines();
            List<String> plainTabLines = new ArrayList<>();
            for (Component c : tabLines) plainTabLines.add(c.getString());
            
            String sub = parseSubAreaFromLines(plainTabLines);
            if (!sub.equals("None")) return sub;
            
            PlayerTabOverlayAccessor tabAccessor = (PlayerTabOverlayAccessor) mc.gui.getTabList();
            Component header = tabAccessor.getHeader();
            Component footer = tabAccessor.getFooter();
            if (header != null) {
                String s = parseSubAreaFromLines(List.of(header.getString()));
                if (!s.equals("None")) return s;
            }
            if (footer != null) {
                String s = parseSubAreaFromLines(List.of(footer.getString()));
                if (!s.equals("None")) return s;
            }
        }

        return "None";
    }

    private static String parseSubAreaFromLines(List<String> lines) {
        for (String line : lines) {
            String clean = line.replaceAll("(?i)§.", "").trim();
            
            // Dungeon Floor Detection (e.g. ⏣ The Catacombs (F7))
            if (clean.contains("The Catacombs (")) {
                int start = clean.indexOf("(") + 1;
                int end = clean.indexOf(")");
                if (start > 0 && end > start) {
                    return clean.substring(start, end);
                }
            }

            // Hypixel subareas usually start with these symbols
            if (clean.startsWith("⏣") || clean.startsWith("ф")) {
                return clean.substring(1).trim();
            }
        }
        return "None";
    }

    private static String parseAreaFromLines(List<String> lines) {
        for (String line : lines) {
            String clean = line.replaceAll("(?i)§.", "").trim();
            // Fuzzy search for common location markers
            if (clean.contains("Area:") || clean.contains("Zone:")) {
                return clean.substring(clean.indexOf(":") + 1).trim();
            }
            // Direct matches for common SkyBlock areas
            String lower = clean.toLowerCase();
            if (lower.contains("the garden") || lower.contains("garden")) return "The Garden";
            if (lower.contains("the hub") || lower.contains("hub")) return "The Hub";
            if (lower.contains("private island") || lower.contains("island")) return "Private Island";
            if (lower.contains("the catacombs") || lower.contains("dungeon")) return "Dungeons";
            if (lower.contains("dwarven mines")) return "Dwarven Mines";
            if (lower.contains("crystal hollows")) return "Crystal Hollows";
            if (lower.contains("limbo")) return "Limbo";
            if (lower.contains("lobby")) return "Lobby";
        }
        return "Unknown";
    }

    public static boolean isInGarden() {
        String loc = getLocation();
        return loc.equalsIgnoreCase("The Garden") || loc.toLowerCase().contains("garden");
    }

    public static List<String> getSidebarLines(Scoreboard scoreboard, Objective objective) {
        List<String> lines = new ArrayList<>();
        scoreboard.listPlayerScores(objective).forEach(score -> {
            String ownerName = score.owner();
            PlayerTeam team = scoreboard.getPlayersTeam(ownerName);
            if (team != null) {
                Component fullName = Component.empty()
                    .append(team.getPlayerPrefix())
                    .append(Component.literal(ownerName))
                    .append(team.getPlayerSuffix());
                lines.add(fullName.getString());
            } else {
                lines.add(ownerName);
            }
        });
        Collections.reverse(lines);
        return lines;
    }

    public static List<Component> getTabListLines() {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        if (mc.getConnection() == null) return lines;

        Collection<PlayerInfo> players = mc.getConnection().getOnlinePlayers();
        for (PlayerInfo info : players) {
            Component displayName = info.getTabListDisplayName();
            if (displayName != null) {
                lines.add(displayName);
            } else if (info.getProfile() != null && info.getProfile().name() != null) {
                lines.add(Component.literal(info.getProfile().name()));
            }
        }
        return lines;
    }
}
