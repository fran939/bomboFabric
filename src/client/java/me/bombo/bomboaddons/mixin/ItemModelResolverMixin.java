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
   @org.spongepowered.asm.mixin.Shadow
   @org.spongepowered.asm.mixin.Final
   private net.minecraft.client.resources.model.ModelManager modelManager;

   @org.spongepowered.asm.mixin.Unique
   private boolean hasValidModel(Identifier id) {
      return resolveValidId(id) != null;
   }

   @org.spongepowered.asm.mixin.Unique
   private Identifier resolveValidId(Identifier id) {
      if (modelManager == null || id == null) return null;
      ItemModel m = modelManager.getItemModel(id);
      if (m != null && !(m instanceof net.minecraft.client.renderer.item.MissingItemModel)) {
         return id;
      }
      if (id.getPath().startsWith("item/")) {
         Identifier stripped = safeId(id.getNamespace(), id.getPath().substring("item/".length()));
         if (stripped != null) {
            m = modelManager.getItemModel(stripped);
            if (m != null && !(m instanceof net.minecraft.client.renderer.item.MissingItemModel)) {
               return stripped;
            }
         }
      } else {
         Identifier prefixed = safeId(id.getNamespace(), "item/" + id.getPath());
         if (prefixed != null) {
            m = modelManager.getItemModel(prefixed);
            if (m != null && !(m instanceof net.minecraft.client.renderer.item.MissingItemModel)) {
               return prefixed;
            }
         }
      }
      return null;
   }

   @org.spongepowered.asm.mixin.Unique
   private Identifier safeId(String namespace, String path) {
      if (path == null || path.isEmpty()) return null;
      String sanitized = path.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
      return Identifier.tryParse(namespace + ":" + sanitized);
   }

   @WrapOperation(
      method = {"appendItemLayers", "shouldPlaySwapAnimation", "swapAnimationScale"},
      at = {@At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"
)}
   )
   private Object devonian$onAppendItemLayers(ItemStack instance, DataComponentType<?> dataComponentType, Operation<Object> original) {
      if (dataComponentType != DataComponents.ITEM_MODEL) {
         return original.call(new Object[]{instance, dataComponentType});
      }

      String sbId = me.bombo.bomboaddons.SkyblockUtils.getInternalIdRaw(instance);
      if (sbId == null || sbId.isEmpty()) {
         sbId = me.bombo.bomboaddons.SkyblockUtils.getSkyblockId(instance);
      }
      if (sbId == null || sbId.isEmpty()) {
         String clean = net.minecraft.ChatFormatting.stripFormatting(instance.getHoverName().getString());
         if (clean != null && !clean.isEmpty()) {
            sbId = me.bombo.bomboaddons.ExperimentationTableHud.getSkyblockIdFromName(clean);
         }
      }

      // Never override Player Heads or Pets with missing/cuboid models — let vanilla skull renderer draw player profile skin!
      if (instance.is(net.minecraft.world.item.Items.PLAYER_HEAD) || "PET".equalsIgnoreCase(sbId)) {
         return original.call(new Object[]{instance, dataComponentType});
      }

      if (sbId != null && !sbId.isEmpty()) {
         String lower = sbId.toLowerCase(java.util.Locale.ROOT);
         if (lower.contains(";")) {
            lower = lower.split(";")[0];
         }
         String sanitized = lower.replaceAll("[^a-z0-9/._-]", "_");

         net.minecraft.nbt.CompoundTag extra = me.bombo.bomboaddons.SkyblockUtils.getExtraAttributes(instance);
         String modifier = (extra != null && extra.contains("modifier")) ? extra.getString("modifier").orElse("").toLowerCase(java.util.Locale.ROOT) : "";
         String reforge = modifier;
         if (reforge.equals("aote_stone")) reforge = "warped";
         else if (reforge.equals("wither_blood")) reforge = "withered";
         else if (reforge.equals("dragon_horn")) reforge = "renowned";

         String plainHover = net.minecraft.ChatFormatting.stripFormatting(instance.getHoverName().getString()).toLowerCase(java.util.Locale.ROOT);
         boolean isWarpedName = plainHover.contains("warped");

         // Special handling for Aspect of the Void (user requested: use normal aotv texture for warped one)
         if (sanitized.equals("aspect_of_the_void")) {
            String manualOverride = me.bombo.bomboaddons.BomboConfig.get().warpedAotvCustomModelOverride;
            if (manualOverride != null && !manualOverride.trim().isEmpty()) {
               Identifier ov = Identifier.tryParse(manualOverride.trim());
               if (ov != null) {
                  Identifier validOv = resolveValidId(ov);
                  if (validOv != null) return validOv;
                  return ov;
               }
            }

            // Directly resolve clean base aspect_of_the_void from pack
            Identifier baseAotv = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.SIMPLE_MODELS.get("aspect_of_the_void");
            Identifier validAotv = resolveValidId(baseAotv);
            if (validAotv != null) return validAotv;

            Identifier firmAotv = resolveValidId(safeId("firmskyblock", "aspect_of_the_void"));
            if (firmAotv != null) return firmAotv;

            Identifier citAotv = resolveValidId(safeId("cittofirmgenerated", "item/aspect_of_the_void"));
            if (citAotv != null) return citAotv;

            Identifier citAotv2 = resolveValidId(safeId("cittofirmgenerated", "aspect_of_the_void"));
            if (citAotv2 != null) return citAotv2;
         }

         // 1. Check if reforge / modifier model exists (e.g. warped_aspect_of_the_void)
         java.util.List<String> prefixCandidates = new java.util.ArrayList<>();
         if (isWarpedName) prefixCandidates.add("warped");
         if (!reforge.isEmpty() && !prefixCandidates.contains(reforge)) prefixCandidates.add(reforge);
         if (!modifier.isEmpty() && !prefixCandidates.contains(modifier)) prefixCandidates.add(modifier);

         for (String p : prefixCandidates) {
            Identifier modSimple = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.SIMPLE_MODELS.get(p + "_" + sanitized);
            Identifier validMod = resolveValidId(modSimple);
            if (validMod != null) {
               return validMod;
            }
            Identifier firmMod = safeId("firmskyblock", p + "_" + sanitized);
            Identifier validFirm = resolveValidId(firmMod);
            if (validFirm != null) {
               return validFirm;
            }
         }

         // 2. Cross-namespace override check (e.g. firmskyblock aspect_of_the_void regex .*Warped.* override)
         Identifier simpleOverride = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.resolveOverrideBySimpleName(sanitized, instance);
         Identifier validSimpleOverride = resolveValidId(simpleOverride);
         if (validSimpleOverride != null) {
            return validSimpleOverride;
         }

         // 3. Check base model in SIMPLE_MODELS (e.g. aspect_of_the_void from hypixel_skyblock or any pack!)
         Identifier baseSimple = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.SIMPLE_MODELS.get(sanitized);
         if (baseSimple != null) {
            Identifier override = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.resolveOverride(baseSimple, instance);
            Identifier validOverride = resolveValidId(override);
            if (validOverride != null) {
               return validOverride;
            }
            if (!reforge.isEmpty()) {
               Identifier modAttempt = safeId(baseSimple.getNamespace(), "item/" + reforge + "_" + sanitized);
               Identifier validModAttempt = resolveValidId(modAttempt);
               if (validModAttempt != null) {
                  return validModAttempt;
               }
            }
            Identifier validBase = resolveValidId(baseSimple);
            if (validBase != null) {
               return validBase;
            }
         }

         Identifier firmModel = safeId("firmskyblock", sanitized);

         if (firmModel != null && hasValidModel(firmModel)) {
            Identifier overrideModel = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.resolveOverride(firmModel, instance);
            if (overrideModel != null) {
               if (hasValidModel(overrideModel)) {
                  return overrideModel;
               }
               Identifier genericOverride = safeId(overrideModel.getNamespace(), "item/" + overrideModel.getPath());
               if (genericOverride != null && hasValidModel(genericOverride)) {
                  return genericOverride;
               }
               return overrideModel;
            }
            // Check modifier / reforge direct model fallback
            if (extra != null && extra.contains("modifier")) {
               String mod = extra.getString("modifier").orElse("").toLowerCase(java.util.Locale.ROOT);
               if (!mod.isEmpty()) {
                  Identifier modModel = safeId("firmskyblock", mod + "_" + firmModel.getPath());
                  if (modModel != null && hasValidModel(modModel)) {
                     return modModel;
                  }
                  Identifier genericMod = safeId("firmskyblock", "item/" + mod + "_" + firmModel.getPath());
                  if (genericMod != null && hasValidModel(genericMod)) {
                     return genericMod;
                  }
               }
            }
            return firmModel;
         }

         Identifier citModel = safeId("cittofirmgenerated", lower);
         if (citModel == null || !hasValidModel(citModel)) {
            if (lower.contains(";")) {
               citModel = safeId("cittofirmgenerated", lower.split(";")[0]);
            }
         }
         if (citModel != null && hasValidModel(citModel)) {
            Identifier overrideModel = me.bombo.bomboaddons.features.texturepack.CustomSkyblockModelRegistry.resolveOverride(citModel, instance);
            if (overrideModel != null) {
               return overrideModel;
            }
            return citModel;
         }

         Identifier citHelmet = safeId("cittofirmgenerated", "helmet_icon/" + lower);
         if (citHelmet == null || !hasValidModel(citHelmet)) {
            if (lower.contains(";")) {
               citHelmet = safeId("cittofirmgenerated", "helmet_icon/" + lower.split(";")[0]);
            }
         }
         if (citHelmet != null && hasValidModel(citHelmet)) {
            return citHelmet;
         }
      }

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
