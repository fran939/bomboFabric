package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import net.minecraft.client.input.MouseButtonEvent;

public class BomboConfigGUI extends Screen {

    private static final int SIDEBAR_WIDTH = 130;
    private static final int HEADER_HEIGHT = 40;
    private static final int ITEM_HEIGHT = 24;
    private static final int PADDING = 8;

    private final Screen parent;
    private final List<String> categories = List.of("General", "HUDs", "Experiments", "Garden", "Hotkeys", "Profiles",
            "Clicker", "Highlights", "Wardrobe", "Anvil", "Debug", "Kuudra", "Pets", "Keybinds", "Waypoints", "Aliases",
            "Chat Triggers", "Dungeons", "Coord Binds", "Mining", "Party Settings", "Block Highlights",
            "Particle Highlights", "Bedwars");
    public static int selectedCategory = 0;

    private static int partyCommandsX = -1;
    private static int partyCommandsY = -1;
    private static int partyCommandsWidth = -1;
    private static int partyCommandsHeight = -1;

    private final List<EditBox> activeBoxes = new ArrayList<>();

    // Transient state for adding clicker targets
    private static String clickGuiInput = "";
    private static String clickKeyInput = "";
    private static String clickItemInput = "";
    private static String clickTypeInput = "left";

    // Transient state for keybinds
    private static String bindCommandInput = "";
    private static String bindComboInput = "";
    private static String bindIslandInput = "";
    private static String bindArmorInput = "";
    private static String profileNameInput = "";

    // Transient state for highlights
    private static String highMobInput = "";
    private static String highColorInput = "GOLD";
    private static boolean highShowInvis = false;
    private static String editingHighMob = null;
    private static String listeningForKeyTarget = "";

    // Transient state for block highlights
    private static String blockNameInput = "";
    private static String blockColorInput = "GOLD";
    private static boolean blockThroughWallsInput = true;
    private static String editingBlockName = null;

    // Transient state for particle highlights
    private static String partHighInput = "";
    private static String partHighColorInput = "GOLD";
    private static String editingPartHigh = null;
    private static final List<String> recordedComboKeys = new ArrayList<>();

    private static int editingClickTargetIdx = -1;
    private static int editingKeybindIdx = -1;
    private static boolean confirmProfileDelete = false;

    // Transient state for waypoints
    private static String wpNameInput = "";
    private static String wpCoordsInput = "";
    private static String wpIslandInput = "";
    private static boolean wpThruWallsInput = true;
    private static boolean wpBeaconInput = true;
    private static String wpColorInput = "AQUA";
    private static int editingWaypointIdx = -1;

    // Transient state for aliases
    private static String aliasCommandInput = "";
    private static String aliasActualInput = "";
    private static String editingAliasKey = null;

    // Transient state for chat triggers
    private static String triggerTextInput = "";
    private static String triggerCommandInput = "";
    private static String triggerTitleInput = "";
    private static int editingTriggerIdx = -1;

    // Transient state for custom party commands
    private static String customPartyTriggerInput = "";
    private static String customPartyCommandInput = "";
    private static int editingCustomPartyIdx = -1;

    // Transient state for coord binds
    private static String cbCoordsInput = "";
    private static String cbCommandInput = "";
    private static String cbIslandInput = "";
    private static String cbRadiusInput = "3";
    private static boolean cbShowWaypointInput = false;
    private static String cbMinDelayInput = "0";
    private static String cbMaxDelayInput = "0";
    private static int editingCoordBindIdx = -1;

    private static int colorPickerMode = 0; // 0 = Background, 1 = Border
    private static boolean isDraggingSv = false;
    private static boolean isDraggingHue = false;
    private static boolean isDraggingAlpha = false;
    private static float currentHue = 0.0f;

    public static boolean isTypingOrListening() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BomboConfigGUI gui) {
            if (listeningForKeyTarget != null && !listeningForKeyTarget.isEmpty()) {
                return true;
            }
            if (gui.activeBoxes != null) {
                for (EditBox box : gui.activeBoxes) {
                    if (box.isFocused()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double[] parseCoords(String input) {
        if (input == null || input.trim().isEmpty())
            return null;
        input = input.trim();
        if (input.contains(" tp @s ")) {
            String sub = input.substring(input.indexOf(" tp @s ") + 7);
            String[] parts = sub.trim().split("\\s+");
            if (parts.length >= 3) {
                try {
                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double z = Double.parseDouble(parts[2]);
                    return new double[] { x, y, z };
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (input.contains("tp ")) {
            String sub = input.substring(input.indexOf("tp ") + 3);
            String[] parts = sub.trim().split("\\s+");
            List<Double> parsed = new ArrayList<>();
            for (String part : parts) {
                try {
                    parsed.add(Double.parseDouble(part));
                    if (parsed.size() == 3) {
                        return new double[] { parsed.get(0), parsed.get(1), parsed.get(2) };
                    }
                } catch (NumberFormatException e) {
                    parsed.clear();
                }
            }
        }
        String[] parts = input.replaceAll(",", " ").trim().split("\\s+");
        if (parts.length >= 3) {
            try {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                return new double[] { x, y, z };
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static String colorPickerTarget = null;
    private static Consumer<String> colorPickerSetter = null;

    private double scrollAmount = 0;
    private double categoryScrollAmount = 0;
    private final List<Button> sidebarButtons = new ArrayList<>();

    public BomboConfigGUI(Screen parent) {
        super(Component.literal("Bomboaddons Configuration"));
        System.out.println("DEBUG: BomboConfigGUI constructor start");
        this.parent = parent;
        System.out.println("DEBUG: BomboConfigGUI constructor end");
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
        System.out.println("DEBUG: BomboConfigGUI init start");
        try {
            super.init();
            BomboConfig.Settings s = BomboConfig.get();
            activeBoxes.clear();
            clearWidgets();

            // 1. Sidebar Category Buttons
            sidebarButtons.clear();
            int renderCount = 0;
            int totalRendered = 0;
            for (int i = 0; i < categories.size(); i++) {
                if (s.hideCheats && (i == 2 || i == 9)) {
                    continue;
                }
                if (categories.get(i).equals("Party Settings")) {
                    continue;
                }
                totalRendered++;
            }
            int totalHeight = totalRendered * 26;
            int viewportHeight = height - (HEADER_HEIGHT + PADDING * 3) - PADDING;
            int maxCategoryScroll = Math.max(0, totalHeight - viewportHeight);
            categoryScrollAmount = Math.max(0, Math.min(categoryScrollAmount, maxCategoryScroll));

            for (int i = 0; i < categories.size(); i++) {
                final int idx = i;
                if (s.hideCheats && (idx == 2 || idx == 9)) {
                    continue; // Skip Experiments and Anvil
                }
                if (categories.get(idx).equals("Party Settings")) {
                    continue;
                }
                int catY = HEADER_HEIGHT + PADDING * 3 + renderCount * 26 - (int) categoryScrollAmount;
                renderCount++;
                String label = (idx == selectedCategory ? "§6§l> " : "§7") + categories.get(idx);

                Button btn = Button.builder(Component.literal(label), b -> {
                    selectedCategory = idx;
                    scrollAmount = 0;
                    confirmProfileDelete = false;
                    colorPickerTarget = null;
                    editingWaypointIdx = -1;
                    wpNameInput = "";
                    wpCoordsInput = "";
                    wpIslandInput = "";
                    wpThruWallsInput = true;
                    wpBeaconInput = true;
                    wpColorInput = "AQUA";
                    editingCoordBindIdx = -1;
                    cbCoordsInput = "";
                    cbCommandInput = "";
                    cbIslandInput = "";
                    cbRadiusInput = "3";
                    cbShowWaypointInput = false;
                    cbMinDelayInput = "0";
                    cbMaxDelayInput = "0";
                    init();
                }).bounds(PADDING, catY, SIDEBAR_WIDTH - PADDING * 2, 22).build();

                boolean visible = (catY + 22 > HEADER_HEIGHT + PADDING * 2) && (catY < height - PADDING);
                btn.active = visible;
                btn.visible = visible;

                addWidget(btn);
                sidebarButtons.add(btn);
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
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;

                    int y1 = contentBaseY + ITEM_HEIGHT;
                    y1 = addBoolOption("Sign Calculator", s.signCalculator, v -> s.signCalculator = v, col1X, col1W,
                            y1);
                    y1 = addBoolOption("SBE Commands", s.sbeCommands, v -> s.sbeCommands = v, col1X, col1W, y1);
                    y1 = addBoolOption("Copy Chat", s.copyChat, v -> s.copyChat = v, col1X, col1W, y1);
                    y1 = addBoolOption("Left Click Etherwarp", s.leftClickEtherwarp, v -> s.leftClickEtherwarp = v,
                            col1X, col1W, y1);
                    y1 = addBoolOption("Sphinx Macro", s.sphinxMacro, v -> s.sphinxMacro = v, col1X, col1W, y1);
                    y1 = addBoolOption("Hollow Wand Fix", s.hollowWandClickThrough, v -> s.hollowWandClickThrough = v,
                            col1X, col1W, y1);
                    y1 = addBoolOption("Hollow Wand Double Click", s.hollowWandAutoCombine,
                            v -> s.hollowWandAutoCombine = v, col1X, col1W, y1);
                    y1 = addBoolOption("Auto Accept Carnival", s.autoAcceptCarnival, v -> s.autoAcceptCarnival = v,
                            col1X, col1W, y1);
                    y1 = addBoolOption("Lowest BIN Tooltip", s.lowestBin, v -> s.lowestBin = v, col1X, col1W, y1);
                    y1 = addBoolOption("NPC Sell Price Tooltip", s.npcPrice, v -> s.npcPrice = v, col1X, col1W, y1);
                    y1 = addBoolOption("Auto Trevor Quest", s.autoTrevorQuest, v -> s.autoTrevorQuest = v, col1X, col1W,
                            y1);
                    y1 += 10;
                    y1 += ITEM_HEIGHT;
                    y1 = addBoolOption("Hoppity Egg Finder", s.eggFinder, v -> {
                        s.eggFinder = v;
                        if (!v)
                            me.bombo.bomboaddons.eggfinder.EggFinder.clearEggs();
                    }, col1X, col1W, y1);
                    y1 = addBoolOption("Egg Finder Chat Alerts", s.eggFinderChat, v -> s.eggFinderChat = v, col1X,
                            col1W, y1);
                    y1 = addBoolOption("Egg Finder Beacon", s.eggFinderBeacon, v -> s.eggFinderBeacon = v, col1X, col1W,
                            y1);
                    y1 = addBoolOption("Egg Finder Through Walls", s.eggFinderThroughWalls,
                            v -> s.eggFinderThroughWalls = v, col1X, col1W, y1);

                    int y2 = contentBaseY + ITEM_HEIGHT;
                    y2 = addBoolOption("Ignore Caps Lock", s.ignoreCapsLock, v -> s.ignoreCapsLock = v, col2X, col2W,
                            y2);
                    y2 = addBoolOption("Server List Button", s.serverListButton, v -> s.serverListButton = v, col2X,
                            col2W, y2);
                    y2 = addBoolOption("Reconnect Button", s.reconnectButton, v -> s.reconnectButton = v, col2X, col2W,
                            y2);
                    y2 = addBoolOption("Quick Join Commands (/f1, /m1, etc)", s.quickJoinCommands,
                            v -> s.quickJoinCommands = v, col2X, col2W, y2);
                    y2 = addBoolOption("Auto Reconnect", s.autoReconnect, v -> s.autoReconnect = v, col2X, col2W, y2);
                    partyCommandsX = col2X;
                    partyCommandsY = y2;
                    partyCommandsWidth = col2W;
                    partyCommandsHeight = ITEM_HEIGHT;
                    y2 = addBoolOption("Party Commands", s.partyCommandsEnabled, v -> s.partyCommandsEnabled = v, col2X,
                            col2W, y2);
                    y2 = addBoolOption("Nuh uh", s.bypassResourcePack, v -> s.bypassResourcePack = v, col2X, col2W, y2);
                    y2 = addBoolOption("Restore Item Models", s.restoreItemModels, v -> s.restoreItemModels = v, col2X,
                            col2W, y2);
                    y2 = addBoolOption("Hypixel Shortcut Button", s.hypixelShortcutButton,
                            v -> s.hypixelShortcutButton = v, col2X, col2W, y2);
                    y2 = addBoolOption("Smart Disconnect", s.smartDisconnect, v -> s.smartDisconnect = v, col2X, col2W,
                            y2);

                    y2 += 10;
                    y2 += ITEM_HEIGHT;
                    y2 = addBoolOption("Fuck Diorite", s.fuckDiorite, v -> s.fuckDiorite = v, col2X, col2W, y2);
                    y2 = addBoolOption("Fuck Diorite Pillar Color", s.fuckDioritePillarColor,
                            v -> s.fuckDioritePillarColor = v, col2X, col2W, y2);
                    y2 = addColorCycleButton("Fuck Diorite Color", s.fuckDioriteColor, v -> s.fuckDioriteColor = v,
                            col2X, col2W, y2);
                }
                case 1 -> { // HUDs
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;

                    int y1 = contentBaseY + ITEM_HEIGHT;
                    y1 = addBoolOption("Dice Tracker HUD", s.diceTracker, v -> s.diceTracker = v, col1X, col1W, y1);
                    y1 = addBoolOption("Feast Bakery HUD", s.feastBakeryHud, v -> s.feastBakeryHud = v, col1X, col1W,
                            y1);
                    y1 = addBoolOption("RNG Profit HUD", s.rngProfitHud, v -> s.rngProfitHud = v, col1X, col1W, y1);
                    y1 = addIntLabelSlider("RNG HUD Opacity", s.rngProfitHudOpacity, 0, 100, 10,
                            v -> s.rngProfitHudOpacity = v, col1X, col1W, y1);
                    y1 = addBoolOption("Custom Timers HUD", s.customTimerHudEnabled, v -> s.customTimerHudEnabled = v,
                            col1X, col1W, y1);

                    int y2 = contentBaseY + ITEM_HEIGHT;
                    y2 = addBoolOption("Custom Tooltip Background", s.customTooltipBg, v -> s.customTooltipBg = v,
                            col2X, col2W, y2);

                    int btnW = col2W / 2 - 2;
                    addRenderableWidget(Button
                            .builder(Component.literal(colorPickerMode == 0 ? "§a§lBg Color" : "§7Bg Color"), btn -> {
                                colorPickerMode = 0;
                                int currentRgb = (s.tooltipBgColor & 0xFFFFFF);
                                float[] hsvTemp = rgbToHsv((currentRgb >> 16) & 0xFF, (currentRgb >> 8) & 0xFF,
                                        currentRgb & 0xFF);
                                currentHue = hsvTemp[0];
                                init();
                            }).bounds(col2X, y2, btnW, 16).build());

                    addRenderableWidget(Button
                            .builder(Component.literal(colorPickerMode == 1 ? "§a§lBorder Color" : "§7Border Color"),
                                    btn -> {
                                        colorPickerMode = 1;
                                        int currentRgb = (s.tooltipBorderColor & 0xFFFFFF);
                                        float[] hsvTemp = rgbToHsv((currentRgb >> 16) & 0xFF, (currentRgb >> 8) & 0xFF,
                                                currentRgb & 0xFF);
                                        currentHue = hsvTemp[0];
                                        init();
                                    })
                            .bounds(col2X + btnW + 4, y2, btnW, 16).build());

                    y2 += 20;

                    int pickerY = y2;
                    addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
                        s.tooltipBgColor = 0xF0100010;
                        s.tooltipBorderColor = 0x505000FF;
                        int currentRgb = colorPickerMode == 0 ? (s.tooltipBgColor & 0xFFFFFF)
                                : (s.tooltipBorderColor & 0xFFFFFF);
                        float[] hsvTemp = rgbToHsv((currentRgb >> 16) & 0xFF, (currentRgb >> 8) & 0xFF,
                                currentRgb & 0xFF);
                        currentHue = hsvTemp[0];
                        BomboConfig.save();
                        init();
                    }).bounds(col2X + 126, pickerY + 63, 40, 16).build());

                    y2 += 90;

                    int activeRgb = colorPickerMode == 0 ? (s.tooltipBgColor & 0xFFFFFF)
                            : (s.tooltipBorderColor & 0xFFFFFF);
                    float[] hsv = rgbToHsv((activeRgb >> 16) & 0xFF, (activeRgb >> 8) & 0xFF, activeRgb & 0xFF);
                    if (!isDraggingHue && !isDraggingSv) {
                        currentHue = hsv[0];
                    }

                    curY = Math.max(y1, y2);
                    curY += 10;
                    addRenderableWidget(Button.builder(Component.literal("§e§lMove HUD Elements"), btn -> {
                        Minecraft.getInstance().setScreenAndShow(new HudMoveScreen());
                    }).bounds(contentX, curY, contentWidth / 2, 20).build());
                    curY += 30;
                }
                case 2 -> { // Experiments
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Auto Experiments", s.autoExperiments, v -> {
                        s.autoExperiments = v;
                        if (!v)
                            AutoExperiments.reset();
                    }, contentX, contentWidth, curY);
                    curY = addIntLabelSlider("Click Delay", s.experimentClickDelay, 0, 2000, 50,
                            v -> s.experimentClickDelay = v, contentX, 150, curY);
                    curY = addIntLabelSlider("Serum Count", s.experimentSerumCount, 0, 3, 1,
                            v -> s.experimentSerumCount = v, contentX, 150, curY);
                    curY = addBoolOption("Auto Close", s.experimentAutoClose, v -> s.experimentAutoClose = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Get Max XP", s.experimentGetMaxXp, v -> s.experimentGetMaxXp = v, contentX,
                            contentWidth, curY);

                    curY += 5;
                    String[] typeNames = { "Left-Click", "Middle-Click", "Shift-Click" };
                    String typeLabel = "Click Type: " + typeNames[Math.max(0, Math.min(2, s.experimentClickType))];
                    addRenderableWidget(Button.builder(Component.literal(typeLabel), btn -> {
                        s.experimentClickType = (s.experimentClickType + 1) % 3;
                        BomboConfig.save();
                        init();
                    }).bounds(contentX, curY, 150, 20).build());
                    curY += ITEM_HEIGHT + 5;
                }
                case 3 -> { // Garden
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;

                    int y1 = contentBaseY + ITEM_HEIGHT;
                    y1 = addBoolOption("Garden Movement", s.gardenMovement, v -> {
                        s.gardenMovement = v;
                        if (!v)
                            GardenMovement.reset();
                    }, col1X, col1W, y1);
                    y1 = addBoolOption("Lock Mouse on Movement", s.lockMouseOnGarden, v -> s.lockMouseOnGarden = v,
                            col1X, col1W, y1);
                    y1 = addBoolOption("Sugar Cane Mode", s.gardenSugarCane, v -> s.gardenSugarCane = v, col1X, col1W,
                            y1);
                    y1 = addBoolOption("Direction Helper Warning", s.gardenDirectionHelper,
                            v -> s.gardenDirectionHelper = v, col1X, col1W, y1);
                    y1 = addBoolOption("Macro Check Detector", s.gardenMacroCheckDetector,
                            v -> s.gardenMacroCheckDetector = v, col1X, col1W, y1);
                    if (s.gardenMacroCheckDetector) {
                        y1 = addBoolOption("Stop Movement on Check", s.gardenMacroCheckStop,
                                v -> s.gardenMacroCheckStop = v, col1X, col1W, y1);
                        y1 = addCycleOption("Alarm Sound", s.gardenMacroCheckSound,
                                List.of("Anvil", "Pling", "Wither", "Explode"), v -> s.gardenMacroCheckSound = v, col1X,
                                col1W, y1);
                        y1 = addIntLabelSlider("Sound Repeats", s.gardenMacroCheckSoundCount, 1, 50, 1,
                                v -> s.gardenMacroCheckSoundCount = v, col1X, col1W, y1);
                        y1 = addIntLabelSlider("Sound Delay (ms)", s.gardenMacroCheckSoundDelay, 100, 2000, 50,
                                v -> s.gardenMacroCheckSoundDelay = v, col1X, col1W, y1);
                    }
                    y1 += 10;
                    y1 = addKeyBindButton("Forward", s.gardenForwardKey, v -> s.gardenForwardKey = v, "gardenF", col1X,
                            col1W, y1);
                    y1 = addKeyBindButton("Backward", s.gardenBackwardKey, v -> s.gardenBackwardKey = v, "gardenB",
                            col1X, col1W, y1);
                    y1 = addKeyBindButton("Left", s.gardenLeftKey, v -> s.gardenLeftKey = v, "gardenL", col1X, col1W,
                            y1);
                    y1 = addKeyBindButton("Right", s.gardenRightKey, v -> s.gardenRightKey = v, "gardenR", col1X, col1W,
                            y1);
                    y1 = addKeyBindButton("Break", s.gardenBreakKey, v -> s.gardenBreakKey = v, "gardenBr", col1X,
                            col1W, y1);
                    y1 = addKeyBindButton("Use", s.gardenUseKey, v -> s.gardenUseKey = v, "gardenU", col1X, col1W, y1);

                    int y2 = contentBaseY + ITEM_HEIGHT;
                    y2 = addBoolOption("Pest ESP Enabled", s.pestEsp, v -> s.pestEsp = v, col2X, col2W, y2);
                    y2 = addBoolOption("Pest Waypoints", s.pestSpawnWaypoint, v -> s.pestSpawnWaypoint = v, col2X,
                            col2W, y2);
                    y2 = addBoolOption("Remove Waypoint On Return", s.pestWaypointRemoveOnNear,
                            v -> s.pestWaypointRemoveOnNear = v, col2X, col2W, y2);
                    y2 = addBoolOption("Pest Waypoint Beacon", s.pestWaypointBeacon, v -> s.pestWaypointBeacon = v,
                            col2X, col2W, y2);
                    y2 = addIntLabelSlider("Pest Waypoint Duration", s.pestWaypointDuration, 0, 600, 10,
                            v -> s.pestWaypointDuration = v, col2X, col2W, y2);
                    y2 = addBoolOption("Pest Tracers", s.pestEspTracer, v -> s.pestEspTracer = v, col2X, col2W, y2);
                    y2 = addColorCycleButton("Pest Color", s.pestEspColor, v -> s.pestEspColor = v, col2X, col2W, y2);
                    y2 = addFloatLabelSlider("Pest Size", s.pestEspThickness, 0.5f, 5.0f, v -> s.pestEspThickness = v,
                            col2X, col2W, y2);
                }
                case 4 -> { // Hotkeys
                    curY += ITEM_HEIGHT;
                    curY = addKeyBindButton("Trade", s.tradeKey, v -> s.tradeKey = v, "trade", contentX, contentWidth,
                            curY);
                    curY = addKeyBindButton("Recipe", s.recipeKey, v -> s.recipeKey = v, "recipe", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Usage", s.usageKey, v -> s.usageKey = v, "usage", contentX, contentWidth,
                            curY);
                    curY = addKeyBindButton("Show Info", s.showItemKey, v -> s.showItemKey = v, "showItem", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Count Items", s.countItemKey, v -> s.countItemKey = v, "countItem",
                            contentX, contentWidth, curY);
                    curY = addKeyBindButton("Copy NBT", s.copyNbtKey, v -> s.copyNbtKey = v, "copyNbt", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("GFS Max", s.gfsMaxKey, v -> s.gfsMaxKey = v, "gfsMax", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("GFS Stack", s.gfsStackKey, v -> s.gfsStackKey = v, "gfsStack", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Chat Peek", s.chatPeekKey, v -> s.chatPeekKey = v, "chatPeek", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Next Page", s.nextPageKey, v -> s.nextPageKey = v, "nextPage", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Prev Page", s.prevPageKey, v -> s.prevPageKey = v, "prevPage", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Go Back", s.goBackKey, v -> s.goBackKey = v, "goBack", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Smart Back", s.smartGoBackKey, v -> s.smartGoBackKey = v, "smartBack",
                            contentX, contentWidth, curY);
                    curY = addKeyBindButton("Save Pet", s.savePetKey, v -> s.savePetKey = v, "savePet", contentX,
                            contentWidth, curY);
                    curY = addKeyBindButton("Run Clipboard Cmd", s.clipboardRunKey, v -> s.clipboardRunKey = v,
                            "clipboardRun", contentX, contentWidth, curY);
                }
                case 6 -> { // Clicker
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Auto GUI Clicker", s.autoClicker, v -> s.autoClicker = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Keypress Clicker", s.chestClicker, v -> s.chestClicker = v, contentX,
                            contentWidth, curY);
                    curY += 10;
                    curY = addTextBox("GUI Name", clickGuiInput, v -> clickGuiInput = v, contentX, contentWidth, curY);
                    curY = addTextBox("Item Name", clickItemInput, v -> clickItemInput = v, contentX, contentWidth,
                            curY);
                    curY = addKeyBindButton("Key", clickKeyInput, v -> clickKeyInput = v, "clicker", contentX,
                            contentWidth, curY);

                    int finalCurY = curY;
                    String addBtnText = editingClickTargetIdx != -1 ? "§e✔ Save Target" : "§a+ Add Target";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!clickGuiInput.isEmpty() && !clickKeyInput.isEmpty()) {
                            if (editingClickTargetIdx != -1) {
                                ClickLogic.updateTarget(editingClickTargetIdx, clickItemInput, clickGuiInput,
                                        clickKeyInput, clickTypeInput, false);
                                editingClickTargetIdx = -1;
                            } else {
                                ClickLogic.setTarget(clickItemInput, clickGuiInput, clickKeyInput, clickTypeInput,
                                        false);
                            }
                            clickGuiInput = "";
                            clickKeyInput = "";
                            clickItemInput = "";
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingClickTargetIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingClickTargetIdx = -1;
                            clickGuiInput = "";
                            clickKeyInput = "";
                            clickItemInput = "";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35; // Space before list
                    int listStartY = curY;
                    for (int i = 0; i < ClickLogic.getTargets().size(); i++) {
                        final int idx = i;
                        ClickLogic.ClickTarget target = ClickLogic.getTargets().get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingClickTargetIdx = idx;
                                clickGuiInput = target.gui;
                                clickItemInput = target.item;
                                clickKeyInput = target.keyName;
                                init();
                            }).bounds(contentX + 180, itemY + 7, 40, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                ClickLogic.removeTarget(idx);
                                init();
                            }).bounds(contentX + 225, itemY + 7, 35, 18).build());
                        }
                    }
                }
                case 5 -> { // Profiles
                    curY += ITEM_HEIGHT;

                    List<String> profiles = new ArrayList<>(s.profileBinds.keySet());
                    if (!profiles.contains("default"))
                        profiles.add(0, "default");
                    int currentIdx = profiles.indexOf(s.activeProfile);

                    addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = profiles.get(next);
                        confirmProfileDelete = false;
                        BomboConfig.save();
                        init();
                    }).bounds(contentX + 150, curY, 20, 20).build());

                    addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = profiles.get(next);
                        confirmProfileDelete = false;
                        BomboConfig.save();
                        init();
                    }).bounds(contentX + 175, curY, 20, 20).build());

                    if (!s.activeProfile.equals("default") && !s.activeProfile.equals("General")) {
                        String delText = confirmProfileDelete ? "§c§lCONFIRM?" : "§cDEL";
                        addRenderableWidget(Button.builder(Component.literal(delText), btn -> {
                            if (confirmProfileDelete) {
                                s.profileBinds.remove(s.activeProfile);
                                s.keybindBinds.remove(s.activeProfile);
                                s.customWaypoints.remove(s.activeProfile);
                                s.profileChatTriggers.remove(s.activeProfile);
                                s.coordBinds.remove(s.activeProfile);
                                s.activeProfile = "default";
                                BomboConfig.save();
                                confirmProfileDelete = false;
                                init();
                            } else {
                                confirmProfileDelete = true;
                                init();
                            }
                        }).bounds(contentX + 200, curY, confirmProfileDelete ? 65 : 30, 20).build());
                    }

                    curY += ITEM_HEIGHT + 5;

                    // Create Profile
                    curY = addTextBox("Create New Profile", profileNameInput, v -> profileNameInput = v, contentX,
                            contentWidth - 60, curY);
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
                    curY = addTextBox("Command", bindCommandInput, v -> bindCommandInput = v, contentX, contentWidth,
                            curY);
                    curY = addComboBindButton("Combo", bindComboInput, v -> bindComboInput = v, "profileCombo",
                            contentX, contentWidth, curY);
                    int finalCurY = curY;
                    String addBindText = editingKeybindIdx != -1 ? "§e✔ Save Bind" : "§a+ Add Bind";
                    addRenderableWidget(Button.builder(Component.literal(addBindText), btn -> {
                        if (!bindCommandInput.isEmpty() && !bindComboInput.isEmpty()) {
                            List<Integer> codes = parseCombo(bindComboInput);
                            if (!codes.isEmpty()) {
                                s.profileBinds.putIfAbsent(s.activeProfile, new ArrayList<>());
                                if (editingKeybindIdx != -1) {
                                    s.profileBinds.get(s.activeProfile).set(editingKeybindIdx,
                                            new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                                    editingKeybindIdx = -1;
                                } else {
                                    s.profileBinds.get(s.activeProfile)
                                            .add(new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                                }
                                BomboConfig.save();
                                bindCommandInput = "";
                                bindComboInput = "";
                                init();
                            }
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingKeybindIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingKeybindIdx = -1;
                            bindCommandInput = "";
                            bindComboInput = "";
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
                            int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                            if (itemY > listStartY + 15 && itemY < height - 20) {
                                addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                    editingKeybindIdx = idx;
                                    bindCommandInput = bind.command;
                                    bindComboInput = bind.keyName;
                                    init();
                                }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                                addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                    binds.remove(idx);
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                            }
                        }
                    }
                }
                case 7 -> { // Highlights
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Highlights Enabled", s.highlightsEnabled, v -> s.highlightsEnabled = v,
                            contentX, contentWidth, curY);
                    curY += 10;
                    curY = addTextBox("Mob Name", highMobInput, v -> highMobInput = v, contentX, contentWidth, curY);
                    curY = addColorCycleButton("Color", highColorInput, v -> highColorInput = v, contentX, contentWidth,
                            curY);

                    int finalCurY = curY;
                    String addBtnText = editingHighMob != null ? "§e✔ Save Highlight" : "§a+ Add Highlight";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!highMobInput.isEmpty()) {
                            if (editingHighMob != null) {
                                s.highlights.remove(editingHighMob);
                            }
                            s.highlights.put(highMobInput.toLowerCase(),
                                    new BomboConfig.HighlightInfo(highColorInput.toUpperCase(), true));
                            BomboConfig.save();
                            highMobInput = "";
                            highColorInput = "GOLD";
                            editingHighMob = null;
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingHighMob != null) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel Edit"), btn -> {
                            highMobInput = "";
                            highColorInput = "GOLD";
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
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                            String toggleLabel = info.enabled ? "§aON" : "§cOFF";
                            addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                info.enabled = !info.enabled;
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingHighMob = mobName;
                                highMobInput = mobName;
                                highColorInput = s.highlights.get(mobName).color;
                                init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                s.highlights.remove(mobName);
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 8 -> { // Wardrobe
                    curY += ITEM_HEIGHT;
                    if (!s.hideCheats) {
                        curY = addBoolOption("Auto Close Wardrobe", s.autoCloseWardrobe, v -> s.autoCloseWardrobe = v,
                                contentX, contentWidth, curY);
                    }
                    curY = addBoolOption("Disable Unequip", s.disableUnequipWardrobe, v -> s.disableUnequipWardrobe = v,
                            contentX, contentWidth, curY);
                    for (int i = 0; i < 9; i++) {
                        final int index = i;
                        curY = addKeyBindButton("Slot " + (i + 1), s.wardrobeKeys.get(i),
                                v -> s.wardrobeKeys.set(index, v), "wardrobe" + i, contentX, contentWidth, curY);
                    }
                }
                case 9 -> { // Anvil
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Auto Combine", s.anvilAutoCombineEnabled, v -> s.anvilAutoCombineEnabled = v,
                            contentX, contentWidth, curY);
                    curY = addIntLabelSlider("Delay: " + s.anvilAutoCombineDelay + "ms", s.anvilAutoCombineDelay, 50,
                            1000, 50, v -> s.anvilAutoCombineDelay = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Require Keybind", s.anvilAutoCombineRequireKey,
                            v -> s.anvilAutoCombineRequireKey = v, contentX, contentWidth, curY);
                    curY = addKeyBindButton("Trigger Key", s.anvilAutoCombineKey, v -> s.anvilAutoCombineKey = v,
                            "anvilTrigger", contentX, contentWidth, curY);
                    curY += 20;
                    int listStartY = curY;
                    List<String> sortedEnchants = new ArrayList<>(s.anvilAutoCombine.keySet());
                    Collections.sort(sortedEnchants);
                    for (int i = 0; i < sortedEnchants.size(); i++) {
                        final String enc = sortedEnchants.get(i);
                        int itemY = listStartY + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY - 10 && itemY < height - 50) {
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                s.anvilAutoCombine.remove(enc);
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 160, itemY, 35, 18).build());
                        }
                    }
                }
                case 10 -> { // Debug
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("MASTER DEBUG", s.debugMaster, v -> s.debugMaster = v, contentX, contentWidth,
                            curY);

                    addRenderableWidget(Button.builder(Component.literal("§a[Enable All Debug]"), btn -> {
                        s.debugChat = true;
                        s.debugGuis = true;
                        s.debugEntities = true;
                        s.debugCommands = true;
                        s.debugMaster = true;
                        s.debugParticles = true;
                    }).bounds(contentX, curY, contentWidth, 20).build());
                    curY += ITEM_HEIGHT + 4;

                    curY = addBoolOption("Chat Debug", s.debugChat, v -> s.debugChat = v, contentX, contentWidth, curY);
                    curY = addBoolOption("GUIs Debug", s.debugGuis, v -> s.debugGuis = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Entities Debug", s.debugEntities, v -> s.debugEntities = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Command Debug", s.debugCommands, v -> s.debugCommands = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Debug Mode (Legacy)", s.debugMode, v -> s.debugMode = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("API Debug", s.apiDebug, v -> s.apiDebug = v, contentX, contentWidth, curY);
                    curY = addBoolOption("API Chat Messages", s.apiChatMessages, v -> s.apiChatMessages = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("LB Debug", s.lbDebug, v -> s.lbDebug = v, contentX, contentWidth, curY);
                    curY = addBoolOption("Particle Debug", s.debugParticles, v -> s.debugParticles = v, contentX,
                            contentWidth, curY);
                }
                case 11 -> { // Kuudra
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Blindness Timer", s.kuudraBlindnessTimer, v -> s.kuudraBlindnessTimer = v,
                            contentX, contentWidth, curY);
                    curY = addBoolOption("Disable Blindness", s.disableBlindness, v -> s.disableBlindness = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Perk Menu Clicker", s.perkMenuClicker, v -> s.perkMenuClicker = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Auto GFS Toxic", s.autoGfsToxic, v -> s.autoGfsToxic = v, contentX,
                            contentWidth, curY);
                    curY = addIntLabelSlider("Toxic Count", s.autoGfsToxicCount, 1, 64, 1, v -> s.autoGfsToxicCount = v,
                            contentX, 150, curY);
                    curY = addBoolOption("Auto GFS Twilight", s.autoGfsTwilight, v -> s.autoGfsTwilight = v, contentX,
                            contentWidth, curY);

                    curY += 10;
                    curY = addBoolOption("Pearl Waypoints & Timers", s.pearlCalculator, v -> s.pearlCalculator = v,
                            contentX, contentWidth, curY);
                    curY = addBoolOption("Show Pearl Throw Timer", s.showTimer, v -> s.showTimer = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Show All Pearl Spots", s.showAll, v -> s.showAll = v, contentX, contentWidth,
                            curY);
                    curY = addBoolOption("Show Sky Pearl Spots", s.showSkyPearls, v -> s.showSkyPearls = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Show Flat Pearl Spots", s.showFlatPearls, v -> s.showFlatPearls = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Show Double Pearl Spots", s.showDoublePearls, v -> s.showDoublePearls = v,
                            contentX, contentWidth, curY);
                    curY = addBoolOption("Kuudra Debug Mode", s.kuudraDebug, v -> s.kuudraDebug = v, contentX,
                            contentWidth, curY);
                    curY = addIntLabelSlider("Talisman Tier (0-3)", s.kuudraTalisman, 0, 3, 1,
                            v -> s.kuudraTalisman = v, contentX, 150, curY);
                }
                case 12 -> { // Pets
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Disable Unequip", s.disableUnequipPet, v -> s.disableUnequipPet = v, contentX,
                            contentWidth, curY);
                    for (int i = 0; i < 9; i++) {
                        final int index = i;
                        boolean listening = listeningForKeyTarget.equals("pets" + index);
                        String currentKey = s.petKeys.get(index);
                        String displayKey = ClickLogic.getKeyDisplayName(currentKey);
                        String keyText = listening ? "§e[PRESS]" : (currentKey.isEmpty() ? "None" : displayKey);

                        addRenderableWidget(Button.builder(Component.literal(keyText), btn -> {
                            listeningForKeyTarget = "pets" + index;
                            init();
                        }).bounds(contentX + 110, curY, 80, 18).build());

                        // Up button (▲)
                        Button upBtn = Button.builder(Component.literal("▲"), btn -> {
                            swapPetSlots(index, index - 1);
                            init();
                        }).bounds(contentX + 195, curY, 20, 18).build();
                        if (index == 0)
                            upBtn.active = false;
                        addRenderableWidget(upBtn);

                        // Down button (▼)
                        Button downBtn = Button.builder(Component.literal("▼"), btn -> {
                            swapPetSlots(index, index + 1);
                            init();
                        }).bounds(contentX + 218, curY, 20, 18).build();
                        if (index == 8)
                            downBtn.active = false;
                        addRenderableWidget(downBtn);

                        // Delete button (✕)
                        Button delBtn = Button.builder(Component.literal("✕"), btn -> {
                            clearPetSlot(index);
                            init();
                        }).bounds(contentX + 241, curY, 20, 18).build();
                        String uuid = s.petKeybinds.get(String.valueOf(index + 1));
                        if (uuid == null || uuid.isEmpty())
                            delBtn.active = false;
                        addRenderableWidget(delBtn);

                        curY += ITEM_HEIGHT;
                    }
                }
                case 13 -> { // Keybinds
                    curY += ITEM_HEIGHT; // Title row

                    // Profile Switcher row
                    List<String> profiles = new ArrayList<>(s.profileBinds.keySet());
                    if (!profiles.contains("default"))
                        profiles.add(0, "default");
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

                    curY += ITEM_HEIGHT + 5; // Active profile row

                    curY = addTextBox("Command", bindCommandInput, v -> bindCommandInput = v, contentX, contentWidth,
                            curY);
                    curY += 5;
                    curY = addComboBindButton("Combo", bindComboInput, v -> bindComboInput = v, "profileCombo",
                            contentX, contentWidth, curY);
                    curY += 5;
                    curY = addTextBox("Only on Island", bindIslandInput, v -> bindIslandInput = v, contentX,
                            contentWidth, curY);
                    curY += 5;
                    curY = addTextBox("Only with Armor", bindArmorInput, v -> bindArmorInput = v, contentX,
                            contentWidth, curY);
                    curY += 5;

                    int finalCurY = curY;
                    String addBindText = editingKeybindIdx != -1 ? "§e✔ Save Bind" : "§a+ Add Bind";
                    addRenderableWidget(Button.builder(Component.literal(addBindText), btn -> {
                        if (!bindCommandInput.isEmpty() && !bindComboInput.isEmpty()) {
                            List<Integer> codes = parseCombo(bindComboInput);
                            if (!codes.isEmpty()) {
                                s.keybindBinds.putIfAbsent(s.activeProfile, new ArrayList<>());
                                if (editingKeybindIdx != -1) {
                                    s.keybindBinds.get(s.activeProfile).set(editingKeybindIdx,
                                            new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput,
                                                    bindIslandInput, bindArmorInput));
                                    editingKeybindIdx = -1;
                                } else {
                                    s.keybindBinds.get(s.activeProfile).add(new BomboConfig.CommandBind(
                                            bindCommandInput, codes, bindComboInput, bindIslandInput, bindArmorInput));
                                }
                                BomboConfig.save();
                                bindCommandInput = "";
                                bindComboInput = "";
                                bindIslandInput = "";
                                bindArmorInput = "";
                                init();
                            }
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingKeybindIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingKeybindIdx = -1;
                            bindCommandInput = "";
                            bindComboInput = "";
                            bindIslandInput = "";
                            bindArmorInput = "";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35;
                    int listStartY = curY;
                    List<BomboConfig.CommandBind> binds = s.keybindBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (int i = 0; i < binds.size(); i++) {
                            final int idx = i;
                            BomboConfig.CommandBind bind = binds.get(idx);
                            int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                            if (itemY > listStartY + 15 && itemY < height - 20) {
                                String toggleLabel = bind.enabled ? "§aON" : "§cOFF";
                                addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                    bind.enabled = !bind.enabled;
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 130, itemY + 5, 45, 18).build());

                                addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                    editingKeybindIdx = idx;
                                    bindCommandInput = bind.command;
                                    bindComboInput = bind.keyName;
                                    bindIslandInput = bind.requiredIsland != null ? bind.requiredIsland : "";
                                    bindArmorInput = bind.requiredArmor != null ? bind.requiredArmor : "";
                                    init();
                                }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                                addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                    binds.remove(idx);
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                            }
                        }
                    }
                }
                case 14 -> { // Waypoints
                    curY += ITEM_HEIGHT;

                    // Profile Switcher row
                    List<String> profiles = new ArrayList<>(s.profileBinds.keySet());
                    if (!profiles.contains("default"))
                        profiles.add(0, "default");
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

                    curY = addTextBox("Name", wpNameInput, v -> wpNameInput = v, contentX, contentWidth, curY);
                    curY += 5;
                    curY = addTextBox("Coords (X Y Z)", wpCoordsInput, v -> wpCoordsInput = v, contentX, contentWidth,
                            curY);
                    curY += 5;
                    curY = addTextBox("Only on Island", wpIslandInput, v -> wpIslandInput = v, contentX, contentWidth,
                            curY);
                    curY += 5;
                    curY = addBoolOption("Show Through Walls", wpThruWallsInput, v -> wpThruWallsInput = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Show Beacon", wpBeaconInput, v -> wpBeaconInput = v, contentX, contentWidth,
                            curY);
                    curY = addColorCycleButton("Color", wpColorInput, v -> wpColorInput = v, contentX, contentWidth,
                            curY);

                    int finalCurY = curY;
                    String addBtnText = editingWaypointIdx != -1 ? "§e✔ Save Waypoint" : "§a+ Add Waypoint";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!wpNameInput.isEmpty() && !wpCoordsInput.isEmpty()) {
                            double[] parsed = parseCoords(wpCoordsInput);
                            if (parsed != null) {
                                s.customWaypoints.putIfAbsent(s.activeProfile, new ArrayList<>());
                                BomboConfig.CustomWaypoint cwp = new BomboConfig.CustomWaypoint(wpNameInput, parsed[0],
                                        parsed[1], parsed[2], wpIslandInput, wpThruWallsInput, wpBeaconInput,
                                        wpColorInput);
                                if (editingWaypointIdx != -1) {
                                    s.customWaypoints.get(s.activeProfile).set(editingWaypointIdx, cwp);
                                    editingWaypointIdx = -1;
                                } else {
                                    s.customWaypoints.get(s.activeProfile).add(cwp);
                                }
                                BomboConfig.save();
                                wpNameInput = "";
                                wpCoordsInput = "";
                                wpIslandInput = "";
                                wpThruWallsInput = true;
                                wpBeaconInput = true;
                                wpColorInput = "AQUA";
                                init();
                            }
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingWaypointIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingWaypointIdx = -1;
                            wpNameInput = "";
                            wpCoordsInput = "";
                            wpIslandInput = "";
                            wpThruWallsInput = true;
                            wpBeaconInput = true;
                            wpColorInput = "AQUA";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35;
                    int listStartY = curY;
                    List<BomboConfig.CustomWaypoint> wps = s.customWaypoints.get(s.activeProfile);
                    if (wps != null) {
                        for (int i = 0; i < wps.size(); i++) {
                            final int idx = i;
                            BomboConfig.CustomWaypoint wp = wps.get(idx);
                            int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                            if (itemY > listStartY + 15 && itemY < height - 20) {
                                String toggleLabel = wp.enabled ? "§aON" : "§cOFF";
                                addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                    wp.enabled = !wp.enabled;
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 130, itemY + 5, 45, 18).build());

                                addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                    editingWaypointIdx = idx;
                                    wpNameInput = wp.name;
                                    wpCoordsInput = String.format("%.2f %.2f %.2f", wp.x, wp.y, wp.z);
                                    wpIslandInput = wp.requiredIsland != null ? wp.requiredIsland : "";
                                    wpThruWallsInput = wp.showThroughWalls;
                                    wpBeaconInput = wp.showBeacon;
                                    wpColorInput = wp.color != null ? wp.color : "AQUA";
                                    init();
                                }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                                addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                    wps.remove(idx);
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                            }
                        }
                    }
                }
                case 15 -> { // Aliases
                    curY += ITEM_HEIGHT;
                    curY = addTextBox("Alias", aliasCommandInput, v -> aliasCommandInput = v, contentX, contentWidth,
                            curY);
                    curY = addTextBox("Command", aliasActualInput, v -> aliasActualInput = v, contentX, contentWidth,
                            curY);

                    int finalCurY = curY;
                    String addBtnText = editingAliasKey != null ? "§e✔ Save Alias" : "§a+ Add Alias";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!aliasCommandInput.isEmpty() && !aliasActualInput.isEmpty()) {
                            String cleanAlias = aliasCommandInput.startsWith("/") ? aliasCommandInput.substring(1)
                                    : aliasCommandInput;
                            if (editingAliasKey != null) {
                                s.commandAliases.remove(editingAliasKey);
                                editingAliasKey = null;
                            }
                            s.commandAliases.put(cleanAlias, aliasActualInput);
                            BomboConfig.save();
                            BomboaddonsClient.registerAllAliases();
                            aliasCommandInput = "";
                            aliasActualInput = "";
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingAliasKey != null) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingAliasKey = null;
                            aliasCommandInput = "";
                            aliasActualInput = "";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35;
                    int listStartY = curY;
                    List<String> sortedAliases = new ArrayList<>(s.commandAliases.keySet());
                    Collections.sort(sortedAliases);
                    for (int i = 0; i < sortedAliases.size(); i++) {
                        final int idx = i;
                        final String aliasKey = sortedAliases.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingAliasKey = aliasKey;
                                aliasCommandInput = aliasKey;
                                aliasActualInput = s.commandAliases.get(aliasKey);
                                init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                s.commandAliases.remove(aliasKey);
                                BomboConfig.save();
                                BomboaddonsClient.registerAllAliases();
                                init();
                            }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 16 -> { // Chat Triggers
                    curY += ITEM_HEIGHT;

                    // Profile Switcher row
                    List<String> profiles = new ArrayList<>(s.profileBinds.keySet());
                    if (!profiles.contains("default"))
                        profiles.add(0, "default");
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

                    curY = addTextBox("If Chat Contains", triggerTextInput, v -> triggerTextInput = v, contentX,
                            contentWidth, curY);
                    curY = addTextBox("Run Command", triggerCommandInput, v -> triggerCommandInput = v, contentX,
                            contentWidth, curY);
                    curY = addTextBox("Show Title", triggerTitleInput, v -> triggerTitleInput = v, contentX,
                            contentWidth, curY);

                    List<BomboConfig.ChatTrigger> triggers = s.profileChatTriggers.computeIfAbsent(s.activeProfile,
                            k -> new ArrayList<>());

                    int finalCurY = curY;
                    String addBtnText = editingTriggerIdx != -1 ? "§e✔ Save Trigger" : "§a+ Add Trigger";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!triggerTextInput.isEmpty()
                                && (!triggerCommandInput.isEmpty() || !triggerTitleInput.isEmpty())) {
                            BomboConfig.ChatTrigger ct = new BomboConfig.ChatTrigger(triggerTextInput,
                                    triggerCommandInput, triggerTitleInput);
                            if (editingTriggerIdx != -1) {
                                triggers.set(editingTriggerIdx, ct);
                                editingTriggerIdx = -1;
                            } else {
                                triggers.add(ct);
                            }
                            BomboConfig.save();
                            triggerTextInput = "";
                            triggerCommandInput = "";
                            triggerTitleInput = "";
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingTriggerIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingTriggerIdx = -1;
                            triggerTextInput = "";
                            triggerCommandInput = "";
                            triggerTitleInput = "";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35;
                    int listStartY = curY;
                    for (int i = 0; i < triggers.size(); i++) {
                        final int idx = i;
                        BomboConfig.ChatTrigger trigger = triggers.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            String toggleLabel = trigger.enabled ? "§aON" : "§cOFF";
                            addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                trigger.enabled = !trigger.enabled;
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 130, itemY + 5, 45, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingTriggerIdx = idx;
                                triggerTextInput = trigger.triggerText;
                                triggerCommandInput = trigger.commandToRun;
                                triggerTitleInput = trigger.titleToShow;
                                init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                triggers.remove(idx);
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 17 -> { // Dungeons
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Pad Timers Purple", s.padTimersPurple, v -> s.padTimersPurple = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Pad Timers Green", s.padTimersGreen, v -> s.padTimersGreen = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("Dungeon Big Hitbox", s.dungeonBigHitbox, v -> s.dungeonBigHitbox = v,
                            contentX, contentWidth, curY);
                    curY = addFloatLabelSlider("Purple Timer (s)", (float) s.padTimerPurpleTime, 1.0f, 10.0f,
                            v -> s.padTimerPurpleTime = (double) v, contentX, 150, curY);
                    curY += 10;
                    addRenderableWidget(Button.builder(Component.literal("§e§lMove HUD Elements"), btn -> {
                        Minecraft.getInstance().setScreenAndShow(new HudMoveScreen());
                    }).bounds(contentX, curY, contentWidth / 2, 20).build());
                    curY += 30;
                }
                case 18 -> { // Coord Binds
                    curY += ITEM_HEIGHT;

                    // Profile Switcher row
                    List<String> profiles = new ArrayList<>(s.profileBinds.keySet());
                    if (!profiles.contains("default"))
                        profiles.add(0, "default");
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

                    curY = addTextBox("Coords (X Y Z)", cbCoordsInput, v -> cbCoordsInput = v, contentX, contentWidth,
                            curY);
                    curY += 5;
                    curY = addTextBox("Command", cbCommandInput, v -> cbCommandInput = v, contentX, contentWidth, curY);
                    curY += 5;
                    curY = addTextBox("Only on Island", cbIslandInput, v -> cbIslandInput = v, contentX, contentWidth,
                            curY);
                    curY += 5;
                    curY = addTextBox("Radius", cbRadiusInput, v -> cbRadiusInput = v, contentX, contentWidth, curY);
                    curY += 5;
                    curY = addBoolOption("Show Waypoint", cbShowWaypointInput, v -> cbShowWaypointInput = v, contentX,
                            contentWidth, curY);
                    curY = addTextBox("Min Delay (s)", cbMinDelayInput, v -> cbMinDelayInput = v, contentX,
                            contentWidth, curY);
                    curY += 5;
                    curY = addTextBox("Max Delay (s)", cbMaxDelayInput, v -> cbMaxDelayInput = v, contentX,
                            contentWidth, curY);
                    curY += 5;

                    int finalCurY = curY;
                    String addBtnText = editingCoordBindIdx != -1 ? "§e✔ Save Coord Bind" : "§a+ Add Coord Bind";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!cbCommandInput.isEmpty()) {
                            double[] parsed;
                            if (cbCoordsInput.trim().isEmpty()) {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc.player != null) {
                                    parsed = new double[] { mc.player.getX(), mc.player.getY(), mc.player.getZ() };
                                } else {
                                    parsed = null;
                                }
                            } else {
                                parsed = parseCoords(cbCoordsInput);
                            }

                            if (parsed != null) {
                                double radiusVal = 3.0;
                                if (!cbRadiusInput.trim().isEmpty()) {
                                    try {
                                        radiusVal = Double.parseDouble(cbRadiusInput.trim());
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                                double minDelayVal = 0.0;
                                double maxDelayVal = 0.0;
                                try {
                                    if (!cbMinDelayInput.trim().isEmpty()) {
                                        minDelayVal = Double.parseDouble(cbMinDelayInput.trim());
                                    }
                                    if (!cbMaxDelayInput.trim().isEmpty()) {
                                        maxDelayVal = Double.parseDouble(cbMaxDelayInput.trim());
                                    }
                                } catch (NumberFormatException ignored) {
                                }

                                s.coordBinds.putIfAbsent(s.activeProfile, new ArrayList<>());
                                BomboConfig.CoordBind cb = new BomboConfig.CoordBind(cbCommandInput, parsed[0],
                                        parsed[1], parsed[2], cbIslandInput, radiusVal, cbShowWaypointInput,
                                        minDelayVal, maxDelayVal);
                                if (editingCoordBindIdx != -1) {
                                    s.coordBinds.get(s.activeProfile).set(editingCoordBindIdx, cb);
                                    editingCoordBindIdx = -1;
                                } else {
                                    s.coordBinds.get(s.activeProfile).add(cb);
                                }
                                BomboConfig.save();
                                cbCoordsInput = "";
                                cbCommandInput = "";
                                cbIslandInput = "";
                                cbRadiusInput = "3";
                                cbShowWaypointInput = false;
                                cbMinDelayInput = "0";
                                cbMaxDelayInput = "0";
                                init();
                            }
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingCoordBindIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingCoordBindIdx = -1;
                            cbCoordsInput = "";
                            cbCommandInput = "";
                            cbIslandInput = "";
                            cbRadiusInput = "3";
                            cbShowWaypointInput = false;
                            cbMinDelayInput = "0";
                            cbMaxDelayInput = "0";
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }

                    curY += 35;
                    int listStartY = curY;
                    List<BomboConfig.CoordBind> binds = s.coordBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (int i = 0; i < binds.size(); i++) {
                            final int idx = i;
                            BomboConfig.CoordBind cb = binds.get(idx);
                            int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                            if (itemY > listStartY + 15 && itemY < height - 20) {
                                String toggleLabel = cb.enabled ? "§aON" : "§cOFF";
                                addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                    cb.enabled = !cb.enabled;
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 130, itemY + 5, 45, 18).build());

                                addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                    editingCoordBindIdx = idx;
                                    cbCoordsInput = String.format("%.2f %.2f %.2f", cb.x, cb.y, cb.z);
                                    cbCommandInput = cb.command;
                                    cbIslandInput = cb.requiredIsland != null ? cb.requiredIsland : "";
                                    cbRadiusInput = String.valueOf(cb.radius <= 0.0 ? 3.0 : cb.radius);
                                    cbShowWaypointInput = cb.showWaypoint;
                                    cbMinDelayInput = String.valueOf(cb.minDelay);
                                    cbMaxDelayInput = String.valueOf(cb.maxDelay);
                                    init();
                                }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                                addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                    binds.remove(idx);
                                    BomboConfig.save();
                                    init();
                                }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                            }
                        }
                    }
                }
                case 19 -> { // Mining
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;

                    int y1 = contentBaseY + ITEM_HEIGHT;
                    y1 = addBoolOption("Corpse ESP Enabled", s.corpseEsp, v -> s.corpseEsp = v, col1X, col1W, y1);
                    y1 = addBoolOption("Hide Opened Corpses", s.hideOpenedCorpses, v -> s.hideOpenedCorpses = v, col1X,
                            col1W, y1);
                    y1 += 5;

                    String[] styleNames = { "Outline", "Filled", "Both" };
                    String styleLabel = "ESP Style: " + s.corpseEspStyle;
                    addRenderableWidget(Button.builder(Component.literal(styleLabel), btn -> {
                        int currentIdx = 0;
                        if ("Filled".equals(s.corpseEspStyle))
                            currentIdx = 1;
                        else if ("Both".equals(s.corpseEspStyle))
                            currentIdx = 2;

                        int nextIdx = (currentIdx + 1) % 3;
                        s.corpseEspStyle = styleNames[nextIdx];
                        BomboConfig.save();
                        init();
                    }).bounds(col1X, y1, col1W, 20).build());
                    y1 += ITEM_HEIGHT + 5;

                    int y2 = contentBaseY + ITEM_HEIGHT;
                    y2 = addColorCycleButton("Lapis Outline Color", s.lapisOutlineColor, v -> s.lapisOutlineColor = v,
                            col2X, col2W, y2);
                    y2 = addColorCycleButton("Lapis Fill Color", s.lapisFillColor, v -> s.lapisFillColor = v, col2X,
                            col2W, y2);
                    y2 = addColorCycleButton("Tungsten Outline Color", s.tungstenOutlineColor,
                            v -> s.tungstenOutlineColor = v, col2X, col2W, y2);
                    y2 = addColorCycleButton("Tungsten Fill Color", s.tungstenFillColor, v -> s.tungstenFillColor = v,
                            col2X, col2W, y2);
                    y2 = addColorCycleButton("Umber Outline Color", s.umberOutlineColor, v -> s.umberOutlineColor = v,
                            col2X, col2W, y2);
                    y2 = addColorCycleButton("Umber Fill Color", s.umberFillColor, v -> s.umberFillColor = v, col2X,
                            col2W, y2);
                    y2 = addColorCycleButton("Vanguard Outline Color", s.vanguardOutlineColor,
                            v -> s.vanguardOutlineColor = v, col2X, col2W, y2);
                    y2 = addColorCycleButton("Vanguard Fill Color", s.vanguardFillColor, v -> s.vanguardFillColor = v,
                            col2X, col2W, y2);
                }
                case 20 -> { // Party Settings
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + ITEM_HEIGHT;

                    y1 = addBoolOption("!timer command", s.partyCommandTimer, v -> s.partyCommandTimer = v, col1X,
                            col1W, y1);
                    y1 = addBoolOption("!warp command", s.partyCommandWarp, v -> s.partyCommandWarp = v, col1X, col1W,
                            y1);
                    y1 = addBoolOption("!psa command", s.partyCommandPsa, v -> s.partyCommandPsa = v, col1X, col1W, y1);

                    y1 += 10;
                    y1 = addTextBox("Prefixes (e.g. !,.,?)", s.partyCommandPrefixes, v -> {
                        s.partyCommandPrefixes = v;
                        BomboConfig.save();
                    }, col1X, col1W, y1);

                    y1 += 20;
                    addRenderableWidget(Button.builder(Component.literal("§e← Back to General"), btn -> {
                        selectedCategory = 0;
                        init();
                    }).bounds(col1X, y1, 150, 20).build());

                    // Column 2: Custom Party Commands
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;
                    int y2 = contentBaseY + ITEM_HEIGHT;

                    y2 = addTextBox("Trigger", customPartyTriggerInput, v -> customPartyTriggerInput = v, col2X, col2W,
                            y2);
                    y2 = addTextBox("Command to run", customPartyCommandInput, v -> customPartyCommandInput = v, col2X,
                            col2W, y2);

                    int finalY2 = y2;
                    String addBtnText = editingCustomPartyIdx != -1 ? "§e✔ Save Command" : "§a+ Add Command";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!customPartyTriggerInput.isEmpty() && !customPartyCommandInput.isEmpty()) {
                            String trig = customPartyTriggerInput.trim();
                            while (trig.startsWith("!") || trig.startsWith(".") || trig.startsWith("?")) {
                                trig = trig.substring(1);
                            }
                            trig = trig.toLowerCase();

                            BomboConfig.CustomPartyCommand cpc = new BomboConfig.CustomPartyCommand(trig,
                                    customPartyCommandInput.trim(), true);
                            if (editingCustomPartyIdx != -1) {
                                s.customPartyCommands.set(editingCustomPartyIdx, cpc);
                                editingCustomPartyIdx = -1;
                            } else {
                                s.customPartyCommands.add(cpc);
                            }
                            BomboConfig.save();
                            customPartyTriggerInput = "";
                            customPartyCommandInput = "";
                            init();
                        }
                    }).bounds(col2X, finalY2, col2W - 55, 20).build());

                    if (editingCustomPartyIdx != -1) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel"), btn -> {
                            editingCustomPartyIdx = -1;
                            customPartyTriggerInput = "";
                            customPartyCommandInput = "";
                            init();
                        }).bounds(col2X + col2W - 50, finalY2, 50, 20).build());
                    }

                    y2 += 25;
                    int listStartY = y2;
                    for (int i = 0; i < s.customPartyCommands.size(); i++) {
                        final int idx = i;
                        BomboConfig.CustomPartyCommand cmd = s.customPartyCommands.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            String toggleLabel = cmd.enabled ? "§aON" : "§cOFF";
                            addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                cmd.enabled = !cmd.enabled;
                                BomboConfig.save();
                                init();
                            }).bounds(col2X + col2W - 120, itemY + 5, 35, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingCustomPartyIdx = idx;
                                customPartyTriggerInput = cmd.triggerText;
                                customPartyCommandInput = cmd.commandToRun;
                                init();
                            }).bounds(col2X + col2W - 80, itemY + 5, 40, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                s.customPartyCommands.remove(idx);
                                BomboConfig.save();
                                init();
                            }).bounds(col2X + col2W - 35, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 21 -> { // Block Highlights
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Block Highlights Enabled", s.blockHighlightsEnabled, v -> {
                        s.blockHighlightsEnabled = v;
                        if (!v)
                            BlockHighlight.highlightedBlocks.clear();
                    }, contentX, contentWidth, curY);
                    curY += 10;
                    curY = addTextBox("Block Name/ID", blockNameInput, v -> blockNameInput = v, contentX, contentWidth,
                            curY);
                    curY = addColorCycleButton("Color", blockColorInput, v -> blockColorInput = v, contentX,
                            contentWidth, curY);
                    curY = addBoolOption("See Through Walls", blockThroughWallsInput, v -> blockThroughWallsInput = v,
                            contentX, contentWidth, curY);

                    int finalCurY = curY;
                    String addBtnText = editingBlockName != null ? "§e✔ Save Block Highlight"
                            : "§a+ Add Block Highlight";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!blockNameInput.isEmpty()) {
                            if (editingBlockName != null) {
                                s.blockHighlights.remove(editingBlockName);
                            }
                            s.blockHighlights.put(blockNameInput.toLowerCase(), new BomboConfig.BlockHighlightInfo(
                                    blockColorInput.toUpperCase(), blockThroughWallsInput));
                            BomboConfig.save();
                            blockNameInput = "";
                            blockColorInput = "GOLD";
                            blockThroughWallsInput = true;
                            editingBlockName = null;
                            BlockHighlight.highlightedBlocks.clear(); // force rescan
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingBlockName != null) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel Edit"), btn -> {
                            blockNameInput = "";
                            blockColorInput = "GOLD";
                            blockThroughWallsInput = true;
                            editingBlockName = null;
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 80, 20).build());
                    }

                    curY += 35; // Space before list
                    int listStartY = curY;
                    List<String> sortedBlocks = new ArrayList<>(s.blockHighlights.keySet());
                    Collections.sort(sortedBlocks);
                    for (int i = 0; i < sortedBlocks.size(); i++) {
                        final String bName = sortedBlocks.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            BomboConfig.BlockHighlightInfo info = s.blockHighlights.get(bName);
                            String toggleLabel = info.enabled ? "§aON" : "§cOFF";
                            addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                info.enabled = !info.enabled;
                                BomboConfig.save();
                                BlockHighlight.highlightedBlocks.clear(); // force rescan
                                init();
                            }).bounds(contentX + 130, itemY + 5, 45, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingBlockName = bName;
                                blockNameInput = bName;
                                blockColorInput = info.color;
                                blockThroughWallsInput = info.throughWalls;
                                init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                s.blockHighlights.remove(bName);
                                BomboConfig.save();
                                BlockHighlight.highlightedBlocks.clear(); // force rescan
                                init();
                            }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 22 -> { // Particle Highlights
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Particle Highlights Enabled", s.particleHighlightsEnabled,
                            v -> s.particleHighlightsEnabled = v, contentX, contentWidth, curY);
                    curY += 10;
                    curY = addTextBox("Particle Name", partHighInput, v -> partHighInput = v, contentX, contentWidth,
                            curY);
                    curY = addColorCycleButton("Color", partHighColorInput, v -> partHighColorInput = v, contentX,
                            contentWidth, curY);

                    int finalCurY = curY;
                    String addBtnText = editingPartHigh != null ? "§e✔ Save Particle Highlight"
                            : "§a+ Add Particle Highlight";
                    addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                        if (!partHighInput.isEmpty()) {
                            if (editingPartHigh != null) {
                                s.particleHighlights.remove(editingPartHigh);
                            }
                            s.particleHighlights.put(partHighInput.toLowerCase(),
                                    new BomboConfig.HighlightInfo(partHighColorInput.toUpperCase(), true));
                            BomboConfig.save();
                            partHighInput = "";
                            partHighColorInput = "GOLD";
                            editingPartHigh = null;
                            init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());

                    if (editingPartHigh != null) {
                        addRenderableWidget(Button.builder(Component.literal("§cCancel Edit"), btn -> {
                            partHighInput = "";
                            partHighColorInput = "GOLD";
                            editingPartHigh = null;
                            init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 80, 20).build());
                    }

                    curY += 35; // Space before list
                    int listStartY = curY;
                    List<String> sortedParticles = new ArrayList<>(s.particleHighlights.keySet());
                    Collections.sort(sortedParticles);
                    for (int i = 0; i < sortedParticles.size(); i++) {
                        final String pName = sortedParticles.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int) scrollAmount;
                        if (itemY > listStartY + 15 && itemY < height - 20) {
                            BomboConfig.HighlightInfo info = s.particleHighlights.get(pName);
                            String toggleLabel = info.enabled ? "§aON" : "§cOFF";
                            addRenderableWidget(Button.builder(Component.literal(toggleLabel), btn -> {
                                info.enabled = !info.enabled;
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 130, itemY + 5, 45, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§eEDIT"), btn -> {
                                editingPartHigh = pName;
                                partHighInput = pName;
                                partHighColorInput = info.color;
                                init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());

                            addRenderableWidget(Button.builder(Component.literal("§cDEL"), btn -> {
                                s.particleHighlights.remove(pName);
                                BomboConfig.save();
                                init();
                            }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                }
                case 23 -> { // Bedwars
                    curY += ITEM_HEIGHT;
                    curY = addBoolOption("Bedwars ESP Enabled", s.bedwarsEsp, v -> {
                        s.bedwarsEsp = v;
                        BomboConfig.save();
                    }, contentX, contentWidth, curY);
                    curY = addBoolOption("Highlight Own Team", s.bedwarsEspOwnTeam, v -> {
                        s.bedwarsEspOwnTeam = v;
                        BomboConfig.save();
                    }, contentX, contentWidth, curY);
                }
            }

            if (colorPickerTarget != null) {
                renderColorPicker(contentX, HEADER_HEIGHT + 30, contentWidth);
            }

            addRenderableWidget(Button.builder(Component.literal("§lSave & Close"), btn -> {
                BomboConfig.save();
                minecraft.setScreenAndShow(parent);
            }).bounds(width / 2 - 75, height - 32, 150, 24).build());

            System.out.println("DEBUG: BomboConfigGUI init end");
        } catch (Throwable e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during init!", e);
            try {
                java.io.File file = new java.io.File("crash_exception.log");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, true))) {
                    pw.println("=== GUI INIT EXCEPTION ===");
                    e.printStackTrace(pw);
                    pw.println("==========================");
                }
            } catch (Throwable ignore) {
            }
        }
    }

    private int addBoolOption(String label, boolean value, Consumer<Boolean> setter, int x, int w, int y) {
        // Use empty label to avoid overlap with drawString in render
        Checkbox cb = Checkbox.builder(Component.literal(""), font).pos(x, y).selected(value)
                .onValueChange((box, val) -> {
                    setter.accept(val);
                    BomboConfig.save();
                }).build();
        cb.setX(x);
        cb.setY(y);
        addRenderableWidget(cb);
        return y + ITEM_HEIGHT;
    }

    private int addIntLabelSlider(String label, int current, int min, int max, int step,
            java.util.function.IntConsumer setter, int x, int w, int y) {
        addRenderableWidget(Button.builder(Component.literal("§7-"), btn -> {
            setter.accept(Math.max(min, current - step));
            BomboConfig.save();
            init();
        }).bounds(x + w - 45, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7+"), btn -> {
            setter.accept(Math.min(max, current + step));
            BomboConfig.save();
            init();
        }).bounds(x + w - 20, y, 20, 20).build());
        return y + ITEM_HEIGHT;
    }

    private int addFloatLabelSlider(String label, float current, float min, float max, Consumer<Float> setter, int x,
            int w, int y) {
        addRenderableWidget(Button.builder(Component.literal("§7-"), btn -> {
            setter.accept(Math.max(min, current - 0.1f));
            BomboConfig.save();
            init();
        }).bounds(x + w - 45, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§7+"), btn -> {
            setter.accept(Math.min(max, current + 0.1f));
            BomboConfig.save();
            init();
        }).bounds(x + w - 20, y, 20, 20).build());
        return y + ITEM_HEIGHT;
    }

    private int addTextBox(String label, String current, Consumer<String> setter, int x, int w, int y) {
        EditBox box = new EditBox(font, x + w / 2, y, w / 2, 16, Component.literal(label));
        box.setValue(current);
        box.setResponder(val -> {
            setter.accept(val);
            BomboConfig.save();
        });
        box.setBordered(false);
        addRenderableWidget(box);
        activeBoxes.add(box);
        return y + ITEM_HEIGHT;
    }

    private int addKeyBindButton(String label, String current, Consumer<String> setter, String target, int x, int w,
            int y) {
        boolean listening = listeningForKeyTarget.equals(target);
        String displayKey = ClickLogic.getKeyDisplayName(current);
        String txt = listening ? "§e[PRESS KEY]" : "§f" + label + ": §d" + (current.isEmpty() ? "None" : displayKey);
        addRenderableWidget(Button.builder(Component.literal(txt), btn -> {
            listeningForKeyTarget = target;
            init();
        }).bounds(x + w / 2, y, w / 2, 16).build());
        return y + ITEM_HEIGHT;
    }

    private int addComboBindButton(String label, String current, Consumer<String> setter, String target, int x, int w,
            int y) {
        boolean listening = listeningForKeyTarget.equals(target);
        String txt = listening ? "§e[PRESS KEYS...]" : "§f" + label + ": §d" + (current.isEmpty() ? "None" : current);
        addRenderableWidget(Button.builder(Component.literal(txt), btn -> {
            listeningForKeyTarget = target;
            recordedComboKeys.clear();
            init();
        }).bounds(x + w / 2, y, w / 2, 16).build());
        return y + ITEM_HEIGHT;
    }

    private String getColorFormatting(String colorName) {
        if (colorName == null)
            return "§r";
        return switch (colorName.toUpperCase()) {
            case "BLACK" -> "§0";
            case "DARK_BLUE" -> "§1";
            case "DARK_GREEN" -> "§2";
            case "DARK_AQUA" -> "§3";
            case "DARK_RED" -> "§4";
            case "DARK_PURPLE" -> "§5";
            case "GOLD" -> "§6";
            case "GRAY" -> "§7";
            case "DARK_GRAY" -> "§8";
            case "BLUE" -> "§9";
            case "GREEN" -> "§a";
            case "AQUA" -> "§b";
            case "RED" -> "§c";
            case "LIGHT_PURPLE", "PINK" -> "§d";
            case "YELLOW" -> "§e";
            case "WHITE" -> "§f";
            default -> "§r";
        };
    }

    private int addCycleOption(String label, String current, List<String> options, Consumer<String> setter, int x,
            int w, int y) {
        addRenderableWidget(Button.builder(Component.literal(current), btn -> {
            int idx = options.indexOf(current);
            int next = (idx + 1) % options.size();
            setter.accept(options.get(next));
            BomboConfig.save();
            init();
        }).bounds(x + w / 2, y, w / 2, 16).build());
        return y + ITEM_HEIGHT;
    }

    private int addColorCycleButton(String label, String current, Consumer<String> setter, int x, int w, int y) {
        String formatting = getColorFormatting(current);
        String btnText = label + ": " + formatting + current.toUpperCase();
        addRenderableWidget(Button.builder(Component.literal(btnText), btn -> {
            colorPickerTarget = label;
            colorPickerSetter = setter;
            init();
        }).bounds(x + w / 2, y, w / 2, 16).build());
        return y + ITEM_HEIGHT;
    }

    private void renderColorPicker(int x, int y, int w) {
        int pickerW = 120;
        int pickerH = height - 80;
        int pickerX = x + (w - pickerW) / 2;
        int pickerY = 40;

        // Close button
        addRenderableWidget(Button.builder(Component.literal("§c✕"), btn -> {
            colorPickerTarget = null;
            init();
        }).bounds(pickerX + pickerW - 22, pickerY + 2, 20, 20).build());

        int btnW = 100;
        int btnH = 16;
        int spacing = 2;
        int startX = pickerX + 10;
        int startY = pickerY + 25;

        List<String> colorsToUse = new ArrayList<>();
        if ("Fuck Diorite Color".equals(colorPickerTarget)) {
            colorsToUse.add("NONE");
        }
        colorsToUse.addAll(SlotHighlight.COLORS);

        for (int i = 0; i < colorsToUse.size(); i++) {
            String color = colorsToUse.get(i);
            String formatting = getColorFormatting(color);

            addRenderableWidget(Button.builder(Component.literal(formatting + color), btn -> {
                colorPickerSetter.accept(color);
                colorPickerTarget = null;
                BomboConfig.save();
                init();
            }).bounds(startX, startY + i * (btnH + spacing), btnW, btnH).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        try {
            g.fillGradient(0, 0, SIDEBAR_WIDTH, height, 0xEE11111B, 0xEE1E1E2E);
            g.fill(SIDEBAR_WIDTH - 1, 0, SIDEBAR_WIDTH, height, 0x33FFFFFF);
            g.fillGradient(SIDEBAR_WIDTH, 0, width, HEADER_HEIGHT, 0xCC11111B, 0xAA1E1E2E);
            g.fill(SIDEBAR_WIDTH, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, 0x55FFFFFF);

            BomboConfig.Settings s = BomboConfig.get();

            if (colorPickerTarget != null) {
                g.fill(0, 0, width, height, 0xAA000000);
                int pickerW = 120;
                int pickerH = height - 80;
                int pickerX = (SIDEBAR_WIDTH + PADDING * 2) + ((width - SIDEBAR_WIDTH - PADDING * 3) - pickerW) / 2;
                int pickerY = 40;
                g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, 0xFF1E1E2E);
                g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + 24, 0xFF11111B);
                g.text(font, "§6Select Color", pickerX + 10, pickerY + 8, 0xFFFFFFFF);
            }

            // Draw sidebar category buttons with scissor clipping
            g.enableScissor(0, HEADER_HEIGHT + PADDING * 2, SIDEBAR_WIDTH, height - PADDING);
            for (Button btn : sidebarButtons) {
                btn.extractRenderState(g, mouseX, mouseY, partialTick);
            }
            g.disableScissor();

            // Draw sidebar category scrollbar if scrollable
            int totalRendered = 0;
            for (int i = 0; i < categories.size(); i++) {
                if (s.hideCheats && (i == 2 || i == 9)) {
                    continue;
                }
                if (categories.get(i).equals("Party Settings")) {
                    continue;
                }
                totalRendered++;
            }
            int totalHeight = totalRendered * 26;
            int viewportHeight = height - (HEADER_HEIGHT + PADDING * 3) - PADDING;
            if (totalHeight > viewportHeight) {
                int trackX = SIDEBAR_WIDTH - 4;
                int trackY = HEADER_HEIGHT + PADDING * 2;
                int trackH = height - trackY - PADDING;

                int thumbH = Math.max(10, (trackH * viewportHeight) / totalHeight);
                int maxScroll = totalHeight - viewportHeight;
                int thumbY = trackY + (int) ((categoryScrollAmount * (trackH - thumbH)) / maxScroll);

                g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x15FFFFFF);
                g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x55FFFFFF);
            }

            super.extractRenderState(g, mouseX, mouseY, partialTick);

            if (selectedCategory == 0 && partyCommandsX != -1 && mouseX >= partyCommandsX
                    && mouseX <= partyCommandsX + partyCommandsWidth && mouseY >= partyCommandsY
                    && mouseY <= partyCommandsY + partyCommandsHeight) {
                List<String> tooltip = List.of(
                        "§eParty Commands Automation",
                        "§7Allows party members to trigger commands.",
                        "§bRight-click §7to configure options.");
                int tX = mouseX + 12;
                int tY = mouseY;
                int width = 180;
                int height = tooltip.size() * 10 + 4;
                g.fill(tX - 4, tY - 4, tX + width, tY + height, 0xFF181818);
                g.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, 0xFF555555);
                g.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, 0xFF555555);
                g.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, 0xFF555555);
                g.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, 0xFF555555);
                for (int idx = 0; idx < tooltip.size(); idx++) {
                    g.text(font, tooltip.get(idx), tX, tY + idx * 10, 0xFFFFFFFF, true);
                }
            }

            g.text(font, "§6§lBomboaddons §r§7Config", SIDEBAR_WIDTH + PADDING, 14, 0xFFFFFFFF, true);
            g.text(font, "§f§lCATEGORIES", PADDING, HEADER_HEIGHT - 12, 0xFFFFFFFF, true);

            int contentX = SIDEBAR_WIDTH + PADDING * 2;
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 3;
            int categoryTitleY = HEADER_HEIGHT + PADDING * 2;
            int safeIdx = Math.max(0, Math.min(selectedCategory, categories.size() - 1));
            g.text(font, "§f§l" + categories.get(safeIdx).toUpperCase(), contentX, categoryTitleY, 0xFFFFFFFF, true);

            int contentBaseY = categoryTitleY + 30;
            int curY = contentBaseY;

            switch (selectedCategory) {
                case 0 -> {
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;

                    int y1 = contentBaseY;
                    g.text(font, "§6§lMod Settings", col1X, y1, 0xFFFFAA00, true);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Sign Calculator", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7SBE Commands", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Copy Chat", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Left Click Etherwarp", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Sphinx Macro", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Hollow Wand Fix", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Hollow Wand Double Click", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Auto Accept Carnival", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Lowest BIN Tooltip", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7NPC Sell Price Tooltip", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Auto Trevor Quest", col1X + 24, y1 + 4, 0xFFFFFFFF, false);

                    y1 += ITEM_HEIGHT;
                    y1 += 10;
                    g.text(font, "§6§lHoppity Egg Finder", col1X, y1, 0xFFFFAA00, true);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Enable Egg Finder", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Egg Finder Chat Alerts", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Egg Finder Beacon", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Egg Finder Through Walls", col1X + 24, y1 + 4, 0xFFFFFFFF, false);

                    int y2 = contentBaseY;
                    g.text(font, "§6§lClient Settings", col2X, y2, 0xFFFFAA00, true);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Ignore Caps Lock", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Server List Button", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Reconnect Button", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Quick Join Commands (/f1, /m1, etc)", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Auto Reconnect", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Party Commands", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Bypass Resource Pack", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Restore Item Models", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Hypixel Shortcut Button", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Smart Disconnect", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;

                    y2 += 10;
                    g.text(font, "§6§lFuck Diorite Settings", col2X, y2, 0xFFFFAA00, true);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Fuck Diorite", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Fuck Diorite Pillar Color", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fFuck Diorite Color:", col2X, y2, 0xFFFFFFFF);
                }
                case 1 -> { // HUDs
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;

                    int y1 = contentBaseY;
                    g.text(font, "§6§lHUD Settings", col1X, y1, 0xFFFFAA00, true);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Dice Tracker HUD", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Feast Bakery HUD", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7RNG Profit HUD", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§fRNG HUD Opacity: §e" + BomboConfig.get().rngProfitHudOpacity + "%", col1X, y1 + 4,
                            0xFFFFFFFF);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Custom Timers HUD", col1X + 24, y1 + 4, 0xFFFFFFFF, false);

                    int y2 = contentBaseY;
                    g.text(font, "§6§lTooltip Customization", col2X, y2, 0xFFFFAA00, true);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Custom Tooltip Bg", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;

                    y2 += 20;

                    int pickerX = col2X;
                    int pickerY = y2;
                    int svSize = 80;

                    // Real-time drag updates
                    if (isDraggingSv || isDraggingHue || isDraggingAlpha) {
                        if (isDraggingSv) {
                            float sat = (float) (mouseX - pickerX) / svSize;
                            float val = 1.0f - ((float) (mouseY - pickerY) / svSize);
                            sat = Math.max(0f, Math.min(1f, sat));
                            val = Math.max(0f, Math.min(1f, val));
                            int rgb = hsvToRgb(currentHue, sat, val);
                            if (colorPickerMode == 0) {
                                s.tooltipBgColor = (s.tooltipBgColor & 0xFF000000) | rgb;
                            } else {
                                s.tooltipBorderColor = (s.tooltipBorderColor & 0xFF000000) | rgb;
                            }
                        } else if (isDraggingHue) {
                            float hue = (float) (mouseY - pickerY) / svSize;
                            hue = Math.max(0f, Math.min(1f, hue));
                            currentHue = hue;
                            int currentRgb = colorPickerMode == 0 ? (s.tooltipBgColor & 0xFFFFFF)
                                    : (s.tooltipBorderColor & 0xFFFFFF);
                            float[] hsvTemp = rgbToHsv((currentRgb >> 16) & 0xFF, (currentRgb >> 8) & 0xFF,
                                    currentRgb & 0xFF);
                            int rgb = hsvToRgb(hue, hsvTemp[1], hsvTemp[2]);
                            if (colorPickerMode == 0) {
                                s.tooltipBgColor = (s.tooltipBgColor & 0xFF000000) | rgb;
                            } else {
                                s.tooltipBorderColor = (s.tooltipBorderColor & 0xFF000000) | rgb;
                            }
                        } else if (isDraggingAlpha) {
                            float pct = 1.0f - ((float) (mouseY - pickerY) / svSize);
                            pct = Math.max(0f, Math.min(1f, pct));
                            int alpha = (int) (pct * 255);
                            if (colorPickerMode == 0) {
                                s.tooltipBgColor = (alpha << 24) | (s.tooltipBgColor & 0xFFFFFF);
                            } else {
                                s.tooltipBorderColor = (alpha << 24) | (s.tooltipBorderColor & 0xFFFFFF);
                            }
                        }
                    }

                    // Render Saturation-Value 2D Square (optimized step)
                    int step = 4;
                    for (int r = 0; r < svSize; r += step) {
                        for (int c = 0; c < svSize; c += step) {
                            float sat = (float) c / svSize;
                            float val = 1.0f - ((float) r / svSize);
                            int rgb = hsvToRgb(currentHue, sat, val);
                            g.fill(pickerX + c, pickerY + r, pickerX + c + step, pickerY + r + step, 0xFF000000 | rgb);
                        }
                    }
                    g.outline(pickerX, pickerY, svSize, svSize, 0xFF555555);

                    // Render Hue Slider rainbow bar (vertical)
                    for (int r = 0; r < svSize; r += 2) {
                        float hue = (float) r / svSize;
                        int rgb = hsvToRgb(hue, 1.0f, 1.0f);
                        g.fill(pickerX + 90, pickerY + r, pickerX + 102, pickerY + r + 2, 0xFF000000 | rgb);
                    }
                    g.outline(pickerX + 90, pickerY, 12, svSize, 0xFF555555);

                    // Render Alpha Slider gradient bar (vertical)
                    int activeColorVal = colorPickerMode == 0 ? s.tooltipBgColor : s.tooltipBorderColor;
                    int activeRgb = activeColorVal & 0xFFFFFF;
                    int activeAlpha = (activeColorVal >> 24) & 0xFF;
                    for (int r = 0; r < svSize; r += 2) {
                        int alpha = (int) ((1.0f - ((float) r / svSize)) * 255);
                        g.fill(pickerX + 110, pickerY + r, pickerX + 122, pickerY + r + 2, (alpha << 24) | activeRgb);
                    }
                    g.outline(pickerX + 110, pickerY, 12, svSize, 0xFF555555);

                    // Render indicators
                    float[] activeHsv = rgbToHsv((activeRgb >> 16) & 0xFF, (activeRgb >> 8) & 0xFF, activeRgb & 0xFF);
                    int circleX = pickerX + (int) (activeHsv[1] * svSize);
                    int circleY = pickerY + (int) ((1.0f - activeHsv[2]) * svSize);
                    g.fill(circleX - 1, circleY - 1, circleX + 1, circleY + 1, 0xFFFFFFFF);

                    int hueY = pickerY + (int) (currentHue * svSize);
                    g.fill(pickerX + 89, hueY, pickerX + 103, hueY + 1, 0xFFFFFFFF);

                    int alphaY = pickerY + (int) ((1.0f - (activeAlpha / 255.0f)) * svSize);
                    g.fill(pickerX + 109, alphaY, pickerX + 123, alphaY + 1, 0xFFFFFFFF);

                    // Render Preview
                    g.fill(pickerX + 130, pickerY + 5, pickerX + 160, pickerY + 35, (activeAlpha << 24) | activeRgb);
                    g.outline(pickerX + 130, pickerY + 5, 30, 30, 0xFFFFFFFF);

                    String colorString = String.format("#%02X%06X", activeAlpha, activeRgb);
                    g.text(font, colorString, pickerX + 128, pickerY + 45, 0xFFFFFFFF);
                    g.text(font, "A: " + activeAlpha, pickerX + 128, pickerY + 57, 0xFFFFFFFF);
                }
                case 2 -> {
                    g.text(font, "§6§lExperiment Solver", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Auto Experiments", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fClick Delay: §e" + BomboConfig.get().experimentClickDelay + "ms", contentX, curY,
                            0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fSerum Count: §e" + BomboConfig.get().experimentSerumCount, contentX, curY,
                            0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Auto Close", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Get Max XP", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                }
                case 3 -> {
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;

                    int y1 = contentBaseY;
                    g.text(font, "§6§lGarden Settings", col1X, y1, 0xFFFFAA00, true);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Garden Movement", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Lock Mouse on Movement", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Sugar Cane Mode", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Direction Helper Warning", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Macro Check Detector", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    if (BomboConfig.get().gardenMacroCheckDetector) {
                        g.text(font, "§7Stop Movement on Check", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                        y1 += ITEM_HEIGHT;
                        g.text(font, "§fAlarm Sound:", col1X, y1 + 4, 0xFFFFFFFF, false);
                        y1 += ITEM_HEIGHT;
                        g.text(font, "§fSound Repeats: §e" + BomboConfig.get().gardenMacroCheckSoundCount, col1X,
                                y1 + 4, 0xFFFFFFFF, false);
                        y1 += ITEM_HEIGHT;
                        g.text(font, "§fSound Delay: §e" + BomboConfig.get().gardenMacroCheckSoundDelay + "ms", col1X,
                                y1 + 4, 0xFFFFFFFF, false);
                        y1 += ITEM_HEIGHT;
                    }
                    y1 += 10;
                    g.text(font, "§fForward:", col1X, y1, 0xFFFFFFFF);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§fBackward:", col1X, y1, 0xFFFFFFFF);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§fLeft:", col1X, y1, 0xFFFFFFFF);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§fRight:", col1X, y1, 0xFFFFFFFF);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§fBreak:", col1X, y1, 0xFFFFFFFF);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§fUse:", col1X, y1, 0xFFFFFFFF);

                    int y2 = contentBaseY;
                    g.text(font, "§6§lPest Settings", col2X, y2, 0xFFFFAA00, true);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Pest ESP Enabled", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Pest Waypoints", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Remove Waypoint On Return", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Pest Waypoint Beacon", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font,
                            "§fPest Waypoint Duration: §e" + (BomboConfig.get().pestWaypointDuration == 0 ? "Infinite"
                                    : BomboConfig.get().pestWaypointDuration + "s"),
                            col2X, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§7Pest Tracers", col2X + 24, y2 + 4, 0xFFFFFFFF, false);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fPest Color:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fPest Thickness: §e" + BomboConfig.get().pestEspThickness, col2X, y2, 0xFFFFFFFF);
                }
                case 4 -> {
                    g.text(font, "§6§lHotkey Shortcuts", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fTrade:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fRecipe:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fUsage:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fShow Info:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fCount Items:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fCopy NBT:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fGFS Max:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fGFS Stack:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fChat Peek:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fNext Page:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fPrev Page:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fGo Back:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fSmart Back:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fSave Pet:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fRun Clipboard Cmd:", contentX, curY, 0xFFFFFFFF);
                }
                case 6 -> {
                    g.text(font, "§6§lClicker Targets", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Auto GUI: " + (s.autoClicker ? "§aON" : "§cOFF"), contentX + 24, curY + 4,
                            0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Keypress: " + (s.chestClicker ? "§aON" : "§cOFF"), contentX + 24, curY + 4,
                            0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.text(font, "§fGUI Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fItem Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fKey to Press:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35; // Space before list
                    int listTitleY = curY;
                    g.text(font, "§9§lActive Targets", contentX, listTitleY, 0xFF5555FF, true);

                    int listY = listTitleY + 20 - (int) scrollAmount;
                    for (ClickLogic.ClickTarget target : ClickLogic.getTargets()) {
                        if (listY > listTitleY + 15 && listY < height - 15) {
                            String displayKey = ClickLogic.getKeyDisplayName(target.keyName);
                            String txt = "§e" + target.gui + " §7- §b" + displayKey;
                            if (!target.item.isEmpty())
                                txt += " §8(" + target.item + ")";
                            g.text(font, txt, contentX, listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 5 -> { // Profiles
                    g.text(font, "§6§lProfile Management", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fActive Profile: §e" + s.activeProfile, contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCreate New Profile:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 15;

                    int listTitleY = curY;
                    g.text(font, "§6§lProfile Binds: §e" + s.activeProfile, contentX, listTitleY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fCommand:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCombo:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35; // Space before list
                    int activeBindsTitleY = curY;
                    g.text(font, "§9§lActive Binds", contentX, activeBindsTitleY, 0xFF5555FF, true);

                    int listY = activeBindsTitleY + 20 - (int) scrollAmount;
                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CommandBind bind : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < height - 15) {
                                g.text(font, "§e" + bind.keyName + " §7-> §b/" + bind.command, contentX, listY + 5,
                                        0xFFFFFFFF, false);
                            }
                            listY += 22;
                        }
                    }
                }
                case 7 -> {
                    g.text(font, "§6§lAdd Highlight", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Highlights Enabled", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.text(font, "§fMob Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fColor:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35;
                    int listTitleY = curY;
                    g.text(font, "§9§lActive Highlights", contentX, listTitleY, 0xFF5555FF, true);
                    int listY = listTitleY + 20 - (int) scrollAmount;
                    List<String> sortedMobs = new ArrayList<>(s.highlights.keySet());
                    Collections.sort(sortedMobs);
                    for (String mobName : sortedMobs) {
                        if (listY > listTitleY + 15 && listY < height - 15) {
                            BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                            String prefix = info.enabled ? "§e" : "§8§m";
                            String color = info.color;
                            g.text(font, prefix + mobName + " §7- " + getColorFormatting(color) + color, contentX,
                                    listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 8 -> {
                    g.text(font, "§6§lWardrobe Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    if (!s.hideCheats) {
                        g.text(font, "§7Auto Close", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                        curY += ITEM_HEIGHT;
                    }
                    g.text(font, "§7Disable Unequip", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    for (int i = 0; i < 9; i++) {
                        g.text(font, "§fSlot " + (i + 1) + ":", contentX, curY, 0xFFFFFFFF);
                        curY += ITEM_HEIGHT;
                    }
                }
                case 9 -> {
                    g.text(font, "§6§lAnvil Auto-Combine", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Auto Combine", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fCombine Delay: §e" + s.anvilAutoCombineDelay + "ms", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Require Keybind", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fTrigger Key:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 20;
                    g.text(font, "§9§lTarget Enchantments", contentX, curY, 0xFF5555FF, true);
                    int listY = curY + 20 - (int) scrollAmount;
                    List<String> sortedEnchants = new ArrayList<>(s.anvilAutoCombine.keySet());
                    Collections.sort(sortedEnchants);
                    for (String enc : sortedEnchants) {
                        if (listY > curY + 15 && listY < height - 15) {
                            int level = s.anvilAutoCombine.get(enc);
                            g.text(font, "§e" + enc + " §7(Target: " + level + ")", contentX, listY + 5, 0xFFFFFFFF,
                                    false);
                        }
                        listY += 22;
                    }
                }
                case 10 -> {
                    g.text(font, "§c§lDebug Settings", contentX, curY, 0xFFFF5555, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7MASTER DEBUG", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 24;
                    g.text(font, "§7Chat Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7GUIs Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Entities Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Command Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Debug Mode (Legacy)", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7API Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7API Chat Messages", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7LB Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Particle Debug", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                }
                case 11 -> { // Kuudra
                    g.text(font, "§6§lKuudra Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Blindness Timer", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Disable Blindness", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Perk Menu Clicker", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Auto GFS Toxic", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fToxic Count: §e" + BomboConfig.get().autoGfsToxicCount, contentX, curY + 4,
                            0xFFFFFFFF);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Auto GFS Twilight", contentX + 24, curY + 4, 0xFFFFFFFF, false);

                    curY += ITEM_HEIGHT;
                    curY += 10;
                    g.text(font, "§7Pearl Waypoints & Timers", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Show Pearl Throw Timer", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Show All Pearl Spots", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Show Sky Pearl Spots", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Show Flat Pearl Spots", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Show Double Pearl Spots", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Kuudra Debug Mode", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fTalisman Tier: §e" + BomboConfig.get().kuudraTalisman, contentX, curY + 4,
                            0xFFFFFFFF);
                }
                case 12 -> { // Pets
                    g.text(font, "§6§lPets Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Disable Unequip", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    for (int i = 0; i < 9; i++) {
                        String uuid = s.petKeybinds.get(String.valueOf(i + 1));
                        String boundInfo = "";
                        if (uuid != null && !uuid.isEmpty()) {
                            String petName = s.petNames.get(String.valueOf(i + 1));
                            if (petName != null && !petName.isEmpty()) {
                                String cleanName = petName.replaceAll("§.", "");
                                cleanName = cleanName.replaceAll("\\[Lvl \\d+\\]", "");
                                cleanName = cleanName.replaceAll("[^a-zA-Z0-9\\s\\-']", "");
                                cleanName = cleanName.trim();
                                if (cleanName.length() > 14) {
                                    cleanName = cleanName.substring(0, 14) + "...";
                                }
                                boundInfo = " §7(" + cleanName + ")";
                            } else {
                                boundInfo = " §7(" + (uuid.length() > 6 ? uuid.substring(0, 6) : uuid) + ")";
                            }
                        }
                        g.text(font, "§fSlot " + (i + 1) + boundInfo + ":", contentX, curY + 4, 0xFFFFFFFF);
                        curY += ITEM_HEIGHT;
                    }
                }
                case 13 -> { // Keybinds
                    g.text(font, "§6§lKeybinds Management", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fActive Profile: §e" + s.activeProfile, contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCommand:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCombo:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fOnly on Island:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fOnly with Armor:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35; // Space before list
                    int activeBindsTitleY = curY;
                    g.text(font, "§9§lActive Binds", contentX, activeBindsTitleY, 0xFF5555FF, true);

                    int listY = activeBindsTitleY + 20 - (int) scrollAmount;
                    List<BomboConfig.CommandBind> binds = s.keybindBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CommandBind bind : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < height - 15) {
                                String extra = "";
                                if (bind.requiredIsland != null && !bind.requiredIsland.isEmpty()) {
                                    extra += " §8[" + bind.requiredIsland + "]";
                                }
                                if (bind.requiredArmor != null && !bind.requiredArmor.isEmpty()) {
                                    extra += " §8(" + bind.requiredArmor + ")";
                                }
                                String statusPrefix = bind.enabled ? "§a[✔] " : "§c[✘] ";
                                g.text(font, statusPrefix + "§e" + bind.keyName + " §7-> §b/" + bind.command + extra,
                                        contentX, listY + 5, 0xFFFFFFFF, false);
                            }
                            listY += 22;
                        }
                    }
                }
                case 14 -> { // Waypoints
                    g.text(font, "§6§lWaypoints Management", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fActive Profile: §e" + s.activeProfile, contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fName:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCoords (X Y Z):", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fOnly on Island:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§7Show Through Walls", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Show Beacon", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fColor:", contentX, curY, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT;

                    curY += 35; // Space before list
                    int activeWaypointsTitleY = curY;
                    g.text(font, "§9§lActive Waypoints", contentX, activeWaypointsTitleY, 0xFF5555FF, true);

                    int listY = activeWaypointsTitleY + 20 - (int) scrollAmount;
                    List<BomboConfig.CustomWaypoint> wps = s.customWaypoints.get(s.activeProfile);
                    if (wps != null) {
                        for (BomboConfig.CustomWaypoint wp : wps) {
                            if (listY > activeWaypointsTitleY + 15 && listY < height - 15) {
                                String extra = "";
                                if (wp.requiredIsland != null && !wp.requiredIsland.isEmpty()) {
                                    extra += " §8[" + wp.requiredIsland + "]";
                                }
                                String statusPrefix = wp.enabled ? "§a[✔] " : "§c[✘] ";
                                String formattedColor = getColorFormatting(wp.color);
                                g.text(font,
                                        statusPrefix + formattedColor + wp.name + " §7-> ("
                                                + String.format("%.1f, %.1f, %.1f", wp.x, wp.y, wp.z) + ")" + extra,
                                        contentX, listY + 5, 0xFFFFFFFF, false);
                            }
                            listY += 22;
                        }
                    }
                }
                case 15 -> { // Aliases
                    g.text(font, "§6§lCommand Aliases", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fAlias:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCommand:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35; // Space before list
                    int activeAliasesTitleY = curY;
                    g.text(font, "§9§lActive Aliases", contentX, activeAliasesTitleY, 0xFF5555FF, true);

                    int listY = activeAliasesTitleY + 20 - (int) scrollAmount;
                    List<String> sortedAliases = new ArrayList<>(s.commandAliases.keySet());
                    Collections.sort(sortedAliases);
                    for (String aliasKey : sortedAliases) {
                        if (listY > activeAliasesTitleY + 15 && listY < height - 15) {
                            g.text(font, "§e/" + aliasKey + " §7-> §b" + s.commandAliases.get(aliasKey), contentX,
                                    listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 16 -> { // Chat Triggers
                    g.text(font, "§6§lChat Triggers & Actions", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fActive Profile: §e" + s.activeProfile, contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fIf Chat Contains:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fRun Command:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fShow Title:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35; // Space before list
                    int activeTriggersTitleY = curY;
                    g.text(font, "§9§lActive Triggers", contentX, activeTriggersTitleY, 0xFF5555FF, true);

                    int listY = activeTriggersTitleY + 20 - (int) scrollAmount;
                    List<BomboConfig.ChatTrigger> activeTriggers = s.profileChatTriggers.get(s.activeProfile);
                    if (activeTriggers != null) {
                        for (BomboConfig.ChatTrigger trigger : activeTriggers) {
                            if (listY > activeTriggersTitleY + 15 && listY < height - 15) {
                                String actionText = "";
                                if (trigger.commandToRun != null && !trigger.commandToRun.isEmpty()) {
                                    actionText += " §7[Cmd: §b" + trigger.commandToRun + "§7]";
                                }
                                if (trigger.titleToShow != null && !trigger.titleToShow.isEmpty()) {
                                    actionText += " §7[Title: §e" + trigger.titleToShow + "§7]";
                                }
                                String statusPrefix = trigger.enabled ? "§a[✔] " : "§c[✘] ";
                                g.text(font, statusPrefix + "§f\"" + trigger.triggerText + "\"" + actionText, contentX,
                                        listY + 5, 0xFFFFFFFF, false);
                            }
                            listY += 22;
                        }
                    }
                }
                case 17 -> { // Dungeons
                    g.text(font, "§6§lDungeons Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Pad Timers Purple", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Pad Timers Green", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Dungeon Big Hitbox", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fPurple Timer: §e" + String.format("%.1fs", s.padTimerPurpleTime), contentX, curY,
                            0xFFFFFFFF);
                }
                case 18 -> { // Coord Binds
                    g.text(font, "§6§lCoord Commands Management", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fActive Profile: §e" + s.activeProfile, contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCoords (X Y Z):", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fCommand:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fOnly on Island:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fRadius:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§7Show Waypoint", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§fMin Delay (s):", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fMax Delay (s):", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35; // Space before list
                    int activeBindsTitleY = curY;
                    g.text(font, "§9§lActive Coord Binds", contentX, activeBindsTitleY, 0xFF5555FF, true);

                    int listY = activeBindsTitleY + 20 - (int) scrollAmount;
                    List<BomboConfig.CoordBind> binds = s.coordBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CoordBind cb : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < height - 15) {
                                String extra = "";
                                if (cb.requiredIsland != null && !cb.requiredIsland.isEmpty()) {
                                    extra += " §8[" + cb.requiredIsland + "]";
                                }
                                double r = cb.radius <= 0.0 ? 3.0 : cb.radius;
                                extra += " §7(r=" + String.format("%.1f", r) + ")";
                                if (cb.showWaypoint) {
                                    extra += " §e[WP]";
                                }
                                if (cb.maxDelay > 0.0) {
                                    extra += " §d[" + String.format("%.1f-%.1fs", cb.minDelay, cb.maxDelay) + "]";
                                }
                                String statusPrefix = cb.enabled ? "§a[✔] " : "§c[✘] ";
                                g.text(font,
                                        statusPrefix + "(" + String.format("%.1f, %.1f, %.1f", cb.x, cb.y, cb.z)
                                                + ") §7-> §b" + cb.command + extra,
                                        contentX, listY + 5, 0xFFFFFFFF, false);
                            }
                            listY += 22;
                        }
                    }
                }
                case 19 -> { // Mining
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;

                    int y1 = contentBaseY;
                    g.text(font, "§6§lCorpse ESP Settings", col1X, y1, 0xFFFFAA00, true);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Corpse ESP Enabled", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Hide Opened Corpses", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT + 5;
                    g.text(font, "§fESP Style:", col1X, y1, 0xFFFFFFFF);

                    int y2 = contentBaseY;
                    g.text(font, "§6§lCorpse Colors", col2X, y2, 0xFFFFAA00, true);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fLapis Outline:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fLapis Fill:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fTungsten Outline:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fTungsten Fill:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fUmber Outline:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fUmber Fill:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fVanguard Outline:", col2X, y2, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fVanguard Fill:", col2X, y2, 0xFFFFFFFF);
                }
                case 20 -> { // Party Settings
                    int col1X = contentX;
                    int y1 = contentBaseY;
                    g.text(font, "§6§lParty Commands Config", col1X, y1, 0xFFFFAA00, true);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Toggle !timer command", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Toggle !warp command (runs /party warp)", col1X + 24, y1 + 4, 0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT;
                    g.text(font, "§7Toggle !psa command (runs /party settings allinvite)", col1X + 24, y1 + 4,
                            0xFFFFFFFF, false);
                    y1 += ITEM_HEIGHT + 10;
                    g.text(font, "§fPrefixes (e.g. !,.,?):", col1X, y1 + 4, 0xFFFFFFFF);

                    // Column 2: Custom Party Commands
                    int col2X = contentX + contentWidth / 2 + 10;
                    int y2 = contentBaseY;
                    g.text(font, "§6§lCustom Party Commands", col2X, y2, 0xFFFFAA00, true);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fTrigger:", col2X, y2 + 4, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;
                    g.text(font, "§fRun Command:", col2X, y2 + 4, 0xFFFFFFFF);
                    y2 += ITEM_HEIGHT;

                    y2 += 25; // Space before list
                    int listStartY = y2;
                    g.text(font, "§9§lActive Custom Commands", col2X, listStartY, 0xFF5555FF, true);

                    int listY = listStartY + 20 - (int) scrollAmount;
                    for (BomboConfig.CustomPartyCommand cmd : s.customPartyCommands) {
                        if (listY > listStartY + 15 && listY < height - 15) {
                            String statusPrefix = cmd.enabled ? "§a[✔] " : "§c[✘] ";
                            String firstPrefix = "!";
                            if (s.partyCommandPrefixes != null && !s.partyCommandPrefixes.trim().isEmpty()) {
                                String[] splits = s.partyCommandPrefixes.split(",");
                                if (splits.length > 0 && !splits[0].trim().isEmpty()) {
                                    firstPrefix = splits[0].trim();
                                }
                            }
                            String label = statusPrefix + "§f" + firstPrefix + cmd.triggerText + " §7-> §b"
                                    + cmd.commandToRun;

                            int maxLabelWidth = contentWidth / 2 - 130;
                            String displayLabel = label;
                            if (font.width(displayLabel) > maxLabelWidth) {
                                while (font.width(displayLabel + "...") > maxLabelWidth && displayLabel.length() > 0) {
                                    displayLabel = displayLabel.substring(0, displayLabel.length() - 1);
                                }
                                displayLabel += "...";
                            }
                            g.text(font, displayLabel, col2X, listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 21 -> { // Block Highlights
                    g.text(font, "§6§lAdd Block Highlight", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Block Highlights Enabled", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.text(font, "§fBlock Name/ID:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fColor:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§7See Through Walls", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;

                    curY += 35;
                    int listTitleY = curY;
                    g.text(font, "§9§lActive Block Highlights", contentX, listTitleY, 0xFF5555FF, true);
                    int listY = listTitleY + 20 - (int) scrollAmount;
                    List<String> sortedBlocks = new ArrayList<>(s.blockHighlights.keySet());
                    Collections.sort(sortedBlocks);
                    for (String bName : sortedBlocks) {
                        if (listY > listTitleY + 15 && listY < height - 15) {
                            BomboConfig.BlockHighlightInfo info = s.blockHighlights.get(bName);
                            String prefix = info.enabled ? "§e" : "§8§m";
                            String color = info.color;
                            String twText = info.throughWalls ? " §7(X-Ray)" : " §8(Depth)";
                            g.text(font, prefix + bName + " §7- " + getColorFormatting(color) + color + twText,
                                    contentX, listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 22 -> { // Particle Highlights
                    g.text(font, "§6§lAdd Particle Highlight", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Particle Highlights Enabled", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT + 10;
                    g.text(font, "§fParticle Name:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;
                    g.text(font, "§fColor:", contentX, curY + 4, 0xFFFFFFFF);
                    curY += ITEM_HEIGHT + 5;

                    curY += 35;
                    int listTitleY = curY;
                    g.text(font, "§9§lActive Particle Highlights", contentX, listTitleY, 0xFF5555FF, true);
                    int listY = listTitleY + 20 - (int) scrollAmount;
                    List<String> sortedParticles = new ArrayList<>(s.particleHighlights.keySet());
                    Collections.sort(sortedParticles);
                    for (String pName : sortedParticles) {
                        if (listY > listTitleY + 15 && listY < height - 15) {
                            BomboConfig.HighlightInfo info = s.particleHighlights.get(pName);
                            String prefix = info.enabled ? "§e" : "§8§m";
                            String color = info.color;
                            g.text(font, prefix + pName + " §7- " + getColorFormatting(color) + color, contentX,
                                    listY + 5, 0xFFFFFFFF, false);
                        }
                        listY += 22;
                    }
                }
                case 23 -> { // Bedwars
                    g.text(font, "§6§lBedwars ESP Settings", contentX, curY, 0xFFFFAA00, true);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Bedwars ESP Enabled", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                    curY += ITEM_HEIGHT;
                    g.text(font, "§7Highlight Own Team", contentX + 24, curY + 4, 0xFFFFFFFF, false);
                }
            }

        } catch (Throwable e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during render!", e);
            try {
                java.io.File file = new java.io.File("crash_exception.log");
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, true))) {
                    pw.println("=== GUI RENDER EXCEPTION ===");
                    e.printStackTrace(pw);
                    pw.println("============================");
                }
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<Integer> parseCombo(String combo) {
        List<Integer> codes = new ArrayList<>();
        String[] parts = combo.toLowerCase().split("\\+");
        for (String p : parts) {
            int code = ClickLogic.getKeyCode(p.trim());
            if (code != -1)
                codes.add(code);
        }
        return codes;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (colorPickerTarget != null && keyCode == 256) {
            colorPickerTarget = null;
            init();
            return true;
        }
        if (!listeningForKeyTarget.isEmpty()) {
            if (listeningForKeyTarget.equals("profileCombo")) {
                if (keyCode == 256) {
                    bindComboInput = "";
                    listeningForKeyTarget = "";
                    recordedComboKeys.clear();
                    init();
                    return true;
                }
                String keyName = ClickLogic.getKeyName(keyCode);
                if (keyName.equals("unknown")) {
                    if (keyCode >= 320 && keyCode <= 329) {
                        keyName = "kp_" + (keyCode - 320);
                    } else {
                        keyName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
                        if (keyName == null)
                            keyName = "key_" + keyCode;
                    }
                }
                if (!recordedComboKeys.contains(keyName)) {
                    recordedComboKeys.add(keyName);
                }
                bindComboInput = String.join("+", recordedComboKeys);
                init();
                return true;
            }
            if (keyCode == 256) {
                updateKeyTarget("");
            } else {
                String keyName = ClickLogic.getKeyName(keyCode);
                if (keyName.equals("unknown")) {
                    if (keyCode >= 320 && keyCode <= 329) {
                        keyName = "kp_" + (keyCode - 320);
                    } else {
                        keyName = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
                        if (keyName == null)
                            keyName = "key_" + keyCode;
                    }
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

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 1) { // Right click
            double mx = event.x();
            double my = event.y();
            if (selectedCategory == 0 && partyCommandsX != -1 && mx >= partyCommandsX
                    && mx <= partyCommandsX + partyCommandsWidth && my >= partyCommandsY
                    && my <= partyCommandsY + partyCommandsHeight) {
                selectedCategory = categories.indexOf("Party Settings");
                scrollAmount = 0;
                init();
                return true;
            }
        }
        if (event.button() == 0 && selectedCategory == 1) {
            double mx = event.x();
            double my = event.y();
            int contentWidth = width - SIDEBAR_WIDTH - PADDING * 3;
            int col2X = SIDEBAR_WIDTH + PADDING * 2 + contentWidth / 2 + 10;
            int contentBaseY = HEADER_HEIGHT + PADDING * 2 + 30;
            int pickerY = contentBaseY + ITEM_HEIGHT * 2 + 20;
            int svSize = 80;

            if (mx >= col2X && mx <= col2X + svSize && my >= pickerY && my <= pickerY + svSize) {
                isDraggingSv = true;
                return true;
            }
            if (mx >= col2X + 90 && mx <= col2X + 102 && my >= pickerY && my <= pickerY + svSize) {
                isDraggingHue = true;
                return true;
            }
            if (mx >= col2X + 110 && mx <= col2X + 122 && my >= pickerY && my <= pickerY + svSize) {
                isDraggingAlpha = true;
                return true;
            }
        }
        if (!listeningForKeyTarget.isEmpty()) {
            int button = event.button();
            if (button != 0) {
                String keyName = "mouse" + (button + 1);
                if (listeningForKeyTarget.equals("profileCombo")) {
                    if (!recordedComboKeys.contains(keyName)) {
                        recordedComboKeys.add(keyName);
                    }
                    bindComboInput = String.join("+", recordedComboKeys);
                    init();
                    return true;
                } else {
                    updateKeyTarget(keyName);
                    listeningForKeyTarget = "";
                    BomboConfig.save();
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        isDraggingSv = false;
        isDraggingHue = false;
        isDraggingAlpha = false;
        BomboConfig.save();
        return super.mouseReleased(event);
    }

    @Override
    public void tick() {
        super.tick();
        if (listeningForKeyTarget.equals("profileCombo") && !recordedComboKeys.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            long window = mc.getWindow().handle();
            boolean anyDown = false;
            for (String keyName : recordedComboKeys) {
                int code = ClickLogic.getKeyCode(keyName);
                if (code != -1 && ClickLogic.isCodeDown(window, mc.getWindow(), code)) {
                    anyDown = true;
                    break;
                }
            }
            if (!anyDown) {
                listeningForKeyTarget = "";
                recordedComboKeys.clear();
                init();
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < SIDEBAR_WIDTH) {
            categoryScrollAmount = Math.max(0, categoryScrollAmount - vertical * 15);
        } else {
            scrollAmount = Math.max(0, scrollAmount - vertical * 15);
        }
        init();
        return true;
    }

    private void updateKeyTarget(String keyName) {
        BomboConfig.Settings s = BomboConfig.get();
        if (listeningForKeyTarget.startsWith("wardrobe")) {
            try {
                int index = Integer.parseInt(listeningForKeyTarget.substring(8));
                s.wardrobeKeys.set(index, keyName);
            } catch (Exception ignored) {
            }
            return;
        }
        if (listeningForKeyTarget.startsWith("pets")) {
            try {
                int index = Integer.parseInt(listeningForKeyTarget.substring(4));
                s.petKeys.set(index, keyName);
            } catch (Exception ignored) {
            }
            return;
        }
        switch (listeningForKeyTarget) {
            case "trade" -> s.tradeKey = keyName;
            case "countItem" -> s.countItemKey = keyName;
            case "recipe" -> s.recipeKey = keyName;
            case "usage" -> s.usageKey = keyName;
            case "showItem" -> s.showItemKey = keyName;
            case "copyNbt" -> s.copyNbtKey = keyName;
            case "gfsMax" -> s.gfsMaxKey = keyName;
            case "gfsStack" -> s.gfsStackKey = keyName;
            case "chatPeek" -> s.chatPeekKey = keyName;
            case "nextPage" -> s.nextPageKey = keyName;
            case "prevPage" -> s.prevPageKey = keyName;
            case "goBack" -> s.goBackKey = keyName;
            case "smartBack" -> s.smartGoBackKey = keyName;
            case "clicker" -> clickKeyInput = keyName;
            case "gardenF" -> s.gardenForwardKey = keyName;
            case "gardenB" -> s.gardenBackwardKey = keyName;
            case "gardenL" -> s.gardenLeftKey = keyName;
            case "gardenR" -> s.gardenRightKey = keyName;
            case "gardenBr" -> s.gardenBreakKey = keyName;
            case "gardenU" -> s.gardenUseKey = keyName;
            case "anvilTrigger" -> s.anvilAutoCombineKey = keyName;
            case "savePet" -> s.savePetKey = keyName;
            case "clipboardRun" -> s.clipboardRunKey = keyName;
        }
    }

    private void swapPetSlots(int idx1, int idx2) {
        BomboConfig.Settings s = BomboConfig.get();
        String key1 = String.valueOf(idx1 + 1);
        String key2 = String.valueOf(idx2 + 1);
        String uuid1 = s.petKeybinds.get(key1);
        String uuid2 = s.petKeybinds.get(key2);

        if (uuid1 == null) {
            s.petKeybinds.remove(key2);
        } else {
            s.petKeybinds.put(key2, uuid1);
        }

        if (uuid2 == null) {
            s.petKeybinds.remove(key1);
        } else {
            s.petKeybinds.put(key1, uuid2);
        }

        String name1 = s.petNames.get(key1);
        String name2 = s.petNames.get(key2);

        if (name1 == null) {
            s.petNames.remove(key2);
        } else {
            s.petNames.put(key2, name1);
        }

        if (name2 == null) {
            s.petNames.remove(key1);
        } else {
            s.petNames.put(key1, name2);
        }

        BomboConfig.save();
    }

    private void clearPetSlot(int idx) {
        BomboConfig.Settings s = BomboConfig.get();
        String key = String.valueOf(idx + 1);
        s.petKeybinds.remove(key);
        s.petNames.remove(key);
        BomboConfig.save();
    }

    public static float[] rgbToHsv(int r, int g, int b) {
        float var_R = (r / 255f);
        float var_G = (g / 255f);
        float var_B = (b / 255f);

        float min = Math.min(var_R, Math.min(var_G, var_B));
        float max = Math.max(var_R, Math.max(var_G, var_B));
        float del_Max = max - min;

        float h = 0;
        float s = 0;
        float v = max;

        if (del_Max != 0) {
            s = del_Max / max;

            float del_R = (((max - var_R) / 6f) + (del_Max / 2f)) / del_Max;
            float del_G = (((max - var_G) / 6f) + (del_Max / 2f)) / del_Max;
            float del_B = (((max - var_B) / 6f) + (del_Max / 2f)) / del_Max;

            if (var_R == max)
                h = del_B - del_G;
            else if (var_G == max)
                h = (1f / 3f) + del_R - del_B;
            else if (var_B == max)
                h = (2f / 3f) + del_G - del_R;

            if (h < 0)
                h += 1;
            if (h > 1)
                h -= 1;
        }
        return new float[] { h, s, v };
    }

    public static int hsvToRgb(float h, float s, float v) {
        int r = 0, g = 0, b = 0;
        if (s == 0) {
            r = g = b = (int) (v * 255f + 0.5f);
        } else {
            float h_h = (h - (float) Math.floor(h)) * 6f;
            float f = h_h - (float) Math.floor(h_h);
            float p = v * (1f - s);
            float q = v * (1f - s * f);
            float t = v * (1f - s * (1f - f));
            switch ((int) h_h) {
                case 0 -> {
                    r = (int) (v * 255f + 0.5f);
                    g = (int) (t * 255f + 0.5f);
                    b = (int) (p * 255f + 0.5f);
                }
                case 1 -> {
                    r = (int) (q * 255f + 0.5f);
                    g = (int) (v * 255f + 0.5f);
                    b = (int) (p * 255f + 0.5f);
                }
                case 2 -> {
                    r = (int) (p * 255f + 0.5f);
                    g = (int) (v * 255f + 0.5f);
                    b = (int) (t * 255f + 0.5f);
                }
                case 3 -> {
                    r = (int) (p * 255f + 0.5f);
                    g = (int) (q * 255f + 0.5f);
                    b = (int) (v * 255f + 0.5f);
                }
                case 4 -> {
                    r = (int) (t * 255f + 0.5f);
                    g = (int) (p * 255f + 0.5f);
                    b = (int) (v * 255f + 0.5f);
                }
                case 5 -> {
                    r = (int) (v * 255f + 0.5f);
                    g = (int) (p * 255f + 0.5f);
                    b = (int) (q * 255f + 0.5f);
                }
            }
        }
        return (r << 16) | (g << 8) | b;
    }
}
