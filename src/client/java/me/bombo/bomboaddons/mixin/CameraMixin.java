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

   @ModifyVariable(
      method = {"getMaxZoom"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private float modifyMaxZoomDistance(float originalDist) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.cameraSettingsEnabled) {
         float dist = s.cameraDistance;
         if (s.hideCheats) {
            return Math.min(4.0F, Math.max(0.5F, dist));
         } else {
            return Math.max(0.5F, dist);
         }
      }
      return originalDist;
   }

   @Inject(
      method = {"clipToDistance"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onClipToDistance(float f, CallbackInfoReturnable<Float> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.cameraSettingsEnabled && !s.hideCheats && s.cameraPassThroughWalls) {
         cir.setReturnValue(f);
      }
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
