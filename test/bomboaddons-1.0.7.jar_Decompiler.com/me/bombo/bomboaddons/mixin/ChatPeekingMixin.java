package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ChatPeek;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_338;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Environment(EnvType.CLIENT)
@Mixin({class_338.class})
public class ChatPeekingMixin {
   @ModifyVariable(
      method = {"method_1805"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private boolean onRenderFocused(boolean focused) {
      return focused || ChatPeek.isPeeking();
   }

   @Redirect(
      method = {"method_1810"},
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/class_338;method_1819()Z"
)
   )
   private boolean onGetHeightFocused(class_338 instance) {
      return instance.method_1819() || ChatPeek.isPeeking();
   }
}
