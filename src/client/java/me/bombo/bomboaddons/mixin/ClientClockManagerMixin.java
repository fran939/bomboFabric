package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public class ClientClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void onGetTotalTicks(Holder<?> holder, CallbackInfoReturnable<Long> cir) {
        if (BomboConfig.get().customTimeEnabled) {
            int hour = BomboConfig.get().customTimeHour;
            long ticks = (long) ((hour - 6 + 24) % 24) * 1000L;
            cir.setReturnValue(ticks);
        }
    }
}
