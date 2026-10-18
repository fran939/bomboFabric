package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.util.ResourcePackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
   @Shadow @Final private Minecraft minecraft;
   @Shadow @Final private ReloadInstance reload;

   @Inject(
      method = "extractRenderState",
      at = @At("HEAD"),
      cancellable = true
   )
   private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
      // Check if we should render seamless overlay (in-world or explicit silent reload)
      if (this.minecraft != null && (this.minecraft.level != null || ResourcePackHelper.silentReload)) {
         ci.cancel();

         int width = this.minecraft.getWindow().getGuiScaledWidth();
         int height = this.minecraft.getWindow().getGuiScaledHeight();

         // Render current screen underneath if one exists
         Screen currentScreen = this.minecraft.gui != null ? this.minecraft.gui.screen() : null;
         if (currentScreen != null) {
            try {
               currentScreen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
            } catch (Throwable ignored) {}
         }

         // Sleek, minimal loading pill at bottom center
         float progress = this.reload != null ? this.reload.getActualProgress() : 0.0F;
         int percent = (int)(progress * 100.0F);
         String text = "§7Loading Textures §b" + percent + "%";

         var font = this.minecraft.font;
         if (font != null) {
            int textWidth = font.width(text);
            int pillWidth = textWidth + 16;
            int pillHeight = 16;
            int pillX = (width - pillWidth) / 2;
            int pillY = height - 28;

            // Semi-transparent pill background
            graphics.fill(pillX, pillY, pillX + pillWidth, pillY + pillHeight, 0xB0000000);

            // Progress bar line at bottom of pill
            int barWidth = (int)((pillWidth - 4) * Math.min(1.0F, Math.max(0.0F, progress)));
            graphics.fill(pillX + 2, pillY + pillHeight - 2, pillX + 2 + barWidth, pillY + pillHeight - 1, 0xFF28F9FE);

            // Centered label inside pill
            graphics.centeredText(font, text, width / 2, pillY + 4, 0xFFFFFFFF);
         }
      }
   }
}
