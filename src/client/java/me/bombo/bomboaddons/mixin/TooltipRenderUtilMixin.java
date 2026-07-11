package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(TooltipRenderUtil.class)
public class TooltipRenderUtilMixin {

    @Shadow
    @Final
    private static Identifier BACKGROUND_SPRITE;

    @Shadow
    @Final
    private static Identifier FRAME_SPRITE;

    @Inject(method = "getBackgroundSprite", at = @At("HEAD"), cancellable = true)
    private static void onGetBackgroundSprite(Identifier style, CallbackInfoReturnable<Identifier> cir) {
        if (!BomboConfig.get().disableCustomTooltips || style == null) return;
        cir.setReturnValue(BACKGROUND_SPRITE);
    }

    @Inject(method = "getFrameSprite", at = @At("HEAD"), cancellable = true)
    private static void onGetFrameSprite(Identifier style, CallbackInfoReturnable<Identifier> cir) {
        if (!BomboConfig.get().disableCustomTooltips || style == null) return;
        cir.setReturnValue(FRAME_SPRITE);
    }

    @Inject(
        method = "extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onExtractTooltipBackground(
        GuiGraphicsExtractor graphics,
        int x,
        int y,
        int width,
        int height,
        Identifier backgroundSprite,
        CallbackInfo ci
    ) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.customTooltipBg) {
            ci.cancel();

            int bg = s.tooltipBgColor;
            int border = s.tooltipBorderColor;

            int pad = 4;
            int padY = 2;
            int x1 = x - pad;
            int x2 = x + width + pad;
            int y1 = y - padY;
            int y2 = y + height + padY;

            if (((bg >> 24) & 0xFF) > 0) {
                // Draw background fill
                graphics.fill(x1, y1, x2, y2, bg);
            }

            if (((border >> 24) & 0xFF) > 0) {
                // Draw border outlines (1px solid border)
                // Left border
                graphics.fill(x1, y1, x1 + 1, y2, border);
                // Right border
                graphics.fill(x2 - 1, y1, x2, y2, border);
                // Top border
                graphics.fill(x1, y1, x2, y1 + 1, border);
                // Bottom border
                graphics.fill(x1, y2 - 1, x2, y2, border);
            }
        } else if (s.disableCustomTooltips) {
            ci.cancel();

            int bg = 0xF0100010;
            int border = 0x505000FF; // Vanilla border color

            int pad = 4;
            int padY = 2;
            int x1 = x - pad;
            int x2 = x + width + pad;
            int y1 = y - padY;
            int y2 = y + height + padY;

            // Draw background fill
            graphics.fill(x1, y1, x2, y2, bg);

            // Draw border outlines (1px solid border)
            graphics.fill(x1, y1, x1 + 1, y2, border);
            graphics.fill(x2 - 1, y1, x2, y2, border);
            graphics.fill(x1, y1, x2, y1 + 1, border);
            graphics.fill(x1, y2 - 1, x2, y2, border);
        }
    }
}
