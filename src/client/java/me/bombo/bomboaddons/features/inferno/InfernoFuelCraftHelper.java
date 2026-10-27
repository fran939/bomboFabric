package me.bombo.bomboaddons.features.inferno;

import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Calculates raw material requirements, intermediate craft deductions,
 * Bazaar costs, and batch crafting sizes for Inferno Minion Hypergolic fuels.
 */
public class InfernoFuelCraftHelper {
    public static final int BAZAAR_ORDER_LIMIT = 71680;

    // Configured target fuels to craft (default 32)
    public static int targetFuels = 32;

    public static class CraftItem {
        public String id;
        public String name;
        public String icon;
        public String bzCommand;
        public boolean isRawMaterial; // true = bought on Bazaar; false = intermediate craft
        public int needed;
        public int haveInv;
        public int havePersonalSacks;
        public int haveCoopSacks;
        public int toBuy;
        public int toCraft;
        public long unitPrice;
        public long totalCost;

        public int getTotalHave() {
            return haveInv + havePersonalSacks + haveCoopSacks;
        }

        public int getPersonalHave() {
            return haveInv + havePersonalSacks;
        }
    }

    public static class CraftPlan {
        public int targetFuels;
        public List<CraftItem> rawMaterials = new ArrayList<>();
        public List<CraftItem> intermediates = new ArrayList<>();
        public long totalEstimatedBuyCost = 0L;
        public int totalMissingRawTypes = 0;
        public int totalReadyRawTypes = 0;
    }

    /**
     * Computes the complete craft plan for the current targetFuels,
     * accounting for intermediate items and materials in inventory/sacks.
     */
    public static CraftPlan computePlan() {
        CraftPlan plan = new CraftPlan();
        plan.targetFuels = Math.max(1, targetFuels);
        int N = plan.targetFuels;

        // 1. Existing items inventory & sacks
        int haveHypergolic = getCombinedHave("HYPERGOLIC_GABAGOOL");
        int haveHeavy = getCombinedHave("HEAVY_GABAGOOL");
        int haveFuelGabagool = getCombinedHave("FUEL_GABAGOOL");
        int haveVeryCrude = getCombinedHave("VERY_CRUDE_GABAGOOL");
        int haveSulphuricCoal = getCombinedHave("SULPHURIC_COAL");
        int haveEnchantedCoal = getCombinedHave("ENCHANTED_COAL");
        int haveEnchantedSulphur = getCombinedHave("ENCHANTED_SULPHUR");
        int haveDistillate = getCombinedHave("CRUDE_GABAGOOL_DISTILLATE");
        int haveFuelBlock = getCombinedHave("INFERNO_FUEL_BLOCK");

        // Step 1: Hypergolic Gabagool (1 per fuel)
        int neededHypergolic = N;
        int craftHypergolic = Math.max(0, neededHypergolic - haveHypergolic);

        // Step 2: Heavy Gabagool (12 per Hypergolic craft)
        int neededHeavy = craftHypergolic * 12;
        int craftHeavy = Math.max(0, neededHeavy - haveHeavy);

        // Step 3: Fuel Gabagool (24 per Heavy Gabagool craft)
        int neededFuelGabagool = craftHeavy * 24;
        int craftFuelGabagool = Math.max(0, neededFuelGabagool - haveFuelGabagool);

        // Fuel Gabagool craft: 1 Very Crude + 8 Sulphuric Coal = 8 Fuel Gabagool
        int fuelBatches = (craftFuelGabagool + 7) / 8;
        int neededVeryCrude = fuelBatches * 1;
        int toBuyVeryCrude = Math.max(0, neededVeryCrude - haveVeryCrude);

        // Step 4: Sulphuric Coal
        // 1 per Hypergolic craft + 1 per Heavy Gabagool craft + 8 per Fuel Gabagool batch
        int totalSulphuricCoal = (craftHypergolic * 1) + (craftHeavy * 1) + (fuelBatches * 8);
        int craftSulphuricCoal = Math.max(0, totalSulphuricCoal - haveSulphuricCoal);

        // Sulphuric Coal craft: 16 Enchanted Coal + 1 Enchanted Sulphur = 4 Sulphuric Coal
        int sulphurBatches = (craftSulphuricCoal + 3) / 4;
        int neededEnchantedCoal = sulphurBatches * 16;
        int neededEnchantedSulphur = sulphurBatches * 1;

        int toBuyEnchantedCoal = Math.max(0, neededEnchantedCoal - haveEnchantedCoal);
        int toBuyEnchantedSulphur = Math.max(0, neededEnchantedSulphur - haveEnchantedSulphur);

        // Step 5: Distillate & Fuel Block
        int neededDistillate = N * 6;
        int toBuyDistillate = Math.max(0, neededDistillate - haveDistillate);

        int neededFuelBlock = N * 2;
        int toBuyFuelBlock = Math.max(0, neededFuelBlock - haveFuelBlock);

        // Build Raw Material items
        CraftItem distillate = createItem("CRUDE_GABAGOOL_DISTILLATE", "Crude Gabagool Distillate", "🧪", "crude gabagool distillate", true, neededDistillate, toBuyDistillate, 0);
        CraftItem fuelBlock = createItem("INFERNO_FUEL_BLOCK", "Inferno Fuel Block", "🧱", "inferno fuel block", true, neededFuelBlock, toBuyFuelBlock, 0);
        CraftItem veryCrude = createItem("VERY_CRUDE_GABAGOOL", "Very Crude Gabagool", "🍖", "very crude gabagool", true, neededVeryCrude, toBuyVeryCrude, 0);
        CraftItem enchCoal = createItem("ENCHANTED_COAL", "Enchanted Coal", "⚫", "enchanted coal", true, neededEnchantedCoal, toBuyEnchantedCoal, 0);
        CraftItem enchSulphur = createItem("ENCHANTED_SULPHUR", "Enchanted Sulphur", "🟡", "enchanted sulphur", true, neededEnchantedSulphur, toBuyEnchantedSulphur, 0);

        plan.rawMaterials.add(distillate);
        plan.rawMaterials.add(fuelBlock);
        plan.rawMaterials.add(veryCrude);
        plan.rawMaterials.add(enchCoal);
        plan.rawMaterials.add(enchSulphur);

        // Build Intermediate items
        CraftItem sulphCoal = createItem("SULPHURIC_COAL", "Sulphuric Coal", "🔥", "sulphuric coal", false, totalSulphuricCoal, 0, craftSulphuricCoal);
        CraftItem fuelGab = createItem("FUEL_GABAGOOL", "Fuel Gabagool", "⛽", "fuel gabagool", false, neededFuelGabagool, 0, craftFuelGabagool);
        CraftItem heavyGab = createItem("HEAVY_GABAGOOL", "Heavy Gabagool", "📦", "heavy gabagool", false, neededHeavy, 0, craftHeavy);
        CraftItem hyperGab = createItem("HYPERGOLIC_GABAGOOL", "Hypergolic Gabagool", "⚡", "hypergolic gabagool", false, neededHypergolic, 0, craftHypergolic);

        plan.intermediates.add(sulphCoal);
        plan.intermediates.add(fuelGab);
        plan.intermediates.add(heavyGab);
        plan.intermediates.add(hyperGab);

        // Tally costs
        for (CraftItem r : plan.rawMaterials) {
            plan.totalEstimatedBuyCost += r.totalCost;
            if (r.toBuy > 0) plan.totalMissingRawTypes++;
            else plan.totalReadyRawTypes++;
        }

        return plan;
    }

    private static CraftItem createItem(String id, String name, String icon, String bzCmd, boolean isRaw, int needed, int toBuy, int toCraft) {
        CraftItem item = new CraftItem();
        item.id = id;
        item.name = name;
        item.icon = icon;
        item.bzCommand = bzCmd != null ? bzCmd.replace("_", " ").trim() : "";
        item.isRawMaterial = isRaw;
        item.needed = needed;
        item.haveInv = SacksHelper.getPhysicalInventoryCount(id);
        item.havePersonalSacks = SacksHelper.getPersonalSackCount(id);
        item.haveCoopSacks = SacksHelper.getCoopSackCount(id, false);
        item.toBuy = toBuy;
        item.toCraft = toCraft;
        item.unitPrice = getBazaarPrice(id);
        item.totalCost = (long) toBuy * item.unitPrice;
        return item;
    }

    private static int getCombinedHave(String id) {
        return SacksHelper.getPhysicalInventoryCount(id)
                + SacksHelper.getPersonalSackCount(id)
                + SacksHelper.getCoopSackCount(id, false);
    }

    public static long getBazaarPrice(String id) {
        long p = LowestBinManager.getCachedPrice(id);
        if (p > 0) return p;
        // Fallback prices if API cache is offline
        switch (id) {
            case "ENCHANTED_COAL": return 1300L;
            case "ENCHANTED_SULPHUR": return 12000L;
            case "CRUDE_GABAGOOL_DISTILLATE": return 18000L;
            case "INFERNO_FUEL_BLOCK": return 145000L;
            case "VERY_CRUDE_GABAGOOL": return 1608L;
            case "FUEL_GABAGOOL": return 14500L;
            case "HEAVY_GABAGOOL": return 350000L;
            case "SULPHURIC_COAL": return 6500L;
            case "HYPERGOLIC_GABAGOOL": return 4350000L;
            default: return 1000L;
        }
    }

    /**
     * Executes the click action:
     * - For raw materials with deficit: copies buy amount to clipboard and executes `/bz <item name>`.
     * - For intermediate crafts or completed items: executes `/viewrecipe <item ID>`.
     */
    public static void executeBazaarAction(CraftItem item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!item.isRawMaterial) {
            mc.player.connection.sendCommand("viewrecipe " + item.id);
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Craft Helper§8] §aOpened §e/viewrecipe " + item.id + "§a!"));
            return;
        }

        if (item.toBuy <= 0) {
            mc.player.connection.sendCommand("viewrecipe " + item.id);
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Craft Helper§8] §aYou have all needed §f" + item.name + "§a! (§e" + formatNum(item.getTotalHave()) + "§a/§e" + formatNum(item.needed) + "§a) - Opened §e/viewrecipe " + item.id));
            return;
        }

        int toCopy = Math.min(item.toBuy, BAZAAR_ORDER_LIMIT);
        mc.keyboardHandler.setClipboard(String.valueOf(toCopy));

        String searchName = item.bzCommand.replace("_", " ").trim();
        mc.player.connection.sendCommand("bz " + searchName);

        if (item.toBuy > BAZAAR_ORDER_LIMIT) {
            int remaining = item.toBuy - toCopy;
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Craft Helper§8] §aOpened §e/bz " + searchName + " §aand copied §b" + formatNum(toCopy) + " §ato clipboard! §7(Max order reached, §c" + formatNum(remaining) + " §7remaining)"));
        } else {
            mc.player.sendSystemMessage(Component.literal("§8[§6🔥 Craft Helper§8] §aOpened §e/bz " + searchName + " §aand copied §b" + formatNum(toCopy) + " §ato clipboard!"));
        }
    }

    /**
     * Computes the batch size for crafting inside a GUI like "Sulphuric Coal" or "Crafting Table",
     * preventing inventory overflow based on empty slots.
     */
    public static class InGuiBatchInfo {
        public String title;
        public int emptySlots;
        public int totalNeeded;
        public int remainingToCraft;
        public int maxCraftsForInventory;
        public int outputPerCraft;
        public int totalOutputThisBatch;
        public Map<String, Integer> ingredientsNeeded = new LinkedHashMap<>();
    }

    public static void setTargetFuels(int amount) {
        targetFuels = Math.max(1, amount);
        try {
            me.bombo.bomboaddons.BomboConfig.get().infernoTargetCraftFuels = targetFuels;
            me.bombo.bomboaddons.BomboConfig.save();
        } catch (Throwable ignored) {}
    }

    public static void refreshSacks() {
        SacksHelper.refreshAllCoopSacks();
    }

    public static InGuiBatchInfo getContainerBatchInfo(String screenTitle) {
        if (screenTitle == null) return null;
        String clean = ChatFormatting.stripFormatting(screenTitle).trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        // Count empty slots in player's 36 inventory slots
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) {
            var st = mc.player.getInventory().getItem(i);
            if (st == null || st.isEmpty()) {
                emptySlots++;
            }
        }

        CraftPlan plan = computePlan();
        InGuiBatchInfo info = new InGuiBatchInfo();
        info.title = clean;
        info.emptySlots = emptySlots;

        if (lower.contains("sulphuric coal") || lower.contains("sulphur coal") || (lower.contains("coal") && lower.contains("sulphur"))) {
            CraftItem sc = findItem(plan.intermediates, "SULPHURIC_COAL");
            if (sc == null) return null;
            info.totalNeeded = sc.needed;
            info.remainingToCraft = sc.toCraft;
            info.outputPerCraft = 4;

            // 1 craft gives 4 items. Each empty slot holds 64 items (= 16 crafts).
            int maxBatches = Math.max(1, emptySlots * 16);
            int craftsNeeded = (sc.toCraft + 3) / 4;
            int batches = Math.min(craftsNeeded, maxBatches);

            info.maxCraftsForInventory = batches;
            info.totalOutputThisBatch = batches * 4;
            info.ingredientsNeeded.put("Enchanted Coal", batches * 16);
            info.ingredientsNeeded.put("Enchanted Sulphur", batches * 1);
            return info;
        }

        if (lower.contains("fuel gabagool")) {
            CraftItem fg = findItem(plan.intermediates, "FUEL_GABAGOOL");
            if (fg == null) return null;
            info.totalNeeded = fg.needed;
            info.remainingToCraft = fg.toCraft;
            info.outputPerCraft = 8;

            int maxBatches = Math.max(1, emptySlots * 8);
            int craftsNeeded = (fg.toCraft + 7) / 8;
            int batches = Math.min(craftsNeeded, maxBatches);

            info.maxCraftsForInventory = batches;
            info.totalOutputThisBatch = batches * 8;
            info.ingredientsNeeded.put("Very Crude Gabagool", batches * 1);
            info.ingredientsNeeded.put("Sulphuric Coal", batches * 8);
            return info;
        }

        if (lower.contains("heavy gabagool")) {
            CraftItem hg = findItem(plan.intermediates, "HEAVY_GABAGOOL");
            if (hg == null) return null;
            info.totalNeeded = hg.needed;
            info.remainingToCraft = hg.toCraft;
            info.outputPerCraft = 1;

            int maxBatches = Math.max(1, emptySlots * 64);
            int batches = Math.min(hg.toCraft, maxBatches);

            info.maxCraftsForInventory = batches;
            info.totalOutputThisBatch = batches * 1;
            info.ingredientsNeeded.put("Fuel Gabagool", batches * 24);
            info.ingredientsNeeded.put("Sulphuric Coal", batches * 1);
            return info;
        }

        if (lower.contains("hypergolic")) {
            CraftItem hyp = findItem(plan.intermediates, "HYPERGOLIC_GABAGOOL");
            if (hyp == null) return null;
            info.totalNeeded = hyp.needed;
            info.remainingToCraft = hyp.toCraft;
            info.outputPerCraft = 1;

            int maxBatches = Math.max(1, emptySlots * 64);
            int batches = Math.min(hyp.toCraft, maxBatches);

            info.maxCraftsForInventory = batches;
            info.totalOutputThisBatch = batches * 1;
            info.ingredientsNeeded.put("Heavy Gabagool", batches * 12);
            info.ingredientsNeeded.put("Sulphuric Coal", batches * 1);
            return info;
        }

        return null;
    }

    private static CraftItem findItem(List<CraftItem> list, String id) {
        for (CraftItem it : list) {
            if (it.id.equalsIgnoreCase(id)) return it;
        }
        return null;
    }

    public static String formatNum(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
