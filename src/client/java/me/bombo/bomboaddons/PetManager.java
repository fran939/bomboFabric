package me.bombo.bomboaddons;

import me.bombo.bomboaddons.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;

@Environment(EnvType.CLIENT)
public class PetManager {
   private static String targetPetUuid = null;
   private static int timeoutTicks = 0;
   private static boolean petsMenuOpened = false;
   private static int pageTurnCooldown = 0;
   private static int pageIndex = 0;

   public static boolean isBusy() {
      return targetPetUuid != null;
   }

   private static void sendFeedback(FabricClientCommandSource source, String msg) {
      if (source != null) {
         source.sendFeedback(Component.literal(msg));
      } else {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
         }
      }

   }

   public static void savePet(FabricClientCommandSource source, String slot) {
      Minecraft mc = Minecraft.getInstance();
      ItemStack petItem = null;
      Screen var5 = mc.screen;
      if (var5 instanceof AbstractContainerScreen screen) {
         Slot hovered = ((AbstractContainerScreenAccessor)screen).getHoveredSlot();
         if (hovered != null && hovered.hasItem()) {
            petItem = hovered.getItem();
         }
      }

      if (petItem == null && mc.player != null) {
         ItemStack held = mc.player.getMainHandItem();
         if (!held.isEmpty()) {
            petItem = held;
         }
      }

      if (petItem == null) {
         sendFeedback(source, "§c[Bombo] Please hover over a pet in a menu or hold a pet in your hand!");
      } else {
         String uuid = getPetUuid(petItem);
         if (uuid != null && !uuid.isEmpty()) {
            String petName = petItem.getHoverName().getString();
            BomboConfig.get().petKeybinds.put(slot, uuid);
            BomboConfig.get().petNames.put(slot, petName);
            BomboConfig.save();
            sendFeedback(source, "§a[Bombo] Saved pet " + petName + " §7(UUID: " + uuid + ") §ato pet slot " + slot + "!");
         } else {
            sendFeedback(source, "§c[Bombo] No pet UUID found on this item!");
         }
      }
   }

   public static void applyPet(FabricClientCommandSource source, String slot) {
      String uuid = (String)BomboConfig.get().petKeybinds.get(slot);
      if (uuid != null && !uuid.isEmpty()) {
         sendFeedback(source, "§a[Bombo] Equipping pet in slot " + slot + "...");
         targetPetUuid = uuid;
         timeoutTicks = 100;
         petsMenuOpened = false;
         pageTurnCooldown = 0;
         pageIndex = 0;
         BomboaddonsClient.executeTracked("pet");
      } else {
         sendFeedback(source, "§c[Bombo] No pet saved in slot " + slot + "!");
      }
   }

   public static void onTick() {
      if (targetPetUuid != null) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player == null) {
            targetPetUuid = null;
         } else if (pageTurnCooldown > 0) {
            --pageTurnCooldown;
         } else {
            Screen var2 = mc.screen;
            if (!(var2 instanceof AbstractContainerScreen)) {
               if (petsMenuOpened) {
                  targetPetUuid = null;
               } else {
                  --timeoutTicks;
                  if (timeoutTicks <= 0) {
                     targetPetUuid = null;
                     mc.player.sendSystemMessage(Component.literal("§c[Bombo] Pets menu opening timed out!"));
                  }
               }
            } else {
               AbstractContainerScreen screen = (AbstractContainerScreen)var2;
               String title = screen.getTitle().getString();
               if (!title.toLowerCase().contains("pets")) {
                  if (petsMenuOpened) {
                     targetPetUuid = null;
                  }
               } else {
                  petsMenuOpened = true;
                  --timeoutTicks;
                  if (timeoutTicks <= 0) {
                     targetPetUuid = null;
                     mc.player.sendSystemMessage(Component.literal("§c[Bombo] Could not find matching pet in the menu!"));
                     mc.player.closeContainer();
                     return;
                  }

                  boolean anyItemLoaded = false;

                  for(int i = 10; i <= 43; ++i) {
                     if (i < screen.getMenu().slots.size() && !((Slot)screen.getMenu().slots.get(i)).getItem().isEmpty()) {
                        anyItemLoaded = true;
                        break;
                     }
                  }

                  if (!anyItemLoaded) {
                     return;
                  }

                  Slot targetSlot = null;

                  for(int i = 10; i <= 43; ++i) {
                     if (i < screen.getMenu().slots.size()) {
                        Slot slot = (Slot)screen.getMenu().slots.get(i);
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
                        for(Component line : targetSlot.getItem().getTooltipLines(TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
                           if (line.getString().contains("Click to despawn!")) {
                              isEquipped = true;
                              break;
                           }
                        }
                     }

                     if (!isEquipped && mc.gameMode != null) {
                        mc.gameMode.handleContainerInput(screen.getMenu().containerId, targetSlot.index, 0, ContainerInput.PICKUP, mc.player);
                     }

                     if (mc.player != null) {
                        mc.player.closeContainer();
                     }

                     targetPetUuid = null;
                     return;
                  }

                  if (pageIndex < 10) {
                     ItemStack nextSlotItem = null;
                     if (53 < screen.getMenu().slots.size()) {
                        nextSlotItem = ((Slot)screen.getMenu().slots.get(53)).getItem();
                     }

                     if (nextSlotItem != null && !nextSlotItem.isEmpty()) {
                        String name = nextSlotItem.getHoverName().getString().toLowerCase();
                        if (name.contains("next page") || name.contains("next")) {
                           if (mc.gameMode != null) {
                              mc.gameMode.handleContainerInput(screen.getMenu().containerId, 53, 0, ContainerInput.PICKUP, mc.player);
                           }

                           pageTurnCooldown = 10;
                           ++pageIndex;
                           timeoutTicks = 100;
                           return;
                        }
                     }
                  }
               }
            }

         }
      }
   }

   public static String getPetUuid(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
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
      } else {
         return null;
      }
   }
}
