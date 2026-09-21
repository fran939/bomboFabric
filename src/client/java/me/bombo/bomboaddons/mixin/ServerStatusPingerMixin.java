package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.features.HypixelIpBypass;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerStatusPinger.class)
public class ServerStatusPingerMixin {
    @Inject(
            method = "pingServer(Lnet/minecraft/client/multiplayer/ServerData;Ljava/lang/Runnable;Ljava/lang/Runnable;)V",
            at = @At("HEAD")
    )
    private void onPingServer(ServerData serverData, Runnable onPersistentDataChange, Runnable onPongResponse, CallbackInfo ci) {
        if (serverData != null) {
            System.out.println("[HypixelBypass] ServerStatusPinger pinging: " + serverData.name + " (" + serverData.ip + ")");
        }
    }
}
