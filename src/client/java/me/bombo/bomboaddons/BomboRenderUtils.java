package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

public class BomboRenderUtils {
   public static void drawBox(PoseStack poseStack, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a, float lineWidth) {
      drawBox(poseStack.last().pose(), buffer, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ, r, g, b, a, lineWidth);
   }

   public static void drawBox(Matrix4f matrix, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a, float lineWidth) {
      drawBox(matrix, buffer, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ, r, g, b, a, lineWidth);
   }

   public static void drawBox(PoseStack poseStack, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a, float lineWidth) {
      drawBox(poseStack.last().pose(), buffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
   }

   public static void drawBox(Matrix4f matrix, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a, float lineWidth) {
      drawLine(matrix, buffer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
      drawLine(matrix, buffer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
   }

   public static void draw2DLine(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color, float thickness) {
      if (Float.isFinite(x1) && Float.isFinite(y1) && Float.isFinite(x2) && Float.isFinite(y2)) {
         float dx = x2 - x1;
         float dy = y2 - y1;
         float len = (float)Math.sqrt((double)(dx * dx + dy * dy));
         if (!(len <= 0.0F) && !(len > 5000.0F) && Float.isFinite(len)) {
            float angle = (float)Math.atan2((double)dy, (double)dx);
            if (Float.isFinite(angle)) {
               Matrix3x2fStack pose = graphics.pose();
               pose.pushMatrix();
               pose.translate(x1, y1);
               pose.rotate(angle);
               int h = (int)Math.max(1.0F, thickness);
               int opaqueColor = -16777216 | color;
               graphics.fill(0, -h / 2, (int)len, h / 2, opaqueColor);
               pose.popMatrix();
            }
         }
      }
   }

   public static void drawLine(Matrix4f matrix, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float lineWidth) {
      float nx = x2 - x1;
      float ny = y2 - y1;
      float nz = z2 - z1;
      float len = (float)Math.sqrt((double)(nx * nx + ny * ny + nz * nz));
      if (len > 0.0F) {
         nx /= len;
         ny /= len;
         nz /= len;
      } else {
         nx = 0.0F;
         ny = 1.0F;
         nz = 0.0F;
      }

      buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
      buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(-nx, -ny, -nz).setLineWidth(lineWidth);
   }

   public static void drawHorizontalCircle(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      drawHorizontalCircle(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
   }

   public static void drawHorizontalCircle(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      int segments = 32;
      double angleStep = (Math.PI * 2D) / (double)segments;

      for(int i = 0; i < segments; ++i) {
         double angle1 = (double)i * angleStep;
         double angle2 = (double)(i + 1) * angleStep;
         float x1 = cx + (float)((double)radius * Math.cos(angle1));
         float z1 = cz + (float)((double)radius * Math.sin(angle1));
         float x2 = cx + (float)((double)radius * Math.cos(angle2));
         float z2 = cz + (float)((double)radius * Math.sin(angle2));
         drawLine(matrix, buffer, x1, cy, z1, x2, cy, z2, r, g, b, a, lineWidth);
      }

   }

   public static void drawVerticalCircleXY(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      drawVerticalCircleXY(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
   }

   public static void drawVerticalCircleXY(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      int segments = 32;
      double angleStep = (Math.PI * 2D) / (double)segments;

      for(int i = 0; i < segments; ++i) {
         double angle1 = (double)i * angleStep;
         double angle2 = (double)(i + 1) * angleStep;
         float x1 = cx + (float)((double)radius * Math.cos(angle1));
         float y1 = cy + (float)((double)radius * Math.sin(angle1));
         float x2 = cx + (float)((double)radius * Math.cos(angle2));
         float y2 = cy + (float)((double)radius * Math.sin(angle2));
         drawLine(matrix, buffer, x1, y1, cz, x2, y2, cz, r, g, b, a, lineWidth);
      }

   }

   public static void drawVerticalCircleYZ(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      drawVerticalCircleYZ(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
   }

   public static void drawVerticalCircleYZ(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      int segments = 32;
      double angleStep = (Math.PI * 2D) / (double)segments;

      for(int i = 0; i < segments; ++i) {
         double angle1 = (double)i * angleStep;
         double angle2 = (double)(i + 1) * angleStep;
         float y1 = cy + (float)((double)radius * Math.cos(angle1));
         float z1 = cz + (float)((double)radius * Math.sin(angle1));
         float y2 = cy + (float)((double)radius * Math.cos(angle2));
         float z2 = cz + (float)((double)radius * Math.sin(angle2));
         drawLine(matrix, buffer, cx, y1, z1, cx, y2, z2, r, g, b, a, lineWidth);
      }

   }

   public static void drawSphere(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      drawSphere(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
   }

   public static void drawSphere(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
      drawHorizontalCircle(matrix, buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
      drawVerticalCircleXY(matrix, buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
      drawVerticalCircleYZ(matrix, buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
   }

   public static void drawText(PoseStack poseStack, OrderedSubmitNodeCollector collector, String text, float x, float y, float z, int color, float scale, boolean shadow) {
      drawText(poseStack, collector, text, x, y, z, color, scale, shadow, true);
   }

   public static void drawText(PoseStack poseStack, OrderedSubmitNodeCollector collector, String text, float x, float y, float z, int color, float scale, boolean shadow, boolean seeThrough) {
      Minecraft mc = Minecraft.getInstance();
      Font font = mc.font;
      poseStack.pushPose();
      poseStack.translate(x, y, z);
      poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
      poseStack.scale(-scale, -scale, scale);
      float offset = (float)(-font.width(text)) / 2.0F;
      Font.DisplayMode mode = seeThrough ? DisplayMode.SEE_THROUGH : DisplayMode.NORMAL;
      FormattedCharSequence seq = Component.literal(text).getVisualOrderText();
      collector.submitText(poseStack, offset, 0.0F, seq, shadow, mode, 15728880, color, 0, 0);
      poseStack.popPose();
   }

   public static int hexToColor(String hex) {
      return colorNameToHex(hex);
   }

   public static int colorNameToHex(String name) {
      if (name == null) {
         return 16776960;
      } else {
         switch (name.toLowerCase().replace("_", "")) {
            case "red":
               return 16733525;
            case "darkred":
               return 11141120;
            case "green":
               return 5635925;
            case "darkgreen":
               return 43520;
            case "blue":
               return 5592575;
            case "darkblue":
               return 170;
            case "yellow":
               return 16777045;
            case "gold":
               return 16755200;
            case "orange":
               return 16755200;
            case "aqua":
               return 5636095;
            case "darkaqua":
               return 43690;
            case "purple":
            case "darkpurple":
               return 11141290;
            case "lightpurple":
               return 16733695;
            case "white":
               return 16777215;
            case "black":
               return 0;
            case "gray":
               return 11184810;
            case "darkgray":
               return 5592405;
            case "pink":
               return 16761035;
            default:
               try {
                  if (name.startsWith("#")) {
                     name = name.substring(1);
                  }

                  return (int)Long.parseLong(name, 16);
               } catch (Exception var4) {
                  return 16776960;
               }
         }
      }
   }
}
