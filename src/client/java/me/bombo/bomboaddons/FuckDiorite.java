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
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState();
               break;
            case "ORANGE":
            case "GOLD":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.ORANGE).defaultBlockState();
               break;
            case "MAGENTA":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.MAGENTA).defaultBlockState();
               break;
            case "LIGHT_BLUE":
            case "AQUA":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE).defaultBlockState();
               break;
            case "YELLOW":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState();
               break;
            case "LIME":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIME).defaultBlockState();
               break;
            case "PINK":
            case "LIGHT_PURPLE":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PINK).defaultBlockState();
               break;
            case "GRAY":
            case "DARK_GRAY":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GRAY).defaultBlockState();
               break;
            case "LIGHT_GRAY":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.LIGHT_GRAY).defaultBlockState();
               break;
            case "CYAN":
            case "DARK_AQUA":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.CYAN).defaultBlockState();
               break;
            case "PURPLE":
            case "DARK_PURPLE":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.PURPLE).defaultBlockState();
               break;
            case "BLUE":
            case "DARK_BLUE":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLUE).defaultBlockState();
               break;
            case "BROWN":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BROWN).defaultBlockState();
               break;
            case "GREEN":
            case "DARK_GREEN":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GREEN).defaultBlockState();
               break;
            case "RED":
            case "DARK_RED":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState();
               break;
            case "BLACK":
               var10000 = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
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
      PILLAR_GLASS_STATES = new BlockState[]{Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.GREEN).defaultBlockState(), Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.YELLOW).defaultBlockState(), Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.BLUE).defaultBlockState(), Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()};
      tickCounter = 0;
      inDungeonsOrPrivateIsland = false;
   }
}
