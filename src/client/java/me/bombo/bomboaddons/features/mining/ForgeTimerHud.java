package me.bombo.bomboaddons.features.mining;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.HudMoveScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public class ForgeTimerHud {
    public static void init() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("bomboaddons", "forge_hud"), ForgeTimerHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker tickDelta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.forgeHud) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyToggleGui.isDown()) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof HudMoveScreen)) return;

        drawForgeInfo(g, s.forgeHudX, s.forgeHudY, mc.gui.screen() instanceof HudMoveScreen);
    }

    public static void drawForgeInfo(GuiGraphicsExtractor g, int x, int y, boolean isPreview) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        List<String> lines = new ArrayList<>();
        if (s.forgeHudClosestOnly) {
            ForgeTimerManager.ForgeSlot closest = ForgeTimerManager.getClosestSlot();
            if (closest != null) {
                String name = closest.itemName.isEmpty() ? ("Slot #" + closest.slotIndex) : closest.itemName;
                lines.add("§6🔨 Forge: §e" + name + " §7- " + closest.getFormattedRemaining());
            } else if (isPreview) {
                lines.add("§6🔨 Forge: §eRefined Diamond §7- §b2h 15m");
            } else {
                lines.add("§6🔨 Forge: §7No active timers");
            }
        } else {
            lines.add("§6§l🔨 The Forge");
            List<ForgeTimerManager.ForgeSlot> slots = ForgeTimerManager.getUnlockedSlots();
            if (slots.isEmpty() && isPreview) {
                lines.add("§7• §eSlot 1: §fRefined Diamond §7- §b2h 15m");
                lines.add("§7• §eSlot 2: §fMithril Drill SX-R326 §7- §aReady!");
                lines.add("§7• §eSlot 3: §8Empty");
            } else if (slots.isEmpty()) {
                lines.add("§7• §8No active slots recorded");
            } else {
                for (ForgeTimerManager.ForgeSlot slot : slots) {
                    String name = slot.itemName.isEmpty() ? "Empty Slot" : slot.itemName;
                    lines.add("§7• §eSlot " + slot.slotIndex + ": §f" + name + " §7- " + slot.getFormattedRemaining());
                }
            }
        }

        int maxW = 0;
        for (String line : lines) {
            int w = font.width(ChatFormatting.stripFormatting(line));
            if (w > maxW) maxW = w;
        }
        int totalW = maxW + 8;
        int totalH = lines.size() * 10 + 4;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        if (s.forgeHudScale != 1.0f) {
            g.pose().scale(s.forgeHudScale, s.forgeHudScale);
        }

        // Background box
        g.fill(0, 0, totalW, totalH, 0x88000000);

        int curY = 3;
        for (String line : lines) {
            g.text(font, line, 4, curY, 0xFFFFFFFF, true);
            curY += 10;
        }

        g.pose().popMatrix();
    }

    public static int getWidth() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.forgeHudClosestOnly) {
            return 160;
        }
        return 170;
    }

    public static int getHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.forgeHudClosestOnly) {
            return 16;
        }
        List<ForgeTimerManager.ForgeSlot> slots = ForgeTimerManager.getUnlockedSlots();
        int count = slots.isEmpty() ? 4 : (slots.size() + 1);
        return count * 10 + 6;
    }
}
