package me.bombo.bomboaddons.gui.config;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public class ConfigItem {
    public enum Type {
        HEADER,
        TOGGLE,
        SLIDER_INT,
        SLIDER_FLOAT,
        CYCLE,
        COLOR,
        KEYBIND,
        TEXT,
        BUTTON,
        SUBMENU,
        CUSTOM_CARD
    }

    @FunctionalInterface
    public interface CustomCardRenderer {
        void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY);
    }

    @FunctionalInterface
    public interface CustomCardClickHandler {
        boolean onClick(int x, int y, int w, int h, int mouseX, int mouseY, int button);
    }

    public final Type type;
    public final String name;
    public final String description;
    public final String category;

    // Callbacks & state providers
    public Supplier<Boolean> boolGetter;
    public Consumer<Boolean> boolSetter;
    public HudMoveScreen.HudTarget hudTarget;

    public Supplier<Integer> intGetter;
    public IntConsumer intSetter;
    public int minInt, maxInt, stepInt;
    public int defaultInt = 0;
    public String intSuffix = "";

    public Supplier<Float> floatGetter;
    public Consumer<Float> floatSetter;
    public float minFloat, maxFloat, stepFloat;
    public float defaultFloat = 0f;
    public String floatSuffix = "";

    public Supplier<String> stringGetter;
    public Consumer<String> stringSetter;
    public List<String> cycleOptions;

    public Runnable action;
    public String buttonText;
    public Runnable submenuOpener;

    // Custom Card Support
    public int cardHeight = 38;
    public Supplier<Integer> dynamicHeightSupplier;
    public CustomCardRenderer customRenderer;
    public CustomCardClickHandler customClickHandler;

    public ConfigItem(Type type, String name, String description, String category) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public int getEffectiveCardHeight() {
        if (dynamicHeightSupplier != null) {
            return dynamicHeightSupplier.get();
        }
        if (type == Type.HEADER) return 24;
        if (type == Type.CUSTOM_CARD) return cardHeight;
        return 38;
    }

    // Static builders for clean definition
    public static ConfigItem header(String title, String category) {
        return new ConfigItem(Type.HEADER, title, null, category);
    }

    public static ConfigItem toggle(String name, String description, String category, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        ConfigItem item = new ConfigItem(Type.TOGGLE, name, description, category);
        item.boolGetter = getter;
        item.boolSetter = setter;
        return item;
    }

    public static ConfigItem hudToggle(String name, String description, String category, Supplier<Boolean> getter, Consumer<Boolean> setter, HudMoveScreen.HudTarget target) {
        ConfigItem item = new ConfigItem(Type.TOGGLE, name, description, category);
        item.boolGetter = getter;
        item.boolSetter = setter;
        item.hudTarget = target;
        return item;
    }

    public static ConfigItem sliderInt(String name, String description, String category, int min, int max, int step, String suffix, Supplier<Integer> getter, IntConsumer setter) {
        return sliderInt(name, description, category, min, max, step, min, suffix, getter, setter);
    }

    public static ConfigItem sliderInt(String name, String description, String category, int min, int max, int step, int defVal, String suffix, Supplier<Integer> getter, IntConsumer setter) {
        ConfigItem item = new ConfigItem(Type.SLIDER_INT, name, description, category);
        item.minInt = min;
        item.maxInt = max;
        item.stepInt = step;
        item.defaultInt = defVal;
        item.intSuffix = suffix != null ? suffix : "";
        item.intGetter = getter;
        item.intSetter = setter;
        return item;
    }

    public static ConfigItem sliderFloat(String name, String description, String category, float min, float max, float step, String suffix, Supplier<Float> getter, Consumer<Float> setter) {
        return sliderFloat(name, description, category, min, max, step, min, suffix, getter, setter);
    }

    public static ConfigItem sliderFloat(String name, String description, String category, float min, float max, float step, float defVal, String suffix, Supplier<Float> getter, Consumer<Float> setter) {
        ConfigItem item = new ConfigItem(Type.SLIDER_FLOAT, name, description, category);
        item.minFloat = min;
        item.maxFloat = max;
        item.stepFloat = step;
        item.defaultFloat = defVal;
        item.floatSuffix = suffix != null ? suffix : "";
        item.floatGetter = getter;
        item.floatSetter = setter;
        return item;
    }

    public static ConfigItem cycle(String name, String description, String category, List<String> options, Supplier<String> getter, Consumer<String> setter) {
        ConfigItem item = new ConfigItem(Type.CYCLE, name, description, category);
        item.cycleOptions = options;
        item.stringGetter = getter;
        item.stringSetter = setter;
        return item;
    }

    public static ConfigItem color(String name, String description, String category, Supplier<String> getter, Consumer<String> setter) {
        ConfigItem item = new ConfigItem(Type.COLOR, name, description, category);
        item.stringGetter = getter;
        item.stringSetter = setter;
        return item;
    }

    public static ConfigItem keybind(String name, String description, String category, Supplier<String> getter, Consumer<String> setter) {
        ConfigItem item = new ConfigItem(Type.KEYBIND, name, description, category);
        item.stringGetter = getter;
        item.stringSetter = setter;
        return item;
    }

    public static ConfigItem keybindWithMode(String name, String description, String category, Supplier<String> keyGetter, Consumer<String> keySetter, Supplier<Boolean> toggleGetter, Consumer<Boolean> toggleSetter) {
        ConfigItem item = new ConfigItem(Type.KEYBIND, name, description, category);
        item.stringGetter = keyGetter;
        item.stringSetter = keySetter;
        item.boolGetter = toggleGetter;
        item.boolSetter = toggleSetter;
        return item;
    }

    public static ConfigItem text(String name, String description, String category, Supplier<String> getter, Consumer<String> setter) {
        ConfigItem item = new ConfigItem(Type.TEXT, name, description, category);
        item.stringGetter = getter;
        item.stringSetter = setter;
        return item;
    }

    public static ConfigItem button(String name, String buttonText, String description, String category, Runnable action) {
        ConfigItem item = new ConfigItem(Type.BUTTON, name, description, category);
        item.buttonText = buttonText;
        item.action = action;
        return item;
    }

    public static ConfigItem submenu(String name, String description, String category, Runnable openAction) {
        ConfigItem item = new ConfigItem(Type.SUBMENU, name, description, category);
        item.submenuOpener = openAction;
        return item;
    }

    public static ConfigItem customCard(String name, String category, int fixedHeight, CustomCardRenderer renderer, CustomCardClickHandler clickHandler) {
        ConfigItem item = new ConfigItem(Type.CUSTOM_CARD, name, null, category);
        item.cardHeight = fixedHeight;
        item.customRenderer = renderer;
        item.customClickHandler = clickHandler;
        return item;
    }

    public static ConfigItem dynamicCustomCard(String name, String category, Supplier<Integer> heightSupplier, CustomCardRenderer renderer, CustomCardClickHandler clickHandler) {
        ConfigItem item = new ConfigItem(Type.CUSTOM_CARD, name, null, category);
        item.dynamicHeightSupplier = heightSupplier;
        item.customRenderer = renderer;
        item.customClickHandler = clickHandler;
        return item;
    }

    public ConfigItem cloneWithCategory(String newCategory) {
        ConfigItem copy = new ConfigItem(this.type, this.name, this.description, newCategory);
        copy.boolGetter = this.boolGetter;
        copy.boolSetter = this.boolSetter;
        copy.hudTarget = this.hudTarget;
        copy.intGetter = this.intGetter;
        copy.intSetter = this.intSetter;
        copy.minInt = this.minInt;
        copy.maxInt = this.maxInt;
        copy.stepInt = this.stepInt;
        copy.defaultInt = this.defaultInt;
        copy.intSuffix = this.intSuffix;
        copy.floatGetter = this.floatGetter;
        copy.floatSetter = this.floatSetter;
        copy.minFloat = this.minFloat;
        copy.maxFloat = this.maxFloat;
        copy.stepFloat = this.stepFloat;
        copy.defaultFloat = this.defaultFloat;
        copy.floatSuffix = this.floatSuffix;
        copy.stringGetter = this.stringGetter;
        copy.stringSetter = this.stringSetter;
        copy.cycleOptions = this.cycleOptions;
        copy.action = this.action;
        copy.buttonText = this.buttonText;
        copy.submenuOpener = this.submenuOpener;
        copy.cardHeight = this.cardHeight;
        copy.dynamicHeightSupplier = this.dynamicHeightSupplier;
        copy.customRenderer = this.customRenderer;
        copy.customClickHandler = this.customClickHandler;
        return copy;
    }
}
