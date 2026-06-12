package me.bombo.bomboaddons_final.mixin;

import me.bombo.bomboaddons_final.DungeonPadTimers;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public class DungeonPadTimersMixin {
    @Inject(method = "handlePing", at = @At("HEAD"))
    private void onHandlePing(ClientboundPingPacket packet, CallbackInfo ci) {
        try {
            DungeonPadTimers.onPingPacket(packet.getId());
        } catch (Throwable t) {
            // Prevent crash
        }
    }
}
