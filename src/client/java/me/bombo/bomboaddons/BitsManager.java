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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import me.bombo.bomboaddons.util.BomboApiUrl;

public class BitsManager {
   private static final HttpClient client = HttpClient.newBuilder().build();
   public static final Map<String, Integer> bitCostCache = new ConcurrentHashMap();
   private static final AtomicBoolean isFetching;
   private static long lastFetchTime;
   private static long lastAttemptTime;

   public static CompletableFuture<Boolean> ensureLoaded() {
      long now = System.currentTimeMillis();
      if (!bitCostCache.isEmpty() && now - lastFetchTime < 300000L) {
         return CompletableFuture.completedFuture(true);
      } else if (now - lastAttemptTime < 30000L) {
         return CompletableFuture.completedFuture(false);
      } else if (!isFetching.compareAndSet(false, true)) {
         return CompletableFuture.completedFuture(false);
      } else {
         lastAttemptTime = now;
         String url = BomboApiUrl.getApiUrl("/commands/bits");
         HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8").header("Accept-Language", "en-US,en;q=0.9").header("Upgrade-Insecure-Requests", "1").timeout(Duration.ofSeconds(10L)).GET().build();
         return client.sendAsync(request, BodyHandlers.ofString()).thenApply((response) -> {
            if (response.statusCode() == 200) {
               try {
                  for(JsonElement element : JsonParser.parseString((String)response.body()).getAsJsonArray()) {
                     JsonObject obj = element.getAsJsonObject();
                     if (obj.has("name") && obj.has("bits")) {
                        bitCostCache.put(obj.get("name").getAsString(), obj.get("bits").getAsInt());
                     }
                  }

                  lastFetchTime = System.currentTimeMillis();
                  return true;
               } catch (Exception var5) {
               }
            }

            return false;
         }).exceptionally((ex) -> false).whenComplete((res, ex) -> isFetching.set(false));
      }
   }

   private static double getPrice(String id) {
      if ("CARROT_ITEM".equals(id)) {
         return (double)LowestBinManager.getSellPrice("CARROT_ITEM");
      } else {
         return id != null && !id.isEmpty() ? (double)LowestBinManager.getSellPrice(id) : (double)0.0F;
      }
   }

   private static void getLocalTopBits(int amount, List<String> results, String reason) {
      try {
         List<BitItem> items = new ArrayList();

         for(SimpleBitItem item : List.of(new SimpleBitItem("GOD_POTION_2", 1500), new SimpleBitItem("KISMET_FEATHER", 1350), new SimpleBitItem("KAT_FLOWER", 500), new SimpleBitItem("KAT_BOUQUET", 2500), new SimpleBitItem("MATRIARCH_PARFUM", 1200), new SimpleBitItem("HOLOGRAM", 2000), new SimpleBitItem("DITTO_BLOB", 600), new SimpleBitItem("BUILDERS_WAND", 12000), new SimpleBitItem("BLOCK_ZAPPER", 5000), new SimpleBitItem("BITS_TALISMAN", 15000), new SimpleBitItem("SHARD_BITBUG", 5000), new SimpleBitItem("POCKET_SACK_IN_A_SACK", 8000), new SimpleBitItem("PORTALIZER", 4800), new SimpleBitItem("TRIO_CONTACTS_ADDON", 6450), new SimpleBitItem("ABICASE_SUMSUNG_1", 15000, "SUMSUNG_1"), new SimpleBitItem("ABICASE_SUMSUNG_2", 25000, "SUMSUNG_2"), new SimpleBitItem("ABICASE_REZAR", 26000), new SimpleBitItem("ABICASE_BLUE_RED", 17000), new SimpleBitItem("ABICASE_BLUE_BLUE", 17000), new SimpleBitItem("ABICASE_BLUE_GREEN", 17000), new SimpleBitItem("ABICASE_BLUE_YELLOW", 17000), new SimpleBitItem("ABICASE_BLUE_AQUA", 17000), new SimpleBitItem("AUTOPET_RULES_2", 21000), new SimpleBitItem("DYE_PURE_BLACK", 250000), new SimpleBitItem("DYE_PURE_WHITE", 250000), new SimpleBitItem("ENCHANTMENT_EXPERTISE_1", 4000), new SimpleBitItem("ENCHANTMENT_COMPACT_1", 4000), new SimpleBitItem("ENCHANTMENT_CULTIVATING_1", 4000), new SimpleBitItem("ENCHANTMENT_ABSORB_1", 4000), new SimpleBitItem("ENCHANTMENT_CHAMPION_1", 4000), new SimpleBitItem("ENCHANTMENT_HECATOMB_1", 6000), new SimpleBitItem("ENCHANTMENT_TOXOPHILITE_1", 4000), new SimpleBitItem("TALISMAN_ENRICHMENT_SWAPPER", 200))) {
            double price = getPrice(item.id);
            if (price > (double)0.0F) {
               items.add(new BitItem(item.displayName, price / (double)item.bits));
            }
         }

         double infernoPrice = getPrice("INFERNO_FUEL_BLOCK");
         if (infernoPrice > (double)0.0F) {
            items.add(new BitItem("INFERNO_FUEL_BLOCK", infernoPrice * (double)64.0F / (double)3120.0F));
         }

         String[] ENRICHMENTS = new String[]{"TALISMAN_ENRICHMENT_WALK_SPEED", "TALISMAN_ENRICHMENT_INTELLIGENCE", "TALISMAN_ENRICHMENT_CRITICAL_DAMAGE", "TALISMAN_ENRICHMENT_CRITICAL_CHANCE", "TALISMAN_ENRICHMENT_STRENGTH", "TALISMAN_ENRICHMENT_DEFENSE", "TALISMAN_ENRICHMENT_HEALTH", "TALISMAN_ENRICHMENT_MAGIC_FIND", "TALISMAN_ENRICHMENT_FEROCITY", "TALISMAN_ENRICHMENT_SEA_CREATURE_CHANCE", "TALISMAN_ENRICHMENT_ATTACK_SPEED"};
         double cheapestEnrichmentPrice = Double.MAX_VALUE;

         for(String enr : ENRICHMENTS) {
            double p = getPrice(enr);
            if (p > (double)0.0F && p < cheapestEnrichmentPrice) {
               cheapestEnrichmentPrice = p;
            }
         }

         if (cheapestEnrichmentPrice != Double.MAX_VALUE) {
            items.add(new BitItem("CHEAPEST_ENRICHMENT", cheapestEnrichmentPrice / (double)5000.0F));
         }

         double heatCorePrice = getPrice("HEAT_CORE");
         if (heatCorePrice > (double)0.0F) {
            items.add(new BitItem("HEAT_CORE (Raw)", heatCorePrice / (double)3000.0F));
         }

         double plasmaPrice = getPrice("PLASMA_BUCKET");
         double magmaPrice = getPrice("MAGMA_BUCKET");
         double enchCoalBlock = getPrice("ENCHANTED_COAL_BLOCK");
         double enchIron = getPrice("ENCHANTED_IRON");
         if (enchCoalBlock > (double)0.0F && enchIron > (double)0.0F) {
            if (magmaPrice > (double)0.0F) {
               double costVal = (double)4.0F * enchCoalBlock + (double)6.0F * enchIron;
               items.add(new BitItem("MAGMA_BUCKET (Crafted)", (magmaPrice - costVal) / (double)3000.0F));
            }

            if (plasmaPrice > (double)0.0F) {
               double costVal = (double)8.0F * enchCoalBlock + (double)12.0F * enchIron;
               items.add(new BitItem("PLASMA_BUCKET (Crafted)", (plasmaPrice - costVal) / (double)9000.0F));
            }
         }

         double hcuPrice = getPrice("HYPER_CATALYST_UPGRADE");
         if (hcuPrice > (double)0.0F) {
            items.add(new BitItem("HYPER_CATALYST_UPGRADE (Raw)", hcuPrice / (double)300.0F));
         }

         double hyperCatalystPrice = getPrice("HYPER_CATALYST");
         double catalystPrice = getPrice("CATALYST");
         if (hyperCatalystPrice > (double)0.0F && catalystPrice > (double)0.0F) {
            double made = hyperCatalystPrice * (double)8.0F;
            double cost = catalystPrice * (double)8.0F;
            items.add(new BitItem("HYPER_CATALYST (Crafted 8x)", (made - cost) / (double)300.0F));
         }

         double uccuPrice = getPrice("ULTIMATE_CARROT_CANDY_UPGRADE");
         if (uccuPrice > (double)0.0F) {
            items.add(new BitItem("ULTIMATE_CARROT_CANDY_UPGRADE (Raw)", uccuPrice / (double)8000.0F));
         }

         double uccPrice = getPrice("ULTIMATE_CARROT_CANDY");
         double carrotMenuPrice = getPrice("CARROT_ITEM");
         double enchCarrotPrice = getPrice("ENCHANTED_CARROT");
         if (uccPrice > (double)0.0F && carrotMenuPrice > (double)0.0F && enchCarrotPrice > (double)0.0F) {
            double totalIngredientsCost = (double)33280.0F * enchCarrotPrice + (double)4608.0F * carrotMenuPrice;
            items.add(new BitItem("ULTIMATE_CARROT_CANDY (Crafted 10x)", (uccPrice * (double)10.0F - totalIngredientsCost) / (double)8000.0F));
         }

         double cebuPrice = getPrice("COLOSSAL_EXP_BOTTLE_UPGRADE");
         if (cebuPrice > (double)0.0F) {
            items.add(new BitItem("COLOSSAL_EXP_BOTTLE_UPGRADE (Raw)", cebuPrice / (double)1200.0F));
         }

         double colossalPrice = getPrice("COLOSSAL_EXP_BOTTLE");
         double titanicPrice = getPrice("TITANIC_EXP_BOTTLE");
         if (colossalPrice > (double)0.0F && titanicPrice > (double)0.0F) {
            items.add(new BitItem("COLOSSAL_EXP_BOTTLE (Crafted)", (colossalPrice - titanicPrice) / (double)1200.0F));
         }

         double jbuPrice = getPrice("JUMBO_BACKPACK_UPGRADE");
         if (jbuPrice > (double)0.0F) {
            items.add(new BitItem("JUMBO_BACKPACK_UPGRADE (Raw)", jbuPrice / (double)4000.0F));
         }

         double jumboPrice = getPrice("JUMBO_BACKPACK");
         double enchLeatherPrice = getPrice("ENCHANTED_LEATHER");
         double leatherPrice = getPrice("LEATHER");
         if (jumboPrice > (double)0.0F && enchLeatherPrice > (double)0.0F && leatherPrice > (double)0.0F) {
            double leatherCost = (double)144.0F * enchLeatherPrice + (double)512.0F * leatherPrice;
            items.add(new BitItem("JUMBO_BACKPACK (Crafted)", (jumboPrice - leatherCost) / (double)4000.0F));
         }

         double msePrice = getPrice("MINION_STORAGE_EXPANDER");
         if (msePrice > (double)0.0F) {
            items.add(new BitItem("MINION_STORAGE_EXPANDER (Raw)", msePrice / (double)1500.0F));
         }

         if (!items.isEmpty()) {
            items.sort(Comparator.comparingDouble((BitItem b) -> b.profitPerBit).reversed());
            results.add("§6Profit Per Bit §7(Local Fallback)");

            for(int i = 0; i < Math.min(amount, items.size()); ++i) {
               BitItem item = (BitItem)items.get(i);
               results.add(String.format("§e%s: §a%d", item.formattedName, Math.round(item.profitPerBit)));
            }
         } else {
            results.add("§c" + reason);
         }
      } catch (Exception var52) {
         results.add("§c" + reason);
      }

   }

   public static CompletableFuture<List<String>> fetchTopBits(int amount) {
      String url = BomboApiUrl.getApiUrl("/commands/bits");
      Bomboaddons.logApiRequest(url);
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36").header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8").header("Accept-Language", "en-US,en;q=0.9").header("Upgrade-Insecure-Requests", "1").timeout(Duration.ofSeconds(10L)).GET().build();
      return client.sendAsync(request, BodyHandlers.ofString()).thenApply((response) -> {
         List<String> results = new ArrayList();
         if (BomboConfig.get().apiDebug) {
            results.add("§b[Debug] API: " + url);
         }

         if (response.statusCode() == 200) {
            try {
               JsonArray jsonArray = JsonParser.parseString((String)response.body()).getAsJsonArray();
               List<BitItem> items = new ArrayList();

               for(JsonElement element : jsonArray) {
                  JsonObject obj = element.getAsJsonObject();
                  if (obj.has("name") && obj.has("profit_per_bit")) {
                     String name = obj.get("name").getAsString();
                     double profit = obj.get("profit_per_bit").getAsDouble();
                     items.add(new BitItem(name, profit));
                  }
               }

               items.sort(Comparator.comparingDouble((BitItem b) -> b.profitPerBit).reversed());
               results.add("§b[Bits] §6Top Bits Profit / Bit:");

               for(int i = 0; i < Math.min(amount, items.size()); ++i) {
                  BitItem item = (BitItem)items.get(i);
                  results.add(String.format("§e#%d %s: §a%,d coins/bit", i + 1, item.formattedName, Math.round(item.profitPerBit)));
               }
            } catch (Exception var12) {
               getLocalTopBits(amount, results, "Failed to parse Bits API response.");
            }
         } else {
            getLocalTopBits(amount, results, "Bits API returned error code: " + response.statusCode());
         }

         return results;
      }).exceptionally((ex) -> {
         List<String> results = new ArrayList();
         getLocalTopBits(amount, results, "Failed to connect to Bits API.");
         return results;
      });
   }

   static {
      bitCostCache.put("GOD_POTION_2", 1500);
      bitCostCache.put("KISMET_FEATHER", 1350);
      bitCostCache.put("KAT_FLOWER", 500);
      bitCostCache.put("KAT_BOUQUET", 2500);
      bitCostCache.put("MATRIARCH_PARFUM", 1200);
      bitCostCache.put("HOLOGRAM", 2000);
      bitCostCache.put("DITTO_BLOB", 600);
      bitCostCache.put("BUILDERS_WAND", 12000);
      bitCostCache.put("BLOCK_ZAPPER", 5000);
      bitCostCache.put("BITS_TALISMAN", 15000);
      bitCostCache.put("SHARD_BITBUG", 5000);
      bitCostCache.put("POCKET_SACK_IN_A_SACK", 8000);
      bitCostCache.put("PORTALIZER", 4800);
      bitCostCache.put("TRIO_CONTACTS_ADDON", 6450);
      bitCostCache.put("AUTOPET_RULES_2", 21000);
      bitCostCache.put("ENCHANTMENT_EXPERTISE_1", 4000);
      bitCostCache.put("ENCHANTMENT_COMPACT_1", 4000);
      bitCostCache.put("ENCHANTMENT_CULTIVATING_1", 4000);
      bitCostCache.put("ENCHANTMENT_ABSORB_1", 4000);
      bitCostCache.put("ENCHANTMENT_CHAMPION_1", 4000);
      bitCostCache.put("ENCHANTMENT_HECATOMB_1", 6000);
      bitCostCache.put("ENCHANTMENT_TOXOPHILITE_1", 4000);
      bitCostCache.put("TALISMAN_ENRICHMENT_SWAPPER", 200);
      bitCostCache.put("HEAT_CORE", 3000);
      bitCostCache.put("HYPER_CATALYST_UPGRADE", 300);
      bitCostCache.put("ULTIMATE_CARROT_CANDY_UPGRADE", 8000);
      bitCostCache.put("COLOSSAL_EXP_BOTTLE_UPGRADE", 1200);
      bitCostCache.put("JUMBO_BACKPACK_UPGRADE", 4000);
      bitCostCache.put("MINION_STORAGE_EXPANDER", 1500);
      isFetching = new AtomicBoolean(false);
      lastFetchTime = 0L;
      lastAttemptTime = 0L;
   }

   public static class BitItem {
      public String originalName;
      public String formattedName;
      public double profitPerBit;

      public BitItem(String originalName, double profitPerBit) {
         this.originalName = originalName;
         this.profitPerBit = profitPerBit;
         this.formattedName = this.formatName(originalName);
      }

      private String formatName(String name) {
         String suffix = "";
         int bracketIndex = name.indexOf("(");
         if (bracketIndex != -1) {
            String var10000 = name.substring(bracketIndex);
            suffix = " " + var10000.trim();
            name = name.substring(0, bracketIndex).trim();
         }

         String[] parts = name.toLowerCase().split("_");
         StringBuilder sb = new StringBuilder();

         for(String part : parts) {
            if (part.length() > 0) {
               sb.append(Character.toUpperCase(part.charAt(0)));
               if (part.length() > 1) {
                  sb.append(part.substring(1));
               }

               sb.append(" ");
            }
         }

         String var10 = sb.toString().trim();
         return var10 + suffix;
      }
   }

   private static class SimpleBitItem {
      final String id;
      final int bits;
      final String displayName;

      SimpleBitItem(String id, int bits) {
         this.id = id;
         this.bits = bits;
         this.displayName = id;
      }

      SimpleBitItem(String id, int bits, String displayName) {
         this.id = id;
         this.bits = bits;
         this.displayName = displayName;
      }
   }
}
