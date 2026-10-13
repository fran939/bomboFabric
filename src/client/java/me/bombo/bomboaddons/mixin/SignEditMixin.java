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
public abstract class SignEditMixin implements me.bombo.bomboaddons.features.shards.SignEditAccessor {
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
         float scale = s.signCalculatorScale > 0.0F ? s.signCalculatorScale : 1.0F;
         int baseX = (s.signCalculatorX >= 0) ? s.signCalculatorX : (screenWidth / 2 - 75);
         int baseY = (s.signCalculatorY >= 0) ? s.signCalculatorY : 80;

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

         guiGraphics.pose().pushMatrix();
         if (scale != 1.0F && scale > 0.0F) {
            guiGraphics.pose().scale(scale, scale);
            int drawX = (int) ((float) baseX / scale);
            int drawY = (int) ((float) baseY / scale);
            if (numberLines > 0) {
               String totalText = "§6Total: §e" + SignCalculator.formatResultOnly(total);
               guiGraphics.text(mc.font, totalText, drawX, drawY, -1, true);
            }
            if (isExpr) {
               String preview = "§a" + SignCalculator.getPreviewText(currentLineText);
               guiGraphics.text(mc.font, preview, drawX, drawY + 12, -1, true);
            } else if (numberLines > 1 && !Double.isNaN(currentVal)) {
               String preview = "§b" + SignCalculator.formatResultOnly(currentVal);
               guiGraphics.text(mc.font, preview, drawX, drawY + 12, -1, true);
            }
         } else {
            if (numberLines > 0) {
               String totalText = "§6Total: §e" + SignCalculator.formatResultOnly(total);
               guiGraphics.text(mc.font, totalText, baseX, baseY, -1, true);
            }
            if (isExpr) {
               String preview = "§a" + SignCalculator.getPreviewText(currentLineText);
               guiGraphics.text(mc.font, preview, baseX, baseY + 12, -1, true);
            } else if (numberLines > 1 && !Double.isNaN(currentVal)) {
               String preview = "§b" + SignCalculator.formatResultOnly(currentVal);
               guiGraphics.text(mc.font, preview, baseX, baseY + 12, -1, true);
            }
         }
         guiGraphics.pose().popMatrix();
      }

      if (me.bombo.bomboaddons.features.shards.ShardsShoppingManager.hasPendingQuantity()) {
         Minecraft mc = Minecraft.getInstance();
         int screenWidth = mc.getWindow().getGuiScaledWidth();
         int btnW = 200;
         int btnH = 26;
         int btnX = screenWidth / 2 - btnW / 2;
         int btnY = 35;
         boolean isHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
         int qty = me.bombo.bomboaddons.features.shards.ShardsShoppingManager.pendingSignQuantity;
         String shard = me.bombo.bomboaddons.features.shards.ShardsShoppingManager.pendingSignShard != null
                 ? me.bombo.bomboaddons.features.shards.ShardsShoppingManager.pendingSignShard : "Shard";

         guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, isHovered ? 0xF0238636 : 0xEE161B22);
         guiGraphics.outline(btnX, btnY, btnW, btnH, isHovered ? 0xFF00FF88 : 0xFF30363D);
         guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + 2, 0xFFF59E0B);

         String label = "§6⚡ Auto-Fill: §f" + qty + " §a(" + shard + ")";
         int textW = mc.font.width(label);
         guiGraphics.text(mc.font, label, btnX + (btnW - textW) / 2, btnY + 5, 0xFFFFFF, true);
         String sub = "§7[Click to insert & submit]";
         int subW = mc.font.width(sub);
         guiGraphics.text(mc.font, sub, btnX + (btnW - subW) / 2, btnY + 15, 0xAAAAAA, true);
      }
   }
 
   @Override
   public void bombo$applyAutoFill(int quantity) {
      if (this.messages != null && this.messages.length > 0) {
         this.messages[0] = String.valueOf(quantity);
      }
      ((AbstractSignEditScreen)(Object)this).onClose();
   }
}
