package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ItemListOverlay {
    public static String query = "";
    private static final List<SkyblockItemManager.SkyblockItemInfo> filteredItems = new ArrayList<>();
    private static final Map<String, ItemStack> itemStackCache = new ConcurrentHashMap<>();
    
    public static int currentPage = 0;
    public static int itemsPerPage = 60;
    public static int cols = 8;
    public static int rows = 8;
    
    public static int sidebarX = 0;
    public static int sidebarY = 0;
    public static int sidebarW = 0;
    public static int sidebarH = 0;
    
    public static EditBox searchBox = null;
    private static boolean initialized = false;

    // Drag and resize states
    public static boolean isDragging = false;
    public static boolean isResizing = false;
    public static double dragOffsetX = 0;
    public static double dragOffsetY = 0;
    private static final int RESIZE_HANDLE_SIZE = 8;
    
    public static ItemStack hoveredStack = null;
    public static String hoveredId = null;

    public static void init() {
        if (!initialized) {
            filterItems();
            initialized = true;
        }
    }

    public static String calcPreview = null;
    public static String pendingQuery = null;
    public static long lastQueryTime = 0;
    
    public static void setQuery(String q) {
        query = q;
        currentPage = 0;
        pendingQuery = q;
        lastQueryTime = q != null && q.isEmpty() ? 0 : System.currentTimeMillis();
        
        if (q != null && !q.isEmpty() && q.matches(".*[\\+\\-\\*/xX].*") && q.matches(".*[0-9].*")) {
            // Replace standalone 'x' or 'X' between numbers with '*'
            String mathQ = q.replaceAll("(?<=\\d)\\s*[xX]\\s*(?=\\d)", "*");
            me.bombo.bomboaddons.SkyblockCalculator.EvaluationResult res = me.bombo.bomboaddons.SkyblockCalculator.evaluate(mathQ);
            if (res.error == null) {
                String resStr = String.valueOf(res.value);
                if (resStr.endsWith(".0")) resStr = resStr.substring(0, resStr.length() - 2);
                calcPreview = q + " = " + resStr;
            } else {
                calcPreview = null;
            }
        } else {
            calcPreview = null;
        }
    }

    private static String expandedId = null;
    private static int expandedX = 0;
    private static int expandedY = 0;
    private static long expandedTime = 0;
    
    public static boolean isHiddenState = false;

    public static long lastSearchBoxClick = 0;
    public static boolean inventorySearchMode = false;
    private static final Map<String, List<SkyblockItemManager.SkyblockItemInfo>> variantMap = new ConcurrentHashMap<>();

        private static int customNameCompare(String nameA, String nameB) {
        // Kuudra armor prefixes
        java.util.List<String> kuudra = java.util.List.of("", "Hot ", "Burning ", "Fiery ", "Infernal ");
        for (String suffix : java.util.List.of("Aurora Helmet", "Aurora Chestplate", "Aurora Leggings", "Aurora Boots", 
                                                "Crimson Helmet", "Crimson Chestplate", "Crimson Leggings", "Crimson Boots",
                                                "Terror Helmet", "Terror Chestplate", "Terror Leggings", "Terror Boots",
                                                "Fervor Helmet", "Fervor Chestplate", "Fervor Leggings", "Fervor Boots",
                                                "Hollow Helmet", "Hollow Chestplate", "Hollow Leggings", "Hollow Boots")) {
            boolean aIsKuudra = false;
            boolean bIsKuudra = false;
            int aRank = -1;
            int bRank = -1;
            for (int i = 0; i < kuudra.size(); i++) {
                if (nameA.equalsIgnoreCase(kuudra.get(i) + suffix)) { aIsKuudra = true; aRank = i; }
                if (nameB.equalsIgnoreCase(kuudra.get(i) + suffix)) { bIsKuudra = true; bRank = i; }
            }
            if (aIsKuudra && bIsKuudra) {
                return Integer.compare(aRank, bRank);
            }
        }
        
        // Roman Numeral Suffixes (Enchants, Minions, Perfect Armor)
        int lastSpaceA = nameA.lastIndexOf(' ');
        int lastSpaceB = nameB.lastIndexOf(' ');
        if (lastSpaceA != -1 && lastSpaceB != -1) {
            String prefixA = nameA.substring(0, lastSpaceA);
            String prefixB = nameB.substring(0, lastSpaceB);
            if (prefixA.equalsIgnoreCase(prefixB)) {
                String suffixA = nameA.substring(lastSpaceA + 1);
                String suffixB = nameB.substring(lastSpaceB + 1);
                int romanA = me.bombo.bomboaddons.RomanNumber.romanToDecimal(suffixA);
                int romanB = me.bombo.bomboaddons.RomanNumber.romanToDecimal(suffixB);
                if (romanA > 0 && romanB > 0) {
                    return Integer.compare(romanA, romanB);
                }
            }
        }
        
        return nameA.compareToIgnoreCase(nameB);
    }

private static void filterItems() {
        filteredItems.clear();
        variantMap.clear();
        Map<String, SkyblockItemManager.SkyblockItemInfo> allItems = SkyblockItemManager.getAllItems();
        if (allItems == null) return;
        
        String lowerQuery = query.toLowerCase().trim();
        Map<String, List<SkyblockItemManager.SkyblockItemInfo>> groups = new HashMap<>();

        boolean hideSkins = BomboConfig.get().itemListHideSkins;
        boolean hideNPCs = BomboConfig.get().itemListHideNPCs;
        boolean hideMobs = BomboConfig.get().itemListHideMobs;
        boolean hideVanilla = BomboConfig.get().itemListHideVanilla;

        for (SkyblockItemManager.SkyblockItemInfo info : allItems.values()) {
            if (hideVanilla && info.vanilla) continue;
            
            if (info.id != null) {
                if (hideSkins) {
                    if (info.id.contains("_SKIN") || info.id.contains("DYE") || info.id.endsWith("_SHIMMER") || info.id.endsWith("_PERSONALITY")) continue;
                    if (info.name != null && (info.name.toLowerCase().contains(" skin") || info.name.toLowerCase().contains(" dye"))) continue;
                }
                if (hideNPCs && info.id.contains("_NPC")) continue;
                if (hideMobs && (info.id.endsWith("_MONSTER") || info.id.endsWith("_BOSS") || info.id.endsWith("_MINIBOSS") || (info.name != null && info.name.toLowerCase().contains("sea creature")))) continue;
            }

            if (lowerQuery.isEmpty() || 
                (info.name != null && info.name.toLowerCase().contains(lowerQuery)) || 
                (info.id != null && info.id.toLowerCase().contains(lowerQuery))) {
                
                String baseId = info.id;
                if (baseId != null) {
                    if (baseId.contains(";")) {
                        baseId = baseId.substring(0, baseId.indexOf(";"));
                    }
                    if (baseId.matches("(HOT|BURNING|FIERY|INFERNAL)_(AURORA|CRIMSON|TERROR|FERVOR|HOLLOW)_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)")) {
                        baseId = baseId.replaceFirst("^(HOT|BURNING|FIERY|INFERNAL)_", "");
                    } else if (baseId.matches("PERFECT_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches(".*_GENERATOR_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches("(SOUL_)?CAMPFIRE_TALISMAN_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches("ROMEO_AND_JULIET_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    } else if (baseId.matches("POTION_AFFINITY_TALISMAN_[0-9]+")) {
                        baseId = baseId.substring(0, baseId.lastIndexOf('_'));
                    }
                }

                if (baseId != null) {
                    groups.computeIfAbsent(baseId, k -> new ArrayList<>()).add(info);
                }
            }
        }
        
        for (List<SkyblockItemManager.SkyblockItemInfo> group : groups.values()) {
            if (group.size() == 1) {
                filteredItems.add(group.get(0));
            } else {
                group.sort((a, b) -> {
                    int tvCmp = Integer.compare(SkyblockItemManager.getTierValue(a.tier), SkyblockItemManager.getTierValue(b.tier));
                    if (tvCmp != 0) return tvCmp;
                    return customNameCompare(a.name != null ? a.name : "", b.name != null ? b.name : "");
                });
                SkyblockItemManager.SkyblockItemInfo rep = group.get(group.size() - 1);
                filteredItems.add(rep);
                variantMap.put(rep.id, group);
            }
        }
        
        // Sort
        int sortType = BomboConfig.get().itemListSortType;
        boolean reverse = BomboConfig.get().itemListSortReverse;
        filteredItems.sort((a, b) -> {
            int result = 0;
            if (sortType == 0) { // Rarity
                int tvA = SkyblockItemManager.getTierValue(a.tier);
                int tvB = SkyblockItemManager.getTierValue(b.tier);
                if (tvA != tvB) result = Integer.compare(tvB, tvA); // Higher rarity first
                else {
                    String nameA = a.name != null ? a.name : "";
                    String nameB = b.name != null ? b.name : "";
                    result = customNameCompare(nameA, nameB);
                }
            } else if (sortType == 1) { // Name
                String nameA = a.name != null ? a.name : "";
                String nameB = b.name != null ? b.name : "";
                result = customNameCompare(nameA, nameB);
            } else { // Price fallback to Name
                String nameA = a.name != null ? a.name : "";
                String nameB = b.name != null ? b.name : "";
                result = customNameCompare(nameA, nameB);
            }
            return reverse ? -result : result;
        });
    }

    private static boolean isCosmetic(SkyblockItemManager.SkyblockItemInfo info) {
        if (info.id == null) return false;
        return info.id.contains("_SKIN") || info.id.contains("DYE") || info.id.endsWith("_SHIMMER") || info.id.endsWith("_PERSONALITY");
    }

    public static void updateLayout(int leftPos, int imageWidth, int topPos, int width, int height) {
        init();
        
        if (BomboConfig.get().itemListX == -1) {
            sidebarW = 180;
            sidebarH = 250;
            sidebarX = 10;
            sidebarY = Math.max(10, height - sidebarH - 10);
        } else {
            sidebarX = BomboConfig.get().itemListX;
            sidebarY = BomboConfig.get().itemListY;
            sidebarW = BomboConfig.get().itemListW;
            sidebarH = BomboConfig.get().itemListH;
            
            // Clamp dimensions to screen bounds
            if (sidebarW > width) {
                sidebarW = width;
            }
            if (sidebarH > height) {
                sidebarH = height;
            }
            
            // Clamp position to screen bounds
            if (sidebarX + sidebarW > width) {
                sidebarX = Math.max(0, width - sidebarW);
            }
            if (sidebarY + sidebarH > height) {
                sidebarY = Math.max(0, height - sidebarH);
            }
            if (sidebarX < 0) sidebarX = 0;
            if (sidebarY < 0) sidebarY = 0;
        }

        updateGridSize();
    }

    public static void updateGridSize() {
        if (sidebarW >= 120) {
            cols = Math.max(1, (sidebarW - 10) / 18);
            rows = Math.max(1, (sidebarH - 55) / 18);
            itemsPerPage = cols * rows;
        }
    }

    public static void saveLayout() {
        BomboConfig.get().itemListX = sidebarX;
        BomboConfig.get().itemListY = sidebarY;
        BomboConfig.get().itemListW = sidebarW;
        BomboConfig.get().itemListH = sidebarH;
        BomboConfig.save();
    }

    public static int getTierColorInt(String tier) {
        if (tier == null) return 0xAA555555;
        switch (tier.toUpperCase()) {
            case "UNCOMMON": return 0xAA55FF55;
            case "RARE": return 0xAA5555FF;
            case "EPIC": return 0xAAAA00AA;
            case "LEGENDARY": return 0xAAFFAA00;
            case "MYTHIC": return 0xAAFF55FF;
            case "DIVINE": return 0xAA55FFFF;
            case "SPECIAL": 
            case "VERY_SPECIAL": return 0xAAFF5555;
            default: return 0xAA555555;
        }
    }

    public static void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (pendingQuery != null && System.currentTimeMillis() - lastQueryTime > 300) {
            filterItems();
            pendingQuery = null;
        }

        BomboConfig.Settings s = BomboConfig.get();
        int globalScreenWidth = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        sidebarX = s.itemListX == -1 ? globalScreenWidth - 150 : s.itemListX;
        sidebarY = s.itemListY == -1 ? 20 : s.itemListY;

        if (s.itemListW != sidebarW || s.itemListH != sidebarH) {
            sidebarW = s.itemListW;
            sidebarH = s.itemListH;
            updateGridSize();
        }



        hoveredStack = null;
        hoveredId = null;
        net.minecraft.network.chat.Component hoveredComponent = null;

        if (!BomboConfig.get().itemListEnabled) {
            if (searchBox != null) searchBox.setVisible(false);
            return;
        } else {
            if (searchBox != null) searchBox.setVisible(true);
        }
        
        SkyblockItemManager.ensureLoaded();

        boolean onLeft = sidebarX < Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
        int togglesWidth = 128;
        int searchBoxW = sidebarW - 10 - togglesWidth - 4;
        if (searchBoxW < 30) searchBoxW = 30; // Min width

        boolean autoHide = BomboConfig.get().autoHideItemList && !(Minecraft.getInstance().screen instanceof HudMoveScreen);
        boolean searchFocused = searchBox != null && searchBox.isFocused();

        int tabX = onLeft ? sidebarX : sidebarX + sidebarW - 15;
        int tabY = sidebarY + (sidebarH / 2) - 15;
        
        if (autoHide && !searchFocused) {
            if (isHiddenState) {
                if (mouseX >= tabX && mouseX <= tabX + 15 && mouseY >= tabY && mouseY <= tabY + 30) {
                    isHiddenState = false;
                }
            } else {
                if (mouseX < sidebarX || mouseX > sidebarX + sidebarW || mouseY < sidebarY || mouseY > sidebarY + sidebarH) {
                    isHiddenState = true;
                }
            }
        } else {
            isHiddenState = false;
        }

        if (isHiddenState) {
            if (searchBox != null) {
                if (BomboConfig.get().itemListSearchAlwaysVisible || BomboConfig.get().itemListSeparateSearch) {
                    searchBox.setVisible(true);
                } else {
                    searchBox.setVisible(false);
                }
            }
            graphics.fill(tabX, tabY, tabX + 15, tabY + 30, 0xAA000000);
            graphics.text(font, onLeft ? ">" : "<", tabX + 4, tabY + 11, 0xFFFFFFFF, true);
            return;
        }
        
        if (searchBox != null) {
            if (!BomboConfig.get().itemListSeparateSearch) {
                if (onLeft) {
                    searchBox.setX(sidebarX + 5 + togglesWidth + 4);
                } else {
                    searchBox.setX(sidebarX + 5);
                }
                searchBox.setY(sidebarY + sidebarH - 52);
                searchBox.setWidth(searchBoxW);
            }
        }
        
        if (Minecraft.getInstance().screen instanceof HudMoveScreen) {
            graphics.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH - 42, 0x88000000);
            graphics.centeredText(font, "Item List Area", sidebarX + sidebarW / 2, sidebarY + (sidebarH - 42) / 2, 0xFFFFFFFF);
            // Draw resize handle
            if (!BomboConfig.get().itemListLocked) {
                graphics.fill(sidebarX, sidebarY + sidebarH - 52, sidebarX + 5, sidebarY + sidebarH - 32, 0xAA00FF00);
            }
            return;
        }
        
        if (!BomboConfig.get().itemListRemoveBackground) {
            graphics.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH - 42, 0xAA000000);
        }
        
        // Draw Toggles
        int togglesX = onLeft ? sidebarX + 5 : sidebarX + 5 + searchBoxW + 4;
        int togglesY = sidebarY + sidebarH - 52;
        
        // Hopper Toggle
        int hopperTx = togglesX;
        graphics.fill(hopperTx, togglesY, hopperTx + 18, togglesY + 20, 0x50000000);
        ItemStack hopperStack = new ItemStack(net.minecraft.world.item.Items.HOPPER);
        int currentSort = BomboConfig.get().itemListSortType;
        String sortName = currentSort == 0 ? "Rarity" : "Name";
        String order = BomboConfig.get().itemListSortReverse ? "(Descending)" : "(Ascending)";
        graphics.item(hopperStack, hopperTx + 1, togglesY + 2);
        if (mouseX >= hopperTx && mouseX < hopperTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
            graphics.fill(hopperTx, togglesY, hopperTx + 18, togglesY + 20, 0x50FFFFFF);
            hoveredComponent = net.minecraft.network.chat.Component.literal("§eSort by " + sortName + " " + order);
        }
        
        // Scatha Skin Toggle
        int scathaTx = togglesX + 22;
        int scathaColor = BomboConfig.get().itemListHideSkins ? 0x80FF0000 : 0x50000000;
        graphics.fill(scathaTx, togglesY, scathaTx + 18, togglesY + 20, scathaColor);
        ItemStack scathaStack = itemStackCache.computeIfAbsent("PET_SKIN_SCATHA_ALBINO", SkyblockItemManager::createSkyblockItem);
        if (scathaStack != null && !scathaStack.isEmpty()) {
            graphics.item(scathaStack, scathaTx + 1, togglesY + 2);
        }
        if (mouseX >= scathaTx && mouseX < scathaTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
            graphics.fill(scathaTx, togglesY, scathaTx + 18, togglesY + 20, 0x50FFFFFF);
            hoveredComponent = net.minecraft.network.chat.Component.literal("§eToggle Skins/Dyes");
        }
        
        // NPC Toggle
        int npcTx = togglesX + 44;
        int npcColor = BomboConfig.get().itemListHideNPCs ? 0x80FF0000 : 0x50000000;
        graphics.fill(npcTx, togglesY, npcTx + 18, togglesY + 20, npcColor);
        ItemStack npcStack = new ItemStack(net.minecraft.world.item.Items.VILLAGER_SPAWN_EGG);
        graphics.item(npcStack, npcTx + 1, togglesY + 2);
        if (mouseX >= npcTx && mouseX < npcTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
            graphics.fill(npcTx, togglesY, npcTx + 18, togglesY + 20, 0x50FFFFFF);
            hoveredComponent = net.minecraft.network.chat.Component.literal("§eToggle NPCs");
        }
        
        // Mob Toggle
        int mobTx = togglesX + 66;
        int mobColor = BomboConfig.get().itemListHideMobs ? 0x80FF0000 : 0x50000000;
        graphics.fill(mobTx, togglesY, mobTx + 18, togglesY + 20, mobColor);
        ItemStack mobStack = new ItemStack(net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG);
        graphics.item(mobStack, mobTx + 1, togglesY + 2);
        if (mouseX >= mobTx && mouseX < mobTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
            graphics.fill(mobTx, togglesY, mobTx + 18, togglesY + 20, 0x50FFFFFF);
            hoveredComponent = net.minecraft.network.chat.Component.literal("§eToggle Mobs");
        }

        // Vanilla Toggle
        int vanillaTx = togglesX + 88;
        int vanillaColor = BomboConfig.get().itemListHideVanilla ? 0x80FF0000 : 0x50000000;
        graphics.fill(vanillaTx, togglesY, vanillaTx + 18, togglesY + 20, vanillaColor);
        ItemStack vanillaStack = new ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK);
        graphics.item(vanillaStack, vanillaTx + 1, togglesY + 2);
        if (mouseX >= vanillaTx && mouseX < vanillaTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
            graphics.fill(vanillaTx, togglesY, vanillaTx + 18, togglesY + 20, 0x50FFFFFF);
            hoveredComponent = net.minecraft.network.chat.Component.literal("§eToggle Vanilla Items");
        }

        // Auto Hide Toggle
        int autoHideTx = togglesX + 110;
        int autoHideColor = BomboConfig.get().autoHideItemList ? 0x8055FF55 : 0x50000000;
        graphics.fill(autoHideTx, togglesY, autoHideTx + 18, togglesY + 20, autoHideColor);
        ItemStack autoHideStack = new ItemStack(net.minecraft.world.item.Items.ENDER_EYE);
        graphics.item(autoHideStack, autoHideTx + 1, togglesY + 2);
        if (mouseX >= autoHideTx && mouseX < autoHideTx + 18 && mouseY >= togglesY && mouseY < togglesY + 20) {
            graphics.fill(autoHideTx, togglesY, autoHideTx + 18, togglesY + 20, 0x50FFFFFF);
            hoveredComponent = net.minecraft.network.chat.Component.literal("§eToggle Auto-Hide");
        }
        
        // Draw resize handle to the left of the search box
        if (!BomboConfig.get().itemListLocked) {
            graphics.fill(sidebarX, sidebarY + sidebarH - 52, sidebarX + 5, sidebarY + sidebarH - 32, 0xFFAAAAAA);
        }

        int startX = sidebarX + 5;
        int startY = sidebarY + 5;
        hoveredStack = null;
        hoveredId = null;

        boolean mouseInPopout = false;
        if (expandedId != null && System.currentTimeMillis() - expandedTime < 200) {
            List<SkyblockItemManager.SkyblockItemInfo> variants = variantMap.get(expandedId);
            if (variants != null) {
                int pCols = Math.min(variants.size(), 10);
                int pRows = (variants.size() + pCols - 1) / pCols;
                int popoutW = pCols * 18;
                int popoutH = pRows * 18;
                int popoutX = expandedX + 8 - (popoutW / 2);
                int popoutY = expandedY + 18;
                
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                if (popoutX < 0) popoutX = 0;
                if (popoutX + popoutW > screenWidth) popoutX = screenWidth - popoutW;

                if (mouseX >= popoutX && mouseX < popoutX + popoutW && mouseY >= popoutY && mouseY < popoutY + popoutH) {
                    mouseInPopout = true;
                }
            }
        }

        for (int i = 0; i < itemsPerPage; i++) {
            int idx = currentPage * itemsPerPage + i;
            if (idx >= filteredItems.size()) break;
            
            int col = i % cols;
            int row = i / cols;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;
            
            SkyblockItemManager.SkyblockItemInfo info = filteredItems.get(idx);
            ItemStack stack = itemStackCache.computeIfAbsent(info.id, SkyblockItemManager::createSkyblockItem);
            
            if (stack != null && !stack.isEmpty()) {
                if (BomboConfig.get().itemListColoredBackground) {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, getTierColorInt(info.tier));
                }
                graphics.item(stack, slotX, slotY);
                
                if (!mouseInPopout && mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                    hoveredStack = stack;
                    hoveredId = info.id;
                }
            }
        }

        // Popout rendering
        boolean anyHovered = false;
        if (hoveredId != null && variantMap.containsKey(hoveredId)) {
            expandedId = hoveredId;
            for (int i = 0; i < itemsPerPage; i++) {
                int idx = currentPage * itemsPerPage + i;
                if (idx >= filteredItems.size()) break;
                if (filteredItems.get(idx).id.equals(hoveredId)) {
                    expandedX = startX + (i % cols) * 18;
                    expandedY = startY + (i / cols) * 18;
                    break;
                }
            }
            expandedTime = System.currentTimeMillis();
            anyHovered = true;
        }

        if (expandedId != null && System.currentTimeMillis() - expandedTime < 200) {
            List<SkyblockItemManager.SkyblockItemInfo> variants = variantMap.get(expandedId);
            if (variants != null) {
                int pCols = Math.min(variants.size(), 10);
                int pRows = (variants.size() + pCols - 1) / pCols;
                int popoutW = pCols * 18;
                int popoutH = pRows * 18;
                int popoutX = expandedX + 8 - (popoutW / 2);
                int popoutY = expandedY + 18;
                
                int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
                if (popoutX < 0) popoutX = 0;
                if (popoutX + popoutW > screenWidth) popoutX = screenWidth - popoutW;

                graphics.fill(popoutX, popoutY, popoutX + popoutW, popoutY + popoutH, 0xDD000000);
                for (int v = 0; v < variants.size(); v++) {
                    SkyblockItemManager.SkyblockItemInfo vInfo = variants.get(v);
                    ItemStack vStack = itemStackCache.computeIfAbsent(vInfo.id, SkyblockItemManager::createSkyblockItem);
                    int vx = popoutX + (v % pCols) * 18;
                    int vy = popoutY + (v / pCols) * 18;
                    graphics.item(vStack, vx, vy);
                    if (mouseX >= vx && mouseX < vx + 16 && mouseY >= vy && mouseY < vy + 16) {
                        graphics.fill(vx, vy, vx + 16, vy + 16, 0x80FFFFFF);
                        hoveredStack = vStack;
                        hoveredId = vInfo.id;
                        anyHovered = true;
                        expandedTime = System.currentTimeMillis();
                    }
                }
            }
        }
        
        if (!anyHovered && System.currentTimeMillis() - expandedTime > 200) {
            expandedId = null;
        }

        // Pagination row rendering
        int maxPages = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / itemsPerPage);
        if (currentPage >= maxPages) currentPage = maxPages - 1;
        if (currentPage < 0) currentPage = 0;
        
        String pageStr = (currentPage + 1) + " / " + maxPages;
        
        int pageRowY = sidebarY + sidebarH - 18;
        graphics.text(font, "§e[<-]", sidebarX + 5, pageRowY + 4, 0xFFFFFFFF, false);
        graphics.text(font, pageStr, sidebarX + 35, pageRowY + 4, 0xFFFFFFFF, false);
        graphics.text(font, "§e[->]", sidebarX + 80, pageRowY + 4, 0xFFFFFFFF, false);

        if (calcPreview != null && searchBox != null && searchBox.visible) {
            int cx = searchBox.getX();
            int cy = searchBox.getY() + 16 + 2;
            int cw = font.width(calcPreview);
            graphics.fill(cx, cy, cx + cw + 4, cy + 12, 0xAA000000);
            graphics.text(font, calcPreview, cx + 2, cy + 2, 0xFFFFAA00, true);
        }

        if (hoveredComponent != null) {
            graphics.setTooltipForNextFrame(font, hoveredComponent, mouseX, mouseY);
        } else if (hoveredStack != null) {
            try {
                if (hoveredId != null && BomboConfig.get().lowestBin) {
                    long price = me.bombo.bomboaddons.LowestBinManager.getLowestBin(hoveredId).getNow(-1L);
                    if (price > 0) {
                        java.util.List<net.minecraft.network.chat.Component> tooltip = hoveredStack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                        boolean isBz = me.bombo.bomboaddons.LowestBinManager.isBazaar(hoveredId);
                        String label = isBz ? "§6BZ: " : "§6Lowest BIN: ";
                        String priceText = label + "§e" + me.bombo.bomboaddons.LowestBinManager.formatPrice(price);
                        
                        if (hoveredId.startsWith("PET-") || hoveredId.contains(";")) {
                            long lvl100Price = me.bombo.bomboaddons.LowestBinManager.getLowestBin(hoveredId + "-100").getNow(-1L);
                            if (lvl100Price > 0) {
                                priceText += " §7(" + me.bombo.bomboaddons.LowestBinManager.formatPrice(lvl100Price) + ")";
                            }
                        }
                        
                        boolean hasPrice = false;
                        for (net.minecraft.network.chat.Component c : tooltip) {
                            if (c.getString().contains("Lowest BIN:") || c.getString().contains("BZ:")) {
                                hasPrice = true;
                                break;
                            }
                        }
                        if (!hasPrice) {
                            java.util.List<net.minecraft.network.chat.Component> mutableTooltip = new java.util.ArrayList<>(tooltip);
                            mutableTooltip.add(net.minecraft.network.chat.Component.literal(priceText));
                            graphics.setTooltipForNextFrame(font, mutableTooltip, java.util.Optional.empty(), mouseX, mouseY);
                            return;
                        }
                    }
                }
                graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            } catch (Throwable t) {
                // Fallback for missing methods across mappings
                graphics.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            }
        }
    }

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!BomboConfig.get().itemListEnabled) return false;
        if (sidebarW < 120) return false;
        // Don't intercept clicks inside the search box, but check for double click and right click
        if (searchBox != null && searchBox.isMouseOver(mouseX, mouseY)) {
            if (button == 1) { // Right click
                searchBox.setValue("");
                searchBox.setFocused(false);
                if (Minecraft.getInstance().screen != null) {
                    Minecraft.getInstance().screen.setFocused(null);
                }
                return true;
            }
            long now = System.currentTimeMillis();
            if (now - lastSearchBoxClick < 300) {
                inventorySearchMode = !inventorySearchMode;
            }
            lastSearchBoxClick = now;
            return false;
        }

        if (isHiddenState) return false;

        if (Minecraft.getInstance().screen instanceof HudMoveScreen) {
            return false;
        }

        boolean onLeft = sidebarX < Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
        int togglesWidth = 128;
        int searchBoxW = sidebarW - 10 - togglesWidth - 4;
        int togglesX = onLeft ? sidebarX + 5 : sidebarX + 5 + searchBoxW + 4;
        int togglesY = sidebarY + sidebarH - 52;

        int hopperTx = togglesX;
        int scathaTx = togglesX + 22;
        int npcTx = togglesX + 44;
        int mobTx = togglesX + 66;
        int vanillaTx = togglesX + 88;
        int autoHideTx = togglesX + 110;

        if (mouseY >= togglesY && mouseY < togglesY + 20) {
            if (mouseX >= hopperTx && mouseX < hopperTx + 18) {
                if (button == 1) { // Right click
                    BomboConfig.get().itemListSortReverse = !BomboConfig.get().itemListSortReverse;
                } else { // Left click
                    int current = BomboConfig.get().itemListSortType;
                    BomboConfig.get().itemListSortType = (current + 1) % 2;
                }
                BomboConfig.save();
                filterItems();
                return true;
            } else if (mouseX >= scathaTx && mouseX < scathaTx + 18) {
                BomboConfig.get().itemListHideSkins = !BomboConfig.get().itemListHideSkins;
                BomboConfig.save();
                filterItems();
                return true;
            } else if (mouseX >= npcTx && mouseX < npcTx + 18) {
                BomboConfig.get().itemListHideNPCs = !BomboConfig.get().itemListHideNPCs;
                BomboConfig.save();
                filterItems();
                return true;
            } else if (mouseX >= mobTx && mouseX < mobTx + 18) {
                BomboConfig.get().itemListHideMobs = !BomboConfig.get().itemListHideMobs;
                BomboConfig.save();
                filterItems();
                return true;
            } else if (mouseX >= vanillaTx && mouseX < vanillaTx + 18) {
                BomboConfig.get().itemListHideVanilla = !BomboConfig.get().itemListHideVanilla;
                BomboConfig.save();
                filterItems();
                return true;
            } else if (mouseX >= autoHideTx && mouseX < autoHideTx + 18) {
                BomboConfig.get().autoHideItemList = !BomboConfig.get().autoHideItemList;
                BomboConfig.save();
                return true;
            }
        }

        // Check popout clicks first
        if (expandedId != null) {
            List<SkyblockItemManager.SkyblockItemInfo> variants = variantMap.get(expandedId);
            if (variants != null) {
                int popoutX = expandedX + 18;
                int popoutY = expandedY;
                for (int v = 0; v < variants.size(); v++) {
                    int vx = popoutX + v * 18;
                    if (mouseX >= vx && mouseX < vx + 16 && mouseY >= popoutY && mouseY < popoutY + 16) {
                        SkyblockItemManager.SkyblockItemInfo info = variants.get(v);
                        if (button == 0) {
                            Minecraft.getInstance().setScreenAndShow(new RecipeViewerScreen(info.id, Minecraft.getInstance().screen));
                        } else if (button == 1) {
                            RecipeViewerScreen rvs = new RecipeViewerScreen(info.id, Minecraft.getInstance().screen);
                            rvs.setUsageMode(true);
                            Minecraft.getInstance().setScreenAndShow(rvs);
                        }
                        return true;
                    }
                }
            }
        }

        // Check if background clicked for dragging
        // We only allow dragging from the top area above the search box
        if (mouseX >= sidebarX && mouseX <= sidebarX + sidebarW &&
            mouseY >= sidebarY && mouseY <= sidebarY + sidebarH - 56) {
            
            // Wait, before starting drag, check if a slot was clicked
            int startX = sidebarX + 5;
            int startY = sidebarY + 5;
            for (int i = 0; i < itemsPerPage; i++) {
                int idx = currentPage * itemsPerPage + i;
                if (idx >= filteredItems.size()) break;
                
                int col = i % cols;
                int row = i / cols;
                int slotX = startX + col * 18;
                int slotY = startY + row * 18;
                
                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    SkyblockItemManager.SkyblockItemInfo info = filteredItems.get(idx);
                    if (button == 0) {
                        Minecraft.getInstance().setScreenAndShow(new RecipeViewerScreen(info.id, Minecraft.getInstance().screen));
                    } else if (button == 1) {
                        RecipeViewerScreen rvs = new RecipeViewerScreen(info.id, Minecraft.getInstance().screen);
                        rvs.setUsageMode(true);
                        Minecraft.getInstance().setScreenAndShow(rvs);
                    }
                    return true;
                }
            }

            // If not clicked on a slot, start dragging
            if (!BomboConfig.get().itemListLocked) {
                isDragging = true;
                dragOffsetX = mouseX - sidebarX;
                dragOffsetY = mouseY - sidebarY;
                return true;
            }
            return false;
        }

        // Check pagination buttons
        int pageRowY = sidebarY + sidebarH - 18;
        if (mouseY >= pageRowY && mouseY < pageRowY + 18) {
            int maxPages = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / itemsPerPage);
            if (mouseX >= sidebarX + 5 && mouseX < sidebarX + 30) { // Prev
                if (currentPage > 0) {
                    currentPage--;
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                        )
                    );
                }
                return true;
            } else if (mouseX >= sidebarX + 80 && mouseX < sidebarX + 105) { // Next
                if (currentPage < maxPages - 1) {
                    currentPage++;
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                        )
                    );
                }
                return true;
            }
        }

        return false;
    }


    public static boolean mouseScrolled(double mouseX, double mouseY, double amountY) {
        if (sidebarW < 120) return false;
        
        if (mouseX >= sidebarX && mouseX < sidebarX + sidebarW && mouseY >= sidebarY && mouseY < sidebarY + sidebarH) {
            int maxPages = Math.max(1, (filteredItems.size() + itemsPerPage - 1) / itemsPerPage);
            int scroll = (int) Math.signum(amountY);
            if (scroll > 0 && currentPage > 0) {
                currentPage--;
                return true;
            } else if (scroll < 0 && currentPage < maxPages - 1) {
                currentPage++;
                return true;
            }
        }
        return false;
    }
}
