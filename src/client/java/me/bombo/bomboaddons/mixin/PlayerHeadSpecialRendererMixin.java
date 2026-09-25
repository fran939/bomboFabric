package me.bombo.bomboaddons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import me.bombo.bomboaddons.SkyblockItemManager;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.features.TextureToggleManager;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerHeadSpecialRenderer.class)
public class PlayerHeadSpecialRendererMixin {
    @ModifyExpressionValue(
        method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;")
    )
    private Object bombo$extractArgument(Object original, @Local(argsOnly = true) ItemStack stack) {
        if (stack == null || stack.isEmpty()) return original;
        if (original != null) return original;

        GameProfile gp = TextureToggleManager.INSTANCE.gameProfile(stack);
        if (gp != null) {
            return ResolvableProfile.createResolved(gp);
        }

        String id = SkyblockUtils.getInternalIdRaw(stack);
        if (id != null && !id.isEmpty()) {
            SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(id);
            if (info != null && info.skinValue != null) {
                ResolvableProfile rp = SkyblockItemManager.createProfile(info.skinValue, info.skinSignature);
                if (rp != null) return rp;
            }
        }
        return original;
    }
}
