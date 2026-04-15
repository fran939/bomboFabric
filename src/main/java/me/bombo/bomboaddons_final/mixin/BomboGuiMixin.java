package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.ClickLogic;
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
@Mixin({ Minecraft.class })
public class BomboGuiMixin {
   @Inject(method = { "setScreen" }, at = { @At("RETURN") })
   private void onSetScreen(Screen screen, CallbackInfo ci) {
      if (screen instanceof AbstractContainerScreen) {
         AbstractContainerScreen containerScreen = (AbstractContainerScreen) screen;
         ClickLogic.onGuiOpen(containerScreen);
      }

   }
}
