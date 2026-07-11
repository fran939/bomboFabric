package me.bombo.bomboaddons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.TextureToggleManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @WrapOperation(
        method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
        )
    )
    private Object onGetItemModel(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
        Object origValue = original.call(stack, type);

        if (BomboConfig.get().noResourcePack && origValue instanceof Identifier) {
            Identifier modelId = (Identifier) origValue;
            Identifier newModelId = TextureToggleManager.INSTANCE.fromModelId(stack, modelId);
            if (newModelId != null && !newModelId.equals(modelId)) {
                return newModelId;
            }
        }

        return origValue;
    }

    @WrapOperation(method = "appendItemLayers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemModel;update(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/item/ItemModelResolver;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/ItemOwner;I)V"))
    private void bomboaddons$onAppendLayer(ItemModel instance, ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemModelResolver itemModelResolver, ItemDisplayContext itemDisplayContext, ClientLevel clientLevel, ItemOwner itemOwner, int i, Operation<Void> original) {
        if (!BomboConfig.get().noResourcePack) {
            original.call(instance, itemStackRenderState, itemStack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i);
            return;
        }

        GameProfile gameProfile = TextureToggleManager.INSTANCE.gameProfile(itemStack);
        if (gameProfile == null) {
            original.call(instance, itemStackRenderState, itemStack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i);
            return;
        }

        ItemStack stack = itemStack.copy();
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gameProfile));
        original.call(instance, itemStackRenderState, stack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i);
    }
}
