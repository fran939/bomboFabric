package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Iterator;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;

public class DisplayESP {
   public static void render(LevelRenderContext context) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s.displayEsp) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null) {
            PoseStack poseStack = context.poseStack();
            OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.bufferSource());
            int colorInt = BomboRenderUtils.colorNameToHex(s.displayEspColor);
            float r = (float)(colorInt >> 16 & 255) / 255.0F;
            float g = (float)(colorInt >> 8 & 255) / 255.0F;
            float b = (float)(colorInt & 255) / 255.0F;
            float a = 1.0F;
            float lineWidth = s.displayEspThickness;
            Vec3 camPos = mc.gameRenderer.getMainCamera().position();
            Vector3fc look = mc.gameRenderer.getMainCamera().forwardVector();
            float startX = look.x();
            float startY = look.y();
            float startZ = look.z();
            Iterator var16 = mc.level.entitiesForRendering().iterator();

            while(true) {
               Entity entity;
               AABB aabb;
               String extraInfo;
               String typeStr;
               while(true) {
                  if (!var16.hasNext()) {
                     return;
                  }

                  entity = (Entity)var16.next();
                  if (entity instanceof Display || entity instanceof Interaction) {
                     AABB var10000;
                     if (entity instanceof Display) {
                        Display display = (Display)entity;
                        var10000 = display.getBoundingBoxForCulling();
                     } else {
                        var10000 = entity.getBoundingBox();
                     }

                     aabb = var10000;
                     typeStr = "Unknown";
                     extraInfo = "";
                     if (entity instanceof Display.TextDisplay) {
                        Display.TextDisplay td = (Display.TextDisplay)entity;
                        typeStr = "Text Display";
                        if (td.getText() != null) {
                           String textStr = td.getText().getString();
                           extraInfo = textStr.substring(0, Math.min(textStr.length(), 20));
                        }
                     } else if (entity instanceof Display.ItemDisplay) {
                        Display.ItemDisplay id = (Display.ItemDisplay)entity;
                        typeStr = "Item Display";
                        if (id.getItemStack() != null) {
                           extraInfo = id.getItemStack().getHoverName().getString();
                        }
                     } else if (entity instanceof Display.BlockDisplay) {
                        Display.BlockDisplay bd = (Display.BlockDisplay)entity;
                        typeStr = "Block Display";
                        if (bd.getBlockState() != null) {
                           extraInfo = bd.getBlockState().getBlock().getName().getString();
                        }
                     } else if (entity instanceof Interaction) {
                        Interaction interaction = (Interaction)entity;
                        typeStr = "Interaction";
                        extraInfo = String.format("W:%.1f H:%.1f", interaction.getBbWidth(), interaction.getBbHeight());
                     }

                     String filterStr = s.displayEspFilter != null ? s.displayEspFilter.trim() : "";
                     if (filterStr.isEmpty()) {
                        break;
                     }

                     boolean matches = false;
                     String[] filters = filterStr.toLowerCase().split(",");
                     String searchTarget = (typeStr + " " + entity.getUUID().toString() + " " + extraInfo).toLowerCase();

                     for(String filter : filters) {
                        String f = filter.trim();
                        if (!f.isEmpty() && searchTarget.contains(f)) {
                           matches = true;
                           break;
                        }
                     }

                     if (matches) {
                        break;
                     }
                  }
               }

               double camX = camPos.x;
               double camY = camPos.y;
               double camZ = camPos.z;
               float minX = (float)(aabb.minX - camX);
               float minY = (float)(aabb.minY - camY);
               float minZ = (float)(aabb.minZ - camZ);
               float maxX = (float)(aabb.maxX - camX);
               float maxY = (float)(aabb.maxY - camY);
               float maxZ = (float)(aabb.maxZ - camZ);
               collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> {
                  Matrix4f matrix = pose.pose();
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
                  BomboRenderUtils.drawLine(matrix, vertexConsumer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
               });
               if (s.displayEspTracer) {
                  float endX = (float)(entity.getX() - camX);
                  float endY = (float)(entity.getY() - camY + aabb.getYsize() / (double)2.0F);
                  float endZ = (float)(entity.getZ() - camZ);
                  collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, vertexConsumer) -> BomboRenderUtils.drawLine(pose.pose(), vertexConsumer, startX, startY, startZ, endX, endY, endZ, r, g, b, a, lineWidth));
               }

               String label = String.format("§e%s §7(UUID: %s) §f%s", typeStr, entity.getUUID().toString().substring(0, 8), extraInfo);
               float labelX = (float)(entity.getX() - camX);
               float labelY = (float)(entity.getY() - camY + aabb.getYsize() + (double)0.5F);
               float labelZ = (float)(entity.getZ() - camZ);
               BomboRenderUtils.drawText(poseStack, collector, label, labelX, labelY, labelZ, 16777215, 0.025F, true, true);
            }
         }
      }
   }
}
