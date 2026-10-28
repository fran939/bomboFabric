package me.bombo.bomboaddons.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class TransferAllManager {
    private static long lastTransferTime = 0L;

    /**
     * Transfers all eligible items from the source container to the other container
     * (e.g. all player inventory items into chest, or all chest items into player inventory),
     * respecting exclusions (weapons, armor, tools, locked slots, hotbar slot 9, custom items).
     */
    public static boolean handleTransferAll(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        if (screen == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastTransferTime < 200L) {
            return false;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null) return false;

        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return false;

        // Determine if we are depositing (inventory -> chest) or withdrawing (chest -> inventory)
        boolean isDeposit = true;
        if (clickedSlot != null && clickedSlot.container != null) {
            isDeposit = (clickedSlot.container instanceof Inventory);
        }

        String guiTitle = ChatFormatting.stripFormatting(screen.getTitle().getString());
        List<Slot> slotsToMove = new ArrayList<>();

        for (Slot slot : menu.slots) {
            if (slot == null || !slot.hasItem()) continue;
            if (!slot.mayPickup(mc.player)) continue;

            boolean isPlayerSlot = (slot.container instanceof Inventory);
            if (isDeposit && !isPlayerSlot) continue;
            if (!isDeposit && isPlayerSlot) continue;

            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) continue;

            // Never move decorative glass panes
            if (QuickTransferManager.isDecorative(stack)) continue;

            // Locked slot protection
            if (QuickTransferManager.isSlotProtected(slot, guiTitle, s)) continue;

            // Check if slot or item is excluded
            if (isExcluded(slot, stack, s)) continue;

            slotsToMove.add(slot);
        }

        if (slotsToMove.isEmpty()) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §7No eligible items to transfer (all matching exceptions)."));
            }
            return false;
        }

        lastTransferTime = now;

        // Execute quick move on each eligible slot
        int movedCount = 0;
        for (Slot slot : slotsToMove) {
            mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
            movedCount++;
        }

        try {
            mc.player.playSound(SoundEvents.ITEM_PICKUP, 0.4F, 1.2F);
        } catch (Throwable ignored) {}

        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §aTransferred §e" + movedCount + " §astacks " + (isDeposit ? "to container." : "to inventory.")));
        }

        return true;
    }

    public static boolean isExcluded(Slot slot, ItemStack stack, BomboConfig.Settings s) {
        if (stack == null || stack.isEmpty()) return true;

        // Hotbar Slot 9 (the SkyBlock menu) is NEVER moved!
        if (slot != null && slot.container instanceof Inventory) {
            int containerSlot = slot.getContainerSlot();
            if (containerSlot == 8) { // 0-indexed: 8 is slot 9
                return true;
            }
            if (s.transferExcludedSlots != null && s.transferExcludedSlots.contains(containerSlot)) {
                return true;
            }
        }

        String itemName = ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
        String sbId = SkyblockUtils.getSkyblockId(stack);
        String cleanSbId = sbId != null ? sbId.toLowerCase(Locale.ROOT) : "";

        // SkyBlock Menu failsafe
        if (itemName.contains("skyblock menu") || cleanSbId.contains("skyblock_menu") || stack.getItem() == net.minecraft.world.item.Items.NETHER_STAR) {
            return true;
        }

        // Custom excluded items list
        if (s.transferExcludedItems != null) {
            for (String exc : s.transferExcludedItems) {
                if (exc == null || exc.trim().isEmpty()) continue;
                String cleanExc = exc.trim().toLowerCase(Locale.ROOT);
                if (itemName.contains(cleanExc) || cleanSbId.equalsIgnoreCase(cleanExc) || cleanSbId.contains(cleanExc)) {
                    return true;
                }
            }
        }

        // Weapon exclusion
        if (s.transferExcludeWeapons && isWeapon(stack, cleanSbId)) {
            return true;
        }

        // Armor exclusion
        if (s.transferExcludeArmor && isArmor(stack, cleanSbId)) {
            return true;
        }

        // Tool exclusion
        if (s.transferExcludeTools && isTool(stack, cleanSbId)) {
            return true;
        }

        return false;
    }

    public static boolean isWeapon(ItemStack stack, String cleanSbId) {
        String regName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (regName.contains("sword") || regName.contains("bow") || regName.contains("crossbow") || regName.contains("trident") || regName.contains("mace")) {
            return true;
        }
        if (cleanSbId != null && !cleanSbId.isEmpty()) {
            return cleanSbId.contains("sword") || cleanSbId.contains("blade") || cleanSbId.contains("bow") ||
                   cleanSbId.contains("katana") || cleanSbId.contains("scythe") || cleanSbId.contains("hyperion") ||
                   cleanSbId.contains("valkyrie") || cleanSbId.contains("astraea") || cleanSbId.contains("scylla") ||
                   cleanSbId.contains("terminator") || cleanSbId.contains("juju") || cleanSbId.contains("shortbow") ||
                   cleanSbId.contains("wand") || cleanSbId.contains("staff") || cleanSbId.contains("spoon") ||
                   cleanSbId.contains("dagger");
        }
        return false;
    }

    public static boolean isArmor(ItemStack stack, String cleanSbId) {
        String regName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (regName.contains("helmet") || regName.contains("chestplate") || regName.contains("leggings") ||
            regName.contains("boots") || regName.contains("elytra") || regName.contains("turtle_scute")) {
            return true;
        }
        if (cleanSbId != null && !cleanSbId.isEmpty()) {
            return cleanSbId.contains("helmet") || cleanSbId.contains("chestplate") ||
                   cleanSbId.contains("leggings") || cleanSbId.contains("boots") ||
                   cleanSbId.contains("crown") || cleanSbId.contains("fedora") || cleanSbId.contains("goggles") ||
                   cleanSbId.contains("cloak") || cleanSbId.contains("belt") || cleanSbId.contains("bracelet") ||
                   cleanSbId.contains("necklace") || cleanSbId.contains("gloves");
        }
        return false;
    }

    public static boolean isTool(ItemStack stack, String cleanSbId) {
        String regName = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (regName.contains("pickaxe") || regName.contains("axe") || regName.contains("shovel") ||
            regName.contains("hoe") || regName.contains("fishing_rod") || regName.contains("shears") ||
            regName.contains("flint_and_steel") || regName.contains("lead") || regName.contains("brush")) {
            return true;
        }
        if (cleanSbId != null && !cleanSbId.isEmpty()) {
            return cleanSbId.contains("pickaxe") || cleanSbId.contains("drill") || cleanSbId.contains("axe") ||
                   cleanSbId.contains("shovel") || cleanSbId.contains("hoe") || cleanSbId.contains("rod") ||
                   cleanSbId.contains("gauntlet") || cleanSbId.contains("shears");
        }
        return false;
    }

    public static void handleCommand(String trimmed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return;

        String[] parts = trimmed.split("\\s+");
        int startIdx = 1;
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("transfer")) {
                startIdx = i + 1;
                break;
            }
        }

        if (startIdx >= parts.length) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §6=== Transfer All Settings ==="));
            mc.player.sendSystemMessage(Component.literal("§7Hotkeys: §e" + (s.transferAllInventoryKey != null ? s.transferAllInventoryKey : "NONE") + " §7(Dump All) / §e" + (s.quickTransferAllKey != null ? s.quickTransferAllKey : "LEFT_ALT") + " §7(Same Item)"));
            mc.player.sendSystemMessage(Component.literal("§7Exclude Weapons: " + (s.transferExcludeWeapons ? "§aENABLED" : "§cDISABLED") + " §8(/b transfer weapons)"));
            mc.player.sendSystemMessage(Component.literal("§7Exclude Armor: " + (s.transferExcludeArmor ? "§aENABLED" : "§cDISABLED") + " §8(/b transfer armor)"));
            mc.player.sendSystemMessage(Component.literal("§7Exclude Tools: " + (s.transferExcludeTools ? "§aENABLED" : "§cDISABLED") + " §8(/b transfer tools)"));
            
            StringBuilder slotsStr = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                if (i == 8 || (s.transferExcludedSlots != null && s.transferExcludedSlots.contains(i))) {
                    if (slotsStr.length() > 0) slotsStr.append(", ");
                    slotsStr.append(i + 1).append(i == 8 ? " (Menu)" : "");
                }
            }
            mc.player.sendSystemMessage(Component.literal("§7Excluded Hotbar Slots: §e" + slotsStr + " §8(/b transfer slot <1-8>)"));
            
            int customCount = s.transferExcludedItems != null ? s.transferExcludedItems.size() : 0;
            mc.player.sendSystemMessage(Component.literal("§7Custom Excluded Items: §e" + customCount + " items §8(/b transfer list / add / remove)"));
            mc.player.sendSystemMessage(Component.literal("§7Usage: §f/b transfer [weapons|armor|tools|slot <1-8>|add <name>|remove <name>|list|reset]"));
            return;
        }

        String action = parts[startIdx].toLowerCase(Locale.ROOT);
        switch (action) {
            case "weapons":
            case "weapon":
                s.transferExcludeWeapons = !s.transferExcludeWeapons;
                BomboConfig.save();
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §7Exclude Weapons: " + (s.transferExcludeWeapons ? "§aENABLED" : "§cDISABLED")));
                break;
            case "armor":
                s.transferExcludeArmor = !s.transferExcludeArmor;
                BomboConfig.save();
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §7Exclude Armor: " + (s.transferExcludeArmor ? "§aENABLED" : "§cDISABLED")));
                break;
            case "tools":
            case "tool":
                s.transferExcludeTools = !s.transferExcludeTools;
                BomboConfig.save();
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §7Exclude Tools: " + (s.transferExcludeTools ? "§aENABLED" : "§cDISABLED")));
                break;
            case "slot":
                if (startIdx + 1 < parts.length) {
                    try {
                        int slotNum = Integer.parseInt(parts[startIdx + 1]);
                        if (slotNum < 1 || slotNum > 9) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cSlot must be between 1 and 9!"));
                            return;
                        }
                        if (slotNum == 9) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §eHotbar Slot 9 (SkyBlock Menu) is permanently protected!"));
                            return;
                        }
                        int slotIdx = slotNum - 1;
                        if (s.transferExcludedSlots == null) s.transferExcludedSlots = new java.util.HashSet<>();
                        if (s.transferExcludedSlots.contains(slotIdx)) {
                            s.transferExcludedSlots.remove(slotIdx);
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §aHotbar Slot " + slotNum + " is now §eINCLUDED §ain transfers."));
                        } else {
                            s.transferExcludedSlots.add(slotIdx);
                            mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cHotbar Slot " + slotNum + " is now §eEXCLUDED §cfrom transfers."));
                        }
                        BomboConfig.save();
                    } catch (NumberFormatException e) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cInvalid slot number! Usage: /b transfer slot <1-8>"));
                    }
                } else {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cUsage: /b transfer slot <1-8>"));
                }
                break;
            case "add":
                if (startIdx + 1 < parts.length) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = startIdx + 1; i < parts.length; i++) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(parts[i]);
                    }
                    String item = sb.toString().trim();
                    if (!item.isEmpty()) {
                        if (s.transferExcludedItems == null) s.transferExcludedItems = new ArrayList<>();
                        s.transferExcludedItems.add(item);
                        BomboConfig.save();
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §aAdded §e\"" + item + "\" §ato transfer exclusion list."));
                    }
                } else {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cUsage: /b transfer add <item name or ID>"));
                }
                break;
            case "remove":
            case "del":
            case "delete":
                if (startIdx + 1 < parts.length) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = startIdx + 1; i < parts.length; i++) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(parts[i]);
                    }
                    String item = sb.toString().trim();
                    if (s.transferExcludedItems != null && s.transferExcludedItems.removeIf(sItem -> sItem.equalsIgnoreCase(item))) {
                        BomboConfig.save();
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §aRemoved §e\"" + item + "\" §afrom transfer exclusion list."));
                    } else {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cItem \"" + item + "\" not found in exclusion list."));
                    }
                } else {
                    mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cUsage: /b transfer remove <item name or ID>"));
                }
                break;
            case "list":
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §6=== Custom Excluded Items ==="));
                if (s.transferExcludedItems == null || s.transferExcludedItems.isEmpty()) {
                    mc.player.sendSystemMessage(Component.literal("§7(No custom excluded items)"));
                } else {
                    for (int i = 0; i < s.transferExcludedItems.size(); i++) {
                        mc.player.sendSystemMessage(Component.literal(" §7" + (i + 1) + ". §e" + s.transferExcludedItems.get(i)));
                    }
                }
                break;
            case "reset":
                s.transferExcludeWeapons = true;
                s.transferExcludeArmor = true;
                s.transferExcludeTools = true;
                s.transferExcludedSlots = new java.util.HashSet<>(java.util.List.of(8));
                s.transferExcludedItems = new ArrayList<>(java.util.List.of("skyblock menu", "nether star"));
                BomboConfig.save();
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §aReset transfer settings and exclusions to defaults."));
                break;
            default:
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cUnknown option: " + action + ". Usage: /b transfer [weapons|armor|tools|slot <1-8>|add <name>|remove <name>|list|reset]"));
                break;
        }
    }
}
