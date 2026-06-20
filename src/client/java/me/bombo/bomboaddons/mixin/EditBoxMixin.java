package me.bombo.bomboaddons.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@Mixin(EditBox.class)
public abstract class EditBoxMixin {
    @Shadow public abstract String getValue();
    @Shadow public abstract void setValue(String string);
    @Shadow public abstract int getCursorPosition();
    @Shadow public abstract void setCursorPosition(int i);

    @Inject(method = "charTyped", at = @At("RETURN"))
    private void onCharTyped(CharacterEvent characterEvent, CallbackInfoReturnable<Boolean> cir) {
        // If the character wasn't consumed (not active, not focused, or not allowed), return
        if (!cir.getReturnValue()) return;
        
        String text = this.getValue();
        if (text != null && me.bombo.bomboaddons.BomboConfig.get().ignoreCapsLock) {
            boolean shift = Minecraft.getInstance().hasShiftDown();
            
            // If shift is NOT down, we ensure the text is lowercase.
            if (!shift) {
                if (!text.equals(text.toLowerCase())) {
                    int cursor = this.getCursorPosition();
                    this.setValue(text.toLowerCase());
                    this.setCursorPosition(cursor);
                }
            }
        }
    }

    @Inject(method = "setValue", at = @At("HEAD"))
    private void onSetValue(String value, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (value != null) {
            String lower = value.toLowerCase();
            if (lower.startsWith("/lb") || lower.startsWith("/lfc")) {
                if (Minecraft.getInstance().gui.screen() instanceof ChatScreen) {
                    me.bombo.bomboaddons.LF.preFetchSelf();
                }
            }
        }
    }
}
