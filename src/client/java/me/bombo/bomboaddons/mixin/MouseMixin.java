package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfigGUI;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.GardenMovement;
import me.bombo.bomboaddons.KuudraPerkClicker;
import me.bombo.bomboaddons.util.FreelookManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({MouseHandler.class})
public abstract class MouseMixin {
   @Shadow
   private double accumulatedDX;
   @Shadow
   private double accumulatedDY;

   @Inject(
      method = {"turnPlayer"},
      at = {@At("HEAD")}
   )
   private void onTurnPlayer(CallbackInfo ci) {
      if (FreelookManager.isFreelookActive()) {
         FreelookManager.onMouseTurn(this.accumulatedDX, this.accumulatedDY);
         this.accumulatedDX = (double)0.0F;
         this.accumulatedDY = (double)0.0F;
      } else {
         if (GardenMovement.shouldLockMouse()) {
            this.accumulatedDX = (double)0.0F;
            this.accumulatedDY = (double)0.0F;
         }

      }
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"onButton"},
      cancellable = true
   )
   private void onMouse(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
      int button = info.button();
      Minecraft mc = Minecraft.getInstance();
      CustomBindsProcessor.onMouseInput(button, action);
      if (action == 1) {
         if (mc.screen instanceof BomboConfigGUI) {
            return;
         }

         if (mc.screen != null && mc.player != null) {
            try {
               Screen var9 = mc.screen;
               if (var9 instanceof AbstractContainerScreen) {
                  AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen)var9;
                  Slot slot = ((AbstractContainerScreenAccessor)containerScreen).getHoveredSlot();
                  if (KuudraPerkClicker.onMouseClicked(containerScreen, slot, button)) {
                     ci.cancel();
                     return;
                  }
               }
            } catch (Throwable t) {
               t.printStackTrace();
            }

            if (!(mc.screen instanceof ChatScreen) && !(mc.screen instanceof AbstractSignEditScreen) && ClickLogic.onKeyPressed(button)) {
               ci.cancel();
               return;
            }
         }
      }
   }
}
