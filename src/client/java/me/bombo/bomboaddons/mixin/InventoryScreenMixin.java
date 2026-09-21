package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.ItemListOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({InventoryScreen.class})
public class InventoryScreenMixin {
   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo ci) {
      if (me.bombo.bomboaddons.BomboConfig.get().noRenderRecipeBook) {
         InventoryScreen self = (InventoryScreen)(Object)this;
         for (net.minecraft.client.gui.components.events.GuiEventListener listener : new java.util.ArrayList<>(self.children())) {
            if (listener instanceof net.minecraft.client.gui.components.ImageButton btn) {
               btn.visible = false;
               btn.active = false;
            }
         }
      }
   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void onExtractRenderStateTail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      InventoryScreen self = (InventoryScreen)(Object)this;
      AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor)self;
      int leftPos = accessor.getLeftPos();
      int topPos = accessor.getTopPos();
      int imageWidth = accessor.getImageWidth();
      int imageHeight = accessor.getImageHeight();

      me.bombo.bomboaddons.features.swapper.InventorySlotSwapManager.renderSlotOverlays(graphics, self, leftPos, topPos, Minecraft.getInstance().font, mouseX, mouseY);
      me.bombo.bomboaddons.features.buttons.InventoryButtonManager.renderButtons(graphics, self, mouseX, mouseY, leftPos, topPos, imageWidth, imageHeight);
      ItemListOverlay.render(graphics, Minecraft.getInstance().font, mouseX, mouseY);
   }
}
