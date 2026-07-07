package me.bombo.bomboaddons.mixin;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public class SlotMixin {
    @Shadow public int index;

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private void onGetItem(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack override = me.bombo.bomboaddons.util.CustomSlotManager.getOverride((Slot)(Object)this);
        if (override != null) {
            cir.setReturnValue(override);
        }
    }
    
    @Inject(method = "hasItem", at = @At("HEAD"), cancellable = true)
    private void onHasItem(CallbackInfoReturnable<Boolean> cir) {
        ItemStack override = me.bombo.bomboaddons.util.CustomSlotManager.getOverride((Slot)(Object)this);
        if (override != null) {
            cir.setReturnValue(!override.isEmpty());
        }
    }
}
