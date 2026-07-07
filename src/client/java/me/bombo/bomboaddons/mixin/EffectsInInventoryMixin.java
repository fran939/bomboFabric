package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EffectsInInventory.class)
public class EffectsInInventoryMixin {
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void onCanSeeEffects(CallbackInfoReturnable<Boolean> cir) {
        if (BomboConfig.get().disableInventoryEffects || BomboConfig.get().itemListEnabled) {
            cir.setReturnValue(false);
        }
    }

}
