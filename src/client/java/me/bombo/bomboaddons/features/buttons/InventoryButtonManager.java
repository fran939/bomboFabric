package me.bombo.bomboaddons.features.buttons;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.TotemAnimationManager;
import me.bombo.bomboaddons.features.sounds.CustomSoundManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

public class InventoryButtonManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("inventory_buttons.json");

    private static Map<String, List<InventoryButton>> profileButtons = new LinkedHashMap<>();
    private static boolean initialized = false;

    public static void init() {
        if (!initialized) {
            load();
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

    public static Set<String> getProfiles() {
        init();
        return profileButtons.keySet();
    }

    public static boolean isGeneral(InventoryButton b) {
        if (b == null) return false;
        String f = b.profileFilter;
        return f == null || f.equalsIgnoreCase("ALL") || f.equalsIgnoreCase("General") || f.equalsIgnoreCase("General (all)");
    }

    public static void syncGeneralButtonsAcrossProfiles() {
        Map<String, InventoryButton> generalButtons = new LinkedHashMap<>();
        String active = getActiveProfile();

        // 1. Gather all General (all) buttons, prioritizing active profile first
        List<InventoryButton> activeList = profileButtons.get(active);
        if (activeList != null) {
            for (InventoryButton b : activeList) {
                if (isGeneral(b)) {
                    String key = (b.name != null ? b.name : "") + "|" + (b.command != null ? b.command : "");
                    generalButtons.put(key, b);
                }
            }
        }

        for (Map.Entry<String, List<InventoryButton>> entry : profileButtons.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(active)) continue;
            for (InventoryButton b : entry.getValue()) {
                if (isGeneral(b)) {
                    String key = (b.name != null ? b.name : "") + "|" + (b.command != null ? b.command : "");
                    if (!generalButtons.containsKey(key)) {
                        generalButtons.put(key, b);
                    }
                }
            }
        }

        // 2. Synchronize into all profiles
        if (!generalButtons.isEmpty()) {
            for (Map.Entry<String, List<InventoryButton>> entry : profileButtons.entrySet()) {
                List<InventoryButton> list = entry.getValue();
                if (list == null) {
                    list = new ArrayList<>();
                    entry.setValue(list);
                }

                // If profile had only untouched default 7 buttons, clear them so user's custom general buttons replace them
                if (isOnlyOldDefaults(list)) {
                    list.clear();
                }

                // Keep only profile-specific buttons
                List<InventoryButton> specific = new ArrayList<>();
                for (InventoryButton b : list) {
                    if (!isGeneral(b)) specific.add(b);
                }

                list.clear();
                list.addAll(generalButtons.values());
                list.addAll(specific);
            }
        }
    }

    private static boolean isOnlyOldDefaults(List<InventoryButton> list) {
        if (list == null || list.size() != 7) return false;
        String[] defaultNames = {"Hub", "Storage", "Bazaar", "Auction House", "Pets", "Wardrobe", "Craft"};
        for (int i = 0; i < 7; i++) {
            if (!list.get(i).name.equalsIgnoreCase(defaultNames[i])) return false;
        }
        return true;
    }

    public static List<InventoryButton> getButtonsForActiveProfile() {
        init();
        String prof = getActiveProfile();
        List<InventoryButton> list = profileButtons.get(prof);
        if (list == null) {
            syncGeneralButtonsAcrossProfiles();
            List<InventoryButton> generals = new ArrayList<>();
            for (List<InventoryButton> pList : profileButtons.values()) {
                for (InventoryButton b : pList) {
                    if (isGeneral(b) && !generals.contains(b)) generals.add(b);
                }
            }
            if (!generals.isEmpty()) {
                list = new ArrayList<>(generals);
            } else {
                list = createDefaultButtons();
            }
            profileButtons.put(prof, list);
            save();
        } else {
            syncGeneralButtonsAcrossProfiles();
        }
        return list;
    }

    public static List<InventoryButton> createDefaultButtons() {
        List<InventoryButton> defaults = new ArrayList<>();
        defaults.add(new InventoryButton("Hub", "/warp hub", "minecraft:compass", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, -40, 18, true, false));
        defaults.add(new InventoryButton("Storage", "/storage", "minecraft:chest", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, -18, 18, true, false));
        defaults.add(new InventoryButton("Bazaar", "/bz", "minecraft:emerald", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, 4, 18, true, false));
        defaults.add(new InventoryButton("Auction House", "/ah", "minecraft:gold_ingot", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, 26, 18, true, false));
        defaults.add(new InventoryButton("Pets", "/pets", "minecraft:bone", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, 48, 18, true, false));
        defaults.add(new InventoryButton("Wardrobe", "/wardrobe", "minecraft:leather_chestplate", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, 70, 18, true, false));
        defaults.add(new InventoryButton("Craft", "/craft", "minecraft:crafting_table", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, 92, 18, true, false));

        for (InventoryButton b : defaults) {
            b.cachedStack = TotemAnimationManager.resolveItemStack(b.icon);
        }
        return defaults;
    }

    public static void resetDefaultsForProfile(String profile) {
        profileButtons.put(profile, createDefaultButtons());
        save();
    }

    public static void load() {
        File file = CONFIG_PATH.toFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Type type = new TypeToken<Map<String, List<InventoryButton>>>() {}.getType();
                Map<String, List<InventoryButton>> loaded = GSON.fromJson(reader, type);
                if (loaded != null) {
                    profileButtons = loaded;
                    for (List<InventoryButton> list : profileButtons.values()) {
                        for (InventoryButton b : list) {
                            if (b.profileFilter == null) b.profileFilter = "ALL";
                            b.cachedStack = TotemAnimationManager.resolveItemStack(b.icon);
                        }
                    }
                    syncGeneralButtonsAcrossProfiles();
                }
            } catch (Exception e) {
                System.err.println("[BomboAddons] Failed to load inventory buttons: " + e.getMessage());
            }
        }

        if (profileButtons.isEmpty()) {
            profileButtons.put("default", createDefaultButtons());
            save();
        }
    }

    public static void save() {
        try {
            syncGeneralButtonsAcrossProfiles();
            File file = CONFIG_PATH.toFile();
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(profileButtons, writer);
            }
        } catch (Exception e) {
            System.err.println("[BomboAddons] Failed to save inventory buttons: " + e.getMessage());
        }
    }

    public static String exportStateJson() {
        init();
        return GSON.toJson(profileButtons);
    }

    public static void importStateJson(String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            Type type = new TypeToken<Map<String, List<InventoryButton>>>() {}.getType();
            Map<String, List<InventoryButton>> loaded = GSON.fromJson(json, type);
            if (loaded != null) {
                profileButtons = loaded;
                for (List<InventoryButton> list : profileButtons.values()) {
                    for (InventoryButton b : list) {
                        if (b.profileFilter == null) b.profileFilter = "ALL";
                        b.cachedStack = TotemAnimationManager.resolveItemStack(b.icon);
                    }
                }
                save();
            }
        } catch (Exception ignored) {}
    }

    public static int importFirmamentButtons(String jsonContent, String targetProfile, boolean append) {
        if (jsonContent == null || jsonContent.trim().isEmpty()) return 0;
        init();

        try {
            JsonElement parsed = JsonParser.parseString(jsonContent.trim());
            JsonArray array = null;
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("buttons") && obj.get("buttons").isJsonArray()) {
                    array = obj.getAsJsonArray("buttons");
                }
            } else if (parsed.isJsonArray()) {
                array = parsed.getAsJsonArray();
            }

            if (array == null || array.isEmpty()) return 0;

            // Backup existing config
            try {
                File file = CONFIG_PATH.toFile();
                if (file.exists()) {
                    File backup = new File(file.getParentFile(), "inventory_buttons_backup.json");
                    java.nio.file.Files.copy(file.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Throwable ignored) {}

            List<InventoryButton> importedList = new ArrayList<>();
            for (JsonElement elem : array) {
                if (!elem.isJsonObject()) continue;
                JsonObject bObj = elem.getAsJsonObject();

                int x = bObj.has("x") ? bObj.get("x").getAsInt() : 0;
                int y = bObj.has("y") ? bObj.get("y").getAsInt() : 0;
                boolean anchorRight = bObj.has("anchorRight") && bObj.get("anchorRight").getAsBoolean();
                boolean anchorBottom = bObj.has("anchorBottom") && bObj.get("anchorBottom").getAsBoolean();
                String icon = bObj.has("icon") ? bObj.get("icon").getAsString().trim() : "minecraft:compass";
                String command = bObj.has("command") ? bObj.get("command").getAsString().trim() : "";
                boolean isGigantic = bObj.has("isGigantic") && bObj.get("isGigantic").getAsBoolean();

                // Ensure command starts with slash
                if (!command.isEmpty() && !command.startsWith("/")) {
                    command = "/" + command;
                }

                // Map anchor
                InventoryButton.Anchor anchor;
                if (anchorRight && !anchorBottom) {
                    anchor = InventoryButton.Anchor.TOP_RIGHT;
                } else if (!anchorRight && !anchorBottom) {
                    anchor = InventoryButton.Anchor.TOP_LEFT;
                } else if (anchorRight && anchorBottom) {
                    anchor = InventoryButton.Anchor.BOTTOM_RIGHT;
                } else {
                    anchor = InventoryButton.Anchor.BOTTOM_LEFT;
                }

                int size = isGigantic ? 28 : 18;

                // Derive friendly display name
                String name = "";
                if (bObj.has("name") && !bObj.get("name").getAsString().trim().isEmpty()) {
                    name = bObj.get("name").getAsString().trim();
                } else if (bObj.has("label") && !bObj.get("label").getAsString().trim().isEmpty()) {
                    name = bObj.get("label").getAsString().trim();
                } else {
                    name = deriveFriendlyName(command, icon);
                }

                InventoryButton button = new InventoryButton(name, command, icon, anchor, x, y, size, true, false, "ALL");
                button.cachedStack = TotemAnimationManager.resolveItemStack(icon);
                importedList.add(button);
            }

            if (importedList.isEmpty()) return 0;

            String prof = (targetProfile != null && !targetProfile.trim().isEmpty()) ? targetProfile.trim() : getActiveProfile();
            List<InventoryButton> existing = profileButtons.get(prof);
            if (append && existing != null) {
                existing.addAll(importedList);
            } else {
                profileButtons.put(prof, importedList);
            }
            save();
            return importedList.size();
        } catch (Throwable t) {
            System.err.println("[BomboAddons] Error importing Firmament buttons: " + t.getMessage());
            t.printStackTrace();
            return 0;
        }
    }

    private static String deriveFriendlyName(String command, String icon) {
        String cleanCmd = command != null ? command.replaceFirst("^/", "").trim() : "";
        if (cleanCmd.equalsIgnoreCase("hex")) return "Hex";
        if (cleanCmd.equalsIgnoreCase("ah")) return "Auction House";
        if (cleanCmd.equalsIgnoreCase("bz")) return "Bazaar";
        if (cleanCmd.equalsIgnoreCase("be")) return "Bestiary";
        if (cleanCmd.equalsIgnoreCase("trades")) return "Trades";
        if (cleanCmd.equalsIgnoreCase("enchantingtable")) return "Enchanting Table";
        if (cleanCmd.equalsIgnoreCase("accessory")) return "Accessory Bag";
        if (cleanCmd.equalsIgnoreCase("fishingbag")) return "Fishing Bag";
        if (cleanCmd.equalsIgnoreCase("potionbag")) return "Potion Bag";
        if (cleanCmd.equalsIgnoreCase("sacks")) return "Sacks";
        if (cleanCmd.equalsIgnoreCase("attributemenu")) return "Attribute Menu";
        if (cleanCmd.equalsIgnoreCase("chocolatefactory")) return "Chocolate Factory";
        if (cleanCmd.equalsIgnoreCase("pets")) return "Pets";
        if (cleanCmd.equalsIgnoreCase("craft")) return "Craft";
        if (cleanCmd.equalsIgnoreCase("wardrobe")) return "Wardrobe";
        if (cleanCmd.equalsIgnoreCase("storage")) return "Storage";
        if (cleanCmd.equalsIgnoreCase("rng")) return "RNG Meter";
        if (cleanCmd.equalsIgnoreCase("bank")) return "Bank";
        if (cleanCmd.equalsIgnoreCase("skyblocklevels")) return "SB Levels";
        if (cleanCmd.equalsIgnoreCase("stats")) return "Stats";
        if (cleanCmd.equalsIgnoreCase("desk")) return "Desk";
        if (cleanCmd.startsWith("call ")) {
            String npc = cleanCmd.substring(5).trim();
            if (!npc.isEmpty()) return "Call " + Character.toUpperCase(npc.charAt(0)) + npc.substring(1);
        }
        if (cleanCmd.startsWith("pv ")) {
            return "PV " + cleanCmd.substring(3).trim();
        }

        if (!cleanCmd.isEmpty()) {
            String[] words = cleanCmd.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.length; i++) {
                if (words[i].isEmpty()) continue;
                if (i > 0) sb.append(" ");
                sb.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
            }
            return sb.toString();
        }

        if (icon != null && !icon.isEmpty()) {
            String cleanIcon = icon.contains(":") ? icon.substring(icon.indexOf(':') + 1) : icon;
            return cleanIcon.replace('_', ' ');
        }
        return "Button";
    }

    public static String readImportSource(String source) {
        // 1. If explicit file or inline JSON
        if (source != null && !source.trim().isEmpty()) {
            String trimmed = source.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                return trimmed;
            }
            File f = new File(trimmed);
            if (f.exists() && f.isFile()) {
                try {
                    return java.nio.file.Files.readString(f.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (Throwable ignored) {}
            }
        }

        // 2. Check Clipboard
        try {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip != null && !clip.trim().isEmpty()) {
                String c = clip.trim();
                if ((c.startsWith("{") && c.contains("buttons")) || (c.startsWith("[") && c.contains("anchorRight"))) {
                    return c;
                }
            }
        } catch (Throwable ignored) {}

        // 3. Check default config paths
        Path[] searchPaths = new Path[]{
            FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("firmament_buttons.json"),
            FabricLoader.getInstance().getConfigDir().resolve("bomboaddons").resolve("import_buttons.json"),
            FabricLoader.getInstance().getConfigDir().resolve("firmament").resolve("buttons.json")
        };

        for (Path p : searchPaths) {
            if (java.nio.file.Files.exists(p) && java.nio.file.Files.isRegularFile(p)) {
                try {
                    return java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
                } catch (Throwable ignored) {}
            }
        }

        return null;
    }

    public static int handleImportCommand(String source) {
        String json = readImportSource(source);
        Minecraft mc = Minecraft.getInstance();
        if (json == null) {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cNo button JSON found! Copy JSON to clipboard or place file in §econfig/bomboaddons/firmament_buttons.json"));
            }
            return 0;
        }

        int count = importFirmamentButtons(json, getActiveProfile(), false);
        if (mc.player != null) {
            if (count > 0) {
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §aSuccessfully imported §e" + count + " §abuttons from Firmament into profile §e" + getActiveProfile() + "§a!"));
            } else {
                mc.player.sendSystemMessage(Component.literal("§8[§bBombo§8] §cFailed to parse Firmament buttons JSON. Please verify the format."));
            }
        }
        return count;
    }

    public static void renderButtons(GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, int mouseX, int mouseY, int leftPos, int topPos, int imageWidth, int imageHeight) {
        init();
        List<InventoryButton> buttons = getButtonsForActiveProfile();
        boolean isInventoryScreen = screen instanceof InventoryScreen;
        String activeProfile = getActiveProfile();
        Font font = Minecraft.getInstance().font;

        InventoryButton hoveredButton = null;
        int hoveredX = 0;
        int hoveredY = 0;

        for (InventoryButton b : buttons) {
            if (!b.enabled) continue;
            if (b.profileFilter != null && !b.profileFilter.equalsIgnoreCase("ALL") && !b.profileFilter.equalsIgnoreCase("General") && !b.profileFilter.equalsIgnoreCase("General (all)") && !b.profileFilter.equalsIgnoreCase(activeProfile)) {
                continue;
            }
            if (b.onlyInInventory && !isInventoryScreen) continue;

            int bx = b.getX(leftPos, imageWidth);
            int by = b.getY(topPos, imageHeight);
            boolean hover = mouseX >= bx && mouseX <= bx + b.size && mouseY >= by && mouseY <= by + b.size;

            // Render background and border according to style
            InventoryButton.BackgroundStyle style = b.backgroundStyle != null ? b.backgroundStyle : InventoryButton.BackgroundStyle.MODERN;
            switch (style) {
                case MODERN -> {
                    int bg = hover ? 0xDD1E293B : 0xAA0F172A;
                    int border = hover ? 0xFF38BDF8 : 0x4464748B;
                    g.fill(bx, by, bx + b.size, by + b.size, bg);
                    if (b.showBorder) {
                        g.outline(bx, by, b.size, b.size, border);
                    }
                }
                case VANILLA -> {
                    g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                            net.minecraft.resources.Identifier.withDefaultNamespace(hover ? "widget/button_highlighted" : "widget/button"),
                            bx, by, b.size, b.size);
                }
                case SLOT -> {
                    g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                            net.minecraft.resources.Identifier.withDefaultNamespace("container/slot"),
                            bx - 1, by - 1, b.size + 2, b.size + 2);
                    if (hover) {
                        g.fill(bx, by, bx + b.size, by + b.size, 0x55FFFFFF);
                    }
                }
                case CUSTOM -> {
                    int bg = hover ? b.customHoverBgColor : b.customBgColor;
                    g.fill(bx, by, bx + b.size, by + b.size, bg);
                    if (b.showBorder) {
                        int border = hover ? b.customHoverBorderColor : b.customBorderColor;
                        g.outline(bx, by, b.size, b.size, border);
                    }
                }
                case INVISIBLE -> {
                    if (b.showBorder) {
                        int border = hover ? b.customHoverBorderColor : b.customBorderColor;
                        g.outline(bx, by, b.size, b.size, border);
                    }
                }
            }

            // Render item icon
            if (b.cachedStack == null || b.cachedStack.isEmpty()) {
                b.cachedStack = TotemAnimationManager.resolveItemStack(b.icon);
            }
            if (b.cachedStack != null && !b.cachedStack.isEmpty()) {
                int itemOffX = bx + (b.size - 16) / 2;
                int itemOffY = by + (b.size - 16) / 2;
                g.item(b.cachedStack, itemOffX, itemOffY);
            }

            if (hover) {
                hoveredButton = b;
                hoveredX = bx;
                hoveredY = by;
            }
        }

        // Render hover tooltip on top
        if (hoveredButton != null) {
            String tooltipMode = BomboConfig.get().inventoryButtonTooltipMode != null ? BomboConfig.get().inventoryButtonTooltipMode.toUpperCase(java.util.Locale.ROOT) : "FULL";
            if (!tooltipMode.equals("NONE")) {
                String label = (hoveredButton.name != null && !hoveredButton.name.isEmpty()) ? hoveredButton.name : hoveredButton.command;
                String cmd = hoveredButton.command != null ? hoveredButton.command : "";
                String line1 = "§b" + label;
                String line2 = !cmd.isEmpty() ? "§7Left-Click: §e" + (cmd.startsWith("/") ? cmd : "/" + cmd) : "";
                String line3 = "";
                if (hoveredButton.rightClickAction == InventoryButton.RightClickAction.EDIT_BUTTON) {
                    line3 = "§dRight-Click: §eEdit Button";
                } else if (hoveredButton.rightClickAction == InventoryButton.RightClickAction.RUN_COMMAND && hoveredButton.rightClickCommand != null && !hoveredButton.rightClickCommand.trim().isEmpty()) {
                    String rCmd = hoveredButton.rightClickCommand.trim();
                    line3 = "§dRight-Click: §e" + (rCmd.startsWith("/") ? rCmd : "/" + rCmd);
                }
                String line4 = "";
                if (hoveredButton.middleClickAction == InventoryButton.MiddleClickAction.RUN_COMMAND && hoveredButton.middleClickCommand != null && !hoveredButton.middleClickCommand.trim().isEmpty()) {
                    String mCmd = hoveredButton.middleClickCommand.trim();
                    line4 = "§aMiddle-Click: §e" + (mCmd.startsWith("/") ? mCmd : "/" + mCmd);
                }

                List<String> displayLines = new ArrayList<>();
                if (!tooltipMode.equals("CLICKS_ONLY")) {
                    displayLines.add(line1);
                }
                if (!tooltipMode.equals("NAME_ONLY")) {
                    if (!line2.isEmpty()) displayLines.add(line2);
                    if (!line3.isEmpty()) displayLines.add(line3);
                    if (!line4.isEmpty()) displayLines.add(line4);
                }

                if (!displayLines.isEmpty()) {
                    int maxW = 0;
                    for (String l : displayLines) {
                        maxW = Math.max(maxW, font.width(l));
                    }
                    int tX = mouseX + 10;
                    int tY = mouseY - 10;
                    int tH = displayLines.size() * 11 + 6;

                    g.fill(tX - 3, tY - 3, tX + maxW + 5, tY + tH, 0xF00F172A);
                    g.outline(tX - 3, tY - 3, maxW + 8, tH + 3, 0xFF38BDF8);
                    int curLineY = tY + 2;
                    for (String l : displayLines) {
                        g.text(font, l, tX, curLineY, 0xFFFFFFFF, true);
                        curLineY += 11;
                    }
                }
            }
        }
    }

    public static boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int mouseButton, int leftPos, int topPos, int imageWidth, int imageHeight) {
        if (mouseButton != 0 && mouseButton != 1 && mouseButton != 2) return false;
        init();
        List<InventoryButton> buttons = getButtonsForActiveProfile();
        boolean isInventoryScreen = screen instanceof InventoryScreen;
        String activeProfile = getActiveProfile();

        for (InventoryButton b : buttons) {
            if (!b.enabled) continue;
            if (b.profileFilter != null && !b.profileFilter.equalsIgnoreCase("ALL") && !b.profileFilter.equalsIgnoreCase("General") && !b.profileFilter.equalsIgnoreCase("General (all)") && !b.profileFilter.equalsIgnoreCase(activeProfile)) {
                continue;
            }
            if (b.onlyInInventory && !isInventoryScreen) continue;

            int bx = b.getX(leftPos, imageWidth);
            int by = b.getY(topPos, imageHeight);
            if (mouseX >= bx && mouseX <= bx + b.size && mouseY >= by && mouseY <= by + b.size) {
                // Play custom or vanilla button click sound if configured
                if (b.clickSound != null && !b.clickSound.trim().isEmpty()) {
                    CustomSoundManager.playCustomOrVanillaSound(b.clickSound.trim(), 1.0f, 1.0f);
                }

                if (mouseButton == 0) {
                    // Left click
                    String cmd = b.command != null ? b.command.trim() : "";
                    if (!cmd.isEmpty()) {
                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null && mc.player.connection != null) {
                            mc.player.connection.sendCommand(cmd);
                        }
                    }
                    return true;
                } else if (mouseButton == 1) {
                    // Right click
                    if (b.rightClickAction == InventoryButton.RightClickAction.EDIT_BUTTON) {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> mc.setScreenAndShow(new InventoryButtonsScreen(screen, b)));
                        return true;
                    } else if (b.rightClickAction == InventoryButton.RightClickAction.RUN_COMMAND) {
                        String rcmd = b.rightClickCommand != null ? b.rightClickCommand.trim() : "";
                        if (!rcmd.isEmpty()) {
                            if (rcmd.startsWith("/")) rcmd = rcmd.substring(1);
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null && mc.player.connection != null) {
                                mc.player.connection.sendCommand(rcmd);
                            }
                        }
                        return true;
                    }
                } else if (mouseButton == 2) {
                    // Middle click
                    if (b.middleClickAction == InventoryButton.MiddleClickAction.RUN_COMMAND) {
                        String mcmd = b.middleClickCommand != null ? b.middleClickCommand.trim() : "";
                        if (!mcmd.isEmpty()) {
                            if (mcmd.startsWith("/")) mcmd = mcmd.substring(1);
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null && mc.player.connection != null) {
                                mc.player.connection.sendCommand(mcmd);
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
