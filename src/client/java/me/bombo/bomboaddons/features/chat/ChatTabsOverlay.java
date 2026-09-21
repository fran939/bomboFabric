package me.bombo.bomboaddons.features.chat;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatTabsOverlay {

    public static class ChatTab {
        public final String name;
        public final String command;

        public ChatTab(String name, String command) {
            this.name = name;
            this.command = command;
        }
    }

    public static List<ChatTab> getActiveTabs() {
        BomboConfig.Settings s = BomboConfig.get();
        List<ChatTab> tabs = new ArrayList<>();
        tabs.add(new ChatTab("All", s.allTabClickCommand));
        if (s.showGuildChatTab) tabs.add(new ChatTab("Guild", s.guildTabClickCommand));
        if (s.showPartyChatTab) tabs.add(new ChatTab("Party", s.partyTabClickCommand));
        if (s.showDmChatTab) tabs.add(new ChatTab("DM", s.dmTabClickCommand));
        if (s.showBomboChatTab) tabs.add(new ChatTab("Bombo", s.bomboTabClickCommand));
        return tabs;
    }

    public static boolean matchesActiveTab(String rawText) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.chatTabs) return true;
        String active = s.activeChatTab;
        if (active == null || "All".equalsIgnoreCase(active)) return true;

        String clean = rawText.replaceAll("§.", "").trim();
        if ("Guild".equalsIgnoreCase(active)) {
            return clean.startsWith("Guild >") || clean.contains("Guild >")
                || clean.contains("Guild Name:") || clean.contains("-- Guild Master --")
                || clean.contains("-- GurtGang --") || clean.contains("-- Kevin --")
                || clean.contains("-- owen --") || clean.contains("-- sybau --")
                || clean.contains("-- Fluh --")
                || (clean.startsWith("-----------------------------------------------------") && (clean.contains("Guild") || rawText.contains("Guild")));
        } else if ("Party".equalsIgnoreCase(active)) {
            return clean.startsWith("Party >") || clean.contains("Party >")
                || clean.contains("Party Members") || clean.contains("Party Leader:")
                || clean.contains("The party was transferred to") || clean.contains("has disbanded the party")
                || clean.contains("joined the party") || clean.contains("left the party")
                || (clean.startsWith("-----------------------------------------------------") && (clean.contains("Party") || rawText.contains("Party")));
        } else if ("DM".equalsIgnoreCase(active) || "MD".equalsIgnoreCase(active)) {
            return clean.startsWith("From ") || clean.startsWith("To ") || clean.contains("From [") || clean.contains("To [");
        } else if ("Bombo".equalsIgnoreCase(active)) {
            return clean.startsWith("[Bombo]") || clean.contains("[Bombo]");
        }
        return true;
    }

    public static void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.chatTabs) return;

        Font font = mc.font;
        List<ChatTab> tabs = getActiveTabs();

        int defaultY = mc.getWindow().getGuiScaledHeight() - 28;
        int renderX = s.chatTabsX >= 0 ? s.chatTabsX : 4;
        int renderY = s.chatTabsY >= 0 ? s.chatTabsY : defaultY;

        g.pose().pushMatrix();
        g.pose().translate((float) renderX, (float) renderY);
        if (s.chatTabsScale != 1.0f && s.chatTabsScale > 0.0f) {
            g.pose().scale(s.chatTabsScale, s.chatTabsScale);
        }

        int curX = 0;
        int tabH = 14;

        for (ChatTab tab : tabs) {
            boolean isSelected = tab.name.equalsIgnoreCase(s.activeChatTab);
            int textW = font.width(tab.name);
            int tabW = textW + 12;

            int bg = isSelected ? 0xD000AAFF : 0xA0202025;
            int border = isSelected ? 0xFF00E5FF : 0x80555555;
            int textCol = isSelected ? 0xFFFFFFFF : 0xFFAAAAAA;

            g.fill(curX, 0, curX + tabW, tabH, bg);
            g.outline(curX, 0, tabW, tabH, border);
            g.text(font, tab.name, curX + 6, 3, textCol, true);

            curX += tabW + 2;
        }

        g.pose().popMatrix();
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return false;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.chatTabs) return false;

        Font font = mc.font;
        List<ChatTab> tabs = getActiveTabs();

        int defaultY = mc.getWindow().getGuiScaledHeight() - 28;
        int renderX = s.chatTabsX >= 0 ? s.chatTabsX : 4;
        int renderY = s.chatTabsY >= 0 ? s.chatTabsY : defaultY;
        float scale = s.chatTabsScale > 0.0f ? s.chatTabsScale : 1.0f;

        int curX = renderX;
        int tabH = (int) (14 * scale);

        for (ChatTab tab : tabs) {
            int textW = font.width(tab.name);
            int tabW = (int) ((textW + 12) * scale);

            if (mouseX >= curX && mouseX <= curX + tabW && mouseY >= renderY && mouseY <= renderY + tabH) {
                s.activeChatTab = tab.name;
                BomboConfig.save();

                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() instanceof me.bombo.bomboaddons.util.IChatComponent icc) {
                    icc.bombo$refreshTrimmedMessages();
                }

                if (tab.command != null && !tab.command.trim().isEmpty()) {
                    String cmd = tab.command.trim();
                    if (cmd.startsWith("/")) cmd = cmd.substring(1);
                    mc.player.connection.sendCommand(cmd);
                }
                return true;
            }
            curX += tabW + (int) (2 * scale);
        }

        return false;
    }

    public static int getWidth() {
        Font font = Minecraft.getInstance().font;
        if (font == null) return 180;
        int total = 0;
        for (ChatTab t : getActiveTabs()) {
            total += font.width(t.name) + 14;
        }
        return total;
    }

    public static int getHeight() {
        return 14;
    }
}
