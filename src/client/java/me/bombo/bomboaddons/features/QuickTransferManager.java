package me.bombo.bomboaddons.features;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class QuickTransferManager {
    private static long lastTransferTime = 0L;

    /**
     * Attempts to transfer all items matching the clicked slot's item type
     * between the player inventory and the opened container GUI (or vice versa).
     *
     * @param screen the container screen currently open
     * @param clickedSlot the slot clicked or hovered
     * @return true if one or more items were transferred
     */
    public static boolean handleSlotClick(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        if (screen == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastTransferTime < 150L) {
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null) return false;

        if (clickedSlot == null || !clickedSlot.hasItem()) {
            return false;
        }

        ItemStack referenceStack = clickedSlot.getItem();
        if (referenceStack == null || referenceStack.isEmpty()) {
            return false;
        }

        // Avoid transferring decorative menu panes
        if (isDecorative(referenceStack)) {
            return false;
        }

        Container sourceContainer = clickedSlot.container;
        if (sourceContainer == null) return false;

        BomboConfig.Settings s = BomboConfig.get();
        String guiTitle = ChatFormatting.stripFormatting(screen.getTitle().getString());

        // Check if the clicked item itself is protected/locked
        if (isSlotProtected(clickedSlot, guiTitle, s)) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cCannot transfer locked/protected item."));
            return false;
        }

        // Collect all slots from the same container that contain matching items
        List<Slot> matchingSlots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (slot.container != sourceContainer) continue;
            if (!slot.mayPickup(mc.player)) continue;

            // Skip locked / protected slots
            if (isSlotProtected(slot, guiTitle, s)) continue;

            ItemStack stack = slot.getItem();
            if (isDecorative(stack)) continue;

            if (isSameType(referenceStack, stack)) {
                matchingSlots.add(slot);
            }
        }

        if (matchingSlots.isEmpty()) {
            return false;
        }

        lastTransferTime = now;

        // Transfer all matching slots via QUICK_MOVE
        for (Slot slot : matchingSlots) {
            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
        }

        try {
            mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.4F);
        } catch (Throwable ignored) {}

        return true;
    }

    public static boolean isSameType(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        String idA = SkyblockUtils.getSkyblockId(a);
        String idB = SkyblockUtils.getSkyblockId(b);
        if (idA != null && !idA.isEmpty() && idB != null && !idB.isEmpty()) {
            return idA.equalsIgnoreCase(idB);
        }
        if ((idA == null || idA.isEmpty()) && (idB == null || idB.isEmpty())) {
            return a.getItem() == b.getItem();
        }
        return false;
    }

    public static boolean isDecorative(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        String name = stack.getItem().toString().toLowerCase();
        if (name.contains("stained_glass_pane")) {
            String hover = ChatFormatting.stripFormatting(stack.getHoverName().getString()).trim();
            return hover.isEmpty();
        }
        return false;
    }

    public static boolean isSlotProtected(Slot slot, String guiTitle, BomboConfig.Settings s) {
        if (slot == null || !slot.hasItem()) return false;
        if (s == null || s.blockedSlots == null || s.blockedSlots.isEmpty()) return false;

        // Check if bypass key is currently pressed
        int bypassKey = ClickLogic.getKeyCode(s.blockedSlotsBypassKey);
        if (bypassKey != -1) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null && GLFW.glfwGetKey(mc.getWindow().handle(), bypassKey) == 1) {
                return false;
            }
        }

        String currentGui = guiTitle != null ? guiTitle.toLowerCase() : "";
        String currentArea = BomboaddonsClient.currentArea != null ? BomboaddonsClient.currentArea.toLowerCase() : "";
        ItemStack stack = slot.getItem();
        String itemName = ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase();
        String sbId = SkyblockUtils.getSkyblockId(stack);
        if (sbId == null) sbId = "";
        sbId = sbId.toLowerCase();

        for (BomboConfig.BlockedSlotDef def : s.blockedSlots) {
            if (def == null || !def.enabled) continue;
            String im = def.itemMatcher != null ? def.itemMatcher.trim().toLowerCase() : "";
            String gm = def.guiMatcher != null ? def.guiMatcher.trim().toLowerCase() : "";
            String ism = def.islandMatcher != null ? def.islandMatcher.trim().toLowerCase() : "";

            boolean itemMatch = im.isEmpty() || itemName.contains(im) || sbId.equalsIgnoreCase(im);
            boolean guiMatch = gm.isEmpty() || currentGui.contains(gm);
            boolean islandMatch = ism.isEmpty() || currentArea.contains(ism);

            if (itemMatch && guiMatch && islandMatch && (!im.isEmpty() || !gm.isEmpty() || !ism.isEmpty())) {
                return true;
            }
        }
        return false;
    }
}
