package me.bombo.bomboaddons.features.critters;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CritterHud {

    public static class BiomeRowBounds {
        public final SafariBiome biome;
        public final int x, y, w, h;

        public BiomeRowBounds(SafariBiome biome, int x, int y, int w, int h) {
            this.biome = biome;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    public static class PlayerRowBounds {
        public final String playerName;
        public final int x, y, w, h;

        public PlayerRowBounds(String playerName, int x, int y, int w, int h) {
            this.playerName = playerName;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    public static final List<BiomeRowBounds> lastRenderedBiomeRows = new ArrayList<>();
    public static final List<PlayerRowBounds> lastRenderedPlayerRows = new ArrayList<>();

    public static void render(GuiGraphicsExtractor g, int x, int y, float scale, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;
        Font font = mc.font;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        boolean uniqueOnly = !"All".equalsIgnoreCase(s.critterTrackingMode);

        SafariSession session = CritterSessionManager.currentOrLast();
        boolean waiting = session == null;
        if (waiting) {
            session = new SafariSession(mc.getUser() != null ? mc.getUser().getName() : "You", System.currentTimeMillis());
        }

        boolean live = CritterSessionManager.current() != null;
        int total = uniqueOnly ? Critters.total() : 37;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        if (scale != 1.0f && scale > 0.0f) {
            g.pose().scale(scale, scale);
        }

        int pad = 5;
        int lineH = 10;
        int curY = pad;

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        // Title
        String title = waiting ? "§6§lCritter Safari §7(Ready)"
                : live ? "§6§lCritter Safari §e" + formatDuration(session.elapsedMillis(System.currentTimeMillis()))
                : "§6§lCritter Safari §7(Last Run)";
        lines.add(title);
        colors.add(0xFFFFAA00);

        // Mode info
        lines.add("§7Mode: §e" + (uniqueOnly ? "Uniques" : "All / Quota"));
        colors.add(0xFFAAAAAA);

        // Overall Party & Own
        int partyCaught = uniqueOnly ? session.partyUnique() : session.partyCompletedQuota(false);
        int ownCaught = uniqueOnly ? session.ownUnique() : session.ownTotal();
        lines.add("§fParty: §a" + partyCaught + "§7/§f" + total);
        colors.add(0xFFFFFFFF);
        lines.add("§fYou: §b" + ownCaught + "§7/§f" + total);
        colors.add(0xFF55FFFF);

        lines.add("");
        colors.add(0);

        int biomeStartIndex = lines.size();
        List<SafariBiome> biomesInOrder = List.of(SafariBiome.FOREST, SafariBiome.CAVERN, SafariBiome.ICY, SafariBiome.HAUNTED);

        for (SafariBiome b : biomesInOrder) {
            int max = Critters.totalIn(b);
            int caught = uniqueOnly ? session.partyUnique(b) : session.partyCompletedQuota(b, false);
            boolean complete = session.biomeComplete(b, uniqueOnly);
            String bText = "§f" + b.displayName() + " Biome: " + (complete ? "§a" : "§e") + caught + "§7/§f" + max + (complete ? " §a✔" : "");
            lines.add(bText);
            colors.add(0xFF000000 | b.colour());
        }

        int playerStartIndex = -1;
        List<String> playerListInOrder = new ArrayList<>();

        // Per-Player Breakdown
        if (s.critterShowPerPlayer) {
            Map<String, Map<SafariBiome, Integer>> uniquePerPlayer = session.uniquePerPlayer();
            Map<String, Integer> totalPerPlayer = session.totalPerPlayer();

            if (uniquePerPlayer.size() > 0) {
                lines.add("");
                colors.add(0);
                lines.add("§6§lParty Members:");
                colors.add(0xFFFFAA00);

                playerStartIndex = lines.size();
                for (String pName : uniquePerPlayer.keySet()) {
                    playerListInOrder.add(pName);
                    Map<SafariBiome, Integer> bMap = uniquePerPlayer.get(pName);
                    int pUniques = bMap != null ? bMap.values().stream().mapToInt(Integer::intValue).sum() : 0;
                    int pTotal = totalPerPlayer.getOrDefault(pName, pUniques);

                    String pSummary;
                    if (uniqueOnly) {
                        pSummary = "§b" + pName + "§7: §a" + pUniques + " §7uniques";
                    } else {
                        pSummary = "§b" + pName + "§7: §a" + pUniques + " §7u / §e" + pTotal + " §7tot";
                    }
                    lines.add(" " + pSummary);
                    colors.add(0xFFFFFFFF);
                }
            }
        }

        // Calculate dimensions
        int maxW = 0;
        for (String l : lines) {
            if (!l.isEmpty()) {
                int w = font.width(l);
                if (w > maxW) maxW = w;
            }
        }
        int boxW = maxW + pad * 2;
        int boxH = lines.size() * lineH + pad * 2;

        // Custom HUD Style
        BomboConfig.HudStyle style = s.getHudStyle("CRITTER_HUD");
        int bgColor = style.getBackgroundColorInt();
        int borderColor = style.getBorderColorInt();

        // Background
        g.fill(0, 0, boxW, boxH, bgColor);
        g.outline(0, 0, boxW, boxH, borderColor);

        // Update biome and player row bounding boxes for hover detection
        lastRenderedBiomeRows.clear();
        lastRenderedPlayerRows.clear();

        curY = pad;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (!l.isEmpty()) {
                int lineCol = colors.get(i);
                if (i == 0) {
                    lineCol = style.getTitleColorInt();
                } else if (lineCol == 0xFFFFFFFF) {
                    lineCol = style.getTextColorInt();
                }
                g.text(font, l, pad, curY, lineCol, true);
            }

            if (i >= biomeStartIndex && i < biomeStartIndex + biomesInOrder.size()) {
                SafariBiome b = biomesInOrder.get(i - biomeStartIndex);
                int rowScreenX = (int) (x + pad * scale);
                int rowScreenY = (int) (y + curY * scale);
                int rowScreenW = (int) ((boxW - pad * 2) * scale);
                int rowScreenH = (int) (lineH * scale);
                lastRenderedBiomeRows.add(new BiomeRowBounds(b, rowScreenX, rowScreenY, rowScreenW, rowScreenH));
            }

            if (playerStartIndex >= 0 && i >= playerStartIndex && i < playerStartIndex + playerListInOrder.size()) {
                String pName = playerListInOrder.get(i - playerStartIndex);
                int rowScreenX = (int) (x + pad * scale);
                int rowScreenY = (int) (y + curY * scale);
                int rowScreenW = (int) ((boxW - pad * 2) * scale);
                int rowScreenH = (int) (lineH * scale);
                lastRenderedPlayerRows.add(new PlayerRowBounds(pName, rowScreenX, rowScreenY, rowScreenW, rowScreenH));
            }

            curY += lineH;
        }

        g.pose().popMatrix();

        // Tooltips
        if (s.critterShowMissing) {
            for (BiomeRowBounds row : lastRenderedBiomeRows) {
                if (mouseX >= row.x && mouseX <= row.x + row.w && mouseY >= row.y && mouseY <= row.y + row.h) {
                    renderMissingTooltip(g, font, row.biome, session, uniqueOnly, mouseX, mouseY);
                    return;
                }
            }
            for (PlayerRowBounds pRow : lastRenderedPlayerRows) {
                if (mouseX >= pRow.x && mouseX <= pRow.x + pRow.w && mouseY >= pRow.y && mouseY <= pRow.y + pRow.h) {
                    renderPlayerTooltip(g, font, pRow.playerName, session, uniqueOnly, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private static void renderMissingTooltip(GuiGraphicsExtractor g, Font font, SafariBiome biome, SafariSession session, boolean uniqueOnly, int mouseX, int mouseY) {
        List<Critter> inBiome = Critters.inBiome(biome);
        List<Critter> missing = new ArrayList<>();
        List<Critter> collected = new ArrayList<>();

        for (Critter c : inBiome) {
            if (session.isComplete(c, uniqueOnly)) {
                collected.add(c);
            } else {
                missing.add(c);
            }
        }

        List<String> tooltip = new ArrayList<>();
        tooltip.add("§6§l" + biome.displayName() + " Biome (" + collected.size() + "/" + inBiome.size() + ")");

        // Red missing first
        if (!missing.isEmpty()) {
            int maxNonUnique = inBiome.stream().mapToInt(c -> c.hasQuota() ? c.spawnQuota() : 1).sum();
            tooltip.add("§cMissing (" + missing.size() + "/" + maxNonUnique + "):");
            for (Critter c : missing) {
                int caughtCount = session.partyCatches(c);
                int quota = c.hasQuota() ? c.spawnQuota() : 1;
                tooltip.add(" §c• §f" + c.name() + " §7(" + caughtCount + "/" + quota + ")");
            }
        }

        // Green collected at bottom
        if (!collected.isEmpty()) {
            tooltip.add("§aAlready Got (" + collected.size() + "):");
            for (Critter c : collected) {
                int caughtCount = session.partyCatches(c);
                int nearbyCount = CritterSafariEngine.nearbyFoundCounts.getOrDefault(c.name(), 0);
                int totalKnown = caughtCount + nearbyCount;
                tooltip.add(" §a✔ §f" + c.name() + " §7(" + caughtCount + "/" + totalKnown + ")");
            }
        }

        int tW = 0;
        for (String t : tooltip) {
            int w = font.width(t);
            if (w > tW) tW = w;
        }
        int tH = tooltip.size() * 10 + 6;
        int tx = mouseX + 10;
        int ty = mouseY + 10;

        g.pose().pushMatrix();
        g.fill(tx, ty, tx + tW + 8, ty + tH, 0xF0101015);
        g.outline(tx, ty, tW + 8, tH, 0xFFFFAA00);

        int lineY = ty + 4;
        for (String t : tooltip) {
            g.text(font, t, tx + 4, lineY, 0xFFFFFFFF, true);
            lineY += 10;
        }
        g.pose().popMatrix();
    }

    private static void renderPlayerTooltip(GuiGraphicsExtractor g, Font font, String playerName, SafariSession session, boolean uniqueOnly, int mouseX, int mouseY) {
        Map<String, Map<SafariBiome, Integer>> uniquePerPlayer = session.uniquePerPlayer();
        Map<String, Integer> totalPerPlayer = session.totalPerPlayer();

        Map<SafariBiome, Integer> bMap = uniquePerPlayer.getOrDefault(playerName, Map.of());
        int totalCaught = totalPerPlayer.getOrDefault(playerName, 0);

        // Detect player's primary biome based on catches
        SafariBiome primaryBiome = null;
        int maxBiomeCatches = -1;
        for (Map.Entry<SafariBiome, Integer> entry : bMap.entrySet()) {
            if (entry.getValue() > maxBiomeCatches) {
                maxBiomeCatches = entry.getValue();
                primaryBiome = entry.getKey();
            }
        }

        List<String> tooltip = new ArrayList<>();
        tooltip.add("§b§l" + playerName + " Info");
        tooltip.add("§7Assigned Biome: " + (primaryBiome != null ? "§e" + primaryBiome.displayName() : "§7Unknown"));
        tooltip.add("§7Total Critters Caught: §a" + totalCaught);
        tooltip.add("§7Biome Breakdown:");
        for (SafariBiome b : SafariBiome.values()) {
            int count = bMap.getOrDefault(b, 0);
            tooltip.add(" §7- " + b.displayName() + ": §a" + count + "§7/§f" + Critters.totalIn(b));
        }

        int tW = 0;
        for (String t : tooltip) {
            int w = font.width(t);
            if (w > tW) tW = w;
        }
        int tH = tooltip.size() * 10 + 6;
        int tx = mouseX + 10;
        int ty = mouseY + 10;

        g.pose().pushMatrix();
        g.fill(tx, ty, tx + tW + 8, ty + tH, 0xF0101015);
        g.outline(tx, ty, tW + 8, tH, 0xFF55FFFF);

        int lineY = ty + 4;
        for (String t : tooltip) {
            g.text(font, t, tx + 4, lineY, 0xFFFFFFFF, true);
            lineY += 10;
        }
        g.pose().popMatrix();
    }

    public static int getWidth() {
        return 140;
    }

    public static int getHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        int base = 75;
        if (s != null && s.critterShowPerPlayer) {
            base += 40;
        }
        return base;
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
