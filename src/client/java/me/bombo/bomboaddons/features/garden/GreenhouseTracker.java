package me.bombo.bomboaddons.features.garden;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

public class GreenhouseTracker {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SESSIONS_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/greenhouse_sessions.json").toFile();

    private static final Pattern RARE_CROP_PATTERN = Pattern.compile("(?i)RARE CROP!\\s+([A-Za-z\\s]+?)\\s*\\(\\+([\\d,]+)[^\\)]*\\)");
    private static final Pattern ITEM_LINE_ADD = Pattern.compile("(?i)^\\+([\\d,]+)x?\\s+(.+?)(?:\\s*\\([^\\)]*\\))?$");
    private static final Pattern ITEM_LINE_SUB = Pattern.compile("(?i)^-([\\d,]+)x?\\s+(.+?)(?:\\s*\\([^\\)]*\\))?$");
    private static final Pattern LAST_SECONDS_PATTERN = Pattern.compile("(?i)Last\\s+(\\d+)s");

    private static final ThreadLocal<Boolean> IS_PROCESSING_MSG = ThreadLocal.withInitial(() -> false);

    public static class Session {
        public String name;
        public long createdTime;
        public Map<String, Long> items = new LinkedHashMap<>();

        public Session() {
            this.name = "Session 1";
            this.createdTime = System.currentTimeMillis();
        }

        public Session(String name) {
            this.name = name;
            this.createdTime = System.currentTimeMillis();
        }
    }

    public static final List<Session> sessions = new ArrayList<>();
    public static int activeSessionIndex = 0;

    public static long lastGreenhouseFarmTime = 0L;
    private static boolean warnedUnconfigured = false;
    private static long lastWarnTime = 0L;
    private static boolean isSyncingFromApi = false;

    private static int lastScannedScreenHashCode = 0;
    private static boolean scannedCurrentScreen = false;

    static {
        loadSessions();
    }

    public static void init() {
        // ChatMixin.java delegates chat messages directly to onChatMessage(message)
    }

    public static Session getActiveSession() {
        if (sessions.isEmpty()) {
            sessions.add(new Session("Session 1"));
            activeSessionIndex = 0;
        }
        if (activeSessionIndex < 0 || activeSessionIndex >= sessions.size()) {
            activeSessionIndex = Math.max(0, Math.min(sessions.size() - 1, activeSessionIndex));
        }
        return sessions.get(activeSessionIndex);
    }

    public static void createNewSession() {
        int nextNum = sessions.size() + 1;
        Session s = new Session("Session " + nextNum);
        sessions.add(s);
        activeSessionIndex = sessions.size() - 1;
        saveSessions();
    }

    public static void nextSession() {
        if (sessions.isEmpty()) return;
        activeSessionIndex = (activeSessionIndex + 1) % sessions.size();
    }

    public static void prevSession() {
        if (sessions.isEmpty()) return;
        activeSessionIndex = (activeSessionIndex - 1 + sessions.size()) % sessions.size();
    }

    public static void deleteActiveSession() {
        if (sessions.size() <= 1) {
            getActiveSession().items.clear();
        } else {
            sessions.remove(activeSessionIndex);
            if (activeSessionIndex >= sessions.size()) {
                activeSessionIndex = sessions.size() - 1;
            }
        }
        saveSessions();
    }

    public static void clearActiveSession() {
        getActiveSession().items.clear();
        saveSessions();
    }

    public static void loadSessions() {
        try {
            if (SESSIONS_FILE.exists()) {
                try (Reader reader = Files.newBufferedReader(SESSIONS_FILE.toPath())) {
                    List<Session> loaded = GSON.fromJson(reader, new TypeToken<List<Session>>(){}.getType());
                    if (loaded != null && !loaded.isEmpty()) {
                        sessions.clear();
                        sessions.addAll(loaded);
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (sessions.isEmpty()) {
            sessions.add(new Session("Session 1"));
        }
        activeSessionIndex = 0;
    }

    public static void saveSessions() {
        try {
            if (!SESSIONS_FILE.getParentFile().exists()) {
                SESSIONS_FILE.getParentFile().mkdirs();
            }
            try (Writer writer = Files.newBufferedWriter(SESSIONS_FILE.toPath())) {
                GSON.toJson(sessions, writer);
            }
        } catch (Throwable ignored) {}
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.greenhouseProfitTracker) return;

        boolean inGarden = SkyblockUtils.isInGarden();
        if (!inGarden) {
            warnedUnconfigured = false;
            return;
        }

        // Check if player is currently standing in a configured greenhouse plot
        if (isPlayerInGreenhouse()) {
            lastGreenhouseFarmTime = System.currentTimeMillis();
        }

        // Auto-sync or warn if greenhouse plots are not configured
        if ((s.greenhousePlots == null || s.greenhousePlots.isEmpty()) && !warnedUnconfigured) {
            long now = System.currentTimeMillis();
            if (now - lastWarnTime > 60000L) {
                lastWarnTime = now;
                warnedUnconfigured = true;
                sendConfigurePrompt(mc);
            }
        }
    }

    private static void sendConfigurePrompt(Minecraft mc) {
        if (mc.player == null) return;

        Component prompt = Component.literal("§8[§aGreenhouse§8] §eGreenhouse plots not configured! ")
                .append(Component.literal("§a§n[Click Here]§r")
                        .setStyle(Style.EMPTY
                                .withColor(ChatFormatting.GREEN)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent.RunCommand("/desk"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to open §b/desk§7 to configure plots")))))
                .append(Component.literal(" §eor run §b/desk§e to configure."));

        mc.player.sendSystemMessage(prompt);
    }

    public static void onChatMessage(Component message) {
        if (message == null) return;
        if (IS_PROCESSING_MSG.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.greenhouseProfitTracker) return;

        String unformatted = ChatFormatting.stripFormatting(message.getString());
        if (unformatted == null || unformatted.isEmpty()) return;

        // Skip our own mod messages or debug messages to prevent loops
        if (unformatted.contains("[Greenhouse") || unformatted.contains("[BomboAddons]") || unformatted.contains("DailyRewardDebug")) {
            return;
        }

        try {
            IS_PROCESSING_MSG.set(true);

            // 1. Rare Crop Drops (Hypixel puts drops in sacks; the (+123) in chat is the fortune/overbloom bonus)
            if (unformatted.contains("RARE CROP!")) {
                lastGreenhouseFarmTime = System.currentTimeMillis();
                if (s.greenhouseDebug) {
                    mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse Debug§8] §7Rare Crop Alert: §f" + unformatted));
                }
            }

            // 2. Sack Messages with Tooltip Inspection
            if (unformatted.contains("[Sacks]")) {
                long now = System.currentTimeMillis();
                boolean inGarden = SkyblockUtils.isInGarden();

                // Parse duration from '(Last 8s.)'
                long durationSec = 10;
                Matcher timeMatcher = LAST_SECONDS_PATTERN.matcher(unformatted);
                if (timeMatcher.find()) {
                    durationSec = parseLongSafe(timeMatcher.group(1));
                    if (durationSec <= 0) durationSec = 10;
                }

                // Check if player was in a greenhouse during this duration (+4s buffer)
                long maxWindowMs = (durationSec + 4) * 1000L;
                boolean wasInGreenhouse = (now - lastGreenhouseFarmTime <= maxWindowMs) || isPlayerInGreenhouse();

                // If on garden and plots haven't been configured yet, also track
                if (inGarden && (s.greenhousePlots == null || s.greenhousePlots.isEmpty())) {
                    wasInGreenhouse = true;
                }

                if (s.greenhouseDebug) {
                    mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse Debug§8] §eSacks message (inGarden=" + inGarden + ", wasInGreenhouse=" + wasInGreenhouse + ", window=" + (durationSec + 4) + "s)"));
                }

                if (wasInGreenhouse) {
                    List<String> hoverLines = extractAllHoverText(message);
                    if (s.greenhouseDebug) {
                        mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse Debug§8] §7Hover lines count: " + hoverLines.size()));
                        for (String hl : hoverLines) {
                            mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse Debug§8] §8  - " + hl));
                        }
                    }

                    Map<String, Long> deltaMap = new HashMap<>();
                    for (String line : hoverLines) {
                        String clean = ChatFormatting.stripFormatting(line).trim();
                        Matcher addMatcher = ITEM_LINE_ADD.matcher(clean);
                        if (addMatcher.find()) {
                            long count = parseLongSafe(addMatcher.group(1));
                            String item = addMatcher.group(2).trim();
                            deltaMap.merge(item, count, Long::sum);
                            continue;
                        }
                        Matcher subMatcher = ITEM_LINE_SUB.matcher(clean);
                        if (subMatcher.find()) {
                            long count = parseLongSafe(subMatcher.group(1));
                            String item = subMatcher.group(2).trim();
                            deltaMap.merge(item, -count, Long::sum);
                        }
                    }

                    for (Map.Entry<String, Long> entry : deltaMap.entrySet()) {
                        String item = entry.getKey();
                        long delta = entry.getValue();
                        if (delta > 0) {
                            recordItem(item, delta);
                            if (s.greenhouseDebug) {
                                mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse Debug§8] §aTracked item: +" + delta + " " + item));
                            }
                        }
                    }
                }
            }
        } finally {
            IS_PROCESSING_MSG.set(false);
        }
    }

    private static List<String> extractAllHoverText(Component component) {
        List<String> lines = new ArrayList<>();
        if (component == null) return lines;

        Set<String> visitedTexts = new HashSet<>();
        inspectComponentHover(component, lines, visitedTexts);
        return lines;
    }

    private static void inspectComponentHover(Component comp, List<String> lines, Set<String> visitedTexts) {
        if (comp == null) return;
        Style style = comp.getStyle();
        if (style != null && style.getHoverEvent() != null) {
            HoverEvent hover = style.getHoverEvent();
            if (hover instanceof HoverEvent.ShowText textHover) {
                Component hoverComp = textHover.value();
                if (hoverComp != null) {
                    String fullText = hoverComp.getString();
                    if (fullText != null && !fullText.trim().isEmpty() && visitedTexts.add(fullText.trim())) {
                        for (String l : fullText.split("\\r?\\n")) {
                            if (!l.trim().isEmpty()) {
                                lines.add(l.trim());
                            }
                        }
                    }
                }
            }
        }
        for (Component child : comp.getSiblings()) {
            inspectComponentHover(child, lines, visitedTexts);
        }
    }

    public static void recordItem(String name, long amount) {
        if (name == null || name.isEmpty() || amount <= 0) return;
        getActiveSession().items.merge(name, amount, Long::sum);
        saveSessions();
    }

    public static int getPlotIdAt(double px, double pz) {
        if (Math.abs(px) <= 48.0 && Math.abs(pz) <= 48.0) return 0; // Barn Center
        if (px < -240.0 || px > 240.0 || pz < -240.0 || pz > 240.0) return -1;

        int gx = (int) Math.floor((px + 240.0) / 96.0);
        int gz = (int) Math.floor((pz + 240.0) / 96.0);
        gx = Math.max(0, Math.min(4, gx));
        gz = Math.max(0, Math.min(4, gz));

        if (gx == 2 && gz == 2) return 0;

        int idx = gz * 5 + gx;
        if (idx < 12) {
            return idx + 1;
        } else if (idx == 12) {
            return 0; // Barn
        } else {
            return idx;
        }
    }

    public static boolean isPlayerInGreenhouse() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int plotId = getPlotIdAt(mc.player.getX(), mc.player.getZ());
        BomboConfig.Settings s = BomboConfig.get();
        return s.greenhousePlots != null && s.greenhousePlots.contains(plotId);
    }

    public static void onContainerTick(AbstractContainerScreen<?> screen) {
        if (screen == null) {
            lastScannedScreenHashCode = 0;
            scannedCurrentScreen = false;
            return;
        }

        String title = screen.getTitle() != null ? screen.getTitle().getString() : "";
        if (title == null) return;

        int currentHash = System.identityHashCode(screen);
        if (currentHash != lastScannedScreenHashCode) {
            lastScannedScreenHashCode = currentHash;
            scannedCurrentScreen = false;
        }

        Minecraft mc = Minecraft.getInstance();
        BomboConfig.Settings s = BomboConfig.get();

        // 1. In Configure Plots GUI: Scan plots for Greenhouse lore
        if (title.contains("Configure Plots")) {
            Set<Integer> detectedPlots = new TreeSet<>();
            int totalPlotsFound = 0;

            for (Slot slot : screen.getMenu().slots) {
                if (slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    String name = stack.getHoverName().getString();
                    if (name.contains("Plot - ")) {
                        int plotNum = parsePlotNumber(name);
                        if (plotNum > 0) {
                            totalPlotsFound++;
                            boolean isGreenhouse = false;
                            ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
                            if (lore != null) {
                                for (Component line : lore.lines()) {
                                    String clean = ChatFormatting.stripFormatting(line.getString()).toLowerCase();
                                    if (clean.contains("greenhouse plot") || clean.contains("greenhouse")) {
                                        isGreenhouse = true;
                                        break;
                                    }
                                }
                            }

                            if (isGreenhouse) {
                                detectedPlots.add(plotNum);
                            }
                        }
                    }
                }
            }

            // Once the inventory slots have populated (at least several plots found)
            if (totalPlotsFound >= 5 && !scannedCurrentScreen) {
                scannedCurrentScreen = true;
                s.greenhousePlots = new HashSet<>(detectedPlots);
                BomboConfig.save();

                if (mc.player != null) {
                    if (!detectedPlots.isEmpty()) {
                        mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse§8] §aDetected §e" + detectedPlots.size() + "§a plots as greenhouse: §e" + detectedPlots));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§8[§aGreenhouse§8] §eNo greenhouse plots detected in Configure Plots."));
                    }
                }
            }
        }
    }

    public static boolean shouldHighlightConfigurePlotsSlot(ItemStack stack, String screenTitle) {
        if (stack == null || stack.isEmpty() || screenTitle == null) return false;
        if (!screenTitle.contains("Desk")) return false;

        BomboConfig.Settings s = BomboConfig.get();
        if (s.greenhousePlots != null && !s.greenhousePlots.isEmpty()) return false;

        String name = stack.getHoverName().getString();
        return name != null && name.contains("Configure Plots");
    }

    private static int parsePlotNumber(String name) {
        try {
            String clean = ChatFormatting.stripFormatting(name).trim();
            Matcher m = Pattern.compile("Plot\\s*-\\s*(\\d+)").matcher(clean);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static double getItemPrice(String itemName) {
        if (itemName == null || itemName.isEmpty()) return 1.0;
        LowestBinManager.ensureLoaded();

        String id = LowestBinManager.findIdByName(itemName, true);
        if (id == null) {
            id = itemName.toUpperCase().replace(" ", "_");
        }

        double bzPrice = LowestBinManager.getSellPrice(id);
        long lbPrice = LowestBinManager.getCachedPrice(id);
        long npcPrice = LowestBinManager.getNpcPrice(id);

        double best = Math.max(bzPrice, Math.max(lbPrice, npcPrice));
        if (best > 0) return best;

        String fuzzy = LowestBinManager.findIdByName(itemName, false);
        if (fuzzy != null && !fuzzy.equals(id)) {
            double fBz = LowestBinManager.getSellPrice(fuzzy);
            long fLb = LowestBinManager.getCachedPrice(fuzzy);
            long fNpc = LowestBinManager.getNpcPrice(fuzzy);
            double fBest = Math.max(fBz, Math.max(fLb, fNpc));
            if (fBest > 0) return fBest;
        }

        return 1.0;
    }

    private static long parseLongSafe(String str) {
        if (str == null) return 0;
        try {
            return Long.parseLong(str.replaceAll("[^0-9]", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
