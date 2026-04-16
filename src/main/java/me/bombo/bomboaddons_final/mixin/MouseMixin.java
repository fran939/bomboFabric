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
import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Inject(at = @At("HEAD"), method = "method_22686", cancellable = true)
    private void onMouse(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == 1) { // GLFW_PRESS
            int button = info.button();
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null && mc.player != null) {
                String activeProfile = BomboConfig.get().activeProfile;
                List<BomboConfig.CommandBind> binds = BomboConfig.get().profileBinds.get(activeProfile);

                if (binds != null) {
                    for (BomboConfig.CommandBind bind : binds) {
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
                                String cmd = bind.command.startsWith("/") ? bind.command.substring(1) : bind.command;
                                mc.player.connection.sendCommand(cmd);
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
