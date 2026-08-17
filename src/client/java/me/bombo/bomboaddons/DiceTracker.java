package me.bombo.bomboaddons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class DiceTracker {
   private static final File SAVE_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/dice_stats.json").toFile();
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final Pattern DICE_PATTERN = Pattern.compile("^Your (High Class )?Archfiend Dice rolled a ([1-7])!");
   private static long lastRollTime = 0L;
   private static int lastRollValue = 0;
   private static long lastHeldTime = 0L;
   private static Stats stats = new Stats();
   private static final Stats sessionStats = new Stats();

   public static void init() {
      load();
   }

   public static void load() {
      if (SAVE_FILE.exists()) {
         try {
            FileReader reader = new FileReader(SAVE_FILE);

            try {
               stats = (Stats)GSON.fromJson(reader, Stats.class);
               if (stats == null) {
                  stats = new Stats();
               }

               if (stats.rolls != null && !stats.rolls.isEmpty()) {
                  for(Map.Entry<String, Integer> entry : stats.rolls.entrySet()) {
                     stats.normalRolls.put((String)entry.getKey(), (Integer)stats.normalRolls.getOrDefault(entry.getKey(), 0) + (Integer)entry.getValue());
                  }

                  stats.rolls = null;
               }

               if (stats.dicesUsed > 0L) {
                  Stats var10000 = stats;
                  var10000.normalDicesUsed += stats.dicesUsed;
                  stats.dicesUsed = 0L;
               }
            } catch (Throwable var4) {
               try {
                  reader.close();
               } catch (Throwable var3) {
                  var4.addSuppressed(var3);
               }

               throw var4;
            }

            reader.close();
         } catch (Exception e) {
            e.printStackTrace();
         }
      }

   }

   public static void save() {
      try {
         String json = GSON.toJson(stats);
         CompletableFuture.runAsync(() -> {
            try {
               if (!SAVE_FILE.getParentFile().exists()) {
                  SAVE_FILE.getParentFile().mkdirs();
               }

               FileWriter writer = new FileWriter(SAVE_FILE);

               try {
                  writer.write(json);
               } catch (Throwable var5) {
                  try {
                     writer.close();
                  } catch (Throwable x2) {
                     var5.addSuppressed(x2);
                  }

                  throw var5;
               }

               writer.close();
            } catch (Exception e) {
               e.printStackTrace();
            }

         });
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public static void onChatMessage(String message) {
      Matcher matcher = DICE_PATTERN.matcher(message);
      if (matcher.find()) {
         boolean isHighClass = matcher.group(1) != null;
         int roll = Integer.parseInt(matcher.group(2));
         String rollKey = String.valueOf(roll);
         long rollCost = isHighClass ? 6666666L : 666666L;
         Stats var10000 = stats;
         var10000.totalSpent += rollCost;
         var10000 = sessionStats;
         var10000.totalSpent += rollCost;
         if (roll >= 6) {
            long dicePrice = isHighClass ? LowestBinManager.getCachedPrice("HIGH_CLASS_ARCHFIEND_DICE") : LowestBinManager.getCachedPrice("ARCHFIEND_DICE");
            if (dicePrice <= 0L) {
               dicePrice = isHighClass ? 6666666L : 666666L;
            }

            var10000 = stats;
            var10000.totalSpent += dicePrice;
            var10000 = sessionStats;
            var10000.totalSpent += dicePrice;
            if (isHighClass) {
               ++stats.highClassDicesUsed;
               ++sessionStats.highClassDicesUsed;
            } else {
               ++stats.normalDicesUsed;
               ++sessionStats.normalDicesUsed;
            }
         }

         long reward = 0L;
         if (roll == 6) {
            reward = isHighClass ? 100000000L : 15000000L;
         } else if (roll == 7) {
            reward = LowestBinManager.getCachedPrice("DYE_ARCHFIEND");
            if (reward < 0L) {
               reward = 0L;
            }
         }

         var10000 = stats;
         var10000.totalEarned += reward;
         var10000 = sessionStats;
         var10000.totalEarned += reward;
         ++stats.totalRolls;
         ++sessionStats.totalRolls;
         if (isHighClass) {
            stats.highClassRolls.put(rollKey, (Integer)stats.highClassRolls.getOrDefault(rollKey, 0) + 1);
            sessionStats.highClassRolls.put(rollKey, (Integer)sessionStats.highClassRolls.getOrDefault(rollKey, 0) + 1);
         } else {
            stats.normalRolls.put(rollKey, (Integer)stats.normalRolls.getOrDefault(rollKey, 0) + 1);
            sessionStats.normalRolls.put(rollKey, (Integer)sessionStats.normalRolls.getOrDefault(rollKey, 0) + 1);
         }

         lastRollTime = System.currentTimeMillis();
         lastRollValue = roll;
         save();
      }

   }

   public static long getLastRollTime() {
      return lastRollTime;
   }

   public static int getLastRollValue() {
      return lastRollValue;
   }

   public static Stats getStats() {
      return stats;
   }

   public static Stats getSessionStats() {
      return sessionStats;
   }

   public static boolean isHoldingDice() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return false;
      } else {
         ItemStack main = mc.player.getMainHandItem();
         ItemStack off = mc.player.getOffhandItem();
         boolean holding = isDice(main) || isDice(off);
         if (holding) {
            lastHeldTime = System.currentTimeMillis();
         }

         return holding;
      }
   }

   private static boolean isDice(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      } else {
         String name = stack.getHoverName().getString().toLowerCase();
         return name.contains("archfiend dice");
      }
   }

   public static boolean shouldShowHud() {
      if (isHoldingDice()) {
         return true;
      } else {
         long now = System.currentTimeMillis();
         if (now - lastHeldTime < 10000L) {
            return true;
         } else {
            long elapsed = now - lastRollTime;
            return elapsed < 30000L;
         }
      }
   }

   public static void reset() {
      stats = new Stats();
      save();
   }

   public static class Stats {
      public Map<String, Integer> normalRolls = new HashMap();
      public Map<String, Integer> highClassRolls = new HashMap();
      public long totalRolls = 0L;
      public long normalDicesUsed = 0L;
      public long highClassDicesUsed = 0L;
      public long totalSpent = 0L;
      public long totalEarned = 0L;
      public transient Map<String, Integer> rolls;
      public transient long dicesUsed;
   }
}
