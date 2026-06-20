package me.bombo.bomboaddons;

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
        Blocks.STAINED_GLASS.green().defaultBlockState(),
        Blocks.STAINED_GLASS.yellow().defaultBlockState(),
        Blocks.STAINED_GLASS.blue().defaultBlockState(),
        Blocks.STAINED_GLASS.red().defaultBlockState()
    };

    private static int tickCounter = 0;
    public static volatile boolean inDungeonsOrPrivateIsland = false;

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
        if (mc.level == null || mc.player == null) {
            inDungeonsOrPrivateIsland = false;
            return;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.fuckDiorite) {
            inDungeonsOrPrivateIsland = false;
            return;
        }

        tickCounter++;
        if (tickCounter % 10 != 0) return; // Run every 10 ticks (0.5 seconds)

        String area = SkyblockUtils.getLocation();
        inDungeonsOrPrivateIsland = "Dungeons".equals(area) || "Private Island".equals(area);
        if (inDungeonsOrPrivateIsland) {
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
            case "WHITE" -> Blocks.STAINED_GLASS.white().defaultBlockState();
            case "ORANGE", "GOLD" -> Blocks.STAINED_GLASS.orange().defaultBlockState();
            case "MAGENTA" -> Blocks.STAINED_GLASS.magenta().defaultBlockState();
            case "LIGHT_BLUE", "AQUA" -> Blocks.STAINED_GLASS.lightBlue().defaultBlockState();
            case "YELLOW" -> Blocks.STAINED_GLASS.yellow().defaultBlockState();
            case "LIME" -> Blocks.STAINED_GLASS.lime().defaultBlockState();
            case "PINK", "LIGHT_PURPLE" -> Blocks.STAINED_GLASS.pink().defaultBlockState();
            case "GRAY", "DARK_GRAY" -> Blocks.STAINED_GLASS.gray().defaultBlockState();
            case "LIGHT_GRAY" -> Blocks.STAINED_GLASS.lightGray().defaultBlockState();
            case "CYAN", "DARK_AQUA" -> Blocks.STAINED_GLASS.cyan().defaultBlockState();
            case "PURPLE", "DARK_PURPLE" -> Blocks.STAINED_GLASS.purple().defaultBlockState();
            case "BLUE", "DARK_BLUE" -> Blocks.STAINED_GLASS.blue().defaultBlockState();
            case "BROWN" -> Blocks.STAINED_GLASS.brown().defaultBlockState();
            case "GREEN", "DARK_GREEN" -> Blocks.STAINED_GLASS.green().defaultBlockState();
            case "RED", "DARK_RED" -> Blocks.STAINED_GLASS.red().defaultBlockState();
            case "BLACK" -> Blocks.STAINED_GLASS.black().defaultBlockState();
            default -> Blocks.GLASS.defaultBlockState();
        };
    }
}
