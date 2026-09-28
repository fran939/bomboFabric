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
        if (Flavor.get().isFreecamActive() || me.bombo.bomboaddons.util.SpectatorCamManager.isActive() || (mc != null && mc.player != null && mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player)) {
            double baseFov = (double) (Integer) mc.options.fov().get();
            double clamped = Math.min(110.0, Math.max(30.0, baseFov));
            cir.setReturnValue(clamped);
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(
        method = "renderItemInHand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;")
    )
    private net.minecraft.world.level.GameType bombo$allowHandInSpectator(net.minecraft.client.multiplayer.MultiPlayerGameMode gameMode) {
        if (me.bombo.bomboaddons.util.SpectatorCamManager.isActive()) {
            return net.minecraft.world.level.GameType.SURVIVAL;
        }
        return gameMode.getPlayerMode();
    }
}
