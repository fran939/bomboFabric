package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void onExtractRenderState(GuiGraphicsExtractor context, CallbackInfo ci) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.noRenderBossBar) {
            ci.cancel();
        }
    }
}
