package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Level.class})
public abstract class LevelMixin {
   @Inject(
      method = {"getOverworldClockTime"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetOverworldClockTime(CallbackInfoReturnable<Long> cir) {
      if (((Object)this) instanceof ClientLevel && BomboConfig.get().customTimeEnabled) {
         int hour = BomboConfig.get().customTimeHour;
         cir.setReturnValue((long)((hour - 6 + 24) % 24) * 1000L);
      }

   }

   @Inject(
      method = {"getDefaultClockTime"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetDefaultClockTime(CallbackInfoReturnable<Long> cir) {
      if (((Object)this) instanceof ClientLevel && BomboConfig.get().customTimeEnabled) {
         int hour = BomboConfig.get().customTimeHour;
         cir.setReturnValue((long)((hour - 6 + 24) % 24) * 1000L);
      }

   }

   @Inject(
      method = {"getRainLevel"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetRainLevel(float delta, CallbackInfoReturnable<Float> cir) {
      if (((Object)this) instanceof ClientLevel && BomboConfig.get().customWeatherEnabled) {
         int mode = BomboConfig.get().customWeatherMode;
         if (mode == 0) {
            cir.setReturnValue(0.0F);
         } else if (mode == 1 || mode == 2) {
            cir.setReturnValue(1.0F);
         }
      }
   }

   @Inject(
      method = {"getThunderLevel"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onGetThunderLevel(float delta, CallbackInfoReturnable<Float> cir) {
      if (((Object)this) instanceof ClientLevel && BomboConfig.get().customWeatherEnabled) {
         int mode = BomboConfig.get().customWeatherMode;
         if (mode == 2) {
            cir.setReturnValue(1.0F);
         } else {
            cir.setReturnValue(0.0F);
         }
      }
   }
}
