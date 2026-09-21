package me.bombo.bomboaddons.features.dungeons;

import java.util.List;
import me.bombo.bomboaddons.AutoCroesus;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public class DungeonChestProfitHud {
    public static List<AutoCroesus.DungeonChestData> currentChests = null;

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
        if (!BomboConfig.get().croesusHelper && !BomboConfig.get().autoCroesusHud && !AutoCroesus.active && !BomboConfig.get().dungeonDebug) {
            return;
        }

        List<AutoCroesus.DungeonChestData> chests = currentChests;
        if (chests == null || chests.isEmpty()) return;

        Font font = mc.font;
        int screenW = screen.width;
        int screenH = screen.height;

        // Position window to the right of the container GUI
        int winW = 280;
        int cardH = 0;
        for (AutoCroesus.DungeonChestData c : chests) {
            cardH += 22 + Math.min(4, c.parsedItems.size()) * 12 + 6;
        }

        int headerH = 24;
        int winH = Math.min(screenH - 20, headerH + cardH + 12);
        int winX = screenW - winW - 8;
        int winY = (screenH - winH) / 2;

        // Draw Background
        g.fill(winX, winY, winX + winW, winY + winH, 0xEE0F172A);
        g.outline(winX, winY, winW, winH, ConfigUITheme.getAccentColor());

        // Header
        g.fill(winX, winY, winX + winW, winY + headerH, 0xEE1E293B);
        g.fill(winX, winY + headerH - 1, winX + winW, winY + headerH, ConfigUITheme.getDividerColor());
        g.text(font, ConfigUITheme.formatFont("§6§lDUNGEON CHESTS REWARDS"), winX + 10, winY + 8, ConfigUITheme.ACCENT_GOLD, false);

        int curY = winY + headerH + 6;

        for (AutoCroesus.DungeonChestData c : chests) {
            if (curY + 24 > winY + winH) break;

            int itemsCount = Math.min(4, c.parsedItems.size());
            int thisCardH = 20 + itemsCount * 12 + 4;

            boolean isProfitable = c.profit > 0 || c.isFree;
            int cardBg = c.alreadyOpened ? 0x22374151 : (isProfitable ? 0x2E10B981 : 0x2EEF4444);
            int cardBorder = c.alreadyOpened ? 0x446B7280 : (isProfitable ? 0x8810B981 : 0x88EF4444);

            g.fill(winX + 6, curY, winX + winW - 6, curY + thisCardH, cardBg);
            g.outline(winX + 6, curY, winW - 12, thisCardH, cardBorder);

            // Chest Title & Profit
            String chestName = c.chestName;
            String status = c.alreadyOpened ? "§7[CLAIMED]" : (c.profit >= 0 ? "§a+" + LowestBinManager.formatPrice(c.profit) : "§c-" + LowestBinManager.formatPrice(Math.abs(c.profit)));
            String costStr = c.isFree ? "§aFREE" : "§6" + LowestBinManager.formatPrice(c.cost);

            g.text(font, "§e" + chestName + " §8(" + costStr + "§8)", winX + 12, curY + 4, -1, false);

            int statusW = font.width(status);
            g.text(font, status, winX + winW - 14 - statusW, curY + 4, -1, false);

            // Itemized Contents
            int itemY = curY + 17;
            for (int i = 0; i < itemsCount; i++) {
                AutoCroesus.ItemDetail item = c.parsedItems.get(i);
                String itemName = item.name.replaceFirst("(?i)^Enchanted Book\\s*\\((.*?)\\)$", "$1");
                if (itemName.length() > 24) itemName = itemName.substring(0, 22) + "..";
                if (item.adjustedQuantity > 1) itemName += " x" + item.adjustedQuantity;

                String valStr = (item.totalValue > 0 ? "§a+" : "§7") + LowestBinManager.formatPrice(item.totalValue);
                g.text(font, " §7• " + itemName, winX + 12, itemY, 0xFFCBD5E1, false);
                int valW = font.width(valStr);
                g.text(font, valStr, winX + winW - 14 - valW, itemY, 0xFF86EFAC, false);
                itemY += 12;
            }

            curY += thisCardH + 4;
        }
    }
}
