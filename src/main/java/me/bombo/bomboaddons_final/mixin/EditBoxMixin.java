package me.bombo.bomboaddons_final.mixin;

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
        if (text != null && text.startsWith("/") && me.bombo.bomboaddons_final.BomboConfig.get().ignoreCapsLock) {
            int modifiers = characterEvent.modifiers();
            boolean shift = (modifiers & 1) != 0;
            
            // "ignore caps lock, if i press shift make it caps"
            // If shift is NOT down, we ensure the command part is lowercase.
            if (!shift) {
                int spaceIdx = text.indexOf(' ');
                String commandPart = spaceIdx == -1 ? text : text.substring(0, spaceIdx);
                
                if (!commandPart.equals(commandPart.toLowerCase())) {
                    int cursor = this.getCursorPosition();
                    String newText = commandPart.toLowerCase() + (spaceIdx == -1 ? "" : text.substring(spaceIdx));
                    this.setValue(newText);
                    this.setCursorPosition(cursor);
                }
            }
        }
    }
}
