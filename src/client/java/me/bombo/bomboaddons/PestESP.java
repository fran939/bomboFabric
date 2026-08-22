package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3fc;

public class PestESP {
   public static final List<PestTracer> TRACERS = new ArrayList();
   private static int debugTicks = 0;

   public static void render(LevelRenderContext context) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.pestEsp) {
         boolean inGarden = SkyblockUtils.isInGarden();
         if (debugTicks++ % 200 == 0 && s.debugEntities) {
            System.out.println("DEBUG: PestESP render call. InGarden=" + inGarden + " Location=" + SkyblockUtils.getLocation());
         }

         if (!inGarden) {
            TRACERS.clear();
         } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
               Vec3 camPos = mc.gameRenderer.getMainCamera().position();
               PoseStack poseStack = context.poseStack();
               OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
               int colorInt = BomboRenderUtils.colorNameToHex(s.pestEspColor);
               float r = (float)(colorInt >> 16 & 255) / 255.0F;
               float g = (float)(colorInt >> 8 & 255) / 255.0F;
               float b = (float)(colorInt & 255) / 255.0F;
               float a = 1.0F;
               float lineWidth = s.pestEspThickness;
               TRACERS.clear();
               int count = 0;
               int armorStands = 0;

               for(Entity entity : mc.level.entitiesForRendering()) {
                  if (entity instanceof ArmorStand) {
                     ArmorStand stand = (ArmorStand)entity;
                     ++armorStands;
                     String pestName = TargetPests.getPestName(stand);
                     if (pestName != null) {
                        ++count;
                        double x = entity.getX() - camPos.x;
                        double y = entity.getY() - camPos.y;
                        double z = entity.getZ() - camPos.z;
                        float dist = (float)Math.sqrt(x * x + y * y + z * z);
                        float scale = 1.0F;

                        float boxWidth = 1.0F;
                        float boxHeight = 1.0F;
                        float boxYOffset = 1.3F;
                        if (pestName.contains("worm")) {
                           boxYOffset = 0.5F * scale;
                           boxHeight = 0.8F * scale;
                        } else if (pestName.equals("slug")) {
                           boxYOffset = 1.3F * scale;
                           boxWidth = 0.8F * scale;
                           boxHeight = 0.4F * scale;
                        } else if (pestName.equals("field mouse")) {
                           boxYOffset = 1.5F * scale;
                        }

                        float textYOffset = 2.5F;
                        if (pestName.contains("worm")) {
                           textYOffset = 1.5F;
                        } else if (pestName.equals("slug")) {
                           textYOffset = 1.9F;
                        } else if (pestName.equals("field mouse")) {
                           textYOffset = 2.7F;
                        }

                        float scaledX = (float)x * scale;
                        float scaledY = (float)y * scale;
                        float scaledZ = (float)z * scale;
                        AABB box = new AABB((double)(scaledX - boxWidth / 2.0F), (double)(scaledY + boxYOffset), (double)(scaledZ - boxWidth / 2.0F), (double)(scaledX + boxWidth / 2.0F), (double)(scaledY + boxYOffset + boxHeight), (double)(scaledZ + boxWidth / 2.0F));
                        RenderType renderType = s.hideCheats ? RenderTypes.lines() : RenderTypes.linesTranslucent();
                        collector.submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, box, r, g, b, a, lineWidth));
                        boolean shouldTrace = false;
                        if ("rat".equals(pestName)) {
                           shouldTrace = s.tracerRat;
                        } else if (!"worm".equals(pestName) && !"wormass".equals(pestName)) {
                           if ("slug".equals(pestName)) {
                              shouldTrace = s.tracerSlug;
                           } else if ("fly".equals(pestName)) {
                              shouldTrace = s.tracerFly;
                           } else if ("locust".equals(pestName)) {
                              shouldTrace = s.tracerLocust;
                           } else if ("beetle".equals(pestName)) {
                              shouldTrace = s.tracerBeetle;
                           } else if ("cricket".equals(pestName)) {
                              shouldTrace = s.tracerCricket;
                           } else if ("spider".equals(pestName)) {
                              shouldTrace = s.tracerSpider;
                           } else if ("moth".equals(pestName)) {
                              shouldTrace = s.tracerMoth;
                           } else if ("mite".equals(pestName)) {
                              shouldTrace = s.tracerMite;
                           } else if ("field mouse".equals(pestName)) {
                              shouldTrace = s.tracerMouse;
                           } else if ("mosquito".equals(pestName)) {
                              shouldTrace = s.tracerMosquito;
                           }
                        } else {
                           shouldTrace = s.tracerWorm;
                        }

                        if (!s.hideCheats && s.pestEspTracer && shouldTrace) {
                           float endX = (float)x;
                           float tempEndY = (float)(y + (double)1.3F + (double)0.5F);
                           if (pestName.contains("worm")) {
                              tempEndY = (float)(y + (double)0.5F + (double)0.4F);
                           } else if (pestName.equals("slug")) {
                              tempEndY = (float)(y + (double)1.3F + (double)0.2F);
                           } else if (pestName.equals("field mouse")) {
                              tempEndY = (float)(y + (double)1.5F + (double)0.5F);
                           }

                           float endZ = (float)z;
                           final float endY = tempEndY;
                           Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
                           float startX = look.x();
                           float startY = look.y();
                           float startZ = look.z();
                           collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, r, g, b, a, 2.0F));
                        }

                        String var10000 = pestName.substring(0, 1).toUpperCase();
                        String displayName = var10000 + pestName.substring(1);
                        BomboRenderUtils.drawText(poseStack, collector, "§e" + displayName, (float)x, (float)(y + (double)textYOffset), (float)z, 16777215, 0.03F, true, !s.hideCheats);
                     }
                  }
               }

               if (debugTicks % 200 == 0 && s.debugEntities) {
                  System.out.println("DEBUG: Scanned " + armorStands + " armor stands, found " + count + " pests.");
               }

            } else {
               TRACERS.clear();
            }
         }
      }
   }

   public static class PestTracer {
      public Vector2f start;
      public Vector2f end;
      public int color;
      public float thickness;
   }
}
