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
               int scanRadius = Math.max(4, Math.min(s.blockScanRadius, 512));
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
                        scanBlocks(level, playerPos, targets, scanRadius);
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

   public static class ParsedBlockRule {
      public final String blockId;
      public final String requiredProps;
      public final BomboConfig.BlockHighlightInfo info;

      public ParsedBlockRule(String query, BomboConfig.BlockHighlightInfo info) {
         this.info = info;
         String q = query.toLowerCase().trim();
         if (q.contains("[") && q.endsWith("]")) {
            this.blockId = q.substring(0, q.indexOf('[')).trim();
            this.requiredProps = q.substring(q.indexOf('[') + 1, q.length() - 1).trim();
         } else if (q.contains(":") && !q.startsWith("minecraft:")) {
            this.blockId = q.substring(0, q.indexOf(':')).trim();
            this.requiredProps = q.substring(q.indexOf(':') + 1).trim();
         } else {
            this.blockId = q;
            this.requiredProps = null;
         }
      }

      public boolean matches(BlockState state) {
         if (state == null) return false;
         Identifier key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
         String rawId = key.toString().toLowerCase();
         String path = key.getPath().toLowerCase();
         if (!rawId.contains(blockId) && !path.contains(blockId)) return false;
         if (requiredProps != null && !requiredProps.isEmpty()) {
            String stateStr = state.toString().toLowerCase();
            String[] reqs = requiredProps.split("[,;]");
            for (String req : reqs) {
               String cleanReq = req.trim().toLowerCase();
               if (!cleanReq.isEmpty() && !stateStr.contains(cleanReq)) {
                  return false;
               }
            }
         }
         return true;
      }
   }

   private static void scanBlocks(ClientLevel level, Vec3 playerPos,
         Map<String, BomboConfig.BlockHighlightInfo> targets, int radius) {
      Map<BlockPos, BomboConfig.BlockHighlightInfo> newHighlights = new HashMap<>();
      int px = (int) playerPos.x;
      int py = (int) playerPos.y;
      int pz = (int) playerPos.z;
      int minHeight = level.getMinY();
      int maxHeight = level.getMaxY();
      int startY = Math.max(minHeight, py - radius);
      int endY = Math.min(maxHeight - 1, py + radius);

      List<ParsedBlockRule> activeRules = new ArrayList<>();
      for (Map.Entry<String, BomboConfig.BlockHighlightInfo> entry : targets.entrySet()) {
         if (entry.getValue() != null && entry.getValue().enabled) {
            activeRules.add(new ParsedBlockRule(entry.getKey(), entry.getValue()));
         }
      }

      if (activeRules.isEmpty()) {
         highlightedBlocks = newHighlights;
         return;
      }

      int minChunkX = (px - radius) >> 4;
      int maxChunkX = (px + radius) >> 4;
      int minChunkZ = (pz - radius) >> 4;
      int maxChunkZ = (pz + radius) >> 4;

      for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
         for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
            if (!level.hasChunk(cx, cz)) continue;
            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunk(cx, cz);
            if (chunk == null) continue;

            net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
            for (int sIdx = 0; sIdx < sections.length; ++sIdx) {
               net.minecraft.world.level.chunk.LevelChunkSection sec = sections[sIdx];
               if (sec == null || sec.hasOnlyAir()) continue;

               int secMinY = chunk.getSectionYFromSectionIndex(sIdx) << 4;
               if (secMinY + 15 < startY || secMinY > endY) continue;

               int baseBlockX = cx << 4;
               int baseBlockZ = cz << 4;

               for (int lx = 0; lx < 16; ++lx) {
                  int bx = baseBlockX + lx;
                  if (bx < px - radius || bx > px + radius) continue;
                  for (int lz = 0; lz < 16; ++lz) {
                     int bz = baseBlockZ + lz;
                     if (bz < pz - radius || bz > pz + radius) continue;
                     for (int ly = 0; ly < 16; ++ly) {
                        int by = secMinY + ly;
                        if (by < startY || by > endY) continue;
                        BlockState state = sec.getBlockState(lx, ly, lz);
                        if (!state.isAir()) {
                           for (ParsedBlockRule rule : activeRules) {
                              if (rule.matches(state)) {
                                 newHighlights.put(new BlockPos(bx, by, bz), rule.info);
                                 break;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      highlightedBlocks = newHighlights;
   }

   public static boolean hasHighlights() {
      return !highlightedBlocks.isEmpty() || !targetChestPosList.isEmpty();
   }

   public static void render(LevelRenderContext context) {
      if (highlightedBlocks.isEmpty() && targetChestPosList.isEmpty()) {
         return;
      }
      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null || mc.player == null) {
            return;
         }

         BomboConfig.Settings s = BomboConfig.get();
         if (s == null || !s.blockHighlightsEnabled) {
            return;
         }

         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = null;
         Map<BlockPos, BomboConfig.BlockHighlightInfo> currentHighlights = highlightedBlocks;

         double maxRenderDist = Math.max(64.0, (double) s.blockScanRadius);
         double maxDistSq = maxRenderDist * maxRenderDist;

         if (!currentHighlights.isEmpty()) {
            for (Map.Entry<BlockPos, BomboConfig.BlockHighlightInfo> entry : currentHighlights.entrySet()) {
               BlockPos pos = entry.getKey();
               BomboConfig.BlockHighlightInfo info = entry.getValue();
               if (info != null && info.enabled) {
                  double x = (double) pos.getX() - camPos.x;
                  double y = (double) pos.getY() - camPos.y;
                  double z = (double) pos.getZ() - camPos.z;
                  double distSq = x * x + y * y + z * z;
                  if (distSq <= maxDistSq) {
                     double dist = Math.sqrt(distSq);
                     int colorHex = BomboRenderUtils.colorNameToHex(info.color);
                     float r = (float) (colorHex >> 16 & 255) / 255.0F;
                     float g = (float) (colorHex >> 8 & 255) / 255.0F;
                     float b = (float) (colorHex & 255) / 255.0F;
                     float a = 1.0F;
                     boolean throughWalls = !s.hideCheats;
                     RenderType renderType = throughWalls ? RenderTypes.linesTranslucent() : RenderTypes.lines();

                     float scale = (throughWalls && dist > 0.2) ? (float) (0.2 / dist) : 1.0F;
                     double minX = x * scale;
                     double minY = y * scale;
                     double minZ = z * scale;
                     double maxX = (x + 1.0) * scale;
                     double maxY = (y + 1.0) * scale;
                     double maxZ = (z + 1.0) * scale;
                     AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

                     if (collector == null) {
                        collector = new OrderedSubmitNodeCollector(context.bufferSource());
                     }

                     collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils
                           .drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, 2.0F));
                  }
               }
            }
         }

         if (!targetChestPosList.isEmpty()) {
            Set<BlockPos> rendered = new HashSet<>();

            for (BlockPos targetChestPos : targetChestPosList) {
               if (!rendered.contains(targetChestPos)) {
                  BlockPos partnerPos = null;

                  try {
                     BlockState state = mc.level.getBlockState(targetChestPos);
                     if (state.getBlock() instanceof ChestBlock) {
                        ChestType type = (ChestType) state.getValue(BlockStateProperties.CHEST_TYPE);
                        if (type != ChestType.SINGLE) {
                           Direction connectedDir = ChestBlock.getConnectedDirection(state);
                           partnerPos = targetChestPos.relative(connectedDir);
                        }
                     }
                  } catch (Exception ignored) {
                  }

                  rendered.add(targetChestPos);
                  double minX = (double) targetChestPos.getX();
                  double minY = (double) targetChestPos.getY();
                  double minZ = (double) targetChestPos.getZ();
                  double maxX = minX + 1.0;
                  double maxY = minY + 1.0;
                  double maxZ = minZ + 1.0;
                  if (partnerPos != null) {
                     rendered.add(partnerPos);
                     minX = Math.min(minX, (double) partnerPos.getX());
                     minY = Math.min(minY, (double) partnerPos.getY());
                     minZ = Math.min(minZ, (double) partnerPos.getZ());
                     maxX = Math.max(maxX, (double) (partnerPos.getX() + 1));
                     maxY = Math.max(maxY, (double) (partnerPos.getY() + 1));
                     maxZ = Math.max(maxZ, (double) (partnerPos.getZ() + 1));
                  }

                  double rx = minX - camPos.x;
                  double ry = minY - camPos.y;
                  double rz = minZ - camPos.z;
                  double rMaxX = maxX - camPos.x;
                  double rMaxY = maxY - camPos.y;
                  double rMaxZ = maxZ - camPos.z;
                  double midX = (minX + maxX) / 2.0 - camPos.x;
                  double midY = (minY + maxY) / 2.0 - camPos.y;
                  double midZ = (minZ + maxZ) / 2.0 - camPos.z;
                  double distSq = midX * midX + midY * midY + midZ * midZ;
                  if (distSq <= 256.0 * 256.0) {
                     double dist = Math.sqrt(distSq);
                     AABB box = new AABB(rx, ry, rz, rMaxX, rMaxY, rMaxZ);
                     if (collector == null) {
                        collector = new OrderedSubmitNodeCollector(context.bufferSource());
                     }
                     collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                           (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, 0.0F,
                                 1.0F, 0.0F, 0.85F, 2.0F));
                     String text = "Target Chest §7(" + (int) dist + "m)";
                     BomboRenderUtils.drawText(poseStack, collector, text, (float) midX,
                           (float) (rMaxY + 0.5), (float) midZ, 65280, 0.03F, true, true);
                  }
               }
            }
         }
      } catch (Throwable ignored) {
      }
   }
}
