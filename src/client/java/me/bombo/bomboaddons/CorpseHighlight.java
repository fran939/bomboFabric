package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3fc;

public class CorpseHighlight {
   private static final HashSet<Integer> openedCorpses = new HashSet();
   public static final List<CorpseTracer> TRACERS = new ArrayList();

   public static void init() {
      ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join)(handler, sender, client) -> clearCache());
      ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, client) -> clearCache());
      UseEntityCallback.EVENT.register((UseEntityCallback)(player, level, hand, entity, hitResult) -> {
         if (level.isClientSide() && entity instanceof ArmorStand stand) {
            BomboConfig.Settings s = BomboConfig.get();
            if (s.corpseEsp && s.hideOpenedCorpses) {
               CorpseType type = getCorpseType(stand);
               if (type != CorpseHighlight.CorpseType.None && hasKeyForCorpse(type)) {
                  openedCorpses.add(stand.getId());
               }
            }
         }

         return InteractionResult.PASS;
      });
   }

   public static void clearCache() {
      openedCorpses.clear();
   }

   public static CorpseType getCorpseType(ArmorStand stand) {
      ItemStack helmet = stand.getItemBySlot(EquipmentSlot.HEAD);
      if (helmet != null && !helmet.isEmpty()) {
         String name = helmet.getHoverName().getString().replaceAll("(?i)§.", "").trim().toLowerCase();
         if (name.contains("lapis armor helmet") || name.contains("lapis helmet")) {
            return CorpseHighlight.CorpseType.Lapis;
         }

         if (name.contains("mineral helmet")) {
            return CorpseHighlight.CorpseType.Tungsten;
         }

         if (name.contains("yog helmet")) {
            return CorpseHighlight.CorpseType.Umber;
         }

         if (name.contains("vanguard helmet")) {
            return CorpseHighlight.CorpseType.Vanguard;
         }
      }

      return CorpseHighlight.CorpseType.None;
   }

   public static boolean hasKeyForCorpse(CorpseType type) {
      String var10000;
      switch (type.ordinal()) {
         case 1 -> var10000 = "TUNGSTEN_KEY";
         case 2 -> var10000 = "UMBER_KEY";
         case 3 -> var10000 = "SKELETON_KEY";
         default -> var10000 = "";
      }

      String id = var10000;
      if (!id.isEmpty()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return false;
         } else {
            for(int i = 0; i <= 35; ++i) {
               ItemStack stack = mc.player.getInventory().getItem(i);
               if (!stack.isEmpty() && SkyblockUtils.getInternalId(stack).equals(id)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return true;
      }
   }

   private static long lastMineshaftCheck = 0L;
   private static boolean cachedInMineshaft = false;

   public static boolean isInMineshaft() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return false;
      }
      long now = System.currentTimeMillis();
      if (now - lastMineshaftCheck < 1000L) {
         return cachedInMineshaft;
      }
      lastMineshaftCheck = now;

      if (SkyblockUtils.isConnectedToHypixel() && "SKYBLOCK".equals(BomboaddonsClient.locrawGametype)) {
         String mode = BomboaddonsClient.locrawMode != null ? BomboaddonsClient.locrawMode.toLowerCase() : "";
         String map = BomboaddonsClient.locrawMap != null ? BomboaddonsClient.locrawMap.toLowerCase() : "";
         if (mode.contains("mineshaft") || map.contains("mineshaft")) {
            cachedInMineshaft = true;
            return true;
         }
      }
      if (BomboaddonsClient.currentSubArea != null && BomboaddonsClient.currentSubArea.toLowerCase().contains("mineshaft")) {
         cachedInMineshaft = true;
         return true;
      }
      if (BomboaddonsClient.currentArea != null && BomboaddonsClient.currentArea.toLowerCase().contains("mineshaft")) {
         cachedInMineshaft = true;
         return true;
      }
      Scoreboard scoreboard = mc.level.getScoreboard();
      Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
      if (sidebar != null) {
         for(String line : SkyblockUtils.getSidebarLines(scoreboard, sidebar)) {
            String clean = line.replaceAll("(?i)§.", "").trim().toLowerCase();
            if (clean.contains("mineshaft")) {
               cachedInMineshaft = true;
               return true;
            }
         }
      }
      cachedInMineshaft = false;
      return false;
   }

   public static BlockPos findGround(BlockPos pos, int maxDistance) {
      int dist = Math.max(0, Math.min(maxDistance, 256));
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return pos;
      } else {
         for(int i = 0; i <= dist; ++i) {
            BlockPos below = pos.below(i);
            if (!mc.level.getBlockState(below).isAir()) {
               return below;
            }
         }

         return pos;
      }
   }

   public static void render(LevelRenderContext context) {
      TRACERS.clear();
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.corpseEsp || !isInMineshaft()) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         Vec3 camPos = mc.gameRenderer.getMainCamera().position();
         PoseStack poseStack = context.poseStack();
         OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());

         for(Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand) {
               ArmorStand stand = (ArmorStand)entity;
               if (!s.hideOpenedCorpses || !openedCorpses.contains(stand.getId())) {
                  CorpseType type = getCorpseType(stand);
                  if (type != CorpseHighlight.CorpseType.None && (!s.hideCheats || mc.player == null || mc.player.hasLineOfSight(stand))) {
                     String var10000;
                     switch (type.ordinal()) {
                        case 0 -> var10000 = s.lapisOutlineColor;
                        case 1 -> var10000 = s.tungstenOutlineColor;
                        case 2 -> var10000 = s.umberOutlineColor;
                        case 3 -> var10000 = s.vanguardOutlineColor;
                        default -> var10000 = "WHITE";
                     }

                     String outlineColorName = var10000;
                     if (outlineColorName == null || outlineColorName.equalsIgnoreCase("DISABLED") || outlineColorName.equalsIgnoreCase("NONE")) {
                        continue;
                     }
                     switch (type.ordinal()) {
                        case 0 -> var10000 = s.lapisFillColor;
                        case 1 -> var10000 = s.tungstenFillColor;
                        case 2 -> var10000 = s.umberFillColor;
                        case 3 -> var10000 = s.vanguardFillColor;
                        default -> var10000 = "WHITE";
                     }

                     String fillColorName = var10000;
                     int outlineColorHex = BomboRenderUtils.colorNameToHex(outlineColorName);
                     int fillColorHex = BomboRenderUtils.colorNameToHex(fillColorName);
                     float rOut = (float)(outlineColorHex >> 16 & 255) / 255.0F;
                     float gOut = (float)(outlineColorHex >> 8 & 255) / 255.0F;
                     float bOut = (float)(outlineColorHex & 255) / 255.0F;
                     float aOut = 1.0F;
                     float rFill = (float)(fillColorHex >> 16 & 255) / 255.0F;
                     float gFill = (float)(fillColorHex >> 8 & 255) / 255.0F;
                     float bFill = (float)(fillColorHex & 255) / 255.0F;
                     float aFill = 0.4F;
                     double x = stand.getX() - camPos.x;
                     double y = stand.getY() - camPos.y;
                     double z = stand.getZ() - camPos.z;
                     float dist = (float)Math.sqrt(x * x + y * y + z * z);
                     float scale = 1.0F;
                     if (!s.hideCheats && dist > 0.2F) {
                        scale = 0.2F / dist;
                     }

                     AABB tempBox = stand.getBoundingBox().inflate((double)0.25F, (double)0.0F, (double)0.25F).move(-camPos.x, -camPos.y, -camPos.z);
                     if (scale != 1.0F) {
                        tempBox = new AABB(tempBox.minX * (double)scale, tempBox.minY * (double)scale, tempBox.minZ * (double)scale, tempBox.maxX * (double)scale, tempBox.maxY * (double)scale, tempBox.maxZ * (double)scale);
                     }
                     final AABB finalBox = tempBox;

                     if ("Outline".equals(s.corpseEspStyle) || "Both".equals(s.corpseEspStyle) || s.hideCheats) {
                        RenderType renderType = s.hideCheats ? RenderTypes.lines() : RenderTypes.linesTranslucent();
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, finalBox, rOut, gOut, bOut, aOut, 2.0F));
                     }

                     if (!s.hideCheats && ("Filled".equals(s.corpseEspStyle) || "Both".equals(s.corpseEspStyle))) {
                        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vertexConsumer) -> drawFilledBox(pose.pose(), vertexConsumer, finalBox, rFill, gFill, bFill, aFill));
                     }

                     boolean shouldTrace = false;
                     if (type == CorpseHighlight.CorpseType.Lapis) {
                        shouldTrace = s.tracerLapis;
                     } else if (type == CorpseHighlight.CorpseType.Tungsten) {
                        shouldTrace = s.tracerTungsten;
                     } else if (type == CorpseHighlight.CorpseType.Umber) {
                        shouldTrace = s.tracerUmber;
                     } else if (type == CorpseHighlight.CorpseType.Vanguard) {
                        shouldTrace = s.tracerVanguard;
                     }

                     if (!s.hideCheats && s.corpseEspStyleTracer && shouldTrace) {
                        float endX = (float)(stand.getX() - camPos.x);
                        float endY = (float)(stand.getY() - camPos.y + (double)1.0F);
                        float endZ = (float)(stand.getZ() - camPos.z);
                        Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
                        float startX = look.x();
                        float startY = look.y();
                        float startZ = look.z();
                        collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, rOut, gOut, bOut, 1.0F, 2.0F));
                     }
                  }
               }
            }
         }
      }
   }

   public static void drawFilledBox(Matrix4f matrix, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a) {
      drawFilledBox(matrix, buffer, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ, r, g, b, a);
   }

   public static void drawFilledBox(Matrix4f matrix, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
      buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
      buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
   }

   public static class CorpseTracer {
      public Vector2f start;
      public Vector2f end;
      public int color;
      public float thickness;
   }

   public static enum CorpseType {
      Lapis,
      Tungsten,
      Umber,
      Vanguard,
      None;

      // $FF: synthetic method
      private static CorpseType[] $values() {
         return new CorpseType[]{Lapis, Tungsten, Umber, Vanguard, None};
      }
   }
}
