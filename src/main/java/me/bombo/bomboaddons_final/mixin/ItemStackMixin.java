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

        if (skyblockId == null || skyblockId.equals("ENCHANTED_BOOK")) {
            if (stack.getItem().toString().contains("enchanted_book") || stack.getHoverName().getString().contains("Enchanted Book")) {
                ItemLore lore = stack.get(DataComponents.LORE);
                if (lore != null && !lore.lines().isEmpty()) {
                    String firstLine = lore.lines().get(0).getString().replaceAll("(?i)§[0-9a-fk-or]", "").trim();
                    int lastSpace = firstLine.lastIndexOf(' ');
                    if (lastSpace != -1) {
                        String name = firstLine.substring(0, lastSpace).trim().toUpperCase().replace(" ", "_");
                        String tierStr = firstLine.substring(lastSpace + 1).trim();
                        int tier = -1;
                        if (tierStr.equals("I")) tier = 1;
                        else if (tierStr.equals("II")) tier = 2;
                        else if (tierStr.equals("III")) tier = 3;
                        else if (tierStr.equals("IV")) tier = 4;
                        else if (tierStr.equals("V")) tier = 5;
                        else if (tierStr.equals("VI")) tier = 6;
                        else if (tierStr.equals("VII")) tier = 7;
                        else if (tierStr.equals("VIII")) tier = 8;
                        else if (tierStr.equals("IX")) tier = 9;
                        else if (tierStr.equals("X")) tier = 10;
                        else {
                            try { tier = Integer.parseInt(tierStr); } catch (Exception ignored) {}
                        }

                        if (tier != -1) {
                            skyblockId = "ENCHANTMENT_" + name + "_" + tier;
                        }
                    }
                }
            }
        }

        if (skyblockId != null) {
            List<Component> tooltip = cir.getReturnValue();
            Long price = LowestBinManager.getLowestBin(skyblockId).getNow(-1L);

            if (price != -1L) {
                int count = stack.getCount();
                String priceText = "§6Lowest BIN: " + LowestBinManager.formatPrice(price) + " coins";
                if (count > 1) {
                    priceText += " §7(" + LowestBinManager.formatPrice(price * count) + " coins)";
                }
                tooltip.add(Component.literal(priceText));
            }

            Integer bitCost = me.bombo.bomboaddons_final.BitsManager.bitCostCache.get(skyblockId);
            if (bitCost != null) {
                int count = stack.getCount();
                String bitsText = "§bBit Cost: " + LowestBinManager.formatPrice((long) bitCost) + " bits";
                if (count > 1) {
                    bitsText += " §7(" + LowestBinManager.formatPrice((long) bitCost * count) + " bits)";
                }
                tooltip.add(Component.literal(bitsText));
            }
        }
    }
}
