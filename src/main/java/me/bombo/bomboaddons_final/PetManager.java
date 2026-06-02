package me.bombo.bomboaddons_final;

import me.bombo.bomboaddons_final.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

@Environment(EnvType.CLIENT)
public class PetManager {
    private static String targetPetUuid = null;
    private static int timeoutTicks = 0;
    private static boolean petsMenuOpened = false;
    private static int pageTurnCooldown = 0;
    private static int pageIndex = 0;

    private static void sendFeedback(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String msg) {
        if (source != null) {
            source.sendFeedback(Component.literal(msg));
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(msg), false);
            }
        }
    }

    public static void savePet(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String slot) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack petItem = null;

        // Check if hovering a pet in an active container screen
        if (mc.screen instanceof AbstractContainerScreen screen) {
            Slot hovered = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
            if (hovered != null && hovered.hasItem()) {
                petItem = hovered.getItem();
            }
        }

        // If not hovering, check if holding an item
        if (petItem == null && mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEmpty()) {
                petItem = held;
            }
        }

        if (petItem == null) {
            sendFeedback(source, "§c[Bombo] Please hover over a pet in a menu or hold a pet in your hand!");
            return;
        }

        String uuid = getPetUuid(petItem);
        if (uuid == null || uuid.isEmpty()) {
            sendFeedback(source, "§c[Bombo] No pet UUID found on this item!");
            return;
        }

        String petName = petItem.getHoverName().getString();
        BomboConfig.get().petKeybinds.put(slot, uuid);
        BomboConfig.save();

        sendFeedback(source, "§a[Bombo] Saved pet " + petName + " §7(UUID: " + uuid + ") §ato pet slot " + slot + "!");
    }

    public static void applyPet(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String slot) {
        String uuid = BomboConfig.get().petKeybinds.get(slot);
        if (uuid == null || uuid.isEmpty()) {
            sendFeedback(source, "§c[Bombo] No pet saved in slot " + slot + "!");
            return;
        }

        sendFeedback(source, "§a[Bombo] Equipping pet in slot " + slot + "...");
        targetPetUuid = uuid;
        timeoutTicks = 100; // 5 seconds timeout
        petsMenuOpened = false;
        pageTurnCooldown = 0;
        pageIndex = 0;

        // Send /pet command to open the menu
        BomboaddonsClient.executeTracked("pet");
    }

    public static void onTick() {
        if (targetPetUuid == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            targetPetUuid = null;
            return;
        }

        if (pageTurnCooldown > 0) {
            pageTurnCooldown--;
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen screen) {
            String title = screen.getTitle().getString();
            if (title.toLowerCase().contains("pets")) {
                petsMenuOpened = true;
                
                // Decrement timeout while in the menu
                timeoutTicks--;
                if (timeoutTicks <= 0) {
                    targetPetUuid = null;
                    mc.player.displayClientMessage(Component.literal("§c[Bombo] Could not find matching pet in the menu!"), false);
                    mc.player.closeContainer();
                    return;
                }

                boolean anyItemLoaded = false;
                for (int i = 10; i <= 43; i++) {
                    if (i < screen.getMenu().slots.size() && !screen.getMenu().slots.get(i).getItem().isEmpty()) {
                        anyItemLoaded = true;
                        break;
                    }
                }
                if (!anyItemLoaded) {
                    return; // Wait for items to load!
                }

                // Scan slots 10 to 43 (inclusive)
                Slot targetSlot = null;
                for (int i = 10; i <= 43; i++) {
                    if (i < screen.getMenu().slots.size()) {
                        Slot slot = screen.getMenu().slots.get(i);
                        ItemStack stack = slot.getItem();
                        String itemUuid = getPetUuid(stack);
                        if (itemUuid != null && itemUuid.equals(targetPetUuid)) {
                            targetSlot = slot;
                            break;
                        }
                    }
                }

                if (targetSlot != null) {
                    boolean isEquipped = false;
                    if (BomboConfig.get().disableUnequipPet) {
                        java.util.List<Component> tooltip = targetSlot.getItem().getTooltipLines(
                            net.minecraft.world.item.Item.TooltipContext.of(mc.level),
                            mc.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL
                        );
                        for (Component line : tooltip) {
                            if (line.getString().contains("Click to despawn!")) {
                                isEquipped = true;
                                break;
                            }
                        }
                    }
                    if (!isEquipped) {
                        if (mc.gameMode != null) {
                            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, targetSlot.index, 0, ClickType.PICKUP, mc.player);
                        }
                    }
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    targetPetUuid = null;
                    return;
                }

                // If not found, try to turn the page
                if (pageIndex < 10) {
                    ItemStack nextSlotItem = null;
                    if (53 < screen.getMenu().slots.size()) {
                        nextSlotItem = screen.getMenu().slots.get(53).getItem();
                    }

                    if (nextSlotItem != null && !nextSlotItem.isEmpty()) {
                        String name = nextSlotItem.getHoverName().getString().toLowerCase();
                        if (name.contains("next page") || name.contains("next")) {
                            if (mc.gameMode != null) {
                                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, 53, 0, ClickType.PICKUP, mc.player);
                            }
                            pageTurnCooldown = 10; // Wait 500ms for page turn
                            pageIndex++;
                            timeoutTicks = 100; // Reset timeout for the new page
                            return;
                        }
                    }
                }
            } else {
                // Not in Pets menu but in some other menu
                if (petsMenuOpened) {
                    // If it was opened and now they are in another menu, cancel
                    targetPetUuid = null;
                }
            }
        } else {
            // No screen open
            if (petsMenuOpened) {
                // If it was opened and now they closed it, cancel
                targetPetUuid = null;
            } else {
                // Still waiting for the menu to open
                timeoutTicks--;
                if (timeoutTicks <= 0) {
                    targetPetUuid = null;
                    mc.player.displayClientMessage(Component.literal("§c[Bombo] Pets menu opening timed out!"), false);
                }
            }
        }
    }

    public static String getPetUuid(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("ExtraAttributes")) {
                CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
                if (extraAttributes != null && extraAttributes.contains("uuid")) {
                    return extraAttributes.getString("uuid").orElse(null);
                }
            }
            if (tag.contains("uuid")) {
                return tag.getString("uuid").orElse(null);
            }
        }
        return null;
    }
}
