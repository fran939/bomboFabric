package me.bombo.bomboaddons_final.mixin;

import java.util.List;
import java.util.function.Predicate;
import me.bombo.bomboaddons_final.CarnivalAuto;
import me.bombo.bomboaddons_final.SphinxMacro;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.GuiMessage.Line;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({ ChatComponent.class })
public abstract class ChatMixin {
   @Shadow
   @Final
   private List<GuiMessage> allMessages;
   @Shadow
   @Final
   private List<Line> trimmedMessages;

   @Shadow
   protected abstract void refreshTrimmedMessages();

   @Inject(method = { "addMessage(Lnet/minecraft/network/chat/Component;)V" }, at = { @At("HEAD") })
   private void onAddMessage(Component message, CallbackInfo ci) {
      if (message != null) {
         String raw = message.getString();
         SphinxMacro.onChatMessage(raw);
         CarnivalAuto.onChatMessage(raw);
      }

   }

   @Unique
   public void bombo$removeMessages(Predicate<GuiMessage> predicate) {
      boolean removed = this.allMessages.removeIf(predicate);
      if (removed) {
         this.trimmedMessages.clear();
         this.refreshTrimmedMessages();
      }

   }
}
