package me.bombo.bomboaddons.cheat.hider;

import me.bombo.bomboaddons.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.PerformanceProfiler;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.cheat.esp.TargetPests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class EntityBlockHider {

   private static volatile boolean cacheValid = false;
   private static final Map<Block, BlockReplaceTarget> FAST_BLOCK_CACHE = new ConcurrentHashMap<>();
   private static String lastCachedLocation = "";
   private static String lastCachedSubarea = "";

   public static class BlockReplaceTarget {
      public final BlockState defaultTargetState;
      public final boolean preserveProperties;

      public BlockReplaceTarget(BlockState state, boolean preserveProps) {
         this.defaultTargetState = state;
         this.preserveProperties = preserveProps;
      }
   }

   private static final it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap ENTITY_HIDE_CACHE = new it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap();
   private static long lastEntityCacheClear = 0L;

   public static void invalidateCache() {
      cacheValid = false;
      FAST_BLOCK_CACHE.clear();
      ENTITY_HIDE_CACHE.clear();
   }

   public static void rebuildCache() {
      FAST_BLOCK_CACHE.clear();
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.hiderEnabled || s.blockReplacements == null || s.blockReplacements.isEmpty()) {
         cacheValid = true;
         return;
      }

      String loc = SkyblockUtils.getLocation();
      String sub = SkyblockUtils.getSubArea();
      lastCachedLocation = loc != null ? loc : "";
      lastCachedSubarea = sub != null ? sub : "";

      for (BomboConfig.BlockReplaceRule rule : s.blockReplacements) {
         if (rule == null || !rule.enabled) continue;

         if (rule.island != null && !rule.island.trim().isEmpty()) {
            if (!SkyblockUtils.matchesIslandRequirement(rule.island.trim())) {
               continue;
            }
         }
         if (rule.subarea != null && !rule.subarea.trim().isEmpty()) {
            if (sub == null || !sub.toLowerCase(Locale.ROOT).contains(rule.subarea.toLowerCase(Locale.ROOT).trim())) {
               continue;
            }
         }

         String from = rule.fromBlock != null ? rule.fromBlock.trim().toLowerCase(Locale.ROOT) : "";
         if (from.isEmpty()) continue;
         if (from.startsWith("minecraft:")) from = from.substring(10);

         Block sourceBlock = null;
         for (Block b : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            String path = id.getPath().toLowerCase(Locale.ROOT);
            String full = id.toString().toLowerCase(Locale.ROOT);
            if (path.equals(from) || full.equals(from) || path.replace('_', ' ').equalsIgnoreCase(from)) {
               sourceBlock = b;
               break;
            }
         }

         if (sourceBlock == null) continue;

         String to = rule.toBlock != null ? rule.toBlock.trim().toLowerCase(Locale.ROOT) : "";
         if (to.isEmpty() || to.equals("air") || to.equals("none") || to.equals("hide")) {
            FAST_BLOCK_CACHE.put(sourceBlock, new BlockReplaceTarget(Blocks.AIR.defaultBlockState(), false));
            continue;
         }

         if (!to.contains(":")) to = "minecraft:" + to;
         try {
            Block targetBlock = BuiltInRegistries.BLOCK.getValue(Identifier.parse(to));
            if (targetBlock != null) {
               FAST_BLOCK_CACHE.put(sourceBlock, new BlockReplaceTarget(targetBlock.defaultBlockState(), rule.preserveProperties));
            }
         } catch (Throwable ignored) {}
      }

      cacheValid = true;
   }

   public static boolean shouldHideEntity(Entity entity) {
      if (entity == null) return false;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.hiderEnabled || s.hiddenEntities == null || s.hiddenEntities.isEmpty()) {
         return false;
      }

      int id = entity.getId();
      long now = System.currentTimeMillis();
      if (now - lastEntityCacheClear > 1000L) {
         ENTITY_HIDE_CACHE.clear();
         lastEntityCacheClear = now;
      }

      if (ENTITY_HIDE_CACHE.containsKey(id)) {
         return ENTITY_HIDE_CACHE.get(id);
      }

      long start = System.nanoTime();
      boolean hide = false;
      try {
         for (BomboConfig.EntityHideRule rule : s.hiddenEntities) {
            if (rule == null || !rule.enabled) continue;

            if (rule.island != null && !rule.island.trim().isEmpty()) {
               if (!SkyblockUtils.matchesIslandRequirement(rule.island.trim())) {
                  continue;
               }
            }
            if (rule.subarea != null && !rule.subarea.trim().isEmpty()) {
               String currentSub = SkyblockUtils.getSubArea();
               if (currentSub == null || !currentSub.toLowerCase(Locale.ROOT).contains(rule.subarea.toLowerCase(Locale.ROOT).trim())) {
                  continue;
               }
            }

            String matcher = rule.matcher != null ? rule.matcher.trim().toLowerCase(Locale.ROOT) : "";
            if (matcher.isEmpty()) continue;

            // 1. Texture hash check (extract hash from Base64 texture value like everywhere else)
            String headTex = TargetPests.getHeadTextureValue(entity);
            if (headTex != null && !headTex.isEmpty()) {
               // Compare against raw value
               if (headTex.toLowerCase(Locale.ROOT).contains(matcher)) {
                  hide = true;
                  break;
               }
               // Extract the actual skull hash from the Base64-encoded texture URL
               String headHash = TargetPests.extractTextureHash(headTex);
               if (headHash != null && headHash.toLowerCase(Locale.ROOT).contains(matcher)) {
                  hide = true;
                  break;
               }
            }

            // 2. Custom name check
            if (entity.hasCustomName()) {
               String cName = entity.getCustomName().getString().toLowerCase(Locale.ROOT);
               if (cName.contains(matcher)) {
                  hide = true;
                  break;
               }
            }

            // 3. Entity type / ID check
            String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
            String typePath = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().toLowerCase(Locale.ROOT);
            if (typeId.contains(matcher) || typePath.contains(matcher)) {
               hide = true;
               break;
            }

            // 4. Player name check
            if (entity instanceof Player player) {
               String pName = player.getGameProfile().name() != null ? player.getGameProfile().name().toLowerCase(Locale.ROOT) : player.getName().getString().toLowerCase(Locale.ROOT);
               if (pName.contains(matcher)) {
                  hide = true;
                  break;
               }
            }
         }
      } finally {
         ENTITY_HIDE_CACHE.put(id, hide);
         if (PerformanceProfiler.isEnabled()) {
            PerformanceProfiler.record("Hider: Entity Check", System.nanoTime() - start);
         }
      }

      return hide;
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   public static BlockState getReplacedBlockState(BlockPos pos, BlockState state) {
      if (state == null) return state;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.hiderEnabled || s.blockReplacements == null || s.blockReplacements.isEmpty()) {
         return state;
      }

      // Check if location or subarea changed
      String loc = SkyblockUtils.getLocation();
      String sub = SkyblockUtils.getSubArea();
      if (!cacheValid || !Objects.equals(loc, lastCachedLocation) || !Objects.equals(sub, lastCachedSubarea)) {
         rebuildCache();
      }

      BlockReplaceTarget target = FAST_BLOCK_CACHE.get(state.getBlock());
      if (target == null) {
         return state;
      }

      if (!target.preserveProperties) {
         return target.defaultTargetState;
      }

      BlockState targetState = target.defaultTargetState;
      for (Property prop : state.getProperties()) {
         if (targetState.hasProperty(prop)) {
            try {
               targetState = targetState.setValue(prop, state.getValue(prop));
            } catch (Throwable ignored) {}
         }
      }
      return targetState;
   }
}
