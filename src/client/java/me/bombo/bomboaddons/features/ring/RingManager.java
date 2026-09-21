package me.bombo.bomboaddons.features.ring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ChromaTextHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class RingManager {

   private static final Pattern CALL_PATTERN = Pattern.compile("^(?:To|From|Para|De)\\s+(?:\\[.*?\\]\\s*)?([A-Za-z0-9_]{1,16}):\\s*CALL\\s+from\\s+([A-Za-z0-9_]{1,16})", Pattern.CASE_INSENSITIVE);
   private static final Pattern RING_PATTERN = Pattern.compile("^(?:To|From|Para|De)\\s+(?:\\[.*?\\]\\s*)?([A-Za-z0-9_]{1,16}):\\s*✆\\s*((?:RING\\.\\.\\.\\s*)+)\\[PICK UP\\]", Pattern.CASE_INSENSITIVE);

   private static final List<ScheduledChat> SCHEDULED_CHATS = new ArrayList<>();
   private static long suppressPartyInvitesUntil = 0;
   private static int suppressSeparatorLines = 0;

   public static class ScheduledChat {
      public int ticksRemaining;
      public String command;

      public ScheduledChat(int ticks, String command) {
         this.ticksRemaining = ticks;
         this.command = command;
      }
   }

   public static void initiateCall(String targetPlayer) {
      if (targetPlayer == null || targetPlayer.trim().isEmpty()) return;
      targetPlayer = targetPlayer.trim();

      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) return;
      String myName = mc.getUser().getName();

      suppressPartyInvitesUntil = System.currentTimeMillis() + 10000L;
      suppressSeparatorLines = 4;

      // 1. Initial CALL msg
      String zws1 = "\u200B".repeat((int) (System.currentTimeMillis() % 3 + 1));
      mc.player.connection.sendCommand("msg " + targetPlayer + " CALL from " + myName + zws1);

      // 2. Party invite if enabled
      if (BomboConfig.get().ringPartyInvite) {
         SCHEDULED_CHATS.add(new ScheduledChat(3, "p " + targetPlayer));
      }

      // 3. Schedule 3 rings with 0.65s (13 ticks) gap in between, with unique invisible characters
      SCHEDULED_CHATS.add(new ScheduledChat(13, "msg " + targetPlayer + " ✆ RING... [PICK UP]\u200B"));
      SCHEDULED_CHATS.add(new ScheduledChat(26, "msg " + targetPlayer + " ✆ RING... RING... [PICK UP]\u200B\u200B"));
      SCHEDULED_CHATS.add(new ScheduledChat(39, "msg " + targetPlayer + " ✆ RING... RING... RING... [PICK UP]\u200B\u200B\u200B"));
   }

   public static void tick() {
      if (SCHEDULED_CHATS.isEmpty()) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         SCHEDULED_CHATS.clear();
         return;
      }

      Iterator<ScheduledChat> it = SCHEDULED_CHATS.iterator();
      while (it.hasNext()) {
         ScheduledChat chat = it.next();
         chat.ticksRemaining--;
         if (chat.ticksRemaining <= 0) {
            try {
               mc.player.connection.sendCommand(chat.command);
            } catch (Throwable ignored) {}
            it.remove();
         }
      }
   }

   public static boolean shouldSuppressMessage(String cleanText) {
      if (cleanText == null) return false;
      String trimmed = cleanText.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();

      if (cleanText.contains("You can only send a message once every half second!") && !SCHEDULED_CHATS.isEmpty()) {
         return true;
      }

      // Divider lines: e.g. -----------------------------------------------------
      if (trimmed.matches("^-{5,}.*") || (trimmed.length() >= 5 && trimmed.replace("-", "").isEmpty())) {
         if (System.currentTimeMillis() < suppressPartyInvitesUntil || suppressSeparatorLines > 0) {
            if (suppressSeparatorLines > 0) suppressSeparatorLines--;
            return true;
         }
      }

      // Check for party invite suppression during active call
      if (System.currentTimeMillis() < suppressPartyInvitesUntil) {
         if ((trimmed.contains("invited ") && trimmed.contains("to the party")) ||
             trimmed.contains("have 60 seconds to accept.") ||
             trimmed.contains("has invited you to join their party!") ||
             trimmed.contains("You have 60 seconds to accept. Click here to join!")) {
            return true;
         }
      }

      return false;
   }

   public static Component processIncomingChat(String cleanText) {
      if (cleanText == null) return null;
      String trimmed = cleanText.replaceAll("(?i)§[0-9a-fk-or]", "").replaceAll("[\\u200B-\\u200D\\uFEFF]", "").trim();

      // Check CALL match
      Matcher callMatcher = CALL_PATTERN.matcher(trimmed);
      if (callMatcher.find()) {
         String target = callMatcher.group(1);
         String caller = callMatcher.group(2);
         boolean isOutgoing = trimmed.startsWith("To ") || trimmed.startsWith("Para ");
         suppressPartyInvitesUntil = System.currentTimeMillis() + 10000L;
         suppressSeparatorLines = Math.max(suppressSeparatorLines, 4);
         if (isOutgoing) {
            return ChromaTextHelper.parseFormattedText("&a✆ &7Ringing &a" + target + "&7. &a✆");
         } else {
            return ChromaTextHelper.parseFormattedText("&e✆ &r&a" + target + "&r&e ✆");
         }
      }

      // Check RING match
      Matcher ringMatcher = RING_PATTERN.matcher(trimmed);
      if (ringMatcher.find()) {
         String user = ringMatcher.group(1);
         String ringsPart = ringMatcher.group(2).trim();
         boolean isOutgoing = trimmed.startsWith("To ") || trimmed.startsWith("Para ");
         if (!isOutgoing) {
            suppressPartyInvitesUntil = System.currentTimeMillis() + 8000;
         }

         if (isOutgoing) {
            String dots = ".";
            if (ringsPart.contains("RING... RING... RING...")) {
               dots = ". . .";
            } else if (ringsPart.contains("RING... RING...")) {
               dots = ". .";
            }
            return ChromaTextHelper.parseFormattedText("&a✆ &7Ringing &a" + user + "&7" + dots + " &a✆");
         }

         String rings = (ringsPart == null || ringsPart.isEmpty()) ? "RING..." : ringsPart;
         MutableComponent mainComp = Component.literal("").append(ChromaTextHelper.parseFormattedText("&a✆ " + rings + " &r "));

         // Create clickable [PICK UP] button with /party accept (user)
         MutableComponent pickUpBtn = Component.literal("§2§l[PICK UP]");
         Style style = Style.EMPTY
            .withClickEvent(new ClickEvent.RunCommand("/party accept " + user))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§aClick to accept call / party from §e" + user)));
         pickUpBtn.setStyle(style);

         mainComp.append(pickUpBtn);
         return mainComp;
      }

      return null;
   }
}
