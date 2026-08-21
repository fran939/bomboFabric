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

      CustomBindsProcessor.onKeyInput(event.key(), action);

      if (action == 1) {
         int key = event.key();
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
