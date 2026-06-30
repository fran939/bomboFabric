package me.bombo.bomboaddons.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @Shadow public abstract int getX();
    @Shadow public abstract int getY();
    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();
    @Shadow public abstract Component getMessage();
    @Shadow public boolean active;
    @Shadow public boolean visible;
    @Shadow public abstract boolean isHoveredOrFocused();
    @Shadow public abstract boolean isFocused();

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void onExtractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof me.bombo.bomboaddons.BomboConfigGUI || mc.screen instanceof me.bombo.bomboaddons.ItemCustomizeScreen) {
            AbstractWidget self = (AbstractWidget) (Object) this;
            
            if (!this.visible) {
                return;
            }
            
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            boolean active = this.active;
            boolean hovered = this.isHoveredOrFocused();
            
            if (self instanceof Checkbox) {
                Checkbox cb = (Checkbox) self;
                boolean selected = cb.selected();
                
                // 1. Draw Checkbox Background
                g.fill(x, y, x + w, y + h, 0xEE11111B);
                
                // 2. Draw Checkbox Border
                int borderColor = !active ? 0x22FFFFFF : (hovered ? 0xFFB4BEFE : 0x44CDD6F4);
                g.fill(x, y, x + w, y + 1, borderColor);
                g.fill(x, y + h - 1, x + w, y + h, borderColor);
                g.fill(x, y, x + 1, y + h, borderColor);
                g.fill(x + w - 1, y, x + w, y + h, borderColor);
                
                // 3. Draw Inner Fill if selected
                if (selected) {
                    int pad = 4;
                    g.fill(x + pad, y + pad, x + w - pad, y + h - pad, 0xFFA6E3A1);
                }
                
                ci.cancel();
            } else if (self instanceof Button) {
                Component msg = this.getMessage();
                
                // 1. Draw Background
                int bgColor = hovered ? 0xDD313244 : 0xDD181825;
                if (!active) {
                    bgColor = 0xAA11111B;
                }
                g.fill(x, y, x + w, y + h, bgColor);
                
                // 2. Draw Borders
                int borderColor = !active ? 0x22FFFFFF : (hovered ? 0xFF89DCEB : 0x44CDD6F4);
                g.fill(x, y, x + w, y + 1, borderColor);
                g.fill(x, y + h - 1, x + w, y + h, borderColor);
                g.fill(x, y, x + 1, y + h, borderColor);
                g.fill(x + w - 1, y, x + w, y + h, borderColor);
                
                // 3. Draw Left Indicator Bar if hovered
                if (active && hovered) {
                    g.fill(x + 1, y + 1, x + 3, y + h - 1, 0xFF74C7EC);
                }
                
                // 4. Draw Text Centered
                String text = msg.getString();
                int textColor = !active ? 0x66CDD6F4 : (hovered ? 0xFF89DCEB : 0xFFCDD6F4);
                
                int textW = mc.font.width(text);
                int textX = x + (w - textW) / 2;
                int textY = y + (h - 8) / 2;
                g.text(mc.font, text, textX, textY, textColor);
                
                ci.cancel();
            } else if (self instanceof EditBox) {
                boolean focused = this.isFocused();
                
                // 1. Draw Custom EditBox Background
                g.fill(x, y, x + w, y + h, 0xEE11111B);
                
                // 2. Draw Custom EditBox Border
                int borderColor = focused ? 0xFFFAB387 : (hovered ? 0xFFCDD6F4 : 0x44CDD6F4);
                g.fill(x, y, x + w, y + 1, borderColor);
                g.fill(x, y + h - 1, x + w, y + h, borderColor);
                g.fill(x, y, x + 1, y + h, borderColor);
                g.fill(x + w - 1, y, x + w, y + h, borderColor);
            }
        }
    }
}
