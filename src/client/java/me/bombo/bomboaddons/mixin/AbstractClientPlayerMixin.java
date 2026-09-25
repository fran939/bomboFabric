package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.flavor.Flavor;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void bombo$clampFreecamFov(boolean isScoping, float partialTicks, CallbackInfoReturnable<Float> cir) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (Flavor.get().isFreecamActive() || (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player)) {
            cir.setReturnValue(1.0F);
        }
    }
}
