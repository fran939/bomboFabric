package me.bombo.bomboaddons.features.swapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.ItemCustomizeScreen;
import me.bombo.bomboaddons.SkyblockUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

public class InventorySlotSwapManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("inventory_slot_swaps_v2.json");
    private static final Path LEGACY_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("inventory_slot_swaps.json");

    public static class SwapRule {
        public String id = UUID.randomUUID().toString();
        public int hotbarSlot; // 0..8
        public int inventorySlot; // 9..35
        public String profile = "ALL";
        public boolean filterEnabled = false;
        public String filterSkyblockId = "";
        public String filterDisplayName = "";
        public String filterUuid = "";
        public boolean enabled = true;

        public SwapRule() {}

        public SwapRule(int hotbarSlot, int inventorySlot, String profile) {
            this.id = UUID.randomUUID().toString();
            this.hotbarSlot = hotbarSlot;
            this.inventorySlot = inventorySlot;
            this.profile = profile != null ? profile : "ALL";
            this.enabled = true;
        }
    }

    // Profile -> List of SwapRules
    private static Map<String, List<SwapRule>> profileRules = new LinkedHashMap<>();

    // First slot selected when holding swap key to create/toggle a pair
    private static Slot firstSelectedSlot = null;
    private static boolean initialized = false;

    public static final Path COLORS_CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("inventory_slot_colors.json");

    // Distinct default colors for hotbar slots 0..8
    public static final int[] DEFAULT_HOTBAR_COLORS = new int[]{
            0xFFEF4444, // 0 (Key 1): Red
            0xFFF97316, // 1 (Key 2): Orange
            0xFFF59E0B, // 2 (Key 3): Amber / Gold
            0xFF10B981, // 3 (Key 4): Emerald
            0xFF06B6D4, // 4 (Key 5): Cyan
            0xFF3B82F6, // 5 (Key 6): Blue
            0xFF8B5CF6, // 6 (Key 7): Violet
            0xFFEC4899, // 7 (Key 8): Pink
            0xFFF43F5E  // 8 (Key 9): Rose
    };

    public static final int[] HOTBAR_PALETTE = new int[]{
            0xFFEF4444, // Red
            0xFFF97316, // Orange
            0xFFF59E0B, // Amber
            0xFF10B981, // Emerald
            0xFF06B6D4, // Cyan
            0xFF3B82F6, // Blue
            0xFF6366F1, // Indigo
            0xFF8B5CF6, // Purple
            0xFFEC4899, // Pink
            0xFFF43F5E, // Rose
            0xFFFFFFFF, // White
            0xFF64748B  // Slate
    };

    public static int[] customHotbarColors = new int[9];

    public static int getSlotColor(int hotbarSlot) {
        int idx = Math.max(0, Math.min(8, hotbarSlot));
        if (customHotbarColors != null && customHotbarColors.length > idx && customHotbarColors[idx] != 0) {
            return customHotbarColors[idx];
        }
        return DEFAULT_HOTBAR_COLORS[idx];
    }

    public static void setSlotColor(int hotbarSlot, int color) {
        int idx = Math.max(0, Math.min(8, hotbarSlot));
        if (customHotbarColors == null || customHotbarColors.length < 9) {
            customHotbarColors = new int[9];
        }
        customHotbarColors[idx] = color;
        saveColors();
    }

    public static void cycleSlotColor(int hotbarSlot) {
        int current = getSlotColor(hotbarSlot);
        int next = HOTBAR_PALETTE[0];
        for (int i = 0; i < HOTBAR_PALETTE.length; i++) {
            if (HOTBAR_PALETTE[i] == current) {
                next = HOTBAR_PALETTE[(i + 1) % HOTBAR_PALETTE.length];
                break;
            }
        }
        setSlotColor(hotbarSlot, next);
    }

    public static void loadColors() {
        File file = COLORS_CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                int[] loaded = GSON.fromJson(reader, int[].class);
                if (loaded != null && loaded.length == 9) {
                    customHotbarColors = loaded;
                }
            } catch (Exception ignored) {}
        }
    }

    public static void saveColors() {
        try {
            File file = COLORS_CONFIG_PATH.toFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(customHotbarColors, writer);
            }
        } catch (Exception ignored) {}
    }

    public static void init() {
        if (!initialized) {
            load();
            loadColors();
            initialized = true;
        }
    }

    public static String getActiveProfile() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.activeProfile != null && !s.activeProfile.trim().isEmpty()) {
            return s.activeProfile.trim();
        }
        return "default";
    }

    public static List<SwapRule> getRulesForActiveProfile() {
        init();
        String prof = getActiveProfile();
        List<SwapRule> result = new ArrayList<>();
        List<SwapRule> currentList = profileRules.computeIfAbsent(prof, k -> new ArrayList<>());
        result.addAll(currentList);

        if (!prof.equalsIgnoreCase("General (all)") && !prof.equalsIgnoreCase("General") && !prof.equalsIgnoreCase("ALL")) {
            for (Map.Entry<String, List<SwapRule>> entry : profileRules.entrySet()) {
                if (entry.getKey().equalsIgnoreCase("General (all)") || entry.getKey().equalsIgnoreCase("General") || entry.getKey().equalsIgnoreCase("ALL")) {
                    for (SwapRule r : entry.getValue()) {
                        if (!result.contains(r)) {
                            result.add(r);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static List<SwapRule> getAllRules() {
        init();
        List<SwapRule> all = new ArrayList<>();
        for (List<SwapRule> list : profileRules.values()) {
            for (SwapRule r : list) {
                if (!all.contains(r)) {
                    all.add(r);
                }
            }
        }
        return all;
    }

    public static List<SwapRule> getAllRulesForProfile(String profile) {
        init();
        return profileRules.computeIfAbsent(profile != null ? profile : "default", k -> new ArrayList<>());
    }

    public static void addRule(SwapRule rule) {
        init();
        String prof = (rule.profile != null && !rule.profile.isEmpty()) ? rule.profile : getActiveProfile();
        List<SwapRule> list = profileRules.computeIfAbsent(prof, k -> new ArrayList<>());
        list.add(rule);
        save();
    }

    public static void removeRule(SwapRule rule) {
        init();
        for (List<SwapRule> list : profileRules.values()) {
            list.removeIf(r -> r.id.equals(rule.id) || (r.hotbarSlot == rule.hotbarSlot && r.inventorySlot == rule.inventorySlot));
        }
        save();
    }

    public static SwapRule findRule(int hotbarSlot, int inventorySlot) {
        List<SwapRule> list = getRulesForActiveProfile();
        for (SwapRule r : list) {
            if (r.hotbarSlot == hotbarSlot && r.inventorySlot == inventorySlot) {
                return r;
            }
        }
        return null;
    }

    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, List<SwapRule>>>() {}.getType();
                Map<String, List<SwapRule>> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    profileRules = loaded;
                    return;
                }
            } catch (Exception e) {
                System.err.println("[BomboAddons] Failed to load inventory slot swaps v2: " + e.getMessage());
            }
        }

        // Migrate from legacy format if available
        File legacyFile = LEGACY_CONFIG_PATH.toFile();
        if (legacyFile.exists()) {
            try (FileReader reader = new FileReader(legacyFile)) {
                Type type = new TypeToken<Map<String, Map<Integer, List<Integer>>>>() {}.getType();
                Map<String, Map<Integer, List<Integer>>> legacy = GSON.fromJson(reader, type);
                if (legacy != null) {
                    for (Map.Entry<String, Map<Integer, List<Integer>>> pEntry : legacy.entrySet()) {
                        List<SwapRule> rules = profileRules.computeIfAbsent(pEntry.getKey(), k -> new ArrayList<>());
                        for (Map.Entry<Integer, List<Integer>> bEntry : pEntry.getValue().entrySet()) {
                            int hb = bEntry.getKey();
                            for (int inv : bEntry.getValue()) {
                                rules.add(new SwapRule(hb, inv, pEntry.getKey()));
                            }
                        }
                    }
                    save();
                }
            } catch (Exception ignored) {}
        }
    }

    public static void save() {
        try {
            Map<String, List<SwapRule>> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, List<SwapRule>> entry : profileRules.entrySet()) {
                for (SwapRule r : entry.getValue()) {
                    String p = (r.profile != null && !r.profile.isEmpty()) ? r.profile : "default";
                    List<SwapRule> list = cleaned.computeIfAbsent(p, k -> new ArrayList<>());
                    if (!list.contains(r)) {
                        list.add(r);
                    }
                }
            }
            profileRules = cleaned;

            File file = CONFIG_PATH.toFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(profileRules, writer);
            }
        } catch (Exception e) {
            System.err.println("[BomboAddons] Failed to save inventory slot swaps: " + e.getMessage());
        }
    }

    public static boolean isHoldingSwapKey() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.inventorySlotSwapEnabled) return false;
        String key = s.inventorySlotSwapKey;
        if (key == null || key.trim().isEmpty()) return false;
        int keyCode = ClickLogic.getKeyCode(key);
        if (keyCode <= 0) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return false;
        long handle = mc.getWindow().handle();
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS;
    }

    public static boolean isTriggerDown(boolean shiftDown) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.inventorySlotSwapEnabled) return false;
        String trigger = s.inventorySlotSwapTrigger != null ? s.inventorySlotSwapTrigger : "Shift";
        Minecraft mc = Minecraft.getInstance();
        if ("Ctrl".equalsIgnoreCase(trigger)) {
            return mc != null && mc.hasControlDown();
        } else if ("Alt".equalsIgnoreCase(trigger)) {
            if (mc != null && mc.getWindow() != null) {
                long handle = mc.getWindow().handle();
                return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            }
            return false;
        } else if ("Hotkey".equalsIgnoreCase(trigger)) {
            return isHoldingSwapKey();
        }
        return shiftDown || (mc != null && mc.hasShiftDown());
    }

    public static boolean isPlayerInventorySlot(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        return slot != null && mc.player != null && slot.container instanceof Inventory;
    }

    public static int getPlayerInventoryIndex(Slot slot) {
        if (!isPlayerInventorySlot(slot)) return -1;
        return slot.getContainerSlot();
    }

    public static boolean hasActiveFilter(SwapRule rule) {
        if (!rule.filterEnabled) return false;
        boolean hasId = rule.filterSkyblockId != null && !rule.filterSkyblockId.trim().isEmpty();
        boolean hasName = rule.filterDisplayName != null && !rule.filterDisplayName.trim().isEmpty();
        boolean hasUuid = rule.filterUuid != null && !rule.filterUuid.trim().isEmpty();
        return hasId || hasName || hasUuid;
    }

    public static boolean matchesItem(SwapRule rule, ItemStack stack) {
        if (!rule.filterEnabled) return true;
        if (!hasActiveFilter(rule)) return true;
        if (stack == null || stack.isEmpty()) return false;

        // Check Skyblock ID
        if (rule.filterSkyblockId != null && !rule.filterSkyblockId.trim().isEmpty()) {
            String isSbId = SkyblockUtils.getSkyblockId(stack);
            if (isSbId == null) isSbId = "";
            String[] tokens = rule.filterSkyblockId.split("[,;]");
            boolean matched = false;
            for (String t : tokens) {
                String token = t.trim();
                if (!token.isEmpty() && isSbId.equalsIgnoreCase(token)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        // Check Display Name
        if (rule.filterDisplayName != null && !rule.filterDisplayName.trim().isEmpty()) {
            String hover = ChatFormatting.stripFormatting(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
            String[] tokens = rule.filterDisplayName.split("[,;]");
            boolean matched = false;
            for (String t : tokens) {
                String token = t.trim().toLowerCase(Locale.ROOT);
                if (!token.isEmpty() && hover.contains(token)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        // Check UUID
        if (rule.filterUuid != null && !rule.filterUuid.trim().isEmpty()) {
            String itemUuid = ItemCustomizeScreen.extractItemUuid(stack);
            if (itemUuid == null) itemUuid = "";
            String[] tokens = rule.filterUuid.split("[,;]");
            boolean matched = false;
            for (String t : tokens) {
                String token = t.trim();
                if (!token.isEmpty() && itemUuid.equalsIgnoreCase(token)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        return true;
    }

    public static boolean matchesRuleFilter(SwapRule rule, ItemStack stack) {
        return matchesItem(rule, stack);
    }

    /**
     * Handles slot clicks inside an AbstractContainerScreen when holding swap key or trigger clicking.
     */
    public static boolean handleSlotClick(AbstractContainerScreen<?> screen, Slot hoveredSlot, int mouseButton, boolean shiftDown) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.inventorySlotSwapEnabled) return false;

        boolean holdingKey = isHoldingSwapKey();

        // 1. Holding Swap Key (e.g. X) -> Toggle Link / Unlink configuration mode
        if (holdingKey && hoveredSlot != null && isPlayerInventorySlot(hoveredSlot)) {
            int slotIdx = getPlayerInventoryIndex(hoveredSlot);
            Minecraft mc = Minecraft.getInstance();
            String activeProf = getActiveProfile();

            // Right-click while holding key -> Clear bindings for hovered slot
            if (mouseButton == 1) {
                List<SwapRule> rules = getRulesForActiveProfile();
                boolean removed = rules.removeIf(r -> (slotIdx < 9 ? r.hotbarSlot == slotIdx : r.inventorySlot == slotIdx));
                if (removed) {
                    save();
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cCleared swap bindings for slot " + (slotIdx < 9 ? "Hotbar " + (slotIdx + 1) : "Inv " + (slotIdx - 8))));
                    }
                }
                firstSelectedSlot = null;
                return true;
            }

            // Left-click while holding key -> Select first slot or complete pair
            if (mouseButton == 0) {
                if (firstSelectedSlot == null) {
                    firstSelectedSlot = hoveredSlot;
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eSelected " + getSlotDescription(slotIdx) + "§e. Click another slot to link or unlink!"));
                    }
                    return true;
                } else {
                    int firstIdx = getPlayerInventoryIndex(firstSelectedSlot);
                    if (firstIdx == slotIdx) {
                        // Clicked same slot again -> Deselect
                        firstSelectedSlot = null;
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §7Deselected slot."));
                        }
                        return true;
                    }

                    boolean firstIsHotbar = (firstIdx >= 0 && firstIdx < 9);
                    boolean secondIsHotbar = (slotIdx >= 0 && slotIdx < 9);

                    if (firstIsHotbar == secondIsHotbar) {
                        // Both are hotbar or both are inventory -> change selection to this new one
                        firstSelectedSlot = hoveredSlot;
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §eSelection changed to " + getSlotDescription(slotIdx) + "§e. Click a " + (secondIsHotbar ? "inventory" : "hotbar") + " slot."));
                        }
                        return true;
                    }

                    int hotbarSlot = firstIsHotbar ? firstIdx : slotIdx;
                    int invSlot = firstIsHotbar ? slotIdx : firstIdx;

                    if ((hotbarSlot == 8 || invSlot == 8) && SkyblockUtils.isInSkyblock()) {
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cHotbar slot 9 is the SkyBlock Menu and cannot be bound on SkyBlock!"));
                        }
                        firstSelectedSlot = null;
                        return true;
                    }

                    SwapRule existing = findRule(hotbarSlot, invSlot);
                    if (existing != null) {
                        // Clicking again removes that link!
                        removeRule(existing);
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cUnlinked Inventory Slot §e" + (invSlot - 8) + " §cfrom Hotbar Slot §e" + (hotbarSlot + 1) + "§c!"));
                        }
                    } else {
                        // Link them
                        SwapRule newRule = new SwapRule(hotbarSlot, invSlot, activeProf);
                        addRule(newRule);
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aLinked Inventory Slot §e" + (invSlot - 8) + " §ato Hotbar Slot §e" + (hotbarSlot + 1) + "§a!"));
                        }
                    }

                    firstSelectedSlot = null;
                    return true;
                }
            }
            return true;
        }

        // 2. Trigger-Click on linked slots -> Instant Swap execution
        boolean triggerDown = isTriggerDown(shiftDown);
        if (!holdingKey && triggerDown && mouseButton == 0 && hoveredSlot != null && isPlayerInventorySlot(hoveredSlot)) {
            int slotIdx = getPlayerInventoryIndex(hoveredSlot);
            List<SwapRule> rules = getRulesForActiveProfile();
            Minecraft mc = Minecraft.getInstance();
            AbstractContainerMenu menu = screen.getMenu();
            String activeProf = getActiveProfile();

            if (slotIdx >= 9 && slotIdx < 36) {
                // Clicked an inventory slot -> Find matching rule
                for (SwapRule rule : rules) {
                    if (!rule.enabled) continue;
                    if (rule.profile != null && !rule.profile.equalsIgnoreCase("ALL") && !rule.profile.equalsIgnoreCase("General") && !rule.profile.equalsIgnoreCase("General (all)") && !rule.profile.equalsIgnoreCase(activeProf)) continue;
                    if (rule.inventorySlot == slotIdx) {
                        if (rule.hotbarSlot == 8 && SkyblockUtils.isInSkyblock()) {
                            continue;
                        }
                        Slot targetHotbarSlot = findContainerSlot(screen, rule.hotbarSlot);
                        boolean sourceMatch = matchesItem(rule, hoveredSlot.getItem());
                        boolean targetMatch = (targetHotbarSlot != null && matchesItem(rule, targetHotbarSlot.getItem()));
                        if (hasActiveFilter(rule) && !sourceMatch && !targetMatch) {
                            continue;
                        }
                        int targetHotbar = rule.hotbarSlot;
                        if (mc.gameMode != null && mc.player != null && menu != null) {
                            mc.gameMode.handleContainerInput(menu.containerId, hoveredSlot.index, targetHotbar, ContainerInput.SWAP, mc.player);
                        }
                        return true;
                    }
                }
            } else if (slotIdx >= 0 && slotIdx < 9) {
                // Clicked a hotbar slot -> Find matching rule
                if (slotIdx == 8 && SkyblockUtils.isInSkyblock()) {
                    if (mc.player != null) {
                        mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cHotbar slot 9 is the SkyBlock Menu and cannot be moved on SkyBlock!"));
                    }
                    return true;
                }

                List<SwapRule> candidates = new ArrayList<>();
                for (SwapRule rule : rules) {
                    if (!rule.enabled) continue;
                    if (rule.profile != null && !rule.profile.equalsIgnoreCase("ALL") && !rule.profile.equalsIgnoreCase("General") && !rule.profile.equalsIgnoreCase("General (all)") && !rule.profile.equalsIgnoreCase(activeProf)) continue;
                    if (rule.hotbarSlot == slotIdx) {
                        candidates.add(rule);
                    }
                }

                if (candidates.isEmpty()) return false;

                // Smart candidate selection:
                SwapRule chosen = null;
                ItemStack hotbarItem = hoveredSlot.getItem();
                // 1. If hotbar has an item, prefer rule whose filter matches hotbar item
                if (hotbarItem != null && !hotbarItem.isEmpty()) {
                    for (SwapRule r : candidates) {
                        if (hasActiveFilter(r) && matchesItem(r, hotbarItem)) {
                            chosen = r;
                            break;
                        }
                    }
                }
                // 2. If hotbar is empty (or no filter matched), look for inventory slot with item matching filter
                if (chosen == null) {
                    for (SwapRule r : candidates) {
                        Slot invS = findContainerSlot(screen, r.inventorySlot);
                        if (invS != null && invS.hasItem() && hasActiveFilter(r) && matchesItem(r, invS.getItem())) {
                            chosen = r;
                            break;
                        }
                    }
                }
                // 3. Look for inventory slot that has ANY non-empty item
                if (chosen == null) {
                    for (SwapRule r : candidates) {
                        Slot invS = findContainerSlot(screen, r.inventorySlot);
                        if (invS != null && invS.hasItem()) {
                            if (!hasActiveFilter(r) || matchesItem(r, invS.getItem())) {
                                chosen = r;
                                break;
                            }
                        }
                    }
                }
                // 4. Default to first candidate
                if (chosen == null) {
                    chosen = candidates.get(0);
                }

                Slot invSlotObj = findContainerSlot(screen, chosen.inventorySlot);
                if (invSlotObj != null) {
                    if (hasActiveFilter(chosen)) {
                        boolean sMatch = matchesItem(chosen, hoveredSlot.getItem());
                        boolean tMatch = matchesItem(chosen, invSlotObj.getItem());
                        if (!sMatch && !tMatch) {
                            return false;
                        }
                    }
                    if (mc.gameMode != null && mc.player != null && menu != null) {
                        mc.gameMode.handleContainerInput(menu.containerId, invSlotObj.index, slotIdx, ContainerInput.SWAP, mc.player);
                    }
                    return true;
                }
            }
        }

        return false;
    }

    public static Slot findContainerSlot(AbstractContainerScreen<?> screen, int containerSlotIndex) {
        if (screen == null || screen.getMenu() == null) return null;
        for (Slot s : screen.getMenu().slots) {
            if (isPlayerInventorySlot(s) && s.getContainerSlot() == containerSlotIndex) {
                return s;
            }
        }
        return null;
    }

    public static void drawLine2D(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            g.fill(x1 - thickness / 2, y1 - thickness / 2, x1 + thickness / 2 + 1, y1 + thickness / 2 + 1, color);
            return;
        }
        float xInc = (float) dx / steps;
        float yInc = (float) dy / steps;
        float curX = x1;
        float curY = y1;
        int half = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int px = Math.round(curX);
            int py = Math.round(curY);
            g.fill(px - half, py - half, px + half + 1, py + half + 1, color);
            curX += xInc;
            curY += yInc;
        }
    }

    public static void drawConnectingLine(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int thickness) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && !s.swapShowLines) return;
        int actualThickness = s != null ? Math.max(1, Math.round(s.swapLineWidth)) : Math.max(1, thickness);
        // Draw subtle 1px shadow line for contrast
        drawLine2D(g, x1 + 1, y1 + 1, x2 + 1, y2 + 1, 0x88000000, 1);
        // Draw main line
        drawLine2D(g, x1, y1, x2, y2, color != 0 ? color : 0xFF00FF00, actualThickness);
    }

    public static boolean swapHeldHotbarSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return swapHotbarSlot(mc.player.getInventory().getSelectedSlot());
    }

    public static boolean swapHotbarSlot(int hotbarSlot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (hotbarSlot < 0 || hotbarSlot >= 9) {
            hotbarSlot = mc.player.getInventory().getSelectedSlot();
        }
        if (hotbarSlot == 8 && SkyblockUtils.isInSkyblock()) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cHotbar slot 9 is the SkyBlock Menu and cannot be moved on SkyBlock!"));
            return false;
        }

        List<SwapRule> rules = getRulesForActiveProfile();
        String activeProf = getActiveProfile();

        List<SwapRule> candidates = new ArrayList<>();
        for (SwapRule rule : rules) {
            if (!rule.enabled) continue;
            if (rule.profile != null && !rule.profile.equalsIgnoreCase("ALL") && !rule.profile.equalsIgnoreCase("General") && !rule.profile.equalsIgnoreCase("General (all)") && !rule.profile.equalsIgnoreCase(activeProf)) continue;
            if (rule.hotbarSlot == hotbarSlot) {
                candidates.add(rule);
            }
        }

        if (candidates.isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §cNo swap rules configured for Hotbar Slot " + (hotbarSlot + 1) + "."));
            return false;
        }

        AbstractContainerMenu menu = mc.gui.screen() instanceof AbstractContainerScreen<?> acs ? acs.getMenu() : mc.player.inventoryMenu;
        if (menu == null || mc.gameMode == null) return false;

        Slot hSlot = (mc.gui.screen() instanceof AbstractContainerScreen<?> acs) ? findContainerSlot(acs, hotbarSlot) : (hotbarSlot >= 0 && hotbarSlot < 9 && menu.slots.size() > 36 + hotbarSlot ? menu.slots.get(36 + hotbarSlot) : null);
        ItemStack hotbarItem = (hSlot != null) ? hSlot.getItem() : mc.player.getInventory().getItem(hotbarSlot);

        SwapRule chosen = null;
        if (hotbarItem != null && !hotbarItem.isEmpty()) {
            for (SwapRule r : candidates) {
                if (hasActiveFilter(r) && matchesItem(r, hotbarItem)) {
                    chosen = r;
                    break;
                }
            }
        }
        if (chosen == null) {
            for (SwapRule r : candidates) {
                Slot invS = (mc.gui.screen() instanceof AbstractContainerScreen<?> acs) ? findContainerSlot(acs, r.inventorySlot) : (r.inventorySlot >= 9 && r.inventorySlot < 36 && menu.slots.size() > r.inventorySlot ? menu.slots.get(r.inventorySlot) : null);
                if (invS != null && invS.hasItem() && hasActiveFilter(r) && matchesItem(r, invS.getItem())) {
                    chosen = r;
                    break;
                }
            }
        }
        if (chosen == null) {
            for (SwapRule r : candidates) {
                Slot invS = (mc.gui.screen() instanceof AbstractContainerScreen<?> acs) ? findContainerSlot(acs, r.inventorySlot) : (r.inventorySlot >= 9 && r.inventorySlot < 36 && menu.slots.size() > r.inventorySlot ? menu.slots.get(r.inventorySlot) : null);
                if (invS != null && invS.hasItem()) {
                    chosen = r;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = candidates.get(0);
        }

        Slot invSlot = (mc.gui.screen() instanceof AbstractContainerScreen<?> acs) ? findContainerSlot(acs, chosen.inventorySlot) : (chosen.inventorySlot >= 9 && chosen.inventorySlot < 36 && menu.slots.size() > chosen.inventorySlot ? menu.slots.get(chosen.inventorySlot) : null);
        if (invSlot != null) {
            mc.gameMode.handleContainerInput(menu.containerId, invSlot.index, hotbarSlot, ContainerInput.SWAP, mc.player);
            mc.player.sendSystemMessage(Component.literal("§8[§bBomboAddons§8] §aSwapped Hotbar Slot §e" + (hotbarSlot + 1) + " §awith Inv Slot §e" + (chosen.inventorySlot - 8) + "§a!"));
            return true;
        }
        return false;
    }

    public static void renderSlotOverlays(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, int leftPos, int topPos, Font font, int mouseX, int mouseY) {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null || !s.inventorySlotSwapEnabled) return;
        boolean holdingKey = isHoldingSwapKey();
        boolean holdingTrigger = isTriggerDown(Minecraft.getInstance() != null && Minecraft.getInstance().hasShiftDown());

        List<SwapRule> rules = getRulesForActiveProfile();
        String activeProf = getActiveProfile();

        class ConnectedPair {
            final Slot from;
            final Slot to;
            final int color;
            ConnectedPair(Slot from, Slot to, int color) {
                this.from = from;
                this.to = to;
                this.color = color;
            }
        }
        List<ConnectedPair> activeConnections = new ArrayList<>();

        Slot hovered = getHoveredSlot(screen, leftPos, topPos, mouseX, mouseY);

        // Render outlines on all bound slots (and corner badge when key/trigger held)
        for (SwapRule rule : rules) {
            if (!rule.enabled) continue;
            if (rule.profile != null && !rule.profile.equalsIgnoreCase("ALL") && !rule.profile.equalsIgnoreCase("General") && !rule.profile.equalsIgnoreCase("General (all)") && !rule.profile.equalsIgnoreCase(activeProf)) continue;

            Slot hSlot = findContainerSlot(screen, rule.hotbarSlot);
            Slot iSlot = findContainerSlot(screen, rule.inventorySlot);
            int color = getSlotColor(rule.hotbarSlot);

            if (hSlot != null) {
                int hx = leftPos + hSlot.x;
                int hy = topPos + hSlot.y;
                g.outline(hx, hy, 16, 16, color);
                if (holdingKey || holdingTrigger) {
                    g.fill(hx + 1, hy + 1, hx + 5, hy + 5, color);
                }
            }
            if (iSlot != null) {
                int ix = leftPos + iSlot.x;
                int iy = topPos + iSlot.y;
                g.outline(ix, iy, 16, 16, color);
                if (holdingKey || holdingTrigger) {
                    g.fill(ix + 1, iy + 1, ix + 5, iy + 5, color);
                }
            }

            // Check if hovered slot connects via this rule
            if (hovered != null) {
                if (hovered == iSlot && hSlot != null) {
                    activeConnections.add(new ConnectedPair(iSlot, hSlot, color));
                } else if (hovered == hSlot && iSlot != null) {
                    activeConnections.add(new ConnectedPair(hSlot, iSlot, color));
                }
            }
        }

        // Render first selected slot in X-mode & draw line following mouse cursor
        if (holdingKey && firstSelectedSlot != null) {
            int fx = leftPos + firstSelectedSlot.x;
            int fy = topPos + firstSelectedSlot.y;
            int lineCol = (s != null && s.swapUnifiedLineColor) ? s.swapUnifiedLineColorValue : 0xFF00FF00;
            if (s == null || !s.swapUnifiedLineColor) {
                if (firstSelectedSlot.container instanceof net.minecraft.world.entity.player.Inventory) {
                    int cSlot = firstSelectedSlot.getContainerSlot();
                    if (cSlot >= 0 && cSlot < 9) {
                        lineCol = getSlotColor(cSlot);
                    }
                }
            }
            int pulse = (int) (System.currentTimeMillis() / 200 % 2 == 0 ? (0x99000000 | (lineCol & 0x00FFFFFF)) : (0xEE000000 | (lineCol & 0x00FFFFFF)));
            g.fill(fx, fy, fx + 16, fy + 16, 0x33000000 | (lineCol & 0x00FFFFFF));
            g.outline(fx, fy, 16, 16, pulse);

            // Follow mouse until user clicks the target slot
            drawConnectingLine(g, fx + 8, fy + 8, mouseX, mouseY, lineCol, 2);
        } else if (!holdingKey) {
            firstSelectedSlot = null;
        }

        // Render clean connecting lines to ALL connected counterparts simultaneously
        if (hovered != null && !activeConnections.isEmpty()) {
            int mainColor = (s != null && s.swapUnifiedLineColor) ? s.swapUnifiedLineColorValue : activeConnections.get(0).color;
            g.outline(leftPos + hovered.x - 1, topPos + hovered.y - 1, 18, 18, mainColor);
            for (ConnectedPair cp : activeConnections) {
                int x1 = leftPos + cp.from.x + 8;
                int y1 = topPos + cp.from.y + 8;
                int x2 = leftPos + cp.to.x + 8;
                int y2 = topPos + cp.to.y + 8;

                int renderCol = (s != null && s.swapUnifiedLineColor) ? s.swapUnifiedLineColorValue : cp.color;
                drawConnectingLine(g, x1, y1, x2, y2, renderCol, 2);
                g.outline(leftPos + cp.to.x - 1, topPos + cp.to.y - 1, 18, 18, renderCol);
            }
        }
    }

    private static Slot getHoveredSlot(AbstractContainerScreen<?> screen, int leftPos, int topPos, int mouseX, int mouseY) {
        if (screen == null || screen.getMenu() == null) return null;
        for (Slot s : screen.getMenu().slots) {
            int sx = leftPos + s.x;
            int sy = topPos + s.y;
            if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) {
                return s;
            }
        }
        return null;
    }

    private static String getSlotDescription(int slotIdx) {
        if (slotIdx >= 0 && slotIdx < 9) {
            return "Hotbar Slot " + (slotIdx + 1);
        } else if (slotIdx >= 9 && slotIdx < 36) {
            return "Inventory Slot " + (slotIdx - 8);
        }
        return "Slot " + slotIdx;
    }
}
