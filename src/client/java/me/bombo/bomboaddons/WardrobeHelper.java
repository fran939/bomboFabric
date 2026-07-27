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
    private static boolean isLoadoutMode = false;
    private static int loadoutStep = 0; // 0 = initial /loadout, 1 = clicking next page(s) to reach target, 2 = clicking slot

    public static void equip(int armorNumber) {
        equip(armorNumber, false);
    }

    public static void equipLoadout(int loadoutNumber) {
        if (loadoutNumber < 1 || loadoutNumber > 27) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        pendingArmorNumber = loadoutNumber;
        isLoadoutMode = true;
        loadoutStep = 0;

        mc.player.connection.sendCommand("loadout");
    }

    public static void equip(int armorNumber, boolean loadout) {
        if (armorNumber < 1) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        pendingArmorNumber = armorNumber;
        isLoadoutMode = loadout;
        loadoutStep = 0;

        if (loadout) {
            mc.player.connection.sendCommand("loadout");
        } else {
            int page = (armorNumber - 1) / 9 + 1;
            mc.player.connection.sendCommand("wd " + page);
        }
    }

    public static void onGuiOpen(AbstractContainerScreen<?> screen) {
        if (pendingArmorNumber == -1) return;
        String title = screen.getTitle().getString().toLowerCase();
        if (title.contains("wardrobe") || title.contains("armor sets") || title.contains("equipment sets") || title.contains("loadouts")) {
            Minecraft mc = Minecraft.getInstance();
            int number = pendingArmorNumber;
            
            if (isLoadoutMode || title.contains("loadouts")) {
                int targetPage = (number - 1) / 12 + 1; // 12 slots per page
                int currentScreenPage = 1;
                
                // Parse page from title e.g. "(2/3) Loadouts"
                String rawTitle = screen.getTitle().getString();
                if (rawTitle.startsWith("(") && rawTitle.contains("/") && rawTitle.contains(")")) {
                    try {
                        String pageStr = rawTitle.substring(1, rawTitle.indexOf("/"));
                        currentScreenPage = Integer.parseInt(pageStr.trim());
                    } catch (Exception ignored) {}
                }

                if (currentScreenPage < targetPage) {
                    // Need to click Next Page button (Slot 53, or item named "Next Page")
                    new Thread(() -> {
                        try {
                            for (int i = 0; i < 20; i++) {
                                Thread.sleep(50L);
                                boolean success = mc.submit(() -> {
                                    if (mc.screen == screen) {
                                        int nextSlot = -1;
                                        for (Slot slot : screen.getMenu().slots) {
                                            if (slot.hasItem()) {
                                                String itemName = slot.getItem().getHoverName().getString();
                                                if (itemName.contains("Next Page")) {
                                                    nextSlot = slot.index;
                                                    break;
                                                }
                                            }
                                        }
                                        if (nextSlot == -1 && screen.getMenu().slots.size() > 53) {
                                            nextSlot = 53;
                                        }

                                        if (nextSlot != -1 && mc.gameMode != null && mc.player != null) {
                                            mc.gameMode.handleContainerInput(screen.getMenu().containerId, nextSlot, 0, ContainerInput.PICKUP, mc.player);
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
                    return; // Wait for the next page screen to open and fire onGuiOpen again
                }

                // We are on the correct page! Click the slot
                pendingArmorNumber = -1; // Reset to prevent loop
                isLoadoutMode = false;

                int relativeSlot = (number - 1) % 12;
                int row = relativeSlot / 3;
                int col = relativeSlot % 3;
                final int slotToClick = 14 + (row * 9) + col;

                clickSlot(screen, mc, slotToClick);
            } else {
                pendingArmorNumber = -1; // Reset
                isLoadoutMode = false;
                int relativeSlot = (number - 1) % 9;
                final int slotToClick = 36 + relativeSlot;
                clickSlot(screen, mc, slotToClick);
            }
        }
    }

    private static void clickSlot(AbstractContainerScreen<?> screen, Minecraft mc, int slotToClick) {
        new Thread(() -> {
            try {
                for (int i = 0; i < 20; i++) {
                    Thread.sleep(50L);
                    boolean success = mc.submit(() -> {
                        if (mc.screen == screen && slotToClick < screen.getMenu().slots.size()) {
                            Slot slot = screen.getMenu().slots.get(slotToClick);
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
