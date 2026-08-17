package me.bombo.bomboaddons.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.bombo.bomboaddons.BomboConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class IQStyleConfigScreen extends Screen {
   private final Screen parentScreen;
   private static final int SIDEBAR_WIDTH = 140;
   private static final int HEADER_HEIGHT = 45;
   private static final int ITEM_HEIGHT = 28;
   private static final int PADDING = 12;
   private final List<String> categories = List.of("General", "HUDs", "Experiments", "Garden", "Hotkeys", "Profiles", "Clicker", "Highlights", "Wardrobe", "Anvil", "Debug", "Kuudra", "Pets", "Keybinds");
   private int selectedCategory = 0;
   private float scrollAmount = 0.0F;
   private final List<EditBox> activeEditBoxes = new ArrayList();

   public IQStyleConfigScreen(Screen parentScreen) {
      super(Component.literal("BomboAddons IQ Config"));
      this.parentScreen = parentScreen;
   }

   protected void init() {
      super.init();
      this.activeEditBoxes.clear();
      this.clearWidgets();
      int winMarginX = Math.max(20, (this.width - 720) / 2);
      int winMarginY = Math.max(15, (this.height - 480) / 2);
      int winX = winMarginX;
      int winW = this.width - winMarginX * 2;
      int var10000 = this.height - winMarginY * 2;
      int categoryY = winMarginY + 45 + 12;

      for(int i = 0; i < this.categories.size(); ++i) {
         final int catIdx = i;
         String catName = (String)this.categories.get(i);
         boolean isSelected = i == this.selectedCategory;
         String label = (isSelected ? "§d> " : "§7  ") + catName;
         this.addRenderableWidget(Button.builder(Component.literal(label), (btn) -> {
            this.selectedCategory = catIdx;
            this.scrollAmount = 0.0F;
            this.init();
         }).bounds(winX + 12, categoryY, 116, 22).build());
         categoryY += 25;
      }

      int contentX = winX + 140 + 12;
      int contentW = winW - 140 - 24;
      int currentY = winMarginY + 45 + 12 + 24 - (int)this.scrollAmount;
      BomboConfig.Settings s = BomboConfig.get();
      switch (this.selectedCategory) {
         case 0:
            currentY = this.addToggleOption("Clear Water & Lava Vision", s.clearWaterAndLava, (val) -> s.clearWaterAndLava = val, contentX, contentW, currentY);
            currentY = this.addToggleOption("Sign Calculator", s.signCalculator, (val) -> s.signCalculator = val, contentX, contentW, currentY);
            currentY = this.addToggleOption("SBE Commands", s.sbeCommands, (val) -> s.sbeCommands = val, contentX, contentW, currentY);
            currentY = this.addToggleOption("Copy Chat", s.copyChat, (val) -> s.copyChat = val, contentX, contentW, currentY);
            currentY = this.addToggleOption("Left Click Etherwarp", s.leftClickEtherwarp, (val) -> s.leftClickEtherwarp = val, contentX, contentW, currentY);
            currentY = this.addToggleOption("Sphinx Macro", s.sphinxMacro, (val) -> s.sphinxMacro = val, contentX, contentW, currentY);
            this.addToggleOption("Daily Reward Helper", s.dailyRewardHelper, (val) -> s.dailyRewardHelper = val, contentX, contentW, currentY);
         default:
      }
   }

   private int addToggleOption(String title, boolean currentValue, Consumer<Boolean> onChange, int x, int w, int y) {
      String statusLabel = currentValue ? "§aENABLED" : "§cDISABLED";
      this.addRenderableWidget(Button.builder(Component.literal(title + ": " + statusLabel), (btn) -> {
         onChange.accept(!currentValue);
         BomboConfig.save();
         this.init();
      }).bounds(x, y, w, 22).build());
      return y + 28;
   }

   public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
      try {
         g.fill(0, 0, this.width, this.height, -586610161);
         int winMarginX = Math.max(20, (this.width - 720) / 2);
         int winMarginY = Math.max(15, (this.height - 480) / 2);
         int winW = this.width - winMarginX * 2;
         int winH = this.height - winMarginY * 2;
         g.fill(winMarginX, winMarginY, winMarginX + winW, winMarginY + winH, -15460830);
         g.fill(winMarginX - 1, winMarginY - 1, winMarginX + winW + 1, winMarginY, 1722417322);
         g.fill(winMarginX - 1, winMarginY + winH, winMarginX + winW + 1, winMarginY + winH + 1, 1722417322);
         g.fill(winMarginX - 1, winMarginY, winMarginX, winMarginY + winH, 1722417322);
         g.fill(winMarginX + winW, winMarginY, winMarginX + winW + 1, winMarginY + winH, 1722417322);
         g.fillGradient(winMarginX, winMarginY, winMarginX + 140, winMarginY + winH, -15197654, -15592673);
         g.fill(winMarginX + 140 - 1, winMarginY, winMarginX + 140, winMarginY + winH, 866779306);
         g.text(this.font, "§7BomboAddons Config", winMarginX + 12, winMarginY + 14, -1, true);
         g.text(this.font, "§7CATEGORIES", winMarginX + 12, winMarginY + 45 - 12, -7829368, false);
         int contentX = winMarginX + 140 + 12;
         int categoryTitleY = winMarginY + 45 + 12;
         g.text(this.font, "§f§l" + ((String)this.categories.get(this.selectedCategory)).toUpperCase(), contentX, categoryTitleY, -1, true);
         super.extractRenderState(g, mouseX, mouseY, partialTick);
      } catch (Throwable var13) {
      }

   }

   public void onClose() {
      BomboConfig.save();
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parentScreen);
      }

   }
}
