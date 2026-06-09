package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.ClickLogic;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void onTurnPlayer(CallbackInfo ci) {
        if (me.bombo.bomboaddons_final.GardenMovement.shouldLockMouse()) {
            this.accumulatedDX = 0;
            this.accumulatedDY = 0;
        }
    }

    @Inject(at = @At("HEAD"), method = "method_22686", cancellable = true)
    private void onMouse(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1) { // GLFW_PRESS
            int button = info.button();
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof me.bombo.bomboaddons_final.BomboConfigGUI) {
                return;
            }
             if (mc.screen != null && mc.player != null) {
                try {
                    if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> containerScreen) {
                        net.minecraft.world.inventory.Slot slot = ((me.bombo.bomboaddons_final.mixin.AbstractContainerScreenAccessor) containerScreen).getHoveredSlot();
                        if (me.bombo.bomboaddons_final.KuudraPerkClicker.onMouseClicked(containerScreen, slot, button)) {
                            ci.cancel();
                            return;
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                if (!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen || mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen)) {
                    if (ClickLogic.onKeyPressed(button)) {
                        ci.cancel();
                        return;
                    }
                }
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
                        if (button == lastKey) {
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
