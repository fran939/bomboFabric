package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.bombo.bomboaddons.gui.DailyRewardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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

public class DailyRewardHelper {

    public static class RewardCard {
        public int index;
        public String title = "Reward";
        public String amount = "1";
        public String rarity = "COMMON"; // COMMON, RARE, EPIC, LEGENDARY
        public String rawType = "";
        public boolean isStreakReward = false;

        public int getRarityColor() {
            return switch (rarity.toUpperCase()) {
                case "RARE" -> 0xFF55FFFF;       // Aqua / Cyan
                case "EPIC" -> 0xFFAA00AA;       // Dark Purple
                case "LEGENDARY" -> 0xFFFFAA00;  // Gold / Yellow
                default -> 0xFFAAAAAA;           // Common Gray
            };
        }

        public int getCardBorderColor() {
            return switch (rarity.toUpperCase()) {
                case "RARE" -> 0xFF00AAAA;
                case "EPIC" -> 0xFF880088;
                case "LEGENDARY" -> 0xFFDD8800;
                default -> 0xFF555555;
            };
        }
    }

    public static class RewardPageData {
        public String rewardKey = "";
        public String securityToken = "";
        public String cookieHeader = "";
        public List<RewardCard> cards = new ArrayList<>();
        public int currentStreak = 0;
        public int highScore = 0;
        public String rawHtml = "";
    }

    private static boolean isFetching = false;

    public static boolean isRewardUrl(String url) {
        if (url == null) return false;
        return url.contains("rewards.hypixel.net/claim-reward/") || url.contains("rewards.hypixel.net/");
    }

    public static String extractRewardKey(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.contains("http") && !trimmed.contains("rewards.hypixel.net") && trimmed.matches("[a-zA-Z0-9_-]+")) {
            return trimmed;
        }
        Pattern p = Pattern.compile("rewards\\.hypixel\\.net/(?:claim-reward/)?([a-zA-Z0-9_-]+)");
        Matcher m = p.matcher(trimmed);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static class FetchResult {
        int statusCode;
        String html = "";
        String cookieHeader = "";
    }

    private static FetchResult fetchUrlWithCookies(String initialUrl) throws Exception {
        FetchResult result = new FetchResult();
        Map<String, String> cookiesMap = new HashMap<>();

        String currentUrl = initialUrl;
        int redirects = 0;
        HttpURLConnection conn = null;

        while (redirects < 5) {
            URL url = URI.create(currentUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (!cookiesMap.isEmpty()) {
                List<String> pairs = new ArrayList<>();
                for (Map.Entry<String, String> entry : cookiesMap.entrySet()) {
                    pairs.add(entry.getKey() + "=" + entry.getValue());
                }
                conn.setRequestProperty("Cookie", String.join("; ", pairs));
            }

            int code = conn.getResponseCode();

            // Extract Set-Cookie
            Map<String, List<String>> headers = conn.getHeaderFields();
            if (headers != null) {
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie")) {
                        for (String headerVal : entry.getValue()) {
                            String cookiePair = headerVal.split(";")[0].trim();
                            int eq = cookiePair.indexOf('=');
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
                    redirects++;
                    continue;
                }
            }

            result.statusCode = code;
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                result.html = sb.toString();
            }
            break;
        }

        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : cookiesMap.entrySet()) {
            pairs.add(entry.getKey() + "=" + entry.getValue());
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
            return;
        }
        final String key = extractRewardKey(keyOrUrl);
        if (key == null || key.isEmpty()) {
            if (BomboConfig.get().debugDailyReward && client.player != null) {
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §cKey extraction failed for: " + keyOrUrl));
                    }
                });
            }
            return;
        }

        isFetching = true;
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eFetching Hypixel Daily Reward (§b" + key + "§e)..."));
            }
        });

        new Thread(() -> {
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
                        client.player.sendSystemMessage(Component.literal("§8[§bDailyRewardDebug§8] §7Token: " + data.securityToken + " | Cookies: " + (data.cookieHeader.isEmpty() ? "none" : data.cookieHeader) + " | Cards: " + data.cards.size()));
                    }

                    isFetching = false;
                    if (data.cards == null || data.cards.isEmpty()) {
                        // Fallback 3 Mystery Cards when cards are face-down or behind an ad
                        data.cards = new ArrayList<>();
                        for (int i = 0; i < 3; i++) {
                            RewardCard card = new RewardCard();
                            card.index = i;
                            card.rarity = (i == 0 ? "RARE" : (i == 1 ? "EPIC" : "LEGENDARY"));
                            card.title = "Mystery Card " + (i + 1);
                            card.amount = "Click to Claim";
                            data.cards.add(card);
                        }
                    }

                    if (data.securityToken != null && !data.securityToken.isEmpty()) {
                        client.setScreen(new DailyRewardScreen(data));
                    } else {
                        if (client.player != null) {
                            client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cCould not parse Daily Reward token. §7(Reward may have already been claimed!)"));
                        }
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
        }, "DailyRewardFetcher").start();
    }

    private static RewardPageData parseRewardHtml(String key, String html) {
        RewardPageData data = new RewardPageData();
        data.rewardKey = key;
        data.rawHtml = html;

        // 1. Security token regex
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

        // 2. Streak regex
        Pattern streakPattern = Pattern.compile("(?:currentScore|currentStreak|score|streak)\\s*[:=]\\s*(\\d+)");
        Matcher streakMatcher = streakPattern.matcher(html);
        if (streakMatcher.find()) {
            try { data.currentStreak = Integer.parseInt(streakMatcher.group(1)); } catch (Exception ignored) {}
        }
        Pattern highPattern = Pattern.compile("(?:highScore|highestStreak)\\s*[:=]\\s*(\\d+)");
        Matcher highMatcher = highPattern.matcher(html);
        if (highMatcher.find()) {
            try { data.highScore = Integer.parseInt(highMatcher.group(1)); } catch (Exception ignored) {}
        }

        // 3. Parse i18n map
        Map<String, String> i18n = new HashMap<>();
        Pattern i18nPattern = Pattern.compile("window\\.i18n\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
        Matcher i18nMatcher = i18nPattern.matcher(html);
        if (i18nMatcher.find()) {
            try {
                JsonObject jsonI18n = JsonParser.parseString(i18nMatcher.group(1)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : jsonI18n.entrySet()) {
                    i18n.put(entry.getKey(), entry.getValue().getAsString());
                }
            } catch (Exception ignored) {}
        }

        // 4. Parse window.appData JSON string or rewards array
        String appDataJson = null;
        Pattern appDataPattern = Pattern.compile("window\\.appData\\s*=\\s*['\"]([^'\"]+)['\"]");
        Matcher appDataMatcher = appDataPattern.matcher(html);
        if (appDataMatcher.find()) {
            appDataJson = appDataMatcher.group(1);
        } else {
            Pattern altAppData = Pattern.compile("window\\.appData\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);
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
            } catch (Exception ignored) {}
        }

        // Fallback: direct rewards array regex
        if (rewardsArr == null) {
            Pattern rewardBlockPattern = Pattern.compile("rewards\\s*[:=]\\s*(\\[.*?\\]);", Pattern.DOTALL);
            Matcher rewardBlockMatcher = rewardBlockPattern.matcher(html);
            if (rewardBlockMatcher.find()) {
                try {
                    JsonElement elem = JsonParser.parseString(rewardBlockMatcher.group(1));
                    if (elem.isJsonArray()) rewardsArr = elem.getAsJsonArray();
                } catch (Exception ignored) {}
            }
        }

        if (rewardsArr != null) {
            for (int i = 0; i < rewardsArr.size(); i++) {
                if (i >= 3) break;
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

                // Format title using i18n or fallback clean key name
                String titleText = i18n.getOrDefault("type." + rewardRawKey, i18n.getOrDefault(rewardRawKey, rewardRawKey));
                if (titleText.contains("{$game}") && !gameType.isEmpty()) {
                    titleText = titleText.replace("{$game}", formatGameName(gameType));
                }
                titleText = titleText.replace("type.", "").replaceAll("_", " ");
                card.title = cleanRewardText(titleText);

                data.cards.add(card);
            }
        }

        // Ultimate fallback regex parsing if JSON array regex missed
        if (data.cards.isEmpty()) {
            Pattern cardPattern = Pattern.compile("\\{\\s*[\"']rarity[\"']\\s*:\\s*[\"']([^\"']+)[\"'].*?[\"'](?:reward|text|title)[\"']\\s*:\\s*[\"']([^\"']+)[\"']", Pattern.DOTALL);
            Matcher cardMatcher = cardPattern.matcher(html);
            int idx = 0;
            while (cardMatcher.find() && idx < 3) {
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
        if (gameType == null) return "Hypixel";
        return switch (gameType.toUpperCase()) {
            case "BEDWARS" -> "BedWars";
            case "SKYWARS" -> "SkyWars";
            case "MURDER_MYSTERY" -> "Murder Mystery";
            case "BUILD_BATTLE" -> "Build Battle";
            case "DUELS" -> "Duels";
            case "ARCADE" -> "Arcade";
            case "SURVIVAL_GAMES" -> "Blitz SG";
            case "UHC" -> "UHC";
            case "PIT" -> "The Pit";
            default -> gameType.substring(0, 1).toUpperCase() + gameType.substring(1).toLowerCase();
        };
    }

    private static String cleanRewardText(String raw) {
        if (raw == null) return "Reward";
        String clean = raw.replaceAll("<[^>]*>", "").trim();
        if (clean.isEmpty()) return "Reward";
        return clean.substring(0, 1).toUpperCase() + clean.substring(1);
    }

    public static void claimReward(RewardPageData pageData, RewardCard selectedCard) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eClaiming §b" + selectedCard.title + "§e..."));
            }
        });

        new Thread(() -> {
            try {
                String claimUrl = "https://rewards.hypixel.net/claim-reward/claim";

                String cookieStr = pageData.cookieHeader;
                if (cookieStr == null || cookieStr.isEmpty()) {
                    cookieStr = "_csrf=" + pageData.securityToken;
                } else if (!cookieStr.contains("_csrf=")) {
                    cookieStr = cookieStr + "; _csrf=" + pageData.securityToken;
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
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                conn.setRequestProperty("Referer", "https://rewards.hypixel.net/claim-reward/" + pageData.rewardKey);
                conn.setRequestProperty("Cookie", cookieStr);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonStr.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

                int code = conn.getResponseCode();

                // If JSON fails (e.g. 403), fallback to x-www-form-urlencoded
                if (code != 200) {
                    HttpURLConnection conn2 = (HttpURLConnection) url.openConnection();
                    conn2.setRequestMethod("POST");
                    conn2.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn2.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    conn2.setRequestProperty("Referer", "https://rewards.hypixel.net/claim-reward/" + pageData.rewardKey);
                    conn2.setRequestProperty("Cookie", cookieStr);
                    conn2.setDoOutput(true);

                    String postParams = "option=" + selectedCard.index + "&id=" + pageData.rewardKey + "&activeAd=0&_csrf=" + pageData.securityToken + "&watchedFallback=false&skipped=0";
                    try (OutputStream os = conn2.getOutputStream()) {
                        os.write(postParams.getBytes(StandardCharsets.UTF_8));
                        os.flush();
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
        }, "DailyRewardClaimer").start();
    }
}
