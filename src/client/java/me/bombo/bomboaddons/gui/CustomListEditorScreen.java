package me.bombo.bomboaddons.gui;

import java.util.List;
import me.bombo.bomboaddons.ClickLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class CustomListEditorScreen extends Screen {
   private final Screen parentScreen;
   private final String titleName;
   private final int categoryIndex;
   private int scrollOffset = 0;
   private boolean isEditingModalOpen = false;
   private int editingIndex = -1;
   private EditBox editBoxItemName;
   private EditBox editBoxGuiName;
   private String inputKeyName = "key_340";
   private boolean inputAuto = false;
   private Button btnAddTarget;
   private Button btnPaste;
   private Button btnBack;
   private Button btnSaveModal;
   private Button btnCancelModal;

   public CustomListEditorScreen(Screen parentScreen, String titleName, int categoryIndex) {
      super(Component.literal(titleName));
      this.parentScreen = parentScreen;
      this.titleName = titleName;
      this.categoryIndex = categoryIndex;
   }

   protected void init() {
      super.init();
      this.clearWidgets();
      int cardW = 460;
      int cardH = 290;
      int cardX = (this.width - cardW) / 2;
      int cardY = (this.height - cardH) / 2;
      int modalW = 340;
      int modalX = (this.width - modalW) / 2;
      int modalY = (this.height - 180) / 2;
      this.editBoxItemName = new EditBox(this.font, modalX + 100, modalY + 34, modalW - 120, 16, Component.literal("Item Name"));
      this.editBoxItemName.setMaxLength(1024);
      this.editBoxItemName.setVisible(false);
      this.addRenderableWidget(this.editBoxItemName);
      this.editBoxGuiName = new EditBox(this.font, modalX + 100, modalY + 64, modalW - 120, 16, Component.literal("GUI Name"));
      this.editBoxGuiName.setMaxLength(1024);
      this.editBoxGuiName.setVisible(false);
      this.addRenderableWidget(this.editBoxGuiName);
      int btnY = cardY + cardH - 26;
      this.btnAddTarget = Button.builder(Component.literal("Add new Target"), (btn) -> this.openModal(-1, "", "", "key_340", false)).bounds(cardX + 14, btnY, 110, 18).build();
      this.addRenderableWidget(this.btnAddTarget);
      this.btnPaste = Button.builder(Component.literal("Paste Target from Clipboard"), (btn) -> {
         String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
         if (clip != null && !clip.trim().isEmpty()) {
            this.openModal(-1, clip.trim(), "", "key_340", false);
         }

      }).bounds(cardX + 130, btnY, 175, 18).build();
      this.addRenderableWidget(this.btnPaste);
      this.btnBack = Button.builder(Component.literal("Back to MoulConfig"), (btn) -> Minecraft.getInstance().setScreen(this.parentScreen)).bounds(cardX + cardW - 144, btnY, 130, 18).build();
      this.addRenderableWidget(this.btnBack);
      int saveX = modalX + 50;
      int saveY = modalY + 140;
      this.btnSaveModal = Button.builder(Component.literal("Save"), (btn) -> this.saveModalData()).bounds(saveX, saveY, 100, 20).build();
      this.btnSaveModal.visible = false;
      this.addRenderableWidget(this.btnSaveModal);
      int cancelX = modalX + modalW - 150;
      this.btnCancelModal = Button.builder(Component.literal("Cancel"), (btn) -> this.closeModal()).bounds(cancelX, saveY, 100, 20).build();
      this.btnCancelModal.visible = false;
      this.addRenderableWidget(this.btnCancelModal);
      if (this.isEditingModalOpen) {
         this.setModalVisible(true);
      } else {
         this.setModalVisible(false);
      }

   }

   private void openModal(int index, String item, String gui, String key, boolean auto) {
      this.editingIndex = index;
      this.inputKeyName = key != null ? key : "key_340";
      this.inputAuto = auto;
      if (this.editBoxItemName != null) {
         this.editBoxItemName.setValue(item != null ? item : "");
      }

      if (this.editBoxGuiName != null) {
         this.editBoxGuiName.setValue(gui != null ? gui : "");
      }

      this.isEditingModalOpen = true;
      this.setModalVisible(true);
   }

   private void closeModal() {
      this.isEditingModalOpen = false;
      this.setModalVisible(false);
   }

   private void setModalVisible(boolean visible) {
      if (this.editBoxItemName != null) {
         this.editBoxItemName.setVisible(visible);
         this.editBoxItemName.setFocused(visible);
      }

      if (this.editBoxGuiName != null) {
         this.editBoxGuiName.setVisible(visible);
         this.editBoxGuiName.setFocused(false);
      }

      if (this.btnSaveModal != null) {
         this.btnSaveModal.visible = visible;
      }

      if (this.btnCancelModal != null) {
         this.btnCancelModal.visible = visible;
      }

      if (this.btnAddTarget != null) {
         this.btnAddTarget.visible = !visible;
      }

      if (this.btnPaste != null) {
         this.btnPaste.visible = !visible;
      }

      if (this.btnBack != null) {
         this.btnBack.visible = !visible;
      }

   }

   public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
      graphics.fill(0, 0, this.width, this.height, Integer.MIN_VALUE);
      int cardW = 460;
      int cardH = 290;
      int cardX = (this.width - cardW) / 2;
      int cardY = (this.height - cardH) / 2;
      graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, -15131870);
      graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY, -13223352);
      graphics.fill(cardX - 1, cardY + cardH, cardX + cardW + 1, cardY + cardH + 1, -13223352);
      graphics.fill(cardX - 1, cardY - 1, cardX, cardY + cardH + 1, -13223352);
      graphics.fill(cardX + cardW, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, -13223352);
      graphics.centeredText(this.font, "§f§l" + this.titleName, cardX + cardW / 2, cardY + 14, -1);
      int listX = cardX + 14;
      int listY = cardY + 38;
      int listW = cardW - 28;
      int listH = 206;
      if (this.categoryIndex == 6) {
         List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
         if (targets.isEmpty()) {
            graphics.centeredText(this.font, "§7No items configured. Click 'Add new Target' below.", listX + listW / 2, listY + listH / 2 - 4, -1996488705);
         } else {
            int rowY = listY + 4 - this.scrollOffset;

            for(int i = 0; i < targets.size(); ++i) {
               if (rowY >= listY && rowY + 24 <= listY + listH) {
                  ClickLogic.ClickTarget target = (ClickLogic.ClickTarget)targets.get(i);
                  String itemDisplay = target.item != null && !target.item.isEmpty() ? target.item : "Any Item";
                  graphics.text(this.font, "§f" + itemDisplay, listX + 8, rowY + 6, -1, false);
                  int rightControlsX = listX + listW - 165;
                  int toggleY = rowY + 4;
                  int toggleW = 34;
                  int toggleH = 14;
                  boolean enabled = target.auto;
                  graphics.fill(rightControlsX, toggleY, rightControlsX + toggleW, toggleY + toggleH, -15724009);
                  graphics.fill(rightControlsX - 1, toggleY - 1, rightControlsX + toggleW + 1, toggleY, -14013126);
                  graphics.fill(rightControlsX - 1, toggleY + toggleH, rightControlsX + toggleW + 1, toggleY + toggleH + 1, -14013126);
                  graphics.fill(rightControlsX - 1, toggleY - 1, rightControlsX, toggleY + toggleH + 1, -14013126);
                  graphics.fill(rightControlsX + toggleW, toggleY - 1, rightControlsX + toggleW + 1, toggleY + toggleH + 1, -14013126);
                  int thumbX = enabled ? rightControlsX + toggleW - 10 : rightControlsX + 2;
                  int thumbColor = enabled ? -11141291 : -43691;
                  graphics.fill(thumbX, toggleY + 2, thumbX + 8, toggleY + toggleH - 2, thumbColor);
                  int editX = rightControlsX + 44;
                  int editW = 48;
                  int editH = 16;
                  boolean editHover = mouseX >= editX && mouseX <= editX + editW && mouseY >= rowY + 3 && mouseY <= rowY + 3 + editH;
                  this.renderSkyHanniButton(graphics, "Edit", editX, rowY + 3, editW, editH, editHover);
                  int delX = editX + 54;
                  int delW = 58;
                  int delH = 16;
                  boolean delHover = mouseX >= delX && mouseX <= delX + delW && mouseY >= rowY + 3 && mouseY <= rowY + 3 + delH;
                  this.renderSkyHanniButton(graphics, "Delete", delX, rowY + 3, delW, delH, delHover);
               }

               rowY += 26;
            }
         }
      } else {
         graphics.centeredText(this.font, "§7No items configured. Click 'Add new Item' below.", listX + listW / 2, listY + listH / 2 - 4, -1996488705);
      }

      super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
      if (this.isEditingModalOpen) {
         this.renderEditModalFrame(graphics, mouseX, mouseY);
      }

   }

   private void renderSkyHanniButton(GuiGraphicsExtractor graphics, String label, int x, int y, int w, int h, boolean hover) {
      int bg = hover ? -12894385 : -14144714;
      graphics.fill(x, y, x + w, y + h, bg);
      graphics.fill(x - 1, y - 1, x + w + 1, y, -11907487);
      graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, -15197663);
      graphics.fill(x - 1, y - 1, x, y + h + 1, -11907487);
      graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, -15197663);
      graphics.centeredText(this.font, label, x + w / 2, y + (h - 8) / 2, hover ? -1 : -2236963);
   }

   private void renderEditModalFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
      graphics.fill(0, 0, this.width, this.height, -1610612736);
      int modalW = 340;
      int modalH = 180;
      int modalX = (this.width - modalW) / 2;
      int modalY = (this.height - modalH) / 2;
      graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, -15131870);
      graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY, -11907487);
      graphics.fill(modalX - 1, modalY + modalH, modalX + modalW + 1, modalY + modalH + 1, -11907487);
      graphics.fill(modalX - 1, modalY - 1, modalX, modalY + modalH + 1, -11907487);
      graphics.fill(modalX + modalW, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, -11907487);
      String title = this.editingIndex == -1 ? "Add New Target" : "Edit Target";
      graphics.centeredText(this.font, "§f§l" + title, modalX + modalW / 2, modalY + 12, -1);
      graphics.text(this.font, "§7Item Name:", modalX + 18, modalY + 38, -5592406, false);
      graphics.text(this.font, "§7GUI Name:", modalX + 18, modalY + 68, -5592406, false);
      graphics.text(this.font, "§7Keybind:", modalX + 18, modalY + 98, -5592406, false);
      graphics.text(this.font, ClickLogic.getKeyDisplayName(this.inputKeyName), modalX + 104, modalY + 98, -171, false);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
      double mouseX = event.x();
      double mouseY = event.y();
      int cardW = 460;
      int cardH = 290;
      int cardX = (this.width - cardW) / 2;
      int cardY = (this.height - cardH) / 2;
      int listX = cardX + 14;
      int listY = cardY + 38;
      int listW = cardW - 28;
      int listH = 206;
      if (!this.isEditingModalOpen && this.categoryIndex == 6) {
         List<ClickLogic.ClickTarget> targets = ClickLogic.getTargets();
         int rowY = listY + 4 - this.scrollOffset;

         for(int i = 0; i < targets.size(); ++i) {
            if (rowY >= listY && rowY + 24 <= listY + listH) {
               int rightControlsX = listX + listW - 165;
               if (mouseX >= (double)rightControlsX && mouseX <= (double)(rightControlsX + 34) && mouseY >= (double)(rowY + 4) && mouseY <= (double)(rowY + 18)) {
                  ClickLogic.ClickTarget t = (ClickLogic.ClickTarget)targets.get(i);
                  t.auto = !t.auto;
                  ClickLogic.saveTargets();
                  return true;
               }

               int editX = rightControlsX + 44;
               if (mouseX >= (double)editX && mouseX <= (double)(editX + 48) && mouseY >= (double)(rowY + 3) && mouseY <= (double)(rowY + 19)) {
                  ClickLogic.ClickTarget t = (ClickLogic.ClickTarget)targets.get(i);
                  this.openModal(i, t.item, t.gui, t.keyName, t.auto);
                  return true;
               }

               int delX = editX + 54;
               if (mouseX >= (double)delX && mouseX <= (double)(delX + 58) && mouseY >= (double)(rowY + 3) && mouseY <= (double)(rowY + 19)) {
                  ClickLogic.removeTarget(i);
                  return true;
               }
            }

            rowY += 26;
         }
      }

      return super.mouseClicked(event, handled);
   }

   private void saveModalData() {
      String item = this.editBoxItemName != null ? this.editBoxItemName.getValue() : "";
      String gui = this.editBoxGuiName != null ? this.editBoxGuiName.getValue() : "";
      if (this.categoryIndex == 6) {
         if (this.editingIndex == -1) {
            ClickLogic.setTarget(item, gui, this.inputKeyName, "click", this.inputAuto);
         } else {
            ClickLogic.updateTarget(this.editingIndex, item, gui, this.inputKeyName, "click", this.inputAuto);
         }
      }

      this.closeModal();
   }
}
