package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
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
      Minecraft mc = Minecraft.getInstance();
      if (me.bombo.bomboaddons.features.camera.FreecamManager.isFreecamActive()) {
         me.bombo.bomboaddons.features.camera.FreecamManager.onMouseTurn(this.accumulatedDX, this.accumulatedDY);
         if (BomboConfig.get().cameraDebug && mc.player != null && (Math.abs(this.accumulatedDX) > 0.01 || Math.abs(this.accumulatedDY) > 0.01)) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bCamDebug§8] §aFreecam §7(Yaw: §e" + String.format(java.util.Locale.ROOT, "%.1f", me.bombo.bomboaddons.features.camera.FreecamManager.getCamYaw()) + "§7, Pitch: §e" + String.format(java.util.Locale.ROOT, "%.1f", me.bombo.bomboaddons.features.camera.FreecamManager.getCamPitch()) + "§7)"));
         }
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         return;
      }
      if (FreelookManager.isFreelookActive()) {
         FreelookManager.onMouseTurn(this.accumulatedDX, this.accumulatedDY);
         if (BomboConfig.get().cameraDebug && mc.player != null && (Math.abs(this.accumulatedDX) > 0.01 || Math.abs(this.accumulatedDY) > 0.01)) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bCamDebug§8] §aFreelook §7(Yaw: §e" + String.format(java.util.Locale.ROOT, "%.1f", FreelookManager.getFreelookYaw()) + "§7, Pitch: §e" + String.format(java.util.Locale.ROOT, "%.1f", FreelookManager.getFreelookPitch()) + "§7)"));
         }
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         return;
      }
      if (mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player) {
         net.minecraft.world.entity.Entity camEnt = mc.getCameraEntity();
         double sens = mc.options.sensitivity().get();
         double f = sens * 0.6 + 0.2;
         double factor = f * f * f * 8.0 * 0.15;
         float dyaw = (float)(this.accumulatedDX * factor);
         float dpitch = (float)(this.accumulatedDY * factor);
         camEnt.setYRot(camEnt.getYRot() + dyaw);
         camEnt.setXRot(Math.max(-90.0F, Math.min(90.0F, camEnt.getXRot() + dpitch)));
         camEnt.yRotO = camEnt.getYRot();
         camEnt.xRotO = camEnt.getXRot();
         if (camEnt instanceof net.minecraft.world.entity.LivingEntity le) {
            le.setYHeadRot(camEnt.getYRot());
            le.setYBodyRot(camEnt.getYRot());
            le.yHeadRotO = camEnt.getYRot();
            le.yBodyRotO = camEnt.getYRot();
         }
         if (BomboConfig.get().cameraDebug && mc.player != null && (Math.abs(this.accumulatedDX) > 0.01 || Math.abs(this.accumulatedDY) > 0.01)) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bCamDebug§8] §7Spectating §e" + camEnt.getType().toShortString() + " §7(Yaw: §e" + String.format(java.util.Locale.ROOT, "%.1f", camEnt.getYRot()) + "§7, Pitch: §e" + String.format(java.util.Locale.ROOT, "%.1f", camEnt.getXRot()) + "§7) dx: " + String.format(java.util.Locale.ROOT, "%.2f", this.accumulatedDX) + " dy: " + String.format(java.util.Locale.ROOT, "%.2f", this.accumulatedDY)));
         }
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         return;
      }
      if (GardenMovement.shouldLockMouse()) {
         this.accumulatedDX = 0.0;
         this.accumulatedDY = 0.0;
         return;
      }
      if (BomboConfig.get().cameraDebug && mc.player != null && (Math.abs(this.accumulatedDX) > 0.01 || Math.abs(this.accumulatedDY) > 0.01)) {
         mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§8[§bCamDebug§8] §7Player (Yaw: §e" + String.format(java.util.Locale.ROOT, "%.1f", mc.player.getYRot()) + "§7, Pitch: §e" + String.format(java.util.Locale.ROOT, "%.1f", mc.player.getXRot()) + "§7) dx: " + String.format(java.util.Locale.ROOT, "%.2f", this.accumulatedDX)));
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
         if (mc.gui.screen() instanceof BomboConfigGUI || mc.gui.screen() instanceof me.bombo.bomboaddons.gui.config.BomboConfigScreen) {
            return;
         }

         if (mc.player != null && !(mc.gui.screen() instanceof ChatScreen) && !(mc.gui.screen() instanceof AbstractSignEditScreen)) {
            // Sequence triggers are handled by the flavor runtime (cheat build only).
            if (me.bombo.bomboaddons.flavor.Flavor.get().onInputTrigger(button, "Mouse Button", "Mouse " + (button + 1))) {
               ci.cancel();
               return;
            }
         }

         if (mc.gui.screen() != null && mc.player != null) {
            try {
               Screen var9 = mc.gui.screen();
               if (var9 instanceof AbstractContainerScreen) {
                  AbstractContainerScreen<?> containerScreen = (AbstractContainerScreen)var9;
                  Slot slot = me.bombo.bomboaddons.util.CustomSlotManager.getHoveredSlot(containerScreen);
                  if (KuudraPerkClicker.onMouseClicked(containerScreen, slot, button)) {
                     ci.cancel();
                     return;
                  }
               }
            } catch (Throwable t) {
               t.printStackTrace();
            }

            if (!(mc.gui.screen() instanceof ChatScreen) && !(mc.gui.screen() instanceof AbstractSignEditScreen) && ClickLogic.onKeyPressed(button)) {
               ci.cancel();
               return;
            }
         }
      }
   }
}
