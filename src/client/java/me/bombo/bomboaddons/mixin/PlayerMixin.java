package me.bombo.bomboaddons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PlayerMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && Minecraft.getInstance().screen != null) {
            ci.cancel();
        }
    }

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void onHasEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == Minecraft.getInstance().player && effect.is(net.minecraft.world.effect.MobEffects.BLINDNESS) && me.bombo.bomboaddons.BomboConfig.get().disableBlindness) {
            cir.setReturnValue(false);
        }
    }
}
