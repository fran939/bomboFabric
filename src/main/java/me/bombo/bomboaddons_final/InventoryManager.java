package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class InventoryManager {
    public static void captureCurrentGUI() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            String guiName = screen.getTitle().getString().replaceAll("§.", "");
            String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            List<String> itemData = new ArrayList<>();

            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());

            // The original code used a heuristic to determine container slots.
            // The provided edit iterates over all slots in the menu.
            // Following the provided edit's loop structure.
            for (Slot slot : screen.getMenu().slots) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    try {
                        Tag tag = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                        itemData.add(slot.index + "|" + tag.toString());
                    } catch (Exception e) {
                        System.err.println(
                                "[BomboAddons] Failed to serialize item in slot " + slot.index + ": " + e.getMessage());
                    }
                }
            }

            InventorySnapshot snapshot = new InventorySnapshot(guiName, date, itemData);
            InventoryConfig.addSnapshot(snapshot);

            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal(
                                "§a[BomboAddons] Saved inventory snapshot for: §6" + guiName + " §7(" + date + ")"),
                        false);
            }
        }
    }

    public static void openSnapshot(String name, int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        List<InventorySnapshot> snapshots = InventoryConfig.getSnapshots();
        String searchName = name.toLowerCase().replaceAll("[^a-z0-9]", "");

        if (searchName.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("§c[BomboAddons] Please specify a snapshot name."), false);
            return;
        }

        List<InventorySnapshot> matches = new ArrayList<>();
        for (InventorySnapshot s : snapshots) {
            String sn = s.guiName.toLowerCase().replaceAll("§.", "").replaceAll("[^a-z0-9]", "");
            if (sn.contains(searchName)) {
                matches.add(s);
            }
        }

        if (matches.isEmpty()) {
            mc.player.displayClientMessage(Component.literal("§c[BomboAddons] Snapshot not found: " + name), false);
            if (!snapshots.isEmpty()) {
                mc.player.displayClientMessage(Component.literal("§7Available snapshots:"), false);
                for (int i = 0; i < Math.min(snapshots.size(), 5); i++) {
                    mc.player.displayClientMessage(Component.literal("§7- " + snapshots.get(i).guiName), false);
                }
                if (snapshots.size() > 5) {
                    mc.player.displayClientMessage(Component.literal("§7... and " + (snapshots.size() - 5) + " more."),
                            false);
                }
            }
            return;
        }

        if (index > matches.size()) {
            mc.player.displayClientMessage(Component.literal("§c[BomboAddons] Only " + matches.size()
                    + " matches found for '" + name + "', but index " + index + " was requested."), false);
            return;
        }

        InventorySnapshot selected = matches.get(index - 1);
        mc.player.displayClientMessage(
                Component.literal(
                        "§a[BomboAddons] Opening snapshot: §6" + selected.guiName + " §7(" + selected.timestamp + ")"),
                false);

        // Use a separate thread with a small sleep to ensure we are completely out of
        // any
        // current screen (like ChatScreen) closing logic.
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
            mc.execute(() -> {
                try {
                    InventoryViewScreen screen = new InventoryViewScreen(selected);
                    mc.setScreen(screen);
                } catch (Exception e) {
                    mc.player.displayClientMessage(
                            Component.literal("§c[BomboAddons] Failed to open snapshot: " + e.getMessage()), false);
                    e.printStackTrace();
                }
            });
        }).start();
    }

    public static void listSnapshots(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        List<InventorySnapshot> snapshots = InventoryConfig.getSnapshots();
        if (snapshots.isEmpty()) {
            source.sendFeedback(Component.literal("§cNo inventory snapshots saved."));
            return;
        }
        source.sendFeedback(Component.literal("§6Saved Inventory Snapshots (Total: " + snapshots.size() + "):"));
        for (InventorySnapshot s : snapshots) {
            String cmdName = s.guiName.replaceAll(" ", "_");
            source.sendFeedback(Component.literal("§e- " + s.guiName + " §7(" + s.timestamp + ")")
                    .withStyle(style -> style.withClickEvent(
                            new net.minecraft.network.chat.ClickEvent.RunCommand("/checki " + cmdName))));
        }
    }
}
