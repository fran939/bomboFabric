package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BomboConfig {
    private static final Path OLD_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons.json");
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons.json");
    
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(HighlightInfo.class, new HighlightInfoAdapter())
            .create();

    private static Settings instance = new Settings();

    public static void load() {
        if (Files.exists(OLD_CONFIG_PATH)) {
            try {
                if (!Files.exists(CONFIG_PATH.getParent())) {
                    Files.createDirectories(CONFIG_PATH.getParent());
                }
                Files.move(OLD_CONFIG_PATH, CONFIG_PATH, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                com.google.gson.JsonElement jsonElement = com.google.gson.JsonParser.parseReader(reader);
                if (jsonElement.isJsonObject()) {
                    com.google.gson.JsonObject jsonObject = jsonElement.getAsJsonObject();
                    if (jsonObject.has("tooltipBgColor")) {
                        com.google.gson.JsonElement bgEl = jsonObject.get("tooltipBgColor");
                        if (bgEl.isJsonPrimitive() && bgEl.getAsJsonPrimitive().isString()) {
                            jsonObject.remove("tooltipBgColor");
                        }
                    }
                    if (jsonObject.has("tooltipBorderColor")) {
                        com.google.gson.JsonElement borderEl = jsonObject.get("tooltipBorderColor");
                        if (borderEl.isJsonPrimitive() && borderEl.getAsJsonPrimitive().isString()) {
                            jsonObject.remove("tooltipBorderColor");
                        }
                    }
                    Settings loaded = GSON.fromJson(jsonObject, Settings.class);
                    if (loaded != null) {
                        instance = loaded;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Migration logic for legacy fields
        if (instance.commandBinds != null && !instance.commandBinds.isEmpty()) {
            instance.profileBinds.putIfAbsent("default", new ArrayList<>());
            instance.profileBinds.get("default").addAll(instance.commandBinds);
            instance.commandBinds.clear();
            instance.commandBinds = null;
            save();
        }
        if (instance.autoGfsToxicTwilight) {
            instance.autoGfsToxic = true;
            instance.autoGfsTwilight = true;
            instance.autoGfsToxicTwilight = false;
            save();
        }
        if (instance.customWaypoints == null) {
            instance.customWaypoints = new HashMap<>();
        }
        if (instance.customItemOverrides == null) {
            instance.customItemOverrides = new HashMap<>();
        }
        if (instance.commandCycles == null) {
            instance.commandCycles = new HashMap<>();
        }
        if (instance.commandCycleIndices == null) {
            instance.commandCycleIndices = new HashMap<>();
        }
        if (instance.getTargets == null) {
            instance.getTargets = new HashMap<>();
        }
        if (!instance.profileBinds.containsKey("General")) {
            instance.profileBinds.put("General", new ArrayList<>());
        }
        if (!instance.keybindBinds.containsKey("General")) {
            instance.keybindBinds.put("General", new ArrayList<>());
        }
        if (!instance.customWaypoints.containsKey("General")) {
            instance.customWaypoints.put("General", new ArrayList<>());
        }
        if (instance.coordBinds == null) {
            instance.coordBinds = new HashMap<>();
        }
        if (!instance.coordBinds.containsKey("General")) {
            instance.coordBinds.put("General", new ArrayList<>());
        }
        if (instance.petNames == null) {
            instance.petNames = new HashMap<>();
        }
        if (instance.commandAliases == null) {
            instance.commandAliases = new HashMap<>();
        }
        
        if (instance.customCrosshair == null) {
            instance.customCrosshair = new CrosshairSettings();
        }
        if (instance.customCrosshair.grid == null || instance.customCrosshair.grid.length != 225) {
            instance.customCrosshair.grid = new boolean[225];
        }
        if (instance.profileChatTriggers == null) {
            instance.profileChatTriggers = new HashMap<>();
        }
        if (!instance.profileChatTriggers.containsKey("General")) {
            instance.profileChatTriggers.put("General", new ArrayList<>());
        }
        if (instance.chatTriggers != null && !instance.chatTriggers.isEmpty()) {
            instance.profileChatTriggers.putIfAbsent("default", new ArrayList<>());
            instance.profileChatTriggers.get("default").addAll(instance.chatTriggers);
            instance.chatTriggers.clear();
            instance.chatTriggers = null;
            save();
        }
        if (instance.customPartyCommands == null) {
            instance.customPartyCommands = new ArrayList<>();
        }
        if (instance.customTracers == null) {
            instance.customTracers = new HashMap<>();
        }
        if (instance.partyCommandPrefixes == null) {
            instance.partyCommandPrefixes = "!,.,?";
        }
        if (instance.clipboardRunKey == null) {
            instance.clipboardRunKey = "";
        }
        if (instance.blockHighlights == null) {
            instance.blockHighlights = new HashMap<>();
        }
        if (instance.particleHighlights == null) {
            instance.particleHighlights = new HashMap<>();
        }
        if (instance.wardrobeKeys != null) {
            while (instance.wardrobeKeys.size() < 12) {
                instance.wardrobeKeys.add("");
            }
        }
    }

    public static void save() {
        try {
            if (!Files.exists(CONFIG_PATH.getParent())) {
                Files.createDirectories(CONFIG_PATH.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Settings get() {
        return instance;
    }

    static {
        load();
    }

    
    public static class CrosshairSettings {
        public boolean enabled = false;
        public boolean[] grid = new boolean[225];
        public String color = "WHITE";
        public boolean chroma = false;
        public boolean outline = true;
        public String outlineColor = "BLACK";
        public float scale = 1.0f;
    }

    public static class Settings {
        public boolean signCalculator = false;
        public boolean chestClicker = false;
        public boolean autoClicker = false;
        public boolean sbeCommands = false;
        public boolean leftClickEtherwarp = false;
        public int signCalcX = -1;
        public int signCalcY = -1;
        public int storageGuiCols = 9;
        public int storageGuiRows = 5;
        public boolean autoExperiments = false;
        public int experimentClickDelay = 200;
        public int experimentClickType = 1; // 0=Left, 1=Middle, 2=Shift
        public boolean experimentAutoClose = false;
        public int experimentSerumCount = 0;
        public boolean experimentGetMaxXp = false;
        public boolean sphinxMacro = false;
        public String chatPeekKey = "y";
        public String tradeKey = "n";
        public String countItemKey = "";
        public String recipeKey = "r";
        public String usageKey = "u";
        public String showItemKey = "o";
        public String copyNbtKey = "p";
        public String gfsMaxKey = "k";
        public String gfsStackKey = "l";
        public String nextPageKey = "";
        public String prevPageKey = "";
        public String goBackKey = "";
        public String smartGoBackKey = "";
        public String textureToggleKey = "";
        public Map<String, HighlightInfo> highlights = new HashMap<>();
        public boolean highlightsEnabled = false;
        public boolean debugMaster = false;
        public boolean debugChat = false;
        public boolean debugGuis = false;
        public boolean debugEntities = false;
        public boolean debugCommands = false;
        public boolean debugMode = false;
        public boolean apiDebug = false;
        public boolean apiChatMessages = false;
        public boolean copyChat = false;
        public boolean lbDebug = false;
        public boolean debugParticles = false;
        public boolean petPriceDebug = false;
        public List<CommandBind> commandBinds = null;
        public String activeProfile = "default";
        public Map<String, List<CommandBind>> profileBinds = new HashMap<>();
        public Map<String, List<CommandBind>> keybindBinds = new HashMap<>();
        public boolean hollowWandClickThrough = false;
        public boolean hollowWandAutoCombine = false;
        public boolean autoAcceptCarnival = false;
        public boolean autoTrevorQuest = false;
        public boolean ignoreCapsLock = false;
        public boolean serverListButton = false;
        public boolean reconnectButton = false;
        public boolean hideCheats = false;
        public boolean diceTracker = false;
        public int diceHudX = 10;
        public int diceHudY = 50;
        
        public boolean feastBakeryHud = false;
        public boolean quickJoinCommands = false;
        public int feastBakeryHudX = 10;
        public int feastBakeryHudY = 100;
        
        public boolean rngProfitHud = false;
        public int rngProfitHudX = 10;
        public int rngProfitHudY = 200;
        public int rngProfitHudOpacity = 80;
        
        public boolean gardenMovement = false;
        public boolean lockMouseOnGarden = false;
        public boolean gardenSugarCane = false;
        public boolean gardenDirectionHelper = false;
        public boolean gardenMacroCheckDetector = false;
        public boolean gardenMacroCheckStop = false;

        public String gardenMacroCheckSound = "Anvil";
        public int gardenMacroCheckSoundCount = 10;
        public int gardenMacroCheckSoundDelay = 500;
        public String gardenForwardKey = "up";
        public String gardenBackwardKey = "down";
        public String gardenLeftKey = "left";
        public String gardenRightKey = "right";
        public String gardenBreakKey = "b";
        public String gardenUseKey = "u";
        
        public boolean lowestBin = false;
        public boolean npcPrice = false;
        public Map<String, String> calculatorAliases = new HashMap<>();
        public boolean pestEsp = false;
        public boolean pestSpawnWaypoint = false;
        public int pestWaypointDuration = 0;
        public boolean pestWaypointRemoveOnNear = false;
        public boolean pestWaypointBeacon = false;
        public boolean pestEspTracer = false;
        public boolean cheeseTracer = false;
        public String pestEspColor = "yellow";
        public float pestEspThickness = 2.0f;
        public boolean fuckDiorite = false;
        public boolean fuckDioritePillarColor = false;
        public String fuckDioriteColor = "None";
        public boolean hitbox = false;
        
        public boolean autoCloseWardrobe = false;
        public boolean disableUnequipWardrobe = false;
        public List<String> wardrobeKeys = new ArrayList<>(java.util.Arrays.asList("", "", "", "", "", "", "", "", "", "", "", ""));
        public Map<String, Integer> anvilAutoCombine = new HashMap<>();
        public boolean anvilAutoCombineEnabled = false;
        public int anvilAutoCombineDelay = 200;
        public String anvilAutoCombineKey = "";
        public boolean anvilAutoCombineRequireKey = false;
        public boolean autoReconnect = false;
        public Map<String, String> petKeybinds = new HashMap<>();
        public Map<String, String> petNames = new HashMap<>();
        public List<String> petKeys = new ArrayList<>(java.util.Arrays.asList("", "", "", "", "", "", "", "", ""));
        public String savePetKey = "";
        public boolean disableUnequipPet = false;
        
        public boolean showPetLowestBin = false;
        public boolean itemListEnabled = true;
        public boolean itemListRemoveBackground = false;
        public boolean itemListColoredBackground = false;
        public boolean itemListLocked = false;
        public boolean itemListSortReverse = false;
        public int itemListSortType = 0;
        public boolean itemListHideSkins = false;
        public boolean itemListHideNPCs = false;
        public boolean itemListHideMobs = false;
        public boolean itemListHideVanilla = false;
        public boolean autoHideItemList = false;
        public boolean itemListSeparateSearch = false;
        public boolean itemListSearchAlwaysVisible = false;
        public int itemListSearchX = -1;
        public int itemListSearchY = -1;
        public int itemListSearchW = 150;
        public float itemListSearchScale = 1.0f;
        public CrosshairSettings customCrosshair = new CrosshairSettings();

        public boolean trophyHighlight = false;
        public boolean customTimeEnabled = false;
        public boolean disableInventoryEffects = false;

        public boolean tracerTestMode = false;
        public boolean tracerTestAllEntities = false;
        public int itemListX = -1;
        public int itemListY = -1;
        public int itemListW = 150;
        public int itemListH = 200;

        public boolean tracerRat = true;
        public boolean tracerWorm = true;
        public boolean tracerSlug = true;
        public boolean tracerFly = true;
        public boolean tracerLocust = true;
        public boolean tracerBeetle = true;
        public boolean tracerCricket = true;
        public boolean tracerSpider = true;
        public boolean tracerMoth = true;
        public boolean tracerMite = true;
        public boolean tracerMouse = true;
        public boolean tracerMosquito = true;

        public List<CustomSlot> customSlots = new ArrayList<>();
        public int customTimeHour = 12;
        public boolean corpseEspStyleTracer = false;
        public String customSlotPrefillKey = "";
        public String freelookKey = "";
        public boolean tracerLapis = true;
        public boolean tracerTungsten = true;
        public boolean tracerUmber = true;
        public boolean tracerVanguard = true;
        public String diceDisplayMode = "Current";
        public boolean dungeonSecretsTracker = false;
        public boolean dungeonSecretsDebug = false;
        public boolean clearInfoHud = false;
        public int clearInfoHudX = 10;
        public int clearInfoHudY = 100;
        public boolean kuudraBlindnessTimer = false;
        public boolean disableBlindness = false;
        public int kuudraBlindnessTimerX = 10;
        public int kuudraBlindnessTimerY = 150;
        
        public float diceHudScale = 1.0f;
        public float feastBakeryHudScale = 1.0f;
        public float rngProfitHudScale = 1.0f;
        public float kuudraBlindnessTimerScale = 1.0f;
        public boolean perkMenuClicker = false;
        public boolean autoGfsToxicTwilight = false;
        public boolean autoGfsToxic = false;
        public int autoGfsToxicCount = 21;
        public boolean autoGfsTwilight = false;
 
        public boolean pearlCalculator = false;
        public boolean showTimer = false;
        public boolean showAll = false;
        public boolean showSkyPearls = false;
        public boolean showFlatPearls = false;
        public boolean showDoublePearls = false;
        public int kuudraTalisman = 3;
        public int kuudraTiers = 5;
        public boolean kuudraDebug = false;
        public Map<String, List<CustomWaypoint>> customWaypoints = new HashMap<>();
        public Map<String, List<String>> commandCycles = new HashMap<>();
        public Map<String, Integer> commandCycleIndices = new HashMap<>();
        public Map<String, GetTarget> getTargets = new HashMap<>();
        public Map<String, String> commandAliases = new HashMap<>();
        public List<ChatTrigger> chatTriggers = null;
        public Map<String, List<ChatTrigger>> profileChatTriggers = new HashMap<>();
        
        public static class CustomTracerInfo {
            public String name = "";
            public String color = "green";
            public CustomTracerInfo() {}
            public CustomTracerInfo(String name, String color) {
                this.name = name;
                this.color = color;
            }
        }
        public Map<String, CustomTracerInfo> customTracers = new HashMap<>();
        public boolean ircChatEnabled = false;
        public boolean ircDefaultChat = false;
        public String ircCustomFormat = "";
        public boolean padTimersPurple = false;
        public boolean padTimersGreen = false;
        public int padTimersX = 10;
        public int padTimersY = 250;
        public float padTimersScale = 1.0f;
        public double padTimerPurpleTime = 4.8;
        public boolean eggFinder = false;
        public boolean eggFinderChat = false;
        public boolean eggFinderBeacon = false;
        public boolean eggFinderThroughWalls = false;
        public boolean dungeonBigHitbox = false;
        public Map<String, List<CoordBind>> coordBinds = new HashMap<>();

        public boolean corpseEsp = false;
        public boolean hideOpenedCorpses = false;
        public String corpseEspStyle = "Outline";
        public String lapisOutlineColor = "BLUE";
        public String lapisFillColor = "BLUE";
        public String tungstenOutlineColor = "WHITE";
        public String tungstenFillColor = "WHITE";
        public String umberOutlineColor = "GOLD";
        public String umberFillColor = "GOLD";
        public String vanguardOutlineColor = "LIGHT_PURPLE";
        public String vanguardFillColor = "LIGHT_PURPLE";

        public boolean customTimerHudEnabled = false;
        public int customTimerHudX = 10;
        public int customTimerHudY = 300;
        public float customTimerHudScale = 1.0f;
        public boolean partyCommandsEnabled = false;
        public boolean partyCommandTimer = false;
        public boolean partyCommandWarp = false;
        public boolean partyCommandPsa = false;
        public String partyCommandPrefixes = "!,.,?";
        public String clipboardRunKey = "";
        public List<CustomPartyCommand> customPartyCommands = new ArrayList<>();
        public boolean bypassResourcePack = false;
        public boolean noResourcePack = false;
        public boolean disableCustomTooltips = false;
        public Map<String, CustomItemOverride> customItemOverrides = new java.util.HashMap<>();
        public boolean customTooltipBg = false;
        public int tooltipBgColor = 0xF0100010;
        public int tooltipBorderColor = 0x505000FF;
        public int tooltipAlpha = 240;
        public boolean hypixelShortcutButton = false;
        public boolean smartDisconnect = false;
        public Map<String, BlockHighlightInfo> blockHighlights = new HashMap<>();
        public boolean blockHighlightsEnabled = false;
        public Map<String, HighlightInfo> particleHighlights = new HashMap<>();
        public boolean particleHighlightsEnabled = false;
        public boolean bedwarsEsp = false;
        public boolean bedwarsEspOwnTeam = false;
        public boolean borderlessFullscreen = false;
    }

    public static class CustomItemOverride {
        public String material = "";
        public String name = "";

        public CustomItemOverride() {}

        public CustomItemOverride(String material, String name) {
            this.material = material;
            this.name = name;
        }
    }

    public static class CustomPartyCommand {
        public String triggerText = "";
        public String commandToRun = "";
        public boolean enabled = true;

        public CustomPartyCommand() {}

        public CustomPartyCommand(String triggerText, String commandToRun, boolean enabled) {
            this.triggerText = triggerText;
            this.commandToRun = commandToRun;
            this.enabled = enabled;
        }
    }

    public static class ChatTrigger {
        public String triggerText = "";
        public String commandToRun = "";
        public String titleToShow = "";
        public boolean enabled = true;

        public ChatTrigger() {}

        public ChatTrigger(String triggerText, String commandToRun, String titleToShow) {
            this.triggerText = triggerText;
            this.commandToRun = commandToRun;
            this.titleToShow = titleToShow;
        }
    }


    public static class CommandBind {
        public String command;
        public List<Integer> keyCodes;
        public String keyName;
        public String requiredIsland = "";
        public String requiredArmor = "";
        public boolean enabled = true;

        public CommandBind() {}

        public CommandBind(String command, List<Integer> keyCodes, String keyName) {
            this.command = command;
            this.keyCodes = keyCodes;
            this.keyName = keyName;
        }

        public CommandBind(String command, List<Integer> keyCodes, String keyName, String requiredIsland, String requiredArmor) {
            this.command = command;
            this.keyCodes = keyCodes;
            this.keyName = keyName;
            this.requiredIsland = requiredIsland;
            this.requiredArmor = requiredArmor;
        }
    }

    public static class CustomWaypoint {
        public String name;
        public double x;
        public double y;
        public double z;
        public String requiredIsland = "";
        public boolean showThroughWalls = true;
        public boolean showBeacon = true;
        public String color = "AQUA";
        public boolean enabled = true;
        public String category = "Imported";
        public boolean ordered = false;
        public transient boolean selected = false;

        public CustomWaypoint() {
        }

        public CustomWaypoint(String name, double x, double y, double z, String requiredIsland,
                boolean showThroughWalls, boolean showBeacon, String color, String category) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requiredIsland = requiredIsland;
            this.showThroughWalls = showThroughWalls;
            this.showBeacon = showBeacon;
            this.color = color;
            this.category = category;
        }

        public CustomWaypoint(String name, double x, double y, double z, String requiredIsland,
                boolean showThroughWalls, boolean showBeacon, String color) {
            this(name, x, y, z, requiredIsland, showThroughWalls, showBeacon, color, "Imported");
        }
    }

    public static class CustomSlot {
        public String guiName;
        public int slotIndex;
        public String icon;
        public String name;
        public String description;
        public String command;

        public CustomSlot() {}

        public CustomSlot(String guiName, int slotIndex, String icon, String name, String description, String command) {
            this.guiName = guiName;
            this.slotIndex = slotIndex;
            this.icon = icon;
            this.name = name;
            this.description = description;
            this.command = command;
        }
    }

    public static class CoordBind {
        public String command = "";
        public double x;
        public double y;
        public double z;
        public String requiredIsland = "";
        public boolean enabled = true;
        public double radius = 3.0;
        public boolean showWaypoint = false;
        public double minDelay = 0.0;
        public double maxDelay = 0.0;
        public transient boolean wasInside = false;

        public CoordBind() {}

        public CoordBind(String command, double x, double y, double z, String requiredIsland) {
            this.command = command;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requiredIsland = requiredIsland;
        }

        public CoordBind(String command, double x, double y, double z, String requiredIsland, double radius) {
            this.command = command;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requiredIsland = requiredIsland;
            this.radius = radius;
        }

        public CoordBind(String command, double x, double y, double z, String requiredIsland, double radius, boolean showWaypoint, double minDelay, double maxDelay) {
            this.command = command;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requiredIsland = requiredIsland;
            this.radius = radius;
            this.showWaypoint = showWaypoint;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }
    }

    public static class GetTarget {
        public String itemId;
        public int targetAmount;

        public GetTarget() {}

        public GetTarget(String itemId, int targetAmount) {
            this.itemId = itemId;
            this.targetAmount = targetAmount;
        }
    }

    public static class HighlightInfo {
        public String color = "RED";
        public boolean showInvisible = false;
        public boolean enabled = true;
        public boolean tracer = false;

        public HighlightInfo() {
        }  public HighlightInfo(String color, boolean showInvisible) {
            this.color = color;
            this.showInvisible = showInvisible;
            this.enabled = true;
        }

        public HighlightInfo(String color, boolean showInvisible, boolean enabled) {
            this.color = color;
            this.showInvisible = showInvisible;
            this.enabled = enabled;
        }

        public HighlightInfo(String color, boolean showInvisible, boolean enabled, boolean tracer) {
            this.color = color;
            this.showInvisible = showInvisible;
            this.enabled = enabled;
            this.tracer = tracer;
        }
    }

    public static class BlockHighlightInfo {
        public String color;
        public boolean throughWalls;
        public boolean enabled = true;

        public BlockHighlightInfo() {}

        public BlockHighlightInfo(String color, boolean throughWalls) {
            this.color = color;
            this.throughWalls = throughWalls;
            this.enabled = true;
        }

        public BlockHighlightInfo(String color, boolean throughWalls, boolean enabled) {
            this.color = color;
            this.throughWalls = throughWalls;
            this.enabled = enabled;
        }
    }

    public static class HighlightInfoAdapter extends TypeAdapter<HighlightInfo> {
        public void write(JsonWriter out, HighlightInfo value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.beginObject();
                out.name("color").value(value.color);
                out.name("showInvisible").value(value.showInvisible);
                out.name("enabled").value(value.enabled);
                out.endObject();
            }
        }

        public HighlightInfo read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else if (in.peek() == JsonToken.STRING) {
                return new HighlightInfo(in.nextString(), false, true);
            } else {
                HighlightInfo info = new HighlightInfo();
                info.enabled = true;
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    if (name.equals("color")) {
                        info.color = in.nextString();
                    } else if (name.equals("showInvisible")) {
                        info.showInvisible = in.nextBoolean();
                    } else if (name.equals("enabled")) {
                        info.enabled = in.nextBoolean();
                    } else {
                        in.skipValue();
                    }
                }
                in.endObject();
                return info;
            }
        }
    }
}
