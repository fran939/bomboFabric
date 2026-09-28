package me.bombo.bomboaddons.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Font.class)
public abstract class FontMixin {

    @Redirect(
        method = "getGlyph",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GlyphSource;getRandomGlyph(Lnet/minecraft/util/RandomSource;I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"
        )
    )
    private BakedGlyph bombo$stabilizeObfuscatedGlyph(GlyphSource glyphSource, RandomSource random, int width) {
        BakedGlyph randomGlyph = glyphSource.getRandomGlyph(random, width);
        if (randomGlyph == null) return null;

        // Force a stable fixed advance equal to the target width so scrambling glyphs never shake text
        return new StableObfuscatedGlyph(randomGlyph, (float) width);
    }

    private static class StableObfuscatedGlyph implements BakedGlyph {
        private final BakedGlyph delegate;
        private final GlyphInfo stableInfo;

        public StableObfuscatedGlyph(BakedGlyph delegate, float fixedAdvance) {
            this.delegate = delegate;
            GlyphInfo original = delegate.info();
            this.stableInfo = new GlyphInfo() {
                @Override
                public float getAdvance() {
                    return fixedAdvance;
                }

                @Override
                public float getAdvance(boolean bold) {
                    return bold ? fixedAdvance + original.getBoldOffset() : fixedAdvance;
                }

                @Override
                public float getBoldOffset() {
                    return original.getBoldOffset();
                }

                @Override
                public float getShadowOffset() {
                    return original.getShadowOffset();
                }
            };
        }

        @Override
        public GlyphInfo info() {
            return stableInfo;
        }

        @Override
        public TextRenderable.Styled createGlyph(float x, float y, int color, int shadowColor, Style style, float shadowOffsetX, float shadowOffsetY) {
            return delegate.createGlyph(x, y, color, shadowColor, style, shadowOffsetX, shadowOffsetY);
        }
    }
}
