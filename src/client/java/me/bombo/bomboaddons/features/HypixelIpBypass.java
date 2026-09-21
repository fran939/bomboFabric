package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.DebugUtils;
import net.minecraft.client.multiplayer.resolver.ResolvedServerAddress;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

public class HypixelIpBypass {
    // Default fallback Cloudflare Spectrum IP for Hypixel (alpha.hypixel.net Spectrum edge)
    public static final String DEFAULT_BYPASS_ENDPOINT = "172.65.200.155";
    public static final byte[] FALLBACK_SPECTRUM_IP = new byte[]{(byte) 172, 65, (byte) 200, (byte) 155}; // 172.65.200.155

    // Known Cloudflare Spectrum edge IPs for Hypixel
    public static final String[] KNOWN_SPECTRUM_IPS = new String[]{
            "172.65.200.155",
            "172.65.242.203",
            "172.65.239.19",
            "172.65.228.188",
            "172.65.201.218"
    };

    public static boolean isHypixelHost(String host) {
        if (host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT).trim();
        return lower.equals("hypixel.net")
                || lower.endsWith(".hypixel.net")
                || lower.equals("hypixel.io")
                || lower.endsWith(".hypixel.io");
    }

    public static String normalizeHypixelHost(String host) {
        if (host == null) return "mc.hypixel.net";
        String lower = host.toLowerCase(Locale.ROOT).trim();
        if (lower.equals("hypixel.net") || lower.equals("hypixel.io")) {
            return "mc.hypixel.net";
        }
        return host;
    }

    public static Optional<ResolvedServerAddress> resolve(ServerAddress serverAddress) {
        if (serverAddress == null) return Optional.empty();

        BomboConfig.Settings config = BomboConfig.get();
        if (config == null || !config.hypixelIspFix) {
            return Optional.empty();
        }

        String host = serverAddress.getHost();
        if (host == null) return Optional.empty();

        String lowerHost = host.toLowerCase(Locale.ROOT).trim();
        // If connecting explicitly to alpha, let vanilla resolution handle it
        if (lowerHost.equals("alpha.hypixel.net")) {
            return Optional.empty();
        }

        if (!isHypixelHost(lowerHost)) {
            return Optional.empty();
        }

        int port = serverAddress.getPort() > 0 ? serverAddress.getPort() : 25565;
        String endpoint = config.hypixelBypassIp;
        if (endpoint == null || endpoint.trim().isEmpty() || endpoint.equalsIgnoreCase("alpha.hypixel.net")) {
            endpoint = DEFAULT_BYPASS_ENDPOINT;
        }
        endpoint = endpoint.trim();

        try {
            InetAddress resolvedTarget;
            try {
                resolvedTarget = InetAddress.getByName(endpoint);
            } catch (Exception e) {
                resolvedTarget = InetAddress.getByAddress(endpoint, FALLBACK_SPECTRUM_IP);
            }

            InetSocketAddress targetSocket = new InetSocketAddress(resolvedTarget, port);
            ResolvedServerAddress resolvedAddress = ResolvedServerAddress.from(targetSocket);

            System.out.println("[BomboAddons] Hypixel ISP Bypass active: Routing " + host + ":" + port + " -> " + targetSocket.getAddress().getHostAddress() + ":" + port);

            return Optional.of(resolvedAddress);
        } catch (Throwable t) {
            System.err.println("[BomboAddons] Failed to resolve Hypixel ISP bypass address for endpoint: " + endpoint);
            t.printStackTrace();
            return Optional.empty();
        }
    }
}
