package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.CrosshairRenderer;
import me.bombo.bomboaddons.kuudra.pearls.Pearls;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Hud.class})
public class GuiMixin {
   @Inject(
      method = {"setTitle"},
      at = {@At("HEAD")}
   )
   private void onSetTitle(Component component, CallbackInfo ci) {
      if (component != null) {
         Pearls.onTitleReceived(component.getString());
      }

   }

   @Inject(
      method = {"setSubtitle"},
      at = {@At("HEAD")}
   )
   private void onSetSubtitle(Component component, CallbackInfo ci) {
      if (component != null) {
         Pearls.onTitleReceived(component.getString());
      }

   }

   @Inject(
      method = {"setOverlayMessage"},
      at = {@At("HEAD")}
   )
   private void onSetOverlayMessage(Component component, boolean animateColor, CallbackInfo ci) {
      if (component != null) {
         Pearls.onTitleReceived(component.getString());
      }

   }

   @Inject(
      method = {"extractCrosshair"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onExtractCrosshair(GuiGraphicsExtractor guiGraphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (BomboConfig.get().customCrosshair != null && BomboConfig.get().customCrosshair.enabled) {
         CrosshairRenderer.render(guiGraphicsExtractor);
         ci.cancel();
      }

   }

   @Inject(
      method = {"extractTextureOverlay"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onRenderTextureOverlay(GuiGraphicsExtractor guiGraphics, Identifier texture, float alpha, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         if (s.clearWaterAndLava || s.waterOpacity < 50 || s.lavaOpacity < 50) {
            ci.cancel();
         }
         if (s.noRenderFireOverlay && texture != null && texture.toString().contains("fire")) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"extractArmor"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private static void onExtractArmor(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int yLineBase, int numHealthRows, int healthRowHeight, int xLeft, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderArmorBar) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractFood"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onExtractFood(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int yLineBase, int xRight, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderFoodBar) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractHearts"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onExtractHearts(GuiGraphicsExtractor graphics, net.minecraft.world.entity.player.Player player, int xLeft, int yLineBase, int healthRowHeight, int heartOffsetIndex, float maxHealth, int currentHealth, int oldHealth, int absorption, boolean blink, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderHealthBar) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractEffects"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onExtractEffects(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderEffectDisplay) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"extractSelectedItemName"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onExtractSelectedItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderSelectedItemName) {
         ci.cancel();
      }
   }
}
