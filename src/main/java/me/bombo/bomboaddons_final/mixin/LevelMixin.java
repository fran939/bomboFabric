package me.bombo.bomboaddons_final.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import me.bombo.bomboaddons_final.FuckDiorite;

@Mixin(Level.class)
public class LevelMixin {
    @Shadow public boolean isClientSide;

    @ModifyVariable(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockState modifyBlockState(BlockState state, BlockPos pos) {
        if (this.isClientSide) {
            return FuckDiorite.checkAndReplace(pos, state);
        }
        return state;
    }
}
