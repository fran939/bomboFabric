package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.util.SpectatorCamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Hud.class)
public class HudMixin {

    @Shadow private ItemStack lastToolHighlight;
    @Shadow private int toolHighlightTimer;

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void bombo$fixSpectatorCameraPlayer(CallbackInfoReturnable<Player> cir) {
        if (SpectatorCamManager.isActive()) {
            cir.setReturnValue(Minecraft.getInstance().player);
        }
    }

    @Inject(method = "tick(Z)V", at = @At("TAIL"))
    private void bombo$syncSpectatedToolHighlight(boolean paused, CallbackInfo ci) {
        if (SpectatorCamManager.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getCameraEntity() instanceof Player other) {
                ItemStack otherItem = other.getMainHandItem();
                if (!ItemStack.matches(this.lastToolHighlight, otherItem)) {
                    this.lastToolHighlight = otherItem.copy();
                    this.toolHighlightTimer = 40;
                }
            }
        }
    }
}
