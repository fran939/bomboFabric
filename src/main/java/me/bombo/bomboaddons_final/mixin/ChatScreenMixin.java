package me.bombo.bomboaddons_final.mixin;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;

    @Inject(method = "charTyped", at = @At("RETURN"))
    private void onCharTyped(CharacterEvent characterEvent, CallbackInfoReturnable<Boolean> cir) {
        if (input == null) return;
        
        String text = input.getValue();
        if (text.startsWith("/")) {
            // Check for Caps Lock (0x10) and Shift (0x01)
            // GLFW_MOD_CAPS_LOCK = 0x0010
            // GLFW_MOD_SHIFT = 0x0001
            int modifiers = characterEvent.modifiers();
            boolean capsLock = (modifiers & 0x10) != 0;
            boolean shift = (modifiers & 0x01) != 0;
            
            // "ignore caps lock, if i press shift make it caps"
            // This means if Caps Lock is ON and Shift is OFF, we want lowercase.
            if (capsLock && !shift) {
                int spaceIdx = text.indexOf(' ');
                String commandPart = spaceIdx == -1 ? text : text.substring(0, spaceIdx);
                
                if (!commandPart.equals(commandPart.toLowerCase())) {
                    int cursor = input.getCursorPosition();
                    String newText = commandPart.toLowerCase() + (spaceIdx == -1 ? "" : text.substring(spaceIdx));
                    input.setValue(newText);
                    input.setCursorPosition(cursor);
                }
            }
        }
    }
}
