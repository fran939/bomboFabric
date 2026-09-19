package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public class PlayerMixin {
   @Inject(
      method = {"swing(Lnet/minecraft/world/InteractionHand;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSwing(InteractionHand hand, CallbackInfo ci) {
      if ((Object)this == Minecraft.getInstance().player && Minecraft.getInstance().gui.screen() != null) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"hasEffect"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this == Minecraft.getInstance().player) {
         if (effect.is(MobEffects.BLINDNESS) && BomboConfig.get().disableBlindness) {
            cir.setReturnValue(false);
         } else if (effect.is(MobEffects.INVISIBILITY) && BomboConfig.get().removeSelfInvisibility) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"getItemBySlot"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetItemBySlot(net.minecraft.world.entity.EquipmentSlot slot, CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null) {
         boolean isSelf = (Object)this == Minecraft.getInstance().player;
         if (s.hideSecondHand && slot == net.minecraft.world.entity.EquipmentSlot.OFFHAND) {
            if (!s.hideArmorOnlySelf || isSelf) {
               cir.setReturnValue(net.minecraft.world.item.ItemStack.EMPTY);
               return;
            }
         }
         if (s.hideArmor && slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
            if (!s.hideArmorOnlySelf || isSelf) {
               boolean shouldHide = false;
               switch (slot) {
                  case HEAD -> shouldHide = s.hideHelmet;
                  case CHEST -> shouldHide = s.hideChestplate;
                  case LEGS -> shouldHide = s.hideLeggings;
                  case FEET -> shouldHide = s.hideBoots;
               }
               if (shouldHide) {
                  cir.setReturnValue(net.minecraft.world.item.ItemStack.EMPTY);
               }
            }
         }
      }
   }

   @Inject(
      method = {"isPickable"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsPickable(CallbackInfoReturnable<Boolean> cir) {
      if ((Object)this instanceof net.minecraft.world.entity.ambient.Bat && BomboConfig.get().lassoClickThroughBats) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            net.minecraft.world.item.ItemStack main = mc.player.getMainHandItem();
            net.minecraft.world.item.ItemStack off = mc.player.getOffhandItem();
            String mainName = main != null && !main.isEmpty() ? main.getHoverName().getString().replaceAll("§.", "").toLowerCase() : "";
            String offName = off != null && !off.isEmpty() ? off.getHoverName().getString().replaceAll("§.", "").toLowerCase() : "";
            if (mainName.contains("lasso") || offName.contains("lasso")) {
               cir.setReturnValue(false);
            }
         }
      }
   }
}
