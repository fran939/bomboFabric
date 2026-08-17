package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class LookCommand {
   public static int execute(String subcmd) {
      return execute(subcmd, false);
   }

   public static int execute(String subcmd, boolean verbose) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         int x = 0;
         int y = 0;
         int z = 0;
         HitResult hit = mc.hitResult;
         Entity target = findLookTarget(mc, false);
         if (target != null) {
            x = (int)Math.floor(target.getX());
            y = (int)Math.floor(target.getY());
            z = (int)Math.floor(target.getZ());
         } else if (hit != null && hit.getType() == Type.BLOCK && hit instanceof BlockHitResult) {
            BlockHitResult blockHit = (BlockHitResult)hit;
            BlockPos pos = blockHit.getBlockPos();
            x = pos.getX();
            y = pos.getY();
            z = pos.getZ();
         } else if (hit != null && hit.getType() == Type.ENTITY && hit instanceof EntityHitResult) {
            EntityHitResult entityHit = (EntityHitResult)hit;
            Entity entity = entityHit.getEntity();
            x = (int)Math.floor(entity.getX());
            y = (int)Math.floor(entity.getY());
            z = (int)Math.floor(entity.getZ());
         } else {
            x = (int)Math.floor(mc.player.getX());
            y = (int)Math.floor(mc.player.getY());
            z = (int)Math.floor(mc.player.getZ());
         }

         String coordsText = verbose ? "x: " + x + ", y: " + y + ", z: " + z : x + " " + y + " " + z;
         if (subcmd != null && !subcmd.trim().isEmpty()) {
            String sub = subcmd.trim();
            String fullCmd;
            if (sub.startsWith("/")) {
               fullCmd = sub + " " + coordsText;
            } else {
               fullCmd = "/" + sub + " " + coordsText;
            }

            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aExecuting: §e" + fullCmd));
            String cmdPayload = fullCmd.substring(1).trim();
            if (mc.player.connection != null) {
               mc.player.connection.sendCommand(cmdPayload);
            }
         } else {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aCoords: §b" + coordsText));
            mc.keyboardHandler.setClipboard(coordsText);
         }

         return 1;
      } else {
         return 0;
      }
   }

   public static Entity findLookTarget(Minecraft mc) {
      return findLookTarget(mc, true);
   }

   public static Entity findLookTarget(Minecraft mc, boolean requireHighlight) {
      List<Entity> targets = findLookTargets(mc, 1, requireHighlight);
      return targets.isEmpty() ? null : targets.get(0);
   }

   public static java.util.List<Entity> findLookTargets(Minecraft mc, int maxCount, boolean requireHighlight) {
      java.util.List<Entity> results = new java.util.ArrayList<>();
      if (mc.player != null && mc.level != null) {
         Vec3 eyePos = mc.player.getEyePosition(1.0F);
         Vec3 lookVec = mc.player.getViewVector(1.0F).normalize();
         class ScoredEntity implements Comparable<ScoredEntity> {
            final Entity entity;
            final double score;
            ScoredEntity(Entity entity, double score) {
               this.entity = entity;
               this.score = score;
            }
            @Override
            public int compareTo(ScoredEntity o) {
               return Double.compare(this.score, o.score);
            }
         }
         java.util.List<ScoredEntity> scored = new java.util.ArrayList<>();
         Set<Entity> candidates = new HashSet<>();
         for (Entity e : mc.level.entitiesForRendering()) {
            if (e != null && e != mc.player) {
               candidates.add(e);
            }
         }
         for (Entity entity : candidates) {
            if (!requireHighlight || HighlightESP.isEntityHighlighted(entity)) {
               Vec3 targetCenter = entity.getBoundingBox().getCenter();
               Vec3 eyeToTarget = targetCenter.subtract(eyePos);
               double dist = eyeToTarget.length();
               if (!(dist < 0.2D)) {
                  double dot = eyeToTarget.dot(lookVec);
                  if (!(dot <= 0.0D)) {
                     Vec3 closestPointOnRay = eyePos.add(lookVec.scale(dot));
                     double rayDist = targetCenter.distanceTo(closestPointOnRay);
                     double maxAllowedRayDist = Math.max(4.5D, (double)entity.getBbWidth() + 2.5D);
                     if (rayDist <= maxAllowedRayDist) {
                        double score = rayDist * 2.0D + dist * 0.5D;
                        scored.add(new ScoredEntity(entity, score));
                     }
                  }
               }
            }
         }
         java.util.Collections.sort(scored);
         int count = Math.min(Math.max(1, maxCount), scored.size());
         for (int i = 0; i < count; i++) {
            results.add(scored.get(i).entity);
         }
      }
      return results;
   }
}
