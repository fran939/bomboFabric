package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SignCalculator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({AbstractSignEditScreen.class})
public abstract class SignEditMixin {
   @Shadow
   @Final
   private String[] messages;
   @Shadow
   private int line;

   @Inject(
      method = {"onDone", "onClose"},
      at = {@At("HEAD")},
      require = 0
   )
   private void onDoneOrClose(CallbackInfo ci) {
      if (BomboConfig.get().signCalculator) {
         for(int i = 0; i < this.messages.length; ++i) {
            if (this.messages[i] != null && !this.messages[i].isEmpty()) {
               this.messages[i] = SignCalculator.calculate(this.messages[i]);
            }
         }
      }

   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void onRender(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (BomboConfig.get().signCalculator) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         BomboConfig.Settings s = BomboConfig.get();
         int baseX = (s.signCalculatorX >= 0) ? s.signCalculatorX : (screenWidth / 2);
         int baseY = s.signCalculatorY;

         String currentLineText = this.messages[this.line];
         double currentVal = (currentLineText != null && !currentLineText.trim().isEmpty()) ? SignCalculator.parseNumberOrExpr(currentLineText) : Double.NaN;
         boolean isExpr = currentLineText != null && SignCalculator.isValidExpression(currentLineText);

         double total = 0.0;
         int numberLines = 0;

         for (String msg : this.messages) {
            double val = SignCalculator.parseNumberOrExpr(msg);
            if (!Double.isNaN(val)) {
               total += val;
               numberLines++;
            }
         }

         if (numberLines > 0) {
            String totalText = "§6Total: §e" + SignCalculator.formatResultOnly(total);
            int tw = mc.font.width(totalText);
            guiGraphics.text(mc.font, totalText, baseX - tw / 2, baseY - 14, -1, true);
         }

         if (isExpr) {
            String preview = "§a" + SignCalculator.getPreviewText(currentLineText);
            int w = mc.font.width(preview);
            guiGraphics.text(mc.font, preview, baseX - w / 2, baseY + 16, -1, true);
         } else if (numberLines > 1 && !Double.isNaN(currentVal)) {
            String preview = "§b" + SignCalculator.formatResultOnly(currentVal);
            int w = mc.font.width(preview);
            guiGraphics.text(mc.font, preview, baseX - w / 2, baseY + 16, -1, true);
         }
      }
   }
}
