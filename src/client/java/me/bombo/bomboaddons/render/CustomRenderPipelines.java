package me.bombo.bomboaddons.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;

public class CustomRenderPipelines {
   public static final RenderPipeline ENTITY_TRANSLUCENT_ESP;
   public static final RenderPipeline BEACON_BEAM_ESP;
   public static final RenderPipeline PRIMITIVE_QUADS_NO_DEPTH;
   public static final RenderPipeline PRIMITIVE_LINES_NO_DEPTH;

   private CustomRenderPipelines() {
   }

   static {
      ENTITY_TRANSLUCENT_ESP = RenderPipelines.register(
         RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.ENTITY_SNIPPET})
            .withDepthStencilState(Optional.empty())
            .withLocation("bombo/entity_translucent_esp")
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .build()
      );
      BEACON_BEAM_ESP = RenderPipelines.register(
         RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.BEACON_BEAM_SNIPPET})
            .withDepthStencilState(Optional.empty())
            .withLocation("bombo/beacon_beam_esp")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .build()
      );
      PRIMITIVE_QUADS_NO_DEPTH = RenderPipelines.register(
         RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.DEBUG_FILLED_SNIPPET})
            .withDepthStencilState(Optional.empty())
            .withLocation("bombo/primitive_quads_no_depth")
            .build()
      );
      PRIMITIVE_LINES_NO_DEPTH = RenderPipelines.register(
         RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.LINES_SNIPPET})
            .withDepthStencilState(Optional.empty())
            .withLocation("bombo/primitive_lines_no_depth")
            .build()
      );
   }
}
