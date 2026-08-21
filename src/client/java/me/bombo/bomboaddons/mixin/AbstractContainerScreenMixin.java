package me.bombo.bomboaddons.mixin;

import me.bombo.bomboaddons.AutoCroesus;
import me.bombo.bomboaddons.AutoCroesusHud;
import me.bombo.bomboaddons.BomboConfig;
import me.bombo.bomboaddons.BomboConfigGUI;
import me.bombo.bomboaddons.Bomboaddons;
import me.bombo.bomboaddons.ClickLogic;
import me.bombo.bomboaddons.ComposterHelper;
import me.bombo.bomboaddons.CroesusHelper;
import me.bombo.bomboaddons.CustomBindsProcessor;
import me.bombo.bomboaddons.ItemListOverlay;
import me.bombo.bomboaddons.SkyblockCalculator;
import me.bombo.bomboaddons.SlotHighlight;
import me.bombo.bomboaddons.StopwatchManager;
import me.bombo.bomboaddons.util.CustomSlotManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag.Default;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({AbstractContainerScreen.class})
public abstract class AbstractContainerScreenMixin extends Screen {
   @Shadow
   protected @Nullable Slot hoveredSlot;
   @Shadow
   protected int leftPos;
   @Shadow
   protected int topPos;
   @Shadow
   protected int imageWidth;
   @Shadow
   protected int imageHeight;

   protected AbstractContainerScreenMixin(Component title) {
      super(title);
   }

   @Inject(
      method = {"init"},
      at = {@At("HEAD")}
   )
   private void onInit(CallbackInfo ci) {
      ClickLogic.onGuiOpen((AbstractContainerScreen)(Object)this);
   }

   @Inject(
      method = {"init"},
      at = {@At("TAIL")}
   )
   private void onInitTail(CallbackInfo ci) {
      CroesusHelper.onContainerTick((AbstractContainerScreen)(Object)this);
      ItemListOverlay.updateLayout(this.leftPos, this.imageWidth, this.topPos, this.width, this.height);
      if (BomboConfig.get().itemListEnabled && ItemListOverlay.sidebarW >= 120) {
         int searchX = ItemListOverlay.sidebarX + 5;
         int searchY = ItemListOverlay.sidebarY + ItemListOverlay.sidebarH - 52;
         int searchW = ItemListOverlay.sidebarW - 10;
         if (BomboConfig.get().itemListSeparateSearch) {
            BomboConfig.Settings s = BomboConfig.get();
            searchW = s.itemListSearchW;
            searchX = s.itemListSearchX == -1 ? this.width / 2 - 75 : s.itemListSearchX;
            searchY = s.itemListSearchY == -1 ? this.height / 2 + 20 : s.itemListSearchY;
         }

         if (ItemListOverlay.searchBox != null) {
            this.removeWidget(ItemListOverlay.searchBox);
         }
         EditBox box = new EditBox(Minecraft.getInstance().font, searchX, searchY, searchW, 16, Component.literal("Search..."));
         box.setValue(ItemListOverlay.query);
         box.setResponder((val) -> ItemListOverlay.setQuery(val));
         box.setBordered(true);
         ItemListOverlay.isHiddenState = BomboConfig.get().autoHideItemList;
         boolean isVisible = !ItemListOverlay.isHiddenState;
         if (BomboConfig.get().itemListSearchAlwaysVisible) {
            isVisible = true;
         }

         box.setVisible(isVisible);
         this.addRenderableWidget(box);
         ItemListOverlay.searchBox = box;
      } else {
         ItemListOverlay.searchBox = null;
      }

      if (StopwatchManager.isActive()) {
         int swX = 10;
         int swY = 215;
         String pauseLabel = StopwatchManager.isPaused() ? "§a▶" : "§e❚❚";
         this.addRenderableWidget(Button.builder(Component.literal(pauseLabel), (btn) -> {
            StopwatchManager.togglePause();
            btn.setMessage(Component.literal(StopwatchManager.isPaused() ? "§a▶" : "§e❚❚"));
         }).bounds(swX, swY, 25, 16).build());
         this.addRenderableWidget(Button.builder(Component.literal("§c⬛"), (btn) -> {
            StopwatchManager.stop();
            this.clearWidgets();
            this.init();
         }).bounds(swX + 28, swY, 25, 16).build());
      }

   }

   @Inject(
      method = {"extractSlot"},
      at = {@At("HEAD")}
   )
   private void onRenderSlotBg(GuiGraphicsExtractor guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
      if (BomboConfig.get().trophyHighlight && slot.hasItem()) {
         AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
         String title = screen.getTitle().getString();
         if (title.contains("Trophy Fish") || title.contains("Trophy Frogs")) {
            ItemStack stack = slot.getItem();
            ItemLore lore = (ItemLore)stack.get(DataComponents.LORE);
            if (lore != null) {
               boolean hasDiamond = false;
               boolean hasGold = false;
               boolean hasSilver = false;
               boolean hasBronze = false;

               for(Component line : lore.lines()) {
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
                  color = -2141847553;
               } else if (hasGold) {
                  color = -2130716928;
               } else if (hasSilver) {
                  color = -2133601325;
               } else if (hasBronze) {
                  color = -2134016206;
               }

               if (color != 0) {
                  guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
               }
            }
         }
      }

   }

   @Inject(
      method = {"extractSlot"},
      at = {@At("TAIL")}
   )
   private void onRenderSlot(GuiGraphicsExtractor guiGraphics, Slot slot, int x, int y, CallbackInfo ci) {
      int color = 0;
      if (SlotHighlight.isTargetSlot(slot.index)) {
         color = SlotHighlight.getCurrentColor();
      } else if (slot.hasItem()) {
         int nameColor = SlotHighlight.getHighlightColor(slot.getItem().getHoverName().getString());
         if (nameColor != 0) {
            color = nameColor;
         }
      }

      if (color != 0) {
         if ((color & -16777216) == 0) {
            color |= Integer.MIN_VALUE;
         }

         guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
      }

      if (ItemListOverlay.inventorySearchMode && ItemListOverlay.query != null && !ItemListOverlay.query.isEmpty() && slot.hasItem()) {
         ItemStack stack = slot.getItem();
         String name = stack.getHoverName().getString().toLowerCase();
         String q = ItemListOverlay.query.toLowerCase();
         int highlightColor = 0;
         if (name.contains(q)) {
            highlightColor = -1442775296;
         } else {
            boolean loreMatch = false;

            for(Component c : stack.getTooltipLines(TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, Default.NORMAL)) {
               if (c.getString().toLowerCase().contains(q)) {
                  loreMatch = true;
                  break;
               }
            }

            if (loreMatch) {
               highlightColor = -1426063616;
            } else {
               highlightColor = -1442840576;
            }
         }

         if (highlightColor != 0) {
            guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, highlightColor);
         }
      }

   }

   @Inject(
      method = {"mouseClicked"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseClicked(MouseButtonEvent event, boolean handled, CallbackInfoReturnable<Boolean> cir) {
      if (ItemListOverlay.searchBox != null) {
         float scale = BomboConfig.get().itemListSearchScale;
         double unscaledX = event.x();
         double unscaledY = event.y();
         if (scale != 1.0F) {
            int tx = ItemListOverlay.searchBox.getX();
            int ty = ItemListOverlay.searchBox.getY();
            unscaledX = (double)tx + (event.x() - (double)tx) / (double)scale;
            unscaledY = (double)ty + (event.y() - (double)ty) / (double)scale;
         }

         if (event.button() == 0 && ItemListOverlay.searchBox.isMouseOver(unscaledX, unscaledY)) {
            ItemListOverlay.searchBox.setFocused(true);
            ((AbstractContainerScreen)(Object)this).setFocused(ItemListOverlay.searchBox);
         } else if (event.button() == 0 && ItemListOverlay.searchBox.isFocused()) {
            ItemListOverlay.searchBox.setFocused(false);
         }
      }

      if (ItemListOverlay.mouseClicked(event.x(), event.y(), event.button())) {
         cir.setReturnValue(true);
      } else {
         if (this.hoveredSlot != null) {
            if (Minecraft.getInstance().hasControlDown() && me.bombo.bomboaddons.features.SupercraftHelper.handleCtrlClick((AbstractContainerScreen)(Object)this, this.hoveredSlot)) {
               cir.setReturnValue(true);
               return;
            }
            ItemStack override = CustomSlotManager.getOverride(this.hoveredSlot);
            if (override != null) {
               if (Minecraft.getInstance().hasControlDown()) {
                  AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
                  String title = screen.getTitle().getString();
                  Minecraft.getInstance().setScreenAndShow(new BomboConfigGUI((Screen)null));
                  BomboConfigGUI.selectedCategory = 25;
                  BomboConfigGUI.autofillCustomSlot(title, this.hoveredSlot.index);
               } else {
                  String cmd = CustomSlotManager.getCommand(this.hoveredSlot);
                  if (cmd != null && !cmd.isEmpty() && Minecraft.getInstance().player != null) {
                     Minecraft.getInstance().player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                  }
               }

               cir.setReturnValue(true);
               return;
            }
         }

         if (BomboConfig.get().hoppityWarp && this.hoveredSlot != null && this.hoveredSlot.hasItem() && event.button() == 0) {
            String screenTitle = this.getTitle().getString();
            if (screenTitle != null && screenTitle.contains("Hoppity's Collection")) {
               ItemStack stack = this.hoveredSlot.getItem();
               String targetWarp = null;
               for (Component c : stack.getTooltipLines(TooltipContext.of(Minecraft.getInstance().level), Minecraft.getInstance().player, Default.NORMAL)) {
                  String line = net.minecraft.ChatFormatting.stripFormatting(c.getString()).toLowerCase(java.util.Locale.ROOT);
                  if (line.contains("moonglade marsh resident")) {
                     targetWarp = "moonglade";
                     break;
                  } else if (line.contains("oasis resident")) {
                     targetWarp = "oasis";
                     break;
                  } else if (line.contains("mushroom gorge resident")) {
                     targetWarp = "desert";
                     break;
                  } else if (line.contains("spider's den resident") || line.contains("spiders den resident")) {
                     targetWarp = "spider";
                     break;
                  } else if (line.contains("the barn resident") || line.contains("barn resident")) {
                     targetWarp = "barn";
                     break;
                  } else if (line.contains("birch park resident") || line.contains("spruce woods resident") || line.contains("dark thicket resident") || line.contains("savanna woodland resident") || line.contains("jungle island resident") || line.contains("howling cave resident") || line.contains("park resident")) {
                     targetWarp = "park";
                     break;
                  } else if (line.contains("dragon's lair resident") || line.contains("goblin holdout resident") || line.contains("precursor remnants resident") || line.contains("jungle resident") || line.contains("crystal hollows resident")) {
                     targetWarp = "ch";
                     break;
                  } else if (line.contains("mithril deposits resident") || line.contains("lava springs resident") || line.contains("royal mines resident") || line.contains("cliffside veins resident") || line.contains("rampart's quarry resident") || line.contains("dwarven village resident") || line.contains("the mist resident") || line.contains("dwarven mines resident") || line.contains("mines resident")) {
                     targetWarp = "mines";
                     break;
                  } else if (line.contains("highland resident") || line.contains("village resident") || line.contains("farm resident") || line.contains("forest resident") || line.contains("mountain resident") || line.contains("ruins resident") || line.contains("graveyard resident") || line.contains("coal mine resident") || line.contains("wilderness resident") || line.contains("colosseum resident") || line.contains("hub resident")) {
                     targetWarp = "hub";
                     break;
                  }
               }
               if (targetWarp != null) {
                  Minecraft mc = Minecraft.getInstance();
                  if (mc.player != null) {
                     mc.player.connection.sendCommand("warp " + targetWarp);
                     mc.player.closeContainer();
                     cir.setReturnValue(true);
                     return;
                  }
               }
            }
         }

         if (ComposterHelper.onMouseClicked((AbstractContainerScreen)(Object)this, this.hoveredSlot, event.button())) {
            cir.setReturnValue(true);
         } else if (this.hoveredSlot == null || !SlotHighlight.isTargetSlot(this.hoveredSlot.index) && this.hoveredSlot.index != 45 && this.hoveredSlot.index != 53) {
            SlotHighlight.clearTargetSlot();
         }
      }
   }

   @Inject(
      method = {"keyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      int focusKey = ClickLogic.getKeyCode(BomboConfig.get().itemListFocusKey);
      if (focusKey != -1 && event.key() == focusKey) {
         if (ItemListOverlay.searchBox != null) {
            boolean focused = !ItemListOverlay.searchBox.isFocused();
            ItemListOverlay.searchBox.setFocused(focused);
            if (focused) {
               ((AbstractContainerScreen<?>)(Object)this).setFocused(ItemListOverlay.searchBox);
               ItemListOverlay.isHiddenState = false;
               ItemListOverlay.searchBox.setVisible(true);
            }
            cir.setReturnValue(true);
            return;
         }
      }

      if (event.key() == 258 && focusKey != 258) {
         if (ItemListOverlay.searchBox != null && !ItemListOverlay.searchBox.isFocused()) {
            cir.setReturnValue(true);
            return;
         }
      }

      if (BomboConfig.get().preventSlotSwapOnGuiKeybind) {
         if (CustomBindsProcessor.checkGuiKeybinds(event.key())) {
            if (BomboConfig.get().debugKeys) {
               Bomboaddons.sendMessage("§e[KeyDebug] AbstractContainerScreen.keyPressed consumed by GUI keybind: " + event.key());
            }
            cir.setReturnValue(true);
            return;
         }
      }
      if (ItemListOverlay.searchBox != null && ItemListOverlay.searchBox.isFocused()) {
         if (event.key() == 257 || event.key() == 335) {
            String val = ItemListOverlay.searchBox.getValue();
            SkyblockCalculator.EvaluationResult res = SkyblockCalculator.evaluate(val);
            if (res.error == null) {
               String resStr = String.valueOf(res.value);
               if (resStr.endsWith(".0")) {
                  resStr = resStr.substring(0, resStr.length() - 2);
               }

               ItemListOverlay.searchBox.setValue(resStr);
               Minecraft.getInstance().keyboardHandler.setClipboard(resStr);
            }
         }

         if (ItemListOverlay.searchBox.keyPressed(event)) {
            cir.setReturnValue(true);
         } else if (event.key() == 256) {
            ItemListOverlay.searchBox.setFocused(false);
            cir.setReturnValue(true);
         } else {
            cir.setReturnValue(true);
         }

      } else {
         int prefillBoundKey = ClickLogic.getKeyCode(BomboConfig.get().customSlotPrefillKey);
         if (this.hoveredSlot != null && event.key() == prefillBoundKey) {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen)(Object)this;
            String title = screen.getTitle().getString();
            Minecraft.getInstance().setScreenAndShow(new BomboConfigGUI((Screen)null));
            BomboConfigGUI.selectedCategory = 25;
            BomboConfigGUI.autofillCustomSlot(title, this.hoveredSlot.index);
            cir.setReturnValue(true);
         }

      }
   }

   @Inject(
      method = {"onClose"},
      at = {@At("HEAD")}
   )
   private void onGuiClose(CallbackInfo ci) {
      AutoCroesus.onScreenClosed();
      if (System.currentTimeMillis() - SlotHighlight.highlightStartTime > 500L) {
         SlotHighlight.clearTargetSlot();
      }

   }

   @Inject(
      method = {"extractRenderState"},
      at = {@At("TAIL")}
   )
   private void onExtractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!(((Object)this) instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) {
         ItemListOverlay.render(graphics, Minecraft.getInstance().font, mouseX, mouseY);
      }

      if (StopwatchManager.isActive()) {
         StopwatchManager.drawStopwatch(graphics, 10, 200);
      }

      AutoCroesus.onContainerTick((AbstractContainerScreen)(Object)this);
      AutoCroesus.onCheckGuiTick((AbstractContainerScreen)(Object)this);
      AutoCroesusHud.renderInContainer(graphics);
   }

   @Inject(
      method = {"mouseScrolled"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onMouseScrolled(double mouseX, double mouseY, double amountX, double amountY, CallbackInfoReturnable<Boolean> cir) {
      if (ItemListOverlay.mouseScrolled(mouseX, mouseY, amountY)) {
         cir.setReturnValue(true);
      }
   }

   @Inject(
      method = {"checkHotbarKeyPressed"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCheckHotbarKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
      if (BomboConfig.get().preventSlotSwapOnGuiKeybind) {
         if (CustomBindsProcessor.hasHandledGuiKeyRecently(event.key()) || CustomBindsProcessor.matchesAnyRegisteredGuiBind(event.key())) {
            if (BomboConfig.get().debugKeys) {
               Bomboaddons.sendMessage("§e[KeyDebug] Blocked checkHotbarKeyPressed for key: " + event.key() + " (matching bind active)");
            }
            cir.setReturnValue(true);
         }
      }
   }
}
