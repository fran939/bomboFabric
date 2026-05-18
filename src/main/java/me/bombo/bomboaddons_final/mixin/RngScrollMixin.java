package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.ExperimentationTableHud;
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
public class RngScrollMixin {
   @Inject(
      method = {"onScroll"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
         String title = screen.getTitle().getString();
         if (title.toLowerCase().contains("experimentation table rng")) {
            if (vertical != 0.0D) {
               ExperimentationTableHud.scroll((int) vertical);
               ci.cancel();
            }
         }
      }
   }
}
