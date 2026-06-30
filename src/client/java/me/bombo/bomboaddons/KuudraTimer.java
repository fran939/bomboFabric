package me.bombo.bomboaddons;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;

public class KuudraTimer {
    private static int lastDurationTicks = 0;
    private static long blindEndTime = 0;
    private static long nowEndTime = 0;

    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "kuudra_timer"), KuudraTimer::render);
    }

    private static void render(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.kuudraBlindnessTimer) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;
        
        // Hide during open screens unless it is the HUD movement screen
        if (client.screen != null && !(client.screen instanceof HudMoveScreen)) return;

        // Only track and show if in Kuudra area OR in singleplayer (for testing)
        boolean isKuudraOrLocal = "Kuudra".equalsIgnoreCase(BomboaddonsClient.currentArea) || "Kuudra's Hollow".equalsIgnoreCase(BomboaddonsClient.currentArea) || client.isLocalServer();
        if (!isKuudraOrLocal) {
            lastDurationTicks = 0;
            return;
        }

        int durationTicks = 0;
        if (client.player != null) {
            MobEffectInstance inst = client.player.getEffect(MobEffects.BLINDNESS);
            if (inst != null) {
                durationTicks = inst.getDuration();
            }
        }

        long now = System.currentTimeMillis();

        if (durationTicks > lastDurationTicks) {
            blindEndTime = now + 500;
            nowEndTime = blindEndTime + 500; // NOW lasts for 500ms after the timer ends
        }
        lastDurationTicks = durationTicks;

        long remaining = blindEndTime - now;
        long remainingNow = nowEndTime - now;

        if (remaining > 0) {
            drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, false, remaining, false);
        } else if (remainingNow > 0) {
            drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, false, 0, true);
        }
    }

    public static void drawTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean forceShow) {
        drawTimerInfo(g, x, y, forceShow, 500, false);
    }

    public static void drawTimerInfo(GuiGraphicsExtractor g, int x, int y, boolean forceShow, long remaining, boolean showNow) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = BomboConfig.get().kuudraBlindnessTimerScale;
        g.pose().scale(scale, scale);

        String timeStr;
        if (showNow) {
            timeStr = "§aNOW";
        } else {
            String colorCode;
            if (remaining >= 400) {
                colorCode = "§a"; // Green
            } else if (remaining >= 200) {
                colorCode = "§6"; // Gold
            } else {
                colorCode = "§c"; // Red
            }
            timeStr = colorCode + remaining + "ms";
        }

        String text = "§9Eaten In §f✈ " + timeStr;
        g.text(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFFFF, true);

        g.pose().popMatrix();
    }
}
