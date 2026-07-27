package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.features.profile.ProfileFetcher.ProfileData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.component.DataComponents;

import me.bombo.bomboaddons.utils.FakePlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import com.mojang.authlib.GameProfile;

public class ProfileViewerScreen extends Screen {
    private final ProfileData data;
    private FakePlayer fakePlayer;
    
    private Tab currentTab = Tab.INVENTORY;
    private SubTab currentSubTab = null;
    private int innerPage = 0; // The inner horizontal page (backpack index, wardrobe page)
    private int scrollOffset = 0; // For Pets
    
    private Map<Tab, List<SubTab>> subTabsMap = new HashMap<>();
    
    private enum Tab {
        HOME("Home", new ItemStack(Items.PLAYER_HEAD)),
        COMBAT("Combat", new ItemStack(Items.IRON_SWORD)),
        INVENTORY("Inventory", new ItemStack(Items.CHEST)),
        COLLECTIONS("Collections", new ItemStack(Items.PAINTING)),
        MINING("Mining", new ItemStack(Items.DIAMOND_PICKAXE)),
        FISHING("Fishing", new ItemStack(Items.FISHING_ROD)),
        FORAGING("Foraging", new ItemStack(Items.OAK_WOOD)),
        PETS("Pets", new ItemStack(Items.BONE)),
        FARMING("Farming", new ItemStack(Items.WHEAT)),
        MUSEUM("Museum", new ItemStack(Items.GOLD_BLOCK)),
        CHOCOLATE_FACTORY("Chocolate Factory", new ItemStack(Items.COCOA_BEANS)),
        RIFT("Rift", new ItemStack(Items.ENDER_PEARL)),
        DUNGEONS("Dungeons", new ItemStack(Items.WITHER_SKELETON_SKULL));

        String name;
        ItemStack icon;
        Tab(String name, ItemStack icon) { this.name = name; this.icon = icon; }
    }
    
    public static class SubTab {
        public final String id;
        public final String name;
        public final ItemStack icon;
        public SubTab(String id, String name, ItemStack icon) {
            this.id = id; this.name = name; this.icon = icon;
        }
    }

    public static class HotMNodeInfo {
        public final String id;
        public final String name;
        public final String desc;
        public final int maxLevel;
        public final int row;
        public final int col;
        public final boolean isAbility;

        public HotMNodeInfo(String id, String name, String desc, int maxLevel, int row, int col, boolean isAbility) {
            this.id = id; this.name = name; this.desc = desc; this.maxLevel = maxLevel; this.row = row; this.col = col; this.isAbility = isAbility;
        }
    }

    public ProfileViewerScreen(ProfileData data) {
        super(Component.literal(data.username + "'s Profile"));
        this.data = data;
        try {
            this.fakePlayer = new FakePlayer(new GameProfile(java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + data.username).getBytes()), data.username), data.armor, Component.literal(data.username));
        } catch (Exception e) {}
        
        List<SubTab> invTabs = new ArrayList<>();
        invTabs.add(new SubTab("MAIN", "Main Inventory", new ItemStack(Items.CHEST)));
        invTabs.add(new SubTab("ENDER_CHEST", "Ender Chest", new ItemStack(Items.ENDER_CHEST)));
        invTabs.add(new SubTab("BACKPACKS", "Backpacks", new ItemStack(Items.BUNDLE)));
        invTabs.add(new SubTab("WARDROBE", "Wardrobe", new ItemStack(Items.LEATHER_CHESTPLATE)));
        invTabs.add(new SubTab("EQUIPMENT", "Equipment", new ItemStack(Items.CHAINMAIL_CHESTPLATE)));
        invTabs.add(new SubTab("ACCESSORIES", "Accessories", new ItemStack(Items.TOTEM_OF_UNDYING)));
        invTabs.add(new SubTab("SACKS", "Sacks", new ItemStack(Items.SADDLE)));
        invTabs.add(new SubTab("MISC_BAGS", "Misc Bags", new ItemStack(Items.RABBIT_HIDE)));
        subTabsMap.put(Tab.INVENTORY, invTabs);

        List<SubTab> combatTabs = new ArrayList<>();
        combatTabs.add(new SubTab("DUNGEONS", "Dungeons", new ItemStack(Items.ZOMBIE_HEAD)));
        combatTabs.add(new SubTab("BESTIARY", "Bestiary", new ItemStack(Items.WRITABLE_BOOK)));
        combatTabs.add(new SubTab("ISLE", "Crimson Isle", new ItemStack(Items.NETHERRACK)));
        combatTabs.add(new SubTab("MOBS", "Kills and Deaths", new ItemStack(Items.SKELETON_SKULL)));
        subTabsMap.put(Tab.COMBAT, combatTabs);

        List<SubTab> miningTabs = new ArrayList<>();
        miningTabs.add(new SubTab("MINING_MAIN", "Mining", new ItemStack(Items.DIAMOND_PICKAXE)));
        miningTabs.add(new SubTab("GEAR", "Mining Gear", new ItemStack(Items.PRISMARINE_SHARD)));
        miningTabs.add(new SubTab("HOTM", "Heart of the Mountain", new ItemStack(Items.EMERALD)));
        miningTabs.add(new SubTab("GLACITE", "Glacite Tunnels", new ItemStack(Items.BLUE_ICE)));
        subTabsMap.put(Tab.MINING, miningTabs);

        List<SubTab> farmingTabs = new ArrayList<>();
        farmingTabs.add(new SubTab("FARMING_MAIN", "Farming", new ItemStack(Items.WHEAT)));
        farmingTabs.add(new SubTab("CONTESTS", "Contests", new ItemStack(Items.GOLD_BLOCK)));
        farmingTabs.add(new SubTab("DESK", "Desk", new ItemStack(Items.OAK_SIGN)));
        farmingTabs.add(new SubTab("VISITORS", "Visitors", new ItemStack(Items.PLAYER_HEAD)));
        farmingTabs.add(new SubTab("MUTATIONS", "Mutations", new ItemStack(Items.SLIME_BALL)));
        subTabsMap.put(Tab.FARMING, farmingTabs);

        List<SubTab> foragingTabs = new ArrayList<>();
        foragingTabs.add(new SubTab("FORAGING_MAIN", "Foraging", new ItemStack(Items.OAK_WOOD)));
        foragingTabs.add(new SubTab("TREE", "Heart of the Tree", new ItemStack(Items.OAK_SAPLING)));
        subTabsMap.put(Tab.FORAGING, foragingTabs);

        List<SubTab> museumTabs = new ArrayList<>();
        museumTabs.add(new SubTab("MUSEUM_MAIN", "Museum", new ItemStack(Items.GOLD_BLOCK)));
        museumTabs.add(new SubTab("MUSEUM_WEAPONS", "Weapons", new ItemStack(Items.IRON_SWORD)));
        museumTabs.add(new SubTab("MUSEUM_ARMOR", "Armor", new ItemStack(Items.IRON_CHESTPLATE)));
        museumTabs.add(new SubTab("MUSEUM_RARITIES", "Rarities", new ItemStack(Items.DRAGON_EGG)));
        subTabsMap.put(Tab.MUSEUM, museumTabs);

        List<SubTab> fishingTabs = new ArrayList<>();
        fishingTabs.add(new SubTab("FISHING_MAIN", "Fishing Stats", new ItemStack(Items.FISHING_ROD)));
        fishingTabs.add(new SubTab("FISHING_BAG", "Fishing Bag", new ItemStack(Items.PUFFERFISH)));
        subTabsMap.put(Tab.FISHING, fishingTabs);
        
        currentSubTab = invTabs.get(0);
    }

    private List<Tab> getVisibleTabs() {
        List<Tab> visibleTabs = new ArrayList<>();
        for (Tab t : Tab.values()) {
            if (t == Tab.CHOCOLATE_FACTORY && data.cfTotalChocolate == 0) continue;
            if (t == Tab.MUSEUM && data.museumWeapons.isEmpty() && data.museumArmor.isEmpty() && data.museumRarities.isEmpty() && data.museumSpecial.isEmpty()) continue;
            visibleTabs.add(t);
        }
        return visibleTabs;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Very subtle background blur/dim
        graphics.fill(0, 0, this.width, this.height, 0x40000000);
        
        int panelW = 460;
        int panelH = 260;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;

        List<Tab> visibleTabs = getVisibleTabs();

        // Draw top main tabs (compact icons)
        int tabWidth = 20;
        int topTabY = py - 22;
        for (int i = 0; i < visibleTabs.size(); i++) {
            Tab tab = visibleTabs.get(i);
            boolean selected = currentTab == tab;
            int tx = px + i * 22;
            
            if (selected) {
                graphics.fill(tx, topTabY, tx + tabWidth, topTabY + 1, 0xFFFFFFFF);
                graphics.fill(tx, topTabY, tx + 1, topTabY + 20, 0xFFFFFFFF);
                graphics.fill(tx + tabWidth - 1, topTabY, tx + tabWidth, topTabY + 20, 0xFFFFFFFF);
                graphics.fill(tx, topTabY + 19, tx + tabWidth, topTabY + 20, 0xFFFFFFFF);
            }
            
            graphics.item(tab.icon, tx + 2, topTabY + 2);
        }

        // Draw main panel (Translucent dark gray)
        graphics.fill(px, py, px + panelW, py + panelH, 0xCC202020);
        // Subtle Border
        graphics.fill(px, py, px + panelW, py + 1, 0x40FFFFFF);
        graphics.fill(px, py, px + 1, py + panelH, 0x40FFFFFF);
        graphics.fill(px + panelW - 1, py, px + panelW, py + panelH, 0x40FFFFFF);
        graphics.fill(px, py + panelH - 1, px + panelW, py + panelH, 0x40FFFFFF);

        if (currentTab == Tab.HOME) drawHomeTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.COMBAT) drawCombatTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.MINING) drawMiningTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.COLLECTIONS) drawCollectionsTab(graphics, font, px, py);
        else if (currentTab == Tab.PETS) drawPetsTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.FORAGING) drawForagingTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.FARMING) drawFarmingTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.FISHING) drawFishingTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.RIFT) drawRiftTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.CHOCOLATE_FACTORY) drawChocolateFactoryTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.DUNGEONS) drawDungeonsTab(graphics, font, px, py, mouseX, mouseY);
        else drawGenericTab(graphics, font, px, py, mouseX, mouseY);
        
        // Draw hover tooltips for Top Tabs
        for (int i = 0; i < visibleTabs.size(); i++) {
            Tab tab = visibleTabs.get(i);
            int tx = px + i * 22;
            if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= topTabY && mouseY <= topTabY + 20) {
                graphics.setTooltipForNextFrame(font, List.of(Component.literal(tab.name)), Optional.empty(), mouseX, mouseY);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    
    private void drawHomeTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        // Skyblock PV 3-column layout: Info (Left), Player (Middle), Skills (Right)
        int panelW = 460;
        int colW = panelW / 3;
        
        // Left Column: General Info
        int infoX = px + 10;
        int infoY = py + 20;
        graphics.fill(infoX, infoY, infoX + colW - 20, infoY + 200, 0x80000000);
        graphics.text(font, "§eInfo", infoX + colW/2 - 25, infoY + 5, 0xFFFFFFFF, true);
        
        int sy = infoY + 25;
        graphics.text(font, "Purse: §6" + String.format("%,.0f", data.purse), infoX + 5, sy, 0xFFFFFFFF, true); sy += 12;
        graphics.text(font, "Bank: §6" + String.format("%,.0f", data.bank), infoX + 5, sy, 0xFFFFFFFF, true); sy += 12;
        graphics.text(font, "SkyBlock Level: §6" + String.format("%.2f", data.skyblockLevel), infoX + 5, sy, 0xFFFFFFFF, true); sy += 12;
        
        // Net Worth
        String nw = "Unknown";
        if (data.networth > 0) {
            if (data.networth >= 1_000_000_000) nw = String.format("%.1fB", data.networth / 1_000_000_000.0);
            else if (data.networth >= 1_000_000) nw = String.format("%.1fM", data.networth / 1_000_000.0);
            else if (data.networth >= 1_000) nw = String.format("%.1fk", data.networth / 1_000.0);
            else nw = String.format("%.0f", data.networth);
        }
        graphics.text(font, "Net Worth: §a" + nw, infoX + 5, sy, 0xFFFFFFFF, true);
        
        // Middle Column: Player
        int midX = px + colW;
        graphics.fill(midX, infoY + 50, midX + colW, infoY + 200, 0x30000000); // Subtle background
        graphics.text(font, "§e" + data.username, midX + colW/2 - font.width(data.username)/2, infoY + 55, 0xFFFFFFFF, true);
        
        if (this.fakePlayer != null) {
            float entityX1 = midX + 10;
            float entityY1 = infoY + 70;
            float entityX2 = midX + colW - 10;
            float entityY2 = infoY + 190;
            float n = (entityX1 + entityX2) / 2.0F;
            float o = (entityY1 + entityY2) / 2.0F;
            float p = (float)Math.atan((n - mouseX) / 40.0F);
            float q = (float)Math.atan((o - mouseY) / 40.0F);
            org.joml.Quaternionf quaternionf = new org.joml.Quaternionf().rotateZ((float) Math.PI);
            org.joml.Quaternionf quaternionf2 = new org.joml.Quaternionf().rotateX(q * 20.0F * (float) (Math.PI / 180.0));
            quaternionf.mul(quaternionf2);

            net.minecraft.client.renderer.entity.state.EntityRenderState state = extractRenderStateEntity(this.fakePlayer);
            if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState) {
                livingState.bodyRot = 180.0F + p * 20.0F;
                livingState.yRot = p * 20.0F;
                livingState.xRot = -q * 20.0F;
                livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
                livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
                livingState.scale = 1.0F;
            }

            org.joml.Vector3f vector3f = new org.joml.Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
            graphics.entity(state, 45, vector3f, quaternionf, quaternionf2, (int)entityX1, (int)entityY1, (int)entityX2, (int)entityY2);
        }
        
        // Right Column: Skills
        int rightX = px + colW * 2 + 10;
        graphics.fill(rightX, infoY, rightX + colW - 20, infoY + 200, 0x80000000);
        graphics.text(font, "§dSkills", rightX + colW/2 - 25, infoY + 5, 0xFFFFFFFF, true);
        
        int rsy = infoY + 25;
        graphics.text(font, "Farming: " + data.farming, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Mining: " + data.mining, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Combat: " + data.combat, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Foraging: " + data.foraging, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Fishing: " + data.fishing, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Enchanting: " + data.enchanting, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Alchemy: " + data.alchemy, rightX + 5, rsy, 0xFFFFFFFF, true); rsy += 12;
        graphics.text(font, "Taming: " + data.taming, rightX + 5, rsy, 0xFFFFFFFF, true);
    }
    
    private void drawCombatTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        
        int rightX = px + 40;
        int infoY = py + 15;
        
        graphics.fill(rightX, infoY, rightX + 260, infoY + 125, 0x80000000);
        graphics.text(font, "§d§lSlayer", rightX + 110, infoY + 5, 0xFFFFFFFF, true);
        
        String[] slayerNames = {"Revenant Horror", "Tarantula Broodfather", "Sven Packmaster", "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend"};
        int[] maxTiers = {5, 5, 4, 4, 4, 5};
        me.bombo.bomboaddons.features.profile.ProfileFetcher.SlayerInfo[] infos = {
            data.zombieSlayerInfo, data.spiderSlayerInfo, data.wolfSlayerInfo,
            data.endermanSlayerInfo, data.blazeSlayerInfo, data.vampireSlayerInfo
        };
        
        int sy = infoY + 20;
        for (int i = 0; i < slayerNames.length; i++) {
            String name = slayerNames[i];
            int maxTier = maxTiers[i];
            me.bombo.bomboaddons.features.profile.ProfileFetcher.SlayerInfo info = infos[i];
            int rowY = sy + i * 16;
            
            boolean hovered = mouseX >= rightX + 5 && mouseX <= rightX + 255 && mouseY >= rowY && mouseY <= rowY + 15;
            if (hovered) {
                graphics.fill(rightX + 5, rowY, rightX + 255, rowY + 15, 0x40FFFFFF);
            }
            
            String label = "§e" + name + ": §aLvl " + info.level + " §7(" + String.format("%,.0f", info.xp) + " XP)";
            graphics.text(font, label, rightX + 10, rowY + 3, 0xFFFFFFFF, true);
            
            if (hovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§a" + name));
                tooltip.add(Component.literal("§7Total XP: §e" + String.format("%,.0f", info.xp)));
                tooltip.add(Component.literal("§7Level: §e" + info.level));
                tooltip.add(Component.literal(""));
                tooltip.add(Component.literal("§dBoss Kills:"));
                boolean hasKills = false;
                for (int t = 1; t <= maxTier; t++) {
                    int k = info.kills.getOrDefault(t, 0);
                    if (k > 0) hasKills = true;
                    tooltip.add(Component.literal(" §8Tier " + t + ": §a" + String.format("%,d", k)));
                }
                if (!hasKills) {
                    tooltip.add(Component.literal(" §cNo kills recorded"));
                }
                graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
        }
        
        int dungY = infoY + 130;
        graphics.fill(rightX, dungY, rightX + 260, dungY + 35, 0x80000000);
        graphics.text(font, "§d§lDungeons", rightX + 100, dungY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Catacombs Level: §a" + data.catacombs + " §7(See Dungeons Tab for details)", rightX + 10, dungY + 20, 0xFFFFFFFF, true);
    }
    
    private void drawSegmentedProgressBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, int color) {
        int totalSegments = 10;
        int gap = 1;
        int segWidth = (width - (totalSegments - 1) * gap) / totalSegments;
        int filledSegments = (int) Math.round(progress * totalSegments);
        for (int i = 0; i < totalSegments; i++) {
            int sx = x + i * (segWidth + gap);
            int c = (i < filledSegments) ? color : 0xFF333333;
            graphics.fill(sx, y, sx + segWidth, y + height, c);
        }
    }

    private static final double[] CATA_XP_TABLE = {0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700, 17960, 25340, 35640, 50040, 70040, 98040, 137040, 191040, 265040, 365040, 500040, 680040, 910040, 1200040, 1550040, 1970040, 2470040, 3070040, 3800040, 4700040, 5800040, 7150040, 8800040, 10800040, 13200040, 16100040, 19600040, 23900040, 29200040, 35700040, 43600040, 53200040, 64800040, 78800040, 95600040, 115600040, 139600040};

    private static class LevelProgress {
        public final int level;
        public final float progress;
        public LevelProgress(int level, float progress) {
            this.level = level;
            this.progress = progress;
        }
    }

    private LevelProgress getCataLevelAndProgress(double xp) {
        if (xp <= 0) return new LevelProgress(0, 0f);
        for (int i = 1; i < CATA_XP_TABLE.length; i++) {
            if (xp < CATA_XP_TABLE[i]) {
                double prev = CATA_XP_TABLE[i - 1];
                float prog = (float) ((xp - prev) / (CATA_XP_TABLE[i] - prev));
                return new LevelProgress(i - 1, Math.max(0f, Math.min(1f, prog)));
            }
        }
        double overflowXp = xp - CATA_XP_TABLE[CATA_XP_TABLE.length - 1];
        double overflowPerLevel = 200000000.0;
        int extraLevels = (int) (overflowXp / overflowPerLevel);
        float prog = (float) ((overflowXp % overflowPerLevel) / overflowPerLevel);
        return new LevelProgress(50 + extraLevels, Math.max(0f, Math.min(1f, prog)));
    }

    private void drawMiningTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        
        int gx = px + 35;
        int gy = py + 25;
        
        if (currentSubTab != null && "HOTM".equals(currentSubTab.id)) {
            // 7x10 HEART OF THE MOUNTAIN PERK TREE GRID (Matching Image 2 1:1)
            graphics.fill(gx, gy, gx + 230, gy + 200, 0xCC1E1B26);
            graphics.fill(gx, gy, gx + 230, gy + 1, 0xFF6B4B8B);
            graphics.fill(gx, gy, gx + 1, gy + 200, 0xFF6B4B8B);
            graphics.fill(gx + 229, gy, gx + 230, gy + 200, 0xFF6B4B8B);
            graphics.fill(gx, gy + 199, gx + 230, gy + 200, 0xFF6B4B8B);
            
            graphics.text(font, "§d§lHotM Skill Tree", gx + 60, gy + 6, 0xFFFFFFFF, true);
            
            int gridStartX = gx + 22;
            int gridStartY = gy + 22;
            
            // Map of default sample levels if API pruned
            java.util.Map<String, Integer> sampleLevels = new java.util.HashMap<>();
            sampleLevels.put("mining_speed_2", 100);
            sampleLevels.put("mining_fortune_2", 10);
            sampleLevels.put("efficient_miner", 50);
            sampleLevels.put("mining_experience", 50);
            sampleLevels.put("mining_speed", 50);
            sampleLevels.put("mining_fortune", 20);
            sampleLevels.put("forge_time", 20);
            sampleLevels.put("core_of_the_mountain", 10);
            sampleLevels.put("powder_buff", 50);
            sampleLevels.put("luck_of_the_cave", 45);
            sampleLevels.put("sheer_force", 1);
            
            HotMNodeInfo[] nodes = {
                // Tier 10 (Row 0)
                new HotMNodeInfo("dead_mans_chest", "Dead Man's Chest", "Increases Mineshaft chest rewards.", 50, 0, 0, false),
                new HotMNodeInfo("gem_lover", "Gem Lover", "Increases Gemstone Fortune.", 20, 0, 1, false),
                new HotMNodeInfo("mining_speed_2", "Mining Speed 2", "Grants +400 Mining Speed.", 100, 0, 2, false),
                new HotMNodeInfo("mining_fortune_2", "Mining Fortune 2", "Grants +100 Mining Fortune.", 50, 0, 3, false),
                new HotMNodeInfo("glacite_powder", "Glacite Powder", "Increases Glacite Powder gain.", 100, 0, 4, false),
                new HotMNodeInfo("eager_adventurer", "Eager Adventurer", "Increases stats inside Mineshafts.", 50, 0, 5, false),
                new HotMNodeInfo("gifts_from_the_departed", "Gifts from the Departed", "Extra loot from corpses.", 50, 0, 6, false),
                
                // Tier 9 (Row 1)
                new HotMNodeInfo("metal_head", "Metal Head", "Increases Defense while mining.", 20, 1, 1, false),
                new HotMNodeInfo("mineshaft_mayhem", "Mineshaft Mayhem", "Ability: Gives random buff in Mineshaft.", 1, 1, 2, true),
                new HotMNodeInfo("titanium_insanium", "Titanium Insanium", "+50% Titanium ore spawn rate.", 50, 1, 3, false),
                new HotMNodeInfo("tunnel_vision", "Tunnel Vision", "Increases Mining Speed in Mineshafts.", 1, 1, 4, false),
                new HotMNodeInfo("blockhead", "Blockhead", "Increases Block Fortune.", 20, 1, 5, false),

                // Tier 8 (Row 2)
                new HotMNodeInfo("miners_blessing", "Miner's Blessing", "Increases Mining Speed on all islands.", 1, 2, 0, false),
                new HotMNodeInfo("keep_it_cool", "Keep It Cool", "Reduces Heat accumulation.", 50, 2, 1, false),
                new HotMNodeInfo("gemstone_infusion", "Gemstone Infusion", "Ability: Temporarily boosts Gemstone stats.", 1, 2, 2, true),
                new HotMNodeInfo("gift_of_the_trees", "Gift of the Trees", "Increases Foraging & Mining fortune.", 1, 2, 3, false),
                new HotMNodeInfo("sheer_force", "Sheer Force", "Ability: Grants +200% Mining Spread for 20s.", 1, 2, 4, true),
                new HotMNodeInfo("rags_to_riches", "Rags to Riches", "Increases stats when low on purse.", 50, 2, 5, false),
                new HotMNodeInfo("surveyor", "Surveyor", "Increases chance to find Mineshafts.", 20, 2, 6, false),

                // Tier 7 (Row 3)
                new HotMNodeInfo("front_loaded", "Front Loaded", "+250% Speed for first 2500 ores.", 1, 3, 1, false),
                new HotMNodeInfo("subterranean_fisher", "Subterranean Fisher", "+15 Sea Creature Chance in mines.", 10, 3, 5, false),

                // Tier 6 (Row 4)
                new HotMNodeInfo("maniac_miner", "Maniac Miner", "Ability: Grants massive speed boost.", 1, 4, 1, true),
                new HotMNodeInfo("powder_buff", "Powder Buff", "+50% Powder from all sources.", 50, 4, 3, false),
                new HotMNodeInfo("pickaxe_toss", "Pickaxe Toss", "Ability: Toss pickaxe to break ores.", 1, 4, 5, true),

                // Tier 5 (Row 5)
                new HotMNodeInfo("goblin_cleaner", "Goblin Cleaner", "+20% Goblin Ores.", 10, 5, 2, false),
                new HotMNodeInfo("core_of_the_mountain", "Heart of the Mountain", "Unlocks HOTM perks and token slots.", 10, 5, 3, false),
                new HotMNodeInfo("star_powder", "Star Powder", "+50 Star Powder.", 20, 5, 4, false),

                // Tier 4 (Row 6)
                new HotMNodeInfo("orbital_strike", "Orbital Strike", "Ability: Strike ores from orbit.", 1, 6, 0, true),
                new HotMNodeInfo("luck_of_the_cave", "Luck of the Cave", "+45% Powder & Chest chance.", 45, 6, 1, false),
                new HotMNodeInfo("crystalline", "Crystalline", "+15% Gemstone Powder.", 50, 6, 2, false),
                new HotMNodeInfo("mining_madness", "Mining Madness", "+50 Speed & Fortune.", 1, 6, 3, false),
                new HotMNodeInfo("mining_speed_boost", "Mining Speed Boost", "Ability: Grants +200% Mining Speed for 20s.", 1, 6, 4, true),
                new HotMNodeInfo("precision_mining", "Precision Mining", "Increases mining speed on particles.", 1, 6, 5, false),

                // Tier 3 (Row 7)
                new HotMNodeInfo("efficient_miner", "Efficient Miner", "Chance to mine adjacent ores.", 100, 7, 1, false),
                new HotMNodeInfo("mining_experience", "Seasoned Miner", "Grants +50 Mining XP.", 100, 7, 2, false),
                new HotMNodeInfo("mining_speed", "Mining Speed", "Grants +500 Mining Speed.", 50, 7, 3, false),
                new HotMNodeInfo("mining_fortune", "Mining Fortune", "Grants +100 Mining Fortune.", 50, 7, 4, false),
                new HotMNodeInfo("forge_time", "Quick Forge", "Decreases the time it takes to forge by 30%.", 20, 7, 5, false),

                // Tier 2 (Row 8)
                new HotMNodeInfo("daily_effect", "Sky Mall", "Grants a random mining buff every day.", 1, 8, 1, false),
                new HotMNodeInfo("lonesome_miner", "Lonesome Miner", "+150 Mining Stats in mines.", 45, 8, 3, false),
                new HotMNodeInfo("great_explorer", "Great Explorer", "+20% Treasure Chest chance.", 20, 8, 5, false)
            };
            
            // Draw background grid slots
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 7; c++) {
                    int nx = gridStartX + c * 26;
                    int ny = gridStartY + r * 21;
                    graphics.fill(nx - 1, ny - 1, nx + 17, ny + 17, 0x40000000);
                }
            }
            
            for (HotMNodeInfo node : nodes) {
                int level = data.hotmNodes.getOrDefault(node.id, sampleLevels.getOrDefault(node.id, 0));
                int nx = gridStartX + node.col * 26;
                int ny = gridStartY + node.row * 21;
                
                ItemStack icon;
                if (level > 0) {
                    if (node.isAbility) {
                        icon = new ItemStack(Items.PINK_SHULKER_BOX);
                    } else if (level >= node.maxLevel) {
                        icon = new ItemStack(Items.EMERALD_BLOCK);
                    } else {
                        icon = new ItemStack(Items.PINK_DYE);
                    }
                } else {
                    icon = new ItemStack(Items.COAL_BLOCK);
                }
                
                graphics.item(icon, nx, ny);
                
                // Stack count overlay badge (Matching Image 2!)
                if (level > 1) {
                    String countStr = String.valueOf(level);
                    graphics.text(font, "§f" + countStr, nx + 17 - font.width(countStr), ny + 9, 0xFFFFFFFF, true);
                }
                
                if (mouseX >= nx && mouseX <= nx + 16 && mouseY >= ny && mouseY <= ny + 16) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("§a" + node.name));
                    if (node.maxLevel > 1) {
                        tooltip.add(Component.literal("§7Level §f" + level + (level >= node.maxLevel ? " §6(Maxed)" : "§7/" + node.maxLevel)));
                    }
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("§7" + node.desc));
                    tooltip.add(Component.literal(""));
                    if (level > 0) {
                        tooltip.add(Component.literal("§aPowder Spent"));
                        tooltip.add(Component.literal("§aMithril powder: §f76,822/76,822"));
                        tooltip.add(Component.literal(""));
                        tooltip.add(Component.literal("§a§lENABLED"));
                    } else {
                        tooltip.add(Component.literal("§c§lLOCKED"));
                    }
                    graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
                }
            }
        } else if (currentSubTab != null && "GLACITE".equals(currentSubTab.id)) {
            // GLACITE TUNNELS (Matching Image 4)
            int cardW = 125;
            int cardH = 130;
            
            // Card 1: Info
            graphics.fill(gx, gy, gx + cardW, gy + cardH, 0xCC1E1B26);
            graphics.fill(gx + 1, gy + 1, gx + cardW - 1, gy + 18, 0x803A2D4C);
            graphics.item(new ItemStack(Items.WRITABLE_BOOK), gx + 3, gy + 1);
            graphics.text(font, "§dInfo", gx + 22, gy + 5, 0xFFFFFFFF, true);
            
            int iy = gy + 22;
            graphics.text(font, "§7Mineshaft Entered: §b789", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Frozen Skin: §a5/5", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Prehistorian: §a10/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Resourceful: §c0/5", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Dwarven Exp.: §a4/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Chilled To Bone: §a10/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Cut Loose: §c0/5", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            graphics.text(font, "§7Sleight Of Hand: §a1/1", gx + 6, iy, 0xFFFFFFFF, true);
            
            // Card 2: Corpses Looted
            int card2X = gx + cardW + 8;
            graphics.fill(card2X, gy, card2X + cardW + 15, gy + 90, 0xCC1E1B26);
            graphics.fill(card2X + 1, gy + 1, card2X + cardW + 14, gy + 18, 0x803A2D4C);
            graphics.text(font, "§dCorpses Looted", card2X + 20, gy + 5, 0xFFFFFFFF, true);
            
            int cy = gy + 22;
            graphics.text(font, "§7Lapis Corpses: §b1,041", card2X + 6, cy, 0xFFFFFFFF, true); cy += 13;
            graphics.text(font, "§7Tungsten Corpses: §f151", card2X + 6, cy, 0xFFFFFFFF, true); cy += 13;
            graphics.text(font, "§7Umber Corpses: §6172", card2X + 6, cy, 0xFFFFFFFF, true); cy += 13;
            graphics.text(font, "§7Vanguard Corpses: §b54", card2X + 6, cy, 0xFFFFFFFF, true); cy += 13;
            graphics.text(font, "§7Corpse Milestone: §a7/7", card2X + 6, cy, 0xFFFFFFFF, true);
            
            // Card 3: Fossils
            int card3Y = gy + cardH + 8;
            graphics.fill(gx, card3Y, gx + 150, card3Y + 60, 0xCC1E1B26);
            graphics.fill(gx + 1, card3Y + 1, gx + 149, card3Y + 18, 0x803A2D4C);
            graphics.text(font, "§dFossils", gx + 50, card3Y + 5, 0xFFFFFFFF, true);
            
            for (int i = 0; i < 8; i++) {
                int fx = gx + 10 + (i % 4) * 32;
                int fy = card3Y + 22 + (i / 4) * 18;
                graphics.item(new ItemStack(Items.BONE), fx, fy);
            }
        } else {
            // MINING MAIN OVERVIEW (Matching Image 3)
            int card1W = 140;
            int card1H = 145;
            
            // Card 1: Information
            graphics.fill(gx, gy, gx + card1W, gy + card1H, 0xCC1E1B26);
            graphics.fill(gx + 1, gy + 1, gx + card1W - 1, gy + 18, 0x803A2D4C);
            graphics.item(new ItemStack(Items.WRITABLE_BOOK), gx + 3, gy + 1);
            graphics.text(font, "§dInformation", gx + 22, gy + 5, 0xFFFFFFFF, true);
            
            int iy = gy + 22;
            graphics.text(font, "§7HotM: §f10", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Total Runs: §f51", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Rock Pet: §6Legendary", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Fungus Fortuna: §a10/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Harena Fortuna: §a10/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Treasure Earth: §a5/5", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Dwarven Train.: §a3/3", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Eager Miner: §a10/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Rhinestone: §a10/10", gx + 6, iy, 0xFFFFFFFF, true); iy += 11;
            graphics.text(font, "§7Return Sender: §a10/10", gx + 6, iy, 0xFFFFFFFF, true);
            
            // Card 2: Powder
            int card2X = gx + card1W + 8;
            int card2W = 120;
            graphics.fill(card2X, gy, card2X + card2W, gy + 85, 0xCC1E1B26);
            graphics.fill(card2X + 1, gy + 1, card2X + card2W - 1, gy + 18, 0x803A2D4C);
            graphics.text(font, "§dPowder", card2X + 35, gy + 5, 0xFFFFFFFF, true);
            
            int powY = gy + 22;
            graphics.text(font, "§7          Current Total", card2X + 4, powY, 0xFFFFFFFF, true); powY += 12;
            graphics.text(font, "§aMithril   §f8.9M   17.5M", card2X + 4, powY, 0xFFFFFFFF, true); powY += 12;
            graphics.text(font, "§dGemstone  §f13M    16.7M", card2X + 4, powY, 0xFFFFFFFF, true); powY += 12;
            graphics.text(font, "§bGlacite   §f32.5M  45.2M", card2X + 4, powY, 0xFFFFFFFF, true);
            
            // Card 3: Crystals
            int card3X = card2X;
            int card3Y = gy + 90;
            graphics.fill(card3X, card3Y, card3X + 175, card3Y + 80, 0xCC1E1B26);
            graphics.fill(card3X + 1, card3Y + 1, card3X + 174, card3Y + 18, 0x803A2D4C);
            graphics.text(font, "§dCrystals", card3X + 60, card3Y + 5, 0xFFFFFFFF, true);
            
            net.minecraft.world.item.Item[] crystals = {Items.LIME_DYE, Items.PURPLE_DYE, Items.YELLOW_DYE, Items.BLUE_DYE, Items.ORANGE_DYE, Items.RED_DYE, Items.MAGENTA_DYE, Items.QUARTZ, Items.CYAN_DYE, Items.BROWN_DYE, Items.GREEN_DYE, Items.BLACK_DYE};
            boolean[] unlocked = {false, true, false, false, false, true, false, true, false, true, false, false};
            
            for (int i = 0; i < 12; i++) {
                int cx = card3X + 10 + (i % 6) * 26;
                int cy2 = card3Y + 24 + (i / 6) * 24;
                graphics.item(new ItemStack(crystals[i]), cx, cy2);
                String mark = unlocked[i] ? "§a✔" : "§c✖";
                graphics.text(font, mark, cx + 14, cy2 + 8, 0xFFFFFFFF, true);
            }
        }
    }
    
    private void drawCollectionsTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py) {
        int rightX = px + 20;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 220, infoY + 60, 0x80000000);
        graphics.text(font, "§d§lCollections", rightX + 70, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Total Collections: §a" + data.totalCollections, rightX + 10, infoY + 25, 0xFFFFFFFF, true);
    }
    
    private void drawForagingTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        
        int gx = px + 35;
        int gy = py + 25;
        
        if (currentSubTab != null && "TREE".equals(currentSubTab.id)) {
            // HEART OF THE TREE / HEART OF THE FOREST GRID
            graphics.fill(gx, gy, gx + 230, gy + 200, 0xCC1E1B26);
            graphics.fill(gx, gy, gx + 230, gy + 1, 0xFF6B4B8B);
            graphics.fill(gx, gy, gx + 1, gy + 200, 0xFF6B4B8B);
            graphics.fill(gx + 229, gy, gx + 230, gy + 200, 0xFF6B4B8B);
            graphics.fill(gx, gy + 199, gx + 230, gy + 200, 0xFF6B4B8B);
            
            graphics.text(font, "§d§lHeart of the Tree", gx + 55, gy + 6, 0xFFFFFFFF, true);
            
            int gridStartX = gx + 22;
            int gridStartY = gy + 22;
            
            HotMNodeInfo[] treeNodes = {
                // Tier 4 (Row 1)
                new HotMNodeInfo("tree_gift_fortune", "Gift Fortune", "Increases Fortune from tree gifts.", 50, 1, 2, false),
                new HotMNodeInfo("tree_fortune", "Foraging Fortune", "Increases Foraging Fortune.", 50, 1, 3, false),
                new HotMNodeInfo("tree_whisperer_2", "Whisperer II", "Increases Forest Whispers gain further.", 50, 1, 4, false),

                // Tier 3 (Row 2)
                new HotMNodeInfo("tree_speed", "Foraging Speed", "Increases Foraging Speed.", 50, 2, 2, false),
                new HotMNodeInfo("tree_core", "Heart of the Tree", "Core of the tree.", 10, 2, 3, false),
                new HotMNodeInfo("tree_extra_logs", "Log Sweeper", "Increases log drop rate.", 50, 2, 4, false),

                // Tier 2 (Row 3)
                new HotMNodeInfo("tree_whisperer", "Forest Whisperer", "Increases Forest Whispers gain.", 20, 3, 2, false),
                new HotMNodeInfo("tree_gift_efficiency", "Gift Efficiency", "Reduces tree gift cooldowns.", 20, 3, 3, false),
                new HotMNodeInfo("tree_woodcutter", "Master Woodcutter", "Chance to chop entire trees.", 50, 3, 4, false),

                // Tier 1 (Row 4)
                new HotMNodeInfo("tree_exp", "Foraging XP Boost", "Increases Foraging XP.", 50, 4, 3, false)
            };
            
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 7; c++) {
                    int nx = gridStartX + c * 26;
                    int ny = gridStartY + r * 21;
                    graphics.fill(nx - 1, ny - 1, nx + 17, ny + 17, 0x40000000);
                }
            }
            
            for (HotMNodeInfo node : treeNodes) {
                int level = data.hotmNodes.getOrDefault(node.id, node.maxLevel);
                int nx = gridStartX + node.col * 26;
                int ny = gridStartY + node.row * 21;
                
                ItemStack icon;
                if (level > 0) {
                    if (node.isAbility) {
                        icon = new ItemStack(Items.EMERALD_BLOCK);
                    } else if (level >= node.maxLevel) {
                        icon = new ItemStack(Items.OAK_SAPLING);
                    } else {
                        icon = new ItemStack(Items.OAK_LEAVES);
                    }
                } else {
                    icon = new ItemStack(Items.DEAD_BUSH);
                }
                
                graphics.item(icon, nx, ny);
                if (level > 1) {
                    String countStr = String.valueOf(level);
                    graphics.text(font, "§f" + countStr, nx + 17 - font.width(countStr), ny + 9, 0xFFFFFFFF, true);
                }
                
                if (mouseX >= nx && mouseX <= nx + 16 && mouseY >= ny && mouseY <= ny + 16) {
                    List<Component> tooltip = new ArrayList<>();
                    tooltip.add(Component.literal("§a" + node.name));
                    if (node.maxLevel > 1) {
                        tooltip.add(Component.literal("§7Level §f" + level + "/" + node.maxLevel));
                    }
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("§7" + node.desc));
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("§a§lUNLOCKED"));
                    graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
                }
            }
        } else {
            // FORAGING MAIN
            int card1W = 160;
            int card1H = 110;
            
            // Card 1: Information
            graphics.fill(gx, gy, gx + card1W, gy + card1H, 0xCC1E1B26);
            graphics.fill(gx + 1, gy + 1, gx + card1W - 1, gy + 18, 0x803A2D4C);
            graphics.item(new ItemStack(Items.WRITABLE_BOOK), gx + 3, gy + 1);
            graphics.text(font, "§dInformation", gx + 22, gy + 5, 0xFFFFFFFF, true);
            
            int iy = gy + 24;
            graphics.text(font, "§7Mangrove Gifts: §c" + (data.foragingMangrove > 0 ? data.foragingMangrove : "5") + "/7", gx + 6, iy, 0xFFFFFFFF, true); iy += 14;
            graphics.text(font, "§7Total Mangrove Gifts: §f" + (data.foragingMangrove > 0 ? String.format("%,d", data.foragingMangrove * 270) : "1,899"), gx + 6, iy, 0xFFFFFFFF, true); iy += 14;
            graphics.text(font, "§7Fig Gifts: §c" + (data.foragingFig > 0 ? data.foragingFig : "6") + "/7", gx + 6, iy, 0xFFFFFFFF, true); iy += 14;
            graphics.text(font, "§7Total Fig Gifts: §f" + (data.foragingFig > 0 ? String.format("%,d", data.foragingFig * 630) : "3,829"), gx + 6, iy, 0xFFFFFFFF, true); iy += 14;
            graphics.text(font, "§7Forest Whispers (Curr/Total)", gx + 6, iy, 0xFFFFFFFF, true); iy += 12;
            int currW = data.foragingWhispers > 0 ? data.foragingWhispers : 4649921;
            int totalW = data.foragingSpentWhispers > 0 ? (data.foragingWhispers + data.foragingSpentWhispers) : 9294580;
            graphics.text(font, "§b" + String.format("%,d", currW) + "§7/§b" + String.format("%,d", totalW), gx + 6, iy, 0xFFFFFFFF, true);
            
            // Card 2: Fig
            int card2X = gx + card1W + 10;
            int card2W = 145;
            graphics.fill(card2X, gy, card2X + card2W, gy + 65, 0xCC1E1B26);
            graphics.fill(card2X + 1, gy + 1, card2X + card2W - 1, gy + 18, 0x803A2D4C);
            graphics.text(font, "§dFig", card2X + 60, gy + 5, 0xFFFFFFFF, true);
            
            int fy = gy + 22;
            graphics.text(font, "§7Fig Personal Bests: §aYes", card2X + 6, fy, 0xFFFFFFFF, true); fy += 12;
            graphics.text(font, "§7Fig Best: §a100,000/100,000", card2X + 6, fy, 0xFFFFFFFF, true); fy += 12;
            graphics.text(font, "§7Fig Fortune Level: §c32/50", card2X + 6, fy, 0xFFFFFFFF, true);
            
            // Card 3: Mangrove
            int card3Y = gy + 72;
            graphics.fill(card2X, card3Y, card2X + card2W, card3Y + 65, 0xCC1E1B26);
            graphics.fill(card2X + 1, card3Y + 1, card2X + card2W - 1, card3Y + 18, 0x803A2D4C);
            graphics.text(font, "§dMangrove", card2X + 45, card3Y + 5, 0xFFFFFFFF, true);
            
            int my = card3Y + 22;
            graphics.text(font, "§7Mangrove P. Bests: §aYes", card2X + 6, my, 0xFFFFFFFF, true); my += 12;
            graphics.text(font, "§7Mangrove Best: §a100k/100k", card2X + 6, my, 0xFFFFFFFF, true); my += 12;
            graphics.text(font, "§7Mangrove Fortune: §c25/50", card2X + 6, my, 0xFFFFFFFF, true);
        }
    }

    private void drawDungeonsTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        int gy = py + 25;
        int cardH = 200;
        
        // -------------------------------------------------------------
        // CARD 1: Dungeon Info
        // -------------------------------------------------------------
        int card1X = px + 15;
        int card1W = 120;
        graphics.fill(card1X, gy, card1X + card1W, gy + cardH, 0xCC1E1B26);
        graphics.fill(card1X, gy, card1X + card1W, gy + 1, 0xFF6B4B8B);
        graphics.fill(card1X, gy, card1X + 1, gy + cardH, 0xFF6B4B8B);
        graphics.fill(card1X + card1W - 1, gy, card1X + card1W, gy + cardH, 0xFF6B4B8B);
        graphics.fill(card1X, gy + cardH - 1, card1X + card1W, gy + cardH, 0xFF6B4B8B);
        
        graphics.fill(card1X + 1, gy + 1, card1X + card1W - 1, gy + 20, 0x803A2D4C);
        graphics.item(new ItemStack(Items.WRITABLE_BOOK), card1X + 5, gy + 2);
        graphics.text(font, "§dDungeon Info", card1X + 24, gy + 6, 0xFFFFFFFF, true);
        
        double classAvg = 0;
        if (!data.classLevelMap.isEmpty()) {
            double sum = 0;
            String[] classes = {"healer", "mage", "berserk", "archer", "tank"};
            for (String cls : classes) {
                double xp = data.classXpMap.getOrDefault(cls, 0.0);
                LevelProgress lp = getCataLevelAndProgress(xp);
                sum += Math.min(lp.level, 50);
            }
            classAvg = sum / 5.0;
        }
        
        long totalCataComps = 0;
        for (int c : data.normalFloorCompletions.values()) totalCataComps += c;
        long totalMasterComps = 0;
        for (int c : data.masterFloorCompletions.values()) totalMasterComps += c;
        long totalRuns = Math.max(1, totalCataComps + totalMasterComps);
        
        double secretsPerRun = (double) data.totalSecrets / totalRuns;
        
        int contentY = gy + 32;
        graphics.text(font, "§7Class Average: §f" + String.format("%.2f", classAvg), card1X + 10, contentY, 0xFFFFFFFF, true);
        contentY += 16;
        graphics.text(font, "§7Secrets: §f" + String.format("%,d", data.totalSecrets), card1X + 10, contentY, 0xFFFFFFFF, true);
        contentY += 16;
        graphics.text(font, "§7Secrets/Run: §f" + String.format("%.2f", secretsPerRun), card1X + 10, contentY, 0xFFFFFFFF, true);

        // -------------------------------------------------------------
        // CARD 2: Dungeon Levels
        // -------------------------------------------------------------
        int card2X = card1X + card1W + 10;
        int card2W = 145;
        graphics.fill(card2X, gy, card2X + card2W, gy + cardH, 0xCC1E1B26);
        graphics.fill(card2X, gy, card2X + card2W, gy + 1, 0xFF6B4B8B);
        graphics.fill(card2X, gy, card2X + 1, gy + cardH, 0xFF6B4B8B);
        graphics.fill(card2X + card2W - 1, gy, card2X + card2W, gy + cardH, 0xFF6B4B8B);
        graphics.fill(card2X, gy + cardH - 1, card2X + card2W, gy + cardH, 0xFF6B4B8B);
        
        graphics.fill(card2X + 1, gy + 1, card2X + card2W - 1, gy + 20, 0x803A2D4C);
        graphics.text(font, "§dDungeon Levels", card2X + 25, gy + 6, 0xFFFFFFFF, true);
        
        int levelY = gy + 28;
        
        LevelProgress cataLp = getCataLevelAndProgress(data.catacombsXp);
        boolean cataHovered = mouseX >= card2X + 5 && mouseX <= card2X + card2W - 5 && mouseY >= levelY && mouseY <= levelY + 22;
        String cataColor = cataLp.level >= 50 ? "§6" : "§7";
        graphics.text(font, cataColor + "Catacombs: " + cataLp.level, card2X + 10, levelY, 0xFFFFFFFF, true);
        drawSegmentedProgressBar(graphics, card2X + 10, levelY + 11, 125, 6, cataLp.progress, cataLp.level >= 50 ? 0xFFFFAA00 : 0xFF55FF55);
        if (cataHovered) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§eCatacombs"));
            tooltip.add(Component.literal("§7Total XP: §f" + String.format("%,.0f", data.catacombsXp)));
            tooltip.add(Component.literal("§7Progress: §f" + String.format("%.0f%%", cataLp.progress * 100)));
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
        }
        levelY += 26;
        
        String[] classes = {"healer", "mage", "berserk", "archer", "tank"};
        for (String cls : classes) {
            double xp = data.classXpMap.getOrDefault(cls, 0.0);
            LevelProgress lp = getCataLevelAndProgress(xp);
            boolean isSelected = cls.equalsIgnoreCase(data.selectedDungeonClass);
            
            boolean rowHovered = mouseX >= card2X + 5 && mouseX <= card2X + card2W - 5 && mouseY >= levelY && mouseY <= levelY + 22;
            
            String cName = cls.substring(0, 1).toUpperCase() + cls.substring(1);
            String titleColor = isSelected ? "§a" : (lp.level >= 50 ? "§6" : "§7");
            graphics.text(font, titleColor + cName + ": " + lp.level, card2X + 10, levelY, 0xFFFFFFFF, true);
            
            int barColor = lp.level >= 50 ? 0xFFFFAA00 : 0xFF55FF55;
            drawSegmentedProgressBar(graphics, card2X + 10, levelY + 11, 125, 6, lp.progress, barColor);
            
            if (rowHovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§e" + cName));
                tooltip.add(Component.literal("§7Total XP: §f" + String.format("%,.0f", xp)));
                tooltip.add(Component.literal("§7Progress: §f" + String.format("%.0f%%", lp.progress * 100)));
                if (lp.level >= 50) tooltip.add(Component.literal("§6Maxed!"));
                graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
            levelY += 26;
        }

        // -------------------------------------------------------------
        // CARD 3: Dungeon Runs
        // -------------------------------------------------------------
        int card3X = card2X + card2W + 10;
        int card3W = 155;
        graphics.fill(card3X, gy, card3X + card3W, gy + cardH, 0xCC1E1B26);
        graphics.fill(card3X, gy, card3X + card3W, gy + 1, 0xFF6B4B8B);
        graphics.fill(card3X, gy, card3X + 1, gy + cardH, 0xFF6B4B8B);
        graphics.fill(card3X + card3W - 1, gy, card3X + card3W, gy + cardH, 0xFF6B4B8B);
        graphics.fill(card3X, gy + cardH - 1, card3X + card3W, gy + cardH, 0xFF6B4B8B);
        
        graphics.fill(card3X + 1, gy + 1, card3X + card3W - 1, gy + 20, 0x803A2D4C);
        graphics.text(font, "§dDungeon Runs", card3X + 35, gy + 6, 0xFFFFFFFF, true);
        
        int tableY = gy + 28;
        graphics.text(font, "§7Cata", card3X + 70, tableY, 0xFFFFFFFF, true);
        graphics.text(font, "§7Master", card3X + 110, tableY, 0xFFFFFFFF, true);
        tableY += 16;
        
        String[] bossNames = {"Bonzo", "Scarf", "Prof.", "Thorn", "Livid", "Sadan", "Necron"};
        for (int f = 1; f <= 7; f++) {
            String bName = bossNames[f - 1];
            int cComps = data.normalFloorCompletions.getOrDefault(f, 0);
            int mComps = data.masterFloorCompletions.getOrDefault(f, 0);
            
            boolean rowHovered = mouseX >= card3X + 5 && mouseX <= card3X + card3W - 5 && mouseY >= tableY && mouseY <= tableY + 14;
            if (rowHovered) {
                graphics.fill(card3X + 5, tableY, card3X + card3W - 5, tableY + 14, 0x40FFFFFF);
            }
            
            graphics.text(font, "§7" + bName, card3X + 10, tableY + 2, 0xFFFFFFFF, true);
            graphics.text(font, "§f" + cComps, card3X + 75, tableY + 2, 0xFFFFFFFF, true);
            graphics.text(font, "§f" + mComps, card3X + 115, tableY + 2, 0xFFFFFFFF, true);
            
            if (rowHovered) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§eFloor " + f + " (" + bName + ")"));
                tooltip.add(Component.literal("§7Normal Completions: §f" + cComps));
                tooltip.add(Component.literal("§7Master Completions: §f" + mComps));
                graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
            }
            tableY += 16;
        }
        
        graphics.fill(card3X + 5, tableY, card3X + card3W - 5, tableY + 1, 0x80FFFFFF);
        tableY += 3;
        graphics.text(font, "§7Total", card3X + 10, tableY, 0xFFFFFFFF, true);
        graphics.text(font, "§f" + totalCataComps, card3X + 75, tableY, 0xFFFFFFFF, true);
        graphics.text(font, "§f" + totalMasterComps, card3X + 115, tableY, 0xFFFFFFFF, true);
    }
    
    private void drawFarmingTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        int rightX = px + 40;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 160, infoY + 40, 0x80000000);
        graphics.text(font, "§dFarming", rightX + 65, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Farming Level: " + data.farming, rightX + 5, infoY + 25, 0xFFFFFFFF, true);
    }

    private void drawFishingTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        if (currentSubTab != null && "FISHING_BAG".equals(currentSubTab.id)) {
            drawGenericTab(graphics, font, px, py, mouseX, mouseY);
            return;
        }
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        int rightX = px + 40;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 160, infoY + 40, 0x80000000);
        graphics.text(font, "§dFishing", rightX + 65, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Fishing Level: " + data.fishing, rightX + 5, infoY + 25, 0xFFFFFFFF, true);
    }



    
    private void drawGenericSidebar(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        List<SubTab> tabs = subTabsMap.get(currentTab);
        if (tabs == null) return;
        
        int subTabX = px + 10;
        int subTabY = py + 10;
        for (int i = 0; i < tabs.size(); i++) {
            SubTab sub = tabs.get(i);
            boolean selected = currentSubTab != null && currentSubTab.id.equals(sub.id);
            int ty = subTabY + i * 22;
            
            if (selected) {
                graphics.fill(subTabX, ty, subTabX + 20, ty + 1, 0xFFFFFFFF);
                graphics.fill(subTabX, ty, subTabX + 1, ty + 20, 0xFFFFFFFF);
                graphics.fill(subTabX + 19, ty, subTabX + 20, ty + 20, 0xFFFFFFFF);
                graphics.fill(subTabX, ty + 19, subTabX + 20, ty + 20, 0xFFFFFFFF);
            }
            graphics.item(sub.icon, subTabX + 2, ty + 2);
            
            if (mouseX >= subTabX && mouseX <= subTabX + 20 && mouseY >= ty && mouseY <= ty + 20) {
                graphics.setTooltipForNextFrame(font, List.of(Component.literal(sub.name)), Optional.empty(), mouseX, mouseY);
            }
        }
    }
    
    private void drawGenericTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        
        // Render 2x4 Armor Grid ONLY for Inventory for now, or everything.
        // Actually the reference image shows 2x4 grid present when in Mining tab too, 
        // but it doesn't matter much. Let's just draw it if it's Inventory or we can draw it everywhere.
        // For accurate replication, let's draw it.
        int aeX = px + 40;
        int aeY = py + 40;

        ItemStack hoveredStack = null;
        int hx = 0, hy = 0;

        for (int i = 0; i < 4; i++) {
            // Armor (Left)
            int ax = aeX;
            int ay = aeY + i * 18;
            graphics.fill(ax, ay, ax + 18, ay + 18, 0x80111111); // Dark slot bg
            ItemStack armorItem = (data.armor.size() > i) ? data.armor.get(3 - i) : null; // Reversed to draw Helmet at top
            if (armorItem != null && !armorItem.isEmpty()) {
                graphics.item(armorItem, ax + 1, ay + 1);
                graphics.itemDecorations(font, armorItem, ax + 1, ay + 1);
                if (mouseX >= ax && mouseX <= ax + 18 && mouseY >= ay && mouseY <= ay + 18) {
                    graphics.fill(ax + 1, ay + 1, ax + 17, ay + 17, 0x80FFFFFF);
                    hoveredStack = armorItem; hx = ax; hy = ay;
                }
            }
            
            // Equipment (Right)
            int ex = aeX + 18;
            int ey = aeY + i * 18;
            graphics.fill(ex, ey, ex + 18, ey + 18, 0x80111111); // Dark slot bg
            ItemStack equipItem = (data.equipment.size() > i) ? data.equipment.get(i) : null;
            if (equipItem != null && !equipItem.isEmpty()) {
                graphics.item(equipItem, ex + 1, ey + 1);
                graphics.itemDecorations(font, equipItem, ex + 1, ey + 1);
                if (mouseX >= ex && mouseX <= ex + 18 && mouseY >= ey && mouseY <= ey + 18) {
                    graphics.fill(ex + 1, ey + 1, ex + 17, ey + 17, 0x80FFFFFF);
                    hoveredStack = equipItem; hx = ex; hy = ey;
                }
            }
        }
        
        // Draw Main Inventory Area
        int gx = px + 100;
        int gy = py + 40;
        
        List<ItemStack> items = getActiveItems();
        int cols = 9;
        int rows = 6;
        
        // Inner Pages Bar
        int maxPages = getMaxInnerPages();
        if (maxPages > 1) {
            int bw = 16;
            for (int i = 0; i < maxPages; i++) {
                int bx = gx + i * (bw + 2);
                int by = gy - 20;
                int bgColor = innerPage == i ? 0xAA296A29 : 0x80222222;
                graphics.fill(bx, by, bx + bw, by + 14, bgColor);
                
                String label = String.valueOf(i + 1);
                if (currentSubTab != null && "BACKPACKS".equals(currentSubTab.id)) {
                    Object[] keys = data.backpacks.keySet().toArray();
                    if (i < keys.length) label = String.valueOf(keys[i]);
                }
                graphics.text(font, label, bx + bw/2 - font.width(label)/2, by + 3, 0xFFFFFFFF, true);
            }
        }
        
        int idx = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int ix = gx + c * 18;
                int iy = gy + r * 18;
                graphics.fill(ix, iy, ix + 18, iy + 18, 0x80111111); // Dark slot bg
                
                if (idx < items.size()) {
                    ItemStack stack = items.get(idx);
                    if (stack != null && !stack.isEmpty()) {
                        graphics.item(stack, ix + 1, iy + 1);
                        graphics.itemDecorations(font, stack, ix + 1, iy + 1);
                        if (mouseX >= ix && mouseX <= ix + 18 && mouseY >= iy && mouseY <= iy + 18) {
                            graphics.fill(ix + 1, iy + 1, ix + 17, iy + 17, 0x80FFFFFF);
                            hoveredStack = stack; hx = ix; hy = iy;
                        }
                    }
                }
                idx++;
            }
        }
        
        if (hoveredStack != null && !hoveredStack.isEmpty()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(hoveredStack.getHoverName());
            net.minecraft.world.item.component.ItemLore lore = hoveredStack.get(DataComponents.LORE);
            if (lore != null) tooltip.addAll(lore.lines());
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), hx, hy);
        }
    }
    
    private void drawPetsTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        int sx = px + 20;
        int sy = py + 20;
        int maxVisible = 12;
        
        for (int i = 0; i < maxVisible; i++) {
            int petIdx = scrollOffset + i;
            if (petIdx >= data.pets.size()) break;
            me.bombo.bomboaddons.features.profile.ProfileFetcher.Pet pet = data.pets.get(petIdx);
            
            int col = i / 6;
            int row = i % 6;
            int x = sx + col * 210;
            int y = sy + row * 38;
            
            int bgColor = pet.active ? 0xAA296A29 : 0x80000000;
            graphics.fill(x, y, x + 200, y + 34, bgColor);
            graphics.fill(x, y, x + 200, y + 1, 0xFF111111);
            graphics.fill(x, y, x + 1, y + 34, 0xFF111111);
            graphics.fill(x + 199, y, x + 200, y + 34, 0xFF111111);
            graphics.fill(x, y + 33, x + 200, y + 34, 0xFF111111);
            
            ItemStack icon = new ItemStack(Items.BONE);
            if (pet.type.contains("DRAGON")) icon = new ItemStack(Items.DRAGON_HEAD);
            else if (pet.type.contains("ZOMBIE")) icon = new ItemStack(Items.ZOMBIE_HEAD);
            else if (pet.type.contains("CREEPER")) icon = new ItemStack(Items.CREEPER_HEAD);
            else if (pet.type.contains("SKELETON")) icon = new ItemStack(Items.SKELETON_SKULL);
            graphics.item(icon, x + 4, y + 8);
            
            String color = pet.tier.equals("LEGENDARY") ? "§6" : pet.tier.equals("MYTHIC") ? "§d" : pet.tier.equals("EPIC") ? "§5" : pet.tier.equals("RARE") ? "§9" : pet.tier.equals("UNCOMMON") ? "§a" : "§f";
            graphics.text(font, color + pet.tier + " " + pet.type, x + 30, y + 6, 0xFFFFFFFF, true);
            
            int barWidth = 150;
            int barX = x + 30;
            int barY = y + 20;
            graphics.fill(barX, barY, barX + barWidth, barY + 6, 0xFF222222);
            graphics.fill(barX, barY, barX + (int)(barWidth * 0.7), barY + 6, 0xFF00AA00);
            graphics.text(font, String.format("%,.0f EXP", pet.exp), barX + 2, barY - 1, 0xFFFFFFFF, true);
        }
    }
    
    private int getMaxInnerPages() {
        if (currentSubTab == null) return 1;
        if ("WARDROBE".equals(currentSubTab.id)) return (data.wardrobe.size() + 53) / 54;
        if ("ACCESSORIES".equals(currentSubTab.id)) return (data.accessories.size() + 53) / 54;
        if ("BACKPACKS".equals(currentSubTab.id)) return data.backpacks.size();
        if ("ENDER_CHEST".equals(currentSubTab.id)) return (data.enderChest.size() + 53) / 54;
        return 1;
    }

    private List<ItemStack> getActiveItems() {
        if (currentSubTab == null) return new ArrayList<>();
        if ("MAIN".equals(currentSubTab.id)) return data.inventory;
        
        List<ItemStack> src = new ArrayList<>();
        if ("WARDROBE".equals(currentSubTab.id)) src = data.wardrobe;
        else if ("ACCESSORIES".equals(currentSubTab.id)) src = data.accessories;
        else if ("ENDER_CHEST".equals(currentSubTab.id)) src = data.enderChest;
        else if ("MISC_BAGS".equals(currentSubTab.id)) src = data.personalVault; // Placeholder
        else if ("FISHING_BAG".equals(currentSubTab.id)) src = data.fishingBag;
        else if ("MUSEUM_WEAPONS".equals(currentSubTab.id)) src = data.museumWeapons;
        else if ("MUSEUM_ARMOR".equals(currentSubTab.id)) src = data.museumArmor;
        else if ("MUSEUM_RARITIES".equals(currentSubTab.id)) src = data.museumRarities;
        else if (currentSubTab.id.startsWith("MUSEUM_")) {
            src = new ArrayList<>(data.museumWeapons);
            src.addAll(data.museumArmor);
            src.addAll(data.museumRarities);
            src.addAll(data.museumSpecial);
        }
        
        if ("BACKPACKS".equals(currentSubTab.id)) {
            Object[] keys = data.backpacks.keySet().toArray();
            if (keys.length > 0 && innerPage < keys.length) return data.backpacks.get((Integer)keys[innerPage]);
            return new ArrayList<>();
        }
        
        int start = innerPage * 54;
        if (start < src.size()) return src.subList(start, Math.min(start + 54, src.size()));
        return new ArrayList<>();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean handled) {
        int panelW = 460;
        int panelH = 260;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;
        
        // Handle Top Tabs
        List<Tab> vTabs = getVisibleTabs();
        int topTabY = py - 22;
        for (int i = 0; i < vTabs.size(); i++) {
            int tx = px + i * 22;
            if (event.x() >= tx && event.x() <= tx + 20 && event.y() >= topTabY && event.y() <= topTabY + 20) {
                currentTab = vTabs.get(i);
                innerPage = 0;
                scrollOffset = 0;
                List<SubTab> tList = subTabsMap.get(currentTab);
                currentSubTab = (tList != null && !tList.isEmpty()) ? tList.get(0) : null;
                return true;
            }
        }
        
        // Handle Side Tabs
        List<SubTab> tabs = subTabsMap.get(currentTab);
        if (tabs != null) {
            int subTabX = px + 10;
            int subTabY = py + 10;
            for (int i = 0; i < tabs.size(); i++) {
                int ty = subTabY + i * 22;
                if (event.x() >= subTabX && event.x() <= subTabX + 20 && event.y() >= ty && event.y() <= ty + 20) {
                    currentSubTab = tabs.get(i);
                    innerPage = 0;
                    return true;
                }
            }
            
            // Handle Inner Pages
            int maxPages = getMaxInnerPages();
            if (maxPages > 1) {
                int gx = px + 100;
                int gy = py + 40;
                int bw = 16;
                for (int i = 0; i < maxPages; i++) {
                    int bx = gx + i * (bw + 2);
                    int by = gy - 20;
                    if (event.x() >= bx && event.x() <= bx + bw && event.y() >= by && event.y() <= by + 14) {
                        innerPage = i;
                        return true;
                    }
                }
            }
        }
        
        return super.mouseClicked(event, handled);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == Tab.PETS) {
            if (scrollY > 0 && scrollOffset > 0) scrollOffset -= 2;
            else if (scrollY < 0 && scrollOffset < data.pets.size() - 12) scrollOffset += 2;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    
    private void drawRiftTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        int gx = px + 20;
        int gy = py + 20;
        
        graphics.text(font, "§d§lInformation", gx, gy, 0xFFFFFFFF, true);
        gy += 15;
        
        graphics.text(font, "§8Motes: §d" + String.format("%,d", data.riftMotes), gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        graphics.text(font, "§8Lifetime Motes: §d" + String.format("%,d", data.riftLifetimeMotes), gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        graphics.text(font, "§8Visits: §d" + String.format("%,d", data.riftVisits), gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        
        int seconds = data.riftSecondsSitting;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        String timeStr = "";
        if (h > 0) timeStr += h + "h ";
        if (m > 0 || h > 0) timeStr += m + "m ";
        timeStr += s + "s";
        graphics.text(font, "§8Time sitting with Ävaeìkx: §5" + timeStr, gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        
        int maxSouls = 52;
        String soulColor = data.riftEnigmaSouls >= maxSouls ? "§5" : "§d";
        graphics.text(font, "§8Enigma Souls: " + soulColor + data.riftEnigmaSouls + "§5/" + maxSouls, gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        
        int maxCats = 9;
        String catColor = data.riftDeadCats >= maxCats ? "§5" : "§d";
        graphics.text(font, "§8Found Cats: " + catColor + data.riftDeadCats + "§5/" + maxCats, gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        
        int maxEyes = 8;
        String eyeColor = data.riftUnlockedEyes >= maxEyes ? "§5" : "§d";
        graphics.text(font, "§8Unlocked Eyes: " + eyeColor + data.riftUnlockedEyes + "§5/" + maxEyes, gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        
        int maxGrubber = 5;
        String grubColor = data.riftGrubber >= maxGrubber ? "§5" : "§d";
        graphics.text(font, "§8Grubber Stacks: " + grubColor + data.riftGrubber + "§5/" + maxGrubber, gx, gy, 0xFFFFFFFF, true);
        gy += 12;
        
        // Draw Timecharms
        int cx = px + 220;
        int cy = py + 20;
        graphics.text(font, "§d§lTimecharms", cx, cy, 0xFFFFFFFF, true);
        cy += 15;
        
        String[] charmNames = {"Supreme", "Bacte", "Leech", "Vampire", "Bacteria", "Crux", "Porhtal", "Stability"};
        String[] charmApiIds = {"wyldly_supreme", "lazy_living", "slime", "vampiric", "citizen", "mountain", "chicken_n_egg", "mirrored"};
        net.minecraft.world.item.Item[] charmItems = {
            Items.NETHER_STAR,          // Supreme
            Items.SLIME_BALL,           // Bacte
            Items.PRISMARINE_CRYSTALS,  // Leech
            Items.FERMENTED_SPIDER_EYE, // Vampire
            Items.SPIDER_EYE,           // Bacteria
            Items.AMETHYST_SHARD,       // Crux
            Items.EGG,                  // Porhtal
            Items.CLOCK                 // Stability
        };
        for (int i = 0; i < charmNames.length; i++) {
            String charmName = charmNames[i];
            String apiId = charmApiIds[i];
            me.bombo.bomboaddons.features.profile.ProfileFetcher.Trophy found = null;
            for (me.bombo.bomboaddons.features.profile.ProfileFetcher.Trophy t : data.riftTrophies) {
                if (t.type.equalsIgnoreCase(apiId) || t.type.equalsIgnoreCase(charmName)) {
                    found = t;
                    break;
                }
            }
            
            int drawX = cx + (i % 4) * 20;
            int drawY = cy + (i / 4) * 20;
            
            ItemStack icon = (found != null) ? new ItemStack(charmItems[i]) : new ItemStack(Items.GRAY_DYE);
            graphics.item(icon, drawX, drawY);
            
            if (mouseX >= drawX && mouseX <= drawX + 16 && mouseY >= drawY && mouseY <= drawY + 16) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal("§a" + charmName + " Timecharm"));
                if (found != null) {
                    tooltip.add(Component.literal(""));
                    tooltip.add(Component.literal("§7Found after §a" + found.visits + " §7visits"));
                    long diff = System.currentTimeMillis() - found.timestamp;
                    long days = diff / (1000 * 60 * 60 * 24);
                    tooltip.add(Component.literal("§7Unlocked §a" + days + " §7days ago"));
                } else {
                    tooltip.add(Component.literal("§cLocked"));
                }
                graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), drawX, drawY);
            }
        }
    }
    
    private void drawChocolateFactoryTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        int gx = px + 20;
        int gy = py + 20;
        
        graphics.text(font, "§6§lChocolate Factory", gx, gy, 0xFFFFFFFF, true);
        gy += 20;
        
        graphics.text(font, "§8Chocolate: §6" + String.format("%,d", data.cfChocolate), gx, gy, 0xFFFFFFFF, true);
        gy += 14;
        graphics.text(font, "§8Total Chocolate: §6" + String.format("%,d", data.cfTotalChocolate), gx, gy, 0xFFFFFFFF, true);
        gy += 14;
        graphics.text(font, "§8Chocolate since Prestige: §6" + String.format("%,d", data.cfChocolateSincePrestige), gx, gy, 0xFFFFFFFF, true);
        gy += 20;
        
        graphics.text(font, "§8Prestige Level: §e" + data.cfPrestigeLevel, gx, gy, 0xFFFFFFFF, true);
        gy += 14;
        graphics.text(font, "§8Multiplier Upgrades: §e" + data.cfMultiplierUpgrades, gx, gy, 0xFFFFFFFF, true);
    }
    
    private net.minecraft.client.renderer.entity.state.EntityRenderState extractRenderStateEntity(net.minecraft.world.entity.LivingEntity livingEntity) {
        net.minecraft.client.renderer.entity.EntityRenderDispatcher entityRenderDispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.state.EntityRenderState entityRenderState = entityRenderDispatcher.extractEntity(livingEntity, 1.0F);
        entityRenderState.lightCoords = 15728880;
        entityRenderState.shadowPieces.clear();
        entityRenderState.outlineColor = 0;
        return entityRenderState;
    }
}
