package me.bombo.bomboaddons.features.profile;

import com.mojang.blaze3d.platform.InputConstants;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.CustomBindsProcessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-scope vanilla key bindings ("Profile Controls").
 *
 * <p>Each scope - either a Bombo profile (the same profiles used by {@code /b profile}) or a
 * Skyblock dungeon class (Berserk / Mage / Archer / Tank / Healer) - can re-map any vanilla
 * {@link KeyMapping}. Overrides are stored by translation key (e.g. {@code key.jump}) so they
 * survive Minecraft updates, and keys use the same friendly names the rest of the mod already
 * uses ({@code x}, {@code f5}, {@code mouse4}, ...).
 *
 * <p>Which scope is <em>applied</em> is automatic: inside a dungeon with a detected class the
 * class scope wins (see {@link #activeScope()}), otherwise the active profile applies. Which scope
 * the editor <em>targets</em> is independent ({@link #editScope()}), so you can set up a class's
 * keys without being that class.
 *
 * <p>Applying is idempotent: on the first apply the vanilla keys are snapshotted, and afterwards
 * every mapping is set to "override for the active scope, else the snapshotted vanilla key".
 */
public final class ProfileKeybindManager {

    public static final String NONE = "unbound";

    /** Dungeon class scopes, in the order the editor lists them. */
    public static final List<String> CLASS_SCOPES = List.of("Berserk", "Mage", "Archer", "Tank", "Healer");

    private static final long CLASS_CACHE_MS = 1000L;

    /** Vanilla key each mapping had before Bombo touched anything, captured on first apply. */
    private static final Map<String, InputConstants.Key> VANILLA_KEYS = new LinkedHashMap<>();
    private static String appliedScope = null;
    private static boolean captured = false;

    /** Scope the editor targets; null means "follow the currently applied scope". */
    private static String editScopeOverride = null;

    private static String cachedClass = "";
    private static long lastClassCheck = 0L;

    private ProfileKeybindManager() {
    }

    /** Called from the client tick; cheap early-out unless the applied scope changed. */
    public static void tick(Minecraft mc) {
        if (mc == null || mc.options == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;
        String scope = activeScope();
        String cacheKey = (s.profileKeyControlsEnabled ? "on:" : "off:") + scope;
        if (cacheKey.equals(appliedScope)) return;
        apply(mc, s, scope);
    }

    /** Forces a re-apply right now (used after the user edits an override). */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;
        apply(mc, s, activeScope());
    }

    // ---------------------------------------------------------------------------------------------
    // Scope resolution
    // ---------------------------------------------------------------------------------------------

    /** The scope whose overrides are applied right now (class in a dungeon, else the profile). */
    public static String activeScope() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return "default";
        if (s.autoClassKeybinds) {
            String cls = detectedClassCached();
            if (cls != null && !cls.isEmpty()) return cls;
        }
        return activeProfile(s);
    }

    /** The scope the editor targets. Defaults to {@link #activeScope()} when nothing is pinned. */
    public static String editScope() {
        if (editScopeOverride != null && !editScopeOverride.isEmpty()) return editScopeOverride;
        return activeScope();
    }

    public static void setEditScope(String scope) {
        editScopeOverride = scope == null || scope.isEmpty() ? null : scope;
    }

    /** Follows the automatically applied scope again. */
    public static void clearEditScope() {
        editScopeOverride = null;
    }

    public static boolean isEditScopePinned() {
        return editScopeOverride != null && !editScopeOverride.isEmpty();
    }

    /** All scopes that can be edited: the active profile, "General", and every dungeon class. */
    public static List<String> editableScopes() {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(activeProfile());
        scopes.add("General");
        scopes.addAll(CLASS_SCOPES);
        return new ArrayList<>(scopes);
    }

    private static String detectedClassCached() {
        long now = System.currentTimeMillis();
        if (now - lastClassCheck < CLASS_CACHE_MS) return cachedClass;
        lastClassCheck = now;
        try {
            String cls = AutoProfileSwapper.getDetectedDungeonClass();
            cachedClass = cls == null ? "" : cls.trim();
        } catch (Throwable ignored) {
            cachedClass = "";
        }
        return cachedClass;
    }

    public static String activeProfile() {
        return activeProfile(BomboConfig.get());
    }

    private static String activeProfile(BomboConfig.Settings s) {
        if (s == null || s.activeProfile == null || s.activeProfile.isEmpty()) return "default";
        return s.activeProfile;
    }

    // ---------------------------------------------------------------------------------------------
    // Override storage
    // ---------------------------------------------------------------------------------------------

    /** Overrides for a scope; the map is created when {@code create} is true. */
    public static Map<String, String> overridesFor(String scope, boolean create) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return null;
        if (s.profileKeyOverrides == null) {
            if (!create) return null;
            s.profileKeyOverrides = new LinkedHashMap<>();
        }
        String key = scope == null || scope.isEmpty() ? "default" : scope;
        Map<String, String> map = s.profileKeyOverrides.get(key);
        if (map == null && create) {
            map = new LinkedHashMap<>();
            s.profileKeyOverrides.put(key, map);
        }
        return map;
    }

    public static String getOverride(String mappingName) {
        if (mappingName == null) return null;
        Map<String, String> overrides = overridesFor(editScope(), false);
        return overrides == null ? null : overrides.get(mappingName);
    }

    public static boolean hasOverride(String mappingName) {
        String value = getOverride(mappingName);
        return value != null && !value.isEmpty();
    }

    public static void setOverride(String mappingName, String keyName) {
        if (mappingName == null || keyName == null || keyName.trim().isEmpty()) return;
        Map<String, String> overrides = overridesFor(editScope(), true);
        if (overrides == null) return;
        overrides.put(mappingName, keyName.trim().toLowerCase(Locale.ROOT));
        BomboConfig.save();
        refresh();
    }

    public static void clearOverride(String mappingName) {
        if (mappingName == null) return;
        String scope = editScope();
        Map<String, String> overrides = overridesFor(scope, false);
        if (overrides == null) return;
        if (overrides.remove(mappingName) == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.profileKeyOverrides != null && overrides.isEmpty()) {
            s.profileKeyOverrides.remove(scope);
        }
        BomboConfig.save();
        refresh();
    }

    public static void clearAllForActiveProfile() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || s.profileKeyOverrides == null) return;
        s.profileKeyOverrides.remove(editScope());
        BomboConfig.save();
        refresh();
    }

    public static int overrideCount() {
        Map<String, String> overrides = overridesFor(editScope(), false);
        return overrides == null ? 0 : overrides.size();
    }

    public static List<KeyMapping> allMappings() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return List.of();
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping != null && mapping.getName() != null) out.add(mapping);
        }
        return out;
    }

    /** Human readable name of a mapping ("Jump", "Sprint"). */
    public static String displayName(KeyMapping mapping) {
        if (mapping == null) return "";
        try {
            String name = Component.translatable(mapping.getName()).getString();
            return name == null || name.isEmpty() ? mapping.getName() : name;
        } catch (Throwable ignored) {
            return mapping.getName();
        }
    }

    /** The key a mapping currently reacts to ("Space", "Left Shift"), or "Unbound". */
    public static String displayKey(KeyMapping mapping) {
        if (mapping == null) return "";
        try {
            return mapping.getTranslatedKeyMessage().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** The vanilla key the mapping had before Bombo changed it, for the reference column. */
    public static String vanillaKeyName(String mappingName) {
        InputConstants.Key key = VANILLA_KEYS.get(mappingName);
        return key == null ? null : friendlyName(key);
    }

    private static void apply(Minecraft mc, BomboConfig.Settings s, String scope) {
        KeyMapping[] mappings = mc.options.keyMappings;
        if (mappings == null) return;

        if (!captured) {
            for (KeyMapping mapping : mappings) {
                if (mapping == null || mapping.getName() == null) continue;
                InputConstants.Key key = readKey(mapping);
                if (key != null) VANILLA_KEYS.put(mapping.getName(), key);
            }
            captured = true;
        }

        Map<String, String> overrides = s.profileKeyControlsEnabled ? overridesFor(scope, false) : null;
        boolean changed = false;
        for (KeyMapping mapping : mappings) {
            if (mapping == null || mapping.getName() == null) continue;
            String name = mapping.getName();

            InputConstants.Key target = null;
            String override = overrides == null ? null : overrides.get(name);
            if (override != null && !override.isEmpty()) {
                target = keyFromName(override);
            }
            if (target == null) target = VANILLA_KEYS.get(name);
            if (target == null) continue;

            InputConstants.Key current = readKey(mapping);
            if (current != null && current.equals(target)) continue;

            mapping.setKey(target);
            changed = true;
        }

        if (changed) {
            KeyMapping.resetMapping();
            // Deliberately NOT mc.options.save(): persisting a scope's overrides into options.txt
            // would make the next launch snapshot the overridden keys as "vanilla", so switching
            // to a scope without overrides would restore the wrong keys. The overrides live in the
            // Bombo config and are re-applied every tick instead.
        }
        appliedScope = (s.profileKeyControlsEnabled ? "on:" : "off:") + scope;
    }

    /** Converts a stored friendly key name into an {@link InputConstants.Key}. */
    public static InputConstants.Key keyFromName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if (clean.equals(NONE) || clean.equals("none") || clean.equals("unknown")) {
            return InputConstants.UNKNOWN;
        }
        int code = CustomBindsProcessor.getGlfwCodeForName(clean);
        if (code >= 1000 && code <= 1007) {
            return InputConstants.Type.MOUSE.getOrCreate(code - 1000);
        }
        if (code >= 0) {
            return InputConstants.Type.KEYSYM.getOrCreate(code);
        }
        return null;
    }

    /** Friendly name for a captured GLFW/mouse code, stored in the config. */
    public static String nameFromCode(int code) {
        return CustomBindsProcessor.getKeyNameForGlfwCode(code);
    }

    private static String friendlyName(InputConstants.Key key) {
        if (key == null || key == InputConstants.UNKNOWN) return "Unbound";
        try {
            return key.getDisplayName().getString();
        } catch (Throwable ignored) {
            return key.getName();
        }
    }

    /** Reads the private {@code key} field of a {@link KeyMapping} the same way the conflict checker does. */
    private static InputConstants.Key readKey(KeyMapping mapping) {
        try {
            Class<?> type = mapping.getClass();
            while (type != null) {
                for (Field field : type.getDeclaredFields()) {
                    if (InputConstants.Key.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return (InputConstants.Key) field.get(mapping);
                    }
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** All mapping names, for {@code /b keymaps list}. */
    public static List<String> mappingNames() {
        List<String> names = new ArrayList<>();
        for (KeyMapping mapping : allMappings()) {
            names.add(mapping.getName());
        }
        return names;
    }
}
