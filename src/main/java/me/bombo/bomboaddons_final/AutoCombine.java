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
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;

public class AutoCombine {
    private static long lastAction = 0;
    private static boolean toggled = false;
    private static boolean wasDown = false;
    private static final String LOG_FILE = "bombo_autocombine.log";

    public static void log(String msg) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println("[" + new Date() + "] " + msg);
        } catch (Exception e) {
            // Ignore
        }
    }

    public static void onTick() {
        try {
            onTickUnsafe();
        } catch (Throwable t) {
            log("CRASH: " + t.toString());
        }
    }

    private static void onTickUnsafe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        
        BomboConfig.Settings config = BomboConfig.get();
        if (!config.anvilAutoCombineEnabled) return;

        // 1. Check if in valid GUI first
        boolean isVanillaAnvil = mc.screen instanceof AnvilScreen;
        boolean isChestAnvil = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString();
            if (title.toLowerCase().contains("anvil") || title.toLowerCase().contains("combine")) isChestAnvil = true;
        }

        if (!isVanillaAnvil && !isChestAnvil) {
            wasDown = false; // Reset to prevent proccing immediately on open
            return;
        }

        // 2. Toggle Logic (Only works while in the GUI)
        String bound = config.anvilAutoCombineKey;
        if (bound != null && !bound.isEmpty()) {
            int code = ClickLogic.getKeyCode(bound);
            if (code != -1) {
                long handle = findWindowHandle(mc.getWindow());
                if (handle != 0) {
                    boolean down = false;
                    if (code >= 0 && code < 8) {
                        down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, code) == 1;
                    } else {
                        down = org.lwjgl.glfw.GLFW.glfwGetKey(handle, code) == 1;
                    }
                    
                    if (down && !wasDown) {
                        toggled = !toggled;
                        Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Anvil Auto-Combine: " + (toggled ? "§aENABLED" : "§cDISABLED"));
                    }
                    wasDown = down;
                }
            }
        }

        // If "Require Keybind" is ON, we only work if we are in "Toggled ON" state
        if (config.anvilAutoCombineRequireKey && !toggled) return;

        // Safety: don't click if holding something with the mouse (Normal Use fix)
        try {
            ItemStack carried = mc.player.containerMenu.getCarried();
            if (carried != null && !carried.isEmpty()) return;
        } catch (Throwable t) {
            try {
                java.lang.reflect.Method m = mc.player.containerMenu.getClass().getMethod("getCursorItem");
                ItemStack carried = (ItemStack) m.invoke(mc.player.containerMenu);
                if (carried != null && !carried.isEmpty()) return;
            } catch (Throwable t2) {}
        }

        if (System.currentTimeMillis() - lastAction < config.anvilAutoCombineDelay) return;

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
                clickSlot(menu.containerId, resultSlot, 0, ClickType.PICKUP);
                lastAction = System.currentTimeMillis();
                return;
            }

            Map<String, Integer> enchants = getEnchantments(result);
            for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                Integer targetTier = config.anvilAutoCombine.get(entry.getKey());
                if (targetTier != null) {
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
            for (int i = invStart; i < menu.slots.size(); i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (stack.isEmpty()) continue;
                Map<String, Integer> enchants = getEnchantments(stack);
                if (enchants.size() != 1) continue;

                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    String enchantId = entry.getKey();
                    int tier = entry.getValue();
                    Integer targetTier = config.anvilAutoCombine.get(enchantId);

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
            ItemStack present = left.isEmpty() ? right : left;
            Map<String, Integer> enchants = getEnchantments(present);
            
            // Allow manual combination if item has multiple enchants
            if (enchants.size() > 1) return;

            if (enchants.size() == 1) {
                Map.Entry<String, Integer> entry = enchants.entrySet().iterator().next();
                String enchantId = entry.getKey();
                int tier = entry.getValue();
                Integer targetTier = config.anvilAutoCombine.get(enchantId);

                if (targetTier != null && tier < targetTier) {
                    int otherSlot = findMatching(menu, enchantId, tier, -1, invStart);
                    if (otherSlot != -1) {
                        clickSlot(menu.containerId, otherSlot, 0, ClickType.QUICK_MOVE);
                        lastAction = System.currentTimeMillis();
                        return;
                    }
                }
            }

            int occupiedSlot = left.isEmpty() ? rightSlot : leftSlot;
            clickSlot(menu.containerId, occupiedSlot, 0, ClickType.QUICK_MOVE);
            lastAction = System.currentTimeMillis();
        }
    }

    private static long findWindowHandle(com.mojang.blaze3d.platform.Window window) {
        try {
            for (java.lang.reflect.Method m : window.getClass().getDeclaredMethods()) {
                if (m.getReturnType() == long.class && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    long val = (long) m.invoke(window);
                    if (val > 1000) return val;
                }
            }
            for (java.lang.reflect.Field f : window.getClass().getDeclaredFields()) {
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    long val = (long) f.get(window);
                    if (val > 1000) return val;
                }
            }
        } catch (Exception e) {}
        return 0;
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
            tag.getCompound("ExtraAttributes").ifPresent(ea -> {
                ea.getCompound("enchantments").ifPresent(encTag -> {
                    for (String key : encTag.keySet()) {
                        enchants.put(key, encTag.getInt(key).orElse(0));
                    }
                });
            });
            if (enchants.isEmpty()) {
                tag.getCompound("enchantments").ifPresent(encTag -> {
                    for (String key : encTag.keySet()) {
                        enchants.put(key, encTag.getInt(key).orElse(0));
                    }
                });
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
