package me.bombo.bomboaddons.features.dungeons.map;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

public class DungeonMapHistory {
    public static class PlayerState {
        public double x;
        public double y;
        public double z;
        public float yaw;

        public PlayerState(double x, double y, double z, float yaw) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }
    }

    public static class Snapshot {
        public long timestamp;
        public Map<String, PlayerState> players = new HashMap<>();

        public Snapshot(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    private static final List<Snapshot> snapshots = new ArrayList<>();
    private static long lastRecordTime = 0L;
    public static float timelineScrubProgress = 1.0f; // 1.0 = LIVE, 0.0 = Start of run
    public static boolean isScrubbing = false;

    public static void reset() {
        synchronized (snapshots) {
            snapshots.clear();
        }
        lastRecordTime = 0L;
        timelineScrubProgress = 1.0f;
        isScrubbing = false;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!DungeonMapScanner.inDungeon && snapshots.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastRecordTime < 1000L) return; // Record every 1 second
        lastRecordTime = now;

        Snapshot snap = new Snapshot(now);

        // Record local player
        snap.players.put(mc.player.getName().getString(), new PlayerState(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot()));

        // Record other players in world
        for (Player p : mc.level.players()) {
            if (p != null && p != mc.player) {
                String name = p.getName().getString();
                snap.players.put(name, new PlayerState(p.getX(), p.getY(), p.getZ(), p.getYRot()));
            }
        }

        // Record tab party players if visible
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String pName = info.getProfile().name();
                if (pName != null && !snap.players.containsKey(pName)) {
                    Player p = mc.level.getPlayerByUUID(info.getProfile().id());
                    if (p != null) {
                        snap.players.put(pName, new PlayerState(p.getX(), p.getY(), p.getZ(), p.getYRot()));
                    }
                }
            }
        }

        synchronized (snapshots) {
            snapshots.add(snap);
            // Cap history to 30 minutes (1800 snapshots)
            if (snapshots.size() > 1800) {
                snapshots.remove(0);
            }
        }
    }

    public static int getSnapshotCount() {
        synchronized (snapshots) {
            return snapshots.size();
        }
    }

    public static Snapshot getSnapshotAt(float progress) {
        synchronized (snapshots) {
            if (snapshots.isEmpty()) return null;
            if (progress >= 1.0f || snapshots.size() == 1) {
                return snapshots.get(snapshots.size() - 1);
            }
            int index = Math.round(progress * (snapshots.size() - 1));
            index = Math.max(0, Math.min(snapshots.size() - 1, index));
            return snapshots.get(index);
        }
    }

    public static long getRunDurationSeconds() {
        synchronized (snapshots) {
            if (snapshots.size() < 2) return 0L;
            return (snapshots.get(snapshots.size() - 1).timestamp - snapshots.get(0).timestamp) / 1000L;
        }
    }

    public static long getScrubbedSecondsAgo(float progress) {
        synchronized (snapshots) {
            if (snapshots.size() < 2 || progress >= 1.0f) return 0L;
            long latest = snapshots.get(snapshots.size() - 1).timestamp;
            Snapshot target = getSnapshotAt(progress);
            if (target == null) return 0L;
            return Math.max(0L, (latest - target.timestamp) / 1000L);
        }
    }
}
