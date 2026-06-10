package me.bombo.bomboaddons_final.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import me.bombo.bomboaddons_final.FuckDiorite;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @ModifyVariable(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"),
        argsOnly = true
    )
    private BlockState modifyBlockState(BlockState state, BlockPos pos) {
        return FuckDiorite.checkAndReplace(pos, state);
    }
}
