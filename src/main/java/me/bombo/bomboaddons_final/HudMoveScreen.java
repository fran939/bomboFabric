package me.bombo.bomboaddons_final;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public class HudMoveScreen extends Screen {
    private HudTarget draggingTarget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    protected HudMoveScreen() {
        super(Component.literal("Move HUD"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
        
        BomboConfig.Settings s = BomboConfig.get();
        
        // 1. Dice Tracker
        renderTarget(g, mouseX, mouseY, s.diceHudX, s.diceHudY, 100, 35, HudTarget.DICE);
        DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, draggingTarget == HudTarget.DICE);

        // 2. Feast Bakery
        renderTarget(g, mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, 140, 95, HudTarget.BAKERY);
        java.util.List<FeastBakeryHud.DetectedItem> dummy = new java.util.ArrayList<>();
        dummy.add(new FeastBakeryHud.DetectedItem("FRESHLY_BAKED_TALISMAN", "Baked Talisman", 25));
        dummy.add(new FeastBakeryHud.DetectedItem("POPCORN_RING", "Popcorn Ring", 125));
        dummy.add(new FeastBakeryHud.DetectedItem("ENCHANTMENT_FEAST_1", "Enchanted Book (Feast I)", 500));
        FeastBakeryHud.drawBakeryInfo(g, s.feastBakeryHudX, s.feastBakeryHudY, dummy);

        g.drawCenteredString(font, "§e§lHUD EDIT MODE", width / 2, 10, 0xFFFFFFFF);
        g.drawCenteredString(font, "§7Drag elements to reposition them", width / 2, 22, 0xFFFFFFFF);
        g.drawCenteredString(font, "§cPress ESC to save and close", width / 2, height - 20, 0xFFFFFFFF);
        
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTarget(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, HudTarget target) {
        BomboConfig.Settings s = BomboConfig.get();
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        if (draggingTarget == target) {
            if (target == HudTarget.DICE) {
                s.diceHudX = mouseX - dragOffsetX;
                s.diceHudY = mouseY - dragOffsetY;
            } else if (target == HudTarget.BAKERY) {
                s.feastBakeryHudX = mouseX - dragOffsetX;
                s.feastBakeryHudY = mouseY - dragOffsetY;
            }
        }

        g.fill(x - 2, y - 2, x + w, y + h, 0x22FFFFFF);
        if (hovered || draggingTarget == target) {
            g.renderOutline(x - 2, y - 2, w + 2, h + 2, 0xFFFFFF00);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        BomboConfig.Settings s = BomboConfig.get();
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Check Bakery first (often larger)
        if (checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, 140, 95)) {
            startDragging(HudTarget.BAKERY, (int) mouseX - s.feastBakeryHudX, (int) mouseY - s.feastBakeryHudY);
            return true;
        }
        // Check Dice
        if (checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, 100, 35)) {
            startDragging(HudTarget.DICE, (int) mouseX - s.diceHudX, (int) mouseY - s.diceHudY);
            return true;
        }

        return super.mouseClicked(event, handled);
    }

    private boolean checkHit(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void startDragging(HudTarget target, int ox, int oy) {
        draggingTarget = target;
        dragOffsetX = ox;
        dragOffsetY = oy;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingTarget = null;
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

    private enum HudTarget {
        DICE, BAKERY
    }
}
