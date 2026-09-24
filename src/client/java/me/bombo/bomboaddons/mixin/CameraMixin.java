package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.util.FreelookManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Camera.class})
public abstract class CameraMixin {
   @Shadow protected abstract void setPosition(double x, double y, double z);
   @Shadow protected abstract void setRotation(float yRot, float xRot);

   @ModifyVariable(
      method = {"setRotation"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private float modifyYaw(float yRot) {
      if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) return me.bombo.bomboaddons.flavor.Flavor.get().freecamYaw();
      return FreelookManager.isFreelookActive() ? FreelookManager.getFreelookYaw() : yRot;
   }

   @ModifyVariable(
      method = {"setRotation"},
      at = @At("HEAD"),
      ordinal = 1,
      argsOnly = true
   )
   private float modifyPitch(float xRot) {
      if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) return me.bombo.bomboaddons.flavor.Flavor.get().freecamPitch();
      return FreelookManager.isFreelookActive() ? FreelookManager.getFreelookPitch() : xRot;
   }

   @Inject(method = "alignWithEntity", at = @At("TAIL"))
   private void onAlignWithEntityTail(float partialTicks, CallbackInfo ci) {
      if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) {
         this.setPosition(me.bombo.bomboaddons.flavor.Flavor.get().freecamX(), me.bombo.bomboaddons.flavor.Flavor.get().freecamY(), me.bombo.bomboaddons.flavor.Flavor.get().freecamZ());
         this.setRotation(me.bombo.bomboaddons.flavor.Flavor.get().freecamYaw(), me.bombo.bomboaddons.flavor.Flavor.get().freecamPitch());
      }
   }

   @Inject(method = "update", at = @At("TAIL"))
   private void onUpdateTail(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
      if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) {
         this.setPosition(me.bombo.bomboaddons.flavor.Flavor.get().freecamX(), me.bombo.bomboaddons.flavor.Flavor.get().freecamY(), me.bombo.bomboaddons.flavor.Flavor.get().freecamZ());
         this.setRotation(me.bombo.bomboaddons.flavor.Flavor.get().freecamYaw(), me.bombo.bomboaddons.flavor.Flavor.get().freecamPitch());
      }
   }

   @Inject(
      method = {"getMaxZoom"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetMaxZoom(float originalDist, CallbackInfoReturnable<Float> cir) {
      if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) {
         cir.setReturnValue(originalDist);
         return;
      }
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.cameraSettingsEnabled) {
         float dist = s.cameraDistance;
         float effectiveDist = s.hideCheats ? Math.min(4.0F, Math.max(0.5F, dist)) : Math.max(0.5F, dist);
         if (!s.hideCheats && s.cameraPassThroughWalls) {
            cir.setReturnValue(effectiveDist);
         }
      }
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
      if (me.bombo.bomboaddons.flavor.Flavor.get().isFreecamActive()) {
         cir.setReturnValue(f);
         return;
      }
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
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         if (s.clearWaterAndLava || s.waterOpacity < 50 || s.lavaOpacity < 50) {
            cir.setReturnValue(FogType.NONE);
         }
      }
   }
}
