package me.bombo.bomboaddons.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public class CustomRenderLayer {
   private static final Map<Identifier, RenderType> BEACON_BEAM_ESP_CACHE = new ConcurrentHashMap<>();
   public static final RenderType PRIMITIVE_QUADS_NO_DEPTH;
   public static final RenderType PRIMITIVE_LINES_NO_DEPTH;

   private CustomRenderLayer() {
   }

   public static RenderType beaconBeamEsp(Identifier texture) {
      return BEACON_BEAM_ESP_CACHE.computeIfAbsent(texture, CustomRenderLayer::createBeaconBeamEsp);
   }

   private static RenderType createBeaconBeamEsp(Identifier texture) {
      return createTexturedEspLayer("bombo_beacon_beam_esp", CustomRenderPipelines.BEACON_BEAM_ESP, texture);
   }

   private static RenderType createTexturedEspLayer(String name, RenderPipeline pipeline, Identifier texture) {
      return RenderType.create(name, RenderSetup.builder(pipeline).withTexture("Sampler0", texture).sortOnUpload().createRenderSetup());
   }

   static {
      PRIMITIVE_QUADS_NO_DEPTH = RenderType.create("bombo_primitive_quads_no_depth", RenderSetup.builder(CustomRenderPipelines.PRIMITIVE_QUADS_NO_DEPTH).createRenderSetup());
      PRIMITIVE_LINES_NO_DEPTH = RenderType.create("bombo_primitive_lines_no_depth", RenderSetup.builder(CustomRenderPipelines.PRIMITIVE_LINES_NO_DEPTH).createRenderSetup());
   }
}
