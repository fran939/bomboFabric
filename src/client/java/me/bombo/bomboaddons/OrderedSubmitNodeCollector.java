package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

public class OrderedSubmitNodeCollector {
    private final MultiBufferSource bufferSource;

    public OrderedSubmitNodeCollector(MultiBufferSource bufferSource) {
        this.bufferSource = bufferSource;
    }

    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
        if (bufferSource == null) return;
        VertexConsumer vc = bufferSource.getBuffer(renderType);
        renderer.render(poseStack.last(), vc);
    }

    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence seq, boolean shadow, Font.DisplayMode mode, int light, int color, int selectStart, int selectEnd) {
        if (bufferSource == null) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.font.drawInBatch(seq, x, y, color, shadow, poseStack.last().pose(), bufferSource, mode, 0, light);
    }

    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer vc);
    }
}
