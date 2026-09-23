package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.util.Stealth;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reports the vanilla client brand so the join handshake carries no mod marker.
 *
 * <p>Fabric Loader makes this method return {@code "fabric"}. That single string is the only
 * mod-related value a vanilla server receives, which makes it the whole attack surface for a
 * mod-id blacklist - the failure mode that forced Odin to re-issue every build after a ban wave.
 *
 * <p><b>Shared on purpose.</b> This is compiled into both jars and applied in both, gated by the
 * {@code modIdHider} setting (on by default) or the cheat build's stealth mode. Protecting only
 * the cheat jar would leave the legit jar - the build most users run - as the exposed one.
 *
 * <p>Scope, honestly: this hides the brand, nothing else. A server can still infer client
 * behaviour from what the player does; this defeats identity blacklisting, not behavioural
 * detection.
 */
@Mixin(ClientBrandRetriever.class)
public class ClientBrandRetrieverMixin {

    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void bombo$hideBrand(CallbackInfoReturnable<String> cir) {
        try {
            if (Stealth.isBrandHidden()) {
                cir.setReturnValue(ClientBrandRetriever.VANILLA_NAME);
            }
        } catch (Throwable ignored) {
            // Never let an identity-hiding failure break the join.
        }
    }
}
