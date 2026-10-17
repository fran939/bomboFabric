package me.bombo.bomboaddons.features.kuudra;

import com.mojang.blaze3d.vertex.PoseStack;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboRenderUtils;
import me.bombo.bomboaddons.OrderedSubmitNodeCollector;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Kuudra Boss Tracker (authentic KIC parity):
 * - Health values format:
 *   Normal: color + comma(hp) + "§7/§a100,000§c❤"
 *   T5 Final (hp <= 25,000): shorthand(hp * 9600) + "§7/§a240M§c❤"
 * - Direction: Only alerts on T5 retreat (hp <= 25,000 and hp >= 24,900)
 *   with directional banner (RIGHT, LEFT, FRONT, BACK).
 * - World Highlight: 3D bounding box around Kuudra with configurable color.
 * - World Health: 3D text floating above Kuudra.
 */
public class KuudraBossTracker {

    private static double lastKuudraHealthPercent = 100.0;
    private static String lastKuudraHealthText = "§a100,000§7/§a100,000§c❤";
    private static Vec3 kuudraPos = new Vec3(-100.0, 10.0, -100.0);

    private static int dirCooldown = 0;
    private static Dir lastShownDir = Dir.NONE;

    public enum Dir {
        RIGHT("§c§lRIGHT →", 0xFFFF3333),
        LEFT("§a§l← LEFT", 0xFF22C55E),
        FRONT("§2§lFRONT ↑", 0xFF15803D),
        BACK("§4§lBACK ↓", 0xFFDC2626),
        NONE("", 0xFFFFFFFF);

        public final String display;
        public final int color;
        Dir(String display, int color) {
            this.display = display;
            this.color = color;
        }
    }

    public static void onClientTick() {
        if (!SkyblockUtils.isInKuudra()) {
            lastShownDir = Dir.NONE;
            dirCooldown = 0;
            return;
        }

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        MagmaCube kuudra = KuudraUtils.getKuudra();
        if (kuudra != null && kuudra.isAlive()) {
            kuudraPos = kuudra.position();
            float hp = kuudra.getHealth();
            int tier = KuudraUtils.getKuudraTier();
            boolean t5Final = tier == 5 && hp <= 25000.0F;
            if (!t5Final && hp > 100000.0F) {
                hp = 100000.0F;
            }
            int maxHp = t5Final ? 25000 : 100000;
            String color = hpColor(hp);

            if (t5Final) {
                lastKuudraHealthText = color + parseToShorthandNumber(hp * 9600.0) + "§7/§a240M§c❤";
            } else {
                int cur = Math.max(0, Math.round(hp));
                lastKuudraHealthText = color + fastComma(cur) + "§7/§a100,000§c❤";
            }

            int pct = maxHp > 0 ? Math.min(100, Math.max(0, Math.round(hp / (float) maxHp * 100.0F))) : 0;
            lastKuudraHealthPercent = pct;

            // KIC Direction Alert: fires ONLY during T5 retreat when Kuudra edges
            if (s.kuudraDirection && tier == 5 && hp <= 25000.0F && hp >= 24900.0F) {
                if (dirCooldown > 0) dirCooldown--;
                Dir dir = determineDir(kuudra.position());
                if (dir != Dir.NONE && (dir != lastShownDir || dirCooldown == 0)) {
                    lastShownDir = dir;
                    dirCooldown = 15;
                    KuudraNotifications.post(dir.display, "§7Kuudra retreat position", dir.color, 3000L, true);
                }
            }
        }
    }

    private static Dir determineDir(Vec3 pos) {
        if (pos.x < -128.0) {
            return Dir.RIGHT;
        } else if (pos.x > -72.0) {
            return Dir.LEFT;
        } else if (pos.z > -84.0) {
            return Dir.FRONT;
        } else if (pos.z < -132.0) {
            return Dir.BACK;
        }
        return Dir.NONE;
    }

    private static String hpColor(float hp) {
        if (hp > 99000.0F) return "§a";
        if (hp > 75000.0F) return "§2";
        if (hp > 50000.0F) return "§e";
        if (hp > 25000.0F) return "§6";
        return hp > 10000.0F ? "§c" : "§4";
    }

    private static String fastComma(int value) {
        if (value < 0) value = 0;
        if (value < 1000) return Integer.toString(value);
        char[] buf = new char[13];
        int idx = buf.length;
        for (int group = 0; value > 0; group++) {
            if (group == 3) {
                idx--;
                buf[idx] = ',';
                group = 0;
            }
            int digit = value % 10;
            idx--;
            buf[idx] = (char) ('0' + digit);
            value /= 10;
        }
        return new String(buf, idx, buf.length - idx);
    }

    private static String parseToShorthandNumber(double val) {
        if (val >= 1_000_000_000.0) {
            return String.format(Locale.ROOT, "%.2fB", val / 1_000_000_000.0);
        } else if (val >= 1_000_000.0) {
            return String.format(Locale.ROOT, "%.2fM", val / 1_000_000.0);
        } else if (val >= 1_000.0) {
            return String.format(Locale.ROOT, "%.1fK", val / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.0f", val);
    }

    public static void renderHUD(GuiGraphicsExtractor g, Font font, int screenW, int screenH) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.kuudraHealthHud) return;
        if (!SkyblockUtils.isInKuudra()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Clean text HUD matching KIC's KuudraHP overlay
        int textW = font.width(lastKuudraHealthText);
        int hudX = (screenW - textW) / 2;
        int hudY = 16;

        if (!s.hudNoBackground) {
            int pad = 4;
            g.fill(hudX - pad, hudY - 2, hudX + textW + pad, hudY + 11, 0x90000000);
        }

        g.text(font, lastKuudraHealthText, hudX, hudY, 0xFFFFFFFF, true);
    }

    public static void renderWorld(LevelRenderContext context) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;
        if (!s.kuudraHighlight && !s.kuudraHealth) return;
        if (!SkyblockUtils.isInKuudra()) return;

        MagmaCube kuudra = KuudraUtils.getKuudra();
        if (kuudra == null || !kuudra.isAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 camPos = mc.gameRenderer.mainCamera().position();
        PoseStack poseStack = context.poseStack();
        OrderedSubmitNodeCollector collector = new OrderedSubmitNodeCollector(context.submitNodeCollector());

        if (s.kuudraHighlight) {
            AABB bb = kuudra.getBoundingBox();
            AABB relBox = new AABB(bb.minX - camPos.x, bb.minY - camPos.y, bb.minZ - camPos.z,
                    bb.maxX - camPos.x, bb.maxY - camPos.y, bb.maxZ - camPos.z);

            int hexColor = 0xFFFF0018;
            try {
                if (s.kuudraHighlightColor != null && !s.kuudraHighlightColor.isEmpty()) {
                    hexColor = (int) Long.parseLong(s.kuudraHighlightColor.replace("#", ""), 16) | 0xFF000000;
                }
            } catch (Exception ignored) {}

            float r = ((hexColor >> 16) & 0xFF) / 255.0F;
            float g = ((hexColor >> 8) & 0xFF) / 255.0F;
            float b = (hexColor & 0xFF) / 255.0F;

            collector.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(),
                    (pose, vertexConsumer) -> BomboRenderUtils.drawBox(pose.pose(), vertexConsumer, relBox, r, g, b, 1.0F, 2.5F));
        }
    }

    public static Vec3 getKuudraPos() {
        return kuudraPos;
    }

    public static String getLastKuudraHealthText() {
        return lastKuudraHealthText;
    }
}
