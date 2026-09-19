package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.SkyblockItemManager;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({PatchedDataComponentMap.class})
public class PatchedDataComponentMapMixin {
   @Inject(
      method = {"get"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private <T> void onGet(DataComponentType<? extends T> type, CallbackInfoReturnable<T> cir) {
      if (type == DataComponents.PROFILE) {
         DataComponentMap map = (DataComponentMap)this;
         String id = SkyblockUtils.getInternalIdRaw(map);
         if (id != null && !id.isEmpty()) {
            SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(id);
            if (info != null && "SKULL_ITEM".equalsIgnoreCase(info.material) && info.skinValue != null) {
               ResolvableProfile rp = SkyblockItemManager.createProfile(info.skinValue, info.skinSignature);
               if (rp != null) {
                  cir.setReturnValue((T)rp);
                  return;
               }
            }
         }
      } else if (type == DataComponents.DYED_COLOR) {
         DataComponentMap map = (DataComponentMap)this;
         try {
            String uuid = null;
            net.minecraft.world.item.component.CustomData cd = (net.minecraft.world.item.component.CustomData)map.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
               net.minecraft.nbt.CompoundTag tag = cd.copyTag();
               if (tag != null && tag.contains("ExtraAttributes")) {
                  net.minecraft.nbt.CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
                  if (ea != null && ea.contains("uuid")) {
                     uuid = ea.getString("uuid").orElse(null);
                  }
               }
            }
            String skyId = SkyblockUtils.getInternalIdRaw(map);
            BomboConfig.CustomItemOverride override = null;
            if (uuid != null && !uuid.isEmpty()) {
               override = BomboConfig.get().customItemOverrides.get(uuid);
            }
            if (override == null && skyId != null && !skyId.isEmpty()) {
               override = BomboConfig.get().customItemOverrides.get(skyId);
            }
            if (override != null && override.armorColor != null && !override.armorColor.trim().isEmpty()) {
               int rgb = me.bombo.bomboaddons.ItemCustomizeScreen.parseColorHex(override.armorColor.trim());
               if (rgb != -1) {
                  cir.setReturnValue((T) new net.minecraft.world.item.component.DyedItemColor(rgb));
               }
            }
         } catch (Throwable ignored) {}
      }

   }
}
