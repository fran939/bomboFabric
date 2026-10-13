package me.bombo.bomboaddons.features.shards;

import me.bombo.bomboaddons.features.shards.ShardsShoppingManager.FusionItem;
import me.bombo.bomboaddons.features.shards.ShardsShoppingManager.PlanData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class FusionHelper {

    public static boolean isFusionContainer(String title) {
        if (title == null) return false;
        String t = title.toLowerCase();
        return t.contains("fusion box") || t.contains("shard fusion") || t.contains("confirm fusion");
    }

    public static void onRenderSlot(GuiGraphicsExtractor guiGraphics, Slot slot, String title) {
        if (!isFusionContainer(title)) return;
        PlanData plan = ShardsShoppingManager.getActivePlan();
        if (plan == null || plan.fusions.isEmpty()) return;

        List<FusionItem> fusions = plan.fusions;
        String t = title.toLowerCase();

        // Pulsing alpha for vibrant feedback
        long time = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin((time % 2000L) / 2000.0 * Math.PI * 2);
        int alphaFill = 0x40 + (int)(0x30 * pulse);
        int fillGreen = (alphaFill << 24) | 0x00FF88;
        int borderGreen = 0xFF00FF88;

        int fillGold = (alphaFill << 24) | 0xFFAA00;
        int borderGold = 0xFFFFAA00;

        int fillCyan = (alphaFill << 24) | 0x00FFFF;
        int borderCyan = 0xFF00FFFF;

        int fillPurple = (alphaFill << 24) | 0xCC22FF;
        int borderPurple = 0xFFFF55FF;

        int fillLime = (alphaFill << 24) | 0x00FF00;
        int borderLime = 0xFF55FF55;

        if (t.contains("fusion box")) {
            // (x/x) Fusion Box main catalogue
            // Check slot 48: Repeat Previous Fusion
            if (slot.index == 48 && slot.hasItem()) {
                String hover = slot.getItem().getHoverName().getString();
                for (FusionItem f : fusions) {
                    if (hover.toLowerCase().contains(f.name.toLowerCase())) {
                        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fillCyan);
                        guiGraphics.outline(slot.x, slot.y, 16, 16, borderCyan);
                        return;
                    }
                }
            }

            // In catalogue slots (9 to 44):
            if (slot.index >= 9 && slot.index <= 44 && slot.hasItem()) {
                String hover = slot.getItem().getHoverName().getString().trim();
                for (FusionItem f : fusions) {
                    if (matchesShard(hover, f.input1Name) || matchesShard(hover, f.input2Name)) {
                        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fillGreen);
                        guiGraphics.outline(slot.x, slot.y, 16, 16, borderGreen);
                        return;
                    }
                }
            }

            // Next / Previous Page arrows:
            // Slot 53 is Next Page, Slot 45 is Previous Page
            if (slot.index == 53 || slot.index == 45) {
                if (slot.hasItem()) {
                    String hover = slot.getItem().getHoverName().getString().toLowerCase();
                    if (hover.contains("next") || hover.contains("previous")) {
                        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fillGold);
                        guiGraphics.outline(slot.x, slot.y, 16, 16, borderGold);
                    }
                }
            }

        } else if (t.contains("shard fusion")) {
            // Shard Fusion combining screen
            // Candidate selection slots or result slots:
            if (slot.hasItem()) {
                String hover = slot.getItem().getHoverName().getString().trim();

                // 1. Result slot check (slots 14, 15, 16):
                for (FusionItem f : fusions) {
                    if (matchesShard(hover, f.name)) {
                        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fillPurple);
                        guiGraphics.outline(slot.x, slot.y, 16, 16, borderPurple);
                        return;
                    }
                }

                // 2. Candidate partner shard check:
                for (FusionItem f : fusions) {
                    if (matchesShard(hover, f.input1Name) || matchesShard(hover, f.input2Name)) {
                        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fillGreen);
                        guiGraphics.outline(slot.x, slot.y, 16, 16, borderGreen);
                        return;
                    }
                }

                // 3. Confirm button check (terracotta or "Click to fuse"):
                String cleanLower = hover.toLowerCase();
                if (cleanLower.contains("fuse") || cleanLower.contains("confirm")) {
                    guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, fillLime);
                    guiGraphics.outline(slot.x, slot.y, 16, 16, borderLime);
                }
            }
        }
    }

    private static boolean matchesShard(String itemName, String targetShardName) {
        if (itemName == null || targetShardName == null) return false;
        String cleanItem = itemName.replaceAll("§[0-9a-fk-or]", "").trim().toLowerCase();
        String cleanTarget = targetShardName.replaceAll("§[0-9a-fk-or]", "").trim().toLowerCase();
        return cleanItem.equals(cleanTarget) || cleanItem.startsWith(cleanTarget) || cleanItem.contains(cleanTarget);
    }
}
