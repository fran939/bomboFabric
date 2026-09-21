package me.bombo.bomboaddons.features.buttons;

import net.minecraft.world.item.ItemStack;

public class InventoryButton {
    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, PLAYER_INV_LEFT, PLAYER_INV_RIGHT
    }

    public enum BackgroundStyle {
        MODERN, VANILLA, SLOT, CUSTOM, INVISIBLE
    }

    public enum RightClickAction {
        NONE, RUN_COMMAND, EDIT_BUTTON
    }

    public enum MiddleClickAction {
        NONE, RUN_COMMAND
    }

    public String id;
    public String name = "";
    public String command = "";
    public RightClickAction rightClickAction = RightClickAction.NONE;
    public String rightClickCommand = "";
    public MiddleClickAction middleClickAction = MiddleClickAction.NONE;
    public String middleClickCommand = "";
    public String clickSound = "";
    public String icon = "";
    public Anchor anchor = Anchor.PLAYER_INV_RIGHT;
    public int offsetX = 4;
    public int offsetY = 0;
    public int size = 18;
    public boolean enabled = true;
    public boolean onlyInInventory = false;
    public String profileFilter = "ALL";

    public BackgroundStyle backgroundStyle = BackgroundStyle.MODERN;
    public int customBgColor = 0xAA0F172A;
    public int customHoverBgColor = 0xDD1E293B;
    public boolean showBorder = true;
    public int customBorderColor = 0x4464748B;
    public int customHoverBorderColor = 0xFF38BDF8;

    public transient ItemStack cachedStack = ItemStack.EMPTY;

    public InventoryButton() {
        this.id = java.util.UUID.randomUUID().toString();
    }

    public InventoryButton(String name, String command, String icon, Anchor anchor, int offsetX, int offsetY, int size, boolean enabled, boolean onlyInInventory) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.command = command;
        this.icon = icon;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.size = size;
        this.enabled = enabled;
        this.onlyInInventory = onlyInInventory;
        this.profileFilter = "ALL";
    }

    public InventoryButton(String name, String command, String icon, Anchor anchor, int offsetX, int offsetY, int size, boolean enabled, boolean onlyInInventory, String profileFilter) {
        this.id = java.util.UUID.randomUUID().toString();
        this.name = name;
        this.command = command;
        this.icon = icon;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.size = size;
        this.enabled = enabled;
        this.onlyInInventory = onlyInInventory;
        this.profileFilter = profileFilter != null ? profileFilter : "ALL";
    }

    public InventoryButton copy() {
        InventoryButton b = new InventoryButton(this.name, this.command, this.icon, this.anchor, this.offsetX, this.offsetY, this.size, this.enabled, this.onlyInInventory, this.profileFilter);
        b.id = java.util.UUID.randomUUID().toString();
        b.rightClickAction = this.rightClickAction;
        b.rightClickCommand = this.rightClickCommand;
        b.cachedStack = this.cachedStack;
        b.backgroundStyle = this.backgroundStyle;
        b.customBgColor = this.customBgColor;
        b.customHoverBgColor = this.customHoverBgColor;
        b.showBorder = this.showBorder;
        b.customBorderColor = this.customBorderColor;
        b.customHoverBorderColor = this.customHoverBorderColor;
        return b;
    }

    public int getX(int leftPos, int imageWidth) {
        switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT, PLAYER_INV_LEFT -> {
                return leftPos + offsetX;
            }
            case TOP_RIGHT, BOTTOM_RIGHT, PLAYER_INV_RIGHT -> {
                return leftPos + imageWidth + offsetX;
            }
            default -> {
                return leftPos + offsetX;
            }
        }
    }

    public int getY(int topPos, int imageHeight) {
        switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> {
                return topPos + offsetY;
            }
            case BOTTOM_LEFT, BOTTOM_RIGHT -> {
                return topPos + imageHeight + offsetY;
            }
            case PLAYER_INV_LEFT, PLAYER_INV_RIGHT -> {
                return topPos + imageHeight - 96 + offsetY;
            }
            default -> {
                return topPos + offsetY;
            }
        }
    }
}
