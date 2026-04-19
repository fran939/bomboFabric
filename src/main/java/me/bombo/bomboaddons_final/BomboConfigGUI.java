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
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.input.KeyEvent;

public class BomboConfigGUI extends Screen {

    private static final int SIDEBAR_WIDTH = 130;
    private static final int HEADER_HEIGHT = 40;
    private static final int ITEM_HEIGHT = 22;
    private static final int PADDING = 8;

    private final Screen parent;
    private final List<String> categories = List.of("General", "Experiments", "Pest ESP", "Hotkeys", "Debug", "Clicker",
            "Keybinds", "Highlights");
    private static int selectedCategory = 0;

    private final List<EditBox> activeBoxes = new ArrayList<>();

    // Transient state for adding clicker targets
    private static String clickGuiInput = "";
    private static String clickKeyInput = "";
    private static String clickItemInput = "";
    private static String clickTypeInput = "left";
    private static int editingTargetIndex = -1;

    // Transient state for keybinds
    private static String bindCommandInput = "";
    private static String bindComboInput = "";
    private static String newProfileInput = "";

    // Transient state for highlights
    private static String highMobInput = "";
    private static String highColorInput = "GOLD";
    private static boolean highShowInvis = false;
    private static String editingHighName = "";
    private static String listeningForKeyTarget = ""; // "clicker", "copyNbt", "gfsMax", etc.

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
                    y = addBoolOption("Sign Calculator", s.signCalculator, v -> s.signCalculator = v, contentX,
                            contentWidth, y);
                    y = addBoolOption("Chest Clicker", s.chestClicker, v -> s.chestClicker = v, contentX, contentWidth,
                            y);
                    y = addBoolOption("SBE Commands", s.sbeCommands, v -> s.sbeCommands = v, contentX, contentWidth, y);
                    y = addBoolOption("Left Click Etherwarp", s.leftClickEtherwarp, v -> s.leftClickEtherwarp = v,
                            contentX, contentWidth, y);
                    y = addBoolOption("Sphinx Macro", s.sphinxMacro, v -> s.sphinxMacro = v, contentX, contentWidth, y);
                    y = addBoolOption("Hollow Wand Fix", s.hollowWandClickThrough, v -> s.hollowWandClickThrough = v,
                            contentX, contentWidth, y);
                    y = addBoolOption("Hollow Wand Double Click", s.hollowWandAutoCombine,
                            v -> s.hollowWandAutoCombine = v, contentX, contentWidth, y);
                    y = addBoolOption("Lowest BIN Tooltip", s.lowestBin, v -> s.lowestBin = v, contentX, contentWidth,
                            y);
                }
                case 1 -> { // Experiments
                    y = addBoolOption("Auto Experiments", s.autoExperiments, v -> {
                        s.autoExperiments = v;
                        if (!v)
                            AutoExperiments.reset();
                    }, contentX, contentWidth, y);
                    y = addIntSlider("Click Delay (ms)", s.experimentClickDelay, 0, 2000, 50,
                            v -> s.experimentClickDelay = v, contentX, contentWidth, y);
                    y = addBoolOption("Auto Close", s.experimentAutoClose, v -> s.experimentAutoClose = v, contentX,
                            contentWidth, y);
                    y = addIntSlider("Serum Count", s.experimentSerumCount, 0, 3, 1, v -> s.experimentSerumCount = v,
                            contentX, contentWidth, y);
                }
                case 2 -> { // Pest ESP
                    y = addBoolOption("Pest ESP Enabled", s.pestEsp, v -> s.pestEsp = v, contentX, contentWidth, y);
                    y = addBoolOption("Pest Tracers", s.pestEspTracer, v -> s.pestEspTracer = v, contentX, contentWidth,
                            y);
                    y = addTextBox("ESP Color (Hex)", s.pestEspColor, v -> s.pestEspColor = v, contentX, contentWidth,
                            y);
                    y = addFloatSlider("Thickness", s.pestEspThickness, 0.5f, 5.0f, v -> s.pestEspThickness = v,
                            contentX, contentWidth, y);
                }
                case 3 -> { // Hotkeys
                    y = addKeyBindButton("Copy NBT Key", s.copyNbtKey, v -> s.copyNbtKey = v, "copyNbt", contentX, contentWidth, y);
                    y = addKeyBindButton("GFS Max Key", s.gfsMaxKey, v -> s.gfsMaxKey = v, "gfsMax", contentX, contentWidth, y);
                    y = addKeyBindButton("GFS Stack Key", s.gfsStackKey, v -> s.gfsStackKey = v, "gfsStack", contentX, contentWidth, y);
                    y = addKeyBindButton("Chat Peek Key", s.chatPeekKey, v -> s.chatPeekKey = v, "chatPeek", contentX, contentWidth, y);
                    y = addKeyBindButton("Trade Key", s.tradeKey, v -> s.tradeKey = v, "trade", contentX, contentWidth, y);
                    y = addKeyBindButton("Recipe Key", s.recipeKey, v -> s.recipeKey = v, "recipe", contentX, contentWidth, y);
                    y = addKeyBindButton("Usage Key", s.usageKey, v -> s.usageKey = v, "usage", contentX, contentWidth, y);
                }
                case 5 -> { // Clicker
                    y = addTextBox("GUI Name", clickGuiInput, v -> clickGuiInput = v, contentX, contentWidth, y);
                    
                    y = addKeyBindButton("Key", clickKeyInput, v -> clickKeyInput = v, "clicker", contentX, contentWidth, y);

                    y = addTextBox("Item", clickItemInput, v -> clickItemInput = v, contentX, contentWidth, y);
                    y = addTextBox("Type", clickTypeInput, v -> clickTypeInput = v, contentX, contentWidth, y);

                    String btnLabel = editingTargetIndex == -1 ? "§a+ Add Target" : "§b§lUpdate Target";
                    addRenderableWidget(Button.builder(Component.literal(btnLabel), btn -> {
                        if (!clickGuiInput.isEmpty() && !clickKeyInput.isEmpty() && !clickItemInput.isEmpty()) {
                            if (editingTargetIndex != -1) {
                                ClickLogic.removeTarget(editingTargetIndex);
                            }
                            ClickLogic.setTarget(clickItemInput, clickGuiInput, clickKeyInput, clickTypeInput, false);
                            editingTargetIndex = -1;
                            clickGuiInput = "";
                            clickKeyInput = "";
                            clickItemInput = "";
                            clickTypeInput = "left";
                            init();
                        }
                    }).bounds(contentX + width / 2, y, contentWidth / 2, 20).build());

                    if (editingTargetIndex != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), btn -> {
                            editingTargetIndex = -1;
                            clickGuiInput = "";
                            clickKeyInput = "";
                            clickItemInput = "";
                            clickTypeInput = "left";
                            init();
                        }).bounds(contentX + width / 2 - 60, y, 55, 20).build());
                    }

                    y += 40;

                    List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
                    for (int i = 0; i < targets.size(); i++) {
                        final int idx = i;
                        int currentY = y;
                        ClickLogic.ClickTarget t = targets.get(i);

                        addRenderableWidget(Button.builder(Component.literal("§bEdit"), btn -> {
                            editingTargetIndex = idx;
                            clickGuiInput = t.gui;
                            clickKeyInput = t.keyName;
                            clickItemInput = t.item;
                            clickTypeInput = t.type;
                            init();
                        }).bounds(contentX + contentWidth - 105, currentY, 50, 18).build());

                        addRenderableWidget(Button.builder(Component.literal("§cRemove"), btn -> {
                            ClickLogic.removeTarget(idx);
                            init();
                        }).bounds(contentX + contentWidth - 50, currentY, 50, 18).build());
                        y += 20;
                    }
                }
                case 6 -> { // Keybinds (Global Binds)
                    // Row 1: Active Profile with Nav Buttons
                    
                    addRenderableWidget(Button.builder(Component.literal("§7<"), btn -> {
                        java.util.List<String> profileList = new java.util.ArrayList<>(s.profileBinds.keySet());
                        if (!profileList.contains("default")) profileList.add("default");
                        int current = profileList.indexOf(s.activeProfile);
                        int next = (current - 1 + profileList.size()) % profileList.size();
                        s.activeProfile = profileList.get(next);
                        BomboConfig.save();
                        init();
                    }).bounds(contentX + contentWidth / 2 - 25, y, 20, 18).build());

                    EditBox profBox = new EditBox(font, contentX + contentWidth / 2, y, contentWidth / 2 - 30, 16, Component.literal("Profile"));
                    profBox.setValue(s.activeProfile);
                    profBox.setResponder(v -> { s.activeProfile = v; BomboConfig.save(); });
                    addRenderableWidget(profBox);

                    addRenderableWidget(Button.builder(Component.literal("§7>"), btn -> {
                        java.util.List<String> profileList = new java.util.ArrayList<>(s.profileBinds.keySet());
                        if (!profileList.contains("default")) profileList.add("default");
                        int current = profileList.indexOf(s.activeProfile);
                        int next = (current + 1) % profileList.size();
                        s.activeProfile = profileList.get(next);
                        BomboConfig.save();
                        init();
                    }).bounds(contentX + contentWidth - 25, y, 20, 18).build());

                    y += ITEM_HEIGHT + 10;

                    // New Profile Creation Row
                    y = addTextBox("New Profile Name", newProfileInput, v -> newProfileInput = v, contentX, contentWidth, y);
                    addRenderableWidget(Button.builder(Component.literal("§aCreate"), btn -> {
                        if (!newProfileInput.isEmpty()) {
                            s.profileBinds.putIfAbsent(newProfileInput, new java.util.ArrayList<>());
                            s.activeProfile = newProfileInput;
                            newProfileInput = "";
                            BomboConfig.save();
                            init();
                        }
                    }).bounds(contentX + contentWidth - 60, y - ITEM_HEIGHT, 60, 18).build());

                    y += ITEM_HEIGHT + 15;

                    y = addTextBox("Command", bindCommandInput, v -> bindCommandInput = v, contentX, contentWidth, y);
                    y = addTextBox("Combination (e.g. F5+1)", bindComboInput, v -> bindComboInput = v, contentX,
                            contentWidth, y);

                    addRenderableWidget(Button.builder(Component.literal("§a+ Add Bind"), btn -> {
                        if (!bindCommandInput.isEmpty() && !bindComboInput.isEmpty()) {
                            List<Integer> codes = parseCombo(bindComboInput);
                            if (!codes.isEmpty()) {
                                s.profileBinds.putIfAbsent(s.activeProfile, new java.util.ArrayList<>());
                                s.profileBinds.get(s.activeProfile).add(
                                        new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                                bindCommandInput = "";
                                bindComboInput = "";
                                BomboConfig.save();
                                init();
                            }
                        }
                    }).bounds(contentX + width / 2, y, contentWidth / 2, 20).build());

                    y += 40;

                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (int i = 0; i < binds.size(); i++) {
                            final int idx = i;
                            int currentY = y;
                            addRenderableWidget(Button.builder(Component.literal("§cRemove"), btn -> {
                                binds.remove(idx);
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + contentWidth - 50, currentY, 50, 18).build());
                            y += 20;
                        }
                    }
                }
                case 7 -> { // Highlights
                    y = addBoolOption("Highlights Enabled", s.highlightsEnabled, v -> s.highlightsEnabled = v, contentX, contentWidth, y);
                    y += 20; // More spacer to prevent overlap
                    
                    y = addTextBox("Mob Name", highMobInput, v -> highMobInput = v, contentX, contentWidth, y);
                    y = addTextBox("Color", highColorInput, v -> highColorInput = v, contentX, contentWidth, y);
                    y = addBoolOption("Show Invisible", highShowInvis, v -> highShowInvis = v, contentX, contentWidth, y);
                    
                    String btnLabel = editingHighName.isEmpty() ? "§a+ Add Highlight" : "§b§lUpdate Highlight";
                    addRenderableWidget(Button.builder(Component.literal(btnLabel), btn -> {
                        if (!highMobInput.isEmpty() && !highColorInput.isEmpty()) {
                            if (!editingHighName.isEmpty()) {
                                BomboConfig.get().highlights.remove(editingHighName);
                            }
                            BomboConfig.get().highlights.put(highMobInput.toLowerCase(), 
                                new BomboConfig.HighlightInfo(highColorInput.toUpperCase(), highShowInvis));
                            
                            editingHighName = "";
                            highMobInput = ""; highColorInput = "GOLD"; highShowInvis = false;
                            BomboConfig.save();
                            init();
                        }
                    }).bounds(contentX + width / 2, y, contentWidth / 2, 20).build());

                    if (!editingHighName.isEmpty()) {
                        addRenderableWidget(Button.builder(Component.literal("§7Cancel"), btn -> {
                            editingHighName = "";
                            highMobInput = ""; highColorInput = "GOLD"; highShowInvis = false;
                            init();
                        }).bounds(contentX + width / 2 - 60, y, 55, 20).build());
                    }

                    y += 40;
                    
                    java.util.List<java.util.Map.Entry<String, BomboConfig.HighlightInfo>> hList = new java.util.ArrayList<>(BomboConfig.get().highlights.entrySet());
                    for (int i = 0; i < hList.size(); i++) {
                        java.util.Map.Entry<String, BomboConfig.HighlightInfo> entry = hList.get(i);
                        int currentY = y;
                        
                        addRenderableWidget(Button.builder(Component.literal("§bEdit"), btn -> {
                            editingHighName = entry.getKey();
                            highMobInput = entry.getKey();
                            highColorInput = entry.getValue().color;
                            highShowInvis = entry.getValue().showInvisible;
                            init();
                        }).bounds(contentX + contentWidth - 105, currentY, 50, 18).build());

                        addRenderableWidget(Button.builder(Component.literal("§cRemove"), btn -> {
                            BomboConfig.get().highlights.remove(entry.getKey());
                            BomboConfig.save();
                            init();
                        }).bounds(contentX + contentWidth - 50, currentY, 50, 18).build());
                        y += 20;
                    }
                }
                case 4 -> { // Debug
                    y = addBoolOption("Debug Mode", s.debugMode, v -> s.debugMode = v, contentX, contentWidth, y);
                    y = addBoolOption("Hitboxes", s.hitbox, v -> s.hitbox = v, contentX, contentWidth, y);
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

    private int addIntSlider(String label, int current, int min, int max, int step,
            java.util.function.IntConsumer setter, int x, int w, int y) {
        int[] val = { current };
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

    private int addFloatSlider(String label, float current, float min, float max, Consumer<Float> setter, int x, int w,
            int y) {
        float[] val = { current };
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

    private int addKeyBindButton(String label, String current, Consumer<String> setter, String target, int x, int w, int y) {
        boolean listening = listeningForKeyTarget.equals(target);
        String btnLabel = listening ? "§e§l[ Press Any Key ]" : "§f" + label + ": §d" + (current.isEmpty() ? "None" : current);
        
        addRenderableWidget(Button.builder(Component.literal(btnLabel), btn -> {
            listeningForKeyTarget = target;
            init();
        }).bounds(x + w / 2, y, w / 2, 16).build());
        
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

            int safeIdx = Math.max(0, Math.min(selectedCategory, categories.size() - 1));
            g.drawString(font, "§f§l" + categories.get(safeIdx).toUpperCase(), contentX, y, 0xFFFFFFFF, true);

            String loc = SkyblockUtils.getLocation();
            String locDisplay = "§7Area: §a" + loc;
            g.drawString(font, locDisplay, width - PADDING - font.width(Component.literal(locDisplay).getString()),
                    y + 2, 0xFFFFFFFF, false);

            y += 20;
            y += 30; // Start offset (y is now HEADER_HEIGHT + PADDING*2 + 50)

            BomboConfig.Settings s = BomboConfig.get();
            switch (selectedCategory) {
                case 0 -> { // General
                    g.drawString(font, "§6§lMod Settings", contentX, y, 0xFFFFAA00, true);
                }
                case 1 -> {
                    y += ITEM_HEIGHT; // Skip Auto Experiments
                    g.drawString(font, "§7Click Delay: §e" + s.experimentClickDelay + "ms", contentX, y + 4, 0xFFCCCCCC,
                            false);
                    y += ITEM_HEIGHT; // Click Delay buttons space
                    y += ITEM_HEIGHT; // Skip Auto Close
                    g.drawString(font, "§7Serum Count: §e" + s.experimentSerumCount, contentX, y + 4, 0xFFCCCCCC,
                            false);
                }
                case 2 -> {
                    y += ITEM_HEIGHT; // Skip ESP Enabled
                    y += ITEM_HEIGHT; // Skip Tracers
                    g.drawString(font, "§7ESP Color (Hex)", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT; // Color box space
                    g.drawString(font, "§7Thickness: §e" + String.format("%.1f", s.pestEspThickness), contentX, y + 4,
                            0xFFCCCCCC, false);

                    if (!SkyblockUtils.isInGarden()) {
                        g.drawString(font, "§c⚠ Pest ESP only active in the Garden!", contentX, height - 60, 0xFFFF5555,
                                true);
                    }
                }
                case 3 -> { // Hotkeys
                    g.drawString(font, "§7Copy NBT Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7GFS Max Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7GFS Stack Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Chat Peek Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Trade Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Recipe Key", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Usage Key", contentX, y + 4, 0xFFCCCCCC, false);
                }
                case 5 -> { // Clicker
                    g.drawString(font, "§6§lAdd/Edit Target", contentX, y, 0xFFFFAA00, true);
                    g.drawString(font, "§7GUI Group:", contentX, y + 24, 0xFFCCCCCC, false);
                    g.drawString(font, "§7Key Name:", contentX, y + 46, 0xFFCCCCCC, false);
                    g.drawString(font, "§7Item Search:", contentX, y + 68, 0xFFCCCCCC, false);
                    g.drawString(font, "§7Click Type:", contentX, y + 90, 0xFFCCCCCC, false);

                    y += 120; // Offset for inputs

                    g.drawString(font, "§6§lCurrent Targets", contentX, y, 0xFFFFAA00, true);
                    y += 20;

                    List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
                    if (targets.isEmpty()) {
                        g.drawString(font, "§7No targets added.", contentX, y, 0xFF888888, false);
                    } else {
                        for (int i = 0; i < targets.size(); i++) {
                            ClickLogic.ClickTarget t = targets.get(i);
                            String info = String.format("§e%s §7(§b%s§7) Key: §d%s", t.item, t.gui, t.keyName);
                            g.drawString(font, info, contentX, y, 0xFFFFFFFF, false);
                            y += 20;
                        }
                    }
                }
                case 6 -> { // Keybinds
                    y += ITEM_HEIGHT + 10;
                    
                    g.drawString(font, "§7Create New Profile:", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT + 25;
                    
                    g.drawString(font, "§6§lProfile Binds: §e" + s.activeProfile, contentX, y, 0xFFFFAA00, true);
                    y += 20;
                    g.drawString(font, "§7Command:", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Combo (e.g. CTRL+X):", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;

                    y += 20;

                    g.drawString(font, "§6§lActive Binds", contentX, y, 0xFFFFAA00, true);
                    y += 20;

                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds == null || binds.isEmpty()) {
                        g.drawString(font, "§7None", contentX, y, 0xFF888888, false);
                    } else {
                        for (BomboConfig.CommandBind b : binds) {
                            g.drawString(font, "§d" + b.keyName + " §7-> §e" + b.command, contentX, y, 0xFFFFFFFF,
                                    false);
                            y += 20;
                        }
                    }
                }
                case 7 -> { // Highlights
                    y += ITEM_HEIGHT; // Skip Toggle
                    y += 20; // Spacer
                    g.drawString(font, "§6§lAdd/Edit Highlight", contentX, y + 4, 0xFFFFAA00, true);
                    y += ITEM_HEIGHT;
                    
                    g.drawString(font, "§7Mob Name:", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Color:", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    g.drawString(font, "§7Show Invisible:", contentX, y + 4, 0xFFCCCCCC, false);
                    y += ITEM_HEIGHT;
                    
                    y += 20;

                    g.drawString(font, "§6§lActive Highlights", contentX, y, 0xFFFFAA00, true);
                    y += 20;

                    java.util.List<java.util.Map.Entry<String, BomboConfig.HighlightInfo>> hList = new java.util.ArrayList<>(BomboConfig.get().highlights.entrySet());
                    if (hList.isEmpty()) {
                        g.drawString(font, "§7None", contentX, y, 0xFF888888, false);
                    } else {
                        for (java.util.Map.Entry<String, BomboConfig.HighlightInfo> entry : hList) {
                            String invisInfo = entry.getValue().showInvisible ? " §7(§aInvis§7)" : "";
                            g.drawString(font, "§e" + entry.getKey() + " §7- §b" + entry.getValue().color + invisInfo, contentX, y, 0xFFFFFFFF, false);
                            y += 20;
                        }
                    }
                }
                case 4 -> { // Debug
                    g.drawString(font, "§6§lDiagnostic Tools", contentX, y, 0xFFFFAA00, true);
                }
            }

            // NOW call super to render buttons and other widgets ON TOP
            super.render(g, mouseX, mouseY, partialTick);

        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during render!", e);
        }
    }

    private List<Integer> parseCombo(String combo) {
        List<Integer> codes = new java.util.ArrayList<>();
        String[] parts = combo.toLowerCase().split("\\+");
        for (String p : parts) {
            int code = ClickLogic.getKeyCode(p.trim());
            if (code != -1)
                codes.add(code);
        }
        return codes;
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

            g.drawString(Minecraft.getInstance().font, prefix + label, getX() + 10, getY() + (height - 8) / 2, color,
                    true);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (!listeningForKeyTarget.isEmpty()) {
            if (keyCode == 256) { // ESC
                updateKeyTarget("");
            } else {
                String keyName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
                if (keyName == null) {
                    keyName = "key_" + keyCode; 
                }
                updateKeyTarget(keyName);
            }
            listeningForKeyTarget = "";
            BomboConfig.save();
            init();
            return true;
        }
        return super.keyPressed(event);
    }

    private void updateKeyTarget(String keyName) {
        BomboConfig.Settings s = BomboConfig.get();
        switch (listeningForKeyTarget) {
            case "copyNbt" -> s.copyNbtKey = keyName;
            case "gfsMax" -> s.gfsMaxKey = keyName;
            case "gfsStack" -> s.gfsStackKey = keyName;
            case "chatPeek" -> s.chatPeekKey = keyName;
            case "trade" -> s.tradeKey = keyName;
            case "recipe" -> s.recipeKey = keyName;
            case "usage" -> s.usageKey = keyName;
            case "clicker" -> clickKeyInput = keyName;
        }
    }
}
