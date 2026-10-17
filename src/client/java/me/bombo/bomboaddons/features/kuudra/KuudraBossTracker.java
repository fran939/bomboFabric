package me.bombo.bomboaddons.features.kuudra;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Kuudra Boss Tracker:
 * - Highlights Kuudra boss bounding box and glow.
 * - Displays Kuudra's current health and percentage on HUD.
 * - Displays a 2D compass direction arrow pointing towards Kuudra.
 */
public class KuudraBossTracker {

    private static double lastKuudraHealthPercent = 100.0;
    private static String lastKuudraHealthText = "100%";
    private static Vec3 kuudraPos = new Vec3(-100.0, 10.0, -100.0);

    public static void onClientTick() {
        if (!SkyblockUtils.isInKuudra()) return;

        MagmaCube kuudra = KuudraUtils.getKuudra();
        if (kuudra != null && kuudra.isAlive()) {
            kuudraPos = kuudra.position();
            float hp = kuudra.getHealth();
            float maxHp = kuudra.getMaxHealth();
            if (maxHp > 0) {
                lastKuudraHealthPercent = (hp / maxHp) * 100.0;
                lastKuudraHealthText = String.format(Locale.ROOT, "%.1f%% §7(§c%.0f§7/§c%.0f§7)", lastKuudraHealthPercent, hp, maxHp);
            }
        }
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraHealthHud) return;
        if (!SkyblockUtils.isInKuudra()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. Health Bar HUD
        int hudW = 160;
        int hudH = 28;
        int hudX = (screenW - hudW) / 2;
        int hudY = 16;

        boolean noBg = s.hudNoBackground;
        if (!noBg) {
            g.fill(hudX, hudY, hudX + hudW, hudY + hudH, 0xD0090D16);
            g.outline(hudX, hudY, hudW, hudH, 0x6038BDF8);
        }

        g.centeredText(font, "§c§lKUUDRA §8| " + lastKuudraHealthText, hudX + hudW / 2, hudY + 4, 0xFFFFFFFF);

        // Health meter bar
        int barX = hudX + 10;
        int barY = hudY + 16;
        int barW = hudW - 20;
        int barH = 6;
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF1E293B);
        int fillW = (int) Math.max(0, Math.min(barW, (lastKuudraHealthPercent / 100.0) * barW));
        int barColor = lastKuudraHealthPercent > 50 ? 0xFF22C55E : (lastKuudraHealthPercent > 20 ? 0xFFF59E0B : 0xFFEF4444);
        g.fill(barX, barY, barX + fillW, barY + barH, barColor);

        // 2. Kuudra Direction Indicator
        if (s.kuudraDirection && kuudraPos != null) {
            renderDirectionArrow(g, font, mc, screenW, screenH);
        }
    }

    private static void renderDirectionArrow(GuiGraphicsExtractor g, Font font, Minecraft mc, int screenW, int screenH) {
        Vec3 playerPos = mc.player.position();
        double dx = kuudraPos.x - playerPos.x;
        double dz = kuudraPos.z - playerPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 8.0) return; // Too close

        double targetAngle = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double playerYaw = mc.player.getYRot();
        double relativeAngle = Mth.wrapDegrees(targetAngle - playerYaw);

        // Draw indicator near bottom center of crosshair
        int centerX = screenW / 2;
        int centerY = screenH / 2 + 50;

        String arrow;
        if (relativeAngle > -22.5 && relativeAngle <= 22.5) {
            arrow = "▲";
        } else if (relativeAngle > 22.5 && relativeAngle <= 67.5) {
            arrow = "▲ ▶";
        } else if (relativeAngle > 67.5 && relativeAngle <= 112.5) {
            arrow = "▶";
        } else if (relativeAngle > 112.5 && relativeAngle <= 157.5) {
            arrow = "▼ ▶";
        } else if (relativeAngle > -67.5 && relativeAngle <= -22.5) {
            arrow = "◀ ▲";
        } else if (relativeAngle > -112.5 && relativeAngle <= -67.5) {
            arrow = "◀";
        } else if (relativeAngle > -157.5 && relativeAngle <= -112.5) {
            arrow = "◀ ▼";
        } else {
            arrow = "▼";
        }

        String label = "§c" + arrow + " §f" + (int) dist + "m";
        int w = font.width(label) + 12;
        g.fill(centerX - w / 2, centerY - 2, centerX + w / 2, centerY + 12, 0x88000000);
        g.centeredText(font, label, centerX, centerY + 1, 0xFFFFFFFF);
    }

    public static Vec3 getKuudraPos() {
        return kuudraPos;
    }
}
