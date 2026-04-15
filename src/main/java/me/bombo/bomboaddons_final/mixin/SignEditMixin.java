package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.Bomboaddons;
import me.bombo.bomboaddons_final.SignCalculator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      Bomboaddons.LOGGER.info("SignEditScreen initialized");
   }

   @Inject(
      method = {"onClose"},
      at = {@At("HEAD")}
   )
   private void onClose(CallbackInfo ci) {
      if (BomboConfig.get().signCalculator) {
         for(int i = 0; i < this.messages.length; ++i) {
            if (this.messages[i] != null && !this.messages[i].isEmpty()) {
               this.messages[i] = SignCalculator.calculate(this.messages[i]);
            }
         }

      }
   }

   @Inject(
      method = {"render"},
      at = {@At("TAIL")}
   )
   private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if (BomboConfig.get().signCalculator) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int baseX = screenWidth / 2;
         int baseY = 55;
         String currentLineText = this.messages[this.line];
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
            totalWidth = mc.font.width(totalText);
            guiGraphics.drawString(mc.font, totalText, baseX - totalWidth / 2, baseY, -1, true);
         }

         double total = 0.0D;
         boolean hasAnyExpression = false;
         String[] var20 = this.messages;
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
            totalWidth = mc.font.width(totalText);
            guiGraphics.drawString(mc.font, totalText, baseX - totalWidth / 2, baseY - 30, -1, true);
         }

      }
   }
}
