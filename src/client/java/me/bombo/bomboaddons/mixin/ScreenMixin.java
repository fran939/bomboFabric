package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.DiceHud;
import me.bombo.bomboaddons.DiceTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s.diceTracker) {
            int w = (int) (260 * s.diceHudScale);
            int h = (int) (52 * s.diceHudScale);
            boolean hovered = mouseX >= s.diceHudX && mouseX <= s.diceHudX + w &&
                              mouseY >= s.diceHudY && mouseY <= s.diceHudY + h;
            
            if (DiceTracker.shouldShowHud()) {
                DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, hovered);
            }
        }
    }
}
