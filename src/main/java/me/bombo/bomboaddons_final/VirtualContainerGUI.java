package me.bombo.bomboaddons_final;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.input.MouseButtonEvent;

public class VirtualContainerGUI extends Screen {
    private static final Identifier CHEST_GUI_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private final List<ItemStack> items;
    private final String guiTitle;
    private final int highlightSlot;
    private final String username;
    private final String currentPath;
    private final int xSize = 176;
    private final int ySize = 222;

    public VirtualContainerGUI(String title, List<ItemStack> items, int highlightSlot, String username, String path) {
        super(Component.literal(title));
        this.items = items;
        this.guiTitle = title;
        this.highlightSlot = highlightSlot;
        this.username = username;
        this.currentPath = path;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;
        
        // Draw the chest background
        graphics.blit(CHEST_GUI_TEXTURE, x, y, xSize, ySize, 0f, 0f, (float) xSize, (float) ySize);
        graphics.drawString(this.font, this.guiTitle, x + 8, y + 6, 4210752, false);
        
        if (System.currentTimeMillis() % 2000 < 50) {
            LF.logDebug("GUI Rendering... Title: " + this.guiTitle + " Items: " + items.size());
        }

        int startX = x + 8;
        int startY = y + 18;
        
        HoveredTooltip hovered = null;

        for (int i = 0; i < 54 && i < items.size(); i++) {
            int row = i / 9;
            int col = i % 9;
            int slotX = startX + col * 18;
            int slotY = startY + row * 18;

            ItemStack stack = items.get(i);
            
            // Highlight target slot
            if (i == highlightSlot) {
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFF00);
            }

            if (stack != null && !stack.isEmpty()) {
                graphics.renderItem(stack, slotX, slotY);
                graphics.renderItemDecorations(this.font, stack, slotX, slotY);

                if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80FFFFFF);
                    
                    List<Component> tooltipLines = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
                    List<String> lines = new ArrayList<>();
                    for (Component line : tooltipLines) {
                        lines.add(line.getString());
                    }
                    if (!lines.isEmpty()) {
                        hovered = new HoveredTooltip(lines.get(0), mouseX, mouseY, lines.subList(1, lines.size()).toArray(new String[0]));
                    }
                }
            }
        }

        // --- NAVIGATION BUTTONS ---
        int buttonY = y - 20;
        int buttonX = x;

        // 1. First Page
        drawButton(graphics, Items.BEACON.getDefaultInstance(), buttonX, buttonY, "§eFirst Page", "§7Jump to the first storage slot.", mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY, buttonX, buttonY)) hovered = getButtonTooltip("§eFirst Page", "§7Jump to the first storage slot.", mouseX, mouseY);

        // 2. Previous Page
        buttonX += 20;
        drawButton(graphics, Items.ARROW.getDefaultInstance(), buttonX, buttonY, "§ePrevious", "§7View the previous slot.", mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY, buttonX, buttonY)) hovered = getButtonTooltip("§ePrevious", "§7View the previous slot.", mouseX, mouseY);

        // 3. Next Page
        buttonX += 20;
        drawButton(graphics, Items.ARROW.getDefaultInstance(), buttonX, buttonY, "§eNext", "§7View the next slot.", mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY, buttonX, buttonY)) hovered = getButtonTooltip("§eNext", "§7View the next slot.", mouseX, mouseY);

        // 4. Last Page
        buttonX += 20;
        drawButton(graphics, Items.NETHER_STAR.getDefaultInstance(), buttonX, buttonY, "§eLast Page", "§7Jump to the last storage slot.", mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY, buttonX, buttonY)) hovered = getButtonTooltip("§eLast Page", "§7Jump to the last storage slot.", mouseX, mouseY);

        // 5. Back to Storage
        buttonX += 30;
        drawButton(graphics, Items.CHEST.getDefaultInstance(), buttonX, buttonY, "§6Back to Storage", "§7View all backpacks and ender chests.", mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY, buttonX, buttonY)) hovered = getButtonTooltip("§6Back to Storage", "§7View all backpacks and ender chests.", mouseX, mouseY);

        // 6. Close
        buttonX = x + xSize - 18;
        drawButton(graphics, Items.BARRIER.getDefaultInstance(), buttonX, buttonY, "§cClose", "§7Exit the virtual container.", mouseX, mouseY);
        if (isMouseOver(mouseX, mouseY, buttonX, buttonY)) hovered = getButtonTooltip("§cClose", "§7Exit the virtual container.", mouseX, mouseY);

        if (hovered != null) {
            drawCustomTooltip(graphics, hovered, mouseX, mouseY);
        }
    }

    private void drawButton(GuiGraphics graphics, ItemStack icon, int x, int y, String title, String desc, int mx, int my) {
        graphics.fill(x - 2, y - 2, x + 18, y + 18, 0x80000000);
        if (isMouseOver(mx, my, x, y)) {
            graphics.fill(x - 2, y - 2, x + 18, y + 18, 0x80FFFFFF);
        }
        graphics.renderItem(icon, x, y);
    }

    private boolean isMouseOver(int mx, int my, int x, int y) {
        return mx >= x && mx < x + 16 && my >= y && my < y + 16;
    }

    private HoveredTooltip getButtonTooltip(String title, String desc, int mx, int my) {
        return new HoveredTooltip(title, mx, my, desc);
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - xSize) / 2;
        int y = (this.height - ySize) / 2;
        int buttonY = y - 20;

        // Navigation Buttons using standard Minecraft widgets (Transparent text, icons drawn in render)
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), (btn) -> navigateAbsolute(0))
            .bounds(x, buttonY, 18, 18).build());
        
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), (btn) -> navigateRelative(-1))
            .bounds(x + 20, buttonY, 18, 18).build());
            
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), (btn) -> navigateRelative(1))
            .bounds(x + 40, buttonY, 18, 18).build());
            
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), (btn) -> navigateAbsolute(17))
            .bounds(x + 60, buttonY, 18, 18).build());
            
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), (btn) -> backToStorage())
            .bounds(x + 85, buttonY, 18, 18).build());
            
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.empty(), (btn) -> this.onClose())
            .bounds(x + xSize - 20, buttonY, 18, 18).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        System.out.println("[Bombo] MOUSE CLICK: " + event.x() + "," + event.y() + " button=" + event.button());
        return super.mouseClicked(event, handled);
    }

    private void navigateRelative(int direction) {
            String[] parts = this.currentPath.split(" > ");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                int current = 0;
                boolean hasIndex = false;
                try {
                    current = Integer.parseInt(lastPart);
                    hasIndex = true;
                } catch (NumberFormatException e) {}

                int next = Math.max(0, current + direction);
                StringBuilder nextPath = new StringBuilder();
                int limit = hasIndex ? parts.length - 1 : parts.length;
                
                for (int i = 0; i < limit; i++) {
                    if (i > 0) nextPath.append(" > ");
                    nextPath.append(parts[i]);
                }
                nextPath.append(" > ").append(next);
                LF.openVirtualContainer(this.username, nextPath.toString(), -1);
            }
    }

    private void navigateAbsolute(int index) {
            String[] parts = this.currentPath.split(" > ");
            if (parts.length > 0) {
                String lastPart = parts[parts.length - 1];
                boolean hasIndex = false;
                try {
                    Integer.parseInt(lastPart);
                    hasIndex = true;
                } catch (NumberFormatException e) {}

                StringBuilder nextPath = new StringBuilder();
                int limit = hasIndex ? parts.length - 1 : parts.length;
                
                for (int i = 0; i < limit; i++) {
                    if (i > 0) nextPath.append(" > ");
                    nextPath.append(parts[i]);
                }
                nextPath.append(" > ").append(index);
                LF.openVirtualContainer(this.username, nextPath.toString(), -1);
            }
    }

    private void backToStorage() {
        try {
            String path = this.currentPath;
            String[] parts = path.split(" > ");
            if (parts.length >= 2) {
                StringBuilder nextPath = new StringBuilder();
                int targetParts = parts.length;
                if (path.contains("backpack_contents") || path.contains("ender_chest_contents")) {
                    targetParts = parts.length - 2;
                } else {
                    targetParts = parts.length - 1;
                }
                
                for (int i = 0; i < Math.max(1, targetParts); i++) {
                    if (i > 0) nextPath.append(" > ");
                    nextPath.append(parts[i]);
                }
                LF.openVirtualContainer(this.username, nextPath.toString(), -1);
            }
        } catch (Exception e) {}
    }

    private void drawCustomTooltip(GuiGraphics graphics, HoveredTooltip tooltip, int mouseX, int mouseY) {
        int maxWidth = this.font.width(tooltip.title);
        for (String line : tooltip.lore) {
            maxWidth = Math.max(maxWidth, this.font.width(line));
        }
        
        int tX = mouseX + 12;
        int tY = mouseY - 12;
        int width = maxWidth + 8;
        int height = (tooltip.lore.length + 1) * 10 + 4;
        
        if (tX + width > this.width) tX = mouseX - width - 8;
        if (tY + height > this.height) tY = this.height - height - 8;

        graphics.fill(tX - 2, tY - 2, tX + width + 2, tY + height + 2, 0xCF000000);
        graphics.drawString(this.font, tooltip.title, tX, tY, 0xFFFFFFFF, true);
        for (int i = 0; i < tooltip.lore.length; i++) {
            graphics.drawString(this.font, tooltip.lore[i], tX, tY + (i + 1) * 10, 0xFFFFFFFF, true);
        }
    }

    private static class HoveredTooltip {
        final String title;
        final int x, y;
        final String[] lore;

        HoveredTooltip(String title, int x, int y, String... lore) {
            this.title = title;
            this.x = x;
            this.y = y;
            this.lore = lore;
        }
    }
}
