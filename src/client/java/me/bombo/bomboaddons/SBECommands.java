package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

@Environment(EnvType.CLIENT)
public class SBECommands {
   private static final HttpClient client = HttpClient.newBuilder().build();
   private static final String API_BASE = "https://sbecommands-api.icarusphantom.dev/v1/sbecommands/";

   public static void handleCommand(String command, String name, String profile) {
      if (!BomboConfig.get().sbeCommands) {
         sendMessage("§cSBEC Commands are currently disabled in /bombo!");
      } else {
         String apiCmd = command.equals("skills") ? "skill" : command;
         String subPath = apiCmd + "/" + name;
         if (profile != null && !profile.equalsIgnoreCase("selected")) {
            subPath = subPath + "/" + profile;
         }

         String url = BomboApiUrl.getApiUrl("/" + subPath);
         String fallbackUrl = "https://sbecommands-api.icarusphantom.dev/v1/sbecommands/" + subPath;
         Bomboaddons.logApiRequest(url);
         Minecraft mc = Minecraft.getInstance();
         String selfUuid = mc.getUser().getProfileId().toString();
         sendMessage("§7Fetching data for " + name + "...");
         if (BomboConfig.get().apiDebug) {
            sendMessage("§b[Debug] API: " + url);
         }

         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Bomboaddons 1.21.11) " + selfUuid).timeout(Duration.ofSeconds(15L)).GET().build();
         client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).thenAccept((body) -> {
            if (body != null && !body.trim().isEmpty()) {
               parseAndDisplay(body, command, name);
            } else {
               sendMessage("§cEmpty response from API.");
            }
         }).exceptionally((e) -> {
            sendMessage("§cError fetching data for " + name + ": " + e.getMessage());
            return null;
         });
      }

   }

   private static void sendFallbackRequest(String fallbackUrl, String command, String name, String selfUuid) {
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(fallbackUrl)).header("User-Agent", "Mozilla/5.0 (Bomboaddons 1.21.11) " + selfUuid).timeout(Duration.ofSeconds(30L)).GET().build();
      client.sendAsync(request, BodyHandlers.ofString()).thenApply(HttpResponse::body).thenAccept((body) -> parseAndDisplay(body, command, name)).exceptionally((e) -> {
         sendMessage("§cError fetching data for " + name + ": " + e.getMessage());
         return null;
      });
   }

   private static void parseAndDisplay(String body, String command, String name) {
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
         String cmd = command.toLowerCase();
         if (cmd.equals("nw") || cmd.equals("nwc")) {
            renderNetworth(data);
         } else if (cmd.equals("cata")) {
            renderCata(data);
         } else if (cmd.equals("skills") || cmd.equals("skill")) {
            renderSkills(data);
         } else if (cmd.equals("slayer")) {
            renderSlayer(data);
         } else if (cmd.equals("crimson") || cmd.equals("kuudra") || cmd.equals("crimsom")) {
            renderCrimson(data);
         } else if (cmd.equals("trophyfish")) {
            renderTrophyFish(data);
         } else {
            sendMessage("§cCommand " + command + " data received but renderer not implemented yet.");
         }
      } catch (Exception var8) {
         sendMessage("§cFailed to parse API response: " + var8.getMessage());
      }

   }

   private static void sendMessage(String message) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> mc.player.sendSystemMessage(Component.literal(message.replace("&", "§"))));
      }

   }

   private static void sendComponent(Component component) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.execute(() -> mc.player.sendSystemMessage(component));
      }

   }

   public static HoverEvent createHoverEventFromComponent(Component content) {
      try {
         for(Class<?> inner : HoverEvent.class.getDeclaredClasses()) {
            for(Constructor<?> c : inner.getDeclaredConstructors()) {
               c.setAccessible(true);
               if (c.getParameterCount() == 1 && c.getParameterTypes()[0].isAssignableFrom(Component.class)) {
                  Object showTextInstance = c.newInstance(content);
                  if (showTextInstance instanceof HoverEvent) {
                     return (HoverEvent)showTextInstance;
                  }
               }
            }
         }

         if (!HoverEvent.class.isInterface()) {
            Class<?> actionClass = null;

            for(Class<?> inner : HoverEvent.class.getDeclaredClasses()) {
               if (inner.isEnum() || inner.getSimpleName().contains("Action")) {
                  actionClass = inner;
                  break;
               }
            }

            if (actionClass != null) {
               Object showTextAction = null;

               for(Field f : actionClass.getDeclaredFields()) {
                  if (f.getName().contains("SHOW_TEXT") || f.getName().contains("TEXT")) {
                     f.setAccessible(true);
                     showTextAction = f.get((Object)null);
                     break;
                  }
               }

               if (showTextAction != null) {
                  for(Constructor<?> c : HoverEvent.class.getDeclaredConstructors()) {
                     c.setAccessible(true);
                     if (c.getParameterCount() == 2) {
                        return (HoverEvent)c.newInstance(showTextAction, content);
                     }
                  }
               }
            }
         }
      } catch (Exception var20) {
         var20.printStackTrace();
      }

      return null;
   }

   public static HoverEvent createHoverEvent(String text) {
      return createHoverEventFromComponent(Component.literal(text.replace("&", "§")));
   }

   private static void renderNetworth(JsonObject data) {
      if (data == null) {
         sendMessage("§cNo Networth data found.");
         return;
      }

      if (data.has("members") && data.get("members").isJsonArray()) {
         renderNetworthCoop(data);
         return;
      }

      if (data.has("networth") && !data.get("networth").isJsonNull()) {
         JsonObject nw = data.getAsJsonObject("networth");
         sendComponent(Component.literal(formatUsername(data) + "§r§c's Networth:§r"));
         long totalNw = nw.has("networth") ? nw.get("networth").getAsLong() : 0L;
         sendMessage("§d ⦾ §6$" + formatCommas(totalNw));
         sendMessage("§r");
         long purse = nw.has("purse") ? nw.get("purse").getAsLong() : 0L;
         long bank = nw.has("bank") ? nw.get("bank").getAsLong() : 0L;
         long personalBank = nw.has("personalBank") ? nw.get("personalBank").getAsLong() : 0L;
         String var100001 = formatNotation(purse + bank + personalBank);
         MutableComponent coins = Component.literal("§a | §bCoins: §6" + var100001);
         MutableComponent coinsDetails = Component.literal(" - §7(Details)§r");
         String var10000 = formatCommas(purse);
         String hoverStr = "§bPurse: §6" + var10000 + "\n§bBank: §6" + formatCommas(bank);
         if (personalBank > 0L) {
            hoverStr = hoverStr + "\n§bPersonal Bank: §6" + formatCommas(personalBank);
         }

         HoverEvent coinsHover = createHoverEvent(hoverStr);
         if (coinsHover != null) {
            coinsDetails.setStyle(Style.EMPTY.withHoverEvent(coinsHover));
         }

         coins.append(coinsDetails);
         sendComponent(coins);
         if (nw.has("types")) {
            JsonObject types = nw.getAsJsonObject("types");
            String[] sequence = new String[]{"armor", "equipment", "wardrobe", "inventory", "enderchest", "accessories", "personal_vault", "storage", "museum", "sacks", "essence", "pets"};

            for(String type : sequence) {
               if (types.has(type) && !types.get(type).isJsonNull()) {
                  JsonObject typeData = types.getAsJsonObject(type);
                  long total = typeData.has("total") ? typeData.get("total").getAsLong() : 0L;
                  JsonArray items = typeData.has("items") ? typeData.getAsJsonArray("items") : new JsonArray();
                  StringBuilder hover = new StringBuilder();

                  for(int i = 0; i < Math.min(items.size(), 16); ++i) {
                     JsonObject item = items.get(i).getAsJsonObject();
                     String itemName = item.has("loreName") ? item.get("loreName").getAsString() : (item.has("name") ? item.get("name").getAsString() : "Unknown");
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

                     hover.append(itemName.replace("&", "§")).append(" §b- ").append(formatNotation(price)).append("§r");
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
      } else {
         sendMessage(formatUsername(data) + " §r§c's Networth:§r");
         sendMessage("§cNo Networth data found.");
      }
   }

   private static void renderNetworthCoop(JsonObject data) {
      String profileName = data.has("profileName") && !data.get("profileName").isJsonNull() ? data.get("profileName").getAsString() : "Co-op";
      long coopNw = data.has("coop_networth") && !data.get("coop_networth").isJsonNull() ? data.get("coop_networth").getAsLong() : 0L;
      long bank = data.has("bank") && !data.get("bank").isJsonNull() ? data.get("bank").getAsLong() : 0L;

      String username = data.has("username") && !data.get("username").isJsonNull() ? data.get("username").getAsString() : null;
      String headerName = formatUsername(data);
      if (username != null && !username.isEmpty()) {
         sendComponent(Component.literal(headerName + "§r§c's Co-op Networth:§r §7(" + profileName + ")"));
      } else {
         sendComponent(Component.literal("§e" + profileName + "§c's Co-op Networth:§r"));
      }

      sendMessage("§d ⦾ §6$" + formatCommas(coopNw));
      sendMessage("§r");

      JsonArray members = data.has("members") && data.get("members").isJsonArray() ? data.getAsJsonArray("members") : new JsonArray();

      long totalPurse = 0L;
      long totalPersonalBank = 0L;
      Map<String, Long> categoryTotals = new java.util.LinkedHashMap<>();
      Map<String, java.util.List<JsonObject>> categoryItems = new java.util.LinkedHashMap<>();

      String[] sequence = new String[]{"armor", "equipment", "wardrobe", "inventory", "enderchest", "accessories", "personal_vault", "storage", "museum", "sacks", "essence", "pets"};
      for (String seq : sequence) {
         categoryTotals.put(seq, 0L);
         categoryItems.put(seq, new java.util.ArrayList<>());
      }

      java.util.List<JsonObject> memberList = new java.util.ArrayList<>();
      for (int mIdx = 0; mIdx < members.size(); ++mIdx) {
         JsonObject m = members.get(mIdx).getAsJsonObject();
         memberList.add(m);

         long p = m.has("purse") && !m.get("purse").isJsonNull() ? m.get("purse").getAsLong() : 0L;
         long pb = m.has("personalBank") && !m.get("personalBank").isJsonNull() ? m.get("personalBank").getAsLong() : 0L;
         totalPurse += p;
         totalPersonalBank += pb;

         JsonObject categories = null;
         if (m.has("categories") && !m.get("categories").isJsonNull()) {
            categories = m.getAsJsonObject("categories");
         } else if (m.has("types") && !m.get("types").isJsonNull()) {
            categories = m.getAsJsonObject("types");
         }

         if (categories != null) {
            for (String type : sequence) {
               if (categories.has(type) && !categories.get(type).isJsonNull()) {
                  JsonObject typeData = categories.getAsJsonObject(type);
                  long t = typeData.has("total") && !typeData.get("total").isJsonNull() ? typeData.get("total").getAsLong() : 0L;
                  categoryTotals.put(type, categoryTotals.get(type) + t);

                  if (typeData.has("items") && typeData.get("items").isJsonArray()) {
                     for (JsonElement itEl : typeData.getAsJsonArray("items")) {
                        if (itEl.isJsonObject()) {
                           categoryItems.get(type).add(itEl.getAsJsonObject());
                        }
                     }
                  }
               }
            }
         }
      }

      long totalCoins = bank + totalPurse + totalPersonalBank;
      MutableComponent coins = Component.literal("§a | §bCoins: §6" + formatNotation(totalCoins));
      MutableComponent coinsDetails = Component.literal(" - §7(Details)§r");
      String coinsHoverStr = "§bCo-op Bank: §6" + formatCommas(bank) + "\n§bCombined Purses: §6" + formatCommas(totalPurse);
      if (totalPersonalBank > 0L) {
         coinsHoverStr += "\n§bCombined Personal Banks: §6" + formatCommas(totalPersonalBank);
      }
      HoverEvent coinsHover = createHoverEvent(coinsHoverStr);
      if (coinsHover != null) {
         coinsDetails.setStyle(Style.EMPTY.withHoverEvent(coinsHover));
      }
      coins.append(coinsDetails);
      sendComponent(coins);

      for (String type : sequence) {
         long total = categoryTotals.getOrDefault(type, 0L);
         java.util.List<JsonObject> items = categoryItems.getOrDefault(type, java.util.Collections.emptyList());

         items.sort((a, b) -> {
            long pA = a.has("price") && !a.get("price").isJsonNull() ? a.get("price").getAsLong() : 0L;
            long pB = b.has("price") && !b.get("price").isJsonNull() ? b.get("price").getAsLong() : 0L;
            return Long.compare(pB, pA);
         });

         StringBuilder hover = new StringBuilder();
         for (int i = 0; i < Math.min(items.size(), 16); ++i) {
            JsonObject item = items.get(i);
            String itemName = item.has("loreName") && !item.get("loreName").isJsonNull() ? item.get("loreName").getAsString() : (item.has("name") && !item.get("name").isJsonNull() ? item.get("name").getAsString() : "Unknown");
            if (itemName.equals("Unknown") && item.has("id") && !item.get("id").isJsonNull()) {
               itemName = toTitleCase(item.get("id").getAsString().replace("_", " "));
            }

            int count = item.has("count") && !item.get("count").isJsonNull() ? item.get("count").getAsInt() : 1;
            long price = item.has("price") && !item.get("price").isJsonNull() ? item.get("price").getAsLong() : 0L;
            if (i > 0) {
               hover.append("\n");
            }

            if (count > 1) {
               hover.append("§7").append(count).append("x§r ");
            }

            hover.append(itemName.replace("&", "§")).append(" §b- ").append(formatNotation(price)).append("§r");
         }

         String typeTitle = toTitleCase(type.replace("_", " "));
         MutableComponent line = Component.literal("§d | §b" + typeTitle + ": §6" + formatNotation(total));
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

      sendMessage("§r");
      sendComponent(Component.literal("§e§lCo-op Members Ranking:§r"));

      memberList.sort((a, b) -> {
         long nA = a.has("networth") && !a.get("networth").isJsonNull() ? a.get("networth").getAsLong() : 0L;
         long nB = b.has("networth") && !b.get("networth").isJsonNull() ? b.get("networth").getAsLong() : 0L;
         return Long.compare(nB, nA);
      });

      for (int i = 0; i < memberList.size(); ++i) {
         JsonObject m = memberList.get(i);
         String mName = m.has("username") && !m.get("username").isJsonNull() ? m.get("username").getAsString() : (m.has("name") && !m.get("name").isJsonNull() ? m.get("name").getAsString() : (m.has("uuid") && !m.get("uuid").isJsonNull() ? m.get("uuid").getAsString() : "Member " + (i + 1)));
         String rankPrefix = RankCache.getRank(mName);
         if (rankPrefix != null && !rankPrefix.isEmpty()) {
            mName = rankPrefix.replace("&", "§") + " " + mName;
         } else {
            mName = "§b" + mName;
         }

         long mNw = m.has("networth") && !m.get("networth").isJsonNull() ? m.get("networth").getAsLong() : 0L;
         long mPurse = m.has("purse") && !m.get("purse").isJsonNull() ? m.get("purse").getAsLong() : 0L;
         long mBank = m.has("personalBank") && !m.get("personalBank").isJsonNull() ? m.get("personalBank").getAsLong() : 0L;

         MutableComponent mLine = Component.literal(" §7" + (i + 1) + ". " + mName + "§7: §6$" + formatNotation(mNw) + " §7(§e$" + formatCommas(mNw) + "§7)");
         String mHoverStr = "§bNetworth: §6$" + formatCommas(mNw) + "\n§bPurse: §6$" + formatCommas(mPurse);
         if (mBank > 0L) {
            mHoverStr += "\n§bPersonal Bank: §6$" + formatCommas(mBank);
         }
         HoverEvent mHover = createHoverEvent(mHoverStr);
         if (mHover != null) {
            mLine.setStyle(Style.EMPTY.withHoverEvent(mHover));
         }
         sendComponent(mLine);
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

            for(String key : floors.keySet()) {
               JsonObject floor = floors.getAsJsonObject(key);
               if (floor.has("completions")) {
                  int comps = floor.get("completions").getAsInt();
                  if (comps > 0) {
                     hover.append("\n§7").append(toTitleCase(key.replace("_", " "))).append(": §6").append(formatCommas((long)comps));
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
         if (nether.has("faction")) {
            JsonObject kuudra = nether.getAsJsonObject("faction");
            long mage = kuudra.has("mages_reputation") ? kuudra.get("mages_reputation").getAsLong() : 0L;
            long barbarian = kuudra.has("barbarians_reputation") ? kuudra.get("barbarians_reputation").getAsLong() : 0L;
            sendMessage("§d ⦾ §bMage Reputation: §6" + formatCommas(mage));
            sendMessage("§d ⦾ §bBarbarian Reputation: §6" + formatCommas(barbarian));
         } else if (nether.has("reputation")) {
            JsonObject kuudra = nether.getAsJsonObject("reputation");
            long mage = kuudra.has("mage") ? kuudra.get("mage").getAsLong() : 0L;
            long barbarian = kuudra.has("barbarian") ? kuudra.get("barbarian").getAsLong() : 0L;
            sendMessage("§d ⦾ §bMage Reputation: §6" + formatCommas(mage));
            sendMessage("§d ⦾ §bBarbarian Reputation: §6" + formatCommas(barbarian));
         }

         int ringDiscount = 0;
         String dataStr = data.toString().toUpperCase();
         if (!dataStr.contains("SEAL_OF_FAMILY") && !dataStr.contains("SEAL_OF_THE_FAMILY") && !dataStr.contains("SEAL OF THE FAMILY")) {
            if (!dataStr.contains("CROOKED_ARTIFACT") && !dataStr.contains("CROOKED ARTIFACT")) {
               if (!dataStr.contains("SHADY_RING") && !dataStr.contains("SHADY RING")) {
                  ringDiscount = checkBase64DataForRings(data);
                  if (ringDiscount == 0) {
                     ringDiscount = AutoCroesus.getRingDiscountPercent();
                  }
               } else {
                  ringDiscount = 1;
               }
            } else {
               ringDiscount = 2;
            }
         } else {
            ringDiscount = 3;
         }

         String ringName = AutoCroesus.getRingDiscountName(ringDiscount);
         sendMessage("§d ⦾ §bKey Discount Ring: §a" + ringName);
         JsonObject kuudra = null;
         if (nether.has("kuudra") && nether.getAsJsonObject("kuudra").has("completed_tier")) {
            kuudra = nether.getAsJsonObject("kuudra").getAsJsonObject("completed_tier");
         } else if (nether.has("kuudra_completed_tiers")) {
            kuudra = nether.getAsJsonObject("kuudra_completed_tiers");
         }

         if (kuudra != null) {
            sendMessage("§d ⦾ §bKuudra Tiers:");
            String[] tiers = new String[]{"basic", "hot", "burning", "fiery", "infernal"};

            for(String tier : tiers) {
               int count = kuudra.has(tier) ? kuudra.get(tier).getAsInt() : 0;
               if (count > 0) {
                  String var10000 = toTitleCase(tier);
                  sendMessage("§7  - §b" + var10000 + ": §6" + formatCommas((long)count));
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

         for(String key : fish.keySet()) {
            if (!key.equals("total_caught") && !key.equals("rank")) {
               JsonElement el = fish.get(key);
               if (el.isJsonObject()) {
                  JsonObject f = el.getAsJsonObject();
                  String name = f.has("name") ? f.get("name").getAsString() : toTitleCase(key.replace("_", " "));
                  int total = f.has("total") ? f.get("total").getAsInt() : 0;
                  if (total > 0) {
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
               }
            }
         }

         if (hasFish) {
            MutableComponent breakdown = Component.literal("§d | §bFish Breakdown: §7(Hover for details)§r");
            HoverEvent hoverEvent = createHoverEvent(hover.toString());
            if (hoverEvent != null) {
               breakdown.setStyle(Style.EMPTY.withHoverEvent(hoverEvent));
            }

            sendComponent(breakdown);
         }

         if (fish.has("rank")) {
            sendMessage("§d ⦾ §bRank: §6" + fish.get("rank").getAsString());
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

         for(String key : skills.keySet()) {
            if (!key.equals("average")) {
               JsonElement el = skills.get(key);
               if (el.isJsonObject()) {
                  JsonObject s = el.getAsJsonObject();
                  int level = s.has("level") ? s.get("level").getAsInt() : 0;
                  double xp = s.has("xp") ? s.get("xp").getAsDouble() : (double)0.0F;
                  hover.append("\n§7").append(toTitleCase(key)).append(": §6").append(level).append(" §8(").append(formatNotation((long)xp)).append(" XP)");
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

            for(String key : slayer.keySet()) {
               if (!key.equals("total_experience") && !key.equals("totalXp") && !key.equals("total_coins_spent")) {
                  JsonElement el = slayer.get(key);
                  if (el.isJsonObject()) {
                     JsonObject s = el.getAsJsonObject();
                     int level = s.has("level") ? s.get("level").getAsInt() : 0;
                     long xp = s.has("xp") ? s.get("xp").getAsLong() : 0L;
                     hover.append("\n§7").append(toTitleCase(key)).append(": §6").append(level).append(" §8(").append(formatNotation(xp)).append(" XP)");
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
      if (data == null) return "§7Unknown";
      String rank = data.has("rank") && !data.get("rank").isJsonNull() ? data.get("rank").getAsString() : "§7";
      String username = data.has("username") && !data.get("username").isJsonNull() ? data.get("username").getAsString() : (data.has("profileName") && !data.get("profileName").isJsonNull() ? data.get("profileName").getAsString() : "Player");
      String var10000 = rank.replace("&", "§");
      return var10000.isEmpty() ? username : var10000 + " " + username;
   }

   private static String formatCommas(long value) {
      return String.format("%,d", value);
   }

   private static String formatNotation(long value) {
      if (value >= 1000000000L) {
         return String.format("%.2fB", (double)value / (double)1.0E9F);
      } else if (value >= 1000000L) {
         return String.format("%.2fM", (double)value / (double)1000000.0F);
      } else {
         return value >= 1000L ? String.format("%.1fK", (double)value / (double)1000.0F) : String.valueOf(value);
      }
   }

   private static String toTitleCase(String input) {
      StringBuilder result = new StringBuilder();
      boolean nextTitleCase = true;

      for(char c : input.toCharArray()) {
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

   private static int checkBase64DataForRings(JsonObject element) {
      if (element == null) {
         return 0;
      } else {
         int[] maxDiscount = new int[]{0};
         findBase64Data(element, maxDiscount);
         return maxDiscount[0];
      }
   }

   private static void findBase64Data(JsonElement element, int[] maxDiscount) {
      if (maxDiscount[0] < 3 && element != null && !element.isJsonNull()) {
         if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("data") && obj.get("data").isJsonPrimitive()) {
               int d = parseBase64Ring(obj.get("data").getAsString());
               if (d > maxDiscount[0]) {
                  maxDiscount[0] = d;
               }
            } else {
               for(Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                  findBase64Data((JsonElement)entry.getValue(), maxDiscount);
               }
            }
         } else if (element.isJsonArray()) {
            for(JsonElement item : element.getAsJsonArray()) {
               findBase64Data(item, maxDiscount);
            }
         }

      }
   }

   private static int parseBase64Ring(String base64) {
      try {
         byte[] bytes = Base64.getDecoder().decode(base64);

         CompoundTag nbt;
         try {
            nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
         } catch (Exception var8) {
            nbt = NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)));
         }

         if (nbt == null) {
            return 0;
         } else {
            int max = 0;
            if (nbt.contains("i")) {
               ListTag list = nbt.get("i") instanceof ListTag ? (ListTag)nbt.get("i") : null;
               if (list != null) {
                  for(int i = 0; i < list.size(); ++i) {
                     CompoundTag item = list.getCompound(i).orElse(null);
                     if (item != null && !item.isEmpty()) {
                        int d = checkItemTagForRing(item);
                        if (d > max) {
                           max = d;
                        }
                     }
                  }
               }
            }

            return max;
         }
      } catch (Exception var9) {
         return 0;
      }
   }

   private static int checkItemTagForRing(CompoundTag item) {
      CompoundTag tag = item.getCompound("tag").orElse(null);
      if (tag == null) {
         return 0;
      } else {
         CompoundTag ea = tag.getCompound("ExtraAttributes").orElse(null);
         if (ea != null) {
            String id = ea.getString("id").orElse("");
            if (!id.isEmpty()) {
               if (id.contains("SEAL_OF_FAMILY") || id.contains("SEAL_OF_THE_FAMILY")) {
                  return 3;
               }

               if (id.contains("CROOKED_ARTIFACT")) {
                  return 2;
               }

               if (id.contains("SHADY_RING")) {
                  return 1;
               }
            }
         }

         CompoundTag display = tag.getCompound("display").orElse(null);
         if (display != null) {
            String name = (display.getString("Name").orElse("")).toLowerCase();
            if (name.contains("seal of the family")) {
               return 3;
            }

            if (name.contains("crooked artifact")) {
               return 2;
            }

            if (name.contains("shady ring")) {
               return 1;
            }
         }

         return 0;
      }
   }
}
