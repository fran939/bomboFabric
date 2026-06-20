package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.kuudra.pearls.Pearls;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Hud.class)
public class GuiMixin {
    @Inject(method = "setTitle", at = @At("HEAD"))
    private void onSetTitle(Component component, CallbackInfo ci) {
        if (component != null) {
            Pearls.onTitleReceived(component.getString());
        }
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"))
    private void onSetSubtitle(Component component, CallbackInfo ci) {
        if (component != null) {
            Pearls.onTitleReceived(component.getString());
        }
    }

    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void onSetOverlayMessage(Component component, boolean animateColor, CallbackInfo ci) {
        if (component != null) {
            Pearls.onTitleReceived(component.getString());
        }
    }
}
