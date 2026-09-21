package me.bombo.bomboaddons;

public class ChromaTextHelper {
   public static final int CHROMA_MARKER_RGB = 16711934;
   public static final int CHROMA_MARKER_ARGB = -65282;
   public static final String MARKER_HEX = "§x§f§f§0§0§f§e";
   private static final char[] RAINBOW = new char[]{'c', '6', 'e', 'a', 'b', '9', 'd', '5'};
   public static int tickCounter = 0;
   public static int chromaGlyphIndex = 0;
   private static long lastRenderFrameTime = 0L;
   private static int currentFrameCharCounter = 0;

   public static synchronized int getPerRenderCharIndex() {
      long now = System.currentTimeMillis();
      if (now - lastRenderFrameTime > 20L) {
         lastRenderFrameTime = now;
         currentFrameCharCounter = 0;
      }
      return currentFrameCharCounter++;
   }

   // Default smooth wave speed (5000ms per full color cycle)
   public static float currentWaveSpeedMultiplier = 1.0f;

   public static int getAnimatedColor(int glyphIndex) {
      long time = System.currentTimeMillis();
      float cyclePeriod = 5000.0f / Math.max(0.1f, currentWaveSpeedMultiplier);
      float hue = ((float)(time % (long)cyclePeriod) / cyclePeriod + (glyphIndex * 0.045f)) % 1.0f;
      return hsvToArgb(hue);
   }

   public static int getAnimatedColor(float xPos, float yPos) {
      long time = System.currentTimeMillis();
      float cyclePeriod = 5000.0f / Math.max(0.1f, currentWaveSpeedMultiplier);
      float hue = ((float)(time % (long)cyclePeriod) / cyclePeriod + (xPos * 0.006f + yPos * 0.003f)) % 1.0f;
      return hsvToArgb(hue);
   }

   public static int hsvToArgb(float hue) {
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
         // Support &w1(msg), &w2(msg), &w5(msg)
         for (int speed = 9; speed >= 1; speed--) {
            message = processSpeedParenthesesBlocks(message, "&w" + speed, speed);
         }
         message = processParenthesesBlocks(message, "&z", true);
         message = processParenthesesBlocks(message, "&chroma", true);
         message = processParenthesesBlocks(message, "&w", true);
         message = processParenthesesBlocks(message, "&q", false);

         // For non-parentheses blocks: only match &w1 .. &w9 if followed by space or non-digit
         for (int speed = 9; speed >= 1; speed--) {
            message = processSpeedBlocksStrict(message, "&w" + speed, speed);
         }
         message = processBlocks(message, "&z", true);
         message = processBlocks(message, "&chroma", true);
         message = processBlocks(message, "&w", true);
         message = processBlocks(message, "&q", false);
         return message;
      } else {
         return message;
      }
   }

   public static String processHexColors(String text) {
      if (text == null || text.isEmpty()) return text;
      // Support &h(7C3756)(content)
      text = java.util.regex.Pattern.compile("&h\\(([0-9a-fA-F]{6})\\)\\(([^)]+)\\)").matcher(text).replaceAll(mr -> {
         String hex = mr.group(1);
         String content = mr.group(2);
         return toMinecraftHex(hex) + content + "§r";
      });
      // Support &h(7C3756)
      text = java.util.regex.Pattern.compile("&h\\(([0-9a-fA-F]{6})\\)").matcher(text).replaceAll(mr -> toMinecraftHex(mr.group(1)));
      // Support &h7C3756
      text = java.util.regex.Pattern.compile("&h([0-9a-fA-F]{6})").matcher(text).replaceAll(mr -> toMinecraftHex(mr.group(1)));
      // Support &#7C3756
      text = java.util.regex.Pattern.compile("&#([0-9a-fA-F]{6})").matcher(text).replaceAll(mr -> toMinecraftHex(mr.group(1)));
      // If user typed standalone #RRGGBB (e.g. /bc #7C3756), color that hex code in its own color
      text = java.util.regex.Pattern.compile("(?<![§&0-9a-fA-F])#([0-9a-fA-F]{6})(?![0-9a-fA-F])").matcher(text).replaceAll(mr -> {
         String hex = mr.group(1);
         return toMinecraftHex(hex) + "#" + hex + "§r";
      });
      return text;
   }

   public static String toMinecraftHex(String hex) {
      StringBuilder hexCode = new StringBuilder("§x");
      for (int i = 0; i < 6; i++) {
         hexCode.append('§').append(Character.toLowerCase(hex.charAt(i)));
      }
      return hexCode.toString();
   }

   private static String processSpeedParenthesesBlocks(String msg, String code, int speed) {
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
         result.append(applySpeedMarker(inner, speed)).append("§r");
         end = closeParen + 1;
      }
      return result.toString();
   }

   private static String processSpeedBlocksStrict(String msg, String code, int speed) {
      StringBuilder result = new StringBuilder();
      int end;
      for (int i = 0; i < msg.length(); i = end) {
         int idx = msg.indexOf(code, i);
         if (idx == -1) {
            result.append(msg.substring(i));
            break;
         }
         // Ensure not followed by another digit (e.g. &w8123 is &w with text 8123)
         if (idx + code.length() < msg.length() && Character.isDigit(msg.charAt(idx + code.length()))) {
            result.append(msg, i, idx + code.length());
            end = idx + code.length();
            continue;
         }

         result.append(msg, i, idx);
         int start = idx + code.length();
         end = findEnd(msg, start);
         String inner = msg.substring(start, end);
         result.append(applySpeedMarker(inner, speed));
      }
      return result.toString();
   }

   private static String applySpeedMarker(String text, int speed) {
      if (text == null || text.isEmpty()) return text;
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < text.length(); ++i) {
         char c = text.charAt(i);
         if (c == ' ') {
            sb.append(c);
         } else {
            int offsetIdx = i % 16;
            String marker = "§x§f§f§0§" + Integer.toHexString(offsetIdx) + "§f§" + Math.min(9, Math.max(1, speed));
            sb.append(marker).append(c);
         }
      }
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
               int offsetIdx = i % 16;
               String marker = "§x§f§f§0§" + Integer.toHexString(offsetIdx) + "§f§e";
               sb.append(marker).append(c);
            }
         }

         return sb.toString();
      } else {
         return text;
      }
   }

   static String applyStaticRainbow(String text) {
      if (text != null && !text.isEmpty()) {
         // Apply smooth wave marker with slow relaxed speed for &q as well
         return applySpeedMarker(text, 1);
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

   public static net.minecraft.network.chat.MutableComponent parseFormattedText(String text) {
      if (text == null || text.isEmpty()) return net.minecraft.network.chat.Component.empty();
      net.minecraft.network.chat.MutableComponent root = net.minecraft.network.chat.Component.empty();
      
      net.minecraft.network.chat.Style currentStyle = net.minecraft.network.chat.Style.EMPTY;
      StringBuilder currentText = new StringBuilder();
      
      int len = text.length();
      int i = 0;
      while (i < len) {
         char c = text.charAt(i);
         if ((c == '&' || c == '§') && i + 1 < len && text.charAt(i + 1) == '#' && i + 7 < len) {
            int startHex = i + 2;
            if (startHex + 6 <= len) {
               String potentialHex = text.substring(startHex, startHex + 6);
               if (potentialHex.matches("(?i)[0-9a-f]{6}")) {
                  if (currentText.length() > 0) {
                     root.append(net.minecraft.network.chat.Component.literal(currentText.toString()).setStyle(currentStyle));
                     currentText.setLength(0);
                  }
                  try {
                     int rgb = Integer.parseInt(potentialHex, 16);
                     currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
                  } catch (Exception ignored) {}
                  i = startHex + 6;
                  continue;
               }
            }
         }

         if ((c == '§' || c == '&') && i + 1 < len) {
            char code = Character.toLowerCase(text.charAt(i + 1));
            
            // Flexible check for &x / §x hex codes (handles &x&f&f&f&7&4&d, &x&f&f&f&74&d, &xfff74d, etc.)
            if (code == 'x') {
               int scan = i + 2;
               StringBuilder hexDigits = new StringBuilder();
               while (scan < len && hexDigits.length() < 6) {
                  char sc = text.charAt(scan);
                  if (sc == '§' || sc == '&') {
                     scan++;
                     continue;
                  }
                  if ((sc >= '0' && sc <= '9') || (sc >= 'a' && sc <= 'f') || (sc >= 'A' && sc <= 'F')) {
                     hexDigits.append(sc);
                     scan++;
                  } else {
                     break;
                  }
               }
               if (hexDigits.length() == 6) {
                  if (currentText.length() > 0) {
                     root.append(net.minecraft.network.chat.Component.literal(currentText.toString()).setStyle(currentStyle));
                     currentText.setLength(0);
                  }
                  try {
                     int rgb = Integer.parseInt(hexDigits.toString(), 16);
                     currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb));
                  } catch (Exception ignored) {}
                  i = scan;
                  continue;
               }
            }
            
            // Standard formatting code
            if (currentText.length() > 0) {
               root.append(net.minecraft.network.chat.Component.literal(currentText.toString()).setStyle(currentStyle));
               currentText.setLength(0);
            }
            
            switch (code) {
               case '0' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x000000)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '1' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x0000AA)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '2' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x00AA00)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '3' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x00AAAA)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '4' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAA0000)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '5' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAA00AA)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '6' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFAA00)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '7' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '8' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x555555)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case '9' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x5555FF)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'a' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x55FF55)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'b' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x55FFFF)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'c' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF5555)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'd' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF55FF)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'e' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF55)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'f' -> currentStyle = currentStyle.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFFFF)).withBold(false).withItalic(false).withUnderlined(false).withStrikethrough(false).withObfuscated(false);
               case 'k' -> currentStyle = currentStyle.withObfuscated(true);
               case 'l' -> currentStyle = currentStyle.withBold(true);
               case 'm' -> currentStyle = currentStyle.withStrikethrough(true);
               case 'n' -> currentStyle = currentStyle.withUnderlined(true);
               case 'o' -> currentStyle = currentStyle.withItalic(true);
               case 'r' -> currentStyle = net.minecraft.network.chat.Style.EMPTY;
            }
            i += 2;
         } else {
            currentText.append(c);
            i++;
         }
      }
      
      if (currentText.length() > 0) {
         root.append(net.minecraft.network.chat.Component.literal(currentText.toString()).setStyle(currentStyle));
      }
      
      return root;
   }

   private static boolean isFormatCode(String s) {
      if (s.length() == 2 && (s.charAt(0) == '&' || s.charAt(0) == '§')) {
         char c = Character.toLowerCase(s.charAt(1));
         return "0123456789abcdefklmnorx".indexOf(c) != -1 || c == 'w' || c == 'q';
      } else {
         return false;
      }
   }
}
