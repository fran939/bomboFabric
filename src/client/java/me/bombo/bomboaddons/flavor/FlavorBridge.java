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

    // -----------------------------------------------------------------------
    // Freecam
    //
    // The camera hooks live in shared mixins so the routing (which camera, which
    // rotation) is identical in both builds; only the freecam state itself is
    // flavor owned. The legit implementation reports "inactive", so every hook
    // becomes a no-op there and no cheat class is needed.
    // -----------------------------------------------------------------------

    /** True while the freecam camera is detached from the player. */
    boolean isFreecamActive();

    /** Camera yaw while freecam is active (degrees). */
    float freecamYaw();

    /** Camera pitch while freecam is active (degrees). */
    float freecamPitch();

    /** Camera X position while freecam is active. */
    double freecamX();

    /** Camera Y position while freecam is active. */
    double freecamY();

    /** Camera Z position while freecam is active. */
    double freecamZ();

    /** Applies a mouse movement delta to the freecam rotation. */
    void freecamRotate(double deltaX, double deltaY);

    /** Toggles freecam on or off. */
    void freecamToggle();

    /** Enables or disables freecam explicitly (used on join/leave and death). */
    void freecamToggle(boolean state);

    /** Per-tick freecam movement update. */
    void freecamTick();
}
