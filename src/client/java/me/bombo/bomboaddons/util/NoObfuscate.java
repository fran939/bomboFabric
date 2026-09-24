package me.bombo.bomboaddons.util;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes the obfuscated ({@code §k}) style so scrambled text becomes readable.
 *
 * <p>Servers and plugins use {@code §k} to hide text that is still physically present in
 * the component - it is only the client's renderer that replaces each glyph with a random
 * character. Dropping the style reveals the real text without touching anything else: colours,
 * bold/italic, click and hover events are all preserved so links keep working.
 *
 * <p>Two cases must <em>not</em> be revealed, which is why the strip is decided per line
 * rather than per component:
 * <ul>
 *   <li><b>Rarity-header padding.</b> Recombobulated items wrap a throwaway glyph in
 *       {@code §k} next to the item name, e.g.
 *       {@code §d§l§ka§r§5 §r§d§lMYTHIC DUNGEON SWORD §ka§r§7}. Those short runs are part of
 *       the intended look, so a line that shows a rarity keyword keeps them.</li>
 *   <li><b>Dummy markers.</b> Placeholders such as {@code §9§kObfuscated-3} carry no real
 *       information - they are revealed (stripped) like any other hidden text.</li>
 * </ul>
 */
public final class NoObfuscate {

    /** Placeholder obfuscation that is never worth preserving. */
    private static final Pattern DUMMY_MARKER = Pattern.compile(
            "(?i)^(obfuscated|hidden|masked|secret|redacted|dummy)[-_ ]?\\d*$");

    /** Item-name rarity keywords that mark a lore line as a rarity header. */
    private static final Pattern RARITY_HEADER = Pattern.compile(
            "(?i)\\b(MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON|DIVINE|SUPREME|SPECIAL|ADMIN)\\b");

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
     * Returns a copy of {@code component} with the obfuscated style cleared where it hides real
     * text, or the original instance when there is nothing to strip (so callers can compare by
     * identity and skip work).
     */
    public static Component strip(Component component) {
        if (component == null) return null;
        if (!hasObfuscated(component)) return component;
        boolean rarityHeader = RARITY_HEADER.matcher(component.getString()).find();
        return rebuild(component, rarityHeader);
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
     * Rebuilds a component tree with the obfuscation cleared where it should be.
     *
     * <p>Built from {@code contents} + style rather than {@code copy()} so the result never
     * shares a mutable sibling list with the original - chat components are cached and reused
     * by the client, and mutating one in place would corrupt later renders.
     */
    private static Component rebuild(Component source, boolean rarityHeader) {
        MutableComponent out = MutableComponent.create(source.getContents());
        out.setStyle(clean(source.getStyle(), ownText(source), rarityHeader));
        for (Component sibling : source.getSiblings()) {
            out.append(rebuild(sibling, rarityHeader));
        }
        return out;
    }

    /** The text this node contributes on its own, excluding its siblings. */
    private static String ownText(Component component) {
        String full = component.getString();
        if (full == null || full.isEmpty()) return "";
        int siblingLength = 0;
        for (Component sibling : component.getSiblings()) {
            String s = sibling.getString();
            if (s != null) siblingLength += s.length();
        }
        if (siblingLength <= 0) return full;
        return full.length() > siblingLength ? full.substring(0, full.length() - siblingLength) : "";
    }

    private static Style clean(Style style, String text, boolean rarityHeader) {
        Style s = style != null ? style : Style.EMPTY;
        if (!s.isObfuscated()) return s;
        return shouldKeepObfuscated(text, rarityHeader) ? s : s.withObfuscated(false);
    }

    /**
     * Rarity headers pad a single throwaway character with {@code §k}; keep those so the item
     * name still looks like the server intended. Everything else (and every dummy marker) is
     * revealed.
     */
    private static boolean shouldKeepObfuscated(String text, boolean rarityHeader) {
        String visible = text == null ? "" : text.trim();
        if (visible.isEmpty()) return false;
        if (DUMMY_MARKER.matcher(visible).matches()) return false;
        if (!rarityHeader) return false;
        if (visible.length() > 3) return false;
        for (int i = 0; i < visible.length(); i++) {
            char c = visible.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    private NoObfuscate() {
    }
}
