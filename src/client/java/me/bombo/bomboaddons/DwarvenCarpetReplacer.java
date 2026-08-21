package me.bombo.bomboaddons;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class DwarvenCarpetReplacer {
   public static BlockState checkAndReplace(BlockState state) {
      if (state == null) return state;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.replaceGrayCarpetDwarven) return state;
      if (!state.is(Blocks.GRAY_CARPET) && !state.is(Blocks.LIGHT_GRAY_CARPET)) return state;
      String area = SkyblockUtils.getLocation();
      if (area == null) area = BomboaddonsClient.currentArea;
      if (area != null && area.toLowerCase().contains("dwarven")) {
         return Blocks.RED_CARPET.defaultBlockState();
      }
      return state;
   }
}
