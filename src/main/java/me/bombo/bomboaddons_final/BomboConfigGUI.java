package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.input.KeyEvent;

public class BomboConfigGUI extends Screen {

    private static final int SIDEBAR_WIDTH = 130;
    private static final int HEADER_HEIGHT = 40;
    private static final int ITEM_HEIGHT = 24;
    private static final int PADDING = 8;

    private final Screen parent;
    private final List<String> categories = List.of("General", "Experiments", "Garden", "Hotkeys", "Clicker",
            "Keybinds", "Highlights", "Wardrobe", "Debug");
    private static int selectedCategory = 0;

    private final List<EditBox> activeBoxes = new ArrayList<>();

    // Transient state for adding clicker targets
    private static String clickGuiInput = "";
    private static String clickKeyInput = "";
    private static String clickItemInput = "";
    private static String clickTypeInput = "left";

    // Transient state for keybinds
    private static String bindCommandInput = "";
    private static String bindComboInput = "";
    private static String profileNameInput = "";

    // Transient state for highlights
    private static String highMobInput = "";
    private static String highColorInput = "GOLD";
    private static boolean highShowInvis = false;
    private static String editingHighMob = null;
    private static String listeningForKeyTarget = "";

    private static int editingClickTargetIdx = -1;
    private static int editingKeybindIdx = -1;

    private double scrollAmount = 0;

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
            BomboConfig.Settings s = BomboConfig.get();
            activeBoxes.clear();
            clearWidgets();

            // 1. Sidebar Category Buttons
            for (int i = 0; i < categories.size(); i++) {
                final int idx = i;
                int catY = HEADER_HEIGHT + PADDING * 3 + i * 26;
                String label = (idx == selectedCategory ? "§6§l> " : "§7") + categories.get(idx);

                Button btn = Button.builder(Component.literal(label), b -> {
                    selectedCategory = idx;
                    scrollAmount = 0;
                    init();
                }).bounds(PADDING, catY, SIDEBAR_WIDTH - PADDING * 2, 22).build();
                addRenderableWidget(btn);
            }

            // 2. Content area
            int contentX = SIDEBAR_WIDTH + PADDING * 2;
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 3;
            
            // Base Y for the category title
            int categoryTitleY = HEADER_HEIGHT + PADDING * 2;
            // Base Y for mod settings (Header drawn here, widgets start 25px lower)
            int contentBaseY = categoryTitleY + 30; 
            int curY = contentBaseY;

            switch (selectedCategory) {
                case 0 -> { // General
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Sign Calculator", s.signCalculator, v -> s.signCalculator = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Chest Clicker", s.chestClicker, v -> s.chestClicker = v, contentX, contentWidth, curY);
                    curY = addBoolOption("SBE Commands", s.sbeCommands, v -> s.sbeCommands = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Left Click Etherwarp", s.leftClickEtherwarp, v -> s.leftClickEtherwarp = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Sphinx Macro", s.sphinxMacro, v -> s.sphinxMacro = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Hollow Wand Fix", s.hollowWandClickThrough, v -> s.hollowWandClickThrough = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Hollow Wand Double Click", s.hollowWandAutoCombine, v -> s.hollowWandAutoCombine = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Auto Accept Carnival", s.autoAcceptCarnival, v -> s.autoAcceptCarnival = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Lowest BIN Tooltip", s.lowestBin, v -> s.lowestBin = v, contentX, contentWidth, curY);
                    curY = addBoolOption("NPC Sell Price Tooltip", s.npcPrice, v -> s.npcPrice = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Ignore Caps Lock", s.ignoreCapsLock, v -> s.ignoreCapsLock = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Server List Button", s.serverListButton, v -> s.serverListButton = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Dice Tracker HUD", s.diceTracker, v -> s.diceTracker = v, contentX, contentWidth, curY);
                }
                case 1 -> { // Experiments
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Auto Experiments", s.autoExperiments, v -> { s.autoExperiments = v; if (!v) AutoExperiments.reset(); }, contentX, contentWidth, curY);
                    curY = addIntLabelSlider("Click Delay", s.experimentClickDelay, 0, 2000, 50, v -> s.experimentClickDelay = v, contentX, 150, curY);
                    curY = addIntLabelSlider("Serum Count", s.experimentSerumCount, 0, 3, 1, v -> s.experimentSerumCount = v, contentX, 150, curY);
                    curY = addBoolOption("Auto Close", s.experimentAutoClose, v -> s.experimentAutoClose = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Get Max XP", s.experimentGetMaxXp, v -> s.experimentGetMaxXp = v, contentX, contentWidth, curY);
                    
                    curY += 5;
                    String[] typeNames = {"Left-Click", "Middle-Click", "Shift-Click"};
                    String typeLabel = "Click Type: " + typeNames[Math.max(0, Math.min(2, s.experimentClickType))];
                    addRenderableWidget(Button.builder(Component.literal(typeLabel), btn -> {
                        s.experimentClickType = (s.experimentClickType + 1) % 3;
                        BomboConfig.save();
                        init();
                    }).bounds(contentX, curY, 150, 20).build());
                    curY += ITEM_HEIGHT + 5;
                }
                case 2 -> { // Garden
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Garden Movement", s.gardenMovement, v -> { s.gardenMovement = v; if (!v) GardenMovement.reset(); }, contentX, contentWidth, curY);
                    curY = addBoolOption("Sugar Cane Mode", s.gardenSugarCane, v -> s.gardenSugarCane = v, contentX, contentWidth, curY);
                    curY += 10;
                    curY = addKeyBindButton("Forward", s.gardenForwardKey, v -> s.gardenForwardKey = v, "gardenF", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Backward", s.gardenBackwardKey, v -> s.gardenBackwardKey = v, "gardenB", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Left", s.gardenLeftKey, v -> s.gardenLeftKey = v, "gardenL", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Right", s.gardenRightKey, v -> s.gardenRightKey = v, "gardenR", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Break", s.gardenBreakKey, v -> s.gardenBreakKey = v, "gardenBr", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Use", s.gardenUseKey, v -> s.gardenUseKey = v, "gardenU", contentX, contentWidth, curY);
                    
                    curY += 20;
                    curY = addBoolOption("Pest ESP Enabled", s.pestEsp, v -> s.pestEsp = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Pest Tracers", s.pestEspTracer, v -> s.pestEspTracer = v, contentX, contentWidth, curY);
                    curY = addTextBox("Pest Color", s.pestEspColor, v -> s.pestEspColor = v, contentX, contentWidth, curY);
                    curY = addFloatLabelSlider("Pest Size", s.pestEspThickness, 0.5f, 5.0f, v -> s.pestEspThickness = v, contentX, contentWidth, curY);
                }
                case 3 -> { // Hotkeys
                    curY += ITEM_HEIGHT;
                    curY = addKeyBindButton("Trade", s.tradeKey, v -> s.tradeKey = v, "trade", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Recipe", s.recipeKey, v -> s.recipeKey = v, "recipe", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Usage", s.usageKey, v -> s.usageKey = v, "usage", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Show Info", s.showItemKey, v -> s.showItemKey = v, "showItem", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Copy NBT", s.copyNbtKey, v -> s.copyNbtKey = v, "copyNbt", contentX, contentWidth, curY);
                    curY = addKeyBindButton("GFS Max", s.gfsMaxKey, v -> s.gfsMaxKey = v, "gfsMax", contentX, contentWidth, curY);
                    curY = addKeyBindButton("GFS Stack", s.gfsStackKey, v -> s.gfsStackKey = v, "gfsStack", contentX, contentWidth, curY);
                    curY = addKeyBindButton("Chat Peek", s.chatPeekKey, v -> s.chatPeekKey = v, "chatPeek", contentX, contentWidth, curY);
                }
                case 4 -> { // Clicker
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Auto Clicker", s.autoClicker, v -> s.autoClicker = v, contentX, contentWidth, curY);
                    curY += 10;
                    curY = addTextBox("GUI Name", clickGuiInput, v -> clickGuiInput = v, contentX, contentWidth, curY);
                    curY = addTextBox("Item Name", clickItemInput, v -> clickItemInput = v, contentX, contentWidth, curY);
                    curY = addKeyBindButton("Key", clickKeyInput, v -> clickKeyInput = v, "clicker", contentX, contentWidth, curY);
                    
                    int finalCurY = curY;
                    String addBtnText = editingClickTargetIdx != -1 ? "§e✔ Save Target" : "§a+ Add Target";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!clickGuiInput.isEmpty() && !clickKeyInput.isEmpty()) {
                            if (editingClickTargetIdx != -1) {
                                ClickLogic.updateTarget(editingClickTargetIdx, clickItemInput, clickGuiInput, clickKeyInput, clickTypeInput, false);
                                editingClickTargetIdx = -1;
                            } else {
                                ClickLogic.setTarget(clickItemInput, clickGuiInput, clickKeyInput, clickTypeInput, false);
                            }
                            clickGuiInput = ""; clickKeyInput = ""; clickItemInput = "";
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    
                    if (editingClickTargetIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingClickTargetIdx = -1;
                            clickGuiInput = ""; clickKeyInput = ""; clickItemInput = "";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35; // Space before list
                    int listStartY = curY;
                    for (int i = 0; i < ClickLogic.getTargets().size(); i++) {
                        final int idx = i;
                        ClickLogic.ClickTarget target = ClickLogic.getTargets().get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingClickTargetIdx = idx;
                                clickGuiInput = target.gui;
                                clickItemInput = target.item;
                                clickKeyInput = target.keyName;
                                init();
                            }).bounds(contentX + 180, itemY + 7, 40, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> { ClickLogic.removeTarget(idx); init(); }).bounds(contentX + 225, itemY + 7, 35, 18).build());
                        }
                    }
                }
                case 5 -> { // Keybinds
                    curY += ITEM_HEIGHT;
                    
                    List<String> profiles = new ArrayList<>(s.profileBinds.keySet());
                    if (!profiles.contains("default")) profiles.add(0, "default");
                    int currentIdx = profiles.indexOf(s.activeProfile);
                    
                    addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = profiles.get(next);
                        BomboConfig.save();
                        init();
                    }).bounds(contentX + 150, curY, 20, 20).build());
                    
                    addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = profiles.get(next);
                        BomboConfig.save();
                        init();
                    }).bounds(contentX + 175, curY, 20, 20).build());
                    
                    curY += ITEM_HEIGHT + 5;
                    
                    // Create Profile
                    curY = addTextBox("Create New Profile", profileNameInput, v -> profileNameInput = v, contentX, contentWidth - 60, curY);
                    int createBtnY = curY - ITEM_HEIGHT - 5;
                    addRenderableWidget(Button.builder(Component.literal("§aCreate"), btn -> {
                        if (!profileNameInput.isEmpty() && !s.profileBinds.containsKey(profileNameInput)) {
                            s.profileBinds.put(profileNameInput, new ArrayList<>());
                            s.activeProfile = profileNameInput;
                            BomboConfig.save();
                            profileNameInput = "";
                            init();
                        }
                    }).bounds(contentX + contentWidth - 55, createBtnY, 55, 20).build());
                    
                    curY += 10;
                    curY += ITEM_HEIGHT;
                    curY = addTextBox("Command", bindCommandInput, v -> bindCommandInput = v, contentX, contentWidth, curY);
                    curY = addTextBox("Combo (e.g. CTRL+G)", bindComboInput, v -> bindComboInput = v, contentX, contentWidth, curY);
                    int finalCurY = curY;
                    String addBindText = editingKeybindIdx != -1 ? "§e✔ Save Bind" : "§a+ Add Bind";
                    addRenderableWidget(Button.builder(Component.literal(addBindText), btn -> {
                        if (!bindCommandInput.isEmpty() && !bindComboInput.isEmpty()) {
                            List<Integer> codes = parseCombo(bindComboInput);
                            if (!codes.isEmpty()) {
                                s.profileBinds.putIfAbsent(s.activeProfile, new ArrayList<>());
                                if (editingKeybindIdx != -1) {
                                    s.profileBinds.get(s.activeProfile).set(editingKeybindIdx, new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                                    editingKeybindIdx = -1;
                                } else {
                                    s.profileBinds.get(s.activeProfile).add(new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                                }
                                BomboConfig.save();
                                bindCommandInput = ""; bindComboInput = "";
                                init();
                            }
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    
                    if (editingKeybindIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingKeybindIdx = -1;
                            bindCommandInput = ""; bindComboInput = "";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35;
                    int listStartY = curY;
                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (int i = 0; i < binds.size(); i++) {
                            final int idx = i;
                            BomboConfig.CommandBind bind = binds.get(idx);
                            int itemY = listStartY + 20 + i * 22 - (int)scrollAmount;
                            if (itemY > listStartY + 15 && itemY < height - 20) {
                                addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                    editingKeybindIdx = idx;
                                    bindCommandInput = bind.command;
                                    bindComboInput = bind.keyName;
                                    init();
                                }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                                addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> { binds.remove(idx); BomboConfig.save(); init(); }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                            }
                        }
                    }
                }
                case 6 -> { // Highlights
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Highlights Enabled", s.highlightsEnabled, v -> s.highlightsEnabled = v, contentX, contentWidth, curY);
                    curY += 10;
                    curY = addTextBox("Mob Name", highMobInput, v -> highMobInput = v, contentX, contentWidth, curY);
                    curY = addTextBox("Color", highColorInput, v -> highColorInput = v, contentX, contentWidth, curY);
                    
                    int finalCurY = curY;
                    String addBtnText = editingHighMob != null ? "§e✔ Save Highlight" : "§a+ Add Highlight";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!highMobInput.isEmpty()) {
                            if (editingHighMob != null) {
                                s.highlights.remove(editingHighMob);
                            }
                            s.highlights.put(highMobInput.toLowerCase(), new BomboConfig.HighlightInfo(highColorInput.toUpperCase(), true));
                            BomboConfig.save();
                            highMobInput = ""; highColorInput = "GOLD";
                            editingHighMob = null;
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingHighMob != null) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel Edit"), btn -> {
                            highMobInput = ""; highColorInput = "GOLD";
                            editingHighMob = null;
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 80, 20).build());
                    }

                    curY += 35; // Space before list
                    int listStartY = curY;
                    List<String> sortedMobs = new ArrayList<>(s.highlights.keySet());
                    Collections.sort(sortedMobs);
                    for (int i = 0; i < sortedMobs.size(); i++) {
                        final String mobName = sortedMobs.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int)scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingHighMob = mobName;
                                highMobInput = mobName;
                                highColorInput = s.highlights.get(mobName).color;
                                init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> { s.highlights.remove(mobName); BomboConfig.save(); init(); }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 7 -> { // Wardrobe
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Auto Close Wardrobe", s.autoCloseWardrobe, v -> s.autoCloseWardrobe = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Disable Unequip", s.disableUnequipWardrobe, v -> s.disableUnequipWardrobe = v, contentX, contentWidth, curY);
                    for (int i = 0; i < 9; i++) {
                        final int index = i;
                        curY = addKeyBindButton("Slot " + (i + 1), s.wardrobeKeys.get(i), v -> s.wardrobeKeys.set(index, v), "wardrobe" + i, contentX, contentWidth, curY);
                    }
                }
                case 8 -> { // Debug
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("MASTER DEBUG", s.debugMaster, v -> s.debugMaster = v, contentX, contentWidth, curY);
                    
                    addRenderableWidget(Button.builder(Component.literal("§a[Enable All Debug]"), btn -> {
                        s.debugChat = true;
                        s.debugGuis = true;
                        s.debugEntities = true;
                        s.debugCommands = true;
                        s.debugMaster = true;
                    }).bounds(contentX, curY, contentWidth, 20).build());
                    curY += ITEM_HEIGHT + 4;

                    curY = addBoolOption("Chat Debug", s.debugChat, v -> s.debugChat = v, contentX, contentWidth, curY);
                    curY = addBoolOption("GUIs Debug", s.debugGuis, v -> s.debugGuis = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Entities Debug", s.debugEntities, v -> s.debugEntities = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Command Debug", s.debugCommands, v -> s.debugCommands = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Debug Mode (Legacy)", s.debugMode, v -> s.debugMode = v, contentX, contentWidth, curY);
                    curY = addBoolOption("API Debug", s.apiDebug, v -> s.apiDebug = v, contentX, contentWidth, curY);
                }
            }

            addRenderableWidget(Button.builder(Component.literal("§lSave & Close"), btn -> {
                BomboConfig.save();
                minecraft.setScreen(parent);
            }).bounds(width / 2 - 75, height - 32, 150, 24).build());

        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during init!", e);
        }
    }

    private int addBoolOption(String label, boolean value, Consumer<Boolean> setter, int x, int w, int y) {
        // Use empty label to avoid overlap with drawString in render
        Checkbox cb = Checkbox.builder(Component.literal(""), font).pos(x, y).selected(value).onValueChange((box, val) -> { setter.accept(val); BomboConfig.save(); }).build();
        addRenderableWidget(cb);
        return y + ITEM_HEIGHT;
    }

    private int addIntLabelSlider(String label, int current, int min, int max, int step, java.util.function.IntConsumer setter, int x, int w, int y) {
        addRenderableWidget(Button.builder(Component.literal("§f-"), btn -> { setter.accept(Math.max(min, current - step)); BomboConfig.save(); init(); }).bounds(x + w + 10, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§f+"), btn -> { setter.accept(Math.min(max, current + step)); BomboConfig.save(); init(); }).bounds(x + w + 35, y, 20, 18).build());
        return y + ITEM_HEIGHT;
    }

    private int addFloatLabelSlider(String label, float current, float min, float max, Consumer<Float> setter, int x, int w, int y) {
        addRenderableWidget(Button.builder(Component.literal("§f-"), btn -> { setter.accept(Math.max(min, current - 0.5f)); BomboConfig.save(); init(); }).bounds(x + w - 50, y, 20, 18).build());
        addRenderableWidget(Button.builder(Component.literal("§f+"), btn -> { setter.accept(Math.min(max, current + 0.5f)); BomboConfig.save(); init(); }).bounds(x + w - 25, y, 20, 18).build());
        return y + ITEM_HEIGHT;
    }

    private int addTextBox(String label, String current, Consumer<String> setter, int x, int w, int y) {
        EditBox box = new EditBox(font, x + w / 2, y, w / 2, 16, Component.literal(label));
        box.setValue(current);
        box.setResponder(val -> { setter.accept(val); BomboConfig.save(); });
        addRenderableWidget(box);
        activeBoxes.add(box);
        return y + ITEM_HEIGHT;
    }

    private int addKeyBindButton(String label, String current, Consumer<String> setter, String target, int x, int w, int y) {
        boolean listening = listeningForKeyTarget.equals(target);
        String displayKey = ClickLogic.getKeyDisplayName(current);
        String txt = listening ? "§e[PRESS KEY]" : "§f" + label + ": §d" + (current.isEmpty() ? "None" : displayKey);
        addRenderableWidget(Button.builder(Component.literal(txt), btn -> { listeningForKeyTarget = target; init(); }).bounds(x + w / 2, y, w / 2, 16).build());
        return y + ITEM_HEIGHT;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        try {
            g.fillGradient(0, 0, SIDEBAR_WIDTH, height, 0xEE11111B, 0xEE1E1E2E);
            g.fill(SIDEBAR_WIDTH - 1, 0, SIDEBAR_WIDTH, height, 0x33FFFFFF);
            g.fillGradient(SIDEBAR_WIDTH, 0, width, HEADER_HEIGHT, 0xCC11111B, 0xAA1E1E2E);
            g.fill(SIDEBAR_WIDTH, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0x55FFFFFF);
            
            BomboConfig.Settings s = BomboConfig.get();

            super.render(g, mouseX, mouseY, partialTick);

            g.drawString(font, "§6§lBomboaddons §r§7Config", SIDEBAR_WIDTH + PADDING, 14, 0xFFFFFFFF, true);
            g.drawString(font, "§f§lCATEGORIES", PADDING, HEADER_HEIGHT - 12, 0xFFFFFFFF, true);

            int contentX = SIDEBAR_WIDTH + PADDING * 2;
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 3;
            int categoryTitleY = HEADER_HEIGHT + PADDING * 2;
            int safeIdx = Math.max(0, Math.min(selectedCategory, categories.size() - 1));
            g.drawString(font, "§f§l" + categories.get(safeIdx).toUpperCase(), contentX, categoryTitleY, 0xFFFFFFFF, true);

            int contentBaseY = categoryTitleY + 30;
            int curY = contentBaseY;

            switch (selectedCategory) {
                case 0 -> {
                    g.drawString(font, "§6§lMod Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Sign Calculator", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Chest Clicker", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7SBE Commands", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Left Click Etherwarp", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Sphinx Macro", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Hollow Wand Fix", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Hollow Wand Double Click", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Auto Accept Carnival", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Lowest BIN Tooltip", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7NPC Sell Price Tooltip", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Ignore Caps Lock", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Server List Button", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Dice Tracker HUD", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                }
                case 1 -> {
                    g.drawString(font, "§6§lExperiment Solver", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Auto Experiments", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fClick Delay: §e" + BomboConfig.get().experimentClickDelay + "ms", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fSerum Count: §e" + BomboConfig.get().experimentSerumCount, contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Auto Close", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Get Max XP", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 5;
                }
                case 2 -> {
                    g.drawString(font, "§6§lGarden Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Garden Movement", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Sugar Cane Mode", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.drawString(font, "§fForward:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fBackward:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fLeft:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fRight:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fBreak:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fUse:", contentX, curY, 0xFFFFFFFF);

                    curY += 20;
                    g.drawString(font, "§7Pest ESP Enabled", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Pest Tracers", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fPest Color (Hex):", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fPest Thickness: §e" + BomboConfig.get().pestEspThickness, contentX, curY, 0xFFFFFFFF);
                }
                case 3 -> {
                    g.drawString(font, "§6§lHotkey Shortcuts", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fTrade:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fRecipe:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fUsage:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fShow Info:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fCopy NBT:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fGFS Max:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fGFS Stack:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fChat Peek:", contentX, curY, 0xFFFFFFFF);
                }
                case 4 -> {
                    g.drawString(font, "§6§lClicker Targets", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Auto Clicker", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.drawString(font, "§fGUI Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.drawString(font, "§fItem Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.drawString(font, "§fKey to Press:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    
                    curY += 35; // Space before list
                    int listTitleY = curY;
                    g.drawString(font, "§9§lActive Targets", contentX, listTitleY, 0xFF5555FF, true);
                    
                    int listY = listTitleY + 20 - (int)scrollAmount;
                    for (ClickLogic.ClickTarget target : ClickLogic.getTargets()) {
                        if (listY > listTitleY + 15 && listY < height - 15) {
                            String displayKey = ClickLogic.getKeyDisplayName(target.keyName);
                            String txt = "§e" + target.gui + " §7- §b" + displayKey;
                            if (!target.item.isEmpty()) txt += " §8(" + target.item + ")";
                            g.drawString(font, txt, contentX, listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 5 -> {
                    g.drawString(font, "§6§lProfile Management", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fActive Profile: §e" + s.activeProfile, contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.drawString(font, "§fCreate New Profile:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 15;
                    
                    int listTitleY = curY;
                    g.drawString(font, "§6§lProfile Binds: §e" + s.activeProfile, contentX, listTitleY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§fCommand:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.drawString(font, "§fCombo:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    
                    curY += 35; // Space before list
                    int activeBindsTitleY = curY;
                    g.drawString(font, "§9§lActive Binds", contentX, activeBindsTitleY, 0xFF5555FF, true);
                    
                    int listY = activeBindsTitleY + 20 - (int)scrollAmount;
                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CommandBind bind : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < height - 15) {
                                g.drawString(font, "§e" + bind.keyName + " §7-> §b/" + bind.command, contentX, listY + 5, 0xFFFFFFFF, false);
                            }
                            listY += 22;
                        }
                    }
                }
                case 6 -> {
                    g.drawString(font, "§6§lAdd Highlight", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Highlights Enabled", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.drawString(font, "§fMob Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.drawString(font, "§fColor:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    curY += 35; // Space before list
                    int listTitleY = curY;
                    g.drawString(font, "§6§lActive Highlights", contentX, listTitleY, 0xFFFFAA00, true);
                    
                    int listY = listTitleY + 20 - (int)scrollAmount;
                    List<String> sortedMobs = new ArrayList<>(BomboConfig.get().highlights.keySet());
                    Collections.sort(sortedMobs);
                    for (String mobName : sortedMobs) {
                        if (listY > listTitleY + 15 && listY < height - 15) {
                            BomboConfig.HighlightInfo info = BomboConfig.get().highlights.get(mobName);
                            g.drawString(font, "§e" + mobName + " §7- §b" + info.color, contentX, listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 7 -> {
                    g.drawString(font, "§6§lWardrobe", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Auto Close Wardrobe", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Disable Unequip", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    for (int i = 0; i < 9; i++) {
                        g.drawString(font, "§fSlot " + (i + 1) + ":", contentX, curY, 0xFFFFFFFF);
                        curY += ITEM_HEIGHT;
                    }
                }
                case 8 -> {
                    g.drawString(font, "§c§lDebug Settings", contentX, curY, 0xFFFF5555, true);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§b§lMASTER DEBUG", contentX + 24, curY + 4, 0xFF55FFFF, false);
                    curY += ITEM_HEIGHT + 24; // Space for button
                    g.drawString(font, "§7Chat Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7GUIs Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Entities Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Command Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7Debug Mode (Legacy)", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.drawString(font, "§7API Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                }
            }
        } catch (Exception e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during render!", e);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private List<Integer> parseCombo(String combo) {
        List<Integer> codes = new ArrayList<>();
        String[] parts = combo.toLowerCase().split("\\+");
        for (String p : parts) {
            int code = ClickLogic.getKeyCode(p.trim());
            if (code != -1) codes.add(code);
        }
        return codes;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (!listeningForKeyTarget.isEmpty()) {
            if (keyCode == 256) {
                updateKeyTarget("");
            } else {
                String keyName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
                if (keyName == null) keyName = "key_" + keyCode; 
                updateKeyTarget(keyName);
            }
            listeningForKeyTarget = "";
            BomboConfig.save();
            init();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollAmount = Math.max(0, scrollAmount - vertical * 15);
        init();
        return true;
    }

    private void updateKeyTarget(String keyName) {
        BomboConfig.Settings s = BomboConfig.get();
        if (listeningForKeyTarget.startsWith("wardrobe")) {
            try {
                int index = Integer.parseInt(listeningForKeyTarget.substring(8));
                s.wardrobeKeys.set(index, keyName);
            } catch (Exception ignored) {}
            return;
        }
        switch (listeningForKeyTarget) {
            case "trade" -> s.tradeKey = keyName;
            case "recipe" -> s.recipeKey = keyName;
            case "usage" -> s.usageKey = keyName;
            case "showItem" -> s.showItemKey = keyName;
            case "copyNbt" -> s.copyNbtKey = keyName;
            case "gfsMax" -> s.gfsMaxKey = keyName;
            case "gfsStack" -> s.gfsStackKey = keyName;
            case "chatPeek" -> s.chatPeekKey = keyName;
            case "clicker" -> clickKeyInput = keyName;
            case "gardenF" -> s.gardenForwardKey = keyName;
            case "gardenB" -> s.gardenBackwardKey = keyName;
            case "gardenL" -> s.gardenLeftKey = keyName;
            case "gardenR" -> s.gardenRightKey = keyName;
            case "gardenBr" -> s.gardenBreakKey = keyName;
            case "gardenU" -> s.gardenUseKey = keyName;
        }
    }
}
