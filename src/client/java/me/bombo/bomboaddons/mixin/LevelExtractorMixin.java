package me.bombo.bomboaddons.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {
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
