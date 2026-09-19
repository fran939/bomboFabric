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
      String uuid = me.bombo.bomboaddons.ItemCustomizeScreen.extractItemUuid(itemStack);
      String sbId = me.bombo.bomboaddons.SkyblockUtils.getInternalIdRaw(itemStack);
      String regId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
      me.bombo.bomboaddons.BomboConfig.CustomItemOverride cov = null;
      if (uuid != null && !uuid.isEmpty()) {
         cov = me.bombo.bomboaddons.BomboConfig.get().customItemOverrides.get(uuid);
      } else if (sbId != null && !sbId.isEmpty()) {
         cov = me.bombo.bomboaddons.BomboConfig.get().customItemOverrides.get(sbId);
      } else if (regId != null && !regId.isEmpty()) {
         cov = me.bombo.bomboaddons.BomboConfig.get().customItemOverrides.get(regId);
      }

      if (cov != null && cov.material != null && !cov.material.trim().isEmpty()) {
         String matName = cov.material.trim().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "");
         net.minecraft.world.item.Item overrideItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("minecraft", matName));
         if (overrideItem != null && overrideItem != net.minecraft.world.item.Items.AIR) {
            ItemStack stack = new ItemStack(overrideItem);
            if (cov.enchanted != null) {
               stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, cov.enchanted);
            } else if (itemStack.hasFoil()) {
               stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            itemModelResolver.updateForTopItem(itemStackRenderState, stack, itemDisplayContext, clientLevel, itemOwner, i);
            return;
         }
      }

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
