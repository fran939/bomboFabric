package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.inventory.ClickType;
import java.util.Map;
import java.util.HashMap;

public class AutoCombine {
    private static long lastAction = 0;
    private static final long DELAY = 200;

    public static void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!BomboConfig.get().anvilAutoCombineEnabled) return;
        if (mc.player == null || mc.level == null) return;
        
        if (mc.screen != null && BomboConfig.get().apiDebug && System.currentTimeMillis() % 1000 < 50) {
             Bomboaddons.sendMessage("§7[Debug] Screen: " + mc.screen.getClass().getSimpleName() + " Title: " + mc.screen.getTitle().getString());
        }

        boolean isVanillaAnvil = mc.screen instanceof AnvilScreen;
        boolean isChestAnvil = false;
        
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString();
            if (title.contains("Anvil")) isChestAnvil = true;
        }

        if (!isVanillaAnvil && !isChestAnvil) return;

        if (System.currentTimeMillis() - lastAction < BomboConfig.get().anvilAutoCombineDelay) return;

        net.minecraft.world.inventory.AbstractContainerMenu menu;
        int leftSlot, rightSlot, resultSlot, invStart;
        
        if (isVanillaAnvil) {
            menu = ((AnvilScreen) mc.screen).getMenu();
            leftSlot = 0; rightSlot = 1; resultSlot = 2; invStart = 3;
        } else {
            menu = ((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen).getMenu();
            leftSlot = 29; rightSlot = 33; resultSlot = 22; invStart = 54;
        }
        
        if (menu.slots.size() < invStart) return;

        // 1. Collect result or click 'Combine' / 'Claim' if available
        ItemStack result = menu.getSlot(resultSlot).getItem();
        if (!result.isEmpty()) {
            boolean isCombineAnvil = result.getItem() == net.minecraft.world.item.Items.ANVIL;
            boolean isSign = result.getItem() == net.minecraft.world.item.Items.OAK_SIGN;
            boolean hasGlint = result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null && result.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

            if ((isCombineAnvil && hasGlint) || isSign) {
                // This is the Hypixel "Click to combine!" or "Claim the result" icon
                clickSlot(menu.containerId, resultSlot, 0, ClickType.PICKUP);
                lastAction = System.currentTimeMillis();
                return;
            }

            Map<String, Integer> enchants = getEnchantments(result);
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                Integer targetTier = BomboConfig.get().anvilAutoCombine.get(entry.getKey());
                if (targetTier != null) {
                    // Use quick move (shift click) for result
                    clickSlot(menu.containerId, resultSlot, 0, ClickType.QUICK_MOVE);
                    lastAction = System.currentTimeMillis();
                    return;
                }
            }
        }

        // 2. If anvil is empty or partially filled, try to fill it
        ItemStack left = menu.getSlot(leftSlot).getItem();
        ItemStack right = menu.getSlot(rightSlot).getItem();

        if (left.isEmpty() && right.isEmpty()) {
            // Both empty: find a pair of matching tiers in inventory
            for (int i = invStart; i < menu.slots.size(); i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (stack.isEmpty()) continue;
                Map<String, Integer> enchants = getEnchantments(stack);
                if (enchants.size() != 1) continue;

                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    String enchantId = entry.getKey();
                    int tier = entry.getValue();
                    Integer targetTier = BomboConfig.get().anvilAutoCombine.get(enchantId);

                    if (targetTier != null && tier < targetTier) {
                        int otherSlot = findMatching(menu, enchantId, tier, i, invStart);
                        if (otherSlot != -1) {
                            clickSlot(menu.containerId, i, 0, ClickType.QUICK_MOVE);
                            lastAction = System.currentTimeMillis();
                            return;
                        }
                    }
                }
            }
        } else if (left.isEmpty() || right.isEmpty()) {
            // One slot is filled: find a matching book for the one already there
            ItemStack present = left.isEmpty() ? right : left;
            int targetSlot = left.isEmpty() ? leftSlot : rightSlot;
            
            Map<String, Integer> enchants = getEnchantments(present);
            if (enchants.size() == 1) {
                Map.Entry<String, Integer> entry = enchants.entrySet().iterator().next();
                String enchantId = entry.getKey();
                int tier = entry.getValue();
                Integer targetTier = BomboConfig.get().anvilAutoCombine.get(enchantId);

                if (targetTier != null && tier < targetTier) {
                    int otherSlot = findMatching(menu, enchantId, tier, -1, invStart);
                    if (otherSlot != -1) {
                        clickSlot(menu.containerId, otherSlot, 0, ClickType.QUICK_MOVE);
                        lastAction = System.currentTimeMillis();
                        return;
                    }
                }
            }
            
            // If we couldn't find a match for what's in the anvil, or it shouldn't be there, take it out
            int occupiedSlot = left.isEmpty() ? rightSlot : leftSlot;
            clickSlot(menu.containerId, occupiedSlot, 0, ClickType.QUICK_MOVE);
            lastAction = System.currentTimeMillis();
        }
    }

    private static int findMatching(net.minecraft.world.inventory.AbstractContainerMenu menu, String enchantId, int tier, int skipSlot, int invStart) {
        for (int i = invStart; i < menu.slots.size(); i++) {
            if (i == skipSlot) continue;
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;

            Map<String, Integer> enchants = getEnchantments(stack);
            if (enchants.size() != 1) continue;
            
            Integer foundTier = enchants.get(enchantId);
            if (foundTier != null && foundTier == tier) {
                return i;
            }
        }
        return -1;
    }

    public static Map<String, Integer> getEnchantments(ItemStack stack) {
        Map<String, Integer> enchants = new HashMap<>();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (BomboConfig.get().apiDebug) {
                Bomboaddons.sendMessage("§7[Debug] NBT: " + tag.keySet());
            }

            CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
            if (ea == null && tag.contains("enchantments")) ea = tag; // Fallback if flat
            
            if (ea != null && ea.contains("enchantments")) {
                CompoundTag encTag = ea.getCompound("enchantments").orElse(null);
                if (encTag != null) {
                    for (String key : encTag.keySet()) {
                        enchants.put(key, encTag.getInt(key).orElse(0));
                    }
                }
            }
        }
        return enchants;
    }

    private static void clickSlot(int syncId, int slotId, int button, ClickType clickType) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleInventoryMouseClick(syncId, slotId, button, clickType, mc.player);
        }
    }
}
