package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PostPass.class})
public abstract class PostPassMixin {
   @Shadow
   private Map<String, GpuBuffer> customUniforms;

   @Inject(
      method = {"addToFrame"},
      at = {@At("HEAD")}
   )
   private void onAddToFrame(FrameGraphBuilder builder, Map<?, ?> map, GpuBufferSlice slice, CallbackInfo ci) {
      this.applyThickerGlow();
   }

   private void applyThickerGlow() {
      try {
         if (this.customUniforms != null) {
            GpuBuffer radiusBuffer = (GpuBuffer)this.customUniforms.get("Radius");
            if (radiusBuffer != null) {
               GpuDevice device = RenderSystem.getDevice();
               CommandEncoder encoder = device.createCommandEncoder();
               GpuBuffer.MappedView mappedView = encoder.mapBuffer(radiusBuffer, false, true);
               if (mappedView != null) {
                  ByteBuffer byteBuffer = mappedView.data();
                  if (byteBuffer != null) {
                     byteBuffer.putFloat(0, 4.0F);
                  }

                  mappedView.close();
               }
            }
         }
      } catch (Exception var6) {
      }

   }
}
