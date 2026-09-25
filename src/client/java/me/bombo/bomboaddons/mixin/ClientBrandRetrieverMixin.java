package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.Constants;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bombo$getClientModName(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(Constants.MOD_ID);
    }
}
