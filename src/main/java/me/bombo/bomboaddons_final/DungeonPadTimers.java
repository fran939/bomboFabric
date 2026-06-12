package me.bombo.bomboaddons_final;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class DungeonPadTimers {
    private static boolean active = false;
    private static int serverTicks = 0;

    public static void init() {
        HudRenderCallback.EVENT.register(DungeonPadTimers::render);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.padTimersPurple && !s.padTimersGreen) return;

        Minecraft client = Minecraft.getInstance();
        if (client.options.hideGui) return;
        
        // Hide during open screens unless it is the HUD movement screen
        if (client.screen != null && !(client.screen instanceof HudMoveScreen)) return;

        drawTimerInfo(g, s.padTimersX, s.padTimersY, false);
    }

    public static void onBossMessage() {
        active = true;
        serverTicks = 196;
        if (BomboConfig.get().debugCommands || BomboConfig.get().debugMaster) {
            Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Dungeon Pad Timers started!");
        }
    }

    public static void onPingPacket(int packetId) {
        if (!active) return;
        if (packetId <= 0) {
            serverTicks--;
            if (serverTicks < 15) { // Deactivate after green NOW! is shown for 10 ticks (0.5s)
                active = false;
            }
        }
    }

    public static void drawTimerInfo(GuiGraphics g, int x, int y, boolean forceShow) {
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        float scale = BomboConfig.get().padTimersScale;
        g.pose().scale(scale, scale);

        String text = "";
        BomboConfig.Settings config = BomboConfig.get();

        if (active) {
            if (serverTicks > 100) {
                if (config.padTimersPurple) {
                    double sec = (serverTicks - 100) / 20.0;
                    text = String.format("Pad §dpurple§r in §b%.1fs", sec);
                }
            } else if (serverTicks > 90) {
                if (config.padTimersPurple) {
                    text = "Pad §dpurple §eNOW!";
                }
            } else if (serverTicks > 25) {
                if (config.padTimersGreen && serverTicks <= 75) {
                    double sec = (serverTicks - 25) / 20.0;
                    text = String.format("Pad §agreen§r in §b%.1fs!", sec);
                }
            } else if (serverTicks >= 15) {
                if (config.padTimersGreen) {
                    text = "Pad §agreen §eNOW!";
                }
            }
        } else if (forceShow) {
            text = "Pad §dpurple§r in §b2.5s";
        }

        if (!text.isEmpty()) {
            g.drawString(Minecraft.getInstance().font, text, 0, 0, 0xFFFFFFFF, true);
        }

        g.pose().popMatrix();
    }
}
