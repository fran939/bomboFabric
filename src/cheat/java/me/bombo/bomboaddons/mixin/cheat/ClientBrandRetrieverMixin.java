package me.bombo.bomboaddons.mixin.cheat;

import me.bombo.bomboaddons.util.Stealth;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports the vanilla client brand while stealth is enabled.
 *
 * <p>Fabric Loader makes this method return {@code "fabric"}, which is the only mod-related
 * field a vanilla server actually receives. With stealth on the client claims to be vanilla,
 * so the join handshake carries no mod marker.
 *
 * <p><b>Cheat-only:</b> this class is compiled from {@code src/cheat/java} and listed only in
 * {@code bomboclient.client.mixins.json}, so the legit jar neither ships nor applies it.
 */
@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {

    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void bombo$stealthBrand(CallbackInfoReturnable<String> cir) {
        if (Stealth.isEnabled()) {
            cir.setReturnValue(ClientBrandRetriever.VANILLA_NAME);
        }
    }
}
