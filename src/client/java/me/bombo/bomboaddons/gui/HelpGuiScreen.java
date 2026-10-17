package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.gui.config.BomboConfigScreen;
import me.bombo.bomboaddons.gui.config.ConfigItem;
import me.bombo.bomboaddons.gui.config.ConfigRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Modern Liquid Glass Help & Feature Explorer GUI for BomboAddons.
 * Provides interactive search, detailed breakdown of all mod commands and sub-options
 * with 1-click execution / suggestion, and comprehensive catalog of features with status badges.
 */
public class HelpGuiScreen extends Screen {

    public enum Tab {
        COMMANDS("⌨ Commands"),
        FEATURES("⚙ Features & Modules");

        public final String title;
        Tab(String title) {
            this.title = title;
        }
    }

    public static class SubOption {
        public final String syntax;
        public final String description;
        public final String commandToRun;

        public SubOption(String syntax, String description, String commandToRun) {
            this.syntax = syntax;
            this.description = description;
            this.commandToRun = commandToRun;
        }
    }

    public static class CommandDoc {
        public final String name;
        public final String category;
        public final String summary;
        public final String detailedDescription;
        public final List<SubOption> subOptions;

        public CommandDoc(String name, String category, String summary, String detailedDescription, List<SubOption> subOptions) {
            this.name = name;
            this.category = category;
            this.summary = summary;
            this.detailedDescription = detailedDescription;
            this.subOptions = subOptions != null ? subOptions : new ArrayList<>();
        }
    }

    public static class FeatureDoc {
        public final String name;
        public final String category;
        public final String description;
        public final java.util.function.Supplier<String> statusSupplier;
        public final Runnable configureAction;

        public FeatureDoc(String name, String category, String description, java.util.function.Supplier<String> statusSupplier, Runnable configureAction) {
            this.name = name;
            this.category = category;
            this.description = description;
            this.statusSupplier = statusSupplier;
            this.configureAction = configureAction;
        }
    }

    private final Screen parent;
    private Tab activeTab = Tab.COMMANDS;
    private String searchQuery = "";
    private boolean isSearchFocused = false;

    private final List<CommandDoc> allCommands = new ArrayList<>();
    private final List<FeatureDoc> allFeatures = new ArrayList<>();

    private int selectedCommandIndex = 0;

    // Scrolling states
    private int commandListScroll = 0;
    private int commandDetailScroll = 0;
    private int featureListScroll = 0;

    public HelpGuiScreen(Screen parent) {
        super(Component.literal("BomboAddons Help"));
        this.parent = parent;
        initializeCatalog();
    }

    private void initializeCatalog() {
        allCommands.clear();
        allFeatures.clear();

        // -------------------------------------------------------------
        // COMMANDS CATALOG (Separated cleanly by top-level command)
        // -------------------------------------------------------------

        // 1. /ac (Auto Croesus)
        allCommands.add(new CommandDoc("/ac", "Dungeons", "Auto Croesus chest selector & claimer.",
                "Automates interaction with the Dungeon Croesus chest menu, calculating profits and auto-claiming optimal reward chests based on current Bazaar and Lowest BIN auction prices.",
                List.of(
                        new SubOption("/ac", "Toggles Auto Croesus feature on or off.", "/ac"),
                        new SubOption("/ac auto", "Toggles fully automatic 1-click chest buying mode.", "/ac auto"),
                        new SubOption("/ac clear", "Clears current Croesus chest selection queue and ignores remaining chests.", "/ac clear"),
                        new SubOption("/ac list", "Lists all pending unopened Croesus chests with estimated profit values.", "/ac list"),
                        new SubOption("/ac chest <name>", "Forces target chest selection by floor name (e.g. /ac chest M7).", "/ac chest ")
                )));

        // 2. /b accept (Chat Confirmations & Prompts)
        allCommands.add(new CommandDoc("/b accept", "Chat", "Silently accepts pending chat prompts & links.",
                "Silently executes pending chat verification actions, party invite confirmations, or clickable link prompts without opening the browser or broadcasting into chat.",
                List.of(
                        new SubOption("/b accept", "Executes the most recent pending chat prompt action.", "/b accept"),
                        new SubOption("/b accept <id>", "Silently confirms a specific prompt or link ID.", "/b accept "),
                        new SubOption("/accept", "Standalone shorthand alias for /b accept.", "/accept")
                )));

        // 3. /b (Main Mod Hub)
        allCommands.add(new CommandDoc("/b", "General", "Main BomboAddons configuration & hub.",
                "Opens the primary Liquid Glass configuration menu or directly configures mod modules.",
                List.of(
                        new SubOption("/b", "Opens the modern Liquid Glass configuration GUI.", "/b"),
                        new SubOption("/b config", "Opens the modern config screen directly.", "/b config"),
                        new SubOption("/b help gui", "Opens this interactive Help & Feature Explorer GUI.", "/b help gui"),
                        new SubOption("/b old", "Opens the legacy fallback configuration GUI.", "/b old"),
                        new SubOption("/b order", "Opens the category and feature order drag-and-drop manager.", "/b order"),
                        new SubOption("/b gui", "Opens the HUD Editor to reposition overlays.", "/b gui")
                )));

        // 4. /mp (Magical Power Optimizer)
        allCommands.add(new CommandDoc("/mp", "Progression", "Magical Power & Accessories Optimizer.",
                "Calculates optimal accessory upgrades ranked by coins per MP, provides authentic 54-slot chest crafting modals, Wikipedia lookups, and dedicated Enrichments auditor.",
                List.of(
                        new SubOption("/mp", "Opens the Magical Power & Accessory optimizer for your active profile.", "/mp"),
                        new SubOption("/mp <player>", "Inspects and optimizes Magical Power for any target player IGN.", "/mp "),
                        new SubOption("/mp talisman", "Opens the dedicated multi-tier accessory progression tree.", "/mp talisman"),
                        new SubOption("/mp enrichments", "Audits accessory enrichments, duplicate hatcessories, and mismatches.", "/mp enrichments"),
                        new SubOption("/b mp", "Alias for /mp.", "/b mp")
                )));

        // 5. /shards (Attribute Shards Optimizer)
        allCommands.add(new CommandDoc("/shards", "Progression", "Attribute Shards Sweet-Spot Optimizer.",
                "Scrapes all 325 attribute shards, calculating optimal fusion pathways, budget sweet-spots, and damage per coin with interactive 3x3 crafting recipes.",
                List.of(
                        new SubOption("/shards", "Opens the Liquid Glass Shards Optimizer GUI.", "/shards"),
                        new SubOption("/shards <class>", "Opens shards optimizer pre-filtered to a specific Dungeon class (e.g. /shards archer).", "/shards archer"),
                        new SubOption("/shards budget <coins>", "Calculates optimal attribute loadout for a target budget (e.g. 50M).", "/shards budget 50M"),
                        new SubOption("/b shards", "Alias for /shards.", "/b shards")
                )));

        // 6. /b perf (Performance Profiler)
        allCommands.add(new CommandDoc("/b perf", "Performance", "Zero-overhead performance profiler & tick monitor.",
                "Inspects tick latencies, CPU profiling tables, thread activity, and confirms zero overhead when features are toggled off.",
                List.of(
                        new SubOption("/b perf", "Opens the high-resolution profiler tables screen.", "/b perf"),
                        new SubOption("/b perf reset", "Resets profiling counters and latency history.", "/b perf reset"),
                        new SubOption("/b perf hud", "Toggles the compact in-game performance HUD overlay.", "/b perf hud"),
                        new SubOption("/perf", "Alias for /b perf.", "/perf")
                )));

        // 7. /b prof (Profiles Manager)
        allCommands.add(new CommandDoc("/b prof", "Profiles", "Multi-profile configuration manager.",
                "Manages distinct mod profiles for different classes or activities (e.g. Dungeons, Mining, Farming). Allows persistent deletion and creation.",
                List.of(
                        new SubOption("/b prof list", "Lists all active and configured profiles with interactive click-to-switch and delete buttons.", "/b prof list"),
                        new SubOption("/b prof <name>", "Switches to or creates a profile with the specified name.", "/b prof "),
                        new SubOption("/b prof delete <name>", "Permanently deletes a profile across all keybinds, waypoints, and configs.", "/b prof delete ")
                )));

        // 8. /b hotbar (Hotbar Slot Swapper)
        allCommands.add(new CommandDoc("/b hotbar", "Inventory", "Inventory & Hotbar slot swapper.",
                "Opens the visual slot swapper screen to configure item swaps between inventory and hotbar slots.",
                List.of(
                        new SubOption("/b hotbar", "Opens the visual inventory slot swapper screen.", "/b hotbar"),
                        new SubOption("/b swap", "Alias for /b hotbar.", "/b swap"),
                        new SubOption("/hotbar", "Standalone shortcut for /b hotbar.", "/hotbar"),
                        new SubOption("/swap", "Standalone shortcut for /b swap.", "/swap")
                )));

        // 9. /b buttons (Custom Inventory Buttons)
        allCommands.add(new CommandDoc("/b buttons", "Inventory", "Custom container click buttons.",
                "Configures custom clickable macro buttons inside container inventories for quick commands or actions.",
                List.of(
                        new SubOption("/b buttons", "Opens the custom inventory buttons editor.", "/b buttons"),
                        new SubOption("/b buttons move", "Opens the interactive screen to reposition inventory buttons.", "/b buttons move"),
                        new SubOption("/b buttons toggle", "Toggles inventory buttons on or off.", "/b buttons toggle"),
                        new SubOption("/buttons", "Standalone shortcut for /b buttons.", "/buttons")
                )));

        // 10. /b chathistory (Chat History & Locraw Tracker)
        allCommands.add(new CommandDoc("/b chathistory", "Chat", "Locraw & Chat History tracker.",
                "Searches past chat messages, system notices, and Hypixel /locraw responses with provenance metadata.",
                List.of(
                        new SubOption("/b chathistory", "Opens the searchable Chat History GUI.", "/b chathistory"),
                        new SubOption("/b chathistory locraw", "Filters chat history specifically to Hypixel /locraw server locations.", "/b chathistory locraw"),
                        new SubOption("/b chathistory search <query>", "Searches historical chat logs matching query.", "/b chathistory search ")
                )));

        // 11. /b pv (SkyBlock Profile Viewer)
        allCommands.add(new CommandDoc("/b pv", "Profiles", "Authentic SkyBlock Profile Viewer.",
                "Inspects any player's full profile including 18 wardrobe sets, armor silhouettes, dynamic pet lores, museum, motes, and dungeon PBs.",
                List.of(
                        new SubOption("/b pv", "Opens profile viewer for your own active profile.", "/b pv"),
                        new SubOption("/b pv <player>", "Fetches and displays full profile stats for target player IGN.", "/b pv "),
                        new SubOption("/pv <player>", "Standalone shortcut for profile viewer.", "/pv ")
                )));

        // 12. /b auto (Automation Sequences)
        allCommands.add(new CommandDoc("/b auto", "Macros", "Visual Sequence & Macro Studio.",
                "Creates, edits, and executes automated key, movement, and command sequences with visual timeline controls.",
                List.of(
                        new SubOption("/b auto", "Opens the Visual Sequence & Macro Studio screen.", "/b auto"),
                        new SubOption("/b auto start <name>", "Starts executing the specified automation sequence.", "/b auto start "),
                        new SubOption("/b auto stop", "Immediately stops all currently running automation sequences.", "/b auto stop"),
                        new SubOption("/b sequence", "Alias for /b auto.", "/b sequence"),
                        new SubOption("/b studio", "Opens the automation sequence builder.", "/b studio")
                )));

        // 13. /b wpo (Ordered Waypoints)
        allCommands.add(new CommandDoc("/b wpo", "Waypoints", "Ordered Waypoints Manager.",
                "Manages ordered path waypoints with clipboard import, visual lines, and auto-progression.",
                List.of(
                        new SubOption("/b wpo", "Opens ordered waypoints configuration.", "/b wpo"),
                        new SubOption("/b wpo import", "Imports ordered waypoints directly from clipboard.", "/b wpo import"),
                        new SubOption("/b wpo clear", "Clears all currently loaded ordered waypoints.", "/b wpo clear")
                )));

        // 14. /b wp (Custom Waypoints)
        allCommands.add(new CommandDoc("/b wp", "Waypoints", "Custom Beacon & Coordinate Waypoints.",
                "Places 3D beacon beams and coordinate markers with custom names and colors across islands.",
                List.of(
                        new SubOption("/b wp", "Opens custom waypoints manager.", "/b wp"),
                        new SubOption("/b wp add <name> [x y z]", "Creates a waypoint at specified coords or player position.", "/b wp add "),
                        new SubOption("/b wp remove <name>", "Deletes a waypoint by name.", "/b wp remove "),
                        new SubOption("/b wp clear", "Clears all waypoints on current island.", "/b wp clear")
                )));

        // 15. /b highlight (Entity Highlights)
        allCommands.add(new CommandDoc("/b highlight", "Visuals", "Persistent Entity Highlights & ESP.",
                "Highlights specific mobs, bosses, or entities with customizable glowing outline colors and bounding boxes.",
                List.of(
                        new SubOption("/b highlight", "Opens persistent entity highlights manager.", "/b highlight"),
                        new SubOption("/b highlight add <mob>", "Adds a mob name or regex pattern to the persistent highlight list.", "/b highlight add "),
                        new SubOption("/b highlight remove <mob>", "Removes a mob from the highlight list.", "/b highlight remove "),
                        new SubOption("/b highlight list", "Prints all actively highlighted entity patterns in chat.", "/b highlight list"),
                        new SubOption("/b mob", "Alias for /b highlight.", "/b mob")
                )));

        // 16. /b block (Block ESP Highlights)
        allCommands.add(new CommandDoc("/b block", "Visuals", "Block ESP & Tracer Highlights.",
                "Highlights custom world blocks by block type or coordinates with bounding boxes and beacon tracers.",
                List.of(
                        new SubOption("/b block", "Opens block highlights manager.", "/b block"),
                        new SubOption("/b block add <block>", "Adds target block to highlight ESP list.", "/b block add "),
                        new SubOption("/b block clear", "Clears active highlighted blocks in area.", "/b block clear")
                )));

        // 17. /b area (Area Detection & Override)
        allCommands.add(new CommandDoc("/b area", "General", "SkyBlock Area Inspector & Simulator.",
                "Displays the current detected SkyBlock island and sub-plot, or allows forcing an area simulation.",
                List.of(
                        new SubOption("/b area", "Prints currently detected island and sub-area in chat.", "/b area"),
                        new SubOption("/b area <name>", "Forces a simulated island area (e.g. /b area The Garden, /b area Hub).", "/b area "),
                        new SubOption("/b area reset", "Clears forced area override and restores live detection.", "/b area reset")
                )));

        // 18. /b apikey (BomboAPI Key Manager)
        allCommands.add(new CommandDoc("/b apikey", "General", "BomboAPI Key & Discord Link.",
                "Manages your personal API authentication key for remote stats, crosshair sharing, and Discord link.",
                List.of(
                        new SubOption("/b apikey", "Prints your active API key and Discord sync status.", "/b apikey"),
                        new SubOption("/b apikey sync", "Synchronizes and pulls your registered key from Discord.", "/b apikey sync"),
                        new SubOption("/b apikey set <key>", "Manually sets your BomboAPI authentication key.", "/b apikey set ")
                )));

        // 19. /b pip (Picture-in-Picture)
        allCommands.add(new CommandDoc("/b pip", "HUD", "Picture-in-Picture Media Overlay.",
                "Streams live screenshares or renders web images/diagrams in a HUD overlay.",
                List.of(
                        new SubOption("/b pip <url>", "Loads and displays the specified stream or image in PiP window.", "/b pip "),
                        new SubOption("/b pip toggle", "Toggles the PiP display overlay on or off.", "/b pip toggle"),
                        new SubOption("/b pip clear", "Clears active loaded media from PiP.", "/b pip clear")
                )));

        // 20. /b cmd (Admin Terminal)
        allCommands.add(new CommandDoc("/b cmd", "Terminal", "In-game Bombo Terminal.",
                "Embedded terminal screen with instant command execution, streaming stdout/stderr, and session history.",
                List.of(
                        new SubOption("/b cmd", "Opens the in-game terminal window.", "/b cmd"),
                        new SubOption("/b cmd <command>", "Executes command in terminal buffer.", "/b cmd "),
                        new SubOption("/b cmd clear", "Clears the active terminal output buffer.", "/b cmd clear")
                )));

        // 21. /clean (Dungeon Cleaner)
        allCommands.add(new CommandDoc("/clean", "Dungeons", "Dungeon Mob & Room Clear Scanner.",
                "Scans active dungeon room for remaining hostile mobs, bats, or starred enemies.",
                List.of(
                        new SubOption("/clean", "Scans and highlights remaining mobs in current room.", "/clean")
                )));

        // 22. /stoptps (TPS Lag Protection)
        allCommands.add(new CommandDoc("/stoptps", "Dungeons", "Dungeon TPS & Tick Lag Alert.",
                "Monitors server tick rates and warns or pauses sequences when server TPS drops below thresholds.",
                List.of(
                        new SubOption("/stoptps", "Toggles TPS lag protection monitoring.", "/stoptps"),
                        new SubOption("/cbtps", "Croesus TPS check and lag safety validator.", "/cbtps")
                )));

        // 23. /freelook & /freecam
        allCommands.add(new CommandDoc("/freelook", "Visuals", "360 Free Camera Rotation.",
                "Rotates the camera freely in 360 degrees without changing the player's movement direction.",
                List.of(
                        new SubOption("/freelook", "Toggles freelook camera mode.", "/freelook"),
                        new SubOption("/freecam", "Toggles detached spectator free-flight camera.", "/freecam")
                )));

        // 24. Quick Warps (/f1-f7, /m1-m7, /t1-t5)
        allCommands.add(new CommandDoc("/f1 - /f7, /m1 - /m7", "Dungeons", "Quick Dungeon & Kuudra Warps.",
                "Instant shorthand warp commands to start Catacombs floors or Kuudra tiers without navigating party menu.",
                List.of(
                        new SubOption("/f1", "Warps party into Catacombs Floor 1.", "/f1"),
                        new SubOption("/f7", "Warps party into Catacombs Floor 7.", "/f7"),
                        new SubOption("/m6", "Warps party into Master Catacombs Floor 6.", "/m6"),
                        new SubOption("/m7", "Warps party into Master Catacombs Floor 7.", "/m7"),
                        new SubOption("/t5", "Warps party into Kuudra Tier 5 (Infernal).", "/t5")
                )));

        // 25. /timer
        allCommands.add(new CommandDoc("/timer", "HUD", "Custom Stopwatches & Timers.",
                "Configures custom on-screen cooldown timers and stopwatches.",
                List.of(
                        new SubOption("/timer", "Starts stopwatch or pauses active timer.", "/timer"),
                        new SubOption("/timer <duration>", "Starts timer for specified duration (e.g. /timer 30s, /timer 5m).", "/timer 30s"),
                        new SubOption("/timer clear", "Clears all active timers.", "/timer clear")
                )));

        // 26. /clicks
        allCommands.add(new CommandDoc("/clicks", "Macros", "Auto-Click Targets & Intervals.",
                "Configures automated click targets and speed intervals in container menus.",
                List.of(
                        new SubOption("/clicks", "Lists active auto-click targets.", "/clicks"),
                        new SubOption("/click debug", "Toggles click debug logging.", "/click debug")
                )));

        // 27. /discord
        allCommands.add(new CommandDoc("/discord", "Discord", "Discord Rich Presence & Voice HUD.",
                "Integrates with local Discord client for Rich Presence and in-game voice channel overlay.",
                List.of(
                        new SubOption("/discord hud", "Toggles Discord voice chat HUD overlay.", "/discord hud"),
                        new SubOption("/discord sync", "Synchronizes Discord client status.", "/discord sync")
                )));

        // 28. /lyrics
        allCommands.add(new CommandDoc("/lyrics", "HUD", "Spotify Synced Lyrics Overlay.",
                "Displays synchronized real-time lyrics for currently playing Spotify track on screen.",
                List.of(
                        new SubOption("/lyrics", "Toggles on-screen Spotify lyrics HUD.", "/lyrics"),
                        new SubOption("/lyrics debug", "Prints active track metadata to chat.", "/lyrics debug")
                )));

        // 29. /lb, /lf, /lfc
        allCommands.add(new CommandDoc("/lb", "Auction", "Lowest BIN Price Lookup.",
                "Queries real-time auction house Lowest BIN prices directly in-game.",
                List.of(
                        new SubOption("/lb <item>", "Queries current Lowest BIN price for item.", "/lb Hyperion"),
                        new SubOption("/lf <player> [query]", "Looks up player's active auction listings.", "/lf ")
                )));

        // 30. /versions
        allCommands.add(new CommandDoc("/versions", "Updater", "Mod Version Manager & Auto-Updater.",
                "Checks API release endpoints for latest full and beta releases with 1-click downloads.",
                List.of(
                        new SubOption("/versions", "Opens version manager and download screen.", "/versions"),
                        new SubOption("/b update check", "Queries API for latest updates.", "/b update check")
                )));

        // -------------------------------------------------------------
        // FEATURES & MODULES CATALOG (Dynamically populated from ConfigRegistry)
        // -------------------------------------------------------------
        Map<String, ConfigItem> master = ConfigRegistry.getMasterItemsMap();
        for (Map.Entry<String, ConfigItem> entry : master.entrySet()) {
            ConfigItem item = entry.getValue();
            if (item == null || item.type == ConfigItem.Type.HEADER) continue;

            String featName = item.name;
            String category = item.category != null ? item.category : "General";
            String desc = (item.description != null && !item.description.trim().isEmpty())
                    ? item.description
                    : "Configurable setting in " + category + " category.";

            java.util.function.Supplier<String> statusSupplier = () -> {
                if (item.type == ConfigItem.Type.TOGGLE) {
                    return (item.boolGetter != null && Boolean.TRUE.equals(item.boolGetter.get())) ? "§aENABLED" : "§cDISABLED";
                } else if (item.type == ConfigItem.Type.SLIDER_INT) {
                    return item.intGetter != null ? "§b" + item.intGetter.get() + (item.intSuffix != null ? item.intSuffix : "") : "§7-";
                } else if (item.type == ConfigItem.Type.SLIDER_FLOAT) {
                    return item.floatGetter != null ? "§b" + String.format(Locale.ROOT, "%.2f", item.floatGetter.get()) + (item.floatSuffix != null ? item.floatSuffix : "") : "§7-";
                } else if (item.type == ConfigItem.Type.CYCLE) {
                    return item.stringGetter != null ? "§e" + item.stringGetter.get() : "§7-";
                } else if (item.type == ConfigItem.Type.COLOR) {
                    return item.stringGetter != null ? "§d" + item.stringGetter.get() : "§7-";
                } else if (item.type == ConfigItem.Type.KEYBIND) {
                    return item.stringGetter != null ? "§6[" + item.stringGetter.get().toUpperCase(Locale.ROOT) + "]" : "§7-";
                }
                return "§7CONFIG";
            };

            Runnable configureAction = () -> {
                Minecraft mc = Minecraft.getInstance();
                this.onClose();
                mc.execute(() -> {
                    BomboConfigScreen.activeCategory = category;
                    mc.setScreenAndShow(new BomboConfigScreen(null));
                });
            };

            allFeatures.add(new FeatureDoc(featName, category, desc, statusSupplier, configureAction));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        // Dark translucent acrylic backdrop
        g.fill(0, 0, this.width, this.height, 0xD00B0F19);

        Font font = this.font;
        int winW = Math.min(840, this.width - 32);
        int winH = Math.min(520, this.height - 24);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Main Liquid Glass Window Body
        g.fill(winX, winY, winX + winW, winY + winH, 0xFA090D16);
        g.outline(winX, winY, winW, winH, 0x6038BDF8);

        // Header Bar
        g.fill(winX, winY, winX + winW, winY + 36, 0x660F172A);
        g.fill(winX, winY + 36, winX + winW, winY + 37, 0x3338BDF8);

        // Title
        g.text(font, "§b§lBOMBOADDONS §8| §fHelp & Feature Explorer", winX + 16, winY + 12, 0xFFFFFFFF, true);

        // Close Button
        int closeX = winX + winW - 28;
        int closeY = winY + 10;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 16;
        g.fill(closeX, closeY, closeX + 18, closeY + 16, closeHov ? 0xFFEF4444 : 0x33EF4444);
        g.outline(closeX, closeY, 18, 16, closeHov ? 0xFFFF7777 : 0x66EF4444);
        g.centeredText(font, "§f✕", closeX + 9, closeY + 4, 0xFFFFFFFF);

        // Tabs
        int tabY = winY + 42;
        int tabX = winX + 16;
        for (Tab t : Tab.values()) {
            boolean isSel = this.activeTab == t;
            int tabW = font.width(t.title) + 24;
            boolean tabHov = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + 20;

            int tabBg = isSel ? 0xFF0284C7 : (tabHov ? 0x3338BDF8 : 0x111E293B);
            int tabBorder = isSel ? 0xFF38BDF8 : (tabHov ? 0x6638BDF8 : 0x22475569);

            g.fill(tabX, tabY, tabX + tabW, tabY + 20, tabBg);
            g.outline(tabX, tabY, tabW, 20, tabBorder);
            g.centeredText(font, (isSel ? "§f§l" : "§7") + t.title, tabX + tabW / 2, tabY + 6, 0xFFFFFFFF);

            tabX += tabW + 8;
        }

        // Search Bar (Right-aligned next to tabs)
        int searchW = 190;
        int searchX = winX + winW - searchW - 16;
        int searchY = tabY;
        boolean searchHov = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 20;

        g.fill(searchX, searchY, searchX + searchW, searchY + 20, isSearchFocused ? 0x660F172A : (searchHov ? 0x331E293B : 0x220F172A));
        g.outline(searchX, searchY, searchW, 20, isSearchFocused ? 0xFF38BDF8 : 0x4464748B);

        String st = searchQuery.isEmpty() ? (isSearchFocused ? "" : "§8🔍 Search catalog...") : searchQuery;
        g.text(font, isSearchFocused ? st + "§b|" : st, searchX + 8, searchY + 6, isSearchFocused ? 0xFFFFFFFF : 0xFF94A3B8, false);

        // Content Area Bounds
        int contentY = winY + 68;
        int contentH = winH - 76;

        if (this.activeTab == Tab.COMMANDS) {
            renderCommandsTab(g, font, winX + 16, contentY, winW - 32, contentH, mouseX, mouseY);
        } else {
            renderFeaturesTab(g, font, winX + 16, contentY, winW - 32, contentH, mouseX, mouseY);
        }
    }

    private void renderCommandsTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<CommandDoc> filtered = getFilteredCommands();
        if (selectedCommandIndex >= filtered.size()) {
            selectedCommandIndex = Math.max(0, filtered.size() - 1);
        }

        int leftW = 210;
        int rightW = w - leftW - 12;

        // Left Panel: Command List
        g.fill(x, y, x + leftW, y + h, 0x660F172A);
        g.outline(x, y, leftW, h, 0x3364748B);

        int itemH = 34;
        int maxVisible = Math.max(1, (h - 8) / itemH);
        int maxScroll = Math.max(0, filtered.size() - maxVisible);
        commandListScroll = Math.max(0, Math.min(maxScroll, commandListScroll));

        int startIdx = commandListScroll;
        int curY = y + 4;

        g.enableScissor(x, y + 2, x + leftW, y + h - 2);
        for (int i = startIdx; i < filtered.size() && (curY + itemH) <= y + h + itemH; i++) {
            CommandDoc cmd = filtered.get(i);
            boolean isSel = i == selectedCommandIndex;
            boolean hov = mouseX >= x + 4 && mouseX <= x + leftW - 4 && mouseY >= curY && mouseY <= curY + itemH - 2 && mouseY >= y && mouseY <= y + h;

            int itemBg = isSel ? 0xFF0284C7 : (hov ? 0x3338BDF8 : 0x111E293B);
            int itemBorder = isSel ? 0xFF38BDF8 : (hov ? 0x6638BDF8 : 0x22475569);

            g.fill(x + 4, curY, x + leftW - 4, curY + itemH - 2, itemBg);
            g.outline(x + 4, curY, leftW - 8, itemH - 2, itemBorder);

            g.text(font, (isSel ? "§f§l" : "§b") + cmd.name, x + 8, curY + 4, 0xFFFFFFFF, false);
            g.text(font, "§7" + cmd.category, x + leftW - 12 - font.width(cmd.category), curY + 4, 0xFFA0AEC0, false);

            String sum = font.plainSubstrByWidth(cmd.summary, leftW - 20);
            g.text(font, "§8" + sum, x + 8, curY + 16, 0xFF94A3B8, false);

            curY += itemH;
        }
        g.disableScissor();

        // Right Panel: Selected Command Details
        int rightX = x + leftW + 12;
        g.fill(rightX, y, rightX + rightW, y + h, 0x660F172A);
        g.outline(rightX, y, rightW, h, 0x3364748B);

        if (!filtered.isEmpty() && selectedCommandIndex >= 0 && selectedCommandIndex < filtered.size()) {
            CommandDoc sel = filtered.get(selectedCommandIndex);

            int detailY = y + 10;
            g.text(font, "§e§l" + sel.name + " §8| §3" + sel.category, rightX + 12, detailY, 0xFFFFFFFF, true);
            detailY += 14;

            // Multi-line word-wrapped detailed description (NEVER leaves the box)
            List<FormattedCharSequence> descLines = font.split(Component.literal("§7" + sel.detailedDescription), rightW - 24);
            for (FormattedCharSequence line : descLines) {
                g.text(font, line, rightX + 12, detailY, 0xFFCBD5E1, false);
                detailY += 10;
            }
            detailY += 6;

            g.fill(rightX + 12, detailY, rightX + rightW - 12, detailY + 1, 0x3364748B);
            detailY += 8;

            g.text(font, "§6§lSub-Commands & Options (" + sel.subOptions.size() + ")", rightX + 12, detailY, 0xFFFFFFFF, true);
            detailY += 16;

            int optH = 46;
            int subListTop = detailY;
            int subListBottom = y + h - 6;
            int subListHeight = Math.max(1, subListBottom - subListTop);

            int totalOptH = sel.subOptions.size() * (optH + 4);
            int maxDetailScroll = Math.max(0, totalOptH - subListHeight);
            commandDetailScroll = Math.max(0, Math.min(maxDetailScroll, commandDetailScroll));

            int optStartY = subListTop - commandDetailScroll;
            int maxDescW = rightW - 24 - 135; // Leave room for Run & Paste action buttons

            g.enableScissor(rightX + 8, subListTop, rightX + rightW - 8, subListBottom);
            for (SubOption opt : sel.subOptions) {
                if (optStartY + optH > subListTop - 10 && optStartY < subListBottom + 10) {
                    boolean optHov = mouseX >= rightX + 12 && mouseX <= rightX + rightW - 12 && mouseY >= optStartY && mouseY <= optStartY + optH && mouseY >= subListTop && mouseY <= subListBottom;

                    g.fill(rightX + 12, optStartY, rightX + rightW - 12, optStartY + optH, optHov ? 0x2238BDF8 : 0x111E293B);
                    g.outline(rightX + 12, optStartY, rightW - 24, optH, optHov ? 0x6638BDF8 : 0x22475569);

                    // Syntax trimmed to maxDescW
                    String syn = font.plainSubstrByWidth(opt.syntax, maxDescW);
                    g.text(font, "§b" + syn, rightX + 18, optStartY + 6, 0xFFFFFFFF, false);

                    // Description trimmed to maxDescW so it never touches buttons or exits card
                    String d = font.plainSubstrByWidth(opt.description, maxDescW);
                    g.text(font, "§7" + d, rightX + 18, optStartY + 20, 0xFF94A3B8, false);

                    // Action buttons: [ ⚡ Run ] and [ 📋 Paste ]
                    int btnRunW = 54;
                    int btnRunX = rightX + rightW - 12 - btnRunW - 8;
                    int btnRunY = optStartY + 6;
                    boolean runHov = mouseX >= btnRunX && mouseX <= btnRunX + btnRunW && mouseY >= btnRunY && mouseY <= btnRunY + 16 && mouseY >= subListTop && mouseY <= subListBottom;

                    g.fill(btnRunX, btnRunY, btnRunX + btnRunW, btnRunY + 16, runHov ? 0xFF10B981 : 0xFF064E3B);
                    g.outline(btnRunX, btnRunY, btnRunW, 16, 0xFF059669);
                    g.centeredText(font, "§f⚡ Run", btnRunX + btnRunW / 2, btnRunY + 4, 0xFFFFFFFF);

                    int btnPasteW = 58;
                    int btnPasteX = btnRunX - btnPasteW - 6;
                    boolean pasteHov = mouseX >= btnPasteX && mouseX <= btnPasteX + btnPasteW && mouseY >= btnRunY && mouseY <= btnRunY + 16 && mouseY >= subListTop && mouseY <= subListBottom;

                    g.fill(btnPasteX, btnRunY, btnPasteX + btnPasteW, btnRunY + 16, pasteHov ? 0xFF0284C7 : 0xFF0C4A6E);
                    g.outline(btnPasteX, btnRunY, btnPasteW, 16, 0xFF0284C7);
                    g.centeredText(font, "§f📋 Paste", btnPasteX + btnPasteW / 2, btnRunY + 4, 0xFFFFFFFF);
                }
                optStartY += optH + 4;
            }
            g.disableScissor();
        }
    }

    private void renderFeaturesTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<FeatureDoc> filtered = getFilteredFeatures();

        int cardH = 46;
        int totalH = filtered.size() * (cardH + 4);
        int maxFeatScroll = Math.max(0, totalH - (h - 8));
        featureListScroll = Math.max(0, Math.min(maxFeatScroll, featureListScroll));

        int curY = y + 4 - featureListScroll;

        g.enableScissor(x, y + 2, x + w, y + h - 2);
        for (FeatureDoc feat : filtered) {
            if (curY + cardH > y - 10 && curY < y + h + 10) {
                boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + cardH && mouseY >= y && mouseY <= y + h;

                g.fill(x, curY, x + w, curY + cardH, hov ? 0x2238BDF8 : 0x111E293B);
                g.outline(x, curY, w, cardH, hov ? 0x6638BDF8 : 0x22475569);

                // Feature Name & Category
                g.text(font, "§f§l" + feat.name + " §8| §3" + feat.category, x + 12, curY + 6, 0xFFFFFFFF, false);

                // Description trimmed so it never touches the badge or configure button
                int maxDescW = w - 210;
                String desc = font.plainSubstrByWidth(feat.description, maxDescW);
                g.text(font, "§7" + desc, x + 12, curY + 20, 0xFF94A3B8, false);

                // Status Badge
                String st = feat.statusSupplier.get();
                int stW = font.width(st) + 12;
                int stX = x + w - stW - 96;
                int stY = curY + 6;
                g.fill(stX, stY, stX + stW, stY + 16, 0x33000000);
                g.outline(stX, stY, stW, 16, 0x44FFFFFF);
                g.centeredText(font, st, stX + stW / 2, stY + 4, 0xFFFFFFFF);

                // Configure / Toggle Button
                int btnW = 80;
                int btnX = x + w - btnW - 10;
                int btnY = curY + 6;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16 && mouseY >= y && mouseY <= y + h;

                g.fill(btnX, btnY, btnX + btnW, btnY + 16, btnHov ? 0xFF0284C7 : 0xFF0F172A);
                g.outline(btnX, btnY, btnW, 16, 0xFF38BDF8);
                g.centeredText(font, "§f⚙ Configure", btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);
            }
            curY += cardH + 4;
        }
        g.disableScissor();
    }

    private List<CommandDoc> getFilteredCommands() {
        if (searchQuery.isEmpty()) return allCommands;
        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<CommandDoc> res = new ArrayList<>();
        for (CommandDoc c : allCommands) {
            if (c.name.toLowerCase(Locale.ROOT).contains(q)
                    || c.category.toLowerCase(Locale.ROOT).contains(q)
                    || c.summary.toLowerCase(Locale.ROOT).contains(q)) {
                res.add(c);
            } else {
                for (SubOption sub : c.subOptions) {
                    if (sub.syntax.toLowerCase(Locale.ROOT).contains(q) || sub.description.toLowerCase(Locale.ROOT).contains(q)) {
                        res.add(c);
                        break;
                    }
                }
            }
        }
        return res;
    }

    private List<FeatureDoc> getFilteredFeatures() {
        if (searchQuery.isEmpty()) return allFeatures;
        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<FeatureDoc> res = new ArrayList<>();
        for (FeatureDoc f : allFeatures) {
            if (f.name.toLowerCase(Locale.ROOT).contains(q)
                    || f.category.toLowerCase(Locale.ROOT).contains(q)
                    || f.description.toLowerCase(Locale.ROOT).contains(q)) {
                res.add(f);
            }
        }
        return res;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = Math.min(840, this.width - 32);
        int winH = Math.min(520, this.height - 24);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Close button
        int closeX = winX + winW - 28;
        int closeY = winY + 10;
        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 16) {
            this.onClose();
            return true;
        }

        // Tab selection
        int tabY = winY + 42;
        int tabX = winX + 16;
        for (Tab t : Tab.values()) {
            int tabW = font.width(t.title) + 24;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= tabY && mouseY <= tabY + 20) {
                this.activeTab = t;
                this.selectedCommandIndex = 0;
                return true;
            }
            tabX += tabW + 8;
        }

        // Search Bar Focus
        int searchW = 190;
        int searchX = winX + winW - searchW - 16;
        int searchY = tabY;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + 20) {
            isSearchFocused = true;
            return true;
        } else {
            isSearchFocused = false;
        }

        // Tab content clicks
        int contentY = winY + 68;
        int contentH = winH - 76;

        if (this.activeTab == Tab.COMMANDS) {
            List<CommandDoc> filtered = getFilteredCommands();
            int leftW = 210;
            int rightW = winW - 32 - leftW - 12;

            // Left Panel Click (Command Selection)
            int itemH = 34;
            int startIdx = commandListScroll;
            int curY = contentY + 4;

            for (int i = startIdx; i < filtered.size() && (curY + itemH) <= contentY + contentH; i++) {
                if (mouseX >= winX + 16 + 4 && mouseX <= winX + 16 + leftW - 4 && mouseY >= curY && mouseY <= curY + itemH - 2) {
                    selectedCommandIndex = i;
                    commandDetailScroll = 0;
                    return true;
                }
                curY += itemH;
            }

            // Right Panel SubOption Action Buttons
            if (!filtered.isEmpty() && selectedCommandIndex >= 0 && selectedCommandIndex < filtered.size()) {
                CommandDoc sel = filtered.get(selectedCommandIndex);
                int rightX = winX + 16 + leftW + 12;
                int optH = 46;

                // Estimate header height dynamically
                List<FormattedCharSequence> descLines = font.split(Component.literal("§7" + sel.detailedDescription), rightW - 24);
                int subHeaderY = contentY + 10 + 14 + (descLines.size() * 10) + 6 + 8;
                int subListTop = subHeaderY + 16;
                int subListBottom = contentY + contentH - 6;

                int optStartY = subListTop - commandDetailScroll;

                for (SubOption opt : sel.subOptions) {
                    if (optStartY + optH > subListTop && optStartY < subListBottom) {
                        int btnRunW = 54;
                        int btnRunX = rightX + rightW - 12 - btnRunW - 8;
                        int btnRunY = optStartY + 6;

                        int btnPasteW = 58;
                        int btnPasteX = btnRunX - btnPasteW - 6;

                        // [ ⚡ Run ]
                        if (mouseX >= btnRunX && mouseX <= btnRunX + btnRunW && mouseY >= btnRunY && mouseY <= btnRunY + 16) {
                            executeCommand(opt.commandToRun);
                            return true;
                        }

                        // [ 📋 Paste ]
                        if (mouseX >= btnPasteX && mouseX <= btnPasteX + btnPasteW && mouseY >= btnRunY && mouseY <= btnRunY + 16) {
                            suggestCommand(opt.commandToRun);
                            return true;
                        }
                    }
                    optStartY += optH + 4;
                }
            }
        } else {
            // Features tab click
            List<FeatureDoc> filtered = getFilteredFeatures();
            int cardH = 46;
            int curY = contentY + 4 - featureListScroll;

            for (FeatureDoc feat : filtered) {
                if (curY + cardH > contentY && curY < contentY + contentH) {
                    int btnW = 80;
                    int btnX = winX + 16 + (winW - 32) - btnW - 10;
                    int btnY = curY + 6;

                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                        feat.configureAction.run();
                        return true;
                    }
                }
                curY += cardH + 4;
            }
        }

        return super.mouseClicked(event, handled);
    }

    private void executeCommand(String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        this.onClose();
        mc.execute(() -> {
            if (mc.player != null) {
                String clean = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                mc.player.connection.sendCommand(clean);
            }
        });
    }

    private void suggestCommand(String cmd) {
        if (cmd == null || cmd.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        this.onClose();
        mc.execute(() -> {
            mc.setScreenAndShow(new net.minecraft.client.gui.screens.ChatScreen(cmd.startsWith("/") ? cmd : "/" + cmd, false));
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            int winW = Math.min(840, this.width - 32);
            int winH = Math.min(520, this.height - 24);
            int contentH = winH - 76;

            if (this.activeTab == Tab.COMMANDS) {
                if (mouseX < this.width / 2.0) {
                    List<CommandDoc> filtered = getFilteredCommands();
                    int itemH = 34;
                    int maxVisible = Math.max(1, (contentH - 8) / itemH);
                    int maxScroll = Math.max(0, filtered.size() - maxVisible);
                    commandListScroll = Math.max(0, Math.min(maxScroll, commandListScroll - (int) verticalAmount));
                } else {
                    List<CommandDoc> filtered = getFilteredCommands();
                    if (selectedCommandIndex >= 0 && selectedCommandIndex < filtered.size()) {
                        CommandDoc sel = filtered.get(selectedCommandIndex);
                        int optH = 46;
                        int totalOptH = sel.subOptions.size() * (optH + 4);
                        int subListHeight = Math.max(1, contentH - 80);
                        int maxDetailScroll = Math.max(0, totalOptH - subListHeight);
                        commandDetailScroll = Math.max(0, Math.min(maxDetailScroll, commandDetailScroll - (int) (verticalAmount * 20)));
                    }
                }
            } else {
                List<FeatureDoc> filtered = getFilteredFeatures();
                int cardH = 46;
                int totalH = filtered.size() * (cardH + 4);
                int maxFeatScroll = Math.max(0, totalH - (contentH - 8));
                featureListScroll = Math.max(0, Math.min(maxFeatScroll, featureListScroll - (int) (verticalAmount * 25)));
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (isSearchFocused) {
            searchQuery += (char) event.codepoint();
            selectedCommandIndex = 0;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (isSearchFocused) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                selectedCommandIndex = 0;
                return true;
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                isSearchFocused = false;
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
