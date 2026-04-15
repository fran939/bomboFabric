package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ChatPeek;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_312;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({class_312.class})
public class ChatPeekScrollMixin {
   @Inject(
      method = {"method_1598"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      if (ChatPeek.isPeeking()) {
         ci.cancel();
         if (vertical != 0.0D) {
            class_310.method_1551().field_1705.method_1743().method_1802((int)vertical);
         }
      }

   }
}
