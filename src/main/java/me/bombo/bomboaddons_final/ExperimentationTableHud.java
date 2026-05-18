package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExperimentationTableHud {
    private static final Map<String, DetectedRngItem> storedRewards = new ConcurrentHashMap<>();
    private static int scrollIndex = 0;

    private static final Path RNG_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("bombo/bomboaddons_rng.json");
    private static final com.google.gson.Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

    private static long lastRenderFrame = -1;
    private static boolean lastInMenu = false;
    private static long lastScanTime = 0;

    public static class RngDataState {
        public long lastScanTime = 0;
        public Map<String, DetectedRngItem> rewards = new ConcurrentHashMap<>();
    }

    static {
        loadRngFromFile();
    }

    private static void loadRngFromFile() {
        try {
            if (Files.exists(RNG_FILE)) {
                try (Reader reader = Files.newBufferedReader(RNG_FILE)) {
                    RngDataState state = GSON.fromJson(reader, RngDataState.class);
                    if (state != null) {
                        if (state.rewards != null) {
                            storedRewards.putAll(state.rewards);
                        }
                        lastScanTime = state.lastScanTime;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveRngToFile() {
        try {
            if (!Files.exists(RNG_FILE.getParent())) {
                Files.createDirectories(RNG_FILE.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(RNG_FILE)) {
                RngDataState state = new RngDataState();
                state.lastScanTime = lastScanTime;
                state.rewards = storedRewards;
                GSON.toJson(state, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void onHudRender(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        long currentFrame = System.currentTimeMillis();
        // Prevent double rendering in the same frame
        if (currentFrame == lastRenderFrame && !(mc.screen instanceof HudMoveScreen)) return;
        lastRenderFrame = currentFrame;

        BomboConfig.Settings s = BomboConfig.get();
        if (!s.rngProfitHud) return;

        LowestBinManager.ensureLoaded();

        boolean inMenu = false;
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            String title = screen.getTitle().getString();
            if (title.toLowerCase().contains("experimentation table rng")) {
                inMenu = true;
                if (!lastInMenu) {
                    if (s.debugMaster) Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Experimentation Table RNG GUI detected: §f" + title);
                    
                    // Clear only if it's been more than 12 hours since the last scan (meaning a new day's experiment)
                    long now = System.currentTimeMillis();
                    if (lastScanTime > 0 && (now - lastScanTime > 43200000)) { // 12 hours
                        storedRewards.clear();
                        saveRngToFile();
                        scrollIndex = 0;
                        if (s.debugMaster) Bomboaddons.sendMessage("§8[§bBomboAddons§8] §7Cleared stored RNG rewards (New daily experiment/12h timeout).");
                    }
                }
                // Scan every 250ms to avoid constant clearing/refilling
                if (currentFrame - lastScanTime > 250) {
                    scanMenu(screen);
                    lastScanTime = currentFrame;
                    saveRngToFile(); // Persist rewards and timestamp
                }
            }
        }
        lastInMenu = inMenu;

        if (!inMenu && !(mc.screen instanceof HudMoveScreen)) {
            // Keep the items stored so they persist when the screen closes!
            // But don't render if we aren't in the GUI or HudMoveScreen
            return;
        }

        // If in HudMoveScreen and empty, show a dummy
        if (mc.screen instanceof HudMoveScreen && storedRewards.isEmpty()) {
            storedRewards.put("ENCHANTMENT_LOOTING_5", new DetectedRngItem("ENCHANTMENT_LOOTING_5", "Looting V", 500000));
            storedRewards.put("ENCHANTMENT_GROWTH_6", new DetectedRngItem("ENCHANTMENT_GROWTH_6", "Growth VI", 150000));
            storedRewards.put("ENCHANTMENT_GIANT_KILLER_7", new DetectedRngItem("ENCHANTMENT_GIANT_KILLER_7", "Giant Killer VII", 500000));
            storedRewards.put("ENCHANTMENT_CRITICAL_7", new DetectedRngItem("ENCHANTMENT_CRITICAL_7", "Critical VII", 500000));
            storedRewards.put("ENCHANTMENT_SHARPNESS_7", new DetectedRngItem("ENCHANTMENT_SHARPNESS_7", "Sharpness VII", 500000));
            storedRewards.put("ENCHANTMENT_POWER_7", new DetectedRngItem("ENCHANTMENT_POWER_7", "Power VII", 500000));
            storedRewards.put("ENCHANTMENT_PROTECTION_7", new DetectedRngItem("ENCHANTMENT_PROTECTION_7", "Protection VII", 500000));
            storedRewards.put("ENCHANTMENT_CUBISM_6", new DetectedRngItem("ENCHANTMENT_CUBISM_6", "Cubism VI", 150000));
            storedRewards.put("ENCHANTMENT_PROSECUTE_6", new DetectedRngItem("ENCHANTMENT_PROSECUTE_6", "Prosecute VI", 150000));
        }

        if (storedRewards.isEmpty()) return;

        // Filter out items with no price and sort: Highest coins per Metter cost to lowest
        List<DetectedRngItem> toDraw = new ArrayList<>();
        for (DetectedRngItem item : storedRewards.values()) {
            long buyPrice = LowestBinManager.getBuyPrice(item.id);
            long sellPrice = LowestBinManager.getSellPrice(item.id);
            if (buyPrice > 0 || sellPrice > 0) {
                toDraw.add(item);
            }
        }

        if (toDraw.isEmpty()) return;

        toDraw.sort((a, b) -> {
            long pA = LowestBinManager.getSellPrice(a.id);
            long pB = LowestBinManager.getSellPrice(b.id);
            double cpmA = a.metterCost > 0 ? (double) pA / a.metterCost : 0;
            double cpmB = b.metterCost > 0 ? (double) pB / b.metterCost : 0;
            return Double.compare(cpmB, cpmA);
        });

        drawRngInfo(g, s.rngProfitHudX, s.rngProfitHudY, toDraw);
    }

    private static void scanMenu(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
        net.minecraft.world.inventory.AbstractContainerMenu menu = screen.getMenu();
        boolean changed = false;
        for (int i = 0; i < 54; i++) {
            if (i >= menu.slots.size()) break;
            net.minecraft.world.inventory.Slot slot = menu.getSlot(i);
            if (!slot.hasItem()) continue;
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            
            // Only scan center rewards (rows 2-5, columns 2-8)
            int row = i / 9;
            int col = i % 9;
            if (row < 1 || row > 4 || col < 1 || col > 7) continue;
            
            String name = stack.getHoverName().getString();
            String cleanName = name.replaceAll("(?i)§.", "").trim();
            if (cleanName.isEmpty() || cleanName.equalsIgnoreCase("Go Back") || cleanName.equalsIgnoreCase("Close") || cleanName.contains("Page")) continue;
            
            // Detect item rarity from lore lines to map Guardian pets correctly
            String rarity = "COMMON";
            net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null) {
                for (net.minecraft.network.chat.Component line : lore.lines()) {
                    String lineStr = line.getString().toUpperCase();
                    if (lineStr.contains("RARE")) rarity = "RARE";
                    else if (lineStr.contains("EPIC")) rarity = "EPIC";
                    else if (lineStr.contains("LEGENDARY")) rarity = "LEGENDARY";
                    else if (lineStr.contains("MYTHIC")) rarity = "MYTHIC";
                    else if (lineStr.contains("UNCOMMON")) rarity = "UNCOMMON";
                }
            }

            String id = getSkyblockIdFromName(cleanName, rarity);
            if (id.isEmpty() || id.contains("GLASS_PANE") || id.equals("BARRIER")) continue;

            int metterCost = 1;
            if (lore != null) {
                for (net.minecraft.network.chat.Component line : lore.lines()) {
                    String lineStr = line.getString().replaceAll("(?i)§.", "").trim();
                    if (lineStr.contains("Experimental XP:") && lineStr.contains("/")) {
                        String maxStr = lineStr.substring(lineStr.indexOf('/') + 1).replaceAll("[^0-9]", "");
                        if (!maxStr.isEmpty()) {
                            try {
                                metterCost = Integer.parseInt(maxStr);
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    }
                }
            }
            if (metterCost <= 1) {
                metterCost = stack.getCount();
                if (metterCost <= 0) metterCost = 1;
            }
            
            DetectedRngItem existing = storedRewards.get(id);
            if (existing == null || existing.metterCost != metterCost || !existing.name.equals(cleanName)) {
                storedRewards.put(id, new DetectedRngItem(id, cleanName, metterCost));
                changed = true;
            }
        }
        if (changed) {
            saveRngToFile();
        }
    }

    public static void scroll(int delta) {
        BomboConfig.Settings s = BomboConfig.get();
        if (!s.rngProfitHud || storedRewards.isEmpty()) return;

        int total = storedRewards.size();
        if (total <= 8) {
            scrollIndex = 0;
            return;
        }

        // Scroll up (delta > 0) -> decrease scrollIndex
        // Scroll down (delta < 0) -> increase scrollIndex
        if (delta > 0) {
            scrollIndex = Math.max(0, scrollIndex - 1);
        } else if (delta < 0) {
            int maxScroll = (total - 1) - 7;
            scrollIndex = Math.min(maxScroll, scrollIndex + 1);
        }
    }

    public static int getHudHeight() {
        int hasPriceCount = 0;
        for (DetectedRngItem item : storedRewards.values()) {
            long buyPrice = LowestBinManager.getBuyPrice(item.id);
            long sellPrice = LowestBinManager.getSellPrice(item.id);
            if (buyPrice > 0 || sellPrice > 0) {
                hasPriceCount++;
            }
        }
        if (hasPriceCount == 0) return 30;
        int shownCount = Math.min(Math.max(0, hasPriceCount - 1), 7);
        return 18 + 10 + 12 + (shownCount > 0 ? 4 : 0) + (shownCount * 10);
    }

    public static void drawRngInfo(GuiGraphics g, int x, int y, List<DetectedRngItem> items) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        int width = 185;
        
        // Fixed height for exactly 8 enchants total (1 best + up to 7 others)
        int shownCount = Math.min(items.size() - 1, 7);
        int totalEnchantsShown = 1 + shownCount; // Best + others
        int height = 18 + 10 + 12 + (shownCount > 0 ? 4 : 0) + (shownCount * 10);

        // Premium background: Dynamic high contrast for visibility
        int opacity = BomboConfig.get().rngProfitHudOpacity;
        int alpha = (int) (opacity * 2.55);
        int bgColor = (alpha << 24) | 0x00000000;
        
        if (opacity > 0) {
            g.fill(x - 5, y - 5, x + width + 5, y + height + 5, bgColor);
            g.renderOutline(x - 5, y - 5, width + 10, height + 10, 0xFFFFFFFF); // Pure white border
        }

        // Header
        g.drawString(font, "§6§lRNG Experiments Profit", x, y, 0xFFFFFFFF, true);
        
        // Separator line
        g.fill(x, y + 11, x + width, y + 12, 0xAAFFFFFF);

        int curY = y + 18;
        if (items.isEmpty()) {
            g.drawString(font, "§7No items detected", x, curY, 0xFFFFFFFF, true);
            return;
        }

        // 1. Render the Fixed "Most profit" item (index 0)
        g.drawString(font, "§eMost profit:", x, curY, 0xFFFFFFFF, true);
        curY += 10;

        DetectedRngItem bestItem = items.get(0);
        long buyPriceBest = LowestBinManager.getBuyPrice(bestItem.id);
        long sellPriceBest = LowestBinManager.getSellPrice(bestItem.id);

        String bestDisplayName = getPrettyName(bestItem.id, bestItem.name);
        String bestNameText = "§a§l" + (bestDisplayName.length() > 16 ? bestDisplayName.substring(0, 14) + ".." : bestDisplayName);
        
        String bestValueText;
        if (buyPriceBest <= 0 && sellPriceBest <= 0) {
            bestValueText = "§8N/A";
        } else {
            String buyFormatted = formatPriceShort(buyPriceBest);
            String sellFormatted = formatPriceShort(sellPriceBest);
            bestValueText = "§a" + buyFormatted + "/" + sellFormatted;
        }
        
        g.drawString(font, bestNameText, x + 5, curY, 0xFFFFFFFF, true);
        int bestValueWidth = font.width(bestValueText.replaceAll("(?i)§.", ""));
        g.drawString(font, bestValueText, x + width - bestValueWidth - 5, curY, 0xFFFFFFFF, true);
        
        curY += 12; // Extra space after best item

        // Subtle separator before other scrollable items if there are any
        if (shownCount > 0) {
            g.fill(x + 5, curY, x + width - 5, curY + 1, 0x44FFFFFF);
            curY += 4;
        }

        // 2. Render the Scrollable Items (starting from 1 + scrollIndex)
        // Ensure scrollIndex is within valid bounds in case items shrank
        int maxScroll = Math.max(0, (items.size() - 1) - 7);
        if (scrollIndex > maxScroll) {
            scrollIndex = maxScroll;
        }

        for (int i = 0; i < shownCount; i++) {
            int itemIndex = 1 + scrollIndex + i;
            if (itemIndex >= items.size()) break;

            DetectedRngItem item = items.get(itemIndex);
            long buyPrice = LowestBinManager.getBuyPrice(item.id);
            long sellPrice = LowestBinManager.getSellPrice(item.id);

            String displayName = getPrettyName(item.id, item.name);
            String nameText = "§f" + (displayName.length() > 16 ? displayName.substring(0, 14) + ".." : displayName);
            
            String valueText;
            if (buyPrice <= 0 && sellPrice <= 0) {
                valueText = "§8N/A";
            } else {
                String buyFormatted = formatPriceShort(buyPrice);
                String sellFormatted = formatPriceShort(sellPrice);
                valueText = "§7" + buyFormatted + "/" + sellFormatted;
            }
            
            g.drawString(font, nameText, x + 5, curY, 0xFFFFFFFF, true);
            int valueWidth = font.width(valueText.replaceAll("(?i)§.", ""));
            g.drawString(font, valueText, x + width - valueWidth - 5, curY, 0xFFFFFFFF, true);
            
            curY += 10;
        }

        // Render Scrollbar indicator if items exceed what can be shown
        if (items.size() - 1 > 7) {
            int scrollbarX = x + width - 2;
            int scrollbarY = y + 44;
            int scrollbarHeight = height - 48;
            
            // Background of scrollbar
            g.fill(scrollbarX, scrollbarY, scrollbarX + 2, scrollbarY + scrollbarHeight, 0x33FFFFFF);
            
            // Thumb of scrollbar
            double scrollPct = (double) scrollIndex / maxScroll;
            int thumbHeight = 15;
            int thumbY = scrollbarY + (int) (scrollPct * (scrollbarHeight - thumbHeight));
            g.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbHeight, 0xCCFFFFFF);
        }
    }

    public static String getSkyblockIdFromName(String cleanName) {
        return getSkyblockIdFromName(cleanName, "COMMON");
    }

    public static String getSkyblockIdFromName(String cleanName, String rarity) {
        if (cleanName == null || cleanName.isEmpty()) return "";

        String upper = cleanName.toUpperCase().replace(" ", "_");
        if (upper.contains("] ")) {
            String petName = upper.substring(upper.indexOf("] ") + 2).trim();
            return "PET_" + petName + "_" + rarity;
        }

        // Specific SkyBlock ID manual mapping overrides
        if (cleanName.equalsIgnoreCase("End Stone Idol")) return "ENDSTONE_IDOL";
        if (cleanName.equalsIgnoreCase("Chain of the End Times")) return "CHAIN_END_TIMES";
        if (cleanName.equalsIgnoreCase("Grand Experience Bottle")) return "GRAND_EXP_BOTTLE";
        if (cleanName.equalsIgnoreCase("Titanic Experience Bottle")) return "TITANIC_EXP_BOTTLE";
        if (cleanName.equalsIgnoreCase("Colossal Experience Bottle")) return "COLOSSAL_EXP_BOTTLE";

        // Check if it's an enchantment book representation
        String[] parts = cleanName.split("\\s+");
        if (parts.length >= 2) {
            String lastWord = parts[parts.length - 1].toUpperCase();
            if (lastWord.matches("^[IVXLCDM]+$")) {
                int level = RomanNumber.romanToDecimal(lastWord);
                if (level > 0) {
                    StringBuilder baseName = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        baseName.append(parts[i].toUpperCase()).append("_");
                    }
                    String base = baseName.toString().replaceAll("_$", "");
                    return "ENCHANTMENT_" + base + "_" + level;
                }
            }
        }

        // Fuzzy match via LowestBinManager
        String found = LowestBinManager.findIdByName(cleanName, true);
        if (found != null) return found;

        return upper;
    }

    public static String getPrettyName(String id, String fallbackName) {
        if (id.startsWith("ENCHANTMENT_")) {
            String enchantName = id.replace("ENCHANTMENT_", "");
            String[] parts = enchantName.split("_");
            if (parts.length >= 2) {
                try {
                    String name = parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1).toLowerCase();
                    if (parts.length > 2) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < parts.length - 1; i++) {
                            sb.append(parts[i].substring(0, 1).toUpperCase()).append(parts[i].substring(1).toLowerCase()).append(" ");
                        }
                        name = sb.toString().trim();
                    }
                    String level = parts[parts.length - 1];
                    return name + " " + level;
                } catch (Exception e) {}
            }
        }
        return fallbackName;
    }

    public static String formatPriceShort(long price) {
        if (price <= 0) return "0";
        double val;
        String unit;
        if (price >= 1_000_000_000L) {
            val = (double) price / 1.0E9D;
            unit = "b";
        } else if (price >= 1_000_000L) {
            val = (double) price / 1_000_000.0D;
            unit = "m";
        } else if (price >= 1000L) {
            val = (double) price / 1000.0D;
            unit = "k";
        } else {
            return String.valueOf(price);
        }
        if (val == (long) val) {
            return String.format("%d%s", (long) val, unit);
        }
        return String.format("%.1f%s", val, unit);
    }

    public static class DetectedRngItem {
        public String id;
        public String name;
        public int metterCost;

        public DetectedRngItem(String id, String name, int metterCost) {
            this.id = id;
            this.name = name;
            this.metterCost = metterCost;
        }
    }
}
