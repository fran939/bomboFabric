package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
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
        // Bottom 4 lines
        drawLine(matrix, buffer, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, lineWidth);

        // Top 4 lines
        drawLine(matrix, buffer, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, lineWidth);

        // Vertical 4 lines
        drawLine(matrix, buffer, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, lineWidth);
        drawLine(matrix, buffer, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth);
    }

    public static void draw2DLine(net.minecraft.client.gui.GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, int color, float thickness) {
        if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)) return;
        
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0 || len > 5000.0f || !Float.isFinite(len)) return;
        
        float angle = (float) Math.atan2(dy, dx);
        if (!Float.isFinite(angle)) return;
        
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate(angle);
        
        // Draw a rectangle as the line. Use a larger range for the fill to ensure it covers the thickness.
        // Since fill takes ints, we scale up or just use what we have.
        int h = (int)Math.max(1, thickness);
        int opaqueColor = 0xFF000000 | color;
        graphics.fill(0, -h/2, (int)len, h/2, opaqueColor);
        
        pose.popMatrix();
    }

    /**
     * Draws a single line segment using the 1.21.1 'LINES' format logic.
     * In 1.21.1, the 'Normal' attribute is used by the shader to find the end of the segment: End = Position + Normal.
     */
    public static void drawLine(Matrix4f matrix, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float lineWidth) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            nx /= len;
            ny /= len;
            nz /= len;
        } else {
            nx = 0; ny = 1; nz = 0;
        }
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(-nx, -ny, -nz).setLineWidth(lineWidth);
    }

    public static void drawHorizontalCircle(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
        drawHorizontalCircle(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
    }

    public static void drawHorizontalCircle(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
        int segments = 32;
        double angleStep = 2.0 * Math.PI / segments;
        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleStep;
            double angle2 = (i + 1) * angleStep;
            float x1 = cx + (float) (radius * Math.cos(angle1));
            float z1 = cz + (float) (radius * Math.sin(angle1));
            float x2 = cx + (float) (radius * Math.cos(angle2));
            float z2 = cz + (float) (radius * Math.sin(angle2));
            drawLine(matrix, buffer, x1, cy, z1, x2, cy, z2, r, g, b, a, lineWidth);
        }
    }

    public static void drawVerticalCircleXY(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
        drawVerticalCircleXY(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
    }

    public static void drawVerticalCircleXY(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
        int segments = 32;
        double angleStep = 2.0 * Math.PI / segments;
        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleStep;
            double angle2 = (i + 1) * angleStep;
            float x1 = cx + (float) (radius * Math.cos(angle1));
            float y1 = cy + (float) (radius * Math.sin(angle1));
            float x2 = cx + (float) (radius * Math.cos(angle2));
            float y2 = cy + (float) (radius * Math.sin(angle2));
            drawLine(matrix, buffer, x1, y1, cz, x2, y2, cz, r, g, b, a, lineWidth);
        }
    }

    public static void drawVerticalCircleYZ(PoseStack poseStack, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
        drawVerticalCircleYZ(poseStack.last().pose(), buffer, cx, cy, cz, radius, r, g, b, a, lineWidth);
    }

    public static void drawVerticalCircleYZ(Matrix4f matrix, VertexConsumer buffer, float cx, float cy, float cz, float radius, float r, float g, float b, float a, float lineWidth) {
        int segments = 32;
        double angleStep = 2.0 * Math.PI / segments;
        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleStep;
            double angle2 = (i + 1) * angleStep;
            float y1 = cy + (float) (radius * Math.cos(angle1));
            float z1 = cz + (float) (radius * Math.sin(angle1));
            float y2 = cy + (float) (radius * Math.cos(angle2));
            float z2 = cz + (float) (radius * Math.sin(angle2));
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

    public static void drawText(PoseStack poseStack, me.bombo.bomboaddons.OrderedSubmitNodeCollector collector, String text, float x, float y, float z, int color, float scale, boolean shadow) {
        drawText(poseStack, collector, text, x, y, z, color, scale, shadow, true);
    }

    public static void drawText(PoseStack poseStack, me.bombo.bomboaddons.OrderedSubmitNodeCollector collector, String text, float x, float y, float z, int color, float scale, boolean shadow, boolean seeThrough) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.Font font = mc.font;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // Billboard effect
        poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
        poseStack.scale(-scale, -scale, scale);

        float offset = -font.width(text) / 2.0f;

        net.minecraft.client.gui.Font.DisplayMode mode = seeThrough ? net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH : net.minecraft.client.gui.Font.DisplayMode.NORMAL;
        net.minecraft.util.FormattedCharSequence seq = net.minecraft.network.chat.Component.literal(text).getVisualOrderText();
        collector.submitText(poseStack, offset, 0.0f, seq, shadow, mode, 15728880, color, 0, 0);

        poseStack.popPose();
    }

    public static int hexToColor(String hex) {
        return colorNameToHex(hex);
    }

    public static int colorNameToHex(String name) {
        if (name == null) return 0xFFFF00; // Default to Yellow
        switch (name.toLowerCase()) {
            case "red": return 0xFF0000;
            case "green": return 0x00FF00;
            case "blue": return 0x0000FF;
            case "yellow": return 0xFFFF00;
            case "orange": return 0xFFAA00;
            case "purple": return 0xA020F0;
            case "aqua": return 0x00FFFF;
            case "white": return 0xFFFFFF;
            case "black": return 0x000000;
            case "gold": return 0xD4AF37;
            case "pink": return 0xFFC0CB;
            default:
                try {
                    if (name.startsWith("#")) name = name.substring(1);
                    return (int) Long.parseLong(name, 16);
                } catch (Exception e) {
                    return 0xFFFF00; // Default to Yellow
                }
        }
    }
}
