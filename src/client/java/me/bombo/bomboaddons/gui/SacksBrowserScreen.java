package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.features.inferno.InfernoFuelCraftHelper;
import me.bombo.bomboaddons.features.inferno.InfernoMinionTracker;
import me.bombo.bomboaddons.features.inferno.SacksHelper;
import me.bombo.bomboaddons.features.inferno.SacksHelper.SackCategory;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * Interactive chest-style GUI displaying a player's sacks bag,
 * categorized into sacks with item counts, coin valuations,
 * drill-down item grids, and click-to-run /bz integration.
 */
public class SacksBrowserScreen extends Screen {
    private final Screen parent;
    private final String targetUser;

    private boolean loading = true;
    private String errorMessage = null;

    // Full user sacks: Map<ItemId, Count>
    private Map<String, Integer> userSacks = new LinkedHashMap<>();

    // Grouped by SackCategory: Map<Category, Map<ItemId, Count>>
    private final Map<SackCategory, Map<String, Integer>> categorizedSacks = new EnumMap<>(SackCategory.class);

    // Active View: null = Category Overview; non-null = Drilldown into this category
    private SackCategory selectedCategory = null;

    private EditBox searchBox = null;
    private String searchQuery = "";

    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private boolean isDraggingScrollbar = false;

    public SacksBrowserScreen(Screen parent, String targetUser) {
        super(Component.literal("Sacks - " + targetUser));
        this.parent = parent;
        this.targetUser = (targetUser != null && !targetUser.trim().isEmpty())
                ? targetUser.trim()
                : (Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName().getString() : "Player");
    }

    public SacksBrowserScreen(String targetUser) {
        this(null, targetUser);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int winW = Math.min(this.width - 40, 720);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - 480) / 2;

        int searchW = 160;
        int searchX = winX + winW - searchW - 12;
        int searchY = winY + 12;

        this.searchBox = new EditBox(this.font, searchX, searchY, searchW, 18, Component.literal("Search..."));
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(val -> {
            this.searchQuery = val.trim().toLowerCase(Locale.ROOT);
            this.scrollAmount = 0.0;
        });
        this.addWidget(this.searchBox);

        if (this.loading) {
            SacksHelper.fetchUserSacks(this.targetUser).thenAccept(sacks -> {
                Minecraft.getInstance().execute(() -> {
                    this.loading = false;
                    if (sacks == null || sacks.isEmpty()) {
                        this.errorMessage = "No sacks data found or Sacks API is disabled for " + this.targetUser;
                    } else {
                        this.userSacks = sacks;
                        this.categorizedSacks.clear();
                        for (Map.Entry<String, Integer> e : sacks.entrySet()) {
                            SackCategory cat = SacksHelper.getSackCategory(e.getKey());
                            this.categorizedSacks.computeIfAbsent(cat, k -> new TreeMap<>()).put(e.getKey(), e.getValue());
                        }
                    }
                });
            }).exceptionally(ex -> {
                Minecraft.getInstance().execute(() -> {
                    this.loading = false;
                    this.errorMessage = "Failed to load sacks: " + ex.getMessage();
                });
                return null;
            });
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Dark translucent background
        g.fill(0, 0, this.width, this.height, 0xD00A0D14);

        int winW = Math.min(this.width - 40, 740);
        int winH = Math.min(this.height - 40, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Window background
        g.fill(winX, winY, winX + winW, winY + winH, 0xF20F172A);
        g.outline(winX, winY, winW, winH, 0xFF334155);

        // Header
        g.fill(winX, winY, winX + winW, winY + 44, 0xFF1E293B);
        g.outline(winX, winY, winW, 44, 0xFF475569);

        if (this.selectedCategory == null) {
            // Main Overview Header
            g.text(this.font, "§6§l🎒 Sacks Bag §7- §b" + this.targetUser, winX + 14, winY + 16, 0xFFFFFFFF, true);

            int totalItems = this.userSacks.values().stream().mapToInt(Integer::intValue).sum();
            int totalTypes = this.userSacks.size();
            long totalCoins = 0L;
            for (Map.Entry<String, Integer> e : this.userSacks.entrySet()) {
                totalCoins += (long) e.getValue() * InfernoFuelCraftHelper.getBazaarPrice(e.getKey());
            }

            String stats = "§7Stored: §e" + SacksHelper.formatNumber(totalItems) + " §7items §8| §b" + totalTypes + " §7types §8| §6~" + InfernoMinionTracker.formatCoins(totalCoins) + " coins";
            g.text(this.font, stats, winX + 220, winY + 17, 0xFFCBD5E1, false);
        } else {
            // Drilldown Header
            // Back button
            int backBtnW = 75;
            int backBtnH = 22;
            int backBtnX = winX + 12;
            int backBtnY = winY + 11;
            boolean backHov = mouseX >= backBtnX && mouseX <= backBtnX + backBtnW && mouseY >= backBtnY && mouseY <= backBtnY + backBtnH;

            g.fill(backBtnX, backBtnY, backBtnX + backBtnW, backBtnY + backBtnH, backHov ? 0xFF3B82F6 : 0xFF1E293B);
            g.outline(backBtnX, backBtnY, backBtnW, backBtnH, backHov ? 0xFF60A5FA : 0xFF475569);
            g.centeredText(this.font, "§f⬅ Back", backBtnX + backBtnW / 2, backBtnY + 6, 0xFFFFFFFF);

            Map<String, Integer> catItems = this.categorizedSacks.getOrDefault(this.selectedCategory, Collections.emptyMap());
            int catTotal = catItems.values().stream().mapToInt(Integer::intValue).sum();
            long catValue = 0L;
            for (Map.Entry<String, Integer> e : catItems.entrySet()) {
                catValue += (long) e.getValue() * InfernoFuelCraftHelper.getBazaarPrice(e.getKey());
            }

            String title = this.selectedCategory.displayName + " §7(" + SacksHelper.formatNumber(catTotal) + " items, ~§6" + InfernoMinionTracker.formatCoins(catValue) + "§7)";
            g.text(this.font, title, winX + 100, winY + 16, 0xFFFFFFFF, true);
        }

        // Render Search Box
        if (this.searchBox != null) {
            this.searchBox.extractRenderState(g, mouseX, mouseY, partialTick);
        }

        // Body area
        int bodyX = winX + 12;
        int bodyY = winY + 52;
        int bodyW = winW - 24;
        int bodyH = winH - 62;

        if (this.loading) {
            g.centeredText(this.font, "§e⏳ Loading sacks data from Hypixel API...", winX + winW / 2, winY + winH / 2 - 10, 0xFFFCD34D);
            return;
        }

        if (this.errorMessage != null) {
            g.centeredText(this.font, "§c" + this.errorMessage, winX + winW / 2, winY + winH / 2 - 10, 0xFFEF4444);
            return;
        }

        // Clip viewport
        g.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);

        int contentHeight;
        if (this.selectedCategory == null) {
            contentHeight = renderCategoriesOverview(g, this.font, bodyX, bodyY - (int) this.scrollAmount, bodyW, mouseX, mouseY);
        } else {
            contentHeight = renderCategoryDrilldown(g, this.font, bodyX, bodyY - (int) this.scrollAmount, bodyW, mouseX, mouseY);
        }

        g.disableScissor();

        // Scrollbar logic
        this.maxScroll = Math.max(0, contentHeight - bodyH);
        if (this.maxScroll > 0) {
            int sbW = 6;
            int sbX = winX + winW - sbW - 4;
            int thumbH = Math.max(24, (int) ((float) bodyH / contentHeight * bodyH));
            int thumbY = bodyY + (int) (this.scrollAmount / this.maxScroll * (bodyH - thumbH));

            g.fill(sbX, bodyY, sbX + sbW, bodyY + bodyH, 0x30000000);
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF64748B);
        }
    }

    private int renderCategoriesOverview(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        int cardW = (w - 16) / 3;
        int cardH = 88;
        int gap = 8;

        int curX = x;
        int curY = y;
        int col = 0;

        SackCategory[] categories = SackCategory.values();
        for (SackCategory cat : categories) {
            Map<String, Integer> items = this.categorizedSacks.getOrDefault(cat, Collections.emptyMap());
            int totalItems = items.values().stream().mapToInt(Integer::intValue).sum();
            int typeCount = items.size();

            // Filter if search query active
            if (!this.searchQuery.isEmpty()) {
                boolean matchesCategory = cat.displayName.toLowerCase(Locale.ROOT).contains(this.searchQuery);
                boolean matchesAnyItem = items.keySet().stream().anyMatch(id -> id.toLowerCase(Locale.ROOT).contains(this.searchQuery) || SacksHelper.formatItemName(id).toLowerCase(Locale.ROOT).contains(this.searchQuery));
                if (!matchesCategory && !matchesAnyItem) continue;
            }

            long totalValue = 0L;
            for (Map.Entry<String, Integer> e : items.entrySet()) {
                totalValue += (long) e.getValue() * InfernoFuelCraftHelper.getBazaarPrice(e.getKey());
            }

            boolean hov = mouseX >= curX && mouseX <= curX + cardW && mouseY >= curY && mouseY <= curY + cardH;

            // Card background
            int bg = hov ? 0xFF1E293B : 0xFF0F172A;
            g.fill(curX, curY, curX + cardW, curY + cardH, bg);
            g.outline(curX, curY, cardW, cardH, hov ? cat.color : 0xFF334155);

            // Title
            g.text(font, cat.displayName, curX + 10, curY + 10, cat.color, true);

            // Item count & types
            String countText = "§f" + SacksHelper.formatNumber(totalItems) + " §7items §8(" + typeCount + " types)";
            g.text(font, countText, curX + 10, curY + 28, 0xFFE2E8F0, false);

            // Value
            String valText = "§7Value: §6~" + InfernoMinionTracker.formatCoins(totalValue) + " coins";
            g.text(font, valText, curX + 10, curY + 44, 0xFFFCD34D, false);

            // Action prompt
            String action = hov ? "§bClick to view items ➜" : "§8Click to inspect";
            g.text(font, action, curX + 10, curY + 66, hov ? 0xFF38BDF8 : 0xFF64748B, false);

            col++;
            if (col >= 3) {
                col = 0;
                curX = x;
                curY += cardH + gap;
            } else {
                curX += cardW + gap;
            }
        }

        if (col != 0) {
            curY += cardH + gap;
        }

        return curY - y;
    }

    private int renderCategoryDrilldown(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mouseX, int mouseY) {
        Map<String, Integer> items = this.categorizedSacks.getOrDefault(this.selectedCategory, Collections.emptyMap());
        if (items.isEmpty()) {
            g.centeredText(font, "§7No items stored in this sack.", x + w / 2, y + 40, 0xFF94A3B8);
            return 80;
        }

        List<Map.Entry<String, Integer>> list = new ArrayList<>(items.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int rowH = 36;
        int curY = y;

        // Subheader line
        g.fill(x, curY, x + w, curY + 20, 0xFF1E293B);
        g.outline(x, curY, w, 20, 0xFF334155);
        g.text(font, "§7ITEM NAME", x + 10, curY + 6, 0xFF94A3B8, false);
        g.text(font, "§7STORED", x + w - 310, curY + 6, 0xFF94A3B8, false);
        g.text(font, "§7UNIT PRICE", x + w - 190, curY + 6, 0xFF94A3B8, false);
        g.text(font, "§7TOTAL VALUE", x + w - 85, curY + 6, 0xFF94A3B8, false);
        curY += 24;

        for (Map.Entry<String, Integer> entry : list) {
            String id = entry.getKey();
            int count = entry.getValue();
            String name = SacksHelper.formatItemName(id);

            // Filter search
            if (!this.searchQuery.isEmpty()) {
                if (!id.toLowerCase(Locale.ROOT).contains(this.searchQuery) && !name.toLowerCase(Locale.ROOT).contains(this.searchQuery)) {
                    continue;
                }
            }

            long unitPrice = InfernoFuelCraftHelper.getBazaarPrice(id);
            long totalValue = (long) count * unitPrice;

            boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= curY && mouseY <= curY + rowH;

            // Row styling
            g.fill(x, curY, x + w, curY + rowH, hov ? 0x303B82F6 : 0x151E293B);
            g.outline(x, curY, w, rowH, hov ? 0xFF3B82F6 : 0x30475569);

            // Item Name & ID
            g.text(font, "§f§l" + name, x + 10, curY + 6, 0xFFFFFFFF, false);
            g.text(font, "§8ID: §7" + id + (hov ? " §8| §bClick to /bz" : ""), x + 10, curY + 19, 0xFF94A3B8, false);

            // Stored Count
            g.text(font, "§a" + SacksHelper.formatNumber(count), x + w - 310, curY + 12, 0xFF10B981, true);

            // Unit Price
            g.text(font, "§e" + InfernoMinionTracker.formatCoins(unitPrice), x + w - 190, curY + 12, 0xFFFBBF24, false);

            // Total Value
            g.text(font, "§6" + InfernoMinionTracker.formatCoins(totalValue), x + w - 85, curY + 12, 0xFFF59E0B, true);

            curY += rowH + 4;
        }

        return curY - y;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDown) {
        if (!isDown) return super.mouseClicked(event, isDown);
        double mouseX = event.x();
        double mouseY = event.y();

        if (this.searchBox != null && this.searchBox.mouseClicked(event, isDown)) {
            return true;
        }

        int winW = Math.min(this.width - 40, 740);
        int winH = Math.min(this.height - 40, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Drilldown Back Button
        if (this.selectedCategory != null) {
            int backBtnW = 75;
            int backBtnH = 22;
            int backBtnX = winX + 12;
            int backBtnY = winY + 11;
            if (mouseX >= backBtnX && mouseX <= backBtnX + backBtnW && mouseY >= backBtnY && mouseY <= backBtnY + backBtnH) {
                this.selectedCategory = null;
                this.scrollAmount = 0.0;
                return true;
            }
        }

        int bodyX = winX + 12;
        int bodyY = winY + 52;
        int bodyW = winW - 24;
        int bodyH = winH - 62;

        if (mouseX >= bodyX && mouseX <= bodyX + bodyW && mouseY >= bodyY && mouseY <= bodyY + bodyH) {
            if (this.selectedCategory == null) {
                // Clicking a Sack Category Card
                int cardW = (bodyW - 16) / 3;
                int cardH = 88;
                int gap = 8;

                int curX = bodyX;
                int curY = bodyY - (int) this.scrollAmount;
                int col = 0;

                for (SackCategory cat : SackCategory.values()) {
                    Map<String, Integer> items = this.categorizedSacks.getOrDefault(cat, Collections.emptyMap());
                    if (!this.searchQuery.isEmpty()) {
                        boolean matchesCategory = cat.displayName.toLowerCase(Locale.ROOT).contains(this.searchQuery);
                        boolean matchesAnyItem = items.keySet().stream().anyMatch(id -> id.toLowerCase(Locale.ROOT).contains(this.searchQuery) || SacksHelper.formatItemName(id).toLowerCase(Locale.ROOT).contains(this.searchQuery));
                        if (!matchesCategory && !matchesAnyItem) continue;
                    }

                    if (mouseX >= curX && mouseX <= curX + cardW && mouseY >= curY && mouseY <= curY + cardH) {
                        this.selectedCategory = cat;
                        this.scrollAmount = 0.0;
                        return true;
                    }

                    col++;
                    if (col >= 3) {
                        col = 0;
                        curX = bodyX;
                        curY += cardH + gap;
                    } else {
                        curX += cardW + gap;
                    }
                }
            } else {
                // Clicking an Item in Drilldown -> run /bz <item name>
                Map<String, Integer> items = this.categorizedSacks.getOrDefault(this.selectedCategory, Collections.emptyMap());
                List<Map.Entry<String, Integer>> list = new ArrayList<>(items.entrySet());
                list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                int rowH = 36;
                int curY = bodyY - (int) this.scrollAmount + 24; // offset by subheader

                for (Map.Entry<String, Integer> entry : list) {
                    String id = entry.getKey();
                    String name = SacksHelper.formatItemName(id);

                    if (!this.searchQuery.isEmpty()) {
                        if (!id.toLowerCase(Locale.ROOT).contains(this.searchQuery) && !name.toLowerCase(Locale.ROOT).contains(this.searchQuery)) {
                            continue;
                        }
                    }

                    if (mouseX >= bodyX && mouseX <= bodyX + bodyW && mouseY >= curY && mouseY <= curY + rowH) {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            String searchName = name.toLowerCase(Locale.ROOT);
                            mc.player.connection.sendCommand("bz " + searchName);
                            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Sacks§8] §aOpened §e/bz " + searchName + "§a!"));
                            this.onClose();
                        }
                        return true;
                    }

                    curY += rowH + 4;
                }
            }
        }

        return super.mouseClicked(event, isDown);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (this.maxScroll > 0) {
            this.scrollAmount = Math.max(0.0, Math.min(this.maxScroll, this.scrollAmount - amountY * 26.0));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.searchBox != null && this.searchBox.keyPressed(event)) {
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (this.selectedCategory != null) {
                this.selectedCategory = null;
                this.scrollAmount = 0.0;
                return true;
            }
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.parent != null) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
        } else {
            super.onClose();
        }
    }
}
