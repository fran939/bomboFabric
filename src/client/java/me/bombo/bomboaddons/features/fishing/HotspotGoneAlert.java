package me.bombo.bomboaddons.features.fishing;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

public class HotspotGoneAlert {
    private static UUID lastClosestHotspotUuid = null;
    private static Vec3 lastClosestHotspotPos = null;
    private static int tickCounter = 0;
    private static int debugTickCounter = 0;
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HotspotAlert-Scheduler");
        t.setDaemon(true);
        return t;
    });

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            lastClosestHotspotUuid = null;
            lastClosestHotspotPos = null;
            return;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        boolean enabled = s.hotspotGoneAlert || s.hotspotDebug;
        if (!enabled) {
            lastClosestHotspotUuid = null;
            lastClosestHotspotPos = null;
            return;
        }

        tickCounter++;
        if (tickCounter >= 5) {
            tickCounter = 0;
            trackHotspot(mc, s);
        }

        // 1-second debug logging if enabled
        if (s.hotspotDebug) {
            debugTickCounter++;
            if (debugTickCounter >= 20) {
                debugTickCounter = 0;
                if (lastClosestHotspotPos != null && mc.player != null) {
                    double dist = Math.sqrt(mc.player.position().distanceToSqr(lastClosestHotspotPos));
                    String coordsStr = String.format("x: %.1f, y: %.1f, z: %.1f", lastClosestHotspotPos.x, lastClosestHotspotPos.y, lastClosestHotspotPos.z);
                    Bomboaddons.sendMessage("§8[§3Bombo Debug§8] §eHotspot at " + coordsStr + " §7(dist: §b" + String.format("%.1f", dist) + "m§7)");
                }
            }
        }
    }

    private static void trackHotspot(Minecraft mc, BomboConfig.Settings s) {
        if (mc.player == null || mc.level == null) return;

        Vec3 playerPos = mc.player.position();
        FishingHook hook = mc.player.fishing;
        Vec3 hookPos = (hook != null && hook.isAlive()) ? hook.position() : null;

        ArmorStand bestStand = null;
        double bestDistSq = 225.0; // 15 blocks maximum range from player

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand stand && stand.isAlive()) {
                if (stand.hasCustomName() && stand.getCustomName() != null) {
                    String name = stand.getCustomName().getString().replaceAll("§.", "").trim();
                    if (name.equalsIgnoreCase("HOTSPOT") || name.contains("HOTSPOT") || name.contains("Fishing Speed")) {
                        double distPlayerSq = stand.position().distanceToSqr(playerPos);
                        double distHookSq = hookPos != null ? stand.position().distanceToSqr(hookPos) : Double.MAX_VALUE;
                        double effectiveDistSq = Math.min(distPlayerSq, distHookSq);

                        if (effectiveDistSq < bestDistSq) {
                            bestDistSq = effectiveDistSq;
                            bestStand = stand;
                        }
                    }
                }
            }
        }

        if (bestStand != null) {
            boolean isDifferentStand = (lastClosestHotspotUuid != null && !lastClosestHotspotUuid.equals(bestStand.getUUID()));
            boolean isDifferentPos = (lastClosestHotspotPos != null && lastClosestHotspotPos.distanceToSqr(bestStand.position()) > 16.0);

            if ((isDifferentStand || isDifferentPos) && lastClosestHotspotPos != null) {
                // If player was close to the old hotspot (< 8 blocks) and it moved/disappeared while fishing
                double oldDistSq = mc.player.position().distanceToSqr(lastClosestHotspotPos);
                double newDistSq = mc.player.position().distanceToSqr(bestStand.position());
                if (oldDistSq <= 64.0 && newDistSq > oldDistSq + 16.0) {
                    if (s.hotspotDebug) {
                        Bomboaddons.sendMessage("§8[§3Bombo Debug§8] §cHotspot moved/changed position! Triggering alert.");
                    }
                    triggerAlert(s);
                }
            }

            boolean isNew = (lastClosestHotspotUuid == null || !lastClosestHotspotUuid.equals(bestStand.getUUID()));
            lastClosestHotspotUuid = bestStand.getUUID();
            lastClosestHotspotPos = bestStand.position();

            if (isNew && s.hotspotDebug) {
                String coordsStr = String.format("x: %.1f, y: %.1f, z: %.1f", lastClosestHotspotPos.x, lastClosestHotspotPos.y, lastClosestHotspotPos.z);
                Bomboaddons.sendMessage("§8[§3Bombo Debug§8] §aTracked new Hotspot at " + coordsStr);
            }
        } else if (lastClosestHotspotUuid != null && lastClosestHotspotPos != null) {
            double distSq = mc.player.position().distanceToSqr(lastClosestHotspotPos);
            if (distSq <= 64.0) { // Player was within 8 blocks -> hotspot actually despawned!
                triggerAlert(s);
                lastClosestHotspotUuid = null;
                lastClosestHotspotPos = null;
            } else {
                // Player walked away from the hotspot (> 8 blocks), silently untrack without warning
                if (s.hotspotDebug) {
                    Bomboaddons.sendMessage("§8[§3Bombo Debug§8] §7Untracked hotspot (player moved away).");
                }
                lastClosestHotspotUuid = null;
                lastClosestHotspotPos = null;
            }
        }
    }

    public static void onEntityRemoved(Entity entity) {
        if (entity instanceof ArmorStand stand) {
            if (lastClosestHotspotUuid != null && lastClosestHotspotUuid.equals(stand.getUUID())) {
                Minecraft mc = Minecraft.getInstance();
                BomboConfig.Settings s = BomboConfig.get();
                if (mc.player != null && lastClosestHotspotPos != null && s != null) {
                    // Only trigger if player is still within 8 blocks
                    if (mc.player.position().distanceToSqr(lastClosestHotspotPos) <= 64.0) {
                        UUID expectedUuid = lastClosestHotspotUuid;
                        scheduler.schedule(() -> {
                            mc.execute(() -> {
                                if (mc.player != null && mc.level != null && expectedUuid.equals(lastClosestHotspotUuid)) {
                                    if (mc.player.position().distanceToSqr(lastClosestHotspotPos) <= 64.0) {
                                        triggerAlert(s);
                                    }
                                    lastClosestHotspotUuid = null;
                                    lastClosestHotspotPos = null;
                                }
                            });
                        }, 150, TimeUnit.MILLISECONDS);
                    } else {
                        lastClosestHotspotUuid = null;
                        lastClosestHotspotPos = null;
                    }
                }
            }
        }
    }

    private static void triggerAlert(BomboConfig.Settings s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (s == null || !s.hotspotGoneAlert) {
            if (s != null && s.hotspotDebug) {
                Bomboaddons.sendMessage("§8[§3Bombo Debug§8] §cHotspot is gone! (Alert disabled in settings)");
            }
            return;
        }

        if (s.hotspotDebug) {
            Bomboaddons.sendMessage("§8[§3Bombo Debug§8] §cHotspot is GONE! Triggering alert.");
        }

        if (s.hotspotAlertTitle && mc.gui != null) {
            mc.gui.hud.setTitle(Component.literal("§dHotspot §cis gone!"));
            mc.gui.hud.setSubtitle(Component.literal("§7Time to find another one"));
        }

        if (s.hotspotAlertChat) {
            Bomboaddons.sendMessage("§d[Hotspot] §cHotspot is gone, time to find another one!");
        }

        if (s.hotspotAlertCommand && s.hotspotAlertCommandText != null && !s.hotspotAlertCommandText.trim().isEmpty()) {
            String cmd = s.hotspotAlertCommandText.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (mc.player.connection != null) {
                mc.player.connection.sendCommand(cmd);
            }
        }

        if (s.hotspotAlertSound) {
            int repeats = Math.max(1, s.hotspotAlertSoundCount);
            int delay = Math.max(100, s.hotspotAlertSoundDelay);
            for (int i = 0; i < repeats; i++) {
                int index = i;
                scheduler.schedule(() -> {
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 1.0F, 1.0F);
                        }
                    });
                }, (long) index * delay, TimeUnit.MILLISECONDS);
            }
        }
    }
}
