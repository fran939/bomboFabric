package me.bombo.bomboaddons.flavor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The legit ({@code bomboaddons}) flavour: deliberately does nothing.
 *
 * <p>No automation executor is registered, no cheat subcommands exist, and no
 * sequence triggers are consumed. This class is present in both jars, which is what
 * lets shared code hold a bridge reference without ever naming a cheat type.
 */
public final class LegitFlavor implements FlavorBridge {

    @Override
    public boolean isCheat() {
        return false;
    }

    @Override
    public void init() {
        // Intentionally empty - the legit build ships no automation runtime.
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        // Intentionally empty - /b hide and /b auto do not exist in this flavor.
    }

    @Override
    public boolean onInputTrigger(int code, String kind, String label) {
        return false;
    }

    @Override
    public void toggleSequenceByIndex(int index, String kind, String label) {
        // Nothing to toggle in the legit build.
    }

    @Override
    public void stopAll() {
        // Nothing is ever running in the legit build.
    }

    @Override
    public boolean hasSequences() {
        return false;
    }
}
