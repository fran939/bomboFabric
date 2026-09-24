package me.bombo.bomboaddons.gui.config;

import me.bombo.bomboaddons.*;
import me.bombo.bomboaddons.HudMoveScreen;
import me.bombo.bomboaddons.HudMoveScreen.HudTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ConfigRegistry {
    /** Estimated-value price source labels (see {@link #applyValueSource}). */
    public static final String VALUE_SOURCE_BUY = "Instant Buy (Lowest BIN)";
    public static final String VALUE_SOURCE_SELL = "Instant Sell (Bazaar Sell Offer / Average BIN)";

    /** True when estimated values should use sell-side prices. */
    public static boolean sellsAtValueSource(BomboConfig.Settings s) {
        if (s == null) return false;
        if (s.estimatedValueBazaarMode) return true;
        return "INSTANT_SELL".equalsIgnoreCase(s.priceSourceMode);
    }

    /** Writes both the new mode string and the legacy boolean so they can never disagree. */
    public static void applyValueSource(BomboConfig.Settings s, String label) {
        if (s == null) return;
        boolean sell = label != null && label.equalsIgnoreCase(VALUE_SOURCE_SELL);
        s.priceSourceMode = sell ? "INSTANT_SELL" : "INSTANT_BUY";
        s.estimatedValueBazaarMode = sell;
    }

    // Hidden cheat categories when "hideCheats" is enabled. "Auto" is deliberately NOT in
    // this set: the sequence editor is shared (only the executor is cheat-only), and hiding it
    // in the legit build made the whole section look like it had vanished.
    public static final Set<String> CHEAT_CATEGORIES = Set.of(
            "Clicker",
            "Experiments",
            // Automation that plays the game for you: only the bomboclient build shows it.
            "Auto Croesus"
    );

    // List of standard categories ordered alphabetically (excluding GUI Settings and Debug which sit at the bottom)
    public static final List<String> BASE_CATEGORIES = List.of(
            "Aliases",
            "Anvil",
            "Auto",
            "Auto Croesus",
            // Display alias so the category reads "Sequences" in the GUI. The internal id,
            // /b auto commands and config schema all keep using "Auto".
            // (see CATEGORY_DISPLAY_NAMES)
            "Bedwars",
            "Block Highlights",
            "Blocked Slots",
            "Chat Modifier",
            "Chat Triggers",
            "Clicker",
            "Coord Binds",
            "Critters",
            "Custom Crosshair",
            "Custom Slots",
            "Custom Tracers",
            "Dungeons",
            "Entity & Block Hider",
            "Experiments",
            "Fishing",
            "Garden",
            "General",
            "Highlights",
            "Hotkeys",
            "HUDs",
            "Item Highlights",
            "Keybinds",
            "Kuudra",
            "Lore Additions",
            "Mining",
            "Misc",
            "Ordered Waypoints",
            "Particle Highlights",
            "Pets",
            "Profiles",
            "Sounds",
            "Timers",
            "Uncategorized",
            "Wardrobe",
            "Waypoints",
            "Widgets"
    );

    /**
     * GUI display names for internal category ids. The organizer file, /b order and the
     * config schema keep using the internal id; only what the user reads changes.
     */
    public static final Map<String, String> CATEGORY_DISPLAY_NAMES = Map.of(
            "Auto", "Sequences"
    );

    /** Inverse of {@link #CATEGORY_DISPLAY_NAMES}: display name to internal id. */
    public static String internalCategoryName(String displayOrInternal) {
        if (displayOrInternal == null) return null;
        for (Map.Entry<String, String> e : CATEGORY_DISPLAY_NAMES.entrySet()) {
            if (e.getValue().equalsIgnoreCase(displayOrInternal.trim())) return e.getKey();
        }
        return displayOrInternal.trim();
    }

    public static String displayCategoryName(String internal) {
        if (internal == null) return null;
        return CATEGORY_DISPLAY_NAMES.getOrDefault(internal, internal);
    }

    public static List<String> getAllProfileNames() {
        BomboConfig.Settings s = BomboConfig.get();
        Set<String> set = new LinkedHashSet<>();
        set.add("default");
        set.add("General");

        if (s.profileBinds != null) set.addAll(s.profileBinds.keySet());
        if (s.keybindBinds != null) set.addAll(s.keybindBinds.keySet());
        if (s.coordBinds != null) set.addAll(s.coordBinds.keySet());
        if (s.customWaypoints != null) set.addAll(s.customWaypoints.keySet());
        if (s.profileChatTriggers != null) set.addAll(s.profileChatTriggers.keySet());
        return new ArrayList<>(set);
    }

    public static List<String> getAvailableCategories() {
        BomboConfig.Settings s = BomboConfig.get();
        List<String> list = new ArrayList<>();
        List<String> activeCats = FeatureOrganizerManager.getOrganizerCategories();
        for (String cat : activeCats) {
            // Cheat categories only exist in the bomboclient build, and even there they
            // respect the /b hide toggle. In the legit build the whole category is absent.
            if (CHEAT_CATEGORIES.contains(cat)
                    && (!me.bombo.bomboaddons.flavor.Flavor.get().isCheat() || s.hideCheats)) {
                continue;
            }
            list.add(cat);
        }
        // At the bottom: GUI Settings right on top of Debug
        if (!list.contains("GUI Settings")) list.add("GUI Settings");
        if (!list.contains("Debug")) list.add("Debug");
        if (BomboConfig.isDev() && !list.contains("Dev")) {
            list.add("Dev");
        }
        return list;
    }

    public static List<ConfigItem> getRegisteredItemsForCategory(String category) {
        BomboConfig.Settings s = BomboConfig.get();
        List<ConfigItem> items = new ArrayList<>();
        List<String> allProfiles = getAllProfileNames();

        switch (category) {
            case "Aliases" -> {
                items.add(ConfigItem.header("Custom Command Aliases (/shortcut -> /command)", category));
                items.add(ConfigItem.customCard("Command Aliases Manager", category,
                        ConfigCustomWidgets.getAliasesCardHeight(),
                        ConfigCustomWidgets::renderAliasesCard,
                        ConfigCustomWidgets::handleAliasesCardClick));

                items.add(ConfigItem.header("General Command Settings", category));
                items.add(ConfigItem.toggle("SBE Command Aliases", "Enables shorthand commands like /nw, /cata, /skills, /slayer.", category, () -> s.sbeCommands, v -> s.sbeCommands = v));
                items.add(ConfigItem.toggle("Clickable Chat Commands", "Makes command names clickable in chat messages.", category, () -> s.clickableChatCommands, v -> s.clickableChatCommands = v));
                items.add(ConfigItem.toggle("Show Command on Hover", "Displays full command preview when hovering alias in chat.", category, () -> s.showCommandOnHover, v -> s.showCommandOnHover = v));
            }

            case "Anvil" -> {
                items.add(ConfigItem.header("Auto Anvil Salvage & Combining", category));
                items.add(ConfigItem.toggle("Auto Anvil Combine", "Automatically combines matching books and items in anvil.", category, () -> s.anvilAutoCombineEnabled, v -> s.anvilAutoCombineEnabled = v));
                items.add(ConfigItem.sliderInt("Combine Delay", "Delay between anvil clicks in milliseconds.", category, 50, 500, 10, "ms", () -> s.anvilAutoCombineDelay > 0 ? s.anvilAutoCombineDelay : 200, v -> s.anvilAutoCombineDelay = v));
                items.add(ConfigItem.keybind("Anvil Combine Key", "Optional keybind required to execute anvil combine.", category, () -> s.anvilAutoCombineKey != null ? s.anvilAutoCombineKey : "", v -> s.anvilAutoCombineKey = v));
                items.add(ConfigItem.toggle("Require Keybind to Combine", "Only runs auto anvil when the designated keybind is held.", category, () -> s.anvilAutoCombineRequireKey, v -> s.anvilAutoCombineRequireKey = v));
            }

            case "Auto" -> {
                // Available in both flavors: creating, editing and keybinding sequences is
                // shared data. Running one needs the sequence runtime, which only the cheat
                // build ships - the card says so instead of pretending otherwise.
                boolean canRun = me.bombo.bomboaddons.features.auto.AutoSequenceManager.hasRuntime();
                items.add(ConfigItem.header("Custom Automation Sequences", category));
                if (canRun) {
                    items.add(ConfigItem.header("Tip: type /b auto in chat to toggle a sequence by keybind or command.", category));
                }
                if (!canRun) {
                    items.add(ConfigItem.header("Read-only on this build: \"/b auto run\" and the RUN "
                            + "button need " + me.bombo.bomboaddons.Constants.artifactPrefix() + "'s sequence "
                            + "runtime.", category));
                }
                items.add(ConfigItem.dynamicCustomCard("Auto Sequences Manager", category,
                        ConfigCustomWidgets::getAutoSequencesCardHeight,
                        ConfigCustomWidgets::renderAutoSequencesCard,
                        ConfigCustomWidgets::handleAutoSequencesCardClick));
            }

            case "Bedwars" -> {
                items.add(ConfigItem.header("Bedwars Overlays & Helpers", category));
                items.add(ConfigItem.toggle("Bedwars Player ESP", "Outlines enemy Bedwars players in 3D world.", category, () -> s.bedwarsEsp, v -> s.bedwarsEsp = v));
                items.add(ConfigItem.toggle("Highlight Own Team", "Also outlines teammates in Bedwars.", category, () -> s.bedwarsEspOwnTeam, v -> s.bedwarsEspOwnTeam = v));
            }

            case "Block Highlights" -> {
                items.add(ConfigItem.header("Block ESP & Highlights", category));
                items.add(ConfigItem.toggle("Block Highlights Enabled", "Master toggle for highlighting blocks in the world.", category, () -> s.blockHighlightsEnabled, v -> s.blockHighlightsEnabled = v));
                items.add(ConfigItem.sliderInt("Highlight Limit", "Maximum count of blocks to highlight simultaneously (0 = Unlimited).", category, 0, 500, 10, " blocks", () -> s.blockHighlightLimit >= 0 ? s.blockHighlightLimit : 100, v -> s.blockHighlightLimit = v));
                items.add(ConfigItem.cycle("Scan Mode", "Algorithm used for scanning matching blocks in world.", category, List.of("NORMAL", "LINE"), () -> s.blockScanMode != null ? s.blockScanMode : "NORMAL", v -> s.blockScanMode = v));
                items.add(ConfigItem.sliderInt("Block Scan Radius", "Search radius in blocks around player.", category, 5, 256, 1, "m", () -> s.blockScanRadius > 0 ? s.blockScanRadius : 20, v -> s.blockScanRadius = v));

                // Block Highlights Manager Card
                items.add(ConfigItem.dynamicCustomCard("Block Highlights Manager", category,
                        ConfigCustomWidgets::getBlockHighlightCardHeight,
                        ConfigCustomWidgets::renderBlockHighlightsCard,
                        ConfigCustomWidgets::handleBlockHighlightsClick));
            }

            case "Blocked Slots" -> {
                items.add(ConfigItem.header("Inventory Slot Protection", category));
                items.add(ConfigItem.keybind("Bypass Keybind", "Keybind held to temporarily bypass slot protection.", category, () -> s.blockedSlotsBypassKey != null ? s.blockedSlotsBypassKey : "LSHIFT", v -> s.blockedSlotsBypassKey = v));
                items.add(ConfigItem.toggle("Prevent Slot Swap on Gui Keybind", "Blocks hotbar number swapping into protected slots.", category, () -> s.preventSlotSwapOnGuiKeybind, v -> s.preventSlotSwapOnGuiKeybind = v));
                items.add(ConfigItem.header("Inventory Slot Swapping", category));
                items.add(ConfigItem.toggle("Inventory Slot Swapper", "Enables holding hotkey to link inventory slots to hotbar slots, and Shift-clicking to swap.", category, () -> s.inventorySlotSwapEnabled, v -> s.inventorySlotSwapEnabled = v));
                items.add(ConfigItem.keybind("Slot Swapper Hotkey", "Keybind held inside containers to bind/unbind inventory slots.", category, () -> s.inventorySlotSwapKey != null ? s.inventorySlotSwapKey : "X", v -> s.inventorySlotSwapKey = v));
            }

            case "Chat Modifier" -> {
                items.add(ConfigItem.header("Chat QoL & Features", category));
                items.add(ConfigItem.hudToggle("In-Chat Search Bar", "Live interactive search filter bar directly inside chat.", category, () -> s.chatSearchBar, v -> s.chatSearchBar = v, HudTarget.CHAT_SEARCH));
                items.add(ConfigItem.toggle("Chat Search Background", "Draws dark background behind the in-chat search box.", category, () -> s.chatSearchBackground, v -> s.chatSearchBackground = v));
                items.add(ConfigItem.toggle("Copy Chat to Clipboard", "Click or right-click any chat message to copy text directly to clipboard.", category, () -> s.copyChat, v -> s.copyChat = v));
                items.add(ConfigItem.toggle("Clickable Chat Commands", "Clickable /warp, /trade, and party command links in chat.", category, () -> s.clickableChatCommands, v -> s.clickableChatCommands = v));
                items.add(ConfigItem.keybind("Chat Peek Key", "Hold keybind to peek chat history while moving without opening chat box.", category, () -> s.chatPeekKey != null ? s.chatPeekKey : "y", v -> s.chatPeekKey = v));
            }

            case "Chat Triggers" -> {
                items.add(ConfigItem.header("Automated Chat Triggers", category));
                items.add(ConfigItem.cycle("Active Profile", "Select configuration profile for triggers.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));
                items.add(ConfigItem.dynamicCustomCard("Chat Triggers Manager", category,
                        ConfigCustomWidgets::getChatTriggersCardHeight,
                        ConfigCustomWidgets::renderChatTriggersCard,
                        ConfigCustomWidgets::handleChatTriggersCardClick));
            }

            case "Clicker" -> {
                items.add(ConfigItem.header("Auto Clicker Logic", category));
                items.add(ConfigItem.dynamicCustomCard("Clicker Targets Manager", category,
                        ConfigCustomWidgets::getClickerCardHeight,
                        ConfigCustomWidgets::renderClickerCard,
                        ConfigCustomWidgets::handleClickerCardClick));
            }

            case "Coord Binds" -> {
                items.add(ConfigItem.header("Coordinate-Based Command Triggers", category));
                items.add(ConfigItem.cycle("Active Profile", "Select configuration profile for coordinate binds.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));

                // Dedicated Coordinate Command Binds Card
                items.add(ConfigItem.customCard("Coordinate Command Binds", category,
                        ConfigCustomWidgets.getCoordBindsCardHeight(),
                        ConfigCustomWidgets::renderCoordBindsManager,
                        ConfigCustomWidgets::handleCoordBindsClick));
            }

            case "Critters" -> {
                items.add(ConfigItem.header("Critter Safari Trackers & Overlays", category));
                items.add(ConfigItem.hudToggle("Critter Safari HUD", "Displays live party and personal Critterdex captures per biome.", category, () -> s.critterHud, v -> s.critterHud = v, HudTarget.CRITTER_HUD));
                items.add(ConfigItem.hudToggle("Critter Safari Minimap", "Displays live overhead 2D/3D map of the safari island and player positions.", category, () -> s.critterMapHud, v -> s.critterMapHud = v, HudTarget.CRITTER_MAP));
                items.add(ConfigItem.cycle("Tracking Mode", "Choose between tracking only unique species (1/biome) or all catches / spawn quotas.", category,
                        List.of("Uniques", "All"),
                        () -> s.critterTrackingMode != null ? s.critterTrackingMode : "Uniques",
                        v -> s.critterTrackingMode = v));
                items.add(ConfigItem.toggle("Show Missing On Hover", "Shows list of missing species per biome when hovering biome progress or in GUI.", category, () -> s.critterShowMissing, v -> s.critterShowMissing = v));
                items.add(ConfigItem.toggle("Per-Player Breakdown", "Shows unique and total critter catches per party member.", category, () -> s.critterShowPerPlayer, v -> s.critterShowPerPlayer = v));
                items.add(ConfigItem.toggle("Safari Island Only", "Hide HUD when not on Critter Safari island.", category, () -> s.critterOnlyInSafari, v -> s.critterOnlyInSafari = v));
                items.add(ConfigItem.toggle("Highlight Mobs", "Highlights missing unique critters in current biome with glowing boxes.", category, () -> s.critterHighlightMobs, v -> s.critterHighlightMobs = v));
                items.add(ConfigItem.toggle("Critter Tracers", "Draws tracer line to the closest missing unique critter in current biome.", category, () -> s.critterTracers, v -> s.critterTracers = v));
                items.add(ConfigItem.toggle("Highlight Strings", "Highlights string cobwebs in current biome and traces the closest string.", category, () -> s.critterHighlightStrings, v -> s.critterHighlightStrings = v));
                items.add(ConfigItem.toggle("Highlight Bee Nests", "Highlights bee nest tree spots in Forest biome until Honeybug is captured.", category, () -> s.critterHighlightBeeNests, v -> s.critterHighlightBeeNests = v));
                items.add(ConfigItem.toggle("Highlight Breakable Walls", "Highlights breakable walls in Cavern biome until Snoozle is captured.", category, () -> s.critterHighlightWalls, v -> s.critterHighlightWalls = v));
                items.add(ConfigItem.toggle("Auto Accept Hideyho", "Automatically sends /selectnpcoption hideyho r_4_1 when prompted.", category, () -> s.autoAcceptHideyho, v -> s.autoAcceptHideyho = v));
                items.add(ConfigItem.cycle("Hideyho Nav Mode", "Choose whether to auto-run /shnav <coords> or draw a blue tracer after Hideyho teleports.", category,
                        List.of("shnav", "Tracer"),
                        () -> s.hideyhoNavMode != null ? s.hideyhoNavMode : "shnav",
                        v -> s.hideyhoNavMode = v));
                items.add(ConfigItem.toggle("Capsule Trajectory", "Visualizes projectile arc when holding Critter Capsule or Masterful Capsule.", category, () -> s.critterCapsuleTrajectory, v -> s.critterCapsuleTrajectory = v));
                items.add(ConfigItem.toggle("Minimap 3D View", "Toggles between 2D flat aerial minimap and 3D isometric perspective view.", category, () -> s.critterMap3D, v -> s.critterMap3D = v));
                items.add(ConfigItem.cycle("Minimap Player Icon", "Choose display style for players on the minimap.", category,
                        List.of("Small Icon", "Player Head", "Head + Name"),
                        () -> s.critterMapPlayerStyle != null ? s.critterMapPlayerStyle : "Small Icon",
                        v -> s.critterMapPlayerStyle = v));
                items.add(ConfigItem.cycle("Completion Notification", "Alert when all uniques in a biome or safari are finished.", category,
                        List.of("Disabled", "Chat (Self)", "Party (/pc)"),
                        () -> s.critterCompletionNotify != null ? s.critterCompletionNotify : "Chat (Self)",
                        v -> s.critterCompletionNotify = v));
                items.add(ConfigItem.color("Sparkling Tracer Color", "Tracer and ESP outline color for Sparkling mobs.", category, () -> s.sparklingTracerColor != null ? s.sparklingTracerColor : "LIGHT_PURPLE", v -> s.sparklingTracerColor = v));
                items.add(ConfigItem.sliderFloat("HUD Scale", "Scale multiplier for Critter Safari HUD overlay.", category, 0.5f, 2.5f, 0.1f, "x", () -> s.critterHudScale > 0 ? s.critterHudScale : 1.0f, v -> s.critterHudScale = v));
                items.add(ConfigItem.sliderFloat("Minimap Scale", "Scale multiplier for Safari Minimap overlay.", category, 0.5f, 2.5f, 0.1f, "x", () -> s.critterMapScale > 0 ? s.critterMapScale : 1.0f, v -> s.critterMapScale = v));
            }

            case "Custom Crosshair" -> {
                items.add(ConfigItem.header("Custom 15x15 Crosshair Designer", category));
                items.add(ConfigItem.toggle("Custom Crosshair Enabled", "Render custom designed pixel crosshair instead of vanilla.", category, () -> s.customCrosshair.enabled, v -> s.customCrosshair.enabled = v));
                items.add(ConfigItem.toggle("Black Pixel Outline", "Draw crisp black border around each active pixel.", category, () -> s.customCrosshair.outline, v -> s.customCrosshair.outline = v));
                items.add(ConfigItem.toggle("Smooth Chroma Animation", "Cycle RGB colors dynamically across the crosshair.", category, () -> s.customCrosshair.chroma, v -> s.customCrosshair.chroma = v));
                items.add(ConfigItem.color("Crosshair Color", "Static tint color when Chroma is disabled.", category, () -> s.customCrosshair.color != null ? s.customCrosshair.color : "WHITE", v -> s.customCrosshair.color = v));
                items.add(ConfigItem.sliderFloat("Crosshair Scale", "Render size multiplier.", category, 0.5f, 3.0f, 0.1f, "x", () -> s.customCrosshair.scale > 0 ? s.customCrosshair.scale : 1.0f, v -> s.customCrosshair.scale = v));

                // Interactive 15x15 Canvas Card Widget
                items.add(ConfigItem.customCard("Interactive Canvas & Presets", category, 265,
                        ConfigCustomWidgets::renderCrosshairDesigner,
                        ConfigCustomWidgets::handleCrosshairClick));
            }

            case "Custom Slots" -> {
                items.add(ConfigItem.header("Custom Inventory GUI Shortcut Slots", category));
                items.add(ConfigItem.cycle("Active Profile", "Select configuration profile for custom slots.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));
            }

            case "Custom Tracers" -> {
                items.add(ConfigItem.header("Custom Entity Tracer Lines", category));
                items.add(ConfigItem.cycle("Active Profile", "Select configuration profile for tracers.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));
            }

            case "Auto Croesus" -> {
                items.add(ConfigItem.header("Auto Croesus (Automation)", category));
                items.add(ConfigItem.toggle("Auto Croesus", "Master switch: claims/rerolls Croesus chests automatically.", category,
                        () -> s.autoCroesus, v -> s.autoCroesus = v));
                items.add(ConfigItem.toggle("Buy Paid Chests", "Claims paid chests (Kismet/keys) when profitable.", category,
                        () -> s.autoCroesusBuyPaid, v -> s.autoCroesusBuyPaid = v));
                items.add(ConfigItem.sliderInt("Action Delay", "Milliseconds between automated container actions.", category,
                        50, 2000, 25, "ms", () -> (int) s.autoCroesusDelay, v -> s.autoCroesusDelay = v));

                items.add(ConfigItem.header("Kismet Feather", category));
                items.add(ConfigItem.toggle("Auto Reroll With Kismet", "Rerolls a chest with a Kismet Feather when its value is below the threshold.", category,
                        () -> s.autoKismet, v -> s.autoKismet = v));
                if (s.autoKismet) {
                    items.add(ConfigItem.sliderCoins("Kismet Threshold", "Reroll when chest value is below this amount (100K - 3M).", category,
                            100000L, 3000000L, 50000L,
                            () -> s.kismetThreshold, v -> s.kismetThreshold = v));
                    items.add(ConfigItem.sliderInt("Kismet Delay", "Milliseconds to wait after a Kismet reroll.", category,
                            50, 2000, 25, "ms", () -> (int) s.autoCroesusKismetDelay, v -> s.autoCroesusKismetDelay = v));
                }
                items.add(ConfigItem.toggle("Reroll Below Value", "Rerolls when chest value is under the value below.", category,
                        () -> s.autoCroesusReroll, v -> s.autoCroesusReroll = v));
                if (s.autoCroesusReroll) {
                    items.add(ConfigItem.sliderCoins("Reroll Value", "Reroll when chest value is below this amount (100K - 3M).", category,
                            100000L, 3000000L, 50000L,
                            () -> s.autoCroesusRerollValue, v -> s.autoCroesusRerollValue = v));
                }

                items.add(ConfigItem.header("Dungeon Chests", category));
                items.add(ConfigItem.toggle("Auto Croesus Dungeons", "Automatically claims profitable dungeon chests with /b ac.", category,
                        () -> s.autoCroesusDungeons, v -> s.autoCroesusDungeons = v));
                items.add(ConfigItem.sliderInt("Chest Profit Threshold", "Only claim dungeon chests above this profit (millions).", category,
                        0, 500, 1, "m",
                        () -> (int) (s.autoCroesusDungeonProfitThreshold / 1000000L), v -> s.autoCroesusDungeonProfitThreshold = (long) v * 1000000L));
                items.add(ConfigItem.cycle("Chest Panel Mode", "NET_ONLY shows just the profit per chest; ITEMIZED lists every parsed item.", category,
                        List.of("ITEMIZED", "NET_ONLY"),
                        () -> s.croesusProfitHudMode != null ? s.croesusProfitHudMode : "ITEMIZED",
                        v -> s.croesusProfitHudMode = v));
                items.add(ConfigItem.toggle("Use Dungeon Key", "Buys the bedrock chest key for the floors above.", category,
                        () -> s.autoCroesusUseDungeonKey, v -> s.autoCroesusUseDungeonKey = v));
                items.add(ConfigItem.cycle("Dungeon Key Mode", "MANUAL uses your threshold; AUTO prices the key from the live Bazaar (DUNGEON_CHEST_KEY) and adds the safety margin.", category,
                        List.of("MANUAL", "AUTO"),
                        () -> s.autoCroesusDungeonKeyMode != null ? s.autoCroesusDungeonKeyMode : "MANUAL",
                        v -> s.autoCroesusDungeonKeyMode = v));
                if ("AUTO".equalsIgnoreCase(s.autoCroesusDungeonKeyMode)) {
                    items.add(ConfigItem.sliderCoins("Key Safety Margin", "Extra profit a second chest must clear on top of the live key price.", category,
                            0L, 1000000L, 50000L,
                            () -> s.autoCroesusDungeonKeySafetyMargin, v -> s.autoCroesusDungeonKeySafetyMargin = v));
                } else {
                    items.add(ConfigItem.sliderCoins("Dungeon Key Value", "Profit a second chest must beat to justify buying a dungeon key (100K - 3M).", category,
                            100000L, 3000000L, 50000L,
                            () -> s.autoCroesusDungeonKeyProfit, v -> s.autoCroesusDungeonKeyProfit = v));
                }

                items.add(ConfigItem.header("Displays", category));
                items.add(ConfigItem.hudToggle("Auto Croesus HUD", "Live analysis of the current chest run.", category,
                        () -> s.autoCroesusHud, v -> s.autoCroesusHud = v, HudTarget.AUTO_CROESUS));
                items.add(ConfigItem.hudToggle("Croesus Chest Values", "Every chest in the open overview with contents and profit.", category,
                        () -> s.croesusHelper, v -> s.croesusHelper = v, HudTarget.CROESUS_PROFIT));
                items.add(ConfigItem.hudToggle("Croesus Profit Tracker", "Cumulative profit, runs and a per-floor breakdown.", category,
                        () -> s.croesusProfitTracker, v -> s.croesusProfitTracker = v, HudTarget.CROESUS_TRACKER));
                items.add(ConfigItem.toggle("Debug Highlight Mode", "Simulation: highlights the slot it would click instead of clicking it.", category,
                        () -> me.bombo.bomboaddons.cheat.automation.AutoCroesus.debugHighlightMode,
                        v -> {
                            me.bombo.bomboaddons.cheat.automation.AutoCroesus.debugHighlightMode = v;
                            me.bombo.bomboaddons.cheat.automation.AutoCroesus.active = v;
                        }));
            }

            case "Dungeons" -> {
                items.add(ConfigItem.header("Dungeons Solvers & Features", category));
                items.add(ConfigItem.hudToggle("Croesus Helper", "Displays unlooted chests and profit in Croesus GUI.", category, () -> s.croesusHelper, v -> s.croesusHelper = v, HudTarget.AUTO_CROESUS));
                items.add(ConfigItem.toggle("Auto Croesus Dungeons", "Automatically claims profitable dungeon chests with /b ac.", category, () -> s.autoCroesusDungeons, v -> s.autoCroesusDungeons = v));
                items.add(ConfigItem.toggle("Dungeon Secrets Tracker", "Tracks all secrets of your party on a dungeon", category, () -> s.dungeonSecretsTracker, v -> s.dungeonSecretsTracker = v));
                if (s.dungeonSecretsTracker) {
                    items.add(ConfigItem.cycle("Secrets Trigger Mode", "When to calculate/display party secrets summary.", category,
                            List.of("DELAYED", "ON_FINISH"),
                            () -> s.dungeonSecretsTriggerMode != null ? s.dungeonSecretsTriggerMode : "DELAYED",
                            v -> s.dungeonSecretsTriggerMode = v));
                    if ("DELAYED".equalsIgnoreCase(s.dungeonSecretsTriggerMode)) {
                        items.add(ConfigItem.sliderInt("Fetch Delay", "Seconds to wait after boss kill message before displaying secrets.", category,
                                0, 10, 1, "s",
                                () -> s.dungeonSecretsDelay >= 0 ? s.dungeonSecretsDelay : 3,
                                v -> s.dungeonSecretsDelay = v));
                    }
                }
                items.add(ConfigItem.toggle("Clear Info HUD", "Displays dungeon clear progress and room counts.", category, () -> s.clearInfoHud, v -> s.clearInfoHud = v));
                items.add(ConfigItem.hudToggle("Pad Timers Purple", "Countdown timer overlay for M7 purple pad.", category, () -> s.padTimersPurple, v -> s.padTimersPurple = v, HudTarget.PAD_TIMERS));
                items.add(ConfigItem.hudToggle("Pad Timers Green", "Countdown timer overlay for M7 green pad.", category, () -> s.padTimersGreen, v -> s.padTimersGreen = v, HudTarget.PAD_TIMERS));
                items.add(ConfigItem.toggle("Dungeon Big Hitbox", "Expands hitbox sizes for bats and levers.", category, () -> s.dungeonBigHitbox, v -> s.dungeonBigHitbox = v));
                items.add(ConfigItem.toggle("Dungeon Boss Waypoints", "3D waypoints for dungeon boss spawns.", category, () -> s.dungeonBossWaypoints, v -> s.dungeonBossWaypoints = v));
                items.add(ConfigItem.toggle("Dungeon Crystal Waypoints", "3D markers for energy crystals.", category, () -> s.dungeonCrystalWaypoints, v -> s.dungeonCrystalWaypoints = v));
                items.add(ConfigItem.toggle("Dungeon Terminal Waypoints", "3D markers for F7 terminals and levers.", category, () -> s.dungeonTerminalWaypoints, v -> s.dungeonTerminalWaypoints = v));
                items.add(ConfigItem.toggle("Dungeon Key Highlight", "Outlines dropped Wither & Blood Keys.", category, () -> s.dungeonKeyHighlight, v -> s.dungeonKeyHighlight = v));
                items.add(ConfigItem.toggle("M4/F4 Etherwarp Helper", "Highlights the etherwarp target block (27, 81, 18) during F4/M4 boss.", category, () -> s.m4EtherwarpHelper, v -> s.m4EtherwarpHelper = v));
                items.add(ConfigItem.color("Dungeon Key Color", "Color for key outline.", category, () -> s.dungeonKeyColor != null ? s.dungeonKeyColor : "GOLD", v -> s.dungeonKeyColor = v));
                items.add(ConfigItem.toggle("Starred Mob Highlight", "Outlines starred dungeon mobs in rooms.", category, () -> s.dungeonStarredMobHighlight, v -> s.dungeonStarredMobHighlight = v));
                items.add(ConfigItem.color("Starred Mob Color", "Highlight color for starred mobs.", category, () -> s.dungeonStarredMobColor != null ? s.dungeonStarredMobColor : "GOLD", v -> s.dungeonStarredMobColor = v));
            }

            case "Entity & Block Hider" -> {
                items.add(ConfigItem.header("Entity & Block Hider (Anti-Lag & Custom Model Rendering)", category));
                items.add(ConfigItem.toggle("Hider Master Toggle", "Enable hiding entities by hash/name and custom block replacements.", category, () -> s.hiderEnabled, v -> s.hiderEnabled = v));
                items.add(ConfigItem.dynamicCustomCard("Entity Hider Rules", category,
                    ConfigCustomWidgets::getEntityHiderCardHeight,
                    ConfigCustomWidgets::renderEntityHiderCard,
                    ConfigCustomWidgets::handleEntityHiderClick));
                items.add(ConfigItem.dynamicCustomCard("Block Replacement Rules", category,
                    ConfigCustomWidgets::getBlockHiderCardHeight,
                    ConfigCustomWidgets::renderBlockHiderCard,
                    ConfigCustomWidgets::handleBlockHiderClick));

                items.add(ConfigItem.header("No Render Settings (NoFrills Hiders)", category));
                items.add(ConfigItem.toggle("No Render Explosions", "Disables explosion particle rendering.", category, () -> s.noRenderExplosions, v -> s.noRenderExplosions = v));
                items.add(ConfigItem.toggle("No Render Empty Tooltips", "Hides blank/empty tooltips.", category, () -> s.noRenderEmptyTooltips, v -> s.noRenderEmptyTooltips = v));
                items.add(ConfigItem.toggle("No Render Fire Overlay", "Hides first-person on-fire screen texture.", category, () -> s.noRenderFireOverlay, v -> s.noRenderFireOverlay = v));
                items.add(ConfigItem.toggle("No Render Break Particles", "Hides block breaking/punching particles.", category, () -> s.noRenderBreakParticles, v -> s.noRenderBreakParticles = v));
                items.add(ConfigItem.toggle("No Render Boss Bar", "Hides the vanilla top boss health bar.", category, () -> s.noRenderBossBar, v -> s.noRenderBossBar = v));
                items.add(ConfigItem.toggle("No Render Armor Bar", "Hides vanilla armor icons above health bar.", category, () -> s.noRenderArmorBar, v -> s.noRenderArmorBar = v));
                items.add(ConfigItem.toggle("No Render Food Bar", "Hides vanilla hunger/food bar.", category, () -> s.noRenderFoodBar, v -> s.noRenderFoodBar = v));
                items.add(ConfigItem.toggle("No Render Health Bar", "Hides vanilla heart icons from HUD.", category, () -> s.noRenderHealthBar, v -> s.noRenderHealthBar = v));
                items.add(ConfigItem.toggle("No Render Fog", "Disables all environmental world fog.", category, () -> s.noRenderFog, v -> s.noRenderFog = v));
                items.add(ConfigItem.toggle("No Render Effect Display", "Hides active status effect icons on screen.", category, () -> s.noRenderEffectDisplay, v -> s.noRenderEffectDisplay = v));
                items.add(ConfigItem.toggle("No Render Recipe Book", "Hides recipe book button in inventory screens.", category, () -> s.noRenderRecipeBook, v -> s.noRenderRecipeBook = v));
                items.add(ConfigItem.toggle("No Render Selected Item Name", "Hides the item name text popup above hotbar on item switch.", category, () -> s.noRenderSelectedItemName, v -> s.noRenderSelectedItemName = v));
                items.add(ConfigItem.toggle("No Render Dead Entities", "Immediately stops rendering dead entities without death delay.", category, () -> s.noRenderDeadEntities, v -> s.noRenderDeadEntities = v));
                items.add(ConfigItem.toggle("No Render Dead Poof", "Disables entity death cloud/smoke poof particles.", category, () -> s.noRenderDeadPoof, v -> s.noRenderDeadPoof = v));
                items.add(ConfigItem.toggle("No Render Lightning", "Hides lightning bolt render effects and flashes.", category, () -> s.noRenderLightning, v -> s.noRenderLightning = v));
                items.add(ConfigItem.toggle("No Render Falling Blocks", "Hides sand, gravel, and falling block entities.", category, () -> s.noRenderFallingBlocks, v -> s.noRenderFallingBlocks = v));
                items.add(ConfigItem.toggle("No Render Entity Fire", "Hides burning fire effect rendered on other entities.", category, () -> s.noRenderEntityFire, v -> s.noRenderEntityFire = v));
                items.add(ConfigItem.toggle("No Render Mage Beam", "Hides mage weapon beam particles.", category, () -> s.noRenderMageBeam, v -> s.noRenderMageBeam = v));
                items.add(ConfigItem.toggle("No Render Ice Spray", "Hides Ice Spray wand particle effects.", category, () -> s.noRenderIceSpray, v -> s.noRenderIceSpray = v));
                items.add(ConfigItem.toggle("Hide Second Hand", "Hides the off-hand / second hand and its held item from rendering.", category, () -> s.hideSecondHand, v -> s.hideSecondHand = v));
            }

            case "Experiments" -> {
                items.add(ConfigItem.header("Auto Experimentation Table Solver", category));
                items.add(ConfigItem.toggle("Auto Experiments Enabled", "Automatically solves Chronomatron, Ultrasequencer, and Superpairs.", category, () -> s.autoExperiments, v -> s.autoExperiments = v));
                items.add(ConfigItem.sliderInt("Solve Click Delay", "Delay between solver clicks in milliseconds.", category, 0, 500, 10, "ms", () -> s.experimentClickDelay, v -> s.experimentClickDelay = v));
                items.add(ConfigItem.sliderInt("Metaphysical Serum Count", "Number of serums to automatically apply.", category, 0, 3, 1, "", () -> s.experimentSerumCount, v -> s.experimentSerumCount = v));
                items.add(ConfigItem.toggle("Auto Close on Completion", "Closes the table GUI automatically once finished.", category, () -> s.experimentAutoClose, v -> s.experimentAutoClose = v));
                items.add(ConfigItem.toggle("Maximize Pair XP", "Selects optimal card matching order for maximum Enchanting XP.", category, () -> s.experimentGetMaxXp, v -> s.experimentGetMaxXp = v));
            }

            case "Fishing" -> {
                items.add(ConfigItem.header("Fishing Automation & Overlays", category));
                items.add(ConfigItem.toggle("Auto Fishing Enabled", "Automatically reels in and recasts rod on bite.", category, () -> s.autoFishingEnabled, v -> s.autoFishingEnabled = v));
                items.add(ConfigItem.hudToggle("Bobber Time HUD", "Displays active hook immersion duration overlay.", category, () -> s.showBobberTime, v -> s.showBobberTime = v, HudTarget.BOBBER_TIME));
                items.add(ConfigItem.sliderInt("Min Reel Delay", "Minimum randomized delay before reeling in bite.", category, 20, 300, 5, "ms", () -> s.autoFishingMinDelay, v -> s.autoFishingMinDelay = v));
                items.add(ConfigItem.sliderInt("Max Reel Delay", "Maximum randomized delay before reeling in bite.", category, 20, 300, 5, "ms", () -> s.autoFishingMaxDelay, v -> s.autoFishingMaxDelay = v));
                items.add(ConfigItem.toggle("Slug Mode", "Waits specified seconds before reeling in for Slugfish.", category, () -> s.autoFishingSlugMode, v -> s.autoFishingSlugMode = v));
                items.add(ConfigItem.toggle("Trophy Fish Highlight", "3D bounding box ESP on caught rare Trophy Fish.", category, () -> s.trophyHighlight, v -> s.trophyHighlight = v));
                items.add(ConfigItem.toggle("Auto Weapon Swap on Catch", "Quickly swaps to designated weapon and strikes upon reel.", category, () -> s.autoFishingSwapEnabled, v -> s.autoFishingSwapEnabled = v));
            }

            case "Garden" -> {
                items.add(ConfigItem.header("Garden Movement & Farming Helpers", category));
                items.add(ConfigItem.toggle("Garden Movement Master", "Enables smart lane navigation and auto turnaround.", category, () -> s.gardenMovement, v -> {
                    s.gardenMovement = v;
                    if (!v) GardenMovement.reset();
                }));
                items.add(ConfigItem.toggle("Lock Mouse on Movement", "Prevents accidental camera movement while walking lanes.", category, () -> s.lockMouseOnGarden, v -> s.lockMouseOnGarden = v));
                items.add(ConfigItem.toggle("Crop Breaking Warning", "Warns with sound, screen title, and chat alert if you click to break crops but nothing breaks.", category, () -> s.cropBreakWarning, v -> s.cropBreakWarning = v));
                items.add(ConfigItem.toggle("Sun's Grasp Warning", "Alerts when you receive the Sun's Grasp cannot break crops notification.", category, () -> s.sunsGraspWarning, v -> s.sunsGraspWarning = v));
                if (!s.hideCheats) {
                    items.add(ConfigItem.toggle("Sun's Grasp Auto Hotbar Swap", "Automatically swaps hand to an empty hotbar slot when Sun's Grasp warning triggers.", category, () -> s.sunsGraspAutoSwap, v -> s.sunsGraspAutoSwap = v));
                }
                items.add(ConfigItem.toggle("Sugar Cane Mode", "Adjusts angles and lane turns for Sugar Cane farming.", category, () -> s.gardenSugarCane, v -> s.gardenSugarCane = v));
                items.add(ConfigItem.toggle("Direction Helper Warning", "Audible and visual alerts on lane turnaround.", category, () -> s.gardenDirectionHelper, v -> s.gardenDirectionHelper = v));

                items.add(ConfigItem.header("Composter Utilities", category));
                items.add(ConfigItem.toggle("Composter Helper", "Calculates optimal organic matter and upgrades.", category, () -> s.composterHelper, v -> s.composterHelper = v));
                items.add(ConfigItem.hudToggle("Composter HUD", "Live status overlay of composter levels and capacity.", category, () -> s.composterHud, v -> s.composterHud = v, HudTarget.COMPOSTER));
                items.add(ConfigItem.hudToggle("Composter Timer HUD", "Remaining time countdown overlay until composter finishes.", category, () -> s.composterTimerHud, v -> s.composterTimerHud = v, HudTarget.COMPOSTER_TIMER));

                items.add(ConfigItem.header("Garden Warnings & Safeguards", category));
                items.add(ConfigItem.toggle("Block Inventory Slots While Farming", "Protects all inventory slots from being clicked or swapped while Garden Movement is active to prevent Limbo kicks.", category, () -> s.gardenBlockSlotsWhileFarming, v -> s.gardenBlockSlotsWhileFarming = v));
                items.add(ConfigItem.toggle("Auto Combine Exp Capsule", "Automatically combines Tool Exp Capsules in The Hex menu until level 50 (uses Anvil Combine Key).", category, () -> s.autoExpCapsuleEnabled, v -> s.autoExpCapsuleEnabled = v));
                items.add(ConfigItem.toggle("Crop Breaking Fail Warning", "Audible and visual warning when crop destruction fails while farming.", category, () -> s.cropBreakWarning, v -> s.cropBreakWarning = v));
                items.add(ConfigItem.toggle("Sun's Grasp Warning", "Warns when breaking crops with an item while wearing Sun's Grasp.", category, () -> s.sunsGraspWarning, v -> s.sunsGraspWarning = v));
                if (!s.hideCheats) {
                    items.add(ConfigItem.toggle("Sun's Grasp Auto Empty Slot", "Automatically switch hotbar to empty hand slot on Sun's Grasp warning.", category, () -> s.sunsGraspAutoEmptySlot, v -> s.sunsGraspAutoEmptySlot = v));
                }

                items.add(ConfigItem.header("Garden Movement Hotkeys", category));
                items.add(ConfigItem.keybind("Garden Forward", "Move forward while Garden Movement is active.", category, () -> s.gardenForwardKey != null ? s.gardenForwardKey : "up", v -> s.gardenForwardKey = v));
                items.add(ConfigItem.keybind("Garden Backward", "Move backward while Garden Movement is active.", category, () -> s.gardenBackwardKey != null ? s.gardenBackwardKey : "down", v -> s.gardenBackwardKey = v));
                items.add(ConfigItem.keybind("Garden Left", "Move left while Garden Movement is active.", category, () -> s.gardenLeftKey != null ? s.gardenLeftKey : "left", v -> s.gardenLeftKey = v));
                items.add(ConfigItem.keybind("Garden Right", "Move right while Garden Movement is active.", category, () -> s.gardenRightKey != null ? s.gardenRightKey : "right", v -> s.gardenRightKey = v));
                items.add(ConfigItem.keybind("Garden Break", "Toggle continuous breaking in Garden.", category, () -> s.gardenBreakKey != null ? s.gardenBreakKey : "b", v -> s.gardenBreakKey = v));
                items.add(ConfigItem.keybind("Garden Use", "Toggle continuous using/placing in Garden.", category, () -> s.gardenUseKey != null ? s.gardenUseKey : "u", v -> s.gardenUseKey = v));

                items.add(ConfigItem.header("Pest ESP & Highlights", category));
                items.add(ConfigItem.toggle("Pest Highlight", "Draws bounding box around spawned garden pests.", category, () -> s.pestEsp, v -> s.pestEsp = v));
                items.add(ConfigItem.toggle("Pest Tracers", "Draws tracer line from crosshair to active pests.", category, () -> s.pestEspTracer, v -> s.pestEspTracer = v));
                items.add(ConfigItem.toggle("Pest Waypoints", "Spawns 3D waypoints at pest locations.", category, () -> s.pestSpawnWaypoint, v -> s.pestSpawnWaypoint = v));
                items.add(ConfigItem.color("Pest Color", "Highlight color for pest entities.", category, () -> s.pestEspColor, v -> s.pestEspColor = v));

                items.add(ConfigItem.header("Greenhouse Profit Tracker", category));
                items.add(ConfigItem.toggle("Greenhouse Profit Tracker", "Tracks crops, rare crops and sack profits while farming Greenhouse plots.", category, () -> s.greenhouseProfitTracker, v -> s.greenhouseProfitTracker = v));
                items.add(ConfigItem.button("View Greenhouse Profits", "Open GUI", "Opens the Greenhouse Profit summary screen (/gp).", category, () -> {
                    Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.features.garden.GreenhouseProfitScreen(Minecraft.getInstance().gui.screen()));
                }));
            }

            case "General" -> {
                items.add(ConfigItem.header("Mod Updates & Release Channel", category));
                items.add(ConfigItem.cycle("Update Channel", "Choose whether /b update checks for only Full release versions or Betas & Full releases.", category,
                        List.of("Betas & Full", "Full Only"),
                        () -> s.updateChannel != null ? s.updateChannel : "Betas & Full",
                        v -> s.updateChannel = v));
                items.add(ConfigItem.button("Check For Updates", "Check Now", "Manually check and download mod updates based on the selected channel.", category, () -> {
                    ModUpdater.checkAndUpdate(false);
                }));
                // The flavor switcher lives in the cheat build only: the legit artifact
                // must not be able to pull the cheat jar onto a user's machine.
                if (me.bombo.bomboaddons.Constants.CHEAT_FLAVOR) {
                    items.add(ConfigItem.button("Switch Flavor", "Install Other Flavor",
                            "Downloads the legit bomboaddons build and removes this one on the next restart.", category, () -> {
                        ModUpdater.installOtherFlavor();
                    }));
                }
                items.add(ConfigItem.header("Running Flavor: " + me.bombo.bomboaddons.Constants.MOD_NAME
                        + " (" + me.bombo.bomboaddons.Constants.FLAVOR + " \u2022 "
                        + me.bombo.bomboaddons.Constants.artifactFilePrefix() + "*.jar)", category));
                items.add(ConfigItem.header("Mod ID hidden on join: always on (vanilla brand sent to servers).", category));
                items.add(ConfigItem.toggle("No Obfuscate (strip \u00a7k)",
                        "Removes the scrambling style from chat messages and item lore, making the text behind it readable.",
                        category, () -> s.noObfuscate, v -> s.noObfuscate = v));
                if (me.bombo.bomboaddons.Constants.CHEAT_FLAVOR) {
                    items.add(ConfigItem.toggle("Stealth Mode",
                            "Stop advertising your presence: no bridge online status, no egg publishing, vanilla brand on join.",
                            category, () -> s.stealthMode, v -> s.stealthMode = v));
                }

                items.add(ConfigItem.header("Chat History", category));
                items.add(ConfigItem.keybind("Chat History Key", "Optional keybind that opens /b chathistory.", category,
                        () -> s.chatHistoryKey != null ? s.chatHistoryKey : "", v -> s.chatHistoryKey = v));
                items.add(ConfigItem.toggle("Unlimited Chat History",
                        "Keep every message for the whole session instead of trimming to the limit below (hard cap 50,000 entries).",
                        category, () -> s.unlimitedChatHistory, v -> s.unlimitedChatHistory = v));
                if (!s.unlimitedChatHistory) {
                    items.add(ConfigItem.sliderInt("Chat History Limit", "How many chat and feature events to keep in memory.", category, 100, 5000, 100, "msgs",
                            () -> s.chatHistoryMaxMessages > 0 ? s.chatHistoryMaxMessages : 500, v -> s.chatHistoryMaxMessages = v));
                }
                items.add(ConfigItem.toggle("Persist History Across Servers",
                        "Never wipe the history when switching worlds, changing servers or disconnecting - a session boundary row is logged instead.",
                        category, () -> s.persistHistoryAcrossServers, v -> s.persistHistoryAcrossServers = v));
                items.add(ConfigItem.button("Open Chat History", "Open", "Browse incoming, outgoing, blocked and feature events.", category, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreenAndShow(new me.bombo.bomboaddons.features.chat.ChatHistoryScreen(mc.gui.screen(),
                            me.bombo.bomboaddons.features.chat.ChatHistoryScreen.FilterTab.ALL));
                }));

                items.add(ConfigItem.header("Quality of Life & Vision", category));
                items.add(ConfigItem.toggle("Clear Water & Lava Vision", "Enhance visibility when underwater or in lava.", category, () -> s.clearWaterAndLava, v -> s.clearWaterAndLava = v));
                items.add(ConfigItem.sliderInt("Water Opacity", "Custom water opacity (10% = almost totally clear, 100% = default).", category, 0, 100, 5, "%", () -> s.waterOpacity >= 0 ? s.waterOpacity : 100, v -> s.waterOpacity = v));
                items.add(ConfigItem.sliderInt("Lava Opacity", "Custom lava opacity (10% = almost totally clear, 100% = default).", category, 0, 100, 5, "%", () -> s.lavaOpacity >= 0 ? s.lavaOpacity : 100, v -> s.lavaOpacity = v));
                items.add(ConfigItem.hudToggle("Sign Calculator", "Live math calculation overlay when typing in signs.", category, () -> s.signCalculator, v -> s.signCalculator = v, HudTarget.SIGN_CALCULATOR));
                items.add(ConfigItem.toggle("Fix Skyblock F3 Day #0", "Replaces vanilla Day #0 on F3 debug overlay with true calculated Skyblock Day.", category, () -> s.fixSkyblockF3Day, v -> s.fixSkyblockF3Day = v));
                items.add(ConfigItem.cycle("Custom SB Level Color", "Recolor the SkyBlock level bracket in chat, tab, and menus without modifying hover info.", category,
                        List.of("Default", "&f", "&a", "&2", "&b", "&3", "&9", "&1", "&d", "&5", "&e", "&6", "&c", "&4", "Chroma"),
                        () -> (s.customSbLevelColor != null && !s.customSbLevelColor.isEmpty()) ? s.customSbLevelColor : "Default",
                        v -> s.customSbLevelColor = "Default".equalsIgnoreCase(v) ? "" : v));
                items.add(ConfigItem.toggle("SBE Commands", "Enable SBE shorthand command aliases (/nw, /cata, etc.).", category, () -> s.sbeCommands, v -> s.sbeCommands = v));
                items.add(ConfigItem.text("BomboAPI Key", "Personal API key for Bombo services. Generate via /b apikey.", category, () -> s.apiKey != null ? s.apiKey : "", v -> s.apiKey = v));
                items.add(ConfigItem.toggle("Copy Chat", "Right-click or click chat messages to copy them to clipboard.", category, () -> s.copyChat, v -> s.copyChat = v));
                items.add(ConfigItem.hudToggle("In-Chat Search Bar", "Adds an interactive search bar directly inside the chat window.", category, () -> s.chatSearchBar, v -> s.chatSearchBar = v, HudTarget.CHAT_SEARCH));
                items.add(ConfigItem.toggle("Controls Search Bar", "Adds an interactive search bar in Minecraft Controls / Keybinds menu.", category, () -> s.controlsSearchBar, v -> s.controlsSearchBar = v));

                items.add(ConfigItem.header("Teleport & Movement Utilities", category));
                items.add(ConfigItem.toggle("AOTV Utilities", "Enables left-click etherwarp and teleport utilities.", category, () -> s.leftClickEtherwarp, v -> s.leftClickEtherwarp = v));
                items.add(ConfigItem.toggle("Fast AOTV", "Triggers AOTV / AOTE without normal right-click delay while holding use.", category, () -> s.fastAotv, v -> s.fastAotv = v));
                if (s.fastAotv) {
                    items.add(ConfigItem.sliderInt("  Fast AOTV Speed", "Speed rate from 1 (slowest, 200ms) to 4 (fastest, 50ms).", category, 1, 4, 1, "x", () -> s.fastAotvSpeed, v -> s.fastAotvSpeed = v));
                }
                items.add(ConfigItem.toggle("Fast Hyperion (Fast Hyp)", "Triggers Wither Impact / Necron's Blade abilities rapidly while holding use.", category, () -> s.fastHyp, v -> s.fastHyp = v));
                if (s.fastHyp) {
                    items.add(ConfigItem.sliderInt("  Fast Hyp Speed", "Speed rate from 1 (slowest, 200ms) to 4 (fastest, 50ms).", category, 1, 4, 1, "x", () -> s.fastHypSpeed, v -> s.fastHypSpeed = v));
                }
                if (s.leftClickEtherwarp) {
                    items.add(ConfigItem.toggle("  Only On Block In Range", "Prevents teleporting if target block is out of range.", category, () -> s.etherwarpBlockOnly, v -> s.etherwarpBlockOnly = v));
                }

                items.add(ConfigItem.header("Hoppity & Events", category));
                items.add(ConfigItem.toggle("Hoppity Egg Finder", "Locates and highlights chocolate eggs during Hoppity's Hunt.", category, () -> s.eggFinder, v -> s.eggFinder = v));
                items.add(ConfigItem.toggle("Egg Finder Chat Alerts", "Sends chat alert when an egg is found.", category, () -> s.eggFinderChat, v -> s.eggFinderChat = v));
                items.add(ConfigItem.toggle("Egg Finder Beacon", "Spawns a vertical beacon beam on egg positions.", category, () -> s.eggFinderBeacon, v -> s.eggFinderBeacon = v));
                items.add(ConfigItem.toggle("Egg Finder Through Walls", "Renders egg ESP highlights through walls.", category, () -> s.eggFinderThroughWalls, v -> s.eggFinderThroughWalls = v));
                items.add(ConfigItem.hudToggle("Hoppity Egg HUD", "Displays active egg hunt stats overlay.", category, () -> s.hoppityHud, v -> s.hoppityHud = v, HudTarget.HOPPITY));
                items.add(ConfigItem.toggle("Auto Accept Hoppity Calls", "Automatically answers incoming phone calls from Hoppity.", category, () -> s.autoHoppityCalls, v -> s.autoHoppityCalls = v));
                if (s.autoHoppityCalls) {
                    items.add(ConfigItem.toggle("  Auto Confirm Hoppity", "Automatically clicks confirm when accepting Hoppity calls.", category, () -> s.autoHoppityConfirm, v -> s.autoHoppityConfirm = v));
                }
                items.add(ConfigItem.toggle("Auto Buy Hoppity Rabbits", "Automatically buys rabbits when viewing Hoppity's rabbit shop / Chocolate Factory.", category, () -> s.autoHoppityBuyRabbit, v -> s.autoHoppityBuyRabbit = v));
                if (s.autoHoppityBuyRabbit) {
                    items.add(ConfigItem.sliderInt("  Hoppity Delay", "Delay in milliseconds between shop purchases.", category, 50, 1000, 50, "ms", () -> (int) s.autoHoppityDelayMs, v -> s.autoHoppityDelayMs = v));
                }

                items.add(ConfigItem.header("Server & Connection", category));
                items.add(ConfigItem.toggle("Smart Disconnect", "Safe disconnect with automated reconnection safety.", category, () -> s.smartDisconnect, v -> s.smartDisconnect = v));
                items.add(ConfigItem.toggle("Auto Reconnect", "Automatically reconnects if disconnected from server.", category, () -> s.autoReconnect, v -> s.autoReconnect = v));
                items.add(ConfigItem.toggle("Auto Rejoin Skyblock", "Automatically executes /skyblock after being kicked to lobby by connection glitch.", category, () -> s.autoRejoinSkyblock, v -> s.autoRejoinSkyblock = v));
                items.add(ConfigItem.hudToggle("Auto Rejoin Timer HUD", "Shows on-screen countdown timer for auto-rejoin.", category, () -> s.autoRejoinHud, v -> s.autoRejoinHud = v, HudTarget.AUTO_REJOIN));
                items.add(ConfigItem.toggle("Hypixel ISP Fix", "Bypasses ISP IP blocks on Hypixel/Cloudflare by resolving connections to unblocked Cloudflare Spectrum endpoints (e.g. mc.hypixel.net / 172.65.200.155).", category, () -> s.hypixelIspFix, v -> s.hypixelIspFix = v));
                if (s.hypixelIspFix) {
                    items.add(ConfigItem.text("  Bypass Endpoint", "Alternate Cloudflare Spectrum IP or domain (Default: mc.hypixel.net / 172.65.200.155).", category, () -> s.hypixelBypassIp != null ? s.hypixelBypassIp : "mc.hypixel.net", v -> s.hypixelBypassIp = v));
                }
                items.add(ConfigItem.toggle("Disable Server Resource Pack", "Silently blocks server resource pack prompts and prevents forced pack loading.", category, () -> s.noResourcePack, v -> s.noResourcePack = v));
                items.add(ConfigItem.toggle("Bypass Resource Pack", "Automatically download and accept Hypixel SkyBlock server resource pack without vanilla prompts.", category, () -> s.bypassResourcePack, v -> s.bypassResourcePack = v));
                if (s.bypassResourcePack) {
                    items.add(ConfigItem.toggle("  Auto-Update Skyblock Pack", "Automatically update the SkyBlock texture pack when a newer version is pushed by Hypixel.", category, () -> s.autoUpdateSkyblockPack, v -> s.autoUpdateSkyblockPack = v));
                }
                items.add(ConfigItem.toggle("Ignore Caps Lock", "Allows normal keybind execution even with Caps Lock enabled.", category, () -> s.ignoreCapsLock, v -> s.ignoreCapsLock = v));

                items.add(ConfigItem.header("Quality of Life & Automation", category));
                items.add(ConfigItem.toggle("Auto Rabbit Hitman", "Automatically visits Chocolate Factory and claims Rabbit Hitman eggs.", category, () -> s.autoHitman, v -> { s.autoHitman = v; if (v) me.bombo.bomboaddons.features.hitman.AutoHitman.scheduleNextRun(); }));
                if (s.autoHitman) {
                    items.add(ConfigItem.sliderInt("  Hitman Interval (min)", "Base interval in minutes (randomized around this target).", category, 1, 60, 1, "m", () -> s.autoHitmanIntervalMinutes, v -> { s.autoHitmanIntervalMinutes = v; me.bombo.bomboaddons.features.hitman.AutoHitman.scheduleNextRun(); }));
                }
                items.add(ConfigItem.toggle("Auto Accept NPC Lore", "Skips NPC dialogs automatically.", category, () -> s.autoAcceptNpcLore, v -> s.autoAcceptNpcLore = v));
                items.add(ConfigItem.text("  NPC Lore Exceptions", "Comma-separated NPC names to never auto-accept options for (e.g. Safari Receptionist).", category, () -> s.autoAcceptNpcLoreExceptions != null ? s.autoAcceptNpcLoreExceptions : "", v -> s.autoAcceptNpcLoreExceptions = v));
                items.add(ConfigItem.toggle("Auto Accept Carnival", "Automatically accepts Carnival mini-games.", category, () -> s.autoAcceptCarnival, v -> s.autoAcceptCarnival = v));
                items.add(ConfigItem.toggle("Auto Accept Trevor", "Accepts Trevor trapper animal quests automatically.", category, () -> s.autoTrevorQuest, v -> s.autoTrevorQuest = v));
                items.add(ConfigItem.toggle("Sphinx Macro", "Automates answering Sphinx quizzes.", category, () -> s.sphinxMacro, v -> s.sphinxMacro = v));
                items.add(ConfigItem.toggle("Daily Reward Helper", "Automatically grabs daily reward token from Hypixel reward links.", category, () -> s.dailyRewardHelper, v -> s.dailyRewardHelper = v));
                items.add(ConfigItem.toggle("Fuck Diorite", "Mines / ignores unwanted stone and diorite blocks.", category, () -> s.fuckDiorite, v -> s.fuckDiorite = v));
                items.add(ConfigItem.toggle("Hitbox Expansion", "Expands entity interaction hitboxes.", category, () -> s.hitbox, v -> s.hitbox = v));

                items.add(ConfigItem.header("Third Person Camera Settings", category));
                items.add(ConfigItem.toggle("Custom Camera Settings", "Enable custom third-person camera distance and perspective tweaks.", category, () -> s.cameraSettingsEnabled, v -> s.cameraSettingsEnabled = v));
                if (s.cameraSettingsEnabled) {
                    if (!s.hideCheats) {
                        items.add(ConfigItem.sliderFloat("Third Person Distance", "Max camera distance from player model in blocks.", category, 0.5f, 30.0f, 0.5f, 4.0f, "m", () -> s.cameraDistance, v -> s.cameraDistance = v));
                        items.add(ConfigItem.toggle("Pass Through Walls", "Allows third-person camera to pass straight through walls without collision clipping.", category, () -> s.cameraPassThroughWalls, v -> s.cameraPassThroughWalls = v));
                    } else {
                        items.add(ConfigItem.sliderFloat("Third Person Distance", "Max camera distance from player model in blocks.", category, 0.5f, 4.0f, 0.1f, 4.0f, "m", () -> s.cameraDistance, v -> s.cameraDistance = v));
                    }
                    items.add(ConfigItem.toggle("Disable Front Camera", "Skip the inverted front-facing 2nd person camera view when cycling F5.", category, () -> s.disableFrontCamera, v -> s.disableFrontCamera = v));
                }
            }

            case "Highlights" -> {
                items.add(ConfigItem.header("Entity & Bestiary Highlights", category));
                items.add(ConfigItem.toggle("Highlights Master Toggle", "Enable 3D bounding box outlines on configured entities.", category, () -> s.highlightsEnabled, v -> s.highlightsEnabled = v));
                items.add(ConfigItem.sliderFloat("Tracer Line Width", "Line thickness in pixels for highlight tracers.", category, 0.5f, 10.0f, 0.5f, "px", () -> s.tracerWidth > 0 ? s.tracerWidth : 2.0f, v -> s.tracerWidth = v));
                items.add(ConfigItem.keybind("Bestiary Toggle Key", "Keybind to cycle Bestiary mob highlight overlay.", category, () -> s.bestiaryHighlightKey != null ? s.bestiaryHighlightKey : "h", v -> s.bestiaryHighlightKey = v));

                // Dedicated Advanced / Basic Highlight Editor Card with Inputs and Entries List
                items.add(ConfigItem.dynamicCustomCard("Highlight Editor & Entries", category,
                        ConfigCustomWidgets::getHighlightCardHeight,
                        ConfigCustomWidgets::renderHighlightEditor,
                        ConfigCustomWidgets::handleHighlightEditorClick));
            }

            case "Hotkeys" -> {
                items.add(ConfigItem.header("Profile & Global Hotkey Settings", category));
                items.add(ConfigItem.cycle("Active Profile", "Select active configuration profile for custom keybinds.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));
                items.add(ConfigItem.toggle("Ignore Caps Lock", "Trigger all hotkeys properly even when Caps Lock is ON.", category, () -> s.ignoreCapsLock, v -> s.ignoreCapsLock = v));

                items.add(ConfigItem.header("Item & Inventory Hotkeys", category));
                items.add(ConfigItem.keybind("Trade Hotkey", "Quick trade with the player you're looking at or hovering over, or lookup on AH/BZ.", category, () -> s.tradeKey, v -> s.tradeKey = v));
                items.add(ConfigItem.keybind("Trade Max Pet Hotkey", "Quick AH search for max level pets (100] or 200] for GDrag/Rose/Jade).", category, () -> s.tradeMaxKey, v -> s.tradeMaxKey = v));
                items.add(ConfigItem.keybind("Recipe Viewer", "Open the SkyBlock recipe viewer screen for the hovered item.", category, () -> s.recipeKey, v -> s.recipeKey = v));
                items.add(ConfigItem.keybind("Recipe Command (/recipe)", "Execute /recipe <item> command for the hovered item.", category, () -> s.recipeCmdKey, v -> s.recipeCmdKey = v));
                items.add(ConfigItem.keybind("View Recipe (/viewrecipe)", "Execute /viewrecipe <id> command for the hovered item.", category, () -> s.viewRecipeKey, v -> s.viewRecipeKey = v));
                items.add(ConfigItem.cycle("Recipe Hotkey Action", "Default behavior when pressing the main Recipe Viewer hotkey.", category,
                        java.util.List.of("GUI", "/recipe", "/viewrecipe"),
                        () -> s.recipeHotkeyAction != null ? s.recipeHotkeyAction : "GUI",
                        v -> s.recipeHotkeyAction = v));
                items.add(ConfigItem.keybind("Usage Viewer", "Open the craft usage viewer screen for the hovered item.", category, () -> s.usageKey, v -> s.usageKey = v));
                items.add(ConfigItem.keybind("Texture Toggle", "Toggle custom/vanilla resourcepack texture for hovered item.", category, () -> s.textureToggleKey, v -> s.textureToggleKey = v));
                items.add(ConfigItem.keybind("Show Item Info", "Display detailed item statistics and NBT summary overlay.", category, () -> s.showItemKey, v -> s.showItemKey = v));
                items.add(ConfigItem.keybind("Count Items", "Count all matching items across inventory, sacks, and containers.", category, () -> s.countItemKey, v -> s.countItemKey = v));
                items.add(ConfigItem.keybind("Copy Item NBT", "Copy full raw NBT data of the hovered item to clipboard.", category, () -> s.copyNbtKey, v -> s.copyNbtKey = v));
                items.add(ConfigItem.keybind("Get From Sacks (Max)", "Retrieve maximum possible quantity of hovered item from sacks.", category, () -> s.gfsMaxKey, v -> s.gfsMaxKey = v));
                items.add(ConfigItem.keybind("Get From Sacks (Stack)", "Retrieve 1 full stack (64) of hovered item from sacks.", category, () -> s.gfsStackKey, v -> s.gfsStackKey = v));
                items.add(ConfigItem.keybind("Save Inventory Layout", "Save a snapshot of current inventory hotbar and slot layout.", category, () -> s.saveInventoryKey, v -> s.saveInventoryKey = v));
                items.add(ConfigItem.keybind("Blocked Slots Bypass", "Hold key to temporarily bypass locked/protected inventory slots.", category, () -> s.blockedSlotsBypassKey, v -> s.blockedSlotsBypassKey = v));

                items.add(ConfigItem.header("Navigation & Utility Hotkeys", category));
                items.add(ConfigItem.keybind("Chat Peek", "Hold key to preview recent chat logs without opening chat prompt.", category, () -> s.chatPeekKey, v -> s.chatPeekKey = v));
                items.add(ConfigItem.keybind("Next Menu Page", "Instantly navigate to the next page in paginated container GUIs.", category, () -> s.nextPageKey, v -> s.nextPageKey = v));
                items.add(ConfigItem.keybind("Previous Menu Page", "Instantly navigate to the previous page in paginated container GUIs.", category, () -> s.prevPageKey, v -> s.prevPageKey = v));
                items.add(ConfigItem.keybind("Go Back", "Click the 'Go Back' button in Hypixel menu interfaces.", category, () -> s.goBackKey, v -> s.goBackKey = v));
                items.add(ConfigItem.keybind("Smart Back", "Intelligently return to the previous SkyBlock menu hierarchy.", category, () -> s.smartGoBackKey, v -> s.smartGoBackKey = v));
                 items.add(ConfigItem.keybind("Save Pet Preset", "Quickly save current active pet configuration preset.", category, () -> s.savePetKey, v -> s.savePetKey = v));
                  items.add(ConfigItem.keybind("Price History Graph", "Open historical price chart for hovered item or held item.", category, () -> s.priceHistoryKey != null ? s.priceHistoryKey : "H", v -> s.priceHistoryKey = v));
                  items.add(ConfigItem.keybind("Vanilla Sneak Toggle Hotkey", "Cycle vanilla Sneak/Crouch control between Hold and Toggle.", category, () -> s.vanillaToggleCrouchKey, v -> s.vanillaToggleCrouchKey = v));
                  items.add(ConfigItem.keybind("Vanilla Attack/Break Toggle Hotkey", "Cycle vanilla Attack/Destroy control between Hold and Toggle.", category, () -> s.vanillaToggleAttackKey, v -> s.vanillaToggleAttackKey = v));
                  items.add(ConfigItem.keybind("Vanilla Use/Place Toggle Hotkey", "Cycle vanilla Use/Place control between Hold and Toggle.", category, () -> s.vanillaToggleUseKey, v -> s.vanillaToggleUseKey = v));
                  items.add(ConfigItem.keybind("Run Copied", "Instantly execute any text on clipboard as a chat slash command.", category, () -> s.clipboardRunKey, v -> s.clipboardRunKey = v));
                  items.add(ConfigItem.keybind("Run Last Copied Command", "Execute the last copied string starting with a slash command (/wd).", category, () -> s.clipboardRunLastCommandKey, v -> s.clipboardRunLastCommandKey = v));
                 if (!s.hideCheats) {
                    items.add(ConfigItem.keybindWithMode("Freelook", "Rotate 360 camera freely without changing player facing direction.", category, () -> s.freelookKey, v -> s.freelookKey = v, () -> s.freelookToggle, v -> s.freelookToggle = v));
                    items.add(ConfigItem.keybindWithMode("Freecam", "Detached free-flying camera view with flight controls.", category, () -> s.freecamKey, v -> s.freecamKey = v, () -> s.freecamToggle, v -> s.freecamToggle = v));
                }
            }

            case "HUDs" -> {
                items.add(ConfigItem.header("Heads-Up Display Overlays", category));
                items.add(ConfigItem.button("❖ Move All HUD Elements", "Open HUD Canvas", "Enter interactive drag-and-drop HUD repositioning screen.", category, () -> {
                    Minecraft.getInstance().setScreenAndShow(new HudMoveScreen());
                }));
                items.add(ConfigItem.hudToggle("Dice Tracker HUD", "Tracks High Class Archfiend Dice rolls and profit.", category, () -> s.diceTracker, v -> s.diceTracker = v, HudTarget.DICE));
                items.add(ConfigItem.hudToggle("Feast Bakery HUD", "Displays Feast bakery cake timers and buffs.", category, () -> s.feastBakeryHud, v -> s.feastBakeryHud = v, HudTarget.BAKERY));
                items.add(ConfigItem.hudToggle("RNG Profit HUD", "Real-time RNG drop profit statistics.", category, () -> s.rngProfitHud, v -> s.rngProfitHud = v, HudTarget.RNG));
                items.add(ConfigItem.sliderInt("RNG HUD Opacity", "Background opacity percentage of RNG overlay.", category, 0, 100, 10, "%", () -> s.rngProfitHudOpacity, v -> s.rngProfitHudOpacity = v));
                items.add(ConfigItem.hudToggle("Custom Timers HUD", "Displays user-configured custom cooldown timers.", category, () -> s.customTimerHudEnabled, v -> s.customTimerHudEnabled = v, HudTarget.TIMERS));
                items.add(ConfigItem.hudToggle("Tab Widget HUD", "Compact tab-list player and server stats widget.", category, () -> s.tabWidgetHudEnabled, v -> s.tabWidgetHudEnabled = v, HudTarget.TAB_WIDGET));
                items.add(ConfigItem.hudToggle("Alpha Tracker HUD", "Displays Alpha server player count and status.", category, () -> s.alphaTrackerHud, v -> s.alphaTrackerHud = v, HudTarget.ALPHA_TRACKER));
                items.add(ConfigItem.hudToggle("Item List HUD", "In-game searchable item and inventory list overlay.", category, () -> s.itemListEnabled, v -> s.itemListEnabled = v, HudTarget.ITEM_LIST));
                items.add(ConfigItem.hudToggle("Speedometer HUD", "Real-time player velocity and movement speed HUD.", category, () -> s.speedometer, v -> s.speedometer = v, HudTarget.SPEEDOMETER));
                if (s.speedometer) {
                    items.add(ConfigItem.cycle("Speedometer Unit", "Unit of measurement for velocity.", category, List.of("bps", "m/s", "%"), () -> s.speedometerUnit != null ? s.speedometerUnit : "bps", v -> s.speedometerUnit = v));
                    items.add(ConfigItem.sliderFloat("Speedometer Scale", "Size multiplier for speedometer overlay.", category, 0.5f, 2.5f, 0.1f, "x", () -> s.speedometerScale > 0 ? s.speedometerScale : 1.0f, v -> s.speedometerScale = v));
                }
                items.add(ConfigItem.hudToggle("Auto Rejoin HUD", "On-screen countdown display for auto rejoin.", category, () -> s.autoRejoinHud, v -> s.autoRejoinHud = v, HudTarget.AUTO_REJOIN));
                items.add(ConfigItem.hudToggle("Item Value Breakdown HUD", "Displays item estimated value breakdown when hovering over items.", category, () -> s.itemValueBreakdownHud, v -> s.itemValueBreakdownHud = v, HudTarget.ITEM_VALUE_BREAKDOWN));
                items.add(ConfigItem.cycle("Value Price Source",
                        "Instant Buy (Lowest BIN) values items at what you would pay. Instant Sell (Bazaar Sell Offer / Average BIN) values them at what you would actually receive.",
                        category, List.of(VALUE_SOURCE_BUY, VALUE_SOURCE_SELL),
                        () -> sellsAtValueSource(s) ? VALUE_SOURCE_SELL : VALUE_SOURCE_BUY,
                        v -> applyValueSource(s, v)));
                items.add(ConfigItem.hudToggle("Armor HUD", "On-screen player armor display with tooltips and click shortcuts.", category, () -> s.armorHud, v -> s.armorHud = v, HudTarget.ARMOR_HUD));
                if (s.armorHud) {
                    items.add(ConfigItem.cycle("  Armor Click Action", "Action to perform when clicking Armor HUD while chat is open.", category, List.of("Wardrobe", "Loadout", "Inventory", "None"), () -> s.armorHudClickAction != null ? s.armorHudClickAction : "Wardrobe", v -> s.armorHudClickAction = v));
                    items.add(ConfigItem.toggle("  Armor HUD Vertical", "Display armor slots vertically (Helmet to Boots stacked).", category, () -> s.armorHudVertical, v -> s.armorHudVertical = v));
                    items.add(ConfigItem.toggle("  Armor Show in Inventory", "Keep Armor HUD visible while inventory is open.", category, () -> s.armorHudShowInInventory, v -> s.armorHudShowInInventory = v));
                    items.add(ConfigItem.toggle("  Armor Show Tooltip on Hover", "Show item info and lore tooltip when hovering over armor slots.", category, () -> s.armorHudShowTooltip, v -> s.armorHudShowTooltip = v));
                }
                items.add(ConfigItem.hudToggle("Equipment HUD", "On-screen equipment tracker overlay (Necklace, Cloak, Belt, Gloves).", category, () -> s.equipmentHud, v -> s.equipmentHud = v, HudTarget.EQUIPMENT_HUD));
                if (s.equipmentHud) {
                    items.add(ConfigItem.cycle("  Equipment Click Action", "Action to perform when clicking Equipment HUD while chat is open.", category, List.of("Equipment", "Stats", "None"), () -> s.equipmentHudClickAction != null ? s.equipmentHudClickAction : "Equipment", v -> s.equipmentHudClickAction = v));
                    items.add(ConfigItem.toggle("  Equipment HUD Vertical", "Display equipment slots vertically (Necklace to Gloves stacked).", category, () -> s.equipmentHudVertical, v -> s.equipmentHudVertical = v));
                    items.add(ConfigItem.toggle("  Equipment Show in Inventory", "Keep Equipment HUD visible while inventory is open.", category, () -> s.equipmentHudShowInInventory, v -> s.equipmentHudShowInInventory = v));
                    items.add(ConfigItem.toggle("  Equipment Show Tooltip on Hover", "Show item info and lore tooltip when hovering over equipment slots.", category, () -> s.equipmentHudShowTooltip, v -> s.equipmentHudShowTooltip = v));
                }
                items.add(ConfigItem.hudToggle("Inventory HUD", "Compact 27-slot inventory preview HUD with hover tooltips.", category, () -> s.inventoryHud, v -> s.inventoryHud = v, HudTarget.INVENTORY_HUD));
                if (s.inventoryHud) {
                    items.add(ConfigItem.toggle("  Inventory Show in GUIs", "Keep Inventory HUD visible while container / inventory GUIs are open.", category, () -> s.inventoryHudShowInInventory, v -> s.inventoryHudShowInInventory = v));
                    items.add(ConfigItem.toggle("  Inventory Show Tooltip on Hover", "Show item info and lore tooltip when hovering over inventory slots.", category, () -> s.inventoryHudShowTooltip, v -> s.inventoryHudShowTooltip = v));
                }

                items.add(ConfigItem.header("HUD Background & Slots Styling", category));
                items.add(ConfigItem.color("HUD Background Color", "Base background color of HUD panels (HEX e.g. #0F172A).", category, () -> s.hudBgColor != null ? s.hudBgColor : "#0F172A", v -> s.hudBgColor = v));
                items.add(ConfigItem.sliderInt("HUD Background Opacity", "Alpha transparency of HUD background panel (0-255).", category, 0, 255, 5, "", () -> s.hudBgAlpha, v -> s.hudBgAlpha = v));
                items.add(ConfigItem.color("HUD Border Color", "Outer border color of HUD panels (HEX e.g. #00E5FF).", category, () -> s.hudBorderColor != null ? s.hudBorderColor : "#00E5FF", v -> s.hudBorderColor = v));
                items.add(ConfigItem.sliderInt("HUD Border Opacity", "Alpha transparency of HUD outer border (0-255).", category, 0, 255, 5, "", () -> s.hudBorderAlpha, v -> s.hudBorderAlpha = v));
                items.add(ConfigItem.color("Slot Background Color", "Interior fill color for item slots (HEX e.g. #1E293B).", category, () -> s.hudSlotBgColor != null ? s.hudSlotBgColor : "#1E293B", v -> s.hudSlotBgColor = v));
                items.add(ConfigItem.sliderInt("Slot Background Opacity", "Alpha transparency of item slot interior (0-255).", category, 0, 255, 5, "", () -> s.hudSlotBgAlpha, v -> s.hudSlotBgAlpha = v));
                items.add(ConfigItem.color("Slot Lining Color", "Border and lining color around individual slots (HEX e.g. #FFFFFF).", category, () -> s.hudSlotBorderColor != null ? s.hudSlotBorderColor : "#FFFFFF", v -> s.hudSlotBorderColor = v));
                items.add(ConfigItem.sliderInt("Slot Lining Opacity", "Alpha transparency of slot lining borders (0-255).", category, 0, 255, 5, "", () -> s.hudSlotBorderAlpha, v -> s.hudSlotBorderAlpha = v));
            }

            case "Item Highlights" -> {
                items.add(ConfigItem.header("Ground Item Drop ESP", category));
                items.add(ConfigItem.toggle("Item Highlights Enabled", "Highlights rare dropped items on the ground.", category, () -> s.itemHighlightsEnabled, v -> s.itemHighlightsEnabled = v));
            }

            case "Keybinds" -> {
                items.add(ConfigItem.header("Profile Command Keybinds", category));
                items.add(ConfigItem.cycle("Active Profile", "Select configuration profile for keybinds.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));
                items.add(ConfigItem.toggle("Ignore Caps Lock", "Allow command keybind execution when Caps Lock is on.", category, () -> s.ignoreCapsLock, v -> s.ignoreCapsLock = v));

                // Dedicated In-Game Command Keybinds Card
                items.add(ConfigItem.dynamicCustomCard("In-Game Command Keybinds", category,
                        ConfigCustomWidgets::getKeybindsCardHeight,
                        ConfigCustomWidgets::renderKeybindsManager,
                        ConfigCustomWidgets::handleKeybindsClick));

                // Dedicated GUI Container Keybinds (Profile Binds) Card
                items.add(ConfigItem.dynamicCustomCard("GUI Container Keybinds (Profile Binds)", category,
                        ConfigCustomWidgets::getGuiBindsCardHeight,
                        ConfigCustomWidgets::renderGuiBindsManager,
                        ConfigCustomWidgets::handleGuiBindsClick));
            }

            case "Kuudra" -> {
                items.add(ConfigItem.header("Kuudra Mandible & Phase Tools", category));
                items.add(ConfigItem.hudToggle("Blindness Timer HUD", "Timer countdown for Kuudra blindness phase overlay.", category, () -> s.kuudraBlindnessTimer, v -> s.kuudraBlindnessTimer = v, HudTarget.KUUDRA));
                items.add(ConfigItem.toggle("Perk Menu Clicker", "Auto clicks perk menu upgrade options.", category, () -> s.perkMenuClicker, v -> s.perkMenuClicker = v));
                items.add(ConfigItem.toggle("Auto GFS Toxic", "Auto refills Toxic Arrow Poison from sacks.", category, () -> s.autoGfsToxic, v -> s.autoGfsToxic = v));
                items.add(ConfigItem.sliderInt("Toxic Refill Count", "Target count of Toxic Arrow Poison to keep.", category, 1, 64, 1, "", () -> s.autoGfsToxicCount, v -> s.autoGfsToxicCount = v));
                items.add(ConfigItem.toggle("Auto GFS Twilight", "Auto refills Twilight Arrow Poison from sacks.", category, () -> s.autoGfsTwilight, v -> s.autoGfsTwilight = v));
            }

            case "Lore Additions" -> {
                items.add(ConfigItem.header("Lore Additions (Custom Tooltips)", category));
                items.add(ConfigItem.toggle("Lore Additions Master Toggle", "Enable custom tooltip modifications, estimated values, and calculations.", category, () -> s.loreAdditionsEnabled, v -> s.loreAdditionsEnabled = v));

                items.add(ConfigItem.header("Price & Market Lore", category));
                items.add(ConfigItem.toggle("Show Bazaar Buy/Sell", "Displays both instant-buy and instant-sell prices on Bazaar item lore (e.g. 31k / 12k).", category, () -> s.showBazaarBuySell, v -> s.showBazaarBuySell = v));
                items.add(ConfigItem.toggle("Show 7d Avg Lowest BIN", "Displays 7-day historical average lowest BIN on item tooltips.", category, () -> s.showAvgLowestBin7d, v -> s.showAvgLowestBin7d = v));
                items.add(ConfigItem.toggle("Show 30d Avg Lowest BIN", "Displays 30-day historical average lowest BIN on item tooltips.", category, () -> s.showAvgLowestBin30d, v -> s.showAvgLowestBin30d = v));

                items.add(ConfigItem.header("Price History Graph & Theme", category));
                items.add(ConfigItem.keybind("Price History Graph Key", "Open historical price chart for hovered item or held item.", category, () -> s.priceHistoryKey != null ? s.priceHistoryKey : "H", v -> s.priceHistoryKey = v));
                items.add(ConfigItem.toggle("Show Mayors on Price Graph", "Displays Mayor terms as colored timeline bands with perks on hover.", category, () -> s.priceHistoryShowMayors, v -> s.priceHistoryShowMayors = v));
                items.add(ConfigItem.color("Price Graph Background", "Custom background color for Price History modal.", category, () -> s.priceHistoryBgColor != null ? s.priceHistoryBgColor : "#120824", v -> s.priceHistoryBgColor = v));
                items.add(ConfigItem.color("Price Graph Border", "Custom border color for Price History window.", category, () -> s.priceHistoryBorderColor != null ? s.priceHistoryBorderColor : "#7C3AED", v -> s.priceHistoryBorderColor = v));
                items.add(ConfigItem.color("Price Graph Line", "Custom line graph color for price curve.", category, () -> s.priceHistoryLineColor != null ? s.priceHistoryLineColor : "#A855F7", v -> s.priceHistoryLineColor = v));

                items.add(ConfigItem.header("Estimated Value Calculations", category));
                items.add(ConfigItem.toggle("Show Estimated Value", "Displays calculated total coin value on item tooltips.", category, () -> s.showEstimatedValue, v -> s.showEstimatedValue = v));
                // Same switch as "Value Price Source" in the HUD category, kept here because this
                // is where anyone auditing estimated values looks first. Both write both fields so
                // the legacy boolean and the new mode string can never disagree.
                items.add(ConfigItem.cycle("Estimated Value Price Source", "Instant Buy (Lowest BIN) vs Instant Sell (Bazaar Sell Offer / Average BIN).", category,
                        List.of(VALUE_SOURCE_BUY, VALUE_SOURCE_SELL),
                        () -> sellsAtValueSource(s) ? VALUE_SOURCE_SELL : VALUE_SOURCE_BUY,
                        v -> applyValueSource(s, v)));
                items.add(ConfigItem.toggle("Prefer Cheapest (AH vs Craft)", "Chooses the cheaper option between lowest BIN and raw craft cost.", category, () -> s.estimatedValuePreferCheapest, v -> s.estimatedValuePreferCheapest = v));
                items.add(ConfigItem.toggle("Show Full Value Breakdown", "Shows detailed line-by-line coin breakdown (Base, Enchants, Stars, Reforge, Gems) on item hover.", category, () -> s.estimatedValueFullBreakdown, v -> s.estimatedValueFullBreakdown = v));

                // Dedicated Custom Tooltip Ordering and Position Card
                items.add(ConfigItem.customCard("Lore Additions Ordering & Display", category,
                        ConfigCustomWidgets.getLoreAdditionsCardHeight(),
                        ConfigCustomWidgets::renderLoreAdditionsCard,
                        ConfigCustomWidgets::handleLoreAdditionsClick));
            }

            case "Mining" -> {
                items.add(ConfigItem.header("Mining & Glacite Mineshafts", category));
                items.add(ConfigItem.toggle("Corpse ESP", "Highlights frozen corpses in Glacite Mineshafts.", category, () -> s.corpseEsp, v -> s.corpseEsp = v));
                items.add(ConfigItem.toggle("Hide Opened Corpses", "Hides ESP once corpse is looted.", category, () -> s.hideOpenedCorpses, v -> s.hideOpenedCorpses = v));
                items.add(ConfigItem.color("Lapis Corpse Color", "Color for Lapis corpse outline.", category, () -> s.lapisOutlineColor != null ? s.lapisOutlineColor : "BLUE", v -> s.lapisOutlineColor = v));
                items.add(ConfigItem.color("Tungsten Corpse Color", "Color for Tungsten corpse outline.", category, () -> s.tungstenOutlineColor != null ? s.tungstenOutlineColor : "WHITE", v -> s.tungstenOutlineColor = v));
                items.add(ConfigItem.color("Umber Corpse Color", "Color for Umber corpse outline.", category, () -> s.umberOutlineColor != null ? s.umberOutlineColor : "GOLD", v -> s.umberOutlineColor = v));
                items.add(ConfigItem.color("Vanguard Corpse Color", "Color for Vanguard corpse outline.", category, () -> s.vanguardOutlineColor != null ? s.vanguardOutlineColor : "LIGHT_PURPLE", v -> s.vanguardOutlineColor = v));
            }

            case "Misc" -> {
                items.add(ConfigItem.header("Controls & Chat", category));
                items.add(ConfigItem.toggle("Controls Search Bar", "Adds an interactive search bar in Minecraft Controls / Keybinds menu.", category, () -> s.controlsSearchBar, v -> s.controlsSearchBar = v));
                items.add(ConfigItem.hudToggle("In-Chat Search Bar", "Adds an interactive search bar directly inside the chat window.", category, () -> s.chatSearchBar, v -> s.chatSearchBar = v, HudTarget.CHAT_SEARCH));
                items.add(ConfigItem.toggle("Copy Chat", "Right-click or click chat messages to copy them to clipboard.", category, () -> s.copyChat, v -> s.copyChat = v));

                items.add(ConfigItem.header("Totem Animation (/b totem)", category));
                items.add(ConfigItem.toggle("Enable Totem Animation (Rare Drops)", "Plays totem animation pop-up showing the rare drop item on RNG drops.", category, () -> s.totemRareDrops, v -> s.totemRareDrops = v));
                if (s.totemRareDrops) {
                    items.add(ConfigItem.toggle("  Totem Animation (All Drops)", "Plays totem animation pop-up for any new item that enters your inventory.", category, () -> s.totemAllDrops, v -> s.totemAllDrops = v));
                }
                items.add(ConfigItem.button("Test Totem (Held Item)", "Test Hand Item", "Displays totem animation holding current main hand item (/b totem or /b totem <id>).", category, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        ItemStack held = mc.player.getMainHandItem();
                        me.bombo.bomboaddons.features.TotemAnimationManager.playTotemAnimation(held);
                    }
                }));

                items.add(ConfigItem.header("Backpack & Storage Preview", category));
                items.add(ConfigItem.cycle("Preview Trigger Mode", "When to display backpack and storage previews.", category,
                        List.of("ALWAYS", "ON_KEY"),
                        () -> s.backpackPreviewTrigger != null ? s.backpackPreviewTrigger : "ALWAYS",
                        v -> s.backpackPreviewTrigger = v));
                if ("ON_KEY".equalsIgnoreCase(s.backpackPreviewTrigger)) {
                    items.add(ConfigItem.keybind("  Preview Keybind", "Key to hold while hovering to show backpack preview.", category, () -> s.backpackPreviewKey != null ? s.backpackPreviewKey : "LEFT_SHIFT", v -> s.backpackPreviewKey = v));
                }
                items.add(ConfigItem.cycle("Preview Background Style", "Background style for backpack/enderchest tooltip previews.", category,
                        List.of("AUTO", "CUSTOM", "VANILLA", "TRANSPARENT"),
                        () -> s.backpackPreviewBgMode != null ? s.backpackPreviewBgMode : "AUTO",
                        v -> s.backpackPreviewBgMode = v));
                if ("CUSTOM".equalsIgnoreCase(s.backpackPreviewBgMode)) {
                    items.add(ConfigItem.text("Custom Background Color", "Hex color code for preview background (e.g. #1E293B).", category, () -> s.backpackPreviewCustomColor != null ? s.backpackPreviewCustomColor : "#1E293B", v -> s.backpackPreviewCustomColor = v));
                }
                items.add(ConfigItem.toggle("Highlight Item Rarity", "Displays color tint behind items in backpack preview based on rarity.", category, () -> s.backpackPreviewItemRarity, v -> s.backpackPreviewItemRarity = v));
                if (s.backpackPreviewItemRarity) {
                    items.add(ConfigItem.cycle("  Rarity Highlight Shape", "Shape of rarity background highlight.", category, List.of("SQUARE", "CIRCLE", "BORDER"), () -> s.backpackPreviewRarityShape != null ? s.backpackPreviewRarityShape : "SQUARE", v -> s.backpackPreviewRarityShape = v));
                    if ("CIRCLE".equalsIgnoreCase(s.backpackPreviewRarityShape)) {
                        items.add(ConfigItem.sliderFloat("  Circle Radius", "Size of rarity background circle in pixels.", category, 2.0f, 12.0f, 0.5f, "px", () -> s.backpackPreviewCircleSize > 0 ? s.backpackPreviewCircleSize : 5.0f, v -> s.backpackPreviewCircleSize = v));
                    }
                    items.add(ConfigItem.toggle("  Item Rarity Border", "Renders 1px colored outline around item slots matching item rarity.", category, () -> s.backpackPreviewItemBorder, v -> s.backpackPreviewItemBorder = v));
                    items.add(ConfigItem.sliderFloat("  Rarity Opacity", "Opacity alpha of the rarity highlight color.", category, 0.05f, 1.0f, 0.05f, "", () -> s.backpackPreviewRarityAlpha > 0 ? s.backpackPreviewRarityAlpha : 0.35f, v -> s.backpackPreviewRarityAlpha = v));
                }
                items.add(ConfigItem.toggle("Inventory / HUD Rarity Background", "Displays rarity color tint in normal inventory container slots and on Armor / Equipment / Inventory HUDs.", category, () -> s.inventoryItemRarityBg, v -> s.inventoryItemRarityBg = v));
            }

            case "Ordered Waypoints" -> {
                items.add(ConfigItem.header("Ordered Waypoints & Sequential Routes", category));
                items.add(ConfigItem.toggle("Through Walls", "Render ordered waypoint bounding boxes and markers through walls.", category,
                        () -> s.orderedWaypointsThroughWalls,
                        v -> { s.orderedWaypointsThroughWalls = v; BomboConfig.save(); }));
                items.add(ConfigItem.sliderInt("Visible Ahead", "How many sequential waypoints ahead to render (0 = All).", category,
                        0, 50, 1, "",
                        () -> s.orderedWaypointsVisibleCount,
                        v -> { s.orderedWaypointsVisibleCount = v; BomboConfig.save(); }));
                items.add(ConfigItem.dynamicCustomCard("Ordered Waypoints Manager", category,
                        ConfigCustomWidgets::getOrderedWaypointsCardHeight,
                        ConfigCustomWidgets::renderOrderedWaypointsManager,
                        ConfigCustomWidgets::handleOrderedWaypointsClick));
            }

            case "Particle Highlights" -> {
                items.add(ConfigItem.header("Particle ESP & Detection", category));
                items.add(ConfigItem.toggle("Particle ESP", "Highlights specific particle clusters in the world.", category, () -> s.particleHighlightsEnabled, v -> s.particleHighlightsEnabled = v));
                items.add(ConfigItem.text("Active Island Filter", "Only enable particle ESP on specific island (leave blank for all).", category, () -> s.particleHighlightsIsland != null ? s.particleHighlightsIsland : "", v -> s.particleHighlightsIsland = v));

                // Dedicated Particle Highlights Manager Card
                items.add(ConfigItem.customCard("Particle ESP Filters", category,
                        ConfigCustomWidgets.getParticleHighlightsCardHeight(),
                        ConfigCustomWidgets::renderParticleHighlightsCard,
                        ConfigCustomWidgets::handleParticleHighlightsClick));
            }

            case "Pets" -> {
                items.add(ConfigItem.header("Pet Fast Swapping", category));
                items.add(ConfigItem.toggle("Disable Unequip Pet", "Prevents clicking active pet to despawn it.", category, () -> s.disableUnequipPet, v -> s.disableUnequipPet = v));
                for (int i = 0; i < 9; i++) {
                    int petIdx = i;
                    items.add(ConfigItem.keybind("Pet Slot " + (petIdx + 1), "Keybind to equip pet in hotbar/slot " + (petIdx + 1), category,
                            () -> petIdx < s.petKeys.size() ? s.petKeys.get(petIdx) : "",
                            key -> {
                                while (s.petKeys.size() <= petIdx) s.petKeys.add("");
                                s.petKeys.set(petIdx, key);
                            }));
                }
            }

            case "Profiles" -> {
                items.add(ConfigItem.header("Configuration Profiles & Management", category));
                items.add(ConfigItem.cycle("Active Profile", "Current active configuration profile.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));

                // Profile Management & Binds Card
                items.add(ConfigItem.customCard("Profile Management & Binds", category, 210,
                        ConfigCustomWidgets::renderProfilesManager,
                        ConfigCustomWidgets::handleProfilesManagerClick));

                items.add(ConfigItem.header("Auto Profile Swapping (Rules & Triggers)", category));
                items.add(ConfigItem.toggle("Auto Swap Profiles Enabled", "Automatically switches configuration profile when matching island, class, armor or chat rules.", category, () -> s.autoSwapProfiles, v -> s.autoSwapProfiles = v));
                items.add(ConfigItem.toggle("Chat Notification", "Prints a message in chat when profile auto-swap activates.", category, () -> s.autoSwapNotifyChat, v -> s.autoSwapNotifyChat = v));
                items.add(ConfigItem.toggle("Screen Notification", "Shows on-screen title when profile auto-swap activates.", category, () -> s.autoSwapNotifyScreen, v -> s.autoSwapNotifyScreen = v));
                items.add(ConfigItem.toggle("Sound Chime", "Plays chime sound when profile auto-swap activates.", category, () -> s.autoSwapNotifySound, v -> s.autoSwapNotifySound = v));

                items.add(ConfigItem.dynamicCustomCard("Auto Profile Swap Rules", category,
                        ConfigCustomWidgets::getAutoProfileRulesCardHeight,
                        ConfigCustomWidgets::renderAutoProfileRulesCard,
                        ConfigCustomWidgets::handleAutoProfileRulesClick));
            }

            case "Sounds" -> {
                items.add(ConfigItem.header("Custom Sound Files & Replacements", category));
                // Dedicated Modern Sounds Manager Card (replaces old GUI)
                items.add(ConfigItem.customCard("Custom Sounds & Replacements Manager", category,
                        ConfigCustomWidgets.getCustomSoundsCardHeight(),
                        ConfigCustomWidgets::renderCustomSoundsCard,
                        ConfigCustomWidgets::handleCustomSoundsClick));

                items.add(ConfigItem.header("Alert Sounds & Chimes", category));
                items.add(ConfigItem.toggle("Diana Lootshare Sound Alert", "Plays sound chime when Diana mob is ready for lootshare.", category, () -> s.dianaLsSound, v -> s.dianaLsSound = v));
                items.add(ConfigItem.toggle("Dojo Shoot Sound", "Plays confirmation ding during Dojo shooting range.", category, () -> s.dojoShootSound, v -> s.dojoShootSound = v));
                items.add(ConfigItem.toggle("Frozen Blaze Warning Sound", "Plays warning chime when Blaze armor AFK timer is low.", category, () -> s.fbWarnSound, v -> s.fbWarnSound = v));

                items.add(ConfigItem.header("Sound Diagnostics", category));
                items.add(ConfigItem.toggle("Sound Debug Logging", "Logs all played sound identifiers and volume levels in chat/console.", category, () -> s.debugSounds, v -> s.debugSounds = v));
            }

            case "Timers" -> {
                items.add(ConfigItem.header("Custom Cooldown Timers", category));
                items.add(ConfigItem.hudToggle("Custom Timers HUD", "Displays active cooldown timers overlay on screen.", category, () -> s.customTimerHudEnabled, v -> s.customTimerHudEnabled = v, HudTarget.TIMERS));
            }

            case "Wardrobe" -> {
                items.add(ConfigItem.header("Wardrobe Fast Swapping", category));
                items.add(ConfigItem.toggle("Disable Unequip Armor", "Prevents clicking active armor pieces in wardrobe to unequip them.", category, () -> s.disableUnequipWardrobe, v -> s.disableUnequipWardrobe = v));
                for (int i = 0; i < 12; i++) {
                    int slotIdx = i;
                    items.add(ConfigItem.keybind("Wardrobe Slot " + (slotIdx + 1), "Keybind to equip wardrobe armor set " + (slotIdx + 1), category,
                            () -> slotIdx < s.wardrobeKeys.size() ? s.wardrobeKeys.get(slotIdx) : "",
                            key -> {
                                while (s.wardrobeKeys.size() <= slotIdx) s.wardrobeKeys.add("");
                                s.wardrobeKeys.set(slotIdx, key);
                            }));
                }
            }

            case "Waypoints" -> {
                items.add(ConfigItem.header("Custom World Coordinates & Beacons", category));
                items.add(ConfigItem.cycle("Active Profile", "Select active profile for custom waypoints.", category,
                        allProfiles,
                        () -> s.activeProfile != null ? s.activeProfile : "default",
                        v -> s.activeProfile = v));

                // Dedicated Custom Waypoints Manager Card
                items.add(ConfigItem.customCard("Custom Waypoints Manager", category,
                        ConfigCustomWidgets.getWaypointsCardHeight(),
                        ConfigCustomWidgets::renderWaypointsManager,
                        ConfigCustomWidgets::handleWaypointsClick));

                items.add(ConfigItem.header("Waypoint Visual Customization", category));
                items.add(ConfigItem.toggle("Waypoint Fill Box", "Fills interior of waypoint boxes with translucent color.", category, () -> s.waypointShowFill, v -> s.waypointShowFill = v));
                if (s.waypointShowFill) {
                    items.add(ConfigItem.sliderInt("Waypoint Fill Opacity", "Opacity of the box fill (0% = transparent, 100% = solid).", category, 5, 100, 5, "%", () -> s.waypointFillOpacity, v -> s.waypointFillOpacity = v));
                }
                items.add(ConfigItem.toggle("Remove Waypoint When Near", "Automatically deletes or marks waypoint reached when entering radius.", category, () -> s.waypointRemoveWhenNear, v -> s.waypointRemoveWhenNear = v));
                if (s.waypointRemoveWhenNear) {
                    items.add(ConfigItem.sliderFloat("Remove Radius Distance", "Distance in blocks to trigger removal when near.", category, 1.0f, 15.0f, 0.5f, "m", () -> (float)s.waypointRemoveDistance, v -> s.waypointRemoveDistance = v));
                }
            }

            case "Widgets" -> {
                items.add(ConfigItem.header("Tab Widget & Stats Overlays", category));
                items.add(ConfigItem.hudToggle("Tab Widget HUD", "Compact player and server stats widget.", category, () -> s.tabWidgetHudEnabled, v -> s.tabWidgetHudEnabled = v, HudTarget.TAB_WIDGET));
            }

            case "GUI Settings" -> {
                items.add(ConfigItem.header("Profile Viewer (/b pv)", category));
                items.add(ConfigItem.sliderFloat("Window Scale", "Size multiplier for the /b pv window (1.0 - 1.8).", category,
                        1.0f, 1.8f, 0.05f, "x",
                        () -> s.pvScale > 0.0F ? s.pvScale : 1.15F, v -> s.pvScale = v));
                items.add(ConfigItem.sliderInt("Backdrop Dim", "How dark the world gets behind the /b pv window. Lower is more transparent.", category,
                        0, 100, 5, "%",
                        () -> (int) Math.round((s.pvBackgroundAlpha > 0.0F ? s.pvBackgroundAlpha : 0.45F) * 100.0F),
                        v -> s.pvBackgroundAlpha = v / 100.0F));

                items.add(ConfigItem.header("Theme & Color Scheme", category));
                items.add(ConfigItem.cycle("Theme Mode", "Select overall GUI aesthetic (Dark Mode, Light Mode, Transparent, Zamasu).", category,
                        List.of("Dark Mode", "Light Mode", "Transparent", "Zamasu"),
                        () -> s.guiThemeMode != null ? s.guiThemeMode : "Dark Mode",
                        v -> {
                            boolean wasZamasu = "Zamasu".equalsIgnoreCase(s.guiThemeMode) || s.zamasuWorldWhite;
                            s.guiThemeMode = v;
                            boolean isZamasu = "Zamasu".equalsIgnoreCase(v);
                            s.zamasuWorldWhite = isZamasu;
                            BomboConfig.save();
                            if (wasZamasu != isZamasu && Minecraft.getInstance().levelRenderer != null) {
                                Minecraft.getInstance().levelRenderer.clearVisibleSections();
                            }
                        }));
                items.add(ConfigItem.cycle("GUI Font", "Select typography font style across the config interface.", category,
                        List.of("Default", "Minecraft", "Alt", "Illusion", "Uniform", "Smooth"),
                        () -> s.guiFont != null ? s.guiFont : "Default",
                        v -> s.guiFont = v));
                items.add(ConfigItem.cycle("Accent Color (Primary)", "Primary highlight color for active tabs, borders, and sliders.", category,
                        List.of("Cyan", "Gold", "Emerald", "Purple", "Red", "Blue", "Pink", "Orange", "White"),
                        () -> s.guiAccentColor != null ? s.guiAccentColor : "Cyan",
                        v -> s.guiAccentColor = v));
                items.add(ConfigItem.cycle("Secondary Color (Borders)", "Color for secondary accents, icons, and frames.", category,
                        List.of("White", "Cyan", "Gold", "Emerald", "Purple", "Blue", "Pink", "Orange"),
                        () -> s.guiSecondaryColor != null ? s.guiSecondaryColor : "White",
                        v -> s.guiSecondaryColor = v));
                items.add(ConfigItem.cycle("Toggle Switch Color", "Active state color for interactive toggle switches.", category,
                        List.of("Emerald", "Cyan", "Gold", "Purple", "Pink", "Blue", "Red"),
                        () -> s.guiToggleColor != null ? s.guiToggleColor : "Emerald",
                        v -> s.guiToggleColor = v));

                items.add(ConfigItem.header("Dimensions & Typography", category));
                items.add(ConfigItem.sliderFloat("Text Scale", "Scale multiplier for text across the GUI.", category,
                        0.80f, 1.50f, 0.05f, "x",
                        () -> s.guiTextScale > 0 ? s.guiTextScale : 1.0f,
                        v -> s.guiTextScale = v));
                items.add(ConfigItem.sliderInt("Background Opacity", "Opacity of the screen backdrop (0% = clear, 100% = solid).", category,
                        0, 100, 5, "%",
                        () -> s.guiBackgroundOpacity >= 0 ? s.guiBackgroundOpacity : 90,
                        v -> s.guiBackgroundOpacity = v));
                items.add(ConfigItem.sliderFloat("Window Scale", "Overall scale multiplier of the configuration window.", category,
                        0.60f, 1.60f, 0.05f, "x",
                        () -> s.guiWindowScale > 0 ? s.guiWindowScale : 1.0f,
                        v -> s.guiWindowScale = v));
                items.add(ConfigItem.sliderInt("Sidebar Width", "Width in pixels of the category navigation sidebar.", category,
                        120, 260, 5, "px",
                        () -> s.guiSidebarWidth > 0 ? s.guiSidebarWidth : 180,
                        v -> s.guiSidebarWidth = v));
                items.add(ConfigItem.toggle("Card Hover Glow", "Displays accent border glow when hovering setting cards.", category,
                        () -> s.guiCardGlow,
                        v -> s.guiCardGlow = v));
            }

            case "Debug" -> {
                items.add(ConfigItem.header("Outbound API History (/b apihistory)", category));
                items.add(ConfigItem.toggle("Track API Requests",
                        "Record every outbound HTTP/WebSocket request (Hypixel, Athen, EliteSkyblock, Bombo API) with status and response time.",
                        category, () -> s.apiHistoryEnabled, v -> s.apiHistoryEnabled = v));
                items.add(ConfigItem.sliderInt("API History Entries", "How many requests to keep in the in-memory history.", category,
                        50, 5000, 50, "req",
                        () -> s.apiHistoryMaxEntries > 0 ? s.apiHistoryMaxEntries : 500, v -> s.apiHistoryMaxEntries = v));
                items.add(ConfigItem.button("Open API History", "Open /b apihistory", "Opens the outbound request log viewer.", category, () -> {
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.gui.ApiHistoryScreen()));
                }));
                items.add(ConfigItem.header("Master Diagnostics Controls", category));
                items.add(ConfigItem.button("Enable All Debug Logs", "Turn On All Logs", "Enables verbose diagnostic logging across all systems.", category, () -> {
                    s.debugMaster = true;
                    s.debugChat = true;
                    s.debugGuis = true;
                    s.debugEntities = true;
                    s.debugCommands = true;
                    s.debugSounds = true;
                    s.debugParticles = true;
                    s.debugArmor = true;
                    s.debugMode = true;
                    s.hotspotDebug = true;
                    s.pvDebug = true;
                    s.eggFinderDebug = true;
                    s.apiDebug = true;
                    s.apiChatMessages = true;
                    s.lbDebug = true;
                    s.petPriceDebug = true;
                    s.debugDailyReward = true;
                    s.debugReconnect = true;
                    s.performanceDebug = true;
                    s.debugKeys = true;
                    s.dojoDebug = true;
                    s.storagePreviewDebug = true;
                    s.npcLoreDebug = true;
                    s.dungeonDebug = true;
                    s.kuudraDebug = true;
                    BomboConfig.save();
                }));
                items.add(ConfigItem.button("Disable All Debug Logs", "Turn Off All Logs", "Disables all debug diagnostic logging.", category, () -> {
                    s.debugMaster = false;
                    s.debugChat = false;
                    s.debugGuis = false;
                    s.debugEntities = false;
                    s.debugCommands = false;
                    s.debugSounds = false;
                    s.debugParticles = false;
                    s.debugArmor = false;
                    s.debugMode = false;
                    s.hotspotDebug = false;
                    s.pvDebug = false;
                    s.eggFinderDebug = false;
                    s.apiDebug = false;
                    s.apiChatMessages = false;
                    s.lbDebug = false;
                    s.petPriceDebug = false;
                    s.debugDailyReward = false;
                    s.debugReconnect = false;
                    s.performanceDebug = false;
                    s.debugKeys = false;
                    s.dojoDebug = false;
                    s.storagePreviewDebug = false;
                    s.npcLoreDebug = false;
                    s.dungeonDebug = false;
                    s.kuudraDebug = false;
                    BomboConfig.save();
                }));

                items.add(ConfigItem.header("Core System Logging", category));
                items.add(ConfigItem.toggle("Master Debug", "Master toggle for debug logs in console.", category, () -> s.debugMaster, v -> s.debugMaster = v));
                items.add(ConfigItem.toggle("Chat Debug", "Log raw incoming chat packet events.", category, () -> s.debugChat, v -> s.debugChat = v));
                items.add(ConfigItem.toggle("GUI Debug", "Log screen opening, closing, and click interactions.", category, () -> s.debugGuis, v -> s.debugGuis = v));
                items.add(ConfigItem.toggle("Entity Debug", "Log spawned and tracked entity metadata in the world.", category, () -> s.debugEntities, v -> s.debugEntities = v));
                items.add(ConfigItem.toggle("Command Debug", "Log executed client commands and execution timing.", category, () -> s.debugCommands, v -> s.debugCommands = v));
                items.add(ConfigItem.toggle("Sound Debug", "Log played sound identifiers and volume levels in chat/console.", category, () -> s.debugSounds, v -> s.debugSounds = v));
                items.add(ConfigItem.toggle("Camera Movement Debug", "Log mouse turning, player yaw/pitch, and camera entity angles.", category, () -> s.cameraDebug, v -> s.cameraDebug = v));
                items.add(ConfigItem.toggle("Particle Debug", "Log spawned particle packet types and coordinates.", category, () -> s.debugParticles, v -> s.debugParticles = v));

                items.add(ConfigItem.header("Feature Diagnostics", category));
                items.add(ConfigItem.toggle("Damage Number Debug", "Prints raw damage entity text and display components to chat.", category, () -> s.showDamageDebug, v -> s.showDamageDebug = v));
                items.add(ConfigItem.toggle("API Debug", "Log API requests (Ashcon, Hypixel, Price API).", category, () -> s.apiDebug, v -> s.apiDebug = v));
                items.add(ConfigItem.toggle("API Chat Messages", "Print API diagnostic status messages directly in chat.", category, () -> s.apiChatMessages, v -> s.apiChatMessages = v));
                items.add(ConfigItem.toggle("Lowest BIN Debug", "Log Lowest BIN lookup cache and updates.", category, () -> s.lbDebug, v -> s.lbDebug = v));
                items.add(ConfigItem.toggle("Pet Price Debug", "Log Pet Lowest BIN calculations and parsing.", category, () -> s.petPriceDebug, v -> s.petPriceDebug = v));
                items.add(ConfigItem.toggle("Hotspot Debug", "Log Crimson Isle fishing hotspot tracking.", category, () -> s.hotspotDebug, v -> s.hotspotDebug = v));
                items.add(ConfigItem.toggle("Coord Bind Debug", "Log random min/max delay and execution timing in chat when triggering a Coord Bind.", category, () -> s.coordBindDebug, v -> s.coordBindDebug = v));
                items.add(ConfigItem.toggle("Profile Viewer (PV) Debug", "Log PV inventory decoding and stats.", category, () -> s.pvDebug, v -> s.pvDebug = v));
                items.add(ConfigItem.toggle("Egg Finder Debug", "Log chocolate egg coordinate scanning details.", category, () -> s.eggFinderDebug, v -> s.eggFinderDebug = v));
                items.add(ConfigItem.toggle("Armor Debug", "Log player armor change packets and sets.", category, () -> s.debugArmor, v -> s.debugArmor = v));
                items.add(ConfigItem.toggle("Dojo Debug", "Log Dojo testing and targets.", category, () -> s.dojoDebug, v -> s.dojoDebug = v));
                items.add(ConfigItem.toggle("Storage Preview Debug", "Log storage chest preview decoding.", category, () -> s.storagePreviewDebug, v -> s.storagePreviewDebug = v));
                items.add(ConfigItem.toggle("Dungeon Debug", "Log dungeon room parsing and score.", category, () -> s.dungeonDebug, v -> s.dungeonDebug = v));
                items.add(ConfigItem.toggle("Dungeon Map Debug", "Log dungeon map scanning and room clear events.", category, () -> s.dungeonMapDebug, v -> s.dungeonMapDebug = v));
                items.add(ConfigItem.toggle("Kuudra Debug", "Log Kuudra phase transitions and boss tracking.", category, () -> s.kuudraDebug, v -> s.kuudraDebug = v));
                items.add(ConfigItem.toggle("Critter Debug", "Prints party and personal critter captures with (Unique) / (Non-Unique) status to chat.", category, () -> s.critterDebug, v -> s.critterDebug = v));
                items.add(ConfigItem.toggle("Pest Debug", "Log detailed garden pest, texture hash scanning and detection data.", category, () -> s.pestDebug, v -> s.pestDebug = v));
                items.add(ConfigItem.toggle("Pest ESP Debug", "Logs entity tags and raw pest detection info to chat.", category, () -> s.pestEspDebug, v -> s.pestEspDebug = v));
                items.add(ConfigItem.toggle("Performance Debug", "Log rendering execution times and frame drops.", category, () -> s.performanceDebug, v -> s.performanceDebug = v));
            }

            case "Dev" -> {
                items.add(ConfigItem.header("Developer Testing Tools & WIP Features", category));
                items.add(ConfigItem.header("Dungeon Map (Devonian & BetterMap Style)", category));
                items.add(ConfigItem.hudToggle("Dungeon Map", "Displays full live Catacombs dungeon map with rooms, doors & players.", category, () -> s.dungeonMap, v -> s.dungeonMap = v, HudTarget.DUNGEON_MAP));
                items.add(ConfigItem.sliderFloat("Dungeon Map Scale", "Scale multiplier for Dungeon Map HUD overlay.", category, 0.5f, 2.5f, 0.1f, "x", () -> s.dungeonMapScale, v -> s.dungeonMapScale = v));
                items.add(ConfigItem.toggle("Show Full Undiscovered Map", "Renders unopened/undiscovered dungeon rooms preview in dimmed colors (Devonian & Doogan pre-discovery).", category, () -> s.dungeonMapShowFullMap, v -> s.dungeonMapShowFullMap = v));
                items.add(ConfigItem.toggle("Player Trajectory Timeline", "Enables interactive timeline slider under map to playback player positions over time.", category, () -> s.dungeonMapTimeline, v -> s.dungeonMapTimeline = v));
                items.add(ConfigItem.toggle("Show Player Heads", "Renders player head icons and directional markers on the map.", category, () -> s.dungeonMapShowPlayerHeads, v -> s.dungeonMapShowPlayerHeads = v));
                items.add(ConfigItem.toggle("Show Room Secrets", "Renders secret count on unexplored and cleared rooms.", category, () -> s.dungeonMapShowSecrets, v -> s.dungeonMapShowSecrets = v));
                items.add(ConfigItem.toggle("Show Room Names", "Renders puzzle names and abbreviations on map rooms.", category, () -> s.dungeonMapShowRoomNames, v -> s.dungeonMapShowRoomNames = v));
                items.add(ConfigItem.toggle("Show Checkmarks", "Renders white/green completion checkmarks on rooms.", category, () -> s.dungeonMapShowCheckmarks, v -> s.dungeonMapShowCheckmarks = v));
                items.add(ConfigItem.toggle("Show Player Clears", "Displays real-time room clear counts per party member.", category, () -> s.dungeonMapShowClears, v -> s.dungeonMapShowClears = v));
                items.add(ConfigItem.toggle("Show Map in Boss", "Keeps map visible during the boss fight.", category, () -> s.dungeonMapShowInBoss, v -> s.dungeonMapShowInBoss = v));

                items.add(ConfigItem.header("Dungeon Map Custom Colors", category));
                items.add(ConfigItem.color("Entrance Color", "Color for Entrance room.", category, () -> s.dungeonMapColorEntrance, v -> s.dungeonMapColorEntrance = v));
                items.add(ConfigItem.color("Normal Room Color", "Color for standard mob rooms.", category, () -> s.dungeonMapColorNormal, v -> s.dungeonMapColorNormal = v));
                items.add(ConfigItem.color("Puzzle Room Color", "Color for puzzle rooms.", category, () -> s.dungeonMapColorPuzzle, v -> s.dungeonMapColorPuzzle = v));
                items.add(ConfigItem.color("Trap Room Color", "Color for trap rooms.", category, () -> s.dungeonMapColorTrap, v -> s.dungeonMapColorTrap = v));
                items.add(ConfigItem.color("Yellow / Miniboss Color", "Color for yellow miniboss room.", category, () -> s.dungeonMapColorYellow, v -> s.dungeonMapColorYellow = v));
                items.add(ConfigItem.color("Fairy Room Color", "Color for fairy revive room.", category, () -> s.dungeonMapColorFairy, v -> s.dungeonMapColorFairy = v));
                items.add(ConfigItem.color("Blood Room Color", "Color for Watcher / Blood room.", category, () -> s.dungeonMapColorBlood, v -> s.dungeonMapColorBlood = v));

                items.add(ConfigItem.header("Feature Organization & Cheat Classification", category));
                items.add(ConfigItem.button("Open Drag & Drop Organizer GUI", "Open GUI", "Opens dedicated drag & drop feature & category organizer (/b order).", category, () -> {
                    Minecraft.getInstance().setScreenAndShow(new BomboOrderScreen(Minecraft.getInstance().gui.screen()));
                }));
                items.add(ConfigItem.dynamicCustomCard("Feature Category & Cheat Organizer", category,
                        FeatureOrganizerManager::getCardHeight,
                        FeatureOrganizerManager::renderOrganizerCard,
                        FeatureOrganizerManager::handleClick));
            }

            case "Uncategorized" -> {
                items.add(ConfigItem.header("★ All Features (Global Overview & Uncategorized)", category));
                for (String cat : BASE_CATEGORIES) {
                    if (!cat.equals("Uncategorized")) {
                        List<ConfigItem> catItems = getItemsForCategory(cat);
                        if (!catItems.isEmpty()) {
                            items.add(ConfigItem.header("── " + cat + " ──", category));
                            items.addAll(catItems);
                        }
                    }
                }
            }

            default -> {
                // Fallback for categories not in BASE_CATEGORIES switch
                items.add(ConfigItem.header(category + " Settings", category));
                items.add(ConfigItem.button("Open Legacy Config Sub-Editor", "Launch Editor", "Open dedicated detailed editor for " + category + ".", category, () -> {
                    int catIdx = BomboConfigGUI.ALL_CATEGORIES_LIST != null ? BomboConfigGUI.ALL_CATEGORIES_LIST.indexOf(category) : 0;
                    if (catIdx >= 0) {
                        BomboConfigGUI.selectedCategory = catIdx;
                        Minecraft.getInstance().setScreenAndShow(new BomboConfigGUI(null));
                    }
                }));
            }
        }

        return items;
    }

    private static Map<String, ConfigItem> masterItemsCache = null;

    public static Map<String, ConfigItem> getMasterItemsMap() {
        if (masterItemsCache == null) {
            masterItemsCache = new LinkedHashMap<>();
            List<String> allCatsToCache = new ArrayList<>(BASE_CATEGORIES);
            if (!allCatsToCache.contains("GUI Settings")) allCatsToCache.add("GUI Settings");
            if (!allCatsToCache.contains("Debug")) allCatsToCache.add("Debug");
            if (!allCatsToCache.contains("Dev")) allCatsToCache.add("Dev");
            for (String baseCat : allCatsToCache) {
                if ("Uncategorized".equalsIgnoreCase(baseCat)) continue;
                List<ConfigItem> baseItems = getRegisteredItemsForCategory(baseCat);
                for (ConfigItem item : baseItems) {
                    if (item.type != ConfigItem.Type.HEADER && item.name != null && !item.name.isEmpty()) {
                        masterItemsCache.putIfAbsent(item.name, item);
                    }
                }
            }
        }
        return masterItemsCache;
    }

    public static List<ConfigItem> getItemsForCategory(String category) {
        FeatureOrganizerManager.initDefaults();
        // If the category matches a base category in organizer or is a custom category,
        // collect all items assigned to this category from FeatureOrganizerManager!
        List<ConfigItem> assignedItems = new ArrayList<>();
        Map<String, ConfigItem> masterMap = getMasterItemsMap();

        for (Map.Entry<String, FeatureOrganizerManager.FeatureMeta> entry : FeatureOrganizerManager.features.entrySet()) {
            FeatureOrganizerManager.FeatureMeta meta = entry.getValue();
            if (meta.category.equalsIgnoreCase(category)) {
                ConfigItem baseItem = masterMap.get(entry.getKey());
                if (baseItem != null) {
                    assignedItems.add(baseItem.cloneWithCategory(category));
                }
            }
        }

        if (!assignedItems.isEmpty()) {
            List<ConfigItem> result = new ArrayList<>();
            result.add(ConfigItem.header(category + " Settings", category));
            result.addAll(assignedItems);
            return result;
        }

        // Otherwise fallback to hardcoded registered items for this category
        return getRegisteredItemsForCategory(category);
    }
}
