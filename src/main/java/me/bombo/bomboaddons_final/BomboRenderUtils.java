package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class BomboRenderUtils {
    public static void drawBox(PoseStack poseStack, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a, float lineWidth) {
        drawBox(poseStack, buffer, (float)aabb.minX, (float)aabb.minY, (float)aabb.minZ, (float)aabb.maxX, (float)aabb.maxY, (float)aabb.maxZ, r, g, b, a, lineWidth);
    }

    public static void drawBox(PoseStack poseStack, VertexConsumer buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a, float lineWidth) {
        Matrix4f matrix = poseStack.last().pose();

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

    public static void draw2DLine(net.minecraft.client.gui.GuiGraphics graphics, float x1, float y1, float x2, float y2, int color, float thickness) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        
        float angle = (float) Math.atan2(dy, dx);
        
        org.joml.Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate(angle);
        
        // Draw a rectangle as the line. Use a larger range for the fill to ensure it covers the thickness.
        // Since fill takes ints, we scale up or just use what we have.
        int h = (int)Math.max(1, thickness);
        graphics.fill(0, -h/2, (int)len, h/2, color);
        
        pose.popMatrix();
    }

    /**
     * Draws a single line segment using the 1.21.1 'LINES' format logic.
     * In 1.21.1, the 'Normal' attribute is used by the shader to find the end of the segment: End = Position + Normal.
     */
    private static void drawLine(Matrix4f matrix, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a, float lineWidth) {
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

    public static void drawText(PoseStack poseStack, net.minecraft.client.renderer.MultiBufferSource consumers, String text, float x, float y, float z, int color, float scale, boolean shadow) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.Font font = mc.font;

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        // Billboard effect
        poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        float offset = -font.width(text) / 2.0f;

        // Use see-through text renderer if possible, or just normal for now
        font.drawInBatch(text, offset, 0, color, shadow, matrix, consumers, net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 15728880);

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
