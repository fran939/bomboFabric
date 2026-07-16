package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class WaypointManager {

    public static class CustomNavWaypoint {
        public final double x, y, z;
        public final String name;
        public final String island;
        public final boolean isNavPath; // if true, draws a line to it. if false, just a waypoint

        public CustomNavWaypoint(double x, double y, double z, String name, String island, boolean isNavPath) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
            this.island = island;
            this.isNavPath = isNavPath;
        }
    }

    private static final List<CustomNavWaypoint> activeWaypoints = new ArrayList<>();
    private static List<Vec3> currentPath = null;
    private static boolean isCalculatingPath = false;
    private static int recalcCooldown = 0;

    public static void init() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
    }

    public static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        Vec3 playerPos = mc.player.getEyePosition();

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        VertexConsumer lineBuffer = context.bufferSource().getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent());
        
        synchronized (activeWaypoints) {
            for (CustomNavWaypoint wp : activeWaypoints) {
                if (wp.isNavPath) {
                    if (currentPath != null && !currentPath.isEmpty()) {
                        Vec3 pPos = new Vec3(playerPos.x, playerPos.y, playerPos.z);
                        Vec3 firstNode = currentPath.get(0);
                        
                        // Draw a faint tracer line from player to first node
                        BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer,
                                (float)pPos.x, (float)pPos.y + 1.0f, (float)pPos.z,
                                (float)firstNode.x, (float)firstNode.y, (float)firstNode.z,
                                0.0f, 1.0f, 0.0f, 0.5f, 2.0f);
                                
                        Vec3 prev = firstNode;
                        for (int i = 1; i < currentPath.size(); i++) {
                            Vec3 curr = currentPath.get(i);
                            BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer,
                                (float)prev.x, (float)prev.y, (float)prev.z,
                                (float)curr.x, (float)curr.y, (float)curr.z,
                                0.0f, 1.0f, 0.0f, 1.0f, 3.0f);
                            prev = curr;
                        }
                    } else if (isCalculatingPath) {
                        BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer,
                                (float)playerPos.x, (float)playerPos.y - 0.5f, (float)playerPos.z,
                                (float)wp.x, (float)wp.y, (float)wp.z,
                                0.5f, 0.5f, 0.5f, 1.0f, 2.0f);
                    } else {
                        BomboRenderUtils.drawLine(poseStack.last().pose(), lineBuffer,
                                (float)playerPos.x, (float)playerPos.y - 0.5f, (float)playerPos.z,
                                (float)wp.x, (float)wp.y, (float)wp.z,
                                0.0f, 1.0f, 0.0f, 1.0f, 5.0f);
                    }
                }
                
                // Draw a box around the waypoint
                AABB box = new AABB(wp.x - 0.5, wp.y, wp.z - 0.5, wp.x + 0.5, wp.y + 1.0, wp.z + 0.5);
                BomboRenderUtils.drawBox(poseStack, lineBuffer, box, wp.isNavPath ? 0f : 1f, wp.isNavPath ? 1f : 0.5f, 0f, 1f, 3f);
            }
        }
        
        poseStack.popPose();
    }

    private static net.minecraft.client.multiplayer.ClientLevel lastLevel = null;
    public static String pendingNavTarget = null;
    private static int pendingNavTicks = 0;

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            activeWaypoints.clear();
            currentPath = null;
            if (pendingNavTarget != null) {
                pendingNavTicks = 200; // wait up to 10 seconds for location to load after joining new server
            }
            return;
        }

        if (pendingNavTarget != null && pendingNavTicks > 0) {
            pendingNavTicks--;
            
            String currentLoc = me.bombo.bomboaddons.SkyblockUtils.getLocation();
            if (pendingNavTicks > 0 && (currentLoc == null || currentLoc.equals("Unknown") || currentLoc.equals("None"))) {
                return; // keep waiting until location is known, but don't decrement past 0
            }
            
            if (pendingNavTicks <= 0 || (currentLoc != null && !currentLoc.equals("Unknown") && !currentLoc.equals("None"))) {
                if (mc.player != null) {
                    mc.player.connection.sendCommand("bnav " + pendingNavTarget);
                }
                pendingNavTarget = null;
                pendingNavTicks = 0;
            }
        }

        if (mc.player == null) return;
        Vec3 playerPos = mc.player.position();
        
        if (recalcCooldown > 0) recalcCooldown--;

        synchronized (activeWaypoints) {
            activeWaypoints.removeIf(wp -> {
                double dx = playerPos.x - wp.x;
                double dy = playerPos.y - wp.y;
                double dz = playerPos.z - wp.z;
                double distSq = dx*dx + dy*dy + dz*dz;
                
                if (wp.isNavPath && currentPath != null && !currentPath.isEmpty()) {
                    // Find the closest node on the path
                    int closestIndex = 0;
                    double minSq = Double.MAX_VALUE;
                    for (int i = 0; i < currentPath.size(); i++) {
                        Vec3 node = currentPath.get(i);
                        double sq = playerPos.distanceToSqr(node);
                        if (sq < minSq) {
                            minSq = sq;
                            closestIndex = i;
                        }
                    }
                    
                    // Smoothly remove nodes we have passed or skipped
                    if (minSq < 900.0) { // If we are within 30 blocks of ANY node on the path
                        // Drop all nodes before the closest one
                        for (int i = 0; i < closestIndex; i++) {
                            currentPath.remove(0);
                        }
                        // Also drop the current closest if we're basically touching it (<4 blocks)
                        if (minSq < 16.0 && !currentPath.isEmpty()) {
                            currentPath.remove(0);
                        }
                    } else if (minSq > 900.0 && recalcCooldown <= 0 && !isCalculatingPath) {
                        // Reroute if off path (> 20 blocks from any point on the path)
                        isCalculatingPath = true;
                        recalcCooldown = 100; // 5 seconds cooldown
                        
                        net.minecraft.core.BlockPos startPos = mc.player.blockPosition();
                        net.minecraft.core.BlockPos endPos = net.minecraft.core.BlockPos.containing(wp.x, wp.y, wp.z);
                        String targetLoc = (wp.island != null && !wp.island.isEmpty()) ? wp.island : me.bombo.bomboaddons.SkyblockUtils.getLocation();
                        
                        java.util.concurrent.CompletableFuture.runAsync(() -> {
                            try {
                                GraphPathfinder.computePathAsync(targetLoc, startPos, endPos, path -> {
                                    if (path != null) currentPath = path;
                                    isCalculatingPath = false;
                                });
                            } catch (Exception e) {
                                isCalculatingPath = false;
                            }
                        });
                    }
                }
                
                if (distSq < 9.0) { // < 3 blocks
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aArrived at " + wp.name + "!"));
                    }
                    return true;
                }
                return false;
            });
        }
    }

    public static void addWaypoint(double x, double y, double z, String name) {
        synchronized (activeWaypoints) {
            activeWaypoints.add(new CustomNavWaypoint(x, y, z, name, null, false));
        }
    }

    public static void setNavPath(double x, double y, double z, String name, String island) {
        synchronized (activeWaypoints) {
            activeWaypoints.removeIf(wp -> wp.isNavPath); // only one nav path at a time
            activeWaypoints.add(new CustomNavWaypoint(x, y, z, name, island, true));
        }
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            net.minecraft.core.BlockPos startPos = mc.player.blockPosition();
            net.minecraft.core.BlockPos endPos = net.minecraft.core.BlockPos.containing(x, y, z);
            
            isCalculatingPath = true;
            currentPath = null;
            
            String targetLoc = (island != null && !island.isEmpty()) ? island : SkyblockUtils.getLocation();
            
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    GraphPathfinder.computePathAsync(targetLoc, startPos, endPos, path -> {
                        if (path != null) {
                            currentPath = path;
                        } else {
                            currentPath = new ArrayList<>();
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
        synchronized (activeWaypoints) {
            activeWaypoints.removeIf(wp -> wp.isNavPath);
            currentPath = null;
            isCalculatingPath = false;
        }
    }
}
