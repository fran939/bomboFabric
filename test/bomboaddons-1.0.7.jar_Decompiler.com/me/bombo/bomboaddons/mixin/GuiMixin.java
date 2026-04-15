package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ClickLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_437;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({class_310.class})
public class GuiMixin {
   @Inject(
      method = {"method_1507"},
      at = {@At("RETURN")}
   )
   private void onSetScreen(class_437 screen, CallbackInfo ci) {
      if (screen instanceof class_465) {
         class_465 containerScreen = (class_465)screen;
         ClickLogic.onGuiOpen(containerScreen);
      }

   }
}
