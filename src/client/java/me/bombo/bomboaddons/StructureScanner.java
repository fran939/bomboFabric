package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class StructureScanner {
   public static class ScannedBlock {
      public final int relX;
      public final int relY;
      public final int relZ;
      public final String blockId;
      public boolean matched = true;

      public ScannedBlock(int relX, int relY, int relZ, String blockId) {
         this(relX, relY, relZ, blockId, true);
      }

      public ScannedBlock(int relX, int relY, int relZ, String blockId, boolean matched) {
         this.relX = relX;
         this.relY = relY;
         this.relZ = relZ;
         this.blockId = blockId;
         this.matched = matched;
      }
   }

   public static class StructurePattern {
      public String name;
      public int sizeX;
      public int sizeY;
      public int sizeZ;
      public String anchorBlockId = "cyan_terracotta";
      public int anchorRelX = 0;
      public int anchorRelY = 0;
      public int anchorRelZ = 0;
      public List<ScannedBlock> blocks = new ArrayList<>();
      public transient List<ScannedBlock> sampleBlocks = new ArrayList<>();

      public void buildSamples() {
         sampleBlocks.clear();
         if (blocks.isEmpty()) return;
         int step = Math.max(1, blocks.size() / 16);
         for (int i = 0; i < blocks.size() && sampleBlocks.size() < 16; i += step) {
            sampleBlocks.add(blocks.get(i));
         }
      }
   }

   private static final Set<String> NOISE_BLOCKS = new HashSet<>();
   static {
      NOISE_BLOCKS.add("air");
      NOISE_BLOCKS.add("cave_air");
      NOISE_BLOCKS.add("void_air");
      NOISE_BLOCKS.add("stone");
      NOISE_BLOCKS.add("cobblestone");
      NOISE_BLOCKS.add("dirt");
      NOISE_BLOCKS.add("coarse_dirt");
      NOISE_BLOCKS.add("deepslate");
      NOISE_BLOCKS.add("andesite");
      NOISE_BLOCKS.add("diorite");
      NOISE_BLOCKS.add("granite");
      NOISE_BLOCKS.add("bedrock");
      NOISE_BLOCKS.add("water");
      NOISE_BLOCKS.add("lava");
      NOISE_BLOCKS.add("gravel");
      NOISE_BLOCKS.add("sand");
      NOISE_BLOCKS.add("iron_ore");
      NOISE_BLOCKS.add("coal_ore");
      NOISE_BLOCKS.add("gold_ore");
      NOISE_BLOCKS.add("copper_ore");
      NOISE_BLOCKS.add("redstone_ore");
      NOISE_BLOCKS.add("lapis_ore");
      NOISE_BLOCKS.add("diamond_ore");
      NOISE_BLOCKS.add("emerald_ore");
      NOISE_BLOCKS.add("ice");
      NOISE_BLOCKS.add("packed_ice");
      NOISE_BLOCKS.add("blue_ice");
      NOISE_BLOCKS.add("snow");
      NOISE_BLOCKS.add("snow_block");
   }

   public static BlockPos pos1 = null;
   public static BlockPos pos2 = null;
   public static final Map<String, StructurePattern> loadedPatterns = new HashMap<>();
   public static StructurePattern lastCopiedPattern = null;
   public static BlockPos pastedStructureOrigin = null;
   public static StructurePattern pastedStructurePattern = null;
   public static long pastedStructureTime = 0L;

   public static void sendMessage(Component msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gui != null && mc.gui.getChat() != null) {
         mc.gui.getChat().addClientSystemMessage(msg);
      } else if (mc.player != null) {
         mc.player.sendSystemMessage(msg);
      }
   }

   public static BlockPos getTargetOrPlayerPos() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult bhr) {
         return bhr.getBlockPos();
      }
      if (mc.player != null) {
         return mc.player.blockPosition();
      }
      return BlockPos.ZERO;
   }

   public static void setPos1(BlockPos pos) {
      pos1 = pos;
      sendMessage(Component.literal("§8[§3Bombo§8] §aPos 1 set to: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
   }

   public static void setPos2(BlockPos pos) {
      pos2 = pos;
      sendMessage(Component.literal("§8[§3Bombo§8] §aPos 2 set to: §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
   }

   public static void scanArea(String structureName, int radius) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;

      BlockPos p1;
      BlockPos p2;
      if (pos1 != null && pos2 != null) {
         p1 = pos1;
         p2 = pos2;
      } else {
         BlockPos center = mc.player.blockPosition();
         p1 = center.offset(-radius, -radius / 2, -radius);
         p2 = center.offset(radius, radius / 2, radius);
      }

      int minX = Math.min(p1.getX(), p2.getX());
      int maxX = Math.max(p1.getX(), p2.getX());
      int minY = Math.min(p1.getY(), p2.getY());
      int maxY = Math.max(p1.getY(), p2.getY());
      int minZ = Math.min(p1.getZ(), p2.getZ());
      int maxZ = Math.max(p1.getZ(), p2.getZ());

      ClientLevel level = mc.level;
      BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
      List<ScannedBlock> collected = new ArrayList<>();

      int sigMinX = Integer.MAX_VALUE, sigMaxX = Integer.MIN_VALUE;
      int sigMinY = Integer.MAX_VALUE, sigYMax = Integer.MIN_VALUE;
      int sigMinZ = Integer.MAX_VALUE, sigMaxZ = Integer.MIN_VALUE;

      for (int x = minX; x <= maxX; ++x) {
         for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
               mut.set(x, y, z);
               BlockState st = level.getBlockState(mut);
               if (!st.isAir()) {
                  String blockKey = getBlockIdentifier(st);
                  if (!isNoiseBlock(blockKey)) {
                     collected.add(new ScannedBlock(x, y, z, blockKey, true));
                     if (x < sigMinX) sigMinX = x;
                     if (x > sigMaxX) sigMaxX = x;
                     if (y < sigMinY) sigMinY = y;
                     if (y > sigYMax) sigYMax = y;
                     if (z < sigMinZ) sigMinZ = z;
                     if (z > sigMaxZ) sigMaxZ = z;
                  }
               }
            }
         }
      }

      if (collected.isEmpty()) {
         sendMessage(Component.literal("§8[§3Bombo§8] §cNo signature structure blocks found in selection!"));
         return;
      }

      int originX = (sigMinX + sigMaxX) / 2;
      int originY = sigMinY;
      int originZ = (sigMinZ + sigMaxZ) / 2;

      StructurePattern pat = new StructurePattern();
      pat.name = structureName;
      pat.sizeX = (sigMaxX - sigMinX + 1);
      pat.sizeY = (sigYMax - sigMinY + 1);
      pat.sizeZ = (sigMaxZ - sigMinZ + 1);

      String bestAnchor = "cyan_terracotta";
      int bestDistSq = Integer.MAX_VALUE;
      int ancX = 0, ancY = 0, ancZ = 0;

      for (ScannedBlock b : collected) {
         int rx = b.relX - originX;
         int ry = b.relY - originY;
         int rz = b.relZ - originZ;
         pat.blocks.add(new ScannedBlock(rx, ry, rz, b.blockId, true));

         if (isSignatureAnchor(b.blockId)) {
            int d = rx * rx + ry * ry + rz * rz;
            if (d < bestDistSq) {
               bestDistSq = d;
               bestAnchor = b.blockId;
               ancX = rx;
               ancY = ry;
               ancZ = rz;
            }
         }
      }

      pat.anchorBlockId = bestAnchor;
      pat.anchorRelX = ancX;
      pat.anchorRelY = ancY;
      pat.anchorRelZ = ancZ;
      pat.buildSamples();

      loadedPatterns.put(structureName.toLowerCase(), pat);
      savePatternToFile(pat);

      sendMessage(Component.literal("§8[§3Bombo§8] §aScanned structure '§e" + structureName + "§a' (§b" + pat.blocks.size() + " signature blocks§a, anchor: §6" + bestAnchor + "§a)"));
      sendMessage(Component.literal("§8[§3Bombo§8] §7Size: §e" + pat.sizeX + "x" + pat.sizeY + "x" + pat.sizeZ + " §a(Noise blocks filtered out)"));
      sendMessage(Component.literal("§8[§3Bombo§8] §aSaved to §econfig/bomboaddons/structures/" + structureName.toLowerCase() + ".json"));
   }

   public static String getBlockIdentifier(BlockState st) {
      if (st == null || st.isAir()) return "air";
      if (st.is(Blocks.CYAN_TERRACOTTA)) return "cyan_terracotta";
      if (st.is(Blocks.STONE_BRICKS)) return "stone_bricks";
      if (st.is(Blocks.STONE_BRICK_STAIRS)) return "stone_brick_stairs";
      if (st.is(Blocks.STONE_BRICK_SLAB)) return "stone_brick_slab";
      if (st.is(Blocks.CRACKED_STONE_BRICKS)) return "cracked_stone_bricks";
      if (st.is(Blocks.MOSSY_STONE_BRICKS)) return "mossy_stone_bricks";
      if (st.is(Blocks.LIGHT_GRAY_TERRACOTTA)) return "light_gray_terracotta";
      if (st.is(Blocks.GRAY_TERRACOTTA)) return "gray_terracotta";
      if (st.is(Blocks.TERRACOTTA)) return "terracotta";
      if (st.is(Blocks.RED_TERRACOTTA)) return "red_terracotta";
      if (st.is(Blocks.PINK_TERRACOTTA)) return "pink_terracotta";
      if (st.is(Blocks.GOLD_BLOCK)) return "gold_block";
      if (st.is(Blocks.REDSTONE_BLOCK)) return "redstone_block";
      if (st.is(Blocks.SANDSTONE)) return "sandstone";
      if (st.is(Blocks.SMOOTH_SANDSTONE)) return "smooth_sandstone";
      if (st.is(Blocks.COBBLESTONE_WALL)) return "cobblestone_wall";
      if (st.is(Blocks.MOSSY_COBBLESTONE_WALL)) return "mossy_cobblestone_wall";
      if (st.is(Blocks.PRISMARINE)) return "prismarine";
      if (st.is(Blocks.PRISMARINE_BRICKS)) return "prismarine_bricks";
      if (st.is(Blocks.DARK_PRISMARINE)) return "dark_prismarine";
      if (st.is(Blocks.SEA_LANTERN)) return "sea_lantern";
      if (st.is(Blocks.SPRUCE_PLANKS)) return "spruce_planks";
      if (st.is(Blocks.SPRUCE_LOG)) return "spruce_log";
      if (st.is(Blocks.SPRUCE_SLAB)) return "spruce_slab";
      if (st.is(Blocks.SPRUCE_STAIRS)) return "spruce_stairs";
      if (st.is(Blocks.LIME_STAINED_GLASS_PANE)) return "lime_stained_glass_pane";
      if (st.is(Blocks.LIME_STAINED_GLASS)) return "lime_stained_glass";
      if (st.is(Blocks.GRAY_WOOL)) return "gray_wool";
      if (st.is(Blocks.LIGHT_BLUE_WOOL)) return "light_blue_wool";
      if (st.is(Blocks.RED_WOOL)) return "red_wool";
      if (st.is(Blocks.POLISHED_GRANITE)) return "polished_granite";
      if (st.is(Blocks.OAK_FENCE)) return "oak_fence";
      if (st.is(Blocks.OAK_WOOD)) return "oak_wood";
      if (st.is(Blocks.RED_STAINED_GLASS_PANE)) return "red_stained_glass_pane";
      if (st.is(Blocks.RED_STAINED_GLASS)) return "red_stained_glass";
      if (st.is(Blocks.EMERALD_BLOCK)) return "emerald_block";
      if (st.is(Blocks.TORCH)) return "torch";
      if (st.is(Blocks.FIRE)) return "fire";
      
      String desc = st.getBlock().getDescriptionId();
      return desc.replace("block.minecraft.", "");
   }

   public static boolean isNoiseBlock(String blockKey) {
      if (blockKey == null) return true;
      return NOISE_BLOCKS.contains(blockKey.toLowerCase());
   }

   public static boolean isSignatureAnchor(String blockKey) {
      if (blockKey == null) return false;
      String k = blockKey.toLowerCase();
      return k.contains("terracotta") ||
             k.contains("prismarine") ||
             k.contains("stone_brick") ||
             k.contains("spruce") ||
             k.contains("glass_pane") ||
             k.contains("gold_block") ||
             k.contains("redstone_block") ||
             k.contains("sandstone");
   }

   private static void savePatternToFile(StructurePattern pat) {
      try {
         File dir = new File(Minecraft.getInstance().gameDirectory, "config/bomboaddons/structures");
         if (!dir.exists()) dir.mkdirs();
         File file = new File(dir, pat.name.toLowerCase() + ".json");
         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(pat, writer);
         }
      } catch (Throwable ignored) {}
   }

   public static StructureFinder.FoundStructure getLookedAtOrClosestStructure(Minecraft mc) {
      if (mc.player == null || mc.level == null) return null;
      if (StructureFinder.foundStructures.isEmpty()) return StructureFinder.lastMatchedStructure;

      Vec3 eyePos = mc.player.getEyePosition();
      Vec3 lookVec = mc.player.getViewVector(1.0F).normalize();

      StructureFinder.FoundStructure bestAim = null;
      double minRayDist = Double.MAX_VALUE;
      double maxDot = -1.0;

      for (StructureFinder.FoundStructure fs : StructureFinder.foundStructures.values()) {
         if (fs == null) continue;
         if (fs.bounds != null) {
            AABB inflated = fs.bounds.inflate(4.0);
            var optHit = inflated.clip(eyePos, eyePos.add(lookVec.scale(350.0)));
            if (optHit.isPresent()) {
               double d = eyePos.distanceToSqr(optHit.get());
               if (d < minRayDist) {
                  minRayDist = d;
                  bestAim = fs;
               }
            }
         }
         if (bestAim == null && fs.centerPos != null) {
            Vec3 toCenter = Vec3.atCenterOf(fs.centerPos).subtract(eyePos).normalize();
            double dot = lookVec.dot(toCenter);
            if (dot > 0.65 && dot > maxDot) {
               maxDot = dot;
               bestAim = fs;
            }
         }
      }

      if (bestAim != null) return bestAim;

      StructureFinder.FoundStructure closest = null;
      double closestDist = Double.MAX_VALUE;
      for (StructureFinder.FoundStructure fs : StructureFinder.foundStructures.values()) {
         if (fs == null || fs.centerPos == null) continue;
         double d = mc.player.distanceToSqr(Vec3.atCenterOf(fs.centerPos));
         if (d < closestDist) {
            closestDist = d;
            closest = fs;
         }
      }

      return closest != null ? closest : StructureFinder.lastMatchedStructure;
   }

   public static void copyMatchedStructure() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;

      StructureFinder.FoundStructure fs = getLookedAtOrClosestStructure(mc);

      if (fs == null) {
         sendMessage(Component.literal("§8[§3Bombo§8] §cNo matched structure found in view or memory! Find a structure first or use /b scan pos1 & pos2."));
         return;
      }

      StructurePattern copy = new StructurePattern();
      copy.name = fs.name != null ? fs.name : "copied_match";
      copy.blocks = new ArrayList<>();

      int matchedBlocksCount = 0;

      // Copy actual world blocks within the structure's world bounding box so the user can inspect in Singleplayer why it matched!
      if (fs.bounds != null) {
         int minX = (int) Math.floor(fs.bounds.minX);
         int maxX = (int) Math.ceil(fs.bounds.maxX);
         int minY = (int) Math.max(mc.level.getMinY(), Math.floor(fs.bounds.minY));
         int maxY = (int) Math.min(mc.level.getMaxY(), Math.ceil(fs.bounds.maxY));
         int minZ = (int) Math.floor(fs.bounds.minZ);
         int maxZ = (int) Math.ceil(fs.bounds.maxZ);

         int sizeX = Math.min(100, Math.max(1, maxX - minX + 1));
         int sizeY = Math.min(60, Math.max(1, maxY - minY + 1));
         int sizeZ = Math.min(100, Math.max(1, maxZ - minZ + 1));

         BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
         for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
               for (int z = 0; z < sizeZ; z++) {
                  int wx = minX + x;
                  int wy = minY + y;
                  int wz = minZ + z;
                  BlockState st = mc.level.getBlockState(mpos.set(wx, wy, wz));
                  if (!st.isAir()) {
                     String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString().replace("minecraft:", "");
                     boolean isMatch = !isNoiseBlock(id) && isMatchingSignatureBlock(fs, st, id);
                     if (isMatch) matchedBlocksCount++;
                     copy.blocks.add(new ScannedBlock(x, y, z, id, isMatch));
                  }
               }
            }
         }
         copy.sizeX = sizeX;
         copy.sizeY = sizeY;
         copy.sizeZ = sizeZ;
      } else if (fs.actualWorldBlocks != null && !fs.actualWorldBlocks.isEmpty()) {
         for (ScannedBlock sb : fs.actualWorldBlocks) {
            copy.blocks.add(new ScannedBlock(sb.relX, sb.relY, sb.relZ, sb.blockId, sb.matched));
            if (sb.matched) matchedBlocksCount++;
         }
         copy.sizeX = fs.matchedPattern != null ? fs.matchedPattern.sizeX : 25;
         copy.sizeY = fs.matchedPattern != null ? fs.matchedPattern.sizeY : 10;
         copy.sizeZ = fs.matchedPattern != null ? fs.matchedPattern.sizeZ : 25;
      }

      if (!copy.blocks.isEmpty()) {
         copy.anchorBlockId = copy.blocks.get(0).blockId;
         copy.anchorRelX = copy.blocks.get(0).relX;
         copy.anchorRelY = copy.blocks.get(0).relY;
         copy.anchorRelZ = copy.blocks.get(0).relZ;
      }

      lastCopiedPattern = copy;
      loadedPatterns.put("copied_match", copy);
      loadedPatterns.put("copy", copy);
      if (fs.name != null) {
         loadedPatterns.put(fs.name.toLowerCase(), copy);
      }
      savePatternToFile(copy);

      String centerStr = fs.centerPos != null ? (fs.centerPos.getX() + ", " + fs.centerPos.getY() + ", " + fs.centerPos.getZ()) : "target";
      sendMessage(Component.literal("§8[§3Bombo§8] §aSuccessfully copied matched structure '§e" + fs.name + "§a' at §e" + centerStr + "§a!"));
      sendMessage(Component.literal("§8[§3Bombo§8] §7Captured §b" + copy.blocks.size() + " actual world blocks§7 (§a" + matchedBlocksCount + " matched signature blocks§7) in box (§e" + copy.sizeX + "x" + copy.sizeY + "x" + copy.sizeZ + "§7)."));
      sendMessage(Component.literal("§8[§3Bombo§8] §7Run §e/b scan paste§7 in Singleplayer to paste and view marked matched blocks."));
   }

   private static boolean isMatchingSignatureBlock(StructureFinder.FoundStructure fs, BlockState st, String id) {
      if (st == null || id == null) return false;
      String idLower = id.toLowerCase();
      if (fs != null && fs.name != null && fs.name.toLowerCase().contains("corleone")) {
         return idLower.contains("stone_brick") || idLower.contains("terracotta") || idLower.contains("smooth_stone");
      }
      return isSignatureAnchor(idLower);
   }

   public static void pasteStructure(String structureName, BlockPos origin) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;
      if (origin == null) origin = getTargetOrPlayerPos();

      StructurePattern pat = null;
      if (structureName == null || structureName.trim().isEmpty() || structureName.equalsIgnoreCase("copy") || structureName.equalsIgnoreCase("copied_match")) {
         pat = lastCopiedPattern;
         if (pat == null) pat = loadedPatterns.get("copied_match");
         if (pat == null) pat = loadedPatterns.get("copy");
         if (pat == null) {
            try {
               File dir = new File(mc.gameDirectory, "config/bomboaddons/structures");
               File f = new File(dir, "copied_match.json");
               if (!f.exists()) f = new File(dir, "copy.json");
               if (f.exists()) {
                  pat = new Gson().fromJson(Files.readString(f.toPath()), StructurePattern.class);
               }
            } catch (Throwable ignored) {}
         }
      }

      if (pat == null && structureName != null) {
         pat = loadedPatterns.get(structureName.toLowerCase());
         if (pat == null) {
            for (Map.Entry<String, StructurePattern> e : loadedPatterns.entrySet()) {
               if (e.getKey().equalsIgnoreCase(structureName) || e.getKey().toLowerCase().startsWith(structureName.toLowerCase())) {
                  pat = e.getValue();
                  break;
               }
            }
         }
         if (pat == null) {
            try {
               File dir = new File(mc.gameDirectory, "config/bomboaddons/structures");
               File f = new File(dir, structureName.toLowerCase() + ".json");
               if (f.exists()) {
                  pat = new Gson().fromJson(Files.readString(f.toPath()), StructurePattern.class);
               }
            } catch (Throwable ignored) {}
         }
      }

      if (pat == null) {
         sendMessage(Component.literal("§8[§3Bombo§8] §cStructure pattern '§e" + (structureName != null ? structureName : "copied_match") + "§c' not found! Use /b scan copy first."));
         return;
      }

      int placedCount = 0;
      int matchedCount = 0;
      boolean isSinglePlayer = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null;
      net.minecraft.server.level.ServerLevel serverLevel = null;
      if (isSinglePlayer) {
         serverLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
      }

      for (ScannedBlock sb : pat.blocks) {
         BlockPos targetPos = origin.offset(sb.relX, sb.relY, sb.relZ);
         String id = sb.blockId;
         if (!id.contains(":")) id = "minecraft:" + id;
         
         if (sb.matched) matchedCount++;

         if (serverLevel != null) {
            net.minecraft.resources.Identifier rl = net.minecraft.resources.Identifier.tryParse(id);
            if (rl != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)) {
               net.minecraft.world.level.block.Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(rl);
               serverLevel.setBlockAndUpdate(targetPos, b.defaultBlockState());
               placedCount++;
            }
         } else {
            mc.player.connection.sendCommand("setblock " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ() + " " + id);
            placedCount++;
         }
      }

      pastedStructureOrigin = origin;
      pastedStructurePattern = pat;
      pastedStructureTime = System.currentTimeMillis();

      sendMessage(Component.literal("§8[§3Bombo§8] §aPasted structure '§e" + pat.name + "§a' (§b" + placedCount + " blocks placed§a, §e" + matchedCount + " matched signature blocks§a) at §e" + origin.getX() + ", " + origin.getY() + ", " + origin.getZ()));
      sendMessage(Component.literal("§8[§3Bombo§8] §7Marked matched blocks in §a§lGREEN§7 and unmatched in §c§lRED§7 (§euse /b scan clear to dismiss§7)."));
   }

   public static void loadPatterns() {
      loadedPatterns.clear();

      // 1. Load built-in default patterns from jar resources
      String[] builtIns = new String[]{"corleone1", "goldendragon1"};
      for (String bName : builtIns) {
         try (java.io.InputStream in = StructureScanner.class.getResourceAsStream("/structures/" + bName + ".json")) {
            if (in != null) {
               try (java.io.InputStreamReader reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                  StructurePattern pat = new Gson().fromJson(reader, StructurePattern.class);
                  if (pat != null && pat.name != null) {
                     sanitizePattern(pat);
                     loadedPatterns.put(pat.name.toLowerCase(), pat);
                     loadedPatterns.put(bName.toLowerCase(), pat);
                  }
               }
            }
         } catch (Throwable ignored) {}
      }

      // 2. Load custom patterns from config folder
      try {
         File dir = new File(Minecraft.getInstance().gameDirectory, "config/bomboaddons/structures");
         if (dir.exists()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files != null) {
               Gson gson = new Gson();
               for (File f : files) {
                  String json = Files.readString(f.toPath());
                  StructurePattern pat = gson.fromJson(json, StructurePattern.class);
                  if (pat != null && pat.name != null) {
                     sanitizePattern(pat);
                     loadedPatterns.put(pat.name.toLowerCase(), pat);
                  }
               }
            }
         }
      } catch (Throwable ignored) {}

      // 3. Fallback: Always ensure Corleone 1 is present
      if (!loadedPatterns.containsKey("corleone1") && !loadedPatterns.containsKey("corleone 1")) {
         StructurePattern corleone = createDefaultCorleonePattern();
         corleone.buildSamples();
         loadedPatterns.put("corleone1", corleone);
      }
   }

   private static void sanitizePattern(StructurePattern pat) {
      if (pat.blocks != null) {
         pat.blocks.removeIf(b -> isNoiseBlock(b.blockId));
      }
      pat.buildSamples();
   }

   private static StructurePattern createDefaultCorleonePattern() {
      StructurePattern p = new StructurePattern();
      p.name = "Corleone 1";
      p.sizeX = 25;
      p.sizeY = 12;
      p.sizeZ = 9;
      p.anchorBlockId = "cyan_terracotta";
      p.anchorRelX = 0;
      p.anchorRelY = 0;
      p.anchorRelZ = 0;

      for (int x = -10; x <= 10; ++x) {
         p.blocks.add(new ScannedBlock(x, 0, -2, "stone_bricks", true));
         p.blocks.add(new ScannedBlock(x, 0, -1, "cyan_terracotta", true));
         p.blocks.add(new ScannedBlock(x, 0, 0, "cyan_terracotta", true));
         p.blocks.add(new ScannedBlock(x, 0, 1, "cyan_terracotta", true));
         p.blocks.add(new ScannedBlock(x, 0, 2, "stone_bricks", true));
      }
      return p;
   }

   public static void render(LevelRenderContext context) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) return;

      Vec3 camPos = mc.gameRenderer.getMainCamera().position();
      PoseStack poseStack = context.poseStack();
      OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());

      if (pos1 != null) {
         double x = (double)pos1.getX() - camPos.x;
         double y = (double)pos1.getY() - camPos.y;
         double z = (double)pos1.getZ() - camPos.z;
         AABB b1 = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> 
            BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, b1, 0.0F, 1.0F, 0.0F, 1.0F, 2.5F)
         );
         BomboRenderUtils.drawText(poseStack, collector, "§a[Pos 1]", (float)(x + 0.5), (float)(y + 1.2), (float)(z + 0.5), 65280, 0.035F, true, true);
      }

      if (pos2 != null) {
         double x = (double)pos2.getX() - camPos.x;
         double y = (double)pos2.getY() - camPos.y;
         double z = (double)pos2.getZ() - camPos.z;
         AABB b2 = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> 
            BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, b2, 1.0F, 0.0F, 0.0F, 1.0F, 2.5F)
         );
         BomboRenderUtils.drawText(poseStack, collector, "§c[Pos 2]", (float)(x + 0.5), (float)(y + 1.2), (float)(z + 0.5), 16711680, 0.035F, true, true);
      }

      if (pos1 != null && pos2 != null) {
         int minX = Math.min(pos1.getX(), pos2.getX());
         int maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
         int minY = Math.min(pos1.getY(), pos2.getY());
         int maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
         int minZ = Math.min(pos1.getZ(), pos2.getZ());
         int maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;

         AABB areaBox = new AABB(minX - camPos.x, minY - camPos.y, minZ - camPos.z, maxX - camPos.x, maxY - camPos.y, maxZ - camPos.z);
         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> 
            BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, areaBox, 1.0F, 1.0F, 0.0F, 0.8F, 2.0F)
         );
      }

      // Render pasted structure markers
      if (pastedStructurePattern != null && pastedStructureOrigin != null && (System.currentTimeMillis() - pastedStructureTime < 180000L)) {
         for (ScannedBlock sb : pastedStructurePattern.blocks) {
            BlockPos bpos = pastedStructureOrigin.offset(sb.relX, sb.relY, sb.relZ);
            double bx = (double)bpos.getX() - camPos.x;
            double by = (double)bpos.getY() - camPos.y;
            double bz = (double)bpos.getZ() - camPos.z;
            AABB blockBox = new AABB(bx, by, bz, bx + 1.0, by + 1.0, bz + 1.0);
            if (sb.matched) {
               collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) ->
                  BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, blockBox, 0.0F, 1.0F, 0.0F, 0.9F, 2.0F)
               );
            } else {
               collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) ->
                  BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, blockBox, 1.0F, 0.2F, 0.2F, 0.4F, 1.0F)
               );
            }
         }

         double ox = (double)pastedStructureOrigin.getX() - camPos.x;
         double oy = (double)pastedStructureOrigin.getY() - camPos.y;
         double oz = (double)pastedStructureOrigin.getZ() - camPos.z;
         AABB totalBox = new AABB(ox, oy, oz, ox + pastedStructurePattern.sizeX, oy + pastedStructurePattern.sizeY, oz + pastedStructurePattern.sizeZ);
         collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) ->
            BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, totalBox, 0.2F, 0.8F, 1.0F, 0.9F, 2.5F)
         );
         BomboRenderUtils.drawText(poseStack, collector, "§b[Pasted: " + pastedStructurePattern.name + "§b] §a(Green = Matched, Red = Unmatched)", (float)(ox + pastedStructurePattern.sizeX / 2.0), (float)(oy + pastedStructurePattern.sizeY + 0.8), (float)(oz + pastedStructurePattern.sizeZ / 2.0), 65280, 0.035F, true, true);
      }
   }
}
