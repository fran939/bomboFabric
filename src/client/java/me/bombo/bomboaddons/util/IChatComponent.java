package me.bombo.bomboaddons.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import java.util.Objects;

public interface IChatComponent {
    GuiMessage.Line bombo$getLineAt(double mouseX, double mouseY);
    java.util.List<GuiMessage.Line> bombo$getFullMessageLines(GuiMessage.Line clickedLine);
    double bombo$getScale();

    static Style getStyleAt(Minecraft mc, GuiMessage.Line line, double mouseX, double scale) {
        double d = mouseX - 2.0;
        d = d / scale;
        int targetWidth = (int)Math.round(d);
        if (targetWidth < 0) return null;
        
        final float[] currentWidth = { 0.0f };
        final Style[] clickedStyle = { null };
        line.content().accept((index, style, codePoint) -> {
            String charStr = new String(Character.toChars(codePoint));
            float charWidth = mc.font.width(charStr);
            if (targetWidth >= currentWidth[0] && targetWidth <= currentWidth[0] + charWidth) {
                clickedStyle[0] = style;
                return false;
            }
            currentWidth[0] += charWidth;
            return true;
        });
        return clickedStyle[0];
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
        final Style[] lastStyle = { null };
        seq.accept((index, style, codePoint) -> {
            if (lastStyle[0] == null || !style.equals(lastStyle[0])) {
                boolean resetNeeded = false;
                if (lastStyle[0] != null) {
                    if (!Objects.equals(style.getColor(), lastStyle[0].getColor())
                            || (lastStyle[0].isBold() && !style.isBold())
                            || (lastStyle[0].isItalic() && !style.isItalic())
                            || (lastStyle[0].isUnderlined() && !style.isUnderlined())
                            || (lastStyle[0].isStrikethrough() && !style.isStrikethrough())
                            || (lastStyle[0].isObfuscated() && !style.isObfuscated())) {
                        sb.append(colorChar).append('r');
                        resetNeeded = true;
                    }
                }
                
                if (style.getColor() != null && (resetNeeded || lastStyle[0] == null || !style.getColor().equals(lastStyle[0].getColor()))) {
                    char code = getColorCode(style.getColor());
                    if (code != ' ') {
                        sb.append(colorChar).append(code);
                    } else {
                        sb.append(colorChar).append("x");
                        String hex = String.format("%06x", style.getColor().getValue());
                        for (char c : hex.toCharArray()) {
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
        if (val == 0x000000) return '0';
        if (val == 0x0000AA) return '1';
        if (val == 0x00AA00) return '2';
        if (val == 0x00AAAA) return '3';
        if (val == 0xAA0000) return '4';
        if (val == 0xAA00AA) return '5';
        if (val == 0xFFAA00) return '6';
        if (val == 0xAAAAAA) return '7';
        if (val == 0x555555) return '8';
        if (val == 0x5555FF) return '9';
        if (val == 0x55FF55) return 'a';
        if (val == 0x55FFFF) return 'b';
        if (val == 0xFF5555) return 'c';
        if (val == 0xFF55FF) return 'd';
        if (val == 0xFFFF55) return 'e';
        if (val == 0xFFFFFF) return 'f';
        
        String name = color.serialize();
        if (name != null) {
            switch (name) {
                case "black": return '0';
                case "dark_blue": return '1';
                case "dark_green": return '2';
                case "dark_aqua": return '3';
                case "dark_red": return '4';
                case "dark_purple": return '5';
                case "gold": return '6';
                case "gray": return '7';
                case "dark_gray": return '8';
                case "blue": return '9';
                case "green": return 'a';
                case "aqua": return 'b';
                case "red": return 'c';
                case "light_purple": return 'd';
                case "yellow": return 'e';
                case "white": return 'f';
            }
        }
        return ' ';
    }
}
