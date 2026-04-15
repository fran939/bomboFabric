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

@Environment(EnvType.CLIENT)
@Mixin({class_7743.class})
public abstract class SignEditMixin {
   @Shadow
   @Final
   private String[] field_40425;
   @Shadow
   private int field_40428;

   @Inject(
      method = {"method_25426"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      Bomboaddons.LOGGER.info("SignEditScreen initialized");
   }

   @Inject(
      method = {"method_25419"},
      at = {@At("HEAD")}
   )
   private void onClose(CallbackInfo ci) {
      if (BomboConfig.get().signCalculator) {
         for(int i = 0; i < this.field_40425.length; ++i) {
            if (this.field_40425[i] != null && !this.field_40425[i].isEmpty()) {
               this.field_40425[i] = SignCalculator.calculate(this.field_40425[i]);
            }
         }

      }
   }

   @Inject(
      method = {"method_25394"},
      at = {@At("TAIL")}
   )
   private void onRender(class_332 guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (BomboConfig.get().signCalculator) {
         class_310 mc = class_310.method_1551();
         int screenWidth = mc.method_22683().method_4486();
         int baseX = screenWidth / 2;
         int baseY = 55;
         String currentLineText = this.field_40425[this.field_40428];
         String totalText;
         int totalWidth;
         if (currentLineText != null && !currentLineText.isEmpty() && SignCalculator.isPotentialExpression(currentLineText)) {
            String preview = SignCalculator.getPreviewText(currentLineText);
            boolean isValid = SignCalculator.isValidExpression(currentLineText);
            String color = isValid ? "§a" : "§c";
            if (!isValid) {
               preview = currentLineText + " = ?";
            }

            totalText = color + preview;
            totalWidth = mc.field_1772.method_1727(totalText);
            guiGraphics.method_51433(mc.field_1772, totalText, baseX - totalWidth / 2, baseY, -1, true);
         }

         double total = 0.0D;
         boolean hasAnyExpression = false;
         String[] var20 = this.field_40425;
         totalWidth = var20.length;

         for(int var16 = 0; var16 < totalWidth; ++var16) {
            String msg = var20[var16];
            if (SignCalculator.isValidExpression(msg)) {
               total += SignCalculator.getResult(msg);
               hasAnyExpression = true;
            }
         }

         if (hasAnyExpression) {
            totalText = "§6Total: §e" + SignCalculator.formatResultOnly(total);
            totalWidth = mc.field_1772.method_1727(totalText);
            guiGraphics.method_51433(mc.field_1772, totalText, baseX - totalWidth / 2, baseY - 30, -1, true);
         }

      }
   }
}
