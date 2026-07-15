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
        RIFT("Rift", new ItemStack(Items.ENDER_PEARL));

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

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Very subtle background blur/dim
        graphics.fill(0, 0, this.width, this.height, 0x40000000);
        
        int panelW = 460;
        int panelH = 260;
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;

        // Draw top main tabs (compact icons)
        int tabWidth = 20;
        int topTabY = py - 22;
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
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
        else if (currentTab == Tab.COMBAT) drawCombatTab(graphics, font, px, py);
        else if (currentTab == Tab.MINING) drawMiningTab(graphics, font, px, py);
        else if (currentTab == Tab.COLLECTIONS) drawCollectionsTab(graphics, font, px, py);
        else if (currentTab == Tab.PETS) drawPetsTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.FORAGING) drawForagingTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.FARMING) drawFarmingTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.FISHING) drawFishingTab(graphics, font, px, py, mouseX, mouseY);
        else if (currentTab == Tab.RIFT) drawRiftTab(graphics, font, px, py, mouseX, mouseY);
        else drawGenericTab(graphics, font, px, py, mouseX, mouseY);
        
        // Draw hover tooltips for Top Tabs
        for (int i = 0; i < Tab.values().length; i++) {
            Tab tab = Tab.values()[i];
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
    
    private void drawCombatTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py) {
        drawGenericSidebar(graphics, font, px, py, 0, 0);
        
        int rightX = px + 40;
        int infoY = py + 20;
        
        graphics.fill(rightX, infoY, rightX + 160, infoY + 60, 0x80000000);
        graphics.text(font, "§dSlayer", rightX + 65, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Z " + data.zombieSlayer + " | S " + data.spiderSlayer + " | W " + data.wolfSlayer, rightX + 5, infoY + 25, 0xFFFFFFFF, true);
        graphics.text(font, "E " + data.endermanSlayer + " | B " + data.blazeSlayer + " | V " + data.vampireSlayer, rightX + 5, infoY + 37, 0xFFFFFFFF, true);
        
        int dungY = infoY + 70;
        graphics.fill(rightX, dungY, rightX + 160, dungY + 40, 0x80000000);
        graphics.text(font, "§dDungeons", rightX + 55, dungY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Catacombs Level: " + data.catacombs, rightX + 5, dungY + 25, 0xFFFFFFFF, true);
    }
    
    private void drawMiningTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py) {
        drawGenericSidebar(graphics, font, px, py, 0, 0);
        
        int rightX = px + 40;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 160, infoY + 60, 0x80000000);
        graphics.text(font, "§dMining", rightX + 65, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "HotM Exp: §6" + String.format("%.0f", data.hotmExp), rightX + 5, infoY + 25, 0xFFFFFFFF, true);
    }
    
    private void drawCollectionsTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py) {
        int rightX = px + 20;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 160, infoY + 60, 0x80000000);
        graphics.text(font, "§dCollections", rightX + 50, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Total Collections: §a" + data.totalCollections, rightX + 5, infoY + 25, 0xFFFFFFFF, true);
    }
    
    private void drawForagingTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        int rightX = px + 40;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 160, infoY + 80, 0x80000000);
        graphics.text(font, "§dForaging", rightX + 60, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Whispers: " + data.foragingWhispers, rightX + 5, infoY + 25, 0xFFFFFFFF, true);
        graphics.text(font, "Spent Whispers: " + data.foragingSpentWhispers, rightX + 5, infoY + 37, 0xFFFFFFFF, true);
        graphics.text(font, "Fig Tree Gifts: " + data.foragingFig, rightX + 5, infoY + 49, 0xFFFFFFFF, true);
        graphics.text(font, "Mangrove Tree Gifts: " + data.foragingMangrove, rightX + 5, infoY + 61, 0xFFFFFFFF, true);
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


    private void drawRiftTab(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int px, int py, int mouseX, int mouseY) {
        drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
        int rightX = px + 40;
        int infoY = py + 20;
        graphics.fill(rightX, infoY, rightX + 160, infoY + 60, 0x80000000);
        graphics.text(font, "§dRift", rightX + 65, infoY + 5, 0xFFFFFFFF, true);
        graphics.text(font, "Visits: " + data.riftVisits, rightX + 5, infoY + 25, 0xFFFFFFFF, true);
        graphics.text(font, "Lifetime Motes: " + data.riftMotes, rightX + 5, infoY + 37, 0xFFFFFFFF, true);
        graphics.text(font, "Grubber Stacks: " + data.riftGrubber, rightX + 5, infoY + 49, 0xFFFFFFFF, true);
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
        int topTabY = py - 22;
        for (int i = 0; i < Tab.values().length; i++) {
            int tx = px + i * 22;
            if (event.x() >= tx && event.x() <= tx + 20 && event.y() >= topTabY && event.y() <= topTabY + 20) {
                currentTab = Tab.values()[i];
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
    
    private net.minecraft.client.renderer.entity.state.EntityRenderState extractRenderStateEntity(net.minecraft.world.entity.LivingEntity livingEntity) {
        net.minecraft.client.renderer.entity.EntityRenderDispatcher entityRenderDispatcher = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher();
        net.minecraft.client.renderer.entity.state.EntityRenderState entityRenderState = entityRenderDispatcher.extractEntity(livingEntity, 1.0F);
        entityRenderState.lightCoords = 15728880;
        entityRenderState.shadowPieces.clear();
        entityRenderState.outlineColor = 0;
        return entityRenderState;
    }
}
