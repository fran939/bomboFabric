package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.HypixelWorldPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlock.class)
public class FallingBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (HypixelWorldPhysics.noPhysics) {
            ci.cancel();
        }
    }

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = true)
    private void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving, CallbackInfo ci) {
        if (HypixelWorldPhysics.noPhysics) {
            ci.cancel();
        }
    }
}
