package me.bombo.bomboaddons.features;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboaddonsClient;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class AutoRejoinManager {

   private static int countdownTicks = -1;

   public static boolean isCountingDown() {
      return countdownTicks > 0;
   }

   public static int getCountdownTicks() {
      return countdownTicks;
   }

   public static int getRemainingSeconds() {
      return (countdownTicks + 19) / 20;
   }

   public static void cancel() {
      countdownTicks = -1;
   }

   public static void startCountdown(int seconds) {
      countdownTicks = seconds * 20;
   }

   public static void onChatMessage(String rawMessage) {
      if (rawMessage == null) return;
      BomboConfig.Settings s = BomboConfig.get();
      if (s == null || !s.autoRejoinSkyblock) return;

      if (rawMessage.contains("A kick occurred in your connection, so you were put in the SkyBlock lobby!")) {
         // 63 seconds = 1260 ticks
         countdownTicks = 63 * 20;
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§6[Bombo] §eConnection kick detected. Auto-rejoin timer started (63s)."));
         }
      }
   }

   public static void tick() {
      if (countdownTicks > 0) {
         // If player is already back on Skyblock, cancel the timer
         boolean inSkyblock = SkyblockUtils.isConnectedToHypixel() && 
            ("SKYBLOCK".equalsIgnoreCase(BomboaddonsClient.locrawGametype) || SkyblockUtils.matchesIslandRequirement("skyblock"));
         if (inSkyblock) {
            countdownTicks = -1;
            return;
         }

         countdownTicks--;
         if (countdownTicks == 0) {
            countdownTicks = -1;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.getConnection() != null) {
               if (!inSkyblock) {
                  mc.player.sendSystemMessage(Component.literal("§6[Bombo] §aExecuting /skyblock to rejoin Skyblock..."));
                  mc.player.connection.sendCommand("skyblock");
               }
            }
         }
      }
   }
}
