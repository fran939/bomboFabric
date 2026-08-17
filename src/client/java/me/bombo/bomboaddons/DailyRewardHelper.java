package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.bombo.bomboaddons.gui.DailyRewardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class DailyRewardHelper {
   private static boolean isFetching = false;

   public static boolean isRewardUrl(String url) {
      if (url == null) {
         return false;
      } else {
         return url.contains("rewards.hypixel.net/claim-reward/") || url.contains("rewards.hypixel.net/");
      }
   }

   public static String extractRewardKey(String text) {
      if (text == null) {
         return null;
      } else {
         String trimmed = text.trim();
         if (!trimmed.contains("http") && !trimmed.contains("rewards.hypixel.net") && trimmed.matches("[a-zA-Z0-9_-]+")) {
            return trimmed;
         } else {
            Pattern p = Pattern.compile("rewards\\.hypixel\\.net/(?:claim-reward/)?([a-zA-Z0-9_-]+)");
            Matcher m = p.matcher(trimmed);
            return m.find() ? m.group(1) : null;
         }
      }
   }

   private static FetchResult fetchUrlWithCookies(String initialUrl) throws Exception {
      FetchResult result = new FetchResult();
      Map<String, String> cookiesMap = new HashMap();
      String currentUrl = initialUrl;
      int redirects = 0;
      HttpURLConnection conn = null;

      while(redirects < 5) {
         URL url = URI.create(currentUrl).toURL();
         conn = (HttpURLConnection)url.openConnection();
         conn.setInstanceFollowRedirects(false);
         conn.setRequestMethod("GET");
         conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
         conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
         conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);
         if (!cookiesMap.isEmpty()) {
            List<String> pairs = new ArrayList();

            for(Map.Entry<String, String> entry : cookiesMap.entrySet()) {
               String var10001 = (String)entry.getKey();
               pairs.add(var10001 + "=" + (String)entry.getValue());
            }

            conn.setRequestProperty("Cookie", String.join("; ", pairs));
         }

         int code = conn.getResponseCode();
         Map<String, List<String>> headers = conn.getHeaderFields();
         if (headers != null) {
            for(Map.Entry<String, List<String>> entry : headers.entrySet()) {
               if (entry.getKey() != null && ((String)entry.getKey()).equalsIgnoreCase("Set-Cookie")) {
                  for(String headerVal : (List<String>)(List<?>)entry.getValue()) {
                     String cookiePair = headerVal.split(";")[0].trim();
                     int eq = cookiePair.indexOf(61);
                     if (eq > 0) {
                        String cKey = cookiePair.substring(0, eq).trim();
                        String cVal = cookiePair.substring(eq + 1).trim();
                        cookiesMap.put(cKey, cVal);
                     }
                  }
               }
            }
         }

         if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String loc = conn.getHeaderField("Location");
            if (loc != null && !loc.isEmpty()) {
               if (loc.startsWith("/")) {
                  currentUrl = "https://rewards.hypixel.net" + loc;
               } else {
                  currentUrl = loc;
               }

               ++redirects;
               continue;
            }
         }

         result.statusCode = code;
         if (code == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();

            String line;
            while((line = reader.readLine()) != null) {
               sb.append(line).append("\n");
            }

            reader.close();
            result.html = sb.toString();
         }
         break;
      }

      List<String> pairs = new ArrayList();

      for(Map.Entry<String, String> entry : cookiesMap.entrySet()) {
         String var28 = (String)entry.getKey();
         pairs.add(var28 + "=" + (String)entry.getValue());
      }

      result.cookieHeader = String.join("; ", pairs);
      return result;
   }

   public static void fetchAndOpenRewardPage(String keyOrUrl) {
      Minecraft client = Minecraft.getInstance();
      if (isFetching) {
         if (BomboConfig.get().debugDailyReward && client.player != null) {
            client.execute(() -> {
               if (client.player != null) {
                  client.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §cAlready fetching, request ignored."));
               }

            });
         }

      } else {
         String key = extractRewardKey(keyOrUrl);
         if (key != null && !key.isEmpty()) {
            isFetching = true;
            client.execute(() -> {
               if (client.player != null) {
                  client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eFetching Hypixel Daily Reward (§b" + key + "§e)..."));
               }

            });
            (new Thread(() -> {
               try {
                  String targetUrl = "https://rewards.hypixel.net/claim-reward/" + key;
                  FetchResult res = fetchUrlWithCookies(targetUrl);
                  if (res.statusCode != 200) {
                     isFetching = false;
                     client.execute(() -> {
                        if (client.player != null) {
                           client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cFailed to fetch daily reward (HTTP " + res.statusCode + ")"));
                        }

                     });
                     return;
                  }

                  RewardPageData data = parseRewardHtml(key, res.html);
                  data.cookieHeader = res.cookieHeader;
                  client.execute(() -> {
                     if (BomboConfig.get().debugDailyReward && client.player != null) {
                        String var10001 = data.securityToken;
                        client.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §7Token: " + var10001 + " | Cookies: " + (data.cookieHeader.isEmpty() ? "none" : data.cookieHeader) + " | Cards: " + data.cards.size()));
                     }

                     isFetching = false;
                     if (data.cards == null || data.cards.isEmpty()) {
                        data.cards = new ArrayList();

                        for(int i = 0; i < 3; ++i) {
                           RewardCard card = new RewardCard();
                           card.index = i;
                           card.rarity = i == 0 ? "RARE" : (i == 1 ? "EPIC" : "LEGENDARY");
                           card.title = "Mystery Card " + (i + 1);
                           card.amount = "Click to Claim";
                           data.cards.add(card);
                        }
                     }

                     if (data.securityToken != null && !data.securityToken.isEmpty()) {
                        client.setScreen(new DailyRewardScreen(data));
                     } else if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cCould not parse Daily Reward token. §7(Reward may have already been claimed!)"));
                     }

                  });
               } catch (Throwable t) {
                  t.printStackTrace();
                  isFetching = false;
                  client.execute(() -> {
                     if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cError loading Daily Reward: §7" + t.getMessage()));
                     }

                  });
               }

            }, "DailyRewardFetcher")).start();
         } else {
            if (BomboConfig.get().debugDailyReward && client.player != null) {
               client.execute(() -> {
                  if (client.player != null) {
                     client.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §cKey extraction failed for: " + keyOrUrl));
                  }

               });
            }

         }
      }
   }

   private static RewardPageData parseRewardHtml(String key, String html) {
      RewardPageData data = new RewardPageData();
      data.rewardKey = key;
      data.rawHtml = html;
      Pattern tokenPattern = Pattern.compile("window\\.securityToken\\s*=\\s*[\"']([^\"']+)[\"']");
      Matcher tokenMatcher = tokenPattern.matcher(html);
      if (tokenMatcher.find()) {
         data.securityToken = tokenMatcher.group(1);
      } else {
         Pattern altTokenPattern = Pattern.compile("(?:securityToken|_csrf|activeToken)\\s*[:=]\\s*[\"']([^\"']+)[\"']");
         Matcher altMatcher = altTokenPattern.matcher(html);
         if (altMatcher.find()) {
            data.securityToken = altMatcher.group(1);
         }
      }

      Pattern streakPattern = Pattern.compile("(?:currentScore|currentStreak|score|streak)\\s*[:=]\\s*(\\d+)");
      Matcher streakMatcher = streakPattern.matcher(html);
      if (streakMatcher.find()) {
         try {
            data.currentStreak = Integer.parseInt(streakMatcher.group(1));
         } catch (Exception var26) {
         }
      }

      Pattern highPattern = Pattern.compile("(?:highScore|highestStreak)\\s*[:=]\\s*(\\d+)");
      Matcher highMatcher = highPattern.matcher(html);
      if (highMatcher.find()) {
         try {
            data.highScore = Integer.parseInt(highMatcher.group(1));
         } catch (Exception var25) {
         }
      }

      Map<String, String> i18n = new HashMap();
      Pattern i18nPattern = Pattern.compile("window\\.i18n\\s*=\\s*(\\{.*?\\});", 32);
      Matcher i18nMatcher = i18nPattern.matcher(html);
      if (i18nMatcher.find()) {
         try {
            JsonObject jsonI18n = JsonParser.parseString(i18nMatcher.group(1)).getAsJsonObject();

            for(Map.Entry<String, JsonElement> entry : jsonI18n.entrySet()) {
               i18n.put((String)entry.getKey(), ((JsonElement)entry.getValue()).getAsString());
            }
         } catch (Exception var27) {
         }
      }

      String appDataJson = null;
      Pattern appDataPattern = Pattern.compile("window\\.appData\\s*=\\s*['\"]([^'\"]+)['\"]");
      Matcher appDataMatcher = appDataPattern.matcher(html);
      if (appDataMatcher.find()) {
         appDataJson = appDataMatcher.group(1);
      } else {
         Pattern altAppData = Pattern.compile("window\\.appData\\s*=\\s*(\\{.*?\\});", 32);
         Matcher altMatcher = altAppData.matcher(html);
         if (altMatcher.find()) {
            appDataJson = altMatcher.group(1);
         }
      }

      JsonArray rewardsArr = null;
      if (appDataJson != null) {
         try {
            JsonElement elem = JsonParser.parseString(appDataJson);
            if (elem.isJsonObject()) {
               JsonObject appObj = elem.getAsJsonObject();
               if (appObj.has("rewards") && appObj.get("rewards").isJsonArray()) {
                  rewardsArr = appObj.getAsJsonArray("rewards");
               }
            }
         } catch (Exception var24) {
         }
      }

      if (rewardsArr == null) {
         Pattern rewardBlockPattern = Pattern.compile("rewards\\s*[:=]\\s*(\\[.*?\\]);", 32);
         Matcher rewardBlockMatcher = rewardBlockPattern.matcher(html);
         if (rewardBlockMatcher.find()) {
            try {
               JsonElement elem = JsonParser.parseString(rewardBlockMatcher.group(1));
               if (elem.isJsonArray()) {
                  rewardsArr = elem.getAsJsonArray();
               }
            } catch (Exception var23) {
            }
         }
      }

      if (rewardsArr != null) {
         for(int i = 0; i < rewardsArr.size() && i < 3; ++i) {
            JsonObject obj = rewardsArr.get(i).getAsJsonObject();
            RewardCard card = new RewardCard();
            card.index = i;
            if (obj.has("rarity")) {
               card.rarity = obj.get("rarity").getAsString();
            }

            String rewardRawKey = obj.has("reward") ? obj.get("reward").getAsString() : "";
            String gameType = obj.has("gameType") ? obj.get("gameType").getAsString() : "";
            String amountVal = obj.has("amount") ? obj.get("amount").getAsString() : "1";
            card.amount = amountVal;
            card.rawType = rewardRawKey;
            String titleText = (String)i18n.getOrDefault("type." + rewardRawKey, (String)i18n.getOrDefault(rewardRawKey, rewardRawKey));
            if (titleText.contains("{$game}") && !gameType.isEmpty()) {
               titleText = titleText.replace("{$game}", formatGameName(gameType));
            }

            titleText = titleText.replace("type.", "").replaceAll("_", " ");
            card.title = cleanRewardText(titleText);
            data.cards.add(card);
         }
      }

      if (data.cards.isEmpty()) {
         Pattern cardPattern = Pattern.compile("\\{\\s*[\"']rarity[\"']\\s*:\\s*[\"']([^\"']+)[\"'].*?[\"'](?:reward|text|title)[\"']\\s*:\\s*[\"']([^\"']+)[\"']", 32);
         Matcher cardMatcher = cardPattern.matcher(html);
         int idx = 0;

         while(cardMatcher.find() && idx < 3) {
            RewardCard card = new RewardCard();
            card.index = idx++;
            card.rarity = cardMatcher.group(1);
            card.title = cleanRewardText(cardMatcher.group(2));
            data.cards.add(card);
         }
      }

      return data;
   }

   private static String formatGameName(String gameType) {
      if (gameType == null) {
         return "Hypixel";
      } else {
         String var10000;
         switch (gameType.toUpperCase()) {
            case "BEDWARS" -> var10000 = "BedWars";
            case "SKYWARS" -> var10000 = "SkyWars";
            case "MURDER_MYSTERY" -> var10000 = "Murder Mystery";
            case "BUILD_BATTLE" -> var10000 = "Build Battle";
            case "DUELS" -> var10000 = "Duels";
            case "ARCADE" -> var10000 = "Arcade";
            case "SURVIVAL_GAMES" -> var10000 = "Blitz SG";
            case "UHC" -> var10000 = "UHC";
            case "PIT" -> var10000 = "The Pit";
            default -> var10000 = gameType.substring(0, 1).toUpperCase() + gameType.substring(1).toLowerCase();
         }

         return var10000;
      }
   }

   private static String cleanRewardText(String raw) {
      if (raw == null) {
         return "Reward";
      } else {
         String clean = raw.replaceAll("<[^>]*>", "").trim();
         if (clean.isEmpty()) {
            return "Reward";
         } else {
            String var10000 = clean.substring(0, 1).toUpperCase();
            return var10000 + clean.substring(1);
         }
      }
   }

   public static void claimReward(RewardPageData pageData, RewardCard selectedCard) {
      Minecraft client = Minecraft.getInstance();
      client.execute(() -> {
         if (client.player != null) {
            client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eClaiming §b" + selectedCard.title + "§e..."));
         }

      });
      (new Thread(() -> {
         try {
            String claimUrl = "https://rewards.hypixel.net/claim-reward/claim";
            String cookieStr = pageData.cookieHeader;
            if (cookieStr != null && !cookieStr.isEmpty()) {
               if (!cookieStr.contains("_csrf=")) {
                  cookieStr = cookieStr + "; _csrf=" + pageData.securityToken;
               }
            } else {
               cookieStr = "_csrf=" + pageData.securityToken;
            }

            JsonObject jsonPayload = new JsonObject();
            jsonPayload.addProperty("option", selectedCard.index);
            jsonPayload.addProperty("id", pageData.rewardKey);
            jsonPayload.addProperty("activeAd", 0);
            jsonPayload.addProperty("_csrf", pageData.securityToken);
            jsonPayload.addProperty("watchedFallback", false);
            jsonPayload.addProperty("skipped", 0);
            String jsonStr = jsonPayload.toString();
            URL url = URI.create(claimUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Referer", "https://rewards.hypixel.net/claim-reward/" + pageData.rewardKey);
            conn.setRequestProperty("Cookie", cookieStr);
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();

            try {
               os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
               os.flush();
            } catch (Throwable var18) {
               if (os != null) {
                  try {
                     os.close();
                  } catch (Throwable x2) {
                     var18.addSuppressed(x2);
                  }
               }

               throw var18;
            }

            if (os != null) {
               os.close();
            }

            int code = conn.getResponseCode();
            if (code != 200) {
               HttpURLConnection conn2 = (HttpURLConnection)url.openConnection();
               conn2.setRequestMethod("POST");
               conn2.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
               conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
               conn2.setRequestProperty("Referer", "https://rewards.hypixel.net/claim-reward/" + pageData.rewardKey);
               conn2.setRequestProperty("Cookie", cookieStr);
               conn2.setDoOutput(true);
               String postParams = "option=" + selectedCard.index + "&id=" + pageData.rewardKey + "&activeAd=0&_csrf=" + pageData.securityToken + "&watchedFallback=false&skipped=0";
               OutputStream os2 = conn2.getOutputStream();

               try {
                  os2.write(postParams.getBytes(StandardCharsets.UTF_8));
                  os2.flush();
               } catch (Throwable var17) {
                  if (os2 != null) {
                     try {
                        os2.close();
                     } catch (Throwable x2) {
                        var17.addSuppressed(x2);
                     }
                  }

                  throw var17;
               }

               if (os != null) {
                  os.close();
               }

               int code2 = conn2.getResponseCode();
               if (code2 == 200) {
                  code = 200;
               }
            }

            final int finalCode = code;
            client.execute(() -> {
               if (client.player != null) {
                  if (finalCode == 200) {
                     client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §a§lSuccessfully claimed Daily Reward! §f(" + selectedCard.title + ")"));
                  } else {
                     client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cClaim response HTTP " + finalCode + ". (Check browser if claim failed)"));
                  }
               }

            });
         } catch (Throwable t) {
            t.printStackTrace();
            client.execute(() -> {
               if (client.player != null) {
                  client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cError claiming reward: §7" + t.getMessage()));
               }

            });
         }

      }, "DailyRewardClaimer")).start();
   }

   public static class RewardCard {
      public int index;
      public String title = "Reward";
      public String amount = "1";
      public String rarity = "COMMON";
      public String rawType = "";
      public boolean isStreakReward = false;

      public int getRarityColor() {
         int var10000;
         switch (this.rarity.toUpperCase()) {
            case "RARE" -> var10000 = -11141121;
            case "EPIC" -> var10000 = -5635926;
            case "LEGENDARY" -> var10000 = -22016;
            default -> var10000 = -5592406;
         }

         return var10000;
      }

      public int getCardBorderColor() {
         int var10000;
         switch (this.rarity.toUpperCase()) {
            case "RARE" -> var10000 = -16733526;
            case "EPIC" -> var10000 = -7864184;
            case "LEGENDARY" -> var10000 = -2258944;
            default -> var10000 = -11184811;
         }

         return var10000;
      }
   }

   public static class RewardPageData {
      public String rewardKey = "";
      public String securityToken = "";
      public String cookieHeader = "";
      public List<RewardCard> cards = new ArrayList();
      public int currentStreak = 0;
      public int highScore = 0;
      public String rawHtml = "";
   }

   private static class FetchResult {
      int statusCode;
      String html = "";
      String cookieHeader = "";
   }
}
