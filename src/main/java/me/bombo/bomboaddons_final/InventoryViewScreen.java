package me.bombo.bomboaddons_final;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.TagParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;

public class InventoryViewScreen extends AbstractContainerScreen<ChestMenu> {
    private static final Identifier CONTAINER_BACKGROUND = Identifier
            .withDefaultNamespace("textures/gui/container/generic_54.png");
    private final int rows;

    public InventoryViewScreen(InventorySnapshot snapshot) {
        super(createMenu(snapshot), Minecraft.getInstance().player.getInventory(),
                Component.literal(snapshot.guiName + " (" + snapshot.timestamp + ")"));
        this.rows = (this.menu.getContainer().getContainerSize() / 9);
        this.imageWidth = 176;
        this.imageHeight = 114 + this.rows * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        if (this.minecraft.player != null) {
            this.minecraft.player.containerMenu = this.menu;
        }
    }

    private static ChestMenu createMenu(InventorySnapshot snapshot) {
        int maxIndex = -1;
        for (String itemStr : snapshot.itemData) {
            String[] parts = itemStr.split("\\|", 2);
            if (parts.length == 2) {
                try {
                    int idx = Integer.parseInt(parts[0]);
                    if (idx > maxIndex)
                        maxIndex = idx;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        int size = 54;
        if (maxIndex != -1) {
            if (maxIndex < 9)
                size = 9;
            else if (maxIndex < 18)
                size = 18;
            else if (maxIndex < 27)
                size = 27;
            else if (maxIndex < 36)
                size = 36;
            else if (maxIndex < 45)
                size = 45;
            else
                size = 54;
        }

        SimpleContainer container = new SimpleContainer(size);
        Minecraft mc = Minecraft.getInstance();
        for (String itemStr : snapshot.itemData) {
            String[] parts = itemStr.split("\\|", 2);
            if (parts.length == 2) {
                try {
                    int idx = Integer.parseInt(parts[0]);
                    if (idx < size) {
                        CompoundTag tag = TagParser.parseCompoundFully(parts[1]);
                        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
                        ItemStack stack = ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
                        container.setItem(idx, stack);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        MenuType<ChestMenu> type = MenuType.GENERIC_9x6;
        int rows = 6;
        if (size == 9) {
            type = MenuType.GENERIC_9x1;
            rows = 1;
        } else if (size == 18) {
            type = MenuType.GENERIC_9x2;
            rows = 2;
        } else if (size == 27) {
            type = MenuType.GENERIC_9x3;
            rows = 3;
        } else if (size == 36) {
            type = MenuType.GENERIC_9x4;
            rows = 4;
        } else if (size == 45) {
            type = MenuType.GENERIC_9x5;
            rows = 5;
        }

        return new ChestMenu(type, 0, mc.player.getInventory(), container, rows);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float delta, int mouseX, int mouseY) {
        // signature: (Identifier, int x, int y, int width, int height, float u,
        // float v, float uWidth, float vHeight)
        // Note: AbstractContainerScreen translates the GuiGraphics to leftPos, topPos
        // before calling renderBg
        guiGraphics.blit(CONTAINER_BACKGROUND, 0, 0, this.imageWidth, this.rows * 18 + 17, 0f, 0f,
                (float) this.imageWidth, (float) (this.rows * 18 + 17));
        guiGraphics.blit(CONTAINER_BACKGROUND, 0, this.rows * 18 + 17, this.imageWidth, 96, 0f, 126f,
                (float) this.imageWidth, 96f);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
