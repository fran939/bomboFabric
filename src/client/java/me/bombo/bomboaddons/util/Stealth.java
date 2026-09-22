package me.bombo.bomboaddons.util;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Constants;

/**
 * Identity / presence hygiene switch for the cheat flavor.
 *
 * <p>When enabled, this build stops telling third parties who is online and where: nothing is
 * POSTed to the Bombo bridge, no location or egg data is published, and the client
 * advertises the vanilla brand on join instead of a modded one. Other BomboAddons users
 * therefore cannot see the player through {@code /b online} or the egg network.
 *
 * <p><b>Scope, honestly:</b> these are client-side identity controls only. They do not - and
 * a client cannot - defeat server-side behavioural analysis, macro heuristics, staff review
 * or player reports. Treat stealth as "stop broadcasting", not "become undetectable".
 *
 * <p>The flag only has an effect on the cheat flavor; the legit build never publishes
 * presence behaviour worth hiding in the first place.
 */
public final class Stealth {

    public static boolean isEnabled() {
        if (!Constants.CHEAT_FLAVOR) return false;
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.stealthMode;
    }

    private Stealth() {
    }
}
