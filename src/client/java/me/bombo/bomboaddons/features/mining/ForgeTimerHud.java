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
                String timePart = closest.finished ? "§aReady!" : ("§b" + closest.getFormattedRemaining());
                lines.add("§6The Forge: §e" + name + " §7in " + timePart);
            } else if (isPreview) {
                lines.add("§6The Forge: §eRefined Diamond §7in §b2h 15m");
            } else {
                lines.add("§6The Forge: §7No active timers");
            }
        } else {
            List<ForgeTimerManager.ForgeSlot> slots = ForgeTimerManager.getUnlockedSlots();
            int totalUnlocked = slots.size();
            int activeCount = 0;
            for (ForgeTimerManager.ForgeSlot slot : slots) {
                if (!slot.empty) activeCount++;
            }

            if (isPreview && slots.isEmpty()) {
                totalUnlocked = 5;
                activeCount = 3;
            }

            // Header matching user's reference: "Total pests: 4/8" -> "Total forge: 3/5"
            lines.add("§6Total forge: §e" + activeCount + "/" + (totalUnlocked > 0 ? totalUnlocked : 5));

            if (slots.isEmpty() && isPreview) {
                lines.add("§eRefined Diamond §7in §b2h 15m");
                lines.add("§eMithril Drill §7in §aReady!");
                lines.add("§eRefined Titanium §7in §b45m");
            } else if (slots.isEmpty()) {
                lines.add("§7No slots recorded");
            } else {
                int shown = 0;
                for (ForgeTimerManager.ForgeSlot slot : slots) {
                    if (slot.empty) continue;
                    String name = slot.itemName.isEmpty() ? ("Slot #" + slot.slotIndex) : slot.itemName;
                    String timePart = slot.finished ? "§aReady!" : ("§b" + slot.getFormattedRemaining());
                    lines.add("§e" + name + " §7in " + timePart);
                    shown++;
                }
                if (shown == 0) {
                    lines.add("§7All slots empty");
                }
            }
        }

        int maxW = 0;
        for (String line : lines) {
            int w = font.width(ChatFormatting.stripFormatting(line));
            if (w > maxW) maxW = w;
        }
        int totalW = maxW + 4;
        int totalH = lines.size() * 10;

        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        if (s.forgeHudScale != 1.0f) {
            g.pose().scale(s.forgeHudScale, s.forgeHudScale);
        }

        // Draw preview outline only in HUD edit screen
        if (isPreview) {
            g.fill(-2, -2, totalW + 2, totalH + 2, 0x44000000);
        }

        int curY = 0;
        for (String line : lines) {
            g.text(font, line, 0, curY, 0xFFFFFFFF, true);
            curY += 10;
        }

        g.pose().popMatrix();
    }

    public static int getWidth() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.forgeHudClosestOnly) {
            return 160;
        }
        return 150;
    }

    public static int getHeight() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.forgeHudClosestOnly) {
            return 12;
        }
        List<ForgeTimerManager.ForgeSlot> slots = ForgeTimerManager.getUnlockedSlots();
        int count = slots.isEmpty() ? 4 : (slots.size() + 1);
        return count * 10 + 2;
    }
}
