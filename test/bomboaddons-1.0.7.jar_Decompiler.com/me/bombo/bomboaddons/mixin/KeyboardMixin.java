package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ClickLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_11908;
import net.minecraft.class_309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({class_309.class})
public abstract class KeyboardMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"method_1466"}
   )
   private void onKey(long window, int action, class_11908 event, CallbackInfo ci) {
      if (action == 1) {
         ClickLogic.onKeyPressed(event.comp_4795());
      }

   }
}
