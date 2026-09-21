package me.bombo.bomboaddons.features.buttons;

import me.bombo.bomboaddons.SkyblockItemManager;
import me.bombo.bomboaddons.features.TotemAnimationManager;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryButtonsScreen extends Screen {
    private final Screen parent;

    // Window layout
    private int winX, winY, winW, winH;
    private final int headerH = 40;
    private final int leftPanelW = 280;

    // Scroll
    private double listScroll = 0.0;
    private double maxListScroll = 0.0;

    // Selected Button & Editor State
    private InventoryButton selectedButton = null;
    private String editName = "";
    private String editCommand = "";
    private InventoryButton.RightClickAction editRightClickAction = InventoryButton.RightClickAction.NONE;
    private String editRightClickCommand = "";
    private InventoryButton.MiddleClickAction editMiddleClickAction = InventoryButton.MiddleClickAction.NONE;
    private String editMiddleClickCommand = "";
    private String editClickSound = "";
    private String editIcon = "";
    private InventoryButton.Anchor editAnchor = InventoryButton.Anchor.PLAYER_INV_RIGHT;
    private int editOffsetX = 4;
    private int editOffsetY = 0;
    private int editSize = 18;
    private boolean editEnabled = true;
    private boolean editOnlyInInventory = false;
    private String editProfileFilter = "ALL";

    private InventoryButton.BackgroundStyle editBackgroundStyle = InventoryButton.BackgroundStyle.MODERN;
    private int editCustomBgColor = 0xAA0F172A;
    private int editCustomHoverBgColor = 0xDD1E293B;
    private boolean editShowBorder = true;
    private int editCustomBorderColor = 0x4464748B;
    private int editCustomHoverBorderColor = 0xFF38BDF8;

    // 0 = Name, 1 = Command, 2 = Icon, 3 = RightClickCommand, 4 = MiddleClickCommand, 5 = ClickSound
    private int focusField = 0;
    private int cursorPos = 0;
    private boolean selectAllOnType = false;

    private int cachedIconBoxY = 0;
    private int cachedProfBoxY = 0;
    private int cachedSoundBoxY = 0;

    // Steppers continuous hold
    private int activeStepper = 0; // 1=decX, 2=incX, 3=decY, 4=incY, 5=decSize, 6=incSize
    private long stepperPressTime = 0L;
    private long lastStepTime = 0L;

    // Item Autocompletion dropdown
    private final List<String> currentIconSuggestions = new ArrayList<>();
    private int selectedSuggestionIdx = 0;
    private int suggestionScrollOffset = 0;
    private static final int MAX_VISIBLE_SUGGESTIONS = 7;

    // Sound Autocompletion dropdown
    private final List<String> currentSoundSuggestions = new ArrayList<>();
    private int selectedSoundSuggestionIdx = 0;
    private int soundSuggestionScrollOffset = 0;

    // Profile Picker Dropdown
    private boolean showProfilePicker = false;
    private int profileScrollOffset = 0;

    // Search Query in Left Sidebar
    private String searchQuery = "";
    private int searchCursorPos = 0;
    private boolean searchFocused = false;
    private boolean searchSelectAll = false;
    private final java.util.Deque<String> searchUndoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<String> searchRedoStack = new java.util.ArrayDeque<>();

    private void pushSearchUndo() {
        searchUndoStack.push(searchQuery);
        if (searchUndoStack.size() > 50) searchUndoStack.removeLast();
        searchRedoStack.clear();
    }

    // Text field undo / redo stacks for button editor
    private final java.util.Map<Integer, java.util.Deque<String>> fieldUndoStacks = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.Deque<String>> fieldRedoStacks = new java.util.HashMap<>();

    private void pushFieldUndo(int field) {
        fieldUndoStacks.computeIfAbsent(field, k -> new java.util.ArrayDeque<>()).push(getFieldText(field));
        java.util.Deque<String> stack = fieldUndoStacks.get(field);
        if (stack.size() > 50) stack.removeLast();
        java.util.Deque<String> redo = fieldRedoStacks.get(field);
        if (redo != null) redo.clear();
    }

    // Undo / Redo Stacks
    private final java.util.Deque<String> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<String> redoStack = new java.util.ArrayDeque<>();

    private void pushUndoState() {
        try {
            String state = InventoryButtonManager.exportStateJson();
            undoStack.push(state);
            redoStack.clear();
            if (undoStack.size() > 50) {
                undoStack.removeLast();
            }
        } catch (Throwable ignored) {}
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            String current = InventoryButtonManager.exportStateJson();
            redoStack.push(current);
            String prev = undoStack.pop();
            InventoryButtonManager.importStateJson(prev);
            this.statusBanner = "§aUndo successful!";
            this.statusBannerTime = System.currentTimeMillis();
            List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
            selectButton(!list.isEmpty() ? list.get(0) : null);
        } else {
            this.statusBanner = "§cNothing to undo!";
            this.statusBannerTime = System.currentTimeMillis();
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            String current = InventoryButtonManager.exportStateJson();
            undoStack.push(current);
            String nxt = redoStack.pop();
            InventoryButtonManager.importStateJson(nxt);
            this.statusBanner = "§aRedo successful!";
            this.statusBannerTime = System.currentTimeMillis();
            List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
            selectButton(!list.isEmpty() ? list.get(0) : null);
        } else {
            this.statusBanner = "§cNothing to redo!";
            this.statusBannerTime = System.currentTimeMillis();
        }
    }

    // Status Banner
    private String statusBanner = "";
    private long statusBannerTime = 0L;

    private List<String> getAvailableProfiles() {
        List<String> list = new ArrayList<>();
        list.add("General (all)");
        list.add("default");
        me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
        if (s != null && s.profileBinds != null) {
            for (String p : s.profileBinds.keySet()) {
                if (p == null || p.trim().isEmpty()) continue;
                if (p.equalsIgnoreCase("General") || p.equalsIgnoreCase("default")) continue;
                if (!list.contains(p)) list.add(p);
            }
        }
        return list;
    }
    public InventoryButtonsScreen(Screen parent) {
        super(Component.literal("Inventory Buttons Configuration"));
        this.parent = parent;
    }

    public InventoryButtonsScreen(Screen parent, InventoryButton buttonToSelect) {
        this(parent);
        this.selectedButton = buttonToSelect;
        if (buttonToSelect != null) {
            selectButton(buttonToSelect);
        }
    }

    @Override
    protected void init() {
        InventoryButtonManager.init();
        int targetW = 860;
        int targetH = 520;
        this.winW = Math.min(this.width - 24, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
        if (selectedButton == null && !list.isEmpty()) {
            selectButton(list.get(0));
        }
    }

    private void selectButton(InventoryButton b) {
        this.selectedButton = b;
        if (b != null) {
            this.editName = b.name != null ? b.name : "";
            this.editCommand = b.command != null ? b.command : "";
            this.editRightClickAction = b.rightClickAction != null ? b.rightClickAction : InventoryButton.RightClickAction.NONE;
            this.editRightClickCommand = b.rightClickCommand != null ? b.rightClickCommand : "";
            this.editMiddleClickAction = b.middleClickAction != null ? b.middleClickAction : InventoryButton.MiddleClickAction.NONE;
            this.editMiddleClickCommand = b.middleClickCommand != null ? b.middleClickCommand : "";
            this.editClickSound = b.clickSound != null ? b.clickSound : "";
            this.editIcon = b.icon != null ? b.icon : "";
            this.editAnchor = b.anchor != null ? b.anchor : InventoryButton.Anchor.PLAYER_INV_RIGHT;
            this.editOffsetX = b.offsetX;
            this.editOffsetY = b.offsetY;
            this.editSize = b.size > 0 ? b.size : 18;
            this.editEnabled = b.enabled;
            this.editOnlyInInventory = b.onlyInInventory;
            this.editProfileFilter = b.profileFilter != null ? b.profileFilter : "ALL";
            this.editBackgroundStyle = b.backgroundStyle != null ? b.backgroundStyle : InventoryButton.BackgroundStyle.MODERN;
            this.editCustomBgColor = b.customBgColor;
            this.editCustomHoverBgColor = b.customHoverBgColor;
            this.editShowBorder = b.showBorder;
            this.editCustomBorderColor = b.customBorderColor;
            this.editCustomHoverBorderColor = b.customHoverBorderColor;
            this.focusField = 0;
            this.cursorPos = editName.length();
            this.selectAllOnType = false;
            updateIconSuggestions();
            updateSoundSuggestions();
        }
    }

    private void saveCurrentEditor() {
        if (selectedButton != null) {
            selectedButton.name = editName.trim();
            selectedButton.command = editCommand.trim();
            selectedButton.rightClickAction = !editRightClickCommand.trim().isEmpty() ? InventoryButton.RightClickAction.RUN_COMMAND : editRightClickAction;
            selectedButton.rightClickCommand = editRightClickCommand.trim();
            selectedButton.middleClickAction = !editMiddleClickCommand.trim().isEmpty() ? InventoryButton.MiddleClickAction.RUN_COMMAND : InventoryButton.MiddleClickAction.NONE;
            selectedButton.middleClickCommand = editMiddleClickCommand.trim();
            selectedButton.clickSound = editClickSound.trim();
            selectedButton.icon = editIcon.trim();
            selectedButton.anchor = editAnchor;
            selectedButton.offsetX = editOffsetX;
            selectedButton.offsetY = editOffsetY;
            selectedButton.size = Math.max(12, Math.min(48, editSize));
            selectedButton.enabled = editEnabled;
            selectedButton.onlyInInventory = editOnlyInInventory;
            selectedButton.profileFilter = editProfileFilter.trim().isEmpty() ? "ALL" : editProfileFilter.trim();
            selectedButton.backgroundStyle = editBackgroundStyle;
            selectedButton.customBgColor = editCustomBgColor;
            selectedButton.customHoverBgColor = editCustomHoverBgColor;
            selectedButton.showBorder = editShowBorder;
            selectedButton.customBorderColor = editCustomBorderColor;
            selectedButton.customHoverBorderColor = editCustomHoverBorderColor;
            selectedButton.cachedStack = TotemAnimationManager.resolveItemStack(selectedButton.icon);
            InventoryButtonManager.save();
        }
    }

    private void updateIconSuggestions() {
        currentIconSuggestions.clear();
        String q = editIcon.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;

        // Check SkyBlock item cache
        try {
            Map<String, SkyblockItemManager.SkyblockItemInfo> cache = SkyblockItemManager.getItemCache();
            if (cache != null) {
                for (SkyblockItemManager.SkyblockItemInfo info : cache.values()) {
                    if (currentIconSuggestions.size() >= 100) break;
                    if (info.id != null && info.id.toLowerCase(Locale.ROOT).contains(q)) {
                        if (!currentIconSuggestions.contains(info.id)) currentIconSuggestions.add(info.id);
                    } else if (info.name != null && info.name.toLowerCase(Locale.ROOT).contains(q)) {
                        if (!currentIconSuggestions.contains(info.id)) currentIconSuggestions.add(info.id);
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Check Vanilla item registry
        if (currentIconSuggestions.size() < 100) {
            for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
                if (currentIconSuggestions.size() >= 100) break;
                String path = id.getPath().toLowerCase(Locale.ROOT);
                String full = id.toString().toLowerCase(Locale.ROOT);
                if (path.contains(q) || full.contains(q)) {
                    String toAdd = id.toString();
                    if (!currentIconSuggestions.contains(toAdd)) currentIconSuggestions.add(toAdd);
                }
            }
        }

        if (selectedSuggestionIdx >= currentIconSuggestions.size()) {
            selectedSuggestionIdx = 0;
        }
        if (suggestionScrollOffset > Math.max(0, currentIconSuggestions.size() - MAX_VISIBLE_SUGGESTIONS)) {
            suggestionScrollOffset = Math.max(0, currentIconSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        }
    }

    private void updateSoundSuggestions() {
        currentSoundSuggestions.clear();
        String q = editClickSound.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return;

        for (java.io.File f : me.bombo.bomboaddons.features.sounds.CustomSoundManager.getCustomSoundFiles()) {
            if (currentSoundSuggestions.size() >= 100) break;
            String fn = f.getName();
            if (fn.toLowerCase(Locale.ROOT).contains(q)) {
                if (!currentSoundSuggestions.contains(fn)) currentSoundSuggestions.add(fn);
            }
        }
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            if (currentSoundSuggestions.size() >= 100) break;
            String str = id.toString().toLowerCase(Locale.ROOT);
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (str.contains(q) || path.contains(q)) {
                String toAdd = id.toString();
                if (!currentSoundSuggestions.contains(toAdd)) currentSoundSuggestions.add(toAdd);
            }
        }

        if (selectedSoundSuggestionIdx >= currentSoundSuggestions.size()) {
            selectedSoundSuggestionIdx = 0;
        }
        if (soundSuggestionScrollOffset > Math.max(0, currentSoundSuggestions.size() - MAX_VISIBLE_SUGGESTIONS)) {
            soundSuggestionScrollOffset = Math.max(0, currentSoundSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        }
    }

    private void applyStepperStep(int id, boolean shift) {
        int delta = shift ? 10 : 1;
        switch (id) {
            case 1 -> editOffsetX -= delta;
            case 2 -> editOffsetX += delta;
            case 3 -> editOffsetY -= delta;
            case 4 -> editOffsetY += delta;
            case 5 -> editSize = Math.max(12, editSize - (shift ? 4 : 1));
            case 6 -> editSize = Math.min(48, editSize + (shift ? 4 : 1));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Continuous stepper holding
        if (activeStepper > 0) {
            long now = System.currentTimeMillis();
            if (now - stepperPressTime > 250L && now - lastStepTime > 40L) {
                boolean shift = Minecraft.getInstance() != null && Minecraft.getInstance().hasShiftDown();
                applyStepperStep(activeStepper, shift);
                lastStepTime = now;
            }
        }

        // Dark backdrop
        g.fill(0, 0, this.width, this.height, 0xD0080A0E);

        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int sidebarBg = ConfigUITheme.getSidebarBg();
        int borderCol = ConfigUITheme.getBorderColor();
        int divCol = ConfigUITheme.getDividerColor();

        // Main window outline & bg
        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);
        g.outline(winX, winY, winW, winH, borderCol);

        // Header
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.fill(winX, winY + headerH, winX + winW, winY + headerH + 1, divCol);

        String activeProf = InventoryButtonManager.getActiveProfile();
        g.text(this.font, "§d§lBOMBOADDONS §8| §bButtons", winX + 16, winY + 14, 0xFFFFFFFF, false);
        g.text(this.font, "§7Profile: §e" + activeProf, winX + 220, winY + 14, 0xFFFFFFFF, false);

        if (statusBanner != null && !statusBanner.isEmpty() && System.currentTimeMillis() - statusBannerTime < 5000L) {
            g.text(this.font, statusBanner, winX + 340, winY + 14, 0xFFFFFFFF, false);
        }

        // Close button
        int closeBtnX = winX + winW - 28;
        int closeBtnY = winY + 9;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + 20 && mouseY >= closeBtnY && mouseY <= closeBtnY + 22;
        ConfigUITheme.drawPillButton(g, this.font, "✕", closeBtnX, closeBtnY, 20, 22, closeHover,
                closeHover ? 0xFFFF6666 : 0xFF94A3B8, closeHover ? 0x44EF4444 : 0x22EF4444, closeHover ? 0xFFEF4444 : 0x44EF4444);

        // Move Mode (Visual GUI) Button
        int moveBtnX = winX + winW - 135;
        int moveBtnY = winY + 9;
        boolean moveHover = mouseX >= moveBtnX && mouseX <= moveBtnX + 102 && mouseY >= moveBtnY && mouseY <= moveBtnY + 22;
        ConfigUITheme.drawPillButton(g, this.font, "⛶ Move (GUI)", moveBtnX, moveBtnY, 102, 22, moveHover,
                0xFF38BDF8, moveHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        // Import Button (Firmament / JSON)
        int importBtnX = winX + winW - 225;
        int importBtnY = winY + 9;
        boolean importHover = mouseX >= importBtnX && mouseX <= importBtnX + 85 && mouseY >= importBtnY && mouseY <= importBtnY + 22;
        ConfigUITheme.drawPillButton(g, this.font, "📥 Import", importBtnX, importBtnY, 85, 22, importHover,
                0xFF10B981, importHover ? 0x44059669 : 0x22059669, 0xFF059669);

        // Tooltip / Hover Mode Button
        int ttBtnX = winX + winW - 335;
        int ttBtnY = winY + 9;
        String ttMode = me.bombo.bomboaddons.BomboConfig.get().inventoryButtonTooltipMode != null ? me.bombo.bomboaddons.BomboConfig.get().inventoryButtonTooltipMode.toUpperCase(java.util.Locale.ROOT) : "FULL";
        String ttLabel = switch (ttMode) {
            case "NAME_ONLY" -> "Hover: Name";
            case "CLICKS_ONLY" -> "Hover: Clicks";
            case "NONE" -> "Hover: None";
            default -> "Hover: Full";
        };
        boolean ttHover = mouseX >= ttBtnX && mouseX <= ttBtnX + 105 && mouseY >= ttBtnY && mouseY <= ttBtnY + 22;
        ConfigUITheme.drawPillButton(g, this.font, ttLabel, ttBtnX, ttBtnY, 105, 22, ttHover,
                0xFFF59E0B, ttHover ? 0x44D97706 : 0x22D97706, 0xFFD97706);

        // Sidebar / Left Panel (Buttons List)
        int panelY = winY + headerH + 1;
        int panelH = winH - headerH - 1;
        g.fill(winX, panelY, winX + leftPanelW, panelY + panelH, sidebarBg);
        g.fill(winX + leftPanelW, panelY, winX + leftPanelW + 1, panelY + panelH, divCol);

        renderLeftPanel(g, winX, panelY, leftPanelW, panelH, mouseX, mouseY);

        // Right Panel (Editor)
        int editorX = winX + leftPanelW + 16;
        int editorY = panelY + 12;
        int editorW = winW - leftPanelW - 32;
        int editorH = panelH - 24;

        renderRightEditor(g, editorX, editorY, editorW, editorH, mouseX, mouseY);
    }

    private void renderLeftPanel(GuiGraphicsExtractor g, int px, int py, int pw, int ph, int mouseX, int mouseY) {
        // Search Bar at Top of Left Panel
        int sbX = px + 8;
        int sbY = py + 6;
        int sbW = pw - 16;
        int sbH = 20;

        boolean sbHover = mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= sbY && mouseY <= sbY + sbH;
        g.fill(sbX, sbY, sbX + sbW, sbY + sbH, searchFocused ? 0xDD1E293B : (sbHover ? 0xAA1E293B : 0x770F172A));
        g.outline(sbX, sbY, sbW, sbH, searchFocused ? 0xFF38BDF8 : (sbHover ? 0x8864748B : 0x44475569));

        int tx = sbX + 6;
        int ty = sbY + 6;
        if (searchQuery.isEmpty()) {
            g.text(this.font, "§8🔍 Search buttons...", tx, ty, 0xFF94A3B8, false);
            if (searchFocused && (System.currentTimeMillis() / 500 % 2 == 0)) {
                g.fill(tx, sbY + 4, tx + 1, sbY + sbH - 4, 0xFF38BDF8);
            }
        } else {
            if (searchFocused && searchSelectAll) {
                int selW = this.font.width(searchQuery);
                g.fill(tx - 1, sbY + 4, tx + selW + 1, sbY + sbH - 4, 0x880284C7);
            }
            g.text(this.font, searchQuery, tx, ty, 0xFFFFFFFF, false);
            if (searchFocused && !searchSelectAll && (System.currentTimeMillis() / 500 % 2 == 0)) {
                int safeCursor = Math.max(0, Math.min(searchQuery.length(), searchCursorPos));
                int cx = tx + this.font.width(searchQuery.substring(0, safeCursor));
                g.fill(cx, sbY + 4, cx + 1, sbY + sbH - 4, 0xFF38BDF8);
            }
            // Clear button
            int clrX = sbX + sbW - 14;
            boolean clrHover = mouseX >= clrX && mouseX <= clrX + 12 && mouseY >= sbY + 4 && mouseY <= sbY + 16;
            g.text(this.font, clrHover ? "§c✕" : "§7✕", clrX, sbY + 6, 0xFFFFFFFF, false);
        }

        List<InventoryButton> fullList = InventoryButtonManager.getButtonsForActiveProfile();
        List<InventoryButton> list = new ArrayList<>();
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        for (InventoryButton b : fullList) {
            if (q.isEmpty() ||
                (b.name != null && b.name.toLowerCase(Locale.ROOT).contains(q)) ||
                (b.command != null && b.command.toLowerCase(Locale.ROOT).contains(q)) ||
                (b.rightClickCommand != null && b.rightClickCommand.toLowerCase(Locale.ROOT).contains(q)) ||
                (b.middleClickCommand != null && b.middleClickCommand.toLowerCase(Locale.ROOT).contains(q)) ||
                (b.icon != null && b.icon.toLowerCase(Locale.ROOT).contains(q))) {
                list.add(b);
            }
        }

        int listY = py + 30;
        int listH = ph - 76;

        g.enableScissor(px, listY, px + pw, listY + listH);
        int startY = listY + 2 - (int) this.listScroll;
        int itemH = 36;
        int totalH = list.size() * (itemH + 6);

        if (list.isEmpty()) {
            g.centeredText(this.font, "§7No matching buttons", px + pw / 2, listY + 30, 0xFF94A3B8);
        }

        for (int i = 0; i < list.size(); i++) {
            InventoryButton b = list.get(i);
            int bx = px + 8;
            int by = startY + i * (itemH + 6);
            int bw = pw - 16;

            if (by + itemH >= listY && by <= listY + listH) {
                boolean isSelected = (selectedButton == b);
                boolean hover = mouseX >= bx && mouseX <= bx + bw && mouseY >= by && mouseY <= by + itemH;

                int bg = isSelected ? 0xDD1E293B : (hover ? 0xAA1E293B : 0x770F172A);
                int border = isSelected ? 0xFF38BDF8 : (hover ? 0x8838BDF8 : 0x33475569);

                g.fill(bx, by, bx + bw, by + itemH, bg);
                g.outline(bx, by, bw, itemH, border);

                // Icon preview box
                g.fill(bx + 4, by + 4, bx + 32, by + itemH - 4, 0x44000000);
                g.outline(bx + 4, by + 4, 28, itemH - 8, 0x33FFFFFF);
                if (b.cachedStack == null || b.cachedStack.isEmpty()) {
                    b.cachedStack = TotemAnimationManager.resolveItemStack(b.icon);
                }
                if (b.cachedStack != null && !b.cachedStack.isEmpty()) {
                    g.item(b.cachedStack, bx + 10, by + 10);
                }

                // Label & Command
                String nameText = (b.name != null && !b.name.isEmpty()) ? b.name : "Unnamed";
                String cmdText = (b.command != null && !b.command.isEmpty()) ? b.command : "No command";
                g.text(this.font, "§f" + nameText, bx + 38, by + 7, 0xFFFFFFFF, false);
                g.text(this.font, "§8" + cmdText, bx + 38, by + 20, 0xFF94A3B8, false);

                // Enabled indicator (interactive toggle)
                int indW = 16;
                int indH = 16;
                int indX = bx + bw - 22;
                int indY = by + (itemH - indH) / 2;
                boolean indHover = mouseX >= indX && mouseX <= indX + indW && mouseY >= indY && mouseY <= indY + indH;
                int indCol = b.enabled ? 0xFF22C55E : 0xFFEF4444;
                g.fill(indX, indY, indX + indW, indY + indH, indHover ? (b.enabled ? 0x6622C55E : 0x66EF4444) : 0x22000000);
                g.outline(indX, indY, indW, indH, indHover ? 0xFFFFFFFF : indCol);
                g.fill(indX + 3, indY + 3, indX + indW - 3, indY + indH - 3, indCol);
            }
        }
        g.disableScissor();
        this.maxListScroll = Math.max(0, totalH - listH);

        // Add Button & Import Button
        int addY = py + ph - 40;
        int addW = pw - 106;
        boolean addHover = mouseX >= px + 8 && mouseX <= px + 8 + addW && mouseY >= addY && mouseY <= addY + 28;
        ConfigUITheme.drawPillButton(g, this.font, "+ Add", px + 8, addY, addW, 28, addHover,
                0xFF38BDF8, addHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        int impX = px + 8 + addW + 6;
        int impW = pw - 16 - addW - 6;
        boolean impHover = mouseX >= impX && mouseX <= impX + impW && mouseY >= addY && mouseY <= addY + 28;
        ConfigUITheme.drawPillButton(g, this.font, "📥 Import", impX, addY, impW, 28, impHover,
                0xFF10B981, impHover ? 0x44059669 : 0x22059669, 0xFF059669);
    }

    private void renderRightEditor(GuiGraphicsExtractor g, int ex, int ey, int ew, int eh, int mouseX, int mouseY) {
        if (selectedButton == null) {
            g.text(this.font, "§7Select a button from the left or click '+ Add Button'", ex + 20, ey + 40, 0xFF94A3B8, false);
            return;
        }

        // Title
        g.text(this.font, "§b§lButton Configuration: §f" + editName, ex, ey, 0xFFFFFFFF, false);

        int curY = ey + 24;

        // Field 0: Display Name
        g.text(this.font, "§7Button Label / Display Name:", ex, curY, 0xFF94A3B8, false);
        curY += 12;
        drawTextBox(g, ex, curY, ew - 120, 20, editName, focusField == 0);
        curY += 28;

        // Field 1: Command (Left-Click)
        g.text(this.font, "§7Left-Click Command to Execute (e.g. /warp hub):", ex, curY, 0xFF94A3B8, false);
        curY += 12;
        drawTextBox(g, ex, curY, ew - 120, 20, editCommand, focusField == 1);
        curY += 28;

        // Row: Right-Click and Middle-Click Commands
        int halfW = (ew - 140) / 2;
        int col1X = ex;
        int col2X = ex + halfW + 16;

        g.text(this.font, "§7Right-Click Command (optional, e.g. /recipe):", col1X, curY, 0xFF94A3B8, false);
        g.text(this.font, "§7Middle-Click Command (optional, e.g. /et):", col2X, curY, 0xFF94A3B8, false);
        curY += 12;
        drawTextBox(g, col1X, curY, halfW, 20, editRightClickCommand, focusField == 3);
        drawTextBox(g, col2X, curY, halfW, 20, editMiddleClickCommand, focusField == 4);
        curY += 28;

        // Row: Click Sound & Icon Item ID
        g.text(this.font, "§7Click Sound (e.g. mi-bombo-duolingo.mp3):", col1X, curY, 0xFF94A3B8, false);
        g.text(this.font, "§7Icon Item (Vanilla / SkyBlock ID):", col2X, curY, 0xFF94A3B8, false);
        curY += 12;
        int soundBoxY = curY;
        this.cachedSoundBoxY = soundBoxY;
        drawTextBox(g, col1X, soundBoxY, halfW, 20, editClickSound, focusField == 5);

        int iconBoxY = curY;
        this.cachedIconBoxY = iconBoxY;
        int iconInputW = halfW - 46;
        drawTextBox(g, col2X, iconBoxY, iconInputW, 20, editIcon, focusField == 2);

        // Icon Preview Box
        int prevBoxX = col2X + halfW - 40;
        int prevBoxY = iconBoxY - 2;
        switch (editBackgroundStyle) {
            case MODERN -> {
                g.fill(prevBoxX, prevBoxY, prevBoxX + 24, prevBoxY + 24, 0xAA0F172A);
                if (editShowBorder) g.outline(prevBoxX, prevBoxY, 24, 24, 0xFF38BDF8);
            }
            case VANILLA -> {
                g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.resources.Identifier.withDefaultNamespace("widget/button"),
                        prevBoxX, prevBoxY, 24, 24);
            }
            case SLOT -> {
                g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.resources.Identifier.withDefaultNamespace("container/slot"),
                        prevBoxX, prevBoxY, 24, 24);
            }
            case CUSTOM -> {
                g.fill(prevBoxX, prevBoxY, prevBoxX + 24, prevBoxY + 24, editCustomBgColor);
                if (editShowBorder) g.outline(prevBoxX, prevBoxY, 24, 24, editCustomBorderColor);
            }
            case INVISIBLE -> {
                if (editShowBorder) g.outline(prevBoxX, prevBoxY, 24, 24, editCustomBorderColor);
            }
        }
        ItemStack resolved = TotemAnimationManager.resolveItemStack(editIcon);
        if (resolved != null && !resolved.isEmpty()) {
            g.item(resolved, prevBoxX + 4, prevBoxY + 4);
        }
        curY += 28;

        // Background Style & Border Row
        g.text(this.font, "§7Background Style & Border:", ex, curY, 0xFF94A3B8, false);
        curY += 12;

        InventoryButton.BackgroundStyle[] styles = InventoryButton.BackgroundStyle.values();
        int sBtnW = 66;
        int sBtnH = 20;
        for (int i = 0; i < styles.length; i++) {
            InventoryButton.BackgroundStyle bs = styles[i];
            int sx = ex + i * (sBtnW + 5);
            boolean isSel = (editBackgroundStyle == bs);
            boolean sHover = mouseX >= sx && mouseX <= sx + sBtnW && mouseY >= curY && mouseY <= curY + sBtnH;
            int sBg = isSel ? 0xDD0284C7 : (sHover ? 0x44334155 : 0x221E293B);
            int sBorder = isSel ? 0xFF38BDF8 : (sHover ? 0x8838BDF8 : 0x33475569);
            g.fill(sx, curY, sx + sBtnW, curY + sBtnH, sBg);
            g.outline(sx, curY, sBtnW, sBtnH, sBorder);
            g.text(this.font, (isSel ? "§f§l" : "§7") + bs.name(), sx + 6, curY + 6, isSel ? 0xFFFFFFFF : 0xFF94A3B8, false);
        }

        // Border toggle checkbox
        int borderCheckX = ex + styles.length * (sBtnW + 5) + 10;
        boolean borderCheckHover = mouseX >= borderCheckX && mouseX <= borderCheckX + 80 && mouseY >= curY && mouseY <= curY + sBtnH;
        g.fill(borderCheckX, curY + 3, borderCheckX + 14, curY + 17, editShowBorder ? 0xFF0284C7 : 0xFF1E293B);
        g.outline(borderCheckX, curY + 3, 14, 14, editShowBorder ? 0xFF38BDF8 : (borderCheckHover ? 0x8864748B : 0x4464748B));
        if (editShowBorder) g.text(this.font, "✓", borderCheckX + 3, curY + 6, 0xFFFFFFFF, false);
        g.text(this.font, "Border", borderCheckX + 18, curY + 6, editShowBorder ? 0xFF38BDF8 : 0xFFCBD5E1, false);

        curY += 26;

        // Color presets row (for Custom Style or Border)
        if (editBackgroundStyle == InventoryButton.BackgroundStyle.CUSTOM || editShowBorder) {
            g.text(this.font, "§7Colors:", ex, curY + 2, 0xFF94A3B8, false);
            int[] presetColors = new int[]{0xAA0F172A, 0xAA7F1D1D, 0xAA14532D, 0xAA1E3A8A, 0xAA78350F, 0xAA581C87, 0xAA0284C7};
            String[] presetNames = new String[]{"Slate", "Red", "Green", "Blue", "Amber", "Purple", "Sky"};
            int pBtnW = 44;
            for (int pi = 0; pi < presetColors.length; pi++) {
                int px = ex + 48 + pi * (pBtnW + 4);
                boolean pHover = mouseX >= px && mouseX <= px + pBtnW && mouseY >= curY && mouseY <= curY + 15;
                g.fill(px, curY, px + pBtnW, curY + 15, presetColors[pi]);
                g.outline(px, curY, pBtnW, 15, pHover ? 0xFFFFFFFF : 0x44FFFFFF);
                g.centeredText(this.font, presetNames[pi], px + pBtnW / 2, curY + 4, 0xFFFFFFFF);
            }
            curY += 20;
        }

        // Profile Filter Row
        g.text(this.font, "§7Profile Scope (Show only on specific profile):", ex, curY, 0xFF94A3B8, false);
        curY += 12;
        String profDisplay = (editProfileFilter.equalsIgnoreCase("ALL") || editProfileFilter.equalsIgnoreCase("General") || editProfileFilter.equalsIgnoreCase("General (all)"))
                ? "General (all) ★ Always Active" : ("Profile: " + editProfileFilter);
        boolean profHover = mouseX >= ex && mouseX <= ex + 230 && mouseY >= curY && mouseY <= curY + 18;
        g.fill(ex, curY, ex + 230, curY + 18, 0xEE1E293B);
        g.outline(ex, curY, 230, 18, (profHover || showProfilePicker) ? 0xFF38BDF8 : 0x4464748B);
        g.text(this.font, "§e" + profDisplay, ex + 6, curY + 5, 0xFFFFFFFF, false);
        g.text(this.font, showProfilePicker ? "▲" : "▼", ex + 215, curY + 5, 0xFF38BDF8, false);

        int profBoxY = curY;
        this.cachedProfBoxY = profBoxY;
        curY += 28;

        // Checkboxes row
        // Checkbox: Only in Inventory Screen
        boolean onlyInvHover = mouseX >= ex && mouseX <= ex + 220 && mouseY >= curY && mouseY <= curY + 16;
        g.fill(ex, curY, ex + 14, curY + 14, editOnlyInInventory ? 0xFF0284C7 : 0xFF1E293B);
        g.outline(ex, curY, 14, 14, editOnlyInInventory ? 0xFF38BDF8 : (onlyInvHover ? 0x8864748B : 0x4464748B));
        if (editOnlyInInventory) g.text(this.font, "✓", ex + 3, curY + 3, 0xFFFFFFFF, false);
        g.text(this.font, "Only in Player Inventory (E)", ex + 20, curY + 3, editOnlyInInventory ? 0xFF38BDF8 : 0xFFCBD5E1, false);
        if (onlyInvHover) {
            g.text(this.font, "§eⓘ When checked: hidden in chests/container GUIs. Shows only in player inventory (E).", ex, curY + 18, 0xFFF59E0B, false);
        }

        // Checkbox: Enabled
        int enX = ex + 250;
        boolean enHover = mouseX >= enX && mouseX <= enX + 140 && mouseY >= curY && mouseY <= curY + 16;
        g.fill(enX, curY, enX + 14, curY + 14, editEnabled ? 0xFF0284C7 : 0xFF1E293B);
        g.outline(enX, curY, 14, 14, editEnabled ? 0xFF38BDF8 : (enHover ? 0x8864748B : 0x4464748B));
        if (editEnabled) g.text(this.font, "✓", enX + 3, curY + 3, 0xFFFFFFFF, false);
        g.text(this.font, "Button Enabled", enX + 20, curY + 3, editEnabled ? 0xFF38BDF8 : 0xFFCBD5E1, false);

        // Save & Delete Row at bottom
        int botY = ey + eh - 28;
        int saveW = 100;
        int delW = 90;

        boolean saveHover = mouseX >= ex && mouseX <= ex + saveW && mouseY >= botY && mouseY <= botY + 26;
        ConfigUITheme.drawPillButton(g, this.font, "✔ Save", ex, botY, saveW, 26, saveHover,
                0xFFFFFFFF, saveHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        int delX = ex + saveW + 12;
        boolean delHover = mouseX >= delX && mouseX <= delX + delW && mouseY >= botY && mouseY <= botY + 26;
        ConfigUITheme.drawPillButton(g, this.font, "🗑 Delete", delX, botY, delW, 26, delHover,
                0xFFFF6666, delHover ? 0x44EF4444 : 0x22EF4444, 0xFFEF4444);

        // Draw floating Profile Picker Popup
        if (showProfilePicker) {
            List<String> profiles = getAvailableProfiles();
            int pX = ex;
            int pY = profBoxY + 20;
            int pW = 230;
            int rH = 20;
            int maxVisible = 8;
            int visibleCount = Math.min(maxVisible, profiles.size());
            int pH = visibleCount * rH + 4;

            g.fill(pX - 1, pY - 1, pX + pW + 1, pY + pH + 1, 0xFF38BDF8);
            g.fill(pX, pY, pX + pW, pY + pH, 0xF50F172A);

            for (int i = 0; i < visibleCount; i++) {
                int pIdx = profileScrollOffset + i;
                if (pIdx >= profiles.size()) break;
                String prof = profiles.get(pIdx);
                int rowY = pY + 2 + i * rH;
                boolean pHover = (mouseX >= pX && mouseX <= pX + pW && mouseY >= rowY && mouseY <= rowY + rH);
                boolean isCurrent = (prof.equals("General (all)") && (editProfileFilter.equalsIgnoreCase("ALL") || editProfileFilter.equalsIgnoreCase("General") || editProfileFilter.equalsIgnoreCase("General (all)")))
                        || prof.equalsIgnoreCase(editProfileFilter);

                if (pHover || isCurrent) {
                    g.fill(pX + 1, rowY, pX + pW - 1, rowY + rH, isCurrent ? 0xDD0284C7 : 0xAA1E293B);
                }

                String label = prof.equals("General (all)") ? "§aGeneral (all) §8(Always Active)" : ("§f" + prof);
                g.text(this.font, label, pX + 8, rowY + 6, isCurrent ? 0xFFFFFFFF : 0xFFCBD5E1, false);
            }

            // Scroll indicator if profiles > maxVisible
            if (profiles.size() > maxVisible) {
                int sbW = 4;
                int sbX = pX + pW - sbW - 2;
                int sbH = pH - 4;
                int maxScroll = profiles.size() - maxVisible;
                float pct = (float) profileScrollOffset / maxScroll;
                int thumbH = Math.max(12, sbH * maxVisible / profiles.size());
                int thumbY = pY + 2 + (int) ((sbH - thumbH) * pct);
                g.fill(sbX, pY + 2, sbX + sbW, pY + 2 + sbH, 0x33FFFFFF);
                g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF38BDF8);
            }
        }

        // Draw floating Autocompletion Dropdown if editing field 2
        if (focusField == 2 && !currentIconSuggestions.isEmpty() && !showProfilePicker) {
            int dropX = ex + halfW + 16;
            int dropY = iconBoxY + 22;
            int dropW = halfW;
            int rowH = 20;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, currentIconSuggestions.size());
            int dropH = visibleCount * rowH + 4;

            g.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF38BDF8);
            g.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xF50F172A);

            for (int i = 0; i < visibleCount; i++) {
                int sIdx = suggestionScrollOffset + i;
                if (sIdx >= currentIconSuggestions.size()) break;

                String sugg = currentIconSuggestions.get(sIdx);
                int rY = dropY + 2 + i * rowH;
                boolean rHover = (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= rY && mouseY <= rY + rowH);
                boolean isSelectedSugg = (sIdx == selectedSuggestionIdx);

                if (rHover || isSelectedSugg) {
                    g.fill(dropX + 1, rY, dropX + dropW - 1, rY + rowH, isSelectedSugg ? 0xDD0284C7 : 0x3338BDF8);
                }

                ItemStack sStack = TotemAnimationManager.resolveItemStack(sugg);
                if (sStack != null && !sStack.isEmpty()) {
                    g.item(sStack, dropX + 4, rY + 2);
                }
                g.text(this.font, "§e" + sugg, dropX + 26, rY + 6, (rHover || isSelectedSugg) ? 0xFFFFFFFF : 0xFFCBD5E1, false);
            }

            // Draw scrollbar indicator
            if (currentIconSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
                int sbW = 4;
                int sbX = dropX + dropW - sbW - 2;
                int sbH = dropH - 4;
                int maxScroll = currentIconSuggestions.size() - MAX_VISIBLE_SUGGESTIONS;
                float pct = (float) suggestionScrollOffset / maxScroll;
                int thumbH = Math.max(12, sbH * MAX_VISIBLE_SUGGESTIONS / currentIconSuggestions.size());
                int thumbY = dropY + 2 + (int) ((sbH - thumbH) * pct);
                g.fill(sbX, dropY + 2, sbX + sbW, dropY + 2 + sbH, 0x33FFFFFF);
                g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF38BDF8);
            }
        }

        // Draw floating Autocompletion Dropdown if editing field 5 (Click Sound)
        if (focusField == 5 && !currentSoundSuggestions.isEmpty() && !showProfilePicker) {
            int dropX = col1X;
            int dropY = (cachedSoundBoxY > 0 ? cachedSoundBoxY : (curY - 28)) + 22;
            int dropW = halfW;
            int rowH = 20;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, currentSoundSuggestions.size());
            int dropH = visibleCount * rowH + 4;

            g.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF38BDF8);
            g.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xF50F172A);

            for (int i = 0; i < visibleCount; i++) {
                int sIdx = soundSuggestionScrollOffset + i;
                if (sIdx >= currentSoundSuggestions.size()) break;

                String sugg = currentSoundSuggestions.get(sIdx);
                int rY = dropY + 2 + i * rowH;
                boolean rHover = (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= rY && mouseY <= rY + rowH);
                boolean isSelectedSugg = (sIdx == selectedSoundSuggestionIdx);

                if (rHover || isSelectedSugg) {
                    g.fill(dropX + 1, rY, dropX + dropW - 1, rY + rowH, isSelectedSugg ? 0xDD0284C7 : 0x3338BDF8);
                }

                g.text(this.font, "§e" + sugg, dropX + 8, rY + 6, (rHover || isSelectedSugg) ? 0xFFFFFFFF : 0xFFCBD5E1, false);
            }

            // Draw scrollbar indicator
            if (currentSoundSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
                int sbW = 4;
                int sbX = dropX + dropW - sbW - 2;
                int sbH = dropH - 4;
                int maxScroll = currentSoundSuggestions.size() - MAX_VISIBLE_SUGGESTIONS;
                float pct = (float) soundSuggestionScrollOffset / maxScroll;
                int thumbH = Math.max(12, sbH * MAX_VISIBLE_SUGGESTIONS / currentSoundSuggestions.size());
                int thumbY = dropY + 2 + (int) ((sbH - thumbH) * pct);
                g.fill(sbX, dropY + 2, sbX + sbW, dropY + 2 + sbH, 0x33FFFFFF);
                g.fill(sbX, thumbY, sbX + sbW, thumbY + thumbH, 0xFF38BDF8);
            }
        }
    }

    private void drawTextBox(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, boolean focused) {
        g.fill(x, y, x + w, y + h, focused ? 0xEE1E293B : 0xAA1E293B);
        g.outline(x, y, w, h, focused ? 0xFF38BDF8 : 0x3364748B);

        if (focused && selectAllOnType && !text.isEmpty()) {
            int selW = this.font.width(text);
            g.fill(x + 5, y + 4, x + 7 + selW, y + h - 4, 0x880284C7);
        }

        g.text(this.font, text, x + 6, y + 6, 0xFFFFFFFF, false);
        if (focused && (System.currentTimeMillis() / 500 % 2 == 0)) {
            cursorPos = Math.max(0, Math.min(text.length(), cursorPos));
            int cx = x + 6 + this.font.width(text.substring(0, cursorPos));
            g.fill(cx, y + 4, cx + 1, y + h - 4, 0xFF38BDF8);
        }
    }

    private void drawStepper(GuiGraphicsExtractor g, int x, int y, int val, int mouseX, int mouseY) {
        // [-]
        boolean minusHover = mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18;
        ConfigUITheme.drawPillButton(g, this.font, "-", x, y, 18, 18, minusHover, 0xFFCBD5E1, 0x22FFFFFF, 0x44FFFFFF);

        // Value text
        g.text(this.font, "§e" + val, x + 24, y + 5, 0xFFFFFFFF, false);

        // [+]
        int plusX = x + 50;
        boolean plusHover = mouseX >= plusX && mouseX <= plusX + 18 && mouseY >= y && mouseY <= y + 18;
        ConfigUITheme.drawPillButton(g, this.font, "+", plusX, y, 18, 18, plusHover, 0xFFCBD5E1, 0x22FFFFFF, 0x44FFFFFF);
    }

    private String getFieldText(int field) {
        return switch (field) {
            case 0 -> editName;
            case 1 -> editCommand;
            case 2 -> editIcon;
            case 3 -> editRightClickCommand;
            case 4 -> editMiddleClickCommand;
            case 5 -> editClickSound;
            default -> "";
        };
    }

    private void setFieldText(int field, String text) {
        switch (field) {
            case 0 -> editName = text;
            case 1 -> editCommand = text;
            case 2 -> { editIcon = text; updateIconSuggestions(); }
            case 3 -> editRightClickCommand = text;
            case 4 -> editMiddleClickCommand = text;
            case 5 -> { editClickSound = text; updateSoundSuggestions(); }
        }
    }

    private int getPrevWordIndex(String text, int pos) {
        if (text == null || pos <= 0) return 0;
        int p = Math.min(pos, text.length()) - 1;
        while (p > 0 && Character.isWhitespace(text.charAt(p))) p--;
        while (p > 0 && !Character.isWhitespace(text.charAt(p - 1))) p--;
        return Math.max(0, p);
    }

    private int getNextWordIndex(String text, int pos) {
        if (text == null || pos >= text.length()) return text != null ? text.length() : 0;
        int p = pos;
        while (p < text.length() && !Character.isWhitespace(text.charAt(p))) p++;
        while (p < text.length() && Character.isWhitespace(text.charAt(p))) p++;
        return Math.min(text.length(), p);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Close Button
        int closeBtnX = winX + winW - 28;
        int closeBtnY = winY + 9;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + 20 && mouseY >= closeBtnY && mouseY <= closeBtnY + 22) {
            saveCurrentEditor();
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        // Move Mode Button
        int moveBtnX = winX + winW - 135;
        int moveBtnY = winY + 9;
        if (mouseX >= moveBtnX && mouseX <= moveBtnX + 102 && mouseY >= moveBtnY && mouseY <= moveBtnY + 22) {
            saveCurrentEditor();
            Minecraft.getInstance().setScreenAndShow(new InventoryButtonMoveScreen(this));
            return true;
        }

        // Import Button (Firmament / JSON)
        int importBtnX = winX + winW - 225;
        int importBtnY = winY + 9;
        if (mouseX >= importBtnX && mouseX <= importBtnX + 85 && mouseY >= importBtnY && mouseY <= importBtnY + 22) {
            triggerImport();
            return true;
        }

        // Tooltip / Hover Mode Button
        int ttBtnX = winX + winW - 335;
        int ttBtnY = winY + 9;
        if (mouseX >= ttBtnX && mouseX <= ttBtnX + 105 && mouseY >= ttBtnY && mouseY <= ttBtnY + 22) {
            String cur = me.bombo.bomboaddons.BomboConfig.get().inventoryButtonTooltipMode != null ? me.bombo.bomboaddons.BomboConfig.get().inventoryButtonTooltipMode.toUpperCase(java.util.Locale.ROOT) : "FULL";
            String next = switch (cur) {
                case "FULL" -> "NAME_ONLY";
                case "NAME_ONLY" -> "CLICKS_ONLY";
                case "CLICKS_ONLY" -> "NONE";
                default -> "FULL";
            };
            me.bombo.bomboaddons.BomboConfig.get().inventoryButtonTooltipMode = next;
            me.bombo.bomboaddons.BomboConfig.save();
            return true;
        }

        // Left Panel Clicks
        int panelY = winY + headerH + 1;
        int panelH = winH - headerH - 1;
        if (mouseX >= winX && mouseX <= winX + leftPanelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            // Check Search Bar click
            int sbX = winX + 8;
            int sbY = panelY + 6;
            int sbW = leftPanelW - 16;
            int sbH = 20;
            if (mouseX >= sbX && mouseX <= sbX + sbW && mouseY >= sbY && mouseY <= sbY + sbH) {
                int clrX = sbX + sbW - 14;
                if (!searchQuery.isEmpty() && mouseX >= clrX && mouseX <= clrX + 12 && mouseY >= sbY + 4 && mouseY <= sbY + 16) {
                    pushSearchUndo();
                    searchQuery = "";
                    searchCursorPos = 0;
                    searchSelectAll = false;
                } else {
                    searchFocused = true;
                    searchSelectAll = false;
                    searchCursorPos = searchQuery.length();
                }
                return true;
            } else {
                searchFocused = false;
                searchSelectAll = false;
            }

            // Check Add Button & Import Button
            int addY = panelY + panelH - 40;
            int addW = leftPanelW - 106;
            if (mouseY >= addY && mouseY <= addY + 28 && mouseX >= winX + 8 && mouseX <= winX + 8 + addW) {
                pushUndoState();
                saveCurrentEditor();
                List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
                InventoryButton newBtn = new InventoryButton("New Button", "/help", "minecraft:compass", InventoryButton.Anchor.PLAYER_INV_RIGHT, 4, 0, 18, true, false);
                list.add(newBtn);
                InventoryButtonManager.save();
                selectButton(newBtn);
                return true;
            }

            int impX = winX + 8 + addW + 6;
            int impW = leftPanelW - 16 - addW - 6;
            if (mouseY >= addY && mouseY <= addY + 28 && mouseX >= impX && mouseX <= impX + impW) {
                triggerImport();
                return true;
            }

            // Check button item clicks
            List<InventoryButton> fullList = InventoryButtonManager.getButtonsForActiveProfile();
            List<InventoryButton> list = new ArrayList<>();
            String q = searchQuery.toLowerCase(Locale.ROOT).trim();
            for (InventoryButton b : fullList) {
                if (q.isEmpty() ||
                    (b.name != null && b.name.toLowerCase(Locale.ROOT).contains(q)) ||
                    (b.command != null && b.command.toLowerCase(Locale.ROOT).contains(q)) ||
                    (b.rightClickCommand != null && b.rightClickCommand.toLowerCase(Locale.ROOT).contains(q)) ||
                    (b.middleClickCommand != null && b.middleClickCommand.toLowerCase(Locale.ROOT).contains(q)) ||
                    (b.icon != null && b.icon.toLowerCase(Locale.ROOT).contains(q))) {
                    list.add(b);
                }
            }

            int listY = panelY + 30;
            int startY = listY + 2 - (int) this.listScroll;
            int itemH = 36;
            for (int i = 0; i < list.size(); i++) {
                InventoryButton b = list.get(i);
                int bx = winX + 8;
                int by = startY + i * (itemH + 6);
                int bw = leftPanelW - 16;
                if (mouseY >= by && mouseY <= by + itemH && mouseX >= bx && mouseX <= bx + bw) {
                    // Check if clicked the toggle indicator on the right
                    int indW = 16;
                    int indH = 16;
                    int indX = bx + bw - 22;
                    int indY = by + (itemH - indH) / 2;
                    if (mouseX >= indX - 2 && mouseX <= indX + indW + 2 && mouseY >= indY - 2 && mouseY <= indY + indH + 2) {
                        pushUndoState();
                        b.enabled = !b.enabled;
                        if (selectedButton == b) {
                            editEnabled = b.enabled;
                        }
                        InventoryButtonManager.save();
                        return true;
                    }
                    saveCurrentEditor();
                    selectButton(b);
                    return true;
                }
            }
            return true;
        } else {
            searchFocused = false;
        }

        // Right Editor Clicks
        if (selectedButton != null) {
            int editorX = winX + leftPanelW + 16;
            int editorY = panelY + 12;
            int editorW = winW - leftPanelW - 32;
            int editorH = panelH - 24;

            // Check Profile Picker clicks first
            if (showProfilePicker) {
                List<String> profiles = getAvailableProfiles();
                int pX = editorX;
                int pY = (cachedProfBoxY > 0 ? cachedProfBoxY : (editorY + 200)) + 20;
                int pW = 230;
                int rH = 20;
                int maxVisible = 8;
                int visibleCount = Math.min(maxVisible, profiles.size());
                int pH = visibleCount * rH + 4;

                if (mouseX >= pX && mouseX <= pX + pW && mouseY >= pY && mouseY <= pY + pH) {
                    int rowClicked = (int) (mouseY - pY - 2) / rH;
                    int clickedIdx = profileScrollOffset + rowClicked;
                    if (clickedIdx >= 0 && clickedIdx < profiles.size()) {
                        String chosen = profiles.get(clickedIdx);
                        editProfileFilter = chosen.equals("General (all)") ? "General" : chosen;
                        showProfilePicker = false;
                        return true;
                    }
                } else if (mouseX >= editorX && mouseX <= editorX + 230 && mouseY >= cachedProfBoxY && mouseY <= cachedProfBoxY + 18) {
                    showProfilePicker = false;
                    return true;
                } else {
                    showProfilePicker = false;
                }
            }

            // Check if clicked suggestion dropdown first
            if (focusField == 2 && !currentIconSuggestions.isEmpty() && !showProfilePicker) {
                int halfW = (editorW - 140) / 2;
                int col2X = editorX + halfW + 16;
                int iconBoxY = (cachedIconBoxY > 0 ? cachedIconBoxY : (editorY + 24 + 40 + 40 + 40));
                int dropX = col2X;
                int dropY = iconBoxY + 22;
                int dropW = halfW;
                int rowH = 20;
                int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, currentIconSuggestions.size());
                int dropH = visibleCount * rowH + 4;

                if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                    int clickedIdx = (int) (mouseY - dropY - 2) / rowH;
                    int actualIdx = suggestionScrollOffset + clickedIdx;
                    if (actualIdx >= 0 && actualIdx < currentIconSuggestions.size()) {
                        editIcon = currentIconSuggestions.get(actualIdx);
                        currentIconSuggestions.clear();
                        cursorPos = editIcon.length();
                        return true;
                    }
                }
            }

            // Check if clicked sound suggestion dropdown
            if (focusField == 5 && !currentSoundSuggestions.isEmpty() && !showProfilePicker) {
                int halfW = (editorW - 140) / 2;
                int col1X = editorX;
                int soundBoxY = (cachedSoundBoxY > 0 ? cachedSoundBoxY : (editorY + 24 + 40 + 40 + 40));
                int dropX = col1X;
                int dropY = soundBoxY + 22;
                int dropW = halfW;
                int rowH = 20;
                int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, currentSoundSuggestions.size());
                int dropH = visibleCount * rowH + 4;

                if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                    int clickedIdx = (int) (mouseY - dropY - 2) / rowH;
                    int actualIdx = soundSuggestionScrollOffset + clickedIdx;
                    if (actualIdx >= 0 && actualIdx < currentSoundSuggestions.size()) {
                        editClickSound = currentSoundSuggestions.get(actualIdx);
                        currentSoundSuggestions.clear();
                        cursorPos = editClickSound.length();
                        return true;
                    }
                }
            }

            int curY = editorY + 24;

            // Field 0: Name
            if (mouseX >= editorX && mouseX <= editorX + editorW - 120 && mouseY >= curY + 12 && mouseY <= curY + 32) {
                focusField = 0;
                cursorPos = editName.length();
                selectAllOnType = false;
                return true;
            }
            curY += 40;

            // Field 1: Command (Left-Click)
            if (mouseX >= editorX && mouseX <= editorX + editorW - 120 && mouseY >= curY + 12 && mouseY <= curY + 32) {
                focusField = 1;
                cursorPos = editCommand.length();
                selectAllOnType = false;
                return true;
            }
            curY += 40;

            // Row: Right-Click (Field 3) and Middle-Click (Field 4)
            int halfW = (editorW - 140) / 2;
            int col1X = editorX;
            int col2X = editorX + halfW + 16;
            if (mouseX >= col1X && mouseX <= col1X + halfW && mouseY >= curY + 12 && mouseY <= curY + 32) {
                focusField = 3;
                cursorPos = editRightClickCommand.length();
                selectAllOnType = false;
                return true;
            }
            if (mouseX >= col2X && mouseX <= col2X + halfW && mouseY >= curY + 12 && mouseY <= curY + 32) {
                focusField = 4;
                cursorPos = editMiddleClickCommand.length();
                selectAllOnType = false;
                return true;
            }
            curY += 40;

            // Row: Click Sound (Field 5) & Icon Item ID (Field 2)
            if (mouseX >= col1X && mouseX <= col1X + halfW && mouseY >= curY + 12 && mouseY <= curY + 32) {
                focusField = 5;
                cursorPos = editClickSound.length();
                selectAllOnType = false;
                updateSoundSuggestions();
                return true;
            }
            int iconInputW = halfW - 46;
            if (mouseX >= col2X && mouseX <= col2X + iconInputW && mouseY >= curY + 12 && mouseY <= curY + 32) {
                focusField = 2;
                cursorPos = editIcon.length();
                selectAllOnType = false;
                updateIconSuggestions();
                return true;
            }
            curY += 40;

            // Background styles click
            curY += 12;
            int styleY = curY;
            InventoryButton.BackgroundStyle[] styles = InventoryButton.BackgroundStyle.values();
            int sBtnW = 66;
            int sBtnH = 20;
            for (int i = 0; i < styles.length; i++) {
                int sx = editorX + i * (sBtnW + 5);
                if (mouseX >= sx && mouseX <= sx + sBtnW && mouseY >= styleY && mouseY <= styleY + sBtnH) {
                    editBackgroundStyle = styles[i];
                    for (InventoryButton b : InventoryButtonManager.getButtonsForActiveProfile()) {
                        b.backgroundStyle = editBackgroundStyle;
                    }
                    if (selectedButton != null) {
                        selectedButton.backgroundStyle = editBackgroundStyle;
                    }
                    InventoryButtonManager.save();
                    return true;
                }
            }

            // Border toggle click
            int borderCheckX = editorX + styles.length * (sBtnW + 5) + 10;
            if (mouseX >= borderCheckX && mouseX <= borderCheckX + 80 && mouseY >= styleY && mouseY <= styleY + sBtnH) {
                editShowBorder = !editShowBorder;
                for (InventoryButton b : InventoryButtonManager.getButtonsForActiveProfile()) {
                    b.showBorder = editShowBorder;
                }
                if (selectedButton != null) {
                    selectedButton.showBorder = editShowBorder;
                }
                InventoryButtonManager.save();
                return true;
            }

            curY += 26;

            // Preset colors click
            if (editBackgroundStyle == InventoryButton.BackgroundStyle.CUSTOM || editShowBorder) {
                int[] presetColors = new int[]{0xAA0F172A, 0xAA7F1D1D, 0xAA14532D, 0xAA1E3A8A, 0xAA78350F, 0xAA581C87, 0xAA0284C7};
                int pBtnW = 44;
                int colorY = curY;
                for (int pi = 0; pi < presetColors.length; pi++) {
                    int px = editorX + 48 + pi * (pBtnW + 4);
                    if (mouseX >= px && mouseX <= px + pBtnW && mouseY >= colorY && mouseY <= colorY + 15) {
                        editCustomBgColor = presetColors[pi];
                        editCustomHoverBgColor = 0xDD000000 | (presetColors[pi] & 0x00FFFFFF);
                        editCustomBorderColor = 0xFF000000 | (presetColors[pi] & 0x00FFFFFF);
                        editCustomHoverBorderColor = 0xFF38BDF8;
                        for (InventoryButton b : InventoryButtonManager.getButtonsForActiveProfile()) {
                            b.customBgColor = editCustomBgColor;
                            b.customHoverBgColor = editCustomHoverBgColor;
                            b.customBorderColor = editCustomBorderColor;
                            b.customHoverBorderColor = editCustomHoverBorderColor;
                        }
                        if (selectedButton != null) {
                            selectedButton.customBgColor = editCustomBgColor;
                            selectedButton.customHoverBgColor = editCustomHoverBgColor;
                            selectedButton.customBorderColor = editCustomBorderColor;
                            selectedButton.customHoverBorderColor = editCustomHoverBorderColor;
                        }
                        InventoryButtonManager.save();
                        return true;
                    }
                }
                curY += 20;
            }

            // Profile Scope Click (toggle profile picker dropdown)
            if (mouseX >= editorX && mouseX <= editorX + 230 && ((cachedProfBoxY > 0 && mouseY >= cachedProfBoxY && mouseY <= cachedProfBoxY + 18) || (mouseY >= curY + 12 && mouseY <= curY + 30))) {
                showProfilePicker = !showProfilePicker;
                return true;
            }

            curY += 40;

            // Checkbox: only in inventory
            if (mouseX >= editorX && mouseX <= editorX + 220 && mouseY >= curY && mouseY <= curY + 16) {
                editOnlyInInventory = !editOnlyInInventory;
                return true;
            }

            // Checkbox: enabled
            int enX = editorX + 250;
            if (mouseX >= enX && mouseX <= enX + 140 && mouseY >= curY && mouseY <= curY + 16) {
                editEnabled = !editEnabled;
                return true;
            }

            // Save & Delete
            int botY = editorY + editorH - 28;
            int saveW = 100;
            int delW = 90;
            if (mouseX >= editorX && mouseX <= editorX + saveW && mouseY >= botY && mouseY <= botY + 26) {
                saveCurrentEditor();
                Minecraft.getInstance().setScreenAndShow(this.parent);
                return true;
            }

            int delX = editorX + saveW + 12;
            if (mouseX >= delX && mouseX <= delX + delW && mouseY >= botY && mouseY <= botY + 26) {
                pushUndoState();
                List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
                list.remove(selectedButton);
                InventoryButtonManager.save();
                selectButton(list.isEmpty() ? null : list.get(0));
                return true;
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        activeStepper = 0;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelY = winY + headerH + 1;
        int panelH = winH - headerH - 1;
        if (mouseX >= winX && mouseX <= winX + leftPanelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            this.listScroll -= verticalAmount * 20.0;
            if (this.listScroll < 0) this.listScroll = 0;
            if (this.listScroll > this.maxListScroll) this.listScroll = this.maxListScroll;
            return true;
        }

        // Profile Picker Popup Scroll
        if (showProfilePicker) {
            List<String> profiles = getAvailableProfiles();
            int editorX = winX + leftPanelW + 16;
            int pX = editorX;
            int pY = (cachedProfBoxY > 0 ? cachedProfBoxY : (panelY + 200)) + 20;
            int pW = 230;
            int maxVisible = 8;
            int visibleCount = Math.min(maxVisible, profiles.size());
            int pH = visibleCount * 20 + 4;

            if (mouseX >= pX && mouseX <= pX + pW && mouseY >= pY && mouseY <= pY + pH) {
                if (verticalAmount > 0) {
                    profileScrollOffset = Math.max(0, profileScrollOffset - 1);
                } else if (verticalAmount < 0) {
                    int maxScroll = Math.max(0, profiles.size() - maxVisible);
                    profileScrollOffset = Math.min(maxScroll, profileScrollOffset + 1);
                }
                return true;
            }
        }

        // Suggestions Dropdown Scroll
        if (focusField == 2 && !currentIconSuggestions.isEmpty() && !showProfilePicker) {
            int editorX = winX + leftPanelW + 16;
            int editorW = winW - leftPanelW - 32;
            int halfW = (editorW - 140) / 2;
            int col2X = editorX + halfW + 16;
            int iconBoxY = (cachedIconBoxY > 0 ? cachedIconBoxY : (panelY + 12 + 24 + 40 + 40 + 40));
            int dropX = col2X;
            int dropY = iconBoxY + 22;
            int dropW = halfW;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, currentIconSuggestions.size());
            int dropH = visibleCount * 20 + 4;

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                if (verticalAmount > 0) {
                    suggestionScrollOffset = Math.max(0, suggestionScrollOffset - 1);
                } else if (verticalAmount < 0) {
                    int maxScroll = Math.max(0, currentIconSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
                    suggestionScrollOffset = Math.min(maxScroll, suggestionScrollOffset + 1);
                }
                return true;
            }
        }

        // Sound Suggestions Dropdown Scroll
        if (focusField == 5 && !currentSoundSuggestions.isEmpty() && !showProfilePicker) {
            int editorX = winX + leftPanelW + 16;
            int editorW = winW - leftPanelW - 32;
            int halfW = (editorW - 140) / 2;
            int col1X = editorX;
            int soundBoxY = (cachedSoundBoxY > 0 ? cachedSoundBoxY : (panelY + 12 + 24 + 40 + 40 + 40));
            int dropX = col1X;
            int dropY = soundBoxY + 22;
            int dropW = halfW;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, currentSoundSuggestions.size());
            int dropH = visibleCount * 20 + 4;

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                if (verticalAmount > 0) {
                    soundSuggestionScrollOffset = Math.max(0, soundSuggestionScrollOffset - 1);
                } else if (verticalAmount < 0) {
                    int maxScroll = Math.max(0, currentSoundSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
                    soundSuggestionScrollOffset = Math.min(maxScroll, soundSuggestionScrollOffset + 1);
                }
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (searchFocused) {
            if (c >= 32 && c != 127) {
                pushSearchUndo();
                if (searchSelectAll) {
                    searchQuery = String.valueOf(c);
                    searchCursorPos = 1;
                    searchSelectAll = false;
                    return true;
                }
                searchCursorPos = Math.max(0, Math.min(searchQuery.length(), searchCursorPos));
                searchQuery = searchQuery.substring(0, searchCursorPos) + c + searchQuery.substring(searchCursorPos);
                searchCursorPos++;
                return true;
            }
        }
        if (c >= 32 && c != 127 && selectedButton != null) {
            pushFieldUndo(focusField);
            if (selectAllOnType) {
                setFieldText(focusField, "");
                cursorPos = 0;
                selectAllOnType = false;
            }

            if (focusField == 0 && editName.length() < 32) {
                cursorPos = Math.max(0, Math.min(editName.length(), cursorPos));
                editName = editName.substring(0, cursorPos) + c + editName.substring(cursorPos);
                cursorPos++;
                return true;
            } else if (focusField == 1 && editCommand.length() < 120) {
                cursorPos = Math.max(0, Math.min(editCommand.length(), cursorPos));
                editCommand = editCommand.substring(0, cursorPos) + c + editCommand.substring(cursorPos);
                cursorPos++;
                return true;
            } else if (focusField == 2 && editIcon.length() < 64) {
                cursorPos = Math.max(0, Math.min(editIcon.length(), cursorPos));
                editIcon = editIcon.substring(0, cursorPos) + c + editIcon.substring(cursorPos);
                cursorPos++;
                updateIconSuggestions();
                return true;
            } else if (focusField == 3 && editRightClickCommand.length() < 120) {
                cursorPos = Math.max(0, Math.min(editRightClickCommand.length(), cursorPos));
                editRightClickCommand = editRightClickCommand.substring(0, cursorPos) + c + editRightClickCommand.substring(cursorPos);
                cursorPos++;
                return true;
            } else if (focusField == 4 && editMiddleClickCommand.length() < 120) {
                cursorPos = Math.max(0, Math.min(editMiddleClickCommand.length(), cursorPos));
                editMiddleClickCommand = editMiddleClickCommand.substring(0, cursorPos) + c + editMiddleClickCommand.substring(cursorPos);
                cursorPos++;
                return true;
            } else if (focusField == 5 && editClickSound.length() < 64) {
                cursorPos = Math.max(0, Math.min(editClickSound.length(), cursorPos));
                editClickSound = editClickSound.substring(0, cursorPos) + c + editClickSound.substring(cursorPos);
                cursorPos++;
                updateSoundSuggestions();
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int code = event.key();
        boolean isCtrl = event.hasControlDown() || (Minecraft.getInstance() != null && Minecraft.getInstance().hasControlDown());
        boolean isShift = event.hasShiftDown() || (Minecraft.getInstance() != null && Minecraft.getInstance().hasShiftDown());

        // Search Bar Key Handling
        if (searchFocused) {
            searchCursorPos = Math.max(0, Math.min(searchQuery.length(), searchCursorPos));

            if (isCtrl && code == GLFW.GLFW_KEY_A) {
                searchSelectAll = true;
                return true;
            }
            if (isCtrl && code == GLFW.GLFW_KEY_C) {
                if (!searchQuery.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(searchQuery);
                }
                return true;
            }
            if (isCtrl && code == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    pushSearchUndo();
                    if (searchSelectAll) {
                        searchQuery = clip;
                        searchCursorPos = searchQuery.length();
                        searchSelectAll = false;
                        return true;
                    }
                    searchQuery = searchQuery.substring(0, searchCursorPos) + clip + searchQuery.substring(searchCursorPos);
                    searchCursorPos += clip.length();
                    return true;
                }
            }
            if (isCtrl && code == GLFW.GLFW_KEY_Z) {
                if (isShift) {
                    if (!searchRedoStack.isEmpty()) {
                        searchUndoStack.push(searchQuery);
                        searchQuery = searchRedoStack.pop();
                        searchCursorPos = searchQuery.length();
                        searchSelectAll = false;
                    }
                } else {
                    if (!searchUndoStack.isEmpty()) {
                        searchRedoStack.push(searchQuery);
                        searchQuery = searchUndoStack.pop();
                        searchCursorPos = searchQuery.length();
                        searchSelectAll = false;
                    }
                }
                return true;
            }
            if (isCtrl && code == GLFW.GLFW_KEY_Y) {
                if (!searchRedoStack.isEmpty()) {
                    searchUndoStack.push(searchQuery);
                    searchQuery = searchRedoStack.pop();
                    searchCursorPos = searchQuery.length();
                    searchSelectAll = false;
                }
                return true;
            }

            if (code == GLFW.GLFW_KEY_BACKSPACE) {
                pushSearchUndo();
                if (searchSelectAll) {
                    searchQuery = "";
                    searchCursorPos = 0;
                    searchSelectAll = false;
                    return true;
                }
                if (isCtrl) {
                    int prev = getPrevWordIndex(searchQuery, searchCursorPos);
                    searchQuery = searchQuery.substring(0, prev) + searchQuery.substring(searchCursorPos);
                    searchCursorPos = prev;
                } else if (searchCursorPos > 0) {
                    searchQuery = searchQuery.substring(0, searchCursorPos - 1) + searchQuery.substring(searchCursorPos);
                    searchCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                pushSearchUndo();
                if (searchSelectAll) {
                    searchQuery = "";
                    searchCursorPos = 0;
                    searchSelectAll = false;
                    return true;
                }
                if (isCtrl) {
                    int next = getNextWordIndex(searchQuery, searchCursorPos);
                    searchQuery = searchQuery.substring(0, searchCursorPos) + searchQuery.substring(next);
                } else if (searchCursorPos < searchQuery.length()) {
                    searchQuery = searchQuery.substring(0, searchCursorPos) + searchQuery.substring(searchCursorPos + 1);
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_LEFT) {
                searchSelectAll = false;
                if (isCtrl) {
                    searchCursorPos = getPrevWordIndex(searchQuery, searchCursorPos);
                } else if (searchCursorPos > 0) {
                    searchCursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                searchSelectAll = false;
                if (isCtrl) {
                    searchCursorPos = getNextWordIndex(searchQuery, searchCursorPos);
                } else if (searchCursorPos < searchQuery.length()) {
                    searchCursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                searchSelectAll = false;
                searchCursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                searchSelectAll = false;
                searchCursorPos = searchQuery.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_KP_ENTER || code == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                searchSelectAll = false;
                return true;
            }
        }

        // Undo / Redo for Button Editor text fields or global list
        if (isCtrl && code == GLFW.GLFW_KEY_Z) {
            if (selectedButton != null && focusField != -1) {
                if (isShift) {
                    java.util.Deque<String> rStack = fieldRedoStacks.get(focusField);
                    if (rStack != null && !rStack.isEmpty()) {
                        pushFieldUndo(focusField);
                        String val = rStack.pop();
                        setFieldText(focusField, val);
                        cursorPos = val.length();
                        selectAllOnType = false;
                        return true;
                    }
                } else {
                    java.util.Deque<String> uStack = fieldUndoStacks.get(focusField);
                    if (uStack != null && !uStack.isEmpty()) {
                        fieldRedoStacks.computeIfAbsent(focusField, k -> new java.util.ArrayDeque<>()).push(getFieldText(focusField));
                        String val = uStack.pop();
                        setFieldText(focusField, val);
                        cursorPos = val.length();
                        selectAllOnType = false;
                        return true;
                    }
                }
            }
            if (isShift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (isCtrl && code == GLFW.GLFW_KEY_Y) {
            if (selectedButton != null && focusField != -1) {
                java.util.Deque<String> rStack = fieldRedoStacks.get(focusField);
                if (rStack != null && !rStack.isEmpty()) {
                    pushFieldUndo(focusField);
                    String val = rStack.pop();
                    setFieldText(focusField, val);
                    cursorPos = val.length();
                    selectAllOnType = false;
                    return true;
                }
            }
            redo();
            return true;
        }

        if (code == GLFW.GLFW_KEY_ESCAPE) {
            saveCurrentEditor();
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        if (selectedButton != null) {
            // Autocomplete selection handling for Icons (field 2)
            if (focusField == 2 && !currentIconSuggestions.isEmpty()) {
                if (code == GLFW.GLFW_KEY_UP) {
                    selectedSuggestionIdx = (selectedSuggestionIdx - 1 + currentIconSuggestions.size()) % currentIconSuggestions.size();
                    if (selectedSuggestionIdx < suggestionScrollOffset) {
                        suggestionScrollOffset = selectedSuggestionIdx;
                    } else if (selectedSuggestionIdx >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                        suggestionScrollOffset = Math.max(0, selectedSuggestionIdx - MAX_VISIBLE_SUGGESTIONS + 1);
                    }
                    return true;
                } else if (code == GLFW.GLFW_KEY_DOWN) {
                    selectedSuggestionIdx = (selectedSuggestionIdx + 1) % currentIconSuggestions.size();
                    if (selectedSuggestionIdx >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                        suggestionScrollOffset = selectedSuggestionIdx - MAX_VISIBLE_SUGGESTIONS + 1;
                    } else if (selectedSuggestionIdx < suggestionScrollOffset) {
                        suggestionScrollOffset = selectedSuggestionIdx;
                    }
                    return true;
                } else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_TAB) {
                    editIcon = currentIconSuggestions.get(selectedSuggestionIdx);
                    currentIconSuggestions.clear();
                    cursorPos = editIcon.length();
                    return true;
                }
            }

            // Autocomplete selection handling for Sound (field 5)
            if (focusField == 5 && !currentSoundSuggestions.isEmpty()) {
                if (code == GLFW.GLFW_KEY_UP) {
                    selectedSoundSuggestionIdx = (selectedSoundSuggestionIdx - 1 + currentSoundSuggestions.size()) % currentSoundSuggestions.size();
                    if (selectedSoundSuggestionIdx < soundSuggestionScrollOffset) {
                        soundSuggestionScrollOffset = selectedSoundSuggestionIdx;
                    } else if (selectedSoundSuggestionIdx >= soundSuggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                        soundSuggestionScrollOffset = Math.max(0, selectedSoundSuggestionIdx - MAX_VISIBLE_SUGGESTIONS + 1);
                    }
                    return true;
                } else if (code == GLFW.GLFW_KEY_DOWN) {
                    selectedSoundSuggestionIdx = (selectedSoundSuggestionIdx + 1) % currentSoundSuggestions.size();
                    if (selectedSoundSuggestionIdx >= soundSuggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                        soundSuggestionScrollOffset = selectedSoundSuggestionIdx - MAX_VISIBLE_SUGGESTIONS + 1;
                    } else if (selectedSoundSuggestionIdx < soundSuggestionScrollOffset) {
                        soundSuggestionScrollOffset = selectedSoundSuggestionIdx;
                    }
                    return true;
                } else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_TAB) {
                    editClickSound = currentSoundSuggestions.get(selectedSoundSuggestionIdx);
                    currentSoundSuggestions.clear();
                    cursorPos = editClickSound.length();
                    return true;
                }
            }

            if (code == GLFW.GLFW_KEY_TAB) {
                if (focusField == 0) focusField = 1;
                else if (focusField == 1) focusField = 3;
                else if (focusField == 3) focusField = 4;
                else if (focusField == 4) focusField = 5;
                else if (focusField == 5) focusField = 2;
                else focusField = 0;
                cursorPos = getFieldText(focusField).length();
                selectAllOnType = false;
                if (focusField == 2) updateIconSuggestions();
                else currentIconSuggestions.clear();
                if (focusField == 5) updateSoundSuggestions();
                else currentSoundSuggestions.clear();
                return true;
            }

            // Select All
            if (isCtrl && code == GLFW.GLFW_KEY_A) {
                selectAllOnType = true;
                return true;
            }

            // Copy
            if (isCtrl && code == GLFW.GLFW_KEY_C) {
                String currentText = getFieldText(focusField);
                if (!currentText.isEmpty()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(currentText);
                }
                return true;
            }

            // Paste
            if (isCtrl && code == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    pushFieldUndo(focusField);
                    String currentText = getFieldText(focusField);
                    if (selectAllOnType) {
                        currentText = "";
                        cursorPos = 0;
                        selectAllOnType = false;
                    }
                    cursorPos = Math.max(0, Math.min(currentText.length(), cursorPos));
                    String updated = currentText.substring(0, cursorPos) + clip + currentText.substring(cursorPos);
                    setFieldText(focusField, updated);
                    cursorPos += clip.length();
                    return true;
                }
            }

            String currentText = getFieldText(focusField);

            if (code == GLFW.GLFW_KEY_LEFT) {
                selectAllOnType = false;
                if (isCtrl) {
                    cursorPos = getPrevWordIndex(currentText, cursorPos);
                } else {
                    if (cursorPos > 0) cursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                selectAllOnType = false;
                if (isCtrl) {
                    cursorPos = getNextWordIndex(currentText, cursorPos);
                } else {
                    if (cursorPos < currentText.length()) cursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                selectAllOnType = false;
                cursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                selectAllOnType = false;
                cursorPos = currentText.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                pushFieldUndo(focusField);
                if (selectAllOnType) {
                    setFieldText(focusField, "");
                    cursorPos = 0;
                    selectAllOnType = false;
                    return true;
                }

                if (isCtrl) {
                    int prev = getPrevWordIndex(currentText, cursorPos);
                    String updated = currentText.substring(0, prev) + currentText.substring(cursorPos);
                    cursorPos = prev;
                    setFieldText(focusField, updated);
                } else if (cursorPos > 0 && !currentText.isEmpty()) {
                    String updated = currentText.substring(0, cursorPos - 1) + currentText.substring(cursorPos);
                    cursorPos--;
                    setFieldText(focusField, updated);
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                pushFieldUndo(focusField);
                if (selectAllOnType) {
                    setFieldText(focusField, "");
                    cursorPos = 0;
                    selectAllOnType = false;
                    return true;
                }

                if (isCtrl) {
                    int next = getNextWordIndex(currentText, cursorPos);
                    String updated = currentText.substring(0, cursorPos) + currentText.substring(next);
                    setFieldText(focusField, updated);
                } else if (cursorPos < currentText.length()) {
                    String updated = currentText.substring(0, cursorPos) + currentText.substring(cursorPos + 1);
                    setFieldText(focusField, updated);
                }
                return true;
            }
        }

        return super.keyPressed(event);
    }

    private void triggerImport() {
        saveCurrentEditor();
        boolean append = Minecraft.getInstance() != null && Minecraft.getInstance().hasShiftDown();
        String json = InventoryButtonManager.readImportSource(null);
        if (json != null) {
            int count = InventoryButtonManager.importFirmamentButtons(json, InventoryButtonManager.getActiveProfile(), append);
            if (count > 0) {
                statusBanner = "§aImported " + count + " buttons from Firmament!";
                statusBannerTime = System.currentTimeMillis();
                List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
                if (!list.isEmpty()) {
                    selectButton(list.get(0));
                }
            } else {
                statusBanner = "§cFailed to parse JSON file/clipboard!";
                statusBannerTime = System.currentTimeMillis();
            }
        } else {
            statusBanner = "§cNo JSON in clipboard or firmament_buttons.json!";
            statusBannerTime = System.currentTimeMillis();
        }
    }
}
