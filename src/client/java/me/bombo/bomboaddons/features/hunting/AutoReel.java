package me.bombo.bomboaddons.features.hunting;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

public class AutoReel {
    private static long lastReelTime = 0L;
    private static int lastReeledEntityId = -1;
    private static long scheduledReelTime = 0L;
    private static int pendingEntityId = -1;
    private static final Random random = new Random();

    public static boolean isLasso(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String itemId = SkyblockUtils.getInternalId(stack);
        if (itemId != null && (itemId.contains("LASSO") || itemId.endsWith("_LASSO"))) {
            return true;
        }
        String name = ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase();
        return name.contains("lasso");
    }

    public static void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null) return; // Do not auto-reel while any GUI is open

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.autoReel) return;

        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        InteractionHand hand = null;
        if (isLasso(main)) {
            hand = InteractionHand.MAIN_HAND;
        } else if (isLasso(off)) {
            hand = InteractionHand.OFF_HAND;
        }
        if (hand == null) return;

        long now = System.currentTimeMillis();

        // Search rendering entities within 7 blocks for REEL text
        Entity targetEntity = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == null || entity == mc.player) continue;
            double distSq = entity.distanceToSqr(mc.player);
            if (distSq > 49.0) continue; // Max 7m radius

            String customName = entity.getCustomName() != null ? entity.getCustomName().getString() : null;
            String displayName = entity.getDisplayName() != null ? entity.getDisplayName().getString() : null;
            String name = entity.getName() != null ? entity.getName().getString() : null;

            if (checkReelText(customName) || checkReelText(displayName) || checkReelText(name)) {
                targetEntity = entity;
                break;
            }
        }

        if (targetEntity == null) {
            // REEL text is gone: reset pending state so next prompt immediately schedules a fresh interval
            scheduledReelTime = 0L;
            pendingEntityId = -1;
            return;
        }

        // Tier cooldown to prevent double clicking the same REEL prompt
        if (targetEntity.getId() == lastReeledEntityId && (now - lastReelTime < 800L)) {
            return;
        }

        // If not scheduled yet for this target, schedule with randomized delay
        if (pendingEntityId != targetEntity.getId() || scheduledReelTime == 0L) {
            pendingEntityId = targetEntity.getId();
            int min = Math.max(10, s.autoReelMinDelayMs);
            int max = Math.max(min, s.autoReelMaxDelayMs);
            int delay = min + (max > min ? random.nextInt(max - min + 1) : 0);
            scheduledReelTime = now + delay;
        }

        // Trigger reel once scheduled delay is reached
        if (now >= scheduledReelTime) {
            lastReelTime = now;
            lastReeledEntityId = targetEntity.getId();
            scheduledReelTime = 0L;
            pendingEntityId = -1;
            mc.gameMode.useItem(mc.player, hand);
            mc.player.swing(hand);
        }
    }

    private static boolean checkReelText(String text) {
        if (text == null) return false;
        String clean = ChatFormatting.stripFormatting(text).trim().toUpperCase();
        return clean.equals("REEL") || clean.contains("REEL");
    }
}
