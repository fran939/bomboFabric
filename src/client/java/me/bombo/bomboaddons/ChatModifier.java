package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

public class ChatModifier {
   private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons/chat_modifier.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   public static final List<ChatRule> rules = new CopyOnWriteArrayList<>();

   public static class ChatRule {
      public String pattern = "";
      public String replacement = "";
      public boolean hideMessage = false;
      public boolean isRegex = false;
      public boolean enabled = true;

      public ChatRule() {
      }

      public ChatRule(String pattern, String replacement, boolean hideMessage, boolean isRegex, boolean enabled) {
         this.pattern = pattern;
         this.replacement = replacement;
         this.hideMessage = hideMessage;
         this.isRegex = isRegex;
         this.enabled = enabled;
      }
   }

   public static void load() {
      try {
         if (Files.exists(FILE_PATH, new LinkOption[0])) {
            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
               List<ChatRule> loaded = GSON.fromJson(reader, new TypeToken<List<ChatRule>>(){}.getType());
               if (loaded != null) {
                  rules.clear();
                  rules.addAll(loaded);
               }
            }
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   public static void save() {
      try {
         if (!Files.exists(FILE_PATH.getParent(), new LinkOption[0])) {
            Files.createDirectories(FILE_PATH.getParent());
         }
         try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
            GSON.toJson(new ArrayList<>(rules), writer);
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   public static boolean shouldHide(String rawText) {
      if (rawText == null || rawText.isEmpty()) return false;
      String cleanText = rawText.replaceAll("§[0-9a-fk-orxX]", "");
      for (ChatRule rule : rules) {
         if (!rule.enabled || !rule.hideMessage || rule.pattern == null || rule.pattern.isEmpty()) continue;
         try {
            if (rule.isRegex) {
               if (Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE).matcher(cleanText).find() ||
                   Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE).matcher(rawText).find()) {
                  return true;
               }
            } else {
               if (cleanText.toLowerCase().contains(rule.pattern.toLowerCase()) || rawText.contains(rule.pattern)) {
                  return true;
               }
            }
         } catch (Throwable ignored) {}
      }
      return false;
   }

   public static String modifyText(String text) {
      if (text == null || text.isEmpty()) return text;
      String current = text;
      for (ChatRule rule : rules) {
         if (!rule.enabled || rule.hideMessage || rule.pattern == null || rule.pattern.isEmpty()) continue;
         try {
            String replacement = rule.replacement != null ? rule.replacement.replace('&', '§') : "";
            
            if (rule.isRegex) {
               current = current.replaceAll("(?i)" + rule.pattern, replacement);
            } else if (rule.pattern.contains("${")) {
               // Support wildcard/template placeholders like ${1}, ${x}, ${any}
               // Example: "x: ${1}, y: ${2}, z: ${3}" -> converts to regex and replaces with capture groups
               String regexPattern = convertTemplateToRegex(rule.pattern);
               String regexReplacement = convertTemplateReplacement(replacement);
               current = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE)
                               .matcher(current)
                               .replaceAll(regexReplacement);
            } else {
               current = Pattern.compile(Pattern.quote(rule.pattern), Pattern.CASE_INSENSITIVE)
                               .matcher(current)
                               .replaceAll(Matcher.quoteReplacement(replacement));
            }
         } catch (Throwable ignored) {}
      }
      return current;
   }

   private static String convertTemplateToRegex(String template) {
      StringBuilder sb = new StringBuilder();
      int i = 0;
      while (i < template.length()) {
         int start = template.indexOf("${", i);
         if (start == -1) {
            sb.append(Pattern.quote(template.substring(i)));
            break;
         }
         if (start > i) {
            sb.append(Pattern.quote(template.substring(i, start)));
         }
         int end = template.indexOf('}', start);
         if (end != -1) {
            // Capture anything lazily until next literal text
            sb.append("(.*?)");
            i = end + 1;
         } else {
            sb.append(Pattern.quote(template.substring(start)));
            break;
         }
      }
      return sb.toString();
   }

   private static String convertTemplateReplacement(String replacement) {
      // Convert ${1} to $1, ${2} to $2, etc.
      return replacement.replaceAll("\\$\\{([0-9]+)\\}", "\\$$1");
   }
}
