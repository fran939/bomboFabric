package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
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

public class OrderedWaypoints {
    public static class Waypoint {
        public final Vec3 position;
        public final String name;

        public Waypoint(Vec3 position, String name) {
            this.position = position;
            this.name = name;
        }
    }

    private static final List<Waypoint> waypoints = new ArrayList<>();
    
    public static List<Waypoint> getWaypoints() {
        synchronized (waypoints) {
            return new ArrayList<>(waypoints);
        }
    }
    public static int currentWpIndex = 0;

    public static void clear() {
        synchronized (waypoints) {
            waypoints.clear();
            currentWpIndex = 0;
        }
    }

    public static int importWaypointsFromClipboard(String data) {
        int count = 0;
        List<Waypoint> tempWaypoints = new ArrayList<>();
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
                            if (route.has("waypoints")) {
                                for (JsonElement wpEl : route.getAsJsonArray("waypoints")) {
                                    JsonObject wp = wpEl.getAsJsonObject();
                                    JsonArray pos = wp.getAsJsonArray("pos");
                                    String name = wp.has("name") ? wp.get("name").getAsString() : "Waypoint";
                                    double x = pos.get(0).getAsDouble();
                                    double y = pos.get(1).getAsDouble();
                                    double z = pos.get(2).getAsDouble();
                                    tempWaypoints.add(new Waypoint(new Vec3(x, y, z), name));
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
                            tempWaypoints.add(new Waypoint(new Vec3(x, y, z), name));
                            count++;
                        }
                    }
                }
            }
            
            // Try to sort numerically if names are numbers
            try {
                tempWaypoints.sort((a, b) -> {
                    try {
                        return Integer.compare(Integer.parseInt(a.name.replaceAll("[^0-9]", "")), Integer.parseInt(b.name.replaceAll("[^0-9]", "")));
                    } catch (Exception e) {
                        return a.name.compareTo(b.name);
                    }
                });
            } catch (Exception e) {}
            
            synchronized (waypoints) {
                waypoints.clear();
                waypoints.addAll(tempWaypoints);
                currentWpIndex = 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return count;
    }

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        synchronized (waypoints) {
            if (waypoints.isEmpty()) return;
            if (currentWpIndex >= waypoints.size()) return;
            
            Waypoint current = waypoints.get(currentWpIndex);
            
            double distToCurrent = current.position.distanceTo(mc.player.position());
            if (distToCurrent < 3.0) {
                currentWpIndex++;
                if (currentWpIndex >= waypoints.size()) {
                    waypoints.clear();
                    currentWpIndex = 0;
                    return;
                }
                current = waypoints.get(currentWpIndex);
            }
            
            Vec3 camPos = mc.gameRenderer.getMainCamera().position();
            PoseStack poseStack = context.poseStack();
            OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
            
            // Render tracer to current
            float startX = (float) mc.gameRenderer.getMainCamera().forwardVector().x();
            float startY = (float) mc.gameRenderer.getMainCamera().forwardVector().y();
            float startZ = (float) mc.gameRenderer.getMainCamera().forwardVector().z();
            
            float endX = (float) (current.position.x - camPos.x);
            float endY = (float) (current.position.y - camPos.y + 1.0f);
            float endZ = (float) (current.position.z - camPos.z);
            
            float dist = (float) Math.sqrt(endX*endX + endY*endY + endZ*endZ);
            float scale = 1.0f;
            if (dist > 0.2f) {
                scale = 0.2f / dist;
            }
            
            float startXScaled = startX * scale;
            float startYScaled = startY * scale;
            float startZScaled = startZ * scale;
            
            float endXScaled = endX * scale;
            float endYScaled = endY * scale;
            float endZScaled = endZ * scale;

            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startXScaled, startYScaled, startZScaled, endXScaled, endYScaled, endZScaled, 0.0f, 1.0f, 1.0f, 1.0f, 2.0f);
            });
            
            // Render current waypoint box
            float boxWidth = 0.5f * scale;
            float boxHeight = 1.0f * scale;
            AABB box = new AABB(endXScaled - boxWidth, endYScaled - boxHeight, endZScaled - boxWidth, endXScaled + boxWidth, endYScaled, endZScaled + boxWidth);
            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, 0.0f, 1.0f, 1.0f, 0.85f, 2.0f);
            });
            BomboRenderUtils.drawText(poseStack, collector, "§b" + current.name, endX, endY + 0.5f, endZ, 0x00FFFF, 0.03f, true, true);
            
            // Render next waypoint if exists
            if (currentWpIndex + 1 < waypoints.size()) {
                Waypoint next = waypoints.get(currentWpIndex + 1);
                float nx = (float) (next.position.x - camPos.x);
                float ny = (float) (next.position.y - camPos.y);
                float nz = (float) (next.position.z - camPos.z);
                
                float nDist = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
                float nScale = 1.0f;
                if (nDist > 0.2f) {
                    nScale = 0.2f / nDist;
                }
                
                float nxScaled = nx * nScale;
                float nyScaled = ny * nScale;
                float nzScaled = nz * nScale;
                
                float nBoxWidth = 0.5f * nScale;
                float nBoxHeight = 1.0f * nScale;
                AABB nextBox = new AABB(nxScaled - nBoxWidth, nyScaled, nzScaled - nBoxWidth, nxScaled + nBoxWidth, nyScaled + nBoxHeight, nzScaled + nBoxWidth);
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, nextBox, 0.0f, 0.5f, 0.5f, 0.5f, 2.0f);
                });
                BomboRenderUtils.drawText(poseStack, collector, "§3" + next.name, nx, ny + 1.5f, nz, 0x00AAAA, 0.03f, true, true);
            }
        }
    }
}
