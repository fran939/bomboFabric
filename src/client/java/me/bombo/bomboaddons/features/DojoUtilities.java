package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.AutoFishing;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class DojoUtilities {

   public static boolean isInDojo() {
      String area = SkyblockUtils.getLocation();
      String sub = SkyblockUtils.getSubArea();
      String areaLower = area != null ? area.toLowerCase() : "";
      String subLower = sub != null ? sub.toLowerCase() : "";

      boolean isCrimson = areaLower.contains("crimson") || subLower.contains("crimson");
      boolean isDojoSub = subLower.contains("dojo");

      return isCrimson || isDojoSub;
   }

   public static void onPreAttack(Minecraft mc) {
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.dojoUtilities || s.hideCheats) return;

      if (!isInDojo()) return;

      Entity target = null;
      if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY && mc.hitResult instanceof EntityHitResult ehr) {
         target = ehr.getEntity();
      }

      if (target instanceof LivingEntity living) {
         handleZombieAttack(mc, living);
      }
   }

   private static void handleZombieAttack(Minecraft mc, LivingEntity entity) {
      ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
      if (head.isEmpty()) return;

      int requiredSwordSlot = -1;

      if (head.is(Items.LEATHER_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "wooden_sword");
      } else if (head.is(Items.IRON_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "iron_sword");
      } else if (head.is(Items.GOLDEN_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "golden_sword");
      } else if (head.is(Items.DIAMOND_HELMET)) {
         requiredSwordSlot = findSwordSlot(mc, "diamond_sword");
      }

      if (requiredSwordSlot != -1) {
         int currentSlot = AutoFishing.getSelectedSlot(mc);
         if (currentSlot != requiredSwordSlot) {
            AutoFishing.setSelectedSlot(mc, requiredSwordSlot);
         }
      }
   }

   private static int findSwordSlot(Minecraft mc, String targetItemPath) {
      if (mc.player == null) return -1;
      for (int i = 0; i < 9; i++) {
         ItemStack stack = mc.player.getInventory().getItem(i);
         if (!stack.isEmpty()) {
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase();
            if (path.equals(targetItemPath)) {
               return i;
            }
         }
      }
      return -1;
   }

   public static boolean isMasteryChallengeActive(Minecraft mc) {
      if (mc.level == null) return false;
      String area = SkyblockUtils.getLocation();
      String sub = SkyblockUtils.getSubArea();
      String areaLower = area != null ? area.toLowerCase() : "";
      String subLower = sub != null ? sub.toLowerCase() : "";
      if (!areaLower.contains("crimson") && !subLower.contains("dojo")) return false;

      net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
      net.minecraft.world.scores.Objective sidebar = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
      if (sidebar != null) {
         java.util.List<String> lines = SkyblockUtils.getSidebarLines(scoreboard, sidebar);
         for (String line : lines) {
            String clean = line.replaceAll("(?i)§[0-9a-z]", "").replaceAll("[^a-zA-Z0-9:\\s]", "").toLowerCase();
            if (clean.contains("challenge") && clean.contains("mastery")) {
               return true;
            }
         }
      }
      return false;
   }

   public static class WoolTrack {
      public net.minecraft.core.BlockPos pos;
      public int stage; // 1 = green, 2 = yellow, 3 = red
      public long greenSpawnTime;
      public long yellowSpawnTime;
      public long redSpawnTime;
      public long initialSpawnTime;
   }

   public static final java.util.List<WoolTrack> trackedWoolList = new java.util.concurrent.CopyOnWriteArrayList<>();
   private static final java.util.Map<net.minecraft.core.BlockPos, WoolTrack> trackedWools = new java.util.concurrent.ConcurrentHashMap<>();
   public static boolean shouldShowShootHud = false;

   public static void init() {
      net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
         net.minecraft.resources.Identifier.fromNamespaceAndPath("bomboaddons", "dojo_shoot_hud"),
         DojoUtilities::renderHud
      );
   }

   public static void onClientTick(Minecraft mc) {
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.dojoUtilities || !s.dojoMasteryWool) {
         if (!trackedWools.isEmpty()) {
            trackedWools.clear();
            trackedWoolList.clear();
         }
         shouldShowShootHud = false;
         return;
      }

      if (!isMasteryChallengeActive(mc)) {
         if (!trackedWools.isEmpty()) {
            trackedWools.clear();
            trackedWoolList.clear();
         }
         shouldShowShootHud = false;
         return;
      }

      long now = System.currentTimeMillis();

      boolean prevShoot = shouldShowShootHud;
      shouldShowShootHud = false;

      java.util.Set<net.minecraft.core.BlockPos> activeWools = new java.util.HashSet<>();

      // Scan the compact 16x16 Dojo platform area (y from -1 to +4) around player
      // Only 16x16x6 = 1,536 block checks which takes < 0.02ms!
      int radiusXZ = 16;
      net.minecraft.core.BlockPos playerPos = mc.player.blockPosition();

      for (int x = -radiusXZ; x <= radiusXZ; x++) {
         for (int z = -radiusXZ; z <= radiusXZ; z++) {
            for (int y = -1; y <= 4; y++) {
               net.minecraft.core.BlockPos pos = playerPos.offset(x, y, z);
               net.minecraft.world.level.block.state.BlockState state = mc.level.getBlockState(pos);
               int stage = 0;
               if (state.is(net.minecraft.world.level.block.Blocks.GREEN_WOOL)) {
                  stage = 1;
               } else if (state.is(net.minecraft.world.level.block.Blocks.YELLOW_WOOL)) {
                  stage = 2;
               } else if (state.is(net.minecraft.world.level.block.Blocks.RED_WOOL)) {
                  stage = 3;
               }

               if (stage > 0) {
                  activeWools.add(pos);
                  WoolTrack track = trackedWools.get(pos);
                  if (track == null) {
                     track = new WoolTrack();
                     track.pos = pos.immutable();
                     track.stage = stage;
                     track.initialSpawnTime = now;
                     trackedWools.put(track.pos, track);
                     trackedWoolList.add(track);
                  } else {
                     track.stage = stage;
                  }
               }
            }
         }
      }

      // Check timers on active yellow wools
      for (WoolTrack target : trackedWoolList) {
         if (target.stage == 2) { // Yellow wool
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
               if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand as) {
                  double dx = Math.abs(as.getX() - (target.pos.getX() + 0.5));
                  double dz = Math.abs(as.getZ() - (target.pos.getZ() + 0.5));
                  double dy = Math.abs(as.getY() - target.pos.getY());
                  if (dx < 1.5 && dz < 1.5 && dy <= 4.0) {
                     String name = as.getCustomName() != null ? as.getCustomName().getString() : "";
                     if (!name.isEmpty()) {
                        String clean = net.minecraft.ChatFormatting.stripFormatting(name).trim();
                        if (clean.contains(":")) {
                           String[] parts = clean.split(":");
                           if (parts.length == 2) {
                              try {
                                 int sec = Integer.parseInt(parts[0].trim());
                                 int ms = Integer.parseInt(parts[1].trim());
                                 if (sec == 0 && ms <= 150) {
                                    shouldShowShootHud = true;
                                    break;
                                 }
                              } catch (Throwable ignored) {}
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      // Cleanup despawned wools
      java.util.Iterator<java.util.Map.Entry<net.minecraft.core.BlockPos, WoolTrack>> iter = trackedWools.entrySet().iterator();
      while (iter.hasNext()) {
         java.util.Map.Entry<net.minecraft.core.BlockPos, WoolTrack> entry = iter.next();
         if (!activeWools.contains(entry.getKey())) {
            trackedWoolList.remove(entry.getValue());
            iter.remove();
         }
      }

      if (shouldShowShootHud && !prevShoot && s.dojoShootSound) {
         try {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F));
         } catch (Exception ignored) {}
      }
   }

   public static void renderTracers(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.dojoUtilities || !s.dojoMasteryWool) return;
      if (!isMasteryChallengeActive(mc) || trackedWoolList.isEmpty()) return;

      net.minecraft.world.phys.Vec3 camPos = mc.gameRenderer.getMainCamera().position();
      com.mojang.blaze3d.vertex.PoseStack poseStack = context.poseStack();
      me.bombo.bomboaddons.OrderedSubmitNodeCollector collector = new me.bombo.bomboaddons.OrderedSubmitNodeCollector(context.bufferSource());
      org.joml.Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
      float startX = look.x();
      float startY = look.y();
      float startZ = look.z();

      // Tracer from crosshair to first wool
      WoolTrack first = trackedWoolList.get(0);
      float endX = (float)((double)first.pos.getX() + 0.5 - camPos.x);
      float endY = (float)((double)first.pos.getY() + 0.5 - camPos.y);
      float endZ = (float)((double)first.pos.getZ() + 0.5 - camPos.z);
      
      float r = first.stage == 3 ? 1.0F : (first.stage == 2 ? 1.0F : 0.0F);
      float g = first.stage == 3 ? 0.0F : (first.stage == 2 ? 1.0F : 1.0F);
      float b = 0.0F;

      collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent(),
         (pose, vertexConsumer) -> me.bombo.bomboaddons.BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, r, g, b, 1.0F, 3.0F));

      // Tracers connecting chain of subsequent wools in spawn order (if enabled)
      if (s.dojoNextWoolTracer) {
         for (int i = 0; i < trackedWoolList.size() - 1; i++) {
            WoolTrack current = trackedWoolList.get(i);
            WoolTrack next = trackedWoolList.get(i + 1);

            float cX = (float)((double)current.pos.getX() + 0.5 - camPos.x);
            float cY = (float)((double)current.pos.getY() + 0.5 - camPos.y);
            float cZ = (float)((double)current.pos.getZ() + 0.5 - camPos.z);

            float nX = (float)((double)next.pos.getX() + 0.5 - camPos.x);
            float nY = (float)((double)next.pos.getY() + 0.5 - camPos.y);
            float nZ = (float)((double)next.pos.getZ() + 0.5 - camPos.z);

            collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.linesTranslucent(),
               (pose, vertexConsumer) -> me.bombo.bomboaddons.BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, cX, cY, cZ, nX, nY, nZ, 1.0F, 1.0F, 0.0F, 0.8F, 2.0F));
         }
      }
   }

   public static void drawShootHud(net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y, float scale, boolean preview) {
      Minecraft mc = Minecraft.getInstance();
      if (!preview && !shouldShowShootHud) return;

      g.pose().pushMatrix();
      if (scale != 1.0F && scale > 0.0F) {
         g.pose().scale(scale, scale);
         int drawX = (int)((float)x / scale);
         int drawY = (int)((float)y / scale);
         g.text(mc.font, "§c§l▶ SHOOT ◀", drawX + 3, drawY + 2, 0xFFFF3333, true);
      } else {
         g.text(mc.font, "§c§l▶ SHOOT ◀", x + 3, y + 2, 0xFFFF3333, true);
      }
      g.pose().popMatrix();
   }

   public static void renderHud(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker deltaTracker) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.dojoUtilities || !s.dojoMasteryWool || !s.dojoShootHud) return;
      if (!isMasteryChallengeActive(mc)) return;

      int screenW = mc.getWindow().getGuiScaledWidth();
      int screenH = mc.getWindow().getGuiScaledHeight();
      int x = s.dojoShootHudX >= 0 ? s.dojoShootHudX : (screenW / 2 - 27);
      int y = s.dojoShootHudY >= 0 ? s.dojoShootHudY : (screenH / 2 + 18);
      float scale = s.dojoShootHudScale > 0.0F ? s.dojoShootHudScale : 1.0F;

      drawShootHud(g, x, y, scale, false);
   }
}
