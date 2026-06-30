package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.core.component.PatchedDataComponentMap.class)
public class PatchedDataComponentMapMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private <T> void onGet(DataComponentType<? extends T> type, CallbackInfoReturnable<T> cir) {
        if (!BomboConfig.get().restoreItemModels) return;

        if (type == DataComponents.PROFILE) {
            DataComponentMap map = (DataComponentMap) this;
            String id = me.bombo.bomboaddons.SkyblockUtils.getInternalIdRaw(map);
            if (id != null && !id.isEmpty()) {
                me.bombo.bomboaddons.SkyblockItemManager.SkyblockItemInfo info = me.bombo.bomboaddons.SkyblockItemManager.getInfo(id);
                if (info != null && "SKULL_ITEM".equalsIgnoreCase(info.material) && info.skinValue != null) {
                    ResolvableProfile rp = me.bombo.bomboaddons.SkyblockItemManager.createProfile(info.skinValue, info.skinSignature);
                    if (rp != null) {
                        cir.setReturnValue((T) rp);
                    }
                }
            }
        }

        if (type == DataComponents.CUSTOM_MODEL_DATA) {
            DataComponentMap map = (DataComponentMap) this;
            String id = me.bombo.bomboaddons.SkyblockUtils.getInternalIdRaw(map);
            if (id != null && !id.isEmpty()) {
                boolean isOverridden = false;
                if (BomboConfig.get().customItemOverrides.containsKey(id)) {
                    isOverridden = true;
                }
                if (!isOverridden) {
                    me.bombo.bomboaddons.SkyblockItemManager.SkyblockItemInfo info = me.bombo.bomboaddons.SkyblockItemManager.getInfo(id);
                    if (info != null) {
                        isOverridden = me.bombo.bomboaddons.SkyblockItemManager.getOverrideItem(info.material) != null;
                    }
                }
                if (isOverridden) {
                    cir.setReturnValue(null);
                }
            }
        }
    }
}
