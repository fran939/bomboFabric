package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.HypixelIpBypass;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientIntentionPacket.class)
public class ClientIntentionPacketMixin {
    @ModifyVariable(
            method = "<init>(ILjava/lang/String;ILnet/minecraft/network/protocol/handshake/ClientIntent;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static String onConstructHost(String hostName) {
        BomboConfig.Settings config = BomboConfig.get();
        if (config != null && config.hypixelIspFix) {
            return HypixelIpBypass.normalizeHypixelHost(hostName);
        }
        return hostName;
    }
}
