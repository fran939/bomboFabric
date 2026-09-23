package me.bombo.bomboaddons.flavor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Constants;
import me.bombo.bomboaddons.features.chat.ChatHistoryTracker;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-time reconciliation between the jar that is actually running and the config on disk.
 *
 * <p>Two things need fixing for existing users:
 * <ol>
 *   <li><b>Legacy cheat state.</b> Before the two flavors existed there was one jar and
 *       {@code hideCheats} was the only switch. Users who had cheats enabled
 *       ({@code hideCheats = false}) or had automation configured get recognised and moved
 *       onto the cheat flavor, so nothing they had set up silently stops working.</li>
 *   <li><b>Forced legit behaviour.</b> If the legit jar is launched over a cheat profile,
 *       {@code hideCheats} is forced back on - the legit build has no automation runtime, and
 *       leaving that flag off would imply features that are not compiled in.</li>
 * </ol>
 *
 * <p>Nothing is ever deleted: values belonging to the other flavor are preserved so switching
 * back and forth never loses settings.
 */
public final class FlavorMigration {

    /** Bump when a new migration step is added below. */
    public static final int SCHEMA_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path MARKER_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve(Constants.CONFIG_DIR).resolve("flavor.json");

    private static boolean ran = false;

    public static void run() {
        if (ran) return;
        ran = true;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        boolean dirty = false;

        if (s.configSchemaVersion < SCHEMA_VERSION) {
            boolean legacyCheatUsage = detectLegacyCheatUsage(s);
            s.legacyHideCheats = s.hideCheats;

            if (Constants.CHEAT_FLAVOR) {
                s.flavor = Constants.FLAVOR_CHEAT;
                // The cheat build ships cheat features, so the default there is to show them.
                // Previously this only flipped when legacy cheat usage was detected, which left
                // a fresh cheat install (or one migrated from a legit profile) hiding the very
                // categories that build exists for - the cheats looked like they had vanished.
                s.hideCheats = false;
            } else {
                s.flavor = Constants.FLAVOR_LEGIT;
                if (!s.hideCheats) {
                    s.hideCheats = true;
                }
            }

            s.configSchemaVersion = SCHEMA_VERSION;
            dirty = true;

            String detail = legacyCheatUsage
                    ? "detected a previous cheat profile"
                    : "no previous cheat usage detected";
            ChatHistoryTracker.recordEvent("FLAVOR",
                    "Migrated config to " + Constants.MOD_NAME + " (" + detail + ")",
                    "Flavor Migration", "Startup", "v" + SCHEMA_VERSION);
        } else if (s.flavor != null && !s.flavor.isEmpty() && !s.flavor.equals(Constants.FLAVOR)) {
            // The user swapped jars. Record it so the history explains a behaviour change.
            String previous = s.flavor;
            s.flavor = Constants.FLAVOR;
            dirty = true;
            ChatHistoryTracker.recordEvent("FLAVOR",
                    "Flavor changed: " + previous + " \u2192 " + Constants.FLAVOR + " (" + Constants.MOD_NAME + ")",
                    "Flavor Migration", "Startup", "Jar swap");
        }

        if (dirty) {
            BomboConfig.save();
        }

        warnIfBothFlavorsInstalled();
        writeMarker();
    }

    /**
     * Both jars at once would load every shared class twice and make behaviour impossible to
     * reason about. They now use different Fabric mod ids, so Fabric itself no longer blocks it -
     * this check replaces that implicit guard with an explicit, loud one.
     */
    private static void warnIfBothFlavorsInstalled() {
        if (!Constants.otherFlavorLoaded()) return;

        String message = "Both flavors are installed (" + Constants.MOD_ID + " and "
                + Constants.otherModId() + "). Remove one from the mods folder - running both "
                + "loads this code twice and produces unpredictable behaviour.";
        System.err.println("[Bombo] WARNING: " + message);
        ChatHistoryTracker.recordEvent("FLAVOR", "Two flavors installed at once: " + message,
                "Flavor Check", "Startup", "Duplicate jars");

        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§8[§c!§8] §c" + message));
            }
        } catch (Throwable ignored) {
            // Startup must never break because of a warning.
        }
    }

    /**
     * Legacy signals that the profile was being used for automation. Any one of these is
     * enough; the sequence file is checked last because it exists for every user (it is
     * seeded with a demo sequence) and only counts when a real trigger is configured.
     */
    private static boolean detectLegacyCheatUsage(BomboConfig.Settings s) {
        if (!s.hideCheats) return true;
        if (s.autoFishingEnabled) return true;
        if (s.autoExperiments) return true;
        if (s.anvilAutoCombineEnabled) return true;
        if (s.structureFinder) return true;
        if (s.autoHitman) return true;

        try {
            File seqFile = FabricLoader.getInstance().getConfigDir()
                    .resolve(Constants.CONFIG_DIR).resolve("bomboaddons_auto_sequences.json").toFile();
            if (seqFile.isFile() && seqFile.length() > 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void writeMarker() {
        try {
            Path parent = MARKER_FILE.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            JsonObject obj = new JsonObject();
            obj.addProperty("flavor", Constants.FLAVOR);
            obj.addProperty("modId", Constants.MOD_ID);
            obj.addProperty("modName", Constants.MOD_NAME);
            obj.addProperty("artifactPrefix", Constants.artifactPrefix());
            obj.addProperty("schemaVersion", SCHEMA_VERSION);
            Files.writeString(MARKER_FILE, GSON.toJson(obj), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // The marker is diagnostic only - never let it break startup.
        }
    }

    private FlavorMigration() {
    }
}
