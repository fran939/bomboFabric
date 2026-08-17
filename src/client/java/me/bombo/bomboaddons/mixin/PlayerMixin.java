package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public class PlayerMixin {
   @Inject(
      method = {"swing(Lnet/minecraft/world/InteractionHand;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSwing(InteractionHand hand, CallbackInfo ci) {
      if ((Object)this == Minecraft.getInstance().player && Minecraft.getInstance().screen != null) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"hasEffect"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this == Minecraft.getInstance().player && effect.is(MobEffects.BLINDNESS) && BomboConfig.get().disableBlindness) {
         cir.setReturnValue(false);
      }

   }
}
