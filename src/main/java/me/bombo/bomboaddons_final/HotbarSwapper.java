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

        // Keep track of which slots in the inventory have already been claimed to
        // prevent duplicate usage
        List<Integer> claimedInvSlots = new ArrayList<>();

        for (int hotbarIndex = 0; hotbarIndex < 8; hotbarIndex++) {
            HotbarConfig.SlotData targetData = targetHotbar[hotbarIndex];

            if (targetData == null)
                continue;

            // Check if the current item is exactly what we want
            ItemStack currentStack = player.getInventory().getItem(hotbarIndex);
            if (matches(currentStack, targetData)) {
                claimedInvSlots.add(hotbarIndex);
                continue; // Already correct
            }

            int foundSlot = -1;

            // Priority 1: Exact UUID match
            if (targetData.skyblockUuid != null) {
                foundSlot = findSlotByUuid(player, targetData.skyblockUuid, hotbarIndex, claimedInvSlots);
            }

            // Priority 2: Skyblock ID match
            if (foundSlot == -1 && targetData.skyblockId != null) {
                foundSlot = findSlotBySkyblockId(player, targetData.skyblockId, targetData.customName, hotbarIndex,
                        claimedInvSlots);
            }

            // Priority 3: Vanilla fallback (if the target didn't even have Skyblock NBT)
            if (foundSlot == -1 && targetData.skyblockUuid == null && targetData.skyblockId == null) {
                foundSlot = findSlotByVanilla(player, targetData.vanillaId, targetData.customName, hotbarIndex,
                        claimedInvSlots);
            }

            if (foundSlot != -1) {
                claimedInvSlots.add(foundSlot);

                if (client.gameMode != null) {
                    int containerId = player.inventoryMenu.containerId;

                    int sourceScreenSlot = (foundSlot < 9) ? (foundSlot + 36) : foundSlot;

                    // Use SWAP click type for an atomic swap operation
                    // button = hotbarIndex (0-8), clickType = SWAP
                    client.gameMode.handleInventoryMouseClick(containerId, sourceScreenSlot, hotbarIndex,
                            ClickType.SWAP,
                            player);
                }
            } else {
                String idLabel = targetData.skyblockId != null ? targetData.skyblockId : targetData.vanillaId;
                player.displayClientMessage(
                        Component.literal(
                                "§cCould not find matching item for slot " + (hotbarIndex + 1) + ": " + idLabel),
                        false);
            }
        }
    }

    private static int findSlotByUuid(LocalPlayer player, String targetUuid, int hotbarIndex,
            List<Integer> claimedInvSlots) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            if (invSlot == hotbarIndex || claimedInvSlots.contains(invSlot))
                continue;

            ItemStack stack = player.getInventory().getItem(invSlot);
            if (stack.isEmpty())
                continue;

            String uuid = getSkyblockUuid(stack);
            if (targetUuid.equals(uuid)) {
                return invSlot;
            }
        }
        return -1;
    }

    private static int findSlotBySkyblockId(LocalPlayer player, String targetId, String targetCustomName,
            int hotbarIndex,
            List<Integer> claimedInvSlots) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            if (invSlot == hotbarIndex || claimedInvSlots.contains(invSlot))
                continue;

            ItemStack stack = player.getInventory().getItem(invSlot);
            if (stack.isEmpty())
                continue;

            String id = getSkyblockId(stack);
            if (targetId.equals(id)) {
                String customName = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
                boolean nameMatch = (targetCustomName == null && customName == null) ||
                        (targetCustomName != null && targetCustomName.equals(customName));
                if (nameMatch) {
                    return invSlot;
                }
            }
        }
        return -1;
    }

    private static int findSlotByVanilla(LocalPlayer player, String targetVanillaId, String targetCustomName,
            int hotbarIndex, List<Integer> claimedInvSlots) {
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            if (invSlot == hotbarIndex || claimedInvSlots.contains(invSlot))
                continue;

            ItemStack stack = player.getInventory().getItem(invSlot);
            if (stack.isEmpty())
                continue;

            String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (targetVanillaId.equals(vanillaId)) {
                String customName = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
                boolean nameMatch = (targetCustomName == null && customName == null) ||
                        (targetCustomName != null && targetCustomName.equals(customName));
                if (nameMatch) {
                    return invSlot;
                }
            }
        }
        return -1;
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
