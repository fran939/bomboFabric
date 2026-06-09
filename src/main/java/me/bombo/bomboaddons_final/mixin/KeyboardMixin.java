package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.ClickLogic;
import me.bombo.bomboaddons_final.BomboConfig;
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
   @Inject(at = { @At("HEAD") }, method = { "method_22678" }, cancellable = true)
   private void onKey(long window, int action, KeyEvent event, CallbackInfo ci) {
      if (action == 1) { // 1 = Press, 0 = Release, 2 = Repeat
         int key = event.key();

         Minecraft mc = Minecraft.getInstance();
         
         if (mc.screen instanceof ChatScreen || mc.screen instanceof AbstractSignEditScreen) {
            return;
         }

         if (mc.screen instanceof me.bombo.bomboaddons_final.BomboConfigGUI && me.bombo.bomboaddons_final.BomboConfigGUI.isTypingOrListening()) {
            return;
         }

         if (mc.screen instanceof me.bombo.bomboaddons_final.BomboConfigGUI) {
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
         
         me.bombo.bomboaddons_final.GardenMovement.handleKey(key);

         // Inventory Snapshot 'P' key
         if (key == 80 && mc.screen instanceof AbstractContainerScreen) {
            me.bombo.bomboaddons_final.InventoryManager.captureCurrentGUI();
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
                        if (me.bombo.bomboaddons_final.ClickLogic.shouldTriggerBind(bind)) {
                           me.bombo.bomboaddons_final.BomboaddonsClient.executeTracked(bind.command);
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
