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
      method = {"hasFoil"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHasFoil(CallbackInfoReturnable<Boolean> cir) {
      ItemStack currentStack = (ItemStack)(Object)this;
      try {
         String uuid = me.bombo.bomboaddons.ItemCustomizeScreen.extractItemUuid(currentStack);
         String skyId = SkyblockUtils.getInternalId(currentStack);
         String regId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(currentStack.getItem()).toString();
         BomboConfig.CustomItemOverride override = null;
         if (uuid != null && !uuid.isEmpty()) {
            override = BomboConfig.get().customItemOverrides.get(uuid);
         } else if (skyId != null && !skyId.isEmpty()) {
            override = BomboConfig.get().customItemOverrides.get(skyId);
         } else if (regId != null && !regId.isEmpty()) {
            override = BomboConfig.get().customItemOverrides.get(regId);
         }
         if (override != null && override.enchanted != null) {
            cir.setReturnValue(override.enchanted);
         }
      } catch (Throwable ignored) {}
   }

   @Inject(
      method = {"getTooltipLines"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void onGetTooltipLines(Item.TooltipContext context, Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
      ItemStack currentStack = (ItemStack)(Object)this;
      if (BomboConfig.get().storagePreview && me.bombo.bomboaddons.StoragePreviewManager.isPreviewActive(currentStack)) {
         cir.setReturnValue(java.util.Collections.emptyList());
         return;
      }
      List<Component> originalLines = (List)cir.getReturnValue();
      if (originalLines == null) return;
      List<Component> lines = new java.util.ArrayList<>(originalLines);
      cir.setReturnValue(lines);

      try {
         String uuid = me.bombo.bomboaddons.ItemCustomizeScreen.extractItemUuid(currentStack);
         String skyId = SkyblockUtils.getInternalId(currentStack);
         String regId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(currentStack.getItem()).toString();
         BomboConfig.CustomItemOverride override = null;
         if (uuid != null && !uuid.isEmpty()) {
            override = BomboConfig.get().customItemOverrides.get(uuid);
         } else if (skyId != null && !skyId.isEmpty()) {
            override = BomboConfig.get().customItemOverrides.get(skyId);
         } else if (regId != null && !regId.isEmpty()) {
            override = BomboConfig.get().customItemOverrides.get(regId);
         }

         if (override != null) {
            if (override.name != null && !override.name.trim().isEmpty() && !lines.isEmpty()) {
               lines.set(0, Component.literal(override.name.replace('&', '§')));
            }
            if (override.lore != null && !override.lore.trim().isEmpty()) {
               if (lines.size() > 1) {
                  lines.subList(1, lines.size()).clear();
               }
               String[] loreLines = override.lore.split("\\\\n|\\r?\\n");
               for (String ll : loreLines) {
                  lines.add(Component.literal(ll.replace('&', '§')));
               }
            }
         }
      } catch (Throwable ignored) {}

      try {
         lines = me.bombo.bomboaddons.features.SupercraftHelper.appendTooltip(currentStack, lines);
         cir.setReturnValue(lines);
      } catch (Throwable ignored) {}

      BomboConfig.Settings s = BomboConfig.get();
      if (s.lowestBin || s.showEstimatedValue || s.craftCostTooltip || s.npcPrice || s.showBazaarBuySell || s.showAvgLowestBin7d || s.showAvgLowestBin30d) {
         ItemStack stack = (ItemStack)(Object)this;
         String skyblockId = null;
         String rawId = SkyblockUtils.getInternalId(stack);
         if (rawId != null && !rawId.isEmpty()) {
            skyblockId = rawId;
         }

         if (skyblockId == null || skyblockId.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            Screen var10 = mc.gui.screen();
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

         // Faction Rabbits resolution (e.g. Avalanche -> FACTION_RABBIT_AVALANCHE, Nectar -> FACTION_RABBIT_NECTAR)
         if (skyblockId == null || skyblockId.isEmpty() || skyblockId.equals("FACTION_RABBIT")) {
            net.minecraft.world.item.component.CustomData customData = (net.minecraft.world.item.component.CustomData)stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null) {
               net.minecraft.nbt.CompoundTag tag = customData.copyTag();
               net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
               String rId = ea != null ? ea.getString("faction_rabbit_id").orElse("") : "";
               if (rId.isEmpty()) rId = tag.getString("faction_rabbit_id").orElse("");
               if (!rId.isEmpty()) {
                  skyblockId = "FACTION_RABBIT_" + rId.toUpperCase(java.util.Locale.ROOT);
               }
            }
            if (skyblockId == null || skyblockId.isEmpty() || skyblockId.equals("FACTION_RABBIT")) {
               String hoverName = stack.getHoverName().getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
               ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
               boolean isRabbit = false;
               if (lore != null && !lore.lines().isEmpty()) {
                  for (Component l : lore.lines()) {
                     String lStr = l.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim().toLowerCase(java.util.Locale.ROOT);
                     if (lStr.contains("rabbit") || lStr.contains("chocolate factory") || lStr.contains("chocolate per second") || lStr.contains("found this rabbit") || lStr.contains("faction rabbit")) {
                        isRabbit = true;
                        break;
                     }
                  }
               }
               if (!isRabbit) {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
                     String title = screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT);
                     if (title.contains("hoppity") || title.contains("chocolate factory")) {
                        isRabbit = true;
                     }
                  }
               }
               if (isRabbit && !hoverName.isEmpty() && !hoverName.equalsIgnoreCase("Barrier") && !hoverName.contains("Glass Pane")) {
                  String rabbitId = "FACTION_RABBIT_" + hoverName.toUpperCase(java.util.Locale.ROOT).replace(" ", "_");
                  if (LowestBinManager.getCachedPrice(rabbitId) > 0L || LowestBinManager.isBazaar(rabbitId)) {
                     skyblockId = rabbitId;
                  }
               }
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

            List<me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition> priceAdditions = new java.util.ArrayList<>();
            boolean shouldShowLowestBin = s.lowestBin;
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

                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("lowestBin", Component.literal(priceText), s.getLorePos("lowestBin", "BOTTOM"), s.getLoreOrder("lowestBin", 9)));
               } else if (s.debugMode) {
                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("lowestBin", Component.literal("§c[Debug] No BIN for: " + skyblockId), s.getLorePos("lowestBin", "BOTTOM"), s.getLoreOrder("lowestBin", 9)));
               }
            }

            boolean shouldShowCraftCost = s.craftCostTooltip;
            if (shouldShowCraftCost && !isPet) {
               long craftCost = LowestBinManager.getCraftCostCached(skyblockId);
               if (craftCost > 0L) {
                  String costText = "§6Raw Craft Cost: §e" + LowestBinManager.formatPrice(craftCost);
                  if (count > 1) {
                     costText = costText + " §7(" + LowestBinManager.formatPrice(craftCost * (long)count) + ")";
                  }

                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("craftCost", Component.literal(costText), s.getLorePos("craftCost", "BOTTOM"), s.getLoreOrder("craftCost", 10)));
               }
            }

            if (s.npcPrice) {
               long npcPrice = LowestBinManager.getNpcPrice(skyblockId);
               if (npcPrice > 0L) {
                  String text = "§6NPC: §e" + LowestBinManager.formatPrice(npcPrice);
                  if (count > 1) {
                     text = text + " §7(" + LowestBinManager.formatPrice(npcPrice * (long)count) + ")";
                  }

                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("npcPrice", Component.literal(text), s.getLorePos("npcPrice", "BOTTOM"), s.getLoreOrder("npcPrice", 11)));
               } else if (s.debugMode) {
                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("npcPrice", Component.literal("§c[Debug] No NPC for: " + skyblockId), s.getLorePos("npcPrice", "BOTTOM"), s.getLoreOrder("npcPrice", 11)));
               }
            }

            Integer bitCost = (Integer)BitsManager.bitCostCache.get(skyblockId);
            if (bitCost != null) {
               String bitsText = "§bBit Cost: " + LowestBinManager.formatPrice((long)bitCost) + " bits";
               if (count > 1) {
                  bitsText = bitsText + " §7(" + LowestBinManager.formatPrice((long)bitCost * (long)count) + " bits)";
               }

               priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("bitCost", Component.literal(bitsText), s.getLorePos("bitCost", "BOTTOM"), s.getLoreOrder("bitCost", 12)));
            }

            if (s.showEstimatedValue) {
               LowestBinManager.ItemValueBreakdown breakdown = LowestBinManager.calculateDetailedEstimatedValue((ItemStack)(Object)this, skyblockId);
               if (breakdown.totalPrice > 0L) {
                  String estText = "§6Estimated Value: §e" + LowestBinManager.formatPrice(breakdown.totalPrice);
                  if (count > 1) {
                     estText = estText + " §7(" + LowestBinManager.formatPrice(breakdown.totalPrice * (long)count) + ")";
                  }
                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("estimatedValue", Component.literal(estText), s.getLorePos("estimatedValue", "BOTTOM"), s.getLoreOrder("estimatedValue", 13)));

                  if (s.estimatedValueFullBreakdown) {
                     if (breakdown.baseItem != null) {
                        priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("est_base", Component.literal("  §8▪ §7Base (" + breakdown.baseItem.source + "): §e" + LowestBinManager.formatPrice(breakdown.baseItem.price)), s.getLorePos("estimatedValue", "BOTTOM"), s.getLoreOrder("estimatedValue", 13) + 1));
                     }
                     int subOrder = s.getLoreOrder("estimatedValue", 13) + 2;
                     for (LowestBinManager.ValueEntry upg : breakdown.upgrades) {
                        priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("est_upg_" + subOrder, Component.literal("  §8▪ §d" + upg.label + ": §e" + LowestBinManager.formatPrice(upg.price)), s.getLorePos("estimatedValue", "BOTTOM"), subOrder++));
                     }
                     for (LowestBinManager.ValueEntry gem : breakdown.gemstones) {
                        priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("est_gem_" + subOrder, Component.literal("  §8▪ §b" + gem.label + ": §e" + LowestBinManager.formatPrice(gem.price)), s.getLorePos("estimatedValue", "BOTTOM"), subOrder++));
                     }
                     for (LowestBinManager.ValueEntry enc : breakdown.enchants) {
                        priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("est_enc_" + subOrder, Component.literal("  §8▪ §9" + enc.label + ": §e" + LowestBinManager.formatPrice(enc.price)), s.getLorePos("estimatedValue", "BOTTOM"), subOrder++));
                     }
                  }
               }
            }
            if (s.showBazaarBuySell && LowestBinManager.isBazaar(skyblockId)) {
               long buy = LowestBinManager.getBuyPrice(skyblockId);
               long sell = LowestBinManager.getSellPrice(skyblockId);
               if (buy > 0L || sell > 0L) {
                  String bzText = "§6BZ: §a" + LowestBinManager.formatPrice(buy) + " §7/ §c" + LowestBinManager.formatPrice(sell);
                  if (count > 1) {
                     bzText += " §7(" + LowestBinManager.formatPrice(buy * (long)count) + " / " + LowestBinManager.formatPrice(sell * (long)count) + ")";
                  }
                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("bazaarBuySell", Component.literal(bzText), s.getLorePos("bazaarBuySell", "BOTTOM"), s.getLoreOrder("bazaarBuySell", 9) + 1));
               }
            }

            if (s.showAvgLowestBin7d || s.showAvgLowestBin30d) {
               if (!me.bombo.bomboaddons.features.PriceHistoryManager.isNonMarketItem(skyblockId)) {
                  if (LowestBinManager.getCachedPrice(skyblockId) > 0 || LowestBinManager.isBazaar(skyblockId)) {
                     me.bombo.bomboaddons.features.PriceHistoryManager.ItemHistoryData hist = me.bombo.bomboaddons.features.PriceHistoryManager.getCachedHistory(skyblockId);
                     if (hist == null) {
                        me.bombo.bomboaddons.features.PriceHistoryManager.fetchHistory(skyblockId, "30d");
                     } else {
                        if (s.showAvgLowestBin7d && hist.avg7d > 0) {
                           String avg7Text = "§6Avg 7d BIN: §b" + LowestBinManager.formatPrice(Math.round(hist.avg7d));
                           priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("avgLowestBin7d", Component.literal(avg7Text), s.getLorePos("avgLowestBin7d", "BOTTOM"), s.getLoreOrder("avgLowestBin7d", 14)));
                        }
                        if (s.showAvgLowestBin30d && hist.avg30d > 0) {
                           String avg30Text = "§6Avg 30d BIN: §b" + LowestBinManager.formatPrice(Math.round(hist.avg30d));
                           priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("avgLowestBin30d", Component.literal(avg30Text), s.getLorePos("avgLowestBin30d", "BOTTOM"), s.getLoreOrder("avgLowestBin30d", 15)));
                        }
                     }
                  }
               }
            }

            try {
               if (s.gardenBlockSlotsWhileFarming && me.bombo.bomboaddons.GardenMovement.isActive() && me.bombo.bomboaddons.SkyblockUtils.isInGarden()) {
                  priceAdditions.add(new me.bombo.bomboaddons.features.SupercraftHelper.LoreAddition("gardenBlocked", Component.literal("§cBlocked by Garden Movement"), "BOTTOM", 999));
               }

               lines = me.bombo.bomboaddons.features.SupercraftHelper.applyAdditionsToLines(lines, priceAdditions);
               cir.setReturnValue(lines);
            } catch (Throwable ignored) {}
         }
      }

      // No Obfuscate: scrub the obfuscated (§k) style from every tooltip line, including the
      // item name. Applied after our own additions so lines we inserted are covered too, and
      // before the search highlight so matched text stays visible.
      if (me.bombo.bomboaddons.util.NoObfuscate.isEnabled()) {
         try {
            me.bombo.bomboaddons.util.NoObfuscate.stripAll(lines);
         } catch (Throwable ignored) {
         }
      }

      // Highlight matching query text in lore only during focus mode (double click / inventorySearchMode) and only for player/container items
      try {
         if (me.bombo.bomboaddons.ItemListOverlay.inventorySearchMode && (Object)this != me.bombo.bomboaddons.ItemListOverlay.hoveredStack) {
            String searchQuery = me.bombo.bomboaddons.ItemListOverlay.query;
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
               String cleanQuery = searchQuery.trim().toLowerCase(java.util.Locale.ROOT);
               for (int i = 0; i < lines.size(); i++) {
                  Component lineComp = lines.get(i);
                  if (lineComp == null) continue;
                  String rawLine = lineComp.getString();
                  if (rawLine.toLowerCase(java.util.Locale.ROOT).contains(cleanQuery)) {
                     Component highlighted = highlightComponent(lineComp, cleanQuery);
                     lines.set(i, highlighted);
                  }
               }
            }
         }
      } catch (Throwable ignored) {}
   }

   private Component highlightComponent(Component original, String query) {
      if (original == null || query == null || query.isEmpty()) return original;
      String plain = original.getString();
      if (!plain.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) {
         return original;
      }
      net.minecraft.network.chat.MutableComponent result = Component.empty();
      original.visit((style, text) -> {
         if (text.isEmpty()) return java.util.Optional.empty();
         String lower = text.toLowerCase(java.util.Locale.ROOT);
         String lowerQuery = query.toLowerCase(java.util.Locale.ROOT);
         int lastIdx = 0;
         int idx;
         while ((idx = lower.indexOf(lowerQuery, lastIdx)) != -1) {
            if (idx > lastIdx) {
               result.append(Component.literal(text.substring(lastIdx, idx)).withStyle(style));
            }
            result.append(Component.literal(text.substring(idx, idx + lowerQuery.length()))
                  .withStyle(style.withColor(net.minecraft.ChatFormatting.GOLD).withBold(true)));
            lastIdx = idx + lowerQuery.length();
         }
         if (lastIdx < text.length()) {
            result.append(Component.literal(text.substring(lastIdx)).withStyle(style));
         }
         return java.util.Optional.empty();
      }, net.minecraft.network.chat.Style.EMPTY);
      return result;
   }
}
