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
 * <p><b>Both keep the same Fabric mod id</b> ({@link #MOD_ID}). That is deliberate: mod
 * ids do not leak to servers, every asset/lang/texture lookup keeps working unchanged, and
 * because the two jars cannot be installed side by side Fabric refuses the duplicate,
 * which removes a whole class of "which jar am I running" bugs.
 *
 * <p>The flavor is therefore signalled by a small marker resource that only the cheat build
 * bundles, and the two artifacts are distinguished by {@link #artifactPrefix()}.
 *
 * <p>The config directory is shared between flavors on purpose so switching jars never
 * loses a user's settings.
 */
public final class Constants {

    /** Fabric mod id - identical in both jars. */
    public static final String MOD_ID = "bomboaddons";

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
        MOD_NAME = CHEAT_FLAVOR ? "BomboClient" : "BomboAddons";
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
