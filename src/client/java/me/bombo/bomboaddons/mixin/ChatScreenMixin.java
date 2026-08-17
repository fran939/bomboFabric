package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.DiceHud;
import me.bombo.bomboaddons.DiceTracker;
import me.bombo.bomboaddons.util.IChatComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ChatScreen.class})
public abstract class ChatScreenMixin extends Screen {
   @Shadow
   protected EditBox input;

   protected ChatScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (this.input != null) {
         String text = this.input.getValue();
         if (text != null && text.toLowerCase().startsWith("/bc ")) {
            String message = text.substring(4);
            if (!message.isEmpty() && message.contains("&")) {
               String previewText = "§7Preview: " + message.replace('&', '§');
               int x = this.input.getX() + 2;
               int y = this.input.getY() - 14;
               int textWidth = this.font.width(previewText);
               g.fill(x - 2, y - 2, x + textWidth + 2, y + 10, -1442840576);
               g.text(this.font, previewText, x, y, -1, true);
            }
         }
      }

      me.bombo.bomboaddons.util.ChatImagePreview.renderPreview(g, this.minecraft, mouseX, mouseY);
   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      double mouseX = event.x();
      double mouseY = event.y();
      System.out.println("[Bombo Chat Click] button=" + event.button() + " mouseX=" + mouseX + " mouseY=" + mouseY + " hudX=" + s.diceHudX + " hudY=" + s.diceHudY + " scale=" + s.diceHudScale);
      if (s.diceTracker && DiceTracker.shouldShowHud()) {
         float scale = s.diceHudScale;
         int w = (int)(260.0F * scale);
         int h = (int)(52.0F * scale);
         if (mouseX >= (double)s.diceHudX && mouseX <= (double)(s.diceHudX + w) && mouseY >= (double)s.diceHudY && mouseY <= (double)(s.diceHudY + h)) {
            int button = event.button();
            if (button == 0) {
               if ("Current".equalsIgnoreCase(s.diceDisplayMode)) {
                  s.diceDisplayMode = "Lifetime";
               } else {
                  s.diceDisplayMode = "Current";
               }

               BomboConfig.save();
            } else if (button == 1) {
               DiceHud.showStatsInChat();
            }

            cir.setReturnValue(true);
            return;
         }
      }

      if (BomboConfig.get().copyChat) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.gui != null && mc.gui.getChat() != null) {
            if (mc.gui.getChat() instanceof IChatComponent) {
               IChatComponent chatAccessor = (IChatComponent)mc.gui.getChat();
               int button = event.button();
               if (button == 1) {
                  GuiMessage.Line line = chatAccessor.bombo$getLineAt(mouseX, mouseY);
                  if (line != null) {
                     boolean isControl = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 341) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 345);
                     boolean isAlt = InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 342) || InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 346);
                     String copiedText = "";
                     if (isControl) {
                        if (isAlt) {
                           copiedText = IChatComponent.getLineWithFormatting(line.content(), '&');
                        } else {
                           copiedText = IChatComponent.getLinePlainText(line.content());
                        }
                     } else {
                        List<GuiMessage.Line> allLines = chatAccessor.bombo$getFullMessageLines(line);
                        StringBuilder sb = new StringBuilder();

                        for(GuiMessage.Line l : allLines) {
                           if (sb.length() > 0) {
                              sb.append(" ");
                           }

                           if (isAlt) {
                              sb.append(IChatComponent.getLineWithFormatting(l.content(), '&'));
                           } else {
                              sb.append(IChatComponent.getLinePlainText(l.content()));
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
   }
}
