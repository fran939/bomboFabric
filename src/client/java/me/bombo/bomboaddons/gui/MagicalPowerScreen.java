package me.bombo.bomboaddons.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MagicalPowerScreen extends Screen {
    private final Screen parent;
    private final String targetPlayer;
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private boolean loading = true;
    private String errorMessage = null;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    // Parsed Data
    private String playerName = "";
    private String profileName = "";
    private int currentMp = 0;
    private int maxMp = 2248;
    private String selectedPower = "None";
    private int tuningPoints = 0;
    private int bagSlots = 0;
    private int maxBagSlots = 281;

    // Bonus MP
    private String abicaseBonus = null;
    private String hegemonyBonus = null;
    private String riftPrismBonus = null;

    public enum Tab {
        UPGRADES,
        MISSING,
        BREAKDOWN
    }

    private Tab currentTab = Tab.UPGRADES;

    public static class UpgradeEntry {
        public final int rank;
        public final String name;
        public final String rarity;
        public final String kind; // "buy" or "upgrade"
        public final String from;
        public final long cost;
        public final int mpGain;
        public final double coinsPerMp;
        public final boolean purchasable;

        public UpgradeEntry(int rank, String name, String rarity, String kind, String from, long cost, int mpGain, double coinsPerMp, boolean purchasable) {
            this.rank = rank;
            this.name = name;
            this.rarity = rarity != null ? rarity.toUpperCase(Locale.ROOT) : "COMMON";
            this.kind = kind;
            this.from = from;
            this.cost = cost;
            this.mpGain = mpGain;
            this.coinsPerMp = coinsPerMp;
            this.purchasable = purchasable;
        }
    }

    public static class RarityBreakdownEntry {
        public final String rarity;
        public final int count;
        public final int mpEach;
        public final int totalMp;

        public RarityBreakdownEntry(String rarity, int count, int mpEach, int totalMp) {
            this.rarity = rarity;
            this.count = count;
            this.mpEach = mpEach;
            this.totalMp = totalMp;
        }
    }

    private final List<UpgradeEntry> upgrades = new ArrayList<>();
    private final List<UpgradeEntry> missing = new ArrayList<>();
    private final List<RarityBreakdownEntry> breakdowns = new ArrayList<>();

    private EditBox searchBox;
    private String searchFilter = "";

    private static class ClickableAction {
        int minX, minY, maxX, maxY;
        Runnable action;
        String tooltip;

        ClickableAction(int minX, int minY, int maxX, int maxY, Runnable action, String tooltip) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.action = action;
            this.tooltip = tooltip;
        }
    }

    private final List<ClickableAction> clickActions = new ArrayList<>();

    public MagicalPowerScreen(Screen parent, String username) {
        super(Component.literal("Magical Power & Accessories"));
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        this.targetPlayer = (username != null && !username.trim().isEmpty()) ? username.trim() :
                (mc.player != null ? mc.player.getScoreboardName() : (mc.getUser() != null ? mc.getUser().getName() : "me"));
    }

    @Override
    protected void init() {
        super.init();
        clickActions.clear();

        int winW = Math.min(this.width - 24, 720);
        int winH = Math.min(this.height - 24, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        String oldSearch = searchBox != null ? searchBox.getValue() : "";
        this.searchBox = new EditBox(this.font, winX + winW - 200, winY + 68, 90, 16, Component.literal("Search"));
        this.searchBox.setValue(oldSearch);
        this.searchBox.setResponder(val -> {
            this.searchFilter = val.trim().toLowerCase(Locale.ROOT);
            this.scrollAmount = 0;
        });
        this.addRenderableWidget(this.searchBox);

        if (loading && upgrades.isEmpty() && breakdowns.isEmpty() && errorMessage == null) {
            fetchDataAsync();
        }
    }

    private void fetchDataAsync() {
        this.loading = true;
        this.errorMessage = null;

        CompletableFuture.runAsync(() -> {
            try {
                String url = BomboApiUrl.getApiUrl("/accessories/" + URLEncoder.encode(targetPlayer, StandardCharsets.UTF_8) + "?format=json");
                HttpRequest request = BomboApiUrl.attachApiKey(
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Accept", "application/json")
                                .GET()
                ).build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    this.errorMessage = "HTTP " + response.statusCode();
                    this.loading = false;
                    return;
                }

                JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                parseData(root);
                this.loading = false;
            } catch (Exception e) {
                this.errorMessage = e.getMessage() != null ? e.getMessage() : e.toString();
                this.loading = false;
            }
        });
    }

    private void parseData(JsonObject root) {
        upgrades.clear();
        missing.clear();
        breakdowns.clear();

        if (root.has("player") && root.get("player").isJsonObject()) {
            JsonObject pl = root.getAsJsonObject("player");
            this.playerName = pl.has("name") ? pl.get("name").getAsString() : targetPlayer;
            if (pl.has("profile") && pl.get("profile").isJsonObject()) {
                this.profileName = pl.getAsJsonObject("profile").has("name") ? pl.getAsJsonObject("profile").get("name").getAsString() : "";
            }
        } else {
            this.playerName = targetPlayer;
        }

        // Ceiling & MP
        if (root.has("ceiling") && root.get("ceiling").isJsonObject()) {
            JsonObject c = root.getAsJsonObject("ceiling");
            this.currentMp = c.has("current") ? c.get("current").getAsInt() : 0;
            this.maxMp = c.has("max") ? c.get("max").getAsInt() : 2248;
        } else if (root.has("total_magical_power")) {
            this.currentMp = root.get("total_magical_power").getAsInt();
        }

        // Selected Power & Tuning
        if (root.has("tuning") && root.get("tuning").isJsonObject()) {
            JsonObject t = root.getAsJsonObject("tuning");
            this.tuningPoints = t.has("points") ? t.get("points").getAsInt() : 0;
        }
        if (root.has("accessories") && root.get("accessories").isJsonObject()) {
            JsonObject acc = root.getAsJsonObject("accessories");
            this.selectedPower = acc.has("selectedPower") ? acc.get("selectedPower").getAsString() : "None";
        } else if (root.has("selected_power")) {
            this.selectedPower = root.get("selected_power").getAsString();
        }

        // Slots
        if (root.has("slots") && root.get("slots").isJsonObject()) {
            JsonObject sl = root.getAsJsonObject("slots");
            this.bagSlots = sl.has("floor") ? sl.get("floor").getAsInt() : (sl.has("confirmed") ? sl.get("confirmed").getAsInt() : 0);
            this.maxBagSlots = sl.has("possibleMax") ? sl.get("possibleMax").getAsInt() : 281;
        }

        // Upgrades (buyOrder)
        if (root.has("buyOrder") && root.get("buyOrder").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("buyOrder");
            int rank = 1;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = o.has("name") ? o.get("name").getAsString() : "Unknown";
                String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "COMMON";
                String kind = o.has("kind") ? o.get("kind").getAsString() : "buy";
                String from = o.has("from") ? o.get("from").getAsString() : "";
                long cost = o.has("cost") ? o.get("cost").getAsLong() : 0L;
                int gain = o.has("mpGain") ? o.get("mpGain").getAsInt() : (o.has("gain") ? o.get("gain").getAsInt() : 0);
                double coinsPerMp = o.has("coinsPerMp") ? o.get("coinsPerMp").getAsDouble() : (gain > 0 ? (double) cost / gain : 0);
                boolean purchasable = !o.has("purchasable") || o.get("purchasable").getAsBoolean();

                upgrades.add(new UpgradeEntry(rank++, name, rarity, kind, from, cost, gain, coinsPerMp, purchasable));
            }
        }

        // Missing Accessories
        if (root.has("missingAccessories") && root.get("missingAccessories").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("missingAccessories");
            int rank = 1;
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = o.has("name") ? o.get("name").getAsString() : "Unknown";
                String rarity = o.has("rarity") ? o.get("rarity").getAsString() : "COMMON";
                long cost = o.has("cost") ? o.get("cost").getAsLong() : 0L;
                int gain = o.has("ap") ? o.get("ap").getAsInt() : 0;
                missing.add(new UpgradeEntry(rank++, name, rarity, "missing", "", cost, gain, 0, true));
            }
        }

        // Bag Breakdown
        if (root.has("bagBreakdown") && root.get("bagBreakdown").isJsonObject()) {
            JsonObject bb = root.getAsJsonObject("bagBreakdown");
            String[] rarities = {"VERY_SPECIAL", "SPECIAL", "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"};
            for (String r : rarities) {
                if (bb.has(r) && bb.get(r).isJsonObject()) {
                    JsonObject ro = bb.getAsJsonObject(r);
                    int count = ro.has("count") ? ro.get("count").getAsInt() : 0;
                    int mpEach = ro.has("mpEach") ? ro.get("mpEach").getAsInt() : 0;
                    int tot = ro.has("totalMp") ? ro.get("totalMp").getAsInt() : count * mpEach;
                    if (count > 0) {
                        breakdowns.add(new RarityBreakdownEntry(r, count, mpEach, tot));
                    }
                }
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int winW = Math.min(this.width - 24, 720);
        int winH = Math.min(this.height - 24, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int bg = ConfigUITheme.getMainWindowBg();
        int border = ConfigUITheme.getBorderColor();
        int accent = 0xFFC084FC; // Purple/magenta accent

        // Background panel
        g.fill(winX, winY, winX + winW, winY + winH, bg);
        g.outline(winX, winY, winW, winH, border);
        g.fill(winX, winY, winX + winW, winY + 3, accent);

        Font font = Minecraft.getInstance().font;

        // Title and Header info
        g.text(font, "§d§lMagical Power & Accessories", winX + 16, winY + 12, 0xFFFFFFFF, true);
        String sub = "§7Player: §f" + (playerName.isEmpty() ? targetPlayer : playerName) +
                (profileName.isEmpty() ? "" : " §8| §7Profile: §b" + profileName);
        g.text(font, sub, winX + 16, winY + 25, 0xFFA0AEC0, false);

        // Top right buttons: [🌐 Web PV] and [✕ Close]
        int webBtnW = 85;
        int webBtnX = winX + winW - webBtnW - 40;
        int webBtnY = winY + 10;
        boolean webHov = mouseX >= webBtnX && mouseX <= webBtnX + webBtnW && mouseY >= webBtnY && mouseY <= webBtnY + 16;
        g.fill(webBtnX, webBtnY, webBtnX + webBtnW, webBtnY + 16, webHov ? 0xFF38BDF8 : 0xFF1E293B);
        g.outline(webBtnX, webBtnY, webBtnW, 16, 0xFF0284C7);
        g.centeredText(font, "§f🌐 Open Web", webBtnX + webBtnW / 2, webBtnY + 4, 0xFFFFFFFF);

        int closeX = winX + winW - 30;
        int closeY = winY + 10;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 16;
        g.fill(closeX, closeY, closeX + 18, closeY + 16, closeHov ? 0xFFEF4444 : 0xFF334155);
        g.outline(closeX, closeY, 18, 16, 0xFF64748B);
        g.centeredText(font, "✕", closeX + 9, closeY + 4, 0xFFFFFFFF);

        // Hero Stats Bar (Row 1)
        int heroY = winY + 40;
        int statW = (winW - 32 - 18) / 4;

        // 1. MP Card
        drawStatCard(g, font, winX + 16, heroY, statW, 24, "MAGICAL POWER", "§d§l✨ " + formatInt(currentMp) + " §7/ " + formatInt(maxMp));
        // 2. Power Card
        drawStatCard(g, font, winX + 16 + statW + 6, heroY, statW, 24, "SELECTED POWER", "§6" + (selectedPower.isEmpty() ? "NONE" : selectedPower.toUpperCase(Locale.ROOT)));
        // 3. Tuning Card
        drawStatCard(g, font, winX + 16 + (statW + 6) * 2, heroY, statW, 24, "TUNING POINTS", "§b" + formatInt(tuningPoints) + " §7pts");
        // 4. Slots Card
        drawStatCard(g, font, winX + 16 + (statW + 6) * 3, heroY, statW, 24, "BAG SLOTS", "§a" + formatInt(bagSlots) + " §7/ " + formatInt(maxBagSlots));

        // Tab selection row & search box (Row 2)
        int tabY = winY + 68;
        int tabX = winX + 16;

        drawTabPill(g, font, tabX, tabY, "Cheapest Upgrades (" + upgrades.size() + ")", currentTab == Tab.UPGRADES, mouseX, mouseY);
        tabX += font.width("Cheapest Upgrades (" + upgrades.size() + ")") + 24;

        drawTabPill(g, font, tabX, tabY, "Missing (" + missing.size() + ")", currentTab == Tab.MISSING, mouseX, mouseY);
        tabX += font.width("Missing (" + missing.size() + ")") + 24;

        drawTabPill(g, font, tabX, tabY, "Rarity Breakdown", currentTab == Tab.BREAKDOWN, mouseX, mouseY);

        // Search label
        g.text(font, "§7Search:", winX + winW - 250, tabY + 4, 0xFF8B949E, false);

        // Divider
        g.fill(winX + 12, winY + 88, winX + winW - 12, winY + 89, border);

        // Render SearchBox widget
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Content Area with Scissor
        int contentX = winX + 16;
        int contentY = winY + 94;
        int contentW = winW - 32;
        int contentH = winH - 106;

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        synchronized (clickActions) {
            clickActions.clear();
        }

        if (loading) {
            g.centeredText(font, "§e⚡ Loading accessories & magical power...", winX + winW / 2, winY + winH / 2, 0xFFFFFFFF);
            g.disableScissor();
            return;
        }

        if (errorMessage != null) {
            g.centeredText(font, "§cFailed to load: §f" + errorMessage, winX + winW / 2, winY + winH / 2 - 10, 0xFFFF5555);
            g.centeredText(font, "§7Click anywhere to retry or verify the username.", winX + winW / 2, winY + winH / 2 + 10, 0xFF888888);
            g.disableScissor();
            return;
        }

        if (currentTab == Tab.UPGRADES) {
            renderUpgradesList(g, font, contentX, contentY, contentW, contentH, mouseX, mouseY);
        } else if (currentTab == Tab.MISSING) {
            renderMissingList(g, font, contentX, contentY, contentW, contentH, mouseX, mouseY);
        } else if (currentTab == Tab.BREAKDOWN) {
            renderBreakdownTab(g, font, contentX, contentY, contentW, contentH, mouseX, mouseY);
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbW = 4;
            int sbX = winX + winW - sbW - 6;
            int sbH = contentH;
            int thumbH = Math.max(24, (int) ((float) contentH / (contentH + maxScroll) * contentH));
            int thumbY = contentY + (int) ((this.scrollAmount / maxScroll) * (contentH - thumbH));

            g.fill(sbX, contentY, sbX + sbW, contentY + sbH, 0x33000000);
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, accent);
        }
    }

    private void renderUpgradesList(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<UpgradeEntry> filtered = new ArrayList<>();
        for (UpgradeEntry u : upgrades) {
            if (searchFilter.isEmpty() || u.name.toLowerCase(Locale.ROOT).contains(searchFilter) || u.rarity.toLowerCase(Locale.ROOT).contains(searchFilter)) {
                filtered.add(u);
            }
        }

        if (filtered.isEmpty()) {
            g.centeredText(font, upgrades.isEmpty() ? "§a🎉 All available accessories owned!" : "§7No upgrades matching filter '§f" + searchFilter + "§7'", x + w / 2, y + 40, 0xFFA0AEC0);
            this.maxScroll = 0;
            return;
        }

        int itemH = 26;
        int totalH = filtered.size() * (itemH + 4);
        this.maxScroll = Math.max(0, totalH - h);

        int curY = y - (int) scrollAmount;
        for (UpgradeEntry u : filtered) {
            if (curY + itemH >= y && curY <= y + h) {
                boolean hovered = mouseX >= x && mouseX <= x + w - 10 && mouseY >= curY && mouseY <= curY + itemH;
                int rowBg = hovered ? 0xFF1F2937 : 0xFF111827;
                int rowBorder = hovered ? 0xFF4B5563 : 0xFF1F2937;

                g.fill(x, curY, x + w - 10, curY + itemH, rowBg);
                g.outline(x, curY, w - 10, itemH, rowBorder);

                // Rank badge
                g.fill(x + 4, curY + 4, x + 26, curY + itemH - 4, 0xFF374151);
                g.centeredText(font, "§7#" + u.rank, x + 15, curY + 9, 0xFFE5E7EB);

                // Rarity color & Name
                int nameColor = getRarityColor(u.rarity);
                g.text(font, u.name, x + 32, curY + 5, nameColor, true);

                // Subtitle: Kind & From
                String kindLabel = u.kind.equalsIgnoreCase("upgrade") ? "§eUpgrade from " + (u.from.isEmpty() ? "previous" : u.from) : "§aDirect Buy / Craft";
                g.text(font, kindLabel, x + 32, curY + 15, 0xFF9CA3AF, false);

                // Gain & Cost Per MP
                String gainStr = "§d+" + u.mpGain + " MP";
                g.text(font, gainStr, x + w - 240, curY + 9, 0xFFF472B6, true);

                String effStr = "§7(" + formatCoins(u.coinsPerMp) + "/MP)";
                g.text(font, effStr, x + w - 195, curY + 9, 0xFF9CA3AF, false);

                // Cost
                String costStr = "§6" + formatCoins(u.cost);
                g.text(font, costStr, x + w - 125, curY + 9, 0xFFFBBF24, true);

                // Buy button [ ⚡ /bz ]
                int btnW = 48;
                int btnH = 18;
                int btnX = x + w - 66;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0xFF10B981 : 0xFF065F46);
                g.outline(btnX, btnY, btnW, btnH, 0xFF047857);
                g.centeredText(font, "§f⚡ /bz", btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);

                final String itemName = u.name;
                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                        triggerBazaarSearch(itemName);
                    }, "Open Bazaar for " + itemName));
                }
            }
            curY += itemH + 4;
        }
    }

    private void renderMissingList(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<UpgradeEntry> filtered = new ArrayList<>();
        for (UpgradeEntry u : missing) {
            if (searchFilter.isEmpty() || u.name.toLowerCase(Locale.ROOT).contains(searchFilter) || u.rarity.toLowerCase(Locale.ROOT).contains(searchFilter)) {
                filtered.add(u);
            }
        }

        if (filtered.isEmpty()) {
            g.centeredText(font, missing.isEmpty() ? "§a🎉 No missing accessories recorded!" : "§7No items matching filter '§f" + searchFilter + "§7'", x + w / 2, y + 40, 0xFFA0AEC0);
            this.maxScroll = 0;
            return;
        }

        int itemH = 24;
        int totalH = filtered.size() * (itemH + 4);
        this.maxScroll = Math.max(0, totalH - h);

        int curY = y - (int) scrollAmount;
        for (UpgradeEntry u : filtered) {
            if (curY + itemH >= y && curY <= y + h) {
                boolean hovered = mouseX >= x && mouseX <= x + w - 10 && mouseY >= curY && mouseY <= curY + itemH;
                int rowBg = hovered ? 0xFF1F2937 : 0xFF111827;

                g.fill(x, curY, x + w - 10, curY + itemH, rowBg);
                g.outline(x, curY, w - 10, itemH, hovered ? 0xFF4B5563 : 0xFF1F2937);

                int nameColor = getRarityColor(u.rarity);
                g.text(font, u.name, x + 12, curY + 7, nameColor, true);
                g.text(font, "§8[" + u.rarity + "]", x + 12 + font.width(u.name) + 8, curY + 7, 0xFF6B7280, false);

                if (u.mpGain > 0) {
                    g.text(font, "§d+" + u.mpGain + " MP", x + w - 180, curY + 7, 0xFFF472B6, false);
                }

                // Buy button
                int btnW = 48;
                int btnH = 16;
                int btnX = x + w - 66;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0xFF10B981 : 0xFF065F46);
                g.outline(btnX, btnY, btnW, btnH, 0xFF047857);
                g.centeredText(font, "§f⚡ /bz", btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);

                final String itemName = u.name;
                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                        triggerBazaarSearch(itemName);
                    }, "Open Bazaar for " + itemName));
                }
            }
            curY += itemH + 4;
        }
    }

    private void renderBreakdownTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        this.maxScroll = 0;
        int curY = y + 10;

        g.text(font, "§f§lAccessories by Rarity", x + 10, curY, 0xFFFFFFFF, true);
        curY += 18;

        for (RarityBreakdownEntry b : breakdowns) {
            int cardW = w - 20;
            int cardH = 22;
            int color = getRarityColor(b.rarity);

            g.fill(x + 10, curY, x + 10 + cardW, curY + cardH, 0xFF111827);
            g.outline(x + 10, curY, cardW, cardH, 0xFF1F2937);

            // Rarity pill
            g.text(font, b.rarity, x + 20, curY + 6, color, true);
            g.text(font, "§7Count: §f" + b.count + " items", x + 160, curY + 6, 0xFFD1D5DB, false);
            g.text(font, "§7Value: §f" + b.mpEach + " MP each", x + 280, curY + 6, 0xFF9CA3AF, false);
            g.text(font, "§d§l" + b.totalMp + " MP total", x + cardW - 70, curY + 6, 0xFFF472B6, true);

            curY += cardH + 6;
        }

        curY += 10;
        g.text(font, "§f§lPermanent Account MP Bonuses", x + 10, curY, 0xFFFFFFFF, true);
        curY += 16;

        drawBonusRow(g, font, x + 10, curY, w - 20, "Rezar® Abicase", "+42 MP (Max 85 Contacts)", "§aACTIVE");
        curY += 24;
        drawBonusRow(g, font, x + 10, curY, w - 20, "Hegemony Artifact", "+22 MP (Doubles Highest Rarity)", "§aACTIVE");
        curY += 24;
        drawBonusRow(g, font, x + 10, curY, w - 20, "Rift Prism Imbuement", "+11 MP (Jacquelle's Rift Cats)", "§aACTIVE");
    }

    private void drawBonusRow(GuiGraphicsExtractor g, Font font, int x, int y, int w, String title, String desc, String status) {
        g.fill(x, y, x + w, y + 20, 0xFF111827);
        g.outline(x, y, w, 20, 0xFF1F2937);
        g.text(font, "§b" + title, x + 10, y + 6, 0xFF38BDF8, true);
        g.text(font, "§7" + desc, x + 180, y + 6, 0xFF9CA3AF, false);
        g.text(font, status, x + w - 60, y + 6, 0xFF10B981, true);
    }

    private void drawStatCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, String value) {
        g.fill(x, y, x + w, y + h, 0xFF111827);
        g.outline(x, y, w, h, 0xFF1F2937);
        g.centeredText(font, "§7" + label, x + w / 2, y + 3, 0xFF9CA3AF);
        g.centeredText(font, value, x + w / 2, y + 13, 0xFFFFFFFF);
    }

    private void drawTabPill(GuiGraphicsExtractor g, Font font, int x, int y, String label, boolean active, int mouseX, int mouseY) {
        int w = font.width(label) + 16;
        int h = 18;
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = active ? 0xFFC084FC : (hov ? 0xFF374151 : 0xFF1F2937);
        int textCol = active ? 0xFF000000 : 0xFFE5E7EB;

        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, active ? 0xFFE9D5FF : 0xFF374151);
        g.centeredText(font, (active ? "§l" : "") + label, x + w / 2, y + 5, textCol);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = Math.min(this.width - 24, 720);
        int winH = Math.min(this.height - 24, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Close button
        int closeX = winX + winW - 30;
        int closeY = winY + 10;
        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 16) {
            this.onClose();
            return true;
        }

        // Open Web PV button
        int webBtnW = 85;
        int webBtnX = winX + winW - webBtnW - 40;
        int webBtnY = winY + 10;
        if (mouseX >= webBtnX && mouseX <= webBtnX + webBtnW && mouseY >= webBtnY && mouseY <= webBtnY + 16) {
            String p = playerName.isEmpty() ? targetPlayer : playerName;
            String url = "https://bomboclas.me/pv/" + URLEncoder.encode(p, StandardCharsets.UTF_8);
            Util.getPlatform().openUri(URI.create(url));
            return true;
        }

        // Tab selection
        int tabY = winY + 68;
        int tabX = winX + 16;
        int tab1W = font.width("Cheapest Upgrades (" + upgrades.size() + ")") + 16;
        if (mouseX >= tabX && mouseX <= tabX + tab1W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.UPGRADES;
            scrollAmount = 0;
            return true;
        }
        tabX += tab1W + 8;
        int tab2W = font.width("Missing (" + missing.size() + ")") + 16;
        if (mouseX >= tabX && mouseX <= tabX + tab2W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.MISSING;
            scrollAmount = 0;
            return true;
        }
        tabX += tab2W + 8;
        int tab3W = font.width("Rarity Breakdown") + 16;
        if (mouseX >= tabX && mouseX <= tabX + tab3W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.BREAKDOWN;
            scrollAmount = 0;
            return true;
        }

        // Clickable actions (e.g. /bz buttons)
        synchronized (clickActions) {
            for (ClickableAction action : clickActions) {
                if (mouseX >= action.minX && mouseX <= action.maxX && mouseY >= action.minY && mouseY <= action.maxY) {
                    action.action.run();
                    return true;
                }
            }
        }

        // If in error state, clicking retries
        if (errorMessage != null) {
            fetchDataAsync();
            return true;
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0) {
            this.scrollAmount -= verticalAmount * 24.0;
            if (this.scrollAmount < 0) this.scrollAmount = 0;
            if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    private void triggerBazaarSearch(String itemName) {
        if (itemName == null || itemName.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            mc.keyboardHandler.setClipboard(itemName);
        } catch (Exception ignored) {}

        mc.player.connection.sendCommand("bz " + itemName);
        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aBazaar: §e" + itemName + " §7(Copied name to clipboard)"));
    }

    private int getRarityColor(String rarity) {
        if (rarity == null) return 0xFFFFFFFF;
        switch (rarity.toUpperCase(Locale.ROOT)) {
            case "VERY_SPECIAL":
            case "SPECIAL":
                return 0xFFFF5555; // Red
            case "MYTHIC":
                return 0xFFFF55FF; // Light purple / Pink
            case "LEGENDARY":
                return 0xFFFFAA00; // Gold / Orange
            case "EPIC":
                return 0xFFAA00AA; // Dark purple
            case "RARE":
                return 0xFF5555FF; // Blue
            case "UNCOMMON":
                return 0xFF55FF55; // Green
            case "COMMON":
            default:
                return 0xFFFFFFFF; // White
        }
    }

    private String formatCoins(double coins) {
        if (coins >= 1_000_000_000) {
            return String.format(Locale.ROOT, "%.2fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000) {
            return String.format(Locale.ROOT, "%.2fM", coins / 1_000_000.0);
        } else if (coins >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", coins / 1_000.0);
        } else {
            return String.format(Locale.ROOT, "%.0f", coins);
        }
    }

    private String formatInt(int val) {
        return String.format(Locale.ROOT, "%,d", val);
    }
}
