package me.bombo.bomboaddons;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class InventoryManager {
   public static void captureCurrentGUI() {
      Minecraft mc = Minecraft.getInstance();
      Screen var2 = mc.screen;
      if (var2 instanceof AbstractContainerScreen<?> screen) {
         String guiName = screen.getTitle().getString().replaceAll("§.", "");
         String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
         List<String> itemData = new ArrayList();
         RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());

         for(Slot slot : screen.getMenu().slots) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
               try {
                  Tag tag = (Tag)ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
                  int var13 = slot.index;
                  itemData.add(var13 + "|" + tag.toString());
               } catch (Exception e) {
                  int var10001 = slot.index;
                  System.err.println("[BomboAddons] Failed to serialize item in slot " + var10001 + ": " + e.getMessage());
               }
            }
         }

         InventorySnapshot snapshot = new InventorySnapshot(guiName, date, itemData);
         InventoryConfig.addSnapshot(snapshot);
         if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§a[BomboAddons] Saved inventory snapshot for: §6" + guiName + " §7(" + date + ")"));
         }
      }

   }

   public static void openSnapshot(String name, int index) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         List<InventorySnapshot> snapshots = InventoryConfig.getSnapshots();
         String searchName = name.toLowerCase().replaceAll("[^a-z0-9]", "");
         if (searchName.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§c[BomboAddons] Please specify a snapshot name."));
         } else {
            List<InventorySnapshot> matches = new ArrayList();

            for(InventorySnapshot s : snapshots) {
               String sn = s.guiName.toLowerCase().replaceAll("§.", "").replaceAll("[^a-z0-9]", "");
               if (sn.contains(searchName)) {
                  matches.add(s);
               }
            }

            if (!matches.isEmpty()) {
               if (index > matches.size()) {
                  mc.player.sendSystemMessage(Component.literal("§c[BomboAddons] Only " + matches.size() + " matches found for '" + name + "', but index " + index + " was requested."));
               } else {
                  InventorySnapshot selected = (InventorySnapshot)matches.get(index - 1);
                  mc.player.sendSystemMessage(Component.literal("§a[BomboAddons] Opening snapshot: §6" + selected.guiName + " §7(" + selected.timestamp + ")"));
                  (new Thread(() -> {
                     try {
                        Thread.sleep(100L);
                     } catch (InterruptedException var3) {
                     }

                     mc.execute(() -> {
                        try {
                           InventoryViewScreen screen = new InventoryViewScreen(selected);
                           mc.setScreen(screen);
                        } catch (Exception e) {
                           mc.player.sendSystemMessage(Component.literal("§c[BomboAddons] Failed to open snapshot: " + e.getMessage()));
                           e.printStackTrace();
                        }

                     });
                  })).start();
               }
            } else {
               mc.player.sendSystemMessage(Component.literal("§c[BomboAddons] Snapshot not found: " + name));
               if (!snapshots.isEmpty()) {
                  mc.player.sendSystemMessage(Component.literal("§7Available snapshots:"));

                  for(int i = 0; i < Math.min(snapshots.size(), 5); ++i) {
                     LocalPlayer var10000 = mc.player;
                     Object var10001 = snapshots.get(i);
                     var10000.sendSystemMessage(Component.literal("§7- " + ((InventorySnapshot)var10001).guiName));
                  }

                  if (snapshots.size() > 5) {
                     LocalPlayer var11 = mc.player;
                     int var12 = snapshots.size();
                     var11.sendSystemMessage(Component.literal("§7... and " + (var12 - 5) + " more."));
                  }
               }

            }
         }
      }
   }

   public static void listSnapshots(FabricClientCommandSource source) {
      List<InventorySnapshot> snapshots = InventoryConfig.getSnapshots();
      if (snapshots.isEmpty()) {
         source.sendFeedback(Component.literal("§cNo inventory snapshots saved."));
      } else {
         source.sendFeedback(Component.literal("§6Saved Inventory Snapshots (Total: " + snapshots.size() + "):"));

         for(InventorySnapshot s : snapshots) {
            String cmdName = s.guiName.replaceAll(" ", "_");
            source.sendFeedback(Component.literal("§e- " + s.guiName + " §7(" + s.timestamp + ")").withStyle((style) -> style.withClickEvent(new ClickEvent.RunCommand("/checki " + cmdName))));
         }

      }
   }

   public static List<String> getSnapshotNames() {
      List<String> names = new ArrayList();

      for(InventorySnapshot s : InventoryConfig.getSnapshots()) {
         if (s.guiName != null && !s.guiName.isEmpty()) {
            String cmdName = s.guiName.replaceAll(" ", "_");
            if (!names.contains(cmdName)) {
               names.add(cmdName);
            }

            if (!names.contains(s.guiName)) {
               names.add(s.guiName);
            }
         }
      }

      return names;
   }
}
