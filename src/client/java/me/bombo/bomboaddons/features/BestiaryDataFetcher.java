package me.bombo.bomboaddons.features;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BestiaryDataFetcher {
   public static final String API_URL = "https://api.bombo.dpdns.org/mod/bestiary";
   private static final Map<String, List<BestiaryMobRule>> islandRules = new ConcurrentHashMap<>();
   private static final Map<String, BestiaryMobRule> allRulesByNameAndIsland = new ConcurrentHashMap<>();
   private static final Map<String, List<BestiaryMobRule>> headLookup = new ConcurrentHashMap<>();
   private static boolean initialized = false;

   public static class BestiaryMobRule {
      public String name;
      public int maxTier = 0;
      public String island;
      public String entityType;
      public List<String> heads = new ArrayList<>();
      public String armor;
      public String playerName;
      public String riding;
      public String heldItem;
      public String subarea;
      public String size;
      public String mobSize;

      public BestiaryMobRule(String name, String island) {
         this.name = name;
         this.island = island != null ? island : "";
      }

      public boolean hasHead(String hash) {
         if (hash == null || heads == null || heads.isEmpty()) return false;
         String clean = hash.trim().toLowerCase(Locale.ROOT);
         for (String h : heads) {
            if (h != null) {
               String cleanH = h.trim().toLowerCase(Locale.ROOT);
               if (cleanH.equals(clean) || cleanH.contains(clean) || clean.contains(cleanH)) return true;
            }
         }
         return false;
      }
   }

   public static void init() {
      if (initialized) return;
      initialized = true;

      // 1. Load bundled default json as fallback
      loadBundledJson();

      // 2. Fetch latest from API asynchronously
      Thread t = new Thread(() -> {
         try {
            HttpClient client = HttpClient.newBuilder()
               .connectTimeout(Duration.ofSeconds(5))
               .build();
            HttpRequest request = HttpRequest.newBuilder()
               .uri(URI.create(API_URL))
               .timeout(Duration.ofSeconds(6))
               .header("User-Agent", "BomboAddons/1.0")
               .GET()
               .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
               parseBestiaryJson(response.body());
            }
         } catch (Throwable ignored) {}
      }, "Bombo-BestiaryDataFetcher");
      t.setDaemon(true);
      t.start();
   }

   private static void loadBundledJson() {
      try (InputStream in = BestiaryDataFetcher.class.getClassLoader().getResourceAsStream("bestiary_data.json")) {
         if (in != null) {
            parseBestiaryJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   public static void parseBestiaryJson(String jsonStr) {
      if (jsonStr == null || jsonStr.trim().isEmpty()) return;
      try {
         JsonElement el = JsonParser.parseString(jsonStr);
         if (el.isJsonObject()) {
            JsonObject root = el.getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
               parseJsonObject(root.getAsJsonObject("data"));
            } else if (root.has("mobs") && root.get("mobs").isJsonArray()) {
               parseArrayOfMobs(root.getAsJsonArray("mobs"), "General");
            } else {
               parseJsonObject(root);
            }
         } else if (el.isJsonArray()) {
            parseArrayOfMobs(el.getAsJsonArray(), "General");
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   private static void parseArrayOfMobs(JsonArray arr, String defaultIsland) {
      Map<String, List<BestiaryMobRule>> newIslandMap = new HashMap<>();
      Map<String, BestiaryMobRule> newLookup = new HashMap<>();
      Map<String, List<BestiaryMobRule>> newHeadLookup = new HashMap<>();

      for (JsonElement el : arr) {
         if (!el.isJsonObject()) continue;
         JsonObject obj = el.getAsJsonObject();
         String island = obj.has("island") ? obj.get("island").getAsString() : defaultIsland;
         BestiaryMobRule rule = parseSingleMob(obj, island);
         if (rule != null) {
            newIslandMap.computeIfAbsent(cleanKey(island), k -> new ArrayList<>()).add(rule);
            newLookup.put(cleanKey(rule.name) + "@" + cleanKey(island), rule);
            newLookup.put(cleanKey(rule.name), rule);
            for (String h : rule.heads) {
               newHeadLookup.computeIfAbsent(h.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(rule);
            }
         }
      }

      if (!newLookup.isEmpty()) {
         islandRules.clear();
         islandRules.putAll(newIslandMap);
         allRulesByNameAndIsland.clear();
         allRulesByNameAndIsland.putAll(newLookup);
         headLookup.clear();
         headLookup.putAll(newHeadLookup);
      }
   }

   private static void parseJsonObject(JsonObject root) {
      Map<String, List<BestiaryMobRule>> newIslandMap = new HashMap<>();
      Map<String, BestiaryMobRule> newLookup = new HashMap<>();
      Map<String, List<BestiaryMobRule>> newHeadLookup = new HashMap<>();

      for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
         String islandName = entry.getKey();
         if (!entry.getValue().isJsonArray()) continue;
         JsonArray arr = entry.getValue().getAsJsonArray();
         List<BestiaryMobRule> rules = new ArrayList<>();

         for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject obj = el.getAsJsonObject();
            BestiaryMobRule rule = parseSingleMob(obj, islandName);
            if (rule != null) {
               rules.add(rule);
               newLookup.put(cleanKey(rule.name) + "@" + cleanKey(islandName), rule);
               newLookup.put(cleanKey(rule.name), rule);
               for (String h : rule.heads) {
                  newHeadLookup.computeIfAbsent(h.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(rule);
               }
            }
         }
         newIslandMap.put(cleanKey(islandName), rules);
      }

      if (!newLookup.isEmpty()) {
         islandRules.clear();
         islandRules.putAll(newIslandMap);
         allRulesByNameAndIsland.clear();
         allRulesByNameAndIsland.putAll(newLookup);
         headLookup.clear();
         headLookup.putAll(newHeadLookup);
      }
   }

   private static BestiaryMobRule parseSingleMob(JsonObject obj, String islandName) {
      String name = obj.has("name") ? obj.get("name").getAsString() : "";
      if (name.isEmpty()) return null;

      BestiaryMobRule rule = new BestiaryMobRule(name, islandName);
      if (obj.has("tier_num")) {
         rule.maxTier = obj.get("tier_num").getAsInt();
      } else if (obj.has("max_tier")) {
         try {
            rule.maxTier = obj.get("max_tier").getAsInt();
         } catch (Exception e) {
            rule.maxTier = parseRomanTier(obj.get("max_tier").getAsString());
         }
      }
      if (obj.has("entity_type")) rule.entityType = obj.get("entity_type").getAsString();
      if (obj.has("armor")) rule.armor = obj.get("armor").getAsString();
      if (obj.has("player_name")) rule.playerName = obj.get("player_name").getAsString();
      if (obj.has("riding")) rule.riding = obj.get("riding").getAsString();
      if (obj.has("held_item")) rule.heldItem = obj.get("held_item").getAsString();
      if (obj.has("subarea")) rule.subarea = obj.get("subarea").getAsString();
      if (obj.has("size")) rule.size = obj.get("size").getAsString();
      if (obj.has("mob_size")) rule.mobSize = obj.get("mob_size").getAsString();
      if (obj.has("rule")) rule.armor = obj.get("rule").getAsString();

      if (obj.has("skull_texture") && !obj.get("skull_texture").getAsString().isEmpty()) {
         rule.heads.add(obj.get("skull_texture").getAsString().toLowerCase(Locale.ROOT));
      }
      if (obj.has("skull_textures") && obj.get("skull_textures").isJsonArray()) {
         for (JsonElement h : obj.get("skull_textures").getAsJsonArray()) {
            rule.heads.add(h.getAsString().toLowerCase(Locale.ROOT));
         }
      }
      if (obj.has("heads") && obj.get("heads").isJsonArray()) {
         for (JsonElement h : obj.get("heads").getAsJsonArray()) {
            rule.heads.add(h.getAsString().toLowerCase(Locale.ROOT));
         }
      } else if (obj.has("heads") && obj.get("heads").isJsonPrimitive()) {
         rule.heads.add(obj.get("heads").getAsString().toLowerCase(Locale.ROOT));
      }
      if (obj.has("skulls") && obj.get("skulls").isJsonArray()) {
         for (JsonElement h : obj.get("skulls").getAsJsonArray()) {
            rule.heads.add(h.getAsString().toLowerCase(Locale.ROOT));
         }
      }
      return rule;
   }

   private static int parseRomanTier(String s) {
      if (s == null || s.trim().isEmpty()) return 5;
      String t = s.trim().toUpperCase(Locale.ROOT);
      if (t.equals("XXV")) return 25;
      if (t.equals("XX")) return 20;
      if (t.equals("XV")) return 15;
      if (t.equals("X")) return 10;
      if (t.equals("V")) return 5;
      try {
         return Integer.parseInt(t);
      } catch (Exception e) {
         return 5;
      }
   }

   public static String cleanKey(String str) {
      if (str == null) return "";
      return str.replaceAll("(?i)§[0-9a-fk-or]", "")
         .replaceAll("(?i)\\s+(?:X{0,3}(?:IX|IV|V?I{1,3})|L|C|D|M|[0-9]+)$", "")
         .trim()
         .toLowerCase(Locale.ROOT);
   }

   public static BestiaryMobRule getRule(String mobName, String island) {
      if (mobName == null) return null;
      if (!initialized) init();

      String cleanMob = cleanKey(mobName);
      String cleanIsl = cleanKey(island);

      if (!cleanIsl.isEmpty()) {
         BestiaryMobRule rule = allRulesByNameAndIsland.get(cleanMob + "@" + cleanIsl);
         if (rule != null) return rule;
      }
      return allRulesByNameAndIsland.get(cleanMob);
   }

   public static BestiaryMobRule getRuleByHead(String skullHash) {
      if (skullHash == null) return null;
      if (!initialized) init();
      String clean = skullHash.toLowerCase(Locale.ROOT);
      List<BestiaryMobRule> list = headLookup.get(clean);
      if (list != null && !list.isEmpty()) return list.get(0);
      for (Map.Entry<String, List<BestiaryMobRule>> e : headLookup.entrySet()) {
         if (e.getKey().contains(clean) || clean.contains(e.getKey())) {
            if (!e.getValue().isEmpty()) return e.getValue().get(0);
         }
      }
      return null;
   }

   public static List<BestiaryMobRule> getAllRules() {
      if (!initialized) init();
      return new ArrayList<>(new HashSet<>(allRulesByNameAndIsland.values()));
   }

   public static int getTotalRuleCount() {
      return (int) allRulesByNameAndIsland.values().stream().distinct().count();
   }

   public static int getRulesCount() {
      return getTotalRuleCount();
   }

   public static int getTotalHeadCount() {
      return headLookup.size();
   }

   public static int getHeadLookupCount() {
      return getTotalHeadCount();
   }

   public static void fetchBestiaryDataAsync() {
      forceRefresh(null);
   }

   public static int getIslandCount() {
      return islandRules.size();
   }

   public static void forceRefresh(Runnable onComplete) {
      Thread t = new Thread(() -> {
         try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_URL)).timeout(Duration.ofSeconds(6)).header("User-Agent", "BomboAddons/1.0").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
               parseBestiaryJson(response.body());
            }
         } catch (Throwable ignored) {}
         if (onComplete != null) onComplete.run();
      }, "Bombo-BestiaryDataFetcher-Refresh");
      t.setDaemon(true);
      t.start();
   }

   public static List<BestiaryMobRule> getRulesForIsland(String island) {
      if (island == null || island.trim().isEmpty()) return Collections.emptyList();
      if (!initialized) init();
      String key = cleanKey(island);
      List<BestiaryMobRule> list = islandRules.get(key);
      if (list != null && !list.isEmpty()) return list;

      // Fuzzy check if key matches or is contained in an island name
      for (Map.Entry<String, List<BestiaryMobRule>> entry : islandRules.entrySet()) {
         String eKey = entry.getKey();
         if (eKey.equalsIgnoreCase(key) || eKey.contains(key) || key.contains(eKey)) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
               return entry.getValue();
            }
         }
      }

      // Check mob rule island properties directly
      List<BestiaryMobRule> matched = new ArrayList<>();
      for (BestiaryMobRule r : allRulesByNameAndIsland.values()) {
         if (r.island != null && !r.island.isEmpty()) {
            String rIsl = cleanKey(r.island);
            if (rIsl.equalsIgnoreCase(key) || rIsl.contains(key) || key.contains(rIsl)) {
               if (!matched.contains(r)) matched.add(r);
            }
         }
      }
      return matched;
   }
}
