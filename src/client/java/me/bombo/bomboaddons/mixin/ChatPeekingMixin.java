package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ChatPeek;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin({ ChatComponent.class })
public class ChatPeekingMixin {
   @ModifyVariable(
      method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private ChatComponent.DisplayMode onExtractRenderStateDisplayMode(ChatComponent.DisplayMode displayMode) {
      if (ChatPeek.isPeeking() && displayMode == ChatComponent.DisplayMode.BACKGROUND) {
         return ChatComponent.DisplayMode.FOREGROUND;
      }
      return displayMode;
   }

   @Redirect(method = {
         "getHeight" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;isChatFocused()Z"))
   private boolean onGetHeightFocused(ChatComponent instance) {
      return instance.isChatFocused() || ChatPeek.isPeeking();
   }
}
