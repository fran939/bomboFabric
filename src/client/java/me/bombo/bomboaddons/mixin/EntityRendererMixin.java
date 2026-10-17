package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.kuudra.KuudraGeneral;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity> {

    @Inject(
        method = "shouldRender",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bombo$onShouldRender(T entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity living && KuudraGeneral.shouldHideDeathAnimation(living)) {
            cir.setReturnValue(false);
            return;
        }

        if (entity instanceof ArmorStand stand && KuudraGeneral.shouldHideArmorStand(stand)) {
            cir.setReturnValue(false);
        }
    }
}
