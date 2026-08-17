package me.bombo.bomboaddons.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class CustomSlotManager {
   public static BomboConfig.CustomSlot getCustomSlot(Slot slot) {
      return slot == null ? null : getCustomSlot(slot.index);
   }

   public static BomboConfig.CustomSlot getCustomSlot(int slotIndex) {
      Minecraft mc = Minecraft.getInstance();
      Screen var3 = mc.screen;
      if (var3 instanceof AbstractContainerScreen<?> screen) {
         String title = screen.getTitle().getString();
         if (BomboConfig.get().customSlots != null) {
            for(BomboConfig.CustomSlot cs : BomboConfig.get().customSlots) {
               if (cs.slotIndex == slotIndex && title.toLowerCase().contains(cs.guiName.toLowerCase())) {
                  return cs;
               }
            }
         }
      }

      return null;
   }

   public static ItemStack getOverride(Slot slot) {
      BomboConfig.CustomSlot cs = getCustomSlot(slot);
      if (cs == null) {
         return null;
      } else {
         Item item = Items.BARRIER;

         try {
            label54: {
               Object res = BuiltInRegistries.ITEM.get(Identifier.parse(cs.icon.trim()));
               if (res instanceof Optional) {
                  Optional<?> o = (Optional)res;
                  if (o.isPresent()) {
                     Object inner = o.get();
                     if (inner instanceof Holder) {
                        Holder<?> h = (Holder)inner;
                        item = (Item)h.value();
                     } else if (inner instanceof Item) {
                        Item i = (Item)inner;
                        item = i;
                     }
                     break label54;
                  }
               }

               if (res instanceof Item) {
                  Item i = (Item)res;
                  item = i;
               }
            }
         } catch (Exception var12) {
         }

         try {
            ItemStack stack = new ItemStack(item);
            stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            String formattedName = cs.name.replace('&', '§');
            if (!formattedName.contains("§")) {
               formattedName = "§6§l" + formattedName;
            }

            stack.set(DataComponents.CUSTOM_NAME, Component.literal(formattedName));
            List<Component> lines = new ArrayList();
            lines.add(Component.literal("§7(From Bombo)"));
            lines.add(Component.literal(""));
            if (cs.description != null && !cs.description.isEmpty()) {
               for(String line : cs.description.split("\n")) {
                  String formattedLine = line.replace('&', '§');
                  if (!formattedLine.contains("§")) {
                     formattedLine = "§7" + formattedLine;
                  }

                  lines.add(Component.literal(formattedLine));
               }

               lines.add(Component.literal(""));
            }

            lines.add(Component.literal("§7Click here to run"));
            lines.add(Component.literal("§e" + cs.command));
            stack.set(DataComponents.LORE, new ItemLore(lines));
            return stack;
         } catch (Exception var11) {
            ItemStack err = new ItemStack(Items.BARRIER);
            err.set(DataComponents.CUSTOM_NAME, Component.literal("§cInvalid Custom Slot Icon: " + cs.icon));
            return err;
         }
      }
   }

   public static String getCommand(Slot slot) {
      BomboConfig.CustomSlot cs = getCustomSlot(slot);
      return cs != null ? cs.command : null;
   }
}
