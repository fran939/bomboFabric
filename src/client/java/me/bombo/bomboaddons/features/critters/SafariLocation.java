package me.bombo.bomboaddons.features.critters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SafariLocation {

    public enum Where {
        ELSEWHERE,
        ENTRANCE,
        INSIDE
    }

    private static final Pattern AREA_LINE = Pattern.compile("\\s*§.(?<symbol>.) §.(?<area>.*)");
    private static final String NON_AREA_SYMBOLS = "♲☀";
    private static final String TAB_AREA_PREFIX = "Area: ";
    private static final String[] ENTRANCE_WORDS = {"Entrance", "Entry"};

    private static String area;
    private static Where where = Where.ELSEWHERE;
    private static SafariBiome biome;
    private static boolean chatEntered;

    private SafariLocation() {}

    public static boolean inSafari() {
        String sim = me.bombo.bomboaddons.SkyblockUtils.getSimulatedArea();
        if (sim != null) {
            return sim.equalsIgnoreCase("Critter Safari") || sim.toLowerCase().contains("safari");
        }
        String loc = me.bombo.bomboaddons.SkyblockUtils.getLocation();
        if (loc != null && (loc.equalsIgnoreCase("Torrhus Canyon") || loc.toLowerCase().contains("canyon"))) {
            return false;
        }
        String sub = me.bombo.bomboaddons.SkyblockUtils.getSubArea();
        if (sub != null && sub.toLowerCase().contains("entrance")) {
            return false;
        }
        if (loc != null && (loc.equalsIgnoreCase("Safari") || loc.toLowerCase().contains("safari"))) {
            return true;
        }
        return where == Where.INSIDE;
    }

    public static boolean inside() {
        return inSafari();
    }

    public static Where where() {
        return where;
    }

    public static SafariBiome biome() {
        return biome;
    }

    public static String area() {
        return area;
    }

    public static void tick() {
        String stated = statedArea();
        if (stated != null) {
            area = stated;
            where = classify(stated);
            chatEntered = where != Where.ELSEWHERE;
        } else if (critterLabelsNearby()) {
            area = null;
            where = Where.INSIDE;
        } else if (chatEntered) {
            area = null;
            where = Where.INSIDE;
        } else {
            area = null;
            where = Where.ELSEWHERE;
        }

        biome = where == Where.INSIDE ? resolveBiome() : null;
    }

    public static void onChatMessage(String line) {
        if (line.endsWith("entered Critter Safari!")) markEntered();
    }

    public static void markEntered() {
        chatEntered = true;
        if (where != Where.INSIDE) where = Where.INSIDE;
    }

    public static void onWorldChange() {
        chatEntered = false;
        where = Where.ELSEWHERE;
        area = null;
        biome = null;
    }

    private static Where classify(String stated) {
        if (!stated.contains("Safari")) return Where.ELSEWHERE;
        for (String word : ENTRANCE_WORDS) {
            if (stated.contains(word)) return Where.ENTRANCE;
        }
        return biomeFromPosition() != null ? Where.INSIDE : Where.ENTRANCE;
    }

    private static String statedArea() {
        for (String raw : sidebarLines()) {
            Matcher matcher = AREA_LINE.matcher(raw);
            if (!matcher.matches()) continue;
            String symbol = matcher.group("symbol");
            if (!symbol.isEmpty() && NON_AREA_SYMBOLS.indexOf(symbol.charAt(0)) >= 0) continue;
            String name = strip(matcher.group("area"));
            if (!name.isEmpty()) return name;
        }
        for (String entry : tabListEntries()) {
            if (entry.startsWith(TAB_AREA_PREFIX)) {
                String name = entry.substring(TAB_AREA_PREFIX.length()).trim();
                if (!name.isEmpty()) return name;
            }
        }
        return null;
    }

    public static List<String> sidebarLines() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return List.of();

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return List.of();

        List<String> lines = new ArrayList<>();
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
            if (entry.isHidden()) continue;
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            lines.add(team == null
                ? entry.ownerName().getString()
                : PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString());
        }
        return lines;
    }

    public static List<String> tabListEntries() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.player.connection == null) return List.of();

        List<String> entries = new ArrayList<>();
        for (PlayerInfo info : client.player.connection.getOnlinePlayers()) {
            if (info.getTabListDisplayName() == null) continue;
            String text = strip(info.getTabListDisplayName().getString());
            if (!text.isEmpty()) entries.add(text);
        }
        return entries;
    }

    private static SafariBiome resolveBiome() {
        if (area != null) {
            SafariBiome named = SafariBiome.fromAreaName(area);
            if (named != null) return named;
        }
        return biomeFromPosition();
    }

    public static SafariBiome biomeFromPosition() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;
        Vec3 pos = client.player.position();
        SafariBiome direct = SafariBiome.fromCoordinates(pos.x, pos.z);
        if (direct != null) return direct;
        return SafariAreaMap.biomeAt(pos.x, pos.y, pos.z);
    }

    public static boolean critterLabelsNearby() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.hasCustomName()) continue;
            if (Critters.byName(strip(entity.getCustomName().getString())) != null) return true;
        }
        return false;
    }

    public static String strip(String text) {
        return text.replaceAll("§.", "").replaceAll("[\\p{Cf}\\p{Co}]", "").trim();
    }
}
