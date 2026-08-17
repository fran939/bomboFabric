package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class BlockHighlight {
   public static volatile Map<BlockPos, BomboConfig.BlockHighlightInfo> highlightedBlocks = new ConcurrentHashMap();
   private static volatile boolean isScanning = false;
   public static List<BlockPos> targetChestPosList = new ArrayList();
   public static long targetChestTime = 0L;

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         BomboConfig.Settings s = BomboConfig.get();
         boolean enabled = s.blockHighlightsEnabled;
         if (!enabled) {
            highlightedBlocks.clear();
         } else if (mc.player.tickCount % 20 == 0) {
            if (!isScanning) {
               isScanning = true;
               ClientLevel level = mc.level;
               Vec3 playerPos = mc.player.position();
               Map<String, BomboConfig.BlockHighlightInfo> targets = new HashMap();
               if (s.blockHighlights != null) {
                  targets.putAll(s.blockHighlights);
               }

               if (targets.isEmpty()) {
                  highlightedBlocks.clear();
                  isScanning = false;
               } else {
                  ForkJoinPool.commonPool().execute(() -> {
                     try {
                        scanBlocks(level, playerPos, targets);
                     } catch (Throwable var7) {
                     } finally {
                        isScanning = false;
                     }

                  });
               }
            }
         }
      } else {
         highlightedBlocks.clear();
      }
   }

   private static void scanBlocks(ClientLevel level, Vec3 playerPos, Map<String, BomboConfig.BlockHighlightInfo> targets) {
      Map<BlockPos, BomboConfig.BlockHighlightInfo> newHighlights = new HashMap<>();
      int px = (int)playerPos.x;
      int py = (int)playerPos.y;
      int pz = (int)playerPos.z;
      int minHeight = level.getMinY();
      int maxHeight = level.getMaxY();
      int radius = 16;
      int startY = Math.max(minHeight, py - radius);
      int endY = Math.min(maxHeight - 1, py + radius);

      // Pre-resolve matching blocks once to eliminate thousands of registry & string lookups
      java.util.Map<net.minecraft.world.level.block.Block, BomboConfig.BlockHighlightInfo> matchingBlocks = new java.util.IdentityHashMap<>();
      for (net.minecraft.world.level.block.Block b : BuiltInRegistries.BLOCK) {
         Identifier key = BuiltInRegistries.BLOCK.getKey(b);
         String idLower = key.toString().toLowerCase();
         String pathLower = key.getPath().toLowerCase();
         for (Map.Entry<String, BomboConfig.BlockHighlightInfo> entry : targets.entrySet()) {
            String query = entry.getKey().toLowerCase();
            BomboConfig.BlockHighlightInfo info = entry.getValue();
            if (info != null && info.enabled && (idLower.contains(query) || pathLower.contains(query))) {
               matchingBlocks.put(b, info);
               break;
            }
         }
      }

      if (matchingBlocks.isEmpty()) {
         highlightedBlocks = newHighlights;
         return;
      }

      BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

      for (int x = px - radius; x <= px + radius; ++x) {
         for (int z = pz - radius; z <= pz + radius; ++z) {
            for (int y = startY; y <= endY; ++y) {
               mutablePos.set(x, y, z);
               try {
                  BlockState state = level.getBlockState(mutablePos);
                  if (!state.isAir()) {
                     BomboConfig.BlockHighlightInfo info = matchingBlocks.get(state.getBlock());
                     if (info != null) {
                        newHighlights.put(new BlockPos(x, y, z), info);
                     }
                  }
               } catch (Throwable ignored) {
               }
            }
         }
      }

      highlightedBlocks = newHighlights;
   }

   public static void render(LevelRenderContext context) {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (s == null || (!s.blockHighlightsEnabled && targetChestPosList.isEmpty())) return;
         if (highlightedBlocks.isEmpty() && targetChestPosList.isEmpty()) return;
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null || mc.gameRenderer == null || mc.gameRenderer.getMainCamera() == null) return;
         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
         Map<BlockPos, BomboConfig.BlockHighlightInfo> currentHighlights = highlightedBlocks;

         for(Map.Entry<BlockPos, BomboConfig.BlockHighlightInfo> entry : currentHighlights.entrySet()) {
            BlockPos pos = (BlockPos)entry.getKey();
            BomboConfig.BlockHighlightInfo info = (BomboConfig.BlockHighlightInfo)entry.getValue();
            if (info.enabled) {
               double x = (double)pos.getX() - camPos.x;
               double y = (double)pos.getY() - camPos.y;
               double z = (double)pos.getZ() - camPos.z;
               double dist = Math.sqrt(x * x + y * y + z * z);
               if (!(dist > (double)64.0F)) {
                  int colorHex = BomboRenderUtils.colorNameToHex(info.color);
                  float r = (float)(colorHex >> 16 & 255) / 255.0F;
                  float g = (float)(colorHex >> 8 & 255) / 255.0F;
                  float b = (float)(colorHex & 255) / 255.0F;
                  float a = 1.0F;
                  boolean throughWalls = !s.hideCheats && (info.throughWalls || s.blockHighlightsEnabled);
                  RenderType renderType = throughWalls ? RenderTypes.linesTranslucent() : RenderTypes.lines();

                  BlockState state = mc.level.getBlockState(pos);
                  net.minecraft.world.phys.shapes.VoxelShape shape = state.getShape(mc.level, pos);
                  List<AABB> aabbs = shape.isEmpty() ? Collections.singletonList(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)) : shape.toAabbs();
                  float scale = (throughWalls && dist > 0.2) ? (float)(0.2 / dist) : 1.0F;
                  for (AABB subBox : aabbs) {
                     double minX = (x + subBox.minX) * (double)scale;
                     double minY = (y + subBox.minY) * (double)scale;
                     double minZ = (z + subBox.minZ) * (double)scale;
                     double maxX = (x + subBox.maxX) * (double)scale;
                     double maxY = (y + subBox.maxY) * (double)scale;
                     double maxZ = (z + subBox.maxZ) * (double)scale;
                     AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
                     collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, 2.0F));
                  }
               }
            }
         }

         if (!targetChestPosList.isEmpty()) {
            Set<BlockPos> rendered = new HashSet();

            for(BlockPos targetChestPos : targetChestPosList) {
               if (!rendered.contains(targetChestPos)) {
                  BlockPos partnerPos = null;

                  try {
                     BlockState state = mc.level.getBlockState(targetChestPos);
                     if (state.getBlock() instanceof ChestBlock) {
                        ChestType type = (ChestType)state.getValue(BlockStateProperties.CHEST_TYPE);
                        if (type != ChestType.SINGLE) {
                           Direction connectedDir = ChestBlock.getConnectedDirection(state);
                           partnerPos = targetChestPos.relative(connectedDir);
                        }
                     }
                  } catch (Exception var28) {
                  }

                  rendered.add(targetChestPos);
                  double minX = (double)targetChestPos.getX();
                  double minY = (double)targetChestPos.getY();
                  double minZ = (double)targetChestPos.getZ();
                  double maxX = minX + (double)1.0F;
                  double maxY = minY + (double)1.0F;
                  double maxZ = minZ + (double)1.0F;
                  if (partnerPos != null) {
                     rendered.add(partnerPos);
                     minX = Math.min(minX, (double)partnerPos.getX());
                     minY = Math.min(minY, (double)partnerPos.getY());
                     minZ = Math.min(minZ, (double)partnerPos.getZ());
                     maxX = Math.max(maxX, (double)(partnerPos.getX() + 1));
                     maxY = Math.max(maxY, (double)(partnerPos.getY() + 1));
                     maxZ = Math.max(maxZ, (double)(partnerPos.getZ() + 1));
                  }

                  double rx = minX - camPos.x;
                  double ry = minY - camPos.y;
                  double rz = minZ - camPos.z;
                  double rMaxX = maxX - camPos.x;
                  double rMaxY = maxY - camPos.y;
                  double rMaxZ = maxZ - camPos.z;
                  double midX = (minX + maxX) / (double)2.0F - camPos.x;
                  double midY = (minY + maxY) / (double)2.0F - camPos.y;
                  double midZ = (minZ + maxZ) / (double)2.0F - camPos.z;
                  double dist = Math.sqrt(midX * midX + midY * midY + midZ * midZ);
                  if (dist <= (double)256.0F) {
                     AABB box = new AABB(rx, ry, rz, rMaxX, rMaxY, rMaxZ);
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, 0.0F, 1.0F, 0.0F, 0.85F, 2.0F));
                     String text = "Target Chest §7(" + (int)dist + "m)";
                     BomboRenderUtils.drawText(poseStack, collector, text, (float)midX, (float)(rMaxY + (double)0.5F), (float)midZ, 65280, 0.03F, true, true);
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }
   }

   private static int getMinBuildHeightReflect(ClientLevel level) {
      try {
         try {
            Method m = level.getClass().getMethod("getBottomY");
            return (Integer)m.invoke(level);
         } catch (NoSuchMethodException var6) {
            try {
               Method m = level.getClass().getMethod("getMinBuildHeight");
               return (Integer)m.invoke(level);
            } catch (NoSuchMethodException var5) {
               try {
                  Method m = level.getClass().getMethod("getMinY");
                  return (Integer)m.invoke(level);
               } catch (NoSuchMethodException var4) {
                  return -64;
               }
            }
         }
      } catch (Throwable var7) {
         return -64;
      }
   }

   private static int getMaxBuildHeightReflect(ClientLevel level) {
      try {
         try {
            Method m = level.getClass().getMethod("getTopY");
            return (Integer)m.invoke(level);
         } catch (NoSuchMethodException var9) {
            try {
               Method m = level.getClass().getMethod("getMaxBuildHeight");
               return (Integer)m.invoke(level);
            } catch (NoSuchMethodException var8) {
               try {
                  Method m = level.getClass().getMethod("getMaxY");
                  return (Integer)m.invoke(level);
               } catch (NoSuchMethodException var7) {
                  try {
                     Method m = level.getClass().getMethod("getHeight");
                     int height = (Integer)m.invoke(level);
                     return getMinBuildHeightReflect(level) + height;
                  } catch (NoSuchMethodException var6) {
                     return 320;
                  }
               }
            }
         }
      } catch (Throwable var10) {
         return 320;
      }
   }
}
