package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.ChatFormatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class IRCClient {
    private static final String SERVER = "irc.esper.net";
    private static final int PORT = 6667;
    private static final String CHANNEL = "#bomboaddons_chat";
    
    private static Socket socket;
    private static PrintWriter writer;
    private static BufferedReader reader;
    private static Thread clientThread;
    private static boolean running = false;
    private static String currentNick = "";
    
    // Concurrent map to keep track of online mod users
    private static final java.util.Map<String, String> onlinePlayers = new java.util.concurrent.ConcurrentHashMap<>();

    public static java.util.Map<String, String> getOnlinePlayers() {
        return onlinePlayers;
    }

    public static boolean isConnected() {
        return running && socket != null && !socket.isClosed();
    }

    public static class ModUser {
        public final String username;
        public final String version;
        
        public ModUser(String username, String version) {
            this.username = username;
            this.version = version;
        }
    }
    
    public static ModUser parseNick(String nick) {
        if (nick.startsWith("b_")) {
            String stripped = nick.substring(2);
            int vIdx = stripped.indexOf("_v");
            if (vIdx != -1) {
                String user = stripped.substring(0, vIdx);
                String verAndSuffix = stripped.substring(vIdx + 2);
                String[] verParts = verAndSuffix.split("_");
                StringBuilder verBuilder = new StringBuilder();
                for (int i = 0; i < Math.min(verParts.length, 3); i++) {
                    if (i > 0) verBuilder.append(".");
                    verBuilder.append(verParts[i]);
                }
                return new ModUser(user, verBuilder.toString());
            } else {
                return new ModUser(stripped, "Unknown (< 26.2.2)");
            }
        }
        return new ModUser(nick, "Unknown");
    }

    public static void start() {
        if (running) return;
        running = true;
        clientThread = new Thread(IRCClient::runLoop, "IRC-Client-Thread");
        clientThread.setDaemon(true);
        clientThread.start();
    }
    
    public static void onEnabledToggled() {
        if (!BomboConfig.get().ircChatEnabled) {
            new Thread(IRCClient::closeQuietly, "IRC-Close-Thread").start();
        }
    }

    private static void runLoop() {
        Random random = new Random();
        while (running) {
            if (!BomboConfig.get().ircChatEnabled) {
                if (socket != null) {
                    closeQuietly();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
                continue;
            }

            try {
                onlinePlayers.clear();
                Minecraft mc = Minecraft.getInstance();
                String username = "Player";
                if (mc.getUser() != null) {
                    username = mc.getUser().getName();
                }
                
                String cleanUsername = username.replaceAll("[^a-zA-Z0-9_]", "");
                if (cleanUsername.isEmpty()) {
                    cleanUsername = "bombo_" + random.nextInt(10000);
                }
                
                // Fetch mod version dynamically
                String versionStr = "26.2.2";
                try {
                    versionStr = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getModContainer("bomboaddons")
                        .map(m -> m.getMetadata().getVersion().getFriendlyString())
                        .orElse("26.2.2");
                } catch (Throwable ignored) {}
                
                String cleanVersion = versionStr.replace('.', '_');
                
                currentNick = "b_" + cleanUsername + "_v" + cleanVersion;
                if (currentNick.length() > 30) {
                    int maxUserLen = 30 - 3 - cleanVersion.length() - 2; // "b_" + "_v"
                    if (maxUserLen > 0 && cleanUsername.length() > maxUserLen) {
                        cleanUsername = cleanUsername.substring(0, maxUserLen);
                    }
                    currentNick = "b_" + cleanUsername + "_v" + cleanVersion;
                }
                
                Socket localSocket = new Socket(SERVER, PORT);
                socket = localSocket;
                PrintWriter localWriter = new PrintWriter(localSocket.getOutputStream(), true);
                writer = localWriter;
                BufferedReader localReader = new BufferedReader(new InputStreamReader(localSocket.getInputStream(), "UTF-8"));
                reader = localReader;
                
                sendRaw("NICK " + currentNick);
                sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                
                String line;
                while (running && BomboConfig.get().ircChatEnabled) {
                    line = localReader.readLine();
                    if (line == null) break;
                    handleLine(line);
                }
            } catch (Exception e) {
                // Ignore connection errors and auto-retry
            } finally {
                closeQuietly();
            }
            
            try {
                Thread.sleep(5000); // Wait 5 seconds before reconnecting
            } catch (InterruptedException ie) {
                break;
            }
        }
    }
    
    private static void handleLine(String line) {
        try {
            if (line.startsWith("PING ")) {
                sendRaw("PONG " + line.substring(5));
                return;
            }
            
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                String command = parts[1];
                if ("001".equals(command)) { // RPL_WELCOME - connection successful
                    sendRaw("JOIN " + CHANNEL);
                } else if ("433".equals(command)) { // ERR_NICKNAMEINUSE
                    Random rand = new Random();
                    currentNick = currentNick + "_" + rand.nextInt(100);
                    if (currentNick.length() > 30) {
                        currentNick = currentNick.substring(0, 20) + rand.nextInt(1000);
                    }
                    sendRaw("NICK " + currentNick);
                } else if ("353".equals(command) && parts.length >= 4) { // RPL_NAMREPLY
                    int colonIdx = line.indexOf(" :");
                    if (colonIdx != -1) {
                        String nicksList = line.substring(colonIdx + 2);
                        String[] nicks = nicksList.split(" ");
                        for (String nick : nicks) {
                            if (nick.startsWith("@") || nick.startsWith("+") || nick.startsWith("%") || nick.startsWith("~") || nick.startsWith("&")) {
                                nick = nick.substring(1);
                            }
                            if (nick.startsWith("b_")) {
                                onlinePlayers.put(nick.toLowerCase(java.util.Locale.ROOT), nick);
                            }
                        }
                    }
                } else if ("JOIN".equals(command)) {
                    String senderNick = parts[0].substring(1).split("!")[0];
                    if (senderNick.startsWith("b_")) {
                        onlinePlayers.put(senderNick.toLowerCase(java.util.Locale.ROOT), senderNick);
                    }
                } else if ("PART".equals(command)) {
                    String senderNick = parts[0].substring(1).split("!")[0];
                    onlinePlayers.remove(senderNick.toLowerCase(java.util.Locale.ROOT));
                } else if ("QUIT".equals(command)) {
                    String senderNick = parts[0].substring(1).split("!")[0];
                    onlinePlayers.remove(senderNick.toLowerCase(java.util.Locale.ROOT));
                } else if ("KICK".equals(command) && parts.length >= 4) {
                    String kickedNick = parts[3];
                    onlinePlayers.remove(kickedNick.toLowerCase(java.util.Locale.ROOT));
                } else if ("NICK".equals(command) && parts.length >= 3) {
                    String oldNick = parts[0].substring(1).split("!")[0];
                    String newNick = parts[2];
                    if (newNick.startsWith(":")) newNick = newNick.substring(1);
                    
                    onlinePlayers.remove(oldNick.toLowerCase(java.util.Locale.ROOT));
                    if (newNick.startsWith("b_")) {
                        onlinePlayers.put(newNick.toLowerCase(java.util.Locale.ROOT), newNick);
                    }
                } else if ("PRIVMSG".equals(command) && parts.length >= 4) {
                    // Line format: :nick!user@host PRIVMSG #channel :message
                    int privmsgIdx = line.indexOf(" PRIVMSG ");
                    int colonIdx = line.indexOf(" :", privmsgIdx);
                    if (privmsgIdx != -1 && colonIdx != -1) {
                        String senderPart = line.substring(1, privmsgIdx);
                        String senderNick = senderPart.split("!")[0];
                        if (senderNick.equalsIgnoreCase(currentNick)) {
                            return; // Skip our own message echoed back from server
                        }
                        
                        String payload = line.substring(colonIdx + 2);
                        String[] msgParts = payload.split("\u0002", 3);
                        String formattedMessage;
                        
                        if (msgParts.length == 3) {
                            String rankPrefix = msgParts[0];
                            String realUsername = msgParts[1];
                            String actualMsg = msgParts[2].replace('&', '§');
                            
                            if (rankPrefix.isEmpty()) {
                                rankPrefix = "§7";
                            } else if (!rankPrefix.endsWith(" ")) {
                                rankPrefix = rankPrefix + " ";
                            }
                            formattedMessage = "§r§8[§r§3Bombo§r§8] §r" + rankPrefix + realUsername + "§f: §r" + actualMsg;
                        } else {
                            String rankPrefix = RankCache.getRank(senderNick);
                            if (rankPrefix.isEmpty()) {
                                rankPrefix = "§7";
                            } else if (!rankPrefix.endsWith(" ")) {
                                rankPrefix = rankPrefix + " ";
                            }
                            String cleanPayload = payload.replace('&', '§');
                            formattedMessage = "§r§8[§r§3Bombo§r§8] §r" + rankPrefix + senderNick + "§f: §r" + cleanPayload;
                        }
                        
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.sendSystemMessage(Component.literal(formattedMessage));
                                }
                            });
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log/ignore parsing error
        }
    }
    
    public static void sendMessage(String msg) {
        System.out.println("[BomboAddons-IRC] sendMessage called with msg: " + msg);
        if (msg == null || msg.trim().isEmpty()) {
            System.out.println("[BomboAddons-IRC] Message is empty, ignoring.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            System.out.println("[BomboAddons-IRC] mc.player is null, ignoring.");
            return;
        }
        
        new Thread(() -> {
            try {
                System.out.println("[BomboAddons-IRC] Send thread started.");
                String username = mc.getUser().getName();
                System.out.println("[BomboAddons-IRC] Username: " + username);
                String prefix = RankCache.getRank(username);
                System.out.println("[BomboAddons-IRC] Rank prefix from cache: " + prefix);
                
                String coloredMsg = msg.replace('&', '§');
                String payload = prefix + "\u0002" + username + "\u0002" + coloredMsg;
                System.out.println("[BomboAddons-IRC] Sending payload: " + payload);
                sendRaw("PRIVMSG " + CHANNEL + " :" + payload);
                
                // Display our own message locally in chat
                String localPrefix = prefix;
                if (localPrefix.isEmpty()) {
                    localPrefix = "§7";
                } else if (!localPrefix.endsWith(" ")) {
                    localPrefix = localPrefix + " ";
                }
                String localMsg = "§r§8[§r§3Bombo§r§8] §r" + localPrefix + username + "§f: §r" + coloredMsg;
                
                final String finalLocalMsg = localMsg;
                System.out.println("[BomboAddons-IRC] Local msg prepared: " + finalLocalMsg);
                mc.execute(() -> {
                    System.out.println("[BomboAddons-IRC] Executing local display on main thread.");
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal(finalLocalMsg));
                        System.out.println("[BomboAddons-IRC] Local msg displayed.");
                    } else {
                        System.out.println("[BomboAddons-IRC] mc.player is null during display.");
                    }
                });
            } catch (Throwable e) {
                System.out.println("[BomboAddons-IRC] Exception/Error in sendMessage: " + e.getMessage());
                e.printStackTrace();
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cFailed to send IRC message: " + e.getClass().getSimpleName() + " - " + e.getMessage()));
                    }
                });
            }
        }, "IRC-Send-Thread").start();
    }
    
    private static void sendRaw(String line) {
        PrintWriter localWriter;
        synchronized (IRCClient.class) {
            localWriter = writer;
        }
        if (localWriter != null) {
            localWriter.println(line);
        }
    }
    
    private static void closeQuietly() {
        onlinePlayers.clear();
        BufferedReader r;
        PrintWriter w;
        Socket s;
        synchronized (IRCClient.class) {
            r = reader;
            w = writer;
            s = socket;
            reader = null;
            writer = null;
            socket = null;
        }
        try { if (r != null) r.close(); } catch (Throwable t) {}
        try { if (w != null) w.close(); } catch (Throwable t) {}
        try { if (s != null) s.close(); } catch (Throwable t) {}
    }
    
    public static String getLegacyString(Component component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();
        component.visit((style, text) -> {
            if (style.getColor() != null) {
                String colorName = style.getColor().serialize();
                ChatFormatting formatting = null;
                try {
                    formatting = ChatFormatting.valueOf(colorName.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {}
                if (formatting != null) {
                    sb.append(formatting.toString());
                }
            }
            if (style.isBold()) sb.append(ChatFormatting.BOLD.toString());
            if (style.isItalic()) sb.append(ChatFormatting.ITALIC.toString());
            if (style.isUnderlined()) sb.append(ChatFormatting.UNDERLINE.toString());
            if (style.isStrikethrough()) sb.append(ChatFormatting.STRIKETHROUGH.toString());
            if (style.isObfuscated()) sb.append(ChatFormatting.OBFUSCATED.toString());
            
            sb.append(text);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return sb.toString();
    }
}
