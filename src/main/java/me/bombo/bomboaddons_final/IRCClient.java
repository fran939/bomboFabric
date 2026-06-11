package me.bombo.bomboaddons_final;

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
    
    private static void runLoop() {
        Random random = new Random();
        while (running) {
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
                
                socket = new Socket(SERVER, PORT);
                writer = new PrintWriter(socket.getOutputStream(), true);
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                
                sendRaw("NICK " + currentNick);
                sendRaw("USER " + currentNick + " 0 * :BomboAddons User");
                
                String line;
                while ((line = reader.readLine()) != null) {
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
                            String actualMsg = msgParts[2];
                            
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
                            formattedMessage = "§9Party §8> " + rankPrefix + senderNick + "§f: §r" + payload;
                        }
                        
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.execute(() -> {
                                if (mc.player != null) {
                                    mc.player.displayClientMessage(Component.literal(formattedMessage), false);
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
        if (msg == null || msg.trim().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        new Thread(() -> {
            try {
                String username = mc.getUser().getName();
                String prefix = RankCache.getRank(username);
                
                String payload = prefix + "\u0002" + username + "\u0002" + msg;
                sendRaw("PRIVMSG " + CHANNEL + " :" + payload);
                
                // Display our own message locally in chat
                String localPrefix = prefix;
                if (localPrefix.isEmpty()) {
                    localPrefix = "§7";
                } else if (!localPrefix.endsWith(" ")) {
                    localPrefix = localPrefix + " ";
                }
                String localMsg = "§9Party §8> " + localPrefix + username + "§f: §r" + msg;
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal(localMsg), false);
                    }
                });
            } catch (Exception e) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(Component.literal("§8[§bBomboAddons§8] §cFailed to send IRC message!"), false);
                    }
                });
            }
        }, "IRC-Send-Thread").start();
    }
    
    private static synchronized void sendRaw(String line) {
        if (writer != null) {
            writer.println(line);
        }
    }
    
    private static void closeQuietly() {
        try { if (reader != null) reader.close(); } catch (Exception e) {}
        try { if (writer != null) writer.close(); } catch (Exception e) {}
        try { if (socket != null) socket.close(); } catch (Exception e) {}
        reader = null;
        writer = null;
        socket = null;
    }
    
    public static String getLegacyString(Component component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();
        component.visit((style, text) -> {
            if (style.getColor() != null) {
                String colorName = style.getColor().serialize();
                ChatFormatting formatting = ChatFormatting.getByName(colorName);
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
