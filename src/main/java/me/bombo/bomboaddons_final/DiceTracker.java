package me.bombo.bomboaddons_final;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiceTracker {
    private static final File SAVE_FILE = FabricLoader.getInstance().getConfigDir().resolve("bombo/dice_stats.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern DICE_PATTERN = Pattern.compile("^Your (High Class )?Archfiend Dice rolled a ([1-7])!");
    
    private static long lastRollTime = 0;
    private static int lastRollValue = 0;
    private static long lastHeldTime = 0;

    public static class Stats {
        public Map<String, Integer> normalRolls = new HashMap<>(); 
        public Map<String, Integer> highClassRolls = new HashMap<>();
        public long totalRolls = 0;
        
        public long normalDicesUsed = 0;
        public long highClassDicesUsed = 0;
        
        public long totalSpent = 0; // Cumulative (rolls + dice lost)
        public long totalEarned = 0;
        
        // Backward compatibility
        public transient Map<String, Integer> rolls; 
        public transient long dicesUsed;
    }

    private static Stats stats = new Stats();

    public static void init() {
        load();
    }

    public static void load() {
        if (SAVE_FILE.exists()) {
            try (FileReader reader = new FileReader(SAVE_FILE)) {
                stats = GSON.fromJson(reader, Stats.class);
                if (stats == null) stats = new Stats();
                
                // Migrate old rolls to normalRolls
                if (stats.rolls != null && !stats.rolls.isEmpty()) {
                    for (Map.Entry<String, Integer> entry : stats.rolls.entrySet()) {
                        stats.normalRolls.put(entry.getKey(), stats.normalRolls.getOrDefault(entry.getKey(), 0) + entry.getValue());
                    }
                    stats.rolls = null;
                }
                
                // Migrate old dicesUsed to normalDicesUsed
                if (stats.dicesUsed > 0) {
                    stats.normalDicesUsed += stats.dicesUsed;
                    stats.dicesUsed = 0;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        if (!SAVE_FILE.getParentFile().exists()) SAVE_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            GSON.toJson(stats, writer);
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

            // 1. Cost per roll
            long rollCost = isHighClass ? 6666666 : 666666;
            stats.totalSpent += rollCost;

            // 2. Cost of dice lost (on 6 or 7)
            if (roll >= 6) {
                long dicePrice = isHighClass ? LowestBinManager.getCachedPrice("HIGH_CLASS_ARCHFIEND_DICE") : LowestBinManager.getCachedPrice("ARCHFIEND_DICE");
                if (dicePrice <= 0) dicePrice = isHighClass ? 6666666 : 666666;
                
                stats.totalSpent += dicePrice;
                if (isHighClass) stats.highClassDicesUsed++;
                else stats.normalDicesUsed++;
            }

            // 3. Rewards
            long reward = 0;
            if (roll == 6) {
                reward = isHighClass ? 100000000 : 15000000;
            } else if (roll == 7) {
                reward = LowestBinManager.getCachedPrice("DYE_ARCHFIEND");
                if (reward < 0) reward = 0;
            }
            stats.totalEarned += reward;

            // Stats tracking
            stats.totalRolls++;
            if (isHighClass) {
                stats.highClassRolls.put(rollKey, stats.highClassRolls.getOrDefault(rollKey, 0) + 1);
            } else {
                stats.normalRolls.put(rollKey, stats.normalRolls.getOrDefault(rollKey, 0) + 1);
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
    
    public static boolean isHoldingDice() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean holding = isDice(main) || isDice(off);
        if (holding) lastHeldTime = System.currentTimeMillis();
        return holding;
    }
    
    private static boolean isDice(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String name = stack.getHoverName().getString().toLowerCase();
        return name.contains("archfiend dice");
    }
    
    public static boolean shouldShowHud() {
        if (isHoldingDice()) return true;
        
        long now = System.currentTimeMillis();
        if (now - lastHeldTime < 10000) return true;
        
        long elapsed = now - lastRollTime;
        if (elapsed < 30000) return true;
        
        return false;
    }
    
    public static void reset() {
        stats = new Stats();
        save();
    }
}
