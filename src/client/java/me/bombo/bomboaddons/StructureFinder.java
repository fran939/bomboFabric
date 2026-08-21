package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Collections;
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
      return area != null && !area.equalsIgnoreCase("None") && !area.equalsIgnoreCase("Lobby") && !area.equalsIgnoreCase("Limbo");
   }

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         clear();
         return;
      }

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.structureFinder || s.hideCheats) {
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
            boolean searchGoldenDragon = s.structureFinderGoldenDragon;

            ForkJoinPool.commonPool().execute(() -> {
               try {
                  scanStructuresAsync(level, playerPos, scanRadius, searchCorleone1, searchGoldenDragon);
               } catch (Throwable ignored) {
               } finally {
                  isScanning = false;
               }
            });
         }
      }
   }

   public static String getDisplayName(String name) {
      if (name == null) return "Structure";
      String lower = name.toLowerCase();
      if (lower.startsWith("corleone")) return "Corleone 1";
      if (lower.startsWith("goldendragon") || lower.startsWith("gdrag") || lower.contains("golden dragon")) return "Golden Dragon";
      return name;
   }

   private static void scanStructuresAsync(ClientLevel level, Vec3 playerPos, int radius, boolean checkCorleone1, boolean checkGoldenDragon) {
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
         for (String name : new String[]{"corleone1", "corleone2"}) {
            StructureScanner.StructurePattern corleone = StructureScanner.loadedPatterns.get(name);
            if (corleone != null && !activePatterns.contains(corleone)) activePatterns.add(corleone);
         }
      }
      if (checkGoldenDragon) {
         for (String name : new String[]{"gdrag1", "gdrag2", "gdrag3", "goldendragon1"}) {
            StructureScanner.StructurePattern gdrag = StructureScanner.loadedPatterns.get(name);
            if (gdrag != null && !activePatterns.contains(gdrag)) activePatterns.add(gdrag);
         }
         for (Map.Entry<String, StructureScanner.StructurePattern> entry : StructureScanner.loadedPatterns.entrySet()) {
            String k = entry.getKey().toLowerCase();
            if ((k.startsWith("gdrag") || k.contains("golden dragon") || k.contains("goldendragon")) && !activePatterns.contains(entry.getValue())) {
               activePatterns.add(entry.getValue());
            }
         }
      }

      for (Map.Entry<String, StructureScanner.StructurePattern> entry : StructureScanner.loadedPatterns.entrySet()) {
         String key = entry.getKey().toLowerCase();
         if (!key.startsWith("corleone") &&
             !key.startsWith("goldendragon") &&
             !key.startsWith("gdrag") &&
             !key.contains("golden dragon") &&
             !key.equalsIgnoreCase("bugged") && !key.equalsIgnoreCase("copy") &&
             !activePatterns.contains(entry.getValue())) {
            activePatterns.add(entry.getValue());
         }
      }

      if (activePatterns.isEmpty()) return;

      Map<String, FoundStructure> bestMatches = new HashMap<>();

      int centerChunkX = px >> 4;
      int centerChunkZ = pz >> 4;

      List<long[]> chunkCoords = new ArrayList<>();
      for (int cx = minChunkX; cx <= maxChunkX; ++cx) {
         for (int cz = minChunkZ; cz <= maxChunkZ; ++cz) {
            if (level.hasChunk(cx, cz)) {
               long dx = cx - centerChunkX;
               long dz = cz - centerChunkZ;
               chunkCoords.add(new long[]{cx, cz, dx * dx + dz * dz});
            }
         }
      }
      // Sort radially: closest chunks to player scanned first!
      chunkCoords.sort((a, b) -> Long.compare(a[2], b[2]));

      for (long[] coord : chunkCoords) {
         int cx = (int) coord[0];
         int cz = (int) coord[1];
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

                     String stId = StructureScanner.getBlockIdentifier(st);
                     // Fast signature pre-filtering: skip stone, dirt, bedrock, lava, etc. in O(1)
                     if (!stId.contains("terracotta") && !stId.contains("sandstone") && !stId.contains("stone_brick") &&
                         !stId.contains("prismarine") && !stId.contains("wool") && !stId.contains("spruce") && !stId.contains("smooth_stone")) {
                        continue;
                     }

                     for (StructureScanner.StructurePattern pat : activePatterns) {
                        List<StructureScanner.ScannedBlock> anchors = (pat.sampleAnchors != null && !pat.sampleAnchors.isEmpty())
                           ? pat.sampleAnchors
                           : Collections.singletonList(new StructureScanner.ScannedBlock(pat.anchorRelX, pat.anchorRelY, pat.anchorRelZ, pat.anchorBlockId));

                        for (StructureScanner.ScannedBlock anchor : anchors) {
                           if (matchesScannedId(st, anchor.blockId)) {
                              FoundStructure found = matchRotatedPatternAtAnchor(level, bx, by, bz, pat, anchor);
                              if (found != null) {
                                 String displayName = getDisplayName(pat.name);
                                 FoundStructure current = bestMatches.get(displayName);
                                 if (current == null || found.accuracy > current.accuracy) {
                                    bestMatches.put(displayName, found);
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

      for (Map.Entry<String, FoundStructure> e : bestMatches.entrySet()) {
         foundStructures.put(e.getKey(), e.getValue());
         lastMatchedStructure = e.getValue();
         alertFound(e.getKey(), e.getValue().centerPos, e.getValue().accuracy);
      }
   }

   private static FoundStructure matchRotatedPatternAtAnchor(ClientLevel level, int worldAnchorX, int worldAnchorY, int worldAnchorZ, StructureScanner.StructurePattern pat, StructureScanner.ScannedBlock anchor) {
      if (pat.blocks.isEmpty()) return null;

      BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
      FoundStructure bestMatch = null;
      int highestAccuracy = 0;

      for (int rot = 0; rot < 4; ++rot) {
         int ancRx = anchor.relX;
         int ancRy = anchor.relY;
         int ancRz = anchor.relZ;

         int rotatedAncDx = getRotatedX(ancRx, ancRz, rot);
         int rotatedAncDz = getRotatedZ(ancRx, ancRz, rot);

         int worldOriginX = worldAnchorX - rotatedAncDx;
         int worldOriginY = worldAnchorY - ancRy;
         int worldOriginZ = worldAnchorZ - rotatedAncDz;

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

         // Accuracy is measured against the full structure template
         int accuracy = total > 0 ? (int) Math.round(((double) matched / (double) total) * 100.0) : 0;
         int reqThreshold = 40;
         if (pat.name != null && (pat.name.toLowerCase().contains("gdrag") || pat.name.toLowerCase().contains("dragon") || pat.name.toLowerCase().contains("gold"))) {
            reqThreshold = 75;
         }
         int minRequiredMatches = Math.max(20, (int) Math.round(total * (reqThreshold / 100.0)));
         int minLoadedThreshold = Math.max(30, (int) Math.round(total * Math.min(0.70, reqThreshold / 100.0)));
         if (loadedTotal >= minLoadedThreshold && matched >= minRequiredMatches && accuracy >= reqThreshold && accuracy > highestAccuracy) {
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
               boolean isMatch = matchesScannedId(st, b.blockId);
               String actualId = !st.isAir() ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().replace("minecraft:", "") : b.blockId;
               actualBlocks.add(new StructureScanner.ScannedBlock(b.relX, b.relY, b.relZ, actualId, isMatch));
            }
            String displayName = getDisplayName(pat.name);
            bestMatch = new FoundStructure(displayName, center, box, accuracy, pat, worldOriginX, worldOriginY, worldOriginZ, rot, actualBlocks);
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

   public static boolean matchesScannedId(BlockState st, String id) {
      if (st == null || id == null) return false;
      String stId = StructureScanner.getBlockIdentifier(st);
      if (stId.equalsIgnoreCase(id)) return true;
      if (id.contains("stone_brick") && stId.contains("stone_brick")) {
         return true;
      }
      if (id.contains("prismarine") && stId.contains("prismarine")) {
         return true;
      }
      if (id.contains("spruce") && stId.contains("spruce")) {
         return true;
      }
      if (id.contains("sandstone") && stId.contains("sandstone") && !id.contains("red_") && !stId.contains("red_")) {
         return true;
      }
      if (id.contains("red_sandstone") && stId.contains("red_sandstone")) {
         return true;
      }
      if (id.contains("wool") && stId.contains("wool")) {
         return true;
      }
      // Catwalk terracotta tolerance: In Skyblock, griefed or naturally generated catwalks can have mixed terracotta or smooth stone
      if ((id.contains("terracotta") || id.contains("smooth_stone")) && (stId.contains("terracotta") || stId.contains("smooth_stone"))) {
         return true;
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

      Bomboaddons.LOGGER.info("[StructureFinder] Found " + name + " (" + accuracy + "%) at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());

      Minecraft mc = Minecraft.getInstance();
      mc.execute(() -> {
         if (mc.player != null) {
            MutableComponent msg = Component.literal("§8[§3Bombo§8] §6★ Found Structure: §b" + name + " §a(" + accuracy + "% match) §7at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " ");
            MutableComponent wpBtn = Component.literal("§a§l[ADD WAYPOINT] ").withStyle(style ->
               style.withClickEvent(new ClickEvent.RunCommand("/b wp " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + name))
            );
            MutableComponent copyBtn = Component.literal("§b§l[COPY]").withStyle(style ->
               style.withClickEvent(new ClickEvent.RunCommand("/b scan copy"))
            );
            msg.append(wpBtn).append(copyBtn);
            mc.player.sendSystemMessage(msg);

            // Add persistent waypoint on the structure center
            GardenWaypoints.addWaypoint(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), "[Structure] " + name + " (" + accuracy + "%)");
            WaypointManager.addWaypoint(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, "[Structure] " + name + " (" + accuracy + "%)");
         }
      });
   }

   public static void render(LevelRenderContext context) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || foundStructures.isEmpty()) return;

      BomboConfig.Settings s = BomboConfig.get();
      if (!s.structureFinder || s.hideCheats) return;

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
