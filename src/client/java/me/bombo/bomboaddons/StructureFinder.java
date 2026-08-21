package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

public class StructureFinder {
   public static class FoundStructure {
      public final String name;
      public final BlockPos centerPos;
      public final AABB bounds;
      public final int accuracy;
      public final long foundTime;
      public final StructureScanner.StructurePattern matchedPattern;
      public final int worldOriginX, worldOriginY, worldOriginZ;
      public final int rotation;
      public final List<StructureScanner.ScannedBlock> actualWorldBlocks;

      public FoundStructure(String name, BlockPos centerPos, AABB bounds, int accuracy) {
         this(name, centerPos, bounds, accuracy, null, 0, 0, 0, 0, null);
      }

      public FoundStructure(String name, BlockPos centerPos, AABB bounds, int accuracy,
                            StructureScanner.StructurePattern matchedPattern,
                            int worldOriginX, int worldOriginY, int worldOriginZ, int rotation,
                            List<StructureScanner.ScannedBlock> actualWorldBlocks) {
         this.name = name;
         this.centerPos = centerPos;
         this.bounds = bounds;
         this.accuracy = accuracy;
         this.foundTime = System.currentTimeMillis();
         this.matchedPattern = matchedPattern;
         this.worldOriginX = worldOriginX;
         this.worldOriginY = worldOriginY;
         this.worldOriginZ = worldOriginZ;
         this.rotation = rotation;
         this.actualWorldBlocks = actualWorldBlocks;
      }
   }

   public static final Map<String, FoundStructure> foundStructures = new ConcurrentHashMap<>();
   public static volatile FoundStructure lastMatchedStructure = null;
   private static volatile boolean isScanning = false;
   private static final Map<String, Integer> lastAlertedAccuracy = new ConcurrentHashMap<>();

   public static void clear() {
      foundStructures.clear();
      lastAlertedAccuracy.clear();
      GardenWaypoints.clearStructureWaypoints();
   }

   public static void resetAlerts() {
      lastAlertedAccuracy.clear();
   }

   public static boolean isScanAllowed() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.hasSingleplayerServer()) {
         return true; // Singleplayer testing support!
      }
      String area = BomboaddonsClient.currentArea;
      if (area != null) {
         String lower = area.toLowerCase();
         if (lower.contains("crystal") || lower.contains("hollow") || lower.contains("mithril") || lower.contains("mine") || lower.contains("goblin") || lower.contains("jungle") || lower.contains("precursor")) {
            return true;
         }
      }
      return SkyblockUtils.matchesIslandRequirement("Crystal Hollows") || SkyblockUtils.matchesIslandRequirement("Mithril Deposits");
   }

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         clear();
         return;
      }

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.structureFinder) {
         if (!foundStructures.isEmpty()) {
            clear();
         }
         return;
      }

      if (!isScanAllowed()) {
         if (!foundStructures.isEmpty()) {
            clear();
         }
         return;
      }

      if (mc.player.tickCount % 20 == 0) {
         if (!isScanning) {
            isScanning = true;
            ClientLevel level = mc.level;
            Vec3 playerPos = mc.player.position();
            int scanRadius = Math.max(32, Math.min(s.structureFinderRadius, 384));
            boolean searchCorleone1 = s.structureFinderCorleone1;

            ForkJoinPool.commonPool().execute(() -> {
               try {
                  scanStructuresAsync(level, playerPos, scanRadius, searchCorleone1);
               } catch (Throwable ignored) {
               } finally {
                  isScanning = false;
               }
            });
         }
      }
   }

   private static void scanStructuresAsync(ClientLevel level, Vec3 playerPos, int radius, boolean checkCorleone1) {
      if (level == null || playerPos == null) return;

      int px = (int) playerPos.x;
      int py = (int) playerPos.y;
      int pz = (int) playerPos.z;

      int minChunkX = (px - radius) >> 4;
      int maxChunkX = (px + radius) >> 4;
      int minChunkZ = (pz - radius) >> 4;
      int maxChunkZ = (pz + radius) >> 4;

      int minY = Math.max(level.getMinY(), py - radius);
      int maxY = Math.min(level.getMaxY() - 1, py + radius);

      List<StructureScanner.StructurePattern> activePatterns = new ArrayList<>();
      if (checkCorleone1) {
         StructureScanner.StructurePattern corleone = StructureScanner.loadedPatterns.get("corleone1");
         if (corleone != null) activePatterns.add(corleone);
      }

      for (Map.Entry<String, StructureScanner.StructurePattern> entry : StructureScanner.loadedPatterns.entrySet()) {
         String key = entry.getKey();
         if (!key.equalsIgnoreCase("corleone1") && !key.equalsIgnoreCase("corleone 1") && !key.equalsIgnoreCase("bugged") && !activePatterns.contains(entry.getValue())) {
            activePatterns.add(entry.getValue());
         }
      }

      FoundStructure bestCorleone = null;
      Map<String, FoundStructure> bestCustomMatches = new HashMap<>();

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
               if (secMinY + 15 < minY || secMinY > maxY) continue;

               int baseBlockX = cx << 4;
               int baseBlockZ = cz << 4;

               for (int lx = 0; lx < 16; ++lx) {
                  int bx = baseBlockX + lx;
                  for (int lz = 0; lz < 16; ++lz) {
                     int bz = baseBlockZ + lz;
                     for (int ly = 0; ly < 16; ++ly) {
                        int by = secMinY + ly;
                        BlockState st = sec.getBlockState(lx, ly, lz);
                        if (st.isAir()) continue;

                        // 1. Direct High-Precision Catwalk Span Detection
                        if (checkCorleone1 && (isCatwalkCenterBlock(st) || isStoneBrickBlock(st))) {
                           FoundStructure found = checkCatwalkBridgeAt(level, bx, by, bz);
                           if (found != null && (bestCorleone == null || found.accuracy > bestCorleone.accuracy)) {
                              bestCorleone = found;
                           }
                        }

                        // 2. Pattern Matching for Custom Patterns
                        for (StructureScanner.StructurePattern pat : activePatterns) {
                           if (matchesScannedId(st, pat.anchorBlockId)) {
                              FoundStructure found = matchRotatedPatternAt(level, bx, by, bz, pat);
                              if (found != null) {
                                 FoundStructure current = bestCustomMatches.get(pat.name);
                                 if (current == null || found.accuracy > current.accuracy) {
                                    bestCustomMatches.put(pat.name, found);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      if (bestCorleone != null) {
         foundStructures.put("Corleone 1", bestCorleone);
         lastMatchedStructure = bestCorleone;
         alertFound("Corleone 1", bestCorleone.centerPos, bestCorleone.accuracy);
      }
      for (Map.Entry<String, FoundStructure> e : bestCustomMatches.entrySet()) {
         foundStructures.put(e.getKey(), e.getValue());
         lastMatchedStructure = e.getValue();
         alertFound(e.getKey(), e.getValue().centerPos, e.getValue().accuracy);
      }
   }

   private static boolean isCatwalkCenterBlock(BlockState st) {
      if (st == null || st.isAir()) return false;
      return st.is(Blocks.CYAN_TERRACOTTA) || st.is(Blocks.LIGHT_GRAY_TERRACOTTA) ||
             st.is(Blocks.GRAY_TERRACOTTA) || st.is(Blocks.TERRACOTTA) ||
             st.is(Blocks.BROWN_TERRACOTTA) || st.is(Blocks.RED_TERRACOTTA) ||
             st.is(Blocks.ORANGE_TERRACOTTA) ||
             st.is(Blocks.SMOOTH_STONE) || st.is(Blocks.SMOOTH_STONE_SLAB);
   }

   private static boolean isStoneBrickBlock(BlockState st) {
      if (st == null || st.isAir()) return false;
      return st.is(Blocks.STONE_BRICKS) || st.is(Blocks.CRACKED_STONE_BRICKS) ||
             st.is(Blocks.MOSSY_STONE_BRICKS) || st.is(Blocks.STONE_BRICK_STAIRS) ||
             st.is(Blocks.STONE_BRICK_SLAB);
   }

   private static FoundStructure checkCatwalkBridgeAt(ClientLevel level, int bx, int by, int bz) {
      BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

      // Check clearance directly above this candidate block
      if (!level.getBlockState(mut.set(bx, by + 1, bz)).isAir() || !level.getBlockState(mut.set(bx, by + 2, bz)).isAir()) return null;

      // 1. Check bridge along X-axis (Width 5 or 3)
      for (int width : new int[]{5, 3}) {
         int halfW = width / 2;
         for (int zOffset = -halfW; zOffset <= halfW; ++zOffset) {
            int centerZ = bz - zOffset;

            int minX = bx;
            while (minX > bx - 35) {
               int nextX = minX - 1;
               boolean hasEdge1 = isStoneBrickBlock(level.getBlockState(mut.set(nextX, by, centerZ - halfW)));
               boolean hasEdge2 = isStoneBrickBlock(level.getBlockState(mut.set(nextX, by, centerZ + halfW)));
               boolean hasCenter = isCatwalkCenterBlock(level.getBlockState(mut.set(nextX, by, centerZ))) ||
                                   isStoneBrickBlock(level.getBlockState(mut.set(nextX, by, centerZ)));
               if ((hasEdge1 || hasEdge2) && hasCenter) {
                  minX = nextX;
               } else {
                  break;
               }
            }

            int maxX = bx;
            while (maxX < bx + 35) {
               int nextX = maxX + 1;
               boolean hasEdge1 = isStoneBrickBlock(level.getBlockState(mut.set(nextX, by, centerZ - halfW)));
               boolean hasEdge2 = isStoneBrickBlock(level.getBlockState(mut.set(nextX, by, centerZ + halfW)));
               boolean hasCenter = isCatwalkCenterBlock(level.getBlockState(mut.set(nextX, by, centerZ))) ||
                                   isStoneBrickBlock(level.getBlockState(mut.set(nextX, by, centerZ)));
               if ((hasEdge1 || hasEdge2) && hasCenter) {
                  maxX = nextX;
               } else {
                  break;
               }
            }

            int length = maxX - minX + 1;
            if (length >= 9) {
               int centerTerracottaCount = 0;
               int openAirAboveCount = 0;
               int matched = 0;
               int total = length * width;

               for (int x = minX; x <= maxX; ++x) {
                  // Check center block
                  BlockState centerSt = level.getBlockState(mut.set(x, by, centerZ));
                  if (isCatwalkCenterBlock(centerSt)) {
                     centerTerracottaCount++;
                  }
                  if (level.getBlockState(mut.set(x, by + 1, centerZ)).isAir()) {
                     openAirAboveCount++;
                  }

                  for (int dz = -halfW; dz <= halfW; ++dz) {
                     BlockState s = level.getBlockState(mut.set(x, by, centerZ + dz));
                     if (Math.abs(dz) == halfW) {
                        if (isStoneBrickBlock(s)) matched++;
                     } else {
                        if (isCatwalkCenterBlock(s) || isStoneBrickBlock(s)) matched++;
                     }
                  }
               }

               // The real Corleone bridge has terracotta in the center and open air clearance above
               if (centerTerracottaCount >= 2 && openAirAboveCount >= length * 0.7) {
                  int accuracy = (int) Math.round(((double) matched / (double) total) * 100.0);
                  if (accuracy >= 60) {
                     BlockPos center = new BlockPos((minX + maxX) / 2, by + 1, centerZ);
                     AABB bounds = new AABB(minX - 0.5, by, centerZ - halfW - 0.5, maxX + 1.5, by + 5, centerZ + halfW + 1.5);
                     List<StructureScanner.ScannedBlock> actualBlocks = new ArrayList<>();
                     for (int x = minX; x <= maxX; ++x) {
                        for (int dz = -halfW; dz <= halfW; ++dz) {
                           BlockState s = level.getBlockState(mut.set(x, by, centerZ + dz));
                           if (!s.isAir()) {
                              String id = StructureScanner.getBlockIdentifier(s);
                              boolean isMatched = (Math.abs(dz) == halfW && isStoneBrickBlock(s)) || (isCatwalkCenterBlock(s) || isStoneBrickBlock(s));
                              actualBlocks.add(new StructureScanner.ScannedBlock(x - minX, 0, dz + halfW, id, isMatched));
                           }
                        }
                     }
                     StructureScanner.StructurePattern pat = StructureScanner.loadedPatterns.get("corleone1");
                     return new FoundStructure("Corleone 1", center, bounds, accuracy, pat, minX, by, centerZ - halfW, 0, actualBlocks);
                  }
               }
            }
         }
      }

      // 2. Check bridge along Z-axis (The standard orientation from the screenshot)
      for (int width : new int[]{5, 3}) {
         int halfW = width / 2;
         for (int xOffset = -halfW; xOffset <= halfW; ++xOffset) {
            int centerX = bx - xOffset;

            int minZ = bz;
            while (minZ > bz - 35) {
               int nextZ = minZ - 1;
               boolean hasEdge1 = isStoneBrickBlock(level.getBlockState(mut.set(centerX - halfW, by, nextZ)));
               boolean hasEdge2 = isStoneBrickBlock(level.getBlockState(mut.set(centerX + halfW, by, nextZ)));
               boolean hasCenter = isCatwalkCenterBlock(level.getBlockState(mut.set(centerX, by, nextZ))) ||
                                   isStoneBrickBlock(level.getBlockState(mut.set(centerX, by, nextZ)));
               if ((hasEdge1 || hasEdge2) && hasCenter) {
                  minZ = nextZ;
               } else {
                  break;
               }
            }

            int maxZ = bz;
            while (maxZ < bz + 35) {
               int nextZ = maxZ + 1;
               boolean hasEdge1 = isStoneBrickBlock(level.getBlockState(mut.set(centerX - halfW, by, nextZ)));
               boolean hasEdge2 = isStoneBrickBlock(level.getBlockState(mut.set(centerX + halfW, by, nextZ)));
               boolean hasCenter = isCatwalkCenterBlock(level.getBlockState(mut.set(centerX, by, nextZ))) ||
                                   isStoneBrickBlock(level.getBlockState(mut.set(centerX, by, nextZ)));
               if ((hasEdge1 || hasEdge2) && hasCenter) {
                  maxZ = nextZ;
               } else {
                  break;
               }
            }

            int length = maxZ - minZ + 1;
            if (length >= 9) {
               int centerTerracottaCount = 0;
               int openAirAboveCount = 0;
               int matched = 0;
               int total = length * width;

               for (int z = minZ; z <= maxZ; ++z) {
                  BlockState centerSt = level.getBlockState(mut.set(centerX, by, z));
                  if (isCatwalkCenterBlock(centerSt)) {
                     centerTerracottaCount++;
                  }
                  if (level.getBlockState(mut.set(centerX, by + 1, z)).isAir()) {
                     openAirAboveCount++;
                  }

                  for (int dx = -halfW; dx <= halfW; ++dx) {
                     BlockState s = level.getBlockState(mut.set(centerX + dx, by, z));
                     if (Math.abs(dx) == halfW) {
                        if (isStoneBrickBlock(s)) matched++;
                     } else {
                        if (isCatwalkCenterBlock(s) || isStoneBrickBlock(s)) matched++;
                     }
                  }
               }

               if (centerTerracottaCount >= 2 && openAirAboveCount >= length * 0.7) {
                  int accuracy = (int) Math.round(((double) matched / (double) total) * 100.0);
                  if (accuracy >= 60) {
                     BlockPos center = new BlockPos(centerX, by + 1, (minZ + maxZ) / 2);
                     AABB bounds = new AABB(centerX - halfW - 0.5, by, minZ - 0.5, centerX + halfW + 1.5, by + 5, maxZ + 1.5);
                     List<StructureScanner.ScannedBlock> actualBlocks = new ArrayList<>();
                     for (int z = minZ; z <= maxZ; ++z) {
                        for (int dx = -halfW; dx <= halfW; ++dx) {
                           BlockState s = level.getBlockState(mut.set(centerX + dx, by, z));
                           if (!s.isAir()) {
                              String id = StructureScanner.getBlockIdentifier(s);
                              boolean isMatched = (Math.abs(dx) == halfW && isStoneBrickBlock(s)) || (isCatwalkCenterBlock(s) || isStoneBrickBlock(s));
                              actualBlocks.add(new StructureScanner.ScannedBlock(dx + halfW, 0, z - minZ, id, isMatched));
                           }
                        }
                     }
                     StructureScanner.StructurePattern pat = StructureScanner.loadedPatterns.get("corleone1");
                     return new FoundStructure("Corleone 1", center, bounds, accuracy, pat, centerX - halfW, by, minZ, 1, actualBlocks);
                  }
               }
            }
         }
      }

      return null;
   }

   private static FoundStructure matchRotatedPatternAt(ClientLevel level, int anchorX, int anchorY, int anchorZ, StructureScanner.StructurePattern pat) {
      if (pat.blocks.isEmpty()) return null;

      BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
      FoundStructure bestMatch = null;
      int highestAccuracy = 0;

      for (int rot = 0; rot < 4; ++rot) {
         int ancRx = pat.anchorRelX;
         int ancRy = pat.anchorRelY;
         int ancRz = pat.anchorRelZ;

         int rotatedAncDx = getRotatedX(ancRx, ancRz, rot);
         int rotatedAncDz = getRotatedZ(ancRx, ancRz, rot);

         int worldOriginX = anchorX - rotatedAncDx;
         int worldOriginY = anchorY - ancRy;
         int worldOriginZ = anchorZ - rotatedAncDz;

         int total = pat.blocks.size();
         int loadedTotal = 0;
         int matched = 0;

         for (StructureScanner.ScannedBlock b : pat.blocks) {
            int wx = worldOriginX + getRotatedX(b.relX, b.relZ, rot);
            int wy = worldOriginY + b.relY;
            int wz = worldOriginZ + getRotatedZ(b.relX, b.relZ, rot);

            int chunkX = wx >> 4;
            int chunkZ = wz >> 4;
            if (level.hasChunk(chunkX, chunkZ)) {
               loadedTotal++;
               BlockState st = level.getBlockState(mut.set(wx, wy, wz));
               if (matchesScannedId(st, b.blockId)) {
                  matched++;
               }
            }
         }

         int accuracy = loadedTotal > 0 ? (int) Math.round(((double) matched / (double) loadedTotal) * 100.0) : 0;
         int minRequiredMatches = Math.min(25, Math.max(5, pat.blocks.size() / 10));
         if (loadedTotal >= 20 && matched >= minRequiredMatches && accuracy >= 60 && accuracy > highestAccuracy) {
            highestAccuracy = accuracy;
            int minRotX = Math.min(getRotatedX(0, 0, rot), getRotatedX(pat.sizeX - 1, pat.sizeZ - 1, rot));
            int maxRotX = Math.max(getRotatedX(0, 0, rot), getRotatedX(pat.sizeX - 1, pat.sizeZ - 1, rot));
            int minRotZ = Math.min(getRotatedZ(0, 0, rot), getRotatedZ(pat.sizeX - 1, pat.sizeZ - 1, rot));
            int maxRotZ = Math.max(getRotatedZ(0, 0, rot), getRotatedZ(pat.sizeX - 1, pat.sizeZ - 1, rot));

            AABB box = new AABB(
               worldOriginX + minRotX - 0.5,
               worldOriginY - 0.5,
               worldOriginZ + minRotZ - 0.5,
               worldOriginX + maxRotX + 1.5,
               worldOriginY + pat.sizeY + 0.5,
               worldOriginZ + maxRotZ + 1.5
            );
            BlockPos center = new BlockPos(worldOriginX + (minRotX + maxRotX) / 2, worldOriginY + pat.sizeY / 2, worldOriginZ + (minRotZ + maxRotZ) / 2);
            List<StructureScanner.ScannedBlock> actualBlocks = new ArrayList<>();
            for (StructureScanner.ScannedBlock b : pat.blocks) {
               int wx = worldOriginX + getRotatedX(b.relX, b.relZ, rot);
               int wy = worldOriginY + b.relY;
               int wz = worldOriginZ + getRotatedZ(b.relX, b.relZ, rot);
               BlockState st = level.getBlockState(mut.set(wx, wy, wz));
               if (matchesScannedId(st, b.blockId) && !st.isAir()) {
                  String actualId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString();
                  actualBlocks.add(new StructureScanner.ScannedBlock(b.relX, b.relY, b.relZ, actualId));
               }
            }
            bestMatch = new FoundStructure(pat.name, center, box, accuracy, pat, worldOriginX, worldOriginY, worldOriginZ, rot, actualBlocks);
         }
      }

      return bestMatch;
   }

   public static int getRotatedX(int rx, int rz, int rot) {
      switch (rot) {
         case 1: return -rz;
         case 2: return -rx;
         case 3: return rz;
         default: return rx;
      }
   }

   public static int getRotatedZ(int rx, int rz, int rot) {
      switch (rot) {
         case 1: return rx;
         case 2: return -rz;
         case 3: return -rx;
         default: return rz;
      }
   }

   private static boolean matchesScannedId(BlockState st, String id) {
      if (st == null || id == null) return false;
      String stId = StructureScanner.getBlockIdentifier(st);
      if (stId.equalsIgnoreCase(id)) return true;
      if (id.contains("stone_brick")) {
         return stId.contains("stone_brick");
      }
      if (id.contains("terracotta")) {
         return stId.contains("terracotta");
      }
      if (id.contains("prismarine")) {
         return stId.contains("prismarine");
      }
      if (id.contains("spruce")) {
         return stId.contains("spruce");
      }
      if (id.contains("glass")) {
         return stId.contains("glass");
      }
      return false;
   }

   private static void alertFound(String name, BlockPos pos, int accuracy) {
      String key = name + "@" + (pos.getX() / 16) + "," + (pos.getZ() / 16);
      Integer lastAcc = lastAlertedAccuracy.get(key);
      if (lastAcc != null && Math.abs(lastAcc - accuracy) < 5) {
         return;
      }
      lastAlertedAccuracy.put(key, accuracy);

      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         MutableComponent msg = Component.literal("§8[§3Bombo§8] §6★ Found Structure: §b" + name + " §a(" + accuracy + "% match) §7at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " ");
         MutableComponent wpBtn = Component.literal("§a§l[ADD WAYPOINT] ").withStyle(style ->
            style.withClickEvent(new ClickEvent.RunCommand("/b wp " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + name))
         );
         MutableComponent copyBtn = Component.literal("§b§l[COPY]").withStyle(style ->
            style.withClickEvent(new ClickEvent.RunCommand("/b scan copy"))
         );
         msg.append(wpBtn).append(copyBtn);
         if (mc.gui != null && mc.gui.getChat() != null) {
            mc.gui.getChat().addClientSystemMessage(msg);
         } else {
            mc.player.sendSystemMessage(msg);
         }

         // Add persistent waypoint on the catwalk floor
         GardenWaypoints.addWaypoint(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), "[Structure] " + name + " (" + accuracy + "%)");
         WaypointManager.addWaypoint(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, "[Structure] " + name + " (" + accuracy + "%)");
      }
   }

   public static void render(LevelRenderContext context) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || foundStructures.isEmpty()) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.structureFinder) return;

      Vec3 camPos = mc.gameRenderer.getMainCamera().position();
      PoseStack poseStack = context.poseStack();
      OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());

      for (FoundStructure fs : foundStructures.values()) {
         AABB b = fs.bounds;
         AABB relBox = new AABB(
            b.minX - camPos.x, b.minY - camPos.y, b.minZ - camPos.z,
            b.maxX - camPos.x, b.maxY - camPos.y, b.maxZ - camPos.z
         );

         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> 
            BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, relBox, 0.0F, 1.0F, 1.0F, 0.8F, 2.5F)
         );

         double targetX = (double)fs.centerPos.getX() + 0.5 - camPos.x;
         double targetY = (double)fs.centerPos.getY() + 1.2 - camPos.y;
         double targetZ = (double)fs.centerPos.getZ() + 0.5 - camPos.z;

         double dist = Math.sqrt(targetX * targetX + targetY * targetY + targetZ * targetZ);
         String text = "§b§l[Structure] " + fs.name + " §a(" + fs.accuracy + "%) §7(" + (int)dist + "m)";
         BomboRenderUtils.drawText(poseStack, collector, text, (float)targetX, (float)targetY, (float)targetZ, 65535, 0.035F, true, true);

         Vector3fc forward = mc.gameRenderer.getMainCamera().forwardVector();
         float startX = forward.x() * 0.4F;
         float startY = forward.y() * 0.4F;
         float startZ = forward.z() * 0.4F;

         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> 
            BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, (float)targetX, (float)(targetY - 1.2), (float)targetZ, 0.0F, 1.0F, 1.0F, 0.75F, 2.0F)
         );
      }
   }
}
