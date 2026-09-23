package me.bombo.bomboaddons;

import java.io.InputStream;

/**
 * Single source of truth for BomboAddons' identity and build flavor.
 *
 * <p>The same codebase is compiled into two artifacts:
 * <ul>
 *   <li>{@code bomboaddons-<version>.jar} - legit flavor</li>
 *   <li>{@code bomboclient-<version>.jar} - cheat flavor</li>
 * </ul>
 *
 * <p><b>The two jars use different Fabric mod ids</b> ({@link #MOD_ID}):
 * {@code bomboaddons} for the legit build and {@code bomboclient} for the cheat build. That is
 * deliberate and mirrors what Odin had to scramble to do after a mod-id blacklist ban wave -
 * a block on one id cannot take the other flavor down with it, and a client can still move
 * between them.
 *
 * <p>The asset namespace stays {@link #ASSET_NAMESPACE} ({@code bomboaddons}) in both jars, so
 * every texture/model/lang lookup keeps working unchanged regardless of the mod id.
 *
 * <p>The flavor is signalled by a small marker resource that only the cheat build bundles,
 * and the two artifacts are distinguished by {@link #artifactPrefix()}.
 *
 * <p>The config directory is shared between flavors on purpose so switching jars never
 * loses a user's settings.
 */
public final class Constants {

    /** Resource namespace for every asset this mod ships. Same in both jars. */
    public static final String ASSET_NAMESPACE = "bomboaddons";

    /** Fabric mod id of the legit build. */
    public static final String LEGIT_MOD_ID = "bomboaddons";

    /** Fabric mod id of the cheat build. */
    public static final String CHEAT_MOD_ID = "bomboclient";

    /** Fabric mod id of the running jar. */
    public static final String MOD_ID;

    /** Config directory name. Shared across flavors on purpose. */
    public static final String CONFIG_DIR = "bomboaddons";

    public static final String FLAVOR_LEGIT = "legit";
    public static final String FLAVOR_CHEAT = "cheat";

    /** Marker resource bundled only by the cheat build. */
    private static final String FLAVOR_MARKER = "/bomboaddons.flavor";

    /** {@link #FLAVOR_LEGIT} or {@link #FLAVOR_CHEAT}. */
    public static final String FLAVOR;

    /** True when this artifact is the cheat build. */
    public static final boolean CHEAT_FLAVOR;

    /** Human readable name shown by Fabric. */
    public static final String MOD_NAME;

    static {
        FLAVOR = detectFlavor();
        CHEAT_FLAVOR = FLAVOR_CHEAT.equals(FLAVOR);
        MOD_ID = CHEAT_FLAVOR ? CHEAT_MOD_ID : LEGIT_MOD_ID;
        MOD_NAME = CHEAT_FLAVOR ? "BomboClient" : "BomboAddons";
    }

    /**
     * The mod id of the <i>other</i> flavor. Used to detect both jars being installed at once,
     * which would load every class twice and produce impossible bugs.
     */
    public static String otherModId() {
        return CHEAT_FLAVOR ? LEGIT_MOD_ID : CHEAT_MOD_ID;
    }

    /**
     * Version of the running jar, resolved from its own mod container.
     *
     * <p>Always use this instead of hardcoding a mod id: the two flavors have different ids,
     * so a literal {@code getModContainer("bomboaddons")} returns empty on the cheat build and
     * the old {@code .get()} call would throw.
     */
    public static String myVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** True when the opposite flavor is also loaded (both jars installed). */
    public static boolean otherFlavorLoaded() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(otherModId());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Short human label for the running jar, e.g. {@code BomboClient (bomboclient)}. */
    public static String identityLine() {
        return MOD_NAME + " (" + MOD_ID + ")";
    }


    private static String detectFlavor() {
        try (InputStream in = Constants.class.getResourceAsStream(FLAVOR_MARKER)) {
            if (in != null) {
                return FLAVOR_CHEAT;
            }
        } catch (Throwable ignored) {
            // Treat any failure as the safer legit outcome.
        }
        return FLAVOR_LEGIT;
    }

    /**
     * Published jar name prefix for this flavor, e.g. {@code bomboaddons} for
     * {@code bomboaddons-26.2.28.33.jar}. Used by the updater to select its own
     * artifact and to scope old-jar cleanup.
     */
    public static String artifactPrefix() {
        return CHEAT_FLAVOR ? "bomboclient" : "bomboaddons";
    }

    /** {@link #artifactPrefix()} with the version separator: {@code bomboaddons-}. */
    public static String artifactFilePrefix() {
        return artifactPrefix() + "-";
    }

    private Constants() {
    }
}
