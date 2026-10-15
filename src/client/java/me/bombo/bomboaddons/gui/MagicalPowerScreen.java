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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private int maxMp = 2184;
    private String selectedPower = "None";
    private int tuningPoints = 0;
    private int bagSlots = 0;
    private int maxBagSlots = 281;

    public enum Tab {
        UPGRADES,
        MISSING,
        TALISMANS,
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
        public final String source;
        public final String recipeText;
        public final JsonObject recipe;
        public final JsonObject craftDetails;

        public UpgradeEntry(int rank, String name, String rarity, String kind, String from, long cost, int mpGain,
                            double coinsPerMp, boolean purchasable, String source, String recipeText,
                            JsonObject recipe, JsonObject craftDetails) {
            this.rank = rank;
            this.name = name;
            this.rarity = rarity != null ? rarity.toUpperCase(Locale.ROOT) : "COMMON";
            this.kind = kind;
            this.from = from;
            this.cost = cost;
            this.mpGain = mpGain;
            this.coinsPerMp = coinsPerMp;
            this.purchasable = purchasable;
            this.source = source != null ? source : "ah";
            this.recipeText = recipeText != null ? recipeText : "";
            this.recipe = recipe;
            this.craftDetails = craftDetails;
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

    public static class TalismanTier {
        public final String id;
        public final String name;
        public final String rarity;
        public final int ap;
        public final long price;
        public final boolean owned;
        public final JsonObject recipe;
        public final JsonObject craftDetails;
        public final long craftCost;

        public TalismanTier(String id, String name, String rarity, int ap, long price, boolean owned,
                            JsonObject recipe, JsonObject craftDetails, long craftCost) {
            this.id = id;
            this.name = name;
            this.rarity = rarity != null ? rarity.toUpperCase(Locale.ROOT) : "COMMON";
            this.ap = ap;
            this.price = price;
            this.owned = owned;
            this.recipe = recipe;
            this.craftDetails = craftDetails;
            this.craftCost = craftCost;
        }
    }

    public static class TalismanChain {
        public final String name;
        public final String key;
        public final String wiki;
        public final List<TalismanTier> tiers;

        public TalismanChain(String name, String key, String wiki, List<TalismanTier> tiers) {
            this.name = name;
            this.key = key;
            this.wiki = wiki != null ? wiki : "";
            this.tiers = tiers;
        }
    }

    public static class CraftIngredient {
        public final String id;
        public final String name;
        public final int needed;
        public final int owned;
        public final int missing;
        public final long unitPrice;
        public final boolean hasEnough;

        public CraftIngredient(String id, String name, int needed, int owned, int missing, long unitPrice, boolean hasEnough) {
            this.id = id;
            this.name = name;
            this.needed = needed;
            this.owned = owned;
            this.missing = missing;
            this.unitPrice = unitPrice;
            this.hasEnough = hasEnough;
        }
    }

    public static class CraftModalState {
        public String itemName;
        public String rarity;
        public JsonObject grid; // A1..C3
        public List<CraftIngredient> ingredients = new ArrayList<>();
        public boolean canCraftNow;
        public List<CraftIngredient> missingToBuy = new ArrayList<>();
        public long totalMissingCost;
    }

    private final List<UpgradeEntry> upgrades = new ArrayList<>();
    private final List<UpgradeEntry> missing = new ArrayList<>();
    private final List<TalismanChain> talismanChains = new ArrayList<>();
    private final List<RarityBreakdownEntry> breakdowns = new ArrayList<>();

    private CraftModalState activeModal = null;

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
    private final List<ClickableAction> modalActions = new ArrayList<>();

    public MagicalPowerScreen(Screen parent, String username) {
        this(parent, username, Tab.UPGRADES);
    }

    public MagicalPowerScreen(Screen parent, String username, Tab initialTab) {
        super(Component.literal("Magical Power & Accessories"));
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        boolean isTalismanWord = username != null && (username.equalsIgnoreCase("talisman") || username.equalsIgnoreCase("talismans"));
        this.targetPlayer = (!isTalismanWord && username != null && !username.trim().isEmpty()) ? username.trim() :
                (mc.player != null ? mc.player.getScoreboardName() : (mc.getUser() != null ? mc.getUser().getName() : "me"));
        this.currentTab = isTalismanWord ? Tab.TALISMANS : (initialTab != null ? initialTab : Tab.UPGRADES);
    }

    @Override
    protected void init() {
        super.init();
        clickActions.clear();
        modalActions.clear();

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
        talismanChains.clear();
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
            this.currentMp = c.has("current") ? c.get("current").getAsInt() : (c.has("reported") ? c.get("reported").getAsInt() : 0);
            this.maxMp = c.has("max") ? c.get("max").getAsInt() : 2184;
        } else if (root.has("magicalPower") && root.get("magicalPower").isJsonObject()) {
            JsonObject mpo = root.getAsJsonObject("magicalPower");
            this.currentMp = mpo.has("reported") ? mpo.get("reported").getAsInt() : (mpo.has("computed") ? mpo.get("computed").getAsInt() : 0);
        } else if (root.has("mp") && root.get("mp").isJsonObject()) {
            JsonObject mpo = root.getAsJsonObject("mp");
            this.currentMp = mpo.has("reported") ? mpo.get("reported").getAsInt() : (mpo.has("computed") ? mpo.get("computed").getAsInt() : 0);
        } else if (root.has("total_magical_power")) {
            this.currentMp = root.get("total_magical_power").getAsInt();
        }

        // If player has max obtainable MP (2184)
        if (this.currentMp >= 2184) {
            this.maxMp = this.currentMp;
        }

        // Selected Power & Tuning
        if (root.has("tuning") && root.get("tuning").isJsonObject()) {
            JsonObject t = root.getAsJsonObject("tuning");
            this.tuningPoints = t.has("points") ? t.get("points").getAsInt() : 0;
        }
        if (root.has("selectedPower") && !root.get("selectedPower").isJsonNull()) {
            this.selectedPower = root.get("selectedPower").getAsString();
        } else if (root.has("selected_power") && !root.get("selected_power").isJsonNull()) {
            this.selectedPower = root.get("selected_power").getAsString();
        } else if (root.has("bag") && root.get("bag").isJsonObject() && root.getAsJsonObject("bag").has("selectedPower") && !root.getAsJsonObject("bag").get("selectedPower").isJsonNull()) {
            this.selectedPower = root.getAsJsonObject("bag").get("selectedPower").getAsString();
        } else if (root.has("accessories") && root.get("accessories").isJsonObject()) {
            JsonObject acc = root.getAsJsonObject("accessories");
            this.selectedPower = acc.has("selectedPower") && !acc.get("selectedPower").isJsonNull() ? acc.get("selectedPower").getAsString() : "None";
        }

        if (this.selectedPower == null || this.selectedPower.trim().isEmpty() || this.selectedPower.equalsIgnoreCase("none")) {
            this.selectedPower = "Fortuitous";
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

                String source = o.has("source") ? o.get("source").getAsString() : "ah";
                String uName = name.toUpperCase(Locale.ROOT);
                JsonObject recipe = o.has("recipe") && o.get("recipe").isJsonObject() ? o.getAsJsonObject("recipe") : null;
                JsonObject craftDetails = o.has("craftDetails") && o.get("craftDetails").isJsonObject() ? o.getAsJsonObject("craftDetails") : null;

                if (source.equals("ah")) {
                    if (uName.contains("KUUDRA") || uName.contains("BURNING")) source = "kuudra";
                    else if (uName.contains("HEGEMONY") || uName.contains("ENDER ARTIFACT") || uName.contains("DARK AUCTION")) source = "dark_auction";
                    else if (uName.contains("RIFT") || uName.contains("PRISM")) source = "rift";
                    else if (uName.contains("CAMPFIRE") || uName.contains("ROMERO") || uName.contains("SEAL OF")) source = "quest";
                    else if (recipe != null) source = "craft";
                }
                String recipeText = o.has("craftText") ? o.get("craftText").getAsString() : "";

                upgrades.add(new UpgradeEntry(rank++, name, rarity, kind, from, cost, gain, coinsPerMp, purchasable, source, recipeText, recipe, craftDetails));
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
                String source = o.has("source") ? o.get("source").getAsString() : "ah";
                String uName = name.toUpperCase(Locale.ROOT);
                JsonObject recipe = o.has("recipe") && o.get("recipe").isJsonObject() ? o.getAsJsonObject("recipe") : null;
                JsonObject craftDetails = o.has("craftDetails") && o.get("craftDetails").isJsonObject() ? o.getAsJsonObject("craftDetails") : null;

                if (source.equals("ah")) {
                    if (uName.contains("KUUDRA") || uName.contains("BURNING")) source = "kuudra";
                    else if (uName.contains("HEGEMONY") || uName.contains("ENDER ARTIFACT") || uName.contains("DARK AUCTION")) source = "dark_auction";
                    else if (uName.contains("RIFT") || uName.contains("PRISM")) source = "rift";
                    else if (uName.contains("CAMPFIRE") || uName.contains("ROMERO") || uName.contains("SEAL OF")) source = "quest";
                    else if (recipe != null) source = "craft";
                }
                String recipeText = o.has("craftText") ? o.get("craftText").getAsString() : "";
                missing.add(new UpgradeEntry(rank++, name, rarity, "missing", "", cost, gain, 0, true, source, recipeText, recipe, craftDetails));
            }
        }

        // Talisman Chains (Tree)
        if (root.has("talismanChains") && root.get("talismanChains").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("talismanChains");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject co = el.getAsJsonObject();
                String cName = co.has("name") ? co.get("name").getAsString() : "Unknown";
                String cKey = co.has("key") ? co.get("key").getAsString() : "";
                String cWiki = co.has("wiki") ? co.get("wiki").getAsString() : "";
                List<TalismanTier> tiers = new ArrayList<>();
                if (co.has("tiers") && co.get("tiers").isJsonArray()) {
                    for (JsonElement tel : co.getAsJsonArray("tiers")) {
                        if (!tel.isJsonObject()) continue;
                        JsonObject to = tel.getAsJsonObject();
                        String tid = to.has("id") ? to.get("id").getAsString() : "";
                        String tname = to.has("name") ? to.get("name").getAsString() : tid;
                        String trarity = to.has("rarity") ? to.get("rarity").getAsString() : (to.has("tier") ? to.get("tier").getAsString() : "COMMON");
                        int tap = to.has("ap") ? to.get("ap").getAsInt() : 0;
                        long tprice = to.has("price") ? to.get("price").getAsLong() : 0L;
                        boolean towned = to.has("owned") && to.get("owned").getAsBoolean();
                        JsonObject trecipe = to.has("recipe") && to.get("recipe").isJsonObject() ? to.getAsJsonObject("recipe") : null;
                        JsonObject tcraftDetails = to.has("craftDetails") && to.get("craftDetails").isJsonObject() ? to.getAsJsonObject("craftDetails") : null;
                        long tcraftCost = to.has("craftCost") ? to.get("craftCost").getAsLong() : 0L;
                        tiers.add(new TalismanTier(tid, tname, trarity, tap, tprice, towned, trecipe, tcraftDetails, tcraftCost));
                    }
                }
                if (!tiers.isEmpty()) {
                    talismanChains.add(new TalismanChain(cName, cKey, cWiki, tiers));
                }
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

    public void openCraftModal(String itemName, String rarity, JsonObject recipe, JsonObject craftDetails) {
        if (recipe == null && craftDetails == null) return;
        CraftModalState modal = new CraftModalState();
        modal.itemName = itemName;
        modal.rarity = rarity;
        if (craftDetails != null && craftDetails.has("grid") && craftDetails.get("grid").isJsonObject()) {
            modal.grid = craftDetails.getAsJsonObject("grid");
        } else {
            modal.grid = recipe;
        }

        if (craftDetails != null) {
            modal.canCraftNow = craftDetails.has("canCraftNow") && craftDetails.get("canCraftNow").getAsBoolean();
            modal.totalMissingCost = craftDetails.has("totalMissingCost") ? craftDetails.get("totalMissingCost").getAsLong() : 0L;

            if (craftDetails.has("ingredients") && craftDetails.get("ingredients").isJsonArray()) {
                for (JsonElement el : craftDetails.getAsJsonArray("ingredients")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject io = el.getAsJsonObject();
                    String id = io.has("id") ? io.get("id").getAsString() : "";
                    String name = io.has("name") ? io.get("name").getAsString() : id;
                    int needed = io.has("needed") ? io.get("needed").getAsInt() : 1;
                    int owned = io.has("owned") ? io.get("owned").getAsInt() : 0;
                    int missing = io.has("missing") ? io.get("missing").getAsInt() : 0;
                    long unitPrice = io.has("unitPrice") ? io.get("unitPrice").getAsLong() : 0L;
                    boolean hasEnough = io.has("hasEnough") && io.get("hasEnough").getAsBoolean();
                    modal.ingredients.add(new CraftIngredient(id, name, needed, owned, missing, unitPrice, hasEnough));
                }
            }

            if (craftDetails.has("missingToBuy") && craftDetails.get("missingToBuy").isJsonArray()) {
                for (JsonElement el : craftDetails.getAsJsonArray("missingToBuy")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject io = el.getAsJsonObject();
                    String id = io.has("id") ? io.get("id").getAsString() : "";
                    String name = io.has("name") ? io.get("name").getAsString() : id;
                    int needed = io.has("needed") ? io.get("needed").getAsInt() : 1;
                    long unitPrice = io.has("unitPrice") ? io.get("unitPrice").getAsLong() : 0L;
                    modal.missingToBuy.add(new CraftIngredient(id, name, needed, 0, needed, unitPrice, false));
                }
            }
        } else if (modal.grid != null) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String key : modal.grid.keySet()) {
                JsonElement gel = modal.grid.get(key);
                if (gel.isJsonObject()) {
                    JsonObject go = gel.getAsJsonObject();
                    String gid = go.has("id") ? go.get("id").getAsString() : "";
                    int gcnt = go.has("count") ? go.get("count").getAsInt() : 1;
                    if (!gid.isEmpty()) {
                        counts.put(gid, counts.getOrDefault(gid, 0) + gcnt);
                    }
                }
            }
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                modal.ingredients.add(new CraftIngredient(entry.getKey(), entry.getKey(), entry.getValue(), 0, entry.getValue(), 0L, false));
            }
            modal.canCraftNow = false;
        }

        this.activeModal = modal;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int winW = Math.min(this.width - 24, 720);
        int winH = Math.min(this.height - 24, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int bg = ConfigUITheme.getMainWindowBg();
        int border = ConfigUITheme.getBorderColor();
        int accent = 0xFFC084FC; // Purple accent

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
        String mpVal = currentMp >= 2184
                ? "§d§l✨ " + formatInt(currentMp) + " §a(MAX)"
                : "§d§l✨ " + formatInt(currentMp) + " §7/ " + formatInt(maxMp);
        drawStatCard(g, font, winX + 16, heroY, statW, 24, "MAGICAL POWER", mpVal);
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
        tabX += font.width("Cheapest Upgrades (" + upgrades.size() + ")") + 20;

        drawTabPill(g, font, tabX, tabY, "Missing (" + missing.size() + ")", currentTab == Tab.MISSING, mouseX, mouseY);
        tabX += font.width("Missing (" + missing.size() + ")") + 20;

        drawTabPill(g, font, tabX, tabY, "Talismans (" + talismanChains.size() + ")", currentTab == Tab.TALISMANS, mouseX, mouseY);
        tabX += font.width("Talismans (" + talismanChains.size() + ")") + 20;

        drawTabPill(g, font, tabX, tabY, "Breakdown", currentTab == Tab.BREAKDOWN, mouseX, mouseY);

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
        } else if (currentTab == Tab.TALISMANS) {
            renderTalismansTab(g, font, contentX, contentY, contentW, contentH, mouseX, mouseY);
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

        // Render Active Crafting Modal if opened
        if (activeModal != null) {
            renderCraftModal(g, font, mouseX, mouseY);
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

                // Action Button (context-aware: /ah, /bz, Craft, Kuudra, Quest)
                int btnW = 54;
                int btnH = 18;
                int btnX = x + w - 70;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                String btnText;
                int btnBg;
                int btnBorder;
                Runnable action;
                String actionTitle;

                final String itemName = u.name;
                final String itemRarity = u.rarity;
                final JsonObject rec = u.recipe;
                final JsonObject cd = u.craftDetails;

                if ("craft".equalsIgnoreCase(u.source) || rec != null || cd != null) {
                    btnText = "§e🔨 Craft";
                    btnBg = btnHov ? 0xFFB45309 : 0xFF78350F;
                    btnBorder = 0xFFD97706;
                    action = () -> openCraftModal(itemName, itemRarity, rec, cd);
                    actionTitle = "View 3x3 Recipe & Crafting Queue for " + itemName;
                } else if ("bz".equalsIgnoreCase(u.source)) {
                    btnText = "§f⚡ /bz";
                    btnBg = btnHov ? 0xFF10B981 : 0xFF065F46;
                    btnBorder = 0xFF047857;
                    action = () -> triggerBazaarSearch(itemName);
                    actionTitle = "Open Bazaar for " + itemName;
                } else if ("kuudra".equalsIgnoreCase(u.source)) {
                    btnText = "§6🔥 Kuudra";
                    btnBg = btnHov ? 0xFFEA580C : 0xFF9A3412;
                    btnBorder = 0xFFF97316;
                    action = () -> triggerAhSearch(itemName);
                    actionTitle = "Kuudra Drop — Search AH for " + itemName;
                } else if ("dark_auction".equalsIgnoreCase(u.source)) {
                    btnText = "§d👑 DA/AH";
                    btnBg = btnHov ? 0xFF9333EA : 0xFF6B21A8;
                    btnBorder = 0xFFA855F7;
                    action = () -> triggerAhSearch(itemName);
                    actionTitle = "Dark Auction — Search AH for " + itemName;
                } else if ("quest".equalsIgnoreCase(u.source) || "rift".equalsIgnoreCase(u.source)) {
                    btnText = "§b📍 Quest";
                    btnBg = btnHov ? 0xFF0284C7 : 0xFF075985;
                    btnBorder = 0xFF0EA5E9;
                    action = () -> Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + itemName.replace(" ", "_"));
                    actionTitle = "Special Item — Open Wiki Guide for " + itemName;
                } else {
                    btnText = "§b⚡ /ah";
                    btnBg = btnHov ? 0xFF2563EB : 0xFF1E40AF;
                    btnBorder = 0xFF3B82F6;
                    action = () -> triggerAhSearch(itemName);
                    actionTitle = "Open Auction House for " + itemName;
                }

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                g.outline(btnX, btnY, btnW, btnH, btnBorder);
                g.centeredText(font, btnText, btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);

                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, action, actionTitle));
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

                // Action Button
                int btnW = 54;
                int btnH = 16;
                int btnX = x + w - 70;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                String btnText;
                int btnBg;
                int btnBorder;
                Runnable action;
                String actionTitle;

                final String itemName = u.name;
                final String itemRarity = u.rarity;
                final JsonObject rec = u.recipe;
                final JsonObject cd = u.craftDetails;

                if ("craft".equalsIgnoreCase(u.source) || rec != null || cd != null) {
                    btnText = "§e🔨 Craft";
                    btnBg = btnHov ? 0xFFB45309 : 0xFF78350F;
                    btnBorder = 0xFFD97706;
                    action = () -> openCraftModal(itemName, itemRarity, rec, cd);
                    actionTitle = "View Craft Recipe for " + itemName;
                } else if ("bz".equalsIgnoreCase(u.source)) {
                    btnText = "§f⚡ /bz";
                    btnBg = btnHov ? 0xFF10B981 : 0xFF065F46;
                    btnBorder = 0xFF047857;
                    action = () -> triggerBazaarSearch(itemName);
                    actionTitle = "Open Bazaar for " + itemName;
                } else if ("kuudra".equalsIgnoreCase(u.source)) {
                    btnText = "§6🔥 Kuudra";
                    btnBg = btnHov ? 0xFFEA580C : 0xFF9A3412;
                    btnBorder = 0xFFF97316;
                    action = () -> triggerAhSearch(itemName);
                    actionTitle = "Kuudra Drop — Search AH for " + itemName;
                } else if ("dark_auction".equalsIgnoreCase(u.source)) {
                    btnText = "§d👑 DA/AH";
                    btnBg = btnHov ? 0xFF9333EA : 0xFF6B21A8;
                    btnBorder = 0xFFA855F7;
                    action = () -> triggerAhSearch(itemName);
                    actionTitle = "Dark Auction — Search AH for " + itemName;
                } else if ("quest".equalsIgnoreCase(u.source) || "rift".equalsIgnoreCase(u.source)) {
                    btnText = "§b📍 Quest";
                    btnBg = btnHov ? 0xFF0284C7 : 0xFF075985;
                    btnBorder = 0xFF0EA5E9;
                    action = () -> Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + itemName.replace(" ", "_"));
                    actionTitle = "Special Item — Open Wiki Guide for " + itemName;
                } else {
                    btnText = "§b⚡ /ah";
                    btnBg = btnHov ? 0xFF2563EB : 0xFF1E40AF;
                    btnBorder = 0xFF3B82F6;
                    action = () -> triggerAhSearch(itemName);
                    actionTitle = "Open Auction House for " + itemName;
                }

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                g.outline(btnX, btnY, btnW, btnH, btnBorder);
                g.centeredText(font, btnText, btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);

                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, action, actionTitle));
                }
            }
            curY += itemH + 4;
        }
    }

    private void renderTalismansTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        List<TalismanChain> filtered = new ArrayList<>();
        for (TalismanChain c : talismanChains) {
            if (searchFilter.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(searchFilter)) {
                filtered.add(c);
            } else {
                boolean matchTier = false;
                for (TalismanTier t : c.tiers) {
                    if (t.name.toLowerCase(Locale.ROOT).contains(searchFilter) || t.rarity.toLowerCase(Locale.ROOT).contains(searchFilter)) {
                        matchTier = true;
                        break;
                    }
                }
                if (matchTier) filtered.add(c);
            }
        }

        if (filtered.isEmpty()) {
            g.centeredText(font, talismanChains.isEmpty() ? "§eLoading talisman progression tree..." : "§7No talisman chains matching '§f" + searchFilter + "§7'", x + w / 2, y + 40, 0xFFA0AEC0);
            this.maxScroll = 0;
            return;
        }

        int chainH = 34;
        int totalH = filtered.size() * (chainH + 6);
        this.maxScroll = Math.max(0, totalH - h);

        int curY = y - (int) scrollAmount;
        for (TalismanChain c : filtered) {
            if (curY + chainH >= y && curY <= y + h) {
                // Background row
                g.fill(x, curY, x + w - 10, curY + chainH, 0xFF111827);
                g.outline(x, curY, w - 10, chainH, 0xFF1F2937);

                // Left: Chain name
                g.text(font, c.name, x + 8, curY + 6, 0xFFFFFFFF, true);
                g.text(font, "§8" + c.tiers.size() + " tiers", x + 8, curY + 18, 0xFF6B7280, false);

                // Right: Tier pills connected by arrows
                int pillX = x + 130;
                for (int i = 0; i < c.tiers.size(); i++) {
                    TalismanTier t = c.tiers.get(i);
                    int pillW = Math.max(88, font.width(t.name) + 16);
                    int pillH = 22;
                    int pillY = curY + 6;

                    boolean pillHov = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH;
                    int pBg = t.owned ? (pillHov ? 0xFF065F46 : 0xFF064E3B) : (pillHov ? 0xFF334155 : 0xFF1E293B);
                    int pBorder = t.owned ? (pillHov ? 0xFF34D399 : 0xFF059669) : (pillHov ? 0xFF60A5FA : 0xFF334155);

                    g.fill(pillX, pillY, pillX + pillW, pillY + pillH, pBg);
                    g.outline(pillX, pillY, pillW, pillH, pBorder);

                    int col = getRarityColor(t.rarity);
                    String topLabel = (t.owned ? "§a✔ " : "§7") + t.name;
                    g.text(font, topLabel, pillX + 6, pillY + 3, col, false);

                    String botLabel = t.owned ? "§aOWNED §8(+" + t.ap + " MP)" : "§6" + formatCoins(t.price) + " §8(+" + t.ap + ")";
                    g.text(font, botLabel, pillX + 6, pillY + 12, 0xFFD1D5DB, false);

                    // Click action on pill: opens recipe modal if has recipe/craftDetails, else searches ah
                    final String tierName = t.name;
                    final String tierRarity = t.rarity;
                    final JsonObject tierRec = t.recipe;
                    final JsonObject tierCd = t.craftDetails;
                    final long tierPrice = t.price;

                    Runnable tierAction = () -> {
                        if (tierRec != null || tierCd != null) {
                            openCraftModal(tierName, tierRarity, tierRec, tierCd);
                        } else if (tierPrice > 0) {
                            triggerAhSearch(tierName);
                        } else {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + tierName.replace(" ", "_"));
                        }
                    };

                    synchronized (clickActions) {
                        clickActions.add(new ClickableAction(pillX, pillY, pillX + pillW, pillY + pillH, tierAction, "Click to view " + tierName + " Recipe & Price"));
                    }

                    pillX += pillW + 4;
                    if (i < c.tiers.size() - 1) {
                        g.text(font, "§7➔", pillX, curY + 12, 0xFF6B7280, false);
                        pillX += 14;
                    }
                }
            }
            curY += chainH + 6;
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

    private void renderCraftModal(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        synchronized (modalActions) {
            modalActions.clear();
        }

        // Dark dim backdrop
        g.fill(0, 0, this.width, this.height, 0xA0000000);

        int mW = 400;
        int mH = 265;
        int mX = (this.width - mW) / 2;
        int mY = (this.height - mH) / 2;

        // Modal Frame
        g.fill(mX, mY, mX + mW, mY + mH, 0xFF0F172A);
        g.outline(mX, mY, mW, mH, 0xFF3B82F6);
        g.fill(mX, mY, mX + mW, mY + 3, 0xFF60A5FA);

        // Header Title
        int rColor = getRarityColor(activeModal.rarity);
        g.text(font, "§e🔨 Crafting Recipe: §r" + activeModal.itemName, mX + 14, mY + 12, rColor, true);

        // Close button
        int closeX = mX + mW - 24;
        int closeY = mY + 10;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + 16 && mouseY >= closeY && mouseY <= closeY + 14;
        g.fill(closeX, closeY, closeX + 16, closeY + 14, closeHov ? 0xFFEF4444 : 0xFF334155);
        g.outline(closeX, closeY, 16, 14, 0xFF64748B);
        g.centeredText(font, "✕", closeX + 8, closeY + 3, 0xFFFFFFFF);
        modalActions.add(new ClickableAction(closeX, closeY, closeX + 16, closeY + 14, () -> this.activeModal = null, "Close Recipe"));

        // Left: 3x3 Crafting Grid
        int gx = mX + 16;
        int gy = mY + 34;
        int slotSize = 28;
        String[] rowKeys = {"A", "B", "C"};
        String[] colKeys = {"1", "2", "3"};

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int sx = gx + c * (slotSize + 3);
                int sy = gy + r * (slotSize + 3);
                String slotKey = rowKeys[r] + colKeys[c];

                JsonObject slotItem = null;
                if (activeModal.grid != null && activeModal.grid.has(slotKey) && activeModal.grid.get(slotKey).isJsonObject()) {
                    slotItem = activeModal.grid.getAsJsonObject(slotKey);
                }

                boolean slotHov = mouseX >= sx && mouseX <= sx + slotSize && mouseY >= sy && mouseY <= sy + slotSize;
                g.fill(sx, sy, sx + slotSize, sy + slotSize, slotHov ? 0xFF1E293B : 0xFF090D16);
                g.outline(sx, sy, slotSize, slotSize, slotHov ? 0xFF60A5FA : 0xFF334155);

                if (slotItem != null) {
                    String id = slotItem.has("id") ? slotItem.get("id").getAsString() : "";
                    int count = slotItem.has("count") ? slotItem.get("count").getAsInt() : 1;
                    String abbrev = id.length() > 4 ? id.substring(0, 4) : id;
                    g.centeredText(font, "§f" + abbrev, sx + slotSize / 2, sy + 6, 0xFFFFFFFF);
                    if (count > 1) {
                        g.text(font, "§e" + count, sx + slotSize - font.width(String.valueOf(count)) - 2, sy + slotSize - 9, 0xFFFDE047, false);
                    }
                }
            }
        }

        // Right: Required Ingredients List
        int ix = mX + 120;
        int iy = mY + 34;
        int iw = mW - 136;

        g.text(font, "§b§lRequired Ingredients:", ix, iy, 0xFF38BDF8, false);
        iy += 14;

        for (CraftIngredient ing : activeModal.ingredients) {
            g.fill(ix, iy, ix + iw, iy + 22, 0xFF1E293B);
            g.outline(ix, iy, iw, 22, ing.hasEnough ? 0xFF059669 : 0xFF475569);

            g.text(font, "§f" + ing.name, ix + 6, iy + 3, 0xFFFFFFFF, false);
            String qtyStr = "§7Need: §e" + ing.needed + " §8| §7Have: " + (ing.hasEnough ? "§a" : "§c") + ing.owned;
            g.text(font, qtyStr, ix + 6, iy + 12, 0xFFCBD5E1, false);

            if (ing.hasEnough) {
                g.text(font, "§a✔ OK", ix + iw - 32, iy + 7, 0xFF10B981, true);
            } else {
                g.text(font, "§c-" + ing.missing, ix + iw - 32, iy + 7, 0xFFEF4444, true);
            }
            iy += 26;
        }

        // Status Banner
        int bannerY = mY + 168;
        if (activeModal.canCraftNow) {
            g.fill(mX + 16, bannerY, mX + mW - 16, bannerY + 24, 0xFF064E3B);
            g.outline(mX + 16, bannerY, mW - 32, 24, 0xFF10B981);
            g.centeredText(font, "§a§l✔ Already have enough mats in bags & sacks to craft!", mX + mW / 2, bannerY + 8, 0xFFFFFFFF);
        } else {
            g.fill(mX + 16, bannerY, mX + mW - 16, bannerY + 24, 0xFF78350F);
            g.outline(mX + 16, bannerY, mW - 32, 24, 0xFFD97706);
            String missingBanner = "§eMissing materials! Est. Cost: §6" + formatCoins(activeModal.totalMissingCost);
            g.centeredText(font, missingBanner, mX + mW / 2, bannerY + 8, 0xFFFFFFFF);
        }

        // Action Buttons
        int btnY = mY + 204;
        int btnW = 180;
        int btnH = 22;
        int btnX = mX + 16;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

        if (activeModal.canCraftNow) {
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0xFF059669 : 0xFF047857);
            g.outline(btnX, btnY, btnW, btnH, 0xFF10B981);
            g.centeredText(font, "§f§l⚡ Open /recipe", btnX + btnW / 2, btnY + 7, 0xFFFFFFFF);

            modalActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.connection.sendCommand("recipe " + activeModal.itemName);
                    mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aOpened recipe for §e" + activeModal.itemName));
                }
                this.activeModal = null;
            }, "Open /recipe in-game"));
        } else {
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0xFFB45309 : 0xFF78350F);
            g.outline(btnX, btnY, btnW, btnH, 0xFFD97706);
            g.centeredText(font, "§f§l🛒 Add to Queue / Buy Missing", btnX + btnW / 2, btnY + 7, 0xFFFFFFFF);

            modalActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && !activeModal.missingToBuy.isEmpty()) {
                    CraftIngredient first = activeModal.missingToBuy.get(0);
                    try {
                        mc.keyboardHandler.setClipboard(String.valueOf(first.needed));
                    } catch (Exception ignored) {}
                    mc.player.connection.sendCommand("bz " + first.name);
                    mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aOpened Bazaar for §e" + first.name + "§a. Copied quantity (§b" + first.needed + "§a) to clipboard!"));
                }
                this.activeModal = null;
            }, "Open Bazaar for first missing ingredient and copy quantity"));
        }

        // Close Modal Button
        int cBtnW = 80;
        int cBtnX = mX + mW - cBtnW - 16;
        boolean cBtnHov = mouseX >= cBtnX && mouseX <= cBtnX + cBtnW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(cBtnX, btnY, cBtnX + cBtnW, btnY + btnH, cBtnHov ? 0xFF475569 : 0xFF334155);
        g.outline(cBtnX, btnY, cBtnW, btnH, 0xFF64748B);
        g.centeredText(font, "Close", cBtnX + cBtnW / 2, btnY + 7, 0xFFFFFFFF);
        modalActions.add(new ClickableAction(cBtnX, btnY, cBtnX + cBtnW, btnY + btnH, () -> this.activeModal = null, "Close Modal"));
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
        int w = font.width(label) + 14;
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

        // If Modal is active, intercept clicks exclusively
        if (activeModal != null) {
            synchronized (modalActions) {
                for (ClickableAction action : modalActions) {
                    if (mouseX >= action.minX && mouseX <= action.maxX && mouseY >= action.minY && mouseY <= action.maxY) {
                        action.action.run();
                        return true;
                    }
                }
            }
            int mW = 400;
            int mH = 265;
            int mX = (this.width - mW) / 2;
            int mY = (this.height - mH) / 2;
            if (mouseX < mX || mouseX > mX + mW || mouseY < mY || mouseY > mY + mH) {
                this.activeModal = null;
            }
            return true;
        }

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
        int tab1W = font.width("Cheapest Upgrades (" + upgrades.size() + ")") + 14;
        if (mouseX >= tabX && mouseX <= tabX + tab1W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.UPGRADES;
            scrollAmount = 0;
            return true;
        }
        tabX += tab1W + 6;
        int tab2W = font.width("Missing (" + missing.size() + ")") + 14;
        if (mouseX >= tabX && mouseX <= tabX + tab2W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.MISSING;
            scrollAmount = 0;
            return true;
        }
        tabX += tab2W + 6;
        int tab3W = font.width("Talismans (" + talismanChains.size() + ")") + 14;
        if (mouseX >= tabX && mouseX <= tabX + tab3W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.TALISMANS;
            scrollAmount = 0;
            return true;
        }
        tabX += tab3W + 6;
        int tab4W = font.width("Breakdown") + 14;
        if (mouseX >= tabX && mouseX <= tabX + tab4W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.BREAKDOWN;
            scrollAmount = 0;
            return true;
        }

        // Clickable actions
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
        if (activeModal != null) return true;
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
            if (this.activeModal != null) {
                this.activeModal = null;
                return true;
            }
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

    private void triggerAhSearch(String itemName) {
        if (itemName == null || itemName.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        try {
            mc.keyboardHandler.setClipboard(itemName);
        } catch (Exception ignored) {}

        mc.player.connection.sendCommand("ah " + itemName);
        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §bAuction House: §e" + itemName + " §7(Copied name to clipboard)"));
        this.onClose();
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
