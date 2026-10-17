package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ClickLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Minecraft.class})
public class BomboGuiMixin {
   @Inject(
      method = {"setScreenAndShow"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetScreenHead(Screen screen, CallbackInfo ci) {
      if (screen instanceof AbstractContainerScreen<?> containerScreen) {
         String title = containerScreen.getTitle().getString();
         if (me.bombo.bomboaddons.WardrobeHelper.shouldSuppressScreenOpen(title)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"setScreenAndShow"},
      at = {@At("RETURN")}
   )
   private void onSetScreen(Screen screen, CallbackInfo ci) {
      if (screen instanceof AbstractContainerScreen containerScreen) {
         ClickLogic.onGuiOpen(containerScreen);
      }

   }
}
