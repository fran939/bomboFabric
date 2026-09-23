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
        // Both builds ship the sequence runtime - the user authors and runs sequences on
        // their own client, so there is nothing cheat-specific to gate away.
        me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.init();
    }

    @Override
    public void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> builder) {
        // Intentionally empty - /b hide and /b auto do not exist in this flavor.
    }

    @Override
    public boolean onInputTrigger(int code, String kind, String label) {
        return me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.onInputTrigger(code, kind, label);
    }

    @Override
    public void toggleSequenceByIndex(int index, String kind, String label) {
        me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.toggleByIndex(index, kind, label);
    }

    @Override
    public void stopAll() {
        me.bombo.bomboaddons.features.auto.AutoSequenceExecutor.stopAll("Command");
    }

    @Override
    public boolean hasSequences() {
        return !me.bombo.bomboaddons.features.auto.AutoSequenceManager.getSequences().isEmpty();
    }
}
