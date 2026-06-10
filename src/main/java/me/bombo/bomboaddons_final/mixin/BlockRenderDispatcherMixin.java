package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.FuckDiorite;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {

    @ModifyVariable(
        method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLjava/util/List;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockState modifyRenderState(BlockState state, BlockState originalState, BlockPos pos) {
        if (FuckDiorite.inDungeonsOrPrivateIsland) {
            return FuckDiorite.checkAndReplace(pos, state);
        }
        return state;
    }
}
