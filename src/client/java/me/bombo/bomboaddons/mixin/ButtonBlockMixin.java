package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ButtonBlock.class})
public class ButtonBlockMixin {
   @Inject(
      method = {"getShape"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
      if (BomboConfig.get().dungeonBigHitbox) {
         AttachFace face = (AttachFace)state.getValue(BlockStateProperties.ATTACH_FACE);
         Direction facing = (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
         if (face == AttachFace.FLOOR) {
            cir.setReturnValue(Shapes.box((double)0.0F, (double)0.0F, (double)0.0F, (double)1.0F, (double)0.125F, (double)1.0F));
         } else if (face == AttachFace.CEILING) {
            cir.setReturnValue(Shapes.box((double)0.0F, (double)0.875F, (double)0.0F, (double)1.0F, (double)1.0F, (double)1.0F));
         } else if (face == AttachFace.WALL) {
            switch (facing) {
               case NORTH -> cir.setReturnValue(Shapes.box((double)0.0F, (double)0.0F, (double)0.875F, (double)1.0F, (double)1.0F, (double)1.0F));
               case SOUTH -> cir.setReturnValue(Shapes.box((double)0.0F, (double)0.0F, (double)0.0F, (double)1.0F, (double)1.0F, (double)0.125F));
               case WEST -> cir.setReturnValue(Shapes.box((double)0.875F, (double)0.0F, (double)0.0F, (double)1.0F, (double)1.0F, (double)1.0F));
               case EAST -> cir.setReturnValue(Shapes.box((double)0.0F, (double)0.0F, (double)0.0F, (double)0.125F, (double)1.0F, (double)1.0F));
               default -> cir.setReturnValue(Shapes.block());
            }
         } else {
            cir.setReturnValue(Shapes.block());
         }
      }

   }
}
