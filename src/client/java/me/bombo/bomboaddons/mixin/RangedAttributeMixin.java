package me.bombo.bomboaddons.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RangedAttribute.class)
public class RangedAttributeMixin {

    @ModifyVariable(
        method = "<init>(Ljava/lang/String;DDD)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 2
    )
    private static double bombo$unclampMaxValue(double maxValue) {
        if (maxValue == 1024.0) {
            return 1_000_000_000.0;
        }
        return maxValue;
    }

    @org.spongepowered.asm.mixin.injection.Inject(
        method = "sanitizeValue(D)D",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bombo$bypassClamp(double value, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Double> cir) {
        if (value > 1024.0) {
            cir.setReturnValue(value);
        }
    }
}
