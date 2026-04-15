package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BomboConfigGUI extends Screen {

    private static final int SIDEBAR_WIDTH = 130;
    private static final int HEADER_HEIGHT = 40;
    private static final int ITEM_HEIGHT = 22;
    private static final int PADDING = 8;

    private final Screen parent;
    private final List<String> categories = List.of("General", "Experiments", "Pest ESP", "Hotkeys", "Debug");
    private static int selectedCategory = 0;

    private final List<EditBox> activeBoxes = new ArrayList<>();

    public BomboConfigGUI(Screen parent) {
        super(Component.literal("Bomboaddons Configuration"));
        this.parent = parent;
    }

    public static Screen create() {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof net.minecraft.client.gui.screens.ChatScreen) {
            return new BomboConfigGUI(null);
        }
        return new BomboConfigGUI(current);
    }

    @Override
    protected void init() {
        try {
            super.init();
            activeBoxes.clear();
            clearWidgets();

            // 1. Sidebar Category Buttons
            for (int i = 0; i < categories.size(); i++) {
                final int idx = i;
                int catY = HEADER_HEIGHT + PADDING * 3 + i * 26;
                
                CategoryButton btn = new CategoryButton(PADDING, catY, SIDEBAR_WIDTH - PADDING * 2, 22, 
                    categories.get(idx), idx == selectedCategory, b -> {
                    selectedCategory = idx;
                    init();
                });
                addRenderableWidget(btn);
            }

            // 2. Content area
            int contentX = SIDEBAR_WIDTH + PADDING * 2;
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 3;
            int y = HEADER_HEIGHT + PADDING * 2 + 50; // Aligned with render() starting point
            BomboConfig.Settings s = BomboConfig.get();

            switch (selectedCategory) {
                case 0 -> { // General
                    y = addBoolOption("Sign Calculator", s.signCalculator, v -> s.signCalculator = v, contentX, contentWidth, y);
                    y = addBoolOption("Chest Clicker", s.chestClicker, v -> s.chestClicker = v, contentX, contentWidth, y);
                    y = addBoolOption("Auto Clicker", s.autoClicker, v -> s.autoClicker = v, contentX, contentWidth, y);
                    y = addBoolOption("SBE Commands", s.sbeCommands, v -> s.sbeCommands = v, contentX, contentWidth, y);
                    y = addBoolOption("Left Click Etherwarp", s.leftClickEtherwarp, v -> s.leftClickEtherwarp = v, contentX, contentWidth, y);
                    y = addBoolOption("Sphinx Macro", s.sphinxMacro, v -> s.sphinxMacro = v, contentX, contentWidth, y);
                    y = addBoolOption("Hollow Wand Click Through", s.hollowWandClickThrough, v -> s.hollowWandClickThrough = v, contentX, contentWidth, y);
                    y = addBoolOption("Hollow Wand Auto Combine", s.hollowWandAutoCombine, v -> s.hollowWandAutoCombine = v, contentX, contentWidth, y);
                    y = addBoolOption("Lowest BIN Tooltip", s.lowestBin, v -> s.lowestBin = v, contentX, contentWidth, y);
                }
                case 1 -> { // Experiments
                    y = addBoolOption("Auto Experiments", s.autoExperiments, v -> { s.autoExperiments = v; if (!v) AutoExperiments.reset(); }, contentX, contentWidth, y);
                    y = addIntSlider("Click Delay (ms)", s.experimentClickDelay, 0, 2000, 50, v -> s.experimentClickDelay = v, contentX, contentWidth, y);
                    y = addBoolOption("Auto Close", s.experimentAutoClose, v -> s.experimentAutoClose = v, contentX, contentWidth, y);
                    y = addIntSlider("Serum Count", s.experimentSerumCount, 0, 3, 1, v -> s.experimentSerumCount = v, contentX, contentWidth, y);
                }
                case 2 -> { // Pest ESP
                    y = addBoolOption("Pest ESP Enabled", s.pestEsp, v -> s.pestEsp = v, contentX, contentWidth, y);
                    y = addBoolOption("Pest Tracers", s.pestEspTracer, v -> s.pestEspTracer = v, contentX, contentWidth, y);
                    y = addTextBox("ESP Color (Hex)", s.pestEspColor, v -> s.pestEspColor = v, contentX, contentWidth, y);
                    y = addFloatSlider("Thickness", s.pestEspThickness, 0.5f, 5.0f, v -> s.pestEspThickness = v, contentX, contentWidth, y);
                }
                case 3 -> { // Hotkeys
                    y = addTextBox("Copy NBT Key", s.copyNbtKey, v -> s.copyNbtKey = v, contentX, contentWidth, y);
                    y = addTextBox("GFS Max Key", s.gfsMaxKey, v -> s.gfsMaxKey = v, contentX, contentWidth, y);
                    y = addTextBox("GFS Stack Key", s.gfsStackKey, v -> s.gfsStackKey = v, contentX, contentWidth, y);
                    y = addTextBox("Chat Peek Key", s.chatPeekKey, v -> s.chatPeekKey = v, contentX, contentWidth, y);
                    y = addTextBox("Trade Key", s.tradeKey, v -> s.tradeKey = v, contentX, contentWidth, y);
                    y = addTextBox("Recipe Key", s.recipeKey, v -> s.recipeKey = v, contentX, contentWidth, y);
                    y = addTextBox("Usage Key", s.usageKey, v -> s.usageKey = v, contentX, contentWidth, y);
                }
                case 4 -> { // Debug
                    y = addBoolOption("Debug Mode", s.debugMode, v -> s.debugMode = v, contentX, contentWidth, y);
                }
            }

            // Bottom Save Button
            addRenderableWidget(Button.builder(Component.literal("§lSave & Done"), btn -> {
                BomboConfig.save();
                minecraft.setScreen(parent);
            }).bounds(width / 2 - 75, height - 32, 150, 24).build());

        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during init!", e);
        }
    }

    private int addBoolOption(String label, boolean currentValue, Consumer<Boolean> setter, int x, int w, int y) {
        Checkbox cb = Checkbox.builder(Component.literal("§f" + label), font)
                .pos(x, y)
                .selected(currentValue)
                .onValueChange((box, val) -> {
                    setter.accept(val);
                    BomboConfig.save();
                })
                .build();
        addRenderableWidget(cb);
        return y + ITEM_HEIGHT;
    }

    private int addIntSlider(String label, int current, int min, int max, int step, java.util.function.IntConsumer setter, int x, int w, int y) {
        int[] val = {current};
        Button minus = Button.builder(Component.literal("§f-"), btn -> {
            val[0] = Math.max(min, val[0] - step);
            setter.accept(val[0]);
            BomboConfig.save();
            init();
        }).bounds(x + w - 50, y, 20, 18).build();
        Button plus = Button.builder(Component.literal("§f+"), btn -> {
            val[0] = Math.min(max, val[0] + step);
            setter.accept(val[0]);
            BomboConfig.save();
            init();
        }).bounds(x + w - 25, y, 20, 18).build();
        addRenderableWidget(minus);
        addRenderableWidget(plus);
        return y + ITEM_HEIGHT;
    }

    private int addFloatSlider(String label, float current, float min, float max, Consumer<Float> setter, int x, int w, int y) {
        float[] val = {current};
        float step = 0.5f;
        Button minus = Button.builder(Component.literal("§f-"), btn -> {
            val[0] = Math.max(min, val[0] - step);
            setter.accept(val[0]);
            BomboConfig.save();
            init();
        }).bounds(x + w - 50, y, 20, 18).build();
        Button plus = Button.builder(Component.literal("§f+"), btn -> {
            val[0] = Math.min(max, val[0] + step);
            setter.accept(val[0]);
            BomboConfig.save();
            init();
        }).bounds(x + w - 25, y, 20, 18).build();
        addRenderableWidget(minus);
        addRenderableWidget(plus);
        return y + ITEM_HEIGHT;
    }

    private int addTextBox(String label, String current, Consumer<String> setter, int x, int w, int y) {
        EditBox box = new EditBox(font, x + w / 2, y, w / 2, 16, Component.literal(label));
        box.setValue(current);
        box.setResponder(newVal -> {
            setter.accept(newVal);
            BomboConfig.save();
        });
        box.setMaxLength(64);
        addRenderableWidget(box);
        activeBoxes.add(box);
        return y + ITEM_HEIGHT;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            // 1. Sidebar Background
            g.fillGradient(0, 0, SIDEBAR_WIDTH, height, 0xEE11111B, 0xEE1E1E2E);
            g.fill(SIDEBAR_WIDTH - 1, 0, SIDEBAR_WIDTH, height, 0x33FFFFFF);

            // 2. Header
            g.fillGradient(0, 0, width, HEADER_HEIGHT, 0xCC11111B, 0xAA1E1E2E);
            g.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0x55FFFFFF);
            g.drawString(font, "§6§lBomboaddons §r§7Configuration", SIDEBAR_WIDTH + PADDING, 14, 0xFFFFFFFF, true);

            // 3. Category Label
            g.drawString(font, "§f§lCATEGORIES", PADDING, HEADER_HEIGHT - 12, 0xFFFFFFFF, true);

            // 4. Selection Marker (Sharp orange bar)
            int markerY = HEADER_HEIGHT + PADDING * 3 + selectedCategory * 26;
            g.fill(PADDING - 4, markerY - 2, PADDING - 2, markerY + 24, 0xFFFFAA00);

            // 5. Content Area Title
            int contentX = SIDEBAR_WIDTH + PADDING * 2;
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 3;
            int y = HEADER_HEIGHT + PADDING * 2;
            
            g.drawString(font, "§f§l" + categories.get(selectedCategory).toUpperCase(), contentX, y, 0xFFFFFFFF, true);
            
            String loc = SkyblockUtils.getLocation();
            String locDisplay = "§7Area: §a" + loc;
            g.drawString(font, locDisplay, width - PADDING - font.width(Component.literal(locDisplay).getString()), y + 2, 0xFFFFFFFF, false);
            
            y += 20;
            y += 30; // Start offset (y is now HEADER_HEIGHT + PADDING*2 + 50)

            BomboConfig.Settings s = BomboConfig.get();
            switch (selectedCategory) {
                case 1 -> { 
                    y += ITEM_HEIGHT; // Skip Auto Experiments
                    g.drawString(font, "§7Click Delay: §e" + s.experimentClickDelay + "ms", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT; // Click Delay buttons space
                    y += ITEM_HEIGHT; // Skip Auto Close
                    g.drawString(font, "§7Serum Count: §e" + s.experimentSerumCount, contentX, y + 4, 0xFFCCCCCC, false);
                }
                case 2 -> {
                    y += ITEM_HEIGHT; // Skip ESP Enabled
                    y += ITEM_HEIGHT; // Skip Tracers
                    g.drawString(font, "§7ESP Color (Hex)", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT; // Color box space
                    g.drawString(font, "§7Thickness: §e" + String.format("%.1f", s.pestEspThickness), contentX, y + 4, 0xFFCCCCCC, false);
                    
                    if (!SkyblockUtils.isInGarden()) {
                        g.drawString(font, "§c⚠ Pest ESP only active in the Garden!", contentX, height - 60, 0xFFFF5555, true);
                    }
                }
                case 3 -> {
                    g.drawString(font, "§7Chat Peek Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Trade Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Recipe Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Usage Key", contentX, y + 4, 0xFFCCCCCC, false);
                }
            }

            // NOW call super to render buttons and other widgets ON TOP
            super.render(g, mouseX, mouseY, partialTick);

        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during render!", e);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class CategoryButton extends Button {
        private final boolean selected;
        private final String label;

        public CategoryButton(int x, int y, int w, int h, String label, boolean selected, OnPress onPress) {
            super(x, y, w, h, Component.literal(label), onPress, DEFAULT_NARRATION);
            this.selected = selected;
            this.label = label;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHovered();
            
            // Forced High-Contrast: ALWAYS use Bold White prefix
            String prefix = selected ? "§f§l" : (hovered ? "§f" : "§f");
            int color = 0xFFFFFFFF; 
            
            if (selected) {
                g.fill(getX() - 4, getY() - 1, getX() + width + 4, getY() + height + 1, 0x66FFFFFF);
            } else if (hovered) {
                g.fill(getX() - 4, getY() - 1, getX() + width + 4, getY() + height + 1, 0x33FFFFFF);
            }

            g.drawString(Minecraft.getInstance().font, prefix + label, getX() + 10, getY() + (height - 8) / 2, color, true);
        }
    }
}
