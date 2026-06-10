package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.FuckDiorite;
import me.bombo.bomboaddons_final.BomboConfig;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelShaper.class)
public class BlockModelShaperMixin {

    @Shadow
    @Final
    private Map<BlockState, BlockStateModel> modelByStateCache;

    @Inject(
        method = "getBlockModel(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/model/BlockStateModel;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onGetBlockModel(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        if (FuckDiorite.inDungeonsOrPrivateIsland) {
            if (state != null && (state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE))) {
                BomboConfig.Settings s = BomboConfig.get();
                BlockState glassState = FuckDiorite.getSelectedGlassState(s.fuckDioriteColor);
                BlockStateModel model = this.modelByStateCache.get(glassState);
                if (model != null) {
                    cir.setReturnValue(model);
                }
            }
        }
    }
}
