package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ItemListOverlay;
import me.bombo.bomboaddons.LF;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({EditBox.class})
public abstract class EditBoxMixin {
   private boolean insertingCopied = false;

   @Shadow
   public abstract String getValue();

   @Shadow
   public abstract void setValue(String var1);

   @Shadow
   public abstract int getCursorPosition();

   @Shadow
   public abstract void setCursorPosition(int var1);

   @Shadow
   public abstract void insertText(String var1);

   @Inject(
      method = {"extractWidgetRenderState"},
      at = {@At("HEAD")}
   )
   private void onRenderWidgetHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if ((Object)this == ItemListOverlay.searchBox) {
         float scale = BomboConfig.get().itemListSearchScale;
         if (scale != 1.0F) {
            graphics.pose().pushMatrix();
            int tx = ((AbstractWidget)(Object)this).getX();
            int ty = ((AbstractWidget)(Object)this).getY();
            graphics.pose().translate((float)tx, (float)ty);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate((float)(-tx), (float)(-ty));
         }
      }

   }

   @Inject(
      method = {"extractWidgetRenderState"},
      at = {@At("RETURN")}
   )
   private void onRenderWidgetReturn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
      if ((Object)this == ItemListOverlay.searchBox) {
         float scale = BomboConfig.get().itemListSearchScale;
         if (scale != 1.0F) {
            graphics.pose().popMatrix();
         }
      }

   }

   @Inject(
      method = {"insertText"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onInsertText(String text, CallbackInfo ci) {
      if (!this.insertingCopied) {
         if (text != null && BomboConfig.get().ignoreCapsLock && text.length() == 1) {
            boolean shift = Minecraft.getInstance().hasShiftDown();
            if (!shift && !text.equals(text.toLowerCase())) {
               ci.cancel();
               this.insertingCopied = true;

               try {
                  this.insertText(text.toLowerCase().replace('§', '&'));
               } finally {
                  this.insertingCopied = false;
               }

               return;
            }
         }

         if (text != null && text.contains("§")) {
            ci.cancel();
            this.insertingCopied = true;

            try {
               this.insertText(text.replace('§', '&'));
            } finally {
               this.insertingCopied = false;
            }
         }

      }
   }

   @Inject(
      method = {"setValue"},
      at = {@At("HEAD")}
   )
   private void onSetValue(String value, CallbackInfo ci) {
      if (value != null) {
         String lower = value.toLowerCase();
         if ((lower.startsWith("/lb") || lower.startsWith("/lfc")) && Minecraft.getInstance().screen instanceof ChatScreen) {
            LF.preFetchSelf();
         }
      }

   }
}
