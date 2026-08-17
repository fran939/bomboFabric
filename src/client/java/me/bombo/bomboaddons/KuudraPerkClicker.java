package me.bombo.bomboaddons;

import me.bombo.bomboaddons.kuudra.pearls.KuudraUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class KuudraPerkClicker {
   private static long lastClickTime = 0L;

   public static boolean shouldBlockAttack() {
      return System.currentTimeMillis() - lastClickTime < 300L;
   }

   public static boolean onMouseClicked(AbstractContainerScreen<?> screen, Slot slot, int button) {
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.perkMenuClicker) {
         return false;
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            return false;
         } else {
            boolean isKuudraOrLocal = KuudraUtils.inKuudra();
            if (!isKuudraOrLocal) {
               return false;
            } else {
               String title = screen.getTitle().getString().replaceAll("(?i)§.", "").trim();
               boolean isPerkMenu = title.contains("Perk Menu");
               boolean isAreYouSure = title.contains("Are you sure?");
               if (!isPerkMenu && !isAreYouSure) {
                  return false;
               } else {
                  boolean isRealItem = false;
                  if (slot != null && slot.hasItem()) {
                     ItemStack stack = slot.getItem();
                     String itemType = stack.getItem().toString().toLowerCase();
                     boolean isGlass = itemType.contains("glass_pane") || itemType.contains("stained_glass_pane");
                     boolean isBarrier = itemType.contains("barrier");
                     if (!isGlass && !isBarrier) {
                        isRealItem = true;
                     }
                  }

                  if (isRealItem) {
                     return false;
                  } else if (isPerkMenu) {
                     boolean swap = s.kuudraPerkSwapControls;
                     int cannonBtn = swap ? 1 : 0;
                     int ballistaBtn = swap ? 0 : 1;
                     if (button == cannonBtn) {
                        tryClickSlotWithName(screen, "Human Cannonball", 1);
                     } else if (button == ballistaBtn && !tryClickSlotWithName(screen, "Specialist Route", 0)) {
                        tryClickSlotWithName(screen, "Ballista Mechanic", 0);
                     }

                     lastClickTime = System.currentTimeMillis();
                     return true;
                  } else if (isAreYouSure) {
                     tryClickSlotWithName(screen, "Confirm", 0);
                     lastClickTime = System.currentTimeMillis();
                     return true;
                  } else {
                     return false;
                  }
               }
            }
         }
      }
   }

   private static boolean tryClickSlotWithName(AbstractContainerScreen<?> screen, String targetName, int button) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.gameMode != null && mc.player != null) {
         AbstractContainerMenu menu = screen.getMenu();

         for(int i = 0; i < menu.slots.size(); ++i) {
            Slot slot = (Slot)menu.slots.get(i);
            if (slot.hasItem()) {
               String name = slot.getItem().getHoverName().getString().toLowerCase();
               if (name.contains(targetName.toLowerCase())) {
                  mc.gameMode.handleContainerInput(menu.containerId, slot.index, button, ContainerInput.PICKUP, mc.player);
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static void onChatMessage(String rawMessage) {
      BomboConfig.Settings s = BomboConfig.get();
      String clean = rawMessage.replaceAll("(?i)§.", "");
      if (s.autoGfsToxic && clean.contains("[NPC] Elle: Phew! The Ballista is finally ready!")) {
         sendCommand("gfs TOXIC_ARROW_POISON " + s.autoGfsToxicCount);
      } else if (s.autoGfsTwilight && clean.contains("You purchased Human Cannonball!")) {
         sendCommand("gfs TWILIGHT_ARROW_POISON " + s.autoGfsTwilightCount);
      }

   }

   private static void sendCommand(String cmd) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.connection != null) {
         mc.player.connection.sendCommand(cmd);
      }

   }
}
