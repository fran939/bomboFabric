package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ParticleESP {
   public static String typeFilter = null;

   public static void render(LevelRenderContext context) {
      BomboConfig.Settings settings = BomboConfig.get();
      if (ParticleTracker.espEnabled || settings.debugParticles || settings.particleHighlightsEnabled) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            String filter = !settings.debugParticles && !settings.particleHighlightsEnabled ? typeFilter : null;
            List<ParticleTracker.ParticleEntry> points = ParticleTracker.getEspPoints(filter);
            if (!points.isEmpty()) {
               Vec3 camPos = mc.gameRenderer.getMainCamera().position();
               PoseStack poseStack = context.poseStack();
               OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
               RenderType renderType = settings.hideCheats ? RenderTypes.lines() : RenderTypes.linesTranslucent();
               Map<String, Integer> typeColors = new HashMap<>();

               // 1. Particle Highlights
               if (settings.particleHighlightsEnabled) {
                  Map<String, List<ParticleTracker.ParticleEntry>> highlightedByType = new HashMap<>();
                  for (ParticleTracker.ParticleEntry p : points) {
                     BomboConfig.HighlightInfo highlight = settings.particleHighlights.get(p.type.toLowerCase());
                     if (highlight != null && highlight.enabled) {
                        highlightedByType.computeIfAbsent(p.type, k -> new ArrayList<>()).add(p);
                     }
                  }

                  for (Map.Entry<String, List<ParticleTracker.ParticleEntry>> entry : highlightedByType.entrySet()) {
                     String typeName = entry.getKey();
                     List<ParticleTracker.ParticleEntry> typePoints = entry.getValue();
                     BomboConfig.HighlightInfo highlight = settings.particleHighlights.get(typeName.toLowerCase());
                     int colorInt = BomboRenderUtils.colorNameToHex(highlight.color);
                     float r = (float)(colorInt >> 16 & 255) / 255.0F;
                     float g = (float)(colorInt >> 8 & 255) / 255.0F;
                     float b = (float)(colorInt & 255) / 255.0F;

                     for (ParticleTracker.ParticleEntry p : typePoints) {
                        double relX = p.x - camPos.x;
                        double relY = p.y - camPos.y;
                        double relZ = p.z - camPos.z;
                        double hs = 0.15;
                        AABB box = new AABB(relX - hs, relY - hs, relZ - hs, relX + hs, relY + hs, relZ + hs);
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85F, 1.5F));
                     }
                  }
               }

               // 2. Debug Particles
               if (settings.debugParticles) {
                  Map<String, List<ParticleTracker.ParticleEntry>> particlesByType = new HashMap<>();
                  for (ParticleTracker.ParticleEntry p : points) {
                     particlesByType.computeIfAbsent(p.type, k -> new ArrayList<>()).add(p);
                  }

                  for (Map.Entry<String, List<ParticleTracker.ParticleEntry>> entry : particlesByType.entrySet()) {
                     String typeName = entry.getKey();
                     List<ParticleTracker.ParticleEntry> typePoints = entry.getValue();
                     BomboConfig.HighlightInfo highlight = settings.particleHighlights.get(typeName.toLowerCase());
                     int colorInt = (settings.particleHighlightsEnabled && highlight != null && highlight.enabled)
                        ? BomboRenderUtils.colorNameToHex(highlight.color)
                        : typeColors.computeIfAbsent(typeName, ParticleTracker::colorForType);
                     float r = (float)(colorInt >> 16 & 255) / 255.0F;
                     float g = (float)(colorInt >> 8 & 255) / 255.0F;
                     float b = (float)(colorInt & 255) / 255.0F;

                     List<List<ParticleTracker.ParticleEntry>> clusters = clusterPoints(typePoints, 6.0);
                     for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        if (cluster.isEmpty()) continue;
                        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                        for (ParticleTracker.ParticleEntry p : cluster) {
                           if (p.x < minX) minX = p.x;
                           if (p.x > maxX) maxX = p.x;
                           if (p.y < minY) minY = p.y;
                           if (p.y > maxY) maxY = p.y;
                           if (p.z < minZ) minZ = p.z;
                           if (p.z > maxZ) maxZ = p.z;
                        }
                        double centerX = (minX + maxX) / 2.0;
                        double centerZ = (minZ + maxZ) / 2.0;
                        boolean isFlat = (maxY - minY) < 1.5;
                        double centerY = isFlat ? minY : (minY + maxY) / 2.0;

                        double maxDist = 0.0;
                        for (ParticleTracker.ParticleEntry p : cluster) {
                           double dx = p.x - centerX;
                           double dy = isFlat ? 0.0 : p.y - centerY;
                           double dz = p.z - centerZ;
                           double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                           if (dist > maxDist) maxDist = dist;
                        }
                        float radius = Math.max(0.5F, Math.min((float)maxDist, 10.0F));
                        final float finalRadius = radius;
                        double relX = centerX - camPos.x;
                        double relY = centerY - camPos.y;
                        double relZ = centerZ - camPos.z;

                        if (isFlat) {
                           collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawHorizontalCircle(pose.pose(), vertexConsumer, (float)relX, (float)relY, (float)relZ, finalRadius, r, g, b, 0.85F, 2.0F));
                        } else {
                           collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawSphere(pose.pose(), vertexConsumer, (float)relX, (float)relY, (float)relZ, finalRadius, r, g, b, 0.85F, 2.0F));
                        }

                        float nameOffset = isFlat ? 0.4F : (finalRadius + 0.4F);
                        BomboRenderUtils.drawText(poseStack, collector, "§f" + typeName + " (" + cluster.size() + ")", (float)relX, (float)relY + nameOffset, (float)relZ, colorInt, 0.022F, true, true);
                     }
                  }
               }

               // 3. Particle Tracker ESP
               if (ParticleTracker.espEnabled) {
                  if (typeFilter != null) {
                     List<List<ParticleTracker.ParticleEntry>> clusters = clusterPoints(points, 6.0);
                     for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        if (cluster.isEmpty()) continue;
                        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                        double minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
                        for (ParticleTracker.ParticleEntry p : cluster) {
                           if (p.x < minX) minX = p.x;
                           if (p.x > maxX) maxX = p.x;
                           if (p.y < minY) minY = p.y;
                           if (p.y > maxY) maxY = p.y;
                           if (p.z < minZ) minZ = p.z;
                           if (p.z > maxZ) maxZ = p.z;
                        }
                        double centerX = (minX + maxX) / 2.0;
                        double centerZ = (minZ + maxZ) / 2.0;
                        double maxDist = 0.0;
                        for (ParticleTracker.ParticleEntry p : cluster) {
                           double dx = p.x - centerX;
                           double dz = p.z - centerZ;
                           double dist = Math.sqrt(dx * dx + dz * dz);
                           if (dist > maxDist) maxDist = dist;
                        }
                        float radius = Math.max(0.5F, Math.min((float)maxDist, 10.0F));
                        final float finalRadius = radius;

                        String firstType = cluster.get(0).type;
                        int colorInt = typeColors.computeIfAbsent(firstType, ParticleTracker::colorForType);
                        float r = (float)(colorInt >> 16 & 255) / 255.0F;
                        float g = (float)(colorInt >> 8 & 255) / 255.0F;
                        float b = (float)(colorInt & 255) / 255.0F;
                        double relX = centerX - camPos.x;
                        double relY = minY - camPos.y;
                        double relZ = centerZ - camPos.z;
                        float hs = 0.25F;
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawHorizontalCircle(pose.pose(), vertexConsumer, (float)relX, (float)relY, (float)relZ, finalRadius, r, g, b, 0.85F, 2.0F));
                        AABB box = new AABB(relX - hs, relY - hs, relZ - hs, relX + hs, relY + hs, relZ + hs);
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85F, 1.5F));
                        BomboRenderUtils.drawText(poseStack, collector, "§e[Hotspot] §f" + firstType + " (" + cluster.size() + ")", (float)relX, (float)relY + 0.4F, (float)relZ, colorInt, 0.022F, true, true);
                     }
                  } else {
                     int count = 0;
                     for (ParticleTracker.ParticleEntry entry : points) {
                        if (count++ > 300) break;
                        int colorInt = typeColors.computeIfAbsent(entry.type, ParticleTracker::colorForType);
                        float r = (float)(colorInt >> 16 & 255) / 255.0F;
                        float g = (float)(colorInt >> 8 & 255) / 255.0F;
                        float b = (float)(colorInt & 255) / 255.0F;
                        double relX = entry.x - camPos.x;
                        double relY = entry.y - camPos.y;
                        double relZ = entry.z - camPos.z;
                        double hs = 0.12;
                        AABB box = new AABB(relX - hs, relY - hs, relZ - hs, relX + hs, relY + hs, relZ + hs);
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85F, 1.5F));
                        BomboRenderUtils.drawText(poseStack, collector, "§f" + entry.type, (float)relX, (float)relY + 0.25F, (float)relZ, colorInt, 0.018F, true, true);
                     }
                  }
               }
            }
         }
      }
   }

   private static List<List<ParticleTracker.ParticleEntry>> clusterPoints(List<ParticleTracker.ParticleEntry> points, double maxDistSq) {
      List<List<ParticleTracker.ParticleEntry>> clusters = new ArrayList<>();
      for (ParticleTracker.ParticleEntry p : points) {
         List<ParticleTracker.ParticleEntry> foundCluster = null;
         for (List<ParticleTracker.ParticleEntry> cluster : clusters) {
            for (ParticleTracker.ParticleEntry member : cluster) {
               double dx = p.x - member.x;
               double dy = p.y - member.y;
               double dz = p.z - member.z;
               if (dx * dx + dy * dy + dz * dz < maxDistSq * maxDistSq) {
                  foundCluster = cluster;
                  break;
               }
            }
            if (foundCluster != null) break;
         }
         if (foundCluster != null) {
            foundCluster.add(p);
         } else {
            List<ParticleTracker.ParticleEntry> newCluster = new ArrayList<>();
            newCluster.add(p);
            clusters.add(newCluster);
         }
      }
      return clusters;
   }
}
