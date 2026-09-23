package me.bombo.bomboaddons.util;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * Removes the obfuscated ({@code \u00a7k}) style so scrambled text becomes readable.
 *
 * <p>Servers and plugins use {@code \u00a7k} to hide text that is still physically present in
 * the component - it is only the client's renderer that replaces each glyph with a random
 * character. Dropping the style reveals the real text without touching anything else: colours,
 * bold/italic, click and hover events are all preserved so links keep working.
 *
 * <p>Implemented as a style rewrite rather than a string round-trip, so nothing that relies on
 * component structure (chat history, item tooltips, hover data) is broken in the process.
 */
public final class NoObfuscate {

    public static boolean isEnabled() {
        BomboConfig.Settings s = BomboConfig.get();
        return s != null && s.noObfuscate;
    }

    /** True when this component, or any of its children, carries the obfuscated style. */
    public static boolean hasObfuscated(Component component) {
        if (component == null) return false;
        final boolean[] found = {false};
        component.visit((style, text) -> {
            if (style != null && style.isObfuscated()) {
                found[0] = true;
            }
            return found[0] ? java.util.Optional.of(Component.empty()) : java.util.Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    /**
     * Returns a copy of {@code component} with every obfuscated style cleared, or the original
     * instance when there is nothing to strip (so callers can compare by identity and skip work).
     */
    public static Component strip(Component component) {
        if (component == null) return null;
        if (!hasObfuscated(component)) return component;
        return rebuild(component);
    }

    /** Convenience for tooltip lists: strips in place and returns the same list. */
    public static List<Component> stripAll(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line == null) continue;
            Component clean = strip(line);
            if (clean != line) {
                lines.set(i, clean);
            }
        }
        return lines;
    }

    /**
     * Rebuilds a component tree with cleared obfuscation.
     *
     * <p>Built from {@code contents} + style rather than {@code copy()} so the result never
     * shares a mutable sibling list with the original - chat components are cached and reused
     * by the client, and mutating one in place would corrupt later renders.
     */
    private static Component rebuild(Component source) {
        MutableComponent out = MutableComponent.create(source.getContents());
        out.setStyle(clean(source.getStyle()));
        for (Component sibling : source.getSiblings()) {
            out.append(rebuild(sibling));
        }
        return out;
    }

    private static Style clean(Style style) {
        Style s = style != null ? style : Style.EMPTY;
        return s.isObfuscated() ? s.withObfuscated(false) : s;
    }

    private NoObfuscate() {
    }
}
