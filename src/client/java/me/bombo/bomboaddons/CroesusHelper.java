package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import java.util.List;

public class CroesusHelper {

    public static void onContainerTick(AbstractContainerScreen<?> screen) {
        if (screen == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.croesusHelper) return;

        String title = screen.getTitle().getString();

        if (title.contains("Croesus")) {
            for (Slot slot : screen.getMenu().slots) {
                if (!slot.hasItem()) continue;
                ItemStack stack = slot.getItem();
                String name = stack.getHoverName().getString();

                if (name.contains("Chest") || name.contains("Reward") || name.contains("Claim") || name.contains("Dungeon") || name.contains("Floor") || name.contains("Master")) {
                    Minecraft mc = Minecraft.getInstance();
                    List<Component> tooltip = stack.getTooltipLines(
                        net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                        mc.player,
                        net.minecraft.world.item.TooltipFlag.Default.NORMAL
                    );
                    boolean canClaim = false;
                    for (Component line : tooltip) {
                        String clean = line.getString();
                        if (clean.contains("Click to claim") || clean.contains("Unclaimed") || clean.contains("Available") || clean.contains("Open Chest")) {
                            canClaim = true;
                            break;
                        }
                    }
                    if (canClaim) {
                        SlotHighlight.addTargetSlot(slot.index, 0x8000FF00); // Highlight green for debugging
                        if (s.croesusDebug && mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bCroesus Debug§8] §aClaimable slot detected: #" + slot.index + " (" + name + ")"));
                        }
                    }
                }
            }
        }
    }
}
