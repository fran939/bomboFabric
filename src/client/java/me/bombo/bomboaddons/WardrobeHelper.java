package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import java.util.List;

public class WardrobeHelper {
    private static int pendingArmorNumber = -1;

    public static void equip(int armorNumber) {
        if (armorNumber < 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        pendingArmorNumber = armorNumber;

        int page = (armorNumber - 1) / 9 + 1;
        mc.player.connection.sendCommand("wd " + page);
    }

    public static void onGuiOpen(AbstractContainerScreen<?> screen) {
        if (pendingArmorNumber == -1) return;
        String title = screen.getTitle().getString().toLowerCase();
        if (title.contains("wardrobe") || title.contains("armor sets") || title.contains("equipment sets") || title.contains("loadouts")) {
            Minecraft mc = Minecraft.getInstance();
            int armorNumber = pendingArmorNumber;
            pendingArmorNumber = -1; // Reset immediately to prevent infinite click loop
            
            int slotToClick = -1;
            if (title.contains("loadouts")) {
                int relativeSlot = (armorNumber - 1) % 12; // Loadouts has 12 per page
                int row = relativeSlot / 3;
                int col = relativeSlot % 3;
                slotToClick = 14 + (row * 9) + col;
            } else {
                int relativeSlot = (armorNumber - 1) % 9; // Normal has 9 per page
                slotToClick = 36 + relativeSlot;
            }
            
            final int finalSlotToClick = slotToClick;

            new Thread(() -> {
                try {
                    // Try checking/waiting up to 1000ms for the item to load
                    for (int i = 0; i < 20; i++) {
                        Thread.sleep(50L);
                        boolean success = mc.submit(() -> {
                            if (mc.screen == screen && finalSlotToClick < screen.getMenu().slots.size()) {
                                Slot slot = screen.getMenu().slots.get(finalSlotToClick);
                                ItemStack stack = slot.getItem();
                                if (!stack.isEmpty()) {
                                    boolean isEquipped = false;
                                    if (BomboConfig.get().disableUnequipWardrobe) {
                                        List<Component> tooltip = stack.getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
                                        for (Component line : tooltip) {
                                            if (line.getString().contains(": Equipped")) {
                                                isEquipped = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (!isEquipped) {
                                        if (mc.gameMode != null && mc.player != null) {
                                            mc.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                                        }
                                    }
                                    if (mc.player != null) {
                                        mc.player.closeContainer();
                                    }
                                    return true;
                                }
                            }
                            return false;
                        }).get();
                        if (success) break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
