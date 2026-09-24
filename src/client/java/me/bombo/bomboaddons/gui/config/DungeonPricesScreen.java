package me.bombo.bomboaddons.gui.config;

import java.util.ArrayList;
import java.util.List;
import me.bombo.bomboaddons.LowestBinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class DungeonPricesScreen extends Screen {
    private final Screen parent;
    private String searchQuery = "";
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;
    private List<DungeonItemEntry> allItems = new ArrayList<>();
    private List<DungeonItemEntry> filteredItems = new ArrayList<>();
    private long lastLoadedTime = 0L;

    public static class DungeonItemEntry {
        public final String name;
        public final String id;
        public final String category;
        public final String floorTag;
        public long price;
        public boolean isBazaar;

        public DungeonItemEntry(String name, String id, String category, String floorTag) {
            this.name = name;
            this.id = id;
            this.category = category;
            this.floorTag = floorTag;
            this.price = 0L;
            this.isBazaar = false;
        }
    }

    public DungeonPricesScreen(Screen parent) {
        super(Component.literal("Dungeon Item Prices"));
        this.parent = parent;
        buildDungeonItemList();
    }

    private void buildDungeonItemList() {
        allItems.clear();
        LowestBinManager.ensureLoaded();

        // Scrolls & Handles & RNG
        add("Necron's Handle", "NECRON_HANDLE", "RNG Drop", "Floor VII / M7");
        add("Wither Shield Scroll", "WITHER_SHIELD_SCROLL", "Scroll", "Floor VII / M7");
        add("Implosion Scroll", "IMPLOSION_SCROLL", "Scroll", "Floor VII / M7");
        add("Shadow Warp Scroll", "SHADOW_WARP_SCROLL", "Scroll", "Floor VII / M7");
        add("Auto Recombobulator", "AUTO_RECOMBOBULATOR", "RNG Drop", "Floor VII / M7");
        add("Dark Claymore", "DARK_CLAYMORE", "Weapon", "Master Mode VII");
        add("Giant's Sword", "GIANTS_SWORD", "Weapon", "Floor VI / M6");
        add("Precursor Eye", "PRECURSOR_EYE", "Armor", "Floor VI / M6");
        add("Shadow Fury", "SHADOW_FURY", "Weapon", "Floor V / M5");
        add("Livid Dagger", "LIVID_DAGGER", "Weapon", "Floor V / M5");
        add("Wither Cloak Sword", "WITHER_CLOAK", "Weapon", "Floor VII / M7");
        add("Spirit Sceptre", "SPIRIT_SCEPTRE", "Weapon", "Floor IV / M4");
        add("Spirit Wing", "SPIRIT_WING", "Material", "Floor IV / M4");
        add("Spirit Bone", "SPIRIT_BONE", "Material", "Floor IV / M4");
        add("Spirit Boots", "SPIRIT_BOOTS", "Armor", "Floor IV / M4");
        add("Spirit Pet (Epic)", "SPIRIT_PET_EPIC", "Pet", "Floor IV / M4");
        add("Spirit Pet (Legendary)", "SPIRIT_PET_LEGENDARY", "Pet", "Floor IV / M4");
        add("Bonzo's Staff", "BONZO_STAFF", "Weapon", "Floor I / M1");
        add("Bonzo's Mask", "BONZO_MASK", "Armor", "Floor I / M1");
        add("Adaptive Blade", "ADAPTIVE_BLADE", "Weapon", "Floor II / M2");
        add("Adaptive Chestplate", "ADAPTIVE_CHESTPLATE", "Armor", "Floor III / M3");
        add("Adaptive Leggings", "ADAPTIVE_LEGGINGS", "Armor", "Floor III / M3");
        add("Adaptive Boots", "ADAPTIVE_BOOTS", "Armor", "Floor III / M3");
        add("Adaptive Helmet", "ADAPTIVE_HELMET", "Armor", "Floor III / M3");
        add("Ice Spray Wand", "ICE_SPRAY_WAND", "Weapon", "Floor IV-VII");
        add("Last Breath", "LAST_BREATH", "Weapon", "Floor V / M5");
        add("Fel Sword", "FEL_SWORD", "Weapon", "Floor VI");
        add("Bouquet of Lies", "BOUQUET_OF_LIES", "Weapon", "Floor VI / M6");

        // Master Stars
        add("First Master Star", "FIRST_MASTER_STAR", "Master Star", "Master Mode III");
        add("Second Master Star", "SECOND_MASTER_STAR", "Master Star", "Master Mode IV");
        add("Third Master Star", "THIRD_MASTER_STAR", "Master Star", "Master Mode V");
        add("Fourth Master Star", "FOURTH_MASTER_STAR", "Master Star", "Master Mode VI");
        add("Fifth Master Star", "FIFTH_MASTER_STAR", "Master Star", "Master Mode VII");

        // Armor Sets
        add("Shadow Assassin Chestplate", "SHADOW_ASSASSIN_CHESTPLATE", "Armor", "Floor V / M5");
        add("Shadow Assassin Leggings", "SHADOW_ASSASSIN_LEGGINGS", "Armor", "Floor V / M5");
        add("Shadow Assassin Boots", "SHADOW_ASSASSIN_BOOTS", "Armor", "Floor V / M5");
        add("Shadow Assassin Helmet", "SHADOW_ASSASSIN_HELMET", "Armor", "Floor V / M5");
        add("Necron's Chestplate", "WITHER_CHESTPLATE", "Armor", "Floor VII / M7");
        add("Storm's Chestplate", "STORM_CHESTPLATE", "Armor", "Floor VII / M7");
        add("Maxor's Chestplate", "MAXOR_CHESTPLATE", "Armor", "Floor VII / M7");
        add("Goldor's Chestplate", "GOLDOR_CHESTPLATE", "Armor", "Floor VII / M7");
        add("Necron's Leggings", "WITHER_LEGGINGS", "Armor", "Floor VII / M7");
        add("Storm's Leggings", "STORM_LEGGINGS", "Armor", "Floor VII / M7");
        add("Maxor's Leggings", "MAXOR_LEGGINGS", "Armor", "Floor VII / M7");
        add("Goldor's Leggings", "GOLDOR_LEGGINGS", "Armor", "Floor VII / M7");
        add("Necron's Boots", "WITHER_BOOTS", "Armor", "Floor VII / M7");
        add("Storm's Boots", "STORM_BOOTS", "Armor", "Floor VII / M7");
        add("Maxor's Boots", "MAXOR_BOOTS", "Armor", "Floor VII / M7");
        add("Goldor's Boots", "GOLDOR_BOOTS", "Armor", "Floor VII / M7");
        add("Necron's Helmet", "WITHER_HELMET", "Armor", "Floor VII / M7");
        add("Storm's Helmet", "STORM_HELMET", "Armor", "Floor VII / M7");
        add("Maxor's Helmet", "MAXOR_HELMET", "Armor", "Floor VII / M7");
        add("Goldor's Helmet", "GOLDOR_HELMET", "Armor", "Floor VII / M7");
        add("Necromancer Lord Chestplate", "NECROMANCER_LORD_CHESTPLATE", "Armor", "Floor VI / M6");
        add("Necromancer Lord Leggings", "NECROMANCER_LORD_LEGGINGS", "Armor", "Floor VI / M6");
        add("Necromancer Lord Boots", "NECROMANCER_LORD_BOOTS", "Armor", "Floor VI / M6");
        add("Necromancer Lord Helmet", "NECROMANCER_LORD_HELMET", "Armor", "Floor VI / M6");

        // Stones & Upgrades
        add("Recombobulator 3000", "RECOMBOBULATOR_3000", "Upgrade", "All Floors");
        add("Warped Stone (AOTE Stone)", "AOTE_STONE", "Reforge", "Floor IV / M4");
        add("Spirit Stone (Decoy)", "SPIRIT_DECOY", "Reforge", "Floor IV / M4");
        add("Necromancer's Brooch", "NECROMANCER_BROOCH", "Reforge", "Floor VI / M6");
        add("Sadan's Brooch", "SADAN_BROOCH", "Reforge", "Floor VI / M6");
        add("Red Scarf", "RED_SCARF", "Reforge", "Floor II / M2");
        add("Treasure Talisman", "TREASURE_TALISMAN", "Accessory", "All Floors");
        add("Treasure Ring", "TREASURE_RING", "Accessory", "All Floors");
        add("Treasure Artifact", "TREASURE_ARTIFACT", "Accessory", "All Floors");
        add("Diamante's Handle", "DIAMANTE_HANDLE", "Material", "Floor VII");
        add("Lasa's Cloak", "LASA_CLOAK", "Material", "Floor VII");
        add("Bigfoot's Lasso", "BIGFOOT_LASSO", "Material", "Floor VII");
        add("Jolly Pink Rock", "JOLLY_PINK_ROCK", "Material", "Floor VII");

        // Master Skulls (Tiers 1-5 drop in M1-M5; Tiers 6 & 7 are Craftable Only)
        add("Master Skull - Tier 1", "MASTER_SKULL_TIER_1", "Accessory", "Master Mode I");
        add("Master Skull - Tier 2", "MASTER_SKULL_TIER_2", "Accessory", "Master Mode II");
        add("Master Skull - Tier 3", "MASTER_SKULL_TIER_3", "Accessory", "Master Mode III");
        add("Master Skull - Tier 4", "MASTER_SKULL_TIER_4", "Accessory", "Master Mode IV");
        add("Master Skull - Tier 5", "MASTER_SKULL_TIER_5", "Accessory", "Master Mode V");
        add("Master Skull - Tier 6", "MASTER_SKULL_TIER_6", "Accessory", "Craftable Only (4x Tier 5)");
        add("Master Skull - Tier 7", "MASTER_SKULL_TIER_7", "Accessory", "Craftable Only (4x Tier 6)");

        // Shards
        add("Wither Shard", "SHARD_WITHER", "Shard", "Floor VII / M7");
        add("Thorn Shard", "SHARD_THORN", "Shard", "Floor IV / M4");
        add("Apex Dragon Shard", "SHARD_APEX_DRAGON", "Shard", "Floor VII / M7");
        add("Power Dragon Shard", "SHARD_POWER_DRAGON", "Shard", "Floor VII / M7");
        add("Scarf Shard", "SHARD_SCARF", "Shard", "Floor II / M2");

        // Enchanted Books (All Dungeon Tiers)
        add("Enchanted Book (One For All I)", "ENCHANTMENT_ONE_FOR_ALL_1", "Enchanted Book", "Floor VII / M7");

        add("Enchanted Book (Ultimate Wise V)", "ENCHANTMENT_ULTIMATE_WISE_5", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Wise IV)", "ENCHANTMENT_ULTIMATE_WISE_4", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Wise III)", "ENCHANTMENT_ULTIMATE_WISE_3", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Wise II)", "ENCHANTMENT_ULTIMATE_WISE_2", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Wise I)", "ENCHANTMENT_ULTIMATE_WISE_1", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Soul Eater V)", "ENCHANTMENT_SOUL_EATER_5", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Soul Eater IV)", "ENCHANTMENT_SOUL_EATER_4", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Soul Eater III)", "ENCHANTMENT_SOUL_EATER_3", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Soul Eater II)", "ENCHANTMENT_SOUL_EATER_2", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Soul Eater I)", "ENCHANTMENT_SOUL_EATER_1", "Enchanted Book", "Floor VII / M7");

        add("Enchanted Book (Overload V)", "ENCHANTMENT_OVERLOAD_5", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Overload IV)", "ENCHANTMENT_OVERLOAD_4", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Overload III)", "ENCHANTMENT_OVERLOAD_3", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Overload II)", "ENCHANTMENT_OVERLOAD_2", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Overload I)", "ENCHANTMENT_OVERLOAD_1", "Enchanted Book", "Floor V / M5");

        add("Enchanted Book (Legion V)", "ENCHANTMENT_LEGION_5", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Legion IV)", "ENCHANTMENT_LEGION_4", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Legion III)", "ENCHANTMENT_LEGION_3", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Legion II)", "ENCHANTMENT_LEGION_2", "Enchanted Book", "Floor VII / M7");
        add("Enchanted Book (Legion I)", "ENCHANTMENT_LEGION_1", "Enchanted Book", "Floor VII / M7");

        add("Enchanted Book (Wisdom V)", "ENCHANTMENT_WISDOM_5", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Wisdom IV)", "ENCHANTMENT_WISDOM_4", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Wisdom III)", "ENCHANTMENT_WISDOM_3", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Wisdom II)", "ENCHANTMENT_WISDOM_2", "Enchanted Book", "Floor V / M5");
        add("Enchanted Book (Wisdom I)", "ENCHANTMENT_WISDOM_1", "Enchanted Book", "Floor V / M5");

        add("Enchanted Book (Last Stand V)", "ENCHANTMENT_LAST_STAND_5", "Enchanted Book", "Floor III-VII");
        add("Enchanted Book (Last Stand IV)", "ENCHANTMENT_LAST_STAND_4", "Enchanted Book", "Floor III-VII");
        add("Enchanted Book (Last Stand III)", "ENCHANTMENT_LAST_STAND_3", "Enchanted Book", "Floor III-VII");
        add("Enchanted Book (Last Stand II)", "ENCHANTMENT_LAST_STAND_2", "Enchanted Book", "Floor III-VII");
        add("Enchanted Book (Last Stand I)", "ENCHANTMENT_LAST_STAND_1", "Enchanted Book", "Floor III-VII");

        add("Enchanted Book (Infinite Quiver X)", "ENCHANTMENT_INFINITE_QUIVER_10", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Infinite Quiver IX)", "ENCHANTMENT_INFINITE_QUIVER_9", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Infinite Quiver VIII)", "ENCHANTMENT_INFINITE_QUIVER_8", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Infinite Quiver VII)", "ENCHANTMENT_INFINITE_QUIVER_7", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Infinite Quiver VI)", "ENCHANTMENT_INFINITE_QUIVER_6", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Feather Falling X)", "ENCHANTMENT_FEATHER_FALLING_10", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Feather Falling IX)", "ENCHANTMENT_FEATHER_FALLING_9", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Feather Falling VIII)", "ENCHANTMENT_FEATHER_FALLING_8", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Feather Falling VII)", "ENCHANTMENT_FEATHER_FALLING_7", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Feather Falling VI)", "ENCHANTMENT_FEATHER_FALLING_6", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Rejuvenate V)", "ENCHANTMENT_REJUVENATE_5", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Rejuvenate IV)", "ENCHANTMENT_REJUVENATE_4", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Rejuvenate III)", "ENCHANTMENT_REJUVENATE_3", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Rejuvenate II)", "ENCHANTMENT_REJUVENATE_2", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Rejuvenate I)", "ENCHANTMENT_REJUVENATE_1", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Bank V)", "ENCHANTMENT_BANK_5", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Bank IV)", "ENCHANTMENT_BANK_4", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Bank III)", "ENCHANTMENT_BANK_3", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Bank II)", "ENCHANTMENT_BANK_2", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Bank I)", "ENCHANTMENT_BANK_1", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Combo V)", "ENCHANTMENT_COMBO_5", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Combo IV)", "ENCHANTMENT_COMBO_4", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Combo III)", "ENCHANTMENT_COMBO_3", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Combo II)", "ENCHANTMENT_COMBO_2", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Combo I)", "ENCHANTMENT_COMBO_1", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (No Pain No Gain V)", "ENCHANTMENT_NO_PAIN_NO_GAIN_5", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (No Pain No Gain IV)", "ENCHANTMENT_NO_PAIN_NO_GAIN_4", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (No Pain No Gain III)", "ENCHANTMENT_NO_PAIN_NO_GAIN_3", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (No Pain No Gain II)", "ENCHANTMENT_NO_PAIN_NO_GAIN_2", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (No Pain No Gain I)", "ENCHANTMENT_NO_PAIN_NO_GAIN_1", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Ultimate Jerry V)", "ENCHANTMENT_ULTIMATE_JERRY_5", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Jerry IV)", "ENCHANTMENT_ULTIMATE_JERRY_4", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Jerry III)", "ENCHANTMENT_ULTIMATE_JERRY_3", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Jerry II)", "ENCHANTMENT_ULTIMATE_JERRY_2", "Enchanted Book", "Floor I-VII");
        add("Enchanted Book (Ultimate Jerry I)", "ENCHANTMENT_ULTIMATE_JERRY_1", "Enchanted Book", "Floor I-VII");

        add("Enchanted Book (Rend V)", "ENCHANTMENT_REND_5", "Enchanted Book", "Floor IV / M4");
        add("Enchanted Book (Duplex V)", "ENCHANTMENT_DUPLEX_5", "Enchanted Book", "Master Mode V-VII");
        add("Enchanted Book (Fatal Tempo V)", "ENCHANTMENT_FATAL_TEMPO_5", "Enchanted Book", "Master Mode VII");
        add("Enchanted Book (Flash V)", "ENCHANTMENT_FLASH_5", "Enchanted Book", "Master Mode VII");
        add("Enchanted Book (Inferno V)", "ENCHANTMENT_INFERNO_5", "Enchanted Book", "Master Mode VII");

        // Essence (Unit values)
        add("Wither Essence (x1)", "ESSENCE_WITHER", "Essence", "Bazaar / Dungeon");
        add("Undead Essence (x1)", "ESSENCE_UNDEAD", "Essence", "Bazaar / Dungeon");
        add("Dragon Essence (x1)", "ESSENCE_DRAGON", "Essence", "Bazaar / Dungeon");
        add("Spider Essence (x1)", "ESSENCE_SPIDER", "Essence", "Bazaar / Dungeon");
        add("Ice Essence (x1)", "ESSENCE_ICE", "Essence", "Bazaar / Dungeon");
        add("Diamond Essence (x1)", "ESSENCE_DIAMOND", "Essence", "Bazaar / Dungeon");
        add("Gold Essence (x1)", "ESSENCE_GOLD", "Essence", "Bazaar / Dungeon");
        add("Crimson Essence (x1)", "ESSENCE_CRIMSON", "Essence", "Bazaar / Kuudra");

        // Fetch live prices
        refreshPrices();
    }

    private void add(String name, String id, String category, String floorTag) {
        allItems.add(new DungeonItemEntry(name, id, category, floorTag));
    }

    public void refreshPrices() {
        for (DungeonItemEntry item : allItems) {
            long p = LowestBinManager.getCachedPrice(item.id);
            if (p <= 0L) {
                p = LowestBinManager.getCachedPrice(item.name.toUpperCase().replace(" ", "_"));
            }
            if (p <= 0L) {
                if (item.id.equals("ESSENCE_WITHER")) p = 4500L;
                else if (item.id.equals("ESSENCE_UNDEAD")) p = 1200L;
                else if (item.id.equals("ESSENCE_DRAGON")) p = 3500L;
                else if (item.id.equals("ESSENCE_CRIMSON")) p = 962L;
                else p = me.bombo.bomboaddons.cheat.automation.AutoCroesus.getFallbackDungeonItemPrice(item.id);
            }
            item.price = p;
            item.isBazaar = LowestBinManager.isBazaar(item.id);
        }

        // Sort descending by price (most expensive first)
        allItems.sort((a, b) -> Long.compare(b.price, a.price));
        updateFilter();
        lastLoadedTime = System.currentTimeMillis();
    }

    private void updateFilter() {
        filteredItems.clear();
        String q = searchQuery.toLowerCase().trim();
        for (DungeonItemEntry it : allItems) {
            if (q.isEmpty() || it.name.toLowerCase().contains(q) || it.id.toLowerCase().contains(q) || it.category.toLowerCase().contains(q) || it.floorTag.toLowerCase().contains(q)) {
                filteredItems.add(it);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // Draw dark background
        g.fill(0, 0, this.width, this.height, 0xC80F172A);

        int winW = Math.min(680, this.width - 32);
        int winH = Math.min(480, this.height - 32);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Window background
        g.fill(winX, winY, winX + winW, winY + winH, 0xF50F172A);
        g.outline(winX, winY, winW, winH, ConfigUITheme.getAccentColor());

        // Header
        int headerH = 46;
        g.fill(winX, winY, winX + winW, winY + headerH, 0xEE1E293B);
        g.fill(winX, winY + headerH - 1, winX + winW, winY + headerH, ConfigUITheme.getDividerColor());

        g.text(this.font, ConfigUITheme.formatFont("§6§lDUNGEON ITEM PRICES"), winX + 16, winY + 12, ConfigUITheme.ACCENT_GOLD, false);
        String sub = "§7Ordered by most expensive first • §e" + filteredItems.size() + " items shown";
        g.text(this.font, sub, winX + 16, winY + 28, ConfigUITheme.getTextMuted(), false);

        // Search Bar
        int searchW = Math.min(200, winW / 3);
        int searchX = winX + winW - searchW - 140;
        int searchY = winY + 13;
        ConfigCustomWidgets.renderCleanInputField(g, this.font, "Search items / IDs...", searchQuery, "dungeonSearch", searchX, searchY, searchW, mouseX, mouseY);

        // Refresh Button
        int refBtnX = winX + winW - 130;
        int refBtnY = winY + 13;
        boolean refHover = mouseX >= refBtnX && mouseX <= refBtnX + 80 && mouseY >= refBtnY && mouseY <= refBtnY + 18;
        ConfigUITheme.drawPillButton(g, this.font, "§b↻ Refresh", refBtnX, refBtnY, 80, 18, refHover, -1, 0x2200E5FF, 0x5500E5FF);

        // Close Button
        int closeBtnX = winX + winW - 40;
        int closeBtnY = winY + 13;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 28 && mouseY >= closeBtnY && mouseY <= closeBtnY + 18;
        ConfigUITheme.drawPillButton(g, this.font, "§c✕", closeBtnX, closeBtnY, 28, 18, closeHover, 0xFFFF5555, 0x33EF4444, 0x66EF4444);

        // Content Area
        int listX = winX + 12;
        int listY = winY + headerH + 8;
        int listW = winW - 24;
        int listH = winH - headerH - 16;

        g.enableScissor(listX, listY, listX + listW, listY + listH);

        int curY = listY - (int) scrollAmount;
        int rowH = 34;
        int totalH = 0;

        for (int i = 0; i < filteredItems.size(); i++) {
            DungeonItemEntry item = filteredItems.get(i);
            int rowY = curY;

            if (rowY + rowH >= listY && rowY <= listY + listH) {
                boolean rowHover = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + rowH;
                int rowBg = rowHover ? 0x2E1E293B : ((i % 2 == 0) ? 0x1A0F172A : 0x221E293B);
                g.fill(listX, rowY, listX + listW, rowY + rowH - 2, rowBg);
                g.outline(listX, rowY, listW, rowH - 2, rowHover ? ConfigUITheme.getAccentColor() : 0x1AFFFFFF);

                // Rank
                String rankStr = "§8#" + (i + 1);
                g.text(this.font, rankStr, listX + 8, rowY + 11, 0xFF64748B, false);

                // Name & ID
                String nameColor = item.price >= 100000000L ? "§6§l" : (item.price >= 10000000L ? "§5§l" : (item.price >= 1000000L ? "§d" : "§f"));
                g.text(this.font, ConfigUITheme.formatFont(nameColor + item.name), listX + 44, rowY + 6, -1, false);
                g.text(this.font, "§8ID: §7" + item.id + " §8| §b" + item.category + " §8[" + item.floorTag + "]", listX + 44, rowY + 19, 0xFF94A3B8, false);

                // Right Side: Price & Source
                String priceFormatted = item.price > 0 ? "§6" + String.format("%,d", item.price) + " coins §a(" + LowestBinManager.formatPrice(item.price) + ")" : "§cNo Price / 0";
                int priceW = this.font.width(priceFormatted);
                int rx = listX + listW - 12;

                g.text(this.font, priceFormatted, rx - priceW - 55, rowY + 10, -1, false);

                // Source Badge
                String badge = item.isBazaar ? "§aBZ" : "§bBIN";
                int badgeW = 38;
                int badgeX = rx - badgeW;
                int badgeBg = item.isBazaar ? 0x3310B981 : 0x3300E5FF;
                int badgeBorder = item.isBazaar ? 0xFF10B981 : 0xFF00E5FF;
                g.fill(badgeX, rowY + 8, badgeX + badgeW, rowY + 22, badgeBg);
                g.outline(badgeX, rowY + 8, badgeW, 14, badgeBorder);
                g.text(this.font, badge, badgeX + badgeW / 2 - this.font.width(badge) / 2, rowY + 11, -1, false);
            }

            curY += rowH;
            totalH += rowH;
        }

        g.disableScissor();

        this.maxScroll = Math.max(0, totalH - listH);

        // Scrollbar
        if (totalH > listH) {
            int trackX = listX + listW - 4;
            int trackY = listY;
            int trackH = listH;
            int thumbH = Math.max(16, (int) ((double) listH * listH / totalH));
            int thumbY = trackY + (int) ((trackH - thumbH) * (scrollAmount / maxScroll));
            ConfigUITheme.drawScrollBar(g, trackX, trackY, 4, trackH, thumbY, thumbH);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = Math.min(680, this.width - 32);
        int winH = Math.min(480, this.height - 32);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int searchW = Math.min(200, winW / 3);
        int searchX = winX + winW - searchW - 140;
        int searchY = winY + 13;
        if (ConfigCustomWidgets.checkFieldClick(searchX, searchY, searchW, 18, "dungeonSearch", (int) mouseX, (int) mouseY)) {
            return true;
        }

        int refBtnX = winX + winW - 130;
        int refBtnY = winY + 13;
        if (mouseX >= refBtnX && mouseX <= refBtnX + 80 && mouseY >= refBtnY && mouseY <= refBtnY + 18) {
            LowestBinManager.reload();
            refreshPrices();
            return true;
        }

        int closeBtnX = winX + winW - 40;
        int closeBtnY = winY + 13;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 28 && mouseY >= closeBtnY && mouseY <= closeBtnY + 18) {
            if (this.minecraft != null) this.minecraft.setScreenAndShow(this.parent);
            return true;
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollAmount -= verticalAmount * 24.0;
        if (this.scrollAmount < 0) this.scrollAmount = 0;
        if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if ("dungeonSearch".equals(ConfigCustomWidgets.activeFocusedField)) {
            char c = (char) event.codepoint();
            if (c >= 32 && c != 127) {
                searchQuery += c;
                updateFilter();
                scrollAmount = 0;
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ("dungeonSearch".equals(ConfigCustomWidgets.activeFocusedField)) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    updateFilter();
                    scrollAmount = 0;
                }
                return true;
            } else if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                ConfigCustomWidgets.activeFocusedField = null;
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (this.minecraft != null) this.minecraft.setScreenAndShow(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
