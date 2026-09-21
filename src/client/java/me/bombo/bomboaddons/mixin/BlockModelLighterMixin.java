package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockModelLighter.class)
public class BlockModelLighterMixin {

   @Inject(method = "prepareQuadAmbientOcclusion", at = @At("RETURN"))
   private void onPrepareAO(BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, QuadInstance instance, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && (s.zamasuWorldWhite || "Zamasu".equalsIgnoreCase(s.guiThemeMode))) {
         instance.setColor(0xFFFFFFFF);
         instance.setLightCoords(0x00F000F0);
      }
   }

   @Inject(method = "prepareQuadFlat", at = @At("RETURN"))
   private void onPrepareFlat(BlockAndTintGetter level, BlockState state, BlockPos pos, int light, BakedQuad quad, QuadInstance instance, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && (s.zamasuWorldWhite || "Zamasu".equalsIgnoreCase(s.guiThemeMode))) {
         instance.setColor(0xFFFFFFFF);
         instance.setLightCoords(0x00F000F0);
      }
   }
}
