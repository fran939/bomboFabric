package me.bombo.bomboaddons.mixin;

import java.util.Iterator;
import java.util.Map.Entry;
import me.bombo.bomboaddons.BomboConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_124;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({class_1297.class})
public abstract class EntityMixin {
   @Inject(
      method = {"method_5756"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisibleTo(class_1657 player, CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugMode) {
         cir.setReturnValue(false);
      }

   }

   @Inject(
      method = {"method_5767"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugMode) {
         cir.setReturnValue(false);
      }

   }

   @Inject(
      method = {"method_5851"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
      class_1297 self = (class_1297)this;
      String name = class_124.method_539(self.method_5477().getString());
      if (name != null) {
         name = name.toLowerCase();
         Iterator var4 = BomboConfig.get().highlights.entrySet().iterator();

         while(var4.hasNext()) {
            Entry<String, BomboConfig.HighlightInfo> entry = (Entry)var4.next();
            if (name.contains((CharSequence)entry.getKey())) {
               if (self.method_5767() && !((BomboConfig.HighlightInfo)entry.getValue()).showInvisible) {
                  return;
               }

               cir.setReturnValue(true);
               return;
            }
         }
      }

   }

   @Inject(
      method = {"method_22861"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
      class_1297 self = (class_1297)this;
      String name = class_124.method_539(self.method_5477().getString());
      if (name != null) {
         name = name.toLowerCase();
         Iterator var4 = BomboConfig.get().highlights.entrySet().iterator();

         while(var4.hasNext()) {
            Entry<String, BomboConfig.HighlightInfo> entry = (Entry)var4.next();
            if (name.contains((CharSequence)entry.getKey())) {
               try {
                  class_124 format = class_124.valueOf(((BomboConfig.HighlightInfo)entry.getValue()).color);
                  if (format != null && format.method_532() != null) {
                     cir.setReturnValue(format.method_532());
                     return;
                  }
               } catch (Exception var7) {
               }
            }
         }
      }

   }

   @Inject(
      method = {"method_5807"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsCustomNameVisible(CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().debugMode) {
         cir.setReturnValue(true);
      }

   }
}
