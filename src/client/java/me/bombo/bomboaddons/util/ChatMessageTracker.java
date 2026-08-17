package me.bombo.bomboaddons.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

public class ChatMessageTracker {
   private static final List<Component> RECENT_MESSAGES = new ArrayList();

   public static synchronized void addMessage(Component component) {
      if (component != null) {
         RECENT_MESSAGES.add(0, component);
         if (RECENT_MESSAGES.size() > 100) {
            RECENT_MESSAGES.remove(RECENT_MESSAGES.size() - 1);
         }

      }
   }

   public static synchronized String findBestAcceptCommand() {
      for(Component comp : RECENT_MESSAGES) {
         String cmd = scanComponentForAccept(comp, true);
         if (cmd != null && !cmd.trim().isEmpty()) {
            return cmd;
         }
      }

      for(Component comp : RECENT_MESSAGES) {
         String cmd = scanComponentForAccept(comp, false);
         if (cmd != null && !cmd.trim().isEmpty()) {
            return cmd;
         }
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
