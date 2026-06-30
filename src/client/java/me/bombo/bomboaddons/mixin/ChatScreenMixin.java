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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;

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
            if (!message.isEmpty() && message.contains("&")) {
                String previewText = "§7Preview: " + message.replace('&', '§');
                int x = this.input.getX() + 2;
                int y = this.input.getY() - 14;

                int textWidth = this.font.width(previewText);
                g.fill(x - 2, y - 2, x + textWidth + 2, y + 10, 0xAA000000);

                g.text(this.font, previewText, x, y, 0xFFFFFFFF, true);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
        if (!me.bombo.bomboaddons.BomboConfig.get().copyChat) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.getChat() == null) return;
        
        if (mc.gui.getChat() instanceof me.bombo.bomboaddons.util.IChatComponent) {
            me.bombo.bomboaddons.util.IChatComponent chatAccessor = (me.bombo.bomboaddons.util.IChatComponent) mc.gui.getChat();
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
            
            if (button == 1) { // Right Click
                net.minecraft.client.multiplayer.chat.GuiMessage.Line line = chatAccessor.bombo$getLineAt(mouseX, mouseY);
                if (line != null) {
                    boolean isControl = Minecraft.getInstance().hasControlDown();
                    
                    boolean copyPlain = !isControl;
                    boolean copyFormatted = isControl;
                    
                    if (copyPlain || copyFormatted) {
                        String copiedText;
                        if (copyFormatted) {
                            copiedText = me.bombo.bomboaddons.util.IChatComponent.getLineWithFormatting(line.content(), '&');
                        } else {
                            copiedText = me.bombo.bomboaddons.util.IChatComponent.getLinePlainText(line.content());
                        }
                        
                        if (copiedText != null && !copiedText.isEmpty()) {
                            mc.keyboardHandler.setClipboard(copiedText);
                            mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCopied chat: §r" + (copyFormatted ? copiedText.replace('&', '§') : copiedText)));
                            cir.setReturnValue(true);
                        }
                    }
                }
            }
        }
    }
}
