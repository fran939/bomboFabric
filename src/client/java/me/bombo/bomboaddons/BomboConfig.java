package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public class BomboConfig {
   private static final Path OLD_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons.json");
   private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().registerTypeAdapter(HighlightInfo.class, new HighlightInfoAdapter()).create();
   private static Settings instance = new Settings();

   public static void load() {
      if (Files.exists(OLD_CONFIG_PATH, new LinkOption[0])) {
         try {
            if (!Files.exists(CONFIG_PATH.getParent(), new LinkOption[0])) {
               Files.createDirectories(CONFIG_PATH.getParent());
            }

            Files.move(OLD_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      if (Files.exists(CONFIG_PATH, new LinkOption[0])) {
         try {
            Reader reader = Files.newBufferedReader(CONFIG_PATH);

            try {
               JsonElement jsonElement = JsonParser.parseReader(reader);
               if (jsonElement.isJsonObject()) {
                  JsonObject jsonObject = jsonElement.getAsJsonObject();
                  if (jsonObject.has("tooltipBgColor")) {
                     JsonElement bgEl = jsonObject.get("tooltipBgColor");
                     if (bgEl.isJsonPrimitive() && bgEl.getAsJsonPrimitive().isString()) {
                        jsonObject.remove("tooltipBgColor");
                     }
                  }

                  if (jsonObject.has("tooltipBorderColor")) {
                     JsonElement borderEl = jsonObject.get("tooltipBorderColor");
                     if (borderEl.isJsonPrimitive() && borderEl.getAsJsonPrimitive().isString()) {
                        jsonObject.remove("tooltipBorderColor");
                     }
                  }

                  Settings loaded = (Settings)GSON.fromJson(jsonObject, Settings.class);
                  if (loaded != null) {
                     instance = loaded;
                     System.out.println("[BomboConfig] Config loaded from " + CONFIG_PATH);
                  }
               }
            } catch (Throwable var7) {
               if (reader != null) {
                  try {
                     reader.close();
                  } catch (Throwable var5) {
                     var7.addSuppressed(var5);
                  }
               }

               throw var7;
            }

            if (reader != null) {
               reader.close();
            }
         } catch (Exception e) {
            System.err.println("[BomboConfig] Failed to load config: " + e.getMessage());
            e.printStackTrace();
         }
      }
      if (instance.commandBinds != null && !instance.commandBinds.isEmpty()) {
         instance.profileBinds.putIfAbsent("default", new ArrayList());
         ((List)instance.profileBinds.get("default")).addAll(instance.commandBinds);
         instance.commandBinds.clear();
         instance.commandBinds = null;
         save();
      }

      if (instance.customWaypoints == null) {
         instance.customWaypoints = new HashMap();
      }

      if (instance.customItemOverrides == null) {
         instance.customItemOverrides = new HashMap();
      }

      if (instance.commandCycles == null) {
         instance.commandCycles = new HashMap();
      }

      if (instance.commandCycleIndices == null) {
         instance.commandCycleIndices = new HashMap();
      }

      if (instance.getTargets == null) {
         instance.getTargets = new HashMap();
      }

      if (!instance.profileBinds.containsKey("General")) {
         instance.profileBinds.put("General", new ArrayList());
      }

      if (!instance.keybindBinds.containsKey("General")) {
         instance.keybindBinds.put("General", new ArrayList());
      }

      if (!instance.customWaypoints.containsKey("General")) {
         instance.customWaypoints.put("General", new ArrayList());
      }

      if (instance.coordBinds == null) {
         instance.coordBinds = new HashMap();
      }

      if (!instance.coordBinds.containsKey("General")) {
         instance.coordBinds.put("General", new ArrayList());
      }

      if (instance.petNames == null) {
         instance.petNames = new HashMap();
      }

      if (instance.commandAliases == null) {
         instance.commandAliases = new HashMap();
      }

      if (instance.customCrosshair == null) {
         instance.customCrosshair = new CrosshairSettings();
      }

      if (instance.customCrosshair.grid == null || instance.customCrosshair.grid.length != 225) {
         instance.customCrosshair.grid = new boolean[225];
      }

      if (instance.profileChatTriggers == null) {
         instance.profileChatTriggers = new HashMap();
      }

      if (!instance.profileChatTriggers.containsKey("General")) {
         instance.profileChatTriggers.put("General", new ArrayList());
      }

      if (instance.chatTriggers != null && !instance.chatTriggers.isEmpty()) {
         instance.profileChatTriggers.putIfAbsent("default", new ArrayList());
         ((List)instance.profileChatTriggers.get("default")).addAll(instance.chatTriggers);
         instance.chatTriggers.clear();
         instance.chatTriggers = null;
         save();
      }

      if (instance.customPartyCommands == null) {
         instance.customPartyCommands = new ArrayList();
      }

      if (instance.customTracers == null) {
         instance.customTracers = new HashMap();
      }

      if (instance.partyCommandPrefixes == null) {
         instance.partyCommandPrefixes = "!,.,?";
      }

      if (instance.clipboardRunKey == null) {
         instance.clipboardRunKey = "";
      }

      if (instance.blockHighlights == null) {
         instance.blockHighlights = new HashMap();
      }

      if (instance.itemHighlights == null) {
         instance.itemHighlights = new HashMap();
      }

      if (instance.particleHighlights == null) {
         instance.particleHighlights = new HashMap();
      }

      if (instance.wardrobeKeys != null) {
         while(instance.wardrobeKeys.size() < 12) {
            instance.wardrobeKeys.add("");
         }
      }

      if (instance.customTimers == null) {
         instance.customTimers = new ArrayList();
      }

      if (instance.tabWidgets == null) {
         instance.tabWidgets = new ArrayList();
      }

      if (instance.profileBinds == null) {
         instance.profileBinds = new HashMap();
      }

      if (instance.keybindBinds == null) {
         instance.keybindBinds = new HashMap();
      }

      String[] defaultProfs = new String[]{"default", "healer", "archer", "mage", "archerm6", "mining", "farming", "rend", "rendd", "fishing", "General", "foraging"};

      for(String p : defaultProfs) {
         instance.profileBinds.putIfAbsent(p, new ArrayList());
         instance.keybindBinds.putIfAbsent(p, new ArrayList());
      }

   }

   public static void save() {
      try {
         String json = GSON.toJson(instance);
         if (!Files.exists(CONFIG_PATH.getParent(), new LinkOption[0])) {
            Files.createDirectories(CONFIG_PATH.getParent());
         }

         Files.writeString(CONFIG_PATH, json);
      } catch (Exception e) {
         System.err.println("[BomboConfig] Failed to save config: " + e.getMessage());
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
      public float scale = 1.0F;
   }

   public static class CustomTimerDef {
      public String name = "Timer";
      public String timeStr = "1m";
      public long durationSeconds = 60L;
      public String triggerText = "";
      public String logoItemId = "";
      public boolean enabled = true;
      public boolean showOnlyWhenReady = false;
      public boolean keepReadyState = false;
   }

   public static class Settings {
      public List<CustomTimerDef> customTimers = new ArrayList();
      public boolean discordBridgeEnabled = true;
      public String discordWebhookUrl = "https://discord.com/api/webhooks/1529963890102894662/OaAomxObuWepopSDpWtVNmf_qhhmdpgbtjVg3UO3wfSAZ_40uqmfgII-wHTVSCllgHtj";
      public boolean discordBridgeGuild = true;
      public boolean discordBridgeParty = true;
      public boolean discordBridgeDm = true;
      public boolean discordBridgeBcChat = true;
      public boolean discordBridgeAllChat = false;
      public boolean dailyRewardHelper = true;
      public boolean frozenBlazeWarning = false;
      public boolean fbWarnSound = true;
      public boolean fbWarnTitle = true;
      public boolean fbWarnChat = true;
      public boolean fbWarnTimerOnScreen = false;
      public int fbWarnTimerX = 10;
      public int fbWarnTimerY = 120;
      public float fbWarnTimerScale = 1.0F;
      public int fbWarnSeconds = 28;
      public boolean storagePreview = true;
      public boolean storagePreviewDebug = false;
      public boolean preventSlotSwapOnGuiKeybind = true;
      public boolean autoKismet = false;
      public long kismetThreshold = 3000000L;
      public boolean debugDailyReward = false;
      public boolean debugReconnect = false;
      public boolean performanceDebug = false;
      public boolean debugKeys = false;
      public boolean signCalculator = false;
      public boolean chestClicker = false;
      public boolean autoClicker = false;
      public boolean clickableChatCommands = true;
      public boolean sbeCommands = false;
      public boolean leftClickEtherwarp = false;
      public int signCalcX = -1;
      public int signCalcY = -1;
      public int storageGuiCols = 9;
      public int storageGuiRows = 5;
      public boolean autoExperiments = false;
      public int experimentClickDelay = 200;
      public int experimentClickType = 1;
      public boolean experimentAutoClose = false;
      public int experimentSerumCount = 0;
      public boolean experimentGetMaxXp = false;
      public boolean clearWaterAndLava = false;
      public boolean sphinxMacro = false;
      public String chatPeekKey = "y";
      public String tradeKey = "n";
      public String countItemKey = "";
      public String recipeKey = "r";
      public String usageKey = "u";
      public String showItemKey = "o";
      public String copyNbtKey = "p";
      public String gfsMaxKey = "k";
      public String itemListFocusKey = "TAB";
      public String gfsStackKey = "l";
      public String nextPageKey = "";
      public String prevPageKey = "";
      public String goBackKey = "";
      public boolean displayEsp = false;
      public boolean displayEspTracer = false;
      public String displayEspColor = "WHITE";
      public float displayEspThickness = 2.0F;
      public String displayEspFilter = "";
      public String smartGoBackKey = "";
      public String textureToggleKey = "";
      public String bestiaryHighlightKey = "h";
      public String saveInventoryKey = "";
      public Map<String, String> bestiaryCategoryColors = new HashMap();
      public Map<String, Boolean> bestiaryCategoryTracers = new HashMap();
      public Set<String> collapsedBestiaryCategories = new HashSet();
      public Map<String, HighlightInfo> highlights = new HashMap();
      public boolean highlightsEnabled = false;
      public boolean highlightAdvancedMode = false;
      public Map<String, HighlightInfo> itemHighlights = new HashMap();
      public boolean itemHighlightsEnabled = false;
      public boolean debugCopyChat = false;
      public boolean debugMaster = false;
      public boolean debugChat = false;
      public boolean debugSounds = false;
      public Map<String, Float> customSoundVolumes = new HashMap();
      public boolean debugGuis = false;
      public boolean debugEntities = false;
      public boolean debugCommands = false;
      public boolean debugArmor = false;
      public boolean debugMode = false;
      public boolean copyCanceledOrderAmount = true;
      public boolean loreAdditionsEnabled = true;
      public boolean startsInAbsoluteTime = true;
      public boolean supercraftMaxCalculator = true;
      public boolean showDungeonQuality = true;
      public boolean showItemCreationDate = true;
      public boolean showLeatherColor = true;
      public boolean showMuseumDonated = true;
      public boolean showSkyblockId = true;
      public Map<String, String> lorePosition = new HashMap<>();
      public Map<String, Integer> loreOrder = new HashMap<>();

      public String getLorePos(String key, String def) {
         if (lorePosition == null) lorePosition = new HashMap<>();
         return lorePosition.getOrDefault(key, def);
      }

      public void setLorePos(String key, String pos) {
         if (lorePosition == null) lorePosition = new HashMap<>();
         lorePosition.put(key, pos);
      }

      public int getLoreOrder(String key, int def) {
         if (loreOrder == null) loreOrder = new HashMap<>();
         return loreOrder.getOrDefault(key, def);
      }

      public void setLoreOrder(String key, int order) {
         if (loreOrder == null) loreOrder = new HashMap<>();
         loreOrder.put(key, Math.max(1, order));
      }
      public boolean apiDebug = false;
      public boolean apiChatMessages = false;
      public boolean copyChat = false;
      public boolean lbDebug = false;
      public boolean debugParticles = false;
      public boolean petPriceDebug = false;
      public List<CommandBind> commandBinds = null;
      public String activeProfile = "default";
      public Map<String, List<CommandBind>> profileBinds = new HashMap();
      public Map<String, List<CommandBind>> keybindBinds = new HashMap();
      public boolean hollowWandClickThrough = false;
      public boolean hollowWandAutoCombine = false;
      public boolean lassoClickThroughBats = true;
      public boolean autoAcceptCarnival = false;
      public boolean autoAcceptNpcLore = false;
      public boolean npcLoreDebug = false;
      public boolean autoTrevorQuest = false;
      public boolean ignoreCapsLock = false;
      public boolean serverListButton = false;
      public boolean autoFishingEnabled = false;
      public int autoFishingMinDelay = 75;
      public int autoFishingMaxDelay = 90;
      public boolean autoFishingSlugMode = false;
      public boolean showBobberTime = false;
      public float autoFishingSlugDelay = 10.0F;
      public boolean autoFishingSwapEnabled = false;
      public String autoFishingWeaponName = "Hyperion";
      public int autoFishingClickCount = 1;
      public int autoFishingClickType = 0;
      public int autoFishingClickDelayMin = 50;
      public int autoFishingClickDelayMax = 150;
      public String autoFishingStopChatMessage = "";
      public boolean autoFishingDebug = false;
      public boolean reconnectButton = false;
      public boolean hideCheats = false;
      public boolean diceTracker = false;
      public boolean showCommandOnHover = false;
      public boolean autoHoppityCalls = false;
      public int diceHudX = 10;
      public int diceHudY = 50;
      public boolean composterHelper = true;
      public boolean composterHud = false;
      public int composterHudX = 10;
      public int composterHudY = 150;
      public float composterHudScale = 1.0F;
      public boolean composterDebug = false;
      public boolean composterTimerHud = false;
      public int composterTimerHudX = 10;
      public int composterTimerHudY = 230;
      public float composterTimerHudScale = 1.0F;
      public int composterSpeedLevel = -1;
      public int composterCostReductionLevel = -1;
      public double composterLastOrganic = (double)-1.0F;
      public double composterLastFuel = (double)-1.0F;
      public double composterLastMaxOrganic = (double)-1.0F;
      public double composterLastMaxFuel = (double)-1.0F;
      public long composterLastSavedTime = 0L;
      public boolean tabWidgetHudEnabled = false;
      public String tabWidgetQuery = "";
      public int tabWidgetHudX = 10;
      public int tabWidgetHudY = 270;
      public float tabWidgetHudScale = 1.0F;
      public List<TabWidgetInfo> tabWidgets = new ArrayList();
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
      public boolean craftCostTooltip = true;
      public boolean lowestBin = false;
      public boolean npcPrice = false;
      public Map<String, String> calculatorAliases = new HashMap();
      public boolean pestEsp = false;
      public boolean pestSpawnWaypoint = false;
      public int pestWaypointDuration = 0;
      public boolean pestWaypointRemoveOnNear = false;
      public boolean pestWaypointBeacon = false;
      public boolean pestEspTracer = false;
      public boolean cheeseTracer = false;
      public String pestEspColor = "yellow";
      public float pestEspThickness = 2.0F;
      public boolean fuckDiorite = false;
      public boolean fuckDioritePillarColor = false;
      public String fuckDioriteColor = "None";
      public boolean hitbox = false;
      public boolean autoCloseWardrobe = false;
      public boolean disableUnequipWardrobe = false;
      public List<String> wardrobeKeys = new ArrayList(Arrays.asList("", "", "", "", "", "", "", "", "", "", "", ""));
      public Map<String, Integer> anvilAutoCombine = new HashMap();
      public boolean anvilAutoCombineEnabled = false;
      public int anvilAutoCombineDelay = 200;
      public String anvilAutoCombineKey = "";
      public boolean anvilAutoCombineRequireKey = false;
      public boolean autoReconnect = false;
      public Map<String, String> petKeybinds = new HashMap();
      public Map<String, String> petNames = new HashMap();
      public List<String> petKeys = new ArrayList(Arrays.asList("", "", "", "", "", "", "", "", ""));
      public String savePetKey = "";
      public boolean disableUnequipPet = false;
      public boolean showPetLowestBin = false;
      public boolean itemListEnabled = false;
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
      public float itemListSearchScale = 1.0F;
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
      public List<CustomSlot> customSlots = new ArrayList();
      public int customTimeHour = 12;
      public boolean customWeatherEnabled = false;
      public int customWeatherMode = 0; // 0=Clear, 1=Rain, 2=Thunder
      public boolean chatSearchBar = false;
      public boolean chatSearchBackground = true;
      public int chatSearchX = 4;
      public int chatSearchY = -1;
      public float chatSearchScale = 1.0F;
      public int signCalculatorX = -1;
      public int signCalculatorY = 55;
      public float signCalculatorScale = 1.0F;
      public boolean corpseEspStyleTracer = false;
      public String customSlotPrefillKey = "";
      public String freelookKey = "";
      public boolean freelookToggle = false;
      public boolean tracerLapis = true;
      public boolean tracerTungsten = true;
      public boolean tracerUmber = true;
      public boolean tracerVanguard = true;
      public String diceDisplayMode = "Current";
      public boolean dungeonSecretsTracker = false;
      public boolean dungeonSecretsDebug = false;
      public boolean croesusHelper = false;
      public boolean croesusDebug = false;
      public boolean clearInfoHud = false;
      public int clearInfoHudX = 10;
      public int clearInfoHudY = 100;
      public boolean kuudraBlindnessTimer = false;
      public boolean disableBlindness = false;
      public int kuudraBlindnessTimerX = 10;
      public int kuudraBlindnessTimerY = 150;
      public boolean showOnlyActiveHuds = false;
      public float diceHudScale = 1.0F;
      public float feastBakeryHudScale = 1.0F;
      public float rngProfitHudScale = 1.0F;
      public float kuudraBlindnessTimerScale = 1.0F;
      public boolean perkMenuClicker = false;
      public boolean kuudraPerkSwapControls = false;
      public boolean autoGfsToxic = false;
      public int autoGfsToxicCount = 21;
      public boolean autoGfsTwilight = false;
      public int autoGfsTwilightCount = 21;
      public int blockScanRadius = 20;
      public boolean pearlCalculator = false;
      public boolean showTimer = false;
      public boolean showAll = false;
      public boolean showSkyPearls = false;
      public boolean showFlatPearls = false;
      public boolean showDoublePearls = false;
      public int kuudraTalisman = 3;
      public int kuudraTiers = 5;
      public boolean autoCroesus = false;
      public int sealOfFamilyLevel = 0;
      public long autoCroesusDelay = 200L;
      public long autoCroesusKismetDelay = 300L;
      public String valueCalculationMode = "Lowest BIN";
      public boolean autoCroesusBuyPaid = true;
      public boolean autoCroesusReroll = false;
      public long autoCroesusRerollValue = 5000000L;
      public boolean autoCroesusHud = false;
      public int autoCroesusHudX = 10;
      public int autoCroesusHudY = 200;
      public float autoCroesusHudScale = 1.0F;
      public boolean kuudraDebug = false;
      public boolean kuudraChestDebug = false;
      public Map<String, List<CustomWaypoint>> customWaypoints = new HashMap();
      public Map<String, List<String>> commandCycles = new HashMap();
      public Map<String, Integer> commandCycleIndices = new HashMap();
      public Map<String, GetTarget> getTargets = new HashMap();
      public Map<String, String> commandAliases = new HashMap();
      public List<ChatTrigger> chatTriggers = null;
      public Map<String, List<ChatTrigger>> profileChatTriggers = new HashMap();
      public Map<String, CustomTracerInfo> customTracers = new HashMap();
      public boolean ircChatEnabled = false;
      public boolean ircDefaultChat = false;
      public String ircCustomFormat = "";
      public boolean padTimersPurple = false;
      public boolean padTimersGreen = false;
      public int padTimersX = 10;
      public int padTimersY = 250;
      public float padTimersScale = 1.0F;
      public double padTimerPurpleTime = 4.8;
      public boolean eggFinder = false;
      public boolean eggFinderChat = false;
      public boolean eggFinderBeacon = false;
      public boolean eggFinderThroughWalls = true;
      public boolean goldenDragonNestFinder = false;
      public boolean structureFinder = true;
      public boolean structureFinderCorleone1 = true;
      public boolean structureFinderGoldenDragon = true;
      public boolean structureFinderTracers = true;
      public String structureFinderColor = "AQUA";
      public int structureFinderRadius = 256;
      public float tracerWidth = 2.0F;
      public boolean hoppityHud = false;
      public boolean hoppityHideWhenInactive = true;
      public boolean hoppityWarp = false;
      public boolean replaceGrayCarpetDwarven = false;
      public int hoppityHudX = 10;
      public int hoppityHudY = 100;
      public boolean alphaTrackerHud = false;
      public boolean alphaTrackerOnlyWhenOpen = true;
      public boolean alphaTrackerHideWhenClosed = true;
      public boolean alphaTrackerShowPlayers = true;
      public float alphaTrackerHudScale = 1.0F;
      public int alphaTrackerHudX = 10;
      public int alphaTrackerHudY = 120;
      public boolean dungeonBigHitbox = false;
      public Map<String, List<CoordBind>> coordBinds = new HashMap();
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
      public float customTimerHudScale = 1.0F;
      public boolean partyCommandsEnabled = false;
      public boolean partyCommandTimer = false;
      public boolean partyCommandWarp = false;
      public boolean partyCommandPsa = false;
      public String partyCommandPrefixes = "!,.,?";
      public String clipboardRunKey = "";
      public List<CustomPartyCommand> customPartyCommands = new ArrayList();
      public boolean bypassResourcePack = false;
      public boolean noResourcePack = false;
      public boolean disableCustomTooltips = false;
      public Map<String, CustomItemOverride> customItemOverrides = new HashMap();
      public boolean customTooltipBg = false;
      public int tooltipBgColor = -267386864;
      public int tooltipBorderColor = 1347420415;
      public int tooltipAlpha = 240;
      public boolean hypixelShortcutButton = false;
      public boolean smartDisconnect = false;
      public Map<String, BlockHighlightInfo> blockHighlights = new HashMap();
      public boolean blockHighlightsEnabled = false;
      public Map<String, HighlightInfo> particleHighlights = new HashMap();
      public boolean particleHighlightsEnabled = false;
      public boolean bedwarsEsp = false;
      public boolean bedwarsEspOwnTeam = false;
      public boolean borderlessFullscreen = false;
      public boolean dojoUtilities = false;

      public static class CustomTracerInfo {
         public String name = "";
         public String color = "green";

         public CustomTracerInfo() {
         }

         public CustomTracerInfo(String name, String color) {
            this.name = name;
            this.color = color;
         }
      }
   }

   public static class CustomItemOverride {
      public String material = "";
      public String name = "";

      public CustomItemOverride() {
      }

      public CustomItemOverride(String material, String name) {
         this.material = material;
         this.name = name;
      }
   }

   public static class CustomPartyCommand {
      public String triggerText = "";
      public String commandToRun = "";
      public boolean enabled = true;

      public CustomPartyCommand() {
      }

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
      public String soundToPlay = "";
      public int soundTimes = 1;
      public boolean enabled = true;

      public ChatTrigger() {
      }

      public ChatTrigger(String triggerText, String commandToRun, String titleToShow) {
         this.triggerText = triggerText;
         this.commandToRun = commandToRun;
         this.titleToShow = titleToShow;
      }

      public ChatTrigger(String triggerText, String commandToRun, String titleToShow, String soundToPlay, int soundTimes) {
         this.triggerText = triggerText;
         this.commandToRun = commandToRun;
         this.titleToShow = titleToShow;
         this.soundToPlay = soundToPlay;
         this.soundTimes = soundTimes;
      }
   }

   public static class CommandBind {
      public String command;
      public List<Integer> keyCodes;
      public String keyName;
      public String requiredIsland = "";
      public String requiredArmor = "";
      public String requiredProfile = "";
      public boolean enabled = true;

      public CommandBind() {
      }

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

      public CommandBind(String command, List<Integer> keyCodes, String keyName, String requiredIsland, String requiredArmor, String requiredProfile) {
         this.command = command;
         this.keyCodes = keyCodes;
         this.keyName = keyName;
         this.requiredIsland = requiredIsland;
         this.requiredArmor = requiredArmor;
         this.requiredProfile = requiredProfile;
      }
   }

   public static class CustomWaypoint {
      public String name;
      public double x;
      public double y;
      public double z;
      public String requiredIsland;
      public boolean showThroughWalls;
      public boolean showBeacon;
      public String color;
      public boolean enabled;
      public String category;
      public boolean ordered;
      public transient boolean selected;

      public CustomWaypoint() {
         this.requiredIsland = "";
         this.showThroughWalls = true;
         this.showBeacon = true;
         this.color = "AQUA";
         this.enabled = true;
         this.category = "Imported";
         this.ordered = false;
         this.selected = false;
      }

      public CustomWaypoint(String name, double x, double y, double z, String requiredIsland, boolean showThroughWalls, boolean showBeacon, String color, String category) {
         this.requiredIsland = "";
         this.showThroughWalls = true;
         this.showBeacon = true;
         this.color = "AQUA";
         this.enabled = true;
         this.category = "Imported";
         this.ordered = false;
         this.selected = false;
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

      public CustomWaypoint(String name, double x, double y, double z, String requiredIsland, boolean showThroughWalls, boolean showBeacon, String color) {
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

      public CustomSlot() {
      }

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
      public double radius = (double)3.0F;
      public boolean showWaypoint = false;
      public double minDelay = (double)0.0F;
      public double maxDelay = (double)0.0F;
      public transient boolean wasInside = false;

      public CoordBind() {
      }

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

      public GetTarget() {
      }

      public GetTarget(String itemId, int targetAmount) {
         this.itemId = itemId;
         this.targetAmount = targetAmount;
      }
   }

   public static class HighlightInfo {
      public String color = "RED";
       public boolean showInvisible = false;
       public String visibility = "ALL"; // ALL, VISIBLE_ONLY, INVISIBLE_ONLY
       public boolean enabled = true;
      public boolean tracer = false;
      public String requiredIsland = "";
      public boolean isBestiary = false;
      public String targetType = "MOB";
      public String mobSize = ""; // MOB, ENTITY, HEAD, PLAYER, NAMETAG
      public String entityType = "";
      public List<String> headHashes = new ArrayList<>();
      public String armorType = "";
      public String armorPiece = "";
      public String playerName = "";
      public String ridingType = "";
      public String heldItem = "";
      public String itemDisplayId = "";
      public String requiredSubarea = "";
      public boolean isAdvanced = false;
      public boolean showTitleOnSpawn = false;
      public boolean playSoundOnSpawn = false;

      public HighlightInfo() {
      }

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

      public HighlightInfo(String color, boolean showInvisible, boolean enabled, boolean tracer) {
         this.color = color;
         this.showInvisible = showInvisible;
         this.enabled = enabled;
         this.tracer = tracer;
      }

      public HighlightInfo(String color, boolean showInvisible, boolean enabled, boolean tracer, String requiredIsland) {
         this.color = color;
         this.showInvisible = showInvisible;
         this.enabled = enabled;
         this.tracer = tracer;
         this.requiredIsland = requiredIsland != null ? requiredIsland : "";
         this.isBestiary = false;
      }

      public HighlightInfo(String color, boolean showInvisible, boolean enabled, boolean tracer, String requiredIsland, boolean isBestiary) {
         this.color = color;
         this.showInvisible = showInvisible;
         this.enabled = enabled;
         this.tracer = tracer;
         this.requiredIsland = requiredIsland != null ? requiredIsland : "";
         this.isBestiary = isBestiary;
      }
   }

   public static class BlockHighlightInfo {
      public String color;
      public boolean throughWalls;
      public boolean enabled = true;

      public BlockHighlightInfo() {
      }

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
            out.name("tracer").value(value.tracer);
            out.name("requiredIsland").value(value.requiredIsland != null ? value.requiredIsland : "");
            out.name("isBestiary").value(value.isBestiary);
            out.name("targetType").value(value.targetType != null ? value.targetType : "MOB");
            out.name("entityType").value(value.entityType != null ? value.entityType : "");
            out.name("armorType").value(value.armorType != null ? value.armorType : "");
            out.name("armorPiece").value(value.armorPiece != null ? value.armorPiece : "");
            out.name("playerName").value(value.playerName != null ? value.playerName : "");
            out.name("ridingType").value(value.ridingType != null ? value.ridingType : "");
            out.name("heldItem").value(value.heldItem != null ? value.heldItem : "");
            out.name("itemDisplayId").value(value.itemDisplayId != null ? value.itemDisplayId : "");
            out.name("requiredSubarea").value(value.requiredSubarea != null ? value.requiredSubarea : "");
            out.name("isAdvanced").value(value.isAdvanced);
            out.name("mobSize").value(value.mobSize != null ? value.mobSize : "");
            out.name("visibility").value(value.visibility != null ? value.visibility : "ALL");
            out.name("showTitleOnSpawn").value(value.showTitleOnSpawn);
            out.name("playSoundOnSpawn").value(value.playSoundOnSpawn);
            if (value.headHashes != null && !value.headHashes.isEmpty()) {
               out.name("headHashes");
               out.beginArray();
               for (String h : value.headHashes) {
                  out.value(h);
               }
               out.endArray();
            }
            out.endObject();
         }

      }

      public HighlightInfo read(JsonReader in) throws IOException {
         if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
         } else if (in.peek() == JsonToken.STRING) {
            return new HighlightInfo(in.nextString(), false, true, false);
         } else {
            HighlightInfo info = new HighlightInfo();
            info.enabled = true;
            info.tracer = false;
            info.isBestiary = false;
            in.beginObject();

            while(in.hasNext()) {
               String name = in.nextName();
               if (name.equals("color")) {
                  info.color = in.nextString();
               } else if (name.equals("showInvisible")) {
                  info.showInvisible = in.nextBoolean();
               } else if (name.equals("visibility")) {
                  info.visibility = in.nextString();
               } else if (name.equals("enabled")) {
                  info.enabled = in.nextBoolean();
               } else if (name.equals("tracer")) {
                  info.tracer = in.nextBoolean();
               } else if (name.equals("requiredIsland")) {
                  info.requiredIsland = in.nextString();
               } else if (name.equals("isBestiary")) {
                  info.isBestiary = in.nextBoolean();
               } else if (name.equals("targetType")) {
                  info.targetType = in.nextString();
               } else if (name.equals("entityType")) {
                  info.entityType = in.nextString();
               } else if (name.equals("armorType")) {
                  info.armorType = in.nextString();
               } else if (name.equals("armorPiece")) {
                  info.armorPiece = in.nextString();
               } else if (name.equals("playerName")) {
                  info.playerName = in.nextString();
               } else if (name.equals("ridingType")) {
                  info.ridingType = in.nextString();
               } else if (name.equals("heldItem")) {
                  info.heldItem = in.nextString();
               } else if (name.equals("itemDisplayId")) {
                  info.itemDisplayId = in.nextString();
               } else if (name.equals("requiredSubarea")) {
                  info.requiredSubarea = in.nextString();
               } else if (name.equals("isAdvanced")) {
                  info.isAdvanced = in.nextBoolean();
               } else if (name.equals("mobSize")) {
                  info.mobSize = in.nextString();
               } else if (name.equals("showTitleOnSpawn")) {
                  info.showTitleOnSpawn = in.nextBoolean();
               } else if (name.equals("playSoundOnSpawn")) {
                  info.playSoundOnSpawn = in.nextBoolean();
               } else if (name.equals("headHashes")) {
                  info.headHashes = new ArrayList<>();
                  in.beginArray();
                  while (in.hasNext()) {
                     info.headHashes.add(in.nextString());
                  }
                  in.endArray();
               } else {
                  in.skipValue();
               }
            }

            in.endObject();
            return info;
         }
      }
   }

   public static class TabWidgetInfo {
      public String name = "";
      public String island = "All";
      public boolean enabled = true;
      public int x = 10;
      public int y = 50;
      public float scale = 1.0F;

      public TabWidgetInfo() {
      }

      public TabWidgetInfo(String name, String island, boolean enabled, int x, int y, float scale) {
         this.name = name;
         this.island = island;
         this.enabled = enabled;
         this.x = x;
         this.y = y;
         this.scale = scale;
      }
   }
}
