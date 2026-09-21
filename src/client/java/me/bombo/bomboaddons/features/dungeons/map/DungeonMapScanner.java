package me.bombo.bomboaddons.features.dungeons.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class DungeonMapScanner {
    public static class RoomDefinition {
        public String name;
        public String type;
        public int secrets;
        public List<Integer> cores = new ArrayList<>();
        public int crypts;
        public String shape;
        public String doors;
    }

    private static final Map<Integer, RoomDefinition> coreToDefinition = new HashMap<>();
    private static final Map<String, RoomDefinition> nameToDefinition = new HashMap<>();

    public static final DungeonRoom[] roomGrid = new DungeonRoom[36];
    public static final List<DungeonRoom> rooms = new ArrayList<>();
    public static final DungeonDoor[] horizontalDoors = new DungeonDoor[30]; // 5x6
    public static final DungeonDoor[] verticalDoors = new DungeonDoor[30];   // 6x5

    public static boolean inDungeon = false;
    public static String currentFloor = "";
    private static long lastScanTime = 0L;
    private static long lastCoreScan = 0L;

    static {
        loadDefinitions();
        reset();
    }

    public static void loadDefinitions() {
        try (InputStream is = DungeonMapScanner.class.getResourceAsStream("/assets/bomboaddons/dungeons/rooms.json")) {
            if (is != null) {
                JsonArray arr = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    RoomDefinition def = new RoomDefinition();
                    def.name = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
                    def.type = obj.has("type") ? obj.get("type").getAsString() : "normal";
                    def.secrets = obj.has("secrets") ? obj.get("secrets").getAsInt() : 0;
                    def.crypts = obj.has("crypts") ? obj.get("crypts").getAsInt() : 0;
                    def.shape = obj.has("shape") ? obj.get("shape").getAsString() : "1x1";
                    def.doors = obj.has("doors") ? obj.get("doors").getAsString() : null;

                    if (obj.has("cores")) {
                        for (JsonElement c : obj.getAsJsonArray("cores")) {
                            int core = c.getAsInt();
                            def.cores.add(core);
                            coreToDefinition.put(core, def);
                        }
                    }
                    nameToDefinition.put(def.name.toLowerCase(), def);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void reset() {
        rooms.clear();
        for (int i = 0; i < 36; i++) {
            int gx = i % 6;
            int gz = i / 6;
            DungeonRoom room = new DungeonRoom(gx, gz);
            roomGrid[i] = room;
            rooms.add(room);
        }
        for (int i = 0; i < 30; i++) {
            int gx = i % 5;
            int gz = i / 5;
            horizontalDoors[i] = new DungeonDoor(gx, gz, true, DungeonDoor.Type.NONE);
        }
        for (int i = 0; i < 30; i++) {
            int gx = i % 6;
            int gz = i / 6;
            verticalDoors[i] = new DungeonDoor(gx, gz, false, DungeonDoor.Type.NONE);
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            inDungeon = false;
            return;
        }

        // Check if player is in Catacombs via Sidebar
        boolean onCatacombs = false;
        net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
        net.minecraft.world.scores.Objective objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective != null) {
            for (String line : SkyblockUtils.getSidebarLines(scoreboard, objective)) {
                String clean = net.minecraft.ChatFormatting.stripFormatting(line);
                if (clean != null && (clean.contains("Catacombs") || clean.contains("The Catacombs") || clean.contains("Cleared:") || clean.contains("Secrets Found:"))) {
                    onCatacombs = true;
                    break;
                }
            }
        }

        if (!onCatacombs && !BomboConfig.get().dungeonMapDebug) {
            if (inDungeon) {
                inDungeon = false;
                reset();
            }
            return;
        }

        inDungeon = true;
        long now = System.currentTimeMillis();

        // Scan held or inventory map data every 100ms
        if (now - lastScanTime > 100L) {
            lastScanTime = now;
            scanMapFromInventory(mc);
        }

        // Scan room cores every 400ms from world blocks (Doogan Pre-Discovery)
        if (now - lastCoreScan > 400L) {
            lastCoreScan = now;
            scanRoomCores(mc);
        }

        // Update clear tracking
        DungeonClearTracker.tick(rooms);
    }

    private static void scanMapFromInventory(Minecraft mc) {
        if (mc.player == null) return;

        ItemStack mapStack = null;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof MapItem) {
                mapStack = stack;
                break;
            }
        }
        if (mapStack == null) return;

        MapId mapId = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID);
        if (mapId == null || mc.level == null) return;

        MapItemSavedData data = mc.level.getMapData(mapId);
        if (data == null || data.colors == null || data.colors.length < 16384) return;

        byte[] colors = data.colors;
        scanMapColors(colors);
    }

    public static void scanMapColors(byte[] colors) {
        int startX = 22;
        int startZ = 22;
        int step = 16;

        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int px = startX + gx * step;
                int pz = startZ + gz * step;
                if (px < 0 || px >= 128 || pz < 0 || pz >= 128) continue;

                int colorIdx = pz * 128 + px;
                if (colorIdx >= colors.length) continue;

                byte c = colors[colorIdx];
                int roomIdx = gz * 6 + gx;
                DungeonRoom room = roomGrid[roomIdx];
                if (room == null) continue;

                // Decode Room Discovery & Types
                switch (c) {
                    case 30 -> { // Green
                        room.isDiscovered = true;
                        if (room.type == DungeonRoom.Type.UNOPENED || room.type == DungeonRoom.Type.UNKNOWN) {
                            room.type = DungeonRoom.Type.ENTRANCE;
                            room.name = "Entrance";
                            room.checkmark = DungeonRoom.Checkmark.GREEN;
                        }
                    }
                    case 63 -> { // Brown (Normal mob room)
                        room.isDiscovered = true;
                        if (room.type == DungeonRoom.Type.UNOPENED || room.type == DungeonRoom.Type.UNKNOWN) {
                            room.type = DungeonRoom.Type.NORMAL;
                        }
                    }
                    case 66 -> { // Purple (Puzzle)
                        room.isDiscovered = true;
                        if (room.type == DungeonRoom.Type.UNOPENED || room.type == DungeonRoom.Type.UNKNOWN) {
                            room.type = DungeonRoom.Type.PUZZLE;
                        }
                    }
                    case 18 -> { // Red / Blood
                        room.isDiscovered = true;
                        if (room.type == DungeonRoom.Type.UNOPENED || room.type == DungeonRoom.Type.UNKNOWN) {
                            room.type = DungeonRoom.Type.BLOOD;
                            room.name = "Blood";
                        }
                    }
                    case 114 -> { // Pink (Fairy)
                        room.isDiscovered = true;
                        if (room.type == DungeonRoom.Type.UNOPENED || room.type == DungeonRoom.Type.UNKNOWN) {
                            room.type = DungeonRoom.Type.FAIRY;
                            room.name = "Fairy";
                            room.checkmark = DungeonRoom.Checkmark.GREEN;
                        }
                    }
                    case 119 -> { // Yellow / Miniboss or Trap
                        room.isDiscovered = true;
                        if (room.type == DungeonRoom.Type.UNOPENED || room.type == DungeonRoom.Type.UNKNOWN) {
                            room.type = DungeonRoom.Type.MINIBOSS;
                        }
                    }
                    default -> {
                        if (c != 0) {
                            room.isDiscovered = true;
                        }
                    }
                }

                // Check center pixels for completion checkmark
                if (room.isDiscovered) {
                    int centerColorIdx = (pz) * 128 + px;
                    if (centerColorIdx < colors.length) {
                        byte sc = colors[centerColorIdx];
                        if (sc == 34) { // White Checkmark
                            room.checkmark = DungeonRoom.Checkmark.WHITE;
                        } else if (sc == 30 && room.type != DungeonRoom.Type.ENTRANCE) { // Green Checkmark
                            room.checkmark = DungeonRoom.Checkmark.GREEN;
                        } else if (sc == 18 && room.type == DungeonRoom.Type.PUZZLE) { // Failed Puzzle
                            room.checkmark = DungeonRoom.Checkmark.FAILED;
                        }
                    }
                }

                // Horizontal Doors & Same-Room Connections
                if (gx < 5) {
                    int doorPx = px + step / 2;
                    int doorIdx = pz * 128 + doorPx;
                    if (doorIdx < colors.length) {
                        byte dc = colors[doorIdx];
                        int hIdx = gz * 5 + gx;
                        DungeonDoor door = horizontalDoors[hIdx];
                        DungeonRoom eastRoom = roomGrid[gz * 6 + (gx + 1)];

                        if (dc == 119) {
                            door.type = DungeonDoor.Type.WITHER;
                        } else if (dc == 18) {
                            door.type = DungeonDoor.Type.BLOOD;
                        } else if (dc == 63 || dc == 30 || (c != 0 && dc == c)) {
                            if (eastRoom != null) {
                                room.connectedEast = true;
                                eastRoom.connectedWest = true;
                                door.type = DungeonDoor.Type.NONE;
                            }
                        } else if (dc != 0) {
                            door.type = DungeonDoor.Type.NORMAL;
                        }
                    }
                }

                // Vertical Doors & Same-Room Connections
                if (gz < 5) {
                    int doorPz = pz + step / 2;
                    int doorIdx = doorPz * 128 + px;
                    if (doorIdx < colors.length) {
                        byte dc = colors[doorIdx];
                        int vIdx = gz * 6 + gx;
                        DungeonDoor door = verticalDoors[vIdx];
                        DungeonRoom southRoom = roomGrid[(gz + 1) * 6 + gx];

                        if (dc == 119) {
                            door.type = DungeonDoor.Type.WITHER;
                        } else if (dc == 18) {
                            door.type = DungeonDoor.Type.BLOOD;
                        } else if (dc == 63 || dc == 30 || (c != 0 && dc == c)) {
                            if (southRoom != null) {
                                room.connectedSouth = true;
                                southRoom.connectedNorth = true;
                                door.type = DungeonDoor.Type.NONE;
                            }
                        } else if (dc != 0) {
                            door.type = DungeonDoor.Type.NORMAL;
                        }
                    }
                }
            }
        }
    }

    private static void scanRoomCores(Minecraft mc) {
        if (mc.level == null) return;

        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int cx = -185 + gx * 32;
                int cz = -185 + gz * 32;
                int cy = 68;

                int roomIdx = gz * 6 + gx;
                DungeonRoom room = roomGrid[roomIdx];
                if (room == null) continue;

                // Hash blocks around room center at y=68 (Devonian 15x15 core hash)
                int hash = calculateCoreHash(mc, cx, cy, cz);
                if (hash != 0 && coreToDefinition.containsKey(hash)) {
                    RoomDefinition def = coreToDefinition.get(hash);
                    room.name = def.name;
                    room.maxSecrets = def.secrets;
                    room.crypts = def.crypts;
                    room.shape = def.shape;

                    String t = def.type.toLowerCase();
                    if (t.contains("puzzle")) {
                        room.type = DungeonRoom.Type.PUZZLE;
                        room.puzzleName = def.name;
                    } else if (t.contains("trap")) {
                        room.type = DungeonRoom.Type.TRAP;
                    } else if (t.contains("yellow") || t.contains("miniboss")) {
                        room.type = DungeonRoom.Type.MINIBOSS;
                    } else if (t.contains("fairy")) {
                        room.type = DungeonRoom.Type.FAIRY;
                    } else if (t.contains("blood")) {
                        room.type = DungeonRoom.Type.BLOOD;
                    } else if (t.contains("entrance")) {
                        room.type = DungeonRoom.Type.ENTRANCE;
                    } else {
                        room.type = DungeonRoom.Type.NORMAL;
                    }
                }
            }
        }

        // Link compound multi-tile rooms together
        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int idx = gz * 6 + gx;
                DungeonRoom r1 = roomGrid[idx];
                if (r1 == null || r1.name == null || r1.name.equalsIgnoreCase("unknown")) continue;

                // Check East neighbor
                if (gx < 5) {
                    DungeonRoom r2 = roomGrid[gz * 6 + (gx + 1)];
                    if (r2 != null && r1.name.equalsIgnoreCase(r2.name)) {
                        r1.connectedEast = true;
                        r2.connectedWest = true;
                        r1.addComponent(gx + 1, gz);
                    }
                }

                // Check South neighbor
                if (gz < 5) {
                    DungeonRoom r2 = roomGrid[(gz + 1) * 6 + gx];
                    if (r2 != null && r1.name.equalsIgnoreCase(r2.name)) {
                        r1.connectedSouth = true;
                        r2.connectedNorth = true;
                        r1.addComponent(gx, gz + 1);
                    }
                }
            }
        }
    }

    private static int calculateCoreHash(Minecraft mc, int cx, int cy, int cz) {
        if (mc.level == null) return 0;
        int hash = 0;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                BlockPos pos = new BlockPos(cx + dx, cy, cz + dz);
                if (!mc.level.isLoaded(pos)) return 0;
                BlockState state = mc.level.getBlockState(pos);
                int id = getLegacyBlockId(state.getBlock());
                hash = (hash * 31) + id;
            }
        }
        return hash;
    }

    public static int getLegacyBlockId(net.minecraft.world.level.block.Block block) {
        if (block == Blocks.AIR) return 0;
        if (block == Blocks.STONE) return 1;
        if (block == Blocks.GRASS_BLOCK) return 2;
        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT) return 3;
        if (block == Blocks.COBBLESTONE) return 4;
        if (block == Blocks.OAK_PLANKS || block == Blocks.SPRUCE_PLANKS || block == Blocks.BIRCH_PLANKS || block == Blocks.JUNGLE_PLANKS || block == Blocks.ACACIA_PLANKS || block == Blocks.DARK_OAK_PLANKS) return 5;
        if (block == Blocks.BEDROCK) return 7;
        if (block == Blocks.WATER) return 9;
        if (block == Blocks.LAVA) return 11;
        if (block == Blocks.SAND) return 12;
        if (block == Blocks.GRAVEL) return 13;
        if (block == Blocks.GOLD_ORE) return 14;
        if (block == Blocks.IRON_ORE) return 15;
        if (block == Blocks.COAL_ORE) return 16;
        if (block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.BIRCH_LOG || block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG) return 17;
        if (block == Blocks.OAK_LEAVES || block == Blocks.SPRUCE_LEAVES || block == Blocks.BIRCH_LEAVES || block == Blocks.JUNGLE_LEAVES || block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES) return 18;
        if (block == Blocks.SPONGE) return 19;
        if (block == Blocks.GLASS) return 20;
        if (block == Blocks.LAPIS_BLOCK) return 22;
        if (block == Blocks.DISPENSER) return 23;
        if (block == Blocks.SANDSTONE) return 24;
        if (block == Blocks.NOTE_BLOCK) return 25;
        if (block == Blocks.GOLD_BLOCK) return 41;
        if (block == Blocks.IRON_BLOCK) return 42;
        if (block == Blocks.BRICKS) return 45;
        if (block == Blocks.TNT) return 46;
        if (block == Blocks.BOOKSHELF) return 47;
        if (block == Blocks.MOSSY_COBBLESTONE) return 48;
        if (block == Blocks.OBSIDIAN) return 49;
        if (block == Blocks.TORCH || block == Blocks.WALL_TORCH) return 50;
        if (block == Blocks.CHEST) return 54;
        if (block == Blocks.DIAMOND_ORE) return 56;
        if (block == Blocks.DIAMOND_BLOCK) return 57;
        if (block == Blocks.CRAFTING_TABLE) return 58;
        if (block == Blocks.FARMLAND) return 60;
        if (block == Blocks.FURNACE) return 61;
        if (block == Blocks.LADDER) return 65;
        if (block == Blocks.RAIL) return 66;
        if (block == Blocks.COBBLESTONE_STAIRS) return 67;
        if (block == Blocks.LEVER) return 69;
        if (block == Blocks.STONE_PRESSURE_PLATE) return 70;
        if (block == Blocks.REDSTONE_ORE) return 73;
        if (block == Blocks.REDSTONE_TORCH || block == Blocks.REDSTONE_WALL_TORCH) return 76;
        if (block == Blocks.STONE_BUTTON) return 77;
        if (block == Blocks.ICE) return 79;
        if (block == Blocks.SNOW_BLOCK) return 80;
        if (block == Blocks.CACTUS) return 81;
        if (block == Blocks.CLAY) return 82;
        if (block == Blocks.JUKEBOX) return 84;
        if (block == Blocks.OAK_FENCE) return 85;
        if (block == Blocks.PUMPKIN || block == Blocks.CARVED_PUMPKIN) return 86;
        if (block == Blocks.NETHERRACK) return 87;
        if (block == Blocks.SOUL_SAND) return 88;
        if (block == Blocks.GLOWSTONE) return 89;
        if (block == Blocks.OAK_TRAPDOOR) return 96;
        if (block == Blocks.STONE_BRICKS || block == Blocks.MOSSY_STONE_BRICKS || block == Blocks.CRACKED_STONE_BRICKS || block == Blocks.CHISELED_STONE_BRICKS) return 98;
        if (block == Blocks.IRON_BARS) return 101;
        if (block == Blocks.GLASS_PANE) return 102;
        if (block == Blocks.VINE) return 106;
        if (block == Blocks.OAK_FENCE_GATE) return 107;
        if (block == Blocks.BRICK_STAIRS) return 108;
        if (block == Blocks.STONE_BRICK_STAIRS) return 109;
        if (block == Blocks.MYCELIUM) return 110;
        if (block == Blocks.LILY_PAD) return 111;
        if (block == Blocks.NETHER_BRICKS) return 112;
        if (block == Blocks.NETHER_BRICK_STAIRS) return 114;
        if (block == Blocks.ENCHANTING_TABLE) return 116;
        if (block == Blocks.END_PORTAL_FRAME) return 120;
        if (block == Blocks.END_STONE) return 121;
        if (block == Blocks.DRAGON_EGG) return 122;
        if (block == Blocks.REDSTONE_LAMP) return 123;
        if (block == Blocks.BEACON) return 138;
        if (block == Blocks.COBBLESTONE_WALL) return 139;
        if (block == Blocks.FLOWER_POT) return 140;
        if (block == Blocks.ANVIL) return 145;
        if (block == Blocks.TRAPPED_CHEST) return 146;
        if (block == Blocks.REDSTONE_BLOCK) return 152;
        if (block == Blocks.QUARTZ_BLOCK) return 155;
        if (block == Blocks.QUARTZ_STAIRS) return 156;
        if (block == Blocks.TERRACOTTA) return 172;
        if (block == Blocks.HAY_BLOCK) return 170;
        if (block == Blocks.PACKED_ICE) return 174;
        if (block == Blocks.PRISMARINE) return 168;
        if (block == Blocks.SEA_LANTERN) return 169;
        if (block == Blocks.RED_SANDSTONE) return 179;
        return Math.abs(block.getDescriptionId().hashCode() % 200) + 1;
    }

    public static DungeonRoom getRoomAt(double worldX, double worldZ) {
        for (DungeonRoom room : rooms) {
            if (room.isInside(worldX, worldZ)) {
                return room;
            }
        }
        return null;
    }
}
