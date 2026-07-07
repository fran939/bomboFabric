package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AFKManager {
    public static boolean isAfk = false;
    public static String targetIsland = null;
    public static String afkMessage = "I am currently AFK.";

    private static int ticksSinceLimbo = 0;
    private static int state = 0; // 0 = idle, 1 = warping lobby, 2 = warping skyblock, 3 = warping island

    public static void toggleAfk(String island) {
        isAfk = !isAfk;
        targetIsland = island;
        state = 0;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (isAfk) {
                String msg = "§aYou are now AFK.";
                if (island != null)
                    msg += " §7Target island: §e" + island;
                mc.player.sendSystemMessage(Component.literal(msg));
            } else {
                mc.player.sendSystemMessage(Component.literal("§cYou are no longer AFK."));
            }
        }
    }

    private static final java.util.Map<String, Long> lastAfkReply = new java.util.HashMap<>();

    public static void onChatMessage(String message) {
        if (!isAfk)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        // Match whisper: "From [Rank] Name: message" or "From Name: message"
        Pattern whisperPattern = Pattern.compile("^From (?:.*\\] )?(\\w+): (.*)$");
        Matcher matcher = whisperPattern.matcher(message);
        if (matcher.matches()) {
            String sender = matcher.group(1);
            String content = matcher.group(2);
            if (content.toLowerCase().contains("afk"))
                return;

            if (!sender.equalsIgnoreCase(mc.player.getName().getString())) {
                long now = System.currentTimeMillis();
                if (now - lastAfkReply.getOrDefault(sender, 0L) > 10000) {
                    lastAfkReply.put(sender, now);
                    String[] baseMessages = {
                            "I am currently AFK.",
                            "I'm away from my keyboard right now.",
                            "AFK at the moment, will reply later.",
                            "Currently AFK, please wait.",
                            "I'm not at my PC right now (AFK).",
                            "Away from keyboard, sorry!",
                            "I'll be back later, currently AFK.",
                            "Not here right now. (AFK)",
                            "wawawa ima be back"
                    };
                    String[] suffixes = { " [.]", " [..]", " [...]", " [!]", " [!!]", " [*]", " [=]", " [-]", " [~]",
                            " [+]" };

                    String fullMsg = "";
                    for (int i = 0; i < 10; i++) {
                        String selectedMessage = baseMessages[new java.util.Random().nextInt(baseMessages.length)];
                        String suffix = suffixes[new java.util.Random().nextInt(suffixes.length)];
                        String randomChars = "";
                        for(int j=0; j<3; j++) randomChars += (char)('a' + new java.util.Random().nextInt(26));
                        fullMsg = selectedMessage + suffix + " [" + randomChars + "]";
                        if (!fullMsg.equals(lastSentMsg)) {
                            break;
                        }
                    }
                    lastSentMsg = fullMsg;

                    mc.player.connection.sendCommand("msg " + sender + " " + fullMsg);
                }
            }
        }
    }

    private static String lastSentMsg = "";
    private static String lastIsland = null;
    private static int ticksOnSameIsland = 0;

    public static void tick() {
        if (!isAfk)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        String island = SkyblockUtils.getLocation();
        if (island == null)
            island = "Unknown";

        if (!island.equals(lastIsland)) {
            lastIsland = island;
            ticksOnSameIsland = 0;
            ticksSinceLimbo = 0;
        } else {
            ticksOnSameIsland++;
        }

        if (targetIsland != null) {
            boolean onSkyblock = SkyblockUtils.isConnectedToHypixel()
                    && "SKYBLOCK".equals(BomboaddonsClient.locrawGametype);
            if (!onSkyblock) {
                ticksSinceLimbo++;
                if (ticksSinceLimbo > 200) { // wait 10 seconds
                    if (state == 0) {
                        mc.player.connection.sendCommand("lobby");
                        state = 1;
                        ticksSinceLimbo = 0;
                    } else if (state == 1) {
                        mc.player.connection.sendCommand("skyblock");
                        state = 2;
                        ticksSinceLimbo = 0;
                    }
                }
            } else {
                if (!island.toLowerCase().contains(targetIsland.toLowerCase())) {
                    // if changed world recently, wait 10s (200 ticks)
                    // if just sitting there, check every 60s (1200 ticks)
                    if (ticksOnSameIsland == 200 || (ticksOnSameIsland > 200 && ticksOnSameIsland % 1200 == 0)) {
                        mc.player.connection.sendCommand("warp " + targetIsland.toLowerCase());
                        state = 3;
                    }
                } else {
                    state = 0;
                }
            }
        }
    }
}
