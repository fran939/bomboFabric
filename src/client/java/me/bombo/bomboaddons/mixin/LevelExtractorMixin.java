package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.flavor.Flavor;
import me.bombo.bomboaddons.util.SpectatorCamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ensures the local player's body and entity model remain visible in the world
 * when the camera is detached (spectating another entity or using freecam).
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    // Prevent frustum culling from hiding the local player's body when camera is away
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
}
