package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class HighlightESP {
    public static void render(WorldRenderContext context) {
        if (!BomboConfig.get().hitbox) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Map<String, BomboConfig.HighlightInfo> highlights = BomboConfig.get().highlights;
        if (highlights.isEmpty()) return;

        PoseStack poseStack = context.matrices();
        // Use gameRenderer to get main camera position for 1.21.10
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        // Use standard lines render type for ESP
        VertexConsumer linesBuffer = bufferSource.getBuffer(RenderType.lines());

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;

            String name = net.minecraft.ChatFormatting.stripFormatting(entity.getDisplayName().getString()).toLowerCase();
            
            for (Map.Entry<String, BomboConfig.HighlightInfo> entry : highlights.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (name.contains(key)) {
                    if (entity.isInvisible() && !entry.getValue().showInvisible) continue;

                    // Get color from formatting name
                    int colorInt = SlotHighlight.getFormattingColor(entry.getValue().color);
                    float r = ((colorInt >> 16) & 0xFF) / 255.0f;
                    float g = ((colorInt >> 8) & 0xFF) / 255.0f;
                    float b = (colorInt & 0xFF) / 255.0f;
                    float a = 1.0f;

                    AABB box = entity.getBoundingBox();
                    BomboRenderUtils.drawBox(poseStack, linesBuffer, box, r, g, b, a);
                    break;
                }
            }
        }

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
