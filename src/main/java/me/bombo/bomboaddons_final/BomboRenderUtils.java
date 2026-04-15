package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class BomboRenderUtils {
    public static void drawBox(PoseStack poseStack, VertexConsumer buffer, AABB aabb, float r, float g, float b, float a) {
        Matrix4f matrix = poseStack.last().pose();

        float minX = (float) aabb.minX;
        float minY = (float) aabb.minY;
        float minZ = (float) aabb.minZ;
        float maxX = (float) aabb.maxX;
        float maxY = (float) aabb.maxY;
        float maxZ = (float) aabb.maxZ;

        // Bottom
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);

        // Top
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);

        // Sides
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(1.0f, 1.0f, 1.0f);
    }

    public static int hexToColor(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (Exception e) {
            return 0xFFAA00;
        }
    }
}
