package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (input != null) {
            String text = input.getValue();
            if (text != null && text.startsWith("/cb ")) {
                String formatStr = text.substring(4);
                ChatScreen screen = (ChatScreen) (Object) this;
                var font = Minecraft.getInstance().font;
                String previewText = "§ePreview: §r" + formatStr.replace('&', '§');
                int textW = font.width(previewText);
                int y = screen.height - 26;
                g.fill(2, y - 2, 2 + textW + 4, y + 10, 0x99000000);
                g.drawString(font, previewText, 4, y, 0xFFFFFFFF, true);
            }
        }
    }
}
