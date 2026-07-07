package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @Redirect(
        method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
        )
    )
    private Object onGetItemModel(ItemStack stack, net.minecraft.core.component.DataComponentType<?> type) {
        if (BomboConfig.get().restoreItemModels && type == DataComponents.ITEM_MODEL) {
            String id = me.bombo.bomboaddons.SkyblockUtils.getInternalIdRaw(stack);
            me.bombo.bomboaddons.BomboConfig.CustomItemOverride customOverride = null;
            if (id != null && !id.isEmpty()) {
                customOverride = BomboConfig.get().customItemOverrides.get(id);
            } else {
                boolean originalRestore = BomboConfig.get().restoreItemModels;
                BomboConfig.get().restoreItemModels = false;
                Item orig = stack.getItem();
                BomboConfig.get().restoreItemModels = originalRestore;
                
                if (orig != null) {
                    String vanillaId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(orig).toString();
                    customOverride = BomboConfig.get().customItemOverrides.get(vanillaId);
                }
            }

            if (customOverride != null && customOverride.material != null && !customOverride.material.isEmpty()) {
                String mapped = me.bombo.bomboaddons.LF.guessItem(customOverride.material);
                if (mapped == null) {
                    mapped = "minecraft:" + customOverride.material.toLowerCase();
                }
                if (BomboConfig.get().debugMode) {
                    System.out.println("[BomboDebug] ItemModelResolver: Overriding ITEM_MODEL to: " + mapped + " for " + stack.getHoverName().getString());
                }
                return Identifier.parse(mapped);
            }

            if (id != null && !id.isEmpty()) {
                me.bombo.bomboaddons.SkyblockItemManager.SkyblockItemInfo info = me.bombo.bomboaddons.SkyblockItemManager.getInfo(id);
                if (info != null) {
                    String overrideModel = info.itemModel;
                    if (overrideModel == null || overrideModel.isEmpty()) {
                        overrideModel = info.material;
                    }
                    if (overrideModel != null && !overrideModel.isEmpty()) {
                        String mapped = me.bombo.bomboaddons.LF.guessItem(overrideModel);
                        if (mapped == null) {
                            mapped = "minecraft:" + overrideModel.toLowerCase();
                        }
                        if (BomboConfig.get().debugMode) {
                            System.out.println("[BomboDebug] ItemModelResolver: Overriding ITEM_MODEL to: " + mapped + " for " + stack.getHoverName().getString());
                        }
                        return Identifier.parse(mapped);
                    }
                }
            }
        }
        return stack.get(type);
    }
}
