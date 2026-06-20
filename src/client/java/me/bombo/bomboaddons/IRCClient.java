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
                Minecraft mc = Minecraft.getInstance();
                String username = "Player";
                if (mc.getUser() != null) {
                    username = mc.getUser().getName();
                }
                
                String cleanUsername = username.replaceAll("[^a-zA-Z0-9_]", "");
                if (cleanUsername.isEmpty()) {
                    cleanUsername = "bombo_" + random.nextInt(10000);
                }
                currentNick = "b_" + cleanUsername;
                if (currentNick.length() > 20) {
                    currentNick = currentNick.substring(0, 20);
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
                    if (currentNick.length() > 20) {
                        currentNick = currentNick.substring(0, 15) + rand.nextInt(9000) + 1000;
                    }
                    sendRaw("NICK " + currentNick);
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
                            formattedMessage = "§9Party §8> " + rankPrefix + realUsername + "§f: §r" + actualMsg;
                        } else {
                            // Fallback: look up rank from cache or fetch it
                            String rankPrefix = RankCache.getRank(senderNick);
                            if (rankPrefix.isEmpty()) {
                                rankPrefix = "§7";
                            } else if (!rankPrefix.endsWith(" ")) {
                                rankPrefix = rankPrefix + " ";
                            }
                            String cleanPayload = payload.replace('&', '§');
                            formattedMessage = "§9Party §8> " + rankPrefix + senderNick + "§f: §r" + cleanPayload;
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
                String localMsg = "§9Party §8> " + localPrefix + username + "§f: §r" + coloredMsg;
                
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
