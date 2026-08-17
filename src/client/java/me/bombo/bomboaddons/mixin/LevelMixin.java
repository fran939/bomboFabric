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
}
