package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ExperimentationTableHud;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({MouseHandler.class})
public class RngScrollMixin {
   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      Screen var10 = mc.screen;
      if (var10 instanceof AbstractContainerScreen<?> screen) {
         String title = screen.getTitle().getString();
         if (title.toLowerCase().contains("experimentation table rng") && vertical != (double)0.0F) {
            ExperimentationTableHud.scroll((int)vertical);
            ci.cancel();
         }
      }

   }
}
