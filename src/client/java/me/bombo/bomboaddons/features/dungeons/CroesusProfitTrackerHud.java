package me.bombo.bomboaddons.features.dungeons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.bombo.bomboaddons.AutoCroesus;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.LowestBinManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Cumulative Croesus profit tracker HUD (like /gp): total profit at the top, then a per-floor
 * breakdown (F1..F7, M1..M7, T1..T5). Data lives in {@code bombo_croesus_profit.json} and is
 * cached here, so the file is not read on every frame.
 */
public class CroesusProfitTrackerHud {

    public static final int LINE_HEIGHT = 10;
    public static final int BASE_W = 150;

    private static AutoCroesus.ProfitRecord cached = null;
    private static long cachedAt = 0L;
    private static final long CACHE_MS = 2000L;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "croesus_profit_tracker"),
                CroesusProfitTrackerHud::render);
    }

    /** Called after a chest is recorded so the next frame shows the new numbers. */
    public static void markDirty() {
        cached = null;
        cachedAt = 0L;
    }

    private static AutoCroesus.ProfitRecord record() {
        long now = System.currentTimeMillis();
        if (cached == null || now - cachedAt > CACHE_MS) {
            cached = AutoCroesus.loadProfitRecord();
            cachedAt = now;
        }
        return cached;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() instanceof me.bombo.bomboaddons.HudMoveScreen) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.croesusProfitTracker) return;
        draw(g, s.croesusTrackerHudX, s.croesusTrackerHudY, s.croesusTrackerHudScale);
    }

    public static void renderInContainer(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.gui.screen() instanceof me.bombo.bomboaddons.HudMoveScreen) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.croesusProfitTracker) return;
        draw(g, s.croesusTrackerHudX, s.croesusTrackerHudY, s.croesusTrackerHudScale);
    }

    public static void draw(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float sc = scale > 0.0F ? scale : 1.0F;

        AutoCroesus.ProfitRecord rec = record();
        List<Map.Entry<String, AutoCroesus.FloorStat>> floors = new ArrayList<>(rec.floors.entrySet());
        floors.sort((a, b) -> Integer.compare(AutoCroesus.floorSortKey(a.getKey()), AutoCroesus.floorSortKey(b.getKey())));

        g.pose().pushMatrix();
        g.pose().translate((float) baseX, (float) baseY);
        g.pose().scale(sc, sc);

        int y = 0;
        g.text(font, "§d§lCroesus Profit Tracker", 0, y, -1, true);
        y += LINE_HEIGHT;

        long avg = rec.totalRuns > 0 ? rec.totalProfit / rec.totalRuns : 0L;
        g.text(font, "§7Total: " + (rec.totalProfit >= 0 ? "§a+" : "§c-")
                + LowestBinManager.formatPrice(Math.abs(rec.totalProfit)), 0, y, -1, true);
        y += LINE_HEIGHT;

        g.text(font, "§7Runs: §e" + rec.totalRuns + " §8| §7Avg: " + (avg >= 0 ? "§a+" : "§c-")
                + LowestBinManager.formatPrice(Math.abs(avg)), 0, y, -1, true);
        y += LINE_HEIGHT;

        if (rec.kismetsUsed > 0) {
            g.text(font, "§7Kismets used: §d" + rec.kismetsUsed, 0, y, -1, true);
            y += LINE_HEIGHT;
        }

        if (floors.isEmpty()) {
            g.text(font, "§8No runs recorded yet.", 0, y, -1, false);
        } else {
            for (Map.Entry<String, AutoCroesus.FloorStat> e : floors) {
                AutoCroesus.FloorStat fs = e.getValue();
                String profitStr = (fs.profit >= 0 ? "§a+" : "§c-") + LowestBinManager.formatPrice(Math.abs(fs.profit));
                g.text(font, "§6" + e.getKey() + " §8| " + profitStr + " §8(§7" + fs.runs + "§8)", 0, y, -1, false);
                y += LINE_HEIGHT;
            }
        }

        g.pose().popMatrix();
    }

    /** Sample rows shown inside /b gui while positioning the tracker. */
    public static void renderDummy(GuiGraphicsExtractor g, int baseX, int baseY, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float sc = scale > 0.0F ? scale : 1.0F;
        String[][] rows = {
                {"§d§lCroesus Profit Tracker", ""},
                {"§7Total: §a+412,800,000", ""},
                {"§7Runs: §e128 §8| §7Avg: §a+3,225,000", ""},
                {"§6F7 §8| §a+310,000,000 §8(§790§8)", ""},
                {"§6M7 §8| §a+98,000,000 §8(§724§8)", ""},
                {"§6M4 §8| §c-1,200,000 §8(§76§8)", ""},
        };
        g.pose().pushMatrix();
        g.pose().translate((float) baseX, (float) baseY);
        g.pose().scale(sc, sc);
        int y = 0;
        for (String[] row : rows) {
            g.text(font, row[0], 0, y, -1, true);
            y += LINE_HEIGHT;
        }
        g.pose().popMatrix();
    }
}
