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
        me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
        double mouseX = event.x();
        double mouseY = event.y();
        System.out.println("[Bombo Chat Click] button=" + event.button() + " mouseX=" + mouseX + " mouseY=" + mouseY + " hudX=" + s.diceHudX + " hudY=" + s.diceHudY + " scale=" + s.diceHudScale);
        if (s.diceTracker && me.bombo.bomboaddons.DiceTracker.shouldShowHud()) {
            float scale = s.diceHudScale;
            int w = (int) (260 * scale);
            int h = (int) (52 * scale);
            if (mouseX >= s.diceHudX && mouseX <= s.diceHudX + w &&
                mouseY >= s.diceHudY && mouseY <= s.diceHudY + h) {
                int button = event.button();
                if (button == 0) { // Left Click -> Toggle Display Mode
                    if ("Current".equalsIgnoreCase(s.diceDisplayMode)) {
                        s.diceDisplayMode = "Lifetime";
                    } else {
                        s.diceDisplayMode = "Current";
                    }
                    me.bombo.bomboaddons.BomboConfig.save();
                } else if (button == 1) { // Right Click -> Print Stats
                    me.bombo.bomboaddons.DiceHud.showStatsInChat();
                }
                cir.setReturnValue(true);
                return;
            }
        }

        if (!me.bombo.bomboaddons.BomboConfig.get().copyChat) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.getChat() == null) return;
        
        if (mc.gui.getChat() instanceof me.bombo.bomboaddons.util.IChatComponent) {
            me.bombo.bomboaddons.util.IChatComponent chatAccessor = (me.bombo.bomboaddons.util.IChatComponent) mc.gui.getChat();
            int button = event.button();
            
            if (button == 1) { // Right Click
                net.minecraft.client.multiplayer.chat.GuiMessage.Line line = chatAccessor.bombo$getLineAt(mouseX, mouseY);
                if (line != null) {
                    boolean isControl = (com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL));
                    boolean isAlt = (com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT));

                    String copiedText = "";
                    if (isControl) {
                        if (isAlt) {
                            copiedText = me.bombo.bomboaddons.util.IChatComponent.getLineWithFormatting(line.content(), '&');
                        } else {
                            copiedText = me.bombo.bomboaddons.util.IChatComponent.getLinePlainText(line.content());
                        }
                    } else {
                        java.util.List<net.minecraft.client.multiplayer.chat.GuiMessage.Line> allLines = chatAccessor.bombo$getFullMessageLines(line);
                        StringBuilder sb = new StringBuilder();
                        for (net.minecraft.client.multiplayer.chat.GuiMessage.Line l : allLines) {
                            if (sb.length() > 0) sb.append(" ");
                            if (isAlt) {
                                sb.append(me.bombo.bomboaddons.util.IChatComponent.getLineWithFormatting(l.content(), '&'));
                            } else {
                                sb.append(me.bombo.bomboaddons.util.IChatComponent.getLinePlainText(l.content()));
                            }
                        }
                        copiedText = sb.toString();
                    }

                    if (copiedText != null && !copiedText.isEmpty()) {
                        mc.keyboardHandler.setClipboard(copiedText);
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§8[§bBomboAddons§8] §aCopied chat: §r" + copiedText.replace('&', '§')));
                        cir.setReturnValue(true);
                    }
                }
                }
            }
        }
}
