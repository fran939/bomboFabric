package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GoldenDragonNestFinder {
   public static volatile BlockPos nestPos = null;
   private static volatile BlockPos lastAlertedPos = null;
   private static int lastScannedChunkX = Integer.MIN_VALUE;
   private static int lastScannedChunkZ = Integer.MIN_VALUE;

   public static void clear() {
      nestPos = null;
      lastAlertedPos = null;
      lastScannedChunkX = Integer.MIN_VALUE;
      lastScannedChunkZ = Integer.MIN_VALUE;
   }

   private static volatile boolean isScanning = false;

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         clear();
         return;
      }
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.goldenDragonNestFinder) {
         nestPos = null;
         return;
      }

      if (mc.player.tickCount % 20 == 0) {
         if (nestPos != null && mc.player.distanceToSqr(nestPos.getX(), nestPos.getY(), nestPos.getZ()) < 256 * 256) {
            return;
         }
         scanForNest(mc.level, mc.player.position());
      }
   }

   private static void scanForNest(ClientLevel level, Vec3 playerPos) {
      if (level == null || playerPos == null) return;

      try {
         // 1. Check loaded entities (fast)
         for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ArmorStand) {
               String name = ChatFormatting.stripFormatting(entity.getDisplayName().getString());
               if (name != null && (name.contains("Dragon's Lair") || name.contains("Golden Dragon") || name.contains("Dragon Egg"))) {
                  setFoundNest(entity.blockPosition());
                  return;
               }
               String headTex = TargetPests.getHeadTextureValue(entity);
               if (headTex != null) {
                  String hash = TargetPests.extractTextureHash(headTex);
                  if (hash != null && (hash.contains("c0062cc98ebda72a6a4b89783adcef2815b483a01d73ea87b3df76072a89d13b") || "sos".equalsIgnoreCase(HighlightESP.ALIAS_MAP.get(hash)))) {
                     setFoundNest(entity.blockPosition());
                     return;
                  }
               }
            }
         }

         // 2. Scan blocks asynchronously to prevent any main thread FPS stutter
         if (!isScanning) {
            isScanning = true;
            java.util.concurrent.ForkJoinPool.commonPool().execute(() -> {
               try {
                  int px = (int)playerPos.x;
                  int py = (int)playerPos.y;
                  int pz = (int)playerPos.z;
                  int radius = 16;
                  int minY = Math.max(level.getMinY(), py - 16);
                  int maxY = Math.min(level.getMaxY() - 1, py + 16);
                  BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

                  for (int x = px - radius; x <= px + radius; ++x) {
                     for (int z = pz - radius; z <= pz + radius; ++z) {
                        for (int y = minY; y <= maxY; ++y) {
                           mutablePos.set(x, y, z);
                           try {
                              BlockState state = level.getBlockState(mutablePos);
                              if (state.is(Blocks.DRAGON_EGG)) {
                                 setFoundNest(new BlockPos(x, y, z));
                                 return;
                              }
                           } catch (Throwable ignored) {
                           }
                        }
                     }
                  }
               } finally {
                  isScanning = false;
               }
            });
         }
      } catch (Throwable ignored) {
      }
   }

   private static void setFoundNest(BlockPos pos) {
      if (pos == null) return;
      nestPos = pos;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      if (!pos.equals(lastAlertedPos)) {
         lastAlertedPos = pos;
         MutableComponent msg = Component.literal("§8[§3Bombo§8]§r §6§lGolden Dragon Nest §afound at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "§a!");
         ClickEvent nav = LF.createClickEventRobust("RUN_COMMAND", "/shnav " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
         if (nav != null) {
            msg.setStyle(msg.getStyle().withClickEvent(nav));
         }
         mc.player.sendSystemMessage(msg);
      }
   }

   public static void render(LevelRenderContext context) {
      try {
         BomboConfig.Settings s = BomboConfig.get();
         if (!s.goldenDragonNestFinder || nestPos == null) return;
         Minecraft mc = Minecraft.getInstance();
         if (mc.level == null) return;

         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());

         BlockPos pos = nestPos;
         double x = (double)pos.getX() - camPos.x;
         double y = (double)pos.getY() - camPos.y;
         double z = (double)pos.getZ() - camPos.z;
         double dist = Math.sqrt(x * x + y * y + z * z);
         if (dist > 512.0) return;

         float r = 1.0F;
         float g = 0.84F;
         float b = 0.0F;
         float a = 1.0F;

         RenderType renderType = s.hideCheats ? RenderTypes.lines() : RenderTypes.linesTranslucent();
         AABB box = new AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
         collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, 2.5F));

         // Draw beacon beam
         float beaconWidth = 0.15F;
         AABB beaconBox = new AABB(x + 0.5 - (double)beaconWidth, y, z + 0.5 - (double)beaconWidth, x + 0.5 + (double)beaconWidth, y + 256.0, z + 0.5 + (double)beaconWidth);
         collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, beaconBox, r, g, b, 0.4F, 2.0F));

         // Draw text label
         String label = "§6§lGolden Dragon Nest §7(" + (int)dist + "m)";
         BomboRenderUtils.drawText(poseStack, collector, label, (float)(x + 0.5), (float)(y + 1.5), (float)(z + 0.5), 16755200, 0.035F, true, true);
      } catch (Throwable ignored) {
      }
   }
}
