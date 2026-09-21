package me.bombo.bomboaddons.features.dungeons.map;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

public class DungeonMapOverlay {

    public static int lastMapBaseX = 10;
    public static int lastMapBaseY = 10;
    public static int lastMapTotalW = 150;
    public static int lastMapTotalH = 150;
    public static int lastSliderX = 10;
    public static int lastSliderY = 10;
    public static int lastSliderW = 150;
    public static int lastSliderH = 14;

    public static int getMapWidth() {
        BomboConfig.Settings config = BomboConfig.get();
        float scale = config.dungeonMapScale > 0.1f ? config.dungeonMapScale : 1.0f;
        int roomSize = (int) (22 * scale);
        int doorSize = (int) (6 * scale);
        int pad = (int) (6 * scale);
        return 6 * roomSize + 5 * doorSize + pad * 2;
    }

    public static int getMapHeight() {
        BomboConfig.Settings config = BomboConfig.get();
        float scale = config.dungeonMapScale > 0.1f ? config.dungeonMapScale : 1.0f;
        int roomSize = (int) (22 * scale);
        int doorSize = (int) (6 * scale);
        int pad = (int) (6 * scale);
        int gridH = 6 * roomSize + 5 * doorSize;
        return gridH + pad * 2;
    }

    public static void onHudRender(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BomboConfig.Settings config = BomboConfig.get();
        if (!config.dungeonMap) return;

        // Ensure we are inside a dungeon (or debug mode)
        if (!DungeonMapScanner.inDungeon && !config.dungeonMapDebug) return;

        Font font = mc.font;
        float scale = config.dungeonMapScale > 0.1f ? config.dungeonMapScale : 1.0f;
        int baseX = config.dungeonMapX >= 0 ? config.dungeonMapX : 10;
        int baseY = config.dungeonMapY >= 0 ? config.dungeonMapY : 10;

        int roomSize = (int) (22 * scale);
        int doorSize = (int) (6 * scale);
        int pad = (int) (6 * scale);

        int gridW = 6 * roomSize + 5 * doorSize;
        int gridH = 6 * roomSize + 5 * doorSize;

        int totalW = gridW + pad * 2;
        boolean isScreenOpen = (mc.gui.screen() != null);
        int timelineH = (config.dungeonMapTimeline && isScreenOpen) ? (int) (16 * scale) : 0;
        int totalH = gridH + pad * 2 + timelineH;

        lastMapBaseX = baseX;
        lastMapBaseY = baseY;
        lastMapTotalW = totalW;
        lastMapTotalH = totalH;

        // Draw Map Background & Border
        g.fill(baseX, baseY, baseX + totalW, baseY + totalH, 0xEE0A0F1D);
        g.outline(baseX, baseY, totalW, totalH, 0x5500E5FF);

        int mapStartX = baseX + pad;
        int mapStartY = baseY + pad;

        // 1. Render Doorways & Hallway Connectors between open rooms (Background layer)
        renderHallwayConnectors(g, mapStartX, mapStartY, roomSize, doorSize);

        // 2. Render Rooms Background, Merged Gaps & Exterior Borders
        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int rx = mapStartX + gx * (roomSize + doorSize);
                int ry = mapStartY + gz * (roomSize + doorSize);
                int idx = gz * 6 + gx;

                DungeonRoom room = DungeonMapScanner.roomGrid[idx];
                if (room == null) continue;

                boolean isDiscovered = room.isDiscovered || (room.type != DungeonRoom.Type.UNOPENED && room.type != DungeonRoom.Type.UNKNOWN);

                if (!isDiscovered && !config.dungeonMapShowFullMap) {
                    continue; // Skip unopened rooms if full map preview is disabled
                }

                // Devonian Warm Brown for Discovered (0xFF93502A) / Green Entrance (0xFF16A34A)
                // Devonian Dark Chocolate Brown for Undiscovered (0xFF1E140C) with border (0xFF0E0905)
                int roomColor = isDiscovered ? getRoomColor(room.type, config) : 0xFF1E140C;
                int borderColor = isDiscovered ? 0x44FFFFFF : 0xFF0E0905;

                // Base Room tile
                g.fill(rx, ry, rx + roomSize, ry + roomSize, roomColor);

                // Seamless multi-room horizontal & vertical connection merging (for 1x4, 1x2, 2x2, L)
                if (gx < 5) {
                    DungeonRoom eastRoom = DungeonMapScanner.roomGrid[gz * 6 + (gx + 1)];
                    if (eastRoom != null && (room.connectedEast || isSameRoom(room, eastRoom))) {
                        int gapX = rx + roomSize;
                        g.fill(gapX, ry, gapX + doorSize, ry + roomSize, roomColor);
                    }
                }
                if (gz < 5) {
                    DungeonRoom southRoom = DungeonMapScanner.roomGrid[(gz + 1) * 6 + gx];
                    if (southRoom != null && (room.connectedSouth || isSameRoom(room, southRoom))) {
                        int gapY = ry + roomSize;
                        g.fill(rx, gapY, rx + roomSize, gapY + doorSize, roomColor);
                    }
                }
                if (gx < 5 && gz < 5 && room.connectedEast && room.connectedSouth) {
                    int gapX = rx + roomSize;
                    int gapY = ry + roomSize;
                    g.fill(gapX, gapY, gapX + doorSize, gapY + doorSize, roomColor);
                }

                // Draw outer borders only on unconnected exterior edges
                if (!room.connectedNorth) g.fill(rx, ry, rx + roomSize, ry + 1, borderColor);
                if (!room.connectedSouth) g.fill(rx, ry + roomSize - 1, rx + roomSize, ry + roomSize, borderColor);
                if (!room.connectedWest)  g.fill(rx, ry, rx + 1, ry + roomSize, borderColor);
                if (!room.connectedEast)  g.fill(rx + roomSize - 1, ry, rx + roomSize, ry + roomSize, borderColor);
            }
        }

        // 3. Render Doors (Wither, Blood, Normal doors between distinct rooms)
        renderDoors(g, mapStartX, mapStartY, roomSize, doorSize);

        // 4. Render Room Labels, Secrets & Custom Icons
        Set<Integer> renderedRooms = new HashSet<>();
        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int idx = gz * 6 + gx;
                if (renderedRooms.contains(idx)) continue;

                DungeonRoom room = DungeonMapScanner.roomGrid[idx];
                if (room == null) continue;

                boolean isDiscovered = room.isDiscovered || (room.type != DungeonRoom.Type.UNOPENED && room.type != DungeonRoom.Type.UNKNOWN);
                if (!isDiscovered && !config.dungeonMapShowFullMap) continue;

                renderedRooms.add(idx);
                int rx = mapStartX + gx * (roomSize + doorSize);
                int ry = mapStartY + gz * (roomSize + doorSize);

                if (isDiscovered && config.dungeonMapShowCheckmarks && room.checkmark != DungeonRoom.Checkmark.NONE) {
                    if (room.checkmark == DungeonRoom.Checkmark.GREEN) {
                        String check = "✔";
                        int tw = font.width(check);
                        g.text(font, "§a" + check, rx + (roomSize - tw) / 2, ry + (roomSize - 8) / 2, -1, false);
                    } else if (room.checkmark == DungeonRoom.Checkmark.WHITE) {
                        String check = "✔";
                        int tw = font.width(check);
                        g.text(font, "§f" + check, rx + (roomSize - tw) / 2, ry + (roomSize - 8) / 2, -1, false);
                    } else if (room.checkmark == DungeonRoom.Checkmark.FAILED) {
                        String cross = "✖";
                        int tw = font.width(cross);
                        g.text(font, "§c" + cross, rx + (roomSize - tw) / 2, ry + (roomSize - 8) / 2, -1, false);
                    }
                } else {
                    renderRoomLabels(g, font, room, rx, ry, roomSize, isDiscovered);
                }
            }
        }

        // 5. Render Players
        renderPlayers(g, font, mc, mapStartX, mapStartY, gridW, gridH, config);

        // 6. Interactive Timeline Scrubber Bar
        if (config.dungeonMapTimeline && isScreenOpen) {
            renderTimelineScrubber(g, font, mapStartX, mapStartY, gridW, gridH, scale);
        }
    }

    private static void renderHallwayConnectors(GuiGraphicsExtractor g, int mapStartX, int mapStartY, int roomSize, int doorSize) {
        // Horizontal Link Corridors
        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 5; gx++) {
                int hIdx = gz * 5 + gx;
                DungeonDoor door = DungeonMapScanner.horizontalDoors[hIdx];
                if (door == null || door.type == DungeonDoor.Type.NONE) continue;

                int dx = mapStartX + gx * (roomSize + doorSize) + roomSize;
                int dy = mapStartY + gz * (roomSize + doorSize) + (roomSize - doorSize) / 2;

                // Render dark connecting hallway corridor
                g.fill(dx, dy, dx + doorSize, dy + doorSize, 0xFF140D08);
            }
        }

        // Vertical Link Corridors
        for (int gz = 0; gz < 5; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int vIdx = gz * 6 + gx;
                DungeonDoor door = DungeonMapScanner.verticalDoors[vIdx];
                if (door == null || door.type == DungeonDoor.Type.NONE) continue;

                int dx = mapStartX + gx * (roomSize + doorSize) + (roomSize - doorSize) / 2;
                int dy = mapStartY + gz * (roomSize + doorSize) + roomSize;

                // Render dark connecting hallway corridor
                g.fill(dx, dy, dx + doorSize, dy + doorSize, 0xFF140D08);
            }
        }
    }

    private static void renderDoors(GuiGraphicsExtractor g, int mapStartX, int mapStartY, int roomSize, int doorSize) {
        // Horizontal Doors
        for (int gz = 0; gz < 6; gz++) {
            for (int gx = 0; gx < 5; gx++) {
                int hIdx = gz * 5 + gx;
                DungeonDoor door = DungeonMapScanner.horizontalDoors[hIdx];
                if (door == null || door.type == DungeonDoor.Type.NONE || door.type == DungeonDoor.Type.NORMAL) continue;

                DungeonRoom r1 = DungeonMapScanner.roomGrid[gz * 6 + gx];
                DungeonRoom r2 = DungeonMapScanner.roomGrid[gz * 6 + (gx + 1)];
                if (r1 != null && r2 != null && (r1.connectedEast || isSameRoom(r1, r2))) continue;

                int dx = mapStartX + gx * (roomSize + doorSize) + roomSize;
                int dy = mapStartY + gz * (roomSize + doorSize) + (roomSize - doorSize) / 2;

                g.fill(dx, dy, dx + doorSize, dy + doorSize, door.type.color);
                if (door.type == DungeonDoor.Type.WITHER) {
                    g.outline(dx, dy, doorSize, doorSize, 0xFF000000);
                }
            }
        }

        // Vertical Doors
        for (int gz = 0; gz < 5; gz++) {
            for (int gx = 0; gx < 6; gx++) {
                int vIdx = gz * 6 + gx;
                DungeonDoor door = DungeonMapScanner.verticalDoors[vIdx];
                if (door == null || door.type == DungeonDoor.Type.NONE || door.type == DungeonDoor.Type.NORMAL) continue;

                DungeonRoom r1 = DungeonMapScanner.roomGrid[gz * 6 + gx];
                DungeonRoom r2 = DungeonMapScanner.roomGrid[(gz + 1) * 6 + gx];
                if (r1 != null && r2 != null && (r1.connectedSouth || isSameRoom(r1, r2))) continue;

                int dx = mapStartX + gx * (roomSize + doorSize) + (roomSize - doorSize) / 2;
                int dy = mapStartY + gz * (roomSize + doorSize) + roomSize;

                g.fill(dx, dy, dx + doorSize, dy + doorSize, door.type.color);
                if (door.type == DungeonDoor.Type.WITHER) {
                    g.outline(dx, dy, doorSize, doorSize, 0xFF000000);
                }
            }
        }
    }

    private static void renderRoomLabels(GuiGraphicsExtractor g, Font font, DungeonRoom room, int rx, int ry, int roomSize, boolean discovered) {
        String name = room.name;
        if (name == null || name.equalsIgnoreCase("unknown")) {
            name = (room.type == DungeonRoom.Type.ENTRANCE) ? "Entrance" : "?";
        }

        // Puzzle custom icons & symbols
        if (room.puzzleName != null && room.puzzleName.toLowerCase().contains("water")) {
            // Water Board Puzzle Icon
            String icon = "§b♨";
            int twIcon = font.width(icon);
            g.text(font, icon, rx + (roomSize - twIcon) / 2, ry + 2, -1, false);
        }

        // Line 1: Short Name
        String label1 = name;
        String label2 = null;
        if (name.contains(" ")) {
            String[] parts = name.split(" ", 2);
            label1 = parts[0];
            label2 = parts[1];
        }

        if (label1.length() > 6) label1 = label1.substring(0, 5) + ".";

        int textY = ry + 2;
        if (label2 != null) {
            if (label2.length() > 6) label2 = label2.substring(0, 5) + ".";
            int tw1 = font.width(label1);
            g.text(font, "§f" + label1, rx + (roomSize - tw1) / 2, textY, -1, false);
            int tw2 = font.width(label2);
            g.text(font, "§f" + label2, rx + (roomSize - tw2) / 2, textY + 7, -1, false);
            textY += 14;
        } else {
            int tw1 = font.width(label1);
            g.text(font, "§f" + label1, rx + (roomSize - tw1) / 2, textY, -1, false);
            textY += 8;
        }

        // Secrets line e.g. "?/3" or "3"
        if (room.maxSecrets > 0) {
            String secStr = discovered ? ("§e" + room.maxSecrets) : ("§7?§8/§e" + room.maxSecrets);
            int twSec = font.width(secStr);
            g.text(font, secStr, rx + (roomSize - twSec) / 2, textY, -1, false);
        }
    }

    private static void renderPlayers(GuiGraphicsExtractor g, Font font, Minecraft mc, int mapStartX, int mapStartY, int gridW, int gridH, BomboConfig.Settings config) {
        boolean isPlayback = config.dungeonMapTimeline && DungeonMapHistory.timelineScrubProgress < 0.98f;

        if (isPlayback) {
            DungeonMapHistory.Snapshot snap = DungeonMapHistory.getSnapshotAt(DungeonMapHistory.timelineScrubProgress);
            if (snap != null) {
                for (Map.Entry<String, DungeonMapHistory.PlayerState> entry : snap.players.entrySet()) {
                    String name = entry.getKey();
                    DungeonMapHistory.PlayerState ps = entry.getValue();

                    double normX = (ps.x - (-201.0)) / 192.0;
                    double normZ = (ps.z - (-201.0)) / 192.0;

                    if (normX >= 0.0 && normX <= 1.0 && normZ >= 0.0 && normZ <= 1.0) {
                        int dotX = mapStartX + (int) (normX * gridW);
                        int dotY = mapStartY + (int) (normZ * gridH);

                        boolean isSelf = mc.player != null && name.equals(mc.player.getName().getString());
                        int markerColor = isSelf ? 0xCC00E5FF : 0xCCFBBF24;

                        g.fill(dotX - 3, dotY - 3, dotX + 4, dotY + 4, markerColor);
                        g.outline(dotX - 3, dotY - 3, 7, 7, 0x88000000);
                        String initial = name.substring(0, 1).toUpperCase();
                        g.text(font, isSelf ? "§b" + initial : "§e" + initial, dotX - 2, dotY - 3, -1, false);
                    }
                }
            }
        } else {
            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    String name = info.getProfile().name();
                    if (name == null || !name.matches("^[a-zA-Z0-9_]{3,16}$")) continue;

                    Player worldPlayer = mc.level.getPlayerByUUID(info.getProfile().id());
                    if (worldPlayer == null) continue;

                    double px = worldPlayer.getX();
                    double pz = worldPlayer.getZ();

                    double normX = (px - (-201.0)) / 192.0;
                    double normZ = (pz - (-201.0)) / 192.0;

                    if (normX >= 0.0 && normX <= 1.0 && normZ >= 0.0 && normZ <= 1.0) {
                        int dotX = mapStartX + (int) (normX * gridW);
                        int dotY = mapStartY + (int) (normZ * gridH);

                        boolean isSelf = (worldPlayer == mc.player);
                        int markerColor = isSelf ? 0xFF00E5FF : 0xFFFFEB3B;

                        if (config.dungeonMapShowPlayerHeads) {
                            g.fill(dotX - 3, dotY - 3, dotX + 4, dotY + 4, markerColor);
                            g.outline(dotX - 3, dotY - 3, 7, 7, 0xFF000000);
                            String initial = name.substring(0, 1).toUpperCase();
                            g.text(font, isSelf ? "§b" + initial : "§e" + initial, dotX - 2, dotY - 3, -1, false);
                        } else {
                            g.fill(dotX - 2, dotY - 2, dotX + 3, dotY + 3, markerColor);
                            g.outline(dotX - 2, dotY - 2, 5, 5, 0xFF000000);
                        }
                    }
                }
            }
        }
    }

    private static void renderTimelineScrubber(GuiGraphicsExtractor g, Font font, int mapStartX, int mapStartY, int gridW, int gridH, float scale) {
        int sliderX = mapStartX;
        int sliderY = mapStartY + gridH + 4;
        int sliderW = gridW;
        int sliderH = (int) (12 * scale);

        lastSliderX = sliderX;
        lastSliderY = sliderY;
        lastSliderW = sliderW;
        lastSliderH = sliderH;

        // Track background
        g.fill(sliderX, sliderY + sliderH / 2 - 2, sliderX + sliderW, sliderY + sliderH / 2 + 2, 0xFF1E293B);

        // Filled track
        float progress = DungeonMapHistory.timelineScrubProgress;
        int fillW = (int) (sliderW * progress);
        boolean isPlayback = DungeonMapHistory.timelineScrubProgress < 0.98f;
        int trackColor = isPlayback ? 0xFFF59E0B : 0xFF10B981;
        g.fill(sliderX, sliderY + sliderH / 2 - 2, sliderX + fillW, sliderY + sliderH / 2 + 2, trackColor);

        // Scrubber knob
        int knobX = sliderX + fillW;
        int knobY = sliderY + sliderH / 2;
        g.fill(knobX - 3, knobY - 4, knobX + 3, knobY + 4, 0xFFFFFFFF);
        g.outline(knobX - 3, knobY - 4, 6, 8, 0xFF000000);

        // Time Label
        if (isPlayback) {
            long secAgo = DungeonMapHistory.getScrubbedSecondsAgo(progress);
            String timeStr = "§e⏪ T-" + secAgo + "s (Scrubbed)";
            g.text(font, timeStr, sliderX, sliderY - 9, -1, false);
        }
    }

    public static void renderDummyMap(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
        Font font = Minecraft.getInstance().font;
        float s = scale > 0.1f ? scale : 1.0f;
        int roomSize = (int) (22 * s);
        int doorSize = (int) (6 * s);
        int pad = (int) (6 * s);

        int gridW = 6 * roomSize + 5 * doorSize;
        int gridH = 6 * roomSize + 5 * doorSize;
        int totalW = gridW + pad * 2;
        int totalH = gridH + pad * 2;

        g.fill(baseX, baseY, baseX + totalW, baseY + totalH, 0xEE0A0F1D);
        g.outline(baseX, baseY, totalW, totalH, 0x5500E5FF);

        int mapStartX = baseX + pad;
        int mapStartY = baseY + pad;

        // Sample Devonian Layout:
        // Entrance at (3, 0)
        int ex = mapStartX + 3 * (roomSize + doorSize);
        int ey = mapStartY;
        g.fill(ex, ey, ex + roomSize, ey + roomSize, 0xFF16A34A);
        g.outline(ex, ey, roomSize, roomSize, 0x44FFFFFF);
        g.text(font, "§fSpawn", ex + 2, ey + 4, -1, false);

        // 2x2 Museum at (0, 0)
        int mx = mapStartX;
        int my = mapStartY;
        int mw = roomSize * 2 + doorSize;
        int mh = roomSize * 2 + doorSize;
        g.fill(mx, my, mx + mw, my + mh, 0xFF1E140C);
        g.outline(mx, my, mw, mh, 0xFF0E0905);
        g.text(font, "§fMuseum", mx + mw / 2 - 16, my + mh / 2 - 6, -1, false);
        g.text(font, "§7?§8/§e5", mx + mw / 2 - 8, my + mh / 2 + 4, -1, false);

        // 1x2 Pedestal at (4, 0)
        int px = mapStartX + 4 * (roomSize + doorSize);
        int py = mapStartY;
        int ph = roomSize * 2 + doorSize;
        g.fill(px, py, px + roomSize, py + ph, 0xFF1E140C);
        g.outline(px, py, roomSize, ph, 0xFF0E0905);
        g.text(font, "§fPedestal", px + 2, py + ph / 2 - 6, -1, false);
        g.text(font, "§7?§8/§e5", px + 4, py + ph / 2 + 4, -1, false);

        // L-shaped Well at (2, 2)
        int wx = mapStartX + 2 * (roomSize + doorSize);
        int wy = mapStartY + 2 * (roomSize + doorSize);
        g.fill(wx, wy, wx + roomSize * 2 + doorSize, wy + roomSize, 0xFF93502A);
        g.fill(wx + roomSize + doorSize, wy, wx + roomSize * 2 + doorSize, wy + roomSize * 2 + doorSize, 0xFF93502A);
        g.text(font, "§fWell", wx + 4, wy + 4, -1, false);
        g.text(font, "§e7", wx + 4, wy + 12, -1, false);

        // Player marker
        int pDotX = ex + roomSize / 2;
        int pDotY = ey + roomSize / 2;
        g.fill(pDotX - 3, pDotY - 3, pDotX + 4, pDotY + 4, 0xFF00E5FF);
        g.outline(pDotX - 3, pDotY - 3, 7, 7, 0xFF000000);
    }

    public static boolean isSameRoom(DungeonRoom a, DungeonRoom b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        if (a.name != null && !a.name.equalsIgnoreCase("unknown") && a.name.equals(b.name)) return true;
        if (a.type != DungeonRoom.Type.UNOPENED && a.type != DungeonRoom.Type.UNKNOWN && a.type == b.type) {
            return a.type == DungeonRoom.Type.ENTRANCE || a.type == DungeonRoom.Type.BLOOD;
        }
        return false;
    }

    public static int getRoomColor(DungeonRoom.Type type, BomboConfig.Settings config) {
        return switch (type) {
            case ENTRANCE -> parseColorHex(config.dungeonMapColorEntrance, 0xFF16A34A);
            case NORMAL -> parseColorHex(config.dungeonMapColorNormal, 0xFF93502A);
            case PUZZLE -> parseColorHex(config.dungeonMapColorPuzzle, 0xFF8B5CF6);
            case TRAP -> parseColorHex(config.dungeonMapColorTrap, 0xFFEA580C);
            case MINIBOSS -> parseColorHex(config.dungeonMapColorYellow, 0xFFEAB308);
            case FAIRY -> parseColorHex(config.dungeonMapColorFairy, 0xFFEC4899);
            case BLOOD -> parseColorHex(config.dungeonMapColorBlood, 0xFFDC2626);
            case BOSS -> 0xFFB71C1C;
            default -> 0xFF1E140C;
        };
    }

    public static int parseColorHex(String hex, int fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        try {
            String clean = hex.replace("#", "").replace("0x", "").trim();
            if (clean.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(clean, 16));
            } else if (clean.length() == 8) {
                return (int) Long.parseLong(clean, 16);
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    public static boolean handleMouseClick(double mouseX, double mouseY, int button) {
        BomboConfig.Settings config = BomboConfig.get();
        if (!config.dungeonMap || !config.dungeonMapTimeline) return false;

        if (mouseX >= lastSliderX && mouseX <= lastSliderX + lastSliderW &&
            mouseY >= lastSliderY - 8 && mouseY <= lastSliderY + lastSliderH + 8) {
            float progress = (float) ((mouseX - lastSliderX) / (double) lastSliderW);
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            DungeonMapHistory.timelineScrubProgress = progress;
            DungeonMapHistory.isScrubbing = (button == 0);
            return true;
        }
        return false;
    }

    public static void handleMouseDrag(double mouseX, double mouseY) {
        if (DungeonMapHistory.isScrubbing && lastSliderW > 0) {
            float progress = (float) ((mouseX - lastSliderX) / (double) lastSliderW);
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            DungeonMapHistory.timelineScrubProgress = progress;
        }
    }
}
