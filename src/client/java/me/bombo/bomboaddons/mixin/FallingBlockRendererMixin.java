package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.FuckDiorite;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import net.minecraft.client.renderer.entity.state.FallingBlockRenderState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockRenderer.class)
public class FallingBlockRendererMixin {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/FallingBlockEntity;Lnet/minecraft/client/renderer/entity/state/FallingBlockRenderState;F)V",
        at = @At("TAIL")
    )
    private void onExtractRenderState(FallingBlockEntity entity, FallingBlockRenderState state, float f, CallbackInfo ci) {
        if (FuckDiorite.inDungeonsOrPrivateIsland) {
            if (state.movingBlockRenderState != null) {
                BlockState dioriteState = state.movingBlockRenderState.blockState;
                if (dioriteState != null && (dioriteState.is(Blocks.DIORITE) || dioriteState.is(Blocks.POLISHED_DIORITE))) {
                    BlockPos pos = state.movingBlockRenderState.blockPos;
                    if (pos != null) {
                        BlockState replacement = FuckDiorite.checkAndReplace(pos, dioriteState);
                        if (replacement != dioriteState) {
                            state.movingBlockRenderState.blockState = replacement;
                        }
                    }
                }
            }
        }
    }
}
