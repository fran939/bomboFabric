package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.Bomboaddons;
import me.bombo.bomboaddons_final.BomboConfig;
import me.bombo.bomboaddons_final.LowestBinManager;
import me.bombo.bomboaddons_final.RomanNumber;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "getTooltipLines", at = @At("RETURN"))
    private void onGetTooltipLines(Item.TooltipContext context, Player player, TooltipFlag tooltipFlag, CallbackInfoReturnable<List<Component>> cir) {
        if (!BomboConfig.get().lowestBin) return;

        ItemStack stack = (ItemStack) (Object) this;
        String skyblockId = null;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("id")) {
                skyblockId = tag.getString("id").orElse(null);
            } else if (tag.contains("ExtraAttributes")) {
                CompoundTag extraAttributes = tag.getCompound("ExtraAttributes").orElse(null);
                if (extraAttributes != null && extraAttributes.contains("id")) {
                    skyblockId = extraAttributes.getString("id").orElse(null);
                }
            }
        }

        if (BomboConfig.get().debugMode) {
            Bomboaddons.sendMessage("§7[Debug] Skyblock ID found: §b" + skyblockId);
        }

        if (skyblockId == null || skyblockId.equals("ENCHANTED_BOOK")) {
            if (stack.getItem().toString().contains("enchanted_book") || stack.getHoverName().getString().contains("Enchanted Book")) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore != null && !lore.lines().isEmpty()) {
                    for (int i = 0; i < Math.min(lore.lines().size(), 10); i++) {
                        String line = lore.lines().get(i).getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                        if (line.isEmpty() || line.equalsIgnoreCase("Rare Book!") || line.equalsIgnoreCase("Super Rare Book!")) continue;

                        int lastSpace = line.lastIndexOf(' ');
                        if (lastSpace != -1) {
                            String name = line.substring(0, lastSpace).trim().toUpperCase().replace(" ", "_");
                            String tierStr = line.substring(lastSpace + 1).trim();
                            int tier = RomanNumber.romanToDecimal(tierStr);
                            if (tier <= 0) {
                                try { tier = Integer.parseInt(tierStr); } catch (Exception ignored) {}
                            }

                            if (tier > 0) {
                                String baseId = name + "_" + tier;
                                // Try common prefixes to find valid price data
                                if (LowestBinManager.getCachedPrice("ENCHANTMENT_" + baseId) > 0) {
                                    skyblockId = "ENCHANTMENT_" + baseId;
                                    break;
                                }
                                if (LowestBinManager.getCachedPrice("ENCHANTED_BOOK_" + baseId) > 0) {
                                    skyblockId = "ENCHANTED_BOOK_" + baseId;
                                    break;
                                }
                                if (LowestBinManager.getCachedPrice(baseId) > 0) {
                                    skyblockId = baseId;
                                    break;
                                }
                                // Fallback to baseId if nothing better found
                                skyblockId = baseId;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (skyblockId != null) {
            List<Component> lines = cir.getReturnValue();
            int count = stack.getCount();

            // Special case for Composter: use 'Compost Available' count instead of stack size
            if (stack.getHoverName().getString().contains("Collect Compost")) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore != null) {
                    for (Component line : lore.lines()) {
                        String lineStr = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                        if (lineStr.contains("Compost Available: ")) {
                            String countStr = lineStr.substring(lineStr.indexOf("Compost Available: ") + 19).replaceAll("[^0-9]", "");
                            if (!countStr.isEmpty()) {
                                try {
                                    count = Integer.parseInt(countStr);
                                } catch (NumberFormatException ignored) {}
                            }
                            break;
                        }
                    }
                }
            }

            if (BomboConfig.get().lowestBin) {
                long price = LowestBinManager.getLowestBin(skyblockId).getNow(-1L);
                if (price > 0) {
                    boolean isBz = LowestBinManager.isBazaar(skyblockId);
                    String label = isBz ? "§6BZ: " : "§6Lowest BIN: ";
                    String priceText = label + "§e" + LowestBinManager.formatPrice(price);
                    if (count > 1) {
                        priceText += " §7(" + LowestBinManager.formatPrice(price * count) + ")";
                    }
                    lines.add(Component.literal(priceText));
                } else if (BomboConfig.get().debugMode) {
                    lines.add(Component.literal("§c[Debug] No BIN for: " + skyblockId));
                }
            }
            
            if (BomboConfig.get().npcPrice) {
                long npcPrice = LowestBinManager.getNpcPrice(skyblockId);
                if (npcPrice >= 0) {
                    String text = "§6NPC: §e" + LowestBinManager.formatPrice(npcPrice);
                    if (count > 1) {
                        text += " §7(" + LowestBinManager.formatPrice(npcPrice * count) + ")";
                    }
                    lines.add(Component.literal(text));
                } else if (BomboConfig.get().debugMode) {
                    lines.add(Component.literal("§c[Debug] No NPC for: " + skyblockId));
                }
            }

            Integer bitCost = me.bombo.bomboaddons_final.BitsManager.bitCostCache.get(skyblockId);
            if (bitCost != null) {
                String bitsText = "§bBit Cost: " + LowestBinManager.formatPrice((long) bitCost) + " bits";
                if (count > 1) {
                    bitsText += " §7(" + LowestBinManager.formatPrice((long) bitCost * count) + " bits)";
                }
                lines.add(Component.literal(bitsText));
            }
        }
    }
}
