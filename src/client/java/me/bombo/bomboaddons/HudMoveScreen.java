package me.bombo.bomboaddons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class HudMoveScreen extends Screen {
   private static boolean showOnlyActiveHuds = false;
   private HudTarget draggingTarget = null;
   private int dragOffsetX = 0;
   private int dragOffsetY = 0;
   private int editingWidgetIdx = -1;
   private boolean isResizingItemList = false;
   private HudTarget resizingTarget = null;
   private int resizeCorner = -1;
   private double resizeStartScale = (double)1.0F;
   private int resizeStartW = 0;
   private int resizeStartH = 0;
   private int resizeStartX = 0;
   private int resizeStartY = 0;
   private double resizeStartMouseX = (double)0.0F;
   private double resizeStartMouseY = (double)0.0F;

   public HudMoveScreen() {
      super(Component.literal("Move HUD"));
   }

   protected void init() {
      super.init();
      if (BomboConfig.get().itemListEnabled) {
         ItemListOverlay.updateLayout(0, this.width, 0, this.width, this.height);
         ItemListOverlay.searchBox = null;
      }

      BomboConfig.Settings s = BomboConfig.get();
      String toggleText = s.showOnlyActiveHuds ? "§e[ Mode: Active HUDs ]" : "§a[ Mode: All HUDs ]";
      this.addRenderableWidget(Button.builder(Component.literal(toggleText), (btn) -> {
         s.showOnlyActiveHuds = !s.showOnlyActiveHuds;
         BomboConfig.save();
         this.init();
      }).bounds(10, 10, 140, 20).build());
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      g.fill(0, 0, this.width, this.height, -2013265920);
      Identifier invTex = Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
      int invW = 176;
      int invH = 166;
      int invX = (this.width - invW) / 2;
      int invY = (this.height - invH) / 2;
      g.blit(invTex, invX, invY, invX + invW, invY + invH, 0.0F, 0.6875F, 0.0F, 0.6484375F);
      BomboConfig.Settings s = BomboConfig.get();
      if (!s.showOnlyActiveHuds || s.diceTracker && DiceTracker.shouldShowHud()) {
         this.renderTarget(g, mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(260.0F * s.diceHudScale), (int)(52.0F * s.diceHudScale), HudMoveScreen.HudTarget.DICE);
         DiceHud.drawDiceInfo(g, s.diceHudX, s.diceHudY, this.draggingTarget == HudMoveScreen.HudTarget.DICE);
         g.text(this.font, "§aRight-click to swap modes!", s.diceHudX, s.diceHudY - 10, -1, true);
      }

      if (!s.showOnlyActiveHuds || s.feastBakeryHud) {
         int bakeryW = (int)((float)FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
         int bakeryH = (int)((float)FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
         this.renderTarget(g, mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH, HudMoveScreen.HudTarget.BAKERY);
         List<FeastBakeryHud.DetectedItem> dummy = new ArrayList();
         dummy.add(new FeastBakeryHud.DetectedItem("FRESHLY_BAKED_TALISMAN", "Baked Talisman", 25));
         dummy.add(new FeastBakeryHud.DetectedItem("POPCORN_RING", "Popcorn Ring", 125));
         dummy.add(new FeastBakeryHud.DetectedItem("ENCHANTMENT_FEAST_1", "Enchanted Book (Feast I)", 500));
         FeastBakeryHud.drawBakeryInfo(g, s.feastBakeryHudX, s.feastBakeryHudY, dummy);
      }

      if (!s.showOnlyActiveHuds || s.rngProfitHud) {
         int rngW = (int)(185.0F * s.rngProfitHudScale);
         int rngH = (int)((float)ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
         this.renderTarget(g, mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH, HudMoveScreen.HudTarget.RNG);
         ExperimentationTableHud.onHudRender(g);
      }

      if (!s.showOnlyActiveHuds || s.kuudraBlindnessTimer && KuudraTimer.isActive()) {
         int kuudraW = (int)(80.0F * s.kuudraBlindnessTimerScale);
         int kuudraH = (int)(12.0F * s.kuudraBlindnessTimerScale);
         this.renderTarget(g, mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, kuudraW, kuudraH, HudMoveScreen.HudTarget.KUUDRA);
         KuudraTimer.drawTimerInfo(g, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, true);
      }

      if (!s.showOnlyActiveHuds || (s.padTimersPurple || s.padTimersGreen) && DungeonPadTimers.isActive()) {
         int padW = (int)(120.0F * s.padTimersScale);
         int padH = (int)(12.0F * s.padTimersScale);
         this.renderTarget(g, mouseX, mouseY, s.padTimersX, s.padTimersY, padW, padH, HudMoveScreen.HudTarget.PAD_TIMERS);
         DungeonPadTimers.drawTimerInfo(g, s.padTimersX, s.padTimersY, true);
      }

      if (!s.showOnlyActiveHuds || s.customTimeEnabled && !CustomTimerManager.activeTimers.isEmpty()) {
         int timerW = (int)((float)CustomTimerManager.getWidth() * s.customTimerHudScale);
         int timerH = (int)((float)CustomTimerManager.getHeight() * s.customTimerHudScale);
         this.renderTarget(g, mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH, HudMoveScreen.HudTarget.TIMERS);
         CustomTimerManager.drawTimers(g, s.customTimerHudX, s.customTimerHudY, true);
      }

      boolean compDataExists = s.composterLastOrganic >= (double)0.0F || s.composterLastFuel >= (double)0.0F;
      if (!s.showOnlyActiveHuds || s.composterHud && compDataExists) {
         int compW = (int)((float)ComposterHud.getWidth() * s.composterHudScale);
         int compH = (int)((float)ComposterHud.getHeight() * s.composterHudScale);
         this.renderTarget(g, mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH, HudMoveScreen.HudTarget.COMPOSTER);
         ComposterHud.drawComposterInfo(g, s.composterHudX, s.composterHudY, !compDataExists);
      }

      if (!s.showOnlyActiveHuds || s.composterTimerHud && compDataExists) {
         int compTimerW = (int)(140.0F * s.composterTimerHudScale);
         int compTimerH = (int)(12.0F * s.composterTimerHudScale);
         this.renderTarget(g, mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, compTimerW, compTimerH, HudMoveScreen.HudTarget.COMPOSTER_TIMER);
         ComposterHud.drawComposterTimerInfo(g, s.composterTimerHudX, s.composterTimerHudY, !compDataExists);
      }

      if (!s.showOnlyActiveHuds || s.hoppityHud) {
         this.renderTarget(g, mouseX, mouseY, s.hoppityHudX, s.hoppityHudY, 90, 45, HudMoveScreen.HudTarget.HOPPITY);
      }

      if (!s.showOnlyActiveHuds || s.alphaTrackerHud) {
         int alphaW = (int)((float)AlphaTrackerHud.getHudWidth() * s.alphaTrackerHudScale);
         int alphaH = (int)((float)AlphaTrackerHud.getHudHeight() * s.alphaTrackerHudScale);
         this.renderTarget(g, mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, alphaW, alphaH, HudMoveScreen.HudTarget.ALPHA_TRACKER);
         AlphaTrackerHud.drawAlphaInfo(g, this.font, s.alphaTrackerHudX, s.alphaTrackerHudY);
      }

      if (!s.showOnlyActiveHuds || s.signCalculator) {
         int signW = (int)(150.0F * s.signCalculatorScale);
         int signH = (int)(30.0F * s.signCalculatorScale);
         int signX = s.signCalculatorX >= 0 ? s.signCalculatorX : (this.width / 2 - signW / 2);
         int signY = s.signCalculatorY;
         this.renderTarget(g, mouseX, mouseY, signX, signY, signW, signH, HudMoveScreen.HudTarget.SIGN_CALCULATOR);
         g.text(this.font, "\u00a76Total: \u00a7e100,000", signX + 4, signY + 4, -1, true);
         g.text(this.font, "\u00a7a100000+1 = 100,001", signX + 4, signY + 16, -1, true);
      }

      if (!s.showOnlyActiveHuds || s.chatSearchBar) {
         int csW = (int)(160.0F * s.chatSearchScale);
         int csH = (int)(14.0F * s.chatSearchScale);
         int csX = s.chatSearchX >= 0 ? s.chatSearchX : 4;
         int csY = s.chatSearchY >= 0 ? s.chatSearchY : (this.height - 28);
         this.renderTarget(g, mouseX, mouseY, csX, csY, csW, csH, HudMoveScreen.HudTarget.CHAT_SEARCH);
         g.text(this.font, "\u00a7eIn-Chat Search Bar", csX + 4, csY + 3, -1, true);
      }

      if (!s.showOnlyActiveHuds || s.frozenBlazeWarning && s.fbWarnTimerOnScreen) {
         int fbX = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
         int fbY = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;
         float fbScale = s.fbWarnTimerScale > 0.0F ? s.fbWarnTimerScale : 1.0F;
         int fbW = (int)(65.0F * fbScale);
         int fbH = (int)(12.0F * fbScale);
         this.renderTarget(g, mouseX, mouseY, fbX, fbY, fbW, fbH, HudMoveScreen.HudTarget.FROZEN_BLAZE);
         me.bombo.bomboaddons.features.FrozenBlazeAFKTracker.drawTimerInfo(g, fbX, fbY, fbScale, true);
      }

      if (!s.showOnlyActiveHuds || s.dojoUtilities && s.dojoMasteryWool) {
         int dsW = (int)(56.0F * s.dojoShootHudScale);
         int dsH = (int)(16.0F * s.dojoShootHudScale);
         int dsX = s.dojoShootHudX >= 0 ? s.dojoShootHudX : (this.width / 2 - dsW / 2);
         int dsY = s.dojoShootHudY >= 0 ? s.dojoShootHudY : (this.height / 2 + 18);
         this.renderTarget(g, mouseX, mouseY, dsX, dsY, dsW, dsH, HudMoveScreen.HudTarget.DOJO_SHOOT);
         me.bombo.bomboaddons.features.DojoUtilities.drawShootHud(g, dsX, dsY, s.dojoShootHudScale, true);
      }

      if (!s.showOnlyActiveHuds || s.autoCroesusHud) {
         int croesusW = (int)(220.0F * s.autoCroesusHudScale);
         int croesusH = (int)(110.0F * s.autoCroesusHudScale);
         this.renderTarget(g, mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, croesusW, croesusH, HudMoveScreen.HudTarget.AUTO_CROESUS);
         AutoCroesusHud.drawCroesusInfo(g, s.autoCroesusHudX, s.autoCroesusHudY, s.autoCroesusHudScale, true);
      }

      if (!s.showOnlyActiveHuds || s.tabWidgetHudEnabled) {
         List<String> lines = TabWidgetHud.getMatchedWidgetLines(s.tabWidgetQuery != null && !s.tabWidgetQuery.isEmpty() ? s.tabWidgetQuery : "Bestiary", false);
         if (lines.isEmpty()) {
            lines = TabWidgetHud.getMatchedWidgetLines(s.tabWidgetQuery != null && !s.tabWidgetQuery.isEmpty() ? s.tabWidgetQuery : "Bestiary", true);
         }
         int tabW = (int)((float)TabWidgetHud.getWidth() * s.tabWidgetHudScale);
         int tabH = (int)((float)TabWidgetHud.getHeight(lines.size()) * s.tabWidgetHudScale);
         this.renderTarget(g, mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, tabW, tabH, HudMoveScreen.HudTarget.TAB_WIDGET);
         TabWidgetHud.drawWidgetInfo(g, s.tabWidgetHudX, s.tabWidgetHudY, s.tabWidgetHudScale, lines);
      }

      if (s.tabWidgets != null) {
         for(int i = 0; i < s.tabWidgets.size(); ++i) {
            BomboConfig.TabWidgetInfo widget = (BomboConfig.TabWidgetInfo)s.tabWidgets.get(i);
            if (widget.enabled || !s.showOnlyActiveHuds) {
               List<String> lines = TabWidgetHud.getMatchedWidgetLines(widget.name, false);
               if (lines.isEmpty()) {
                  lines = TabWidgetHud.getMatchedWidgetLines(widget.name, true);
               }

               int widgetW = (int)((float)TabWidgetHud.getWidth() * widget.scale);
               int widgetH = (int)((float)TabWidgetHud.getHeight(lines.size()) * widget.scale);
               boolean hovered = mouseX >= widget.x - 12 && mouseX <= widget.x + widgetW + 12 && mouseY >= widget.y - 12 && mouseY <= widget.y + widgetH + 12;
               if (this.draggingTarget == HudMoveScreen.HudTarget.TAB_WIDGET && this.editingWidgetIdx == i) {
                  widget.x = mouseX - this.dragOffsetX;
                  widget.y = mouseY - this.dragOffsetY;
               }

               g.fill(widget.x - 2, widget.y - 2, widget.x + widgetW, widget.y + widgetH, 587202559);
               if (!hovered && (this.draggingTarget != HudMoveScreen.HudTarget.TAB_WIDGET || this.editingWidgetIdx != i)) {
                  g.outline(widget.x - 2, widget.y - 2, widgetW + 2, widgetH + 2, -11184811);
               } else {
                  g.outline(widget.x - 2, widget.y - 2, widgetW + 2, widgetH + 2, -256);
                  g.text(this.font, "§eWidget: " + widget.name, widget.x, widget.y - 12, -1, true);
               }

               TabWidgetHud.drawWidgetInfo(g, widget.x, widget.y, widget.scale, lines);
            }
         }
      }

      g.centeredText(this.font, "§e§lHUD EDIT MODE", this.width / 2, 10, -1);
      g.centeredText(this.font, "§7Drag elements to reposition them, scroll wheel to resize", this.width / 2, 22, -1);
      g.centeredText(this.font, "§cPress ESC to save and close", this.width / 2, this.height - 20, -1);
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      super.extractRenderState(g, mouseX, mouseY, partialTick);
      if (BomboConfig.get().itemListEnabled) {
         int ilX = s.itemListX == -1 ? this.width - 150 : s.itemListX;
         int ilY = s.itemListY == -1 ? 20 : s.itemListY;
         this.renderTarget(g, mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH, HudMoveScreen.HudTarget.ITEM_LIST);
         ItemListOverlay.render(g, Minecraft.getInstance().font, mouseX, mouseY);
         if (s.itemListSeparateSearch) {
            int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
            int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
            int searchW = (int)((float)s.itemListSearchW * s.itemListSearchScale);
            int searchH = (int)(16.0F * s.itemListSearchScale);
            this.renderTarget(g, mouseX, mouseY, searchX, searchY, searchW, searchH, HudMoveScreen.HudTarget.ITEM_LIST_SEARCH);
            g.pose().pushMatrix();
            g.pose().translate((float)searchX, (float)searchY);
            g.pose().scale(s.itemListSearchScale, s.itemListSearchScale);
            g.fill(0, 0, s.itemListSearchW, 16, -1442840576);
            g.outline(0, 0, s.itemListSearchW, 16, -5592406);
            g.text(Minecraft.getInstance().font, "Search...", 4, 4, -5592406, false);
            g.pose().popMatrix();
         }
      }

   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      if (this.resizingTarget == null) {
         return super.mouseDragged(event, dragX, dragY);
      } else {
         BomboConfig.Settings s = BomboConfig.get();
         double mx = event.x();
         double my = event.y();
         double originX = (double)(this.resizeStartX + (this.resizeCorner != 1 && this.resizeCorner != 3 ? this.resizeStartW : 0));
         double originY = (double)(this.resizeStartY + (this.resizeCorner != 2 && this.resizeCorner != 3 ? this.resizeStartH : 0));
         double oldDist = Math.hypot(this.resizeStartMouseX - originX, this.resizeStartMouseY - originY);
         double newDist = Math.hypot(mx - originX, my - originY);
         if (oldDist < (double)1.0F) {
            oldDist = (double)1.0F;
         }

         float newScale = (float)(this.resizeStartScale * (newDist / oldDist));
         newScale = Math.max(0.2F, Math.min(5.0F, newScale));
         if (this.resizingTarget == HudMoveScreen.HudTarget.ITEM_LIST) {
            int dx = (int)(mx - this.resizeStartMouseX);
            int dy = (int)(my - this.resizeStartMouseY);
            if (this.resizeCorner == 0) {
               s.itemListX = this.resizeStartX + dx;
               s.itemListY = this.resizeStartY + dy;
               s.itemListW = Math.max(120, this.resizeStartW - dx);
               s.itemListH = Math.max(100, this.resizeStartH - dy);
            } else if (this.resizeCorner == 1) {
               s.itemListY = this.resizeStartY + dy;
               s.itemListW = Math.max(120, this.resizeStartW + dx);
               s.itemListH = Math.max(100, this.resizeStartH - dy);
            } else if (this.resizeCorner == 2) {
               s.itemListX = this.resizeStartX + dx;
               s.itemListW = Math.max(120, this.resizeStartW - dx);
               s.itemListH = Math.max(100, this.resizeStartH + dy);
            } else if (this.resizeCorner == 3) {
               s.itemListW = Math.max(120, this.resizeStartW + dx);
               s.itemListH = Math.max(100, this.resizeStartH + dy);
            }
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.ITEM_LIST_SEARCH) {
            s.itemListSearchScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.DICE) {
            s.diceHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.BAKERY) {
            s.feastBakeryHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.RNG) {
            s.rngProfitHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.KUUDRA) {
            s.kuudraBlindnessTimerScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.PAD_TIMERS) {
            s.padTimersScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.TIMERS) {
            s.customTimerHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.COMPOSTER) {
            s.composterHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.COMPOSTER_TIMER) {
            s.composterTimerHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.TAB_WIDGET) {
            s.tabWidgetHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.ALPHA_TRACKER) {
            s.alphaTrackerHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.AUTO_CROESUS) {
            s.autoCroesusHudScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.FROZEN_BLAZE) {
            s.fbWarnTimerScale = newScale;
         } else if (this.resizingTarget == HudMoveScreen.HudTarget.DOJO_SHOOT) {
            s.dojoShootHudScale = newScale;
         }

         return true;
      }
   }

   private void renderTarget(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, HudTarget target) {
      BomboConfig.Settings s = BomboConfig.get();
      boolean hovered = mouseX >= x - 12 && mouseX <= x + w + 12 && mouseY >= y - 12 && mouseY <= y + h + 12;
      if (this.draggingTarget == target) {
         if (target == HudMoveScreen.HudTarget.DICE) {
            s.diceHudX = mouseX - this.dragOffsetX;
            s.diceHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.BAKERY) {
            s.feastBakeryHudX = mouseX - this.dragOffsetX;
            s.feastBakeryHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.RNG) {
            s.rngProfitHudX = mouseX - this.dragOffsetX;
            s.rngProfitHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.KUUDRA) {
            s.kuudraBlindnessTimerX = mouseX - this.dragOffsetX;
            s.kuudraBlindnessTimerY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.PAD_TIMERS) {
            s.padTimersX = mouseX - this.dragOffsetX;
            s.padTimersY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.TIMERS) {
            s.customTimerHudX = mouseX - this.dragOffsetX;
            s.customTimerHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.COMPOSTER) {
            s.composterHudX = mouseX - this.dragOffsetX;
            s.composterHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.COMPOSTER_TIMER) {
            s.composterTimerHudX = mouseX - this.dragOffsetX;
            s.composterTimerHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.TAB_WIDGET) {
            if (this.editingWidgetIdx == -1) {
               s.tabWidgetHudX = mouseX - this.dragOffsetX;
               s.tabWidgetHudY = mouseY - this.dragOffsetY;
            } else if (this.editingWidgetIdx >= 0 && s.tabWidgets != null && this.editingWidgetIdx < s.tabWidgets.size()) {
               BomboConfig.TabWidgetInfo twi = (BomboConfig.TabWidgetInfo)s.tabWidgets.get(this.editingWidgetIdx);
               twi.x = mouseX - this.dragOffsetX;
               twi.y = mouseY - this.dragOffsetY;
            }
         } else if (target == HudMoveScreen.HudTarget.ITEM_LIST) {
            s.itemListX = mouseX - this.dragOffsetX;
            s.itemListY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.ITEM_LIST_SEARCH) {
            s.itemListSearchX = mouseX - this.dragOffsetX;
            s.itemListSearchY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.HOPPITY) {
            s.hoppityHudX = mouseX - this.dragOffsetX;
            s.hoppityHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.ALPHA_TRACKER) {
            s.alphaTrackerHudX = mouseX - this.dragOffsetX;
            s.alphaTrackerHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.SIGN_CALCULATOR) {
            s.signCalculatorX = mouseX - this.dragOffsetX;
            s.signCalculatorY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.CHAT_SEARCH) {
            s.chatSearchX = mouseX - this.dragOffsetX;
            s.chatSearchY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.AUTO_CROESUS) {
            s.autoCroesusHudX = mouseX - this.dragOffsetX;
            s.autoCroesusHudY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.FROZEN_BLAZE) {
            s.fbWarnTimerX = mouseX - this.dragOffsetX;
            s.fbWarnTimerY = mouseY - this.dragOffsetY;
         } else if (target == HudMoveScreen.HudTarget.DOJO_SHOOT) {
            s.dojoShootHudX = mouseX - this.dragOffsetX;
            s.dojoShootHudY = mouseY - this.dragOffsetY;
         }
      }

      g.fill(x - 2, y - 2, x + w, y + h, 587202559);
      if (hovered || this.draggingTarget == target || this.resizingTarget == target) {
         g.outline(x - 2, y - 2, w + 2, h + 2, -256);
         g.fill(x - 5, y - 5, x + 3, y + 3, -1);
         g.fill(x + w - 3, y - 5, x + w + 5, y + 3, -1);
         g.fill(x - 5, y + h - 3, x + 3, y + h + 5, -1);
         g.fill(x + w - 3, y + h - 3, x + w + 5, y + h + 5, -1);
         String var10000;
         switch (target) {
            case DICE -> var10000 = "Dice Tracker HUD";
            case BAKERY -> var10000 = "Feast Bakery HUD";
            case RNG -> var10000 = "RNG Profit HUD";
            case KUUDRA -> var10000 = "Kuudra Blindness Timer";
            case PAD_TIMERS -> var10000 = "Pad Timers";
            case TIMERS -> var10000 = "Custom Timers";
            case COMPOSTER -> var10000 = "Composter Status HUD";
            case COMPOSTER_TIMER -> var10000 = "Composter Timer HUD";
            case TAB_WIDGET -> var10000 = "Tab Widget HUD";
            case ITEM_LIST -> var10000 = "Item List HUD";
            case ITEM_LIST_SEARCH -> var10000 = "Item List Search";
            case HOPPITY -> var10000 = "Hoppity Egg HUD";
            case ALPHA_TRACKER -> var10000 = "Alpha Tracker HUD";
            case AUTO_CROESUS -> var10000 = "Auto Croesus HUD";
            case SIGN_CALCULATOR -> var10000 = "Sign Calculator";
            case CHAT_SEARCH -> var10000 = "In-Chat Search Bar";
            case FROZEN_BLAZE -> var10000 = "Frozen Blaze AFK Timer";
            default -> var10000 = target.name();
         }

         String targetName = var10000;
         g.text(this.font, "§e" + targetName, x, y - 12, -1, true);
      }

   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      if (BomboConfig.get().itemListEnabled && ItemListOverlay.mouseClicked(event.x(), event.y(), event.button())) {
         return true;
      } else {
         BomboConfig.Settings s = BomboConfig.get();
         double mouseX = event.x();
         double mouseY = event.y();
         int button = event.button();
         if (!s.showOnlyActiveHuds || s.signCalculator) {
            int signW = (int)(150.0F * s.signCalculatorScale);
            int signH = (int)(30.0F * s.signCalculatorScale);
            int signX = s.signCalculatorX >= 0 ? s.signCalculatorX : (this.width / 2 - signW / 2);
            int signY = s.signCalculatorY;
            if (this.checkHit(mouseX, mouseY, signX, signY, signW, signH)) {
               this.startDragging(HudMoveScreen.HudTarget.SIGN_CALCULATOR, (int)mouseX - signX, (int)mouseY - signY);
               return true;
            }
         }
         if (!s.showOnlyActiveHuds || s.frozenBlazeWarning && s.fbWarnTimerOnScreen) {
             int fbX = s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10;
             int fbY = s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120;
             float fbScale = s.fbWarnTimerScale > 0.0F ? s.fbWarnTimerScale : 1.0F;
             int fbW = (int)(65.0F * fbScale);
             int fbH = (int)(12.0F * fbScale);
             if (this.startCornerResize(mouseX, mouseY, fbX, fbY, fbW, fbH, HudMoveScreen.HudTarget.FROZEN_BLAZE, (double)fbScale)) {
                return true;
             } else if (this.checkHit(mouseX, mouseY, fbX, fbY, fbW, fbH)) {
                this.startDragging(HudMoveScreen.HudTarget.FROZEN_BLAZE, (int)mouseX - fbX, (int)mouseY - fbY);
                return true;
             }
          }
          if (!s.showOnlyActiveHuds || s.dojoUtilities && s.dojoMasteryWool) {
             int dsW = (int)(56.0F * s.dojoShootHudScale);
             int dsH = (int)(16.0F * s.dojoShootHudScale);
             int dsX = s.dojoShootHudX >= 0 ? s.dojoShootHudX : (this.width / 2 - dsW / 2);
             int dsY = s.dojoShootHudY >= 0 ? s.dojoShootHudY : (this.height / 2 + 18);
             if (this.startCornerResize(mouseX, mouseY, dsX, dsY, dsW, dsH, HudMoveScreen.HudTarget.DOJO_SHOOT, (double)s.dojoShootHudScale)) {
                return true;
             } else if (this.checkHit(mouseX, mouseY, dsX, dsY, dsW, dsH)) {
                this.startDragging(HudMoveScreen.HudTarget.DOJO_SHOOT, (int)mouseX - dsX, (int)mouseY - dsY);
                return true;
             }
          }
         if (!s.showOnlyActiveHuds || s.chatSearchBar) {
            int csW = (int)(160.0F * s.chatSearchScale);
            int csH = (int)(14.0F * s.chatSearchScale);
            int csX = s.chatSearchX >= 0 ? s.chatSearchX : 4;
            int csY = s.chatSearchY >= 0 ? s.chatSearchY : (this.height - 28);
            if (this.checkHit(mouseX, mouseY, csX, csY, csW, csH)) {
               this.startDragging(HudMoveScreen.HudTarget.CHAT_SEARCH, (int)mouseX - csX, (int)mouseY - csY);
               return true;
            }
         }
         int rngW = (int)(185.0F * s.rngProfitHudScale);
         int rngH = (int)((float)ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
         if (this.startCornerResize(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH, HudMoveScreen.HudTarget.RNG, (double)s.rngProfitHudScale)) {
            return true;
         } else if (this.checkHit(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH)) {
            this.startDragging(HudMoveScreen.HudTarget.RNG, (int)mouseX - s.rngProfitHudX, (int)mouseY - s.rngProfitHudY);
            return true;
         } else {
            int bakeryW = (int)((float)FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
            int bakeryH = (int)((float)FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
            if (this.startCornerResize(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH, HudMoveScreen.HudTarget.BAKERY, (double)s.feastBakeryHudScale)) {
               return true;
            } else if (this.checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH)) {
               this.startDragging(HudMoveScreen.HudTarget.BAKERY, (int)mouseX - s.feastBakeryHudX, (int)mouseY - s.feastBakeryHudY);
               return true;
            } else {
               int diceW = (int)(260.0F * s.diceHudScale);
               int diceH = (int)(52.0F * s.diceHudScale);
               if (this.startCornerResize(mouseX, mouseY, s.diceHudX, s.diceHudY, diceW, diceH, HudMoveScreen.HudTarget.DICE, (double)s.diceHudScale)) {
                  return true;
               } else if (this.checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, diceW, diceH)) {
                  if (button != 1 && button != 2) {
                     this.startDragging(HudMoveScreen.HudTarget.DICE, (int)mouseX - s.diceHudX, (int)mouseY - s.diceHudY);
                     return true;
                  } else {
                     s.diceDisplayMode = "Current".equalsIgnoreCase(s.diceDisplayMode) ? "Lifetime" : "Current";
                     BomboConfig.save();
                     return true;
                  }
               } else {
                  int kuudraW = (int)(80.0F * s.kuudraBlindnessTimerScale);
                  int kuudraH = (int)(12.0F * s.kuudraBlindnessTimerScale);
                  if (this.startCornerResize(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, kuudraW, kuudraH, HudMoveScreen.HudTarget.KUUDRA, (double)s.kuudraBlindnessTimerScale)) {
                     return true;
                  } else if (this.checkHit(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, kuudraW, kuudraH)) {
                     this.startDragging(HudMoveScreen.HudTarget.KUUDRA, (int)mouseX - s.kuudraBlindnessTimerX, (int)mouseY - s.kuudraBlindnessTimerY);
                     return true;
                  } else {
                     int padW = (int)(120.0F * s.padTimersScale);
                     int padH = (int)(12.0F * s.padTimersScale);
                     if (this.startCornerResize(mouseX, mouseY, s.padTimersX, s.padTimersY, padW, padH, HudMoveScreen.HudTarget.PAD_TIMERS, (double)s.padTimersScale)) {
                        return true;
                     } else if (this.checkHit(mouseX, mouseY, s.padTimersX, s.padTimersY, padW, padH)) {
                        this.startDragging(HudMoveScreen.HudTarget.PAD_TIMERS, (int)mouseX - s.padTimersX, (int)mouseY - s.padTimersY);
                        return true;
                     } else {
                        int timerW = (int)((float)CustomTimerManager.getWidth() * s.customTimerHudScale);
                        int timerH = (int)((float)CustomTimerManager.getHeight() * s.customTimerHudScale);
                        if (this.startCornerResize(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH, HudMoveScreen.HudTarget.TIMERS, (double)s.customTimerHudScale)) {
                           return true;
                        } else if (this.checkHit(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH)) {
                           this.startDragging(HudMoveScreen.HudTarget.TIMERS, (int)mouseX - s.customTimerHudX, (int)mouseY - s.customTimerHudY);
                           return true;
                        } else {
                           int compW = (int)((float)ComposterHud.getWidth() * s.composterHudScale);
                           int compH = (int)((float)ComposterHud.getHeight() * s.composterHudScale);
                           if (this.startCornerResize(mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH, HudMoveScreen.HudTarget.COMPOSTER, (double)s.composterHudScale)) {
                              return true;
                           } else if (this.checkHit(mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH)) {
                              this.startDragging(HudMoveScreen.HudTarget.COMPOSTER, (int)mouseX - s.composterHudX, (int)mouseY - s.composterHudY);
                              return true;
                           } else {
                              int compTimerW = (int)(140.0F * s.composterTimerHudScale);
                              int compTimerH = (int)(12.0F * s.composterTimerHudScale);
                              if (this.startCornerResize(mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, compTimerW, compTimerH, HudMoveScreen.HudTarget.COMPOSTER_TIMER, (double)s.composterTimerHudScale)) {
                                 return true;
                              } else if (this.checkHit(mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, compTimerW, compTimerH)) {
                                 this.startDragging(HudMoveScreen.HudTarget.COMPOSTER_TIMER, (int)mouseX - s.composterTimerHudX, (int)mouseY - s.composterTimerHudY);
                                 return true;
                              } else if (this.checkHit(mouseX, mouseY, s.hoppityHudX, s.hoppityHudY, 90, 45)) {
                                 this.startDragging(HudMoveScreen.HudTarget.HOPPITY, (int)mouseX - s.hoppityHudX, (int)mouseY - s.hoppityHudY);
                                 return true;
                              } else {
                                 int croesusW = (int)(220.0F * s.autoCroesusHudScale);
                                 int croesusH = (int)(110.0F * s.autoCroesusHudScale);
                                 if (this.startCornerResize(mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, croesusW, croesusH, HudMoveScreen.HudTarget.AUTO_CROESUS, (double)s.autoCroesusHudScale)) {
                                    return true;
                                 } else if (this.checkHit(mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, croesusW, croesusH)) {
                                    this.startDragging(HudMoveScreen.HudTarget.AUTO_CROESUS, (int)mouseX - s.autoCroesusHudX, (int)mouseY - s.autoCroesusHudY);
                                    return true;
                                 } else {
                                    int alphaW = (int)((float)AlphaTrackerHud.getHudWidth() * s.alphaTrackerHudScale);
                                    int alphaH = (int)((float)AlphaTrackerHud.getHudHeight() * s.alphaTrackerHudScale);
                                    if (this.startCornerResize(mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, alphaW, alphaH, HudMoveScreen.HudTarget.ALPHA_TRACKER, (double)s.alphaTrackerHudScale)) {
                                       return true;
                                    } else if (this.checkHit(mouseX, mouseY, s.alphaTrackerHudX, s.alphaTrackerHudY, alphaW, alphaH)) {
                                       this.startDragging(HudMoveScreen.HudTarget.ALPHA_TRACKER, (int)mouseX - s.alphaTrackerHudX, (int)mouseY - s.alphaTrackerHudY);
                                       return true;
                                    } else {
                                       if (!s.showOnlyActiveHuds || s.tabWidgetHudEnabled) {
                                          int mainTabW = (int)((float)TabWidgetHud.getWidth() * s.tabWidgetHudScale);
                                          List<String> mainLines = TabWidgetHud.getMatchedWidgetLines(s.tabWidgetQuery != null && !s.tabWidgetQuery.isEmpty() ? s.tabWidgetQuery : "Area", true);
                                          int mainTabH = (int)((float)TabWidgetHud.getHeight(mainLines.size()) * s.tabWidgetHudScale);
                                          if (this.startCornerResize(mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, mainTabW, mainTabH, HudMoveScreen.HudTarget.TAB_WIDGET, (double)s.tabWidgetHudScale)) {
                                             return true;
                                          } else if (this.checkHit(mouseX, mouseY, s.tabWidgetHudX, s.tabWidgetHudY, mainTabW, mainTabH)) {
                                             this.startDragging(HudMoveScreen.HudTarget.TAB_WIDGET, (int)mouseX - s.tabWidgetHudX, (int)mouseY - s.tabWidgetHudY);
                                             return true;
                                          }
                                       }

                                       if (s.tabWidgets != null) {
                                          for(int i = 0; i < s.tabWidgets.size(); ++i) {
                                             BomboConfig.TabWidgetInfo widget = (BomboConfig.TabWidgetInfo)s.tabWidgets.get(i);
                                             List<String> lines = TabWidgetHud.getMatchedWidgetLines(widget.name, true);
                                             int widgetW = (int)((float)TabWidgetHud.getWidth() * widget.scale);
                                             int widgetH = (int)((float)TabWidgetHud.getHeight(lines.size()) * widget.scale);
                                             if (this.startCornerResize(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH, HudMoveScreen.HudTarget.TAB_WIDGET, (double)widget.scale)) {
                                                this.editingWidgetIdx = i;
                                                return true;
                                             }

                                             if (this.checkHit(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH)) {
                                                this.editingWidgetIdx = i;
                                                this.startDragging(HudMoveScreen.HudTarget.TAB_WIDGET, (int)mouseX - widget.x, (int)mouseY - widget.y);
                                                return true;
                                             }
                                          }
                                       }
                                    }

                                    if (s.itemListEnabled && s.itemListSeparateSearch) {
                                       int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
                                       int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
                                       int searchW = (int)((float)s.itemListSearchW * s.itemListSearchScale);
                                       int searchH = (int)(16.0F * s.itemListSearchScale);
                                       if (this.startCornerResize(mouseX, mouseY, searchX, searchY, searchW, searchH, HudMoveScreen.HudTarget.ITEM_LIST_SEARCH, (double)s.itemListSearchScale)) {
                                          return true;
                                       }

                                       if (this.checkHit(mouseX, mouseY, searchX, searchY, searchW, searchH)) {
                                          this.startDragging(HudMoveScreen.HudTarget.ITEM_LIST_SEARCH, (int)mouseX - searchX, (int)mouseY - searchY);
                                          return true;
                                       }
                                    }

                                    if (s.itemListEnabled && !s.itemListLocked) {
                                       int ilX = s.itemListX == -1 ? this.width - 150 : s.itemListX;
                                       int ilY = s.itemListY == -1 ? 20 : s.itemListY;
                                       if (this.startCornerResize(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH, HudMoveScreen.HudTarget.ITEM_LIST, (double)1.0F)) {
                                          return true;
                                       }

                                       if (this.checkHit(mouseX, mouseY, ilX, ilY, s.itemListW, s.itemListH)) {
                                          this.startDragging(HudMoveScreen.HudTarget.ITEM_LIST, (int)mouseX - ilX, (int)mouseY - ilY);
                                          return true;
                                       }
                                    }

                                    return super.mouseClicked(event, handled);
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
      if (BomboConfig.get().itemListEnabled && ItemListOverlay.mouseScrolled(mouseX, mouseY, vertical)) {
         return true;
      } else {
         BomboConfig.Settings s = BomboConfig.get();
         if (this.checkHit(mouseX, mouseY, s.diceHudX, s.diceHudY, (int)(260.0F * s.diceHudScale), (int)(52.0F * s.diceHudScale))) {
            s.diceHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.diceHudScale + vertical * 0.1));
            BomboConfig.save();
            return true;
         } else {
            int bakeryW = (int)((float)FeastBakeryHud.getHudWidth() * s.feastBakeryHudScale);
            int bakeryH = (int)((float)FeastBakeryHud.getHudHeight(3) * s.feastBakeryHudScale);
            if (this.checkHit(mouseX, mouseY, s.feastBakeryHudX, s.feastBakeryHudY, bakeryW, bakeryH)) {
               s.feastBakeryHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.feastBakeryHudScale + vertical * 0.1));
               BomboConfig.save();
               return true;
            } else {
               int rngW = (int)(185.0F * s.rngProfitHudScale);
               int rngH = (int)((float)ExperimentationTableHud.getHudHeight() * s.rngProfitHudScale);
               if (this.checkHit(mouseX, mouseY, s.rngProfitHudX, s.rngProfitHudY, rngW, rngH)) {
                  s.rngProfitHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.rngProfitHudScale + vertical * 0.1));
                  BomboConfig.save();
                  return true;
               } else if (this.checkHit(mouseX, mouseY, s.kuudraBlindnessTimerX, s.kuudraBlindnessTimerY, (int)(80.0F * s.kuudraBlindnessTimerScale), (int)(12.0F * s.kuudraBlindnessTimerScale))) {
                  s.kuudraBlindnessTimerScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.kuudraBlindnessTimerScale + vertical * 0.1));
                  BomboConfig.save();
                  return true;
               } else if (this.checkHit(mouseX, mouseY, s.padTimersX, s.padTimersY, (int)(120.0F * s.padTimersScale), (int)(12.0F * s.padTimersScale))) {
                  s.padTimersScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.padTimersScale + vertical * 0.1));
                  BomboConfig.save();
                  return true;
               } else {
                  int timerW = (int)((float)CustomTimerManager.getWidth() * s.customTimerHudScale);
                  int timerH = (int)((float)CustomTimerManager.getHeight() * s.customTimerHudScale);
                  if (this.checkHit(mouseX, mouseY, s.customTimerHudX, s.customTimerHudY, timerW, timerH)) {
                     s.customTimerHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.customTimerHudScale + vertical * 0.1));
                     BomboConfig.save();
                     return true;
                  } else {
                     int compW = (int)((float)ComposterHud.getWidth() * s.composterHudScale);
                     int compH = (int)((float)ComposterHud.getHeight() * s.composterHudScale);
                     if (this.checkHit(mouseX, mouseY, s.composterHudX, s.composterHudY, compW, compH)) {
                        s.composterHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.composterHudScale + vertical * 0.1));
                        BomboConfig.save();
                        return true;
                     } else if (this.checkHit(mouseX, mouseY, s.signCalculatorX >= 0 ? s.signCalculatorX : (this.width / 2 - 75), s.signCalculatorY, (int)(150.0F * s.signCalculatorScale), (int)(30.0F * s.signCalculatorScale))) {
         s.signCalculatorScale = (float)Math.max(0.5, Math.min(3.0, (double)s.signCalculatorScale + vertical * 0.1));
         BomboConfig.save();
         return true;
      }
      if (this.checkHit(mouseX, mouseY, s.chatSearchX >= 0 ? s.chatSearchX : 4, s.chatSearchY >= 0 ? s.chatSearchY : (this.height - 28), (int)(160.0F * s.chatSearchScale), (int)(14.0F * s.chatSearchScale))) {
         s.chatSearchScale = (float)Math.max(0.5, Math.min(3.0, (double)s.chatSearchScale + vertical * 0.1));
         BomboConfig.save();
         return true;
      }
      if (this.checkHit(mouseX, mouseY, s.autoCroesusHudX, s.autoCroesusHudY, (int)(220.0F * s.autoCroesusHudScale), (int)(110.0F * s.autoCroesusHudScale))) {
                        s.autoCroesusHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.autoCroesusHudScale + vertical * 0.1));
                        BomboConfig.save();
                        return true;
                     } else if (this.checkHit(mouseX, mouseY, s.fbWarnTimerX > 0 ? s.fbWarnTimerX : 10, s.fbWarnTimerY > 0 ? s.fbWarnTimerY : 120, (int)(65.0F * (s.fbWarnTimerScale > 0 ? s.fbWarnTimerScale : 1.0F)), (int)(12.0F * (s.fbWarnTimerScale > 0 ? s.fbWarnTimerScale : 1.0F)))) {
                        s.fbWarnTimerScale = (float)Math.max(0.5, Math.min(3.0, (double)(s.fbWarnTimerScale > 0 ? s.fbWarnTimerScale : 1.0F) + vertical * 0.1));
                        BomboConfig.save();
                        return true;
                     } else if (this.checkHit(mouseX, mouseY, s.dojoShootHudX >= 0 ? s.dojoShootHudX : (this.width / 2 - 27), s.dojoShootHudY >= 0 ? s.dojoShootHudY : (this.height / 2 + 18), (int)(56.0F * (s.dojoShootHudScale > 0 ? s.dojoShootHudScale : 1.0F)), (int)(16.0F * (s.dojoShootHudScale > 0 ? s.dojoShootHudScale : 1.0F)))) {
         s.dojoShootHudScale = (float)Math.max(0.5, Math.min(3.0, (double)(s.dojoShootHudScale > 0 ? s.dojoShootHudScale : 1.0F) + vertical * 0.1));
         BomboConfig.save();
         return true;
      } else if (this.checkHit(mouseX, mouseY, s.composterTimerHudX, s.composterTimerHudY, (int)(140.0F * s.composterTimerHudScale), (int)(12.0F * s.composterTimerHudScale))) {
                        s.composterTimerHudScale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)s.composterTimerHudScale + vertical * 0.1));
                        BomboConfig.save();
                        return true;
                     } else {
                        if (s.tabWidgets != null) {
                           for(BomboConfig.TabWidgetInfo widget : s.tabWidgets) {
                              List<String> lines = TabWidgetHud.getMatchedWidgetLines(widget.name, true);
                              int widgetW = (int)((float)TabWidgetHud.getWidth() * widget.scale);
                              int widgetH = (int)((float)TabWidgetHud.getHeight(lines.size()) * widget.scale);
                              if (this.checkHit(mouseX, mouseY, widget.x, widget.y, widgetW, widgetH)) {
                                 widget.scale = (float)Math.max((double)0.5F, Math.min((double)3.0F, (double)widget.scale + vertical * 0.1));
                                 BomboConfig.save();
                                 return true;
                              }
                           }
                        }

                        if (s.itemListEnabled && s.itemListSeparateSearch) {
                           int searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
                           int searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
                           if (this.checkHit(mouseX, mouseY, searchX, searchY, s.itemListSearchW, 16)) {
                              s.itemListSearchW = (int)Math.max((double)30.0F, (double)s.itemListSearchW + vertical * (double)10.0F);
                              BomboConfig.save();
                              return true;
                           }
                        }

                        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
                     }
                  }
               }
            }
         }
      }
   }

   private int getCornerHit(double mx, double my, int x, int y, int w, int h) {
      // Only bottom-right corner handles resize so clicking the body always drags!
      if (mx >= (double)(x + w - 8) && mx <= (double)(x + w + 4) && my >= (double)(y + h - 8) && my <= (double)(y + h + 4)) {
         return 3;
      }
      return -1;
   }

   private boolean startCornerResize(double mx, double my, int x, int y, int w, int h, HudTarget target, double scale) {
      int corner = this.getCornerHit(mx, my, x, y, w, h);
      if (corner != -1) {
         this.resizingTarget = target;
         this.resizeCorner = corner;
         this.resizeStartScale = scale;
         this.resizeStartW = w;
         this.resizeStartH = h;
         this.resizeStartX = x;
         this.resizeStartY = y;
         this.resizeStartMouseX = mx;
         this.resizeStartMouseY = my;
         return true;
      } else {
         return false;
      }
   }

   private boolean checkHit(double mx, double my, int x, int y, int w, int h) {
      return mx >= (double)x && mx <= (double)(x + w) && my >= (double)y && my <= (double)(y + h);
   }

   private void startDragging(HudTarget target, int ox, int oy) {
      this.draggingTarget = target;
      this.dragOffsetX = ox;
      this.dragOffsetY = oy;
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.resizingTarget != null) {
         this.resizingTarget = null;
         BomboConfig.save();
         return true;
      } else if (this.isResizingItemList) {
         this.isResizingItemList = false;
         BomboConfig.save();
         return true;
      } else {
         this.draggingTarget = null;
         BomboConfig.save();
         return super.mouseReleased(event);
      }
   }

   public void onClose() {
      BomboConfig.save();
      super.onClose();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static enum HudTarget {
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
      DOJO_SHOOT;

      // $FF: synthetic method
      private static HudTarget[] $values() {
         return new HudTarget[]{DICE, BAKERY, RNG, KUUDRA, PAD_TIMERS, TIMERS, COMPOSTER, COMPOSTER_TIMER, TAB_WIDGET, ITEM_LIST, ITEM_LIST_SEARCH, HOPPITY, ALPHA_TRACKER, AUTO_CROESUS};
      }
   }
}
