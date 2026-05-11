package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.DiceHud;
import me.bombo.bomboaddons_final.DiceTracker;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (Minecraft.getInstance().level == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s.diceTracker) {
            int w = 100;
            int h = 35;
            boolean hovered = mouseX >= s.diceHudX && mouseX <= s.diceHudX + w &&
                              mouseY >= s.diceHudY && mouseY <= s.diceHudY + h;
            
            if (DiceTracker.shouldShowHud() || hovered) {
                DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, hovered);
            }
        }
    }
}
