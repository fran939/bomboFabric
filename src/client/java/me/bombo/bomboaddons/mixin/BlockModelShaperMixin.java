package me.bombo.bomboaddons.mixin;

import java.util.Map;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.FuckDiorite;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockStateModelSet.class})
public class BlockModelShaperMixin {
   @Shadow
   @Final
   private Map<BlockState, BlockStateModel> modelByState;

   @Inject(
      method = {"get(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetBlockModel(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && (s.zamasuWorldWhite || "Zamasu".equalsIgnoreCase(s.guiThemeMode))) {
         BlockStateModel whiteModel = this.modelByState.get(Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState());
         if (whiteModel != null) {
            cir.setReturnValue(whiteModel);
            return;
         }
      }

      if (s != null && s.replaceGrayCarpetDwarven && state != null && (state.is(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.GRAY)) || state.is(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY)))) {
         String area = me.bombo.bomboaddons.SkyblockUtils.getLocation();
         if (area == null) area = me.bombo.bomboaddons.BomboaddonsClient.currentArea;
         if (area != null && area.toLowerCase().contains("dwarven")) {
            BlockStateModel redCarpetModel = this.modelByState.get(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState());
            if (redCarpetModel != null) {
               cir.setReturnValue(redCarpetModel);
               return;
            }
         }
      }

      if (FuckDiorite.inDungeonsOrPrivateIsland && state != null && (state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE))) {
         BlockState glassState = FuckDiorite.getSelectedGlassState(s.fuckDioriteColor);
         BlockStateModel model = this.modelByState.get(glassState);
         if (model != null) {
            cir.setReturnValue(model);
            return;
         }
      }

      if (state != null) {
         BlockState hiderState = me.bombo.bomboaddons.features.hider.EntityBlockHider.getReplacedBlockState(null, state);
         if (hiderState != null && hiderState != state) {
            BlockStateModel hiderModel = this.modelByState.get(hiderState);
            if (hiderModel != null) {
               cir.setReturnValue(hiderModel);
               return;
            }
         }
      }
   }
}
