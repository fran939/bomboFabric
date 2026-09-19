package me.bombo.bomboaddons;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class HotbarSwapper {
   public static boolean saveSnapshot(String id) {
      Minecraft client = Minecraft.getInstance();
      LocalPlayer player = client.player;
      if (player == null) {
         return false;
      } else {
         HotbarConfig.SlotData[] data = new HotbarConfig.SlotData[8];

         for(int i = 0; i < 8; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
               String sbUuid = getSkyblockUuid(stack);
               String sbId = getSkyblockId(stack);
               String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
               String customName = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
               data[i] = new HotbarConfig.SlotData(sbUuid, sbId, vanillaId, customName);
            } else {
               data[i] = null;
            }
         }

         HotbarConfig.saveSnapshot(id, data);
         return true;
      }
   }

   public static boolean deleteSnapshot(String id) {
      Map<String, HotbarConfig.SlotData[]> snapshots = HotbarConfig.getSnapshots();
      if (snapshots.containsKey(id)) {
         HotbarConfig.deleteSnapshot(id);
         return true;
      } else {
         return false;
      }
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
      if (player != null) {
         HotbarConfig.SlotData[] targetHotbar = (HotbarConfig.SlotData[])HotbarConfig.getSnapshots().get(id);
         if (targetHotbar != null) {
            HotbarConfig.SlotData[] virtualInv = new HotbarConfig.SlotData[36];

            for(int i = 0; i < 36; ++i) {
               ItemStack stack = player.getInventory().getItem(i);
               if (!stack.isEmpty()) {
                  virtualInv[i] = new HotbarConfig.SlotData(getSkyblockUuid(stack), getSkyblockId(stack), BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null);
               } else {
                  virtualInv[i] = null;
               }
            }

            if (BomboConfig.get().apiDebug) {
               System.out.println("DEBUG: Applying Hotbar Snapshot: " + id);

               for(int i = 0; i < 36; ++i) {
                  if (virtualInv[i] != null) {
                     System.out.println("  Slot " + i + ": " + virtualInv[i].skyblockId + " (" + virtualInv[i].skyblockUuid + ")");
                  }
               }
            }

            for(int hotbarIndex = 0; hotbarIndex < 8; ++hotbarIndex) {
               HotbarConfig.SlotData targetData = targetHotbar[hotbarIndex];
               if (targetData != null) {
                  if (matches(virtualInv[hotbarIndex], targetData)) {
                     if (BomboConfig.get().apiDebug) {
                        System.out.println("DEBUG: Slot " + hotbarIndex + " already matches: " + targetData.skyblockId);
                     }
                  } else {
                     int foundSlot = -1;
                     if (targetData.skyblockUuid != null) {
                        foundSlot = findVirtualSlotByUuid(virtualInv, targetData.skyblockUuid, hotbarIndex, targetHotbar);
                     }

                     if (foundSlot == -1 && targetData.skyblockId != null) {
                        foundSlot = findVirtualSlotBySkyblockId(virtualInv, targetData.skyblockId, targetData.customName, hotbarIndex, targetHotbar);
                     }

                     if (foundSlot == -1 && targetData.skyblockUuid == null && targetData.skyblockId == null) {
                        foundSlot = findVirtualSlotByVanilla(virtualInv, targetData.vanillaId, targetData.customName, hotbarIndex, targetHotbar);
                     }

                     if (foundSlot != -1) {
                        if (client.gameMode != null) {
                           int containerId = player.inventoryMenu.containerId;
                           int sourceScreenSlot = foundSlot < 9 ? foundSlot + 36 : foundSlot;
                           if (BomboConfig.get().apiDebug) {
                              System.out.println("DEBUG: Swapping " + targetData.skyblockId + " from slot " + foundSlot + " (Screen: " + sourceScreenSlot + ") to hotbar " + hotbarIndex);
                           }

                           client.gameMode.handleContainerInput(containerId, sourceScreenSlot, hotbarIndex, ContainerInput.SWAP, player);
                           HotbarConfig.SlotData temp = virtualInv[hotbarIndex];
                           virtualInv[hotbarIndex] = virtualInv[foundSlot];
                           virtualInv[foundSlot] = temp;
                        }
                     } else {
                        String idLabel = targetData.skyblockId != null ? targetData.skyblockId : targetData.vanillaId;
                        player.sendSystemMessage(Component.literal("§cCould not find matching item for slot " + (hotbarIndex + 1) + ": " + idLabel));
                        if (BomboConfig.get().apiDebug) {
                           System.out.println("DEBUG: FAILED to find " + idLabel + " for slot " + hotbarIndex);
                        }
                     }
                  }
               }
            }

         }
      }
   }

   private static int findVirtualSlotByUuid(HotbarConfig.SlotData[] virtualInv, String targetUuid, int hotbarIndex, HotbarConfig.SlotData[] targetHotbar) {
      for(int invSlot = 0; invSlot < 36; ++invSlot) {
         if (invSlot != hotbarIndex && (invSlot >= hotbarIndex || targetHotbar[invSlot] == null || !matches(virtualInv[invSlot], targetHotbar[invSlot]))) {
            HotbarConfig.SlotData data = virtualInv[invSlot];
            if (data != null && targetUuid.equals(data.skyblockUuid)) {
               return invSlot;
            }
         }
      }

      return -1;
   }

   private static int findVirtualSlotBySkyblockId(HotbarConfig.SlotData[] virtualInv, String targetId, String targetCustomName, int hotbarIndex, HotbarConfig.SlotData[] targetHotbar) {
      for(int invSlot = 0; invSlot < 36; ++invSlot) {
         if (invSlot != hotbarIndex && (invSlot >= hotbarIndex || targetHotbar[invSlot] == null || !matches(virtualInv[invSlot], targetHotbar[invSlot]))) {
            HotbarConfig.SlotData data = virtualInv[invSlot];
            if (data != null && targetId.equals(data.skyblockId)) {
               boolean nameMatch = targetCustomName == null && data.customName == null || targetCustomName != null && targetCustomName.equals(data.customName);
               if (nameMatch) {
                  return invSlot;
               }
            }
         }
      }

      return -1;
   }

   private static int findVirtualSlotByVanilla(HotbarConfig.SlotData[] virtualInv, String targetVanillaId, String targetCustomName, int hotbarIndex, HotbarConfig.SlotData[] targetHotbar) {
      for(int invSlot = 0; invSlot < 36; ++invSlot) {
         if (invSlot != hotbarIndex && (invSlot >= hotbarIndex || targetHotbar[invSlot] == null || !matches(virtualInv[invSlot], targetHotbar[invSlot]))) {
            HotbarConfig.SlotData data = virtualInv[invSlot];
            if (data != null && targetVanillaId.equals(data.vanillaId)) {
               boolean nameMatch = targetCustomName == null && data.customName == null || targetCustomName != null && targetCustomName.equals(data.customName);
               if (nameMatch) {
                  return invSlot;
               }
            }
         }
      }

      return -1;
   }

   private static boolean matches(HotbarConfig.SlotData s1, HotbarConfig.SlotData s2) {
      if (s1 != null && s2 != null) {
         if (s2.skyblockUuid != null) {
            return s2.skyblockUuid.equals(s1.skyblockUuid);
         } else if (s2.skyblockId != null) {
            if (!s2.skyblockId.equals(s1.skyblockId)) {
               return false;
            } else {
               return s2.customName == null && s1.customName == null || s2.customName != null && s2.customName.equals(s1.customName);
            }
         } else if (!s2.vanillaId.equals(s1.vanillaId)) {
            return false;
         } else {
            return s2.customName == null && s1.customName == null || s2.customName != null && s2.customName.equals(s1.customName);
         }
      } else {
         return s1 == s2;
      }
   }

   private static boolean matches(ItemStack stack, HotbarConfig.SlotData targetData) {
      if (!stack.isEmpty() && targetData != null) {
         if (targetData.skyblockUuid != null) {
            String uuid = getSkyblockUuid(stack);
            return targetData.skyblockUuid.equals(uuid);
         } else {
            if (targetData.skyblockId != null) {
               String id = getSkyblockId(stack);
               if (targetData.skyblockId.equals(id)) {
                  String customName = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
                  boolean nameMatch = targetData.customName == null && customName == null || targetData.customName != null && targetData.customName.equals(customName);
                  if (nameMatch) {
                     return true;
                  }
               }
            }

            String vanillaId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!vanillaId.equals(targetData.vanillaId)) {
               return false;
            } else {
               String customName = stack.has(DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : null;
               if (targetData.customName == null) {
                  return customName == null;
               } else {
                  return targetData.customName.equals(customName);
               }
            }
         }
      } else {
         return false;
      }
   }

   private static String getSkyblockUuid(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         if (tag.contains("uuid")) {
            return tag.getString("uuid").orElse(null);
         }

         if (tag.contains("ExtraAttributes")) {
            CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
            if (extraAttributes != null && extraAttributes.contains("uuid")) {
               return extraAttributes.getString("uuid").orElse(null);
            }
         }
      }

      return null;
   }

   private static String getSkyblockId(ItemStack stack) {
      CustomData customData = (CustomData)stack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         if (tag.contains("id")) {
            return tag.getString("id").orElse(null);
         }

         if (tag.contains("ExtraAttributes")) {
            CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
            if (extraAttributes != null && extraAttributes.contains("id")) {
               return extraAttributes.getString("id").orElse(null);
            }
         }
      }

      return null;
   }
}
