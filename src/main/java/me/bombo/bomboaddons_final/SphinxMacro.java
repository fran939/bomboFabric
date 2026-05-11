package me.bombo.bomboaddons_final;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class SphinxMacro {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final File OLD_DATA_FILE = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons_sphinx.json").toFile();
   private static final File DATA_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons_sphinx.json").toFile();
   private static final Map<String, String> QUESTIONS = new HashMap();
   private static boolean inBlock = false;
   private static String currentQuestion = null;
   private static final String[] options = new String[3];
   private static boolean answered = false;

   public static void onChatMessage(String rawMessage) {
      if (BomboConfig.get().sphinxMacro) {
         String raw = removeFormatting(rawMessage).trim();
         if (!raw.isEmpty()) {
            if ("Question".equalsIgnoreCase(raw)) {
               resetBlock();
               inBlock = true;
               answered = false;
            } else if (inBlock) {
               if (currentQuestion == null && !raw.equalsIgnoreCase("Question")) {
                  currentQuestion = raw;
               } else if (isOptionLine(raw)) {
                  parseOption(raw);
               } else if (raw.toLowerCase().contains("click your response to answer")) {
                  if (!answered && options[0] != null && options[1] != null && options[2] != null) {
                     tryAnswerAndSend();
                     answered = true;
                  } else if (!answered) {
                     sendMessage("§e[Bombo] Incomplete Sphinx question detected, skipping...");
                  }

                  resetBlock();
               }
            }

         }
      }
   }

   private static void tryAnswerAndSend() {
      if (currentQuestion != null) {
         String expected = (String)QUESTIONS.get(currentQuestion);
         if (expected != null && !expected.trim().isEmpty()) {
            String normExpected = norm(expected);
            int matchedIdx = -1;

            int i;
            String opt;
            for(i = 0; i < 3; ++i) {
               opt = options[i];
               if (opt != null && norm(opt).equals(normExpected)) {
                  matchedIdx = i;
                  break;
               }
            }

            if (matchedIdx == -1) {
               for(i = 0; i < 3; ++i) {
                  opt = options[i];
                  if (opt != null) {
                     String n = norm(opt);
                     if (n.contains(normExpected) || normExpected.contains(n)) {
                        matchedIdx = i;
                        break;
                     }
                  }
               }
            }

            if (matchedIdx >= 0) {
               char letter = (char)(65 + matchedIdx);
               sendMessage("§a[Bombo] > Answer is " + expected + " (" + letter + ")");
               Minecraft.getInstance().player.connection.sendCommand("sphinxanswer " + matchedIdx);
            } else {
               sendMessage("§c[Bombo] Error: No matching answer found for: §f" + expected);
            }
         } else {
            sendMessage("§c[Bombo] Error: Unknown Sphinx question: §f" + currentQuestion);
         }
      }

   }

   private static boolean isOptionLine(String raw) {
      if (raw.length() < 3) {
         return false;
      } else {
         char c = Character.toUpperCase(raw.charAt(0));
         if (c != 'A' && c != 'B' && c != 'C') {
            return false;
         } else {
            int closeIdx = raw.indexOf(41);
            return closeIdx > 0 && closeIdx <= 3;
         }
      }
   }

   private static void parseOption(String raw) {
      char label = Character.toUpperCase(raw.charAt(0));
      int idx = -1;
      if (label == 'A') {
         idx = 0;
      } else if (label == 'B') {
         idx = 1;
      } else if (label == 'C') {
         idx = 2;
      }

      if (idx != -1) {
         int close = raw.indexOf(41);
         String value = raw.substring(close + 1).trim();
         if (value.startsWith("-")) {
            value = value.substring(1).trim();
         }

         if (value.startsWith(":")) {
            value = value.substring(1).trim();
         }

         options[idx] = value;
      }

   }

   private static void resetBlock() {
      inBlock = false;
      currentQuestion = null;
      options[0] = options[1] = options[2] = null;
   }

   private static String norm(String s) {
      String t = s.toLowerCase().replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"');
      StringBuilder sb = new StringBuilder();
      boolean lastSpace = false;

      for(int i = 0; i < t.length(); ++i) {
         char ch = t.charAt(i);
         if (Character.isLetterOrDigit(ch)) {
            sb.append(ch);
            lastSpace = false;
         } else if (Character.isWhitespace(ch)) {
            if (!lastSpace) {
               sb.append(' ');
            }

            lastSpace = true;
         }
      }

      return sb.toString().trim();
   }

   private static String removeFormatting(String s) {
      return s.replaceAll("(?i)§[0-9a-fk-or]", "");
   }

   private static void sendMessage(String msg) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.displayClientMessage(Component.literal(msg), false);
      }

   }

   public static void load() {
      if (OLD_DATA_FILE.exists()) {
         try {
            if (!DATA_FILE.getParentFile().exists()) DATA_FILE.getParentFile().mkdirs();
            java.nio.file.Files.move(OLD_DATA_FILE.toPath(), DATA_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
      if (DATA_FILE.exists()) {
         try {
            InputStreamReader r = new InputStreamReader(Files.newInputStream(DATA_FILE.toPath()), StandardCharsets.UTF_8);

            try {
               Map<String, String> read = (Map)GSON.fromJson(r, (new TypeToken<Map<String, String>>() {
               }).getType());
               if (read != null) {
                  QUESTIONS.clear();
                  QUESTIONS.putAll(read);
               }
            } catch (Throwable var4) {
               try {
                  r.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }

               throw var4;
            }

            r.close();
         } catch (IOException var5) {
            var5.printStackTrace();
         }
      }

   }

   public static void save() {
      try {
         OutputStreamWriter w = new OutputStreamWriter(Files.newOutputStream(DATA_FILE.toPath()), StandardCharsets.UTF_8);

         try {
            GSON.toJson(QUESTIONS, w);
         } catch (Throwable var4) {
            try {
               w.close();
            } catch (Throwable var3) {
               var4.addSuppressed(var3);
            }

            throw var4;
         }

         w.close();
      } catch (IOException var5) {
         var5.printStackTrace();
      }

   }

   public static void addQuestion(String question, String answer) {
      QUESTIONS.put(question, answer);
      save();
   }

   public static boolean removeQuestion(String question) {
      boolean removed = QUESTIONS.remove(question) != null;
      if (removed) {
         save();
      }

      return removed;
   }

   public static Map<String, String> getQuestions() {
      return QUESTIONS;
   }

   public static int addDefaultQuestions() {
      Map<String, String> defaults = new LinkedHashMap();
      defaults.put("Which of these is NOT a pet?", "Slime");
      defaults.put("What type of mob is exclusive to the Fishing Festival?", "Shark");
      defaults.put("Where is Trevor the Trapper found?", "Mushroom Desert");
      defaults.put("Who helps you apply Rod Parts?", "Roddy");
      defaults.put("Which type of Gemstone has the lowest Breaking Power?", "Ruby");
      defaults.put("Which item rarity comes after Mythic?", "Divine");
      defaults.put("How do you obtain the Dark Purple Dye?", "Dark Auction");
      defaults.put("Who runs the Chocolate Factory?", "Hoppity");
      defaults.put("How many floors are there in The Catacombs?", "7");
      defaults.put("What is the first type of slayer Maddox offers?", "Zombie");
      defaults.put("What item do you use to kill Pests?", "Vacuum");
      defaults.put("Who owns the Gold Essence Shop?", "Marigold");
      defaults.put("Which of these is NOT a type of Gemstone?", "Prismite");
      defaults.put("What does Junker Joel collect?", "Junk");
      defaults.put("Where is the Titanoboa found?", "Backwater Bayou");
      int changed = 0;
      Iterator var2 = defaults.entrySet().iterator();

      while(var2.hasNext()) {
         Entry<String, String> e = (Entry)var2.next();
         if (!QUESTIONS.containsKey(e.getKey())) {
            QUESTIONS.put((String)e.getKey(), (String)e.getValue());
            ++changed;
         }
      }

      if (changed > 0) {
         save();
      }

      return changed;
   }

   static {
      load();
      if (QUESTIONS.isEmpty()) {
         addDefaultQuestions();
      }

   }
}
