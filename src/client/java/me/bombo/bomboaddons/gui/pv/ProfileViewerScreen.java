package me.bombo.bomboaddons.gui.pv;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.bombo.bomboaddons.LowestBinManager;
import me.bombo.bomboaddons.features.profile.ProfileFetcher;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProfileViewerScreen extends Screen {
    private final String targetPlayer;
    private ProfileFetcher.ProfileData profile;
    private boolean loading = true;
    private String errorMessage = null;

    // Tabs matching skyblock-pv
    public enum PvCategory {
        MAIN("Overview", Items.PLAYER_HEAD),
        ARMOR("Armor & Equip", Items.DIAMOND_CHESTPLATE),
        INVENTORY("Inventory", Items.CHEST),
        ENDERCHEST("Ender Chest", Items.ENDER_CHEST),
        BACKPACKS("Backpacks", Items.BARREL),
        WARDROBE("Wardrobe", Items.LEATHER_CHESTPLATE),
        PETS("Pets", Items.BONE),
        ACCESSORIES("Accessories", Items.TOTEM_OF_UNDYING),
        DUNGEONS("Dungeons", Items.WITHER_SKELETON_SKULL),
        SLAYERS("Slayers", Items.ROTTEN_FLESH),
        SKILLS("Skills", Items.DIAMOND_SWORD),
        MUSEUM("Museum", Items.GOLD_BLOCK),
        RIFT("Rift", Items.DRAGON_BREATH);

        public final String title;
        public final ItemStack icon;

        PvCategory(String title, net.minecraft.world.item.Item item) {
            this.title = title;
            this.icon = new ItemStack(item);
        }
    }

    private PvCategory currentCategory = PvCategory.MAIN;
    private int scrollOffset = 0;
    private ItemStack hoveredStack = ItemStack.EMPTY;

    public ProfileViewerScreen(String player) {
        super(Component.literal("Profile Viewer - " + player));
        this.targetPlayer = player;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        if (this.profile == null && this.loading) {
            if (me.bombo.bomboaddons.BomboConfig.get().pvDebug) {
                me.bombo.bomboaddons.Bomboaddons.sendMessage("§8[§3Bombo PV Debug§8] §eFetching profile data for §b" + this.targetPlayer + "§e...");
            }
            ProfileFetcher.fetchProfile(this.targetPlayer).thenAccept(data -> {
                Minecraft.getInstance().execute(() -> {
                    this.loading = false;
                    if (data != null) {
                        this.profile = data;
                        if (me.bombo.bomboaddons.BomboConfig.get().pvDebug) {
                            me.bombo.bomboaddons.Bomboaddons.sendMessage("§8[§3Bombo PV Debug§8] §aProfile loaded: §f" + data.username + " §7(Profile: " + data.profileName + ", SB Lvl: " + String.format("%.1f", data.skyblockLevel) + ", Networth: " + (long)data.networth + ")");
                        }
                    } else {
                        this.errorMessage = "Failed to load profile for " + this.targetPlayer;
                        if (me.bombo.bomboaddons.BomboConfig.get().pvDebug) {
                            me.bombo.bomboaddons.Bomboaddons.sendMessage("§8[§3Bombo PV Debug§8] §cFailed to load profile for " + this.targetPlayer);
                        }
                    }
                });
            }).exceptionally(ex -> {
                Minecraft.getInstance().execute(() -> {
                    this.loading = false;
                    this.errorMessage = "Error loading profile: " + ex.getMessage();
                    if (me.bombo.bomboaddons.BomboConfig.get().pvDebug) {
                        me.bombo.bomboaddons.Bomboaddons.sendMessage("§8[§3Bombo PV Debug§8] §cException: " + ex.getMessage());
                    }
                });
                return null;
            });
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && this.scrollOffset > 0) {
            this.scrollOffset--;
        } else if (scrollY < 0) {
            this.scrollOffset++;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        int panelW = Math.min(this.width - 40, 520);
        int panelH = Math.min(this.height - 40, 310);
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        int tabX = px;
        int tabY = py - 26;
        PvCategory[] cats = PvCategory.values();
        for (int i = 0; i < cats.length; i++) {
            int tx = tabX + i * 36;
            if (event.x() >= tx && event.x() < tx + 34 && event.y() >= tabY && event.y() < tabY + 24) {
                this.currentCategory = cats[i];
                this.scrollOffset = 0;
                return true;
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);

        int panelW = Math.min(this.width - 40, 520);
        int panelH = Math.min(this.height - 40, 310);
        int px = (this.width - panelW) / 2;
        int py = (this.height - panelH) / 2;

        // Render Tabs
        int tabX = px;
        int tabY = py - 26;
        PvCategory[] cats = PvCategory.values();
        for (int i = 0; i < cats.length; i++) {
            PvCategory cat = cats[i];
            boolean sel = cat == this.currentCategory;
            int tx = tabX + i * 36;
            int ty = sel ? tabY - 2 : tabY;
            int th = sel ? 28 : 24;

            g.fill(tx, ty, tx + 34, ty + th, sel ? 0xFF2A2A2A : 0xFF141414);
            g.fill(tx + 1, ty + 1, tx + 33, ty + th, sel ? 0xFF383838 : 0xFF1E1E1E);
            if (sel) {
                g.fill(tx + 1, ty + 1, tx + 33, ty + 2, 0xFF55FFFF);
            }
            g.item(cat.icon, tx + 9, ty + 4);

            if (mouseX >= tx && mouseX < tx + 34 && mouseY >= ty && mouseY < ty + th) {
                g.setTooltipForNextFrame(this.font, List.of(Component.literal("§e" + cat.title)), java.util.Optional.empty(), mouseX, mouseY);
            }
        }

        // Main Window Panel
        g.fill(px, py, px + panelW, py + panelH, 0xFF181818);
        g.fill(px + 1, py + 1, px + panelW - 1, py + panelH - 1, 0xFF222222);

        // Header
        g.fill(px + 6, py + 6, px + panelW - 6, py + 38, 0xFF181818);
        if (this.loading) {
            g.text(this.font, "§eLoading profile data for " + this.targetPlayer + "...", px + 14, py + 18, -1, true);
            return;
        }

        if (this.errorMessage != null || this.profile == null) {
            g.text(this.font, "§c" + (this.errorMessage != null ? this.errorMessage : "Unknown error"), px + 14, py + 18, -1, true);
            return;
        }

        // Profile Header Info
        String ign = "§b§l" + this.profile.username;
        String profName = "§7Profile: §a" + (this.profile.profileName != null ? this.profile.profileName : "Unknown");
        String sbLvl = "§6[SB " + String.format("%.0f", this.profile.skyblockLevel) + "]";
        String purseStr = "§6Purse: §e" + LowestBinManager.formatPrice((long) this.profile.purse);
        String bankStr = "§6Bank: §e" + LowestBinManager.formatPrice((long) this.profile.bank);
        String nwStr = "§dNetworth: §b" + LowestBinManager.formatPrice((long) this.profile.networth);

        g.text(this.font, ign + " " + sbLvl + " " + profName, px + 12, py + 12, -1, true);
        g.text(this.font, purseStr + "  " + bankStr + "  " + nwStr, px + 12, py + 24, -1, false);

        int contentX = px + 10;
        int contentY = py + 46;
        int contentW = panelW - 20;
        int contentH = panelH - 56;

        this.hoveredStack = ItemStack.EMPTY;

        switch (this.currentCategory) {
            case MAIN -> renderMainTab(g, contentX, contentY, contentW, contentH);
            case ARMOR -> renderArmorTab(g, contentX, contentY, contentW, contentH);
            case INVENTORY -> renderItemsGrid(g, this.profile.inventory, contentX, contentY, contentW, contentH, 9);
            case ENDERCHEST -> renderItemsGrid(g, this.profile.enderChest, contentX, contentY, contentW, contentH, 9);
            case BACKPACKS -> renderBackpacksTab(g, contentX, contentY, contentW, contentH);
            case WARDROBE -> renderItemsGrid(g, this.profile.wardrobe, contentX, contentY, contentW, contentH, 9);
            case PETS -> renderPetsTab(g, contentX, contentY, contentW, contentH);
            case ACCESSORIES -> renderItemsGrid(g, this.profile.accessories, contentX, contentY, contentW, contentH, 9);
            case DUNGEONS -> renderDungeonsTab(g, contentX, contentY, contentW, contentH);
            case SLAYERS -> renderSlayersTab(g, contentX, contentY, contentW, contentH);
            case SKILLS -> renderSkillsTab(g, contentX, contentY, contentW, contentH);
            case MUSEUM -> renderMuseumTab(g, contentX, contentY, contentW, contentH);
            case RIFT -> renderRiftTab(g, contentX, contentY, contentW, contentH);
        }

        if (!this.hoveredStack.isEmpty()) {
            g.setTooltipForNextFrame(this.font, this.hoveredStack, mouseX, mouseY);
        }
    }

    private void renderMainTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int col1W = w / 2 - 10;
        int col2X = x + col1W + 20;

        // Column 1: Skills & Slayers
        int cy = y;
        g.text(this.font, "§a§lSkill Levels", x, cy, -1, true);
        cy += 14;
        g.text(this.font, "§7Combat: §e" + this.profile.combat + "   §7Mining: §e" + this.profile.mining, x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Farming: §e" + this.profile.farming + "   §7Foraging: §e" + this.profile.foraging, x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Fishing: §e" + this.profile.fishing + "   §7Enchanting: §e" + this.profile.enchanting, x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Alchemy: §e" + this.profile.alchemy + "   §7Taming: §e" + String.format("%.0f", this.profile.taming), x, cy, -1, false); cy += 18;

        g.text(this.font, "§c§lSlayer Progression", x, cy, -1, true);
        cy += 14;
        g.text(this.font, "§7Zombie: §eLvl " + this.profile.zombieSlayer + " (" + formatCompact((long)this.profile.zombieSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Spider: §eLvl " + this.profile.spiderSlayer + " (" + formatCompact((long)this.profile.spiderSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Wolf: §eLvl " + this.profile.wolfSlayer + " (" + formatCompact((long)this.profile.wolfSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Enderman: §eLvl " + this.profile.endermanSlayer + " (" + formatCompact((long)this.profile.endermanSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Blaze: §eLvl " + this.profile.blazeSlayer + " (" + formatCompact((long)this.profile.blazeSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 12;
        g.text(this.font, "§7Vampire: §eLvl " + this.profile.vampireSlayer + " (" + formatCompact((long)this.profile.vampireSlayerInfo.xp) + " XP)", x, cy, -1, false);

        // Column 2: Dungeons & Overview
        int ry = y;
        g.text(this.font, "§d§lDungeons & Catacombs", col2X, ry, -1, true);
        ry += 14;
        g.text(this.font, "§7Catacombs Level: §e" + this.profile.catacombs + " §7(" + formatCompact((long)this.profile.catacombsXp) + " XP)", col2X, ry, -1, false); ry += 12;
        g.text(this.font, "§7Selected Class: §a" + this.profile.selectedDungeonClass, col2X, ry, -1, false); ry += 12;
        g.text(this.font, "§7Total Secrets Found: §b" + this.profile.totalSecrets, col2X, ry, -1, false); ry += 18;

        g.text(this.font, "§6§lEquipped Armor", col2X, ry, -1, true);
        ry += 14;
        for (int i = 0; i < 4; i++) {
            ItemStack st = this.profile.armor != null && this.profile.armor.size() > i ? this.profile.armor.get(i) : ItemStack.EMPTY;
            int sx = col2X + i * 24;
            drawItemSlot(g, st, sx, ry);
        }
        ry += 28;

        g.text(this.font, "§b§lEquipped Equipment", col2X, ry, -1, true);
        ry += 14;
        for (int i = 0; i < 4; i++) {
            ItemStack st = this.profile.equipment != null && this.profile.equipment.size() > i ? this.profile.equipment.get(i) : ItemStack.EMPTY;
            int sx = col2X + i * 24;
            drawItemSlot(g, st, sx, ry);
        }
    }

    private void renderArmorTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.text(this.font, "§6Equipped Armor", x, y, -1, true);
        for (int i = 0; i < 4; i++) {
            ItemStack st = this.profile.armor != null && this.profile.armor.size() > i ? this.profile.armor.get(i) : ItemStack.EMPTY;
            drawItemSlot(g, st, x + i * 24, y + 14);
        }

        g.text(this.font, "§bEquipped Equipment", x, y + 46, -1, true);
        for (int i = 0; i < 4; i++) {
            ItemStack st = this.profile.equipment != null && this.profile.equipment.size() > i ? this.profile.equipment.get(i) : ItemStack.EMPTY;
            drawItemSlot(g, st, x + i * 24, y + 60);
        }

        g.text(this.font, "§eWardrobe Sets", x, y + 92, -1, true);
        renderItemsGrid(g, this.profile.wardrobe, x, y + 106, w, h - 106, 9);
    }

    private void renderBackpacksTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (this.profile.backpacks.isEmpty()) {
            g.text(this.font, "§7No backpacks unlocked or found.", x, y, -1, false);
            return;
        }
        int by = y;
        for (Map.Entry<Integer, List<ItemStack>> entry : this.profile.backpacks.entrySet()) {
            g.text(this.font, "§6Backpack #" + (entry.getKey() + 1), x, by, -1, true);
            by += 14;
            List<ItemStack> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                int col = i % 9;
                int row = i / 9;
                drawItemSlot(g, items.get(i), x + col * 20, by + row * 20);
            }
            by += ((items.size() + 8) / 9) * 20 + 8;
        }
    }

    private void renderPetsTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (this.profile.pets.isEmpty()) {
            g.text(this.font, "§7No pets found on profile.", x, y, -1, false);
            return;
        }
        int py = y;
        for (ProfileFetcher.Pet pet : this.profile.pets) {
            String activeStr = pet.active ? "§a[ACTIVE] " : "§8";
            String tierColor = switch (pet.tier != null ? pet.tier : "COMMON") {
                case "UNCOMMON" -> "§a";
                case "RARE" -> "§9";
                case "EPIC" -> "§5";
                case "LEGENDARY" -> "§6";
                case "MYTHIC" -> "§d";
                default -> "§f";
            };
            g.text(this.font, activeStr + tierColor + pet.tier + " " + pet.type + " §7(Held: " + (pet.heldItem != null ? pet.heldItem : "None") + ")", x, py, -1, false);
            py += 14;
        }
    }

    private void renderDungeonsTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.text(this.font, "§dCatacombs Level: §e" + this.profile.catacombs + " §7(" + formatCompact((long)this.profile.catacombsXp) + " XP)", x, y, -1, true);
        g.text(this.font, "§7Total Secrets: §b" + this.profile.totalSecrets, x, y + 14, -1, false);

        int cy = y + 36;
        g.text(this.font, "§aNormal Floor Completions:", x, cy, -1, true); cy += 14;
        for (int floor = 1; floor <= 7; floor++) {
            int comp = this.profile.normalFloorCompletions.getOrDefault(floor, 0);
            g.text(this.font, "§7Floor " + floor + ": §e" + comp + " completions", x, cy, -1, false); cy += 12;
        }

        int my = y + 36;
        int mX = x + w / 2;
        g.text(this.font, "§cMaster Mode Completions:", mX, my, -1, true); my += 14;
        for (int floor = 1; floor <= 7; floor++) {
            int comp = this.profile.masterFloorCompletions.getOrDefault(floor, 0);
            g.text(this.font, "§7Master " + floor + ": §e" + comp + " completions", mX, my, -1, false); my += 12;
        }
    }

    private void renderSlayersTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int cy = y;
        g.text(this.font, "§c§lSlayer Summary", x, cy, -1, true); cy += 16;
        g.text(this.font, "§7Zombie Slayer: §eLvl " + this.profile.zombieSlayer + "  §7(" + formatCompact((long)this.profile.zombieSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Spider Slayer: §eLvl " + this.profile.spiderSlayer + "  §7(" + formatCompact((long)this.profile.spiderSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Wolf Slayer: §eLvl " + this.profile.wolfSlayer + "  §7(" + formatCompact((long)this.profile.wolfSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Enderman Slayer: §eLvl " + this.profile.endermanSlayer + "  §7(" + formatCompact((long)this.profile.endermanSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Blaze Slayer: §eLvl " + this.profile.blazeSlayer + "  §7(" + formatCompact((long)this.profile.blazeSlayerInfo.xp) + " XP)", x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Vampire Slayer: §eLvl " + this.profile.vampireSlayer + "  §7(" + formatCompact((long)this.profile.vampireSlayerInfo.xp) + " XP)", x, cy, -1, false);
    }

    private void renderSkillsTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int cy = y;
        g.text(this.font, "§b§lPlayer Skills", x, cy, -1, true); cy += 16;
        g.text(this.font, "§7Combat: §e" + this.profile.combat, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Mining: §e" + this.profile.mining, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Farming: §e" + this.profile.farming, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Foraging: §e" + this.profile.foraging, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Fishing: §e" + this.profile.fishing, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Enchanting: §e" + this.profile.enchanting, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Alchemy: §e" + this.profile.alchemy, x, cy, -1, false); cy += 14;
        g.text(this.font, "§7Taming: §e" + String.format("%.0f", this.profile.taming), x, cy, -1, false);
    }

    private void renderMuseumTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int my = y;
        g.text(this.font, "§6Museum Weapons (" + this.profile.museumWeapons.size() + ")", x, my, -1, true);
        renderItemsGrid(g, this.profile.museumWeapons, x, my + 14, w, 50, 9);
        my += 70;
        g.text(this.font, "§6Museum Armor (" + this.profile.museumArmor.size() + ")", x, my, -1, true);
        renderItemsGrid(g, this.profile.museumArmor, x, my + 14, w, 50, 9);
        my += 70;
        g.text(this.font, "§6Museum Special / Rarities (" + (this.profile.museumRarities.size() + this.profile.museumSpecial.size()) + ")", x, my, -1, true);
        List<ItemStack> combined = new ArrayList<>(this.profile.museumRarities);
        combined.addAll(this.profile.museumSpecial);
        renderItemsGrid(g, combined, x, my + 14, w, 50, 9);
    }

    private void renderRiftTab(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int ry = y;
        g.text(this.font, "§5§lThe Rift Overview", x, ry, -1, true); ry += 16;
        g.text(this.font, "§7Rift Visits: §d" + this.profile.riftVisits, x, ry, -1, false); ry += 14;
        g.text(this.font, "§7Motes Stored: §d" + formatCompact(this.profile.riftMotes) + " §7(Lifetime: " + formatCompact(this.profile.riftLifetimeMotes) + ")", x, ry, -1, false); ry += 14;
        g.text(this.font, "§7Enigma Souls: §d" + this.profile.riftEnigmaSouls + " / 42", x, ry, -1, false); ry += 14;
        g.text(this.font, "§7Dead Cats: §d" + this.profile.riftDeadCats + " / 9", x, ry, -1, false); ry += 14;
        g.text(this.font, "§7Unlocked Eyes: §d" + this.profile.riftUnlockedEyes, x, ry, -1, false); ry += 14;
        g.text(this.font, "§7Larva Eaten (Grubber): §d" + this.profile.riftGrubber, x, ry, -1, false);
    }

    private void renderItemsGrid(GuiGraphicsExtractor g, List<ItemStack> items, int x, int y, int w, int h, int cols) {
        if (items == null || items.isEmpty()) {
            g.text(this.font, "§7Empty.", x, y, -1, false);
            return;
        }

        int maxRows = h / 20;
        int startIndex = this.scrollOffset * cols;
        for (int i = startIndex; i < items.size(); i++) {
            int slotIdx = i - startIndex;
            int row = slotIdx / cols;
            int col = slotIdx % cols;
            if (row >= maxRows) break;

            drawItemSlot(g, items.get(i), x + col * 20, y + row * 20);
        }
    }

    private void drawItemSlot(GuiGraphicsExtractor g, ItemStack stack, int sx, int sy) {
        g.fill(sx, sy, sx + 18, sy + 18, 0xFF141414);
        g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF2A2A2A);
        if (stack != null && !stack.isEmpty()) {
            g.item(stack, sx + 1, sy + 1);
            g.itemDecorations(this.font, stack, sx + 1, sy + 1);
            Minecraft mc = Minecraft.getInstance();
            double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
            double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                this.hoveredStack = stack;
            }
        }
    }

    private String formatCompact(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fB", n / 1_000_000_000.0);
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000L) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }
}
