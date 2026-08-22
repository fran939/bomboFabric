package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class DwarvenCarpetReplacer {
   private static int tickCounter = 0;

   public static void tick(Minecraft mc) {
      if (mc == null || mc.level == null || mc.player == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.replaceGrayCarpetDwarven) return;

      if (++tickCounter % 4 != 0) return; // Run every 4 ticks

      String area = SkyblockUtils.getLocation();
      if (area == null) area = BomboaddonsClient.currentArea;
      if (area != null && !area.toLowerCase().contains("dwarven") && !area.toLowerCase().contains("mines")) {
         return;
      }

      BlockPos pPos = mc.player.blockPosition();
      int radius = 24;
      BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
      for (int x = -radius; x <= radius; x++) {
         for (int z = -radius; z <= radius; z++) {
            for (int y = -8; y <= 8; y++) {
               mpos.set(pPos.getX() + x, pPos.getY() + y, pPos.getZ() + z);
               if (mc.level.isLoaded(mpos)) {
                  BlockState state = mc.level.getBlockState(mpos);
                  if (state.is(Blocks.GRAY_CARPET) || state.is(Blocks.LIGHT_GRAY_CARPET)) {
                     mc.level.setBlock(mpos.immutable(), Blocks.RED_CARPET.defaultBlockState(), 19);
                  }
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
      if (area == null) area = BomboaddonsClient.currentArea;
      if (area != null && (area.toLowerCase().contains("dwarven") || area.toLowerCase().contains("mines"))) {
         return Blocks.RED_CARPET.defaultBlockState();
      }
      return state;
   }
}
