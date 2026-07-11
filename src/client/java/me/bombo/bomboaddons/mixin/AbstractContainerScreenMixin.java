package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.SlotHighlight;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends net.minecraft.client.gui.screens.Screen {
    protected AbstractContainerScreenMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;

    @Inject(method = "init", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        me.bombo.bomboaddons.ClickLogic.onGuiOpen((AbstractContainerScreen)(Object)this);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        me.bombo.bomboaddons.ItemListOverlay.updateLayout(this.leftPos, this.imageWidth, this.topPos, this.width, this.height);
        
        if (me.bombo.bomboaddons.ItemListOverlay.sidebarW >= 120) {
            int searchX = me.bombo.bomboaddons.ItemListOverlay.sidebarX + 5;
            int searchY = me.bombo.bomboaddons.ItemListOverlay.sidebarY + me.bombo.bomboaddons.ItemListOverlay.sidebarH - 52;
            int searchW = me.bombo.bomboaddons.ItemListOverlay.sidebarW - 10;
            
            if (me.bombo.bomboaddons.BomboConfig.get().itemListSeparateSearch) {
                me.bombo.bomboaddons.BomboConfig.Settings s = me.bombo.bomboaddons.BomboConfig.get();
                searchW = s.itemListSearchW;
                searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
                searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
            }
            
            net.minecraft.client.gui.components.EditBox box = new net.minecraft.client.gui.components.EditBox(
                net.minecraft.client.Minecraft.getInstance().font,
                searchX, searchY, searchW, 16,
                net.minecraft.network.chat.Component.literal("Search...")
            );
            box.setValue(me.bombo.bomboaddons.ItemListOverlay.query);
            box.setResponder(val -> {
                me.bombo.bomboaddons.ItemListOverlay.setQuery(val);
            });
            box.setBordered(true);
            
            me.bombo.bomboaddons.ItemListOverlay.isHiddenState = me.bombo.bomboaddons.BomboConfig.get().autoHideItemList;
            boolean isVisible = !me.bombo.bomboaddons.ItemListOverlay.isHiddenState;
            if (me.bombo.bomboaddons.BomboConfig.get().itemListSearchAlwaysVisible) {
                isVisible = true;
            }
            box.setVisible(isVisible);
            
            this.addRenderableWidget(box);
            me.bombo.bomboaddons.ItemListOverlay.searchBox = box;
        } else {
            me.bombo.bomboaddons.ItemListOverlay.searchBox = null;
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void onRenderSlotBg(GuiGraphicsExtractor guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
        if (me.bombo.bomboaddons.BomboConfig.get().trophyHighlight && slot.hasItem()) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
            String title = screen.getTitle().getString();
            if (title.contains("Trophy Fish") || title.contains("Trophy Frogs")) {
                net.minecraft.world.item.ItemStack stack = slot.getItem();
                net.minecraft.world.item.component.ItemLore lore = stack.get(net.minecraft.core.component.DataComponents.LORE);
                if (lore != null) {
                    boolean hasDiamond = false;
                    boolean hasGold = false;
                    boolean hasSilver = false;
                    boolean hasBronze = false;
                    for (net.minecraft.network.chat.Component line : lore.lines()) {
                        String clean = line.getString().replaceAll("(?i)§[0-9a-fk-or]", "");
                        if (clean.contains("Diamond") && (clean.contains("✔") || clean.contains("✓"))) {
                            hasDiamond = true;
                        }
                        if (clean.contains("Gold") && (clean.contains("✔") || clean.contains("✓"))) {
                            hasGold = true;
                        }
                        if (clean.contains("Silver") && (clean.contains("✔") || clean.contains("✓"))) {
                            hasSilver = true;
                        }
                        if (clean.contains("Bronze") && (clean.contains("✔") || clean.contains("✓"))) {
                            hasBronze = true;
                        }
                    }
                    int color = 0;
                    if (hasDiamond) {
                        color = 0x8055FFFF; // Diamond Cyan
                    } else if (hasGold) {
                        color = 0x80FFD700; // Gold
                    } else if (hasSilver) {
                        color = 0x80D3D3D3; // Silver
                    } else if (hasBronze) {
                        color = 0x80CD7F32; // Bronze
                    }
                    if (color != 0) {
                        guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
                    }
                }
            }
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void onRenderSlot(GuiGraphicsExtractor guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
        int color = 0;
        
        // Only highlight slots targeted during search navigation (temporary session results)
        if (SlotHighlight.isTargetSlot(slot.index)) {
            color = SlotHighlight.getCurrentColor();
        } else if (slot.hasItem()) {
            int nameColor = SlotHighlight.getHighlightColor(slot.getItem().getHoverName().getString());
            if (nameColor != 0) color = nameColor;
        } 

        if (color != 0) {
            // Guarantee visibility with an alpha floor (0x80 = 50% opacity minimum)
            if ((color & 0xFF000000) == 0) color |= 0x80000000;
            
            guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        }

        if (me.bombo.bomboaddons.ItemListOverlay.inventorySearchMode && me.bombo.bomboaddons.ItemListOverlay.query != null && !me.bombo.bomboaddons.ItemListOverlay.query.isEmpty() && slot.hasItem()) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            String name = stack.getHoverName().getString().toLowerCase();
            String q = me.bombo.bomboaddons.ItemListOverlay.query.toLowerCase();
            int highlightColor = 0;
            if (name.contains(q)) {
                highlightColor = 0xAA00FF00; // Green for name match
            } else {
                boolean loreMatch = false;
                java.util.List<net.minecraft.network.chat.Component> lore = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(net.minecraft.client.Minecraft.getInstance().level), net.minecraft.client.Minecraft.getInstance().player, net.minecraft.world.item.TooltipFlag.Default.NORMAL);
                for (net.minecraft.network.chat.Component c : lore) {
                    if (c.getString().toLowerCase().contains(q)) {
                        loreMatch = true;
                        break;
                    }
                }
                if (loreMatch) {
                    highlightColor = 0xAAFFFF00; // Yellow for lore match
                } else {
                    highlightColor = 0xAA000000; // Darken non-matches
                }
            }
            if (highlightColor != 0) {
                guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, highlightColor);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean handled,
            CallbackInfoReturnable<Boolean> cir) {
        if (me.bombo.bomboaddons.ItemListOverlay.searchBox != null) {
            float scale = me.bombo.bomboaddons.BomboConfig.get().itemListSearchScale;
            double unscaledX = event.x();
            double unscaledY = event.y();
            if (scale != 1.0f) {
                int tx = me.bombo.bomboaddons.ItemListOverlay.searchBox.getX();
                int ty = me.bombo.bomboaddons.ItemListOverlay.searchBox.getY();
                unscaledX = tx + (event.x() - tx) / scale;
                unscaledY = ty + (event.y() - ty) / scale;
            }
            if (event.button() == 0 && me.bombo.bomboaddons.ItemListOverlay.searchBox.isMouseOver(unscaledX, unscaledY)) {
                me.bombo.bomboaddons.ItemListOverlay.searchBox.setFocused(true);
                ((AbstractContainerScreen<?>)(Object)this).setFocused(me.bombo.bomboaddons.ItemListOverlay.searchBox);
            } else if (event.button() == 0 && me.bombo.bomboaddons.ItemListOverlay.searchBox.isFocused()) {
                me.bombo.bomboaddons.ItemListOverlay.searchBox.setFocused(false);
            }
        }
        if (me.bombo.bomboaddons.ItemListOverlay.mouseClicked(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (hoveredSlot != null) {
            net.minecraft.world.item.ItemStack override = me.bombo.bomboaddons.util.CustomSlotManager.getOverride(hoveredSlot);
            if (override != null) {
                if (net.minecraft.client.Minecraft.getInstance().hasControlDown()) {
                    AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
                    String title = screen.getTitle().getString();
                    net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.BomboConfigGUI(null));
                    me.bombo.bomboaddons.BomboConfigGUI.selectedCategory = 25; // Custom Slots category
                    me.bombo.bomboaddons.BomboConfigGUI.autofillCustomSlot(title, hoveredSlot.index);
                } else {
                    String cmd = me.bombo.bomboaddons.util.CustomSlotManager.getCommand(hoveredSlot);
                    if (cmd != null && !cmd.isEmpty()) {
                        if (net.minecraft.client.Minecraft.getInstance().player != null) {
                            net.minecraft.client.Minecraft.getInstance().player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                        }
                    }
                }
                cir.setReturnValue(true);
                return;
            }
        }

        if (me.bombo.bomboaddons.ComposterHelper.onMouseClicked((AbstractContainerScreen)(Object)this, hoveredSlot, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        
        if (hoveredSlot != null && (SlotHighlight.isTargetSlot(hoveredSlot.index) || hoveredSlot.index == 45 || hoveredSlot.index == 53)) {
            return; // Don't clear if we clicked a target slot or navigation arrows.
        }
        SlotHighlight.clearTargetSlot();
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (me.bombo.bomboaddons.ItemListOverlay.searchBox != null && me.bombo.bomboaddons.ItemListOverlay.searchBox.isFocused()) {
            if (event.key() == 257 || event.key() == 335) { // ENTER
                String val = me.bombo.bomboaddons.ItemListOverlay.searchBox.getValue();
                me.bombo.bomboaddons.SkyblockCalculator.EvaluationResult res = me.bombo.bomboaddons.SkyblockCalculator.evaluate(val);
                if (res.error == null) {
                    String resStr = String.valueOf(res.value);
                    if (resStr.endsWith(".0")) resStr = resStr.substring(0, resStr.length() - 2);
                    me.bombo.bomboaddons.ItemListOverlay.searchBox.setValue(resStr);
                    net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(resStr);
                }
            }
            if (me.bombo.bomboaddons.ItemListOverlay.searchBox.keyPressed(event)) {
                cir.setReturnValue(true);
            } else if (event.key() == 256) { // ESC
                me.bombo.bomboaddons.ItemListOverlay.searchBox.setFocused(false);
                cir.setReturnValue(true);
            } else {
                cir.setReturnValue(true);
            }
            return;
        }

        int prefillBoundKey = me.bombo.bomboaddons.ClickLogic.getKeyCode(me.bombo.bomboaddons.BomboConfig.get().customSlotPrefillKey);
        if (hoveredSlot != null && event.key() == prefillBoundKey) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>)(Object)this;
            String title = screen.getTitle().getString();
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(new me.bombo.bomboaddons.BomboConfigGUI(null));
            me.bombo.bomboaddons.BomboConfigGUI.selectedCategory = 25; // Custom Slots category
            me.bombo.bomboaddons.BomboConfigGUI.autofillCustomSlot(title, hoveredSlot.index);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onGuiClose(CallbackInfo ci) {
        if (System.currentTimeMillis() - me.bombo.bomboaddons.SlotHighlight.highlightStartTime > 500) {
            SlotHighlight.clearTargetSlot();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!((Object)this instanceof net.minecraft.client.gui.screens.inventory.EffectsInInventory)) {
            me.bombo.bomboaddons.ItemListOverlay.render(graphics, net.minecraft.client.Minecraft.getInstance().font, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double amountX, double amountY, CallbackInfoReturnable<Boolean> cir) {
        if (me.bombo.bomboaddons.ItemListOverlay.mouseScrolled(mouseX, mouseY, amountY)) {
            cir.setReturnValue(true);
        }
    }



}
