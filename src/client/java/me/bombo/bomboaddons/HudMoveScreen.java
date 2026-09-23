package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class HudMoveScreen extends Screen {
    public static final float MIN_SCALE = 0.2f;
    public static final float MAX_SCALE = 20.0f;
    public static final int SNAP_DISTANCE = 8;

    private static final java.util.ArrayDeque<Runnable> undoStack = new java.util.ArrayDeque<>();
    private Runnable pendingUndoAction = null;

    private static boolean snappingEnabled = true;
    private HudTarget selectedTarget = null;
    private HudTarget draggingTarget = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private int editingWidgetIdx = -1;

    private boolean isResizing = false;
    private HudTarget resizingTarget = null;
    private double resizeStartScale = 1.0;
    private int resizeStartW = 0;
    private int resizeStartH = 0;
    private int resizeStartX = 0;
    private int resizeStartY = 0;
    private double resizeStartMouseX = 0.0;
    private double resizeStartMouseY = 0.0;

    private HudTarget hoveredTarget = null;
    private int lastMouseX = 0;
    private int lastMouseY = 0;

    // Visual snapping guide lines
    private Integer snapGuideX = null;
    private Integer snapGuideY = null;

    public HudMoveScreen() {
        this((HudTarget) null);
    }

    public HudMoveScreen(HudTarget initialTarget) {
        super(Component.literal("Move HUD"));
        this.selectedTarget = initialTarget;
    }

    public HudMoveScreen(String targetName) {
        super(Component.literal("Move HUD"));
        if (targetName != null) {
            try {
                this.selectedTarget = HudTarget.valueOf(targetName.toUpperCase());
            } catch (Exception ignored) {
                this.selectedTarget = null;
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        if (BomboConfig.get().itemListEnabled) {
            ItemListOverlay.updateLayout(0, this.width, 0, this.width, this.height);
            ItemListOverlay.searchBox = null;
        }

        BomboConfig.Settings s = BomboConfig.get();
        // The chest value panel ships "auto" (docked right of the container). Give it a concrete
        // spot while the move screen is open so it can actually be grabbed and dragged.
        if (s.croesusProfitHudX < 0 || s.croesusProfitHudY < 0) {
            s.croesusProfitHudX = this.width / 2 + 120;
            s.croesusProfitHudY = Math.max(4, this.height / 2 - 100);
        }
        String toggleModeText = s.showOnlyActiveHuds ? "§eMode: Active HUDs" : "§aMode: All HUDs";
        this.addRenderableWidget(Button.builder(Component.literal(toggleModeText), btn -> {
            s.showOnlyActiveHuds = !s.showOnlyActiveHuds;
            BomboConfig.save();
            this.init();
        }).bounds(10, 10, 130, 20).build());

        String snapText = snappingEnabled ? "§aSnapping: ON" : "§cSnapping: OFF";
        this.addRenderableWidget(Button.builder(Component.literal(snapText), btn -> {
            snappingEnabled = !snappingEnabled;
            btn.setMessage(Component.literal(snappingEnabled ? "§aSnapping: ON" : "§cSnapping: OFF"));
        }).bounds(145, 10, 110, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§cSave & Close"), btn -> {
            this.onClose();
        }).bounds(this.width - 110, 10, 100, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        this.hoveredTarget = null;
        this.snapGuideX = null;
        this.snapGuideY = null;

        // Dark background overlay
        g.fill(0, 0, this.width, this.height, 0x90000000);

        // Render dummy container background in the center for reference
        Identifier invTex = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
        int dummyInvW = 176;
        int dummyInvH = 166;
        int invX = (this.width - dummyInvW) / 2;
        int invY = (this.height - dummyInvH) / 2;
        g.blit(invTex, invX, invY, invX + dummyInvW, invY + dummyInvH, 0.0F, 0.6875F, 0.0F, 0.6484375F);

        BomboConfig.Settings s = BomboConfig.get();

        // 1. DICE
        if (!s.showOnlyActiveHuds || (s.diceTracker && DiceTracker.shouldShowHud())) {
            int w = (int) (260.0F * s.diceHudScale);
            int h = (int) (52.0F * s.diceHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.DICE, (nx, ny) -> {
                s.diceHudX = nx;
                s.diceHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.diceHudX, s.diceHudY, w, h, HudTarget.DICE, s.diceHudScale);
            DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, this.draggingTarget == HudTarget.DICE);
            if (this.isTargetActive(HudTarget.DICE, mouseX, mouseY, s.diceHudX, s.diceHudY, w, h)) {
                g.text(this.font, "§aRight-click to swap modes", s.diceHudX, s.diceHudY - 12, -1, true);
            }
        }

        // 2. BAKERY
        if (!s.showOnlyActiveHuds || s.feastBakeryHud) {
            int w = (int) ((float) FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
            int h = (int) ((float) FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.BAKERY, (nx, ny) -> {
                s.feastBakeryHudX = nx;
                s.feastBakeryHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, w, h, HudTarget.BAKERY, s.feastBakeryHudScale);
            List<FeastBakeryHud.DetectedItem> dummy = new ArrayList<>();
            dummy.add(new FeastBakeryHud.DetectedItem("FRESHLY_BAKED_TALISMAN", "Baked Talisman", 25));
            dummy.add(new FeastBakeryHud.DetectedItem("POPCORN_RING", "Popcorn Ring", 125));
            dummy.add(new FeastBakeryHud.DetectedItem("ENCHANTMENT_FEAST_1", "Enchanted Book (Feast I)", 500));
            FeastBakeryHud.drawBakeryInfo(g, s.feastBakeryHudX, s.feastBakeryHudY, dummy);
        }

        // 3. RNG
        if (!s.showOnlyActiveHuds || s.rngProfitHud) {
            int w = (int) (185.0F * s.rngProfitHudScale);
            int h = (int) ((float) ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.RNG, (nx, ny) -> {
                s.rngProfitHudX = nx;
                s.rngProfitHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, w, h, HudTarget.RNG, s.rngProfitHudScale);
            ExperimentationTableHud.onHudRender(g);
        }

        // 4. KUUDRA
        if (!s.showOnlyActiveHuds || (s.kuudraBlindnessTimer && KuudraTimer.isActive())) {
            int w = (int) (80.0F * s.kuudraBlindnessTimerScale);
            int h = (int) (12.0F * s.kuudraBlindnessTimerScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.KUUDRA, (nx, ny) -> {
                s.kuudraBlindnessTimerX = nx;
                s.kuudraBlindnessTimerY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, w, h, HudTarget.KUUDRA, s.kuudraBlindnessTimerScale);
            KuudraTimer.drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, true);
        }

        // 5. PAD_TIMERS
        if (!s.showOnlyActiveHuds || ((s.padTimersPurple || s.padTimersGreen) && DungeonPadTimers.isActive())) {
            int w = (int) (120.0F * s.padTimersScale);
            int h = (int) (12.0F * s.padTimersScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.PAD_TIMERS, (nx, ny) -> {
                s.padTimersX = nx;
                s.padTimersY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.padTimersX, s.padTimersY, w, h, HudTarget.PAD_TIMERS, s.padTimersScale);
            DungeonPadTimers.drawTimerInfo(g, s.padTimersX, s.padTimersY, true);
        }

        // 6. TIMERS
        if (!s.showOnlyActiveHuds || (s.customTimeEnabled && !CustomTimerManager.activeTimers.isEmpty())) {
            int w = (int) ((float) CustomTimerManager.getWidth() * s.customTimerHudScale);
            int h = (int) ((float) CustomTimerManager.getHeight() * s.customTimerHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.TIMERS, (nx, ny) -> {
                s.customTimerHudX = nx;
                s.customTimerHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, w, h, HudTarget.TIMERS, s.customTimerHudScale);
            CustomTimerManager.drawTimers(g, s.customTimerHudX, s.customTimerHudY, true);
        }

        // 7. COMPOSTER
        boolean compDataExists = s.composterLastOrganic >= 0.0 || s.composterLastFuel >= 0.0;
        if (!s.showOnlyActiveHuds || (s.composterHud && compDataExists)) {
            int w = (int) ((float) ComposterHud.getWidth() * s.composterHudScale);
            int h = (int) ((float) ComposterHud.getHeight() * s.composterHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.COMPOSTER, (nx, ny) -> {
                s.composterHudX = nx;
                s.composterHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.composterHudX, s.composterHudY, w, h, HudTarget.COMPOSTER, s.composterHudScale);
            ComposterHud.drawComposterInfo(g, s.composterHudX, s.composterHudY, !compDataExists);
        }

        // 8. COMPOSTER_TIMER
        if (!s.showOnlyActiveHuds || (s.composterTimerHud && compDataExists)) {
            int w = (int) (140.0F * s.composterTimerHudScale);
            int h = (int) (12.0F * s.composterTimerHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.COMPOSTER_TIMER, (nx, ny) -> {
                s.composterTimerHudX = nx;
                s.composterTimerHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, w, h, HudTarget.COMPOSTER_TIMER, s.composterTimerHudScale);
            ComposterHud.drawComposterTimerInfo(g, s.composterTimerHudX, s.composterTimerHudY, !compDataExists);
        }

        // 9. HOPPITY
        if (!s.showOnlyActiveHuds || (s.hoppityHud && (AlphaTrackerHud.isHoppityActive() || !s.hoppityHideWhenInactive))) {
            int w = HoppityHud.getHudWidth();
            int h = HoppityHud.getHudHeight();
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.HOPPITY, (nx, ny) -> {
                s.hoppityHudX = nx;
                s.hoppityHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.hoppityHudX, s.hoppityHudY, w, h, HudTarget.HOPPITY, 1.0f);
            HoppityHud.drawHud(g, s.hoppityHudX, s.hoppityHudY, true);
        }

        // 10. ALPHA_TRACKER
        if (!s.showOnlyActiveHuds || s.alphaTrackerHud) {
            int w = (int) ((float) AlphaTrackerHud.getHudWidth() * s.alphaTrackerHudScale);
            int h = (int) ((float) AlphaTrackerHud.getHudHeight() * s.alphaTrackerHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.ALPHA_TRACKER, (nx, ny) -> {
                s.alphaTrackerHudX = nx;
                s.alphaTrackerHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, w, h, HudTarget.ALPHA_TRACKER, s.alphaTrackerHudScale);
            AlphaTrackerHud.drawAlphaInfo(g, this.font, s.alphaTrackerHudX, s.alphaTrackerHudY);
        }

        // 11. SIGN_CALCULATOR & SIGN GUI
        if (!s.showOnlyActiveHuds || s.signCalculator) {
            float scale = s.signCalculatorScale > 0.0F ? s.signCalculatorScale : 1.0F;
            int signW = (int) (96 * scale);
            int signH = (int) (48 * scale);
            int calcW = (int) (120.0F * scale);
            int calcH = (int) (24.0F * scale);
            int totalW = Math.max(signW, calcW);
            int totalH = signH + calcH + 6;

            int curX = s.signCalculatorX >= 0 ? s.signCalculatorX : (this.width / 2 - totalW / 2);
            int curY = s.signCalculatorY >= 0 ? s.signCalculatorY : 40;

            this.updateDragPosition(mouseX, mouseY, totalW, totalH, HudTarget.SIGN_CALCULATOR, (nx, ny) -> {
                s.signCalculatorX = nx;
                s.signCalculatorY = ny;
            });
            int renderX = s.signCalculatorX >= 0 ? s.signCalculatorX : curX;
            int renderY = s.signCalculatorY >= 0 ? s.signCalculatorY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, totalW, totalH, HudTarget.SIGN_CALCULATOR, scale);

            int signX = renderX + (totalW - signW) / 2;
            int signY = renderY;

            // Wooden Sign Board Planks
            g.fill(signX, signY, signX + signW, signY + signH, 0xFFC89658);
            g.outline(signX, signY, signW, signH, 0xFF7A5424);
            g.fill(signX + 1, signY + Math.max(1, signH / 4), signX + signW - 1, signY + Math.max(1, signH / 4) + 1, 0xFF9E7036);
            g.fill(signX + 1, signY + Math.max(2, signH / 2), signX + signW - 1, signY + Math.max(2, signH / 2) + 1, 0xFF9E7036);
            g.fill(signX + 1, signY + Math.max(3, (signH * 3) / 4), signX + signW - 1, signY + Math.max(3, (signH * 3) / 4) + 1, 0xFF9E7036);

            // Lines on sign
            g.text(this.font, "2", signX + signW / 2 - this.font.width("2") / 2, signY + 2, 0xFF000000, false);
            g.text(this.font, "2", signX + signW / 2 - this.font.width("2") / 2, signY + Math.max(3, signH / 4 + 2), 0xFF000000, false);
            g.text(this.font, "1322", signX + signW / 2 - this.font.width("1322") / 2, signY + Math.max(5, signH / 2 + 2), 0xFF000000, false);
            g.text(this.font, "64", signX + signW / 2 - this.font.width("64") / 2, signY + Math.max(7, (signH * 3) / 4 + 2), 0xFF000000, false);

            // Calculator numbers
            int calcY = signY + signH + 3;
            g.text(this.font, "§6Total: §e1,390", renderX, calcY, -1, true);
            g.text(this.font, "§b2", renderX, calcY + 11, -1, true);
        }

        // 12. CHAT_SEARCH
        if (!s.showOnlyActiveHuds || s.chatSearchBar) {
            int w = (int) (160.0F * s.chatSearchScale);
            int h = (int) (14.0F * s.chatSearchScale);
            int curX = s.chatSearchX >= 0 ? s.chatSearchX : 4;
            int curY = s.chatSearchY >= 0 ? s.chatSearchY : (this.height - 28);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.CHAT_SEARCH, (nx, ny) -> {
                s.chatSearchX = nx;
                s.chatSearchY = ny;
            });
            int renderX = s.chatSearchX >= 0 ? s.chatSearchX : curX;
            int renderY = s.chatSearchY >= 0 ? s.chatSearchY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, w, h, HudTarget.CHAT_SEARCH, s.chatSearchScale);
            g.text(this.font, "§eIn-Chat Search Bar", renderX + 4, renderY + 3, -1, true);
        }

        // 13. FROZEN_BLAZE
        if (!s.showOnlyActiveHuds || (s.frozenBlazeWarning && s.fbWarnTimerOnScreen)) {
            float scale = s.fbWarnTimerScale > 0.0F ? s.fbWarnTimerScale : 1.0F;
            int w = (int) (65.0F * scale);
            int h = (int) (12.0F * scale);
            int curX = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
            int curY = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.FROZEN_BLAZE, (nx, ny) -> {
                s.fbWarnTimerX = nx;
                s.fbWarnTimerY = ny;
            });
            int renderX = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : curX;
            int renderY = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, w, h, HudTarget.FROZEN_BLAZE, scale);
            me.bombo.bomboaddons.features.FrozenBlazeAFKTracker.drawTimerInfo(g, renderX, renderY, scale, true);
        }

        // 14. DOJO_SHOOT
        if (!s.showOnlyActiveHuds || (s.dojoUtilities && s.dojoMasteryWool)) {
            float scale = s.dojoShootHudScale > 0.0F ? s.dojoShootHudScale : 1.0F;
            int w = (int) (56.0F * scale);
            int h = (int) (16.0F * scale);
            int curX = s.dojoShootHudX >= 0 ? s.dojoShootHudX : (this.width / 2 - w / 2);
            int curY = s.dojoShootHudY >= 0 ? s.dojoShootHudY : (this.height / 2 + 18);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.DOJO_SHOOT, (nx, ny) -> {
                s.dojoShootHudX = nx;
                s.dojoShootHudY = ny;
            });
            int renderX = s.dojoShootHudX >= 0 ? s.dojoShootHudX : curX;
            int renderY = s.dojoShootHudY >= 0 ? s.dojoShootHudY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, w, h, HudTarget.DOJO_SHOOT, scale);
            me.bombo.bomboaddons.features.DojoUtilities.drawShootHud(g, renderX, renderY, scale, true);
        }

        // 15. BOBBER_TIME
        if (!s.showOnlyActiveHuds || s.showBobberTime) {
            float scale = s.bobberTimeHudScale > 0.0F ? s.bobberTimeHudScale : 1.0F;
            int w = (int) (95.0F * scale);
            int h = (int) (12.0F * scale);
            int curX = s.bobberTimeHudX >= 0 ? s.bobberTimeHudX : (this.width / 2 - w / 2);
            int curY = s.bobberTimeHudY >= 0 ? s.bobberTimeHudY : (this.height / 2 + 15);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.BOBBER_TIME, (nx, ny) -> {
                s.bobberTimeHudX = nx;
                s.bobberTimeHudY = ny;
            });
            int renderX = s.bobberTimeHudX >= 0 ? s.bobberTimeHudX : curX;
            int renderY = s.bobberTimeHudY >= 0 ? s.bobberTimeHudY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, w, h, HudTarget.BOBBER_TIME, scale);
            AutoFishing.drawBobberHud(g, this.minecraft, "§bBobber Time: 5.0s", renderX, renderY, scale);
        }

        // 16. SPEEDOMETER
        if (!s.showOnlyActiveHuds || s.speedometer) {
            float scale = s.speedometerScale > 0.0F ? s.speedometerScale : 1.0F;
            int w = (int) (75.0F * scale);
            int h = (int) (12.0F * scale);
            int curX = s.speedometerX >= 0 ? s.speedometerX : 10;
            int curY = s.speedometerY >= 0 ? s.speedometerY : 120;
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.SPEEDOMETER, (nx, ny) -> {
                s.speedometerX = nx;
                s.speedometerY = ny;
            });
            int renderX = s.speedometerX >= 0 ? s.speedometerX : curX;
            int renderY = s.speedometerY >= 0 ? s.speedometerY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, w, h, HudTarget.SPEEDOMETER, scale);
            me.bombo.bomboaddons.features.SpeedometerHud.drawSpeedometer(g, this.minecraft, renderX, renderY, scale, true);
        }

        // 17. AUTO_CROESUS
        // Chest value panel (contents + profit per chest) - movable like every other HUD.
        {
            int w = (int) (280.0F * s.croesusProfitHudScale);
            int h = (int) (200.0F * s.croesusProfitHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.CROESUS_PROFIT, (nx, ny) -> {
                s.croesusProfitHudX = nx;
                s.croesusProfitHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.croesusProfitHudX, s.croesusProfitHudY, w, h, HudTarget.CROESUS_PROFIT, s.croesusProfitHudScale);
            me.bombo.bomboaddons.features.dungeons.DungeonChestProfitHud.renderDummy(g, s.croesusProfitHudX, s.croesusProfitHudY, s.croesusProfitHudScale);
        }

        if (!s.showOnlyActiveHuds || s.autoCroesusHud) {
            int w = (int) (220.0F * s.autoCroesusHudScale);
            int h = (int) (110.0F * s.autoCroesusHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.AUTO_CROESUS, (nx, ny) -> {
                s.autoCroesusHudX = nx;
                s.autoCroesusHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, w, h, HudTarget.AUTO_CROESUS, s.autoCroesusHudScale);
            AutoCroesusHud.drawCroesusInfo(g, s.autoCroesusHudX, s.autoCroesusHudY, s.autoCroesusHudScale, true);
        }

        // 17b. CROESUS_TRACKER (cumulative profit + per-floor)
        {
            int w = (int) (me.bombo.bomboaddons.features.dungeons.CroesusProfitTrackerHud.BASE_W * s.croesusTrackerHudScale);
            int h = (int) (60.0F * s.croesusTrackerHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.CROESUS_TRACKER, (nx, ny) -> {
                s.croesusTrackerHudX = nx;
                s.croesusTrackerHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.croesusTrackerHudX, s.croesusTrackerHudY, w, h, HudTarget.CROESUS_TRACKER, s.croesusTrackerHudScale);
            me.bombo.bomboaddons.features.dungeons.CroesusProfitTrackerHud.renderDummy(g, s.croesusTrackerHudX, s.croesusTrackerHudY, s.croesusTrackerHudScale);
        }

        // AUTO_REJOIN
        if (!s.showOnlyActiveHuds || s.autoRejoinHud) {
            int w = (int) ((float) me.bombo.bomboaddons.features.AutoRejoinHud.getHudWidth() * s.autoRejoinHudScale);
            int h = (int) ((float) me.bombo.bomboaddons.features.AutoRejoinHud.getHudHeight() * s.autoRejoinHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.AUTO_REJOIN, (nx, ny) -> {
                s.autoRejoinHudX = nx;
                s.autoRejoinHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.autoRejoinHudX, s.autoRejoinHudY, w, h, HudTarget.AUTO_REJOIN, s.autoRejoinHudScale);
            me.bombo.bomboaddons.features.AutoRejoinHud.drawHud(g, s.autoRejoinHudX, s.autoRejoinHudY, s.autoRejoinHudScale, true);
        }

        // ITEM_VALUE_BREAKDOWN
        if (!s.showOnlyActiveHuds || s.itemValueBreakdownHud) {
            int w = (int) ((float) me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudWidth() * s.itemValueBreakdownHudScale);
            int h = (int) ((float) me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudHeight(null) * s.itemValueBreakdownHudScale);
            int curX = s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : 10;
            int curY = s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : (this.height / 2 - 40);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.ITEM_VALUE_BREAKDOWN, (nx, ny) -> {
                s.itemValueBreakdownHudX = nx;
                s.itemValueBreakdownHudY = ny;
            });
            int renderX = s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : curX;
            int renderY = s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, w, h, HudTarget.ITEM_VALUE_BREAKDOWN, s.itemValueBreakdownHudScale);
            me.bombo.bomboaddons.features.ItemValueBreakdownHud.drawBreakdown(g, renderX, renderY, s.itemValueBreakdownHudScale, null, true);
        }

        // 18. TAB_WIDGET
        if (!s.showOnlyActiveHuds || s.tabWidgetHudEnabled) {
            List<String> lines = TabWidgetHud.getMatchedWidgetLines(s.tabWidgetQuery != null && !s.tabWidgetQuery.isEmpty() ? s.tabWidgetQuery : "Bestiary", false);
            if (lines.isEmpty()) {
                lines = TabWidgetHud.getMatchedWidgetLines(s.tabWidgetQuery != null && !s.tabWidgetQuery.isEmpty() ? s.tabWidgetQuery : "Bestiary", true);
            }
            int w = (int) ((float) TabWidgetHud.getWidth() * s.tabWidgetHudScale);
            int h = (int) ((float) TabWidgetHud.getHeight(lines.size()) * s.tabWidgetHudScale);
            this.updateDragPosition(mouseX, mouseY, w, h, HudTarget.TAB_WIDGET, (nx, ny) -> {
                if (this.editingWidgetIdx == -1) {
                    s.tabWidgetHudX = nx;
                    s.tabWidgetHudY = ny;
                }
            });
            this.renderTargetBox(g, mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, w, h, HudTarget.TAB_WIDGET, s.tabWidgetHudScale);
            TabWidgetHud.drawWidgetInfo(g, s.tabWidgetHudX, s.tabWidgetHudY, s.tabWidgetHudScale, lines);
        }

        // Individual Tab Widgets
        if (s.tabWidgets != null) {
            for (int i = 0; i < s.tabWidgets.size(); ++i) {
                BomboConfig.TabWidgetInfo widget = s.tabWidgets.get(i);
                if (widget.enabled || !s.showOnlyActiveHuds) {
                    List<String> lines = TabWidgetHud.getMatchedWidgetLines(widget.name, false);
                    if (lines.isEmpty()) {
                        lines = TabWidgetHud.getMatchedWidgetLines(widget.name, true);
                    }
                    int widgetW = (int) ((float) TabWidgetHud.getWidth() * widget.scale);
                    int widgetH = (int) ((float) TabWidgetHud.getHeight(lines.size()) * widget.scale);
                    if (this.draggingTarget == HudTarget.TAB_WIDGET && this.editingWidgetIdx == i) {
                        int[] clamped = this.clampAndSnap(mouseX - this.dragOffsetX, mouseY - this.dragOffsetY, widgetW, widgetH);
                        widget.x = clamped[0];
                        widget.y = clamped[1];
                    }

                    boolean hovered = this.checkHit(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH);
                    boolean isDragged = (this.draggingTarget == HudTarget.TAB_WIDGET && this.editingWidgetIdx == i);
                    int outlineColor = isDragged ? 0xFF00FFCC : (hovered ? 0xFFFFAA00 : 0x884488FF);
                    int fillColor = isDragged ? 0x4400FFCC : (hovered ? 0x33FFAA00 : 0x22000000);

                    g.fill(widget.x - 2, widget.y - 2, widget.x + widgetW + 2, widget.y + widgetH + 2, fillColor);
                    g.outline(widget.x - 2, widget.y - 2, widgetW + 4, widgetH + 4, outlineColor);
                    g.text(this.font, "§eWidget: " + widget.name + " §7(" + String.format("%.1f", widget.scale) + "x)", widget.x, widget.y - 12, -1, true);

                    TabWidgetHud.drawWidgetInfo(g, widget.x, widget.y, widget.scale, lines);
                }
            }
        }

        // 19. ITEM_LIST & 20. ITEM_LIST_SEARCH
        if (BomboConfig.get().itemListEnabled) {
            int ilX = s.itemListX == -1 ? this.width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            this.updateDragPosition(mouseX, mouseY, s.itemListW, s.itemListH, HudTarget.ITEM_LIST, (nx, ny) -> {
                s.itemListX = nx;
                s.itemListY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH, HudTarget.ITEM_LIST, 1.0f);
            ItemListOverlay.render(g, Minecraft.getInstance().font, mouseX, mouseY);

            if (s.itemListSeparateSearch) {
                int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
                int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
                int searchW = (int) ((float) s.itemListSearchW * s.itemListSearchScale);
                int searchH = (int) (16.0F * s.itemListSearchScale);
                this.updateDragPosition(mouseX, mouseY, searchW, searchH, HudTarget.ITEM_LIST_SEARCH, (nx, ny) -> {
                    s.itemListSearchX = nx;
                    s.itemListSearchY = ny;
                });
                this.renderTargetBox(g, mouseX, mouseY, searchX, searchY, searchW, searchH, HudTarget.ITEM_LIST_SEARCH, s.itemListSearchScale);
                g.pose().pushMatrix();
                g.pose().translate((float) searchX, (float) searchY);
                g.pose().scale(s.itemListSearchScale, s.itemListSearchScale);
                g.fill(0, 0, s.itemListSearchW, 16, -1442840576);
                g.outline(0, 0, s.itemListSearchW, 16, -5592406);
                g.text(Minecraft.getInstance().font, "Search...", 4, 4, -5592406, false);
                g.pose().popMatrix();
            }
        }

        // Draw magnetic alignment guides if active
        if (this.snapGuideX != null) {
            g.fill(this.snapGuideX, 0, this.snapGuideX + 1, this.height, 0x8800FFFF);
        }
        if (this.snapGuideY != null) {
            g.fill(0, this.snapGuideY, this.width, this.snapGuideY + 1, 0x8800FFFF);
        }

        // 21. DUNGEON MAP
        // 21. DUNGEON_MAP
        if (!s.showOnlyActiveHuds || s.dungeonMap) {
            int mapW = me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapWidth();
            int mapH = me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapHeight();
            int curX = s.dungeonMapX >= 0 ? s.dungeonMapX : 10;
            int curY = s.dungeonMapY >= 0 ? s.dungeonMapY : 10;
            this.updateDragPosition(mouseX, mouseY, mapW, mapH, HudTarget.DUNGEON_MAP, (nx, ny) -> {
                s.dungeonMapX = nx;
                s.dungeonMapY = ny;
            });
            int renderX = s.dungeonMapX >= 0 ? s.dungeonMapX : curX;
            int renderY = s.dungeonMapY >= 0 ? s.dungeonMapY : curY;
            this.renderTargetBox(g, mouseX, mouseY, renderX, renderY, mapW, mapH, HudTarget.DUNGEON_MAP, s.dungeonMapScale);
            me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.renderDummyMap(g, renderX, renderY, s.dungeonMapScale);
        }

        // 22. ARMOR_HUD
        if (!s.showOnlyActiveHuds || s.armorHud) {
            int armW = (int) ((float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudWidth() * s.armorHudScale);
            int armH = (int) ((float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudHeight() * s.armorHudScale);
            this.updateDragPosition(mouseX, mouseY, armW, armH, HudTarget.ARMOR_HUD, (nx, ny) -> {
                s.armorHudX = nx;
                s.armorHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.armorHudX, s.armorHudY, armW, armH, HudTarget.ARMOR_HUD, s.armorHudScale);
            me.bombo.bomboaddons.features.hud.ArmorHud.drawHud(g, s.armorHudX, s.armorHudY, s.armorHudScale, true);
            if (this.isTargetActive(HudTarget.ARMOR_HUD, mouseX, mouseY, s.armorHudX, s.armorHudY, armW, armH)) {
                String layout = s.armorHudVertical ? "\u00a7eVertical" : "\u00a7aHorizontal";
                String inv = s.armorHudShowInInventory ? "\u00a7aShow in GUI" : "\u00a7cHide in GUI";
                g.text(this.font, layout + " \u00a77(R-click) | " + inv + " \u00a77(M-click)", s.armorHudX, s.armorHudY + armH + 2, -1, true);
            }
        }

        // 23. EQUIPMENT_HUD
        if (!s.showOnlyActiveHuds || s.equipmentHud) {
            int eqW = (int) ((float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudWidth() * s.equipmentHudScale);
            int eqH = (int) ((float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudHeight() * s.equipmentHudScale);
            this.updateDragPosition(mouseX, mouseY, eqW, eqH, HudTarget.EQUIPMENT_HUD, (nx, ny) -> {
                s.equipmentHudX = nx;
                s.equipmentHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.equipmentHudX, s.equipmentHudY, eqW, eqH, HudTarget.EQUIPMENT_HUD, s.equipmentHudScale);
            me.bombo.bomboaddons.features.hud.EquipmentHud.drawHud(g, s.equipmentHudX, s.equipmentHudY, s.equipmentHudScale, true);
            if (this.isTargetActive(HudTarget.EQUIPMENT_HUD, mouseX, mouseY, s.equipmentHudX, s.equipmentHudY, eqW, eqH)) {
                String layout = s.equipmentHudVertical ? "\u00a7eVertical" : "\u00a7aHorizontal";
                String inv = s.equipmentHudShowInInventory ? "\u00a7aShow in GUI" : "\u00a7cHide in GUI";
                g.text(this.font, layout + " \u00a77(R-click) | " + inv + " \u00a77(M-click)", s.equipmentHudX, s.equipmentHudY + eqH + 2, -1, true);
            }
        }

        // 24. INVENTORY_HUD
        if (!s.showOnlyActiveHuds || s.inventoryHud) {
            int invW = (int) ((float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudWidth() * s.inventoryHudScale);
            int invH = (int) ((float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight() * s.inventoryHudScale);
            this.updateDragPosition(mouseX, mouseY, invW, invH, HudTarget.INVENTORY_HUD, (nx, ny) -> {
                s.inventoryHudX = nx;
                s.inventoryHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.inventoryHudX, s.inventoryHudY, invW, invH, HudTarget.INVENTORY_HUD, s.inventoryHudScale);
            me.bombo.bomboaddons.features.hud.InventoryHud.drawHud(g, s.inventoryHudX, s.inventoryHudY, s.inventoryHudScale, true);
            if (this.isTargetActive(HudTarget.INVENTORY_HUD, mouseX, mouseY, s.inventoryHudX, s.inventoryHudY, invW, invH)) {
                String layout = s.inventoryHudVertical ? "\u00a7eVertical (3x9)" : "\u00a7aHorizontal (9x3)";
                String inv = s.inventoryHudShowInInventory ? "\u00a7aShow in GUI" : "\u00a7cHide in GUI";
                g.text(this.font, layout + " \u00a77(R-click) | " + inv + " \u00a77(M-click)", s.inventoryHudX, s.inventoryHudY + invH + 2, -1, true);
            }
        }

        // 25. CRITTER_HUD
        if (!s.showOnlyActiveHuds || s.critterHud) {
            int critW = (int) ((float) me.bombo.bomboaddons.features.critters.CritterHud.getWidth() * s.critterHudScale);
            int critH = (int) ((float) me.bombo.bomboaddons.features.critters.CritterHud.getHeight() * s.critterHudScale);
            this.updateDragPosition(mouseX, mouseY, critW, critH, HudTarget.CRITTER_HUD, (nx, ny) -> {
                s.critterHudX = nx;
                s.critterHudY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, s.critterHudX, s.critterHudY, critW, critH, HudTarget.CRITTER_HUD, s.critterHudScale);
            me.bombo.bomboaddons.features.critters.CritterHud.render(g, s.critterHudX, s.critterHudY, s.critterHudScale, (int) mouseX, (int) mouseY);
        }

        // 26. CRITTER_MAP
        if (!s.showOnlyActiveHuds || s.critterMapHud) {
            int mapW = (int) (120.0f * s.critterMapScale);
            int mapH = (int) (120.0f * s.critterMapScale);
            int mx = s.critterMapX >= 0 ? s.critterMapX : (this.width - 130);
            int my = s.critterMapY >= 0 ? s.critterMapY : 10;
            this.updateDragPosition(mouseX, mouseY, mapW, mapH, HudTarget.CRITTER_MAP, (nx, ny) -> {
                s.critterMapX = nx;
                s.critterMapY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, mx, my, mapW, mapH, HudTarget.CRITTER_MAP, s.critterMapScale);
            me.bombo.bomboaddons.features.critters.CritterMapHud.render(g, mx, my, s.critterMapScale);
        }

        // 27. CHAT_TABS
        if (!s.showOnlyActiveHuds || s.chatTabs) {
            int chatTabsW = (int) ((float) me.bombo.bomboaddons.features.chat.ChatTabsOverlay.getWidth() * s.chatTabsScale);
            int chatTabsH = (int) (14.0f * s.chatTabsScale);
            int defaultY = this.height - 28;
            int cx = s.chatTabsX >= 0 ? s.chatTabsX : 4;
            int cy = s.chatTabsY >= 0 ? s.chatTabsY : defaultY;
            this.updateDragPosition(mouseX, mouseY, chatTabsW, chatTabsH, HudTarget.CHAT_TABS, (nx, ny) -> {
                s.chatTabsX = nx;
                s.chatTabsY = ny;
            });
            this.renderTargetBox(g, mouseX, mouseY, cx, cy, chatTabsW, chatTabsH, HudTarget.CHAT_TABS, s.chatTabsScale);
            me.bombo.bomboaddons.features.chat.ChatTabsOverlay.render(g, mouseX, mouseY);
        }

        // Snap Guide Lines (Cyan/Teal)
        if (this.snapGuideX != null) {
            g.fill(this.snapGuideX - 1, 0, this.snapGuideX + 1, this.height, 0xDD00E5FF);
        }
        if (this.snapGuideY != null) {
            g.fill(0, this.snapGuideY - 1, this.width, this.snapGuideY + 1, 0xDD00E5FF);
        }

        // Top & bottom info headers
        g.centeredText(this.font, "§6§lHUD EDIT MODE", this.width / 2, 8, -1);
        g.centeredText(this.font, "§7Left-Click + Drag to Move | Scroll Wheel to Scale | §bCtrl+Click to Config | §e[R] Reset Target", this.width / 2, 22, -1);
        g.centeredText(this.font, "§7Snapping: " + (snappingEnabled ? "§aEnabled" : "§cDisabled") + " §8| §cPress ESC to save & exit", this.width / 2, this.height - 18, -1);

        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    private boolean isTargetActive(HudTarget target, int mx, int my, int x, int y, int w, int h) {
        return this.checkHit(mx, my, x, y, w, h) || this.selectedTarget == target || this.draggingTarget == target || this.resizingTarget == target;
    }

    private void updateDragPosition(int mouseX, int mouseY, int w, int h, HudTarget target, java.util.function.BiConsumer<Integer, Integer> positionConsumer) {
        if (this.draggingTarget == target) {
            int rawX = mouseX - this.dragOffsetX;
            int rawY = mouseY - this.dragOffsetY;
            int[] clamped = this.clampAndSnap(rawX, rawY, w, h, target);
            positionConsumer.accept(clamped[0], clamped[1]);
        }
    }

    private static record HudRect(int x, int y, int w, int h, HudTarget target) {}

    private java.util.List<HudRect> getOtherHudRects(HudTarget current) {
        java.util.List<HudRect> list = new java.util.ArrayList<>();
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return list;

        // Container reference dummy
        int dummyInvW = 176, dummyInvH = 166;
        list.add(new HudRect((this.width - dummyInvW) / 2, (this.height - dummyInvH) / 2, dummyInvW, dummyInvH, null));

        if (current != HudTarget.DICE && (!s.showOnlyActiveHuds || s.diceTracker))
            list.add(new HudRect(s.diceHudX, s.diceHudY, (int)(260 * s.diceHudScale), (int)(52 * s.diceHudScale), HudTarget.DICE));
        if (current != HudTarget.BAKERY && (!s.showOnlyActiveHuds || s.feastBakeryHud))
            list.add(new HudRect(s.feastBakeryHudX, s.feastBakeryHudY, (int)(FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale), (int)(FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale), HudTarget.BAKERY));
        if (current != HudTarget.RNG && (!s.showOnlyActiveHuds || s.rngProfitHud))
            list.add(new HudRect(s.rngProfitHudX, s.rngProfitHudY, (int)(185 * s.rngProfitHudScale), (int)(ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale), HudTarget.RNG));
        if (current != HudTarget.ARMOR_HUD && (!s.showOnlyActiveHuds || s.armorHud))
            list.add(new HudRect(s.armorHudX, s.armorHudY, (int)(me.bombo.bomboaddons.features.hud.ArmorHud.getHudWidth() * s.armorHudScale), (int)(me.bombo.bomboaddons.features.hud.ArmorHud.getHudHeight() * s.armorHudScale), HudTarget.ARMOR_HUD));
        if (current != HudTarget.EQUIPMENT_HUD && (!s.showOnlyActiveHuds || s.equipmentHud))
            list.add(new HudRect(s.equipmentHudX, s.equipmentHudY, (int)(me.bombo.bomboaddons.features.hud.EquipmentHud.getHudWidth() * s.equipmentHudScale), (int)(me.bombo.bomboaddons.features.hud.EquipmentHud.getHudHeight() * s.equipmentHudScale), HudTarget.EQUIPMENT_HUD));
        if (current != HudTarget.INVENTORY_HUD && (!s.showOnlyActiveHuds || s.inventoryHud))
            list.add(new HudRect(s.inventoryHudX, s.inventoryHudY, (int)(me.bombo.bomboaddons.features.hud.InventoryHud.getHudWidth() * s.inventoryHudScale), (int)(me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight() * s.inventoryHudScale), HudTarget.INVENTORY_HUD));
        if (current != HudTarget.DUNGEON_MAP && (!s.showOnlyActiveHuds || s.dungeonMap))
            list.add(new HudRect(s.dungeonMapX >= 0 ? s.dungeonMapX : 10, s.dungeonMapY >= 0 ? s.dungeonMapY : 10, me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapWidth(), me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapHeight(), HudTarget.DUNGEON_MAP));
        if (current != HudTarget.AUTO_REJOIN && (!s.showOnlyActiveHuds || s.autoRejoinHud))
            list.add(new HudRect(s.autoRejoinHudX, s.autoRejoinHudY, (int)(me.bombo.bomboaddons.features.AutoRejoinHud.getHudWidth() * s.autoRejoinHudScale), (int)(me.bombo.bomboaddons.features.AutoRejoinHud.getHudHeight() * s.autoRejoinHudScale), HudTarget.AUTO_REJOIN));
        if (current != HudTarget.ITEM_VALUE_BREAKDOWN && (!s.showOnlyActiveHuds || s.itemValueBreakdownHud))
            list.add(new HudRect(s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : 10, s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : (this.height / 2 - 40), (int)(me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudWidth() * s.itemValueBreakdownHudScale), (int)(me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudHeight(null) * s.itemValueBreakdownHudScale), HudTarget.ITEM_VALUE_BREAKDOWN));

        return list;
    }

    private int[] clampAndSnap(int x, int y, int w, int h) {
        return clampAndSnap(x, y, w, h, null);
    }

    private int[] clampAndSnap(int x, int y, int w, int h, HudTarget currentTarget) {
        int clampedX = x;
        int clampedY = y;

        boolean ctrlDown = (Minecraft.getInstance() != null && Minecraft.getInstance().hasControlDown())
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 341)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), 345);

        if (snappingEnabled && !ctrlDown) {
            int snapDist = SNAP_DISTANCE;
            // 1. Screen edges & screen center
            if (Math.abs(clampedX - 4) <= snapDist) {
                clampedX = 4;
                this.snapGuideX = 4;
            } else if (Math.abs((clampedX + w) - (this.width - 4)) <= snapDist) {
                clampedX = this.width - w - 4;
                this.snapGuideX = this.width - 4;
            } else if (Math.abs((clampedX + w / 2) - (this.width / 2)) <= snapDist) {
                clampedX = (this.width - w) / 2;
                this.snapGuideX = this.width / 2;
            }

            if (Math.abs(clampedY - 4) <= snapDist) {
                clampedY = 4;
                this.snapGuideY = 4;
            } else if (Math.abs((clampedY + h) - (this.height - 4)) <= snapDist) {
                clampedY = this.height - h - 4;
                this.snapGuideY = this.height - 4;
            } else if (Math.abs((clampedY + h / 2) - (this.height / 2)) <= snapDist) {
                clampedY = (this.height - h) / 2;
                this.snapGuideY = this.height / 2;
            }

            // 2. Inter-HUD bounding box snapping
            for (HudRect other : getOtherHudRects(currentTarget)) {
                // Snap X (left-align, right-align, adjacent-right, adjacent-left, center-align)
                if (Math.abs(clampedX - other.x) <= snapDist) {
                    clampedX = other.x;
                    this.snapGuideX = other.x;
                } else if (Math.abs((clampedX + w) - (other.x + other.w)) <= snapDist) {
                    clampedX = other.x + other.w - w;
                    this.snapGuideX = other.x + other.w;
                } else if (Math.abs(clampedX - (other.x + other.w + 4)) <= snapDist) {
                    clampedX = other.x + other.w + 4;
                    this.snapGuideX = other.x + other.w + 4;
                } else if (Math.abs((clampedX + w + 4) - other.x) <= snapDist) {
                    clampedX = other.x - w - 4;
                    this.snapGuideX = other.x;
                } else if (Math.abs((clampedX + w / 2) - (other.x + other.w / 2)) <= snapDist) {
                    clampedX = other.x + other.w / 2 - w / 2;
                    this.snapGuideX = other.x + other.w / 2;
                }

                // Snap Y (top-align, bottom-align, adjacent-below, adjacent-above, center-align)
                if (Math.abs(clampedY - other.y) <= snapDist) {
                    clampedY = other.y;
                    this.snapGuideY = other.y;
                } else if (Math.abs((clampedY + h) - (other.y + other.h)) <= snapDist) {
                    clampedY = other.y + other.h - h;
                    this.snapGuideY = other.y + other.h;
                } else if (Math.abs(clampedY - (other.y + other.h + 4)) <= snapDist) {
                    clampedY = other.y + other.h + 4;
                    this.snapGuideY = other.y + other.h + 4;
                } else if (Math.abs((clampedY + h + 4) - other.y) <= snapDist) {
                    clampedY = other.y - h - 4;
                    this.snapGuideY = other.y;
                } else if (Math.abs((clampedY + h / 2) - (other.y + other.h / 2)) <= snapDist) {
                    clampedY = other.y + other.h / 2 - h / 2;
                    this.snapGuideY = other.y + other.h / 2;
                }
            }
        }

        // Clamp inside screen bounds
        int minX = 0;
        int maxX = Math.max(0, this.width - w);
        int minY = 0;
        int maxY = Math.max(0, this.height - h);

        clampedX = Math.max(minX, Math.min(maxX, clampedX));
        clampedY = Math.max(minY, Math.min(maxY, clampedY));

        return new int[]{clampedX, clampedY};
    }

    private void renderTargetBox(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, HudTarget target, float scale) {
        boolean hovered = this.checkHit(mouseX, mouseY, x, y, w, h);
        if (hovered) {
            this.hoveredTarget = target;
        }

        boolean isSelected = (this.selectedTarget == target);
        boolean isDragged = (this.draggingTarget == target);
        boolean isResized = (this.resizingTarget == target);

        // Sleek translucent border without chunky filled corner handles
        if (isDragged || isResized || isSelected || hovered) {
            int outlineColor = (isDragged || isResized) ? 0xFF00E5FF : (isSelected ? 0xFFFFD700 : (hovered ? 0x8800E5FF : 0x33FFFFFF));
            int fillColor = (isDragged || isResized) ? 0x2200E5FF : (isSelected ? 0x1AFFAA00 : 0x0AFFFFFF);

            g.fill(x, y, x + w, y + h, fillColor);
            g.outline(x, y, w, h, outlineColor);

            String displayName = getTargetDisplayName(target);
            String scaleStr = String.format("%.1fx", scale);
            g.text(this.font, "§e" + displayName + " §7(" + scaleStr + ")", x, y - 12, -1, true);
        }
    }

    private static String getTargetDisplayName(HudTarget target) {
        if (target == null) return "HUD Element";
        return switch (target) {
            case DICE -> "Dice Tracker HUD";
            case BAKERY -> "Feast Bakery HUD";
            case RNG -> "RNG Profit HUD";
            case KUUDRA -> "Kuudra Blindness Timer";
            case PAD_TIMERS -> "Pad Timers";
            case TIMERS -> "Custom Timers HUD";
            case COMPOSTER -> "Composter Status HUD";
            case COMPOSTER_TIMER -> "Composter Timer HUD";
            case TAB_WIDGET -> "Tab Widget HUD";
            case ITEM_LIST -> "Item List HUD";
            case ITEM_LIST_SEARCH -> "Item List Search";
            case HOPPITY -> "Hoppity Egg HUD";
            case ALPHA_TRACKER -> "Alpha Tracker HUD";
            case AUTO_CROESUS -> "Auto Croesus HUD";
            case CROESUS_PROFIT -> "Croesus Chest Values";
            case CROESUS_TRACKER -> "Croesus Profit Tracker";
            case SIGN_CALCULATOR -> "Sign Calculator";
            case CHAT_SEARCH -> "In-Chat Search Bar";
            case FROZEN_BLAZE -> "Frozen Blaze AFK Timer";
            case DOJO_SHOOT -> "Dojo Shoot HUD";
            case AUTO_REJOIN -> "Auto Rejoin Skyblock HUD";
            case ITEM_VALUE_BREAKDOWN -> "Item Value Breakdown HUD";
            case ARMOR_HUD -> "Armor HUD";
            case EQUIPMENT_HUD -> "Equipment HUD";
            case INVENTORY_HUD -> "Inventory HUD";
            case CRITTER_HUD -> "Critter Safari HUD";
            case CRITTER_MAP -> "Critter Safari Map";
            case CHAT_TABS -> "Chat Tabs Overlay";
            default -> "HUD Element";
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (handled) return true;
        BomboConfig.Settings s = BomboConfig.get();
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        // 1. SIGN_CALCULATOR
        if (!s.showOnlyActiveHuds || s.signCalculator) {
            float scale = s.signCalculatorScale > 0.0F ? s.signCalculatorScale : 1.0F;
            int signW = (int) (96 * scale);
            int signH = (int) (48 * scale);
            int calcW = (int) (120.0F * scale);
            int calcH = (int) (24.0F * scale);
            int totalW = Math.max(signW, calcW);
            int totalH = signH + calcH + 6;
            int curX = s.signCalculatorX >= 0 ? s.signCalculatorX : (this.width / 2 - totalW / 2);
            int curY = s.signCalculatorY >= 0 ? s.signCalculatorY : 40;
            if (this.startCornerResize(mouseX, mouseY, curX, curY, totalW, totalH, HudTarget.SIGN_CALCULATOR, scale)) return true;
            if (this.checkHit(mouseX, mouseY, curX, curY, totalW, totalH)) {
                this.selectAndDrag(HudTarget.SIGN_CALCULATOR, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // 2. BOBBER_TIME
        if (!s.showOnlyActiveHuds || s.showBobberTime) {
            float scale = s.bobberTimeHudScale > 0.0F ? s.bobberTimeHudScale : 1.0F;
            int w = (int) (95.0F * scale);
            int h = (int) (12.0F * scale);
            int curX = s.bobberTimeHudX >= 0 ? s.bobberTimeHudX : (this.width / 2 - w / 2);
            int curY = s.bobberTimeHudY >= 0 ? s.bobberTimeHudY : (this.height / 2 + 15);
            if (this.startCornerResize(mouseX, mouseY, curX, curY, w, h, HudTarget.BOBBER_TIME, scale)) return true;
            if (this.checkHit(mouseX, mouseY, curX, curY, w, h)) {
                this.selectAndDrag(HudTarget.BOBBER_TIME, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // 3. FROZEN_BLAZE
        if (!s.showOnlyActiveHuds || (s.frozenBlazeWarning && s.fbWarnTimerOnScreen)) {
            float scale = s.fbWarnTimerScale > 0.0F ? s.fbWarnTimerScale : 1.0F;
            int w = (int) (65.0F * scale);
            int h = (int) (12.0F * scale);
            int curX = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
            int curY = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;
            if (this.startCornerResize(mouseX, mouseY, curX, curY, w, h, HudTarget.FROZEN_BLAZE, scale)) return true;
            if (this.checkHit(mouseX, mouseY, curX, curY, w, h)) {
                this.selectAndDrag(HudTarget.FROZEN_BLAZE, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // 4. DOJO_SHOOT
        if (!s.showOnlyActiveHuds || (s.dojoUtilities && s.dojoMasteryWool)) {
            float scale = s.dojoShootHudScale > 0.0F ? s.dojoShootHudScale : 1.0F;
            int w = (int) (56.0F * scale);
            int h = (int) (16.0F * scale);
            int curX = s.dojoShootHudX >= 0 ? s.dojoShootHudX : (this.width / 2 - w / 2);
            int curY = s.dojoShootHudY >= 0 ? s.dojoShootHudY : (this.height / 2 + 18);
            if (this.startCornerResize(mouseX, mouseY, curX, curY, w, h, HudTarget.DOJO_SHOOT, scale)) return true;
            if (this.checkHit(mouseX, mouseY, curX, curY, w, h)) {
                this.selectAndDrag(HudTarget.DOJO_SHOOT, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // 5. CHAT_SEARCH
        if (!s.showOnlyActiveHuds || s.chatSearchBar) {
            int w = (int) (160.0F * s.chatSearchScale);
            int h = (int) (14.0F * s.chatSearchScale);
            int curX = s.chatSearchX >= 0 ? s.chatSearchX : 4;
            int curY = s.chatSearchY >= 0 ? s.chatSearchY : (this.height - 28);
            if (this.checkHit(mouseX, mouseY, curX, curY, w, h)) {
                this.selectAndDrag(HudTarget.CHAT_SEARCH, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // 6. SPEEDOMETER
        if (!s.showOnlyActiveHuds || s.speedometer) {
            float scale = s.speedometerScale > 0.0F ? s.speedometerScale : 1.0F;
            int w = (int) (75.0F * scale);
            int h = (int) (12.0F * scale);
            int curX = s.speedometerX >= 0 ? s.speedometerX : 10;
            int curY = s.speedometerY >= 0 ? s.speedometerY : 120;
            if (this.startCornerResize(mouseX, mouseY, curX, curY, w, h, HudTarget.SPEEDOMETER, scale)) return true;
            if (this.checkHit(mouseX, mouseY, curX, curY, w, h)) {
                this.selectAndDrag(HudTarget.SPEEDOMETER, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // 7. RNG
        int rngW = (int) (185.0F * s.rngProfitHudScale);
        int rngH = (int) ((float) ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH, HudTarget.RNG, s.rngProfitHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH)) {
            this.selectAndDrag(HudTarget.RNG, (int) mouseX - s.rngProfitHudX, (int) mouseY - s.rngProfitHudY);
            return true;
        }

        // 8. BAKERY
        int bakeryW = (int) ((float) FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
        int bakeryH = (int) ((float) FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH, HudTarget.BAKERY, s.feastBakeryHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH)) {
            this.selectAndDrag(HudTarget.BAKERY, (int) mouseX - s.feastBakeryHudX, (int) mouseY - s.feastBakeryHudY);
            return true;
        }

        // 9. DICE
        int diceW = (int) (260.0F * s.diceHudScale);
        int diceH = (int) (52.0F * s.diceHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.diceHudX, s.diceHudY, diceW, diceH, HudTarget.DICE, s.diceHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, diceW, diceH)) {
            if (button == 1 || button == 2) {
                s.diceDisplayMode = "Current".equalsIgnoreCase(s.diceDisplayMode) ? "Lifetime" : "Current";
                BomboConfig.save();
                return true;
            }
            this.selectAndDrag(HudTarget.DICE, (int) mouseX - s.diceHudX, (int) mouseY - s.diceHudY);
            return true;
        }

        // 10. KUUDRA
        int kuudraW = (int) (80.0F * s.kuudraBlindnessTimerScale);
        int kuudraH = (int) (12.0F * s.kuudraBlindnessTimerScale);
        if (this.startCornerResize(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, kuudraW, kuudraH, HudTarget.KUUDRA, s.kuudraBlindnessTimerScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, kuudraW, kuudraH)) {
            this.selectAndDrag(HudTarget.KUUDRA, (int) mouseX - s.kuudraBlindnessTimerX, (int) mouseY - s.kuudraBlindnessTimerY);
            return true;
        }

        // 11. PAD_TIMERS
        int padW = (int) (120.0F * s.padTimersScale);
        int padH = (int) (12.0F * s.padTimersScale);
        if (this.startCornerResize(mouseX, mouseY, s.padTimersX, s.padTimersY, padW, padH, HudTarget.PAD_TIMERS, s.padTimersScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.padTimersX, s.padTimersY, padW, padH)) {
            this.selectAndDrag(HudTarget.PAD_TIMERS, (int) mouseX - s.padTimersX, (int) mouseY - s.padTimersY);
            return true;
        }

        // 12. TIMERS
        int timerW = (int) ((float) CustomTimerManager.getWidth() * s.customTimerHudScale);
        int timerH = (int) ((float) CustomTimerManager.getHeight() * s.customTimerHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH, HudTarget.TIMERS, s.customTimerHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH)) {
            this.selectAndDrag(HudTarget.TIMERS, (int) mouseX - s.customTimerHudX, (int) mouseY - s.customTimerHudY);
            return true;
        }

        // 13. COMPOSTER
        int compW = (int) ((float) ComposterHud.getWidth() * s.composterHudScale);
        int compH = (int) ((float) ComposterHud.getHeight() * s.composterHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH, HudTarget.COMPOSTER, s.composterHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH)) {
            this.selectAndDrag(HudTarget.COMPOSTER, (int) mouseX - s.composterHudX, (int) mouseY - s.composterHudY);
            return true;
        }

        // 14. COMPOSTER_TIMER
        int compTimerW = (int) (140.0F * s.composterTimerHudScale);
        int compTimerH = (int) (12.0F * s.composterTimerHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, compTimerW, compTimerH, HudTarget.COMPOSTER_TIMER, s.composterTimerHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, compTimerW, compTimerH)) {
            this.selectAndDrag(HudTarget.COMPOSTER_TIMER, (int) mouseX - s.composterTimerHudX, (int) mouseY - s.composterTimerHudY);
            return true;
        }

        // 15. HOPPITY
        int hopW = HoppityHud.getHudWidth();
        int hopH = HoppityHud.getHudHeight();
        if (this.checkHit(mouseX, mouseY, s.hoppityHudX, s.hoppityHudY, hopW, hopH)) {
            this.selectAndDrag(HudTarget.HOPPITY, (int) mouseX - s.hoppityHudX, (int) mouseY - s.hoppityHudY);
            return true;
        }

        // 15b. CROESUS_PROFIT (chest value panel)
        int cpW = (int) (280.0F * s.croesusProfitHudScale);
        int cpH = (int) (200.0F * s.croesusProfitHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.croesusProfitHudX, s.croesusProfitHudY, cpW, cpH, HudTarget.CROESUS_PROFIT, s.croesusProfitHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.croesusProfitHudX, s.croesusProfitHudY, cpW, cpH)) {
            this.selectAndDrag(HudTarget.CROESUS_PROFIT, (int) mouseX - s.croesusProfitHudX, (int) mouseY - s.croesusProfitHudY);
            return true;
        }

        // 15c. CROESUS_TRACKER (cumulative profit + per-floor)
        int ctW = (int) (me.bombo.bomboaddons.features.dungeons.CroesusProfitTrackerHud.BASE_W * s.croesusTrackerHudScale);
        int ctH = (int) (60.0F * s.croesusTrackerHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.croesusTrackerHudX, s.croesusTrackerHudY, ctW, ctH, HudTarget.CROESUS_TRACKER, s.croesusTrackerHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.croesusTrackerHudX, s.croesusTrackerHudY, ctW, ctH)) {
            this.selectAndDrag(HudTarget.CROESUS_TRACKER, (int) mouseX - s.croesusTrackerHudX, (int) mouseY - s.croesusTrackerHudY);
            return true;
        }

        // 16. AUTO_CROESUS
        int croesusW = (int) (220.0F * s.autoCroesusHudScale);
        int croesusH = (int) (110.0F * s.autoCroesusHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, croesusW, croesusH, HudTarget.AUTO_CROESUS, s.autoCroesusHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, croesusW, croesusH)) {
            this.selectAndDrag(HudTarget.AUTO_CROESUS, (int) mouseX - s.autoCroesusHudX, (int) mouseY - s.autoCroesusHudY);
            return true;
        }

        // 17. ALPHA_TRACKER
        int alphaW = (int) ((float) AlphaTrackerHud.getHudWidth() * s.alphaTrackerHudScale);
        int alphaH = (int) ((float) AlphaTrackerHud.getHudHeight() * s.alphaTrackerHudScale);
        if (this.startCornerResize(mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, alphaW, alphaH, HudTarget.ALPHA_TRACKER, s.alphaTrackerHudScale)) return true;
        if (this.checkHit(mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, alphaW, alphaH)) {
            this.selectAndDrag(HudTarget.ALPHA_TRACKER, (int) mouseX - s.alphaTrackerHudX, (int) mouseY - s.alphaTrackerHudY);
            return true;
        }

        // 18. TAB_WIDGET
        if (!s.showOnlyActiveHuds || s.tabWidgetHudEnabled) {
            int mainTabW = (int) ((float) TabWidgetHud.getWidth() * s.tabWidgetHudScale);
            List<String> mainLines = TabWidgetHud.getMatchedWidgetLines(s.tabWidgetQuery != null && !s.tabWidgetQuery.isEmpty() ? s.tabWidgetQuery : "Area", true);
            int mainTabH = (int) ((float) TabWidgetHud.getHeight(mainLines.size()) * s.tabWidgetHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, mainTabW, mainTabH, HudTarget.TAB_WIDGET, s.tabWidgetHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, mainTabW, mainTabH)) {
                this.selectAndDrag(HudTarget.TAB_WIDGET, (int) mouseX - s.tabWidgetHudX, (int) mouseY - s.tabWidgetHudY);
                return true;
            }
        }

        // Individual Tab Widgets
        if (s.tabWidgets != null) {
            for (int i = 0; i < s.tabWidgets.size(); ++i) {
                BomboConfig.TabWidgetInfo widget = s.tabWidgets.get(i);
                List<String> lines = TabWidgetHud.getMatchedWidgetLines(widget.name, true);
                int widgetW = (int) ((float) TabWidgetHud.getWidth() * widget.scale);
                int widgetH = (int) ((float) TabWidgetHud.getHeight(lines.size()) * widget.scale);
                if (this.startCornerResize(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH, HudTarget.TAB_WIDGET, widget.scale)) {
                    this.editingWidgetIdx = i;
                    this.selectedTarget = HudTarget.TAB_WIDGET;
                    return true;
                }
                if (this.checkHit(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH)) {
                    this.editingWidgetIdx = i;
                    this.selectAndDrag(HudTarget.TAB_WIDGET, (int) mouseX - widget.x, (int) mouseY - widget.y);
                    return true;
                }
            }
        }

        // 19 & 20. ITEM_LIST & SEARCH
        if (s.itemListEnabled && s.itemListSeparateSearch) {
            int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
            int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
            int searchW = (int) ((float) s.itemListSearchW * s.itemListSearchScale);
            int searchH = (int) (16.0F * s.itemListSearchScale);
            if (this.startCornerResize(mouseX, mouseY, searchX, searchY, searchW, searchH, HudTarget.ITEM_LIST_SEARCH, s.itemListSearchScale)) return true;
            if (this.checkHit(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
                this.selectAndDrag(HudTarget.ITEM_LIST_SEARCH, (int) mouseX - searchX, (int) mouseY - searchY);
                return true;
            }
        }
        if (s.itemListEnabled) {
            int ilX = s.itemListX == -1 ? this.width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            if (this.startCornerResize(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH, HudTarget.ITEM_LIST, 1.0f)) return true;
            if (this.checkHit(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH)) {
                this.selectAndDrag(HudTarget.ITEM_LIST, (int) mouseX - ilX, (int) mouseY - ilY);
                return true;
            }
        }

        // 21. DUNGEON_MAP
        if (!s.showOnlyActiveHuds || s.dungeonMap) {
            int mapW = me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapWidth();
            int mapH = me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapHeight();
            int curX = s.dungeonMapX >= 0 ? s.dungeonMapX : 10;
            int curY = s.dungeonMapY >= 0 ? s.dungeonMapY : 10;
            if (this.startCornerResize(mouseX, mouseY, curX, curY, mapW, mapH, HudTarget.DUNGEON_MAP, s.dungeonMapScale)) return true;
            if (this.checkHit(mouseX, mouseY, curX, curY, mapW, mapH)) {
                this.selectAndDrag(HudTarget.DUNGEON_MAP, (int) mouseX - curX, (int) mouseY - curY);
                return true;
            }
        }

        // AUTO_REJOIN
        if (!s.showOnlyActiveHuds || s.autoRejoinHud) {
            int arW = (int) ((float) me.bombo.bomboaddons.features.AutoRejoinHud.getHudWidth() * s.autoRejoinHudScale);
            int arH = (int) ((float) me.bombo.bomboaddons.features.AutoRejoinHud.getHudHeight() * s.autoRejoinHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.autoRejoinHudX, s.autoRejoinHudY, arW, arH, HudTarget.AUTO_REJOIN, s.autoRejoinHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, s.autoRejoinHudX, s.autoRejoinHudY, arW, arH)) {
                this.selectAndDrag(HudTarget.AUTO_REJOIN, (int) mouseX - s.autoRejoinHudX, (int) mouseY - s.autoRejoinHudY);
                return true;
            }
        }

        // ITEM_VALUE_BREAKDOWN
        if (!s.showOnlyActiveHuds || s.itemValueBreakdownHud) {
            int ivW = (int) ((float) me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudWidth() * s.itemValueBreakdownHudScale);
            int ivH = (int) ((float) me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudHeight(null) * s.itemValueBreakdownHudScale);
            int ivX = s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : 10;
            int ivY = s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : (this.height / 2 - 40);
            if (this.startCornerResize(mouseX, mouseY, ivX, ivY, ivW, ivH, HudTarget.ITEM_VALUE_BREAKDOWN, s.itemValueBreakdownHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, ivX, ivY, ivW, ivH)) {
                this.selectAndDrag(HudTarget.ITEM_VALUE_BREAKDOWN, (int) mouseX - ivX, (int) mouseY - ivY);
                return true;
            }
        }

        // ARMOR_HUD
        if (!s.showOnlyActiveHuds || s.armorHud) {
            int armW = (int) ((float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudWidth() * s.armorHudScale);
            int armH = (int) ((float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudHeight() * s.armorHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.armorHudX, s.armorHudY, armW, armH, HudTarget.ARMOR_HUD, s.armorHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, s.armorHudX, s.armorHudY, armW, armH)) {
                if (button == 1) {
                    // Right-click: toggle vertical/horizontal
                    s.armorHudVertical = !s.armorHudVertical;
                    BomboConfig.save();
                    return true;
                } else if (button == 2) {
                    // Middle-click: toggle show-in-inventory
                    s.armorHudShowInInventory = !s.armorHudShowInInventory;
                    BomboConfig.save();
                    return true;
                }
                this.selectAndDrag(HudTarget.ARMOR_HUD, (int) mouseX - s.armorHudX, (int) mouseY - s.armorHudY);
                return true;
            }
        }

        // EQUIPMENT_HUD
        if (!s.showOnlyActiveHuds || s.equipmentHud) {
            int eqW = (int) ((float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudWidth() * s.equipmentHudScale);
            int eqH = (int) ((float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudHeight() * s.equipmentHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.equipmentHudX, s.equipmentHudY, eqW, eqH, HudTarget.EQUIPMENT_HUD, s.equipmentHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, s.equipmentHudX, s.equipmentHudY, eqW, eqH)) {
                if (button == 1) {
                    // Right-click: toggle vertical/horizontal
                    s.equipmentHudVertical = !s.equipmentHudVertical;
                    BomboConfig.save();
                    return true;
                } else if (button == 2) {
                    // Middle-click: toggle show-in-inventory
                    s.equipmentHudShowInInventory = !s.equipmentHudShowInInventory;
                    BomboConfig.save();
                    return true;
                }
                this.selectAndDrag(HudTarget.EQUIPMENT_HUD, (int) mouseX - s.equipmentHudX, (int) mouseY - s.equipmentHudY);
                return true;
            }
        }

        // INVENTORY_HUD
        if (!s.showOnlyActiveHuds || s.inventoryHud) {
            int invW = (int) ((float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudWidth() * s.inventoryHudScale);
            int invH = (int) ((float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight() * s.inventoryHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.inventoryHudX, s.inventoryHudY, invW, invH, HudTarget.INVENTORY_HUD, s.inventoryHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, s.inventoryHudX, s.inventoryHudY, invW, invH)) {
                if (button == 1) {
                    // Right-click: toggle vertical/horizontal
                    s.inventoryHudVertical = !s.inventoryHudVertical;
                    BomboConfig.save();
                    return true;
                } else if (button == 2) {
                    // Middle-click: toggle show-in-inventory
                    s.inventoryHudShowInInventory = !s.inventoryHudShowInInventory;
                    BomboConfig.save();
                    return true;
                }
                this.selectAndDrag(HudTarget.INVENTORY_HUD, (int) mouseX - s.inventoryHudX, (int) mouseY - s.inventoryHudY);
                return true;
            }
        }

        // CRITTER_HUD
        if (!s.showOnlyActiveHuds || s.critterHud) {
            int critW = (int) ((float) me.bombo.bomboaddons.features.critters.CritterHud.getWidth() * s.critterHudScale);
            int critH = (int) ((float) me.bombo.bomboaddons.features.critters.CritterHud.getHeight() * s.critterHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.critterHudX, s.critterHudY, critW, critH, HudTarget.CRITTER_HUD, s.critterHudScale)) return true;
            if (this.checkHit(mouseX, mouseY, s.critterHudX, s.critterHudY, critW, critH)) {
                this.selectAndDrag(HudTarget.CRITTER_HUD, (int) mouseX - s.critterHudX, (int) mouseY - s.critterHudY);
                return true;
            }
        }

        // CRITTER_MAP
        if (!s.showOnlyActiveHuds || s.critterMapHud) {
            int mapW = (int) (120.0f * s.critterMapScale);
            int mapH = (int) (120.0f * s.critterMapScale);
            int mx = s.critterMapX >= 0 ? s.critterMapX : (this.width - 130);
            int my = s.critterMapY >= 0 ? s.critterMapY : 10;
            if (this.startCornerResize(mouseX, mouseY, mx, my, mapW, mapH, HudTarget.CRITTER_MAP, s.critterMapScale)) return true;
            if (this.checkHit(mouseX, mouseY, mx, my, mapW, mapH)) {
                this.selectAndDrag(HudTarget.CRITTER_MAP, (int) mouseX - mx, (int) mouseY - my);
                return true;
            }
        }

        // CHAT_TABS
        if (!s.showOnlyActiveHuds || s.chatTabs) {
            int chatTabsW = (int) ((float) me.bombo.bomboaddons.features.chat.ChatTabsOverlay.getWidth() * s.chatTabsScale);
            int chatTabsH = (int) (14.0f * s.chatTabsScale);
            int defaultY = this.height - 28;
            int cx = s.chatTabsX >= 0 ? s.chatTabsX : 4;
            int cy = s.chatTabsY >= 0 ? s.chatTabsY : defaultY;
            if (this.startCornerResize(mouseX, mouseY, cx, cy, chatTabsW, chatTabsH, HudTarget.CHAT_TABS, s.chatTabsScale)) return true;
            if (this.checkHit(mouseX, mouseY, cx, cy, chatTabsW, chatTabsH)) {
                this.selectAndDrag(HudTarget.CHAT_TABS, (int) mouseX - cx, (int) mouseY - cy);
                return true;
            }
        }

        return super.mouseClicked(event, handled);
    }

    private Runnable captureCurrentState() {
        BomboConfig.Settings s = BomboConfig.get();
        if (s == null) return () -> {};
        int diceX = s.diceHudX, diceY = s.diceHudY; float diceS = s.diceHudScale;
        int bakeryX = s.feastBakeryHudX, bakeryY = s.feastBakeryHudY; float bakeryS = s.feastBakeryHudScale;
        int rngX = s.rngProfitHudX, rngY = s.rngProfitHudY; float rngS = s.rngProfitHudScale;
        int kuudraX = s.kuudraBlindnessTimerX, kuudraY = s.kuudraBlindnessTimerY; float kuudraS = s.kuudraBlindnessTimerScale;
        int padX = s.padTimersX, padY = s.padTimersY; float padS = s.padTimersScale;
        int timerX = s.customTimerHudX, timerY = s.customTimerHudY; float timerS = s.customTimerHudScale;
        int compX = s.composterHudX, compY = s.composterHudY; float compS = s.composterHudScale;
        int compTX = s.composterTimerHudX, compTY = s.composterTimerHudY; float compTS = s.composterTimerHudScale;
        int tabX = s.tabWidgetHudX, tabY = s.tabWidgetHudY; float tabS = s.tabWidgetHudScale;
        int signX = s.signCalculatorX, signY = s.signCalculatorY; float signS = s.signCalculatorScale;
        int chatX = s.chatSearchX, chatY = s.chatSearchY; float chatS = s.chatSearchScale;
        int fbX = s.fbWarnTimerX, fbY = s.fbWarnTimerY; float fbS = s.fbWarnTimerScale;
        int dojoX = s.dojoShootHudX, dojoY = s.dojoShootHudY; float dojoS = s.dojoShootHudScale;
        int bobberX = s.bobberTimeHudX, bobberY = s.bobberTimeHudY; float bobberS = s.bobberTimeHudScale;
        int speedX = s.speedometerX, speedY = s.speedometerY; float speedS = s.speedometerScale;
        int croesusX = s.autoCroesusHudX, croesusY = s.autoCroesusHudY; float croesusS = s.autoCroesusHudScale; int mapX = s.dungeonMapX, mapY = s.dungeonMapY; float mapS = s.dungeonMapScale;
        int alphaX = s.alphaTrackerHudX, alphaY = s.alphaTrackerHudY; float alphaS = s.alphaTrackerHudScale;
        int armX = s.armorHudX, armY = s.armorHudY; float armS = s.armorHudScale;
        int eqX = s.equipmentHudX, eqY = s.equipmentHudY; float eqS = s.equipmentHudScale;
        int invHX = s.inventoryHudX, invHY = s.inventoryHudY; float invHS = s.inventoryHudScale;

        return () -> {
            s.diceHudX = diceX; s.diceHudY = diceY; s.diceHudScale = diceS;
            s.feastBakeryHudX = bakeryX; s.feastBakeryHudY = bakeryY; s.feastBakeryHudScale = bakeryS;
            s.rngProfitHudX = rngX; s.rngProfitHudY = rngY; s.rngProfitHudScale = rngS;
            s.kuudraBlindnessTimerX = kuudraX; s.kuudraBlindnessTimerY = kuudraY; s.kuudraBlindnessTimerScale = kuudraS;
            s.padTimersX = padX; s.padTimersY = padY; s.padTimersScale = padS;
            s.customTimerHudX = timerX; s.customTimerHudY = timerY; s.customTimerHudScale = timerS;
            s.composterHudX = compX; s.composterHudY = compY; s.composterHudScale = compS;
            s.composterTimerHudX = compTX; s.composterTimerHudY = compTY; s.composterTimerHudScale = compTS;
            s.tabWidgetHudX = tabX; s.tabWidgetHudY = tabY; s.tabWidgetHudScale = tabS;
            s.signCalculatorX = signX; s.signCalculatorY = signY; s.signCalculatorScale = signS;
            s.chatSearchX = chatX; s.chatSearchY = chatY; s.chatSearchScale = chatS;
            s.fbWarnTimerX = fbX; s.fbWarnTimerY = fbY; s.fbWarnTimerScale = fbS;
            s.dojoShootHudX = dojoX; s.dojoShootHudY = dojoY; s.dojoShootHudScale = dojoS;
            s.bobberTimeHudX = bobberX; s.bobberTimeHudY = bobberY; s.bobberTimeHudScale = bobberS;
            s.speedometerX = speedX; s.speedometerY = speedY; s.speedometerScale = speedS;
            s.autoCroesusHudX = croesusX; s.autoCroesusHudY = croesusY; s.autoCroesusHudScale = croesusS; s.dungeonMapX = mapX; s.dungeonMapY = mapY; s.dungeonMapScale = mapS;
            s.armorHudX = armX; s.armorHudY = armY; s.armorHudScale = armS;
            s.equipmentHudX = eqX; s.equipmentHudY = eqY; s.equipmentHudScale = eqS;
            s.inventoryHudX = invHX; s.inventoryHudY = invHY; s.inventoryHudScale = invHS;
            BomboConfig.save();
        };
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.resizingTarget != null) {
            BomboConfig.Settings s = BomboConfig.get();

            double mx = event.x();
            double my = event.y();
            double originX = this.resizeStartX;
            double originY = this.resizeStartY;
            double oldDist = Math.hypot(this.resizeStartMouseX - originX, this.resizeStartMouseY - originY);
            double newDist = Math.hypot(mx - originX, my - originY);
            if (oldDist < 1.0) oldDist = 1.0;

            float newScale = (float) (this.resizeStartScale * (newDist / oldDist));
            newScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));

            if (this.resizingTarget == HudTarget.ITEM_LIST) {
                int dx = (int) (mx - this.resizeStartMouseX);
                int dy = (int) (my - this.resizeStartMouseY);
                s.itemListW = Math.max(120, this.resizeStartW + dx);
                s.itemListH = Math.max(100, this.resizeStartH + dy);
            } else if (this.resizingTarget == HudTarget.ITEM_LIST_SEARCH) {
                s.itemListSearchScale = newScale;
            } else if (this.resizingTarget == HudTarget.DICE) {
                s.diceHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.BAKERY) {
                s.feastBakeryHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.RNG) {
                s.rngProfitHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.KUUDRA) {
                s.kuudraBlindnessTimerScale = newScale;
            } else if (this.resizingTarget == HudTarget.PAD_TIMERS) {
                s.padTimersScale = newScale;
            } else if (this.resizingTarget == HudTarget.TIMERS) {
                s.customTimerHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.COMPOSTER) {
                s.composterHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.COMPOSTER_TIMER) {
                s.composterTimerHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.TAB_WIDGET) {
                if (this.editingWidgetIdx >= 0 && s.tabWidgets != null && this.editingWidgetIdx < s.tabWidgets.size()) {
                    s.tabWidgets.get(this.editingWidgetIdx).scale = newScale;
                } else {
                    s.tabWidgetHudScale = newScale;
                }
            } else if (this.resizingTarget == HudTarget.ALPHA_TRACKER) {
                s.alphaTrackerHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.CROESUS_PROFIT) {
                s.croesusProfitHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.CROESUS_TRACKER) {
                s.croesusTrackerHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.AUTO_CROESUS) {
                s.autoCroesusHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.FROZEN_BLAZE) {
                s.fbWarnTimerScale = newScale;
            } else if (this.resizingTarget == HudTarget.DOJO_SHOOT) {
                s.dojoShootHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.BOBBER_TIME) {
                s.bobberTimeHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.SPEEDOMETER) {
                s.speedometerScale = newScale;
            } else if (this.resizingTarget == HudTarget.SIGN_CALCULATOR) {
                s.signCalculatorScale = newScale;
            } else if (this.resizingTarget == HudTarget.CHAT_SEARCH) {
                s.chatSearchScale = newScale;
            } else if (this.resizingTarget == HudTarget.AUTO_REJOIN) {
                s.autoRejoinHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.ITEM_VALUE_BREAKDOWN) {
                s.itemValueBreakdownHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.ARMOR_HUD) {
                s.armorHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.EQUIPMENT_HUD) {
                s.equipmentHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.INVENTORY_HUD) {
                s.inventoryHudScale = newScale;
            } else if (this.resizingTarget == HudTarget.DUNGEON_MAP) {
                s.dungeonMapScale = newScale;
            }
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        BomboConfig.Settings s = BomboConfig.get();
        float delta = (float) (vertical * 0.1);
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        boolean hasCtrl = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean hasShift = GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        // ITEM_LIST
        if (s.itemListEnabled) {
            int ilX = s.itemListX == -1 ? this.width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            if (this.checkHit(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH)) {
                int step = (int) (18 * Math.signum(vertical));
                if (hasCtrl) {
                    // Ctrl+scroll -> change width only
                    s.itemListW = Math.max(120, Math.min(800, s.itemListW + step));
                } else if (hasShift) {
                    // Shift+scroll -> change height only
                    s.itemListH = Math.max(100, Math.min(1000, s.itemListH + step));
                } else {
                    s.itemListW = Math.max(120, Math.min(800, s.itemListW + step));
                    s.itemListH = Math.max(100, Math.min(1000, s.itemListH + (int) (step * 1.33f)));
                }
                this.selectedTarget = HudTarget.ITEM_LIST;
                BomboConfig.save();
                return true;
            }
        }

        // ITEM_LIST_SEARCH
        if (s.itemListEnabled && s.itemListSeparateSearch) {
            int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
            int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
            int searchW = (int) ((float) s.itemListSearchW * s.itemListSearchScale);
            int searchH = (int) (16.0F * s.itemListSearchScale);
            if (this.checkHit(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
                s.itemListSearchScale = clampScale(s.itemListSearchScale + delta);
                this.selectedTarget = HudTarget.ITEM_LIST_SEARCH;
                BomboConfig.save();
                return true;
            }
        }

        // 1. DICE
        if (this.checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, (int) (260.0F * s.diceHudScale), (int) (52.0F * s.diceHudScale))) {
            s.diceHudScale = clampScale(s.diceHudScale + delta);
            this.selectedTarget = HudTarget.DICE;
            BomboConfig.save();
            return true;
        }

        // 2. BAKERY
        int bakeryW = (int) ((float) FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
        int bakeryH = (int) ((float) FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
        if (this.checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH)) {
            s.feastBakeryHudScale = clampScale(s.feastBakeryHudScale + delta);
            this.selectedTarget = HudTarget.BAKERY;
            BomboConfig.save();
            return true;
        }

        // 3. RNG
        int rngW = (int) (185.0F * s.rngProfitHudScale);
        int rngH = (int) ((float) ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
        if (this.checkHit(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH)) {
            s.rngProfitHudScale = clampScale(s.rngProfitHudScale + delta);
            this.selectedTarget = HudTarget.RNG;
            BomboConfig.save();
            return true;
        }

        // 4. KUUDRA
        if (this.checkHit(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, (int) (80.0F * s.kuudraBlindnessTimerScale), (int) (12.0F * s.kuudraBlindnessTimerScale))) {
            s.kuudraBlindnessTimerScale = clampScale(s.kuudraBlindnessTimerScale + delta);
            this.selectedTarget = HudTarget.KUUDRA;
            BomboConfig.save();
            return true;
        }

        // 5. PAD_TIMERS
        if (this.checkHit(mouseX, mouseY, s.padTimersX, s.padTimersY, (int) (120.0F * s.padTimersScale), (int) (12.0F * s.padTimersScale))) {
            s.padTimersScale = clampScale(s.padTimersScale + delta);
            this.selectedTarget = HudTarget.PAD_TIMERS;
            BomboConfig.save();
            return true;
        }

        // 6. TIMERS
        int timerW = (int) ((float) CustomTimerManager.getWidth() * s.customTimerHudScale);
        int timerH = (int) ((float) CustomTimerManager.getHeight() * s.customTimerHudScale);
        if (this.checkHit(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH)) {
            s.customTimerHudScale = clampScale(s.customTimerHudScale + delta);
            this.selectedTarget = HudTarget.TIMERS;
            BomboConfig.save();
            return true;
        }

        // 7. COMPOSTER
        int compW = (int) ((float) ComposterHud.getWidth() * s.composterHudScale);
        int compH = (int) ((float) ComposterHud.getHeight() * s.composterHudScale);
        if (this.checkHit(mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH)) {
            s.composterHudScale = clampScale(s.composterHudScale + delta);
            this.selectedTarget = HudTarget.COMPOSTER;
            BomboConfig.save();
            return true;
        }

        // 8. COMPOSTER_TIMER
        if (this.checkHit(mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, (int) (140.0F * s.composterTimerHudScale), (int) (12.0F * s.composterTimerHudScale))) {
            s.composterTimerHudScale = clampScale(s.composterTimerHudScale + delta);
            this.selectedTarget = HudTarget.COMPOSTER_TIMER;
            BomboConfig.save();
            return true;
        }

        // 9. SIGN_CALCULATOR
        int signX = s.signCalculatorX >= 0 ? s.signCalculatorX : (this.width / 2 - (int) (150.0F * s.signCalculatorScale) / 2);
        int signY = s.signCalculatorY >= 0 ? s.signCalculatorY : 40;
        if (this.checkHit(mouseX, mouseY, signX, signY, (int) (150.0F * s.signCalculatorScale), (int) (30.0F * s.signCalculatorScale))) {
            s.signCalculatorScale = clampScale(s.signCalculatorScale + delta);
            this.selectedTarget = HudTarget.SIGN_CALCULATOR;
            BomboConfig.save();
            return true;
        }

        // 10. CHAT_SEARCH
        int csX = s.chatSearchX >= 0 ? s.chatSearchX : 4;
        int csY = s.chatSearchY >= 0 ? s.chatSearchY : (this.height - 28);
        if (this.checkHit(mouseX, mouseY, csX, csY, (int) (160.0F * s.chatSearchScale), (int) (14.0F * s.chatSearchScale))) {
            s.chatSearchScale = clampScale(s.chatSearchScale + delta);
            this.selectedTarget = HudTarget.CHAT_SEARCH;
            BomboConfig.save();
            return true;
        }

        // 11. BOBBER_TIME
        float bobberScale = s.bobberTimeHudScale > 0 ? s.bobberTimeHudScale : 1.0F;
        int bobberX = s.bobberTimeHudX >= 0 ? s.bobberTimeHudX : (this.width / 2 - (int) (95.0F * bobberScale) / 2);
        int bobberY = s.bobberTimeHudY >= 0 ? s.bobberTimeHudY : (this.height / 2 + 15);
        if (this.checkHit(mouseX, mouseY, bobberX, bobberY, (int) (95.0F * bobberScale), (int) (12.0F * bobberScale))) {
            s.bobberTimeHudScale = clampScale(bobberScale + delta);
            this.selectedTarget = HudTarget.BOBBER_TIME;
            BomboConfig.save();
            return true;
        }

        // 12. SPEEDOMETER
        float speedScale = s.speedometerScale > 0 ? s.speedometerScale : 1.0F;
        int speedX = s.speedometerX >= 0 ? s.speedometerX : 10;
        int speedY = s.speedometerY >= 0 ? s.speedometerY : 120;
        if (this.checkHit(mouseX, mouseY, speedX, speedY, (int) (75.0F * speedScale), (int) (12.0F * speedScale))) {
            s.speedometerScale = clampScale(speedScale + delta);
            this.selectedTarget = HudTarget.SPEEDOMETER;
            BomboConfig.save();
            return true;
        }

        // 13. AUTO_CROESUS
        if (this.checkHit(mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, (int) (220.0F * s.autoCroesusHudScale), (int) (110.0F * s.autoCroesusHudScale))) {
            s.autoCroesusHudScale = clampScale(s.autoCroesusHudScale + delta);
            this.selectedTarget = HudTarget.AUTO_CROESUS;
            BomboConfig.save();
            return true;
        }

        // 14. FROZEN_BLAZE
        float fbScale = s.fbWarnTimerScale > 0 ? s.fbWarnTimerScale : 1.0F;
        int fbX = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
        int fbY = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;
        if (this.checkHit(mouseX, mouseY, fbX, fbY, (int) (65.0F * fbScale), (int) (12.0F * fbScale))) {
            s.fbWarnTimerScale = clampScale(fbScale + delta);
            this.selectedTarget = HudTarget.FROZEN_BLAZE;
            BomboConfig.save();
            return true;
        }

        // 15. DOJO_SHOOT
        float dsScale = s.dojoShootHudScale > 0 ? s.dojoShootHudScale : 1.0F;
        int dsX = s.dojoShootHudX >= 0 ? s.dojoShootHudX : (this.width / 2 - (int) (56.0F * dsScale) / 2);
        int dsY = s.dojoShootHudY >= 0 ? s.dojoShootHudY : (this.height / 2 + 18);
        if (this.checkHit(mouseX, mouseY, dsX, dsY, (int) (56.0F * dsScale), (int) (16.0F * dsScale))) {
            s.dojoShootHudScale = clampScale(dsScale + delta);
            this.selectedTarget = HudTarget.DOJO_SHOOT;
            BomboConfig.save();
            return true;
        }

        // 16. ALPHA_TRACKER
        int alphaW = (int) ((float) AlphaTrackerHud.getHudWidth() * s.alphaTrackerHudScale);
        int alphaH = (int) ((float) AlphaTrackerHud.getHudHeight() * s.alphaTrackerHudScale);
        if (this.checkHit(mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, alphaW, alphaH)) {
            s.alphaTrackerHudScale = clampScale(s.alphaTrackerHudScale + delta);
            this.selectedTarget = HudTarget.ALPHA_TRACKER;
            BomboConfig.save();
            return true;
        }

        // 17. TAB_WIDGET
        int tabW = (int) ((float) TabWidgetHud.getWidth() * s.tabWidgetHudScale);
        int tabH = (int) ((float) TabWidgetHud.getHeight(3) * s.tabWidgetHudScale);
        if (this.checkHit(mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, tabW, tabH)) {
            s.tabWidgetHudScale = clampScale(s.tabWidgetHudScale + delta);
            this.selectedTarget = HudTarget.TAB_WIDGET;
            BomboConfig.save();
            return true;
        }

        // Individual Tab Widgets
        if (s.tabWidgets != null) {
            for (BomboConfig.TabWidgetInfo widget : s.tabWidgets) {
                List<String> lines = TabWidgetHud.getMatchedWidgetLines(widget.name, true);
                int widgetW = (int) ((float) TabWidgetHud.getWidth() * widget.scale);
                int widgetH = (int) ((float) TabWidgetHud.getHeight(lines.size()) * widget.scale);
                if (this.checkHit(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH)) {
                    widget.scale = clampScale(widget.scale + delta);
                    this.selectedTarget = HudTarget.TAB_WIDGET;
                    BomboConfig.save();
                    return true;
                }
            }
        }

        // Item List Search bar width resizing
        if (s.itemListEnabled && s.itemListSeparateSearch) {
            int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
            int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
            if (this.checkHit(mouseX, mouseY, searchX, searchY, s.itemListSearchW, 16)) {
                s.itemListSearchScale = clampScale(s.itemListSearchScale + delta);
                this.selectedTarget = HudTarget.ITEM_LIST_SEARCH;
                BomboConfig.save();
                return true;
            }
        }

        // Item List HUD box resizing
        if (s.itemListEnabled) {
            int ilX = s.itemListX == -1 ? this.width - 150 : s.itemListX;
            int ilY = s.itemListY == -1 ? 20 : s.itemListY;
            if (this.checkHit(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH)) {
                int step = (int) (delta * 120);
                s.itemListW = Math.max(100, Math.min(800, s.itemListW + step));
                s.itemListH = Math.max(80, Math.min(1000, s.itemListH + (int)(step * 1.33f)));
                this.selectedTarget = HudTarget.ITEM_LIST;
                BomboConfig.save();
                return true;
            }
        }

        // 18. AUTO_REJOIN
        if (!s.showOnlyActiveHuds || s.autoRejoinHud) {
            int arW = (int) ((float) me.bombo.bomboaddons.features.AutoRejoinHud.getHudWidth() * s.autoRejoinHudScale);
            int arH = (int) ((float) me.bombo.bomboaddons.features.AutoRejoinHud.getHudHeight() * s.autoRejoinHudScale);
            if (this.checkHit(mouseX, mouseY, s.autoRejoinHudX, s.autoRejoinHudY, arW, arH)) {
                s.autoRejoinHudScale = clampScale(s.autoRejoinHudScale + delta);
                this.selectedTarget = HudTarget.AUTO_REJOIN;
                BomboConfig.save();
                return true;
            }
        }

        // 19. ITEM_VALUE_BREAKDOWN
        if (!s.showOnlyActiveHuds || s.itemValueBreakdownHud) {
            int ivW = (int) ((float) me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudWidth() * s.itemValueBreakdownHudScale);
            int ivH = (int) ((float) me.bombo.bomboaddons.features.ItemValueBreakdownHud.getHudHeight(null) * s.itemValueBreakdownHudScale);
            int ivX = s.itemValueBreakdownHudX >= 0 ? s.itemValueBreakdownHudX : 10;
            int ivY = s.itemValueBreakdownHudY >= 0 ? s.itemValueBreakdownHudY : (this.height / 2 - 40);
            if (this.checkHit(mouseX, mouseY, ivX, ivY, ivW, ivH)) {
                s.itemValueBreakdownHudScale = clampScale(s.itemValueBreakdownHudScale + delta);
                this.selectedTarget = HudTarget.ITEM_VALUE_BREAKDOWN;
                BomboConfig.save();
                return true;
            }
        }

        // 20. ARMOR_HUD
        if (!s.showOnlyActiveHuds || s.armorHud) {
            int armW = (int) ((float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudWidth() * s.armorHudScale);
            int armH = (int) ((float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudHeight() * s.armorHudScale);
            if (this.checkHit(mouseX, mouseY, s.armorHudX, s.armorHudY, armW, armH)) {
                float newScale = clampScale(s.armorHudScale + delta);
                if (s.inventoryHud && s.armorHudVertical) {
                    float invTargetH = (float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight() * s.inventoryHudScale;
                    float curArmorBaseH = (float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudHeight();
                    if (Math.abs(curArmorBaseH * newScale - invTargetH) <= 5.0f) {
                        newScale = invTargetH / curArmorBaseH;
                    }
                }
                s.armorHudScale = newScale;
                this.selectedTarget = HudTarget.ARMOR_HUD;
                BomboConfig.save();
                return true;
            }
        }

        // 21. EQUIPMENT_HUD
        if (!s.showOnlyActiveHuds || s.equipmentHud) {
            int eqW = (int) ((float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudWidth() * s.equipmentHudScale);
            int eqH = (int) ((float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudHeight() * s.equipmentHudScale);
            if (this.checkHit(mouseX, mouseY, s.equipmentHudX, s.equipmentHudY, eqW, eqH)) {
                float newScale = clampScale(s.equipmentHudScale + delta);
                if (s.inventoryHud && s.equipmentHudVertical) {
                    float invTargetH = (float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight() * s.inventoryHudScale;
                    float curEqBaseH = (float) me.bombo.bomboaddons.features.hud.EquipmentHud.getHudHeight();
                    if (Math.abs(curEqBaseH * newScale - invTargetH) <= 5.0f) {
                        newScale = invTargetH / curEqBaseH;
                    }
                }
                s.equipmentHudScale = newScale;
                this.selectedTarget = HudTarget.EQUIPMENT_HUD;
                BomboConfig.save();
                return true;
            }
        }

        // 22. INVENTORY_HUD
        if (!s.showOnlyActiveHuds || s.inventoryHud) {
            int invW = (int) ((float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudWidth() * s.inventoryHudScale);
            int invH = (int) ((float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight() * s.inventoryHudScale);
            if (this.checkHit(mouseX, mouseY, s.inventoryHudX, s.inventoryHudY, invW, invH)) {
                float newScale = clampScale(s.inventoryHudScale + delta);
                if (s.armorHud && s.armorHudVertical) {
                    float armTargetH = (float) me.bombo.bomboaddons.features.hud.ArmorHud.getHudHeight() * s.armorHudScale;
                    float curInvBaseH = (float) me.bombo.bomboaddons.features.hud.InventoryHud.getHudHeight();
                    if (Math.abs(curInvBaseH * newScale - armTargetH) <= 5.0f) {
                        newScale = armTargetH / curInvBaseH;
                    }
                }
                s.inventoryHudScale = newScale;
                this.selectedTarget = HudTarget.INVENTORY_HUD;
                BomboConfig.save();
                return true;
            }
        }

        // 23. DUNGEON_MAP
        if (!s.showOnlyActiveHuds || s.dungeonMap) {
            int mapW = (int) ((float) me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapWidth() * s.dungeonMapScale);
            int mapH = (int) ((float) me.bombo.bomboaddons.features.dungeons.map.DungeonMapOverlay.getMapHeight() * s.dungeonMapScale);
            int mapX = s.dungeonMapX >= 0 ? s.dungeonMapX : 10;
            int mapY = s.dungeonMapY >= 0 ? s.dungeonMapY : 10;
            if (this.checkHit(mouseX, mouseY, mapX, mapY, mapW, mapH)) {
                s.dungeonMapScale = clampScale(s.dungeonMapScale + delta);
                this.selectedTarget = HudTarget.DUNGEON_MAP;
                BomboConfig.save();
                return true;
            }
        }

        // 24. CRITTER_HUD
        if (!s.showOnlyActiveHuds || s.critterHud) {
            int critW = (int) ((float) me.bombo.bomboaddons.features.critters.CritterHud.getWidth() * s.critterHudScale);
            int critH = (int) ((float) me.bombo.bomboaddons.features.critters.CritterHud.getHeight() * s.critterHudScale);
            if (this.checkHit(mouseX, mouseY, s.critterHudX, s.critterHudY, critW, critH)) {
                s.critterHudScale = clampScale(s.critterHudScale + delta);
                this.selectedTarget = HudTarget.CRITTER_HUD;
                BomboConfig.save();
                return true;
            }
        }

        // 25. CRITTER_MAP
        if (!s.showOnlyActiveHuds || s.critterMapHud) {
            int mapW = (int) (120.0f * s.critterMapScale);
            int mapH = (int) (120.0f * s.critterMapScale);
            int mx = s.critterMapX >= 0 ? s.critterMapX : (this.width - 130);
            int my = s.critterMapY >= 0 ? s.critterMapY : 10;
            if (this.checkHit(mouseX, mouseY, mx, my, mapW, mapH)) {
                s.critterMapScale = clampScale(s.critterMapScale + delta);
                this.selectedTarget = HudTarget.CRITTER_MAP;
                BomboConfig.save();
                return true;
            }
        }

        // 26. CHAT_TABS
        if (!s.showOnlyActiveHuds || s.chatTabs) {
            int chatTabsW = (int) ((float) me.bombo.bomboaddons.features.chat.ChatTabsOverlay.getWidth() * s.chatTabsScale);
            int chatTabsH = (int) (14.0f * s.chatTabsScale);
            int defaultY = this.height - 28;
            int cx = s.chatTabsX >= 0 ? s.chatTabsX : 4;
            int cy = s.chatTabsY >= 0 ? s.chatTabsY : defaultY;
            if (this.checkHit(mouseX, mouseY, cx, cy, chatTabsW, chatTabsH)) {
                s.chatTabsScale = clampScale(s.chatTabsScale + delta);
                this.selectedTarget = HudTarget.CHAT_TABS;
                BomboConfig.save();
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private static float clampScale(float scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private boolean startCornerResize(double mx, double my, int x, int y, int w, int h, HudTarget target, double scale) {
        // Bottom-right corner handle (8x8 px)
        if (mx >= (double) (x + w - 8) && mx <= (double) (x + w + 4) && my >= (double) (y + h - 8) && my <= (double) (y + h + 4)) {
            this.resizingTarget = target;
            this.selectedTarget = target;
            this.resizeStartScale = scale;
            this.resizeStartW = w;
            this.resizeStartH = h;
            this.resizeStartX = x;
            this.resizeStartY = y;
            this.resizeStartMouseX = mx;
            this.resizeStartMouseY = my;
            return true;
        }
        return false;
    }

    private boolean checkHit(double mx, double my, int x, int y, int w, int h) {
        return mx >= (double) x && mx <= (double) (x + w) && my >= (double) y && my <= (double) (y + h);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingTarget != null || this.resizingTarget != null) {
            if (this.pendingUndoAction != null) {
                undoStack.push(this.pendingUndoAction);
                this.pendingUndoAction = null;
            }
        }
        this.resizingTarget = null;
        this.draggingTarget = null;
        this.editingWidgetIdx = -1;
        BomboConfig.save();
        return super.mouseReleased(event);
    }

    private void selectAndDrag(HudTarget target, int ox, int oy) {
        long handle = Minecraft.getInstance().getWindow().handle();
        boolean isCtrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (isCtrl && target != null) {
            openSettingsForTarget(target);
            return;
        }

        this.selectedTarget = target;
        this.draggingTarget = target;
        this.dragOffsetX = ox;
        this.dragOffsetY = oy;
        if (this.pendingUndoAction == null) {
            this.pendingUndoAction = captureCurrentState();
        }
    }

    private void openSettingsForTarget(HudTarget target) {
        String category = switch (target) {
            case CROESUS_PROFIT, CROESUS_TRACKER -> "Auto Croesus";
            case DICE, BAKERY, RNG, PAD_TIMERS, HOPPITY, ALPHA_TRACKER, AUTO_CROESUS, SIGN_CALCULATOR, DOJO_SHOOT, ITEM_LIST, ITEM_LIST_SEARCH, AUTO_REJOIN, ITEM_VALUE_BREAKDOWN, ARMOR_HUD, EQUIPMENT_HUD, INVENTORY_HUD -> "HUDs";
            case KUUDRA -> "Kuudra";
            case TIMERS -> "Timers";
            case COMPOSTER, COMPOSTER_TIMER -> "Garden";
            case CHAT_SEARCH, FROZEN_BLAZE, SPEEDOMETER -> "Misc";
            case BOBBER_TIME -> "Fishing";
            case TAB_WIDGET -> "Widgets";
            case DUNGEON_MAP -> "Dungeons";
            case CRITTER_HUD, CRITTER_MAP -> "Critters";
            case CHAT_TABS -> "Chat";
        };
        me.bombo.bomboaddons.gui.config.BomboConfigScreen.activeCategory = category;
        me.bombo.bomboaddons.gui.config.BomboConfigScreen.searchQuery = "";
        Minecraft.getInstance().setScreenAndShow(me.bombo.bomboaddons.gui.config.BomboConfigScreen.create());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        long handle = Minecraft.getInstance().getWindow().handle();
        boolean isCtrl = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        // Ctrl + Z Undo for HUD placement & resizing (only stationary changes)
        if (isCtrl && event.key() == GLFW.GLFW_KEY_Z) {
            if (!undoStack.isEmpty()) {
                undoStack.pop().run();
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("§8[§3Bombo§8]§r §aUndid HUD movement!"));
                }
                return true;
            }
        }
        if (event.key() == GLFW.GLFW_KEY_R) {
            HudTarget targetToReset = this.selectedTarget != null ? this.selectedTarget : this.hoveredTarget;
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            BomboConfig.Settings s = BomboConfig.get();
            if (targetToReset != null) {
                switch (targetToReset) {
                    case DICE -> { s.diceHudX = centerX - (int) (130 * s.diceHudScale); s.diceHudY = centerY - 100; s.diceHudScale = 1.0f; }
                    case BAKERY -> { s.feastBakeryHudX = centerX - (int) (FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale / 2); s.feastBakeryHudY = centerY - 60; s.feastBakeryHudScale = 1.0f; }
                    case RNG -> { s.rngProfitHudX = centerX - 92; s.rngProfitHudY = centerY - 40; s.rngProfitHudScale = 1.0f; }
                    case KUUDRA -> { s.kuudraBlindnessTimerX = centerX - 40; s.kuudraBlindnessTimerY = centerY - 80; s.kuudraBlindnessTimerScale = 1.0f; }
                    case PAD_TIMERS -> { s.padTimersX = centerX - 60; s.padTimersY = centerY - 90; s.padTimersScale = 1.0f; }
                    case TIMERS -> { s.customTimerHudX = centerX - 60; s.customTimerHudY = centerY - 70; s.customTimerHudScale = 1.0f; }
                    case COMPOSTER -> { s.composterHudX = centerX - 70; s.composterHudY = centerY + 10; s.composterHudScale = 1.0f; }
                    case COMPOSTER_TIMER -> { s.composterTimerHudX = centerX - 70; s.composterTimerHudY = centerY + 40; s.composterTimerHudScale = 1.0f; }
                    case HOPPITY -> { s.hoppityHudX = centerX - 45; s.hoppityHudY = centerY - 22; }
                    case ALPHA_TRACKER -> { s.alphaTrackerHudX = centerX - (int) (AlphaTrackerHud.getHudWidth() * s.alphaTrackerHudScale / 2); s.alphaTrackerHudY = centerY + 60; s.alphaTrackerHudScale = 1.0f; }
                    case AUTO_CROESUS -> { s.autoCroesusHudX = centerX - 110; s.autoCroesusHudY = centerY - 55; s.autoCroesusHudScale = 1.0f; }
                    case CROESUS_PROFIT -> { s.croesusProfitHudX = centerX + 120; s.croesusProfitHudY = centerY - 100; s.croesusProfitHudScale = 1.0f; }
                    case CROESUS_TRACKER -> { s.croesusTrackerHudX = centerX - 75; s.croesusTrackerHudY = centerY + 60; s.croesusTrackerHudScale = 1.0f; }
                    case SIGN_CALCULATOR -> { s.signCalculatorX = centerX - 75; s.signCalculatorY = centerY + 30; s.signCalculatorScale = 1.0f; }
                    case CHAT_SEARCH -> { s.chatSearchX = 4; s.chatSearchY = this.height - 28; s.chatSearchScale = 1.0f; }
                    case FROZEN_BLAZE -> { s.fbWarnTimerX = centerX - 32; s.fbWarnTimerY = centerY - 50; s.fbWarnTimerScale = 1.0f; }
                    case DOJO_SHOOT -> { s.dojoShootHudX = centerX - 28; s.dojoShootHudY = centerY + 18; s.dojoShootHudScale = 1.0f; }
                    case BOBBER_TIME -> { s.bobberTimeHudX = centerX - 47; s.bobberTimeHudY = centerY + 15; s.bobberTimeHudScale = 1.0f; }
                    case SPEEDOMETER -> { s.speedometerX = centerX - 37; s.speedometerY = centerY + 35; s.speedometerScale = 1.0f; }
                    case TAB_WIDGET -> { s.tabWidgetHudX = centerX - 60; s.tabWidgetHudY = centerY - 30; s.tabWidgetHudScale = 1.0f; }
                    case ITEM_LIST -> { s.itemListX = this.width - 150; s.itemListY = 20; s.itemListW = 140; s.itemListH = 200; }
                    case DUNGEON_MAP -> { s.dungeonMapX = 10; s.dungeonMapY = 10; s.dungeonMapScale = 1.0f; }
                    case ITEM_LIST_SEARCH -> { s.itemListSearchX = centerX - 75; s.itemListSearchY = centerY + 20; s.itemListSearchScale = 1.0f; }
                    case AUTO_REJOIN -> { s.autoRejoinHudX = 10; s.autoRejoinHudY = 150; s.autoRejoinHudScale = 1.0f; }
                    case ITEM_VALUE_BREAKDOWN -> { s.itemValueBreakdownHudX = 10; s.itemValueBreakdownHudY = centerY - 40; s.itemValueBreakdownHudScale = 1.0f; }
                    case ARMOR_HUD -> { s.armorHudX = 10; s.armorHudY = 160; s.armorHudScale = 1.0f; }
                    case EQUIPMENT_HUD -> { s.equipmentHudX = 10; s.equipmentHudY = 220; s.equipmentHudScale = 1.0f; }
                    case INVENTORY_HUD -> { s.inventoryHudX = 10; s.inventoryHudY = 280; s.inventoryHudScale = 1.0f; }
                    case CRITTER_HUD -> { s.critterHudX = 10; s.critterHudY = 10; s.critterHudScale = 1.0f; }
                    case CRITTER_MAP -> { s.critterMapX = this.width - 130; s.critterMapY = 10; s.critterMapScale = 1.0f; }
                    case CHAT_TABS -> { s.chatTabsX = 4; s.chatTabsY = this.height - 28; s.chatTabsScale = 1.0f; }
                }
                BomboConfig.save();
                return true;
            }
        }
        return super.keyPressed(event);
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

    public enum HudTarget {
        DICE,
        BAKERY,
        RNG,
        KUUDRA,
        PAD_TIMERS,
        TIMERS,
        COMPOSTER,
        COMPOSTER_TIMER,
        TAB_WIDGET,
        ITEM_LIST,
        ITEM_LIST_SEARCH,
        HOPPITY,
        ALPHA_TRACKER,
        AUTO_CROESUS,
        SIGN_CALCULATOR,
        CHAT_SEARCH,
        FROZEN_BLAZE,
        DOJO_SHOOT,
        BOBBER_TIME,
        SPEEDOMETER,
        DUNGEON_MAP,
        AUTO_REJOIN,
        ITEM_VALUE_BREAKDOWN,
        ARMOR_HUD,
        EQUIPMENT_HUD,
        INVENTORY_HUD,
        CRITTER_HUD,
        CRITTER_MAP,
        CHAT_TABS,
        CROESUS_PROFIT,
        CROESUS_TRACKER;
    }
}
