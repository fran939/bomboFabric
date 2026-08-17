package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class WaypointManager {
   private static final List<CustomNavWaypoint> activeWaypoints = new ArrayList();
   private static List<Vec3> currentPath = null;
   private static boolean isCalculatingPath = false;
   private static int recalcCooldown = 0;
   private static ClientLevel lastLevel = null;
   public static String pendingNavTarget = null;
   private static int pendingNavTicks = 0;

   public static void init() {
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> onClientTick());
   }

   public static boolean hasWaypoints() {
      return !activeWaypoints.isEmpty() || (currentPath != null && !currentPath.isEmpty());
   }

   public static void render(LevelRenderContext context) {
      if (!hasWaypoints()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         Vec3 playerPos = mc.player.getEyePosition();
         PoseStack poseStack = context.poseStack();
         poseStack.pushPose();
         poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
         VertexConsumer lineBuffer = context.bufferSource().getBuffer(RenderTypes.linesTranslucent());
         synchronized(activeWaypoints) {
            for(CustomNavWaypoint wp : activeWaypoints) {
               if (wp.isNavPath) {
                  if (currentPath != null && !currentPath.isEmpty()) {
                     Vec3 pPos = new Vec3(playerPos.x, playerPos.y, playerPos.z);
                     Vec3 firstNode = (Vec3)currentPath.get(0);
                     BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer, (float)pPos.x, (float)pPos.y + 1.0F, (float)pPos.z, (float)firstNode.x, (float)firstNode.y, (float)firstNode.z, 0.0F, 1.0F, 0.0F, 0.5F, 2.0F);
                     Vec3 prev = firstNode;

                     for(int i = 1; i < currentPath.size(); ++i) {
                        Vec3 curr = (Vec3)currentPath.get(i);
                        BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer, (float)prev.x, (float)prev.y, (float)prev.z, (float)curr.x, (float)curr.y, (float)curr.z, 0.0F, 1.0F, 0.0F, 1.0F, 3.0F);
                        prev = curr;
                     }
                  } else if (isCalculatingPath) {
                     BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer, (float)playerPos.x, (float)playerPos.y - 0.5F, (float)playerPos.z, (float)wp.x, (float)wp.y, (float)wp.z, 0.5F, 0.5F, 0.5F, 1.0F, 2.0F);
                  } else {
                     BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer, (float)playerPos.x, (float)playerPos.y - 0.5F, (float)playerPos.z, (float)wp.x, (float)wp.y, (float)wp.z, 0.0F, 1.0F, 0.0F, 1.0F, 5.0F);
                  }
               }

               AABB box = new AABB(wp.x - (double)0.5F, wp.y, wp.z - (double)0.5F, wp.x + (double)0.5F, wp.y + (double)1.0F, wp.z + (double)0.5F);
               BomboRenderUtils.drawBox(poseStack, lineBuffer, box, wp.isNavPath ? 0.0F : 1.0F, wp.isNavPath ? 1.0F : 0.5F, 0.0F, 1.0F, 3.0F);
            }
         }

         poseStack.popPose();
      }
   }

   public static void onClientTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != lastLevel) {
         lastLevel = mc.level;
         activeWaypoints.clear();
         currentPath = null;
         if (pendingNavTarget != null) {
            pendingNavTicks = 200;
         }

      } else {
         if (pendingNavTarget != null && pendingNavTicks > 0) {
            --pendingNavTicks;
            String currentLoc = SkyblockUtils.getLocation();
            if (pendingNavTicks > 0 && (currentLoc == null || currentLoc.equals("Unknown") || currentLoc.equals("None"))) {
               return;
            }

            if (pendingNavTicks <= 0 || currentLoc != null && !currentLoc.equals("Unknown") && !currentLoc.equals("None")) {
               if (mc.player != null) {
                  mc.player.connection.sendCommand("bnav " + pendingNavTarget);
               }

               pendingNavTarget = null;
               pendingNavTicks = 0;
            }
         }

         if (mc.player != null) {
            Vec3 playerPos = mc.player.position();
            if (recalcCooldown > 0) {
               --recalcCooldown;
            }

            synchronized(activeWaypoints) {
               activeWaypoints.removeIf((wp) -> {
                  double dx = playerPos.x - wp.x;
                  double dy = playerPos.y - wp.y;
                  double dz = playerPos.z - wp.z;
                  double distSq = dx * dx + dy * dy + dz * dz;
                  if (wp.isNavPath && currentPath != null && !currentPath.isEmpty()) {
                     int closestIndex = 0;
                     double minSq = Double.MAX_VALUE;

                     for(int i = 0; i < currentPath.size(); ++i) {
                        Vec3 node = (Vec3)currentPath.get(i);
                        double sq = playerPos.distanceToSqr(node);
                        if (sq < minSq) {
                           minSq = sq;
                           closestIndex = i;
                        }
                     }

                     if (minSq < (double)900.0F) {
                        for(int i = 0; i < closestIndex; ++i) {
                           currentPath.remove(0);
                        }

                        if (minSq < (double)16.0F && !currentPath.isEmpty()) {
                           currentPath.remove(0);
                        }
                     } else if (minSq > (double)900.0F && recalcCooldown <= 0 && !isCalculatingPath) {
                        isCalculatingPath = true;
                        recalcCooldown = 100;
                        BlockPos startPos = mc.player.blockPosition();
                        BlockPos endPos = BlockPos.containing(wp.x, wp.y, wp.z);
                        String targetLoc = wp.island != null && !wp.island.isEmpty() ? wp.island : SkyblockUtils.getLocation();
                        CompletableFuture.runAsync(() -> {
                           try {
                              GraphPathfinder.computePathAsync(targetLoc, startPos, endPos, (path) -> {
                                 if (path != null) {
                                    currentPath = path;
                                 }

                                 isCalculatingPath = false;
                              });
                           } catch (Exception var4) {
                              isCalculatingPath = false;
                           }

                        });
                     }
                  }

                  if (distSq < (double)9.0F) {
                     if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§aArrived at " + wp.name + "!"));
                     }

                     return true;
                  } else {
                     return false;
                  }
               });
            }
         }
      }
   }

   public static void addWaypoint(double x, double y, double z, String name) {
      synchronized(activeWaypoints) {
         activeWaypoints.add(new CustomNavWaypoint(x, y, z, name, (String)null, false));
      }
   }

   public static void setNavPath(double x, double y, double z, String name, String island) {
      synchronized(activeWaypoints) {
         activeWaypoints.removeIf((wp) -> wp.isNavPath);
         activeWaypoints.add(new CustomNavWaypoint(x, y, z, name, island, true));
      }

      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         BlockPos startPos = mc.player.blockPosition();
         BlockPos endPos = BlockPos.containing(x, y, z);
         isCalculatingPath = true;
         currentPath = null;
         String targetLoc = island != null && !island.isEmpty() ? island : SkyblockUtils.getLocation();
         CompletableFuture.runAsync(() -> {
            try {
               GraphPathfinder.computePathAsync(targetLoc, startPos, endPos, (path) -> {
                  if (path != null) {
                     currentPath = path;
                  } else {
                     currentPath = new ArrayList();
                  }

               });
               isCalculatingPath = false;
            } catch (Exception e) {
               e.printStackTrace();
               isCalculatingPath = false;
            }

         });
      }

   }

   public static void clearNav() {
      synchronized(activeWaypoints) {
         activeWaypoints.removeIf((wp) -> wp.isNavPath);
         currentPath = null;
         isCalculatingPath = false;
      }
   }

   public static class CustomNavWaypoint {
      public final double x;
      public final double y;
      public final double z;
      public final String name;
      public final String island;
      public final boolean isNavPath;

      public CustomNavWaypoint(double x, double y, double z, String name, String island, boolean isNavPath) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.name = name;
         this.island = island;
         this.isNavPath = isNavPath;
      }
   }
}
