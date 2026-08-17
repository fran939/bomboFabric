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
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class OrderedWaypoints {
   private static final List<Waypoint> waypoints = new ArrayList();
   public static int currentWpIndex = 0;

   public static List<Waypoint> getWaypoints() {
      synchronized(waypoints) {
         return new ArrayList(waypoints);
      }
   }

   public static void clear() {
      synchronized(waypoints) {
         waypoints.clear();
         currentWpIndex = 0;
      }
   }

   public static int importWaypointsFromClipboard(String data) {
      int count = 0;
      List<Waypoint> tempWaypoints = new ArrayList();

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
                        if (route.has("waypoints")) {
                           for(JsonElement wpEl : route.getAsJsonArray("waypoints")) {
                              JsonObject wp = wpEl.getAsJsonObject();
                              JsonArray pos = wp.getAsJsonArray("pos");
                              String name = wp.has("name") ? wp.get("name").getAsString() : "Waypoint";
                              double x = pos.get(0).getAsDouble();
                              double y = pos.get(1).getAsDouble();
                              double z = pos.get(2).getAsDouble();
                              tempWaypoints.add(new Waypoint(new Vec3(x, y, z), name));
                              ++count;
                           }
                        }
                     }
                  }
               } catch (Throwable var27) {
                  try {
                     br.close();
                  } catch (Throwable var24) {
                     var27.addSuppressed(var24);
                  }

                  throw var27;
               }

               br.close();
            } catch (Throwable var28) {
               try {
                  gis.close();
               } catch (Throwable var23) {
                  var28.addSuppressed(var23);
               }

               throw var28;
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

                     tempWaypoints.add(new Waypoint(new Vec3(x, y, z), name));
                     ++count;
                  }
               }
            }
         }

         try {
            tempWaypoints.sort((a, b) -> {
               try {
                  return Integer.compare(Integer.parseInt(a.name.replaceAll("[^0-9]", "")), Integer.parseInt(b.name.replaceAll("[^0-9]", "")));
               } catch (Exception var3) {
                  return a.name.compareTo(b.name);
               }
            });
         } catch (Exception var26) {
         }

         synchronized(waypoints) {
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
      if (waypoints.isEmpty()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         synchronized(waypoints) {
            if (!waypoints.isEmpty()) {
               if (currentWpIndex < waypoints.size()) {
                  Waypoint current = (Waypoint)waypoints.get(currentWpIndex);
                  double distToCurrent = current.position.distanceTo(mc.player.position());
                  if (distToCurrent < (double)3.0F) {
                     ++currentWpIndex;
                     if (currentWpIndex >= waypoints.size()) {
                        waypoints.clear();
                        currentWpIndex = 0;
                        return;
                     }

                     current = (Waypoint)waypoints.get(currentWpIndex);
                  }

                  Vec3 camPos = mc.gameRenderer.getMainCamera().position();
                  PoseStack poseStack = context.poseStack();
                  OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
                  float startX = mc.gameRenderer.getMainCamera().forwardVector().x();
                  float startY = mc.gameRenderer.getMainCamera().forwardVector().y();
                  float startZ = mc.gameRenderer.getMainCamera().forwardVector().z();
                  float endX = (float)(current.position.x - camPos.x);
                  float endY = (float)(current.position.y - camPos.y + (double)1.0F);
                  float endZ = (float)(current.position.z - camPos.z);
                  float dist = (float)Math.sqrt((double)(endX * endX + endY * endY + endZ * endZ));
                  float scale = 1.0F;
                  if (dist > 0.2F) {
                     scale = 0.2F / dist;
                  }

                  float startXScaled = startX * scale;
                  float startYScaled = startY * scale;
                  float startZScaled = startZ * scale;
                  float endXScaled = endX * scale;
                  float endYScaled = endY * scale;
                  float endZScaled = endZ * scale;
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startXScaled, startYScaled, startZScaled, endXScaled, endYScaled, endZScaled, 0.0F, 1.0F, 1.0F, 1.0F, 2.0F));
                  float boxWidth = 0.5F * scale;
                  float boxHeight = 1.0F * scale;
                  AABB box = new AABB((double)(endXScaled - boxWidth), (double)(endYScaled - boxHeight), (double)(endZScaled - boxWidth), (double)(endXScaled + boxWidth), (double)endYScaled, (double)(endZScaled + boxWidth));
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, 0.0F, 1.0F, 1.0F, 0.85F, 2.0F));
                  BomboRenderUtils.drawText(poseStack, collector, "§b" + current.name, endX, endY + 0.5F, endZ, 65535, 0.03F, true, true);
                  if (currentWpIndex + 1 < waypoints.size()) {
                     Waypoint next = (Waypoint)waypoints.get(currentWpIndex + 1);
                     float nx = (float)(next.position.x - camPos.x);
                     float ny = (float)(next.position.y - camPos.y);
                     float nz = (float)(next.position.z - camPos.z);
                     float nDist = (float)Math.sqrt((double)(nx * nx + ny * ny + nz * nz));
                     float nScale = 1.0F;
                     if (nDist > 0.2F) {
                        nScale = 0.2F / nDist;
                     }

                     float nxScaled = nx * nScale;
                     float nyScaled = ny * nScale;
                     float nzScaled = nz * nScale;
                     float nBoxWidth = 0.5F * nScale;
                     float nBoxHeight = 1.0F * nScale;
                     AABB nextBox = new AABB((double)(nxScaled - nBoxWidth), (double)nyScaled, (double)(nzScaled - nBoxWidth), (double)(nxScaled + nBoxWidth), (double)(nyScaled + nBoxHeight), (double)(nzScaled + nBoxWidth));
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, nextBox, 0.0F, 0.5F, 0.5F, 0.5F, 2.0F));
                     BomboRenderUtils.drawText(poseStack, collector, "§3" + next.name, nx, ny + 1.5F, nz, 43690, 0.03F, true, true);
                  }

               }
            }
         }
      }
   }

   public static class Waypoint {
      public final Vec3 position;
      public final String name;

      public Waypoint(Vec3 position, String name) {
         this.position = position;
         this.name = name;
      }
   }
}
