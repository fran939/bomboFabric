package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.cheat.esp.HighlightESP;
import me.bombo.bomboaddons.util.HeadOnlyRenderState;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
        at = @At("TAIL")
    )
    private void onExtractRenderStateTail(ArmorStand entity, ArmorStandRenderState state, float f, CallbackInfo ci) {
        if (state instanceof HeadOnlyRenderState h) {
            boolean headOnly = entity != null && HighlightESP.shouldHideBody(entity);
            h.bombo$setHeadOnly(headOnly);
            if (headOnly) {
                state.showArms = false;
                state.showBasePlate = false;
            }
        }
    }

    @Inject(
        method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onGetRenderType(ArmorStandRenderState state, boolean isBodyVisible, boolean translucent, boolean appearsGlowing, CallbackInfoReturnable<RenderType> cir) {
        if (state instanceof HeadOnlyRenderState h && h.bombo$isHeadOnly()) {
            cir.setReturnValue(null);
        }
    }
}
