package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.SlotHighlight;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        me.bombo.bomboaddons_final.ClickLogic.onGuiOpen((AbstractContainerScreen)(Object)this);
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void onRenderSlot(GuiGraphics guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
        int color = 0;
        
        // Only highlight slots targeted during search navigation (temporary session results)
        if (SlotHighlight.isTargetSlot(slot.index)) {
            color = SlotHighlight.getCurrentColor();
        } 

        if (color != 0) {
            // Guarantee visibility with an alpha floor (0x80 = 50% opacity minimum)
            if ((color & 0xFF000000) == 0) color |= 0x80000000;
            
            guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean handled,
            CallbackInfoReturnable<Boolean> cir) {
        if (hoveredSlot != null && (SlotHighlight.isTargetSlot(hoveredSlot.index) || hoveredSlot.index == 45 || hoveredSlot.index == 53)) {
            return; // Don't clear if we clicked a target slot or navigation arrows.
        }
        SlotHighlight.clearTargetSlot();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onGuiClose(CallbackInfo ci) {
        SlotHighlight.clearTargetSlot();
    }
}
