package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.HighlightESP;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorStandRenderer.class)
public abstract class ArmorStandRendererMixin {
    @Unique
    private static final java.util.WeakHashMap<ArmorStandRenderState, Boolean> BOMBO_HEAD_ONLY_MAP = new java.util.WeakHashMap<>();

    public static boolean isHeadOnly(ArmorStandRenderState state) {
        return state != null && Boolean.TRUE.equals(BOMBO_HEAD_ONLY_MAP.get(state));
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/decoration/ArmorStand;Lnet/minecraft/client/renderer/entity/state/ArmorStandRenderState;F)V",
        at = @At("TAIL")
    )
    private void onExtractRenderStateTail(ArmorStand entity, ArmorStandRenderState state, float f, CallbackInfo ci) {
        if (entity != null && state != null && HighlightESP.shouldHideBody(entity)) {
            BOMBO_HEAD_ONLY_MAP.put(state, true);
        }
    }
}
