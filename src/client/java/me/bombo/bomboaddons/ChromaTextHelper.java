package me.bombo.bomboaddons;

public class ChromaTextHelper {
   public static final int CHROMA_MARKER_RGB = 16711934;
   public static final int CHROMA_MARKER_ARGB = -65282;
   private static final String MARKER_HEX = "§x§f§f§0§0§f§e";
   private static final char[] RAINBOW = new char[]{'c', '6', 'e', 'a', 'b', '9', 'd', '5'};
   public static int tickCounter = 0;
   public static int chromaGlyphIndex = 0;
   public static int getAnimatedColor(int glyphIndex) {
      long time = System.currentTimeMillis();
      float hue = ((time / 10L) + (glyphIndex * 15L)) % 360L / 360.0F;
      return hsvToArgb(hue);
   }

   static int hsvToArgb(float hue) {
      int hi = (int)(hue * 6.0F);
      float f = hue * 6.0F - (float)hi;
      float q = 1.0F - f;
      float r;
      float g;
      float b;
      switch (hi % 6) {
         case 0:
            r = 1.0F;
            g = f;
            b = 0.0F;
            break;
         case 1:
            r = q;
            g = 1.0F;
            b = 0.0F;
            break;
         case 2:
            r = 0.0F;
            g = 1.0F;
            b = f;
            break;
         case 3:
            r = 0.0F;
            g = q;
            b = 1.0F;
            break;
         case 4:
            r = f;
            g = 0.0F;
            b = 1.0F;
            break;
         default:
            r = 1.0F;
            g = 0.0F;
            b = q;
      }

      int ri = Math.min(255, (int)(r * 255.0F));
      int gi = Math.min(255, (int)(g * 255.0F));
      int bi = Math.min(255, (int)(b * 255.0F));
      return -16777216 | ri << 16 | gi << 8 | bi;
   }

   public static String processChroma(String message) {
      if (message != null && !message.isEmpty()) {
         message = processHexColors(message);
         // Support &w(message) and &q(message) parenthetical syntax
         message = processParenthesesBlocks(message, "&w", true);
         message = processParenthesesBlocks(message, "&q", false);
         message = processBlocks(message, "&w", true);
         message = processBlocks(message, "&q", false);
         return message;
      } else {
         return message;
      }
   }

   public static String processHexColors(String text) {
      if (text == null || !text.contains("#")) return text;
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("#([0-9a-fA-F]{6})").matcher(text);
      StringBuilder sb = new StringBuilder();
      int last = 0;
      while (m.find()) {
         sb.append(text, last, m.start());
         String hex = m.group(1);
         sb.append("§x");
         for (int i = 0; i < 6; i++) {
            sb.append('§').append(Character.toLowerCase(hex.charAt(i)));
         }
         sb.append(m.group(0)); // Append the text itself (e.g. #3E05AF) colored with the hex code!
         last = m.end();
      }
      sb.append(text.substring(last));
      return sb.toString();
   }

   private static String processParenthesesBlocks(String msg, String code, boolean wave) {
      String target = code + "(";
      StringBuilder result = new StringBuilder();
      int end;
      for (int i = 0; i < msg.length(); i = end) {
         int idx = msg.indexOf(target, i);
         if (idx == -1) {
            result.append(msg.substring(i));
            break;
         }
         result.append(msg, i, idx);
         int start = idx + target.length();
         int closeParen = msg.indexOf(")", start);
         if (closeParen == -1) {
            result.append(msg.substring(idx));
            break;
         }
         String inner = msg.substring(start, closeParen);
         result.append(wave ? applyMarker(inner) : applyStaticRainbow(inner)).append("§r");
         end = closeParen + 1;
      }
      return result.toString();
   }

   private static String processBlocks(String msg, String code, boolean wave) {
      StringBuilder result = new StringBuilder();

      int end;
      for(int i = 0; i < msg.length(); i = end) {
         int idx = msg.indexOf(code, i);
         if (idx == -1) {
            result.append(msg.substring(i));
            break;
         }

         result.append(msg, i, idx);
         int start = idx + code.length();
         end = findEnd(msg, start);
         String inner = msg.substring(start, end);
         result.append(wave ? applyMarker(inner) : applyStaticRainbow(inner));
      }

      return result.toString();
   }

   private static String applyMarker(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c == ' ') {
               sb.append(c);
            } else {
               sb.append(MARKER_HEX).append(c);
            }
         }

         return sb.toString();
      } else {
         return text;
      }
   }

   static String applyStaticRainbow(String text) {
      if (text != null && !text.isEmpty()) {
         StringBuilder sb = new StringBuilder();
         int offset = tickCounter % RAINBOW.length;

         for(int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c == ' ') {
               sb.append(c);
            } else {
               int colorIdx = (offset + i) % RAINBOW.length;
               sb.append('§').append(RAINBOW[colorIdx]).append(c);
            }
         }

         return sb.toString();
      } else {
         return text;
      }
   }

   private static int findEnd(String msg, int start) {
      int next;
      for(int idx = start; idx < msg.length(); idx = next + 1) {
         next = msg.indexOf(38, idx);
         if (next == -1) {
            return msg.length();
         }

         if (next + 2 <= msg.length()) {
            String sub = msg.substring(next, next + 2);
            if (sub.equals("&r") || isFormatCode(sub)) {
               return next;
            }
         }
      }

      return msg.length();
   }

   private static boolean isFormatCode(String s) {
      if (s.length() == 2 && s.charAt(0) == '&') {
         char c = s.charAt(1);
         return "0123456789abcdefklmnor".indexOf(c) != -1 || c == 'w' || c == 'q';
      } else {
         return false;
      }
   }
}
