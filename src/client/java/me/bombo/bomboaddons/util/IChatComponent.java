package me.bombo.bomboaddons.util;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;

public interface IChatComponent {
   GuiMessage.Line bombo$getLineAt(double var1, double var3);

   List<GuiMessage.Line> bombo$getFullMessageLines(GuiMessage.Line var1);

   List<GuiMessage> bombo$getAllMessages();

   double bombo$getScale();

   void bombo$removeProfileListMessages();

   static Style getStyleAt(Minecraft mc, GuiMessage.Line line, double mouseX, double scale) {
      double d = mouseX - (double)2.0F;
      d /= scale;
      int targetWidth = (int)Math.round(d);
      if (targetWidth < 0) {
         return null;
      } else {
         float[] currentWidth = new float[]{0.0F};
         Style[] clickedStyle = new Style[]{null};
         line.content().accept((index, style, codePoint) -> {
            String charStr = new String(Character.toChars(codePoint));
            float charWidth = (float)mc.font.width(charStr);
            if ((float)targetWidth >= currentWidth[0] && (float)targetWidth <= currentWidth[0] + charWidth) {
               clickedStyle[0] = style;
               return false;
            } else {
               currentWidth[0] += charWidth;
               return true;
            }
         });
         return clickedStyle[0];
      }
   }

   static String getLinePlainText(FormattedCharSequence seq) {
      StringBuilder sb = new StringBuilder();
      seq.accept((index, style, codePoint) -> {
         sb.appendCodePoint(codePoint);
         return true;
      });
      return sb.toString();
   }

   static String getLineWithFormatting(FormattedCharSequence seq, char colorChar) {
      StringBuilder sb = new StringBuilder();
      Style[] lastStyle = new Style[]{null};
      seq.accept((index, style, codePoint) -> {
         if (lastStyle[0] == null || !style.equals(lastStyle[0])) {
            boolean resetNeeded = false;
            if (lastStyle[0] != null && (!Objects.equals(style.getColor(), lastStyle[0].getColor()) || lastStyle[0].isBold() && !style.isBold() || lastStyle[0].isItalic() && !style.isItalic() || lastStyle[0].isUnderlined() && !style.isUnderlined() || lastStyle[0].isStrikethrough() && !style.isStrikethrough() || lastStyle[0].isObfuscated() && !style.isObfuscated())) {
               sb.append(colorChar).append('r');
               resetNeeded = true;
            }

            if (style.getColor() != null && (resetNeeded || lastStyle[0] == null || !style.getColor().equals(lastStyle[0].getColor()))) {
               char code = getColorCode(style.getColor());
               if (code != ' ') {
                  sb.append(colorChar).append(code);
               } else {
                  sb.append(colorChar).append("x");
                  String hex = String.format("%06x", style.getColor().getValue());

                  for(char c : hex.toCharArray()) {
                     sb.append(colorChar).append(c);
                  }
               }
            }

            if (style.isBold() && (resetNeeded || lastStyle[0] == null || !lastStyle[0].isBold())) {
               sb.append(colorChar).append('l');
            }

            if (style.isItalic() && (resetNeeded || lastStyle[0] == null || !lastStyle[0].isItalic())) {
               sb.append(colorChar).append('o');
            }

            if (style.isUnderlined() && (resetNeeded || lastStyle[0] == null || !lastStyle[0].isUnderlined())) {
               sb.append(colorChar).append('n');
            }

            if (style.isStrikethrough() && (resetNeeded || lastStyle[0] == null || !lastStyle[0].isStrikethrough())) {
               sb.append(colorChar).append('m');
            }

            if (style.isObfuscated() && (resetNeeded || lastStyle[0] == null || !lastStyle[0].isObfuscated())) {
               sb.append(colorChar).append('k');
            }

            lastStyle[0] = style;
         }

         sb.appendCodePoint(codePoint);
         return true;
      });
      return sb.toString();
   }

   static char getColorCode(TextColor color) {
      int val = color.getValue();
      if (val == 0) {
         return '0';
      } else if (val == 170) {
         return '1';
      } else if (val == 43520) {
         return '2';
      } else if (val == 43690) {
         return '3';
      } else if (val == 11141120) {
         return '4';
      } else if (val == 11141290) {
         return '5';
      } else if (val == 16755200) {
         return '6';
      } else if (val == 11184810) {
         return '7';
      } else if (val == 5592405) {
         return '8';
      } else if (val == 5592575) {
         return '9';
      } else if (val == 5635925) {
         return 'a';
      } else if (val == 5636095) {
         return 'b';
      } else if (val == 16733525) {
         return 'c';
      } else if (val == 16733695) {
         return 'd';
      } else if (val == 16777045) {
         return 'e';
      } else if (val == 16777215) {
         return 'f';
      } else {
         String name = color.serialize();
         if (name != null) {
            switch (name) {
               case "black" -> {
                  return '0';
               }
               case "dark_blue" -> {
                  return '1';
               }
               case "dark_green" -> {
                  return '2';
               }
               case "dark_aqua" -> {
                  return '3';
               }
               case "dark_red" -> {
                  return '4';
               }
               case "dark_purple" -> {
                  return '5';
               }
               case "gold" -> {
                  return '6';
               }
               case "gray" -> {
                  return '7';
               }
               case "dark_gray" -> {
                  return '8';
               }
               case "blue" -> {
                  return '9';
               }
               case "green" -> {
                  return 'a';
               }
               case "aqua" -> {
                  return 'b';
               }
               case "red" -> {
                  return 'c';
               }
               case "light_purple" -> {
                  return 'd';
               }
               case "yellow" -> {
                  return 'e';
               }
               case "white" -> {
                  return 'f';
               }
            }
         }

         return ' ';
      }
   }
}
