package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class KuudraPerkClicker {
    private static long lastClickTime = 0;

    public static boolean shouldBlockAttack() {
        return System.currentTimeMillis() - lastClickTime < 300;
    }
    
    public static boolean onMouseClicked(AbstractContainerScreen<?> screen, Slot slot, int button) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.perkMenuClicker) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        boolean isKuudraOrLocal = me.bombo.bomboaddons_final.kuudra.pearls.KuudraUtils.inKuudra();
        if (!isKuudraOrLocal) return false;

        String title = screen.getTitle().getString().replaceAll("(?i)§.", "").trim();
        boolean isPerkMenu = title.contains("Perk Menu");
        boolean isAreYouSure = title.contains("Are you sure?");

        if (!isPerkMenu && !isAreYouSure) return false;

        // Condition: "and i click anywhere on the screen, and there is not a real item"
        // Real item means: slot is not null, slot has an item, and it is not a glass pane
        boolean isRealItem = false;
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            String itemType = stack.getItem().toString().toLowerCase();
            boolean isGlass = itemType.contains("glass_pane") || itemType.contains("stained_glass_pane");
            if (!isGlass) {
                isRealItem = true;
            }
        }

        if (isRealItem) {
            // Normal click - let Minecraft handle it
            return false;
        }

        if (isPerkMenu) {
            if (button == 0) { // Left Click
                // Try clicking Specialist Route, fallback to Ballista Mechanic
                if (!tryClickSlotWithName(screen, "Specialist Route", 0)) {
                    tryClickSlotWithName(screen, "Ballista Mechanic", 0);
                }
            } else if (button == 1) { // Right Click
                // Click Human Cannonball
                tryClickSlotWithName(screen, "Human Cannonball", 1);
            }
            lastClickTime = System.currentTimeMillis();
            return true;
        } else if (isAreYouSure) {
            // Click Confirm with left click (pickup)
            if (button == 0) {
                tryClickSlotWithName(screen, "Confirm", 0);
            }
            lastClickTime = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    private static boolean tryClickSlotWithName(AbstractContainerScreen<?> screen, String targetName, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem()) continue;
            String name = slot.getItem().getHoverName().getString().toLowerCase();
            if (name.contains(targetName.toLowerCase())) {
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, button, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                return true;
            }
        }
        return false;
    }

    public static void onChatMessage(String rawMessage) {
        BomboConfig.Settings s = BomboConfig.get();
        String clean = rawMessage.replaceAll("(?i)§.", "");

        // Ballista is ready → get toxic arrows
        if (s.autoGfsToxic && clean.contains("[NPC] Elle: Phew! The Ballista is finally ready!")) {
            sendCommand("gfs TOXIC_ARROW_POISON 21");
        }
        // Purchased Human Cannonball → get twilight arrows
        else if (s.autoGfsTwilight && clean.contains("You purchased Human Cannonball!")) {
            sendCommand("gfs TWILIGHT_ARROW_POISON 2");
        }
    }

    private static void sendCommand(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand(cmd);
        }
    }
}
