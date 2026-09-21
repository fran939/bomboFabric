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
   private static final Path OLD_BOMBO_DIR = FabricLoader.getInstance().getConfigDir().resolve("bombo");
   private static final Path BOMBOADDONS_DIR = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons");
   private static final Path CONFIG_PATH = BOMBOADDONS_DIR.resolve("bomboaddons.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().registerTypeAdapter(HighlightInfo.class, new HighlightInfoAdapter()).create();
   private static Settings instance = new Settings();
   public static String controlsInitialSearchQuery = null;

   public static void load() {
      // 1. If old loose config file exists in config/bomboaddons.json
      if (Files.exists(OLD_CONFIG_PATH, new LinkOption[0])) {
         try {
            if (!Files.exists(BOMBOADDONS_DIR, new LinkOption[0])) {
               Files.createDirectories(BOMBOADDONS_DIR);
            }
            Files.move(OLD_CONFIG_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

      // 2. Merge entire config/bombo folder into config/bomboaddons folder and remove config/bombo
      if (Files.exists(OLD_BOMBO_DIR, new LinkOption[0])) {
         try {
            if (!Files.exists(BOMBOADDONS_DIR, new LinkOption[0])) {
               Files.createDirectories(BOMBOADDONS_DIR);
            }
            try (java.util.stream.Stream<Path> stream = Files.walk(OLD_BOMBO_DIR)) {
               stream.forEach(source -> {
                  try {
                     Path dest = BOMBOADDONS_DIR.resolve(OLD_BOMBO_DIR.relativize(source));
                     if (Files.isDirectory(source)) {
                        if (!Files.exists(dest)) {
                           Files.createDirectories(dest);
                        }
                     } else {
                        if (!Files.exists(dest)) {
                           Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
                        } else {
                           // If destination exists, replace or delete source
                           Files.deleteIfExists(source);
                        }
                     }
                  } catch (Exception ex) {
                     ex.printStackTrace();
                  }
               });
            }
            // Delete old bombo directory recursively
            try (java.util.stream.Stream<Path> stream = Files.walk(OLD_BOMBO_DIR)) {
               stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                  try {
                     Files.deleteIfExists(p);
                  } catch (Exception ignored) {}
               });
            }
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
      if (instance.updateChannel == null || instance.updateChannel.trim().isEmpty()) {
         instance.updateChannel = "Betas & Full";
      }
      if (instance.swapUnifiedLineColorValue == 0) {
         instance.swapUnifiedLineColorValue = 0xFF22C55E;
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

      if (instance.hiddenEntities == null) {
         instance.hiddenEntities = new ArrayList();
      }

      if (instance.hudStyles == null) {
         instance.hudStyles = new HashMap<>();
      }

      if (instance.blockReplacements == null) {
         instance.blockReplacements = new ArrayList();
      }

      if (instance.autoProfileRules == null) {
         instance.autoProfileRules = new ArrayList<>();
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

   public static boolean isDev() {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc == null || mc.getUser() == null) return false;
      String name = mc.getUser().getName();
      if (name == null) return false;
      String lower = name.toLowerCase();
      return lower.equals("zamasu12045") || lower.equals("bomboclas") || lower.equals("fran938") || lower.equals("fran939") || lower.equals("fran");
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

   public static class BlockedSlotDef {
      public String itemMatcher = ""; // Item name or Skyblock ID
      public String guiMatcher = "";  // GUI title filter (or empty for any)
      public String islandMatcher = ""; // Island name filter (or empty for any)
      public boolean enabled = true;

      public BlockedSlotDef() {}

      public BlockedSlotDef(String itemMatcher, String guiMatcher, String islandMatcher) {
         this.itemMatcher = itemMatcher;
         this.guiMatcher = guiMatcher;
         this.islandMatcher = islandMatcher;
         this.enabled = true;
      }
   }

   public static class Settings {
      public List<BlockedSlotDef> blockedSlots = new ArrayList();
      public String blockedSlotsBypassKey = "LSHIFT";
      public List<CustomTimerDef> customTimers = new ArrayList();
      public boolean dailyRewardHelper = true;
      public boolean frozenBlazeWarning = false;
      public boolean fbWarnSound = true;
      public boolean fbWarnTitle = true;
      public boolean fbWarnChat = true;
      public boolean fbWarnTimerOnScreen = false;
      public boolean fbWarnRequireRod = false;
      public int fbWarnTimerX = 10;
      public int fbWarnTimerY = 120;
      public float fbWarnTimerScale = 1.0F;
      public int fbWarnSeconds = 28;
      public String ircNameColor = "";
      public String ircDiscordUser = "fran938";
      public boolean cameraSettingsEnabled = false;
      public float cameraDistance = 4.0F;
      public boolean cameraPassThroughWalls = false;
      public boolean disableFrontCamera = false;
      public boolean dojoMasteryWool = true;
      public boolean dojoNextWoolTracer = true;
      public boolean dojoShootHud = true;
      public boolean dojoShootSound = true;
      public boolean dojoDebug = false;
      public int dojoShootHudX = -1;
      public int dojoShootHudY = -1;
      public float dojoShootHudScale = 1.0F;
      public boolean storagePreview = true;
      public boolean storagePreviewDebug = false;
      public boolean storageOverlay = false;
      public boolean preventSlotSwapOnGuiKeybind = true;
      public boolean inventorySlotSwapEnabled = true;
      public String inventorySlotSwapKey = "X";
      public String inventorySlotSwapTrigger = "Shift"; // "Shift", "Ctrl", "Alt", "Hotkey"
      public boolean autoKismet = false;
      public long kismetThreshold = 3000000L;
      public boolean debugDailyReward = false;
      public boolean debugReconnect = false;
      public boolean hypixelIspFix = true;
      public String hypixelBypassIp = "mc.hypixel.net";
      public boolean autoRejoinSkyblock = true;
      public boolean autoRejoinHud = true;
      public boolean autoHitman = false;
      public int autoHitmanIntervalMinutes = 5;
      public String updateChannel = "Betas & Full"; // "Betas & Full" or "Full Only"

      public boolean isBetaUpdateChannel() {
         return !"Full Only".equalsIgnoreCase(this.updateChannel);
      }
      public int autoRejoinHudX = 10;
      public int autoRejoinHudY = 150;
      public float autoRejoinHudScale = 1.0F;
      public boolean itemValueBreakdownHud = true;
      public int itemValueBreakdownHudX = -1;
      public int itemValueBreakdownHudY = -1;
      public float itemValueBreakdownHudScale = 1.0F;
      public boolean performanceDebug = false;
      public boolean debugKeys = false;
      public boolean apiKeyDebug = false;
      public boolean clickableChatCommands = true;
      public boolean sbeCommands = false;
      public boolean leftClickEtherwarp = false;
      public boolean etherwarpBlockOnly = false;
      public boolean etherwarpFallbackRightClick = false;
      public boolean fastAotv = false;
      public int fastAotvSpeed = 2;
      public boolean fastHyp = false;
      public int fastHypSpeed = 2;
      public boolean fastUseEnabled = false;
      public boolean fastUseBlocksOnly = false;
      public int fastUseSpeed = 2;
      public String fastUseSkyblockId = "";
      public int etherwarpMaxDistance = 61;

      public boolean dianaLootshareEnabled = false;
      public boolean dianaLsInquisitor = true;
      public boolean dianaLsChampion = true;
      public boolean dianaLsGaia = true;
      public boolean dianaLsMinotaur = true;
      public boolean dianaLsSiamese = true;
      public boolean dianaLsUserMessage = true;
      public String dianaLsUserMessageText = "&8[&eDiana&8] &aLootshare ready on &6{mob}&a!";
      public boolean dianaLsCommand = false;
      public String dianaLsCommandText = "pc Lootshare Ready on {mob}!";
      public boolean dianaLsTitle = true;
      public String dianaLsTitleText = "&e&lLOOTSHARE READY &7({mob})";
      public boolean dianaLsSound = true;
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
      public int autoAhSellDelayMs = 120;
      public String chatPeekKey = "y";
      public String tradeKey = "n";
      public String tradeMaxKey = "";
      public String countItemKey = "";
      public String recipeKey = "r";
      public String recipeCmdKey = "";
      public String viewRecipeKey = "";
      public String recipeHotkeyAction = "GUI"; // "GUI", "/recipe", "/viewrecipe"
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
      public Map<String, String> customSoundReplacements = new HashMap();
      public Set<String> disabledCustomSoundReplacements = new HashSet();
      public boolean debugGuis = false;
      public boolean debugEntities = false;
      public boolean debugCommands = false;
      public boolean debugArmor = false;
      public boolean debugMode = false;
      public boolean hotspotDebug = false;
      public boolean coordBindDebug = false;
      public boolean pvDebug = false;
      public boolean eggFinderDebug = true;
      public boolean copyCanceledOrderAmount = true;
      public boolean loreAdditionsEnabled = true;
      public boolean startsInAbsoluteTime = true;
      public boolean supercraftMaxCalculator = true;
      public boolean showDungeonQuality = true;
      public boolean showItemCreationDate = true;
      public boolean showLeatherColor = true;
      public boolean showMuseumDonated = true;
      public boolean showSkyblockId = true;
       public boolean signCalculator = false;
       public boolean chestClicker = false;
       public boolean autoCloseClicker = false;
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

      public void setLoreOrder(String key, int newOrder) {
         if (loreOrder == null) loreOrder = new HashMap<>();
         int safeOrder = Math.max(1, newOrder);
         int oldOrder = getLoreOrder(key, safeOrder);
         if (oldOrder == safeOrder) return;

         String[] allKeys = new String[] {
            "copyCanceled", "supercraft", "startsIn", "dungeonQuality",
            "itemCreationDate", "leatherColor", "museumDonated", "skyblockId",
            "lowestBin", "craftCost", "npcPrice", "petLowestBin"
         };

         // Initialize default orders if missing
         for (int i = 0; i < allKeys.length; i++) {
            if (!loreOrder.containsKey(allKeys[i])) {
               loreOrder.put(allKeys[i], i + 1);
            }
         }

         // Shift any item that occupied newOrder or was in between
         for (String otherKey : allKeys) {
            if (!otherKey.equals(key)) {
               int currentOther = loreOrder.getOrDefault(otherKey, 1);
               if (currentOther == safeOrder) {
                  loreOrder.put(otherKey, oldOrder);
               }
            }
         }
         loreOrder.put(key, safeOrder);
      }
      // GUI Theme & Customization
      public String guiAccentColor = "Cyan";
      public String guiSecondaryColor = "White";
      public String guiToggleColor = "Emerald";
      public String guiThemeMode = "Dark Mode"; // "Dark Mode", "Light Mode", "Transparent", "Zamasu"
      public String previousThemeMode = "Dark Mode";
      public String guiFont = "Default";
      public float guiWindowScale = 1.0F;
      public int guiBackgroundOpacity = 90;
      public int guiSidebarWidth = 180;
      public boolean guiCardGlow = true;
      public float guiTextScale = 1.0F;
      public String customSbLevelColor = ""; // e.g. "&b", "&6", "&d", "&c", "&a", "Chroma"

      public boolean showDamageDebug = false;
      public boolean damageDebugCritsOnly = false;
      public boolean apiDebug = false;
      public boolean apiChatMessages = false;
      public String apiKey = "";
      public String keyOwnerName = "";
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
      public String autoAcceptNpcLoreExceptions = "Safari Receptionist, Safari Manager";
      public boolean npcLoreDebug = false;
      public boolean autoTrevorQuest = false;
      public boolean ignoreCapsLock = false;
      public boolean serverListButton = false;
      public boolean autoFishingEnabled = false;
      public int autoFishingMinDelay = 75;
      public int autoFishingMaxDelay = 90;
      public boolean autoFishingSlugMode = false;
      public boolean showBobberTime = false;
      public int bobberTimeHudX = -1;
      public int bobberTimeHudY = -1;
      public float bobberTimeHudScale = 1.0F;
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
      public String lastServerIp = "";
      public String lastServerName = "";
      public boolean hideCheats = true;
      public boolean diceTracker = false;
      public boolean showCommandOnHover = false;
      public boolean autoHoppityCalls = false;
      public String inventoryButtonTooltipMode = "FULL"; // "FULL", "NAME_ONLY", "CLICKS_ONLY", "NONE"
      public boolean speedometer = false;
      public int speedometerX = 10;
      public int speedometerY = 120;
      public float speedometerScale = 1.0F;
      public String speedometerUnit = "bps";
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
       public boolean cropBreakWarning = false;
       public boolean sunsGraspWarning = true;
       public boolean sunsGraspAutoSwap = false;
       public boolean totemRareDrops = false;
       public boolean totemAllDrops = false;
       public boolean gardenBlockSlotsWhileFarming = true;
       public String gardenForwardKey = "up";
       public String gardenBackwardKey = "down";
       public String gardenLeftKey = "left";
       public String gardenRightKey = "right";
       public String gardenBreakKey = "b";
       public String gardenUseKey = "u";
       public boolean pestEspDebug = false;
       public boolean autoSwapProfiles = false;
       public boolean autoSwapNotifyChat = true;
       public boolean autoSwapNotifyScreen = true;
       public boolean autoSwapNotifySound = true;
       public List<ProfileAutoSwapRule> autoProfileRules = new ArrayList<>();
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
       public boolean critterDebug = false;
      public boolean critterHud = false;
      public int critterHudX = 10;
      public int critterHudY = 10;
      public float critterHudScale = 1.0F;
      public String critterTrackingMode = "Uniques";
      public boolean critterShowPerPlayer = true;
      public boolean critterShowMissing = true;
      public boolean critterOnlyInSafari = true;
      public boolean critterHighlightMobs = true;
      public boolean critterTracers = true;
      public boolean critterHighlightStrings = true;
      public boolean critterHighlightBeeNests = true;
      public boolean critterHighlightWalls = true;
      public boolean autoAcceptHideyho = true;
      public boolean critterCapsuleTrajectory = true;
      public boolean critterMapHud = true;
      public int critterMapX = -1;
      public int critterMapY = -1;
      public float critterMapScale = 1.0F;
      public int critterMapHudX = 10;
      public int critterMapHudY = 140;
      public float critterMapHudScale = 1.0F;
      public String hideyhoNavMode = "shnav"; // "shnav" or "Tracer"
      public String critterMapPlayerStyle = "Small Icon"; // "Small Icon", "Player Head", "Head + Name"
      public boolean critterMap3D = false;
      public String critterCompletionNotify = "Chat (Self)"; // "Disabled", "Chat (Self)", "Party (/pc)"
      public String sparklingTracerColor = "LIGHT_PURPLE"; // Purple default for Sparkling mobs
      public boolean chatTabs = true;
      public int chatTabsX = 4;
      public int chatTabsY = -1;
      public float chatTabsScale = 1.0F;
      public String activeChatTab = "All";
      public boolean showGuildChatTab = true;
      public boolean showPartyChatTab = true;
      public boolean showDmChatTab = true;
      public boolean showBomboChatTab = true;
      public String guildTabClickCommand = "/chat g";
      public String partyTabClickCommand = "/chat p";
      public String allTabClickCommand = "/chat a";
      public String bomboTabClickCommand = "/chat b";
      public String dmTabClickCommand = "";
       public Map<String, HudStyle> hudStyles = new HashMap<>();

       public HudStyle getHudStyle(String hudName) {
          if (hudStyles == null) hudStyles = new HashMap<>();
          return hudStyles.computeIfAbsent(hudName, k -> new HudStyle());
       }
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
      public boolean controlsSearchBar = true;
      public boolean chatSearchBackground = true;
      public int chatSearchX = 4;
      public int chatSearchY = -1;
      public float chatSearchScale = 1.0F;
      public int signCalculatorX = -1;
      public int signCalculatorY = 40;
      public float signCalculatorScale = 1.0F;
      public boolean corpseEspStyleTracer = false;
      public String customSlotPrefillKey = "";
      public String freelookKey = "";
      public boolean freelookToggle = false;
      public String freecamKey = "";
      public boolean freecamToggle = false;
      public boolean cameraDebug = false;
      public boolean tracerLapis = true;
      public boolean tracerTungsten = true;
      public boolean tracerUmber = true;
      public boolean tracerVanguard = true;
      public String diceDisplayMode = "Current";
      public boolean dungeonSecretsTracker = false;
      public boolean dungeonSecretsDebug = false;
      public String dungeonSecretsTriggerMode = "DELAYED"; // "ON_FINISH", "DELAYED"
      public int dungeonSecretsDelay = 3; // seconds
      public int waterOpacity = 100; // 0-100%
      public int lavaOpacity = 100; // 0-100%
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
      public boolean autoCroesusDungeons = true;
      public boolean autoCroesusUseDungeonKey = true;
      public long autoCroesusDungeonKeyProfit = 200000L;
      public long autoCroesusDungeonProfitThreshold = 0L;
      public boolean autoCroesusHud = false;
      public int autoCroesusHudX = 10;
      public int autoCroesusHudY = 200;
      public float autoCroesusHudScale = 1.0F;
      public boolean kuudraDebug = false;
      public boolean kuudraChestDebug = false;
      public boolean dungeonDebug = false;
      public boolean commandDebug = false;
      public boolean greenhouseDebug = false;
      public boolean greenhouseProfitTracker = true;
      public Set<Integer> greenhousePlots = new HashSet();
      public boolean dungeonBossWaypoints = true;
      public boolean dungeonCrystalWaypoints = true;
      public boolean dungeonTerminalWaypoints = true;
      public boolean dungeonBreakWaypoints = true;
      public boolean dungeonTracers = true;
      public boolean dungeonShowAllClassWaypoints = false;
      public boolean dungeonKeyHighlight = true;
      public String dungeonKeyColor = "GOLD";
      public boolean dungeonKeyTracers = true;
      public boolean dungeonStarredMobHighlight = true;
      public String dungeonStarredMobColor = "GOLD";
      public boolean dungeonStarredMobTracers = false;
      public String dungeonMageColor = "aqua";
      public String dungeonBersColor = "red";
      public String dungeonArcherColor = "green";
      public String dungeonTankColor = "gray";
      public String dungeonHealerColor = "light_purple";
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
      public boolean structureFinder = false;
      public boolean structureFinderCorleone1 = true;
      public boolean structureFinderGoldenDragon = true;
      public boolean structureFinderTracers = true;
      public String structureFinderColor = "AQUA";
      public String structurePasteWrongBorderColor = "RED";
      public String structurePasteUnplacedBorderColor = "BROWN";
      public float structurePasteBorderWidth = 1.5F;
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
      public List<CustomPartyCommand> customPartyCommands = new ArrayList();
      public String clipboardRunKey = "";
      public String clipboardRunLastCommandKey = "";
      public boolean swapUnifiedLineColor = false;
      public int swapUnifiedLineColorValue = 0xFF22C55E;
      public boolean swapShowLines = true;
      public float swapLineWidth = 2.0f;
      public String warpedAotvCustomModelOverride = "";
      public int chatHistoryMaxMessages = 500;
      public String vanillaToggleCrouchKey = "";
      public String vanillaToggleAttackKey = "";
      public String vanillaToggleUseKey = "";
      public String priceHistoryKey = "H";
      public String priceHistoryBgColor = "#120824";
      public String priceHistoryBorderColor = "#7C3AED";
      public String priceHistoryLineColor = "#A855F7";
      public boolean priceHistoryShowMayors = true;
      public boolean showBazaarBuySell = false;
      public boolean showAvgLowestBin7d = false;
      public boolean showAvgLowestBin30d = false;
      public String backpackPreviewBgMode = "AUTO"; // AUTO, CUSTOM, VANILLA, TRANSPARENT
      public String backpackPreviewCustomColor = "#1E293B";
      public boolean backpackPreviewItemRarity = true;
      public String backpackPreviewRarityShape = "SQUARE"; // SQUARE, CIRCLE
      public float backpackPreviewCircleSize = 5.0F; // 2.0 - 8.0 radius
      public boolean backpackPreviewItemBorder = false; // 1px rarity outline
      public float backpackPreviewRarityAlpha = 0.35F;
      public String backpackPreviewTrigger = "ALWAYS"; // ALWAYS, ON_KEY
      public String backpackPreviewKey = "LEFT_SHIFT";
      public boolean inventoryItemRarityBg = false; // Rarity colors on normal inventory slots & HUDs
      public boolean autoExpCapsuleEnabled = true;
      public boolean bypassResourcePack = false;
      public boolean autoUpdateSkyblockPack = true;
      public String lastSkyblockPackHash = "";
      public boolean hideArmor = false;
      public boolean hideHelmet = true;
      public boolean hideChestplate = true;
      public boolean hideLeggings = true;
      public boolean hideBoots = true;
      public boolean hideArmorOnlySelf = true;
      public boolean removeSelfInvisibility = false;
      public boolean showEstimatedValue = true;
      public String storageTheme = "Default"; // Default, Dark, Transparent
      public String storageSortMode = "Total Count"; // Total Count, Estimated Value, Name
      public boolean hotspotGoneAlert = false;
      public boolean hotspotAlertCommand = false;
      public String hotspotAlertCommandText = "";
      public boolean hotspotAlertTitle = true;
      public boolean hotspotAlertChat = true;
      public boolean hotspotAlertSound = true;
      public String hotspotAlertSoundName = "Anvil";
      public int hotspotAlertSoundCount = 3;
      public int hotspotAlertSoundDelay = 400;
      public boolean autoHoppityConfirm = false;
      public boolean autoHoppityBuyRabbit = false;
      public int autoHoppityDelayMs = 500;
      public int blockHighlightLimit = 100;
      public int blockHighlightOverallMax = 500;
      public String blockScanMode = "NORMAL"; // NORMAL, LINE
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
      public String particleHighlightsIsland = "";
      public boolean bedwarsEsp = false;
      public boolean bedwarsEspOwnTeam = false;
      public boolean borderlessFullscreen = false;
      public boolean dojoUtilities = false;
      public boolean waypointShowFill = true;
      public int waypointFillOpacity = 30; // 0-100%
      public float waypointBorderWidth = 2.0F;
      public boolean waypointRemoveWhenNear = false;
      public double waypointRemoveDistance = 3.0;
      public boolean orderedWaypointsThroughWalls = true;
      public int orderedWaypointsVisibleCount = 3;
      public java.util.Map<String, OrderedRoute> orderedRoutes = new java.util.LinkedHashMap<>();
      public String activeOrderedRoute = "Default";
      public boolean zamasuWorldWhite = false;

      // Dungeon Map & Clear Tracker Settings
      public boolean dungeonMap = false;
      public float dungeonMapScale = 1.0F;
      public int dungeonMapX = 10;
      public int dungeonMapY = 10;
      public boolean dungeonMapShowFullMap = true;
      public boolean dungeonMapShowPlayerHeads = true;
      public boolean dungeonMapShowSecrets = true;
      public boolean dungeonMapShowRoomNames = true;
      public boolean dungeonMapShowCheckmarks = true;
      public boolean dungeonMapShowClears = true;
      public boolean dungeonMapShowInBoss = false;
      public boolean dungeonMapTimeline = true;
      public boolean dungeonMapDebug = false;

      // Custom Room Colors (HEX/ARGB)
      public String dungeonMapColorEntrance = "#22C55E";
      public String dungeonMapColorNormal = "#92400E";
      public String dungeonMapColorPuzzle = "#8B5CF6";
      public String dungeonMapColorTrap = "#EA580C";
      public String dungeonMapColorYellow = "#EAB308";
      public String dungeonMapColorFairy = "#D946EF";
      public String dungeonMapColorBlood = "#DC2626";
      public boolean sunsGraspAutoEmptySlot = true;
      public boolean armorHud = false;
      public int armorHudX = 10;
      public int armorHudY = 160;
      public float armorHudScale = 1.0F;
      public String armorHudClickAction = "Wardrobe";
      public boolean armorHudVertical = false;
      public boolean armorHudShowInInventory = false;
      public boolean armorHudShowTooltip = true;
      public boolean equipmentHud = false;
      public int equipmentHudX = 10;
      public int equipmentHudY = 220;
      public float equipmentHudScale = 1.0F;
      public String equipmentHudClickAction = "Equipment";
      public boolean equipmentHudVertical = false;   // false = horizontal (1 2 3 4), true = vertical (1\n2\n3\n4)
      public boolean equipmentHudShowInInventory = false; // show HUD when any allowed GUI is open
      public boolean equipmentHudShowTooltip = true;
      public List<String> savedEquipmentNbt = new ArrayList<>();
      public boolean inventoryHud = false;
      public int inventoryHudX = 10;
      public int inventoryHudY = 280;
      public float inventoryHudScale = 1.0F;
      public boolean inventoryHudVertical = false; // false = 9x3 horizontal, true = 3x9 vertical
      public boolean inventoryHudShowInInventory = false;
      public boolean inventoryHudShowTooltip = true;
      public String hudBgColor = "#0F172A";
      public int hudBgAlpha = 170; // 0-255
      public String hudBorderColor = "#00E5FF";
      public int hudBorderAlpha = 68; // 0-255
      public String hudSlotBgColor = "#1E293B";
      public int hudSlotBgAlpha = 68; // 0-255
      public String hudSlotBorderColor = "#FFFFFF";
      public int hudSlotBorderAlpha = 51; // 0-255

      // NoRender (Hider) Settings from NoFrills
      public boolean noRenderExplosions = false;
      public boolean noRenderEmptyTooltips = false;
      public boolean noRenderFireOverlay = false;
      public boolean noRenderBreakParticles = false;
      public boolean noRenderBossBar = false;
      public boolean noRenderArmorBar = false;
      public boolean noRenderFoodBar = false;
      public boolean noRenderHealthBar = false;
      public boolean noRenderFog = false;
      public boolean noRenderEffectDisplay = false;
      public boolean noRenderRecipeBook = false;
      public boolean noRenderSelectedItemName = false;
      public boolean noRenderDeadEntities = false;
      public boolean noRenderDeadPoof = false;
      public boolean noRenderLightning = false;
      public boolean noRenderFallingBlocks = false;
      public boolean noRenderEntityFire = false;
      public boolean noRenderMageBeam = false;
      public boolean noRenderIceSpray = false;
      public boolean hideSecondHand = false;

      public int getHudBgColorArgb() {
         return BomboRenderUtils.parseHexColorWithAlpha(hudBgColor, hudBgAlpha, 0xAA0F172A);
      }
      public int getHudBorderColorArgb() {
         return BomboRenderUtils.parseHexColorWithAlpha(hudBorderColor, hudBorderAlpha, 0x4400E5FF);
      }
      public int getHudSlotBgColorArgb() {
         return BomboRenderUtils.parseHexColorWithAlpha(hudSlotBgColor, hudSlotBgAlpha, 0x441E293B);
      }
      public int getHudSlotBorderColorArgb() {
         return BomboRenderUtils.parseHexColorWithAlpha(hudSlotBorderColor, hudSlotBorderAlpha, 0x33FFFFFF);
      }
      public boolean fixSkyblockF3Day = true;
      public boolean pestDebug = false;
      public boolean estimatedValueBazaarMode = false;
      public boolean estimatedValuePreferCheapest = true;
      public boolean estimatedValueFullBreakdown = true;
      public boolean hiderEnabled = true;
      public List<EntityHideRule> hiddenEntities = new ArrayList<>();
      public List<BlockReplaceRule> blockReplacements = new ArrayList<>();
      public boolean ringPartyInvite = true;
   }

   public static class EntityHideRule {
      public String matcher = "";
      public String island = "";
      public String subarea = "";
      public boolean enabled = true;

      public EntityHideRule() {}

      public EntityHideRule(String matcher, String island, String subarea) {
         this.matcher = matcher;
         this.island = island;
         this.subarea = subarea;
         this.enabled = true;
      }
   }

   public static class BlockReplaceRule {
      public String fromBlock = "";
      public String toBlock = "";
      public String island = "";
      public String subarea = "";
      public boolean preserveProperties = true;
      public boolean enabled = true;

      public BlockReplaceRule() {}

      public BlockReplaceRule(String fromBlock, String toBlock, String island, String subarea, boolean preserveProperties) {
         this.fromBlock = fromBlock;
         this.toBlock = toBlock;
         this.island = island;
         this.subarea = subarea;
         this.preserveProperties = preserveProperties;
         this.enabled = true;
      }
   }

   public static class ProfileAutoSwapRule {
      public String targetProfile = "";
      public String island = "";
      public String subarea = "";
      public String dungeonClass = "";
      public String armorRequirement = "";
      public String chatMessage = "";
      public boolean enabled = true;

      public ProfileAutoSwapRule() {}

      public ProfileAutoSwapRule(String targetProfile, String island, String subarea, String dungeonClass, String armorRequirement, String chatMessage) {
         this.targetProfile = targetProfile != null ? targetProfile : "";
         this.island = island != null ? island : "";
         this.subarea = subarea != null ? subarea : "";
         this.dungeonClass = dungeonClass != null ? dungeonClass : "";
         this.armorRequirement = armorRequirement != null ? armorRequirement : "";
         this.chatMessage = chatMessage != null ? chatMessage : "";
         this.enabled = true;
      }
   }

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

   public static class CustomItemOverride {
      public String material = "";
      public String name = "";
      public String lore = "";
      public Boolean enchanted = null; // null = keep original, true = force glint, false = force no glint
      public String armorColor = ""; // hex e.g. "#7FFFD4" or dye name

      public CustomItemOverride() {
      }

      public CustomItemOverride(String material, String name) {
         this(material, name, "", null, "");
      }

      public CustomItemOverride(String material, String name, String lore) {
         this(material, name, lore, null, "");
      }

      public CustomItemOverride(String material, String name, String lore, Boolean enchanted) {
         this(material, name, lore, enchanted, "");
      }

      public CustomItemOverride(String material, String name, String lore, Boolean enchanted, String armorColor) {
         this.material = material;
         this.name = name;
         this.lore = lore != null ? lore : "";
         this.enchanted = enchanted;
         this.armorColor = armorColor != null ? armorColor : "";
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

   public static class OrderedRoute {
      public String name = "Default";
      public String requiredIsland = "";
      public String requiredArmor = "";
      public boolean enabled = true;
      public String color = "Aqua";
      public boolean collapsed = false;
      public java.util.List<OrderedWaypointData> waypoints = new java.util.ArrayList<>();

      public OrderedRoute() {}

      public OrderedRoute(String name) {
         this.name = name;
      }
   }

   public static class OrderedWaypointData {
      public double x;
      public double y;
      public double z;
      public String name = "Waypoint";
      public String color = "Aqua";
      public String requiredIsland = "";
      public String requiredArmor = "";
      public boolean enabled = true;

      public OrderedWaypointData() {}

      public OrderedWaypointData(double x, double y, double z, String name, String color) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.name = name != null ? name : "Waypoint";
         this.color = color != null ? color : "Aqua";
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
      public double cooldownSeconds = 3.0;
      public transient boolean wasInside = false;
      public transient long lastTriggeredTime = 0L;

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

   public static class HudStyle {
      public String backgroundColor = "#90000000";
      public String borderColor = "#FFAA00";
      public String titleColor = "#FFAA00";
      public String textColor = "#FFFFFF";

      public HudStyle() {
      }

      public HudStyle(String bg, String border, String title, String text) {
         this.backgroundColor = bg;
         this.borderColor = border;
         this.titleColor = title;
         this.textColor = text;
      }

      public int getBackgroundColorInt() {
         return parseHexColor(backgroundColor, 0x90000000);
      }

      public int getBorderColorInt() {
         return parseHexColor(borderColor, 0xFFFFAA00);
      }

      public int getTitleColorInt() {
         return parseHexColor(titleColor, 0xFFFFAA00);
      }

      public int getTextColorInt() {
         return parseHexColor(textColor, 0xFFFFFFFF);
      }

      public static int parseHexColor(String hex, int defaultCol) {
         if (hex == null || hex.isEmpty()) return defaultCol;
         String clean = hex.trim();
         if (clean.startsWith("#")) clean = clean.substring(1);
         if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2);
         try {
            long val = Long.parseLong(clean, 16);
            if (clean.length() <= 6) {
               return (int) (0xFF000000L | val);
            }
            return (int) val;
         } catch (Throwable t) {
            return defaultCol;
         }
      }
   }
}
