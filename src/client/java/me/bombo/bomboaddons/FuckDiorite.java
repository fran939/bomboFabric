package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FuckDiorite {
   private static final BlockPos[] PILLARS = new BlockPos[]{new BlockPos(46, 169, 41), new BlockPos(46, 169, 65), new BlockPos(100, 169, 65), new BlockPos(100, 169, 41)};
   private static final BlockState[] PILLAR_GLASS_STATES;
   private static int tickCounter;
   public static volatile boolean inDungeonsOrPrivateIsland;

   public static BlockState checkAndReplace(BlockPos pos, BlockState state) {
      if (state != null && (state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE))) {
         BomboConfig.Settings s = BomboConfig.get();
         if (!s.fuckDiorite) {
            return state;
         } else {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();

            for(int p = 0; p < 4; ++p) {
               BlockPos pillar = PILLARS[p];
               if (x >= pillar.getX() - 3 && x <= pillar.getX() + 3 && y >= 50 && y <= 220 && z >= pillar.getZ() - 3 && z <= pillar.getZ() + 3) {
                  BlockState customGlass = getSelectedGlassState(s.fuckDioriteColor);
                  return s.fuckDioritePillarColor ? PILLAR_GLASS_STATES[p] : customGlass;
               }
            }

            return state;
         }
      } else {
         return state;
      }
   }

   public static void onTick() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         BomboConfig.Settings s = BomboConfig.get();
         if (!s.fuckDiorite) {
            inDungeonsOrPrivateIsland = false;
         } else {
            ++tickCounter;
            if (tickCounter % 10 == 0) {
               String area = SkyblockUtils.getLocation();
               inDungeonsOrPrivateIsland = "Dungeons".equals(area) || "Private Island".equals(area);
               if (inDungeonsOrPrivateIsland) {
                  replaceDioriteDungeons(mc, s);
               }

            }
         }
      } else {
         inDungeonsOrPrivateIsland = false;
      }
   }

   private static void replaceDioriteDungeons(Minecraft mc, BomboConfig.Settings s) {
      BlockState customGlass = getSelectedGlassState(s.fuckDioriteColor);

      for(int p = 0; p < 4; ++p) {
         BlockPos pillar = PILLARS[p];
         BlockState targetGlass = s.fuckDioritePillarColor ? PILLAR_GLASS_STATES[p] : customGlass;

         for(int dx = pillar.getX() - 3; dx <= pillar.getX() + 3; ++dx) {
            for(int dy = 120; dy <= 220; ++dy) {
               for(int dz = pillar.getZ() - 3; dz <= pillar.getZ() + 3; ++dz) {
                  BlockPos pos = new BlockPos(dx, dy, dz);
                  BlockState state = mc.level.getBlockState(pos);
                  if (state.is(Blocks.DIORITE) || state.is(Blocks.POLISHED_DIORITE)) {
                     mc.level.setBlock(pos, targetGlass, 3);
                  }
               }
            }
         }
      }

   }

   public static BlockState getSelectedGlassState(String colorName) {
      if (colorName != null && !"NONE".equalsIgnoreCase(colorName)) {
         BlockState var10000;
         switch (colorName.toUpperCase()) {
            case "WHITE":
               var10000 = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
               break;
            case "ORANGE":
            case "GOLD":
               var10000 = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
               break;
            case "MAGENTA":
               var10000 = Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
               break;
            case "LIGHT_BLUE":
            case "AQUA":
               var10000 = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
               break;
            case "YELLOW":
               var10000 = Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
               break;
            case "LIME":
               var10000 = Blocks.LIME_STAINED_GLASS.defaultBlockState();
               break;
            case "PINK":
            case "LIGHT_PURPLE":
               var10000 = Blocks.PINK_STAINED_GLASS.defaultBlockState();
               break;
            case "GRAY":
            case "DARK_GRAY":
               var10000 = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
               break;
            case "LIGHT_GRAY":
               var10000 = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
               break;
            case "CYAN":
            case "DARK_AQUA":
               var10000 = Blocks.CYAN_STAINED_GLASS.defaultBlockState();
               break;
            case "PURPLE":
            case "DARK_PURPLE":
               var10000 = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
               break;
            case "BLUE":
            case "DARK_BLUE":
               var10000 = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
               break;
            case "BROWN":
               var10000 = Blocks.BROWN_STAINED_GLASS.defaultBlockState();
               break;
            case "GREEN":
            case "DARK_GREEN":
               var10000 = Blocks.GREEN_STAINED_GLASS.defaultBlockState();
               break;
            case "RED":
            case "DARK_RED":
               var10000 = Blocks.RED_STAINED_GLASS.defaultBlockState();
               break;
            case "BLACK":
               var10000 = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
               break;
            default:
               var10000 = Blocks.GLASS.defaultBlockState();
         }

         return var10000;
      } else {
         return Blocks.GLASS.defaultBlockState();
      }
   }

   static {
      PILLAR_GLASS_STATES = new BlockState[]{Blocks.GREEN_STAINED_GLASS.defaultBlockState(), Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), Blocks.BLUE_STAINED_GLASS.defaultBlockState(), Blocks.RED_STAINED_GLASS.defaultBlockState()};
      tickCounter = 0;
      inDungeonsOrPrivateIsland = false;
   }
}
