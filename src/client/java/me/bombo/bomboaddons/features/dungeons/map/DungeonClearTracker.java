package me.bombo.bomboaddons.features.dungeons.map;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class DungeonClearTracker {
    public static final Map<String, Integer> playerClears = new ConcurrentHashMap<>();
    private static final Map<Integer, DungeonRoom.Checkmark> previousCheckmarks = new HashMap<>();
    private static final Map<String, Integer> playerLastRoom = new ConcurrentHashMap<>();

    public static void reset() {
        playerClears.clear();
        previousCheckmarks.clear();
        playerLastRoom.clear();
    }

    public static void tick(List<DungeonRoom> rooms) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 1. Update player locations in rooms
        Map<Integer, List<String>> roomToPlayers = new HashMap<>();

        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (name == null || !name.matches("^[a-zA-Z0-9_]{3,16}$")) continue;

                // Check world player instance for position
                Player worldPlayer = mc.level.getPlayerByUUID(info.getProfile().id());
                if (worldPlayer != null) {
                    double px = worldPlayer.getX();
                    double pz = worldPlayer.getZ();

                    for (int i = 0; i < rooms.size(); i++) {
                        DungeonRoom room = rooms.get(i);
                        if (room.isInside(px, pz)) {
                            roomToPlayers.computeIfAbsent(i, k -> new ArrayList<>()).add(name);
                            playerLastRoom.put(name, i);
                            break;
                        }
                    }
                }
            }
        }

        // 2. Check for newly cleared rooms (Transition from NONE/GRAY -> WHITE or GREEN)
        for (int i = 0; i < rooms.size(); i++) {
            DungeonRoom room = rooms.get(i);
            DungeonRoom.Checkmark prev = previousCheckmarks.getOrDefault(i, DungeonRoom.Checkmark.NONE);
            DungeonRoom.Checkmark cur = room.checkmark;

            boolean newlyCleared = (prev == DungeonRoom.Checkmark.NONE || prev == DungeonRoom.Checkmark.GRAY)
                    && (cur == DungeonRoom.Checkmark.WHITE || cur == DungeonRoom.Checkmark.GREEN);

            if (newlyCleared && room.type != DungeonRoom.Type.FAIRY && room.type != DungeonRoom.Type.ENTRANCE) {
                List<String> playersInRoom = roomToPlayers.getOrDefault(i, Collections.emptyList());

                if (!playersInRoom.isEmpty()) {
                    for (String pName : playersInRoom) {
                        int c = playerClears.getOrDefault(pName, 0) + 1;
                        playerClears.put(pName, c);
                        room.clearedBy = pName;
                        room.clearTime = System.currentTimeMillis();

                        if (BomboConfig.get().dungeonMapDebug && mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bMap Debug§8] §aRoom " + room.name + " cleared by §e" + pName + " §a(Total: " + c + ")"));
                        }
                    }
                } else {
                    // Fallback: If no player currently standing in room, check who was in it last
                    String lastPlayer = null;
                    for (Map.Entry<String, Integer> entry : playerLastRoom.entrySet()) {
                        if (entry.getValue() == i) {
                            lastPlayer = entry.getKey();
                            break;
                        }
                    }
                    if (lastPlayer != null) {
                        int c = playerClears.getOrDefault(lastPlayer, 0) + 1;
                        playerClears.put(lastPlayer, c);
                        room.clearedBy = lastPlayer;
                        room.clearTime = System.currentTimeMillis();

                        if (BomboConfig.get().dungeonMapDebug && mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bMap Debug§8] §aRoom " + room.name + " cleared by §e" + lastPlayer + " §7(last seen)"));
                        }
                    }
                }
            }

            previousCheckmarks.put(i, cur);
        }
    }

    public static int getClears(String playerName) {
        return playerClears.getOrDefault(playerName, 0);
    }

    public static String getFormattedClearsSummary() {
        if (playerClears.isEmpty()) return "§7No clears yet";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : playerClears.entrySet()) {
            if (!first) sb.append(" §8| ");
            sb.append("§e").append(entry.getKey()).append(" §a").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}
