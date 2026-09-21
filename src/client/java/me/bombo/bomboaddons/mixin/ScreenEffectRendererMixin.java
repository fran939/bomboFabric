package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void onRenderFireOverlay(CallbackInfo ci) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.noRenderFireOverlay) {
            ci.cancel();
        }
    }
}
