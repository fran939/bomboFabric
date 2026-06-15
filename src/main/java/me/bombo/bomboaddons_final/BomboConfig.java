package me.bombo.bomboaddons_final;

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
                Settings loaded = GSON.fromJson(reader, Settings.class);
                if (loaded != null) {
                    instance = loaded;
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

    public static class Settings {
        public boolean signCalculator = false;
        public boolean chestClicker = false;
        public boolean autoClicker = false;
        public boolean sbeCommands = false;
        public boolean leftClickEtherwarp = false;
        public int signCalcX = -1;
        public int signCalcY = -1;
        public boolean autoExperiments = false;
        public int experimentClickDelay = 200;
        public int experimentClickType = 1; // 0=Left, 1=Middle, 2=Shift
        public boolean experimentAutoClose = false;
        public int experimentSerumCount = 0;
        public boolean experimentGetMaxXp = false;
        public boolean sphinxMacro = false;
        public String chatPeekKey = "y";
        public String tradeKey = "n";
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
        public List<CommandBind> commandBinds = null;
        public String activeProfile = "default";
        public Map<String, List<CommandBind>> profileBinds = new HashMap<>();
        public Map<String, List<CommandBind>> keybindBinds = new HashMap<>();
        public boolean hollowWandClickThrough = false;
        public boolean hollowWandAutoCombine = false;
        public boolean autoAcceptCarnival = false;
        public boolean ignoreCapsLock = false;
        public boolean serverListButton = false;
        public boolean reconnectButton = false;
        public boolean hideCheats = true;
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
        public String pestEspColor = "yellow";
        public float pestEspThickness = 2.0f;
        public boolean fuckDiorite = false;
        public boolean fuckDioritePillarColor = true;
        public String fuckDioriteColor = "None";
        public boolean hitbox = false;
        
        public boolean autoCloseWardrobe = false;
        public boolean disableUnequipWardrobe = false;
        public List<String> wardrobeKeys = new ArrayList<>(java.util.Arrays.asList("", "", "", "", "", "", "", "", ""));
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
        public boolean ircChatEnabled = true;
        public boolean ircDefaultChat = false;
        public String ircCustomFormat = "";
        public boolean padTimersPurple = false;
        public boolean padTimersGreen = false;
        public int padTimersX = 10;
        public int padTimersY = 250;
        public float padTimersScale = 1.0f;
        public double padTimerPurpleTime = 4.8;
        public boolean eggFinder = false;
        public boolean eggFinderChat = true;
        public boolean eggFinderBeacon = true;
        public boolean eggFinderThroughWalls = true;
        public boolean dungeonBigHitbox = false;
        public Map<String, List<CoordBind>> coordBinds = new HashMap<>();

        public boolean corpseEsp = false;
        public boolean hideOpenedCorpses = true;
        public String corpseEspStyle = "Outline";
        public String lapisOutlineColor = "BLUE";
        public String lapisFillColor = "BLUE";
        public String tungstenOutlineColor = "WHITE";
        public String tungstenFillColor = "WHITE";
        public String umberOutlineColor = "GOLD";
        public String umberFillColor = "GOLD";
        public String vanguardOutlineColor = "LIGHT_PURPLE";
        public String vanguardFillColor = "LIGHT_PURPLE";
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

        public CustomWaypoint() {}

        public CustomWaypoint(String name, double x, double y, double z, String requiredIsland, boolean showThroughWalls, boolean showBeacon, String color) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requiredIsland = requiredIsland;
            this.showThroughWalls = showThroughWalls;
            this.showBeacon = showBeacon;
            this.color = color;
        }
    }

    public static class CoordBind {
        public String command = "";
        public double x;
        public double y;
        public double z;
        public String requiredIsland = "";
        public boolean enabled = true;
        public transient boolean wasInside = false;

        public CoordBind() {}

        public CoordBind(String command, double x, double y, double z, String requiredIsland) {
            this.command = command;
            this.x = x;
            this.y = y;
            this.z = z;
            this.requiredIsland = requiredIsland;
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
        public String color;
        public boolean showInvisible;
        public boolean enabled = true;

        public HighlightInfo() {}

        public HighlightInfo(String color, boolean showInvisible) {
            this.color = color;
            this.showInvisible = showInvisible;
            this.enabled = true;
        }

        public HighlightInfo(String color, boolean showInvisible, boolean enabled) {
            this.color = color;
            this.showInvisible = showInvisible;
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
