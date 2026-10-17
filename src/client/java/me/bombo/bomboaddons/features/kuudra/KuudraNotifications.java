package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Kuudra in-game notifications:
 * - No Pre: Alert when a teammate's pre didn't spawn.
 * - Crate Priority: Alert specifying optimal next crate pickup spot.
 * - Dropped Chest: Warning when a supply chest is dropped.
 * - Grabbing & Grabbed: Alerts when a crate is being pulled.
 * - Placed: Confirmation when a supply is completed.
 * - Fresh: High-priority notification when you or team member fresh.
 * - All Placed: Alert when 6/6 supplies are placed.
 * - Build Finished: Alert when ballista build hits 100%.
 */
public class KuudraNotifications {

    public static class ActiveNotification {
        public final String title;
        public final String subtitle;
        public final int color;
        public final long expiryTime;

        public ActiveNotification(String title, String subtitle, int color, long durationMs) {
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            this.expiryTime = System.currentTimeMillis() + durationMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    private static final Deque<ActiveNotification> activeNotifs = new ArrayDeque<>();

    public static void post(String title, String subtitle, int color, long durationMs, boolean playDing) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraNotifications) return;
        if (!SkyblockUtils.isInKuudra()) return;

        // Clean expired
        activeNotifs.removeIf(ActiveNotification::isExpired);

        activeNotifs.addLast(new ActiveNotification(title, subtitle, color, durationMs));
        while (activeNotifs.size() > 3) {
            activeNotifs.removeFirst();
        }

        if (playDing && s.kuudraNotifSound) {
            playNotificationSound();
        }
    }

    public static void notifyNoPre(String spotName) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifNoPre) {
            post("§c§lNO PRE! §7(" + spotName + ")", "§eCheck backup crate!", 0xFFFF3333, 4000L, true);
        }
    }

    public static void notifyCratePriority(String nextSpot) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifCratePrio) {
            post("§b§lNEXT CRATE: §e" + nextSpot, "§7Priority routing", 0xFF38BDF8, 3500L, true);
        }
    }

    public static void notifyDroppedChest(String who) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifDropped) {
            post("§c§lCHEST DROPPED!", "§7" + who + " dropped a crate!", 0xFFFF4444, 3000L, true);
        }
    }

    public static void notifyGrabbing(String spot) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifGrabbing) {
            post("§6§lGRABBING CRATE", "§7Spot: §e" + spot, 0xFFF59E0B, 2000L, false);
        }
    }

    public static void notifyPlaced(int count) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifPlaced) {
            post("§a§lSUPPLY PLACED §8(§e" + count + "§7/§e6§8)", "§7Keep piling!", 0xFF10B981, 2500L, true);
        }
    }

    public static void notifyFresh() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifFresh) {
            post("§d§l✨ FRESH! ✨", "§fSpeed & Build boosted!", 0xFFEC4899, 3500L, true);
        }
    }

    public static void notifyAllPlaced() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifAllPlaced) {
            post("§a§lALL SUPPLIES PLACED!", "§fHead to ballista!", 0xFF22C55E, 4000L, true);
        }
    }

    public static void notifyBuildFinished() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.kuudraNotifBuildFinished) {
            post("§6§lBALLISTA COMPLETED!", "§fGet ready to stun!", 0xFFF97316, 4000L, true);
        }
    }

    public static void playNotificationSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.execute(() -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 1.0F));
            });
        }
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        if (activeNotifs.isEmpty()) return;
        activeNotifs.removeIf(ActiveNotification::isExpired);
        if (activeNotifs.isEmpty()) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraNotifications) return;
        if (!SkyblockUtils.isInKuudra()) return;

        int curY = screenH / 4;
        for (ActiveNotification notif : activeNotifs) {
            int titleW = font.width(notif.title);
            int subW = font.width(notif.subtitle);
            int boxW = Math.max(titleW, subW) + 32;
            int boxH = 32;
            int boxX = (screenW - boxW) / 2;

            boolean transparent = s.hudNoBackground;
            if (!transparent) {
                // Liquid Glass Notification Banner
                g.fill(boxX, curY, boxX + boxW, curY + boxH, 0xD0090D16);
                g.outline(boxX, curY, boxW, boxH, notif.color);
            }

            g.centeredText(font, notif.title, screenW / 2, curY + 6, notif.color);
            g.centeredText(font, notif.subtitle, screenW / 2, curY + 18, 0xFFCBD5E1);

            curY += boxH + 8;
        }
    }

    public static void clear() {
        activeNotifs.clear();
    }
}
