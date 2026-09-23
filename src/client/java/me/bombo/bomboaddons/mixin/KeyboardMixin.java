package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboConfigGUI;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.GardenMovement;
import me.bombo.bomboaddons.InventoryManager;
import me.bombo.bomboaddons.ItemListOverlay;
import me.bombo.bomboaddons.util.FreelookManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({KeyboardHandler.class})
public abstract class KeyboardMixin {
   @Inject(
      at = {@At("HEAD")},
      method = {"keyPress"},
      cancellable = true
   )
   private void onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
      if (event == null || event.key() <= 0 || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();

      // While the in-game terminal is open, every Bombo keybind is suppressed (ESC is
      // handled by the screen itself): typing `ls` in the terminal must never trigger a
      // sequence, a toggle or a command bind.
      if (mc.gui.screen() instanceof me.bombo.bomboaddons.gui.CmdScreen) {
         return;
      }

      String flKey = BomboConfig.get().freelookKey;
      if (flKey != null && !flKey.isEmpty()) {
         int targetCode = ClickLogic.getKeyCode(flKey);
         if (targetCode != -1 && event.key() == targetCode && mc.gui.screen() == null) {
            if (BomboConfig.get().freelookToggle) {
               if (action == 1) {
                  FreelookManager.toggleFreelook(!FreelookManager.isFreelookActive());
                  ci.cancel();
                  return;
               }
            } else {
               if (action == 1) {
                  FreelookManager.toggleFreelook(true);
                  ci.cancel();
                  return;
               }

               if (action == 0) {
                  FreelookManager.toggleFreelook(false);
                  ci.cancel();
                  return;
               }
            }
         }
      }

      String fcKey = BomboConfig.get().freecamKey;
      if (fcKey != null && !fcKey.isEmpty()) {
         int targetCode = ClickLogic.getKeyCode(fcKey);
         if (targetCode != -1 && event.key() == targetCode && mc.gui.screen() == null) {
            if (BomboConfig.get().freecamToggle) {
               if (action == 1) {
                  me.bombo.bomboaddons.features.camera.FreecamManager.toggleFreecam();
                  ci.cancel();
                  return;
               }
            } else {
               if (action == 1) {
                  me.bombo.bomboaddons.features.camera.FreecamManager.toggleFreecam(true);
                  ci.cancel();
                  return;
               }
               if (action == 0) {
                  me.bombo.bomboaddons.features.camera.FreecamManager.toggleFreecam(false);
                  ci.cancel();
                  return;
               }
            }
         }
      }

      if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F5 && action == 1 && mc.gui.screen() == null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s != null && s.cameraSettingsEnabled && s.disableFrontCamera) {
            if (mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK) {
               mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
               ci.cancel();
               return;
            }
         }
      }

      if (me.bombo.bomboaddons.features.AutoAhSell.isRunning()) {
         if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE_ACCENT) {
            me.bombo.bomboaddons.features.AutoAhSell.stop();
         }
      }

      CustomBindsProcessor.onKeyInput(event.key(), action);

      if (action == 1) {
         int key = event.key();
         boolean isCtrl = event.hasControlDown() || Minecraft.getInstance().hasControlDown() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), 341) || com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), 345);
         if (key == 86 && isCtrl && mc.gui.screen() instanceof ChatScreen chatScreen) {
            if (me.bombo.bomboaddons.util.ClipboardImageUploader.hasClipboardImage()) {
               net.minecraft.client.gui.components.EditBox input = null;
               try {
                  for (java.lang.reflect.Field f : ChatScreen.class.getDeclaredFields()) {
                     if (net.minecraft.client.gui.components.EditBox.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        input = (net.minecraft.client.gui.components.EditBox) f.get(chatScreen);
                        break;
                     }
                  }
               } catch (Throwable ignored) {}
               if (input != null && me.bombo.bomboaddons.util.ClipboardImageUploader.tryUploadClipboardImage(input)) {
                  ci.cancel();
                  return;
               }
            }
         }

         if (BomboConfig.get().debugKeys) {
            String keyStr = CustomBindsProcessor.getKeyNameForGlfwCode(key);
            Bomboaddons.sendMessage("§b[KeyDebug] KeyPress: " + keyStr + " (code: " + key + ", screen: " + (mc.gui.screen() == null ? "None" : mc.gui.screen().getClass().getSimpleName()) + ", modifierHeld: " + CustomBindsProcessor.isAnyGuiModifierHeld() + ")");
         }
         if (mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof AbstractSignEditScreen) {
            return;
         }

         if (ItemListOverlay.searchBox != null && ItemListOverlay.searchBox.isFocused()) {
            return;
         }

         if (mc.gui.screen() instanceof BomboConfigGUI && BomboConfigGUI.isTypingOrListening()) {
            return;
         }

         if (mc.gui.screen() != null && !(mc.gui.screen() instanceof BomboConfigGUI)) {
            if (BomboConfig.get().preventSlotSwapOnGuiKeybind) {
               if (CustomBindsProcessor.hasHandledGuiKeyRecently(key)) {
                  if (BomboConfig.get().debugKeys) {
                     Bomboaddons.sendMessage("§e[KeyDebug] KeyboardHandler canceled event for handled GUI key: " + key);
                  }
                  ci.cancel();
                  return;
               }
            }
         }

         String cbKey = BomboConfig.get().clipboardRunKey;
         if (cbKey != null && !cbKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(cbKey);
            if (targetCode != -1 && key == targetCode) {
               String clip = mc.keyboardHandler.getClipboard();
               if (clip != null && !clip.trim().isEmpty()) {
                  String trimmed = clip.trim();
                  if (trimmed.length() > 256) {
                     Bomboaddons.sendMessage("§c[Bombo] Clipboard text is too long to send! (" + trimmed.length() + " > 256)");
                  } else {
                     BomboaddonsClient.executeTracked(trimmed);
                  }
               }

               ci.cancel();
               return;
            }
         }

         String lastCmdKey = BomboConfig.get().clipboardRunLastCommandKey;
         if (lastCmdKey != null && !lastCmdKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(lastCmdKey);
            if (targetCode != -1 && key == targetCode) {
               String cmd = me.bombo.bomboaddons.util.ClipboardCommandManager.getLastCopiedCommand();
               if (cmd != null && !cmd.trim().isEmpty()) {
                  BomboaddonsClient.executeTracked(cmd.trim());
               } else {
                  Bomboaddons.sendMessage("§8[§3Bombo§8] §cNo slash command found in clipboard history!");
               }
               ci.cancel();
               return;
            }
         }

         String crouchKey = BomboConfig.get().vanillaToggleCrouchKey;
         if (crouchKey != null && !crouchKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(crouchKey);
            if (targetCode != -1 && key == targetCode) {
               boolean curToggle = mc.options.toggleCrouch().get();
               boolean newToggle = !curToggle;
               mc.options.toggleCrouch().set(newToggle);
               mc.options.save();
               String modeStr = newToggle ? "§aTOGGLE" : "§eHOLD";
               Bomboaddons.sendMessage("§8[§3Bombo§8] §7Vanilla Sneak/Crouch set to: " + modeStr);
               ci.cancel();
               return;
            }
         }

         String attackKey = BomboConfig.get().vanillaToggleAttackKey;
         if (attackKey != null && !attackKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(attackKey);
            if (targetCode != -1 && key == targetCode) {
               boolean curToggle = mc.options.toggleAttack().get();
               boolean newToggle = !curToggle;
               mc.options.toggleAttack().set(newToggle);
               mc.options.save();
               String modeStr = newToggle ? "§aTOGGLE" : "§eHOLD";
               Bomboaddons.sendMessage("§8[§3Bombo§8] §7Vanilla Attack/Break set to: " + modeStr);
               ci.cancel();
               return;
            }
         }

         String useKey = BomboConfig.get().vanillaToggleUseKey;
         if (useKey != null && !useKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(useKey);
            if (targetCode != -1 && key == targetCode) {
               boolean curToggle = mc.options.toggleUse().get();
               boolean newToggle = !curToggle;
               mc.options.toggleUse().set(newToggle);
               mc.options.save();
               String modeStr = newToggle ? "§aTOGGLE" : "§eHOLD";
               Bomboaddons.sendMessage("§8[§3Bombo§8] §7Vanilla Use/Place set to: " + modeStr);
               ci.cancel();
               return;
            }
         }

         if (mc.gui.screen() instanceof BomboConfigGUI || mc.gui.screen() instanceof me.bombo.bomboaddons.gui.config.BomboOrderScreen || mc.gui.screen() instanceof me.bombo.bomboaddons.gui.config.BomboConfigScreen) {
            return;
         }

         String chatHistKey = BomboConfig.get().chatHistoryKey;
         if (chatHistKey != null && !chatHistKey.isEmpty() && mc.gui.screen() == null) {
            int histCode = ClickLogic.getKeyCode(chatHistKey);
            if (histCode != -1 && key == histCode) {
               BomboaddonsClient.openChatHistory(mc, me.bombo.bomboaddons.features.chat.ChatHistoryScreen.FilterTab.ALL);
               ci.cancel();
               return;
            }
         }

         // Sequence triggers are handled by the flavor runtime (cheat build only).
         if (me.bombo.bomboaddons.flavor.Flavor.get().onInputTrigger(key, "Keybind",
               me.bombo.bomboaddons.features.auto.AutoSequenceManager.describeKeyCode(key))) {
            ci.cancel();
            return;
         }

         if (ClickLogic.onKeyPressed(key)) {
            ci.cancel();
            return;
         }

         GardenMovement.handleKey(key);
         int saveInvKey = ClickLogic.getKeyCode(BomboConfig.get().saveInventoryKey);
         if (saveInvKey != -1 && key == saveInvKey && mc.gui.screen() instanceof AbstractContainerScreen) {
            InventoryManager.captureCurrentGUI();
         }
      }
   }
}
