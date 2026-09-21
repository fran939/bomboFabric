package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ChromaTextHelper;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin({BakedSheetGlyph.class})
public class ChromaGlyphMixin {
   @ModifyVariable(
      method = {"createGlyph"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 3
   )
   private int replaceChromaColor(int color) {
      int rgb = color & 0x00FFFFFF;
      if ((rgb & 0x00FF00F0) == 0x00FF00F0 || (rgb >= 0x00FF00F1 && rgb <= 0x00FFFFFE)) {
         int high = (rgb >> 16) & 0xFF;
         int mid = (rgb >> 8) & 0xFF;
         int low = rgb & 0xFF;

         if (high == 0xFF && (low >= 0xF1 && low <= 0xFE)) {
            int speed = (low == 0xFE) ? 1 : (low & 0xF);
            int letterOffset = mid & 0xF;
            long time = System.currentTimeMillis();
            // Calm, smooth 8-second wave with individual letter color shift
            double cyclePeriod = 8000.0 / Math.max(0.2, (double)speed);
            float baseHue = (float)((time % (long)cyclePeriod) / cyclePeriod);
            float hue = (baseHue + letterOffset * 0.045f) % 1.0f;
            return ChromaTextHelper.hsvToArgb(hue);
         }
      }
      return color;
   }
}
