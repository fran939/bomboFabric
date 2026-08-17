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
               Map<String, Integer> typeColors = new HashMap();
               if (settings.particleHighlightsEnabled) {
                  Map<String, List<ParticleTracker.ParticleEntry>> highlightedByType = new HashMap();

                  for(ParticleTracker.ParticleEntry p : points) {
                     BomboConfig.HighlightInfo highlight = (BomboConfig.HighlightInfo)settings.particleHighlights.get(p.type.toLowerCase());
                     if (highlight != null && highlight.enabled) {
                        ((List)highlightedByType.computeIfAbsent(p.type, (k) -> new ArrayList())).add(p);
                     }
                  }

                  for(Map.Entry<String, List<ParticleTracker.ParticleEntry>> entry : highlightedByType.entrySet()) {
                     String typeName = (String)entry.getKey();
                     List<ParticleTracker.ParticleEntry> typePoints = (List)entry.getValue();
                     BomboConfig.HighlightInfo highlight = (BomboConfig.HighlightInfo)settings.particleHighlights.get(typeName.toLowerCase());
                     int colorInt = BomboRenderUtils.colorNameToHex(highlight.color);
                     float r = (float)(colorInt >> 16 & 255) / 255.0F;
                     float g = (float)(colorInt >> 8 & 255) / 255.0F;
                     float b = (float)(colorInt & 255) / 255.0F;
                     List<List<ParticleTracker.ParticleEntry>> clusters = new ArrayList();

                     for(ParticleTracker.ParticleEntry p : typePoints) {
                        List<ParticleTracker.ParticleEntry> foundCluster = null;

                        for(List<ParticleTracker.ParticleEntry> cluster : clusters) {
                           for(ParticleTracker.ParticleEntry member : cluster) {
                              double dx = p.x - member.x;
                              double dy = p.y - member.y;
                              double dz = p.z - member.z;
                              if (dx * dx + dy * dy + dz * dz < (double)36.0F) {
                                 foundCluster = cluster;
                                 break;
                              }
                           }

                           if (foundCluster != null) {
                              break;
                           }
                        }

                        if (foundCluster != null) {
                           foundCluster.add(p);
                        } else {
                           List<ParticleTracker.ParticleEntry> newCluster = new ArrayList();
                           newCluster.add(p);
                           clusters.add(newCluster);
                        }
                     }

                     for(List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        if (!cluster.isEmpty()) {
                           double minX = Double.MAX_VALUE;
                           double maxX = -Double.MAX_VALUE;
                           double minZ = Double.MAX_VALUE;
                           double maxZ = -Double.MAX_VALUE;
                           double minY = Double.MAX_VALUE;
                           double maxY = -Double.MAX_VALUE;

                           for(ParticleTracker.ParticleEntry p : cluster) {
                              if (p.x < minX) {
                                 minX = p.x;
                              }

                              if (p.x > maxX) {
                                 maxX = p.x;
                              }

                              if (p.z < minZ) {
                                 minZ = p.z;
                              }

                              if (p.z > maxZ) {
                                 maxZ = p.z;
                              }

                              if (p.y < minY) {
                                 minY = p.y;
                              }

                              if (p.y > maxY) {
                                 maxY = p.y;
                              }
                           }

                           double pad = 0.15;
                           minX -= pad;
                           maxX += pad;
                           minY -= pad;
                           maxY += pad;
                           minZ -= pad;
                           maxZ += pad;
                           double relMinX = minX - camPos.x;
                           double relMaxX = maxX - camPos.x;
                           double relMinY = minY - camPos.y;
                           double relMaxY = maxY - camPos.y;
                           double relMinZ = minZ - camPos.z;
                           double relMaxZ = maxZ - camPos.z;
                           double centerX = (relMinX + relMaxX) / (double)2.0F;
                           double centerY = (relMinY + relMaxY) / (double)2.0F;
                           double centerZ = (relMinZ + relMaxZ) / (double)2.0F;
                           float dist = (float)Math.sqrt(centerX * centerX + centerY * centerY + centerZ * centerZ);
                           float scale = 1.0F;
                           if (dist > 0.2F) {
                              scale = 0.2F / dist;
                           }

                           double scaledMinX = relMinX * (double)scale;
                           double scaledMaxX = relMaxX * (double)scale;
                           double scaledMinY = relMinY * (double)scale;
                           double scaledMaxY = relMaxY * (double)scale;
                           double scaledMinZ = relMinZ * (double)scale;
                           double scaledMaxZ = relMaxZ * (double)scale;
                           AABB box = new AABB(scaledMinX, scaledMinY, scaledMinZ, scaledMaxX, scaledMaxY, scaledMaxZ);
                           collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85F, 1.5F));
                        }
                     }
                  }
               }

               if (settings.debugParticles) {
                  Map<String, List<ParticleTracker.ParticleEntry>> particlesByType = new HashMap();

                  for(ParticleTracker.ParticleEntry p : points) {
                     ((List)particlesByType.computeIfAbsent(p.type, (k) -> new ArrayList())).add(p);
                  }

                  for(Map.Entry<String, List<ParticleTracker.ParticleEntry>> entry : particlesByType.entrySet()) {
                     String typeName = (String)entry.getKey();
                     List<ParticleTracker.ParticleEntry> typePoints = (List)entry.getValue();
                     BomboConfig.HighlightInfo highlight = (BomboConfig.HighlightInfo)settings.particleHighlights.get(typeName.toLowerCase());
                     int colorInt;
                     if (settings.particleHighlightsEnabled && highlight != null && highlight.enabled) {
                        colorInt = BomboRenderUtils.colorNameToHex(highlight.color);
                     } else {
                        colorInt = (Integer)typeColors.computeIfAbsent(typeName, ParticleTracker::colorForType);
                     }

                     List<List<ParticleTracker.ParticleEntry>> clusters = new ArrayList();

                     for(ParticleTracker.ParticleEntry p : typePoints) {
                        List<ParticleTracker.ParticleEntry> foundCluster = null;

                        for(List<ParticleTracker.ParticleEntry> cluster : clusters) {
                           for(ParticleTracker.ParticleEntry member : cluster) {
                              double dx = p.x - member.x;
                              double dy = p.y - member.y;
                              double dz = p.z - member.z;
                              if (dx * dx + dy * dy + dz * dz < (double)36.0F) {
                                 foundCluster = cluster;
                                 break;
                              }
                           }

                           if (foundCluster != null) {
                              break;
                           }
                        }

                        if (foundCluster != null) {
                           foundCluster.add(p);
                        } else {
                           List<ParticleTracker.ParticleEntry> newCluster = new ArrayList();
                           newCluster.add(p);
                           clusters.add(newCluster);
                        }
                     }

                     for(List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        if (!cluster.isEmpty()) {
                           double minX = Double.MAX_VALUE;
                           double maxX = -Double.MAX_VALUE;
                           double minZ = Double.MAX_VALUE;
                           double maxZ = -Double.MAX_VALUE;
                           double minY = Double.MAX_VALUE;
                           double maxY = -Double.MAX_VALUE;

                           for(ParticleTracker.ParticleEntry p : cluster) {
                              if (p.x < minX) {
                                 minX = p.x;
                              }

                              if (p.x > maxX) {
                                 maxX = p.x;
                              }

                              if (p.z < minZ) {
                                 minZ = p.z;
                              }

                              if (p.z > maxZ) {
                                 maxZ = p.z;
                              }

                              if (p.y < minY) {
                                 minY = p.y;
                              }

                              if (p.y > maxY) {
                                 maxY = p.y;
                              }
                           }

                           double centerX = (minX + maxX) / (double)2.0F;
                           double centerZ = (minZ + maxZ) / (double)2.0F;
                           double heightDiff = maxY - minY;
                           boolean isFlat = heightDiff < (double)1.5F;
                           double centerY;
                           float radius;
                           if (isFlat) {
                              centerY = (minY + maxY) / (double)2.0F;
                              double maxDist = (double)0.0F;

                              for(ParticleTracker.ParticleEntry p : cluster) {
                                 double dx = p.x - centerX;
                                 double dz = p.z - centerZ;
                                 double dist = Math.sqrt(dx * dx + dz * dz);
                                 if (dist > maxDist) {
                                    maxDist = dist;
                                 }
                              }

                              radius = (float)maxDist;
                           } else {
                              centerY = (minY + maxY) / (double)2.0F;
                              double maxDist = (double)0.0F;

                              for(ParticleTracker.ParticleEntry p : cluster) {
                                 double dx = p.x - centerX;
                                 double dy = p.y - centerY;
                                 double dz = p.z - centerZ;
                                 double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                 if (dist > maxDist) {
                                    maxDist = dist;
                                 }
                              }

                              radius = (float)maxDist;
                           }

                           if (radius < 0.5F) {
                              radius = 0.5F;
                           }

                           if (radius > 10.0F) {
                              radius = 10.0F;
                           }

                           float r = (float)(colorInt >> 16 & 255) / 255.0F;
                           float g = (float)(colorInt >> 8 & 255) / 255.0F;
                           float b = (float)(colorInt & 255) / 255.0F;
                           double relX = centerX - camPos.x;
                           double relY = centerY - camPos.y;
                           double relZ = centerZ - camPos.z;
                           float dist = (float)Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                           float scale = 1.0F;
                           if (dist > 0.2F) {
                              scale = 0.2F / dist;
                           }

                           double scaledX = relX * (double)scale;
                           double scaledY = relY * (double)scale;
                           double scaledZ = relZ * (double)scale;
                           float scaledRadius = radius * scale;
                           if (isFlat) {
                              collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawHorizontalCircle(pose.pose(), vertexConsumer, (float)scaledX, (float)scaledY, (float)scaledZ, scaledRadius, r, g, b, 0.85F, 2.0F));
                           } else {
                              collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawSphere(pose.pose(), vertexConsumer, (float)scaledX, (float)scaledY, (float)scaledZ, scaledRadius, r, g, b, 0.85F, 2.0F));
                           }

                           float nameOffset = radius + 0.4F;
                           if (isFlat) {
                              nameOffset = 0.4F;
                           }

                           BomboRenderUtils.drawText(poseStack, collector, "§f" + typeName + " (" + cluster.size() + ")", (float)relX, (float)relY + nameOffset, (float)relZ, colorInt, 0.022F, true, true);
                        }
                     }
                  }
               }

               if (ParticleTracker.espEnabled) {
                  if (typeFilter != null) {
                     List<List<ParticleTracker.ParticleEntry>> clusters = new ArrayList();

                     for(ParticleTracker.ParticleEntry p : points) {
                        List<ParticleTracker.ParticleEntry> foundCluster = null;

                        for(List<ParticleTracker.ParticleEntry> cluster : clusters) {
                           for(ParticleTracker.ParticleEntry member : cluster) {
                              double dx = p.x - member.x;
                              double dy = p.y - member.y;
                              double dz = p.z - member.z;
                              if (dx * dx + dy * dy + dz * dz < (double)36.0F) {
                                 foundCluster = cluster;
                                 break;
                              }
                           }

                           if (foundCluster != null) {
                              break;
                           }
                        }

                        if (foundCluster != null) {
                           foundCluster.add(p);
                        } else {
                           List<ParticleTracker.ParticleEntry> newCluster = new ArrayList();
                           newCluster.add(p);
                           clusters.add(newCluster);
                        }
                     }

                     for(List<ParticleTracker.ParticleEntry> cluster : clusters) {
                        if (!cluster.isEmpty()) {
                           double minX = Double.MAX_VALUE;
                           double maxX = -Double.MAX_VALUE;
                           double minZ = Double.MAX_VALUE;
                           double maxZ = -Double.MAX_VALUE;
                           double minY = Double.MAX_VALUE;
                           double maxY = -Double.MAX_VALUE;

                           for(ParticleTracker.ParticleEntry p : cluster) {
                              if (p.x < minX) {
                                 minX = p.x;
                              }

                              if (p.x > maxX) {
                                 maxX = p.x;
                              }

                              if (p.z < minZ) {
                                 minZ = p.z;
                              }

                              if (p.z > maxZ) {
                                 maxZ = p.z;
                              }

                              if (p.y < minY) {
                                 minY = p.y;
                              }

                              if (p.y > maxY) {
                                 maxY = p.y;
                              }
                           }

                           double centerX = (minX + maxX) / (double)2.0F;
                           double centerZ = (minZ + maxZ) / (double)2.0F;
                           double maxDist = (double)0.0F;

                           for(ParticleTracker.ParticleEntry p : cluster) {
                              double dx = p.x - centerX;
                              double dz = p.z - centerZ;
                              double dist = Math.sqrt(dx * dx + dz * dz);
                              if (dist > maxDist) {
                                 maxDist = dist;
                              }
                           }

                           float radius = (float)maxDist;
                           if (radius < 0.5F) {
                              radius = 2.0F;
                           }

                           if (radius > 10.0F) {
                              radius = 10.0F;
                           }

                           String firstType = ((ParticleTracker.ParticleEntry)cluster.get(0)).type;
                           int colorInt = (Integer)typeColors.computeIfAbsent(firstType, ParticleTracker::colorForType);
                           float r = (float)(colorInt >> 16 & 255) / 255.0F;
                           float g = (float)(colorInt >> 8 & 255) / 255.0F;
                           float b = (float)(colorInt & 255) / 255.0F;
                           double relX = centerX - camPos.x;
                           double relY = minY - camPos.y;
                           double relZ = centerZ - camPos.z;
                           float dist = (float)Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                           float scale = 1.0F;
                           if (dist > 0.2F) {
                              scale = 0.2F / dist;
                           }

                           double scaledX = relX * (double)scale;
                           double scaledY = relY * (double)scale;
                           double scaledZ = relZ * (double)scale;
                           float scaledRadius = radius * scale;
                           float scaledHs = 0.25F * scale;
                           collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawHorizontalCircle(pose.pose(), vertexConsumer, (float)scaledX, (float)scaledY, (float)scaledZ, scaledRadius, r, g, b, 0.85F, 2.0F));
                           AABB box = new AABB(scaledX - (double)scaledHs, scaledY - (double)scaledHs, scaledZ - (double)scaledHs, scaledX + (double)scaledHs, scaledY + (double)scaledHs, scaledZ + (double)scaledHs);
                           collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85F, 1.5F));
                           BomboRenderUtils.drawText(poseStack, collector, "§e[Hotspot] §f" + firstType + " (" + cluster.size() + ")", (float)relX, (float)relY + 0.4F, (float)relZ, colorInt, 0.022F, true, true);
                        }
                     }
                  } else {
                     int count = 0;

                     for(ParticleTracker.ParticleEntry entry : points) {
                        if (count++ > 150) {
                           break;
                        }

                        int colorInt = (Integer)typeColors.computeIfAbsent(entry.type, ParticleTracker::colorForType);
                        float r = (float)(colorInt >> 16 & 255) / 255.0F;
                        float g = (float)(colorInt >> 8 & 255) / 255.0F;
                        float b = (float)(colorInt & 255) / 255.0F;
                        double relX = entry.x - camPos.x;
                        double relY = entry.y - camPos.y;
                        double relZ = entry.z - camPos.z;
                        float dist = (float)Math.sqrt(relX * relX + relY * relY + relZ * relZ);
                        float scale = 1.0F;
                        if (dist > 0.2F) {
                           scale = 0.2F / dist;
                        }

                        double scaledX = relX * (double)scale;
                        double scaledY = relY * (double)scale;
                        double scaledZ = relZ * (double)scale;
                        float scaledHs = 0.15F * scale;
                        AABB box = new AABB(scaledX - (double)scaledHs, scaledY - (double)scaledHs, scaledZ - (double)scaledHs, scaledX + (double)scaledHs, scaledY + (double)scaledHs, scaledZ + (double)scaledHs);
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, 0.85F, 1.5F));
                        BomboRenderUtils.drawText(poseStack, collector, "§f" + entry.type, (float)relX, (float)relY + 0.3F, (float)relZ, colorInt, 0.018F, true, true);
                     }
                  }
               }

            }
         }
      }
   }
}
