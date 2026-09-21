package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.HypixelIpBypass;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerNameResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerNameResolver.class)
public class ServerNameResolverMixin {
    @Inject(
            method = "resolveAddress(Lnet/minecraft/client/multiplayer/resolver/ServerAddress;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onResolveAddress(ServerAddress serverAddress, CallbackInfoReturnable<Optional<ResolvedServerAddress>> cir) {
        if (serverAddress != null) {
            String host = serverAddress.getHost();
            System.out.println("[HypixelBypass] Resolving server address: " + host + ":" + serverAddress.getPort());
            Optional<ResolvedServerAddress> bypass = HypixelIpBypass.resolve(serverAddress);
            if (bypass != null && bypass.isPresent()) {
                System.out.println("[HypixelBypass] Returning bypassed address: " + bypass.get().asInetSocketAddress());
                cir.setReturnValue(bypass);
            }
        }
    }
}
