package me.bombo.bomboaddons;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class AFKManager {
   public static boolean isAfk = false;
   public static String targetIsland = null; // "private" or specific island
   public static String afkMessage = "I am currently AFK.";

   private static final Map<String, Long> lastAfkReply = new HashMap<>();
   private static String lastSentMsg = "";

   private static float lastYaw = 0f;
   private static float lastPitch = 0f;
   private static boolean hasLastRot = false;

   private static int actionCooldownTicks = 0;
   private static int state = 0;

   public static void toggleAfk(String island) {
      if (island != null && island.equalsIgnoreCase("off")) {
         setAfk(false, null);
         return;
      }

      if (isAfk) {
         if (island != null && !island.equalsIgnoreCase(targetIsland)) {
            // Change target island mode while remaining AFK
            targetIsland = island.toLowerCase();
            actionCooldownTicks = 40;
            state = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
               mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §aAFK mode updated: §e" + targetIsland + "§7."));
            }
            return;
         }
         setAfk(false, null);
      } else {
         setAfk(true, island);
      }
   }

   public static void setAfk(boolean afk, String island) {
      isAfk = afk;
      targetIsland = (island != null) ? island.toLowerCase() : null;
      hasLastRot = false;
      actionCooldownTicks = 40;
      state = 0;

      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         if (isAfk) {
            String msg = "§8[§3Bombo§8] §aYou are now AFK.";
            if (targetIsland != null) {
               msg += " §7Recovery mode: §e" + targetIsland;
            }
            mc.player.sendSystemMessage(Component.literal(msg));
         } else {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §cYou are no longer AFK."));
         }
      }
   }

   public static void disableAfk(String reason) {
      if (!isAfk) return;
      isAfk = false;
      targetIsland = null;
      hasLastRot = false;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8] §cAFK disabled (" + reason + ")."));
      }
   }

   public static void onChatMessage(String message) {
      if (!isAfk) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;

      Pattern whisperPattern = Pattern.compile("^From (?:.*\\] )?(\\w+): (.*)$");
      Matcher matcher = whisperPattern.matcher(message);
      if (matcher.matches()) {
         String sender = matcher.group(1);
         String content = matcher.group(2);
         if (content.toLowerCase().contains("afk")) {
            return;
         }

         if (!sender.equalsIgnoreCase(mc.player.getName().getString())) {
            long now = System.currentTimeMillis();
            if (now - lastAfkReply.getOrDefault(sender, 0L) > 10000L) {
               lastAfkReply.put(sender, now);
               String[] baseMessages = new String[]{
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
               String[] suffixes = new String[]{" [.]", " [..]", " [...]", " [!]", " [!!]", " [*]", " [=]", " [-]", " [~]", " [+]"};
               String fullMsg = "";

               for (int i = 0; i < 10; ++i) {
                  String selectedMessage = baseMessages[(new Random()).nextInt(baseMessages.length)];
                  String suffix = suffixes[(new Random()).nextInt(suffixes.length)];
                  String randomChars = "";

                  for (int j = 0; j < 3; ++j) {
                     randomChars += (char)(97 + (new Random()).nextInt(26));
                  }

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

   public static void tick() {
      if (!isAfk) return;

      Minecraft mc = Minecraft.getInstance();

      if (actionCooldownTicks > 0) {
         actionCooldownTicks--;
         return;
      }

      // Target Island / Private Recovery Sequence
      if (targetIsland != null && targetIsland.contains("privat")) {
         runPrivateIslandRecovery(mc);
      }
   }

   private static void runPrivateIslandRecovery(Minecraft mc) {
      // Step A: Check if connected to Hypixel
      boolean connectedHypixel = SkyblockUtils.isConnectedToHypixel();
      if (!connectedHypixel || mc.level == null || mc.player == null) {
         // Not on Hypixel -> join Hypixel
         try {
            BomboaddonsClient.reconnect(mc.gui.screen(), mc);
         } catch (Throwable ignored) {}
         actionCooldownTicks = 140; // 7s cooldown
         return;
      }

      // Step B: Check if in Limbo
      if (SkyblockUtils.isInLimbo() || "limbo".equalsIgnoreCase(BomboaddonsClient.locrawServer) || "Limbo".equalsIgnoreCase(BomboaddonsClient.currentArea)) {
         // On Limbo -> run /lobby
         mc.player.connection.sendCommand("lobby");
         actionCooldownTicks = 120; // 6s cooldown
         return;
      }

      // Step C: Check if in Hypixel Main Lobby (not in SkyBlock)
      boolean onSkyblock = "SKYBLOCK".equalsIgnoreCase(BomboaddonsClient.locrawGametype) || SkyblockUtils.isOnSkyblock();
      if (!onSkyblock) {
         // On lobby -> /play sb
         mc.player.connection.sendCommand("play sb");
         actionCooldownTicks = 140; // 7s cooldown
         return;
      }

      // Step D: On SkyBlock -> Check if on Private Island
      if (!isPrivateIsland()) {
         // On Skyblock but not private island -> /warp home
         mc.player.connection.sendCommand("warp home");
         actionCooldownTicks = 140; // 7s cooldown
         return;
      }

      // Step E: On Private Island -> All good! Check again in 4 seconds
      actionCooldownTicks = 80;
   }

   public static boolean isPrivateIsland() {
      String loc = SkyblockUtils.getLocation();
      if (loc != null && (loc.equalsIgnoreCase("Private Island") || loc.toLowerCase().contains("private island"))) {
         return true;
      }
      String sub = SkyblockUtils.getSubArea();
      if (sub != null && (sub.equalsIgnoreCase("Your Island") || sub.toLowerCase().contains("your island"))) {
         return true;
      }
      if ("dynamic".equalsIgnoreCase(BomboaddonsClient.locrawMode)) {
         return true;
      }
      return false;
   }
}
