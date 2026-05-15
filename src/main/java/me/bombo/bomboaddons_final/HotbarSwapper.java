package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class HotbarSwapper {

    public static boolean saveSnapshot(String id) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null)
            return false;

        HotbarConfig.SlotData[] data = new HotbarConfig.SlotData[8];
        // Hotbar slots are 0-7 in the player's inventory (Slot 9 at index 8 is
        // excluded)
        for (int i = 0; i < 8; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                String sbUuid = getSkyblockUuid(stack);
                String sbId = getSkyblockId(stack);
                String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                String customName = stack.has(DataComponents.CUSTOM_NAME)
                        ? stack.getHoverName().getString()
                        : null;

                data[i] = new HotbarConfig.SlotData(sbUuid, sbId, vanillaId, customName);
            } else {
                data[i] = null;
            }
        }

        HotbarConfig.saveSnapshot(id, data);
        return true;
    }

    public static boolean deleteSnapshot(String id) {
        Map<String, HotbarConfig.SlotData[]> snapshots = HotbarConfig.getSnapshots();
        if (snapshots.containsKey(id)) {
            HotbarConfig.deleteSnapshot(id);
            return true;
        }
        return false;
    }

    public static boolean exists(String id) {
        return HotbarConfig.getSnapshots().containsKey(id);
    }

    public static Iterable<String> list() {
        return HotbarConfig.getSnapshots().keySet();
    }

    public static void apply(String id) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null)
            return;

        HotbarConfig.SlotData[] targetHotbar = HotbarConfig.getSnapshots().get(id);
        if (targetHotbar == null)
            return;

        // Create a virtual inventory to track item movements during swaps
        HotbarConfig.SlotData[] virtualInv = new HotbarConfig.SlotData[36];
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                virtualInv[i] = new HotbarConfig.SlotData(
                    getSkyblockUuid(stack),
                    getSkyblockId(stack),
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null
                );
            } else {
                virtualInv[i] = null;
            }
        }

        if (BomboConfig.get().apiDebug) {
            System.out.println("DEBUG: Applying Hotbar Snapshot: " + id);
            for (int i = 0; i < 36; i++) {
                if (virtualInv[i] != null) System.out.println("  Slot " + i + ": " + virtualInv[i].skyblockId + " (" + virtualInv[i].skyblockUuid + ")");
            }
        }

        for (int hotbarIndex = 0; hotbarIndex < 8; hotbarIndex++) {
            HotbarConfig.SlotData targetData = targetHotbar[hotbarIndex];

            if (targetData == null)
                continue;

            // Check if the current item in the virtual inventory is what we want
            if (matches(virtualInv[hotbarIndex], targetData)) {
                if (BomboConfig.get().apiDebug) System.out.println("DEBUG: Slot " + hotbarIndex + " already matches: " + targetData.skyblockId);
                continue;
            }

            int foundSlot = -1;

            // Priority 1: Exact UUID match
            if (targetData.skyblockUuid != null) {
                foundSlot = findVirtualSlotByUuid(virtualInv, targetData.skyblockUuid, hotbarIndex, targetHotbar);
            }

            // Priority 2: Skyblock ID match
            if (foundSlot == -1 && targetData.skyblockId != null) {
                foundSlot = findVirtualSlotBySkyblockId(virtualInv, targetData.skyblockId, targetData.customName, hotbarIndex, targetHotbar);
            }

            // Priority 3: Vanilla fallback
            if (foundSlot == -1 && targetData.skyblockUuid == null && targetData.skyblockId == null) {
                foundSlot = findVirtualSlotByVanilla(virtualInv, targetData.vanillaId, targetData.customName, hotbarIndex, targetHotbar);
            }

            if (foundSlot != -1) {
                if (client.gameMode != null) {
                    int containerId = player.inventoryMenu.containerId;
                    int sourceScreenSlot = (foundSlot < 9) ? (foundSlot + 36) : foundSlot;

                    if (BomboConfig.get().apiDebug) {
                        System.out.println("DEBUG: Swapping " + targetData.skyblockId + " from slot " + foundSlot + " (Screen: " + sourceScreenSlot + ") to hotbar " + hotbarIndex);
                    }

                    // Execute the actual swap
                    client.gameMode.handleInventoryMouseClick(containerId, sourceScreenSlot, hotbarIndex, ClickType.SWAP, player);
                    
                    // Update the virtual inventory to reflect the swap
                    HotbarConfig.SlotData temp = virtualInv[hotbarIndex];
                    virtualInv[hotbarIndex] = virtualInv[foundSlot];
                    virtualInv[foundSlot] = temp;
                }
            } else {
                String idLabel = targetData.skyblockId != null ? targetData.skyblockId : targetData.vanillaId;
                player.displayClientMessage(
                        Component.literal("§cCould not find matching item for slot " + (hotbarIndex + 1) + ": " + idLabel),
                        false);
                if (BomboConfig.get().apiDebug) {
                    System.out.println("DEBUG: FAILED to find " + idLabel + " for slot " + hotbarIndex);
                }
            }
        }
    }

    private static int findVirtualSlotByUuid(HotbarConfig.SlotData[] virtualInv, String targetUuid, int hotbarIndex, HotbarConfig.SlotData[] targetHotbar) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            if (invSlot == hotbarIndex) continue;
            
            // If we've already correctly filled a hotbar slot, don't take the item from there
            if (invSlot < hotbarIndex && targetHotbar[invSlot] != null && matches(virtualInv[invSlot], targetHotbar[invSlot])) {
                continue;
            }

            HotbarConfig.SlotData data = virtualInv[invSlot];
            if (data != null && targetUuid.equals(data.skyblockUuid)) {
                return invSlot;
            }
        }
        return -1;
    }

    private static int findVirtualSlotBySkyblockId(HotbarConfig.SlotData[] virtualInv, String targetId, String targetCustomName, int hotbarIndex, HotbarConfig.SlotData[] targetHotbar) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            if (invSlot == hotbarIndex) continue;
            
            if (invSlot < hotbarIndex && targetHotbar[invSlot] != null && matches(virtualInv[invSlot], targetHotbar[invSlot])) {
                continue;
            }

            HotbarConfig.SlotData data = virtualInv[invSlot];
            if (data != null && targetId.equals(data.skyblockId)) {
                boolean nameMatch = (targetCustomName == null && data.customName == null) ||
                                    (targetCustomName != null && targetCustomName.equals(data.customName));
                if (nameMatch) return invSlot;
            }
        }
        return -1;
    }

    private static int findVirtualSlotByVanilla(HotbarConfig.SlotData[] virtualInv, String targetVanillaId, String targetCustomName, int hotbarIndex, HotbarConfig.SlotData[] targetHotbar) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            if (invSlot == hotbarIndex) continue;

            if (invSlot < hotbarIndex && targetHotbar[invSlot] != null && matches(virtualInv[invSlot], targetHotbar[invSlot])) {
                continue;
            }

            HotbarConfig.SlotData data = virtualInv[invSlot];
            if (data != null && targetVanillaId.equals(data.vanillaId)) {
                boolean nameMatch = (targetCustomName == null && data.customName == null) ||
                                    (targetCustomName != null && targetCustomName.equals(data.customName));
                if (nameMatch) return invSlot;
            }
        }
        return -1;
    }

    private static boolean matches(HotbarConfig.SlotData s1, HotbarConfig.SlotData s2) {
        if (s1 == null || s2 == null) return s1 == s2;
        
        if (s2.skyblockUuid != null) {
            return s2.skyblockUuid.equals(s1.skyblockUuid);
        }
        
        if (s2.skyblockId != null) {
            if (s2.skyblockId.equals(s1.skyblockId)) {
                return (s2.customName == null && s1.customName == null) ||
                       (s2.customName != null && s2.customName.equals(s1.customName));
            }
            return false;
        }
        
        if (s2.vanillaId.equals(s1.vanillaId)) {
            return (s2.customName == null && s1.customName == null) ||
                   (s2.customName != null && s2.customName.equals(s1.customName));
        }
        
        return false;
    }

    private static boolean matches(ItemStack stack, HotbarConfig.SlotData targetData) {
        if (stack.isEmpty() || targetData == null)
            return false;

        // Strict priority matching
        if (targetData.skyblockUuid != null) {
            String uuid = getSkyblockUuid(stack);
            return targetData.skyblockUuid.equals(uuid);
        }

        if (targetData.skyblockId != null) {
            String id = getSkyblockId(stack);
            if (targetData.skyblockId.equals(id)) {
                String customName = stack.has(DataComponents.CUSTOM_NAME)
                        ? stack.getHoverName().getString()
                        : null;
                boolean nameMatch = (targetData.customName == null && customName == null) ||
                        (targetData.customName != null && targetData.customName.equals(customName));
                if (nameMatch)
                    return true;
            }
        }

        // Vanilla fallback matching
        String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!vanillaId.equals(targetData.vanillaId))
            return false;

        String customName = stack.has(DataComponents.CUSTOM_NAME)
                ? stack.getHoverName().getString()
                : null;

        if (targetData.customName == null) {
            return customName == null;
        } else {
            return targetData.customName.equals(customName);
        }
    }

    private static String getSkyblockUuid(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("uuid")) {
                return tag.getString("uuid").orElse(null);
            } else if (tag.contains("ExtraAttributes")) {
                CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
                if (extraAttributes != null && extraAttributes.contains("uuid")) {
                    return extraAttributes.getString("uuid").orElse(null);
                }
            }
        }
        return null;
    }

    private static String getSkyblockId(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("id")) {
                return tag.getString("id").orElse(null);
            } else if (tag.contains("ExtraAttributes")) {
                CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
                if (extraAttributes != null && extraAttributes.contains("id")) {
                    return extraAttributes.getString("id").orElse(null);
                }
            }
        }
        return null;
    }
}
