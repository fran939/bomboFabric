package me.bombo.bomboaddons.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    @Shadow protected EditBox input;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.input == null) return;
        String text = this.input.getValue();
        if (text != null && text.toLowerCase().startsWith("/bc ")) {
            String message = text.substring(4);
            if (!message.isEmpty()) {
                String previewText = "§7Preview: " + message.replace('&', '§');
                int x = this.input.getX() + 2;
                int y = this.input.getY() - 14;

                int textWidth = this.font.width(previewText);
                g.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0xAA000000);

                g.text(this.font, previewText, x, y, 0xFFFFFFFF, true);
            }
        }
    }
}
