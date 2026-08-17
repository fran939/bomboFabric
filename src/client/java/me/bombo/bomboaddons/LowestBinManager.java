package me.bombo.bomboaddons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import me.bombo.bomboaddons.util.BomboApiUrl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@Environment(EnvType.CLIENT)
public class LowestBinManager {
   private static final HttpClient client = HttpClient.newBuilder().build();
   private static final Map<String, Long> priceCache = new ConcurrentHashMap();
   private static final Map<String, Long> npcCache = new ConcurrentHashMap();
   private static final Map<String, Double> bazaarCache = new ConcurrentHashMap();
   private static final Map<String, Double> bazaarSellCache = new ConcurrentHashMap();
   private static final Map<String, Long> craftCostCache = new ConcurrentHashMap();
   private static final AtomicBoolean isFetchingBazaar = new AtomicBoolean(false);
   private static final AtomicBoolean isFetchingPrices = new AtomicBoolean(false);
   private static final AtomicBoolean isFetchingNpc = new AtomicBoolean(false);
   private static long lastFetchTime = 0L;
   private static long lastBazaarFetch = 0L;
   private static long lastNpcFetch = 0L;
   private static final long CACHE_DURATION = 300000L;
   private static long lastBazaarAttempt = 0L;
   private static long lastPricesAttempt = 0L;
   private static long lastNpcAttempt = 0L;

   public static void ensureLoaded() {
      long now = System.currentTimeMillis();
      boolean bazaarFresh = now - lastBazaarFetch < 300000L;
      if (!bazaarFresh && now - lastBazaarAttempt < 30000L) {
         bazaarFresh = true;
      }

      boolean pricesFresh = !priceCache.isEmpty() && now - lastFetchTime < 300000L;
      if (!pricesFresh && now - lastPricesAttempt < 30000L) {
         pricesFresh = true;
      }

      boolean npcFresh = !npcCache.isEmpty() && now - lastNpcFetch < 300000L;
      if (!npcFresh && now - lastNpcAttempt < 30000L) {
         npcFresh = true;
      }

      if (!bazaarFresh || !pricesFresh || !npcFresh) {
         reload();
      }

   }

   public static void reload() {
      long now = System.currentTimeMillis();
      boolean bazaarFresh = now - lastBazaarFetch < 300000L;
      boolean pricesFresh = !priceCache.isEmpty() && now - lastFetchTime < 300000L;
      boolean npcFresh = !npcCache.isEmpty() && now - lastNpcFetch < 300000L;
      if (!bazaarFresh && now - lastBazaarAttempt >= 30000L) {
         lastBazaarAttempt = now;
         fetchFromBazaar();
      }

      if (!pricesFresh && now - lastPricesAttempt >= 30000L) {
         lastPricesAttempt = now;
         fetchFromPrices();
      }

      if (!npcFresh && now - lastNpcAttempt >= 30000L) {
         lastNpcAttempt = now;
         fetchFromNpc();
      }

      BitsManager.ensureLoaded();
   }

   public static String getStatus() {
      long now = System.currentTimeMillis();
      boolean bazaarFresh = now - lastBazaarFetch < 300000L;
      boolean pricesFresh = now - lastFetchTime < 300000L;
      StringBuilder sb = new StringBuilder("§6API Status:\n");
      sb.append("§7- Prices: ").append(pricesFresh ? "§aFresh" : "§cStale").append(" §8(").append(priceCache.size()).append(" ids)\n");
      sb.append("§7- Bazaar: ").append(bazaarFresh ? "§aFresh" : "§cStale").append(" §8(").append(bazaarCache.size()).append(" ids)\n");
      sb.append("§7- NPC: ").append(!npcCache.isEmpty() && now - lastNpcFetch < 300000L ? "§aFresh" : "§cStale").append(" §8(").append(npcCache.size()).append(" ids)\n");
      sb.append("§7- Bits: §aLoaded §8(").append(BitsManager.bitCostCache.size()).append(" ids)");
      return sb.toString();
   }

   public static String findIdByName(String name) {
      return findIdByName(name, false);
   }

   public static String findIdByName(String name, boolean isStrict) {
      String search = name.toLowerCase().trim().replace(" ", "_");
      if (search.startsWith("e_")) {
         search = "ENCHANTED_" + search.substring(2);
      } else if (!search.startsWith("enchanted_") && search.startsWith("s_")) {
         search = "SUPER_" + search.substring(2);
      }

      String upperSearch = search.toUpperCase();
      if (bazaarCache.containsKey(upperSearch)) {
         return upperSearch;
      } else if (priceCache.containsKey(upperSearch)) {
         return upperSearch;
      } else if (isStrict) {
         return null;
      } else {
         for(String id : bazaarCache.keySet()) {
            if (id.toLowerCase().contains(search)) {
               return id;
            }
         }

         for(String id : priceCache.keySet()) {
            if (id.toLowerCase().contains(search)) {
               return id;
            }
         }

         return null;
      }
   }

   public static long getCachedPrice(String skyblockId) {
      if (skyblockId != null && !skyblockId.isEmpty()) {
         skyblockId = mapEnchantedBookId(skyblockId);
         long price = getRawPrice(skyblockId);
         if (price > 0L) {
            return price;
         } else {
            if (skyblockId.contains(";")) {
               String[] parts = skyblockId.split(";");
               if (parts.length >= 2) {
                  String petName = parts[0];
                  String rarityPart = parts[1];
                  boolean lvl100 = rarityPart.contains("+100");
                  if (lvl100) {
                     rarityPart = rarityPart.replace("+100", "");
                  }

                  try {
                     int rarityNum = Integer.parseInt(rarityPart);
                     String rarity = "";
                     if (rarityNum == 0) {
                        rarity = "COMMON";
                     } else if (rarityNum == 1) {
                        rarity = "UNCOMMON";
                     } else if (rarityNum == 2) {
                        rarity = "RARE";
                     } else if (rarityNum == 3) {
                        rarity = "EPIC";
                     } else if (rarityNum == 4) {
                        rarity = "LEGENDARY";
                     } else if (rarityNum == 5) {
                        rarity = "MYTHIC";
                     }

                     if (!rarity.isEmpty()) {
                        String suffix = lvl100 ? "-100" : "";
                        price = getRawPrice("PET-" + petName + "-" + rarity + suffix);
                        if (price > 0L) {
                           return price;
                        }

                        price = getRawPrice("PET_" + petName + "_" + rarity + suffix);
                        if (price > 0L) {
                           return price;
                        }

                        String lvlPrefix = lvl100 ? "LVL_100_" : "LVL_1_";
                        price = getRawPrice(lvlPrefix + rarity + "_" + petName);
                        if (price > 0L) {
                           return price;
                        }
                     }
                  } catch (Exception var14) {
                  }
               }
            }

            if (skyblockId.startsWith("PET_") || skyblockId.startsWith("PET-") || skyblockId.contains(";")) {
               boolean lvl100 = skyblockId.endsWith("-100");
               boolean lvl200 = skyblockId.endsWith("-200");
               String baseId = skyblockId;
               if (lvl100 || lvl200) {
                  baseId = skyblockId.substring(0, skyblockId.length() - 4);
               }

               if (baseId.contains(";")) {
                  String prefixRemoved = baseId.startsWith("PET-") ? baseId.substring(4) : baseId;
                  String[] split = prefixRemoved.split(";");
                  if (split.length >= 2) {
                     String petName = split[0];
                     String rarityNum = split[1];
                     String rarity = "COMMON";
                     if (rarityNum.equals("1")) {
                        rarity = "UNCOMMON";
                     } else if (rarityNum.equals("2")) {
                        rarity = "RARE";
                     } else if (rarityNum.equals("3")) {
                        rarity = "EPIC";
                     } else if (rarityNum.equals("4")) {
                        rarity = "LEGENDARY";
                     } else if (rarityNum.equals("5")) {
                        rarity = "MYTHIC";
                     }

                     String lvlPrefix = lvl200 ? "LVL_200_" : (lvl100 ? "LVL_100_" : "LVL_1_");
                     price = getRawPrice(lvlPrefix + rarity + "_" + petName);
                     if (price > 0L) {
                        return price;
                     }

                     String odId = "PET-" + petName + "-" + rarity + (lvl200 ? "-200" : (lvl100 ? "-100" : ""));
                     price = getRawPrice(odId);
                     if (price > 0L) {
                        return price;
                     }
                  }
               } else {
                  String[] parts = baseId.split("[_-]");
                  if (parts.length >= 3) {
                     String rarity = parts[parts.length - 1];
                     String petName = baseId.substring(4, baseId.length() - rarity.length() - 1);
                     int rarityNum = 0;
                     if (rarity.equals("UNCOMMON")) {
                        rarityNum = 1;
                     } else if (rarity.equals("RARE")) {
                        rarityNum = 2;
                     } else if (rarity.equals("EPIC")) {
                        rarityNum = 3;
                     } else if (rarity.equals("LEGENDARY")) {
                        rarityNum = 4;
                     } else if (rarity.equals("MYTHIC")) {
                        rarityNum = 5;
                     }

                     String suffix = lvl200 ? "+200" : (lvl100 ? "+100" : "");
                     price = getRawPrice(petName + ";" + rarityNum + suffix);
                     if (price > 0L) {
                        return price;
                     }

                     String lvlPrefix = lvl200 ? "LVL_200_" : (lvl100 ? "LVL_100_" : "LVL_1_");
                     price = getRawPrice(lvlPrefix + rarity + "_" + petName);
                     if (price > 0L) {
                        return price;
                     }

                     String odId = "PET-" + petName + "-" + rarity + (lvl200 ? "-200" : (lvl100 ? "-100" : ""));
                     price = getRawPrice(odId);
                     if (price > 0L) {
                        return price;
                     }
                  }
               }
            }

            if (BomboConfig.get().petPriceDebug && skyblockId.startsWith("PET")) {
               Minecraft.getInstance().player.sendSystemMessage(Component.literal("§d[Pet Price Debug] Tested ID: §f" + skyblockId));
               Minecraft.getInstance().player.sendSystemMessage(Component.literal("§d[Pet Price Debug] Price found: §6" + price));
            }

            if (skyblockId.startsWith("ENCHANTMENT_")) {
               String[] parts = skyblockId.split("_");
               if (parts.length >= 3) {
                  String level = parts[parts.length - 1];
                  String base = skyblockId.substring(0, skyblockId.lastIndexOf("_"));
                  String[] roman = new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

                  try {
                     int lvl = Integer.parseInt(level);
                     if (lvl > 0 && lvl < roman.length) {
                        price = getRawPrice(base + "_" + roman[lvl]);
                        if (price > 0L) {
                           return price;
                        }
                     }
                  } catch (Exception var13) {
                  }
               }
            }

            String fuzzy = findIdByName(skyblockId, true);
            return fuzzy != null && !fuzzy.equals(skyblockId) ? getCachedPrice(fuzzy) : -1L;
         }
      } else {
         return -1L;
      }
   }

   private static long getRawPrice(String id) {
      if (bazaarCache.containsKey(id)) {
         double price = (Double)bazaarCache.get(id);
         if (price <= (double)0.0F && bazaarSellCache.containsKey(id)) {
            price = (Double)bazaarSellCache.get(id);
         }

         if (price > (double)0.0F) {
            return Math.round(price);
         }
      }

      if (id.startsWith("SHARD_") || id.endsWith("_SHARD")) {
         String alt = id.startsWith("SHARD_") ? id.substring(6) + "_SHARD" : "SHARD_" + id.substring(0, id.length() - 6);
         if (bazaarCache.containsKey(alt)) {
            double price = (Double)bazaarCache.get(alt);
            if (price <= (double)0.0F && bazaarSellCache.containsKey(alt)) {
               price = (Double)bazaarSellCache.get(alt);
            }

            if (price > (double)0.0F) {
               return Math.round(price);
            }
         }
      }

      if (priceCache.containsKey(id)) {
         return (Long)priceCache.get(id);
      } else {
         if (id.contains(";")) {
            String baseId = id.split(";")[0];
            if (bazaarCache.containsKey(baseId)) {
               double price = (Double)bazaarCache.get(baseId);
               if (price <= (double)0.0F && bazaarSellCache.containsKey(baseId)) {
                  price = (Double)bazaarSellCache.get(baseId);
               }

               if (price > (double)0.0F) {
                  return Math.round(price);
               }
            }

            if (priceCache.containsKey(baseId)) {
               return (Long)priceCache.get(baseId);
            }
         }

         return -1L;
      }
   }

   public static boolean isBazaar(String skyblockId) {
      if (skyblockId == null) {
         return false;
      } else {
         skyblockId = mapEnchantedBookId(skyblockId);
         if (bazaarCache.containsKey(skyblockId)) {
            return true;
         } else if (!skyblockId.startsWith("SHARD_") && !skyblockId.endsWith("_SHARD")) {
            return skyblockId.contains(";") ? bazaarCache.containsKey(skyblockId.split(";")[0]) : false;
         } else {
            return true;
         }
      }
   }

   public static CompletableFuture<Long> getLowestBin(String skyblockId) {
      return skyblockId == null ? CompletableFuture.completedFuture(-1L) : CompletableFuture.completedFuture(getCachedPrice(skyblockId));
   }

   public static long getNpcPrice(String skyblockId) {
      if (skyblockId == null) {
         return -1L;
      } else {
         skyblockId = mapEnchantedBookId(skyblockId);
         if (npcCache.containsKey(skyblockId)) {
            return (Long)npcCache.get(skyblockId);
         } else {
            if (skyblockId.contains(";")) {
               String baseId = skyblockId.split(";")[0];
               if (npcCache.containsKey(baseId)) {
                  return (Long)npcCache.get(baseId);
               }
            }

            return -1L;
         }
      }
   }

   public static long getBuyPrice(String skyblockId) {
      long price = getCachedPrice(skyblockId);
      return price > 0L ? price : 0L;
   }

   public static long getSellPrice(String skyblockId) {
      if (skyblockId != null && !skyblockId.isEmpty()) {
         if (bazaarSellCache.containsKey(skyblockId)) {
            return Math.round((Double)bazaarSellCache.get(skyblockId));
         } else {
            if (skyblockId.contains(";")) {
               String baseId = skyblockId.split(";")[0];
               if (bazaarSellCache.containsKey(baseId)) {
                  return Math.round((Double)bazaarSellCache.get(baseId));
               }
            }

            return getBuyPrice(skyblockId);
         }
      } else {
         return 0L;
      }
   }

   private static CompletableFuture<Boolean> fetchFromBazaar() {
      if (!isFetchingBazaar.compareAndSet(false, true)) {
         return CompletableFuture.completedFuture(false);
      } else {
         String url = "https://api.hypixel.net/skyblock/bazaar";
         Bomboaddons.logApiRequest(url);
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Bomboaddons)").timeout(Duration.ofSeconds(10L)).GET().build();
         return client.sendAsync(request, BodyHandlers.ofString()).thenApply((response) -> {
            if (response.statusCode() == 200) {
               try {
                  JsonObject json = JsonParser.parseString((String)response.body()).getAsJsonObject();
                  if (json.has("success") && json.get("success").getAsBoolean()) {
                     JsonObject products = json.getAsJsonObject("products");

                     for(String key : products.keySet()) {
                        JsonObject product = products.getAsJsonObject(key);
                        if (product.has("quick_status")) {
                           double buyPrice = product.getAsJsonObject("quick_status").get("buyPrice").getAsDouble();
                           double sellPrice = product.getAsJsonObject("quick_status").get("sellPrice").getAsDouble();
                           double price = buyPrice > (double)0.0F ? buyPrice : sellPrice;
                           bazaarCache.put(key, price);
                           bazaarSellCache.put(key, sellPrice);
                        }
                     }

                     lastBazaarFetch = System.currentTimeMillis();
                     craftCostCache.clear();
                     if (BomboConfig.get().debugMode) {
                        Bomboaddons.sendMessage("§7[Debug] Loaded " + products.size() + " Bazaar prices");
                     }

                     return true;
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }

            return false;
         }).exceptionally((ex) -> false).whenComplete((res, ex) -> isFetchingBazaar.set(false));
      }
   }

   private static CompletableFuture<Boolean> fetchFromPrices() {
      return !isFetchingPrices.compareAndSet(false, true) ? CompletableFuture.completedFuture(false) : fetchFromUrl("https://api.eliteskyblock.com/resources/auctions/neu").thenCompose((success) -> success ? CompletableFuture.completedFuture(true) : fetchFromUrl("https://api.odtheking.com/lb/lowestbins")).thenCompose((success) -> success ? CompletableFuture.completedFuture(true) : fetchFromUrl("https://maro.skyblockextras.com/api/auctions/all")).thenCompose((success) -> success ? CompletableFuture.completedFuture(true) : fetchFromUrl(BomboApiUrl.getApiUrl("/prices2"))).thenCompose((success) -> success ? CompletableFuture.completedFuture(true) : fetchFromUrl(BomboApiUrl.getApiUrl("/prices"))).whenComplete((res, ex) -> isFetchingPrices.set(false));
   }

   private static CompletableFuture<Boolean> fetchFromNpc() {
      if (!isFetchingNpc.compareAndSet(false, true)) {
         return CompletableFuture.completedFuture(false);
      } else {
         String url = "https://api.hypixel.net/resources/skyblock/items";
         Bomboaddons.logApiRequest(url);
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Bomboaddons)").timeout(Duration.ofSeconds(10L)).GET().build();
         return client.sendAsync(request, BodyHandlers.ofString()).thenApply((response) -> {
            if (response.statusCode() == 200) {
               try {
                  JsonObject json = JsonParser.parseString((String)response.body()).getAsJsonObject();
                  if (json.has("items") && json.get("items").isJsonArray()) {
                     JsonArray dataArray = json.getAsJsonArray("items");
                     int count = 0;

                     for(JsonElement element : dataArray) {
                        if (element.isJsonObject()) {
                           JsonObject item = element.getAsJsonObject();
                           if (item.has("id") && item.has("npc_sell_price")) {
                              String id = item.get("id").getAsString();
                              long value = Math.round(item.get("npc_sell_price").getAsDouble());
                              npcCache.put(id, value);
                              if (id.contains(";")) {
                                 npcCache.putIfAbsent(id.split(";")[0], value);
                              }

                              ++count;
                           }
                        }
                     }

                     lastNpcFetch = System.currentTimeMillis();
                     craftCostCache.clear();
                     if (BomboConfig.get().debugMode) {
                        Bomboaddons.sendMessage("§7[Debug] Loaded " + count + " NPC prices");
                     }

                     return true;
                  }
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }

            return false;
         }).exceptionally((ex) -> false).whenComplete((res, ex) -> isFetchingNpc.set(false));
      }
   }

   private static CompletableFuture<Boolean> fetchFromUrl(String url) {
      Bomboaddons.logApiRequest(url);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Bomboaddons)").timeout(Duration.ofSeconds(10L)).GET().build();
      return client.sendAsync(request, BodyHandlers.ofString()).thenApply((response) -> {
         if (response.statusCode() == 200) {
            try {
               JsonElement root = JsonParser.parseString((String)response.body());
               int count = 0;
               if (root.isJsonObject()) {
                  JsonObject json = root.getAsJsonObject();
                  if (json.has("data")) {
                     JsonElement data = json.get("data");
                     if (data.isJsonArray()) {
                        for(JsonElement element : data.getAsJsonArray()) {
                           if (element.isJsonObject()) {
                              JsonObject item = element.getAsJsonObject();
                              if (item.has("id") && item.has("value")) {
                                 String id = item.get("id").getAsString();
                                 long value = Math.round(item.get("value").getAsDouble());
                                 priceCache.put(id, value);
                                 if (id.contains(";")) {
                                    priceCache.putIfAbsent(id.split(";")[0], value);
                                 }

                                 ++count;
                              }
                           }
                        }
                     } else if (data.isJsonObject()) {
                        JsonObject dataObj = data.getAsJsonObject();

                        for(String key : dataObj.keySet()) {
                           JsonElement val = dataObj.get(key);
                           if (val.isJsonPrimitive()) {
                              long value = Math.round(val.getAsDouble());
                              priceCache.put(key, value);
                              if (key.contains(";")) {
                                 priceCache.putIfAbsent(key.split(";")[0], value);
                              }

                              ++count;
                           }
                        }
                     }
                  } else {
                     for(String key : json.keySet()) {
                        JsonElement val = json.get(key);
                        if (val.isJsonPrimitive()) {
                           try {
                              long value = Math.round(val.getAsDouble());
                              priceCache.put(key, value);
                              if (key.contains(";")) {
                                 priceCache.putIfAbsent(key.split(";")[0], value);
                              }

                              ++count;
                           } catch (Exception var13) {
                           }
                        }
                     }
                  }
               } else if (root.isJsonArray()) {
                  for(JsonElement element : root.getAsJsonArray()) {
                     if (element.isJsonObject()) {
                        JsonObject item = element.getAsJsonObject();
                        if (item.has("id") && item.has("value")) {
                           String id = item.get("id").getAsString();
                           long value = Math.round(item.get("value").getAsDouble());
                           priceCache.put(id, value);
                           if (id.contains(";")) {
                              priceCache.putIfAbsent(id.split(";")[0], value);
                           }

                           ++count;
                        }
                     }
                  }
               }

               if (count > 0) {
                  lastFetchTime = System.currentTimeMillis();
                  craftCostCache.clear();
                  if (BomboConfig.get().debugMode) {
                     Bomboaddons.sendMessage("§7[Debug] Loaded " + count + " prices from " + url);
                  }

                  return true;
               }
            } catch (Exception e) {
               e.printStackTrace();
            }
         }

         return false;
      }).exceptionally((ex) -> {
         ex.printStackTrace();
         return false;
      });
   }

   public static long getCraftCostCached(String skyblockId) {
      if (skyblockId != null && !skyblockId.isEmpty()) {
         if (!SkyblockItemManager.isLoaded()) {
            return -1L;
         } else if (craftCostCache.containsKey(skyblockId)) {
            return (Long)craftCostCache.get(skyblockId);
         } else {
            long cost = getCraftCost(skyblockId, new HashSet());
            craftCostCache.put(skyblockId, cost);
            return cost;
         }
      } else {
         return -1L;
      }
   }

   private static long getCraftCost(String skyblockId, Set<String> visited) {
      if (skyblockId != null && !skyblockId.isEmpty() && !visited.contains(skyblockId)) {
         if (craftCostCache.containsKey(skyblockId)) {
            return (Long)craftCostCache.get(skyblockId);
         } else {
            visited.add(skyblockId);
            SkyblockItemManager.SkyblockItemInfo info = SkyblockItemManager.getInfo(skyblockId);
            if (info != null && info.recipes != null && !info.recipes.isEmpty()) {
               long minCraftCost = -1L;

               for(JsonElement el : info.recipes) {
                  if (el.isJsonObject()) {
                     JsonObject recipeObj = el.getAsJsonObject();
                     long currentRecipeCost = 0L;
                     boolean canCraft = true;
                     int outputCount = recipeObj.has("count") ? parseJsonCount(recipeObj.get("count")) : 1;
                     Map<String, Integer> requiredItems = new HashMap();
                     if (recipeObj.has("A1")) {
                        String[] keys = new String[]{"A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3"};

                        for(String key : keys) {
                           if (recipeObj.has(key)) {
                              String inputStr = recipeObj.get(key).getAsString();
                              if (!inputStr.isEmpty()) {
                                 String[] parts = inputStr.split(":");
                                 String inId = parts[0];
                                 int inCount = parts.length > 1 ? parseCount(parts[1]) : 1;
                                 requiredItems.put(inId, (Integer)requiredItems.getOrDefault(inId, 0) + inCount);
                              }
                           }
                        }
                     } else if (recipeObj.has("inputs") && recipeObj.get("inputs").isJsonArray()) {
                        JsonArray inputs = recipeObj.getAsJsonArray("inputs");

                        for(int i = 0; i < inputs.size(); ++i) {
                           String inputStr = inputs.get(i).getAsString();
                           if (!inputStr.isEmpty()) {
                              String[] parts = inputStr.split(":");
                              String inId = parts[0];
                              int inCount = parts.length > 1 ? parseCount(parts[1]) : 1;
                              requiredItems.put(inId, (Integer)requiredItems.getOrDefault(inId, 0) + inCount);
                           }
                        }
                     }

                     if (!requiredItems.isEmpty()) {
                        for(Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                           String reqId = (String)entry.getKey();
                           int count = (Integer)entry.getValue();
                           long itemCost = getCachedPrice(reqId);
                           if (itemCost <= 0L) {
                              itemCost = getCraftCost(reqId, visited);
                           } else {
                              long subCraftCost = getCraftCost(reqId, visited);
                              if (subCraftCost > 0L && subCraftCost < itemCost) {
                                 itemCost = subCraftCost;
                              }
                           }

                           if (itemCost <= 0L) {
                              canCraft = false;
                              break;
                           }

                           currentRecipeCost += itemCost * (long)count;
                        }

                        if (canCraft) {
                           long costPerItem = currentRecipeCost / (long)outputCount;
                           if (minCraftCost == -1L || costPerItem < minCraftCost) {
                              minCraftCost = costPerItem;
                           }
                        }
                     }
                  }
               }

               visited.remove(skyblockId);
               if (minCraftCost > 0L) {
                  craftCostCache.put(skyblockId, minCraftCost);
               }

               return minCraftCost;
            } else {
               visited.remove(skyblockId);
               return -1L;
            }
         }
      } else {
         return -1L;
      }
   }

   private static int parseCount(String str) {
      if (str != null && !str.isEmpty()) {
         try {
            String clean = str.trim();
            return clean.contains(".") ? (int)Math.round(Double.parseDouble(clean)) : Integer.parseInt(clean);
         } catch (Exception var2) {
            return 1;
         }
      } else {
         return 1;
      }
   }

   private static int parseJsonCount(JsonElement el) {
      if (el != null && !el.isJsonNull()) {
         try {
            if (el.isJsonPrimitive()) {
               String str = el.getAsString();
               return parseCount(str);
            }
         } catch (Exception var2) {
         }

         return 1;
      } else {
         return 1;
      }
   }

   public static String formatPrice(long price) {
      if (price >= 1000000000L) {
         return String.format("%.2fB", (double)price / (double)1.0E9F);
      } else if (price >= 1000000L) {
         return String.format("%.2fM", (double)price / (double)1000000.0F);
      } else {
         return price >= 1000L ? String.format("%.1fK", (double)price / (double)1000.0F) : String.valueOf(price);
      }
   }

   public static String formatPrice(double price) {
      if (price >= (double)1.0E9F) {
         return String.format("%.2fB", price / (double)1.0E9F);
      } else if (price >= (double)1000000.0F) {
         return String.format("%.2fM", price / (double)1000000.0F);
      } else if (price >= (double)1000.0F) {
         return String.format("%.1fK", price / (double)1000.0F);
      } else {
         return price == (double)((long)price) ? String.valueOf((long)price) : String.format("%.3f", price);
      }
   }

   public static String mapEnchantedBookId(String skyblockId) {
      if (skyblockId == null) {
         return null;
      } else {
         String encName = null;
         String levelStr = null;
         if (skyblockId.contains(";")) {
            String[] parts = skyblockId.split(";");
            if (parts.length == 2) {
               encName = parts[0].toUpperCase();
               levelStr = parts[1];
            }
         } else if (skyblockId.startsWith("ENCHANTMENT_") || skyblockId.startsWith("ENCHANTED_BOOK_")) {
            int prefixLen = skyblockId.startsWith("ENCHANTMENT_") ? 12 : 15;
            int lastUnderscore = skyblockId.lastIndexOf("_");
            if (lastUnderscore > prefixLen) {
               encName = skyblockId.substring(prefixLen, lastUnderscore).toUpperCase();
               levelStr = skyblockId.substring(lastUnderscore + 1);
            }
         }

         if (encName != null && levelStr != null) {
            if (encName.equals("CHINERA")) {
               encName = "CHIMERA";
            }

            List<String> ultimates = List.of("CHIMERA", "ONE_FOR_ALL", "SOUL_EATER", "LEGION", "LAST_STAND", "WISDOM", "BANK", "COMBO", "NO_PAIN_NO_GAIN", "INFERNO", "FATAL_TEMPO", "REND", "FLASH", "JERRY", "HABANERO_TACTICUS", "THE_ONE");
            if (ultimates.contains(encName)) {
               encName = "ULTIMATE_" + encName;
            }

            String officialBz = "ENCHANTMENT_" + encName + "_" + levelStr;
            if (!bazaarCache.containsKey(officialBz) && !priceCache.containsKey(officialBz)) {
               String candidate = "ENCHANTED_BOOK-" + encName + "-" + levelStr;
               if (!bazaarCache.containsKey(candidate) && !priceCache.containsKey(candidate)) {
                  String[] roman = new String[]{"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

                  try {
                     int lvl = Integer.parseInt(levelStr);
                     if (lvl > 0 && lvl < roman.length) {
                        label118: {
                           String r = roman[lvl];
                           String candBzRoman = "ENCHANTMENT_" + encName + "_" + r;
                           if (!bazaarCache.containsKey(candBzRoman) && !priceCache.containsKey(candBzRoman)) {
                              String candRoman = "ENCHANTED_BOOK-" + encName + "-" + r;
                              if (!bazaarCache.containsKey(candRoman) && !priceCache.containsKey(candRoman)) {
                                 break label118;
                              }

                              return candRoman;
                           }

                           return candBzRoman;
                        }
                     }
                  } catch (Exception var11) {
                  }

                  int arabicLvl = RomanNumber.romanToDecimal(levelStr);
                  if (arabicLvl > 0) {
                     String candBzArabic = "ENCHANTMENT_" + encName + "_" + arabicLvl;
                     if (bazaarCache.containsKey(candBzArabic) || priceCache.containsKey(candBzArabic)) {
                        return candBzArabic;
                     }

                     String candArabic = "ENCHANTED_BOOK-" + encName + "-" + arabicLvl;
                     if (bazaarCache.containsKey(candArabic) || priceCache.containsKey(candArabic)) {
                        return candArabic;
                     }
                  }

                  return officialBz;
               } else {
                  return candidate;
               }
            } else {
               return officialBz;
            }
         } else {
            return skyblockId;
         }
      }
   }
}
