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
import me.bombo.bomboaddons.SkyblockItemManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
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
import java.util.function.Consumer;

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

    private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

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
        ENRICHMENTS,
        BREAKDOWN
    }

    private Tab currentTab = Tab.UPGRADES;

    public static class UpgradeEntry {
        public final int rank;
        public final String name;
        public final String rarity;
        public final String kind; // "buy", "upgrade", or "recomb"
        public final String from;
        public final long cost;
        public final int mpGain;
        public final double coinsPerMp;
        public final boolean purchasable;
        public final String source;
        public final String recipeText;
        public final JsonObject recipe;
        public final JsonObject craftDetails;
        public final String targetItem;

        public UpgradeEntry(int rank, String name, String rarity, String kind, String from, long cost, int mpGain,
                            double coinsPerMp, boolean purchasable, String source, String recipeText,
                            JsonObject recipe, JsonObject craftDetails, String targetItem) {
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
            this.targetItem = targetItem != null ? targetItem : name;
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
        public final String status; // "ACTIVE", "INACTIVE", "UNOWNED"
        public final List<String> lore;
        public final JsonObject recipe;
        public final JsonObject craftDetails;
        public final long craftCost;

        public TalismanTier(String id, String name, String rarity, int ap, long price, boolean owned,
                            String status, List<String> lore, JsonObject recipe, JsonObject craftDetails, long craftCost) {
            this.id = id;
            this.name = name;
            this.rarity = rarity != null ? rarity.toUpperCase(Locale.ROOT) : "COMMON";
            this.ap = ap;
            this.price = price;
            this.owned = owned;
            this.status = status != null ? status : (owned ? "ACTIVE" : "UNOWNED");
            this.lore = lore != null ? lore : new ArrayList<>();
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

    public static class EnrichmentWarning {
        public final String type;
        public final String title;
        public final String description;

        public EnrichmentWarning(String type, String title, String description) {
            this.type = type;
            this.title = title;
            this.description = description;
        }
    }

    public static class MissingEnrichmentEntry {
        public final String id;
        public final String name;
        public final String rarity;
        public final long price;

        public MissingEnrichmentEntry(String id, String name, String rarity, long price) {
            this.id = id;
            this.name = name;
            this.rarity = rarity != null ? rarity.toUpperCase(Locale.ROOT) : "LEGENDARY";
            this.price = price;
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
        public String itemId = "";
        public String rarity;
        public JsonObject grid; // A1..C3
        public List<CraftIngredient> ingredients = new ArrayList<>();
        public boolean canCraftNow;
        public List<CraftIngredient> missingToBuy = new ArrayList<>();
        public long totalMissingCost;
        public long craftCost;
    }

    private final List<UpgradeEntry> upgrades = new ArrayList<>();
    private final List<UpgradeEntry> missing = new ArrayList<>();
    private final List<TalismanChain> talismanChains = new ArrayList<>();
    private final List<RarityBreakdownEntry> breakdowns = new ArrayList<>();
    private final List<EnrichmentWarning> enrichmentWarnings = new ArrayList<>();
    private final List<MissingEnrichmentEntry> missingEnrichments = new ArrayList<>();
    private int enrichedCount = 0;
    private int eligibleEnrichmentsCount = 0;
    private long enrichmentMinCost = 0L;

    private CraftModalState activeModal = null;

    private EditBox searchBox;
    private String searchFilter = "";

    public static class ClickableAction {
        int minX, minY, maxX, maxY;
        java.util.function.Consumer<Integer> clickAction;
        String tooltip;
        List<Component> richTooltip;

        ClickableAction(int minX, int minY, int maxX, int maxY, Runnable action, String tooltip) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.clickAction = (btn) -> { if (btn == 0 && action != null) action.run(); };
            this.tooltip = tooltip;
            this.richTooltip = null;
        }

        ClickableAction(int minX, int minY, int maxX, int maxY, java.util.function.Consumer<Integer> action, String tooltip, List<Component> richTooltip) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.clickAction = action;
            this.tooltip = tooltip;
            this.richTooltip = richTooltip;
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
        this.searchBox = new EditBox(this.font, winX + winW - 225, winY + 10, 85, 16, Component.literal("Search"));
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

    private static String optString(JsonObject obj, String key, String def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return def;
        try {
            return el.getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static int optInt(JsonObject obj, String key, int def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return def;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static long optLong(JsonObject obj, String key, long def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return def;
        try {
            return el.getAsLong();
        } catch (Exception e) {
            return def;
        }
    }

    private static double optDouble(JsonObject obj, String key, double def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return def;
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean optBoolean(JsonObject obj, String key, boolean def) {
        if (obj == null || !obj.has(key)) return def;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return def;
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }

    private static JsonObject optObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }

    private static JsonArray optArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) return null;
        JsonElement el = obj.get(key);
        return (el != null && el.isJsonArray()) ? el.getAsJsonArray() : null;
    }

    private void parseData(JsonObject root) {
        upgrades.clear();
        missing.clear();
        talismanChains.clear();
        breakdowns.clear();

        JsonObject pl = optObject(root, "player");
        if (pl != null) {
            this.playerName = optString(pl, "name", targetPlayer);
            if (pl.has("profile")) {
                JsonElement profEl = pl.get("profile");
                if (profEl != null && profEl.isJsonObject()) {
                    this.profileName = optString(profEl.getAsJsonObject(), "name", "");
                } else if (profEl != null && profEl.isJsonPrimitive()) {
                    this.profileName = profEl.getAsString();
                } else {
                    this.profileName = "";
                }
            }
        } else {
            this.playerName = targetPlayer;
        }

        // Ceiling & MP
        JsonObject c = optObject(root, "ceiling");
        JsonObject mpo = optObject(root, "magicalPower");
        JsonObject mpObj = optObject(root, "mp");
        if (c != null) {
            this.currentMp = optInt(c, "current", optInt(c, "reported", 0));
            this.maxMp = optInt(c, "max", 2184);
        } else if (mpo != null) {
            this.currentMp = optInt(mpo, "reported", optInt(mpo, "computed", 0));
        } else if (mpObj != null) {
            this.currentMp = optInt(mpObj, "reported", optInt(mpObj, "computed", 0));
        } else if (root.has("total_magical_power")) {
            this.currentMp = optInt(root, "total_magical_power", 0);
        }

        // If player has max obtainable MP (2184)
        if (this.currentMp >= 2184) {
            this.maxMp = this.currentMp;
        }

        // Selected Power & Tuning
        JsonObject t = optObject(root, "tuning");
        if (t != null) {
            this.tuningPoints = optInt(t, "points", 0);
        }
        String p1 = optString(root, "selectedPower", null);
        if (p1 == null) p1 = optString(root, "selected_power", null);
        if (p1 == null) {
            JsonObject bg = optObject(root, "bag");
            if (bg != null) p1 = optString(bg, "selectedPower", null);
        }
        if (p1 == null) {
            JsonObject acc = optObject(root, "accessories");
            if (acc != null) p1 = optString(acc, "selectedPower", null);
        }
        this.selectedPower = (p1 != null && !p1.trim().isEmpty() && !p1.equalsIgnoreCase("none")) ? p1 : "Fortuitous";

        // Slots
        JsonObject sl = optObject(root, "slots");
        if (sl != null) {
            this.bagSlots = optInt(sl, "floor", optInt(sl, "confirmed", 0));
            this.maxBagSlots = optInt(sl, "possibleMax", 281);
        }

        // Upgrades (buyOrder)
        JsonArray buyArr = optArray(root, "buyOrder");
        if (buyArr != null) {
            int rank = 1;
            for (JsonElement el : buyArr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = optString(o, "name", "Unknown");
                String rarity = optString(o, "rarity", "COMMON");
                String kind = optString(o, "kind", "buy");
                String from = optString(o, "from", "");
                long cost = optLong(o, "cost", 0L);
                int gain = optInt(o, "mpGain", optInt(o, "gain", 0));
                double coinsPerMp = optDouble(o, "coinsPerMp", gain > 0 ? (double) cost / gain : 0);
                boolean purchasable = optBoolean(o, "purchasable", true);

                String source = optString(o, "source", "ah");
                String uName = name.toUpperCase(Locale.ROOT);
                JsonObject recipe = optObject(o, "recipe");
                JsonObject craftDetails = optObject(o, "craftDetails");

                if (uName.contains("PANDORA")) {
                    source = "ah";
                    recipe = null;
                    craftDetails = null;
                } else if (source.equals("ah")) {
                    if (uName.contains("KUUDRA") || uName.contains("BURNING")) source = "kuudra";
                    else if (uName.contains("HEGEMONY") || uName.contains("ENDER ARTIFACT") || uName.contains("DARK AUCTION")) source = "dark_auction";
                    else if (uName.contains("RIFT") || uName.contains("PRISM")) source = "rift";
                    else if (uName.contains("CAMPFIRE") || uName.contains("ROMERO") || uName.contains("SEAL OF")) source = "quest";
                    else if (recipe != null) source = "craft";
                }
                String recipeText = optString(o, "craftText", "");

                String targetItem = optString(o, "targetItem", name);
                upgrades.add(new UpgradeEntry(rank++, name, rarity, kind, from, cost, gain, coinsPerMp, purchasable, source, recipeText, recipe, craftDetails, targetItem));
            }
        }

        // Missing Accessories
        JsonArray missArr = optArray(root, "missingAccessories");
        if (missArr != null) {
            int rank = 1;
            for (JsonElement el : missArr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String name = optString(o, "name", "Unknown");
                String rarity = optString(o, "rarity", "COMMON");
                long cost = optLong(o, "cost", 0L);
                int gain = optInt(o, "ap", 0);
                String source = optString(o, "source", "ah");
                String uName = name.toUpperCase(Locale.ROOT);
                JsonObject recipe = optObject(o, "recipe");
                JsonObject craftDetails = optObject(o, "craftDetails");

                if (uName.contains("PANDORA")) {
                    source = "ah";
                    recipe = null;
                    craftDetails = null;
                } else if (source.equals("ah")) {
                    if (uName.contains("KUUDRA") || uName.contains("BURNING")) source = "kuudra";
                    else if (uName.contains("HEGEMONY") || uName.contains("ENDER ARTIFACT") || uName.contains("DARK AUCTION")) source = "dark_auction";
                    else if (uName.contains("RIFT") || uName.contains("PRISM")) source = "rift";
                    else if (uName.contains("CAMPFIRE") || uName.contains("ROMERO") || uName.contains("SEAL OF")) source = "quest";
                    else if (recipe != null) source = "craft";
                }
                String recipeText = optString(o, "craftText", "");
                missing.add(new UpgradeEntry(rank++, name, rarity, "missing", "", cost, gain, 0, true, source, recipeText, recipe, craftDetails, name));
            }
        }

        // Talisman Chains (Tree)
        JsonArray chainArr = optArray(root, "talismanChains");
        if (chainArr != null) {
            for (JsonElement el : chainArr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject co = el.getAsJsonObject();
                String cName = optString(co, "name", "Unknown");
                String cKey = optString(co, "key", "");
                String cWiki = optString(co, "wiki", "");
                List<TalismanTier> tiers = new ArrayList<>();
                JsonArray tiersArr = optArray(co, "tiers");
                if (tiersArr != null) {
                    for (JsonElement tel : tiersArr) {
                        if (tel == null || !tel.isJsonObject()) continue;
                        JsonObject to = tel.getAsJsonObject();
                        String tid = optString(to, "id", "");
                        String tname = optString(to, "name", tid);
                        String trarity = optString(to, "rarity", optString(to, "tier", "COMMON"));
                        int tap = optInt(to, "ap", 0);
                        long tprice = optLong(to, "price", 0L);
                        boolean towned = optBoolean(to, "owned", false);
                        String tstatus = optString(to, "status", towned ? "ACTIVE" : "UNOWNED");
                        List<String> tlore = new ArrayList<>();
                        JsonArray loreArr = optArray(to, "lore");
                        if (loreArr != null) {
                            for (JsonElement le : loreArr) {
                                if (le != null && le.isJsonPrimitive()) tlore.add(le.getAsString());
                            }
                        }
                        JsonObject trecipe = optObject(to, "recipe");
                        JsonObject tcraftDetails = optObject(to, "craftDetails");
                        long tcraftCost = optLong(to, "craftCost", 0L);
                        tiers.add(new TalismanTier(tid, tname, trarity, tap, tprice, towned, tstatus, tlore, trecipe, tcraftDetails, tcraftCost));
                    }
                }
                if (!tiers.isEmpty()) {
                    talismanChains.add(new TalismanChain(cName, cKey, cWiki, tiers));
                }
            }
        }

        // Enrichments
        this.enrichedCount = optInt(root, "enrichedCount", 0);
        this.eligibleEnrichmentsCount = optInt(root, "eligibleEnrichmentsCount", 0);
        JsonObject enrichObj = optObject(root, "enrichments");
        if (enrichObj != null) {
            this.enrichmentMinCost = optLong(enrichObj, "cost", 0L);
            if (this.enrichedCount == 0) this.enrichedCount = optInt(enrichObj, "enrichedCount", 0);
            if (this.eligibleEnrichmentsCount == 0) this.eligibleEnrichmentsCount = optInt(enrichObj, "eligibleCount", 0);
        }

        missingEnrichments.clear();
        JsonArray meArr = optArray(root, "missingEnrichments");
        if (meArr == null && enrichObj != null) meArr = optArray(enrichObj, "missing");
        if (meArr != null) {
            for (JsonElement el : meArr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject eo = el.getAsJsonObject();
                String eid = optString(eo, "id", "");
                String ename = optString(eo, "name", eid);
                String erarity = optString(eo, "rarity", "LEGENDARY");
                long eprice = optLong(eo, "cheapest", enrichmentMinCost);
                missingEnrichments.add(new MissingEnrichmentEntry(eid, ename, erarity, eprice));
            }
        }

        enrichmentWarnings.clear();
        JsonArray ewArr = optArray(root, "enrichmentWarnings");
        if (ewArr == null && enrichObj != null) ewArr = optArray(enrichObj, "warnings");
        if (ewArr != null) {
            for (JsonElement el : ewArr) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject wo = el.getAsJsonObject();
                String wtype = optString(wo, "type", "WARNING");
                String wtitle = optString(wo, "title", "Enrichment Warning");
                String wdesc = optString(wo, "description", "");
                enrichmentWarnings.add(new EnrichmentWarning(wtype, wtitle, wdesc));
            }
        }

        // Bag Breakdown
        JsonObject bb = optObject(root, "bagBreakdown");
        if (bb != null) {
            String[] rarities = {"VERY_SPECIAL", "SPECIAL", "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"};
            for (String r : rarities) {
                JsonObject ro = optObject(bb, r);
                if (ro != null) {
                    int count = optInt(ro, "count", 0);
                    int mpEach = optInt(ro, "mpEach", 0);
                    int tot = optInt(ro, "totalMp", count * mpEach);
                    if (count > 0) {
                        breakdowns.add(new RarityBreakdownEntry(r, count, mpEach, tot));
                    }
                }
            }
        }
    }

    public void openCraftModal(String itemName, String rarity, JsonObject recipe, JsonObject craftDetails) {
        openCraftModal(itemName, itemName, rarity, recipe, craftDetails, 0L);
    }

    public void openCraftModal(String itemId, String itemName, String rarity, JsonObject recipe, JsonObject craftDetails, long craftCost) {
        if (recipe == null && craftDetails == null) return;
        CraftModalState modal = new CraftModalState();
        modal.itemId = itemId != null ? itemId : "";
        modal.itemName = itemName != null ? itemName : itemId;
        modal.rarity = rarity;
        modal.craftCost = craftCost;
        JsonObject gObj = optObject(craftDetails, "grid");
        if (gObj != null) {
            modal.grid = gObj;
        } else {
            modal.grid = recipe;
        }

        if (craftDetails != null) {
            modal.canCraftNow = optBoolean(craftDetails, "canCraftNow", false);
            modal.totalMissingCost = optLong(craftDetails, "totalMissingCost", 0L);

            JsonArray ingArr = optArray(craftDetails, "ingredients");
            if (ingArr != null) {
                for (JsonElement el : ingArr) {
                    if (el == null || !el.isJsonObject()) continue;
                    JsonObject io = el.getAsJsonObject();
                    String id = optString(io, "id", "");
                    String name = optString(io, "name", id);
                    int needed = optInt(io, "needed", 1);
                    int owned = optInt(io, "owned", 0);
                    int missing = optInt(io, "missing", 0);
                    long unitPrice = optLong(io, "unitPrice", 0L);
                    boolean hasEnough = optBoolean(io, "hasEnough", false);
                    modal.ingredients.add(new CraftIngredient(id, name, needed, owned, missing, unitPrice, hasEnough));
                }
            }

            JsonArray missArr = optArray(craftDetails, "missingToBuy");
            if (missArr != null) {
                for (JsonElement el : missArr) {
                    if (el == null || !el.isJsonObject()) continue;
                    JsonObject io = el.getAsJsonObject();
                    String id = optString(io, "id", "");
                    String name = optString(io, "name", id);
                    int needed = optInt(io, "needed", 1);
                    long unitPrice = optLong(io, "unitPrice", 0L);
                    modal.missingToBuy.add(new CraftIngredient(id, name, needed, 0, needed, unitPrice, false));
                }
            }
        } else if (modal.grid != null) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String key : modal.grid.keySet()) {
                JsonElement gel = modal.grid.get(key);
                if (gel != null && gel.isJsonObject()) {
                    JsonObject go = gel.getAsJsonObject();
                    String gid = optString(go, "id", "");
                    int gcnt = optInt(go, "count", 1);
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

        drawTabPill(g, font, tabX, tabY, "Enrichments (" + missingEnrichments.size() + ")", currentTab == Tab.ENRICHMENTS, mouseX, mouseY);
        tabX += font.width("Enrichments (" + missingEnrichments.size() + ")") + 20;

        drawTabPill(g, font, tabX, tabY, "Breakdown", currentTab == Tab.BREAKDOWN, mouseX, mouseY);

        // Search label (in top header bar next to searchBox)
        g.text(font, "§7Search:", winX + winW - 270, winY + 14, 0xFF8B949E, false);

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
        } else if (currentTab == Tab.ENRICHMENTS) {
            renderEnrichmentsTab(g, font, contentX, contentY, contentW, contentH, mouseX, mouseY);
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

        // Render rich hover tooltip if any hovered action has one
        if (activeModal == null) {
            synchronized (clickActions) {
                for (ClickableAction action : clickActions) {
                    if (mouseX >= action.minX && mouseX <= action.maxX && mouseY >= action.minY && mouseY <= action.maxY) {
                        if (action.richTooltip != null && !action.richTooltip.isEmpty()) {
                            g.setTooltipForNextFrame(font, action.richTooltip, java.util.Optional.empty(), mouseX, mouseY);
                        } else if (action.tooltip != null && !action.tooltip.isEmpty()) {
                            g.setTooltipForNextFrame(font, java.util.List.of(Component.literal(action.tooltip)), java.util.Optional.empty(), mouseX, mouseY);
                        }
                        break;
                    }
                }
            }
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
                int rowBg = hovered ? 0x301E293B : 0x1A0F172A;
                int rowBorder = hovered ? 0x8038BDF8 : 0x3038BDF8;

                g.fill(x, curY, x + w - 10, curY + itemH, rowBg);
                g.outline(x, curY, w - 10, itemH, rowBorder);

                // Rank badge
                g.fill(x + 4, curY + 4, x + 26, curY + itemH - 4, 0x4038BDF8);
                g.outline(x + 4, curY + 4, 22, itemH - 8, 0x6038BDF8);
                g.centeredText(font, "§b#" + u.rank, x + 15, curY + 9, 0xFFE0F2FE);

                // Rarity color & Name
                int nameColor = getRarityColor(u.rarity);
                g.text(font, u.name, x + 32, curY + 5, nameColor, true);

                // Subtitle: Kind & From
                String kindLabel;
                if ("recomb".equalsIgnoreCase(u.kind)) {
                    kindLabel = "§dRecombobulate §8(+" + u.mpGain + " MP) · Upgrade Rarity";
                } else if (u.kind.equalsIgnoreCase("upgrade")) {
                    kindLabel = "§eUpgrade from " + (u.from.isEmpty() ? "previous" : u.from);
                } else {
                    kindLabel = "§aDirect Buy / Craft";
                }
                g.text(font, kindLabel, x + 32, curY + 15, 0xFF94A3B8, false);

                // Gain & Cost Per MP
                String gainStr = "§d+" + u.mpGain + " MP";
                g.text(font, gainStr, x + w - 240, curY + 9, 0xFFF472B6, true);

                String effStr = "§7(" + formatCoins(u.coinsPerMp) + "/MP)";
                g.text(font, effStr, x + w - 195, curY + 9, 0xFF9CA3AF, false);

                // Cost
                String costStr = "§6" + formatCoins(u.cost);
                g.text(font, costStr, x + w - 125, curY + 9, 0xFFFBBF24, true);

                // Action Button (context-aware: /ahs, /bz, Craft, Kuudra, Quest)
                int btnW = 56;
                int btnH = 18;
                int btnX = x + w - 72;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                String btnText;
                int btnBg;
                int btnBorder;
                Consumer<Integer> action;
                String actionTitle;

                final String itemName = u.name;
                final String itemRarity = u.rarity;
                final JsonObject rec = u.recipe;
                final JsonObject cd = u.craftDetails;
                final String target = u.targetItem != null && !u.targetItem.isEmpty() ? u.targetItem : itemName;

                if ("recomb".equalsIgnoreCase(u.kind)) {
                    btnText = "§d⚡ /bz";
                    btnBg = btnHov ? 0xCC7E22CE : 0x99581C87;
                    btnBorder = 0xFFA855F7;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/Recombobulator_3000");
                        } else {
                            triggerBazaarSearch("Recombobulator 3000");
                        }
                    };
                    actionTitle = "Recombobulate: Buy Recombobulator 3000 on Bazaar";
                } else if ("craft".equalsIgnoreCase(u.source) || rec != null || cd != null) {
                    btnText = "§e🔨 Craft";
                    btnBg = btnHov ? 0xCCB45309 : 0x9978350F;
                    btnBorder = 0xFFF59E0B;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            openCraftModal(itemName, itemRarity, rec, cd);
                        }
                    };
                    actionTitle = "View 3x3 Recipe & Crafting Queue for " + itemName;
                } else if ("bz".equalsIgnoreCase(u.source)) {
                    btnText = "§f⚡ /bz";
                    btnBg = btnHov ? 0xCC059669 : 0x99065F46;
                    btnBorder = 0xFF10B981;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerBazaarSearch(target);
                        }
                    };
                    actionTitle = "Open Bazaar for " + target;
                } else if ("kuudra".equalsIgnoreCase(u.source)) {
                    btnText = "§6🔥 Kuudra";
                    btnBg = btnHov ? 0xCCEA580C : 0x999A3412;
                    btnBorder = 0xFFF97316;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerAhSearch(target);
                        }
                    };
                    actionTitle = "Kuudra Drop — Search AH for " + target;
                } else if ("dark_auction".equalsIgnoreCase(u.source)) {
                    btnText = "§d👑 DA/AH";
                    btnBg = btnHov ? 0xCC9333EA : 0x996B21A8;
                    btnBorder = 0xFFA855F7;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerAhSearch(target);
                        }
                    };
                    actionTitle = "Dark Auction — Search AH for " + target;
                } else if ("quest".equalsIgnoreCase(u.source) || "rift".equalsIgnoreCase(u.source)) {
                    btnText = "§b📍 Quest";
                    btnBg = btnHov ? 0xCC0284C7 : 0x99075985;
                    btnBorder = 0xFF0EA5E9;
                    action = (btn) -> Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                    actionTitle = "Special Item — Open Wiki Guide for " + target;
                } else {
                    btnText = "§b⚡ /ahs";
                    btnBg = btnHov ? 0xCC2563EB : 0x991E40AF;
                    btnBorder = 0xFF38BDF8;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerAhSearch(target);
                        }
                    };
                    actionTitle = "Search Auction House for " + target;
                }

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                g.outline(btnX, btnY, btnW, btnH, btnBorder);
                g.centeredText(font, btnText, btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);

                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("§f§l" + itemName));
                tip.add(Component.literal("§7" + actionTitle));
                tip.add(Component.literal(""));
                tip.add(Component.literal("§eLeft-Click: §fExecute"));
                tip.add(Component.literal("§bRight-Click: §fOpen SkyBlock Wiki"));

                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(x, curY, x + w - 10, curY + itemH, action, actionTitle, tip));
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
                int rowBg = hovered ? 0x301E293B : 0x1A0F172A;
                int rowBorder = hovered ? 0x8038BDF8 : 0x3038BDF8;

                g.fill(x, curY, x + w - 10, curY + itemH, rowBg);
                g.outline(x, curY, w - 10, itemH, rowBorder);

                int nameColor = getRarityColor(u.rarity);
                g.text(font, u.name, x + 12, curY + 7, nameColor, true);
                g.text(font, "§8[" + u.rarity + "]", x + 12 + font.width(u.name) + 8, curY + 7, 0xFF6B7280, false);

                if (u.mpGain > 0) {
                    g.text(font, "§d+" + u.mpGain + " MP", x + w - 180, curY + 7, 0xFFF472B6, false);
                }

                // Action Button
                int btnW = 56;
                int btnH = 16;
                int btnX = x + w - 72;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                String btnText;
                int btnBg;
                int btnBorder;
                Consumer<Integer> action;
                String actionTitle;

                final String itemName = u.name;
                final String itemRarity = u.rarity;
                final JsonObject rec = u.recipe;
                final JsonObject cd = u.craftDetails;
                final String target = u.targetItem != null && !u.targetItem.isEmpty() ? u.targetItem : itemName;

                if ("craft".equalsIgnoreCase(u.source) || rec != null || cd != null) {
                    btnText = "§e🔨 Craft";
                    btnBg = btnHov ? 0xCCB45309 : 0x9978350F;
                    btnBorder = 0xFFF59E0B;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            openCraftModal(itemName, itemRarity, rec, cd);
                        }
                    };
                    actionTitle = "View Craft Recipe for " + itemName;
                } else if ("bz".equalsIgnoreCase(u.source)) {
                    btnText = "§f⚡ /bz";
                    btnBg = btnHov ? 0xCC059669 : 0x99065F46;
                    btnBorder = 0xFF10B981;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerBazaarSearch(target);
                        }
                    };
                    actionTitle = "Open Bazaar for " + target;
                } else if ("kuudra".equalsIgnoreCase(u.source)) {
                    btnText = "§6🔥 Kuudra";
                    btnBg = btnHov ? 0xCCEA580C : 0x999A3412;
                    btnBorder = 0xFFF97316;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerAhSearch(target);
                        }
                    };
                    actionTitle = "Kuudra Drop — Search AH for " + target;
                } else if ("dark_auction".equalsIgnoreCase(u.source)) {
                    btnText = "§d👑 DA/AH";
                    btnBg = btnHov ? 0xCC9333EA : 0x996B21A8;
                    btnBorder = 0xFFA855F7;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerAhSearch(target);
                        }
                    };
                    actionTitle = "Dark Auction — Search AH for " + target;
                } else if ("quest".equalsIgnoreCase(u.source) || "rift".equalsIgnoreCase(u.source)) {
                    btnText = "§b📍 Quest";
                    btnBg = btnHov ? 0xCC0284C7 : 0x99075985;
                    btnBorder = 0xFF0EA5E9;
                    action = (btn) -> Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                    actionTitle = "Special Item — Open Wiki Guide for " + target;
                } else {
                    btnText = "§b⚡ /ahs";
                    btnBg = btnHov ? 0xCC2563EB : 0x991E40AF;
                    btnBorder = 0xFF38BDF8;
                    action = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + target.replace(" ", "_"));
                        } else {
                            triggerAhSearch(target);
                        }
                    };
                    actionTitle = "Search Auction House for " + target;
                }

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                g.outline(btnX, btnY, btnW, btnH, btnBorder);
                g.centeredText(font, btnText, btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);

                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("§f§l" + itemName));
                tip.add(Component.literal("§7" + actionTitle));
                tip.add(Component.literal(""));
                tip.add(Component.literal("§eLeft-Click: §fExecute"));
                tip.add(Component.literal("§bRight-Click: §fOpen SkyBlock Wiki"));

                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(x, curY, x + w - 10, curY + itemH, action, actionTitle, tip));
                }
            }
            curY += itemH + 4;
        }
    }

    private int getChainCardHeight(Font font, TalismanChain c, int availableW) {
        int startX = 120;
        int curX = startX;
        int rows = 1;
        for (int i = 0; i < c.tiers.size(); i++) {
            TalismanTier t = c.tiers.get(i);
            int pillW = Math.max(80, font.width(t.name) + 14);
            int neededW = pillW + (i < c.tiers.size() - 1 ? 14 : 0);
            if (curX + pillW > availableW && curX > startX) {
                rows++;
                curX = startX;
            }
            curX += neededW + 4;
        }
        return Math.max(34, rows * 26 + 8);
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

        int availableW = w - 16;
        int totalH = 0;
        for (TalismanChain c : filtered) {
            totalH += getChainCardHeight(font, c, availableW) + 6;
        }
        this.maxScroll = Math.max(0, totalH - h);

        int curY = y - (int) scrollAmount;
        for (TalismanChain c : filtered) {
            int cardH = getChainCardHeight(font, c, availableW);
            if (curY + cardH >= y && curY <= y + h) {
                // Background card with liquid-glass aesthetic
                g.fill(x, curY, x + w - 10, curY + cardH, 0x1A0F172A);
                g.outline(x, curY, w - 10, cardH, 0x3038BDF8);

                // Left: Chain name & wiki link
                g.text(font, c.name, x + 8, curY + 6, 0xFFFFFFFF, true);
                g.text(font, "§8" + c.tiers.size() + " tiers", x + 8, curY + 18, 0xFF64748B, false);

                // Right: Wrapped tier pills
                int startX = x + 120;
                int pillX = startX;
                int pillY = curY + 6;

                for (int i = 0; i < c.tiers.size(); i++) {
                    TalismanTier t = c.tiers.get(i);
                    int pillW = Math.max(80, font.width(t.name) + 14);
                    int pillH = 22;

                    if (pillX + pillW > x + availableW && pillX > startX) {
                        pillX = startX;
                        pillY += 26;
                    }

                    boolean pillHov = mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH;

                    int pBg;
                    int pBorder;
                    if ("INACTIVE".equalsIgnoreCase(t.status)) {
                        pBg = pillHov ? 0xCC9A3412 : 0x997C2D12;
                        pBorder = pillHov ? 0xFFFBBF24 : 0xFFF59E0B;
                    } else if (t.owned) {
                        pBg = pillHov ? 0xCC065F46 : 0x99064E3B;
                        pBorder = pillHov ? 0xFF34D399 : 0xFF059669;
                    } else {
                        pBg = pillHov ? 0xCC334155 : 0x991E293B;
                        pBorder = pillHov ? 0xFF60A5FA : 0xFF334155;
                    }

                    g.fill(pillX, pillY, pillX + pillW, pillY + pillH, pBg);
                    g.outline(pillX, pillY, pillW, pillH, pBorder);

                    int col = getRarityColor(t.rarity);
                    String topLabel;
                    if ("INACTIVE".equalsIgnoreCase(t.status)) {
                        topLabel = "§6⚠ " + t.name;
                    } else if (t.owned) {
                        topLabel = "§a✔ " + t.name;
                    } else {
                        topLabel = "§7" + t.name;
                    }
                    g.text(font, topLabel, pillX + 5, pillY + 3, col, false);

                    String botLabel;
                    if ("INACTIVE".equalsIgnoreCase(t.status)) {
                        botLabel = "§6INACTIVE §8(+" + t.ap + ")";
                    } else if (t.owned) {
                        botLabel = "§aOWNED §8(+" + t.ap + " MP)";
                    } else if (t.price > 0) {
                        botLabel = "§6" + formatCoins(t.price) + " §8(+" + t.ap + ")";
                    } else {
                        botLabel = "§7Unowned §8(+" + t.ap + ")";
                    }
                    g.text(font, botLabel, pillX + 5, pillY + 12, 0xFFCBD5E1, false);

                    // Build rich lore tooltip
                    List<Component> richTip = new ArrayList<>();
                    if (t.lore != null && !t.lore.isEmpty()) {
                        for (String l : t.lore) {
                            richTip.add(Component.literal(l));
                        }
                    } else {
                        richTip.add(Component.literal(topLabel));
                        richTip.add(Component.literal("§7Rarity: §f" + t.rarity));
                        richTip.add(Component.literal("§dMagical Power: +" + t.ap + " MP"));
                        if (t.craftCost > 0) {
                            richTip.add(Component.literal("§eCraft Cost: §6" + formatCoins(t.craftCost)));
                        }
                        if (t.price > 0) {
                            richTip.add(Component.literal("§bAH / BZ Price: §6" + formatCoins(t.price)));
                        }
                    }

                    richTip.add(Component.literal(""));
                    if (t.recipe != null || t.craftDetails != null) {
                        richTip.add(Component.literal("§eLeft-Click: §fView 3x3 Recipe & Crafting Queue"));
                    } else if (t.price > 0) {
                        richTip.add(Component.literal("§eLeft-Click: §fSearch /ahs " + t.name));
                    } else {
                        richTip.add(Component.literal("§eLeft-Click: §fOpen SkyBlock Wiki"));
                    }
                    richTip.add(Component.literal("§bRight-Click: §fOpen SkyBlock Wiki"));

                    // Click action
                    final String tierName = t.name;
                    final String tierRarity = t.rarity;
                    final JsonObject tierRec = t.recipe;
                    final JsonObject tierCd = t.craftDetails;
                    final long tierPrice = t.price;

                    Consumer<Integer> tierAction = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + tierName.replace(" ", "_"));
                        } else {
                            if (tierRec != null || tierCd != null) {
                                openCraftModal(tierName, tierRarity, tierRec, tierCd);
                            } else if (tierPrice > 0) {
                                triggerAhSearch(tierName);
                            } else {
                                Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + tierName.replace(" ", "_"));
                            }
                        }
                    };

                    synchronized (clickActions) {
                        clickActions.add(new ClickableAction(pillX, pillY, pillX + pillW, pillY + pillH, tierAction, "Talisman: " + tierName, richTip));
                    }

                    pillX += pillW + 4;
                    if (i < c.tiers.size() - 1 && pillX + 14 < x + availableW) {
                        g.text(font, "§7➔", pillX, pillY + 6, 0xFF64748B, false);
                        pillX += 14;
                    }
                }
            }
            curY += cardH + 6;
        }
    }

    private void renderEnrichmentsTab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        int statW = (w - 18) / 4;
        int heroY = y;

        // 4 Hero Stat Cards
        drawStatCard(g, font, x, heroY, statW, 26, "APPLIED ENRICHMENTS", "§a" + enrichedCount + " §7/ " + eligibleEnrichmentsCount);
        drawStatCard(g, font, x + statW + 6, heroY, statW, 26, "ELIGIBLE TALISMANS", "§e" + eligibleEnrichmentsCount + " §7(LEG+/SPEC)");
        drawStatCard(g, font, x + (statW + 6) * 2, heroY, statW, 26, "MISSING ENRICHMENTS", (missingEnrichments.isEmpty() ? "§a0" : "§c" + missingEnrichments.size()));
        drawStatCard(g, font, x + (statW + 6) * 3, heroY, statW, 26, "EST. COST TO MAX", "§6" + formatCoins(missingEnrichments.size() * enrichmentMinCost));

        int curY = heroY + 34;

        // Warnings Section (e.g. multi-hatcessory duplicates, unenriched higher-tier accessories)
        if (!enrichmentWarnings.isEmpty()) {
            for (EnrichmentWarning warn : enrichmentWarnings) {
                int warnH = 28;
                g.fill(x, curY, x + w - 10, curY + warnH, 0x2A78350F);
                g.outline(x, curY, w - 10, warnH, 0xFFF59E0B);

                g.text(font, "§6⚠ §l" + warn.title, x + 8, curY + 4, 0xFFFBBF24, true);
                g.text(font, "§e" + warn.description, x + 8, curY + 16, 0xFFFEF08A, false);

                curY += warnH + 6;
            }
            curY += 4;
        }

        // Missing Enrichments List
        List<MissingEnrichmentEntry> filtered = new ArrayList<>();
        for (MissingEnrichmentEntry m : missingEnrichments) {
            if (searchFilter.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(searchFilter) || m.rarity.toLowerCase(Locale.ROOT).contains(searchFilter)) {
                filtered.add(m);
            }
        }

        int itemH = 24;
        int listH = h - (curY - y);
        int totalH = (curY - y) + filtered.size() * (itemH + 4);
        this.maxScroll = Math.max(0, totalH - h);

        curY -= (int) scrollAmount;

        if (filtered.isEmpty()) {
            int emptyY = heroY + 50;
            if (missingEnrichments.isEmpty()) {
                g.centeredText(font, "§a🎉 Perfect! All eligible accessories have active enrichments applied!", x + w / 2, emptyY, 0xFF34D399);
                g.centeredText(font, "§7No missing enrichments found across your talisman bag.", x + w / 2, emptyY + 14, 0xFF94A3B8);
            } else {
                g.centeredText(font, "§7No items matching filter '§f" + searchFilter + "§7'", x + w / 2, emptyY, 0xFFA0AEC0);
            }
            return;
        }

        for (MissingEnrichmentEntry m : filtered) {
            if (curY + itemH >= y + 34 && curY <= y + h) {
                boolean hovered = mouseX >= x && mouseX <= x + w - 10 && mouseY >= curY && mouseY <= curY + itemH;
                int rowBg = hovered ? 0x301E293B : 0x1A0F172A;
                int rowBorder = hovered ? 0x8038BDF8 : 0x3038BDF8;

                g.fill(x, curY, x + w - 10, curY + itemH, rowBg);
                g.outline(x, curY, w - 10, itemH, rowBorder);

                int nameColor = getRarityColor(m.rarity);
                g.text(font, m.name, x + 10, curY + 7, nameColor, true);
                g.text(font, "§8[" + m.rarity + "]", x + 10 + font.width(m.name) + 8, curY + 7, 0xFF64748B, false);

                // Action Button: Buy Enrichment on Bazaar
                int btnW = 96;
                int btnH = 16;
                int btnX = x + w - 110;
                int btnY = curY + 4;
                boolean btnHov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                String needStr = "§7Needs Enrichment · Est. §6" + formatCoins(enrichmentMinCost);
                int needW = font.width(needStr);
                g.text(font, needStr, btnX - 10 - needW, curY + 7, 0xFFCBD5E1, false);

                g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0xCC059669 : 0x99065F46);
                g.outline(btnX, btnY, btnW, btnH, 0xFF10B981);
                g.centeredText(font, "§f⚡ /bz Enrichment", btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);

                final String itemName = m.name;
                Consumer<Integer> buyAction = (btn) -> {
                    if (btn == 1) {
                        Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/Accessory_Enrichment");
                    } else {
                        triggerBazaarSearch("Magic Find Enrichment");
                    }
                };

                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal("§f§l" + itemName));
                tip.add(Component.literal("§7Eligible for stat enrichment (+1% stat per talisman)."));
                tip.add(Component.literal("§eLeft-Click: §fOpen Bazaar for Enrichment"));
                tip.add(Component.literal("§bRight-Click: §fOpen Wiki Guide"));

                synchronized (clickActions) {
                    clickActions.add(new ClickableAction(btnX, btnY, btnX + btnW, btnY + btnH, buyAction, "Buy Enrichment for " + itemName, tip));
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

            g.fill(x + 10, curY, x + 10 + cardW, curY + cardH, 0x1A0F172A);
            g.outline(x + 10, curY, cardW, cardH, 0x3038BDF8);

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
        g.fill(0, 0, this.width, this.height, 0xB0000000);

        int chestW = 176;
        int chestH = 222;
        int chestX = (this.width - chestW) / 2;
        int chestY = (this.height - chestH) / 2;

        // Render standard vanilla 54-slot chest container background
        g.blit(CHEST_GUI_TEXTURE, chestX, chestY, chestX + chestW, chestY + chestH, 0.0F, 0.6875F, 0.0F, 0.8671875F);

        // Header Title in authentic container font color (4210752 = 0x404040 dark gray)
        String titleStr = activeModal.itemName;
        g.text(font, titleStr, chestX + 8, chestY + 6, 4210752, false);

        int startX = chestX + 8;
        int startY = chestY + 18;
        ItemStack hoveredSlotStack = null;

        // Pre-create standard gray pane for empty/border slots
        ItemStack grayPane = new ItemStack(Items.STAINED_GLASS_PANE.pick(net.minecraft.world.item.DyeColor.GRAY));
        grayPane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));

        // Map crafting grid coordinates:
        // Row 1: slots 10, 11, 12 -> A1, A2, A3
        // Row 2: slots 19, 20, 21 -> B1, B2, B3
        // Row 3: slots 28, 29, 30 -> C1, C2, C3
        Map<Integer, String> gridSlotMap = new LinkedHashMap<>();
        gridSlotMap.put(10, "A1");
        gridSlotMap.put(11, "A2");
        gridSlotMap.put(12, "A3");
        gridSlotMap.put(19, "B1");
        gridSlotMap.put(20, "B2");
        gridSlotMap.put(21, "B3");
        gridSlotMap.put(28, "C1");
        gridSlotMap.put(29, "C2");
        gridSlotMap.put(30, "C3");

        for (int i = 0; i < 54; i++) {
            int row = i / 9;
            int col = i % 9;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;

            ItemStack slotStack = null;
            Consumer<Integer> slotClickAction = null;

            if (gridSlotMap.containsKey(i)) {
                String gridKey = gridSlotMap.get(i);
                if (activeModal.grid != null && activeModal.grid.has(gridKey) && activeModal.grid.get(gridKey).isJsonObject()) {
                    JsonObject cell = activeModal.grid.getAsJsonObject(gridKey);
                    String id = optString(cell, "id", "");
                    int count = optInt(cell, "count", 1);
                    ItemStack st = SkyblockItemManager.createSkyblockItem(id);
                    if (st == null || st.isEmpty()) st = new ItemStack(Items.STONE);
                    st = st.copy();
                    st.setCount(Math.max(1, count));

                    // Add need/have info to item lore
                    CraftIngredient matchIng = null;
                    for (CraftIngredient ing : activeModal.ingredients) {
                        if (ing.id.equalsIgnoreCase(id) || ing.name.equalsIgnoreCase(id)) {
                            matchIng = ing;
                            break;
                        }
                    }

                    List<Component> extraLore = new ArrayList<>();
                    ItemLore existingLore = st.get(DataComponents.LORE);
                    if (existingLore != null) {
                        extraLore.addAll(existingLore.lines());
                    }
                    extraLore.add(Component.literal(""));
                    if (matchIng != null) {
                        extraLore.add(Component.literal("§7Need: §e" + matchIng.needed + " §8| §7Have: " + (matchIng.hasEnough ? "§a" : "§c") + matchIng.owned));
                        if (!matchIng.hasEnough) {
                            extraLore.add(Component.literal("§cMissing: " + matchIng.missing + " §7(Est. §6" + formatCoins(matchIng.unitPrice * matchIng.missing) + "§7)"));
                        }
                    }
                    extraLore.add(Component.literal("§eLeft-Click: §f/bz " + (matchIng != null ? matchIng.name : id)));
                    extraLore.add(Component.literal("§bRight-Click: §fOpen Wiki"));
                    st.set(DataComponents.LORE, new ItemLore(extraLore));

                    slotStack = st;
                    final String ingName = matchIng != null ? matchIng.name : id;
                    slotClickAction = (btn) -> {
                        if (btn == 1) {
                            Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + ingName.replace(" ", "_"));
                        } else {
                            triggerBazaarSearch(ingName);
                        }
                    };
                }
            } else if (i == 23) {
                // Slot 23: Crafting Table
                ItemStack table = new ItemStack(Items.CRAFTING_TABLE);
                table.set(DataComponents.CUSTOM_NAME, Component.literal("§aCrafting Table"));
                List<Component> tLore = new ArrayList<>();
                tLore.add(Component.literal("§7Crafting recipe for: §f" + activeModal.itemName));
                table.set(DataComponents.LORE, new ItemLore(tLore));
                slotStack = table;
            } else if (i == 25) {
                // Slot 25: Output Crafted Item
                String lookup = activeModal.itemId != null && !activeModal.itemId.isEmpty() ? activeModal.itemId : activeModal.itemName;
                ItemStack out = SkyblockItemManager.createSkyblockItem(lookup);
                if (out == null || out.isEmpty()) {
                    String cleanId = activeModal.itemName.toUpperCase(Locale.ROOT).replace(" ", "_").replace("'", "");
                    out = SkyblockItemManager.createSkyblockItem(cleanId);
                }
                if (out == null || out.isEmpty()) {
                    out = new ItemStack(Items.PLAYER_HEAD);
                }
                out = out.copy();

                int rColor = getRarityColor(activeModal.rarity);
                out.set(DataComponents.CUSTOM_NAME, Component.literal(activeModal.itemName).withStyle(s -> s.withColor(rColor)));

                List<Component> outLore = new ArrayList<>();
                ItemLore baseLore = out.get(DataComponents.LORE);
                if (baseLore != null) {
                    outLore.addAll(baseLore.lines());
                }
                outLore.add(Component.literal(""));
                outLore.add(Component.literal("§eClick to view recipe!"));
                if (activeModal.totalMissingCost > 0) {
                    outLore.add(Component.literal("§6Raw Craft Cost: §e" + formatCoins(activeModal.totalMissingCost)));
                }
                outLore.add(Component.literal("§bRight-Click: §fOpen SkyBlock Wiki"));
                out.set(DataComponents.LORE, new ItemLore(outLore));

                slotStack = out;
                slotClickAction = (btn) -> {
                    if (btn == 1) {
                        Util.getPlatform().openUri("https://hypixelskyblock.minecraft.wiki/w/" + activeModal.itemName.replace(" ", "_"));
                    } else {
                        triggerAhSearch(activeModal.itemName);
                    }
                };
            } else if (i == 48) {
                // Slot 48: Go Back
                ItemStack arrow = new ItemStack(Items.ARROW);
                arrow.set(DataComponents.CUSTOM_NAME, Component.literal("§aGo Back"));
                List<Component> aLore = new ArrayList<>();
                aLore.add(Component.literal("§7To previous screen"));
                arrow.set(DataComponents.LORE, new ItemLore(aLore));
                slotStack = arrow;
                slotClickAction = (btn) -> this.activeModal = null;
            } else if (i == 49) {
                // Slot 49: Close
                ItemStack barrier = new ItemStack(Items.BARRIER);
                barrier.set(DataComponents.CUSTOM_NAME, Component.literal("§cClose"));
                slotStack = barrier;
                slotClickAction = (btn) -> this.activeModal = null;
            } else if (i == 50) {
                // Slot 50: Buy Missing / Add to Queue
                ItemStack queueItem = new ItemStack(activeModal.canCraftNow ? Items.EMERALD : Items.HOPPER);
                queueItem.set(DataComponents.CUSTOM_NAME, Component.literal(activeModal.canCraftNow ? "§a✔ Ready to Craft!" : "§e🛒 Buy Missing Ingredients"));
                List<Component> qLore = new ArrayList<>();
                if (activeModal.canCraftNow) {
                    qLore.add(Component.literal("§7You have all ingredients in bags & sacks!"));
                    qLore.add(Component.literal("§eClick to run /recipe " + activeModal.itemName));
                } else {
                    qLore.add(Component.literal("§7Missing ingredients:"));
                    for (CraftIngredient mi : activeModal.missingToBuy) {
                        qLore.add(Component.literal("§c- " + mi.needed + "x §f" + mi.name));
                    }
                    qLore.add(Component.literal(""));
                    qLore.add(Component.literal("§6Total Est. Cost: §e" + formatCoins(activeModal.totalMissingCost)));
                    qLore.add(Component.literal("§eClick to open Bazaar & copy quantity to clipboard!"));
                }
                queueItem.set(DataComponents.LORE, new ItemLore(qLore));
                slotStack = queueItem;
                slotClickAction = (btn) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        if (activeModal.canCraftNow) {
                            mc.player.connection.sendCommand("recipe " + activeModal.itemName);
                            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aOpened recipe for §e" + activeModal.itemName));
                        } else if (!activeModal.missingToBuy.isEmpty()) {
                            CraftIngredient first = activeModal.missingToBuy.get(0);
                            try {
                                mc.keyboardHandler.setClipboard(String.valueOf(first.needed));
                            } catch (Exception ignored) {}
                            mc.player.connection.sendCommand("bz " + first.name);
                            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aOpened Bazaar for §e" + first.name + "§a. Copied quantity (§b" + first.needed + "§a) to clipboard!"));
                        }
                    }
                    this.activeModal = null;
                };
            } else {
                // Border glass pane
                slotStack = grayPane;
            }

            if (slotStack != null && !slotStack.isEmpty()) {
                g.item(slotStack, slotX, slotY);
                g.itemDecorations(font, slotStack, slotX, slotY);

                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    g.fill(slotX, slotY, slotX + 16, slotY + 16, -2130706433);
                    if (slotStack != grayPane) {
                        hoveredSlotStack = slotStack;
                    }
                }

                if (slotClickAction != null) {
                    modalActions.add(new ClickableAction(slotX, slotY, slotX + 16, slotY + 16, slotClickAction, null, null));
                }
            }
        }

        // Render hovered slot tooltip
        if (hoveredSlotStack != null) {
            try {
                g.setTooltipForNextFrame(font, hoveredSlotStack, mouseX, mouseY);
            } catch (Throwable ignored) {}
        }
    }

    private void drawBonusRow(GuiGraphicsExtractor g, Font font, int x, int y, int w, String title, String desc, String status) {
        g.fill(x, y, x + w, y + 20, 0x1A0F172A);
        g.outline(x, y, w, 20, 0x3038BDF8);
        g.text(font, "§b" + title, x + 10, y + 6, 0xFF38BDF8, true);
        g.text(font, "§7" + desc, x + 180, y + 6, 0xFF9CA3AF, false);
        g.text(font, status, x + w - 60, y + 6, 0xFF10B981, true);
    }

    private void drawStatCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, String value) {
        g.fill(x, y, x + w, y + h, 0x250F172A);
        g.outline(x, y, w, h, 0x4038BDF8);
        g.centeredText(font, "§7" + label, x + w / 2, y + 3, 0xFF94A3B8);
        g.centeredText(font, value, x + w / 2, y + 13, 0xFFFFFFFF);
    }

    private void drawTabPill(GuiGraphicsExtractor g, Font font, int x, int y, String label, boolean active, int mouseX, int mouseY) {
        int w = font.width(label) + 14;
        int h = 18;
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = active ? 0xDD38BDF8 : (hov ? 0x4038BDF8 : 0x200F172A);
        int border = active ? 0xFFE0F2FE : (hov ? 0x8038BDF8 : 0x3038BDF8);
        int textCol = active ? 0xFF090D16 : (hov ? 0xFFFFFFFF : 0xFFCBD5E1);

        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
        g.centeredText(font, (active ? "§l" : "") + label, x + w / 2, y + 5, textCol);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // If Modal is active, intercept clicks exclusively
        if (activeModal != null) {
            synchronized (modalActions) {
                for (ClickableAction action : modalActions) {
                    if (mouseX >= action.minX && mouseX <= action.maxX && mouseY >= action.minY && mouseY <= action.maxY) {
                        if (action.clickAction != null) action.clickAction.accept(button);
                        return true;
                    }
                }
            }
            int mW = 176;
            int mH = 222;
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
            String url = me.bombo.bomboaddons.util.BomboApiUrl.getWebUrl("/pv/" + URLEncoder.encode(p, StandardCharsets.UTF_8));
            Util.getPlatform().openUri(URI.create(url));
            return true;
        }

        // Tab selection
        int tabY = winY + 68;
        int tabX = winX + 16;
        int tab1W = font.width("Cheapest Upgrades (" + upgrades.size() + ")") + 20;
        if (mouseX >= tabX && mouseX <= tabX + tab1W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.UPGRADES;
            scrollAmount = 0;
            return true;
        }
        tabX += tab1W;
        int tab2W = font.width("Missing (" + missing.size() + ")") + 20;
        if (mouseX >= tabX && mouseX <= tabX + tab2W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.MISSING;
            scrollAmount = 0;
            return true;
        }
        tabX += tab2W;
        int tab3W = font.width("Talismans (" + talismanChains.size() + ")") + 20;
        if (mouseX >= tabX && mouseX <= tabX + tab3W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.TALISMANS;
            scrollAmount = 0;
            return true;
        }
        tabX += tab3W;
        int tab4W = font.width("Enrichments (" + missingEnrichments.size() + ")") + 20;
        if (mouseX >= tabX && mouseX <= tabX + tab4W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.ENRICHMENTS;
            scrollAmount = 0;
            return true;
        }
        tabX += tab4W;
        int tab5W = font.width("Breakdown") + 20;
        if (mouseX >= tabX && mouseX <= tabX + tab5W && mouseY >= tabY && mouseY <= tabY + 18) {
            currentTab = Tab.BREAKDOWN;
            scrollAmount = 0;
            return true;
        }

        // Clickable actions
        synchronized (clickActions) {
            for (ClickableAction action : clickActions) {
                if (mouseX >= action.minX && mouseX <= action.maxX && mouseY >= action.minY && mouseY <= action.maxY) {
                    if (action.clickAction != null) {
                        action.clickAction.accept(button);
                        return true;
                    }
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

        mc.player.connection.sendCommand("ahs " + itemName);
        mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §bAuction House Search: §e" + itemName + " §7(Copied name to clipboard)"));
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
