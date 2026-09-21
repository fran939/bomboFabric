package me.bombo.bomboaddons;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class PingTest {
    public static void main(String[] args) {
        String[] targets = {
                "172.65.197.160",
                "172.65.200.155",
                "172.65.242.203",
                "172.65.239.19",
                "172.65.228.188",
                "172.65.201.218"
        };
        String[] hosts = {
                "mc.hypixel.net",
                "alpha.hypixel.net",
                "hypixel.net"
        };

        for (String targetIp : targets) {
            for (String host : hosts) {
                testPing(targetIp, host, 25565);
            }
        }
    }

    public static void testPing(String targetIp, String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(targetIp, port), 2500);
            socket.setSoTimeout(2500);
            OutputStream out = socket.getOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            InputStream in = socket.getInputStream();
            DataInputStream dis = new DataInputStream(in);

            // Handshake packet
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(b);
            handshake.writeByte(0x00); // packet id 0
            writeVarInt(handshake, 767); // protocol version
            writeString(handshake, host);
            handshake.writeShort(port);
            writeVarInt(handshake, 1); // next state: status (1)

            byte[] handshakeBytes = b.toByteArray();
            writeVarInt(dos, handshakeBytes.length);
            dos.write(handshakeBytes);

            // Status request packet
            dos.writeByte(0x01); // length
            dos.writeByte(0x00); // packet id 0 (status request)
            dos.flush();

            // Read response
            int length = readVarInt(dis);
            int packetId = readVarInt(dis);
            String json = readString(dis);
            System.out.println("SUCCESS: IP=" + targetIp + " Host=" + host + " -> " + json.substring(0, Math.min(json.length(), 60)));
        } catch (Exception e) {
            System.out.println("FAILED:  IP=" + targetIp + " Host=" + host + " -> " + e.getMessage());
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while ((value & -128) != 0) {
            out.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws Exception {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = read & 127;
            result |= value << 7 * numRead;
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt is too big");
        } while ((read & 128) != 0);
        return result;
    }

    private static void writeString(DataOutputStream out, String string) throws Exception {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws Exception {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
