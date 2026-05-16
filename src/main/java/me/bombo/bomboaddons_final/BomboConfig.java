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
        public boolean signCalculator = true;
        public boolean chestClicker = true;
        public boolean autoClicker = true;
        public boolean sbeCommands = true;
        public boolean leftClickEtherwarp = false;
        public int signCalcX = -1;
        public int signCalcY = -1;
        public boolean autoExperiments = true;
        public int experimentClickDelay = 200;
        public int experimentClickType = 1; // 0=Left, 1=Middle, 2=Shift
        public boolean experimentAutoClose = true;
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
        public boolean highlightsEnabled = true;
        public boolean debugMaster = false;
        public boolean debugChat = false;
        public boolean debugGuis = false;
        public boolean debugEntities = false;
        public boolean debugCommands = false;
        public boolean debugMode = false;
        public boolean apiDebug = false;
        public List<CommandBind> commandBinds = null;
        public String activeProfile = "default";
        public Map<String, List<CommandBind>> profileBinds = new HashMap<>();
        public boolean hollowWandClickThrough = false;
        public boolean hollowWandAutoCombine = false;
        public boolean autoAcceptCarnival = false;
        public boolean ignoreCapsLock = true;
        public boolean serverListButton = true;
        public boolean diceTracker = true;
        public int diceHudX = 10;
        public int diceHudY = 50;
        
        public boolean feastBakeryHud = true;
        public boolean quickJoinCommands = true;
        public int feastBakeryHudX = 10;
        public int feastBakeryHudY = 100;
        
        public boolean gardenMovement = false;
        public boolean gardenSugarCane = false;
        public String gardenForwardKey = "up";
        public String gardenBackwardKey = "down";
        public String gardenLeftKey = "left";
        public String gardenRightKey = "right";
        public String gardenBreakKey = "b";
        public String gardenUseKey = "u";
        
        public boolean lowestBin = true;
        public boolean npcPrice = true;
        public Map<String, String> calculatorAliases = new HashMap<>();
        public boolean pestEsp = false;
        public boolean pestEspTracer = false;
        public String pestEspColor = "yellow";
        public float pestEspThickness = 2.0f;
        public boolean hitbox = false;
        
        public boolean autoCloseWardrobe = true;
        public boolean disableUnequipWardrobe = true;
        public List<String> wardrobeKeys = new ArrayList<>(java.util.Arrays.asList("", "", "", "", "", "", "", "", ""));
        public Map<String, Integer> anvilAutoCombine = new HashMap<>();
        public boolean anvilAutoCombineEnabled = true;
        public int anvilAutoCombineDelay = 200;
        public String anvilAutoCombineKey = "";
        public boolean anvilAutoCombineRequireKey = false;
    }

    public static class CommandBind {
        public String command;
        public List<Integer> keyCodes;
        public String keyName;

        public CommandBind() {}

        public CommandBind(String command, List<Integer> keyCodes, String keyName) {
            this.command = command;
            this.keyCodes = keyCodes;
            this.keyName = keyName;
        }
    }

    public static class HighlightInfo {
        public String color;
        public boolean showInvisible;

        public HighlightInfo() {}

        public HighlightInfo(String color, boolean showInvisible) {
            this.color = color;
            this.showInvisible = showInvisible;
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
                out.endObject();
            }
        }

        public HighlightInfo read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            } else if (in.peek() == JsonToken.STRING) {
                return new HighlightInfo(in.nextString(), false);
            } else {
                HighlightInfo info = new HighlightInfo();
                in.beginObject();
                while (in.hasNext()) {
                    String name = in.nextName();
                    if (name.equals("color")) {
                        info.color = in.nextString();
                    } else if (name.equals("showInvisible")) {
                        info.showInvisible = in.nextBoolean();
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
