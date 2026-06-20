package me.bombo.bomboaddons;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
        
        BomboConfig.Settings s = BomboConfig.get();
        
        // 1. Dice Tracker
        renderTarget(g, mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(100 * s.diceHudScale), (int)(35 * s.diceHudScale), HudTarget.DICE);
        DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, draggingTarget == HudTarget.DICE);

        // 2. Feast Bakery
        int bakeryW = (int)(FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
        int bakeryH = (int)(FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale); // 3 dummy items in edit screen
        renderTarget(g, mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH, HudTarget.BAKERY);
        java.util.List<FeastBakeryHud.DetectedItem> dummy = new java.util.ArrayList<>();
        dummy.add(new FeastBakeryHud.DetectedItem("FRESHLY_BAKED_TALISMAN", "Baked Talisman", 25));
        dummy.add(new FeastBakeryHud.DetectedItem("POPCORN_RING", "Popcorn Ring", 125));
        dummy.add(new FeastBakeryHud.DetectedItem("ENCHANTMENT_FEAST_1", "Enchanted Book (Feast I)", 500));
        FeastBakeryHud.drawBakeryInfo(g, s.feastBakeryHudX, s.feastBakeryHudY, dummy);

        // 3. RNG Experiments Profit
        int rngW = (int)(185 * s.rngProfitHudScale);
        int rngH = (int)(ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
        renderTarget(g, mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH, HudTarget.RNG);
        ExperimentationTableHud.onHudRender(g);

        // 4. Kuudra Blindness Timer
        int kuudraW = (int)(80 * s.kuudraBlindnessTimerScale);
        int kuudraH = (int)(12 * s.kuudraBlindnessTimerScale);
        renderTarget(g, mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, kuudraW, kuudraH, HudTarget.KUUDRA);
        KuudraTimer.drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, true);

        // 5. Dungeon Pad Timers
        int padW = (int)(120 * s.padTimersScale);
        int padH = (int)(12 * s.padTimersScale);
        renderTarget(g, mouseX, mouseY, s.padTimersX, s.padTimersY, padW, padH, HudTarget.PAD_TIMERS);
        DungeonPadTimers.drawTimerInfo(g, s.padTimersX, s.padTimersY, true);

        // 6. Custom Timers
        int timerW = (int)(CustomTimerManager.getWidth() * s.customTimerHudScale);
        int timerH = (int)(CustomTimerManager.getHeight() * s.customTimerHudScale);
        renderTarget(g, mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH, HudTarget.TIMERS);
        CustomTimerManager.drawTimers(g, s.customTimerHudX, s.customTimerHudY, true);

        g.centeredText(font, "§e§lHUD EDIT MODE", width / 2, 10, 0xFFFFFFFF);
        g.centeredText(font, "§7Drag elements to reposition them, scroll wheel to resize", width / 2, 22, 0xFFFFFFFF);
        g.centeredText(font, "§cPress ESC to save and close", width / 2, height - 20, 0xFFFFFFFF);
        
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private void renderTarget(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, HudTarget target) {
        BomboConfig.Settings s = BomboConfig.get();
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        if (draggingTarget == target) {
            if (target == HudTarget.DICE) {
                s.diceHudX = mouseX - dragOffsetX;
                s.diceHudY = mouseY - dragOffsetY;
            } else if (target == HudTarget.BAKERY) {
                s.feastBakeryHudX = mouseX - dragOffsetX;
                s.feastBakeryHudY = mouseY - dragOffsetY;
            } else if (target == HudTarget.RNG) {
                s.rngProfitHudX = mouseX - dragOffsetX;
                s.rngProfitHudY = mouseY - dragOffsetY;
            } else if (target == HudTarget.KUUDRA) {
                s.kuudraBlindnessTimerX = mouseX - dragOffsetX;
                s.kuudraBlindnessTimerY = mouseY - dragOffsetY;
            } else if (target == HudTarget.PAD_TIMERS) {
                s.padTimersX = mouseX - dragOffsetX;
                s.padTimersY = mouseY - dragOffsetY;
            } else if (target == HudTarget.TIMERS) {
                s.customTimerHudX = mouseX - dragOffsetX;
                s.customTimerHudY = mouseY - dragOffsetY;
            }
        }

        g.fill(x - 2, y - 2, x + w, y + h, 0x22FFFFFF);
        if (hovered || draggingTarget == target) {
            g.outline(x - 2, y - 2, w + 2, h + 2, 0xFFFFFF00);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        BomboConfig.Settings s = BomboConfig.get();
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // Check RNG
        int rngW = (int)(185 * s.rngProfitHudScale);
        int rngH = (int)(ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
        if (checkHit(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH)) {
            startDragging(HudTarget.RNG, (int) mouseX - s.rngProfitHudX, (int) mouseY - s.rngProfitHudY);
            return true;
        }
        // Check Bakery
        int bakeryW = (int)(FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
        int bakeryH = (int)(FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
        if (checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH)) {
            startDragging(HudTarget.BAKERY, (int) mouseX - s.feastBakeryHudX, (int) mouseY - s.feastBakeryHudY);
            return true;
        }
        // Check Dice
        if (checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(100 * s.diceHudScale), (int)(35 * s.diceHudScale))) {
            startDragging(HudTarget.DICE, (int) mouseX - s.diceHudX, (int) mouseY - s.diceHudY);
            return true;
        }
        // Check Kuudra
        if (checkHit(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, (int)(80 * s.kuudraBlindnessTimerScale), (int)(12 * s.kuudraBlindnessTimerScale))) {
            startDragging(HudTarget.KUUDRA, (int) mouseX - s.kuudraBlindnessTimerX, (int) mouseY - s.kuudraBlindnessTimerY);
            return true;
        }
        // Check Pad Timers
        if (checkHit(mouseX, mouseY, s.padTimersX, s.padTimersY, (int)(120 * s.padTimersScale), (int)(12 * s.padTimersScale))) {
            startDragging(HudTarget.PAD_TIMERS, (int) mouseX - s.padTimersX, (int) mouseY - s.padTimersY);
            return true;
        }
        // Check Custom Timers
        int timerW = (int)(CustomTimerManager.getWidth() * s.customTimerHudScale);
        int timerH = (int)(CustomTimerManager.getHeight() * s.customTimerHudScale);
        if (checkHit(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH)) {
            startDragging(HudTarget.TIMERS, (int) mouseX - s.customTimerHudX, (int) mouseY - s.customTimerHudY);
            return true;
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        BomboConfig.Settings s = BomboConfig.get();
        // dice
        if (checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(100 * s.diceHudScale), (int)(35 * s.diceHudScale))) {
            s.diceHudScale = (float) Math.max(0.5, Math.min(3.0, s.diceHudScale + vertical * 0.1));
            BomboConfig.save();
            return true;
        }
        // bakery
        int bakeryW = (int)(FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
        int bakeryH = (int)(FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
        if (checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH)) {
            s.feastBakeryHudScale = (float) Math.max(0.5, Math.min(3.0, s.feastBakeryHudScale + vertical * 0.1));
            BomboConfig.save();
            return true;
        }
        // rng
        int rngW = (int)(185 * s.rngProfitHudScale);
        int rngH = (int)(ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
        if (checkHit(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH)) {
            s.rngProfitHudScale = (float) Math.max(0.5, Math.min(3.0, s.rngProfitHudScale + vertical * 0.1));
            BomboConfig.save();
            return true;
        }
        // kuudra
        if (checkHit(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, (int)(80 * s.kuudraBlindnessTimerScale), (int)(12 * s.kuudraBlindnessTimerScale))) {
            s.kuudraBlindnessTimerScale = (float) Math.max(0.5, Math.min(3.0, s.kuudraBlindnessTimerScale + vertical * 0.1));
            BomboConfig.save();
            return true;
        }
        // pad timers
        if (checkHit(mouseX, mouseY, s.padTimersX, s.padTimersY, (int)(120 * s.padTimersScale), (int)(12 * s.padTimersScale))) {
            s.padTimersScale = (float) Math.max(0.5, Math.min(3.0, s.padTimersScale + vertical * 0.1));
            BomboConfig.save();
            return true;
        }
        // custom timers
        int timerW = (int)(CustomTimerManager.getWidth() * s.customTimerHudScale);
        int timerH = (int)(CustomTimerManager.getHeight() * s.customTimerHudScale);
        if (checkHit(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH)) {
            s.customTimerHudScale = (float) Math.max(0.5, Math.min(3.0, s.customTimerHudScale + vertical * 0.1));
            BomboConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
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
        DICE, BAKERY, RNG, KUUDRA, PAD_TIMERS, TIMERS
    }
}
