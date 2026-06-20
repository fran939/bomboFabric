package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.FuckDiorite;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelSet.class)
public class BlockModelShaperMixin {

    @Shadow
    @Final
    private Map<BlockState, BlockModel> blockModelByStateCache;

    @Inject(
        method = "get(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/client/renderer/block/model/BlockModel;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onGetBlockModel(BlockState state, CallbackInfoReturnable<BlockModel> cir) {
        if (FuckDiorite.inDungeonsOrPrivateIsland) {
            if (state != null && (state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE))) {
                BomboConfig.Settings s = BomboConfig.get();
                BlockState glassState = FuckDiorite.getSelectedGlassState(s.fuckDioriteColor);
                BlockModel model = this.blockModelByStateCache.get(glassState);
                if (model != null) {
                    cir.setReturnValue(model);
                }
            }
        }
    }
}
