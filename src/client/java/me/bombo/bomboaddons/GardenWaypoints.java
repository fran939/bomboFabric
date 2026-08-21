package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Vector3fc;

public class GardenWaypoints {
   private static final List<Waypoint> waypoints = new ArrayList();
   public static Map<String, Integer> currentOrderedIndexPerCat = new HashMap();
   public static Map<String, String> lastOrderedIslandPerCat = new HashMap();

   public static void addWaypoint(Vec3 pos, String label) {
      synchronized(waypoints) {
         waypoints.add(new Waypoint(pos, label));
      }
   }

   public static void clear() {
      synchronized(waypoints) {
         waypoints.clear();
      }
   }

   public static void clearStructureWaypoints() {
      synchronized(waypoints) {
         waypoints.removeIf(wp -> wp.label != null && (wp.label.startsWith("[Structure]") || wp.label.equalsIgnoreCase("Corleone 1")));
      }
   }

   public static boolean hasAnyVisibleWaypoints() {
      synchronized(waypoints) {
         if (!waypoints.isEmpty()) return true;
      }
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         if (s.customWaypoints != null) {
            List<BomboConfig.CustomWaypoint> active = s.customWaypoints.get(s.activeProfile);
            if (active != null) {
               for (BomboConfig.CustomWaypoint wp : active) {
                  if (wp.enabled && matchesIsland(wp.requiredIsland)) return true;
               }
            }
            List<BomboConfig.CustomWaypoint> general = s.customWaypoints.get("General");
            if (general != null && !s.activeProfile.equals("General")) {
               for (BomboConfig.CustomWaypoint wp : general) {
                  if (wp.enabled && matchesIsland(wp.requiredIsland)) return true;
               }
            }
         }
         if (s.coordBinds != null) {
            List<BomboConfig.CoordBind> active = s.coordBinds.get(s.activeProfile);
            if (active != null) {
               for (BomboConfig.CoordBind cb : active) {
                  if (cb.enabled && cb.showWaypoint && matchesIsland(cb.requiredIsland)) return true;
               }
            }
            List<BomboConfig.CoordBind> general = s.coordBinds.get("General");
            if (general != null && !s.activeProfile.equals("General")) {
               for (BomboConfig.CoordBind cb : general) {
                  if (cb.enabled && cb.showWaypoint && matchesIsland(cb.requiredIsland)) return true;
               }
            }
         }
      }
      return false;
   }

   public static List<Waypoint> getWaypoints() {
      synchronized(waypoints) {
         return new ArrayList(waypoints);
      }
   }

   public static int importWaypointsFromClipboard(String data) {
      int count = 0;

      try {
         if (data.startsWith("[Skyblocker-Waypoint-Data-V1]")) {
            data = data.substring("[Skyblocker-Waypoint-Data-V1]".length());
            byte[] decoded = Base64.getDecoder().decode(data);
            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(decoded));

            try {
               BufferedReader br = new BufferedReader(new InputStreamReader(gis, "UTF-8"));

               try {
                  String jsonStr = (String)br.lines().collect(Collectors.joining());
                  JsonElement el = JsonParser.parseString(jsonStr);
                  if (el.isJsonArray()) {
                     for(JsonElement routeEl : el.getAsJsonArray()) {
                        JsonObject route = routeEl.getAsJsonObject();
                        String island = route.has("island") && !route.get("island").isJsonNull() ? route.get("island").getAsString() : "";
                        String category = route.has("name") && !route.get("name").isJsonNull() ? route.get("name").getAsString() : "Imported";
                        if (route.has("waypoints")) {
                           for(JsonElement wpEl : route.getAsJsonArray("waypoints")) {
                              JsonObject wp = wpEl.getAsJsonObject();
                              JsonArray pos = wp.getAsJsonArray("pos");
                              String name = wp.has("name") ? wp.get("name").getAsString() : "Waypoint";
                              double x = pos.get(0).getAsDouble();
                              double y = pos.get(1).getAsDouble();
                              double z = pos.get(2).getAsDouble();
                              BomboConfig.CustomWaypoint cwp = new BomboConfig.CustomWaypoint(name, x, y, z, island, true, false, "AQUA", category);
                              BomboConfig.get().customWaypoints.putIfAbsent(BomboConfig.get().activeProfile, new ArrayList());
                              ((List)BomboConfig.get().customWaypoints.get(BomboConfig.get().activeProfile)).add(cwp);
                              ++count;
                           }
                        }
                     }
                  }
               } catch (Throwable var26) {
                  try {
                     br.close();
                  } catch (Throwable var25) {
                     var26.addSuppressed(var25);
                  }

                  throw var26;
               }

               br.close();
            } catch (Throwable var27) {
               try {
                  gis.close();
               } catch (Throwable var24) {
                  var27.addSuppressed(var24);
               }

               throw var27;
            }

            gis.close();
         } else if (data.trim().startsWith("[") && data.trim().endsWith("]")) {
            JsonElement el = JsonParser.parseString(data.trim());
            if (el.isJsonArray()) {
               for(JsonElement wpEl : el.getAsJsonArray()) {
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
                     BomboConfig.get().customWaypoints.putIfAbsent(BomboConfig.get().activeProfile, new ArrayList());
                     ((List)BomboConfig.get().customWaypoints.get(BomboConfig.get().activeProfile)).add(cwp);
                     ++count;
                  }
               }
            }
         }

         if (count > 0) {
            BomboConfig.save();
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

      return count;
   }

   public static void render(LevelRenderContext context) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null) return;
         Vec3 playerPos = mc.player.position();
         boolean isGarden = "Garden".equalsIgnoreCase(BomboaddonsClient.currentArea) || "The Garden".equalsIgnoreCase(BomboaddonsClient.currentArea);

         List<Waypoint> activeWaypoints = Collections.emptyList();
         if (!waypoints.isEmpty()) {
            long now = System.currentTimeMillis();
            List<Waypoint> toRemove = new ArrayList();
            synchronized(waypoints) {
               for(Waypoint wp : waypoints) {
                  if (isGarden && s.pestWaypointDuration > 0 && now - wp.creationTime >= (long)s.pestWaypointDuration * 1000L) {
                     toRemove.add(wp);
                  } else if (isGarden && s.pestWaypointRemoveOnNear) {
                     double dist = wp.position.distanceTo(playerPos);
                     if (!wp.hasLeftRadius) {
                        if (dist > (double)15.0F) {
                           wp.hasLeftRadius = true;
                        }
                     } else if (dist <= (double)10.0F) {
                        toRemove.add(wp);
                     }
                  }
               }

               waypoints.removeAll(toRemove);
            }
            activeWaypoints = getWaypoints();
         }

         List<BomboConfig.CustomWaypoint> customWps = new ArrayList();
         if (s.customWaypoints != null) {
            List<BomboConfig.CustomWaypoint> activeCustomWps = (List)s.customWaypoints.get(s.activeProfile);
            List<BomboConfig.CustomWaypoint> generalCustomWps = (List)s.customWaypoints.get("General");
            if (activeCustomWps != null) {
               for (BomboConfig.CustomWaypoint wp : activeCustomWps) {
                  if (wp.enabled && matchesIsland(wp.requiredIsland)) customWps.add(wp);
               }
            }

            if (generalCustomWps != null && !s.activeProfile.equals("General")) {
               for (BomboConfig.CustomWaypoint wp : generalCustomWps) {
                  if (wp.enabled && matchesIsland(wp.requiredIsland)) customWps.add(wp);
               }
            }
         }

         List<BomboConfig.CoordBind> coordWps = new ArrayList();
         if (s.coordBinds != null) {
            List<BomboConfig.CoordBind> activeCoordBinds = (List)s.coordBinds.get(s.activeProfile);
            List<BomboConfig.CoordBind> generalCoordBinds = (List)s.coordBinds.get("General");
            if (activeCoordBinds != null) {
               for(BomboConfig.CoordBind cb : activeCoordBinds) {
                  if (cb.enabled && cb.showWaypoint && matchesIsland(cb.requiredIsland)) {
                     coordWps.add(cb);
                  }
               }
            }

            if (generalCoordBinds != null && !s.activeProfile.equals("General")) {
               for(BomboConfig.CoordBind cb : generalCoordBinds) {
                  if (cb.enabled && cb.showWaypoint && matchesIsland(cb.requiredIsland)) {
                     coordWps.add(cb);
                  }
               }
            }
         }

         boolean hasActive = !activeWaypoints.isEmpty();
         boolean hasCustom = !customWps.isEmpty();
         boolean hasCoord = !coordWps.isEmpty();
         if (!hasActive && !hasCustom && !hasCoord) {
            return;
         }

         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
         if (hasActive) {
            int colorInt = BomboRenderUtils.colorNameToHex("aqua");
            float r = (float)(colorInt >> 16 & 255) / 255.0F;
            float g = (float)(colorInt >> 8 & 255) / 255.0F;
               float b = (float)(colorInt & 255) / 255.0F;
               float a = 1.0F;
               float lineWidth = 2.0F;

               for(Waypoint wp : activeWaypoints) {
                  double x = wp.position.x - camPos.x;
                  double y = wp.position.y - camPos.y;
                  double z = wp.position.z - camPos.z;
                  float dist = (float)Math.sqrt(x * x + y * y + z * z);
                  float scale = 1.0F;
                  if (!s.hideCheats && dist > 0.2F) {
                     scale = 0.2F / dist;
                  }

                  float boxWidth = 0.5F * scale;
                  float boxHeight = 0.5F * scale;
                  float scaledX = (float)x * scale;
                  float scaledY = (float)y * scale;
                  float scaledZ = (float)z * scale;
                  AABB box = new AABB((double)(scaledX - boxWidth), (double)(scaledY - boxHeight), (double)(scaledZ - boxWidth), (double)(scaledX + boxWidth), (double)(scaledY + boxHeight), (double)(scaledZ + boxWidth));
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, lineWidth));
                  if (s.pestWaypointBeacon) {
                     float beaconWidth = 0.15F * scale;
                     AABB beaconBox = new AABB((double)(scaledX - beaconWidth), (double)scaledY, (double)(scaledZ - beaconWidth), (double)(scaledX + beaconWidth), (double)(scaledY + 256.0F * scale), (double)(scaledZ + beaconWidth));
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4F, lineWidth));
                  }

                  String text = wp.label + " §7(" + (int)dist + "m)";
                  BomboRenderUtils.drawText(poseStack, collector, text, (float)x, (float)y + 0.8F, (float)z, 16777215, 0.03F, true, true);
               }
            }

            if (hasCustom) {
               String currentAreaLower = (BomboaddonsClient.currentArea == null ? "" : BomboaddonsClient.currentArea).toLowerCase();
               List<BomboConfig.CustomWaypoint> validWps = new ArrayList();

               for(BomboConfig.CustomWaypoint wp : customWps) {
                  if (wp.enabled) {
                     if (matchesIsland(wp.requiredIsland)) {
                        validWps.add(wp);
                     }
                  }
               }

               Map<String, List<Integer>> orderedWpsPerCat = new HashMap();

               for(BomboConfig.CustomWaypoint wp : validWps) {
                  if (wp.ordered) {
                     try {
                        int val = Integer.parseInt(wp.name.trim());
                        String cat = wp.category != null ? wp.category : "Default";
                        orderedWpsPerCat.putIfAbsent(cat, new ArrayList());
                        if (!((List)orderedWpsPerCat.get(cat)).contains(val)) {
                           ((List)orderedWpsPerCat.get(cat)).add(val);
                        }
                     } catch (NumberFormatException var45) {
                     }
                  }
               }

               for(String cat : orderedWpsPerCat.keySet()) {
                  List<Integer> numericWps = (List)orderedWpsPerCat.get(cat);
                  numericWps.sort(Integer::compareTo);
                  String lastIsland = (String)lastOrderedIslandPerCat.getOrDefault(cat, "");
                  int currentOrderedIndex = (Integer)currentOrderedIndexPerCat.getOrDefault(cat, -1);
                  if (!lastIsland.equals(currentAreaLower)) {
                     lastOrderedIslandPerCat.put(cat, currentAreaLower);
                     currentOrderedIndex = (Integer)numericWps.get(0);
                  } else if (!numericWps.contains(currentOrderedIndex)) {
                     currentOrderedIndex = (Integer)numericWps.get(0);
                  }

                  for(BomboConfig.CustomWaypoint wp : validWps) {
                     if (wp.ordered && (wp.category != null ? wp.category : "Default").equals(cat)) {
                        try {
                           int val = Integer.parseInt(wp.name.trim());
                           if (val == currentOrderedIndex) {
                              double dx = wp.x - playerPos.x;
                              double dy = wp.y - playerPos.y;
                              double dz = wp.z - playerPos.z;
                              if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= (double)3.0F) {
                                 int idx = numericWps.indexOf(currentOrderedIndex);
                                 if (idx + 1 < numericWps.size()) {
                                    currentOrderedIndex = (Integer)numericWps.get(idx + 1);
                                 } else {
                                    currentOrderedIndex = (Integer)numericWps.get(0);
                                 }
                              }
                           }
                        } catch (NumberFormatException var44) {
                        }
                     }
                  }

                  currentOrderedIndexPerCat.put(cat, currentOrderedIndex);
               }

               Iterator var63 = validWps.iterator();

               label285:
               while(true) {
                  BomboConfig.CustomWaypoint wp;
                  boolean isCurrent;
                  while(true) {
                     if (!var63.hasNext()) {
                        break label285;
                     }

                     wp = (BomboConfig.CustomWaypoint)var63.next();
                     isCurrent = false;
                     if (!wp.ordered) {
                        break;
                     }

                     try {
                        int val = Integer.parseInt(wp.name.trim());
                        String cat = wp.category != null ? wp.category : "Default";
                        int currentOrderedIndex = (Integer)currentOrderedIndexPerCat.getOrDefault(cat, -1);
                        List<Integer> numericWps = (List)orderedWpsPerCat.get(cat);
                        boolean isNext = false;
                        if (numericWps != null) {
                           if (val == currentOrderedIndex) {
                              isCurrent = true;
                           } else if (numericWps.indexOf(currentOrderedIndex) != -1) {
                              int idx = numericWps.indexOf(currentOrderedIndex);
                              if (idx + 1 < numericWps.size() && val == (Integer)numericWps.get(idx + 1)) {
                                 isNext = true;
                              } else if (idx + 1 == numericWps.size() && val == (Integer)numericWps.get(0)) {
                                 isNext = true;
                              }
                           }
                        }

                        if (isCurrent || isNext) {
                           break;
                        }
                     } catch (NumberFormatException var46) {
                        break;
                     }
                  }

                  double x = wp.x - camPos.x;
                  double y = wp.y - camPos.y;
                  double z = wp.z - camPos.z;
                  float dist = (float)Math.sqrt(x * x + y * y + z * z);
                  float scale = 1.0F;
                  if (wp.showThroughWalls && !s.hideCheats && dist > 0.2F) {
                     scale = 0.2F / dist;
                  }

                  float boxWidth = 0.5F * scale;
                  float boxHeight = 0.5F * scale;
                  float scaledX = (float)x * scale;
                  float scaledY = (float)y * scale;
                  float scaledZ = (float)z * scale;
                  int colorHex = BomboRenderUtils.colorNameToHex(wp.color != null ? wp.color.toLowerCase() : "aqua");
                  float r = (float)(colorHex >> 16 & 255) / 255.0F;
                  float g = (float)(colorHex >> 8 & 255) / 255.0F;
                  float b = (float)(colorHex & 255) / 255.0F;
                  float a = 1.0F;
                  float lineWidth = 2.0F;
                  AABB box = new AABB((double)(scaledX - boxWidth), (double)(scaledY - boxHeight), (double)(scaledZ - boxWidth), (double)(scaledX + boxWidth), (double)(scaledY + boxHeight), (double)(scaledZ + boxWidth));
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, lineWidth));
                  if (wp.showBeacon && (!wp.ordered || isCurrent)) {
                     float beaconWidth = 0.15F * scale;
                     AABB beaconBox = new AABB((double)(scaledX - beaconWidth), (double)scaledY, (double)(scaledZ - beaconWidth), (double)(scaledX + beaconWidth), (double)(scaledY + 256.0F * scale), (double)(scaledZ + beaconWidth));
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4F, lineWidth));
                  }

                  String text = wp.name + " §7(" + (int)dist + "m)";
                  BomboRenderUtils.drawText(poseStack, collector, text, (float)x, (float)y + 0.8F, (float)z, 16777215, 0.03F, true, wp.showThroughWalls);
                  if (isCurrent) {
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                        Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
                        float trX = look.x();
                        float trY = look.y();
                        float trZ = look.z();
                        BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, trX, trY, trZ, (float)x, (float)y, (float)z, r, g, b, 1.0F, lineWidth);
                     });
                  }
               }
            }

            if (!coordWps.isEmpty()) {
               for(BomboConfig.CoordBind cb : coordWps) {
                  double x = cb.x - camPos.x;
                  double y = cb.y - camPos.y;
                  double z = cb.z - camPos.z;
                  float dist = (float)Math.sqrt(x * x + y * y + z * z);
                  double rVal = cb.radius <= (double)0.0F ? (double)3.0F : cb.radius;
                  int colorHex = BomboRenderUtils.colorNameToHex("light_purple");
                  float r = (float)(colorHex >> 16 & 255) / 255.0F;
                  float g = (float)(colorHex >> 8 & 255) / 255.0F;
                  float b = (float)(colorHex & 255) / 255.0F;
                  AABB boundaryBox = new AABB(x - rVal, y - rVal, z - rVal, x + rVal, y + rVal, z + rVal);
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, boundaryBox, r, g, b, 0.4F, 1.5F));
                  float scale = 1.0F;
                  if (!s.hideCheats && dist > 0.2F) {
                     scale = 0.2F / dist;
                  }

                  float boxWidth = 0.4F * scale;
                  float boxHeight = 0.4F * scale;
                  float scaledX = (float)x * scale;
                  float scaledY = (float)y * scale;
                  float scaledZ = (float)z * scale;
                  AABB centerBox = new AABB((double)(scaledX - boxWidth), (double)(scaledY - boxHeight), (double)(scaledZ - boxWidth), (double)(scaledX + boxWidth), (double)(scaledY + boxHeight), (double)(scaledZ + boxWidth));
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, centerBox, r, g, b, 1.0F, 2.0F));
                  String var10000 = cb.command;
                  String text = "§d[Bind] §f" + var10000 + " §7(" + (int)dist + "m, r=" + String.format("%.1f", rVal) + ")";
                  BomboRenderUtils.drawText(poseStack, collector, text, (float)x, (float)y + 0.8F, (float)z, 16777215, 0.03F, true, true);
               }
            }
         }
      }

   private static boolean matchesIsland(String requiredIsland) {
      return SkyblockUtils.matchesIslandRequirement(requiredIsland);
   }

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
}
