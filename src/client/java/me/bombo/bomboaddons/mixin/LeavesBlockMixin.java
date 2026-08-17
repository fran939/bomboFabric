package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.HypixelWorldPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeavesBlock.class)
public class LeavesBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void onRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (HypixelWorldPhysics.noPhysics) {
            ci.cancel();
        }
    }

    @Inject(method = "decaying", at = @At("HEAD"), cancellable = true)
    private void onDecaying(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (HypixelWorldPhysics.noPhysics) {
            cir.setReturnValue(false);
        }
    }
}
