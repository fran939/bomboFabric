package me.bombo.bomboaddons.flavor;

import me.bombo.bomboaddons.Constants;

/**
 * Resolves the {@link FlavorBridge} for the jar that is currently running.
 *
 * <p>The cheat implementation is looked up <b>by name</b> so shared code never links
 * against it. In the {@code bomboaddons} (legit) jar that class is absent from the
 * classpath, the lookup fails by construction, and {@link LegitFlavor} is used instead.
 * That is what makes "cheat modules completely excluded" a structural property rather
 * than a convention.
 */
public final class Flavor {

    private static final String CHEAT_IMPLEMENTATION = "me.bombo.bomboaddons.flavor.cheat.CheatFlavor";

    private static volatile FlavorBridge instance;

    public static FlavorBridge get() {
        FlavorBridge local = instance;
        if (local == null) {
            synchronized (Flavor.class) {
                local = instance;
                if (local == null) {
                    local = resolve();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static FlavorBridge resolve() {
        if (Constants.CHEAT_FLAVOR) {
            try {
                Class<?> type = Class.forName(CHEAT_IMPLEMENTATION);
                return (FlavorBridge) type.getDeclaredConstructor().newInstance();
            } catch (Throwable t) {
                System.err.println("[Bombo] " + Constants.MOD_ID
                        + " could not load its flavor implementation, falling back to legit behaviour: " + t);
            }
        }
        return new LegitFlavor();
    }

    private Flavor() {
    }
}
