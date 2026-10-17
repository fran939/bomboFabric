package me.bombo.bomboaddons.mixin;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z", at = @At("HEAD"), cancellable = true)
    private void bombo$alwaysRenderLayers(AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Boolean.TRUE);
    }

    @Inject(
        method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("TAIL")
    )
    private void bombo$scalePlayer(AvatarRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (me.bombo.bomboaddons.features.misc.PlayerSizing.shouldScalePlayer()) {
            float s = me.bombo.bomboaddons.features.misc.PlayerSizing.getPlayerScale();
            if (s != 1.0F) {
                poseStack.scale(s, s, s);
            }
        }
    }
}
