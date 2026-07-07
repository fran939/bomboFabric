package me.bombo.bomboaddons;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public class HudMoveScreen extends Screen {
    private HudTarget draggingTarget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private boolean isResizingItemList = false;

    protected HudMoveScreen() {
        super(Component.literal("Move HUD"));
    }

    @Override
    protected void init() {
        super.init();
        if (BomboConfig.get().itemListEnabled) {
            me.bombo.bomboaddons.ItemListOverlay.updateLayout(0, width, 0, width, height);
            me.bombo.bomboaddons.ItemListOverlay.searchBox = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x88000000);
        
        BomboConfig.Settings s = BomboConfig.get();
        
        // 1. Dice Tracker
        renderTarget(g, mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(260 * s.diceHudScale), (int)(52 * s.diceHudScale), HudTarget.DICE);
        DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, draggingTarget == HudTarget.DICE);
        g.text(font, "§aRight-click to swap modes!", s.diceHudX, s.diceHudY - 10, 0xFFFFFFFF, true);

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
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (BomboConfig.get().itemListEnabled) {
            int ilX = s.itemListX == -1 ? width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            renderTarget(g, mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH, HudTarget.ITEM_LIST);
            me.bombo.bomboaddons.ItemListOverlay.render(g, net.minecraft.client.Minecraft.getInstance().font, mouseX, mouseY);
            
            if (s.itemListSeparateSearch) {
                int searchX = s.itemListSearchX == -1 ? width / 2 - 75 : s.itemListSearchX;
                int searchY = s.itemListSearchY == -1 ? height / 2 + 20 : s.itemListSearchY;
                renderTarget(g, mouseX, mouseY, searchX, searchY, s.itemListSearchW, 16, HudTarget.ITEM_LIST_SEARCH);
                g.fill(searchX, searchY, searchX + s.itemListSearchW, searchY + 16, 0xAA000000);
                g.outline(searchX, searchY, searchX + s.itemListSearchW, searchY + 16, 0xFFAAAAAA);
                g.text(net.minecraft.client.Minecraft.getInstance().font, "Search...", searchX + 4, searchY + 4, 0xFFAAAAAA, false);
            }
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isResizingItemList) {
            BomboConfig.Settings s = BomboConfig.get();
            int ilX = s.itemListX == -1 ? width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            s.itemListW = (int) Math.max(120, event.x() - ilX + 5);
            s.itemListH = (int) Math.max(100, event.y() - ilY + 42); // Adjust offset so handle stays under mouse
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
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
            } else if (target == HudTarget.ITEM_LIST) {
                s.itemListX = mouseX - dragOffsetX;
                s.itemListY = mouseY - dragOffsetY;
            } else if (target == HudTarget.ITEM_LIST_SEARCH) {
                s.itemListSearchX = mouseX - dragOffsetX;
                s.itemListSearchY = mouseY - dragOffsetY;
            }
        }

        g.fill(x - 2, y - 2, x + w, y + h, 0x22FFFFFF);
        if (hovered || draggingTarget == target) {
            g.outline(x - 2, y - 2, w + 2, h + 2, 0xFFFFFF00);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (BomboConfig.get().itemListEnabled && me.bombo.bomboaddons.ItemListOverlay.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
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
        if (checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(260 * s.diceHudScale), (int)(52 * s.diceHudScale))) {
            if (button == 1 || button == 2) {
                s.diceDisplayMode = "Current".equalsIgnoreCase(s.diceDisplayMode) ? "Lifetime" : "Current";
                BomboConfig.save();
                return true;
            }
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

        // Check Separate Search
        if (s.itemListEnabled && s.itemListSeparateSearch) {
            int searchX = s.itemListSearchX == -1 ? width / 2 - 75 : s.itemListSearchX;
            int searchY = s.itemListSearchY == -1 ? height / 2 + 20 : s.itemListSearchY;
            if (checkHit(mouseX, mouseY, searchX, searchY, s.itemListSearchW, 16)) {
                startDragging(HudTarget.ITEM_LIST_SEARCH, (int) mouseX - searchX, (int) mouseY - searchY);
                return true;
            }
        }

        // Check Item List Resize Handle
        if (s.itemListEnabled && !s.itemListLocked) {
            int ilX = s.itemListX == -1 ? width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            System.out.println("DEBUG: HudMoveScreen click check! mouse=(" + mouseX + ", " + mouseY + ") ilX=" + ilX + " ilY=" + ilY + " W=" + s.itemListW + " H=" + s.itemListH);
            if (mouseX >= ilX && mouseX <= ilX + 5 && mouseY >= ilY + s.itemListH - 52 && mouseY <= ilY + s.itemListH - 32) {
                isResizingItemList = true;
                dragOffsetX = (int) mouseX;
                dragOffsetY = (int) mouseY;
                return true;
            }
            if (checkHit(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH)) {
                System.out.println("DEBUG: Hit ITEM_LIST! startDragging called!");
                startDragging(HudTarget.ITEM_LIST, (int) mouseX - ilX, (int) mouseY - ilY);
                return true;
            }
        }

        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (BomboConfig.get().itemListEnabled && me.bombo.bomboaddons.ItemListOverlay.mouseScrolled(mouseX, mouseY, vertical)) {
            return true;
        }
        BomboConfig.Settings s = BomboConfig.get();
        // dice
        if (checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(260 * s.diceHudScale), (int)(52 * s.diceHudScale))) {
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
        if (isResizingItemList) {
            isResizingItemList = false;
            BomboConfig.save();
            return true;
        }

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
        DICE, BAKERY, RNG, KUUDRA, PAD_TIMERS, TIMERS, ITEM_LIST, ITEM_LIST_SEARCH
    }
}
