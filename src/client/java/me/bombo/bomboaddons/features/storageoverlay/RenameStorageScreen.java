package me.bombo.bomboaddons.features.storageoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class RenameStorageScreen extends Screen {

    private final Screen parent;
    private final int storageIndex;
    private final String currentName;
    private EditBox nameBox;

    public RenameStorageScreen(Screen parent, int storageIndex, String currentName) {
        super(Component.literal("Rename Storage"));
        this.parent = parent;
        this.storageIndex = storageIndex;
        this.currentName = currentName != null ? currentName : "";
    }

    @Override
    protected void init() {
        super.init();
        int boxW = 220;
        int boxH = 20;
        int x = (this.width - boxW) / 2;
        int y = (this.height - boxH) / 2 - 20;

        nameBox = new EditBox(this.font, x, y, boxW, boxH, Component.literal("Custom Name"));
        nameBox.setValue(currentName);
        nameBox.setMaxLength(32);
        this.addRenderableWidget(nameBox);
        this.setInitialFocus(nameBox);

        int btnW = 68;
        int btnH = 20;
        int btnY = y + 30;

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> saveAndClose())
                .bounds(x, btnY, btnW, btnH)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            BackpackPreview.setCustomStorageName(storageIndex, null);
            Minecraft.getInstance().setScreenAndShow(parent);
        }).bounds(x + 76, btnY, btnW, btnH).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
            Minecraft.getInstance().setScreenAndShow(parent);
        }).bounds(x + 152, btnY, btnW, btnH).build());
    }

    private void saveAndClose() {
        String val = nameBox != null ? nameBox.getValue().trim() : "";
        BackpackPreview.setCustomStorageName(storageIndex, val);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) { // ENTER
            saveAndClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.fill(0, 0, this.width, this.height, 0x99000000);
        String title = "§eRename Storage: §f" + BackpackPreview.getStorageName(storageIndex);
        graphics.text(this.font, title, (this.width - this.font.width(title)) / 2, (this.height / 2) - 55, 0xFFFFFFFF, true);
        String tip = "§7Give this storage a custom label to organize your items.";
        graphics.text(this.font, tip, (this.width - this.font.width(tip)) / 2, (this.height / 2) - 40, 0xFFAAAAAA, true);
    }
}
