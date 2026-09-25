package me.bombo.bomboaddons.gui;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import me.bombo.bomboaddons.util.ApiHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * {@code /b apihistory} - every outbound HTTP/WebSocket request the mod made, with status code and
 * response time, so a silently failing call can be seen instead of guessed at.
 */
public class ApiHistoryScreen extends Screen {

    private static final String[] SERVICE_FILTERS = {"All", "Hypixel", "Athen", "EliteSkyblock", "Bombo API", "Other"};

    private final Screen parent;
    private double scrollAmount = Double.MAX_VALUE;
    private double maxScroll = 0.0D;
    private ApiHistory.Entry hoveredEntry = null;
    private int hoveredRowY = -1;
    private int hoverIndex = -1;
    private String serviceFilter = "All";
    private boolean httpOnly = false;
    private boolean wsOnly = false;

    public ApiHistoryScreen() {
        this(null);
    }

    public ApiHistoryScreen(Screen parent) {
        super(Component.literal("BomboAddons API History"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.scrollAmount = Double.MAX_VALUE;
    }

    private List<ApiHistory.Entry> filteredEntries() {
        List<ApiHistory.Entry> all = ApiHistory.getEntries();
        List<ApiHistory.Entry> out = new ArrayList<>();
        for (ApiHistory.Entry entry : all) {
            if (httpOnly && entry.kind != ApiHistory.Kind.HTTP) continue;
            if (wsOnly && entry.kind != ApiHistory.Kind.WS) continue;
            if (!"All".equals(serviceFilter) && !serviceFilter.equalsIgnoreCase(entry.service)) continue;
            out.add(entry);
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

        int winW = Math.min(920, this.width - 40);
        int winH = Math.min(560, this.height - 40);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;
        int headerH = 62;

        g.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, 0xEE11141F);
        g.outline(winX, winY, winW, winH, ConfigUITheme.getAccentColor());
        g.fill(winX, winY, winX + winW, winY + headerH - 22, 0xEE1E293B);

        g.text(this.font, ConfigUITheme.formatFont("§b§lAPI REQUEST HISTORY"), winX + 14, winY + 10, 0xFFFFFFFF, false);

        List<ApiHistory.Entry> entries = filteredEntries();
        g.text(this.font, ApiHistory.summarize(entries) + " §8| §7showing §f" + entries.size()
                + "§7 of §f" + ApiHistory.getTotalRecorded() + " §7(" + ApiHistory.getDroppedFromMemory() + " recycled)",
                winX + 14, winY + 26, 0xFF94A3B8, false);

        // Filter row
        int fx = winX + 14;
        int fy = winY + 40;
        for (String service : SERVICE_FILTERS) {
            int w = this.font.width(service) + 14;
            boolean selected = service.equalsIgnoreCase(serviceFilter);
            boolean hovered = mouseX >= fx && mouseX <= fx + w && mouseY >= fy && mouseY <= fy + 16;
            ConfigUITheme.drawPillButton(g, this.font, (selected ? "§a" : "§7") + service, fx, fy, w, 16, hovered, -1,
                    selected ? 0x3310B981 : 0x1AFFFFFF, selected ? 0x6610B981 : 0x33FFFFFF);
            fx += w + 4;
        }
        int httpW = 46;
        int httpX = winX + winW - 250;
        ConfigUITheme.drawPillButton(g, this.font, (httpOnly ? "§a" : "§7") + "HTTP", httpX, fy, httpW, 16,
                mouseX >= httpX && mouseX <= httpX + httpW && mouseY >= fy && mouseY <= fy + 16, -1,
                httpOnly ? 0x3310B981 : 0x1AFFFFFF, httpOnly ? 0x6610B981 : 0x33FFFFFF);
        int wsX = httpX + httpW + 6;
        ConfigUITheme.drawPillButton(g, this.font, (wsOnly ? "§a" : "§7") + "WS", wsX, fy, 34, 16,
                mouseX >= wsX && mouseX <= wsX + 34 && mouseY >= fy && mouseY <= fy + 16, -1,
                wsOnly ? 0x3310B981 : 0x1AFFFFFF, wsOnly ? 0x6610B981 : 0x33FFFFFF);
        int clearX = wsX + 44;
        ConfigUITheme.drawPillButton(g, this.font, "§cClear", clearX, fy, 50, 16,
                mouseX >= clearX && mouseX <= clearX + 50 && mouseY >= fy && mouseY <= fy + 16, -1, 0x22EF4444, 0x44EF4444);
        int closeX = clearX + 56;
        ConfigUITheme.drawPillButton(g, this.font, "§7Close", closeX, fy, 50, 16,
                mouseX >= closeX && mouseX <= closeX + 50 && mouseY >= fy && mouseY <= fy + 16, -1, 0x22FFFFFF, 0x44FFFFFF);

        int contentX = winX + 10;
        int contentY = winY + headerH;
        int contentW = winW - 20;
        int contentH = winH - headerH - 14;

        if (entries.isEmpty()) {
            g.text(this.font, "§8No requests recorded yet. Toggle 'Track API Requests' in Debug settings.", contentX + 6, contentY + 8, 0xFF64748B, false);
            return;
        }

        int rowH = 18;
        int total = entries.size() * rowH;
        maxScroll = Math.max(0, total - contentH);
        if (scrollAmount > maxScroll) scrollAmount = maxScroll;

        int firstRow = (int) (scrollAmount / rowH);
        int visibleRows = contentH / rowH + 2;

        hoveredEntry = null;
        hoveredRowY = -1;
        hoverIndex = -1;

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
        for (int i = 0; i < visibleRows && firstRow + i < entries.size(); i++) {
            int index = firstRow + i;
            ApiHistory.Entry entry = entries.get(index);
            int rowY = contentY + i * rowH - (int) (scrollAmount % rowH);
            boolean hovered = mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= rowY && mouseY <= rowY + rowH - 1;
            if (hovered) {
                hoveredEntry = entry;
                hoveredRowY = rowY;
                hoverIndex = index;
            }

            g.fill(contentX, rowY, contentX + contentW, rowY + rowH - 1, index % 2 == 0 ? 0x140F172A : 0x221E293B);

            String kindColor = entry.kind == ApiHistory.Kind.WS ? "§d" : "§b";
            String statusColor = entry.status >= 200 && entry.status < 400 ? "§a"
                    : (entry.status <= 0 ? "§8" : "§c");
            String duration = entry.durationMs > 0L ? entry.durationMs + "ms" : "-";
            String note = entry.note != null && !entry.note.isEmpty() ? " §8(" + entry.note + "§8)" : "";

            g.text(this.font, "§8" + ApiHistory.formatTime(entry.timestamp), contentX + 6, rowY + 5, 0xFF64748B, false);
            g.text(this.font, kindColor + entry.kind.label, contentX + 82, rowY + 5, 0xFFFFFFFF, false);
            g.text(this.font, "§7" + entry.method, contentX + 118, rowY + 5, 0xFFCBD5E1, false);
            g.text(this.font, statusColor + ApiHistory.statusText(entry.status), contentX + 186, rowY + 5, 0xFFFFFFFF, false);
            g.text(this.font, "§e" + duration, contentX + 236, rowY + 5, 0xFFFFFFFF, false);
            g.text(this.font, "§8[" + entry.service + "]", contentX + 310, rowY + 5, 0xFF94A3B8, false);
            g.text(this.font, "§f" + ApiHistory.shorten(entry.url) + note, contentX + 400, rowY + 5, 0xFFE2E8F0, false);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int scrollbarX = contentX + contentW - 5;
            g.fill(scrollbarX, contentY, scrollbarX + 4, contentY + contentH, 0x22FFFFFF);
            int thumbH = Math.max(18, (int) ((contentH / (float) total) * contentH));
            int thumbY = contentY + (int) ((scrollAmount / maxScroll) * (contentH - thumbH));
            g.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, 0x8800E5FF);
        }

        // Hover tooltip: full URL + response note, so nothing is truncated away.
        if (hoveredEntry != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§b§l" + hoveredEntry.service + " §7- " + hoveredEntry.kind.label + " " + hoveredEntry.method));
            tooltip.add(Component.literal("§7Status: " + (hoveredEntry.status == -1 ? "§8issued (no response seen)"
                    : (hoveredEntry.status == 0 ? "§cno response" : "§f" + hoveredEntry.status))
                    + (hoveredEntry.durationMs > 0L ? " §8| §7took §e" + hoveredEntry.durationMs + "ms" : "")));
            tooltip.add(Component.literal("§7" + hoveredEntry.url));
            if (hoveredEntry.headers != null && !hoveredEntry.headers.isEmpty()) {
                tooltip.add(Component.literal("§7Headers: §f" + hoveredEntry.headers));
            }
            if (hoveredEntry.note != null && !hoveredEntry.note.isEmpty()) {
                tooltip.add(Component.literal("§8" + hoveredEntry.note));
            }
            tooltip.add(Component.literal("§8Right-click a row to copy the URL"));
            g.setTooltipForNextFrame(this.font, tooltip, java.util.Optional.empty(), (int) mouseX, (int) mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (maxScroll > 0) {
            scrollAmount -= amountY * 28.0D;
            scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() != 0 && event.button() != 1) {
            return super.mouseClicked(event, handled);
        }
        int winW = Math.min(920, this.width - 40);
        int winH = Math.min(560, this.height - 40);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;
        int fy = winY + 40;

        int fx = winX + 14;
        for (String service : SERVICE_FILTERS) {
            int w = this.font.width(service) + 14;
            if (event.x() >= fx && event.x() <= fx + w && event.y() >= fy && event.y() <= fy + 16) {
                serviceFilter = service;
                scrollAmount = 0;
                return true;
            }
            fx += w + 4;
        }

        int httpX = winX + winW - 250;
        if (event.x() >= httpX && event.x() <= httpX + 46 && event.y() >= fy && event.y() <= fy + 16) {
            httpOnly = !httpOnly;
            if (httpOnly) wsOnly = false;
            return true;
        }
        int wsX = httpX + 52;
        if (event.x() >= wsX && event.x() <= wsX + 34 && event.y() >= fy && event.y() <= fy + 16) {
            wsOnly = !wsOnly;
            if (wsOnly) httpOnly = false;
            return true;
        }
        int clearX = wsX + 44;
        if (event.x() >= clearX && event.x() <= clearX + 50 && event.y() >= fy && event.y() <= fy + 16) {
            ApiHistory.clear();
            scrollAmount = 0;
            return true;
        }
        int closeX = clearX + 56;
        if (event.x() >= closeX && event.x() <= closeX + 50 && event.y() >= fy && event.y() <= fy + 16) {
            this.onClose();
            return true;
        }

        // Copy a row's URL.
        if (event.button() == 1 && hoveredEntry != null) {
            Minecraft.getInstance().keyboardHandler.setClipboard(hoveredEntry.url);
            return true;
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            scrollAmount = 0;
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            scrollAmount = maxScroll;
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
