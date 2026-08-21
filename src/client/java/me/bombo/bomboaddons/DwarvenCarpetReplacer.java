package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class DwarvenCarpetReplacer {
   private static int tickCounter = 0;

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.replaceGrayCarpetDwarven) return;

      String area = SkyblockUtils.getLocation();
      if (area == null || !area.toLowerCase().contains("dwarven")) return;

      tickCounter++;
      if (tickCounter % 5 != 0) return;

      BlockPos pPos = mc.player.blockPosition();
      int rH = 24;
      int rV = 10;
      for (int dx = -rH; dx <= rH; dx++) {
         for (int dz = -rH; dz <= rH; dz++) {
            for (int dy = -rV; dy <= rV; dy++) {
               BlockPos pos = pPos.offset(dx, dy, dz);
               BlockState state = mc.level.getBlockState(pos);
               if (state.is(Blocks.GRAY_CARPET) || state.is(Blocks.LIGHT_GRAY_CARPET)) {
                  mc.level.setBlock(pos, Blocks.RED_CARPET.defaultBlockState(), 3);
               }
            }
         }
      }
   }

   public static BlockState checkAndReplace(BlockState state) {
      if (state == null) return state;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.replaceGrayCarpetDwarven) return state;
      if (!state.is(Blocks.GRAY_CARPET) && !state.is(Blocks.LIGHT_GRAY_CARPET)) return state;
      String area = SkyblockUtils.getLocation();
      if (area != null && area.toLowerCase().contains("dwarven")) {
         return Blocks.RED_CARPET.defaultBlockState();
      }
      return state;
   }
}
