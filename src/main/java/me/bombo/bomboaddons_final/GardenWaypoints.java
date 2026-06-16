package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class GardenWaypoints {
    public static class Waypoint {
        public final Vec3 position;
        public final String label;
        public final long creationTime;
        public boolean hasLeftRadius = false;

        public Waypoint(Vec3 position, String label) {
            this.position = position;
            this.label = label;
            this.creationTime = System.currentTimeMillis();
        }
    }

    private static final List<Waypoint> waypoints = new ArrayList<>();

    public static void addWaypoint(Vec3 pos, String label) {
        synchronized (waypoints) {
            waypoints.add(new Waypoint(pos, label));
        }
    }

    public static void clear() {
        synchronized (waypoints) {
            waypoints.clear();
        }
    }

    public static List<Waypoint> getWaypoints() {
        synchronized (waypoints) {
            return new ArrayList<>(waypoints);
        }
    }

    public static void render(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        Vec3 playerPos = mc.player.position();
        long now = System.currentTimeMillis();
        List<Waypoint> toRemove = new ArrayList<>();

        synchronized (waypoints) {
            for (Waypoint wp : waypoints) {
                // Check duration expiration
                if (s.pestWaypointDuration > 0) {
                    if (now - wp.creationTime >= s.pestWaypointDuration * 1000L) {
                        toRemove.add(wp);
                        continue;
                    }
                }

                // Check proximity removal
                double dist = wp.position.distanceTo(playerPos);
                if (s.pestWaypointRemoveOnNear) {
                    if (!wp.hasLeftRadius) {
                        if (dist > 15.0) {
                            wp.hasLeftRadius = true;
                        }
                    } else {
                        if (dist <= 10.0) {
                            toRemove.add(wp);
                            continue;
                        }
                    }
                }
            }
            waypoints.removeAll(toRemove);
        }

        List<Waypoint> activeWaypoints = getWaypoints();
        List<BomboConfig.CustomWaypoint> customWps = new java.util.ArrayList<>();
        List<BomboConfig.CustomWaypoint> activeCustomWps = BomboConfig.get().customWaypoints.get(BomboConfig.get().activeProfile);
        List<BomboConfig.CustomWaypoint> generalCustomWps = BomboConfig.get().customWaypoints.get("General");
        if (activeCustomWps != null) customWps.addAll(activeCustomWps);
        if (generalCustomWps != null && !BomboConfig.get().activeProfile.equals("General")) customWps.addAll(generalCustomWps);

        boolean hasActive = !activeWaypoints.isEmpty();
        boolean hasCustom = !customWps.isEmpty();

        if (!hasActive && !hasCustom) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

        if (hasActive) {
            // Choose a color (we'll use green/aqua/gold depending on type)
            int colorInt = BomboRenderUtils.colorNameToHex("aqua");
            float r = ((colorInt >> 16) & 0xFF) / 255.0f;
            float g = ((colorInt >> 8) & 0xFF) / 255.0f;
            float b = (colorInt & 0xFF) / 255.0f;
            float a = 1.0f;
            float lineWidth = 2.0f;

            for (Waypoint wp : activeWaypoints) {
                double x = wp.position.x - camPos.x;
                double y = wp.position.y - camPos.y;
                double z = wp.position.z - camPos.z;

                // Perspective scaling trick so it renders through walls
                float dist = (float) Math.sqrt(x*x + y*y + z*z);
                float scale = 1.0f;
                if (!s.hideCheats && dist > 0.2f) {
                    scale = 0.2f / dist;
                }

                float boxWidth = 0.5f * scale;
                float boxHeight = 0.5f * scale;

                float scaledX = (float)x * scale;
                float scaledY = (float)y * scale;
                float scaledZ = (float)z * scale;

                VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
                AABB box = new AABB(
                    scaledX - boxWidth, scaledY - boxHeight, scaledZ - boxWidth,
                    scaledX + boxWidth, scaledY + boxHeight, scaledZ + boxWidth
                );
                BomboRenderUtils.drawBox(poseStack, lineBuffer, box, r, g, b, a, lineWidth);

                if (s.pestWaypointBeacon) {
                    float beaconWidth = 0.15f * scale;
                    AABB beaconBox = new AABB(
                        scaledX - beaconWidth, scaledY, scaledZ - beaconWidth,
                        scaledX + beaconWidth, scaledY + (256.0f * scale), scaledZ + beaconWidth
                    );
                    BomboRenderUtils.drawBox(poseStack, lineBuffer, beaconBox, r, g, b, 0.4f, lineWidth);
                }

                // Draw label
                String text = wp.label + " §7(" + (int)dist + "m)";
                BomboRenderUtils.drawText(poseStack, consumers, text, (float)x, (float)y + 0.8f, (float)z, 0xFFFFFF, 0.03f, true, true);
            }
        }

        if (hasCustom) {
            for (BomboConfig.CustomWaypoint wp : customWps) {
                if (!wp.enabled) continue;

                // Check required island
                if (wp.requiredIsland != null && !wp.requiredIsland.trim().isEmpty()) {
                    String currentArea = BomboaddonsClient.currentArea;
                    if (currentArea == null) currentArea = "";
                    String target = wp.requiredIsland.trim().toLowerCase();
                    boolean matched = currentArea.toLowerCase().contains(target);
                    if (!matched && mc.level != null) {
                        var sidebar = mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                        if (sidebar != null) {
                            for (String line : SkyblockUtils.getSidebarLines(mc.level.getScoreboard(), sidebar)) {
                                String clean = line.replaceAll("(?i)§.", "").trim().toLowerCase();
                                if (clean.contains(target)) {
                                    matched = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (!matched) continue;
                }

                double x = wp.x - camPos.x;
                double y = wp.y - camPos.y;
                double z = wp.z - camPos.z;

                float dist = (float) Math.sqrt(x*x + y*y + z*z);

                float scale = 1.0f;
                if (wp.showThroughWalls) {
                    if (!s.hideCheats && dist > 0.2f) {
                        scale = 0.2f / dist;
                    }
                }

                float boxWidth = 0.5f * scale;
                float boxHeight = 0.5f * scale;

                float scaledX = (float)x * scale;
                float scaledY = (float)y * scale;
                float scaledZ = (float)z * scale;

                int colorHex = BomboRenderUtils.colorNameToHex(wp.color != null ? wp.color.toLowerCase() : "aqua");
                float r = ((colorHex >> 16) & 0xFF) / 255.0f;
                float g = ((colorHex >> 8) & 0xFF) / 255.0f;
                float b = (colorHex & 0xFF) / 255.0f;
                float a = 1.0f;
                float lineWidth = 2.0f;

                VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
                AABB box = new AABB(
                    scaledX - boxWidth, scaledY - boxHeight, scaledZ - boxWidth,
                    scaledX + boxWidth, scaledY + boxHeight, scaledZ + boxWidth
                );
                BomboRenderUtils.drawBox(poseStack, lineBuffer, box, r, g, b, a, lineWidth);

                if (wp.showBeacon) {
                    float beaconWidth = 0.15f * scale;
                    AABB beaconBox = new AABB(
                        scaledX - beaconWidth, scaledY, scaledZ - beaconWidth,
                        scaledX + beaconWidth, scaledY + (256.0f * scale), scaledZ + beaconWidth
                    );
                    BomboRenderUtils.drawBox(poseStack, lineBuffer, beaconBox, r, g, b, 0.4f, lineWidth);
                }

                // Draw label
                String text = wp.name + " §7(" + (int)dist + "m)";
                BomboRenderUtils.drawText(poseStack, consumers, text, (float)x, (float)y + 0.8f, (float)z, 0xFFFFFF, 0.03f, true, wp.showThroughWalls);
            }
        }

        // Render Coord Binds Waypoints
        List<BomboConfig.CoordBind> coordWps = new java.util.ArrayList<>();
        if (BomboConfig.get().coordBinds != null) {
            List<BomboConfig.CoordBind> activeCoordBinds = BomboConfig.get().coordBinds.get(BomboConfig.get().activeProfile);
            List<BomboConfig.CoordBind> generalCoordBinds = BomboConfig.get().coordBinds.get("General");
            if (activeCoordBinds != null) {
                for (BomboConfig.CoordBind cb : activeCoordBinds) {
                    if (cb.enabled && cb.showWaypoint && matchesIsland(cb.requiredIsland)) {
                        coordWps.add(cb);
                    }
                }
            }
            if (generalCoordBinds != null && !BomboConfig.get().activeProfile.equals("General")) {
                for (BomboConfig.CoordBind cb : generalCoordBinds) {
                    if (cb.enabled && cb.showWaypoint && matchesIsland(cb.requiredIsland)) {
                        coordWps.add(cb);
                    }
                }
            }
        }

        if (!coordWps.isEmpty()) {
            for (BomboConfig.CoordBind cb : coordWps) {
                double x = cb.x - camPos.x;
                double y = cb.y - camPos.y;
                double z = cb.z - camPos.z;

                float dist = (float) Math.sqrt(x*x + y*y + z*z);
                double rVal = cb.radius <= 0.0 ? 3.0 : cb.radius;

                VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
                int colorHex = BomboRenderUtils.colorNameToHex("light_purple");
                float r = ((colorHex >> 16) & 0xFF) / 255.0f;
                float g = ((colorHex >> 8) & 0xFF) / 255.0f;
                float b = (colorHex & 0xFF) / 255.0f;

                // 1. Draw real-world proc boundary box (non-perspective-scaled)
                AABB boundaryBox = new AABB(
                    x - rVal, y - rVal, z - rVal,
                    x + rVal, y + rVal, z + rVal
                );
                BomboRenderUtils.drawBox(poseStack, lineBuffer, boundaryBox, r, g, b, 0.4f, 1.5f);

                // 2. Draw perspective-scaled center marker box (visible through walls)
                float scale = 1.0f;
                if (!s.hideCheats && dist > 0.2f) {
                    scale = 0.2f / dist;
                }

                float boxWidth = 0.4f * scale;
                float boxHeight = 0.4f * scale;
                float scaledX = (float)x * scale;
                float scaledY = (float)y * scale;
                float scaledZ = (float)z * scale;

                AABB centerBox = new AABB(
                    scaledX - boxWidth, scaledY - boxHeight, scaledZ - boxWidth,
                    scaledX + boxWidth, scaledY + boxHeight, scaledZ + boxWidth
                );
                BomboRenderUtils.drawBox(poseStack, lineBuffer, centerBox, r, g, b, 1.0f, 2.0f);



                // Draw waypoint text label
                String text = "§d[Bind] §f" + cb.command + " §7(" + (int)dist + "m, r=" + String.format("%.1f", rVal) + ")";
                BomboRenderUtils.drawText(poseStack, consumers, text, (float)x, (float)y + 0.8f, (float)z, 0xFFFFFF, 0.03f, true, true);
            }
        }
    }

    private static boolean matchesIsland(String requiredIsland) {
        if (requiredIsland == null || requiredIsland.trim().isEmpty()) return true;
        Minecraft mc = Minecraft.getInstance();
        String currentArea = BomboaddonsClient.currentArea;
        if (currentArea == null) currentArea = "";
        String target = requiredIsland.trim().toLowerCase();
        boolean matched = currentArea.toLowerCase().contains(target);
        if (!matched && mc.level != null) {
            var scoreboard = mc.level.getScoreboard();
            var sidebar = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                for (String line : SkyblockUtils.getSidebarLines(scoreboard, sidebar)) {
                    String clean = line.replaceAll("(?i)§.", "").trim().toLowerCase();
                    if (clean.contains(target)) {
                        matched = true;
                        break;
                    }
                }
            }
        }
        return matched;
    }
}
