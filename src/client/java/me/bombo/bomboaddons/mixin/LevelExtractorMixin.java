package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.flavor.Flavor;
import me.bombo.bomboaddons.util.SpectatorCamManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true)
    private void bombo$keepPlayerBodyVisible(Entity entity, Frustum frustum,
                                             double camX, double camY, double camZ,
                                             CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && entity == mc.player) {
            if (SpectatorCamManager.isActive() || Flavor.get().isFreecamActive()) {
                cir.setReturnValue(Boolean.TRUE);
            }
        }
    }

    @Redirect(
        method = "extractVisibleEntities",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;",
            ordinal = 3
        )
    )
    private Entity redirectLocalPlayerCameraEntity(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            return mc.player;
        }
        return camera.entity();
    }
}
