package me.bombo.bomboaddons.features.buttons;

import me.bombo.bomboaddons.features.TotemAnimationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class InventoryButtonMoveScreen extends Screen {
    private final Screen parent;
    private InventoryButton draggingButton = null;
    private int dragMouseOffsetX = 0;
    private int dragMouseOffsetY = 0;

    private int previewMode = 0; // 0 = 3-row chest (166h), 1 = 6-row chest (222h), 2 = Inventory (166h)
    private static final String[] PREVIEW_NAMES = new String[]{"3-Row Chest", "6-Row Chest", "Inventory"};

    public InventoryButtonMoveScreen(Screen parent) {
        super(Component.literal("Button Position Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        InventoryButtonManager.init();

        int topY = 12;
        int btnH = 20;

        // Mode switch
        this.addRenderableWidget(Button.builder(Component.literal("Preview: " + PREVIEW_NAMES[previewMode]), btn -> {
            previewMode = (previewMode + 1) % 3;
            btn.setMessage(Component.literal("Preview: " + PREVIEW_NAMES[previewMode]));
        }).bounds(12, topY, 130, btnH).build());

        // Presets
        this.addRenderableWidget(Button.builder(Component.literal("Preset: Right Column"), btn -> applyPreset(0)).bounds(148, topY, 125, btnH).build());
        this.addRenderableWidget(Button.builder(Component.literal("Preset: Left Column"), btn -> applyPreset(1)).bounds(279, topY, 125, btnH).build());
        this.addRenderableWidget(Button.builder(Component.literal("Preset: Top Bar"), btn -> applyPreset(2)).bounds(410, topY, 110, btnH).build());
        this.addRenderableWidget(Button.builder(Component.literal("Preset: Bottom Bar"), btn -> applyPreset(3)).bounds(526, topY, 115, btnH).build());

        // Save & Close
        this.addRenderableWidget(Button.builder(Component.literal("§aSave & Close"), btn -> {
            InventoryButtonManager.save();
            Minecraft.getInstance().setScreenAndShow(this.parent);
        }).bounds(this.width - 110, topY, 98, btnH).build());
    }

    private void applyPreset(int preset) {
        List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
        int idx = 0;
        for (InventoryButton b : list) {
            if (!b.enabled) continue;
            switch (preset) {
                case 0 -> { // Right Column (Player Inv relative)
                    b.anchor = InventoryButton.Anchor.PLAYER_INV_RIGHT;
                    b.offsetX = 4;
                    b.offsetY = -40 + idx * (b.size + 4);
                }
                case 1 -> { // Left Column (Player Inv relative)
                    b.anchor = InventoryButton.Anchor.PLAYER_INV_LEFT;
                    b.offsetX = -b.size - 4;
                    b.offsetY = -40 + idx * (b.size + 4);
                }
                case 2 -> { // Top Bar
                    b.anchor = InventoryButton.Anchor.TOP_LEFT;
                    b.offsetX = 4 + idx * (b.size + 4);
                    b.offsetY = -b.size - 4;
                }
                case 3 -> { // Bottom Bar
                    b.anchor = InventoryButton.Anchor.BOTTOM_LEFT;
                    b.offsetX = 4 + idx * (b.size + 4);
                    b.offsetY = 4;
                }
            }
            idx++;
        }
        InventoryButtonManager.save();
    }

    private int getContainerWidth() {
        return 176;
    }

    private int getContainerHeight() {
        return (previewMode == 1) ? 222 : 166;
    }

    private int getContainerLeft() {
        return (this.width - getContainerWidth()) / 2;
    }

    private int getContainerTop() {
        return (this.height - getContainerHeight()) / 2 + 10;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // Darkened background
        g.fill(0, 0, this.width, this.height, 0xDD090D16);

        Font font = this.font;
        int cX = getContainerLeft();
        int cY = getContainerTop();
        int cW = getContainerWidth();
        int cH = getContainerHeight();

        // Render preview container mockup
        g.fill(cX, cY, cX + cW, cY + cH, 0xFF1E293B);
        g.outline(cX, cY, cW, cH, 0xFF475569);
        g.text(font, "§7Container Preview (" + PREVIEW_NAMES[previewMode] + ")", cX + 8, cY + 6, 0xFF94A3B8, false);

        // Render mockup player inventory boundary at bottom
        int invY = cY + cH - 96;
        g.fill(cX + 4, invY, cX + cW - 4, cY + cH - 4, 0x330F172A);
        g.outline(cX + 4, invY, cW - 8, 92, 0x6638BDF8);
        g.text(font, "§8Player Inventory Area", cX + 8, invY + 4, 0xFF64748B, false);

        // Helper instruction banner
        g.text(font, "§eClick & Drag any button to reposition it. Changes apply immediately!", this.width / 2 - 160, this.height - 24, 0xFFCBD5E1, true);

        // Render buttons
        List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
        String activeProf = InventoryButtonManager.getActiveProfile();

        for (InventoryButton b : list) {
            if (!b.enabled) continue;
            if (b.profileFilter != null && !b.profileFilter.equalsIgnoreCase("ALL") && !b.profileFilter.equalsIgnoreCase(activeProf)) continue;

            int bx = b.getX(cX, cW);
            int by = b.getY(cY, cH);

            boolean isHover = (mouseX >= bx && mouseX <= bx + b.size && mouseY >= by && mouseY <= by + b.size);
            boolean isDrag = (draggingButton == b);

            int bg = isDrag ? 0xFF0284C7 : (isHover ? 0xDD38BDF8 : 0xDD1E293B);
            int border = isDrag ? 0xFFFFFFFF : (isHover ? 0xFFBAE6FD : 0xFF64748B);

            g.fill(bx, by, bx + b.size, by + b.size, bg);
            g.outline(bx, by, b.size, b.size, border);

            // Icon
            if (b.cachedStack == null || b.cachedStack.isEmpty()) {
                b.cachedStack = TotemAnimationManager.resolveItemStack(b.icon);
            }
            if (b.cachedStack != null && !b.cachedStack.isEmpty()) {
                g.item(b.cachedStack, bx + (b.size - 16) / 2, by + (b.size - 16) / 2);
            }

            // Small badge with anchor indicator when hovered or dragged
            if (isHover || isDrag) {
                String badge = "§b" + (b.name != null && !b.name.isEmpty() ? b.name : "Button") + " §7(" + b.anchor.name() + " X:" + b.offsetX + " Y:" + b.offsetY + ")";
                g.text(font, badge, bx, by - 10, 0xFFFFFFFF, true);
            }
        }

        // Draw Snapping Guidelines if active
        if (draggingButton != null) {
            if (snappedLineX != -1) {
                g.fill(snappedLineX, 0, snappedLineX + 1, this.height, 0xCC38BDF8);
            }
            if (snappedLineY != -1) {
                g.fill(0, snappedLineY, this.width, snappedLineY + 1, 0xCC38BDF8);
            }
        }

        // Instruction status
        boolean ctrlDown = Minecraft.getInstance() != null && Minecraft.getInstance().hasControlDown();
        String snapNote = (ctrlDown ? "§e[Snapping Disabled (Ctrl)] " : "§7[Snap Active - Hold §bCtrl§7] ") + "§8| §d[Scroll Wheel to Resize]";
        g.text(font, snapNote, this.width / 2 - 140, this.height - 38, 0xFF94A3B8, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0) {
            int cX = getContainerLeft();
            int cY = getContainerTop();
            int cW = getContainerWidth();
            int cH = getContainerHeight();

            List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
            String activeProf = InventoryButtonManager.getActiveProfile();

            for (int i = list.size() - 1; i >= 0; i--) {
                InventoryButton b = list.get(i);
                if (!b.enabled) continue;
                if (b.profileFilter != null && !b.profileFilter.equalsIgnoreCase("ALL") && !b.profileFilter.equalsIgnoreCase(activeProf)) continue;

                int bx = b.getX(cX, cW);
                int by = b.getY(cY, cH);
                if (event.x() >= bx && event.x() <= bx + b.size && event.y() >= by && event.y() <= by + b.size) {
                    draggingButton = b;
                    dragMouseOffsetX = (int) event.x() - bx;
                    dragMouseOffsetY = (int) event.y() - by;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, handled);
    }

    private int snappedLineX = -1;
    private int snappedLineY = -1;

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingButton != null) {
            int cX = getContainerLeft();
            int cY = getContainerTop();
            int cW = getContainerWidth();
            int cH = getContainerHeight();

            int targetX = (int) event.x() - dragMouseOffsetX;
            int targetY = (int) event.y() - dragMouseOffsetY;
            int bSize = draggingButton.size;

            snappedLineX = -1;
            snappedLineY = -1;

            boolean ctrlDown = Minecraft.getInstance() != null && Minecraft.getInstance().hasControlDown();

            if (!ctrlDown) {
                int snapDist = 6;
                int invY = cY + cH - 96;

                // 1. Snap X to container bounds
                if (Math.abs(targetX - (cX - bSize - 4)) <= snapDist) {
                    targetX = cX - bSize - 4;
                    snappedLineX = cX;
                } else if (Math.abs(targetX - (cX + 4)) <= snapDist) {
                    targetX = cX + 4;
                    snappedLineX = cX + 4;
                } else if (Math.abs(targetX - (cX + cW + 4)) <= snapDist) {
                    targetX = cX + cW + 4;
                    snappedLineX = cX + cW;
                } else if (Math.abs(targetX - (cX + cW - bSize - 4)) <= snapDist) {
                    targetX = cX + cW - bSize - 4;
                    snappedLineX = cX + cW - bSize - 4;
                } else if (Math.abs((targetX + bSize / 2) - (cX + cW / 2)) <= snapDist) {
                    targetX = (cX + cW / 2) - bSize / 2;
                    snappedLineX = cX + cW / 2;
                }

                // 2. Snap Y to container bounds
                if (Math.abs(targetY - (cY - bSize - 4)) <= snapDist) {
                    targetY = cY - bSize - 4;
                    snappedLineY = cY;
                } else if (Math.abs(targetY - (cY + 4)) <= snapDist) {
                    targetY = cY + 4;
                    snappedLineY = cY + 4;
                } else if (Math.abs(targetY - (cY + cH + 4)) <= snapDist) {
                    targetY = cY + cH + 4;
                    snappedLineY = cY + cH;
                } else if (Math.abs(targetY - (invY + 4)) <= snapDist) {
                    targetY = invY + 4;
                    snappedLineY = invY;
                }

                // 3. Snap to other buttons
                List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
                for (InventoryButton other : list) {
                    if (other == draggingButton || !other.enabled) continue;
                    int ox = other.getX(cX, cW);
                    int oy = other.getY(cY, cH);

                    // Align X
                    if (Math.abs(targetX - ox) <= snapDist) {
                        targetX = ox;
                        snappedLineX = ox;
                    } else if (Math.abs((targetX + bSize) - (ox + other.size)) <= snapDist) {
                        targetX = ox + other.size - bSize;
                        snappedLineX = ox + other.size;
                    } else if (Math.abs(targetX - (ox + other.size + 4)) <= snapDist) {
                        targetX = ox + other.size + 4;
                        snappedLineX = ox + other.size + 4;
                    } else if (Math.abs((targetX + bSize) - (ox - 4)) <= snapDist) {
                        targetX = ox - bSize - 4;
                        snappedLineX = ox;
                    }

                    // Align Y
                    if (Math.abs(targetY - oy) <= snapDist) {
                        targetY = oy;
                        snappedLineY = oy;
                    } else if (Math.abs((targetY + bSize) - (oy + other.size)) <= snapDist) {
                        targetY = oy + other.size - bSize;
                        snappedLineY = oy + other.size;
                    } else if (Math.abs(targetY - (oy + other.size + 4)) <= snapDist) {
                        targetY = oy + other.size + 4;
                        snappedLineY = oy + other.size + 4;
                    } else if (Math.abs((targetY + bSize) - (oy - 4)) <= snapDist) {
                        targetY = oy - bSize - 4;
                        snappedLineY = oy;
                    }
                }
            }

            // Compute anchor based on position
            boolean isRight = (targetX > cX + cW / 2);
            int invY = cY + cH - 96;
            boolean isPlayerInvArea = (targetY >= invY - 20);

            if (isPlayerInvArea) {
                draggingButton.anchor = isRight ? InventoryButton.Anchor.PLAYER_INV_RIGHT : InventoryButton.Anchor.PLAYER_INV_LEFT;
                draggingButton.offsetX = isRight ? (targetX - (cX + cW)) : (targetX - cX);
                draggingButton.offsetY = targetY - invY;
            } else {
                draggingButton.anchor = isRight ? InventoryButton.Anchor.TOP_RIGHT : InventoryButton.Anchor.TOP_LEFT;
                draggingButton.offsetX = isRight ? (targetX - (cX + cW)) : (targetX - cX);
                draggingButton.offsetY = targetY - cY;
            }

            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingButton != null) {
            draggingButton = null;
            snappedLineX = -1;
            snappedLineY = -1;
            InventoryButtonManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amountX, double amountY) {
        InventoryButton target = draggingButton;
        if (target == null) {
            int cX = getContainerLeft();
            int cY = getContainerTop();
            int cW = getContainerWidth();
            int cH = getContainerHeight();
            List<InventoryButton> list = InventoryButtonManager.getButtonsForActiveProfile();
            String activeProf = InventoryButtonManager.getActiveProfile();
            for (int i = list.size() - 1; i >= 0; i--) {
                InventoryButton b = list.get(i);
                if (!b.enabled) continue;
                if (b.profileFilter != null && !b.profileFilter.equalsIgnoreCase("ALL") && !b.profileFilter.equalsIgnoreCase(activeProf)) continue;
                int bx = b.getX(cX, cW);
                int by = b.getY(cY, cH);
                if (mouseX >= bx && mouseX <= bx + b.size && mouseY >= by && mouseY <= by + b.size) {
                    target = b;
                    break;
                }
            }
        }
        if (target != null) {
            int delta = (amountY > 0) ? 2 : -2;
            target.size = Math.max(12, Math.min(48, target.size + delta));
            InventoryButtonManager.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amountX, amountY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            InventoryButtonManager.save();
            Minecraft.getInstance().setScreenAndShow(this.parent);
            return true;
        }
        return super.keyPressed(event);
    }
}
