/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.Font
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.Button
 *  net.minecraft.client.gui.components.Checkbox
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.ChatScreen
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.network.chat.Component
 *  org.lwjgl.glfw.GLFW
 */
package me.bombo.bomboaddons;

import java.util.Locale;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import me.bombo.bomboaddons.AutoExperiments;
import me.bombo.bomboaddons.BlockHighlight;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.CrosshairPresets;
import me.bombo.bomboaddons.CustomTimerManager;
import me.bombo.bomboaddons.GardenMovement;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.OrderedWaypoints;
import me.bombo.bomboaddons.SlotHighlight;
import me.bombo.bomboaddons.TabWidgetHud;
import me.bombo.bomboaddons.eggfinder.EggFinder;
import me.bombo.bomboaddons.mixin.WindowAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class BomboConfigGUI
extends Screen {
    private static final int SIDEBAR_WIDTH = 130;
    private static final int HEADER_HEIGHT = 40;
    private static final int ITEM_HEIGHT = 24;
    private static final int PADDING = 8;
    private final Screen parent;
    private final List<String> categories = List.of("General", "HUDs", "Experiments", "Garden", "Hotkeys", "Profiles", "Clicker", "Highlights", "Wardrobe", "Anvil", "Debug", "Kuudra", "Pets", "Keybinds", "Waypoints", "Aliases", "Chat Triggers", "Dungeons", "Coord Binds", "Mining", "Party Settings", "Block Highlights", "Particle Highlights", "Bedwars", "Fishing", "Custom Crosshair", "Custom Slots", "Custom Tracers", "Item Highlights", "Ordered Waypoints", "Timers", "Widgets", "Chat Modifier");
    public static int selectedCategory = 0;
    public static boolean loreAdditionsSubmenu = false;
    private static int loreAdditionsX = -1, loreAdditionsY = -1, loreAdditionsW = -1, loreAdditionsH = -1;
    public static boolean frozenBlazeWarnSubmenu = false;
    private static int frozenBlazeWarnX = -1, frozenBlazeWarnY = -1, frozenBlazeWarnW = -1, frozenBlazeWarnH = -1;
    public static boolean structureFinderSubmenu = false;
    private static int structureFinderX = -1, structureFinderY = -1, structureFinderW = -1, structureFinderH = -1;
        private int drawSectionHeader(GuiGraphicsExtractor g, String title, int x, int y) {
        if (!optionMatchesSearch(title)) return y;
        g.text(this.font, title, x, y, -22016, true);
        return y + 14;
    }

    public static boolean optionMatchesSearch(String label) {
        if (configSearchTerm == null || configSearchTerm.trim().isEmpty()) return true;
        String q = configSearchTerm.toLowerCase().trim();
        String l = label.replaceAll("\u00a7[0-9a-fk-orxX]", "").toLowerCase();
        return l.contains(q);
    }

    private int drawOptionLabel(GuiGraphicsExtractor g, String label, int x, int y, int color, boolean shadow) {
        if (!optionMatchesSearch(label)) return y;
        g.text(this.font, label, x, y + 4, color, shadow);
        return y + 24;
    }
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.ofEntries(
        Map.entry("General", List.of("water", "lava", "sign", "calculator", "sbe", "copy chat", "etherwarp", "sphinx", "hollow wand", "lasso", "carnival", "npc lore", "lowest bin", "craft cost", "npc sell", "trevor", "daily reward", "egg finder", "caps lock", "server list", "reconnect", "f1", "m1", "auto reconnect", "party commands", "resource pack", "command hover", "hoppity", "warp", "hypixel tooltips", "shortcut", "borderless", "diorite", "time", "weather", "search bar", "paste")),
        Map.entry("HUDs", List.of("dice", "feast", "bakery", "rng", "custom timer", "tab widget", "hoppity", "egg", "alpha", "item list", "tooltip")),
        Map.entry("Experiments", List.of("chronomatron", "ultrasequencer", "superpairs")),
        Map.entry("Garden", List.of("movement", "pest", "spray", "composter", "desk")),
        Map.entry("Hotkeys", List.of("wardrobe", "pets", "freelook", "bestiary", "focus", "item list", "search", "trade", "recipe", "usage", "gfs", "save")),
        Map.entry("Profiles", List.of("profile", "save", "load", "delete")),
        Map.entry("Clicker", List.of("auto click", "cps", "left click", "right click", "key click")),
        Map.entry("Highlights", List.of("mob highlight", "tracer", "color", "invis", "sound", "title", "spawn")),
        Map.entry("Wardrobe", List.of("wardrobe", "armor", "slot", "key")),
        Map.entry("Anvil", List.of("anvil", "combine", "books")),
        Map.entry("Debug", List.of("chat", "sounds", "guis", "entities", "commands", "api", "pet price", "messages", "lb", "particles", "lore", "composter", "fishing", "croesus", "reconnect", "performance", "keys", "esp", "filter")),
        Map.entry("Kuudra", List.of("kuudra", "perks", "keys", "crates", "pearls", "timer", "blindness")),
        Map.entry("Pets", List.of("pet", "swap", "keybind", "slot")),
        Map.entry("Keybinds", List.of("bind", "key", "command", "combo", "island")),
        Map.entry("Waypoints", List.of("waypoint", "coords", "beacon", "wall", "color")),
        Map.entry("Aliases", List.of("alias", "command", "shortcut")),
        Map.entry("Chat Triggers", List.of("trigger", "chat", "command", "title")),
        Map.entry("Dungeons", List.of("dungeon", "secrets", "croesus", "clear")),
        Map.entry("Coord Binds", List.of("coord bind", "radius", "delay")),
        Map.entry("Mining", List.of("corpse", "mineshaft", "glacite", "tracer", "vanguard", "umber", "tungsten", "structure", "corleone", "catwalk", "carpet", "dwarven", "red carpet")),
        Map.entry("Block Highlights", List.of("block", "outline", "through walls")),
        Map.entry("Particle Highlights", List.of("particle", "highlight")),
        Map.entry("Bedwars", List.of("bedwars", "spawner", "timer", "tracker")),
        Map.entry("Fishing", List.of("fishing", "reel", "catch", "sea creature")),
        Map.entry("Custom Crosshair", List.of("crosshair", "preset", "color", "dot", "plus", "circle")),
        Map.entry("Custom Slots", List.of("custom slot", "icon", "command")),
        Map.entry("Custom Tracers", List.of("custom tracer", "target", "color")),
        Map.entry("Item Highlights", List.of("item highlight", "inventory")),
        Map.entry("Ordered Waypoints", List.of("ordered", "route", "loop")),
        Map.entry("Timers", List.of("timer", "countdown", "duration", "alert")),
        Map.entry("Widgets", List.of("widget", "tab", "island")),
        Map.entry("Chat Modifier", List.of("chat modifier", "rule", "replace", "hide", "suppress", "regex"))
    );
    private static String configSearchTerm = "";
    private EditBox searchBoxWidget = null;
    private static String widgetNameInput = "";
    private static String widgetIslandInput = "All";
    private static boolean widgetEnabledInput = true;
    private static int editingWidgetIndex = -1;
    private String listPickerTitle = null;
    private List<String> listPickerOptions = null;
    private Consumer<String> listPickerSetter = null;
    private float listPickerScroll = 0.0f;
    private static int partyCommandsX = -1;
    private static int partyCommandsY = -1;
    private static int partyCommandsWidth = -1;
    private static int partyCommandsHeight = -1;
    private final List<EditBox> activeBoxes = new ArrayList<EditBox>();
    private static String clickGuiInput = "";
    private static String clickKeyInput = "";
    private static String clickItemInput = "";
    private static String clickTypeInput = "left";
    private static String bindCommandInput = "";
    private static String bindComboInput = "";
    private static String bindIslandInput = "";
    private static String bindArmorInput = "";
    private static String profileNameInput = "";
    private static String highMobInput = "";
    private static String highColorInput = "GOLD";
    private static boolean highTracerInput = false;
    private static boolean highShowInvis = false;
    private static String highIslandInput = "";
    private static String editingHighMob = null;
    private static String advEntityTypeInput = "";
    private static String advHeadHashInput = "";
    private static String advMobSizeInput = "";
    private static String advArmorPieceInput = "";
    private static String advPlayerNameInput = "";
    private static String advVisibilityInput = "ALL";
    private static boolean advShowTitleInput = false;
    private static boolean advPlaySoundInput = false;

    // Highlight History (Undo / Redo)
    private static final java.util.Deque<Map<String, BomboConfig.HighlightInfo>> highlightUndoStack = new java.util.ArrayDeque<>();
    private static final java.util.Deque<Map<String, BomboConfig.HighlightInfo>> highlightRedoStack = new java.util.ArrayDeque<>();

    public static void pushHighlightHistory() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s.highlights != null) {
            Map<String, BomboConfig.HighlightInfo> clone = new java.util.HashMap<>();
            for (Map.Entry<String, BomboConfig.HighlightInfo> e : s.highlights.entrySet()) {
                if (e.getValue() != null) {
                    BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(
                        e.getValue().color, e.getValue().showInvisible, e.getValue().enabled,
                        e.getValue().tracer, e.getValue().requiredIsland, e.getValue().isBestiary
                    );
                    hi.targetType = e.getValue().targetType;
                    hi.entityType = e.getValue().entityType;
                    hi.armorType = e.getValue().armorType;
                    hi.playerName = e.getValue().playerName;
                    hi.ridingType = e.getValue().ridingType;
                    hi.heldItem = e.getValue().heldItem;
                    hi.requiredSubarea = e.getValue().requiredSubarea;
                    hi.isAdvanced = e.getValue().isAdvanced;
                    hi.mobSize = e.getValue().mobSize;
                    hi.armorPiece = e.getValue().armorPiece;
                    hi.showTitleOnSpawn = e.getValue().showTitleOnSpawn;
                    hi.playSoundOnSpawn = e.getValue().playSoundOnSpawn;
                    if (e.getValue().headHashes != null) hi.headHashes = new java.util.ArrayList<>(e.getValue().headHashes);
                    clone.put(e.getKey(), hi);
                }
            }
            if (highlightUndoStack.size() >= 100) highlightUndoStack.removeLast();
            highlightUndoStack.push(clone);
            highlightRedoStack.clear();
        }
    }

    public static boolean undoHighlight() {
        if (!highlightUndoStack.isEmpty()) {
            BomboConfig.Settings s = BomboConfig.get();
            Map<String, BomboConfig.HighlightInfo> currentClone = new java.util.HashMap<>();
            if (s.highlights != null) {
                for (Map.Entry<String, BomboConfig.HighlightInfo> e : s.highlights.entrySet()) {
                    if (e.getValue() != null) {
                        BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(
                            e.getValue().color, e.getValue().showInvisible, e.getValue().enabled,
                            e.getValue().tracer, e.getValue().requiredIsland, e.getValue().isBestiary
                        );
                        hi.targetType = e.getValue().targetType;
                        hi.entityType = e.getValue().entityType;
                        hi.armorType = e.getValue().armorType;
                        hi.playerName = e.getValue().playerName;
                        hi.ridingType = e.getValue().ridingType;
                        hi.heldItem = e.getValue().heldItem;
                        hi.requiredSubarea = e.getValue().requiredSubarea;
                        hi.isAdvanced = e.getValue().isAdvanced;
                        hi.mobSize = e.getValue().mobSize;
                        hi.armorPiece = e.getValue().armorPiece;
                        hi.showTitleOnSpawn = e.getValue().showTitleOnSpawn;
                        hi.playSoundOnSpawn = e.getValue().playSoundOnSpawn;
                        if (e.getValue().headHashes != null) hi.headHashes = new java.util.ArrayList<>(e.getValue().headHashes);
                        currentClone.put(e.getKey(), hi);
                    }
                }
            }
            highlightRedoStack.push(currentClone);
            Map<String, BomboConfig.HighlightInfo> popped = highlightUndoStack.pop();
            s.highlights = new java.util.HashMap<>();
            for (Map.Entry<String, BomboConfig.HighlightInfo> e : popped.entrySet()) {
                if (e.getValue() != null) {
                    BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(
                        e.getValue().color, e.getValue().showInvisible, e.getValue().enabled,
                        e.getValue().tracer, e.getValue().requiredIsland, e.getValue().isBestiary
                    );
                    hi.targetType = e.getValue().targetType;
                    hi.entityType = e.getValue().entityType;
                    hi.armorType = e.getValue().armorType;
                    hi.playerName = e.getValue().playerName;
                    hi.ridingType = e.getValue().ridingType;
                    hi.heldItem = e.getValue().heldItem;
                    hi.requiredSubarea = e.getValue().requiredSubarea;
                    hi.isAdvanced = e.getValue().isAdvanced;
                    hi.mobSize = e.getValue().mobSize;
                    hi.armorPiece = e.getValue().armorPiece;
                    hi.showTitleOnSpawn = e.getValue().showTitleOnSpawn;
                    hi.playSoundOnSpawn = e.getValue().playSoundOnSpawn;
                    if (e.getValue().headHashes != null) hi.headHashes = new java.util.ArrayList<>(e.getValue().headHashes);
                    s.highlights.put(e.getKey(), hi);
                }
            }
            BomboConfig.save();
            return true;
        }
        return false;
    }

    public static boolean redoHighlight() {
        if (!highlightRedoStack.isEmpty()) {
            BomboConfig.Settings s = BomboConfig.get();
            Map<String, BomboConfig.HighlightInfo> currentClone = new java.util.HashMap<>();
            if (s.highlights != null) {
                for (Map.Entry<String, BomboConfig.HighlightInfo> e : s.highlights.entrySet()) {
                    if (e.getValue() != null) {
                        BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(
                            e.getValue().color, e.getValue().showInvisible, e.getValue().enabled,
                            e.getValue().tracer, e.getValue().requiredIsland, e.getValue().isBestiary
                        );
                        hi.targetType = e.getValue().targetType;
                        hi.entityType = e.getValue().entityType;
                        hi.armorType = e.getValue().armorType;
                        hi.playerName = e.getValue().playerName;
                        hi.ridingType = e.getValue().ridingType;
                        hi.heldItem = e.getValue().heldItem;
                        hi.requiredSubarea = e.getValue().requiredSubarea;
                        hi.isAdvanced = e.getValue().isAdvanced;
                        hi.mobSize = e.getValue().mobSize;
                        hi.armorPiece = e.getValue().armorPiece;
                        hi.showTitleOnSpawn = e.getValue().showTitleOnSpawn;
                        hi.playSoundOnSpawn = e.getValue().playSoundOnSpawn;
                        if (e.getValue().headHashes != null) hi.headHashes = new java.util.ArrayList<>(e.getValue().headHashes);
                        currentClone.put(e.getKey(), hi);
                    }
                }
            }
            highlightUndoStack.push(currentClone);
            s.highlights = highlightRedoStack.pop();
            BomboConfig.save();
            return true;
        }
        return false;
    }

    private static String listeningForKeyTarget = "";
    private static String timerNameInput = "";
    private static String timerTimeInput = "";
    private static String timerTriggerInput = "";
    private static String timerItemInput = "";
    private static boolean timerEnabledInput = true;
    private static boolean timerShowOnlyReadyInput = false;
    private static boolean timerKeepReadyInput = false;
    private static int editingTimerIndex = -1;
    private static String listeningForKeyTargetKeybinds = "";
    private static String blockNameInput = "";
    private static String blockColorInput = "GOLD";
    private static boolean blockThroughWallsInput = true;
    private static String editingBlockName = null;
    private static String itemHighMobInput = "";
    private static String itemHighColorInput = "WHITE";
    private static boolean itemHighShowInvInput = true;
    private static String editingItemHighMob = null;
    private static String partHighInput = "";
    private static String partHighColorInput = "GOLD";
    private static String editingPartHigh = null;
    private static String editingCustomTracer = null;
    private static String customTracerIdInput = "";
    private static String customTracerColorInput = "GREEN";
    private static String newTracerIdInput = "";
    private static String newTracerNameInput = "";
    private static final List<String> recordedComboKeys = new ArrayList<String>();
    private static int editingClickTargetIdx = -1;
    private static int editingKeybindIdx = -1;
    private static boolean confirmProfileDelete = false;
    private static String wpNameInput = "";
    private static String wpCoordsInput = "";
    private static String wpIslandInput = "";
    private static boolean wpThruWallsInput = true;
    private static boolean wpBeaconInput = true;
    private static String wpColorInput = "AQUA";
    private static int editingWaypointIdx = -1;
    private static String aliasCommandInput = "";
    private static String aliasActualInput = "";
    private static String editingAliasKey = null;
    private static String triggerTextInput = "";
    private static String triggerCommandInput = "";
    private static String triggerTitleInput = "";
    private static String triggerSoundInput = "";
    private static int triggerSoundTimesInput = 1;
    private static int editingTriggerIdx = -1;
    private static String customPartyTriggerInput = "";
    private static String customPartyCommandInput = "";
    private static int editingCustomPartyIdx = -1;
    private static String csGuiNameInput = "";
    private static String csSlotIndexInput = "";
    private static String csIconInput = "minecraft:barrier";
    private static String csNameInput = "";
    private static String csDescInput = "";
    private static String csCommandInput = "";
    private static int editingCustomSlotIdx = -1;
    private static String cbCoordsInput = "";
    private static String cbCommandInput = "";
    private static String cbIslandInput = "";
    private static String cbRadiusInput = "3";
    private static boolean cbShowWaypointInput = false;
    private static String cbMinDelayInput = "0";
    private static String cbMaxDelayInput = "0";
    private static int editingCoordBindIdx = -1;
    private static int colorPickerMode = 0;
    private static boolean isDraggingSv = false;
    private static boolean isDraggingHue = false;
    private static boolean isDraggingAlpha = false;
    private static float currentHue = 0.0f;
    private static String colorPickerTarget = null;
    private static Consumer<String> colorPickerSetter = null;
    private double scrollAmount = 0.0;
    private double categoryScrollAmount = 0.0;
    private final List<Button> sidebarButtons = new ArrayList<Button>();

    public static boolean isTypingOrListening() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen instanceof BomboConfigGUI) {
            BomboConfigGUI gui = (BomboConfigGUI)screen;
            if (listeningForKeyTarget != null && !listeningForKeyTarget.isEmpty()) {
                return true;
            }
            if (gui.activeBoxes != null) {
                for (EditBox box : gui.activeBoxes) {
                    if (!box.isFocused()) continue;
                    return true;
                }
            }
        }
        return false;
    }

    private static double centerCoord(double val) {
        if (val == Math.floor(val)) {
            return val + 0.5;
        }
        return val;
    }

    public static double[] parseCoords(String input) {
        String[] parts;
        String sub;
        String[] parts2;
        if (input == null || input.trim().isEmpty()) {
            return null;
        }
        if ((input = input.trim()).contains(" tp @s ") && (parts2 = (sub = input.substring(input.indexOf(" tp @s ") + 7)).trim().split("\\s+")).length >= 3) {
            try {
                double x = Double.parseDouble(parts2[0]);
                double y = Double.parseDouble(parts2[1]);
                double z = Double.parseDouble(parts2[2]);
                return new double[]{BomboConfigGUI.centerCoord(x), y, BomboConfigGUI.centerCoord(z)};
            }
            catch (NumberFormatException x) {
                // empty catch block
            }
        }
        if (input.contains("tp ")) {
            sub = input.substring(input.indexOf("tp ") + 3);
            parts2 = sub.trim().split("\\s+");
            ArrayList<Double> parsed = new ArrayList<Double>();
            for (String part : parts2) {
                try {
                    parsed.add(Double.parseDouble(part));
                    if (parsed.size() == 3) {
                        return new double[]{BomboConfigGUI.centerCoord((Double)parsed.get(0)), (Double)parsed.get(1), BomboConfigGUI.centerCoord((Double)parsed.get(2))};
                    }
                }
                catch (NumberFormatException e) {
                    parsed.clear();
                }
            }
        }
        if ((parts = input.replaceAll(",", " ").trim().split("\\s+")).length >= 3) {
            try {
                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double z = Double.parseDouble(parts[2]);
                return new double[]{BomboConfigGUI.centerCoord(x), y, BomboConfigGUI.centerCoord(z)};
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return null;
    }

    public BomboConfigGUI(Screen parent) {
        super((Component)Component.literal((String)"Bomboaddons Configuration"));
                this.parent = parent;
            }

    public static Screen create() {
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof ChatScreen) {
            return new BomboConfigGUI(null);
        }
        return new BomboConfigGUI(current);
    }

    public static void autofillCustomSlot(String title, int slotIndex) {
        selectedCategory = 26;
        csGuiNameInput = title;
        csSlotIndexInput = String.valueOf(slotIndex);
    }

    protected void init() {
                try {
            int contentBaseY;
            super.init();
            BomboConfig.Settings s = BomboConfig.get();
            this.activeBoxes.clear();
            this.clearWidgets();
            this.sidebarButtons.clear();

            // Search Bar at Top of Sidebar
            boolean wasSearchFocused = (this.searchBoxWidget != null && this.searchBoxWidget.isFocused());
            int prevCursor = this.searchBoxWidget != null ? this.searchBoxWidget.getCursorPosition() : configSearchTerm.length();
            this.searchBoxWidget = new EditBox(this.font, 8, 38, 114, 16, Component.literal("Search..."));
            this.searchBoxWidget.setHint(Component.literal("§8Search..."));
            this.searchBoxWidget.setValue(configSearchTerm);
            if (wasSearchFocused) {
                this.searchBoxWidget.setFocused(true);
                this.setFocused(this.searchBoxWidget);
                this.searchBoxWidget.setCursorPosition(Math.min(configSearchTerm.length(), prevCursor));
            }
            this.searchBoxWidget.setResponder(val -> {
                configSearchTerm = val;
                this.init();
            });
            this.addWidget(this.searchBoxWidget);
            this.activeBoxes.add(this.searchBoxWidget);
            int renderCount = 0;
            int totalRendered = 0;
            for (int i = 0; i < this.categories.size(); ++i) {
                if (s.hideCheats && (i == 2 || i == 9 || i == 24) || this.categories.get(i).equals("Party Settings")) continue;
                ++totalRendered;
            }
            int totalHeight = totalRendered * 26;
            int viewportHeight = this.height - 64 - 8;
            int maxCategoryScroll = Math.max(0, totalHeight - viewportHeight);
            this.categoryScrollAmount = Math.max(0.0, Math.min(this.categoryScrollAmount, (double)maxCategoryScroll));
            for (int i = 0; i < this.categories.size(); ++i) {
                boolean visible;
                int idx = i;
                if (s.hideCheats && (idx == 2 || idx == 9 || idx == 24) || this.categories.get(idx).equals("Party Settings")) continue;
                String catName = this.categories.get(idx);
                if (configSearchTerm != null && !configSearchTerm.trim().isEmpty()) {
                    String q = configSearchTerm.toLowerCase().trim();
                    boolean nameMatch = catName.toLowerCase().contains(q);
                    boolean featureMatch = false;
                    List<String> keywords = CATEGORY_KEYWORDS.get(catName);
                    if (keywords != null) {
                        for (String kw : keywords) {
                            if (kw.contains(q)) { featureMatch = true; break; }
                        }
                    }
                    if (!nameMatch && !featureMatch) continue;
                }
                int catY = 64 + renderCount * 26 - (int)this.categoryScrollAmount;
                ++renderCount;
                String label = (idx == selectedCategory ? "\u00a76\u00a7l> " : "\u00a77") + this.categories.get(idx);
                Button btn2 = Button.builder((Component)Component.literal((String)label), b -> {
                    selectedCategory = idx;
                    this.scrollAmount = 0.0;
                    loreAdditionsSubmenu = false;
                    frozenBlazeWarnSubmenu = false;
                    structureFinderSubmenu = false;
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
                    this.init();
                }).bounds(8, catY, 114, 22).build();
                btn2.active = visible = catY + 22 > 56 && catY < this.height - 8;
                btn2.visible = visible;
                this.addWidget(btn2);
                this.sidebarButtons.add(btn2);
            }
            int contentX = 146;
            int contentWidth = this.width - 130 - 24;
            int categoryTitleY = 56;
            int curY = contentBaseY = categoryTitleY + 30;
            switch (selectedCategory) {
                case 0: {
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;

                    if (loreAdditionsSubmenu) {
                        this.addRenderableWidget(Button.builder(Component.literal("§c← Back to General"), b -> { loreAdditionsSubmenu = false; this.scrollAmount = 0.0; this.init(); }).bounds(col1X, y1, 140, 20).build());
                        y1 += 28;
                        y1 = this.addBoolOption("Copy Canceled Order Amount (Ctrl+Click)", s.copyCanceledOrderAmount, v -> s.copyCanceledOrderAmount = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Supercraft Max Calculator (Ctrl+Click)", s.supercraftMaxCalculator, v -> s.supercraftMaxCalculator = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Starts In Absolute Time", s.startsInAbsoluteTime, v -> s.startsInAbsoluteTime = v, col1X, col1W * 2, y1);
                        break;
                    }

                    if (frozenBlazeWarnSubmenu) {
                        this.addRenderableWidget(Button.builder(Component.literal("§c← Back to General"), b -> { frozenBlazeWarnSubmenu = false; this.scrollAmount = 0.0; this.init(); }).bounds(col1X, y1, 140, 20).build());
                        y1 += 28;
                        y1 = this.addBoolOption("Frozen Blaze Warning Enabled", s.frozenBlazeWarning, v -> s.frozenBlazeWarning = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Play Warning Sound", s.fbWarnSound, v -> s.fbWarnSound = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Show Title On Screen", s.fbWarnTitle, v -> s.fbWarnTitle = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Send Chat Warning", s.fbWarnChat, v -> s.fbWarnChat = v, col1X, col1W * 2, y1);
                        y1 = this.addIntLabelSlider("AFK Warning Time (Seconds)", s.fbWarnSeconds, 5, 120, 1, v -> s.fbWarnSeconds = v, col1X, col1W * 2, y1);
                        break;
                    }

                    y1 = this.addBoolOption("Clear Water & Lava Vision", s.clearWaterAndLava, v -> s.clearWaterAndLava = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Sign Calculator", s.signCalculator, v -> s.signCalculator = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("SBE Commands", s.sbeCommands, v -> s.sbeCommands = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Copy Chat", s.copyChat, v -> s.copyChat = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Left Click Etherwarp", s.leftClickEtherwarp, v -> s.leftClickEtherwarp = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Sphinx Macro", s.sphinxMacro, v -> s.sphinxMacro = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Hollow Wand Fix", s.hollowWandClickThrough, v -> s.hollowWandClickThrough = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Hollow Wand Double Click", s.hollowWandAutoCombine, v -> s.hollowWandAutoCombine = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Lasso Fix", s.lassoClickThroughBats, v -> s.lassoClickThroughBats = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Auto Accept Carnival", s.autoAcceptCarnival, v -> s.autoAcceptCarnival = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Auto Accept NPC Lore", s.autoAcceptNpcLore, v -> s.autoAcceptNpcLore = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Lowest BIN Tooltip", s.lowestBin, v -> s.lowestBin = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Raw Craft Cost Tooltip", s.craftCostTooltip, v -> s.craftCostTooltip = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("NPC Sell Price Tooltip", s.npcPrice, v -> s.npcPrice = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Auto Trevor Quest", s.autoTrevorQuest, v -> s.autoTrevorQuest = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Daily Reward Helper", s.dailyRewardHelper, v -> s.dailyRewardHelper = v, col1X, col1W, y1);
                    
                    loreAdditionsX = col1X;
                    loreAdditionsY = y1;
                    loreAdditionsW = col1W;
                    loreAdditionsH = 24;
                    y1 = this.addBoolOption("Lore Additions", s.loreAdditionsEnabled, v -> s.loreAdditionsEnabled = v, col1X, col1W, y1);

                    y1 = this.addBoolOption("In-Chat Search Bar", s.chatSearchBar, v -> s.chatSearchBar = v, col1X, col1W, y1);
                    if (s.chatSearchBar) {
                        y1 = this.addBoolOption("Chat Search Background", s.chatSearchBackground, v -> s.chatSearchBackground = v, col1X, col1W, y1);
                    }
                    y1 = this.addBoolOption("Custom Time Enabled", s.customTimeEnabled, v -> s.customTimeEnabled = v, col1X, col1W, y1);
                    if (s.customTimeEnabled) {
                        y1 = this.addIntLabelSlider("Custom Time Hour", s.customTimeHour, 0, 23, 1, v -> s.customTimeHour = v, col1X, col1W, y1);
                        this.addRenderableWidget(Button.builder(Component.literal("Day"), b -> { s.customTimeHour = 12; BomboConfig.save(); this.init(); }).bounds(col1X, y1, col1W / 4 - 2, 18).build());
                        this.addRenderableWidget(Button.builder(Component.literal("Night"), b -> { s.customTimeHour = 0; BomboConfig.save(); this.init(); }).bounds(col1X + col1W / 4, y1, col1W / 4 - 2, 18).build());
                        this.addRenderableWidget(Button.builder(Component.literal("Sunrise"), b -> { s.customTimeHour = 6; BomboConfig.save(); this.init(); }).bounds(col1X + 2 * (col1W / 4), y1, col1W / 4 - 2, 18).build());
                        this.addRenderableWidget(Button.builder(Component.literal("Sunset"), b -> { s.customTimeHour = 18; BomboConfig.save(); this.init(); }).bounds(col1X + 3 * (col1W / 4), y1, col1W / 4 - 2, 18).build());
                        y1 += 22;
                    }
                    y1 = this.addBoolOption("Weather Override Enabled", s.customWeatherEnabled, v -> s.customWeatherEnabled = v, col1X, col1W, y1);
                    if (s.customWeatherEnabled) {
                        String[] weatherModes = new String[]{"Clear", "Rain", "Thunder"};
                        Button wBtn = Button.builder(Component.literal("Weather: " + weatherModes[Math.max(0, Math.min(2, s.customWeatherMode))]), b -> {
                            s.customWeatherMode = (s.customWeatherMode + 1) % 3;
                            b.setMessage(Component.literal("Weather: " + weatherModes[s.customWeatherMode]));
                            BomboConfig.save();
                        }).bounds(col1X + 24, y1, Math.min(col1W - 24, 160), 20).build();
                        wBtn.visible = (y1 >= 56 && y1 <= this.height - 44);
                        this.addRenderableWidget(wBtn);
                        y1 += 26;
                    }
                    y1 += 10;
                    y1 = this.addBoolOption("Hoppity Egg Finder", s.eggFinder, v -> {
                        s.eggFinder = v;
                        if (!v.booleanValue()) {
                            EggFinder.clearEggs();
                        }
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("Egg Finder Chat Alerts", s.eggFinderChat, v -> s.eggFinderChat = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Egg Finder Beacon", s.eggFinderBeacon, v -> s.eggFinderBeacon = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Egg Finder Through Walls", s.eggFinderThroughWalls, v -> s.eggFinderThroughWalls = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Hoppity Egg HUD", s.hoppityHud, v -> s.hoppityHud = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Hoppity Warp", s.hoppityWarp, v -> s.hoppityWarp = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Golden Dragon Nest Finder", s.goldenDragonNestFinder, v -> s.goldenDragonNestFinder = v, col1X, col1W, y1);

                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.addBoolOption("Ignore Caps Lock", s.ignoreCapsLock, v -> s.ignoreCapsLock = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Server List Button", s.serverListButton, v -> s.serverListButton = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Reconnect Button", s.reconnectButton, v -> s.reconnectButton = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Quick Join Commands (/f1, /m1, etc)", s.quickJoinCommands, v -> s.quickJoinCommands = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Auto Reconnect", s.autoReconnect, v -> s.autoReconnect = v, col2X, col2W, y2);
                    partyCommandsX = col2X;
                    partyCommandsY = y2;
                    partyCommandsWidth = col2W;
                    partyCommandsHeight = 24;
                    y2 = this.addBoolOption("Party Commands", s.partyCommandsEnabled, v -> s.partyCommandsEnabled = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Bypass Resource Pack", s.bypassResourcePack, v -> s.bypassResourcePack = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("NoResourcePack Feature", s.noResourcePack, v -> s.noResourcePack = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Show Command On Hover", s.showCommandOnHover, v -> s.showCommandOnHover = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Auto Hoppity Calls", s.autoHoppityCalls, v -> s.autoHoppityCalls = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Disable Hypixel Tooltips", s.disableCustomTooltips, v -> s.disableCustomTooltips = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Hypixel Shortcut Button", s.hypixelShortcutButton, v -> s.hypixelShortcutButton = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Smart Disconnect", s.smartDisconnect, v -> s.smartDisconnect = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Borderless Fullscreen", s.borderlessFullscreen, v -> {
                        s.borderlessFullscreen = v;
                        BomboConfig.save();
                        try {
                            ((WindowAccessor)(Object)Minecraft.getInstance().getWindow()).invokeUpdateFullscreen((Boolean)Minecraft.getInstance().options.enableVsync().get());
                        } catch (Throwable ignored) {}
                    }, col2X, col2W, y2);
                    y2 += 10;
                    y2 = this.addBoolOption("Fuck Diorite", s.fuckDiorite, v -> s.fuckDiorite = v, col2X, col2W, y2);
                    y2 = this.addBoolOption("Fuck Diorite Pillar Color", s.fuckDioritePillarColor, v -> s.fuckDioritePillarColor = v, col2X, col2W, y2);
                    if (s.fuckDioritePillarColor) {
                        y2 = this.addColorCycleButton("Fuck Diorite Color", s.fuckDioriteColor, v -> s.fuckDioriteColor = v, col2X, col2W, y2);
                    }
                    frozenBlazeWarnX = col2X;
                    frozenBlazeWarnY = y2;
                    frozenBlazeWarnW = col2W;
                    frozenBlazeWarnH = 24;
                    y2 = this.addBoolOption("Frozen Blaze Warning §8(Right-Click)", s.frozenBlazeWarning, v -> s.frozenBlazeWarning = v, col2X, col2W, y2);
                    break;
                }
                case 1: {
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    y1 = this.addBoolOption("Dice Tracker HUD", s.diceTracker, v -> s.diceTracker = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Feast Bakery HUD", s.feastBakeryHud, v -> s.feastBakeryHud = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("RNG Profit HUD", s.rngProfitHud, v -> s.rngProfitHud = v, col1X, col1W, y1);
                    y1 = this.addIntLabelSlider("RNG HUD Opacity", s.rngProfitHudOpacity, 0, 100, 10, v -> s.rngProfitHudOpacity = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Custom Timers HUD", s.customTimerHudEnabled, v -> s.customTimerHudEnabled = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Tab Widget HUD", s.tabWidgetHudEnabled, v -> s.tabWidgetHudEnabled = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Hoppity Egg HUD", s.hoppityHud, v -> s.hoppityHud = v, col1X, col1W, y1);
                    this.addRenderableWidget(Button.builder(Component.literal("Move HUD Elements"), b -> Minecraft.getInstance().setScreenAndShow(new HudMoveScreen())).bounds(col1X, y1, col1W, 20).build());
                    y1 += 26;
                    y1 = this.addBoolOption("Alpha Tracker HUD", s.alphaTrackerHud, v -> s.alphaTrackerHud = v, col1X, col1W, y1);
                    if (s.alphaTrackerHud) {
                        y1 = this.addBoolOption("Alpha Only When Open", s.alphaTrackerOnlyWhenOpen, v -> s.alphaTrackerOnlyWhenOpen = v, col1X, col1W, y1);
                        y1 = this.addBoolOption("Alpha Hide When Closed", s.alphaTrackerHideWhenClosed, v -> s.alphaTrackerHideWhenClosed = v, col1X, col1W, y1);
                        y1 = this.addBoolOption("Alpha Show Players", s.alphaTrackerShowPlayers, v -> s.alphaTrackerShowPlayers = v, col1X, col1W, y1);
                    }
                    y1 = this.addBoolOption("Item List Enabled", s.itemListEnabled, v -> s.itemListEnabled = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Rarity Background", s.itemListColoredBackground, v -> s.itemListColoredBackground = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Remove Background", s.itemListRemoveBackground, v -> s.itemListRemoveBackground = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Lock Item List Position", s.itemListLocked, v -> s.itemListLocked = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Separate IL Search", s.itemListSeparateSearch, v -> s.itemListSeparateSearch = v, col1X, col1W, y1);
                    y1 = this.addBoolOption("Keep IL Search Vis", s.itemListSearchAlwaysVisible, v -> s.itemListSearchAlwaysVisible = v, col1X, col1W, y1);

                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.addBoolOption("Custom Tooltip Bg", s.customTooltipBg, v -> s.customTooltipBg = v, col2X, col2W, y2);
                    y2 += 24;
                    this.addRenderableWidget(Button.builder(Component.literal("Bg Color"), b -> colorPickerMode = 0).bounds(col2X, y2, col2W / 2 - 2, 18).build());
                    this.addRenderableWidget(Button.builder(Component.literal("Border Color"), b -> colorPickerMode = 1).bounds(col2X + col2W / 2 + 2, y2, col2W / 2 - 2, 18).build());
                    y2 += 22;
                    break;
                }
                case 2: {
                    curY += 24;
                    curY = this.addBoolOption("Auto Experiments", s.autoExperiments, v -> {
                        s.autoExperiments = v;
                        if (!v.booleanValue()) {
                            AutoExperiments.reset();
                        }
                    }, contentX, contentWidth, curY);
                    curY = this.addIntLabelSlider("Click Delay", s.experimentClickDelay, 0, 2000, 50, v -> {
                        s.experimentClickDelay = v;
                    }, contentX, 150, curY);
                    curY = this.addIntLabelSlider("Serum Count", s.experimentSerumCount, 0, 3, 1, v -> {
                        s.experimentSerumCount = v;
                    }, contentX, 150, curY);
                    curY = this.addBoolOption("Auto Close", s.experimentAutoClose, v -> {
                        s.experimentAutoClose = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Get Max XP", s.experimentGetMaxXp, v -> {
                        s.experimentGetMaxXp = v;
                    }, contentX, contentWidth, curY);
                    String[] typeNames = new String[]{"Left-Click", "Middle-Click", "Shift-Click"};
                    String typeLabel = "Click Type: " + typeNames[Math.max(0, Math.min(2, s.experimentClickType))];
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)typeLabel), btn -> {
                        s.experimentClickType = (s.experimentClickType + 1) % 3;
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX, curY += 5, 150, 20).build());
                    curY += 29;
                    break;
                }
                case 3: {
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    y1 = this.addBoolOption("Garden Movement", s.gardenMovement, v -> {
                        s.gardenMovement = v;
                        if (!v.booleanValue()) {
                            GardenMovement.reset();
                        }
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("Lock Mouse on Movement", s.lockMouseOnGarden, v -> {
                        s.lockMouseOnGarden = v;
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("Sugar Cane Mode", s.gardenSugarCane, v -> {
                        s.gardenSugarCane = v;
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("Direction Helper Warning", s.gardenDirectionHelper, v -> {
                        s.gardenDirectionHelper = v;
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("Macro Check Detector", s.gardenMacroCheckDetector, v -> {
                        s.gardenMacroCheckDetector = v;
                    }, col1X, col1W, y1);
                    if (s.gardenMacroCheckDetector) {
                        y1 = this.addBoolOption("Stop Movement on Check", s.gardenMacroCheckStop, v -> {
                            s.gardenMacroCheckStop = v;
                        }, col1X, col1W, y1);
                        y1 = this.addCycleOption("Alarm Sound", s.gardenMacroCheckSound, List.of("Anvil", "Pling", "Wither", "Explode"), v -> {
                            s.gardenMacroCheckSound = v;
                        }, col1X, col1W, y1);
                        y1 = this.addIntLabelSlider("Sound Repeats", s.gardenMacroCheckSoundCount, 1, 50, 1, v -> {
                            s.gardenMacroCheckSoundCount = v;
                        }, col1X, col1W, y1);
                        y1 = this.addIntLabelSlider("Sound Delay (ms)", s.gardenMacroCheckSoundDelay, 100, 2000, 50, v -> {
                            s.gardenMacroCheckSoundDelay = v;
                        }, col1X, col1W, y1);
                    }
                    y1 += 10;
                    y1 = this.addKeyBindButton("Forward", s.gardenForwardKey, v -> {
                        s.gardenForwardKey = v;
                    }, "gardenF", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Backward", s.gardenBackwardKey, v -> {
                        s.gardenBackwardKey = v;
                    }, "gardenB", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Left", s.gardenLeftKey, v -> {
                        s.gardenLeftKey = v;
                    }, "gardenL", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Right", s.gardenRightKey, v -> {
                        s.gardenRightKey = v;
                    }, "gardenR", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Break", s.gardenBreakKey, v -> {
                        s.gardenBreakKey = v;
                    }, "gardenBr", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Use", s.gardenUseKey, v -> {
                        s.gardenUseKey = v;
                    }, "gardenU", col1X, col1W, y1);
                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.addBoolOption("Composter Helper", s.composterHelper, v -> {
                        s.composterHelper = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Composter HUD", s.composterHud, v -> {
                        s.composterHud = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Composter Timer HUD", s.composterTimerHud, v -> {
                        s.composterTimerHud = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Pest ESP Enabled", s.pestEsp, v -> {
                        s.pestEsp = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Pest Waypoints", s.pestSpawnWaypoint, v -> {
                        s.pestSpawnWaypoint = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Remove Waypoint On Return", s.pestWaypointRemoveOnNear, v -> {
                        s.pestWaypointRemoveOnNear = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Pest Waypoint Beacon", s.pestWaypointBeacon, v -> {
                        s.pestWaypointBeacon = v;
                    }, col2X, col2W, y2);
                    y2 = this.addIntLabelSlider("Pest Waypoint Duration", s.pestWaypointDuration, 0, 600, 10, v -> {
                        s.pestWaypointDuration = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Pest Tracers", s.pestEspTracer, v -> {
                        s.pestEspTracer = v;
                    }, col2X, col2W, y2);
                    y2 = this.addBoolOption("Cheese Tracers", s.cheeseTracer, v -> {
                        s.cheeseTracer = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Pest Color", s.pestEspColor, v -> {
                        s.pestEspColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addFloatLabelSlider("Pest Size", s.pestEspThickness, 0.5f, 5.0f, 0.5f, v -> {
                        s.pestEspThickness = v.floatValue();
                    }, col2X, col2W, y2);
                    break;
                }
                case 4: {
                    int col1X = contentX;
                    int col1W = (contentWidth - 20) / 2;
                    int col2X = contentX + col1W + 20;
                    int col2W = col1W;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y1 = this.addKeyBindButton("Trade", s.tradeKey, v -> {
                        s.tradeKey = v;
                    }, "trade", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Recipe", s.recipeKey, v -> {
                        s.recipeKey = v;
                    }, "recipe", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Texture Toggle", s.textureToggleKey, v -> {
                        s.textureToggleKey = v;
                    }, "textureToggle", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Usage", s.usageKey, v -> {
                        s.usageKey = v;
                    }, "usage", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Show Info", s.showItemKey, v -> {
                        s.showItemKey = v;
                    }, "showItem", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Count Items", s.countItemKey, v -> {
                        s.countItemKey = v;
                    }, "countItem", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Copy NBT", s.copyNbtKey, v -> {
                        s.copyNbtKey = v;
                    }, "copyNbt", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("GFS Max", s.gfsMaxKey, v -> {
                        s.gfsMaxKey = v;
                    }, "gfsMax", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("GFS Stack", s.gfsStackKey, v -> {
                        s.gfsStackKey = v;
                    }, "gfsStack", col1X, col1W, y1);
                    y1 = this.addKeyBindButton("Save Inventory", s.saveInventoryKey, v -> {
                        s.saveInventoryKey = v;
                    }, "saveInventory", col1X, col1W, y1);

                    y2 = this.addKeyBindButton("Chat Peek", s.chatPeekKey, v -> {
                        s.chatPeekKey = v;
                    }, "chatPeek", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Next Page", s.nextPageKey, v -> {
                        s.nextPageKey = v;
                    }, "nextPage", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Prev Page", s.prevPageKey, v -> {
                        s.prevPageKey = v;
                    }, "prevPage", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Go Back", s.goBackKey, v -> {
                        s.goBackKey = v;
                    }, "goBack", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Smart Back", s.smartGoBackKey, v -> {
                        s.smartGoBackKey = v;
                    }, "smartBack", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Save Pet", s.savePetKey, v -> {
                        s.savePetKey = v;
                    }, "savePet", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Run Clipboard Cmd", s.clipboardRunKey, v -> {
                        s.clipboardRunKey = v;
                    }, "clipboardRun", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Freelook", s.freelookKey, v -> {
                        s.freelookKey = v;
                    }, "freelook", col2X, col2W, y2);
                    y2 = this.addCycleOption("Freelook Mode", s.freelookToggle ? "Toggle" : "Hold", List.of("Hold", "Toggle"), v -> {
                        s.freelookToggle = "Toggle".equalsIgnoreCase(v);
                        BomboConfig.save();
                    }, col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Bestiary Highlight", s.bestiaryHighlightKey, v -> {
                        s.bestiaryHighlightKey = v;
                    }, "bestiaryHighlight", col2X, col2W, y2);
                    y2 = this.addKeyBindButton("Item List Search Focus", s.itemListFocusKey, v -> {
                        s.itemListFocusKey = v;
                    }, "itemListFocus", col2X, col2W, y2);
                    break;
                }
                case 6: {
                    curY += 24;
                    curY = this.addBoolOption("Auto GUI Clicker", s.autoClicker, v -> {
                        s.autoClicker = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Keypress Clicker", s.chestClicker, v -> {
                        s.chestClicker = v;
                    }, contentX, contentWidth, curY);
                    curY += 10;
                    curY = this.addTextBox("GUI Name", clickGuiInput, v -> {
                        clickGuiInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addTextBox("Item Name", clickItemInput, v -> {
                        clickItemInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addKeyBindButton("Key", clickKeyInput, v -> {
                        clickKeyInput = v;
                    }, "clicker", contentX, contentWidth, curY);
                    String addBtnText = editingClickTargetIdx != -1 ? "\u00a7e\u2714 Save Target" : "\u00a7a+ Add Target";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!clickGuiInput.isEmpty() && !clickKeyInput.isEmpty()) {
                            if (editingClickTargetIdx != -1) {
                                ClickLogic.updateTarget(editingClickTargetIdx, clickItemInput, clickGuiInput, clickKeyInput, clickTypeInput, false);
                                editingClickTargetIdx = -1;
                            } else {
                                ClickLogic.setTarget(clickItemInput, clickGuiInput, clickKeyInput, clickTypeInput, false);
                            }
                            clickGuiInput = "";
                            clickKeyInput = "";
                            clickItemInput = "";
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingClickTargetIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingClickTargetIdx = -1;
                            clickGuiInput = "";
                            clickKeyInput = "";
                            clickItemInput = "";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    for (int i = 0; i < ClickLogic.getTargets().size(); ++i) {
                        int idx = i;
                        ClickLogic.ClickTarget target = ClickLogic.getTargets().get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingClickTargetIdx = idx;
                            clickGuiInput = target.gui;
                            clickItemInput = target.item;
                            clickKeyInput = target.keyName;
                            this.init();
                        }).bounds(contentX + 180, itemY + 7, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            ClickLogic.removeTarget(idx);
                            this.init();
                        }).bounds(contentX + 225, itemY + 7, 35, 18).build());
                    }
                    break;
                }
                case 5: {
                    curY += 24;
                    ArrayList<String> profiles = new ArrayList<String>(s.profileBinds.keySet());
                    if (!profiles.contains("default")) {
                        profiles.add(0, "default");
                    }
                    int currentIdx = profiles.indexOf(s.activeProfile);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        confirmProfileDelete = false;
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 150, curY, 20, 20).build());
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        confirmProfileDelete = false;
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 175, curY, 20, 20).build());
                    if (!s.activeProfile.equals("default") && !s.activeProfile.equals("General")) {
                        String delText = confirmProfileDelete ? "\u00a7c\u00a7lCONFIRM?" : "\u00a7cDEL";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)delText), btn -> {
                            if (confirmProfileDelete) {
                                s.profileBinds.remove(s.activeProfile);
                                s.keybindBinds.remove(s.activeProfile);
                                s.customWaypoints.remove(s.activeProfile);
                                s.profileChatTriggers.remove(s.activeProfile);
                                s.coordBinds.remove(s.activeProfile);
                                s.activeProfile = "default";
                                BomboConfig.save();
                                confirmProfileDelete = false;
                                this.init();
                            } else {
                                confirmProfileDelete = true;
                                this.init();
                            }
                        }).bounds(contentX + 200, curY, confirmProfileDelete ? 65 : 30, 20).build());
                    }
                    curY += 29;
                    curY = this.addTextBox("Create New Profile", profileNameInput, v -> {
                        profileNameInput = v;
                    }, contentX, contentWidth - 60, curY);
                    int createBtnY = curY - 24 - 5;
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7aCreate"), btn -> {
                        if (!profileNameInput.isEmpty() && !s.profileBinds.containsKey(profileNameInput)) {
                            s.profileBinds.put(profileNameInput, new ArrayList());
                            s.activeProfile = profileNameInput;
                            BomboConfig.save();
                            profileNameInput = "";
                            this.init();
                        }
                    }).bounds(contentX + contentWidth - 55, createBtnY, 55, 20).build());
                    curY += 10;
                    curY += 24;
                    curY = this.addTextBox("Command", bindCommandInput, v -> {
                        bindCommandInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addComboBindButton("Combo", bindComboInput, v -> {
                        bindComboInput = v;
                    }, "profileCombo", contentX, contentWidth, curY);
                    String addBindText = editingKeybindIdx != -1 ? "\u00a7e\u2714 Save Bind" : "\u00a7a+ Add Bind";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBindText), btn -> {
                        List<Integer> codes;
                        if (!(bindCommandInput.isEmpty() || bindComboInput.isEmpty() || (codes = this.parseCombo(bindComboInput)).isEmpty())) {
                            s.profileBinds.putIfAbsent(s.activeProfile, new ArrayList());
                            if (editingKeybindIdx != -1) {
                                s.profileBinds.get(s.activeProfile).set(editingKeybindIdx, new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                                editingKeybindIdx = -1;
                            } else {
                                s.profileBinds.get(s.activeProfile).add(new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput));
                            }
                            BomboConfig.save();
                            bindCommandInput = "";
                            bindComboInput = "";
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingKeybindIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingKeybindIdx = -1;
                            bindCommandInput = "";
                            bindComboInput = "";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (int i = 0; i < binds.size(); ++i) {
                            int idx = i;
                            BomboConfig.CommandBind bind = binds.get(idx);
                            int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                            if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                                editingKeybindIdx = idx;
                                bindCommandInput = bind.command;
                                bindComboInput = bind.keyName;
                                this.init();
                            }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                                binds.remove(idx);
                                BomboConfig.save();
                                this.init();
                            }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                        }
                    }
                    break;
                }
                                                case 7: {
                    curY += 24;
                    curY = this.addBoolOption("Highlights Enabled", s.highlightsEnabled, v -> {
                        s.highlightsEnabled = v;
                    }, contentX, contentWidth, curY);
                    
                    curY = this.addFloatLabelSlider("Tracer Width: " + String.format("%.1f", s.tracerWidth), s.tracerWidth, 0.5f, 10.0f, 0.5f, v -> {
                        s.tracerWidth = v;
                    }, contentX, contentWidth, curY);

                    int modeBtnY = curY;
                    curY += 26;
                    String modeBtnText = s.highlightAdvancedMode ? "§bMode: §e[ Advanced Mode ] §7(Click to switch)" : "§bMode: §a[ Basic Mode ] §7(Click to switch)";
                    this.addRenderableWidget(Button.builder(Component.literal(modeBtnText), btn -> {
                        s.highlightAdvancedMode = !s.highlightAdvancedMode;
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX, modeBtnY, 240, 20).build());



                    if (s.highlightAdvancedMode) {
                        curY = this.addTextBox("Nametag / Regex", highMobInput, v -> highMobInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Entity Type (e.g. zombie, tropical_fish:blue)", advEntityTypeInput, v -> advEntityTypeInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Head Texture Hash", advHeadHashInput, v -> advHeadHashInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Mob Size (e.g. 24, 10+, big, small)", advMobSizeInput, v -> advMobSizeInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Armor Piece (e.g. chainmail, diamond)", advArmorPieceInput, v -> advArmorPieceInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Player Name / Skin", advPlayerNameInput, v -> advPlayerNameInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Island / Subarea", highIslandInput, v -> highIslandInput = v, contentX, contentWidth, curY);
                        curY = this.addCycleOption("Visibility", advVisibilityInput, List.of("ALL", "ONLY_VISIBLE", "ONLY_INVISIBLE"), v -> advVisibilityInput = v, contentX, contentWidth, curY);
                        curY = this.addColorCycleButton("Color", highColorInput, v -> highColorInput = v, contentX, contentWidth, curY);
                        curY = this.addBoolOption("Tracer", highTracerInput, v -> highTracerInput = v, contentX, contentWidth, curY);
                        curY = this.addBoolOption("Show Title On Spawn", advShowTitleInput, v -> advShowTitleInput = v, contentX, contentWidth, curY);
                        curY = this.addBoolOption("Play Sound On Spawn", advPlaySoundInput, v -> advPlaySoundInput = v, contentX, contentWidth, curY);

                        int finalCurY = curY += 6;
                        String addBtnText = editingHighMob != null ? "§e✔ Save Advanced Highlight" : "§a+ Add Advanced Highlight";
                        this.addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                            String key = !highMobInput.trim().isEmpty() ? highMobInput.trim() : (!advEntityTypeInput.trim().isEmpty() ? advEntityTypeInput.trim() : advHeadHashInput.trim());
                            if (!key.isEmpty()) {
                                pushHighlightHistory();
                                if (editingHighMob != null) {
                                    s.highlights.remove(editingHighMob);
                                }
                                boolean showInvis = "ALL".equals(advVisibilityInput) || "ONLY_INVISIBLE".equals(advVisibilityInput);
                                BomboConfig.HighlightInfo hi = new BomboConfig.HighlightInfo(highColorInput.toUpperCase(Locale.ROOT), showInvis, true, highTracerInput, highIslandInput.trim());
                                hi.isAdvanced = true;
                                hi.targetType = "ADVANCED";
                                hi.entityType = advEntityTypeInput.trim();
                                if (!advHeadHashInput.trim().isEmpty()) {
                                    hi.headHashes = new ArrayList<>(List.of(advHeadHashInput.trim().toLowerCase(Locale.ROOT)));
                                }
                                hi.mobSize = advMobSizeInput.trim();
                                hi.armorPiece = advArmorPieceInput.trim();
                                hi.playerName = advPlayerNameInput.trim();
                                hi.showTitleOnSpawn = advShowTitleInput;
                                hi.playSoundOnSpawn = advPlaySoundInput;
                                s.highlights.put(key.toLowerCase(Locale.ROOT), hi);
                                BomboConfig.save();
                                highMobInput = "";
                                advEntityTypeInput = "";
                                advHeadHashInput = "";
                                advMobSizeInput = "";
                                advArmorPieceInput = "";
                                advPlayerNameInput = "";
                                advShowTitleInput = false;
                                advPlaySoundInput = false;
                                highIslandInput = "";
                                editingHighMob = null;
                                this.init();
                            }
                        }).bounds(contentX, finalCurY, 180, 20).build());

                        if (editingHighMob != null) {
                            this.addRenderableWidget(Button.builder(Component.literal("§cCancel Edit"), btn -> {
                                highMobInput = "";
                                advEntityTypeInput = "";
                                advHeadHashInput = "";
                                advMobSizeInput = "";
                                advArmorPieceInput = "";
                                advPlayerNameInput = "";
                                advShowTitleInput = false;
                                advPlaySoundInput = false;
                                highIslandInput = "";
                                editingHighMob = null;
                                this.init();
                            }).bounds(contentX + 185, finalCurY, 80, 20).build());
                        }
                    } else {
                        curY = this.addTextBox("Mob Name", highMobInput, v -> highMobInput = v, contentX, contentWidth, curY);
                        curY = this.addTextBox("Island (e.g. garden, !garden)", highIslandInput, v -> highIslandInput = v, contentX, contentWidth, curY);
                        curY = this.addColorCycleButton("Color", highColorInput, v -> highColorInput = v, contentX, contentWidth, curY);
                        curY = this.addBoolOption("Tracer", highTracerInput, v -> highTracerInput = v, contentX, contentWidth, curY);

                        int finalCurY = curY += 6;
                        String addBtnText = editingHighMob != null ? "§e✔ Save Highlight" : "§a+ Add Highlight";
                        this.addRenderableWidget(Button.builder(Component.literal(addBtnText), btn -> {
                            String cleanMob = highMobInput.trim().replace("\"", "").trim();
                            if (!cleanMob.isEmpty()) {
                                pushHighlightHistory();
                                if (editingHighMob != null) {
                                    s.highlights.remove(editingHighMob);
                                }
                                s.highlights.put(cleanMob.toLowerCase(Locale.ROOT), new BomboConfig.HighlightInfo(highColorInput.toUpperCase(Locale.ROOT), true, true, highTracerInput, highIslandInput.trim()));
                                BomboConfig.save();
                                highMobInput = "";
                                highIslandInput = "";
                                highColorInput = "GOLD";
                                highTracerInput = false;
                                editingHighMob = null;
                                this.init();
                            }
                        }).bounds(contentX, finalCurY, 140, 20).build());

                        if (editingHighMob != null) {
                            this.addRenderableWidget(Button.builder(Component.literal("§cCancel Edit"), btn -> {
                                highMobInput = "";
                                highIslandInput = "";
                                highColorInput = "GOLD";
                                highTracerInput = false;
                                editingHighMob = null;
                                this.init();
                            }).bounds(contentX + 145, finalCurY, 80, 20).build());
                        }
                    }
                    int listStartY = curY += 30;
                    List<String> generalMobs = me.bombo.bomboaddons.features.BestiaryManager.getGeneralHighlights(s.highlights);
                    Map<String, List<String>> groupedBestiary = me.bombo.bomboaddons.features.BestiaryManager.getGroupedBestiary(s.highlights);
                    int currentListY = listStartY + 15 - (int)this.scrollAmount;

                    // 1. General / Custom Highlights
                    boolean isGeneralCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("General");
                    int generalHeaderY = currentListY;
                    currentListY += 24;

                    if (generalHeaderY > listStartY + 5 && generalHeaderY < this.height - 20) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal(isGeneralCollapsed ? "\u00a7e▶" : "\u00a7a▼"), btn -> {
                            if (s.collapsedBestiaryCategories == null) s.collapsedBestiaryCategories = new HashSet<String>();
                            if (isGeneralCollapsed) s.collapsedBestiaryCategories.remove("General");
                            else s.collapsedBestiaryCategories.add("General");
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX, generalHeaderY + 2, 22, 18).build());

                        String defaultGeneralColor = me.bombo.bomboaddons.features.BestiaryManager.getCategoryColor("General");
                        this.addRenderableWidget(Button.builder((Component)Component.literal(this.getColorFormatting(defaultGeneralColor) + defaultGeneralColor), btn -> {
                            colorPickerTarget = "General Color";
                            colorPickerSetter = color -> {
                                for (String m : generalMobs) {
                                    if (s.highlights.containsKey(m)) s.highlights.get(m).color = color;
                                }
                                me.bombo.bomboaddons.features.BestiaryManager.setCategoryColor("General", color);
                                BomboConfig.save();
                            };
                            this.init();
                        }).bounds(contentX + contentWidth - 210, generalHeaderY + 2, 60, 18).build());

                        boolean anyGeneralTracer = generalMobs.stream().anyMatch(m -> s.highlights.containsKey(m) && s.highlights.get(m).tracer);
                        this.addRenderableWidget(Button.builder((Component)Component.literal(anyGeneralTracer ? "\u00a7bTRACER" : "\u00a77NO TR"), btn -> {
                            for (String m : generalMobs) {
                                if (s.highlights.containsKey(m)) s.highlights.get(m).tracer = !anyGeneralTracer;
                            }
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + contentWidth - 145, generalHeaderY + 2, 55, 18).build());

                        this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7aALL"), btn -> {
                            boolean anyDisabled = generalMobs.stream().anyMatch(m -> s.highlights.containsKey(m) && !s.highlights.get(m).enabled);
                            for (String m : generalMobs) {
                                if (s.highlights.containsKey(m)) s.highlights.get(m).enabled = anyDisabled;
                            }
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + contentWidth - 85, generalHeaderY + 2, 40, 18).build());

                        this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7cDEL"), btn -> {
                            pushHighlightHistory();
                            for (String m : generalMobs) {
                                s.highlights.remove(m);
                            }
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + contentWidth - 40, generalHeaderY + 2, 35, 18).build());
                    }

                    if (!isGeneralCollapsed) {
                        for (String mobName : generalMobs) {
                            int itemY = currentListY;
                            currentListY += 22;
                            if (itemY <= listStartY + 5 || itemY >= this.height - 20) continue;
                            BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                            if (info == null) continue;

                            this.addRenderableWidget(Button.builder((Component)Component.literal(this.getColorFormatting(info.color) + info.color), btn -> {
                                colorPickerTarget = mobName + " Color";
                                colorPickerSetter = color -> {
                                    info.color = color;
                                    BomboConfig.save();
                                };
                                this.init();
                            }).bounds(contentX + contentWidth - 210, itemY + 2, 60, 18).build());

                            this.addRenderableWidget(Button.builder((Component)Component.literal(info.tracer ? "\u00a7bTR" : "\u00a77OFF"), btn -> {
                                info.tracer = !info.tracer;
                                BomboConfig.save();
                                this.init();
                            }).bounds(contentX + contentWidth - 145, itemY + 2, 38, 18).build());

                            String toggleLabel = info.enabled ? "\u00a7aON" : "\u00a7cOFF";
                            this.addRenderableWidget(Button.builder((Component)Component.literal(toggleLabel), btn -> {
                                info.enabled = !info.enabled;
                                BomboConfig.save();
                                this.init();
                            }).bounds(contentX + contentWidth - 102, itemY + 2, 38, 18).build());

                            this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7eEDIT"), b -> {
                                highMobInput = mobName;
                                highColorInput = info.color;
                                highTracerInput = info.tracer;
                                highShowInvis = info.showInvisible;
                                highIslandInput = info.requiredIsland != null ? info.requiredIsland : "";
                                editingHighMob = mobName;
                                if (info.isAdvanced || (info.mobSize != null && !info.mobSize.isEmpty()) || (info.entityType != null && !info.entityType.isEmpty()) || (info.headHashes != null && !info.headHashes.isEmpty())) {
                                    s.highlightAdvancedMode = true;
                                    advEntityTypeInput = info.entityType != null ? info.entityType : "";
                                    advHeadHashInput = info.headHashes != null && !info.headHashes.isEmpty() ? info.headHashes.get(0) : "";
                                    advMobSizeInput = info.mobSize != null ? info.mobSize : "";
                                    advArmorPieceInput = info.armorPiece != null ? info.armorPiece : "";
                                    advPlayerNameInput = info.playerName != null ? info.playerName : "";
                                    advVisibilityInput = info.showInvisible ? "ALL" : "ONLY_VISIBLE";
                                    advShowTitleInput = info.showTitleOnSpawn;
                                    advPlaySoundInput = info.playSoundOnSpawn;
                                }
                                this.init();
                            }).bounds(contentX + contentWidth - 60, itemY + 2, 32, 18).build());

                            this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7cDEL"), b -> {
                                pushHighlightHistory();
                                s.highlights.remove(mobName);
                                BomboConfig.save();
                                if (editingHighMob != null && editingHighMob.equals(mobName)) {
                                    highMobInput = "";
                                    editingHighMob = null;
                                }
                                this.init();
                            }).bounds(contentX + contentWidth - 25, itemY + 2, 25, 18).build());
                        }
                    }

                    // Spacer
                    currentListY += 8;

                    // 2. Bestiary Group
                    int totalBestiaryMobs = 0;
                    for (List<String> list : groupedBestiary.values()) totalBestiaryMobs += list.size();

                    boolean isBestiaryParentCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("Bestiary");

                    int bestiaryHeaderY = currentListY;
                    currentListY += 24;

                    if (bestiaryHeaderY > listStartY + 5 && bestiaryHeaderY < this.height - 20) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal(isBestiaryParentCollapsed ? "\u00a7e▶" : "\u00a7a▼"), btn -> {
                            if (s.collapsedBestiaryCategories == null) s.collapsedBestiaryCategories = new HashSet<String>();
                            if (isBestiaryParentCollapsed) s.collapsedBestiaryCategories.remove("Bestiary");
                            else s.collapsedBestiaryCategories.add("Bestiary");
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX, bestiaryHeaderY + 2, 22, 18).build());

                        String defaultCatColor = me.bombo.bomboaddons.features.BestiaryManager.getCategoryColor("Bestiary");
                        this.addRenderableWidget(Button.builder((Component)Component.literal(this.getColorFormatting(defaultCatColor) + defaultCatColor), btn -> {
                            colorPickerTarget = "Bestiary Color";
                            colorPickerSetter = color -> {
                                for (String c : groupedBestiary.keySet()) {
                                    me.bombo.bomboaddons.features.BestiaryManager.setCategoryColor(c, color);
                                }
                                me.bombo.bomboaddons.features.BestiaryManager.setCategoryColor("Bestiary", color);
                            };
                            this.init();
                        }).bounds(contentX + contentWidth - 210, bestiaryHeaderY + 2, 60, 18).build());

                        boolean anyBestiaryTracer = groupedBestiary.keySet().stream().anyMatch(me.bombo.bomboaddons.features.BestiaryManager::getCategoryTracer);
                        this.addRenderableWidget(Button.builder((Component)Component.literal(anyBestiaryTracer ? "\u00a7bTRACER" : "\u00a77NO TR"), btn -> {
                            for (String c : groupedBestiary.keySet()) {
                                me.bombo.bomboaddons.features.BestiaryManager.setCategoryTracer(c, !anyBestiaryTracer);
                            }
                            this.init();
                        }).bounds(contentX + contentWidth - 145, bestiaryHeaderY + 2, 55, 18).build());

                        this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7aALL"), btn -> {
                            boolean anyDisabled = false;
                            for (List<String> list : groupedBestiary.values()) {
                                for (String m : list) {
                                    if (s.highlights.containsKey(m) && !s.highlights.get(m).enabled) anyDisabled = true;
                                }
                            }
                            for (List<String> list : groupedBestiary.values()) {
                                for (String m : list) {
                                    if (s.highlights.containsKey(m)) s.highlights.get(m).enabled = anyDisabled;
                                }
                            }
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + contentWidth - 85, bestiaryHeaderY + 2, 40, 18).build());

                        this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7cDEL"), btn -> {
                            pushHighlightHistory();
                            for (List<String> list : groupedBestiary.values()) {
                                for (String m : list) {
                                    s.highlights.remove(m);
                                }
                            }
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + contentWidth - 40, bestiaryHeaderY + 2, 35, 18).build());
                    }

                    if (!isBestiaryParentCollapsed) {
                        int catIndex = 0;
                        for (Map.Entry<String, List<String>> entry : groupedBestiary.entrySet()) {
                            String cat = entry.getKey();
                            List<String> mobsInCat = entry.getValue();
                            boolean isCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains(cat);

                            if (catIndex > 0) {
                                currentListY += 6;
                            }
                            catIndex++;

                            int headerY = currentListY;
                            currentListY += 24;

                            if (headerY > listStartY + 5 && headerY < this.height - 20) {
                                this.addRenderableWidget(Button.builder((Component)Component.literal(isCollapsed ? "\u00a7e▶" : "\u00a7a▼"), btn -> {
                                    if (s.collapsedBestiaryCategories == null) s.collapsedBestiaryCategories = new HashSet<String>();
                                    if (isCollapsed) s.collapsedBestiaryCategories.remove(cat);
                                    else s.collapsedBestiaryCategories.add(cat);
                                    BomboConfig.save();
                                    this.init();
                                }).bounds(contentX + 16, headerY + 2, 22, 18).build());

                                String catColor = me.bombo.bomboaddons.features.BestiaryManager.getCategoryColor(cat);
                                this.addRenderableWidget(Button.builder((Component)Component.literal(this.getColorFormatting(catColor) + catColor), btn -> {
                                    colorPickerTarget = cat + " Color";
                                    colorPickerSetter = color -> {
                                        me.bombo.bomboaddons.features.BestiaryManager.setCategoryColor(cat, color);
                                    };
                                    this.init();
                                }).bounds(contentX + contentWidth - 210, headerY + 2, 60, 18).build());

                                boolean catTracer = me.bombo.bomboaddons.features.BestiaryManager.getCategoryTracer(cat);
                                this.addRenderableWidget(Button.builder((Component)Component.literal(catTracer ? "\u00a7bTRACER" : "\u00a77NO TR"), btn -> {
                                    me.bombo.bomboaddons.features.BestiaryManager.setCategoryTracer(cat, !catTracer);
                                    this.init();
                                }).bounds(contentX + contentWidth - 145, headerY + 2, 55, 18).build());

                                this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7aALL"), btn -> {
                                    boolean anyDisabled = mobsInCat.stream().anyMatch(m -> s.highlights.containsKey(m) && !s.highlights.get(m).enabled);
                                    for (String m : mobsInCat) {
                                        if (s.highlights.containsKey(m)) s.highlights.get(m).enabled = anyDisabled;
                                    }
                                    BomboConfig.save();
                                    this.init();
                                }).bounds(contentX + contentWidth - 85, headerY + 2, 40, 18).build());

                                this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7cDEL"), btn -> {
                                    pushHighlightHistory();
                                    for (String m : mobsInCat) {
                                        s.highlights.remove(m);
                                    }
                                    BomboConfig.save();
                                    this.init();
                                }).bounds(contentX + contentWidth - 40, headerY + 2, 35, 18).build());
                            }

                            if (!isCollapsed) {
                                for (String mobName : mobsInCat) {
                                    int itemY = currentListY;
                                    currentListY += 22;
                                    if (itemY <= listStartY + 5 || itemY >= this.height - 20) continue;
                                    BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                                    if (info == null) continue;

                                    this.addRenderableWidget(Button.builder((Component)Component.literal(this.getColorFormatting(info.color) + info.color), btn -> {
                                        colorPickerTarget = mobName + " Color";
                                        colorPickerSetter = color -> {
                                            info.color = color;
                                            BomboConfig.save();
                                        };
                                        this.init();
                                    }).bounds(contentX + contentWidth - 210, itemY + 2, 60, 18).build());

                                    this.addRenderableWidget(Button.builder((Component)Component.literal(info.tracer ? "\u00a7bTR" : "\u00a77OFF"), btn -> {
                                        info.tracer = !info.tracer;
                                        BomboConfig.save();
                                        this.init();
                                    }).bounds(contentX + contentWidth - 145, itemY + 2, 38, 18).build());

                                    String toggleLabel = info.enabled ? "\u00a7aON" : "\u00a7cOFF";
                                    this.addRenderableWidget(Button.builder((Component)Component.literal(toggleLabel), btn -> {
                                        info.enabled = !info.enabled;
                                        BomboConfig.save();
                                        this.init();
                                    }).bounds(contentX + contentWidth - 102, itemY + 2, 38, 18).build());

                                    this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7eEDIT"), b -> {
                                        highMobInput = mobName;
                                        highColorInput = info.color;
                                        highTracerInput = info.tracer;
                                        highShowInvis = info.showInvisible;
                                        highIslandInput = info.requiredIsland;
                                        editingHighMob = mobName;
                                        this.init();
                                    }).bounds(contentX + contentWidth - 60, itemY + 2, 32, 18).build());

                                    this.addRenderableWidget(Button.builder((Component)Component.literal("\u00a7cDEL"), b -> {
                                        pushHighlightHistory();
                                        s.highlights.remove(mobName);
                                        BomboConfig.save();
                                        if (editingHighMob != null && editingHighMob.equals(mobName)) {
                                            highMobInput = "";
                                            editingHighMob = null;
                                        }
                                        this.init();
                                    }).bounds(contentX + contentWidth - 25, itemY + 2, 25, 18).build());
                                }
                            }
                        }
                    }
                    break;
                }
                case 8: {
                    int i;
                    curY += 24;
                    curY = this.addBoolOption("Clear Water & Lava Vision", s.clearWaterAndLava, v -> {
                        s.clearWaterAndLava = v;
                    }, contentX, contentWidth, curY);
                    if (!s.hideCheats) {
                        curY = this.addBoolOption("Auto Close Wardrobe", s.autoCloseWardrobe, v -> {
                            s.autoCloseWardrobe = v;
                        }, contentX, contentWidth, curY);
                    }
                    curY = this.addBoolOption("Disable Unequip", s.disableUnequipWardrobe, v -> {
                        s.disableUnequipWardrobe = v;
                    }, contentX, contentWidth, curY);
                    for (i = 0; i < s.wardrobeKeys.size(); ++i) {
                        int index = i;
                        curY = this.addKeyBindButton("Slot " + (i + 1), s.wardrobeKeys.get(i), v -> s.wardrobeKeys.set(index, (String)v), "wardrobe" + i, contentX, contentWidth, curY);
                    }
                    break;
                }
                case 9: {
                    curY += 24;
                    curY = this.addBoolOption("Auto Combine", s.anvilAutoCombineEnabled, v -> {
                        s.anvilAutoCombineEnabled = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addIntLabelSlider("Delay: " + s.anvilAutoCombineDelay + "ms", s.anvilAutoCombineDelay, 50, 1000, 50, v -> {
                        s.anvilAutoCombineDelay = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Require Keybind", s.anvilAutoCombineRequireKey, v -> {
                        s.anvilAutoCombineRequireKey = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addKeyBindButton("Trigger Key", s.anvilAutoCombineKey, v -> {
                        s.anvilAutoCombineKey = v;
                    }, "anvilTrigger", contentX, contentWidth, curY);
                    int listStartY = curY += 20;
                    ArrayList<String> sortedEnchants = new ArrayList<String>(s.anvilAutoCombine.keySet());
                    Collections.sort(sortedEnchants);
                    for (int i = 0; i < sortedEnchants.size(); ++i) {
                        String enc = (String)sortedEnchants.get(i);
                        int itemY = listStartY + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY - 10 || itemY >= this.height - 50) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.anvilAutoCombine.remove(enc);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 160, itemY, 35, 18).build());
                    }
                    break;
                }
                case 10: {
                    curY = contentBaseY + 24 - (int)this.scrollAmount;
                    this.addRenderableWidget(Button.builder(Component.literal("§eDump GUI Layout Debug to File"), b -> {
                        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("bomboaddons_gui_debug.log"))) {
                            pw.println("=== BOMBOADDONS GUI DEBUG ===");
                            pw.println("selectedCategory: " + selectedCategory);
                            pw.println("scrollAmount: " + this.scrollAmount);
                            pw.println("categoryScrollAmount: " + this.categoryScrollAmount);
                            pw.println("configSearchTerm: " + configSearchTerm);
                            pw.println("width: " + this.width + ", height: " + this.height);
                            pw.println("Total widgets: " + this.children().size());
                            for (var child : this.children()) {
                                pw.println(" - Widget: " + child.getClass().getName());
                            }
                            pw.println("=============================");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        me.bombo.bomboaddons.Bomboaddons.sendMessage("§a[BomboAddons] Dumped GUI debug to bomboaddons_gui_debug.log!");
                    }).bounds(contentX, curY, 220, 20).build());
                    curY += 26;
                    curY = this.addBoolOption("MASTER DEBUG", s.debugMaster, v -> {
                        s.debugMaster = v;
                    }, contentX, contentWidth, curY);
                    int btnY = curY;
                    this.addRenderableWidget(Button.builder(Component.literal("§a[Enable All Debug]"), btn -> {
                        s.debugChat = true;
                        s.debugGuis = true;
                        s.debugEntities = true;
                        s.debugCommands = true;
                        s.debugSounds = true;
                        s.debugMaster = true;
                        s.debugParticles = true;
                        s.debugCopyChat = true;
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX, btnY, contentWidth / 2 - 5, 20).build());
                    this.addRenderableWidget(Button.builder(Component.literal("§c[Disable All Debug]"), btn -> {
                        s.debugMaster = false;
                        s.debugChat = false;
                        s.debugSounds = false;
                        s.debugGuis = false;
                        s.debugEntities = false;
                        s.debugCommands = false;
                        s.debugMode = false;
                        s.apiDebug = false;
                        s.petPriceDebug = false;
                        s.apiChatMessages = false;
                        s.lbDebug = false;
                        s.debugParticles = false;
                        s.npcLoreDebug = false;
                        s.composterDebug = false;
                        s.autoFishingDebug = false;
                        s.croesusDebug = false;
                        s.debugReconnect = false;
                        s.performanceDebug = false;
                        s.debugCopyChat = false;
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + contentWidth / 2 + 5, btnY, contentWidth / 2 - 5, 20).build());
                    curY += 28;
                    curY = this.addBoolOption("Copy Chat Debug", s.debugCopyChat, v -> s.debugCopyChat = v, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Chat Debug", s.debugChat, v -> {
                        s.debugChat = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Sounds Debug", s.debugSounds, v -> {
                        s.debugSounds = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("GUIs Debug", s.debugGuis, v -> {
                        s.debugGuis = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Entities Debug", s.debugEntities, v -> {
                        s.debugEntities = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Command Debug", s.debugCommands, v -> {
                        s.debugCommands = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Debug Mode (Legacy)", s.debugMode, v -> {
                        s.debugMode = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("API Debug", s.apiDebug, v -> {
                        s.apiDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Pet Price Debug", s.petPriceDebug, v -> {
                        s.petPriceDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("API Chat Messages", s.apiChatMessages, v -> {
                        s.apiChatMessages = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("LB Debug", s.lbDebug, v -> {
                        s.lbDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Particle Debug", s.debugParticles, v -> {
                        s.debugParticles = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("NPC Lore Debug", s.npcLoreDebug, v -> {
                        s.npcLoreDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Composter Debug", s.composterDebug, v -> {
                        s.composterDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Auto Fishing Debug", s.autoFishingDebug, v -> {
                        s.autoFishingDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Croesus Debug", s.croesusDebug, v -> {
                        s.croesusDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Auto Reconnect Debug", s.debugReconnect, v -> {
                        s.debugReconnect = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Performance Debug", s.performanceDebug, v -> {
                        s.performanceDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Keys Debug", s.debugKeys, v -> {
                        s.debugKeys = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Daily Reward Debug", s.debugDailyReward, v -> {
                        s.debugDailyReward = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Display ESP Enabled", s.displayEsp, v -> {
                        s.displayEsp = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Display Tracers", s.displayEspTracer, v -> {
                        s.displayEspTracer = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addColorCycleButton("Display Color", s.displayEspColor, v -> {
                        s.displayEspColor = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addFloatLabelSlider("Display Size", s.displayEspThickness, 0.5f, 5.0f, 0.5f, v -> {
                        s.displayEspThickness = v.floatValue();
                    }, contentX, contentWidth, curY);
                    curY += 29;
                    curY = this.addTextBox("Display ESP Filter", s.displayEspFilter, v -> {
                        s.displayEspFilter = v;
                    }, contentX, contentWidth, curY);
                    break;
                }
                case 11: {
                    curY += 24;
                    curY = this.addBoolOption("Blindness Timer", s.kuudraBlindnessTimer, v -> {
                        s.kuudraBlindnessTimer = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Disable Blindness", s.disableBlindness, v -> {
                        s.disableBlindness = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Perk Menu Clicker", s.perkMenuClicker, v -> {
                        s.perkMenuClicker = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Auto GFS Toxic", s.autoGfsToxic, v -> {
                        s.autoGfsToxic = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addIntLabelSlider("Toxic Count", s.autoGfsToxicCount, 1, 64, 1, v -> {
                        s.autoGfsToxicCount = v;
                    }, contentX, 150, curY);
                    curY = this.addBoolOption("Auto GFS Twilight", s.autoGfsTwilight, v -> {
                        s.autoGfsTwilight = v;
                    }, contentX, contentWidth, curY);
                    curY += 10;
                    curY = this.addBoolOption("Pearl Waypoints & Timers", s.pearlCalculator, v -> {
                        s.pearlCalculator = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Show Pearl Throw Timer", s.showTimer, v -> {
                        s.showTimer = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Show All Pearl Spots", s.showAll, v -> {
                        s.showAll = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Show Sky Pearl Spots", s.showSkyPearls, v -> {
                        s.showSkyPearls = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Show Flat Pearl Spots", s.showFlatPearls, v -> {
                        s.showFlatPearls = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Show Double Pearl Spots", s.showDoublePearls, v -> {
                        s.showDoublePearls = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Kuudra Debug Mode", s.kuudraDebug, v -> {
                        s.kuudraDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addIntLabelSlider("Talisman Tier (0-3)", s.kuudraTalisman, 0, 3, 1, v -> {
                        s.kuudraTalisman = v;
                    }, contentX, 150, curY);
                    break;
                }
                case 12: {
                    int i;
                    curY += 24;
                    curY = this.addBoolOption("Show Pet Lowest BIN", s.showPetLowestBin, v -> {
                        s.showPetLowestBin = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Disable Unequip", s.disableUnequipPet, v -> {
                        s.disableUnequipPet = v;
                    }, contentX, contentWidth, curY);
                    for (i = 0; i < 9; ++i) {
                        int index = i;
                        boolean listening = listeningForKeyTarget.equals("pets" + index);
                        String currentKey = s.petKeys.get(index);
                        String displayKey = ClickLogic.getKeyDisplayName(currentKey);
                        String keyText = listening ? "\u00a7e[PRESS]" : (currentKey.isEmpty() ? "None" : displayKey);
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)keyText), btn -> {
                            listeningForKeyTarget = "pets" + index;
                            this.init();
                        }).bounds(contentX + 110, curY, 80, 18).build());
                        Button upBtn = Button.builder((Component)Component.literal((String)"\u25b2"), btn -> {
                            this.swapPetSlots(index, index - 1);
                            this.init();
                        }).bounds(contentX + 195, curY, 20, 18).build();
                        if (index == 0) {
                            upBtn.active = false;
                        }
                        this.addRenderableWidget(upBtn);
                        Button downBtn = Button.builder((Component)Component.literal((String)"\u25bc"), btn -> {
                            this.swapPetSlots(index, index + 1);
                            this.init();
                        }).bounds(contentX + 218, curY, 20, 18).build();
                        if (index == 8) {
                            downBtn.active = false;
                        }
                        this.addRenderableWidget(downBtn);
                        Button delBtn = Button.builder((Component)Component.literal((String)"\u2715"), btn -> {
                            this.clearPetSlot(index);
                            this.init();
                        }).bounds(contentX + 241, curY, 20, 18).build();
                        String uuid = s.petKeybinds.get(String.valueOf(index + 1));
                        if (uuid == null || uuid.isEmpty()) {
                            delBtn.active = false;
                        }
                        this.addRenderableWidget(delBtn);
                        curY += 24;
                    }
                    break;
                }
                case 13: {
                    curY += 24;
                    ArrayList<String> profiles = new ArrayList<String>(s.profileBinds.keySet());
                    if (!profiles.contains("default")) {
                        profiles.add(0, "default");
                    }
                    int currentIdx = profiles.indexOf(s.activeProfile);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 150, curY, 20, 20).build());
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 175, curY, 20, 20).build());
                    curY += 29;
                    curY = this.addTextBox("Command", bindCommandInput, v -> {
                        bindCommandInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addComboBindButton("Combo", bindComboInput, v -> {
                        bindComboInput = v;
                    }, "profileCombo", contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Only on Island", bindIslandInput, v -> {
                        bindIslandInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Only with Armor", bindArmorInput, v -> {
                        bindArmorInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY += 5;
                    String addBindText = editingKeybindIdx != -1 ? "\u00a7e\u2714 Save Bind" : "\u00a7a+ Add Bind";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBindText), btn -> {
                        List<Integer> codes;
                        if (!(bindCommandInput.isEmpty() || bindComboInput.isEmpty() || (codes = this.parseCombo(bindComboInput)).isEmpty())) {
                            s.keybindBinds.putIfAbsent(s.activeProfile, new ArrayList());
                            if (editingKeybindIdx != -1) {
                                s.keybindBinds.get(s.activeProfile).set(editingKeybindIdx, new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput, bindIslandInput, bindArmorInput));
                                editingKeybindIdx = -1;
                            } else {
                                s.keybindBinds.get(s.activeProfile).add(new BomboConfig.CommandBind(bindCommandInput, codes, bindComboInput, bindIslandInput, bindArmorInput));
                            }
                            BomboConfig.save();
                            bindCommandInput = "";
                            bindComboInput = "";
                            bindIslandInput = "";
                            bindArmorInput = "";
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingKeybindIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingKeybindIdx = -1;
                            bindCommandInput = "";
                            bindComboInput = "";
                            bindIslandInput = "";
                            bindArmorInput = "";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    List<BomboConfig.CommandBind> binds = s.keybindBinds.get(s.activeProfile);
                    if (binds == null) break;
                    for (int i = 0; i < binds.size(); ++i) {
                        int idx = i;
                        BomboConfig.CommandBind bind = binds.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        String toggleLabel = bind.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            bind.enabled = !bind.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingKeybindIdx = idx;
                            bindCommandInput = bind.command;
                            bindComboInput = bind.keyName;
                            bindIslandInput = bind.requiredIsland != null ? bind.requiredIsland : "";
                            bindArmorInput = bind.requiredArmor != null ? bind.requiredArmor : "";
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            binds.remove(idx);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 14: {
                    curY += 24;
                    ArrayList<String> profiles = new ArrayList<String>(s.profileBinds.keySet());
                    if (!profiles.contains("default")) {
                        profiles.add(0, "default");
                    }
                    int currentIdx = profiles.indexOf(s.activeProfile);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 150, curY, 20, 20).build());
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 175, curY, 20, 20).build());
                    curY += 29;
                    curY = this.addTextBox("Name", wpNameInput, v -> {
                        wpNameInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Coords (X Y Z)", wpCoordsInput, v -> {
                        wpCoordsInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Only on Island", wpIslandInput, v -> {
                        wpIslandInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Show Through Walls", wpThruWallsInput, v -> {
                        wpThruWallsInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Show Beacon", wpBeaconInput, v -> {
                        wpBeaconInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addColorCycleButton("Color", wpColorInput, v -> {
                        wpColorInput = v;
                    }, contentX, contentWidth, curY);
                    String addBtnText = editingWaypointIdx != -1 ? "\u00a7e\u2714 Save Waypoint" : "\u00a7a+ Add Waypoint";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        double[] parsed;
                        if (!wpNameInput.isEmpty() && !wpCoordsInput.isEmpty() && (parsed = BomboConfigGUI.parseCoords(wpCoordsInput)) != null) {
                            s.customWaypoints.putIfAbsent(s.activeProfile, new ArrayList());
                            BomboConfig.CustomWaypoint cwp = new BomboConfig.CustomWaypoint(wpNameInput, parsed[0], parsed[1], parsed[2], wpIslandInput, wpThruWallsInput, wpBeaconInput, wpColorInput);
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
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingWaypointIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingWaypointIdx = -1;
                            wpNameInput = "";
                            wpCoordsInput = "";
                            wpIslandInput = "";
                            wpThruWallsInput = true;
                            wpBeaconInput = true;
                            wpColorInput = "AQUA";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    List<BomboConfig.CustomWaypoint> wps = s.customWaypoints.get(s.activeProfile);
                    if (wps == null) break;
                    for (int i = 0; i < wps.size(); ++i) {
                        int idx = i;
                        BomboConfig.CustomWaypoint wp = wps.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        String toggleLabel = wp.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            wp.enabled = !wp.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingWaypointIdx = idx;
                            wpNameInput = wp.name;
                            wpCoordsInput = String.format("%.2f %.2f %.2f", wp.x, wp.y, wp.z);
                            wpIslandInput = wp.requiredIsland != null ? wp.requiredIsland : "";
                            wpThruWallsInput = wp.showThroughWalls;
                            wpBeaconInput = wp.showBeacon;
                            wpColorInput = wp.color != null ? wp.color : "AQUA";
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            wps.remove(idx);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 15: {
                    curY += 24;
                    curY = this.addTextBox("Alias", aliasCommandInput, v -> {
                        aliasCommandInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addTextBox("Command", aliasActualInput, v -> {
                        aliasActualInput = v;
                    }, contentX, contentWidth, curY);
                    String addBtnText = editingAliasKey != null ? "\u00a7e\u2714 Save Alias" : "\u00a7a+ Add Alias";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!aliasCommandInput.isEmpty() && !aliasActualInput.isEmpty()) {
                            String cleanAlias;
                            String string = cleanAlias = aliasCommandInput.startsWith("/") ? aliasCommandInput.substring(1) : aliasCommandInput;
                            if (editingAliasKey != null) {
                                s.commandAliases.remove(editingAliasKey);
                                editingAliasKey = null;
                            }
                            s.commandAliases.put(cleanAlias, aliasActualInput);
                            BomboConfig.save();
                            BomboaddonsClient.registerAllAliases();
                            aliasCommandInput = "";
                            aliasActualInput = "";
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingAliasKey != null) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingAliasKey = null;
                            aliasCommandInput = "";
                            aliasActualInput = "";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    ArrayList<String> sortedAliases = new ArrayList<String>(s.commandAliases.keySet());
                    Collections.sort(sortedAliases);
                    for (int i = 0; i < sortedAliases.size(); ++i) {
                        int idx = i;
                        String aliasKey = (String)sortedAliases.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingAliasKey = aliasKey;
                            aliasCommandInput = aliasKey;
                            aliasActualInput = s.commandAliases.get(aliasKey);
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.commandAliases.remove(aliasKey);
                            BomboConfig.save();
                            BomboaddonsClient.registerAllAliases();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 16: {
                    curY += 24;
                    ArrayList<String> profiles = new ArrayList<String>(s.profileBinds.keySet());
                    if (!profiles.contains("default")) {
                        profiles.add(0, "default");
                    }
                    int currentIdx = profiles.indexOf(s.activeProfile);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 150, curY, 20, 20).build());
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 175, curY, 20, 20).build());
                    curY += 24;
                    curY = this.addTextBox("If Chat Contains", triggerTextInput, v -> {
                        triggerTextInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addTextBox("Run Command", triggerCommandInput, v -> {
                        triggerCommandInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addTextBox("Show Title", triggerTitleInput, v -> {
                        triggerTitleInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addSoundTextBox("Play Sound (TAB)", triggerSoundInput, v -> {
                        triggerSoundInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addIntLabelSlider("Sound Times: " + triggerSoundTimesInput, triggerSoundTimesInput, 1, 10, 1, v -> {
                        triggerSoundTimesInput = v;
                    }, contentX, contentWidth, curY);
                    List triggers = s.profileChatTriggers.computeIfAbsent(s.activeProfile, k -> new ArrayList());
                    int finalCurY = curY + 6;
                    String addBtnText = editingTriggerIdx != -1 ? "\u00a7e\u2714 Save Trigger" : "\u00a7a+ Add Trigger";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!(triggerTextInput.isEmpty() || (triggerCommandInput.isEmpty() && triggerTitleInput.isEmpty() && triggerSoundInput.isEmpty()))) {
                            BomboConfig.ChatTrigger ct = new BomboConfig.ChatTrigger(triggerTextInput, triggerCommandInput, triggerTitleInput, triggerSoundInput, triggerSoundTimesInput);
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
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingTriggerIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingTriggerIdx = -1;
                            triggerTextInput = "";
                            triggerCommandInput = "";
                            triggerTitleInput = "";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    for (int i = 0; i < triggers.size(); ++i) {
                        int idx = i;
                        BomboConfig.ChatTrigger trigger = (BomboConfig.ChatTrigger)triggers.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        String toggleLabel = trigger.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            trigger.enabled = !trigger.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingTriggerIdx = idx;
                            triggerTextInput = trigger.triggerText;
                            triggerCommandInput = trigger.commandToRun;
                            triggerTitleInput = trigger.titleToShow;
                            triggerSoundInput = trigger.soundToPlay != null ? trigger.soundToPlay : "";
                            triggerSoundTimesInput = trigger.soundTimes > 0 ? trigger.soundTimes : 1;
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            triggers.remove(idx);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 17: {
                    curY += 24;
                    curY = this.addBoolOption("Croesus Helper", s.croesusHelper, v -> {
                        s.croesusHelper = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Dungeon Secrets Tracker", s.dungeonSecretsTracker, v -> {
                        s.dungeonSecretsTracker = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Dungeon Secrets Debug", s.dungeonSecretsDebug, v -> {
                        s.dungeonSecretsDebug = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Clear Info HUD", s.clearInfoHud, v -> {
                        s.clearInfoHud = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Pad Timers Purple", s.padTimersPurple, v -> {
                        s.padTimersPurple = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Pad Timers Green", s.padTimersGreen, v -> {
                        s.padTimersGreen = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Dungeon Big Hitbox", s.dungeonBigHitbox, v -> {
                        s.dungeonBigHitbox = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addFloatLabelSlider("Purple Timer (s)", (float)s.padTimerPurpleTime, 1.0f, 10.0f, 0.1f, v -> {
                        s.padTimerPurpleTime = v.floatValue();
                    }, contentX, 150, curY);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7e\u00a7lMove HUD Elements"), btn -> Minecraft.getInstance().setScreenAndShow((Screen)new HudMoveScreen())).bounds(contentX, curY += 10, contentWidth / 2, 20).build());
                    curY += 30;
                    break;
                }
                case 18: {
                    curY += 24;
                    ArrayList<String> profiles = new ArrayList<String>(s.profileBinds.keySet());
                    if (!profiles.contains("default")) {
                        profiles.add(0, "default");
                    }
                    int currentIdx = profiles.indexOf(s.activeProfile);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"<"), btn -> {
                        int next = (currentIdx - 1 + profiles.size()) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 150, curY, 20, 20).build());
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)">"), btn -> {
                        int next = (currentIdx + 1) % profiles.size();
                        s.activeProfile = (String)profiles.get(next);
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX + 175, curY, 20, 20).build());
                    curY += 29;
                    curY = this.addTextBox("Coords (X Y Z)", cbCoordsInput, v -> {
                        cbCoordsInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Command", cbCommandInput, v -> {
                        cbCommandInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Only on Island", cbIslandInput, v -> {
                        cbIslandInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Radius", cbRadiusInput, v -> {
                        cbRadiusInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Show Waypoint", cbShowWaypointInput, v -> {
                        cbShowWaypointInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addTextBox("Min Delay (s)", cbMinDelayInput, v -> {
                        cbMinDelayInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Max Delay (s)", cbMaxDelayInput, v -> {
                        cbMaxDelayInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY += 5;
                    String addBtnText = editingCoordBindIdx != -1 ? "\u00a7e\u2714 Save Coord Bind" : "\u00a7a+ Add Coord Bind";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!cbCommandInput.isEmpty()) {
                            Object parsed;
                            if (cbCoordsInput.trim().isEmpty()) {
                                Minecraft mc = Minecraft.getInstance();
                                parsed = mc.player != null ? new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()} : null;
                            } else {
                                parsed = BomboConfigGUI.parseCoords(cbCoordsInput);
                            }
                            if (parsed != null) {
                                double radiusVal = 3.0;
                                if (!cbRadiusInput.trim().isEmpty()) {
                                    try {
                                        radiusVal = Double.parseDouble(cbRadiusInput.trim());
                                    }
                                    catch (NumberFormatException numberFormatException) {
                                        // empty catch block
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
                                }
                                catch (NumberFormatException numberFormatException) {
                                    // empty catch block
                                }
                                s.coordBinds.putIfAbsent(s.activeProfile, new ArrayList());
                                double[] pArr = (double[])(Object)parsed;
                                BomboConfig.CoordBind cb = new BomboConfig.CoordBind(cbCommandInput, pArr[0], pArr[1], pArr[2], cbIslandInput, radiusVal, cbShowWaypointInput, minDelayVal, maxDelayVal);
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
                                this.init();
                            }
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingCoordBindIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingCoordBindIdx = -1;
                            cbCoordsInput = "";
                            cbCommandInput = "";
                            cbIslandInput = "";
                            cbRadiusInput = "3";
                            cbShowWaypointInput = false;
                            cbMinDelayInput = "0";
                            cbMaxDelayInput = "0";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 60, 20).build());
                    }
                    int listStartY = curY += 35;
                    List<BomboConfig.CoordBind> binds = s.coordBinds.get(s.activeProfile);
                    if (binds == null) break;
                    for (int i = 0; i < binds.size(); ++i) {
                        int idx = i;
                        BomboConfig.CoordBind cb = binds.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        String toggleLabel = cb.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            cb.enabled = !cb.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingCoordBindIdx = idx;
                            cbCoordsInput = String.format("%.2f %.2f %.2f", cb.x, cb.y, cb.z);
                            cbCommandInput = cb.command;
                            cbIslandInput = cb.requiredIsland != null ? cb.requiredIsland : "";
                            cbRadiusInput = String.valueOf(cb.radius <= 0.0 ? 3.0 : cb.radius);
                            cbShowWaypointInput = cb.showWaypoint;
                            cbMinDelayInput = String.valueOf(cb.minDelay);
                            cbMaxDelayInput = String.valueOf(cb.maxDelay);
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            binds.remove(idx);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 19: {
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;

                    if (structureFinderSubmenu) {
                        this.addRenderableWidget(Button.builder(Component.literal("§c← Back to Mining"), b -> { structureFinderSubmenu = false; this.scrollAmount = 0.0; this.init(); }).bounds(col1X, y1, 140, 20).build());
                        y1 += 28;
                        y1 = this.addBoolOption("Corleone 1", s.structureFinderCorleone1, v -> s.structureFinderCorleone1 = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Golden Dragon", s.structureFinderGoldenDragon, v -> s.structureFinderGoldenDragon = v, col1X, col1W * 2, y1);
                        y1 = this.addBoolOption("Structure Tracers", s.structureFinderTracers, v -> s.structureFinderTracers = v, col1X, col1W * 2, y1);
                        y1 = this.addColorCycleButton("Structure Color", s.structureFinderColor, v -> s.structureFinderColor = v, col1X, col1W * 2, y1);
                        y1 = this.addIntLabelSlider("Scan Radius: " + s.structureFinderRadius + " blocks", s.structureFinderRadius, 32, 512, 16, v -> s.structureFinderRadius = v, col1X, col1W * 2, y1);
                        break;
                    }

                    y1 = this.addBoolOption("Corpse ESP Enabled", s.corpseEsp, v -> {
                        s.corpseEsp = v;
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("Hide Opened Corpses", s.hideOpenedCorpses, v -> {
                        s.hideOpenedCorpses = v;
                    }, col1X, col1W, y1);
                    String[] styleNames = new String[]{"Outline", "Filled", "Both"};
                    String styleLabel = "ESP Style: " + s.corpseEspStyle;
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)styleLabel), btn -> {
                        int currentIdx = 0;
                        if ("Filled".equals(s.corpseEspStyle)) {
                            currentIdx = 1;
                        } else if ("Both".equals(s.corpseEspStyle)) {
                            currentIdx = 2;
                        }
                        int nextIdx = (currentIdx + 1) % 3;
                        s.corpseEspStyle = styleNames[nextIdx];
                        BomboConfig.save();
                        this.init();
                    }).bounds(col1X, y1 += 5, col1W, 20).build());
                    y1 += 29;
                    y1 = this.addBoolOption("Golden Dragon Nest Finder", s.goldenDragonNestFinder, v -> {
                        s.goldenDragonNestFinder = v;
                    }, col1X, col1W, y1);

                    if (!s.hideCheats) {
                        structureFinderX = col1X;
                        structureFinderY = y1;
                        structureFinderW = col1W;
                        structureFinderH = 24;
                        y1 = this.addBoolOption("Structure Finder (Right Click)", s.structureFinder, v -> s.structureFinder = v, col1X, col1W, y1);
                    } else {
                        structureFinderX = -1;
                        structureFinderY = -1;
                        structureFinderW = -1;
                        structureFinderH = -1;
                    }

                    y1 = this.addBoolOption("Dwarven Red Carpets", s.replaceGrayCarpetDwarven, v -> {
                        s.replaceGrayCarpetDwarven = v;
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.levelRenderer != null) {
                            mc.levelRenderer.allChanged();
                        }
                    }, col1X, col1W, y1);

                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.addColorCycleButton("Lapis Outline Color", s.lapisOutlineColor, v -> {
                        s.lapisOutlineColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Lapis Fill Color", s.lapisFillColor, v -> {
                        s.lapisFillColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Tungsten Outline Color", s.tungstenOutlineColor, v -> {
                        s.tungstenOutlineColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Tungsten Fill Color", s.tungstenFillColor, v -> {
                        s.tungstenFillColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Umber Outline Color", s.umberOutlineColor, v -> {
                        s.umberOutlineColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Umber Fill Color", s.umberFillColor, v -> {
                        s.umberFillColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Vanguard Outline Color", s.vanguardOutlineColor, v -> {
                        s.vanguardOutlineColor = v;
                    }, col2X, col2W, y2);
                    y2 = this.addColorCycleButton("Vanguard Fill Color", s.vanguardFillColor, v -> {
                        s.vanguardFillColor = v;
                    }, col2X, col2W, y2);
                    break;
                }
                case 20: {
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + 24;
                    y1 = this.addBoolOption("!timer command", s.partyCommandTimer, v -> {
                        s.partyCommandTimer = v;
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("!warp command", s.partyCommandWarp, v -> {
                        s.partyCommandWarp = v;
                    }, col1X, col1W, y1);
                    y1 = this.addBoolOption("!psa command", s.partyCommandPsa, v -> {
                        s.partyCommandPsa = v;
                    }, col1X, col1W, y1);
                    y1 += 10;
                    y1 = this.addTextBox("Prefixes (e.g. !,.,?)", s.partyCommandPrefixes, v -> {
                        s.partyCommandPrefixes = v;
                        BomboConfig.save();
                    }, col1X, col1W, y1);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7e\u2190 Back to General"), btn -> {
                        selectedCategory = 0;
                        this.init();
                    }).bounds(col1X, y1 += 20, 150, 20).build());
                    int col2X = contentX + contentWidth / 2 + 10;
                    int col2W = contentWidth / 2 - 10;
                    int y2 = contentBaseY + 24;
                    y2 = this.addTextBox("Trigger", customPartyTriggerInput, v -> {
                        customPartyTriggerInput = v;
                    }, col2X, col2W, y2);
                    int finalY2 = y2 = this.addTextBox("Command to run", customPartyCommandInput, v -> {
                        customPartyCommandInput = v;
                    }, col2X, col2W, y2);
                    String addBtnText = editingCustomPartyIdx != -1 ? "\u00a7e\u2714 Save Command" : "\u00a7a+ Add Command";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!customPartyTriggerInput.isEmpty() && !customPartyCommandInput.isEmpty()) {
                            String trig = customPartyTriggerInput.trim();
                            while (trig.startsWith("!") || trig.startsWith(".") || trig.startsWith("?")) {
                                trig = trig.substring(1);
                            }
                            trig = trig.toLowerCase();
                            BomboConfig.CustomPartyCommand cpc = new BomboConfig.CustomPartyCommand(trig, customPartyCommandInput.trim(), true);
                            if (editingCustomPartyIdx != -1) {
                                s.customPartyCommands.set(editingCustomPartyIdx, cpc);
                                editingCustomPartyIdx = -1;
                            } else {
                                s.customPartyCommands.add(cpc);
                            }
                            BomboConfig.save();
                            customPartyTriggerInput = "";
                            customPartyCommandInput = "";
                            this.init();
                        }
                    }).bounds(col2X, finalY2, col2W - 55, 20).build());
                    if (editingCustomPartyIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel"), btn -> {
                            editingCustomPartyIdx = -1;
                            customPartyTriggerInput = "";
                            customPartyCommandInput = "";
                            this.init();
                        }).bounds(col2X + col2W - 50, finalY2, 50, 20).build());
                    }
                    int listStartY = y2 += 25;
                    for (int i = 0; i < s.customPartyCommands.size(); ++i) {
                        int idx = i;
                        BomboConfig.CustomPartyCommand cmd = s.customPartyCommands.get(idx);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        String toggleLabel = cmd.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            cmd.enabled = !cmd.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(col2X + col2W - 120, itemY + 5, 35, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingCustomPartyIdx = idx;
                            customPartyTriggerInput = cmd.triggerText;
                            customPartyCommandInput = cmd.commandToRun;
                            this.init();
                        }).bounds(col2X + col2W - 80, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.customPartyCommands.remove(idx);
                            BomboConfig.save();
                            this.init();
                        }).bounds(col2X + col2W - 35, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 21: {
                    curY += 24;
                    curY = this.addBoolOption("Block Highlights Enabled", s.blockHighlightsEnabled, v -> {
                        s.blockHighlightsEnabled = v;
                        if (!v.booleanValue()) {
                           BlockHighlight.highlightedBlocks.clear();
                        }
                    }, contentX, contentWidth, curY);
                    curY = this.addIntLabelSlider("Block Scan Radius: " + s.blockScanRadius + " blocks", s.blockScanRadius, 4, 512, 4, v -> {
                        s.blockScanRadius = v;
                        BlockHighlight.highlightedBlocks.clear();
                    }, contentX, contentWidth, curY);
                    curY += 10;
                    curY = this.addTextBox("Block Name/ID", blockNameInput, v -> {
                        blockNameInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addColorCycleButton("Color", blockColorInput, v -> {
                        blockColorInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addBoolOption("See Through Walls", blockThroughWallsInput, v -> {
                        blockThroughWallsInput = v;
                    }, contentX, contentWidth, curY);
                    String addBtnText = editingBlockName != null ? "\u00a7e\u2714 Save Block Highlight" : "\u00a7a+ Add Block Highlight";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!blockNameInput.isEmpty()) {
                            if (editingBlockName != null) {
                                s.blockHighlights.remove(editingBlockName);
                            }
                            s.blockHighlights.put(blockNameInput.toLowerCase(), new BomboConfig.BlockHighlightInfo(blockColorInput.toUpperCase(), blockThroughWallsInput));
                            BomboConfig.save();
                            blockNameInput = "";
                            blockColorInput = "GOLD";
                            blockThroughWallsInput = true;
                            editingBlockName = null;
                            BlockHighlight.highlightedBlocks.clear();
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingBlockName != null) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel Edit"), btn -> {
                            blockNameInput = "";
                            blockColorInput = "GOLD";
                            blockThroughWallsInput = true;
                            editingBlockName = null;
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 80, 20).build());
                    }
                    int listStartY = curY += 35;
                    ArrayList<String> sortedBlocks = new ArrayList<String>(s.blockHighlights.keySet());
                    Collections.sort(sortedBlocks);
                    for (int i = 0; i < sortedBlocks.size(); ++i) {
                        String bName = (String)sortedBlocks.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        BomboConfig.BlockHighlightInfo info = s.blockHighlights.get(bName);
                        String toggleLabel = info.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            info.enabled = !info.enabled;
                            BomboConfig.save();
                            BlockHighlight.highlightedBlocks.clear();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingBlockName = bName;
                            blockNameInput = bName;
                            blockColorInput = info.color;
                            blockThroughWallsInput = info.throughWalls;
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.blockHighlights.remove(bName);
                            BomboConfig.save();
                            BlockHighlight.highlightedBlocks.clear();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 22: {
                    curY += 24;
                    curY = this.addBoolOption("Particle Highlights Enabled", s.particleHighlightsEnabled, v -> {
                        s.particleHighlightsEnabled = v;
                    }, contentX, contentWidth, curY);
                    curY += 10;
                    curY = this.addTextBox("Particle Name", partHighInput, v -> {
                        partHighInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addColorCycleButton("Color", partHighColorInput, v -> {
                        partHighColorInput = v;
                    }, contentX, contentWidth, curY);
                    String addBtnText = editingPartHigh != null ? "\u00a7e\u2714 Save Particle Highlight" : "\u00a7a+ Add Particle Highlight";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!partHighInput.isEmpty()) {
                            if (editingPartHigh != null) {
                                s.particleHighlights.remove(editingPartHigh);
                            }
                            s.particleHighlights.put(partHighInput.toLowerCase(), new BomboConfig.HighlightInfo(partHighColorInput.toUpperCase(), true));
                            BomboConfig.save();
                            partHighInput = "";
                            partHighColorInput = "GOLD";
                            editingPartHigh = null;
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingPartHigh != null) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel Edit"), btn -> {
                            partHighInput = "";
                            partHighColorInput = "GOLD";
                            editingPartHigh = null;
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 80, 20).build());
                    }
                    int listStartY = curY += 35;
                    ArrayList<String> sortedParticles = new ArrayList<String>(s.particleHighlights.keySet());
                    Collections.sort(sortedParticles);
                    for (int i = 0; i < sortedParticles.size(); ++i) {
                        String pName = (String)sortedParticles.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        BomboConfig.HighlightInfo info = s.particleHighlights.get(pName);
                        String toggleLabel = info.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            info.enabled = !info.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingPartHigh = pName;
                            partHighInput = pName;
                            partHighColorInput = info.color;
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.particleHighlights.remove(pName);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 23: {
                    curY += 24;
                    curY = this.addBoolOption("Bedwars ESP Enabled", s.bedwarsEsp, v -> {
                        s.bedwarsEsp = v;
                        BomboConfig.save();
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Highlight Own Team", s.bedwarsEspOwnTeam, v -> {
                        s.bedwarsEspOwnTeam = v;
                        BomboConfig.save();
                    }, contentX, contentWidth, curY);
                    break;
                }
                case 24: {
                    curY = contentBaseY + 24 - (int)this.scrollAmount;
                    curY = this.addBoolOption("Auto Fishing Enabled", s.autoFishingEnabled, v -> {
                        s.autoFishingEnabled = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Show Bobber Time", s.showBobberTime, v -> {
                        s.showBobberTime = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addIntLabelSlider("Min Delay (ms)", s.autoFishingMinDelay, 0, 2000, 5, v -> {
                        s.autoFishingMinDelay = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addIntLabelSlider("Max Delay (ms)", s.autoFishingMaxDelay, 0, 2000, 5, v -> {
                        s.autoFishingMaxDelay = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Slug Mode Enabled", s.autoFishingSlugMode, v -> {
                        s.autoFishingSlugMode = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addFloatLabelSlider("Slug Min Bobber Time (s)", s.autoFishingSlugDelay, 1.0f, 30.0f, 0.5f, v -> {
                        s.autoFishingSlugDelay = v.floatValue();
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Trophy Fish Highlight", s.trophyHighlight, v -> {
                        s.trophyHighlight = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Swap on Catch Enabled", s.autoFishingSwapEnabled, v -> {
                        s.autoFishingSwapEnabled = v;
                    }, contentX, contentWidth, curY);
                    if (s.autoFishingSwapEnabled) {
                        curY += 5;
                        curY = this.addTextBox("Weapon Name / Slot", s.autoFishingWeaponName, v -> {
                            s.autoFishingWeaponName = v;
                        }, contentX, contentWidth, curY);
                        curY += 5;
                        curY = this.addIntLabelSlider("Attack Click Count", s.autoFishingClickCount, 1, 20, 1, v -> {
                            s.autoFishingClickCount = v;
                        }, contentX, contentWidth, curY);
                        curY += 5;
                        String currentClickType = s.autoFishingClickType == 1 ? "Left Click" : "Right Click";
                        curY = this.addCycleOption("Attack Click Type", currentClickType, List.of("Right Click", "Left Click"), v -> {
                            s.autoFishingClickType = v.equalsIgnoreCase("Left Click") ? 1 : 0;
                        }, contentX, contentWidth, curY);
                        curY += 5;
                        curY = this.addIntLabelSlider("Min Attack Click Delay (ms)", s.autoFishingClickDelayMin, 20, 500, 5, v -> {
                            s.autoFishingClickDelayMin = v;
                        }, contentX, contentWidth, curY);
                        curY += 5;
                        curY = this.addIntLabelSlider("Max Attack Click Delay (ms)", s.autoFishingClickDelayMax, 20, 500, 5, v -> {
                            s.autoFishingClickDelayMax = v;
                        }, contentX, contentWidth, curY);
                    }
                    curY += 5;
                    curY = this.addTextBox("Stop Chat Trigger", s.autoFishingStopChatMessage, v -> {
                        s.autoFishingStopChatMessage = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Auto Fishing Debug Logs", s.autoFishingDebug, v -> {
                        s.autoFishingDebug = v;
                    }, contentX, contentWidth, curY);
                    break;
                }
                case 25: {
                    String[] presetNames;
                    BomboConfig.CrosshairSettings crosshair = s.customCrosshair;
                    curY += 24;
                    curY = this.addBoolOption("Enable Custom Crosshair", crosshair.enabled, v -> {
                        crosshair.enabled = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addBoolOption("Chroma", crosshair.chroma, v -> {
                        crosshair.chroma = v;
                    }, contentX, contentWidth, curY);
                    if (!crosshair.chroma) {
                        curY = this.addColorCycleButton("Color", crosshair.color, v -> {
                            crosshair.color = v;
                        }, contentX, contentWidth, curY);
                    }
                    curY = this.addBoolOption("Outline", crosshair.outline, v -> {
                        crosshair.outline = v;
                    }, contentX, contentWidth, curY);
                    if (crosshair.outline) {
                        curY = this.addColorCycleButton("Outline Color", crosshair.outlineColor, v -> {
                            crosshair.outlineColor = v;
                        }, contentX, contentWidth, curY);
                    }
                    curY += 5;
                    curY += 29;
                    int btnW = 45;
                    int px = contentX;
                    int startX = contentX;
                    int count = 0;
                    for (String name : presetNames = new String[]{"Dot", "Plus", "Lg Plus", "Sm Plus", "Circle", "Op Circle", "Square", "F Square", "Target", "F Target", "Arrow", "Cross", "Sm Cross", "T Shape", "Caret", "Hash", "Clear"}) {
                        if (count > 0 && count % 6 == 0) {
                            curY += 25;
                            px = startX;
                        }
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)""), btn -> {
                            System.arraycopy(this.getPresetGrid(name), 0, crosshair.grid, 0, 225);
                            BomboConfig.save();
                            this.init();
                        }).bounds(px, curY, btnW, 20).build());
                        px += btnW + 5;
                        ++count;
                    }
                    curY += 25;
                    break;
                }
                case 26: {
                    curY += 24;
                    curY = this.addTextBox("GUI Name", csGuiNameInput, v -> {
                        csGuiNameInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Slot Index", csSlotIndexInput, v -> {
                        csSlotIndexInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Icon (e.g. minecraft:barrier)", csIconInput, v -> {
                        csIconInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Name", csNameInput, v -> {
                        csNameInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Description", csDescInput, v -> {
                        csDescInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Command", csCommandInput, v -> {
                        csCommandInput = v;
                    }, contentX, contentWidth, curY);
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)(editingCustomSlotIdx == -1 ? "Add Custom Slot" : "Save Changes")), btn -> {
                        int sIdx;
                        if (csGuiNameInput.isEmpty() || csSlotIndexInput.isEmpty()) {
                            return;
                        }
                        try {
                            sIdx = Integer.parseInt(csSlotIndexInput);
                        }
                        catch (Exception e) {
                            return;
                        }
                        BomboConfig.CustomSlot cs = new BomboConfig.CustomSlot(csGuiNameInput, sIdx, csIconInput, csNameInput, csDescInput, csCommandInput);
                        if (s.customSlots == null) {
                            s.customSlots = new ArrayList<BomboConfig.CustomSlot>();
                        }
                        if (editingCustomSlotIdx != -1) {
                            s.customSlots.set(editingCustomSlotIdx, cs);
                        } else {
                            s.customSlots.add(0, cs);
                        }
                        editingCustomSlotIdx = -1;
                        csGuiNameInput = "";
                        csSlotIndexInput = "";
                        csIconInput = "minecraft:barrier";
                        csNameInput = "";
                        csDescInput = "";
                        csCommandInput = "";
                        BomboConfig.save();
                        this.init();
                    }).bounds(contentX, curY += 5, contentWidth / 2 - 5, 20).build());
                    if (editingCustomSlotIdx != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"Cancel Edit"), btn -> {
                            editingCustomSlotIdx = -1;
                            csGuiNameInput = "";
                            csSlotIndexInput = "";
                            csIconInput = "minecraft:barrier";
                            csNameInput = "";
                            csDescInput = "";
                            csCommandInput = "";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, curY, contentWidth / 2 - 5, 20).build());
                    }
                    curY += 30;
                    if (s.customSlots == null || s.customSlots.isEmpty()) break;
                    int listStartY = curY;
                    for (int i = 0; i < s.customSlots.size(); ++i) {
                        int idx = i;
                        BomboConfig.CustomSlot cs = s.customSlots.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingCustomSlotIdx = idx;
                            csGuiNameInput = cs.guiName;
                            csSlotIndexInput = String.valueOf(cs.slotIndex);
                            csIconInput = cs.icon != null ? cs.icon : "minecraft:barrier";
                            csNameInput = cs.name != null ? cs.name : "";
                            csDescInput = cs.description != null ? cs.description : "";
                            csCommandInput = cs.command != null ? cs.command : "";
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.customSlots.remove(idx);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 27: {
                    curY += 24;
                    curY = this.addTextBox("ID/UUID", newTracerIdInput, v -> {
                        newTracerIdInput = v;
                    }, contentX, contentWidth, curY);
                    curY += 5;
                    curY = this.addTextBox("Name", newTracerNameInput, v -> {
                        newTracerNameInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurYAdd = curY += 5;
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7a+ Add Tracer"), btn -> {
                        try {
                            String id = newTracerIdInput.trim();
                            if (!id.isEmpty()) {
                                s.customTracers.put(id, new BomboConfig.Settings.CustomTracerInfo(newTracerNameInput, "GREEN"));
                                newTracerIdInput = "";
                                newTracerNameInput = "";
                                BomboConfig.save();
                                this.init();
                            }
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }).bounds(contentX, finalCurYAdd, contentWidth, 20).build());
                    curY += 30;
                    if (editingCustomTracer != null) {
                        curY = this.addTextBox("ID/UUID", customTracerIdInput, v -> {
                            customTracerIdInput = v;
                        }, contentX, contentWidth, curY);
                        curY += 5;
                        int finalCurY = curY = this.addColorCycleButton("Color", customTracerColorInput, v -> {
                            customTracerColorInput = v;
                        }, contentX, contentWidth, curY);
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7e\u2714 Save Tracer"), btn -> {
                            if (s.customTracers.containsKey(editingCustomTracer)) {
                                BomboConfig.Settings.CustomTracerInfo info = s.customTracers.get(editingCustomTracer);
                                info.color = customTracerColorInput;
                                if (!editingCustomTracer.equals(customTracerIdInput)) {
                                    s.customTracers.remove(editingCustomTracer);
                                    s.customTracers.put(customTracerIdInput, info);
                                }
                                BomboConfig.save();
                            }
                            editingCustomTracer = null;
                            customTracerColorInput = "GREEN";
                            this.init();
                        }).bounds(contentX, finalCurY, contentWidth / 2 - 5, 20).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel Edit"), btn -> {
                            editingCustomTracer = null;
                            customTracerColorInput = "GREEN";
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, contentWidth / 2 - 5, 20).build());
                        curY += 25;
                    }
                    int listStartY = curY += 15;
                    ArrayList<String> tracerIds = new ArrayList<String>(s.customTracers.keySet());
                    for (int i = 0; i < tracerIds.size(); ++i) {
                        String tid = (String)tracerIds.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), btn -> {
                            editingCustomTracer = tid;
                            customTracerIdInput = tid;
                            customTracerColorInput = s.customTracers.get((Object)tid).color;
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.customTracers.remove(tid);
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 28: {
                    curY += 24;
                    curY = this.addBoolOption("Item Highlights Enabled", s.itemHighlightsEnabled, v -> {
                        s.itemHighlightsEnabled = v;
                    }, contentX, contentWidth, curY);
                    curY += 10;
                    curY = this.addTextBox("Target Name / Regex:", itemHighMobInput, v -> {
                        itemHighMobInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addColorCycleButton("Color", itemHighColorInput, v -> {
                        itemHighColorInput = v;
                    }, contentX, contentWidth, curY);
                    int finalCurY = curY = this.addBoolOption("Show Invis", itemHighShowInvInput, v -> {
                        itemHighShowInvInput = v;
                    }, contentX, contentWidth, curY);
                    String addBtnText = editingItemHighMob != null ? "\u00a7e\u2714 Save Highlight" : "\u00a7a+ Add Highlight";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!itemHighMobInput.trim().isEmpty()) {
                            if (editingItemHighMob != null && !editingItemHighMob.equalsIgnoreCase(itemHighMobInput)) {
                                s.itemHighlights.remove(editingItemHighMob);
                            }
                            BomboConfig.HighlightInfo info = new BomboConfig.HighlightInfo();
                            info.color = itemHighColorInput;
                            info.showInvisible = itemHighShowInvInput;
                            info.enabled = true;
                            s.itemHighlights.put(itemHighMobInput.toLowerCase(), info);
                            BomboConfig.save();
                            itemHighMobInput = "";
                            itemHighColorInput = "WHITE";
                            itemHighShowInvInput = true;
                            editingItemHighMob = null;
                            this.init();
                        }
                    }).bounds(contentX, finalCurY, contentWidth / 2, 20).build());
                    if (editingItemHighMob != null) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel Edit"), btn -> {
                            itemHighMobInput = "";
                            itemHighColorInput = "WHITE";
                            itemHighShowInvInput = true;
                            editingItemHighMob = null;
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, finalCurY, 80, 20).build());
                    }
                    int listStartY = curY += 35;
                    ArrayList<String> sortedMobs = new ArrayList<String>(s.itemHighlights.keySet());
                    Collections.sort(sortedMobs);
                    for (int i = 0; i < sortedMobs.size(); ++i) {
                        String mobName = (String)sortedMobs.get(i);
                        int itemY = listStartY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listStartY + 15 || itemY >= this.height - 20) continue;
                        BomboConfig.HighlightInfo info = s.itemHighlights.get(mobName);
                        String toggleLabel = info.enabled ? "\u00a7aON" : "\u00a7cOFF";
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)toggleLabel), btn -> {
                            info.enabled = !info.enabled;
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + 130, itemY + 5, 45, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEDIT"), b -> {
                            itemHighMobInput = mobName;
                            itemHighColorInput = info.color;
                            itemHighShowInvInput = info.showInvisible;
                            editingItemHighMob = mobName;
                            this.init();
                        }).bounds(contentX + 180, itemY + 5, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), b -> {
                            s.itemHighlights.remove(mobName);
                            BomboConfig.save();
                            if (editingItemHighMob != null && editingItemHighMob.equals(mobName)) {
                                itemHighMobInput = "";
                                editingItemHighMob = null;
                            }
                            this.init();
                        }).bounds(contentX + 225, itemY + 5, 35, 18).build());
                    }
                    break;
                }
                case 29: {
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cClear Ordered Waypoints"), btn -> {
                        OrderedWaypoints.clear();
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage((Component)Component.literal((String)"\u00a7aCleared ordered waypoints."));
                        }
                    }).bounds(contentX, curY += 24, 200, 20).build());
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7bImport from Clipboard"), btn -> {
                        try {
                            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                            if (clipboard != null && !clipboard.isEmpty()) {
                                int imported = OrderedWaypoints.importWaypointsFromClipboard(clipboard);
                                if (Minecraft.getInstance().player != null) {
                                    Minecraft.getInstance().player.sendSystemMessage((Component)Component.literal((String)("\u00a7aImported " + imported + " ordered waypoints.")));
                                }
                            }
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }).bounds(contentX, curY += 24, 200, 20).build());
                    curY += 24;
                    break;
                }
                case 30: {
                    int finalCurY = curY += 24;
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7aAdd/Save Custom Timer"), btn -> {
                        if (!(timerNameInput.isEmpty() || timerTimeInput.isEmpty() || timerTriggerInput.isEmpty() || timerItemInput.isEmpty())) {
                            long dur = CustomTimerManager.parseTimeMs(timerTimeInput);
                            if (dur > 0L) {
                                BomboConfig.CustomTimerDef def = new BomboConfig.CustomTimerDef();
                                def.name = timerNameInput;
                                def.timeStr = timerTimeInput;
                                def.durationSeconds = dur / 1000L;
                                def.triggerText = timerTriggerInput;
                                def.logoItemId = timerItemInput;
                                def.enabled = timerEnabledInput;
                                def.showOnlyWhenReady = timerShowOnlyReadyInput;
                                def.keepReadyState = timerKeepReadyInput;
                                if (editingTimerIndex >= 0 && editingTimerIndex < s.customTimers.size()) {
                                    s.customTimers.set(editingTimerIndex, def);
                                    editingTimerIndex = -1;
                                } else {
                                    s.customTimers.add(def);
                                }
                                timerNameInput = "";
                                timerTimeInput = "";
                                timerTriggerInput = "";
                                timerItemInput = "";
                                timerEnabledInput = true;
                                timerShowOnlyReadyInput = false;
                                timerKeepReadyInput = false;
                                BomboConfig.save();
                                this.minecraft.setScreen((Screen)new BomboConfigGUI(this.parent));
                            } else if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage((Component)Component.literal((String)"\u00a7cInvalid duration format!"));
                            }
                        }
                    }).bounds(contentX, curY, 200, 20).build());
                    EditBox nameBox = new EditBox(this.font, contentX, curY += 34, 120, 16, (Component)Component.literal((String)"Name"));
                    nameBox.setValue(timerNameInput);
                    nameBox.setResponder(val -> {
                        timerNameInput = val;
                    });
                    this.addRenderableWidget(nameBox);
                    this.activeBoxes.add(nameBox);
                    EditBox timeBox = new EditBox(this.font, contentX + 130, curY, 60, 16, (Component)Component.literal((String)"Time"));
                    timeBox.setValue(timerTimeInput);
                    timeBox.setResponder(val -> {
                        timerTimeInput = val;
                    });
                    this.addRenderableWidget(timeBox);
                    this.activeBoxes.add(timeBox);
                    EditBox triggerBox = new EditBox(this.font, contentX, curY += 34, 150, 16, (Component)Component.literal((String)"Trigger"));
                    triggerBox.setValue(timerTriggerInput);
                    triggerBox.setResponder(val -> {
                        timerTriggerInput = val;
                    });
                    this.addRenderableWidget(triggerBox);
                    this.activeBoxes.add(triggerBox);
                    EditBox itemBox = new EditBox(this.font, contentX + 160, curY, 100, 16, (Component)Component.literal((String)"Item ID"));
                    itemBox.setValue(timerItemInput);
                    itemBox.setResponder(val -> {
                        timerItemInput = val;
                    });
                    this.addRenderableWidget(itemBox);
                    this.activeBoxes.add(itemBox);
                    curY += 34;
                    curY = this.addBoolOption("Enabled", timerEnabledInput, val -> {
                        timerEnabledInput = val;
                    }, contentX, 80, curY);
                    curY = this.addBoolOption("Show Only When Ready", timerShowOnlyReadyInput, val -> {
                        timerShowOnlyReadyInput = val;
                    }, contentX + 90, 160, curY - 24);
                    curY = this.addBoolOption("Keep Ready", timerKeepReadyInput, val -> {
                        timerKeepReadyInput = val;
                    }, contentX + 240, 100, curY - 24);
                    int listY = (curY += 8) + 20 - (int)this.scrollAmount;
                    for (int i = 0; i < s.customTimers.size(); ++i) {
                        int idx = i;
                        int itemY = listY + i * 24;
                        if (itemY <= curY + 15 || itemY >= this.height - 15) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7aStart"), btn -> {
                            BomboConfig.CustomTimerDef def = s.customTimers.get(idx);
                            CustomTimerManager.startTimer(def.name, def.durationSeconds * 1000L, false, def.logoItemId, def.showOnlyWhenReady, def.keepReadyState);
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage((Component)Component.literal((String)("\u00a78[\u00a7bBomboAddons\u00a78] \u00a77Started custom timer: \u00a7e" + def.name)));
                            }
                        }).bounds(contentX + contentWidth - 140, itemY + 2, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEdit"), btn -> {
                            editingTimerIndex = idx;
                            BomboConfig.CustomTimerDef def = s.customTimers.get(idx);
                            timerNameInput = def.name;
                            timerTimeInput = def.timeStr;
                            timerTriggerInput = def.triggerText;
                            timerItemInput = def.logoItemId;
                            timerEnabledInput = def.enabled;
                            timerShowOnlyReadyInput = def.showOnlyWhenReady;
                            timerKeepReadyInput = def.keepReadyState;
                            this.minecraft.setScreen((Screen)new BomboConfigGUI(this.parent));
                        }).bounds(contentX + contentWidth - 95, itemY + 2, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDelete"), btn -> {
                            s.customTimers.remove(idx);
                            if (editingTimerIndex == idx) {
                                editingTimerIndex = -1;
                                timerNameInput = "";
                                timerTimeInput = "";
                                timerTriggerInput = "";
                                timerItemInput = "";
                                timerEnabledInput = true;
                                timerShowOnlyReadyInput = false;
                            }
                            BomboConfig.save();
                            this.minecraft.setScreen((Screen)new BomboConfigGUI(this.parent));
                        }).bounds(contentX + contentWidth - 50, itemY + 2, 50, 18).build());
                    }
                    break;
                }
                case 31: {
                    curY += 24;
                    ArrayList<String> presetWidgets = new ArrayList<String>(TabWidgetHud.getAvailableTabWidgets());
                    for (String w : TabWidgetHud.COMMON_WIDGETS) {
                        if (presetWidgets.contains(w)) continue;
                        presetWidgets.add(w);
                    }
                    presetWidgets.add("Custom...");
                    String selectedPreset = presetWidgets.contains(widgetNameInput) ? widgetNameInput : "Custom...";
                    curY = this.addListPickerOption("Select Preset", selectedPreset, presetWidgets, v -> {
                        if (!v.equals("Custom...")) {
                            widgetNameInput = v;
                        }
                        this.init();
                    }, contentX, contentWidth, curY);
                    curY = this.addTextBox("Widget Name", widgetNameInput, v -> {
                        widgetNameInput = v;
                    }, contentX, contentWidth, curY);
                    List<String> islandOptions = List.of("All", "The Garden", "Private Island", "The Hub", "Dwarven Mines", "Crystal Hollows", "Crimson Isle", "The End", "Spider's Den", "The Park", "Deep Caverns", "Gold Mine", "Farming Islands", "The Rift", "Jerry's Workshop", "Dark Auction", "Dungeon Hub", "Kuudra's Hollow", "Dungeons");
                    curY = this.addListPickerOption("Island Only", widgetIslandInput, islandOptions, v -> {
                        widgetIslandInput = v;
                    }, contentX, contentWidth, curY);
                    curY = this.addBoolOption("Enabled", widgetEnabledInput, v -> {
                        widgetEnabledInput = v;
                    }, contentX, contentWidth, curY);
                    String addBtnText = editingWidgetIndex != -1 ? "\u00a7e\u2714 Save Widget" : "\u00a7a+ Add Widget";
                    this.addRenderableWidget(Button.builder((Component)Component.literal((String)addBtnText), btn -> {
                        if (!widgetNameInput.trim().isEmpty()) {
                            BomboConfig.TabWidgetInfo info = new BomboConfig.TabWidgetInfo(widgetNameInput.trim(), widgetIslandInput, widgetEnabledInput, 10, 50 + s.tabWidgets.size() * 30, 1.0f);
                            if (editingWidgetIndex != -1 && editingWidgetIndex < s.tabWidgets.size()) {
                                info.x = s.tabWidgets.get((int)BomboConfigGUI.editingWidgetIndex).x;
                                info.y = s.tabWidgets.get((int)BomboConfigGUI.editingWidgetIndex).y;
                                info.scale = s.tabWidgets.get((int)BomboConfigGUI.editingWidgetIndex).scale;
                                s.tabWidgets.set(editingWidgetIndex, info);
                            } else {
                                s.tabWidgets.add(info);
                            }
                            BomboConfig.save();
                            widgetNameInput = "";
                            widgetIslandInput = "All";
                            widgetEnabledInput = true;
                            editingWidgetIndex = -1;
                            this.init();
                        }
                    }).bounds(contentX, curY, contentWidth / 2 - 5, 20).build());
                    if (editingWidgetIndex != -1) {
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cCancel Edit"), btn -> {
                            widgetNameInput = "";
                            widgetIslandInput = "All";
                            widgetEnabledInput = true;
                            editingWidgetIndex = -1;
                            this.init();
                        }).bounds(contentX + contentWidth / 2 + 5, curY, contentWidth / 2 - 5, 20).build());
                    }
                    int listStartY = curY += 30;
                    for (int i = 0; i < s.tabWidgets.size(); ++i) {
                        int idx = i;
                        BomboConfig.TabWidgetInfo info = s.tabWidgets.get(i);
                        int itemY = listStartY + i * 24 - (int)this.scrollAmount;
                        if (itemY <= listStartY - 10 || itemY >= this.height - 50) continue;
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7eEdit"), btn -> {
                            widgetNameInput = info.name;
                            widgetIslandInput = info.island;
                            widgetEnabledInput = info.enabled;
                            editingWidgetIndex = idx;
                            this.init();
                        }).bounds(contentX + contentWidth - 95, itemY, 40, 18).build());
                        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7cDEL"), btn -> {
                            s.tabWidgets.remove(idx);
                            if (editingWidgetIndex == idx) {
                                editingWidgetIndex = -1;
                                widgetNameInput = "";
                            }
                            BomboConfig.save();
                            this.init();
                        }).bounds(contentX + contentWidth - 50, itemY, 45, 18).build());
                    }
                    break;
                }
                case 32: {
                    int col1X = contentX;
                    int col1W = contentWidth / 2 - 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    this.addRenderableWidget(Button.builder(Component.literal("§bOpen Chat Modifier Rules Manager"), b -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new ChatModifierScreen(this));
                        }
                    }).bounds(col1X, y1, 240, 24).build());
                    y1 += 34;
                    break;
                }
            }
            if (colorPickerTarget != null) {
                this.renderColorPicker(contentX, 70, contentWidth);
            }
            if (this.listPickerTitle != null) {
                this.renderListPicker(contentX, 70, contentWidth);
            }
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7lSave & Close"), btn -> {
                BomboConfig.save();
                this.minecraft.setScreenAndShow(this.parent);
            }).bounds(this.width / 2 - 75, this.height - 32, 150, 24).build());
                    }
        catch (Throwable e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during init!", e);
            try {
                File file = new File("crash_exception.log");
                try (PrintWriter pw = new PrintWriter(new FileWriter(file, true));){
                    pw.println("=== GUI INIT EXCEPTION ===");
                    e.printStackTrace(pw);
                    pw.println("==========================");
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    private int addBoolOption(String label, boolean value, Consumer<Boolean> setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        Checkbox cb = Checkbox.builder((Component)Component.literal((String)""), (Font)this.font).pos(x, y).selected(value).onValueChange((box, val) -> {
            setter.accept(val);
            BomboConfig.save();
        }).build();
        cb.setX(x);
        cb.setY(y);
        cb.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(cb);
        return y + 24;
    }

    private int addIntLabelSlider(String label, int current, int min, int max, int step, IntConsumer setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        Button b1 = Button.builder((Component)Component.literal((String)"\u00a77-"), btn -> {
            setter.accept(Math.max(min, current - step));
            BomboConfig.save();
            this.init();
        }).bounds(x + w - 45, y, 20, 20).build();
        b1.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(b1);

        Button b2 = Button.builder((Component)Component.literal((String)"\u00a77+"), btn -> {
            setter.accept(Math.min(max, current + step));
            BomboConfig.save();
            this.init();
        }).bounds(x + w - 20, y, 20, 20).build();
        b2.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(b2);
        return y + 24;
    }

    private int addFloatLabelSlider(String label, float current, float min, float max, float increment, Consumer<Float> setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        Button b1 = Button.builder((Component)Component.literal((String)"\u00a77-"), btn -> {
            setter.accept(Float.valueOf(Math.max(min, current - increment)));
            BomboConfig.save();
            this.init();
        }).bounds(x + w - 45, y, 20, 20).build();
        b1.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(b1);

        Button b2 = Button.builder((Component)Component.literal((String)"\u00a77+"), btn -> {
            setter.accept(Float.valueOf(Math.min(max, current + increment)));
            BomboConfig.save();
            this.init();
        }).bounds(x + w - 20, y, 20, 20).build();
        b2.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(b2);
        return y + 24;
    }

        private static EditBox activeSoundBox = null;
    private static List<String> soundSuggestions = new ArrayList<>();
    private static boolean isTabCyclingSound = false;
    private static int selectedSoundSuggestion = 0;
    private static int soundBoxX = 0;
    private static int soundBoxY = 0;
    private static int soundBoxW = 0;


    private int addSoundTextBox(String label, String current, Consumer<String> setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        int bx = x + w / 2;
        int bw = w / 2;
        soundBoxX = bx;
        soundBoxY = y;
        soundBoxW = bw;
        EditBox box = new EditBox(this.font, bx, y, bw, 16, Component.literal(label));
        box.setMaxLength(1024);
        box.setValue(current);
        box.setResponder(val -> {
            setter.accept(val);
            BomboConfig.save();
            if (!isTabCyclingSound) {
                updateSoundSuggestions(val);
            }
        });
        box.setBordered(true);
        box.setVisible(y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(box);
        this.activeBoxes.add(box);
        activeSoundBox = box;
        updateSoundSuggestions(current);
        return y + 24;
    }

    private static void updateSoundSuggestions(String query) {
        soundSuggestions.clear();
        selectedSoundSuggestion = 0;
        if (query == null || query.trim().isEmpty()) return;
        String raw = query.trim().toLowerCase();
        String prefix = raw.startsWith("minecraft:") ? raw.substring(10) : raw;
        for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.keySet()) {
            String full = id.toString();
            String path = id.getPath();
            if (full.toLowerCase().contains(prefix) || path.toLowerCase().contains(prefix)) {
                soundSuggestions.add(full);
                if (soundSuggestions.size() >= 8) break;
            }
        }
    }

    private int addTextBox(String label, String current, Consumer<String> setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        EditBox box = new EditBox(this.font, x + w / 2, y, w / 2, 16, (Component)Component.literal((String)label));
        box.setMaxLength(1024);
        box.setValue(current);
        box.setResponder(val -> {
            setter.accept((String)val);
            BomboConfig.save();
        });
        box.setBordered(true);
        box.setVisible(y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(box);
        this.activeBoxes.add(box);
        return y + 24;
    }

    private int addKeyBindButton(String label, String current, Consumer<String> setter, String target, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        boolean listening = listeningForKeyTarget.equals(target);
        String displayKey = ClickLogic.getKeyDisplayName(current);
        String txt = listening ? "\u00a7e[PRESS KEY]" : "\u00a7f" + label + ": \u00a7d" + (current.isEmpty() ? "None" : displayKey);
        Button btn = Button.builder((Component)Component.literal((String)txt), b -> {
            listeningForKeyTarget = target;
            this.init();
        }).bounds(x + w / 2, y, w / 2, 16).build();
        btn.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(btn);
        return y + 24;
    }

    private int addComboBindButton(String label, String current, Consumer<String> setter, String target, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        boolean listening = listeningForKeyTarget.equals(target);
        String txt = listening ? "\u00a7e[PRESS KEYS...]" : "\u00a7f" + label + ": \u00a7d" + (current.isEmpty() ? "None" : current);
        Button btn = Button.builder((Component)Component.literal((String)txt), b -> {
            listeningForKeyTarget = target;
            recordedComboKeys.clear();
            this.init();
        }).bounds(x + w / 2, y, w / 2, 16).build();
        btn.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(btn);
        return y + 24;
    }

    private String getColorFormatting(String colorName) {
        if (colorName == null) {
            return "\u00a7r";
        }
        return switch (colorName.toUpperCase()) {
            case "BLACK" -> "\u00a70";
            case "DARK_BLUE" -> "\u00a71";
            case "DARK_GREEN" -> "\u00a72";
            case "DARK_AQUA" -> "\u00a73";
            case "DARK_RED" -> "\u00a74";
            case "DARK_PURPLE" -> "\u00a75";
            case "GOLD" -> "\u00a76";
            case "GRAY" -> "\u00a77";
            case "DARK_GRAY" -> "\u00a78";
            case "BLUE" -> "\u00a79";
            case "GREEN" -> "\u00a7a";
            case "AQUA" -> "\u00a7b";
            case "RED" -> "\u00a7c";
            case "LIGHT_PURPLE", "PINK" -> "\u00a7d";
            case "YELLOW" -> "\u00a7e";
            case "WHITE" -> "\u00a7f";
            default -> "\u00a7r";
        };
    }

    private int addCycleOption(String label, String current, List<String> options, Consumer<String> setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        Button btn = Button.builder((Component)Component.literal((String)current), b -> {
            int idx = options.indexOf(current);
            int next = (idx + 1) % options.size();
            setter.accept((String)options.get(next));
            BomboConfig.save();
            this.init();
        }).bounds(x + w / 2, y, w / 2, 16).build();
        btn.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(btn);
        return y + 24;
    }

    private int addColorCycleButton(String label, String current, Consumer<String> setter, int x, int w, int y) {
        if (!optionMatchesSearch(label)) return y;
        String formatting = this.getColorFormatting(current);
        String btnText = label + ": " + formatting + current.toUpperCase();
        Button btn = Button.builder((Component)Component.literal((String)btnText), b -> {
            colorPickerTarget = label;
            colorPickerSetter = setter;
            this.init();
        }).bounds(x + 24, y, Math.min(w - 24, 200), 20).build();
        btn.visible = (y >= 56 && y <= this.height - 44);
        this.addRenderableWidget(btn);
        return y + 24;
    }

    private void renderColorPicker(int x, int y, int w) {
        int pickerW = 120;
        int pickerH = this.height - 80;
        int pickerX = x + (w - pickerW) / 2;
        int pickerY = 40;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7c\u2715"), btn -> {
            colorPickerTarget = null;
            this.init();
        }).bounds(pickerX + pickerW - 22, pickerY + 2, 20, 20).build());
        int btnW = 100;
        int btnH = 16;
        int spacing = 2;
        int startX = pickerX + 10;
        int startY = pickerY + 25;
        ArrayList<String> colorsToUse = new ArrayList<String>();
        if ("Fuck Diorite Color".equals(colorPickerTarget)) {
            colorsToUse.add("NONE");
        }
        colorsToUse.addAll(SlotHighlight.COLORS);
        for (int i = 0; i < colorsToUse.size(); ++i) {
            String color = (String)colorsToUse.get(i);
            String formatting = this.getColorFormatting(color);
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)(formatting + color)), btn -> {
                colorPickerSetter.accept(color);
                colorPickerTarget = null;
                BomboConfig.save();
                this.init();
            }).bounds(startX, startY + i * (btnH + spacing), btnW, btnH).build());
        }
    }

    private int addListPickerOption(String label, String current, List<String> options, Consumer<String> setter, int x, int w, int y) {
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)(current.isEmpty() ? "Select..." : current)), btn -> {
            this.listPickerTitle = label;
            this.listPickerOptions = options;
            this.listPickerSetter = setter;
            this.listPickerScroll = 0.0f;
            this.init();
        }).bounds(x + w / 2, y, w / 2, 16).build());
        return y + 24;
    }

    private void renderListPicker(int x, int y, int w) {
        int pickerW = 200;
        int pickerH = this.height - 80;
        int pickerX = x + (w - pickerW) / 2;
        int pickerY = 40;
        this.addRenderableWidget(Button.builder((Component)Component.literal((String)"\u00a7c\u2715"), btn -> {
            this.listPickerTitle = null;
            this.listPickerOptions = null;
            this.listPickerSetter = null;
            this.init();
        }).bounds(pickerX + pickerW - 22, pickerY + 2, 20, 20).build());
        int btnW = 180;
        int btnH = 18;
        int spacing = 3;
        int startX = pickerX + 10;
        int startY = pickerY + 28;
        for (int i = 0; i < this.listPickerOptions.size(); ++i) {
            String opt = this.listPickerOptions.get(i);
            int itemY = startY + i * (btnH + spacing) - (int)this.listPickerScroll;
            if (itemY < startY || itemY > pickerY + pickerH - 24) continue;
            this.addRenderableWidget(Button.builder((Component)Component.literal((String)("\u00a7e" + opt)), btn -> {
                this.listPickerSetter.accept(opt);
                this.listPickerTitle = null;
                this.listPickerOptions = null;
                this.listPickerSetter = null;
                BomboConfig.save();
                this.init();
            }).bounds(startX, itemY, btnW, btnH).build());
        }
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        try {
            
            int contentBaseY;
            int pickerY;
            int pickerX;
            int pickerH;
            int pickerW;
            g.fillGradient(0, 0, 130, this.height, -300871397, -300016082);
            g.fill(129, 0, 130, this.height, 0x33FFFFFF);
            g.fillGradient(130, 0, this.width, 40, -871296741, -1440866770);
            g.fill(130, 39, this.width, 40, 0x55FFFFFF);
            BomboConfig.Settings s = BomboConfig.get();
            if (colorPickerTarget != null) {
                g.fill(0, 0, this.width, this.height, -1442840576);
                pickerW = 120;
                pickerH = this.height - 80;
                pickerX = 146 + (this.width - 130 - 24 - pickerW) / 2;
                pickerY = 40;
                g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, -14803410);
                g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + 24, -15658725);
                g.text(this.font, "\u00a76Select Color", pickerX + 10, pickerY + 8, -1);
            }
            if (this.listPickerTitle != null) {
                g.fill(0, 0, this.width, this.height, -1442840576);
                pickerW = 200;
                pickerH = this.height - 80;
                pickerX = 146 + (this.width - 130 - 24 - pickerW) / 2;
                pickerY = 40;
                g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, -14803410);
                g.fill(pickerX, pickerY, pickerX + pickerW, pickerY + 24, -15658725);
                g.text(this.font, "\u00a76Select " + this.listPickerTitle, pickerX + 10, pickerY + 8, -1);
            }
            g.enableScissor(0, 56, 130, this.height - 8);
            for (Button btn : this.sidebarButtons) {
                btn.extractRenderState(g, mouseX, mouseY, partialTick);
            }
            g.disableScissor();
            int totalRendered = 0;
            for (int i = 0; i < this.categories.size(); ++i) {
                if (s.hideCheats && (i == 2 || i == 9 || i == 24) || this.categories.get(i).equals("Party Settings")) continue;
                ++totalRendered;
            }
            int totalHeight = totalRendered * 26;
            int viewportHeight = this.height - 64 - 8;
            if (totalHeight > viewportHeight) {
                int trackX = 126;
                int trackY = 56;
                int trackH = this.height - trackY - 8;
                int thumbH = Math.max(10, trackH * viewportHeight / totalHeight);
                int maxScroll = totalHeight - viewportHeight;
                int thumbY = trackY + (int)(this.categoryScrollAmount * (double)(trackH - thumbH) / (double)maxScroll);
                g.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x15FFFFFF);
                g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0x55FFFFFF);
            }
                        // Render sound suggestions popup list if activeSoundBox is focused
            if (activeSoundBox != null && activeSoundBox.isFocused() && !soundSuggestions.isEmpty() && activeSoundBox.getY() > 0) {
                int bx = activeSoundBox.getX();
                int bw = activeSoundBox.getWidth();
                int by = activeSoundBox.getY();
                int listH = Math.min(soundSuggestions.size() * 14 + 4, 120);
                int listY = by + 18;
                if (listY + listH > this.height - 20) {
                    listY = by - listH - 2;
                }
                g.fill(bx, listY, bx + bw, listY + listH, 0xF0101015);
                g.outline(bx, listY, bw, listH, 0xFF555555);
                for (int sIdx = 0; sIdx < soundSuggestions.size(); sIdx++) {
                    int itemY = listY + 2 + sIdx * 14;
                    String sName = soundSuggestions.get(sIdx);
                    if (sIdx == selectedSoundSuggestion) {
                        g.fill(bx + 1, itemY, bx + bw - 1, itemY + 13, 0xFF335588);
                    }
                    g.text(this.font, "§e" + sName, bx + 4, itemY + 3, -1, false);
                }
            }
            super.extractRenderState(g, mouseX, mouseY, partialTick);
            if (selectedCategory == 0 && loreAdditionsX != -1 && mouseX >= loreAdditionsX && mouseX <= loreAdditionsX + loreAdditionsW && mouseY >= loreAdditionsY && mouseY <= loreAdditionsY + loreAdditionsH) {
                List<String> tooltip = List.of("§eLore Additions", "§7Adds extra info to item lores.", "§bRight-click §7to configure options.");
                int tX = Math.max(loreAdditionsX + loreAdditionsW - 60, mouseX + 30);
                int tY = mouseY - 16;
                int width = 180;
                int height = tooltip.size() * 10 + 4;
                g.fill(tX - 4, tY - 4, tX + width, tY + height, -15198184);
                g.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, -11184811);
                g.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, -11184811);
                g.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, -11184811);
                g.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, -11184811);
                for (int idx = 0; idx < tooltip.size(); ++idx) {
                    g.text(this.font, tooltip.get(idx), tX, tY + idx * 10, -1, false);
                }
            }
            if (selectedCategory == 0 && partyCommandsX != -1 && mouseX >= partyCommandsX && mouseX <= partyCommandsX + partyCommandsWidth && mouseY >= partyCommandsY && mouseY <= partyCommandsY + partyCommandsHeight) {
                List<String> tooltip = List.of("\u00a7eParty Commands Automation", "\u00a77Allows party members to trigger commands.", "\u00a7bRight-click \u00a77to configure options.");
                int tX = mouseX + 12;
                int tY = mouseY;
                int width = 180;
                int height = tooltip.size() * 10 + 4;
                g.fill(tX - 4, tY - 4, tX + width, tY + height, -15198184);
                g.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, -11184811);
                g.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, -11184811);
                g.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, -11184811);
                g.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, -11184811);
                for (int idx = 0; idx < tooltip.size(); ++idx) {
                    g.text(this.font, tooltip.get(idx), tX, tY + idx * 10, -1, true);
                }
            }
            if (selectedCategory == 0 && frozenBlazeWarnX != -1 && mouseX >= frozenBlazeWarnX && mouseX <= frozenBlazeWarnX + frozenBlazeWarnW && mouseY >= frozenBlazeWarnY && mouseY <= frozenBlazeWarnY + frozenBlazeWarnH) {
                List<String> tooltip = List.of("§eFrozen Blaze Warning", "§7Alerts when AFK with Frozen Blaze.", "§bRight-click §7to configure options.");
                int tX = mouseX + 12;
                int tY = mouseY;
                int width = 180;
                int height = tooltip.size() * 10 + 4;
                g.fill(tX - 4, tY - 4, tX + width, tY + height, -15198184);
                g.fill(tX - 5, tY - 5, tX - 4, tY + height + 1, -11184811);
                g.fill(tX + width, tY - 5, tX + width + 1, tY + height + 1, -11184811);
                g.fill(tX - 5, tY - 5, tX + width + 1, tY - 4, -11184811);
                g.fill(tX - 5, tY + height, tX + width + 1, tY + height + 1, -11184811);
                for (int idx = 0; idx < tooltip.size(); ++idx) {
                    g.text(this.font, tooltip.get(idx), tX, tY + idx * 10, -1, true);
                }
            }
            g.text(this.font, "\u00a76\u00a7lBomboaddons \u00a7r\u00a77Config", 138, 14, -1, true);
            g.text(this.font, "\u00a7f\u00a7lCATEGORIES", 8, 26, -1, true);
            if (this.searchBoxWidget != null) { this.searchBoxWidget.extractRenderState(g, mouseX, mouseY, partialTick); }
            int contentX = 146;
            int contentWidth = this.width - 130 - 24;
            int categoryTitleY = 56;
            int safeIdx = Math.max(0, Math.min(selectedCategory, this.categories.size() - 1));
            g.text(this.font, "\u00a7f\u00a7l" + this.categories.get(safeIdx).toUpperCase(), contentX, categoryTitleY, -1, true);
            int curY = contentBaseY = categoryTitleY + 30;
            switch (selectedCategory) {
                case 0: {
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;

                    if (loreAdditionsSubmenu) {
                        g.text(this.font, "§6§lLore Additions Settings", col1X, y1, -22016, true);
                        y1 += 28;
                        y1 = this.drawOptionLabel(g, "§7Copy Canceled Order Amount (Ctrl+Click)", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Supercraft Max Calculator (Ctrl+Click)", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Starts In Absolute Time", col1X + 24, y1, -1, false);
                        break;
                    }

                    if (frozenBlazeWarnSubmenu) {
                        g.text(this.font, "§6§lFrozen Blaze Warning Settings", col1X, y1, -22016, true);
                        y1 += 28;
                        y1 = this.drawOptionLabel(g, "§7Frozen Blaze Warning Enabled", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Play Warning Sound", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Show Title On Screen", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Send Chat Warning", col1X + 24, y1, -1, false);
                        g.text(this.font, "§fAFK Warning Time: §e" + s.fbWarnSeconds + "s", col1X, (y1 += 24) + 4, -1);
                        break;
                    }

                    y1 = this.drawOptionLabel(g, "§7Clear Water & Lava Vision", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Sign Calculator", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7SBE Commands", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Copy Chat", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Left Click Etherwarp", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Sphinx Macro", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Hollow Wand Fix", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Hollow Wand Double Click", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Lasso Fix", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Auto Accept Carnival", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Auto Accept NPC Lore", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Lowest BIN Tooltip", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Raw Craft Cost Tooltip", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7NPC Sell Price Tooltip", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Auto Trevor Quest", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Daily Reward Helper", col1X + 24, y1, -1, false);
                    
                    y1 = this.drawOptionLabel(g, "§7Lore Additions §b(Right-Click)", col1X + 24, y1, -1, false);

                    y1 = this.drawOptionLabel(g, "§7In-Chat Search Bar", col1X + 24, y1, -1, false);
                    if (s.chatSearchBar) {
                        y1 = this.drawOptionLabel(g, "§7Chat Search Background", col1X + 24, y1, -1, false);
                    }
                    y1 = this.drawOptionLabel(g, "§7Custom Time Enabled", col1X + 24, y1, -1, false);
                    if (s.customTimeEnabled) {
                        g.text(this.font, "§fCustom Time Hour: §e" + BomboConfig.get().customTimeHour + ":00", col1X, (y1 += 24) + 4, -1);
                        y1 += 22;
                    }
                    y1 = this.drawOptionLabel(g, "§7Weather Override Enabled", col1X + 24, y1, -1, false);
                    if (s.customWeatherEnabled) {
                        y1 += 26;
                    }
                    y1 += 10;
                    y1 = this.drawOptionLabel(g, "§7Enable Egg Finder", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Egg Finder Chat Alerts", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Egg Finder Beacon", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Egg Finder Through Walls", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Hoppity Egg HUD", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Hoppity Warp", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Golden Dragon Nest Finder", col1X + 24, y1, -1, false);

                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.drawOptionLabel(g, "§7Ignore Caps Lock", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Server List Button", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Reconnect Button", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Quick Join Commands (/f1, /m1, etc)", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Auto Reconnect", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Party Commands §b(Right-Click)", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Bypass Resource Pack", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7NoResourcePack Feature", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Show Command On Hover", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Auto Hoppity Calls", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Disable Hypixel Tooltips", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Hypixel Shortcut Button", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Smart Disconnect", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Borderless Fullscreen", col2X + 24, y2, -1, false);
                    y2 += 10;
                    y2 = this.drawOptionLabel(g, "§7Fuck Diorite", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "§7Fuck Diorite Pillar Color", col2X + 24, y2, -1, false);
                    if (s.fuckDioritePillarColor && optionMatchesSearch("Fuck Diorite Color")) {
                        y2 += 24;
                    }
                    y2 = this.drawOptionLabel(g, "§7Frozen Blaze Warning §b(Right-Click)", col2X + 24, y2, -1, false);
                    break;
                }
                case 1: {
                    int r;
                    int rgb;
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    y1 = this.drawOptionLabel(g, "§7Dice Tracker HUD", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Feast Bakery HUD", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7RNG Profit HUD", col1X + 24, y1, -1, false);
                    g.text(this.font, "§fRNG HUD Opacity: §e" + BomboConfig.get().rngProfitHudOpacity + "%", col1X, y1 + 4, -1);
                    y1 += 24;
                    y1 = this.drawOptionLabel(g, "§7Custom Timers HUD", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Tab Widget HUD", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Hoppity Egg HUD", col1X + 24, y1, -1, false);
                    y1 += 26; // button offset
                    y1 = this.drawOptionLabel(g, "§7Alpha Tracker HUD", col1X + 24, y1, -1, false);
                    if (BomboConfig.get().alphaTrackerHud) {
                        y1 = this.drawOptionLabel(g, "§7Alpha Only When Open", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Alpha Hide When Closed", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Alpha Show Players", col1X + 24, y1, -1, false);
                    }
                    y1 = this.drawOptionLabel(g, "§7Item List Enabled", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Rarity Background", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Remove Background", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Lock Item List Position", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Separate IL Search", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Keep IL Search Vis", col1X + 24, y1, -1, false);

                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.drawOptionLabel(g, "§7Custom Tooltip Bg", col2X + 24, y2, -1, false);
                    y2 += 24;
                    y2 += 22; // buttons offset
                    int pickerX2 = col2X;
                    int pickerY2 = y2;
                    int svSize = 80;
                    if (isDraggingSv || isDraggingHue || isDraggingAlpha) {
                        if (isDraggingSv) {
                            float sat = (float)(mouseX - pickerX2) / (float)svSize;
                            float val = 1.0f - (float)(mouseY - pickerY2) / (float)svSize;
                            sat = Math.max(0.0f, Math.min(1.0f, sat));
                            val = Math.max(0.0f, Math.min(1.0f, val));
                            int rgb2 = BomboConfigGUI.hsvToRgb(currentHue, sat, val);
                            if (colorPickerMode == 0) {
                                s.tooltipBgColor = s.tooltipBgColor & 0xFF000000 | rgb2;
                            } else {
                                s.tooltipBorderColor = s.tooltipBorderColor & 0xFF000000 | rgb2;
                            }
                        } else if (isDraggingHue) {
                            float hue = (float)(mouseY - pickerY2) / (float)svSize;
                            currentHue = hue = Math.max(0.0f, Math.min(1.0f, hue));
                            int currentRgb = colorPickerMode == 0 ? s.tooltipBgColor & 0xFFFFFF : s.tooltipBorderColor & 0xFFFFFF;
                            float[] hsvTemp = BomboConfigGUI.rgbToHsv(currentRgb >> 16 & 0xFF, currentRgb >> 8 & 0xFF, currentRgb & 0xFF);
                            rgb = BomboConfigGUI.hsvToRgb(hue, hsvTemp[1], hsvTemp[2]);
                            if (colorPickerMode == 0) {
                                s.tooltipBgColor = s.tooltipBgColor & 0xFF000000 | rgb;
                            } else {
                                s.tooltipBorderColor = s.tooltipBorderColor & 0xFF000000 | rgb;
                            }
                        } else if (isDraggingAlpha) {
                            float pct = 1.0f - (float)(mouseY - pickerY2) / (float)svSize;
                            pct = Math.max(0.0f, Math.min(1.0f, pct));
                            int alpha = (int)(pct * 255.0f);
                            if (colorPickerMode == 0) {
                                s.tooltipBgColor = alpha << 24 | s.tooltipBgColor & 0xFFFFFF;
                            } else {
                                s.tooltipBorderColor = alpha << 24 | s.tooltipBorderColor & 0xFFFFFF;
                            }
                        }
                    }
                    int step = 4;
                    for (r = 0; r < svSize; r += step) {
                        for (int c = 0; c < svSize; c += step) {
                            float sat = (float)c / (float)svSize;
                            float val = 1.0f - (float)r / (float)svSize;
                            int rgb3 = BomboConfigGUI.hsvToRgb(currentHue, sat, val);
                            g.fill(pickerX2 + c, pickerY2 + r, pickerX2 + c + step, pickerY2 + r + step, 0xFF000000 | rgb3);
                        }
                    }
                    g.outline(pickerX2, pickerY2, svSize, svSize, -11184811);
                    for (r = 0; r < svSize; r += 2) {
                        float hue = (float)r / (float)svSize;
                        rgb = BomboConfigGUI.hsvToRgb(hue, 1.0f, 1.0f);
                        g.fill(pickerX2 + 90, pickerY2 + r, pickerX2 + 102, pickerY2 + r + 2, 0xFF000000 | rgb);
                    }
                    g.outline(pickerX2 + 90, pickerY2, 12, svSize, -11184811);
                    int activeColorVal = colorPickerMode == 0 ? s.tooltipBgColor : s.tooltipBorderColor;
                    int activeRgb = activeColorVal & 0xFFFFFF;
                    int activeAlpha = activeColorVal >> 24 & 0xFF;
                    for (int r2 = 0; r2 < svSize; r2 += 2) {
                        int alpha = (int)((1.0f - (float)r2 / (float)svSize) * 255.0f);
                        g.fill(pickerX2 + 110, pickerY2 + r2, pickerX2 + 122, pickerY2 + r2 + 2, alpha << 24 | activeRgb);
                    }
                    g.outline(pickerX2 + 110, pickerY2, 12, svSize, -11184811);
                    float[] activeHsv = BomboConfigGUI.rgbToHsv(activeRgb >> 16 & 0xFF, activeRgb >> 8 & 0xFF, activeRgb & 0xFF);
                    int circleX = pickerX2 + (int)(activeHsv[1] * (float)svSize);
                    int circleY = pickerY2 + (int)((1.0f - activeHsv[2]) * (float)svSize);
                    g.fill(circleX - 1, circleY - 1, circleX + 1, circleY + 1, -1);
                    int hueY = pickerY2 + (int)(currentHue * (float)svSize);
                    g.fill(pickerX2 + 89, hueY, pickerX2 + 103, hueY + 1, -1);
                    int alphaY = pickerY2 + (int)((1.0f - (float)activeAlpha / 255.0f) * (float)svSize);
                    g.fill(pickerX2 + 109, alphaY, pickerX2 + 123, alphaY + 1, -1);
                    g.fill(pickerX2 + 130, pickerY2 + 5, pickerX2 + 160, pickerY2 + 35, activeAlpha << 24 | activeRgb);
                    g.outline(pickerX2 + 130, pickerY2 + 5, 30, 30, -1);
                    String colorString = String.format("#%02X%06X", activeAlpha, activeRgb);
                    g.text(this.font, colorString, pickerX2 + 128, pickerY2 + 45, -1);
                    g.text(this.font, "A: " + activeAlpha, pickerX2 + 128, pickerY2 + 57, -1);
                    break;
                }
                                case 2: {
                    curY += 24;
                    curY = this.drawOptionLabel(g, "§7Auto Experiments", contentX + 24, curY, -1, false);
                    g.text(this.font, "§fClick Delay: §e" + BomboConfig.get().experimentClickDelay + "ms", contentX, curY + 4, -1);
                    curY += 24;
                    g.text(this.font, "§fSerum Count: §e" + BomboConfig.get().experimentSerumCount, contentX, curY + 4, -1);
                    curY += 24;
                    curY = this.drawOptionLabel(g, "§7Auto Close", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "§7Get Max XP", contentX + 24, curY, -1, false);
                    break;
                }
                case 3: {
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    y1 = this.drawOptionLabel(g, "§7Garden Movement", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "\u00a77Lock Mouse on Movement", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "\u00a77Sugar Cane Mode", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "\u00a77Direction Helper Warning", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "\u00a77Macro Check Detector", col1X + 24, y1, -1, false);
                    y1 += 24;
                    if (BomboConfig.get().gardenMacroCheckDetector) {
                        g.text(this.font, "\u00a77Stop Movement on Check", col1X + 24, y1 + 4, -1, false);
                        y1 = this.drawOptionLabel(g, "\u00a7fAlarm Sound:", col1X, y1, -1, false);
                        g.text(this.font, "\u00a7fSound Repeats: \u00a7e" + BomboConfig.get().gardenMacroCheckSoundCount, col1X, (y1 += 24) + 4, -1, false);
                        y1 = this.drawOptionLabel(g, "\u00a7fSound Delay: \u00a7e" + BomboConfig.get().gardenMacroCheckSoundDelay + "ms", col1X, y1, -1, false);
                        y1 += 24;
                    }
                    g.text(this.font, "\u00a7fForward:", col1X, y1 += 10, -1);
                    g.text(this.font, "\u00a7fBackward:", col1X, y1 += 24, -1);
                    g.text(this.font, "\u00a7fLeft:", col1X, y1 += 24, -1);
                    g.text(this.font, "\u00a7fRight:", col1X, y1 += 24, -1);
                    g.text(this.font, "\u00a7fBreak:", col1X, y1 += 24, -1);
                    g.text(this.font, "\u00a7fUse:", col1X, y1 += 24, -1);
                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    y2 = this.drawOptionLabel(g, "§7Composter Helper", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Composter HUD", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Composter Timer HUD", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Pest ESP Enabled", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Pest Waypoints", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Remove Waypoint On Return", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Pest Waypoint Beacon", col2X + 24, y2, -1, false);
                    g.text(this.font, "\u00a7fPest Waypoint Duration: \u00a7e" + (String)(BomboConfig.get().pestWaypointDuration == 0 ? "Infinite" : BomboConfig.get().pestWaypointDuration + "s"), col2X, (y2 += 24) + 4, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Pest Tracers", col2X + 24, y2, -1, false);
                    y2 = this.drawOptionLabel(g, "\u00a77Cheese Tracers", col2X + 24, y2, -1, false);
                    g.text(this.font, "\u00a7fPest Color:", col2X, y2 += 24, -1);
                    g.text(this.font, "\u00a7fPest Thickness: \u00a7e" + BomboConfig.get().pestEspThickness, col2X, y2 += 24, -1);
                    break;
                }
                case 4: {
                    int col1X = contentX;
                    int col1W = (contentWidth - 20) / 2;
                    int col2X = contentX + col1W + 20;
                    int y1 = contentBaseY + 24 - (int)this.scrollAmount;
                    int y2 = contentBaseY + 24 - (int)this.scrollAmount;
                    g.text(this.font, "\u00a76\u00a7lHotkey Shortcuts", contentX, contentBaseY - (int)this.scrollAmount, -22016, true);
                    if (optionMatchesSearch("Trade")) { g.text(this.font, "\u00a7fTrade:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Recipe")) { g.text(this.font, "\u00a7fRecipe:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Texture Toggle")) { g.text(this.font, "\u00a7fTexture Toggle:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Usage")) { g.text(this.font, "\u00a7fUsage:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Show Info")) { g.text(this.font, "\u00a7fShow Info:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Count Items")) { g.text(this.font, "\u00a7fCount Items:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Copy NBT")) { g.text(this.font, "\u00a7fCopy NBT:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("GFS Max")) { g.text(this.font, "\u00a7fGFS Max:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("GFS Stack")) { g.text(this.font, "\u00a7fGFS Stack:", col1X, y1 + 4, -1); y1 += 24; }
                    if (optionMatchesSearch("Save Inventory")) { g.text(this.font, "\u00a7fSave Inventory:", col1X, y1 + 4, -1); y1 += 24; }

                    if (optionMatchesSearch("Chat Peek")) { g.text(this.font, "\u00a7fChat Peek:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Next Page")) { g.text(this.font, "\u00a7fNext Page:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Prev Page")) { g.text(this.font, "\u00a7fPrev Page:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Go Back")) { g.text(this.font, "\u00a7fGo Back:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Smart Back")) { g.text(this.font, "\u00a7fSmart Back:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Save Pet")) { g.text(this.font, "\u00a7fSave Pet:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Run Clipboard Cmd")) { g.text(this.font, "\u00a7fRun Clipboard Cmd:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Freelook")) { g.text(this.font, "\u00a7fFreelook:", col2X, y2 + 4, -1); y2 += 24; }
                    y2 = this.drawOptionLabel(g, "\u00a77Freelook Mode: " + (s.freelookToggle ? "Toggle" : "Hold"), col2X + 24, y2, -1, false);
                    if (optionMatchesSearch("Bestiary Highlight")) { g.text(this.font, "\u00a7fBestiary Highlight:", col2X, y2 + 4, -1); y2 += 24; }
                    if (optionMatchesSearch("Item List Search Focus")) { g.text(this.font, "\u00a7fItem List Search Focus:", col2X, y2 + 4, -1); y2 += 24; }
                    break;
                }
                case 6: {
                    g.text(this.font, "\u00a76\u00a7lClicker Targets", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a77Auto GUI: " + (s.autoClicker ? "\u00a7aON" : "\u00a7cOFF"), contentX + 24, (curY += 24) + 4, -1, false);
                    g.text(this.font, "\u00a77Keypress: " + (s.chestClicker ? "\u00a7aON" : "\u00a7cOFF"), contentX + 24, (curY += 24) + 4, -1, false);
                    g.text(this.font, "\u00a7fGUI Name:", contentX, (curY += 34) + 4, -1);
                    g.text(this.font, "\u00a7fItem Name:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fKey to Press:", contentX, (curY += 29) + 4, -1);
                    curY += 29;
                    int listTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Targets", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 20 - (int)this.scrollAmount;
                    for (ClickLogic.ClickTarget target : ClickLogic.getTargets()) {
                        if (listY > listTitleY + 15 && listY < this.height - 15) {
                            String displayKey = ClickLogic.getKeyDisplayName(target.keyName);
                            String txt = "\u00a7e" + target.gui + " \u00a77- \u00a7b" + displayKey;
                            if (!target.item.isEmpty()) {
                                txt = txt + " \u00a78(" + target.item + ")";
                            }
                            g.text(this.font, txt, contentX, listY + 5, -1, false);
                        }
                        listY += 22;
                    }
                    break;
                }
                case 5: {
                    g.text(this.font, "\u00a76\u00a7lProfile Management", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fActive Profile: \u00a7e" + s.activeProfile, contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fCreate New Profile:", contentX, (curY += 29) + 4, -1);
                    int listTitleY = curY += 39;
                    g.text(this.font, "\u00a76\u00a7lProfile Binds: \u00a7e" + s.activeProfile, contentX, listTitleY, -22016, true);
                    g.text(this.font, "\u00a7fCommand:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fCombo:", contentX, (curY += 29) + 4, -1);
                    curY += 29;
                    int activeBindsTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Binds", contentX, activeBindsTitleY, -11184641, true);
                    int listY = activeBindsTitleY + 20 - (int)this.scrollAmount;
                    List<BomboConfig.CommandBind> binds = s.profileBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CommandBind bind : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < this.height - 15) {
                                g.text(this.font, "\u00a7e" + bind.keyName + " \u00a77-> \u00a7b/" + bind.command, contentX, listY + 5, -1, false);
                            }
                            listY += 22;
                        }
                    }
                    break;
                }
                                                case 7: {
                    curY += 24;
                    curY = this.drawOptionLabel(g, "§7Highlights Enabled", contentX + 24, curY, -1, false);
                    g.text(this.font, "§fTracer Width: §e" + String.format("%.1f", s.tracerWidth), contentX, curY + 6, -1);
                    curY += 24;
                    curY += 26; // Mode switch button
                    if (s.highlightAdvancedMode) {
                        g.text(this.font, "§fNametag / Regex:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fEntity Type:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fHead Hash:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fMob Size:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fArmor Piece:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fPlayer / Skin:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fIsland / Subarea:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fVisibility:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fColor:", contentX, curY + 6, -1);
                        curY += 24;
                        curY = this.drawOptionLabel(g, "§7Tracer", contentX + 24, curY, -1, false);
                        curY = this.drawOptionLabel(g, "§7Show Title On Spawn", contentX + 24, curY, -1, false);
                        curY = this.drawOptionLabel(g, "§7Play Sound On Spawn", contentX + 24, curY, -1, false);
                        curY += 30; // Button offset
                    } else {
                        g.text(this.font, "§fMob Name:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fIsland:", contentX, curY + 6, -1);
                        curY += 24;
                        g.text(this.font, "§fColor:", contentX, curY + 6, -1);
                        curY += 24;
                        curY = this.drawOptionLabel(g, "§7Tracer", contentX + 24, curY, -1, false);
                        curY += 30; // Button offset
                    }
                    int listTitleY = curY;
                    g.text(this.font, "\u00a79\u00a7lBestiary & Custom Highlights", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 15 - (int)this.scrollAmount;

                    List<String> generalMobs = me.bombo.bomboaddons.features.BestiaryManager.getGeneralHighlights(s.highlights);
                    Map<String, List<String>> groupedBestiary = me.bombo.bomboaddons.features.BestiaryManager.getGroupedBestiary(s.highlights);

                    // 1. General Highlights Header
                    boolean isGeneralCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("General");
                    int generalHeaderY = listY;
                    listY += 24;

                    if (generalHeaderY > listTitleY + 5 && generalHeaderY < this.height - 15) {
                        g.enableScissor(contentX + 26, generalHeaderY, contentX + contentWidth - 215, generalHeaderY + 20);
                        g.text(this.font, "\u00a76\u00a7lGeneral Highlights \u00a77(" + generalMobs.size() + ")", contentX + 26, generalHeaderY + 6, -22016, true);
                        g.disableScissor();
                        g.fill(contentX + 16, generalHeaderY + 21, contentX + contentWidth, generalHeaderY + 22, 0x44888888);
                    }

                    if (!isGeneralCollapsed) {
                        for (String mobName : generalMobs) {
                            int itemY = listY;
                            listY += 22;
                            if (itemY > listTitleY + 5 && itemY < this.height - 15) {
                                BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                                if (info != null) {
                                    String prefix = info.enabled ? "\u00a7e" : "\u00a78\u00a7m";
                                    g.enableScissor(contentX + 24, itemY, contentX + contentWidth - 215, itemY + 20);
                                    g.text(this.font, "  \u00a77• " + prefix + mobName, contentX + 24, itemY + 6, -1, false);
                                    g.disableScissor();
                                }
                                g.fill(contentX + 16, itemY + 21, contentX + contentWidth, itemY + 22, 0x44888888);
                            }
                        }
                    }

                    // Spacer
                    listY += 8;

                    // 2. Bestiary Parent Header
                    int totalBestiaryMobs = 0;
                    for (List<String> list : groupedBestiary.values()) totalBestiaryMobs += list.size();

                    boolean isBestiaryParentCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains("Bestiary");

                    int bestiaryHeaderY = listY;
                    listY += 24;

                    if (bestiaryHeaderY > listTitleY + 5 && bestiaryHeaderY < this.height - 15) {
                        g.enableScissor(contentX + 26, bestiaryHeaderY, contentX + contentWidth - 215, bestiaryHeaderY + 20);
                        g.text(this.font, "\u00a73\u00a7lBestiary \u00a77(" + totalBestiaryMobs + " mobs in " + groupedBestiary.size() + " categories)", contentX + 26, bestiaryHeaderY + 6, -11184641, true);
                        g.disableScissor();
                        g.fill(contentX + 16, bestiaryHeaderY + 21, contentX + contentWidth, bestiaryHeaderY + 22, 0x44888888);
                    }

                    if (!isBestiaryParentCollapsed) {
                        int catIndex = 0;
                        for (Map.Entry<String, List<String>> entry : groupedBestiary.entrySet()) {
                            String cat = entry.getKey();
                            List<String> mobsInCat = entry.getValue();
                            boolean isCollapsed = s.collapsedBestiaryCategories != null && s.collapsedBestiaryCategories.contains(cat);

                            if (catIndex > 0) {
                                listY += 4;
                            }
                            catIndex++;

                            int headerY = listY;
                            listY += 24;

                            if (headerY > listTitleY + 5 && headerY < this.height - 15) {
                                g.enableScissor(contentX + 42, headerY, contentX + contentWidth - 215, headerY + 20);
                                g.text(this.font, "\u00a76\u00a7l" + cat + " \u00a77(" + mobsInCat.size() + ")", contentX + 42, headerY + 6, -22016, true);
                                g.disableScissor();
                                g.fill(contentX + 16, headerY + 21, contentX + contentWidth, headerY + 22, 0x44888888);
                            }

                            if (!isCollapsed) {
                                for (String mobName : mobsInCat) {
                                    int itemY = listY;
                                    listY += 22;
                                    if (itemY > listTitleY + 5 && itemY < this.height - 15) {
                                        BomboConfig.HighlightInfo info = s.highlights.get(mobName);
                                        if (info != null) {
                                            String prefix = info.enabled ? "\u00a7e" : "\u00a78\u00a7m";
                                            g.enableScissor(contentX + 36, itemY, contentX + contentWidth - 215, itemY + 20);
                                            g.text(this.font, "  \u00a77• " + prefix + mobName, contentX + 36, itemY + 6, -1, false);
                                            g.disableScissor();
                                        }
                                        g.fill(contentX + 16, itemY + 21, contentX + contentWidth, itemY + 22, 0x44888888);
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
                case 8: {
                    curY += 24;
                    if (!s.hideCheats) {
                        g.text(this.font, "\u00a77Auto Close", contentX + 24, curY + 4, -1, false);
                        curY += 24;
                    }
                    g.text(this.font, "\u00a77Disable Unequip", contentX + 24, curY + 4, -1, false);
                    curY += 24;
                    for (int i = 0; i < 9; ++i) {
                        g.text(this.font, "\u00a7fSlot " + (i + 1) + ":", contentX, curY, -1);
                        curY += 24;
                    }
                    break;
                }
                case 9: {
                    g.text(this.font, "\u00a76\u00a7lAnvil Auto-Combine", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Auto Combine", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fCombine Delay: \u00a7e" + s.anvilAutoCombineDelay + "ms", contentX, curY += 24, -1);
                    curY = this.drawOptionLabel(g, "\u00a77Require Keybind", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fTrigger Key:", contentX, curY += 24, -1);
                    g.text(this.font, "\u00a79\u00a7lTarget Enchantments", contentX, curY += 44, -11184641, true);
                    int listY = curY + 20 - (int)this.scrollAmount;
                    ArrayList<String> sortedEnchants = new ArrayList<String>(s.anvilAutoCombine.keySet());
                    Collections.sort(sortedEnchants);
                    for (String enc : sortedEnchants) {
                        if (listY > curY + 15 && listY < this.height - 15) {
                            int level = s.anvilAutoCombine.get(enc);
                            g.text(this.font, "\u00a7e" + enc + " \u00a77(Target: " + level + ")", contentX, listY + 5, -1, false);
                        }
                        listY += 22;
                    }
                    break;
                }
                case 10: {
                    curY = contentBaseY + 24 - (int)this.scrollAmount;
                    g.text(this.font, "§c§lDebug Settings", contentX, curY - 24, -43691, true);
                    curY += 26; // Space for Dump button
                    curY = this.drawOptionLabel(g, "§7MASTER DEBUG", contentX + 24, curY, -1, false);
                    curY += 28; // Space for [Enable/Disable All Debug] buttons
                    curY = this.drawOptionLabel(g, "§7Copy Chat Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "§7Chat Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "§7Sounds Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77GUIs Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Entities Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Command Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Debug Mode (Legacy)", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77API Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Pet Price Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77API Chat Messages", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77LB Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Particle Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77NPC Lore Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "§7Composter Debug", contentX + 24, curY, -1, false);

                    curY = this.drawOptionLabel(g, "\u00a77Auto Fishing Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Croesus Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Auto Reconnect Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Performance Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Keys Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Daily Reward Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Display ESP Enabled", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Display Tracers", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fDisplay Color:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fDisplay Size: \u00a7e" + String.format("%.1f", Float.valueOf(BomboConfig.get().displayEspThickness)), contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fDisplay Filter:", contentX, (curY += 29) + 4, -1);
                    break;
                }
                case 11: {
                    g.text(this.font, "\u00a76\u00a7lKuudra Settings", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Blindness Timer", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Disable Blindness", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Perk Menu Clicker", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Auto GFS Toxic", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fToxic Count: \u00a7e" + BomboConfig.get().autoGfsToxicCount, contentX, (curY += 24) + 4, -1);
                    curY = this.drawOptionLabel(g, "\u00a77Auto GFS Twilight", contentX + 24, curY, -1, false);
                    curY += 24;
                    g.text(this.font, "\u00a77Pearl Waypoints & Timers", contentX + 24, (curY += 10) + 4, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Show Pearl Throw Timer", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Show All Pearl Spots", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Show Sky Pearl Spots", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Show Flat Pearl Spots", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Show Double Pearl Spots", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Kuudra Debug Mode", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fTalisman Tier: \u00a7e" + BomboConfig.get().kuudraTalisman, contentX, (curY += 24) + 4, -1);
                    break;
                }
                case 12: {
                    g.text(this.font, "\u00a76\u00a7lPets Settings", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Show Pet Lowest BIN", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a77Disable Unequip", contentX + 24, (curY += 29) + 4, -1, false);
                    curY += 24;
                    for (int i = 0; i < 9; ++i) {
                        String uuid = s.petKeybinds.get(String.valueOf(i + 1));
                        Object boundInfo = "";
                        if (uuid != null && !uuid.isEmpty()) {
                            String petName = s.petNames.get(String.valueOf(i + 1));
                            if (petName != null && !petName.isEmpty()) {
                                Object cleanName = petName.replaceAll("\u00a7.", "");
                                cleanName = ((String)cleanName).replaceAll("\\[Lvl \\d+\\]", "");
                                cleanName = ((String)cleanName).replaceAll("[^a-zA-Z0-9\\s\\-']", "");
                                if (((String)(cleanName = ((String)cleanName).trim())).length() > 14) {
                                    cleanName = ((String)cleanName).substring(0, 14) + "...";
                                }
                                boundInfo = " \u00a77(" + (String)cleanName + ")";
                            } else {
                                boundInfo = " \u00a77(" + (uuid.length() > 6 ? uuid.substring(0, 6) : uuid) + ")";
                            }
                        }
                        g.text(this.font, "\u00a7fSlot " + (i + 1) + (String)boundInfo + ":", contentX, curY + 4, -1);
                        curY += 24;
                    }
                    break;
                }
                case 13: {
                    g.text(this.font, "\u00a76\u00a7lKeybinds Management", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fActive Profile: \u00a7e" + s.activeProfile, contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fCommand:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fCombo:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fOnly on Island:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fOnly with Armor:", contentX, (curY += 29) + 4, -1);
                    curY += 29;
                    int activeBindsTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Binds", contentX, activeBindsTitleY, -11184641, true);
                    int listY = activeBindsTitleY + 20 - (int)this.scrollAmount;
                    List<BomboConfig.CommandBind> binds = s.keybindBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CommandBind bind : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < this.height - 15) {
                                Object extra = "";
                                if (bind.requiredIsland != null && !bind.requiredIsland.isEmpty()) {
                                    extra = (String)extra + " \u00a78[" + bind.requiredIsland + "]";
                                }
                                if (bind.requiredArmor != null && !bind.requiredArmor.isEmpty()) {
                                    extra = (String)extra + " \u00a78(" + bind.requiredArmor + ")";
                                }
                                String statusPrefix = bind.enabled ? "\u00a7a[\u2714] " : "\u00a7c[\u2718] ";
                                g.text(this.font, statusPrefix + "\u00a7e" + bind.keyName + " \u00a77-> \u00a7b/" + bind.command + (String)extra, contentX, listY + 5, -1, false);
                            }
                            listY += 22;
                        }
                    }
                    break;
                }
                case 14: {
                    g.text(this.font, "\u00a76\u00a7lWaypoints Management", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fActive Profile: \u00a7e" + s.activeProfile, contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fName:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fCoords (X Y Z):", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fOnly on Island:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Show Through Walls", contentX + 24, (curY += 29) + 4, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Show Beacon", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fColor:", contentX, curY += 24, -1);
                    curY += 24;
                    int activeWaypointsTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Waypoints", contentX, activeWaypointsTitleY, -11184641, true);
                    int listY = activeWaypointsTitleY + 20 - (int)this.scrollAmount;
                    List<BomboConfig.CustomWaypoint> wps = s.customWaypoints.get(s.activeProfile);
                    if (wps != null) {
                        for (BomboConfig.CustomWaypoint wp : wps) {
                            if (listY > activeWaypointsTitleY + 15 && listY < this.height - 15) {
                                Object extra = "";
                                if (wp.requiredIsland != null && !wp.requiredIsland.isEmpty()) {
                                    extra = (String)extra + " \u00a78[" + wp.requiredIsland + "]";
                                }
                                String statusPrefix = wp.enabled ? "\u00a7a[\u2714] " : "\u00a7c[\u2718] ";
                                String formattedColor = this.getColorFormatting(wp.color);
                                g.text(this.font, statusPrefix + formattedColor + wp.name + " \u00a77-> (" + String.format("%.1f, %.1f, %.1f", wp.x, wp.y, wp.z) + ")" + (String)extra, contentX, listY + 5, -1, false);
                            }
                            listY += 22;
                        }
                    }
                    break;
                }
                case 15: {
                    g.text(this.font, "\u00a76\u00a7lCommand Aliases", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fAlias:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fCommand:", contentX, (curY += 29) + 4, -1);
                    curY += 29;
                    int activeAliasesTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Aliases", contentX, activeAliasesTitleY, -11184641, true);
                    int listY = activeAliasesTitleY + 20 - (int)this.scrollAmount;
                    ArrayList<String> sortedAliases = new ArrayList<String>(s.commandAliases.keySet());
                    Collections.sort(sortedAliases);
                    for (String aliasKey : sortedAliases) {
                        if (listY > activeAliasesTitleY + 15 && listY < this.height - 15) {
                            g.text(this.font, "\u00a7e/" + aliasKey + " \u00a77-> \u00a7b" + s.commandAliases.get(aliasKey), contentX, listY + 5, -1, false);
                        }
                        listY += 22;
                    }
                    break;
                }
                case 16: {
                    g.text(this.font, "\u00a76\u00a7lChat Triggers & Actions", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fActive Profile: \u00a7e" + s.activeProfile, contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fIf Chat Contains:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fRun Command:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fShow Title:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fPlay Sound (TAB):", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fSound Times: \u00a7e" + triggerSoundTimesInput, contentX, (curY += 24) + 4, -1);
                    int activeTriggersTitleY = (curY += 34) + 30;
                    g.text(this.font, "\u00a79\u00a7lActive Triggers", contentX, activeTriggersTitleY, -11184641, true);
                    int listY = activeTriggersTitleY + 20 - (int)this.scrollAmount;
                    List<BomboConfig.ChatTrigger> activeTriggers = s.profileChatTriggers.get(s.activeProfile);
                    if (activeTriggers != null) {
                        for (BomboConfig.ChatTrigger trigger : activeTriggers) {
                            if (listY > activeTriggersTitleY + 15 && listY < this.height - 15) {
                                Object actionText = "";
                                if (trigger.commandToRun != null && !trigger.commandToRun.isEmpty()) {
                                    actionText = (String)actionText + " \u00a77[Cmd: \u00a7b" + trigger.commandToRun + "\u00a77]";
                                }
                                if (trigger.titleToShow != null && !trigger.titleToShow.isEmpty()) {
                                    actionText = (String)actionText + " \u00a77[Title: \u00a7e" + trigger.titleToShow + "\u00a77]";
                                }
                                String statusPrefix = trigger.enabled ? "\u00a7a[\u2714] " : "\u00a7c[\u2718] ";
                                g.text(this.font, statusPrefix + "\u00a7f\"" + trigger.triggerText + "\"" + (String)actionText, contentX, listY + 5, -1, false);
                            }
                            listY += 22;
                        }
                    }
                    break;
                }
                case 17: {
                    g.text(this.font, "\u00a76\u00a7lDungeons Settings", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Croesus Helper", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Dungeon Secrets Tracker", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Dungeon Secrets Debug", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Clear Info HUD", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Pad Timers Purple", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Pad Timers Green", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Dungeon Big Hitbox", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fPurple Timer: \u00a7e" + String.format("%.1fs", s.padTimerPurpleTime), contentX, curY += 24, -1);
                    break;
                }
                case 18: {
                    g.text(this.font, "\u00a76\u00a7lCoord Commands Management", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fActive Profile: \u00a7e" + s.activeProfile, contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fCoords (X Y Z):", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fCommand:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fOnly on Island:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fRadius:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Show Waypoint", contentX + 24, (curY += 29) + 4, -1, false);
                    g.text(this.font, "\u00a7fMin Delay (s):", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fMax Delay (s):", contentX, (curY += 29) + 4, -1);
                    curY += 29;
                    int activeBindsTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Coord Binds", contentX, activeBindsTitleY, -11184641, true);
                    int listY = activeBindsTitleY + 20 - (int)this.scrollAmount;
                    List<BomboConfig.CoordBind> binds = s.coordBinds.get(s.activeProfile);
                    if (binds != null) {
                        for (BomboConfig.CoordBind cb : binds) {
                            if (listY > activeBindsTitleY + 15 && listY < this.height - 15) {
                                Object extra = "";
                                if (cb.requiredIsland != null && !cb.requiredIsland.isEmpty()) {
                                    extra = (String)extra + " \u00a78[" + cb.requiredIsland + "]";
                                }
                                double r = cb.radius <= 0.0 ? 3.0 : cb.radius;
                                extra = (String)extra + " \u00a77(r=" + String.format("%.1f", r) + ")";
                                if (cb.showWaypoint) {
                                    extra = (String)extra + " \u00a7e[WP]";
                                }
                                if (cb.maxDelay > 0.0) {
                                    extra = (String)extra + " \u00a7d[" + String.format("%.1f-%.1fs", cb.minDelay, cb.maxDelay) + "]";
                                }
                                String statusPrefix = cb.enabled ? "\u00a7a[\u2714] " : "\u00a7c[\u2718] ";
                                g.text(this.font, statusPrefix + "(" + String.format("%.1f, %.1f, %.1f", cb.x, cb.y, cb.z) + ") \u00a77-> \u00a7b" + cb.command + (String)extra, contentX, listY + 5, -1, false);
                            }
                            listY += 22;
                        }
                    }
                    break;
                }
                case 19: {
                    int col1X = contentX;
                    int col2X = contentX + contentWidth / 2 + 10;
                    int y1 = contentBaseY - (int)this.scrollAmount;
                    if (structureFinderSubmenu) {
                        g.text(this.font, "§6§lStructure Finder Settings", col1X, y1, -22016, true);
                        y1 += 52;
                        y1 = this.drawOptionLabel(g, "§7Corleone 1", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Golden Dragon", col1X + 24, y1, -1, false);
                        y1 = this.drawOptionLabel(g, "§7Structure Tracers", col1X + 24, y1, -1, false);
                        break;
                    }
                    g.text(this.font, "§6§lCorpse ESP Settings", col1X, y1, -22016, true);
                    y1 += 24;
                    y1 = this.drawOptionLabel(g, "§7Corpse ESP Enabled", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "§7Hide Opened Corpses", col1X + 24, y1, -1, false);
                    y1 += 29;
                    y1 = this.drawOptionLabel(g, "§7Golden Dragon Nest Finder", col1X + 24, y1, -1, false);
                    if (!s.hideCheats) {
                        y1 = this.drawOptionLabel(g, "§7Structure Finder (Right Click)", col1X + 24, y1, -1, false);
                    }
                    y1 = this.drawOptionLabel(g, "§7Dwarven Red Carpets", col1X + 24, y1, -1, false);
                    int y2 = contentBaseY - (int)this.scrollAmount;
                    g.text(this.font, "§6§lCorpse Colors", col2X, y2, -22016, true);
                    break;
                }
                case 20: {
                    int col1X = contentX;
                    int y1 = contentBaseY;
                    g.text(this.font, "\u00a76\u00a7lParty Commands Config", col1X, y1, -22016, true);
                    y1 = this.drawOptionLabel(g, "\u00a77Toggle !timer command", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "\u00a77Toggle !warp command (runs /party warp)", col1X + 24, y1, -1, false);
                    y1 = this.drawOptionLabel(g, "\u00a77Toggle !psa command (runs /party settings allinvite)", col1X + 24, y1, -1, false);
                    g.text(this.font, "\u00a7fPrefixes (e.g. !,.,?):", col1X, (y1 += 34) + 4, -1);
                    int col2X = contentX + contentWidth / 2 + 10;
                    int y2 = contentBaseY;
                    g.text(this.font, "\u00a76\u00a7lCustom Party Commands", col2X, y2, -22016, true);
                    g.text(this.font, "\u00a7fTrigger:", col2X, (y2 += 24) + 4, -1);
                    g.text(this.font, "\u00a7fRun Command:", col2X, (y2 += 24) + 4, -1);
                    y2 += 24;
                    int listStartY = y2 += 25;
                    g.text(this.font, "\u00a79\u00a7lActive Custom Commands", col2X, listStartY, -11184641, true);
                    int listY = listStartY + 20 - (int)this.scrollAmount;
                    for (BomboConfig.CustomPartyCommand cmd : s.customPartyCommands) {
                        if (listY > listStartY + 15 && listY < this.height - 15) {
                            String[] splits;
                            String statusPrefix = cmd.enabled ? "\u00a7a[\u2714] " : "\u00a7c[\u2718] ";
                            String firstPrefix = "!";
                            if (s.partyCommandPrefixes != null && !s.partyCommandPrefixes.trim().isEmpty() && (splits = s.partyCommandPrefixes.split(",")).length > 0 && !splits[0].trim().isEmpty()) {
                                firstPrefix = splits[0].trim();
                            }
                            String label = statusPrefix + "\u00a7f" + firstPrefix + cmd.triggerText + " \u00a77-> \u00a7b" + cmd.commandToRun;
                            int maxLabelWidth = contentWidth / 2 - 130;
                            Object displayLabel = label;
                            if (this.font.width((String)displayLabel) > maxLabelWidth) {
                                while (this.font.width((String)displayLabel + "...") > maxLabelWidth && ((String)displayLabel).length() > 0) {
                                    displayLabel = ((String)displayLabel).substring(0, ((String)displayLabel).length() - 1);
                                }
                                displayLabel = (String)displayLabel + "...";
                            }
                            g.text(this.font, (String)displayLabel, col2X, listY + 5, -1, false);
                        }
                        listY += 22;
                    }
                    break;
                }
                case 21: {
                    g.text(this.font, "\u00a76\u00a7lAdd Block Highlight", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Block Highlights Enabled", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fBlock Scan Radius: \u00a7e" + s.blockScanRadius + " \u00a77blocks", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fBlock Name/ID:", contentX, (curY += 34) + 4, -1);
                    g.text(this.font, "\u00a7fColor:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a77See Through Walls", contentX + 24, (curY += 29) + 4, -1, false);
                    curY += 24;
                    int listTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Block Highlights", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 20 - (int)this.scrollAmount;
                    ArrayList<String> sortedBlocks = new ArrayList<String>(s.blockHighlights.keySet());
                    Collections.sort(sortedBlocks);
                    for (String bName : sortedBlocks) {
                        if (listY > listTitleY + 15 && listY < this.height - 15) {
                            BomboConfig.BlockHighlightInfo info = s.blockHighlights.get(bName);
                            String prefix = info.enabled ? "\u00a7e" : "\u00a78\u00a7m";
                            String color = info.color;
                            String twText = info.throughWalls ? " \u00a77(X-Ray)" : " \u00a78(Depth)";
                            g.text(this.font, prefix + bName + " \u00a77- " + this.getColorFormatting(color) + color + twText, contentX, listY + 5, -1, false);
                        }
                        listY += 22;
                    }
                    break;
                }
                case 22: {
                    g.text(this.font, "\u00a76\u00a7lAdd Particle Highlight", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Particle Highlights Enabled", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fParticle Name:", contentX, (curY += 34) + 4, -1);
                    g.text(this.font, "\u00a7fColor:", contentX, (curY += 29) + 4, -1);
                    curY += 29;
                    int listTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Particle Highlights", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 20 - (int)this.scrollAmount;
                    ArrayList<String> sortedParticles = new ArrayList<String>(s.particleHighlights.keySet());
                    Collections.sort(sortedParticles);
                    for (String pName : sortedParticles) {
                        if (listY > listTitleY + 15 && listY < this.height - 15) {
                            BomboConfig.HighlightInfo info = s.particleHighlights.get(pName);
                            String prefix = info.enabled ? "\u00a7e" : "\u00a78\u00a7m";
                            String color = info.color;
                            g.text(this.font, prefix + pName + " \u00a77- " + this.getColorFormatting(color) + color, contentX, listY + 5, -1, false);
                        }
                        listY += 22;
                    }
                    break;
                }
                case 23: {
                    g.text(this.font, "\u00a76\u00a7lBedwars ESP Settings", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Bedwars ESP Enabled", contentX + 24, curY, -1, false);
                    curY = this.drawOptionLabel(g, "\u00a77Highlight Own Team", contentX + 24, curY, -1, false);
                    break;
                }
                case 24: {
                    g.enableScissor(contentX - 10, 56, this.width, this.height - 40);
                    int textY = contentBaseY - (int)this.scrollAmount;
                    g.text(this.font, "\u00a76\u00a7lFishing Settings", contentX, textY, -22016, true);
                    textY = this.drawOptionLabel(g, "\u00a77Auto Fishing Enabled", contentX + 24, textY, -1, false);
                    g.text(this.font, "\u00a77Show Bobber Time", contentX + 24, (textY += 29) + 4, -1, false);
                    g.text(this.font, "\u00a7fMin Delay (ms): \u00a7a" + s.autoFishingMinDelay, contentX, (textY += 29) + 4, -1);
                    g.text(this.font, "\u00a7fMax Delay (ms): \u00a7a" + s.autoFishingMaxDelay, contentX, (textY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Slug Mode Enabled", contentX + 24, (textY += 29) + 4, -1, false);
                    g.text(this.font, "\u00a7fSlug Min Bobber Time (s): \u00a7a" + String.format("%.1f", Float.valueOf(s.autoFishingSlugDelay)), contentX, (textY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Trophy Fish Highlight", contentX + 24, (textY += 29) + 4, -1, false);
                    g.text(this.font, "\u00a77Swap on Catch Enabled", contentX + 24, (textY += 29) + 4, -1, false);
                    if (s.autoFishingSwapEnabled) {
                        g.text(this.font, "\u00a7fWeapon Name / Slot:", contentX, (textY += 29) + 4, -1);
                        g.text(this.font, "\u00a7fAttack Click Count: \u00a7a" + s.autoFishingClickCount, contentX, (textY += 29) + 4, -1);
                        g.text(this.font, "\u00a7fAttack Click Type:", contentX, (textY += 29) + 4, -1);
                        g.text(this.font, "\u00a7fMin Attack Click Delay (ms): \u00a7a" + s.autoFishingClickDelayMin, contentX, (textY += 29) + 4, -1);
                        g.text(this.font, "\u00a7fMax Attack Click Delay (ms): \u00a7a" + s.autoFishingClickDelayMax, contentX, (textY += 29) + 4, -1);
                    }
                    g.text(this.font, "\u00a7fStop Chat Trigger:", contentX, (textY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Auto Fishing Debug Logs", contentX + 24, (textY += 29) + 4, -1, false);
                    g.disableScissor();
                    break;
                }
                case 25: {
                    String[] presetNames2;
                    BomboConfig.CrosshairSettings crosshair = BomboConfig.get().customCrosshair;
                    g.text(this.font, "\u00a76\u00a7lCustom Crosshair", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Enable Custom Crosshair", contentX + 24, curY, -1, false);
                    curY += 24;
                    g.text(this.font, "\u00a77Chroma", contentX + 24, (curY += 5) + 4, -1, false);
                    curY += 24;
                    if (!crosshair.chroma) {
                        g.text(this.font, "\u00a7fColor:", contentX, curY + 4, -1, false);
                        curY += 24;
                    }
                    g.text(this.font, "\u00a77Outline", contentX + 24, curY + 4, -1, false);
                    curY += 24;
                    if (crosshair.outline) {
                        g.text(this.font, "\u00a7fOutline Color:", contentX, curY + 4, -1, false);
                        curY += 24;
                    }
                    g.text(this.font, "\u00a7ePresets:", contentX, (curY += 5) + 4, -1, false);
                    int ppx = contentX;
                    int startPx = contentX;
                    int pCount = 0;
                    int pCurY = curY += 29;
                    for (String name : presetNames2 = new String[]{"Dot", "Plus", "Lg Plus", "Sm Plus", "Circle", "Op Circle", "Square", "F Square", "Target", "F Target", "Arrow", "Cross", "Sm Cross", "T Shape", "Caret", "Hash", "Clear"}) {
                        if (pCount > 0 && pCount % 6 == 0) {
                            pCurY += 25;
                            ppx = startPx;
                        }
                        boolean[] pGrid = this.getPresetGrid(name);
                        int miniX = ppx + 22 - 8;
                        int miniY = pCurY + 2;
                        for (int r = 0; r < 15; ++r) {
                            for (int c = 0; c < 15; ++c) {
                                if (!pGrid[r * 15 + c]) continue;
                                g.fill(miniX + c, miniY + r, miniX + c + 1, miniY + r + 1, -1);
                            }
                        }
                        ppx += 50;
                        ++pCount;
                    }
                    curY = pCurY + 25;
                    g.text(this.font, "\u00a7aDraw your crosshair (L=Draw R=Erase):", contentX, curY, -1, false);
                    int gridSize = 10;
                    int gridStartX = contentX + (contentWidth - 15 * gridSize) / 2;
                    int gridStartY = curY += 15;
                    for (int r = 0; r < 15; ++r) {
                        for (int c = 0; c < 15; ++c) {
                            int color;
                            int idx = r * 15 + c;
                            int px = gridStartX + c * gridSize;
                            int py = gridStartY + r * gridSize;
                            int n = color = crosshair.grid[idx] ? -11141291 : -11184811;
                            if (!crosshair.grid[idx] && r == 7 && c == 7) {
                                color = -8947849;
                            }
                            if (mouseX >= px && mouseX < px + gridSize && mouseY >= py && mouseY < py + gridSize) {
                                color = -1;
                            }
                            g.fill(px, py, px + gridSize, py + gridSize, color);
                            g.fill(px + 1, py + 1, px + gridSize - 1, py + gridSize - 1, crosshair.grid[idx] ? -16711936 : (r == 7 && c == 7 ? -13421773 : -14540254));
                        }
                    }
                    break;
                }
                case 26: {
                    int listStartY;
                    if (s.customSlots == null || s.customSlots.isEmpty()) break;
                    int yOffset = listStartY = categoryTitleY + 30 + 144 + 60;
                    g.fill(contentX - 5, yOffset, contentX + contentWidth + 5, this.height - 8, -1442840576);
                    g.text(this.font, "\u00a7e\u00a7lCustom Slots", contentX, yOffset + 5, -1, true);
                    g.enableScissor(contentX - 5, yOffset + 20, contentX + contentWidth + 5, this.height - 8);
                    for (int i = 0; i < s.customSlots.size(); ++i) {
                        BomboConfig.CustomSlot cs = s.customSlots.get(i);
                        int itemY = yOffset + 20 + i * 22 - (int)this.scrollAmount;
                        g.text(this.font, "\u00a7b" + cs.guiName + " \u00a78[Slot " + cs.slotIndex + "]", contentX, itemY + 5, -1, true);
                        g.text(this.font, "\u00a77Cmd: " + (cs.command != null ? cs.command : ""), contentX, itemY + 15, -1, true);
                    }
                    g.disableScissor();
                    break;
                }
                case 27: {
                    int listTitleY = categoryTitleY + 30 + 130;
                    if (editingCustomTracer != null) {
                        listTitleY += 50;
                    }
                    g.text(this.font, "\u00a79\u00a7lCustom Tracers", contentX, listTitleY, -11184641, true);
                    ArrayList<String> tracerIds = new ArrayList<String>(s.customTracers.keySet());
                    for (int i = 0; i < tracerIds.size(); ++i) {
                        String tid = (String)tracerIds.get(i);
                        int itemY = listTitleY + 20 + i * 22 - (int)this.scrollAmount;
                        if (itemY <= listTitleY + 15 || itemY >= this.height - 20) continue;
                        BomboConfig.Settings.CustomTracerInfo info = s.customTracers.get(tid);
                        String nameText = info.name != null && !info.name.isEmpty() ? info.name : "Unknown";
                        g.text(this.font, "\u00a77Name: \u00a7e" + nameText + " \u00a78[" + tid + "]", contentX, itemY + 10, -1, true);
                        int cHex = BomboRenderUtils.colorNameToHex(info.color);
                        g.fill(contentX - 10, itemY + 8, contentX - 5, itemY + 18, cHex | 0xFF000000);
                    }
                    break;
                }
                case 28: {
                    g.text(this.font, "\u00a76\u00a7lAdd Item Highlight", contentX, curY, -22016, true);
                    curY = this.drawOptionLabel(g, "\u00a77Item Highlights Enabled", contentX + 24, curY, -1, false);
                    g.text(this.font, "\u00a7fTarget Name:", contentX, (curY += 34) + 4, -1);
                    g.text(this.font, "\u00a7fColor:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Show Invis", contentX + 24, (curY += 29) + 4, -1, false);
                    curY += 29;
                    int listTitleY = curY += 35;
                    g.text(this.font, "\u00a79\u00a7lActive Item Highlights", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 20 - (int)this.scrollAmount;
                    ArrayList<String> sortedMobs = new ArrayList<String>(s.itemHighlights.keySet());
                    Collections.sort(sortedMobs);
                    for (int i = 0; i < sortedMobs.size(); ++i) {
                        String mName = (String)sortedMobs.get(i);
                        int itemY = listY;
                        if (itemY > listTitleY + 15 && itemY < this.height - 20) {
                            BomboConfig.HighlightInfo info = s.itemHighlights.get(mName);
                            String text = mName + " \u00a77(" + info.color + ")";
                            if (!info.enabled) {
                                text = "\u00a78" + mName + " (" + info.color + ")";
                            }
                            g.text(this.font, text, contentX + 24, itemY + 6, -1, false);
                        }
                        listY += 24;
                    }
                    break;
                }
                case 29: {
                    g.text(this.font, "\u00a76\u00a7lOrdered Waypoints", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a77Import a list of coordinates or clear the active route.", contentX, curY += 34, -5592406, false);
                    curY += 48;
                    curY += 24;
                    int listTitleY = curY += 24;
                    g.text(this.font, "\u00a79\u00a7lActive Route", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 20 - (int)this.scrollAmount;
                    List<OrderedWaypoints.Waypoint> wps = OrderedWaypoints.getWaypoints();
                    if (wps.isEmpty()) {
                        if (listY > listTitleY + 15 && listY < this.height - 20) {
                            g.text(this.font, "\u00a7cNo waypoints loaded.", contentX + 24, listY + 6, -1, false);
                        }
                    } else {
                        for (int i = 0; i < wps.size(); ++i) {
                            int itemY = listY;
                            if (itemY > listTitleY + 15 && itemY < this.height - 20) {
                                OrderedWaypoints.Waypoint wp = wps.get(i);
                                String color = i == OrderedWaypoints.currentWpIndex ? "\u00a7a" : "\u00a77";
                                String name = wp.name != null ? wp.name : "WP " + (i + 1);
                                String text = color + (i + 1) + ". " + name + " \u00a78[" + (int)wp.position.x + ", " + (int)wp.position.y + ", " + (int)wp.position.z + "]";
                                g.text(this.font, text, contentX + 24, itemY + 6, -1, false);
                            }
                            listY += 24;
                        }
                    }
                    break;
                }
                case 30: {
                    g.text(this.font, "\u00a76\u00a7lCustom Timers", contentX, curY, -22016, true);
                    curY += 24;
                    g.text(this.font, "\u00a77Name", contentX, (curY += 34) - 10, -1, false);
                    g.text(this.font, "\u00a77Time (e.g. 2h, 5m)", contentX + 130, curY - 10, -1, false);
                    g.text(this.font, "\u00a77Trigger Text", contentX, (curY += 34) - 10, -1, false);
                    g.text(this.font, "\u00a77Item ID", contentX + 160, curY - 10, -1, false);
                    g.text(this.font, "\u00a77Enabled", contentX + 24, (curY += 34) + 8, -1, false);
                    g.text(this.font, "\u00a77Show Only When Ready", contentX + 114, curY + 8, -1, false);
                    g.text(this.font, "\u00a77Keep Ready", contentX + 264, curY + 8, -1, false);
                    curY += 24;
                    int activeTimersTitleY = curY += 8;
                    g.text(this.font, "\u00a79\u00a7lConfigured Timers", contentX, activeTimersTitleY, -11184641, true);
                    int listY = activeTimersTitleY + 20 - (int)this.scrollAmount;
                    for (int i = 0; i < s.customTimers.size(); ++i) {
                        if (listY > activeTimersTitleY + 15 && listY < this.height - 15) {
                            BomboConfig.CustomTimerDef def = s.customTimers.get(i);
                            g.enableScissor(contentX, listY, contentX + contentWidth - 145, listY + 24);
                            g.text(this.font, "\u00a7e" + def.name + " \u00a77- " + def.timeStr + " \u00a77- Triggers on: \u00a7b" + def.triggerText, contentX, listY + 7, -1, true);
                            g.disableScissor();
                        }
                        listY += 24;
                    }
                    break;
                }
                case 31: {
                    g.text(this.font, "\u00a76\u00a7lTab Widgets Manager", contentX, curY, -22016, true);
                    g.text(this.font, "\u00a7fWidget Name:", contentX, (curY += 24) + 4, -1);
                    g.text(this.font, "\u00a7fIsland Only:", contentX, (curY += 29) + 4, -1);
                    g.text(this.font, "\u00a77Enabled", contentX + 24, (curY += 29) + 4, -1, false);
                    curY += 29;
                    int listTitleY = curY += 30;
                    g.text(this.font, "\u00a79\u00a7lConfigured Tab Widgets", contentX, listTitleY, -11184641, true);
                    int listY = listTitleY + 20 - (int)this.scrollAmount;
                    for (int i = 0; i < s.tabWidgets.size(); ++i) {
                        if (listY > listTitleY + 15 && listY < this.height - 15) {
                            BomboConfig.TabWidgetInfo info = s.tabWidgets.get(i);
                            String statusStr = info.enabled ? "\u00a7a[ON]" : "\u00a7c[OFF]";
                            String islandStr = info.island.equalsIgnoreCase("All") ? "\u00a77[All Islands]" : "\u00a7b[" + info.island + "]";
                            g.enableScissor(contentX, listY, contentX + contentWidth - 100, listY + 24);
                            g.text(this.font, statusStr + " \u00a7e" + info.name + " " + islandStr, contentX, listY + 5, -1, true);
                            g.disableScissor();
                        }
                        listY += 24;
                    }
                    break;
                }
                case 32: {
                    int col1X = contentX;
                    int y1 = contentBaseY - (int)this.scrollAmount;
                    g.text(this.font, "\u00a76\u00a7lChat Modifier Rules", col1X, y1, -22016, true);
                    g.text(this.font, "\u00a77Create customizable rules to replace or hide chat messages (Regex supported).", col1X, y1 + 50, -1, false);
                    g.text(this.font, "\u00a77Total active rules: \u00a7e" + ChatModifier.rules.size(), col1X, y1 + 65, -1, false);
                    break;
                }
            }
        }
        catch (Throwable e) {
            Bomboaddons.LOGGER.error("[BomboAddons] Error during render!", e);
            try {
                File file = new File("crash_exception.log");
                try (PrintWriter pw = new PrintWriter(new FileWriter(file, true));){
                    pw.println("=== GUI RENDER EXCEPTION ===");
                    e.printStackTrace(pw);
                    pw.println("============================");
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }

    public boolean isPauseScreen() {
        return false;
    }

    private List<Integer> parseCombo(String combo) {
        String[] parts;
        ArrayList<Integer> codes = new ArrayList<Integer>();
        for (String p : parts = combo.toLowerCase().split("\\+")) {
            int code = ClickLogic.getKeyCode(p.trim());
            if (code == -1) continue;
            codes.add(code);
        }
        return codes;
    }


    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        long window = Minecraft.getInstance().getWindow().handle();
        boolean isCtrl = event.hasControlDown() || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (selectedCategory == 7 && isCtrl) {
            if (keyCode == 90 || keyCode == 122 || keyCode == 309 || keyCode == GLFW.GLFW_KEY_Z) {
                if (undoHighlight()) {
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §a✔ Undone highlight change! (Ctrl+Z)"));
                    }
                    this.init();
                    return true;
                } else {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §eNo highlight actions in undo history."));
                    }
                }
            } else if (keyCode == 89 || keyCode == 121 || keyCode == 310 || keyCode == GLFW.GLFW_KEY_Y) {
                if (redoHighlight()) {
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.2F));
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §a✔ Redone highlight change! (Ctrl+Y)"));
                    }
                    this.init();
                    return true;
                } else {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §eNo highlight actions in redo history."));
                    }
                }
            }
        }
        if (activeSoundBox != null && activeSoundBox.isFocused() && !soundSuggestions.isEmpty()) {
            if (keyCode == 258) { // TAB -> cycle to next option
                selectedSoundSuggestion = (selectedSoundSuggestion + 1) % soundSuggestions.size();
                String selected = soundSuggestions.get(selectedSoundSuggestion);
                isTabCyclingSound = true;
                activeSoundBox.setValue(selected);
                triggerSoundInput = selected;
                isTabCyclingSound = false;
                return true;
            } else if (keyCode == 264) { // Down arrow
                selectedSoundSuggestion = (selectedSoundSuggestion + 1) % soundSuggestions.size();
                String selected = soundSuggestions.get(selectedSoundSuggestion);
                isTabCyclingSound = true;
                activeSoundBox.setValue(selected);
                triggerSoundInput = selected;
                isTabCyclingSound = false;
                return true;
            } else if (keyCode == 265) { // Up arrow
                selectedSoundSuggestion = (selectedSoundSuggestion - 1 + soundSuggestions.size()) % soundSuggestions.size();
                String selected = soundSuggestions.get(selectedSoundSuggestion);
                isTabCyclingSound = true;
                activeSoundBox.setValue(selected);
                triggerSoundInput = selected;
                isTabCyclingSound = false;
                return true;
            } else if (keyCode == 257 || keyCode == 335) { // ENTER
                String selected = soundSuggestions.get(selectedSoundSuggestion);
                isTabCyclingSound = true;
                activeSoundBox.setValue(selected);
                triggerSoundInput = selected;
                isTabCyclingSound = false;
                soundSuggestions.clear();
                return true;
            } else if (keyCode == 256) { // ESCAPE
                soundSuggestions.clear();
                return true;
            }
        }
        for (EditBox box : this.activeBoxes) {
            if (box != null && box.isFocused()) {
                if (box.keyPressed(event)) return true;
            }
        }
        if (colorPickerTarget != null && keyCode == 256) {
            colorPickerTarget = null;
            this.init();
            return true;
        }
        if (!listeningForKeyTarget.isEmpty()) {
            if (listeningForKeyTarget.equals("profileCombo")) {
                if (keyCode == 256) {
                    bindComboInput = "";
                    listeningForKeyTarget = "";
                    recordedComboKeys.clear();
                    this.init();
                    return true;
                }
                Object keyName = ClickLogic.getKeyName(keyCode);
                if (((String)keyName).equals("unknown")) {
                    if (keyCode >= 320 && keyCode <= 329) {
                        keyName = "kp_" + (keyCode - 320);
                    } else {
                        keyName = GLFW.glfwGetKeyName((int)keyCode, (int)0);
                        if (keyName == null) {
                            keyName = "key_" + keyCode;
                        }
                    }
                }
                if (!recordedComboKeys.contains(keyName)) {
                    recordedComboKeys.add((String)keyName);
                }
                bindComboInput = String.join((CharSequence)"+", recordedComboKeys);
                this.init();
                return true;
            }
            if (keyCode == 256) {
                this.updateKeyTarget("");
            } else {
                Object keyName = ClickLogic.getKeyName(keyCode);
                if (((String)keyName).equals("unknown")) {
                    if (keyCode >= 320 && keyCode <= 329) {
                        keyName = "kp_" + (keyCode - 320);
                    } else {
                        keyName = GLFW.glfwGetKeyName((int)keyCode, (int)0);
                        if (keyName == null) {
                            keyName = "key_" + keyCode;
                        }
                    }
                }
                this.updateKeyTarget((String)keyName);
            }
            listeningForKeyTarget = "";
            BomboConfig.save();
            this.init();
            return true;
        }
        return super.keyPressed(event);
    }

    private void handleCrosshairDraw(double mx, double my, int button) {
        if (button == 0 || button == 1) {
            int contentWidth = this.width - 130 - 24;
            int gridSize = 10;
            int contentX = 146;
            int gridStartX = contentX + (contentWidth - 15 * gridSize) / 2;
            BomboConfig.CrosshairSettings crosshair = BomboConfig.get().customCrosshair;
            int curY = 86;
            curY += 24;
            curY += 24;
            curY += 5;
            curY += 24;
            if (!crosshair.chroma) {
                curY += 24;
            }
            curY += 24;
            if (crosshair.outline) {
                curY += 24;
            }
            curY += 5;
            curY += 29;
            curY += 75;
            int top = curY += 15;
            int left = gridStartX;
            if (mx >= (double)left && mx < (double)(left + 150) && my >= (double)top && my < (double)(top + 150)) {
                int col = (int)(mx - (double)left) / 10;
                int row = (int)(my - (double)top) / 10;
                if (col >= 0 && col < 16 && row >= 0 && row < 16) {
                    crosshair.grid[row * 15 + col] = button == 0;
                    BomboConfig.save();
                }
            }
        }
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (selectedCategory == 25 && BomboConfig.get().customCrosshair != null && BomboConfig.get().customCrosshair.enabled) {
            this.handleCrosshairDraw(event.x(), event.y(), event.button());
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (activeSoundBox != null && activeSoundBox.isFocused() && !soundSuggestions.isEmpty() && activeSoundBox.getY() > 0) {
            int bx = activeSoundBox.getX();
            int bw = activeSoundBox.getWidth();
            int by = activeSoundBox.getY();
            int listH = Math.min(soundSuggestions.size() * 14 + 4, 120);
            int listY = by + 18;
            if (listY + listH > this.height - 20) {
                listY = by - listH - 2;
            }
            if (mouseX >= (double)bx && mouseX <= (double)(bx + bw) && mouseY >= (double)listY && mouseY <= (double)(listY + listH)) {
                int clickedIdx = (int)((mouseY - (double)listY - 2) / 14);
                if (clickedIdx >= 0 && clickedIdx < soundSuggestions.size()) {
                    String selected = soundSuggestions.get(clickedIdx);
                    isTabCyclingSound = true;
                    activeSoundBox.setValue(selected);
                    triggerSoundInput = selected;
                    isTabCyclingSound = false;
                    soundSuggestions.clear();
                    return true;
                }
            }
        }
        int button;
        double my;
        double mx;
        if (selectedCategory == 25 && event.button() <= 1 && BomboConfig.get().customCrosshair != null && BomboConfig.get().customCrosshair.enabled) {
            this.handleCrosshairDraw(event.x(), event.y(), event.button());
        }
        if (event.button() == 1) {
            mx = event.x();
            my = event.y();
            if (selectedCategory == 0 && loreAdditionsX != -1 && mx >= (double)loreAdditionsX && mx <= (double)(loreAdditionsX + loreAdditionsW) && my >= (double)loreAdditionsY && my <= (double)(loreAdditionsY + loreAdditionsH)) {
                loreAdditionsSubmenu = true;
                configSearchTerm = "";
                this.scrollAmount = 0.0;
                this.init();
                return true;
            }
            if (selectedCategory == 0 && frozenBlazeWarnX != -1 && mx >= (double)frozenBlazeWarnX && mx <= (double)(frozenBlazeWarnX + frozenBlazeWarnW) && my >= (double)frozenBlazeWarnY && my <= (double)(frozenBlazeWarnY + frozenBlazeWarnH)) {
                frozenBlazeWarnSubmenu = true;
                configSearchTerm = "";
                this.scrollAmount = 0.0;
                this.init();
                return true;
            }
            if (selectedCategory == 0 && partyCommandsX != -1 && mx >= (double)partyCommandsX && mx <= (double)(partyCommandsX + partyCommandsWidth) && my >= (double)partyCommandsY && my <= (double)(partyCommandsY + partyCommandsHeight)) {
                selectedCategory = this.categories.indexOf("Party Settings");
                this.scrollAmount = 0.0;
                this.init();
                return true;
            }
            if (selectedCategory == 19 && structureFinderX != -1 && mx >= (double)structureFinderX && mx <= (double)(structureFinderX + structureFinderW) && my >= (double)structureFinderY && my <= (double)(structureFinderY + structureFinderH)) {
                structureFinderSubmenu = true;
                configSearchTerm = "";
                this.scrollAmount = 0.0;
                this.init();
                return true;
            }
        }
        if (event.button() == 0 && selectedCategory == 1) {
            mx = event.x();
            my = event.y();
            int contentWidth = this.width - 130 - 24;
            int col2X = 146 + contentWidth / 2 + 10;
            int contentBaseY = 86;
            int pickerY = contentBaseY + 48 + 20;
            int svSize = 80;
            if (mx >= (double)col2X && mx <= (double)(col2X + svSize) && my >= (double)pickerY && my <= (double)(pickerY + svSize)) {
                isDraggingSv = true;
                return true;
            }
            if (mx >= (double)(col2X + 90) && mx <= (double)(col2X + 102) && my >= (double)pickerY && my <= (double)(pickerY + svSize)) {
                isDraggingHue = true;
                return true;
            }
            if (mx >= (double)(col2X + 110) && mx <= (double)(col2X + 122) && my >= (double)pickerY && my <= (double)(pickerY + svSize)) {
                isDraggingAlpha = true;
                return true;
            }
        }
        if (!listeningForKeyTarget.isEmpty() && (button = event.button()) != 0) {
            String keyName = "mouse" + (button + 1);
            if (listeningForKeyTarget.equals("profileCombo")) {
                if (!recordedComboKeys.contains(keyName)) {
                    recordedComboKeys.add(keyName);
                }
                bindComboInput = String.join((CharSequence)"+", recordedComboKeys);
                this.init();
                return true;
            }
            this.updateKeyTarget(keyName);
            listeningForKeyTarget = "";
            BomboConfig.save();
            this.init();
            return true;
        }
        return super.mouseClicked(event, handled);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        isDraggingSv = false;
        isDraggingHue = false;
        isDraggingAlpha = false;
        BomboConfig.save();
        return super.mouseReleased(event);
    }

    public void tick() {
        super.tick();
        if (listeningForKeyTarget.equals("profileCombo") && !recordedComboKeys.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            long window = mc.getWindow().handle();
            boolean anyDown = false;
            for (String keyName : recordedComboKeys) {
                int code = ClickLogic.getKeyCode(keyName);
                if (code == -1 || !ClickLogic.isCodeDown(window, mc.getWindow(), code)) continue;
                anyDown = true;
                break;
            }
            if (!anyDown) {
                listeningForKeyTarget = "";
                recordedComboKeys.clear();
                this.init();
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < 130.0) {
            this.categoryScrollAmount = Math.max(0.0, this.categoryScrollAmount - vertical * 15.0);
        } else {
            double newScroll = this.scrollAmount - vertical * 15.0;
            this.scrollAmount = Math.max(0.0, Math.min(newScroll, 1000.0));
        }
        this.init();
        return true;
    }

    private void updateKeyTarget(String keyName) {
        BomboConfig.Settings s = BomboConfig.get();
        if (listeningForKeyTarget.startsWith("wardrobe")) {
            try {
                int index = Integer.parseInt(listeningForKeyTarget.substring(8));
                s.wardrobeKeys.set(index, keyName);
            }
            catch (Exception index) {
                // empty catch block
            }
            return;
        }
        if (listeningForKeyTarget.startsWith("pets")) {
            try {
                int index = Integer.parseInt(listeningForKeyTarget.substring(4));
                s.petKeys.set(index, keyName);
            }
            catch (Exception exception) {
                // empty catch block
            }
            return;
        }
        switch (listeningForKeyTarget) {
            case "trade": {
                s.tradeKey = keyName;
                break;
            }
            case "countItem": {
                s.countItemKey = keyName;
                break;
            }
            case "recipe": {
                s.recipeKey = keyName;
                break;
            }
            case "textureToggle": {
                s.textureToggleKey = keyName;
                break;
            }
            case "usage": {
                s.usageKey = keyName;
                break;
            }
            case "showItem": {
                s.showItemKey = keyName;
                break;
            }
            case "copyNbt": {
                s.copyNbtKey = keyName;
                break;
            }
            case "gfsMax": {
                s.gfsMaxKey = keyName;
                break;
            }
            case "gfsStack": {
                s.gfsStackKey = keyName;
                break;
            }
            case "saveInventory": {
                s.saveInventoryKey = keyName;
                break;
            }
            case "chatPeek": {
                s.chatPeekKey = keyName;
                break;
            }
            case "nextPage": {
                s.nextPageKey = keyName;
                break;
            }
            case "prevPage": {
                s.prevPageKey = keyName;
                break;
            }
            case "goBack": {
                s.goBackKey = keyName;
                break;
            }
            case "smartBack": {
                s.smartGoBackKey = keyName;
                break;
            }
            case "clicker": {
                clickKeyInput = keyName;
                break;
            }
            case "gardenF": {
                s.gardenForwardKey = keyName;
                break;
            }
            case "gardenB": {
                s.gardenBackwardKey = keyName;
                break;
            }
            case "gardenL": {
                s.gardenLeftKey = keyName;
                break;
            }
            case "gardenR": {
                s.gardenRightKey = keyName;
                break;
            }
            case "gardenBr": {
                s.gardenBreakKey = keyName;
                break;
            }
            case "gardenU": {
                s.gardenUseKey = keyName;
                break;
            }
            case "anvilTrigger": {
                s.anvilAutoCombineKey = keyName;
                break;
            }
            case "savePet": {
                s.savePetKey = keyName;
                break;
            }
            case "clipboardRun": {
                s.clipboardRunKey = keyName;
                break;
            }
            case "freelook": {
                s.freelookKey = keyName;
                break;
            }
            case "bestiaryHighlight": {
                s.bestiaryHighlightKey = keyName;
                break;
            }
            case "itemListFocus": {
                s.itemListFocusKey = keyName;
                break;
            }
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
        float var_R = (float)r / 255.0f;
        float var_G = (float)g / 255.0f;
        float var_B = (float)b / 255.0f;
        float min = Math.min(var_R, Math.min(var_G, var_B));
        float max = Math.max(var_R, Math.max(var_G, var_B));
        float del_Max = max - min;
        float h = 0.0f;
        float s = 0.0f;
        float v = max;
        if (del_Max != 0.0f) {
            s = del_Max / max;
            float del_R = ((max - var_R) / 6.0f + del_Max / 2.0f) / del_Max;
            float del_G = ((max - var_G) / 6.0f + del_Max / 2.0f) / del_Max;
            float del_B = ((max - var_B) / 6.0f + del_Max / 2.0f) / del_Max;
            if (var_R == max) {
                h = del_B - del_G;
            } else if (var_G == max) {
                h = 0.33333334f + del_R - del_B;
            } else if (var_B == max) {
                h = 0.6666667f + del_G - del_R;
            }
            if (h < 0.0f) {
                h += 1.0f;
            }
            if (h > 1.0f) {
                h -= 1.0f;
            }
        }
        return new float[]{h, s, v};
    }

    public static int hsvToRgb(float h, float s, float v) {
        int r = 0;
        int g = 0;
        int b = 0;
        if (s == 0.0f) {
            g = b = (int)(v * 255.0f + 0.5f);
            r = b;
        } else {
            float h_h = (h - (float)Math.floor(h)) * 6.0f;
            float f = h_h - (float)Math.floor(h_h);
            float p = v * (1.0f - s);
            float q = v * (1.0f - s * f);
            float t = v * (1.0f - s * (1.0f - f));
            switch ((int)h_h) {
                case 0: {
                    r = (int)(v * 255.0f + 0.5f);
                    g = (int)(t * 255.0f + 0.5f);
                    b = (int)(p * 255.0f + 0.5f);
                    break;
                }
                case 1: {
                    r = (int)(q * 255.0f + 0.5f);
                    g = (int)(v * 255.0f + 0.5f);
                    b = (int)(p * 255.0f + 0.5f);
                    break;
                }
                case 2: {
                    r = (int)(p * 255.0f + 0.5f);
                    g = (int)(v * 255.0f + 0.5f);
                    b = (int)(t * 255.0f + 0.5f);
                    break;
                }
                case 3: {
                    r = (int)(p * 255.0f + 0.5f);
                    g = (int)(q * 255.0f + 0.5f);
                    b = (int)(v * 255.0f + 0.5f);
                    break;
                }
                case 4: {
                    r = (int)(t * 255.0f + 0.5f);
                    g = (int)(p * 255.0f + 0.5f);
                    b = (int)(v * 255.0f + 0.5f);
                    break;
                }
                case 5: {
                    r = (int)(v * 255.0f + 0.5f);
                    g = (int)(p * 255.0f + 0.5f);
                    b = (int)(q * 255.0f + 0.5f);
                }
            }
        }
        return r << 16 | g << 8 | b;
    }



    private boolean[] getPresetGrid(String name) {
        int index = 0;
        String[] presetNames = new String[]{"Dot", "Plus", "Lg Plus", "Sm Plus", "Circle", "Op Circle", "Square", "F Square", "Target", "F Target", "Arrow", "Cross", "Sm Cross", "T Shape", "Caret", "Hash", "Clear"};
        for (int i = 0; i < presetNames.length; ++i) {
            if (!presetNames[i].equals(name)) continue;
            index = i;
            break;
        }
        if (index == 16) {
            return new boolean[225];
        }
        boolean[] result = new boolean[225];
        if (index < CrosshairPresets.PRESETS.length) {
            System.arraycopy(CrosshairPresets.PRESETS[index], 0, result, 0, 225);
        }
        return result;
    }
}

