package me.bombo.bomboaddons;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import me.bombo.bomboaddons.BomboConfig.CustomItemOverride;

public class ItemCustomizeScreen extends Screen {
    private final Screen parent;
    private final ItemStack heldStack;
    private final String itemKey;
    private final String originalDisplayName;
    private final String initialMaterial;
    private final String initialName;

    private EditBox materialBox;
    private EditBox nameBox;

    public ItemCustomizeScreen(Screen parent) {
        super(Component.literal("Customize Item"));
        this.parent = parent;
        
        Minecraft mc = Minecraft.getInstance();
        this.heldStack = mc.player != null ? mc.player.getMainHandItem() : ItemStack.EMPTY;
        
        String skyblockId = SkyblockUtils.getInternalIdRaw(this.heldStack);
        if (skyblockId != null && !skyblockId.isEmpty()) {
            this.itemKey = skyblockId;
        } else if (!this.heldStack.isEmpty()) {
            this.itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.heldStack.getItem()).toString();
        } else {
            this.itemKey = "";
        }
        
        CustomItemOverride tempOverride = BomboConfig.get().customItemOverrides.get(this.itemKey);
        
        this.originalDisplayName = this.heldStack.isEmpty() ? "None" : this.heldStack.getHoverName().getString();
        
        // Restore config overrides
        if (tempOverride != null) {
            BomboConfig.get().customItemOverrides.put(this.itemKey, tempOverride);
        }

        
        if (tempOverride != null) {
            this.initialMaterial = tempOverride.material != null ? tempOverride.material : "";
            this.initialName = tempOverride.name != null ? tempOverride.name : "";
        } else {
            this.initialMaterial = "";
            this.initialName = "";
        }
    }

    @Override
    protected void init() {
        int w = 320;
        int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        String currentMaterial = this.materialBox != null ? this.materialBox.getValue() : this.initialMaterial;
        String currentName = this.nameBox != null ? this.nameBox.getValue() : this.initialName;

        this.materialBox = new EditBox(this.font, x + 100, y + 60, 200, 16, Component.literal("Material"));
        this.materialBox.setMaxLength(64);
        this.materialBox.setValue(currentMaterial);
        this.materialBox.setBordered(false);
        this.addRenderableWidget(this.materialBox);

        this.nameBox = new EditBox(this.font, x + 100, y + 90, 200, 16, Component.literal("Display Name"));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(currentName);
        this.nameBox.setBordered(false);
        this.addRenderableWidget(this.nameBox);

        // Buttons: Save, Reset, Cancel
        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> {
            String mat = this.materialBox.getValue().trim().toUpperCase().replace(' ', '_');
            String nm = this.nameBox.getValue().trim();
            
            if (mat.isEmpty() && nm.isEmpty()) {
                BomboConfig.get().customItemOverrides.remove(this.itemKey);
            } else {
                BomboConfig.get().customItemOverrides.put(this.itemKey, new CustomItemOverride(mat, nm));
            }
            BomboConfig.save();
            
            if (Minecraft.getInstance().player != null) {
                Bomboaddons.sendMessage("§8[§bBomboAddons§8] §aSaved custom item override for " + this.itemKey);
            }
            this.onClose();
        }).bounds(x + 20, y + 140, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), btn -> {
            BomboConfig.get().customItemOverrides.remove(this.itemKey);
            BomboConfig.save();
            
            if (Minecraft.getInstance().player != null) {
                Bomboaddons.sendMessage("§8[§bBomboAddons§8] §cReset custom item override for " + this.itemKey);
            }
            this.onClose();
        }).bounds(x + 120, y + 140, 80, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            this.onClose();
        }).bounds(x + 220, y + 140, 80, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, 0xD0101010);

        int w = 320;
        int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        g.fill(x, y, x + w, y + h, 0xFF181818);
        g.outline(x, y, w, h, 0xFF555555);

        g.centeredText(this.font, "§6§lCustomize Item", x + w / 2, y + 10, 0xFFFFFFFF);
        g.text(this.font, "§7Original: §f" + this.originalDisplayName, x + 20, y + 30, 0xFFFFFFFF, true);
        g.text(this.font, "§7Key: §e" + this.itemKey, x + 20, y + 42, 0xFFFFFFFF, true);

        g.text(this.font, "§fMaterial:", x + 20, y + 64, 0xFFFFFFFF, true);
        g.text(this.font, "§fDisplay Name:", x + 20, y + 94, 0xFFFFFFFF, true);

        String rawName = this.nameBox != null ? this.nameBox.getValue().trim() : "";
        String previewName = rawName.isEmpty() ? "§7(No custom name)" : rawName.replace('&', '§');
        g.text(this.font, "§7Preview: §r" + previewName, x + 20, y + 115, 0xFFFFFFFF, true);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
