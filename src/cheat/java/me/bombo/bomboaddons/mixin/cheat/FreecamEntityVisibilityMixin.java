package me.bombo.bomboaddons.mixin.cheat;

import me.bombo.bomboaddons.cheat.camera.FreecamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the player's own body on screen while freecam is detached from it.
 *
 * <p>Freecam moves the render camera away from the player, which silently made the body
 * disappear: {@code LevelExtractor#isEntityVisible} culls it through
 * {@code Entity#shouldRenderAtSqrDistance} (roughly 115 blocks for a player bounding box)
 * and through {@code LevelRenderer.isSectionCompiledAndVisible} for the player's chunk.
 * Both checks are meaningless for the entity the camera belongs to, so while freecam is
 * active this mixin answers {@code true} for {@code mc.player} only.
 *
 * <p>Cheat-only: it ships in {@code bomboclient} (see {@code bomboclient.client.mixins.json}).
 */
@Mixin(LevelExtractor.class)
public abstract class FreecamEntityVisibilityMixin {

    @Inject(method = "isEntityVisible", at = @At("HEAD"), cancellable = true, require = 0)
    private void bombo$keepPlayerBodyVisible(Entity entity, Frustum frustum,
                                             double camX, double camY, double camZ,
                                             CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || entity != mc.player) {
            return;
        }
        if (FreecamManager.isFreecamActive()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
