package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.BazaarUtils;
import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.ClickLogic;
import me.bombo.bomboaddons_final.LF;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import me.bombo.bomboaddons_final.SBECommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin({ AbstractContainerScreen.class })
public abstract class ItemHotkeysMixin {
   @Shadow
   protected Slot hoveredSlot;

   @Inject(method = { "keyPressed" }, at = { @At("HEAD") }, cancellable = true)
   private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
         int pressedKey = event.key();
         int tradeBoundKey = ClickLogic.getKeyCode(BomboConfig.get().tradeKey);
         int recipeBoundKey = ClickLogic.getKeyCode(BomboConfig.get().recipeKey);
         int usageBoundKey = ClickLogic.getKeyCode(BomboConfig.get().usageKey);
         int showItemBoundKey = ClickLogic.getKeyCode(BomboConfig.get().showItemKey);
         int copyNbtBoundKey = ClickLogic.getKeyCode(BomboConfig.get().copyNbtKey);
         int gfsMaxBoundKey = ClickLogic.getKeyCode(BomboConfig.get().gfsMaxKey);
         int gfsStackBoundKey = ClickLogic.getKeyCode(BomboConfig.get().gfsStackKey);

         if (pressedKey != -1 && pressedKey == tradeBoundKey) {
            if (this.handleKey(this.hoveredSlot.getItem(), "TRADE")) {
               cir.setReturnValue(true);
            }
         } else if (pressedKey != -1 && pressedKey == recipeBoundKey) {
            if (this.handleKey(this.hoveredSlot.getItem(), "RECIPE")) {
               cir.setReturnValue(true);
            }
         } else if (pressedKey != -1 && pressedKey == usageBoundKey) {
            if (this.handleKey(this.hoveredSlot.getItem(), "USAGE")) {
               cir.setReturnValue(true);
            }
         } else if (pressedKey != -1 && pressedKey == showItemBoundKey) {
            this.showItemInfo(this.hoveredSlot.getItem());
            cir.setReturnValue(true);
         } else if (pressedKey != -1 && pressedKey == copyNbtBoundKey) {
            this.copyItemNbt(this.hoveredSlot.getItem());
            cir.setReturnValue(true);
         } else if (pressedKey != -1 && pressedKey == gfsMaxBoundKey) {
            this.handleGFS(this.hoveredSlot.getItem(), true);
            cir.setReturnValue(true);
         } else if (pressedKey != -1 && pressedKey == gfsStackBoundKey) {
            this.handleGFS(this.hoveredSlot.getItem(), false);
            cir.setReturnValue(true);
         }
      }
   }

   private void showItemInfo(ItemStack itemStack) {
      String name = itemStack.getHoverName().getString();
      Minecraft.getInstance().player.displayClientMessage(Component.literal("§6--- Item Info: " + name + " ---"), false);
      
      CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
               String eaString = ea.toString();
               HoverEvent hover = SBECommands.createHoverEvent(eaString);
               Style style = Style.EMPTY;
               if (hover != null) style = style.withHoverEvent(hover);
               
               Minecraft.getInstance().player.displayClientMessage(Component.literal("§eExtraAttributes: §7(Hover for JSON)").withStyle(style), false);
               
               String eaId = ea.getString("id").orElse("");
               if (!eaId.isEmpty()) {
                  Minecraft.getInstance().player.displayClientMessage(Component.literal("§7Internal ID: §f" + eaId), false);
               }
               
               String modifier = ea.getString("modifier").orElse("");
               if (!modifier.isEmpty()) {
                  Minecraft.getInstance().player.displayClientMessage(Component.literal("§7Modifier: §d" + modifier), false);
               }
         }
      }

      ItemLore itemLore = itemStack.get(DataComponents.LORE);
      if (itemLore != null) {
         for (Component line : itemLore.lines()) {
            Minecraft.getInstance().player.displayClientMessage(line, false);
         }
      }
   }

   private void handleGFS(ItemStack itemStack, boolean max) {
      String skyblockId = getSkyblockId(itemStack);
      if (skyblockId == null || skyblockId.isEmpty()) return;

      if (max) {
         Minecraft.getInstance().player.connection.sendCommand("gfs " + skyblockId + " 999999");
      } else {
         int currentCount = itemStack.getCount();
         int maxStack = itemStack.getMaxStackSize();
         if (currentCount < maxStack) {
            int needed = maxStack - currentCount;
            Minecraft.getInstance().player.connection.sendCommand("gfs " + skyblockId + " " + needed);
         } else {
            Minecraft.getInstance().player.connection.sendCommand("gfs " + skyblockId + " " + maxStack);
         }
      }
   }

   private String getSkyblockId(ItemStack itemStack) {
      CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         String id = tag.getString("id").orElse("");
         if (!id.isEmpty()) return id;
         
         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
            return ea.getString("id").orElse("");
         }
      }
      return itemStack.getItem().toString().toUpperCase();
   }

   private void copyItemNbt(ItemStack itemStack) {
      CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         String nbtString = tag.toString();
         Minecraft.getInstance().keyboardHandler.setClipboard(nbtString);
         Minecraft.getInstance().player.displayClientMessage(Component.literal("§6Item NBT copied to clipboard."), false);
      } else {
         Minecraft.getInstance().player.displayClientMessage(Component.literal("§cNo NBT data found for this item."), false);
      }
   }

   private boolean handleKey(ItemStack itemStack, String action) {
      String skyblockId = getSkyblockId(itemStack);
      if (skyblockId == null || skyblockId.isEmpty()) {
         skyblockId = itemStack.getItem().toString().toUpperCase();
      }

      if (skyblockId != null) {
         if (action.equalsIgnoreCase("RECIPE") || action.equalsIgnoreCase("USAGE")) {
            Minecraft.getInstance().player.connection.sendCommand("viewrecipe " + skyblockId);
            return true;
         }

         if (action.equalsIgnoreCase("TRADE")) {
            String title = Minecraft.getInstance().screen.getTitle().getString();
            String cleanName = this.cleanName(itemStack);
            
            if (title.contains("Hunting Box")) {
               Minecraft.getInstance().player.connection.sendCommand("bz " + cleanName + " Shard");
               return true;
            }
            
            if (title.contains("Attribute Menu")) {
               ItemLore itemLore = itemStack.get(DataComponents.LORE);
               if (itemLore != null) {
                  for (Component line : itemLore.lines()) {
                     String lineStr = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                     if (lineStr.startsWith("Source: ")) {
                        String sourceItem = lineStr.substring(8).replaceAll("\\(C\\d+\\)", "").trim();
                        Minecraft.getInstance().player.connection.sendCommand("bz " + sourceItem);
                        return true;
                     }
                  }
               }
            }

            boolean isBazaar = BazaarUtils.isBazaarItem(skyblockId);
            if (skyblockId.contains("ENCHANTED_BOOK")) isBazaar = true;
            
            if (isBazaar) {
               Minecraft.getInstance().player.connection.sendCommand("bz " + cleanName.toLowerCase());
            } else {
               Minecraft.getInstance().player.connection.sendCommand("ahs " + cleanName);
            }
            return true;
         }
      }
      return false;
   }

   private String cleanName(ItemStack itemStack) {
      String originalName = itemStack.getHoverName().getString();
      String name = originalName.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      
      // Remove quantity prefixes like "64x " or "1x " at the start
      name = name.replaceAll("^\\d+x\\s+", "").trim();
      // Remove quantity suffixes like " x64" at the end
      name = name.replaceAll("\\s+x\\d+$", "").trim();
      
      name = name.replaceAll("[⚚✪⭐✦]", "").trim();

      if (name.startsWith("SELL ")) name = name.substring(5).trim();
      else if (name.startsWith("BUY ")) name = name.substring(4).trim();

      if (name.startsWith("[Lvl")) {
         int closingBracket = name.indexOf(93);
         if (closingBracket != -1) return name.substring(closingBracket + 1).trim();
      }

      if (name.contains("Enchanted Book")) {
         ItemLore itemLore = itemStack.get(DataComponents.LORE);
         if (itemLore != null && itemLore.lines().size() > 0) {
            for (Component line : itemLore.lines()) {
               String cleanLine = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               if (cleanLine.isEmpty() || cleanLine.equalsIgnoreCase("Combinable in Anvil") || cleanLine.contains("view")) continue;
               return cleanLine.replaceAll("(?i)\\s+(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV|XV|[0-9]+)$", "").trim();
            }
         }
      }

      // Strip reforge from NBT if available
      CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
            String modifier = ea.getString("modifier").orElse("");
            if (!modifier.isEmpty()) {
               // Capitalize first letter of modifier for matching
               String reforgeName = modifier.substring(0, 1).toUpperCase() + modifier.substring(1).toLowerCase() + " ";
               if (name.startsWith(reforgeName)) {
                  name = name.substring(reforgeName.length()).trim();
               }
            }
         }
      }

      String skyblockId = getSkyblockId(itemStack).toUpperCase();
      if (!BazaarUtils.isBazaarItem(skyblockId)) {
          String[] reforges = new String[] { "Gentle ", "Odd ", "Fast ", "Fair ", "Epic ", "Sharp ", "Heroic ", "Spicy ",
                "Legendary ", "Dirty ", "Fabled ", "Suspicious ", "Gilded ", "Warped ", "Withered ", "Bulky ", "Stellar ",
                "Heated ", "Ambered ", "Fruitful ", "Magnetic ", "Fleet ", "Mithraic ", "Auspicious ", "Refined ",
                "Headstrong ", "Precise ", "Spiritual ", "Renowned ", "Giant ", "Submerged ", "Jaded ", "Loving ",
                "Necrotic ", "Ancient ", "Undead ", "Red ", "Snaded ", "Pitchin ", "Beady ", "Ridiculous ", "Unreal ",
                "Awkward ", "Rich ", "Fine ", "Neat ", "Hasty ", "Grand ", "Rapid ", "Deadly " };
          for (String reforge : reforges) {
             if (name.startsWith(reforge)) {
                name = name.substring(reforge.length());
                break;
             }
          }
      }

      return name.trim();
   }
}
