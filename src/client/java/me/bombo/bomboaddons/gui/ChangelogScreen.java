package me.bombo.bomboaddons.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ChangelogScreen extends Screen {
    private final Screen parent;
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private boolean loading = true;
    private String errorMessage = null;
    private final List<ReleaseEntry> releases = new ArrayList<>();
    private String targetVersion = null;
    private boolean scrolledToTarget = false;

    public static class ReleaseEntry {
        public final String version;
        public final String date;
        public final List<String> changes;

        public ReleaseEntry(String version, String date, List<String> changes) {
            this.version = version;
            this.date = date;
            this.changes = changes;
        }
    }

    public static class FormattedLine {
        public final String bullet;
        public final int color;
        public final String text;
        public final boolean isSubBullet;

        public FormattedLine(String raw) {
            if (raw == null) {
                this.bullet = "§7• ";
                this.color = 0xFFA0AEC0;
                this.text = "";
                this.isSubBullet = false;
                return;
            }
            this.isSubBullet = raw.startsWith("  ") || raw.startsWith("\t");
            String trimmed = raw.trim();
            if (trimmed.startsWith("Feature: ") || trimmed.startsWith("Added: ") || trimmed.startsWith("+ ") || trimmed.startsWith("+")) {
                this.bullet = "§a+ ";
                this.color = 0xFF4ADE80;
                String c = trimmed.startsWith("Feature: ") ? trimmed.substring(9) : (trimmed.startsWith("Added: ") ? trimmed.substring(7) : (trimmed.startsWith("+ ") ? trimmed.substring(2) : trimmed.substring(1)));
                this.text = formatTitleDesc(c);
            } else if (trimmed.startsWith("Removed: ") || trimmed.startsWith("Deleted: ") || trimmed.startsWith("- ") || trimmed.startsWith("-")) {
                this.bullet = "§c- ";
                this.color = 0xFFF87171;
                String c = trimmed.startsWith("Removed: ") ? trimmed.substring(9) : (trimmed.startsWith("Deleted: ") ? trimmed.substring(9) : (trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed.substring(1)));
                this.text = formatTitleDesc(c);
            } else if (trimmed.startsWith("Fix: ") || trimmed.startsWith("Fixed: ") || trimmed.startsWith("~ ") || trimmed.startsWith("~")) {
                this.bullet = "§b~ ";
                this.color = 0xFF38BDF8;
                String c = trimmed.startsWith("Fix: ") ? trimmed.substring(5) : (trimmed.startsWith("Fixed: ") ? trimmed.substring(7) : (trimmed.startsWith("~ ") ? trimmed.substring(2) : trimmed.substring(1)));
                this.text = formatTitleDesc(c);
            } else if (trimmed.startsWith("Improvement: ") || trimmed.startsWith("Improved: ") || trimmed.startsWith("* ") || trimmed.startsWith("*")) {
                this.bullet = "§e* ";
                this.color = 0xFFFBBF24;
                String c = trimmed.startsWith("Improvement: ") ? trimmed.substring(13) : (trimmed.startsWith("Improved: ") ? trimmed.substring(10) : (trimmed.startsWith("* ") ? trimmed.substring(2) : trimmed.substring(1)));
                this.text = formatTitleDesc(c);
            } else {
                this.bullet = isSubBullet ? "§8- " : "§7• ";
                this.color = 0xFFA0AEC0;
                this.text = formatTitleDesc(trimmed);
            }
        }

        private static String formatTitleDesc(String str) {
            int dashIdx = str.indexOf(" - ");
            if (dashIdx > 0 && dashIdx < 50) {
                String title = str.substring(0, dashIdx).trim();
                String desc = str.substring(dashIdx + 3).trim();
                return "§f§l" + title + " §8- §7" + desc;
            }
            int colonIdx = str.indexOf(": ");
            if (colonIdx > 0 && colonIdx < 50) {
                String title = str.substring(0, colonIdx).trim();
                String desc = str.substring(colonIdx + 2).trim();
                return "§f§l" + title + " §8- §7" + desc;
            }
            return "§f" + str;
        }
    }

    public ChangelogScreen(Screen parent) {
        super(Component.literal("BomboAddons Changelog"));
        this.parent = parent;
    }

    public ChangelogScreen(Screen parent, String targetVersion) {
        this(parent);
        this.targetVersion = targetVersion;
    }

    public ChangelogScreen(Screen parent, JsonArray arr) {
        this(parent);
        parseJson(arr);
        this.loading = false;
    }

    @Override
    protected void init() {
        super.init();
        if (loading && releases.isEmpty()) {
            fetchChangelogAsync();
        }
    }

    private void parseJson(JsonArray arr) {
        releases.clear();
        if (arr == null) return;
        for (JsonElement el : arr) {
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                String ver = obj.has("version") ? obj.get("version").getAsString() : "Unknown";
                String date = obj.has("date") ? obj.get("date").getAsString() : "";
                List<String> changes = new ArrayList<>();
                if (obj.has("changes") && obj.get("changes").isJsonArray()) {
                    for (JsonElement c : obj.getAsJsonArray("changes")) {
                        changes.add(c.getAsString());
                    }
                }
                releases.add(new ReleaseEntry(ver, date, changes));
            }
        }
    }

    private void fetchChangelogAsync() {
        new Thread(() -> {
            try {
                String changelogUrl = "https://api.bombo.dpdns.org/mod/changelog";
                HttpURLConnection conn = (HttpURLConnection) (new URL(changelogUrl)).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    JsonArray arr = JsonParser.parseReader(reader).getAsJsonArray();
                    Minecraft.getInstance().execute(() -> {
                        parseJson(arr);
                        loading = false;
                    });
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    try {
                        errorMessage = "Server returned status: " + conn.getResponseCode();
                    } catch (Exception ignored) {}
                    loading = false;
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    errorMessage = e.getMessage();
                    loading = false;
                });
            }
        }).start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent background
        g.fill(0, 0, this.width, this.height, 0xD00A0D14);

        int winW = Math.min(this.width - 40, 680);
        int winH = Math.min(this.height - 40, 480);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int bg = ConfigUITheme.getMainWindowBg();
        int border = ConfigUITheme.getBorderColor();
        int accent = ConfigUITheme.getAccentColor();

        // Main window panel
        g.fill(winX, winY, winX + winW, winY + winH, bg);
        g.outline(winX, winY, winW, winH, border);

        Font font = Minecraft.getInstance().font;

        // Header
        g.text(font, "§6§lBomboAddons Changelog", winX + 16, winY + 14, 0xFFFFFFFF, true);
        g.text(font, "§7Recent updates and patch notes", winX + 16, winY + 28, 0xFFA0AEC0, false);

        // Close [ESC / X] button
        int closeW = 24;
        int closeH = 20;
        int closeX = winX + winW - closeW - 12;
        int closeY = winY + 12;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        g.fill(closeX, closeY, closeX + closeW, closeY + closeH, closeHovered ? 0xFFEF4444 : 0xFF2D3748);
        g.outline(closeX, closeY, closeW, closeH, 0xFF4A5568);
        g.centeredText(font, "✕", closeX + closeW / 2, closeY + 5, 0xFFFFFFFF);

        // Divider
        g.fill(winX + 12, winY + 44, winX + winW - 12, winY + 45, border);

        // Content Area
        int contentX = winX + 16;
        int contentY = winY + 52;
        int contentW = winW - 32;
        int contentH = winH - 64;

        if (loading) {
            g.centeredText(font, "§7Loading changelog...", winX + winW / 2, winY + winH / 2, 0xFFFFFFFF);
            return;
        }

        if (errorMessage != null) {
            g.centeredText(font, "§cFailed to load changelog: §f" + errorMessage, winX + winW / 2, winY + winH / 2, 0xFFFFFFFF);
            return;
        }

        if (releases.isEmpty()) {
            g.centeredText(font, "§7No changelog entries found.", winX + winW / 2, winY + winH / 2, 0xFFFFFFFF);
            return;
        }

        // Calculate total content height
        int totalHeight = 0;
        for (ReleaseEntry r : releases) {
            totalHeight += 24; // Header row
            int cardInnerHeight = 8;
            for (String c : r.changes) {
                FormattedLine fl = new FormattedLine(c);
                int textW = contentW - 24 - (fl.isSubBullet ? 12 : 0);
                var wrapped = font.getSplitter().splitLines(Component.literal(fl.bullet + fl.text), Math.max(80, textW), net.minecraft.network.chat.Style.EMPTY);
                cardInnerHeight += Math.max(1, wrapped.size()) * 12;
            }
            totalHeight += cardInnerHeight + 8; // Card height + gap
        }

        this.maxScroll = Math.max(0, totalHeight - contentH);
        if (targetVersion != null && !scrolledToTarget && !releases.isEmpty()) {
            int targetYOffset = 0;
            for (ReleaseEntry r : releases) {
                if (r.version.equalsIgnoreCase(targetVersion) || ("v" + r.version).equalsIgnoreCase(targetVersion)) {
                    this.scrollAmount = Math.max(0, Math.min(targetYOffset, this.maxScroll));
                    this.scrolledToTarget = true;
                    break;
                }
                targetYOffset += 24;
                int cardInner = 8;
                for (String c : r.changes) {
                    FormattedLine fl = new FormattedLine(c);
                    int textW = contentW - 24 - (fl.isSubBullet ? 12 : 0);
                    var wrapped = font.getSplitter().splitLines(Component.literal(fl.bullet + fl.text), Math.max(80, textW), net.minecraft.network.chat.Style.EMPTY);
                    cardInner += Math.max(1, wrapped.size()) * 12;
                }
                targetYOffset += cardInner + 8;
            }
            this.scrolledToTarget = true;
        }

        if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
        if (this.scrollAmount < 0) this.scrollAmount = 0;

        // Render visible entries
        int curY = (int) (contentY - this.scrollAmount);

        for (ReleaseEntry r : releases) {
            boolean isTarget = targetVersion != null && (r.version.equalsIgnoreCase(targetVersion) || ("v" + r.version).equalsIgnoreCase(targetVersion));
            // Release badge + version title
            if (curY + 20 >= contentY && curY <= contentY + contentH) {
                String badgeText = (isTarget ? "§b§l" : "§b") + "v" + r.version;
                int badgeW = font.width(badgeText) + 12;
                g.fill(contentX, curY, contentX + badgeW, curY + 16, isTarget ? 0x6600E5FF : 0x3300E5FF);
                g.outline(contentX, curY, badgeW, 16, isTarget ? 0xFF00E5FF : accent);
                g.text(font, badgeText, contentX + 6, curY + 4, accent, true);

                int dateOffset = 0;
                if (!r.date.isEmpty()) {
                    String dateStr = "§8(" + r.date + ")";
                    g.text(font, dateStr, contentX + badgeW + 8, curY + 4, 0xFFA0AEC0, false);
                    dateOffset = font.width(dateStr) + 8;
                }
                if (isTarget) {
                    g.text(font, "§e★ Selected Version", contentX + badgeW + 8 + dateOffset, curY + 4, 0xFFFBBF24, false);
                }
            }
            curY += 20;

            // Compute card height for this release
            int cardH = 8;
            for (String c : r.changes) {
                FormattedLine fl = new FormattedLine(c);
                int textW = contentW - 24 - (fl.isSubBullet ? 12 : 0);
                var wrapped = font.getSplitter().splitLines(Component.literal(fl.bullet + fl.text), Math.max(80, textW), net.minecraft.network.chat.Style.EMPTY);
                cardH += Math.max(1, wrapped.size()) * 12;
            }

            // Draw card background & border
            if (curY + cardH >= contentY && curY <= contentY + contentH) {
                g.fill(contentX, curY, contentX + contentW, curY + cardH, 0x221E293B);
                g.outline(contentX, curY, contentW, cardH, 0x44475569);
            }

            int itemY = curY + 5;
            for (String change : r.changes) {
                FormattedLine fl = new FormattedLine(change);
                int indent = fl.isSubBullet ? 18 : 6;
                int textW = contentW - 24 - (fl.isSubBullet ? 12 : 0);
                var lines = font.getSplitter().splitLines(Component.literal(fl.bullet + fl.text), Math.max(80, textW), net.minecraft.network.chat.Style.EMPTY);
                for (var l : lines) {
                    if (itemY + 10 >= contentY && itemY <= contentY + contentH) {
                        String lineStr = l.getString();
                        g.text(font, lineStr, contentX + indent, itemY, 0xFFE2E8F0, false);
                    }
                    itemY += 12;
                }
            }

            curY += cardH + 10; // Extra gap between release cards
        }

        // Scrollbar if needed
        if (maxScroll > 0) {
            int sbW = 4;
            int sbX = winX + winW - sbW - 6;
            int sbH = contentH;
            int thumbH = Math.max(20, (int) ((float) contentH / totalHeight * contentH));
            int thumbY = contentY + (int) ((this.scrollAmount / maxScroll) * (contentH - thumbH));

            g.fill(sbX, contentY, sbX + sbW, contentY + sbH, 0x33000000);
            g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, accent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0) {
            this.scrollAmount -= verticalAmount * 24.0;
            if (this.scrollAmount < 0) this.scrollAmount = 0;
            if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        int winW = Math.min(this.width - 40, 680);
        int winH = Math.min(this.height - 40, 480);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int closeW = 24;
        int closeH = 20;
        int closeX = winX + winW - closeW - 12;
        int closeY = winY + 12;
        if (event.x() >= closeX && event.x() <= closeX + closeW && event.y() >= closeY && event.y() <= closeY + closeH) {
            this.onClose();
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
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }
}
