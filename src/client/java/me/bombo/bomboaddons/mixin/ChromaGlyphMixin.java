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
      if (color == -65282) {
         int idx = ChromaTextHelper.chromaGlyphIndex++;
         return ChromaTextHelper.getAnimatedColor(idx);
      } else {
         return color;
      }
   }
}
