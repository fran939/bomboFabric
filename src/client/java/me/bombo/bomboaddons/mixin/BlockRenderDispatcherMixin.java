package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.FuckDiorite;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ModelBlockRenderer.class)
public class BlockRenderDispatcherMixin {

    @ModifyVariable(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockState modifyRenderState(BlockState state, BlockQuadOutput quadOutput, float f1, float f2, float f3, BlockAndTintGetter level, BlockPos pos) {
        if (FuckDiorite.inDungeonsOrPrivateIsland) {
            return FuckDiorite.checkAndReplace(pos, state);
        }
        return state;
    }
}
