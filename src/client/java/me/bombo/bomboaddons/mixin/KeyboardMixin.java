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
      Minecraft mc = Minecraft.getInstance();
      String flKey = BomboConfig.get().freelookKey;
      if (flKey != null && !flKey.isEmpty()) {
         int targetCode = ClickLogic.getKeyCode(flKey);
         if (targetCode != -1 && event.key() == targetCode && mc.screen == null) {
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

      if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F5 && action == 1 && mc.screen == null) {
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
         if (key == 86 && isCtrl && mc.screen instanceof ChatScreen chatScreen) {
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
            Bomboaddons.sendMessage("§b[KeyDebug] KeyPress: " + keyStr + " (code: " + key + ", screen: " + (mc.screen == null ? "None" : mc.screen.getClass().getSimpleName()) + ", modifierHeld: " + CustomBindsProcessor.isAnyGuiModifierHeld() + ")");
         }
         if (mc.screen instanceof ChatScreen || mc.screen instanceof AbstractSignEditScreen) {
            return;
         }

         if (ItemListOverlay.searchBox != null && ItemListOverlay.searchBox.isFocused()) {
            return;
         }

         if (mc.screen instanceof BomboConfigGUI && BomboConfigGUI.isTypingOrListening()) {
            return;
         }

         if (mc.screen != null && !(mc.screen instanceof BomboConfigGUI)) {
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

         if (mc.screen instanceof BomboConfigGUI) {
            return;
         }

         if (ClickLogic.onKeyPressed(key)) {
            ci.cancel();
            return;
         }

         GardenMovement.handleKey(key);
         int saveInvKey = ClickLogic.getKeyCode(BomboConfig.get().saveInventoryKey);
         if (saveInvKey != -1 && key == saveInvKey && mc.screen instanceof AbstractContainerScreen) {
            InventoryManager.captureCurrentGUI();
         }
      }
   }
}
