package me.bombo.bomboaddons_final;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public class HudMoveScreen extends Screen {
    private boolean dragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    protected HudMoveScreen() {
        super(Component.literal("Move HUD"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Draw a dark background
        g.fill(0, 0, width, height, 0x88000000);
        
        BomboConfig.Settings s = BomboConfig.get();
        
        // Approximate size for hover detection
        int w = 100;
        int h = 35;
        boolean hovered = mouseX >= s.diceHudX && mouseX <= s.diceHudX + w &&
                          mouseY >= s.diceHudY && mouseY <= s.diceHudY + h;

        if (dragging) {
            s.diceHudX = mouseX - dragOffsetX;
            s.diceHudY = mouseY - dragOffsetY;
        }

        // Draw selection box
        g.fill(s.diceHudX - 4, s.diceHudY - 4, s.diceHudX + w, s.diceHudY + h, 0x44FFFFFF);
        if (hovered || dragging) {
            g.renderOutline(s.diceHudX - 4, s.diceHudY - 4, w + 4, h + 4, 0xFFFFFF00);
        }

        DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, hovered);
        
        g.drawCenteredString(font, "§e§lHUD EDIT MODE", width / 2, 20, 0xFFFFFFFF);
        g.drawCenteredString(font, "§7Drag the Dice Tracker to reposition it", width / 2, 35, 0xFFFFFFFF);
        g.drawCenteredString(font, "§7Hover to see breakdown | §cRight Click to Disable", width / 2, 50, 0xFFFFFFFF);
        g.drawCenteredString(font, "§cPress ESC to save and close", width / 2, height - 30, 0xFFFFFFFF);
        
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        BomboConfig.Settings s = BomboConfig.get();
        int w = 100;
        int h = 35;
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        
        if (mouseX >= s.diceHudX && mouseX <= s.diceHudX + w &&
            mouseY >= s.diceHudY && mouseY <= s.diceHudY + h) {
            if (button == 1) { // Right Click
                s.diceTracker = false;
                BomboConfig.save();
                this.onClose();
                return true;
            }
            dragging = true;
            dragOffsetX = (int) mouseX - s.diceHudX;
            dragOffsetY = (int) mouseY - s.diceHudY;
            return true;
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        BomboConfig.save();
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        BomboConfig.save();
        super.onClose();
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
