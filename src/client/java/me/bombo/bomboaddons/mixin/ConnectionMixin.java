package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.HypixelIpBypass;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;

@Mixin(Connection.class)
public class ConnectionMixin {
    @ModifyVariable(
            method = "connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static InetSocketAddress onConnect(InetSocketAddress address) {
        if (address != null) {
            BomboConfig.Settings config = BomboConfig.get();
            if (config != null && config.hypixelIspFix) {
                String host = address.getHostString();
                String ip = address.getAddress() != null ? address.getAddress().getHostAddress() : "";
                
                boolean isHypixel = HypixelIpBypass.isHypixelHost(host) || "172.65.197.160".equals(ip);
                if (isHypixel) {
                    String lower = host != null ? host.toLowerCase(Locale.ROOT).trim() : "";
                    if (!lower.equals("alpha.hypixel.net")) {
                        String bypassIp = config.hypixelBypassIp != null && !config.hypixelBypassIp.trim().isEmpty() 
                                ? config.hypixelBypassIp.trim() 
                                : HypixelIpBypass.DEFAULT_BYPASS_ENDPOINT;
                        try {
                            InetAddress resolved;
                            try {
                                resolved = InetAddress.getByName(bypassIp);
                            } catch (Exception e) {
                                resolved = InetAddress.getByAddress(HypixelIpBypass.FALLBACK_SPECTRUM_IP);
                            }
                            System.out.println("[BomboAddons] Connection socket redirect: " + host + " (" + ip + ") -> " + resolved.getHostAddress() + ":" + address.getPort());
                            return new InetSocketAddress(resolved, address.getPort());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return address;
    }
}
