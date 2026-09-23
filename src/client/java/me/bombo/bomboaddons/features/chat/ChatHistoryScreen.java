package me.bombo.bomboaddons.features.chat;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatHistoryScreen extends Screen {
    private final Screen parent;

    private int winX, winY, winW, winH;
    private final int headerH = 40;
    private final int controlsH = 34;

    /**
     * Filters the user asked for, in order: everything, this mod, any mod, plain server/player
     * chat, blocked messages, and what the client sent.
     */
    public enum FilterTab {
        ALL("All"),
        BOMBO("BomboAddons"),
        MODS("Mods Only"),
        NORMAL("Normal Chat"),
        BLOCKED("Blocked Only"),
        OUTGOING("Outgoing");

        public final String label;
        FilterTab(String label) {
            this.label = label;
        }
    }

    private FilterTab activeTab = FilterTab.ALL;
    private final FilterTab initialTab;
    /** {@code /b chathistory auto}: restrict to feature events regardless of the active tab. */
    private final boolean eventsOnly;
    private boolean eventsChipOn;

    private String searchQuery = "";
    private boolean searchFocused = false;

    private int scrollOffset = 0;
    /** When set, the next render snaps the view to the newest entry. */
    private boolean snapToBottom = true;
    private final int rowH = 13;

    private ChatHistoryTracker.Entry hoveredEntry = null;

    private String toastMessage = null;
    private long toastExpiry = 0;

    public ChatHistoryScreen(Screen parent) {
        this(parent, FilterTab.ALL, false);
    }

    public ChatHistoryScreen(Screen parent, FilterTab initialTab) {
        this(parent, initialTab, false);
    }

    public ChatHistoryScreen(Screen parent, FilterTab initialTab, boolean eventsOnly) {
        super(Component.literal("Chat History"));
        this.parent = parent;
        this.initialTab = initialTab != null ? initialTab : FilterTab.ALL;
        this.activeTab = this.initialTab;
        this.eventsOnly = eventsOnly;
        this.eventsChipOn = eventsOnly;
    }

    @Override
    protected void init() {
        int targetW = 860;
        int targetH = 540;
        this.winW = Math.min(this.width - 20, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
        this.activeTab = this.initialTab;
        this.snapToBottom = true;
    }

    /**
     * Single source of truth for the list geometry. Render and click handling used to
     * disagree by two pixels, which made the bottom row unclickable.
     */
    private int controlsTop() {
        return winY + headerH + 6;
    }

    private int listTop() {
        return controlsTop() + controlsH + 4;
    }

    private int listRowsTop() {
        return listTop() + 16;
    }

    private int listVisibleRows() {
        return Math.max(0, (winY + winH - 24 - listRowsTop()) / rowH);
    }

    private boolean isOutgoing(ChatHistoryTracker.Entry e) {
        return e.status == ChatHistoryTracker.Status.OUTGOING;
    }

    private boolean isEvent(ChatHistoryTracker.Entry e) {
        return e.status == ChatHistoryTracker.Status.EVENT
                || e.status == ChatHistoryTracker.Status.BLOCKED_EVENT;
    }

    private boolean isBlocked(ChatHistoryTracker.Entry e) {
        return e.status == ChatHistoryTracker.Status.BLOCKED
                || e.status == ChatHistoryTracker.Status.BLOCKED_EVENT;
    }

    private List<ChatHistoryTracker.Entry> getFilteredEntries() {
        List<ChatHistoryTracker.Entry> all = ChatHistoryTracker.getEntries();
        List<ChatHistoryTracker.Entry> filtered = new ArrayList<>();

        String q = searchQuery.toLowerCase(Locale.ROOT).trim();

        for (ChatHistoryTracker.Entry e : all) {
            // Feature-event only mode (from /b chathistory auto or the Events chip).
            if (eventsChipOn && !isEvent(e)) continue;

            switch (activeTab) {
                case BOMBO -> {
                    // Anything this mod produced: its own feature events and its own chat lines.
                    if (!e.isBombo && !isEvent(e)) continue;
                }
                case MODS -> {
                    if (!e.isMod && !isEvent(e)) continue;
                }
                case NORMAL -> {
                    // Plain player/server chat, including anything a mod blocked.
                    if (e.isMod || isEvent(e) || isOutgoing(e)) continue;
                }
                case BLOCKED -> {
                    if (!isBlocked(e)) continue;
                }
                case OUTGOING -> {
                    if (!isOutgoing(e)) continue;
                }
                default -> {
                }
            }

            if (!q.isEmpty()) {
                boolean match = false;
                if (e.rawText != null && e.rawText.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.category != null && e.category.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.callerModName != null && e.callerModName.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.callerModId != null && e.callerModId.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.callerFrame != null && e.callerFrame.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.featureName != null && e.featureName.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.originFeature != null && e.originFeature.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.triggerDesc != null && e.triggerDesc.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match && e.clickAction != null && e.clickAction.toLowerCase(Locale.ROOT).contains(q)) match = true;
                if (!match) continue;
            }

            filtered.add(e);
        }

        return filtered;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        hoveredEntry = null;

        // Dark backdrop
        g.fill(0, 0, this.width, this.height, 0xD0080A0E);

        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int borderCol = ConfigUITheme.getBorderColor();
        int divCol = ConfigUITheme.getDividerColor();
        Font font = this.font;

        // Window outline & bg
        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);
        g.outline(winX, winY, winW, winH, borderCol);

        // Header
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.fill(winX, winY + headerH, winX + winW, winY + headerH + 1, divCol);

        g.text(font, "§d§lBOMBOADDONS §8| §bChat History (/b chathistory)", winX + 16, winY + 14, 0xFFFFFFFF, false);

        BomboConfig.Settings cfg = BomboConfig.get();
        int curLimit = cfg != null ? cfg.chatHistoryMaxMessages : 500;
        g.text(font, "§7Max History: §e" + curLimit, winX + winW - 270, winY + 14, 0xFFCBD5E1, false);

        int clrBtnX = winX + winW - 145;
        int clrBtnY = winY + 9;
        boolean clrHover = mouseX >= clrBtnX && mouseX <= clrBtnX + 75 && mouseY >= clrBtnY && mouseY <= clrBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, "🗑 Clear", clrBtnX, clrBtnY, 75, 22, clrHover,
                0xFFCBD5E1, clrHover ? 0x44EF4444 : 0x22EF4444, 0xFFEF4444);

        int closeBtnX = winX + winW - 60;
        int closeBtnY = winY + 9;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 50 && mouseY >= closeBtnY && mouseY <= closeBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, "Done", closeBtnX, closeBtnY, 50, 22, closeHover,
                0xFFFFFFFF, closeHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        // Controls bar (Tabs & Search)
        int ctrlY = controlsTop();
        g.fill(winX, ctrlY + controlsH, winX + winW, ctrlY + controlsH + 1, divCol);

        int tabX = winX + 14;
        for (FilterTab tab : FilterTab.values()) {
            int tabW = font.width(tab.label) + 16;
            boolean isSel = (activeTab == tab);
            boolean tHover = mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= ctrlY && mouseY <= ctrlY + 24;

            g.fill(tabX, ctrlY, tabX + tabW, ctrlY + 24, isSel ? 0xFF0284C7 : (tHover ? 0x441E293B : 0x221E293B));
            g.outline(tabX, ctrlY, tabW, 24, isSel ? 0xFF38BDF8 : (tHover ? 0x8864748B : 0x44475569));
            g.centeredText(font, tab.label, tabX + tabW / 2, ctrlY + 8, isSel ? 0xFFFFFFFF : (tHover ? 0xFFE2E8F0 : 0xFF94A3B8));

            tabX += (tabW + 6);
        }

        // "Events" chip: the Auto Sequences log without losing the requested tab set.
        int chipW = font.width("Events") + 16;
        boolean chipHover = mouseX >= tabX && mouseX <= tabX + chipW && mouseY >= ctrlY && mouseY <= ctrlY + 24;
        g.fill(tabX, ctrlY, tabX + chipW, ctrlY + 24, eventsChipOn ? 0xFF7C3AED : (chipHover ? 0x441E293B : 0x221E293B));
        g.outline(tabX, ctrlY, chipW, 24, eventsChipOn ? 0xFFA855F7 : (chipHover ? 0x8864748B : 0x44475569));
        g.centeredText(font, "Events", tabX + chipW / 2, ctrlY + 8, eventsChipOn ? 0xFFFFFFFF : 0xFF94A3B8);

        // Search Bar on right side of controls
        int sBoxW = 220;
        int sBoxX = winX + winW - sBoxW - 14;
        int sBoxH = 24;
        g.fill(sBoxX, ctrlY, sBoxX + sBoxW, ctrlY + sBoxH, 0xEE1E293B);
        g.outline(sBoxX, ctrlY, sBoxW, sBoxH, searchFocused ? 0xFF38BDF8 : 0x4464748B);

        String displaySearch = searchQuery.isEmpty() && !searchFocused ? "§8Search messages/mods..." : searchQuery;
        g.text(font, displaySearch + (searchFocused && (System.currentTimeMillis() / 400 % 2 == 0) ? "_" : ""), sBoxX + 8, ctrlY + 8, searchFocused ? 0xFFFFFFFF : 0xFF94A3B8, false);

        if (!searchQuery.isEmpty()) {
            int clearX = sBoxX + sBoxW - 18;
            boolean clearHover = mouseX >= clearX && mouseX <= clearX + 14 && mouseY >= ctrlY + 4 && mouseY <= ctrlY + 20;
            g.text(font, "✕", clearX, ctrlY + 8, clearHover ? 0xFFEF4444 : 0xFF94A3B8, false);
        }

        // Table List Area
        int listTop = listTop();
        int listBottom = winY + winH - 24;

        List<ChatHistoryTracker.Entry> list = getFilteredEntries();

        // Table Header
        g.fill(winX + 12, listTop, winX + winW - 12, listTop + 14, 0x660F172A);
        g.text(font, "§7STATUS", winX + 16, listTop + 3, 0xFF94A3B8, false);
        g.text(font, "§7CATEGORY", winX + 76, listTop + 3, 0xFF94A3B8, false);
        g.text(font, "§7SOURCE", winX + 146, listTop + 3, 0xFF94A3B8, false);
        g.text(font, "§7MESSAGE / COMMAND CONTENT", winX + 256, listTop + 3, 0xFF94A3B8, false);
        g.text(font, "§7TIME", winX + winW - 68, listTop + 3, 0xFF94A3B8, false);

        int rowsTop = listRowsTop();
        int visibleRows = listVisibleRows();
        int maxScroll = Math.max(0, list.size() - visibleRows);

        // Open at (and return to) the newest entry - the bottom of the log.
        if (snapToBottom) {
            scrollOffset = maxScroll;
            snapToBottom = false;
        }
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        for (int i = 0; i < visibleRows && (i + scrollOffset) < list.size(); i++) {
            ChatHistoryTracker.Entry e = list.get(i + scrollOffset);
            int ry = rowsTop + i * rowH;

            boolean rHover = mouseX >= winX + 12 && mouseX <= winX + winW - 24 && mouseY >= ry && mouseY < ry + rowH;
            if (rHover) {
                hoveredEntry = e;
                g.fill(winX + 12, ry, winX + winW - 24, ry + rowH, 0x3338BDF8);
            } else if (i % 2 == 1) {
                g.fill(winX + 12, ry, winX + winW - 24, ry + rowH, 0x11FFFFFF);
            }

            // Status Badge
            int statusCol = e.status != null ? e.status.color : 0xFFFFFFFF;
            String statusLbl = e.status != null ? e.status.label : "ALLOWED";
            g.text(font, statusLbl, winX + 16, ry + 2, statusCol, false);

            // Category tag
            String cat = e.category != null ? e.category : "CHAT";
            g.text(font, "§7" + cat, winX + 76, ry + 2, 0xFFCBD5E1, false);

            // Source: who actually produced the row (mod name, server, player input).
            // featureName is the closest thing to an author for our own keybind/feature sends.
            String source = e.callerModName != null ? e.callerModName
                    : (e.featureName != null ? e.featureName : "Unknown");
            int sourceCol = e.isBombo ? 0xFFA855F7 : (e.isMod ? 0xFF38BDF8 : 0xFF94A3B8);
            g.text(font, font.plainSubstrByWidth(source, 105), winX + 146, ry + 2, sourceCol, false);

            // Content Component (truncated if too long to prevent screen overflow)
            int msgX = winX + 256;
            int maxMsgW = winW - 336;
            if (e.component != null) {
                String full = e.rawText != null ? e.rawText : e.component.getString();
                if (font.width(full) > maxMsgW) {
                    String trimmed = font.plainSubstrByWidth(full, maxMsgW - 12) + "...";
                    g.text(font, trimmed, msgX, ry + 2, 0xFFFFFFFF, false);
                } else {
                    g.text(font, e.component, msgX, ry + 2, 0xFFFFFFFF, false);
                }
            } else {
                g.text(font, e.rawText != null ? e.rawText : "", msgX, ry + 2, 0xFFFFFFFF, false);
            }

            // Time
            String time = e.timeFormatted != null ? e.timeFormatted : "";
            g.text(font, "§8" + time, winX + winW - 68, ry + 2, 0xFF64748B, false);
        }

        // Scrollbar
        if (list.size() > visibleRows) {
            int sbX = winX + winW - 18;
            int sbY = rowsTop;
            int sbH = visibleRows * rowH;
            g.fill(sbX, sbY, sbX + 4, sbY + sbH, 0x44000000);

            float ratio = (float) visibleRows / list.size();
            int thumbH = Math.max(16, (int) (sbH * ratio));
            int thumbY = maxScroll == 0 ? sbY : sbY + (int) ((float) scrollOffset / maxScroll * (sbH - thumbH));
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF38BDF8);
        }

        // Bottom status & count
        int botY = winY + winH - 18;
        String countInfo = "§8Showing " + list.size() + " of "
                + ChatHistoryTracker.getEntries().size()
                + " entries | Left-click copy | Right-click run command | End = newest";
        g.text(font, countInfo, winX + 16, botY, 0xFF94A3B8, false);

        // Render Toast message if active
        if (toastMessage != null && System.currentTimeMillis() < toastExpiry) {
            int tw = font.width(toastMessage) + 24;
            int tx = winX + (winW - tw) / 2;
            int ty = winY + winH - 46;
            g.fill(tx, ty, tx + tw, ty + 20, 0xEE10B981);
            g.outline(tx, ty, tw, 20, 0xFFFFFFFF);
            g.centeredText(font, toastMessage, tx + tw / 2, ty + 6, 0xFFFFFFFF);
        }

        // Render Rich Tooltip on top of everything
        if (hoveredEntry != null) {
            renderEntryTooltip(g, font, hoveredEntry, mouseX, mouseY);
        }
    }

    private void renderEntryTooltip(GuiGraphicsExtractor g, Font font, ChatHistoryTracker.Entry entry, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();

        if (entry.isEvent || entry.status == ChatHistoryTracker.Status.BLOCKED_EVENT) {
            lines.add("§8── §dFeature Event §8──");
        }
        if (entry.originFeature != null) {
            lines.add("§7Origin: §d" + entry.originFeature);
        }
        if (entry.triggerDesc != null && !entry.triggerDesc.trim().isEmpty()) {
            lines.add("§7Trigger: §6" + entry.triggerDesc);
        }
        if (entry.featureName != null) {
            lines.add("§7Feature: §d" + entry.featureName);
        }

        if (entry.isMod) {
            // Never render a bare null: id is a real value even when the display name is missing.
            String who = entry.callerModName != null ? entry.callerModName
                    : (entry.callerModId != null ? entry.callerModId : "Unknown");
            lines.add("§7Created by: §a" + who);
            lines.add("§7Mod id: §b" + (entry.callerModId != null ? entry.callerModId : "unknown"));
        } else {
            lines.add("§7Source: §f" + (entry.callerModName != null ? entry.callerModName : "Unknown"));
        }
        if (entry.callerFrame != null) {
            lines.add("§8" + entry.callerFrame);
        }

        if (entry.clickAction != null) {
            lines.add("§7Click Action: §6" + entry.clickAction);
        }
        if (entry.hoverText != null && !entry.hoverText.trim().isEmpty()) {
            lines.add("§7Hover Data: §f" + entry.hoverText.trim());
        }
        if (entry.status == ChatHistoryTracker.Status.OUTGOING) {
            lines.add("§7Outgoing: §c" + entry.rawText);
        }
        lines.add("§8Time: " + (entry.timeFormatted != null ? entry.timeFormatted : ""));

        int maxW = 0;
        for (String l : lines) {
            int w = font.width(l);
            if (w > maxW) maxW = w;
        }

        int pad = 8;
        int tipW = maxW + pad * 2;
        int tipH = lines.size() * 12 + pad * 2;

        int tipX = mouseX + 12;
        int tipY = mouseY + 12;

        if (tipX + tipW > this.width - 10) {
            tipX = this.width - tipW - 10;
        }
        if (tipY + tipH > this.height - 10) {
            tipY = mouseY - tipH - 4;
        }
        if (tipX < 4) tipX = 4;
        if (tipY < 4) tipY = 4;

        g.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF80A0F1D);
        g.outline(tipX, tipY, tipW, tipH, 0xFF8B5CF6);

        for (int li = 0; li < lines.size(); li++) {
            g.text(font, lines.get(li), tipX + pad, tipY + pad + li * 12, 0xFFFFFFFF, false);
        }
    }

    private void showToast(String message) {
        this.toastMessage = message;
        this.toastExpiry = System.currentTimeMillis() + 2000;
    }

    private void scrollBy(int rows) {
        scrollOffset += rows;
        snapToBottom = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scrollBy(-(int) (verticalAmount * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Close button
        int closeBtnX = winX + winW - 60;
        int closeBtnY = winY + 9;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 50 && mouseY >= closeBtnY && mouseY <= closeBtnY + 22) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        // Clear history button
        int clrBtnX = winX + winW - 145;
        int clrBtnY = winY + 9;
        if (mouseX >= clrBtnX && mouseX <= clrBtnX + 75 && mouseY >= clrBtnY && mouseY <= clrBtnY + 22) {
            ChatHistoryTracker.clear();
            showToast("Chat History Cleared!");
            return true;
        }

        // Controls bar tabs + events chip
        int ctrlY = controlsTop();
        int tabX = winX + 14;
        for (FilterTab tab : FilterTab.values()) {
            int tabW = font.width(tab.label) + 16;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= ctrlY && mouseY <= ctrlY + 24) {
                activeTab = tab;
                snapToBottom = true;
                return true;
            }
            tabX += (tabW + 6);
        }
        int chipW = font.width("Events") + 16;
        if (mouseX >= tabX && mouseX <= tabX + chipW && mouseY >= ctrlY && mouseY <= ctrlY + 24) {
            eventsChipOn = !eventsChipOn;
            snapToBottom = true;
            return true;
        }

        // Search Bar click
        int sBoxW = 220;
        int sBoxX = winX + winW - sBoxW - 14;
        int sBoxH = 24;
        if (mouseX >= sBoxX && mouseX <= sBoxX + sBoxW && mouseY >= ctrlY && mouseY <= ctrlY + sBoxH) {
            if (!searchQuery.isEmpty() && mouseX >= sBoxX + sBoxW - 18) {
                searchQuery = "";
            }
            searchFocused = true;
            return true;
        } else {
            searchFocused = false;
        }

        // Table Rows click
        int rowsTop = listRowsTop();
        int visibleRows = listVisibleRows();

        List<ChatHistoryTracker.Entry> list = getFilteredEntries();
        for (int i = 0; i < visibleRows && (i + scrollOffset) < list.size(); i++) {
            int ry = rowsTop + i * rowH;
            if (mouseX >= winX + 12 && mouseX <= winX + winW - 24 && mouseY >= ry && mouseY < ry + rowH) {
                ChatHistoryTracker.Entry e = list.get(i + scrollOffset);
                if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    String toCopy = e.rawText != null ? e.rawText : (e.component != null ? e.component.getString() : "");
                    Minecraft.getInstance().keyboardHandler.setClipboard(toCopy);
                    showToast("Copied to clipboard!");
                    return true;
                } else if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && mc.player.connection != null) {
                        String cmd = null;
                        if (e.clickAction != null && e.clickAction.startsWith("RUN_COMMAND: ")) {
                            cmd = e.clickAction.substring("RUN_COMMAND: ".length()).trim();
                        } else if (e.rawText != null && e.rawText.trim().startsWith("/")) {
                            cmd = e.rawText.trim();
                        }
                        if (cmd != null) {
                            String toSend = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                            mc.player.connection.sendCommand(toSend);
                            showToast("Executed: /" + toSend);
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (searchFocused) {
            searchQuery += c;
            snapToBottom = true;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (searchFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    snapToBottom = true;
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
        }

        switch (event.key()) {
            case GLFW.GLFW_KEY_END -> {
                snapToBottom = true;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                scrollOffset = 0;
                snapToBottom = false;
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scrollBy(-listVisibleRows());
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollBy(listVisibleRows());
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                Minecraft.getInstance().setScreenAndShow(this.parent);
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(event);
    }
}
