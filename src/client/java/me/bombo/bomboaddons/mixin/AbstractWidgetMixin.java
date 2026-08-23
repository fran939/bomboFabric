package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfigGUI;
import me.bombo.bomboaddons.ItemCustomizeScreen;
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

@Mixin({AbstractWidget.class})
public abstract class AbstractWidgetMixin {
   @Shadow
   public boolean active;
   @Shadow
   public boolean visible;

   @Shadow
   public abstract int getX();

   @Shadow
   public abstract int getY();

   @Shadow
   public abstract int getWidth();

   @Shadow
   public abstract int getHeight();

   @Shadow
   public abstract Component getMessage();

   @Shadow
   public abstract boolean isHoveredOrFocused();

   @Shadow
   public abstract boolean isFocused();

   @Inject(
      method = {"extractRenderState"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onExtractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof BomboConfigGUI || mc.screen instanceof ItemCustomizeScreen) {
         AbstractWidget self = (AbstractWidget)(Object)this;
         if (!this.visible) {
            return;
         }

         int x = this.getX();
         int y = this.getY();
         int w = this.getWidth();
         int h = this.getHeight();
         boolean active = this.active;
         boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
         if (self instanceof Checkbox) {
            Checkbox cb = (Checkbox)self;
            boolean selected = cb.selected();
            g.fill(x, y, x + w, y + h, -300871397);
            int borderColor = !active ? 587202559 : (hovered ? -4931842 : 1154340596);
            g.fill(x, y, x + w, y + 1, borderColor);
            g.fill(x, y + h - 1, x + w, y + h, borderColor);
            g.fill(x, y, x + 1, y + h, borderColor);
            g.fill(x + w - 1, y, x + w, y + h, borderColor);
            if (selected) {
               int pad = 4;
               g.fill(x + pad, y + pad, x + w - pad, y + h - pad, -5839967);
            }

            ci.cancel();
         } else if (self instanceof Button) {
            Component msg = this.getMessage();
            int bgColor = hovered ? -583978428 : -585623515;
            if (!active) {
               bgColor = -1441722085;
            }

            g.fill(x, y, x + w, y + h, bgColor);
            int borderColor = !active ? 587202559 : (hovered ? -7742229 : 1154340596);
            g.fill(x, y, x + w, y + 1, borderColor);
            g.fill(x, y + h - 1, x + w, y + h, borderColor);
            g.fill(x, y, x + 1, y + h, borderColor);
            g.fill(x + w - 1, y, x + w, y + h, borderColor);
            if (active && hovered) {
               g.fill(x + 1, y + 1, x + 3, y + h - 1, -9123860);
            }

            String text = msg.getString();
            int textColor = !active ? 1724765940 : (hovered ? -7742229 : -3287308);
            int textW = mc.font.width(text);
            int textX = x + (w - textW) / 2;
            int textY = y + (h - 8) / 2;
            g.text(mc.font, text, textX, textY, textColor);
            ci.cancel();
         } else if (self instanceof EditBox) {
            boolean focused = this.isFocused();
            g.fill(x, y, x + w, y + h, -300871397);
            int borderColor = focused ? -347257 : (hovered ? -3287308 : 1154340596);
            g.fill(x, y, x + w, y + 1, borderColor);
            g.fill(x, y + h - 1, x + w, y + h, borderColor);
            g.fill(x, y, x + 1, y + h, borderColor);
            g.fill(x + w - 1, y, x + w, y + h, borderColor);
         }
      }

   }
}
