package me.bombo.bomboaddons.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
public abstract class PostPassMixin {

    @Shadow
    private java.util.Map<java.lang.String, com.mojang.blaze3d.buffers.GpuBuffer> customUniforms;

    @Inject(method = "addToFrame", at = @At("HEAD"))
    private void onAddToFrame(com.mojang.blaze3d.framegraph.FrameGraphBuilder builder, java.util.Map<?, ?> map, com.mojang.blaze3d.buffers.GpuBufferSlice slice, CallbackInfo ci) {
        applyThickerGlow();
    }

    private void applyThickerGlow() {
        try {
            if (customUniforms != null) {
                com.mojang.blaze3d.buffers.GpuBuffer radiusBuffer = customUniforms.get("Radius");
                if (radiusBuffer != null) {
                    com.mojang.blaze3d.buffers.GpuBufferSlice.MappedView mappedView = radiusBuffer.map(false, true);
                    if (mappedView != null) {
                        java.nio.ByteBuffer byteBuffer = mappedView.data();
                        if (byteBuffer != null) {
                            byteBuffer.putFloat(0, 4.0f);
                        }
                        mappedView.close();
                    }
                }
            }
        } catch (Exception ignored) {
            // Silently fail to ensure game stability
        }
    }
}
