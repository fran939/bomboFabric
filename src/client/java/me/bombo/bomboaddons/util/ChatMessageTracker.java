package me.bombo.bomboaddons.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class ChatMessageTracker {
   private static final List<Component> RECENT_MESSAGES = new ArrayList();

   public static class AcceptInfo {
      public final String command;
      public final String ticketDescription;

      public AcceptInfo(String command, String ticketDescription) {
         this.command = command;
         this.ticketDescription = ticketDescription;
      }
   }

   private static String lastTicketDescription = null;

   public static synchronized void addMessage(Component component) {
      if (component != null) {
         RECENT_MESSAGES.add(0, component);
         if (RECENT_MESSAGES.size() > 100) {
            RECENT_MESSAGES.remove(RECENT_MESSAGES.size() - 1);
         }

         // Check if this component contains a ticket description
         String desc = extractTicketDescription(component);
         if (desc != null && !desc.trim().isEmpty()) {
            lastTicketDescription = desc.trim();
         }
      }
   }

   public static synchronized String getLastTicketDescription() {
      return lastTicketDescription;
   }

   public static synchronized AcceptInfo findBestAcceptInfo() {
      for(Component comp : RECENT_MESSAGES) {
         String cmd = scanComponentForAccept(comp, true);
         if (cmd != null && !cmd.trim().isEmpty()) {
            String desc = extractTicketDescription(comp);
            if (desc == null) desc = lastTicketDescription;
            return new AcceptInfo(cmd, desc);
         }
      }

      for(Component comp : RECENT_MESSAGES) {
         String cmd = scanComponentForAccept(comp, false);
         if (cmd != null && !cmd.trim().isEmpty()) {
            String desc = extractTicketDescription(comp);
            if (desc == null) desc = lastTicketDescription;
            return new AcceptInfo(cmd, desc);
         }
      }

      return null;
   }

   public static synchronized String findBestAcceptCommand() {
      AcceptInfo info = findBestAcceptInfo();
      return info != null ? info.command : null;
   }

   private static String extractTicketDescription(Component comp) {
      if (comp == null) return null;
      
      // 1. Check HoverEvent on component or any siblings
      String hoverText = extractHoverText(comp);
      if (hoverText != null && !hoverText.isEmpty()) {
         String cleanHover = hoverText.replaceAll("(?i)§[0-9a-fk-orxX]", "").trim();
         if (!cleanHover.isEmpty() && !cleanHover.equalsIgnoreCase("click to accept") && !cleanHover.equalsIgnoreCase("click to view")) {
            return cleanHover;
         }
      }

      // 2. Check full text of message
      String fullText = comp.getString().replaceAll("(?i)§[0-9a-fk-orxX]", "").trim();
      
      // Check regex for Ticket #\d+ followed by description
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)ticket\\s*#?\\s*\\d+[:\\-–—\\s|>\\]]+\\s*(.+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(fullText);
      if (m.find()) {
         String captured = m.group(1).trim();
         // Remove "[ACCEPT]" or "(Click here)" buttons at the end
         captured = captured.replaceAll("(?i)\\[?(click\\s+to\\s+)?(accept|view|open)\\]?", "").trim();
         if (!captured.isEmpty()) {
            return captured;
         }
      }

      // 3. Fallback: If text contains Ticket and something after it
      if (fullText.toLowerCase().contains("ticket")) {
         int tIdx = fullText.toLowerCase().indexOf("ticket");
         String sub = fullText.substring(tIdx);
         sub = sub.replaceAll("(?i)\\[?(click\\s+to\\s+)?(accept|view|open)\\]?", "").trim();
         if (sub.length() > 5) {
            return sub;
         }
      } else if (!fullText.isEmpty()) {
         // If this component was clicked or has an accept command, the text itself might be the ticket description!
         String clean = fullText.replaceAll("(?i)\\[?(click\\s+to\\s+)?(accept|view|open)\\]?", "").trim();
         if (!clean.isEmpty() && clean.length() > 5) {
            return clean;
         }
      }

      return null;
   }

   private static String extractHoverText(Component comp) {
      if (comp == null) return null;
      if (comp.getStyle() != null && comp.getStyle().getHoverEvent() != null) {
         net.minecraft.network.chat.HoverEvent hover = comp.getStyle().getHoverEvent();
         if (hover instanceof net.minecraft.network.chat.HoverEvent.ShowText showText) {
            return showText.value().getString();
         } else {
            // In case of custom ShowText serialization
            try {
               for (java.lang.reflect.Method m : hover.getClass().getDeclaredMethods()) {
                  if (m.getParameterCount() == 0 && Component.class.isAssignableFrom(m.getReturnType())) {
                     m.setAccessible(true);
                     Component c = (Component) m.invoke(hover);
                     if (c != null) return c.getString();
                  }
               }
            } catch (Throwable ignored) {}
         }
      }
      for (Component sib : comp.getSiblings()) {
         String found = extractHoverText(sib);
         if (found != null) return found;
      }
      return null;
   }

   private static String scanComponentForAccept(Component comp, boolean strictAcceptOnly) {
      if (comp == null) {
         return null;
      } else {
         Style style = comp.getStyle();
         if (style != null && style.getClickEvent() != null) {
            ClickEvent click = style.getClickEvent();
            String cmd = extractCommand(click);
            if (cmd != null && !cmd.trim().isEmpty()) {
               String cmdLower = cmd.toLowerCase();
               String textLower = comp.getString().toLowerCase();
               String styleStr = style.toString().toLowerCase();
               if (strictAcceptOnly) {
                  if (cmdLower.contains("accept") || textLower.contains("accept") || textLower.contains("click") && styleStr.contains("green")) {
                     return cmd;
                  }
               } else if (cmdLower.startsWith("/") || cmdLower.contains("accept") || textLower.contains("click")) {
                  return cmd;
               }
            }
         }

         for(Component sibling : comp.getSiblings()) {
            String found = scanComponentForAccept(sibling, strictAcceptOnly);
            if (found != null) {
               return found;
            }
         }

         return null;
      }
   }

   private static String extractCommand(ClickEvent clickEvent) {
      if (clickEvent == null) {
         return null;
      } else if (clickEvent instanceof ClickEvent.RunCommand) {
         ClickEvent.RunCommand runCmd = (ClickEvent.RunCommand)clickEvent;
         return runCmd.command();
      } else if (clickEvent instanceof ClickEvent.SuggestCommand) {
         ClickEvent.SuggestCommand suggestCmd = (ClickEvent.SuggestCommand)clickEvent;
         return suggestCmd.command();
      } else {
         try {
            for(Method m : clickEvent.getClass().getDeclaredMethods()) {
               if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                  m.setAccessible(true);
                  String val = (String)m.invoke(clickEvent);
                  if (val != null && (val.startsWith("/") || val.toLowerCase().contains("accept"))) {
                     return val;
                  }
               }
            }
         } catch (Throwable var6) {
         }

         return clickEvent.toString();
      }
   }
}
