package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.ChatPeek;
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
   @ModifyVariable(method = { "render" }, at = @At("HEAD"), argsOnly = true)
   private boolean onRenderFocused(boolean focused) {
      return focused || ChatPeek.isPeeking();
   }

   @Redirect(method = {
         "getHeight" }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;isChatFocused()Z"))
   private boolean onGetHeightFocused(ChatComponent instance) {
      return instance.isChatFocused() || ChatPeek.isPeeking();
   }
}
