package me.bombo.bomboaddons.features.dungeons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonBossManager {

    public enum BossPhase {
        NONE("None"),
        MAXOR("Phase 1 - Maxor"),
        STORM("Phase 2 - Storm"),
        GOLDOR("Phase 3 - Goldor"),
        NECRON("Phase 4 - Necron");

        public final String display;
        BossPhase(String display) {
            this.display = display;
        }
    }

    public enum DungeonClass {
        MAGE("M", "Mage", "aqua", 0x55FFFF),
        BERSERK("B", "Berserk", "red", 0xFF5555),
        ARCHER("A", "Archer", "green", 0x55FF55),
        TANK("T", "Tank", "gray", 0xAAAAAA),
        HEALER("H", "Healer", "light_purple", 0xFF55FF),
        UNKNOWN("?", "Unknown", "white", 0xFFFFFF);

        public final String code;
        public final String fullName;
        public final String colorName;
        public final int defaultHex;

        DungeonClass(String code, String fullName, String colorName, int defaultHex) {
            this.code = code;
            this.fullName = fullName;
            this.colorName = colorName;
            this.defaultHex = defaultHex;
        }

        public static DungeonClass fromCode(String c) {
            if (c == null) return UNKNOWN;
            String upper = c.trim().toUpperCase();
            for (DungeonClass dc : values()) {
                if (dc.code.equals(upper)) return dc;
            }
            return UNKNOWN;
        }

        public static DungeonClass fromName(String name) {
            if (name == null) return UNKNOWN;
            String upper = name.trim().toUpperCase();
            for (DungeonClass dc : values()) {
                if (dc.name().equals(upper) || dc.fullName.toUpperCase().equals(upper)) return dc;
            }
            return UNKNOWN;
        }
    }

    public static class PlayerDungeonInfo {
        public final String name;
        public final DungeonClass dungeonClass;
        public final int level;

        public PlayerDungeonInfo(String name, DungeonClass dungeonClass, int level) {
            this.name = name;
            this.dungeonClass = dungeonClass;
            this.level = level;
        }
    }

    public static class TermWaypoint {
        public final int section;
        public final BlockPos pos;
        public final String type; // term, lever, device
        public final String extra; // ss, light
        public final Set<DungeonClass> classes = new HashSet<>();
        public final boolean isStack;
        public final boolean isBersTank4;
        public boolean completed = false;

        public TermWaypoint(int section, int x, int y, int z, String type, String extra, boolean isStack, boolean isBersTank4, DungeonClass... assignedClasses) {
            this.section = section;
            this.pos = new BlockPos(x, y, z);
            this.type = type;
            this.extra = extra;
            this.isStack = isStack;
            this.isBersTank4 = isBersTank4;
            this.classes.addAll(Arrays.asList(assignedClasses));
        }

        public boolean isAssignedTo(DungeonClass playerClass, boolean i4Done) {
            if (isBersTank4) {
                if (!i4Done) {
                    return playerClass == DungeonClass.TANK;
                } else {
                    return playerClass == DungeonClass.BERSERK;
                }
            }
            if (isStack) {
                return playerClass == DungeonClass.BERSERK || playerClass == DungeonClass.TANK;
            }
            return classes.contains(playerClass);
        }
    }

    // State variables
    private static BossPhase currentPhase = BossPhase.NONE;
    private static int goldorSection = 1; // 1, 2, 3, 4
    private static boolean i4Done = false;
    private static final Map<String, PlayerDungeonInfo> teamClasses = new LinkedHashMap<>();

    // Waypoints definition
    private static final List<TermWaypoint> TERM_WAYPOINTS = new ArrayList<>();
    private static final BlockPos BREAK_START = new BlockPos(54, 63, 114);

    static {
        // Section 1
        TERM_WAYPOINTS.add(new TermWaypoint(1, 111, 113, 73, "term", "", false, false, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(1, 111, 119, 79, "term", "", false, false, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(1, 110, 121, 91, "device", "ss", false, false, DungeonClass.HEALER));
        TERM_WAYPOINTS.add(new TermWaypoint(1, 89, 112, 92, "term", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(1, 89, 122, 101, "term", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(1, 94, 124, 113, "lever", "", false, false, DungeonClass.MAGE));
        TERM_WAYPOINTS.add(new TermWaypoint(1, 106, 124, 113, "lever", "", false, false, DungeonClass.MAGE));

        // Section 2
        TERM_WAYPOINTS.add(new TermWaypoint(2, 68, 109, 121, "term", "", false, false, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 59, 120, 122, "term", "", false, false, DungeonClass.MAGE));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 60, 133, 142, "device", "light", false, false, DungeonClass.HEALER));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 47, 109, 121, "term", "", true, false, DungeonClass.BERSERK, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 39, 108, 143, "term", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 40, 124, 122, "term", "", false, false, DungeonClass.BERSERK));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 27, 124, 127, "lever", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(2, 23, 132, 138, "lever", "", false, false, DungeonClass.HEALER));

        // Section 3
        TERM_WAYPOINTS.add(new TermWaypoint(3, -3, 109, 112, "term", "", false, false, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(3, -3, 119, 93, "term", "", false, false, DungeonClass.HEALER));
        TERM_WAYPOINTS.add(new TermWaypoint(3, 19, 123, 93, "term", "", false, false, DungeonClass.BERSERK));
        TERM_WAYPOINTS.add(new TermWaypoint(3, -3, 109, 77, "term", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(3, -3, 121, 77, "term", "", false, false, DungeonClass.HEALER));
        TERM_WAYPOINTS.add(new TermWaypoint(3, 2, 122, 55, "lever", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(3, 14, 122, 55, "lever", "", false, false, DungeonClass.ARCHER));

        // Section 4
        TERM_WAYPOINTS.add(new TermWaypoint(4, 41, 109, 29, "term", "", false, false, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(4, 44, 121, 29, "term", "", false, false, DungeonClass.ARCHER));
        TERM_WAYPOINTS.add(new TermWaypoint(4, 67, 109, 29, "term", "", false, true, DungeonClass.BERSERK, DungeonClass.TANK));
        TERM_WAYPOINTS.add(new TermWaypoint(4, 72, 115, 48, "term", "", false, false, DungeonClass.HEALER));
        TERM_WAYPOINTS.add(new TermWaypoint(4, 86, 128, 46, "lever", "", false, false, DungeonClass.HEALER));
        TERM_WAYPOINTS.add(new TermWaypoint(4, 84, 121, 34, "lever", "", false, false, DungeonClass.HEALER, DungeonClass.BERSERK));
    }

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("(?<player>\\w+) (activated a (terminal|lever)|completed a device)! \\((?<done>\\d+)/(?<max>\\d+)\\)");
    private static final Pattern SCOREBOARD_CLASS_PATTERN = Pattern.compile("\\[([HMBAThmbat])\\]\\s*([A-Za-z0-9_]+)\\s*(?:\\[Lv(\\d+)\\])?");
    private static final Pattern TAB_CLASS_PATTERN = Pattern.compile("\\[([HMBAThmbat])\\]\\s*([A-Za-z0-9_]+)");
    private static final Pattern CLASS_SELECTED_PATTERN = Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]+)\\s+selected the\\s+([A-Za-z]+)\\s+Class!", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUR_CLASS_STATS_PATTERN = Pattern.compile("^Your\\s+([A-Za-z]+)\\s+stats are doubled", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_STAT_BRACKET_PATTERN = Pattern.compile("^\\[([A-Za-z]+)\\]\\s+(?:Intelligence|Ability Damage|Cooldown Reduction|Health|Defense|Speed|Damage|Crit|Bow Damage)", Pattern.CASE_INSENSITIVE);
    /** Any {@code [BOSS] <Name>: ...} line. Used for the generic clear -> boss transition. */
    private static final Pattern GENERIC_BOSS_PATTERN = Pattern.compile("\\[BOSS\\]\\s*([^:]+):");
    /** The Watcher guards the blood room - it must not count as a floor boss. */
    private static final String WATCHER = "The Watcher";

    private static boolean bloodDoorOpened = false;
    private static boolean leftCrystalPickedUp = false;
    private static boolean rightCrystalPickedUp = false;
    private static boolean leftCrystalPlaced = false;
    private static boolean rightCrystalPlaced = false;

    public static boolean isBloodDoorOpened() {
        return bloodDoorOpened;
    }

    public static BossPhase getCurrentPhase() {
        return currentPhase;
    }

    public static int getGoldorSection() {
        return goldorSection;
    }

    public static boolean isI4Done() {
        return i4Done;
    }

    public static Map<String, PlayerDungeonInfo> getTeamClasses() {
        return teamClasses;
    }

    public static void putPlayerClass(String name, DungeonClass dc, int lvl) {
        if (name == null || name.isEmpty() || dc == DungeonClass.UNKNOWN) return;
        String lower = name.toLowerCase();

        // Check if a shorter or longer matching key exists and remove it to avoid duplicates (e.g. zamasu12 vs zamasu12045)
        String toRemove = null;
        for (String existingKey : teamClasses.keySet()) {
            if (existingKey.equalsIgnoreCase(lower)) {
                toRemove = existingKey;
                break;
            }
            if (lower.startsWith(existingKey) || existingKey.startsWith(lower)) {
                // If the new name is longer/more complete, remove the shorter key
                if (lower.length() >= existingKey.length()) {
                    toRemove = existingKey;
                } else {
                    // Existing key is longer, keep existing key's name but update class/level
                    PlayerDungeonInfo existing = teamClasses.get(existingKey);
                    teamClasses.put(existingKey, new PlayerDungeonInfo(existing.name, dc, lvl > 0 ? lvl : existing.level));
                    return;
                }
            }
        }
        if (toRemove != null) {
            teamClasses.remove(toRemove);
        }
        teamClasses.put(lower, new PlayerDungeonInfo(name, dc, lvl));
    }

    public static DungeonClass getMyClass() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return DungeonClass.UNKNOWN;
        String myName = mc.getUser().getName();
        PlayerDungeonInfo info = teamClasses.get(myName.toLowerCase());
        return info != null ? info.dungeonClass : DungeonClass.UNKNOWN;
    }

    public static void resetAll() {
        currentPhase = BossPhase.NONE;
        goldorSection = 1;
        i4Done = false;
        bloodDoorOpened = false;
        leftCrystalPickedUp = false;
        rightCrystalPickedUp = false;
        leftCrystalPlaced = false;
        rightCrystalPlaced = false;
        breakBlocksBroken = false;
        SkyblockUtils.setDungeonBossActive(false);
        teamClasses.clear();
        resetTermWaypoints();
    }

    public static void onChatMessage(String cleanMessage) {
        if (cleanMessage == null) return;
        Minecraft mc = Minecraft.getInstance();
        BomboConfig.Settings s = BomboConfig.get();

        // Blood Door Opened
        if (cleanMessage.contains("The BLOOD DOOR has been opened!")) {
            bloodDoorOpened = true;
            if (s != null && s.dungeonDebug && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Boss§8] §cBlood door opened detected."));
            }
        }

        // Energy Crystal Events (Maxor P1)
        if (cleanMessage.contains("picked up an Energy Crystal!")) {
            String myName = mc.getUser().getName();
            if (cleanMessage.contains(myName)) {
                DungeonClass myClass = getMyClass();
                if (myClass == DungeonClass.MAGE) {
                    leftCrystalPickedUp = true;
                    leftCrystalPlaced = false;
                } else if (myClass == DungeonClass.BERSERK) {
                    rightCrystalPickedUp = true;
                    rightCrystalPlaced = false;
                } else {
                    // Default to left if not picked up yet, otherwise right
                    if (!leftCrystalPickedUp) {
                        leftCrystalPickedUp = true;
                        leftCrystalPlaced = false;
                    } else {
                        rightCrystalPickedUp = true;
                        rightCrystalPlaced = false;
                    }
                }
            }
        } else if (cleanMessage.contains("Energy Crystals are now active!") || cleanMessage.contains("Energy Crystal is now active!")) {
            if (cleanMessage.contains("1/2")) {
                DungeonClass myClass = getMyClass();
                if (myClass == DungeonClass.MAGE && leftCrystalPickedUp) {
                    leftCrystalPlaced = true;
                    leftCrystalPickedUp = false;
                } else if (myClass == DungeonClass.BERSERK && rightCrystalPickedUp) {
                    rightCrystalPlaced = true;
                    rightCrystalPickedUp = false;
                } else if (leftCrystalPickedUp) {
                    leftCrystalPlaced = true;
                    leftCrystalPickedUp = false;
                } else if (rightCrystalPickedUp) {
                    rightCrystalPlaced = true;
                    rightCrystalPickedUp = false;
                }
            } else if (cleanMessage.contains("2/2")) {
                leftCrystalPlaced = true;
                rightCrystalPlaced = true;
                leftCrystalPickedUp = false;
                rightCrystalPickedUp = false;
            }
        }

        // 0. End of Dungeon Run detection (same as DungeonSecretsTracker)
        if ((cleanMessage.contains("Team Score:") && cleanMessage.contains("(")) || cleanMessage.contains("EXTRA STATS") || cleanMessage.contains("☠ Defeated ") || (cleanMessage.contains("Defeated ") && cleanMessage.contains(" in "))) {
            resetAll();
            if (s != null && s.dungeonDebug && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Boss§8] §cReset dungeon boss state (Run End)."));
            }
            return;
        }

        // 1. Chat Class Selection / Swaps (e.g. "bomboclas selected the Mage Class!", "Your Mage stats are doubled...", "[Mage] Intelligence...")
        Matcher selectMatcher = CLASS_SELECTED_PATTERN.matcher(cleanMessage);
        if (selectMatcher.find()) {
            String pName = selectMatcher.group(1);
            String cName = selectMatcher.group(2);
            DungeonClass dc = DungeonClass.fromName(cName);
            if (dc != DungeonClass.UNKNOWN) {
                int lvl = 50;
                PlayerDungeonInfo existing = teamClasses.get(pName.toLowerCase());
                if (existing != null) lvl = existing.level;
                putPlayerClass(pName, dc, lvl);
                if (s != null && s.dungeonDebug && mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Boss§8] §aUpdated class from chat: §e" + pName + " §a-> §b" + dc.fullName));
                }
            }
        } else if (mc.player != null) {
            String myName = mc.getUser().getName();
            Matcher yourStatsMatcher = YOUR_CLASS_STATS_PATTERN.matcher(cleanMessage);
            if (yourStatsMatcher.find()) {
                String cName = yourStatsMatcher.group(1);
                DungeonClass dc = DungeonClass.fromName(cName);
                if (dc != DungeonClass.UNKNOWN) {
                    int lvl = 50;
                    PlayerDungeonInfo existing = teamClasses.get(myName.toLowerCase());
                    if (existing != null) lvl = existing.level;
                    putPlayerClass(myName, dc, lvl);
                }
            } else {
                Matcher statBracketMatcher = CLASS_STAT_BRACKET_PATTERN.matcher(cleanMessage);
                if (statBracketMatcher.find()) {
                    String cName = statBracketMatcher.group(1);
                    DungeonClass dc = DungeonClass.fromName(cName);
                    if (dc != DungeonClass.UNKNOWN) {
                        int lvl = 50;
                        PlayerDungeonInfo existing = teamClasses.get(myName.toLowerCase());
                        if (existing != null) lvl = existing.level;
                        putPlayerClass(myName, dc, lvl);
                    }
                }
            }
        }

        // 2. Boss Phase Detection
        // Generic transition: a "[BOSS] <Name>:" line (excluding the Watcher, who lives in the
        // blood room) means the floor boss is up, which is what /b area reports as "(boss)".
        java.util.regex.Matcher genericBoss = GENERIC_BOSS_PATTERN.matcher(cleanMessage);
        if (genericBoss.find()) {
            String bossName = genericBoss.group(1).trim();
            if (!WATCHER.equalsIgnoreCase(bossName)) {
                SkyblockUtils.setDungeonBossActive(true);
                // Start the profit-ledger run clock so chest entries carry a real duration.
                String floorTag = SkyblockUtils.getDungeonFloorTag();
                if (floorTag != null) {
                    DungeonProfitLog.noteRunStart(floorTag);
                }
            }
        }

        if (cleanMessage.contains("[BOSS] Maxor:") || cleanMessage.contains("WELL! WELL! WELL! LOOK WHO'S HERE!")) {
            setPhase(BossPhase.MAXOR);
        } else if (cleanMessage.contains("[BOSS] Storm:") || cleanMessage.contains("Pathetic Maxor, just like expected.")) {
            setPhase(BossPhase.STORM);
        } else if (cleanMessage.contains("[BOSS] Goldor:") || cleanMessage.contains("Who dares trespass into my domain?") || cleanMessage.contains("I won't let you break the factory core") || cleanMessage.contains("There is no stopping me down there!")) {
            if (currentPhase != BossPhase.GOLDOR) {
                setPhase(BossPhase.GOLDOR);
                goldorSection = 1;
                i4Done = false;
                resetTermWaypoints();
            }
        } else if (cleanMessage.contains("[BOSS] Necron:") || cleanMessage.contains("You went further than any human before")) {
            setPhase(BossPhase.NECRON);
        }

        // 3. Goldor Progress & i4 Check (Also auto-detect Goldor phase if progress messages arrive)
        Matcher m = PROGRESS_PATTERN.matcher(cleanMessage);
        if (m.find()) {
            if (currentPhase != BossPhase.GOLDOR) {
                setPhase(BossPhase.GOLDOR);
            }
            String player = m.group("player");
            int done = Integer.parseInt(m.group("done"));
            int max = Integer.parseInt(m.group("max"));

            // Check i4 completion: if player is Berserk class and completed a device in Section 1
            if (cleanMessage.contains("completed a device") && goldorSection == 1 && !i4Done) {
                PlayerDungeonInfo pInfo = teamClasses.get(player.toLowerCase());
                if (pInfo != null && pInfo.dungeonClass == DungeonClass.BERSERK) {
                    i4Done = true;
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Boss§8] §ai4 done"));
                    }
                }
            }

            if (s != null && s.dungeonDebug && mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Debug§8] §eProgress: §a" + done + "/" + max + " §7(Area " + goldorSection + ")"));
            }

            if (done >= max && max > 0) {
                if (goldorSection < 4) {
                    goldorSection++;
                    if (s != null && s.dungeonDebug && mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Debug§8] §6Advanced to Phase 3 Area " + goldorSection + "!"));
                    }
                }
            }
        }
    }

    private static void setPhase(BossPhase phase) {
        currentPhase = phase;
        goldorSection = 1;
        i4Done = false;
        resetTermWaypoints();
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.dungeonDebug) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§bDungeon Debug§8] §aEntered " + phase.display + "!"));
            }
        }
    }

    public static void resetTermWaypoints() {
        for (TermWaypoint wp : TERM_WAYPOINTS) {
            wp.completed = false;
        }
    }

    private static boolean breakBlocksBroken = false;

    public static void onClientTick() {
        onClientTick(Minecraft.getInstance());
    }

    public static void onClientTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            resetAll();
            return;
        }

        // Only tick in Dungeons or when dungeonDebug is on
        BomboConfig.Settings s = BomboConfig.get();
        String area = SkyblockUtils.getLocation();
        boolean inDungeons = area != null && (area.toLowerCase().contains("catacombs") || area.toLowerCase().contains("dungeon"));
        if (!inDungeons && (s == null || !s.dungeonDebug)) {
            if (currentPhase != BossPhase.NONE || !teamClasses.isEmpty()) {
                resetAll();
            }
            return;
        }

        updateTeamClasses();

        // 3x3 Break Block state check: If all 9 blocks (or middle block) in the 3x3 break area are air/broken, mark as broken
        if (!breakBlocksBroken) {
            int airCount = 0;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos bp = new BlockPos(BREAK_START.getX() + dx, BREAK_START.getY(), BREAK_START.getZ() + dz);
                    if (mc.level.getBlockState(bp).isAir()) {
                        airCount++;
                    }
                }
            }
            if (airCount >= 5) {
                breakBlocksBroken = true;
            }
        }

        if (currentPhase == BossPhase.GOLDOR || (s != null && s.dungeonDebug)) {
            // Position-based Goldor section auto-advance fallback
            double px = mc.player.getX();
            double pz = mc.player.getZ();
            double py = mc.player.getY();
            if (py > 100 && py < 150) {
                if (px > 80 && pz < 120 && goldorSection < 1) {
                    goldorSection = 1;
                } else if (px < 80 && pz > 115 && goldorSection < 2) {
                    goldorSection = 2;
                } else if (px < 30 && pz < 115 && goldorSection < 3) {
                    goldorSection = 3;
                } else if (px > 30 && pz < 55 && goldorSection < 4) {
                    goldorSection = 4;
                }
            }

            updateWaypointCompletion(mc);
        }
    }

    public static void updateTeamClasses() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Collect all online player full names from connection
        List<String> onlineNames = new ArrayList<>();
        if (mc.getConnection() != null) {
            for (net.minecraft.client.multiplayer.PlayerInfo p : mc.getConnection().getOnlinePlayers()) {
                if (p.getProfile() != null && p.getProfile().name() != null) {
                    onlineNames.add(p.getProfile().name());
                }
            }
        }

        // Try Scoreboard Sidebar first (contains accurate [LvXX])
        var scoreboard = mc.level.getScoreboard();
        var objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective != null) {
            scoreboard.listPlayerScores(objective).forEach(score -> {
                String ownerName = score.owner();
                net.minecraft.world.scores.PlayerTeam team = scoreboard.getPlayersTeam(ownerName);
                String prefix = team != null ? team.getPlayerPrefix().getString() : "";
                String suffix = team != null ? team.getPlayerSuffix().getString() : "";
                String full = prefix + ownerName + suffix;
                String clean = ChatFormatting.stripFormatting(full);
                if (clean != null) {
                    Matcher m = SCOREBOARD_CLASS_PATTERN.matcher(clean);
                    if (m.find()) {
                        String classCode = m.group(1);
                        String rawName = m.group(2);
                        String lvlStr = m.group(3);
                        int lvl = (lvlStr != null && !lvlStr.isEmpty()) ? Integer.parseInt(lvlStr) : -1;
                        DungeonClass dc = DungeonClass.fromCode(classCode);

                        // Resolve full player name if scoreboard truncated it
                        String fullName = rawName;
                        for (String on : onlineNames) {
                            if (on.equalsIgnoreCase(rawName) || on.toLowerCase().startsWith(rawName.toLowerCase())) {
                                fullName = on;
                                break;
                            }
                        }

                        putPlayerClass(fullName, dc, lvl > 0 ? lvl : 50);
                    }
                }
            });
        }

        // Try Tab List (only set class/level if not already set or without level)
        for (Component comp : SkyblockUtils.getTabListLines()) {
            String clean = ChatFormatting.stripFormatting(comp.getString());
            if (clean == null) continue;
            Matcher m = TAB_CLASS_PATTERN.matcher(clean);
            if (m.find()) {
                String classCode = m.group(1);
                String rawName = m.group(2);
                DungeonClass dc = DungeonClass.fromCode(classCode);
                String fullName = rawName;
                for (String on : onlineNames) {
                    if (on.equalsIgnoreCase(rawName) || on.toLowerCase().startsWith(rawName.toLowerCase())) {
                        fullName = on;
                        break;
                    }
                }
                PlayerDungeonInfo existing = teamClasses.get(fullName.toLowerCase());
                int lvl = existing != null && existing.level > 0 ? existing.level : 50;
                putPlayerClass(fullName, dc, lvl);
            }
        }
    }

    private static void updateWaypointCompletion(Minecraft mc) {
        BomboConfig.Settings s = BomboConfig.get();
        if (mc.level == null) return;

        for (TermWaypoint wp : TERM_WAYPOINTS) {
            if (wp.completed) continue;
            if (currentPhase == BossPhase.GOLDOR && wp.section != goldorSection) continue;

            // 1. If waypoint is a lever, check if the lever block is powered/flipped
            if (wp.type.equals("lever")) {
                try {
                    net.minecraft.world.level.block.state.BlockState bs = mc.level.getBlockState(wp.pos);
                    if (bs.getBlock() instanceof net.minecraft.world.level.block.LeverBlock) {
                        if (bs.hasProperty(net.minecraft.world.level.block.LeverBlock.POWERED) && bs.getValue(net.minecraft.world.level.block.LeverBlock.POWERED)) {
                            wp.completed = true;
                            continue;
                        }
                    }
                } catch (Exception ignored) {}
            }

            double wpX = wp.pos.getX() + 0.5;
            double wpY = wp.pos.getY();
            double wpZ = wp.pos.getZ() + 0.5;

            // 2. Check armor stand labels near waypoint for terminals/devices
            // ONLY check for terminals and devices; do NOT let generic armor stands falsely complete levers
            if (!wp.type.equals("lever")) {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity instanceof ArmorStand stand) {
                        double distSq = stand.distanceToSqr(wpX, wpY, wpZ);
                        if (distSq <= 12.25) { // within 3.5 blocks
                            String name = stand.getName().getString();
                            String custom = stand.hasCustomName() ? stand.getCustomName().getString() : "";
                            String full = (name + " " + custom).toLowerCase();

                            // Explicit completion strings on Hypixel terminals/devices
                            if (full.contains("inactive") || full.contains("not active") || full.contains("click here") || full.contains("locked")) {
                                continue;
                            }
                            if (full.contains("activated") || full.contains("completed")) {
                                wp.completed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    public static void render(LevelRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());
        DungeonClass myClass = getMyClass();

        // 1. Crystal Waypoints & Drop Point (Phase 1 - Maxor)
        if (s.dungeonCrystalWaypoints && (currentPhase == BossPhase.MAXOR || (s.dungeonDebug && mc.player.getY() > 200))) {
            renderCrystalWaypoints(poseStack, collector, camPos, mc, s, myClass);
        }

        // 2. Terminal, Lever, Device Waypoints (Phase 3 - Goldor ONLY)
        if (s.dungeonTerminalWaypoints && (currentPhase == BossPhase.GOLDOR || (s.dungeonDebug && mc.player.getY() < 150))) {
            renderTerminalWaypoints(poseStack, collector, camPos, mc, s, myClass);
        }

        // 3. Break Waypoints (Between Goldor & Necron)
        if (s.dungeonBreakWaypoints && !breakBlocksBroken && (currentPhase == BossPhase.GOLDOR || currentPhase == BossPhase.NECRON || s.dungeonDebug)) {
            renderBreakWaypoints(poseStack, collector, camPos, mc, s);
        }

        // 4. M4/F4 etherwarp helper - only during the floor boss, on the exact floor.
        if (s.m4EtherwarpHelper) {
            String floorTag = SkyblockUtils.getDungeonFloorTag();
            boolean onF4 = "F4".equals(floorTag) || "M4".equals(floorTag);
            if (onF4 && "boss".equals(SkyblockUtils.getDungeonPhase())) {
                renderEtherwarpTarget(poseStack, collector, camPos, s);
            }
        }
    }

    /** Fixed etherwarp landing block used to escape the F4/M4 boss arena. */
    private static final BlockPos M4_ETHERWARP_TARGET = new BlockPos(27, 81, 18);

    private static void renderEtherwarpTarget(PoseStack poseStack, OrderedSubmitNodeCollector collector, Vec3 camPos, BomboConfig.Settings s) {
        double x = M4_ETHERWARP_TARGET.getX() + 0.5 - camPos.x;
        double y = M4_ETHERWARP_TARGET.getY() - camPos.y;
        double z = M4_ETHERWARP_TARGET.getZ() + 0.5 - camPos.z;
        float r = 0.55F;
        float g = 0.27F;
        float b = 1.0F;
        float width = s.tracerWidth > 0 ? s.tracerWidth : 2.0F;

        AABB box = new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1.0, z + 0.5);
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, width));

        if (s.dungeonTracers) {
            Vector3fc look = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawLine(pose.pose(), vc, look.x(), look.y(), look.z(), (float) x, (float) y + 0.5F, (float) z, r, g, b, 0.9F, width));
        }

        BomboRenderUtils.drawText(poseStack, collector, "§5§lEtherwarp", (float) x, (float) y + 1.5F, (float) z, 0x8B45FF, 0.035F, true, true);
    }

    private static boolean hasEndCrystalNear(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        double targetX = pos.getX() + 0.5;
        double targetY = pos.getY();
        double targetZ = pos.getZ() + 0.5;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) {
                double distSq = entity.distanceToSqr(targetX, targetY, targetZ);
                if (distSq <= 12.0) { // within ~3.4 blocks
                    return true;
                }
            }
        }
        return false;
    }

    private static void renderCrystalWaypoints(PoseStack poseStack, OrderedSubmitNodeCollector collector, Vec3 camPos, Minecraft mc, BomboConfig.Settings s, DungeonClass myClass) {
        // Left crystal: (82, 237, 50) -> Drop: (94, 224, 41) [Mage]
        // Right crystal: (64, 237, 50) -> Drop: (52, 224, 41) [Berserk]
        BlockPos leftCrystal = new BlockPos(82, 237, 50);
        BlockPos leftDrop = new BlockPos(94, 224, 41);

        BlockPos rightCrystal = new BlockPos(64, 237, 50);
        BlockPos rightDrop = new BlockPos(52, 224, 41);

        // Check whether EndCrystal entity exists at the crystal spawn spots
        boolean leftCrystalEntityExists = hasEndCrystalNear(mc, leftCrystal);
        boolean rightCrystalEntityExists = hasEndCrystalNear(mc, rightCrystal);

        // Also check inventory slot 8 as an auxiliary confirmation
        boolean hasCrystalInSlot8 = false;
        ItemStack slot8 = mc.player.getInventory().getItem(8); // 9th hotbar slot is index 8
        if (slot8 != null && !slot8.isEmpty()) {
            String name = slot8.getHoverName().getString().toLowerCase();
            if (name.contains("energy crystal") || SkyblockUtils.getSkyblockId(slot8).equalsIgnoreCase("MAXOR_ENERGY_CRYSTAL")) {
                hasCrystalInSlot8 = true;
            }
        }

        int mageColor = BomboRenderUtils.colorNameToHex(s.dungeonMageColor != null ? s.dungeonMageColor : "aqua");
        int bersColor = BomboRenderUtils.colorNameToHex(s.dungeonBersColor != null ? s.dungeonBersColor : "red");

        // Mage gets left crystal
        if (myClass == DungeonClass.MAGE || myClass == DungeonClass.UNKNOWN || s.dungeonShowAllClassWaypoints) {
            if (leftCrystalPlaced) {
                // Crystal already placed into the beam / active, don't show drop waypoint anymore
                if (leftCrystalEntityExists) {
                    // Respawned
                    leftCrystalPlaced = false;
                    leftCrystalPickedUp = false;
                    renderCrystalPickup(poseStack, collector, camPos, leftCrystal, "Left Crystal [Mage]", mageColor, s);
                }
            } else if (leftCrystalPickedUp || hasCrystalInSlot8) {
                // Player picked it up -> show drop point
                renderDropPoint(poseStack, collector, camPos, leftDrop, "Drop Left Crystal", mageColor, s);
            } else {
                // Not picked up yet -> show pickup point if entity is present or default
                if (leftCrystalEntityExists) {
                    renderCrystalPickup(poseStack, collector, camPos, leftCrystal, "Left Crystal [Mage]", mageColor, s);
                }
            }
        }

        // Berserk gets right crystal
        if (myClass == DungeonClass.BERSERK || myClass == DungeonClass.UNKNOWN || s.dungeonShowAllClassWaypoints) {
            if (rightCrystalPlaced) {
                if (rightCrystalEntityExists) {
                    rightCrystalPlaced = false;
                    rightCrystalPickedUp = false;
                    renderCrystalPickup(poseStack, collector, camPos, rightCrystal, "Right Crystal [Berserk]", bersColor, s);
                }
            } else if (rightCrystalPickedUp || hasCrystalInSlot8) {
                renderDropPoint(poseStack, collector, camPos, rightDrop, "Drop Right Crystal", bersColor, s);
            } else {
                if (rightCrystalEntityExists) {
                    renderCrystalPickup(poseStack, collector, camPos, rightCrystal, "Right Crystal [Berserk]", bersColor, s);
                }
            }
        }
    }

    private static void renderCrystalPickup(PoseStack poseStack, OrderedSubmitNodeCollector collector, Vec3 camPos, BlockPos pos, String label, int hexColor, BomboConfig.Settings s) {
        double x = pos.getX() + 0.5 - camPos.x;
        double y = pos.getY() - camPos.y;
        double z = pos.getZ() + 0.5 - camPos.z;
        float r = (float)(hexColor >> 16 & 255) / 255.0F;
        float g = (float)(hexColor >> 8 & 255) / 255.0F;
        float b = (float)(hexColor & 255) / 255.0F;

        AABB box = new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1.0, z + 0.5);
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, s.tracerWidth > 0 ? s.tracerWidth : 2.0F));
        BomboRenderUtils.drawText(poseStack, collector, label, (float)x, (float)y + 1.5F, (float)z, hexColor, 0.03F, true, true);
    }

    private static void renderDropPoint(PoseStack poseStack, OrderedSubmitNodeCollector collector, Vec3 camPos, BlockPos pos, String label, int hexColor, BomboConfig.Settings s) {
        double x = pos.getX() + 0.5 - camPos.x;
        double y = pos.getY() - camPos.y;
        double z = pos.getZ() + 0.5 - camPos.z;
        float r = (float)(hexColor >> 16 & 255) / 255.0F;
        float g = (float)(hexColor >> 8 & 255) / 255.0F;
        float b = (float)(hexColor & 255) / 255.0F;

        AABB box = new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1.0, z + 0.5);
        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, s.tracerWidth > 0 ? s.tracerWidth : 2.0F));

        // Tracer to drop point
        if (s.dungeonTracers) {
            Vector3fc look = Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawLine(pose.pose(), vc, look.x(), look.y(), look.z(), (float)x, (float)y + 0.5F, (float)z, r, g, b, 1.0F, s.tracerWidth > 0 ? s.tracerWidth : 2.0F));
        }

        BomboRenderUtils.drawText(poseStack, collector, "§6§l" + label, (float)x, (float)y + 1.5F, (float)z, hexColor, 0.035F, true, true);
    }

    private static void renderTerminalWaypoints(PoseStack poseStack, OrderedSubmitNodeCollector collector, Vec3 camPos, Minecraft mc, BomboConfig.Settings s, DungeonClass myClass) {
        TermWaypoint closestTracerWp = null;
        double closestDistSq = Double.MAX_VALUE;
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();

        for (TermWaypoint wp : TERM_WAYPOINTS) {
            if (wp.section != goldorSection) continue;
            if (wp.completed) continue;

            boolean assigned = wp.isAssignedTo(myClass, i4Done) || myClass == DungeonClass.UNKNOWN || s.dungeonShowAllClassWaypoints;
            if (!assigned) continue;

            double x = wp.pos.getX() + 0.5 - camPos.x;
            double y = wp.pos.getY() - camPos.y;
            double z = wp.pos.getZ() + 0.5 - camPos.z;

            // Find closest active waypoint for the single tracer
            double dX = (wp.pos.getX() + 0.5) - playerX;
            double dY = wp.pos.getY() - playerY;
            double dZ = (wp.pos.getZ() + 0.5) - playerZ;
            double distSq = dX * dX + dY * dY + dZ * dZ;
            if (distSq < closestDistSq) {
                closestDistSq = distSq;
                closestTracerWp = wp;
            }

            // Class color
            DungeonClass firstClass = wp.classes.isEmpty() ? DungeonClass.UNKNOWN : wp.classes.iterator().next();
            int colorHex = getClassColorHex(firstClass, s);
            float r = (float)(colorHex >> 16 & 255) / 255.0F;
            float g = (float)(colorHex >> 8 & 255) / 255.0F;
            float b = (float)(colorHex & 255) / 255.0F;

            AABB box = new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1.0, z + 0.5);
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, s.tracerWidth > 0 ? s.tracerWidth : 2.0F));

            String title = formatWaypointLabel(wp);
            BomboRenderUtils.drawText(poseStack, collector, title, (float)x, (float)y + 1.4F, (float)z, colorHex, 0.03F, true, true);
        }

        // Draw only ONE tracer to the closest terminal in current section
        if (s.dungeonTracers && closestTracerWp != null) {
            double tx = closestTracerWp.pos.getX() + 0.5 - camPos.x;
            double ty = closestTracerWp.pos.getY() - camPos.y;
            double tz = closestTracerWp.pos.getZ() + 0.5 - camPos.z;
            DungeonClass firstClass = closestTracerWp.classes.isEmpty() ? DungeonClass.UNKNOWN : closestTracerWp.classes.iterator().next();
            int colorHex = getClassColorHex(firstClass, s);
            float r = (float)(colorHex >> 16 & 255) / 255.0F;
            float g = (float)(colorHex >> 8 & 255) / 255.0F;
            float b = (float)(colorHex & 255) / 255.0F;

            Vector3fc look = mc.gameRenderer.mainCamera().forwardVector();
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawLine(pose.pose(), vc, look.x(), look.y(), look.z(), (float)tx, (float)ty + 0.5F, (float)tz, r, g, b, 0.8F, s.tracerWidth > 0 ? s.tracerWidth : 2.0F));
        }
    }

    private static String formatWaypointLabel(TermWaypoint wp) {
        StringBuilder sb = new StringBuilder();
        if (wp.type.equals("device")) {
            sb.append("§dDevice");
            if (!wp.extra.isEmpty()) sb.append(" (").append(wp.extra).append(")");
        } else if (wp.type.equals("lever")) {
            sb.append("§eLever");
        } else {
            sb.append("§aTerminal");
        }
        if (!wp.classes.isEmpty()) {
            sb.append(" §8[");
            int i = 0;
            for (DungeonClass dc : wp.classes) {
                if (i++ > 0) sb.append(", ");
                sb.append(dc.code);
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private static void renderBreakWaypoints(PoseStack poseStack, OrderedSubmitNodeCollector collector, Vec3 camPos, Minecraft mc, BomboConfig.Settings s) {
        // Middle block is (54, 63, 114). Flat 3x3 on X and Z (1 block to sides), 1 block high (Y in [63, 64])
        // X ranges from 54 - 1 = 53 to 54 + 2 = 56
        // Z ranges from 114 - 1 = 113 to 114 + 2 = 116
        // Y ranges from 63 to 64
        int centerX = BREAK_START.getX(); // 54
        int centerY = BREAK_START.getY(); // 63
        int centerZ = BREAK_START.getZ(); // 114

        double minX = (centerX - 1) - camPos.x;
        double minY = centerY - camPos.y;
        double minZ = (centerZ - 1) - camPos.z;
        double maxX = (centerX + 2) - camPos.x;
        double maxY = (centerY + 1.0) - camPos.y;
        double maxZ = (centerZ + 2) - camPos.z;

        int colorHex = 0xFF5555; // Red / Gold break indicator
        float r = 1.0F, g = 0.3F, b = 0.3F;
        AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vc) -> BomboRenderUtils.drawBox(pose.pose(), vc, box, r, g, b, 1.0F, 2.5F));
        BomboRenderUtils.drawText(poseStack, collector, "§c§lBreak 3x3", (float)(minX + 1.5), (float)(maxY + 0.5), (float)(minZ + 1.5), colorHex, 0.035F, true, true);
    }

    public static int getClassColorHex(DungeonClass dc, BomboConfig.Settings s) {
        return switch (dc) {
            case MAGE -> BomboRenderUtils.colorNameToHex(s.dungeonMageColor != null ? s.dungeonMageColor : "aqua");
            case BERSERK -> BomboRenderUtils.colorNameToHex(s.dungeonBersColor != null ? s.dungeonBersColor : "red");
            case ARCHER -> BomboRenderUtils.colorNameToHex(s.dungeonArcherColor != null ? s.dungeonArcherColor : "green");
            case TANK -> BomboRenderUtils.colorNameToHex(s.dungeonTankColor != null ? s.dungeonTankColor : "gray");
            case HEALER -> BomboRenderUtils.colorNameToHex(s.dungeonHealerColor != null ? s.dungeonHealerColor : "light_purple");
            default -> 0xFFFFFF;
        };
    }
}
