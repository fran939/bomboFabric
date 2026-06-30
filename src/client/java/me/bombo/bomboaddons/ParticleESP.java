package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParticleESP {

    /** Optional type filter — only show particles whose name contains this string (case-insensitive). Null = show all. */
    public static String typeFilter = null;

    // -----------------------------------------------------------------------
    // LevelRenderEvents.AFTER_ENTITIES render hook
    // -----------------------------------------------------------------------
    public static void render(LevelRenderContext context) {
        BomboConfig.Settings settings = BomboConfig.get();
        if (!ParticleTracker.espEnabled && !settings.debugParticles && !settings.particleHighlightsEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // If either debug or custom highlights is enabled, collect all tracked particles (null filter).
        // Otherwise, use command typeFilter.
        String filter = (settings.debugParticles || settings.particleHighlightsEnabled) ? null : typeFilter;
        List<ParticleTracker.ParticleEntry> points = ParticleTracker.getEspPoints(filter);
        if (points.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.poseStack();
        net.minecraft.client.renderer.OrderedSubmitNodeCollector collector = context.submitNodeCollector();
        if (collector == null) return;

        // Colors lookup map
        Map<String, Integer> typeColors = new HashMap<>();

        // -----------------------------------------------------------------------
        // Path 1: Custom Particle Highlights (Combined nearby particles into bounding boxes)
        // -----------------------------------------------------------------------
        if (settings.particleHighlightsEnabled) {
            // Group the points by type
            Map<String, List<ParticleTracker.ParticleEntry>> highlightedByType = new HashMap<>();
            for (ParticleTracker.ParticleEntry p : points) {
                BomboConfig.HighlightInfo highlight = settings.particleHighlights.get(p.type.toLowerCase());
                if (highlight != null && highlight.enabled) {
                    highlightedByType.computeIfAbsent(p.type, k -> new java.util.ArrayList<>()).add(p);
                }
            }

            for (Map.Entry<String, List<ParticleTracker.ParticleEntry>> entry : highlightedByType.entrySet()) {
                String typeName = entry.getKey();
                List<ParticleTracker.ParticleEntry> typePoints = entry.getValue();

                // Get highlight config
                BomboConfig.HighlightInfo highlight = settings.particleHighlights.get(typeName.toLowerCase());
                int colorInt = BomboRenderUtils.colorNameToHex(highlight.color);
                float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                float b = (colorInt & 0xFF) / 255.0f;

                // Cluster these points (6.0 block distance threshold)
                List<List<ParticleTracker.ParticleEntry>> clusters = new java.util.ArrayList<>();
                for (ParticleTracker.ParticleEntry p : typePoints) {
                    List<ParticleTracker.ParticleEntry> foundCluster = null;
                    for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        for (ParticleTracker.ParticleEntry member : cluster) {
                            double dx = p.x - member.x;
                            double dy = p.y - member.y;
                            double dz = p.z - member.z;
                            if (dx * dx + dy * dy + dz * dz < 6.0 * 6.0) {
                                foundCluster = cluster;
                                break;
                            }
                        }
                        if (foundCluster != null) break;
                    }
                    if (foundCluster != null) {
                        foundCluster.add(p);
                    } else {
                        List<ParticleTracker.ParticleEntry> newCluster = new java.util.ArrayList<>();
                        newCluster.add(p);
                        clusters.add(newCluster);
                    }
                }

                for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                    if (cluster.isEmpty()) continue;

                    // Find bounds of the cluster
                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                    double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                    double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                    for (ParticleTracker.ParticleEntry p : cluster) {
                        if (p.x < minX) minX = p.x;
                        if (p.x > maxX) maxX = p.x;
                        if (p.z < minZ) minZ = p.z;
                        if (p.z > maxZ) maxZ = p.z;
                        if (p.y < minY) minY = p.y;
                        if (p.y > maxY) maxY = p.y;
                    }

                    // Pad the bounds slightly so the box is clearly visible
                    double pad = 0.15;
                    minX -= pad; maxX += pad;
                    minY -= pad; maxY += pad;
                    minZ -= pad; maxZ += pad;

                    // Relative coordinates to the camera
                    double relMinX = minX - camPos.x;
                    double relMaxX = maxX - camPos.x;
                    double relMinY = minY - camPos.y;
                    double relMaxY = maxY - camPos.y;
                    double relMinZ = minZ - camPos.z;
                    double relMaxZ = maxZ - camPos.z;

                    double centerX = (relMinX + relMaxX) / 2.0;
                    double centerY = (relMinY + relMaxY) / 2.0;
                    double centerZ = (relMinZ + relMaxZ) / 2.0;

                    // Perspective scaling trick to see through walls
                    float dist = (float) Math.sqrt(centerX * centerX + centerY * centerY + centerZ * centerZ);
                    float scale = 1.0f;
                    if (dist > 0.2f) {
                        scale = 0.2f / dist;
                    }

                    double scaledMinX = relMinX * scale;
                    double scaledMaxX = relMaxX * scale;
                    double scaledMinY = relMinY * scale;
                    double scaledMaxY = relMaxY * scale;
                    double scaledMinZ = relMinZ * scale;
                    double scaledMaxZ = relMaxZ * scale;

                    AABB box = new AABB(scaledMinX, scaledMinY, scaledMinZ, scaledMaxX, scaledMaxY, scaledMaxZ);

                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85f, 1.5f);
                    });
                }
            }
        }

        // -----------------------------------------------------------------------
        // Path 2: Debug Particles Mode (Clusters, circles/spheres, and name labels)
        // -----------------------------------------------------------------------
        if (settings.debugParticles) {
            // Group all points by type
            Map<String, List<ParticleTracker.ParticleEntry>> particlesByType = new HashMap<>();
            for (ParticleTracker.ParticleEntry p : points) {
                particlesByType.computeIfAbsent(p.type, k -> new java.util.ArrayList<>()).add(p);
            }

            for (Map.Entry<String, List<ParticleTracker.ParticleEntry>> entry : particlesByType.entrySet()) {
                String typeName = entry.getKey();
                List<ParticleTracker.ParticleEntry> typePoints = entry.getValue();

                // Check if this type has a custom highlight to inherit color, otherwise default
                BomboConfig.HighlightInfo highlight = settings.particleHighlights.get(typeName.toLowerCase());
                int colorInt;
                if (settings.particleHighlightsEnabled && highlight != null && highlight.enabled) {
                    colorInt = BomboRenderUtils.colorNameToHex(highlight.color);
                } else {
                    colorInt = typeColors.computeIfAbsent(typeName, ParticleTracker::colorForType);
                }

                // Cluster these points (6.0 block distance threshold)
                List<List<ParticleTracker.ParticleEntry>> clusters = new java.util.ArrayList<>();
                for (ParticleTracker.ParticleEntry p : typePoints) {
                    List<ParticleTracker.ParticleEntry> foundCluster = null;
                    for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        for (ParticleTracker.ParticleEntry member : cluster) {
                            double dx = p.x - member.x;
                            double dy = p.y - member.y;
                            double dz = p.z - member.z;
                            if (dx * dx + dy * dy + dz * dz < 6.0 * 6.0) {
                                foundCluster = cluster;
                                break;
                            }
                        }
                        if (foundCluster != null) break;
                    }
                    if (foundCluster != null) {
                        foundCluster.add(p);
                    } else {
                        List<ParticleTracker.ParticleEntry> newCluster = new java.util.ArrayList<>();
                        newCluster.add(p);
                        clusters.add(newCluster);
                    }
                }

                for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                    if (cluster.isEmpty()) continue;

                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                    double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                    double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                    for (ParticleTracker.ParticleEntry p : cluster) {
                        if (p.x < minX) minX = p.x;
                        if (p.x > maxX) maxX = p.x;
                        if (p.z < minZ) minZ = p.z;
                        if (p.z > maxZ) maxZ = p.z;
                        if (p.y < minY) minY = p.y;
                        if (p.y > maxY) maxY = p.y;
                    }

                    double centerX = (minX + maxX) / 2.0;
                    double centerZ = (minZ + maxZ) / 2.0;
                    double heightDiff = maxY - minY;
                    boolean isFlat = heightDiff < 1.5;

                    double centerY;
                    float radius;

                    if (isFlat) {
                        centerY = (minY + maxY) / 2.0;
                        double maxDist = 0;
                        for (ParticleTracker.ParticleEntry p : cluster) {
                            double dx = p.x - centerX;
                            double dz = p.z - centerZ;
                            double dist = Math.sqrt(dx * dx + dz * dz);
                            if (dist > maxDist) {
                                maxDist = dist;
                            }
                        }
                        radius = (float) maxDist;
                    } else {
                        centerY = (minY + maxY) / 2.0;
                        double maxDist = 0;
                        for (ParticleTracker.ParticleEntry p : cluster) {
                            double dx = p.x - centerX;
                            double dy = p.y - centerY;
                            double dz = p.z - centerZ;
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            if (dist > maxDist) {
                                maxDist = dist;
                            }
                        }
                        radius = (float) maxDist;
                    }

                    if (radius < 0.5f) radius = 0.5f;
                    if (radius > 10.0f) radius = 10.0f;

                    float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                    float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                    float b = (colorInt & 0xFF) / 255.0f;

                    double relX = centerX - camPos.x;
                    double relY = centerY - camPos.y;
                    double relZ = centerZ - camPos.z;

                    float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                    float scale = 1.0f;
                    if (dist > 0.2f) {
                        scale = 0.2f / dist;
                    }

                    double scaledX = relX * scale;
                    double scaledY = relY * scale;
                    double scaledZ = relZ * scale;
                    float scaledRadius = radius * scale;

                    if (isFlat) {
                        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                            BomboRenderUtils.drawHorizontalCircle(pose.pose(), vertexConsumer, (float) scaledX, (float) scaledY, (float) scaledZ, scaledRadius, r, g, b, 0.85f, 2.0f);
                        });
                    } else {
                        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                            BomboRenderUtils.drawSphere(pose.pose(), vertexConsumer, (float) scaledX, (float) scaledY, (float) scaledZ, scaledRadius, r, g, b, 0.85f, 2.0f);
                        });
                    }

                    float nameOffset = radius + 0.4f;
                    if (isFlat) nameOffset = 0.4f;

                    BomboRenderUtils.drawText(
                        poseStack, collector,
                        "§f" + typeName + " (" + cluster.size() + ")",
                        (float) relX, (float) relY + nameOffset, (float) relZ,
                        colorInt, 0.022f, true, true
                    );
                }
            }
        }

        // -----------------------------------------------------------------------
        // Path 3: Command / Manual ESP Mode (/b particle esp)
        // -----------------------------------------------------------------------
        if (ParticleTracker.espEnabled) {
            if (typeFilter != null) {
                // Group particles into clusters to find hotspots (within 6.0 blocks)
                List<List<ParticleTracker.ParticleEntry>> clusters = new java.util.ArrayList<>();
                for (ParticleTracker.ParticleEntry p : points) {
                    List<ParticleTracker.ParticleEntry> foundCluster = null;
                    for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        for (ParticleTracker.ParticleEntry member : cluster) {
                            double dx = p.x - member.x;
                            double dy = p.y - member.y;
                            double dz = p.z - member.z;
                            if (dx * dx + dy * dy + dz * dz < 6.0 * 6.0) {
                                foundCluster = cluster;
                                break;
                            }
                        }
                        if (foundCluster != null) break;
                    }
                    if (foundCluster != null) {
                        foundCluster.add(p);
                    } else {
                        List<ParticleTracker.ParticleEntry> newCluster = new java.util.ArrayList<>();
                        newCluster.add(p);
                        clusters.add(newCluster);
                    }
                }

                for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                    if (cluster.isEmpty()) continue;
                    double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                    double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                    double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                    for (ParticleTracker.ParticleEntry p : cluster) {
                        if (p.x < minX) minX = p.x;
                        if (p.x > maxX) maxX = p.x;
                        if (p.z < minZ) minZ = p.z;
                        if (p.z > maxZ) maxZ = p.z;
                        if (p.y < minY) minY = p.y;
                        if (p.y > maxY) maxY = p.y;
                    }
                    double centerX = (minX + maxX) / 2.0;
                    double centerZ = (minZ + maxZ) / 2.0;
                    double centerY = minY; // Sit at the ground/lowest particle level

                    double maxDist = 0;
                    for (ParticleTracker.ParticleEntry p : cluster) {
                        double dx = p.x - centerX;
                        double dz = p.z - centerZ;
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist > maxDist) {
                            maxDist = dist;
                        }
                    }
                    float radius = (float) maxDist;
                    if (radius < 0.5f) radius = 2.0f;
                    if (radius > 10.0f) radius = 10.0f;

                    String firstType = cluster.get(0).type;
                    int colorInt = typeColors.computeIfAbsent(firstType, ParticleTracker::colorForType);
                    float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                    float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                    float b = (colorInt & 0xFF) / 255.0f;

                    double relX = centerX - camPos.x;
                    double relY = centerY - camPos.y;
                    double relZ = centerZ - camPos.z;

                    float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                    float scale = 1.0f;
                    if (dist > 0.2f) {
                        scale = 0.2f / dist;
                    }

                    double scaledX = relX * scale;
                    double scaledY = relY * scale;
                    double scaledZ = relZ * scale;
                    float scaledRadius = radius * scale;
                    float scaledHs = 0.25f * scale;

                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawHorizontalCircle(pose.pose(), vertexConsumer, (float) scaledX, (float) scaledY, (float) scaledZ, scaledRadius, r, g, b, 0.85f, 2.0f);
                    });

                    AABB box = new AABB(
                        scaledX - scaledHs, scaledY - scaledHs, scaledZ - scaledHs,
                        scaledX + scaledHs, scaledY + scaledHs, scaledZ + scaledHs
                    );
                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85f, 1.5f);
                    });

                    BomboRenderUtils.drawText(
                        poseStack, collector,
                        "§e[Hotspot] §f" + firstType + " (" + cluster.size() + ")",
                        (float) relX, (float) relY + 0.4f, (float) relZ,
                        colorInt, 0.022f, true, true
                    );
                }
            } else {
                // Draw individual particles (limit to 150)
                int count = 0;
                for (ParticleTracker.ParticleEntry entry : points) {
                    if (count++ > 150) break;
                    int colorInt = typeColors.computeIfAbsent(entry.type, ParticleTracker::colorForType);

                    float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                    float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                    float b = (colorInt & 0xFF) / 255.0f;

                    double relX = entry.x - camPos.x;
                    double relY = entry.y - camPos.y;
                    double relZ = entry.z - camPos.z;

                    float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                    float scale = 1.0f;
                    if (dist > 0.2f) {
                        scale = 0.2f / dist;
                    }

                    double scaledX = relX * scale;
                    double scaledY = relY * scale;
                    double scaledZ = relZ * scale;
                    float scaledHs = 0.15f * scale;

                    AABB box = new AABB(
                        scaledX - scaledHs, scaledY - scaledHs, scaledZ - scaledHs,
                        scaledX + scaledHs, scaledY + scaledHs, scaledZ + scaledHs
                    );

                    collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85f, 1.5f);
                    });

                    BomboRenderUtils.drawText(
                        poseStack, collector,
                        "§f" + entry.type,
                        (float) relX, (float) relY + 0.3f, (float) relZ,
                        colorInt, 0.018f, true, true
                    );
                }
            }
        }
    }
}
