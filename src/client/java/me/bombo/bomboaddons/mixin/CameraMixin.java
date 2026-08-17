package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.FreelookManager;
import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @ModifyVariable(
      method = {"setRotation"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private float modifyYaw(float yRot) {
      return FreelookManager.isFreelookActive() ? FreelookManager.getFreelookYaw() : yRot;
   }

   @ModifyVariable(
      method = {"setRotation"},
      at = @At("HEAD"),
      ordinal = 1,
      argsOnly = true
   )
   private float modifyPitch(float xRot) {
      return FreelookManager.isFreelookActive() ? FreelookManager.getFreelookPitch() : xRot;
   }

   @Inject(
      method = {"getFluidInCamera"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetFluidInCamera(CallbackInfoReturnable<FogType> cir) {
      if (BomboConfig.get().clearWaterAndLava) {
         cir.setReturnValue(FogType.NONE);
      }

   }
}
