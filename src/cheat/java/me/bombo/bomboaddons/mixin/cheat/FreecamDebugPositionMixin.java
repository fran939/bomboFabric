package me.bombo.bomboaddons.mixin.cheat;

import java.util.List;
import java.util.Locale;
import me.bombo.bomboaddons.cheat.camera.FreecamManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the F3 position block follow the freecam camera instead of the player.
 *
 * <p>Vanilla {@code DebugEntryPosition#display} derives every line from
 * {@code Minecraft.getCameraEntity()}. While freecam is active the detached camera is
 * nowhere near the player, so the debug overlay used to report the player's coordinates -
 * which made it useless for reading coordinates out of a freecam screenshot. This mixin
 * re-renders the same group from {@link FreecamManager}'s camera state and cancels the
 * vanilla body, so the numbers always describe what the screen is actually looking at.
 *
 * <p>Cheat-only: it ships in {@code bomboclient} (see {@code bomboclient.client.mixins.json}).
 */
@Mixin(DebugEntryPosition.class)
public abstract class FreecamDebugPositionMixin {

    @Inject(method = "display", at = @At("HEAD"), cancellable = true, require = 0)
    private void bombo$positionFromFreecamCamera(DebugScreenDisplayer displayer, Level level,
                                                 LevelChunk firstChunk, LevelChunk secondChunk, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (level == null || !FreecamManager.isFreecamActive()) {
            return;
        }

        double camX = FreecamManager.getCamX();
        double camY = FreecamManager.getCamY();
        double camZ = FreecamManager.getCamZ();
        float camYaw = FreecamManager.getCamYaw();
        float camPitch = FreecamManager.getCamPitch();

        BlockPos pos = BlockPos.containing(camX, camY, camZ);
        ChunkPos chunk = ChunkPos.containing(pos);
        Direction facing = Direction.fromYRot(camYaw);

        displayer.addToGroup(DebugEntryPosition.GROUP, List.of(
                String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", camX, camY, camZ),
                String.format(Locale.ROOT, "Block: %d %d %d", pos.getX(), pos.getY(), pos.getZ()),
                String.format(Locale.ROOT, "Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                        chunk.x(), SectionPos.blockToSectionCoord(pos.getY()), chunk.z(),
                        chunk.getRegionLocalX(), chunk.getRegionLocalZ(), chunk.getRegionX(), chunk.getRegionZ()),
                String.format(Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)",
                        facingDescription(facing), facing.getName(),
                        Mth.wrapDegrees(camYaw), Mth.wrapDegrees(camPitch)),
                "Dimension: " + level.dimension().identifier() + " §7(freecam camera)"
        ));

        ci.cancel();
    }

    /** Same wording vanilla uses for the compass-style "Towards negative X" suffix. */
    private static String facingDescription(Direction direction) {
        return switch (direction) {
            case NORTH -> "Towards negative Z";
            case SOUTH -> "Towards positive Z";
            case WEST -> "Towards negative X";
            case EAST -> "Towards positive X";
            default -> "Invalid";
        };
    }
}
