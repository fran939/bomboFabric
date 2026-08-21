package me.bombo.bomboaddons.mixin;

import java.util.Map;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.FuckDiorite;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockModelSet.class})
public class BlockModelShaperMixin {
   @Shadow
   @Final
   private Map<BlockState, BlockModel> blockModelByStateCache;

   @Inject(
      method = {"get(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/model/BlockModel;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetBlockModel(BlockState state, CallbackInfoReturnable<BlockModel> cir) {
      BomboConfig.Settings s = BomboConfig.get();
      if (s != null && s.replaceGrayCarpetDwarven && state != null && (state.is(Blocks.GRAY_CARPET) || state.is(Blocks.LIGHT_GRAY_CARPET))) {
         String area = me.bombo.bomboaddons.BomboaddonsClient.currentArea;
         if (area != null && area.toLowerCase().contains("dwarven")) {
            BlockModel redCarpetModel = (BlockModel)this.blockModelByStateCache.get(Blocks.RED_CARPET.defaultBlockState());
            if (redCarpetModel != null) {
               cir.setReturnValue(redCarpetModel);
               return;
            }
         }
      }

      if (FuckDiorite.inDungeonsOrPrivateIsland && state != null && (state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE))) {
         BlockState glassState = FuckDiorite.getSelectedGlassState(s.fuckDioriteColor);
         BlockModel model = (BlockModel)this.blockModelByStateCache.get(glassState);
         if (model != null) {
            cir.setReturnValue(model);
         }
      }

   }
}
