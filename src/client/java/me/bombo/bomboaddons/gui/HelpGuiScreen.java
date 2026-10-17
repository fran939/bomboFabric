package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.shards.ShardsOptimizerScreen;
import me.bombo.bomboaddons.gui.config.BomboConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        // COMMANDS CATALOG
        // -------------------------------------------------------------
        allCommands.add(new CommandDoc("/ac", "Dungeons", "Auto Croesus chest selector & claimer.",
                "Automates interaction with the Dungeon Croesus chest menu, calculating profits and auto-claiming optimal reward chests.",
                List.of(
                        new SubOption("/ac", "Toggles Auto Croesus feature on or off.", "/ac"),
                        new SubOption("/ac accept", "Confirms and executes purchase for the currently highlighted Croesus chest.", "/ac accept"),
                        new SubOption("/ac clear", "Clears current Croesus chest selection queue and ignores remaining chests.", "/ac clear"),
                        new SubOption("/ac list", "Lists all pending unopened Croesus chests with estimated profit values.", "/ac list"),
                        new SubOption("/ac auto", "Toggles fully automatic 1-click chest buying mode.", "/ac auto"),
                        new SubOption("/ac chest <name>", "Forces target chest selection by floor name (e.g. /ac chest M7).", "/ac chest ")
                )));

        allCommands.add(new CommandDoc("/b", "General", "Main BomboAddons configuration & hub.",
                "Opens the primary Liquid Glass configuration menu or directly configures mod modules.",
                List.of(
                        new SubOption("/b", "Opens the modern Liquid Glass configuration GUI.", "/b"),
                        new SubOption("/b config", "Opens the modern config screen directly.", "/b config"),
                        new SubOption("/b help gui", "Opens this interactive Help & Feature Explorer GUI.", "/b help gui"),
                        new SubOption("/b old", "Opens the legacy fallback configuration GUI.", "/b old"),
                        new SubOption("/b order", "Opens the category and feature order manager.", "/b order"),
                        new SubOption("/b gui", "Opens the HUD Editor to reposition overlays.", "/b gui")
                )));

        allCommands.add(new CommandDoc("/mp", "Progression", "Magical Power & Accessories Optimizer.",
                "Calculates optimal accessory upgrades by coins/MP, provides authentic 54-slot chest crafting modals, Wikipedia lookups, and dedicated Enrichments auditor.",
                List.of(
                        new SubOption("/mp", "Opens the Magical Power & Accessory optimizer for your active profile.", "/mp"),
                        new SubOption("/mp <player>", "Inspects and optimizes Magical Power for any target player IGN.", "/mp "),
                        new SubOption("/mp talisman", "Opens the dedicated multi-tier accessory progression tree.", "/mp talisman"),
                        new SubOption("/mp enrichments", "Audits accessory enrichments, duplicate hatcessories, and mismatches.", "/mp enrichments"),
                        new SubOption("/b mp", "Alias for /mp.", "/b mp")
                )));

        allCommands.add(new CommandDoc("/shards", "Progression", "Attribute Shards Sweet-Spot Optimizer.",
                "Scrapes all 325 attribute shards, calculating optimal fusion pathways, budget sweet-spots, and damage per coin with interactive 3x3 crafting recipes.",
                List.of(
                        new SubOption("/shards", "Opens the Liquid Glass Shards Optimizer GUI.", "/shards"),
                        new SubOption("/shards <class>", "Opens shards optimizer pre-filtered to a specific Dungeon class (e.g. /shards archer).", "/shards archer"),
                        new SubOption("/shards budget <coins>", "Calculates optimal attribute loadout for a target budget (e.g. 50M).", "/shards budget 50M"),
                        new SubOption("/b shards", "Alias for /shards.", "/b shards")
                )));

        allCommands.add(new CommandDoc("/b perf", "Performance", "Zero-overhead performance profiler & tick monitor.",
                "Inspects tick latencies, CPU profiling tables, thread activity, and confirms zero overhead when features are toggled off.",
                List.of(
                        new SubOption("/b perf", "Opens the high-resolution profiler tables screen.", "/b perf"),
                        new SubOption("/b perf reset", "Resets profiling counters and latency history.", "/b perf reset"),
                        new SubOption("/b perf hud", "Toggles the compact in-game performance HUD overlay.", "/b perf hud"),
                        new SubOption("/perf", "Alias for /b perf.", "/perf")
                )));

        allCommands.add(new CommandDoc("/b prof", "Profiles", "Multi-profile configuration manager.",
                "Manages distinct mod profiles for different classes or activities (e.g. Dungeons, Mining, Farming). Allows persistent deletion and creation.",
                List.of(
                        new SubOption("/b prof list", "Lists all active and configured profiles with interactive click-to-switch and delete buttons.", "/b prof list"),
                        new SubOption("/b prof <name>", "Switches to or creates a profile with the specified name.", "/b prof "),
                        new SubOption("/b prof delete <name>", "Permanently deletes a profile across all keybinds, waypoints, and configs.", "/b prof delete ")
                )));

        allCommands.add(new CommandDoc("/b hotbar", "Inventory", "Inventory & Hotbar slot swapper.",
                "Opens the visual slot swapper screen to configure item swaps between inventory and hotbar slots.",
                List.of(
                        new SubOption("/b hotbar", "Opens the visual inventory slot swapper screen.", "/b hotbar"),
                        new SubOption("/b swap", "Alias for /b hotbar.", "/b swap"),
                        new SubOption("/hotbar", "Standalone shortcut for /b hotbar.", "/hotbar"),
                        new SubOption("/swap", "Standalone shortcut for /b swap.", "/swap")
                )));

        allCommands.add(new CommandDoc("/b buttons", "Inventory", "Custom container click buttons.",
                "Configures custom clickable macro buttons inside container inventories for quick commands or actions.",
                List.of(
                        new SubOption("/b buttons", "Opens the custom inventory buttons editor.", "/b buttons"),
                        new SubOption("/b buttons move", "Opens the interactive screen to reposition inventory buttons.", "/b buttons move"),
                        new SubOption("/b buttons toggle", "Toggles inventory buttons on or off.", "/b buttons toggle"),
                        new SubOption("/buttons", "Standalone shortcut for /b buttons.", "/buttons")
                )));

        allCommands.add(new CommandDoc("/b chathistory", "Chat & Logs", "Locraw & Chat History tracker.",
                "Searches past chat messages, system notices, and Hypixel /locraw responses with provenance metadata.",
                List.of(
                        new SubOption("/b chathistory", "Opens the searchable Chat History GUI.", "/b chathistory"),
                        new SubOption("/b chathistory locraw", "Filters chat history specifically to Hypixel /locraw server locations.", "/b chathistory locraw"),
                        new SubOption("/b chathistory search <query>", "Searches historical chat logs matching query.", "/b chathistory search ")
                )));

        allCommands.add(new CommandDoc("/b pv", "Profiles", "Authentic SkyBlock Profile Viewer.",
                "Inspects any player's full profile including 18 wardrobe sets, armor silhouettes, dynamic pet lores, museum, motes, and dungeon PBs.",
                List.of(
                        new SubOption("/b pv", "Opens profile viewer for your own active profile.", "/b pv"),
                        new SubOption("/b pv <player>", "Fetches and displays full profile stats for target player IGN.", "/b pv "),
                        new SubOption("/pv <player>", "Standalone shortcut for profile viewer.", "/pv ")
                )));

        allCommands.add(new CommandDoc("/b auto", "Macros", "Visual Sequence & Macro Studio.",
                "Creates, edits, and executes automated key, movement, and command sequences with visual timeline controls.",
                List.of(
                        new SubOption("/b auto", "Opens the Visual Sequence & Macro Studio screen.", "/b auto"),
                        new SubOption("/b auto start <name>", "Starts executing the specified automation sequence.", "/b auto start "),
                        new SubOption("/b auto stop", "Immediately stops all currently running automation sequences.", "/b auto stop"),
                        new SubOption("/b sequence", "Alias for /b auto.", "/b sequence"),
                        new SubOption("/b studio", "Opens the automation sequence builder.", "/b studio")
                )));

        allCommands.add(new CommandDoc("/b wpo", "Waypoints", "Ordered Waypoints Manager.",
                "Manages ordered path waypoints with clipboard import, visual lines, and auto-progression.",
                List.of(
                        new SubOption("/b wpo", "Opens ordered waypoints configuration.", "/b wpo"),
                        new SubOption("/b wpo import", "Imports ordered waypoints directly from clipboard.", "/b wpo import"),
                        new SubOption("/b wpo clear", "Clears all currently loaded ordered waypoints.", "/b wpo clear")
                )));

        allCommands.add(new CommandDoc("/b highlight", "Visuals", "Persistent Entity Highlights & ESP.",
                "Highlights specific mobs, bosses, or entities with customizable glowing outline colors and bounding boxes.",
                List.of(
                        new SubOption("/b highlight", "Opens persistent entity highlights manager.", "/b highlight"),
                        new SubOption("/b highlight add <mob>", "Adds a mob name or regex pattern to the persistent highlight list.", "/b highlight add "),
                        new SubOption("/b highlight remove <mob>", "Removes a mob from the highlight list.", "/b highlight remove "),
                        new SubOption("/b highlight list", "Prints all actively highlighted entity patterns in chat.", "/b highlight list")
                )));

        allCommands.add(new CommandDoc("/b update", "Updater", "Mod version manager & auto-updater.",
                "Checks API release endpoints for latest full and beta releases, with 1-click in-game downloading.",
                List.of(
                        new SubOption("/b update", "Opens the update checker and version selector GUI.", "/b update"),
                        new SubOption("/b update check", "Checks for available updates via the remote API.", "/b update check"),
                        new SubOption("/b update channel full", "Sets update channel to Full releases only.", "/b update channel full"),
                        new SubOption("/b update channel beta", "Sets update channel to include Beta subversions.", "/b update channel beta")
                )));

        allCommands.add(new CommandDoc("/b cmd", "Terminal", "In-game Bombo Terminal.",
                "Embedded terminal screen with instant command execution, streaming stdout/stderr, and session history.",
                List.of(
                        new SubOption("/b cmd", "Opens the in-game terminal window.", "/b cmd"),
                        new SubOption("/b cmd <command>", "Opens terminal and immediately executes the specified command.", "/b cmd ping 1.1.1.1"),
                        new SubOption("/b cmd clear", "Clears the active terminal output buffer.", "/b cmd clear")
                )));

        allCommands.add(new CommandDoc("/stoptps", "Dungeons", "Dungeon TPS & Tick Lag Alert.",
                "Monitors server tick rates and warns or pauses sequences when server TPS drops below thresholds.",
                List.of(
                        new SubOption("/stoptps", "Toggles TPS lag protection monitoring.", "/stoptps"),
                        new SubOption("/cbtps", "Croesus TPS check and lag safety validator.", "/cbtps")
                )));

        allCommands.add(new CommandDoc("/clean", "Utility", "Temporary entity & particle cleaner.",
                "Cleans up client-side render lag by purging non-critical particles, ground items, or entity clutter.",
                List.of(
                        new SubOption("/clean", "Executes immediate temporary client render cleanup.", "/clean")
                )));

        // -------------------------------------------------------------
        // FEATURES CATALOG
        // -------------------------------------------------------------
        BomboConfig.Settings s = BomboConfig.get();

        allFeatures.add(new FeatureDoc("Disable Blindness & Darkness", "System",
                "Completely extinguishes Blindness and Warden Darkness fog, effect darkening, and blindness overlay shaders for maximum visual clarity.",
                () -> s.disableBlindness ? "§aENABLED" : "§cDISABLED",
                () -> { s.disableBlindness = !s.disableBlindness; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("Left-Click Etherwarp (LCEW)", "Combat",
                "Enables single-click etherwarp teleports on left click with held AOTV/AOTE. Automatically manages crouch packets without double-firing or state locks.",
                () -> s.leftClickEtherwarp ? "§aENABLED" : "§cDISABLED",
                () -> { s.leftClickEtherwarp = !s.leftClickEtherwarp; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("Fast AOTV & Fast Use", "Combat",
                "Removes right-click delay on Aspect of the Void/End and weapons, allowing smooth high-speed teleportation and ability usage.",
                () -> s.fastAotv ? "§aENABLED (Spd " + s.fastAotvSpeed + ")" : "§cDISABLED",
                () -> { s.fastAotv = !s.fastAotv; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("Auto Croesus", "Dungeons",
                "Scans Croesus chest menu, automatically selects chests with positive net profit, and supports 1-click confirmation.",
                () -> s.autoCroesus ? "§aENABLED" : "§cDISABLED",
                () -> { s.autoCroesus = !s.autoCroesus; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("Dungeon Profit Tracker", "Dungeons",
                "Calculates chest coin value and net profit against key costs using live Bazaar/lowest BIN market prices.",
                () -> s.croesusProfitTracker ? "§aENABLED" : "§cDISABLED",
                () -> { s.croesusProfitTracker = !s.croesusProfitTracker; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("Magical Power & Accessories Engine", "Progression",
                "Full SkyBlock accessory engine computing cheapest upgrades per coin, wiki URLs, 54-slot recipe modals, and enrichments auditing.",
                () -> "§aREADY",
                () -> Minecraft.getInstance().setScreenAndShow(new MagicalPowerScreen(this, ""))));

        allFeatures.add(new FeatureDoc("Attribute Shards Sweet-Spot", "Progression",
                "Scrapes 325 attribute shards, finding optimal budget tiers, class synergies, and coin savings.",
                () -> "§aREADY",
                () -> Minecraft.getInstance().setScreenAndShow(new ShardsOptimizerScreen())));

        allFeatures.add(new FeatureDoc("Garden Movement Keybinds", "Garden",
                "Configures custom keybinds for farming lanes, speed lock, plot navigation, and macro protection.",
                () -> s.gardenBindsEverywhere ? "§aEVERYWHERE" : (s.gardenMovement ? "§aGARDEN ONLY" : "§cDISABLED"),
                () -> { s.gardenMovement = !s.gardenMovement; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("Zero-Overhead Profiler", "Performance",
                "Real-time microsecond performance profiling tables. Enforces 100% idle state with zero hooks when features are off.",
                () -> "§aREADY",
                () -> Minecraft.getInstance().setScreenAndShow(new PerformanceScreen(this))));

        allFeatures.add(new FeatureDoc("Discord Rich Presence & Chat HUD", "HUD",
                "Rich Discord RPC integration showing current SkyBlock area, floor, and in-game chat bridge. Halts all sockets when disabled.",
                () -> s.discordHudEnabled ? "§aENABLED" : "§cDISABLED",
                () -> { s.discordHudEnabled = !s.discordHudEnabled; BomboConfig.save(); }));

        allFeatures.add(new FeatureDoc("HUD Overlays Editor", "HUD",
                "Visual drag-and-drop editor to reposition and scale all in-game HUDs (Profit, TPS, Kuudra, Speed, Coords, etc.).",
                () -> "§aREADY",
                () -> Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.HudMoveScreen())));

        allFeatures.add(new FeatureDoc("Multi-Profile Keybind Swapper", "Profiles",
                "Configures separate profiles with custom keybinds, command binds, and coordinate waypoints per activity.",
                () -> "§e" + (s.activeProfile != null ? s.activeProfile : "default"),
                () -> Minecraft.getInstance().setScreenAndShow(new BomboConfigScreen(this))));
    }

    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
        // Dark translucent background
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int winW = Math.min(760, this.width - 40);
        int winH = Math.min(480, this.height - 30);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int bg = 0xFA090D16;
        int border = 0x6038BDF8;
        int accent = 0xFF38BDF8;

        // Base Glass Card
        g.fill(winX, winY, winX + winW, winY + winH, bg);
        g.outline(winX, winY, winW, winH, border);
        g.fill(winX, winY, winX + winW, winY + 3, accent);

        Font font = Minecraft.getInstance().font;

        // Title and Header
        g.text(font, "§b§lBOMBOADDONS §8| §fHelp & Feature Explorer", winX + 16, winY + 12, 0xFFFFFFFF, true);
        g.text(font, "§7Explore commands, sub-options with 1-click execution, and cataloged features.", winX + 16, winY + 25, 0xFFA0AEC0, false);

        // Top right close button [✕]
        int closeX = winX + winW - 28;
        int closeY = winY + 10;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 16;
        g.fill(closeX, closeY, closeX + 18, closeY + 16, closeHov ? 0xFFEF4444 : 0xFF334155);
        g.outline(closeX, closeY, 18, 16, 0xFF64748B);
        g.centeredText(font, "✕", closeX + 9, closeY + 4, 0xFFFFFFFF);

        // Tab Switcher & Search Bar (Row 2)
        int tabY = winY + 42;
        int tabX = winX + 16;

        for (Tab tab : Tab.values()) {
            boolean isSelected = this.activeTab == tab;
            int tw = font.width(tab.title) + 20;
            boolean hov = mouseX >= tabX && mouseX <= tabX + tw && mouseY >= tabY && mouseY <= tabY + 18;

            int tabBg = isSelected ? 0xFF0284C7 : (hov ? 0x4438BDF8 : 0x221E293B);
            int tabBorder = isSelected ? 0xFF38BDF8 : (hov ? 0x8838BDF8 : 0x3364748B);

            g.fill(tabX, tabY, tabX + tw, tabY + 18, tabBg);
            g.outline(tabX, tabY, tw, 18, tabBorder);
            g.centeredText(font, (isSelected ? "§f§l" : "§7") + tab.title, tabX + tw / 2, tabY + 5, 0xFFFFFFFF);

            tabX += tw + 8;
        }

        // Search Input Box (Right side of tab bar)
        int searchW = 160;
        int searchX = winX + winW - searchW - 16;
        int searchY = tabY;
        int searchH = 18;
        boolean searchHov = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH;

        g.fill(searchX, searchY, searchX + searchW, searchY + searchH, isSearchFocused ? 0xEE1E293B : 0xEE0F172A);
        g.outline(searchX, searchY, searchW, searchH, isSearchFocused ? 0xFF38BDF8 : (searchHov ? 0x8838BDF8 : 0x4464748B));

        String searchDisplay = searchQuery.isEmpty() ? (isSearchFocused ? "" : "§8Search commands / features...") : searchQuery;
        g.text(font, "🔍 " + searchDisplay, searchX + 6, searchY + 5, searchQuery.isEmpty() ? 0xFF64748B : 0xFFFFFFFF, false);

        // Divider
        int contentY = winY + 68;
        g.fill(winX + 16, contentY, winX + winW - 16, contentY + 1, 0x3364748B);
        contentY += 8;

        int contentH = winY + winH - contentY - 12;

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

        int leftW = 220;
        int rightW = w - leftW - 12;

        // Left Panel: Command List
        g.fill(x, y, x + leftW, y + h, 0x660F172A);
        g.outline(x, y, leftW, h, 0x3364748B);

        int itemH = 34;
        int maxVisible = h / itemH;
        int startIdx = Math.max(0, Math.min(commandListScroll, Math.max(0, filtered.size() - maxVisible)));

        int curY = y + 4;
        for (int i = startIdx; i < filtered.size() && (curY + itemH) <= y + h - 4; i++) {
            CommandDoc cmd = filtered.get(i);
            boolean isSel = i == selectedCommandIndex;
            boolean hov = mouseX >= x + 4 && mouseX <= x + leftW - 4 && mouseY >= curY && mouseY <= curY + itemH - 2;

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

        // Right Panel: Selected Command Details
        int rightX = x + leftW + 12;
        g.fill(rightX, y, rightX + rightW, y + h, 0x660F172A);
        g.outline(rightX, y, rightW, h, 0x3364748B);

        if (!filtered.isEmpty() && selectedCommandIndex >= 0 && selectedCommandIndex < filtered.size()) {
            CommandDoc sel = filtered.get(selectedCommandIndex);

            int detailY = y + 10;
            g.text(font, "§e§l" + sel.name + " §8| §3" + sel.category, rightX + 12, detailY, 0xFFFFFFFF, true);
            detailY += 14;

            g.text(font, "§7" + sel.detailedDescription, rightX + 12, detailY, 0xFFCBD5E1, false);
            detailY += 20;

            g.fill(rightX + 12, detailY, rightX + rightW - 12, detailY + 1, 0x3364748B);
            detailY += 8;

            g.text(font, "§6§lSub-Commands & Options (" + sel.subOptions.size() + ")", rightX + 12, detailY, 0xFFFFFFFF, true);
            detailY += 16;

            int optH = 46;
            int optStartY = detailY - commandDetailScroll;

            for (SubOption opt : sel.subOptions) {
                if (optStartY + optH > y + 50 && optStartY < y + h - 6) {
                    boolean optHov = mouseX >= rightX + 12 && mouseX <= rightX + rightW - 12 && mouseY >= optStartY && mouseY <= optStartY + optH;

                    g.fill(rightX + 12, optStartY, rightX + rightW - 12, optStartY + optH, optHov ? 0x2238BDF8 : 0x111E293B);
                    g.outline(rightX + 12, optStartY, rightW - 24, optH, optHov ? 0x6638BDF8 : 0x22475569);

                    g.text(font, "§b" + opt.syntax, rightX + 18, optStartY + 6, 0xFFFFFFFF, false);
                    g.text(font, "§7" + opt.description, rightX + 18, optStartY + 20, 0xFF94A3B8, false);

                    // Action buttons: [ ⚡ Run ] and [ 📋 Paste ]
                    int btnRunW = 54;
                    int btnRunX = rightX + rightW - 12 - btnRunW - 8;
                    int btnRunY = optStartY + 6;
                    boolean runHov = mouseX >= btnRunX && mouseX <= btnRunX + btnRunW && mouseY >= btnRunY && mouseY <= btnRunY + 16;

                    g.fill(btnRunX, btnRunY, btnRunX + btnRunW, btnRunY + 16, runHov ? 0xFF10B981 : 0xFF064E3B);
                    g.outline(btnRunX, btnRunY, btnRunW, 16, 0xFF059669);
                    g.centeredText(font, "§f⚡ Run", btnRunX + btnRunW / 2, btnRunY + 4, 0xFFFFFFFF);

                    int btnPasteW = 58;
                    int btnPasteX = btnRunX - btnPasteW - 6;
                    boolean pasteHov = mouseX >= btnPasteX && mouseX <= btnPasteX + btnPasteW && mouseY >= btnRunY && mouseY <= btnRunY + 16;

                    g.fill(btnPasteX, btnRunY, btnPasteX + btnPasteW, btnRunY + 16, pasteHov ? 0xFF0284C7 : 0xFF0C4A6E);
                    g.outline(btnPasteX, btnRunY, btnPasteW, 16, 0xFF0284C7);
                    g.centeredText(font, "§f📋 Paste", btnPasteX + btnPasteW / 2, btnRunY + 4, 0xFFFFFFFF);
                }
                optStartY += optH + 4;
            }
        }
    }

    private void renderFeaturesTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<FeatureDoc> filtered = getFilteredFeatures();

        int cardH = 50;
        int curY = y + 4 - featureListScroll;

        for (FeatureDoc feat : filtered) {
            if (curY + cardH > y && curY < y + h) {
                boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + cardH;

                g.fill(x, curY, x + w, curY + cardH, hov ? 0x2238BDF8 : 0x111E293B);
                g.outline(x, curY, w, cardH, hov ? 0x6638BDF8 : 0x22475569);

                // Feature Name & Category
                g.text(font, "§f§l" + feat.name + " §8| §3" + feat.category, x + 12, curY + 6, 0xFFFFFFFF, false);

                // Description
                String desc = font.plainSubstrByWidth(feat.description, w - 190);
                g.text(font, "§7" + desc, x + 12, curY + 22, 0xFF94A3B8, false);

                // Status Badge
                String st = feat.statusSupplier.get();
                int stW = font.width(st) + 12;
                int stX = x + w - stW - 100;
                int stY = curY + 8;
                g.fill(stX, stY, stX + stW, stY + 16, 0x33000000);
                g.outline(stX, stY, stW, 16, 0x44FFFFFF);
                g.centeredText(font, st, stX + stW / 2, stY + 4, 0xFFFFFFFF);

                // Configure / Toggle Button
                int btnW = 80;
                int btnX = x + w - btnW - 12;
                int btnY = curY + 8;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16;

                g.fill(btnX, btnY, btnX + btnW, btnY + 16, btnHov ? 0xFF0284C7 : 0xFF0F172A);
                g.outline(btnX, btnY, btnW, 16, 0xFF38BDF8);
                g.centeredText(font, "§f⚙ Configure", btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);
            }
            curY += cardH + 4;
        }
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

        int winW = Math.min(760, this.width - 40);
        int winH = Math.min(480, this.height - 30);
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
        Font font = Minecraft.getInstance().font;

        for (Tab tab : Tab.values()) {
            int tw = font.width(tab.title) + 20;
            if (mouseX >= tabX && mouseX <= tabX + tw && mouseY >= tabY && mouseY <= tabY + 18) {
                this.activeTab = tab;
                this.commandDetailScroll = 0;
                this.featureListScroll = 0;
                return true;
            }
            tabX += tw + 8;
        }

        // Search Focus
        int searchW = 160;
        int searchX = winX + winW - searchW - 16;
        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= tabY && mouseY <= tabY + 18) {
            this.isSearchFocused = true;
            return true;
        } else {
            this.isSearchFocused = false;
        }

        int contentY = winY + 76;
        int contentH = winY + winH - contentY - 12;

        if (this.activeTab == Tab.COMMANDS) {
            List<CommandDoc> filtered = getFilteredCommands();
            int leftW = 220;
            int rightW = winW - 32 - leftW - 12;

            // Click command in list
            int itemH = 34;
            int maxVisible = contentH / itemH;
            int startIdx = Math.max(0, Math.min(commandListScroll, Math.max(0, filtered.size() - maxVisible)));

            int curY = contentY + 4;
            for (int i = startIdx; i < filtered.size() && (curY + itemH) <= contentY + contentH - 4; i++) {
                if (mouseX >= winX + 16 + 4 && mouseX <= winX + 16 + leftW - 4 && mouseY >= curY && mouseY <= curY + itemH - 2) {
                    this.selectedCommandIndex = i;
                    this.commandDetailScroll = 0;
                    return true;
                }
                curY += itemH;
            }

            // Click sub-options in right detail panel
            if (!filtered.isEmpty() && selectedCommandIndex >= 0 && selectedCommandIndex < filtered.size()) {
                CommandDoc sel = filtered.get(selectedCommandIndex);
                int rightX = winX + 16 + leftW + 12;
                int optH = 46;
                int optStartY = contentY + 68 - commandDetailScroll;

                for (SubOption opt : sel.subOptions) {
                    if (optStartY + optH > contentY + 50 && optStartY < contentY + contentH - 6) {
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
            int cardH = 50;
            int curY = contentY + 4 - featureListScroll;

            for (FeatureDoc feat : filtered) {
                if (curY + cardH > contentY && curY < contentY + contentH) {
                    int btnW = 80;
                    int btnX = winX + 16 + (winW - 32) - btnW - 12;
                    int btnY = curY + 8;

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
            if (this.activeTab == Tab.COMMANDS) {
                if (mouseX < this.width / 2.0) {
                    commandListScroll = Math.max(0, commandListScroll - (int) verticalAmount);
                } else {
                    commandDetailScroll = Math.max(0, commandDetailScroll - (int) (verticalAmount * 20));
                }
            } else {
                featureListScroll = Math.max(0, featureListScroll - (int) (verticalAmount * 25));
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
