package me.bombo.bomboaddons.flavor;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * The narrow surface through which shared code reaches flavor specific behaviour.
 *
 * <p><b>Why this exists:</b> cheat modules live in a separate source set
 * ({@code src/cheat/java}) that is only packaged into the {@code bomboclient} jar, so
 * shared code cannot reference them directly. Everything the shared code needs is
 * declared here and resolved reflectively by {@link Flavor}.
 *
 * <p>Every implementation must stay null-safe: the bridge is called from tick loops,
 * mixin entry points and screens.
 */
public interface FlavorBridge {

    /** True when this jar is the cheat build ({@code bomboclient}). */
    boolean isCheat();

    /** Called once from {@code BomboaddonsClient.onInitializeClient}. */
    void init();

    /**
     * Lets the flavor contribute {@code /b} subcommands. The legit flavor contributes
     * nothing, so commands such as {@code /b hide} simply do not exist in that jar.
     */
    void registerCommands(LiteralArgumentBuilder<FabricClientCommandSource> builder);

    /**
     * Called from the keyboard/mouse mixins with the raw key or mouse code.
     *
     * @param kind  {@code "Keybind"} or {@code "Mouse Button"}
     * @param label human readable trigger, e.g. {@code "TAB"} or {@code "Mouse 5"}
     * @return true when a flavor feature consumed the input
     */
    boolean onInputTrigger(int code, String kind, String label);

    /** Toggle a saved sequence by index, recording the trigger source in chat history. */
    void toggleSequenceByIndex(int index, String kind, String label);

    /** Stop whatever the flavor is currently running. */
    void stopAll();

    /** True when the flavor registered any sequences (cheat flavor only). */
    boolean hasSequences();
}
