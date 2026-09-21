package me.bombo.bomboaddons.features.swapper;

import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.ItemCustomizeScreen;
import me.bombo.bomboaddons.SkyblockUtils;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class InventorySwapScreen extends Screen {
    private final Screen parent;

    private int winX, winY, winW, winH;
    private final int headerH = 40;
    private double scrollY = 0.0;
    private double maxScrollY = 0.0;

    // Grid selection in screen
    private int selectedGridSlot = -1;

    // Editing filter on a rule
    private InventorySlotSwapManager.SwapRule editingRule = null;
    private int editFilterField = 0; // 0 = Skyblock ID, 1 = Name, 2 = UUID
    private int cursorPos = 0;
    private boolean selectAllOnType = false;

    // Profile selector popup
    private boolean viewAllProfiles = false;
    private InventorySlotSwapManager.SwapRule pickingProfileRule = null;
    private int pickingProfileX = 0;
    private int pickingProfileY = 0;

    public InventorySwapScreen(Screen parent) {
        super(Component.literal("Inventory Slot Swapper"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        InventorySlotSwapManager.init();
        int targetW = 860;
        int targetH = 540;
        this.winW = Math.min(this.width - 20, targetW);
        this.winH = Math.min(this.height - 20, targetH);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Dark backdrop
        g.fill(0, 0, this.width, this.height, 0xD0080A0E);

        int mainBg = ConfigUITheme.getMainWindowBg();
        int headerBg = ConfigUITheme.getHeaderBg();
        int borderCol = ConfigUITheme.getBorderColor();
        int divCol = ConfigUITheme.getDividerColor();
        Font font = this.font;

        // Window outline & bg
        g.fill(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, 0x33000000);
        g.fill(winX, winY, winX + winW, winY + winH, mainBg);
        g.outline(winX, winY, winW, winH, borderCol);

        // Header
        g.fill(winX, winY, winX + winW, winY + headerH, headerBg);
        g.fill(winX, winY + headerH, winX + winW, winY + headerH + 1, divCol);

        String activeProf = InventorySlotSwapManager.getActiveProfile();
        g.text(font, "§d§lBOMBOADDONS §8| §bSwapper §8(§7Profile: §e" + activeProf + "§8)", winX + 16, winY + 14, 0xFFFFFFFF, false);

        // Dynamic right-to-left layout for header buttons
        int rightX = winX + winW - 12;
        int closeBtnW = 20;
        int closeBtnX = rightX - closeBtnW;
        rightX -= (closeBtnW + 8);

        int trigBtnW = 165;
        int trigBtnX = rightX - trigBtnW;
        rightX -= (trigBtnW + 6);

        int viewBtnW = 145;
        int viewBtnX = rightX - viewBtnW;
        rightX -= (viewBtnW + 6);

        int colBtnW = 105;
        int colBtnX = rightX - colBtnW;

        // Slot Colors Button
        int colBtnY = winY + 9;
        boolean colHover = mouseX >= colBtnX && mouseX <= colBtnX + colBtnW && mouseY >= colBtnY && mouseY <= colBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, "🎨 Slot Colors", colBtnX, colBtnY, colBtnW, 22, colHover,
                0xFFF59E0B, colHover ? 0x44D97706 : 0x22D97706, 0xFFD97706);

        // View Filter Setting Pill
        int viewBtnY = winY + 9;
        String viewLabel = viewAllProfiles ? "View: All Profiles" : ("View: " + activeProf);
        boolean viewHover = mouseX >= viewBtnX && mouseX <= viewBtnX + viewBtnW && mouseY >= viewBtnY && mouseY <= viewBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, viewLabel, viewBtnX, viewBtnY, viewBtnW, 22, viewHover,
                0xFF10B981, viewHover ? 0x44059669 : 0x22059669, 0xFF059669);

        // Swap Trigger Setting Pill
        BomboConfig.Settings s = BomboConfig.get();
        String trigger = (s != null && s.inventorySlotSwapTrigger != null) ? s.inventorySlotSwapTrigger : "Shift";
        int trigBtnY = winY + 9;
        boolean trigHover = mouseX >= trigBtnX && mouseX <= trigBtnX + trigBtnW && mouseY >= trigBtnY && mouseY <= trigBtnY + 22;
        ConfigUITheme.drawPillButton(g, font, "Trigger: §e" + trigger + " + Click", trigBtnX, trigBtnY, trigBtnW, 22, trigHover,
                0xFF38BDF8, trigHover ? 0x440284C7 : 0x220284C7, 0xFF0284C7);

        // Close button
        int closeBtnY = winY + 10;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnW && mouseY >= closeBtnY && mouseY <= closeBtnY + 20;
        ConfigUITheme.drawPillButton(g, font, "✕", closeBtnX, closeBtnY, closeBtnW, 20, closeHover,
                closeHover ? 0xFFFF6666 : 0xFF94A3B8, closeHover ? 0x44EF4444 : 0x22EF4444, closeHover ? 0xFFEF4444 : 0x44EF4444);

        // TOP SECTION: Interactive Player Inventory Grid
        int topSectionY = winY + headerH + 20;
        int slotCell = 22;
        int gridW = 9 * slotCell + 12;
        int gridH = 4 * slotCell + 18;
        int gridX = winX + (winW - gridW) / 2;

        g.fill(gridX, topSectionY, gridX + gridW, topSectionY + gridH, 0xAA0F172A);
        g.outline(gridX, topSectionY, gridW, gridH, 0x44475569);

        String tip = (editingRule != null && editFilterField >= 0)
                ? "§e★ Auto-Fill Mode: Click an item below to copy its ID/Name/UUID into filter!"
                : "§7Click 2 slots to link/unlink | §eRight-click hotbar slot to change color§7 | §cSlot 9 locked on SkyBlock";
        g.text(font, tip, gridX + 4, winY + headerH + 6, (editingRule != null && editFilterField >= 0) ? 0xFFF59E0B : 0xFF94A3B8, false);

        Minecraft mc = Minecraft.getInstance();
        List<InventorySlotSwapManager.SwapRule> rules = viewAllProfiles ? InventorySlotSwapManager.getAllRules() : InventorySlotSwapManager.getRulesForActiveProfile();
        if (pickingProfileRule != null && !rules.contains(pickingProfileRule)) {
            rules = new ArrayList<>(rules);
            rules.add(pickingProfileRule);
        }

        // 3 rows of main inventory (slots 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = 9 + row * 9 + col;
                int sx = gridX + 6 + col * slotCell;
                int sy = topSectionY + 6 + row * slotCell;
                renderGridSlot(g, mc, slotIdx, sx, sy, slotCell, rules, mouseX, mouseY);
            }
        }

        // Hotbar divider line
        int hbY = topSectionY + 6 + 3 * slotCell + 4;
        g.fill(gridX + 6, hbY - 2, gridX + gridW - 6, hbY - 1, 0x4464748B);

        // 1 row of hotbar (slots 0..8)
        for (int col = 0; col < 9; col++) {
            int slotIdx = col;
            int sx = gridX + 6 + col * slotCell;
            renderGridSlot(g, mc, slotIdx, sx, hbY, slotCell, rules, mouseX, mouseY);
        }

        // Selected slot pulse outline
        if (selectedGridSlot != -1) {
            int selX, selY;
            if (selectedGridSlot >= 9 && selectedGridSlot < 36) {
                int r = (selectedGridSlot - 9) / 9;
                int c = (selectedGridSlot - 9) % 9;
                selX = gridX + 6 + c * slotCell;
                selY = topSectionY + 6 + r * slotCell;
            } else {
                selX = gridX + 6 + selectedGridSlot * slotCell;
                selY = hbY;
            }
            int pulse = (int) (System.currentTimeMillis() / 200 % 2 == 0 ? 0xEEF59E0B : 0x88F59E0B);
            g.outline(selX - 1, selY - 1, slotCell + 2, slotCell + 2, pulse);
        }

        // BOTTOM SECTION: List of Rules & Filters
        int bottomSectionY = topSectionY + gridH + 16;
        int bottomSectionH = (winY + winH - 34) - bottomSectionY;

        g.fill(winX + 16, bottomSectionY - 2, winX + winW - 16, bottomSectionY - 1, divCol);
        String rulesTitle = viewAllProfiles
                ? "§b§lAll Slot Swap Rules across Profiles (" + rules.size() + "):"
                : "§b§lActive Slot Swap Rules & Exceptions (" + rules.size() + "):";
        g.text(font, rulesTitle, winX + 20, bottomSectionY + 2, 0xFFFFFFFF, false);

        // Render Rules List with Scissors
        int listY = bottomSectionY + 16;
        int listH = bottomSectionH - 20;
        int listW = winW - 40;
        g.enableScissor(winX + 20, listY, winX + 20 + listW, listY + listH);

        int curCardY = listY + 2 - (int) scrollY;
        int cardH = 52;
        int totalContentH = rules.size() * (cardH + 6);

        for (int i = 0; i < rules.size(); i++) {
            InventorySlotSwapManager.SwapRule r = rules.get(i);
            int cardX = winX + 20;

            if (curCardY + cardH >= listY && curCardY <= listY + listH) {
                boolean cardHover = (mouseX >= cardX && mouseX <= cardX + listW && mouseY >= curCardY && mouseY <= curCardY + cardH);
                int bg = r.enabled ? (cardHover ? 0xDD1E293B : 0xAA0F172A) : 0x550F172A;
                int border = r.enabled ? (cardHover ? 0xFF38BDF8 : 0x44475569) : 0x33475569;

                g.fill(cardX, curCardY, cardX + listW, curCardY + cardH, bg);
                g.outline(cardX, curCardY, listW, cardH, border);

                // Slot Swap Pair Badge
                int hbCol = InventorySlotSwapManager.getSlotColor(r.hotbarSlot);
                g.fill(cardX + 8, curCardY + 8, cardX + 140, curCardY + 26, 0x33000000);
                g.outline(cardX + 8, curCardY + 8, 132, 18, hbCol);
                g.text(font, "§eHotbar " + (r.hotbarSlot + 1) + " §b⇄ §eInv " + (r.inventorySlot - 8), cardX + 14, curCardY + 13, 0xFFFFFFFF, false);

                // Enabled switch
                boolean enHover = mouseX >= cardX + 150 && mouseX <= cardX + 225 && mouseY >= curCardY + 8 && mouseY <= curCardY + 26;
                ConfigUITheme.drawPillButton(g, font, r.enabled ? "✔ Enabled" : "✖ Disabled", cardX + 150, curCardY + 8, 75, 18, enHover,
                        r.enabled ? 0xFF22C55E : 0xFFEF4444, r.enabled ? 0x3322C55E : 0x33EF4444, r.enabled ? 0xFF22C55E : 0xFFEF4444);

                // Profile Filter Pill
                boolean pFilterHover = mouseX >= cardX + 235 && mouseX <= cardX + 340 && mouseY >= curCardY + 8 && mouseY <= curCardY + 26;
                String profText = (r.profile.equalsIgnoreCase("General") || r.profile.equalsIgnoreCase("ALL") || r.profile.equalsIgnoreCase("General (all)"))
                        ? "General (all)" : r.profile;
                ConfigUITheme.drawPillButton(g, font, "Prof: " + profText, cardX + 235, curCardY + 8, 105, 18, pFilterHover,
                        0xFFCBD5E1, 0x22FFFFFF, 0x44FFFFFF);

                // Item Filter Toggle
                boolean iFilterHover = mouseX >= cardX + 345 && mouseX <= cardX + 450 && mouseY >= curCardY + 8 && mouseY <= curCardY + 26;
                ConfigUITheme.drawPillButton(g, font, r.filterEnabled ? "Filter: ON" : "Filter: OFF", cardX + 345, curCardY + 8, 105, 18, iFilterHover,
                        r.filterEnabled ? 0xFF38BDF8 : 0xFF64748B, r.filterEnabled ? 0x3338BDF8 : 0x221E293B, r.filterEnabled ? 0xFF38BDF8 : 0x44475569);

                // Item Filter Details Row
                if (r.filterEnabled) {
                    boolean isEditingThis = (editingRule == r);
                    int f1X = cardX + 8;
                    int f2X = cardX + 210;
                    int f3X = cardX + 420;
                    int fY = curCardY + 30;

                    // Skyblock ID
                    drawFilterBox(g, font, f1X, fY, 190, 16, "SB ID: ", r.filterSkyblockId, isEditingThis && editFilterField == 0);
                    // Display Name
                    drawFilterBox(g, font, f2X, fY, 200, 16, "Name: ", r.filterDisplayName, isEditingThis && editFilterField == 1);
                    // UUID
                    drawFilterBox(g, font, f3X, fY, 220, 16, "UUID: ", r.filterUuid, isEditingThis && editFilterField == 2);
                } else {
                    g.text(font, "§8No item restrictions. Any item in these slots will swap.", cardX + 12, curCardY + 34, 0xFF64748B, false);
                }

                // Delete Button
                int delX = cardX + listW - 32;
                int delY = curCardY + 8;
                boolean delHover = (mouseX >= delX && mouseX <= delX + 24 && mouseY >= delY && mouseY <= delY + 24);
                ConfigUITheme.drawPillButton(g, font, "🗑", delX, delY, 24, 24, delHover,
                        0xFFFF6666, delHover ? 0x44EF4444 : 0x22EF4444, 0xFFEF4444);
            }
            curCardY += (cardH + 6);
        }
        g.disableScissor();
        this.maxScrollY = Math.max(0, totalContentH - listH);

        // Bottom status note
        g.text(font, "§8Tip: Hold hotkey ('" + (s != null ? s.inventorySlotSwapKey : "X") + "') in any container to click-link or click-unlink slots directly!", winX + 24, winY + winH - 18, 0xFF64748B, false);

        // Draw floating Profile Picker Popup
        if (pickingProfileRule != null) {
            List<String> profiles = getAvailableProfiles();
            int pX = pickingProfileX;
            int pY = pickingProfileY;
            int pW = 160;
            int rH = 20;
            int pH = profiles.size() * rH + 4;
            if (pY + pH > winY + winH - 10) {
                pY = Math.max(winY + 10, winY + winH - 10 - pH);
            }

            g.fill(pX - 1, pY - 1, pX + pW + 1, pY + pH + 1, 0xFF38BDF8);
            g.fill(pX, pY, pX + pW, pY + pH, 0xF50F172A);

            for (int pIdx = 0; pIdx < profiles.size(); pIdx++) {
                String prof = profiles.get(pIdx);
                int rowY = pY + 2 + pIdx * rH;
                boolean pHover = (mouseX >= pX && mouseX <= pX + pW && mouseY >= rowY && mouseY <= rowY + rH);
                boolean isCurrent = (prof.equals("General (all)") && (pickingProfileRule.profile.equalsIgnoreCase("ALL") || pickingProfileRule.profile.equalsIgnoreCase("General") || pickingProfileRule.profile.equalsIgnoreCase("General (all)")))
                        || prof.equalsIgnoreCase(pickingProfileRule.profile);

                if (pHover || isCurrent) {
                    g.fill(pX + 1, rowY, pX + pW - 1, rowY + rH, isCurrent ? 0xDD0284C7 : 0xAA1E293B);
                }

                String label = prof.equals("General (all)") ? "§aGeneral (all)" : ("§f" + prof);
                g.text(font, label, pX + 8, rowY + 6, isCurrent ? 0xFFFFFFFF : 0xFFCBD5E1, false);
            }
        }
    }

    private void drawFilterBox(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String prefix, String text, boolean focused) {
        int bg = focused ? 0xEE1E293B : 0xAA0F172A;
        int border = focused ? 0xFFF59E0B : 0x33475569;
        g.fill(x, y, x + w, y + h, bg);
        g.outline(x, y, w, h, border);

        String fullText = text != null ? text : "";
        int prefixW = font.width(prefix);
        g.text(font, prefix, x + 4, y + 4, 0xFF94A3B8, false);

        if (focused && selectAllOnType && !fullText.isEmpty()) {
            int selW = font.width(fullText);
            g.fill(x + 4 + prefixW, y + 2, x + 4 + prefixW + selW, y + h - 2, 0x880284C7);
        }

        int textX = x + 4 + prefixW;
        int availW = w - prefixW - 8;
        int textW = font.width(fullText);
        int renderOffset = 0;
        if (textW > availW) {
            renderOffset = textW - availW;
        }

        g.enableScissor(textX, y, x + w - 2, y + h);
        g.text(font, fullText, textX - renderOffset, y + 4, focused ? 0xFFFFFFFF : 0xFFCBD5E1, false);

        if (focused && (System.currentTimeMillis() / 500 % 2 == 0)) {
            cursorPos = Math.max(0, Math.min(fullText.length(), cursorPos));
            int cx = textX - renderOffset + font.width(fullText.substring(0, cursorPos));
            g.fill(cx, y + 3, cx + 1, y + h - 3, 0xFFF59E0B);
        }
        g.disableScissor();
    }

    private void renderGridSlot(GuiGraphicsExtractor g, Minecraft mc, int slotIdx, int sx, int sy, int size, List<InventorySlotSwapManager.SwapRule> rules, int mouseX, int mouseY) {
        boolean hover = mouseX >= sx && mouseX <= sx + size && mouseY >= sy && mouseY <= sy + size;
        boolean isSkyblockSlot9 = (slotIdx == 8 && SkyblockUtils.isInSkyblock());

        if (isSkyblockSlot9) {
            g.fill(sx, sy, sx + size, sy + size, 0x55EF4444);
            g.outline(sx, sy, size, size, 0xAAEF4444);
        } else {
            g.fill(sx, sy, sx + size, sy + size, hover ? 0x8838BDF8 : 0x440F172A);
            g.outline(sx, sy, size, size, hover ? 0xFFBAE6FD : 0x44475569);
        }

        // Render bound indicator if slot is part of any rule
        for (InventorySlotSwapManager.SwapRule r : rules) {
            if (!r.enabled) continue;
            if (r.hotbarSlot == slotIdx || r.inventorySlot == slotIdx) {
                int col = InventorySlotSwapManager.getSlotColor(r.hotbarSlot);
                g.outline(sx, sy, size, size, col);
                g.fill(sx + 1, sy + 1, sx + 5, sy + 5, col);
                break;
            }
        }

        // Live item from player inventory centered inside slot
        if (mc.player != null) {
            ItemStack stack = mc.player.getInventory().getItem(slotIdx);
            if (stack != null && !stack.isEmpty()) {
                g.item(stack, sx + (size - 16) / 2, sy + (size - 16) / 2);
            }
        }

        if (isSkyblockSlot9) {
            g.text(this.font, "🔒", sx + size - 10, sy + size - 9, 0xFFFF5555, false);
        }

        // Slot number in top-left corner faint or colored for hotbar
        String num = (slotIdx < 9) ? "H" + (slotIdx + 1) : String.valueOf(slotIdx - 8);
        int numCol = (slotIdx < 9) ? (InventorySlotSwapManager.getSlotColor(slotIdx) | 0xFF000000) : 0x5594A3B8;
        g.text(this.font, (slotIdx < 9 ? "" : "§8") + num, sx + 2, sy + 2, numCol, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseBtn = event.button();

        // Profile Picker Popup Click
        if (pickingProfileRule != null) {
            List<String> profiles = getAvailableProfiles();
            int pX = pickingProfileX;
            int pY = pickingProfileY;
            int pW = 160;
            int rH = 20;
            int pH = profiles.size() * rH + 4;
            if (pY + pH > winY + winH - 10) {
                pY = Math.max(winY + 10, winY + winH - 10 - pH);
            }

            if (mouseX >= pX && mouseX <= pX + pW && mouseY >= pY && mouseY <= pY + pH) {
                int clickedIdx = (int) (mouseY - pY - 2) / rH;
                if (clickedIdx >= 0 && clickedIdx < profiles.size()) {
                    String chosen = profiles.get(clickedIdx);
                    pickingProfileRule.profile = (chosen.equals("General (all)") || chosen.equals("ALL")) ? "General" : chosen;
                    InventorySlotSwapManager.save();
                    pickingProfileRule = null;
                    return true;
                }
            } else {
                pickingProfileRule = null;
            }
        }

        // Header Button Clicks (matching dynamic layout)
        int rightX = winX + winW - 12;
        int closeBtnW = 20;
        int closeBtnX = rightX - closeBtnW;
        rightX -= (closeBtnW + 8);

        int trigBtnW = 165;
        int trigBtnX = rightX - trigBtnW;
        rightX -= (trigBtnW + 6);

        int viewBtnW = 145;
        int viewBtnX = rightX - viewBtnW;
        rightX -= (viewBtnW + 6);

        int colBtnW = 105;
        int colBtnX = rightX - colBtnW;

        // Close button
        int closeBtnY = winY + 10;
        if (mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnW && mouseY >= closeBtnY && mouseY <= closeBtnY + 20) {
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        // Slot Colors Button
        int colBtnY = winY + 9;
        if (mouseX >= colBtnX && mouseX <= colBtnX + colBtnW && mouseY >= colBtnY && mouseY <= colBtnY + 22) {
            Minecraft.getInstance().setScreenAndShow(new InventorySlotColorScreen(this));
            return true;
        }

        // View Filter Setting Pill
        int viewBtnY = winY + 9;
        if (mouseX >= viewBtnX && mouseX <= viewBtnX + viewBtnW && mouseY >= viewBtnY && mouseY <= viewBtnY + 22) {
            viewAllProfiles = !viewAllProfiles;
            return true;
        }

        // Trigger Pill Click (cycles through Shift -> Ctrl -> Alt -> Hotkey)
        int trigBtnY = winY + 9;
        if (mouseX >= trigBtnX && mouseX <= trigBtnX + trigBtnW && mouseY >= trigBtnY && mouseY <= trigBtnY + 22) {
            BomboConfig.Settings s = BomboConfig.get();
            if (s != null) {
                String cur = s.inventorySlotSwapTrigger != null ? s.inventorySlotSwapTrigger : "Shift";
                if ("Shift".equalsIgnoreCase(cur)) s.inventorySlotSwapTrigger = "Ctrl";
                else if ("Ctrl".equalsIgnoreCase(cur)) s.inventorySlotSwapTrigger = "Alt";
                else if ("Alt".equalsIgnoreCase(cur)) s.inventorySlotSwapTrigger = "Hotkey";
                else s.inventorySlotSwapTrigger = "Shift";
                BomboConfig.save();
            }
            return true;
        }

        // Top Grid Slot Clicks
        int topSectionY = winY + headerH + 20;
        int slotCell = 22;
        int gridW = 9 * slotCell + 12;
        int gridX = winX + (winW - gridW) / 2;
        int hbY = topSectionY + 6 + 3 * slotCell + 4;

        // Check 3 rows of inventory
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int slotIdx = 9 + r * 9 + c;
                int sx = gridX + 6 + c * slotCell;
                int sy = topSectionY + 6 + r * slotCell;
                if (mouseX >= sx && mouseX <= sx + slotCell && mouseY >= sy && mouseY <= sy + slotCell) {
                    handleGridSlotClick(slotIdx);
                    return true;
                }
            }
        }

        // Check hotbar
        for (int c = 0; c < 9; c++) {
            int slotIdx = c;
            int sx = gridX + 6 + c * slotCell;
            if (mouseX >= sx && mouseX <= sx + slotCell && mouseY >= hbY && mouseY <= hbY + slotCell) {
                if (mouseBtn == 1) {
                    // Right click cycles slot color
                    InventorySlotSwapManager.cycleSlotColor(slotIdx);
                    return true;
                }
                handleGridSlotClick(slotIdx);
                return true;
            }
        }

        // Bottom Rules List Clicks
        int bottomSectionY = topSectionY + (4 * slotCell + 18) + 16;
        int listY = bottomSectionY + 16;
        int listW = winW - 40;
        List<InventorySlotSwapManager.SwapRule> rules = viewAllProfiles ? InventorySlotSwapManager.getAllRules() : InventorySlotSwapManager.getRulesForActiveProfile();
        if (pickingProfileRule != null && !rules.contains(pickingProfileRule)) {
            rules = new ArrayList<>(rules);
            rules.add(pickingProfileRule);
        }
        int curCardY = listY + 2 - (int) scrollY;
        int cardH = 52;

        for (int i = 0; i < rules.size(); i++) {
            InventorySlotSwapManager.SwapRule rule = rules.get(i);
            int cardX = winX + 20;

            if (mouseY >= curCardY && mouseY <= curCardY + cardH && mouseX >= cardX && mouseX <= cardX + listW) {
                // Enabled toggle
                if (mouseX >= cardX + 150 && mouseX <= cardX + 225 && mouseY >= curCardY + 8 && mouseY <= curCardY + 26) {
                    rule.enabled = !rule.enabled;
                    InventorySlotSwapManager.save();
                    return true;
                }

                // Profile Filter Pill Click (open dropdown popup)
                if (mouseX >= cardX + 235 && mouseX <= cardX + 340 && mouseY >= curCardY + 8 && mouseY <= curCardY + 26) {
                    pickingProfileRule = rule;
                    pickingProfileX = cardX + 235;
                    pickingProfileY = curCardY + 28;
                    return true;
                }

                // Filter Enabled Toggle
                if (mouseX >= cardX + 345 && mouseX <= cardX + 450 && mouseY >= curCardY + 8 && mouseY <= curCardY + 26) {
                    rule.filterEnabled = !rule.filterEnabled;
                    InventorySlotSwapManager.save();
                    return true;
                }

                // Delete Button
                int delX = cardX + listW - 32;
                int delY = curCardY + 8;
                if (mouseX >= delX && mouseX <= delX + 24 && mouseY >= delY && mouseY <= delY + 24) {
                    InventorySlotSwapManager.removeRule(rule);
                    return true;
                }

                // Filter Fields Focus
                if (rule.filterEnabled) {
                    int f1X = cardX + 8;
                    int f2X = cardX + 210;
                    int f3X = cardX + 420;
                    int fY = curCardY + 30;

                    if (mouseX >= f1X && mouseX <= f1X + 190 && mouseY >= fY && mouseY <= fY + 16) {
                        editingRule = rule;
                        editFilterField = 0;
                        cursorPos = rule.filterSkyblockId.length();
                        return true;
                    }
                    if (mouseX >= f2X && mouseX <= f2X + 200 && mouseY >= fY && mouseY <= fY + 16) {
                        editingRule = rule;
                        editFilterField = 1;
                        cursorPos = rule.filterDisplayName.length();
                        return true;
                    }
                    if (mouseX >= f3X && mouseX <= f3X + 220 && mouseY >= fY && mouseY <= fY + 16) {
                        editingRule = rule;
                        editFilterField = 2;
                        cursorPos = rule.filterUuid.length();
                        return true;
                    }
                }
            }
            curCardY += (cardH + 6);
        }

        editingRule = null;
        return super.mouseClicked(event, handled);
    }

    private List<String> getAvailableProfiles() {
        List<String> list = new ArrayList<>();
        list.add("General (all)");
        String active = InventorySlotSwapManager.getActiveProfile();
        if (active != null && !active.isEmpty() && !active.equalsIgnoreCase("General") && !active.equalsIgnoreCase("General (all)") && !active.equalsIgnoreCase("ALL")) {
            list.add(active);
        }
        BomboConfig.Settings s = BomboConfig.get();
        if (s != null && s.profileBinds != null) {
            for (String p : s.profileBinds.keySet()) {
                if (!p.equalsIgnoreCase("General") && !p.equalsIgnoreCase("General (all)") && !p.equalsIgnoreCase("ALL")) {
                    if (!list.contains(p)) {
                        list.add(p);
                    }
                }
            }
        }
        for (InventorySlotSwapManager.SwapRule r : InventorySlotSwapManager.getAllRules()) {
            if (r.profile != null && !r.profile.equalsIgnoreCase("ALL") && !r.profile.equalsIgnoreCase("General") && !r.profile.equalsIgnoreCase("General (all)")) {
                if (!list.contains(r.profile)) {
                    list.add(r.profile);
                }
            }
        }
        return list;
    }

    private int getPrevWordIndex(String text, int pos) {
        if (text == null || pos <= 0) return 0;
        int p = Math.min(pos, text.length()) - 1;
        while (p > 0 && Character.isWhitespace(text.charAt(p))) p--;
        while (p > 0 && !Character.isWhitespace(text.charAt(p - 1)) && text.charAt(p - 1) != ',' && text.charAt(p - 1) != ';') p--;
        return Math.max(0, p);
    }

    private int getNextWordIndex(String text, int pos) {
        if (text == null || pos >= text.length()) return text != null ? text.length() : 0;
        int p = Math.max(0, pos);
        while (p < text.length() && (Character.isWhitespace(text.charAt(p)) || text.charAt(p) == ',' || text.charAt(p) == ';')) p++;
        while (p < text.length() && !Character.isWhitespace(text.charAt(p)) && text.charAt(p) != ',' && text.charAt(p) != ';') p++;
        return Math.min(text.length(), p);
    }

    private void handleGridSlotClick(int slotIdx) {
        if (editingRule != null && editFilterField >= 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                ItemStack stack = mc.player.getInventory().getItem(slotIdx);
                if (stack != null && !stack.isEmpty()) {
                    if (editFilterField == 0) {
                        String sbId = SkyblockUtils.getSkyblockId(stack);
                        if (sbId != null && !sbId.isEmpty()) {
                            if (editingRule.filterSkyblockId == null || editingRule.filterSkyblockId.trim().isEmpty()) {
                                editingRule.filterSkyblockId = sbId;
                            } else if (!editingRule.filterSkyblockId.contains(sbId)) {
                                editingRule.filterSkyblockId += ", " + sbId;
                            }
                            editingRule.filterEnabled = true;
                            cursorPos = editingRule.filterSkyblockId.length();
                            InventorySlotSwapManager.save();
                            return;
                        }
                    } else if (editFilterField == 1) {
                        String plainName = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                        if (plainName != null && !plainName.isEmpty()) {
                            if (editingRule.filterDisplayName == null || editingRule.filterDisplayName.trim().isEmpty()) {
                                editingRule.filterDisplayName = plainName;
                            } else if (!editingRule.filterDisplayName.contains(plainName)) {
                                editingRule.filterDisplayName += ", " + plainName;
                            }
                            editingRule.filterEnabled = true;
                            cursorPos = editingRule.filterDisplayName.length();
                            InventorySlotSwapManager.save();
                            return;
                        }
                    } else if (editFilterField == 2) {
                        String uuid = ItemCustomizeScreen.extractItemUuid(stack);
                        if (uuid != null && !uuid.isEmpty()) {
                            if (editingRule.filterUuid == null || editingRule.filterUuid.trim().isEmpty()) {
                                editingRule.filterUuid = uuid;
                            } else if (!editingRule.filterUuid.contains(uuid)) {
                                editingRule.filterUuid += ", " + uuid;
                            }
                            editingRule.filterEnabled = true;
                            cursorPos = editingRule.filterUuid.length();
                            InventorySlotSwapManager.save();
                            return;
                        }
                    }
                }
            }
            return;
        }

        if (selectedGridSlot == -1) {
            if (slotIdx == 8 && SkyblockUtils.isInSkyblock()) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§c[BomboAddons] Slot 9 (SkyBlock Menu) is locked and cannot be swapped!")
                    );
                }
                return;
            }
            selectedGridSlot = slotIdx;
        } else {
            if (selectedGridSlot == slotIdx) {
                selectedGridSlot = -1;
                return;
            }

            if ((slotIdx == 8 || selectedGridSlot == 8) && SkyblockUtils.isInSkyblock()) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§c[BomboAddons] Slot 9 (SkyBlock Menu) is locked and cannot be swapped!")
                    );
                }
                selectedGridSlot = -1;
                return;
            }

            boolean firstIsHotbar = (selectedGridSlot < 9);
            boolean secondIsHotbar = (slotIdx < 9);

            if (firstIsHotbar == secondIsHotbar) {
                // Change selection
                selectedGridSlot = slotIdx;
                return;
            }

            int hb = firstIsHotbar ? selectedGridSlot : slotIdx;
            int inv = firstIsHotbar ? slotIdx : selectedGridSlot;

            InventorySlotSwapManager.SwapRule existing = InventorySlotSwapManager.findRule(hb, inv);
            if (existing != null) {
                InventorySlotSwapManager.removeRule(existing);
            } else {
                InventorySlotSwapManager.SwapRule newRule = new InventorySlotSwapManager.SwapRule(hb, inv, InventorySlotSwapManager.getActiveProfile());
                InventorySlotSwapManager.addRule(newRule);
            }

            selectedGridSlot = -1;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollY -= verticalAmount * 20.0;
        if (this.scrollY < 0) this.scrollY = 0;
        if (this.scrollY > this.maxScrollY) this.scrollY = this.maxScrollY;
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (c >= 32 && c != 127 && editingRule != null) {
            String current = (editFilterField == 0) ? editingRule.filterSkyblockId : (editFilterField == 1 ? editingRule.filterDisplayName : editingRule.filterUuid);
            if (selectAllOnType) {
                current = "";
                cursorPos = 0;
                selectAllOnType = false;
            }
            if (current.length() < 512) {
                cursorPos = Math.max(0, Math.min(current.length(), cursorPos));
                String updated = current.substring(0, cursorPos) + c + current.substring(cursorPos);
                cursorPos++;
                if (editFilterField == 0) editingRule.filterSkyblockId = updated;
                else if (editFilterField == 1) editingRule.filterDisplayName = updated;
                else editingRule.filterUuid = updated;
                InventorySlotSwapManager.save();
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int code = event.key();
        boolean isCtrl = event.hasControlDown() || (Minecraft.getInstance() != null && Minecraft.getInstance().hasControlDown());

        if (code == GLFW.GLFW_KEY_ESCAPE) {
            InventorySlotSwapManager.save();
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }

        if (editingRule != null) {
            String current = (editFilterField == 0) ? editingRule.filterSkyblockId : (editFilterField == 1 ? editingRule.filterDisplayName : editingRule.filterUuid);

            if (code == GLFW.GLFW_KEY_TAB || code == GLFW.GLFW_KEY_ENTER) {
                editFilterField = (editFilterField + 1) % 3;
                cursorPos = (editFilterField == 0) ? editingRule.filterSkyblockId.length() : (editFilterField == 1 ? editingRule.filterDisplayName.length() : editingRule.filterUuid.length());
                selectAllOnType = false;
                return true;
            }

            // Select All
            if (isCtrl && code == GLFW.GLFW_KEY_A) {
                selectAllOnType = true;
                return true;
            }

            // Paste Clipboard
            if (isCtrl && code == GLFW.GLFW_KEY_V) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null && !clip.isEmpty()) {
                    if (selectAllOnType) {
                        current = "";
                        cursorPos = 0;
                        selectAllOnType = false;
                    }
                    String updated = current.substring(0, cursorPos) + clip + current.substring(cursorPos);
                    cursorPos += clip.length();
                    if (editFilterField == 0) editingRule.filterSkyblockId = updated;
                    else if (editFilterField == 1) editingRule.filterDisplayName = updated;
                    else editingRule.filterUuid = updated;
                    InventorySlotSwapManager.save();
                }
                return true;
            }

            if (code == GLFW.GLFW_KEY_LEFT) {
                selectAllOnType = false;
                if (isCtrl) {
                    cursorPos = getPrevWordIndex(current, cursorPos);
                } else {
                    if (cursorPos > 0) cursorPos--;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_RIGHT) {
                selectAllOnType = false;
                if (isCtrl) {
                    cursorPos = getNextWordIndex(current, cursorPos);
                } else {
                    if (cursorPos < current.length()) cursorPos++;
                }
                return true;
            } else if (code == GLFW.GLFW_KEY_HOME) {
                selectAllOnType = false;
                cursorPos = 0;
                return true;
            } else if (code == GLFW.GLFW_KEY_END) {
                selectAllOnType = false;
                cursorPos = current.length();
                return true;
            } else if (code == GLFW.GLFW_KEY_BACKSPACE) {
                if (selectAllOnType) {
                    if (editFilterField == 0) editingRule.filterSkyblockId = "";
                    else if (editFilterField == 1) editingRule.filterDisplayName = "";
                    else editingRule.filterUuid = "";
                    cursorPos = 0;
                    selectAllOnType = false;
                    InventorySlotSwapManager.save();
                    return true;
                }

                if (isCtrl) {
                    int prev = getPrevWordIndex(current, cursorPos);
                    String updated = current.substring(0, prev) + current.substring(cursorPos);
                    cursorPos = prev;
                    if (editFilterField == 0) editingRule.filterSkyblockId = updated;
                    else if (editFilterField == 1) editingRule.filterDisplayName = updated;
                    else editingRule.filterUuid = updated;
                    InventorySlotSwapManager.save();
                    return true;
                } else {
                    if (cursorPos > 0 && !current.isEmpty()) {
                        String updated = current.substring(0, cursorPos - 1) + current.substring(cursorPos);
                        cursorPos--;
                        if (editFilterField == 0) editingRule.filterSkyblockId = updated;
                        else if (editFilterField == 1) editingRule.filterDisplayName = updated;
                        else editingRule.filterUuid = updated;
                        InventorySlotSwapManager.save();
                    }
                    return true;
                }
            } else if (code == GLFW.GLFW_KEY_DELETE) {
                if (selectAllOnType) {
                    if (editFilterField == 0) editingRule.filterSkyblockId = "";
                    else if (editFilterField == 1) editingRule.filterDisplayName = "";
                    else editingRule.filterUuid = "";
                    cursorPos = 0;
                    selectAllOnType = false;
                    InventorySlotSwapManager.save();
                    return true;
                }

                if (isCtrl) {
                    int next = getNextWordIndex(current, cursorPos);
                    String updated = current.substring(0, cursorPos) + current.substring(next);
                    if (editFilterField == 0) editingRule.filterSkyblockId = updated;
                    else if (editFilterField == 1) editingRule.filterDisplayName = updated;
                    else editingRule.filterUuid = updated;
                    InventorySlotSwapManager.save();
                    return true;
                } else {
                    if (cursorPos < current.length()) {
                        String updated = current.substring(0, cursorPos) + current.substring(cursorPos + 1);
                        if (editFilterField == 0) editingRule.filterSkyblockId = updated;
                        else if (editFilterField == 1) editingRule.filterDisplayName = updated;
                        else editingRule.filterUuid = updated;
                        InventorySlotSwapManager.save();
                    }
                    return true;
                }
            }
        }

        return super.keyPressed(event);
    }
}
