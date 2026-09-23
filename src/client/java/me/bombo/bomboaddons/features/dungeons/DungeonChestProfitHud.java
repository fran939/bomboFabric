package me.bombo.bomboaddons.features.dungeons;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.AutoCroesus;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Dungeon / Kuudra chest value panel: every chest in the opened overview with its cost,
 * contents and profit. Position and scale are user controllable through /b gui; while the
 * position is still the "auto" sentinel (-1) it docks to the right of the container.
 */
public class DungeonChestProfitHud {
    public static List<AutoCroesus.DungeonChestData> currentChests = null;

    /** Natural (unscaled) panel size. */
    public static final int BASE_W = 280;

    public static int getHudWidth() {
        float s = BomboConfig.get().croesusProfitHudScale;
        return (int) (BASE_W * (s > 0.0F ? s : 1.0F));
    }

    public static void onScreenRender(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            currentChests = null;
            return;
        }
        if (!AutoCroesus.isDungeonRunGui(screen) && !AutoCroesus.isKuudraChestGui(screen)) {
            currentChests = null;
            return;
        }
        if (!BomboConfig.get().croesusHelper && !BomboConfig.get().autoCroesusHud && !AutoCroesus.active && !BomboConfig.get().dungeonDebug
                && !AutoCroesus.debugHighlightMode) {
            return;
        }

        List<AutoCroesus.DungeonChestData> chests = currentChests;
        if (chests == null || chests.isEmpty()) return;

        BomboConfig.Settings s = BomboConfig.get();
        int[] at = resolvePosition(s, screen, chests);
        drawPanel(g, chests, at[0], at[1], s.croesusProfitHudScale);
    }

    /** Wooden sample chest shown inside /b gui so the panel can be positioned. */
    public static void renderDummy(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
        List<AutoCroesus.DungeonChestData> dummy = new ArrayList<>();
        dummy.add(dummyChest("Bedrock", 2200000L, false, 14500000L, new String[][]{
                {"Necron Handle", "412000000"}, {"Master Skull T5", "98000000"}, {"Wither Essence x420", "12500000"}}));
        dummy.add(dummyChest("Obsidian", 1000000L, false, 2100000L, new String[][]{
                {"Shadow Fury", "2400000"}, {"Enchanted Book (Ultimate Wise V)", "640000"}}));
        dummy.add(dummyChest("Free Chest", 0L, true, 320000L, new String[][]{
                {"Dungeon Disc 5", "180000"}, {"Golden Fragment x3", "95000"}}));
        drawPanel(g, dummy, baseX, baseY, scale);
    }

    private static AutoCroesus.DungeonChestData dummyChest(String name, long cost, boolean free, long contentsValue, String[][] items) {
        AutoCroesus.DungeonChestData c = new AutoCroesus.DungeonChestData();
        c.chestName = name;
        c.cost = cost;
        c.isFree = free;
        c.alreadyOpened = false;
        c.totalContentsValue = contentsValue;
        c.profit = contentsValue - cost;
        c.parsedItems = new ArrayList<>();
        for (String[] it : items) {
            AutoCroesus.ItemDetail d = new AutoCroesus.ItemDetail();
            d.name = it[0];
            try {
                d.totalValue = Long.parseLong(it[1]);
            } catch (NumberFormatException ignored) {
                d.totalValue = 0L;
            }
            d.adjustedQuantity = 1;
            c.parsedItems.add(d);
        }
        return c;
    }

    /** Panel origin: configured position, or docked right of the container while still "auto". */
    private static int[] resolvePosition(BomboConfig.Settings s, AbstractContainerScreen<?> screen, List<AutoCroesus.DungeonChestData> chests) {
        int winW = getHudWidth();
        int totalH = (int) ((24 + contentHeight(chests) + 12) * safeScale(s.croesusProfitHudScale));
        if (s.croesusProfitHudX >= 0 && s.croesusProfitHudY >= 0) {
            return new int[]{s.croesusProfitHudX, s.croesusProfitHudY};
        }
        int x = screen.width - winW - 8;
        int y = Math.max(4, (screen.height - totalH) / 2);
        return new int[]{x, y};
    }

    private static float safeScale(float scale) {
        return scale > 0.0F ? scale : 1.0F;
    }

    private static int contentHeight(List<AutoCroesus.DungeonChestData> chests) {
        int h = 0;
        for (AutoCroesus.DungeonChestData c : chests) {
            h += 22 + Math.min(4, c.parsedItems.size()) * 12 + 6;
        }
        return h;
    }

    private static void drawPanel(GuiGraphicsExtractor g, List<AutoCroesus.DungeonChestData> chests, int baseX, int baseY, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float sc = safeScale(scale);

        int winW = BASE_W;
        int headerH = 24;
        int winH = headerH + contentHeight(chests) + 12;

        g.pose().pushMatrix();
        g.pose().translate((float) baseX, (float) baseY);
        g.pose().scale(sc, sc);

        g.fill(0, 0, winW, winH, 0xEE0F172A);
        g.outline(0, 0, winW, winH, ConfigUITheme.getAccentColor());

        g.fill(0, 0, winW, headerH, 0xEE1E293B);
        g.fill(0, headerH - 1, winW, headerH, ConfigUITheme.getDividerColor());
        g.text(font, ConfigUITheme.formatFont("§6§lDUNGEON CHESTS REWARDS"), 10, 8, ConfigUITheme.ACCENT_GOLD, false);

        int curY = headerH + 6;
        for (AutoCroesus.DungeonChestData c : chests) {
            int itemsCount = Math.min(4, c.parsedItems.size());
            int thisCardH = 20 + itemsCount * 12 + 4;

            boolean isProfitable = c.profit > 0 || c.isFree;
            int cardBg = c.alreadyOpened ? 0x22374151 : (isProfitable ? 0x2E10B981 : 0x2EEF4444);
            int cardBorder = c.alreadyOpened ? 0x446B7280 : (isProfitable ? 0x8810B981 : 0x88EF4444);

            g.fill(6, curY, winW - 6, curY + thisCardH, cardBg);
            g.outline(6, curY, winW - 12, thisCardH, cardBorder);

            String chestName = c.chestName;
            String status = c.alreadyOpened ? "§7[CLAIMED]" : (c.profit >= 0 ? "§a+" + LowestBinManager.formatPrice(c.profit) : "§c-" + LowestBinManager.formatPrice(Math.abs(c.profit)));
            String costStr = c.isFree ? "§aFREE" : "§6" + LowestBinManager.formatPrice(c.cost);

            g.text(font, "§e" + chestName + " §8(" + costStr + "§8)", 12, curY + 4, -1, false);

            int statusW = font.width(status);
            g.text(font, status, winW - 14 - statusW, curY + 4, -1, false);

            int itemY = curY + 17;
            for (int i = 0; i < itemsCount; i++) {
                AutoCroesus.ItemDetail item = c.parsedItems.get(i);
                String itemName = item.name.replaceFirst("(?i)^Enchanted Book\\s*\\((.*?)\\)$", "$1");
                if (itemName.length() > 24) itemName = itemName.substring(0, 22) + "..";
                if (item.adjustedQuantity > 1) itemName += " x" + item.adjustedQuantity;

                String valStr = (item.totalValue > 0 ? "§a+" : "§7") + LowestBinManager.formatPrice(item.totalValue);
                g.text(font, " §7• " + itemName, 12, itemY, 0xFFCBD5E1, false);
                int valW = font.width(valStr);
                g.text(font, valStr, winW - 14 - valW, itemY, 0xFF86EFAC, false);
                itemY += 12;
            }

            curY += thisCardH + 4;
        }

        g.pose().popMatrix();
    }
}
