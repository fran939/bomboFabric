package me.bombo.bomboaddons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

public class OrderedSubmitNodeCollector {
   private final net.minecraft.client.renderer.OrderedSubmitNodeCollector collector;

   public OrderedSubmitNodeCollector(net.minecraft.client.renderer.OrderedSubmitNodeCollector collector) {
      this.collector = collector;
   }

   public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
      this.collector.submitCustomGeometry(poseStack, renderType, renderer::render);
   }

   public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence seq, boolean shadow, Font.DisplayMode mode, int light, int color, int selectStart, int selectEnd) {
      this.collector.submitText(poseStack, x, y, seq, shadow, mode, light, color, selectStart, selectEnd);
   }

   public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> modelParts, int[] tintLayers, int light, int overlay, int tintColor) {
      this.collector.submitBlockModel(poseStack, renderType, modelParts, tintLayers, light, overlay, tintColor);
   }

   public interface CustomGeometryRenderer {
      void render(PoseStack.Pose var1, VertexConsumer var2);
   }
}
