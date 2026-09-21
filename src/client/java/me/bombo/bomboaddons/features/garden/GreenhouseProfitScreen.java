package me.bombo.bomboaddons.features.garden;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class GreenhouseProfitScreen extends Screen {
    private final Screen parent;
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;

    public static class Entry {
        public final String name;
        public final long count;
        public final double unitPrice;
        public final double totalValue;

        public Entry(String name, long count, double unitPrice) {
            this.name = name;
            this.count = count;
            this.unitPrice = unitPrice;
            this.totalValue = count * unitPrice;
        }
    }

    public GreenhouseProfitScreen(Screen parent) {
        super(Component.literal("Greenhouse Profit Tracker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        GreenhouseTracker.loadSessions();

        int panelW = Math.min(420, this.width - 30);
        int panelX = (this.width - panelW) / 2;
        int panelH = Math.min(270, this.height - 30);
        int panelY = (this.height - panelH) / 2;

        // Session Navigation Header Controls:
        // [<] Prev
        this.addRenderableWidget(Button.builder(Component.literal("§e<"), btn -> {
            GreenhouseTracker.prevSession();
            this.scrollAmount = 0;
        }).bounds(panelX + 12, panelY + 24, 24, 20).build());

        // [>] Next
        this.addRenderableWidget(Button.builder(Component.literal("§e>"), btn -> {
            GreenhouseTracker.nextSession();
            this.scrollAmount = 0;
        }).bounds(panelX + 40, panelY + 24, 24, 20).build());

        // [+ New Session]
        this.addRenderableWidget(Button.builder(Component.literal("§a+ New Session"), btn -> {
            GreenhouseTracker.createNewSession();
            this.scrollAmount = 0;
        }).bounds(panelX + panelW - 195, panelY + 24, 92, 20).build());

        // [Delete Session]
        this.addRenderableWidget(Button.builder(Component.literal("§cDelete"), btn -> {
            GreenhouseTracker.deleteActiveSession();
            this.scrollAmount = 0;
        }).bounds(panelX + panelW - 98, panelY + 24, 86, 20).build());

        // Footer buttons:
        // [Clear / Reset]
        this.addRenderableWidget(Button.builder(Component.literal("§eClear Items"), btn -> {
            GreenhouseTracker.clearActiveSession();
        }).bounds(panelX + 12, panelY + panelH - 26, 90, 20).build());

        // [Close]
        this.addRenderableWidget(Button.builder(Component.literal("§7Close"), btn -> {
            this.onClose();
        }).bounds(panelX + panelW - 82, panelY + panelH - 26, 70, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Dark background overlay
        g.fill(0, 0, this.width, this.height, 0xAA000000);

        Font font = this.font;
        int panelW = Math.min(420, this.width - 30);
        int panelX = (this.width - panelW) / 2;
        int panelH = Math.min(270, this.height - 30);
        int panelY = (this.height - panelH) / 2;

        // Main Panel Card
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF00F172A);
        g.outline(panelX, panelY, panelW, panelH, 0xFF10B981);

        // Header Title
        g.centeredText(font, "§a§lGreenhouse Profit Tracker", panelX + panelW / 2, panelY + 8, -1);

        // Active Session Name
        GreenhouseTracker.Session activeSession = GreenhouseTracker.getActiveSession();
        int sessIdx = GreenhouseTracker.activeSessionIndex + 1;
        int totalSess = Math.max(1, GreenhouseTracker.sessions.size());
        String sessionLabel = String.format("§f%s §7(%d/%d)", activeSession.name, sessIdx, totalSess);
        g.text(font, sessionLabel, panelX + 70, panelY + 30, -1, false);

        // Build item entries
        List<Entry> entries = new ArrayList<>();
        double grandTotal = 0.0;
        for (Map.Entry<String, Long> e : activeSession.items.entrySet()) {
            String name = e.getKey();
            long count = e.getValue();
            double price = GreenhouseTracker.getItemPrice(name);
            Entry entry = new Entry(name, count, price);
            entries.add(entry);
            grandTotal += entry.totalValue;
        }

        // Sort by highest total value first
        entries.sort((a, b) -> Double.compare(b.totalValue, a.totalValue));

        // Table Header
        int tableY = panelY + 48;
        g.fill(panelX + 10, tableY, panelX + panelW - 10, tableY + 14, 0xFF1E293B);
        g.text(font, "§8Item Name", panelX + 16, tableY + 3, -1, false);
        g.text(font, "§8Count", panelX + 175, tableY + 3, -1, false);
        g.text(font, "§8Unit Price", panelX + 250, tableY + 3, -1, false);
        g.text(font, "§8Total Value", panelX + panelW - 85, tableY + 3, -1, false);

        // Scrollable List Area
        int listTop = tableY + 16;
        int listBottom = panelY + panelH - 32;
        int listHeight = listBottom - listTop;
        int rowHeight = 16;

        int totalListHeight = entries.size() * rowHeight;
        this.maxScroll = Math.max(0, totalListHeight - listHeight);
        this.scrollAmount = Math.max(0, Math.min(this.maxScroll, this.scrollAmount));

        g.enableScissor(panelX + 10, listTop, panelX + panelW - 10, listBottom);

        if (entries.isEmpty()) {
            g.centeredText(font, "§7No crops recorded in this session.", panelX + panelW / 2, listTop + listHeight / 2 - 4, -1);
        } else {
            int currentY = listTop - (int) this.scrollAmount;
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                if (currentY + rowHeight >= listTop && currentY <= listBottom) {
                    if (i % 2 == 1) {
                        g.fill(panelX + 10, currentY, panelX + panelW - 10, currentY + rowHeight, 0x22FFFFFF);
                    }

                    // Item Name
                    g.text(font, "§f" + entry.name, panelX + 16, currentY + 4, -1, false);

                    // Count
                    String countStr = NumberFormat.getNumberInstance(Locale.US).format(entry.count);
                    g.text(font, "§e" + countStr, panelX + 175, currentY + 4, -1, false);

                    // Unit Price
                    String unitStr = formatCoins(entry.unitPrice);
                    g.text(font, "§7" + unitStr, panelX + 250, currentY + 4, -1, false);

                    // Total Value
                    String totalStr = "§6" + formatCoins(entry.totalValue);
                    g.text(font, totalStr, panelX + panelW - 85, currentY + 4, -1, false);
                }
                currentY += rowHeight;
            }
        }

        g.disableScissor();

        // Footer Total
        int footerY = panelY + panelH - 24;
        String totalProfitStr = "§6§lTotal Profit: §e" + formatCoins(grandTotal);
        g.centeredText(font, totalProfitStr, panelX + panelW / 2, footerY + 5, -1);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (this.maxScroll > 0) {
            this.scrollAmount = Math.max(0, Math.min(this.maxScroll, this.scrollAmount - vertical * 16));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
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

    private static String formatCoins(double coins) {
        if (coins >= 1_000_000_000) {
            return String.format("%.2fB", coins / 1_000_000_000.0);
        } else if (coins >= 1_000_000) {
            return String.format("%.2fM", coins / 1_000_000.0);
        } else if (coins >= 1_000) {
            return String.format("%.1fK", coins / 1_000.0);
        } else {
            return String.format("%.0f", coins);
        }
    }
}
