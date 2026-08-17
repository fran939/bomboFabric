package me.bombo.bomboaddons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
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

@Mixin({ItemModelResolver.class})
public class ItemModelResolverMixin {
   @WrapOperation(
      method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
)}
   )
   private Object devonian$onAppendItemLayers(ItemStack instance, DataComponentType<?> dataComponentType, Operation<Object> original) {
      Object orig = original.call(new Object[]{instance, dataComponentType});
      if (orig instanceof Identifier modelId) {
         return TextureToggleManager.INSTANCE.fromModelId(instance, modelId);
      }
      return orig;
   }

   @WrapOperation(
      method = {"appendItemLayers"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/client/renderer/item/ItemModel;update(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/item/ItemModelResolver;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/ItemOwner;I)V"
)}
   )
   private void nrp$onAppendLayer(ItemModel instance, ItemStackRenderState itemStackRenderState, ItemStack itemStack, ItemModelResolver itemModelResolver, ItemDisplayContext itemDisplayContext, ClientLevel clientLevel, ItemOwner itemOwner, int i, Operation<Void> original) {
      if (!TextureToggleManager.INSTANCE.shouldBypass(itemStack)) {
         original.call(new Object[]{instance, itemStackRenderState, itemStack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i});
         return;
      }

      ItemStack stack = itemStack.copy();
      stack.remove(DataComponents.CUSTOM_MODEL_DATA);
      stack.remove(DataComponents.ITEM_MODEL);
      stack.remove(DataComponents.CUSTOM_DATA);

      GameProfile gameProfile = TextureToggleManager.INSTANCE.gameProfile(itemStack);
      if (gameProfile != null) {
         stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gameProfile));
      }

      original.call(new Object[]{instance, itemStackRenderState, stack, itemModelResolver, itemDisplayContext, clientLevel, itemOwner, i});
   }
}
