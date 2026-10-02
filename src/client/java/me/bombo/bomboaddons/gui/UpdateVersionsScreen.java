package me.bombo.bomboaddons.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.Constants;
import me.bombo.bomboaddons.ModUpdater;
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

public class UpdateVersionsScreen extends Screen {
    private final Screen parent;
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private boolean loading = true;
    private String errorMessage = null;
    private String installingVersion = null;

    // Filter modes: 0 = ALL, 1 = FULL ONLY, 2 = BETA ONLY
    private int filterMode = 0;

    private final List<VersionEntry> allVersions = new ArrayList<>();
    private final List<VersionEntry> filteredVersions = new ArrayList<>();

    public static class VersionEntry {
        public final String version;
        public final boolean isBeta;
        public final String filename;
        public final String downloadUrl;
        public final String releaseDate;

        public VersionEntry(String version, boolean isBeta, String filename, String downloadUrl, String releaseDate) {
            this.version = version;
            this.isBeta = isBeta;
            this.filename = filename;
            this.downloadUrl = downloadUrl;
            this.releaseDate = (releaseDate != null && !releaseDate.isEmpty()) ? releaseDate : "2026-10-01";
        }
    }

    public UpdateVersionsScreen(Screen parent) {
        super(Component.literal("BomboAddons Version Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if (loading && allVersions.isEmpty()) {
            fetchVersionsAsync();
        }
    }

    private void fetchVersionsAsync() {
        loading = true;
        errorMessage = null;
        new Thread(() -> {
            try {
                // First fetch changelog dates map
                java.util.Map<String, String> dateMap = new java.util.HashMap<>();
                try {
                    HttpURLConnection clConn = (HttpURLConnection) (new URL("https://api.bombo.dpdns.org/mod/changelog")).openConnection();
                    clConn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    clConn.setRequestProperty("Accept", "application/json");
                    clConn.setConnectTimeout(3000);
                    clConn.setReadTimeout(3000);
                    if (clConn.getResponseCode() == 200) {
                        BufferedReader clReader = new BufferedReader(new InputStreamReader(clConn.getInputStream(), StandardCharsets.UTF_8));
                        JsonArray clArr = JsonParser.parseReader(clReader).getAsJsonArray();
                        for (JsonElement cle : clArr) {
                            if (cle.isJsonObject()) {
                                JsonObject co = cle.getAsJsonObject();
                                if (co.has("version") && co.has("date")) {
                                    dateMap.put(co.get("version").getAsString().trim(), co.get("date").getAsString().trim());
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                String apiUrl = "https://api.bombo.dpdns.org/mod/version";
                HttpURLConnection conn = (HttpURLConnection) (new URL(apiUrl)).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) BomboAddons");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    List<VersionEntry> loaded = new ArrayList<>();

                    if (obj.has("versions") && obj.get("versions").isJsonArray()) {
                        JsonArray arr = obj.getAsJsonArray("versions");
                        String myVer = Constants.myVersion();
                        for (JsonElement el : arr) {
                            if (el.isJsonObject()) {
                                JsonObject vObj = el.getAsJsonObject();
                                String ver = vObj.has("version") ? vObj.get("version").getAsString() : "";
                                boolean isBeta = vObj.has("isBeta") && vObj.get("isBeta").getAsBoolean();
                                String fn = vObj.has("filename") ? vObj.get("filename").getAsString() : "";
                                String dl = vObj.has("downloadUrl") ? vObj.get("downloadUrl").getAsString() : "";
                                String date = vObj.has("date") ? vObj.get("date").getAsString()
                                        : (vObj.has("releasedAt") ? vObj.get("releasedAt").getAsString() : dateMap.getOrDefault(ver, "2026-10-01"));

                                // Strictly filter for current Minecraft line (e.g. 26.2.*)
                                if (sameMinecraftLine(myVer, ver)) {
                                    loaded.add(new VersionEntry(ver, isBeta, fn, dl, date));
                                }
                            }
                        }
                    }

                    Minecraft.getInstance().execute(() -> {
                        allVersions.clear();
                        allVersions.addAll(loaded);
                        applyFilter();
                        loading = false;
                    });
                    return;
                }

                Minecraft.getInstance().execute(() -> {
                    try {
                        errorMessage = "Server returned HTTP " + conn.getResponseCode();
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

    private boolean sameMinecraftLine(String a, String b) {
        if (a == null || b == null) return true;
        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
        if (pa.length < 2 || pb.length < 2) return true;
        return pa[0].equals(pb[0]) && pa[1].equals(pb[1]);
    }

    private void applyFilter() {
        filteredVersions.clear();
        for (VersionEntry v : allVersions) {
            if (filterMode == 1 && v.isBeta) continue; // FULL ONLY
            if (filterMode == 2 && !v.isBeta) continue; // BETA ONLY
            filteredVersions.add(v);
        }
        this.scrollAmount = 0;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Semi-transparent background
        g.fill(0, 0, this.width, this.height, 0xD00A0D14);

        int winW = Math.min(this.width - 40, 680);
        int winH = Math.min(this.height - 40, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int bg = ConfigUITheme.getMainWindowBg();
        int border = ConfigUITheme.getBorderColor();
        int accent = ConfigUITheme.getAccentColor();

        // Main window panel
        g.fill(winX, winY, winX + winW, winY + winH, bg);
        g.outline(winX, winY, winW, winH, border);

        Font font = Minecraft.getInstance().font;

        // Header Title
        g.text(font, "§6§lBomboAddons Version Catalog", winX + 16, winY + 14, 0xFFFFFFFF, true);
        String currentVer = Constants.myVersion();
        g.text(font, "§7Current: §b" + currentVer + " §8| §7Select a version to switch or downgrade", winX + 16, winY + 28, 0xFFA0AEC0, false);

        // Header Controls: Filter buttons & Refresh & Close
        int closeW = 22;
        int closeH = 18;
        int closeX = winX + winW - closeW - 12;
        int closeY = winY + 12;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        g.fill(closeX, closeY, closeX + closeW, closeY + closeH, closeHovered ? 0xFFEF4444 : 0xFF2D3748);
        g.outline(closeX, closeY, closeW, closeH, 0xFF4A5568);
        g.centeredText(font, "✕", closeX + closeW / 2, closeY + 5, 0xFFFFFFFF);

        // Refresh Button
        int refW = 40;
        int refH = 18;
        int refX = closeX - refW - 6;
        int refY = winY + 12;
        boolean refHovered = mouseX >= refX && mouseX <= refX + refW && mouseY >= refY && mouseY <= refY + refH;
        g.fill(refX, refY, refX + refW, refY + refH, refHovered ? 0xFF3B82F6 : 0xFF2D3748);
        g.outline(refX, refY, refW, refH, 0xFF4A5568);
        g.centeredText(font, "↺", refX + refW / 2, refY + 4, 0xFFFFFFFF);

        // Filter Pills: [All] [Full] [Beta]
        int filterY = winY + 44;
        drawFilterPill(g, font, winX + 16, filterY, 46, 16, "All", filterMode == 0, mouseX, mouseY);
        drawFilterPill(g, font, winX + 66, filterY, 56, 16, "Full Only", filterMode == 1, mouseX, mouseY);
        drawFilterPill(g, font, winX + 126, filterY, 56, 16, "Betas Only", filterMode == 2, mouseX, mouseY);

        // Divider
        g.fill(winX + 12, winY + 66, winX + winW - 12, winY + 67, border);

        // Content Area
        int contentX = winX + 16;
        int contentY = winY + 74;
        int contentW = winW - 32;
        int contentH = winH - 86;

        if (loading) {
            g.centeredText(font, "§7Loading versions from API...", winX + winW / 2, winY + winH / 2, 0xFFFFFFFF);
            return;
        }

        if (errorMessage != null) {
            g.centeredText(font, "§cFailed to load versions: §f" + errorMessage, winX + winW / 2, winY + winH / 2 - 10, 0xFFFFFFFF);
            g.centeredText(font, "§7Click ↺ to retry or check your internet connection.", winX + winW / 2, winY + winH / 2 + 6, 0xFFA0AEC0);
            return;
        }

        if (filteredVersions.isEmpty()) {
            g.centeredText(font, "§7No versions found for the selected filter.", winX + winW / 2, winY + winH / 2, 0xFFFFFFFF);
            return;
        }

        // Row height = 36px, gap = 6px
        int rowH = 34;
        int gap = 6;
        int totalHeight = filteredVersions.size() * (rowH + gap);

        this.maxScroll = Math.max(0, totalHeight - contentH);
        if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
        if (this.scrollAmount < 0) this.scrollAmount = 0;

        // Scissor clip content area correctly (minX, minY, maxX, maxY)
        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int curY = (int) (contentY - this.scrollAmount);

        for (VersionEntry v : filteredVersions) {
            if (curY + rowH >= contentY && curY <= contentY + contentH) {
                boolean isCurrent = v.version.equalsIgnoreCase(currentVer);
                boolean isHovered = mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= curY && mouseY <= curY + rowH;

                // Row background
                int rowBg = isCurrent ? 0x2210B981 : (isHovered ? 0x22FFFFFF : 0x15FFFFFF);
                int rowBorder = isCurrent ? 0xFF10B981 : (isHovered ? 0xFF4A5568 : 0xFF2D3748);
                g.fill(contentX, curY, contentX + contentW, curY + rowH, rowBg);
                g.outline(contentX, curY, contentW, rowH, rowBorder);

                // Badges: [FULL] or [BETA]
                int badgeX = contentX + 8;
                int badgeY = curY + (rowH - 16) / 2;
                if (v.isBeta) {
                    g.fill(badgeX, badgeY, badgeX + 44, badgeY + 16, 0x33F59E0B);
                    g.outline(badgeX, badgeY, 44, 16, 0xFFF59E0B);
                    g.centeredText(font, "§6§lBETA", badgeX + 22, badgeY + 4, 0xFFF59E0B);
                } else {
                    g.fill(badgeX, badgeY, badgeX + 44, badgeY + 16, 0x3310B981);
                    g.outline(badgeX, badgeY, 44, 16, 0xFF10B981);
                    g.centeredText(font, "§a§lFULL", badgeX + 22, badgeY + 4, 0xFF10B981);
                }

                // Version text
                int textX = badgeX + 52;
                g.text(font, (isCurrent ? "§a§l" : "§f§l") + "v" + v.version, textX, curY + 8, isCurrent ? 0xFF10B981 : 0xFFFFFFFF, true);
                if (isCurrent) {
                    g.text(font, "§2[Currently Active]", textX + font.width("v" + v.version) + 10, curY + 8, 0xFF10B981, false);
                }
                g.text(font, "§e📅 " + v.releaseDate + " §8| " + (v.filename.isEmpty() ? "bomboaddons-" + v.version + ".jar" : v.filename), textX, curY + 20, 0xFF94A3B8, false);

                // Action Button on Right
                int btnW = 86;
                int btnH = 20;
                int btnX = contentX + contentW - btnW - 8;
                int btnY = curY + (rowH - btnH) / 2;
                boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;

                if (isCurrent) {
                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0x3310B981);
                    g.outline(btnX, btnY, btnW, btnH, 0xFF10B981);
                    g.centeredText(font, "§aCurrent", btnX + btnW / 2, btnY + 6, 0xFF10B981);
                } else if (v.version.equals(installingVersion)) {
                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH, 0xFFF59E0B);
                    g.centeredText(font, "§0Installing...", btnX + btnW / 2, btnY + 6, 0xFF000000);
                } else {
                    int btnBg = btnHovered ? 0xFF00A4DC : 0xFF2D3748;
                    int btnBorder = btnHovered ? 0xFF00E5FF : 0xFF4A5568;
                    g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnBg);
                    g.outline(btnX, btnY, btnW, btnH, btnBorder);
                    g.centeredText(font, btnHovered ? "§f§lSwitch" : "§fSwitch", btnX + btnW / 2, btnY + 6, 0xFFFFFFFF);
                }
            }
            curY += rowH + gap;
        }

        g.disableScissor();

        // Scrollbar
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

    private void drawFilterPill(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, boolean active, int mx, int my) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int bg = active ? 0xFF00A4DC : (hovered ? 0xFF2D3748 : 0x221E293B);
        int border = active ? 0xFF00E5FF : (hovered ? 0xFF4A5568 : 0xFF334155);
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);
        g.centeredText(font, (active ? "§f§l" : "§7") + label, x + w / 2, y + 4, 0xFFFFFFFF);
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
        int winH = Math.min(this.height - 40, 500);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        double mx = event.x();
        double my = event.y();

        // Close button
        int closeW = 22;
        int closeH = 18;
        int closeX = winX + winW - closeW - 12;
        int closeY = winY + 12;
        if (mx >= closeX && mx <= closeX + closeW && my >= closeY && my <= closeY + closeH) {
            this.onClose();
            return true;
        }

        // Refresh button
        int refW = 40;
        int refH = 18;
        int refX = closeX - refW - 6;
        int refY = winY + 12;
        if (mx >= refX && mx <= refX + refW && my >= refY && my <= refY + refH) {
            fetchVersionsAsync();
            return true;
        }

        // Filter pills
        int filterY = winY + 44;
        if (mx >= winX + 16 && mx <= winX + 16 + 46 && my >= filterY && my <= filterY + 16) {
            filterMode = 0;
            applyFilter();
            return true;
        }
        if (mx >= winX + 66 && mx <= winX + 66 + 56 && my >= filterY && my <= filterY + 16) {
            filterMode = 1;
            applyFilter();
            return true;
        }
        if (mx >= winX + 126 && mx <= winX + 126 + 56 && my >= filterY && my <= filterY + 16) {
            filterMode = 2;
            applyFilter();
            return true;
        }

        // Content Area clicks
        int contentX = winX + 16;
        int contentY = winY + 74;
        int contentW = winW - 32;
        int contentH = winH - 86;

        if (mx >= contentX && mx <= contentX + contentW && my >= contentY && my <= contentY + contentH) {
            int rowH = 34;
            int gap = 6;
            int curY = (int) (contentY - this.scrollAmount);

            for (VersionEntry v : filteredVersions) {
                if (curY + rowH >= contentY && curY <= contentY + contentH) {
                    int btnW = 86;
                    int btnH = 20;
                    int btnX = contentX + contentW - btnW - 8;
                    int btnY = curY + (rowH - btnH) / 2;

                    if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                        if (!v.version.equalsIgnoreCase(Constants.myVersion()) && installingVersion == null) {
                            installingVersion = v.version;
                            ModUpdater.installSpecificVersion(v.version);
                            return true;
                        }
                    }
                }
                curY += rowH + gap;
            }
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
