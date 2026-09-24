package me.bombo.bomboaddons.mixin;

import java.util.Map;
import me.bombo.bomboaddons.cheat.esp.BedwarsESP;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.cheat.esp.HighlightESP;
import me.bombo.bomboaddons.cheat.esp.TargetPests;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({Entity.class})
public abstract class EntityMixin {
   @Inject(
      method = {"isInvisibleTo"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
      if (me.bombo.bomboaddons.cheat.hider.EntityBlockHider.shouldHideEntity((Entity)(Object)this)) {
         cir.setReturnValue(true);
         return;
      }
      if (BomboConfig.get().debugEntities) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"isInvisible"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
      if (me.bombo.bomboaddons.cheat.hider.EntityBlockHider.shouldHideEntity((Entity)(Object)this)) {
         cir.setReturnValue(true);
         return;
      }
      if (BomboConfig.get().debugEntities) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"isCurrentlyGlowing"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugEntities) {
         cir.setReturnValue(true);
         return;
      }
      Entity entity = (Entity)(Object)this;
      if (HighlightESP.shouldHideBody(entity)) {
         cir.setReturnValue(false);
         return;
      }
      if (HighlightESP.shouldEntityGlow(entity)) {
         BomboConfig.Settings s = BomboConfig.get();
         if (s.hideCheats) {
            Minecraft mc = Minecraft.getInstance();
            if (HighlightESP.isEntityVisibleCached(mc, entity)) {
               cir.setReturnValue(true);
            }
         } else {
            cir.setReturnValue(true);
         }
      }
   }

   @Inject(
      method = {"getTeamColor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      Integer color = HighlightESP.getEntityGlowColor((Entity)(Object)this);
      if (color != null) {
         cir.setReturnValue(color);
      }
   }

   @Inject(
      method = {"isCustomNameVisible"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCustomNameVisible(CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugEntities) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"isOnFire"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onIsOnFire(CallbackInfoReturnable<Boolean> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.noRenderFireOverlay && (Object)this == Minecraft.getInstance().player) {
         cir.setReturnValue(false);
      }
   }
}
