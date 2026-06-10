package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FuckDiorite {
    private static final BlockPos[] PILLARS = {
        new BlockPos(46, 169, 41),
        new BlockPos(46, 169, 65),
        new BlockPos(100, 169, 65),
        new BlockPos(100, 169, 41)
    };

    private static final BlockState[] PILLAR_GLASS_STATES = {
        Blocks.GREEN_STAINED_GLASS.defaultBlockState(),
        Blocks.YELLOW_STAINED_GLASS.defaultBlockState(),
        Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
        Blocks.RED_STAINED_GLASS.defaultBlockState()
    };

    private static int tickCounter = 0;

    public static BlockState checkAndReplace(BlockPos pos, BlockState state) {
        if (state == null || (!state.is(Blocks.DIORITE) && !state.is(Blocks.POLISHED_DIORITE))) {
            return state;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.fuckDiorite) return state;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        for (int p = 0; p < 4; p++) {
            BlockPos pillar = PILLARS[p];
            if (x >= pillar.getX() - 3 && x <= pillar.getX() + 3 &&
                y >= 50 && y <= 220 &&
                z >= pillar.getZ() - 3 && z <= pillar.getZ() + 3) {
                
                BlockState customGlass = getSelectedGlassState(s.fuckDioriteColor);
                return s.fuckDioritePillarColor ? PILLAR_GLASS_STATES[p] : customGlass;
            }
        }

        return state;
    }

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.fuckDiorite) return;

        tickCounter++;
        if (tickCounter % 10 != 0) return; // Run every 10 ticks (0.5 seconds)

        String area = SkyblockUtils.getLocation();
        if ("Dungeons".equals(area) || "Private Island".equals(area)) {
            replaceDioriteDungeons(mc, s);
        }
    }

    private static void replaceDioriteDungeons(Minecraft mc, BomboConfig.Settings s) {
        BlockState customGlass = getSelectedGlassState(s.fuckDioriteColor);

        for (int p = 0; p < 4; p++) {
            BlockPos pillar = PILLARS[p];
            BlockState targetGlass = s.fuckDioritePillarColor ? PILLAR_GLASS_STATES[p] : customGlass;

            for (int dx = pillar.getX() - 3; dx <= pillar.getX() + 3; dx++) {
                for (int dy = 120; dy <= 220; dy++) {
                    for (int dz = pillar.getZ() - 3; dz <= pillar.getZ() + 3; dz++) {
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
        if (colorName == null || "NONE".equalsIgnoreCase(colorName)) {
            return Blocks.GLASS.defaultBlockState();
        }
        return switch (colorName.toUpperCase()) {
            case "WHITE" -> Blocks.WHITE_STAINED_GLASS.defaultBlockState();
            case "ORANGE", "GOLD" -> Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
            case "MAGENTA" -> Blocks.MAGENTA_STAINED_GLASS.defaultBlockState();
            case "LIGHT_BLUE", "AQUA" -> Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
            case "YELLOW" -> Blocks.YELLOW_STAINED_GLASS.defaultBlockState();
            case "LIME" -> Blocks.LIME_STAINED_GLASS.defaultBlockState();
            case "PINK", "LIGHT_PURPLE" -> Blocks.PINK_STAINED_GLASS.defaultBlockState();
            case "GRAY", "DARK_GRAY" -> Blocks.GRAY_STAINED_GLASS.defaultBlockState();
            case "LIGHT_GRAY" -> Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
            case "CYAN", "DARK_AQUA" -> Blocks.CYAN_STAINED_GLASS.defaultBlockState();
            case "PURPLE", "DARK_PURPLE" -> Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
            case "BLUE", "DARK_BLUE" -> Blocks.BLUE_STAINED_GLASS.defaultBlockState();
            case "BROWN" -> Blocks.BROWN_STAINED_GLASS.defaultBlockState();
            case "GREEN", "DARK_GREEN" -> Blocks.GREEN_STAINED_GLASS.defaultBlockState();
            case "RED", "DARK_RED" -> Blocks.RED_STAINED_GLASS.defaultBlockState();
            case "BLACK" -> Blocks.BLACK_STAINED_GLASS.defaultBlockState();
            default -> Blocks.GLASS.defaultBlockState();
        };
    }
}
