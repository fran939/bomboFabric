package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.util.stream.Collectors;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

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
    public static java.util.Map<String, Integer> currentOrderedIndexPerCat = new java.util.HashMap<>();
    public static java.util.Map<String, String> lastOrderedIslandPerCat = new java.util.HashMap<>();

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

    public static int importWaypointsFromClipboard(String data) {
        int count = 0;
        try {
            if (data.startsWith("[Skyblocker-Waypoint-Data-V1]")) {
                data = data.substring("[Skyblocker-Waypoint-Data-V1]".length());
                byte[] decoded = Base64.getDecoder().decode(data);
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(decoded));
                     BufferedReader br = new BufferedReader(new InputStreamReader(gis, "UTF-8"))) {
                    String jsonStr = br.lines().collect(Collectors.joining());
                    JsonElement el = JsonParser.parseString(jsonStr);
                    if (el.isJsonArray()) {
                        for (JsonElement routeEl : el.getAsJsonArray()) {
                            JsonObject route = routeEl.getAsJsonObject();
                            String island = route.has("island") && !route.get("island").isJsonNull() ? route.get("island").getAsString() : "";
                            String category = route.has("name") && !route.get("name").isJsonNull() ? route.get("name").getAsString() : "Imported";
                            if (route.has("waypoints")) {
                                for (JsonElement wpEl : route.getAsJsonArray("waypoints")) {
                                    JsonObject wp = wpEl.getAsJsonObject();
                                    JsonArray pos = wp.getAsJsonArray("pos");
                                    String name = wp.has("name") ? wp.get("name").getAsString() : "Waypoint";
                                    double x = pos.get(0).getAsDouble();
                                    double y = pos.get(1).getAsDouble();
                                    double z = pos.get(2).getAsDouble();
                                    BomboConfig.CustomWaypoint cwp = new BomboConfig.CustomWaypoint(name, x, y, z, island, true, false, "AQUA", category);
                                    BomboConfig.get().customWaypoints.putIfAbsent(BomboConfig.get().activeProfile, new ArrayList<>());
                                    BomboConfig.get().customWaypoints.get(BomboConfig.get().activeProfile).add(cwp);
                                    count++;
                                }
                            }
                        }
                    }
                }
            } else if (data.trim().startsWith("[") && data.trim().endsWith("]")) {
                JsonElement el = JsonParser.parseString(data.trim());
                if (el.isJsonArray()) {
                    for (JsonElement wpEl : el.getAsJsonArray()) {
                        JsonObject wp = wpEl.getAsJsonObject();
                        if (wp.has("x") && wp.has("y") && wp.has("z")) {
                            double x = wp.get("x").getAsDouble();
                            double y = wp.get("y").getAsDouble();
                            double z = wp.get("z").getAsDouble();
                            String name = "Waypoint";
                            if (wp.has("options")) {
                                JsonObject options = wp.getAsJsonObject("options");
                                if (options.has("name")) {
                                    name = options.get("name").getAsString();
                                }
                            }
                            BomboConfig.CustomWaypoint cwp = new BomboConfig.CustomWaypoint(name, x, y, z, "", true, false, "AQUA", "Imported");
                            BomboConfig.get().customWaypoints.putIfAbsent(BomboConfig.get().activeProfile, new ArrayList<>());
                            BomboConfig.get().customWaypoints.get(BomboConfig.get().activeProfile).add(cwp);
                            count++;
                        }
                    }
                }
            }
            if (count > 0) BomboConfig.save();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    public static void render(LevelRenderContext context) {
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
        PoseStack poseStack = context.poseStack();
        me.bombo.bomboaddons.OrderedSubmitNodeCollector collector = new me.bombo.bomboaddons.OrderedSubmitNodeCollector(context.bufferSource());

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

                AABB box = new AABB(
                    scaledX - boxWidth, scaledY - boxHeight, scaledZ - boxWidth,
                    scaledX + boxWidth, scaledY + boxHeight, scaledZ + boxWidth
                );
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, lineWidth);
                });

                if (s.pestWaypointBeacon) {
                    float beaconWidth = 0.15f * scale;
                    AABB beaconBox = new AABB(
                        scaledX - beaconWidth, scaledY, scaledZ - beaconWidth,
                        scaledX + beaconWidth, scaledY + (256.0f * scale), scaledZ + beaconWidth
                    );
                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4f, lineWidth);
                    });
                }

                // Draw label
                String text = wp.label + " §7(" + (int)dist + "m)";
                BomboRenderUtils.drawText(poseStack, collector, text, (float)x, (float)y + 0.8f, (float)z, 0xFFFFFF, 0.03f, true, true);
            }
        }
        if (hasCustom) {
            String currentAreaLower = (BomboaddonsClient.currentArea == null ? "" : BomboaddonsClient.currentArea).toLowerCase();
            List<BomboConfig.CustomWaypoint> validWps = new ArrayList<>();
            
            for (BomboConfig.CustomWaypoint wp : customWps) {
                if (!wp.enabled) continue;
                boolean matched = true;
                if (wp.requiredIsland != null && !wp.requiredIsland.trim().isEmpty()) {
                    String target = wp.requiredIsland.trim().toLowerCase();
                    matched = false;
                    
                    if (target.equals(BomboaddonsClient.locrawMode.toLowerCase()) || target.equals(BomboaddonsClient.locrawMap.toLowerCase())) {
                        matched = true;
                    } else if (currentAreaLower.contains(target)) {
                        matched = true;
                    }
                    
                    if (!matched && mc.level != null) {
                        var sidebar = mc.level.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
                        if (sidebar != null) {
                            for (String line : SkyblockUtils.getSidebarLines(mc.level.getScoreboard(), sidebar)) {
                                String clean = line.replaceAll("(?i)\\u00a7.", "").trim().toLowerCase();
                                if (clean.contains(target)) {
                                    matched = true;
                                    break;
                                }
                            }
                        }
                    }
                }
                if (matched) {
                    validWps.add(wp);
                }
            }

            java.util.Map<String, List<Integer>> orderedWpsPerCat = new java.util.HashMap<>();
            for (BomboConfig.CustomWaypoint wp : validWps) {
                if (wp.ordered) {
                    try {
                        int val = Integer.parseInt(wp.name.trim());
                        String cat = wp.category != null ? wp.category : "Default";
                        orderedWpsPerCat.putIfAbsent(cat, new ArrayList<>());
                        if (!orderedWpsPerCat.get(cat).contains(val)) {
                            orderedWpsPerCat.get(cat).add(val);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            for (String cat : orderedWpsPerCat.keySet()) {
                List<Integer> numericWps = orderedWpsPerCat.get(cat);
                numericWps.sort(Integer::compareTo);
                
                String lastIsland = lastOrderedIslandPerCat.getOrDefault(cat, "");
                int currentOrderedIndex = currentOrderedIndexPerCat.getOrDefault(cat, -1);
                
                if (!lastIsland.equals(currentAreaLower)) {
                    lastOrderedIslandPerCat.put(cat, currentAreaLower);
                    currentOrderedIndex = numericWps.get(0);
                } else if (!numericWps.contains(currentOrderedIndex)) {
                    currentOrderedIndex = numericWps.get(0);
                }
                
                // check if we are close to currentOrderedIndex
                for (BomboConfig.CustomWaypoint wp : validWps) {
                    if (wp.ordered && (wp.category != null ? wp.category : "Default").equals(cat)) {
                        try {
                            int val = Integer.parseInt(wp.name.trim());
                            if (val == currentOrderedIndex) {
                                double dx = wp.x - playerPos.x;
                                double dy = wp.y - playerPos.y;
                                double dz = wp.z - playerPos.z;
                                if (Math.sqrt(dx*dx + dy*dy + dz*dz) <= 3.0) {
                                    // Advance
                                    int idx = numericWps.indexOf(currentOrderedIndex);
                                    if (idx + 1 < numericWps.size()) {
                                        currentOrderedIndex = numericWps.get(idx + 1);
                                    } else {
                                        currentOrderedIndex = numericWps.get(0); // loop back
                                    }
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                currentOrderedIndexPerCat.put(cat, currentOrderedIndex);
            }

            for (BomboConfig.CustomWaypoint wp : validWps) {
                boolean isCurrent = false;
                if (wp.ordered) {
                    try {
                        int val = Integer.parseInt(wp.name.trim());
                        String cat = wp.category != null ? wp.category : "Default";
                        int currentOrderedIndex = currentOrderedIndexPerCat.getOrDefault(cat, -1);
                        List<Integer> numericWps = orderedWpsPerCat.get(cat);
                        
                        boolean isNext = false;
                        if (numericWps != null) {
                            if (val == currentOrderedIndex) {
                                isCurrent = true;
                            } else if (numericWps.indexOf(currentOrderedIndex) != -1) {
                                int idx = numericWps.indexOf(currentOrderedIndex);
                                if (idx + 1 < numericWps.size() && val == numericWps.get(idx + 1)) {
                                    isNext = true;
                                } else if (idx + 1 == numericWps.size() && val == numericWps.get(0)) {
                                    isNext = true;
                                }
                            }
                        }
                        if (!isCurrent && !isNext) continue; // Hide other ordered waypoints in this category
                    } catch (NumberFormatException ignored) {}
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

                AABB box = new AABB(
                    scaledX - boxWidth, scaledY - boxHeight, scaledZ - boxWidth,
                    scaledX + boxWidth, scaledY + boxHeight, scaledZ + boxWidth
                );
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, lineWidth);
                });

                if (wp.showBeacon && (!wp.ordered || isCurrent)) {
                    float beaconWidth = 0.15f * scale;
                    AABB beaconBox = new AABB(
                        scaledX - beaconWidth, scaledY, scaledZ - beaconWidth,
                        scaledX + beaconWidth, scaledY + (256.0f * scale), scaledZ + beaconWidth
                    );
                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4f, lineWidth);
                    });
                }

                // Draw label
                String text = wp.name + " \u00a77(" + (int)dist + "m)";
                BomboRenderUtils.drawText(poseStack, collector, text, (float)x, (float)y + 0.8f, (float)z, 0xFFFFFF, 0.03f, true, wp.showThroughWalls);

                if (isCurrent) {
                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        org.joml.Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
                        float trX = look.x();
                        float trY = look.y();
                        float trZ = look.z();
                        BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, trX, trY, trZ, (float)x, (float)y, (float)z, r, g, b, 1.0f, lineWidth);
                    });
                }
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

                int colorHex = BomboRenderUtils.colorNameToHex("light_purple");
                float r = ((colorHex >> 16) & 0xFF) / 255.0f;
                float g = ((colorHex >> 8) & 0xFF) / 255.0f;
                float b = (colorHex & 0xFF) / 255.0f;

                // 1. Draw real-world proc boundary box (non-perspective-scaled)
                AABB boundaryBox = new AABB(
                    x - rVal, y - rVal, z - rVal,
                    x + rVal, y + rVal, z + rVal
                );
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, boundaryBox, r, g, b, 0.4f, 1.5f);
                });

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
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, centerBox, r, g, b, 1.0f, 2.0f);
                });



                // Draw waypoint text label
                String text = "§d[Bind] §f" + cb.command + " §7(" + (int)dist + "m, r=" + String.format("%.1f", rVal) + ")";
                BomboRenderUtils.drawText(poseStack, collector, text, (float)x, (float)y + 0.8f, (float)z, 0xFFFFFF, 0.03f, true, true);
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
