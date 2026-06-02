package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
    // WorldRenderEvents.AFTER_ENTITIES render hook
    // -----------------------------------------------------------------------

    public static void render(WorldRenderContext context) {
        if (!ParticleTracker.espEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        List<ParticleTracker.ParticleEntry> points = ParticleTracker.getEspPoints(typeFilter);
        if (points.isEmpty()) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.matrices();
        MultiBufferSource consumers = context.consumers();
        if (consumers == null) return;

        // Group by type so we can assign colors and avoid re-lookups
        Map<String, Integer> typeColors = new HashMap<>();

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

                // Calculate radius as the maximum horizontal distance from the center to any particle
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

                // Color based on type of first particle
                String firstType = cluster.get(0).type;
                int colorInt = typeColors.computeIfAbsent(firstType, ParticleTracker::colorForType);
                float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                float b = (colorInt & 0xFF) / 255.0f;

                double relX = centerX - camPos.x;
                double relY = centerY - camPos.y;
                double relZ = centerZ - camPos.z;

                // Perspective scaling trick to see through walls
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

                VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
                // Draw horizontal circle at the hotspot
                BomboRenderUtils.drawHorizontalCircle(poseStack, lineBuffer, (float) scaledX, (float) scaledY, (float) scaledZ, scaledRadius, r, g, b, 0.85f, 2.0f);

                // Draw a box at the center of the hotspot
                AABB box = new AABB(
                    scaledX - scaledHs, scaledY - scaledHs, scaledZ - scaledHs,
                    scaledX + scaledHs, scaledY + scaledHs, scaledZ + scaledHs
                );
                BomboRenderUtils.drawBox(poseStack, lineBuffer, box, r, g, b, 0.85f, 1.5f);

                // Label at the hotspot (uses original unscaled coords with built-in see-through text)
                BomboRenderUtils.drawText(
                    poseStack, consumers,
                    "§e[Hotspot] §f" + firstType + " (" + cluster.size() + ")",
                    (float) relX, (float) relY + 0.4f, (float) relZ,
                    colorInt, 0.022f, true, true
                );
            }
        } else {
            // Draw individual particles (limit to 150 to prevent massive FPS lag)
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

                // Perspective scaling trick to see through walls
                float dist = (float) Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                float scale = 1.0f;
                if (dist > 0.2f) {
                    scale = 0.2f / dist;
                }

                double scaledX = relX * scale;
                double scaledY = relY * scale;
                double scaledZ = relZ * scale;
                float scaledHs = 0.15f * scale;

                // Small box centered at the particle origin
                AABB box = new AABB(
                    scaledX - scaledHs, scaledY - scaledHs, scaledZ - scaledHs,
                    scaledX + scaledHs, scaledY + scaledHs, scaledZ + scaledHs
                );

                VertexConsumer lineBuffer = consumers.getBuffer(RenderTypes.linesTranslucent());
                BomboRenderUtils.drawBox(poseStack, lineBuffer, box, r, g, b, 0.85f, 1.5f);

                // Label: type name, floats 0.3 blocks above the particle origin
                BomboRenderUtils.drawText(
                    poseStack, consumers,
                    "§f" + entry.type,
                    (float) relX, (float) relY + 0.3f, (float) relZ,
                    colorInt, 0.018f, true, true
                );
            }
        }
    }
}
