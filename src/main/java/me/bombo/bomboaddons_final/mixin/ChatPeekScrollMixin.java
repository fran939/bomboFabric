package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.ChatPeek;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({MouseHandler.class})
public class ChatPeekScrollMixin {
   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (ChatPeek.isPeeking()) {
         ci.cancel();
         if (vertical != 0.0D) {
            Minecraft.getInstance().gui.getChat().scrollChat((int)vertical);
         }
      }

   }
}
