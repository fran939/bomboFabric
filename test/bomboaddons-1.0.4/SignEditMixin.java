/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.EnvType
 *  net.fabricmc.api.Environment
 *  net.minecraft.class_310
 *  net.minecraft.class_332
 *  net.minecraft.class_7743
 *  org.spongepowered.asm.mixin.Final
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.SignCalculator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_7743;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(value=EnvType.CLIENT)
@Mixin(value={class_7743.class})
public abstract class SignEditMixin {
    @Shadow
    @Final
    private String[] field_40425;
    @Shadow
    private int field_40428;

    @Inject(method={"method_25426"}, at={@At(value="TAIL")})
    private void onInit(CallbackInfo ci) {
        Bomboaddons.LOGGER.info("SignEditScreen initialized");
    }

    @Inject(method={"method_45662"}, at={@At(value="HEAD")})
    private void onDone(CallbackInfo ci) {
        if (!BomboConfig.get().signCalculator) {
            return;
        }
        for (int i = 0; i < this.field_40425.length; ++i) {
            if (this.field_40425[i] == null || this.field_40425[i].isEmpty()) continue;
            this.field_40425[i] = SignCalculator.calculate(this.field_40425[i]);
        }
    }

    @Inject(method={"method_25394"}, at={@At(value="TAIL")})
    private void onRender(class_332 guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!BomboConfig.get().signCalculator) {
            return;
        }
        class_310 mc = class_310.method_1551();
        int screenWidth = mc.method_22683().method_4486();
        int baseX = screenWidth / 2;
        int baseY = 55;
        String currentLineText = this.field_40425[this.field_40428];
        if (currentLineText != null && !currentLineText.isEmpty() && SignCalculator.isPotentialExpression(currentLineText)) {
            String color;
            Object preview = SignCalculator.getPreviewText(currentLineText);
            boolean isValid = SignCalculator.isValidExpression(currentLineText);
            String string = color = isValid ? "\u00a7a" : "\u00a7c";
            if (!isValid) {
                preview = currentLineText + " = ?";
            }
            String string2 = color + (String)preview;
            int textWidth = mc.field_1772.method_1727(string2);
            guiGraphics.method_51433(mc.field_1772, string2, baseX - textWidth / 2, baseY, -1, true);
        }
        double total = 0.0;
        boolean hasAnyExpression = false;
        for (String msg : this.field_40425) {
            if (!SignCalculator.isValidExpression(msg)) continue;
            total += SignCalculator.getResult(msg);
            hasAnyExpression = true;
        }
        if (hasAnyExpression) {
            String string = "\u00a76Total: \u00a7e" + SignCalculator.formatResultOnly(total);
            int totalWidth = mc.field_1772.method_1727(string);
            guiGraphics.method_51433(mc.field_1772, string, baseX - totalWidth / 2, baseY - 30, -1, true);
        }
    }
}
