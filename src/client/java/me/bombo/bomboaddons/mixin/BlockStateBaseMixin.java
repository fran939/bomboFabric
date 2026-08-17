package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.HypixelWorldPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "canSurvive", at = @At("HEAD"), cancellable = true)
    private void onCanSurvive(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (HypixelWorldPhysics.noPhysics) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void onUpdateShape(LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
        if (HypixelWorldPhysics.noPhysics) {
            cir.setReturnValue((BlockState) (Object) this);
        }
    }
}
