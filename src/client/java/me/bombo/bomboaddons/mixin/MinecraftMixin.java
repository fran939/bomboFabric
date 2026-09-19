package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.KuudraPerkClicker;
import me.bombo.bomboaddons.LeftClickEtherwarp;
import me.bombo.bomboaddons.PlaytimeTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public class MinecraftMixin {
   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onStartAttack(CallbackInfoReturnable<Boolean> cir) {
      Minecraft mc = (Minecraft)(Object)this;
      if (mc.crosshairPickEntity != null) {
         me.bombo.bomboaddons.features.diana.DianaLootshare.onPlayerAttackEntity(mc.crosshairPickEntity);
      }
      if (mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
         net.minecraft.core.BlockPos bp = bhr.getBlockPos();
         if (me.bombo.bomboaddons.features.critters.CritterSafariEngine.BEE_NESTS.contains(bp)) {
            me.bombo.bomboaddons.features.critters.CritterSafariEngine.clearedBeeNests.add(bp);
         }
      }
      me.bombo.bomboaddons.features.DojoUtilities.onPreAttack(mc);
      if (KuudraPerkClicker.shouldBlockAttack()) {
         cir.setReturnValue(false);
         cir.cancel();
      } else {
         if (LeftClickEtherwarp.onLeftClick()) {
            cir.setReturnValue(false);
            cir.cancel();
         }

      }
   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onContinueAttack(boolean breaking, CallbackInfo ci) {
      if (LeftClickEtherwarp.isHoldingEtherwarp()) {
         LeftClickEtherwarp.onContinueAttack();
         ci.cancel();
      } else if (KuudraPerkClicker.shouldBlockAttack()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void onTickTail(CallbackInfo ci) {
      Minecraft mc = (Minecraft)(Object)this;
      if (me.bombo.bomboaddons.BomboaddonsClient.holdingLeftClick) {
         if (mc.options != null) mc.options.keyAttack.setDown(true);
         if (mc.player != null && mc.level != null) {
            try {
               if (mc.gameMode != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                  net.minecraft.core.BlockPos pos = bhr.getBlockPos();
                  net.minecraft.core.Direction dir = bhr.getDirection();
                  if (!mc.level.getBlockState(pos).isAir()) {
                     mc.gameMode.continueDestroyBlock(pos, dir);
                  }
               } else if (mc.gameMode != null && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                  mc.gameMode.attack(mc.player, ehr.getEntity());
               }
               if (!mc.player.swinging) {
                  mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
               }
            } catch (Throwable ignored) {}
         }
      }
      if (me.bombo.bomboaddons.BomboaddonsClient.holdingRightClick) {
         if (mc.options != null) mc.options.keyUse.setDown(true);
      }
      me.bombo.bomboaddons.GardenMovement.onTick(mc);
      me.bombo.bomboaddons.features.profile.AutoProfileSwapper.onClientTick();
      me.bombo.bomboaddons.features.TotemAnimationManager.onClientTick();
      me.bombo.bomboaddons.features.AutoExpCapsule.onTick(mc);
      me.bombo.bomboaddons.util.ClipboardCommandManager.pollClipboard();
   }

   @Inject(
      method = {"close"},
      at = {@At("HEAD")}
   )
   private void onClose(CallbackInfo ci) {
      PlaytimeTracker.sendPlaytimeDataToCloud(false);
   }
}
