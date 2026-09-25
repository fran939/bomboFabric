package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.flavor.Flavor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void bombo$clampFov(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (Flavor.get().isFreecamActive() || (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player)) {
            double fov = (double) (Integer) mc.options.fov().get();
            cir.setReturnValue(fov);
        }
    }
}
