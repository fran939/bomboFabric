package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

public class BlockHighlight {
    public static volatile Map<BlockPos, BomboConfig.BlockHighlightInfo> highlightedBlocks = new ConcurrentHashMap<>();
    private static volatile boolean isScanning = false;

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            highlightedBlocks.clear();
            return;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.blockHighlightsEnabled || s.blockHighlights == null || s.blockHighlights.isEmpty()) {
            highlightedBlocks.clear();
            return;
        }

        // Only scan once per 20 ticks (1 second)
        if (mc.player.tickCount % 20 != 0) {
            return;
        }

        if (isScanning) {
            return;
        }

        isScanning = true;

        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        Vec3 playerPos = mc.player.position();
        Map<String, BomboConfig.BlockHighlightInfo> highlightsCopy = new HashMap<>(s.blockHighlights);

        ForkJoinPool.commonPool().execute(() -> {
            try {
                scanBlocks(level, playerPos, highlightsCopy);
            } catch (Throwable t) {
                // Ignore any async exceptions
            } finally {
                isScanning = false;
            }
        });
    }

    private static void scanBlocks(net.minecraft.client.multiplayer.ClientLevel level, Vec3 playerPos, Map<String, BomboConfig.BlockHighlightInfo> targets) {
        Map<BlockPos, BomboConfig.BlockHighlightInfo> newHighlights = new HashMap<>();

        int px = (int) playerPos.x;
        int py = (int) playerPos.y;
        int pz = (int) playerPos.z;

        int minHeight = getMinBuildHeightReflect(level);
        int maxHeight = getMaxBuildHeightReflect(level);

        int startY = Math.max(minHeight, py - 24);
        int endY = Math.min(maxHeight - 1, py + 24);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = px - 32; x <= px + 32; x++) {
            for (int z = pz - 32; z <= pz + 32; z++) {
                for (int y = startY; y <= endY; y++) {
                    mutablePos.set(x, y, z);
                    try {
                        BlockState state = level.getBlockState(mutablePos);
                        if (state.isAir()) {
                            continue;
                        }

                        net.minecraft.resources.Identifier key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                        String idLower = key.toString().toLowerCase();
                        String pathLower = key.getPath().toLowerCase();

                        for (Map.Entry<String, BomboConfig.BlockHighlightInfo> entry : targets.entrySet()) {
                            String query = entry.getKey().toLowerCase();
                            BomboConfig.BlockHighlightInfo info = entry.getValue();
                            if (!info.enabled) continue;

                            if (idLower.contains(query) || pathLower.contains(query)) {
                                newHighlights.put(new BlockPos(x, y, z), info);
                                break; // Match found, no need to check other queries for this block
                            }
                        }
                    } catch (Throwable t) {
                        // Prevent thread failure if block lookup fails
                    }
                }
            }
        }

        highlightedBlocks = newHighlights;
    }

    public static void render(LevelRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.blockHighlightsEnabled && targetChestPos == null) {
            return;
        }
        if (highlightedBlocks.isEmpty() && targetChestPos == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 camPos = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.poseStack();
        me.bombo.bomboaddons.OrderedSubmitNodeCollector collector = new me.bombo.bomboaddons.OrderedSubmitNodeCollector(context.bufferSource());

        // Take local reference of current highlighted blocks to avoid thread race conditions
        Map<BlockPos, BomboConfig.BlockHighlightInfo> currentHighlights = highlightedBlocks;

        for (Map.Entry<BlockPos, BomboConfig.BlockHighlightInfo> entry : currentHighlights.entrySet()) {
            BlockPos pos = entry.getKey();
            BomboConfig.BlockHighlightInfo info = entry.getValue();
            if (!info.enabled) continue;

            double x = pos.getX() - camPos.x;
            double y = pos.getY() - camPos.y;
            double z = pos.getZ() - camPos.z;

            double dist = Math.sqrt(x * x + y * y + z * z);
            if (dist > 64.0) continue; // Optimize by not rendering far away outlines

            int colorHex = BomboRenderUtils.colorNameToHex(info.color);
            float r = ((colorHex >> 16) & 0xFF) / 255.0f;
            float g = ((colorHex >> 8) & 0xFF) / 255.0f;
            float b = (colorHex & 0xFF) / 255.0f;
            float a = 0.85f;

            final AABB box;
            boolean throughWalls = info.throughWalls && !s.hideCheats;
            if (throughWalls) {
                float scale = 1.0f;
                if (dist > 0.2) {
                    scale = 0.2f / (float) dist;
                }
                box = new AABB(
                        x * scale, y * scale, z * scale,
                        (x + 1.0) * scale, (y + 1.0) * scale, (z + 1.0) * scale
                );
            } else {
                box = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
            }

            net.minecraft.client.renderer.rendertype.RenderType renderType = throughWalls ? RenderTypes.linesTranslucent() : RenderTypes.lines();
            collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> {
                BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, 2.0f);
            });
        }

        if (targetChestPos != null) {
            double x = targetChestPos.getX() - camPos.x;
            double y = targetChestPos.getY() - camPos.y;
            double z = targetChestPos.getZ() - camPos.z;
            double dist = Math.sqrt(x * x + y * y + z * z);
            if (dist <= 256.0) {
                double scale = 1.0;
                if (dist > 0.2) {
                    scale = 0.2 / dist;
                }
                AABB box = new AABB(
                        x * scale, y * scale, z * scale,
                        (x + 1.0) * scale, (y + 1.0) * scale, (z + 1.0) * scale
                );
                collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                    BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, 0.0f, 1.0f, 0.0f, 0.85f, 2.0f);
                });
                
                String text = "Target Chest §7(" + (int)dist + "m)";
                BomboRenderUtils.drawText(poseStack, collector, text, (float)(x + 0.5), (float)(y + 1.5), (float)(z + 0.5), 0x00FF00, 0.03f, true, true);
            }
        } else {
            targetChestPos = null;
        }
    }

    public static net.minecraft.core.BlockPos targetChestPos = null;
    public static long targetChestTime = 0;

    private static int getMinBuildHeightReflect(net.minecraft.client.multiplayer.ClientLevel level) {
        try {
            try {
                java.lang.reflect.Method m = level.getClass().getMethod("getBottomY");
                return (Integer) m.invoke(level);
            } catch (NoSuchMethodException e) {
                try {
                    java.lang.reflect.Method m = level.getClass().getMethod("getMinBuildHeight");
                    return (Integer) m.invoke(level);
                } catch (NoSuchMethodException e2) {
                    try {
                        java.lang.reflect.Method m = level.getClass().getMethod("getMinY");
                        return (Integer) m.invoke(level);
                    } catch (NoSuchMethodException e3) {
                        return -64;
                    }
                }
            }
        } catch (Throwable t) {
            return -64;
        }
    }

    private static int getMaxBuildHeightReflect(net.minecraft.client.multiplayer.ClientLevel level) {
        try {
            try {
                java.lang.reflect.Method m = level.getClass().getMethod("getTopY");
                return (Integer) m.invoke(level);
            } catch (NoSuchMethodException e) {
                try {
                    java.lang.reflect.Method m = level.getClass().getMethod("getMaxBuildHeight");
                    return (Integer) m.invoke(level);
                } catch (NoSuchMethodException e2) {
                    try {
                        java.lang.reflect.Method m = level.getClass().getMethod("getMaxY");
                        return (Integer) m.invoke(level);
                    } catch (NoSuchMethodException e3) {
                        try {
                            java.lang.reflect.Method m = level.getClass().getMethod("getHeight");
                            int height = (Integer) m.invoke(level);
                            return getMinBuildHeightReflect(level) + height;
                        } catch (NoSuchMethodException e4) {
                            return 320;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            return 320;
        }
    }
}
