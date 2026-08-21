package me.bombo.bomboaddons.mixin;

import java.util.List;
import me.bombo.bomboaddons.BitsManager;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.ExperimentationTableHud;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.RomanNumber;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({ItemStack.class})
public abstract class ItemStackMixin {
   @Inject(
      method = {"getTooltipLines"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onGetTooltipLines(Item.TooltipContext context, Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
      List<Component> originalLines = (List)cir.getReturnValue();
      if (originalLines == null) return;
      List<Component> lines;
      if (originalLines instanceof java.util.ArrayList) {
         lines = originalLines;
      } else {
         lines = new java.util.ArrayList<>(originalLines);
         cir.setReturnValue(lines);
      }

      ItemStack currentStack = (ItemStack)(Object)this;
      me.bombo.bomboaddons.features.SupercraftHelper.appendTooltip(currentStack, lines);

      if (BomboConfig.get().lowestBin) {
         ItemStack stack = (ItemStack)(Object)this;
         String skyblockId = null;
         String rawId = SkyblockUtils.getInternalId(stack);
         if (rawId != null && !rawId.isEmpty()) {
            skyblockId = rawId;
         }

         if (skyblockId == null || skyblockId.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            Screen var10 = mc.screen;
            if (var10 instanceof AbstractContainerScreen) {
               AbstractContainerScreen<?> screen = (AbstractContainerScreen)var10;
               String title = screen.getTitle().getString();
               if (title.toLowerCase().contains("experimentation table rng")) {
                  String cleanName = stack.getHoverName().getString().replaceAll("(?i)§.", "").trim();
                  String possibleId = ExperimentationTableHud.getSkyblockIdFromName(cleanName);
                  if (!possibleId.isEmpty() && !possibleId.contains("GLASS_PANE") && !possibleId.equals("BARRIER")) {
                     skyblockId = possibleId;
                  }
               }
            }
         }

         if (skyblockId == null || skyblockId.isEmpty() || skyblockId.equals("SHARD")) {
            String hoverName = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            if (hoverName.toLowerCase().contains("shard")) {
               String clean = hoverName.toUpperCase().replace(" ", "_");
               if (!clean.startsWith("SHARD_")) {
                  if (clean.endsWith("_SHARD")) {
                     String var10000 = clean.substring(0, clean.length() - 6);
                     clean = "SHARD_" + var10000;
                  } else {
                     clean = "SHARD_" + clean;
                  }
               }

               skyblockId = clean;
            }
         }

         if (BomboConfig.get().debugMode) {
            Bomboaddons.sendMessage("§7[Debug] Skyblock ID found: §b" + skyblockId);
         }

         if (skyblockId == null || skyblockId.equals("ENCHANTED_BOOK")) {
            String hoverName = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
            if (stack.getItem().toString().contains("enchanted_book") && !hoverName.contains("Enchanted Book") && !hoverName.isEmpty()) {
               int lastSpace = hoverName.lastIndexOf(32);
               if (lastSpace != -1) {
                  String name = hoverName.substring(0, lastSpace).trim().toUpperCase().replace(" ", "_");
                  String tierStr = hoverName.substring(lastSpace + 1).trim();
                  int tier = RomanNumber.romanToDecimal(tierStr);
                  if (tier > 0) {
                     skyblockId = "ENCHANTMENT_" + name + "_" + tier;
                  }
               }
            }
         }

         if ((skyblockId == null || skyblockId.equals("ENCHANTED_BOOK")) && (stack.getItem().toString().contains("enchanted_book") || stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim().contains("Enchanted Book"))) {
            ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
               for(int i = 0; i < Math.min(lore.lines().size(), 10); ++i) {
                  String line = ((Component)lore.lines().get(i)).getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                  if (!line.isEmpty() && !line.equalsIgnoreCase("Rare Book!") && !line.equalsIgnoreCase("Super Rare Book!")) {
                     int lastSpace = line.lastIndexOf(32);
                     if (lastSpace != -1) {
                        String name = line.substring(0, lastSpace).trim().toUpperCase().replace(" ", "_");
                        String tierStr = line.substring(lastSpace + 1).trim();
                        int tier = RomanNumber.romanToDecimal(tierStr);
                        if (tier <= 0) {
                           try {
                              tier = Integer.parseInt(tierStr);
                           } catch (Exception var21) {
                           }
                        }

                        if (tier > 0) {
                           String baseId = name + "_" + tier;
                           if (LowestBinManager.getCachedPrice("ENCHANTMENT_" + baseId) > 0L) {
                              skyblockId = "ENCHANTMENT_" + baseId;
                           } else if (LowestBinManager.getCachedPrice("ENCHANTED_BOOK_" + baseId) > 0L) {
                              skyblockId = "ENCHANTED_BOOK_" + baseId;
                           } else if (LowestBinManager.getCachedPrice(baseId) > 0L) {
                              skyblockId = baseId;
                           } else {
                              skyblockId = baseId;
                           }
                           break;
                        }
                     }
                  }
               }
            }
         }

         if (skyblockId != null) {
            List<Component> lines = (List)cir.getReturnValue();
            int count = stack.getCount();
            if (stack.getHoverName().getString().contains("Collect Compost")) {
               ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
               if (lore != null) {
                  for(Component line : lore.lines()) {
                     String lineStr = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                     if (lineStr.contains("Compost Available: ")) {
                        String countStr = lineStr.substring(lineStr.indexOf("Compost Available: ") + 19).replaceAll("[^0-9]", "");
                        if (!countStr.isEmpty()) {
                           try {
                              count = Integer.parseInt(countStr);
                           } catch (NumberFormatException var20) {
                           }
                        }
                        break;
                     }
                  }
               }
            }

            boolean shouldShowLowestBin = BomboConfig.get().lowestBin;
            boolean isPet = skyblockId.startsWith("PET-") || skyblockId.contains(";");
            if (shouldShowLowestBin) {
               long price = (Long)LowestBinManager.getLowestBin(skyblockId).getNow(-1L);
               if (price > 0L) {
                  boolean isBz = LowestBinManager.isBazaar(skyblockId);
                  String label = isBz ? "§6BZ: " : "§6Lowest BIN: ";
                  String priceText = label + "§e" + LowestBinManager.formatPrice(price);
                  if (isPet) {
                     long lvlMaxPrice = -1L;
                     int maxLvl = !skyblockId.contains("GOLDEN_DRAGON") && !skyblockId.contains("JADE_DRAGON") && !skyblockId.contains("ROSE_DRAGON") ? 100 : 200;
                     if (skyblockId.startsWith("PET-") || skyblockId.contains(";")) {
                        lvlMaxPrice = (Long)LowestBinManager.getLowestBin(skyblockId + "-" + maxLvl).getNow(-1L);
                     }

                     if (lvlMaxPrice > 0L) {
                        priceText = priceText + " §7(" + LowestBinManager.formatPrice(lvlMaxPrice) + ")";
                     }
                  } else if (count > 1) {
                     priceText = priceText + " §7(" + LowestBinManager.formatPrice(price * (long)count) + ")";
                  }

                  lines.add(Component.literal(priceText));
               } else if (BomboConfig.get().debugMode) {
                  lines.add(Component.literal("§c[Debug] No BIN for: " + skyblockId));
               }
            }

            boolean shouldShowCraftCost = BomboConfig.get().craftCostTooltip;
            if (shouldShowCraftCost && !isPet) {
               long craftCost = LowestBinManager.getCraftCostCached(skyblockId);
               if (craftCost > 0L) {
                  String costText = "§6Raw Craft Cost: §e" + LowestBinManager.formatPrice(craftCost);
                  if (count > 1) {
                     costText = costText + " §7(" + LowestBinManager.formatPrice(craftCost * (long)count) + ")";
                  }

                  lines.add(Component.literal(costText));
               }
            }

            if (BomboConfig.get().npcPrice) {
               long npcPrice = LowestBinManager.getNpcPrice(skyblockId);
               if (npcPrice > 0L) {
                  String text = "§6NPC: §e" + LowestBinManager.formatPrice(npcPrice);
                  if (count > 1) {
                     text = text + " §7(" + LowestBinManager.formatPrice(npcPrice * (long)count) + ")";
                  }

                  lines.add(Component.literal(text));
               } else if (BomboConfig.get().debugMode) {
                  lines.add(Component.literal("§c[Debug] No NPC for: " + skyblockId));
               }
            }

            Integer bitCost = (Integer)BitsManager.bitCostCache.get(skyblockId);
            if (bitCost != null) {
               String bitsText = "§bBit Cost: " + LowestBinManager.formatPrice((long)bitCost) + " bits";
               if (count > 1) {
                  bitsText = bitsText + " §7(" + LowestBinManager.formatPrice((long)bitCost * (long)count) + " bits)";
               }

               lines.add(Component.literal(bitsText));
            }
         }

      }
   }
}
