package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.FuckDiorite;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

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

    @ModifyVariable(method = "setTimeFromServer", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private long modifyTimeFromServer(long time) {
        if (BomboConfig.get().customTimeEnabled) {
            int hour = BomboConfig.get().customTimeHour;
            return (long) ((hour - 6 + 24) % 24) * 1000L;
        }
        return time;
    }
}
