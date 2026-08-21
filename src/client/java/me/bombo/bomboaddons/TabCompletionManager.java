package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public class TabCompletionManager {
   public static final Set<String> friends = Collections.newSetFromMap(new ConcurrentHashMap());
   public static final Set<String> guild = Collections.newSetFromMap(new ConcurrentHashMap());
   public static final Set<String> party = Collections.newSetFromMap(new ConcurrentHashMap());
   private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("bombo/autocomplete.json");
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();

   public static void load() {
      try {
         if (Files.exists(FILE_PATH, new LinkOption[0])) {
            Reader reader = Files.newBufferedReader(FILE_PATH);

            try {
               AutocompleteData data = (AutocompleteData)GSON.fromJson(reader, AutocompleteData.class);
               if (data != null) {
                  if (data.friends != null) {
                     friends.addAll(data.friends);
                  }

                  if (data.guild != null) {
                     guild.addAll(data.guild);
                  }
               }
            } catch (Throwable var4) {
               if (reader != null) {
                  try {
                     reader.close();
                  } catch (Throwable var3) {
                     var4.addSuppressed(var3);
                  }
               }

               throw var4;
            }

            if (reader != null) {
               reader.close();
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

         AutocompleteData data = new AutocompleteData();
         data.friends.addAll(friends);
         data.guild.addAll(guild);
         Writer writer = Files.newBufferedWriter(FILE_PATH);

         try {
            GSON.toJson(data, writer);
         } catch (Throwable var5) {
            if (writer != null) {
               try {
                  writer.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (writer != null) {
            writer.close();
         }
      } catch (Throwable t) {
         t.printStackTrace();
      }

   }

   public static void onChatMessage(String rawMessage) {
      if (rawMessage != null) {
         String clean = rawMessage.replaceAll("§.", "").trim();
         if (!clean.isEmpty()) {
            String[] lines = clean.split("\n");

            for(String line : lines) {
               String trimmed = line.trim();
               if (!trimmed.isEmpty()) {
                  String trimmedLower = trimmed.toLowerCase();
                  Matcher mJoinLeave = Pattern.compile("^(Friends|Guild)\\s*>\\s*(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s+(joined|left)\\.", 2).matcher(trimmed);
                  if (mJoinLeave.find()) {
                     String type = mJoinLeave.group(1).toLowerCase();
                     String name = mJoinLeave.group(2);
                     if (type.equals("friends")) {
                        if (friends.add(name)) {
                           save();
                        }
                     } else if (type.equals("guild") && guild.add(name)) {
                        save();
                     }
                  } else {
                     Matcher mFriendEntry = Pattern.compile("^([a-zA-Z0-9_]{3,16})\\*?\\s+is\\s+(?:in|currently\\s+offline)", 2).matcher(trimmed);
                     if (mFriendEntry.find()) {
                        if (friends.add(mFriendEntry.group(1))) {
                           save();
                        }
                     } else if (trimmed.contains("●") && !trimmedLower.startsWith("party leader:") && !trimmedLower.startsWith("party moderators:") && !trimmedLower.startsWith("party members:")) {
                        String[] parts = trimmed.split("●");
                        boolean addedAny = false;

                        for(String part : parts) {
                           String name = part.replaceAll("\\[[^\\]]+\\]", "").trim();
                           if (name.matches("^[a-zA-Z0-9_]{3,16}$") && guild.add(name)) {
                              addedAny = true;
                           }
                        }

                        if (addedAny) {
                           save();
                        }
                     } else if (!trimmedLower.startsWith("party leader:") && !trimmedLower.startsWith("party moderators:") && !trimmedLower.startsWith("party members:")) {
                        if (trimmedLower.contains("you have joined") && trimmedLower.contains("'s party!")) {
                           party.clear();
                           Matcher m = Pattern.compile("you\\s+have\\s+joined\\s*(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})'s\\s+party", 2).matcher(trimmed);
                           if (m.find()) {
                              party.add(m.group(1));
                           }

                           Minecraft mc = Minecraft.getInstance();
                           if (mc.player != null) {
                              party.add(mc.player.getGameProfile().name());
                           }
                        } else if (trimmedLower.endsWith("has joined the party.")) {
                           Matcher m = Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s+has\\s+joined\\s+the\\s+party\\.", 2).matcher(trimmed);
                           if (m.find()) {
                              party.add(m.group(1));
                           }
                        } else if (!trimmedLower.endsWith("left the party.") && !trimmedLower.endsWith("has been removed from the party.")) {
                           if (trimmedLower.contains("the party was disbanded") || trimmedLower.contains("you left the party") || trimmedLower.contains("you are not currently in a party")) {
                              party.clear();
                           }
                        } else {
                           Matcher m = Pattern.compile("^(?:\\[[^\\]]+\\]\\s*)?([a-zA-Z0-9_]{3,16})\\s+(?:left\\s+the\\s+party|has\\s+been\\s+removed)", 2).matcher(trimmed);
                           if (m.find()) {
                              party.remove(m.group(1));
                           }
                        }
                     } else {
                        int colonIdx = trimmed.indexOf(":");
                        if (colonIdx != -1) {
                           String content = trimmed.substring(colonIdx + 1);
                           content = content.replace("●", " ");
                           content = content.replaceAll("\\[[^\\]]+\\]", " ");
                           String[] words = content.split("[,\\s]+");

                           for(String word : words) {
                              String w = word.trim();
                              if (w.matches("^[a-zA-Z0-9_]{3,16}$")) {
                                 party.add(w);
                              }
                           }
                        }
                     }
                  }
               }
            }

         }
      }
   }

   public static CompletableFuture<Suggestions> getUsernameSuggestions(CommandContext<?> context, SuggestionsBuilder builder) {
      String fullRemaining = builder.getRemaining();
      int lastSpaceIndex = fullRemaining.lastIndexOf(32);
      String remaining;
      SuggestionsBuilder actualBuilder;
      if (lastSpaceIndex != -1) {
         remaining = fullRemaining.substring(lastSpaceIndex + 1).toLowerCase();
         actualBuilder = builder.createOffset(builder.getStart() + lastSpaceIndex + 1);
      } else {
         remaining = fullRemaining.toLowerCase();
         actualBuilder = builder;
      }

      Set<String> suggestions = new TreeSet(String.CASE_INSENSITIVE_ORDER);

      try {
         Minecraft mc = Minecraft.getInstance();
         if (mc.getConnection() != null) {
            for(PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
               String name = info.getProfile().name();
               if (name != null && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
                  suggestions.add(name);
               }
            }
         }

         if (mc.player != null) {
            String selfName = mc.player.getGameProfile().name();
            if (selfName != null && selfName.matches("^[a-zA-Z0-9_]{3,16}$")) {
               suggestions.add(selfName);
            }
         }
      } catch (Throwable var11) {
      }

      suggestions.addAll(friends);
      suggestions.addAll(guild);
      suggestions.addAll(party);

      if (lastSpaceIndex == -1) {
         String input = builder.getInput().trim().toLowerCase();
         if (input.startsWith("/f ") || input.startsWith("f ") || input.startsWith("/friend ") || input.startsWith("friend ")) {
            suggestions.add("add");
            suggestions.add("remove");
            suggestions.add("list");
            suggestions.add("accept");
            suggestions.add("deny");
            suggestions.add("toggle");
            suggestions.add("help");
            suggestions.add("requests");
            suggestions.add("notifications");
            suggestions.add("removeall");
            suggestions.add("best");
            suggestions.add("unbest");
         } else if (input.startsWith("/p ") || input.startsWith("p ") || input.startsWith("/party ") || input.startsWith("party ")) {
            suggestions.add("invite");
            suggestions.add("kick");
            suggestions.add("disband");
            suggestions.add("leave");
            suggestions.add("transfer");
            suggestions.add("promote");
            suggestions.add("demote");
            suggestions.add("warp");
            suggestions.add("poll");
            suggestions.add("settings");
            suggestions.add("list");
            suggestions.add("help");
            suggestions.add("private");
            suggestions.add("chat");
         }
      }

      for(String name : suggestions) {
         if (name.toLowerCase().startsWith(remaining) && name.matches("^[a-zA-Z0-9_]{3,16}$")) {
            actualBuilder.suggest(name);
         }
      }

      return actualBuilder.buildFuture();
   }

   public static class AutocompleteData {
      public Set<String> friends = new HashSet();
      public Set<String> guild = new HashSet();
   }
}
