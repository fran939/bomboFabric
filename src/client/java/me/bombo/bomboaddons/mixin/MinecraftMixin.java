package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.KuudraPerkClicker;
import me.bombo.bomboaddons.LeftClickEtherwarp;
import me.bombo.bomboaddons.PlaytimeTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public class MinecraftMixin {
   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
      if (KuudraPerkClicker.shouldBlockAttack()) {
         cir.setReturnValue(false);
         cir.cancel();
      } else {
         if (LeftClickEtherwarp.onLeftClick()) {
            cir.setReturnValue(false);
            cir.cancel();
         }

      }
   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onContinueAttack(boolean breaking, CallbackInfo ci) {
      if (LeftClickEtherwarp.isHoldingEtherwarp() || KuudraPerkClicker.shouldBlockAttack()) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"close"},
      at = {@At("HEAD")}
   )
   private void onClose(CallbackInfo ci) {
      PlaytimeTracker.sendPlaytimeDataToCloud(false);
   }
}
