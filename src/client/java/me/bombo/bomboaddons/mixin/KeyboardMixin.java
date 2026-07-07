package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.KeyEvent;
import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin({ KeyboardHandler.class })
public abstract class KeyboardMixin {
    @Inject(at = { @At("HEAD") }, method = { "keyPress" }, cancellable = true)
    private void onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        String flKey = BomboConfig.get().freelookKey;
        if (flKey != null && !flKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(flKey);
            if (targetCode != -1 && event.key() == targetCode) {
                if (mc.screen == null) {
                    if (action == 1) {
                        me.bombo.bomboaddons.util.FreelookManager.toggleFreelook(true);
                        ci.cancel();
                        return;
                    } else if (action == 0) {
                        me.bombo.bomboaddons.util.FreelookManager.toggleFreelook(false);
                        ci.cancel();
                        return;
                    }
                }
            }
        }

        if (action == 1) { // 1 = Press, 0 = Release, 2 = Repeat
         int key = event.key();

         if (mc.screen instanceof ChatScreen || mc.screen instanceof AbstractSignEditScreen) {
            return;
         }

         if (me.bombo.bomboaddons.ItemListOverlay.searchBox != null && me.bombo.bomboaddons.ItemListOverlay.searchBox.isFocused()) {
            return;
         }

         if (mc.screen instanceof me.bombo.bomboaddons.BomboConfigGUI && me.bombo.bomboaddons.BomboConfigGUI.isTypingOrListening()) {
            return;
         }

         // Clipboard run keybind execution
         String cbKey = BomboConfig.get().clipboardRunKey;
         if (cbKey != null && !cbKey.isEmpty()) {
            int targetCode = ClickLogic.getKeyCode(cbKey);
            if (targetCode != -1 && key == targetCode) {
               String clip = mc.keyboardHandler.getClipboard();
               if (clip != null && !clip.trim().isEmpty()) {
                  me.bombo.bomboaddons.BomboaddonsClient.executeTracked(clip.trim());
               }
               ci.cancel();
               return;
            }
         }

         if (mc.screen instanceof me.bombo.bomboaddons.BomboConfigGUI) {
            if (key == 256 || mc.options.keyInventory.matches(event)) {
               return;
            }
            ci.cancel();
            return;
         }

         if (ClickLogic.onKeyPressed(key)) {
            ci.cancel();
            return;
         }
         
         me.bombo.bomboaddons.GardenMovement.handleKey(key);

         // Inventory Snapshot 'P' key
         if (key == 80 && mc.screen instanceof AbstractContainerScreen) {
            me.bombo.bomboaddons.InventoryManager.captureCurrentGUI();
         }

         if (mc.player != null) {
            String activeProfile = BomboConfig.get().activeProfile;
            List<BomboConfig.CommandBind> binds = new java.util.ArrayList<>();
            List<BomboConfig.CommandBind> activeBinds = null;
            List<BomboConfig.CommandBind> generalBinds = null;

            if (mc.screen != null) {
               // Profile keybinds only trigger when a GUI is open
               activeBinds = BomboConfig.get().profileBinds.get(activeProfile);
               generalBinds = BomboConfig.get().profileBinds.get("General");
            } else {
               // Keybinds category keybinds only trigger when NO GUI is open
               activeBinds = BomboConfig.get().keybindBinds.get(activeProfile);
               generalBinds = BomboConfig.get().keybindBinds.get("General");
            }
            if (activeBinds != null) binds.addAll(activeBinds);
            if (generalBinds != null && !activeProfile.equals("General")) binds.addAll(generalBinds);

            if (binds != null) {
               for (BomboConfig.CommandBind bind : binds) {
                  if (!bind.enabled)
                     continue;
                  if (bind.keyCodes.isEmpty())
                     continue;

                  int lastKey = bind.keyCodes.get(bind.keyCodes.size() - 1);
                  if (key == lastKey) {
                     boolean allMatch = true;
                     for (int i = 0; i < bind.keyCodes.size() - 1; i++) {
                        if (!ClickLogic.isCodeDown(window, mc.getWindow(), bind.keyCodes.get(i))) {
                           allMatch = false;
                           break;
                        }
                     }

                     if (allMatch) {
                        if (me.bombo.bomboaddons.ClickLogic.shouldTriggerBind(bind)) {
                           me.bombo.bomboaddons.BomboaddonsClient.executeTracked(bind.command);
                           ci.cancel();
                           return;
                        }
                     }
                  }
               }
            }
         }


      }

   }
}
