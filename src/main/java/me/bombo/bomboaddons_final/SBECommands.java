package me.bombo.bomboaddons_final;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Iterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;

@Environment(EnvType.CLIENT)
public class SBECommands {
   private static final HttpClient client = HttpClient.newBuilder().build();
   private static final String API_BASE = "https://sbecommands-api.icarusphantom.dev/v1/sbecommands/";

   public static void handleCommand(String command, String name, String profile) {
      if (!BomboConfig.get().sbeCommands) {
         sendMessage("§cSBEC Commands are currently disabled in /bombo!");
      } else {
         String var10000 = command.equals("skills") ? "skill" : command;
         String url = "https://sbecommands-api.icarusphantom.dev/v1/sbecommands/" + var10000 + "/" + name;
         if (profile != null && !profile.equalsIgnoreCase("selected")) {
            url = url + "/" + profile;
         }

         Minecraft mc = Minecraft.getInstance();
         String selfUuid = mc.getUser().getProfileId().toString();
         sendMessage("§7Fetching data for " + name + "...");
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
               .header("User-Agent", "Mozilla/5.0 (Bomboaddons 1.21.11) " + selfUuid).timeout(Duration.ofSeconds(60L))
               .GET().build();
         client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).thenAccept((body) -> {
            try {
               if (body == null || body.trim().isEmpty()) {
                  sendMessage("§cError: Empty response from API.");
                  return;
               }

               JsonElement element = JsonParser.parseString(body);
               if (!element.isJsonObject()) {
                  sendMessage("§cError: Unexpected response format from API.");
                  return;
               }

               JsonObject json = element.getAsJsonObject();
               if (json.has("error")) {
                  sendMessage("§cError: " + json.get("error").getAsString());
                  return;
               }

               if (!json.has("data") || json.get("data").isJsonNull()) {
                  sendMessage("§cError: No data returned from API for " + name + ".");
                  return;
               }

               JsonObject data = json.getAsJsonObject("data");
               byte var7 = -1;
               switch (command.hashCode()) {
                  case -1620171640:
                     if (command.equals("trophyfish")) {
                        var7 = 5;
                     }
                     break;
                  case -900562878:
                     if (command.equals("skills")) {
                        var7 = 2;
                     }
                     break;
                  case -899865410:
                     if (command.equals("slayer")) {
                        var7 = 3;
                     }
                     break;
                  case 3529:
                     if (command.equals("nw")) {
                        var7 = 0;
                     }
                     break;
                  case 3046219:
                     if (command.equals("cata")) {
                        var7 = 1;
                     }
                     break;
                  case 1032605407:
                     if (command.equals("crimson")) {
                        var7 = 4;
                     }
               }

               switch (var7) {
                  case 0:
                     renderNetworth(data);
                     break;
                  case 1:
                     renderCata(data);
                     break;
                  case 2:
                     renderSkills(data);
                     break;
                  case 3:
                     renderSlayer(data);
                     break;
                  case 4:
                     renderCrimson(data);
                     break;
                  case 5:
                     renderTrophyFish(data);
                     break;
                  default:
                     sendMessage("§cCommand " + command + " data received but renderer not implemented yet.");
               }
            } catch (Exception var8) {
               sendMessage("§cFailed to parse API response: " + var8.getMessage());
            }

         }).exceptionally((ex) -> {
            sendMessage("§cAPI Request failed: " + ex.getMessage());
            return null;
         });
      }
   }

   private static void sendMessage(String message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> {
            mc.player.displayClientMessage(Component.literal(message.replace("&", "§")), false);
         });
      }

   }

   private static void sendComponent(Component component) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> {
            mc.player.displayClientMessage(component, false);
         });
      }

   }

   public static HoverEvent createHoverEvent(String text) {
      try {
         Component content = Component.literal(text.replace("&", "§"));
         Class[] var2 = HoverEvent.class.getDeclaredClasses();
         int var3 = var2.length;

         int var4;
         for (var4 = 0; var4 < var3; ++var4) {
            Class<?> inner = var2[var4];
            Constructor[] var6 = inner.getDeclaredConstructors();
            int var7 = var6.length;

            for (int var8 = 0; var8 < var7; ++var8) {
               Constructor<?> c = var6[var8];
               c.setAccessible(true);
               if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isAssignableFrom(Component.class)) {
                  Object showTextInstance = c.newInstance(content);
                  if (showTextInstance instanceof HoverEvent) {
                     return (HoverEvent) showTextInstance;
                  }
               }
            }
         }

         if (!HoverEvent.class.isInterface()) {
            Class<?> actionClass = null;
            Class[] var13 = HoverEvent.class.getDeclaredClasses();
            var4 = var13.length;

            int var16;
            for (var16 = 0; var16 < var4; ++var16) {
               Class<?> inner = var13[var16];
               if (inner.isEnum() || inner.getSimpleName().contains("Action")) {
                  actionClass = inner;
                  break;
               }
            }

            if (actionClass != null) {
               Object showTextAction = null;
               Field[] var15 = actionClass.getDeclaredFields();
               var16 = var15.length;

               int var19;
               for (var19 = 0; var19 < var16; ++var19) {
                  Field f = var15[var19];
                  if (f.getName().contains("SHOW_TEXT") || f.getName().contains("TEXT")) {
                     f.setAccessible(true);
                     showTextAction = f.get((Object) null);
                     break;
                  }
               }

               if (showTextAction != null) {
                  Constructor[] var17 = HoverEvent.class.getDeclaredConstructors();
                  var16 = var17.length;

                  for (var19 = 0; var19 < var16; ++var19) {
                     Constructor<?> c = var17[var19];
                     c.setAccessible(true);
                     if (c.getParameterCount() == 2) {
                        return (HoverEvent) c.newInstance(showTextAction, content);
                     }
                  }
               }
            }
         }
      } catch (Throwable var11) {
      }

      return null;
   }

   private static void renderNetworth(JsonObject data) {
      if (data.has("networth") && !data.get("networth").isJsonNull()) {
         JsonObject nw = data.getAsJsonObject("networth");
         String var10000;
         if (nw.has("noInventory") && nw.get("noInventory").getAsBoolean()) {
            var10000 = formatUsername(data);
            sendMessage(var10000 + " §r§chas inventory API disabled in profile '"
                  + data.get("profileName").getAsString() + "'!§r");
         } else {
            sendComponent(Component.literal(formatUsername(data) + "§r§c's Networth:§r"));
            long totalNw = nw.has("networth") ? nw.get("networth").getAsLong() : 0L;
            sendMessage("§d ⦾ §6$" + formatCommas(totalNw));
            sendMessage("§r");
            long purse = nw.has("purse") ? nw.get("purse").getAsLong() : 0L;
            long bank = nw.has("bank") ? nw.get("bank").getAsLong() : 0L;
            MutableComponent coins = Component.literal("§a | §bCoins: §6" + formatNotation(purse + bank));
            MutableComponent coinsDetails = Component.literal(" - §7(Details)§r");
            var10000 = formatCommas(purse);
            HoverEvent coinsHover = createHoverEvent("§bPurse: §6" + var10000 + "\n§bBank: §6" + formatCommas(bank));
            if (coinsHover != null) {
               coinsDetails.setStyle(Style.EMPTY.withHoverEvent(coinsHover));
            }

            coins.append(coinsDetails);
            sendComponent(coins);
            if (nw.has("types")) {
               JsonObject types = nw.getAsJsonObject("types");
               String[] sequence = new String[] { "armor", "equipment", "wardrobe", "inventory", "enderchest",
                     "accessories", "personal_vault", "storage", "museum", "sacks", "essence", "pets" };
               String[] var13 = sequence;
               int var14 = sequence.length;

               for (int var15 = 0; var15 < var14; ++var15) {
                  String type = var13[var15];
                  if (types.has(type) && !types.get(type).isJsonNull()) {
                     JsonObject typeData = types.getAsJsonObject(type);
                     long total = typeData.has("total") ? typeData.get("total").getAsLong() : 0L;
                     JsonArray items = typeData.has("items") ? typeData.getAsJsonArray("items") : new JsonArray();
                     StringBuilder hover = new StringBuilder();

                     for (int i = 0; i < Math.min(items.size(), 16); ++i) {
                        JsonObject item = items.get(i).getAsJsonObject();
                        String itemName = item.has("loreName") ? item.get("loreName").getAsString()
                              : (item.has("name") ? item.get("name").getAsString() : "Unknown");
                        if (itemName.equals("Unknown") && item.has("id")) {
                           itemName = toTitleCase(item.get("id").getAsString().replace("_", " "));
                        }

                        int count = item.has("count") ? item.get("count").getAsInt() : 1;
                        long price = item.has("price") ? item.get("price").getAsLong() : 0L;
                        if (i > 0) {
                           hover.append("\n");
                        }

                        if (count > 1) {
                           hover.append("§7").append(count).append("x§r ");
                        }

                        hover.append(itemName.replace("&", "§")).append(" §b- ").append(formatNotation(price))
                              .append("§r");
                     }

                     var10000 = toTitleCase(type.replace("_", " "));
                     MutableComponent line = Component.literal("§d | §b" + var10000 + ": §6" + formatNotation(total));
                     if (hover.length() > 0) {
                        MutableComponent typeDetails = Component.literal(" - §7(Details)§r");
                        HoverEvent typeHover = createHoverEvent(hover.toString());
                        if (typeHover != null) {
                           typeDetails.setStyle(Style.EMPTY.withHoverEvent(typeHover));
                        }

                        line.append(typeDetails);
                     }

                     sendComponent(line);
                  }
               }
            }

         }
      } else {
         sendMessage(formatUsername(data) + " §r§c's Networth:§r");
         sendMessage("§cNo Networth data found.");
      }
   }

   private static void renderCata(JsonObject data) {
      sendMessage(formatUsername(data) + " §r§c's Catacombs:§r");
      JsonObject catacombs = null;
      if (data.has("dungeons")) {
         JsonObject dungeons = data.getAsJsonObject("dungeons");
         if (dungeons.has("catacombs")) {
            catacombs = dungeons.getAsJsonObject("catacombs");
         }
      } else if (data.has("catacombs")) {
         catacombs = data.getAsJsonObject("catacombs");
      }

      if (catacombs != null && !catacombs.isJsonNull()) {
         int level = 0;
         if (catacombs.has("skill") && catacombs.get("skill").isJsonObject()) {
            level = catacombs.getAsJsonObject("skill").get("level").getAsInt();
         } else if (catacombs.has("level")) {
            level = catacombs.get("level").getAsInt();
         }

         sendMessage("§d ⦾ §bLevel: §6" + level);
         if (catacombs.has("floors")) {
            JsonObject floors = catacombs.getAsJsonObject("floors");
            StringBuilder hover = new StringBuilder("§bFloors Completions:");
            boolean hasFloors = false;
            Iterator var6 = floors.keySet().iterator();

            while (var6.hasNext()) {
               String key = (String) var6.next();
               JsonObject floor = floors.getAsJsonObject(key);
               if (floor.has("completions")) {
                  int comps = floor.get("completions").getAsInt();
                  if (comps > 0) {
                     hover.append("\n§7").append(toTitleCase(key.replace("_", " "))).append(": §6")
                           .append(formatCommas((long) comps));
                     hasFloors = true;
                  }
               }
            }

            if (hasFloors) {
               MutableComponent floorsComp = Component.literal("§d | §bFloors Completions: §7(Hover)§r");
               HoverEvent hoverEvent = createHoverEvent(hover.toString());
               if (hoverEvent != null) {
                  floorsComp.setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
               }

               sendComponent(floorsComp);
            }
         }

      } else {
         sendMessage("§cNo Catacombs data found.");
      }
   }

   private static void renderCrimson(JsonObject data) {
      JsonObject nether = null;
      if (data.has("crimson")) {
         nether = data.getAsJsonObject("crimson");
      } else if (data.has("nether")) {
         nether = data.getAsJsonObject("nether");
      }

      if (nether != null && !nether.isJsonNull()) {
         sendMessage(formatUsername(data) + " §r§c's Crimson Isle:§r");
         JsonObject kuudra;
         long mage;
         long barbarian;
         if (nether.has("faction")) {
            kuudra = nether.getAsJsonObject("faction");
            mage = kuudra.has("mages_reputation") ? kuudra.get("mages_reputation").getAsLong() : 0L;
            barbarian = kuudra.has("barbarians_reputation") ? kuudra.get("barbarians_reputation").getAsLong() : 0L;
            sendMessage("§d ⦾ §bMage Reputation: §6" + formatCommas(mage));
            sendMessage("§d ⦾ §bBarbarian Reputation: §6" + formatCommas(barbarian));
         } else if (nether.has("reputation")) {
            kuudra = nether.getAsJsonObject("reputation");
            mage = kuudra.has("mage") ? kuudra.get("mage").getAsLong() : 0L;
            barbarian = kuudra.has("barbarian") ? kuudra.get("barbarian").getAsLong() : 0L;
            sendMessage("§d ⦾ §bMage Reputation: §6" + formatCommas(mage));
            sendMessage("§d ⦾ §bBarbarian Reputation: §6" + formatCommas(barbarian));
         }

         kuudra = null;
         if (nether.has("kuudra") && nether.getAsJsonObject("kuudra").has("completed_tier")) {
            kuudra = nether.getAsJsonObject("kuudra").getAsJsonObject("completed_tier");
         } else if (nether.has("kuudra_completed_tiers")) {
            kuudra = nether.getAsJsonObject("kuudra_completed_tiers");
         }

         if (kuudra != null) {
            sendMessage("§d ⦾ §bKuudra Tiers:");
            String[] tiers = new String[] { "basic", "hot", "burning", "fiery", "infernal" };
            String[] var4 = tiers;
            int var10 = tiers.length;

            for (int var6 = 0; var6 < var10; ++var6) {
               String tier = var4[var6];
               int count = kuudra.has(tier) ? kuudra.get(tier).getAsInt() : 0;
               if (count > 0) {
                  String var10000 = toTitleCase(tier);
                  sendMessage("§7  - §b" + var10000 + ": §6" + formatCommas((long) count));
               }
            }
         }

      } else {
         sendMessage(formatUsername(data) + " §r§c's Crimson Isle:§r");
         sendMessage("§cNo Crimson Isle data found.");
      }
   }

   private static void renderTrophyFish(JsonObject data) {
      if (data.has("trophy_fish") && !data.get("trophy_fish").isJsonNull()) {
         JsonObject fish = data.getAsJsonObject("trophy_fish");
         sendMessage(formatUsername(data) + " §r§c's Trophy Fish:§r");
         if (fish.has("total_caught")) {
            sendMessage("§d ⦾ §bTotal Caught: §6" + formatCommas(fish.get("total_caught").getAsLong()));
         }

         StringBuilder hover = new StringBuilder("§bTrophy Fish Breakdown:");
         boolean hasFish = false;
         Iterator var4 = fish.keySet().iterator();

         while (true) {
            JsonObject f;
            String name;
            int total;
            do {
               String key;
               JsonElement el;
               do {
                  do {
                     do {
                        if (!var4.hasNext()) {
                           if (hasFish) {
                              MutableComponent breakdown = Component
                                    .literal("§d | §bFish Breakdown: §7(Hover for details)§r");
                              HoverEvent hoverEvent = createHoverEvent(hover.toString());
                              if (hoverEvent != null) {
                                 breakdown.setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
                              }

                              sendComponent(breakdown);
                           }

                           if (fish.has("rank")) {
                              sendMessage("§d ⦾ §bRank: §6" + fish.get("rank").getAsString());
                           }

                           return;
                        }

                        key = (String) var4.next();
                     } while (key.equals("total_caught"));
                  } while (key.equals("rank"));

                  el = fish.get(key);
               } while (!el.isJsonObject());

               f = el.getAsJsonObject();
               name = f.has("name") ? f.get("name").getAsString() : toTitleCase(key.replace("_", " "));
               total = f.has("total") ? f.get("total").getAsInt() : 0;
            } while (total <= 0);

            hover.append("\n§7").append(name).append(": §6").append(total);
            if (f.has("bronze") || f.has("silver") || f.has("gold") || f.has("diamond")) {
               hover.append(" §8(");
               if (f.has("bronze")) {
                  hover.append("§c").append(f.get("bronze").getAsInt()).append("§8, ");
               }

               if (f.has("silver")) {
                  hover.append("§7").append(f.get("silver").getAsInt()).append("§8, ");
               }

               if (f.has("gold")) {
                  hover.append("§6").append(f.get("gold").getAsInt()).append("§8, ");
               }

               if (f.has("diamond")) {
                  hover.append("§b").append(f.get("diamond").getAsInt());
               }

               hover.append("§8)");
            }

            hasFish = true;
         }
      } else {
         sendMessage(formatUsername(data) + " §r§c's Trophy Fish:§r");
         sendMessage("§cNo Trophy Fish data found.");
      }
   }

   private static void renderSkills(JsonObject data) {
      sendMessage(formatUsername(data) + " §r§c's Skills:§r");
      if (data.has("skills") && !data.get("skills").isJsonNull()) {
         JsonObject skills = data.getAsJsonObject("skills");
         Object var10000 = skills.has("average") ? skills.get("average").getAsDouble() : "0.0";
         sendMessage("§d ⦾ §bAverage: §6" + String.valueOf(var10000));
         StringBuilder hover = new StringBuilder("§bSkills Breakdown:");
         boolean hasSkills = false;
         Iterator var4 = skills.keySet().iterator();

         while (var4.hasNext()) {
            String key = (String) var4.next();
            if (!key.equals("average")) {
               JsonElement el = skills.get(key);
               if (el.isJsonObject()) {
                  JsonObject s = el.getAsJsonObject();
                  int level = s.has("level") ? s.get("level").getAsInt() : 0;
                  double xp = s.has("xp") ? s.get("xp").getAsDouble() : 0.0D;
                  hover.append("\n§7").append(toTitleCase(key)).append(": §6").append(level).append(" §8(")
                        .append(formatNotation((long) xp)).append(" XP)");
                  hasSkills = true;
               }
            }
         }

         if (hasSkills) {
            MutableComponent breakdown = Component.literal("§d | §bSkills Breakdown: §7(Hover for details)§r");
            HoverEvent hoverEvent = createHoverEvent(hover.toString());
            if (hoverEvent != null) {
               breakdown.setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
            }

            sendComponent(breakdown);
         }

      } else {
         sendMessage("§cNo Skills data found.");
      }
   }

   private static void renderSlayer(JsonObject data) {
      sendMessage(formatUsername(data) + " §r§c's Slayer:§r");
      if (!data.has("slayers") && !data.has("slayer")) {
         sendMessage("§cNo Slayer data found.");
      } else {
         JsonObject slayer = data.has("slayers") ? data.getAsJsonObject("slayers") : data.getAsJsonObject("slayer");
         if (slayer != null && !slayer.isJsonNull()) {
            long totalXp = 0L;
            if (slayer.has("total_experience")) {
               totalXp = slayer.get("total_experience").getAsLong();
            } else if (slayer.has("totalXp")) {
               totalXp = slayer.get("totalXp").getAsLong();
            }

            sendMessage("§d ⦾ §bTotal XP: §6" + formatNotation(totalXp));
            StringBuilder hover = new StringBuilder("§bSlayers Breakdown:");
            boolean hasSlayer = false;
            Iterator var6 = slayer.keySet().iterator();

            while (var6.hasNext()) {
               String key = (String) var6.next();
               if (!key.equals("total_experience") && !key.equals("totalXp") && !key.equals("total_coins_spent")) {
                  JsonElement el = slayer.get(key);
                  if (el.isJsonObject()) {
                     JsonObject s = el.getAsJsonObject();
                     int level = s.has("level") ? s.get("level").getAsInt() : 0;
                     long xp = s.has("xp") ? s.get("xp").getAsLong() : 0L;
                     hover.append("\n§7").append(toTitleCase(key)).append(": §6").append(level).append(" §8(")
                           .append(formatNotation(xp)).append(" XP)");
                     hasSlayer = true;
                  }
               }
            }

            if (hasSlayer) {
               MutableComponent breakdown = Component.literal("§d | §bSlayers Breakdown: §7(Hover for details)§r");
               HoverEvent hoverEvent = createHoverEvent(hover.toString());
               if (hoverEvent != null) {
                  breakdown.setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
               }

               sendComponent(breakdown);
            }

         } else {
            sendMessage("§cNo Slayer data found.");
         }
      }
   }

   private static String formatUsername(JsonObject data) {
      String rank = data.has("rank") ? data.get("rank").getAsString() : "§7";
      String username = data.get("username").getAsString();
      String var10000 = rank.replace("&", "§");
      return var10000 + " " + username;
   }

   private static String formatCommas(long value) {
      return String.format("%,d", value);
   }

   private static String formatNotation(long value) {
      if (value >= 1000000000L) {
         return String.format("%.2fB", (double) value / 1.0E9D);
      } else if (value >= 1000000L) {
         return String.format("%.2fM", (double) value / 1000000.0D);
      } else {
         return value >= 1000L ? String.format("%.1fK", (double) value / 1000.0D) : String.valueOf(value);
      }
   }

   private static String toTitleCase(String input) {
      StringBuilder result = new StringBuilder();
      boolean nextTitleCase = true;
      char[] var3 = input.toCharArray();
      int var4 = var3.length;

      for (int var5 = 0; var5 < var4; ++var5) {
         char c = var3[var5];
         if (Character.isSpaceChar(c)) {
            nextTitleCase = true;
         } else if (nextTitleCase) {
            c = Character.toTitleCase(c);
            nextTitleCase = false;
         } else {
            c = Character.toLowerCase(c);
         }

         result.append(c);
      }

      return result.toString();
   }
}
