package me.bombo.bomboaddons.gui.auto;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager.ActionType;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoAction;
import me.bombo.bomboaddons.features.auto.AutoSequenceManager.AutoSequence;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.bombo.bomboaddons.flavor.Flavor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Scratch-styled visual sequence editor.
 * Allows visual block creation, reordering, condition attachments (except-if, only-if),
 * dynamic variable insertion (${color}), and real-time execution controls.
 */
public class AutoSequenceVisualScreen extends Screen {

    private final Screen parent;
    private int activeSequenceIndex = 0;
    private double scrollAmount = 0.0;
    private double maxScroll = 0.0;

    // Editing modal / inline edit states
    private int editingActionIndex = -1;
    private EditBox primaryInput = null;
    private EditBox conditionInput = null;
    private EditBox delayInput = null;
    private String editingModalMode = null; // null, "EDIT_BLOCK", "RENAME_SEQ", "SET_KEY"
    private boolean conditionIsIfMode = true;

    // Drag-and-drop reordering state
    private int draggedActionIndex = -1;
    private double dragStartX = 0.0;
    private double dragStartY = 0.0;
    private boolean isDragging = false;
    private int dropTargetIndex = -1;

    // Hover tooltip container
    private final List<String> activeTooltip = new ArrayList<>();

    public AutoSequenceVisualScreen(Screen parent) {
        super(Component.literal("Auto Sequences Visual Studio"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        List<AutoSequence> list = AutoSequenceManager.getSequences();
        if (list.isEmpty()) {
            AutoSequence defaultSeq = new AutoSequence("My Sequence", "");
            defaultSeq.actions.add(AutoAction.clickSlot(10, "", "LEFT", 200));
            defaultSeq.actions.add(AutoAction.waitDelay(400));
            defaultSeq.actions.add(AutoAction.closeGui(100));
            AutoSequenceManager.addSequence(defaultSeq);
        }
        if (activeSequenceIndex >= list.size()) {
            activeSequenceIndex = Math.max(0, list.size() - 1);
        }
    }

    private AutoSequence getActiveSequence() {
        List<AutoSequence> list = AutoSequenceManager.getSequences();
        if (list.isEmpty()) return null;
        if (activeSequenceIndex < 0 || activeSequenceIndex >= list.size()) {
            activeSequenceIndex = 0;
        }
        return list.get(activeSequenceIndex);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        activeTooltip.clear();
        // Dark studio backdrop
        g.fill(0, 0, this.width, this.height, 0xF00A0D14);

        Font font = Minecraft.getInstance().font;
        int headerH = 44;

        // Top Header Bar
        g.fill(0, 0, this.width, headerH, 0xFF111827);
        g.fill(0, headerH - 1, this.width, headerH, 0xFF374151);

        // Header Title & Studio Badge
        g.text(font, "§b§lBombo§fStudio §8| §eVisual Sequence Builder", 14, 10, 0xFFFFFFFF, true);
        g.text(font, "§7Block-based sequence orchestrator with exceptions & dynamic conditions", 14, 25, 0xFF9CA3AF, false);

        AutoSequence seq = getActiveSequence();
        List<AutoSequence> allSeqs = AutoSequenceManager.getSequences();

        // Header Controls (Back, Sequence Selector, New, Run/Stop)
        int rightX = this.width - 12;

        // Back button
        int backBtnW = 60;
        rightX -= backBtnW;
        boolean hoverBack = mouseX >= rightX && mouseX <= rightX + backBtnW && mouseY >= 10 && mouseY <= 34;
        g.fill(rightX, 10, rightX + backBtnW, 34, hoverBack ? 0x446B7280 : 0x22374151);
        g.outline(rightX, 10, backBtnW, 24, hoverBack ? 0xFF9CA3AF : 0x449CA3AF);
        g.text(font, "← Back", rightX + 12, 18, 0xFFE5E7EB, false);

        rightX -= 10;

        // Run / Stop Execution Button
        boolean isRunning = seq != null && AutoSequenceManager.isRunning(seq);
        int runBtnW = 90;
        rightX -= runBtnW;
        boolean hoverRun = mouseX >= rightX && mouseX <= rightX + runBtnW && mouseY >= 10 && mouseY <= 34;
        int runBg = isRunning ? (hoverRun ? 0xFFEF4444 : 0xFFDC2626) : (hoverRun ? 0xFF10B981 : 0xFF059669);
        g.fill(rightX, 10, rightX + runBtnW, 34, runBg);
        g.outline(rightX, 10, runBtnW, 24, 0xFFFFFFFF);
        String runLabel = isRunning ? "⏹ Stop" : "▶ Run (F6)";
        g.text(font, runLabel, rightX + 16, 18, 0xFFFFFFFF, true);

        rightX -= 10;

        // Loop Toggle
        if (seq != null) {
            int loopBtnW = 85;
            rightX -= loopBtnW;
            boolean hoverLoop = mouseX >= rightX && mouseX <= rightX + loopBtnW && mouseY >= 10 && mouseY <= 34;
            int loopBg = seq.loop ? 0x44F59E0B : 0x22374151;
            g.fill(rightX, 10, rightX + loopBtnW, 34, loopBg);
            g.outline(rightX, 10, loopBtnW, 24, seq.loop ? 0xFFF59E0B : 0x449CA3AF);
            g.text(font, seq.loop ? "🔁 Loop: ON" : "🔁 Loop: OFF", rightX + 8, 18, seq.loop ? 0xFFFCD34D : 0xFF9CA3AF, false);

            rightX -= 10;

            // Trigger Key Button
            int keyBtnW = 95;
            rightX -= keyBtnW;
            boolean hoverKey = mouseX >= rightX && mouseX <= rightX + keyBtnW && mouseY >= 10 && mouseY <= 34;
            g.fill(rightX, 10, rightX + keyBtnW, 34, hoverKey ? 0x448B5CF6 : 0x22374151);
            g.outline(rightX, 10, keyBtnW, 24, hoverKey ? 0xFFA78BFA : 0x448B5CF6);
            String keyStr = (seq.triggerKey != null && !seq.triggerKey.isEmpty()) ? seq.triggerKey : "None";
            g.text(font, "⌨ Key: " + keyStr, rightX + 8, 18, 0xFFDDD6FE, false);
        }

        // Sequence Switcher in Center Header
        int selX = 360;
        if (rightX > selX + 160) {
            boolean hoverPrev = mouseX >= selX && mouseX <= selX + 22 && mouseY >= 10 && mouseY <= 34;
            g.fill(selX, 10, selX + 22, 34, hoverPrev ? 0x446B7280 : 0x22374151);
            g.text(font, "◀", selX + 7, 18, 0xFFFFFFFF, false);

            String seqName = seq != null ? seq.name : "None";
            int nameW = Math.min(180, font.width(seqName) + 20);
            int boxX = selX + 26;
            boolean hoverSeq = mouseX >= boxX && mouseX <= boxX + nameW && mouseY >= 10 && mouseY <= 34;
            g.fill(boxX, 10, boxX + nameW, 34, hoverSeq ? 0x440284C7 : 0x220284C7);
            g.outline(boxX, 10, nameW, 24, 0xFF38BDF8);
            g.text(font, seqName, boxX + 10, 18, 0xFFE0F2FE, true);

            int nextX = boxX + nameW + 4;
            boolean hoverNext = mouseX >= nextX && mouseX <= nextX + 22 && mouseY >= 10 && mouseY <= 34;
            g.fill(nextX, 10, nextX + 22, 34, hoverNext ? 0x446B7280 : 0x22374151);
            g.text(font, "▶", nextX + 7, 18, 0xFFFFFFFF, false);

            // New Sequence button
            int newX = nextX + 26;
            boolean hoverNew = mouseX >= newX && mouseX <= newX + 50 && mouseY >= 10 && mouseY <= 34;
            g.fill(newX, 10, newX + 50, 34, hoverNew ? 0x4410B981 : 0x2210B981);
            g.outline(newX, 10, 50, 24, 0xFF34D399);
            g.text(font, "+ New", newX + 8, 18, 0xFFA7F3D0, false);
        }

        // Layout Split: Left Block Palette (width: 220), Right Workspace Canvas
        int paletteW = 210;
        int canvasX = paletteW + 8;
        int canvasY = headerH + 8;
        int canvasW = this.width - canvasX - 12;
        int canvasH = this.height - canvasY - 8;

        // Render Left Palette
        renderPalette(g, font, 8, canvasY, paletteW - 8, canvasH, mouseX, mouseY);

        // Render Right Canvas Workspace
        renderCanvas(g, font, canvasX, canvasY, canvasW, canvasH, mouseX, mouseY);

        // Render Active Modal Dialog if open
        if (editingModalMode != null) {
            renderModal(g, font, mouseX, mouseY);
        }

        // Floating drag preview
        if (isDragging && draggedActionIndex >= 0 && getActiveSequence() != null
                && draggedActionIndex < getActiveSequence().actions.size()) {
            AutoAction dragging = getActiveSequence().actions.get(draggedActionIndex);
            int dragW = 230;
            int dragH = 34;
            int dragX = mouseX - 25;
            int dragY = mouseY - 17;
            g.fill(dragX, dragY, dragX + dragW, dragY + dragH, 0xEE1E293B);
            g.outline(dragX, dragY, dragW, dragH, 0xFF00E5FF);
            g.fill(dragX, dragY, dragX + 5, dragY + dragH, 0xFF00E5FF);
            g.text(font, "#" + (draggedActionIndex + 1) + " " + dragging.type.displayName, dragX + 12, dragY + 6, 0xFFFFFFFF, true);
            g.text(font, getActionParamsText(dragging), dragX + 12, dragY + 18, 0xFF94A3B8, false);
        }

        // Render Active Hover Tooltip on Top of Everything
        if (!activeTooltip.isEmpty()) {
            int maxLineW = 0;
            for (String s : activeTooltip) {
                maxLineW = Math.max(maxLineW, font.width(s));
            }
            int boxW = maxLineW + 16;
            int boxH = activeTooltip.size() * 11 + 8;
            int tipX = Math.min(mouseX + 12, this.width - boxW - 8);
            int tipY = Math.min(Math.max(8, mouseY - 8), this.height - boxH - 8);
            g.fill(tipX, tipY, tipX + boxW, tipY + boxH, 0xF50F172A);
            g.outline(tipX, tipY, boxW, boxH, 0xFF38BDF8);
            int ty = tipY + 5;
            for (String s : activeTooltip) {
                g.text(font, s, tipX + 8, ty, 0xFFFFFFFF, false);
                ty += 11;
            }
        }
    }

    private void renderPalette(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, 0xFF111827);
        g.outline(x, y, w, h, 0xFF1F2937);

        g.text(font, "BLOCK PALETTE", x + 10, y + 10, 0xFF9CA3AF, true);
        g.text(font, "Click to append block:", x + 10, y + 22, 0xFF6B7280, false);

        int curY = y + 38;
        int blockH = 26;

        // Action Blocks (Blue)
        g.text(font, "§9Actions", x + 10, curY, 0xFF60A5FA, false);
        curY += 14;

        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "▶ Click Slot", 0xFF2563EB, 0xFF3B82F6, mouseX, mouseY,
                "§9§lClick Slot", "§7Clicks a specific numeric slot inside an open container.", "§7Tip: Slot 0-53 for chests, 54-89 for inventory.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "▶ Click Item / Lore", 0xFF1D4ED8, 0xFF60A5FA, mouseX, mouseY,
                "§9§lClick Item / Lore", "§7Finds and clicks an item by name or tooltip lore.", "§7Supports 'lore:text' and 'starts_with:prefix'.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "■ Close Container", 0xFFDC2626, 0xFFEF4444, mouseX, mouseY,
                "§c§lClose Container", "§7Closes any open chest, backpack, or menu GUI.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "> Run Command", 0xFF7C3AED, 0xFF8B5CF6, mouseX, mouseY,
                "§5§lRun Command", "§7Executes a chat command (e.g. /warp garden, /bz).");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "* Click World", 0xFF0D9488, 0xFF14B8A6, mouseX, mouseY,
                "§2§lClick World", "§7Simulates a mouse click towards where crosshair looks.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "* Interact NPC", 0xFF0284C7, 0xFF38BDF8, mouseX, mouseY,
                "§3§lInteract NPC", "§7Interacts with the nearest NPC entity matching name.");
        curY += blockH + 12;

        // Flow & Timing Blocks (Amber)
        g.text(font, "§6Timing & Control", x + 10, curY, 0xFFFBBF24, false);
        curY += 14;

        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "~ Wait Delay (ms)", 0xFFD97706, 0xFFF59E0B, mouseX, mouseY,
                "§6§lWait Delay (ms)", "§7Pauses sequence execution for the specified milliseconds.");
        curY += blockH + 12;

        // Conditions & Dynamic Presets (Green / Orange)
        g.text(font, "§aConditions & Exceptions", x + 10, curY, 0xFF34D399, false);
        curY += 14;

        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "[!] Skip: When Full", 0xFFB91C1C, 0xFFF87171, mouseX, mouseY,
                "§c§lSkip: Container Full", "§7Bypasses this step if the open container has no empty slots.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "[!] Skip: Item Count", 0xFFC2410C, 0xFFFB923C, mouseX, mouseY,
                "§6§lSkip: Item Count", "§7Bypasses step if target item count reaches threshold.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "[!] Skip: Slot Has Item", 0xFF991B1B, 0xFFEF4444, mouseX, mouseY,
                "§c§lSkip: Slot Occupied", "§7Bypasses step if slot already has an item.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "[!] Skip: Has NO Item", 0xFF9A3412, 0xFFF97316, mouseX, mouseY,
                "§6§lSkip: Missing Item", "§7Bypasses step if target item is not found in menu.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "[+] Only: Menu Open", 0xFF047857, 0xFF34D399, mouseX, mouseY,
                "§a§lOnly: Menu Open", "§7Only runs if player is viewing a container screen.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "[+] Only: In Area", 0xFF065F46, 0xFF10B981, mouseX, mouseY,
                "§a§lOnly: In Area", "§7Only runs if player is inside the specified SkyBlock area.");
        curY += blockH + 4;
        renderPaletteItem(g, font, x + 6, curY, w - 12, blockH, "● Dynamic: ${color}", 0xFF6D28D9, 0xFFA78BFA, mouseX, mouseY,
                "§d§lDynamic ${color}", "§7Extracts color word from container title (e.g. Red, Blue, Green)", "§7and dynamically replaces ${color} parameter.");
    }

    private void renderPaletteItem(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String title, int color, int border, int mouseX, int mouseY, String... tooltip) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = hover ? color : (color & 0x00FFFFFF) | 0x33000000;
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, hover ? border : 0x55FFFFFF);
        g.text(font, title, x + 8, y + 8, 0xFFFFFFFF, false);
        if (hover && tooltip != null && tooltip.length > 0 && editingModalMode == null) {
            for (String line : tooltip) activeTooltip.add(line);
        }
    }

    public static String formatFriendlyCondition(String cond) {
        if (cond == null || cond.isBlank()) return "";
        String c = cond.trim();
        String lower = c.toLowerCase(Locale.ROOT);
        if (lower.equals("gui_full") || lower.equals("full")) return "Container Full";
        if (lower.equals("no_gui") || lower.equals("none")) return "No Menu Open";
        if (lower.startsWith("rows_full:") || lower.startsWith("rf:")) {
            return "Top " + c.substring(c.indexOf(':') + 1).trim() + " Rows Full";
        }
        if (lower.startsWith("empty_slots:") || lower.startsWith("empty:")) {
            return "Empty Slots " + c.substring(c.indexOf(':') + 1).trim();
        }
        if (lower.startsWith("no_item:") || lower.startsWith("!has_item:") || lower.startsWith("!item:")) {
            return "NO " + c.substring(c.indexOf(':') + 1).trim();
        }
        if (lower.startsWith("has_item:") || lower.startsWith("item:")) {
            return "Contains " + c.substring(c.indexOf(':') + 1).trim();
        }
        if (lower.startsWith("has_lore:") || lower.startsWith("lore:")) {
            return "Lore: \"" + c.substring(c.indexOf(':') + 1).trim() + "\"";
        }
        if (lower.startsWith("slot_empty:")) {
            return "Slot " + c.substring(11).trim() + " is Empty";
        }
        if (lower.startsWith("slot_has:") || lower.startsWith("slot:")) {
            String rest = c.substring(c.indexOf(':') + 1).trim();
            String[] parts = rest.split(",", 2);
            if (parts.length == 2) {
                return "Slot " + parts[0].trim() + " has " + parts[1].trim();
            }
            return "Slot " + rest;
        }
        if (lower.startsWith("item_count:") || lower.startsWith("count:") || lower.startsWith("repeat_until:")) {
            String rest = c.substring(c.indexOf(':') + 1).trim();
            return "Count of " + rest;
        }
        if (lower.startsWith("area:")) {
            return "In " + c.substring(5).trim();
        }
        if (lower.startsWith("subarea:")) {
            return "In " + c.substring(8).trim();
        }
        if (lower.startsWith("coords:") || lower.startsWith("c:")) {
            return "Near (" + c.substring(c.indexOf(':') + 1).trim() + ")";
        }
        if (lower.startsWith("in_gui:") || lower.startsWith("gui:")) {
            return "Menu \"" + c.substring(c.indexOf(':') + 1).trim() + "\"";
        }
        return c;
    }

    private void renderCanvas(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, 0xFF0D1117);
        g.outline(x, y, w, h, 0xFF1F2937);

        AutoSequence seq = getActiveSequence();
        if (seq == null) {
            g.text(font, "§cNo sequence active. Click '+ New' above to create one.", x + 20, y + 20, 0xFFEF4444, false);
            return;
        }

        List<AutoAction> actions = seq.actions;
        if (actions.isEmpty()) {
            g.text(font, "§7This sequence is empty.", x + 20, y + 20, 0xFF9CA3AF, false);
            g.text(font, "§eClick any block on the left palette to add steps to this sequence.", x + 20, y + 36, 0xFFFBBF24, false);
            return;
        }

        int blockH = 46;
        int blockGap = 6;
        int totalContentH = actions.size() * (blockH + blockGap) + 20;
        maxScroll = Math.max(0, totalContentH - (h - 20));
        scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));

        g.enableScissor(x, y, x + w, y + h);

        int curY = y + 10 - (int) scrollAmount;

        for (int i = 0; i < actions.size(); i++) {
            AutoAction action = actions.get(i);
            int cardY = curY;
            curY += blockH + blockGap;

            if (cardY + blockH < y || cardY > y + h) {
                continue;
            }

            // Draw insertion marker if dragging above this card
            if (isDragging && dropTargetIndex == i) {
                g.fill(x + 10, cardY - 4, x + w - 14, cardY - 1, 0xFF00E5FF);
                g.fill(x + 6, cardY - 5, x + 10, cardY, 0xFF00E5FF);
            }

            renderActionBlock(g, font, x + 10, cardY, w - 24, blockH, action, i, mouseX, mouseY);
        }

        if (isDragging && dropTargetIndex >= actions.size()) {
            int insertY = curY - 3;
            if (insertY >= y && insertY <= y + h) {
                g.fill(x + 10, insertY, x + w - 14, insertY + 3, 0xFF00E5FF);
            }
        }

        g.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = x + w - 8;
            g.fill(sbX, y + 4, sbX + 4, y + h - 4, 0x22FFFFFF);
            int thumbH = Math.max(20, (int) (((h - 8) / (float) totalContentH) * (h - 8)));
            int thumbY = y + 4 + (int) ((scrollAmount / maxScroll) * (h - 8 - thumbH));
            g.fill(sbX, thumbY, sbX + 4, thumbY + thumbH, 0xFF38BDF8);
        }
    }

    private void renderActionBlock(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, AutoAction action, int index, int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        // Block color coding based on type
        int color = switch (action.type) {
            case CLICK_SLOT -> 0xFF2563EB;
            case CLOSE_GUI -> 0xFFDC2626;
            case RUN_COMMAND -> 0xFF7C3AED;
            case CLICK_WORLD -> 0xFF0D9488;
            case INTERACT_ENTITY -> 0xFF0284C7;
            case WAIT -> 0xFFD97706;
        };

        // Dark card background with block accent border
        int bg = action.enabled ? (hover ? 0x2E1F2937 : 0x1A1F2937) : 0x0D111827;
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, action.enabled ? (hover ? 0xFF38BDF8 : 0xFF374151) : 0x224B5563);

        // Scratch block left color tab / notch
        g.fill(x, y, x + 6, y + h, action.enabled ? color : 0xFF4B5563);

        // Step number badge
        String stepStr = "#" + (index + 1);
        g.fill(x + 12, y + 8, x + 38, y + 26, 0x22FFFFFF);
        g.text(font, stepStr, x + 16, y + 13, 0xFFE5E7EB, true);

        // Block Icon & Main Title
        String typeTitle = action.type.displayName;
        g.text(font, typeTitle, x + 44, y + 8, 0xFFFFFFFF, true);

        // Target / Parameters Details
        String paramText = getActionParamsText(action);
        g.text(font, paramText, x + 44, y + 22, 0xFF9CA3AF, false);

        // Exception / Condition Badges
        int badgeX = x + 44 + font.width(paramText) + 8;
        if (action.exceptIf != null && !action.exceptIf.isBlank()) {
            String excLabel = "§c[Skip if: " + formatFriendlyCondition(action.exceptIf) + "]";
            int excW = font.width(excLabel) + 8;
            g.fill(badgeX, y + 20, badgeX + excW, y + 34, 0x33EF4444);
            g.outline(badgeX, y + 20, excW, 14, 0xFFEF4444);
            g.text(font, excLabel, badgeX + 4, y + 23, 0xFFFCA5A5, false);
            badgeX += excW + 6;
        }

        if (action.onlyIf != null && !action.onlyIf.isBlank()) {
            String onlyLabel = "§a[Only if: " + formatFriendlyCondition(action.onlyIf) + "]";
            int onlyW = font.width(onlyLabel) + 8;
            g.fill(badgeX, y + 20, badgeX + onlyW, y + 34, 0x3310B981);
            g.outline(badgeX, y + 20, onlyW, 14, 0xFF10B981);
            g.text(font, onlyLabel, badgeX + 4, y + 23, 0xFF6EE7B7, false);
        }

        // Right side tools: [▲ Move Up] [▼ Move Down] [✏ Edit] [⎘ Dup] [✔ Toggle] [🗑 Delete]
        int btnW = 20;
        int btnH = 20;
        int toolX = x + w - 10 - btnW;
        int toolY = y + 13;

        // Delete button
        boolean hoverDel = mouseX >= toolX && mouseX <= toolX + btnW && mouseY >= toolY && mouseY <= toolY + btnH;
        g.fill(toolX, toolY, toolX + btnW, toolY + btnH, hoverDel ? 0xFFEF4444 : 0x22374151);
        g.text(font, "✖", toolX + 6, toolY + 6, hoverDel ? 0xFFFFFFFF : 0xFFEF4444, false);
        toolX -= btnW + 4;

        // Toggle Enable/Disable
        boolean hoverToggle = mouseX >= toolX && mouseX <= toolX + btnW && mouseY >= toolY && mouseY <= toolY + btnH;
        int togColor = action.enabled ? 0xFF10B981 : 0xFF6B7280;
        g.fill(toolX, toolY, toolX + btnW, toolY + btnH, hoverToggle ? togColor : 0x22374151);
        g.text(font, action.enabled ? "✔" : "○", toolX + 6, toolY + 6, hoverToggle ? 0xFFFFFFFF : togColor, false);
        toolX -= btnW + 4;

        // Duplicate button
        boolean hoverDup = mouseX >= toolX && mouseX <= toolX + btnW && mouseY >= toolY && mouseY <= toolY + btnH;
        g.fill(toolX, toolY, toolX + btnW, toolY + btnH, hoverDup ? 0xFF3B82F6 : 0x22374151);
        g.text(font, "⎘", toolX + 6, toolY + 6, hoverDup ? 0xFFFFFFFF : 0xFF93C5FD, false);
        toolX -= btnW + 4;

        // Edit button
        boolean hoverEdit = mouseX >= toolX && mouseX <= toolX + btnW && mouseY >= toolY && mouseY <= toolY + btnH;
        g.fill(toolX, toolY, toolX + btnW, toolY + btnH, hoverEdit ? 0xFFF59E0B : 0x22374151);
        g.text(font, "✏", toolX + 6, toolY + 6, hoverEdit ? 0xFFFFFFFF : 0xFFFCD34D, false);
        toolX -= btnW + 4;

        // Move Down
        boolean hoverDown = mouseX >= toolX && mouseX <= toolX + btnW && mouseY >= toolY && mouseY <= toolY + btnH;
        g.fill(toolX, toolY, toolX + btnW, toolY + btnH, hoverDown ? 0x446B7280 : 0x22374151);
        g.text(font, "▼", toolX + 6, toolY + 6, 0xFFE5E7EB, false);
        toolX -= btnW + 4;

        // Move Up
        boolean hoverUp = mouseX >= toolX && mouseX <= toolX + btnW && mouseY >= toolY && mouseY <= toolY + btnH;
        g.fill(toolX, toolY, toolX + btnW, toolY + btnH, hoverUp ? 0x446B7280 : 0x22374151);
        g.text(font, "▲", toolX + 6, toolY + 6, 0xFFE5E7EB, false);

        if (hover && !isDragging && editingModalMode == null) {
            if (hoverDel) {
                activeTooltip.add("§c§lDelete Step");
                activeTooltip.add("§7Permanently removes this action block.");
            } else if (hoverToggle) {
                activeTooltip.add("§a§lToggle Step");
                activeTooltip.add("§7Enable or temporarily bypass this step.");
            } else if (hoverDup) {
                activeTooltip.add("§9§lDuplicate Step");
                activeTooltip.add("§7Creates a duplicate copy right below.");
            } else if (hoverEdit) {
                activeTooltip.add("§e§lEdit Step");
                activeTooltip.add("§7Open block settings, delay, and conditions.");
            } else if (hoverDown) {
                activeTooltip.add("§7Move Step Down");
            } else if (hoverUp) {
                activeTooltip.add("§7Move Step Up");
            } else {
                activeTooltip.add("§b§lStep #" + (index + 1) + ": §f" + typeTitle);
                activeTooltip.add("§7Target: §e" + paramText);
                activeTooltip.add("§7Delay: §a" + action.delayMs + "ms");
                if (action.exceptIf != null && !action.exceptIf.isBlank()) {
                    activeTooltip.add("§cSkip When: §f" + formatFriendlyCondition(action.exceptIf));
                }
                if (action.onlyIf != null && !action.onlyIf.isBlank()) {
                    activeTooltip.add("§aOnly Run When: §f" + formatFriendlyCondition(action.onlyIf));
                }
                activeTooltip.add("§8Click to edit • Drag to reorder");
            }
        }
    }

    private String getActionParamsText(AutoAction action) {
        return switch (action.type) {
            case CLICK_SLOT -> {
                String target = action.slotIndex >= 0 ? ("Slot #" + action.slotIndex)
                        : (action.itemMatcher != null && !action.itemMatcher.isEmpty() ? ("\"" + action.itemMatcher + "\"") : "Any Item");
                yield target + " • " + action.clickType + " • " + action.delayMs + "ms" + (action.repeatCount > 1 ? (" x" + action.repeatCount) : "");
            }
            case CLOSE_GUI -> "Close active container • " + action.delayMs + "ms";
            case RUN_COMMAND -> "\"" + action.command + "\" • " + action.delayMs + "ms";
            case CLICK_WORLD -> (action.rightClick ? "Right-Click" : "Left-Click") + " • " + action.delayMs + "ms";
            case INTERACT_ENTITY -> "NPC: \"" + action.entityMatcher + "\" • " + (action.rightClick ? "Right-Click" : "Left-Click") + " • " + action.delayMs + "ms";
            case WAIT -> "Pause sequence execution • " + action.delayMs + "ms";
        };
    }

    private void setFocusedInput(EditBox target, MouseButtonEvent event) {
        if (primaryInput != null) primaryInput.setFocused(primaryInput == target);
        if (delayInput != null) delayInput.setFocused(delayInput == target);
        if (conditionInput != null) conditionInput.setFocused(conditionInput == target);
        if (target != null && event != null) {
            target.mouseClicked(event, false);
        }
    }

    private void renderModal(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        // Modal backdrop overlay
        g.fill(0, 0, this.width, this.height, 0x88000000);

        int modalW = 440;
        int modalH = 295;
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2;

        g.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF1F2937);
        g.outline(modalX, modalY, modalW, modalH, 0xFF38BDF8);

        if ("RENAME_SEQ".equals(editingModalMode)) {
            g.text(font, "Rename Sequence", modalX + 16, modalY + 16, 0xFFFFFFFF, true);
            g.text(font, "Enter a new display name for this sequence:", modalX + 16, modalY + 36, 0xFF9CA3AF, false);
            if (primaryInput != null) {
                primaryInput.setPosition(modalX + 16, modalY + 60);
                primaryInput.extractRenderState(g, mouseX, mouseY, 0);
            }
        } else if ("SET_KEY".equals(editingModalMode)) {
            g.text(font, "Set Sequence Trigger Key", modalX + 16, modalY + 16, 0xFFFFFFFF, true);
            g.text(font, "Enter key name (e.g. F, R, V, NONE):", modalX + 16, modalY + 36, 0xFF9CA3AF, false);
            if (primaryInput != null) {
                primaryInput.setPosition(modalX + 16, modalY + 60);
                primaryInput.extractRenderState(g, mouseX, mouseY, 0);
            }
        } else if ("EDIT_BLOCK".equals(editingModalMode)) {
            AutoSequence seq = getActiveSequence();
            AutoAction action = (seq != null && editingActionIndex >= 0 && editingActionIndex < seq.actions.size())
                    ? seq.actions.get(editingActionIndex) : null;

            g.text(font, "Configure Step #" + (editingActionIndex + 1), modalX + 16, modalY + 14, 0xFFFFFFFF, true);

            if (action != null) {
                // Section 1: Target / Parameter
                String label1 = action.type == ActionType.CLICK_SLOT ? "Slot # or Item Matcher / ${color}:" :
                        (action.type == ActionType.RUN_COMMAND ? "Command or Chat Message:" : "Entity Matcher:");
                g.text(font, label1, modalX + 16, modalY + 32, 0xFF9CA3AF, false);
                if (primaryInput != null) {
                    primaryInput.setPosition(modalX + 16, modalY + 44);
                    primaryInput.setWidth(modalW - 32);
                    primaryInput.extractRenderState(g, mouseX, mouseY, 0);
                }

                // Section 2: Delay
                g.text(font, "Delay (ms):", modalX + 16, modalY + 72, 0xFF9CA3AF, false);
                if (delayInput != null) {
                    delayInput.setPosition(modalX + 16, modalY + 84);
                    delayInput.setWidth(100);
                    delayInput.extractRenderState(g, mouseX, mouseY, 0);
                }
                renderSmallChip(g, font, modalX + 124, modalY + 86, 48, 16, "50ms", mouseX, mouseY, "§7Fast 50ms pause");
                renderSmallChip(g, font, modalX + 176, modalY + 86, 54, 16, "100ms", mouseX, mouseY, "§7Standard 100ms pause");
                renderSmallChip(g, font, modalX + 234, modalY + 86, 54, 16, "200ms", mouseX, mouseY, "§7Safe 200ms pause");
                renderSmallChip(g, font, modalX + 292, modalY + 86, 54, 16, "500ms", mouseX, mouseY, "§7Relaxed 500ms pause");

                // Section 3: Scratch-Style Condition Guard Mode Tabs
                int tabY = modalY + 112;
                int tab1W = 155;
                int tab2W = 145;
                // Tab 1: [ ▶ Run ONLY IF (Condition) ]
                boolean hoverTab1 = mouseX >= modalX + 16 && mouseX <= modalX + 16 + tab1W && mouseY >= tabY && mouseY <= tabY + 18;
                int tab1Bg = conditionIsIfMode ? 0xFF059669 : (hoverTab1 ? 0x44059669 : 0x22374151);
                int tab1Border = conditionIsIfMode ? 0xFF10B981 : 0xFF4B5563;
                g.fill(modalX + 16, tabY, modalX + 16 + tab1W, tabY + 18, tab1Bg);
                g.outline(modalX + 16, tabY, tab1W, 18, tab1Border);
                g.text(font, "▶ Run ONLY IF (Condition)", modalX + 22, tabY + 5, conditionIsIfMode ? 0xFFFFFFFF : 0xFF9CA3AF, false);

                // Tab 2: [ ✕ SKIP IF (Exception) ]
                int tab2X = modalX + 16 + tab1W + 8;
                boolean hoverTab2 = mouseX >= tab2X && mouseX <= tab2X + tab2W && mouseY >= tabY && mouseY <= tabY + 18;
                int tab2Bg = !conditionIsIfMode ? 0xFFDC2626 : (hoverTab2 ? 0x44DC2626 : 0x22374151);
                int tab2Border = !conditionIsIfMode ? 0xFFEF4444 : 0xFF4B5563;
                g.fill(tab2X, tabY, tab2X + tab2W, tabY + 18, tab2Bg);
                g.outline(tab2X, tabY, tab2W, 18, tab2Border);
                g.text(font, "✕ SKIP IF (Exception)", tab2X + 16, tabY + 5, !conditionIsIfMode ? 0xFFFFFFFF : 0xFF9CA3AF, false);

                // Visual Condition Quick-Pick Pills
                int pillY = modalY + 138;
                if (conditionIsIfMode) {
                    renderConditionPill(g, font, modalX + 16, pillY, 82, 16, "★ Has Item", 0xFF0D9488, 0xFF14B8A6, mouseX, mouseY,
                            "§a§lCondition: Contains Item", "§7Only executes if target item exists in container/inventory,", "§7otherwise skips this step cleanly.");
                    renderConditionPill(g, font, modalX + 102, pillY, 82, 16, "📜 Has Lore", 0xFF0284C7, 0xFF38BDF8, mouseX, mouseY,
                            "§b§lCondition: Matches Lore", "§7Only executes if item lore contains specified text,", "§7otherwise skips this step cleanly.");
                    renderConditionPill(g, font, modalX + 188, pillY, 74, 16, "📦 In Menu", 0xFF6366F1, 0xFF818CF8, mouseX, mouseY,
                            "§d§lCondition: In Menu", "§7Only executes if container GUI is open,", "§7otherwise skips this step cleanly.");
                    renderConditionPill(g, font, modalX + 266, pillY, 80, 16, "🎯 Slot Has", 0xFFD97706, 0xFFF59E0B, mouseX, mouseY,
                            "§6§lCondition: Slot Has Item", "§7Format: slot_has:<slot>,<item> (e.g. slot_has:10,diamond)");
                    renderConditionPill(g, font, modalX + 350, pillY, 74, 16, "○ Empty", 0xFF4338CA, 0xFF6366F1, mouseX, mouseY,
                            "§9§lCondition: Slot Empty", "§7Only executes if slot is currently empty.");
                } else {
                    renderConditionPill(g, font, modalX + 16, pillY, 74, 16, "■ Full", 0xFFB91C1C, 0xFFEF4444, mouseX, mouseY,
                            "§c§lSkip: Container Full", "§7Skips this step if opened container has no empty slots left.");
                    renderConditionPill(g, font, modalX + 94, pillY, 82, 16, "✕ No Item", 0xFFC2410C, 0xFFFB923C, mouseX, mouseY,
                            "§6§lSkip: Missing Item", "§7Skips this step if target item or lore is missing.");
                    renderConditionPill(g, font, modalX + 180, pillY, 84, 16, "★ Has Item", 0xFF0D9488, 0xFF14B8A6, mouseX, mouseY,
                            "§a§lSkip: Contains Item", "§7Skips this step if target item already exists.");
                    renderConditionPill(g, font, modalX + 268, pillY, 78, 16, "○ Empty", 0xFF4338CA, 0xFF6366F1, mouseX, mouseY,
                            "§9§lSkip: Slot Empty", "§7Skips this step if target slot has no item.");
                    renderConditionPill(g, font, modalX + 350, pillY, 74, 16, "≡ In Menu", 0xFF0284C7, 0xFF38BDF8, mouseX, mouseY,
                            "§b§lSkip: In Menu", "§7Skips this step if container GUI is open.");
                }

                if (conditionInput != null) {
                    conditionInput.setPosition(modalX + 16, modalY + 160);
                    conditionInput.setWidth(modalW - 74);
                    conditionInput.extractRenderState(g, mouseX, mouseY, 0);

                    // Clear button
                    renderSmallChip(g, font, modalX + modalW - 54, modalY + 160, 38, 20, "Clear", mouseX, mouseY,
                            "§c§lClear Condition", "§7Removes condition so step always executes unconditionally.");

                    String condVal = conditionInput.getValue().trim();
                    if (!condVal.isEmpty()) {
                        String friendlyDesc = conditionIsIfMode
                                ? "▶ Reads as: Run ONLY IF " + formatFriendlyCondition(condVal) + " (otherwise skip)"
                                : "✕ Reads as: SKIP step IF " + formatFriendlyCondition(condVal);
                        g.text(font, friendlyDesc, modalX + 16, modalY + 186, conditionIsIfMode ? 0xFF34D399 : 0xFFF87171, false);
                    } else {
                        g.text(font, "§8(No condition set - step always executes unconditionally)", modalX + 16, modalY + 186, 0xFF6B7280, false);
                    }
                }
            }
        }

        // Modal Action Buttons (Save & Cancel)
        int btnW = 80;
        int btnH = 22;
        int saveX = modalX + modalW - btnW * 2 - 20;
        int cancelX = modalX + modalW - btnW - 10;
        int btnY = modalY + modalH - 34;

        boolean hoverSave = mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(saveX, btnY, saveX + btnW, btnY + btnH, hoverSave ? 0xFF059669 : 0xFF10B981);
        g.text(font, "Save", saveX + 26, btnY + 7, 0xFFFFFFFF, true);

        boolean hoverCancel = mouseX >= cancelX && mouseX <= cancelX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        g.fill(cancelX, btnY, cancelX + btnW, btnY + btnH, hoverCancel ? 0xFFDC2626 : 0xFFEF4444);
        g.text(font, "Cancel", cancelX + 22, btnY + 7, 0xFFFFFFFF, true);
    }

    private void renderSmallChip(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, int mouseX, int mouseY, String... tooltip) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g.fill(x, y, x + w, y + h, hover ? 0x446B7280 : 0x22374151);
        g.outline(x, y, w, h, hover ? 0xFF9CA3AF : 0x449CA3AF);
        int textW = font.width(text);
        g.text(font, text, x + (w - textW) / 2, y + (h - 8) / 2, hover ? 0xFFFFFFFF : 0xFFD1D5DB, false);
        if (hover && tooltip != null && tooltip.length > 0) {
            for (String line : tooltip) activeTooltip.add(line);
        }
    }

    private void renderConditionPill(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String text, int colorBg, int colorBorder, int mouseX, int mouseY, String... tooltip) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g.fill(x, y, x + w, y + h, hover ? colorBg : (colorBg & 0x66FFFFFF));
        g.outline(x, y, w, h, colorBorder);
        int textW = font.width(text);
        g.text(font, text, x + (w - textW) / 2, y + (h - 8) / 2, 0xFFFFFFFF, false);
        if (hover && tooltip != null && tooltip.length > 0) {
            for (String line : tooltip) activeTooltip.add(line);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        if (editingModalMode != null) return true;
        if (maxScroll > 0) {
            scrollAmount -= amountY * 28.0;
            scrollAmount = Math.max(0, Math.min(scrollAmount, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();

            // Handle Modal Clicks
            if (editingModalMode != null) {
                int modalW = 440;
                int modalH = 295;
                int modalX = (this.width - modalW) / 2;
                int modalY = (this.height - modalH) / 2;
                int btnW = 80;
                int btnH = 22;
                int saveX = modalX + modalW - btnW * 2 - 20;
                int cancelX = modalX + modalW - btnW - 10;
                int btnY = modalY + modalH - 34;

                if (mx >= saveX && mx <= saveX + btnW && my >= btnY && my <= btnY + btnH) {
                    commitModal();
                    return true;
                }
                if (mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + btnH) {
                    editingModalMode = null;
                    return true;
                }

                if ("EDIT_BLOCK".equals(editingModalMode)) {
                    // Check quick delay chips
                    if (my >= modalY + 86 && my <= modalY + 102) {
                        if (mx >= modalX + 124 && mx <= modalX + 172 && delayInput != null) {
                            delayInput.setValue("50");
                            setFocusedInput(delayInput, null);
                            return true;
                        }
                        if (mx >= modalX + 176 && mx <= modalX + 230 && delayInput != null) {
                            delayInput.setValue("100");
                            setFocusedInput(delayInput, null);
                            return true;
                        }
                        if (mx >= modalX + 234 && mx <= modalX + 288 && delayInput != null) {
                            delayInput.setValue("200");
                            setFocusedInput(delayInput, null);
                            return true;
                        }
                        if (mx >= modalX + 292 && mx <= modalX + 346 && delayInput != null) {
                            delayInput.setValue("500");
                            setFocusedInput(delayInput, null);
                            return true;
                        }
                    }

                    // Check Condition Mode Switcher Tabs
                    AutoSequence seq = getActiveSequence();
                    int tabY = modalY + 112;
                    int tab1W = 155;
                    int tab2W = 145;
                    int tab2X = modalX + 16 + tab1W + 8;
                    if (my >= tabY && my <= tabY + 18) {
                        if (mx >= modalX + 16 && mx <= modalX + 16 + tab1W) {
                            conditionIsIfMode = true;
                            AutoAction act = (seq != null && editingActionIndex >= 0 && editingActionIndex < seq.actions.size()) ? seq.actions.get(editingActionIndex) : null;
                            if (act != null && conditionInput != null && act.onlyIf != null && !act.onlyIf.isBlank()) {
                                conditionInput.setValue(act.onlyIf);
                            }
                            return true;
                        }
                        if (mx >= tab2X && mx <= tab2X + tab2W) {
                            conditionIsIfMode = false;
                            AutoAction act = (seq != null && editingActionIndex >= 0 && editingActionIndex < seq.actions.size()) ? seq.actions.get(editingActionIndex) : null;
                            if (act != null && conditionInput != null && act.exceptIf != null && !act.exceptIf.isBlank()) {
                                conditionInput.setValue(act.exceptIf);
                            }
                            return true;
                        }
                    }

                    // Check quick condition pills
                    int pillY = modalY + 138;
                    if (my >= pillY && my <= pillY + 16) {
                        if (conditionIsIfMode) {
                            if (mx >= modalX + 16 && mx <= modalX + 98 && conditionInput != null) {
                                conditionInput.setValue("has_item:");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 102 && mx <= modalX + 184 && conditionInput != null) {
                                conditionInput.setValue("has_lore:");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 188 && mx <= modalX + 262 && conditionInput != null) {
                                conditionInput.setValue("in_gui:Chest");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 266 && mx <= modalX + 346 && conditionInput != null) {
                                conditionInput.setValue("slot_has:0,Diamond");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 350 && mx <= modalX + 424 && conditionInput != null) {
                                conditionInput.setValue("slot_empty:0");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                        } else {
                            if (mx >= modalX + 16 && mx <= modalX + 90 && conditionInput != null) {
                                conditionInput.setValue("gui_full");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 94 && mx <= modalX + 176 && conditionInput != null) {
                                conditionInput.setValue("no_item:");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 180 && mx <= modalX + 264 && conditionInput != null) {
                                conditionInput.setValue("has_item:");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 268 && mx <= modalX + 346 && conditionInput != null) {
                                conditionInput.setValue("slot_empty:0");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                            if (mx >= modalX + 350 && mx <= modalX + 424 && conditionInput != null) {
                                conditionInput.setValue("in_gui:Chest");
                                setFocusedInput(conditionInput, null);
                                conditionInput.moveCursorToEnd(false);
                                return true;
                            }
                        }
                    }

                    // Clear condition button
                    if (mx >= modalX + modalW - 54 && mx <= modalX + modalW - 16 && my >= modalY + 160 && my <= modalY + 180) {
                        if (conditionInput != null) {
                            conditionInput.setValue("");
                            setFocusedInput(conditionInput, null);
                            return true;
                        }
                    }
                }

                if (primaryInput != null && primaryInput.isMouseOver(mx, my)) {
                    setFocusedInput(primaryInput, event);
                    return true;
                }
                if (delayInput != null && delayInput.isMouseOver(mx, my)) {
                    setFocusedInput(delayInput, event);
                    return true;
                }
                if (conditionInput != null && conditionInput.isMouseOver(mx, my)) {
                    setFocusedInput(conditionInput, event);
                    return true;
                }
                return true; // Modal is blocking
            }

            // Top Header Button Clicks
            int rightX = this.width - 12;

            // Back button
            int backBtnW = 60;
            rightX -= backBtnW;
            if (mx >= rightX && mx <= rightX + backBtnW && my >= 10 && my <= 34) {
                this.onClose();
                return true;
            }

            rightX -= 10;

            // Run / Stop Execution Button
            AutoSequence seq = getActiveSequence();
            int runBtnW = 90;
            rightX -= runBtnW;
            if (mx >= rightX && mx <= rightX + runBtnW && my >= 10 && my <= 34) {
                if (seq != null) {
                    if (AutoSequenceManager.isRunning(seq)) {
                        Flavor.get().stopAll();
                    } else {
                        int idx = AutoSequenceManager.getSequences().indexOf(seq);
                        if (idx >= 0) {
                            Flavor.get().toggleSequenceByIndex(idx, "Studio", "Visual Studio");
                        }
                    }
                }
                return true;
            }

            rightX -= 10;

            // Loop Toggle
            if (seq != null) {
                int loopBtnW = 85;
                rightX -= loopBtnW;
                if (mx >= rightX && mx <= rightX + loopBtnW && my >= 10 && my <= 34) {
                    seq.loop = !seq.loop;
                    AutoSequenceManager.save();
                    return true;
                }

                rightX -= 10;

                // Trigger Key Button
                int keyBtnW = 95;
                rightX -= keyBtnW;
                if (mx >= rightX && mx <= rightX + keyBtnW && my >= 10 && my <= 34) {
                    openKeyModal(seq);
                    return true;
                }
            }

            // Sequence Switcher in Center Header
            int selX = 360;
            List<AutoSequence> allSeqs = AutoSequenceManager.getSequences();
            if (mx >= selX && mx <= selX + 22 && my >= 10 && my <= 34) {
                if (activeSequenceIndex > 0) {
                    activeSequenceIndex--;
                    scrollAmount = 0;
                }
                return true;
            }

            String seqName = seq != null ? seq.name : "None";
            int nameW = Math.min(180, font.width(seqName) + 20);
            int boxX = selX + 26;
            if (mx >= boxX && mx <= boxX + nameW && my >= 10 && my <= 34) {
                openRenameModal(seq);
                return true;
            }

            int nextX = boxX + nameW + 4;
            if (mx >= nextX && mx <= nextX + 22 && my >= 10 && my <= 34) {
                if (activeSequenceIndex + 1 < allSeqs.size()) {
                    activeSequenceIndex++;
                    scrollAmount = 0;
                }
                return true;
            }

            // New Sequence button
            int newX = nextX + 26;
            if (mx >= newX && mx <= newX + 50 && my >= 10 && my <= 34) {
                AutoSequence created = new AutoSequence("Sequence #" + (allSeqs.size() + 1), "");
                created.actions.add(AutoAction.clickSlot(10, "", "LEFT", 200));
                AutoSequenceManager.addSequence(created);
                activeSequenceIndex = allSeqs.size() - 1;
                scrollAmount = 0;
                return true;
            }

            // Left Palette Clicks
            int paletteW = 210;
            int canvasY = 44 + 8;
            int canvasH = this.height - canvasY - 8;
            if (mx >= 8 && mx <= paletteW) {
                handlePaletteClick(mx, my, canvasY);
                return true;
            }

            // Right Canvas Clicks
            int canvasX = paletteW + 8;
            int canvasW = this.width - canvasX - 12;
            if (mx >= canvasX && mx <= canvasX + canvasW && my >= canvasY && my <= canvasY + canvasH) {
                handleCanvasClick(mx, my, canvasX, canvasY, canvasW, canvasH);
                return true;
            }
        }
        return super.mouseClicked(event, handled);
    }

    private void handlePaletteClick(double mx, double my, int canvasY) {
        AutoSequence seq = getActiveSequence();
        if (seq == null) return;

        int curY = canvasY + 38 + 14;
        int blockH = 26;

        // Click Slot
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.clickSlot(10, "", "LEFT", 200));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Click Item / Lore
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.clickSlot(-1, "Diamond", "LEFT", 200));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Close GUI
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.closeGui(150));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Run Command
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.runCommand("/wardrobe", 300));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Click World
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.clickWorld(true, 250));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Click NPC
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.interactEntity("Plushie", 5.0, true, 200));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 12 + 14;

        // Wait Delay
        if (my >= curY && my <= curY + blockH) {
            seq.actions.add(AutoAction.waitDelay(500));
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 12 + 14;

        // Except If: GUI Full
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.clickSlot(10, "", "LEFT", 200);
            a.exceptIf = "gui_full";
            seq.actions.add(a);
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Except If: Item Count
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.closeGui(100);
            a.exceptIf = "item_count:diamond>=64";
            seq.actions.add(a);
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Except If: Slot Has Item
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.clickSlot(10, "diamond", "SHIFT_LEFT", 200);
            a.exceptIf = "slot_has:10,diamond";
            seq.actions.add(a);
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Except If: Has NO Item
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.closeGui(100);
            a.exceptIf = "no_item:diamond";
            seq.actions.add(a);
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Only If: Menu Open
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.clickSlot(10, "", "LEFT", 200);
            a.onlyIf = "in_gui:Chest";
            seq.actions.add(a);
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Only If: In Area
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.runCommand("/warp hub", 400);
            a.onlyIf = "area:Hub";
            seq.actions.add(a);
            AutoSequenceManager.save();
            return;
        }
        curY += blockH + 4;

        // Dynamic ${color}
        if (my >= curY && my <= curY + blockH) {
            AutoAction a = AutoAction.clickSlot(-1, "${color}", "LEFT", 200);
            seq.actions.add(a);
            AutoSequenceManager.save();
        }
    }

    private void handleCanvasClick(double mx, double my, int x, int y, int w, int h) {
        AutoSequence seq = getActiveSequence();
        if (seq == null || seq.actions.isEmpty()) return;

        int blockH = 46;
        int blockGap = 6;
        int curY = y + 10 - (int) scrollAmount;

        for (int i = 0; i < seq.actions.size(); i++) {
            AutoAction action = seq.actions.get(i);
            int cardY = curY;
            curY += blockH + blockGap;

            if (cardY + blockH < y || cardY > y + h) continue;

            if (mx >= x + 10 && mx <= x + w - 14 && my >= cardY && my <= cardY + blockH) {
                int btnW = 20;
                int btnH = 20;
                int toolX = x + 10 + (w - 24) - 10 - btnW;
                int toolY = cardY + 13;

                // Delete
                if (mx >= toolX && mx <= toolX + btnW && my >= toolY && my <= toolY + btnH) {
                    seq.actions.remove(i);
                    AutoSequenceManager.save();
                    return;
                }
                toolX -= btnW + 4;

                // Toggle Enable
                if (mx >= toolX && mx <= toolX + btnW && my >= toolY && my <= toolY + btnH) {
                    action.enabled = !action.enabled;
                    AutoSequenceManager.save();
                    return;
                }
                toolX -= btnW + 4;

                // Duplicate
                if (mx >= toolX && mx <= toolX + btnW && my >= toolY && my <= toolY + btnH) {
                    seq.actions.add(i + 1, action.copy());
                    AutoSequenceManager.save();
                    return;
                }
                toolX -= btnW + 4;

                // Edit
                if (mx >= toolX && mx <= toolX + btnW && my >= toolY && my <= toolY + btnH) {
                    openEditBlockModal(seq, i);
                    return;
                }
                toolX -= btnW + 4;

                // Move Down
                if (mx >= toolX && mx <= toolX + btnW && my >= toolY && my <= toolY + btnH) {
                    if (i + 1 < seq.actions.size()) {
                        AutoAction item = seq.actions.remove(i);
                        seq.actions.add(i + 1, item);
                        AutoSequenceManager.save();
                    }
                    return;
                }
                toolX -= btnW + 4;

                // Move Up
                if (mx >= toolX && mx <= toolX + btnW && my >= toolY && my <= toolY + btnH) {
                    if (i > 0) {
                        AutoAction item = seq.actions.remove(i);
                        seq.actions.add(i - 1, item);
                        AutoSequenceManager.save();
                    }
                    return;
                }

                // Dragging or clicking block body
                draggedActionIndex = i;
                dragStartX = mx;
                dragStartY = my;
                isDragging = false;
                dropTargetIndex = i;
                return;
            }
        }
    }

    private void openRenameModal(AutoSequence seq) {
        if (seq == null) return;
        editingModalMode = "RENAME_SEQ";
        Font font = Minecraft.getInstance().font;
        primaryInput = new EditBox(font, 0, 0, 380, 20, Component.literal("Name"));
        primaryInput.setMaxLength(32);
        primaryInput.setValue(seq.name);
        primaryInput.setFocused(true);
    }

    private void openKeyModal(AutoSequence seq) {
        if (seq == null) return;
        editingModalMode = "SET_KEY";
        Font font = Minecraft.getInstance().font;
        primaryInput = new EditBox(font, 0, 0, 380, 20, Component.literal("Key"));
        primaryInput.setMaxLength(16);
        primaryInput.setValue(seq.triggerKey != null ? seq.triggerKey : "");
        primaryInput.setFocused(true);
    }

    private void openEditBlockModal(AutoSequence seq, int actionIdx) {
        if (seq == null || actionIdx < 0 || actionIdx >= seq.actions.size()) return;
        editingActionIndex = actionIdx;
        editingModalMode = "EDIT_BLOCK";
        AutoAction a = seq.actions.get(actionIdx);

        Font font = Minecraft.getInstance().font;
        primaryInput = new EditBox(font, 0, 0, 380, 20, Component.literal("Param"));
        primaryInput.setMaxLength(64);
        String val = a.type == ActionType.CLICK_SLOT ? (a.slotIndex >= 0 ? String.valueOf(a.slotIndex) : a.itemMatcher)
                : (a.type == ActionType.RUN_COMMAND ? a.command : a.entityMatcher);
        primaryInput.setValue(val != null ? val : "");

        delayInput = new EditBox(font, 0, 0, 380, 20, Component.literal("Delay"));
        delayInput.setMaxLength(8);
        delayInput.setValue(String.valueOf(a.delayMs));

        conditionInput = new EditBox(font, 0, 0, 380, 20, Component.literal("Condition"));
        conditionInput.setMaxLength(64);
        if (a.onlyIf != null && !a.onlyIf.isBlank()) {
            conditionIsIfMode = true;
            conditionInput.setValue(a.onlyIf);
        } else if (a.exceptIf != null && !a.exceptIf.isBlank()) {
            conditionIsIfMode = false;
            conditionInput.setValue(a.exceptIf);
        } else {
            conditionIsIfMode = true;
            conditionInput.setValue("");
        }

        primaryInput.setFocused(true);
    }

    private void commitModal() {
        AutoSequence seq = getActiveSequence();
        if ("RENAME_SEQ".equals(editingModalMode)) {
            if (seq != null && primaryInput != null) {
                String name = primaryInput.getValue().trim();
                if (!name.isEmpty()) seq.name = name;
                AutoSequenceManager.save();
            }
        } else if ("SET_KEY".equals(editingModalMode)) {
            if (seq != null && primaryInput != null) {
                seq.triggerKey = primaryInput.getValue().trim().toUpperCase(Locale.ROOT);
                AutoSequenceManager.save();
            }
        } else if ("EDIT_BLOCK".equals(editingModalMode)) {
            if (seq != null && editingActionIndex >= 0 && editingActionIndex < seq.actions.size()) {
                AutoAction a = seq.actions.get(editingActionIndex);
                if (primaryInput != null) {
                    String p = primaryInput.getValue().trim();
                    if (a.type == ActionType.CLICK_SLOT) {
                        try {
                            a.slotIndex = Integer.parseInt(p);
                            a.itemMatcher = "";
                        } catch (NumberFormatException e) {
                            a.slotIndex = -1;
                            a.itemMatcher = p;
                        }
                    } else if (a.type == ActionType.RUN_COMMAND) {
                        a.command = p;
                    } else if (a.type == ActionType.INTERACT_ENTITY) {
                        a.entityMatcher = p;
                    }
                }
                if (delayInput != null) {
                    try {
                        a.delayMs = Math.max(10, Integer.parseInt(delayInput.getValue().trim()));
                    } catch (Throwable ignored) {}
                }
                if (conditionInput != null) {
                    String cond = conditionInput.getValue().trim();
                    if (conditionIsIfMode) {
                        a.onlyIf = cond;
                        a.exceptIf = "";
                    } else {
                        a.exceptIf = cond;
                        a.onlyIf = "";
                    }
                }
                AutoSequenceManager.save();
            }
        }
        editingModalMode = null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingModalMode != null) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                commitModal();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                editingModalMode = null;
                return true;
            }
            if (primaryInput != null && primaryInput.isFocused() && primaryInput.keyPressed(event)) return true;
            if (delayInput != null && delayInput.isFocused() && delayInput.keyPressed(event)) return true;
            if (conditionInput != null && conditionInput.isFocused() && conditionInput.keyPressed(event)) return true;
            return true;
        }

        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingModalMode != null) {
            if (primaryInput != null && primaryInput.isFocused() && primaryInput.charTyped(event)) return true;
            if (delayInput != null && delayInput.isFocused() && delayInput.charTyped(event)) return true;
            if (conditionInput != null && conditionInput.isFocused() && conditionInput.charTyped(event)) return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (editingModalMode != null) {
            Font font = Minecraft.getInstance().font;
            EditBox focusedBox = (primaryInput != null && primaryInput.isFocused()) ? primaryInput :
                    ((delayInput != null && delayInput.isFocused()) ? delayInput :
                    ((conditionInput != null && conditionInput.isFocused()) ? conditionInput : null));
            if (focusedBox != null && font != null) {
                int relX = (int) (event.x() - focusedBox.getX() - 4);
                String val = focusedBox.getValue();
                if (relX <= 0) {
                    focusedBox.moveCursorTo(0, true);
                } else {
                    int charIdx = font.plainSubstrByWidth(val, relX).length();
                    focusedBox.moveCursorTo(charIdx, true);
                }
                return true;
            }
        }

        if (draggedActionIndex >= 0) {
            double mx = event.x();
            double my = event.y();
            if (!isDragging && Math.hypot(mx - dragStartX, my - dragStartY) > 4) {
                isDragging = true;
            }
            if (isDragging) {
                AutoSequence seq = getActiveSequence();
                if (seq != null) {
                    int headerH = 44;
                    int canvasY = headerH + 8;
                    int blockH = 46;
                    int blockGap = 6;
                    int relY = (int) (my - (canvasY + 10 - scrollAmount));
                    int target = (int) Math.round((double) relY / (blockH + blockGap));
                    dropTargetIndex = Math.max(0, Math.min(target, seq.actions.size()));
                }
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (editingModalMode != null) {
            if (primaryInput != null && primaryInput.isFocused()) primaryInput.mouseReleased(event);
            if (delayInput != null && delayInput.isFocused()) delayInput.mouseReleased(event);
            if (conditionInput != null && conditionInput.isFocused()) conditionInput.mouseReleased(event);
        }

        if (isDragging && draggedActionIndex >= 0) {
            AutoSequence seq = getActiveSequence();
            if (seq != null && dropTargetIndex >= 0 && dropTargetIndex != draggedActionIndex) {
                if (draggedActionIndex < seq.actions.size()) {
                    AutoAction a = seq.actions.remove(draggedActionIndex);
                    int insertIdx = dropTargetIndex;
                    if (insertIdx > draggedActionIndex) insertIdx--;
                    insertIdx = Math.max(0, Math.min(insertIdx, seq.actions.size()));
                    seq.actions.add(insertIdx, a);
                    AutoSequenceManager.save();
                }
            }
        } else if (!isDragging && draggedActionIndex >= 0) {
            AutoSequence seq = getActiveSequence();
            if (seq != null && draggedActionIndex < seq.actions.size()) {
                openEditBlockModal(seq, draggedActionIndex);
            }
        }
        draggedActionIndex = -1;
        isDragging = false;
        dropTargetIndex = -1;
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
