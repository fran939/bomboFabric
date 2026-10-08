package me.bombo.bomboaddons.gui;

import me.bombo.bomboaddons.features.profile.ProfileKeybindManager;
import me.bombo.bomboaddons.gui.config.ConfigUITheme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /b keymaps} - Profile Controls.
 *
 * <p>Re-maps any vanilla Minecraft key binding for the currently active Bombo profile. Swapping
 * profiles restores the vanilla keys for that profile, so e.g. one profile can use {@code R} for
 * sprint while another keeps vanilla {@code Left Shift}.
 */
public class ProfileKeybindsScreen extends Screen {

    private static final int ROW_H = 22;

    private final Screen parent;
    private double scrollAmount = 0.0D;
    private double maxScroll = 0.0D;
    private String search = "";
    private boolean searchFocused = false;
    private KeyMapping captureTarget = null;
    private int hoveredIndex = -1;
    private boolean draggingScrollbar = false;

    public ProfileKeybindsScreen() {
        this(null);
    }

    public ProfileKeybindsScreen(Screen parent) {
        super(Component.literal("BomboAddons Profile Controls"));
        this.parent = parent;
    }

    private List<KeyMapping> filteredMappings() {
        String query = search.trim().toLowerCase(Locale.ROOT);
        List<KeyMapping> out = new ArrayList<>();
        for (KeyMapping mapping : ProfileKeybindManager.allMappings()) {
            if (!query.isEmpty()) {
                String name = ProfileKeybindManager.displayName(mapping).toLowerCase(Locale.ROOT);
                String raw = mapping.getName() == null ? "" : mapping.getName().toLowerCase(Locale.ROOT);
                String key = ProfileKeybindManager.displayKey(mapping).toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !raw.contains(query) && !key.contains(query)) continue;
            }
            out.add(mapping);
        }
        return out;
    }

    @Override
    protected void init() {
        super.init();
        this.scrollAmount = 0.0D;
        this.captureTarget = null;
        this.searchFocused = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xDD0A0C10);

        int winW = Math.min(660, this.width - 40);
        int winH = Math.min(460, this.height - 40);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        g.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, 0xEE11141F);
        g.outline(winX, winY, winW, winH, ConfigUITheme.getAccentColor());
        g.fill(winX, winY, winX + winW, winY + 26, 0xEE1E293B);

        String scope = ProfileKeybindManager.editScope();
        String appliedScope = ProfileKeybindManager.activeScope();
        String applied = scope.equals(appliedScope) ? "" : " §8| §7Applied now: §a" + appliedScope;
        g.text(this.font, "§b§lPROFILE CONTROLS §8| §7Editing: §e" + scope
                + " §8| §7" + ProfileKeybindManager.overrideCount() + " override(s)" + applied, winX + 14, winY + 9, 0xFFFFFFFF, false);

        // Scope selector (profiles + dungeon classes, or Auto to follow the applied scope)
        int scopeBtnW = 150;
        int scopeBtnX = winX + winW - 14 - scopeBtnW;
        boolean scopeHover = this.inRect(mouseX, mouseY, scopeBtnX, winY + 4, scopeBtnW, 18);
        String scopeLabel = "§bScope: §f" + (ProfileKeybindManager.isEditScopePinned() ? scope : scope + " §8(auto)");
        ConfigUITheme.drawPillButton(g, this.font, scopeLabel, scopeBtnX, winY + 4, scopeBtnW, 18, scopeHover, -1, 0x1A00E5FF, 0x5500E5FF);

        // Search field
        int searchY = winY + 32;
        int searchW = winW - 28;
        g.fill(winX + 14, searchY, winX + 14 + searchW, searchY + 18, 0xEE0B0F19);
        g.outline(winX + 14, searchY, searchW, 18, searchFocused ? 0xFF00E5FF : 0x33FFFFFF);
        String searchText = search.isEmpty() && !searchFocused ? "§8Search key bindings..." : "§f" + search;
        g.text(this.font, searchText, winX + 20, searchY + 5, 0xFFFFFFFF, false);
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int caretX = winX + 20 + this.font.width(search);
            g.fill(caretX, searchY + 4, caretX + 1, searchY + 14, 0xFF00E5FF);
        }

        int contentY = searchY + 24;
        int contentH = winH - (contentY - winY) - 36;

        List<KeyMapping> mappings = filteredMappings();
        double totalHeight = mappings.size() * (double) ROW_H;
        this.maxScroll = Math.max(0.0D, totalHeight - contentH);
        if (this.scrollAmount > this.maxScroll) this.scrollAmount = this.maxScroll;

        g.enableScissor(winX + 1, contentY, winX + winW - 1, contentY + contentH);
        this.hoveredIndex = -1;
        int rowY = contentY - (int) this.scrollAmount;
        for (int i = 0; i < mappings.size(); i++) {
            KeyMapping mapping = mappings.get(i);
            if (rowY + ROW_H >= contentY && rowY <= contentY + contentH) {
                this.renderRow(g, mapping, winX, rowY, winW, mouseX, mouseY);
                if (mouseX >= winX + 1 && mouseX <= winX + winW - 1 && mouseY >= rowY && mouseY <= rowY + ROW_H - 1) {
                    this.hoveredIndex = i;
                }
            }
            rowY += ROW_H;
        }
        if (mappings.isEmpty()) {
            g.text(this.font, "§8No key bindings match your search.", winX + 20, contentY + 6, 0xFF94A3B8, false);
        }
        g.disableScissor();

        if (this.maxScroll > 0.0D) {
            int sbX = winX + winW - 8;
            int thumbH = Math.max(18, (int) ((contentH / Math.max(1.0D, totalHeight)) * contentH));
            int thumbY = contentY + (int) ((this.scrollAmount / this.maxScroll) * (contentH - thumbH));
            g.fill(sbX, contentY, sbX + 3, contentY + contentH, 0x33000000);
            g.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, ConfigUITheme.getAccentColor());
        }

        // Footer
        int fy = winY + winH - 26;
        boolean resetHover = this.inRect(mouseX, mouseY, winX + 14, fy, 130, 18);
        ConfigUITheme.drawPillButton(g, this.font, "§cReset Profile", winX + 14, fy, 130, 18, resetHover, -1, 0x33EF4444, 0x66EF4444);

        boolean copyHover = this.inRect(mouseX, mouseY, winX + 150, fy, 130, 18);
        ConfigUITheme.drawPillButton(g, this.font, "§7Copy Overrides", winX + 150, fy, 130, 18, copyHover, -1, 0x1AFFFFFF, 0x44FFFFFF);

        int doneW = 90;
        int doneX = winX + winW - 14 - doneW;
        boolean doneHover = this.inRect(mouseX, mouseY, doneX, fy, doneW, 18);
        ConfigUITheme.drawPillButton(g, this.font, "§aDone", doneX, fy, doneW, 18, doneHover, -1, 0x2210B981, 0x6610B981);

        g.text(this.font, "§7Left-click a key to re-bind §8| §7✕ right-click/↺ resets one §8| §7Esc cancels", winX + 14, winY + winH - 40, 0xFF94A3B8, false);

        if (this.captureTarget != null) {
            g.fill(winX, winY + winH - 58, winX + winW, winY + winH - 44, 0xEE78350F);
            g.centeredText(this.font, "§e§lPress any key or mouse button to bind §f" + ProfileKeybindManager.displayName(this.captureTarget)
                    + " §8(§7Esc to cancel, Delete to unbind§8)", winX + winW / 2, winY + winH - 54, 0xFFFFFFFF);
        }

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderRow(GuiGraphicsExtractor g, KeyMapping mapping, int winX, int rowY, int winW, int mouseX, int mouseY) {
        boolean overridden = ProfileKeybindManager.hasOverride(mapping.getName());
        boolean hovered = this.inRect(mouseX, mouseY, winX + 1, rowY, winW - 2, ROW_H - 1);
        int bg = hovered ? 0x33243B55 : (overridden ? 0x1AFFAA00 : 0x00000000);
        if (bg != 0) g.fill(winX + 1, rowY, winX + winW - 1, rowY + ROW_H - 1, bg);

        String name = ProfileKeybindManager.displayName(mapping);
        g.text(this.font, (overridden ? "§e" : "§f") + name, winX + 14, rowY + 7, 0xFFFFFFFF, false);

        int clearW = 16;
        int clearX = winX + winW - 14 - clearW;
        int keyW = 104;
        int keyX = clearX - 4 - keyW;

        String fallback = ProfileKeybindManager.defaultKeyName(mapping);
        String current = ProfileKeybindManager.displayKey(mapping);
        if (overridden) {
            String ref = "§8default: §7" + (fallback == null ? current : fallback);
            g.text(this.font, ref, keyX - 8 - this.font.width(ref), rowY + 7, 0xFF64748B, false);
        }

        boolean capturing = this.captureTarget == mapping;
        String pillText = capturing ? "§e> press key <" : "§f" + (current == null || current.isEmpty() ? "Unbound" : current);
        boolean keyHover = this.inRect(mouseX, mouseY, keyX, rowY + 3, keyW, 16);
        ConfigUITheme.drawPillButton(g, this.font, pillText, keyX, rowY + 3, keyW, 16, keyHover,
                overridden ? 0xFFFFAA00 : 0xFFFFFFFF,
                capturing ? 0x55FFAA00 : (overridden ? 0x33FFAA00 : 0x1AFFFFFF),
                capturing ? 0xFFFFAA00 : (overridden ? 0x88FFAA00 : 0x44FFFFFF));

        // Every row keeps a reset button: an override is cleared back to the previous binding, and a
        // row without one is reset to the game's own default key.
        boolean clearHover = this.inRect(mouseX, mouseY, clearX, rowY + 3, clearW, 16);
        ConfigUITheme.drawPillButton(g, this.font, overridden ? "§c✕" : "§8↺", clearX, rowY + 3, clearW, 16,
                clearHover, overridden ? 0xFFFF5555 : 0xFF64748B,
                overridden ? 0x22EF4444 : 0x0FFFFFFF,
                overridden ? 0x55EF4444 : 0x22FFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        int winW = Math.min(660, this.width - 40);
        int winH = Math.min(460, this.height - 40);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int scopeBtnW = 150;
        int scopeBtnX = winX + winW - 14 - scopeBtnW;
        if (this.inRect(event.x(), event.y(), scopeBtnX, winY + 4, scopeBtnW, 18)) {
            cycleScope();
            return true;
        }

        int searchY = winY + 32;
        if (this.inRect(event.x(), event.y(), winX + 14, searchY, winW - 28, 18)) {
            this.searchFocused = true;
            return true;
        }

        int contentY = searchY + 24;
        int contentH = winH - (contentY - winY) - 36;

        // Capturing a mouse button for the pending mapping.
        if (this.captureTarget != null) {
            if (!this.inRect(event.x(), event.y(), winX, winY, winW, winH)) {
                this.captureTarget = null;
                return true;
            }
            ProfileKeybindManager.setOverride(this.captureTarget.getName(),
                    ProfileKeybindManager.nameFromCode(1000 + Math.max(0, event.button())));
            this.captureTarget = null;
            return true;
        }

        if (this.maxScroll > 0.0D) {
            int sbX = winX + winW - 8;
            if (event.x() >= sbX - 4 && event.x() <= sbX + 8 && event.y() >= contentY && event.y() <= contentY + contentH) {
                double thumbH = Math.max(18.0D, (contentH / Math.max(1.0D, contentH + this.maxScroll)) * contentH);
                double fraction = (event.y() - contentY - thumbH / 2.0D) / Math.max(1.0D, contentH - thumbH);
                this.scrollAmount = Math.max(0.0D, Math.min(this.maxScroll, fraction * this.maxScroll));
                this.draggingScrollbar = true;
                return true;
            }
        }

        // Rows
        if (event.y() >= contentY && event.y() <= contentY + contentH) {
            List<KeyMapping> mappings = filteredMappings();
            int index = (int) ((event.y() - contentY + this.scrollAmount) / ROW_H);
            if (index >= 0 && index < mappings.size()) {
                KeyMapping mapping = mappings.get(index);
                int rowY = contentY - (int) this.scrollAmount + index * ROW_H;
                int clearW = 16;
                int clearX = winX + winW - 14 - clearW;
                int keyW = 104;
                int keyX = clearX - 4 - keyW;

                if (event.button() == 1) {
                    ProfileKeybindManager.clearOverride(mapping.getName());
                    this.captureTarget = null;
                    return true;
                }
                if (this.inRect(event.x(), event.y(), clearX, rowY + 3, clearW, 16)) {
                    if (ProfileKeybindManager.hasOverride(mapping.getName())) {
                        ProfileKeybindManager.clearOverride(mapping.getName());
                    } else {
                        ProfileKeybindManager.resetToDefault(mapping);
                    }
                    this.captureTarget = null;
                    return true;
                }
                if (this.inRect(event.x(), event.y(), keyX, rowY + 3, keyW, 16)) {
                    this.captureTarget = mapping;
                    return true;
                }
            }
        }

        int fy = winY + winH - 26;
        if (this.inRect(event.x(), event.y(), winX + 14, fy, 130, 18)) {
            ProfileKeybindManager.clearAllForActiveProfile();
            this.captureTarget = null;
            return true;
        }
        if (this.inRect(event.x(), event.y(), winX + 150, fy, 130, 18)) {
            this.copyOverridesToClipboard();
            return true;
        }
        int doneW = 90;
        int doneX = winX + winW - 14 - doneW;
        if (this.inRect(event.x(), event.y(), doneX, fy, doneW, 18)) {
            this.onClose();
            return true;
        }

        this.searchFocused = false;
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0.0D) {
            this.scrollAmount = Math.max(0.0D, Math.min(this.maxScroll, this.scrollAmount - verticalAmount * ROW_H * 1.5D));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.draggingScrollbar && this.maxScroll > 0.0D) {
            int winW = Math.min(660, this.width - 40);
            int winH = Math.min(460, this.height - 40);
            int winY = (this.height - winH) / 2;
            int contentY = winY + 32 + 24;
            int contentH = winH - (contentY - winY) - 36;
            double thumbH = Math.max(18.0D, (contentH / Math.max(1.0D, contentH + this.maxScroll)) * contentH);
            double fraction = (event.y() - contentY - thumbH / 2.0D) / Math.max(1.0D, contentH - thumbH);
            this.scrollAmount = Math.max(0.0D, Math.min(this.maxScroll, fraction * this.maxScroll));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchFocused) {
            char c = (char) event.codepoint();
            if (c >= 32 && c != 127 && this.search.length() < 40) {
                this.search += c;
                this.scrollAmount = 0.0D;
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.captureTarget != null) {
            int code = event.key();
            if (code == GLFW.GLFW_KEY_ESCAPE) {
                this.captureTarget = null;
                return true;
            }
            if (code == GLFW.GLFW_KEY_DELETE || code == GLFW.GLFW_KEY_BACKSPACE) {
                ProfileKeybindManager.setOverride(this.captureTarget.getName(), ProfileKeybindManager.NONE);
                this.captureTarget = null;
                return true;
            }
            ProfileKeybindManager.setOverride(this.captureTarget.getName(), ProfileKeybindManager.nameFromCode(code));
            this.captureTarget = null;
            return true;
        }

        if (this.searchFocused && event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (!this.search.isEmpty()) {
                this.search = this.search.substring(0, this.search.length() - 1);
                this.scrollAmount = 0.0D;
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (this.searchFocused) {
                this.searchFocused = false;
                return true;
            }
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    /** Cycles Auto -> profile -> General -> each dungeon class -> Auto. */
    private void cycleScope() {
        List<String> options = new ArrayList<>();
        options.add("Auto");
        options.addAll(ProfileKeybindManager.editableScopes());

        String current = ProfileKeybindManager.isEditScopePinned() ? ProfileKeybindManager.editScope() : "Auto";
        int idx = options.indexOf(current);
        if (idx < 0) idx = 0;
        int next = (idx + 1) % options.size();
        String chosen = options.get(next);
        if ("Auto".equals(chosen)) {
            ProfileKeybindManager.clearEditScope();
        } else {
            ProfileKeybindManager.setEditScope(chosen);
        }
        this.scrollAmount = 0.0D;
    }

    /** Copies the active profile's overrides as a shareable list. */
    private void copyOverridesToClipboard() {
        StringBuilder sb = new StringBuilder("BomboAddons profile key overrides - " + ProfileKeybindManager.activeProfile() + "\n");
        for (KeyMapping mapping : ProfileKeybindManager.allMappings()) {
            if (!ProfileKeybindManager.hasOverride(mapping.getName())) continue;
            sb.append(ProfileKeybindManager.displayName(mapping))
                    .append(" = ")
                    .append(ProfileKeybindManager.getOverride(mapping.getName()))
                    .append('\n');
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(sb.toString());
        }
        if (mc != null && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aCopied §e"
                    + ProfileKeybindManager.overrideCount() + "§a key override(s) for profile §e"
                    + ProfileKeybindManager.activeProfile() + "§a."));
        }
    }
}
