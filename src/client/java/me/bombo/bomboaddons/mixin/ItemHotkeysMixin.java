package me.bombo.bomboaddons.mixin;

import java.util.List;
import me.bombo.bomboaddons.BazaarUtils;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboConfigGUI;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.ItemListOverlay;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.RecipeViewerScreen;
import me.bombo.bomboaddons.SBECommands;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({AbstractContainerScreen.class})
public abstract class ItemHotkeysMixin {
   @Shadow
   protected Slot hoveredSlot;

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (!BomboConfigGUI.isTypingOrListening()) {
         if (ItemListOverlay.searchBox == null || !ItemListOverlay.searchBox.isFocused()) {
            ItemStack targetItem = null;
            if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
               targetItem = this.hoveredSlot.getItem();
            } else if (ItemListOverlay.hoveredStack != null) {
               targetItem = ItemListOverlay.hoveredStack;
            }

            if (targetItem != null) {
               int pressedKey = event.key();
               BomboConfig.Settings s = BomboConfig.get();

               // Check key combination matches (prioritizing tradeMaxKey over tradeKey if both match)
               if (CustomBindsProcessor.matchesKey(s.textureToggleKey, pressedKey)) {
                  String sbId = me.bombo.bomboaddons.features.TextureToggleManager.INSTANCE.skyblockId(targetItem);
                  if (sbId != null) {
                     me.bombo.bomboaddons.features.TextureToggleManager.INSTANCE.toggleItem(sbId);
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.tradeMaxKey, pressedKey)) {
                  if (this.handleKey(targetItem, "TRADE_MAX")) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.tradeKey, pressedKey)) {
                  if (this.handleKey(targetItem, "TRADE")) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.recipeKey, pressedKey)) {
                  if (this.handleKey(targetItem, "RECIPE")) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.viewRecipeKey, pressedKey)) {
                  if (this.handleKey(targetItem, "VIEWRECIPE_CMD")) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.recipeCmdKey, pressedKey)) {
                  if (this.handleKey(targetItem, "RECIPE_CMD")) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.usageKey, pressedKey)) {
                  if (this.handleKey(targetItem, "USAGE")) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.showItemKey, pressedKey)) {
                  this.showItemInfo(targetItem);
                  cir.setReturnValue(true);
               } else if (CustomBindsProcessor.matchesKey(s.countItemKey, pressedKey)) {
                  this.countAndDisplayItems(targetItem);
                  cir.setReturnValue(true);
               } else if (CustomBindsProcessor.matchesKey(s.copyNbtKey, pressedKey)) {
                  this.copyItemNbt(targetItem);
                  cir.setReturnValue(true);
               } else if (CustomBindsProcessor.matchesKey(s.gfsMaxKey, pressedKey)) {
                  this.handleGFS(targetItem, true);
                  cir.setReturnValue(true);
               } else if (CustomBindsProcessor.matchesKey(s.gfsStackKey, pressedKey)) {
                  this.handleGFS(targetItem, false);
                  cir.setReturnValue(true);
               } else if (CustomBindsProcessor.matchesKey(s.bestiaryHighlightKey, pressedKey)) {
                  if (me.bombo.bomboaddons.features.BestiaryManager.handleBestiaryKey(targetItem, this.hoveredSlot, (AbstractContainerScreen<?>)(Object)this)) {
                     cir.setReturnValue(true);
                  }
               } else if (CustomBindsProcessor.matchesKey(s.priceHistoryKey, pressedKey)) {
                  Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.gui.PriceHistoryScreen((AbstractContainerScreen<?>)(Object)this, targetItem));
                  cir.setReturnValue(true);
               }
            }

         }
      }
   }

   private void showItemInfo(ItemStack itemStack) {
      if (itemStack == null || itemStack.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      // Send item's original display name with true color formatting
      mc.player.sendSystemMessage(itemStack.getHoverName());

      CustomData customData = (CustomData)itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
            String eaString = ea.toString();
            HoverEvent hover = SBECommands.createHoverEvent(eaString);
            Style style = Style.EMPTY;
            if (hover != null) {
               style = style.withHoverEvent(hover);
            }

            mc.player.sendSystemMessage(Component.literal("§eExtraAttributes: §7(Hover for JSON)").withStyle(style));
            String eaId = ea.getString("id").orElse("");
            if (!eaId.isEmpty()) {
               mc.player.sendSystemMessage(Component.literal("§7Internal ID: §f" + eaId));
            }

            String modifier = ea.getString("modifier").orElse("");
            if (!modifier.isEmpty()) {
               mc.player.sendSystemMessage(Component.literal("§7Modifier: §d" + modifier));
            }
         }
      }

      // Display ALL tooltip lines (including those added by BomboAddons, NEU, SkyHanni, etc.)
      try {
         List<Component> tooltip = itemStack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
         if (tooltip != null && tooltip.size() > 1) {
            for (int i = 1; i < tooltip.size(); i++) {
               mc.player.sendSystemMessage(tooltip.get(i));
            }
         }
      } catch (Throwable t) {
         ItemLore itemLore = (ItemLore)itemStack.get(DataComponents.LORE);
         if (itemLore != null) {
            for (Component line : itemLore.lines()) {
               mc.player.sendSystemMessage(line);
            }
         }
      }
   }

   private void handleGFS(ItemStack itemStack, boolean max) {
      String skyblockId = this.getSkyblockId(itemStack);
      if (skyblockId != null && !skyblockId.isEmpty()) {
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
   }

   private String getSkyblockId(ItemStack itemStack) {
      CustomData customData = (CustomData)itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         String id = tag.getString("id").orElse("");
         if (!id.isEmpty()) {
            return id;
         }

         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
            return ea.getString("id").orElse("");
         }
      }

      return itemStack.getItem().toString().toUpperCase();
   }

   private void copyItemNbt(ItemStack itemStack) {
      CustomData customData = (CustomData)itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         String nbtString = tag.toString();
         Minecraft.getInstance().keyboardHandler.setClipboard(nbtString);
         Minecraft.getInstance().player.sendSystemMessage(Component.literal("§6Item NBT copied to clipboard."));
      } else {
         Minecraft.getInstance().player.sendSystemMessage(Component.literal("§cNo NBT data found for this item."));
      }

   }

   private boolean handleKey(ItemStack itemStack, String action) {
      String skyblockId = this.getSkyblockId(itemStack);
      if (skyblockId == null || skyblockId.isEmpty()) {
         skyblockId = itemStack.getItem().toString().toUpperCase();
      }
      String internalId = SkyblockUtils.getInternalId(itemStack);
      if (internalId == null || internalId.isEmpty()) {
         internalId = skyblockId;
      }

      if (skyblockId != null) {
         if (action.equalsIgnoreCase("VIEWRECIPE_CMD")) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
               Minecraft.getInstance().player.connection.sendCommand("viewrecipe " + skyblockId);
               return true;
            }
         }
         if (action.equalsIgnoreCase("RECIPE_CMD")) {
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
               String cleanName = this.cleanName(itemStack);
               Minecraft.getInstance().player.connection.sendCommand("recipe " + cleanName);
               return true;
            }
         }

         if (action.equalsIgnoreCase("RECIPE") || action.equalsIgnoreCase("USAGE")) {
            String mode = BomboConfig.get().recipeHotkeyAction != null ? BomboConfig.get().recipeHotkeyAction.trim().toUpperCase(java.util.Locale.ROOT) : "GUI";
            if (action.equalsIgnoreCase("RECIPE") && (mode.contains("VIEWRECIPE") || mode.contains("VIEW_RECIPE"))) {
               if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
                  Minecraft.getInstance().player.connection.sendCommand("viewrecipe " + skyblockId);
                  return true;
               }
            } else if (action.equalsIgnoreCase("RECIPE") && mode.contains("RECIPE") && !mode.equals("GUI")) {
               if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.connection != null) {
                  String cleanName = this.cleanName(itemStack);
                  Minecraft.getInstance().player.connection.sendCommand("recipe " + cleanName);
                  return true;
               }
            }
            RecipeViewerScreen rvs = new RecipeViewerScreen(skyblockId, Minecraft.getInstance().gui.screen());
            if (action.equalsIgnoreCase("USAGE")) {
               rvs.setUsageMode(true);
            }
            Minecraft.getInstance().setScreenAndShow(rvs);
            return true;
         }

         if (action.equalsIgnoreCase("TRADE") || action.equalsIgnoreCase("TRADE_MAX")) {
            String title = Minecraft.getInstance().gui.screen() != null ? Minecraft.getInstance().gui.screen().getTitle().getString() : "";
            String cleanTitle = net.minecraft.ChatFormatting.stripFormatting(title).toLowerCase(java.util.Locale.ROOT);
            String cleanName = this.cleanName(itemStack);
            if (cleanTitle.contains("hunting box") || title.contains("Hunting Box")) {
               Minecraft.getInstance().player.connection.sendCommand("bz " + cleanName + " Shard");
               return true;
            }

            if ((cleanTitle.contains("composter") || title.contains("Composter")) && cleanName.contains("Collect Compost")) {
               Minecraft.getInstance().player.connection.sendCommand("bz compost");
               return true;
            }

            if (cleanTitle.contains("attribute menu") || title.contains("Attribute Menu")) {
               ItemLore itemLore = (ItemLore)itemStack.get(DataComponents.LORE);
               if (itemLore != null) {
                  for(Component line : itemLore.lines()) {
                     String lineStr = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                     if (lineStr.startsWith("Source: ")) {
                        String sourceItem = lineStr.substring(8).replaceAll("\\([A-Za-z0-9]+\\)", "").trim();
                        Minecraft.getInstance().player.connection.sendCommand("bz " + sourceItem);
                        return true;
                     }
                  }
               }
            }

            // Check if inside Bazaar Orders GUI (Your Bazaar Orders, Co-op Bazaar Orders)
            if (cleanTitle.contains("bazaar orders") || cleanTitle.contains("bazar orders")) {
               Minecraft.getInstance().player.connection.sendCommand("bz " + cleanName.toLowerCase(java.util.Locale.ROOT));
               return true;
            }

            // Check if inside Hoppity's Collection / Chocolate Factory GUI, or if item is a rabbit/faction item
            boolean isHoppityGui = cleanTitle.contains("hoppity") || cleanTitle.contains("chocolate factory") || cleanTitle.contains("rabbit shop");
            boolean isFish = skyblockId.equals("RABBIT_THE_FISH") || skyblockId.endsWith("_THE_FISH");
            boolean isRabbitItem = !isFish && (skyblockId.startsWith("FACTION_RABBIT") || skyblockId.startsWith("HOPPITY_"));
            if (!isFish && !isRabbitItem && !skyblockId.equals("RAW_RABBIT") && !skyblockId.equals("COOKED_RABBIT") && !skyblockId.equals("RABBIT_FOOT") && !skyblockId.equals("RABBIT_HIDE")) {
               ItemLore itemLore = (ItemLore)itemStack.get(DataComponents.LORE);
               if (itemLore != null) {
                  for(Component line : itemLore.lines()) {
                     String lineStr = net.minecraft.ChatFormatting.stripFormatting(line.getString()).toLowerCase(java.util.Locale.ROOT);
                     if (lineStr.contains("chocolate factory") || lineStr.contains("chocolate per second") || lineStr.contains("hotspot") || lineStr.contains("faction rabbit") || lineStr.contains("right click to add to your factory")) {
                        isRabbitItem = true;
                        break;
                     }
                  }
               }
            }

            if (!isFish && (isHoppityGui || isRabbitItem)) {
               String bzTarget = cleanName.replaceAll("(?i)\\s+rabbit$", "").trim();
               if (bzTarget.isEmpty()) bzTarget = cleanName;
               Minecraft.getInstance().player.connection.sendCommand("bz " + bzTarget.toLowerCase(java.util.Locale.ROOT));
               return true;
            }

            // Check if item is a Pet
            boolean isPet = cleanName.startsWith("] ") || skyblockId.equals("PET") || skyblockId.startsWith("PET_");
            if (!isPet) {
               CustomData cd = (CustomData)itemStack.get(DataComponents.CUSTOM_DATA);
               if (cd != null) {
                  CompoundTag tag = cd.copyTag();
                  CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                  if (ea != null && ea.contains("petInfo")) {
                     isPet = true;
                  }
               }
            }
            if (isPet) {
               String petTarget = cleanName;
               if (petTarget.startsWith("] ")) {
                  petTarget = petTarget.substring(2).trim();
               }
               if (action.equalsIgnoreCase("TRADE_MAX")) {
                  String upperName = petTarget.toUpperCase(java.util.Locale.ROOT);
                  String upperId = skyblockId.toUpperCase(java.util.Locale.ROOT);
                  boolean isLvl200 = upperName.contains("GOLDEN DRAGON") || upperName.contains("ROSE DRAGON") || upperName.contains("JADE DRAGON")
                          || upperId.contains("GOLDEN_DRAGON") || upperId.contains("ROSE_DRAGON") || upperId.contains("JADE_DRAGON");
                  int maxLvl = isLvl200 ? 200 : 100;
                  Minecraft.getInstance().player.connection.sendCommand("ahs " + maxLvl + "] " + petTarget);
               } else {
                  Minecraft.getInstance().player.connection.sendCommand("ahs ] " + petTarget);
               }
               return true;
            }

            // Check if item is an Enchanted Book
            if (skyblockId.contains("ENCHANTED_BOOK") || cleanName.contains("Enchanted Book") || internalId.startsWith("ENCHANTMENT_")) {
               String enchantName = extractEnchantName(itemStack);
               if (enchantName != null && !enchantName.isEmpty()) {
                  Minecraft.getInstance().player.connection.sendCommand("bz " + enchantName.toLowerCase(java.util.Locale.ROOT));
                  return true;
               }
            }

            me.bombo.bomboaddons.features.ItemTradeManager.TradeEntry tradeEntry = me.bombo.bomboaddons.features.ItemTradeManager.lookup(internalId, cleanName);
            if (tradeEntry == null && !skyblockId.equals(internalId)) {
               tradeEntry = me.bombo.bomboaddons.features.ItemTradeManager.lookup(skyblockId, cleanName);
            }
            boolean isBazaar = false;
            String searchTarget = cleanName;

            if (tradeEntry != null) {
               isBazaar = tradeEntry.isBazaar;
               if (tradeEntry.name != null && !tradeEntry.name.equalsIgnoreCase("null") && !tradeEntry.name.trim().isEmpty() && !tradeEntry.name.equalsIgnoreCase("PET")) {
                  searchTarget = tradeEntry.name.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               }
            } else {
               isBazaar = BazaarUtils.isBazaarItem(internalId) || BazaarUtils.isBazaarItem(skyblockId) || LowestBinManager.isBazaar(internalId) || LowestBinManager.isBazaar(skyblockId);
            }

            if (isBazaar) {
               Minecraft.getInstance().player.connection.sendCommand("bz " + searchTarget.toLowerCase(java.util.Locale.ROOT));
            } else {
               Minecraft.getInstance().player.connection.sendCommand("ahs " + searchTarget);
            }

            return true;
         }
      }

      return false;
   }

   private String extractEnchantName(ItemStack itemStack) {
      String internalId = SkyblockUtils.getInternalId(itemStack);
      ItemLore itemLore = (ItemLore)itemStack.get(DataComponents.LORE);
      if (itemLore != null && itemLore.lines().size() > 0) {
         for (Component line : itemLore.lines()) {
            String cleanLine = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            if (!cleanLine.isEmpty() && !cleanLine.equalsIgnoreCase("Combinable in Anvil") && !cleanLine.toLowerCase(java.util.Locale.ROOT).contains("view recipes") && !cleanLine.toLowerCase(java.util.Locale.ROOT).contains("click to inspect")) {
               return cleanLine;
            }
         }
      }
      if (internalId.startsWith("ENCHANTMENT_")) {
         String enchantName = internalId.replace("ENCHANTMENT_", "");
         String[] parts = enchantName.split("_");
         if (parts.length >= 2) {
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; ++i) {
               if (i > 0) nameBuilder.append(" ");
               String word = parts[i];
               if (!word.isEmpty()) {
                  nameBuilder.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
               }
            }
            String levelStr = "";
            try {
               levelStr = this.toRoman(Integer.parseInt(parts[parts.length - 1]));
            } catch (NumberFormatException var8) {
               levelStr = parts[parts.length - 1];
            }
            return nameBuilder.toString() + " " + levelStr;
         }
      }
      return null;
   }

   private String cleanName(ItemStack itemStack) {
      String originalName = itemStack.getHoverName().getString();
      String name = originalName.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
      name = name.replaceAll("^\\d+x\\s+", "").trim();
      name = name.replaceAll("\\s+x\\d+$", "").trim();
      name = name.replaceAll("[⚚✪⭐✦]", "").trim();
      if (name.startsWith("SELL ")) {
         name = name.substring(5).trim();
      } else if (name.startsWith("BUY ")) {
         name = name.substring(4).trim();
      }

      if (name.contains("[Lvl") || name.startsWith("[Lvl")) {
         int lastBracket = name.lastIndexOf(93); // ']'
         if (lastBracket != -1) {
            String petName = name.substring(lastBracket + 1).trim();
            petName = petName.replaceAll("[⚚✪⭐✦]", "").trim();
            if (petName.contains("(")) {
               petName = petName.substring(0, petName.indexOf(40)).trim();
            }

            return "] " + petName;
         }
      }

      if (name.contains("Enchanted Book")) {
         ItemLore itemLore = (ItemLore)itemStack.get(DataComponents.LORE);
         if (itemLore != null && itemLore.lines().size() > 0) {
            for(Component line : itemLore.lines()) {
               String cleanLine = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               if (!cleanLine.isEmpty() && !cleanLine.equalsIgnoreCase("Combinable in Anvil") && !cleanLine.contains("view")) {
                  return cleanLine.replaceAll("(?i)\\s+(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV|XV|[0-9]+)$", "").trim();
               }
            }
         }
      }

      CustomData customData = (CustomData)itemStack.get(DataComponents.CUSTOM_DATA);
      if (customData != null) {
         CompoundTag tag = customData.copyTag();
         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
            String modifier = ea.getString("modifier").orElse("");
            if (!modifier.isEmpty()) {
               String var10000 = modifier.substring(0, 1).toUpperCase();
               String reforgeName = var10000 + modifier.substring(1).toLowerCase() + " ";
               if (name.startsWith(reforgeName)) {
                  name = name.substring(reforgeName.length()).trim();
               }
            }
         }
      }

      String skyblockId = this.getSkyblockId(itemStack).toUpperCase();
      if (!BazaarUtils.isBazaarItem(skyblockId)) {
         String[] reforges = new String[]{"Gentle ", "Odd ", "Fast ", "Fair ", "Epic ", "Sharp ", "Heroic ", "Spicy ", "Legendary ", "Dirty ", "Fabled ", "Suspicious ", "Gilded ", "Warped ", "Withered ", "Bulky ", "Stellar ", "Heated ", "Ambered ", "Fruitful ", "Magnetic ", "Fleet ", "Mithraic ", "Auspicious ", "Refined ", "Headstrong ", "Precise ", "Spiritual ", "Renowned ", "Giant ", "Submerged ", "Jaded ", "Loving ", "Necrotic ", "Ancient ", "Undead ", "Red ", "Snaded ", "Pitchin ", "Beady ", "Ridiculous ", "Unreal ", "Awkward ", "Rich ", "Fine ", "Neat ", "Hasty ", "Grand ", "Rapid ", "Deadly "};

         for(String reforge : reforges) {
            if (name.startsWith(reforge)) {
               name = name.substring(reforge.length());
               break;
            }
         }
      }

      return name.trim();
   }

   private String getMatchingKey(ItemStack stack) {
      String id = SkyblockUtils.getInternalId(stack);
      return id != null && !id.isEmpty() ? id : stack.getItem().toString();
   }

   private String toRoman(int num) {
      int[] values = new int[]{1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
      String[] romanLetters = new String[]{"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
      StringBuilder roman = new StringBuilder();

      for(int i = 0; i < values.length; ++i) {
         while(num >= values[i]) {
            num -= values[i];
            roman.append(romanLetters[i]);
         }
      }

      return roman.toString();
   }

   private String getEnchantedBookDisplay(ItemStack stack) {
      String id = SkyblockUtils.getInternalId(stack);
      if (id.startsWith("ENCHANTMENT_")) {
         String enchantName = id.replace("ENCHANTMENT_", "");
         String[] parts = enchantName.split("_");
         if (parts.length >= 2) {
            StringBuilder nameBuilder = new StringBuilder();

            for(int i = 0; i < parts.length - 1; ++i) {
               if (i > 0) {
                  nameBuilder.append(" ");
               }

               String word = parts[i];
               if (!word.isEmpty()) {
                  nameBuilder.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase());
               }
            }

            String levelStr = "";

            try {
               levelStr = this.toRoman(Integer.parseInt(parts[parts.length - 1]));
            } catch (NumberFormatException var8) {
               levelStr = parts[parts.length - 1];
            }

            String var10000 = nameBuilder.toString();
            return "Enchanted Book (" + var10000 + " " + levelStr + ")";
         }
      }

      return "Enchanted Book";
   }

   private void countAndDisplayItems(ItemStack hoveredStack) {
      String targetKey = this.getMatchingKey(hoveredStack);
      String displayName = hoveredStack.getHoverName().getString();
      if (targetKey.startsWith("ENCHANTMENT_")) {
         displayName = this.getEnchantedBookDisplay(hoveredStack);
      }

      int inventoryCount = 0;
      int guiCount = 0;
      AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
      AbstractContainerMenu menu = screen.getMenu();

      for(Slot slot : menu.slots) {
         if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            if (this.getMatchingKey(stack).equals(targetKey)) {
               if (slot.container instanceof Inventory) {
                  inventoryCount += stack.getCount();
               } else {
                  guiCount += stack.getCount();
               }
            }
         }
      }

      String cleanName = displayName.replaceAll("(?i)§.", "").trim();
      Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §f" + cleanName + " " + inventoryCount + " (" + guiCount + ")"));
   }
}
