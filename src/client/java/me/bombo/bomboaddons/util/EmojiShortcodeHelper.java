package me.bombo.bomboaddons.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmojiShortcodeHelper {
   private static final Map<String, String> EMOJIS = new HashMap<>();
   private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":([a-zA-Z0-9_+-]+):");

   static {
      // Faces & Smileys
      EMOJIS.put("pleading_face", "🥺");
      EMOJIS.put("pleading", "🥺");
      EMOJIS.put("sob", "😭");
      EMOJIS.put("cry", "😢");
      EMOJIS.put("joy", "😂");
      EMOJIS.put("rofl", "🤣");
      EMOJIS.put("smile", "😄");
      EMOJIS.put("smiley", "😃");
      EMOJIS.put("grinning", "😀");
      EMOJIS.put("blush", "😊");
      EMOJIS.put("heart_eyes", "😍");
      EMOJIS.put("kissing_heart", "😘");
      EMOJIS.put("wink", "😉");
      EMOJIS.put("sweat_smile", "😅");
      EMOJIS.put("relieved", "😌");
      EMOJIS.put("sunglasses", "😎");
      EMOJIS.put("smirk", "😏");
      EMOJIS.put("neutral_face", "😐");
      EMOJIS.put("expressionless", "😑");
      EMOJIS.put("unamused", "😒");
      EMOJIS.put("sweat", "😓");
      EMOJIS.put("pensive", "😔");
      EMOJIS.put("confused", "😕");
      EMOJIS.put("upside_down", "🙃");
      EMOJIS.put("money_mouth", "🤑");
      EMOJIS.put("astonished", "😲");
      EMOJIS.put("flushed", "😳");
      EMOJIS.put("scream", "😱");
      EMOJIS.put("fearful", "😨");
      EMOJIS.put("rage", "😡");
      EMOJIS.put("angry", "😠");
      EMOJIS.put("skull", "💀");
      EMOJIS.put("clown", "🤡");
      EMOJIS.put("poop", "💩");
      EMOJIS.put("ghost", "👻");
      EMOJIS.put("alien", "👽");
      EMOJIS.put("robot", "🤖");
      EMOJIS.put("thinking", "🤔");
      EMOJIS.put("salute", "🫡");
      EMOJIS.put("melt", "🫠");
      EMOJIS.put("melting_face", "🫠");
      EMOJIS.put("eyes", "👀");
      EMOJIS.put("100", "💯");

      // Hearts & Symbols
      EMOJIS.put("heart", "❤️");
      EMOJIS.put("red_heart", "❤️");
      EMOJIS.put("broken_heart", "💔");
      EMOJIS.put("orange_heart", "🧡");
      EMOJIS.put("yellow_heart", "💛");
      EMOJIS.put("green_heart", "💚");
      EMOJIS.put("blue_heart", "💙");
      EMOJIS.put("purple_heart", "💜");
      EMOJIS.put("black_heart", "🖤");
      EMOJIS.put("white_heart", "🤍");
      EMOJIS.put("sparkles", "✨");
      EMOJIS.put("star", "⭐");
      EMOJIS.put("fire", "🔥");
      EMOJIS.put("flame", "🔥");
      EMOJIS.put("zap", "⚡");
      EMOJIS.put("boom", "💥");
      EMOJIS.put("collision", "💥");

      // Hands & Gestures
      EMOJIS.put("thumbsup", "👍");
      EMOJIS.put("+1", "👍");
      EMOJIS.put("thumbsdown", "👎");
      EMOJIS.put("-1", "👎");
      EMOJIS.put("ok_hand", "👌");
      EMOJIS.put("wave", "👋");
      EMOJIS.put("clap", "👏");
      EMOJIS.put("pray", "🙏");
      EMOJIS.put("raised_hands", "🙌");
      EMOJIS.put("point_right", "👉");
      EMOJIS.put("point_left", "👈");
      EMOJIS.put("point_up", "👆");
      EMOJIS.put("point_down", "👇");
      EMOJIS.put("handshake", "🤝");
      EMOJIS.put("muscle", "💪");

      // Utility & Marks
      EMOJIS.put("check", "✔");
      EMOJIS.put("white_check_mark", "✅");
      EMOJIS.put("x", "❌");
      EMOJIS.put("cross", "❌");
      EMOJIS.put("warning", "⚠️");
      EMOJIS.put("no_entry", "⛔");
      EMOJIS.put("question", "❓");
      EMOJIS.put("exclamation", "❗");
      EMOJIS.put("gem", "💎");
      EMOJIS.put("diamond", "💎");
      EMOJIS.put("crown", "👑");
      EMOJIS.put("trophy", "🏆");
      EMOJIS.put("medal", "🏅");
      EMOJIS.put("first_place", "🥇");
      EMOJIS.put("sword", "⚔️");
      EMOJIS.put("shield", "🛡️");
      EMOJIS.put("bow", "🏹");
      EMOJIS.put("cookie", "🍪");
      EMOJIS.put("cake", "🍰");
      EMOJIS.put("coffee", "☕");
      EMOJIS.put("beer", "🍺");
      EMOJIS.put("pizza", "🍕");
      EMOJIS.put("apple", "🍎");
   }

   public static String replaceEmojis(String text) {
      if (text == null || !text.contains(":")) return text;
      Matcher m = SHORTCODE_PATTERN.matcher(text);
      StringBuilder sb = new StringBuilder();
      int lastIdx = 0;
      while (m.find()) {
         String key = m.group(1).toLowerCase(java.util.Locale.ROOT);
         String emoji = EMOJIS.get(key);
         if (emoji != null) {
            sb.append(text, lastIdx, m.start());
            sb.append(emoji);
            lastIdx = m.end();
         }
      }
      if (lastIdx == 0) return text;
      sb.append(text.substring(lastIdx));
      return sb.toString();
   }

   public static net.minecraft.network.chat.Component replaceEmojisInComponent(net.minecraft.network.chat.Component component) {
      if (component == null) return null;
      String raw = component.getString();
      if (!raw.contains(":")) return component;
      net.minecraft.network.chat.MutableComponent result = net.minecraft.network.chat.Component.empty();
      component.visit((style, text) -> {
         if (text.isEmpty()) return java.util.Optional.empty();
         String replaced = replaceEmojis(text);
         result.append(net.minecraft.network.chat.Component.literal(replaced).withStyle(style));
         return java.util.Optional.empty();
      }, net.minecraft.network.chat.Style.EMPTY);
      return result;
   }
}
