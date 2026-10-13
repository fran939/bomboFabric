package me.bombo.bomboaddons.features.shards;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

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
import java.util.function.Consumer;

public class ShardsShoppingManager {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public static class DirectBuyItem {
        public String id;
        public String name;
        public int quantity;
        public long estimatedCost;
        public String forFusion; // e.g. "Quake craft"

        public DirectBuyItem(String id, String name, int quantity, long estimatedCost, String forFusion) {
            this.id = id;
            this.name = name;
            this.quantity = quantity;
            this.estimatedCost = estimatedCost;
            this.forFusion = forFusion;
        }
    }

    public static class FusionItem {
        public String id;
        public String name;
        public String rarity;
        public int quantity;
        public int craftsCount;
        public long estimatedCost;
        public double savingsPercent;
        public String input1Name;
        public String input1Id;
        public int input1Qty;
        public int fuse1;
        public String input2Name;
        public String input2Id;
        public int input2Qty;
        public int fuse2;
    }

    public static class PlannedUpgrade {
        public String shardId;
        public String name;
        public String attrName;
        public String rarity;
        public int priority;
        public int fromTier;
        public int toTier;
        public int shardsNeeded;
        public long cost;
        public String method;
        public double savingsPercent;
    }

    public static class PlanData {
        public String player;
        public String targetClass;
        public long budget;
        public long spent;
        public long remaining;
        public List<PlannedUpgrade> upgrades = new ArrayList<>();
        public List<DirectBuyItem> directBuys = new ArrayList<>();
        public List<FusionItem> fusions = new ArrayList<>();
    }

    private static PlanData activePlan = null;
    private static boolean isLoading = false;
    private static String lastError = null;

    // Bazaar Sign Auto-Fill State
    public static int pendingSignQuantity = 0;
    public static String pendingSignShard = null;
    public static long pendingSignTimestamp = 0L;

    // HUD Hitboxes for click interactions
    public static class ClickableItemArea {
        public int minX, minY, maxX, maxY;
        public String shardName;
        public int quantity;

        public ClickableItemArea(int minX, int minY, int maxX, int maxY, String shardName, int quantity) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.shardName = shardName;
            this.quantity = quantity;
        }
    }
    private static final List<ClickableItemArea> activeClickables = new ArrayList<>();

    public static boolean hasPlan() {
        return activePlan != null && (!activePlan.directBuys.isEmpty() || !activePlan.fusions.isEmpty());
    }

    public static PlanData getActivePlan() {
        return activePlan;
    }

    public static boolean isLoading() {
        return isLoading;
    }

    public static String getLastError() {
        return lastError;
    }

    public static boolean hasPendingQuantity() {
        if (pendingSignQuantity <= 0) return false;
        if (System.currentTimeMillis() - pendingSignTimestamp > 120_000L) { // 2 min expiration
            pendingSignQuantity = 0;
            pendingSignShard = null;
            return false;
        }
        return true;
    }

    public static void clearPendingQuantity() {
        pendingSignQuantity = 0;
        pendingSignShard = null;
    }

    public static void buyItem(String shardName, int quantity) {
        if (shardName == null || shardName.isEmpty() || quantity <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        pendingSignQuantity = quantity;
        pendingSignShard = shardName;
        pendingSignTimestamp = System.currentTimeMillis();

        try {
            mc.keyboardHandler.setClipboard(String.valueOf(quantity));
        } catch (Exception ignored) {}

        String bzQuery = shardName;
        if (!bzQuery.toLowerCase().contains("shard")) {
            bzQuery = bzQuery + " Shard";
        }

        mc.player.connection.sendCommand("bz " + bzQuery);
        mc.player.sendSystemMessage(Component.literal(
                "§8[§3Bombo§8]§r §aBazaar: §e" + bzQuery + " §7x§f" + quantity +
                        " §a(Copied §e" + quantity + "§a to clipboard! Click sign auto-fill on quantity prompt)"
        ));
    }

    public static void fetchPlanAsync(String player, String targetClass, String budget, Consumer<String> callback) {
        isLoading = true;
        lastError = null;

        CompletableFuture.runAsync(() -> {
            try {
                String p = player != null && !player.trim().isEmpty() ? player.trim() :
                        (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getScoreboardName() : "me");
                String cls = targetClass != null && !targetClass.trim().isEmpty() ? targetClass.trim().toLowerCase() : "archer";
                String bg = budget != null && !budget.trim().isEmpty() ? budget.trim() : "100m";

                String url = me.bombo.bomboaddons.util.BomboApiUrl.getApiUrl(String.format("/api/v1/dungeons/shards?player=%s&class=%s&budget=%s",
                        URLEncoder.encode(p, StandardCharsets.UTF_8),
                        URLEncoder.encode(cls, StandardCharsets.UTF_8),
                        URLEncoder.encode(bg, StandardCharsets.UTF_8)));

                HttpRequest request = me.bombo.bomboaddons.util.BomboApiUrl.attachApiKey(
                        HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("Accept", "application/json")
                                .GET()
                ).build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    lastError = "API error " + response.statusCode();
                    isLoading = false;
                    if (callback != null) callback.accept(lastError);
                    return;
                }

                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!json.has("ok") || !json.get("ok").getAsBoolean()) {
                    lastError = json.has("error") ? json.get("error").getAsString() : "Optimizer calculation failed";
                    isLoading = false;
                    if (callback != null) callback.accept(lastError);
                    return;
                }

                PlanData plan = new PlanData();
                plan.player = json.has("player") && json.get("player").isJsonObject() && json.get("player").getAsJsonObject().has("name")
                        ? json.get("player").getAsJsonObject().get("name").getAsString() : p;
                plan.targetClass = json.has("activeClass") ? json.get("activeClass").getAsString() : cls;

                if (json.has("budgetPlan") && json.get("budgetPlan").isJsonObject()) {
                    JsonObject bp = json.getAsJsonObject("budgetPlan");
                    plan.budget = bp.has("budget") ? bp.get("budget").getAsLong() : 0L;
                    plan.spent = bp.has("spent") ? bp.get("spent").getAsLong() : 0L;
                    plan.remaining = bp.has("remaining") ? bp.get("remaining").getAsLong() : 0L;

                    if (bp.has("upgrades") && bp.get("upgrades").isJsonArray()) {
                        for (JsonElement el : bp.getAsJsonArray("upgrades")) {
                            JsonObject uObj = el.getAsJsonObject();
                            PlannedUpgrade u = new PlannedUpgrade();
                            u.shardId = uObj.has("shardId") ? uObj.get("shardId").getAsString() : "";
                            u.name = uObj.has("name") ? uObj.get("name").getAsString() : "";
                            u.attrName = uObj.has("attrName") ? uObj.get("attrName").getAsString() : "";
                            u.rarity = uObj.has("rarity") ? uObj.get("rarity").getAsString() : "";
                            u.priority = uObj.has("priority") ? uObj.get("priority").getAsInt() : 0;
                            u.fromTier = uObj.has("fromTier") ? uObj.get("fromTier").getAsInt() : 0;
                            u.toTier = uObj.has("toTier") ? uObj.get("toTier").getAsInt() : 0;
                            u.shardsNeeded = uObj.has("shardsNeeded") ? uObj.get("shardsNeeded").getAsInt() : 0;
                            u.cost = uObj.has("cost") ? uObj.get("cost").getAsLong() : 0L;
                            u.method = uObj.has("method") ? uObj.get("method").getAsString() : "buy";
                            u.savingsPercent = uObj.has("savingsPercent") ? uObj.get("savingsPercent").getAsDouble() : 0.0;
                            plan.upgrades.add(u);
                        }
                    }

                    if (bp.has("fusions") && bp.get("fusions").isJsonArray()) {
                        for (JsonElement el : bp.getAsJsonArray("fusions")) {
                            JsonObject fObj = el.getAsJsonObject();
                            FusionItem f = new FusionItem();
                            f.id = fObj.has("id") ? fObj.get("id").getAsString() : "";
                            f.name = fObj.has("name") ? fObj.get("name").getAsString() : "";
                            f.rarity = fObj.has("rarity") ? fObj.get("rarity").getAsString() : "";
                            f.quantity = fObj.has("quantity") ? fObj.get("quantity").getAsInt() : 0;
                            f.craftsCount = fObj.has("craftsCount") ? fObj.get("craftsCount").getAsInt() : 0;
                            f.estimatedCost = fObj.has("estimatedCost") ? fObj.get("estimatedCost").getAsLong() : 0L;
                            f.savingsPercent = fObj.has("savingsPercent") ? fObj.get("savingsPercent").getAsDouble() : 0.0;

                            if (fObj.has("recipe") && fObj.get("recipe").isJsonObject()) {
                                JsonObject rObj = fObj.getAsJsonObject("recipe");
                                f.input1Name = rObj.has("input1Name") ? rObj.get("input1Name").getAsString() : "";
                                f.input1Id = rObj.has("input1Id") ? rObj.get("input1Id").getAsString() : "";
                                f.input1Qty = rObj.has("input1Qty") ? rObj.get("input1Qty").getAsInt() : 0;
                                f.fuse1 = rObj.has("fuse1") ? rObj.get("fuse1").getAsInt() : 5;

                                f.input2Name = rObj.has("input2Name") ? rObj.get("input2Name").getAsString() : "";
                                f.input2Id = rObj.has("input2Id") ? rObj.get("input2Id").getAsString() : "";
                                f.input2Qty = rObj.has("input2Qty") ? rObj.get("input2Qty").getAsInt() : 0;
                                f.fuse2 = rObj.has("fuse2") ? rObj.get("fuse2").getAsInt() : 5;
                            }
                            plan.fusions.add(f);
                        }
                    }

                    if (bp.has("directBuy") && bp.get("directBuy").isJsonArray()) {
                        for (JsonElement el : bp.getAsJsonArray("directBuy")) {
                            JsonObject bObj = el.getAsJsonObject();
                            String name = bObj.has("name") ? bObj.get("name").getAsString() : "";
                            if (name.isEmpty()) continue;
                            String id = bObj.has("id") ? bObj.get("id").getAsString() : "";
                            int qty = bObj.has("quantity") ? bObj.get("quantity").getAsInt() : 0;
                            long cost = bObj.has("estimatedCost") ? bObj.get("estimatedCost").getAsLong() : 0L;

                            String forFusion = null;
                            for (FusionItem f : plan.fusions) {
                                if (f.input1Name.equalsIgnoreCase(name) || f.input2Name.equalsIgnoreCase(name)) {
                                    forFusion = "for " + f.name;
                                    break;
                                }
                            }
                            plan.directBuys.add(new DirectBuyItem(id, name, qty, cost, forFusion));
                        }
                    }
                }

                activePlan = plan;
                isLoading = false;
                if (callback != null) callback.accept(null);
            } catch (Exception e) {
                lastError = e.getMessage() != null ? e.getMessage() : "Connection failed";
                isLoading = false;
                if (callback != null) callback.accept(lastError);
            }
        });
    }

    public static boolean handleHudClick(double mouseX, double mouseY) {
        synchronized (activeClickables) {
            for (ClickableItemArea area : activeClickables) {
                if (mouseX >= area.minX && mouseX <= area.maxX && mouseY >= area.minY && mouseY <= area.maxY) {
                    buyItem(area.shardName, area.quantity);
                    return true;
                }
            }
        }
        return false;
    }

    public static int[] renderHud(GuiGraphicsExtractor g, int x, int y, float scale, boolean isHovered) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int width = 230;
        int height = 30;

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        synchronized (activeClickables) {
            activeClickables.clear();
        }

        if (activePlan == null || (activePlan.directBuys.isEmpty() && activePlan.fusions.isEmpty())) {
            width = 190;
            height = 36;
            g.fill(0, 0, width, height, 0xDD0D1117);
            g.outline(0, 0, width, height, isHovered ? 0xFF00FFCC : 0xAA30363D);
            g.text(font, "§6§lShard Shopping List", 6, 6, -1, true);
            g.text(font, "§7Run §e/b shards§7 to calculate!", 6, 20, -1, true);
            g.pose().popMatrix();
            return new int[]{ (int)(width * scale), (int)(height * scale) };
        }

        List<String> renderLines = new ArrayList<>();
        List<Runnable> clickRegistrations = new ArrayList<>();

        String budgetStr = formatCoins(activePlan.budget);
        renderLines.add("§6§lShard Shopping §7(" + capitalize(activePlan.targetClass) + " • " + budgetStr + ")");

        int lineIdx = 1;

        if (!activePlan.directBuys.isEmpty()) {
            renderLines.add("§a§lDirect Buys §7(Click to /bz):");
            lineIdx++;

            for (DirectBuyItem item : activePlan.directBuys) {
                String fusionTag = item.forFusion != null ? " §8(" + item.forFusion + ")" : "";
                String lineText = " §e[BZ] §f" + item.name + " §ax" + item.quantity + fusionTag;
                renderLines.add(lineText);

                final int lineY = lineIdx * 11 + 6;
                final String sName = item.name;
                final int sQty = item.quantity;
                clickRegistrations.add(() -> {
                    int textW = font.width(lineText);
                    int minX = (int)(x + 4 * scale);
                    int minY = (int)(y + (lineY - 1) * scale);
                    int maxX = (int)(x + (textW + 10) * scale);
                    int maxY = (int)(y + (lineY + 10) * scale);
                    activeClickables.add(new ClickableItemArea(minX, minY, maxX, maxY, sName, sQty));
                });

                lineIdx++;
            }
        }

        if (!activePlan.fusions.isEmpty()) {
            if (!activePlan.directBuys.isEmpty()) {
                renderLines.add("");
                lineIdx++;
            }
            renderLines.add("§d§lFusions Needed §7(Galatea):");
            lineIdx++;

            for (FusionItem f : activePlan.fusions) {
                String saveStr = f.savingsPercent > 0 ? String.format(Locale.US, " §a(-%.0f%%)", f.savingsPercent) : "";
                String lineText = " §f• §b" + f.name + " §fx" + f.quantity + saveStr;
                renderLines.add(lineText);
                lineIdx++;

                String ingText = "   §8" + f.input1Qty + " " + f.input1Name + " + " + f.input2Qty + " " + f.input2Name;
                renderLines.add(ingText);
                lineIdx++;
            }
        }

        height = lineIdx * 11 + 10;
        int maxW = 200;
        for (String ln : renderLines) {
            int w = font.width(ln);
            if (w + 14 > maxW) maxW = w + 14;
        }
        width = maxW;

        // Background
        g.fill(0, 0, width, height, 0xEE0B0E14);
        g.outline(0, 0, width, height, isHovered ? 0xFF00FFCC : 0xAA2B3245);

        // Header accent line
        g.fill(0, 0, width, 2, 0xFFF59E0B);

        int drawY = 6;
        for (String ln : renderLines) {
            if (!ln.isEmpty()) {
                g.text(font, ln, 6, drawY, -1, true);
            }
            drawY += 11;
        }

        // Register hitboxes
        for (Runnable r : clickRegistrations) {
            r.run();
        }

        g.pose().popMatrix();
        return new int[]{ (int)(width * scale), (int)(height * scale) };
    }

    public static String formatCoins(long coins) {
        if (coins >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", coins / 1_000_000.0);
        } else if (coins >= 1_000L) {
            return String.format(Locale.US, "%.0fk", coins / 1_000.0);
        }
        return String.valueOf(coins);
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
