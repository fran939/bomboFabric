package me.bombo.bomboaddons.gui.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import me.bombo.bomboaddons.BomboConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FeatureOrganizerManager {
    private static final Path FILE_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("feature_organization.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static class FeatureMeta {
        public String name;
        public String category = "General";
        public String subCategory = "";
        public boolean isCheat = false;
        public String description = "";
        public String parentDependency = ""; // If set, this feature requires parent feature to be enabled
        public boolean enabledByDefault = false;

        public FeatureMeta() {}

        public FeatureMeta(String name, String category, String subCategory, boolean isCheat) {
            this(name, category, subCategory, isCheat, "");
        }

        public FeatureMeta(String name, String category, String subCategory, boolean isCheat, String description) {
            this.name = name;
            this.category = category != null ? category : "General";
            this.subCategory = subCategory != null ? subCategory : "";
            this.isCheat = isCheat;
            this.description = description != null ? description : "";
            this.parentDependency = "";
            this.enabledByDefault = false;
        }
    }

    public static class OrganizerData {
        public List<String> categories = new ArrayList<>();
        public Map<String, List<String>> subcategories = new LinkedHashMap<>();
        public Map<String, FeatureMeta> features = new LinkedHashMap<>();
    }

    public static final List<String> customCategories = new ArrayList<>();
    public static final Map<String, List<String>> subcategoriesMap = new LinkedHashMap<>();
    public static final Map<String, FeatureMeta> features = new LinkedHashMap<>();

    public static String searchFilter = "";
    public static String categoryFilter = "ALL";
    public static float scrollOffset = 0f;
    public static boolean searchBoxFocused = false;

    // Category and subcategory adding modals / inputs
    public static boolean addingCategory = false;
    public static String newCategoryInput = "";
    public static boolean addingSubcategory = false;
    public static String targetCatForSub = "";
    public static String newSubcategoryInput = "";

    // Card bounds tracking
    public static int lastCardX = 0;
    public static int lastCardY = 0;
    public static int lastCardW = 0;
    public static int lastCardH = 0;

    public static boolean isMouseOverCard(double mouseX, double mouseY) {
        return mouseX >= lastCardX && mouseX <= lastCardX + lastCardW && mouseY >= lastCardY && mouseY <= lastCardY + lastCardH;
    }

    // Dropdown state
    public static boolean dropdownOpen = false;
    public static boolean isCategoryFilterDropdown = false;
    public static FeatureMeta dropdownTargetFeature = null;
    public static boolean dropdownIsSubCategory = false;
    public static int dropdownX = 0;
    public static int dropdownY = 0;
    public static int dropdownW = 140;
    public static float dropdownScroll = 0f;
    public static List<String> dropdownOptions = new ArrayList<>();

    private static boolean initialized = false;

    public static List<String> getOrganizerCategories() {
        initDefaults();
        return new ArrayList<>(customCategories);
    }

    public static void initDefaults() {
        if (initialized) return;
        initialized = true;

        // 1. Auto-discover all default features and their descriptions from all registered categories
        List<String> scanCats = new ArrayList<>(ConfigRegistry.BASE_CATEGORIES);
        if (!scanCats.contains("GUI Settings")) scanCats.add("GUI Settings");
        if (!scanCats.contains("Debug")) scanCats.add("Debug");
        if (!scanCats.contains("Dev")) scanCats.add("Dev");

        for (String cat : scanCats) {
            if (cat.equals("Uncategorized")) continue;
            try {
                List<ConfigItem> items = ConfigRegistry.getRegisteredItemsForCategory(cat);
                for (ConfigItem item : items) {
                    if (item.type == ConfigItem.Type.HEADER || item.type == ConfigItem.Type.CUSTOM_CARD) {
                        continue;
                    }
                    if (item.name != null && !item.name.trim().isEmpty() && !features.containsKey(item.name)) {
                        boolean cheat = ConfigRegistry.CHEAT_CATEGORIES.contains(cat)
                                || item.name.toLowerCase(Locale.ROOT).contains("fast")
                                || item.name.toLowerCase(Locale.ROOT).contains("macro")
                                || item.name.toLowerCase(Locale.ROOT).contains("auto");
                        features.put(item.name, new FeatureMeta(item.name, cat, "", cheat, item.description != null ? item.description : ""));
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Also add other known features if not caught
        ensureFeature("Sphinx Macro", "General", "", true, "Automates answering Sphinx quizzes.");
        ensureFeature("Auto Accept Carnival", "General", "", false, "Automatically accepts Carnival mini-games.");
        ensureFeature("Auto Accept NPC Lore", "General", "", false, "Skips NPC dialogs automatically.");
        ensureFeature("Auto Accept Trevor Quest", "General", "", false, "Accepts Trevor trapper animal quests automatically.");
        ensureFeature("Fuck Diorite", "General", "", true, "Mines / ignores unwanted stone blocks.");
        ensureFeature("Hitbox Expansion", "General", "", true, "Expands entity interaction hitboxes.");
        ensureFeature("Fast AOTV", "General", "", true, "Instant Aspect of the Void transmission.");
        ensureFeature("Fast Hyperion (Fast Hyp)", "General", "", true, "Instant Hyperion wither impact clicks.");
        ensureFeature("Perk Menu Clicker", "Kuudra", "", true, "Quick purchases Kuudra perks in menu.");
        ensureFeature("Auto Croesus Dungeons", "Dungeons", "", true, "Auto open Croesus chest menu.");
        ensureFeature("Auto Croesus Reroll", "Dungeons", "", true, "Auto reroll Croesus chests.");
        ensureFeature("Auto Kismet Reroll", "Dungeons", "", true, "Auto uses Kismet feather on chests.");
        ensureFeature("Dungeon Big Hitbox", "Dungeons", "", true, "Expands dungeon mob hitboxes.");
        ensureFeature("Garden Movement Master", "Garden", "", true, "Optimized garden farming movement.");
        ensureFeature("Freelook", "Hotkeys", "", true, "Freely rotate camera perspective.");
        ensureFeature("Freecam", "Hotkeys", "", true, "Move camera outside player body.");
        ensureFeature("Block Highlights Manager", "Block Highlights", "", false, "Manager and list for custom world block ESP highlights.");
        ensureFeature("Highlight Editor & Entries", "Highlights", "", false, "Manager and entries list for entity outlines and bestiary highlights.");
        ensureFeature("Chat Triggers Manager", "Chat Triggers", "", false, "Manager and rule entries for automated chat triggers.");
        ensureFeature("Entity Hider Rules", "Entity & Block Hider", "", false, "Rules to hide entities by hash or display name.");
        ensureFeature("Block Replacement Rules", "Entity & Block Hider", "", false, "Rules to visually replace blocks in world.");

        // 2. Load user customized overrides from file
        load();

        // 3. Initialize default category list if still empty
        if (customCategories.isEmpty()) {
            customCategories.addAll(ConfigRegistry.BASE_CATEGORIES);
        }
    }

    private static void ensureFeature(String name, String cat, String subCat, boolean cheat, String desc) {
        if (!features.containsKey(name)) {
            features.put(name, new FeatureMeta(name, cat, subCat, cheat, desc));
        }
    }

    public static void load() {
        if (!Files.exists(FILE_PATH)) return;
        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            Type type = new TypeToken<OrganizerData>(){}.getType();
            OrganizerData loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                if (loaded.categories != null && !loaded.categories.isEmpty()) {
                    customCategories.clear();
                    customCategories.addAll(loaded.categories);
                }
                if (loaded.subcategories != null) {
                    subcategoriesMap.clear();
                    subcategoriesMap.putAll(loaded.subcategories);
                }
                if (loaded.features != null) {
                    for (Map.Entry<String, FeatureMeta> e : loaded.features.entrySet()) {
                        if (features.containsKey(e.getKey()) && e.getValue() != null) {
                            FeatureMeta fm = features.get(e.getKey());
                            if (e.getValue().category != null && !e.getValue().category.isEmpty()) {
                                fm.category = e.getValue().category;
                            }
                            if (e.getValue().subCategory != null) {
                                fm.subCategory = e.getValue().subCategory;
                            }
                            fm.isCheat = e.getValue().isCheat;
                            if (e.getValue().description != null && !e.getValue().description.trim().isEmpty()) {
                                fm.description = e.getValue().description;
                            }
                            if (e.getValue().parentDependency != null) {
                                fm.parentDependency = e.getValue().parentDependency;
                            }
                        } else if (e.getValue() != null) {
                            features.put(e.getKey(), e.getValue());
                        }
                    }
                }
                // Cleanup deprecated or duplicate feature names
                features.remove("Test Totem (Rare Drop)");
                features.remove("Test Totem Animation");
            } else {
                // Backward compatibility: old format was Map<String, FeatureMeta>
                try (Reader reader2 = Files.newBufferedReader(FILE_PATH)) {
                    Type oldType = new TypeToken<Map<String, FeatureMeta>>(){}.getType();
                    Map<String, FeatureMeta> oldMap = GSON.fromJson(reader2, oldType);
                    if (oldMap != null) {
                        for (Map.Entry<String, FeatureMeta> e : oldMap.entrySet()) {
                            if (features.containsKey(e.getKey()) && e.getValue() != null) {
                                FeatureMeta fm = features.get(e.getKey());
                                if (e.getValue().category != null) fm.category = e.getValue().category;
                                if (e.getValue().subCategory != null) fm.subCategory = e.getValue().subCategory;
                                fm.isCheat = e.getValue().isCheat;
                            } else if (e.getValue() != null) {
                                features.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            if (!Files.exists(FILE_PATH.getParent())) {
                Files.createDirectories(FILE_PATH.getParent());
            }
            OrganizerData data = new OrganizerData();
            data.categories = new ArrayList<>(customCategories);
            data.subcategories = new LinkedHashMap<>(subcategoriesMap);
            data.features = new LinkedHashMap<>(features);

            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void reorderCategory(String cat, int targetIndex) {
        initDefaults();
        if (cat == null || !customCategories.contains(cat)) return;
        customCategories.remove(cat);
        int target = Math.max(0, Math.min(customCategories.size(), targetIndex));
        customCategories.add(target, cat);
        save();
    }

    public static boolean reorderFeatureInMemory(String featureName, String targetFeatureName) {
        initDefaults();
        if (featureName == null || targetFeatureName == null || featureName.equals(targetFeatureName)) return false;
        if (!features.containsKey(featureName) || !features.containsKey(targetFeatureName)) return false;

        List<Map.Entry<String, FeatureMeta>> entryList = new ArrayList<>(features.entrySet());
        Map.Entry<String, FeatureMeta> sourceEntry = null;
        for (int i = 0; i < entryList.size(); i++) {
            if (entryList.get(i).getKey().equals(featureName)) {
                sourceEntry = entryList.remove(i);
                break;
            }
        }
        if (sourceEntry != null) {
            int targetIdx = -1;
            for (int i = 0; i < entryList.size(); i++) {
                if (entryList.get(i).getKey().equals(targetFeatureName)) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx != -1) {
                entryList.add(targetIdx, sourceEntry);
                features.clear();
                for (Map.Entry<String, FeatureMeta> e : entryList) {
                    features.put(e.getKey(), e.getValue());
                }
                return true;
            }
        }
        return false;
    }

    public static void reorderFeature(String featureName, String targetFeatureName) {
        if (reorderFeatureInMemory(featureName, targetFeatureName)) {
            save();
        }
    }

    public static boolean reorderFeatureRelative(String featureName, String targetFeatureName, boolean insertAfter) {
        initDefaults();
        if (featureName == null || targetFeatureName == null || featureName.equals(targetFeatureName)) return false;
        if (!features.containsKey(featureName) || !features.containsKey(targetFeatureName)) return false;

        List<Map.Entry<String, FeatureMeta>> entryList = new ArrayList<>(features.entrySet());
        Map.Entry<String, FeatureMeta> sourceEntry = null;
        for (int i = 0; i < entryList.size(); i++) {
            if (entryList.get(i).getKey().equals(featureName)) {
                sourceEntry = entryList.remove(i);
                break;
            }
        }
        if (sourceEntry != null) {
            int targetIdx = -1;
            for (int i = 0; i < entryList.size(); i++) {
                if (entryList.get(i).getKey().equals(targetFeatureName)) {
                    targetIdx = i;
                    break;
                }
            }
            if (targetIdx != -1) {
                int insertIdx = insertAfter ? targetIdx + 1 : targetIdx;
                insertIdx = Math.max(0, Math.min(entryList.size(), insertIdx));
                entryList.add(insertIdx, sourceEntry);
                features.clear();
                for (Map.Entry<String, FeatureMeta> e : entryList) {
                    features.put(e.getKey(), e.getValue());
                }
                save();
                return true;
            }
        }
        return false;
    }

    public static void reorderFeature(String featureName, int targetIndex) {
        initDefaults();
        if (featureName == null || !features.containsKey(featureName)) return;
        List<Map.Entry<String, FeatureMeta>> entryList = new ArrayList<>(features.entrySet());
        Map.Entry<String, FeatureMeta> targetEntry = null;
        for (int i = 0; i < entryList.size(); i++) {
            if (entryList.get(i).getKey().equals(featureName)) {
                targetEntry = entryList.remove(i);
                break;
            }
        }
        if (targetEntry != null) {
            int target = Math.max(0, Math.min(entryList.size(), targetIndex));
            entryList.add(target, targetEntry);
            features.clear();
            for (Map.Entry<String, FeatureMeta> e : entryList) {
                features.put(e.getKey(), e.getValue());
            }
            save();
        }
    }

    public static int getCardHeight() {
        initDefaults();
        return 430;
    }

    public static List<FeatureMeta> getFilteredList() {
        initDefaults();
        List<FeatureMeta> list = new ArrayList<>();
        String q = searchFilter.trim().toLowerCase(Locale.ROOT);
        for (FeatureMeta fm : features.values()) {
            if (!categoryFilter.equals("ALL") && !fm.category.equalsIgnoreCase(categoryFilter)) {
                continue;
            }
            if (!q.isEmpty() && !fm.name.toLowerCase(Locale.ROOT).contains(q)
                    && !fm.category.toLowerCase(Locale.ROOT).contains(q)
                    && !fm.subCategory.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            list.add(fm);
        }
        return list;
    }

    private static void drawOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void renderOrganizerCard(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        initDefaults();
        lastCardX = x;
        lastCardY = y;
        lastCardW = w;
        lastCardH = h;

        // Card Container
        g.fill(x, y, x + w, y + h, 0xEE0B1120);
        drawOutline(g, x, y, w, h, 0xFF38BDF8);

        // Header
        g.text(font, "§b§lFeature Category & Cheat Organizer", x + 10, y + 8, 0xFFFFFFFF, true);
        g.text(font, "§7Manage categories, subcategories, and Cheat/Legit flags. Auto-saved to §econfig/bomboaddons/feature_organization.json", x + 10, y + 20, 0xFFAAAAAA, false);

        // Controls Bar (Search Box + Category Filter Dropdown + Add Category Button + Reorder Buttons)
        int barY = y + 34;

        // Search Box
        int searchW = 120;
        g.fill(x + 10, barY, x + 10 + searchW, barY + 16, searchBoxFocused ? 0xEE1E293B : 0xAA1E293B);
        drawOutline(g, x + 10, barY, searchW, 16, searchBoxFocused ? 0xFF38BDF8 : 0x4464748B);
        String searchDisplay = searchFilter.isEmpty() && !searchBoxFocused ? "§8Search..." : searchFilter + (searchBoxFocused && (System.currentTimeMillis() / 500 % 2 == 0) ? "_" : "");
        g.text(font, searchDisplay, x + 14, barY + 4, 0xFFFFFFFF, false);

        // Category Filter Dropdown Button
        int catFilterX = x + 10 + searchW + 6;
        int catFilterW = 110;
        boolean hoverCatF = mouseX >= catFilterX && mouseX <= catFilterX + catFilterW && mouseY >= barY && mouseY <= barY + 16;
        g.fill(catFilterX, barY, catFilterX + catFilterW, barY + 16, hoverCatF ? 0xEE334155 : 0xAA1E293B);
        drawOutline(g, catFilterX, barY, catFilterW, 16, hoverCatF ? 0xFF38BDF8 : 0x4464748B);
        String catFilterLabel = "Filter: " + (categoryFilter.length() > 8 ? categoryFilter.substring(0, 7) + ".." : categoryFilter) + " ▾";
        g.text(font, "§e" + catFilterLabel, catFilterX + 4, barY + 4, 0xFFFFFFFF, false);

        // + Add Category Button
        int addCatX = catFilterX + catFilterW + 6;
        int addCatW = 56;
        boolean hoverAddCat = mouseX >= addCatX && mouseX <= addCatX + addCatW && mouseY >= barY && mouseY <= barY + 16;
        g.fill(addCatX, barY, addCatX + addCatW, barY + 16, hoverAddCat ? 0xEE15803D : 0xAA166534);
        drawOutline(g, addCatX, barY, addCatW, 16, hoverAddCat ? 0xFF4ADE80 : 0x4422C55E);
        g.text(font, "§a+ Cat", addCatX + 8, barY + 4, 0xFFFFFFFF, false);

        // + Add Subcategory Button (for current filter category if not ALL)
        int addSubX = addCatX + addCatW + 4;
        int addSubW = 58;
        if (!categoryFilter.equals("ALL")) {
            boolean hoverAddSub = mouseX >= addSubX && mouseX <= addSubX + addSubW && mouseY >= barY && mouseY <= barY + 16;
            g.fill(addSubX, barY, addSubX + addSubW, barY + 16, hoverAddSub ? 0xEE0284C7 : 0xAA0369A1);
            drawOutline(g, addSubX, barY, addSubW, 16, hoverAddSub ? 0xFF38BDF8 : 0x440EA5E9);
            g.text(font, "§b+ Sub", addSubX + 6, barY + 4, 0xFFFFFFFF, false);
        }

        // Category Reorder (▲ Move Up, ▼ Move Down, ✖ Delete)
        if (!categoryFilter.equals("ALL")) {
            int reorderX = addSubX + addSubW + 6;
            // Move Up
            boolean hoverUp = mouseX >= reorderX && mouseX <= reorderX + 16 && mouseY >= barY && mouseY <= barY + 16;
            g.fill(reorderX, barY, reorderX + 16, barY + 16, hoverUp ? 0xEE475569 : 0xAA334155);
            drawOutline(g, reorderX, barY, 16, 16, hoverUp ? 0xFF94A3B8 : 0x4464748B);
            g.text(font, "▲", reorderX + 4, barY + 4, 0xFFFFFFFF, false);

            // Move Down
            boolean hoverDown = mouseX >= reorderX + 18 && mouseX <= reorderX + 34 && mouseY >= barY && mouseY <= barY + 16;
            g.fill(reorderX + 18, barY, reorderX + 34, barY + 16, hoverDown ? 0xEE475569 : 0xAA334155);
            drawOutline(g, reorderX + 18, barY, 16, 16, hoverDown ? 0xFF94A3B8 : 0x4464748B);
            g.text(font, "▼", reorderX + 22, barY + 4, 0xFFFFFFFF, false);

            // Delete Category
            boolean hoverDel = mouseX >= reorderX + 36 && mouseX <= reorderX + 52 && mouseY >= barY && mouseY <= barY + 16;
            g.fill(reorderX + 36, barY, reorderX + 52, barY + 16, hoverDel ? 0xEE991B1B : 0xAA7F1D1D);
            drawOutline(g, reorderX + 36, barY, 16, 16, hoverDel ? 0xFFF87171 : 0x44EF4444);
            g.text(font, "✖", reorderX + 40, barY + 4, 0xFFFFFFFF, false);
        }

        // Summary count
        List<FeatureMeta> filtered = getFilteredList();
        g.text(font, "§7Showing: §a" + filtered.size() + "§7 / §f" + features.size(), x + w - 105, barY + 4, 0xFFAAAAAA, false);

        // Input Modal for Adding Category
        if (addingCategory) {
            int modalY = barY + 20;
            g.fill(x + 10, modalY, x + w - 10, modalY + 22, 0xFF0F172A);
            drawOutline(g, x + 10, modalY, w - 20, 22, 0xFF22C55E);
            g.text(font, "§aNew Category Name: §f" + newCategoryInput + "_", x + 16, modalY + 6, 0xFFFFFFFF, false);
            g.text(font, "§7[ENTER to Confirm, ESC to Cancel]", x + w - 190, modalY + 6, 0xFFAAAAAA, false);
            return;
        }

        // Input Modal for Adding Subcategory
        if (addingSubcategory) {
            int modalY = barY + 20;
            g.fill(x + 10, modalY, x + w - 10, modalY + 22, 0xFF0F172A);
            drawOutline(g, x + 10, modalY, w - 20, 22, 0xFF0EA5E9);
            g.text(font, "§bNew Subcategory for " + targetCatForSub + ": §f" + newSubcategoryInput + "_", x + 16, modalY + 6, 0xFFFFFFFF, false);
            g.text(font, "§7[ENTER to Confirm, ESC to Cancel]", x + w - 190, modalY + 6, 0xFFAAAAAA, false);
            return;
        }

        // Entries List Viewport
        int listY = barY + 22;
        int listH = h - 64;
        int entryH = 22;
        int visibleCount = listH / entryH;

        g.fill(x + 8, listY, x + w - 8, listY + listH, 0x660F172A);
        drawOutline(g, x + 8, listY, w - 16, listH, 0x22475569);

        int maxScroll = Math.max(0, filtered.size() - visibleCount);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int startIdx = (int) scrollOffset;

        for (int i = 0; i < visibleCount && startIdx + i < filtered.size(); i++) {
            FeatureMeta fm = filtered.get(startIdx + i);
            int rowY = listY + 2 + i * entryH;

            boolean hoverRow = mouseX >= x + 10 && mouseX <= x + w - 10 && mouseY >= rowY && mouseY <= rowY + entryH - 2;
            if (hoverRow) {
                g.fill(x + 10, rowY, x + w - 10, rowY + entryH - 2, 0x2238BDF8);
            }

            // Feature Name
            String nameDisp = fm.name;
            if (font.width(nameDisp) > 230) {
                while (font.width(nameDisp + "..") > 230 && nameDisp.length() > 5) {
                    nameDisp = nameDisp.substring(0, nameDisp.length() - 1);
                }
                nameDisp += "..";
            }
            g.text(font, nameDisp, x + 14, rowY + 6, 0xFFFFFFFF, false);

            // Subcategory Button
            int subBtnW = 95;
            int subBtnX = x + w - subBtnW - 195;
            boolean hoverSub = mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= rowY + 2 && mouseY <= rowY + 18;
            g.fill(subBtnX, rowY + 2, subBtnX + subBtnW, rowY + 18, hoverSub ? 0xEE0369A1 : 0xAA075985);
            drawOutline(g, subBtnX, rowY + 2, subBtnW, 16, hoverSub ? 0xFF38BDF8 : 0x440284C7);
            String subDisp = fm.subCategory.isEmpty() ? "Sub: None" : "Sub: " + fm.subCategory;
            if (subDisp.length() > 12) subDisp = subDisp.substring(0, 11) + "..";
            g.text(font, "§3" + subDisp + " ▾", subBtnX + 4, rowY + 6, 0xFFFFFFFF, false);

            // Category Button
            int catBtnW = 110;
            int catBtnX = x + w - catBtnW - 80;
            boolean hoverCat = mouseX >= catBtnX && mouseX <= catBtnX + catBtnW && mouseY >= rowY + 2 && mouseY <= rowY + 18;
            g.fill(catBtnX, rowY + 2, catBtnX + catBtnW, rowY + 18, hoverCat ? 0xEE2563EB : 0xAA1E3A8A);
            drawOutline(g, catBtnX, rowY + 2, catBtnW, 16, hoverCat ? 0xFF60A5FA : 0x443B82F6);
            String catDisp = fm.category;
            if (catDisp.length() > 13) catDisp = catDisp.substring(0, 12) + "..";
            g.text(font, "§f" + catDisp + " §7▾", catBtnX + 5, rowY + 6, 0xFFFFFFFF, false);

            // Cheat / Legit Toggle Button
            int cheatBtnW = 62;
            int cheatBtnX = x + w - cheatBtnW - 14;
            boolean hoverCheat = mouseX >= cheatBtnX && mouseX <= cheatBtnX + cheatBtnW && mouseY >= rowY + 2 && mouseY <= rowY + 18;
            int cheatBg = fm.isCheat ? (hoverCheat ? 0xEEDC2626 : 0xAA991B1B) : (hoverCheat ? 0xEE16A34A : 0xAA166534);
            int cheatBorder = fm.isCheat ? 0xFFEF4444 : 0xFF22C55E;
            g.fill(cheatBtnX, rowY + 2, cheatBtnX + cheatBtnW, rowY + 18, cheatBg);
            drawOutline(g, cheatBtnX, rowY + 2, cheatBtnW, 16, cheatBorder);
            String cheatLabel = fm.isCheat ? "§c[CHEAT]" : "§a[LEGIT]";
            g.text(font, cheatLabel, cheatBtnX + (cheatBtnW - font.width(cheatLabel)) / 2, rowY + 6, 0xFFFFFFFF, false);
        }

        // Scrollbar track & thumb
        if (filtered.size() > visibleCount) {
            int trackX = x + w - 6;
            int trackY = listY + 2;
            int trackH = listH - 4;
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, 0x44000000);

            float ratio = (float) visibleCount / (float) filtered.size();
            int thumbH = Math.max(12, (int) (trackH * ratio));
            int thumbY = trackY + (int) ((trackH - thumbH) * (scrollOffset / (float) maxScroll));
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF38BDF8);
        }
    }

    public static void renderDropdownOverlay(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        if (!dropdownOpen || dropdownOptions.isEmpty()) return;

        int optH = 18;
        int maxVisible = 10;
        int visibleCount = Math.min(dropdownOptions.size(), maxVisible);
        int totalH = visibleCount * optH + 4;

        g.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + totalH, 0xFA0F172A);
        drawOutline(g, dropdownX, dropdownY, dropdownW, totalH, 0xFF38BDF8);

        int maxScroll = Math.max(0, dropdownOptions.size() - visibleCount);
        dropdownScroll = Math.max(0, Math.min(dropdownScroll, maxScroll));
        int startIdx = (int) dropdownScroll;

        for (int i = 0; i < visibleCount && startIdx + i < dropdownOptions.size(); i++) {
            String opt = dropdownOptions.get(startIdx + i);
            int optY = dropdownY + 2 + i * optH;
            boolean hover = mouseX >= dropdownX && mouseX <= dropdownX + dropdownW && mouseY >= optY && mouseY <= optY + optH;
            if (hover) {
                g.fill(dropdownX + 2, optY, dropdownX + dropdownW - 2, optY + optH, 0xFF2563EB);
            }
            g.text(font, opt, dropdownX + 6, optY + 5, 0xFFFFFFFF, false);
        }
    }

    public static boolean handleClick(int x, int y, int w, int h, int mouseX, int mouseY, int button) {
        initDefaults();

        // Handle dropdown overlay click first
        if (dropdownOpen) {
            int optH = 18;
            int maxVisible = 10;
            int visibleCount = Math.min(dropdownOptions.size(), maxVisible);
            int totalH = visibleCount * optH + 4;

            if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW && mouseY >= dropdownY && mouseY <= dropdownY + totalH) {
                int clicked = (mouseY - dropdownY - 2) / optH;
                int targetIdx = (int) dropdownScroll + clicked;
                if (targetIdx >= 0 && targetIdx < dropdownOptions.size()) {
                    String selected = dropdownOptions.get(targetIdx);
                    if (isCategoryFilterDropdown) {
                        categoryFilter = selected;
                        scrollOffset = 0;
                    } else if (dropdownTargetFeature != null) {
                        if (dropdownIsSubCategory) {
                            dropdownTargetFeature.subCategory = selected.equals("[None]") ? "" : selected;
                        } else {
                            dropdownTargetFeature.category = selected;
                        }
                        save();
                    }
                }
            }
            dropdownOpen = false;
            dropdownTargetFeature = null;
            return true;
        }

        int barY = y + 34;
        int searchW = 120;

        // Search Box click
        if (mouseX >= x + 10 && mouseX <= x + 10 + searchW && mouseY >= barY && mouseY <= barY + 16) {
            searchBoxFocused = true;
            return true;
        } else {
            searchBoxFocused = false;
        }

        // Category Filter Dropdown Click
        int catFilterX = x + 10 + searchW + 6;
        int catFilterW = 110;
        if (mouseX >= catFilterX && mouseX <= catFilterX + catFilterW && mouseY >= barY && mouseY <= barY + 16) {
            dropdownOpen = true;
            isCategoryFilterDropdown = true;
            dropdownTargetFeature = null;
            dropdownX = catFilterX;
            dropdownY = barY + 18;
            dropdownW = 130;
            dropdownScroll = 0;
            dropdownOptions.clear();
            dropdownOptions.add("ALL");
            dropdownOptions.addAll(customCategories);
            return true;
        }

        // + Add Category Button Click
        int addCatX = catFilterX + catFilterW + 6;
        int addCatW = 56;
        if (mouseX >= addCatX && mouseX <= addCatX + addCatW && mouseY >= barY && mouseY <= barY + 16) {
            addingCategory = true;
            newCategoryInput = "";
            return true;
        }

        // + Add Subcategory Button Click
        int addSubX = addCatX + addCatW + 4;
        int addSubW = 58;
        if (!categoryFilter.equals("ALL") && mouseX >= addSubX && mouseX <= addSubX + addSubW && mouseY >= barY && mouseY <= barY + 16) {
            addingSubcategory = true;
            targetCatForSub = categoryFilter;
            newSubcategoryInput = "";
            return true;
        }

        // Category Reorder Buttons
        if (!categoryFilter.equals("ALL")) {
            int reorderX = addSubX + addSubW + 6;
            int curIdx = customCategories.indexOf(categoryFilter);

            // Move Up
            if (curIdx > 0 && mouseX >= reorderX && mouseX <= reorderX + 16 && mouseY >= barY && mouseY <= barY + 16) {
                Collections.swap(customCategories, curIdx, curIdx - 1);
                save();
                return true;
            }

            // Move Down
            if (curIdx >= 0 && curIdx < customCategories.size() - 1 && mouseX >= reorderX + 18 && mouseX <= reorderX + 34 && mouseY >= barY && mouseY <= barY + 16) {
                Collections.swap(customCategories, curIdx, curIdx + 1);
                save();
                return true;
            }

            // Delete Category
            if (curIdx >= 0 && mouseX >= reorderX + 36 && mouseX <= reorderX + 52 && mouseY >= barY && mouseY <= barY + 16) {
                String toRemove = customCategories.remove(curIdx);
                // Reassign orphaned features to General
                for (FeatureMeta fm : features.values()) {
                    if (fm.category.equalsIgnoreCase(toRemove)) {
                        fm.category = "General";
                    }
                }
                categoryFilter = "ALL";
                save();
                return true;
            }
        }

        // Entries List Click
        int listY = barY + 22;
        int listH = h - 64;
        int entryH = 22;
        int visibleCount = listH / entryH;

        if (mouseX >= x + 8 && mouseX <= x + w - 8 && mouseY >= listY && mouseY <= listY + listH) {
            List<FeatureMeta> filtered = getFilteredList();
            int clickedRow = (mouseY - listY - 2) / entryH;
            int targetIdx = (int) scrollOffset + clickedRow;

            if (targetIdx >= 0 && targetIdx < filtered.size()) {
                FeatureMeta fm = filtered.get(targetIdx);
                int rowY = listY + 2 + clickedRow * entryH;

                // Subcategory Button Click -> Open Dropdown
                int subBtnW = 95;
                int subBtnX = x + w - subBtnW - 195;
                if (mouseX >= subBtnX && mouseX <= subBtnX + subBtnW && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    dropdownOpen = true;
                    isCategoryFilterDropdown = false;
                    dropdownTargetFeature = fm;
                    dropdownIsSubCategory = true;
                    dropdownX = subBtnX;
                    dropdownY = rowY + 18;
                    dropdownW = 120;
                    dropdownScroll = 0;
                    dropdownOptions.clear();
                    dropdownOptions.add("[None]");
                    List<String> subs = subcategoriesMap.get(fm.category);
                    if (subs != null) dropdownOptions.addAll(subs);
                    return true;
                }

                // Category Button Click -> Open Dropdown
                int catBtnW = 110;
                int catBtnX = x + w - catBtnW - 80;
                if (mouseX >= catBtnX && mouseX <= catBtnX + catBtnW && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    dropdownOpen = true;
                    isCategoryFilterDropdown = false;
                    dropdownTargetFeature = fm;
                    dropdownIsSubCategory = false;
                    dropdownX = catBtnX;
                    dropdownY = rowY + 18;
                    dropdownW = 130;
                    dropdownScroll = 0;
                    dropdownOptions.clear();
                    dropdownOptions.addAll(customCategories);
                    return true;
                }

                // Cheat / Legit Toggle Click
                int cheatBtnW = 62;
                int cheatBtnX = x + w - cheatBtnW - 14;
                if (mouseX >= cheatBtnX && mouseX <= cheatBtnX + cheatBtnW && mouseY >= rowY + 2 && mouseY <= rowY + 18) {
                    fm.isCheat = !fm.isCheat;
                    save();
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean handleKey(int keyCode, int scanCode, int modifiers) {
        if (addingCategory) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!newCategoryInput.trim().isEmpty() && !customCategories.contains(newCategoryInput.trim())) {
                    customCategories.add(newCategoryInput.trim());
                    categoryFilter = newCategoryInput.trim();
                    save();
                }
                addingCategory = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                addingCategory = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!newCategoryInput.isEmpty()) {
                    newCategoryInput = newCategoryInput.substring(0, newCategoryInput.length() - 1);
                }
                return true;
            }
        }

        if (addingSubcategory) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (!newSubcategoryInput.trim().isEmpty() && !targetCatForSub.isEmpty()) {
                    subcategoriesMap.computeIfAbsent(targetCatForSub, k -> new ArrayList<>());
                    List<String> list = subcategoriesMap.get(targetCatForSub);
                    if (!list.contains(newSubcategoryInput.trim())) {
                        list.add(newSubcategoryInput.trim());
                        save();
                    }
                }
                addingSubcategory = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                addingSubcategory = false;
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!newSubcategoryInput.isEmpty()) {
                    newSubcategoryInput = newSubcategoryInput.substring(0, newSubcategoryInput.length() - 1);
                }
                return true;
            }
        }

        if (searchBoxFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchFilter.isEmpty()) {
                    searchFilter = searchFilter.substring(0, searchFilter.length() - 1);
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchBoxFocused = false;
                return true;
            }
        }
        return false;
    }

    public static boolean handleChar(char codepoint) {
        if (addingCategory) {
            if (codepoint >= 32 && codepoint != 127 && newCategoryInput.length() < 24) {
                newCategoryInput += codepoint;
                return true;
            }
        }
        if (addingSubcategory) {
            if (codepoint >= 32 && codepoint != 127 && newSubcategoryInput.length() < 24) {
                newSubcategoryInput += codepoint;
                return true;
            }
        }
        if (searchBoxFocused) {
            if (codepoint >= 32 && codepoint != 127) {
                searchFilter += codepoint;
                return true;
            }
        }
        return false;
    }

    public static boolean handleScrolled(double amount) {
        if (dropdownOpen) {
            dropdownScroll = Math.max(0, dropdownScroll - (float) amount);
            return true;
        }
        scrollOffset = Math.max(0, scrollOffset - (float) amount * 2.5f);
        return true;
    }
}
