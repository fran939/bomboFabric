package me.bombo.bomboaddons.gui;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import me.bombo.bomboaddons.features.profile.ProfileFetcher;
import me.bombo.bomboaddons.utils.FakePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ProfileViewerScreen extends Screen {
   private final ProfileFetcher.ProfileData data;
   private FakePlayer fakePlayer;
   private Tab currentTab;
   private SubTab currentSubTab;
   private int innerPage;
   private int scrollOffset;
   private Map<Tab, List<SubTab>> subTabsMap;
   private static final double[] CATA_XP_TABLE = new double[]{(double)0.0F, (double)50.0F, (double)125.0F, (double)235.0F, (double)395.0F, (double)625.0F, (double)955.0F, (double)1425.0F, (double)2095.0F, (double)3045.0F, (double)4385.0F, (double)6275.0F, (double)8940.0F, (double)12700.0F, (double)17960.0F, (double)25340.0F, (double)35640.0F, (double)50040.0F, (double)70040.0F, (double)98040.0F, (double)137040.0F, (double)191040.0F, (double)265040.0F, (double)365040.0F, (double)500040.0F, (double)680040.0F, (double)910040.0F, (double)1200040.0F, (double)1550040.0F, (double)1970040.0F, (double)2470040.0F, (double)3070040.0F, (double)3800040.0F, (double)4700040.0F, (double)5800040.0F, (double)7150040.0F, (double)8800040.0F, (double)1.080004E7F, (double)1.320004E7F, (double)1.610004E7F, (double)1.960004E7F, (double)2.390004E7F, (double)2.920004E7F, (double)3.570004E7F, (double)4.360004E7F, (double)5.320004E7F, (double)6.480004E7F, (double)7.880004E7F, (double)9.560004E7F, (double)1.1560004E8F, 1.3960004E8};

   public ProfileViewerScreen(ProfileFetcher.ProfileData data) {
      super(Component.literal(data.username + "'s Profile"));
      this.currentTab = ProfileViewerScreen.Tab.INVENTORY;
      this.currentSubTab = null;
      this.innerPage = 0;
      this.scrollOffset = 0;
      this.subTabsMap = new HashMap();
      this.data = data;

      try {
         this.fakePlayer = new FakePlayer(new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + data.username).getBytes()), data.username), data.armor, Component.literal(data.username));
      } catch (Exception var9) {
      }

      List<SubTab> invTabs = new ArrayList();
      invTabs.add(new SubTab("MAIN", "Main Inventory", new ItemStack(Items.CHEST)));
      invTabs.add(new SubTab("ENDER_CHEST", "Ender Chest", new ItemStack(Items.ENDER_CHEST)));
      invTabs.add(new SubTab("BACKPACKS", "Backpacks", new ItemStack(Items.BUNDLE)));
      invTabs.add(new SubTab("WARDROBE", "Wardrobe", new ItemStack(Items.LEATHER_CHESTPLATE)));
      invTabs.add(new SubTab("EQUIPMENT", "Equipment", new ItemStack(Items.CHAINMAIL_CHESTPLATE)));
      invTabs.add(new SubTab("ACCESSORIES", "Accessories", new ItemStack(Items.TOTEM_OF_UNDYING)));
      invTabs.add(new SubTab("SACKS", "Sacks", new ItemStack(Items.SADDLE)));
      invTabs.add(new SubTab("MISC_BAGS", "Misc Bags", new ItemStack(Items.RABBIT_HIDE)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.INVENTORY, invTabs);
      List<SubTab> combatTabs = new ArrayList();
      combatTabs.add(new SubTab("DUNGEONS", "Dungeons", new ItemStack(Items.ZOMBIE_HEAD)));
      combatTabs.add(new SubTab("BESTIARY", "Bestiary", new ItemStack(Items.WRITABLE_BOOK)));
      combatTabs.add(new SubTab("ISLE", "Crimson Isle", new ItemStack(Items.NETHERRACK)));
      combatTabs.add(new SubTab("MOBS", "Kills and Deaths", new ItemStack(Items.SKELETON_SKULL)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.COMBAT, combatTabs);
      List<SubTab> miningTabs = new ArrayList();
      miningTabs.add(new SubTab("MINING_MAIN", "Mining", new ItemStack(Items.DIAMOND_PICKAXE)));
      miningTabs.add(new SubTab("GEAR", "Mining Gear", new ItemStack(Items.PRISMARINE_SHARD)));
      miningTabs.add(new SubTab("HOTM", "Heart of the Mountain", new ItemStack(Items.EMERALD)));
      miningTabs.add(new SubTab("GLACITE", "Glacite Tunnels", new ItemStack(Items.BLUE_ICE)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.MINING, miningTabs);
      List<SubTab> farmingTabs = new ArrayList();
      farmingTabs.add(new SubTab("FARMING_MAIN", "Farming", new ItemStack(Items.WHEAT)));
      farmingTabs.add(new SubTab("CONTESTS", "Contests", new ItemStack(Items.GOLD_BLOCK)));
      farmingTabs.add(new SubTab("DESK", "Desk", new ItemStack(Items.OAK_SIGN)));
      farmingTabs.add(new SubTab("VISITORS", "Visitors", new ItemStack(Items.PLAYER_HEAD)));
      farmingTabs.add(new SubTab("MUTATIONS", "Mutations", new ItemStack(Items.SLIME_BALL)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.FARMING, farmingTabs);
      List<SubTab> foragingTabs = new ArrayList();
      foragingTabs.add(new SubTab("FORAGING_MAIN", "Foraging", new ItemStack(Items.OAK_WOOD)));
      foragingTabs.add(new SubTab("TREE", "Heart of the Tree", new ItemStack(Items.OAK_SAPLING)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.FORAGING, foragingTabs);
      List<SubTab> museumTabs = new ArrayList();
      museumTabs.add(new SubTab("MUSEUM_MAIN", "Museum", new ItemStack(Items.GOLD_BLOCK)));
      museumTabs.add(new SubTab("MUSEUM_WEAPONS", "Weapons", new ItemStack(Items.IRON_SWORD)));
      museumTabs.add(new SubTab("MUSEUM_ARMOR", "Armor", new ItemStack(Items.IRON_CHESTPLATE)));
      museumTabs.add(new SubTab("MUSEUM_RARITIES", "Rarities", new ItemStack(Items.DRAGON_EGG)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.MUSEUM, museumTabs);
      List<SubTab> fishingTabs = new ArrayList();
      fishingTabs.add(new SubTab("FISHING_MAIN", "Fishing Stats", new ItemStack(Items.FISHING_ROD)));
      fishingTabs.add(new SubTab("FISHING_BAG", "Fishing Bag", new ItemStack(Items.PUFFERFISH)));
      this.subTabsMap.put(ProfileViewerScreen.Tab.FISHING, fishingTabs);
      this.currentSubTab = (SubTab)invTabs.get(0);
   }

   private List<Tab> getVisibleTabs() {
      List<Tab> visibleTabs = new ArrayList();

      for(Tab t : ProfileViewerScreen.Tab.values()) {
         if ((t != ProfileViewerScreen.Tab.CHOCOLATE_FACTORY || this.data.cfTotalChocolate != 0L) && (t != ProfileViewerScreen.Tab.MUSEUM || !this.data.museumWeapons.isEmpty() || !this.data.museumArmor.isEmpty() || !this.data.museumRarities.isEmpty() || !this.data.museumSpecial.isEmpty())) {
            visibleTabs.add(t);
         }
      }

      return visibleTabs;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      graphics.fill(0, 0, this.width, this.height, 1073741824);
      int panelW = 460;
      int panelH = 260;
      int px = (this.width - panelW) / 2;
      int py = (this.height - panelH) / 2;
      Font font = Minecraft.getInstance().font;
      List<Tab> visibleTabs = this.getVisibleTabs();
      int tabWidth = 20;
      int topTabY = py - 22;

      for(int i = 0; i < visibleTabs.size(); ++i) {
         Tab tab = (Tab)visibleTabs.get(i);
         boolean selected = this.currentTab == tab;
         int tx = px + i * 22;
         if (selected) {
            graphics.fill(tx, topTabY, tx + tabWidth, topTabY + 1, -1);
            graphics.fill(tx, topTabY, tx + 1, topTabY + 20, -1);
            graphics.fill(tx + tabWidth - 1, topTabY, tx + tabWidth, topTabY + 20, -1);
            graphics.fill(tx, topTabY + 19, tx + tabWidth, topTabY + 20, -1);
         }

         graphics.item(tab.icon, tx + 2, topTabY + 2);
      }

      graphics.fill(px, py, px + panelW, py + panelH, -870309856);
      graphics.fill(px, py, px + panelW, py + 1, 1090519039);
      graphics.fill(px, py, px + 1, py + panelH, 1090519039);
      graphics.fill(px + panelW - 1, py, px + panelW, py + panelH, 1090519039);
      graphics.fill(px, py + panelH - 1, px + panelW, py + panelH, 1090519039);
      if (this.currentTab == ProfileViewerScreen.Tab.HOME) {
         this.drawHomeTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.COMBAT) {
         this.drawCombatTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.MINING) {
         this.drawMiningTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.COLLECTIONS) {
         this.drawCollectionsTab(graphics, font, px, py);
      } else if (this.currentTab == ProfileViewerScreen.Tab.PETS) {
         this.drawPetsTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.FORAGING) {
         this.drawForagingTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.FARMING) {
         this.drawFarmingTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.FISHING) {
         this.drawFishingTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.RIFT) {
         this.drawRiftTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.CHOCOLATE_FACTORY) {
         this.drawChocolateFactoryTab(graphics, font, px, py, mouseX, mouseY);
      } else if (this.currentTab == ProfileViewerScreen.Tab.DUNGEONS) {
         this.drawDungeonsTab(graphics, font, px, py, mouseX, mouseY);
      } else {
         this.drawGenericTab(graphics, font, px, py, mouseX, mouseY);
      }

      for(int i = 0; i < visibleTabs.size(); ++i) {
         Tab tab = (Tab)visibleTabs.get(i);
         int tx = px + i * 22;
         if (mouseX >= tx && mouseX <= tx + tabWidth && mouseY >= topTabY && mouseY <= topTabY + 20) {
            graphics.setTooltipForNextFrame(font, List.of(Component.literal(tab.name)), Optional.empty(), mouseX, mouseY);
         }
      }

      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   private void drawHomeTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      int panelW = 460;
      int colW = panelW / 3;
      int infoX = px + 10;
      int infoY = py + 20;
      graphics.fill(infoX, infoY, infoX + colW - 20, infoY + 200, Integer.MIN_VALUE);
      graphics.text(font, "§eInfo", infoX + colW / 2 - 25, infoY + 5, -1, true);
      int sy = infoY + 25;
      graphics.text(font, "Purse: §6" + String.format("%,.0f", this.data.purse), infoX + 5, sy, -1, true);
      sy += 12;
      graphics.text(font, "Bank: §6" + String.format("%,.0f", this.data.bank), infoX + 5, sy, -1, true);
      sy += 12;
      graphics.text(font, "SkyBlock Level: §6" + String.format("%.2f", this.data.skyblockLevel), infoX + 5, sy, -1, true);
      sy += 12;
      String nw = "Unknown";
      if (this.data.networth > (double)0.0F) {
         if (this.data.networth >= (double)1.0E9F) {
            nw = String.format("%.1fB", this.data.networth / (double)1.0E9F);
         } else if (this.data.networth >= (double)1000000.0F) {
            nw = String.format("%.1fM", this.data.networth / (double)1000000.0F);
         } else if (this.data.networth >= (double)1000.0F) {
            nw = String.format("%.1fk", this.data.networth / (double)1000.0F);
         } else {
            nw = String.format("%.0f", this.data.networth);
         }
      }

      graphics.text(font, "Net Worth: §a" + nw, infoX + 5, sy, -1, true);
      int midX = px + colW;
      graphics.fill(midX, infoY + 50, midX + colW, infoY + 200, 805306368);
      graphics.text(font, "§e" + this.data.username, midX + colW / 2 - font.width(this.data.username) / 2, infoY + 55, -1, true);
      if (this.fakePlayer != null) {
         float entityX1 = (float)(midX + 10);
         float entityY1 = (float)(infoY + 70);
         float entityX2 = (float)(midX + colW - 10);
         float entityY2 = (float)(infoY + 190);
         float n = (entityX1 + entityX2) / 2.0F;
         float o = (entityY1 + entityY2) / 2.0F;
         float p = (float)Math.atan((double)((n - (float)mouseX) / 40.0F));
         float q = (float)Math.atan((double)((o - (float)mouseY) / 40.0F));
         Quaternionf quaternionf = (new Quaternionf()).rotateZ((float)Math.PI);
         Quaternionf quaternionf2 = (new Quaternionf()).rotateX(q * 20.0F * ((float)Math.PI / 180F));
         quaternionf.mul(quaternionf2);
         EntityRenderState state = this.extractRenderStateEntity(this.fakePlayer);
         if (state instanceof LivingEntityRenderState) {
            LivingEntityRenderState livingState = (LivingEntityRenderState)state;
            livingState.bodyRot = 180.0F + p * 20.0F;
            livingState.yRot = p * 20.0F;
            livingState.xRot = -q * 20.0F;
            livingState.boundingBoxWidth /= livingState.scale;
            livingState.boundingBoxHeight /= livingState.scale;
            livingState.scale = 1.0F;
         }

         Vector3f vector3f = new Vector3f(0.0F, state.boundingBoxHeight / 2.0F + 0.0625F, 0.0F);
         graphics.entity(state, 45.0F, vector3f, quaternionf, quaternionf2, (int)entityX1, (int)entityY1, (int)entityX2, (int)entityY2);
      }

      int rightX = px + colW * 2 + 10;
      graphics.fill(rightX, infoY, rightX + colW - 20, infoY + 200, Integer.MIN_VALUE);
      graphics.text(font, "§dSkills", rightX + colW / 2 - 25, infoY + 5, -1, true);
      int rsy = infoY + 25;
      graphics.text(font, "Farming: " + this.data.farming, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Mining: " + this.data.mining, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Combat: " + this.data.combat, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Foraging: " + this.data.foraging, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Fishing: " + this.data.fishing, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Enchanting: " + this.data.enchanting, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Alchemy: " + this.data.alchemy, rightX + 5, rsy, -1, true);
      rsy += 12;
      graphics.text(font, "Taming: " + this.data.taming, rightX + 5, rsy, -1, true);
   }

   private void drawCombatTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      this.drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
      int rightX = px + 40;
      int infoY = py + 15;
      graphics.fill(rightX, infoY, rightX + 260, infoY + 125, Integer.MIN_VALUE);
      graphics.text(font, "§d§lSlayer", rightX + 110, infoY + 5, -1, true);
      String[] slayerNames = new String[]{"Revenant Horror", "Tarantula Broodfather", "Sven Packmaster", "Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend"};
      int[] maxTiers = new int[]{5, 5, 4, 4, 4, 5};
      ProfileFetcher.SlayerInfo[] infos = new ProfileFetcher.SlayerInfo[]{this.data.zombieSlayerInfo, this.data.spiderSlayerInfo, this.data.wolfSlayerInfo, this.data.endermanSlayerInfo, this.data.blazeSlayerInfo, this.data.vampireSlayerInfo};
      int sy = infoY + 20;

      for(int i = 0; i < slayerNames.length; ++i) {
         String name = slayerNames[i];
         int maxTier = maxTiers[i];
         ProfileFetcher.SlayerInfo info = infos[i];
         int rowY = sy + i * 16;
         boolean hovered = mouseX >= rightX + 5 && mouseX <= rightX + 255 && mouseY >= rowY && mouseY <= rowY + 15;
         if (hovered) {
            graphics.fill(rightX + 5, rowY, rightX + 255, rowY + 15, 1090519039);
         }

         String label = "§e" + name + ": §aLvl " + info.level + " §7(" + String.format("%,.0f", info.xp) + " XP)";
         graphics.text(font, label, rightX + 10, rowY + 3, -1, true);
         if (hovered) {
            List<Component> tooltip = new ArrayList();
            tooltip.add(Component.literal("§a" + name));
            Object[] var10002 = new Object[]{info.xp};
            tooltip.add(Component.literal("§7Total XP: §e" + String.format("%,.0f", var10002)));
            tooltip.add(Component.literal("§7Level: §e" + info.level));
            tooltip.add(Component.literal(""));
            tooltip.add(Component.literal("§dBoss Kills:"));
            boolean hasKills = false;

            for(int t = 1; t <= maxTier; ++t) {
               int k = (Integer)info.kills.getOrDefault(t, 0);
               if (k > 0) {
                  hasKills = true;
               }

               tooltip.add(Component.literal(" §8Tier " + t + ": §a" + String.format("%,d", k)));
            }

            if (!hasKills) {
               tooltip.add(Component.literal(" §cNo kills recorded"));
            }

            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
         }
      }

      int dungY = infoY + 130;
      graphics.fill(rightX, dungY, rightX + 260, dungY + 35, Integer.MIN_VALUE);
      graphics.text(font, "§d§lDungeons", rightX + 100, dungY + 5, -1, true);
      graphics.text(font, "Catacombs Level: §a" + this.data.catacombs + " §7(See Dungeons Tab for details)", rightX + 10, dungY + 20, -1, true);
   }

   private void drawSegmentedProgressBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float progress, int color) {
      int totalSegments = 10;
      int gap = 1;
      int segWidth = (width - (totalSegments - 1) * gap) / totalSegments;
      int filledSegments = Math.round(progress * (float)totalSegments);

      for(int i = 0; i < totalSegments; ++i) {
         int sx = x + i * (segWidth + gap);
         int c = i < filledSegments ? color : -13421773;
         graphics.fill(sx, y, sx + segWidth, y + height, c);
      }

   }

   private LevelProgress getCataLevelAndProgress(double xp) {
      if (xp <= (double)0.0F) {
         return new LevelProgress(0, 0.0F);
      } else {
         for(int i = 1; i < CATA_XP_TABLE.length; ++i) {
            if (xp < CATA_XP_TABLE[i]) {
               double prev = CATA_XP_TABLE[i - 1];
               float prog = (float)((xp - prev) / (CATA_XP_TABLE[i] - prev));
               return new LevelProgress(i - 1, Math.max(0.0F, Math.min(1.0F, prog)));
            }
         }

         double overflowXp = xp - CATA_XP_TABLE[CATA_XP_TABLE.length - 1];
         double overflowPerLevel = (double)2.0E8F;
         int extraLevels = (int)(overflowXp / overflowPerLevel);
         float prog = (float)(overflowXp % overflowPerLevel / overflowPerLevel);
         return new LevelProgress(50 + extraLevels, Math.max(0.0F, Math.min(1.0F, prog)));
      }
   }

   private void drawMiningTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      this.drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
      int gx = px + 35;
      int gy = py + 25;
      if (this.currentSubTab != null && "HOTM".equals(this.currentSubTab.id)) {
         graphics.fill(gx, gy, gx + 230, gy + 200, -870442202);
         graphics.fill(gx, gy, gx + 230, gy + 1, -9745525);
         graphics.fill(gx, gy, gx + 1, gy + 200, -9745525);
         graphics.fill(gx + 229, gy, gx + 230, gy + 200, -9745525);
         graphics.fill(gx, gy + 199, gx + 230, gy + 200, -9745525);
         graphics.text(font, "§d§lHotM Skill Tree", gx + 60, gy + 6, -1, true);
         int gridStartX = gx + 22;
         int gridStartY = gy + 22;
         Map<String, Integer> sampleLevels = new HashMap();
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
         HotMNodeInfo[] nodes = new HotMNodeInfo[]{new HotMNodeInfo("dead_mans_chest", "Dead Man's Chest", "Increases Mineshaft chest rewards.", 50, 0, 0, false), new HotMNodeInfo("gem_lover", "Gem Lover", "Increases Gemstone Fortune.", 20, 0, 1, false), new HotMNodeInfo("mining_speed_2", "Mining Speed 2", "Grants +400 Mining Speed.", 100, 0, 2, false), new HotMNodeInfo("mining_fortune_2", "Mining Fortune 2", "Grants +100 Mining Fortune.", 50, 0, 3, false), new HotMNodeInfo("glacite_powder", "Glacite Powder", "Increases Glacite Powder gain.", 100, 0, 4, false), new HotMNodeInfo("eager_adventurer", "Eager Adventurer", "Increases stats inside Mineshafts.", 50, 0, 5, false), new HotMNodeInfo("gifts_from_the_departed", "Gifts from the Departed", "Extra loot from corpses.", 50, 0, 6, false), new HotMNodeInfo("metal_head", "Metal Head", "Increases Defense while mining.", 20, 1, 1, false), new HotMNodeInfo("mineshaft_mayhem", "Mineshaft Mayhem", "Ability: Gives random buff in Mineshaft.", 1, 1, 2, true), new HotMNodeInfo("titanium_insanium", "Titanium Insanium", "+50% Titanium ore spawn rate.", 50, 1, 3, false), new HotMNodeInfo("tunnel_vision", "Tunnel Vision", "Increases Mining Speed in Mineshafts.", 1, 1, 4, false), new HotMNodeInfo("blockhead", "Blockhead", "Increases Block Fortune.", 20, 1, 5, false), new HotMNodeInfo("miners_blessing", "Miner's Blessing", "Increases Mining Speed on all islands.", 1, 2, 0, false), new HotMNodeInfo("keep_it_cool", "Keep It Cool", "Reduces Heat accumulation.", 50, 2, 1, false), new HotMNodeInfo("gemstone_infusion", "Gemstone Infusion", "Ability: Temporarily boosts Gemstone stats.", 1, 2, 2, true), new HotMNodeInfo("gift_of_the_trees", "Gift of the Trees", "Increases Foraging & Mining fortune.", 1, 2, 3, false), new HotMNodeInfo("sheer_force", "Sheer Force", "Ability: Grants +200% Mining Spread for 20s.", 1, 2, 4, true), new HotMNodeInfo("rags_to_riches", "Rags to Riches", "Increases stats when low on purse.", 50, 2, 5, false), new HotMNodeInfo("surveyor", "Surveyor", "Increases chance to find Mineshafts.", 20, 2, 6, false), new HotMNodeInfo("front_loaded", "Front Loaded", "+250% Speed for first 2500 ores.", 1, 3, 1, false), new HotMNodeInfo("subterranean_fisher", "Subterranean Fisher", "+15 Sea Creature Chance in mines.", 10, 3, 5, false), new HotMNodeInfo("maniac_miner", "Maniac Miner", "Ability: Grants massive speed boost.", 1, 4, 1, true), new HotMNodeInfo("powder_buff", "Powder Buff", "+50% Powder from all sources.", 50, 4, 3, false), new HotMNodeInfo("pickaxe_toss", "Pickaxe Toss", "Ability: Toss pickaxe to break ores.", 1, 4, 5, true), new HotMNodeInfo("goblin_cleaner", "Goblin Cleaner", "+20% Goblin Ores.", 10, 5, 2, false), new HotMNodeInfo("core_of_the_mountain", "Heart of the Mountain", "Unlocks HOTM perks and token slots.", 10, 5, 3, false), new HotMNodeInfo("star_powder", "Star Powder", "+50 Star Powder.", 20, 5, 4, false), new HotMNodeInfo("orbital_strike", "Orbital Strike", "Ability: Strike ores from orbit.", 1, 6, 0, true), new HotMNodeInfo("luck_of_the_cave", "Luck of the Cave", "+45% Powder & Chest chance.", 45, 6, 1, false), new HotMNodeInfo("crystalline", "Crystalline", "+15% Gemstone Powder.", 50, 6, 2, false), new HotMNodeInfo("mining_madness", "Mining Madness", "+50 Speed & Fortune.", 1, 6, 3, false), new HotMNodeInfo("mining_speed_boost", "Mining Speed Boost", "Ability: Grants +200% Mining Speed for 20s.", 1, 6, 4, true), new HotMNodeInfo("precision_mining", "Precision Mining", "Increases mining speed on particles.", 1, 6, 5, false), new HotMNodeInfo("efficient_miner", "Efficient Miner", "Chance to mine adjacent ores.", 100, 7, 1, false), new HotMNodeInfo("mining_experience", "Seasoned Miner", "Grants +50 Mining XP.", 100, 7, 2, false), new HotMNodeInfo("mining_speed", "Mining Speed", "Grants +500 Mining Speed.", 50, 7, 3, false), new HotMNodeInfo("mining_fortune", "Mining Fortune", "Grants +100 Mining Fortune.", 50, 7, 4, false), new HotMNodeInfo("forge_time", "Quick Forge", "Decreases the time it takes to forge by 30%.", 20, 7, 5, false), new HotMNodeInfo("daily_effect", "Sky Mall", "Grants a random mining buff every day.", 1, 8, 1, false), new HotMNodeInfo("lonesome_miner", "Lonesome Miner", "+150 Mining Stats in mines.", 45, 8, 3, false), new HotMNodeInfo("great_explorer", "Great Explorer", "+20% Treasure Chest chance.", 20, 8, 5, false)};

         for(int r = 0; r < 8; ++r) {
            for(int c = 0; c < 7; ++c) {
               int nx = gridStartX + c * 26;
               int ny = gridStartY + r * 21;
               graphics.fill(nx - 1, ny - 1, nx + 17, ny + 17, 1073741824);
            }
         }

         for(HotMNodeInfo node : nodes) {
            int level = (Integer)this.data.hotmNodes.getOrDefault(node.id, (Integer)sampleLevels.getOrDefault(node.id, 0));
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
            if (level > 1) {
               String countStr = String.valueOf(level);
               graphics.text(font, "§f" + countStr, nx + 17 - font.width(countStr), ny + 9, -1, true);
            }

            if (mouseX >= nx && mouseX <= nx + 16 && mouseY >= ny && mouseY <= ny + 16) {
               List<Component> tooltip = new ArrayList();
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
      } else if (this.currentSubTab != null && "GLACITE".equals(this.currentSubTab.id)) {
         int cardW = 125;
         int cardH = 130;
         graphics.fill(gx, gy, gx + cardW, gy + cardH, -870442202);
         graphics.fill(gx + 1, gy + 1, gx + cardW - 1, gy + 18, -2143670964);
         graphics.item(new ItemStack(Items.WRITABLE_BOOK), gx + 3, gy + 1);
         graphics.text(font, "§dInfo", gx + 22, gy + 5, -1, true);
         int iy = gy + 22;
         graphics.text(font, "§7Mineshaft Entered: §b789", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Frozen Skin: §a5/5", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Prehistorian: §a10/10", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Resourceful: §c0/5", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Dwarven Exp.: §a4/10", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Chilled To Bone: §a10/10", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Cut Loose: §c0/5", gx + 6, iy, -1, true);
         iy += 12;
         graphics.text(font, "§7Sleight Of Hand: §a1/1", gx + 6, iy, -1, true);
         int card2X = gx + cardW + 8;
         graphics.fill(card2X, gy, card2X + cardW + 15, gy + 90, -870442202);
         graphics.fill(card2X + 1, gy + 1, card2X + cardW + 14, gy + 18, -2143670964);
         graphics.text(font, "§dCorpses Looted", card2X + 20, gy + 5, -1, true);
         int cy = gy + 22;
         graphics.text(font, "§7Lapis Corpses: §b1,041", card2X + 6, cy, -1, true);
         cy += 13;
         graphics.text(font, "§7Tungsten Corpses: §f151", card2X + 6, cy, -1, true);
         cy += 13;
         graphics.text(font, "§7Umber Corpses: §6172", card2X + 6, cy, -1, true);
         cy += 13;
         graphics.text(font, "§7Vanguard Corpses: §b54", card2X + 6, cy, -1, true);
         cy += 13;
         graphics.text(font, "§7Corpse Milestone: §a7/7", card2X + 6, cy, -1, true);
         int card3Y = gy + cardH + 8;
         graphics.fill(gx, card3Y, gx + 150, card3Y + 60, -870442202);
         graphics.fill(gx + 1, card3Y + 1, gx + 149, card3Y + 18, -2143670964);
         graphics.text(font, "§dFossils", gx + 50, card3Y + 5, -1, true);

         for(int i = 0; i < 8; ++i) {
            int fx = gx + 10 + i % 4 * 32;
            int fy = card3Y + 22 + i / 4 * 18;
            graphics.item(new ItemStack(Items.BONE), fx, fy);
         }
      } else {
         int card1W = 140;
         int card1H = 145;
         graphics.fill(gx, gy, gx + card1W, gy + card1H, -870442202);
         graphics.fill(gx + 1, gy + 1, gx + card1W - 1, gy + 18, -2143670964);
         graphics.item(new ItemStack(Items.WRITABLE_BOOK), gx + 3, gy + 1);
         graphics.text(font, "§dInformation", gx + 22, gy + 5, -1, true);
         int iy = gy + 22;
         graphics.text(font, "§7HotM: §f10", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Total Runs: §f51", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Rock Pet: §6Legendary", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Fungus Fortuna: §a10/10", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Harena Fortuna: §a10/10", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Treasure Earth: §a5/5", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Dwarven Train.: §a3/3", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Eager Miner: §a10/10", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Rhinestone: §a10/10", gx + 6, iy, -1, true);
         iy += 11;
         graphics.text(font, "§7Return Sender: §a10/10", gx + 6, iy, -1, true);
         int card2X = gx + card1W + 8;
         int card2W = 120;
         graphics.fill(card2X, gy, card2X + card2W, gy + 85, -870442202);
         graphics.fill(card2X + 1, gy + 1, card2X + card2W - 1, gy + 18, -2143670964);
         graphics.text(font, "§dPowder", card2X + 35, gy + 5, -1, true);
         int powY = gy + 22;
         graphics.text(font, "§7          Current Total", card2X + 4, powY, -1, true);
         powY += 12;
         graphics.text(font, "§aMithril   §f8.9M   17.5M", card2X + 4, powY, -1, true);
         powY += 12;
         graphics.text(font, "§dGemstone  §f13M    16.7M", card2X + 4, powY, -1, true);
         powY += 12;
         graphics.text(font, "§bGlacite   §f32.5M  45.2M", card2X + 4, powY, -1, true);
         int card3X = card2X;
         int card3Y = gy + 90;
         graphics.fill(card2X, card3Y, card2X + 175, card3Y + 80, -870442202);
         graphics.fill(card2X + 1, card3Y + 1, card2X + 174, card3Y + 18, -2143670964);
         graphics.text(font, "§dCrystals", card2X + 60, card3Y + 5, -1, true);
         Item[] crystals = new Item[]{Items.LIME_DYE, Items.PURPLE_DYE, Items.YELLOW_DYE, Items.BLUE_DYE, Items.ORANGE_DYE, Items.RED_DYE, Items.MAGENTA_DYE, Items.QUARTZ, Items.CYAN_DYE, Items.BROWN_DYE, Items.GREEN_DYE, Items.BLACK_DYE};
         boolean[] unlocked = new boolean[]{false, true, false, false, false, true, false, true, false, true, false, false};

         for(int i = 0; i < 12; ++i) {
            int cx = card3X + 10 + i % 6 * 26;
            int cy2 = card3Y + 24 + i / 6 * 24;
            graphics.item(new ItemStack(crystals[i]), cx, cy2);
            String mark = unlocked[i] ? "§a✔" : "§c✖";
            graphics.text(font, mark, cx + 14, cy2 + 8, -1, true);
         }
      }

   }

   private void drawCollectionsTab(GuiGraphicsExtractor graphics, Font font, int px, int py) {
      int rightX = px + 20;
      int infoY = py + 20;
      graphics.fill(rightX, infoY, rightX + 220, infoY + 60, Integer.MIN_VALUE);
      graphics.text(font, "§d§lCollections", rightX + 70, infoY + 5, -1, true);
      graphics.text(font, "Total Collections: §a" + this.data.totalCollections, rightX + 10, infoY + 25, -1, true);
   }

   private void drawForagingTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      this.drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
      int gx = px + 35;
      int gy = py + 25;
      if (this.currentSubTab != null && "TREE".equals(this.currentSubTab.id)) {
         graphics.fill(gx, gy, gx + 230, gy + 200, -870442202);
         graphics.fill(gx, gy, gx + 230, gy + 1, -9745525);
         graphics.fill(gx, gy, gx + 1, gy + 200, -9745525);
         graphics.fill(gx + 229, gy, gx + 230, gy + 200, -9745525);
         graphics.fill(gx, gy + 199, gx + 230, gy + 200, -9745525);
         graphics.text(font, "§d§lHeart of the Tree", gx + 55, gy + 6, -1, true);
         int gridStartX = gx + 22;
         int gridStartY = gy + 22;
         HotMNodeInfo[] treeNodes = new HotMNodeInfo[]{new HotMNodeInfo("tree_gift_fortune", "Gift Fortune", "Increases Fortune from tree gifts.", 50, 1, 2, false), new HotMNodeInfo("tree_fortune", "Foraging Fortune", "Increases Foraging Fortune.", 50, 1, 3, false), new HotMNodeInfo("tree_whisperer_2", "Whisperer II", "Increases Forest Whispers gain further.", 50, 1, 4, false), new HotMNodeInfo("tree_speed", "Foraging Speed", "Increases Foraging Speed.", 50, 2, 2, false), new HotMNodeInfo("tree_core", "Heart of the Tree", "Core of the tree.", 10, 2, 3, false), new HotMNodeInfo("tree_extra_logs", "Log Sweeper", "Increases log drop rate.", 50, 2, 4, false), new HotMNodeInfo("tree_whisperer", "Forest Whisperer", "Increases Forest Whispers gain.", 20, 3, 2, false), new HotMNodeInfo("tree_gift_efficiency", "Gift Efficiency", "Reduces tree gift cooldowns.", 20, 3, 3, false), new HotMNodeInfo("tree_woodcutter", "Master Woodcutter", "Chance to chop entire trees.", 50, 3, 4, false), new HotMNodeInfo("tree_exp", "Foraging XP Boost", "Increases Foraging XP.", 50, 4, 3, false)};

         for(int r = 0; r < 8; ++r) {
            for(int c = 0; c < 7; ++c) {
               int nx = gridStartX + c * 26;
               int ny = gridStartY + r * 21;
               graphics.fill(nx - 1, ny - 1, nx + 17, ny + 17, 1073741824);
            }
         }

         for(HotMNodeInfo node : treeNodes) {
            int level = (Integer)this.data.hotmNodes.getOrDefault(node.id, node.maxLevel);
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
               graphics.text(font, "§f" + countStr, nx + 17 - font.width(countStr), ny + 9, -1, true);
            }

            if (mouseX >= nx && mouseX <= nx + 16 && mouseY >= ny && mouseY <= ny + 16) {
               List<Component> tooltip = new ArrayList();
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
         int card1W = 160;
         int card1H = 110;
         graphics.fill(gx, gy, gx + card1W, gy + card1H, -870442202);
         graphics.fill(gx + 1, gy + 1, gx + card1W - 1, gy + 18, -2143670964);
         graphics.item(new ItemStack(Items.WRITABLE_BOOK), gx + 3, gy + 1);
         graphics.text(font, "§dInformation", gx + 22, gy + 5, -1, true);
         int iy = gy + 24;
         Object var10002 = this.data.foragingMangrove > 0 ? this.data.foragingMangrove : "5";
         graphics.text(font, "§7Mangrove Gifts: §c" + String.valueOf(var10002) + "/7", gx + 6, iy, -1, true);
         iy += 14;
         String var45 = this.data.foragingMangrove > 0 ? String.format("%,d", this.data.foragingMangrove * 270) : "1,899";
         graphics.text(font, "§7Total Mangrove Gifts: §f" + var45, gx + 6, iy, -1, true);
         iy += 14;
         Object var46 = this.data.foragingFig > 0 ? this.data.foragingFig : "6";
         graphics.text(font, "§7Fig Gifts: §c" + String.valueOf(var46) + "/7", gx + 6, iy, -1, true);
         iy += 14;
         String var47 = this.data.foragingFig > 0 ? String.format("%,d", this.data.foragingFig * 630) : "3,829";
         graphics.text(font, "§7Total Fig Gifts: §f" + var47, gx + 6, iy, -1, true);
         iy += 14;
         graphics.text(font, "§7Forest Whispers (Curr/Total)", gx + 6, iy, -1, true);
         iy += 12;
         int currW = this.data.foragingWhispers > 0 ? this.data.foragingWhispers : 4649921;
         int totalW = this.data.foragingSpentWhispers > 0 ? this.data.foragingWhispers + this.data.foragingSpentWhispers : 9294580;
         graphics.text(font, "§b" + String.format("%,d", currW) + "§7/§b" + String.format("%,d", totalW), gx + 6, iy, -1, true);
         int card2X = gx + card1W + 10;
         int card2W = 145;
         graphics.fill(card2X, gy, card2X + card2W, gy + 65, -870442202);
         graphics.fill(card2X + 1, gy + 1, card2X + card2W - 1, gy + 18, -2143670964);
         graphics.text(font, "§dFig", card2X + 60, gy + 5, -1, true);
         int fy = gy + 22;
         graphics.text(font, "§7Fig Personal Bests: §aYes", card2X + 6, fy, -1, true);
         fy += 12;
         graphics.text(font, "§7Fig Best: §a100,000/100,000", card2X + 6, fy, -1, true);
         fy += 12;
         graphics.text(font, "§7Fig Fortune Level: §c32/50", card2X + 6, fy, -1, true);
         int card3Y = gy + 72;
         graphics.fill(card2X, card3Y, card2X + card2W, card3Y + 65, -870442202);
         graphics.fill(card2X + 1, card3Y + 1, card2X + card2W - 1, card3Y + 18, -2143670964);
         graphics.text(font, "§dMangrove", card2X + 45, card3Y + 5, -1, true);
         int my = card3Y + 22;
         graphics.text(font, "§7Mangrove P. Bests: §aYes", card2X + 6, my, -1, true);
         my += 12;
         graphics.text(font, "§7Mangrove Best: §a100k/100k", card2X + 6, my, -1, true);
         my += 12;
         graphics.text(font, "§7Mangrove Fortune: §c25/50", card2X + 6, my, -1, true);
      }

   }

   private void drawDungeonsTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      int gy = py + 25;
      int cardH = 200;
      int card1X = px + 15;
      int card1W = 120;
      graphics.fill(card1X, gy, card1X + card1W, gy + cardH, -870442202);
      graphics.fill(card1X, gy, card1X + card1W, gy + 1, -9745525);
      graphics.fill(card1X, gy, card1X + 1, gy + cardH, -9745525);
      graphics.fill(card1X + card1W - 1, gy, card1X + card1W, gy + cardH, -9745525);
      graphics.fill(card1X, gy + cardH - 1, card1X + card1W, gy + cardH, -9745525);
      graphics.fill(card1X + 1, gy + 1, card1X + card1W - 1, gy + 20, -2143670964);
      graphics.item(new ItemStack(Items.WRITABLE_BOOK), card1X + 5, gy + 2);
      graphics.text(font, "§dDungeon Info", card1X + 24, gy + 6, -1, true);
      double classAvg = (double)0.0F;
      if (!this.data.classLevelMap.isEmpty()) {
         double sum = (double)0.0F;
         String[] classes = new String[]{"healer", "mage", "berserk", "archer", "tank"};

         for(String cls : classes) {
            double xp = (Double)this.data.classXpMap.getOrDefault(cls, (double)0.0F);
            LevelProgress lp = this.getCataLevelAndProgress(xp);
            sum += (double)Math.min(lp.level, 50);
         }

         classAvg = sum / (double)5.0F;
      }

      long totalCataComps = 0L;

      for(int c : this.data.normalFloorCompletions.values()) {
         totalCataComps += (long)c;
      }

      long totalMasterComps = 0L;

      for(int c : this.data.masterFloorCompletions.values()) {
         totalMasterComps += (long)c;
      }

      long totalRuns = Math.max(1L, totalCataComps + totalMasterComps);
      double secretsPerRun = (double)this.data.totalSecrets / (double)totalRuns;
      int contentY = gy + 32;
      graphics.text(font, "§7Class Average: §f" + String.format("%.2f", classAvg), card1X + 10, contentY, -1, true);
      contentY += 16;
      graphics.text(font, "§7Secrets: §f" + String.format("%,d", this.data.totalSecrets), card1X + 10, contentY, -1, true);
      contentY += 16;
      graphics.text(font, "§7Secrets/Run: §f" + String.format("%.2f", secretsPerRun), card1X + 10, contentY, -1, true);
      int card2X = card1X + card1W + 10;
      int card2W = 145;
      graphics.fill(card2X, gy, card2X + card2W, gy + cardH, -870442202);
      graphics.fill(card2X, gy, card2X + card2W, gy + 1, -9745525);
      graphics.fill(card2X, gy, card2X + 1, gy + cardH, -9745525);
      graphics.fill(card2X + card2W - 1, gy, card2X + card2W, gy + cardH, -9745525);
      graphics.fill(card2X, gy + cardH - 1, card2X + card2W, gy + cardH, -9745525);
      graphics.fill(card2X + 1, gy + 1, card2X + card2W - 1, gy + 20, -2143670964);
      graphics.text(font, "§dDungeon Levels", card2X + 25, gy + 6, -1, true);
      int levelY = gy + 28;
      LevelProgress cataLp = this.getCataLevelAndProgress(this.data.catacombsXp);
      boolean cataHovered = mouseX >= card2X + 5 && mouseX <= card2X + card2W - 5 && mouseY >= levelY && mouseY <= levelY + 22;
      String cataColor = cataLp.level >= 50 ? "§6" : "§7";
      graphics.text(font, cataColor + "Catacombs: " + cataLp.level, card2X + 10, levelY, -1, true);
      this.drawSegmentedProgressBar(graphics, card2X + 10, levelY + 11, 125, 6, cataLp.progress, cataLp.level >= 50 ? -22016 : -11141291);
      if (cataHovered) {
         List<Component> tooltip = new ArrayList();
         tooltip.add(Component.literal("§eCatacombs"));
         Object[] var10002 = new Object[]{this.data.catacombsXp};
         tooltip.add(Component.literal("§7Total XP: §f" + String.format("%,.0f", var10002)));
         var10002 = new Object[]{cataLp.progress * 100.0F};
         tooltip.add(Component.literal("§7Progress: §f" + String.format("%.0f%%", var10002)));
         graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
      }

      levelY += 26;
      String[] classes = new String[]{"healer", "mage", "berserk", "archer", "tank"};

      for(String cls : classes) {
         double xp = (Double)this.data.classXpMap.getOrDefault(cls, (double)0.0F);
         LevelProgress lp = this.getCataLevelAndProgress(xp);
         boolean isSelected = cls.equalsIgnoreCase(this.data.selectedDungeonClass);
         boolean rowHovered = mouseX >= card2X + 5 && mouseX <= card2X + card2W - 5 && mouseY >= levelY && mouseY <= levelY + 22;
         String var10000 = cls.substring(0, 1).toUpperCase();
         String cName = var10000 + cls.substring(1);
         String titleColor = isSelected ? "§a" : (lp.level >= 50 ? "§6" : "§7");
         graphics.text(font, titleColor + cName + ": " + lp.level, card2X + 10, levelY, -1, true);
         int barColor = lp.level >= 50 ? -22016 : -11141291;
         this.drawSegmentedProgressBar(graphics, card2X + 10, levelY + 11, 125, 6, lp.progress, barColor);
         if (rowHovered) {
            List<Component> tooltip = new ArrayList();
            tooltip.add(Component.literal("§e" + cName));
            Object[] var67 = new Object[]{xp};
            tooltip.add(Component.literal("§7Total XP: §f" + String.format("%,.0f", var67)));
            var67 = new Object[]{lp.progress * 100.0F};
            tooltip.add(Component.literal("§7Progress: §f" + String.format("%.0f%%", var67)));
            if (lp.level >= 50) {
               tooltip.add(Component.literal("§6Maxed!"));
            }

            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
         }

         levelY += 26;
      }

      int card3X = card2X + card2W + 10;
      int card3W = 155;
      graphics.fill(card3X, gy, card3X + card3W, gy + cardH, -870442202);
      graphics.fill(card3X, gy, card3X + card3W, gy + 1, -9745525);
      graphics.fill(card3X, gy, card3X + 1, gy + cardH, -9745525);
      graphics.fill(card3X + card3W - 1, gy, card3X + card3W, gy + cardH, -9745525);
      graphics.fill(card3X, gy + cardH - 1, card3X + card3W, gy + cardH, -9745525);
      graphics.fill(card3X + 1, gy + 1, card3X + card3W - 1, gy + 20, -2143670964);
      graphics.text(font, "§dDungeon Runs", card3X + 35, gy + 6, -1, true);
      int tableY = gy + 28;
      graphics.text(font, "§7Cata", card3X + 70, tableY, -1, true);
      graphics.text(font, "§7Master", card3X + 110, tableY, -1, true);
      tableY += 16;
      String[] bossNames = new String[]{"Bonzo", "Scarf", "Prof.", "Thorn", "Livid", "Sadan", "Necron"};

      for(int f = 1; f <= 7; ++f) {
         String bName = bossNames[f - 1];
         int cComps = (Integer)this.data.normalFloorCompletions.getOrDefault(f, 0);
         int mComps = (Integer)this.data.masterFloorCompletions.getOrDefault(f, 0);
         boolean rowHovered = mouseX >= card3X + 5 && mouseX <= card3X + card3W - 5 && mouseY >= tableY && mouseY <= tableY + 14;
         if (rowHovered) {
            graphics.fill(card3X + 5, tableY, card3X + card3W - 5, tableY + 14, 1090519039);
         }

         graphics.text(font, "§7" + bName, card3X + 10, tableY + 2, -1, true);
         graphics.text(font, "§f" + cComps, card3X + 75, tableY + 2, -1, true);
         graphics.text(font, "§f" + mComps, card3X + 115, tableY + 2, -1, true);
         if (rowHovered) {
            List<Component> tooltip = new ArrayList();
            tooltip.add(Component.literal("§eFloor " + f + " (" + bName + ")"));
            tooltip.add(Component.literal("§7Normal Completions: §f" + cComps));
            tooltip.add(Component.literal("§7Master Completions: §f" + mComps));
            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), mouseX, mouseY);
         }

         tableY += 16;
      }

      graphics.fill(card3X + 5, tableY, card3X + card3W - 5, tableY + 1, -2130706433);
      tableY += 3;
      graphics.text(font, "§7Total", card3X + 10, tableY, -1, true);
      graphics.text(font, "§f" + totalCataComps, card3X + 75, tableY, -1, true);
      graphics.text(font, "§f" + totalMasterComps, card3X + 115, tableY, -1, true);
   }

   private void drawFarmingTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      this.drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
      int rightX = px + 40;
      int infoY = py + 20;
      graphics.fill(rightX, infoY, rightX + 160, infoY + 40, Integer.MIN_VALUE);
      graphics.text(font, "§dFarming", rightX + 65, infoY + 5, -1, true);
      graphics.text(font, "Farming Level: " + this.data.farming, rightX + 5, infoY + 25, -1, true);
   }

   private void drawFishingTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      if (this.currentSubTab != null && "FISHING_BAG".equals(this.currentSubTab.id)) {
         this.drawGenericTab(graphics, font, px, py, mouseX, mouseY);
      } else {
         this.drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
         int rightX = px + 40;
         int infoY = py + 20;
         graphics.fill(rightX, infoY, rightX + 160, infoY + 40, Integer.MIN_VALUE);
         graphics.text(font, "§dFishing", rightX + 65, infoY + 5, -1, true);
         graphics.text(font, "Fishing Level: " + this.data.fishing, rightX + 5, infoY + 25, -1, true);
      }
   }

   private void drawGenericSidebar(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      List<SubTab> tabs = (List)this.subTabsMap.get(this.currentTab);
      if (tabs != null) {
         int subTabX = px + 10;
         int subTabY = py + 10;

         for(int i = 0; i < tabs.size(); ++i) {
            SubTab sub = (SubTab)tabs.get(i);
            boolean selected = this.currentSubTab != null && this.currentSubTab.id.equals(sub.id);
            int ty = subTabY + i * 22;
            if (selected) {
               graphics.fill(subTabX, ty, subTabX + 20, ty + 1, -1);
               graphics.fill(subTabX, ty, subTabX + 1, ty + 20, -1);
               graphics.fill(subTabX + 19, ty, subTabX + 20, ty + 20, -1);
               graphics.fill(subTabX, ty + 19, subTabX + 20, ty + 20, -1);
            }

            graphics.item(sub.icon, subTabX + 2, ty + 2);
            if (mouseX >= subTabX && mouseX <= subTabX + 20 && mouseY >= ty && mouseY <= ty + 20) {
               graphics.setTooltipForNextFrame(font, List.of(Component.literal(sub.name)), Optional.empty(), mouseX, mouseY);
            }
         }

      }
   }

   private void drawGenericTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      this.drawGenericSidebar(graphics, font, px, py, mouseX, mouseY);
      int aeX = px + 40;
      int aeY = py + 40;
      ItemStack hoveredStack = null;
      int hx = 0;
      int hy = 0;

      for(int i = 0; i < 4; ++i) {
         int ay = aeY + i * 18;
         graphics.fill(aeX, ay, aeX + 18, ay + 18, -2146365167);
         ItemStack armorItem = this.data.armor.size() > i ? (ItemStack)this.data.armor.get(3 - i) : null;
         if (armorItem != null && !armorItem.isEmpty()) {
            graphics.item(armorItem, aeX + 1, ay + 1);
            graphics.itemDecorations(font, armorItem, aeX + 1, ay + 1);
            if (mouseX >= aeX && mouseX <= aeX + 18 && mouseY >= ay && mouseY <= ay + 18) {
               graphics.fill(aeX + 1, ay + 1, aeX + 17, ay + 17, -2130706433);
               hoveredStack = armorItem;
               hx = aeX;
               hy = ay;
            }
         }

         int ex = aeX + 18;
         int ey = aeY + i * 18;
         graphics.fill(ex, ey, ex + 18, ey + 18, -2146365167);
         ItemStack equipItem = this.data.equipment.size() > i ? (ItemStack)this.data.equipment.get(i) : null;
         if (equipItem != null && !equipItem.isEmpty()) {
            graphics.item(equipItem, ex + 1, ey + 1);
            graphics.itemDecorations(font, equipItem, ex + 1, ey + 1);
            if (mouseX >= ex && mouseX <= ex + 18 && mouseY >= ey && mouseY <= ey + 18) {
               graphics.fill(ex + 1, ey + 1, ex + 17, ey + 17, -2130706433);
               hoveredStack = equipItem;
               hx = ex;
               hy = ey;
            }
         }
      }

      int gx = px + 100;
      int gy = py + 40;
      List<ItemStack> items = this.getActiveItems();
      int cols = 9;
      int rows = 6;
      int maxPages = this.getMaxInnerPages();
      if (maxPages > 1) {
         int bw = 16;

         for(int i = 0; i < maxPages; ++i) {
            int bx = gx + i * (bw + 2);
            int by = gy - 20;
            int bgColor = this.innerPage == i ? -1440126423 : -2145246686;
            graphics.fill(bx, by, bx + bw, by + 14, bgColor);
            String label = String.valueOf(i + 1);
            if (this.currentSubTab != null && "BACKPACKS".equals(this.currentSubTab.id)) {
               Object[] keys = this.data.backpacks.keySet().toArray();
               if (i < keys.length) {
                  label = String.valueOf(keys[i]);
               }
            }

            graphics.text(font, label, bx + bw / 2 - font.width(label) / 2, by + 3, -1, true);
         }
      }

      int idx = 0;

      for(int r = 0; r < rows; ++r) {
         for(int c = 0; c < cols; ++c) {
            int ix = gx + c * 18;
            int iy = gy + r * 18;
            graphics.fill(ix, iy, ix + 18, iy + 18, -2146365167);
            if (idx < items.size()) {
               ItemStack stack = (ItemStack)items.get(idx);
               if (stack != null && !stack.isEmpty()) {
                  graphics.item(stack, ix + 1, iy + 1);
                  graphics.itemDecorations(font, stack, ix + 1, iy + 1);
                  if (mouseX >= ix && mouseX <= ix + 18 && mouseY >= iy && mouseY <= iy + 18) {
                     graphics.fill(ix + 1, iy + 1, ix + 17, iy + 17, -2130706433);
                     hoveredStack = stack;
                     hx = ix;
                     hy = iy;
                  }
               }
            }

            ++idx;
         }
      }

      if (hoveredStack != null && !hoveredStack.isEmpty()) {
         List<Component> tooltip = new ArrayList();
         tooltip.add(hoveredStack.getHoverName());
         ItemLore lore = (ItemLore)hoveredStack.get(DataComponents.LORE);
         if (lore != null) {
            tooltip.addAll(lore.lines());
         }

         graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), hx, hy);
      }

   }

   private void drawPetsTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      int sx = px + 20;
      int sy = py + 20;
      int maxVisible = 12;

      for(int i = 0; i < maxVisible; ++i) {
         int petIdx = this.scrollOffset + i;
         if (petIdx >= this.data.pets.size()) {
            break;
         }

         ProfileFetcher.Pet pet = (ProfileFetcher.Pet)this.data.pets.get(petIdx);
         int col = i / 6;
         int row = i % 6;
         int x = sx + col * 210;
         int y = sy + row * 38;
         int bgColor = pet.active ? -1440126423 : Integer.MIN_VALUE;
         graphics.fill(x, y, x + 200, y + 34, bgColor);
         graphics.fill(x, y, x + 200, y + 1, -15658735);
         graphics.fill(x, y, x + 1, y + 34, -15658735);
         graphics.fill(x + 199, y, x + 200, y + 34, -15658735);
         graphics.fill(x, y + 33, x + 200, y + 34, -15658735);
         ItemStack icon = new ItemStack(Items.BONE);
         if (pet.type.contains("DRAGON")) {
            icon = new ItemStack(Items.DRAGON_HEAD);
         } else if (pet.type.contains("ZOMBIE")) {
            icon = new ItemStack(Items.ZOMBIE_HEAD);
         } else if (pet.type.contains("CREEPER")) {
            icon = new ItemStack(Items.CREEPER_HEAD);
         } else if (pet.type.contains("SKELETON")) {
            icon = new ItemStack(Items.SKELETON_SKULL);
         }

         graphics.item(icon, x + 4, y + 8);
         String color = pet.tier.equals("LEGENDARY") ? "§6" : (pet.tier.equals("MYTHIC") ? "§d" : (pet.tier.equals("EPIC") ? "§5" : (pet.tier.equals("RARE") ? "§9" : (pet.tier.equals("UNCOMMON") ? "§a" : "§f"))));
         graphics.text(font, color + pet.tier + " " + pet.type, x + 30, y + 6, -1, true);
         int barWidth = 150;
         int barX = x + 30;
         int barY = y + 20;
         graphics.fill(barX, barY, barX + barWidth, barY + 6, -14540254);
         graphics.fill(barX, barY, barX + (int)((double)barWidth * 0.7), barY + 6, -16733696);
         graphics.text(font, String.format("%,.0f EXP", pet.exp), barX + 2, barY - 1, -1, true);
      }

   }

   private int getMaxInnerPages() {
      if (this.currentSubTab == null) {
         return 1;
      } else if ("WARDROBE".equals(this.currentSubTab.id)) {
         return (this.data.wardrobe.size() + 53) / 54;
      } else if ("ACCESSORIES".equals(this.currentSubTab.id)) {
         return (this.data.accessories.size() + 53) / 54;
      } else if ("BACKPACKS".equals(this.currentSubTab.id)) {
         return this.data.backpacks.size();
      } else {
         return "ENDER_CHEST".equals(this.currentSubTab.id) ? (this.data.enderChest.size() + 53) / 54 : 1;
      }
   }

   private List<ItemStack> getActiveItems() {
      if (this.currentSubTab == null) {
         return new ArrayList();
      } else if ("MAIN".equals(this.currentSubTab.id)) {
         return this.data.inventory;
      } else {
         List<ItemStack> src = new ArrayList();
         if ("WARDROBE".equals(this.currentSubTab.id)) {
            src = this.data.wardrobe;
         } else if ("ACCESSORIES".equals(this.currentSubTab.id)) {
            src = this.data.accessories;
         } else if ("ENDER_CHEST".equals(this.currentSubTab.id)) {
            src = this.data.enderChest;
         } else if ("MISC_BAGS".equals(this.currentSubTab.id)) {
            src = this.data.personalVault;
         } else if ("FISHING_BAG".equals(this.currentSubTab.id)) {
            src = this.data.fishingBag;
         } else if ("MUSEUM_WEAPONS".equals(this.currentSubTab.id)) {
            src = this.data.museumWeapons;
         } else if ("MUSEUM_ARMOR".equals(this.currentSubTab.id)) {
            src = this.data.museumArmor;
         } else if ("MUSEUM_RARITIES".equals(this.currentSubTab.id)) {
            src = this.data.museumRarities;
         } else if (this.currentSubTab.id.startsWith("MUSEUM_")) {
            src = new ArrayList(this.data.museumWeapons);
            src.addAll(this.data.museumArmor);
            src.addAll(this.data.museumRarities);
            src.addAll(this.data.museumSpecial);
         }

         if ("BACKPACKS".equals(this.currentSubTab.id)) {
            Object[] keys = this.data.backpacks.keySet().toArray();
            return (List<ItemStack>)(keys.length > 0 && this.innerPage < keys.length ? (List)this.data.backpacks.get((Integer)keys[this.innerPage]) : new ArrayList());
         } else {
            int start = this.innerPage * 54;
            return (List<ItemStack>)(start < src.size() ? src.subList(start, Math.min(start + 54, src.size())) : new ArrayList());
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      int panelW = 460;
      int panelH = 260;
      int px = (this.width - panelW) / 2;
      int py = (this.height - panelH) / 2;
      List<Tab> vTabs = this.getVisibleTabs();
      int topTabY = py - 22;

      for(int i = 0; i < vTabs.size(); ++i) {
         int tx = px + i * 22;
         if (event.x() >= (double)tx && event.x() <= (double)(tx + 20) && event.y() >= (double)topTabY && event.y() <= (double)(topTabY + 20)) {
            this.currentTab = (Tab)vTabs.get(i);
            this.innerPage = 0;
            this.scrollOffset = 0;
            List<SubTab> tList = (List)this.subTabsMap.get(this.currentTab);
            this.currentSubTab = tList != null && !tList.isEmpty() ? (SubTab)tList.get(0) : null;
            return true;
         }
      }

      List<SubTab> tabs = (List)this.subTabsMap.get(this.currentTab);
      if (tabs != null) {
         int subTabX = px + 10;
         int subTabY = py + 10;

         for(int i = 0; i < tabs.size(); ++i) {
            int ty = subTabY + i * 22;
            if (event.x() >= (double)subTabX && event.x() <= (double)(subTabX + 20) && event.y() >= (double)ty && event.y() <= (double)(ty + 20)) {
               this.currentSubTab = (SubTab)tabs.get(i);
               this.innerPage = 0;
               return true;
            }
         }

         int maxPages = this.getMaxInnerPages();
         if (maxPages > 1) {
            int gx = px + 100;
            int gy = py + 40;
            int bw = 16;

            for(int i = 0; i < maxPages; ++i) {
               int bx = gx + i * (bw + 2);
               int by = gy - 20;
               if (event.x() >= (double)bx && event.x() <= (double)(bx + bw) && event.y() >= (double)by && event.y() <= (double)(by + 14)) {
                  this.innerPage = i;
                  return true;
               }
            }
         }
      }

      return super.mouseClicked(event, handled);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
      if (this.currentTab != ProfileViewerScreen.Tab.PETS) {
         return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
      } else {
         if (scrollY > (double)0.0F && this.scrollOffset > 0) {
            this.scrollOffset -= 2;
         } else if (scrollY < (double)0.0F && this.scrollOffset < this.data.pets.size() - 12) {
            this.scrollOffset += 2;
         }

         return true;
      }
   }

   private void drawRiftTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      int gx = px + 20;
      int gy = py + 20;
      graphics.text(font, "§d§lInformation", gx, gy, -1, true);
      gy += 15;
      graphics.text(font, "§8Motes: §d" + String.format("%,d", this.data.riftMotes), gx, gy, -1, true);
      gy += 12;
      graphics.text(font, "§8Lifetime Motes: §d" + String.format("%,d", this.data.riftLifetimeMotes), gx, gy, -1, true);
      gy += 12;
      graphics.text(font, "§8Visits: §d" + String.format("%,d", this.data.riftVisits), gx, gy, -1, true);
      gy += 12;
      int seconds = this.data.riftSecondsSitting;
      long h = (long)(seconds / 3600);
      long m = (long)(seconds % 3600 / 60);
      long s = (long)(seconds % 60);
      String timeStr = "";
      if (h > 0L) {
         timeStr = timeStr + h + "h ";
      }

      if (m > 0L || h > 0L) {
         timeStr = timeStr + m + "m ";
      }

      timeStr = timeStr + s + "s";
      graphics.text(font, "§8Time sitting with Ävaeìkx: §5" + timeStr, gx, gy, -1, true);
      gy += 12;
      int maxSouls = 52;
      String soulColor = this.data.riftEnigmaSouls >= maxSouls ? "§5" : "§d";
      graphics.text(font, "§8Enigma Souls: " + soulColor + this.data.riftEnigmaSouls + "§5/" + maxSouls, gx, gy, -1, true);
      gy += 12;
      int maxCats = 9;
      String catColor = this.data.riftDeadCats >= maxCats ? "§5" : "§d";
      graphics.text(font, "§8Found Cats: " + catColor + this.data.riftDeadCats + "§5/" + maxCats, gx, gy, -1, true);
      gy += 12;
      int maxEyes = 8;
      String eyeColor = this.data.riftUnlockedEyes >= maxEyes ? "§5" : "§d";
      graphics.text(font, "§8Unlocked Eyes: " + eyeColor + this.data.riftUnlockedEyes + "§5/" + maxEyes, gx, gy, -1, true);
      gy += 12;
      int maxGrubber = 5;
      String grubColor = this.data.riftGrubber >= maxGrubber ? "§5" : "§d";
      graphics.text(font, "§8Grubber Stacks: " + grubColor + this.data.riftGrubber + "§5/" + maxGrubber, gx, gy, -1, true);
      gy += 12;
      int cx = px + 220;
      int cy = py + 20;
      graphics.text(font, "§d§lTimecharms", cx, cy, -1, true);
      cy += 15;
      String[] charmNames = new String[]{"Supreme", "Bacte", "Leech", "Vampire", "Bacteria", "Crux", "Porhtal", "Stability"};
      String[] charmApiIds = new String[]{"wyldly_supreme", "lazy_living", "slime", "vampiric", "citizen", "mountain", "chicken_n_egg", "mirrored"};
      Item[] charmItems = new Item[]{Items.NETHER_STAR, Items.SLIME_BALL, Items.PRISMARINE_CRYSTALS, Items.FERMENTED_SPIDER_EYE, Items.SPIDER_EYE, Items.AMETHYST_SHARD, Items.EGG, Items.CLOCK};

      for(int i = 0; i < charmNames.length; ++i) {
         String charmName = charmNames[i];
         String apiId = charmApiIds[i];
         ProfileFetcher.Trophy found = null;

         for(ProfileFetcher.Trophy t : this.data.riftTrophies) {
            if (t.type.equalsIgnoreCase(apiId) || t.type.equalsIgnoreCase(charmName)) {
               found = t;
               break;
            }
         }

         int drawX = cx + i % 4 * 20;
         int drawY = cy + i / 4 * 20;
         ItemStack icon = found != null ? new ItemStack(charmItems[i]) : new ItemStack(Items.GRAY_DYE);
         graphics.item(icon, drawX, drawY);
         if (mouseX >= drawX && mouseX <= drawX + 16 && mouseY >= drawY && mouseY <= drawY + 16) {
            List<Component> tooltip = new ArrayList();
            tooltip.add(Component.literal("§a" + charmName + " Timecharm"));
            if (found != null) {
               tooltip.add(Component.literal(""));
               tooltip.add(Component.literal("§7Found after §a" + found.visits + " §7visits"));
               long diff = System.currentTimeMillis() - found.timestamp;
               long days = diff / 86400000L;
               tooltip.add(Component.literal("§7Unlocked §a" + days + " §7days ago"));
            } else {
               tooltip.add(Component.literal("§cLocked"));
            }

            graphics.setTooltipForNextFrame(font, tooltip, Optional.empty(), drawX, drawY);
         }
      }

   }

   private void drawChocolateFactoryTab(GuiGraphicsExtractor graphics, Font font, int px, int py, int mouseX, int mouseY) {
      int gx = px + 20;
      int gy = py + 20;
      graphics.text(font, "§6§lChocolate Factory", gx, gy, -1, true);
      gy += 20;
      graphics.text(font, "§8Chocolate: §6" + String.format("%,d", this.data.cfChocolate), gx, gy, -1, true);
      gy += 14;
      graphics.text(font, "§8Total Chocolate: §6" + String.format("%,d", this.data.cfTotalChocolate), gx, gy, -1, true);
      gy += 14;
      graphics.text(font, "§8Chocolate since Prestige: §6" + String.format("%,d", this.data.cfChocolateSincePrestige), gx, gy, -1, true);
      gy += 20;
      graphics.text(font, "§8Prestige Level: §e" + this.data.cfPrestigeLevel, gx, gy, -1, true);
      gy += 14;
      graphics.text(font, "§8Multiplier Upgrades: §e" + this.data.cfMultiplierUpgrades, gx, gy, -1, true);
   }

   private EntityRenderState extractRenderStateEntity(LivingEntity livingEntity) {
      EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
      EntityRenderState entityRenderState = entityRenderDispatcher.extractEntity(livingEntity, 1.0F);
      entityRenderState.lightCoords = 15728880;
      entityRenderState.shadowPieces.clear();
      entityRenderState.outlineColor = 0;
      return entityRenderState;
   }

   private static enum Tab {
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

      private Tab(String name, ItemStack icon) {
         this.name = name;
         this.icon = icon;
      }

      // $FF: synthetic method
      private static Tab[] $values() {
         return new Tab[]{HOME, COMBAT, INVENTORY, COLLECTIONS, MINING, FISHING, FORAGING, PETS, FARMING, MUSEUM, CHOCOLATE_FACTORY, RIFT, DUNGEONS};
      }
   }

   public static class SubTab {
      public final String id;
      public final String name;
      public final ItemStack icon;

      public SubTab(String id, String name, ItemStack icon) {
         this.id = id;
         this.name = name;
         this.icon = icon;
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
         this.id = id;
         this.name = name;
         this.desc = desc;
         this.maxLevel = maxLevel;
         this.row = row;
         this.col = col;
         this.isAbility = isAbility;
      }
   }

   private static class LevelProgress {
      public final int level;
      public final float progress;

      public LevelProgress(int level, float progress) {
         this.level = level;
         this.progress = progress;
      }
   }
}
