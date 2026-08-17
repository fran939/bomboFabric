package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.CommandTracker;
import me.bombo.bomboaddons.CustomBindsProcessor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public class CommandMixin {
   @Inject(
      method = {"sendCommand"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendCommand(String command, CallbackInfo ci) {
      CommandTracker.onCommandSent(command);
      if (CustomBindsProcessor.processAlias(command)) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"sendChat"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChat(String message, CallbackInfo ci) {
      if (CustomBindsProcessor.processAlias(message)) {
         ci.cancel();
      }

   }
}
