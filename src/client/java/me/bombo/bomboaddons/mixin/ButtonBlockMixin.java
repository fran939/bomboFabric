package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@Mixin(ButtonBlock.class)
public class ButtonBlockMixin {

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void onGetShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (BomboConfig.get().dungeonBigHitbox) {
            AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            
            if (face == AttachFace.FLOOR) {
                // Attached to bottom face
                cir.setReturnValue(Shapes.box(0.0, 0.0, 0.0, 1.0, 0.125, 1.0));
            } else if (face == AttachFace.CEILING) {
                // Attached to top face
                cir.setReturnValue(Shapes.box(0.0, 0.875, 0.0, 1.0, 1.0, 1.0));
            } else if (face == AttachFace.WALL) {
                switch (facing) {
                    case NORTH:
                        // Attached to South wall (pointing North)
                        cir.setReturnValue(Shapes.box(0.0, 0.0, 0.875, 1.0, 1.0, 1.0));
                        break;
                    case SOUTH:
                        // Attached to North wall (pointing South)
                        cir.setReturnValue(Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.125));
                        break;
                    case WEST:
                        // Attached to East wall (pointing West)
                        cir.setReturnValue(Shapes.box(0.875, 0.0, 0.0, 1.0, 1.0, 1.0));
                        break;
                    case EAST:
                        // Attached to West wall (pointing East)
                        cir.setReturnValue(Shapes.box(0.0, 0.0, 0.0, 0.125, 1.0, 1.0));
                        break;
                    default:
                        cir.setReturnValue(Shapes.block());
                        break;
                }
            } else {
                cir.setReturnValue(Shapes.block());
            }
        }
    }
}
